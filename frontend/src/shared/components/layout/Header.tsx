import { useState } from 'react';
import { Link, NavLink } from 'react-router';
import { Menu, Search } from 'lucide-react';
import LocaleSwitch from '@/shared/components/layout/LocaleSwitch';
import ThemeSwitch from '@/shared/components/layout/ThemeSwitch';
import SettingsLink from '@/shared/components/layout/SettingsLink';
import BellMenu from '@/shared/components/layout/BellMenu';
import AvatarMenu from '@/shared/components/layout/AvatarMenu';
import ShortcutHint from '@/shared/components/ui/ShortcutHint';
import IconButton from '@/shared/components/ui/IconButton';
import Modal from '@/shared/components/ui/Modal';
import { useT, type DictKey } from '@/shared/i18n';
import { useIsMobile } from '@/shared/hooks/useViewport';
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
  { to: '/qa', labelKey: 'nav.qa' },
  // Vision 49d Section 2.6: Hadith Explorer new app section
  { to: '/hadith/hadiths', labelKey: 'nav.hadith' },
  { to: '/library/collections', labelKey: 'nav.collections' },
  { to: '/admin/shamela', labelKey: 'nav.admin' },
];

/**
 * Глобальный top-bar для всех full-page UI (TopicList, BookList,
 * AdminShamela, Reader). Использует v2 design system - семантические
 * токены, единый стиль кнопок и pillов.
 *
 * Состав:
 * - brand: ﷽ logo (текст "Argument Map" убран в Сессии 38 - bismillah
 *   служит symbol-identity, текстовая надпись была redundancy)
 * - nav: Темы / Библиотека / Q&A (disabled) / Админ
 * - right side:
 *   - кнопка поиск Alt+K, открывает CommandPalette (тот же hotkey)
 *   - LocaleSwitch (RU/AR), ThemeSwitch (Sun/Moon)
 *   - Bell - dropdown уведомлений (placeholder, нет бэка)
 *   - Avatar - dropdown профиля (placeholder, нет auth)
 *
 * Бренд не зеркалится при rtl - логотип всегда "иконка + текст".
 * Граф темы (TopicGraphPage) использует свой top-bar, не этот.
 */
