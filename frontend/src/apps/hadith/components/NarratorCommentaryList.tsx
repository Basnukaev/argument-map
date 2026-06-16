import Card from '@/shared/components/ui/Card';
import { useT } from '@/shared/i18n';
import type { NarratorCommentaryDto } from '@/apps/hadith/types';

/**
 * Карточка оценки учёного о передатчике (джарх/таʿдиль). Шапка — критик
 * (`commenter`, RTL) + год смерти; тело — список вердиктов (`comments[]`, RTL);
 * мета-строка — книга · автор · том/страница (атрибуция из риджаль-книги).
 */
function NarratorCommentaryItem({ commentary }: { commentary: NarratorCommentaryDto }) {
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
    <Card className="p-4">
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
function NarratorCommentaryList({ commentaries }: { commentaries: NarratorCommentaryDto[] }) {
  return (
    <ul className="space-y-3">
      {commentaries.map((c, i) => (
        // У цитаты нет id с бэка; стабильный ключ — критик + книга + индекс.
        <li key={`${c.commenter}-${c.bookName ?? ''}-${i}`}>
          <NarratorCommentaryItem commentary={c} />
        </li>
      ))}
    </ul>
  );
}

export default NarratorCommentaryList;
