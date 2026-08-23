<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import { apiRequest } from '@/shared/api/client'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const email = ref('')
const password = ref('')
const code = ref('')
const uuid = ref('')
const captcha = ref('')
const busy = ref(false)
const error = ref('')

async function submit() {
  busy.value = true
  error.value = ''
  try {
    await session.login(email.value.trim(), password.value, code.value.trim(), uuid.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/questions'
    await router.replace(redirect)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败'
  } finally {
    busy.value = false
  }
}

async function refreshCaptcha() {
  const result = await apiRequest<any>('/captchaImage')
  uuid.value = result.uuid || result.data?.uuid || ''
  captcha.value = result.img || result.data?.img || ''
}
onMounted(() => { void refreshCaptcha() })
</script>

<template>
  <section class="auth-page">
    <form class="auth-card" @submit.prevent="submit">
      <span class="eyebrow">WELCOME BACK</span>
      <h1>登录面试教练</h1>
      <label>邮箱<input v-model="email" type="email" autocomplete="username" required /></label>
      <label>密码<input v-model="password" type="password" autocomplete="current-password" required /></label>
      <label>验证码<div class="captcha-row"><input v-model="code" required /><button type="button" @click="refreshCaptcha"><img v-if="captcha" :src="`data:image/gif;base64,${captcha}`" alt="RuoYi 验证码" /><span v-else>刷新</span></button></div></label>
      <p v-if="error" class="error-message" role="alert">{{ error }}</p>
      <button class="primary-button" :disabled="busy">{{ busy ? '登录中…' : '登录' }}</button>
      <RouterLink class="card-link" to="/register">没有账号？创建一个本地体验账号</RouterLink>
    </form>
  </section>
</template>
