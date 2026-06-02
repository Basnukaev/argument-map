import { useT } from '@/shared/i18n';
import type { HadithGrade } from '@/apps/hadith/types';

/** Цвет чипа по тексту степени (freeform RU/EN — матчим по подстроке). */
function gradeChip(grade: string | null): string {
  const g = (grade ?? '').toLowerCase();
  if (/сахих|sahih|муттафак/.test(g)) return 'bg-emerald-100 text-emerald-700';
  if (/хасан|hasan/.test(g)) return 'bg-sky-100 text-sky-700';
  if (/даиф|слаб|daif/.test(g)) return 'bg-amber-100 text-amber-700';
  if (/мауду|выдум|maudu|fabric/.test(g)) return 'bg-rose-100 text-rose-700';
  return 'bg-ink-100 text-ink-700';
}

interface Props {
  grades: HadithGrade[];
}

/**
 * Секция «Оценки учёных» — курируемые вердикты (Бухари/Муслим/муттафакун
 * алейхи/аль-Албани) о достоверности хадиса. Презентационный компонент:
 * данные приходят из detail endpoint. Рендерит аккуратную таблицу-список
 * {учёный → степень} вместо «вразнобой» чипов; пустой список → дружелюбный
 * empty-state. Заголовок/обёртку секции владеет страница (единый layout).
 */
function HadithGradesList({ grades }: Props) {
  const t = useT();

  if (grades.length === 0) {
    return (
      <div className="rounded-md border border-dashed border-border-strong bg-elevated/50 p-6 text-center text-sm text-ink-500">
        {t('hadith.detail.grades_empty')}
      </div>
    );
  }

  return (
    <ul className="divide-y divide-border rounded-md border border-border-strong bg-elevated">
      {grades.map((g, i) => (
        <li
          key={`${g.scholar ?? '?'}:${g.grade ?? '?'}:${i}`}
          className="flex flex-wrap items-baseline gap-x-3 gap-y-1 px-4 py-3"
        >
          {g.scholar && (
            <span className="min-w-[8rem] flex-1 text-sm font-medium text-ink-900" dir="auto">
              {g.scholar}
            </span>
          )}
          {g.grade && (
            <span
              className={`shrink-0 rounded-sm px-2 py-0.5 text-xs font-semibold ${gradeChip(g.grade)}`}
              dir="auto"
            >
              {g.grade}
            </span>
          )}
          {g.note && (
            <p className="w-full text-xs leading-snug text-ink-500" dir="auto">
              {g.note}
            </p>
          )}
        </li>
      ))}
    </ul>
  );
}

export default HadithGradesList;
