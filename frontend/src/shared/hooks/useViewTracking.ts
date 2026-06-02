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
    if (typeof window === 'undefined') return;
    const sessionKey = `viewed:${entityType}:${id}`;

    // Dedup-check ДО POST: если view уже успешно засчитан в этой session —
    // не шлём повторно (reload страницы). Read может бросить в private
    // mode — тогда продолжаем без dedup.
    try {
      if (window.sessionStorage.getItem(sessionKey)) return;
    } catch {
      // sessionStorage недоступен (private mode etc) — продолжаем без
      // dedup, idempotent counter может увеличиться несколько раз
      // (acceptable trade-off).
    }

    // Guard от двойного POST в пределах одного mount (StrictMode double-effect
    // в dev, race до того как sessionStorage записан). Сбрасывается на cleanup.
    let cancelled = false;

    // Fire-and-forget по семантике UI (не ждём в render), но флаг "view
    // засчитан" ставим ТОЛЬКО после успешного POST — иначе упавший первый
    // POST навсегда заблокировал бы retry на следующем визите в этой session.
    apiPostRaw(`/api/v1/${entityType}/${id}/views`, {})
      .then(() => {
        if (cancelled) return;
        try {
          window.sessionStorage.setItem(sessionKey, '1');
        } catch {
          // sessionStorage недоступен — пропускаем dedup-маркер
        }
      })
      .catch(() => {
        // silent fail — view tracking is best-effort. Флаг НЕ ставим,
        // чтобы следующий визит в этой session повторил попытку.
      });

    return () => {
      cancelled = true;
    };
  }, [entityType, id]);
}
