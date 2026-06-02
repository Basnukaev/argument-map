import { useState } from 'react';
import { Monitor, Moon, RotateCcw, Sun } from 'lucide-react';
import { useT } from '@/shared/i18n';
import Modal from '@/shared/components/ui/Modal';
import Button from '@/shared/components/ui/Button';
import UiScaleControl from '@/apps/settings/components/UiScaleControl';
import { useThemeStore } from '@/shared/stores/themeStore';
import { useUiScaleStore } from '@/shared/stores/uiScaleStore';
import {
  ARABIC_FONTS,
  FONT_PAIRS,
  findArabicFont,
  findPair,
  useFontPairStore,
} from '@/shared/stores/fontPairStore';

/**
 * Секция настроек шрифтов и оформления. Контролы сгруппированы по смыслу
 * (реорганизация - меньше скролла, легче сканировать):
 *   - «Тема» (light/dark/system)
 *   - «Шрифты» (latin/cyrillic пара + арабский шрифт + превью)
 *   - «Размер» (масштаб интерфейса + плотность reader prose)
 *   - «Насыщенность» (вес заголовков + вес UI-текста)
 *   - Reset (включая масштаб интерфейса)
 *
 * Применение через CSS variables (FontPairEffect / UiScaleEffect) -
 * переключение мгновенное во всём UI без re-mount. Persist в localStorage.
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

  const resetUiScale = useUiScaleStore((s) => s.reset);

  const activePair = findPair(pairId);
  const activeArabic = findArabicFont(arabicFontId);

  return (
    <div className="flex flex-col gap-6">
      {/* ======================= ГРУППА: Тема ======================= */}
      <GroupHeading>{t('settings.group.theme')}</GroupHeading>
      <section>
        <SectionLabel>{t('settings.section.theme')}</SectionLabel>
        <SectionHint>{t('settings.section.theme.hint')}</SectionHint>
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

      {/* ====================== ГРУППА: Шрифты ====================== */}
      <GroupHeading>{t('settings.group.fonts')}</GroupHeading>

      {/* Latin/Cyrillic pair */}
      <section>
        <SectionLabel>{t('settings.section.fontPair')}</SectionLabel>
        <SectionHint>{t('settings.section.fontPair.hint')}</SectionHint>
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

      {/* Arabic font */}
      <section>
        <SectionLabel>{t('settings.section.arabicFont')}</SectionLabel>
        <SectionHint>{t('settings.section.arabicFont.hint')}</SectionHint>
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

      {/* ====================== ГРУППА: Размер ====================== */}
      <GroupHeading>{t('settings.group.size')}</GroupHeading>

      {/* UI scale (interface zoom, баг #2) */}
      <section>
        <SectionLabel>{t('settings.section.uiScale')}</SectionLabel>
        <SectionHint>{t('settings.section.uiScale.hint')}</SectionHint>
        <UiScaleControl />
      </section>

      {/* Density slider (reader prose) */}
      <section>
        <div className="mb-1 flex items-center justify-between text-sm font-semibold text-ink-900">
          <span>{t('settings.section.density')}</span>
          <span className="font-mono text-xs text-ink-600">
            {density.toFixed(2)}×
          </span>
        </div>
        <SectionHint>{t('settings.section.density.hint')}</SectionHint>
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

      {/* =================== ГРУППА: Насыщенность =================== */}
      <GroupHeading>{t('settings.group.weights')}</GroupHeading>

      {/* Title weight slider */}
      <section>
        <div className="mb-1 flex items-center justify-between text-sm font-semibold text-ink-900">
          <span>{t('settings.section.titleWeight')}</span>
          <span className="font-mono text-xs text-ink-600">{titleWeight}</span>
        </div>
        <SectionHint>{t('settings.section.titleWeight.hint')}</SectionHint>
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

      {/* Body weight slider */}
      <section>
        <div className="mb-1 flex items-center justify-between text-sm font-semibold text-ink-900">
          <span>{t('settings.section.bodyWeight')}</span>
          <span className="font-mono text-xs text-ink-600">{bodyWeight}</span>
        </div>
        <SectionHint>{t('settings.section.bodyWeight.hint')}</SectionHint>
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

      {/* ========================== Reset ========================== */}
      <section className="border-t border-border pt-5">
        <button
          type="button"
          onClick={() => setResetOpen(true)}
          className="inline-flex items-center gap-2 rounded-md border border-err-500 px-3 py-1.5 text-sm text-err-700 hover:bg-err-100 transition-colors"
        >
          <RotateCcw size={14} aria-hidden /> {t('settings.reset.action')}
        </button>
        <p className="mt-2 text-xs text-ink-500">{t('settings.reset.hint')}</p>
        <p className="mt-1 text-xs text-ink-500">
          {t('settings.reset.uiScale_note')}
        </p>
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
                  resetUiScale();
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

/** Заголовок группы секций - крупнее и заметнее чем SectionLabel. */
function GroupHeading({ children }: { children: React.ReactNode }) {
  return (
    <h2 className="text-xs font-semibold uppercase tracking-wider text-ink-500 first:mt-0">
      {children}
    </h2>
  );
}

/** Подпись отдельного контрола внутри группы. */
function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <div className="mb-1 text-sm font-semibold text-ink-900">{children}</div>
  );
}

/** Hint под подписью контрола. */
function SectionHint({ children }: { children: React.ReactNode }) {
  return <p className="mb-2 text-xs text-ink-500">{children}</p>;
}

export default FontSettings;
