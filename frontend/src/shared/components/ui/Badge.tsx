import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';

/**
 * v2 Badge - generic chip-like элемент. В отличие от Chip (новый primitive),
 * Badge поддерживает per-tone семантику для случаев когда нужен особый
 * акцент (warning, success, info и т.д.). Для статусов узлов - StatusBadge.
 *
 * Тоны переведены на семантические токены через ink/accent/ok/warn/err
 * с автоматической темизацией. Старые `tone` названия (slate, indigo и т.д.)
 * сохранены как алиасы для backwards compatibility.
 */
type Tone =
  | 'slate'
  | 'indigo'
  | 'emerald'
  | 'amber'
  | 'red'
  | 'blue'
  | 'violet'
  | 'sky'
  | 'teal';

type Size = 'sm' | 'md' | 'lg';

interface Props {
  children: ReactNode;
  tone?: Tone;
  size?: Size;
  icon?: LucideIcon;
  className?: string;
  'data-testid'?: string;
}

const SIZE_CLASSES: Record<Size, string> = {
  sm: 'h-5 px-1.5 text-xs gap-1 rounded-sm',
  md: 'h-[22px] px-2 text-xs gap-1 rounded-sm',
  lg: 'h-7 px-2.5 text-xs gap-1.5 rounded-sm',
};

const ICON_SIZE: Record<Size, number> = { sm: 11, md: 12, lg: 14 };

/**
 * Старые цветные tones (slate/indigo/emerald/etc.) на новой палитре
 * через семантические токены. Outline через border-{token}/30 - тонкий
 * контур без жирной линии.
 */
const TONE_CLASSES: Record<Tone, string> = {
  slate: 'bg-ink-100 text-ink-700 border-ink-200',
  indigo: 'bg-accent-50 text-accent-700 border-accent-100',
  emerald: 'bg-ok-100 text-ok-700 border-ok-500/30',
  amber: 'bg-warn-100 text-warn-700 border-warn-500/30',
  red: 'bg-err-100 text-err-700 border-err-500/30',
  blue: 'bg-edge-qualifies-bg text-edge-qualifies border-edge-qualifies/30',
  violet: 'bg-type-abstract-bg text-type-abstract-fg border-type-abstract-fg/20',
  sky: 'bg-edge-qualifies-bg text-edge-qualifies border-edge-qualifies/30',
  teal: 'bg-type-empirical-bg text-type-empirical-fg border-type-empirical-fg/20',
};

function Badge({
  children,
  tone = 'slate',
  size = 'md',
  icon: Icon,
  className = '',
  ...rest
}: Props) {
  return (
    <span
      className={`inline-flex items-center font-medium border whitespace-nowrap ${SIZE_CLASSES[size]} ${TONE_CLASSES[tone]} ${className}`}
      {...rest}
    >
      {Icon && <Icon size={ICON_SIZE[size]} aria-hidden="true" />}
      {children}
    </span>
  );
}

export default Badge;
