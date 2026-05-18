import { describe, it, expect, beforeEach } from 'vitest';
import { clearUserStorage } from './clearUserStorage';

describe('clearUserStorage', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('удаляет onboarding_dismissed', () => {
    window.localStorage.setItem('onboarding_dismissed', '1');
    clearUserStorage();
    expect(window.localStorage.getItem('onboarding_dismissed')).toBeNull();
  });

  it('удаляет app.preferences', () => {
    window.localStorage.setItem(
      'app.preferences',
      JSON.stringify({ locale: 'ru' }),
    );
    clearUserStorage();
    expect(window.localStorage.getItem('app.preferences')).toBeNull();
  });

  it('НЕ удаляет theme (ambient device pref)', () => {
    window.localStorage.setItem('theme', 'dark');
    clearUserStorage();
    expect(window.localStorage.getItem('theme')).toBe('dark');
  });

  it('НЕ удаляет argmap.layoutAlgorithm (device pref)', () => {
    window.localStorage.setItem('argmap.layoutAlgorithm', 'elk-layered');
    clearUserStorage();
    expect(window.localStorage.getItem('argmap.layoutAlgorithm')).toBe(
      'elk-layered',
    );
  });

  it('НЕ удаляет argmap.showEdgeLabels (viewport pref)', () => {
    window.localStorage.setItem('argmap.showEdgeLabels', 'true');
    clearUserStorage();
    expect(window.localStorage.getItem('argmap.showEdgeLabels')).toBe('true');
  });

  it('не падает если ключи отсутствуют', () => {
    // pre-condition: storage пуст. Должен no-op без exception
    expect(() => clearUserStorage()).not.toThrow();
  });
});
