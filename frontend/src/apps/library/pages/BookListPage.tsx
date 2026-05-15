import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { BookOpen, Search, AlertCircle, Loader2, Download, ChevronDown } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import Button from '@/shared/components/ui/Button';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import { useT, type DictKey } from '@/shared/i18n';
import type { AsyncState } from '@/shared/types/async';
import type { components } from '@/shared/api/types';

type Book = components['schemas']['BookSummaryResponse'];
type BookType = NonNullable<Book['bookType']>;

/** Ключи в словаре через book.type.* - подцепляем через useT() */
const BOOK_TYPE_DICT_KEY: Record<BookType, DictKey> = {
  QURAN: 'book.type.QURAN',
  HADITH_COLLECTION: 'book.type.HADITH_COLLECTION',
  BOOK: 'book.type.BOOK',
  ARTICLE: 'book.type.ARTICLE',
  MANUSCRIPT: 'book.type.MANUSCRIPT',
};

/**
 * Toned chip-классы по типу книги. В v2 design нет per-tone individuality
 * как в v1 (emerald/amber/indigo и т.д.) - все типы используют единый
 * neutral chip (ink-100/ink-700), кроме Quran/Hadith которые получают
 * accent-фон чтобы выделить религиозный канон от обычных книг.
 */
const BOOK_TYPE_BADGE: Record<BookType, string> = {
  QURAN: 'bg-ok-100 text-ok-700',
  HADITH_COLLECTION: 'bg-warn-100 text-warn-700',
  BOOK: 'bg-ink-100 text-ink-700',
  ARTICLE: 'bg-ink-100 text-ink-700',
  MANUSCRIPT: 'bg-type-abstract-bg text-type-abstract-fg',
};

/**
 * Колор-генератор для Card.Cover - стабильный цвет на основе bookId,
 * выглядит как индивидуальная обложка. В дизайне (см. handoff/04-pages.md)
 * cover должен быть solid color (не gradient).
 */
function coverColorFor(id: string): string {
  // 5 цветов выбранных так чтобы цвет читался в обоих темах
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

const BOOK_TYPE_FILTER_VALUES: ReadonlyArray<BookType | 'ALL'> = [
  'ALL',
  'BOOK',
  'HADITH_COLLECTION',
  'QURAN',
  'ARTICLE',
  'MANUSCRIPT',
];

type SortKey = 'added' | 'title' | 'type';

function BookListPage() {
  const t = useT();
  const [state, setState] = useState<AsyncState<Book[]>>({ kind: 'loading' });
  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState<BookType | 'ALL'>('ALL');
  const [sortBy, setSortBy] = useState<SortKey>('added');

  useEffect(() => {
    const controller = new AbortController();
    apiGetRaw<Book[]>('/api/v1/library/books', { signal: controller.signal })
      .then((books) => {
        setState({ kind: 'success', data: books ?? [] });
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
  }, []);

  const filteredBooks = useMemo(() => {
    if (state.kind !== 'success') return [];
    const q = search.trim().toLowerCase();
    const list = state.data.filter((b) => {
      if (typeFilter !== 'ALL' && b.bookType !== typeFilter) return false;
      if (!q) return true;
      return (b.title ?? '').toLowerCase().includes(q);
    });
    // Сортировка - локальная (бэк сортировку пока не поддерживает).
    // `added` = stable order бэка (по умолчанию), `title` = alphabetic,
    // `type` = группировка по bookType
    if (sortBy === 'title') {
      return [...list].sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''));
    }
    if (sortBy === 'type') {
      return [...list].sort((a, b) =>
        (a.bookType ?? '').localeCompare(b.bookType ?? ''),
      );
    }
    return list;
  }, [state, search, typeFilter, sortBy]);

  return (
    <main className="min-h-screen bg-bg">
      <Header />

      <div className="mx-auto max-w-[1380px] px-6 py-6">
        <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="text-xl font-bold tracking-tight text-ink-900">
              {t('book.list.title')}
            </h1>
            {state.kind === 'success' && (
              <p className="mt-1 text-sm text-ink-500">
                {t('book.list.subtitle')} ·{' '}
                <span className="font-mono font-semibold text-ink-700">
                  <bdi dir="ltr">{state.data.length}</bdi>{' '}
                  {t('book.list.books_suffix')}
                </span>
              </p>
            )}
          </div>
          {/* "Импорт из Shamela" - переход на admin страницу. Per референс
              библиотеки эта кнопка живёт в правом верхнем углу заголовка,
              а не где-то в админке - чтобы пользователь, который смотрит
              библиотеку, мог быстро запустить пополнение */}
          <Link to="/admin/shamela">
            <Button variant="secondary" icon={Download}>
              {t('book.list.import_from_shamela')}
            </Button>
          </Link>
        </div>

        <div className="mb-6 flex flex-wrap items-center gap-3">
          <div className="flex h-9 max-w-md flex-1 items-center rounded-sm border border-ink-200 bg-elevated transition-colors focus-within:border-accent-500">
            <Search size={14} className="ms-3 text-ink-400" aria-hidden />
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={t('book.list.search_placeholder')}
              className="flex-1 bg-transparent px-3 text-sm text-ink-900 outline-none placeholder:text-ink-400"
              aria-label={t('common.search')}
            />
          </div>
          <div className="flex items-center gap-1 rounded-sm border border-ink-200 bg-elevated p-1">
            {BOOK_TYPE_FILTER_VALUES.map((value) => (
              <button
                key={value}
                type="button"
                onClick={() => setTypeFilter(value)}
                className={
                  typeFilter === value
                    ? 'rounded-sm bg-accent-600 px-2.5 py-1 text-xs font-medium text-ink-0'
                    : 'rounded-sm px-2.5 py-1 text-xs text-ink-600 hover:bg-ink-100 hover:text-ink-900 transition-colors'
                }
              >
                {value === 'ALL'
                  ? t('book.list.filter_all')
                  : t(BOOK_TYPE_DICT_KEY[value])}
              </button>
            ))}
          </div>
          {/* Сортировка - native <select> stylized под design-system. Не
              делаем custom dropdown ради UX-простоты + native a11y */}
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

        {state.kind === 'success' && state.data.length === 0 && (
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
          state.data.length > 0 &&
          filteredBooks.length === 0 && (
            <p className="text-center text-sm text-ink-500">
              {t('topic.list.not_found')}
            </p>
          )}

        {state.kind === 'success' && filteredBooks.length > 0 && (
          <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5">
            {filteredBooks
              .filter((b): b is Book & { id: string } => Boolean(b.id))
              .map((book) => (
                <li key={book.id}>
                  <BookCard book={book} />
                </li>
              ))}
          </ul>
        )}
      </div>
    </main>
  );
}

interface BookCardProps {
  book: Book & { id: string };
}

function BookCard({ book }: BookCardProps) {
  const t = useT();
  const bookType = book.bookType ?? 'BOOK';
  const isArabic = book.language === 'ar';
  const fallbackTitle = t('reader.no_book_title');
  const title = book.title ?? fallbackTitle;
  const initialLetter = title.charAt(0).toUpperCase() || '?';

  return (
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
          </Card.Eyebrow>
          <Card.Title arabic={isArabic}>{title}</Card.Title>
          <Card.Meta>
            <span className="font-mono text-xs">
              <bdi dir="ltr">{book.id.slice(0, 8)}</bdi>
            </span>
          </Card.Meta>
        </Card.Body>
      </Card>
    </Link>
  );
}

export default BookListPage;
