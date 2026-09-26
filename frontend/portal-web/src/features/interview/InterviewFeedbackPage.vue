<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { apiRequest, ApiClientError, jsonBody, newOperationKey } from '@/shared/api/client'
type Feedback = { interviewId: string; model: string; createdAt: string; limitations: string[]; turns: Array<{ turnId: string; question: string; answer: string; rubricVersionId: string; dimensions: Array<{ code: string; criterion: string; judgement: string; quote: string; feedback: string }> }> }
type SessionSnapshot = { state: string }
const route = useRoute()
const report = ref<Feedback | null>(null)
const sessionState = ref('')
const busy = ref(false)
const error = ref('')
const consent = ref(false)
const labels: Record<string, string> = { SUPPORTED: '有依据支持', PARTIAL: '部分支持', INSUFFICIENT_EVIDENCE: '证据不足' }
const endpoint = `/interviews/${encodeURIComponent(String(route.params.interviewId))}/feedback`
const interviewEndpoint = `/interviews/${encodeURIComponent(String(route.params.interviewId))}`
const canGenerate = computed(() => sessionState.value === 'COMPLETED')
async function load() {
  busy.value = true; error.value = ''
  try { report.value = await apiRequest<Feedback>(endpoint) }
  catch (cause) {
    if (cause instanceof ApiClientError && cause.status === 404) report.value = null
    else error.value = cause instanceof Error ? cause.message : '读取反馈失败'
  }
  finally { busy.value = false }
}
async function loadSession() {
  try { sessionState.value = (await apiRequest<SessionSnapshot>(interviewEndpoint)).state }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '读取面试状态失败' }
}
async function generate() {
  if (!consent.value || !canGenerate.value || busy.value) return
  busy.value = true; error.value = ''
  try {
    const policies = await apiRequest<{ policies: Array<{ purpose: string; versionId: string }> }>('/policies/current')
    const policy = policies.policies.find(p => p.purpose === 'MODEL_PROCESSING')
    if (!policy) throw new Error('模型处理政策不可用')
    await apiRequest('/consents/MODEL_PROCESSING/grants', { method: 'POST', headers: { 'Idempotency-Key': newOperationKey() }, body: jsonBody({ policyVersionId: policy.versionId, acknowledgement: true }) })
    report.value = await apiRequest<Feedback>(endpoint, { method: 'POST', headers: { 'Idempotency-Key': newOperationKey() } })
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '生成反馈失败，可稍后重新读取' }
  finally { busy.value = false }
}
onMounted(async () => { await Promise.all([loadSession(), load()]) })
</script>
<template>
  <main class="feedback-page">
    <RouterLink :to="`/interviews/${route.params.interviewId}`">← 返回面试</RouterLink>
    <h1>面试反馈报告</h1>
    <p>按题目评分标准逐项解释，引用你的已确认回答。</p>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <p v-if="busy" role="status">正在处理，请稍候。生成反馈可能需要一分钟。</p>
    <section v-if="!report" class="panel">
      <p v-if="canGenerate">面试已经结束，可以基于已确认回答生成反馈。</p>
      <p v-else>当前面试尚未结束。请返回面试完成回答或主动结束后，再生成反馈。</p>
      <label><input v-model="consent" type="checkbox"> 我同意将本次已确认回答发送给模型服务，用于生成面试反馈。</label>
      <div class="actions"><button :disabled="busy || !consent || !canGenerate" @click="generate">生成反馈报告</button><button :disabled="busy" @click="load">重新读取</button></div>
    </section>
    <template v-if="report">
      <p class="meta">生成时间：{{ new Date(report.createdAt).toLocaleString() }} · 模型：{{ report.model }}</p>
      <p v-for="limitation in report.limitations" :key="limitation" class="notice">{{ limitation }}</p>
      <article v-for="(turn, index) in report.turns" :key="turn.turnId" class="panel">
        <h2>第 {{ index + 1 }} 个回答</h2><h3>{{ turn.question }}</h3>
        <details><summary>查看已确认回答</summary><p class="answer">{{ turn.answer }}</p></details>
        <section v-for="dimension in turn.dimensions" :key="dimension.code" class="dimension">
          <h4>{{ dimension.code }} · {{ labels[dimension.judgement] ?? dimension.judgement }}</h4>
          <p><strong>标准：</strong>{{ dimension.criterion }}</p>
          <blockquote v-if="dimension.quote">{{ dimension.quote }}</blockquote>
          <p v-else>未找到足够的原话证据。</p>
          <p><strong>反馈：</strong>{{ dimension.feedback }}</p>
        </section>
        <small>评分标准版本：{{ turn.rubricVersionId }}</small>
      </article>
    </template>
  </main>
</template>
<style scoped>
.feedback-page{max-width:1000px;margin:0 auto;padding:32px 20px 64px;color:#263551}.panel{padding:24px;margin:20px 0;border:1px solid #e0e6ef;border-radius:16px;background:#fff}.dimension{margin-top:20px;padding-top:10px;border-top:1px solid #e0e6ef}.dimension p,.answer{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.8}blockquote{margin:12px 0;padding:12px 16px;border-left:4px solid #3457d5;background:#f3f6ff;white-space:pre-wrap}.error{color:#a72d27}.notice{padding:12px;background:#fffaf1}.meta,small{color:#65728b}.actions{display:flex;gap:12px;margin-top:18px}button{padding:10px 16px;border:1px solid #3457d5;border-radius:8px;color:#3457d5;background:white;cursor:pointer}button:disabled{opacity:.5;cursor:default}input{width:auto}h3{line-height:1.7}
</style>
