import type { paths } from '@/shared/api/types';

/**
 * Базовый URL бэка. Логика:
 *   - test - используем VITE_API_URL (msw слушает абсолютные URLs из
 *     test-setup, "http://test.local")
 *   - dev (browser через Vite) - пустой prefix → запросы идут по
 *     относительному пути через Vite proxy (см. vite.config.ts:
 *     /api → :9090). Same-origin важен для auth-flow Этапа 21.b:
 *     SameSite=Strict refresh cookie не шлётся cross-origin
 *     (с :5173 на :9090), плюс backend CORS allowCredentials=false.
 *     Через proxy всё одно origin, cookies работают.
 *   - prod build - VITE_API_URL обычно тоже пустой (фронт и API на
 *     одном домене через reverse proxy), иначе явно задаётся в env
 *
 * Экспортируется чтобы прямые fetch-запросы (например react-pdf
 * Document file=...) могли строить absolute URLs - они тоже идут
 * через proxy (пустой prefix + относительный путь /api/...)
 */
export const API_BASE_URL =
  import.meta.env.MODE === 'test'
    ? (import.meta.env.VITE_API_URL ?? 'http://test.local')
    : ''; // browser - всегда через Vite proxy / same-origin для prod

/**
 * UUID текущего пользователя для заголовка X-User-Id (ADR-006).
 * Временное решение до Этапа 6 (Spring Security). Берётся из VITE_DEV_USER_ID.
 */
const DEV_USER_ID = import.meta.env.VITE_DEV_USER_ID ?? '';

/**
 * Problem Details (RFC 7807) - формат ошибок бэка из api-design.md.
 * Поле errors[] добавляется только для validation-ошибок 400.
 */
export interface ProblemDetails {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  errors?: Array<{ field: string; message: string }>;
}

/**
 * Типизированное исключение для ошибок API.
 * Содержит распарсенный Problem Details + статус-код.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetails;

  constructor(status: number, problem: ProblemDetails) {
    super(problem.detail ?? problem.title);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  /** Проверка по type-коду из api-contract.md (например "node-not-found") */
  is(typeSuffix: string): boolean {
    return this.problem.type.endsWith(`/${typeSuffix}`);
  }
}

/**
 * Форматирует ошибку в человекочитаемую строку для UI. Приоритет:
 * field-validation ошибки → problem.detail → problem.title → fallback.
 * Для не-ApiError возвращает error.message либо fallback.
 *
 * Используется в onCatch блоках чтобы избежать дублирования формулы
 * в 5+ компонентах (F-09 audit).
 */
export function formatApiError(error: unknown, fallback = 'Неизвестная ошибка'): string {
  if (error instanceof ApiError) {
    const fieldErrors = error.problem.errors
      ?.map((er) => `${er.field}: ${er.message}`)
      .join('; ');
    return fieldErrors || error.problem.detail || error.problem.title || fallback;
  }
  if (error instanceof Error) return error.message;
  return fallback;
}

type Method = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';

const MUTATING_METHODS: ReadonlySet<Method> = new Set(['POST', 'PATCH', 'PUT', 'DELETE']);

/**
 * Auth endpoints не идут через 401-retry pipeline - они сами и есть pipeline.
 * 401 от /auth/login - валидный «неверные креды», retry бессмыслен.
 */
function isAuthEndpoint(path: string): boolean {
  return path.startsWith('/api/v1/auth/');
}

interface RequestOptions {
  method?: Method;
  body?: unknown;
  /**
   * Тело уже как FormData - не json-сериализуется, Content-Type не
   * выставляется вручную (браузер сам добавит multipart boundary).
   * Игнорируется если method=GET.
   */
  formData?: FormData;
  signal?: AbortSignal;
}

/**
 * Lazy import authStore чтобы избежать circular dependency: authStore
 * импортирует ApiError из этого модуля. Делаем dynamic-обращение через
 * function ref - инициализируется ниже в `_attachAuthStore`. До инициализации
 * (например в unit-тестах без auth) request работает без Bearer headers
 * и без retry-on-401 - тесты явно мокают handlers через MSW
 */
