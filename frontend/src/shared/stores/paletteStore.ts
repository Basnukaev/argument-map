import { create } from 'zustand';

/**
 * Глобальный state Command Palette (Cmd+K). Хранится в zustand
 * чтобы listener жил в App.tsx и работал на любом route - включая
 * TopicGraphPage у которого свой top-bar без AppHeader, и поэтому
 * локальный useState в Header'е не сработал бы (Header не монтируется
 * на этой странице).
 */
interface PaletteState {
  open: boolean;
  toggle: () => void;
  show: () => void;
  hide: () => void;
}

export const usePaletteStore = create<PaletteState>((set) => ({
  open: false,
  toggle: () => set((s) => ({ open: !s.open })),
  show: () => set({ open: true }),
  hide: () => set({ open: false }),
}));
