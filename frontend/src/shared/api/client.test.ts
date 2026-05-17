import { describe, it, expect, afterEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import {
  apiGet,
  apiGetRaw,
  apiPost,
  apiPostRaw,
  apiDelete,
  apiDeleteRaw,
  ApiError,
  _attachAuthAccessor,
} from '@/shared/api/client';

const BASE = 'http://test.local';
const USER_ID = '00000000-0000-0000-0000-000000000001';

/**
 * После interceptor-тестов сбрасываем accessor чтобы legacy-тесты выше
 * не наследовали Bearer flow
 */
function resetAuthAccessor() {
  _attachAuthAccessor({
    getAccessToken: () => null,
    refresh: async () => null,
    clearSession: () => {},
  });
}

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

describe('api/client - auth interceptor (Этап 21.b)', () => {
  afterEach(() => {
    resetAuthAccessor();
  });

  it('добавляет Bearer header если accessToken присутствует', async () => {
    let mockToken: string | null = 'my.access.token';
    _attachAuthAccessor({
      getAccessToken: () => mockToken,
      refresh: async () => null,
      clearSession: () => {
        mockToken = null;
      },
    });

    let receivedAuth: string | null = null;
    server.use(
      http.get(`${BASE}/api/v1/topics`, ({ request }) => {
        receivedAuth = request.headers.get('Authorization');
        return HttpResponse.json([]);
      }),
    );

    await apiGetRaw('/api/v1/topics');
    expect(receivedAuth).toBe('Bearer my.access.token');
  });

  it('НЕ добавляет Bearer для /api/v1/auth/* endpoints', async () => {
    _attachAuthAccessor({
      getAccessToken: () => 'tok',
      refresh: async () => null,
      clearSession: () => {},
    });

    let receivedAuth: string | null = 'sentinel';
    server.use(
      http.post(`${BASE}/api/v1/auth/login`, ({ request }) => {
        receivedAuth = request.headers.get('Authorization');
        return HttpResponse.json({ ok: true });
      }),
    );

    await apiPostRaw('/api/v1/auth/login', { email: 'x', password: 'y' });
    expect(receivedAuth).toBeNull();
  });

  it('401 → refresh успех → retry с новым token', async () => {
    let mockToken: string | null = 'stale.token';
    let refreshCalls = 0;
    _attachAuthAccessor({
      getAccessToken: () => mockToken,
      refresh: async () => {
        refreshCalls += 1;
        mockToken = 'fresh.token';
        return 'fresh.token';
      },
      clearSession: () => {
        mockToken = null;
      },
    });

    let attempt = 0;
    const observedAuth: Array<string | null> = [];
    server.use(
      http.get(`${BASE}/api/v1/topics`, ({ request }) => {
        attempt += 1;
        observedAuth.push(request.headers.get('Authorization'));
        if (attempt === 1) {
          return HttpResponse.json(
            { type: 'about:blank', title: 'Unauthorized', status: 401 },
            { status: 401 },
          );
        }
        return HttpResponse.json({ topics: [{ id: 'x' }] });
      }),
    );

    const result = await apiGetRaw<{ topics: Array<{ id: string }> }>(
      '/api/v1/topics',
    );
    expect(result.topics).toHaveLength(1);
    expect(attempt).toBe(2);
    expect(refreshCalls).toBe(1);
    expect(observedAuth[0]).toBe('Bearer stale.token');
    expect(observedAuth[1]).toBe('Bearer fresh.token');
  });

  it('401 → refresh fail → бросает 401 ApiError + clearSession', async () => {
    let mockToken: string | null = 'stale.token';
    let cleared = false;
    _attachAuthAccessor({
      getAccessToken: () => mockToken,
      refresh: async () => null,
      clearSession: () => {
        cleared = true;
        mockToken = null;
      },
    });

    server.use(
      http.get(`${BASE}/api/v1/topics`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Unauthorized', status: 401 },
          { status: 401 },
        ),
      ),
    );

    await expect(apiGetRaw('/api/v1/topics')).rejects.toMatchObject({
      status: 401,
    });
    expect(cleared).toBe(true);
  });

  it('конкурентные 401 - один общий refresh, все retry с новым token', async () => {
    let mockToken: string | null = 'stale';
    let refreshCalls = 0;
    _attachAuthAccessor({
      getAccessToken: () => mockToken,
      refresh: async () => {
        refreshCalls += 1;
        // имитация задержки refresh - чтобы параллельные запросы успели
        // встроиться в очередь
        await new Promise((r) => setTimeout(r, 20));
        mockToken = 'fresh';
        return 'fresh';
      },
      clearSession: () => {
        mockToken = null;
      },
    });

    const makeHandler = (path: string) =>
      http.get(`${BASE}${path}`, ({ request }) => {
        const auth = request.headers.get('Authorization');
        if (auth === 'Bearer stale') {
          return HttpResponse.json(
            { type: 'about:blank', title: 'Unauthorized', status: 401 },
            { status: 401 },
          );
        }
        return HttpResponse.json({ ok: true });
      });

    server.use(
      makeHandler('/api/v1/a'),
      makeHandler('/api/v1/b'),
      makeHandler('/api/v1/c'),
    );

    const results = await Promise.all([
      apiGetRaw<{ ok: boolean }>('/api/v1/a'),
      apiGetRaw<{ ok: boolean }>('/api/v1/b'),
      apiGetRaw<{ ok: boolean }>('/api/v1/c'),
    ]);

    expect(results.every((r) => r.ok)).toBe(true);
    expect(refreshCalls).toBe(1);
  });
});