function Header() {
  const t = useT();
  // Alt+K listener живёт в App.tsx (см. paletteStore docstring) -
  // Header только триггерит открытие через store.show().
  const showPalette = usePaletteStore((s) => s.show);
  const isMobile = useIsMobile();
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <>
      {/* На mobile - compact (gap-2, px-3) чтобы logo + hamburger + search
          + locale поместились в 375px viewport. Desktop сохраняет старые
          gap-6/px-6 */}
      <header className="sticky top-0 z-40 flex-none h-12 flex items-center gap-2 px-3 md:gap-6 md:px-6 bg-elevated border-b border-border-strong shadow-sh1">
        {/* На mobile - hamburger перед logo (стандарт для drawer-pattern).
            Desktop - hamburger не нужен, nav inline */}
        {isMobile && (
          <IconButton
            icon={Menu}
            label={t('nav.menu_open_aria')}
            size="sm"
            onClick={() => setMenuOpen(true)}
          />
        )}

        {/* Brand - не зеркалится при rtl. Используем 6-step scale (gap-2 = 8px) */}
        <Link
          to="/topics"
          dir="ltr"
          className="flex items-center gap-2 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 focus-visible:ring-offset-2 rounded-sm"
          aria-label={t('nav.home_aria')}
        >
          {/* ﷽ (U+FDFD) - арабская лигатура Бисмиллах, горизонтальный
              aspect-ratio ~4:1. Квадратный 28×28 box обрезал её → используем
              w-auto + px-2.5 чтобы box расширялся под каллиграфию. font-arabic
              даёт правильный naskh rendering вместо системного serif fallback */}
          {/* ﷽ компактный mode: text-sm + минимальный padding. logo
              работает как symbol-identity (узнаётся по форме вязи),
              читаемость как текста не важна - поэтому жертвуем размером
              ради геометрической компактности. nav остаётся рядом */}
          {/* Logo font - LOCKED Scheherazade New, не подменяется через
              FontPairEffect (которое динамически меняет --font-arabic).
              Logo - часть brand identity, должен оставаться constant
              даже когда пользователь меняет шрифт интерфейса в Settings */}
          <span
            className="inline-flex h-7 w-auto min-w-7 items-center justify-center rounded-md bg-accent-600 px-1.5 text-sm font-semibold leading-none text-ink-0"
            style={{ fontFamily: "'Scheherazade New', 'Amiri', 'Noto Naskh Arabic', serif" }}
          >
            ﷽
          </span>
        </Link>

        {/* Inline navigation - только desktop (≥md). На mobile скрыт,
            доступен через hamburger Modal */}
        <nav className="hidden md:flex gap-1 flex-1">
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

        {/* На mobile - spacer чтобы right cluster прижался к концу */}
        <div className="md:hidden flex-1" />

        {/* Right cluster. Search/Settings/Bell/Avatar скрываются на mobile -
            доступ через menu Modal. Locale + Theme оставлены (часто
            переключаемое + узкое affordance) */}
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={showPalette}
            className="hidden md:inline-flex h-7 items-center gap-2 px-2 rounded-sm text-xs text-ink-600 hover:bg-ink-100 hover:text-ink-900 transition-colors"
            title={t('common.search')}
          >
            <Search size={13} aria-hidden />
            <ShortcutHint keys="alt+k" />
          </button>

          <LocaleSwitch />

          <div className="hidden md:contents">
            <SettingsLink />
          </div>

          <ThemeSwitch />

          <div className="hidden md:contents">
            <BellMenu />
            <AvatarMenu />
          </div>
        </div>
      </header>

      {/* Mobile menu drawer - render в Modal (fullscreen на mobile уже из
          Modal Фазы 1). Содержит nav + быстрые actions которые на mobile
          скрыты из top bar */}
      {menuOpen && (
        <Modal
          open
          onClose={() => setMenuOpen(false)}
          title={t('nav.menu_title')}
        >
          <nav className="flex flex-col gap-1">
            {NAV_ITEMS.map((item) =>
              item.disabled ? (
                <span
                  key={item.to}
                  className="inline-flex h-10 cursor-not-allowed items-center rounded-sm px-3 text-sm font-medium text-ink-400"
                  title={t('nav.disabled_hint')}
                >
                  {t(item.labelKey)}
                </span>
              ) : (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/topics'}
                  onClick={() => setMenuOpen(false)}
                  className={({ isActive }) =>
                    isActive
                      ? 'inline-flex h-10 items-center rounded-sm bg-accent-50 px-3 text-sm font-medium text-accent-700'
                      : 'inline-flex h-10 items-center rounded-sm px-3 text-sm font-medium text-ink-700 hover:bg-ink-100 hover:text-ink-900 transition-colors'
                  }
                >
                  {t(item.labelKey)}
                </NavLink>
              ),
            )}
            <div className="mt-4 border-t border-border pt-4 flex flex-col gap-1">
              <button
                type="button"
                onClick={() => {
                  setMenuOpen(false);
                  showPalette();
                }}
                className="inline-flex h-10 items-center gap-2 px-3 rounded-sm text-sm text-ink-700 hover:bg-ink-100 hover:text-ink-900 transition-colors"
              >
                <Search size={15} aria-hidden />
                <span>{t('common.search')}</span>
              </button>
              <Link
                to="/settings"
                onClick={() => setMenuOpen(false)}
                className="inline-flex h-10 items-center gap-2 px-3 rounded-sm text-sm text-ink-700 hover:bg-ink-100 hover:text-ink-900 transition-colors"
              >
                <span>{t('settings.link.title')}</span>
              </Link>
            </div>
          </nav>
        </Modal>
      )}
    </>
  );
}

export default Header;
