import { Buffer } from 'node:buffer'
import { AdenApiClient, isRecord } from '../transport/api-client'
import { AdenTransportError } from '../transport/error-mapper'
import type { SessionContext } from './session-controller'

export interface CaptchaChallenge {
  readonly enabled: boolean
  readonly uuid: string | null
  readonly jpegDataUrl: string | null
}

export interface LoginInput {
  readonly username: string
  readonly password: string
  readonly code?: string
  readonly uuid?: string
}

export interface AuthenticatedUser {
  readonly user: Record<string, unknown>
  readonly roles: readonly string[]
  readonly permissions: readonly string[]
  readonly session: SessionContext
}

export class AdenAuthService {
  constructor(private readonly api: AdenApiClient) {}

  async captcha(): Promise<CaptchaChallenge> {
    const response = await this.api.request<unknown>({ path: '/captchaImage', authenticated: false })
    const body = requireRecord(response.data, response.correlationId)
    const enabled = body.captchaEnabled
    if (typeof enabled !== 'boolean') {
      throw invalidResponse(response.correlationId, '验证码开关缺失')
    }
    if (!enabled) return Object.freeze({ enabled: false, uuid: null, jpegDataUrl: null })
    if (typeof body.uuid !== 'string' || !/^[A-Za-z0-9_-]{8,128}$/.test(body.uuid)) {
      throw invalidResponse(response.correlationId, '验证码 UUID 非法')
    }
    if (typeof body.img !== 'string' || !isJpegBase64(body.img)) {
      throw invalidResponse(response.correlationId, '验证码不是有效 JPEG base64')
    }
    return Object.freeze({ enabled: true, uuid: body.uuid, jpegDataUrl: `data:image/jpeg;base64,${body.img}` })
  }

  async login(input: LoginInput): Promise<AuthenticatedUser> {
    validateLoginInput(input)
    const login = await this.api.request<unknown>({
      path: '/login',
      method: 'POST',
      authenticated: false,
      body: {
        username: input.username,
        password: input.password,
        code: input.code ?? '',
        uuid: input.uuid ?? ''
      }
    })
    const body = requireRecord(login.data, login.correlationId)
    if (typeof body.token !== 'string' || body.token.length < 8 || body.token.length > 8192) {
      throw invalidResponse(login.correlationId, '登录响应没有有效 Token')
    }
    this.api.session.authenticate(body.token)
    try {
      return await this.getInfo()
    } catch (error) {
      this.api.session.clear()
      throw error
    }
  }

  async getInfo(): Promise<AuthenticatedUser> {
    const response = await this.api.request<unknown>({ path: '/getInfo' })
    const body = requireRecord(response.data, response.correlationId)
    const user = requireRecord(body.user, response.correlationId)
    const roles = stringArray(body.roles, response.correlationId, 'roles')
    const permissions = stringArray(body.permissions, response.correlationId, 'permissions')
    return Object.freeze({ user, roles, permissions, session: this.api.session.context() })
  }

  async logout(): Promise<SessionContext> {
    try {
      if (this.api.session.token()) {
        await this.api.request<unknown>({ path: '/logout', method: 'POST' })
      }
    } finally {
      if (this.api.session.token()) this.api.session.clear()
    }
    return this.api.session.context()
  }
}

function validateLoginInput(input: LoginInput): void {
  if (!input.username.trim() || input.username.length > 64 || input.password.length < 1 || input.password.length > 256) {
    throw new TypeError('用户名或密码格式非法')
  }
  if (input.code !== undefined && input.code.length > 16) throw new TypeError('验证码过长')
  if (input.uuid !== undefined && input.uuid.length > 128) throw new TypeError('验证码 UUID 过长')
}

function isJpegBase64(value: string): boolean {
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(value) || value.length % 4 !== 0 || value.length > 2_000_000) return false
  try {
    const bytes = Buffer.from(value, 'base64')
    if (bytes.length < 4 || bytes[0] !== 0xff || bytes[1] !== 0xd8 || bytes[2] !== 0xff
        || bytes[bytes.length - 2] !== 0xff || bytes[bytes.length - 1] !== 0xd9) return false
    return bytes.toString('base64') === value
  } catch {
    return false
  }
}

function requireRecord(value: unknown, correlationId: string): Record<string, unknown> {
  if (!isRecord(value)) throw invalidResponse(correlationId, '服务响应必须是 JSON object')
  return value
}

function stringArray(value: unknown, correlationId: string, field: string): readonly string[] {
  if (!Array.isArray(value) || value.some((item) => typeof item !== 'string')) {
    throw invalidResponse(correlationId, `${field} 必须是字符串数组`)
  }
  return Object.freeze([...value]) as readonly string[]
}

function invalidResponse(correlationId: string, message: string): AdenTransportError {
  return new AdenTransportError('INVALID_RESPONSE', message, 200, correlationId, false)
}
