import { describe, it, expect } from 'vitest';
import { StrictMode, type ReactNode } from 'react';
import { act, renderHook, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router';
import { http, HttpResponse, delay } from 'msw';
import { server } from '@/test/server';
import { usePagedList } from './usePagedList';

const BASE = 'http://test.local';

interface Item {
  id: string;
}

function paged(
  items: Item[],
  opts: { page?: number; totalPages?: number } = {},
) {
  const page = opts.page ?? 0;
  const totalPages = opts.totalPages ?? 1;
  return {
    items,
    page,
    size: 20,
    totalElements: totalPages * 20,
    totalPages,
    hasNext: page < totalPages - 1,
    hasPrev: page > 0,
  };
}

/** URL-builder для тестов: /api/v1/items?page=&size=&q= (page — 0-based бэк). */
function buildUrl(page: number, q: string): string {
  const params = new URLSearchParams();
  params.set('page', String(page));
  params.set('size', '20');
  if (q) params.set('q', q);
  return `/api/v1/items?${params.toString()}`;
}

/**
 * Wrapper-фабрика: MemoryRouter с заданным начальным URL (для deep-link
 * из ?page=). LocationSpy пишет текущий search в `seen` — проверяем что
 * goToPage / сброс действительно меняют URL.
 */
function wrapperWith(
  initialEntry: string,
  seen: { search: string },
  strict = false,
) {
  function LocationSpy() {
    const loc = useLocation();
    seen.search = loc.search;
    return null;
  }
  return function Wrapper({ children }: { children: ReactNode }) {
    const inner = (
      <MemoryRouter initialEntries={[initialEntry]}>
        {children}
        <LocationSpy />
      </MemoryRouter>
    );
    // strict=true оборачивает в StrictMode → React дважды вызывает эффекты
    // на mount (как dev-сервер). Нужно для регресс-теста deep-link.
    return strict ? <StrictMode>{inner}</StrictMode> : inner;
  };
}

describe('usePagedList', () => {
  it('загружает первую страницу: loading → success', async () => {
    server.use(
      http.get(`${BASE}/api/v1/items`, () =>
        HttpResponse.json(paged([{ id: 'a' }])),
      ),
    );
    const seen = { search: '' };
    const { result } = renderHook(() => usePagedList<Item>({ buildUrl }), {
      wrapper: wrapperWith('/items', seen),
    });

    expect(result.current.state.kind).toBe('loading');
    expect(result.current.page).toBe(1);
    await waitFor(() => expect(result.current.state.kind).toBe('success'));
    if (result.current.state.kind === 'success') {
      expect(result.current.state.data.items).toEqual([{ id: 'a' }]);
    }
  });

  it('init из ?page=3: грузит третью страницу (бэк page=2)', async () => {
    const requestedPages: (string | null)[] = [];
    server.use(
      http.get(`${BASE}/api/v1/items`, ({ request }) => {
        const p = new URL(request.url).searchParams.get('page');
        requestedPages.push(p);
        return HttpResponse.json(
          paged([{ id: `page-${p}` }], { page: Number(p), totalPages: 5 }),
        );
      }),
    );
    const seen = { search: '' };
    const { result } = renderHook(() => usePagedList<Item>({ buildUrl }), {
      wrapper: wrapperWith('/items?page=3', seen),
    });

    expect(result.current.page).toBe(3);
    await waitFor(() => {
      if (result.current.state.kind !== 'success') throw new Error('not success');
      // ?page=3 (1-based) → бэк page=2 (0-based)
      expect(result.current.state.data.items).toEqual([{ id: 'page-2' }]);
    });
    expect(requestedPages).toContain('2');
  });

  it('init из ?page=3 под StrictMode: deep-link переживает double-invoke (не сброс на стр.1)', async () => {
    // Регрессия: «скип первого прогона» ref сбрасывал страницу на ВТОРОМ
    // (StrictMode) вызове mount-эффекта → ?page= терялся, грузилась стр.1.
    // Тут StrictMode включён — эффекты двоятся как на dev-сервере.
    const requestedPages: (string | null)[] = [];
    server.use(
      http.get(`${BASE}/api/v1/items`, ({ request }) => {
        const p = new URL(request.url).searchParams.get('page');
        requestedPages.push(p);
        return HttpResponse.json(
          paged([{ id: `page-${p}` }], { page: Number(p), totalPages: 5 }),
        );
      }),
    );
    const seen = { search: '' };
    const { result } = renderHook(() => usePagedList<Item>({ buildUrl }), {
      wrapper: wrapperWith('/items?page=3', seen, true), // StrictMode ON
    });

    expect(result.current.page).toBe(3);
    await waitFor(() => {
      if (result.current.state.kind !== 'success') throw new Error('not success');
      // ?page=3 (1-based) → бэк page=2; сброса на стр.1 НЕ произошло
      expect(result.current.state.data.items).toEqual([{ id: 'page-2' }]);
    });
    expect(result.current.page).toBe(3);
    expect(seen.search).toContain('page=3');
    // Ключевое: бэк page=0 НИКОГДА не запрашивался (иначе был бы сброс).
    expect(requestedPages).not.toContain('0');
  });

  it('goToPage меняет ?page= в URL и грузит ту страницу (REPLACE items)', async () => {
    server.use(
      http.get(`${BASE}/api/v1/items`, ({ request }) => {
        const p = Number(new URL(request.url).searchParams.get('page') ?? '0');
        return HttpResponse.json(
          paged([{ id: `item-${p}` }], { page: p, totalPages: 5 }),
        );
      }),
    );
    const seen = { search: '' };
    const { result } = renderHook(() => usePagedList<Item>({ buildUrl }), {
      wrapper: wrapperWith('/items', seen),
    });
    await waitFor(() => {
      if (result.current.state.kind !== 'success') throw new Error('not success');
      expect(result.current.state.data.items).toEqual([{ id: 'item-0' }]);
    });

    act(() => result.current.goToPage(3));

    // URL содержит ?page=3 (1-based)
    await waitFor(() => expect(seen.search).toContain('page=3'));
    // items ЗАМЕНЕНЫ на страницу 3 (бэк page=2), не аппендятся
    await waitFor(() => {
      if (result.current.state.kind !== 'success') throw new Error('not success');
      expect(result.current.state.data.items).toEqual([{ id: 'item-2' }]);
    });
    expect(result.current.page).toBe(3);
  });

  it('goToPage(1) убирает ?page= из URL (чистый URL для первой)', async () => {
    server.use(
      http.get(`${BASE}/api/v1/items`, ({ request }) => {
        const p = Number(new URL(request.url).searchParams.get('page') ?? '0');
        return HttpResponse.json(paged([{ id: `i-${p}` }], { page: p, totalPages: 5 }));
      }),
    );
    const seen = { search: '' };
    const { result } = renderHook(() => usePagedList<Item>({ buildUrl }), {
      wrapper: wrapperWith('/items?page=4', seen),
    });
    await waitFor(() => expect(result.current.state.kind).toBe('success'));

    act(() => result.current.goToPage(1));

    await waitFor(() => expect(result.current.page).toBe(1));
    expect(seen.search).not.toContain('page=');
  });

  it('смена query сбрасывает на стр.1 и убирает ?page=', async () => {
    const requestedPages: Array<{ page: string | null; q: string | null }> = [];
    server.use(
      http.get(`${BASE}/api/v1/items`, ({ request }) => {
        const u = new URL(request.url).searchParams;
        requestedPages.push({ page: u.get('page'), q: u.get('q') });
        return HttpResponse.json(
          paged([{ id: 'x' }], { page: Number(u.get('page')), totalPages: 5 }),
        );
      }),
    );
    const seen = { search: '' };
    const { result } = renderHook(
      () => usePagedList<Item>({ buildUrl, debounceMs: 0 }),
      { wrapper: wrapperWith('/items?page=3', seen) },
    );
    await waitFor(() => expect(result.current.state.kind).toBe('success'));
    expect(result.current.page).toBe(3);

    act(() => result.current.setSearchInput('malik'));

    // ?page= ушёл (сброс на стр.1), q применился
    await waitFor(() => expect(result.current.page).toBe(1));
    await waitFor(() => {
      expect(
        requestedPages.some((r) => r.q === 'malik' && r.page === '0'),
      ).toBe(true);
    });
    expect(seen.search).not.toContain('page=');
  });

  it('смена deps (фильтр) сбрасывает на стр.1', async () => {
    const calls: Array<{ page: string | null; status: string | null }> = [];
    server.use(
      http.get(`${BASE}/api/v1/items`, ({ request }) => {
        const u = new URL(request.url).searchParams;
        calls.push({ page: u.get('page'), status: u.get('status') });
        return HttpResponse.json(
          paged([{ id: 'x' }], { page: Number(u.get('page')), totalPages: 5 }),
        );
      }),
    );
    const seen = { search: '' };
    const { result, rerender } = renderHook(
      ({ status }: { status: string }) =>
        usePagedList<Item>({
          buildUrl: (page, q) => {
            const params = new URLSearchParams();
            params.set('page', String(page));
            if (q) params.set('q', q);
            if (status !== 'ALL') params.set('status', status);
            return `/api/v1/items?${params.toString()}`;
          },
          deps: [status],
        }),
      {
        wrapper: wrapperWith('/items?page=3', seen),
        initialProps: { status: 'ALL' },
      },
    );
    await waitFor(() => expect(result.current.state.kind).toBe('success'));
    expect(result.current.page).toBe(3);

    rerender({ status: 'CANONICAL' });

    // фильтр сменился → сброс на стр.1, запрос page=0 со status=CANONICAL
    await waitFor(() => expect(result.current.page).toBe(1));
    await waitFor(() => {
      expect(
        calls.some((c) => c.status === 'CANONICAL' && c.page === '0'),
      ).toBe(true);
    });
  });

  it('стейл-ответ устаревшей страницы игнорируется (быстрый goToPage)', async () => {
    // page 2 (бэк page=1) отвечает медленно, page 3 (бэк page=2) — быстро.
    // Уходим на 2, тут же на 3: ответ 3 должен победить, поздний ответ 2 —
    // игнорироваться (иначе перетёр бы свежую страницу).
    server.use(
      http.get(`${BASE}/api/v1/items`, async ({ request }) => {
        const p = Number(new URL(request.url).searchParams.get('page') ?? '0');
        if (p === 1) {
          await delay(120);
          return HttpResponse.json(
            paged([{ id: 'stale-page2' }], { page: 1, totalPages: 5 }),
          );
        }
        return HttpResponse.json(
          paged([{ id: `item-${p}` }], { page: p, totalPages: 5 }),
        );
      }),
    );
    const seen = { search: '' };
    const { result } = renderHook(() => usePagedList<Item>({ buildUrl }), {
      wrapper: wrapperWith('/items', seen),
    });
    await waitFor(() => {
      if (result.current.state.kind !== 'success') throw new Error('not success');
      expect(result.current.state.data.items).toEqual([{ id: 'item-0' }]);
    });

    act(() => result.current.goToPage(2));
    act(() => result.current.goToPage(3));

    // Свежая страница 3 (бэк page=2) приходит первой (без задержки).
    await waitFor(() => {
      if (result.current.state.kind !== 'success') throw new Error('not success');
      expect(result.current.state.data.items).toEqual([{ id: 'item-2' }]);
    });

    // Ждём пока отложенный stale-ответ page 2 точно прилетит (120ms+).
    await new Promise((r) => setTimeout(r, 250));

    // Stale НЕ перетёр свежую страницу 3.
    if (result.current.state.kind === 'success') {
      expect(result.current.state.data.items).toEqual([{ id: 'item-2' }]);
    }
    expect(result.current.page).toBe(3);
  });
});
