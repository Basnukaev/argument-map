import { useEffect, useMemo, useState } from 'react';
import { BookOpen, ChevronLeft, ChevronRight, Loader2, Search, X } from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import BookHeader from '@/shared/components/reader/BookHeader';
import PageView, {
  type PageContentState,
  type PageDetail,
  type TextSelection,
} from '@/shared/components/reader/PageView';
import { apiGetRaw, apiPostRaw, formatApiError, ApiError } from '@/shared/api/client';
import type { components } from '@/shared/api/types';

type Book = components['schemas']['BookSummaryResponse'];
type BookDetailDto = components['schemas']['BookDetailResponse'];
type PageSummaryDto = components['schemas']['PageSummaryResponse'] & {
  printedPage?: string | null;
  part?: string | null;
};

interface Props {
  nodeId: string;
  nodeContent: string;
  onClose: () => void;
  onCreated: () => void;
}

type BooksState =
  | { kind: 'loading' }
  | { kind: 'success'; books: Book[] }
  | { kind: 'error'; message: string };

type BookState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'success'; book: BookDetailDto; pages: PageSummaryDto[] }
  | { kind: 'error'; message: string };

/**
 * Модалка привязки positional citation к узлу argument-map. 3-колонный
 * layout: библиотека слева - встроенный mini-reader в центре - выделение
 * + context справа.
 *
 * <p>MVP - только text mode. ЛКМ-drag в PageView вычисляет char range
 * через TreeWalker по plain text. POST /api/v1/nodes/:id/citations с
 * bookId+pageId+rangeStart+rangeEnd. PDF mode (bbox selection) -
 * следующая итерация (требует backend API change).
 *
 * <p>Conditional render (`{open && <CitationPicker .../>}`) - idiom
 * проекта, обеспечивает чистый state при каждом открытии.
 */
