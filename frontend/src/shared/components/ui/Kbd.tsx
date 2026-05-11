import type { ReactNode } from 'react';

interface Props {
  children: ReactNode;
  className?: string;
}

function Kbd({ children, className = '' }: Props) {
  return (
    <kbd
      className={`inline-flex items-center justify-center min-w-[20px] h-[20px] px-1.5 rounded border border-slate-300 bg-white text-[11px] font-mono text-slate-600 shadow-[0_1px_0_rgba(15,23,42,0.04)] ${className}`}
    >
      {children}
    </kbd>
  );
}

export default Kbd;
