import { useEffect, useRef, useState } from 'react';
import { Check, Monitor, Moon, Sun } from 'lucide-react';
import { useThemeStore, type ThemeMode } from '@/shared/stores/themeStore';
import { useT, type DictKey } from '@/shared/i18n';

interface Option {
  mode: ThemeMode;
  labelKey: DictKey;
  Icon: typeof Sun;
}

const OPTIONS: ReadonlyArray<Option> = [
  { mode: 'system', labelKey: 'theme.system', Icon: Monitor },
  { mode: 'light', labelKey: 'theme.light', Icon: Sun },
  { mode: 'dark', labelKey: 'theme.dark', Icon: Moon },
];

/**
 * Dropdown переключатель темы с 3 опциями: System / Light / Dark.
 *
 * Trigger - icon-кнопка (Monitor/Sun/Moon в зависимости от текущего mode).
 * Click открывает маленький popover со всеми тремя вариантами + текущий
 * отмечен галочкой Check. ESC и outside-click закрывают.
 *
 * `system` mode уважает `prefers-color-scheme` ОС - effective theme
 * пересчитывается через matchMedia listener в themeStore.
 *
 * Используется в AppHeader рядом с LocaleSwitch.
 */
function ThemeSwitch() {
  const t = useT();
  const mode = useThemeStore((s) => s.mode);
  const setMode = useThemeStore((s) => s.setMode);
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Outside click + ESC закрывают popover. Современный pattern -
  // listener на document; снимаем при close или unmount
  useEffect(() => {
    if (!open) return;
    function onDocClick(e: MouseEvent) {
      if (!containerRef.current?.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false);
    }
    document.addEventListener('mousedown', onDocClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDocClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const currentOption = OPTIONS.find((o) => o.mode === mode) ?? OPTIONS[0]!;
  const TriggerIcon = currentOption.Icon;
  const triggerLabel = t('theme.aria_label');

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        title={triggerLabel}
        aria-label={triggerLabel}
        aria-haspopup="menu"
        aria-expanded={open}
        className="inline-flex h-7 w-7 items-center justify-center rounded-sm border border-ink-200 bg-elevated text-ink-700 hover:bg-ink-100 hover:text-ink-900 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 focus-visible:ring-offset-1 focus-visible:ring-offset-bg"
      >
        <TriggerIcon size={14} aria-hidden />
      </button>

      {open && (
        <div
          role="menu"
          aria-label={t('theme.menu_label')}
          className="absolute end-0 top-9 z-50 min-w-[180px] rounded-md border border-border bg-elevated p-1 shadow-sh3"
        >
          {OPTIONS.map((opt) => {
            const Icon = opt.Icon;
            const active = mode === opt.mode;
            return (
              <button
                key={opt.mode}
                type="button"
                role="menuitemradio"
                aria-checked={active}
                onClick={() => {
                  setMode(opt.mode);
                  setOpen(false);
                }}
                className={`flex w-full items-center gap-2 rounded-sm px-2.5 py-1.5 text-start text-sm transition-colors ${
                  active
                    ? 'bg-accent-50 text-accent-700'
                    : 'text-ink-700 hover:bg-ink-100 hover:text-ink-900'
                }`}
              >
                <Icon size={14} aria-hidden />
                <span className="flex-1">{t(opt.labelKey)}</span>
                {active && <Check size={14} aria-hidden />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default ThemeSwitch;
