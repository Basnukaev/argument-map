import { create } from 'zustand';

/**
 * Глобальный state Settings Drawer (правый slide-over с настройками).
 * Хранится в zustand чтобы trigger жил где угодно (Header gear, command
 * palette, hotkey в App.tsx), а сам drawer рендерился один раз на уровне
 * App поверх текущей страницы - закрытие возвращает пользователя ровно
 * туда где он был, без route-навигации (баг #1: full-page /settings
 * терял контекст).
 *
 * Зеркалит paletteStore - тот же container-on-App паттерн.
 */
interface SettingsDrawerState {
  open: boolean;
  toggle: () => void;
  show: () => void;
  hide: () => void;
}

export const useSettingsDrawerStore = create<SettingsDrawerState>((set) => ({
  open: false,
  toggle: () => set((s) => ({ open: !s.open })),
  show: () => set({ open: true }),
  hide: () => set({ open: false }),
}));
