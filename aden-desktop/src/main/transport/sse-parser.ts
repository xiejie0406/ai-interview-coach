export interface ParsedSseEvent {
  readonly event: string
  readonly id: string
  readonly data: string
}

export interface SseParserLimits {
  readonly maxBufferBytes: number
  readonly maxLineBytes: number
  readonly maxEventBytes: number
  readonly maxDataBytes: number
}

const DEFAULT_LIMITS: SseParserLimits = Object.freeze({
  maxBufferBytes: 256 * 1024,
  maxLineBytes: 64 * 1024,
  maxEventBytes: 192 * 1024,
  maxDataBytes: 128 * 1024
})

/** 增量 UTF-8 SSE parser；只有完整空行才产出事件，EOF 半帧永远丢弃。 */
export class BoundedSseParser {
  #decoder = new TextDecoder('utf-8', { fatal: true })
  #buffer = ''
  #eventName = ''
  #eventId = ''
  #data = ''
  #eventBytes = 0

  constructor(readonly limits: SseParserLimits = DEFAULT_LIMITS) {}

  feed(chunk: Uint8Array): ParsedSseEvent[] {
    const decoded = this.#decoder.decode(chunk, { stream: true })
    this.#buffer += decoded
    if (byteLength(this.#buffer) > this.limits.maxBufferBytes) throw new RangeError('SSE 累计 buffer 超限')
    const events: ParsedSseEvent[] = []
    while (true) {
      const boundary = findLineBoundary(this.#buffer)
      if (!boundary) break
      const line = this.#buffer.slice(0, boundary.index)
      this.#buffer = this.#buffer.slice(boundary.index + boundary.length)
      if (byteLength(line) > this.limits.maxLineBytes) throw new RangeError('SSE 单行超限')
      const emitted = this.#consumeLine(line)
      if (emitted) events.push(emitted)
    }
    return events
  }

  discardIncomplete(): void {
    this.#buffer = ''
    this.#eventName = ''
    this.#eventId = ''
    this.#data = ''
    this.#eventBytes = 0
    // 不 flush 半个 UTF-8 code point；直接换 decoder 才是“半帧丢弃”。
    this.#decoder = new TextDecoder('utf-8', { fatal: true })
  }

  #consumeLine(line: string): ParsedSseEvent | null {
    if (line === '') {
      if (this.#data === '') {
        this.#resetEvent()
        return null
      }
      const event = Object.freeze({ event: this.#eventName || 'message', id: this.#eventId, data: this.#data })
      this.#resetEvent()
      return event
    }
    if (line.startsWith(':')) return null
    this.#eventBytes += byteLength(line)
    if (this.#eventBytes > this.limits.maxEventBytes) throw new RangeError('SSE 单事件超限')
    const separator = line.indexOf(':')
    const field = separator < 0 ? line : line.slice(0, separator)
    let value = separator < 0 ? '' : line.slice(separator + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') this.#eventName = value
    if (field === 'id' && !value.includes('\0')) this.#eventId = value
    if (field === 'data') {
      // Aden current 每个 event 只允许一个 data 行，避免模糊拼接。
      if (this.#data !== '') throw new TypeError('SSE 单事件只允许一个 data 行')
      if (byteLength(value) > this.limits.maxDataBytes) throw new RangeError('SSE data 超限')
      this.#data = value
    }
    return null
  }

  #resetEvent(): void {
    this.#eventName = ''
    this.#eventId = ''
    this.#data = ''
    this.#eventBytes = 0
  }
}

function findLineBoundary(value: string): { index: number; length: number } | null {
  const lf = value.indexOf('\n')
  const cr = value.indexOf('\r')
  if (lf < 0 && cr < 0) return null
  const index = lf < 0 ? cr : cr < 0 ? lf : Math.min(lf, cr)
  return { index, length: value[index] === '\r' && value[index + 1] === '\n' ? 2 : 1 }
}

function byteLength(value: string): number {
  return new TextEncoder().encode(value).byteLength
}
