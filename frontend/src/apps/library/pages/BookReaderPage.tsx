import { lazy, Suspense, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { AlertCircle, ChevronLeft, ChevronRight, Loader2, ArrowLeft, Maximize2, X } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Button from '@/shared/components/ui/Button';
import Header from '@/shared/components/layout/Header';
import BookHeader from '@/shared/components/reader/BookHeader';
import ReaderModeSwitch from '@/shared/components/reader/ReaderModeSwitch';
import ChapterList, { type Chapter } from '@/shared/components/reader/ChapterList';
import PageJump from '@/shared/components/reader/PageJump';
import PageView, { type PageContentState, type PageDetail } from '@/shared/components/reader/PageView';
import { type ReaderMode } from '@/shared/components/reader/utils';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import type { components } from '@/shared/api/types';

// Lazy-load PdfViewer - тяжёлая зависимость (react-pdf + pdfjs-dist
// весит ~600KB gzipped). Подгружается только при переключении в PDF mode
const PdfViewer = lazy(() => import('@/shared/components/reader/PdfViewer'));

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
 * подкомпонентам в `shared/components/reader/`.
 */
function BookReaderPage() {
  const { bookId } = useParams<{ bookId: string }>();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [state, setState] = useState<BookState>({ kind: 'loading' });
  const [pageNumber, setPageNumber] = useState<number>(1);
  const [pageContent, setPageContent] = useState<PageContentState>({ kind: 'loading' });
  const [readerMode, setReaderMode] = useState<ReaderMode>('text');
  // Inline PDF preview - shamela-like UX: кнопка 📕 на странице text mode
  // открывает PDF в overlay внизу экрана. Из preview можно "развернуть на
  // весь экран" → readerMode=pdf + закрытие overlay
  const [pdfPreviewOpen, setPdfPreviewOpen] = useState(false);
  // Bottom-sheet height в vh (25..90). Resizable через drag handle на
  // верхнем border. По умолчанию 65vh - комфортно видеть и text сверху
  // и PDF снизу одновременно
  const [sheetHeightVh, setSheetHeightVh] = useState(65);

  const handleResizeStart = (e: React.PointerEvent<HTMLDivElement>) => {
    e.preventDefault();
    const startY = e.clientY;
    const startHeight = sheetHeightVh;
    const onMove = (ev: PointerEvent) => {
      const delta = startY - ev.clientY;
      const deltaVh = (delta / window.innerHeight) * 100;
      const next = Math.max(25, Math.min(90, startHeight + deltaVh));
      setSheetHeightVh(next);
    };
    const onUp = () => {
      document.removeEventListener('pointermove', onMove);
      document.removeEventListener('pointerup', onUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
    document.body.style.cursor = 'ns-resize';
    document.body.style.userSelect = 'none';
    document.addEventListener('pointermove', onMove);
    document.addEventListener('pointerup', onUp);
  };

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

  // Deep link handling после загрузки pages - применяем query params:
  // ?pageId=X - navigate на страницу X (fallback на 1 с toast если не найдена)
  // ?highlight=start-end - параметр для PageView highlightRange prop (memo ниже)
  // ?pdf=1 - переключить в PDF mode
  // ?pdfPageNumber=N - initial page в PDF
  useEffect(() => {
    if (state.kind !== 'success' || state.pages.length === 0) return;
    const pdfFlag = searchParams.get('pdf') === '1';
    if (pdfFlag) {
      setReaderMode('pdf');
      const pdfPage = searchParams.get('pdfPageNumber');
      if (pdfPage) {
        const n = parseInt(pdfPage, 10);
        if (Number.isFinite(n) && n >= 1) setPageNumber(n);
      }
      return;
    }
    const pageIdParam = searchParams.get('pageId');
    if (pageIdParam) {
      const found = state.pages.find((p) => p.id === pageIdParam);
      if (found?.pageNumber) {
        setPageNumber(found.pageNumber);
      } else {
        toast.warning('Страница не найдена, открыта первая');
        setPageNumber(state.pages[0]?.pageNumber ?? 1);
      }
    }
  }, [state, searchParams]);

  // Highlight range из ?highlight=start-end - parsing для PageView prop.
  // Silent fallback при corrupted значениях (NaN), не падаем.
  const highlightRange = useMemo<[number, number] | null>(() => {
    const param = searchParams.get('highlight');
    if (!param) return null;
    const parts = param.split('-');
    if (parts.length !== 2) return null;
    const startStr = parts[0];
    const endStr = parts[1];
    if (!startStr || !endStr) return null;
    const s = parseInt(startStr, 10);
    const e = parseInt(endStr, 10);
    if (!Number.isFinite(s) || !Number.isFinite(e) || e <= s) return null;
    return [s, e];
  }, [searchParams]);

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

  /**
   * Уникальные `part` значения через всю книгу - для dropdown Тома.
   * Sorted в порядке появления (первая встреча идёт первой) - shamela
   * хранит pages в логическом order, distinct preserve этот же order
   */
  const distinctParts: string[] = state.kind === 'success'
    ? Array.from(
        state.pages.reduce<Map<string, true>>((acc, p) => {
          const part = p.part;
          if (part != null && part !== '' && !acc.has(part)) acc.set(part, true);
          return acc;
        }, new Map()).keys(),
      )
    : [];

  /** Смена тома - переходим на первую страницу указанного part */
  const handlePartChange = (newPart: string) => {
    if (state.kind !== 'success') return;
    const firstPage = state.pages.find((p) => p.part === newPart);
    if (firstPage?.pageNumber) gotoPage(firstPage.pageNumber);
  };

  /** Jump к (currentPart, printedPage). Если page с такой комбинацией нет -
   * пробуем по всей книге (если у юзера например указан printedPage без
   * учёта тома). Если и тогда нет - silently ignore */
  const handlePrintedPageJump = (printedPage: string) => {
    if (state.kind !== 'success') return;
    const target =
      state.pages.find(
        (p) => p.part === currentPageMeta?.part && p.printedPage === printedPage,
      ) ?? state.pages.find((p) => p.printedPage === printedPage);
    if (target?.pageNumber) gotoPage(target.pageNumber);
  };

  // shamela mapping для PDF: `part` (том) → fileIndex, `printedPage` →
  // pdfPage. printedPage TEXT в БД (может быть "39" или "أ"), parseInt
  // отсеивает арабские буквы → null fallback на page 1
  const currentPart = currentPageMeta?.part ?? null;
  const parsedPrintedPage = currentPageMeta?.printedPage
    ? parseInt(currentPageMeta.printedPage, 10)
    : null;
  const currentPrintedPage =
    parsedPrintedPage != null && Number.isFinite(parsedPrintedPage) ? parsedPrintedPage : null;

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
                  {/* Sticky top - prev/next кнопки всегда accessible над
                      bottom-sheet PDF overlay (если он открыт, занимает 65vh
                      снизу и может перекрыть toolbar пушенный вниз большим
                      BookHeader). z-30 < aside z-40 но они на разных y, не
                      перекрываются. -mt чтобы прижать вплотную к Header */}
                  <div className="sticky top-2 z-30 mb-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white px-4 py-2.5 shadow-sm">
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
                      availableParts={distinctParts}
                      onPartChange={handlePartChange}
                      onPrintedPageJump={handlePrintedPageJump}
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
                  <PageView
                    state={pageContent}
                    bookLanguage={state.book.language}
                    onOpenPdfPreview={() => setPdfPreviewOpen(true)}
                    highlightRange={highlightRange}
                  />
                </>
              )}
              {readerMode === 'pdf' && (
                <>
                  {/* В fullscreen PDF mode - кнопка "Назад к тексту" чтобы юзер
                      мог вернуться к чтению с того места где был */}
                  <div className="mb-3 flex justify-end">
                    <Button
                      variant="ghost"
                      size="sm"
                      icon={ArrowLeft}
                      onClick={() => setReaderMode('text')}
                    >
                      Назад к тексту
                    </Button>
                  </div>
                  <Suspense
                    fallback={
                      <Card className="p-12 text-center">
                        <Loader2 size={20} className="mx-auto animate-spin text-slate-400" />
                        <p className="mt-2 text-[12px] text-slate-500">Загрузка PDF viewer'а</p>
                      </Card>
                    }
                  >
                    <PdfViewer
                key={`${currentPart ?? ''}-${currentPrintedPage ?? ''}`}
                bookId={bookId}
                isArabic={state.book.language === 'ar'}
                initialPart={currentPart}
                initialPrintedPage={currentPrintedPage}
              />
                  </Suspense>
                </>
              )}
            </>
          )}
        </div>
      </div>

      {/* Inline PDF preview overlay - shamela-like bottom sheet с PdfViewer.
          Не модалка а fixed bottom-positioned panel чтобы text сверху
          оставался видимым (юзер сравнивает text транскрипцию с PDF
          оригиналом). Кнопка Maximize2 = развернуть в fullscreen mode */}
      {pdfPreviewOpen && state.kind === 'success' && bookId && (
        <aside
          className="fixed inset-x-0 bottom-0 z-40 flex flex-col border-t border-slate-300 bg-white shadow-2xl"
          style={{ height: `${sheetHeightVh}vh` }}
        >
          {/* Drag handle - тонкая зона сверху для resize высоты. Визуально
              «гриф» из 3 точек по центру, hover показывает усиление */}
          <div
            role="separator"
            aria-orientation="horizontal"
            aria-label="Изменить высоту PDF preview"
            onPointerDown={handleResizeStart}
            className="group flex h-3 cursor-ns-resize items-center justify-center border-b border-slate-200 bg-slate-50 transition-colors hover:bg-indigo-50"
          >
            <span className="h-0.5 w-10 rounded-full bg-slate-300 transition-colors group-hover:bg-indigo-400" />
          </div>
          <div className="flex items-center justify-between border-b border-slate-200 bg-slate-50 px-4 py-2">
            <h3 className="text-[13px] font-semibold text-slate-700">
              PDF оригинал
              {currentPageMeta?.printedPage && (
                <span className="ms-2 text-slate-500">
                  · стр. {currentPageMeta.printedPage}
                  {currentPart && ` · том ${currentPart}`}
                </span>
              )}
            </h3>
            <div className="flex items-center gap-1">
              <Button
                variant="ghost"
                size="sm"
                icon={Maximize2}
                onClick={() => {
                  setReaderMode('pdf');
                  setPdfPreviewOpen(false);
                }}
              >
                На весь экран
              </Button>
              <button
                type="button"
                onClick={() => setPdfPreviewOpen(false)}
                className="grid h-7 w-7 place-items-center rounded text-slate-500 hover:bg-slate-100 hover:text-slate-700"
                aria-label="Закрыть PDF preview"
              >
                <X size={14} />
              </button>
            </div>
          </div>
          <div className="flex-1 overflow-auto bg-slate-100">
            <Suspense
              fallback={
                <div className="grid h-full place-items-center text-[12px] text-slate-500">
                  <Loader2 size={20} className="animate-spin" />
                </div>
              }
            >
              <PdfViewer
                key={`${currentPart ?? ''}-${currentPrintedPage ?? ''}`}
                bookId={bookId}
                isArabic={state.book.language === 'ar'}
                initialPart={currentPart}
                initialPrintedPage={currentPrintedPage}
              />
            </Suspense>
          </div>
        </aside>
      )}
    </main>
  );
}

export default BookReaderPage;
