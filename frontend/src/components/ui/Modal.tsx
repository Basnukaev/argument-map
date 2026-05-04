import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import { X } from 'lucide-react';

interface Props {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  /** ширина диалога (Tailwind max-w-*); по умолчанию 'max-w-lg' */
  maxWidth?: string;
}

/**
 * Модалка на нативном <dialog> - доступность (focus trap, Escape, role="dialog")
 * из коробки. Backdrop закрывает при клике на тёмную область.
 */
function Modal({ open, onClose, title, children, maxWidth = 'max-w-lg' }: Props) {
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
      className={`w-full ${maxWidth} rounded-lg bg-white p-0 shadow-xl backdrop:bg-black/40 backdrop:backdrop-blur-sm`}
    >
      <header className="flex items-center justify-between border-b border-gray-200 px-5 py-3">
        <h2 className="text-lg font-semibold text-gray-900">{title}</h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="Закрыть"
          className="rounded p-1 text-gray-500 hover:bg-gray-100 hover:text-gray-700"
        >
          <X size={18} />
        </button>
      </header>
      <div className="px-5 py-4">{children}</div>
    </dialog>
  );
}

export default Modal;
