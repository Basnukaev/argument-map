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
 * данные приходят из detail endpoint, локализация не нужна (текст уже на
 * языке курирования). Пустой список → ничего не рендерит.
 */
function HadithGradesList({ grades }: Props) {
  const t = useT();
  if (grades.length === 0) return null;
  return (
    <section className="mb-8">
      <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-ink-500">
        {t('hadith.detail.grades')} · {grades.length}
      </h2>
      <ul className="flex flex-wrap gap-2">
        {grades.map((g) => (
          <li key={`${g.scholar ?? '?'}:${g.grade ?? '?'}`} className="max-w-xs">
            <div className="rounded-md border border-border-strong bg-elevated px-3 py-2">
              <div className="flex items-center gap-2">
                {g.grade && (
                  <span className={`rounded-sm px-1.5 py-0.5 text-xs font-semibold ${gradeChip(g.grade)}`}>
                    {g.grade}
                  </span>
                )}
                {g.scholar && (
                  <span className="text-sm font-medium text-ink-900" dir="auto">
                    {g.scholar}
                  </span>
                )}
              </div>
              {g.note && (
                <p className="mt-1 text-xs leading-snug text-ink-500" dir="auto">
                  {g.note}
                </p>
              )}
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default HadithGradesList;
