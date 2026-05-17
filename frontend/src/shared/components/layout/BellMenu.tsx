import { useEffect, useRef, useState } from 'react';
import { Bell } from 'lucide-react';
import { useT } from '@/shared/i18n';
import { useHotkey } from '@/shared/hooks/useHotkey';

/**
 * Bell-кнопка с dropdown списком уведомлений. Backend пока нет - показывает
 * placeholder "Уведомлений нет". Реальные уведомления добавятся когда
 * появится /api/v1/notifications (Этап с multi-user).
 *
 * Закрывается по outside click и Escape.
 */
function BellMenu() {
  const t = useT();
  const [open, setOpen] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('pointerdown', onPointerDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
    };
  }, [open]);

  useHotkey('escape', () => setOpen(false), { enabled: open });

  return (
    <div ref={wrapperRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={t('common.notifications')}
        title={t('common.notifications')}
        className="inline-flex h-7 w-7 items-center justify-center rounded-sm text-ink-600 hover:bg-ink-100 hover:text-ink-900 transition-colors"
      >
        <Bell size={14} aria-hidden />
      </button>
      {open && (
        <div
          role="menu"
          className="absolute end-0 top-9 z-50 w-72 rounded-md border border-border bg-elevated shadow-sh3"
        >
          <div className="border-b border-border px-4 py-3 text-xs font-semibold uppercase tracking-wider text-ink-500">
            {t('common.notifications')}
          </div>
          <div className="px-4 py-6 text-center text-sm text-ink-500">
            {t('notifications.empty')}
          </div>
        </div>
      )}
    </div>
  );
}

export default BellMenu;
