import { expect, test, type Page, type Route } from '@playwright/test'

const quote = {
  id: '970040', quoteNo: 'Q-IMP08', versionNo: 1, customerId: '970010', customerName: '星河集团',
  title: '四品类正式报价', salespersonId: '1', requirementText: '一百套四品类员工服',
  requirement: { confirmed: true, preferred_colors: ['黑色'], exclusions: [] }, requirementConfirmed: true,
  requestedQty: 100, budget: 30000, budgetBasis: 'total', quoteMode: 'combined', progressive: true,
  comboTemplate: { groups: [{ count: 4, slots: ['TOP', 'PANTS', 'HAT', 'SHOES'], candidate_count: 1 }] },
  warehouseCode: 'MAIN', currency: 'CNY', taxMode: 'included', status: 'draft',
  updateTime: '2026-09-13T00:00:00Z', rowVersion: 1, priceFacts: []
}

const products = [
  ['301', 'TOP', '上衣', '80.00'], ['302', 'PANTS', '裤子', '60.00'],
  ['303', 'HAT', '帽子', '20.00'], ['304', 'SHOES', '鞋子', '90.00']
]

function workspace(status = 'draft', rowVersion = 1, badQty = false) {
  return {
    quoteId: quote.id, quoteNo: quote.quoteNo, versionNo: 1, customerName: quote.customerName, requestedQty: 100, status,
    rowVersion, mode: 'combined', warehouseCode: 'MAIN', currency: 'CNY', taxMode: 'included',
    feeTaxable: true, discountType: 'percent', discountRate: '5.00', fixedDiscount: '0.00',
    freight: '300.00', validDays: 7, publicNote: '含运费，七日有效', inputHash: '8'.repeat(64),
    approvalRequired: false, approvalValid: false, subtotal: '25000.00', discountAmount: '1250.00',
    taxAmount: '0.00', totalAmount: '24050.00', maximumAvailableSets: 130, calculatedCombos: [],
    issues: badQty ? [{ code: 'ALLOCATION_MISMATCH', message: '组合 200 的槽位 TOP 数量 90，应为 100' }] : [],
    combos: [{
      id: '200', comboNo: 'C-1', name: '四品类方案', categoryCount: 4, setQty: 100, selected: true,
      allocationConfirmed: !badQty, selectedImageValid: true, subtotal: '25000.00', discountAmount: '1250.00', freight: '300.00',
      taxAmount: '0.00', totalAmount: '24050.00', rowVersion,
      lines: products.map(([id, slotCode, productName, price], index) => ({
        id, slotCode, productId: String(index + 1), skuCode: `SKU-${index + 1}`, productName,
        categoryCode: slotCode, colorName: '黑色', sizeCode: 'M', unit: '件',
        qty: badQty && index === 0 ? 90 : 100, sourcePrice: price, quotePrice: price,
        amount: `${Number(price) * 100}.00`, stockQty: 130, stockAsOf: '2026-09-13T00:00:00Z',
        priceBatchId: '10', stockBatchId: '20', priceChanged: false, stockChanged: false,
        imageChanged: false, rowVersion
      }))
    }]
  }
}

