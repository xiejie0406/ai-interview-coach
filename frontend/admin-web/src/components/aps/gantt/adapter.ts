import { Gantt, type GanttData, type GanttStatic, type Task } from 'dhtmlx-gantt'

import type {
  ApsAdjustmentIntentHandler,
  ApsGanttModel
} from '@/types/aps/planning'
import { mapApsGanttModel } from '@/components/aps/gantt/mapping'

export interface ApsGanttRuntime {
  init(container: HTMLElement): void
  parse(data: GanttData): void
  clearAll(): void
  attachEvent(name: string, handler: (...args: unknown[]) => unknown): string
  detachEvent(eventId: string): void
  getTask(taskId: string | number): Task
  showTask(taskId: string | number): void
  zoomIn(): void
  zoomOut(): void
  destructor(): void
}

export interface ApsGanttAdapter {
  render(model: ApsGanttModel): void
  locate(taskId: string): boolean
  zoomIn(): void
  zoomOut(): void
  dispose(): void
}

export type ApsGanttAdapterFactory = (
  container: HTMLElement,
  onIntent: ApsAdjustmentIntentHandler
) => ApsGanttAdapter

export const DHTMLX_COMMUNITY_LICENSE = 'mit'

/**
 * 不通过赋值伪装许可证类型；只接受构建产物自身声明为 MIT 的 Community 包。
 */
export function assertDhtmlxCommunityLicense(license: string): void {
  if (license.trim().toLowerCase() !== DHTMLX_COMMUNITY_LICENSE) {
    throw new Error(`DHTMLX Gantt 必须使用 MIT Community 构建，当前为: ${license}`)
  }
}

function createDhtmlxRuntime(): ApsGanttRuntime {
  const instance: GanttStatic = Gantt.getGanttInstance()
  assertDhtmlxCommunityLicense(instance.license)
  instance.config.drag_links = false
  instance.config.drag_progress = false
  instance.config.readonly = false
  instance.ext.zoom.init({
    current: 'day',
    levels: [
      {
        name: 'hour', scale_height: 52, min_column_width: 32,
        scales: [{ unit: 'day', step: 1, format: '%Y-%m-%d' }, { unit: 'hour', step: 1, format: '%H:%i' }]
      },
      {
        name: 'day', scale_height: 52, min_column_width: 60,
        scales: [{ unit: 'month', step: 1, format: '%Y-%m' }, { unit: 'day', step: 1, format: '%m-%d' }]
      },
      {
        name: 'week', scale_height: 52, min_column_width: 70,
        scales: [{ unit: 'month', step: 1, format: '%Y-%m' }, { unit: 'week', step: 1, format: '第 %W 周' }]
      }
    ]
  })

  return {
    init: (container) => instance.init(container),
    parse: (data) => instance.parse(data),
    clearAll: () => instance.clearAll(),
    attachEvent: (name, handler) => {
      if (name !== 'onAfterTaskDrag') {
        throw new Error(`尚未适配 DHTMLX 事件: ${name}`)
      }
      return instance.attachEvent('onAfterTaskDrag', (taskId, mode, event) => {
        handler(taskId, mode, event)
      })
    },
    detachEvent: (eventId) => instance.detachEvent(eventId),
    getTask: (taskId) => instance.getTask(taskId),
    showTask: (taskId) => instance.showTask(taskId),
    zoomIn: () => instance.ext.zoom.zoomIn(),
    zoomOut: () => instance.ext.zoom.zoomOut(),
    destructor: () => instance.destructor()
  }
}

export function createDhtmlxGanttAdapter(
  container: HTMLElement,
  onIntent: ApsAdjustmentIntentHandler,
  runtime: ApsGanttRuntime = createDhtmlxRuntime()
): ApsGanttAdapter {
  let currentModel: ApsGanttModel | undefined
  let disposed = false
  const eventIds: string[] = []

  runtime.init(container)
  eventIds.push(
    runtime.attachEvent('onAfterTaskDrag', (...args: unknown[]) => {
      const [rawTaskId, rawMode] = args
      if (!currentModel || (rawMode !== 'move' && rawMode !== 'resize')) {
        return true
      }

      const taskId = String(rawTaskId)
      const task = runtime.getTask(taskId)
      if (!task.start_date || !task.end_date) {
        return true
      }

      try {
        onIntent({
          source: 'GANTT',
          basePlanVersionId: currentModel.planVersionId,
          baseRevision: currentModel.revision,
          targetType: 'JOB',
          targetId: taskId,
          requestedStartAt: task.start_date.toISOString(),
          requestedEndAt: task.end_date.toISOString()
        })
      } finally {
        // 与 vis-timeline 的 accept(null) 一致：调整只是意图。在服务端返回
        // 新 revision 前，第三方运行时必须恢复当前已确认领域模型的位置。
        if (!disposed && currentModel) {
          const confirmedData = mapApsGanttModel(currentModel)
          runtime.clearAll()
          runtime.parse(confirmedData)
        }
      }
      return true
    })
  )

  return {
    render(model) {
      if (disposed) {
        throw new Error('DHTMLX Gantt 适配器已销毁')
      }
      currentModel = model
      runtime.clearAll()
      runtime.parse(mapApsGanttModel(model))
    },
    locate(taskId) {
      if (disposed || !currentModel?.tasks.some((task) => task.taskId === taskId)) return false
      runtime.showTask(taskId)
      return true
    },
    zoomIn() {
      if (!disposed) runtime.zoomIn()
    },
    zoomOut() {
      if (!disposed) runtime.zoomOut()
    },
    dispose() {
      if (disposed) {
        return
      }
      disposed = true
      for (const eventId of eventIds) {
        runtime.detachEvent(eventId)
      }
      runtime.clearAll()
      runtime.destructor()
      currentModel = undefined
    }
  }
}
