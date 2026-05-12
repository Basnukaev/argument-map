import { AlertCircle, FileImage, Loader2 } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import type { components } from '@/shared/api/types';
import { isArabicText, sanitizePageHtml } from '@/apps/library/utils/bookReaderUtils';

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

interface Props {
  state: PageContentState;
  bookLanguage: string | undefined;
  /**
   * Callback для открытия inline PDF preview этой страницы. Если undefined -
   * кнопка не рендерится (книга без PDF source). Юзер увидит PDF в bottom
   * sheet / модалке, не уходя со страницы text mode.
   */
  onOpenPdfPreview?: () => void;
}

/**
 * Рендеринг контента страницы: loading/error spinner или HTML контент
 * (через DOMPurify + PUA-strip) + опциональный imageUrl.
 */
function PageView({ state, bookLanguage, onOpenPdfPreview }: Props) {
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
      {/* Inline PDF preview trigger - shamela-like иконка для открытия
          PDF этой страницы в bottom-sheet. Только если книга имеет PDF
          source (родитель пробрасывает onOpenPdfPreview). Rose accent -
          стилизация под PDF (тёмно-красные icons в PDF reader'ах) +
          визуальное выделение на фоне slate Card */}
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
          className={
            isArabic
              ? 'book-content font-naskh text-[19px] leading-[2] text-slate-900'
              : 'book-content text-[15px] leading-relaxed text-slate-900'
          }
          dir={isArabic ? 'rtl' : 'ltr'}
          // sanitizePageHtml убирает PUA-маркеры фирменного шрифта MUSHAF +
          // прогоняет через DOMPurify (защита от XSS). .book-content в
          // index.css даёт white-space: pre-line + стилизует [data-type="title"]
          dangerouslySetInnerHTML={{ __html: sanitizePageHtml(text) }}
        />
      )}
    </Card>
  );
}

export default PageView;
