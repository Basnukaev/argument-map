import { useT, hasArabicScript, type DictKey } from '@/shared/i18n';
import type { HadithGrade, HadithGradeValue } from '@/apps/hadith/types';

/** Цвет чипа по enum-оценке (ADR-062: grade ∈ SAHIH/HASAN/DAIF/MAUDU). */
function gradeChip(grade: HadithGradeValue): string {
  switch (grade) {
    case 'SAHIH':
      return 'bg-emerald-100 text-emerald-700';
    case 'HASAN':
      return 'bg-sky-100 text-sky-700';
    case 'DAIF':
      return 'bg-amber-100 text-amber-700';
    case 'MAUDU':
      return 'bg-rose-100 text-rose-700';
    default:
      return 'bg-ink-100 text-ink-700';
  }
}

interface Props {
  grades: HadithGrade[];
}

/**
 * Секция «Оценки учёных» — ручные вердикты учёных (ADR-062 Option B) о
 * достоверности хадиса из `hadith_grades`. Презентационный компонент: данные
 * приходят из detail endpoint в структурной форме (authority-FK + enum-grade).
 * Заголовок/обёртку секции и кнопку «Добавить» владеет страница.
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
      {grades.map((g) => {
        const scholar = g.scholarName ?? g.scholarFullName ?? '';
        return (
          <li
            key={g.gradeId}
            className="flex flex-wrap items-baseline gap-x-3 gap-y-1 px-4 py-3"
          >
            {scholar && (
              <span
                className={`min-w-[8rem] flex-1 text-sm font-medium text-ink-900 ${
                  hasArabicScript(scholar) ? 'font-arabic' : ''
                }`}
                dir="auto"
              >
                {scholar}
                {g.scholarDeathYearHijri != null && (
                  <span className="ms-1.5 text-xs font-normal text-ink-400">
                    {t('hadith.detail.ruling.died').replace(
                      '{year}',
                      String(g.scholarDeathYearHijri),
                    )}
                  </span>
                )}
              </span>
            )}
            <span
              className={`shrink-0 rounded-sm px-2 py-0.5 text-xs font-semibold ${gradeChip(g.grade)}`}
            >
              {t(`hadith.grade.value.${g.grade}` as DictKey)}
            </span>
            {g.gradeCitation && (
              <p className="w-full text-xs leading-snug text-ink-500" dir="auto">
                {g.gradeCitation}
              </p>
            )}
            {g.note && (
              <p className="w-full text-xs leading-snug text-ink-500" dir="auto">
                {g.note}
              </p>
            )}
          </li>
        );
      })}
    </ul>
  );
}

export default HadithGradesList;
