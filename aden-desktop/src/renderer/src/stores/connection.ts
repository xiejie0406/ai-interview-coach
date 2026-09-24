import { defineStore } from 'pinia'
import type { EventBatch } from '../../../shared/contracts/operator-events'
import type { StreamStatus } from '../../../shared/contracts/desktop-api'
import { useWorkspaceStore } from './workspace'
import { useTasksStore } from './tasks'
import { useSessionStore } from './session'
import { router } from '../router'

let cleanup: (() => void) | null = null

export const useConnectionStore = defineStore('connection', {
  state: () => ({ state: 'STOPPED' as StreamStatus['state'], reason: null as string | null, processing: false }),
  actions: {
    startListeners(): void {
      cleanup?.()
      const stopBatch = window.adenDesktop.events.onBatch((batch) => {
        void this.applyBatch(batch).catch(() => {
          if (useSessionStore().phase !== 'authenticated') return
          this.state = 'DEGRADED'
          this.reason = 'event-apply-failed'
          void useWorkspaceStore().refreshBootstrap().catch(() => undefined)
        })
      })
      const stopStatus = window.adenDesktop.events.onStatus((status) => this.applyStatus(status))
      cleanup = () => { stopBatch(); stopStatus(); cleanup = null }
    },
    stopListeners(): void { cleanup?.() },
    applyStatus(status: StreamStatus): void {
      const context = useSessionStore().context
      if (status.sessionEpoch !== context.sessionEpoch || status.workspaceEpoch !== context.workspaceEpoch
          || status.workspaceId !== context.workspaceId) return
      this.state = status.state
      this.reason = status.reason ?? null
      if (status.state === 'UNAUTHENTICATED') {
        useWorkspaceStore().resetWorkspaceData()
        useSessionStore().clear()
        void router.replace({ name: 'login' }).catch(() => undefined)
      }
      if (status.state === 'DEGRADED') void useWorkspaceStore().refreshBootstrap().catch(() => undefined)
    },
    async applyBatch(batch: EventBatch): Promise<void> {
      const workspace = useWorkspaceStore()
      let context
      try { context = workspace.context() } catch { return }
      if (!sameContext(batch, context) || this.processing) return
      this.processing = true
      try {
        const taskIds = new Set(batch.events.filter((event) => event.aggregateType === 'TASK').map((event) => event.aggregateId))
        if (batch.events.some((event) => event.aggregateType !== 'TASK')) {
          await workspace.refreshBootstrap()
        } else {
          for (const taskId of taskIds) await useTasksStore().refresh(context, taskId)
        }
        await window.adenDesktop.events.ack({ ...context, batchId: batch.batchId, lastCursor: batch.lastCursor })
      } finally {
        this.processing = false
      }
    }
  }
})

function sameContext(left: { sessionEpoch: number; workspaceEpoch: number; workspaceId: string }, right: { sessionEpoch: number; workspaceEpoch: number; workspaceId: string }): boolean {
  return left.sessionEpoch === right.sessionEpoch && left.workspaceEpoch === right.workspaceEpoch && left.workspaceId === right.workspaceId
}
