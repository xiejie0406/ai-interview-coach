import { describe, expect, it, vi } from 'vitest'
import { resolveRuntimeConfig } from '../../src/main/config/runtime-config'
import { SessionController } from '../../src/main/auth/session-controller'
import { AdenApiClient, type FetchLike } from '../../src/main/transport/api-client'
import { AdenTransportError } from '../../src/main/transport/error-mapper'

const config = resolveRuntimeConfig('https://aden.example.test', false)

describe('AdenApiClient', () => {
  it('bounds binary files and rejects bytes delivered after session expiry', async () => {
    const session = authenticatedSession()
    const api = new AdenApiClient(config, session, async () => new Response(new Uint8Array(20)))
    await expect(api.request({ path: '/asset', expectedContentType: 'binary', maxResponseBytes: 10 })).rejects.toMatchObject({ code: 'RESPONSE_TOO_LARGE' })
    let finish: (() => void) | undefined
    const late = new AdenApiClient(config, session, async () => new Response(new ReadableStream({ start(controller) { finish = () => { session.clear(); controller.enqueue(new Uint8Array([1])); controller.close() } } })))
    const pending = late.request({ path: '/asset', expectedContentType: 'binary' })
    await Promise.resolve(); finish?.()
    await expect(pending).rejects.toMatchObject({ code: 'ABORTED' })
  })
  it.each([301, 302, 307, 308])('rejects HTTP %i without replaying credentials', async (status) => {
    const session = authenticatedSession()
    const fetcher = vi.fn<FetchLike>(async () => new Response(null, {
      status,
      headers: { location: status % 2 ? 'https://evil.invalid/steal' : '/same-origin' }
    }))
    const api = new AdenApiClient(config, session, fetcher)
    await expect(api.request({ path: '/getInfo' })).rejects.toMatchObject({ code: 'REDIRECT_REJECTED', status })
    expect(fetcher).toHaveBeenCalledTimes(1)
    const init = fetcher.mock.calls[0][1] as RequestInit
    expect(init.redirect).toBe('manual')
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer test-secret-token')
  })

  it('rejects non-JSON, oversized and RuoYi business errors', async () => {
    const session = authenticatedSession()
    const responses = [
      new Response('<html>', { status: 200, headers: { 'content-type': 'text/html' } }),
      new Response(JSON.stringify({ value: 'x'.repeat(100) }), { status: 200, headers: { 'content-type': 'application/json' } }),
      jsonResponse({ code: 500, msg: '业务拒绝' })
    ]
    const api = new AdenApiClient(config, session, async () => responses.shift() as Response, {
      timeoutMs: 100, maxBodyBytes: 64
    })
    await expect(api.request({ path: '/one' })).rejects.toMatchObject({ code: 'INVALID_CONTENT_TYPE' })
    await expect(api.request({ path: '/two' })).rejects.toMatchObject({ code: 'RESPONSE_TOO_LARGE' })
    await expect(api.request({ path: '/three' })).rejects.toMatchObject({ code: 'RUOYI_ERROR' })
  })

  it('clears session on 401 but preserves it on 403', async () => {
    const first = authenticatedSession()
    const unauthorized = new AdenApiClient(config, first, async () => new Response(null, { status: 401 }))
    await expect(unauthorized.request({ path: '/private' })).rejects.toMatchObject({ code: 'UNAUTHENTICATED' })
    expect(first.token()).toBeNull()

    const second = authenticatedSession()
    const forbidden = new AdenApiClient(config, second, async () => new Response(null, { status: 403 }))
    await expect(forbidden.request({ path: '/private' })).rejects.toMatchObject({ code: 'FORBIDDEN' })
    expect(second.token()).toBe('test-secret-token')
  })

  it.each([[429, 'RATE_LIMITED'], [503, 'SERVER_ERROR']] as const)('maps HTTP %i as retryable %s', async (status, code) => {
    const api = new AdenApiClient(config, authenticatedSession(), async () => new Response(null, { status }))
    await expect(api.request({ path: '/busy' })).rejects.toMatchObject({ code, retryable: true })
  })

  it('rejects malformed JSON without exposing its body', async () => {
    const api = new AdenApiClient(config, authenticatedSession(), async () => new Response('{secret', {
      status: 200, headers: { 'content-type': 'application/json' }
    }))
    await expect(api.request({ path: '/broken' })).rejects.toMatchObject({ code: 'INVALID_RESPONSE', message: '服务返回的 JSON 无效' })
  })

  it('times out and never accepts an absolute or authority-relative path', async () => {
    const session = authenticatedSession()
    const api = new AdenApiClient(config, session, async (_url, init) => {
      await new Promise<void>((_resolve, reject) => init?.signal?.addEventListener('abort', () => reject(new Error('aborted'))))
      return jsonResponse({})
    }, { timeoutMs: 5, maxBodyBytes: 1024 })
    await expect(api.request({ path: '/slow' })).rejects.toMatchObject({ code: 'TIMEOUT' })
    await expect(api.request({ path: '//evil.invalid' })).rejects.toBeInstanceOf(AdenTransportError)
    await expect(api.request({ path: 'https://evil.invalid' })).rejects.toBeInstanceOf(AdenTransportError)
  })
})

function authenticatedSession(): SessionController {
  const session = new SessionController()
  session.authenticate('test-secret-token')
  return session
}

function jsonResponse(value: unknown): Response {
  return new Response(JSON.stringify(value), { status: 200, headers: { 'content-type': 'application/json' } })
}
