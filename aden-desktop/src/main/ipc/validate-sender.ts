import type { IpcMainInvokeEvent } from 'electron'
import { isTrustedRendererUrl, type RendererTrust } from '../config/trusted-origins'

export function assertTrustedSender(event: IpcMainInvokeEvent, trust: RendererTrust): void {
  if (!event.senderFrame || event.senderFrame !== event.sender.mainFrame) {
    throw new Error('IPC 仅允许主 frame 调用')
  }
  if (!isTrustedRendererUrl(event.senderFrame.url, trust)) {
    throw new Error('IPC sender URL 不受信任')
  }
}

export function assertPlainPayload(value: unknown, maxBytes = 32 * 1024): void {
  assertPlainValue(value, 0)
  if (Buffer.byteLength(JSON.stringify(value), 'utf8') > maxBytes) throw new RangeError('IPC payload 超过大小上限')
}

function assertPlainValue(value: unknown, depth: number): void {
  if (depth > 8) throw new TypeError('IPC payload 嵌套过深')
  if (value === null || ['string', 'number', 'boolean'].includes(typeof value)) return
  if (Array.isArray(value)) {
    for (const item of value) assertPlainValue(item, depth + 1)
    return
  }
  if (typeof value !== 'object' || Object.getPrototypeOf(value) !== Object.prototype) {
    throw new TypeError('IPC payload 只能包含 plain JSON value')
  }
  for (const [key, item] of Object.entries(value as Record<string, unknown>)) {
    if (!/^[A-Za-z][A-Za-z0-9]*$/.test(key)) throw new TypeError('IPC payload 字段名非法')
    assertPlainValue(item, depth + 1)
  }
}
