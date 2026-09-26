// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import TaskCenterView from '../../src/renderer/src/features/task-center/TaskCenterView.vue'
import { useWorkspaceStore } from '../../src/renderer/src/stores/workspace'
import { useTasksStore } from '../../src/renderer/src/stores/tasks'
import { useSessionStore } from '../../src/renderer/src/stores/session'
import { parseCanonicalInt64 } from '../../src/shared/contracts/wire-scalars'

const workspaceId = '11111111-1111-4111-8111-111111111111'

describe('TaskCenterView', () => {
  beforeEach(() => {
    const pinia = createPinia(); setActivePinia(pinia)
    const session = useSessionStore()
    session.phase = 'authenticated'
    session.context = { authenticated: true, workspaceId, sessionEpoch: 1, workspaceEpoch: 2 }
    const workspace = useWorkspaceStore()
    workspace.items = [{ workspaceId, displayName: '合成空间', role: 'OWNER', status: 'ACTIVE', version: parseCanonicalInt64('1'), createdAt: '2026-09-13T00:00:00Z' }]
    workspace.selectedId = workspaceId
    useTasksStore().replace([task(['SUBMIT_FOR_VALIDATION'])])
  })

  it('renders only public allowedCommands from the server projection', async () => {
    const pinia = createPinia(); setActivePinia(pinia)
    const session = useSessionStore(); session.phase = 'authenticated'; session.context = { authenticated: true, workspaceId, sessionEpoch: 1, workspaceEpoch: 2 }
    const workspace = useWorkspaceStore(); workspace.items = [{ workspaceId, displayName: '合成空间', role: 'OWNER', status: 'ACTIVE', version: parseCanonicalInt64('1'), createdAt: '2026-09-13T00:00:00Z' }]; workspace.selectedId = workspaceId
    useTasksStore().replace([task(['SUBMIT_FOR_VALIDATION'])])
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: TaskCenterView }] })
    router.push('/'); await router.isReady()
    const wrapper = mount(TaskCenterView, { global: { plugins: [pinia, router] } })
    expect(wrapper.text()).toContain('提交验证')
    expect(wrapper.text()).not.toContain('请求取消')
    expect(wrapper.text()).not.toContain('VALIDATION_PASSED')
  })
})

function task(commands: ('SUBMIT_FOR_VALIDATION' | 'REQUEST_CANCEL')[]) {
  return {
    taskId: '22222222-2222-4222-8222-222222222222', workspaceId, taskType: 'SYNTHETIC_CORE' as const,
    capabilityCode: 'CORE' as const, title: '合成任务', state: 'DRAFT' as const,
    version: parseCanonicalInt64('1'), allowedCommands: commands, steps: [], reasonCode: null,
    createdAt: '2026-09-13T00:00:00Z', updatedAt: '2026-09-13T00:00:00Z',
    correlationId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
  }
}
