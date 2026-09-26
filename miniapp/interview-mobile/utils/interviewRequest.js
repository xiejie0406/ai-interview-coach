const API_PREFIX = '/api/v1'
const RUOYI_TOKEN_KEY = 'App-Token'
const RUOYI_PATHS = ['/login', '/captchaImage', '/getInfo', '/getRouters', '/logout']

function correlationId() {
  return globalThis.crypto?.randomUUID?.() || `mobile-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function interviewRequest({ url, method = 'GET', data, header = {} }) {
  if (!url.startsWith('/') || url.startsWith('//') || url.includes('://')) {
    return Promise.reject(new Error('业务 API 必须使用 /api/v1 下的相对路径'))
  }
  const normalizedMethod = method.toUpperCase()
  const token = uni.getStorageSync(RUOYI_TOKEN_KEY)
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${RUOYI_PATHS.some(item => url.startsWith(item)) ? '/api' : API_PREFIX}${url}`,
      method: normalizedMethod,
      data,
      header: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-Correlation-ID': correlationId(),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...header
      },
      withCredentials: false,
      success(response) {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data)
          return
        }
        const message = response.data?.msg || response.data?.error?.userMessage || `请求失败（${response.statusCode}）`
        reject(Object.assign(new Error(message), {
          status: response.statusCode,
          code: response.data?.errorCode || response.data?.error?.code || 'UNKNOWN_ERROR'
        }))
      },
      fail(error) {
        reject(new Error(error.errMsg || '网络连接失败'))
      }
    })
  })
}

export function interviewRequestWithMeta(options) {
  return new Promise((resolve, reject) => {
    const normalizedMethod = (options.method || 'GET').toUpperCase()
    const token = uni.getStorageSync(RUOYI_TOKEN_KEY)
    uni.request({
      url: `${RUOYI_PATHS.some(item => options.url.startsWith(item)) ? '/api' : API_PREFIX}${options.url}`,
      method: normalizedMethod,
      data: options.data,
      withCredentials: false,
      header: {
        Accept: 'application/json', 'Content-Type': 'application/json', 'X-Correlation-ID': correlationId(),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.header || {})
      },
      success(response) {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          const headers = response.header || {}
          resolve({ data: response.data, etag: headers.ETag || headers.Etag || headers.etag || '' })
          return
        }
        reject(Object.assign(new Error(response.data?.msg || response.data?.error?.userMessage || `请求失败（${response.statusCode}）`), { status: response.statusCode, code: response.data?.errorCode || response.data?.error?.code }))
      },
      fail(error) { reject(new Error(error.errMsg || '网络连接失败')) }
    })
  })
}

export function operationKey() {
  return globalThis.crypto?.randomUUID?.() || `mobile-op-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
