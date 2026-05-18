import { describe, it, expect, beforeEach } from 'vitest';
import { useSourceDetailPanelStore } from './sourceDetailPanelStore';

describe('useSourceDetailPanelStore', () => {
  beforeEach(() => {
    // сброс store к начальному состоянию между тестами
    useSourceDetailPanelStore.setState({ current: null, isOpen: false });
  });

  it('default - panel закрыта, current=null', () => {
    const state = useSourceDetailPanelStore.getState();
    expect(state.isOpen).toBe(false);
    expect(state.current).toBeNull();
  });

  it('openWith - устанавливает current и isOpen=true', () => {
    useSourceDetailPanelStore.getState().openWith({
      sourceId: 'src-1',
      quote: 'lorem ipsum',
    });
    const state = useSourceDetailPanelStore.getState();
    expect(state.isOpen).toBe(true);
    expect(state.current).toEqual({ sourceId: 'src-1', quote: 'lorem ipsum' });
  });

  it('close - сбрасывает current в null и isOpen в false', () => {
    useSourceDetailPanelStore.getState().openWith({ sourceId: 'src-1' });
    useSourceDetailPanelStore.getState().close();
    const state = useSourceDetailPanelStore.getState();
    expect(state.isOpen).toBe(false);
    expect(state.current).toBeNull();
  });

  it('openWith при уже открытой panel - меняет current', () => {
    useSourceDetailPanelStore.getState().openWith({ sourceId: 'src-1' });
    useSourceDetailPanelStore.getState().openWith({ sourceId: 'src-2', quote: 'q2' });
    const state = useSourceDetailPanelStore.getState();
    expect(state.isOpen).toBe(true);
    expect(state.current).toEqual({ sourceId: 'src-2', quote: 'q2' });
  });
});
