import type { BrowserWindow, Session } from 'electron'
import { isTrustedRendererUrl, type RendererTrust } from '../config/trusted-origins'
import { buildContentSecurityPolicy } from './content-security-policy'

export function installSessionSecurity(session: Session, trust: RendererTrust): void {
  session.setPermissionCheckHandler(() => false)
  session.setPermissionRequestHandler((_webContents, _permission, callback) => callback(false))
  session.webRequest.onHeadersReceived((details, callback) => {
    if (!isTrustedRendererUrl(details.url, trust)) return callback({ responseHeaders: details.responseHeaders })
    callback({
      responseHeaders: {
        ...details.responseHeaders,
        'Content-Security-Policy': [buildContentSecurityPolicy(trust)]
      }
    })
  })
}

export function installWebContentsSecurity(window: BrowserWindow, trust: RendererTrust): void {
  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }))
  window.webContents.on('will-navigate', (event, url) => {
    if (!isTrustedRendererUrl(url, trust)) event.preventDefault()
  })
  window.webContents.on('will-attach-webview', (event) => event.preventDefault())
}

export function securityPreferencesAreLocked(preferences: Record<string, unknown>): boolean {
  return preferences.contextIsolation === true
    && preferences.nodeIntegration === false
    && preferences.sandbox === true
    && preferences.webSecurity !== false
    && preferences.allowRunningInsecureContent !== true
}
