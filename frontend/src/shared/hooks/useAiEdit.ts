/**
 * useAiEdit - polling hook для AI editing pass (Этап 17.e.f, ADR-042).
 *
 * Запускает async AI edit на странице через `POST
 * /api/v1/library/pages/{id}/ai-edit`, опрашивает `GET ...` каждые
 * `POLL_INTERVAL_MS` пока статус не станет `DONE` / `FAILED` (либо пока
 * не сработает `MAX_POLL_DURATION_MS` таймаут). При `DONE` фетчит
 * свежий `PageResponse` через `/pages/{id}` чтобы получить обновлённое
 * `formattedContent`, передаёт его в `onContentReady` callback.
 *
 * **Cleanup-инварианты**:
 * 1. `cancel()` отменяет fetch'ы через AbortController + чистит
 *    interval/timeout. Polling прерывается, server-side processing -
 *    нет (это контракт ADR-042: AI edit идёт в bounded task pool, его
 *    нельзя отменить из REST).
 * 2. Новый `start()` сначала зовёт `cancel()` чтобы убить старый цикл -
 *    исключает гонки одновременных polling'ов на двух pageId.
 * 3. `useEffect` cleanup на unmount вызывает `cancel()` - не оставляем
 *    висящих fetch'ей после ухода со страницы.
 *
 * `status` отражает локальное состояние UI:
 * - `idle` - ничего не запущено / cancel был вызван
 * - `pending` / `processing` - polling активен (cовпадает с backend
 *   ai_edit_status)
 * - `done` - terminal, контент уже передан в callback
 * - `failed` - terminal, AI edit upstream упал
 *
 * 503 на POST (`ai-edit-not-configured`) - ApiError пробрасывается из
 * `start()` промиса, callsite сам ловит и показывает toast (нужен
 * контроль над user-facing сообщением через i18n).
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { apiGetRaw, apiPostRaw, ApiError } from '@/shared/api/client';
import type { components } from '@/shared/api/types';

type AiEditJobResponse = components['schemas']['AiEditJobResponse'];
type PageResponse = components['schemas']['PageResponse'] & {
  formattedContent?: object | null;
};

export type AiEditStatus = 'idle' | 'pending' | 'processing' | 'done' | 'failed';

export interface UseAiEditResult {
  status: AiEditStatus;
  /** Секунды с момента запуска - для overlay-таймера. 0 когда idle. */
  elapsedSeconds: number;
  /** Запустить AI edit на указанной странице. Полный цикл - POST + polling. */
  start: (pageId: string) => Promise<void>;
  /** Прервать polling (server-side processing продолжится). */
  cancel: () => void;
}

/** Интервал между GET-опросами (мс). 3 сек - компромисс между
 *  отзывчивостью и нагрузкой на бэк. */
const POLL_INTERVAL_MS = 3000;
/** Максимальная длительность polling (мс). 5 мин - AI edit обычно
 *  10-30 сек, 5 мин запас на медленный Anthropic + retry. */
const MAX_POLL_DURATION_MS = 5 * 60 * 1000;

