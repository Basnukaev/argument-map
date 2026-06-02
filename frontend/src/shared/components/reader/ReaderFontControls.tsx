import { useEffect, useRef, useState } from 'react';
import { Type, X } from 'lucide-react';
import Select from '@/shared/components/ui/Select';
import { useT } from '@/shared/i18n';
import { useSettingsDrawerStore } from '@/shared/stores/settingsDrawerStore';
import {
  ARABIC_FONTS,
  FONT_PAIRS,
  useFontPairStore,
} from '@/shared/stores/fontPairStore';

/**
 * Плавающая кнопка «Aa» + компактный поповер с самыми нужными для
 * чтения контролами шрифта (баг #1: «примерить шрифты ПРЯМО во время
 * чтения книги»). Использует тот же fontPairStore что и полные
 * настройки - изменения применяются live к тексту страницы (через
 * FontPairEffect CSS-переменные) и persist'ятся везде.
 *
 * Контролы: арабский шрифт + плотность текста + пара шрифтов интерфейса/
 * заголовков. Кнопка «Все настройки» открывает полный drawer.
 *
 * Закрывается по клику снаружи и Escape. Позиционируется в конце экрана
 * (logical end), не перекрывает текст в центре.
 */
function ReaderFontControls() {
  const t = useT();
  const [open, setOpen] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);

  const arabicFontId = useFontPairStore((s) => s.arabicFontId);
  const setArabicFont = useFontPairStore((s) => s.setArabicFont);
  const pairId = useFontPairStore((s) => s.pairId);
  const setPair = useFontPairStore((s) => s.setPair);
  const density = useFontPairStore((s) => s.density);
  const setDensity = useFontPairStore((s) => s.setDensity);
  const showSettings = useSettingsDrawerStore((s) => s.show);

  // Клик снаружи закрывает поповер
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (
        wrapperRef.current &&
        !wrapperRef.current.contains(e.target as Node)
      ) {
        setOpen(false);
      }
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const arabicOptions = ARABIC_FONTS.map((f) => ({
    value: f.id,
    label: f.name,
  }));
  const pairOptions = FONT_PAIRS.map((p) => ({
    value: p.id,
    label: p.name,
  }));

  return (
    <div
      ref={wrapperRef}
      className="fixed bottom-5 end-5 z-30 flex flex-col items-end gap-2"
    >
      {open && (
        <div
          role="dialog"
          aria-label={t('reader.fonts.title')}
          className="w-72 max-w-[calc(100vw-2.5rem)] rounded-lg border border-border bg-elevated p-4 shadow-sh4"
        >
          <div className="mb-3 flex items-start justify-between gap-2">
            <div className="min-w-0">
              <div className="text-sm font-semibold text-ink-900">
                {t('reader.fonts.title')}
              </div>
              <p className="mt-0.5 text-xs text-ink-500">
                {t('reader.fonts.hint')}
              </p>
            </div>
            <button
              type="button"
              onClick={() => setOpen(false)}
              aria-label={t('reader.fonts.close')}
              className="grid h-6 w-6 shrink-0 place-items-center rounded text-ink-500 hover:bg-ink-100 hover:text-ink-700"
            >
              <X size={14} aria-hidden />
            </button>
          </div>

          <div className="flex flex-col gap-3">
            {/* Arabic font - самый важный для чтения арабских текстов */}
            <label className="flex flex-col gap-1">
              <span className="text-xs font-medium text-ink-700">
                {t('reader.fonts.arabic_label')}
              </span>
              <Select
                value={arabicFontId}
                onChange={setArabicFont}
                options={arabicOptions}
                size="sm"
                className="w-full"
                ariaLabel={t('reader.fonts.arabic_label')}
              />
            </label>

            {/* Body / title pair */}
            <label className="flex flex-col gap-1">
              <span className="text-xs font-medium text-ink-700">
                {t('reader.fonts.pair_label')}
              </span>
              <Select
                value={pairId}
                onChange={setPair}
                options={pairOptions}
                size="sm"
                className="w-full"
                ariaLabel={t('reader.fonts.pair_label')}
              />
            </label>

            {/* Density */}
            <label className="flex flex-col gap-1">
              <span className="flex items-center justify-between text-xs font-medium text-ink-700">
                {t('reader.fonts.density_label')}
                <span className="font-mono text-ink-500">
                  {density.toFixed(2)}×
                </span>
              </span>
              <input
                type="range"
                min={0.85}
                max={1.15}
                step={0.05}
                value={density}
                onChange={(e) => setDensity(Number(e.target.value))}
                className="w-full accent-accent-600"
                aria-label={t('reader.fonts.density_label')}
              />
            </label>

            <button
              type="button"
              onClick={() => {
                setOpen(false);
                showSettings();
              }}
              className="mt-1 text-start text-xs font-medium text-accent-600 hover:text-accent-700 hover:underline"
            >
              {t('reader.fonts.open_full')}
            </button>
          </div>
        </div>
      )}

      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-label={t('reader.fonts.button_aria')}
        title={t('reader.fonts.title')}
        className={`grid h-11 w-11 place-items-center rounded-full border shadow-sh3 transition-colors ${
          open
            ? 'border-accent-600 bg-accent-600 text-ink-0'
            : 'border-border bg-elevated text-ink-700 hover:bg-ink-100 hover:text-ink-900'
        }`}
      >
        <Type size={18} aria-hidden />
      </button>
    </div>
  );
}

export default ReaderFontControls;
