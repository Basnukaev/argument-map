import { create } from 'zustand';

/**
 * Хранилище мульти-выделения в графе аргументации. Источник истины
 * для FloatingActionBar и bulk-операций (delete, change status).
 *
 * Selection живёт **параллельно** с RF-state (`selected` флаги на nodes/
 * edges). React Flow синхронизирует свой state через `onSelectionChange`,
 * мы зеркалим это в store чтобы:
 *
 * - FloatingActionBar мог подписаться без проп-drilling через RF
 * - bulk-операции читали selection вне рендера (handler в useCallback)
 * - keyboard shortcuts (Esc / Del) могли очищать или действовать
 *
 * Используем `Set<string>` а не массив - O(1) проверки `has(id)`
 * (нужны для filter в bulk operations) при разумном API
 */
interface GraphSelectionState {
  selectedNodeIds: Set<string>;
  selectedEdgeIds: Set<string>;
  /** Заменить selection целиком (вызывается из `onSelectionChange` RF). */
  setSelection: (nodeIds: string[], edgeIds: string[]) => void;
  /** Снять выделение (Esc, после bulk-операции). */
  clearSelection: () => void;
  /** Убрать один id из selection (например удалённый узел). */
  removeFromSelection: (id: string) => void;
}

function sameSet(a: Set<string>, b: Set<string>): boolean {
  if (a.size !== b.size) return false;
  for (const id of a) if (!b.has(id)) return false;
  return true;
}

export const useGraphSelectionStore = create<GraphSelectionState>((set, get) => ({
  selectedNodeIds: new Set(),
  selectedEdgeIds: new Set(),

  setSelection(nodeIds, edgeIds) {
    const nextNodes = new Set(nodeIds);
    const nextEdges = new Set(edgeIds);
    const prev = get();
    // identity-preserving update - если содержимое не изменилось, Zustand
    // не сделает render. RF дёргает onSelectionChange на каждое setNodes
    // даже если выделение фактически прежнее
    if (sameSet(prev.selectedNodeIds, nextNodes) && sameSet(prev.selectedEdgeIds, nextEdges)) {
      return;
    }
    set({ selectedNodeIds: nextNodes, selectedEdgeIds: nextEdges });
  },

  clearSelection() {
    const prev = get();
    if (prev.selectedNodeIds.size === 0 && prev.selectedEdgeIds.size === 0) return;
    set({ selectedNodeIds: new Set(), selectedEdgeIds: new Set() });
  },

  removeFromSelection(id) {
    const prev = get();
    if (!prev.selectedNodeIds.has(id) && !prev.selectedEdgeIds.has(id)) return;
    const nextNodes = new Set(prev.selectedNodeIds);
    const nextEdges = new Set(prev.selectedEdgeIds);
    nextNodes.delete(id);
    nextEdges.delete(id);
    set({ selectedNodeIds: nextNodes, selectedEdgeIds: nextEdges });
  },
}));
