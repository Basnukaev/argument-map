import { useState, type ReactNode } from 'react';
import { ChevronDown } from 'lucide-react';

type Props = {
  title: string;
  count?: number;
  defaultOpen?: boolean;
  children: ReactNode;
};

/**
 * Аккордеон-секция. Header - кнопка toggle, body рендерится только при open.
 * Используется чтобы свернуть metadata под always-visible QuoteBlock
 */
export function Collapsible({
  title,
  count,
  defaultOpen = false,
  children,
}: Props) {
  const [open, setOpen] = useState<boolean>(defaultOpen);
  return (
    <div className="border-t border-border">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex w-full items-center justify-between gap-2 rounded py-2.5 text-start focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 focus-visible:ring-offset-2"
      >
        <span className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-ink-400">
          {title}
          {count != null && (
            <span className="rounded-full bg-ink-100 px-1.5 py-0.5 text-xs font-semibold tracking-normal text-ink-600 normal-case">
              {count}
            </span>
          )}
        </span>
        <ChevronDown
          aria-hidden
          size={13}
          className={`text-ink-400 transition-transform ${
            open ? 'rotate-180' : ''
          }`}
        />
      </button>
      {open && <div className="pb-3">{children}</div>}
    </div>
  );
}
