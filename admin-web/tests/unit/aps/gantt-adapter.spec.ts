import type { GanttData, Task } from 'dhtmlx-gantt'
import { describe, expect, it, vi } from 'vitest'

import {
  assertDhtmlxCommunityLicense,
  createDhtmlxGanttAdapter,
  type ApsGanttRuntime
} from '@/components/aps/gantt/adapter'
import type { ApsAdjustmentIntent } from '@/types/aps/planning'
import { createSyntheticApsPlan } from '../../fixtures/aps/synthetic-plan'

class FakeGanttRuntime implements ApsGanttRuntime {
  readonly handlers = new Map<string, (...args: unknown[]) => unknown>()
  readonly tasks = new Map<string, Task>()
  readonly init = vi.fn<(container: HTMLElement) => void>()
  readonly clearAll = vi.fn(() => this.tasks.clear())
  readonly detachEvent = vi.fn<(eventId: string) => void>((eventId) => {
    this.handlers.delete(eventId)
  })
  readonly showTask = vi.fn<(taskId: string | number) => void>()
  readonly zoomIn = vi.fn()
  readonly zoomOut = vi.fn()
  readonly destructor = vi.fn()

  readonly parse = vi.fn((data: GanttData): void => {
    const tasks = 'data' in data && data.data ? data.data : data.tasks
    if (!tasks) {
      throw new Error('Gantt 测试数据缺少任务集合')
    }
    for (const task of tasks) {
      this.tasks.set(String(task.id), task as Task)
    }
  })

  attachEvent(name: string, handler: (...args: unknown[]) => unknown): string {
    const eventId = `event:${name}`
    this.handlers.set(eventId, handler)
    return eventId
  }

  getTask(taskId: string | number): Task {
    const task = this.tasks.get(String(taskId))
    if (!task) {
      throw new Error(`任务不存在: ${taskId}`)
    }
    return task
  }

  fireTaskDrag(taskId: string, mode: 'move' | 'resize' | 'progress'): void {
    this.handlers.get('event:onAfterTaskDrag')?.(taskId, mode)
  }
}

describe('DHTMLX Gantt 适配器', () => {
  it('把拖动转换为纯 APS 调整意图', () => {
    const runtime = new FakeGanttRuntime()
    const intents: ApsAdjustmentIntent[] = []
    const fixture = createSyntheticApsPlan({ resourceCount: 2, segmentCount: 3 })
    const adapter = createDhtmlxGanttAdapter(
      document.createElement('div'),
      (intent) => intents.push(intent),
      runtime
    )

    adapter.render(fixture.gantt)
    const confirmedStartAt = fixture.gantt.tasks[0].startAt
    const confirmedEndAt = fixture.gantt.tasks[0].endAt
    const task = runtime.getTask('TASK-00001')
    task.start_date = new Date('2026-09-15T01:00:00.000Z')
    task.end_date = new Date('2026-09-15T02:00:00.000Z')
    runtime.fireTaskDrag('TASK-00001', 'move')

    expect(intents).toEqual([
      {
        source: 'GANTT',
        basePlanVersionId: 'PLAN-SYNTHETIC-001',
        baseRevision: 1,
        targetType: 'JOB',
        targetId: 'TASK-00001',
        requestedStartAt: '2026-09-15T01:00:00.000Z',
        requestedEndAt: '2026-09-15T02:00:00.000Z'
      }
    ])
    expect(Object.keys(intents[0])).toEqual([
      'source',
      'basePlanVersionId',
      'baseRevision',
      'targetType',
      'targetId',
      'requestedStartAt',
      'requestedEndAt'
    ])
    expect(runtime.parse).toHaveBeenCalledTimes(2)
    expect(runtime.clearAll).toHaveBeenCalledTimes(2)
    expect(runtime.getTask('TASK-00001').start_date?.toISOString()).toBe(
      confirmedStartAt
    )
    expect(runtime.getTask('TASK-00001').end_date?.toISOString()).toBe(
      confirmedEndAt
    )
  })

  it('只允许 MIT Community 构建进入运行时', () => {
    expect(() => assertDhtmlxCommunityLicense('mit')).not.toThrow()
    expect(() => assertDhtmlxCommunityLicense('MIT')).not.toThrow()
    expect(() => assertDhtmlxCommunityLicense('commercial')).toThrow(
      '必须使用 MIT Community 构建'
    )
  })

  it('幂等清理事件和 DHTMLX 实例', () => {
    const runtime = new FakeGanttRuntime()
    const fixture = createSyntheticApsPlan({ resourceCount: 1, segmentCount: 1 })
    const adapter = createDhtmlxGanttAdapter(
      document.createElement('div'),
      vi.fn(),
      runtime
    )

    adapter.render(fixture.gantt)
    adapter.dispose()
    adapter.dispose()

    expect(runtime.detachEvent).toHaveBeenCalledTimes(1)
    expect(runtime.destructor).toHaveBeenCalledTimes(1)
    expect(() => adapter.render(fixture.gantt)).toThrow('已销毁')
  })

  it('按稳定作业 ID 定位并控制缩放', () => {
    const runtime = new FakeGanttRuntime()
    const fixture = createSyntheticApsPlan({ resourceCount: 1, segmentCount: 1 })
    const adapter = createDhtmlxGanttAdapter(document.createElement('div'), vi.fn(), runtime)
    adapter.render(fixture.gantt)

    expect(adapter.locate('TASK-00001')).toBe(true)
    expect(adapter.locate('missing')).toBe(false)
    adapter.zoomIn()
    adapter.zoomOut()

    expect(runtime.showTask).toHaveBeenCalledWith('TASK-00001')
    expect(runtime.zoomIn).toHaveBeenCalledOnce()
    expect(runtime.zoomOut).toHaveBeenCalledOnce()
  })
})
