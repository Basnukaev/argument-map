import { describe, it, expect } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { apiGet, apiPost, apiDelete, apiDeleteRaw, ApiError } from '@/shared/api/client';

const BASE = 'http://test.local';
const USER_ID = '00000000-0000-0000-0000-000000000001';

describe('api/client', () => {
  it('GET не отправляет X-User-Id (read-only)', async () => {
    server.use(
      http.get(`${BASE}/api/v1/topics`, ({ request }) => {
        expect(request.headers.get('X-User-Id')).toBeNull();
        return HttpResponse.json([]);
      }),
    );
    const result = await apiGet('/api/v1/topics');
    expect(result).toEqual([]);
  });

  it('POST добавляет X-User-Id из VITE_DEV_USER_ID', async () => {
    let captured: string | null = null;
    server.use(
      http.post(`${BASE}/api/v1/topics`, async ({ request }) => {
        captured = request.headers.get('X-User-Id');
        return HttpResponse.json({ id: 'new-id', title: 'X', rootQuestion: 'Y' });
      }),
    );

    await apiPost('/api/v1/topics', { title: 'X', rootQuestion: 'Y' });

    expect(captured).toBe(USER_ID);
  });

  it('бросает ApiError с распарсенным Problem Details на 4xx', async () => {
    server.use(
      http.get(`${BASE}/api/v1/topics`, () =>
        HttpResponse.json(
          {
            type: 'https://argumentmap.example/errors/topic-not-found',
            title: 'Тема не найдена',
            status: 404,
            detail: 'Тема с id=abc не найдена',
          },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    await expect(apiGet('/api/v1/topics')).rejects.toMatchObject({
      status: 404,
      problem: { title: 'Тема не найдена', status: 404 },
    });
  });

  it('ApiError.is(suffix) сопоставляет type-код', async () => {
    server.use(
      http.get(`${BASE}/api/v1/topics`, () =>
        HttpResponse.json(
          { type: 'https://example.com/errors/topic-not-found', title: 't', status: 404 },
          { status: 404 },
        ),
      ),
    );

    try {
      await apiGet('/api/v1/topics');
      throw new Error('должен был бросить');
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError);
      const err = e as ApiError;
      expect(err.is('topic-not-found')).toBe(true);
      expect(err.is('node-not-found')).toBe(false);
    }
  });

  it('ApiError содержит errors[] для валидационной ошибки', async () => {
    server.use(
      http.post(`${BASE}/api/v1/topics`, () =>
        HttpResponse.json(
          {
            type: 'https://example.com/errors/validation',
            title: 'Ошибка валидации',
            status: 400,
            errors: [{ field: 'title', message: 'не должно быть пустым' }],
          },
          { status: 400 },
        ),
      ),
    );

    try {
      await apiPost('/api/v1/topics', { title: '', rootQuestion: 'q' });
      throw new Error('должен был бросить');
    } catch (e) {
      const err = e as ApiError;
      expect(err.problem.errors).toEqual([{ field: 'title', message: 'не должно быть пустым' }]);
    }
  });

  it('DELETE возвращает void на 204', async () => {
    server.use(
      http.delete(`${BASE}/api/v1/topics/abc`, () => new HttpResponse(null, { status: 204 })),
    );
    const result = await apiDelete('/api/v1/topics/{topicId}'.replace('{topicId}', 'abc') as never);
    expect(result).toBeUndefined();
  });

  it('apiDeleteRaw отправляет X-User-Id и работает с динамическим путём', async () => {
    let captured: string | null = null;
    server.use(
      http.delete(`${BASE}/api/v1/nodes/abc-123`, ({ request }) => {
        captured = request.headers.get('X-User-Id');
        return new HttpResponse(null, { status: 204 });
      }),
    );

    await apiDeleteRaw('/api/v1/nodes/abc-123');

    expect(captured).toBe(USER_ID);
  });
});
