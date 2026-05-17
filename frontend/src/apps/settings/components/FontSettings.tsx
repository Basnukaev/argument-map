import { useState } from 'react';
import { Monitor, Moon, RotateCcw, Sun } from 'lucide-react';
import { useT } from '@/shared/i18n';
import Modal from '@/shared/components/ui/Modal';
import Button from '@/shared/components/ui/Button';
import { useThemeStore } from '@/shared/stores/themeStore';
import {
  ARABIC_FONTS,
  FONT_PAIRS,
  findArabicFont,
  findPair,
  useFontPairStore,
} from '@/shared/stores/fontPairStore';

/**
 * Секция настроек "Шрифты" для SettingsPage. Контролы:
 *   - Тема (light/dark)
 *   - Радио-список пар шрифтов (sans + serif для латиницы/кириллицы)
 *   - Слайдер веса заголовков
 *   - Слайдер веса UI-текста
 *   - Слайдер плотности reader prose (--density-scale)
 *   - Радио-список арабских шрифтов отдельно
 *   - Reset кнопка
 *
 * Применение через CSS variables (FontPairEffect) - переключение
 * мгновенное во всём UI без re-mount. Persist в localStorage.
 *
 * Latin/Cyrillic пары и Arabic font разделены, потому что эти шрифты
 * родом из разных типографических традиций и не комбинируются как одна
 * "пара". Plus у Manrope/Inter/Lora нет арабских глифов, у Amiri
 * нет латиницы - нужны независимые селекторы.
 */
