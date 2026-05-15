import { useCallback } from 'react';
import { useLocaleStore } from './localeStore';

const FORMATTERS = {
  ru: new Intl.NumberFormat('ru-RU'),
  ar: new Intl.NumberFormat('ar'),
} as const;

/**
 * Hook возвращает форматтер чисел для текущей локали интерфейса.
 *
 *   ru: 8 589 / 3 187 / 1 261
 *   ar: ٨٬٥٨٩ / ٣٬١٨٧ / ١٬٢٦١
 *
 * Стабилен через useCallback - безопасно включать в deps.
 */
export function useNumberFormat(): (n: number | undefined | null) => string {
  const locale = useLocaleStore((s) => s.locale);
  return useCallback(
    (n) => {
      if (n == null) return '—';
      return FORMATTERS[locale].format(n);
    },
    [locale],
  );
}
