import { useCallback, useState } from 'react';
import { Link } from 'react-router';
import { BookOpen, Search, Loader2, Users } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import { useT } from '@/shared/i18n';
import { usePagedSearch } from '@/shared/hooks/usePagedSearch';

// Backend types ещё не regenerated в types.ts - inline types.
interface HadithItem {
  id: string;
  collectionId: string | null;
  primaryNumber: number | null;
  normalizedMatn: string;
  status: 'CANONICAL' | 'VARIANT' | 'WEAK' | 'FABRICATED';
  sourceId: string | null;
  createdAt: string;
}

const PAGE_SIZE = 20;

/**
 * Vision 49d Section 2.6 Phase 2 frontend — Hadith list page.
 * GET /api/v1/hadith/hadiths. Debounce + Load-More — через usePagedSearch.
 */
function HadithListPage() {
  const t = useT();
  const [statusFilter, setStatusFilter] = useState<HadithItem['status'] | 'ALL'>('ALL');

  const buildUrl = useCallback(
    (page: number, q: string): string => {
      const params = new URLSearchParams();
      params.set('page', String(page));
      params.set('size', String(PAGE_SIZE));
      if (q) params.set('q', q);
      if (statusFilter !== 'ALL') params.set('status', statusFilter);
      return `/api/v1/hadith/hadiths?${params.toString()}`;
    },
    [statusFilter],
  );

  const { state, searchInput, setSearchInput, loadMore, loadingMore } =
    usePagedSearch<HadithItem>({ buildUrl, deps: [statusFilter] });

  return (
    <main className="min-h-screen bg-bg">
      <Header />
      <div className="mx-auto max-w-[1380px] px-3 py-6 sm:px-6 sm:py-8">
        <header className="mb-6">
          <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-ink-500">
            <BookOpen size={12} aria-hidden /> {t('hadith.title')}
          </div>
          <h1 className="mt-1 text-2xl font-semibold text-ink-900">{t('hadith.title')}</h1>
          <p className="mt-1 text-sm text-ink-600">{t('hadith.subtitle')}</p>
          <Link
            to="/hadith/narrators"
            className="mt-2 inline-flex items-center gap-1 text-sm font-medium text-accent-700 hover:underline"
          >
            <Users size={14} aria-hidden /> {t('hadith.narrators.link')}
          </Link>
        </header>

        <div className="mb-6 flex flex-wrap items-center gap-3">
          <div className="flex h-9 max-w-md flex-1 items-center rounded-md border border-border-strong bg-elevated focus-within:border-accent-500">
            <Search size={15} className="ms-3 text-ink-400" aria-hidden />
            <input
              type="search"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder={t('hadith.search_placeholder')}
              dir="auto"
              className="flex-1 bg-transparent px-3 text-sm text-ink-900 outline-none"
            />
          </div>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as typeof statusFilter)}
            className="h-9 rounded-md border border-border-strong bg-elevated px-2 text-sm text-ink-900 outline-none focus:border-accent-500"
          >
            <option value="ALL">{t('hadith.filter.all')}</option>
            <option value="CANONICAL">{t('hadith.filter.canonical')}</option>
            <option value="VARIANT">{t('hadith.filter.variant')}</option>
            <option value="WEAK">{t('hadith.filter.weak')}</option>
            <option value="FABRICATED">{t('hadith.filter.fabricated')}</option>
          </select>
        </div>

        {state.kind === 'loading' && (
          <div className="flex items-center gap-2 py-12 text-sm text-ink-500">
            <Loader2 size={16} className="animate-spin" /> {t('common.loading')}
          </div>
        )}

        {state.kind === 'error' && (
          <Card className="border-err-500/40 bg-err-100 p-5">
            <div className="text-sm text-ink-900">{state.message}</div>
          </Card>
        )}

        {state.kind === 'success' && (
          <>
            {state.data.items.length === 0 ? (
              <div className="rounded-md border border-dashed border-border-strong bg-elevated/50 p-8 text-center text-sm text-ink-500">
                {t('hadith.empty')}
              </div>
            ) : (
              <ul className="grid grid-cols-1 gap-3 md:grid-cols-2">
                {state.data.items.map((h) => (
                  <li key={h.id}>
                    <Link to={`/hadith/hadiths/${h.id}`} className="block focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 rounded-md">
                      <Card interactive className="p-4">
                        <div className="flex items-center gap-2 text-xs text-ink-500 mb-2">
                          <span className={`rounded-sm px-1.5 py-0.5 text-xs font-semibold ${
                            h.status === 'CANONICAL' ? 'bg-emerald-100 text-emerald-700' :
                            h.status === 'WEAK' ? 'bg-amber-100 text-amber-700' :
                            h.status === 'FABRICATED' ? 'bg-rose-100 text-rose-700' :
                            'bg-ink-100 text-ink-700'
                          }`}>
                            {h.status}
                          </span>
                          {h.primaryNumber != null && (
                            <span className="font-mono">№{h.primaryNumber}</span>
                          )}
                        </div>
                        <div className="text-sm text-ink-900 line-clamp-3" dir="auto">
                          {h.normalizedMatn || '(пусто)'}
                        </div>
                      </Card>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
            <div className="mt-4 text-xs text-ink-500">
              {t('hadith.total').replace('{count}', String(state.data.totalElements))}
            </div>
            {state.data.hasNext && (
              <div className="mt-4 flex justify-center">
                <button
                  type="button"
                  onClick={loadMore}
                  disabled={loadingMore}
                  className="inline-flex items-center gap-2 rounded-md border border-border-strong bg-elevated px-4 py-2 text-sm font-medium text-ink-700 hover:bg-ink-100 disabled:opacity-50"
                >
                  {loadingMore && <Loader2 size={14} className="animate-spin" />}
                  {t('common.load_more')}
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </main>
  );
}

export default HadithListPage;
