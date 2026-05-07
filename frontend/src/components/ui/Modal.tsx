import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import IconButton from '@/components/ui/IconButton';
import { X } from 'lucide-react';

interface Props {
  open: boolean;
  onClose: () => void;
  title: string;
  /** опциональная подпись под title в шапке модалки */
  subtitle?: string;
  children: ReactNode;
  /** ширина диалога (Tailwind max-w-*); по умолчанию 'max-w-lg' */
  maxWidth?: string;
}

/**
 * Модалка на нативном <dialog> - доступность (focus trap, Escape, role="dialog")
 * из коробки. Backdrop закрывает при клике на тёмную область.
 */
function Modal({ open, onClose, title, subtitle, children, maxWidth = 'max-w-lg' }: Props) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  function handleBackdropClick(event: React.MouseEvent<HTMLDialogElement>) {
    if (event.target === ref.current) {
      onClose();
    }
  }

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onClick={handleBackdropClick}
      className={`m-auto w-full ${maxWidth} rounded-lg border border-slate-200 bg-white p-0 shadow-2xl backdrop:bg-slate-900/40 backdrop:backdrop-blur-sm`}
    >
      <header className="flex items-start justify-between gap-3 border-b border-slate-200 px-6 py-4">
        <div className="min-w-0">
          <h2 className="text-[16px] font-semibold text-slate-900">{title}</h2>
          {subtitle && <p className="mt-0.5 text-[12px] text-slate-500">{subtitle}</p>}
        </div>
        <IconButton icon={X} label="Закрыть" size="sm" onClick={onClose} />
      </header>
      <div className="px-6 py-5">{children}</div>
    </dialog>
  );
}

export default Modal;
