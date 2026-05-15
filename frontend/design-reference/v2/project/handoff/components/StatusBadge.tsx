import { clsx } from 'clsx';

export type ArgumentStatus = 'STANDING' | 'DISPUTED' | 'REFUTED' | 'UNVERIFIED';

const STATUS: Record<ArgumentStatus, { bg: string; fg: string; label: string }> = {
  STANDING:   { bg: 'bg-ok-100',   fg: 'text-ok-700',   label: 'Устоявшийся' },
  DISPUTED:   { bg: 'bg-warn-100', fg: 'text-warn-700', label: 'Спорный' },
  REFUTED:    { bg: 'bg-err-100',  fg: 'text-err-700',  label: 'Опровергнут' },
  UNVERIFIED: { bg: 'bg-ink-100',  fg: 'text-ink-600',  label: 'Не оценён' },
};

/**
 * StatusBadge — argument-graph status pill.
 *
 *   <StatusBadge status="DISPUTED" />
 *
 * Uppercase 10px with a tiny dot. Use only for nodes' STATUS field.
 * If you find yourself reaching for it for something else, you probably
 * want <Chip /> instead.
 */
export function StatusBadge({
  status,
  className,
  labelOverride,
}: {
  status: ArgumentStatus;
  className?: string;
  labelOverride?: string;
}) {
  const s = STATUS[status];
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-semibold uppercase tracking-wider',
        s.bg,
        s.fg,
        className,
      )}
    >
      <span className="w-[5px] h-[5px] rounded-full bg-current" />
      {labelOverride ?? s.label}
    </span>
  );
}
