import { Languages } from 'lucide-react';
import { useLocaleStore, useT } from '@/shared/i18n';
import type { Locale } from '@/shared/i18n';

/**
 * Маленький RU/AR переключатель локали. Сегментированный chip с двумя
 * пунктами - active highlighted accent. Persist через zustand store +
 * localStorage. LocaleEffect синхронизирует `<html lang dir>`.
 *
 * Использует v2 design tokens (ink-/accent-/elevated/border) -
 * автоматически переключается в dark theme.
 */
function LocaleSwitch() {
  const t = useT();
  const locale = useLocaleStore((s) => s.locale);
  const setLocale = useLocaleStore((s) => s.setLocale);

  const options: { value: Locale; label: string }[] = [
    { value: 'ru', label: 'RU' },
    { value: 'ar', label: 'AR' },
  ];

  return (
    <div
      className="inline-flex items-center gap-1 rounded-sm border border-ink-200 bg-elevated px-1.5 py-1 text-xs font-semibold"
      role="group"
      aria-label={t('common.locale_switch')}
      dir="ltr"
    >
      <Languages size={12} className="text-ink-400" aria-hidden />
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
                ? 'rounded-sm bg-accent-600 px-1.5 py-0.5 text-ink-0'
                : 'rounded-sm px-1.5 py-0.5 text-ink-600 hover:bg-ink-100'
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
