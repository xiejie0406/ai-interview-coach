import { describe, expect, it } from 'vitest'
import { join } from 'node:path'
import { pathToFileURL } from 'node:url'
import { createRendererTrust, isTrustedRendererUrl } from '../../src/main/config/trusted-origins'
import { buildContentSecurityPolicy } from '../../src/main/security/content-security-policy'
import { securityPreferencesAreLocked } from '../../src/main/security/web-contents-policy'
import { assertPlainPayload, assertTrustedSender } from '../../src/main/ipc/validate-sender'

const entry = join(process.cwd(), 'out/renderer/index.html')

describe('desktop security boundaries', () => {
  it('trusts exact development origin and exact packaged file entry', () => {
    const dev = createRendererTrust(true, 'http://127.0.0.1:5173', entry)
    expect(isTrustedRendererUrl('http://127.0.0.1:5173/', dev)).toBe(true)
    expect(isTrustedRendererUrl('http://localhost:5173/', dev)).toBe(false)
    expect(() => createRendererTrust(true, 'http://127.0.0.1:5173/path', entry)).toThrow()

    const packaged = createRendererTrust(false, undefined, entry)
    const url = pathToFileURL(entry).href
    expect(isTrustedRendererUrl(url, packaged)).toBe(true)
    expect(isTrustedRendererUrl(`${url}?x=1`, packaged)).toBe(false)
    expect(isTrustedRendererUrl(`${url}#x`, packaged)).toBe(false)
    expect(isTrustedRendererUrl(pathToFileURL(join(process.cwd(), 'other.html')).href, packaged)).toBe(false)
  })

  it('requires main frame and trusted sender URL', () => {
    const trust = createRendererTrust(true, 'http://127.0.0.1:5173', entry)
    const mainFrame = { url: 'http://127.0.0.1:5173/' }
    const event = { senderFrame: mainFrame, sender: { mainFrame } }
    expect(() => assertTrustedSender(event as never, trust)).not.toThrow()
    expect(() => assertTrustedSender({ senderFrame: { url: mainFrame.url }, sender: { mainFrame } } as never, trust)).toThrow(/主 frame/)
    expect(() => assertTrustedSender({ senderFrame: { url: 'https://evil.invalid' }, sender: { mainFrame: { url: 'https://evil.invalid' } } } as never, trust)).toThrow()
  })

  it('rejects Node objects, functions and oversized IPC payloads', () => {
    expect(() => assertPlainPayload({ workspaceId: 'synthetic' })).not.toThrow()
    expect(() => assertPlainPayload(new Date())).toThrow(/plain JSON/)
    expect(() => assertPlainPayload({ callback: () => undefined })).toThrow(/plain JSON/)
    expect(() => assertPlainPayload({ data: 'x'.repeat(100) }, 20)).toThrow(/大小上限/)
  })

  it('locks window preferences and renderer networking', () => {
    expect(securityPreferencesAreLocked({ contextIsolation: true, nodeIntegration: false, sandbox: true, webSecurity: true })).toBe(true)
    expect(securityPreferencesAreLocked({ contextIsolation: true, nodeIntegration: true, sandbox: true })).toBe(false)
    const csp = buildContentSecurityPolicy(createRendererTrust(false, undefined, entry))
    expect(csp).toContain("connect-src 'none'")
    expect(csp).toContain("object-src 'none'")
  })
})
