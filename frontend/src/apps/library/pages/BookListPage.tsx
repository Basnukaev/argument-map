import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import {
  BookOpen,
  AlertCircle,
  Loader2,
  Download,
  Pencil,
  X,
  Heart,
} from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import Button from '@/shared/components/ui/Button';
import ListToolbar from '@/shared/components/ui/ListToolbar';
import SearchInput from '@/shared/components/ui/SearchInput';
import FilterChips from '@/shared/components/ui/FilterChips';
import SortSelect from '@/shared/components/ui/SortSelect';
import Pagination from '@/shared/components/ui/Pagination';
import BookEditModal from '@/shared/components/library/BookEditModal';
import VisibilityBadge from '@/shared/components/visibility/VisibilityBadge';
import { apiGetRaw, apiPostRaw, apiDeleteRaw, formatApiError } from '@/shared/api/client';
import { formatPermissionError } from '@/shared/api/permissionErrors';
import { toast } from '@/shared/stores/toastStore';
import { useAuthStore } from '@/shared/stores/authStore';
import { useT, type DictKey } from '@/shared/i18n';
import { usePagedList } from '@/shared/hooks/usePagedList';
import type { components } from '@/shared/api/types';

type Book = components['schemas']['BookSummaryResponse'];
type BookType = NonNullable<Book['bookType']>;
type BookDetailResponse = components['schemas']['BookDetailResponse'];
type AuthorityResponse = components['schemas']['AuthorityResponse'];
type PagedAuthorities = components['schemas']['PagedResponseAuthorityResponse'];
type CollectionResponse = components['schemas']['CollectionResponse'];

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

/** Ключи в словаре через book.type.* - подцепляем через useT() */
const BOOK_TYPE_DICT_KEY: Record<BookType, DictKey> = {
  QURAN: 'book.type.QURAN',
  HADITH_COLLECTION: 'book.type.HADITH_COLLECTION',
  BOOK: 'book.type.BOOK',
  ARTICLE: 'book.type.ARTICLE',
  MANUSCRIPT: 'book.type.MANUSCRIPT',
};

/** content_kind → словарный ключ для chip'а доступности контента. Показываем
 * только FILE_ONLY («Только PDF») - значимый сигнал что текста нет (archive.org
 * сканы). TEXT_AND_FILE / TEXT_ONLY / undefined chip не получают: текст -
 * ожидание по умолчанию, лишний шум на карточке не нужен. */
const CONTENT_KIND_CHIP: Partial<Record<NonNullable<Book['contentKind']>, DictKey>> = {
  FILE_ONLY: 'library.content_kind.file_only',
};

/** Toned chip-классы по типу книги. v2 design: единый ink chip кроме
 * Quran/Hadith (accent для отделения религиозного канона от обычных книг) */
const BOOK_TYPE_BADGE: Record<BookType, string> = {
  QURAN: 'bg-ok-100 text-ok-700',
  HADITH_COLLECTION: 'bg-warn-100 text-warn-700',
  BOOK: 'bg-ink-100 text-ink-700',
  ARTICLE: 'bg-ink-100 text-ink-700',
  MANUSCRIPT: 'bg-type-abstract-bg text-type-abstract-fg',
};

/** Стабильный цвет на основе bookId для Card.Cover - выглядит как
 * индивидуальная обложка. Используем выделенную cover-палитру
 * (--cover-1..5, см. tokens.css): mid-lightness в light, приглушённую
 * в dark ([data-theme="dark"] override) - белый текст читается в обеих
 * темах, обложки «спокойные» в тёмной теме (не слепят). Раньше брали
 * *-ink (foreground) токены, которые в dark взлетают до ~85% lightness
 * = блёклые яркие пятна. Cover должен быть solid color (не gradient) -
 * см. handoff/04 */
function coverColorFor(id: string): string {
  const palette = [
    'var(--cover-1)',
    'var(--cover-2)',
    'var(--cover-3)',
    'var(--cover-4)',
    'var(--cover-5)',
  ];
  let hash = 0;
  for (let i = 0; i < id.length; i++) hash = (hash * 31 + id.charCodeAt(i)) | 0;
  return palette[Math.abs(hash) % palette.length]!;
}

