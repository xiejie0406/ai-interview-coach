import { resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

export interface RendererTrust {
  readonly development: boolean
  readonly devOrigin?: string
  readonly packagedEntryUrl: string
}

export function createRendererTrust(
  development: boolean,
  devServerUrl: string | undefined,
  packagedEntryPath: string
): RendererTrust {
  const packagedEntryUrl = pathToFileURL(resolve(packagedEntryPath)).href
  if (!development) {
    return Object.freeze({ development, packagedEntryUrl })
  }
  if (!devServerUrl) {
    return Object.freeze({ development, packagedEntryUrl })
  }
  const candidate = new URL(devServerUrl)
  if (!['http:', 'https:'].includes(candidate.protocol)
      || candidate.username || candidate.password || candidate.search || candidate.hash
      || candidate.pathname !== '/') {
    throw new Error('ELECTRON_RENDERER_URL 必须是无凭据、query、fragment 和路径的 Origin')
  }
  return Object.freeze({ development, devOrigin: candidate.origin, packagedEntryUrl })
}

export function isTrustedRendererUrl(value: string, trust: RendererTrust): boolean {
  try {
    const candidate = new URL(value)
    if (trust.development && trust.devOrigin) {
      return candidate.origin === trust.devOrigin
    }
    if (candidate.protocol !== 'file:' || candidate.search || candidate.hash) {
      return false
    }
    return fileURLToPath(candidate) === fileURLToPath(trust.packagedEntryUrl)
  } catch {
    return false
  }
}
