import { describe, it, expect, beforeEach } from 'vitest';
import { usePaletteStore } from './paletteStore';

describe('usePaletteStore', () => {
  beforeEach(() => {
    // Reset state перед каждым тестом - zustand store сохраняет state между тестами
    usePaletteStore.setState({ open: false });
  });

  it('начальное состояние - закрыто', () => {
    expect(usePaletteStore.getState().open).toBe(false);
  });

  it('show() ставит open=true', () => {
    usePaletteStore.getState().show();
    expect(usePaletteStore.getState().open).toBe(true);
  });

  it('hide() ставит open=false', () => {
    usePaletteStore.setState({ open: true });
    usePaletteStore.getState().hide();
    expect(usePaletteStore.getState().open).toBe(false);
  });

  it('toggle() переключает open', () => {
    expect(usePaletteStore.getState().open).toBe(false);
    usePaletteStore.getState().toggle();
    expect(usePaletteStore.getState().open).toBe(true);
    usePaletteStore.getState().toggle();
    expect(usePaletteStore.getState().open).toBe(false);
  });

  it('show() идемпотентен - повторный вызов не меняет state', () => {
    usePaletteStore.getState().show();
    const first = usePaletteStore.getState().open;
    usePaletteStore.getState().show();
    expect(usePaletteStore.getState().open).toBe(first);
  });
});
