import { parseCanonicalInt64, type CanonicalInt64String } from './wire-scalars'
import type { EventEnvelope as GeneratedEventEnvelope } from '../generated/operator-contracts'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
const EVENT_TYPES = new Set([
  'aden.task.created.v1',
  'aden.task.state-changed.v1',
  'aden.task.progressed.v1',
  'aden.runner.status-changed.v1',
  'aden.capability.changed.v1',
  'aden.audit.recorded.v1'
])
const AGGREGATE_TYPES = new Set(['TASK', 'RUNNER', 'CAPABILITY', 'AUDIT'])
const PROJECTION_STATES = new Set([
  'DRAFT', 'VALIDATING', 'QUEUED', 'RUNNING', 'WAITING_USER', 'WAITING_EXTERNAL',
  'CANCEL_REQUESTED', 'SUCCEEDED', 'FAILED', 'CANCELED', 'ONLINE', 'OFFLINE',
  'LEASED', 'QUARANTINED', 'AVAILABLE', 'GATED', 'DISABLED', 'EXPERIMENTAL'
])
const DATA_FIELDS = new Set(['from', 'to', 'reasonCode', 'progressPercent', 'runnerId', 'capabilityCode', 'externalActionsEnabled'])
const CAPABILITIES = new Set(['CORE', 'WX', 'PUR', 'COL'])
const EVENT_FIELDS = new Set([
  'schemaVersion', 'eventId', 'workspaceId', 'eventType', 'aggregateType', 'aggregateId',
  'aggregateVersion', 'sequence', 'occurredAt', 'correlationId', 'data'
])

export type EventEnvelope = GeneratedEventEnvelope

export interface EpochContext {
  readonly workspaceId: string
  readonly sessionEpoch: number
  readonly workspaceEpoch: number
}

export interface EventBatch extends EpochContext {
  readonly batchId: string
  readonly events: readonly EventEnvelope[]
  readonly lastCursor: string
}

/** current operator.schema.json 的显式边界映射；未知字段和枚举 fail closed。 */
export function parseEventEnvelope(value: unknown): EventEnvelope {
  if (!isRecord(value) || Object.keys(value).some((key) => !EVENT_FIELDS.has(key))
      || Object.keys(value).length !== EVENT_FIELDS.size) {
    throw new TypeError('EventEnvelope 字段集合不符合 current Schema')
  }
  if (value.schemaVersion !== 1) throw new TypeError('未知 EventEnvelope schemaVersion')
  for (const field of ['eventId', 'workspaceId', 'aggregateId'] as const) {
    if (typeof value[field] !== 'string' || !UUID.test(value[field])) throw new TypeError(`${field} 必须是 UUID`)
  }
  if (typeof value.eventType !== 'string' || !EVENT_TYPES.has(value.eventType)) throw new TypeError('未知 eventType')
  if (typeof value.aggregateType !== 'string' || !AGGREGATE_TYPES.has(value.aggregateType)) throw new TypeError('未知 aggregateType')
  if (typeof value.occurredAt !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(value.occurredAt)) {
    throw new TypeError('occurredAt 必须是 UTC date-time')
  }
  if (typeof value.correlationId !== 'string' || value.correlationId.length < 8 || value.correlationId.length > 128) {
    throw new TypeError('correlationId 非法')
  }
  if (!isRecord(value.data) || Object.keys(value.data).some((key) => !DATA_FIELDS.has(key))) {
    throw new TypeError('EventEnvelope.data 不符合 PublicEventData')
  }
  for (const field of ['from', 'to'] as const) {
    if (value.data[field] !== undefined && (typeof value.data[field] !== 'string' || !PROJECTION_STATES.has(value.data[field]))) {
      throw new TypeError(`EventEnvelope.data.${field} 非法`)
    }
  }
  if (value.data.reasonCode !== undefined
      && (typeof value.data.reasonCode !== 'string' || !/^[A-Z][A-Z0-9_]{2,63}$/.test(value.data.reasonCode))) {
    throw new TypeError('EventEnvelope.data.reasonCode 非法')
  }
  if (value.data.progressPercent !== undefined
      && (!Number.isInteger(value.data.progressPercent) || (value.data.progressPercent as number) < 0
        || (value.data.progressPercent as number) > 100)) throw new TypeError('EventEnvelope.data.progressPercent 非法')
  if (value.data.runnerId !== undefined && (typeof value.data.runnerId !== 'string' || !UUID.test(value.data.runnerId))) {
    throw new TypeError('EventEnvelope.data.runnerId 非法')
  }
  if (value.data.capabilityCode !== undefined
      && (typeof value.data.capabilityCode !== 'string' || !CAPABILITIES.has(value.data.capabilityCode))) {
    throw new TypeError('EventEnvelope.data.capabilityCode 非法')
  }
  if (value.data.externalActionsEnabled !== undefined && value.data.externalActionsEnabled !== false) {
    throw new TypeError('externalActionsEnabled 当前只能为 false')
  }
  return Object.freeze({
    schemaVersion: 1,
    eventId: value.eventId as string,
    workspaceId: value.workspaceId as string,
    eventType: value.eventType as string,
    aggregateType: value.aggregateType as string,
    aggregateId: value.aggregateId as string,
    aggregateVersion: parseCanonicalInt64(value.aggregateVersion),
    sequence: parseCanonicalInt64(value.sequence),
    occurredAt: value.occurredAt as string,
    correlationId: value.correlationId as string,
    data: Object.freeze({ ...value.data })
  }) as EventEnvelope
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}
