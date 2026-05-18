import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import {
  BookOpen,
  Search,
  AlertCircle,
  Loader2,
  Download,
  ChevronDown,
  Pencil,
  X,
} from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import Button from '@/shared/components/ui/Button';
import BookEditModal from '@/apps/admin/components/BookEditModal';
import VisibilityBadge from '@/shared/components/visibility/VisibilityBadge';
import { apiGetRaw, ApiError, formatApiError } from '@/shared/api/client';
import { formatPermissionError } from '@/shared/api/permissionErrors';
import { toast } from '@/shared/stores/toastStore';
import { useT, type DictKey } from '@/shared/i18n';
import type { AsyncState } from '@/shared/types/async';
import type { components } from '@/shared/api/types';

type Book = components['schemas']['BookSummaryResponse'];
type BookType = NonNullable<Book['bookType']>;
type BookDetailResponse = components['schemas']['BookDetailResponse'];
type PagedBooks = components['schemas']['PagedResponseBookSummaryResponse'];

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

interface BooksAccum {
  books: Book[];
  page: number;
  hasNext: boolean;
  totalElements: number;
}

/** Ключи в словаре через book.type.* - подцепляем через useT() */
const BOOK_TYPE_DICT_KEY: Record<BookType, DictKey> = {
  QURAN: 'book.type.QURAN',
  HADITH_COLLECTION: 'book.type.HADITH_COLLECTION',
  BOOK: 'book.type.BOOK',
  ARTICLE: 'book.type.ARTICLE',
  MANUSCRIPT: 'book.type.MANUSCRIPT',
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
 * индивидуальная обложка. 5 цветов выбраны так чтобы цвет читался в
 * обоих темах. Cover должен быть solid color (не gradient) - см. handoff/04 */
function coverColorFor(id: string): string {
  const palette = [
    'var(--c-accent-600)',
    'var(--c-type-abstract-fg)',
    'var(--c-type-empirical-fg)',
    'var(--c-ok-700)',
    'var(--c-warn-700)',
  ];
  let hash = 0;
  for (let i = 0; i < id.length; i++) hash = (hash * 31 + id.charCodeAt(i)) | 0;
  return palette[Math.abs(hash) % palette.length]!;
}

/** Visibility-фильтр chips - PRIVATE/SHARED/PUBLIC применяется client-side
 * поверх загруженной страницы (backend не поддерживает ?visibility=).
 * «Мои» книги через `BookSummary.createdBy` сделать не могу - backend не
 * отдаёт это поле в summary; PRIVATE приближённо соответствует «моим»
 * для типичного юзера */
type VisibilityFilter = 'ALL' | 'PRIVATE' | 'SHARED' | 'PUBLIC';
const VISIBILITY_FILTERS: ReadonlyArray<VisibilityFilter> = [
  'ALL',
  'PRIVATE',
  'SHARED',
  'PUBLIC',
];

type SortKey = 'added' | 'title' | 'type';

function BookListPage() {
  const t = useT();
  const [state, setState] = useState<AsyncState<BooksAccum>>({ kind: 'loading' });
  const [searchInput, setSearchInput] = useState('');
  const [searchQ, setSearchQ] = useState('');
  const [visibilityFilter, setVisibilityFilter] = useState<VisibilityFilter>('ALL');
  const [typeFilter, setTypeFilter] = useState<BookType | 'ALL'>('ALL');
  const [sortBy, setSortBy] = useState<SortKey>('added');
  const [editingBook, setEditingBook] = useState<BookDetailResponse | null>(null);
  const [loadingEdit, setLoadingEdit] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);

  /** Debounced search - после 300ms простоя sync'аем searchInput → searchQ.
   * searchQ - триггер refetch'а через useEffect ниже. UX: юзер печатает
   * быстро без сетевого спама, после паузы происходит запрос */
  useEffect(() => {
    const handle = setTimeout(() => {
      setSearchQ(searchInput.trim());
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(handle);
  }, [searchInput]);

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

  /** URL builder - объединяет current filter state. Server-side: ?q=, ?type=,
   * ?page=, ?size=. Visibility и sort - client-side (backend не поддерживает) */
  const buildBooksUrl = useCallback(
    (page: number): string => {
      const params = new URLSearchParams();
      params.set('page', String(page));
      params.set('size', String(PAGE_SIZE));
      if (searchQ) params.set('q', searchQ);
      if (typeFilter !== 'ALL') params.set('type', typeFilter);
      return `/api/v1/library/books?${params.toString()}`;
    },
    [searchQ, typeFilter],
  );

  /** Initial fetch + refetch при изменении server-side filters (q/type).
   * setState({kind:'loading'}) синхронно в effect не вызываем
   * (react-hooks/set-state-in-effect) - держим предыдущие данные пока
   * новые не пришли. На initial mount loading через useState initial value */
  useEffect(() => {
    const controller = new AbortController();
    apiGetRaw<PagedBooks>(buildBooksUrl(0), { signal: controller.signal })
      .then((paged) => {
        if (controller.signal.aborted) return;
        setState({
          kind: 'success',
          data: {
            books: (paged.items ?? []) as Book[],
            page: paged.page ?? 0,
            hasNext: paged.hasNext ?? false,
            totalElements: paged.totalElements ?? 0,
          },
        });
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        const message =
          e instanceof ApiError
            ? `${e.problem.title}${e.problem.detail ? ': ' + e.problem.detail : ''}`
            : e instanceof Error
              ? e.message
              : 'Не удалось загрузить библиотеку';
        setState({ kind: 'error', message });
      });
    return () => controller.abort();
  }, [buildBooksUrl]);

  /** Load More - подгружает следующую страницу. Аппендит к existing list.
   * Local filter (visibility) применяется поверх */
  const handleLoadMore = async () => {
    if (state.kind !== 'success' || !state.data.hasNext || loadingMore) return;
    setLoadingMore(true);
    try {
      const nextPage = state.data.page + 1;
      const resp = await apiGetRaw<PagedBooks>(buildBooksUrl(nextPage));
      const nextItems = (resp.items ?? []) as Book[];
      setState({
        kind: 'success',
        data: {
          books: [...state.data.books, ...nextItems],
          page: resp.page ?? nextPage,
          hasNext: resp.hasNext ?? false,
          totalElements: resp.totalElements ?? state.data.totalElements,
        },
      });
    } catch (e: unknown) {
      toast.error(formatApiError(e, t('common.error')));
    } finally {
      setLoadingMore(false);
    }
  };

  /** Client-side filter: visibility + sort поверх загруженной страницы.
   * Search/type/authorityId уже применены server-side через ?q=&type= */
  const displayedBooks = useMemo(() => {
    if (state.kind !== 'success') return [];
    let list = state.data.books;
    if (visibilityFilter !== 'ALL') {
      list = list.filter((b) => b.visibility === visibilityFilter);
    }
    if (sortBy === 'title') {
      list = [...list].sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''));
    } else if (sortBy === 'type') {
      list = [...list].sort((a, b) =>
        (a.bookType ?? '').localeCompare(b.bookType ?? ''),
      );
    }
    return list;
  }, [state, visibilityFilter, sortBy]);

  /** Local filter активен (visibility) - скрываем Load More: новые items
   * приходят но скрыты client-side фильтром, юзер не поймёт */
  const localFilterActive = visibilityFilter !== 'ALL';

  return (
    <main className="min-h-screen bg-bg">
      <Header />

      <div className="mx-auto max-w-[1380px] px-3 py-6 sm:px-6 sm:py-8">
        <LibraryHero
          totalCount={state.kind === 'success' ? state.data.totalElements : null}
        />

        <div className="mb-6 space-y-3">
          <div className="flex flex-wrap items-center gap-3">
            <div className="relative flex h-9 max-w-md flex-1 items-center rounded-md border border-border-strong bg-elevated transition-all focus-within:border-accent-500 focus-within:ring-2 focus-within:ring-accent-500/20">
              <Search size={15} className="ms-3 text-ink-400" aria-hidden />
              <input
                type="search"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder={t('library.overview.search_placeholder')}
                className="flex-1 bg-transparent px-3 text-sm text-ink-900 outline-none placeholder:text-ink-400"
                aria-label={t('common.search')}
              />
              {searchInput && (
                <button
                  type="button"
                  onClick={() => setSearchInput('')}
                  title={t('library.overview.search_clear')}
                  aria-label={t('library.overview.search_clear')}
                  className="me-2 grid h-6 w-6 place-items-center rounded-sm text-ink-400 hover:bg-ink-100 hover:text-ink-700"
                >
                  <X size={12} aria-hidden />
                </button>
              )}
            </div>

            <label className="ms-auto inline-flex items-center gap-2 text-xs text-ink-500">
              {t('book.list.sort_label')}
              <span className="relative inline-flex items-center">
                <select
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value as SortKey)}
                  className="h-9 appearance-none rounded-sm border border-ink-200 bg-elevated ps-3 pe-7 text-xs font-medium text-ink-900 outline-none focus:border-accent-500"
                >
                  <option value="added">{t('book.list.sort_added')}</option>
                  <option value="title">{t('book.list.sort_title')}</option>
                  <option value="type">{t('book.list.sort_type')}</option>
                </select>
                <ChevronDown
                  size={12}
                  aria-hidden
                  className="pointer-events-none absolute end-2 text-ink-400"
                />
              </span>
            </label>
          </div>

          {/* Visibility chips - PRIVATE/SHARED/PUBLIC. Mobile: overflow-x
              scroll для wrap'а с другими row'ами */}
          <div className="-mx-3 flex overflow-x-auto px-3 sm:mx-0 sm:overflow-visible sm:px-0">
            <div className="flex items-center gap-1 rounded-sm border border-ink-200 bg-elevated p-1 shrink-0">
              {VISIBILITY_FILTERS.map((value) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => setVisibilityFilter(value)}
                  className={
                    visibilityFilter === value
                      ? 'rounded-sm bg-accent-600 px-2.5 py-1 text-xs font-medium text-ink-0 whitespace-nowrap'
                      : 'rounded-sm px-2.5 py-1 text-xs text-ink-600 hover:bg-ink-100 hover:text-ink-900 transition-colors whitespace-nowrap'
                  }
                >
                  {value === 'ALL'
                    ? t('library.overview.filter.all')
                    : value === 'PRIVATE'
                      ? t('library.overview.filter.my')
                      : value === 'SHARED'
                        ? t('library.overview.filter.shared')
                        : t('library.overview.filter.public')}
                </button>
              ))}
            </div>
          </div>

          {/* Type filter - сохранён в виде secondary row для backward compat
              после рефакторинга. Может быть скрыт в backlog upgrade */}
          <div className="-mx-3 flex overflow-x-auto px-3 sm:mx-0 sm:overflow-visible sm:px-0">
            <div className="flex items-center gap-1 rounded-sm border border-ink-200 bg-elevated p-1 shrink-0">
              {(['ALL', 'BOOK', 'HADITH_COLLECTION', 'QURAN', 'ARTICLE', 'MANUSCRIPT'] as ReadonlyArray<BookType | 'ALL'>).map((value) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => setTypeFilter(value)}
                  className={
                    typeFilter === value
                      ? 'rounded-sm bg-accent-600 px-2.5 py-1 text-xs font-medium text-ink-0 whitespace-nowrap'
                      : 'rounded-sm px-2.5 py-1 text-xs text-ink-600 hover:bg-ink-100 hover:text-ink-900 transition-colors whitespace-nowrap'
                  }
                >
                  {value === 'ALL'
                    ? t('book.list.filter_all')
                    : t(BOOK_TYPE_DICT_KEY[value])}
                </button>
              ))}
            </div>
          </div>
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

        {state.kind === 'success' && state.data.books.length === 0 && (
          <Card className="mx-auto max-w-2xl p-12 text-center">
            <BookOpen
              size={32}
              className="mx-auto mb-3 text-ink-400"
              aria-hidden
            />
            <p className="text-base text-ink-700">{t('book.list.title')}</p>
          </Card>
        )}

        {state.kind === 'success' &&
          state.data.books.length > 0 &&
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
                    />
                  </li>
                ))}
            </ul>

            {state.data.hasNext && !localFilterActive && (
              <div className="mt-6 flex justify-center">
                <Button
                  variant="ghost"
                  onClick={handleLoadMore}
                  disabled={loadingMore}
                  icon={loadingMore ? Loader2 : undefined}
                >
                  {loadingMore ? t('common.loading') : t('common.load_more')}
                </Button>
              </div>
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

interface BookCardProps {
  book: Book & { id: string };
  onEdit: (bookId: string) => void;
  editLoading: boolean;
}

function BookCard({ book, onEdit, editLoading }: BookCardProps) {
  const t = useT();
  const bookType = book.bookType ?? 'BOOK';
  const fallbackTitle = t('reader.no_book_title');
  const title = book.title ?? fallbackTitle;
  const initialLetter = title.charAt(0).toUpperCase() || '?';

  return (
    <div className="relative">
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
      <Link
        to={`/books/${book.id}`}
        aria-label={title}
        className="group block focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 focus-visible:ring-offset-2 focus-visible:ring-offset-bg rounded-md"
      >
        <Card interactive className="h-full overflow-hidden">
          <Card.Cover color={coverColorFor(book.id)}>{initialLetter}</Card.Cover>
          <Card.Body>
            <Card.Eyebrow>
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
              <VisibilityBadge
                visibility={book.visibility}
                labelPrefix="book.visibility"
                compact
              />
            </Card.Eyebrow>
            <Card.Title>{title}</Card.Title>
            <Card.Meta>
              <span className="font-mono text-xs">
                <bdi dir="ltr">{book.id.slice(0, 8)}</bdi>
              </span>
            </Card.Meta>
          </Card.Body>
        </Card>
      </Link>
    </div>
  );
}

export default BookListPage;
