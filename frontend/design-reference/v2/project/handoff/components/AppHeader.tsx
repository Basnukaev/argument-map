import { Link, useLocation } from 'react-router-dom'; // or your router
import { Graph, Book, Sparkles, Search, Bookmark } from 'lucide-react';
import { clsx } from 'clsx';

interface User {
  initials: string;
}

/**
 * AppHeader — top bar across all non-reader pages. There's only one,
 * don't fork it per page.
 */
export function AppHeader({
  user,
  locale = 'ru',
  onSearchClick,
}: {
  user?: User;
  locale?: 'ru' | 'ar' | 'en';
  onSearchClick?: () => void;
}) {
  const { pathname } = useLocation();
  const isCurrent = (p: string) => pathname.startsWith(p);

  const nav = [
    { path: '/topics', label: 'Темы',  Icon: Graph },
    { path: '/books',  label: 'Книги', Icon: Book },
    { path: '/admin',  label: 'Админ', Icon: Sparkles },
  ];

  return (
    <header className="flex-none h-[52px] flex items-center gap-6 px-6 bg-ink-0 border-b border-border">
      {/* Logo */}
      <div className="flex items-center gap-2.5">
        <div className="w-[26px] h-[26px] rounded-md bg-accent-600 text-ink-0 grid place-items-center font-serif font-bold text-sm tracking-tight">
          ﷽
        </div>
        <span className="font-semibold text-sm text-ink-900 tracking-tight">
          Argument Map
        </span>
      </div>

      {/* Nav */}
      <nav className="flex gap-0.5 flex-1">
        {nav.map(({ path, label, Icon }) => {
          const cur = isCurrent(path);
          return (
            <Link
              key={path}
              to={path}
              className={clsx(
                'inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded text-[13px] font-medium',
                cur ? 'bg-accent-50 text-accent-700' : 'text-ink-700 hover:bg-ink-50',
              )}
            >
              <Icon size={14} />
              {label}
            </Link>
          );
        })}
      </nav>

      {/* Right */}
      <div className="flex items-center gap-1.5">
        <button
          onClick={onSearchClick}
          className="inline-flex items-center gap-2 px-2.5 py-1.5 rounded text-xs text-ink-700 hover:bg-ink-100"
        >
          <Search size={13} />
          <Kbd>⌘K</Kbd>
        </button>

        {/* Locale switch */}
        <div className="inline-flex p-0.5 gap-px border border-ink-200 rounded bg-ink-50">
          {(['ru', 'ar', 'en'] as const).map((l) => (
            <button
              key={l}
              className={clsx(
                'px-1.5 py-0.5 text-[11px] font-medium font-mono uppercase tracking-wider rounded',
                l === locale ? 'bg-ink-900 text-ink-0' : 'text-ink-500',
              )}
            >
              {l}
            </button>
          ))}
        </div>

        <button className="w-7 h-7 grid place-items-center text-ink-600 hover:bg-ink-100 rounded" aria-label="Уведомления">
          <Bookmark size={14} />
        </button>

        {/* Avatar */}
        {user && (
          <div className="w-7 h-7 rounded-full bg-accent-100 text-accent-700 grid place-items-center text-xs font-semibold">
            {user.initials}
          </div>
        )}
      </div>
    </header>
  );
}

function Kbd({ children }: { children: React.ReactNode }) {
  return (
    <span className="inline-flex items-center justify-center min-w-[18px] h-[18px] px-1 font-mono text-[10px] font-medium text-ink-700 bg-ink-0 border border-ink-200 border-b-2 rounded-sm">
      {children}
    </span>
  );
}
