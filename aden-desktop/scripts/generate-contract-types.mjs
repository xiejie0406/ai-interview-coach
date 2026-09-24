import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'

const banner = '// GENERATED FROM contracts/aden — DO NOT EDIT'
const schemaRoot = resolve('../contracts/aden/schemas/current')
const outputPath = resolve('src/shared/generated/operator-contracts.ts')
const schemas = await Promise.all(['common.schema.json', 'operator.schema.json'].map(async (name) =>
  JSON.parse(await readFile(resolve(schemaRoot, name), 'utf8'))
))

const lines = [
  banner,
  "import type { CanonicalInt64String as BrandedCanonicalInt64String } from '../contracts/wire-scalars'",
  '',
  '/** 此文件由 current JSON Schema 确定性生成；运行时仍必须经过边界 validator。 */'
]

for (const schema of schemas) {
  for (const [name, definition] of Object.entries(schema.$defs ?? {})) {
    const rendered = name === 'CanonicalInt64String'
      ? 'BrandedCanonicalInt64String'
      : render(definition)
    lines.push(`export type ${name} = ${rendered}`, '')
  }
}
const output = `${lines.join('\n').trim()}\n`

if (process.argv.includes('--check')) {
  let existing = ''
  try { existing = await readFile(outputPath, 'utf8') } catch { /* 缺失即漂移 */ }
  if (existing !== output) {
    throw new Error('生成类型已漂移；请运行 npm run contracts:generate 并提交结果')
  }
} else {
  await mkdir(dirname(outputPath), { recursive: true })
  await writeFile(outputPath, output, 'utf8')
  process.stdout.write(`已生成 ${outputPath}\n`)
}

function render(node) {
  if (node.$ref) return node.$ref.slice(node.$ref.lastIndexOf('/') + 1)
  if (Object.hasOwn(node, 'const')) return JSON.stringify(node.const)
  if (Array.isArray(node.enum)) return node.enum.map((item) => JSON.stringify(item)).join(' | ')
  if (Array.isArray(node.oneOf)) return node.oneOf.map(render).join(' | ')
  if (Array.isArray(node.anyOf)) return node.anyOf.map(render).join(' | ')
  if (Array.isArray(node.allOf) && node.type === undefined && !node.properties) {
    return node.allOf.map((item) => `(${render(item)})`).join(' & ')
  }
  if (Array.isArray(node.type)) return node.type.map((type) => render({ ...node, type })).join(' | ')
  if (node.type === 'array') {
    const item = render(node.items ?? {})
    return `readonly (${item})[]`
  }
  if (node.type === 'object' || node.properties) {
    const required = new Set(node.required ?? [])
    const fields = Object.entries(node.properties ?? {}).map(([name, value]) =>
      `readonly ${safeName(name)}${required.has(name) ? '' : '?'}: ${render(value)}`
    )
    if (node.additionalProperties && typeof node.additionalProperties === 'object') {
      fields.push(`readonly [key: string]: ${render(node.additionalProperties)}`)
    }
    return fields.length ? `{ ${fields.join('; ')} }` : 'Readonly<Record<string, unknown>>'
  }
  if (node.type === 'string') return 'string'
  if (node.type === 'integer' || node.type === 'number') return 'number'
  if (node.type === 'boolean') return 'boolean'
  if (node.type === 'null') return 'null'
  return 'unknown'
}

function safeName(value) {
  return /^[A-Za-z_$][A-Za-z0-9_$]*$/.test(value) ? value : JSON.stringify(value)
}
