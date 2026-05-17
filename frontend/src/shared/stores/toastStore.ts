import { create } from 'zustand';

export type ToastKind = 'error' | 'warning' | 'info' | 'success';

export interface ToastAction {
  label: string;
  onClick: () => void;
  /** Опциональный tooltip (HTML title) для action кнопки. Используется
   *  например для предупреждения "связи не восстанавливаются" в undo */
  hint?: string;
}

export interface Toast {
  id: string;
  kind: ToastKind;
  message: string;
  /** опциональная кнопка действия (например "Открыть") */
  action?: ToastAction;
  /** TTL в мс. Если не указан - автоматический по типу */
  ttl?: number;
}

interface ToastState {
  toasts: Toast[];
  show: (toast: Omit<Toast, 'id'>) => string;
  dismiss: (id: string) => void;
  clear: () => void;
}

const DEFAULT_TTL: Record<ToastKind, number> = {
  success: 3000,
  info: 4000,
  warning: 6000,
  error: 8000,
};

let nextId = 1;
const timers = new Map<string, ReturnType<typeof setTimeout>>();

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],

  show(input) {
    const id = `t${nextId++}`;
    const toast: Toast = { id, ...input };
    const ttl = input.ttl ?? DEFAULT_TTL[input.kind];
    set((s) => ({ toasts: [...s.toasts, toast] }));

    if (ttl > 0) {
      const timer = setTimeout(() => get().dismiss(id), ttl);
      timers.set(id, timer);
    }
    return id;
  },

  dismiss(id) {
    const timer = timers.get(id);
    if (timer) {
      clearTimeout(timer);
      timers.delete(id);
    }
    set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
  },

  clear() {
    for (const timer of timers.values()) clearTimeout(timer);
    timers.clear();
    set({ toasts: [] });
  },
}));

/** Удобный шорткат для вызова из любого callback без хука. */
export const toast = {
  error: (message: string, action?: ToastAction) =>
    useToastStore.getState().show({ kind: 'error', message, action }),
  warning: (message: string, action?: ToastAction) =>
    useToastStore.getState().show({ kind: 'warning', message, action }),
  info: (message: string, action?: ToastAction) =>
    useToastStore.getState().show({ kind: 'info', message, action }),
  success: (message: string, action?: ToastAction) =>
    useToastStore.getState().show({ kind: 'success', message, action }),
};
