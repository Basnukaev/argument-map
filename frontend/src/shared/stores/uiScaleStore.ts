import { create } from 'zustand';

/**
 * Масштаб интерфейса (interface zoom). Применяется через
 * `document.documentElement.style.fontSize` (UiScaleEffect) - весь UI на
 * Tailwind rem-based, поэтому изменение base font-size масштабирует
 * всё: nav, кнопки, карточки, узлы графа, reader. Это глобальный zoom
 * by design.
 *
 * Дефолт - 'compact' (0.9): владелец просил весь UI ~10% меньше базового
 * (баг #2). Но 'standard' (1.0 = базовый) всегда доступен как
 * one-click rollback ("не руби с плеча") - сброс настроек тоже
 * возвращает к compact-дефолту, а не к standard.
 *
 * Persist в localStorage под `app.uiScale` (зеркалит fontPairStore /
 * themeStore ручной persist стиль). FOUC: первая отрисовка может
 * мигнуть базовым размером до того как UiScaleEffect отработает -
 * приемлемо (в отличие от темы это не цветовой flash, а лёгкое
 * изменение размера; inline-script усложнять не стали).
 */
export type UiScalePreset = 'compact' | 'standard' | 'comfortable';

/** Множители для каждого пресета. standard = 1.0 = базовый rem (16px). */
export const UI_SCALE_VALUES: Record<UiScalePreset, number> = {
  compact: 0.9,
  standard: 1.0,
  comfortable: 1.1,
};

/** Базовый размер шрифта <html> в px, который пресет домножает. */
export const BASE_FONT_SIZE_PX = 16;

/** Дефолт - compact (владелец предпочитает ~10% меньше базового). */
export const DEFAULT_UI_SCALE: UiScalePreset = 'compact';

const STORAGE_KEY = 'app.uiScale';

function isPreset(value: string): value is UiScalePreset {
  return value === 'compact' || value === 'standard' || value === 'comfortable';
}

function readPersistedScale(): UiScalePreset {
  if (typeof window === 'undefined') return DEFAULT_UI_SCALE;
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (raw && isPreset(raw)) return raw;
  return DEFAULT_UI_SCALE;
}

interface UiScaleState {
  scale: UiScalePreset;
  setScale: (s: UiScalePreset) => void;
  /** Сброс к дефолту (compact). Удаляет persisted значение. */
  reset: () => void;
}

export const useUiScaleStore = create<UiScaleState>((set) => ({
  scale: readPersistedScale(),
  setScale: (s) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY, s);
    }
    set({ scale: s });
  },
  reset: () => {
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem(STORAGE_KEY);
    }
    set({ scale: DEFAULT_UI_SCALE });
  },
}));

/** Множитель для текущего пресета - утилита для эффекта/превью. */
export function scaleMultiplier(preset: UiScalePreset): number {
  return UI_SCALE_VALUES[preset];
}
