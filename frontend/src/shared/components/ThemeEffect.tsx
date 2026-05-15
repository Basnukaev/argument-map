import { useEffect } from 'react';
import { useThemeStore } from '@/shared/stores/themeStore';

/**
 * Side-effect компонент, синхронизирующий zustand theme store с
 * `<html data-theme>`. На смене темы выставляет/убирает атрибут на
 * корневом <html>:
 *
 * - light: `<html>` (без data-theme)
 * - dark:  `<html data-theme="dark">`
 *
 * Все семантические токены в tokens.css (--c-bg, --c-text, --c-border
 * и пр.) переключаются автоматически благодаря селектору [data-theme="dark"].
 * Render nothing.
 */
export function ThemeEffect() {
  const theme = useThemeStore((s) => s.theme);
  useEffect(() => {
    const html = document.documentElement;
    if (theme === 'dark') {
      html.setAttribute('data-theme', 'dark');
    } else {
      html.removeAttribute('data-theme');
    }
  }, [theme]);
  return null;
}
