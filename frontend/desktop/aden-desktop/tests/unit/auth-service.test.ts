import { describe, expect, it, vi } from 'vitest'
import { resolveRuntimeConfig } from '../../src/main/config/runtime-config'
import { SessionController } from '../../src/main/auth/session-controller'
import { AdenApiClient, type FetchLike } from '../../src/main/transport/api-client'
import { AdenAuthService } from '../../src/main/auth/auth-service'

const config = resolveRuntimeConfig('https://aden.example.test', false)
const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0x00, 0xff, 0xd9]).toString('base64')

describe('AdenAuthService', () => {
  it('accepts disabled captcha without synthetic image fields', async () => {
    const service = serviceFor([{ code: 200, captchaEnabled: false }])
    await expect(service.auth.captcha()).resolves.toEqual({ enabled: false, uuid: null, jpegDataUrl: null })
  })

  it('accepts a real JPEG signature and rejects arbitrary base64', async () => {
    const valid = serviceFor([{ code: 200, captchaEnabled: true, uuid: 'abcdef123456', img: jpeg }])
    await expect(valid.auth.captcha()).resolves.toMatchObject({ enabled: true, jpegDataUrl: `data:image/jpeg;base64,${jpeg}` })
    const invalid = serviceFor([{ code: 200, captchaEnabled: true, uuid: 'abcdef123456', img: Buffer.from('not-jpeg').toString('base64') }])
    await expect(invalid.auth.captcha()).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })

  it('keeps token in main memory and calls getInfo immediately', async () => {
    const fixture = serviceFor([
      { code: 200, token: 'main-only-token' },
      { code: 200, user: { userName: 'synthetic' }, roles: ['operator'], permissions: ['aden:task:list'] }
    ])
    const result = await fixture.auth.login({ username: 'synthetic', password: 'secret', code: '1', uuid: 'u' })
    expect(result.permissions).toEqual(['aden:task:list'])
    expect(fixture.session.token()).toBe('main-only-token')
    expect(fixture.fetcher).toHaveBeenCalledTimes(2)
    const secondHeaders = new Headers((fixture.fetcher.mock.calls[1][1] as RequestInit).headers)
    expect(secondHeaders.get('Authorization')).toBe('Bearer main-only-token')
    expect(JSON.stringify(result)).not.toContain('main-only-token')
  })

  it('clears token when getInfo fails after login', async () => {
    const fixture = serviceFor([{ code: 200, token: 'temporary-token' }, { code: 500, msg: '拒绝' }])
    await expect(fixture.auth.login({ username: 'synthetic', password: 'secret' })).rejects.toMatchObject({ code: 'RUOYI_ERROR' })
    expect(fixture.session.token()).toBeNull()
  })
})

function serviceFor(bodies: unknown[]) {
  const session = new SessionController()
  const fetcher = vi.fn<FetchLike>(async () => new Response(JSON.stringify(bodies.shift()), {
    status: 200, headers: { 'content-type': 'application/json' }
  }))
  const api = new AdenApiClient(config, session, fetcher)
  return { auth: new AdenAuthService(api), session, fetcher }
}
