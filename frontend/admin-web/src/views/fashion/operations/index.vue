<template>
  <div class="app-container operations-page">
    <el-alert title="阈值只形成告警候选；当前没有通知渠道，因此不能解释为告警已送达。保留策略页面只做 dry-run，不删除任何对象。" type="warning" :closable="false" show-icon class="mb16" />
    <div class="toolbar mb16"><el-button type="primary" icon="Refresh" :loading="loading" @click="load">刷新运行状态</el-button><el-button v-hasPermi="['fashion:operations:retention']" @click="loadRetention">计算待清理清单</el-button></div>
    <el-row v-if="overview" :gutter="12" class="mb16">
      <el-col v-for="metric in metrics" :key="metric.code" :xs="12" :sm="8" :lg="4"><el-card shadow="never" class="metric-card"><span>{{metric.label}}</span><strong>{{metric.value}} {{metric.unit==='count'?'个':metric.unit}}</strong><small>{{metric.window}}</small></el-card></el-col>
    </el-row>
    <el-card v-if="overview" shadow="never" class="mb16">
      <template #header><strong>阈值候选</strong></template>
      <div class="alerts"><el-tag v-for="item in overview.alertCandidates" :key="item.code" :type="item.thresholdReached?'danger':'success'">{{item.description}}：{{item.currentValue}} / {{item.thresholdValue}}</el-tag></div>
      <p class="muted">{{overview.notificationStatus}}</p>
    </el-card>
    <el-card v-if="overview" shadow="never" class="mb16">
      <template #header><strong>异常回查</strong></template>
      <el-table :data="overview.exceptions" empty-text="当前没有异常记录">
        <el-table-column prop="type" label="类型" width="100" /><el-table-column prop="id" label="记录 ID" width="160" /><el-table-column prop="correlationId" label="关联 ID" min-width="250"><template #default="{row}"><code>{{row.correlationId}}</code></template></el-table-column><el-table-column prop="status" label="状态" width="110" /><el-table-column prop="errorSummary" label="脱敏摘要" min-width="220" /><el-table-column prop="occurredAt" label="时间" min-width="180" />
      </el-table>
    </el-card>
    <el-card v-if="retention" shadow="never">
      <template #header><div class="card-header"><strong>保留策略 dry-run</strong><el-tag type="success">真实删除：关闭</el-tag></div></template>
      <el-descriptions :column="3" border class="mb16"><el-descriptions-item label="报价/交付">{{retention.quoteDays}} 天</el-descriptions-item><el-descriptions-item label="原始导入文件">{{retention.importFileDays}} 天</el-descriptions-item><el-descriptions-item label="失败未采用素材">{{retention.failedImageDays}} 天</el-descriptions-item></el-descriptions>
      <el-table :data="retention.candidates" empty-text="没有可计算的对象">
        <el-table-column prop="category" label="对象" width="130" /><el-table-column prop="recordId" label="记录 ID" width="150" /><el-table-column prop="objectKey" label="对象键" min-width="260" show-overflow-tooltip /><el-table-column label="判断" width="110"><template #default="{row}"><el-tag :type="retentionType(row.decision)">{{retentionLabel(row.decision)}}</el-tag></template></el-table-column><el-table-column prop="reason" label="解释" min-width="220" /><el-table-column prop="eligibleAt" label="预计到期" min-width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getFashionOperations, getRetentionDryRun } from '@/api/fashion/operations'
import type { FashionOperationsSnapshot, RetentionDryRun } from '@/api/fashion/types'

const loading=ref(false),overview=ref<FashionOperationsSnapshot>(),retention=ref<RetentionDryRun>()
let controller:AbortController|undefined
const metrics=computed(()=>overview.value?[{...overview.value.importFailureRate,label:'批次失败率'},{...overview.value.imageFailedOrUnknown,label:'图片失败/待查'},{...overview.value.expiredStock,label:'库存过期'},{...overview.value.deliveryFailureRate,label:'文件失败率'},{...overview.value.deliveryAverageDurationMs,label:'PPT/文件耗时'}]:[])
async function load(){controller?.abort();controller=new AbortController();loading.value=true;try{overview.value=(await getFashionOperations(50,controller.signal)).data}finally{loading.value=false}}
async function loadRetention(){controller?.abort();controller=new AbortController();loading.value=true;try{retention.value=(await getRetentionDryRun(controller.signal)).data}finally{loading.value=false}}
function retentionLabel(value:string){return({cleanable:'可清理',protected:'引用保护',extended:'已延长',not_due:'未到期'} as Record<string,string>)[value]??value}
function retentionType(value:string){return value==='cleanable'?'warning':value==='protected'?'success':value==='extended'?'primary':'info'}
onMounted(load);onBeforeUnmount(()=>controller?.abort())
</script>

<style scoped>.mb16{margin-bottom:16px}.toolbar,.alerts,.card-header{display:flex;gap:10px;align-items:center;flex-wrap:wrap}.card-header{justify-content:space-between}.metric-card :deep(.el-card__body){display:flex;flex-direction:column;gap:8px}.metric-card strong{font-size:25px;color:var(--el-color-primary)}.metric-card span,.metric-card small,.muted{color:var(--el-text-color-secondary)}@media(max-width:720px){.operations-page :deep(.el-descriptions__body){overflow:auto}.metric-card strong{font-size:20px}}</style>
