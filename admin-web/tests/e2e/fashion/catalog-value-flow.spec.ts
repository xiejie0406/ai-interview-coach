import { expect, test, type Page, type Route } from '@playwright/test'

const dictionaries: Record<string, { dictLabel: string; dictValue: string }[]> = {
  fashion_product_source: [{ dictLabel: '人工维护', dictValue: 'MANUAL' }],
  fashion_product_category: [{ dictLabel: '上衣', dictValue: 'TOP' }],
  fashion_warehouse: [{ dictLabel: '主仓', dictValue: 'MAIN' }]
}

function detail(sku: string, value: Record<string, unknown>, status = 'valid') {
  return {
    id: `95${sku}`, detailNo: Number(sku.slice(-1)), sourceRowNo: Number(sku.slice(-1)) + 1,
    rowType: 'input', businessKey: `MANUAL:${sku}`,
    beforeData: { salePrice: '100.00', currency: 'CNY', taxMode: 'included', asOf: '2026-09-12T00:00:00Z' },
    normalizedData: value, status, errors: [], changeType: 'changed'
  }
}

function batch(overrides: Record<string, unknown> = {}) {
  return {
    batchId: '940001', batchNo: 'PRICE-940001', importType: 'price', operationType: 'import',
    status: 'validated', sourceCode: 'MANUAL', fileName: 'price-v1.csv', fileHash: 'a'.repeat(64),
    scope: { sourceCode: 'MANUAL', mode: 'strict_full' }, scopeHash: 'b'.repeat(64),
    baseDataHash: 'c'.repeat(64), asOf: new Date().toISOString(), expectedCount: 2, actualCount: 2,
    errorCount: 0, rowVersion: 1,
    details: [
      detail('000201', { salePrice: '0.00', currency: 'CNY', taxMode: 'included', asOf: new Date().toISOString() }),
      detail('000202', { salePrice: '120.00', currency: 'CNY', taxMode: 'included', asOf: new Date().toISOString() })
    ],
    ...overrides
  }
}

async function installMock(page: Page) {
  let pricePreviews = 0
  const writes: string[] = []
  await page.route('**/dev-api/**', async (route: Route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/dev-api', '')
    const method = request.method()
    const ok = (data: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) })
    if (path === '/captchaImage') return ok({ code: 200, captchaEnabled: false })
    if (path === '/login' && method === 'POST') return ok({ code: 200, token: 'fashion-value-token' })
    if (path === '/getInfo') return ok({
      code: 200, user: { userId: 1, userName: 'operator', nickName: '数据运营', avatar: '' },
      roles: ['fashion_operator'], permissions: ['fashion:price:import', 'fashion:stock:list',
        'fashion:stock:import', 'fashion:import:restore'], isDefaultModifyPwd: false, isPasswordExpired: false
    })
    if (path === '/getRouters') return ok({ code: 200, data: [{
      name: 'Fashion', path: '/fashion', component: 'Layout', redirect: 'noRedirect', alwaysShow: true,
      meta: { title: '智能选品', icon: 'shopping' }, children: [
        { name: 'FashionPriceImport', path: 'price-import', component: 'fashion/import/price/index', meta: { title: '价格全量更新' } },
        { name: 'FashionStockImport', path: 'stock-import', component: 'fashion/import/stock/index', meta: { title: '库存全量更新' } }
      ]
    }] })
    const dict = path.match(/^\/system\/dict\/data\/type\/(.+)$/)
    if (dict) return ok({ code: 200, data: (dictionaries[dict[1]] ?? []).map(item => ({ ...item, status: '0' })) })
    if (path === '/fashion/imports/prices/preview' && method === 'POST') {
      writes.push('price-preview')
      pricePreviews++
      if (pricePreviews === 1) {
        return ok({ code: 200, data: batch({
          status: 'invalid', actualCount: 1, errorCount: 1, errorMessage: '存在 1 个阻断项',
          details: [detail('000201', { salePrice: '0.00', currency: 'CNY', taxMode: 'included' }), {
            id: 'missing', detailNo: 2, rowType: 'missing', businessKey: 'MANUAL:000202',
            beforeData: { salePrice: '100.00' }, status: 'invalid', changeType: null,
            errors: [{ field: 'skuCode', code: 'missing_scope_row', valueSummary: '000202', message: '严格全量缺少范围内 SKU' }]
          }]
        }) })
      }
      return ok({ code: 200, data: batch() })
    }
    if (path === '/fashion/imports/prices/940001/publish' && method === 'POST') {
      writes.push('price-publish')
      return ok({ code: 200, data: batch({ status: 'success', rowVersion: 3 }) })
    }
    if (path === '/fashion/imports/prices/940001/restore' && method === 'POST') {
      writes.push('price-restore-preview')
      return ok({ code: 200, data: batch({
        batchId: '940010', batchNo: 'RESTORE-PRICE-940010', operationType: 'restore',
        sourceBatchId: '940001', status: 'validated', rowVersion: 1
      }) })
    }
    if (path === '/fashion/imports/prices/940010/publish' && method === 'POST') {
      writes.push('price-restore-publish')
      return ok({ code: 200, data: batch({
        batchId: '940010', batchNo: 'RESTORE-PRICE-940010', operationType: 'restore',
        sourceBatchId: '940001', status: 'success', rowVersion: 3
      }) })
    }
    if (path === '/fashion/imports/stocks/preview' && method === 'POST') {
      writes.push('stock-preview')
      return ok({ code: 200, data: batch({
        batchId: '940020', batchNo: 'STOCK-940020', importType: 'stock', warehouseCode: 'MAIN',
        details: [
          detail('000201', { availableQty: 0, asOf: new Date().toISOString() }),
          detail('000202', { availableQty: 8, asOf: new Date().toISOString() })
        ]
      }) })
    }
    if (path === '/fashion/imports/stocks/940020/publish' && method === 'POST') {
      writes.push('stock-publish')
      return ok({ code: 200, data: batch({
        batchId: '940020', batchNo: 'STOCK-940020', importType: 'stock', warehouseCode: 'MAIN',
        status: 'success', rowVersion: 3,
        details: [detail('000201', { availableQty: 0 }), detail('000202', { availableQty: 8 })]
      }) })
    }
    return route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ code: 404, msg: `未模拟 ${method} ${path}` }) })
  })
  return writes
}

