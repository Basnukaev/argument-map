import { useEffect, useRef, useState } from 'react';
import { LogOut, Settings, User } from 'lucide-react';
import { useT } from '@/shared/i18n';

interface Props {
  initials: string;
}

/**
 * Avatar-кнопка с dropdown профиля - placeholder до multi-user/auth.
 * Сейчас показывает statиc initials и три пункта: профиль, настройки,
 * выход. Реальный auth подключится когда добавится Spring Security
 * на бэке.
 */
function AvatarMenu({ initials }: Props) {
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
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div ref={wrapperRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={t('avatar.menu_aria')}
        className="grid h-7 w-7 place-items-center rounded-full bg-accent-100 text-accent-700 text-xs font-semibold transition-colors hover:bg-accent-500 hover:text-ink-0"
      >
        {initials}
      </button>
      {open && (
        <div
          role="menu"
          className="absolute end-0 top-9 z-50 w-56 rounded-md border border-border bg-elevated py-1 shadow-sh3"
        >
          <div className="border-b border-border px-3 py-2">
            <div className="text-sm font-semibold text-ink-900">
              {t('avatar.guest_user')}
            </div>
            <div className="text-xs text-ink-500">{t('avatar.no_auth_yet')}</div>
          </div>
          <button
            type="button"
            role="menuitem"
            className="flex w-full items-center gap-2 px-3 py-2 text-start text-sm text-ink-700 hover:bg-ink-100"
            onClick={() => setOpen(false)}
          >
            <User size={14} aria-hidden />
            {t('avatar.profile')}
          </button>
          <button
            type="button"
            role="menuitem"
            className="flex w-full items-center gap-2 px-3 py-2 text-start text-sm text-ink-700 hover:bg-ink-100"
            onClick={() => setOpen(false)}
          >
            <Settings size={14} aria-hidden />
            {t('avatar.settings')}
          </button>
          <div className="my-1 border-t border-border" />
          <button
            type="button"
            role="menuitem"
            disabled
            className="flex w-full items-center gap-2 px-3 py-2 text-start text-sm text-ink-400 cursor-not-allowed"
          >
            <LogOut size={14} aria-hidden />
            {t('avatar.logout')}
          </button>
        </div>
      )}
    </div>
  );
}

export default AvatarMenu;
