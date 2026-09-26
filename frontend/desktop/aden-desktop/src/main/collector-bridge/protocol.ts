import type { SessionContext } from '../auth/session-controller'

export const COLLECTOR_PROTOCOL_VERSION = 1
export const COLLECTOR_EXTENSION_ID = 'mnejmmlalapfhnanlnckfdhmfpbahidm'
export const MAX_COLLECTOR_FRAME = 256 * 1024
const TYPES = new Set(['hello', 'capture.lookup', 'capture.prepare', 'capture.commit', 'asset.chunk', 'capture.status', 'library.open'])

export interface CollectorRequest {
  protocolVersion: 1
  messageId: string
  type: string
  payload: Record<string, unknown>
}

export function parseCollectorRequest(value: unknown): CollectorRequest {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new TypeError('采集消息格式错误')
  const input = value as Record<string, unknown>
  if (Object.keys(input).some(key => !['protocolVersion', 'messageId', 'type', 'payload'].includes(key))
      || input.protocolVersion !== 1 || typeof input.messageId !== 'string'
      || !/^[A-Za-z0-9_-]{1,80}$/.test(input.messageId)
      || typeof input.type !== 'string' || !TYPES.has(input.type)
      || !input.payload || typeof input.payload !== 'object' || Array.isArray(input.payload)
      || Buffer.byteLength(JSON.stringify(input)) > MAX_COLLECTOR_FRAME) {
    throw new TypeError('采集消息版本、类型或大小不合法')
  }
  return input as unknown as CollectorRequest
}

export function assertCollectorSession(bound: SessionContext | null, current: SessionContext): asserts bound is SessionContext {
  if (!bound || !current.authenticated || !current.workspaceId || !bound.authenticated
      || bound.sessionEpoch !== current.sessionEpoch || bound.workspaceEpoch !== current.workspaceEpoch
      || bound.workspaceId !== current.workspaceId) throw Object.assign(new Error('连接已失效，请在当前工作空间重新连接插件'), { code: 'SESSION_REVOKED' })
}

export function collectorUuid(input: unknown): string {
  if (typeof input !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(input)) {
    throw new TypeError('采集记录标识不合法')
  }
  return input
}
