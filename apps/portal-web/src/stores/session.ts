import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiRequest, clearRuoYiToken } from '@/shared/api/client'

export type SessionAccount = {
  id: string
  email?: string
  username: string
  displayName: string
  roles?: string[]
  permissions?: string[]
}

export const useSessionStore = defineStore('session', () => {
  const account = ref<SessionAccount | null>(null)
  const loaded = ref(false)
  const loading = ref(false)
  let loadingPromise: Promise<void> | null = null

  async function ensureLoaded() {
    if (loaded.value) return
    if (loadingPromise) return loadingPromise
    loadingPromise = (async () => {
      loading.value = true
      try {
        const result = await apiRequest<{ code: number; data: { user: { userId: number; userName: string; nickName?: string; email?: string }; roles?: string[]; permissions?: string[] }}>('/getInfo')
        const info = result.data ?? result
        account.value = { id: String(info.user.userId), username: info.user.userName, email: info.user.email ?? '', displayName: info.user.nickName || info.user.userName, roles: info.roles, permissions: info.permissions }
      } catch {
        account.value = null
      } finally {
        loaded.value = true
        loading.value = false
        loadingPromise = null
      }
    })()
    return loadingPromise
  }

  async function login(username: string, password: string, code: string, uuid: string) {
    const result = await apiRequest<{ code: number; token?: string }>('/login', { method: 'POST', body: JSON.stringify({ username, password, code, uuid }) })
    if (!result.token) throw new Error('RuoYi 登录响应缺少 Token')
    localStorage.setItem('ruoyi-token', result.token)
    loaded.value = false
    await ensureLoaded()
  }

  function clear() {
    clearRuoYiToken()
    account.value = null
    loaded.value = true
  }

  return { account, loaded, loading, ensureLoaded, login, clear }
})
