import Card from '@/shared/components/ui/Card';
import { useT } from '@/shared/i18n';
import type { RulingDto } from '@/apps/hadith/types';

/**
 * Список вердиктов учёных (rulings) на хадис. Каждый — учёный + год смерти
 * + текст вердикта + книга/том/страница. При source='index' с
 * relatedExternalId показываем подпись «на параллельную передачу {id}» —
 * вердикт относится не к этому хадису, а к его сиблингу (фикс M2).
 */
function RulingItem({ ruling }: { ruling: RulingDto }) {
  const t = useT();
  const cite = [
    ruling.bookName,
    ruling.volume != null ? `${t('hadith.matn.vol')}${ruling.volume}` : null,
    ruling.page != null ? `${t('hadith.matn.page')}${ruling.page}` : null,
  ]
    .filter((p): p is string => Boolean(p))
    .join(' · ');

  const onParallel = ruling.source === 'index' && Boolean(ruling.relatedExternalId);

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
        {onParallel && (
          <span className="rounded-sm bg-amber-50 px-1.5 py-0.5 text-amber-700">
            {t('hadith.detail.ruling.on_parallel').replace(
              '{id}',
              ruling.relatedExternalId ?? '',
            )}
          </span>
        )}
      </div>
    </Card>
  );
}

function RulingsList({ rulings }: { rulings: RulingDto[] }) {
  return (
    <ul className="space-y-3">
      {rulings.map((r, i) => (
        // У ruling нет id с бэка; список неизменяемый (detail-снимок) → index ок.
        <li key={`${r.rulerName ?? ''}-${i}`}>
          <RulingItem ruling={r} />
        </li>
      ))}
    </ul>
  );
}

export default RulingsList;
