import type { GanttData, Link, Task } from 'dhtmlx-gantt'

import type { ApsGanttModel } from '@/types/aps/planning'
import {
  assertApsInterval,
  assertUniqueApsIds,
  escapeApsHtml,
  parseApsDate
} from '@/components/aps/shared/mapping'

export type ApsDhtmlxTask = Task & {
  readonly apsTaskId: string
}

export type ApsDhtmlxLink = Link & {
  readonly apsDependencyKind: 'FINISH' | 'QUANTITY' | 'TRANSFER'
}

export interface ApsDhtmlxGanttData {
  data: ApsDhtmlxTask[]
  links: ApsDhtmlxLink[]
}

/**
 * 将 APS 领域模型转换成 DHTMLX 私有视图模型。反向事件只能生成
 * ApsAdjustmentIntent，禁止把本返回值当作 API 或 store 契约。
 */
export function mapApsGanttModel(model: ApsGanttModel): ApsDhtmlxGanttData {
  assertUniqueApsIds(
    model.tasks.map((task) => task.taskId),
    '任务'
  )
  assertUniqueApsIds(
    model.dependencies.map((dependency) => dependency.dependencyId),
    '依赖'
  )

  const taskIds = new Set(model.tasks.map((task) => task.taskId))
  const data = model.tasks.map<ApsDhtmlxTask>((task) => {
    if (task.parentTaskId && !taskIds.has(task.parentTaskId)) {
      throw new Error(`任务 ${task.taskId} 的父任务不存在: ${task.parentTaskId}`)
    }
    if (task.progressRatio < 0 || task.progressRatio > 1) {
      throw new Error(`任务 ${task.taskId} 的进度必须在 0 到 1 之间`)
    }

    const start = parseApsDate(task.startAt, `任务 ${task.taskId}.startAt`)
    const end = parseApsDate(task.endAt, `任务 ${task.taskId}.endAt`)
    assertApsInterval(start, end, `任务 ${task.taskId}`)

    return {
      id: task.taskId,
      apsTaskId: task.taskId,
      text: escapeApsHtml(task.label),
      start_date: start,
      end_date: end,
      progress: task.progressRatio,
      parent: task.parentTaskId,
      open: task.expanded ?? true,
      readonly: false
    }
  })

  const links = model.dependencies.map<ApsDhtmlxLink>((dependency) => {
    if (!taskIds.has(dependency.predecessorTaskId)) {
      throw new Error(`依赖 ${dependency.dependencyId} 的前置任务不存在`)
    }
    if (!taskIds.has(dependency.successorTaskId)) {
      throw new Error(`依赖 ${dependency.dependencyId} 的后置任务不存在`)
    }

    return {
      id: dependency.dependencyId,
      source: dependency.predecessorTaskId,
      target: dependency.successorTaskId,
      // 当前三个领域关系在图上都只表达“前置到后置”的方向；
      // 数量语义仍由领域详情和后端校验解释，不能由 DHTMLX link type 推断。
      type: '0',
      readonly: true,
      apsDependencyKind: dependency.kind
    }
  })

  return { data, links }
}