export function useAiEdit(
  onContentReady: (formattedContent: object) => void,
): UseAiEditResult {
  const [status, setStatus] = useState<AiEditStatus>('idle');
  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  const abortRef = useRef<AbortController | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const tickerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const startedAtRef = useRef<number>(0);

  // Стабильная reference на callback - не пересоздаём polling-логику
  // при каждом render родителя
  const callbackRef = useRef(onContentReady);
  callbackRef.current = onContentReady;

  const clearTimers = useCallback(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
    if (tickerRef.current) {
      clearInterval(tickerRef.current);
      tickerRef.current = null;
    }
  }, []);

  const cancel = useCallback(() => {
    if (abortRef.current) {
      abortRef.current.abort();
      abortRef.current = null;
    }
    clearTimers();
    startedAtRef.current = 0;
    setElapsedSeconds(0);
    setStatus('idle');
  }, [clearTimers]);

  const start = useCallback(
    async (pageId: string) => {
      // Гарантируем чистый старт - если был старый цикл, прибьём
      cancel();

      const controller = new AbortController();
      abortRef.current = controller;
      startedAtRef.current = Date.now();
      setElapsedSeconds(0);
      setStatus('pending');

      // Тикер для UI-overlay (обновление elapsedSeconds раз в секунду).
      // Отдельный от polling interval - не хотим связывать UI tick c
      // network request frequency.
      tickerRef.current = setInterval(() => {
        setElapsedSeconds(Math.floor((Date.now() - startedAtRef.current) / 1000));
      }, 1000);

      try {
        // POST триггер. 503 → ApiError пробрасывается callsite'у.
        const initial = await apiPostRaw<AiEditJobResponse>(
          `/api/v1/library/pages/${pageId}/ai-edit`,
          {},
          { signal: controller.signal },
        );

        const initialStatus = mapBackendStatus(initial.status);
        setStatus(initialStatus);

        // На случай если backend уже выставил DONE/FAILED к моменту
        // 202 ответа (race - PROCESSING очень быстро отработал)
        if (initialStatus === 'done') {
          await fetchAndApplyResult(pageId, controller.signal);
          finishCleanup();
          return;
        }
        if (initialStatus === 'failed') {
          finishCleanup();
          return;
        }
      } catch (e) {
        // AbortError при cancel() - silently бросать выше нет смысла,
        // status уже сброшен в cancel(). Любые другие - re-throw чтобы
        // callsite показал toast.
        if (controller.signal.aborted) return;
        cleanupOnError();
        throw e;
      }

      // Polling цикл
      intervalRef.current = setInterval(() => {
        apiGetRaw<AiEditJobResponse>(
          `/api/v1/library/pages/${pageId}/ai-edit`,
          { signal: controller.signal },
        )
          .then(async (job) => {
            if (controller.signal.aborted) return;
            const next = mapBackendStatus(job.status);
            setStatus(next);
            if (next === 'done') {
              await fetchAndApplyResult(pageId, controller.signal);
              finishCleanup();
            } else if (next === 'failed') {
              finishCleanup();
            }
          })
          .catch((e: unknown) => {
            if (controller.signal.aborted) return;
            // Транзитные ошибки (network blip) - не валим polling, ждём
            // следующий tick. Но если ApiError 404 - страница исчезла,
            // ставим failed и стопаем.
            if (e instanceof ApiError && e.status === 404) {
              setStatus('failed');
              cleanupOnError();
            }
            // Иначе - log + continue. Postpone до next tick.
            console.warn('AI edit polling error (will retry):', e);
          });
      }, POLL_INTERVAL_MS);

      // Hard timeout - оставшиеся 5 мин страховка от вечного polling.
      timeoutRef.current = setTimeout(() => {
        if (controller.signal.aborted) return;
        console.warn(`AI edit polling timeout (${MAX_POLL_DURATION_MS}ms), aborting`);
        cleanupOnError();
        setStatus('failed');
      }, MAX_POLL_DURATION_MS);

      async function fetchAndApplyResult(pageIdInner: string, signal: AbortSignal) {
        const page = await apiGetRaw<PageResponse>(
          `/api/v1/library/pages/${pageIdInner}`,
          { signal },
        );
        if (signal.aborted) return;
        if (page.formattedContent != null) {
          callbackRef.current(page.formattedContent);
        }
      }

      function finishCleanup() {
        clearTimers();
        abortRef.current = null;
      }

      function cleanupOnError() {
        clearTimers();
        abortRef.current = null;
      }
    },
    [cancel, clearTimers],
  );

  // Unmount - сбросить всё, не допустить leak'ов
  useEffect(() => {
    return () => {
      if (abortRef.current) abortRef.current.abort();
      if (intervalRef.current) clearInterval(intervalRef.current);
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
      if (tickerRef.current) clearInterval(tickerRef.current);
    };
  }, []);

  return { status, elapsedSeconds, start, cancel };
}

/**
 * Маппинг backend enum (PENDING/PROCESSING/DONE/FAILED) на UI status.
 * Backend ai_edit_status nullable - null означает «never started»,
 * мапим на `pending` пока POST не вернул реальный статус.
 */
function mapBackendStatus(backend: string | undefined): AiEditStatus {
  switch (backend) {
    case 'PENDING':
      return 'pending';
    case 'PROCESSING':
      return 'processing';
    case 'DONE':
      return 'done';
    case 'FAILED':
      return 'failed';
    default:
      return 'pending';
  }
}
