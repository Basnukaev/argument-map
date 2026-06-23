import { useState, type ReactNode } from 'react';
import { Loader2, Pencil } from 'lucide-react';
import { apiPutRaw, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import { hasRoleAtLeast, type AuthRole } from '@/shared/stores/authStore';

type FieldKind = 'text' | 'enum' | 'number';

interface EnumOption {
  value: string;
  label: string;
}

/**
 * Курация Фаза 3.b — переиспользуемый ADMIN inline-редактор поля сущности
 * (зеркалит C9-паттерн MatnTranslateControls). Не-ADMIN видит значение plain
 * (рендерится через children/value-обёртку вызывающим), ADMIN дополнительно —
 * карандаш → инлайн-редактор (select для enum, textarea для text, number-input
 * для number) + Сохранить/Отмена. Save → PUT /admin/curation/overrides
 * { entityTable, entityId, fieldName, value } (value всегда строкой). Успех →
 * toast + onSaved() (родитель рефетчит). Ошибка → formatApiError-тост.
 *
 * Когда НЕ в режиме правки — рендерит `label` (богатый стилизованный бейдж из
 * родителя), либо текстовое представление `value` по умолчанию (для enum —
 * label соответствующей опции). В режиме правки заменяет себя на редактор.
 */
function EditableField({
  entityTable,
  entityId,
  fieldName,
  value,
  kind,
  options,
  role,
  onSaved,
  label,
}: {
  entityTable: string;
  entityId: string;
  fieldName: string;
  value: string | number | null;
  kind: FieldKind;
  options?: EnumOption[];
  role: string | undefined;
  onSaved: () => void;
  label?: ReactNode;
}) {
  const t = useT();
  // role приходит как string из page (s.user?.role). hasRoleAtLeast делает
  // indexOf по ALL_ROLES → неизвестная строка безопасно даёт false.
  const isAdmin = hasRoleAtLeast(role as AuthRole | undefined, 'ADMIN');

  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState('');
  const [saving, setSaving] = useState(false);

  // Узел отображения (вне режима правки): богатый label из родителя, либо
  // текстовое значение по умолчанию (для enum — подпись опции).
  const displayNode: ReactNode = (() => {
    if (label != null) return label;
    if (value == null || value === '') return '—';
    if (kind === 'enum') {
      const opt = options?.find((o) => o.value === String(value));
      return <span dir="auto">{opt ? opt.label : String(value)}</span>;
    }
    return <span dir="auto">{String(value)}</span>;
  })();

  function startEdit(): void {
    setDraft(value == null ? '' : String(value));
    setEditing(true);
  }

  function cancelEdit(): void {
    setEditing(false);
    setDraft('');
  }

  async function save(): Promise<void> {
    if (saving) return;
    const next = draft.trim();
    setSaving(true);
    try {
      await apiPutRaw('/api/v1/admin/curation/overrides', {
        entityTable,
        entityId,
        fieldName,
        value: next,
      });
      setEditing(false);
      setDraft('');
      toast.success(t('hadith.curation.saved'));
      onSaved();
    } catch (error) {
      toast.error(formatApiError(error, t('hadith.curation.save_failed')));
    } finally {
      setSaving(false);
    }
  }

  if (editing) {
    return (
      <span className="inline-flex items-center gap-1.5 align-middle">
        {kind === 'enum' ? (
          <select
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            className="rounded-sm border border-border bg-bg px-1.5 py-0.5 text-xs text-ink-700 focus:border-accent-500 focus:outline-none focus:ring-1 focus:ring-accent-500"
          >
            {options?.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        ) : (
          <input
            type={kind === 'number' ? 'number' : 'text'}
            dir="auto"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            className="w-40 rounded-sm border border-border bg-bg px-1.5 py-0.5 text-xs text-ink-700 focus:border-accent-500 focus:outline-none focus:ring-1 focus:ring-accent-500"
          />
        )}
        <button
          type="button"
          disabled={saving}
          onClick={save}
          className="inline-flex items-center gap-1 rounded-sm border border-accent-500 bg-accent-50 px-1.5 py-0.5 text-xs font-medium text-accent-700 hover:bg-accent-100 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {saving && <Loader2 size={11} className="animate-spin" aria-hidden />}
          {t('common.save')}
        </button>
        <button
          type="button"
          disabled={saving}
          onClick={cancelEdit}
          className="inline-flex items-center rounded-sm border border-border px-1.5 py-0.5 text-xs font-medium text-ink-600 hover:bg-ink-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {t('common.cancel')}
        </button>
      </span>
    );
  }

  return (
    <span className="inline-flex items-center gap-1 align-middle">
      {displayNode}
      {isAdmin && (
        <button
          type="button"
          onClick={startEdit}
          aria-label={t('hadith.curation.edit_field')}
          title={t('hadith.curation.edit_field')}
          className="rounded-sm p-0.5 text-ink-400 hover:bg-ink-50 hover:text-ink-600"
        >
          <Pencil size={12} aria-hidden />
        </button>
      )}
    </span>
  );
}

export default EditableField;
