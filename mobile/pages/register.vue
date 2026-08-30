<script setup>
import { onMounted, reactive, ref } from 'vue'
import { currentPolicies, login, registerAccount } from '@/api/session'

const form = reactive({ displayName: '', email: '', password: '', confirmPassword: '' })
const policies = ref([])
const accepted = ref(false)
const busy = ref(false)
const error = ref('')

onMounted(async () => {
  try {
    policies.value = (await currentPolicies()).policies || []
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '注册政策加载失败'
  }
})

async function submit() {
  if (form.password !== form.confirmPassword) { error.value = '两次输入的密码不一致'; return }
  if (!accepted.value) { error.value = '请先同意服务条款与隐私说明'; return }
  busy.value = true
  error.value = ''
  try {
    const acceptedPolicyVersions = Object.fromEntries(policies.value.filter(item => item.requiredForRegistration !== false).map(item => [item.purpose, item.versionId]))
    await registerAccount({
      email: form.email.trim(), password: form.password, displayName: form.displayName.trim(),
      locale: 'zh-CN', timeZone: globalThis.Intl?.DateTimeFormat?.().resolvedOptions().timeZone || 'Asia/Shanghai',
      acceptedPolicyVersions
    })
    await login(form.email.trim(), form.password)
    uni.reLaunch({ url: '/pages/index' })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '注册失败'
  } finally { busy.value = false }
}
</script>

<template>
  <view class="page"><view class="card"><text class="eyebrow">CREATE ACCOUNT</text><text class="title">创建体验账号</text>
    <text class="label">昵称</text><input v-model="form.displayName" maxlength="80" placeholder="请输入昵称" />
    <text class="label">邮箱</text><input v-model="form.email" type="text" placeholder="请输入邮箱" />
    <text class="label">密码</text><input v-model="form.password" password placeholder="至少 8 位" />
    <text class="label">确认密码</text><input v-model="form.confirmPassword" password placeholder="再次输入密码" />
    <label class="consent"><checkbox :checked="accepted" @click="accepted=!accepted" />我已阅读并同意服务条款与隐私说明</label>
    <view v-for="item in policies" :key="item.purpose" class="policy"><text class="policy-title">{{item.title}}</text><text>{{item.summary}}</text></view>
    <text v-if="error" class="error">{{error}}</text>
    <button type="primary" :loading="busy" :disabled="busy||policies.length<2" @click="submit">创建并登录</button>
    <navigator url="/pages/login" class="link">已有账号，返回登录</navigator>
  </view></view>
</template>

<style scoped>.page{min-height:100vh;padding:40rpx;background:#f5f7fb}.card{display:flex;flex-direction:column;gap:20rpx;padding:40rpx;background:#fff;border-radius:28rpx}.eyebrow{color:#3157d5;font-size:22rpx;font-weight:800;letter-spacing:3rpx}.title{font-size:44rpx;font-weight:800}.label{font-weight:600}input{padding:24rpx;border:1px solid #d8deeb;border-radius:16rpx}.consent{display:flex;align-items:flex-start;line-height:1.6}.policy{display:flex;flex-direction:column;gap:8rpx;padding:18rpx;background:#f5f7fb;border-radius:14rpx;color:#667085}.policy-title{font-weight:700;color:#344054}.error{color:#b42318}.link{text-align:center;color:#3157d5}</style>
