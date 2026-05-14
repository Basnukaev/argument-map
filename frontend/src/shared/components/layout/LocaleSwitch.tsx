import { Languages } from 'lucide-react';
import { useLocaleStore } from '@/shared/i18n';
import type { Locale } from '@/shared/i18n';

/**
 * Маленький RU/AR переключатель локали. Кликабельный chip с двумя сегментами
 * - active highlighted indigo. Persist через zustand store + localStorage,
 * LocaleEffect синхронизирует `<html dir lang>`
 */
function LocaleSwitch() {
  const locale = useLocaleStore((s) => s.locale);
  const setLocale = useLocaleStore((s) => s.setLocale);

  const options: { value: Locale; label: string }[] = [
    { value: 'ru', label: 'RU' },
    { value: 'ar', label: 'AR' },
  ];

  return (
    <div
      className="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-white px-1.5 py-1 text-[11px] font-semibold"
      role="group"
      aria-label="Локаль интерфейса"
      dir="ltr"
    >
      <Languages size={12} className="text-slate-400" aria-hidden />
      {options.map((opt) => {
        const active = opt.value === locale;
        return (
          <button
            key={opt.value}
            type="button"
            onClick={() => setLocale(opt.value)}
            aria-pressed={active}
            className={
              active
                ? 'rounded bg-indigo-600 px-1.5 py-0.5 text-white'
                : 'rounded px-1.5 py-0.5 text-slate-600 hover:bg-slate-100'
            }
          >
            {opt.label}
          </button>
        );
      })}
    </div>
  );
}

export default LocaleSwitch;
