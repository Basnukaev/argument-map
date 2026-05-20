import { useEffect, useState } from 'react';
import { apiGetRaw, formatApiError } from '@/shared/api/client';
import type { AsyncState } from '@/shared/types/async';

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
 */
export function useApiQuery<T>(path: string | null, options: Options = {}): AsyncState<T> {
  const { fallbackError = 'Не удалось загрузить', enabled = true } = options;
  // Lazy init: если на mount path и enabled активны - стартуем сразу с 'loading',
  // без промежуточного 'idle' рендера. Иначе consumer мигает с empty-state на 1
  // фрейм перед запросом, что особенно заметно при List<T> рендере (showing
  // "пусто" перед "загрузка")
  const [state, setState] = useState<AsyncState<T>>(() =>
    enabled && path != null ? { kind: 'loading' } : { kind: 'idle' },
  );

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
    // setState('loading') при смене path: семантический переход (новый fetch =
    // новое loading), не cosmetic. Sync setState in effect здесь intentional
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setState({ kind: 'loading' });
    apiGetRaw<T>(path, { signal: controller.signal })
      .then((data) => {
        if (controller.signal.aborted) return;
        setState({ kind: 'success', data });
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        setState({ kind: 'error', message: formatApiError(e, fallbackError) });
      });
    return () => controller.abort();
  }, [path, enabled, fallbackError]);

  return state;
}
