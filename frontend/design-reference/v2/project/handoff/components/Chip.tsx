import type { HTMLAttributes, ReactNode } from 'react';
import { clsx } from 'clsx';

interface ChipProps extends HTMLAttributes<HTMLSpanElement> {
  /** Soft-fill accent. Use for hierarchy hints, never for status. */
  accent?: boolean;
  /** Soft-fill positive (success). */
  ok?: boolean;
  icon?: ReactNode;
}

/**
 * Chip — generic short inline label.
 *
 * For *semantic* labels prefer:
 *   • <TypeChip type="CLAIM" />     — graph node types
 *   • <StatusBadge status="..." />  — argument status
 */
export function Chip({ accent, ok, icon, className, children, ...rest }: ChipProps) {
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium',
        accent && 'bg-accent-100 text-accent-700',
        ok && 'bg-ok-100 text-ok-700',
        !accent && !ok && 'bg-ink-100 text-ink-700',
        className,
      )}
      {...rest}
    >
      {icon}
      {children}
    </span>
  );
}
