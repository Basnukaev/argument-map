import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { clsx } from 'clsx';

type ButtonVariant = 'primary' | 'secondary' | 'ghost';
type ButtonSize = 'sm' | 'md';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  iconOnly?: boolean;
  leftIcon?: ReactNode;
  rightIcon?: ReactNode;
}

const VARIANTS: Record<ButtonVariant, string> = {
  primary:
    'bg-accent-600 text-ink-0 hover:bg-accent-700 disabled:opacity-40 disabled:hover:bg-accent-600',
  secondary:
    'bg-elevated text-ink-900 border border-ink-200 hover:bg-ink-50 hover:border-ink-300 disabled:opacity-40',
  ghost:
    'text-ink-700 hover:bg-ink-100 hover:text-ink-900 data-[active=true]:bg-accent-100 data-[active=true]:text-accent-700',
};

const SIZES: Record<ButtonSize, string> = {
  sm: 'h-7 px-2 text-xs gap-1',
  md: 'h-8 px-3 text-sm gap-2',
};

const ICON_ONLY_SIZES: Record<ButtonSize, string> = {
  sm: 'w-7 h-7',
  md: 'w-8 h-8',
};

/**
 * Reader Redesign — primary, secondary and ghost buttons.
 *
 *   <Button variant="primary" leftIcon={<Sparkles size={14} />}>Создать</Button>
 *
 * Use `iconOnly` for single-icon affordances; always include `aria-label`.
 */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ variant = 'secondary', size = 'md', iconOnly = false, leftIcon, rightIcon, className, children, ...rest }, ref) => {
    return (
      <button
        ref={ref}
        className={clsx(
          'inline-flex items-center justify-center rounded font-medium whitespace-nowrap',
          'transition-[background-color,border-color,color] duration-150',
          'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent-500',
          'disabled:cursor-not-allowed',
          VARIANTS[variant],
          iconOnly ? ICON_ONLY_SIZES[size] : SIZES[size],
          className,
        )}
        {...rest}
      >
        {leftIcon}
        {!iconOnly && children}
        {rightIcon}
      </button>
    );
  }
);
Button.displayName = 'Button';
