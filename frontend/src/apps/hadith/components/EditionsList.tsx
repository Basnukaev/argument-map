import { useT } from '@/shared/i18n';
import type { EditionDto } from '@/apps/hadith/types';

/** Компактный список печатных изданий хадиса (название + том/страница). */
function EditionsList({ editions }: { editions: EditionDto[] }) {
  const t = useT();
  return (
    <ul className="space-y-1.5">
      {editions.map((e, i) => {
        const loc = [
          e.volume != null ? `${t('hadith.matn.vol')}${e.volume}` : null,
          e.page != null ? `${t('hadith.matn.page')}${e.page}` : null,
        ]
          .filter((p): p is string => Boolean(p))
          .join(' · ');
        return (
          // У edition нет id с бэка; список неизменяемый (detail-снимок) → index ок.
          <li
            key={`${e.editionName ?? ''}-${i}`}
            className="flex flex-wrap items-baseline gap-x-2 text-sm text-ink-700"
          >
            {e.editionName && (
              <span className="font-arabic text-ink-800" dir="auto">
                {e.editionName}
              </span>
            )}
            {loc && <span className="text-xs text-ink-400">{loc}</span>}
          </li>
        );
      })}
    </ul>
  );
}

export default EditionsList;
