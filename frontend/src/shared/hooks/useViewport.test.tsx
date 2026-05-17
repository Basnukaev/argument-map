import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useIsMobile, BREAKPOINTS } from './useViewport';

type Listener = (e: MediaQueryListEvent) => void;

interface MqlMock {
  matches: boolean;
  media: string;
  listeners: Listener[];
  addEventListener: (type: 'change', l: Listener) => void;
  removeEventListener: (type: 'change', l: Listener) => void;
  dispatch: (matches: boolean) => void;
}

function installMatchMedia(initialWidth: number): { get: () => MqlMock } {
  let mql: MqlMock | null = null;
  const factory = (query: string): MqlMock => {
    const match = /max-width:\s*(\d+)px/.exec(query);
    const max = match ? Number(match[1]) : Infinity;
    const instance: MqlMock = {
      matches: initialWidth <= max,
      media: query,
      listeners: [],
      addEventListener(_type, l) {
        this.listeners.push(l);
      },
      removeEventListener(_type, l) {
        this.listeners = this.listeners.filter((x) => x !== l);
      },
      dispatch(matches: boolean) {
        this.matches = matches;
        this.listeners.forEach((l) => l({ matches } as MediaQueryListEvent));
      },
    };
    mql = instance;
    return instance;
  };
  vi.stubGlobal('matchMedia', vi.fn(factory));
  // window.matchMedia тоже надо подменить - jsdom использует window object
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: vi.fn(factory),
  });
  return {
    get: () => {
      if (!mql) throw new Error('matchMedia ещё не вызван');
      return mql;
    },
  };
}

describe('useIsMobile', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('возвращает true когда viewport уже мобильный (< md)', () => {
    installMatchMedia(400);
    const { result } = renderHook(() => useIsMobile());
    expect(result.current).toBe(true);
  });

  it('возвращает false когда viewport ≥ md (768px)', () => {
    installMatchMedia(1024);
    const { result } = renderHook(() => useIsMobile());
    expect(result.current).toBe(false);
  });

  it('обновляется при смене viewport через MediaQueryList event', () => {
    const ctx = installMatchMedia(1024);
    const { result } = renderHook(() => useIsMobile());
    expect(result.current).toBe(false);
    act(() => ctx.get().dispatch(true));
    expect(result.current).toBe(true);
    act(() => ctx.get().dispatch(false));
    expect(result.current).toBe(false);
  });

  it('кастомный breakpoint применяется', () => {
    installMatchMedia(800);
    const { result } = renderHook(() => useIsMobile(BREAKPOINTS.lg));
    // 800 < 1024 → mobile при breakpoint = lg
    expect(result.current).toBe(true);
  });
});