async function installMock(page: Page) {
  let current = workspace()
  const writes: Array<{ method: string; path: string; body?: unknown }> = []
  await page.route('**/dev-api/**', async (route: Route) => {
    const request = route.request(), method = request.method()
    const path = new URL(request.url()).pathname.replace('/dev-api', '')
    const ok = (data: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) })
    if (path === '/captchaImage') return ok({ code: 200, captchaEnabled: false })
    if (path === '/login' && method === 'POST') return ok({ code: 200, token: 'pricing-token' })
    if (path === '/getInfo') return ok({ code: 200,
      user: { userId: 1, userName: 'operator', nickName: '报价运营', avatar: '' }, roles: ['fashion_operator'],
      permissions: ['fashion:quote:list', 'fashion:quote:query', 'fashion:quote:add', 'fashion:quote:edit',
        'fashion:quote:confirm', 'fashion:quote:approve'], isDefaultModifyPwd: false,
      isPasswordExpired: false, pwdChrtype: 0 })
    if (path === '/getRouters') return ok({ code: 200, data: [{ name: 'Fashion', path: '/fashion',
      component: 'Layout', redirect: 'noRedirect', alwaysShow: true, meta: { title: '智能选品' }, children: [
        { name: 'FashionQuote', path: 'quote', component: 'fashion/quote/index', meta: { title: '方案管理' } }
      ] }] })
    if (path.startsWith('/system/dict/data/type/')) return ok({ code: 200, data: path.endsWith('fashion_warehouse')
      ? [{ dictLabel: '主仓', dictValue: 'MAIN', status: '0' }]
      : [{ dictLabel: '上衣', dictValue: 'TOP', status: '0' }] })
    if (path === '/fashion/customers') return ok({ code: 200, data: { items: [{ id: '970010', code: 'C-1',
      name: quote.customerName, customerType: 'group_purchase', salespersonId: '1', collaboratorIds: [],
      status: 'active', updateTime: '2026-09-13T00:00:00Z', rowVersion: 1 }], total: 1, page: 1, pageSize: 20 } })
    if (path === '/fashion/quotes' && method === 'GET') return ok({ code: 200, data: { items: [quote], total: 1, page: 1, pageSize: 20 } })
    if (path === `/fashion/quotes/${quote.id}/pricing` && method === 'GET') return ok({ code: 200, data: current })
    if (path === `/fashion/quotes/${quote.id}/pricing` && method === 'PUT') {
      const body = request.postDataJSON(); writes.push({ method, path, body })
      current = workspace('draft', current.rowVersion + 1, body.lines[0].qty === 90)
      return ok({ code: 200, data: current })
    }
    if (path === `/fashion/quotes/${quote.id}/pricing/confirm` && method === 'POST') {
      writes.push({ method, path, body: request.postDataJSON() }); current = workspace('confirmed', current.rowVersion + 1)
      return ok({ code: 200, data: current })
    }
    if (path === `/fashion/quotes/${quote.id}/copy` && method === 'POST') {
      writes.push({ method, path }); return ok({ code: 200, data: { ...quote, id: '970041', versionNo: 2 } })
    }
    return route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ code: 404, msg: `${method} ${path}` }) })
  })
  return writes
}

test('Java 十进制报价、数量阻断、版本冻结和修订复制形成同一条工作流', async ({ page }) => {
  const writes = await installMock(page)
  const foreign: string[] = []
  page.on('request', request => { if (new URL(request.url()).origin !== 'http://127.0.0.1:4175') foreign.push(request.url()) })
  await page.goto('/login'); await page.getByRole('button', { name: '登 录' }).click()
  await page.goto('/fashion/quote'); await page.getByRole('button', { name: '报价', exact: true }).click()
  const drawer = page.locator('.el-drawer')
  await expect(drawer.getByText('应付 ¥24050.00')).toBeVisible()

  await drawer.locator('.el-input-number').nth(1).getByRole('spinbutton').fill('90')
  await drawer.getByRole('button', { name: '保存并重新核价' }).click()
  await expect(drawer.getByText(/ALLOCATION_MISMATCH/)).toBeVisible()
  await expect(drawer.getByRole('button', { name: '确认并冻结版本' })).toBeDisabled()

  await drawer.locator('.el-input-number').nth(1).getByRole('spinbutton').fill('100')
  await drawer.getByRole('button', { name: '保存并重新核价' }).click()
  await expect(drawer.getByText(/ALLOCATION_MISMATCH/)).toHaveCount(0)
  await drawer.getByRole('button', { name: '确认并冻结版本' }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '确定' }).click()
  await expect(drawer.getByText('已确认', { exact: true })).toBeVisible()
  await expect(drawer.getByRole('button', { name: '复制为新修订' })).toBeVisible()
  await drawer.getByRole('button', { name: '复制为新修订' }).click()
  await expect(page.getByText('已复制三层记录为新修订')).toBeVisible()

  expect(writes.map(item => `${item.method} ${item.path}`)).toEqual([
    `PUT /fashion/quotes/${quote.id}/pricing`, `PUT /fashion/quotes/${quote.id}/pricing`,
    `POST /fashion/quotes/${quote.id}/pricing/confirm`, `POST /fashion/quotes/${quote.id}/copy`
  ])
  expect(foreign).toEqual([])
  await page.screenshot({ path: '../output/playwright/fashion-production/pricing-flow.png', fullPage: true })
})
