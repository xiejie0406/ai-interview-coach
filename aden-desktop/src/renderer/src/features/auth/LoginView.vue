<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '../../stores/session'
import { useWorkspaceStore } from '../../stores/workspace'

const session = useSessionStore()
const workspace = useWorkspaceStore()
const router = useRouter()
const form = reactive({ username: '', password: '', code: '' })
onMounted(() => { void session.loadCaptcha() })

async function submit(): Promise<void> {
  try {
    await session.login({ username: form.username, password: form.password,
      ...(session.captcha?.enabled ? { code: form.code, uuid: session.captcha.uuid ?? undefined } : {}) })
    await workspace.loadList()
    if (workspace.items.length === 1) await workspace.select(workspace.items[0].workspaceId)
    await router.replace('/tasks')
  } catch {
    // Store 已保存可展示错误；表单事件不向 Vue 泄漏未处理 rejection。
  }
}
</script>

<template>
  <main class="login-page"><section class="login-card"><span class="brand-mark">A</span><p class="eyebrow">FEAT-ADEN-001</p><h1>登录 Aden</h1><p class="lede">使用 RuoYi 账号进入获授权的 Workspace。Token 只驻留桌面主进程内存。</p><form @submit.prevent="submit"><label>用户名<input v-model.trim="form.username" name="username" autocomplete="username" maxlength="64" required /></label><label>密码<input v-model="form.password" name="password" type="password" autocomplete="current-password" maxlength="256" required /></label><label v-if="session.captcha?.enabled" class="captcha-row">验证码<span><input v-model.trim="form.code" name="captcha" maxlength="16" required /><img :src="session.captcha.jpegDataUrl ?? ''" alt="登录验证码" @click="session.loadCaptcha" /></span></label><p v-if="session.error" class="error-banner" role="alert">{{ session.error }}</p><button class="primary-button" type="submit" :disabled="session.phase === 'loading'">{{ session.phase === 'loading' ? '正在登录…' : '登录' }}</button></form><small>当前只开放合成 CORE；不会连接微信、电商、ERP 或外部发送。</small></section></main>
</template>
