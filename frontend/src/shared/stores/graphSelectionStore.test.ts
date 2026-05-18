import { describe, it, expect, beforeEach } from 'vitest';
import { useGraphSelectionStore } from './graphSelectionStore';

describe('useGraphSelectionStore', () => {
  beforeEach(() => {
    useGraphSelectionStore.getState().clearSelection();
  });

  it('default - пустые сеты', () => {
    const s = useGraphSelectionStore.getState();
    expect(s.selectedNodeIds.size).toBe(0);
    expect(s.selectedEdgeIds.size).toBe(0);
  });

  it('setSelection - заполняет сеты по массивам', () => {
    useGraphSelectionStore.getState().setSelection(['n1', 'n2'], ['e1']);
    const s = useGraphSelectionStore.getState();
    expect([...s.selectedNodeIds].sort()).toEqual(['n1', 'n2']);
    expect([...s.selectedEdgeIds]).toEqual(['e1']);
  });

  it('setSelection с тем же содержимым - identity-preserving (нет нового сета)', () => {
    useGraphSelectionStore.getState().setSelection(['n1', 'n2'], []);
    const setBefore = useGraphSelectionStore.getState().selectedNodeIds;
    useGraphSelectionStore.getState().setSelection(['n2', 'n1'], []);
    const setAfter = useGraphSelectionStore.getState().selectedNodeIds;
    expect(setAfter).toBe(setBefore);
  });

  it('setSelection с новым содержимым - создаёт новый сет', () => {
    useGraphSelectionStore.getState().setSelection(['n1'], []);
    const setBefore = useGraphSelectionStore.getState().selectedNodeIds;
    useGraphSelectionStore.getState().setSelection(['n1', 'n2'], []);
    const setAfter = useGraphSelectionStore.getState().selectedNodeIds;
    expect(setAfter).not.toBe(setBefore);
    expect(setAfter.size).toBe(2);
  });

  it('clearSelection - сбрасывает оба сета', () => {
    useGraphSelectionStore.getState().setSelection(['n1'], ['e1']);
    useGraphSelectionStore.getState().clearSelection();
    const s = useGraphSelectionStore.getState();
    expect(s.selectedNodeIds.size).toBe(0);
    expect(s.selectedEdgeIds.size).toBe(0);
  });

  it('removeFromSelection - убирает один id и из node-сета и из edge-сета', () => {
    useGraphSelectionStore.getState().setSelection(['n1', 'n2'], ['e1']);
    useGraphSelectionStore.getState().removeFromSelection('n1');
    const s = useGraphSelectionStore.getState();
    expect(s.selectedNodeIds.has('n1')).toBe(false);
    expect(s.selectedNodeIds.has('n2')).toBe(true);
    expect(s.selectedEdgeIds.has('e1')).toBe(true);
  });

  it('removeFromSelection отсутствующего id - не меняет identity сетов', () => {
    useGraphSelectionStore.getState().setSelection(['n1'], ['e1']);
    const nodesBefore = useGraphSelectionStore.getState().selectedNodeIds;
    const edgesBefore = useGraphSelectionStore.getState().selectedEdgeIds;
    useGraphSelectionStore.getState().removeFromSelection('unknown');
    const s = useGraphSelectionStore.getState();
    expect(s.selectedNodeIds).toBe(nodesBefore);
    expect(s.selectedEdgeIds).toBe(edgesBefore);
  });
});
