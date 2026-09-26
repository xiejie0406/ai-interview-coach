import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  compareCanonicalInt64,
  parseCanonicalInt64
} from '../src/shared/contracts/wire-scalars'
import {
  OPERATOR_TASK_COMMANDS,
  parseOperatorTaskCommand
} from '../src/shared/contracts/operator-task-command'

const contractRoot = fileURLToPath(new URL('../../../../contracts/aden/', import.meta.url))

describe('Aden TypeScript contract consumer', () => {
  it('keeps canonical int64 values exact across the JavaScript safe-integer boundary', () => {
    const commonSchema = readJson('schemas/current/common.schema.json')
    const int64Schema = objectField(objectField(commonSchema, '$defs'), 'CanonicalInt64String')
    const schemaPattern = new RegExp(stringField(int64Schema, 'pattern'))
    const accepted = [
      '0',
      '9007199254740991',
      '9007199254740992',
      '9223372036854775807'
    ]

    for (const wireValue of accepted) {
      expect(schemaPattern.test(wireValue)).toBe(true)
      const parsed = parseCanonicalInt64(wireValue)
      expect(parsed).toBe(wireValue)
      expect(JSON.parse(JSON.stringify({ value: parsed }))).toEqual({ value: wireValue })
    }

    const belowBoundary = parseCanonicalInt64('9007199254740991')
    const boundary = parseCanonicalInt64('9007199254740992')
    const max = parseCanonicalInt64('9223372036854775807')
    expect(compareCanonicalInt64(belowBoundary, boundary)).toBe(-1)
    expect(compareCanonicalInt64(boundary, max)).toBe(-1)
  })

  it.each([
    '-1',
    '+1',
    '01',
    '1.0',
    '1e3',
    '9223372036854775808'
  ])('rejects non-canonical or overflowing int64 string %s', (wireValue) => {
    expect(() => parseCanonicalInt64(wireValue)).toThrow()
  })

  it('rejects JSON numbers instead of coercing them after precision may be lost', () => {
    const invalidEvent = readJson('examples/invalid/event-int64-as-number.json')
    expect(typeof invalidEvent.aggregateVersion).toBe('number')
    expect(() => parseCanonicalInt64(invalidEvent.aggregateVersion)).toThrow(/JSON string/)
  })

  it('reads EventEnvelope.workspaceId and boundary fields from canonical examples', () => {
    const event = readJson('examples/current/operator/event-2pow53.json')
    expect(event.workspaceId).toBe('11111111-1111-4111-8111-111111111111')
    expect(parseCanonicalInt64(event.aggregateVersion)).toBe('9007199254740992')
    expect(parseCanonicalInt64(event.sequence)).toBe('9007199254740992')

    const bootstrap = readJson('examples/current/operator/bootstrap-long-max-watermark.json')
    expect(parseCanonicalInt64(bootstrap.streamWatermark)).toBe('9223372036854775807')
    expect(parseCanonicalInt64(objectField(bootstrap, 'workspace').version))
      .toBe('9007199254740991')
  })

  it('matches only the two public Operator commands from the canonical Schema', () => {
    const operatorSchema = readJson('schemas/current/operator.schema.json')
    const definitions = objectField(operatorSchema, '$defs')
    const commandRequest = objectField(definitions, 'OperatorTaskCommandRequest')
    const alternatives = arrayField(commandRequest, 'oneOf')
    const schemaCommands = alternatives.map((alternative) => {
      const properties = objectField(asObject(alternative), 'properties')
      return stringField(objectField(properties, 'command'), 'const')
    })

    expect([...OPERATOR_TASK_COMMANDS].sort()).toEqual([...schemaCommands].sort())
    expect(parseOperatorTaskCommand('SUBMIT_FOR_VALIDATION')).toBe('SUBMIT_FOR_VALIDATION')
    expect(parseOperatorTaskCommand('REQUEST_CANCEL')).toBe('REQUEST_CANCEL')

    const internal = readJson('examples/invalid/operator-internal-command.json')
    expect(() => parseOperatorTaskCommand(internal.command)).toThrow()
    for (const internalCommand of [
      'VALIDATION_PASSED',
      'VALIDATION_FAILED',
      'START',
      'COMPLETE',
      'FAIL',
      'WAIT_FOR_USER',
      'WAIT_FOR_EXTERNAL',
      'RESUME',
      'CONFIRM_CANCELED'
    ]) {
      expect(() => parseOperatorTaskCommand(internalCommand)).toThrow()
    }
  })
})

function readJson(relativePath: string): Record<string, unknown> {
  return asObject(JSON.parse(readFileSync(join(contractRoot, relativePath), 'utf8')))
}

function asObject(value: unknown): Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw new TypeError('预期 JSON object')
  }
  return value as Record<string, unknown>
}

function objectField(
  value: Record<string, unknown>,
  field: string
): Record<string, unknown> {
  return asObject(value[field])
}

function stringField(value: Record<string, unknown>, field: string): string {
  const fieldValue = value[field]
  if (typeof fieldValue !== 'string') {
    throw new TypeError(`字段 ${field} 必须是 string`)
  }
  return fieldValue
}

function arrayField(value: Record<string, unknown>, field: string): unknown[] {
  const fieldValue = value[field]
  if (!Array.isArray(fieldValue)) {
    throw new TypeError(`字段 ${field} 必须是 array`)
  }
  return fieldValue
}