/** Filter chips - применяется client-side поверх загруженной страницы
 * (backend не поддерживает ?visibility=). «Мои» - strict проверка
 * `book.createdBy === currentUser.id` (точное owner-equality, не
 * approximation visibility=PRIVATE: backlog tech debt round 4 #8).
 * Остальные SHARED/PUBLIC - по `book.visibility` */
type LibraryFilter = 'ALL' | 'MINE' | 'SHARED' | 'PUBLIC';
const LIBRARY_FILTERS: ReadonlyArray<LibraryFilter> = [
  'ALL',
  'MINE',
  'SHARED',
  'PUBLIC',
];

/** Sort - client-side поверх загруженного. Backend default = createdAt DESC
 * (стабильный порядок) = `latest`. `alphabetical` сортирует по title через
 * localeCompare. Server-side sort через ?sort=field,DESC - в backlog */
type SortKey = 'recent' | 'popular' | 'alphabetical';

function BookListPage() {
  const t = useT();
  const navigate = useNavigate();
  const [libraryFilter, setLibraryFilter] = useState<LibraryFilter>('ALL');
  // Хранится currentUserId для strict MINE-фильтра. null = anonymous (или
  // bootstrap ещё идёт) - MINE вернёт empty (см. displayedBooks)
  const currentUserId = useAuthStore((s) => s.user?.id ?? null);
  const [typeFilter, setTypeFilter] = useState<BookType | 'ALL'>('ALL');
  const [authorityFilter, setAuthorityFilter] = useState<AuthorityResponse | null>(null);
  const [sortBy, setSortBy] = useState<SortKey>('recent');
  const [editingBook, setEditingBook] = useState<BookDetailResponse | null>(null);
  const [loadingEdit, setLoadingEdit] = useState<string | null>(null);
  // Vision 49d Section 2.2 — Set bookIds в default коллекции "Избранное"
  const [favBookIds, setFavBookIds] = useState<Set<string>>(new Set());
  const [favLoadingId, setFavLoadingId] = useState<string | null>(null);
  // Под-проект #2.B: книга-сборник хадисов (bookType=HADITH_COLLECTION) - это
  // вторая «репрезентация» сборника. Открывается не в ридере (lib_pages пустые,
  // контент в hd_*), а в обозревателе хадисов. По клику резолвим collection.id
  // через мост Source и навигируем в /hadith?collectionId=. resolvingBookId -
  // book.id у которого by-book запрос in-flight (показывает спиннер).
  const [resolvingBookId, setResolvingBookId] = useState<string | null>(null);

  // Initial fetch favorites - один раз на mount
  useEffect(() => {
    if (!currentUserId) return;
    const controller = new AbortController();
    apiGetRaw<Array<{ bookId: string }>>(
      '/api/v1/library/collections?name=Избранное',
      { signal: controller.signal },
    )
      .then((entries) => {
        if (controller.signal.aborted) return;
        setFavBookIds(new Set(entries.map((e) => e.bookId)));
      })
      .catch(() => {
        // silent - favorites не critical, не показываем error
      });
    return () => controller.abort();
  }, [currentUserId]);

  const handleToggleFavorite = useCallback(async (bookId: string, currentlyFav: boolean) => {
    setFavLoadingId(bookId);
    try {
      if (currentlyFav) {
        await apiDeleteRaw(`/api/v1/library/collections/${bookId}?name=${encodeURIComponent('Избранное')}`);
        setFavBookIds((prev) => {
          const next = new Set(prev);
          next.delete(bookId);
          return next;
        });
      } else {
        await apiPostRaw('/api/v1/library/collections', { bookId });
        setFavBookIds((prev) => new Set(prev).add(bookId));
      }
    } catch (e) {
      toast.error(formatApiError(e, t('common.error')));
    } finally {
      setFavLoadingId(null);
    }
  }, [t]);

  /** HADITH_COLLECTION книга → обозреватель хадисов. Резолвим collection.id
   * через GET /hadith/collections/by-book/{bookId} (мост Source) и навигируем
   * в /hadith?collectionId=. На 404 (или любой ошибке) - defensive fallback в
   * обычный ридер /books/{bookId}. Запрос только по клику, не для каждой карточки. */
  const handleOpenHadithCollection = useCallback(
    async (bookId: string) => {
      if (resolvingBookId) return;
      setResolvingBookId(bookId);
      try {
        const collection = await apiGetRaw<CollectionResponse>(
          `/api/v1/hadith/collections/by-book/${bookId}`,
        );
        if (collection.id) {
          navigate(`/hadith?collectionId=${collection.id}`);
        } else {
          // мост есть, но id нет (неожиданно) - fallback в ридер
          navigate(`/books/${bookId}`);
        }
      } catch {
        // 404 = моста нет, либо иная ошибка - открываем обычный ридер
        navigate(`/books/${bookId}`);
      } finally {
        setResolvingBookId(null);
      }
    },
    [navigate, resolvingBookId],
  );

  const handleEdit = async (bookId: string) => {
    setLoadingEdit(bookId);
    try {
      const detail = await apiGetRaw<BookDetailResponse>(
        `/api/v1/library/books/${bookId}`,
      );
      setEditingBook(detail);
    } catch (e) {
      // 22.c.f: GET book detail может вернуть 403 forbidden-book-access для
      // приватных чужих книг (теоретически они в list не появляются, но
      // defensive)
      const permMsg = formatPermissionError(e, t);
      toast.error(permMsg ?? formatApiError(e, t('common.error')));
    } finally {
      setLoadingEdit(null);
    }
  };

  /** URL builder - объединяет current filter state. Server-side: ?q=,
   * ?type=, ?authorityId=, ?page=, ?size=, ?sort= (Vision 49d Phase 1).
   * `q` приходит от usePagedList (debounced). Visibility - client-side
   * (libraryFilter MINE/SHARED/PUBLIC) поверх загруженной страницы. */
  const buildBooksUrl = useCallback(
    (page: number, q: string): string => {
      const params = new URLSearchParams();
      params.set('page', String(page));
      params.set('size', String(PAGE_SIZE));
      params.set('sort', sortBy);
      if (q) params.set('q', q);
      if (typeFilter !== 'ALL') params.set('type', typeFilter);
      if (authorityFilter?.id) params.set('authorityId', authorityFilter.id);
      return `/api/v1/library/books?${params.toString()}`;
    },
    [typeFilter, authorityFilter, sortBy],
  );

  // Debounce + нумерованная пагинация (?page= в URL, 1-based). Server-side
  // фильтры (type/authority/sort) — в deps: их смена сбрасывает на стр.1.
  // Visibility — client-side (ниже), при активном таком фильтре пагинация
  // скрыта (новые страницы пришли бы с бэка но скрылись client-side).
  const { state, searchInput, setSearchInput, page, goToPage } =
    usePagedList<Book>({
      buildUrl: buildBooksUrl,
      debounceMs: SEARCH_DEBOUNCE_MS,
      deps: [typeFilter, authorityFilter, sortBy],
      fallbackError: 'Не удалось загрузить библиотеку',
    });

  /** Client-side filter: visibility/owner поверх загруженной страницы.
   * Search/type/authorityId уже применены server-side через
   * ?q=&type=&authorityId= */
  const displayedBooks = useMemo(() => {
    if (state.kind !== 'success') return [];
    let list = state.data.items;
    if (libraryFilter === 'MINE') {
      // Anonymous (currentUserId=null) - пустой список (нет owner identity)
      list = currentUserId
        ? list.filter((b) => b.createdBy === currentUserId)
        : [];
    } else if (libraryFilter === 'SHARED' || libraryFilter === 'PUBLIC') {
      list = list.filter((b) => b.visibility === libraryFilter);
    }
    // Vision 49d Phase 1: sort теперь server-side через ?sort= param.
    // Client-side sort removed - бэк делает ORDER BY правильно для
    // recent/popular/alphabetical.
    return list;
  }, [state, libraryFilter, currentUserId]);

  /** Local filter активен - скрываем Load More: новые items приходят но
   * скрыты client-side фильтром, юзер не поймёт */
  const localFilterActive = libraryFilter !== 'ALL';

  /** Чипы видимости (ALL/MINE/SHARED/PUBLIC) - client-side фильтр. */
  const visibilityChips = useMemo(
    () =>
      LIBRARY_FILTERS.map((value) => ({
        value,
        label:
          value === 'ALL'
            ? t('library.overview.filter.all')
            : value === 'MINE'
              ? t('library.overview.filter.my')
              : value === 'SHARED'
                ? t('library.overview.filter.shared')
                : t('library.overview.filter.public'),
      })),
    [t],
  );

  /** Чипы типа книги (server-side ?type=). */
  const typeChips = useMemo(
    () =>
      (['ALL', 'BOOK', 'HADITH_COLLECTION', 'QURAN', 'ARTICLE', 'MANUSCRIPT'] as ReadonlyArray<BookType | 'ALL'>).map(
        (value) => ({
          value,
          label: value === 'ALL' ? t('book.list.filter_all') : t(BOOK_TYPE_DICT_KEY[value]),
        }),
      ),
    [t],
  );

  const sortOptions = useMemo(
    () => [
      { value: 'recent', label: t('common.sort.recent') },
      { value: 'popular', label: t('common.sort.popular') },
      { value: 'alphabetical', label: t('common.sort.alphabetical') },
    ],
    [t],
  );

  return (
    <main className="min-h-screen bg-bg">
      <Header />

      <div className="mx-auto max-w-[1380px] px-3 py-6 sm:px-6 sm:py-8">
        <LibraryHero
          totalCount={state.kind === 'success' ? state.data.totalElements : null}
        />

        <div className="mb-6 space-y-3">
          {/* Главный бар: поиск (растягивается) + видимость + сортировка +
              authority autocomplete как action в конце */}
          <ListToolbar
            search={
              <SearchInput
                value={searchInput}
                onChange={setSearchInput}
                placeholder={t('library.overview.search_placeholder')}
                ariaLabel={t('common.search')}
                className="w-full"
              />
            }
            filters={
              <FilterChips
                options={visibilityChips}
                value={libraryFilter}
                onChange={(v) => setLibraryFilter(v as LibraryFilter)}
                ariaLabel={t('library.overview.filter.all')}
              />
            }
            sort={
              <SortSelect
                value={sortBy}
                onChange={(v) => setSortBy(v as SortKey)}
                options={sortOptions}
                label={t('library.overview.sort.label')}
              />
            }
            actions={
              <AuthorityFilter
                selected={authorityFilter}
                onChange={setAuthorityFilter}
              />
            }
          />

          {/* Type filter - вторичный ряд чипов (server-side ?type=) */}
          <FilterChips
            options={typeChips}
            value={typeFilter}
            onChange={(v) => setTypeFilter(v as BookType | 'ALL')}
            ariaLabel={t('book.list.filter_all')}
          />
        </div>

        {state.kind === 'loading' && (
          <div className="flex items-center justify-center gap-2 py-20 text-sm text-ink-500">
            <Loader2 size={16} className="animate-spin" aria-hidden />
            {t('common.loading')}
          </div>
        )}

        {state.kind === 'error' && (
          <Card className="mx-auto max-w-2xl p-5 border-err-500/40 bg-err-100">
            <div className="flex items-start gap-3">
              <AlertCircle
                size={20}
                className="mt-0.5 shrink-0 text-err-700"
                aria-hidden
              />
              <div>
                <p className="font-semibold text-err-700">{t('common.error')}</p>
                <p className="mt-1 text-sm text-err-700">{state.message}</p>
              </div>
            </div>
          </Card>
        )}

        {state.kind === 'success' && state.data.items.length === 0 && (
          <EmptyState />
        )}

        {state.kind === 'success' &&
          state.data.items.length > 0 &&
          displayedBooks.length === 0 && (
            <p className="text-center text-sm text-ink-500">
              {t('topic.list.not_found')}
            </p>
          )}

        {state.kind === 'success' && displayedBooks.length > 0 && (
          <>
            <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5">
              {displayedBooks
                .filter((b): b is Book & { id: string } => Boolean(b.id))
                .map((book) => (
                  <li key={book.id}>
                    <BookCard
                      book={book}
                      onEdit={handleEdit}
                      editLoading={loadingEdit === book.id}
                      isFavorite={favBookIds.has(book.id)}
                      onToggleFavorite={handleToggleFavorite}
                      favLoading={favLoadingId === book.id}
                      onOpenHadithCollection={handleOpenHadithCollection}
                      resolving={resolvingBookId === book.id}
                    />
                  </li>
                ))}
            </ul>

            {!localFilterActive && (
              <Pagination
                page={page}
                totalPages={state.data.totalPages}
                totalElements={state.data.totalElements}
                pageSize={PAGE_SIZE}
                onPageChange={goToPage}
              />
            )}
          </>
        )}
      </div>
      {editingBook && (
        <BookEditModal
          book={editingBook}
          onClose={() => setEditingBook(null)}
          onSaved={(updated) => {
            // BookSummary поля не меняются BookEditModal'ом - refetch не нужен
            setEditingBook(null);
            void updated;
          }}
        />
      )}
    </main>
  );
}

