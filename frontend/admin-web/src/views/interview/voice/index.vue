<script setup>
import { onMounted, ref } from 'vue'
import request from '@/utils/request'
const result = ref(null)
const loading = ref(false)
const error = ref('')
async function refresh() {
  loading.value = true
  error.value = ''
  result.value = null
  try { result.value = await request({ url: '/api/v1/voice-capabilities', method: 'get' }) }
  catch { error.value = '状态读取失败，请检查登录权限或服务连接后重试。' }
  finally { loading.value = false }
}
onMounted(refresh)
</script>
<template>
  <div class="app-container">
    <el-alert title="语音面试使用 RuoYi 当前登录主体和权限。" type="info" :closable="false" show-icon />
    <el-card class="voice-card" shadow="never">
      <h2>语音面试运行状态</h2>
      <el-button :loading="loading" @click="refresh">重新检查</el-button>
      <p v-if="error" role="alert">{{ error }}</p>
      <template v-if="result">
        <p><el-tag :type="result.status === 'VOICE_NOT_READY' ? 'warning' : 'info'">{{ result.status === 'VOICE_NOT_READY' ? '语音尚未就绪，请查看检查结果' : result.providerConnectivity === 'PASS' ? '最近识别和合成调用成功' : '配置完整，等待真实语音验证' }}</el-tag></p>
        <el-table :data="result.checks"><el-table-column prop="label" label="检查项" /><el-table-column label="结果"><template #default="{ row }">{{ row.ready ? '可用' : row.reasonCode }}</template></el-table-column></el-table>
        <p v-for="(observation, name) in result.providerObservations" :key="name">{{ name }} 最近调用：{{ observation.status }} · {{ observation.reasonCode ?? '尚未验证' }} · {{ observation.observedAt ?? '无调用时间' }}</p>
        <p class="muted">检查时间：{{ result.observedAt }}。最近调用仅代表所示时间的结果，重启后清空；重新检查不会发起收费调用。</p>
      </template>
      <p class="muted">用户端在未就绪、无权限、麦克风拒绝或断线时应恢复到文字回答。</p>
    </el-card>
  </div>
</template>

<style scoped>
.voice-card { margin-top: 20px; max-width: 720px; }
.muted { color: #909399; }
</style>
