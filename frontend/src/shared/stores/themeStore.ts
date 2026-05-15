import { create } from 'zustand';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'app.theme';

function readPersistedTheme(): Theme {
  if (typeof window === 'undefined') return 'light';
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (raw === 'light' || raw === 'dark') return raw;
  // Если ничего не сохранено - уважаем системные настройки пользователя
  if (window.matchMedia?.('(prefers-color-scheme: dark)').matches) return 'dark';
  return 'light';
}

interface ThemeState {
  theme: Theme;
  setTheme: (t: Theme) => void;
  toggle: () => void;
}

/**
 * Zustand store для текущей темы UI (light/dark). Persist в localStorage
 * чтобы выбор сохранялся между сессиями. На первой загрузке без сохранения -
 * уважает prefers-color-scheme.
 *
 * Side-effect (применение data-theme="dark" на <html>) делает компонент
 * ThemeEffect (см. components/ThemeEffect.tsx). Семантические токены
 * --c-bg / --c-text / --c-border переключаются автоматически.
 */
export const useThemeStore = create<ThemeState>((set, get) => ({
  theme: readPersistedTheme(),
  setTheme: (t) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY, t);
    }
    set({ theme: t });
  },
  toggle: () => {
    const next: Theme = get().theme === 'dark' ? 'light' : 'dark';
    get().setTheme(next);
  },
}));
