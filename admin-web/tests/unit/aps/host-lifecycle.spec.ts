import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import ApsGanttHost from '@/components/aps/gantt/ApsGanttHost.vue'
import type {
  ApsGanttAdapter,
  ApsGanttAdapterFactory
} from '@/components/aps/gantt/adapter'
import ApsTimelineHost from '@/components/aps/timeline/ApsTimelineHost.vue'
import type {
  ApsTimelineAdapter,
  ApsTimelineAdapterFactory
} from '@/components/aps/timeline/adapter'
import type { ApsAdjustmentIntentHandler } from '@/types/aps/planning'
import { createSyntheticApsPlan } from '../../fixtures/aps/synthetic-plan'

describe('APS 可视化宿主生命周期', () => {
  it('Gantt 宿主转发意图并在卸载时清理适配器', async () => {
    const fixture = createSyntheticApsPlan({ resourceCount: 1, segmentCount: 1 })
    const render = vi.fn<ApsGanttAdapter['render']>()
    const locate = vi.fn<ApsGanttAdapter['locate']>(() => true)
    const zoomIn = vi.fn<ApsGanttAdapter['zoomIn']>()
    const zoomOut = vi.fn<ApsGanttAdapter['zoomOut']>()
    const dispose = vi.fn<ApsGanttAdapter['dispose']>()
    let handler: ApsAdjustmentIntentHandler | undefined
    const factory: ApsGanttAdapterFactory = (_container, onIntent) => {
      handler = onIntent
      return { render, locate, zoomIn, zoomOut, dispose }
    }

    const wrapper = mount(ApsGanttHost, {
      props: { model: fixture.gantt, adapterFactory: factory }
    })
    handler?.({
      source: 'GANTT',
      basePlanVersionId: fixture.gantt.planVersionId,
      baseRevision: 1,
      targetType: 'JOB',
      targetId: 'TASK-00001',
      requestedStartAt: fixture.gantt.tasks[0].startAt,
      requestedEndAt: fixture.gantt.tasks[0].endAt
    })
    await wrapper.vm.$nextTick()

    expect(render).toHaveBeenCalledWith(fixture.gantt)
    expect(wrapper.vm.locate('TASK-00001')).toBe(true)
    wrapper.vm.zoomIn()
    wrapper.vm.zoomOut()
    expect(zoomIn).toHaveBeenCalledOnce()
    expect(zoomOut).toHaveBeenCalledOnce()
    expect(wrapper.emitted('intent')).toHaveLength(1)
    wrapper.unmount()
    expect(dispose).toHaveBeenCalledTimes(1)
  })

  it('Timeline 宿主在卸载时清理适配器', () => {
    const fixture = createSyntheticApsPlan({ resourceCount: 1, segmentCount: 1 })
    const render = vi.fn<ApsTimelineAdapter['render']>()
    const locate = vi.fn<ApsTimelineAdapter['locate']>(() => true)
    const zoomIn = vi.fn<ApsTimelineAdapter['zoomIn']>()
    const zoomOut = vi.fn<ApsTimelineAdapter['zoomOut']>()
    const dispose = vi.fn<ApsTimelineAdapter['dispose']>()
    const factory: ApsTimelineAdapterFactory = () => ({ render, locate, zoomIn, zoomOut, dispose })

    const wrapper = mount(ApsTimelineHost, {
      props: { model: fixture.timeline, adapterFactory: factory }
    })

    expect(render).toHaveBeenCalledWith(fixture.timeline)
    expect(wrapper.vm.locate('TASK-00001')).toBe(true)
    wrapper.unmount()
    expect(dispose).toHaveBeenCalledTimes(1)
  })
})
