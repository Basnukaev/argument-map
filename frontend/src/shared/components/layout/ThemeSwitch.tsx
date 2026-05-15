import { Moon, Sun } from 'lucide-react';
import { useThemeStore } from '@/shared/stores/themeStore';

/**
 * Маленький переключатель темы light/dark - иконка-кнопка Sun/Moon.
 * Persist через zustand store + localStorage (см. themeStore).
 * ThemeEffect синхронизирует `<html data-theme>`.
 *
 * Аналог LocaleSwitch для темы. Используется в AppHeader.
 */
function ThemeSwitch() {
  const theme = useThemeStore((s) => s.theme);
  const toggle = useThemeStore((s) => s.toggle);

  const isDark = theme === 'dark';
  const label = isDark ? 'Переключить на светлую тему' : 'Переключить на тёмную тему';

  return (
    <button
      type="button"
      onClick={toggle}
      title={label}
      aria-label={label}
      aria-pressed={isDark}
      className="inline-flex h-7 w-7 items-center justify-center rounded-sm border border-ink-200 bg-elevated text-ink-700 hover:bg-ink-100 hover:text-ink-900 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 focus-visible:ring-offset-1 focus-visible:ring-offset-bg"
    >
      {isDark ? <Moon size={14} aria-hidden /> : <Sun size={14} aria-hidden />}
    </button>
  );
}

export default ThemeSwitch;
