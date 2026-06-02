import { describe, it, expect, beforeEach } from 'vitest';
import {
  useUiScaleStore,
  scaleMultiplier,
  UI_SCALE_VALUES,
  DEFAULT_UI_SCALE,
} from './uiScaleStore';

describe('uiScaleStore', () => {
  beforeEach(() => {
    window.localStorage.clear();
    useUiScaleStore.getState().reset();
  });

  it('дефолт - compact (владелец предпочитает ~10% меньше базового)', () => {
    expect(useUiScaleStore.getState().scale).toBe('compact');
    expect(DEFAULT_UI_SCALE).toBe('compact');
  });

  it('compact = 0.9, standard = 1.0 (базовый rollback), comfortable = 1.1', () => {
    expect(UI_SCALE_VALUES.compact).toBe(0.9);
    expect(UI_SCALE_VALUES.standard).toBe(1.0);
    expect(UI_SCALE_VALUES.comfortable).toBe(1.1);
  });

  it('setScale("standard") - one-click rollback к базе, персистит', () => {
    useUiScaleStore.getState().setScale('standard');
    expect(useUiScaleStore.getState().scale).toBe('standard');
    expect(window.localStorage.getItem('app.uiScale')).toBe('standard');
  });

  it('setScale("comfortable") персистит', () => {
    useUiScaleStore.getState().setScale('comfortable');
    expect(useUiScaleStore.getState().scale).toBe('comfortable');
    expect(window.localStorage.getItem('app.uiScale')).toBe('comfortable');
  });

  it('reset возвращает к compact-дефолту И очищает localStorage', () => {
    useUiScaleStore.getState().setScale('comfortable');
    useUiScaleStore.getState().reset();
    expect(useUiScaleStore.getState().scale).toBe('compact');
    expect(window.localStorage.getItem('app.uiScale')).toBeNull();
  });

  it('scaleMultiplier возвращает множитель пресета', () => {
    expect(scaleMultiplier('compact')).toBe(0.9);
    expect(scaleMultiplier('standard')).toBe(1.0);
    expect(scaleMultiplier('comfortable')).toBe(1.1);
  });
});
