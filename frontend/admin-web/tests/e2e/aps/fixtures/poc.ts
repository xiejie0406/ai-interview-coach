import 'dhtmlx-gantt/codebase/dhtmlxgantt.css'
import 'vis-timeline/styles/vis-timeline-graph2d.min.css'

import { createDhtmlxGanttAdapter } from '@/components/aps/gantt/adapter'
import { createVisTimelineAdapter } from '@/components/aps/timeline/adapter'
import { createSyntheticApsPlan } from '../../../fixtures/aps/synthetic-plan'

declare global {
  interface Window {
    __APS_ADAPTER_POC__?: {
      readonly ready: boolean
      readonly resourceCount: number
      readonly segmentCount: number
      readonly ganttRenderDurationMs: number
      readonly timelineRenderDurationMs: number
      readonly readyDurationMs: number
      dispose(): void
      recreate(): void
    }
  }
}

function requireHost(selector: string): HTMLElement {
  const host = document.querySelector<HTMLElement>(selector)
  if (!host) {
    throw new Error(`APS PoC 容器不存在: ${selector}`)
  }
  return host
}

const ganttHost = requireHost('#gantt')
const timelineHost = requireHost('#timeline')
const fixture = createSyntheticApsPlan({ resourceCount: 500, segmentCount: 5_000 })
let gantt: ReturnType<typeof createDhtmlxGanttAdapter> | undefined
let timeline: ReturnType<typeof createVisTimelineAdapter> | undefined

function createAndRenderAdapters(): void {
  gantt = createDhtmlxGanttAdapter(ganttHost, () => undefined)
  timeline = createVisTimelineAdapter(timelineHost, () => undefined)
  gantt.render(fixture.gantt)
  timeline.render(fixture.timeline)
}

function disposeAdapters(): void {
  gantt?.dispose()
  timeline?.dispose()
  gantt = undefined
  timeline = undefined
}

const renderStartedAt = performance.now()
gantt = createDhtmlxGanttAdapter(ganttHost, () => undefined)
timeline = createVisTimelineAdapter(timelineHost, () => undefined)
gantt.render(fixture.gantt)
const ganttRenderedAt = performance.now()
timeline.render(fixture.timeline)
const timelineRenderedAt = performance.now()

// 两帧后再声明 ready，覆盖同步适配、浏览器布局和首屏绘制。
await new Promise<void>((resolve) => {
  requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
})
const readyAt = performance.now()

window.__APS_ADAPTER_POC__ = {
  ready: true,
  resourceCount: fixture.timeline.resources.length,
  segmentCount: fixture.timeline.segments.length,
  ganttRenderDurationMs: ganttRenderedAt - renderStartedAt,
  timelineRenderDurationMs: timelineRenderedAt - ganttRenderedAt,
  readyDurationMs: readyAt - renderStartedAt,
  dispose() {
    disposeAdapters()
  },
  recreate() {
    disposeAdapters()
    createAndRenderAdapters()
  }
}
