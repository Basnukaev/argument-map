import { useEffect, useRef } from 'react';
import { X } from 'lucide-react';
import IconButton from '@/shared/components/ui/IconButton';
import FontSettings from '@/apps/settings/components/FontSettings';
import UserPreferencesSection from '@/apps/settings/components/UserPreferencesSection';
import { useT } from '@/shared/i18n';
import { useIsMobile } from '@/shared/hooks/useViewport';
import { useSettingsDrawerStore } from '@/shared/stores/settingsDrawerStore';

/**
 * Правый slide-over с настройками (баг #1). Рендерит тот же контент что
 * и /settings (FontSettings + UserPreferencesSection), но БЕЗ навигации -
 * закрытие возвращает пользователя ровно туда где он был (контекст
 * страницы, scroll, состояние графа/reader сохранены).
 *
 * Mounted один раз в App (как CommandPalette / SourceDetailPanel) поверх
 * текущей страницы. Conditional render `{open && <Drawer/>}` - идиома
 * проекта, естественный reset без effect-driven state.
 *
 * Реализация - own backdrop + `<aside>` slide-in (как TopicSettingsDrawer
 * и SourceDetailPanel), не нативный `<dialog>` Modal: нужен end-side
 * slide, а не центр. Escape / клик по backdrop закрывают.
 *
 * Focus trap: drawer помечен `role="dialog" aria-modal="true"`,
 * автофокус уводится на close-кнопку через autoFocus, Escape закрывает.
 * Tab/Shift+Tab зациклены внутри drawer'а (не убегают на underlying
 * page), при закрытии фокус возвращается на элемент-триггер.
 * Underlying page не размонтируется - state не теряется.
 */
function SettingsDrawerBody() {
  const t = useT();
  const isMobile = useIsMobile();
  const hide = useSettingsDrawerStore((s) => s.hide);
  const asideRef = useRef<HTMLElement>(null);

  // Escape закрывает drawer. Слушатель на document - покрывает фокус
  // в любом месте внутри drawer'а (включая контролы шрифтов).
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') hide();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [hide]);

  // Focus trap + restore. Нативный <dialog> даёт это из коробки, но drawer -
  // кастомный <aside> (нужен end-side slide, не центр), поэтому реализуем
  // вручную: Tab/Shift+Tab на крайних focusable-элементах зацикливаются
  // внутрь, при unmount фокус возвращается на ранее сфокусированный элемент
  // (триггер открытия drawer'а).
  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null;
    const FOCUSABLE =
      'a[href],area[href],input:not([disabled]),select:not([disabled]),' +
      'textarea:not([disabled]),button:not([disabled]),[tabindex]:not([tabindex="-1"])';

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key !== 'Tab') return;
      const root = asideRef.current;
      if (!root) return;
      const focusable = Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
        (el) => el.offsetParent !== null || el === document.activeElement,
      );
      if (focusable.length === 0) {
        // Нет фокусируемых элементов - удерживаем фокус на самом drawer'е.
        e.preventDefault();
        return;
      }
      const first = focusable[0]!;
      const last = focusable[focusable.length - 1]!;
      const active = document.activeElement;
      if (e.shiftKey) {
        if (active === first || !root.contains(active)) {
          e.preventDefault();
          last.focus();
        }
      } else if (active === last || !root.contains(active)) {
        e.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      // Возврат фокуса на триггер (если он ещё в DOM и фокусируем).
      if (previouslyFocused && typeof previouslyFocused.focus === 'function') {
        previouslyFocused.focus();
      }
    };
  }, []);

  const widthClass = isMobile ? 'w-screen' : 'w-[440px] max-w-[92vw]';

  return (
    <>
      <div
        className="fixed inset-0 z-40 bg-black/40 backdrop-blur-[1px]"
        onClick={hide}
        data-testid="settings-drawer-backdrop"
        aria-hidden="true"
      />
      <aside
        ref={asideRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="settings-drawer-title"
        data-testid="settings-drawer"
        className={`fixed inset-y-0 end-0 z-50 flex ${widthClass} flex-col border-s border-border bg-bg shadow-sh4`}
      >
        <header className="flex flex-none items-center justify-between gap-3 border-b border-border px-5 py-3">
          <h2
            id="settings-drawer-title"
            className="min-w-0 flex-1 truncate text-base font-semibold text-ink-900"
          >
            {t('settings.drawer.title')}
          </h2>
          <IconButton
            icon={X}
            label={t('settings.drawer.close')}
            size="sm"
            onClick={hide}
            autoFocus
          />
        </header>

        <div className="flex-1 overflow-y-auto px-5 py-5">
          <FontSettings />
          <UserPreferencesSection />
        </div>
      </aside>
    </>
  );
}

/**
 * Контейнер - монтирует body только при open=true (idiom проекта).
 * Mounted в App.tsx один раз.
 */
function SettingsDrawer() {
  const open = useSettingsDrawerStore((s) => s.open);
  if (!open) return null;
  return <SettingsDrawerBody />;
}

export default SettingsDrawer;
