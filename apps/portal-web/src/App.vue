<script setup lang="ts">
import { onMounted } from 'vue'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
onMounted(() => session.ensureLoaded())
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <RouterLink class="brand" to="/">AI Interview Coach</RouterLink>
      <nav class="site-nav" aria-label="主导航">
        <RouterLink to="/questions">题库</RouterLink>
        <RouterLink to="/interviews/new">模拟面试</RouterLink>
        <RouterLink to="/interviews/new?mode=voice">语音面试</RouterLink>
      </nav>
      <RouterLink class="header-action" :to="session.account ? '/account' : '/login'">
        {{ session.account?.displayName ?? '登录' }}
      </RouterLink>
    </header>
    <main><RouterView /></main>
  </div>
</template>
