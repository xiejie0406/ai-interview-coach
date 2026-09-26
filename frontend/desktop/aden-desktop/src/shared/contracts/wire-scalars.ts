/**
 * Aden current 契约的 64-bit wire scalar。
 *
 * 公开 JSON 始终保留十进制字符串；这里不使用 JavaScript number，也不拥有 JSON Schema 真相。
 * Schema 漂移由 tests/aden-consumer-contract.test.ts 直接读取 contracts/aden 检查。
 */
declare const canonicalInt64Brand: unique symbol

export type CanonicalInt64String = string & {
  readonly [canonicalInt64Brand]: 'CanonicalInt64String'
}

const LONG_MAX_VALUE = '9223372036854775807'
const CANONICAL_DECIMAL = /^(?:0|[1-9][0-9]*)$/

export function parseCanonicalInt64(value: unknown): CanonicalInt64String {
  if (typeof value !== 'string' || !CANONICAL_DECIMAL.test(value)) {
    throw new TypeError('Aden 64-bit wire scalar 必须是 canonical 非负十进制 JSON string')
  }
  if (compareUnsignedDecimal(value, LONG_MAX_VALUE) > 0) {
    throw new RangeError('Aden 64-bit wire scalar 超出 Long.MAX_VALUE')
  }
  return value as CanonicalInt64String
}

export function compareCanonicalInt64(
  left: CanonicalInt64String,
  right: CanonicalInt64String
): -1 | 0 | 1 {
  return compareUnsignedDecimal(left, right)
}

export function nextCanonicalInt64(value: CanonicalInt64String): CanonicalInt64String {
  if (value === LONG_MAX_VALUE) throw new RangeError('Aden 64-bit wire scalar 已到 Long.MAX_VALUE')
  const digits = value.split('')
  for (let index = digits.length - 1; index >= 0; index -= 1) {
    if (digits[index] !== '9') {
      digits[index] = String(Number(digits[index]) + 1)
      return parseCanonicalInt64(digits.join(''))
    }
    digits[index] = '0'
  }
  return parseCanonicalInt64(`1${digits.join('')}`)
}

function compareUnsignedDecimal(left: string, right: string): -1 | 0 | 1 {
  if (left.length !== right.length) {
    return left.length < right.length ? -1 : 1
  }
  if (left === right) {
    return 0
  }
  return left < right ? -1 : 1
}
