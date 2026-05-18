import { describe, it, expect, beforeEach } from 'vitest';
import {
  useFontPairStore,
  findPair,
  findArabicFont,
  FONT_PAIRS,
  ARABIC_FONTS,
  DEFAULT_PAIR_ID,
  DEFAULT_ARABIC_FONT_ID,
  DEFAULT_TITLE_WEIGHT,
  DEFAULT_BODY_WEIGHT,
  DEFAULT_DENSITY,
} from './fontPairStore';

describe('fontPairStore helpers', () => {
  describe('findPair', () => {
    it('возвращает FontPair по существующему id', () => {
      const pair = findPair('inter-lora');
      expect(pair.id).toBe('inter-lora');
      expect(pair.name).toContain('Inter');
    });

    it('возвращает default pair для несуществующего id (defensive fallback)', () => {
      // Защита от corrupt localStorage / outdated client schemas
      const pair = findPair('NOT_REAL_PAIR_ID_XYZ');
      expect(pair.id).toBe(FONT_PAIRS[0]!.id);
    });
  });

  describe('findArabicFont', () => {
    it('возвращает ArabicFont по существующему id', () => {
      const font = findArabicFont('scheherazade');
      expect(font.id).toBe('scheherazade');
    });

    it('возвращает default для несуществующего id', () => {
      const font = findArabicFont('NOT_REAL_FONT');
      expect(font.id).toBe(ARABIC_FONTS[0]!.id);
    });
  });
});

describe('useFontPairStore', () => {
  beforeEach(() => {
    window.localStorage.clear();
    // Reset store - resetAll очищает + ставит дефолты
    useFontPairStore.getState().resetAll();
  });

  it('начальное состояние - дефолты из констант', () => {
    const state = useFontPairStore.getState();
    expect(state.pairId).toBe(DEFAULT_PAIR_ID);
    expect(state.titleWeight).toBe(DEFAULT_TITLE_WEIGHT);
    expect(state.bodyWeight).toBe(DEFAULT_BODY_WEIGHT);
    expect(state.density).toBe(DEFAULT_DENSITY);
    expect(state.arabicFontId).toBe(DEFAULT_ARABIC_FONT_ID);
  });

  it('setPair персистит в localStorage', () => {
    useFontPairStore.getState().setPair('inter-lora');
    expect(useFontPairStore.getState().pairId).toBe('inter-lora');
    expect(window.localStorage.getItem('app.fontPair')).toBe('inter-lora');
  });

  it('setTitleWeight персистит', () => {
    useFontPairStore.getState().setTitleWeight(700);
    expect(useFontPairStore.getState().titleWeight).toBe(700);
    expect(window.localStorage.getItem('app.titleWeight')).toBe('700');
  });

  it('setArabicFont персистит и обновляет state', () => {
    useFontPairStore.getState().setArabicFont('scheherazade');
    expect(useFontPairStore.getState().arabicFontId).toBe('scheherazade');
    expect(window.localStorage.getItem('app.arabicFont')).toBe('scheherazade');
  });

  it('setDensity персистит', () => {
    useFontPairStore.getState().setDensity(1.1);
    expect(useFontPairStore.getState().density).toBe(1.1);
    expect(window.localStorage.getItem('app.density')).toBe('1.1');
  });

  it('resetAll возвращает все настройки к дефолту И очищает localStorage', () => {
    const store = useFontPairStore.getState();
    store.setPair('inter-lora');
    store.setTitleWeight(800);
    store.setBodyWeight(500);
    store.setArabicFont('scheherazade');
    store.setDensity(1.1);

    useFontPairStore.getState().resetAll();

    const state = useFontPairStore.getState();
    expect(state.pairId).toBe(DEFAULT_PAIR_ID);
    expect(state.titleWeight).toBe(DEFAULT_TITLE_WEIGHT);
    expect(state.bodyWeight).toBe(DEFAULT_BODY_WEIGHT);
    expect(state.density).toBe(DEFAULT_DENSITY);
    expect(state.arabicFontId).toBe(DEFAULT_ARABIC_FONT_ID);

    expect(window.localStorage.getItem('app.fontPair')).toBeNull();
    expect(window.localStorage.getItem('app.titleWeight')).toBeNull();
    expect(window.localStorage.getItem('app.bodyWeight')).toBeNull();
    expect(window.localStorage.getItem('app.density')).toBeNull();
    expect(window.localStorage.getItem('app.arabicFont')).toBeNull();
  });
});
