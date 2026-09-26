export interface RuntimeConfig {
  readonly apiBaseUrl: string
  readonly apiOrigin: string
  readonly development: boolean
}

const LOOPBACK_HOSTS = new Set(['127.0.0.1', 'localhost', '[::1]'])

export function resolveRuntimeConfig(
  rawApiBaseUrl: string | undefined,
  development: boolean,
  localTestPackage = false
): RuntimeConfig {
  const configured = rawApiBaseUrl?.trim()
  if (!configured && !development) {
    throw new Error('打包环境必须显式配置 ADEN_API_BASE_URL')
  }

  let parsed: URL
  try {
    parsed = new URL(configured || 'http://127.0.0.1:8081')
  } catch {
    throw new Error('ADEN_API_BASE_URL 不是合法绝对 URL')
  }

  if (parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error('ADEN_API_BASE_URL 只能包含协议、主机、端口和路径')
  }
  if (localTestPackage && !LOOPBACK_HOSTS.has(parsed.hostname)) {
    throw new Error('本地测试安装版 API 只允许精确 loopback 地址')
  }

  const localDevelopment = (development || localTestPackage)
    && parsed.protocol === 'http:'
    && LOOPBACK_HOSTS.has(parsed.hostname)
  if (parsed.protocol !== 'https:' && !localDevelopment) {
    throw new Error('生产环境 Aden API 必须使用 HTTPS；开发/测试环境只允许精确 loopback HTTP')
  }

  parsed.pathname = parsed.pathname.replace(/\/+$/, '') || '/'
  const apiBaseUrl = parsed.toString().replace(/\/$/, '')
  return Object.freeze({
    apiBaseUrl,
    apiOrigin: parsed.origin,
    development
  })
}
