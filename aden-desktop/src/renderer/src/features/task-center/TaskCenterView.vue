<script setup lang="ts">
import { computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useWorkspaceStore } from '../../stores/workspace'
import { useTasksStore } from '../../stores/tasks'
import { useCapabilitiesStore } from '../../stores/capabilities'

const workspace = useWorkspaceStore(); const tasks = useTasksStore(); const capabilities = useCapabilitiesStore(); const router = useRouter()
const draft = reactive({ title: '', instruction: '', expectedOutcome: 'SUCCEED' as 'SUCCEED' | 'FAIL_VALIDATION' | 'CANCEL_AT_SAFE_POINT' })
const visibleTasks = computed(() => capabilities.filter ? tasks.items.filter((task) => task.capabilityCode === capabilities.filter) : tasks.items)
onMounted(async () => {
  try { if (workspace.items.length === 0) await workspace.loadList(); if (!workspace.selectedId && workspace.items.length === 1) await workspace.select(workspace.items[0].workspaceId) }
  catch { /* Store 已保存错误。 */ }
})
async function choose(event: Event): Promise<void> {
  const id = (event.target as HTMLSelectElement).value
  if (id) await workspace.select(id).catch(() => undefined)
}
async function createTask(): Promise<void> {
  try {
    await tasks.create(workspace.context(), { taskType: 'SYNTHETIC_CORE', capabilityCode: 'CORE', title: draft.title,
      input: { fixtureId: 'fixture:desktop-core', instruction: draft.instruction, expectedOutcome: draft.expectedOutcome } })
    draft.title = ''; draft.instruction = ''
  } catch { /* Store 已保存错误。 */ }
}
async function refresh(): Promise<void> {
  await workspace.refreshBootstrap().catch(() => undefined)
}
async function command(taskId: string, value: 'SUBMIT_FOR_VALIDATION' | 'REQUEST_CANCEL'): Promise<void> {
  if (value === 'REQUEST_CANCEL' && !window.confirm('确认请求在 Runner 安全点取消该任务？')) return
  await tasks.command(workspace.context(), taskId,
    value === 'REQUEST_CANCEL' ? { command: value, reasonCode: 'USER_REQUESTED' } : { command: value })
    .catch(() => undefined)
}
</script>

<template>
  <header class="topbar"><div><p class="eyebrow">FEAT-ADEN-001 · CORE</p><h1>任务中心</h1><p class="lede">服务端快照始终权威；桌面只展示公开 allowedCommands，不复制状态机。</p></div><span class="runtime-pill">{{ workspace.loading ? '同步中' : '快照已就绪' }}</span></header>
  <section class="toolbar-card"><label>Workspace<select :value="workspace.selectedId ?? ''" @change="choose"><option value="" disabled>请选择</option><option v-for="item in workspace.items" :key="item.workspaceId" :value="item.workspaceId">{{ item.displayName }} · {{ item.role }}</option></select></label><label>能力筛选<select :value="capabilities.filter ?? ''" @change="capabilities.setFilter(($event.target as HTMLSelectElement).value || null)"><option value="">全部</option><option value="CORE">CORE</option></select></label><button type="button" :disabled="!workspace.selectedId" @click="refresh">刷新快照</button></section>
  <p v-if="workspace.error || tasks.error" class="error-banner" role="alert">{{ workspace.error || tasks.error }}</p>
  <section v-if="workspace.selectedId" class="workspace-grid"><div><div class="section-heading"><div><p class="eyebrow">SERVER PROJECTION</p><h2>任务列表</h2></div><span>{{ visibleTasks.length }} 项</span></div><div v-if="visibleTasks.length === 0" class="empty-card">当前 Workspace 暂无任务。</div><article v-for="task in visibleTasks" :key="task.taskId" class="task-card"><button class="task-title" type="button" @click="router.push(`/tasks/${task.taskId}`)">{{ task.title }}</button><span class="state-chip">{{ task.state }}</span><p>{{ task.taskType }} · v{{ task.version }} · {{ task.steps.length }} steps</p><div class="command-row"><button v-for="allowed in task.allowedCommands" :key="allowed" type="button" @click="command(task.taskId, allowed)">{{ allowed === 'REQUEST_CANCEL' ? '请求取消' : '提交验证' }}</button><small v-if="task.allowedCommands.length === 0">当前状态无可用操作</small></div><p v-if="tasks.conflictTaskId === task.taskId" class="warning-text">版本已变化，已拉取最新快照，请重新确认操作。</p></article></div><aside class="detail-card create-card"><p class="eyebrow">SYNTHETIC CORE</p><h2>创建合成任务</h2><form @submit.prevent="createTask"><label>标题<input v-model.trim="draft.title" maxlength="120" required /></label><label>指令<textarea v-model="draft.instruction" maxlength="1000" required /></label><label>预期结果<select v-model="draft.expectedOutcome"><option value="SUCCEED">成功</option><option value="FAIL_VALIDATION">校验失败</option><option value="CANCEL_AT_SAFE_POINT">安全点取消</option></select></label><button class="primary-button" type="submit" :disabled="tasks.loading">创建 DRAFT</button></form></aside></section>
  <section v-else class="empty-card">选择一个 Workspace 后加载一致性 bootstrap。</section>
</template>
