import { useLocaleStore } from './localeStore';
import { DICTIONARY, type DictKey } from './dictionary';

/**
 * Hook для translation. Returns функция `t(key)` которая возвращает
 * перевод для текущей локали из zustand store.
 *
 * Usage:
 *   const t = useT();
 *   <Chip>{t('cite.chip.library')}</Chip>
 */
export function useT(): (key: DictKey) => string {
  const locale = useLocaleStore((s) => s.locale);
  return (key: DictKey) => DICTIONARY[locale][key];
}
