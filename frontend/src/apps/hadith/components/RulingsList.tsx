import { Link } from 'react-router';
import { ArrowRight } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import { useT } from '@/shared/i18n';
import type { RulingDto } from '@/apps/hadith/types';

/**
 * Список вердиктов учёных (rulings) на хадис. Каждый — учёный + год смерти
 * + текст вердикта + книга/том/страница. Бейдж параллельной передачи:
 *  - вердикт на эту же запись (relatedExternalId === externalId страницы) →
 *    бейдж НЕ показываем;
 *  - resolved (relatedHadithId) → бейдж-ссылка на detail сиблинга;
 *  - иначе → текстовый бейдж с именем сборника (или внешним id).
 */
function RulingItem({ ruling, hadithExternalId }: { ruling: RulingDto; hadithExternalId: string | null }) {
  const t = useT();
  const cite = [
    ruling.bookName,
    ruling.volume != null ? `${t('hadith.matn.vol')}${ruling.volume}` : null,
    ruling.page != null ? `${t('hadith.matn.page')}${ruling.page}` : null,
  ]
    .filter((p): p is string => Boolean(p))
    .join(' · ');

  // Вердикт на эту же запись (своя alminasa-id) — бейдж параллели не нужен.
  const selfRuling =
    ruling.relatedExternalId != null && ruling.relatedExternalId === hadithExternalId;
  // Параллель: есть relatedExternalId, и это НЕ своя запись.
  const onParallel = ruling.relatedExternalId != null && !selfRuling;
  const label = ruling.relatedCollectionNameRu ?? ruling.relatedExternalId ?? '';

  return (
    <Card className="p-4">
      <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
        {ruling.rulerName && (
          <span className="font-arabic text-base font-semibold text-ink-900" dir="rtl">
            {ruling.rulerName}
          </span>
        )}
        {ruling.rulerDeathYear != null && (
          <span className="text-xs text-ink-500">
            {t('hadith.detail.ruling.died').replace('{year}', String(ruling.rulerDeathYear))}
          </span>
        )}
      </div>

      {ruling.rulingText && (
        <p className="mt-2 text-sm leading-relaxed text-ink-800" dir="auto">
          {ruling.rulingText}
        </p>
      )}

      <div className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-ink-500">
        {cite && (
          <span dir="auto" className="text-ink-600">
            {cite}
          </span>
        )}
        {onParallel && ruling.relatedHadithId && (
          // resolved → бейдж-ссылка на detail параллельной передачи
          <Link
            to={`/hadith/hadiths/${ruling.relatedHadithId}`}
            className="inline-flex items-center gap-1 rounded-sm bg-amber-50 px-1.5 py-0.5 text-amber-700 hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
            dir="auto"
          >
            <ArrowRight size={12} aria-hidden />
            <span>{label}</span>
            {ruling.relatedExternalId && (
              <span className="font-mono text-[11px] text-amber-600">
                {ruling.relatedExternalId}
              </span>
            )}
          </Link>
        )}
        {onParallel && !ruling.relatedHadithId && (
          // unresolved → текстовый бейдж с именем сборника / внешним id
          <span
            className="inline-flex items-center gap-1 rounded-sm bg-amber-50 px-1.5 py-0.5 text-amber-700"
            dir="auto"
          >
            <span>{t('hadith.detail.ruling.on_parallel').replace('{id}', label)}</span>
          </span>
        )}
      </div>
    </Card>
  );
}

function RulingsList({
  rulings,
  hadithExternalId,
}: {
  rulings: RulingDto[];
  /** Своя alminasa-id хадиса страницы — для скрытия self-вердикт-бейджа. */
  hadithExternalId: string | null;
}) {
  return (
    <ul className="space-y-3">
      {rulings.map((r, i) => (
        // У ruling нет id с бэка; список неизменяемый (detail-снимок) → index ок.
        <li key={`${r.rulerName ?? ''}-${i}`}>
          <RulingItem ruling={r} hadithExternalId={hadithExternalId} />
        </li>
      ))}
    </ul>
  );
}

export default RulingsList;
