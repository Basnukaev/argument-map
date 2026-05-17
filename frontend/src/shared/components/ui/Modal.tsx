import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import IconButton from '@/shared/components/ui/IconButton';
import { ArrowLeft, X } from 'lucide-react';
import { useT } from '@/shared/i18n';
import { useIsMobile } from '@/shared/hooks/useViewport';

interface Props {
  open: boolean;
  onClose: () => void;
  title: string;
  /** опциональная подпись под title в шапке модалки */
  subtitle?: string;
  children: ReactNode;
  /** ширина диалога (Tailwind max-w-*); по умолчанию 'max-w-lg'.
   *  На mobile (<md) игнорируется - модалка занимает весь viewport */
  maxWidth?: string;
}

/**
 * Модалка на нативном <dialog> - доступность (focus trap, Escape, role="dialog")
 * из коробки. Backdrop закрывает при клике на тёмную область.
 *
 * v2 токены: bg-elevated/border (переключаются по теме), shadow-sh4 для
 * наивысшей elevation - модалка стоит над всем
 *
 * **Responsive (Фаза 1 Сессия 39):**
 * - На mobile (<md=768px) - full-screen без rounded corners, header
 *   с back-arrow вместо close-X, max-h убран чтобы content скроллился
 *   внутри viewport. Body использует `dvh` - корректная высота при
 *   collapsing browser address-bar на iOS Safari / Chrome
 * - На md+ - centered с rounded corners, max-w согласно prop'у
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
  const isMobile = useIsMobile();
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
    // На mobile dialog full-screen - backdrop click недостижим, dismiss
    // через back-arrow или Escape только
    if (event.target === ref.current) {
      onClose();
    }
  }

  // Mobile - full-screen overlay (inset-0). Native <dialog> по умолчанию
  // получает margin: auto - переопределяем через m-0.
  // Desktop - centered (m-auto) + max-w + rounded
  const dialogClass = isMobile
    ? 'm-0 h-dvh max-h-dvh w-screen max-w-none rounded-none border-0 bg-elevated p-0 shadow-sh4 backdrop:bg-black/50 backdrop:backdrop-blur-sm'
    : `m-auto w-full ${maxWidth} max-h-[90vh] rounded-lg border border-border bg-elevated p-0 shadow-sh4 backdrop:bg-black/50 backdrop:backdrop-blur-sm`;

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onClick={handleBackdropClick}
      className={`${dialogClass} flex flex-col`}
    >
      <header className="flex flex-none items-start justify-between gap-3 border-b border-border px-6 py-4">
        {isMobile && (
          <IconButton
            icon={ArrowLeft}
            label={t('common.back')}
            size="sm"
            onClick={onClose}
          />
        )}
        <div className="min-w-0 flex-1">
          <h2 className="text-base font-semibold text-ink-900">{title}</h2>
          {subtitle && <p className="mt-0.5 text-xs text-ink-500">{subtitle}</p>}
        </div>
        {!isMobile && (
          <IconButton icon={X} label={t('common.close')} size="sm" onClick={onClose} />
        )}
      </header>
      <div className="flex-1 overflow-y-auto px-6 py-5 text-ink-900">{children}</div>
    </dialog>
  );
}

export default Modal;
