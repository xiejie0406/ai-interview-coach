import { expect, test, type Page, type Route } from '@playwright/test'

const dictionaries: Record<string, { dictLabel: string; dictValue: string }[]> = {
  fashion_product_source: [{ dictLabel: '人工维护', dictValue: 'MANUAL' }, { dictLabel: '京东', dictValue: 'JD' }],
  fashion_product_category: [{ dictLabel: '上衣', dictValue: 'TOP' }],
  fashion_product_color: [{ dictLabel: '黑色', dictValue: 'BLACK' }],
  fashion_product_unit: [{ dictLabel: '件', dictValue: '件' }],
  fashion_product_season: [{ dictLabel: '四季', dictValue: '四季' }]
}

function product(name = '前导零 SKU 商品', status = 'draft', rowVersion = 1) {
  return {
    id: '910003', sourceCode: 'MANUAL', skuCode: '000123', styleCode: 'STYLE-X', name,
    categoryCode: 'TOP', colorCode: 'BLACK', colorName: '黑色', sizeCode: 'M', sizeSystem: 'LETTER',
    unit: '件', salePrice: null, currency: 'CNY', taxMode: 'included', brand: '品牌', material: '棉',
    season: '四季', tags: [], images: [], visualVersion: 1, attributesConfirmed: false, status,
    incompleteReasons: ['missing_price', 'missing_image', 'attributes_unconfirmed', ...(status === 'active' ? [] : ['not_active'])],
    rowVersion, updateTime: '2026-09-13T00:00:00Z'
  }
}

async function installJavaApiMock(page: Page) {
  let currentProduct = product()
  const writes: string[] = []
  await page.route('**/dev-api/**', async (route: Route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/dev-api', '')
    const method = request.method()
    const ok = (data: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) })

    if (path === '/captchaImage') return ok({ code: 200, captchaEnabled: false })
    if (path === '/login' && method === 'POST') return ok({ code: 200, token: 'fashion-e2e-token' })
    if (path === '/getInfo') return ok({
      code: 200,
      user: { userId: 1, userName: 'operator', nickName: '商品运营', avatar: '' },
      roles: ['fashion_operator'],
      permissions: ['fashion:product:list', 'fashion:product:query', 'fashion:product:edit',
        'fashion:product:import', 'fashion:product:image', 'fashion:ai:run:list',
        'fashion:ai:run:execute', 'fashion:ai:run:apply'],
      isDefaultModifyPwd: false, isPasswordExpired: false, pwdChrtype: 0
    })
    if (path === '/getRouters') return ok({ code: 200, data: [{
      name: 'Fashion', path: '/fashion', component: 'Layout', redirect: 'noRedirect', alwaysShow: true,
      meta: { title: '智能选品', icon: 'shopping' },
      children: [
        { name: 'FashionProduct', path: 'product', component: 'fashion/product/index', meta: { title: '商品中心' } },
        { name: 'FashionImport', path: 'import', component: 'fashion/import/index', meta: { title: '商品导入' } },
        { name: 'FashionMaterial', path: 'material', component: 'fashion/material/index', meta: { title: '图片素材' } }
      ]
    }] })
    const dictMatch = path.match(/^\/system\/dict\/data\/type\/(.+)$/)
    if (dictMatch) return ok({ code: 200, data: (dictionaries[dictMatch[1]] ?? []).map(item => ({ ...item, status: '0' })) })
    if (path === '/fashion/products' && method === 'GET') {
      return ok({ code: 200, data: { items: [currentProduct], total: 1, page: 1, pageSize: 20 } })
    }
    if (path === '/fashion/ai/runs/capabilities/product-attribute-suggestion' && method === 'GET') {
      return ok({ code: 200, data: { enabled: false, reason: 'provider_disabled' } })
    }
    if (path === '/fashion/products/batch' && method === 'PUT') {
      writes.push('product-edit')
      currentProduct = product('已人工改名', currentProduct.status, currentProduct.rowVersion + 1)
      return ok({ code: 200, data: [currentProduct] })
    }
    if (path === '/fashion/products/910003/status' && method === 'PUT') {
      writes.push('product-status')
      currentProduct = product(currentProduct.name, 'active', currentProduct.rowVersion + 1)
      return ok({ code: 200, data: currentProduct })
    }
    if (path === '/fashion/imports/products/preview' && method === 'POST') {
      writes.push('product-preview')
      return ok({ code: 200, data: {
        batchId: '920001', batchNo: 'PRODUCT-920001', status: 'validated', sourceCode: 'MANUAL',
        fileHash: 'a'.repeat(64), asOf: '2026-09-13T00:00:00Z', actualCount: 1, errorCount: 0, rowVersion: 1,
        details: [{ id: '920002', detailNo: 1, sourceRowNo: 2, businessKey: 'MANUAL:000123',
          status: 'valid', errors: [], changeType: 'changed' }]
      } })
    }
    if (path === '/fashion/imports/products/920001/publish' && method === 'POST') {
      writes.push('product-publish')
      return ok({ code: 200, data: {
        batchId: '920001', batchNo: 'PRODUCT-920001', status: 'success', sourceCode: 'MANUAL',
        fileHash: 'a'.repeat(64), asOf: '2026-09-13T00:00:00Z', actualCount: 1, errorCount: 0, rowVersion: 3,
        details: [{ id: '920002', detailNo: 1, sourceRowNo: 2, businessKey: 'MANUAL:000123',
          status: 'applied', errors: [], changeType: 'changed' }]
      } })
    }
    if (path === '/fashion/materials/preview' && method === 'POST') {
      writes.push('material-preview')
      return ok({ code: 200, data: {
        batchId: '930001', batchNo: 'IMAGE-930001', status: 'validated', sourceCode: 'MANUAL',
        fileHash: 'b'.repeat(64), asOf: '2026-09-13T00:00:00Z', actualCount: 1, errorCount: 0, rowVersion: 1,
        details: [{ id: '930002', detailNo: 1, sourceRowNo: 1, businessKey: 'MANUAL:image:profile.jpg',
          rawData: { filename: 'profile.jpg' }, normalizedData: { width: 320, height: 240 },
          status: 'valid', errors: [], changeType: 'changed' }]
      } })
    }
    if (path === '/fashion/materials/930001/confirm' && method === 'POST') {
      writes.push('material-confirm')
      return ok({ code: 200, data: {
        batchId: '930001', batchNo: 'IMAGE-930001', status: 'success', sourceCode: 'MANUAL',
        fileHash: 'b'.repeat(64), asOf: '2026-09-13T00:00:00Z', actualCount: 1, errorCount: 0, rowVersion: 3,
        details: [{ id: '930002', detailNo: 1, businessKey: 'MANUAL:image:profile.jpg',
          normalizedData: { width: 320, height: 240 }, status: 'applied', errors: [], changeType: 'changed' }]
      } })
    }
    return route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ code: 404, msg: `未模拟 ${method} ${path}` }) })
  })
  return writes
}

