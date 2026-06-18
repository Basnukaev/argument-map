import { useState } from 'react';
import { Languages, Loader2, Pencil } from 'lucide-react';
import { apiPostRaw, apiPatchRaw, ApiError, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import { hasRoleAtLeast, type AuthRole } from '@/shared/stores/authStore';
import type { components } from '@/shared/api/types';

type MatnTranslationResponse = components['schemas']['MatnTranslationResponse'];

type Lang = 'ru' | 'en';

/**
 * On-demand AI-перевод матна (План 7, реш. 7). Кнопки RU/EN под текстом матна:
 * перевод есть (из detail-пропсов или после POST) → текст показан, кнопка скрыта;
 * нет → POST /hadith/matns/{matnId}/translate {lang} + лоадер «Перевод (5-15с)…» →
 * текст под матном. 503 llm-not-configured → тост «AI-провайдер не настроен»,
 * прочие ошибки → formatApiError-тост.
 *
 * ADMIN-правка сохранённого перевода (C9): когда перевод УЖЕ есть и юзер ADMIN —
 * рядом кнопка-карандаш → инлайн-textarea → PATCH
 * /hadith/matns/{matnId}/translation {lang,text} перезаписывает сохранённый
 * text_ru/text_en БЕЗ вызова LLM. Роль приходит пропом role из HadithDetailPage
 * (тот же источник, что грейд-гейт). Не-ADMIN правки не видит.
 */
function MatnTranslateControls({
  matnId,
  textRu,
  textEn,
  role,
}: {
  matnId: string;
  textRu: string | null;
  textEn: string | null;
  role?: AuthRole | null;
}) {
  const t = useT();
  // Локальный стейт переводов: инициализация из detail-пропсов, дополняется POST/PATCH'ем.
  const [translations, setTranslations] = useState<Record<Lang, string | null>>({
    ru: textRu,
    en: textEn,
  });
  const [loading, setLoading] = useState<Lang | null>(null);
  // Инлайн-правка: какой язык редактируется (null — не в режиме правки) + черновик.
  const [editing, setEditing] = useState<Lang | null>(null);
  const [draft, setDraft] = useState('');
  const [saving, setSaving] = useState(false);

  const isAdmin = hasRoleAtLeast(role, 'ADMIN');

  async function translate(lang: Lang): Promise<void> {
    if (loading || translations[lang]) return;
    setLoading(lang);
    try {
      const res = await apiPostRaw<MatnTranslationResponse>(
        `/api/v1/hadith/matns/${matnId}/translate`,
        { lang },
      );
      setTranslations((prev) => ({ ...prev, [lang]: res.text ?? '' }));
    } catch (error) {
      if (error instanceof ApiError && error.is('llm-not-configured')) {
        toast.error(t('hadith.translate.not_configured'));
      } else {
        toast.error(formatApiError(error, t('hadith.translate.failed')));
      }
    } finally {
      setLoading(null);
    }
  }

  function startEdit(lang: Lang): void {
    setEditing(lang);
    setDraft(translations[lang] ?? '');
  }

  function cancelEdit(): void {
    setEditing(null);
    setDraft('');
  }

  async function save(lang: Lang): Promise<void> {
    const text = draft.trim();
    // Дизейбл-гард уже не пускает пустой/неизменённый, но дублируем для надёжности.
    if (saving || text === '' || text === translations[lang]) return;
    setSaving(true);
    try {
      const res = await apiPatchRaw<MatnTranslationResponse>(
        `/api/v1/hadith/matns/${matnId}/translation`,
        { lang, text },
      );
      setTranslations((prev) => ({ ...prev, [lang]: res.text ?? text }));
      setEditing(null);
      setDraft('');
      toast.success(t('hadith.translate.saved'));
    } catch (error) {
      toast.error(formatApiError(error, t('hadith.translate.save_failed')));
    } finally {
      setSaving(false);
    }
  }

  const langs: Lang[] = ['ru', 'en'];
  // Кнопку перевода показываем только для языков без перевода.
  const pending = langs.filter((l) => !translations[l]);

  return (
    <div className="mt-3">
      {pending.length > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          <Languages size={14} aria-hidden className="text-ink-400" />
          {pending.map((lang) => (
            <button
              key={lang}
              type="button"
              disabled={loading != null}
              onClick={() => translate(lang)}
              className="inline-flex items-center gap-1 rounded-sm border border-border px-2 py-0.5 text-xs font-medium text-ink-600 hover:bg-ink-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {loading === lang ? (
                <>
                  <Loader2 size={12} className="animate-spin" aria-hidden />
                  {t('hadith.translate.loading')}
                </>
              ) : (
                t(lang === 'ru' ? 'hadith.translate.ru' : 'hadith.translate.en')
              )}
            </button>
          ))}
        </div>
      )}

      {langs.map((lang) => {
        const value = translations[lang];
        if (!value) return null;
        if (editing === lang) {
          const trimmed = draft.trim();
          const canSave = !saving && trimmed !== '' && trimmed !== value;
          return (
            <div key={lang} className="mt-2">
              <textarea
                dir="auto"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                rows={3}
                className="w-full resize-y rounded-sm border border-border bg-bg px-2 py-1.5 text-sm text-ink-700 focus:border-accent-500 focus:outline-none focus:ring-1 focus:ring-accent-500"
              />
              <div className="mt-1.5 flex items-center gap-2">
                <button
                  type="button"
                  disabled={!canSave}
                  onClick={() => save(lang)}
                  className="inline-flex items-center gap-1 rounded-sm border border-accent-500 bg-accent-50 px-2 py-0.5 text-xs font-medium text-accent-700 hover:bg-accent-100 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {saving && <Loader2 size={12} className="animate-spin" aria-hidden />}
                  {t('common.save')}
                </button>
                <button
                  type="button"
                  disabled={saving}
                  onClick={cancelEdit}
                  className="inline-flex items-center rounded-sm border border-border px-2 py-0.5 text-xs font-medium text-ink-600 hover:bg-ink-50 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {t('common.cancel')}
                </button>
              </div>
            </div>
          );
        }
        return (
          <p key={lang} className="mt-2 flex items-start gap-1.5 text-sm text-ink-500">
            <span dir="auto" className="min-w-0 flex-1">
              {value}
            </span>
            {isAdmin && (
              <button
                type="button"
                onClick={() => startEdit(lang)}
                aria-label={t('hadith.translate.edit')}
                title={t('hadith.translate.edit')}
                className="mt-0.5 shrink-0 rounded-sm p-0.5 text-ink-400 hover:bg-ink-50 hover:text-ink-600"
              >
                <Pencil size={13} aria-hidden />
              </button>
            )}
          </p>
        );
      })}
    </div>
  );
}

export default MatnTranslateControls;
