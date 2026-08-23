<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <section class="auth-page"><div class="auth-card">
    <span class="eyebrow">RUOYI ACCOUNT</span><h1>账号注册</h1>
    <p>主页不创建 AI 自建账号，请由 RuoYi 管理员按平台流程开通账号。</p>
    <button class="primary-button" @click="router.replace('/login')">返回登录</button>
  </div></section>
</template>
