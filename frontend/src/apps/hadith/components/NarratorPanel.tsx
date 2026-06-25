import { X, ArrowRight } from 'lucide-react';
import { useT, type DictKey } from '@/shared/i18n';
import { hasRoleAtLeast, type AuthRole } from '@/shared/stores/authStore';
import { RELIABILITY_TOKENS } from '@/apps/hadith/sanadTokens';
import EditableField from '@/apps/hadith/components/curation/EditableField';
import CurationFieldsPanel, {
  type CurationFieldSpec,
} from '@/apps/hadith/components/curation/CurationFieldsPanel';
import type { SanadFlowNodeData } from '@/apps/hadith/types';

/** Курация Фаза 5.b — enum-опции ADMIN-правки степени надёжности рави
 *  (зеркало NarratorDetailPage.RELIABILITY_OPTIONS). */
const RELIABILITY_OPTIONS: { value: string; label: string }[] = [
  { value: 'THIQA', label: 'THIQA' },
  { value: 'SADUQ', label: 'SADUQ' },
  { value: 'MAQBUL', label: 'MAQBUL' },
  { value: 'DAIF', label: 'DAIF' },
  { value: 'MATRUK', label: 'MATRUK' },
  { value: 'SAHABI', label: 'SAHABI' },
  { value: 'UNKNOWN', label: 'UNKNOWN' },
];

interface NarratorPanelProps {
  data: SanadFlowNodeData;
  onClose: () => void;
  /**
   * Форма имени рави как она записана в тексте иснада (клик из IsnadText).
   * Показывается muted-строкой «في الإسناد: …» под каноническим именем —
   * снимает путаницу الفاكهي vs الخزاعي. undefined при клике из графа.
   */
  textForm?: string;
  /**
   * Роль пользователя — гейт ADMIN inline-правки полей рави (курация Фаза 5.b).
   * не-ADMIN: панель правки скрыта (карточка read-only как прежде).
   */
  role?: string;
  /**
   * Сохранение поля рави → рефетч графа (родитель пересобирает с EFFECTIVE-
   * значениями). undefined — правка недоступна (нет колбэка рефетча).
   */
  onEdited?: () => void;
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
 * Компактная карточка с биографией передатчика (С64-редизайн: была панель
 * во всю высоту справа — «бандура», стала аккуратная плавающая карточка в
 * верхнем углу). Открывается по ДВОЙНОМУ клику на узел графа иснада.
 * Позиционируется абсолютно внутри контейнера графа (родитель relative);
 * высота по контенту со скроллом, на узких экранах ужимается по ширине.
 */
function NarratorPanel({ data, onClose, textForm, role, onEdited }: NarratorPanelProps) {
  const t = useT();
  const rel = data.reliabilityGrade ? RELIABILITY_TOKENS[data.reliabilityGrade] : null;

  // Курация Фаза 5.b: ADMIN-правка полей рави доступна только при наличии
  // narratorId (синтетический Пророк ﷺ его не имеет) И колбэка рефетча графа.
  const isAdmin = hasRoleAtLeast(role as AuthRole | undefined, 'ADMIN');
  const canEdit = isAdmin && data.narratorId != null && onEdited != null;
  const handleEdited = onEdited ?? (() => {});
  // §5.b: admin-индикатор «отредактировано» — непустой overriddenFields.
  const isEdited = isAdmin && (data.overriddenFields?.length ?? 0) > 0;

  // §5-редактируемые текстовые поля рави (reliability_grade — enum, отдельно).
  const editableTextFields: CurationFieldSpec[] = [
    { label: t('hadith.narrator.kunya'), fieldName: 'kunya', value: data.kunya, kind: 'text' },
    { label: t('hadith.narrator.laqab'), fieldName: 'laqab', value: data.laqab, kind: 'text' },
    { label: t('hadith.narrator.generation'), fieldName: 'tabaqa', value: data.tabaqa, kind: 'text' },
  ];

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
    <aside className="absolute end-3 top-3 z-20 flex max-h-[calc(100%-1.5rem)] w-[330px] max-w-[calc(100%-1.5rem)] flex-col overflow-hidden rounded-xl border border-border-strong bg-elevated shadow-sh3">
      <header className="flex items-start justify-between gap-3 border-b border-border px-4 py-3">
        <div className="min-w-0">
          <div className="flex items-center gap-1.5">
            <span className="font-arabic text-xl leading-tight text-ink-900" dir="rtl">
              {data.nameAr}
            </span>
            {/* §5.b admin-индикатор «отредактировано» — тонкая точка-маркер. */}
            {isEdited && (
              <span
                className="inline-block h-1.5 w-1.5 shrink-0 rounded-full bg-accent-500"
                title={t('hadith.curation.fields_title')}
                aria-label={t('hadith.curation.fields_title')}
              />
            )}
          </div>
          {data.nameRu && <div className="mt-0.5 text-sm text-ink-600">{data.nameRu}</div>}
          {/* Форма имени как в иснаде (клик из текста) — снимает путаницу
              канонического имени vs того, как рави назван в этой цепи. */}
          {textForm && (
            <div className="mt-1 font-arabic text-sm text-ink-400" dir="rtl">
              {t('hadith.narrator.in_isnad')}: {textForm}
            </div>
          )}
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

        {/* Курация Фаза 5.b — ADMIN inline-правка полей рави прямо из графа.
            reliability_grade (enum) отдельной строкой; прочие текстовые поля —
            переиспользуем CurationFieldsPanel (та же сетка, что у сателлитов).
            Save → PUT overrides → onEdited() рефетчит граф (EFFECTIVE-значения). */}
        {canEdit && data.narratorId != null && (
          <>
            <div className="mt-4 border-t border-dashed border-border pt-2">
              <dl className="grid grid-cols-[auto_1fr] items-baseline gap-x-3 gap-y-1 text-xs">
                <dt className="text-ink-500">{t('hadith.graph.legend_reliability')}</dt>
                <dd className="min-w-0 text-ink-700">
                  <EditableField
                    entityTable="hd_narrators"
                    entityId={data.narratorId}
                    fieldName="reliability_grade"
                    value={data.reliabilityGrade}
                    kind="enum"
                    options={RELIABILITY_OPTIONS}
                    role={role}
                    onSaved={handleEdited}
                  />
                </dd>
              </dl>
            </div>
            <CurationFieldsPanel
              entityTable="hd_narrators"
              entityId={data.narratorId}
              fields={editableTextFields}
              role={role}
              onChanged={handleEdited}
            />
          </>
        )}
      </div>
    </aside>
  );
}

export default NarratorPanel;
