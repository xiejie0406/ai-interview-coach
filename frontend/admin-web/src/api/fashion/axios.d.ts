import 'axios'

declare module 'axios' {
  interface AxiosRequestConfig<D = any, P = any> {
    /** 仅供若依响应拦截器使用，不会序列化为 HTTP 请求头。 */
    preserveBusinessError?: boolean
  }

  interface InternalAxiosRequestConfig<D = any, P = any> {
    preserveBusinessError?: boolean
  }
}
