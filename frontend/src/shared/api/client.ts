export type ApiErrorPayload = {
  error: {
    code: string;
    userMessage: string;
    retryable: boolean;
    retryAfterSeconds?: number | null;
    correlationId: string;
    details?: Record<string, unknown>;
  };
};

export type ApiResponse<T> = {
  data: T;
  etag?: string;
  correlationId?: string;
  retryAfterSeconds?: number;
};

export type RuoYiResult<T> = {
  code: number;
  msg?: string;
  data?: T;
  token?: string;
};

export const SESSION_EXPIRED_EVENT = 'aic:session-expired';
export const SESSION_CLEARED_EVENT = 'aic:session-cleared';

export class ApiClientError extends Error {
  readonly retryAfterSeconds?: number;

  constructor(
    public readonly status: number,
    public readonly payload: ApiErrorPayload | undefined,
    options: { retryAfterSeconds?: number; cause?: unknown } = {},
  ) {
    super(payload?.error.userMessage ?? (status === 0 ? '网络连接失败' : '请求失败'), {
      cause: options.cause,
    });
    this.name = 'ApiClientError';
    this.retryAfterSeconds = options.retryAfterSeconds;
  }

  get retryable() {
    return this.payload?.error.retryable ?? (this.status === 0 || this.status >= 500);
  }

  get code() {
    return this.payload?.error.code ?? (this.status === 0 ? 'NETWORK_ERROR' : 'UNKNOWN_ERROR');
  }

  get correlationId() {
    return this.payload?.error.correlationId;
  }

  get details() {
    return this.payload?.error.details;
  }
}

function createCorrelationId() {
  return globalThis.crypto?.randomUUID?.() ?? `web-${randomHex(16)}`;
}

export function newIdempotencyKey() {
  return globalThis.crypto?.randomUUID?.() ?? `web-op-${randomHex(24)}`;
}

export function versionEtag(version: number) {
  return `"v${version}"`;
}

function randomHex(byteLength: number) {
  if (!globalThis.crypto?.getRandomValues) {
    throw new Error('secure browser randomness is unavailable');
  }
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(byteLength));
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}

function parseRetryAfter(value: string | null) {
  if (!value) return undefined;
  const seconds = Number(value);
  if (Number.isFinite(seconds) && seconds >= 0) return seconds;
  const timestamp = Date.parse(value);
  if (!Number.isNaN(timestamp)) return Math.max(0, Math.ceil((timestamp - Date.now()) / 1000));
  return undefined;
}

function isApiErrorPayload(value: unknown): value is ApiErrorPayload {
  if (!value || typeof value !== 'object' || !('error' in value)) return false;
  const error = (value as { error?: unknown }).error;
  return Boolean(
    error
      && typeof error === 'object'
      && typeof (error as { code?: unknown }).code === 'string'
      && typeof (error as { retryable?: unknown }).retryable === 'boolean'
      && typeof (error as { correlationId?: unknown }).correlationId === 'string',
  );
}

function shouldClearSessionOnUnauthorized(path: string) {
  const basePath = path.split(/[?#]/, 1)[0];
  return !['/login', '/captchaImage'].includes(basePath);
}

function assertSameOriginApiPath(path: string) {
  if (!path.startsWith('/') || path.startsWith('//') || path.includes('://')) {
    throw new Error('API path must be a same-origin absolute path under /api or /api/v1');
  }
}

export async function apiRequestWithMeta<T>(path: string, init: RequestInit = {}): Promise<ApiResponse<T>> {
  assertSameOriginApiPath(path);
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  headers.set('X-Correlation-ID', createCorrelationId());
  const token = getRuoYiToken();
  if (token && !headers.has('Authorization')) headers.set('Authorization', `Bearer ${token}`);
  if (init.body !== undefined && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  let response: Response;
  try {
    const apiPrefix = ['/login', '/captchaImage', '/getInfo', '/getRouters', '/logout', '/system/'].some((prefix) => path.startsWith(prefix)) ? '/api' : '/api/v1';
    response = await fetch(`${apiPrefix}${path}`, {
      ...init,
      method,
      credentials: 'omit',
      headers,
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') throw cause;
    throw new ApiClientError(0, undefined, { cause });
  }

  const text = await response.text();
  let body: unknown;
  try {
    body = text ? JSON.parse(text) : undefined;
  } catch {
    body = undefined;
  }

  const retryAfterSeconds = parseRetryAfter(response.headers.get('Retry-After'));
  const ruoyiResult = body && typeof body === 'object' && 'code' in body && typeof (body as { code?: unknown }).code === 'number'
    ? body as RuoYiResult<unknown>
    : undefined;
  if (!response.ok || (ruoyiResult && ruoyiResult.code !== 200)) {
    const payload = isApiErrorPayload(body) ? body : undefined;
    const status = response.status || (ruoyiResult?.code && ruoyiResult.code >= 400 ? ruoyiResult.code : 500);
    if (status === 401 && typeof window !== 'undefined' && shouldClearSessionOnUnauthorized(path)) {
      window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT, { detail: { code: payload?.error.code ?? ruoyiResult?.code } }));
    }
    if (!payload && ruoyiResult?.msg) {
      throw new ApiClientError(status, {
        error: {
          code: `RUOYI_${ruoyiResult.code}`,
          userMessage: ruoyiResult.msg,
          retryable: status === 0 || status >= 500,
          correlationId: headers.get('X-Correlation-ID') ?? 'client',
        },
      }, { retryAfterSeconds });
    }
    throw new ApiClientError(status, payload, { retryAfterSeconds });
  }

  return {
    data: body as T,
    etag: response.headers.get('ETag') ?? undefined,
    correlationId: response.headers.get('X-Correlation-ID') ?? undefined,
    retryAfterSeconds,
  };
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  return (await apiRequestWithMeta<T>(path, init)).data;
}

export function jsonBody(value: unknown) {
  return JSON.stringify(value);
}
import { getRuoYiToken } from '../session/ruoyiToken';
