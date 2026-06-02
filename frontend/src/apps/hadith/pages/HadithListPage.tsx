import { useCallback, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { BookOpen, Loader2, Users, Info } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import ListToolbar from '@/shared/components/ui/ListToolbar';
import SearchInput from '@/shared/components/ui/SearchInput';
import FilterChips from '@/shared/components/ui/FilterChips';
import SortSelect from '@/shared/components/ui/SortSelect';
import LoadMoreButton from '@/shared/components/ui/LoadMoreButton';
import { useT } from '@/shared/i18n';
import { usePagedSearch } from '@/shared/hooks/usePagedSearch';
import { useApiQuery } from '@/shared/hooks/useApiQuery';
import type { components } from '@/shared/api/types';

type HadithItem = components['schemas']['HadithResponse'];
type CollectionItem = components['schemas']['CollectionResponse'];

const PAGE_SIZE = 20;
type SortKey = 'number' | 'alphabetical' | 'recent';
type StatusFilter = 'CANONICAL' | 'VARIANT' | 'WEAK' | 'FABRICATED' | 'ALL';

function statusClass(status: string | undefined): string {
  switch (status) {
    case 'CANONICAL':
      return 'bg-emerald-100 text-emerald-700';
    case 'WEAK':
      return 'bg-amber-100 text-amber-700';
    case 'FABRICATED':
      return 'bg-rose-100 text-rose-700';
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
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
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

  const statusChips = useMemo(
    () => [
      { value: 'ALL', label: t('hadith.filter.all') },
      { value: 'CANONICAL', label: t('hadith.filter.canonical') },
      { value: 'VARIANT', label: t('hadith.filter.variant') },
      { value: 'WEAK', label: t('hadith.filter.weak') },
      { value: 'FABRICATED', label: t('hadith.filter.fabricated') },
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
      if (statusFilter !== 'ALL') params.set('status', statusFilter);
      if (collectionId) params.set('collectionId', collectionId);
      return `/api/v1/hadith/hadiths?${params.toString()}`;
    },
    [statusFilter, collectionId, sort],
  );

  const { state, searchInput, setSearchInput, loadMore, loadingMore } = usePagedSearch<HadithItem>({
    buildUrl,
    deps: [statusFilter, collectionId, sort],
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

        {/* фильтр по статусу + легенда (статусы непрозрачны без подсказки) */}
        <div className="mb-5 flex items-center gap-2">
          <FilterChips
            options={statusChips}
            value={statusFilter}
            onChange={(v) => setStatusFilter(v as StatusFilter)}
            ariaLabel={t('hadith.filter.all')}
            className="min-w-0 flex-1"
          />
          <StatusLegend />
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
            <LoadMoreButton
              onClick={loadMore}
              loading={loadingMore}
              hasNext={state.data.hasNext}
              shownCount={state.data.items.length}
              totalCount={state.data.totalElements}
            />
          </>
        )}
      </div>
    </main>
  );
}

/**
 * StatusLegend - info-поповер расшифровывающий статусы хадиса
 * (CANONICAL / VARIANT / WEAK / FABRICATED). Юзер жаловался что
 * статусы непонятны - короткая инлайн-легенда по клику на (i).
 * Toggle-кнопка + dropdown-панель, закрывается повторным кликом /
 * blur. Цветные точки совпадают с statusClass карточек.
 */
function StatusLegend() {
  const t = useT();
  const [open, setOpen] = useState(false);
  const items: ReadonlyArray<{ key: string; dot: string; text: string }> = [
    { key: 'CANONICAL', dot: 'bg-emerald-500', text: t('hadith.legend.canonical') },
    { key: 'VARIANT', dot: 'bg-ink-400', text: t('hadith.legend.variant') },
    { key: 'WEAK', dot: 'bg-amber-500', text: t('hadith.legend.weak') },
    { key: 'FABRICATED', dot: 'bg-rose-500', text: t('hadith.legend.fabricated') },
  ];
  return (
    <div className="relative shrink-0">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        onBlur={() => setOpen(false)}
        aria-expanded={open}
        aria-label={t('hadith.legend.title')}
        title={t('hadith.legend.title')}
        className={`grid h-8 w-8 place-items-center rounded-full border transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500/40 ${
          open
            ? 'border-accent-500 bg-accent-50 text-accent-700'
            : 'border-border-strong bg-elevated text-ink-500 hover:bg-ink-100 hover:text-ink-700'
        }`}
      >
        <Info size={15} aria-hidden />
      </button>
      {open && (
        <div className="absolute end-0 z-30 mt-1.5 w-72 rounded-md border border-border bg-elevated p-3 shadow-sh3">
          <p className="mb-2 text-xs font-semibold text-ink-900">
            {t('hadith.legend.title')}
          </p>
          <ul className="space-y-1.5">
            {items.map((it) => (
              <li key={it.key} className="flex items-start gap-2 text-xs text-ink-600">
                <span
                  className={`mt-1 h-2 w-2 shrink-0 rounded-full ${it.dot}`}
                  aria-hidden
                />
                <span>{it.text}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

export default HadithListPage;
