import { useCallback } from 'react';
import { useLocaleStore } from './localeStore';

const FORMATTERS = {
  ru: {
    full: new Intl.DateTimeFormat('ru-RU', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }),
    short: new Intl.DateTimeFormat('ru-RU', {
      day: 'numeric',
      month: 'short',
    }),
  },
  ar: {
    full: new Intl.DateTimeFormat('ar', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }),
    short: new Intl.DateTimeFormat('ar', {
      day: 'numeric',
      month: 'short',
    }),
  },
} as const;

type DateStyle = 'full' | 'short';

/**
 * Hook возвращает форматтер дат для текущей локали интерфейса.
 *
 * Стили:
 * - `full`  - «8 мая 2026 г. в 03:14» / «٨ مايو ٢٠٢٦ في ٠٣:١٤»
 * - `short` - «8 мая» / «٨ مايو»
 *
 * Локаль берётся из useLocaleStore - даты переключаются автоматически
 * при смене UI-локали.
 */
export function useFormatDate(): (iso: string | undefined, style?: DateStyle) => string {
  const locale = useLocaleStore((s) => s.locale);
  // useCallback - стабильный референс пока locale не сменилась. Позволяет
  // включать formatDate в deps useEffect/useMemo без infinite-loop.
  return useCallback(
    (iso, style = 'full') => {
      if (!iso) return '—';
      const d = new Date(iso);
      if (Number.isNaN(d.getTime())) return iso;
      return FORMATTERS[locale][style].format(d);
    },
    [locale],
  );
}
