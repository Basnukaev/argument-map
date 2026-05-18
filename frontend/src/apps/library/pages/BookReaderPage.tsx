import { lazy, Suspense, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { AlertCircle, ChevronLeft, ChevronRight, Loader2, ArrowLeft, Maximize2, X, List, Users, Lock } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Button from '@/shared/components/ui/Button';
import Modal from '@/shared/components/ui/Modal';
import Header from '@/shared/components/layout/Header';
import BookHeader from '@/shared/components/reader/BookHeader';
import ReaderModeSwitch from '@/shared/components/reader/ReaderModeSwitch';
import ChapterList, { type Chapter } from '@/shared/components/reader/ChapterList';
import PageJump from '@/shared/components/reader/PageJump';
import PageView, { type PageContentState, type PageDetail } from '@/shared/components/reader/PageView';
import { type ReaderMode } from '@/shared/components/reader/utils';
import VisibilityBadge from '@/shared/components/visibility/VisibilityBadge';
import VisibilityRadioGroup, {
  type Visibility,
} from '@/shared/components/visibility/VisibilityRadioGroup';
import BookMembersModal from '@/apps/library/components/BookMembersModal';
import { apiGetRaw, apiPatchRaw, ApiError, formatApiError } from '@/shared/api/client';
import { formatPermissionError } from '@/shared/api/permissionErrors';
import { toast } from '@/shared/stores/toastStore';
import { useAuthStore } from '@/shared/stores/authStore';
import { useLocaleStore, useT } from '@/shared/i18n';
import { useIsMobile } from '@/shared/hooks/useViewport';
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
  const locale = useLocaleStore((s) => s.locale);
  const t = useT();
  const currentUser = useAuthStore((s) => s.user);
  // Стрелки toolbar пагинации - по локали интерфейса, не по языку книги.
  // Навигация - UI-элемент: «следующая» = по направлению чтения локали
  const prevIcon = locale === 'ar' ? ChevronRight : ChevronLeft;
  const nextIcon = locale === 'ar' ? ChevronLeft : ChevronRight;
  const [state, setState] = useState<BookState>({ kind: 'loading' });
  const [refreshKey, setRefreshKey] = useState(0);
  const [pageNumber, setPageNumber] = useState<number>(1);
  const [pageContent, setPageContent] = useState<PageContentState>({ kind: 'loading' });
  const [readerMode, setReaderMode] = useState<ReaderMode>('text');
  // Members + visibility modals (22.c.f, ADR-043 Amendment). Open only
  // for owner/admin
  const [membersOpen, setMembersOpen] = useState(false);
  const [visibilityModalOpen, setVisibilityModalOpen] = useState(false);
  const [savingVisibility, setSavingVisibility] = useState(false);
  // Inline PDF preview - shamela-like UX: кнопка 📕 на странице text mode
  // открывает PDF в overlay внизу экрана. Из preview можно "развернуть на
  // весь экран" → readerMode=pdf + закрытие overlay
  const [pdfPreviewOpen, setPdfPreviewOpen] = useState(false);
  // Bottom-sheet height в vh (25..90). Resizable через drag handle на
  // верхнем border. По умолчанию 65vh - комфортно видеть и text сверху
  // и PDF снизу одновременно
  const [sheetHeightVh, setSheetHeightVh] = useState(65);
  // На mobile sidebar с chapters tree вынесен в drawer (Modal full-screen),
  // открывается через кнопку List в content. На desktop sidebar inline.
  const isMobile = useIsMobile();
  const [chaptersDrawerOpen, setChaptersDrawerOpen] = useState(false);

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
              : t('reader.book_load_failed');
        setState({ kind: 'error', message });
      });
    return () => controller.abort();
  }, [bookId, refreshKey, t]);

  // Deep link handling после загрузки pages - применяем query params один
  // раз когда state становится success. Это инициализация под query, не
  // sync state - eslint правило set-state-in-effect не покрывает initial
  // deep link case, eslint-disable обоснован (one-shot on success).
  useEffect(() => {
    if (state.kind !== 'success' || state.pages.length === 0) return;
    const pdfFlag = searchParams.get('pdf') === '1';
    if (pdfFlag) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
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
        toast.warning(t('reader.page_not_found'));
         
        setPageNumber(state.pages[0]?.pageNumber ?? 1);
      }
    }
  }, [state, searchParams, t]);

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
              : t('citation_picker.page_load_failed');
        setPageContent({ kind: 'error', message });
      });
    return () => controller.abort();
  }, [state, pageNumber, t]);

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

  // ChapterSidebar content - переиспользуется в desktop aside и mobile drawer
  const handleChapterSelect = (pn: number) => {
    gotoPage(pn);
    setChaptersDrawerOpen(false);
  };

  // Permission check на основании book.createdBy. Бэк - источник истины и
  // сам бросает 403 при попытке write без прав (мы локализуем через
  // formatPermissionError). UI hint - чтобы пользователь видел read-only
  // не сделав запрос. ADMIN - bypass'ит проверки на бэке, показываем кнопки
  const book = state.kind === 'success' ? state.book : undefined;
  const visibility: Visibility = (book?.visibility ?? 'PRIVATE') as Visibility;
  const isOwner = Boolean(
    currentUser && book?.createdBy && currentUser.id === book.createdBy,
  );
  const isAdmin = currentUser?.role === 'ADMIN';
  const canWriteOptimistic = isOwner || isAdmin || visibility !== 'PRIVATE';

  async function handleSaveVisibility(next: Visibility) {
    if (!bookId || next === visibility) {
      setVisibilityModalOpen(false);
      return;
    }
    setSavingVisibility(true);
    try {
      await apiPatchRaw(`/api/v1/library/books/${bookId}/visibility`, {
        visibility: next,
      });
      toast.success(t('book.visibility.change_success'));
      setVisibilityModalOpen(false);
      setRefreshKey((k) => k + 1);
    } catch (err: unknown) {
      const permMsg =
        err instanceof ApiError ? formatPermissionError(err, t) : null;
      toast.error(
        permMsg ?? formatApiError(err, t('book.visibility.change_failed')),
      );
    } finally {
      setSavingVisibility(false);
    }
  }

  const chaptersContent = (
    <>
      <button
        type="button"
        onClick={() => navigate('/books')}
        className="mb-3 inline-flex items-center gap-1.5 text-xs text-ink-600 transition-colors hover:text-accent-600"
      >
        <ArrowLeft size={14} aria-hidden="true" />
        {t('reader.back_to_list')}
      </button>
      <h3 className="mb-3 text-xs font-semibold uppercase tracking-wide text-ink-500">
        {t('reader.chapters')}
      </h3>
      {state.kind === 'loading' && (
        <div className="text-xs text-ink-400">{t('common.loading')}</div>
      )}
      {state.kind === 'success' && chapterTree.length === 0 && (
        <p className="text-xs text-ink-400">{t('reader.chapters_empty')}</p>
      )}
      {state.kind === 'success' && chapterTree.length > 0 && (
        <ChapterList
          nodes={chapterTree}
          depth={0}
          onSelect={handleChapterSelect}
          currentPage={pageNumber}
          bookLanguage={state.book.language}
        />
      )}
    </>
  );

  return (
    <main className="min-h-screen bg-bg">
      <Header />

      <div className="mx-auto flex max-w-[1380px] gap-6 px-3 py-4 md:px-6 md:py-6">
        {/* Desktop: inline sidebar. Mobile: hidden - доступ через кнопку
            «Главы» в content (см. ниже) которая открывает drawer Modal */}
        <aside className="hidden w-[280px] shrink-0 md:block">
          <Card className="sticky top-6 max-h-[calc(100dvh-7rem)] overflow-y-auto p-4">
            {chaptersContent}
          </Card>
        </aside>

        <div className="min-w-0 flex-1">
          {state.kind === 'loading' && (
            <div className="flex items-center justify-center gap-2 py-20 text-sm text-ink-500">
              <Loader2 size={16} className="animate-spin" aria-hidden="true" />
              {t('common.loading')}
            </div>
          )}

          {state.kind === 'error' && (
            <Card className="border-err-500/40 bg-err-100 p-5">
              <div className="flex items-start gap-3">
                <AlertCircle
                  size={20}
                  className="mt-0.5 shrink-0 text-err-700"
                  aria-hidden="true"
                />
                <div>
                  <p className="font-semibold text-err-700">{t('common.error')}</p>
                  <p className="mt-1 text-sm text-err-700">{state.message}</p>
                </div>
              </div>
            </Card>
          )}

          {state.kind === 'success' && bookId && (
            <>
              {/* BookHeader в Card-wrapper для consistency с PageView ширины */}
              <Card className="mb-4 p-5">
                <BookHeader book={state.book} pagesCount={totalPages}>
                  {/* Visibility / members controls (22.c.f, ADR-043 Amendment).
                      Owner/admin видят кнопку смены visibility (badge кликабелен)
                      и кнопку «Управление участниками» при SHARED. Прочие user'ы
                      только read-only badge + Lock badge если read-only */}
                  <div className="flex flex-wrap items-center gap-2">
                    {!canWriteOptimistic && (
                      <span
                        title={t('book.permission.read_only_hint')}
                        className="inline-flex items-center gap-1 rounded-sm border border-border bg-elevated px-1.5 py-0.5 text-xs font-medium text-ink-500"
                      >
                        <Lock size={11} aria-hidden />
                        {t('book.permission.read_only')}
                      </span>
                    )}
                    {(isOwner || isAdmin) && (
                      <button
                        type="button"
                        onClick={() => setVisibilityModalOpen(true)}
                        title={t('book.visibility.change_action')}
                        className="inline-flex items-center gap-1 rounded-sm border border-border bg-elevated px-1.5 py-0.5 text-xs font-medium text-ink-700 transition-colors hover:bg-ink-100"
                      >
                        <VisibilityBadge
                          visibility={visibility}
                          labelPrefix="book.visibility"
                          className="border-0 bg-transparent !px-0 !py-0"
                        />
                      </button>
                    )}
                    {!isOwner && !isAdmin && (
                      <VisibilityBadge
                        visibility={visibility}
                        labelPrefix="book.visibility"
                      />
                    )}
                    {(isOwner || isAdmin) && visibility === 'SHARED' && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        icon={Users}
                        onClick={() => setMembersOpen(true)}
                      >
                        {t('book.members.manage_button')}
                      </Button>
                    )}
                  </div>
                </BookHeader>
              </Card>
              {readerMode === 'text' && (
                <>
                  {/* Toolbar: prev/next + page jump + reader mode switch.
                      Desktop: sticky top-2 (z-30 < aside z-40).
                      Mobile: НЕ sticky - browser address-bar collapsing
                      делает sticky прыгающим и недостойным места на узком
                      экране. На mobile добавлена кнопка «Главы» которая
                      открывает drawer Modal со списком (chapters tree
                      основной отсутствующий элемент при скрытом sidebar) */}
                  <div className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border bg-elevated px-3 py-2.5 shadow-sm md:sticky md:top-2 md:z-30 md:gap-3 md:px-4">
                    {/* Mobile only: «Главы» кнопка слева. Desktop: hidden
                        (chapters в inline sidebar) */}
                    <Button
                      variant="ghost"
                      size="sm"
                      icon={List}
                      onClick={() => setChaptersDrawerOpen(true)}
                      className="md:hidden"
                    >
                      {t('reader.chapters')}
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      icon={prevIcon}
                      onClick={goPrev}
                      disabled={!hasPrev}
                    >
                      {t('reader.prev')}
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
                    <div className="flex items-center gap-2">
                      <ReaderModeSwitch mode={readerMode} onChange={setReaderMode} />
                      <Button
                        variant="ghost"
                        size="sm"
                        iconRight={nextIcon}
                        onClick={goNext}
                        disabled={!hasNext}
                      >
                      {t('reader.next')}
                      </Button>
                    </div>
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
                      мог вернуться к чтению с того места где был.
                      На mobile добавляем кнопку «Главы» которая открывает
                      drawer (т.к. inline sidebar скрыт на mobile) */}
                  <div className="mb-3 flex items-center justify-between gap-2">
                    <Button
                      variant="ghost"
                      size="sm"
                      icon={List}
                      onClick={() => setChaptersDrawerOpen(true)}
                      className="md:hidden"
                    >
                      {t('reader.chapters')}
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      icon={ArrowLeft}
                      onClick={() => setReaderMode('text')}
                      className="ms-auto"
                    >
                      {t('reader.back_to_text')}
                    </Button>
                  </div>
                  <Suspense
                    fallback={
                      <Card className="p-12 text-center">
                        <Loader2 size={20} className="mx-auto animate-spin text-ink-400" />
                        <p className="mt-2 text-xs text-ink-500">{t('reader.pdf_preview_loading')}</p>
                      </Card>
                    }
                  >
                    <PdfViewer
                key={`${currentPart ?? ''}-${currentPrintedPage ?? ''}`}
                bookId={bookId}
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
          оригиналом). Кнопка Maximize2 = развернуть в fullscreen mode.
          На mobile - занимает всю высоту (h-dvh) без drag handle:
          одновременное чтение text + PDF на 375px нерелевантно, проще
          показать PDF как fullscreen modal-like overlay */}
      {pdfPreviewOpen && state.kind === 'success' && bookId && (
        <aside
          className="fixed inset-x-0 bottom-0 z-40 flex flex-col border-t border-border-strong bg-elevated shadow-2xl max-md:inset-0 max-md:h-dvh"
          style={isMobile ? undefined : { height: `${sheetHeightVh}vh` }}
        >
          {/* Drag handle - тонкая зона сверху для resize высоты. Визуально
              «гриф» из 3 точек по центру, hover показывает усиление.
              Скрыт на mobile - sheet всегда fullscreen */}
          <div
            role="separator"
            aria-orientation="horizontal"
            aria-label={t('reader.pdf_preview_resize_aria')}
            onPointerDown={handleResizeStart}
            className="group hidden h-3 cursor-ns-resize items-center justify-center border-b border-border bg-ink-50 transition-colors hover:bg-accent-50 md:flex"
          >
            <span className="h-0.5 w-10 rounded-full bg-ink-300 transition-colors group-hover:bg-accent-500" />
          </div>
          <div className="flex items-center justify-between border-b border-border bg-ink-50 px-4 py-2">
            <h3 className="text-sm font-semibold text-ink-700">
              {t('reader.pdf_original')}
              {currentPageMeta?.printedPage && (
                <span className="ms-2 text-ink-500">
                  · {t('reader.short.page_prefix')} <bdi dir="ltr">{currentPageMeta.printedPage}</bdi>
                  {currentPart && <> · {t('reader.volume').toLowerCase()} <bdi>{currentPart}</bdi></>}
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
                {t('reader.pdf_fullscreen')}
              </Button>
              <button
                type="button"
                onClick={() => setPdfPreviewOpen(false)}
                className="grid h-7 w-7 place-items-center rounded text-ink-500 hover:bg-ink-100 hover:text-ink-700"
                aria-label={t('reader.pdf_preview_close')}
              >
                <X size={14} />
              </button>
            </div>
          </div>
          <div className="flex-1 overflow-auto bg-ink-100">
            <Suspense
              fallback={
                <div className="grid h-full place-items-center text-xs text-ink-500">
                  <Loader2 size={20} className="animate-spin" />
                </div>
              }
            >
              <PdfViewer
                key={`${currentPart ?? ''}-${currentPrintedPage ?? ''}`}
                bookId={bookId}
                initialPart={currentPart}
                initialPrintedPage={currentPrintedPage}
              />
            </Suspense>
          </div>
        </aside>
      )}

      {/* Mobile chapters drawer - Modal full-screen уже из Фазы 1. Только
          mount/unmount по условию isMobile && open, чтобы native <dialog>
          showModal не конфликтовал с PDF preview overlay */}
      {isMobile && chaptersDrawerOpen && (
        <Modal
          open
          onClose={() => setChaptersDrawerOpen(false)}
          title={t('reader.chapters')}
        >
          {chaptersContent}
        </Modal>
      )}

      {/* Members modal - SHARED books, открывается только для owner/admin */}
      {membersOpen && bookId && (
        <BookMembersModal
          open={membersOpen}
          bookId={bookId}
          ownerUserId={book?.createdBy}
          onClose={() => setMembersOpen(false)}
        />
      )}

      {/* Change visibility modal - radio group + Save/Cancel */}
      {visibilityModalOpen && (
        <Modal
          open={visibilityModalOpen}
          onClose={() => setVisibilityModalOpen(false)}
          title={t('book.visibility.field_label')}
          subtitle={t('book.visibility.field_hint')}
          maxWidth="max-w-xl"
        >
          <BookVisibilityChangeForm
            initial={visibility}
            saving={savingVisibility}
            onCancel={() => setVisibilityModalOpen(false)}
            onSave={(v) => void handleSaveVisibility(v)}
          />
        </Modal>
      )}
    </main>
  );
}

interface VisibilityChangeFormProps {
  initial: Visibility;
  saving: boolean;
  onCancel: () => void;
  onSave: (next: Visibility) => void;
}

/**
 * Local форма выбора visibility - параллельна `VisibilityChangeForm` из
 * TopicGraphPage. Дублирование - 10 строк, абстракцию не выделяем (YAGNI:
 * если 3-я страница появится с тем же паттерном - тогда вынесем generic
 * `<VisibilityChangeForm>` в shared)
 */
function BookVisibilityChangeForm({
  initial,
  saving,
  onCancel,
  onSave,
}: VisibilityChangeFormProps) {
  const t = useT();
  const [draft, setDraft] = useState<Visibility>(initial);
  return (
    <div className="flex flex-col gap-4">
      <VisibilityRadioGroup
        value={draft}
        onChange={setDraft}
        disabled={saving}
        labelPrefix="book.visibility"
      />
      <div className="flex items-center justify-end gap-2">
        <Button variant="ghost" onClick={onCancel} disabled={saving}>
          {t('common.cancel')}
        </Button>
        <Button onClick={() => onSave(draft)} disabled={saving}>
          {saving ? t('common.loading') : t('book.visibility.change_action')}
        </Button>
      </div>
    </div>
  );
}

export default BookReaderPage;
