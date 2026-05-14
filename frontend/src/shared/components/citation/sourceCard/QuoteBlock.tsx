import { hasArabicScript, useT } from '@/shared/i18n';

type Props = {
  part: string | null;
  page: string | null;
  quote: string | null;
  context: string | null;
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
export function QuoteBlock({ part, page, quote, context }: Props) {
  const t = useT();
  const quoteIsAr = quote ? hasArabicScript(quote) : false;
  const partIsAr = part ? hasArabicScript(part) : false;
  return (
    <div className="mt-1.5 rounded-xl border-s-[3px] border-indigo-500 bg-indigo-50/40 px-3.5 py-3">
      <div className="mb-2 flex items-baseline justify-between gap-2 text-[11px] text-slate-400">
        <span dir="ltr">
          {t('cite.page.short')}{' '}
          <span className="font-semibold text-slate-700">{page ?? '—'}</span>
        </span>
        {part && (
          <span
            dir={partIsAr ? 'rtl' : 'ltr'}
            lang={partIsAr ? 'ar' : undefined}
            className={
              partIsAr
                ? 'font-naskh text-[13px] text-slate-600'
                : 'text-[11px] text-slate-600'
            }
          >
            {part}
          </span>
        )}
      </div>

      {quote && (
        <div
          dir="auto"
          lang={quoteIsAr ? 'ar' : undefined}
          className={
            quoteIsAr
              ? 'font-naskh text-[19px] font-medium leading-[1.95] text-slate-900 text-start'
              : 'text-[15px] leading-[1.6] text-slate-900 text-start'
          }
        >
          {quote}
        </div>
      )}

      {context && (
        <div
          dir="auto"
          className="mt-2.5 border-t border-dashed border-slate-200 pt-2 text-xs italic text-slate-400 text-start"
        >
          «{context}»
        </div>
      )}
    </div>
  );
}
