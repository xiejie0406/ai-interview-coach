declare module '@/utils/request' {
  function request<T = unknown>(config: {
    url: string
    method: 'get' | 'post' | 'put' | 'delete'
    params?: Record<string, unknown>
    data?: unknown
    headers?: Record<string, string>
    responseType?: 'blob' | 'json' | 'text'
  }): Promise<T>

  export default request
}
