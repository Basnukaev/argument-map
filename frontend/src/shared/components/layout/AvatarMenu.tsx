import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { LogOut, Settings, User } from 'lucide-react';
import { useT } from '@/shared/i18n';
import { useHotkey } from '@/shared/hooks/useHotkey';
import { useAuthStore } from '@/shared/stores/authStore';
import { toast } from '@/shared/stores/toastStore';

interface Props {
  /** Если задан - используется вместо вычисленных из user.username
   *  (для backwards-compat тестов / placeholder когда user пока null) */
  initials?: string;
}

/**
 * Avatar-кнопка с dropdown профиля. Показывает текущего user из
 * authStore. Logout - реальный, через POST /auth/logout +
 * чистка локальной сессии, потом редирект на /login.
 *
 * Если user отсутствует (race на старте) - показываем «Гость» как
 * раньше; реально такого быть не должно потому что AvatarMenu рендерится
 * внутри Header, который рендерится только на protected pages.
 */
function AvatarMenu({ initials }: Props) {
  const t = useT();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const [open, setOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
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

  const handleLogout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    try {
      await logout();
      setOpen(false);
      navigate('/login', { replace: true });
    } catch {
      toast.error(t('logout.failed'));
      setLoggingOut(false);
    }
  };

  // Initials: первая буква username (или email если username пустой)
  // либо forced initials через props (legacy)
  const computedInitials = (() => {
    if (initials) return initials;
    if (user?.username) return user.username.slice(0, 2).toUpperCase();
    return '?';
  })();

  const displayName = user?.username ?? t('avatar.guest_user');
  const displayEmail = user?.email ?? '';

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
        {computedInitials}
      </button>
      {open && (
        <div
          role="menu"
          className="absolute end-0 top-9 z-50 w-56 rounded-md border border-border bg-elevated py-1 shadow-sh3"
        >
          <div className="border-b border-border px-3 py-2">
            <div className="text-sm font-semibold text-ink-900 truncate">
              {displayName}
            </div>
            {displayEmail && (
              <div className="text-xs text-ink-500 truncate">{displayEmail}</div>
            )}
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
            disabled={loggingOut || !user}
            onClick={handleLogout}
            className="flex w-full items-center gap-2 px-3 py-2 text-start text-sm text-ink-700 hover:bg-ink-100 disabled:cursor-not-allowed disabled:text-ink-400"
          >
            <LogOut size={14} aria-hidden />
            {t('logout.label')}
          </button>
        </div>
      )}
    </div>
  );
}

export default AvatarMenu;
