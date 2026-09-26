import request from '@/utils/interviewRequest'

function normalizeParams(params) {
  return Object.fromEntries(
    Object.entries(params ?? {}).filter(([, value]) => value !== '' && value !== null && value !== undefined)
  )
}

function encodePath(value) {
  return encodeURIComponent(String(value))
}

export function newCatalogIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `admin-catalog-${Date.now()}-${Math.random().toString(36).slice(2, 14)}`
}

function ensureIdempotencyKey(value) {
  const key = String(value ?? '').trim()
  if (!key) return newCatalogIdempotencyKey()
  if (key.length < 16 || key.length > 128) {
    throw new Error('Idempotency-Key 长度必须在 16 到 128 个字符之间')
  }
  return key
}

function normalizeEtag(value) {
  if (value === null || value === undefined || value === '') return undefined
  const etag = String(value).trim()
  if (/^"v\d+"$/.test(etag)) return etag
  if (/^v\d+$/.test(etag)) return `"${etag}"`
  if (/^\d+$/.test(etag)) return `"v${etag}"`
  return etag
}

function writeHeaders({ idempotencyKey, etag, requireMatch = false } = {}) {
  const headers = {}
  headers['Idempotency-Key'] = ensureIdempotencyKey(idempotencyKey)
  const normalizedEtag = normalizeEtag(etag)
  if (requireMatch && !normalizedEtag) throw new Error('If-Match 是题库版本写操作的必填前置条件')
  if (normalizedEtag) headers['If-Match'] = normalizedEtag
  return headers
}

function mutationOptions(optionsOrEtag, maybeIdempotencyKey) {
  if (optionsOrEtag && typeof optionsOrEtag === 'object') return optionsOrEtag
  return { etag: optionsOrEtag, idempotencyKey: maybeIdempotencyKey }
}

function idempotencyOptions(value) {
  if (value && typeof value === 'object') return value
  return { idempotencyKey: value }
}

function dataWithMeta(config) {
  const configuredTransforms = request.defaults?.transformResponse
  const transforms = Array.isArray(configuredTransforms)
    ? configuredTransforms.filter(Boolean)
    : configuredTransforms ? [configuredTransforms] : []
  return request({
    ...config,
    transformResponse: [
      ...transforms,
      (data, headers, status) => {
        const parsed = typeof data === 'string' ? (() => {
          try { return data ? JSON.parse(data) : undefined } catch { return data }
        })() : data
        // 错误体保持原结构，让共享拦截器继续提取 error.code/userMessage。
        if (Number(status) >= 400) return parsed
        return {
          data: parsed,
          etag: headers?.get?.('etag') ?? headers?.etag,
          correlationId: headers?.get?.('x-correlation-id') ?? headers?.['x-correlation-id']
        }
      }
    ]
  })
}

export function listPublishedInterviewQuestions(params) {
  return request({ url: '/questions', method: 'get', params: normalizeParams(params) })
}

export function getPublishedInterviewQuestion(questionId) {
  return request({ url: `/questions/${encodePath(questionId)}`, method: 'get' })
}

export function listAdminInterviewQuestions(params) {
  return request({ url: '/admin/questions', method: 'get', params: normalizeParams(params) })
}

export function getAdminInterviewQuestion(questionId) {
  return dataWithMeta({ url: `/admin/questions/${encodePath(questionId)}`, method: 'get' })
}

export function createInterviewQuestionDraft(data, idempotencyKeyOrOptions) {
  const options = idempotencyOptions(idempotencyKeyOrOptions)
  return dataWithMeta({
    url: '/admin/questions',
    method: 'post',
    data,
    headers: writeHeaders({ idempotencyKey: options.idempotencyKey })
  })
}

export function createInterviewQuestionVersion(questionId, data, optionsOrEtag = {}, maybeIdempotencyKey) {
  const { etag, idempotencyKey } = mutationOptions(optionsOrEtag, maybeIdempotencyKey)
  return dataWithMeta({
    url: `/admin/questions/${encodePath(questionId)}/versions`,
    method: 'post',
    data,
    headers: writeHeaders({ etag, idempotencyKey, requireMatch: true })
  })
}

export function createInterviewRubricVersion(questionId, data, optionsOrEtag = {}, maybeIdempotencyKey) {
  const options = mutationOptions(optionsOrEtag, maybeIdempotencyKey)
  return dataWithMeta({
    url: `/admin/questions/${encodePath(questionId)}/rubrics`,
    method: 'post',
    data,
    headers: writeHeaders({ etag: options.etag, idempotencyKey: options.idempotencyKey, requireMatch: true })
  })
}

export function applyInterviewQuestionCommand(questionId, command, data, optionsOrEtag = {}, maybeIdempotencyKey) {
  const { etag, idempotencyKey } = mutationOptions(optionsOrEtag, maybeIdempotencyKey)
  const allowedCommands = ['submit-review', 'reject-review', 'publish', 'retire']
  if (!allowedCommands.includes(command)) {
    return Promise.reject(new Error(`不支持的题库工作流命令：${command}`))
  }
  return dataWithMeta({
    url: `/admin/questions/${encodePath(questionId)}/commands/${command}`,
    method: 'post',
    data,
    headers: writeHeaders({ etag, idempotencyKey, requireMatch: true })
  })
}

// 保留已有调用方的公开题库 API 名称；管理工作台必须使用 /admin/questions。
export function listInterviewQuestions(params) {
  return listPublishedInterviewQuestions(params)
}

export function getInterviewQuestion(questionId) {
  return getPublishedInterviewQuestion(questionId)
}

// 与 contracts/openapi/catalog.yaml 的 operationId 对齐，便于其他管理页面复用。
export const listAdminQuestions = listAdminInterviewQuestions
export const getAdminQuestion = getAdminInterviewQuestion
export const createQuestionDraft = createInterviewQuestionDraft
export const createQuestionVersion = createInterviewQuestionVersion
export const createRubricVersion = createInterviewRubricVersion
export const applyQuestionWorkflowCommand = applyInterviewQuestionCommand
