import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';

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
  sm: 'h-5 px-1.5 text-[11px] gap-1 rounded',
  md: 'h-[22px] px-2 text-[11px] gap-1 rounded',
  lg: 'h-7 px-2.5 text-[12px] gap-1.5 rounded-md',
};

const ICON_SIZE: Record<Size, number> = { sm: 11, md: 12, lg: 14 };

const TONE_CLASSES: Record<Tone, string> = {
  slate: 'bg-slate-100 text-slate-700 border-slate-200',
  indigo: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  emerald: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  amber: 'bg-amber-50 text-amber-800 border-amber-200',
  red: 'bg-red-50 text-red-700 border-red-200',
  blue: 'bg-blue-50 text-blue-700 border-blue-200',
  violet: 'bg-violet-50 text-violet-700 border-violet-200',
  sky: 'bg-sky-50 text-sky-700 border-sky-200',
  teal: 'bg-teal-50 text-teal-700 border-teal-200',
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
