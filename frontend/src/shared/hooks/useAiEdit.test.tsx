/**
 * useAiEdit tests - covering happy path, 503 handling, polling timeout,
 * and abort on unmount (Этап 17.e.f).
 *
 * Polling cadence (3 сек) ускоряется через `vi.useFakeTimers` -
 * без mock'а тесты тянули бы минуты.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { useAiEdit } from './useAiEdit';
import { ApiError } from '@/shared/api/client';

const BASE = 'http://test.local';
const PAGE_ID = '00000000-0000-0000-0000-0000000000aa';

describe('useAiEdit', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('happy path: POST → polling → DONE → onContentReady с formattedContent', async () => {
    const formatted = { type: 'doc', content: [{ type: 'paragraph' }] };
    let pollCount = 0;
    server.use(
      http.post(`${BASE}/api/v1/library/pages/${PAGE_ID}/ai-edit`, () =>
        HttpResponse.json(
          { pageId: PAGE_ID, status: 'PROCESSING', hasTextContent: true },
          { status: 202 },
        ),
      ),
      http.get(`${BASE}/api/v1/library/pages/${PAGE_ID}/ai-edit`, () => {
        pollCount++;
        // первый poll - всё ещё processing, второй - done
        const status = pollCount >= 2 ? 'DONE' : 'PROCESSING';
        return HttpResponse.json({
          pageId: PAGE_ID,
          status,
          hasTextContent: true,
        });
      }),
      http.get(`${BASE}/api/v1/library/pages/${PAGE_ID}`, () =>
        HttpResponse.json({
          id: PAGE_ID,
          textContent: 'raw',
          formattedContent: formatted,
        }),
      ),
    );

    const onReady = vi.fn();
    const { result } = renderHook(() => useAiEdit(onReady));

    await act(async () => {
      await result.current.start(PAGE_ID);
    });

    // После POST - processing
    expect(result.current.status).toBe('processing');

    // Прокручиваем время на 2 polling tick'а (6+ сек) - первый
    // вернёт PROCESSING, второй DONE
    await act(async () => {
      await vi.advanceTimersByTimeAsync(3500);
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(3500);
    });

    await waitFor(() => {
      expect(result.current.status).toBe('done');
    });
    expect(onReady).toHaveBeenCalledWith(formatted);
  });

  it('503 ai-edit-not-configured: ApiError пробрасывается из start()', async () => {
    server.use(
      http.post(`${BASE}/api/v1/library/pages/${PAGE_ID}/ai-edit`, () =>
        HttpResponse.json(
          {
            type: 'https://argumentmap.example/errors/ai-edit-not-configured',
            title: 'AI editing не настроен',
            status: 503,
            detail: 'установите ANTHROPIC_API_KEY env var',
          },
          {
            status: 503,
            headers: { 'Content-Type': 'application/problem+json' },
          },
        ),
      ),
    );

    const onReady = vi.fn();
    const { result } = renderHook(() => useAiEdit(onReady));

    let captured: unknown = null;
    await act(async () => {
      try {
        await result.current.start(PAGE_ID);
      } catch (e) {
        captured = e;
      }
    });

    expect(captured).toBeInstanceOf(ApiError);
    expect((captured as ApiError).status).toBe(503);
    expect((captured as ApiError).is('ai-edit-not-configured')).toBe(true);
    expect(onReady).not.toHaveBeenCalled();
    // После 503 status должен вернуться в idle - overlay не должен залипать.
    // 503 уже залогирован toast'ом в callsite, никакого «processing» state
    // не должно остаться
    expect(result.current.status).toBe('idle');
    expect(result.current.elapsedSeconds).toBe(0);
  });

  it('polling timeout: после 5 минут статус становится failed', async () => {
    server.use(
      http.post(`${BASE}/api/v1/library/pages/${PAGE_ID}/ai-edit`, () =>
        HttpResponse.json(
          { pageId: PAGE_ID, status: 'PROCESSING', hasTextContent: true },
          { status: 202 },
        ),
      ),
      // GET всегда возвращает PROCESSING - имитируем зависший job
      http.get(`${BASE}/api/v1/library/pages/${PAGE_ID}/ai-edit`, () =>
        HttpResponse.json({
          pageId: PAGE_ID,
          status: 'PROCESSING',
          hasTextContent: true,
        }),
      ),
    );

    const onReady = vi.fn();
    const { result } = renderHook(() => useAiEdit(onReady));

    await act(async () => {
      await result.current.start(PAGE_ID);
    });
    expect(result.current.status).toBe('processing');

    // 5 минут + чуть-чуть запас
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5 * 60 * 1000 + 100);
    });

    await waitFor(() => {
      expect(result.current.status).toBe('failed');
    });
    expect(onReady).not.toHaveBeenCalled();
  });

  it('cancel() прерывает polling и сбрасывает status в idle', async () => {
    server.use(
      http.post(`${BASE}/api/v1/library/pages/${PAGE_ID}/ai-edit`, () =>
        HttpResponse.json(
          { pageId: PAGE_ID, status: 'PROCESSING', hasTextContent: true },
          { status: 202 },
        ),
      ),
      http.get(`${BASE}/api/v1/library/pages/${PAGE_ID}/ai-edit`, () =>
        HttpResponse.json({
          pageId: PAGE_ID,
          status: 'PROCESSING',
          hasTextContent: true,
        }),
      ),
    );

    const onReady = vi.fn();
    const { result } = renderHook(() => useAiEdit(onReady));

    await act(async () => {
      await result.current.start(PAGE_ID);
    });
    expect(result.current.status).toBe('processing');

    act(() => {
      result.current.cancel();
    });

    expect(result.current.status).toBe('idle');
    expect(result.current.elapsedSeconds).toBe(0);
  });

  it('unmount чистит polling - дальнейшие toast/state-обновления не падают', async () => {
    server.use(
      http.post(`${BASE}/api/v1/library/pages/${PAGE_ID}/ai-edit`, () =>
        HttpResponse.json(
          { pageId: PAGE_ID, status: 'PROCESSING', hasTextContent: true },
          { status: 202 },
        ),
      ),
      http.get(`${BASE}/api/v1/library/pages/${PAGE_ID}/ai-edit`, () =>
        HttpResponse.json({
          pageId: PAGE_ID,
          status: 'PROCESSING',
          hasTextContent: true,
        }),
      ),
    );

    const onReady = vi.fn();
    const { result, unmount } = renderHook(() => useAiEdit(onReady));

    await act(async () => {
      await result.current.start(PAGE_ID);
    });

    // Сразу unmount - polling interval ещё активен внутри
    unmount();

    // Прокрутка времени - cleanup должен был отменить, не должно
    // быть ни упавших промисов, ни вызовов callback
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });

    expect(onReady).not.toHaveBeenCalled();
  });
});
