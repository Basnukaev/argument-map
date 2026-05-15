import type { ButtonHTMLAttributes, ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';

/**
 * v2 Button - ровно три "канонических" варианта (primary/secondary/ghost)
 * из дизайн-системы плюс несколько унаследованных от v1 (danger/danger-ghost/link)
 * для backwards compatibility, чтобы не сломать существующие места.
 *
 * Best practice: в новом коде использовать только primary/secondary/ghost.
 * Если нужен "опасный" акцент - предпочесть `danger` как красный primary.
 */
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
  xs: 'h-7 px-2 text-xs gap-1 rounded-sm',
  sm: 'h-8 px-3 text-sm gap-1.5 rounded-sm',
  md: 'h-9 px-3 text-sm gap-2 rounded-sm',
  lg: 'h-11 px-5 text-base gap-2 rounded-md',
};

const ICON_SIZE: Record<Size, number> = {
  xs: 13,
  sm: 14,
  md: 15,
  lg: 18,
};

const VARIANT_CLASSES: Record<Variant, string> = {
  primary:
    'bg-accent-600 text-ink-0 hover:bg-accent-700 active:bg-accent-700 border border-accent-700/40 shadow-sh1',
  secondary:
    'bg-elevated text-ink-900 hover:bg-ink-50 active:bg-ink-100 border border-ink-200 hover:border-ink-300 shadow-sh1',
  ghost:
    'text-ink-700 hover:bg-ink-100 hover:text-ink-900 active:bg-ink-200 border border-transparent data-[active=true]:bg-accent-50 data-[active=true]:text-accent-700',
  danger:
    'bg-err-500 text-ink-0 hover:bg-err-700 active:bg-err-700 border border-err-700/40 shadow-sh1',
  'danger-ghost':
    'text-err-700 hover:bg-err-100 active:bg-err-100 border border-transparent',
  link:
    'text-accent-600 hover:text-accent-700 hover:underline underline-offset-4 border border-transparent px-1',
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
      data-variant={variant}
      className={`inline-flex items-center justify-center font-medium select-none transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 focus-visible:ring-offset-2 focus-visible:ring-offset-bg ${SIZE_CLASSES[size]} ${VARIANT_CLASSES[variant]} ${full ? 'w-full' : ''} ${disabled ? 'opacity-50 cursor-not-allowed pointer-events-none' : ''} ${className}`}
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
