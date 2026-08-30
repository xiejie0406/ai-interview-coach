import axios from 'axios'
import { getToken } from '@/utils/auth'

const interviewRequest = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  withCredentials: false
})

function normalizeErrorPayload(payload) {
  let current = payload
  // RuoYi AjaxResult 可能在 data 中包一层业务 ErrorEnvelope；限制深度避免异常对象循环。
  for (let depth = 0; depth < 3; depth += 1) {
    if (!current || typeof current !== 'object' || !Object.prototype.hasOwnProperty.call(current, 'data')) break
    const statusCode = Number(current.code)
    if (current.error || !current.data || typeof current.data !== 'object'
      || (Number.isFinite(statusCode) && statusCode >= 400)) break
    current = current.data
  }
  return current && typeof current === 'object' ? current : {}
}

interviewRequest.interceptors.request.use(config => {
  config.headers.Accept = 'application/json'
  config.headers['X-Correlation-ID'] = globalThis.crypto?.randomUUID?.() ?? `admin-${Date.now()}`
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

interviewRequest.interceptors.response.use(
  response => response.data,
  error => {
    const payload = normalizeErrorPayload(error.response?.data)
    const businessError = payload.error && typeof payload.error === 'object' ? payload.error : {}
    const message = businessError.userMessage ?? payload.msg ?? error.message ?? 'AI 面试业务接口请求失败'
    return Promise.reject(Object.assign(new Error(message), {
      status: error.response?.status ?? 0,
      code: businessError.code ?? payload.errorCode ?? payload.code ?? 'UNKNOWN_ERROR',
      correlationId: businessError.correlationId
        ?? payload.correlationId
        ?? error.response?.headers?.['x-correlation-id'],
      retryable: businessError.retryable ?? payload.retryable ?? false
    }))
  }
)

export default interviewRequest
