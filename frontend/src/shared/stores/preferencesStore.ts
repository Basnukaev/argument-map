import { create } from 'zustand';
import {
  apiDeleteRaw,
  apiGetRaw,
  ApiError,
  apiPutRaw,
} from '@/shared/api/client';

/**
 * Settings screen - user-preferences с persistance на бэке (Backend
 * Этап 42). LocalStorage используется как pre-paint cache: на reload
 * рендерим из localStorage сразу (без FOUC), затем asynchronously
 * подтягиваем с бэка - если значения разошлись (другое устройство),
 * backend wins.
 *
 * Whitelisted keys (валидация на бэке тоже):
 *   - locale: 'ru' | 'ar' | 'en'
 *   - arabicFont: 'naskh' | 'kufi' | 'tahoma' (применяется к --font-arabic)
 *   - textSize: 'small' | 'medium' | 'large' | 'xl'
 *   - hideTashkeelByDefault: boolean
 *   - transliteration: boolean
 *   - theme: 'system' | 'light' | 'dark'
 *
 * Применение к UI - отдельные эффекты:
 *   - locale → useLocaleStore.setLocale (LocaleEffect ставит <html lang dir>)
 *   - theme → useThemeStore.setMode
 *   - textSize / arabicFont / tashkeel / translit → CSS variables на root
 *     через PreferencesEffect
 */

export type LocalePref = 'ru' | 'ar' | 'en';
export type ArabicFontPref = 'naskh' | 'kufi' | 'tahoma';
export type TextSizePref = 'small' | 'medium' | 'large' | 'xl';
export type ThemePref = 'system' | 'light' | 'dark';

export interface Preferences {
  locale: LocalePref;
  arabicFont: ArabicFontPref;
  textSize: TextSizePref;
  hideTashkeelByDefault: boolean;
  transliteration: boolean;
  theme: ThemePref;
}

const DEFAULT_PREFERENCES: Preferences = {
  locale: 'ru',
  arabicFont: 'naskh',
  textSize: 'medium',
  hideTashkeelByDefault: false,
  transliteration: false,
  theme: 'system',
};

const STORAGE_KEY = 'app.preferences';

/**
 * Читаем cached значения - чтобы при reload не было flash дефолтных
 * настроек пока бэк отвечает.
 */
function readCached(): Preferences {
  if (typeof window === 'undefined') return { ...DEFAULT_PREFERENCES };
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) return { ...DEFAULT_PREFERENCES };
  try {
    const parsed = JSON.parse(raw) as Partial<Preferences>;
    return mergeWithDefaults(parsed);
  } catch {
    return { ...DEFAULT_PREFERENCES };
  }
}

function persistCache(prefs: Preferences): void {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs));
}

function mergeWithDefaults(partial: Partial<Preferences>): Preferences {
  return {
    locale: isLocale(partial.locale) ? partial.locale : DEFAULT_PREFERENCES.locale,
    arabicFont: isArabicFont(partial.arabicFont)
      ? partial.arabicFont
      : DEFAULT_PREFERENCES.arabicFont,
    textSize: isTextSize(partial.textSize)
      ? partial.textSize
      : DEFAULT_PREFERENCES.textSize,
    hideTashkeelByDefault:
      typeof partial.hideTashkeelByDefault === 'boolean'
        ? partial.hideTashkeelByDefault
        : DEFAULT_PREFERENCES.hideTashkeelByDefault,
    transliteration:
      typeof partial.transliteration === 'boolean'
        ? partial.transliteration
        : DEFAULT_PREFERENCES.transliteration,
    theme: isTheme(partial.theme) ? partial.theme : DEFAULT_PREFERENCES.theme,
  };
}

function isLocale(v: unknown): v is LocalePref {
  return v === 'ru' || v === 'ar' || v === 'en';
}
function isArabicFont(v: unknown): v is ArabicFontPref {
  return v === 'naskh' || v === 'kufi' || v === 'tahoma';
}
function isTextSize(v: unknown): v is TextSizePref {
  return v === 'small' || v === 'medium' || v === 'large' || v === 'xl';
}
function isTheme(v: unknown): v is ThemePref {
  return v === 'system' || v === 'light' || v === 'dark';
}

interface PreferencesState extends Preferences {
  isLoading: boolean;
  /** успел ли хотя бы раз отработать loadFromBackend */
  loaded: boolean;
  loadFromBackend: () => Promise<void>;
  set: <K extends keyof Preferences>(key: K, value: Preferences[K]) => Promise<void>;
  /** Сбросить все ключи на дефолты (DELETE на бэке + local). */
  resetAll: () => Promise<void>;
  /** Сброс state без backend-вызовов - на logout. */
  resetLocal: () => void;
}

/**
 * Zustand store user-preferences. Backend wins на load; optimistic update
 * на set с revert при ошибке. Persist в localStorage для FOUC prevention.
 */
export const usePreferencesStore = create<PreferencesState>((set, get) => ({
  ...readCached(),
  isLoading: false,
  loaded: false,

  async loadFromBackend() {
    set({ isLoading: true });
    try {
      const raw = await apiGetRaw<Record<string, unknown>>('/api/v1/preferences');
      const merged = mergeWithDefaults(raw as Partial<Preferences>);
      persistCache(merged);
      set({ ...merged, isLoading: false, loaded: true });
    } catch (e) {
      // 401 - не залогинен, остаёмся на cached/defaults
      if (!(e instanceof ApiError) || e.status !== 401) {
        // другой код - просто не подгрузили
      }
      set({ isLoading: false, loaded: true });
    }
  },

  async set(key, value) {
    const prev = get()[key];
    // optimistic update + cache
    const current = readCurrentPrefs(get());
    const next = { ...current, [key]: value };
    persistCache(next);
    set({ [key]: value } as Pick<Preferences, typeof key>);
    try {
      await apiPutRaw(`/api/v1/preferences/${key}`, { value });
    } catch (e) {
      // revert при ошибке
      const reverted = { ...next, [key]: prev };
      persistCache(reverted);
      set({ [key]: prev } as Pick<Preferences, typeof key>);
      throw e;
    }
  },

  async resetAll() {
    const keys: (keyof Preferences)[] = [
      'locale',
      'arabicFont',
      'textSize',
      'hideTashkeelByDefault',
      'transliteration',
      'theme',
    ];
    // параллельно удаляем все - если что-то упадёт, defaults всё равно
    // подставятся локально через mergeWithDefaults
    await Promise.allSettled(
      keys.map((k) => apiDeleteRaw(`/api/v1/preferences/${k}`)),
    );
    persistCache({ ...DEFAULT_PREFERENCES });
    set({ ...DEFAULT_PREFERENCES });
  },

  resetLocal() {
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem(STORAGE_KEY);
    }
    set({ ...DEFAULT_PREFERENCES, loaded: false });
  },
}));

/** Внутренний helper для текущих prefs из state (без isLoading/loaded). */
function readCurrentPrefs(s: PreferencesState): Preferences {
  return {
    locale: s.locale,
    arabicFont: s.arabicFont,
    textSize: s.textSize,
    hideTashkeelByDefault: s.hideTashkeelByDefault,
    transliteration: s.transliteration,
    theme: s.theme,
  };
}
