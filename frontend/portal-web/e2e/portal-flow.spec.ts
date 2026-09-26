import { expect, test, type Page, type Route } from '@playwright/test'

const json = (route: Route, body: unknown, status = 200, headers: Record<string, string> = {}) =>
  route.fulfill({ status, contentType: 'application/json', headers, body: JSON.stringify(body) })

async function authenticated(page: Page) {
  await page.context().addCookies([{
    name: 'Admin-Token',
    value: 'e2e-token',
    domain: '127.0.0.1',
    path: '/',
    sameSite: 'Lax',
  }])
  await page.route('**/api/getInfo', route => json(route, {
    code: 200,
    user: { userId: 1, userName: 'e2e', nickName: '自动化验收用户' },
    roles: ['admin'],
    permissions: ['*:*:*'],
  }))
  await page.route('**/api/getRouters', route => json(route, { code: 200, data: [] }))
}

function snapshot(state: 'READY' | 'IN_PROGRESS' | 'COMPLETED', phase = 0, mode = 'TEXT') {
  const primary = {
    turnId: '10000000-0000-0000-0000-000000000001',
    sequence: 1,
    kind: 'PRIMARY',
    state: phase > 0 ? 'ANSWER_CONFIRMED' : 'QUESTION_COMMITTED',
    questionText: '请说明 HashMap 在并发写入时的风险。',
    answerVersionId: phase > 0 ? 'answer-1' : null,
  }
  const followUp = {
    turnId: '10000000-0000-0000-0000-000000000002',
    sequence: 2,
    kind: 'FOLLOW_UP',
    state: 'QUESTION_COMMITTED',
    questionText: '如果需要线程安全，你会如何选择替代方案？',
    answerVersionId: null,
  }
  return {
    id: '10000000-0000-0000-0000-000000000000',
    state,
    mode,
    turns: state === 'READY' ? [] : phase > 0 ? [primary, followUp] : [primary],
    allowedCommands: state === 'READY' ? ['START', 'CANCEL'] : state === 'IN_PROGRESS'
      ? ['PAUSE', 'SKIP', 'SUBMIT_ANSWER', 'COMPLETE', 'CANCEL'] : [],
    version: state === 'READY' ? 1 : phase > 0 ? 6 : 4,
    failureCode: null,
  }
}

test('题库能够浏览列表、题目和已发布答案', async ({ page }) => {
  await page.route('**/api/v1/questions?**', route => json(route, {
    items: [{
      id: '20000000-0000-0000-0000-000000000001',
      versionId: 'question-version-1',
      title: 'Agent 如何选择工具',
      category: 'AGENT_BASICS_DEEP_V3',
      difficulty: 'MID',
    }],
    hasMore: false,
    nextCursor: null,
  }))
  await page.route('**/api/v1/questions/20000000-0000-0000-0000-000000000001', route => json(route, {
    id: '20000000-0000-0000-0000-000000000001',
    versionId: 'question-version-1',
    title: 'Agent 如何选择工具',
    category: 'AGENT_BASICS_DEEP_V3',
    difficulty: 'MID',
    prompt: '请说明 Agent 选择工具时需要考虑的因素。',
    referenceAnswer: ['【核心】根据能力描述、输入约束和失败策略选择工具。'],
    systemAnswer: ['【核心】系统发布答案。'],
    hasUserAnswer: false,
  }))

  await page.goto('/questions')
  await page.getByRole('button', { name: /Agent 基础/ }).click()
  await expect(page.getByText('Agent 基础 · 当前 1 题')).toBeVisible()
  await page.getByRole('button', { name: /Agent 如何选择工具/ }).click()
  await expect(page.getByRole('heading', { name: 'Agent 如何选择工具' })).toBeVisible()
  await expect(page.getByText('根据能力描述、输入约束和失败策略选择工具。')).toBeVisible()
})

