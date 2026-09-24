import { randomUUID } from 'node:crypto'
import type { RuntimeConfig } from '../config/runtime-config'
import { SessionController, type SessionContext } from '../auth/session-controller'
import { AdenTransportError, mapHttpError } from './error-mapper'

export type FetchLike = (input: string | URL | Request, init?: RequestInit) => Promise<Response>

export interface ApiRequest {
  readonly path: string
  readonly method?: 'GET' | 'POST' | 'DELETE'
  readonly body?: unknown
  readonly headers?: Readonly<Record<string, string>>
  readonly authenticated?: boolean
  readonly expectedContentType?: 'json' | 'none'
  readonly signal?: AbortSignal
}

export interface ApiResponse<T> {
  readonly data: T
  readonly correlationId: string
  readonly context: SessionContext
  readonly headers: Headers
}

export interface ApiClientLimits {
  readonly timeoutMs: number
  readonly maxBodyBytes: number
}

const DEFAULT_LIMITS: ApiClientLimits = Object.freeze({ timeoutMs: 15_000, maxBodyBytes: 2 * 1024 * 1024 })

export class AdenApiClient {
  readonly #base: URL
  readonly #origin: string

  constructor(
    config: RuntimeConfig,
    readonly session: SessionController,
    readonly fetchImpl: FetchLike = globalThis.fetch,
    readonly limits: ApiClientLimits = DEFAULT_LIMITS
  ) {
    this.#base = new URL(`${config.apiBaseUrl}/`)
    this.#origin = config.apiOrigin
  }

  async request<T>(request: ApiRequest): Promise<ApiResponse<T>> {
    const correlationId = randomUUID()
    const url = this.#resolvePath(request.path, correlationId)
    const context = this.session.context()
    const authenticated = request.authenticated !== false
    const token = authenticated ? this.session.token() : null
    if (authenticated && !token) {
      throw new AdenTransportError('UNAUTHENTICATED', '请先登录', 401, correlationId, false)
    }

    const tracked = this.session.trackedAbortController()
    const timeout = setTimeout(() => tracked.abort('timeout'), this.limits.timeoutMs)
    const onExternalAbort = (): void => tracked.abort('external-abort')
    request.signal?.addEventListener('abort', onExternalAbort, { once: true })
    try {
      const headers = new Headers(request.headers)
      headers.set('Accept', 'application/json')
      headers.set('X-Correlation-Id', correlationId)
      if (request.body !== undefined) headers.set('Content-Type', 'application/json')
      if (token) headers.set('Authorization', `Bearer ${token}`)
      let response: Response
      try {
        response = await this.fetchImpl(url, {
          method: request.method ?? 'GET',
          headers,
          body: request.body === undefined ? undefined : JSON.stringify(request.body),
          redirect: 'manual',
          signal: tracked.signal,
          credentials: 'omit',
          cache: 'no-store'
        })
      } catch (error) {
        if (tracked.signal.aborted) {
          const timeoutAbort = tracked.signal.reason === 'timeout'
          throw new AdenTransportError(
            timeoutAbort ? 'TIMEOUT' : 'ABORTED',
            timeoutAbort ? '请求超时' : '请求已取消',
            null,
            correlationId,
            timeoutAbort
          )
        }
        throw new AdenTransportError('NETWORK_ERROR', '无法连接中心服务', null, correlationId, true)
      }

      if (response.status >= 300 && response.status < 400) {
        throw new AdenTransportError('REDIRECT_REJECTED', '安全策略拒绝 HTTP 跳转', response.status, correlationId, false)
      }
      if (!response.ok) {
        if (response.status === 401) this.session.clear()
        throw mapHttpError(response.status, correlationId)
      }
      if (!this.session.isCurrent(context)) {
        throw new AdenTransportError('ABORTED', '会话上下文已经变化', null, correlationId, false)
      }

      const expected = request.expectedContentType ?? 'json'
      if (expected === 'none') {
        return { data: undefined as T, correlationId, context, headers: response.headers }
      }
      const contentType = response.headers.get('content-type')?.toLowerCase() ?? ''
      if (!contentType.includes('application/json')) {
        throw new AdenTransportError('INVALID_CONTENT_TYPE', '服务返回了非 JSON 内容', response.status, correlationId, false)
      }
      const bytes = await readBoundedBody(response, this.limits.maxBodyBytes, correlationId)
      let data: unknown
      try {
        data = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes))
      } catch {
        throw new AdenTransportError('INVALID_RESPONSE', '服务返回的 JSON 无效', response.status, correlationId, false)
      }
      assertAjaxResultSuccess(data, response.status, correlationId)
      return { data: data as T, correlationId, context, headers: response.headers }
    } finally {
      clearTimeout(timeout)
      request.signal?.removeEventListener('abort', onExternalAbort)
      this.session.release(tracked)
    }
  }

  #resolvePath(path: string, correlationId: string): URL {
    if (!path.startsWith('/') || path.startsWith('//') || /[\\\r\n]/.test(path)) {
      throw new AdenTransportError('INVALID_REQUEST', '请求路径非法', null, correlationId, false)
    }
    const url = new URL(path.slice(1), this.#base)
    if (url.origin !== this.#origin || url.username || url.password || url.hash) {
      throw new AdenTransportError('INVALID_REQUEST', '请求不能离开已冻结的 API Origin', null, correlationId, false)
    }
    return url
  }
}

async function readBoundedBody(
  response: Response,
  maxBytes: number,
  correlationId: string
): Promise<Uint8Array> {
  const declared = response.headers.get('content-length')
  if (declared && Number(declared) > maxBytes) {
    throw new AdenTransportError('RESPONSE_TOO_LARGE', '服务响应超过大小上限', response.status, correlationId, false)
  }
  if (!response.body) return new Uint8Array()
  const reader = response.body.getReader()
  const chunks: Uint8Array[] = []
  let size = 0
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    size += value.byteLength
    if (size > maxBytes) {
      await reader.cancel('response-too-large')
      throw new AdenTransportError('RESPONSE_TOO_LARGE', '服务响应超过大小上限', response.status, correlationId, false)
    }
    chunks.push(value)
  }
  const result = new Uint8Array(size)
  let offset = 0
  for (const chunk of chunks) {
    result.set(chunk, offset)
    offset += chunk.byteLength
  }
  return result
}

function assertAjaxResultSuccess(data: unknown, status: number, correlationId: string): void {
  if (!isRecord(data) || typeof data.code !== 'number') return
  if (data.code !== 200) {
    const safeMessage = typeof data.msg === 'string' && data.msg.length <= 200
      ? data.msg
      : 'RuoYi 业务请求失败'
    throw new AdenTransportError('RUOYI_ERROR', safeMessage, status, correlationId, false)
  }
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}
