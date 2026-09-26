import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { parseWorkspaceBootstrap } from '../../src/shared/contracts/operator-bootstrap'

const fixturePath = join(process.cwd(), '../../../contracts/aden/examples/current/operator/bootstrap-long-max-watermark.json')

describe('WorkspaceBootstrap runtime boundary', () => {
  it('preserves int64 above 2^53 and Long.MAX_VALUE as strings', () => {
    const parsed = parseWorkspaceBootstrap(JSON.parse(readFileSync(fixturePath, 'utf8')))
    expect(parsed.tasks.items[0]?.version).toBe('9007199254740992')
    expect(parsed.streamWatermark).toBe('9223372036854775807')
    expect(typeof parsed.streamWatermark).toBe('string')
  })

  it('rejects number coercion, unknown fields and missing capability entries', () => {
    const original = JSON.parse(readFileSync(fixturePath, 'utf8'))
    expect(() => parseWorkspaceBootstrap({ ...original, streamWatermark: 9007199254740992 })).toThrow()
    expect(() => parseWorkspaceBootstrap({ ...original, injected: true })).toThrow(/未知字段/)
    expect(() => parseWorkspaceBootstrap({ ...original, capabilities: original.capabilities.slice(0, 3) })).toThrow(/四项/)
  })
})
