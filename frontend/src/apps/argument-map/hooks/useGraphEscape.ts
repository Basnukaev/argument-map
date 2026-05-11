import { useEffect } from 'react';

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
  useEffect(() => {
    function onEsc(e: KeyboardEvent) {
      if (e.key !== 'Escape') return;

      // нативный <dialog open> закроется сам (showModal API)
      if (document.querySelector('dialog[open]')) return;
      // ContextMenu имеет свой Esc-обработчик
      if (hasContextMenu) return;

      const active = document.activeElement;
      const inSidebar =
        active instanceof HTMLElement && active.closest('aside[role="complementary"]');

      if (inSidebar && hasDetail) {
        onCloseDetail();
        e.preventDefault();
        return;
      }
      if (hasSelection) {
        onClearSelection();
        e.preventDefault();
        return;
      }
      if (hasDetail) {
        onCloseDetail();
        e.preventDefault();
      }
    }
    document.addEventListener('keydown', onEsc);
    return () => document.removeEventListener('keydown', onEsc);
  }, [hasSelection, hasDetail, hasContextMenu, onClearSelection, onCloseDetail]);
}
