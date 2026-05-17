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

/**
 * matchMedia mock с singleton-by-query - useSyncExternalStore переcoздаёт
 * matchMedia call в getSnapshot после каждого notify, и если фабрика
 * возвращает новый instance с initial matches - тест зафиксируется
 * на initial значении. Singleton same query → updated matches
 */
function installMatchMedia(initialWidth: number): { dispatch: (m: boolean) => void } {
  const cache = new Map<string, MqlMock>();
  const factory = (query: string): MqlMock => {
    const cached = cache.get(query);
    if (cached) return cached;
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
    cache.set(query, instance);
    return instance;
  };
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: vi.fn(factory),
  });
  return {
    dispatch(m: boolean) {
      // dispatch на любом cached query - в тестах используем один query
      cache.forEach((mql) => mql.dispatch(m));
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
    act(() => ctx.dispatch(true));
    expect(result.current).toBe(true);
    act(() => ctx.dispatch(false));
    expect(result.current).toBe(false);
  });

  it('кастомный breakpoint применяется', () => {
    installMatchMedia(800);
    const { result } = renderHook(() => useIsMobile(BREAKPOINTS.lg));
    // 800 < 1024 → mobile при breakpoint = lg
    expect(result.current).toBe(true);
  });
});