type AuthAccessor = {
  getAccessToken: () => string | null;
  refresh: () => Promise<string | null>;
  clearSession: () => void;
};
let authAccessor: AuthAccessor | null = null;

export function _attachAuthAccessor(accessor: AuthAccessor): void {
  authAccessor = accessor;
}

/**
 * Сериализованный refresh - если 5 параллельных запросов получают 401
 * одновременно, refresh должен сделаться ОДИН раз. Очередь хранится
 * как single promise, остальные ждут его.
 */
let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  if (authAccessor == null) return null;
  if (refreshPromise) return refreshPromise;
  refreshPromise = (async () => {
    try {
      return await authAccessor!.refresh();
    } finally {
      // через микротик - чтобы все параллельные wait'еры успели await'нуться
      // на текущий promise прежде чем мы освободим slot для следующего цикла
      queueMicrotask(() => {
        refreshPromise = null;
      });
    }
  })();
  return refreshPromise;
}

/**
 * Низкоуровневый запрос к API. Сам не используется - через типизированные
 * хелперы apiGet / apiPost / apiPatch / apiDelete / apiPostMultipart.
 *
 * Поддерживает auth flow (ADR-040):
 *   1. Если есть accessToken в authStore - добавляет Authorization: Bearer
 *   2. Если ответ 401 (не на /auth/*) - пробует refresh + retry один раз
 *   3. Если refresh fail - чистит session, оригинальный 401 ApiError бросается
 *
 * Cookies (refresh) шлются через credentials: 'include'.
 */
async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return requestWithRetry<T>(path, options, false);
}

