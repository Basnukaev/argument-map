/**
 * In-memory stale-while-revalidate (SWR) cache для GET-запросов.
 *
 * Зачем: useApiQuery / usePagedSearch перезапрашивают данные с нуля на
 * каждый mount — навигация на list-страницу и обратно даёт 1с+ пустого
 * экрана / спиннера. Этот кэш отдаёт последний известный ответ МГНОВЕННО
 * (state=success сразу), а сетевой запрос идёт в фоне ("revalidate") и
 * заменяет данные когда придёт.
 *
 * Природа кэша:
 * - module-scoped Map — живёт пока жив JS-модуль (т.е. в рамках одной
 *   сессии браузера, переживает смену роута). Полная перезагрузка
 *   страницы (F5) очищает — это нормально, SWR не претендует на
 *   persistence.
 * - ключ — обычно request path (`/api/v1/topics?page=0`). Caller сам
 *   решает схему ключа (для usePagedSearch ключ включает deps).
 * - value — последний успешный ответ + timestamp записи.
 *
 * Инвалидация: мутации (создать тему, удалить узел) должны звать
 * `invalidateCache(predicate)` чтобы сбросить устаревшие ключи. Без
 * этого фоновый revalidate всё равно подтянет свежие данные при
 * следующем mount, но кэш покажет stale-версию на один кадр — поэтому
 * после мутаций лучше явно инвалидировать соответствующий префикс.
 */

interface CacheEntry {
  data: unknown;
  /** epoch ms момента записи в кэш. */
  ts: number;
}

const cache = new Map<string, CacheEntry>();

/**
 * Дефолтный TTL "свежести" (мс). Используется `isFresh` для решения
 * можно ли пропустить фоновый refetch. По умолчанию SWR всё равно
 * ревалидирует на каждый mount — TTL даёт callerʼу опцию НЕ дёргать
 * сеть если запись совсем свежая (например только что вернулись с
 * другой вкладки). Держим простым: 30с.
 */
export const DEFAULT_TTL_MS = 30_000;

/**
 * Возвращает закэшированную запись (data + ts) или undefined.
 * Дженерик `T` — тип данных; caller отвечает за корректность (как и
 * apiGetRaw<T> — runtime-проверки нет).
 */
export function getCached<T>(key: string): { data: T; ts: number } | undefined {
  const entry = cache.get(key);
  if (entry === undefined) return undefined;
  return { data: entry.data as T, ts: entry.ts };
}

/** Записывает (или перезаписывает) значение по ключу с текущим timestamp. */
export function setCached(key: string, data: unknown): void {
  cache.set(key, { data, ts: Date.now() });
}

/**
 * "Достаточно ли свежая" запись чтобы пропустить фоновый refetch.
 * `ttlMs` по умолчанию DEFAULT_TTL_MS.
 */
export function isFresh(ts: number, ttlMs: number = DEFAULT_TTL_MS): boolean {
  return Date.now() - ts < ttlMs;
}

/**
 * Сбрасывает ключи. Без predicate — чистит весь кэш. С predicate —
 * удаляет ключи для которых predicate(key) === true.
 *
 * Пример (после создания темы):
 * ```ts
 * invalidateCache((k) => k.startsWith('/api/v1/topics'));
 * ```
 */
export function invalidateCache(predicate?: (key: string) => boolean): void {
  if (predicate === undefined) {
    cache.clear();
    return;
  }
  for (const key of cache.keys()) {
    if (predicate(key)) cache.delete(key);
  }
}
