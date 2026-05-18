import { useState } from 'react';
import { RotateCcw } from 'lucide-react';
import { useT } from '@/shared/i18n';
import Modal from '@/shared/components/ui/Modal';
import Button from '@/shared/components/ui/Button';
import {
  usePreferencesStore,
  type ArabicFontPref,
  type BilingualModePref,
  type LocalePref,
  type TextSizePref,
} from '@/shared/stores/preferencesStore';
import { useToastStore } from '@/shared/stores/toastStore';

/**
 * Секция Settings: язык интерфейса, размер текста, упрощённый выбор
 * арабского шрифта (3 опции), переключатели tashkeel/транслит. Все
 * изменения persist на бэк через preferencesStore. Toast на каждое
 * успешное сохранение / ошибку.
 *
 * Сосуществует с FontSettings (тонкая настройка пар + density + 10
 * арабских шрифтов) - эта секция для базовых user-preferences которые
 * нужны массовому пользователю; FontSettings - для тех кто хочет
 * fine-grained typography control.
 */
function UserPreferencesSection() {
  const t = useT();
  const showToast = useToastStore((s) => s.show);
  const [resetOpen, setResetOpen] = useState(false);

  const locale = usePreferencesStore((s) => s.locale);
  const arabicFont = usePreferencesStore((s) => s.arabicFont);
  const textSize = usePreferencesStore((s) => s.textSize);
  const hideTashkeel = usePreferencesStore((s) => s.hideTashkeelByDefault);
  const transliteration = usePreferencesStore((s) => s.transliteration);
  const bilingualMode = usePreferencesStore((s) => s.bilingualMode);
  const setPref = usePreferencesStore((s) => s.set);
  const resetAll = usePreferencesStore((s) => s.resetAll);

  async function tryPersist(action: () => Promise<void>) {
    try {
      await action();
      showToast({ kind: 'success', message: t('settings.saved_toast') });
    } catch {
      showToast({ kind: 'error', message: t('settings.save_error_toast') });
    }
  }

  const setLocale = (v: LocalePref) => tryPersist(() => setPref('locale', v));
  const setArabicFont = (v: ArabicFontPref) =>
    tryPersist(() => setPref('arabicFont', v));
  const setTextSize = (v: TextSizePref) =>
    tryPersist(() => setPref('textSize', v));
  const setHideTashkeel = (v: boolean) =>
    tryPersist(() => setPref('hideTashkeelByDefault', v));
  const setTransliteration = (v: boolean) =>
    tryPersist(() => setPref('transliteration', v));
  const setBilingualMode = (v: BilingualModePref) =>
    tryPersist(() => setPref('bilingualMode', v));

  const textSizeScaleMap: Record<TextSizePref, string> = {
    small: '0.875rem',
    medium: '1rem',
    large: '1.125rem',
    xl: '1.25rem',
  };

  return (
    <div className="mt-8 flex flex-col gap-8 border-t border-border pt-8">
      {/* === Language === */}
      <section>
        <div className="mb-2 text-sm font-semibold text-ink-900">
          {t('settings.section.language')}
        </div>
        <p className="mb-3 text-xs text-ink-500">
          {t('settings.section.language.hint')}
        </p>
        <div className="flex flex-wrap gap-2">
          {(['ru', 'ar', 'en'] as const).map((l) => (
            <button
              key={l}
              type="button"
              onClick={() => void setLocale(l)}
              className={`inline-flex items-center gap-2 rounded-md border px-3 py-1.5 text-sm transition-colors ${
                locale === l
                  ? 'border-accent-600 bg-accent-50 text-accent-700'
                  : 'border-border bg-elevated text-ink-800 hover:border-border-strong'
              }`}
            >
              {t(`settings.language.${l}` as const)}
            </button>
          ))}
        </div>
      </section>

      {/* === Text size === */}
      <section>
        <div className="mb-2 text-sm font-semibold text-ink-900">
          {t('settings.section.textSize')}
        </div>
        <p className="mb-3 text-xs text-ink-500">
          {t('settings.section.textSize.hint')}
        </p>
        <div className="flex flex-wrap gap-2">
          {(['small', 'medium', 'large', 'xl'] as const).map((sz) => (
            <button
              key={sz}
              type="button"
              onClick={() => void setTextSize(sz)}
              className={`inline-flex items-center gap-2 rounded-md border px-3 py-1.5 text-sm transition-colors ${
                textSize === sz
                  ? 'border-accent-600 bg-accent-50 text-accent-700'
                  : 'border-border bg-elevated text-ink-800 hover:border-border-strong'
              }`}
            >
              {t(`settings.textSize.${sz}` as const)}
            </button>
          ))}
        </div>
        <div
          className="mt-3 rounded-md border border-border bg-bg px-3 py-2 text-ink-800"
          style={{ fontSize: textSizeScaleMap[textSize] }}
        >
          {t('settings.textSize.preview')}
        </div>
      </section>

      {/* === Arabic font (simplified) === */}
      <section>
        <div className="mb-2 text-sm font-semibold text-ink-900">
          {t('settings.section.arabicFont.pref')}
        </div>
        <p className="mb-3 text-xs text-ink-500">
          {t('settings.section.arabicFont.pref.hint')}
        </p>
        <div className="flex flex-wrap gap-2">
          {(['naskh', 'kufi', 'tahoma'] as const).map((f) => (
            <button
              key={f}
              type="button"
              onClick={() => void setArabicFont(f)}
              className={`inline-flex items-center gap-2 rounded-md border px-3 py-1.5 text-sm transition-colors ${
                arabicFont === f
                  ? 'border-accent-600 bg-accent-50 text-accent-700'
                  : 'border-border bg-elevated text-ink-800 hover:border-border-strong'
              }`}
            >
              {t(`settings.arabicFont.${f}` as const)}
            </button>
          ))}
        </div>
      </section>

      {/* === Tashkeel === */}
      <section>
        <div className="mb-2 text-sm font-semibold text-ink-900">
          {t('settings.section.tashkeel')}
        </div>
        <p className="mb-3 text-xs text-ink-500">
          {t('settings.section.tashkeel.hint')}
        </p>
        <label className="flex items-center gap-3 cursor-pointer">
          <input
            type="checkbox"
            checked={hideTashkeel}
            onChange={(e) =>
              void setHideTashkeel(e.target.checked)
            }
            className="h-4 w-4 accent-accent-600"
          />
          <span className="text-sm text-ink-800">
            {t('settings.tashkeel.hide_default')}
          </span>
        </label>
      </section>

      {/* === Transliteration === */}
      <section>
        <div className="mb-2 text-sm font-semibold text-ink-900">
          {t('settings.section.transliteration')}
        </div>
        <p className="mb-3 text-xs text-ink-500">
          {t('settings.section.transliteration.hint')}
        </p>
        <label className="flex items-center gap-3 cursor-pointer">
          <input
            type="checkbox"
            checked={transliteration}
            onChange={(e) => void setTransliteration(e.target.checked)}
            className="h-4 w-4 accent-accent-600"
          />
          <span className="text-sm text-ink-800">
            {t('settings.transliteration.enable')}
          </span>
        </label>
      </section>

      {/* === Bilingual mode === */}
      <section>
        <div className="mb-2 text-sm font-semibold text-ink-900">
          {t('settings.section.bilingual')}
        </div>
        <p className="mb-3 text-xs text-ink-500">
          {t('settings.section.bilingual.hint')}
        </p>
        <div className="flex flex-wrap gap-2">
          {(['original', 'translation', 'both'] as const).map((m) => (
            <button
              key={m}
              type="button"
              onClick={() => void setBilingualMode(m)}
              className={`inline-flex items-center gap-2 rounded-md border px-3 py-1.5 text-sm transition-colors ${
                bilingualMode === m
                  ? 'border-accent-600 bg-accent-50 text-accent-700'
                  : 'border-border bg-elevated text-ink-800 hover:border-border-strong'
              }`}
            >
              {t(`settings.bilingual.${m}` as const)}
            </button>
          ))}
        </div>
      </section>

      {/* === Reset all === */}
      <section className="border-t border-border pt-6">
        <button
          type="button"
          onClick={() => setResetOpen(true)}
          className="inline-flex items-center gap-2 rounded-md border border-err-500 px-3 py-1.5 text-sm text-err-700 hover:bg-err-100 transition-colors"
        >
          <RotateCcw size={14} aria-hidden /> {t('settings.reset_defaults')}
        </button>
        <p className="mt-2 text-xs text-ink-500">
          {t('settings.reset_defaults.hint')}
        </p>
      </section>

      {resetOpen && (
        <Modal
          open={resetOpen}
          onClose={() => setResetOpen(false)}
          title={t('settings.reset_confirm')}
          maxWidth="max-w-md"
        >
          <div className="p-6">
            <p className="mb-6 text-sm text-ink-700">
              {t('settings.reset_confirm.body')}
            </p>
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setResetOpen(false)}>
                {t('settings.reset.confirm.cancel')}
              </Button>
              <Button
                variant="danger"
                onClick={async () => {
                  try {
                    await resetAll();
                    showToast({
                      kind: 'success',
                      message: t('settings.saved_toast'),
                    });
                  } catch {
                    showToast({
                      kind: 'error',
                      message: t('settings.save_error_toast'),
                    });
                  } finally {
                    setResetOpen(false);
                  }
                }}
              >
                {t('settings.reset.confirm.ok')}
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}

export default UserPreferencesSection;
