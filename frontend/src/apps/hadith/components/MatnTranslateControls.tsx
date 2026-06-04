import { useState } from 'react';
import { Languages, Loader2 } from 'lucide-react';
import { apiPostRaw, ApiError, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type MatnTranslationResponse = components['schemas']['MatnTranslationResponse'];

type Lang = 'ru' | 'en';

/**
 * On-demand AI-перевод матна (План 7, реш. 7). Кнопки RU/EN под текстом матна:
 * перевод есть (из detail-пропсов или после POST) → текст показан, кнопка скрыта;
 * нет → POST /hadith/matns/{matnId}/translate {lang} + лоадер «Перевод (5-15с)…» →
 * текст под матном. 503 llm-not-configured → тост «AI-провайдер не настроен»,
 * прочие ошибки → formatApiError-тост. force-режим (ADMIN-регенерация) — не в UI,
 * admin-фича через curl.
 */
function MatnTranslateControls({
  matnId,
  textRu,
  textEn,
}: {
  matnId: string;
  textRu: string | null;
  textEn: string | null;
}) {
  const t = useT();
  // Локальный стейт переводов: инициализация из detail-пропсов, дополняется POST'ом.
  const [translations, setTranslations] = useState<Record<Lang, string | null>>({
    ru: textRu,
    en: textEn,
  });
  const [loading, setLoading] = useState<Lang | null>(null);

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

  const langs: Lang[] = ['ru', 'en'];
  // Кнопку показываем только для языков без перевода.
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

      {langs.map((lang) =>
        translations[lang] ? (
          <p key={lang} className="mt-2 text-sm text-ink-500" dir="ltr">
            {translations[lang]}
          </p>
        ) : null,
      )}
    </div>
  );
}

export default MatnTranslateControls;
