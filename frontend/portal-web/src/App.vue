<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useSessionStore } from '@/stores/session'
import { useRoute, useRouter } from 'vue-router'

const session = useSessionStore()
const route = useRoute()
const router = useRouter()
const handleUnauthorized = () => {
  session.clear()
  if (router.currentRoute.value.path !== '/login') {
    void router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  }
}
onMounted(() => {
  void session.ensureLoaded()
  globalThis.addEventListener('ruoyi:unauthorized', handleUnauthorized)
})
onUnmounted(() => globalThis.removeEventListener('ruoyi:unauthorized', handleUnauthorized))
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <RouterLink class="brand" to="/">AI Interview Coach</RouterLink>
      <nav class="site-nav" aria-label="主导航">
        <RouterLink to="/questions">题库</RouterLink>
        <RouterLink class="interview-link" :class="{ 'is-current': route.path === '/interviews/new' && route.query.mode !== 'voice' }" to="/interviews/new">文字面试</RouterLink>
        <RouterLink class="interview-link" :class="{ 'is-current': route.path === '/interviews/new' && route.query.mode === 'voice' }" to="/interviews/new?mode=voice">语音面试</RouterLink>
      </nav>
      <RouterLink class="header-action" :to="session.account ? '/account' : '/login'">
        {{ session.account?.displayName ?? '登录' }}
      </RouterLink>
    </header>
    <main><RouterView /></main>
  </div>
</template>
