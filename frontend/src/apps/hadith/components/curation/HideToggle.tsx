import { useState } from 'react';
import { Eye, EyeOff, Loader2 } from 'lucide-react';
import { apiPutRaw, apiDeleteRaw, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import { hasRoleAtLeast, type AuthRole } from '@/shared/stores/authStore';

/**
 * Курация Фаза 4.b — ADMIN-тогл скрытия записи целиком (record-hide).
 * Зеркалит C-паттерн EditableField: не-ADMIN видит null, ADMIN — кнопку.
 *
 * - Запись видна (`hiddenByAdmin=false`) → кнопка `EyeOff` «Скрыть»; клик
 *   раскрывает инлайн-форму с ОБЯЗАТЕЛЬНым `<textarea>` причины + «Скрыть» /
 *   «Отмена». Submit → PUT /admin/curation/overrides
 *   { entityTable, entityId, fieldName: '__record__', hidden: true, reason }.
 * - Запись скрыта (`hiddenByAdmin=true`) → кнопка `Eye` «Показать снова»; клик
 *   → DELETE …/overrides?entityTable=&entityId=&fieldName=__record__.
 *
 * Родитель отвечает за затемнение карточки + пилюлю «Скрыто администратором».
 * Успех любой операции → toast + onChanged() (родитель рефетчит detail/bio).
 */
function HideToggle({
  entityTable,
  entityId,
  hiddenByAdmin,
  role,
  onChanged,
}: {
  entityTable: string;
  entityId: string;
  hiddenByAdmin: boolean;
  hideReason?: string | null;
  role: string | undefined;
  onChanged: () => void;
}) {
  const t = useT();
  // role приходит string из page (s.user?.role). hasRoleAtLeast делает indexOf
  // по ALL_ROLES → неизвестная строка безопасно даёт false.
  const isAdmin = hasRoleAtLeast(role as AuthRole | undefined, 'ADMIN');

  const [formOpen, setFormOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  if (!isAdmin) return null;

  const recordField = '__record__';

  async function hide(): Promise<void> {
    if (busy) return;
    const next = reason.trim();
    if (!next) return; // обязательная причина — submit заблокирован
    setBusy(true);
    try {
      await apiPutRaw('/api/v1/admin/curation/overrides', {
        entityTable,
        entityId,
        fieldName: recordField,
        hidden: true,
        reason: next,
      });
      setFormOpen(false);
      setReason('');
      toast.success(t('hadith.curation.hidden_confirm'));
      onChanged();
    } catch (error) {
      toast.error(formatApiError(error, t('hadith.curation.save_failed')));
    } finally {
      setBusy(false);
    }
  }

  async function reveal(): Promise<void> {
    if (busy) return;
    setBusy(true);
    try {
      const qs = new URLSearchParams({
        entityTable,
        entityId,
        fieldName: recordField,
      });
      await apiDeleteRaw(`/api/v1/admin/curation/overrides?${qs.toString()}`);
      toast.success(t('hadith.curation.saved'));
      onChanged();
    } catch (error) {
      toast.error(formatApiError(error, t('hadith.curation.save_failed')));
    } finally {
      setBusy(false);
    }
  }

  // Скрытая запись (ADMIN-вид): кнопка вернуть в публичный показ.
  if (hiddenByAdmin) {
    return (
      <button
        type="button"
        disabled={busy}
        onClick={reveal}
        className="inline-flex items-center gap-1 rounded-sm border border-border px-1.5 py-0.5 text-xs font-medium text-ink-600 hover:bg-ink-50 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {busy ? (
          <Loader2 size={12} className="animate-spin" aria-hidden />
        ) : (
          <Eye size={12} aria-hidden />
        )}
        {t('hadith.curation.reveal')}
      </button>
    );
  }

  // Видимая запись: инлайн-форма причины (раскрывается по клику «Скрыть»).
  if (formOpen) {
    return (
      <div className="inline-flex flex-col gap-1.5 align-top">
        <textarea
          dir="auto"
          rows={2}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          aria-label={t('hadith.curation.hide_reason_label')}
          placeholder={t('hadith.curation.hide_reason_label')}
          className="w-56 rounded-sm border border-border bg-bg px-1.5 py-1 text-xs text-ink-700 focus:border-accent-500 focus:outline-none focus:ring-1 focus:ring-accent-500"
        />
        <div className="flex items-center gap-1.5">
          <button
            type="button"
            disabled={busy || reason.trim() === ''}
            onClick={hide}
            className="inline-flex items-center gap-1 rounded-sm border border-rose-500 bg-rose-50 px-1.5 py-0.5 text-xs font-medium text-rose-700 hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {busy && <Loader2 size={11} className="animate-spin" aria-hidden />}
            {t('hadith.curation.hide')}
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={() => {
              setFormOpen(false);
              setReason('');
            }}
            className="inline-flex items-center rounded-sm border border-border px-1.5 py-0.5 text-xs font-medium text-ink-600 hover:bg-ink-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {t('common.cancel')}
          </button>
        </div>
      </div>
    );
  }

  return (
    <button
      type="button"
      onClick={() => setFormOpen(true)}
      className="inline-flex items-center gap-1 rounded-sm border border-border px-1.5 py-0.5 text-xs font-medium text-ink-500 hover:bg-ink-50 hover:text-ink-700"
    >
      <EyeOff size={12} aria-hidden />
      {t('hadith.curation.hide')}
    </button>
  );
}

export default HideToggle;
