import { Settings } from 'lucide-react';
import { useT } from '@/shared/i18n';
import { useSettingsDrawerStore } from '@/shared/stores/settingsDrawerStore';

/**
 * Кнопка-шестерёнка в header. Открывает Settings drawer (slide-over) -
 * primary access к настройкам без потери контекста текущей страницы
 * (баг #1). Раньше это была ссылка на /settings (full-page navigation,
 * теряла scroll/state). /settings route остаётся для deep-link'ов.
 */
function SettingsLink() {
  const t = useT();
  const showSettings = useSettingsDrawerStore((s) => s.show);
  return (
    <button
      type="button"
      onClick={showSettings}
      title={t('settings.link.title')}
      aria-label={t('settings.drawer.open_aria')}
      className="inline-flex h-7 w-7 items-center justify-center rounded-sm border border-ink-200 bg-elevated text-ink-700 hover:bg-ink-100 hover:text-ink-900 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 focus-visible:ring-offset-1 focus-visible:ring-offset-bg"
    >
      <Settings size={14} aria-hidden />
    </button>
  );
}

export default SettingsLink;
