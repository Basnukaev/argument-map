import { useEffect, useState } from 'react';

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

/**
 * Подписка на медиа-запрос `(max-width: {breakpointPx - 1}px)`.
 * Возвращает `true` если viewport уже мобильный
 *
 * Используй для **conditional logic** (другой компонент, drawer-pattern
 * вместо right-panel, разные хэндлеры) - для **стилей** предпочтительнее
 * Tailwind breakpoint prefix (`md:`) без JS
 *
 * Default breakpoint - 768px (md). Ниже = mobile, выше = tablet/desktop
 */
export function useIsMobile(breakpointPx: number = BREAKPOINTS.md): boolean {
  const [isMobile, setIsMobile] = useState<boolean>(() => {
    if (typeof window === 'undefined') return false;
    return window.matchMedia(`(max-width: ${breakpointPx - 1}px)`).matches;
  });

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const mql = window.matchMedia(`(max-width: ${breakpointPx - 1}px)`);
    setIsMobile(mql.matches);
    const handler = (e: MediaQueryListEvent) => setIsMobile(e.matches);
    mql.addEventListener('change', handler);
    return () => mql.removeEventListener('change', handler);
  }, [breakpointPx]);

  return isMobile;
}
