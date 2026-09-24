import { expect, test, type Page, type Route } from '@playwright/test'

const customer = {
  id: '950001', code: 'C-001', name: '星河集团', customerType: 'group_purchase',
  contactName: '李经理', contactPhone: '13800138000', region: '上海', salespersonId: '1',
  collaboratorIds: [], internalNote: '仅内部可见', status: 'active',
  updateTime: '2026-09-13T00:00:00Z', rowVersion: 1
}
const quote = {
  id: '950002', quoteNo: 'Q-20260913-001', versionNo: 1, customerId: customer.id,
  customerName: customer.name, title: '秋季员工活动服', salespersonId: '1',
  requirementText: '需要一百套秋季员工活动服装', requirement: {
    confirmed: false, preferred_colors: [], exclusions: []
  }, requirementConfirmed: false, requestedQty: 100, budget: 30000, budgetBasis: 'total',
  quoteMode: 'alternatives', progressive: true,
  comboTemplate: { groups: [{ count: 1, slots: ['TOP'], candidate_count: 3 }] },
  warehouseCode: 'MAIN', currency: 'CNY', taxMode: 'included', status: 'draft',
  updateTime: '2026-09-13T00:00:00Z', rowVersion: 1, priceFacts: []
}

async function installMock(page: Page) {
  const writes: string[] = []
  await page.route('**/dev-api/**', async (route: Route) => {
    const request = route.request(), method = request.method()
    const path = new URL(request.url()).pathname.replace('/dev-api', '')
    const ok = (data: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) })
    if (path === '/captchaImage') return ok({ code: 200, captchaEnabled: false })
    if (path === '/login' && method === 'POST') return ok({ code: 200, token: 'workflow-token' })
    if (path === '/getInfo') return ok({ code: 200, user: { userId: 1, userName: 'operator', nickName: '方案运营', avatar: '' }, roles: ['fashion_operator'], permissions: [
      'fashion:customer:list','fashion:customer:query','fashion:customer:add','fashion:customer:edit',
      'fashion:quote:list','fashion:quote:query','fashion:quote:add','fashion:quote:edit',
      'fashion:ai:agent:list','fashion:ai:agent:edit','fashion:ai:agent:publish',
      'fashion:ai:run:list','fashion:ai:run:execute','fashion:ai:run:cancel','fashion:ai:run:apply'
    ], isDefaultModifyPwd: false, isPasswordExpired: false, pwdChrtype: 0 })
    if (path === '/getRouters') return ok({ code: 200, data: [{ name: 'Fashion', path: '/fashion', component: 'Layout', redirect: 'noRedirect', alwaysShow: true, meta: { title: '智能选品' }, children: [
      { name: 'FashionCustomer', path: 'customer', component: 'fashion/customer/index', meta: { title: '客户中心' } },
      { name: 'FashionQuote', path: 'quote', component: 'fashion/quote/index', meta: { title: '方案管理' } },
      { name: 'FashionWorkbench', path: 'workbench', component: 'fashion/workbench/index', meta: { title: '方案工作台' } },
      { name: 'FashionAgent', path: 'agent', component: 'fashion/agent/index', meta: { title: 'Agent 版本' } }
    ] }] })
    if (path.startsWith('/system/dict/data/type/')) return ok({ code: 200, data: path.endsWith('fashion_warehouse')
      ? [{ dictLabel: '主仓', dictValue: 'MAIN', status: '0' }]
      : [{ dictLabel: '上衣', dictValue: 'TOP', status: '0' }] })
    if (path === '/fashion/customers' && method === 'GET') return ok({ code: 200, data: { items: [customer], total: 1, page: 1, pageSize: 20 } })
    if (path === '/fashion/customers' && method === 'POST') { writes.push('customer-create'); return ok({ code: 200, data: customer }) }
    if (path === '/fashion/quotes' && method === 'GET') return ok({ code: 200, data: { items: [quote], total: 1, page: 1, pageSize: 20 } })
    if (path === `/fashion/quotes/${quote.id}` && method === 'GET') return ok({ code: 200, data: quote })
    if (path === '/fashion/ai/runs/capabilities/requirement-analysis') return ok({ code: 200, data: { enabled: false, reason: 'provider_disabled' } })
    if (path === '/fashion/ai/runs/capabilities/selection-styling') return ok({ code: 200, data: { enabled: false, reason: 'provider_disabled' } })
    if (path === `/fashion/quotes/${quote.id}/selection` && method === 'GET') return ok({ code: 200, data: {
      quote,
      preview: {
        quote_ref: quote.id, quote_row_version: quote.rowVersion, selection_mode: 'progressive',
        requested_qty: 100, budget_maximum_per_set_minor: 30000,
        tiers: [{ category_count: 1, candidate_count: 3, slots: [{ slot_index: 1, category: 'TOP', required: true }] }],
        frozen_candidates: [], locks: [],
        shortages: [{ category_code: 'TOP', reason: '没有同时满足当前价格、库存和图片约束的候选' }],
        candidate_set_hash: 'a'.repeat(64)
      },
      combinations: []
    } })
    if (path === '/fashion/ai/agents') return ok({ code: 200, data: [{ id: '950003', agentCode: 'requirement', name: '需求分析', agentType: 'requirement', currentVersionId: null, status: 'active', rowVersion: 1 }] })
    if (path === '/fashion/ai/agents/950003/versions') return ok({ code: 200, data: [{ id: '950004', agentId: '950003', versionNo: 1, providerCode: 'openai', modelName: 'configured-model', systemInstruction: '生成待确认需求', modelConfig: {}, tools: [], handoffs: [], inputSchema: {}, outputSchema: {}, guardrails: {}, maxSteps: 4, timeoutSeconds: 120, configHash: 'a'.repeat(64), status: 'draft', rowVersion: 1 }] })
    if (path === '/fashion/ai/agents/950003/versions/950004/publish' && method === 'POST') { writes.push('agent-publish'); return ok({ code: 200, data: {} }) }
    return route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ code: 404, msg: `${method} ${path}` }) })
  })
  return writes
}

