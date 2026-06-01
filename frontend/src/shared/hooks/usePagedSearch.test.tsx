import { describe, it, expect } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse, delay } from 'msw';
import { server } from '@/test/server';
import { usePagedSearch } from './usePagedSearch';

const BASE = 'http://test.local';

interface Item {
  id: string;
}

function paged(
  items: Item[],
  opts: { page?: number; hasNext?: boolean } = {},
) {
  return {
    items,
    page: opts.page ?? 0,
    size: 20,
    totalElements: items.length,
    totalPages: opts.hasNext ? 2 : 1,
    hasNext: opts.hasNext ?? false,
  };
}

/** URL-builder для тестов: /api/v1/items?page=&size=&q= */
function buildUrl(page: number, q: string): string {
  const params = new URLSearchParams();
  params.set('page', String(page));
  params.set('size', '20');
  if (q) params.set('q', q);
  return `/api/v1/items?${params.toString()}`;
}

describe('usePagedSearch', () => {
  it('загружает первую страницу: loading → success', async () => {
    server.use(
      http.get(`${BASE}/api/v1/items`, () =>
        HttpResponse.json(paged([{ id: 'a' }])),
      ),
    );

    const { result } = renderHook(() => usePagedSearch<Item>({ buildUrl }));

    expect(result.current.state.kind).toBe('loading');
    await waitFor(() => expect(result.current.state.kind).toBe('success'));
    if (result.current.state.kind === 'success') {
      expect(result.current.state.data.items).toEqual([{ id: 'a' }]);
    }
  });

  it('debounce: один запрос после серии keystroke (ровно финальный q)', async () => {
    const queries: string[] = [];
    server.use(
      http.get(`${BASE}/api/v1/items`, ({ request }) => {
        const q = new URL(request.url).searchParams.get('q');
        if (q) queries.push(q);
        return HttpResponse.json(paged([{ id: 'a' }]));
      }),
    );

    const { result } = renderHook(() => usePagedSearch<Item>({ buildUrl }));
    await waitFor(() => expect(result.current.state.kind).toBe('success'));

    // 4 «keystroke» быстрее 300ms debounce → промежуточные значения
    // (ма/мал/мали) не должны слать запрос, только финальный «малик».
    act(() => result.current.setSearchInput('ма'));
    act(() => result.current.setSearchInput('мал'));
    act(() => result.current.setSearchInput('мали'));
    act(() => result.current.setSearchInput('малик'));

    await waitFor(
      () => {
        expect(queries[queries.length - 1]).toBe('малик');
      },
      { timeout: 800 },
    );

    // Suppression: ровно один q-запрос, а не по одному на keystroke.
    await new Promise((r) => setTimeout(r, 350));
    expect(queries).toEqual(['малик']);
  });

  it('Load More: page 1 аппендится к page 0 (не заменяет)', async () => {
    server.use(
      http.get(`${BASE}/api/v1/items`, ({ request }) => {
        const page = new URL(request.url).searchParams.get('page');
        if (page === '1') {
          return HttpResponse.json(paged([{ id: 'b' }], { page: 1, hasNext: false }));
        }
        return HttpResponse.json(paged([{ id: 'a' }], { page: 0, hasNext: true }));
      }),
    );

    const { result } = renderHook(() => usePagedSearch<Item>({ buildUrl }));
    await waitFor(() => expect(result.current.state.kind).toBe('success'));

    act(() => result.current.loadMore());

    await waitFor(() => {
      if (result.current.state.kind !== 'success') throw new Error('not success');
      expect(result.current.state.data.items).toEqual([{ id: 'a' }, { id: 'b' }]);
    });
    // hasNext обновился из ответа page 1
    if (result.current.state.kind === 'success') {
      expect(result.current.state.data.hasNext).toBe(false);
    }
  });

  it('stale-append race: смена query пока loadMore in-flight → stale ответ игнорируется', async () => {
    // page 1 (старый query) отвечает с задержкой, чтобы query успел
    // смениться до прихода ответа. Свежий page 0 (новый query «x»)
    // должен победить, а stale page-1 items НЕ приклеиться.
    server.use(
      http.get(`${BASE}/api/v1/items`, async ({ request }) => {
        const url = new URL(request.url);
        const page = url.searchParams.get('page');
        const q = url.searchParams.get('q');
        if (page === '1') {
          await delay(120);
          return HttpResponse.json(
            paged([{ id: 'stale-page1' }], { page: 1, hasNext: false }),
          );
        }
        if (q === 'x') {
          return HttpResponse.json(
            paged([{ id: 'fresh-x' }], { page: 0, hasNext: false }),
          );
        }
        return HttpResponse.json(paged([{ id: 'a' }], { page: 0, hasNext: true }));
      }),
    );

    // debounceMs: 0 → fresh-x refetch стартует немедленно и приходит
    // ПЕРВЫМ (page 0 без задержки), а stale page-1 (delay 120ms) — позже.
    // Так гарантируем что stale ответ пытается аппендиться УЖЕ ПОСЛЕ
    // того как fresh-x занял state — именно это ловит race-guard.
    const { result } = renderHook(() =>
      usePagedSearch<Item>({ buildUrl, debounceMs: 0 }),
    );
    await waitFor(() => expect(result.current.state.kind).toBe('success'));

    // Issue loadMore (page 1, in-flight 120ms), затем сразу меняем query.
    act(() => result.current.loadMore());
    act(() => result.current.setSearchInput('x'));

    // Свежий запрос по «x» приходит первым (page 0, без задержки).
    await waitFor(
      () => {
        if (result.current.state.kind !== 'success') throw new Error('not success');
        expect(result.current.state.data.items).toEqual([{ id: 'fresh-x' }]);
      },
      { timeout: 800 },
    );

    // Ждём пока отложенный stale page-1 ответ точно прилетит (120ms+).
    await new Promise((r) => setTimeout(r, 250));

    // Stale items НЕ приклеились — список остался свежим page-0.
    if (result.current.state.kind === 'success') {
      expect(result.current.state.data.items).toEqual([{ id: 'fresh-x' }]);
      expect(
        result.current.state.data.items.some((i) => i.id === 'stale-page1'),
      ).toBe(false);
    }
  });

  it('refetch при смене deps (фильтр) с page 0', async () => {
    const calls: Array<{ q: string | null; status: string | null }> = [];
    server.use(
      http.get(`${BASE}/api/v1/items`, ({ request }) => {
        const url = new URL(request.url);
        calls.push({
          q: url.searchParams.get('q'),
          status: url.searchParams.get('status'),
        });
        return HttpResponse.json(paged([{ id: 'a' }]));
      }),
    );

    const { result, rerender } = renderHook(
      ({ status }: { status: string }) =>
        usePagedSearch<Item>({
          buildUrl: (page, q) => {
            const params = new URLSearchParams();
            params.set('page', String(page));
            if (q) params.set('q', q);
            if (status !== 'ALL') params.set('status', status);
            return `/api/v1/items?${params.toString()}`;
          },
          deps: [status],
        }),
      { initialProps: { status: 'ALL' } },
    );

    await waitFor(() => expect(result.current.state.kind).toBe('success'));

    rerender({ status: 'CANONICAL' });

    await waitFor(() => {
      expect(calls.some((c) => c.status === 'CANONICAL')).toBe(true);
    });
  });
});