/** Hero header - title + description + total count. Compact на mobile.
 * «Импорт из Shamela» - secondary т.к. library overview преимущественно
 * read-only поверхность, импорт редкая admin-операция */
interface LibraryHeroProps {
  totalCount: number | null;
}

function LibraryHero({ totalCount }: LibraryHeroProps) {
  const t = useT();
  const countLabel = totalCount === null
    ? null
    : t('library.overview.total_books').replace('{count}', String(totalCount));

  return (
    <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0 flex-1">
        <div className="mb-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-500">
          {t('book.list.eyebrow')}
        </div>
        <h1 className="font-serif text-[28px] font-semibold leading-tight tracking-tight text-ink-900">
          {t('library.overview.title')}
        </h1>
        <p className="mt-1.5 max-w-[680px] text-sm text-ink-500">
          {t('library.overview.description')}
          {countLabel && (
            <>
              {' '}·{' '}
              <span className="font-medium text-ink-700">
                <bdi dir="ltr">{countLabel}</bdi>
              </span>
            </>
          )}
        </p>
      </div>
      <Link to="/admin/shamela">
        <Button variant="secondary" icon={Download}>
          {t('book.list.import_from_shamela')}
        </Button>
      </Link>
    </header>
  );
}

/** Authority dropdown - text input + debounced search через
 * /api/v1/authorities?q=. Dropdown показывает до 10 results,
 * click select. Без virtualization (MVP размер списка) */
