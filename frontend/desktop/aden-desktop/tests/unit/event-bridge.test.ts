import { afterEach, describe, expect, it, vi } from 'vitest'
import { BoundedEventBridge } from '../../src/main/transport/sse-client'
import { parseEventEnvelope } from '../../src/shared/contracts/operator-events'

const context = { workspaceId: '11111111-1111-4111-8111-111111111111', sessionEpoch: 2, workspaceEpoch: 3 }

describe('BoundedEventBridge', () => {
  afterEach(() => vi.useRealTimers())

  it('advances only after matching batch, cursor and epoch ack', () => {
    const batches: unknown[] = []
    const faults: string[] = []
    const bridge = new BoundedEventBridge(context, (batch) => batches.push(batch), (reason) => faults.push(reason))
    bridge.enqueue('cursor-1', event('1'))
    const batch = batches[0] as { batchId: string; lastCursor: string }
    expect(bridge.ack(batch.batchId, batch.lastCursor, { ...context, workspaceEpoch: 4 })).toBeNull()
    expect(bridge.ack(batch.batchId, batch.lastCursor, context)?.cursor).toBe('cursor-1')
    expect(faults).toEqual([])
    bridge.clear()
  })

  it('fails closed when renderer does not ack', () => {
    vi.useFakeTimers()
    const faults: string[] = []
    const bridge = new BoundedEventBridge(context, () => undefined, (reason) => faults.push(reason), {
      maxQueuedEvents: 2, maxBatchEvents: 1, maxBatchBytes: 10_000, ackTimeoutMs: 10, maxEventsPerSecond: 10
    })
    bridge.enqueue('cursor-1', event('1'))
    vi.advanceTimersByTime(11)
    expect(faults).toEqual(['renderer-ack-timeout'])
  })

  it('fails closed on bounded queue pressure', () => {
    const faults: string[] = []
    const bridge = new BoundedEventBridge(context, () => undefined, (reason) => faults.push(reason), {
      maxQueuedEvents: 1, maxBatchEvents: 1, maxBatchBytes: 10_000, ackTimeoutMs: 10_000, maxEventsPerSecond: 10
    })
    bridge.enqueue('cursor-1', event('1'))
    bridge.enqueue('cursor-2', event('2'))
    expect(faults).toEqual(['event-queue-limit'])
  })
})

function event(sequence: string) {
  return parseEventEnvelope({
    schemaVersion: 1,
    eventId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    workspaceId: context.workspaceId,
    eventType: 'aden.task.state-changed.v1',
    aggregateType: 'TASK',
    aggregateId: '22222222-2222-4222-8222-222222222222',
    aggregateVersion: sequence,
    sequence,
    occurredAt: '2026-09-12T12:00:02Z',
    correlationId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    data: {}
  })
}
