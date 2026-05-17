import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';

/**
 * Тесты для themeStore (3-option mode: system/light/dark).
 *
 * Тонкость: store создаётся при первом import - читает localStorage в
 * initialMode. Поэтому используем dynamic import + resetModules для
 * пересоздания store в каждом тесте с нужным начальным state.
 */
describe('themeStore', () => {
  let mockMatches = false;

  beforeEach(() => {
    localStorage.clear();
    mockMatches = false;
    vi.stubGlobal(
      'matchMedia',
      (query: string) =>
        ({
          matches: query.includes('dark') ? mockMatches : false,
          media: query,
          onchange: null,
          addEventListener: vi.fn(),
          removeEventListener: vi.fn(),
          addListener: vi.fn(),
          removeListener: vi.fn(),
          dispatchEvent: () => false,
        }) as unknown as MediaQueryList,
    );
    // matchMedia должен быть на window для themeStore initialization
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      configurable: true,
      value: globalThis.matchMedia,
    });
    vi.resetModules();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('default mode = system без сохранения, effective = light на светлой системе', async () => {
    mockMatches = false;
    const { useThemeStore } = await import('./themeStore');
    const state = useThemeStore.getState();
    expect(state.mode).toBe('system');
    expect(state.effectiveTheme).toBe('light');
    expect(state.theme).toBe('light');
  });

  it('default mode = system с dark системой → effective = dark', async () => {
    mockMatches = true;
    const { useThemeStore } = await import('./themeStore');
    const state = useThemeStore.getState();
    expect(state.mode).toBe('system');
    expect(state.effectiveTheme).toBe('dark');
  });

  it('persisted dark restored как mode=dark', async () => {
    localStorage.setItem('app.theme', 'dark');
    const { useThemeStore } = await import('./themeStore');
    const state = useThemeStore.getState();
    expect(state.mode).toBe('dark');
    expect(state.effectiveTheme).toBe('dark');
  });

  it('persisted light игнорирует системный dark', async () => {
    mockMatches = true; // system хочет dark
    localStorage.setItem('app.theme', 'light');
    const { useThemeStore } = await import('./themeStore');
    const state = useThemeStore.getState();
    expect(state.mode).toBe('light');
    expect(state.effectiveTheme).toBe('light');
  });

  it('setMode persist в localStorage и обновляет effective', async () => {
    const { useThemeStore } = await import('./themeStore');
    useThemeStore.getState().setMode('dark');
    expect(localStorage.getItem('app.theme')).toBe('dark');
    expect(useThemeStore.getState().effectiveTheme).toBe('dark');

    useThemeStore.getState().setMode('system');
    expect(localStorage.getItem('app.theme')).toBe('system');
  });

  it('setMode(system) с темной системой даёт effective=dark', async () => {
    mockMatches = true;
    localStorage.setItem('app.theme', 'light');
    const { useThemeStore } = await import('./themeStore');
    expect(useThemeStore.getState().effectiveTheme).toBe('light');

    useThemeStore.getState().setMode('system');
    expect(useThemeStore.getState().effectiveTheme).toBe('dark');
  });

  it('legacy toggle переключает между light и dark', async () => {
    const { useThemeStore } = await import('./themeStore');
    useThemeStore.getState().setMode('light');
    useThemeStore.getState().toggle();
    expect(useThemeStore.getState().mode).toBe('dark');

    useThemeStore.getState().toggle();
    expect(useThemeStore.getState().mode).toBe('light');
  });

  it('legacy setTheme работает как setMode', async () => {
    const { useThemeStore } = await import('./themeStore');
    useThemeStore.getState().setTheme('dark');
    expect(useThemeStore.getState().mode).toBe('dark');
    expect(useThemeStore.getState().effectiveTheme).toBe('dark');
  });

  it('невалидное значение в localStorage → fallback system', async () => {
    localStorage.setItem('app.theme', 'invalid-value');
    const { useThemeStore } = await import('./themeStore');
    expect(useThemeStore.getState().mode).toBe('system');
  });
});
