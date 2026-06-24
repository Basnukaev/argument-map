import { Pencil } from 'lucide-react';
import EditableField from '@/apps/hadith/components/curation/EditableField';
import { useT } from '@/shared/i18n';
import { hasRoleAtLeast, type AuthRole } from '@/shared/stores/authStore';

/** Тип значения одного редактируемого поля (зеркало FieldKind в EditableField). */
type FieldKind = 'text' | 'number';

/** Одно §5-редактируемое поле сателлита: подпись + имя колонки в БД + значение. */
export interface CurationFieldSpec {
  /** Готовая i18n-подпись поля (родитель уже прогнал через t()). */
  label: string;
  /** Имя колонки в БД (entityTable.fieldName) — уходит в PUT overrides. */
  fieldName: string;
  value: string | number | null;
  kind: FieldKind;
}

/**
 * Курация Фаза 5 — ADMIN-панель инлайн-правки §5-полей сателлита (rulings /
 * explanations / commentaries / matns). Чисто аддитивна: не-ADMIN видит null
 * (карточка рендерит публичные значения как прежде), ADMIN — компактную сетку
 * «подпись → EditableField» под публичным контентом карточки. Каждое поле
 * сохраняется независимо через EditableField → PUT /admin/curation/overrides
 * { entityTable, entityId, fieldName, value }; onChanged рефетчит detail.
 *
 * Один компонент на четыре списка: поля приходят пропом `fields`, чтобы не
 * плодить дубли разметки сетки в каждой карточке-сателлите.
 */
function CurationFieldsPanel({
  entityTable,
  entityId,
  fields,
  role,
  onChanged,
}: {
  entityTable: string;
  entityId: string;
  fields: CurationFieldSpec[];
  role: string | undefined;
  onChanged: () => void;
}) {
  const t = useT();
  const isAdmin = hasRoleAtLeast(role as AuthRole | undefined, 'ADMIN');
  if (!isAdmin) return null;

  return (
    <div className="mt-3 border-t border-dashed border-border pt-2">
      <div className="mb-1.5 inline-flex items-center gap-1 text-[11px] font-medium uppercase tracking-wider text-ink-400">
        <Pencil size={11} aria-hidden />
        {t('hadith.curation.fields_title')}
      </div>
      <dl className="grid grid-cols-[auto_1fr] items-baseline gap-x-3 gap-y-1 text-xs">
        {fields.map((f) => (
          <div key={f.fieldName} className="contents">
            <dt className="text-ink-500" dir="auto">
              {f.label}
            </dt>
            <dd className="min-w-0 text-ink-700">
              <EditableField
                entityTable={entityTable}
                entityId={entityId}
                fieldName={f.fieldName}
                value={f.value}
                kind={f.kind}
                role={role}
                onSaved={onChanged}
              />
            </dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

export default CurationFieldsPanel;
