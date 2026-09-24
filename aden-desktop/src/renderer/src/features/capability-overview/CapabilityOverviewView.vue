<script setup lang="ts">
import { useCapabilitiesStore } from '../../stores/capabilities'
import { useRunnersStore } from '../../stores/runners'
const capabilities = useCapabilitiesStore(); const runners = useRunnersStore()
</script>

<template>
  <header class="topbar"><div><p class="eyebrow">SERVER PROJECTION</p><h1>能力总览</h1><p class="lede">四个能力共享同一任务中心；WX、PUR、COL 当前没有真实外部动作授权。</p></div></header>
  <section class="module-grid"><article v-for="item in capabilities.items" :key="item.capabilityCode" class="module-card"><span class="module-code">{{ item.capabilityCode }}</span><span class="module-state">{{ item.status }}</span><strong>{{ item.capabilityCode === 'CORE' ? '共用执行底座' : `${item.capabilityCode} 能力包` }}</strong><small>{{ item.nextGate ?? '当前门禁已满足' }}</small><small>外部动作：{{ item.externalActionsEnabled ? '开启' : '关闭' }} · v{{ item.projectionVersion }}</small></article></section>
  <section class="page-card"><div class="section-heading"><div><p class="eyebrow">READ ONLY</p><h2>Runner 状态</h2></div><span>{{ runners.items.length }} 台</span></div><div v-if="runners.items.length === 0" class="empty-card">暂无 Runner 投影。</div><div v-for="runner in runners.items" :key="runner.runnerId" class="runner-row"><strong>{{ runner.displayName }}</strong><span>{{ runner.presence }}</span><small>{{ runner.capabilities.join(' / ') }} · {{ runner.lastSeenAt ?? '尚无心跳' }}</small></div></section>
</template>
