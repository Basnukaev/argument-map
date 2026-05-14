import { create } from 'zustand';
import type { Locale } from './dictionary';

interface LocaleState {
  locale: Locale;
  setLocale: (l: Locale) => void;
}

/**
 * Zustand store для текущей локали UI. Default `ru` - проект на русском
 * сейчас. Переключатель в UI добавится в будущем (Этап 21+ multi-user).
 *
 * Когда setLocale('ar') - вместе с переключением словаря нужно ставить
 * `<html dir="rtl">` на root (через `useEffect` в LocaleProvider или
 * вручную в Header). Logical Tailwind classes (ms-/me-/text-start) во
 * всех citation-related компонентах работают в обоих направлениях
 */
export const useLocaleStore = create<LocaleState>((set) => ({
  locale: 'ru',
  setLocale: (l) => set({ locale: l }),
}));
