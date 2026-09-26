import { randomUUID } from 'node:crypto'
import type { RuntimeConfig } from '../config/runtime-config'
import { SessionController, type SessionContext } from '../auth/session-controller'
import { nextCanonicalInt64, type CanonicalInt64String } from '../../shared/contracts/wire-scalars'
import { parseEventEnvelope, type EpochContext, type EventBatch, type EventEnvelope } from '../../shared/contracts/operator-events'
import type { FetchLike } from './api-client'
import { BoundedSseParser } from './sse-parser'
import { retryDelayMs } from './retry-policy'

export type StreamState = 'CONNECTING' | 'LIVE' | 'RECONNECTING' | 'DEGRADED' | 'STOPPED' | 'UNAUTHENTICATED'

export interface StreamStatus extends EpochContext {
  readonly state: StreamState
  readonly reason?: string
}

export interface EventBridgeLimits {
  readonly maxQueuedEvents: number
  readonly maxBatchEvents: number
  readonly maxBatchBytes: number
  readonly ackTimeoutMs: number
  readonly maxEventsPerSecond: number
}

const DEFAULT_BRIDGE_LIMITS: EventBridgeLimits = Object.freeze({
  maxQueuedEvents: 200,
  maxBatchEvents: 25,
  maxBatchBytes: 256 * 1024,
  ackTimeoutMs: 5_000,
  maxEventsPerSecond: 100
})

interface QueuedEvent {
  readonly cursor: string
  readonly event: EventEnvelope
}

export class BoundedEventBridge {
  readonly #queue: QueuedEvent[] = []
  #pending: EventBatch | null = null
  #ackTimer: ReturnType<typeof setTimeout> | null = null
  #windowStartedAt = Date.now()
  #windowEvents = 0

  constructor(
    readonly context: EpochContext,
    readonly send: (batch: EventBatch) => void,
    readonly fault: (reason: string) => void,
    readonly limits: EventBridgeLimits = DEFAULT_BRIDGE_LIMITS,
    readonly now: () => number = Date.now
  ) {}

