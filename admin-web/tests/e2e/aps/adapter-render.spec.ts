import { expect, test } from '@playwright/test'

test('DHTMLX 与 vis 适配器可以真实渲染并清理', async ({ page }) => {
  const consoleErrors: string[] = []
  const pageErrors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors.push(message.text())
    }
  })
  page.on('pageerror', (error) => pageErrors.push(error.message))

  await page.goto('/tests/e2e/aps/fixtures/index.html')
  await page.waitForFunction(() => window.__APS_ADAPTER_POC__?.ready === true)

  const firstGanttTask = page.locator('#gantt .gantt_task_content').first()
  const firstTimelineItem = page.locator('#timeline .vis-item').first()
  const firstTimelineLabel = page.locator('#timeline .vis-label .vis-inner').first()
  await expect(firstGanttTask).toBeVisible()
  await expect(firstGanttTask).toContainText('合成任务 1')
  await expect(firstTimelineItem).toBeVisible()
  await expect(firstTimelineItem).toContainText('运行段 1')
  await expect(firstTimelineLabel).toBeVisible()
  await expect(firstTimelineLabel).toContainText('合成资源 1')
  await expect
    .poll(() =>
      page.evaluate(() => ({
        resources: window.__APS_ADAPTER_POC__?.resourceCount,
        segments: window.__APS_ADAPTER_POC__?.segmentCount,
        ganttRenderDurationMs:
          window.__APS_ADAPTER_POC__?.ganttRenderDurationMs,
        timelineRenderDurationMs:
          window.__APS_ADAPTER_POC__?.timelineRenderDurationMs,
        readyDurationMs: window.__APS_ADAPTER_POC__?.readyDurationMs
      }))
    )
    .toMatchObject({ resources: 500, segments: 5_000 })

  const metrics = await page.evaluate(() => window.__APS_ADAPTER_POC__)
  expect(metrics?.ganttRenderDurationMs).toBeGreaterThan(0)
  expect(metrics?.timelineRenderDurationMs).toBeGreaterThan(0)
  expect(metrics?.readyDurationMs).toBeGreaterThan(0)
  console.log(
    `APS 500/5000 浏览器渲染：Gantt=${metrics?.ganttRenderDurationMs.toFixed(1)}ms, ` +
      `Timeline=${metrics?.timelineRenderDurationMs.toFixed(1)}ms, ` +
      `首屏 ready=${metrics?.readyDurationMs.toFixed(1)}ms`
  )

  await page.evaluate(() => window.__APS_ADAPTER_POC__?.dispose())
  await expect(page.locator('#gantt')).toBeEmpty()
  await expect(page.locator('#timeline')).toBeEmpty()

  await page.evaluate(() => window.__APS_ADAPTER_POC__?.recreate())
  await expect(firstGanttTask).toBeVisible()
  await expect(firstGanttTask).toContainText('合成任务 1')
  await expect(firstTimelineItem).toBeVisible()
  await expect(firstTimelineItem).toContainText('运行段 1')
  await expect(firstTimelineLabel).toBeVisible()
  await expect(firstTimelineLabel).toContainText('合成资源 1')

  await page.evaluate(() => window.__APS_ADAPTER_POC__?.dispose())
  await expect(page.locator('#gantt')).toBeEmpty()
  await expect(page.locator('#timeline')).toBeEmpty()
  expect(consoleErrors).toEqual([])
  expect(pageErrors).toEqual([])
})