test('客户、方案工作台和 Agent 版本只经 Java API，Provider 未启用时明确禁用', async ({ page }) => {
  const writes = await installMock(page)
  const foreign: string[] = []
  page.on('request', request => { if (new URL(request.url()).origin !== 'http://127.0.0.1:4175') foreign.push(request.url()) })
  await page.goto('/login'); await page.getByRole('button', { name: '登 录' }).click(); await page.waitForURL(url => !url.pathname.endsWith('/login'))

  await page.goto('/fashion/customer'); await expect(page.getByText('星河集团')).toBeVisible()
  await page.getByRole('button', { name: '新增客户' }).click()
  await page.getByLabel('客户编码').fill('C-002'); await page.getByLabel('客户名称').fill('新客户')
  await page.locator('.el-dialog').getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('客户已保存')).toBeVisible()

  await page.goto('/fashion/quote'); await expect(page.getByText('秋季员工活动服')).toBeVisible()
  await page.getByRole('button', { name: '工作台' }).click(); await expect(page.getByText('AI Runtime 未配置，需求分析已禁用')).toBeVisible()
  await expect(page.getByRole('button', { name: '开始分析' })).toBeDisabled()
  await expect(page.getByText('AI Runtime 未配置，选品搭配已禁用')).toBeVisible()
  await expect(page.getByRole('button', { name: '生成搭配' })).toBeDisabled()
  await page.getByRole('button', { name: '从 Java 刷新' }).click()
  await expect(page.getByText('没有同时满足当前价格、库存和图片约束的候选')).toBeVisible()

  await page.goto('/fashion/agent'); await page.getByText('需求分析', { exact: true }).first().click()
  await expect(page.getByText('openai / configured-model')).toBeVisible()
  await page.getByRole('button', { name: '发布' }).click(); await page.locator('.el-message-box').getByRole('button', { name: '确定' }).click()
  await expect(page.getByText('Agent 版本已发布')).toBeVisible()
  expect(writes).toEqual(['customer-create', 'agent-publish'])
  expect(foreign).toEqual([])
  await page.screenshot({ path: '../output/playwright/fashion-production/workflow-flow.png', fullPage: true })
})