interface AuthorityFilterProps {
  selected: AuthorityResponse | null;
  onChange: (v: AuthorityResponse | null) => void;
}

function AuthorityFilter({ selected, onChange }: AuthorityFilterProps) {
  const t = useT();
  const [query, setQuery] = useState('');
  const [debouncedQ, setDebouncedQ] = useState('');
  const [open, setOpen] = useState(false);
  const [results, setResults] = useState<AuthorityResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handle = setTimeout(() => setDebouncedQ(query.trim()), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(handle);
  }, [query]);

  /** Fetch authorities когда dropdown открыт и query поменялся.
   * setLoading(true) синхронно в effect не вызываем (eslint
   * react-hooks/set-state-in-effect) - через microtask Promise.resolve
   * правило допускает (async callback) */
  useEffect(() => {
    if (!open) return;
    const controller = new AbortController();
    void Promise.resolve().then(() => {
      if (controller.signal.aborted) return;
      setLoading(true);
    });
    const params = new URLSearchParams();
    if (debouncedQ) params.set('q', debouncedQ);
    params.set('size', '10');
    apiGetRaw<PagedAuthorities>(`/api/v1/authorities?${params.toString()}`, {
      signal: controller.signal,
    })
      .then((paged) => {
        if (controller.signal.aborted) return;
        setResults(paged.items ?? []);
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        // тихо игнорим - dropdown покажет empty state
        setResults([]);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [debouncedQ, open]);

  // close on outside click
  useEffect(() => {
    if (!open) return;
    const handleClick = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [open]);

  const displayValue = selected?.name ?? selected?.fullName ?? '';

  return (
    <div ref={wrapperRef} className="relative">
      <div className="flex h-9 min-w-[180px] items-center rounded-md border border-ink-200 bg-elevated transition-all focus-within:border-accent-500 focus-within:ring-2 focus-within:ring-accent-500/20">
        <input
          type="search"
          value={open ? query : displayValue}
          onChange={(e) => {
            setQuery(e.target.value);
            if (!open) setOpen(true);
          }}
          onFocus={() => {
            setOpen(true);
            setQuery('');
          }}
          placeholder={t('library.overview.filter.authority_placeholder')}
          className="flex-1 bg-transparent px-3 text-xs text-ink-900 outline-none placeholder:text-ink-400"
          aria-label={t('library.overview.filter.authority')}
        />
        {selected && (
          <button
            type="button"
            onClick={() => {
              onChange(null);
              setQuery('');
            }}
            title={t('library.overview.filter.authority_clear')}
            aria-label={t('library.overview.filter.authority_clear')}
            className="me-2 grid h-6 w-6 place-items-center rounded-sm text-ink-400 hover:bg-ink-100 hover:text-ink-700"
          >
            <X size={12} aria-hidden />
          </button>
        )}
      </div>
      {open && (
        <div className="absolute z-20 mt-1 max-h-72 w-full min-w-[240px] overflow-y-auto rounded-md border border-border bg-elevated shadow-sh2">
          {loading && (
            <div className="px-3 py-2 text-xs text-ink-500">
              {t('common.loading')}
            </div>
          )}
          {!loading && results.length === 0 && (
            <div className="px-3 py-2 text-xs text-ink-500">
              {t('topic.list.not_found')}
            </div>
          )}
          {!loading &&
            results.map((auth) => (
              <button
                key={auth.id}
                type="button"
                onClick={() => {
                  onChange(auth);
                  setOpen(false);
                  setQuery('');
                }}
                className="block w-full px-3 py-2 text-start text-xs text-ink-700 hover:bg-ink-100"
              >
                <span dir="auto">{auth.name ?? auth.fullName ?? '—'}</span>
                {auth.era && (
                  <span className="ms-2 text-ink-400">· {auth.era}</span>
                )}
              </button>
            ))}
        </div>
      )}
    </div>
  );
}

/** Illustrated empty state с CTA на shamela import. Иконка крупная в
 * круге, описательный текст (не «список пуст»), call-to-action - явная
 * кнопка primary т.к. это единственный action на странице если пусто */
function EmptyState() {
  const t = useT();
  return (
    <Card className="mx-auto max-w-2xl p-12 text-center">
      <div className="mx-auto mb-4 grid h-16 w-16 place-items-center rounded-full bg-ink-100">
        <BookOpen size={28} className="text-ink-400" aria-hidden />
      </div>
      <h2 className="font-serif text-xl font-semibold text-ink-900">
        {t('library.overview.empty_state.title')}
      </h2>
      <p className="mt-2 text-sm text-ink-500">
        {t('library.overview.empty_state.description')}
      </p>
      <Link to="/admin/shamela" className="mt-5 inline-block">
        <Button icon={Download}>
          {t('library.overview.empty_state.cta')}
        </Button>
      </Link>
    </Card>
  );
}

interface BookCardProps {
  book: Book & { id: string };
  onEdit: (bookId: string) => void;
  editLoading: boolean;
  isFavorite: boolean;
  onToggleFavorite: (bookId: string, currentlyFav: boolean) => void;
  favLoading: boolean;
  /** Под-проект #2.B: открыть сборник хадисов в обозревателе (резолв collection
   * через by-book мост + навигация в /hadith?collectionId=). */
  onOpenHadithCollection: (bookId: string) => void;
  /** by-book запрос для этой книги in-flight - карточка показывает спиннер. */
  resolving: boolean;
}

function BookCard({
  book,
  onEdit,
  editLoading,
  isFavorite,
  onToggleFavorite,
  favLoading,
  onOpenHadithCollection,
  resolving,
}: BookCardProps) {
  const t = useT();
  const bookType = book.bookType ?? 'BOOK';
  const fallbackTitle = t('reader.no_book_title');
  const title = book.title ?? fallbackTitle;
  const initialLetter = title.charAt(0).toUpperCase() || '?';
  // HADITH_COLLECTION = вторая репрезентация сборника: контент в hd_*, не в
  // lib_pages, поэтому ридер был бы пуст. Карточка ведёт в обозреватель хадисов.
  const isHadithCollection = bookType === 'HADITH_COLLECTION';

  const cardInner = (
    <Card interactive className="flex h-full flex-col overflow-hidden">
      {/* Реальная обложка (book.coverUrl, напр. archive.org thumbnail) если
          задана; иначе сгенерированная letter-обложка. img onError →
          graceful fallback на letter (см. Card.Cover). Спиннер резолва
          сборника показываем только в letter-режиме (resolving не для img). */}
      <Card.Cover
        color={coverColorFor(book.id)}
        imageUrl={book.coverUrl || undefined}
      >
        {resolving ? <Loader2 size={28} className="animate-spin" aria-hidden /> : initialLetter}
      </Card.Cover>
      {/* flex-1 чтобы body тянул карточку до равной высоты; meta
          прижат к низу через mt-auto - id-строка всегда на одной
          базовой линии у всех карточек ряда */}
      <Card.Body className="flex-1">
        {/* flex-wrap: при узкой карточке chips переносятся аккуратно,
            не выходя за края eyebrow-строки */}
        <Card.Eyebrow>
          <div className="flex flex-wrap items-center gap-1">
            <span
              className={`inline-flex items-center rounded-sm px-1.5 py-0.5 text-xs font-semibold uppercase tracking-wider ${BOOK_TYPE_BADGE[bookType]}`}
            >
              {t(BOOK_TYPE_DICT_KEY[bookType])}
            </span>
            {book.language && (
              <span className="inline-flex items-center rounded-sm bg-ink-100 px-1.5 py-0.5 text-xs font-mono uppercase text-ink-600">
                <bdi dir="ltr">{book.language}</bdi>
              </span>
            )}
            {/* Content-kind hint - только для не-хадис книг. FILE_ONLY
                («Только PDF») сигналит что читаемого текста нет, остальные
                kind'ы chip не получают (см. CONTENT_KIND_CHIP). */}
            {!isHadithCollection && book.contentKind && CONTENT_KIND_CHIP[book.contentKind] && (
              <span className="inline-flex items-center rounded-sm bg-ink-100 px-1.5 py-0.5 text-xs font-medium text-ink-600">
                {t(CONTENT_KIND_CHIP[book.contentKind]!)}
              </span>
            )}
            <VisibilityBadge
              visibility={book.visibility}
              labelPrefix="book.visibility"
              compact
            />
          </div>
        </Card.Eyebrow>
        <Card.Title clamp>{title}</Card.Title>
        <Card.Meta className="mt-auto pt-1">
          <span className="font-mono text-xs">
            <bdi dir="ltr">{book.id.slice(0, 8)}</bdi>
          </span>
        </Card.Meta>
      </Card.Body>
    </Card>
  );

  return (
    <div className="relative h-full">
      {/* Vision 49d Section 2.2 - Favorite (heart) button. End-12 = чуть
          левее pencil чтобы не накладываться */}
      <button
        type="button"
        onClick={(e) => {
          e.preventDefault();
          e.stopPropagation();
          onToggleFavorite(book.id, isFavorite);
        }}
        disabled={favLoading}
        title={isFavorite ? t('library.fav.remove') : t('library.fav.add')}
        aria-label={isFavorite ? t('library.fav.remove') : t('library.fav.add')}
        className={`absolute end-11 top-2 z-10 grid h-7 w-7 place-items-center rounded-sm shadow-sh1 backdrop-blur disabled:opacity-50 ${
          isFavorite
            ? 'bg-err-100 text-err-500 hover:bg-err-100'
            : 'bg-elevated/90 text-ink-600 hover:bg-elevated hover:text-err-500'
        }`}
      >
        {favLoading ? (
          <Loader2 size={13} className="animate-spin" aria-hidden />
        ) : (
          <Heart size={13} fill={isFavorite ? 'currentColor' : 'none'} aria-hidden />
        )}
      </button>
      <button
        type="button"
        onClick={(e) => {
          e.preventDefault();
          e.stopPropagation();
          onEdit(book.id);
        }}
        disabled={editLoading}
        title={t('admin.edit_book.action')}
        className="absolute end-2 top-2 z-10 grid h-7 w-7 place-items-center rounded-sm bg-elevated/90 text-ink-600 shadow-sh1 backdrop-blur hover:bg-elevated hover:text-accent-700 disabled:opacity-50"
      >
        {editLoading ? (
          <Loader2 size={13} className="animate-spin" aria-hidden />
        ) : (
          <Pencil size={13} aria-hidden />
        )}
      </button>
      {isHadithCollection ? (
        // Сборник хадисов: <button>, не <Link> - таргет резолвится по клику
        // (by-book → collectionId), поэтому статичного href нет.
        <button
          type="button"
          onClick={() => onOpenHadithCollection(book.id)}
          disabled={resolving}
          aria-label={`${title} — ${t('book.open_hadith_explorer')}`}
          title={t('book.open_hadith_explorer')}
          className="group block h-full w-full text-start focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 focus-visible:ring-offset-2 focus-visible:ring-offset-bg rounded-md disabled:cursor-wait"
        >
          {cardInner}
        </button>
      ) : (
        <Link
          to={`/books/${book.id}`}
          aria-label={title}
          className="group block h-full focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 focus-visible:ring-offset-2 focus-visible:ring-offset-bg rounded-md"
        >
          {cardInner}
        </Link>
      )}
    </div>
  );
}

export default BookListPage;
