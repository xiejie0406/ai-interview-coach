import type { DataGroup, DataItem } from 'vis-timeline'

import type { ApsTimelineModel } from '@/types/aps/planning'
import {
  assertApsInterval,
  assertUniqueApsIds,
  escapeApsHtml,
  parseApsDate
} from '@/components/aps/shared/mapping'

export type ApsVisGroup = DataGroup & {
  readonly apsResourceId: string
  readonly order: number
  readonly nestedGroups?: string[]
}

export type ApsVisItem = DataItem & {
  readonly id: string
  readonly group: string
  readonly end: Date
  readonly apsTaskId: string
  readonly apsSegmentId: string
  readonly apsPhase: string
}

export interface ApsVisTimelineData {
  readonly groups: ApsVisGroup[]
  readonly items: ApsVisItem[]
}

/** 返回值只属于 vis-timeline 适配层，不得进入 API、store 或领域契约。 */
export function mapApsTimelineModel(model: ApsTimelineModel): ApsVisTimelineData {
  assertUniqueApsIds(
    model.resources.map((resource) => resource.resourceId),
    '资源'
  )
  assertUniqueApsIds(
    model.segments.map((segment) => segment.timelineItemId ?? segment.segmentId),
    '时间线项'
  )

  const resourcesById = new Map(
    model.resources.map((resource) => [resource.resourceId, resource] as const)
  )
  const childIdsByParent = new Map<string, string[]>()

  for (const resource of model.resources) {
    if (!resource.parentResourceId) {
      continue
    }
    if (!resourcesById.has(resource.parentResourceId)) {
      throw new Error(
        `资源 ${resource.resourceId} 的父资源不存在: ${resource.parentResourceId}`
      )
    }
    const children = childIdsByParent.get(resource.parentResourceId) ?? []
    children.push(resource.resourceId)
    childIdsByParent.set(resource.parentResourceId, children)
  }

  const groups = model.resources.map<ApsVisGroup>((resource) => ({
    id: resource.resourceId,
    apsResourceId: resource.resourceId,
    content: escapeApsHtml(resource.label),
    className: `aps-resource aps-resource--${resource.kind.toLowerCase()}`,
    order: resource.sortOrder,
    nestedGroups: childIdsByParent.get(resource.resourceId)
  }))

  const items = model.segments.map<ApsVisItem>((segment) => {
    if (!resourcesById.has(segment.resourceId)) {
      throw new Error(`分段 ${segment.segmentId} 的资源不存在: ${segment.resourceId}`)
    }
    const start = parseApsDate(segment.startAt, `分段 ${segment.segmentId}.startAt`)
    const end = parseApsDate(segment.endAt, `分段 ${segment.segmentId}.endAt`)
    assertApsInterval(start, end, `分段 ${segment.segmentId}`)

    return {
      id: segment.timelineItemId ?? segment.segmentId,
      apsSegmentId: segment.segmentId,
      apsTaskId: segment.taskId,
      apsPhase: segment.phase,
      group: segment.resourceId,
      content: escapeApsHtml(segment.label),
      title: escapeApsHtml(`${segment.label} · ${segment.phase}`),
      start,
      end,
      type: 'range',
      editable: segment.editable,
      className: `aps-segment aps-segment--${segment.phase.toLowerCase()}`
    }
  })

  return { groups, items }
}
