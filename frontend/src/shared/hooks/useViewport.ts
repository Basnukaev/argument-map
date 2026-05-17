import { useSyncExternalStore } from 'react';

/**
 * Tailwind v4 default breakpoints (px). Используются для consistency
 * между JS conditional logic и CSS utility prefixes (`sm:`/`md:`/...)
 *
 * - sm: 640px
 * - md: 768px  (mobile/tablet boundary - default для `useIsMobile`)
 * - lg: 1024px
 * - xl: 1280px
 */
export const BREAKPOINTS = {
  sm: 640,
  md: 768,
  lg: 1024,
  xl: 1280,
} as const;

const SERVER_SNAPSHOT = false;

/**
 * Подписка на медиа-запрос `(max-width: {breakpointPx - 1}px)`.
 * Возвращает `true` если viewport уже мобильный
 *
 * Реализация через `useSyncExternalStore` - правильный паттерн для
 * subscription к browser API без useEffect + setState (eslint правило
 * react-hooks/set-state-in-effect). Снимок берётся синхронно через
 * `matchMedia(query).matches`, подписка через `mql.addEventListener`
 *
 * Используй для **conditional logic** (другой компонент, drawer-pattern
 * вместо right-panel, разные хэндлеры) - для **стилей** предпочтительнее
 * Tailwind breakpoint prefix (`md:`) без JS
 *
 * Default breakpoint - 768px (md). Ниже = mobile, выше = tablet/desktop
 */
export function useIsMobile(breakpointPx: number = BREAKPOINTS.md): boolean {
  const query = `(max-width: ${breakpointPx - 1}px)`;

  function subscribe(notify: () => void): () => void {
    if (typeof window === 'undefined') return () => {};
    const mql = window.matchMedia(query);
    mql.addEventListener('change', notify);
    return () => mql.removeEventListener('change', notify);
  }

  function getSnapshot(): boolean {
    if (typeof window === 'undefined') return SERVER_SNAPSHOT;
    return window.matchMedia(query).matches;
  }

  return useSyncExternalStore(subscribe, getSnapshot, () => SERVER_SNAPSHOT);
}
