import { describe, expect, it } from 'vitest'

import { mapApsGanttModel } from '@/components/aps/gantt/mapping'
import { mapApsTimelineModel } from '@/components/aps/timeline/mapping'
import { createSyntheticApsPlan } from '../../fixtures/aps/synthetic-plan'

describe('APS 甘特纯映射', () => {
  it('稳定映射任务和依赖 ID，且不把业务文本当作 HTML', () => {
    const fixture = createSyntheticApsPlan({ resourceCount: 2, segmentCount: 3 })
    const model = {
      ...fixture.gantt,
      tasks: [
        {
          ...fixture.gantt.tasks[0],
          label: '<img src=x onerror=alert(1)>'
        },
        ...fixture.gantt.tasks.slice(1)
      ]
    }

    const first = mapApsGanttModel(model)
    const second = mapApsGanttModel(model)

    expect(first.data.map((task) => task.id)).toEqual(
      model.tasks.map((task) => task.taskId)
    )
    expect(first.links.map((link) => link.id)).toEqual(
      model.dependencies.map((dependency) => dependency.dependencyId)
    )
    expect(first.data.map((task) => task.id)).toEqual(
      second.data.map((task) => task.id)
    )
    expect(first.data[0].text).toBe('&lt;img src=x onerror=alert(1)&gt;')
  })

  it('映射 500 个资源行和 5000 个可见分段并保持稳定 ID', () => {
    const fixture = createSyntheticApsPlan()

    const first = mapApsTimelineModel(fixture.timeline)
    const second = mapApsTimelineModel(fixture.timeline)

    expect(first.groups).toHaveLength(500)
    expect(first.items).toHaveLength(5_000)
    expect(new Set(first.groups.map((group) => group.id)).size).toBe(500)
    expect(new Set(first.items.map((item) => item.id)).size).toBe(5_000)
    expect(first.groups.map((group) => group.id)).toEqual(
      second.groups.map((group) => group.id)
    )
    expect(first.items.map((item) => item.id)).toEqual(
      second.items.map((item) => item.id)
    )
    expect(first.items.every((item) => item.apsSegmentId === item.id)).toBe(true)
  })

  it('拒绝重复 ID、悬空资源和非法时间区间', () => {
    const fixture = createSyntheticApsPlan({ resourceCount: 1, segmentCount: 2 })

    expect(() =>
      mapApsTimelineModel({
        ...fixture.timeline,
        segments: [fixture.timeline.segments[0], fixture.timeline.segments[0]]
      })
    ).toThrow('时间线项 ID 重复')

    expect(() =>
      mapApsTimelineModel({
        ...fixture.timeline,
        segments: [
          {
            ...fixture.timeline.segments[0],
            resourceId: 'RES-MISSING'
          }
        ]
      })
    ).toThrow('资源不存在')

    expect(() =>
      mapApsGanttModel({
        ...fixture.gantt,
        tasks: [
          {
            ...fixture.gantt.tasks[0],
            endAt: fixture.gantt.tasks[0].startAt
          }
        ],
        dependencies: []
      })
    ).toThrow('结束时间必须晚于开始时间')
  })
})
