import { Link, NavLink } from 'react-router';
import { Search } from 'lucide-react';
import LocaleSwitch from '@/shared/components/layout/LocaleSwitch';
import ThemeSwitch from '@/shared/components/layout/ThemeSwitch';
import BellMenu from '@/shared/components/layout/BellMenu';
import AvatarMenu from '@/shared/components/layout/AvatarMenu';
import Kbd from '@/shared/components/ui/Kbd';
import { useT, type DictKey } from '@/shared/i18n';
import { usePaletteStore } from '@/shared/stores/paletteStore';

interface NavItem {
  to: string;
  labelKey: DictKey;
  /** Раздел не реализован - подсветка disabled, клик не делает ничего */
  disabled?: boolean;
}

const NAV_ITEMS: ReadonlyArray<NavItem> = [
  { to: '/topics', labelKey: 'nav.topics' },
  { to: '/books', labelKey: 'nav.library' },
  { to: '/qa', labelKey: 'nav.qa', disabled: true },
  { to: '/admin/shamela', labelKey: 'nav.admin' },
];

/**
 * Глобальный top-bar для всех full-page UI (TopicList, BookList,
 * AdminShamela, Reader). Использует v2 design system - семантические
 * токены, единый стиль кнопок и pillов.
 *
 * Состав:
 * - brand: ﷽ logo + "Argument Map"
 * - nav: Темы / Библиотека / Q&A (disabled) / Админ
 * - right side:
 *   - кнопка поиск ⌘K, открывает CommandPalette (тот же по Cmd/Ctrl+K)
 *   - LocaleSwitch (RU/AR), ThemeSwitch (Sun/Moon)
 *   - Bell - dropdown уведомлений (placeholder, нет бэка)
 *   - Avatar - dropdown профиля (placeholder, нет auth)
 *
 * Бренд не зеркалится при rtl - логотип всегда "иконка + текст".
 * Граф темы (TopicGraphPage) использует свой top-bar, не этот.
 */
function Header() {
  const t = useT();
  // Cmd+K listener живёт в App.tsx (см. paletteStore docstring) -
  // Header только триггерит открытие через store.show().
  const showPalette = usePaletteStore((s) => s.show);

  return (
    <>
      <header className="flex-none h-12 flex items-center gap-6 px-6 bg-elevated border-b border-border">
        {/* Brand - не зеркалится при rtl. Используем 6-step scale (gap-2 = 8px) */}
        <Link
          to="/topics"
          dir="ltr"
          className="flex items-center gap-2 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 focus-visible:ring-offset-2 rounded-sm"
          aria-label={t('nav.home_aria')}
        >
          <span className="grid h-7 w-7 place-items-center rounded-md bg-accent-600 text-ink-0 font-serif font-bold text-base">
            ﷽
          </span>
          <span className="text-sm font-semibold text-ink-900 tracking-tight">
            Argument Map
          </span>
        </Link>

        {/* Navigation. gap-1 = 4px (s-1), достаточно между nav-pills */}
        <nav className="flex gap-1 flex-1">
          {NAV_ITEMS.map((item) =>
            item.disabled ? (
              <span
                key={item.to}
                className="inline-flex h-7 cursor-not-allowed items-center rounded-sm px-3 text-xs font-medium text-ink-400"
                title={t('nav.disabled_hint')}
              >
                {t(item.labelKey)}
              </span>
            ) : (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/topics'}
                className={({ isActive }) =>
                  isActive
                    ? 'inline-flex h-7 items-center rounded-sm bg-accent-50 px-3 text-xs font-medium text-accent-700'
                    : 'inline-flex h-7 items-center rounded-sm px-3 text-xs font-medium text-ink-700 hover:bg-ink-100 hover:text-ink-900 transition-colors'
                }
              >
                {t(item.labelKey)}
              </NavLink>
            ),
          )}
        </nav>

        {/* Right cluster. gap-2 = 8px (s-2) - стандарт между chip-elements */}
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={showPalette}
            className="inline-flex h-7 items-center gap-2 px-2 rounded-sm text-xs text-ink-600 hover:bg-ink-100 hover:text-ink-900 transition-colors"
            title={t('common.search')}
          >
            <Search size={13} aria-hidden />
            <Kbd>⌘K</Kbd>
          </button>

          <LocaleSwitch />

          <ThemeSwitch />

          <BellMenu />

          <AvatarMenu initials="AB" />
        </div>
      </header>
    </>
  );
}

export default Header;
