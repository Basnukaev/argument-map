import { useState } from 'react';
import { isArabicText } from '@/apps/library/utils/bookReaderUtils';

interface Props {
  currentPage: number;
  totalPages: number;
  currentPrintedPage: string | null;
  currentPart: string | null;
  onJump: (page: number) => void;
}

/**
 * Input + go-button для прямого перехода к internal pageNumber.
 *
 * Source-first label (ADR-021): рядом с input показывается оригинальный
 * маркер «стр {printedPage} том {part}». Internal pageNumber оставлен
 * для navigation - меняем только display.
 *
 * Эвристика на dir: если part содержит арабские символы, рендерим label
 * в RTL чтобы знаки препинания и порядок слов сохранялись.
 *
 * Синхронизация draft с внешним currentPage - через key-prop в родителе
 * (PageJump remount'ится с новым initial state). Идиома проекта (см.
 * memory feedback_react_key_remount) вместо useEffect-сброса который
 * ловит react-hooks/set-state-in-effect.
 */
function PageJump({ currentPage, totalPages, currentPrintedPage, currentPart, onJump }: Props) {
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
        {totalPages > 0 && <span className="font-mono text-slate-400">/ {totalPages}</span>}
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

export default PageJump;
