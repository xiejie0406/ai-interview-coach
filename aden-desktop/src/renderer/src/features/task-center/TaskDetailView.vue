<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTasksStore } from '../../stores/tasks'
import { useWorkspaceStore } from '../../stores/workspace'

const route = useRoute(); const router = useRouter(); const tasks = useTasksStore(); const workspace = useWorkspaceStore()
const taskId = computed(() => String(route.params.taskId)); const task = computed(() => tasks.byId(taskId.value))
onMounted(() => { if (workspace.selectedId) void tasks.refresh(workspace.context(), taskId.value).catch(() => undefined) })
</script>

<template>
  <button class="back-button" type="button" @click="router.push('/tasks')">← 返回任务中心</button>
  <section v-if="task" class="page-card"><p class="eyebrow">TASK DETAIL · v{{ task.version }}</p><h1>{{ task.title }}</h1><span class="state-chip">{{ task.state }}</span><dl class="detail-list"><div><dt>Task ID</dt><dd>{{ task.taskId }}</dd></div><div><dt>Capability</dt><dd>{{ task.capabilityCode }}</dd></div><div><dt>Reason</dt><dd>{{ task.reasonCode ?? '—' }}</dd></div><div><dt>Updated</dt><dd>{{ task.updatedAt }}</dd></div></dl><h2>步骤</h2><ol class="step-list"><li v-for="step in task.steps" :key="step.stepId"><strong>#{{ step.ordinal }} · {{ step.state }}</strong><span>attempt {{ step.attemptNo }} · v{{ step.version }}<template v-if="step.progressPercent !== undefined"> · {{ step.progressPercent }}%</template></span></li></ol></section>
  <section v-else class="empty-card">正在从服务端读取任务快照…</section>
</template>
