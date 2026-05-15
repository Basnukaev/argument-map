import type { ReactNode } from 'react';

type Props = {
  children: ReactNode;
  className?: string;
};

/** Small-caps label для строк/полей */
export function Label({ children, className = '' }: Props) {
  return (
    <span
      className={`text-xs font-semibold uppercase tracking-wide text-ink-400 ${className}`}
    >
      {children}
    </span>
  );
}
