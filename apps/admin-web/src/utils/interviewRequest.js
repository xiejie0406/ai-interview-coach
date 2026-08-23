import axios from 'axios'
import { getToken } from '@/utils/auth'

const interviewRequest = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  withCredentials: false
})

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
    const payload = error.response?.data
    const message = payload?.msg ?? payload?.error?.userMessage ?? error.message ?? 'AI 面试业务接口请求失败'
    return Promise.reject(Object.assign(new Error(message), {
      status: error.response?.status ?? 0,
      code: payload?.errorCode ?? payload?.error?.code ?? 'UNKNOWN_ERROR',
      correlationId: payload?.error?.correlationId
    }))
  }
)

export default interviewRequest
