import { useMemo } from 'react';
import type { ExplanationDto } from '@/apps/hadith/types';
import { buildMatnSegments } from '@/apps/hadith/utils/highlightGharib';
import GharibWord from '@/apps/hadith/components/GharibWord';

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
 * Используется как fallback для legacy-хадисов без `full_text_ar` (огласованного
 * текста с кликабельными рави). Когда `full_text_ar` есть — гариб подсвечивается
 * прямо в огласованном тексте через IsnadText (С7).
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
          <GharibWord
            key={seg.key}
            word={seg.text}
            exp={seg.gharib}
            trailPunct={seg.trailPunct}
          />
        ) : (
          seg.text
        ),
      )}
    </>
  );
}

export default HighlightedMatn;
