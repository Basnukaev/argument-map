import { useCallback } from 'react';
import { useLocaleStore } from './localeStore';
import { DICTIONARY, type DictKey } from './dictionary';

/**
 * Hook для translation. Returns функция `t(key)` которая возвращает
 * перевод для текущей локали из zustand store.
 *
 * Возвращаемый t стабилен по референсу пока локаль не сменилась
 * (через useCallback) - это позволяет включать t в deps useEffect/
 * useMemo без infinite-loop проблем.
 *
 * Usage:
 *   const t = useT();
 *   <Chip>{t('cite.chip.library')}</Chip>
 */
export function useT(): (key: DictKey) => string {
  const locale = useLocaleStore((s) => s.locale);
  return useCallback((key: DictKey) => DICTIONARY[locale][key], [locale]);
}
