import { Link, NavLink } from 'react-router';
import { Network } from 'lucide-react';
import LocaleSwitch from '@/shared/components/layout/LocaleSwitch';

interface NavItem {
  to: string;
  label: string;
  /** Раздел не реализован - подсветка disabled, клик не делает ничего */
  disabled?: boolean;
}

const NAV_ITEMS: ReadonlyArray<NavItem> = [
  { to: '/topics', label: 'Темы' },
  { to: '/books', label: 'Библиотека' },
  { to: '/qa', label: 'Q&A', disabled: true },
  { to: '/admin/shamela', label: 'Админ' },
];

/**
 * Глобальный top-bar страниц вне графа: список тем, библиотека,
 * формы создания. Содержит брендинг (логотип + название) и навигацию
 * между разделами супер-аппа. Используется во всех full-page UI -
 * `TopicListPage`, `BookListPage`, `BookReaderPage` и т.д.
 *
 * Граф темы (`TopicGraphPage`) свой top-bar не использует - он
 * full-screen canvas с floating UI overlay.
 *
 * Активный раздел подсвечивается через `react-router` `NavLink` -
 * матчинг по path-префиксу: `/topics` подсвечивается на `/topics`,
 * `/topics/new`, но не на `/topics/:id` (графовая страница).
 */
function Header() {
  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex h-12 max-w-[1380px] items-center gap-3 px-6">
        <Link
          to="/topics"
          className="flex items-center gap-3 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 rounded-md"
          aria-label="На главную"
        >
          <span className="grid h-7 w-7 place-items-center rounded-md bg-indigo-600 text-white">
            <Network size={16} aria-hidden="true" />
          </span>
          <span className="text-[14px] font-bold tracking-tight text-slate-900">
            Argument Map
          </span>
        </Link>
        <div className="h-5 w-px bg-slate-200" />
        <nav className="flex items-center gap-1 text-[12px]">
          {NAV_ITEMS.map((item) =>
            item.disabled ? (
              <span
                key={item.to}
                className="inline-flex h-7 cursor-not-allowed items-center rounded-md px-2.5 text-slate-400"
                title="Будет в одном из следующих этапов"
              >
                {item.label}
              </span>
            ) : (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/topics'}
                className={({ isActive }) =>
                  isActive
                    ? 'inline-flex h-7 items-center rounded-md bg-slate-100 px-2.5 font-medium text-slate-900'
                    : 'inline-flex h-7 items-center rounded-md px-2.5 text-slate-600 hover:bg-slate-50 hover:text-slate-900 transition-colors'
                }
              >
                {item.label}
              </NavLink>
            ),
          )}
        </nav>
        <div className="ms-auto">
          <LocaleSwitch />
        </div>
      </div>
    </header>
  );
}

export default Header;
