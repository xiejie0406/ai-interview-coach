type BusinessResponse = {
  config?: { preserveBusinessError?: boolean }
  data?: { code?: unknown }
  status?: number
}

export type PreservedBusinessError = Error & {
  response: BusinessResponse
  status: number
}

/**
 * 只在调用方显式选择时保留若依 HTTP 200 业务错误的原始响应结构。
 * 未选择的既有接口仍收到原来的 fallback，不改变全局兼容行为。
 */
export function rejectBusinessError(
  response: BusinessResponse,
  message: string,
  fallback: unknown
): Promise<never> {
  if (response.config?.preserveBusinessError !== true) {
    return Promise.reject(fallback)
  }

  const error = new Error(message) as PreservedBusinessError
  error.response = response
  error.status = typeof response.data?.code === 'number'
    ? response.data.code
    : response.status ?? 0
  return Promise.reject(error)
}
