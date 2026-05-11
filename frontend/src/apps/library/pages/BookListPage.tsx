import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import {
  BookOpen,
  Search,
  AlertCircle,
  Loader2,
  Library,
} from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import type { components } from '@/shared/api/types';

type Book = components['schemas']['BookSummary'];
type BookType = NonNullable<Book['bookType']>;

type ViewState =
  | { kind: 'loading' }
  | { kind: 'success'; books: Book[] }
  | { kind: 'error'; message: string };

const BOOK_TYPE_LABEL: Record<BookType, string> = {
  QURAN: 'Коран',
  HADITH_COLLECTION: 'Сборник хадисов',
  BOOK: 'Книга',
  ARTICLE: 'Статья',
  MANUSCRIPT: 'Рукопись',
};

const BOOK_TYPE_BADGE: Record<BookType, string> = {
  QURAN: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  HADITH_COLLECTION: 'bg-amber-50 text-amber-700 border-amber-200',
  BOOK: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  ARTICLE: 'bg-slate-50 text-slate-700 border-slate-200',
  MANUSCRIPT: 'bg-purple-50 text-purple-700 border-purple-200',
};

const BOOK_TYPE_FILTER: ReadonlyArray<{ value: BookType | 'ALL'; label: string }> = [
  { value: 'ALL', label: 'Все типы' },
  { value: 'BOOK', label: 'Книги' },
  { value: 'HADITH_COLLECTION', label: 'Хадисы' },
  { value: 'QURAN', label: 'Коран' },
  { value: 'ARTICLE', label: 'Статьи' },
  { value: 'MANUSCRIPT', label: 'Рукописи' },
];

function BookListPage() {
  const [state, setState] = useState<ViewState>({ kind: 'loading' });
  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState<BookType | 'ALL'>('ALL');

  useEffect(() => {
    const controller = new AbortController();
    apiGetRaw<Book[]>('/api/v1/library/books', { signal: controller.signal })
      .then((books) => {
        setState({ kind: 'success', books: books ?? [] });
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
    return state.books.filter((b) => {
      if (typeFilter !== 'ALL' && b.bookType !== typeFilter) return false;
      if (!q) return true;
      return (b.title ?? '').toLowerCase().includes(q);
    });
  }, [state, search, typeFilter]);

  return (
    <main className="min-h-screen bg-slate-50/60">
      <Header />

      <div className="mx-auto max-w-[1380px] px-6 py-8">
        <div className="mb-6">
          <h1 className="flex items-center gap-2.5 text-[28px] font-bold tracking-tight text-slate-900">
            <Library size={26} className="text-indigo-600" aria-hidden="true" />
            Библиотека
          </h1>
          {state.kind === 'success' && (
            <p className="mt-1 text-[13px] text-slate-500">
              Импортированные классические труды и источники ·{' '}
              <span className="font-mono font-semibold text-slate-700">
                {state.books.length} книг{state.books.length === 1 ? 'а' : ''}
              </span>
            </p>
          )}
        </div>

        <div className="mb-6 flex flex-wrap items-center gap-3">
          <div className="flex h-9 max-w-md flex-1 items-center rounded-md border border-slate-300 bg-white transition-colors focus-within:border-indigo-500 focus-within:ring-2 focus-within:ring-indigo-500/20">
            <Search size={16} className="ml-3 text-slate-400" aria-hidden="true" />
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Поиск по названию"
              className="flex-1 bg-transparent px-3 text-[13px] text-slate-900 outline-none placeholder:text-slate-400"
              aria-label="Поиск книг"
            />
          </div>
          <div className="flex items-center gap-1 rounded-md border border-slate-200 bg-white p-1">
            {BOOK_TYPE_FILTER.map((opt) => (
              <button
                key={opt.value}
                type="button"
                onClick={() => setTypeFilter(opt.value)}
                className={
                  typeFilter === opt.value
                    ? 'rounded-md bg-indigo-600 px-2.5 py-1 text-[12px] font-medium text-white'
                    : 'rounded-md px-2.5 py-1 text-[12px] text-slate-600 hover:bg-slate-100 hover:text-slate-900 transition-colors'
                }
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>

        {state.kind === 'loading' && (
          <div className="flex items-center justify-center gap-2 py-20 text-[13px] text-slate-500">
            <Loader2 size={16} className="animate-spin" aria-hidden="true" />
            Загрузка
          </div>
        )}

        {state.kind === 'error' && (
          <Card className="mx-auto max-w-2xl border-red-200 bg-red-50 p-5">
            <div className="flex items-start gap-3">
              <AlertCircle size={20} className="mt-0.5 shrink-0 text-red-600" aria-hidden="true" />
              <div>
                <p className="font-semibold text-red-900">Ошибка</p>
                <p className="mt-1 text-[13px] text-red-800">{state.message}</p>
              </div>
            </div>
          </Card>
        )}

        {state.kind === 'success' && state.books.length === 0 && (
          <Card className="mx-auto max-w-2xl p-12 text-center">
            <BookOpen size={32} className="mx-auto mb-3 text-slate-400" aria-hidden="true" />
            <p className="text-[15px] text-slate-700">Библиотека пуста</p>
            <p className="mt-2 text-[13px] text-slate-500">
              Импортируй книги через admin endpoint{' '}
              <code className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[12px]">
                POST /api/v1/admin/shamela/map-book/&#123;id&#125;
              </code>
            </p>
          </Card>
        )}

        {state.kind === 'success' && state.books.length > 0 && filteredBooks.length === 0 && (
          <p className="text-center text-[13px] text-slate-500">
            Ничего не найдено{search && ` по запросу "${search}"`}
            {typeFilter !== 'ALL' && ` среди ${BOOK_TYPE_LABEL[typeFilter].toLowerCase()}`}
          </p>
        )}

        {state.kind === 'success' && filteredBooks.length > 0 && (
          <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
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
  const bookType = book.bookType ?? 'BOOK';
  const isArabic = book.language === 'ar';

  return (
    <Link
      to={`/books/${book.id}`}
      aria-label={book.title ?? '(без названия)'}
      className="group block focus:outline-none"
    >
      <Card className="h-full overflow-hidden transition-all hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-md group-focus-visible:ring-2 group-focus-visible:ring-indigo-500 group-focus-visible:ring-offset-2">
        <div className="flex h-[100px] items-center justify-center bg-gradient-to-br from-indigo-50 via-white to-slate-50">
          <BookOpen size={36} className="text-indigo-600/70" aria-hidden="true" />
        </div>
        <div className="p-4">
          <div className="mb-2 flex items-center gap-1.5">
            <span
              className={`inline-flex items-center rounded border px-1.5 py-0.5 text-[10px] font-medium ${BOOK_TYPE_BADGE[bookType]}`}
            >
              {BOOK_TYPE_LABEL[bookType]}
            </span>
            {book.language && (
              <span className="inline-flex items-center rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-mono uppercase text-slate-600">
                {book.language}
              </span>
            )}
          </div>
          <h2
            className={
              isArabic
                ? 'line-clamp-2 font-naskh text-[16px] font-semibold leading-snug text-slate-900 transition-colors group-hover:text-indigo-700'
                : 'line-clamp-2 text-[14px] font-semibold leading-snug text-slate-900 transition-colors group-hover:text-indigo-700'
            }
            dir={isArabic ? 'rtl' : 'ltr'}
          >
            {book.title ?? '(без названия)'}
          </h2>
          <div className="mt-3 text-[11px] font-mono text-slate-500">
            {book.id.slice(0, 8)}
          </div>
        </div>
      </Card>
    </Link>
  );
}

export default BookListPage;
