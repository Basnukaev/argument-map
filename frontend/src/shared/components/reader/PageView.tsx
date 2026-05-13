import { useEffect, useRef } from 'react';
import { AlertCircle, FileImage, Loader2 } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import type { components } from '@/shared/api/types';
import { isArabicText, sanitizePageHtml } from '@/shared/components/reader/utils';
import {
  applyHighlight,
  computeRangeOffsets,
  removeHighlights,
} from '@/shared/components/reader/textRangeUtils';

// Source-first поля (миграция 19, ADR-021) - в runtime есть, но types.ts
// регенерируется отдельно. Intersection даёт безопасный доступ.
export type PageDetail = components['schemas']['PageResponse'] & {
  printedPage?: string | null;
  part?: string | null;
  pdfPageNumber?: number | null;
};

export type PageContentState =
  | { kind: 'loading' }
  | { kind: 'success'; page: PageDetail }
  | { kind: 'error'; message: string };

/**
 * Текстовое выделение пользователя через ЛКМ-drag. char offsets считаются
 * по plain text (TreeWalker через text nodes, HTML теги не считаются).
 * Используется CitationPicker для построения text-mode citation.
 */
export interface TextSelection {
  pageId: string;
  rangeStart: number;
  rangeEnd: number;
  quote: string;
}

interface Props {
  state: PageContentState;
  bookLanguage: string | undefined;
  /**
   * Callback для открытия inline PDF preview этой страницы. Если undefined -
   * кнопка не рендерится (книга без PDF source).
   */
  onOpenPdfPreview?: () => void;
  /**
   * Включает selection mode - ЛКМ-drag выделяет фрагмент text content,
   * вычисляется char range и передаётся в onSelectionChange. Для
   * CitationPicker.
   */
  selectable?: boolean;
  onSelectionChange?: (sel: TextSelection | null) => void;
  /**
   * Подсветить фрагмент текста через <mark class="citation-highlight">.
   * Используется при открытии BookReader через deep link на citation.
   */
  highlightRange?: [number, number] | null;
}

/**
 * Рендеринг контента страницы: loading/error spinner или HTML контент
 * (через DOMPurify + PUA-strip) + опциональный imageUrl.
 *
 * Если {@code selectable=true}, ЛКМ-drag выделение трекается через
 * window.getSelection и передаётся в {@code onSelectionChange}.
 * Если задан {@code highlightRange}, при mount/update применяется
 * подсветка фрагмента через {@code <mark>}.
 */
function PageView({
  state,
  bookLanguage,
  onOpenPdfPreview,
  selectable = false,
  onSelectionChange,
  highlightRange = null,
}: Props) {
  const contentRef = useRef<HTMLElement>(null);
  const pageId = state.kind === 'success' ? state.page.id : null;

  useEffect(() => {
    if (!selectable || !onSelectionChange || !contentRef.current || !pageId) {
      return;
    }
    const container = contentRef.current;

    const handleMouseUp = () => {
      const sel = window.getSelection();
      if (!sel || sel.rangeCount === 0 || sel.isCollapsed) {
        onSelectionChange(null);
        return;
      }
      const range = sel.getRangeAt(0);
      const offsets = computeRangeOffsets(container, range);
      if (offsets) {
        onSelectionChange({
          pageId,
          rangeStart: offsets.start,
          rangeEnd: offsets.end,
          quote: offsets.quote,
        });
      } else {
        onSelectionChange(null);
      }
    };

    container.addEventListener('mouseup', handleMouseUp);
    return () => container.removeEventListener('mouseup', handleMouseUp);
  }, [selectable, onSelectionChange, pageId]);

  useEffect(() => {
    if (!contentRef.current) return;
    const container = contentRef.current;
    removeHighlights(container);
    if (!highlightRange) return;
    applyHighlight(container, highlightRange[0], highlightRange[1]);
    const mark = container.querySelector('mark.citation-highlight');
    mark?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [highlightRange, pageId]);

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
    <Card className="relative px-8 pb-8 pt-14">
      {onOpenPdfPreview && (
        <button
          type="button"
          onClick={onOpenPdfPreview}
          className="absolute end-4 top-3 inline-flex items-center gap-1.5 rounded-md border border-rose-200 bg-rose-50 px-2.5 py-1 text-[12px] font-semibold text-rose-700 shadow-sm transition-colors hover:border-rose-300 hover:bg-rose-100 hover:text-rose-800"
          title="Открыть PDF оригинала на этой странице"
        >
          <FileImage size={14} aria-hidden="true" />
          <span>PDF</span>
        </button>
      )}
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
          ref={contentRef}
          className={
            isArabic
              ? 'book-content font-naskh text-[19px] leading-[2] text-slate-900'
              : 'book-content text-[15px] leading-relaxed text-slate-900'
          }
          dir={isArabic ? 'rtl' : 'ltr'}
          dangerouslySetInnerHTML={{ __html: sanitizePageHtml(text) }}
        />
      )}
    </Card>
  );
}

export default PageView;
