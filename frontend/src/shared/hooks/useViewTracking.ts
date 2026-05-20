import { useEffect } from 'react';
import { apiPostRaw } from '@/shared/api/client';

/**
 * Vision 49d Section 2.1 Phase 2 — frontend view tracking. На mount
 * detail page POST /api/v1/{entityType}/{id}/views (анонимно
 * разрешено, guest views тоже count).
 *
 * <p>Anti-spam: per-session dedup через sessionStorage (key
 * `viewed:{entityType}:{id}`). Reload страницы в той же session →
 * no extra POST. Открытие в новой tab/window — отдельная session,
 * считаем view.
 *
 * <p>Errors silently игнорированы — view tracking is best-effort,
 * не блокирует UI. Backend endpoint anonymous-allowed, нет 401.
 *
 * @param entityType - "topics" | "questions" | "library/books"
 * @param id - UUID сущности или null (no-op если null - до загрузки)
 */
export function useViewTracking(
  entityType: 'topics' | 'questions' | 'library/books',
  id: string | null | undefined,
): void {
  useEffect(() => {
    if (!id) return;
    const sessionKey = `viewed:${entityType}:${id}`;
    if (typeof window === 'undefined') return;
    try {
      if (window.sessionStorage.getItem(sessionKey)) return;
      window.sessionStorage.setItem(sessionKey, '1');
    } catch {
      // sessionStorage недоступен (private mode etc) — продолжаем
      // без dedup, повторный POST не сломает (idempotent counter
      // увеличится несколько раз — acceptable trade-off)
    }
    // Fire-and-forget — не ждём response, не показываем errors
    apiPostRaw(`/api/v1/${entityType}/${id}/views`, {}).catch(() => {
      // silent fail - view tracking is best-effort
    });
  }, [entityType, id]);
}
