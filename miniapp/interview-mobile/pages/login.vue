<script setup>
import { onMounted, ref } from 'vue'
import { login } from '@/api/session'
import { interviewRequest } from '@/utils/interviewRequest'

const email = ref('')
const password = ref('')
const code = ref('')
const uuid = ref('')
const captcha = ref('')
const busy = ref(false)
const error = ref('')

async function submit() {
  busy.value = true; error.value = ''
  try { await login(email.value.trim(), password.value, code.value.trim(), uuid.value); uni.reLaunch({ url: '/pages/index' }) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '登录失败' }
  finally { busy.value = false }
}

async function refreshCaptcha() {
  const result = await interviewRequest({ url: '/captchaImage' })
  uuid.value = result.uuid || ''
  captcha.value = result.img || ''
}
onMounted(() => { void refreshCaptcha() })
</script>

<template>
  <view class="login-page"><view class="login-card"><text class="eyebrow">AI INTERVIEW COACH</text><text class="title">登录面试教练</text>
    <text class="label">邮箱</text><input v-model="email" type="text" placeholder="请输入邮箱" />
    <text class="label">密码</text><input v-model="password" password placeholder="请输入密码" />
    <text class="label">验证码</text><view class="captcha-row"><input v-model="code" placeholder="请输入验证码" /><image v-if="captcha" :src="`data:image/gif;base64,${captcha}`" mode="aspectFit" @click="refreshCaptcha" /><button v-else size="mini" @click="refreshCaptcha">刷新</button></view>
    <text v-if="error" class="error">{{ error }}</text><button type="primary" :loading="busy" @click="submit">登录</button>
    <navigator url="/pages/register" class="register-link">没有账号？创建体验账号</navigator>
    <text class="hint">账号、Token、权限和语音主体统一由 RuoYi 平台管理。</text></view></view>
</template>

<style scoped>
.login-page { min-height: 100vh; display:flex; align-items:center; padding:40rpx; background:#f5f7fb; }
.login-card { width:100%; display:flex; flex-direction:column; gap:22rpx; padding:42rpx; background:#fff; border-radius:30rpx; }
.eyebrow { color:#3157d5; font-size:22rpx; font-weight:800; letter-spacing:3rpx; }.title{font-size:44rpx;font-weight:800}.label{font-weight:600}
input{padding:24rpx;border:1px solid #d8deeb;border-radius:16rpx}.error{color:#b42318}.hint{color:#74809a;line-height:1.6}
.register-link{text-align:center;color:#3157d5}
</style>
