import { X, ArrowRight } from 'lucide-react';
import { useT, type DictKey } from '@/shared/i18n';
import { RELIABILITY_TOKENS } from '@/apps/hadith/sanadTokens';
import type { SanadFlowNodeData } from '@/apps/hadith/types';

interface NarratorPanelProps {
  data: SanadFlowNodeData;
  onClose: () => void;
}

function Field({ label, value }: { label: string; value: string | null }) {
  if (!value) return null;
  return (
    <div>
      <dt className="text-[11px] uppercase tracking-wider text-ink-400">{label}</dt>
      <dd className="mt-0.5 text-sm text-ink-800">{value}</dd>
    </div>
  );
}

/**
 * Боковая панель с биографией передатчика. Открывается по клику на узел
 * графа иснада. Абсолютно позиционируется внутри контейнера графа
 * (родитель должен быть relative); на mobile занимает всю ширину.
 */
function NarratorPanel({ data, onClose }: NarratorPanelProps) {
  const t = useT();
  const rel = data.reliabilityGrade ? RELIABILITY_TOKENS[data.reliabilityGrade] : null;

  const lifePath = [data.birthplace, data.primaryResidence, data.deathPlace]
    .filter((p): p is string => Boolean(p));
  // Убираем подряд идущие дубликаты (родился=жил=умер в одном городе).
  const uniquePath = lifePath.filter((p, i) => i === 0 || p !== lifePath[i - 1]);

  const years =
    data.yearBirthHijri != null && data.yearDeathHijri != null
      ? `${data.yearBirthHijri}–${data.yearDeathHijri} ${t('hadith.graph.hijri')}`
      : data.yearDeathHijri != null
        ? `${t('hadith.graph.died')} ${data.yearDeathHijri} ${t('hadith.graph.hijri')}`
        : null;

  return (
    <aside className="absolute inset-y-0 end-0 z-20 flex w-full max-w-sm flex-col border-s border-border-strong bg-elevated shadow-sh3">
      <header className="flex items-start justify-between gap-3 border-b border-border px-4 py-3">
        <div className="min-w-0">
          <div className="font-arabic text-2xl leading-tight text-ink-900" dir="rtl">
            {data.nameAr}
          </div>
          {data.nameRu && <div className="mt-0.5 text-sm text-ink-600">{data.nameRu}</div>}
        </div>
        <button
          type="button"
          onClick={onClose}
          className="shrink-0 rounded-sm p-1 text-ink-500 hover:bg-ink-100 hover:text-ink-900"
          aria-label={t('common.close')}
        >
          <X size={18} aria-hidden />
        </button>
      </header>

      <div className="flex-1 overflow-y-auto px-4 py-4">
        {rel && data.reliabilityGrade && (
          <div className="mb-4 flex items-center gap-2">
            <span className={`rounded-sm px-2 py-0.5 font-arabic text-sm font-semibold ${rel.chip}`} dir="rtl">
              {rel.ar}
            </span>
            <span className="text-sm font-medium text-ink-700">
              {t(`hadith.reliability.${data.reliabilityGrade}` as DictKey)}
            </span>
          </div>
        )}

        <dl className="grid grid-cols-2 gap-x-4 gap-y-3">
          <Field label={t('hadith.narrator.kunya')} value={data.kunya} />
          <Field label={t('hadith.narrator.laqab')} value={data.laqab} />
          {/* M3: у alminasa-рави generation=null, поколение живёт в tabaqa. */}
          <Field
            label={t('hadith.narrator.generation')}
            value={data.tabaqa ?? data.generation}
          />
          <Field label={t('hadith.narrator.years')} value={years} />
        </dl>

        {uniquePath.length > 0 && (
          <div className="mt-4">
            <div className="text-[11px] uppercase tracking-wider text-ink-400">
              {t('hadith.narrator.life_path')}
            </div>
            <div className="mt-1 flex flex-wrap items-center gap-1.5 text-sm text-ink-800">
              {uniquePath.map((place, i) => (
                <span key={place} className="inline-flex items-center gap-1.5">
                  {i > 0 && <ArrowRight size={12} className="text-ink-400" aria-hidden />}
                  {place}
                </span>
              ))}
            </div>
          </div>
        )}

        {/* M3: verbatim джарх — gradeText (alminasa), фолбэк reliabilityComment. */}
        {(data.gradeText ?? data.reliabilityComment) && (
          <div
            className="mt-4 rounded-md bg-sunken p-3 text-sm leading-relaxed text-ink-700"
            dir="auto"
          >
            {data.gradeText ?? data.reliabilityComment}
          </div>
        )}
      </div>
    </aside>
  );
}

export default NarratorPanel;
