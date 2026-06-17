import { useT, type DictKey } from '@/shared/i18n';

/**
 * Переключатель вкладок detail-страницы хадиса (С10): клик по вкладке
 * показывает ТОЛЬКО её секцию (настоящий tab-switcher, не scroll-to-anchor).
 * Активная вкладка владеет single-state в HadithDetailPage; deep-link через
 * URL hash синхронизируется там же.
 *
 * Хедер крупнее и заметнее (С18): больший размер шрифта/паддинги, контрастный
 * активный стейт (заливка + рамка), горизонтальный скролл на узких экранах.
 */
export interface HadithTabItem {
  /** Идентификатор вкладки (= URL hash без `#`). */
  id: string;
  labelKey: DictKey;
}

interface Props {
  items: ReadonlyArray<HadithTabItem>;
  active: string;
  onSelect: (id: string) => void;
}

function HadithTabs({ items, active, onSelect }: Props) {
  const t = useT();

  return (
    <nav
      aria-label={t('hadith.detail.nav.aria')}
      className="sticky top-12 z-30 -mx-3 mb-8 border-b-2 border-border-strong bg-bg/95 px-3 backdrop-blur sm:-mx-6 sm:px-6"
    >
      <ul
        role="tablist"
        className="flex gap-2 overflow-x-auto py-3 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      >
        {items.map((it) => {
          const isActive = active === it.id;
          return (
            <li key={it.id} className="shrink-0">
              <button
                type="button"
                role="tab"
                aria-selected={isActive}
                onClick={() => onSelect(it.id)}
                className={`inline-flex items-center rounded-lg border px-4 py-2 text-base font-semibold transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 ${
                  isActive
                    ? 'border-accent-500 bg-accent-100 text-accent-800 shadow-sm'
                    : 'border-transparent text-ink-500 hover:bg-ink-100 hover:text-ink-800'
                }`}
              >
                {t(it.labelKey)}
              </button>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

export default HadithTabs;