function FontSettings() {
  const t = useT();
  const [resetOpen, setResetOpen] = useState(false);

  const pairId = useFontPairStore((s) => s.pairId);
  const setPair = useFontPairStore((s) => s.setPair);
  const titleWeight = useFontPairStore((s) => s.titleWeight);
  const setTitleWeight = useFontPairStore((s) => s.setTitleWeight);
  const bodyWeight = useFontPairStore((s) => s.bodyWeight);
  const setBodyWeight = useFontPairStore((s) => s.setBodyWeight);
  const density = useFontPairStore((s) => s.density);
  const setDensity = useFontPairStore((s) => s.setDensity);
  const arabicFontId = useFontPairStore((s) => s.arabicFontId);
  const setArabicFont = useFontPairStore((s) => s.setArabicFont);
  const resetAll = useFontPairStore((s) => s.resetAll);

  const mode = useThemeStore((s) => s.mode);
  const setMode = useThemeStore((s) => s.setMode);

  const activePair = findPair(pairId);
  const activeArabic = findArabicFont(arabicFontId);

  return (
    <div className="flex flex-col gap-8">
      {/* === Theme === */}
      <section>
        <div className="mb-2 text-sm font-semibold text-ink-900">
          {t('settings.section.theme')}
        </div>
        <p className="mb-3 text-xs text-ink-500">
          {t('settings.section.theme.hint')}
        </p>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setMode('system')}
            className={`inline-flex items-center gap-2 rounded-md border px-3 py-1.5 text-sm transition-colors ${
              mode === 'system'
                ? 'border-accent-600 bg-accent-50 text-accent-700'
                : 'border-border bg-elevated text-ink-800 hover:border-border-strong'
            }`}
          >
            <Monitor size={14} aria-hidden /> {t('settings.theme.system')}
          </button>
          <button
            type="button"
            onClick={() => setMode('light')}
            className={`inline-flex items-center gap-2 rounded-md border px-3 py-1.5 text-sm transition-colors ${
              mode === 'light'
                ? 'border-accent-600 bg-accent-50 text-accent-700'
                : 'border-border bg-elevated text-ink-800 hover:border-border-strong'
            }`}
          >
            <Sun size={14} aria-hidden /> {t('settings.theme.light')}
          </button>
          <button
            type="button"
            onClick={() => setMode('dark')}
            className={`inline-flex items-center gap-2 rounded-md border px-3 py-1.5 text-sm transition-colors ${
              mode === 'dark'
                ? 'border-accent-600 bg-accent-50 text-accent-700'
                : 'border-border bg-elevated text-ink-800 hover:border-border-strong'
            }`}
          >
            <Moon size={14} aria-hidden /> {t('settings.theme.dark')}
          </button>
        </div>
      </section>

      {/* === Latin/Cyrillic pair === */}
      <section>
        <div className="mb-2 text-sm font-semibold text-ink-900">
          {t('settings.section.fontPair')}
        </div>
        <p className="mb-3 text-xs text-ink-500">
          {t('settings.section.fontPair.hint')}
        </p>
        <ul className="grid grid-cols-1 gap-1 sm:grid-cols-2">
          {FONT_PAIRS.map((p) => {
            const active = p.id === pairId;
            return (
              <li key={p.id}>
                <button
                  type="button"
                  onClick={() => setPair(p.id)}
                  className={`w-full rounded-md border px-3 py-2 text-start text-sm transition-colors ${
                    active
                      ? 'border-accent-600 bg-accent-50 text-accent-700'
                      : 'border-border bg-elevated text-ink-800 hover:border-border-strong'
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span
                      className="truncate"
                      style={{ fontFamily: p.ui, fontWeight: 600 }}
                    >
                      {p.name}
                    </span>
                    <span
                      className="shrink-0 text-lg"
                      style={{ fontFamily: p.serif, fontWeight: 600 }}
                    >
                      Aa
                    </span>
                  </div>
                  {p.hint && (
                    <div className="mt-0.5 text-xs text-ink-500">{p.hint}</div>
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      </section>

      {/* === Title weight slider === */}
      <section>
        <div className="mb-2 flex items-center justify-between text-sm font-semibold text-ink-900">
          <span>{t('settings.section.titleWeight')}</span>
          <span className="font-mono text-xs text-ink-600">{titleWeight}</span>
        </div>
        <p className="mb-3 text-xs text-ink-500">
          {t('settings.section.titleWeight.hint')}
        </p>
        <input
          type="range"
          min={300}
          max={900}
          step={50}
          value={titleWeight}
          onChange={(e) => setTitleWeight(Number(e.target.value))}
          className="w-full accent-accent-600"
          aria-label={t('settings.titleWeight.aria')}
        />
        <div
          className="mt-3 rounded-md border border-border bg-bg px-3 py-2"
          style={{
            fontFamily: activePair.serif,
            fontSize: 15,
            fontWeight: titleWeight,
            lineHeight: 1.3,
          }}
        >
          {t('settings.preview.title')}
        </div>
      </section>

      {/* === Body weight slider === */}
      <section>
        <div className="mb-2 flex items-center justify-between text-sm font-semibold text-ink-900">
          <span>{t('settings.section.bodyWeight')}</span>
          <span className="font-mono text-xs text-ink-600">{bodyWeight}</span>
        </div>
        <p className="mb-3 text-xs text-ink-500">
          {t('settings.section.bodyWeight.hint')}
        </p>
        <input
          type="range"
          min={300}
          max={700}
          step={50}
          value={bodyWeight}
          onChange={(e) => setBodyWeight(Number(e.target.value))}
          className="w-full accent-accent-600"
          aria-label={t('settings.bodyWeight.aria')}
        />
        <div
          className="mt-3 rounded-md border border-border bg-bg px-3 py-2 text-sm text-ink-800"
          style={{ fontFamily: activePair.ui, fontWeight: bodyWeight }}
        >
          {t('settings.bodyWeight.preview')}
        </div>
      </section>

      {/* === Density slider === */}
      <section>
        <div className="mb-2 flex items-center justify-between text-sm font-semibold text-ink-900">
          <span>{t('settings.section.density')}</span>
          <span className="font-mono text-xs text-ink-600">
            {density.toFixed(2)}×
          </span>
        </div>
        <p className="mb-3 text-xs text-ink-500">
          {t('settings.section.density.hint')}
        </p>
        <input
          type="range"
          min={0.85}
          max={1.15}
          step={0.05}
          value={density}
          onChange={(e) => setDensity(Number(e.target.value))}
          className="w-full accent-accent-600"
          aria-label={t('settings.density.aria')}
        />
      </section>

      {/* === Arabic font === */}
      <section>
        <div className="mb-2 text-sm font-semibold text-ink-900">
          {t('settings.section.arabicFont')}
        </div>
        <p className="mb-3 text-xs text-ink-500">
          {t('settings.section.arabicFont.hint')}
        </p>
        <ul className="grid grid-cols-1 gap-1 sm:grid-cols-2">
          {ARABIC_FONTS.map((f) => {
            const active = f.id === arabicFontId;
            return (
              <li key={f.id}>
                <button
                  type="button"
                  onClick={() => setArabicFont(f.id)}
                  className={`w-full rounded-md border px-3 py-2 text-start text-sm transition-colors ${
                    active
                      ? 'border-accent-600 bg-accent-50 text-accent-700'
                      : 'border-border bg-elevated text-ink-800 hover:border-border-strong'
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="truncate">{f.name}</span>
                    <span
                      dir="rtl"
                      className="shrink-0 text-xl"
                      style={{ fontFamily: f.value, fontWeight: 600 }}
                    >
                      المعارف
                    </span>
                  </div>
                  {f.hint && (
                    <div className="mt-0.5 text-xs text-ink-500">{f.hint}</div>
                  )}
                </button>
              </li>
            );
          })}
        </ul>
        <div
          dir="rtl"
          className="mt-3 rounded-md border border-border bg-bg px-3 py-2 text-end"
          style={{
            fontFamily: activeArabic.value,
            fontSize: 20,
            fontWeight: 600,
            lineHeight: 1.5,
          }}
        >
          {t('settings.preview.arabic')}
        </div>
      </section>

      {/* === Reset === */}
      <section className="border-t border-border pt-6">
        <button
          type="button"
          onClick={() => setResetOpen(true)}
          className="inline-flex items-center gap-2 rounded-md border border-err-500 px-3 py-1.5 text-sm text-err-700 hover:bg-err-100 transition-colors"
        >
          <RotateCcw size={14} aria-hidden /> {t('settings.reset.action')}
        </button>
        <p className="mt-2 text-xs text-ink-500">{t('settings.reset.hint')}</p>
      </section>

      {resetOpen && (
        <Modal
          open={resetOpen}
          onClose={() => setResetOpen(false)}
          title={t('settings.reset.confirm.title')}
          maxWidth="max-w-md"
        >
          <div className="p-6">
            <p className="mb-6 text-sm text-ink-700">
              {t('settings.reset.confirm.body')}
            </p>
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setResetOpen(false)}>
                {t('settings.reset.confirm.cancel')}
              </Button>
              <Button
                variant="danger"
                onClick={() => {
                  resetAll();
                  setResetOpen(false);
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

export default FontSettings;
