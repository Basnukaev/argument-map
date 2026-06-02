import { describe, it, expect, beforeEach } from 'vitest';
import { useSettingsDrawerStore } from './settingsDrawerStore';

describe('useSettingsDrawerStore', () => {
  beforeEach(() => {
    useSettingsDrawerStore.setState({ open: false });
  });

  it('начальное состояние - закрыто', () => {
    expect(useSettingsDrawerStore.getState().open).toBe(false);
  });

  it('show() ставит open=true', () => {
    useSettingsDrawerStore.getState().show();
    expect(useSettingsDrawerStore.getState().open).toBe(true);
  });

  it('hide() ставит open=false', () => {
    useSettingsDrawerStore.setState({ open: true });
    useSettingsDrawerStore.getState().hide();
    expect(useSettingsDrawerStore.getState().open).toBe(false);
  });

  it('toggle() переключает open', () => {
    expect(useSettingsDrawerStore.getState().open).toBe(false);
    useSettingsDrawerStore.getState().toggle();
    expect(useSettingsDrawerStore.getState().open).toBe(true);
    useSettingsDrawerStore.getState().toggle();
    expect(useSettingsDrawerStore.getState().open).toBe(false);
  });
});