async function requestWithRetry<T>(
  path: string,
  options: RequestOptions,
  isRetry: boolean,
): Promise<T> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = {
    Accept: 'application/json',
  };
  if (options.body !== undefined && options.formData === undefined) {
    headers['Content-Type'] = 'application/json';
  }

  // Bearer JWT приоритет - prod auth flow. X-User-Id оставляем как fallback
  // для dev/tests где Bearer ещё не выдан (e.g. integration shimmy) -
  // backend XUserIdAuthenticationFilter поднимает ту же AuthenticatedUser.
  // Если есть оба - Bearer выигрывает (Spring Security filter chain)
  const accessToken = authAccessor?.getAccessToken() ?? null;
  if (accessToken && !isAuthEndpoint(path)) {
    headers['Authorization'] = `Bearer ${accessToken}`;
  } else if (MUTATING_METHODS.has(method) && DEV_USER_ID) {
    headers['X-User-Id'] = DEV_USER_ID;
  }

  // Тело: FormData (multipart) vs JSON. Для FormData Content-Type
  // ставит сам браузер с правильным boundary
  const fetchBody: BodyInit | undefined = options.formData
    ? options.formData
    : options.body !== undefined
      ? JSON.stringify(options.body)
      : undefined;

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body: fetchBody,
    signal: options.signal,
    // credentials для refresh cookie на любом auth-aware вызове
    credentials: 'include',
  });

  // 401 на не-auth endpoint и ещё не пробовали refresh - попытка
  if (
    response.status === 401 &&
    !isRetry &&
    !isAuthEndpoint(path) &&
    authAccessor != null
  ) {
    const newToken = await refreshAccessToken();
    if (newToken) {
      return requestWithRetry<T>(path, options, true);
    }
    // refresh fail - clear session, дальше throw ApiError(401) как обычно
    authAccessor.clearSession();
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get('Content-Type') ?? '';
  const isJson = contentType.includes('application/json') || contentType.includes('application/problem+json');

  if (!response.ok) {
    let problem: ProblemDetails;
    if (isJson) {
      problem = (await response.json()) as ProblemDetails;
    } else {
      problem = {
        type: 'about:blank',
        title: response.statusText || 'Ошибка запроса',
        status: response.status,
        detail: await response.text().catch(() => undefined),
      };
    }
    throw new ApiError(response.status, problem);
  }

  if (!isJson) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/**
 * Типизированный GET. Path вида '/api/v1/topics' даёт типы из OpenAPI.
 */
export async function apiGet<P extends keyof paths>(
  path: P,
  options?: { signal?: AbortSignal },
): Promise<ResponseBody<P, 'get'>> {
  return request(path as string, { method: 'GET', signal: options?.signal });
}

/**
 * Сырой GET с явным типом ответа. Для динамических путей (с подстановкой
 * параметров типа `/api/v1/topics/${id}/graph`), которые TS не выводит из
 * `keyof paths`. Тип ответа подставляется руками: `apiGetRaw<GraphResponse>(...)`.
 */
export async function apiGetRaw<T>(
  path: string,
  options?: { signal?: AbortSignal },
): Promise<T> {
  return request<T>(path, { method: 'GET', signal: options?.signal });
}

export async function apiPost<P extends keyof paths>(
  path: P,
  body: RequestBody<P, 'post'>,
  options?: { signal?: AbortSignal },
): Promise<ResponseBody<P, 'post'>> {
  return request(path as string, { method: 'POST', body, signal: options?.signal });
}

export async function apiPatch<P extends keyof paths>(
  path: P,
  body: RequestBody<P, 'patch'>,
  options?: { signal?: AbortSignal },
): Promise<ResponseBody<P, 'patch'>> {
  return request(path as string, { method: 'PATCH', body, signal: options?.signal });
}

export async function apiDelete<P extends keyof paths>(
  path: P,
  options?: { signal?: AbortSignal },
): Promise<void> {
  await request(path as string, { method: 'DELETE', signal: options?.signal });
}

/**
 * Сырой DELETE для динамических путей (по аналогии с apiGetRaw).
 */
export async function apiDeleteRaw(
  path: string,
  options?: { signal?: AbortSignal },
): Promise<void> {
  await request<void>(path, { method: 'DELETE', signal: options?.signal });
}

/**
 * Сырой PATCH для динамических путей. Тип ответа подставляется руками.
 */
export async function apiPatchRaw<T>(
  path: string,
  body: unknown,
  options?: { signal?: AbortSignal },
): Promise<T> {
  return request<T>(path, { method: 'PATCH', body, signal: options?.signal });
}

/**
 * Сырой POST для динамических путей (`/api/v1/nodes/${id}/sources` и т.п.).
 * Тип ответа подставляется руками: `apiPostRaw<NodeSourceResponse>(...)`.
 */
export async function apiPostRaw<T>(
  path: string,
  body: unknown,
  options?: { signal?: AbortSignal },
): Promise<T> {
  return request<T>(path, { method: 'POST', body, signal: options?.signal });
}

/**
 * POST с multipart/form-data телом - для file uploads и т.п. Content-Type
 * не задаётся вручную: браузер сам формирует с правильным boundary
 * (`multipart/form-data; boundary=----WebKitFormBoundaryXyz`). Если выставить
 * руками - boundary не подставится и Spring multipart parser отвергнет
 * запрос как malformed.
 *
 * Тип ответа подставляется руками:
 * `apiPostMultipart<FileImportResponse>('/api/v1/library/imports/file', formData)`.
 */
export async function apiPostMultipart<T>(
  path: string,
  formData: FormData,
  options?: { signal?: AbortSignal },
): Promise<T> {
  return request<T>(path, { method: 'POST', formData, signal: options?.signal });
}

// === Helper-типы для извлечения тела запроса/ответа из openapi-typescript ===
//
// Springdoc выводит контент-типы как "*/*" (не "application/json"), пока бэк
// явно не пометит. Helper покрывает оба варианта.

type Operations<P extends keyof paths, M extends string> = paths[P] extends Record<M, infer O> ? O : never;

type JsonContent<C> = C extends { 'application/json': infer R }
  ? R
  : C extends { '*/*': infer R }
    ? R
    : never;

type ResponseBody<P extends keyof paths, M extends string> =
  Operations<P, M> extends {
    responses: {
      200?: { content: infer C };
    };
  }
    ? JsonContent<C>
    : Operations<P, M> extends {
          responses: {
            201?: { content: infer C };
          };
        }
      ? JsonContent<C>
      : void;

type RequestBody<P extends keyof paths, M extends string> =
  Operations<P, M> extends { requestBody: { content: infer C } } ? JsonContent<C> : never;