  enqueue(cursor: string, event: EventEnvelope): void {
    if (!cursor || cursor.length > 768) return this.#fail('invalid-cursor')
    const now = this.now()
    if (now - this.#windowStartedAt >= 1_000) {
      this.#windowStartedAt = now
      this.#windowEvents = 0
    }
    this.#windowEvents += 1
    if (this.#windowEvents > this.limits.maxEventsPerSecond) return this.#fail('event-rate-limit')
    if (this.#queue.length + (this.#pending?.events.length ?? 0) >= this.limits.maxQueuedEvents) {
      return this.#fail('event-queue-limit')
    }
    this.#queue.push({ cursor, event })
    this.#flush()
  }

  ack(batchId: string, lastCursor: string, context: EpochContext): QueuedEvent | null {
    if (!sameContext(context, this.context) || !this.#pending
        || this.#pending.batchId !== batchId || this.#pending.lastCursor !== lastCursor) return null
    if (this.#ackTimer) clearTimeout(this.#ackTimer)
    this.#ackTimer = null
    const accepted = {
      cursor: this.#pending.lastCursor,
      event: this.#pending.events[this.#pending.events.length - 1]
    }
    this.#pending = null
    this.#flush()
    return accepted
  }

  clear(): void {
    if (this.#ackTimer) clearTimeout(this.#ackTimer)
    this.#ackTimer = null
    this.#pending = null
    this.#queue.length = 0
  }

  #flush(): void {
    if (this.#pending || this.#queue.length === 0) return
    const selected: QueuedEvent[] = []
    let bytes = 0
    while (this.#queue.length > 0 && selected.length < this.limits.maxBatchEvents) {
      const next = this.#queue[0]
      const nextBytes = Buffer.byteLength(JSON.stringify(next.event), 'utf8')
      if (selected.length > 0 && bytes + nextBytes > this.limits.maxBatchBytes) break
      if (nextBytes > this.limits.maxBatchBytes) return this.#fail('ipc-payload-limit')
      selected.push(this.#queue.shift() as QueuedEvent)
      bytes += nextBytes
    }
    const last = selected[selected.length - 1]
    this.#pending = Object.freeze({
      ...this.context,
      batchId: randomUUID(),
      events: Object.freeze(selected.map((item) => item.event)),
      lastCursor: last.cursor
    })
    this.send(this.#pending)
    this.#ackTimer = setTimeout(() => this.#fail('renderer-ack-timeout'), this.limits.ackTimeoutMs)
  }

  #fail(reason: string): void {
    this.clear()
    this.fault(reason)
  }
}

export interface SseCoordinatorCallbacks {
  readonly sendBatch: (batch: EventBatch) => void
  readonly sendStatus: (status: StreamStatus) => void
  readonly requireBootstrap: (context: EpochContext, reason: string) => void
}

export class AdenSseCoordinator {
  #run: Promise<void> | null = null
  #bridge: BoundedEventBridge | null = null
  #lastAckCursor: string | null = null
  #lastAckSequence: CanonicalInt64String | null = null
  #lastSequence: CanonicalInt64String | null = null
  #stopRequested = true
  #generation = 0
  #streamController: AbortController | null = null

  constructor(
    private readonly config: RuntimeConfig,
    private readonly session: SessionController,
    private readonly callbacks: SseCoordinatorCallbacks,
    private readonly fetchImpl: FetchLike = globalThis.fetch,
    private readonly random: () => number = Math.random
  ) {}

  start(workspaceId: string, cursor: string, watermark: CanonicalInt64String): Promise<void> {
    this.stop()
    const current = this.session.context()
    if (current.workspaceId !== workspaceId || !current.authenticated) {
      throw new TypeError('SSE 启动上下文与当前会话不一致')
    }
    const context = toEpochContext(current)
    this.#lastAckCursor = cursor
    this.#lastSequence = watermark
    this.#lastAckSequence = watermark
    this.#stopRequested = false
    const generation = this.#generation
    this.#bridge = new BoundedEventBridge(
      context,
      this.callbacks.sendBatch,
      (reason) => this.#degrade(context, reason)
    )
    this.#run = this.#runLoop(context, generation)
    return this.#run
  }

  stop(): void {
    this.#stopRequested = true
    this.#generation += 1
    this.#streamController?.abort('stream-stopped')
    this.#streamController = null
    this.#bridge?.clear()
    this.#bridge = null
    // SessionController 统一持有并中止在途 SSE/REST。
  }

  ack(batchId: string, cursor: string, context: EpochContext): boolean {
    const accepted = this.#bridge?.ack(batchId, cursor, context)
    if (!accepted) return false
    this.#lastAckCursor = accepted.cursor
    this.#lastAckSequence = accepted.event.sequence
    return true
  }

  async #runLoop(context: EpochContext, generation: number): Promise<void> {
    let attempt = 0
    while (!this.#stopRequested && generation === this.#generation && this.#isCurrent(context)) {
      this.#status(context, attempt === 0 ? 'CONNECTING' : 'RECONNECTING')
      const outcome = await this.#connectOnce(context, generation)
      if (outcome === 'stop' || this.#stopRequested || generation !== this.#generation || !this.#isCurrent(context)) break
      if (outcome === 'bootstrap') {
        this.#degrade(context, 'bootstrap-required')
        break
      }
      this.#bridge?.clear()
      this.#lastSequence = this.#lastAckSequence
      attempt += 1
      await wait(retryDelayMs(attempt, this.random))
    }
    if (this.#isCurrent(context) && this.#stopRequested) this.#status(context, 'STOPPED')
  }

  async #connectOnce(context: EpochContext, generation: number): Promise<'retry' | 'bootstrap' | 'stop'> {
    const token = this.session.token()
    if (!token) return 'stop'
    const controller = this.session.trackedAbortController()
    this.#streamController = controller
    try {
      const path = `/api/v1/aden/workspaces/${encodeURIComponent(context.workspaceId)}/events?filter=workspace-all-v1`
      const response = await this.fetchImpl(new URL(path.slice(1), `${this.config.apiBaseUrl}/`), {
        method: 'GET',
        redirect: 'manual',
        credentials: 'omit',
        cache: 'no-store',
        signal: controller.signal,
        headers: {
          Accept: 'text/event-stream',
          Authorization: `Bearer ${token}`,
          ...(this.#lastAckCursor ? { 'Last-Event-ID': this.#lastAckCursor } : {})
        }
      })
      if (response.status >= 300 && response.status < 400) return 'bootstrap'
      if (response.status === 400 || response.status === 410) return 'bootstrap'
      if (response.status === 401) {
        this.session.clear()
        this.#status(context, 'UNAUTHENTICATED', 'http-401')
        return 'stop'
      }
      if (!response.ok) return response.status >= 500 || response.status === 429 ? 'retry' : 'bootstrap'
      const type = response.headers.get('content-type')?.toLowerCase() ?? ''
      if (!type.includes('text/event-stream')) return 'bootstrap'
      if (!response.body) return 'retry'
      this.#status(context, 'LIVE')
      const parser = new BoundedSseParser()
      const reader = response.body.getReader()
      try {
        while (!this.#stopRequested && generation === this.#generation && this.#isCurrent(context)) {
          const { value, done } = await readWithIdleTimeout(reader, 45_000)
          if (done) break
          let frames
          try {
            frames = parser.feed(value)
          } catch {
            return 'bootstrap'
          }
          for (const frame of frames) {
            if (frame.event !== 'aden_event' || !frame.id || frame.id.length > 768) return 'bootstrap'
            let event: EventEnvelope
            try {
              event = parseEventEnvelope(JSON.parse(frame.data))
            } catch {
              return 'bootstrap'
            }
            if (event.workspaceId !== context.workspaceId || !this.#lastSequence
                || event.sequence !== nextCanonicalInt64(this.#lastSequence)) return 'bootstrap'
            this.#lastSequence = event.sequence
            this.#bridge?.enqueue(frame.id, event)
          }
        }
      } finally {
        parser.discardIncomplete()
        await reader.cancel().catch(() => undefined)
      }
      return this.#stopRequested ? 'stop' : 'retry'
    } catch {
      return controller.signal.aborted ? 'stop' : 'retry'
    } finally {
      if (this.#streamController === controller) this.#streamController = null
      this.session.release(controller)
    }
  }

  #degrade(context: EpochContext, reason: string): void {
    this.#stopRequested = true
    this.#streamController?.abort('stream-degraded')
    this.#bridge?.clear()
    this.#status(context, 'DEGRADED', reason)
    this.callbacks.requireBootstrap(context, reason)
  }

  #status(context: EpochContext, state: StreamState, reason?: string): void {
    this.callbacks.sendStatus(Object.freeze({ ...context, state, ...(reason ? { reason } : {}) }))
  }

  #isCurrent(context: EpochContext): boolean {
    const current = this.session.context()
    return current.authenticated
      && current.workspaceId === context.workspaceId
      && current.sessionEpoch === context.sessionEpoch
      && current.workspaceEpoch === context.workspaceEpoch
  }
}

function toEpochContext(context: SessionContext): EpochContext {
  if (!context.workspaceId) throw new TypeError('workspaceId 缺失')
  return { workspaceId: context.workspaceId, sessionEpoch: context.sessionEpoch, workspaceEpoch: context.workspaceEpoch }
}

function sameContext(left: EpochContext, right: EpochContext): boolean {
  return left.workspaceId === right.workspaceId
    && left.sessionEpoch === right.sessionEpoch
    && left.workspaceEpoch === right.workspaceEpoch
}

async function wait(milliseconds: number): Promise<void> {
  await new Promise<void>((resolve) => setTimeout(resolve, milliseconds))
}

async function readWithIdleTimeout(
  reader: ReadableStreamDefaultReader<Uint8Array>,
  timeoutMs: number
): Promise<ReadableStreamReadResult<Uint8Array>> {
  let timer: ReturnType<typeof setTimeout> | undefined
  try {
    return await Promise.race([
      reader.read(),
      new Promise<never>((_resolve, reject) => {
        timer = setTimeout(() => reject(new Error('SSE heartbeat timeout')), timeoutMs)
      })
    ])
  } finally {
    if (timer) clearTimeout(timer)
  }
}
