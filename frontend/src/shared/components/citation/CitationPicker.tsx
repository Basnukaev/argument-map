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
import { hasArabicScript, useT } from '@/shared/i18n';
import { useHotkey } from '@/shared/hooks/useHotkey';
import { useIsMobile } from '@/shared/hooks/useViewport';

type Book = components['schemas']['BookSummaryResponse'];
type BookDetailDto = components['schemas']['BookDetailResponse'];
type PagedBooks = components['schemas']['PagedResponseBookSummaryResponse'];
type PageSummaryDto = components['schemas']['PageSummaryResponse'] & {
  printedPage?: string | null;
  part?: string | null;
};

// Picker - модалка ограниченной высоты, Load More UX некомфортный.
// Стабильно грузим бóльшую первую страницу (size=100 max). Когда в
// библиотеке окажется >100 книг - добавить search-by-title или
// pagination control внутри picker'а (backlog)
const PICKER_PAGE_SIZE = 100;

interface Props {
  /** Тип сущности к которой привязывается citation - влияет на URL */
  targetType: 'nodes' | 'questions' | 'answers';
  /** id сущности (node, question или answer) */
  targetId: string;
  /** Короткий label для header «Привязать к: ...» (node.content / question.title / answer.body preview) */
  targetLabel: string;
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
type MobileTab = 'books' | 'reader' | 'selection';

function CitationPicker({ targetType, targetId, targetLabel, onClose, onCreated }: Props) {
  const t = useT();
  const isMobile = useIsMobile();
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
  // На mobile - 3-tab switcher между books / reader / selection.
  // Auto-переключение: при выборе книги → reader, при появлении
  // selection в reader → selection (см. эффект ниже).
  // Не используется на desktop (3 колонки видны одновременно)
  const [mobileTab, setMobileTab] = useState<MobileTab>('books');

  // Загрузка списка книг при первом рендере. Backend pagination
  // breaking change (см. api-contract): GET /library/books →
  // PagedResponse<BookSummaryResponse>. Без Load More - см. константу
  useEffect(() => {
    const ctl = new AbortController();
    apiGetRaw<PagedBooks>(
      `/api/v1/library/books?page=0&size=${PICKER_PAGE_SIZE}`,
      { signal: ctl.signal },
    )
      .then((paged) =>
        setBooksState({ kind: 'success', books: paged.items ?? [] }),
      )
      .catch((e: unknown) => {
        if (ctl.signal.aborted) return;
        setBooksState({ kind: 'error', message: formatApiError(e, t('citation_picker.books_load_failed')) });
      });
    return () => ctl.abort();
  }, [t]);

  // Загрузка book detail + pages при выборе книги. Loading state выставляется
  // в handleSelectBook (event handler) не в effect - правило
  // react-hooks/set-state-in-effect. Effect только async fetch + result.
  useEffect(() => {
    if (!selectedBookId) return;
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
        setBookState({ kind: 'error', message: formatApiError(e, t('reader.book_load_failed')) });
      });
    return () => ctl.abort();
  }, [selectedBookId, t]);

  // Загрузка контента текущей страницы. Loading state выставляется в gotoPage/
  // goPrev/goNext (handlers) не в effect.
  useEffect(() => {
    if (bookState.kind !== 'success') return;
    const target = bookState.pages.find((p) => p.pageNumber === pageNumber);
    if (!target?.id) return;
    const ctl = new AbortController();
    apiGetRaw<PageDetail>(`/api/v1/library/pages/${target.id}`, { signal: ctl.signal })
      .then((page) => setPageContent({ kind: 'success', page }))
      .catch((e: unknown) => {
        if (ctl.signal.aborted) return;
        setPageContent({ kind: 'error', message: formatApiError(e, t('citation_picker.page_load_failed')) });
      });
    return () => ctl.abort();
  }, [bookState, pageNumber, t]);

  // Esc закрывает (если не submitting). enableOnFormTags=true потому что
  // в picker'е есть search input и textarea для комментария.
  useHotkey(
    'escape',
    () => {
      if (!submitting) onClose();
    },
    { enableOnFormTags: true },
    [submitting, onClose],
  );

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

  function handleSelectBook(bookId: string) {
    setBookState({ kind: 'loading' });
    setSelection(null);
    setPageContent({ kind: 'loading' });
    setSelectedBookId(bookId);
    // На mobile - после выбора книги сразу перевести юзера в reader tab,
    // иначе он останется на books и должен будет вручную переключиться
    if (isMobile) setMobileTab('reader');
  }
  function goPrev() {
    if (bookState.kind !== 'success' || !hasPrev) return;
    const prev = bookState.pages[currentIndex - 1]?.pageNumber;
    if (prev) {
      setPageContent({ kind: 'loading' });
      setSelection(null);
      setPageNumber(prev);
    }
  }
  function goNext() {
    if (bookState.kind !== 'success' || !hasNext) return;
    const next = bookState.pages[currentIndex + 1]?.pageNumber;
    if (next) {
      setPageContent({ kind: 'loading' });
      setSelection(null);
      setPageNumber(next);
    }
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
    if (clamped !== pageNumber) {
      setPageContent({ kind: 'loading' });
      setSelection(null);
      setPageNumber(clamped);
    }
  }

  async function handleSubmit() {
    if (!selection || !selectedBookId) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      await apiPostRaw(`/api/v1/${targetType}/${targetId}/citations`, {
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
        setSubmitError(formatApiError(e, t('citation_picker.create_failed')));
      } else {
        setSubmitError(formatApiError(e, t('citation_picker.create_failed')));
      }
      setSubmitting(false);
    }
  }

  const truncatedTargetLabel =
    targetLabel.length > 80 ? targetLabel.substring(0, 77) + '...' : targetLabel;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={t('citation_picker.title_for')}
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink-900/60 backdrop-blur-sm p-0 sm:p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget && !submitting) onClose();
      }}
    >
      <div className="flex h-dvh w-full max-w-[1480px] flex-col rounded-none border-0 bg-elevated shadow-2xl sm:h-full sm:max-h-[92vh] sm:rounded-lg sm:border sm:border-border">
        {/* Header */}
        <header className="flex items-start justify-between gap-3 border-b border-border px-4 py-3 sm:px-6 sm:py-3.5">
          <div className="min-w-0">
            <h2 className="flex items-center gap-2 text-base font-semibold text-ink-900">
              <BookOpen size={18} className="text-accent-600 shrink-0" aria-hidden="true" />
              <span className="truncate">
                {t('citation_picker.title_for')}:{' '}
                <span dir="auto" className="text-ink-600 font-normal">«{truncatedTargetLabel}»</span>
              </span>
            </h2>
            <p className="mt-0.5 text-xs text-ink-500 hidden sm:block">
              {t('citation_picker.subtitle')}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="rounded p-1.5 text-ink-400 hover:bg-ink-100 hover:text-ink-700 disabled:opacity-40 shrink-0"
            aria-label={t('common.close')}
          >
            <X size={18} aria-hidden="true" />
          </button>
        </header>

        {/* Mobile tabs - 3 переключателя поверх body. На desktop hidden,
            используется 3-колоночный layout без табов */}
        <div
          role="tablist"
          aria-label={t('citation_picker.title_for')}
          className="flex border-b border-border bg-ink-50/60 px-2 sm:hidden"
        >
          <MobileTabButton
            label={t('citation_picker.tab_books')}
            active={mobileTab === 'books'}
            onClick={() => setMobileTab('books')}
          />
          <MobileTabButton
            label={t('citation_picker.tab_reader')}
            active={mobileTab === 'reader'}
            disabled={!selectedBookId}
            onClick={() => setMobileTab('reader')}
          />
          <MobileTabButton
            label={t('citation_picker.tab_selection')}
            active={mobileTab === 'selection'}
            badge={selection ? '•' : undefined}
            onClick={() => setMobileTab('selection')}
          />
        </div>

        {/* Body 3-column on desktop. На mobile - один активный таб на полную
            ширину (через hidden/flex switching). Используем CSS classes
            вместо conditional render чтобы не сбрасывать internal state
            форм при переключении табов */}
        <div className="flex flex-1 min-h-0 gap-3 p-2 sm:p-3">
          {/* Left: BookListSidebar */}
          <aside
            className={`${
              isMobile && mobileTab !== 'books' ? 'hidden' : 'flex'
            } w-full flex-col gap-2 sm:flex sm:w-[280px]`}
          >
            <div className="flex h-9 items-center rounded-md border border-border-strong bg-elevated transition-colors focus-within:border-accent-500 focus-within:ring-2 focus-within:ring-accent-500/20">
              <Search size={14} className="ms-3 text-ink-400" aria-hidden="true" />
              <input
                type="search"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder={t('citation_picker.search_book')}
                className="flex-1 bg-transparent px-2.5 text-sm outline-none placeholder:text-ink-400"
              />
            </div>
            <div className="flex-1 overflow-y-auto rounded-md border border-border bg-ink-50/40">
              {booksState.kind === 'loading' && (
                <div className="p-4 text-center">
                  <Loader2 size={16} className="mx-auto animate-spin text-ink-400" />
                </div>
              )}
              {booksState.kind === 'error' && (
                <p className="p-3 text-xs text-err-700">{booksState.message}</p>
              )}
              {booksState.kind === 'success' && filteredBooks.length === 0 && (
                <p className="p-3 text-center text-xs italic text-ink-400">{t('citation_picker.nothing_found')}</p>
              )}
              {booksState.kind === 'success' &&
                filteredBooks.map((b) => (
                  <button
                    key={b.id}
                    type="button"
                    onClick={() => b.id && handleSelectBook(b.id)}
                    className={
                      selectedBookId === b.id
                        ? 'block w-full border-b border-border bg-accent-50 px-3 py-2 text-start text-xs font-medium text-accent-700'
                        : 'block w-full border-b border-border px-3 py-2 text-start text-xs text-ink-700 hover:bg-ink-100'
                    }
                  >
                    {b.title}
                  </button>
                ))}
            </div>
          </aside>

          {/* Center: EmbeddedReader */}
          <section
            className={`${
              isMobile && mobileTab !== 'reader' ? 'hidden' : 'flex'
            } flex-1 min-w-0 flex-col gap-2 overflow-hidden sm:flex`}
          >
            {bookState.kind === 'idle' && (
              <Card className="flex flex-1 items-center justify-center text-center">
                <p className="text-sm italic text-ink-400">{t('citation_picker.select_book_hint')}</p>
              </Card>
            )}
            {bookState.kind === 'loading' && (
              <Card className="flex flex-1 items-center justify-center">
                <Loader2 size={20} className="animate-spin text-ink-400" />
              </Card>
            )}
            {bookState.kind === 'error' && (
              <Card className="flex flex-1 items-center justify-center border-err-500/40 bg-err-100 p-4">
                <p className="text-sm text-err-700">{bookState.message}</p>
              </Card>
            )}
            {bookState.kind === 'success' && (
              <>
                <BookHeader book={bookState.book} pagesCount={bookState.pages.length} />
                <div className="flex items-center justify-between gap-3 rounded-md border border-border bg-elevated px-3 py-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    icon={ChevronLeft}
                    onClick={goPrev}
                    disabled={!hasPrev}
                  >
                    {t('reader.prev')}
                  </Button>
                  <div className="flex items-center gap-2 text-sm text-ink-700">
                    <span className="text-ink-500">{t('reader.page_short')}</span>
                    <input
                      type="number"
                      min={1}
                      value={pageNumber}
                      onChange={(e) => {
                        const v = parseInt(e.target.value, 10);
                        if (Number.isFinite(v)) gotoPage(v);
                      }}
                      className="h-7 w-16 rounded border border-border-strong px-2 text-center font-mono outline-none focus:border-accent-500 focus:ring-2 focus:ring-accent-500/20"
                      aria-label={t('reader.page')}
                    />
                    <span className="font-mono text-ink-400">
                      <bdi dir="ltr">/ {bookState.pages.length}</bdi>
                    </span>
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    iconRight={ChevronRight}
                    onClick={goNext}
                    disabled={!hasNext}
                  >
                    {t('reader.next')}
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
          <aside
            className={`${
              isMobile && mobileTab !== 'selection' ? 'hidden' : 'flex'
            } w-full flex-col gap-2 sm:flex sm:w-[320px]`}
          >
            <Card className="p-3">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-ink-500">
                {t('citation_picker.selected_fragment')}
              </h3>
              {selection ? (
                <blockquote
                  className="mt-2 max-h-[200px] overflow-y-auto border-s-2 border-accent-100 ps-2 text-sm italic text-ink-700"
                  dir={hasArabicScript(selection.quote) ? 'rtl' : 'ltr'}
                >
                  «{selection.quote}»
                </blockquote>
              ) : (
                <p className="mt-2 text-xs italic text-ink-400">
                  {t('citation_picker.select_hint')}
                </p>
              )}
              {selection && (
                <p className="mt-2 font-mono text-xs text-ink-500">
                  {t('citation_picker.chars_label')} {selection.rangeStart}-{selection.rangeEnd}
                </p>
              )}
            </Card>

            <Card className="flex flex-1 flex-col p-3">
              <label
                htmlFor="citation-context"
                className="text-xs font-semibold uppercase tracking-wide text-ink-500"
              >
                {t('citation_picker.comment_optional')}
              </label>
              <textarea
                id="citation-context"
                value={context}
                onChange={(e) => setContext(e.target.value)}
                placeholder={t('citation_picker.comment_placeholder')}
                rows={6}
                className="mt-2 flex-1 resize-none rounded-md border border-border-strong px-2.5 py-2 text-sm outline-none transition-colors focus:border-accent-500 focus:ring-2 focus:ring-accent-500/20"
              />
            </Card>

            {submitError && (
              <div className="rounded-md border border-err-500/40 bg-err-100 p-2.5 text-xs text-err-700">
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
              {submitting ? t('common.saving') : t('citation_picker.submit')}
            </Button>
          </aside>
        </div>
      </div>
    </div>
  );
}

interface MobileTabButtonProps {
  label: string;
  active: boolean;
  onClick: () => void;
  disabled?: boolean;
  /** Bullet/dot для индикатора что в табе есть unread / selection */
  badge?: string;
}

/**
 * Кнопка переключателя tab на mobile-режиме CitationPicker.
 * Активный получает accent-border-bottom и accent-цвет текста.
 * Disabled (reader без selectedBookId) - greyed-out, не кликается.
 */
function MobileTabButton({ label, active, onClick, disabled, badge }: MobileTabButtonProps) {
  const baseClass =
    'flex-1 inline-flex items-center justify-center gap-1.5 py-2.5 text-xs font-medium border-b-2 transition-colors';
  const activeClass = 'border-accent-500 text-accent-700';
  const idleClass = disabled
    ? 'border-transparent text-ink-300 cursor-not-allowed'
    : 'border-transparent text-ink-600 hover:text-ink-900';
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      disabled={disabled}
      onClick={onClick}
      className={`${baseClass} ${active ? activeClass : idleClass}`}
    >
      <span>{label}</span>
      {badge && <span className="text-accent-500" aria-hidden>{badge}</span>}
    </button>
  );
}

export default CitationPicker;
