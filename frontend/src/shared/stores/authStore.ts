import { create } from 'zustand';
import { API_BASE_URL, ApiError } from '@/shared/api/client';
import { clearUserStorage } from '@/shared/utils/clearUserStorage';

/**
 * Auth-роли как union literal (ADR-040, без TS enum по правилам проекта).
 * Vision 49d Phase A.6 expanded к 4 значениям (USER < STUDENT <
 * SCHOLAR < ADMIN, монотонная иерархия). Backend источник истины —
 * UserRole.java + migration 49 CHECK constraint.
 */
export type AuthRole = 'USER' | 'STUDENT' | 'SCHOLAR' | 'ADMIN';

/**
 * Ordered list ролей по возрастанию привилегий. Используется в
 * hasRoleAtLeast helper (mirror UserRole.hasAtLeast backend).
 */
export const ALL_ROLES: readonly AuthRole[] = ['USER', 'STUDENT', 'SCHOLAR', 'ADMIN'];

/**
 * Иерархическая проверка: возвращает true если actual не ниже required
 * в порядке USER → STUDENT → SCHOLAR → ADMIN. null/undefined actual
 * (anonymous) → всегда false.
 */
export function hasRoleAtLeast(actual: AuthRole | null | undefined, required: AuthRole): boolean {
  if (!actual) return false;
  return ALL_ROLES.indexOf(actual) >= ALL_ROLES.indexOf(required);
}

export interface AuthUser {
  id: string;
  username: string;
  email: string;
  role: AuthRole;
}

/**
 * Selector-хук: залогинен ли пользователь. Guest view (roadmap 49.G):
 * анонимный читатель видит публичный контент, но write-действия (создать
 * тему/вопрос, импорт, голос) скрыты. Компоненты гейтят CTA через
 * `useIsAuthenticated()` вместо ручного `useAuthStore((s) => Boolean(s.user))`.
 */
export function useIsAuthenticated(): boolean {
  return useAuthStore((s) => s.user !== null);
}

/**
 * Backend ответ /auth/login и /auth/refresh.
 */
interface AuthLoginResponse {
  accessToken: string;
  accessTokenExpiresAt: string;
  user: AuthUser;
}

interface AuthState {
  user: AuthUser | null;
  accessToken: string | null;
  /** initial bootstrap - /me lookup ещё не завершён */
  isLoading: boolean;
  /** успел ли отработать loadCurrentUser хотя бы раз */
  initialized: boolean;

  login: (email: string, password: string) => Promise<void>;
  register: (email: string, username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  /** Возвращает новый access token либо null если refresh не удался */
  refreshAccessToken: () => Promise<string | null>;
  loadCurrentUser: () => Promise<void>;
  /** Только для тестов и interceptor'а - устанавливает state напрямую */
  _setSession: (user: AuthUser | null, accessToken: string | null) => void;
}

/**
 * Ключ в localStorage. Сохраняем только user (id/username/email/role) -
 * быстрый UI bootstrap без spinner'а пока летит /me request. Access token
 * НЕ кладём в localStorage (XSS-safety, ADR-040), refresh - в httpOnly
 * cookie. На refresh странички user может краткосрочно показываться
 * залогиненым, но реальный access получается через /refresh либо /me
 * только что после loadCurrentUser
 */
const USER_STORAGE_KEY = 'auth.user';

function readPersistedUser(): AuthUser | null {
  if (typeof window === 'undefined') return null;
  const raw = window.localStorage.getItem(USER_STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<AuthUser>;
    if (
      typeof parsed.id === 'string' &&
      typeof parsed.username === 'string' &&
      typeof parsed.email === 'string' &&
      // Vision 49d Phase A.6: role расширен USER<STUDENT<SCHOLAR<ADMIN.
      // Раньше проверка была только USER|ADMIN → persisted STUDENT/SCHOLAR
      // не проходил валидацию и сессия терялась на reload. Проверяем по
      // ALL_ROLES (источник истины списка ролей).
      ALL_ROLES.includes(parsed.role as AuthRole)
    ) {
      return parsed as AuthUser;
    }
  } catch {
    // corrupted - игнорируем, treat as logged out
  }
  return null;
}

function persistUser(user: AuthUser | null): void {
  if (typeof window === 'undefined') return;
  if (user) {
    window.localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
  } else {
    window.localStorage.removeItem(USER_STORAGE_KEY);
  }
}

/**
 * Сырой fetch для auth endpoints чтобы избежать circular dependency с
 * apiClient (interceptor ссылается на authStore, store - на apiClient
 * → loop при импорте). Дублируем минимум: JSON + credentials: 'include'
 * для refresh cookie + парсинг ProblemDetails в ApiError. Не идёт через
 * 401-retry pipeline (auth endpoints сами и есть pipeline)
 */
async function rawAuthFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...(init.body ? { 'Content-Type': 'application/json' } : {}),
    ...((init.headers as Record<string, string>) ?? {}),
  };
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
    credentials: 'include',
  });
  if (response.status === 204) return undefined as T;
  const contentType = response.headers.get('Content-Type') ?? '';
  const isJson =
    contentType.includes('application/json') ||
    contentType.includes('application/problem+json');
  if (!response.ok) {
    if (isJson) {
      const problem = await response.json();
      throw new ApiError(response.status, problem);
    }
    throw new ApiError(response.status, {
      type: 'about:blank',
      title: response.statusText || 'Auth request failed',
      status: response.status,
    });
  }
  if (!isJson) return undefined as T;
  return (await response.json()) as T;
}

