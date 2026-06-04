import { parseIsnadHtml } from '@/apps/hadith/utils/parseIsnadHtml';
import type { NarratorData } from '@/apps/hadith/types';

interface IsnadTextProps {
  /** Сырой `fullTextAr` хадиса (alminasa-HTML). */
  html: string;
  /**
   * Карта externalId → NarratorData из загруженного графа. Пока граф
   * грузится (карта null) — рави НЕ-кликабельны (guard). Клик резолвится
   * из этой карты БЕЗ доп. фетча.
   */
  narratorByExternalId: Map<string, NarratorData> | null;
  /** Клик по кликабельному рави → открыть панель страницы. */
  onNarratorClick: (data: NarratorData) => void;
}

/**
 * Безопасный рендер текста иснада: токенизация через parseIsnadHtml
 * (НЕ dangerouslySetInnerHTML). rawy-сегменты кликабельны ТОЛЬКО когда
 * граф загружен и externalId есть в карте; matn — стилевое выделение
 * (не кликабельно); остальное — plain-текст. RTL.
 */
function IsnadText({ html, narratorByExternalId, onNarratorClick }: IsnadTextProps) {
  const segments = parseIsnadHtml(html);
  if (segments.length === 0) return null;

  return (
    <p className="font-arabic text-lg leading-loose text-ink-800" dir="rtl">
      {segments.map((seg, i) => {
        if (seg.kind === 'matn') {
          return (
            // matn — стилевое выделение текста матна (тонкий фон, не кликабельно).
            <span key={i} className="rounded-sm bg-accent-50 px-0.5 text-ink-900">
              {seg.text}
            </span>
          );
        }
        if (seg.kind === 'rawy') {
          const resolved =
            seg.externalId != null ? narratorByExternalId?.get(seg.externalId) : undefined;
          // Кликабельно только при загруженном графе + наличии рави в карте.
          if (resolved) {
            return (
              <button
                key={i}
                type="button"
                onClick={() => onNarratorClick(resolved)}
                className="rounded-sm font-semibold text-accent-700 underline decoration-accent-300 decoration-dotted underline-offset-4 hover:bg-accent-50 hover:decoration-accent-500 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
              >
                {seg.text}
              </button>
            );
          }
          // Stub-рави / граф ещё грузится → акцент без интерактивности.
          return (
            <span key={i} className="font-semibold text-ink-700">
              {seg.text}
            </span>
          );
        }
        return <span key={i}>{seg.text}</span>;
      })}
    </p>
  );
}

export default IsnadText;
