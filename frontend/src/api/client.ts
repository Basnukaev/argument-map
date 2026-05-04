import type { paths } from '@/api/types';

/**
 * Базовый URL бэка. Берётся из VITE_API_URL, по умолчанию localhost:9090.
 */
const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:9090';

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

type Method = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';

const MUTATING_METHODS: ReadonlySet<Method> = new Set(['POST', 'PATCH', 'PUT', 'DELETE']);

interface RequestOptions {
  method?: Method;
  body?: unknown;
  signal?: AbortSignal;
}

/**
 * Низкоуровневый запрос к API. Сам не используется - через типизированные
 * хелперы apiGet / apiPost / apiPatch / apiDelete.
 */
async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = {
    Accept: 'application/json',
  };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (MUTATING_METHODS.has(method) && DEV_USER_ID) {
    headers['X-User-Id'] = DEV_USER_ID;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    signal: options.signal,
  });

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
