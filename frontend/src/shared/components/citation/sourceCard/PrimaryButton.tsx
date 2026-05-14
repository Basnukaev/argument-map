import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { ExternalLink } from 'lucide-react';

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  full?: boolean;
  children: ReactNode;
};

/** Indigo primary action button для source cards */
export function PrimaryButton({
  full = false,
  children,
  className = '',
  ...rest
}: Props) {
  return (
    <button
      type="button"
      className={
        'inline-flex items-center justify-center gap-2 rounded-lg ' +
        'bg-indigo-600 px-3.5 py-2.5 text-[13px] font-semibold text-white ' +
        'shadow-sm transition-colors hover:bg-indigo-700 ' +
        'focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 ' +
        (full ? 'w-full ' : '') +
        className
      }
      {...rest}
    >
      <ExternalLink size={14} aria-hidden />
      {children}
    </button>
  );
}
