import {
  Timeline,
  type DataGroup,
  type DataItem,
  type TimelineItem,
  type TimelineOptions
} from 'vis-timeline/standalone'

import type {
  ApsAdjustmentIntentHandler,
  ApsTimelineModel,
  ApsTimelineSegment
} from '@/types/aps/planning'
import { mapApsTimelineModel } from '@/components/aps/timeline/mapping'

export interface ApsTimelineRuntime {
  setGroups(groups: DataGroup[]): void
  setItems(items: DataItem[]): void
  setOptions(options: TimelineOptions): void
  focus(itemId: string): void
  zoomIn(percentage: number): void
  zoomOut(percentage: number): void
  destroy(): void
}

export type ApsTimelineRuntimeFactory = (
  container: HTMLElement,
  options: TimelineOptions
) => ApsTimelineRuntime

export interface ApsTimelineAdapter {
  render(model: ApsTimelineModel): void
  locate(targetId: string): boolean
  zoomIn(): void
  zoomOut(): void
  dispose(): void
}

export type ApsTimelineAdapterFactory = (
  container: HTMLElement,
  onIntent: ApsAdjustmentIntentHandler
) => ApsTimelineAdapter

function createVisRuntime(
  container: HTMLElement,
  options: TimelineOptions
): ApsTimelineRuntime {
  const timeline = new Timeline(container, [], [], options)
  return {
    setGroups: (groups) => timeline.setGroups(groups),
    setItems: (items) => timeline.setItems(items),
    setOptions: (nextOptions) => timeline.setOptions(nextOptions),
    focus: (itemId) => timeline.focus(itemId, { animation: { duration: 250, easingFunction: 'easeInOutQuad' } }),
    zoomIn: (percentage) => timeline.zoomIn(percentage, { animation: false }),
    zoomOut: (percentage) => timeline.zoomOut(percentage, { animation: false }),
    destroy: () => timeline.destroy()
  }
}

function toIsoDate(value: TimelineItem['start'] | TimelineItem['end']): string | undefined {
  if (value === undefined || value === null) {
    return undefined
  }
  const date = value instanceof Date ? value : new Date(value as string | number)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}

export function createVisTimelineAdapter(
  container: HTMLElement,
  onIntent: ApsAdjustmentIntentHandler,
  runtimeFactory: ApsTimelineRuntimeFactory = createVisRuntime
): ApsTimelineAdapter {
  let currentModel: ApsTimelineModel | undefined
  let segmentById = new Map<string, ApsTimelineSegment>()
  let disposed = false

  const options: TimelineOptions = {
    autoResize: true,
    stack: false,
    horizontalScroll: true,
    verticalScroll: true,
    groupOrder: 'order',
    editable: {
      add: false,
      remove: false,
      updateGroup: true,
      updateTime: true,
      overrideItems: false
    },
    onMove(item, accept) {
      try {
        if (!currentModel || item.id === undefined || item.id === null) {
          return
        }
        const timelineItemId = String(item.id)
        const original = segmentById.get(timelineItemId)
        const requestedStartAt = toIsoDate(item.start)
        const requestedEndAt = toIsoDate(item.end)
        if (!original || !requestedStartAt || !requestedEndAt) {
          return
        }

        onIntent({
          source: 'TIMELINE',
          basePlanVersionId: currentModel.planVersionId,
          baseRevision: currentModel.revision,
          targetType: original.allocationId ? 'ALLOCATION' : 'SEGMENT',
          targetId: original.allocationId ?? original.segmentId,
          requestedStartAt,
          requestedEndAt,
          requestedResourceId:
            item.group === undefined || item.group === null
              ? original.resourceId
              : String(item.group)
        })
      } finally {
        // 调整只是一条意图；在服务端返回新版本前恢复已确认位置。
        accept(null)
      }
    }
  }

  const runtime = runtimeFactory(container, options)
  runtime.setOptions(options)

  return {
    render(model) {
      if (disposed) {
        throw new Error('vis-timeline 适配器已销毁')
      }
      currentModel = model
      segmentById = new Map(
        model.segments.map((segment) => [segment.timelineItemId ?? segment.segmentId, segment] as const)
      )
      const data = mapApsTimelineModel(model)
      runtime.setGroups(data.groups)
      runtime.setItems(data.items)
    },
    locate(targetId) {
      if (disposed) return false
      const entry = [...segmentById.entries()].find(([itemId, segment]) =>
        itemId === targetId || segment.segmentId === targetId || segment.taskId === targetId)
      if (!entry) return false
      runtime.focus(entry[0])
      return true
    },
    zoomIn() {
      if (!disposed) runtime.zoomIn(0.35)
    },
    zoomOut() {
      if (!disposed) runtime.zoomOut(0.35)
    },
    dispose() {
      if (disposed) {
        return
      }
      disposed = true
      currentModel = undefined
      segmentById.clear()
      runtime.destroy()
    }
  }
}
