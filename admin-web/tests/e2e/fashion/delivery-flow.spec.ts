import { expect, test, type Page, type Route } from '@playwright/test'

const quoteId = '970040'

async function installMock(page: Page) {
  let taskCreated = false
  let workspaceReadsAfterCreate = 0
  const writes: string[] = []
  await page.route('**/dev-api/**', async (route: Route) => {
    const request = route.request(), method = request.method()
    const path = new URL(request.url()).pathname.replace('/dev-api', '')
    const ok = (data: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) })
    if (path === '/captchaImage') return ok({ code: 200, captchaEnabled: false })
    if (path === '/login' && method === 'POST') return ok({ code: 200, token: 'delivery-token' })
    if (path === '/getInfo') return ok({ code: 200,
      user: { userId: 1, userName: 'operator', nickName: '交付运营', avatar: '' }, roles: ['fashion_operator'],
      permissions: ['fashion:quote:query', 'fashion:quote:export', 'fashion:operations:query', 'fashion:operations:retention'],
      isDefaultModifyPwd: false, isPasswordExpired: false, pwdChrtype: 0 })
    if (path === '/getRouters') return ok({ code: 200, data: [{ name: 'Fashion', path: '/fashion', component: 'Layout',
      redirect: 'noRedirect', alwaysShow: true, meta: { title: '智能选品' }, children: [
        { name: 'FashionDelivery', path: 'delivery', component: 'fashion/delivery/index', meta: { title: '交付中心' } },
        { name: 'FashionOperations', path: 'operations', component: 'fashion/operations/index', meta: { title: '运行与保留' } }
      ] }] })
    if (path === `/fashion/quotes/${quoteId}/files` && method === 'POST') {
      taskCreated = true; writes.push(`${method} ${path}`)
      return ok({ code: 200, data: task('queued', false) })
    }
    if (path === `/fashion/quotes/${quoteId}/files` && method === 'GET') {
      if (taskCreated) workspaceReadsAfterCreate++
      const status = !taskCreated ? undefined : workspaceReadsAfterCreate === 1 ? 'running' : 'success'
      return ok({ code: 200, data: { quoteId, quoteNo: 'Q-IMP08', versionNo: 1,
        quoteTitle: '四品类正式报价', quoteHash: '8'.repeat(64), status: 'confirmed',
        files: status ? [task(status, status === 'success')] : [] } })
    }
    if (path === `/fashion/quotes/${quoteId}/files/990001/artifacts/1` && method === 'GET') {
      writes.push(`${method} ${path}`)
      return route.fulfill({ status: 200,
        contentType: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
        headers: { 'content-disposition': 'attachment; filename="quote.pptx"' },
        body: 'PK\u0003\u0004pptx-test' })
    }
    if (path === '/fashion/operations/overview') return ok({ code: 200, data: {
      generatedAt: '2026-09-13T00:00:00Z',
      importFailureRate: metric('import_failure_rate', '5.00', '%', '24h'),
      imageFailedOrUnknown: metric('image_failed_or_unknown', '2', 'count', 'current'),
      expiredStock: metric('stock_expired', '3', 'count', '24h'),
      deliveryFailureRate: metric('delivery_failure_rate', '0.00', '%', '24h'),
      deliveryAverageDurationMs: metric('delivery_average_duration', '1350.00', 'ms', '24h'),
      alertCandidates: [{ code: 'delivery_failure_rate', thresholdReached: false, currentValue: '0.00', thresholdValue: '10.00', description: '24 小时文件失败率达到阈值' }],
      exceptions: [], notificationStatus: 'not_configured（仅形成告警候选，未声称通知已送达）' } })
    if (path === '/fashion/operations/retention/dry-run') return ok({ code: 200, data: {
      generatedAt: '2026-09-13T00:00:00Z', deletionEnabled: false, quoteDays: 365,
      importFileDays: 90, failedImageDays: 30, candidates: [{ category: 'quote_file', recordId: '990001',
        objectKey: 'delivery/quote.pptx', createdAt: '2025-01-01T00:00:00Z', eligibleAt: '2026-01-01T00:00:00Z',
        decision: 'protected', reason: '报价仍在有效期内', quoteId }] } })
    return route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ code: 404, msg: `${method} ${path}` }) })
  })
  return writes
}

function metric(code: string, value: string, unit: string, window: string) { return { code, value, unit, window } }
function task(status: string, withFile: boolean) { return { id: '990001', fileType: 'pptx', purpose: 'customer',
  rendererVersion: 'fashion-delivery-1.0', requestKey: 'a'.repeat(64), status,
  files: withFile ? [{ fileName: '四品类正式报价_Q-IMP08_V1_客户报价.pptx', objectKey: 'delivery/quote.pptx',
    contentType: 'application/vnd.openxmlformats-officedocument.presentationml.presentation', sha256: 'b'.repeat(64),
    byteSize: 24576, pageCount: 8, role: 'customer-presentation', skuCodes: ['SKU-1'], sourceMode: 'frozen-quote', reviewStatus: 'confirmed' }] : [],
  retryCount: 0, downloadCount: 0, createTime: '2026-09-13T00:00:00Z', rowVersion: status === 'queued' ? 1 : status === 'running' ? 2 : 3 } }

test('已确认报价生成、恢复、下载、窄屏与保留 dry-run 形成 Java 单入口交付流程', async ({ page }) => {
  const writes = await installMock(page)
  const foreign: string[] = []
  page.on('request', request => { if (!['http://127.0.0.1:4175'].includes(new URL(request.url()).origin)) foreign.push(request.url()) })
  await page.goto('/login'); await page.getByRole('button', { name: '登 录' }).click()
  await page.goto(`/fashion/delivery?quoteId=${quoteId}`)
  await expect(page.getByText('Q-IMP08 / V1')).toBeVisible()
  await page.getByRole('button', { name: /可编辑 PPTX/ }).focus()
  await expect(page.getByRole('button', { name: /可编辑 PPTX/ })).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.getByText('生成中')).toBeVisible()
  await page.getByRole('button', { name: '刷新' }).click()
  await expect(page.getByText('8 页')).toBeVisible()
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载' }).click()
  expect((await download).suggestedFilename()).toContain('客户报价.pptx')

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByText('生成客户交付文件')).toBeVisible()
  await expect(page.getByRole('button', { name: /图片 ZIP/ })).toBeVisible()

  await page.goto('/fashion/operations')
  await expect(page.getByText('PPT/文件耗时')).toBeVisible()
  await page.getByRole('button', { name: '计算待清理清单' }).click()
  await expect(page.getByText('真实删除：关闭')).toBeVisible()
  await expect(page.getByText('引用保护')).toBeVisible()
  expect(writes).toEqual([`POST /fashion/quotes/${quoteId}/files`, `GET /fashion/quotes/${quoteId}/files/990001/artifacts/1`])
  expect(foreign).toEqual([])
  await page.screenshot({ path: '../output/playwright/fashion-production/delivery-operations.png', fullPage: true })
})
