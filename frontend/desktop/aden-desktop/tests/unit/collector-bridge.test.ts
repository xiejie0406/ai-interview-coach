import { describe, expect, it, vi } from 'vitest'
import { CollectorBridge } from '../../src/main/collector-bridge/bridge'
import { COLLECTOR_EXTENSION_ID, parseCollectorRequest } from '../../src/main/collector-bridge/protocol'
import { SessionController } from '../../src/main/auth/session-controller'
import type { AdenApiClient } from '../../src/main/transport/api-client'

const workspace = '11111111-1111-4111-8111-111111111111'
const capture = '22222222-2222-4222-8222-222222222222'
function fixture() {
  const session = new SessionController()
  session.authenticate('test-only-token')
  session.selectWorkspace(workspace)
  const request = vi.fn().mockImplementation(async (input: { path: string }) => ({ data: input.path === '/getInfo' ? { user: { userId: 100 } } : { captureId: capture } }))
  const confirmPair = vi.fn().mockResolvedValue(true)
  const bridge = new CollectorBridge({ helperPath: 'unused', session, api: { request } as unknown as AdenApiClient,
    confirmPair, openLibrary: vi.fn() })
  const send = (type: string, payload: unknown) => bridge.handle({ protocolVersion: 1, messageId: 'test-1', type, payload })
  return { session, request, confirmPair, bridge, send }
}

describe('collector trusted bridge', () => {
  it('rejects unpaired requests and mismatched extension identity', async () => {
    const f = fixture()
    expect((await f.send('capture.prepare', { captureId: capture, source: 'JD' })).ok).toBe(false)
    expect((await f.send('hello', { extensionId: 'wrong' })).ok).toBe(false)
    expect(f.request).not.toHaveBeenCalled()
    expect(f.confirmPair).not.toHaveBeenCalled()
  })
  it('pairs explicitly and fixes workspace, rejecting a switched session', async () => {
    const f = fixture()
    expect((await f.send('hello', { extensionId: COLLECTOR_EXTENSION_ID })).ok).toBe(true)
    expect((await f.send('capture.prepare', { captureId: capture, source: 'JD' })).ok).toBe(true)
    expect(f.request).toHaveBeenCalledWith(expect.objectContaining({ path: `/api/v1/aden/collection/workspaces/${workspace}/captures` }))
    f.session.selectWorkspace('33333333-3333-4333-8333-333333333333')
    expect((await f.send('capture.status', { captureId: capture })).ok).toBe(false)
    expect(f.request).toHaveBeenCalledTimes(2)
  })
  it('does not accept workspace override or arbitrary operations', async () => {
    const f = fixture()
    await f.send('hello', { extensionId: COLLECTOR_EXTENSION_ID })
    f.request.mockClear()
    expect((await f.send('capture.prepare', { captureId: capture, source: 'JD', workspaceId: workspace })).ok).toBe(false)
    expect((await f.send('shell', { command: 'anything' })).ok).toBe(false)
    expect(f.request).not.toHaveBeenCalled()
  })
  it('invalidates a pairing if the account changes during confirmation', async () => {
    const f = fixture()
    f.confirmPair.mockImplementation(async () => { f.session.clear(); return true })
    expect((await f.send('hello', { extensionId: COLLECTOR_EXTENSION_ID })).ok).toBe(false)
  })
  it('rejects oversized messages and unexpected envelope fields', () => {
    expect(() => parseCollectorRequest({ protocolVersion: 1, messageId: 'a', type: 'hello', payload: {}, token: 'x' })).toThrow()
    expect(() => parseCollectorRequest({ protocolVersion: 1, messageId: 'a', type: 'hello', payload: { data: 'x'.repeat(300000) } })).toThrow()
  })
})
