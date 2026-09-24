import type { RendererTrust } from '../config/trusted-origins'

export function buildContentSecurityPolicy(trust: RendererTrust): string {
  const connect = trust.development && trust.devOrigin
    ? `'self' ${trust.devOrigin} ${toWebSocketOrigin(trust.devOrigin)}`
    : "'none'"
  return [
    "default-src 'self'",
    "base-uri 'none'",
    "object-src 'none'",
    "frame-src 'none'",
    "form-action 'none'",
    "script-src 'self'",
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data:",
    `connect-src ${connect}`
  ].join('; ')
}

function toWebSocketOrigin(origin: string): string {
  const value = new URL(origin)
  value.protocol = value.protocol === 'https:' ? 'wss:' : 'ws:'
  return value.origin
}
