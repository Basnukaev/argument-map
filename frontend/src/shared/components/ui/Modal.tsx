import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import IconButton from '@/shared/components/ui/IconButton';
import { X } from 'lucide-react';
import { useT } from '@/shared/i18n';

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
 *
 * v2 токены: bg-elevated/border (переключаются по теме), shadow-sh4 для
 * наивысшей elevation - модалка стоит над всем.
 */
function Modal({
  open,
  onClose,
  title,
  subtitle,
  children,
  maxWidth = 'max-w-lg',
}: Props) {
  const t = useT();
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
      className={`m-auto w-full ${maxWidth} rounded-lg border border-border bg-elevated p-0 shadow-sh4 backdrop:bg-black/50 backdrop:backdrop-blur-sm`}
    >
      <header className="flex items-start justify-between gap-3 border-b border-border px-6 py-4">
        <div className="min-w-0">
          <h2 className="text-base font-semibold text-ink-900">{title}</h2>
          {subtitle && <p className="mt-0.5 text-xs text-ink-500">{subtitle}</p>}
        </div>
        <IconButton icon={X} label={t('common.close')} size="sm" onClick={onClose} />
      </header>
      <div className="px-6 py-5 text-ink-900">{children}</div>
    </dialog>
  );
}

export default Modal;
