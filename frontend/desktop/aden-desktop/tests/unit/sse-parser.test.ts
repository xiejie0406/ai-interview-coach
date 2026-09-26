import { describe, expect, it } from 'vitest'
import { BoundedSseParser } from '../../src/main/transport/sse-parser'

const encoder = new TextEncoder()

describe('BoundedSseParser', () => {
  it('parses UTF-8 split across chunks only after an empty line', () => {
    const parser = new BoundedSseParser()
    const bytes = encoder.encode('event: aden_event\nid: cursor-1\ndata: {"title":"中文"}\n\n')
    expect(parser.feed(bytes.slice(0, bytes.length - 1))).toEqual([])
    expect(parser.feed(bytes.slice(bytes.length - 1))).toEqual([
      { event: 'aden_event', id: 'cursor-1', data: '{"title":"中文"}' }
    ])
  })

  it('drops a half frame on disconnect', () => {
    const parser = new BoundedSseParser()
    expect(parser.feed(encoder.encode('event: aden_event\nid: old\ndata: {"partial":'))).toEqual([])
    parser.discardIncomplete()
    expect(parser.feed(encoder.encode('event: aden_event\nid: new\ndata: {}\n\n'))[0]?.id).toBe('new')
  })

  it('rejects never-ending lines, multiple data lines and oversized data', () => {
    const limits = { maxBufferBytes: 32, maxLineBytes: 16, maxEventBytes: 24, maxDataBytes: 8 }
    expect(() => new BoundedSseParser(limits).feed(encoder.encode('x'.repeat(33)))).toThrow(/buffer/)
    expect(() => new BoundedSseParser().feed(encoder.encode('data: {}\ndata: {}\n\n'))).toThrow(/一个 data/)
    expect(() => new BoundedSseParser(limits).feed(encoder.encode('data: 123456789\n\n'))).toThrow(/data/)
  })
})
