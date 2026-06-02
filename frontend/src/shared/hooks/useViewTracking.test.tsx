import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { useViewTracking } from './useViewTracking';

const BASE = 'http://test.local';
const TOPIC_ID = '11111111-1111-1111-1111-111111111111';

describe('useViewTracking', () => {
  beforeEach(() => {
    window.sessionStorage.clear();
  });

  it('POSTs view increment на mount если id присутствует', async () => {
    let called = false;
    server.use(
      http.post(`${BASE}/api/v1/topics/${TOPIC_ID}/views`, () => {
        called = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderHook(() => useViewTracking('topics', TOPIC_ID));

    // Hook fire-and-forget - даём micro-task завершиться
    await vi.waitFor(() => expect(called).toBe(true));
  });

  it('НЕ POSTs если id == null', async () => {
    let called = false;
    server.use(
      http.post(`${BASE}/api/v1/topics/${TOPIC_ID}/views`, () => {
        called = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderHook(() => useViewTracking('topics', null));

    // Wait small time + check no POST happened
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(called).toBe(false);
  });

  it('per-session dedup - повторный mount той же entity → only one POST', async () => {
    let postCount = 0;
    server.use(
      http.post(`${BASE}/api/v1/topics/${TOPIC_ID}/views`, () => {
        postCount += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    const { unmount } = renderHook(() => useViewTracking('topics', TOPIC_ID));
    await vi.waitFor(() => expect(postCount).toBe(1));
    unmount();

    // Second mount of same entity in same session
    renderHook(() => useViewTracking('topics', TOPIC_ID));
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Только 1 POST (dedup через sessionStorage)
    expect(postCount).toBe(1);
  });

  it('silent fail на network error', async () => {
    server.use(
      http.post(`${BASE}/api/v1/topics/${TOPIC_ID}/views`, () => {
        return new HttpResponse(null, { status: 500 });
      }),
    );

    // Не должно бросать exception - hook silent-fails best-effort
    const { result } = renderHook(() => useViewTracking('topics', TOPIC_ID));
    await new Promise((resolve) => setTimeout(resolve, 50));
    // No throw - just check that hook returned undefined (правильно)
    expect(result.current).toBeUndefined();
  });

  it('упавший первый POST НЕ ставит dedup-флаг → следующий визит ретраит', async () => {
    let postCount = 0;
    server.use(
      http.post(`${BASE}/api/v1/topics/${TOPIC_ID}/views`, () => {
        postCount += 1;
        // первый POST падает, второй — успешен
        return postCount === 1
          ? new HttpResponse(null, { status: 500 })
          : new HttpResponse(null, { status: 204 });
      }),
    );

    const { unmount } = renderHook(() => useViewTracking('topics', TOPIC_ID));
    await vi.waitFor(() => expect(postCount).toBe(1));
    // первый POST провалился — флаг НЕ должен быть выставлен
    expect(window.sessionStorage.getItem(`viewed:topics:${TOPIC_ID}`)).toBeNull();
    unmount();

    // Второй визит в той же session — раз флаг не стоял, ретраим
    renderHook(() => useViewTracking('topics', TOPIC_ID));
    await vi.waitFor(() => expect(postCount).toBe(2));
    // теперь успех — флаг выставлен
    await vi.waitFor(() =>
      expect(window.sessionStorage.getItem(`viewed:topics:${TOPIC_ID}`)).toBe('1'),
    );
  });

  it('успешный POST ставит dedup-флаг', async () => {
    server.use(
      http.post(`${BASE}/api/v1/topics/${TOPIC_ID}/views`, () => {
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderHook(() => useViewTracking('topics', TOPIC_ID));
    await vi.waitFor(() =>
      expect(window.sessionStorage.getItem(`viewed:topics:${TOPIC_ID}`)).toBe('1'),
    );
  });
});
