import { create } from 'zustand';

/**
 * Mode - user preference. `system` означает "следовать ОС" - effective
 * theme вычисляется из `prefers-color-scheme`. `light` / `dark` -
 * явный override игнорирующий системную настройку.
 */
export type ThemeMode = 'system' | 'light' | 'dark';

/** Effective theme - то что реально применяется (после resolution `system`). */
export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'app.theme';

/** Чтение persisted mode. По умолчанию `system` - уважает ОС */
function readPersistedMode(): ThemeMode {
  if (typeof window === 'undefined') return 'system';
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (raw === 'light' || raw === 'dark' || raw === 'system') return raw;
  // Legacy миграция: до Сессии 41 хранили только 'light'/'dark', формат
  // совместим - старое значение становится явным override (не теряем выбор)
  return 'system';
}

/** Resolve mode → effective theme. `system` читает prefers-color-scheme */
function computeEffective(mode: ThemeMode): Theme {
  if (mode === 'dark') return 'dark';
  if (mode === 'light') return 'light';
  if (typeof window === 'undefined') return 'light';
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

interface ThemeState {
  /** User preference (что выбрано в UI) */
  mode: ThemeMode;
  /** Computed - light или dark после resolution `system`. Используется ThemeEffect */
  effectiveTheme: Theme;
  /** Сменить user preference - persist + recompute effective */
  setMode: (m: ThemeMode) => void;
  /** Внутренний - вызывается system listener на change prefers-color-scheme */
  _recomputeEffective: () => void;
  /**
   * Legacy 2-state API для совместимости. CommandPalette использует
   * toggle() / theme. `theme` отдаёт effectiveTheme, toggle переключает
   * mode между light/dark (даже если был system - явный выбор)
   */
  theme: Theme;
  toggle: () => void;
  /** @deprecated используй setMode. Оставлено для FontSettings совместимости */
  setTheme: (t: Theme) => void;
}

/**
 * Zustand store для темы UI (system/light/dark). Persist в localStorage
 * под ключом `app.theme`. На первой загрузке без сохранения - `system`
 * (уважает prefers-color-scheme).
 *
 * 3 опции (Сессия 41):
 * - `system` - следовать ОС через matchMedia subscribe
 * - `light` / `dark` - явный override
 *
 * Side-effect (применение data-theme="dark" на <html>) делает компонент
 * ThemeEffect (см. shared/components/ThemeEffect.tsx). Семантические
 * токены --c-bg / --c-text / --c-border переключаются автоматически.
 *
 * FOUC prevention - inline script в index.html читает тот же ключ.
 * Если меняешь STORAGE_KEY или ключи в localStorage - синхронизировать
 * с index.html script (single source of truth).
 */
export const useThemeStore = create<ThemeState>((set, get) => {
  const initialMode = readPersistedMode();
  const initialEffective = computeEffective(initialMode);

  // System change listener - живёт всё время приложения, не отписываемся
  // (store singleton, live as long as page). Активирует только при mode=system
  if (typeof window !== 'undefined' && window.matchMedia) {
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = () => {
      if (get().mode === 'system') {
        get()._recomputeEffective();
      }
    };
    // Современные браузеры (Chrome 89+, Firefox 86+, Safari 14+) поддерживают
    // addEventListener. Старый Safari ≤14 - addListener (deprecated, в
    // нашем target browser matrix не входит, поэтому не добавляем fallback).
    mq.addEventListener?.('change', onChange);
  }

  return {
    mode: initialMode,
    effectiveTheme: initialEffective,
    theme: initialEffective,
    setMode: (m) => {
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(STORAGE_KEY, m);
      }
      const eff = computeEffective(m);
      set({ mode: m, effectiveTheme: eff, theme: eff });
    },
    _recomputeEffective: () => {
      const eff = computeEffective(get().mode);
      set({ effectiveTheme: eff, theme: eff });
    },
    toggle: () => {
      // Legacy: toggle переключает между light↔dark. Если был system - смотрим
      // что сейчас effective и переключаем на противоположное (user явно
      // зафиксировал выбор кликом)
      const cur = get().effectiveTheme;
      const next: ThemeMode = cur === 'dark' ? 'light' : 'dark';
      get().setMode(next);
    },
    setTheme: (t) => {
      get().setMode(t);
    },
  };
});
