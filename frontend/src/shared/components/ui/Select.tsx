import { useEffect, useRef, useState } from 'react';
import { Check, ChevronDown } from 'lucide-react';
import { useHotkey } from '@/shared/hooks/useHotkey';

export interface SelectOption {
  value: string;
  label: string;
  /** Optional CSS class на label - для custom typography (например font-naskh для арабских) */
  labelClassName?: string;
  /** RTL direction для конкретной option */
  dir?: 'rtl' | 'ltr';
}

interface Props {
  value: string;
  onChange: (value: string) => void;
  options: ReadonlyArray<SelectOption>;
  /** Размер: sm для inline-toolbar, md для form-style. По умолчанию md */
  size?: 'sm' | 'md';
  /** Класс для trigger-button - чтобы родитель мог настроить width/colors */
  className?: string;
  /** ARIA label для accessibility */
  ariaLabel?: string;
  /** RTL для trigger - по умолчанию ltr */
  dir?: 'rtl' | 'ltr';
  /** Минимальная ширина menu в px (по умолчанию = trigger width) */
  menuMinWidth?: number;
  /** Hint - после Сессии 39 dropdown всегда получает CSS-only `max-h:
   * min(16rem, 50vh)` чтобы корректно скроллиться на любом viewport.
   * Prop остался для совместимости и как data-attribute (тесты могут
   * проверять желаемый count). По умолчанию 12 */
  maxVisibleItems?: number;
}

/**
 * Custom Select - порт из `design-reference/project/dropdown.jsx::Select`.
 * Заменяет native `<select>` где нужен консистентный project styling:
 * - centered text option items (native HTML не центрирует option'ы)
 * - indigo focus ring
 * - hover slate border
 * - ChevronDown с rotation при open
 * - selected check icon
 *
 * Используется в PageJump (Том selector) и PdfViewer (volume selector).
 * Для editable text input (printedPage) - стандартный `<input>` лучше.
 *
 * Closes on outside click (mousedown) и Escape. Auto-scrolls selected
 * option into view при open.
 */
function Select({
  value,
  onChange,
  options,
  size = 'md',
  className = '',
  ariaLabel,
  dir,
  menuMinWidth,
  maxVisibleItems = 12,
}: Props) {
  const [open, setOpen] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLUListElement>(null);

  // Dismiss on outside click - Escape мигрирован на useHotkey ниже
  useEffect(() => {
    if (!open) return;
    const onMouseDown = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onMouseDown);
    return () => {
      document.removeEventListener('mousedown', onMouseDown);
    };
  }, [open]);

  useHotkey('escape', () => setOpen(false), { enabled: open });

  // Auto-scroll selected option в view при open
  useEffect(() => {
    if (!open || !menuRef.current) return;
    const selected = menuRef.current.querySelector('[data-selected="true"]') as HTMLElement | null;
    if (selected) selected.scrollIntoView({ block: 'nearest' });
  }, [open]);

  const selected = options.find((o) => o.value === value);
  const sizeStyles = size === 'sm'
    ? 'h-7 px-2.5 text-xs gap-1.5 rounded-md'
    : 'h-9 px-3 text-sm gap-2 rounded-md';

  return (
    <div ref={wrapperRef} className={`relative inline-block ${className}`} dir={dir}>
      <button
        type="button"
        onClick={() => setOpen(!open)}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={ariaLabel}
        className={`inline-flex w-full items-center justify-between border bg-elevated font-medium text-ink-700 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500/30 ${sizeStyles} ${
          open
            ? 'border-accent-500 ring-2 ring-accent-500/20'
            : 'border-ink-200 hover:border-ink-300'
        }`}
      >
        <span
          className={`flex-1 truncate text-center ${selected?.labelClassName ?? ''}`}
          dir={selected?.dir}
        >
          {selected?.label ?? ''}
        </span>
        <ChevronDown
          size={size === 'sm' ? 12 : 14}
          className={`shrink-0 text-ink-400 transition-transform ${open ? 'rotate-180 text-ink-600' : ''}`}
          aria-hidden="true"
        />
      </button>

      {open && (
        <ul
          ref={menuRef}
          role="listbox"
          // max-h использует `min(16rem, 50vh)` - на desktop стандартный
          // 16rem (256px) ≈ 12 опций ×~22px. На mobile / коротком viewport
          // 50vh не даёт menu вылезти за экран. overflow-y-auto всегда
          // активен - cheaper чем conditional class, browsers handle гладко
          // (scrollbar appearance только при реальном overflow)
          className="absolute inset-x-0 z-40 mt-1.5 max-h-[min(16rem,50vh)] overflow-y-auto rounded-md border border-border bg-elevated py-1 shadow-sh3"
          style={{ minWidth: menuMinWidth ?? undefined }}
          data-max-visible={maxVisibleItems}
        >
          {options.map((o) => {
            const isSelected = o.value === value;
            return (
              <li key={o.value} role="option" aria-selected={isSelected}>
                <button
                  type="button"
                  data-selected={isSelected}
                  onClick={() => {
                    onChange(o.value);
                    setOpen(false);
                  }}
                  className={`flex w-full items-center justify-center gap-2 px-2.5 py-1.5 text-sm transition-colors ${
                    isSelected
                      ? 'bg-accent-50 text-accent-700 font-semibold hover:bg-accent-100'
                      : 'text-ink-700 hover:bg-ink-200 active:bg-ink-200'
                  }`}
                >
                  <span
                    className={`flex-1 truncate text-center ${o.labelClassName ?? ''}`}
                    dir={o.dir}
                  >
                    {o.label}
                  </span>
                  {isSelected && <Check size={12} className="shrink-0 text-accent-600" aria-hidden="true" />}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

export default Select;