test('商品、资料导入与图片确认均只经 Java API 完成', async ({ page }) => {
  const writes = await installJavaApiMock(page)
  const foreignRequests: string[] = []
  page.on('request', request => {
    const url = new URL(request.url())
    if (url.origin !== 'http://127.0.0.1:4175') foreignRequests.push(url.href)
  })

  await page.goto('/login')
  await page.getByRole('button', { name: '登 录' }).click()
  await page.waitForURL(url => !url.pathname.endsWith('/login'))
  await page.goto('/fashion/product')
  await expect(page.locator('.fashion-products')).toBeVisible()
  await expect(page.getByText('MANUAL / STYLE-X')).toBeVisible()
  await expect(page.getByText('000123', { exact: true })).toBeVisible()
  await expect(page.getByText('缺价格')).toBeVisible()
  await expect(page.getByText('AI 商品属性建议已禁用：Provider 未配置')).toBeVisible()
  await expect(page.getByRole('button', { name: 'AI 属性建议' })).toBeDisabled()

  await page.getByRole('button', { name: '改名' }).click()
  await page.locator('.el-message-box__input input').fill('已人工改名')
  await page.locator('.el-message-box').getByRole('button', { name: '确定' }).click()
  await expect(page.getByText('已人工改名')).toBeVisible()
  await page.getByRole('button', { name: '上架' }).click()
  await expect(page.locator('.fashion-products .el-table__body').getByText('在售', { exact: true })).toBeVisible()

  await page.goto('/fashion/import')
  await page.locator('.fashion-import .el-select').click()
  await page.getByRole('option', { name: '人工维护' }).click()
  await expect(page.getByRole('link', { name: '下载 CSV 模板' })).toHaveAttribute(
    'href', '/dev-api/fashion/imports/products/template')
  await page.locator('.fashion-import input[type=file]').setInputFiles(
    '../platform-backend/ruoyi-fashion/src/main/resources/fashion/import-templates/product-v1.csv')
  await page.getByRole('button', { name: '校验并预览' }).click()
  await expect(page.getByText('PRODUCT-920001')).toBeVisible()
  await expect(page.getByText('MANUAL:000123')).toBeVisible()
  await page.getByRole('button', { name: '确认生效' }).click()
  await expect(page.getByText('PRODUCT-920001 · success')).toBeVisible()

  await page.goto('/fashion/material')
  await page.locator('.fashion-materials .el-select').first().click()
  await page.getByRole('option', { name: '人工维护' }).click()
  await page.locator('.fashion-materials input[type=file]').setInputFiles('src/assets/images/profile.jpg')
  const mappingRow = page.locator('.fashion-materials .el-table__body tr').first()
  await mappingRow.locator('input').first().fill('000123')
  await mappingRow.locator('.el-checkbox').nth(0).click()
  await mappingRow.locator('.el-checkbox').nth(1).click()
  await page.getByRole('button', { name: '上传并预览' }).click()
  await expect(page.getByText('320×240')).toBeVisible()
  await page.getByRole('button', { name: '人工确认入库' }).click()
  await expect(page.getByText(/IMAGE-930001 · 错误 0/)).toBeVisible()

  expect(writes).toEqual([
    'product-edit', 'product-status', 'product-preview', 'product-publish',
    'material-preview', 'material-confirm'
  ])
  expect(foreignRequests).toEqual([])
  await page.screenshot({ path: '../output/playwright/fashion-production/catalog-flow.png', fullPage: true })
})
