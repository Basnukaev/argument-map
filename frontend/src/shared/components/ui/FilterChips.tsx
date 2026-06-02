export interface FilterChipOption {
  value: string;
  label: string;
  /** Опциональный счётчик - рендерится тонким бейджем внутри пилюли. */
  count?: number;
}

interface Props {
  options: ReadonlyArray<FilterChipOption>;
  /** Активное значение. null = ни один чип не активен. */
  value: string | null;
  onChange: (value: string) => void;
  ariaLabel?: string;
  /** Доп. классы на внешнюю обёртку (ряд). */
  className?: string;
}

/**
 * FilterChips - ряд пилюль-фильтров. Активная пилюля = indigo-заливка,
 * неактивная = нейтральная поверхность. Опциональный count показывается
 * тонким бейджем.
 *
 * Layout: ряд `overflow-x-auto` со скрытым скроллбаром (scrollbar-hide) -
 * на mobile если пилюли не помещаются, ряд скроллится горизонтально без
 * визуально-шумной полосы; на desktop пилюли просто помещаются в ряд.
 * Каждая пилюля - настоящий `<button>` (клавиатура / screen-reader).
 *
 * Единый primitive вместо локальных ChipButton которые раньше были
 * скопированы в Hadith (round-full chips), Library (segmented control) и
 * QA (flat pills) с разной стилистикой.
 */
function FilterChips({
  options,
  value,
  onChange,
  ariaLabel,
  className = '',
}: Props) {
  return (
    <div
      role="group"
      aria-label={ariaLabel}
      className={`flex items-center gap-1.5 overflow-x-auto scrollbar-hide ${className}`}
    >
      {options.map((opt) => {
        const active = opt.value === value;
        return (
          <button
            key={opt.value}
            type="button"
            onClick={() => onChange(opt.value)}
            aria-pressed={active}
            className={`inline-flex h-8 shrink-0 items-center gap-1.5 rounded-full border px-3 text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500/40 ${
              active
                ? 'border-accent-600 bg-accent-600 text-ink-0'
                : 'border-border-strong bg-elevated text-ink-700 hover:bg-ink-100 hover:text-ink-900'
            }`}
          >
            <span className="whitespace-nowrap">{opt.label}</span>
            {opt.count != null && (
              <span
                className={`inline-flex min-w-4 items-center justify-center rounded-full px-1 text-xs font-semibold tabular-nums ${
                  active
                    ? 'bg-ink-0/20 text-ink-0'
                    : 'bg-ink-100 text-ink-500'
                }`}
              >
                {opt.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}

export default FilterChips;
