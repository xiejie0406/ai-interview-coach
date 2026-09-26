<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiRequest, ApiClientError } from '@/shared/api/client'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const formRef = ref()
// 仅本地开发预填若依初始化账号；正式构建不带默认登录信息。
const form = ref({
  username: import.meta.env.DEV ? 'admin' : '',
  password: import.meta.env.DEV ? 'admin123' : '',
  code: '',
  uuid: '',
})
const captcha = ref('')
const captchaEnabled = ref(true)
const captchaLoading = ref(false)
const loading = ref(false)
const error = ref('')
const captchaSrc = computed(() => captcha.value ? `data:image/gif;base64,${captcha.value}` : '')

async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    const result = await apiRequest<{ captchaEnabled?: boolean; uuid?: string; img?: string; data?: { captchaEnabled?: boolean; uuid?: string; img?: string } }>('/captchaImage')
    const payload = result.data ?? result
    captchaEnabled.value = payload.captchaEnabled !== false
    captcha.value = payload.img ?? ''
    form.value.uuid = payload.uuid ?? ''
    if (!captchaEnabled.value) form.value.code = ''
  } catch (cause) {
    captcha.value = ''
    error.value = cause instanceof ApiClientError ? cause.message : '验证码加载失败，请稍后重试'
  } finally {
    captchaLoading.value = false
  }
}

async function submit() {
  if (loading.value) return
  error.value = ''
  if (!formRef.value?.validate()) return
  loading.value = true
  try {
    await session.login(form.value.username.trim(), form.value.password, form.value.code.trim(), form.value.uuid)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') && !route.query.redirect.startsWith('//') ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (cause) {
    error.value = cause instanceof ApiClientError ? cause.message : cause instanceof Error ? cause.message : '登录失败，请稍后重试'
    if (captchaEnabled.value) await refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => { void refreshCaptcha() })
</script>

<template>
  <section class="ruoyi-login-page">
    <el-form ref="formRef" :model="form" class="ruoyi-login-form" label-position="top" @submit.prevent="submit">
      <div class="login-heading">
        <span class="eyebrow">AI INTERVIEW COACH</span>
        <h1>登录面试教练</h1>
        <p>进入题库学习与文字、语音面试空间</p>
      </div>
      <el-form-item prop="username" label="账号" :rules="[{ required: true, message: '请输入您的账号', trigger: 'blur' }]">
        <el-input v-model="form.username" size="large" autocomplete="username" placeholder="请输入账号" @keyup.enter="submit" />
      </el-form-item>
      <el-form-item prop="password" label="密码" :rules="[{ required: true, message: '请输入您的密码', trigger: 'blur' }]">
        <el-input v-model="form.password" size="large" type="password" show-password autocomplete="current-password" placeholder="请输入密码" @keyup.enter="submit" />
      </el-form-item>
      <el-form-item v-if="captchaEnabled" prop="code" label="验证码" :rules="[{ required: true, message: '请输入验证码', trigger: 'change' }]">
        <div class="ruoyi-captcha-row">
          <el-input v-model="form.code" size="large" autocomplete="off" placeholder="请输入验证码" @keyup.enter="submit" />
          <button class="captcha-button" type="button" :disabled="captchaLoading" aria-label="刷新验证码" @click="refreshCaptcha">
            <img v-if="captchaSrc" :src="captchaSrc" alt="验证码" />
            <span v-else>{{ captchaLoading ? '加载中' : '刷新验证码' }}</span>
          </button>
        </div>
      </el-form-item>
      <p v-if="error" class="login-error" role="alert">{{ error }}</p>
      <el-button class="ruoyi-submit" native-type="submit" type="primary" size="large" :loading="loading" :disabled="loading">{{ loading ? '登录中...' : '登 录' }}</el-button>
      <RouterLink class="login-register" to="/register">账号开通请联系平台管理员</RouterLink>
    </el-form>
    <footer class="ruoyi-login-footer">AI Interview Coach · Secure RuoYi authentication</footer>
  </section>
</template>

<style scoped>
.ruoyi-login-page { min-height: calc(100vh - 68px); display: flex; justify-content: center; align-items: center; padding: 40px 20px 72px; background: linear-gradient(rgba(0,0,0,.12), rgba(0,0,0,.12)), url('../../assets/images/login-background.jpg') center/cover; }
.ruoyi-login-form { width: min(400px, 100%); padding: 26px 26px 18px; border-radius: 6px; background: #fff; box-shadow: 0 12px 34px rgba(0,0,0,.16); }
.login-heading { margin-bottom: 22px; text-align: center; }
.login-heading h1 { margin: 10px 0 8px; color: #303133; font-size: 25px; }
.login-heading p { margin: 0; color: #909399; font-size: 13px; }
.ruoyi-login-form :deep(.el-form-item) { margin-bottom: 18px; }
.ruoyi-login-form :deep(.el-form-item__label) { padding-bottom: 5px; color: #606266; font-weight: 600; }
.ruoyi-captcha-row { display: grid; grid-template-columns: minmax(0, 1fr) 118px; gap: 10px; width: 100%; }
.captcha-button { display: grid; place-items: center; min-width: 0; height: 40px; overflow: hidden; border: 1px solid #dcdfe6; border-radius: 4px; background: #f8f9fb; color: #606266; font-size: 12px; }
.captcha-button:not(:disabled) { cursor: pointer; }
.captcha-button img { display: block; width: 100%; height: 40px; object-fit: cover; }
.ruoyi-submit { width: 100%; margin-top: 4px; }
.login-error { margin: -4px 0 14px; padding: 9px 12px; border-radius: 4px; color: #f56c6c; background: #fef0f0; font-size: 13px; line-height: 1.5; }
.login-register { display: block; margin-top: 18px; color: #409eff; text-align: center; font-size: 13px; }
.ruoyi-login-footer { position: fixed; right: 0; bottom: 12px; left: 0; color: #fff; text-align: center; font-size: 12px; letter-spacing: .04em; }
@media (max-width: 520px) { .ruoyi-login-page { min-height: calc(100vh - 68px); padding: 24px 14px 66px; align-items: flex-start; } .ruoyi-login-form { margin-top: 7vh; padding: 22px 18px 16px; } .ruoyi-captcha-row { grid-template-columns: minmax(0, 1fr) 104px; } .ruoyi-login-footer { padding: 0 14px; font-size: 11px; } }
</style>
