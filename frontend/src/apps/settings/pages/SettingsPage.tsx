import Header from '@/shared/components/layout/Header';
import FontSettings from '@/apps/settings/components/FontSettings';
import { useT } from '@/shared/i18n';

/**
 * Страница настроек приложения. Единственная секция - FontSettings:
 * тема (light/dark/system), пара latin/cyrillic шрифтов, арабский шрифт
 * (10 опций), масштаб интерфейса, плотность reader-prose, вес заголовков
 * и UI-текста. Persist в localStorage.
 *
 * Все настройки применяются мгновенно через FontPairEffect / UiScaleEffect /
 * ThemeEffect (CSS variables на root, без re-mount).
 */
function SettingsPage() {
  const t = useT();
  return (
    <div className="flex min-h-screen flex-col bg-bg">
      <Header />
      <main className="mx-auto w-full max-w-3xl flex-1 px-6 py-8">
        <div className="mb-6 text-xs uppercase tracking-wider text-ink-500">
          {t('settings.breadcrumb')}
        </div>
        <h1 className="mb-2 text-2xl font-semibold text-ink-900">
          {t('settings.title')}
        </h1>
        <p className="mb-8 text-sm text-ink-600">{t('settings.subtitle')}</p>
        <div className="rounded-lg border border-border bg-elevated p-6">
          <FontSettings />
        </div>
      </main>
    </div>
  );
}

export default SettingsPage;
