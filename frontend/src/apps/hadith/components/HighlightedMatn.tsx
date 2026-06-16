import { useEffect, useMemo, useRef, useState } from 'react';
import { useT } from '@/shared/i18n';
import type { ExplanationDto } from '@/apps/hadith/types';
import { buildMatnSegments } from '@/apps/hadith/utils/highlightGharib';

interface HighlightedMatnProps {
  /** Hero-матн (`normalizedMatn`) — уже folded бэком, рендерится как есть. */
  matn: string;
  /** GHARIB-толкования секции «غريب» — для подсветки слов матна. */
  gharib: ExplanationDto[];
}

/**
 * Hero-матн с подсветкой гариб-слов. Слова, для которых есть толкование
 * (GHARIB.reference), оборачиваются в кнопку с акцентным подчёркиванием;
 * клик открывает поповер с толкованием (`text`) и словарём (`bookName · author`).
 * Когда гарибов нет или ни одно слово не совпало — рендерится чистый текст
 * (graceful, разметка матна не меняется). RTL, naskh — задаются родителем (h1).
 *
 * Парсинг матна мемоизируется: текст крупный, а подсветка — чистая функция от
 * (matn, gharib).
 */
function HighlightedMatn({ matn, gharib }: HighlightedMatnProps) {
  const segments = useMemo(() => buildMatnSegments(matn, gharib), [matn, gharib]);

  // Без совпадений будет один plain-сегмент — рендерим текст напрямую (нет
  // лишних span'ов).
  return (
    <>
      {segments.map((seg) =>
        seg.gharib ? (
          <GharibWord key={seg.key} word={seg.text} exp={seg.gharib} />
        ) : (
          seg.text
        ),
      )}
    </>
  );
}

/**
 * Подсвеченное гариб-слово: акцентное подчёркивание + click-popover с
 * толкованием. Поповер управляется локальным state'ом; закрытие по
 * click-outside и Escape (паттерн InlineCitationMarker — native popover-атрибут
 * не используем, jsdom его не держит).
 */
function GharibWord({ word, exp }: { word: string; exp: ExplanationDto }) {
  const t = useT();
  const [open, setOpen] = useState(false);
  const wrapperRef = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDocClick = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDocClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDocClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const dict = [exp.bookName, exp.author].filter((p): p is string => Boolean(p)).join(' · ');

  return (
    <span ref={wrapperRef} className="relative inline-block">
      <button
        type="button"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="rounded-sm font-arabic text-accent-700 underline decoration-accent-400 decoration-dotted underline-offset-4 hover:bg-accent-50 hover:decoration-accent-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
      >
        {word}
      </button>
      {open && (
        <span
          role="dialog"
          dir="rtl"
          className="absolute end-0 top-full z-50 mt-1 block w-72 rounded-md border border-border bg-elevated p-3 text-start text-sm leading-relaxed text-ink-700 shadow-sh3 dark:text-ink-200"
          onClick={(e) => e.stopPropagation()}
        >
          {exp.text && (
            <span className="block font-arabic text-base leading-loose text-ink-800 dark:text-ink-100">
              {exp.text}
            </span>
          )}
          {dict && (
            <span className="mt-2 block border-t border-border pt-2 font-arabic text-xs text-ink-500 dark:text-ink-300">
              {dict}
            </span>
          )}
          <button
            type="button"
            onClick={() => setOpen(false)}
            aria-label={t('common.close')}
            className="absolute start-1 top-1 rounded p-1 text-ink-400 hover:bg-ink-100 hover:text-ink-700 dark:hover:bg-ink-800 dark:hover:text-ink-200"
          >
            <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
              <path d="M2 2 L10 10 M10 2 L2 10" stroke="currentColor" strokeWidth="1.5" fill="none" />
            </svg>
          </button>
        </span>
      )}
    </span>
  );
}

export default HighlightedMatn;
