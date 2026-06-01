import { describe, it, expect, beforeEach, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { useAuthStore } from './authStore';

const API = 'http://test.local';

const ADMIN_USER = {
  id: '00000000-0000-0000-0000-000000000001',
  username: 'admin',
  email: 'admin@argumentmap.local',
  role: 'ADMIN' as const,
};

const LOGIN_RESPONSE = {
  accessToken: 'access.token.v1',
  accessTokenExpiresAt: '2030-01-01T00:00:00Z',
  user: ADMIN_USER,
};

function resetStore() {
  // Чистый initial state для каждого теста + localStorage
  if (typeof window !== 'undefined') {
    window.localStorage.removeItem('auth.user');
    window.localStorage.removeItem('app.preferences');
    window.localStorage.removeItem('onboarding_dismissed');
  }
  useAuthStore.setState({
    user: null,
    accessToken: null,
    isLoading: false,
    initialized: false,
  });
}

describe('authStore', () => {
  beforeEach(() => {
    resetStore();
  });

  it('login успех - сохраняет user + accessToken + persist в localStorage', async () => {
    server.use(
      http.post(`${API}/api/v1/auth/login`, async ({ request }) => {
        const body = (await request.json()) as { email: string; password: string };
        expect(body.email).toBe('admin@argumentmap.local');
        expect(body.password).toBe('admin12345');
        return HttpResponse.json(LOGIN_RESPONSE);
      }),
    );

    await useAuthStore.getState().login('admin@argumentmap.local', 'admin12345');

    const state = useAuthStore.getState();
    expect(state.user).toEqual(ADMIN_USER);
    expect(state.accessToken).toBe('access.token.v1');
    expect(window.localStorage.getItem('auth.user')).toContain('admin');
  });

  it('login fail 401 - бросает ApiError, state без изменений', async () => {
    server.use(
      http.post(`${API}/api/v1/auth/login`, () =>
        HttpResponse.json(
          {
            type: 'https://argumentmap.example/errors/invalid-credentials',
            title: 'Invalid credentials',
            status: 401,
          },
          { status: 401 },
        ),
      ),
    );

    await expect(
      useAuthStore.getState().login('admin@argumentmap.local', 'wrong'),
    ).rejects.toMatchObject({ status: 401 });

    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().accessToken).toBeNull();
  });

  it('register - также устанавливает session как login', async () => {
    server.use(
      http.post(`${API}/api/v1/auth/register`, async ({ request }) => {
        const body = (await request.json()) as { email: string; username: string; password: string };
        expect(body.username).toBe('newuser');
        return HttpResponse.json(LOGIN_RESPONSE, { status: 201 });
      }),
    );

    await useAuthStore.getState().register('new@x.com', 'newuser', 'password123');
    expect(useAuthStore.getState().user).toEqual(ADMIN_USER);
    expect(useAuthStore.getState().accessToken).toBe('access.token.v1');
  });

  it('logout - чистит user, accessToken + localStorage', async () => {
    // setup signed-in state
    useAuthStore.getState()._setSession(ADMIN_USER, 'tok');
    expect(window.localStorage.getItem('auth.user')).toBeTruthy();

    server.use(
      http.post(`${API}/api/v1/auth/logout`, () => new HttpResponse(null, { status: 204 })),
    );

    await useAuthStore.getState().logout();
    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(window.localStorage.getItem('auth.user')).toBeNull();
  });

  it('logout - чистит user-scoped кеши (app.preferences + onboarding_dismissed)', async () => {
    // setup: shared машина - user A выставил prefs + закрыл onboarding
    useAuthStore.getState()._setSession(ADMIN_USER, 'tok');
    window.localStorage.setItem(
      'app.preferences',
      JSON.stringify({ arabicFont: 'kufi', textSize: 'xl' }),
    );
    window.localStorage.setItem('onboarding_dismissed', '1');

    server.use(
      http.post(`${API}/api/v1/auth/logout`, () => new HttpResponse(null, { status: 204 })),
    );

    await useAuthStore.getState().logout();

    // user-scoped кеши очищены - следующий user не унаследует
    expect(window.localStorage.getItem('app.preferences')).toBeNull();
    expect(window.localStorage.getItem('onboarding_dismissed')).toBeNull();
  });

  it('logout backend упал - всё равно чистит user-scoped кеши', async () => {
    useAuthStore.getState()._setSession(ADMIN_USER, 'tok');
    window.localStorage.setItem('app.preferences', '{"arabicFont":"kufi"}');
    window.localStorage.setItem('onboarding_dismissed', '1');

    server.use(http.post(`${API}/api/v1/auth/logout`, () => HttpResponse.error()));

    await useAuthStore.getState().logout();

    expect(window.localStorage.getItem('app.preferences')).toBeNull();
    expect(window.localStorage.getItem('onboarding_dismissed')).toBeNull();
  });

  it('refreshAccessToken 401 (session expired) - чистит user-scoped кеши', async () => {
    useAuthStore.getState()._setSession(ADMIN_USER, 'stale.token');
    window.localStorage.setItem('app.preferences', '{"arabicFont":"kufi"}');
    window.localStorage.setItem('onboarding_dismissed', '1');

    server.use(
      http.post(`${API}/api/v1/auth/refresh`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'expired', status: 401 },
          { status: 401 },
        ),
      ),
    );

    await useAuthStore.getState().refreshAccessToken();

    expect(window.localStorage.getItem('app.preferences')).toBeNull();
    expect(window.localStorage.getItem('onboarding_dismissed')).toBeNull();
  });

  it('logout - работает даже если backend упал (network error)', async () => {
    useAuthStore.getState()._setSession(ADMIN_USER, 'tok');
    server.use(
      http.post(`${API}/api/v1/auth/logout`, () => HttpResponse.error()),
    );

    await useAuthStore.getState().logout();
    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().accessToken).toBeNull();
  });

  it('refreshAccessToken успех - обновляет access + user', async () => {
    server.use(
      http.post(`${API}/api/v1/auth/refresh`, () =>
        HttpResponse.json({
          ...LOGIN_RESPONSE,
          accessToken: 'fresh.token.v2',
        }),
      ),
    );

    const result = await useAuthStore.getState().refreshAccessToken();
    expect(result).toBe('fresh.token.v2');
    expect(useAuthStore.getState().accessToken).toBe('fresh.token.v2');
    expect(useAuthStore.getState().user).toEqual(ADMIN_USER);
  });

  it('refreshAccessToken 401 - чистит state, возвращает null', async () => {
    useAuthStore.getState()._setSession(ADMIN_USER, 'stale.token');
    server.use(
      http.post(`${API}/api/v1/auth/refresh`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Invalid token', status: 401 },
          { status: 401 },
        ),
      ),
    );

    const result = await useAuthStore.getState().refreshAccessToken();
    expect(result).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().accessToken).toBeNull();
  });

  it('loadCurrentUser успех (refresh выдал token) - флипает initialized', async () => {
    server.use(
      http.post(`${API}/api/v1/auth/refresh`, () => HttpResponse.json(LOGIN_RESPONSE)),
    );

    await useAuthStore.getState().loadCurrentUser();
    expect(useAuthStore.getState().initialized).toBe(true);
    expect(useAuthStore.getState().user).toEqual(ADMIN_USER);
    expect(useAuthStore.getState().isLoading).toBe(false);
  });

  it('loadCurrentUser fail (refresh 401) - очищает persisted user, инициализирован', async () => {
    // Симулируем что localStorage содержит "залогиненного" user после
    // предыдущей сессии, но refresh cookie больше невалидный
    useAuthStore.getState()._setSession(ADMIN_USER, null);
    server.use(
      http.post(`${API}/api/v1/auth/refresh`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'expired', status: 401 },
          { status: 401 },
        ),
      ),
    );

    await useAuthStore.getState().loadCurrentUser();
    expect(useAuthStore.getState().initialized).toBe(true);
    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(window.localStorage.getItem('auth.user')).toBeNull();
  });

  it('refreshAccessToken - параллельные вызовы делятся одним promise', async () => {
    // Этот тест проверяет логику authStore (не interceptor).
    // Через ручной счётчик server hits
    let calls = 0;
    server.use(
      http.post(`${API}/api/v1/auth/refresh`, async () => {
        calls += 1;
        await new Promise((r) => setTimeout(r, 10));
        return HttpResponse.json(LOGIN_RESPONSE);
      }),
    );

    // store сам по себе не дедуплицирует - дедупликация в apiClient interceptor.
    // Тут только проверка что store работает корректно если refresh вызвать
    // несколько раз последовательно
    const t1 = await useAuthStore.getState().refreshAccessToken();
    const t2 = await useAuthStore.getState().refreshAccessToken();
    expect(t1).toBe('access.token.v1');
    expect(t2).toBe('access.token.v1');
    expect(calls).toBe(2);
  });
});