test('价格和库存严格全量、显式零与恢复均只调用 Java API', async ({ page }) => {
  const writes = await installMock(page)
  const foreignRequests: string[] = []
  page.on('request', request => {
    const url = new URL(request.url())
    if (url.origin !== 'http://127.0.0.1:4175') foreignRequests.push(url.href)
  })
  await page.goto('/login')
  await page.getByRole('button', { name: '登 录' }).click()
  await page.waitForURL(url => !url.pathname.endsWith('/login'))
  await page.goto('/fashion/price-import')
  await expect(page.getByText('这是范围全量，不是全库更新。')).toBeVisible()
  await expect(page.getByRole('link', { name: '下载 CSV 模板' })).toHaveAttribute(
    'href', '/dev-api/fashion/imports/prices/template')
  await page.locator('.catalog-import input[type=file]').setInputFiles(
    '../platform-backend/ruoyi-fashion/src/main/resources/fashion/import-templates/price-v1.csv')
  await page.getByRole('button', { name: '严格校验并预览' }).click()
  await expect(page.getByText('严格全量缺少范围内 SKU')).toBeVisible()
  await expect(page.getByText('范围应覆盖').locator('..')).toContainText('2')

  // 首次失败预览的响应会先刷新视图，然后再在 finally 中解除按钮加载状态。
  // 重新加载页面代表运营人员修正文件后重新进入导入流程，也避免将同一次点击事件误当成第二次预览。
  await page.reload()
  await expect(page.getByText('这是范围全量，不是全库更新。')).toBeVisible()
  await page.locator('.catalog-import input[type=file]').setInputFiles(
    '../platform-backend/ruoyi-fashion/src/main/resources/fashion/import-templates/price-v1.csv')
  await page.getByRole('button', { name: '严格校验并预览' }).click()
  await expect(page.getByText('PRICE-940001 · 待发布')).toBeVisible()
  await expect(page.getByText('salePrice=0.00')).toBeVisible()
  await page.getByRole('button', { name: '确认原子发布' }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '确定' }).click()
  await expect(page.getByText('PRICE-940001 · 已成功')).toBeVisible()
  await page.getByRole('button', { name: '生成恢复预览' }).click()
  await expect(page.getByText('RESTORE-PRICE-940010 · 待发布')).toBeVisible()
  await page.getByRole('button', { name: '确认原子发布' }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '确定' }).click()
  await expect(page.getByText('RESTORE-PRICE-940010 · 已成功')).toBeVisible()

  await page.goto('/fashion/stock-import')
  await expect(page.getByRole('link', { name: '下载 CSV 模板' })).toHaveAttribute(
    'href', '/dev-api/fashion/imports/stocks/template')
  await page.locator('.catalog-import input[type=file]').setInputFiles(
    '../platform-backend/ruoyi-fashion/src/main/resources/fashion/import-templates/stock-v1.csv')
  await page.getByRole('button', { name: '严格校验并预览' }).click()
  await expect(page.getByText('availableQty=0')).toBeVisible()
  await page.getByRole('button', { name: '确认原子发布' }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '确定' }).click()
  await expect(page.getByText('STOCK-940020 · 已成功')).toBeVisible()
  expect(writes).toEqual([
    'price-preview', 'price-preview', 'price-publish', 'price-restore-preview',
    'price-restore-publish', 'stock-preview', 'stock-publish'
  ])
  expect(foreignRequests).toEqual([])
  await page.screenshot({ path: '../output/playwright/fashion-production/catalog-value-flow.png', fullPage: true })
})
