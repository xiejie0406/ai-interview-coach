import { defineStore } from 'pinia'
import type { AuthenticatedUser, CaptchaChallenge, LoginInput, SessionContext } from '../../../shared/contracts/desktop-api'

const emptyContext = (): SessionContext => ({ authenticated: false, sessionEpoch: 0, workspaceEpoch: 0, workspaceId: null })

export const useSessionStore = defineStore('session', {
  state: () => ({
    phase: 'anonymous' as 'anonymous' | 'loading' | 'authenticated',
    user: null as Record<string, unknown> | null,
    roles: [] as string[],
    permissions: [] as string[],
    captcha: null as CaptchaChallenge | null,
    context: emptyContext(),
    error: null as string | null
  }),
  actions: {
    async restore(): Promise<boolean> {
      this.phase = 'loading'
      try {
        const response = await window.adenDesktop.auth.restore()
        this.applyAuthenticated(response.value)
        return true
      } catch {
        this.clear()
        return false
      }
    },
    async loadCaptcha(): Promise<void> {
      this.error = null
      try {
        this.captcha = (await window.adenDesktop.auth.captcha()).value
      } catch (error) {
        this.error = safeMessage(error)
      }
    },
    async login(input: LoginInput): Promise<void> {
      this.phase = 'loading'
      this.error = null
      try {
        const response = await window.adenDesktop.auth.login(input)
        this.applyAuthenticated(response.value)
      } catch (error) {
        this.clear()
        this.error = safeMessage(error)
        await this.loadCaptcha()
        throw error
      }
    },
    async logout(): Promise<void> {
      const remoteLogout = window.adenDesktop.auth.logout()
      // 先撤销 renderer 权限态，避免远端 logout 等待期间重新挂载受保护页面。
      this.clear()
      await remoteLogout.catch(() => undefined)
    },
    applyAuthenticated(result: AuthenticatedUser): void {
      this.user = { ...result.user }
      this.roles = [...result.roles]
      this.permissions = [...result.permissions]
      this.context = { ...result.session }
      this.phase = 'authenticated'
      this.error = null
    },
    updateContext(context: SessionContext): void {
      this.context = { ...context }
      if (!context.authenticated) this.clear()
    },
    clear(): void {
      this.phase = 'anonymous'
      this.user = null
      this.roles = []
      this.permissions = []
      this.context = emptyContext()
    }
  }
})

function safeMessage(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败'
}
