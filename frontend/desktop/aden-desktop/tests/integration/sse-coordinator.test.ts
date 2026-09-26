import { describe, expect, it, vi } from 'vitest'
import { resolveRuntimeConfig } from '../../src/main/config/runtime-config'
import { SessionController } from '../../src/main/auth/session-controller'
import { AdenSseCoordinator } from '../../src/main/transport/sse-client'
import type { FetchLike } from '../../src/main/transport/api-client'
import { parseCanonicalInt64 } from '../../src/shared/contracts/wire-scalars'

const workspaceId = '11111111-1111-4111-8111-111111111111'
const config = resolveRuntimeConfig('https://aden.example.test', false)

describe('AdenSseCoordinator integration', () => {
  it.each([400, 410, 302])('requires bootstrap for HTTP %i and never follows redirect', async (status) => {
    const fixture = createFixture(async () => new Response(null, { status }))
    await fixture.coordinator.start(workspaceId, 'cursor-0', parseCanonicalInt64('0'))
    expect(fixture.bootstrap).toEqual(['bootstrap-required'])
    expect(fixture.fetcher).toHaveBeenCalledTimes(1)
    expect((fixture.fetcher.mock.calls[0][1] as RequestInit).redirect).toBe('manual')
  })

  it('rejects wrong content type and a sequence gap', async () => {
    const wrongType = createFixture(async () => new Response('{}', {
      status: 200, headers: { 'content-type': 'application/json' }
    }))
    await wrongType.coordinator.start(workspaceId, 'cursor-0', parseCanonicalInt64('0'))
    expect(wrongType.bootstrap).toEqual(['bootstrap-required'])

    const frame = `event: aden_event\nid: cursor-2\ndata: ${JSON.stringify(event('2'))}\n\n`
    const gap = createFixture(async () => new Response(frame, {
      status: 200, headers: { 'content-type': 'text/event-stream; charset=utf-8' }
    }))
    await gap.coordinator.start(workspaceId, 'cursor-0', parseCanonicalInt64('0'))
    expect(gap.bootstrap).toEqual(['bootstrap-required'])
  })

  it('sends Authorization and Last-Event-ID but clears session on 401', async () => {
    const fixture = createFixture(async () => new Response(null, { status: 401 }))
    await fixture.coordinator.start(workspaceId, 'opaque-cursor', parseCanonicalInt64('0'))
    const headers = new Headers((fixture.fetcher.mock.calls[0][1] as RequestInit).headers)
    expect(headers.get('Authorization')).toBe('Bearer stream-token')
    expect(headers.get('Last-Event-ID')).toBe('opaque-cursor')
    expect(fixture.session.token()).toBeNull()
    expect(fixture.statuses).toContain('UNAUTHENTICATED')
  })
})

function createFixture(fetch: FetchLike) {
  const session = new SessionController()
  session.authenticate('stream-token')
  session.selectWorkspace(workspaceId)
  const statuses: string[] = []
  const bootstrap: string[] = []
  const fetcher = vi.fn(fetch)
  const coordinator = new AdenSseCoordinator(config, session, {
    sendBatch: () => undefined,
    sendStatus: (status) => statuses.push(status.state),
    requireBootstrap: (_context, reason) => bootstrap.push(reason)
  }, fetcher, () => 0)
  return { session, coordinator, statuses, bootstrap, fetcher }
}

function event(sequence: string) {
  return {
    schemaVersion: 1,
    eventId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    workspaceId,
    eventType: 'aden.task.state-changed.v1',
    aggregateType: 'TASK',
    aggregateId: '22222222-2222-4222-8222-222222222222',
    aggregateVersion: sequence,
    sequence,
    occurredAt: '2026-09-12T12:00:02Z',
    correlationId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    data: {}
  }
}
