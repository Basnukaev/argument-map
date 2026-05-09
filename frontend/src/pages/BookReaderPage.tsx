import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import {
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  Loader2,
  ArrowLeft,
  BookOpen,
} from 'lucide-react';
import Card from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import Header from '@/components/layout/Header';
import { apiGetRaw, ApiError } from '@/api/client';
import type { components } from '@/api/types';

type BookDetail = components['schemas']['BookDetailResponse'];
type Chapter = components['schemas']['ChapterResponse'];
type PageDetail = components['schemas']['PageResponse'];
type PageSummary = components['schemas']['PageSummary'];

type BookState =
  | { kind: 'loading' }
  | { kind: 'success'; book: BookDetail; pages: PageSummary[] }
  | { kind: 'error'; message: string };

type PageContentState =
  | { kind: 'loading' }
  | { kind: 'success'; page: PageDetail }
  | { kind: 'error'; message: string };

interface ChapterTreeNode extends Chapter {
  children: ChapterTreeNode[];
}

/**
 * Строит дерево из плоского массива {@link ChapterResponse}: бэк
 * возвращает все главы книги одним списком, иерархия выражена через
 * {@code parentChapterId}. Группируем по parent, рекурсивно собираем
 * children. Если parentChapterId указывает на несуществующую главу
 * (orphan), такая глава попадает в roots - не теряется.
 */
function buildChapterTree(chapters: ReadonlyArray<Chapter>): ChapterTreeNode[] {
  const byId = new Map<string, ChapterTreeNode>();
  for (const c of chapters) {
    if (!c.id) continue;
    byId.set(c.id, { ...c, children: [] });
  }
  const roots: ChapterTreeNode[] = [];
  for (const c of chapters) {
    if (!c.id) continue;
    const node = byId.get(c.id)!;
    const parent = c.parentChapterId ? byId.get(c.parentChapterId) : undefined;
    if (parent) {
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  }
  // сортируем по orderIndex на каждом уровне
  const sortRecursive = (nodes: ChapterTreeNode[]) => {
    nodes.sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0));
    for (const n of nodes) sortRecursive(n.children);
  };
  sortRecursive(roots);
  return roots;
}

/**
 * Эвристика: если language === 'ar' или контент содержит арабские
 * символы (Unicode-блок 0x0600-0x06FF), это арабский текст -
 * рендерим RTL + naskh-шрифт.
 */
function isArabicText(text: string | undefined): boolean {
  if (!text) return false;
  return /[؀-ۿ]/.test(text);
}

function BookReaderPage() {
  const { bookId } = useParams<{ bookId: string }>();
  const navigate = useNavigate();
  const [state, setState] = useState<BookState>({ kind: 'loading' });
  const [pageNumber, setPageNumber] = useState<number>(1);
  const [pageContent, setPageContent] = useState<PageContentState>({ kind: 'loading' });

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

  const chapterTree = useMemo(() => {
    if (state.kind !== 'success') return [];
    return buildChapterTree(state.book.chapters ?? []);
  }, [state]);

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
              <ChapterList nodes={chapterTree} depth={0} />
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

          {state.kind === 'success' && (
            <>
              <BookHeader book={state.book} pagesCount={totalPages} />
              <div className="mb-4 flex items-center justify-between rounded-lg border border-slate-200 bg-white px-4 py-2.5">
                <Button
                  variant="ghost"
                  size="sm"
                  icon={ChevronLeft}
                  onClick={goPrev}
                  disabled={!hasPrev}
                >
                  Предыдущая
                </Button>
                <span className="text-[13px] font-mono text-slate-700">
                  Страница{' '}
                  <span className="font-semibold text-slate-900">{pageNumber}</span>
                  {totalPages > 0 && (
                    <span className="text-slate-400"> / {totalPages}</span>
                  )}
                </span>
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
              />
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
}

function BookHeader({ book, pagesCount }: BookHeaderProps) {
  const isArabic = book.language === 'ar';
  return (
    <div className="mb-4">
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
              ? 'mt-2 font-naskh text-[14px] leading-relaxed text-slate-600'
              : 'mt-2 text-[13px] leading-relaxed text-slate-600'
          }
          dir={isArabic ? 'rtl' : 'ltr'}
        >
          {book.description}
        </p>
      )}
    </div>
  );
}

interface ChapterListProps {
  nodes: ReadonlyArray<ChapterTreeNode>;
  depth: number;
}

function ChapterList({ nodes, depth }: ChapterListProps) {
  return (
    <ul className={depth === 0 ? 'space-y-0.5' : 'mt-0.5 space-y-0.5'}>
      {nodes.map((n) => {
        const isArabic = isArabicText(n.title);
        const indent = depth * 12;
        return (
          <li key={n.id}>
            <div
              className={
                isArabic
                  ? 'rounded px-2 py-1 font-naskh text-[13px] leading-snug text-slate-700 hover:bg-slate-50'
                  : 'rounded px-2 py-1 text-[12px] leading-snug text-slate-700 hover:bg-slate-50'
              }
              style={{ paddingLeft: `${indent + 8}px` }}
              dir={isArabic ? 'rtl' : 'ltr'}
              title={n.title ?? ''}
            >
              {n.title ?? '(без названия)'}
            </div>
            {n.children.length > 0 && (
              <ChapterList nodes={n.children} depth={depth + 1} />
            )}
          </li>
        );
      })}
    </ul>
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
          // shamela page.content - HTML с тэгами (<p>, <br>, и т.п.).
          // На MVP рендерим как-есть. TODO: подключить DOMPurify когда
          // выйдем за рамки доверенного источника shamela (см. gotchas).
          // .book-content класс из index.css восстанавливает margin'ы
          // у <p>/<blockquote>/<br> которые сбрасывает Tailwind preflight
          dangerouslySetInnerHTML={{ __html: text }}
        />
      )}
    </Card>
  );
}

export default BookReaderPage;
