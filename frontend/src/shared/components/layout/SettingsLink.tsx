import { Settings } from 'lucide-react';
import { Link } from 'react-router';
import { useT } from '@/shared/i18n';

/**
 * Кнопка-ссылка на /settings в header. Маленькая иконка шестерёнки,
 * стиль аналогичен ThemeSwitch/LocaleSwitch. Заменила FontPairSwitch
 * dropdown (Сессия 38) - tweaker перенесён в отдельный экран
 * настроек, который будет расширяться (density, hotkeys, etc.).
 */
function SettingsLink() {
  const t = useT();
  return (
    <Link
      to="/settings"
      title={t('settings.link.title')}
      aria-label={t('settings.link.aria')}
      className="inline-flex h-7 w-7 items-center justify-center rounded-sm border border-ink-200 bg-elevated text-ink-700 hover:bg-ink-100 hover:text-ink-900 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 focus-visible:ring-offset-1 focus-visible:ring-offset-bg"
    >
      <Settings size={14} aria-hidden />
    </Link>
  );
}

export default SettingsLink;
