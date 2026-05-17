import { useEffect } from 'react';
import { useThemeStore } from '@/shared/stores/themeStore';

/**
 * Side-effect компонент, синхронизирующий zustand theme store с
 * `<html data-theme>`. На смене effectiveTheme выставляет/убирает
 * атрибут на корневом <html>:
 *
 * - light: `<html>` (без data-theme)
 * - dark:  `<html data-theme="dark">`
 *
 * Все семантические токены в tokens.css (--c-bg, --c-text, --c-border
 * и пр.) переключаются автоматически благодаря селектору [data-theme="dark"].
 * Tiptap custom extensions (HadithBox / AyahBox / Marginalia / etc) тоже
 * используют этот атрибут, не prefers-color-scheme - иначе manual override
 * в light theme на dark системе показывал бы dark variants extensions.
 *
 * Render nothing.
 */
export function ThemeEffect() {
  const effective = useThemeStore((s) => s.effectiveTheme);
  useEffect(() => {
    const html = document.documentElement;
    if (effective === 'dark') {
      html.setAttribute('data-theme', 'dark');
    } else {
      html.removeAttribute('data-theme');
    }
  }, [effective]);
  return null;
}
