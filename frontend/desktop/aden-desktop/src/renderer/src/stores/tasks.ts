import { defineStore } from 'pinia'
import type { CreateTaskRequest, OperatorTaskCommandRequest, TaskSnapshot } from '../../../shared/generated/operator-contracts'
import type { WorkspaceRequestContext } from '../../../shared/contracts/desktop-api'
import { compareCanonicalInt64 } from '../../../shared/contracts/wire-scalars'

interface PendingOperation {
  idempotencyKey: string
  kind: 'create' | 'command'
  signature: string
  etag?: string
}

export const useTasksStore = defineStore('tasks', {
  state: () => ({
    items: [] as TaskSnapshot[],
    etags: {} as Record<string, string>,
    pending: {} as Record<string, PendingOperation>,
    conflictTaskId: null as string | null,
    loading: false,
    error: null as string | null
  }),
  getters: {
    byId: (state) => (taskId: string) => state.items.find((item) => item.taskId === taskId) ?? null
  },
  actions: {
    replace(items: readonly TaskSnapshot[]): void {
      this.items = [...items]
      this.etags = {}
      this.pending = {}
      this.conflictTaskId = null
    },
    upsert(task: TaskSnapshot, etag?: string): void {
      const index = this.items.findIndex((item) => item.taskId === task.taskId)
      if (index >= 0 && compareCanonicalInt64(this.items[index].version, task.version) > 0) return
      if (index >= 0) this.items[index] = task
      else this.items.unshift(task)
      if (etag) this.etags[task.taskId] = etag
    },
    async create(context: WorkspaceRequestContext, request: CreateTaskRequest, operationKey?: string): Promise<TaskSnapshot> {
      this.loading = true
      this.error = null
      const signature = JSON.stringify(request)
      const previous = this.pending.create
      const key = operationKey ?? (previous?.kind === 'create' && previous.signature === signature
        ? previous.idempotencyKey : crypto.randomUUID())
      this.pending.create = { idempotencyKey: key, kind: 'create', signature }
      try {
        const response = await window.adenDesktop.tasks.create({ ...context, idempotencyKey: key, request })
        if (!sameContext(response.context, context)) throw new Error('已丢弃过期 Workspace 响应')
        this.upsert(response.value.task, response.value.etag)
        delete this.pending.create
        return response.value.task
      } catch (error) {
        this.error = safeMessage(error)
        throw error
      } finally {
        this.loading = false
      }
    },
    async refresh(context: WorkspaceRequestContext, taskId: string): Promise<TaskSnapshot> {
      const response = await window.adenDesktop.tasks.get({ ...context, taskId })
      if (!sameContext(response.context, context)) throw new Error('已丢弃过期 Workspace 响应')
      this.upsert(response.value.task, response.value.etag)
      return response.value.task
    },
    async command(context: WorkspaceRequestContext, taskId: string, command: OperatorTaskCommandRequest, operationKey?: string): Promise<TaskSnapshot> {
      this.error = null
      if (!this.etags[taskId]) await this.refresh(context, taskId)
      const signature = JSON.stringify(command)
      const previous = this.pending[taskId]
      const key = operationKey ?? (previous?.kind === 'command' && previous.signature === signature
        ? previous.idempotencyKey : crypto.randomUUID())
      const etag = operationKey === undefined && previous?.kind === 'command'
        && previous.signature === signature && previous.etag ? previous.etag : this.etags[taskId]
      this.pending[taskId] = { idempotencyKey: key, kind: 'command', signature, etag }
      try {
        const response = await window.adenDesktop.tasks.command({
          ...context, taskId, command, idempotencyKey: key, etag
        })
        if (!sameContext(response.context, context)) throw new Error('已丢弃过期 Workspace 响应')
        this.upsert(response.value.task, response.value.etag)
        delete this.pending[taskId]
        this.conflictTaskId = null
        return response.value.task
      } catch (error) {
        const status = (error as { status?: unknown }).status
        if (status === 409 || status === 412) {
          await this.refresh(context, taskId)
          this.conflictTaskId = taskId
          delete this.pending[taskId]
        }
        this.error = safeMessage(error)
        throw error
      }
    }
  }
})

function sameContext(left: { sessionEpoch: number; workspaceEpoch: number; workspaceId: string | null }, right: WorkspaceRequestContext): boolean {
  return left.sessionEpoch === right.sessionEpoch && left.workspaceEpoch === right.workspaceEpoch && left.workspaceId === right.workspaceId
}
function safeMessage(error: unknown): string { return error instanceof Error ? error.message : '任务操作失败' }
