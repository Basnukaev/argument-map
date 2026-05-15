import type { ReactNode } from 'react';

interface Props {
  children: ReactNode;
  className?: string;
}

function Kbd({ children, className = '' }: Props) {
  return (
    <kbd
      className={`inline-flex items-center justify-center min-w-[20px] h-[20px] px-1.5 rounded-sm border border-ink-200 bg-elevated text-xs font-mono text-ink-700 shadow-sh1 ${className}`}
    >
      {children}
    </kbd>
  );
}

export default Kbd;
