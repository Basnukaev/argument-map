import { useCallback, useEffect, useRef, useState } from 'react';
import { AlertCircle, FileImage, Loader2 } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import type { components } from '@/shared/api/types';
import { isArabicText, sanitizePageHtml } from '@/shared/components/reader/utils';
import {
  applyHighlight,
  computeRangeOffsets,
  removeHighlights,
} from '@/shared/components/reader/textRangeUtils';
import RichTextRenderer from '@/shared/components/editor/RichTextRenderer';
import { useT } from '@/shared/i18n';
import { HadithBox } from '@/shared/components/editor/extensions/HadithBox';
import { AyahBox } from '@/shared/components/editor/extensions/AyahBox';
import { Marginalia } from '@/shared/components/editor/extensions/Marginalia';
import { Footnote } from '@/shared/components/editor/extensions/Footnote';
import { ColorHighlight } from '@/shared/components/editor/extensions/ColorHighlight';
import { DecoratedHeading } from '@/shared/components/editor/extensions/DecoratedHeading';
import { PageNumber } from '@/shared/components/editor/extensions/PageNumber';

// Custom Tiptap extensions для read-only render. Список должен совпадать
// с extensions в AdminPageEditorPage - иначе пользовательский HadithBox
// упадёт на «unknown node type»: HadithBox / AyahBox / Marginalia /
// Footnote / ColorHighlight / DecoratedHeading / PageNumber
const READER_EXTENSIONS = [
  HadithBox,
  AyahBox,
  Marginalia,
  Footnote,
  ColorHighlight,
  DecoratedHeading,
  PageNumber,
];

// Source-first поля (миграция 19, ADR-021) - в runtime есть, но types.ts
// регенерируется отдельно. Intersection даёт безопасный доступ.
// formattedContent (миграция 33, ADR-039) тоже appended до regenerate-api -
// ProseMirror JSON либо null для legacy страниц
export type PageDetail = components['schemas']['PageResponse'] & {
  printedPage?: string | null;
  part?: string | null;
  pdfPageNumber?: number | null;
  formattedContent?: object | null;
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
  const t = useT();
  const contentRef = useRef<HTMLElement>(null);
  const pageId = state.kind === 'success' ? (state.page.id ?? null) : null;

  // Флаг готовности Tiptap DOM: false пока useEditor не инициализировался.
  // Сбрасывается при смене pageId (новая страница = новый контент = новый editor).
  // Нужен чтобы highlight-эффект не стрелял раньше чем text nodes в DOM есть.
  const [richTextReady, setRichTextReady] = useState(false);
  // Сброс при смене страницы — новый контент загружает новый editor
  const prevPageIdRef = useRef<string | null>(null);
  if (prevPageIdRef.current !== pageId) {
    prevPageIdRef.current = pageId;
    // Сброс только если был уже ready (избегаем двойного setState на mount)
    if (richTextReady) setRichTextReady(false);
  }

  const onRichTextReady = useCallback(() => {
    setRichTextReady(true);
  }, []);

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

    // Для formattedContent (Tiptap path): ждём сигнала richTextReady,
    // иначе text nodes ещё не в DOM и applyHighlight ничего не найдёт.
    // Для legacy dangerouslySetInnerHTML path: richTextReady всегда false,
    // но там DOM синхронный — достаточно pageId как сигнала готовности.
    const hasFormattedContent =
      state.kind === 'success' && state.page.formattedContent != null;
    if (hasFormattedContent && !richTextReady) return;

    removeHighlights(container);
    if (!highlightRange) return;
    applyHighlight(container, highlightRange[0], highlightRange[1]);
    const mark = container.querySelector('mark.citation-highlight');
    mark?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [highlightRange, pageId, richTextReady, state]);

  if (state.kind === 'loading') {
    return (
      <Card className="p-12 text-center">
        <Loader2 size={20} className="mx-auto animate-spin text-ink-400" aria-hidden="true" />
        <p className="mt-2 text-xs text-ink-500">{t('reader.page_loading')}</p>
      </Card>
    );
  }
  if (state.kind === 'error') {
    return (
      <Card className="border-err-500/40 bg-err-100 p-5">
        <div className="flex items-start gap-3">
          <AlertCircle size={20} className="mt-0.5 shrink-0 text-err-700" aria-hidden="true" />
          <p className="text-sm text-err-700">{state.message}</p>
        </div>
      </Card>
    );
  }
  const { page } = state;
  const text = page.textContent ?? '';
  const isArabic = bookLanguage === 'ar' || isArabicText(text);

  // article-классы: базовая типографика для арабского / латиницы
  const articleClass = isArabic
    ? 'book-content font-naskh text-md leading-[2] text-ink-900'
    : 'book-content text-base leading-relaxed text-ink-900';

  return (
    <Card className="relative px-8 pb-8 pt-14">
      <div className="absolute end-4 top-3 inline-flex items-center gap-2">
        {onOpenPdfPreview && (
          <button
            type="button"
            onClick={onOpenPdfPreview}
            className="inline-flex items-center gap-1.5 rounded-md border border-err-500/40 bg-err-100 px-2.5 py-1 text-xs font-semibold text-err-700 shadow-sm transition-colors hover:border-err-500/40 hover:bg-err-100 hover:text-err-700"
            title={t('reader.open_pdf_page_title')}
          >
            <FileImage size={14} aria-hidden="true" />
            <span>PDF</span>
          </button>
        )}
      </div>
      {!text && !page.imageUrl && (
        <p className="text-center text-sm text-ink-400">{t('reader.page_empty')}</p>
      )}
      {page.imageUrl && (
        <img
          src={page.imageUrl}
          alt={`Скан страницы ${page.pageNumber ?? ''}`}
          className="mx-auto mb-4 max-h-[800px] w-auto rounded-md border border-border"
        />
      )}
      {/* ADR-039: если есть formatted_content - рендерим через
          RichTextRenderer (HadithBox + другие custom nodes).
          Иначе fallback на старый sanitized HTML путь.
          Highlight ranges + ЛКМ-selection пока работают только в
          legacy режиме - перенос на ProseMirror selection API
          отдельным этапом */}
      {page.formattedContent ? (
        <article ref={contentRef} className={articleClass} dir={isArabic ? 'rtl' : 'ltr'}>
          <RichTextRenderer
            content={page.formattedContent}
            extensions={READER_EXTENSIONS}
            onReady={onRichTextReady}
          />
        </article>
      ) : (
        text && (
          <article
            ref={contentRef}
            className={articleClass}
            dir={isArabic ? 'rtl' : 'ltr'}
            // Legacy path (NULL formatted_content): sanitize чистит
            // script/style теги перед DOM-injection
            dangerouslySetInnerHTML={{
              __html: sanitizePageHtml(text),
            }}
          />
        )
      )}
    </Card>
  );
}

export default PageView;