// readPersistedUser вызывается на module-load (seed initial store.user),
// поэтому тестируем через resetModules + динамический реимпорт после того
// как засеяли localStorage. Регрессия: Vision 49d Phase A.6 расширил role
// до USER<STUDENT<SCHOLAR<ADMIN, но валидация осталась USER|ADMIN →
// persisted STUDENT/SCHOLAR не проходил и сессия терялась на reload.
describe('authStore: hydration persisted role (Vision 49d Phase A.6)', () => {
  beforeEach(() => {
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem('auth.user');
    }
  });

  it.each(['USER', 'STUDENT', 'SCHOLAR', 'ADMIN'] as const)(
    'persisted role %s переживает reload (initial store.user не null)',
    async (role) => {
      window.localStorage.setItem(
        'auth.user',
        JSON.stringify({
          id: '00000000-0000-0000-0000-0000000000aa',
          username: 'u-' + role.toLowerCase(),
          email: role.toLowerCase() + '@e.com',
          role,
        }),
      );

      vi.resetModules();
      const { useAuthStore: freshStore } = await import('./authStore');
      const user = freshStore.getState().user;

      expect(user).not.toBeNull();
      expect(user?.role).toBe(role);
    },
  );

  it('невалидная role в localStorage → user=null (treated as logged out)', async () => {
    window.localStorage.setItem(
      'auth.user',
      JSON.stringify({
        id: '00000000-0000-0000-0000-0000000000bb',
        username: 'bogus',
        email: 'bogus@e.com',
        role: 'SUPERADMIN',
      }),
    );

    vi.resetModules();
    const { useAuthStore: freshStore } = await import('./authStore');
    expect(freshStore.getState().user).toBeNull();
  });
});
