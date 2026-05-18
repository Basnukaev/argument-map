import { useEffect } from 'react';
import { usePreferencesStore } from '@/shared/stores/preferencesStore';
import { useAuthStore } from '@/shared/stores/authStore';
import { useLocaleStore } from '@/shared/i18n';
import { useThemeStore } from '@/shared/stores/themeStore';

/**
 * Side-effect: применяет user-preferences к остальным сторам и CSS
 * variables. На login - подтягивает с бэка; на logout - сбрасывает
 * локальный кэш.
 *
 * Применение:
 *   - locale → useLocaleStore.setLocale (LocaleEffect ставит <html lang dir>)
 *   - theme → useThemeStore.setMode (ThemeEffect применит data-theme)
 *   - textSize → CSS variable --text-size-scale (root font-size scale)
 *   - arabicFont → CSS variable --font-arabic-pref
 *   - hideTashkeelByDefault → атрибут data-tashkeel-hidden (за PageView toggle)
 *   - transliteration → атрибут data-transliteration на root
 *
 * Render: null.
 */
export function PreferencesEffect() {
  const user = useAuthStore((s) => s.user);
  const initialized = useAuthStore((s) => s.initialized);
  const loadFromBackend = usePreferencesStore((s) => s.loadFromBackend);
  const resetLocal = usePreferencesStore((s) => s.resetLocal);
  const loaded = usePreferencesStore((s) => s.loaded);

  const locale = usePreferencesStore((s) => s.locale);
  const arabicFont = usePreferencesStore((s) => s.arabicFont);
  const textSize = usePreferencesStore((s) => s.textSize);
  const hideTashkeel = usePreferencesStore((s) => s.hideTashkeelByDefault);
  const transliteration = usePreferencesStore((s) => s.transliteration);
  const theme = usePreferencesStore((s) => s.theme);

  const setLocale = useLocaleStore((s) => s.setLocale);
  const setThemeMode = useThemeStore((s) => s.setMode);

  // Sync с бэком на login. На logout - resetLocal.
  useEffect(() => {
    if (!initialized) return;
    if (user) {
      // Если ещё не подгружали (или сменился user) - load
      if (!loaded) {
        void loadFromBackend();
      }
    } else if (loaded) {
      // User logged out - reset local cache
      resetLocal();
    }
  }, [user, initialized, loaded, loadFromBackend, resetLocal]);

  // Apply locale → locale store (ru/ar только - LocaleStore не знает 'en'
  // пока не добавим в DICTIONARY). Для en сейчас fallback на ru
  useEffect(() => {
    if (locale === 'ar') setLocale('ar');
    else setLocale('ru');
    // en пока остаётся на ru как fallback до расширения DICTIONARY
  }, [locale, setLocale]);

  // Apply theme → theme store
  useEffect(() => {
    setThemeMode(theme);
  }, [theme, setThemeMode]);

  // Apply CSS variables / data attributes
  useEffect(() => {
    const html = document.documentElement;

    // textSize → root font-size scale (1 = 16px baseline)
    const scaleMap: Record<typeof textSize, string> = {
      small: '0.875',
      medium: '1',
      large: '1.125',
      xl: '1.25',
    };
    html.style.setProperty('--text-size-scale', scaleMap[textSize]);

    // arabicFont → --font-arabic-pref. Реальные стеки:
    const arabicMap: Record<typeof arabicFont, string> = {
      naskh: "'Amiri', 'Noto Naskh Arabic', serif",
      kufi: "'Reem Kufi', 'Amiri', serif",
      tahoma: "'Tahoma', 'Amiri', sans-serif",
    };
    html.style.setProperty('--font-arabic-pref', arabicMap[arabicFont]);

    // data attributes для условных стилей
    html.dataset.tashkeelHidden = hideTashkeel ? 'true' : 'false';
    html.dataset.transliteration = transliteration ? 'on' : 'off';
  }, [textSize, arabicFont, hideTashkeel, transliteration]);

  return null;
}