test('文字面试完成回答、动态追问、结束和反馈报告闭环', async ({ page }) => {
  await authenticated(page)
  let state: 'READY' | 'IN_PROGRESS' | 'COMPLETED' = 'READY'
  let phase = 0
  let reportCreated = false

  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path.endsWith('/policies/current')) {
      return json(route, { policies: [{ purpose: 'MODEL_PROCESSING', versionId: 'policy-v1' }] })
    }
    if (path.endsWith('/consents/MODEL_PROCESSING/grants')) return json(route, { granted: true })
    if (path.endsWith('/feedback')) {
      if (request.method() === 'GET' && !reportCreated) {
        return json(route, { code: 404, msg: '请求的资源不存在', errorCode: 'NOT_FOUND' }, 404)
      }
      reportCreated = true
      return json(route, {
        interviewId: '10000000-0000-0000-0000-000000000000',
        model: 'deepseek-chat',
        createdAt: '2026-09-10T08:00:00Z',
        limitations: ['AI 分类反馈，不代表招聘结论。'],
        turns: [{
          turnId: '10000000-0000-0000-0000-000000000001',
          question: '请说明 HashMap 在并发写入时的风险。',
          answer: '并发写入会产生覆盖和结构不一致，应使用 ConcurrentHashMap。',
          rubricVersionId: 'rubric-v1',
          dimensions: [{
            code: 'CORRECTNESS',
            criterion: '识别并发风险并提出合理方案',
            judgement: 'SUPPORTED',
            quote: '应使用 ConcurrentHashMap',
            feedback: '风险和替代方案明确，可以补充复合操作的原子性。',
          }],
        }],
      })
    }
    if (path.endsWith('/commands/start')) {
      state = 'IN_PROGRESS'
      return json(route, snapshot(state, phase), 200, { ETag: '"v4"' })
    }
    if (path.endsWith('/answers')) {
      phase = 1
      return json(route, snapshot(state, phase), 200, { ETag: '"v6"' })
    }
    if (path.endsWith('/commands/complete')) {
      state = 'COMPLETED'
      return json(route, snapshot(state, phase), 200, { ETag: '"v8"' })
    }
    if (path.endsWith('/interviews/10000000-0000-0000-0000-000000000000')) {
      return json(route, snapshot(state, phase), 200, { ETag: `"v${state === 'READY' ? 1 : phase ? 6 : 4}"` })
    }
    return json(route, { code: 404, msg: `未模拟 ${path}` }, 404)
  })

  await page.goto('/interviews/10000000-0000-0000-0000-000000000000')
  await page.getByRole('button', { name: '开始面试' }).click()
  await expect(page.getByText('请说明 HashMap 在并发写入时的风险。')).toBeVisible()
  await page.getByLabel('面试回答').fill('并发写入会产生覆盖和结构不一致，应使用 ConcurrentHashMap。')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByText('如果需要线程安全，你会如何选择替代方案？')).toBeVisible()
  await page.getByRole('button', { name: '结束并查看反馈' }).click()
  await expect(page).toHaveURL(/\/feedback$/)
  await expect(page.getByText('面试已经结束，可以基于已确认回答生成反馈。')).toBeVisible()
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '生成反馈报告' }).click()
  await expect(page.getByText('风险和替代方案明确，可以补充复合操作的原子性。')).toBeVisible()
})

test('语音预检失败时保留文字回答路径', async ({ page }) => {
  await authenticated(page)
  await page.route('**/api/v1/**', route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path.endsWith('/policies/current')) return json(route, { policies: [
      { purpose: 'VOICE_CAPTURE', versionId: 'voice-v1' },
      { purpose: 'MODEL_PROCESSING', versionId: 'model-v1' },
    ] })
    if (path.includes('/consents/')) return json(route, { granted: true })
    if (path.endsWith('/voice-preflight')) return json(route, {
      enabled: false,
      consentRequired: false,
      supportedCodecs: ['audio/webm;codecs=opus'],
      maxDurationSeconds: 120,
      maxBytes: 12582912,
      unavailableReasonCode: 'ASR_NOT_CONFIGURED',
    })
    if (path.endsWith('/interviews/10000000-0000-0000-0000-000000000000')) {
      return json(route, snapshot('IN_PROGRESS', 0, 'CASCADE_VOICE'), 200, { ETag: '"v4"' })
    }
    return json(route, { code: 404, msg: `未模拟 ${path}` }, 404)
  })

  await page.goto('/interviews/10000000-0000-0000-0000-000000000000')
  await page.getByText('我同意本轮语音采集和模型转写').click()
  await page.getByRole('button', { name: '开始语音回答' }).click()
  await expect(page.getByRole('alert')).toContainText('ASR_NOT_CONFIGURED')
  await expect(page.getByLabel('面试回答')).toBeVisible()
})

test('窄屏面试仍可跳过或结束并进入反馈', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await authenticated(page)
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/policies/current')) return json(route, { policies: [] })
    if (path.endsWith('/interviews/10000000-0000-0000-0000-000000000000')) {
      return json(route, snapshot('IN_PROGRESS'), 200, { ETag: '"v4"' })
    }
    return json(route, { code: 404, msg: `未模拟 ${path}` }, 404)
  })

  await page.goto('/interviews/10000000-0000-0000-0000-000000000000')

  await expect(page.getByRole('button', { name: '跳过' }).last()).toBeVisible()
  await expect(page.getByRole('button', { name: '结束并查看反馈' }).last()).toBeVisible()
})
