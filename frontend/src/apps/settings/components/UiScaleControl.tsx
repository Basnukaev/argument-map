import { useT, type DictKey } from '@/shared/i18n';
import {
  useUiScaleStore,
  type UiScalePreset,
} from '@/shared/stores/uiScaleStore';

const PRESETS: ReadonlyArray<{ id: UiScalePreset; labelKey: DictKey }> = [
  { id: 'compact', labelKey: 'settings.uiScale.compact' },
  { id: 'standard', labelKey: 'settings.uiScale.standard' },
  { id: 'comfortable', labelKey: 'settings.uiScale.comfortable' },
];

/**
 * Сегментированный контрол масштаба интерфейса (баг #2). Три пресета,
 * текущий подсвечен. 'standard' = базовый 100% - one-click rollback к
 * базе ("не руби с плеча"). Дефолт - compact (90%).
 *
 * Применяется через UiScaleEffect (base font-size на <html>) - весь
 * rem-based UI масштабируется. Persist в localStorage через
 * uiScaleStore.
 */
function UiScaleControl() {
  const t = useT();
  const scale = useUiScaleStore((s) => s.scale);
  const setScale = useUiScaleStore((s) => s.setScale);

  return (
    <div
      role="radiogroup"
      aria-label={t('settings.uiScale.aria')}
      className="inline-flex w-full rounded-md border border-border bg-elevated p-0.5"
    >
      {PRESETS.map((preset) => {
        const active = preset.id === scale;
        return (
          <button
            key={preset.id}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => setScale(preset.id)}
            className={`flex-1 rounded-sm px-2 py-1.5 text-center text-xs font-medium transition-colors ${
              active
                ? 'bg-accent-600 text-ink-0 shadow-sh1'
                : 'text-ink-700 hover:bg-ink-100 hover:text-ink-900'
            }`}
          >
            {t(preset.labelKey)}
          </button>
        );
      })}
    </div>
  );
}

export default UiScaleControl;
