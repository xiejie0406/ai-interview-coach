<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiRequest, apiRequestWithMeta, jsonBody, newOperationKey } from '@/shared/api/client'

type Plan = { id: string; planVersionNo: number; state: string; questionCount: number; estimatedUsage: { quantity: number; unit: string; estimateVersion: string }; version: number }
type Snapshot = { id: string }

const router = useRouter()
const form = reactive({ targetRole: 'JAVA_BACKEND', targetLevel: 'MID', durationMinutes: 30, mode: 'TEXT', topics: ['Spring Boot'] })
const plan = ref<Plan | null>(null)
const planEtag = ref('')
const busy = ref(false)
const error = ref('')

async function createPlan() {
  busy.value = true
  error.value = ''
  try {
    const response = await apiRequestWithMeta<Plan>('/interview-plans', {
      method: 'POST', body: jsonBody(form), headers: { 'Idempotency-Key': newOperationKey() },
    })
    plan.value = response.data
    planEtag.value = response.etag ?? `"v${response.data.version}"`
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '面试计划创建失败'
  } finally {
    busy.value = false
  }
}

async function confirmAndStart() {
  if (!plan.value) return
  busy.value = true
  try {
    const confirmed = await apiRequest<Plan>(`/interview-plans/${plan.value.id}/commands/confirm`, {
      method: 'POST',
      body: jsonBody({ acknowledgedEstimateVersion: plan.value.estimatedUsage.estimateVersion }),
      headers: { 'If-Match': planEtag.value || `"v${plan.value.version}"`, 'Idempotency-Key': newOperationKey() },
    })
    const snapshot = await apiRequest<Snapshot>('/interviews', {
      method: 'POST', body: jsonBody({ confirmedPlanId: confirmed.id, planVersionNo: confirmed.planVersionNo }), headers: { 'Idempotency-Key': newOperationKey() },
    })
    await router.push(`/interviews/${encodeURIComponent(snapshot.id)}`)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '面试启动失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="page-width section-page setup-layout">
    <form class="content-card setup-form" @submit.prevent="createPlan">
      <span class="eyebrow">NEW INTERVIEW</span><h1>配置模拟面试</h1>
      <label>目标岗位<select v-model="form.targetRole"><option value="JAVA_BACKEND">Java 后端</option><option value="AI_APPLICATION">AI 应用</option><option value="AGENT_ENGINEER">Agent 工程</option></select></label>
      <label>目标级别<select v-model="form.targetLevel"><option value="JUNIOR">初级</option><option value="MID">中级</option><option value="SENIOR">高级</option></select></label>
      <label>面试时长<input v-model.number="form.durationMinutes" type="number" min="10" max="120" /></label>
      <label>面试模式<select v-model="form.mode"><option value="TEXT">文字面试</option><option value="CASCADE_VOICE">语音面试</option></select></label>
      <button class="primary-button" :disabled="busy">生成面试计划</button>
      <p v-if="error" class="error-message">{{ error }}</p>
    </form>
    <article class="content-card plan-preview">
      <h2>计划预览</h2>
      <div v-if="plan"><p>{{ plan.questionCount }} 道题 · {{ plan.state }}</p><p>预计用量：{{ plan.estimatedUsage.quantity }} {{ plan.estimatedUsage.unit }}</p><button class="primary-button" :disabled="busy" @click="confirmAndStart">确认并进入面试</button></div>
      <p v-else class="muted">生成后显示服务端返回的题量、用量和计划状态。</p>
    </article>
  </section>
</template>
