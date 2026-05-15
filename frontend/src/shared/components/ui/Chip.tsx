import type { HTMLAttributes, ReactNode } from 'react';

interface ChipProps extends HTMLAttributes<HTMLSpanElement> {
  /** Soft-fill accent. Для иерархических подсказок, но не для статусов. */
  accent?: boolean;
  /** Soft-fill positive (success). */
  ok?: boolean;
  icon?: ReactNode;
  children: ReactNode;
}

/**
 * Chip - generic inline-label короткий и без рамки. Дефолт - нейтральный
 * фон ink-100/text-ink-700.
 *
 * Для семантических меток предпочесть:
 *   • <TypeChip type="CLAIM" />     - типы узлов в графе
 *   • <StatusBadge status="..." />  - статусы аргумента
 *
 * Не использовать для статуса/типа - есть специальные primitives.
 */
function Chip({
  accent = false,
  ok = false,
  icon,
  className = '',
  children,
  ...rest
}: ChipProps) {
  let toneClass = 'bg-ink-100 text-ink-700';
  if (accent) toneClass = 'bg-accent-100 text-accent-700';
  if (ok) toneClass = 'bg-ok-100 text-ok-700';

  return (
    <span
      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-sm text-xs font-medium ${toneClass} ${className}`}
      {...rest}
    >
      {icon}
      {children}
    </span>
  );
}

export default Chip;
