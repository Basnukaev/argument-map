import { useCallback, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { BookOpen, Loader2, Users } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import ListToolbar from '@/shared/components/ui/ListToolbar';
import SearchInput from '@/shared/components/ui/SearchInput';
import FilterChips from '@/shared/components/ui/FilterChips';
import SortSelect from '@/shared/components/ui/SortSelect';
import Pagination from '@/shared/components/ui/Pagination';
import { useT } from '@/shared/i18n';
import { usePagedList } from '@/shared/hooks/usePagedList';
import { useApiQuery } from '@/shared/hooks/useApiQuery';
import type { components } from '@/shared/api/types';

type HadithItem = components['schemas']['HadithResponse'];
type CollectionItem = components['schemas']['CollectionResponse'];

const PAGE_SIZE = 20;
type SortKey = 'number' | 'alphabetical' | 'recent';
// Две ортогональные оси (спека 2026-06-17): происхождение (провенанс) и
// достоверность. ALL = ось не фильтрует.
type ProvenanceFilter = 'CANONICAL' | 'VARIANT' | 'ALL';
type AuthenticityFilter = 'SAHIH' | 'HASAN' | 'DAIF' | 'MAUDU' | 'ALL';

/** Цвет бейджа происхождения на карточке (ось провенанса CANONICAL/VARIANT). */
function statusClass(status: string | undefined): string {
  switch (status) {
    case 'CANONICAL':
      return 'bg-emerald-100 text-emerald-700';
    default:
      return 'bg-ink-100 text-ink-700';
  }
}

/**
 * Hadith list — инструмент просмотра/дебага корпуса (под-проект #1).
 * Навигация: чипы-сборники (GET /hadith/collections) + сортировка
 * (номер / арабский алфавит / новые) + поиск. Карточки — одна колонка,
 * чистый диакритизированный matn (previewMatn), naskh + RTL.
 */
function HadithListPage() {
  const t = useT();
  // Под-проект #2.B: библиотека → обозреватель. Карточка книги-сборника
  // навигирует в /hadith?collectionId=<id>; читаем param на load и
  // предвыбираем сборник (фильтр-чип). Дальше чипы управляют state как обычно
  // (param только начальное значение, не source of truth).
  const [searchParams] = useSearchParams();
  const initialCollectionId = searchParams.get('collectionId');
  const [provenanceFilter, setProvenanceFilter] = useState<ProvenanceFilter>('ALL');
  const [authenticityFilter, setAuthenticityFilter] = useState<AuthenticityFilter>('ALL');
  const [collectionId, setCollectionId] = useState<string | null>(initialCollectionId);
  const [sort, setSort] = useState<SortKey>('number');

  const collectionsState = useApiQuery<CollectionItem[]>('/api/v1/hadith/collections');
  const collections = useMemo(
    () => (collectionsState.kind === 'success' ? collectionsState.data : []),
    [collectionsState],
  );
  const collectionName = useMemo(() => {
    const map = new Map<string, string>();
    for (const c of collections) {
      if (c.id) map.set(c.id, c.nameRu || c.nameEn || c.nameAr || c.slug || c.id);
    }
    return map;
  }, [collections]);

  /** Чипы-сборники: «Все» (value `ALL`) + по одному на сборник с
   *  hadithCount > 0. value `ALL` мапится на collectionId=null. */
  const collectionChips = useMemo(
    () => [
      { value: 'ALL', label: t('hadith.collections.all') },
      ...collections
        .filter((c) => c.id && (c.hadithCount ?? 0) > 0)
        .map((c) => ({
          value: c.id as string,
          label: c.nameRu || c.nameEn || c.slug || (c.id as string),
          count: c.hadithCount ?? undefined,
        })),
    ],
    [collections, t],
  );

  // Ось ПРОИСХОЖДЕНИЯ (провенанс): Сахихайн[CANONICAL] / Параллельная[VARIANT].
  const provenanceChips = useMemo(
    () => [
      { value: 'ALL', label: t('hadith.facet.provenance.all') },
      {
        value: 'CANONICAL',
        label: t('hadith.facet.provenance.canonical'),
        title: t('hadith.facet.tip.canonical'),
      },
      {
        value: 'VARIANT',
        label: t('hadith.facet.provenance.variant'),
        title: t('hadith.facet.tip.variant'),
      },
    ],
    [t],
  );

  // Ось ДОСТОВЕРНОСТИ: Сахих/Хасан/Даиф/Мауду[authenticity] — из вердиктов рулингов.
  const authenticityChips = useMemo(
    () => [
      { value: 'ALL', label: t('hadith.facet.authenticity.all') },
      {
        value: 'SAHIH',
        label: t('hadith.facet.authenticity.sahih'),
        title: t('hadith.facet.tip.sahih'),
      },
      {
        value: 'HASAN',
        label: t('hadith.facet.authenticity.hasan'),
        title: t('hadith.facet.tip.hasan'),
      },
      {
        value: 'DAIF',
        label: t('hadith.facet.authenticity.daif'),
        title: t('hadith.facet.tip.daif'),
      },
      {
        value: 'MAUDU',
        label: t('hadith.facet.authenticity.maudu'),
        title: t('hadith.facet.tip.maudu'),
      },
    ],
    [t],
  );

  const sortOptions = useMemo(
    () => [
      { value: 'number', label: t('hadith.sort.number') },
      { value: 'alphabetical', label: t('hadith.sort.alphabetical') },
      { value: 'recent', label: t('hadith.sort.recent') },
    ],
    [t],
  );

  const buildUrl = useCallback(
    (page: number, q: string): string => {
      const params = new URLSearchParams();
      params.set('page', String(page));
      params.set('size', String(PAGE_SIZE));
      params.set('sort', sort);
      if (q) params.set('q', q);
      // status = ось провенанса; authenticity = ось достоверности (ортогональны).
      if (provenanceFilter !== 'ALL') params.set('status', provenanceFilter);
      if (authenticityFilter !== 'ALL') params.set('authenticity', authenticityFilter);
      if (collectionId) params.set('collectionId', collectionId);
      return `/api/v1/hadith/hadiths?${params.toString()}`;
    },
    [provenanceFilter, authenticityFilter, collectionId, sort],
  );

  const { state, searchInput, setSearchInput, page, goToPage } = usePagedList<HadithItem>({
    buildUrl,
    deps: [provenanceFilter, authenticityFilter, collectionId, sort],
  });

  return (
    <main className="min-h-screen bg-bg">
      <Header />
      <div className="mx-auto max-w-3xl px-3 py-6 sm:px-6 sm:py-8">
        <header className="mb-5">
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

        {/* единый бар: поиск + сборники + сортировка */}
        <ListToolbar
          className="mb-3"
          search={
            <SearchInput
              value={searchInput}
              onChange={setSearchInput}
              placeholder={t('hadith.search_placeholder')}
              ariaLabel={t('common.search')}
              className="w-full"
            />
          }
          filters={
            <FilterChips
              options={collectionChips}
              value={collectionId ?? 'ALL'}
              onChange={(v) => setCollectionId(v === 'ALL' ? null : v)}
              ariaLabel={t('hadith.collections.all')}
            />
          }
          sort={
            <SortSelect
              value={sort}
              onChange={(v) => setSort(v as SortKey)}
              options={sortOptions}
            />
          }
        />

        {/* Две оси фасетов: происхождение (провенанс) + достоверность.
            Раньше одна сломанная ось мешала их — daif/maudu из status всегда 0. */}
        <div className="mb-5 space-y-2.5">
          <div className="flex items-center gap-2">
            <span className="w-24 shrink-0 text-xs font-medium uppercase tracking-wide text-ink-500">
              {t('hadith.facet.provenance.label')}
            </span>
            <FilterChips
              options={provenanceChips}
              value={provenanceFilter}
              onChange={(v) => setProvenanceFilter(v as ProvenanceFilter)}
              ariaLabel={t('hadith.facet.provenance.label')}
              className="min-w-0 flex-1"
            />
          </div>
          <div className="flex items-center gap-2">
            <span className="w-24 shrink-0 text-xs font-medium uppercase tracking-wide text-ink-500">
              {t('hadith.facet.authenticity.label')}
            </span>
            <FilterChips
              options={authenticityChips}
              value={authenticityFilter}
              onChange={(v) => setAuthenticityFilter(v as AuthenticityFilter)}
              ariaLabel={t('hadith.facet.authenticity.label')}
              className="min-w-0 flex-1"
            />
          </div>
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
              <ul className="space-y-3">
                {state.data.items.map((h) => (
                  <li key={h.id}>
                    <Link
                      to={`/hadith/hadiths/${h.id}`}
                      className="block rounded-md focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
                    >
                      <Card interactive className="p-4">
                        <div className="mb-2 flex items-center gap-2 text-xs text-ink-500">
                          {h.collectionId && collectionName.get(h.collectionId) && (
                            <span className="font-medium text-ink-600">
                              {collectionName.get(h.collectionId)}
                            </span>
                          )}
                          {h.primaryNumber != null && (
                            <span className="font-mono">№{h.primaryNumber}</span>
                          )}
                          <span
                            className={`rounded-sm px-1.5 py-0.5 text-xs font-semibold ${statusClass(h.status)}`}
                          >
                            {h.status}
                          </span>
                        </div>
                        <p
                          className="font-arabic text-lg leading-loose text-ink-900 line-clamp-3"
                          dir="rtl"
                        >
                          {h.previewMatn || h.normalizedMatn || '—'}
                        </p>
                      </Card>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
            <Pagination
              page={page}
              totalPages={state.data.totalPages}
              totalElements={state.data.totalElements}
              pageSize={PAGE_SIZE}
              onPageChange={goToPage}
            />
          </>
        )}
      </div>
    </main>
  );
}

export default HadithListPage;
