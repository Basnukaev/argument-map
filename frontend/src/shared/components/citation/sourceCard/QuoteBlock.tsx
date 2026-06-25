import { hasArabicScript, useT } from '@/shared/i18n';

type Props = {
  part: string | null;
  page: string | null;
  quote: string | null;
  context: string | null;
  /** PDF-том («Том N»). Для PDF/PDF_LINK-цитат, где LocationRef пуст. */
  volume?: string | null;
  /** PDF_LINK-цитата с выделенной областью (bbox) — показываем чип «область». */
  hasRegion?: boolean;
};

/**
 * Always-visible body of source card.
 *
 * Quote сам использует `dir="auto"` + `text-align: start` → выравнивается
 * по первому strong character:
 *   - Arabic quote   → resolves to rtl → reads/aligns right
 *   - Russian quote  → resolves to ltr → reads/aligns left
 *   - English quote  → resolves to ltr → reads/aligns left
 *
 * Локатор (стр. N / part) держит page с одной стороны, part с другой
 * независимо от direction карточки
 */
export function QuoteBlock({ part, page, quote, context, volume, hasRegion }: Props) {
  const t = useT();
  const quoteIsAr = quote ? hasArabicScript(quote) : false;
  const partIsAr = part ? hasArabicScript(part) : false;
  // PDF-локатор (PDF/PDF_LINK): LocationRef пуст, том берём из PdfRef. Если
  // задан текстовый `part` (shamela TEXT), он приоритетнее — `volume` для
  // FILE_ONLY книг без shamela part-метки.
  const partLabel = part ?? (volume != null ? `${t('cite.volume.short')} ${volume}` : null);
  const partLabelIsAr = part ? partIsAr : false;
  return (
    <div className="mt-1.5 rounded-md border border-border bg-paper px-3.5 py-3">
      <div className="mb-2 flex items-baseline justify-between gap-2 text-xs text-ink-400">
        <span dir="ltr" className="inline-flex items-baseline gap-1.5">
          <span>
            {t('cite.page.short')}{' '}
            <span className="font-semibold text-ink-700">{page ?? '—'}</span>
          </span>
          {hasRegion && (
            <span className="inline-flex items-center gap-0.5 rounded-sm bg-ink-100 px-1 text-ink-600">
              <span aria-hidden>▢</span>
              {t('cite.region.label')}
            </span>
          )}
        </span>
        {partLabel && (
          <span
            dir={partLabelIsAr ? 'rtl' : 'ltr'}
            lang={partLabelIsAr ? 'ar' : undefined}
            className={
              partLabelIsAr
                ? 'font-naskh text-sm text-ink-600'
                : 'text-xs text-ink-600'
            }
          >
            {partLabel}
          </span>
        )}
      </div>

      {quote && (
        <div
          dir="auto"
          lang={quoteIsAr ? 'ar' : undefined}
          className={
            quoteIsAr
              ? 'font-naskh text-md font-medium leading-[1.95] text-ink-900 text-start'
              : 'text-base leading-[1.6] text-ink-900 text-start'
          }
        >
          {quote}
        </div>
      )}

      {context && (
        <div
          dir="auto"
          className="mt-2.5 border-t border-dashed border-border pt-2 text-xs italic text-ink-400 text-start"
        >
          «{context}»
        </div>
      )}
    </div>
  );
}