/**
 * Single-flight для refresh НА УРОВНЕ СТОРА. Дедупликации в apiClient
 * недостаточно: bootstrap (loadCurrentUser) зовёт refreshAccessToken
 * напрямую, минуя interceptor, и при перезагрузке страницы гонится с
 * 401-retry компонентных запросов. Ротация refresh-токена (ADR-047)
 * валидирует только первый POST /auth/refresh - проигравшие получают
 * 401, а их catch стирал сессию → флаки-логаут по F5.
 */
let refreshInFlight: Promise<string | null> | null = null;

export const useAuthStore = create<AuthState>((set, get) => ({
  user: readPersistedUser(),
  accessToken: null,
  isLoading: false,
  initialized: false,

  _setSession(user, accessToken) {
    persistUser(user);
    set({ user, accessToken });
  },

  async login(email, password) {
    const data = await rawAuthFetch<AuthLoginResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
    persistUser(data.user);
    set({ user: data.user, accessToken: data.accessToken, initialized: true });
  },

  async register(email, username, password) {
    const data = await rawAuthFetch<AuthLoginResponse>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, username, password }),
    });
    persistUser(data.user);
    set({ user: data.user, accessToken: data.accessToken, initialized: true });
  },

  async logout() {
    try {
      await rawAuthFetch<void>('/api/v1/auth/logout', { method: 'POST' });
    } catch {
      // даже если бэк не ответил - всё равно чистим клиентскую сессию
    }
    persistUser(null);
    // Очищаем user-scoped кеши (preferences + onboarding) - чтобы на
    // shared машине следующий user не унаследовал чужие настройки.
    // Источник истины - бэк, на следующем login данные будут получены
    // через loadFromBackend()
    clearUserStorage();
    set({ user: null, accessToken: null, initialized: true });
  },

  async refreshAccessToken() {
    if (refreshInFlight) return refreshInFlight;
    refreshInFlight = (async () => {
      try {
        const data = await rawAuthFetch<AuthLoginResponse>(
          '/api/v1/auth/refresh',
          { method: 'POST' },
        );
        persistUser(data.user);
        set({
          user: data.user,
          accessToken: data.accessToken,
          initialized: true,
        });
        return data.accessToken;
      } catch (e) {
        // 401 = refresh cookie невалидный либо истёк - clear local session
        // включая user-scoped кеши (preferences + onboarding) чтобы на
        // следующем login другого user'а они не унаследовались
        if (e instanceof ApiError && e.status === 401) {
          persistUser(null);
          clearUserStorage();
          set({ user: null, accessToken: null, initialized: true });
        }
        return null;
      } finally {
        // через микротик - чтобы параллельные wait'еры успели await'нуться
        // на текущий promise прежде чем освободим slot (идиома client.ts)
        queueMicrotask(() => {
          refreshInFlight = null;
        });
      }
    })();
    return refreshInFlight;
  },

  async loadCurrentUser() {
    set({ isLoading: true });
    try {
      // Попытка 1: refresh - если cookie валидный, получаем новый access +
      // полный user. Это default путь: на старте приложения accessToken=null
      // (он в памяти, теряется при reload), но refresh cookie должен быть
      // в браузере. Если refresh fail (нет cookie / expired) - очищаем
      // persisted user и редиректим на /login через ProtectedRoute
      const newToken = await get().refreshAccessToken();
      if (newToken == null) {
        // refresh упал - clear любого persisted user + user-scoped кешей
        persistUser(null);
        clearUserStorage();
        set({ user: null, accessToken: null });
      }
    } finally {
      set({ isLoading: false, initialized: true });
    }
  },
}));
