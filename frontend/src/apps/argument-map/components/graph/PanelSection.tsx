import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { ChevronDown, type LucideIcon } from 'lucide-react';

interface Props {
  icon: LucideIcon;
  title: string;
  count?: number | string;
  defaultOpen?: boolean;
  /** вызывается при первом раскрытии секции - удобно для lazy-load */
  onFirstOpen?: () => void;
  children: ReactNode;
}

/**
 * Сворачиваемая секция в правой панели деталей. Header с иконкой,
 * заголовком, optional счётчиком и chevron-стрелкой. Поддерживает
 * lazy-load через onFirstOpen (срабатывает один раз при первом
 * раскрытии).
 */
function PanelSection({ icon: Icon, title, count, defaultOpen = true, onFirstOpen, children }: Props) {
  const [open, setOpen] = useState(defaultOpen);
  const firedRef = useRef(false);

  useEffect(() => {
    if (open && !firedRef.current) {
      firedRef.current = true;
      onFirstOpen?.();
    }
  }, [open, onFirstOpen]);

  return (
    <section className="border-t border-slate-200">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex w-full items-center gap-2 px-5 py-3 text-start transition-colors hover:bg-slate-50"
      >
        <Icon size={14} className="text-slate-500" aria-hidden="true" />
        <span className="text-[12px] font-semibold uppercase tracking-wider text-slate-700">
          {title}
        </span>
        {count !== undefined && (
          <span className="text-[11px] font-mono text-slate-400">{count}</span>
        )}
        <ChevronDown
          size={14}
          className={`ms-auto text-slate-400 transition-transform ${open ? '' : '-rotate-90'}`}
          aria-hidden="true"
        />
      </button>
      {open && <div className="px-5 pb-4">{children}</div>}
    </section>
  );
}

export default PanelSection;
