import { lazy, Suspense, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { AlertCircle, ChevronLeft, ChevronRight, Loader2, ArrowLeft } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Button from '@/shared/components/ui/Button';
import Header from '@/shared/components/layout/Header';
import BookHeader from '@/apps/library/components/BookHeader';
import ReaderModeSwitch from '@/apps/library/components/ReaderModeSwitch';
import ChapterList, { type Chapter } from '@/apps/library/components/ChapterList';
import PageJump from '@/apps/library/components/PageJump';
import PageView, { type PageContentState, type PageDetail } from '@/apps/library/components/PageView';
import { type ReaderMode } from '@/apps/library/utils/bookReaderUtils';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import type { components } from '@/shared/api/types';

// Lazy-load PdfViewer - тяжёлая зависимость (react-pdf + pdfjs-dist
// весит ~600KB gzipped). Подгружается только при переключении в PDF mode
const PdfViewer = lazy(() => import('@/apps/library/components/PdfViewer'));

type BookDetail = components['schemas']['BookDetailResponse'] & {
  chapters?: Chapter[];
};
type PageSummary = components['schemas']['PageSummaryResponse'] & {
  printedPage?: string | null;
  part?: string | null;
};

type BookState =
  | { kind: 'loading' }
  | { kind: 'success'; book: BookDetail; pages: PageSummary[] }
  | { kind: 'error'; message: string };

/**
 * Reader страница для книг из библиотеки. Грузит book metadata +
 * страницы, управляет навигацией (prev/next/jump/chapter-click),
 * переключается между text и PDF режимами. Делегирует рендеринг
 * подкомпонентам в `apps/library/components/`.
 */
function BookReaderPage() {
  const { bookId } = useParams<{ bookId: string }>();
  const navigate = useNavigate();
  const [state, setState] = useState<BookState>({ kind: 'loading' });
  const [pageNumber, setPageNumber] = useState<number>(1);
  const [pageContent, setPageContent] = useState<PageContentState>({ kind: 'loading' });
  const [readerMode, setReaderMode] = useState<ReaderMode>('text');

  useEffect(() => {
    if (!bookId) return;
    const controller = new AbortController();
    Promise.all([
      apiGetRaw<BookDetail>(`/api/v1/library/books/${bookId}`, { signal: controller.signal }),
      apiGetRaw<PageSummary[]>(`/api/v1/library/books/${bookId}/pages`, {
        signal: controller.signal,
      }),
    ])
      .then(([book, pages]) => {
        const sorted = [...(pages ?? [])].sort(
          (a, b) => (a.pageNumber ?? 0) - (b.pageNumber ?? 0),
        );
        setState({ kind: 'success', book, pages: sorted });
        const first = sorted[0]?.pageNumber;
        if (first) setPageNumber(first);
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        const message =
          e instanceof ApiError
            ? `${e.problem.title}${e.problem.detail ? ': ' + e.problem.detail : ''}`
            : e instanceof Error
              ? e.message
              : 'Не удалось загрузить книгу';
        setState({ kind: 'error', message });
      });
    return () => controller.abort();
  }, [bookId]);

  // Загрузка контента текущей страницы. Loading state выставляется в
  // event handlers (goPrev/goNext) и initial useState, не в effect - это
  // правило react-hooks/set-state-in-effect (см. gotchas)
  useEffect(() => {
    if (state.kind !== 'success') return;
    const target = state.pages.find((p) => p.pageNumber === pageNumber);
    if (!target?.id) return;
    const controller = new AbortController();
    apiGetRaw<PageDetail>(`/api/v1/library/pages/${target.id}`, {
      signal: controller.signal,
    })
      .then((page) => {
        setPageContent({ kind: 'success', page });
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        const message =
          e instanceof ApiError
            ? (e.problem.detail ?? e.problem.title)
            : e instanceof Error
              ? e.message
              : 'Не удалось загрузить страницу';
        setPageContent({ kind: 'error', message });
      });
    return () => controller.abort();
  }, [state, pageNumber]);

  const chapterTree: Chapter[] = state.kind === 'success' ? (state.book.chapters ?? []) : [];
  const totalPages = state.kind === 'success' ? state.pages.length : 0;
  const currentIndex =
    state.kind === 'success'
      ? state.pages.findIndex((p) => p.pageNumber === pageNumber)
      : -1;
  const hasPrev = currentIndex > 0;
  const hasNext = state.kind === 'success' && currentIndex < state.pages.length - 1;

  const goPrev = () => {
    if (state.kind !== 'success' || !hasPrev) return;
    const prev = state.pages[currentIndex - 1]?.pageNumber;
    if (prev) {
      setPageContent({ kind: 'loading' });
      setPageNumber(prev);
    }
  };

  const goNext = () => {
    if (state.kind !== 'success' || !hasNext) return;
    const next = state.pages[currentIndex + 1]?.pageNumber;
    if (next) {
      setPageContent({ kind: 'loading' });
      setPageNumber(next);
    }
  };

  /**
   * Goto: переход на конкретный pageNumber. Используется page-jump
   * input'ом и кликом по chapter. Если запрошенный pageNumber не
   * существует - clamp к ближайшему. Это безопаснее чем error: shamela
   * page numbering может иметь gaps.
   */
  const gotoPage = (target: number) => {
    if (state.kind !== 'success' || state.pages.length === 0) return;
    const numbers = state.pages.map((p) => p.pageNumber ?? 0);
    const minN = numbers[0] ?? 1;
    const maxN = numbers[numbers.length - 1] ?? 1;
    let clamped = Math.max(minN, Math.min(maxN, target));
    if (!numbers.includes(clamped)) {
      const sorted = [...numbers].sort((a, b) => Math.abs(a - target) - Math.abs(b - target));
      clamped = sorted[0] ?? clamped;
    }
    if (clamped !== pageNumber) {
      setPageContent({ kind: 'loading' });
      setPageNumber(clamped);
    }
  };

  const currentPageMeta =
    state.kind === 'success' ? state.pages.find((p) => p.pageNumber === pageNumber) : undefined;

  return (
    <main className="min-h-screen bg-slate-50/60">
      <Header />

      <div className="mx-auto flex max-w-[1380px] gap-6 px-6 py-6">
        <aside className="w-[280px] shrink-0">
          <Card className="sticky top-6 max-h-[calc(100vh-7rem)] overflow-y-auto p-4">
            <button
              type="button"
              onClick={() => navigate('/books')}
              className="mb-3 inline-flex items-center gap-1.5 text-[12px] text-slate-600 transition-colors hover:text-indigo-600"
            >
              <ArrowLeft size={14} aria-hidden="true" />К библиотеке
            </button>
            <h3 className="mb-3 text-[11px] font-semibold uppercase tracking-wide text-slate-500">
              Содержание
            </h3>
            {state.kind === 'loading' && (
              <div className="text-[12px] text-slate-400">Загрузка</div>
            )}
            {state.kind === 'success' && chapterTree.length === 0 && (
              <p className="text-[12px] text-slate-400">Главы не указаны</p>
            )}
            {state.kind === 'success' && chapterTree.length > 0 && (
              <ChapterList
                nodes={chapterTree}
                depth={0}
                onSelect={gotoPage}
                currentPage={pageNumber}
                bookLanguage={state.book.language}
              />
            )}
          </Card>
        </aside>

        <div className="min-w-0 flex-1">
          {state.kind === 'loading' && (
            <div className="flex items-center justify-center gap-2 py-20 text-[13px] text-slate-500">
              <Loader2 size={16} className="animate-spin" aria-hidden="true" />
              Загрузка книги
            </div>
          )}

          {state.kind === 'error' && (
            <Card className="border-red-200 bg-red-50 p-5">
              <div className="flex items-start gap-3">
                <AlertCircle
                  size={20}
                  className="mt-0.5 shrink-0 text-red-600"
                  aria-hidden="true"
                />
                <div>
                  <p className="font-semibold text-red-900">Ошибка</p>
                  <p className="mt-1 text-[13px] text-red-800">{state.message}</p>
                </div>
              </div>
            </Card>
          )}

          {state.kind === 'success' && bookId && (
            <>
              <BookHeader book={state.book} pagesCount={totalPages}>
                <ReaderModeSwitch mode={readerMode} onChange={setReaderMode} />
              </BookHeader>
              {readerMode === 'text' && (
                <>
                  <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white px-4 py-2.5">
                    <Button
                      variant="ghost"
                      size="sm"
                      icon={ChevronLeft}
                      onClick={goPrev}
                      disabled={!hasPrev}
                    >
                      Предыдущая
                    </Button>
                    <PageJump
                      key={pageNumber}
                      currentPage={pageNumber}
                      totalPages={totalPages}
                      currentPrintedPage={currentPageMeta?.printedPage ?? null}
                      currentPart={currentPageMeta?.part ?? null}
                      onJump={gotoPage}
                    />
                    <Button
                      variant="ghost"
                      size="sm"
                      iconRight={ChevronRight}
                      onClick={goNext}
                      disabled={!hasNext}
                    >
                      Следующая
                    </Button>
                  </div>
                  <PageView state={pageContent} bookLanguage={state.book.language} />
                </>
              )}
              {readerMode === 'pdf' && (
                <Suspense
                  fallback={
                    <Card className="p-12 text-center">
                      <Loader2 size={20} className="mx-auto animate-spin text-slate-400" />
                      <p className="mt-2 text-[12px] text-slate-500">Загрузка PDF viewer'а</p>
                    </Card>
                  }
                >
                  <PdfViewer bookId={bookId} isArabic={state.book.language === 'ar'} />
                </Suspense>
              )}
            </>
          )}
        </div>
      </div>
    </main>
  );
}

export default BookReaderPage;
