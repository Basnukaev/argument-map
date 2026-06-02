import { useId } from 'react';
import { ArrowUpDown, ChevronDown } from 'lucide-react';
import { useT } from '@/shared/i18n';

export interface SortOption {
  value: string;
  label: string;
}

interface Props {
  value: string;
  onChange: (value: string) => void;
  options: ReadonlyArray<SortOption>;
  /** Видимая подпись слева. По умолчанию common.sort_by («Сортировка»). */
  label?: string;
  /** Доп. классы на внешнюю обёртку. */
  className?: string;
}

/**
 * SortSelect - компактный сорт-контрол: иконка ArrowUpDown + подпись +
 * нативный `<select>`. Нативный select (не custom dropdown) намеренно -
 * combobox role + selectOptions работают из коробки в тестах и со
 * screen-reader'ами, а сорт-меню не требует кастомной типографики
 * опций (в отличие от Select для арабских названий томов).
 *
 * Подпись связана с select через htmlFor/id - accessible name select'а =
 * текст подписи (по умолчанию «Сортировка»). Высота h-9 = ряд ListToolbar.
 *
 * appearance-none + кастомный ChevronDown - убирает нативную стрелку
 * браузера ради консистентного вида с Select / Button.
 */
function SortSelect({
  value,
  onChange,
  options,
  label,
  className = '',
}: Props) {
  const t = useT();
  const id = useId();
  const resolvedLabel = label ?? t('common.sort_by');
  return (
    <div className={`inline-flex items-center gap-2 ${className}`}>
      <label
        htmlFor={id}
        className="inline-flex shrink-0 items-center gap-1.5 text-xs font-medium text-ink-500"
      >
        <ArrowUpDown size={13} aria-hidden />
        <span className="whitespace-nowrap">{resolvedLabel}</span>
      </label>
      <div className="relative inline-flex items-center">
        <select
          id={id}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className="h-9 appearance-none rounded-md border border-border-strong bg-elevated ps-3 pe-8 text-sm font-medium text-ink-900 outline-none transition-colors hover:border-ink-300 focus:border-accent-500 focus:ring-2 focus:ring-accent-500/20"
        >
          {options.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
        <ChevronDown
          size={14}
          aria-hidden
          className="pointer-events-none absolute end-2.5 text-ink-400"
        />
      </div>
    </div>
  );
}

export default SortSelect;
