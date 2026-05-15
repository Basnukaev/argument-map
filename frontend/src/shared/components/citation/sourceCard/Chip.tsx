import type { ComponentType, ReactNode } from 'react';

type ChipTone = 'library' | 'free';

type Props = {
  icon: ComponentType<{ size?: number; className?: string; 'aria-hidden'?: boolean }>;
  tone?: ChipTone;
  children: ReactNode;
};

const TONE_CLASSES: Record<ChipTone, string> = {
  library: 'bg-accent-50 text-accent-600',
  free: 'bg-ink-100 text-ink-600',
};

/** Source-type pill: «из библиотеки» / «свободная» */
export function Chip({ icon: Icon, tone = 'library', children }: Props) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-bold uppercase tracking-wider leading-none ${TONE_CLASSES[tone]}`}
    >
      <Icon size={11} aria-hidden className="opacity-90" />
      {children}
    </span>
  );
}
