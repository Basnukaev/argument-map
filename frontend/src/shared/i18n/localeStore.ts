import { create } from 'zustand';
import type { Locale } from './dictionary';

const STORAGE_KEY = 'app.locale';

function readPersistedLocale(): Locale {
  if (typeof window === 'undefined') return 'ru';
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (raw === 'ru' || raw === 'ar') return raw;
  return 'ru';
}

interface LocaleState {
  locale: Locale;
  setLocale: (l: Locale) => void;
}

/**
 * Zustand store для текущей локали UI. Persist в localStorage чтобы
 * выбор сохранялся между сессиями. На mount при setLocale - LocaleEffect
 * (см. LocaleEffect.tsx) применяет `<html lang dir>` для CSS direction
 * inheritance во весь UI tree
 */
export const useLocaleStore = create<LocaleState>((set) => ({
  locale: readPersistedLocale(),
  setLocale: (l) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY, l);
    }
    set({ locale: l });
  },
}));