function CitationPicker({ nodeId, nodeContent, onClose, onCreated }: Props) {
  const [booksState, setBooksState] = useState<BooksState>({ kind: 'loading' });
  const [search, setSearch] = useState('');
  const [selectedBookId, setSelectedBookId] = useState<string | null>(null);
  const [bookState, setBookState] = useState<BookState>({ kind: 'idle' });
  const [pageNumber, setPageNumber] = useState(1);
  const [pageContent, setPageContent] = useState<PageContentState>({ kind: 'loading' });
  const [selection, setSelection] = useState<TextSelection | null>(null);
  const [context, setContext] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  // Загрузка списка книг при первом рендере
  useEffect(() => {
    const ctl = new AbortController();
    apiGetRaw<Book[]>('/api/v1/library/books', { signal: ctl.signal })
      .then((books) => setBooksState({ kind: 'success', books: books ?? [] }))
      .catch((e: unknown) => {
        if (ctl.signal.aborted) return;
        setBooksState({ kind: 'error', message: formatApiError(e, 'Не удалось загрузить библиотеку') });
      });
    return () => ctl.abort();
  }, []);

  // Загрузка book detail + pages при выборе книги
  useEffect(() => {
    if (!selectedBookId) {
      setBookState({ kind: 'idle' });
      return;
    }
    setBookState({ kind: 'loading' });
    setSelection(null);
    const ctl = new AbortController();
    Promise.all([
      apiGetRaw<BookDetailDto>(`/api/v1/library/books/${selectedBookId}`, { signal: ctl.signal }),
      apiGetRaw<PageSummaryDto[]>(`/api/v1/library/books/${selectedBookId}/pages`, {
        signal: ctl.signal,
      }),
    ])
      .then(([book, pages]) => {
        const sorted = [...(pages ?? [])].sort(
          (a, b) => (a.pageNumber ?? 0) - (b.pageNumber ?? 0),
        );
        setBookState({ kind: 'success', book, pages: sorted });
        const first = sorted[0]?.pageNumber;
        if (first) setPageNumber(first);
      })
      .catch((e: unknown) => {
        if (ctl.signal.aborted) return;
        setBookState({ kind: 'error', message: formatApiError(e, 'Не удалось загрузить книгу') });
      });
    return () => ctl.abort();
  }, [selectedBookId]);

  // Загрузка контента текущей страницы
  useEffect(() => {
    if (bookState.kind !== 'success') return;
    const target = bookState.pages.find((p) => p.pageNumber === pageNumber);
    if (!target?.id) return;
    setPageContent({ kind: 'loading' });
    setSelection(null);
    const ctl = new AbortController();
    apiGetRaw<PageDetail>(`/api/v1/library/pages/${target.id}`, { signal: ctl.signal })
      .then((page) => setPageContent({ kind: 'success', page }))
      .catch((e: unknown) => {
        if (ctl.signal.aborted) return;
        setPageContent({ kind: 'error', message: formatApiError(e, 'Не удалось загрузить страницу') });
      });
    return () => ctl.abort();
  }, [bookState, pageNumber]);

  // Esc закрывает (если не submitting)
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !submitting) {
        onClose();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [submitting, onClose]);

  const filteredBooks = useMemo(() => {
    if (booksState.kind !== 'success') return [];
    const q = search.trim().toLowerCase();
    if (!q) return booksState.books;
    return booksState.books.filter((b) => (b.title ?? '').toLowerCase().includes(q));
  }, [booksState, search]);

  const currentIndex =
    bookState.kind === 'success'
      ? bookState.pages.findIndex((p) => p.pageNumber === pageNumber)
      : -1;
  const hasPrev = currentIndex > 0;
  const hasNext = bookState.kind === 'success' && currentIndex < bookState.pages.length - 1;

  function goPrev() {
    if (bookState.kind !== 'success' || !hasPrev) return;
    const prev = bookState.pages[currentIndex - 1]?.pageNumber;
    if (prev) setPageNumber(prev);
  }
  function goNext() {
    if (bookState.kind !== 'success' || !hasNext) return;
    const next = bookState.pages[currentIndex + 1]?.pageNumber;
    if (next) setPageNumber(next);
  }
  function gotoPage(target: number) {
    if (bookState.kind !== 'success' || bookState.pages.length === 0) return;
    const numbers = bookState.pages.map((p) => p.pageNumber ?? 0);
    const minN = numbers[0] ?? 1;
    const maxN = numbers[numbers.length - 1] ?? 1;
    let clamped = Math.max(minN, Math.min(maxN, target));
    if (!numbers.includes(clamped)) {
      const sorted = [...numbers].sort((a, b) => Math.abs(a - target) - Math.abs(b - target));
      clamped = sorted[0] ?? clamped;
    }
    if (clamped !== pageNumber) setPageNumber(clamped);
  }

  async function handleSubmit() {
    if (!selection || !selectedBookId) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      await apiPostRaw(`/api/v1/nodes/${nodeId}/citations`, {
        bookId: selectedBookId,
        pageId: selection.pageId,
        rangeStart: selection.rangeStart,
        rangeEnd: selection.rangeEnd,
        quote: selection.quote,
        context: context.trim() || undefined,
      });
      onCreated();
      onClose();
    } catch (e: unknown) {
      if (e instanceof ApiError) {
        setSubmitError(formatApiError(e, 'Не удалось привязать цитату'));
      } else {
        setSubmitError(formatApiError(e, 'Не удалось привязать цитату'));
      }
      setSubmitting(false);
    }
  }

  const truncatedNodeContent =
    nodeContent.length > 80 ? nodeContent.substring(0, 77) + '...' : nodeContent;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Привести источник"
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget && !submitting) onClose();
      }}
    >
      <div className="flex h-full max-h-[92vh] w-full max-w-[1480px] flex-col rounded-lg border border-slate-200 bg-white shadow-2xl">
        {/* Header */}
        <header className="flex items-start justify-between gap-3 border-b border-slate-200 px-6 py-3.5">
          <div className="min-w-0">
            <h2 className="flex items-center gap-2 text-[15px] font-semibold text-slate-900">
              <BookOpen size={18} className="text-indigo-600" aria-hidden="true" />
              Привести источник для:
              <span className="truncate text-slate-600 font-normal">«{truncatedNodeContent}»</span>
            </h2>
            <p className="mt-0.5 text-[11px] text-slate-500">
              Выберите книгу, найдите фрагмент, выделите курсором и нажмите «Привести»
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="rounded p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-40"
            aria-label="Закрыть"
          >
            <X size={18} aria-hidden="true" />
          </button>
        </header>

        {/* Body 3-column */}
        <div className="flex flex-1 min-h-0 gap-3 p-3">
          {/* Left: BookListSidebar */}
          <aside className="flex w-[280px] flex-col gap-2">
            <div className="flex h-9 items-center rounded-md border border-slate-300 bg-white transition-colors focus-within:border-indigo-500 focus-within:ring-2 focus-within:ring-indigo-500/20">
              <Search size={14} className="ml-3 text-slate-400" aria-hidden="true" />
              <input
                type="search"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Поиск книги"
                className="flex-1 bg-transparent px-2.5 text-[13px] outline-none placeholder:text-slate-400"
              />
            </div>
            <div className="flex-1 overflow-y-auto rounded-md border border-slate-200 bg-slate-50/40">
              {booksState.kind === 'loading' && (
                <div className="p-4 text-center">
                  <Loader2 size={16} className="mx-auto animate-spin text-slate-400" />
                </div>
              )}
              {booksState.kind === 'error' && (
                <p className="p-3 text-[12px] text-red-700">{booksState.message}</p>
              )}
              {booksState.kind === 'success' && filteredBooks.length === 0 && (
                <p className="p-3 text-center text-[12px] italic text-slate-400">Ничего не найдено</p>
              )}
              {booksState.kind === 'success' &&
                filteredBooks.map((b) => (
                  <button
                    key={b.id}
                    type="button"
                    onClick={() => b.id && setSelectedBookId(b.id)}
                    className={
                      selectedBookId === b.id
                        ? 'block w-full border-b border-slate-200 bg-indigo-50 px-3 py-2 text-left text-[12px] font-medium text-indigo-900'
                        : 'block w-full border-b border-slate-100 px-3 py-2 text-left text-[12px] text-slate-700 hover:bg-slate-100'
                    }
                  >
                    {b.title}
                  </button>
                ))}
            </div>
          </aside>

          {/* Center: EmbeddedReader */}
          <section className="flex flex-1 min-w-0 flex-col gap-2 overflow-hidden">
            {bookState.kind === 'idle' && (
              <Card className="flex flex-1 items-center justify-center text-center">
                <p className="text-[13px] italic text-slate-400">Выберите книгу в списке слева</p>
              </Card>
            )}
            {bookState.kind === 'loading' && (
              <Card className="flex flex-1 items-center justify-center">
                <Loader2 size={20} className="animate-spin text-slate-400" />
              </Card>
            )}
            {bookState.kind === 'error' && (
              <Card className="flex flex-1 items-center justify-center border-red-200 bg-red-50 p-4">
                <p className="text-[13px] text-red-800">{bookState.message}</p>
              </Card>
            )}
            {bookState.kind === 'success' && (
              <>
                <BookHeader book={bookState.book} pagesCount={bookState.pages.length} />
                <div className="flex items-center justify-between gap-3 rounded-md border border-slate-200 bg-white px-3 py-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    icon={ChevronLeft}
                    onClick={goPrev}
                    disabled={!hasPrev}
                  >
                    Назад
                  </Button>
                  <div className="flex items-center gap-2 text-[13px] text-slate-700">
                    <span className="text-slate-500">Стр</span>
                    <input
                      type="number"
                      min={1}
                      value={pageNumber}
                      onChange={(e) => {
                        const v = parseInt(e.target.value, 10);
                        if (Number.isFinite(v)) gotoPage(v);
                      }}
                      className="h-7 w-16 rounded border border-slate-300 px-2 text-center font-mono outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
                      aria-label="Номер страницы"
                    />
                    <span className="font-mono text-slate-400">/ {bookState.pages.length}</span>
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    iconRight={ChevronRight}
                    onClick={goNext}
                    disabled={!hasNext}
                  >
                    Вперёд
                  </Button>
                </div>
                <div className="flex-1 min-h-0 overflow-y-auto">
                  <PageView
                    state={pageContent}
                    bookLanguage={bookState.book.language ?? undefined}
                    selectable
                    onSelectionChange={setSelection}
                  />
                </div>
              </>
            )}
          </section>

          {/* Right: SelectionPanel */}
          <aside className="flex w-[320px] flex-col gap-2">
            <Card className="p-3">
              <h3 className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                Выделенный фрагмент
              </h3>
              {selection ? (
                <blockquote
                  className="mt-2 max-h-[200px] overflow-y-auto border-l-2 border-indigo-300 pl-2 text-[13px] italic text-slate-700"
                  dir={isArabicLikely(selection.quote) ? 'rtl' : 'ltr'}
                >
                  «{selection.quote}»
                </blockquote>
              ) : (
                <p className="mt-2 text-[12px] italic text-slate-400">
                  Выделите фрагмент текста на странице курсором
                </p>
              )}
              {selection && (
                <p className="mt-2 font-mono text-[11px] text-slate-500">
                  Символы {selection.rangeStart}-{selection.rangeEnd}
                </p>
              )}
            </Card>

            <Card className="flex flex-1 flex-col p-3">
              <label
                htmlFor="citation-context"
                className="text-[11px] font-semibold uppercase tracking-wide text-slate-500"
              >
                Комментарий (опционально)
              </label>
              <textarea
                id="citation-context"
                value={context}
                onChange={(e) => setContext(e.target.value)}
                placeholder="Как эта цитата подкрепляет тезис узла"
                rows={6}
                className="mt-2 flex-1 resize-none rounded-md border border-slate-300 px-2.5 py-2 text-[13px] outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
              />
            </Card>

            {submitError && (
              <div className="rounded-md border border-red-300 bg-red-50 p-2.5 text-[12px] text-red-800">
                {submitError}
              </div>
            )}

            <Button
              type="button"
              icon={BookOpen}
              onClick={handleSubmit}
              disabled={!selection || submitting}
              className="w-full justify-center"
            >
              {submitting ? 'Привязываем' : 'Привести'}
            </Button>
          </aside>
        </div>
      </div>
    </div>
  );
}

function isArabicLikely(s: string): boolean {
  return /[؀-ۿ]/.test(s);
}

export default CitationPicker;
