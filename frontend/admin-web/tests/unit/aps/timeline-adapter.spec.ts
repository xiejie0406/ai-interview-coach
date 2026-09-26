import type {
  DataGroup,
  DataItem,
  TimelineItem,
  TimelineOptions
} from 'vis-timeline/standalone'
import { describe, expect, it, vi } from 'vitest'

import {
  createVisTimelineAdapter,
  type ApsTimelineRuntime
} from '@/components/aps/timeline/adapter'
import type { ApsAdjustmentIntent } from '@/types/aps/planning'
import { createSyntheticApsPlan } from '../../fixtures/aps/synthetic-plan'

class FakeTimelineRuntime implements ApsTimelineRuntime {
  groups: DataGroup[] = []
  items: DataItem[] = []
  options: TimelineOptions = {}
  readonly focus = vi.fn<(itemId: string) => void>()
  readonly zoomIn = vi.fn<(percentage: number) => void>()
  readonly zoomOut = vi.fn<(percentage: number) => void>()
  readonly destroy = vi.fn()

  setGroups(groups: DataGroup[]): void {
    this.groups = groups
  }

  setItems(items: DataItem[]): void {
    this.items = items
  }

  setOptions(options: TimelineOptions): void {
    this.options = options
  }

  move(item: TimelineItem, accept: (item: TimelineItem | null) => void): void {
    this.options.onMove?.(item, accept)
  }
}

describe('vis-timeline 适配器', () => {
  it('拒绝在前端直接提交拖动结果并发出 APS 调整意图', () => {
    const runtime = new FakeTimelineRuntime()
    const intents: ApsAdjustmentIntent[] = []
    const fixture = createSyntheticApsPlan({ resourceCount: 2, segmentCount: 2 })
    const adapter = createVisTimelineAdapter(
      document.createElement('div'),
      (intent) => intents.push(intent),
      () => runtime
    )
    const accept = vi.fn<(item: TimelineItem | null) => void>()

    adapter.render(fixture.timeline)
    runtime.move(
      {
        ...runtime.items[0],
        id: 'SEG-00001',
        group: 'RES-0002',
        start: new Date('2026-09-15T03:00:00.000Z'),
        end: new Date('2026-09-15T04:00:00.000Z')
      } as TimelineItem,
      accept
    )

    expect(accept).toHaveBeenCalledWith(null)
    expect(intents).toEqual([
      {
        source: 'TIMELINE',
        basePlanVersionId: 'PLAN-SYNTHETIC-001',
        baseRevision: 1,
        targetType: 'SEGMENT',
        targetId: 'SEG-00001',
        requestedStartAt: '2026-09-15T03:00:00.000Z',
        requestedEndAt: '2026-09-15T04:00:00.000Z',
        requestedResourceId: 'RES-0002'
      }
    ])
    expect(intents[0]).not.toHaveProperty('content')
    expect(intents[0]).not.toHaveProperty('group')
  })

  it('幂等销毁 timeline 实例', () => {
    const runtime = new FakeTimelineRuntime()
    const fixture = createSyntheticApsPlan({ resourceCount: 1, segmentCount: 1 })
    const adapter = createVisTimelineAdapter(
      document.createElement('div'),
      vi.fn(),
      () => runtime
    )

    adapter.render(fixture.timeline)
    adapter.dispose()
    adapter.dispose()

    expect(runtime.destroy).toHaveBeenCalledTimes(1)
    expect(() => adapter.render(fixture.timeline)).toThrow('已销毁')
  })

  it('可按作业身份定位资源占用并控制缩放', () => {
    const runtime = new FakeTimelineRuntime()
    const fixture = createSyntheticApsPlan({ resourceCount: 1, segmentCount: 1 })
    const adapter = createVisTimelineAdapter(document.createElement('div'), vi.fn(), () => runtime)
    adapter.render(fixture.timeline)

    expect(adapter.locate('TASK-00001')).toBe(true)
    expect(adapter.locate('missing')).toBe(false)
    adapter.zoomIn()
    adapter.zoomOut()

    expect(runtime.focus).toHaveBeenCalledWith('SEG-00001')
    expect(runtime.zoomIn).toHaveBeenCalledWith(0.35)
    expect(runtime.zoomOut).toHaveBeenCalledWith(0.35)
  })
})
