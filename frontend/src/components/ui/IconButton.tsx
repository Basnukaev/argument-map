import type { ButtonHTMLAttributes } from 'react';
import type { LucideIcon } from 'lucide-react';

type Variant = 'ghost' | 'solid';
type Size = 'sm' | 'md' | 'lg';

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  icon: LucideIcon;
  /** обязательный для a11y - читается screen reader-ом и используется как title */
  label: string;
  active?: boolean;
  size?: Size;
  variant?: Variant;
};

const SIZE_CLASSES: Record<Size, string> = {
  sm: 'h-7 w-7',
  md: 'h-9 w-9',
  lg: 'h-10 w-10',
};

const ICON_PX: Record<Size, number> = { sm: 16, md: 18, lg: 20 };

function ghostClasses(active: boolean): string {
  return active
    ? 'bg-indigo-50 text-indigo-700 border-indigo-200'
    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900 border-transparent';
}

function IconButton({
  icon: Icon,
  label,
  active = false,
  size = 'md',
  variant = 'ghost',
  className = '',
  ...rest
}: Props) {
  const variantClass =
    variant === 'ghost'
      ? ghostClasses(active)
      : 'bg-white border-slate-300 text-slate-700 hover:bg-slate-50';
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      className={`inline-flex items-center justify-center rounded-md border transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-1 ${SIZE_CLASSES[size]} ${variantClass} ${className}`}
      {...rest}
    >
      <Icon size={ICON_PX[size]} aria-hidden="true" />
    </button>
  );
}

export default IconButton;
