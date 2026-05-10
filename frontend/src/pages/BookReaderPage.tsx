import { lazy, Suspense, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import {
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  FileText,
  Loader2,
  ArrowLeft,
  BookOpen,
  Image as ImageIcon,
} from 'lucide-react';
import Card from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import Header from '@/components/layout/Header';
import { apiGetRaw, ApiError } from '@/api/client';
import type { components } from '@/api/types';

// Lazy-load PdfViewer - тяжёлая зависимость (react-pdf + pdfjs-dist
// весит ~600KB gzipped). Подгружается только если пользователь
// переключается на PDF mode. Если он остаётся в тексте - bundle
// не вырастает
const PdfViewer = lazy(() => import('@/components/library/PdfViewer'));

type ReaderMode = 'text' | 'pdf';

type BookDetail = components['schemas']['BookDetailResponse'];
// Дополняем ChapterResponse полем children - springdoc-openapi 2.x не
// выводит self-referential properties в /v3/api-docs (известная gotcha,
// см. gotchas.md). В runtime JSON children приходит (LibraryDtoMappers
// строит nested tree), но в types.ts его нет. Self-referential
// intersection даёт type-safe доступ к рекурсивной структуре
type Chapter = components['schemas']['ChapterResponse'] & {
  children?: Chapter[];
};
// Source-first поля (миграция 19, ADR-021) - в runtime есть после
// рестарта бэка с миграцией 19, но types.ts регенерируется отдельно.
// Intersection даёт безопасный доступ; после regen-api схлопнется.
type PageDetail = components['schemas']['PageResponse'] & {
  printedPage?: string | null;
  part?: string | null;
  pdfPageNumber?: number | null;
};
type PageSummary = components['schemas']['PageSummary'] & {
  printedPage?: string | null;
  part?: string | null;
};

type BookState =
  | { kind: 'loading' }
  | { kind: 'success'; book: BookDetail; pages: PageSummary[] }
  | { kind: 'error'; message: string };

type PageContentState =
  | { kind: 'loading' }
  | { kind: 'success'; page: PageDetail }
  | { kind: 'error'; message: string };

/**
 * Эвристика: если language === 'ar' или контент содержит арабские
 * символы (Unicode-блок 0x0600-0x06FF), это арабский текст -
 * рендерим RTL + naskh-шрифт.
 */
function isArabicText(text: string | undefined): boolean {
  if (!text) return false;
  return /[؀-ۿ]/.test(text);
}

/**
 * Чистит shamela-specific артефакты в HTML-контенте перед
 * dangerouslySetInnerHTML. Shamela использует фирменный шрифт MUSHAF
 * который имеет глифы для специальных Unicode-символов (U+820C `舄`,
 * U+FDFA `ﷺ` lig, U+FDFD bismillah lig, etc) - их фронт-страница
 * shamela.ws рендерит как иконки. У нас Noto Naskh Arabic их не
 * стилизует или вообще не имеет глифов → отображается мусор.
 *
 * На MVP - удаляем известные маркеры. ﷺ и bismillah-лигатура
 * оставляем, они есть в Noto Naskh.
 */
function sanitizeShamelaContent(html: string): string {
  return html
    .replace(/舄/g, '')           // U+820C - shamela title marker
    .replace(/[-]/g, ''); // Private Use Area - шрифт-specific glyphs
}

/**
 * shamela-bibliography приходит одной плоской строкой с ключами через
 * пробел: "الكتاب: ... المؤلف: ... تحقيق: ... الطبعة: ...". JS-парсер
 * вставляет \n перед каждым ключом (кроме первого) - вместе с
 * white-space: pre-line в CSS это даёт многострочное отображение.
 *
 * Список ключей расширяется по мере обнаружения новых форматов в
 * других книгах shamela.
 */
const SHAMELA_BIBLIOGRAPHY_KEYS = [
  'الكتاب',
  'المؤلف',
  'المحقق',
  'تحقيق',
  'الناشر',
  'الطبعة',
  'سنة النشر',
  'تاريخ النشر',
  'عدد الأجزاء',
  'الجزء',
  'الصفحة',
  'عدد الصفحات',
  'حجم الكتاب',
  'مصدر الكتاب',
];

function formatShamelaBibliography(raw: string | undefined): string {
  if (!raw) return '';
  let result = raw.trim();
  for (const key of SHAMELA_BIBLIOGRAPHY_KEYS) {
    // ищем " <key>:" (пробел перед ключом, чтобы не сломать ключ в начале строки),
    // ставим перед ним перенос
    const re = new RegExp(`\\s+(${key}\\s*:)`, 'g');
    result = result.replace(re, '\n$1');
  }
  return result;
}

function BookReaderPage() {
  const { bookId } = useParams<{ bookId: string }>();
  const navigate = useNavigate();
  const [state, setState] = useState<BookState>({ kind: 'loading' });
  const [pageNumber, setPageNumber] = useState<number>(1);
  const [pageContent, setPageContent] = useState<PageContentState>({ kind: 'loading' });
  const [readerMode, setReaderMode] = useState<ReaderMode>('text');

  // загрузка book detail + список pages
  useEffect(() => {
    if (!bookId) return;
    const controller = new AbortController();
    Promise.all([
      apiGetRaw<BookDetail>(`/api/v1/library/books/${bookId}`, {
        signal: controller.signal,
      }),
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

  // загрузка контента текущей страницы. Если target.id не найден -
  // молча не делаем fetch (пользователь не может выбрать несуществующий
  // pageNumber через goPrev/goNext). Loading state выставляется в
  // event handlers (goPrev/goNext) и initial useState, не в effect -
  // это правило react-hooks/set-state-in-effect (см. gotchas)
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
            ? e.problem.detail ?? e.problem.title
            : e instanceof Error
              ? e.message
              : 'Не удалось загрузить страницу';
        setPageContent({ kind: 'error', message });
      });
    return () => controller.abort();
  }, [state, pageNumber]);

  const chapterTree = state.kind === 'success' ? (state.book.chapters ?? []) : [];
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
   * input'ом и кликом по chapter в side-panel. Если запрошенный
   * pageNumber не существует в state.pages - clamp к ближайшему
   * (первой/последней). Это безопаснее чем error для пользователя -
   * shamela page numbering может иметь gaps
   */
  const gotoPage = (target: number) => {
    if (state.kind !== 'success' || state.pages.length === 0) return;
    const numbers = state.pages.map((p) => p.pageNumber ?? 0);
    const minN = numbers[0] ?? 1;
    const maxN = numbers[numbers.length - 1] ?? 1;
    let clamped = Math.max(minN, Math.min(maxN, target));
    // ищем ближайший существующий pageNumber (gaps tolerable)
    if (!numbers.includes(clamped)) {
      const sorted = [...numbers].sort((a, b) => Math.abs(a - target) - Math.abs(b - target));
      clamped = sorted[0] ?? clamped;
    }
    if (clamped !== pageNumber) {
      setPageContent({ kind: 'loading' });
      setPageNumber(clamped);
    }
  };

  return (
    <main className="min-h-screen bg-slate-50/60">
      <Header />

      <div className="mx-auto flex max-w-[1380px] gap-6 px-6 py-6">
        {/* Side-panel: chapters */}
        <aside className="w-[280px] shrink-0">
          <Card className="sticky top-6 max-h-[calc(100vh-7rem)] overflow-y-auto p-4">
            <button
              type="button"
              onClick={() => navigate('/books')}
              className="mb-3 inline-flex items-center gap-1.5 text-[12px] text-slate-600 hover:text-indigo-600 transition-colors"
            >
              <ArrowLeft size={14} aria-hidden="true" />
              К библиотеке
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
              <ChapterList nodes={chapterTree} depth={0} onSelect={gotoPage} currentPage={pageNumber} />
            )}
          </Card>
        </aside>

        {/* Main area: header + pagination + content */}
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
                      currentPrintedPage={
                        state.pages.find((p) => p.pageNumber === pageNumber)?.printedPage ?? null
                      }
                      currentPart={
                        state.pages.find((p) => p.pageNumber === pageNumber)?.part ?? null
                      }
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

interface BookHeaderProps {
  book: BookDetail;
  pagesCount: number;
  children?: React.ReactNode;
}

function BookHeader({ book, pagesCount, children }: BookHeaderProps) {
  const isArabic = book.language === 'ar';
  return (
    <div className="mb-4 flex items-start justify-between gap-4">
      <div className="min-w-0 flex-1">
        <div className="mb-1 flex items-center gap-2 text-[11px] uppercase tracking-wide text-slate-500">
          <BookOpen size={12} aria-hidden="true" />
          {book.bookType ?? 'BOOK'}
          <span className="text-slate-300">·</span>
          <span className="font-mono">{pagesCount} стр.</span>
        </div>
        <h1
          className={
            isArabic
              ? 'font-naskh text-[26px] font-bold leading-tight text-slate-900'
              : 'text-[22px] font-bold leading-tight text-slate-900'
          }
          dir={isArabic ? 'rtl' : 'ltr'}
        >
          {book.title ?? '(без названия)'}
        </h1>
        {book.description && (
          <p
            className={
              isArabic
                ? 'book-bibliography mt-2 font-naskh text-[14px] leading-relaxed text-slate-600'
                : 'book-bibliography mt-2 text-[13px] leading-relaxed text-slate-600'
            }
            dir={isArabic ? 'rtl' : 'ltr'}
          >
            {isArabic ? formatShamelaBibliography(book.description) : book.description}
          </p>
        )}
      </div>
      {children && <div className="shrink-0">{children}</div>}
    </div>
  );
}

interface ReaderModeSwitchProps {
  mode: ReaderMode;
  onChange: (mode: ReaderMode) => void;
}

/**
 * Toggle между Text / PDF режимами reader'а. По дизайн-референсу
 * platform_reader.jsx::PageToolbar - сегментированный switcher
 * сnapшотом активного состояния через bg-white + shadow.
 */
function ReaderModeSwitch({ mode, onChange }: ReaderModeSwitchProps) {
  const options: { k: ReaderMode; l: string; icon: typeof FileText }[] = [
    { k: 'text', l: 'Текст', icon: FileText },
    { k: 'pdf', l: 'PDF', icon: ImageIcon },
  ];
  return (
    <div className="inline-flex items-center gap-0.5 rounded-md bg-slate-100 p-0.5">
      {options.map((o) => {
        const Icon = o.icon;
        const active = mode === o.k;
        return (
          <button
            key={o.k}
            type="button"
            onClick={() => onChange(o.k)}
            className={`inline-flex h-7 items-center gap-1.5 rounded px-2.5 text-[12px] font-medium transition-colors ${
              active
                ? 'bg-white text-slate-900 shadow-[0_1px_2px_rgba(15,23,42,0.06)]'
                : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            <Icon size={13} aria-hidden="true" />
            {o.l}
          </button>
        );
      })}
    </div>
  );
}

interface ChapterListProps {
  nodes: ReadonlyArray<Chapter>;
  depth: number;
  onSelect: (pageNumber: number) => void;
  currentPage: number;
}

/**
 * Визуальная иерархия уровней глав (по design-reference
 * platform_reader.jsx::ChapterTreeRow):
 * - depth=0: 15px, font-semibold, slate-900 - root («книги», «тома»)
 * - depth=1: 14px, font-medium, slate-700 - sub-разделы
 * - depth=2: 13px, regular, slate-600 - главы
 * - depth>=3: 12px, regular, slate-500 - под-главы
 *
 * Для арабского ramp размеров чуть больше из-за naskh-шрифта
 * (на 1-2px) - арабские буквы визуально мельче latin при том же
 * font-size, компенсируем.
 *
 * Connector line (border-l/border-r в RTL) - тонкая вертикальная
 * линия слева/справа от группы детей. Создаёт визуальную связь
 * parent → child, помогает читать tree.
 */
function getChapterLevelStyles(depth: number, isArabic: boolean): {
  font: string;
  color: string;
  weight: string;
} {
  const baseSize = isArabic
    ? ['text-[16px]', 'text-[14.5px]', 'text-[13.5px]', 'text-[12.5px]']
    : ['text-[14px]', 'text-[13px]', 'text-[12px]', 'text-[11.5px]'];
  const fontClass = isArabic ? 'font-naskh' : '';
  const size = baseSize[Math.min(depth, baseSize.length - 1)];
  const color =
    depth === 0
      ? 'text-slate-900'
      : depth === 1
        ? 'text-slate-700'
        : depth === 2
          ? 'text-slate-600'
          : 'text-slate-500';
  const weight = depth === 0 ? 'font-semibold' : depth === 1 ? 'font-medium' : '';
  return { font: `${fontClass} ${size}`.trim(), color, weight };
}

function ChapterList({ nodes, depth, onSelect, currentPage }: ChapterListProps) {
  // Connector-rail: тонкая линия для under-root уровней. В RTL смещается
  // вправо через logical border-inline-start (Tailwind border-s-*).
  // Для depth=0 - без рейла (root уровень, нет parent'а к которому
  // привязываться)
  const railClass =
    depth > 0
      ? 'border-s border-slate-200/70 ms-[10px] ps-[6px]'
      : '';

  return (
    <ul className={`${depth === 0 ? 'space-y-0.5' : 'mt-0.5 space-y-0.5'} ${railClass}`}>
      {nodes.map((n) => {
        const isArabic = isArabicText(n.title);
        const styles = getChapterLevelStyles(depth, isArabic);
        const target = n.startPageNumber ?? null;
        const clickable = target != null && target > 0;
        const isCurrent = clickable && target === currentPage;
        const indent = depth * 6;
        const stateClass = !clickable
          ? 'cursor-not-allowed opacity-50'
          : isCurrent
            ? 'bg-indigo-50 text-indigo-700 font-semibold'
            : `${styles.color} hover:bg-slate-100/70 hover:text-indigo-700 cursor-pointer`;
        const children = n.children ?? [];
        return (
          <li key={n.id}>
            <button
              type="button"
              onClick={clickable ? () => onSelect(target) : undefined}
              disabled={!clickable}
              className={`block w-full rounded-md px-2 py-1 text-start leading-snug transition-colors ${styles.font} ${styles.weight} ${stateClass}`}
              style={{ paddingInlineStart: `${indent + 8}px` }}
              dir={isArabic ? 'rtl' : 'ltr'}
              title={
                clickable
                  ? `${n.title ?? ''} (стр. ${target})`
                  : `${n.title ?? ''} (страница не указана)`
              }
            >
              {n.title ?? '(без названия)'}
            </button>
            {children.length > 0 && (
              <ChapterList
                nodes={children}
                depth={depth + 1}
                onSelect={onSelect}
                currentPage={currentPage}
              />
            )}
          </li>
        );
      })}
    </ul>
  );
}

interface PageJumpProps {
  currentPage: number;
  totalPages: number;
  currentPrintedPage: string | null;
  currentPart: string | null;
  onJump: (page: number) => void;
}

/**
 * Input + go-button для прямого перехода к internal pageNumber. Submit
 * либо по Enter в input, либо по клику на кнопку. Игнорирует невалидный
 * input (NaN, < 1) - просто не делает jump.
 *
 * Source-first label (ADR-021): рядом с input показывается оригинальный
 * маркер «стр {printedPage} том {part}» если оба заполнены. Internal
 * pageNumber оставлен для navigation (URL-state, prev/next) - меняем
 * только display.
 *
 * Эвристика на dir: если part содержит арабские символы (предисловие
 * "المقدمة"), рендерим label в RTL чтобы знаки препинания и порядок
 * слов сохранялись.
 */
function PageJump({
  currentPage,
  totalPages,
  currentPrintedPage,
  currentPart,
  onJump,
}: PageJumpProps) {
  // Локальный draft для контролируемого input. Синхронизация с
  // внешним currentPage (после prev/next/chapter-click) идёт через
  // key-prop в родителе - PageJump remount'ится с новым initial
  // state. Идиома проекта (см. memory feedback_react_key_remount)
  // вместо useEffect-сброса который ловит правило set-state-in-effect
  const [draft, setDraft] = useState<string>(String(currentPage));

  const submit = () => {
    const parsed = parseInt(draft, 10);
    if (!Number.isFinite(parsed) || parsed < 1) {
      setDraft(String(currentPage));
      return;
    }
    onJump(parsed);
  };

  const partIsArabic = currentPart != null && isArabicText(currentPart);
  const printedIsArabic = currentPrintedPage != null && isArabicText(currentPrintedPage);
  const hasSourceMarker = currentPrintedPage != null || currentPart != null;

  return (
    <div className="flex items-center gap-3 text-[13px] text-slate-700">
      <div className="flex items-center gap-2">
        <span className="text-slate-500">Страница</span>
        <input
          type="number"
          min={1}
          max={totalPages > 0 ? totalPages : undefined}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              submit();
            }
          }}
          onBlur={submit}
          className="h-7 w-20 rounded border border-slate-300 px-2 text-center font-mono text-[13px] outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
          aria-label="Номер страницы (internal)"
        />
        {totalPages > 0 && (
          <span className="font-mono text-slate-400">/ {totalPages}</span>
        )}
      </div>
      {hasSourceMarker && (
        <div
          className="flex items-center gap-1.5 rounded-md border border-indigo-100 bg-indigo-50/60 px-2 py-1 text-[12px] text-indigo-800"
          title="Маркер страницы в оригинальном издании"
        >
          {currentPart != null && (
            <span
              className={partIsArabic ? 'font-naskh' : 'font-mono'}
              dir={partIsArabic ? 'rtl' : 'ltr'}
            >
              {partIsArabic ? `ج: ${currentPart}` : `Том ${currentPart}`}
            </span>
          )}
          {currentPart != null && currentPrintedPage != null && (
            <span className="text-indigo-300">·</span>
          )}
          {currentPrintedPage != null && (
            <span
              className={printedIsArabic ? 'font-naskh' : 'font-mono'}
              dir={printedIsArabic ? 'rtl' : 'ltr'}
            >
              {printedIsArabic ? `ص: ${currentPrintedPage}` : `Стр ${currentPrintedPage}`}
            </span>
          )}
        </div>
      )}
    </div>
  );
}

interface PageViewProps {
  state: PageContentState;
  bookLanguage: string | undefined;
}

function PageView({ state, bookLanguage }: PageViewProps) {
  if (state.kind === 'loading') {
    return (
      <Card className="p-12 text-center">
        <Loader2 size={20} className="mx-auto animate-spin text-slate-400" aria-hidden="true" />
        <p className="mt-2 text-[12px] text-slate-500">Загрузка страницы</p>
      </Card>
    );
  }
  if (state.kind === 'error') {
    return (
      <Card className="border-red-200 bg-red-50 p-5">
        <div className="flex items-start gap-3">
          <AlertCircle size={20} className="mt-0.5 shrink-0 text-red-600" aria-hidden="true" />
          <p className="text-[13px] text-red-800">{state.message}</p>
        </div>
      </Card>
    );
  }
  const { page } = state;
  const text = page.textContent ?? '';
  const isArabic = bookLanguage === 'ar' || isArabicText(text);

  return (
    <Card className="p-8">
      {!text && !page.imageUrl && (
        <p className="text-center text-[13px] text-slate-400">Страница пустая</p>
      )}
      {page.imageUrl && (
        <img
          src={page.imageUrl}
          alt={`Скан страницы ${page.pageNumber ?? ''}`}
          className="mx-auto mb-4 max-h-[800px] w-auto rounded-md border border-slate-200"
        />
      )}
      {text && (
        <article
          className={
            isArabic
              ? 'book-content font-naskh text-[19px] leading-[2] text-slate-900'
              : 'book-content text-[15px] leading-relaxed text-slate-900'
          }
          dir={isArabic ? 'rtl' : 'ltr'}
          // shamela page.content - HTML со span[data-type="title"] и
          // \r как разделитель строк. Sanitize убирает PUA-маркеры
          // от фирменного шрифта MUSHAF; .book-content в index.css
          // даёт white-space: pre-line чтобы \r/\n работали как
          // линбрейки + стилизует [data-type="title"] как заголовок.
          // TODO: DOMPurify для не-shamela источников (см. gotchas)
          dangerouslySetInnerHTML={{ __html: sanitizeShamelaContent(text) }}
        />
      )}
    </Card>
  );
}

export default BookReaderPage;
