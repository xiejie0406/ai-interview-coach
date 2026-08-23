<script setup lang="ts">
import { onMounted } from 'vue'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
onMounted(() => session.ensureLoaded())
</script>

<template>
  <section class="page-width section-page narrow-page">
    <span class="eyebrow">ACCOUNT</span>
    <h1>账号中心</h1>
    <div class="content-card" v-if="session.account">
      <strong>{{ session.account.displayName }}</strong>
      <p>{{ session.account.email }}</p>
      <p class="muted">账号资料、隐私导出和删除请求将在后续迁移波次接入。</p>
    </div>
  </section>
</template>
