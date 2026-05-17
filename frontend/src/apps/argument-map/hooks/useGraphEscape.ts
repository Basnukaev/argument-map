import { useHotkey } from '@/shared/hooks/useHotkey';

interface Params {
  hasSelection: boolean;
  hasDetail: boolean;
  hasContextMenu: boolean;
  onClearSelection: () => void;
  onCloseDetail: () => void;
}

/**
 * Escape с очередью приоритетов:
 * 1. Если фокус в sidebar и есть detail-панель -> сразу закрыть её
 *    (пользователь кликнул в панель и хочет её скрыть)
 * 2. Иначе если есть выделение -> снять выделение
 * 3. Иначе если есть detail -> закрыть detail
 *
 * Modal (dialog[open]) и ContextMenu закрываются своими обработчиками -
 * мы их пропускаем чтобы не было двойной обработки Esc.
 */
export function useGraphEscape({
  hasSelection,
  hasDetail,
  hasContextMenu,
  onClearSelection,
  onCloseDetail,
}: Params) {
  useHotkey(
    'escape',
    () => {
      // нативный <dialog open> закроется сам (showModal API)
      if (document.querySelector('dialog[open]')) return;
      // ContextMenu имеет свой Esc-обработчик
      if (hasContextMenu) return;

      const active = document.activeElement;
      const inSidebar =
        active instanceof HTMLElement && active.closest('aside[role="complementary"]');

      if (inSidebar && hasDetail) {
        onCloseDetail();
        return;
      }
      if (hasSelection) {
        onClearSelection();
        return;
      }
      if (hasDetail) {
        onCloseDetail();
      }
    },
    { enableOnFormTags: true },
    [hasSelection, hasDetail, hasContextMenu, onClearSelection, onCloseDetail],
  );
}
