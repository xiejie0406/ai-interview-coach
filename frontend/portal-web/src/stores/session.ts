import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiRequest, clearRuoYiToken, getRuoYiToken, setRuoYiToken } from '@/shared/api/client'

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
  const routers = ref<unknown[]>([])
  let loadingPromise: Promise<void> | null = null

  async function ensureLoaded() {
    if (loaded.value) return
    if (!getRuoYiToken()) {
      account.value = null
      loaded.value = true
      return
    }
    if (loadingPromise) return loadingPromise
    loadingPromise = (async () => {
      loading.value = true
      try {
        const [result, routerResult] = await Promise.all([
          apiRequest<{ user?: { userId: number; userName: string; nickName?: string; email?: string }; roles?: string[]; permissions?: string[]; data?: { user: { userId: number; userName: string; nickName?: string; email?: string }; roles?: string[]; permissions?: string[] } }>('/getInfo'),
          apiRequest<{ data?: unknown[] }>('/getRouters'),
        ])
        const info = result.data ?? result
        if (!info.user) throw new Error('RuoYi 用户信息响应无效')
        account.value = { id: String(info.user.userId), username: info.user.userName, email: info.user.email ?? '', displayName: info.user.nickName || info.user.userName, roles: info.roles, permissions: info.permissions }
        routers.value = routerResult.data ?? []
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
    setRuoYiToken(result.token)
    loaded.value = false
    await ensureLoaded()
    if (!account.value) {
      clearRuoYiToken()
      throw new Error('登录状态校验失败，请稍后重试')
    }
  }

  function clear() {
    clearRuoYiToken()
    account.value = null
    routers.value = []
    loaded.value = true
  }

  async function logout() {
    try {
      if (getRuoYiToken()) await apiRequest('/logout', { method: 'POST' })
    } finally {
      clear()
    }
  }

  return { account, loaded, loading, routers, ensureLoaded, login, logout, clear }
})
