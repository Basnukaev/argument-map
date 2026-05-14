import { useEffect } from 'react';
import { useLocaleStore } from './localeStore';

/**
 * Side-effect компонент, который синхронизирует zustand locale store
 * с `<html lang dir>`. На smene locale - меняет атрибуты на root <html>:
 * - ru: `<html lang="ru" dir="ltr">`
 * - ar: `<html lang="ar" dir="rtl">`
 *
 * Все RTL Tailwind logical classes (ms-/me-/text-start/border-s-) сами
 * адаптируются под `<html dir="rtl">`. Render nothing
 */
export function LocaleEffect() {
  const locale = useLocaleStore((s) => s.locale);
  useEffect(() => {
    const html = document.documentElement;
    html.setAttribute('lang', locale);
    html.setAttribute('dir', locale === 'ar' ? 'rtl' : 'ltr');
  }, [locale]);
  return null;
}
