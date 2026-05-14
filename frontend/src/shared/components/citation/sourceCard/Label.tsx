import type { ReactNode } from 'react';

type Props = {
  children: ReactNode;
  className?: string;
};

/** Small-caps label для строк/полей */
export function Label({ children, className = '' }: Props) {
  return (
    <span
      className={`text-[10.5px] font-semibold uppercase tracking-wide text-slate-400 ${className}`}
    >
      {children}
    </span>
  );
}
