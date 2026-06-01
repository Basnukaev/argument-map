import { useEffect, useState } from 'react';
import { apiGetRaw, formatApiError } from '@/shared/api/client';
import type { AsyncState } from '@/shared/types/async';
import { getCached, setCached } from './queryCache';

interface Options {
  /** Возвращаемое сообщение при ошибке если не Error-like */
  fallbackError?: string;
  /** Если false - hook сидит в idle, не делает запрос. Удобно для условной
   * подгрузки (например `enabled: Boolean(bookId)`). */
  enabled?: boolean;
}

/**
 * Generic fetch hook. Загружает данные по `path` через apiGetRaw,
 * управляет AbortController, возвращает `AsyncState<T>` (kind:
 * idle/loading/success/error) - тот же discriminated union что и в
 * shared/types/async.ts.
 *
 * Использование:
 * ```ts
 * const state = useApiQuery<Topic[]>('/api/v1/topics');
 * if (state.kind === 'success') return state.data.map(...);
 * ```
 *
 * Для refresh - изменить `path` или передать `enabled: false → true`.
 * Если нужен явный refetch-callback (после мутации) - используй
 * useState + useEffect напрямую (как TopicGraphPage с refreshKey).
 *
 * F-12 audit: hook доступен для future fetch-only компонентов с
 * простым success-state. Существующие useEffect-patterns не
 * мигрировались - они уже работают и иногда имеют extra logic
 * (counters, manual refetch) которые этот hook не покрывает.
 *
 * SWR (stale-while-revalidate): ответ кэшируется по `path` в
 * `queryCache`. На повторный mount того же path кэш отдаётся МГНОВЕННО
 * (state=success без спиннера), а сетевой запрос идёт в фоне и заменяет
 * данные когда придёт. На ошибке при наличии кэша мы НЕ затираем
 * валидные данные error-экраном — оставляем последний успешный ответ
 * (для path без кэша ошибка показывается как раньше). Кэш чистится
 * мутациями через `invalidateCache` из queryCache.
 */
export function useApiQuery<T>(path: string | null, options: Options = {}): AsyncState<T> {
  const { fallbackError = 'Не удалось загрузить', enabled = true } = options;
  // Lazy init:
  // - есть кэш по path → стартуем сразу с 'success' (SWR: мгновенно
  //   показываем последний известный ответ, без спиннера);
  // - path/enabled активны но кэша нет → 'loading' (без промежуточного
  //   'idle' кадра — иначе List<T> мигает "пусто" перед "загрузка");
  // - иначе → 'idle'.
  const [state, setState] = useState<AsyncState<T>>(() => {
    if (!enabled || path == null) return { kind: 'idle' };
    const cached = getCached<T>(path);
    if (cached !== undefined) return { kind: 'success', data: cached.data };
    return { kind: 'loading' };
  });

  useEffect(() => {
    if (!enabled || path == null) {
      // Reset на 'idle' при transition enabled→false / path→null:
      // нельзя выразить derived state без потери семантики (если был
      // success, после disable hook должен вернуть idle, не закешированный
      // success). Explicit trade-off за ясность.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setState({ kind: 'idle' });
      return;
    }
    const controller = new AbortController();
    // SWR: при смене path сначала отдаём кэш (если есть) — мгновенный
    // success без спиннера. Иначе семантический переход в 'loading'
    // (новый fetch = новое loading). Здесь setState стоит после guard'а
    // (early-return выше) — правило set-state-in-effect его не флагует.
    const cached = getCached<T>(path);
    if (cached !== undefined) {
      setState({ kind: 'success', data: cached.data });
    } else {
      setState({ kind: 'loading' });
    }
    // Revalidate всегда — даже при наличии кэша подтягиваем свежие данные.
    apiGetRaw<T>(path, { signal: controller.signal })
      .then((data) => {
        if (controller.signal.aborted) return;
        setCached(path, data);
        setState({ kind: 'success', data });
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        // Не затираем валидный кэш error-экраном: если по path есть
        // закэшированные данные — оставляем их (revalidate провалился,
        // но stale-данные лучше пустой ошибки). Без кэша — показываем
        // ошибку как раньше.
        if (getCached<T>(path) !== undefined) return;
        setState({ kind: 'error', message: formatApiError(e, fallbackError) });
      });
    return () => controller.abort();
  }, [path, enabled, fallbackError]);

  return state;
}
