import type { ButtonHTMLAttributes, ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'danger-ghost' | 'link';
type Size = 'xs' | 'sm' | 'md' | 'lg';

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
  size?: Size;
  icon?: LucideIcon;
  iconRight?: LucideIcon;
  full?: boolean;
  children?: ReactNode;
};

const SIZE_CLASSES: Record<Size, string> = {
  xs: 'h-7 px-2.5 text-[12px] gap-1 rounded',
  sm: 'h-8 px-3 text-[13px] gap-1.5 rounded-md',
  md: 'h-9 px-3.5 text-[13px] gap-2 rounded-md',
  lg: 'h-11 px-5 text-[14px] gap-2 rounded-md',
};

const ICON_SIZE: Record<Size, number> = {
  xs: 13,
  sm: 14,
  md: 15,
  lg: 18,
};

const VARIANT_CLASSES: Record<Variant, string> = {
  primary:
    'bg-indigo-600 text-white hover:bg-indigo-700 active:bg-indigo-800 border border-indigo-700/40 shadow-sm',
  secondary:
    'bg-white text-slate-800 hover:bg-slate-50 active:bg-slate-100 border border-slate-300 shadow-[0_1px_0_rgba(15,23,42,0.04)]',
  ghost: 'text-slate-700 hover:bg-slate-100 active:bg-slate-200 border border-transparent',
  danger:
    'bg-red-600 text-white hover:bg-red-700 active:bg-red-800 border border-red-700/40 shadow-sm',
  'danger-ghost':
    'text-red-700 hover:bg-red-50 active:bg-red-100 border border-transparent',
  link: 'text-indigo-700 hover:text-indigo-800 hover:underline underline-offset-4 border border-transparent px-1',
};

function Button({
  variant = 'primary',
  size = 'md',
  icon: Icon,
  iconRight: IconRight,
  full,
  className = '',
  children,
  disabled,
  ...rest
}: Props) {
  const iconPx = ICON_SIZE[size];
  return (
    <button
      className={`inline-flex items-center justify-center font-medium select-none transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 ${SIZE_CLASSES[size]} ${VARIANT_CLASSES[variant]} ${full ? 'w-full' : ''} ${disabled ? 'opacity-50 cursor-not-allowed pointer-events-none' : ''} ${className}`}
      disabled={disabled}
      {...rest}
    >
      {Icon && <Icon size={iconPx} aria-hidden="true" />}
      {children}
      {IconRight && <IconRight size={iconPx} aria-hidden="true" />}
    </button>
  );
}

export default Button;
