import { EyeOff } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import { useT } from '@/shared/i18n';
import HideToggle from '@/apps/hadith/components/curation/HideToggle';
import type { NarratorCommentaryDto } from '@/apps/hadith/types';

/**
 * Карточка оценки учёного о передатчике (джарх/таʿдиль). Шапка — критик
 * (`commenter`, RTL) + год смерти; тело — список вердиктов (`comments[]`, RTL);
 * мета-строка — книга · автор · том/страница (атрибуция из риджаль-книги).
 */
function NarratorCommentaryItem({
  commentary,
  role,
  onChanged,
}: {
  commentary: NarratorCommentaryDto;
  /** Роль зрителя — гейт ADMIN record-hide (курация 4.b). */
  role: string | undefined;
  /** Рефетч bio после скрытия/показа записи. */
  onChanged: () => void;
}) {
  const t = useT();
  const cite = [
    commentary.bookName,
    commentary.author,
    commentary.volume != null ? `${t('hadith.matn.vol')}${commentary.volume}` : null,
    commentary.page != null ? `${t('hadith.matn.page')}${commentary.page}` : null,
  ]
    .filter((p): p is string => Boolean(p))
    .join(' · ');

  return (
    <Card className={`p-4 ${commentary.hiddenByAdmin ? 'opacity-50' : ''}`}>
      {/* ADMIN record-hide (курация 4.b): пилюля причины + тогл показать/скрыть. */}
      {(commentary.hiddenByAdmin || role) && (
        <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
          {commentary.hiddenByAdmin ? (
            <div className="inline-flex items-center gap-1.5 rounded-sm bg-rose-50 px-1.5 py-0.5 text-xs text-rose-700">
              <EyeOff size={12} aria-hidden />
              <span dir="auto">
                {t('hadith.curation.hidden_by_admin')}
                {commentary.hideReason ? `: ${commentary.hideReason}` : ''}
              </span>
            </div>
          ) : (
            <span />
          )}
          <HideToggle
            entityTable="hd_narrator_commentaries"
            entityId={commentary.id}
            hiddenByAdmin={commentary.hiddenByAdmin}
            hideReason={commentary.hideReason}
            role={role}
            onChanged={onChanged}
          />
        </div>
      )}
      <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
        <span className="font-arabic text-base font-semibold text-ink-900" dir="rtl">
          {commentary.commenter}
        </span>
        {commentary.commenterDeathYear != null && (
          <span className="text-xs text-ink-500">
            {t('hadith.narrator.commentaries.died').replace(
              '{year}',
              String(commentary.commenterDeathYear),
            )}
          </span>
        )}
      </div>

      {commentary.comments.length > 0 && (
        <ul className="mt-2 space-y-1.5">
          {commentary.comments.map((verdict, i) => (
            // У вердикта нет id с бэка; список неизменяемый (detail-снимок) →
            // стабильный ключ из критика + книги + индекса.
            <li
              key={`${commentary.commenter}-${commentary.bookName ?? ''}-${i}`}
              className="font-arabic text-base leading-loose text-ink-800"
              dir="rtl"
            >
              {verdict}
            </li>
          ))}
        </ul>
      )}

      {cite && (
        <div className="mt-2 text-xs text-ink-500" dir="auto">
          {cite}
        </div>
      )}
    </Card>
  );
}

/**
 * Список оценок учёных о передатчике. Порядок с бэка стабилен
 * (commenter_dod asc, book_order); detail-снимок неизменяем.
 */
function NarratorCommentaryList({
  commentaries,
  role,
  onChanged,
}: {
  commentaries: NarratorCommentaryDto[];
  /** Роль зрителя — гейт ADMIN record-hide (курация 4.b). */
  role: string | undefined;
  /** Рефетч bio после скрытия/показа записи. */
  onChanged: () => void;
}) {
  return (
    <ul className="space-y-3">
      {commentaries.map((c) => (
        <li key={c.id}>
          <NarratorCommentaryItem commentary={c} role={role} onChanged={onChanged} />
        </li>
      ))}
    </ul>
  );
}

export default NarratorCommentaryList;
