import type {
  ApsGanttDependency,
  ApsGanttModel,
  ApsGanttTask,
  ApsResourceRow,
  ApsTimelineModel,
  ApsTimelineSegment
} from '@/types/aps/planning'

export interface SyntheticApsPlan {
  readonly gantt: ApsGanttModel
  readonly timeline: ApsTimelineModel
}

export interface SyntheticApsPlanOptions {
  readonly resourceCount?: number
  readonly segmentCount?: number
}

const HOUR = 60 * 60 * 1000

export function createSyntheticApsPlan(
  options: SyntheticApsPlanOptions = {}
): SyntheticApsPlan {
  const resourceCount = options.resourceCount ?? 500
  const segmentCount = options.segmentCount ?? 5_000
  if (!Number.isInteger(resourceCount) || resourceCount <= 0) {
    throw new Error('resourceCount 必须是正整数')
  }
  if (!Number.isInteger(segmentCount) || segmentCount <= 0) {
    throw new Error('segmentCount 必须是正整数')
  }

  const base = Date.parse('2026-09-14T00:00:00.000Z')
  const pad = (value: number, width: number) => String(value).padStart(width, '0')

  const resources: ApsResourceRow[] = Array.from(
    { length: resourceCount },
    (_, index) => ({
      resourceId: `RES-${pad(index + 1, 4)}`,
      label: `合成资源 ${index + 1}`,
      kind: index % 2 === 0 ? 'PERSON' : 'MACHINE',
      sortOrder: index
    })
  )

  const tasks: ApsGanttTask[] = []
  const segments: ApsTimelineSegment[] = []
  for (let index = 0; index < segmentCount; index += 1) {
    const resourceIndex = index % resourceCount
    const slot = Math.floor(index / resourceCount)
    const start = new Date(base + slot * 2 * HOUR + (resourceIndex % 4) * 15 * 60 * 1000)
    const end = new Date(start.getTime() + HOUR)
    const taskId = `TASK-${pad(index + 1, 5)}`
    const segmentId = `SEG-${pad(index + 1, 5)}`

    tasks.push({
      taskId,
      label: `合成任务 ${index + 1}`,
      startAt: start.toISOString(),
      endAt: end.toISOString(),
      progressRatio: (index % 10) / 10
    })
    segments.push({
      segmentId,
      taskId,
      resourceId: resources[resourceIndex].resourceId,
      label: `运行段 ${index + 1}`,
      startAt: start.toISOString(),
      endAt: end.toISOString(),
      phase: 'RUN',
      editable: true
    })
  }

  const dependencies: ApsGanttDependency[] = tasks.slice(1).map((task, index) => ({
    dependencyId: `DEP-${pad(index + 1, 5)}`,
    predecessorTaskId: tasks[index].taskId,
    successorTaskId: task.taskId,
    kind: 'FINISH'
  }))

  const identity = {
    planVersionId: 'PLAN-SYNTHETIC-001',
    revision: 1
  } as const

  return {
    gantt: { ...identity, tasks, dependencies },
    timeline: { ...identity, resources, segments }
  }
}
