import { create } from 'zustand';

export interface ConfirmOptions {
  /** Заголовок диалога; по умолчанию — common.confirm_title */
  title?: string;
  message: string;
  /** Подпись кнопки подтверждения; по умолчанию — common.confirm */
  confirmLabel?: string;
  /** Подпись кнопки отмены; по умолчанию — common.cancel */
  cancelLabel?: string;
  /** Опасное действие — красная кнопка подтверждения */
  danger?: boolean;
}

interface ConfirmRequest extends ConfirmOptions {
  id: number;
  resolve: (result: boolean) => void;
}

interface ConfirmState {
  request: ConfirmRequest | null;
  open: (options: ConfirmOptions) => Promise<boolean>;
  /** Резолвит текущий запрос и очищает (вызывается из ConfirmDialog). */
  settle: (result: boolean) => void;
}

let nextId = 1;

/**
 * Promise-based confirm — замена блокирующего `window.confirm` на стилизованный
 * тестируемый диалог. Императивный API (как у toastStore): открывается из любого
 * обработчика без хука, реактивный host `ConfirmDialog` подписан на `request`.
 */
export const useConfirmStore = create<ConfirmState>((set, get) => ({
  request: null,
  open: (options) =>
    new Promise<boolean>((resolve) => {
      // Если уже открыт другой запрос — резолвим его как false (отмена),
      // чтобы висящий промис не остался неразрешённым.
      const current = get().request;
      if (current) current.resolve(false);
      set({ request: { ...options, id: nextId++, resolve } });
    }),
  settle: (result) => {
    const current = get().request;
    if (current) current.resolve(result);
    set({ request: null });
  },
}));

/**
 * Императивный confirm. Использование:
 * `if (!(await askConfirm({ message: t('...'), danger: true }))) return;`
 */
export function askConfirm(options: ConfirmOptions): Promise<boolean> {
  return useConfirmStore.getState().open(options);
}
