export type ApiErrorPayload = {
  error: {
    code: string
    userMessage: string
    retryable: boolean
    correlationId: string
    details?: Record<string, unknown>
  }
}

export class ApiClientError extends Error {
  constructor(public readonly status: number, public readonly payload?: ApiErrorPayload) {
    super(payload?.error.userMessage ?? (status === 0 ? '网络连接失败' : '请求失败'))
    this.name = 'ApiClientError'
  }
}

export type ApiResponse<T> = {
  data: T
  etag?: string
  correlationId?: string
}

const RUOYI_TOKEN_KEY = 'ruoyi-token'

function getRuoYiToken() {
  return localStorage.getItem(RUOYI_TOKEN_KEY)
}

export function clearRuoYiToken() {
  localStorage.removeItem(RUOYI_TOKEN_KEY)
}

function correlationId() {
  return globalThis.crypto?.randomUUID?.() ?? `portal-${Date.now()}`
}

export async function apiRequestWithMeta<T>(path: string, init: RequestInit = {}): Promise<ApiResponse<T>> {
  if (!path.startsWith('/') || path.startsWith('//') || path.includes('://')) {
    throw new Error('API path 必须是 /api/v1 下的同源绝对路径')
  }
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  headers.set('X-Correlation-ID', correlationId())
  const token = getRuoYiToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  let response: Response
  try {
    const prefix = ['/login', '/captchaImage', '/getInfo', '/getRouters', '/logout', '/system/'].some((item) => path.startsWith(item)) ? '/api' : '/api/v1'
    response = await fetch(`${prefix}${path}`, { ...init, method, headers, credentials: 'omit' })
  } catch {
    throw new ApiClientError(0)
  }
  const body = response.status === 204 ? undefined : await response.json().catch(() => undefined)
  const ruoyiBody = body as { code?: number; msg?: string; data?: unknown } | undefined
  if (!response.ok || (ruoyiBody?.code !== undefined && ruoyiBody.code !== 200)) {
    if (ruoyiBody?.msg) throw new ApiClientError(response.status || 500, { error: { code: `RUOYI_${ruoyiBody.code ?? 500}`, userMessage: ruoyiBody.msg, retryable: (response.status || 500) >= 500, correlationId: headers.get('X-Correlation-ID') ?? 'portal' } })
    throw new ApiClientError(response.status, body as ApiErrorPayload | undefined)
  }
  return {
    data: body as T,
    etag: response.headers.get('ETag') ?? undefined,
    correlationId: response.headers.get('X-Correlation-ID') ?? undefined,
  }
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  return (await apiRequestWithMeta<T>(path, init)).data
}

export const jsonBody = (value: unknown) => JSON.stringify(value)
export const newOperationKey = () => globalThis.crypto.randomUUID()
