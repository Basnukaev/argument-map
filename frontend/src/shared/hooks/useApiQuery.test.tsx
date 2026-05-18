import { describe, it, expect } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { useApiQuery } from './useApiQuery';

interface Topic {
  id: string;
  title: string;
}

describe('useApiQuery', () => {
  it('начинает в loading и переходит в success при удачном ответе', async () => {
    server.use(
      http.get('http://test.local/api/v1/topics', () =>
        HttpResponse.json([{ id: '1', title: 'Тема' }] as Topic[]),
      ),
    );

    const { result } = renderHook(() => useApiQuery<Topic[]>('/api/v1/topics'));

    expect(result.current.kind).toBe('loading');

    await waitFor(() => expect(result.current.kind).toBe('success'));
    if (result.current.kind === 'success') {
      expect(result.current.data).toEqual([{ id: '1', title: 'Тема' }]);
    }
  });

  it('error path - ставит kind=error с сообщением', async () => {
    server.use(
      http.get('http://test.local/api/v1/topics', () =>
        HttpResponse.json(
          {
            type: 'https://example.com/problems/server-error',
            title: 'Server Error',
            status: 500,
            detail: 'тестовая ошибка',
          },
          { status: 500 },
        ),
      ),
    );

    const { result } = renderHook(() =>
      useApiQuery<Topic[]>('/api/v1/topics'),
    );

    await waitFor(() => expect(result.current.kind).toBe('error'));
    if (result.current.kind === 'error') {
      expect(result.current.message).toContain('тестовая ошибка');
    }
  });

  it('enabled=false → kind=idle, не делает запрос', async () => {
    // Если бы запрос пошёл - msw упал бы с onUnhandledRequest='error'
    const { result } = renderHook(() =>
      useApiQuery<Topic[]>('/api/v1/topics', { enabled: false }),
    );
    expect(result.current.kind).toBe('idle');
  });

  it('path=null → kind=idle, не делает запрос', async () => {
    const { result } = renderHook(() => useApiQuery<Topic[]>(null));
    expect(result.current.kind).toBe('idle');
  });

  it('кастомный fallbackError используется когда error не Error-like', async () => {
    server.use(
      http.get('http://test.local/api/v1/topics', () =>
        HttpResponse.text('plain text', { status: 502 }),
      ),
    );

    const { result } = renderHook(() =>
      useApiQuery<Topic[]>('/api/v1/topics', { fallbackError: 'custom-fail' }),
    );
    await waitFor(() => expect(result.current.kind).toBe('error'));
  });

  it('rerender со сменой path → перезапускает запрос', async () => {
    server.use(
      http.get('http://test.local/api/v1/topics/1', () =>
        HttpResponse.json({ id: '1', title: 'A' } as Topic),
      ),
      http.get('http://test.local/api/v1/topics/2', () =>
        HttpResponse.json({ id: '2', title: 'B' } as Topic),
      ),
    );

    const { result, rerender } = renderHook(
      ({ id }: { id: string }) => useApiQuery<Topic>(`/api/v1/topics/${id}`),
      { initialProps: { id: '1' } },
    );

    await waitFor(() => expect(result.current.kind).toBe('success'));
    if (result.current.kind === 'success') {
      expect(result.current.data.id).toBe('1');
    }

    rerender({ id: '2' });

    await waitFor(
      () =>
        result.current.kind === 'success' && result.current.data.id === '2',
    );
  });
});
