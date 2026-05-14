import { useEffect, useRef, useState } from 'react';
import { Check, ChevronDown } from 'lucide-react';

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
  /** Сколько опций показывать без scrollbar. >: dropdown получает max-h
   * + overflow-y-auto. ≤: без max-h, без scrollbar (опции уместаются).
   * По умолчанию 12 - для большинства dropdown'ов (тома, фильтры) хватает */
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

  // Dismiss on outside click + Escape
  useEffect(() => {
    if (!open) return;
    const onMouseDown = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onMouseDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onMouseDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  // Auto-scroll selected option в view при open
  useEffect(() => {
    if (!open || !menuRef.current) return;
    const selected = menuRef.current.querySelector('[data-selected="true"]') as HTMLElement | null;
    if (selected) selected.scrollIntoView({ block: 'nearest' });
  }, [open]);

  const selected = options.find((o) => o.value === value);
  const sizeStyles = size === 'sm'
    ? 'h-7 px-2.5 text-[12px] gap-1.5 rounded-md'
    : 'h-9 px-3 text-[13px] gap-2 rounded-md';

  return (
    <div ref={wrapperRef} className={`relative inline-block ${className}`} dir={dir}>
      <button
        type="button"
        onClick={() => setOpen(!open)}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={ariaLabel}
        className={`inline-flex w-full items-center justify-between border bg-white font-medium text-slate-700 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500/30 ${sizeStyles} ${
          open
            ? 'border-indigo-500 ring-2 ring-indigo-500/20'
            : 'border-slate-300 hover:border-slate-400'
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
          className={`shrink-0 text-slate-400 transition-transform ${open ? 'rotate-180 text-slate-600' : ''}`}
          aria-hidden="true"
        />
      </button>

      {open && (
        <ul
          ref={menuRef}
          role="listbox"
          className={`absolute inset-x-0 z-40 mt-1.5 rounded-md border border-slate-200 bg-white py-1 shadow-lg ring-1 ring-black/[0.04] ${options.length > maxVisibleItems ? 'max-h-64 overflow-y-auto' : ''}`}
          style={{ minWidth: menuMinWidth ?? undefined }}
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
                  className={`flex w-full items-center justify-center gap-2 px-2.5 py-1.5 text-[13px] transition-colors ${
                    isSelected
                      ? 'bg-indigo-50 text-indigo-800 font-semibold'
                      : 'text-slate-700 hover:bg-slate-100 active:bg-slate-200'
                  }`}
                >
                  <span
                    className={`flex-1 truncate text-center ${o.labelClassName ?? ''}`}
                    dir={o.dir}
                  >
                    {o.label}
                  </span>
                  {isSelected && <Check size={12} className="shrink-0 text-indigo-600" aria-hidden="true" />}
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
