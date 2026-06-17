import { useEffect, useRef, useState } from 'react';
import { useT } from '@/shared/i18n';
import type { ExplanationDto } from '@/apps/hadith/types';

/**
 * Подсвеченное гариб-слово: акцентное подчёркивание + click-popover с
 * толкованием. Поповер управляется локальным state'ом; закрытие по
 * click-outside и Escape (паттерн InlineCitationMarker — native popover-атрибут
 * не используем, jsdom его не держит).
 *
 * Поповер позиционируется с учётом вьюпорта: по умолчанию раскрывается вниз
 * от слова (top-full), но если места снизу нет — флипает вверх (bottom-full).
 * По горизонтали: по умолчанию выравнивается по концу (end-0); если уходит
 * за левый край — выравнивается по началу (start-0). Внутри — скролл при
 * длинном толковании (max-height + overflow-y: auto).
 *
 * Вынесен из HighlightedMatn в отдельный компонент: переиспользуется и
 * hero-матном (legacy fallback), и текстом иснада (огласованный full_text_ar —
 * гариб подсвечивается на не-рави сегментах, рядом с кликабельными рави).
 */
function GharibWord({
  word,
  exp,
  trailPunct,
}: {
  word: string;
  exp: ExplanationDto;
  trailPunct?: string;
}) {
  const t = useT();
  const [open, setOpen] = useState(false);
  const wrapperRef = useRef<HTMLSpanElement>(null);
  const popoverRef = useRef<HTMLSpanElement>(null);

  // Направление раскрытия поповера: вниз (false) или вверх (true)
  const [flipUp, setFlipUp] = useState(false);
  // Выравнивание по горизонтали: по концу (false) или по началу (true)
  const [flipStart, setFlipStart] = useState(false);

  useEffect(() => {
    if (!open) return;

    // Вычислить направление сразу после открытия
    const measurePosition = () => {
      if (!wrapperRef.current || !popoverRef.current) return;
      const wRect = wrapperRef.current.getBoundingClientRect();
      const pRect = popoverRef.current.getBoundingClientRect();
      const vp = { h: window.innerHeight, w: window.innerWidth };

      // Флип вверх если поповер выходит за нижний край вьюпорта
      setFlipUp(wRect.bottom + pRect.height > vp.h - 8);

      // Флип к началу если поповер выходит за левый край вьюпорта при end-0
      // (в RTL end-0 = правый край обёртки; поповер уходит влево)
      // Проверяем: если левый край поповера < 8px
      setFlipStart(pRect.left < 8);
    };

    // Два RAF — чтобы поповер успел отрендериться в DOM до замера
    const raf1 = requestAnimationFrame(() => {
      requestAnimationFrame(measurePosition);
    });

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
      cancelAnimationFrame(raf1);
      document.removeEventListener('mousedown', onDocClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const dict = [exp.bookName, exp.author].filter((p): p is string => Boolean(p)).join(' · ');

  // Позиционирование: вертикаль (вниз/вверх), горизонталь (конец/начало)
  const vertClass = flipUp ? 'bottom-full mb-1' : 'top-full mt-1';
  const horizClass = flipStart ? 'start-0' : 'end-0';

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
      {trailPunct}
      {open && (
        <span
          ref={popoverRef}
          role="dialog"
          dir="rtl"
          className={`absolute ${vertClass} ${horizClass} z-50 block w-72 rounded-md border border-border bg-elevated p-3 text-start text-sm leading-relaxed shadow-sh3`}
          style={{ maxHeight: 'min(70vh, 20rem)', overflowY: 'auto' }}
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

export default GharibWord;
