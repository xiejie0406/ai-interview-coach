import { expect, test, type BrowserContext, type Page, type APIRequestContext } from '@playwright/test'

const liveEnabled = process.env.INTERVIEW_LIVE_E2E === '1'
const username = process.env.INTERVIEW_E2E_USERNAME ?? 'admin'
const password = process.env.INTERVIEW_E2E_PASSWORD ?? ''

type LoginResponse = { code: number; msg?: string; token?: string }

async function login(request: APIRequestContext, context: BrowserContext) {
  const response = await request.post('/api/login', {
    data: { username, password, code: '', uuid: '' },
  })
  expect(response.ok()).toBeTruthy()
  const body = await response.json() as LoginResponse
  expect(body.code, body.msg).toBe(200)
  expect(body.token).toBeTruthy()
  await context.addCookies([{
    name: 'Admin-Token',
    value: body.token!,
    domain: '127.0.0.1',
    path: '/',
    sameSite: 'Lax',
  }])
  return body.token!
}

async function createInterview(page: Page, mode: 'TEXT' | 'CASCADE_VOICE') {
  await page.goto('/interviews/new')
  await page.getByLabel('面试模式').selectOption(mode)
  await page.getByLabel('面试时长').fill('5')
  await page.getByRole('button', { name: '生成面试计划' }).click()
  const confirm = page.getByRole('button', { name: '确认并进入面试' })
  await expect(confirm).toBeVisible({ timeout: 30_000 })
  await confirm.click()
  await expect(page).toHaveURL(/\/interviews\/[0-9a-f-]+$/i, { timeout: 30_000 })
  await page.getByRole('button', { name: '开始面试' }).click()
  await expect(page.locator('.question-bubble p').first()).toBeVisible({ timeout: 60_000 })
}

async function completeAndGenerateFeedback(page: Page) {
  await page.getByRole('button', { name: '结束并查看反馈' }).first().click()
  await expect(page).toHaveURL(/\/feedback$/, { timeout: 30_000 })
  await expect(page.getByText('面试已经结束，可以基于已确认回答生成反馈。')).toBeVisible()
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '生成反馈报告' }).click()
  await expect(page.locator('.dimension').first()).toBeVisible({ timeout: 90_000 })
  await expect(page.getByText(/模型：deepseek/i)).toBeVisible()
}

test.describe('真实本地面试链路', () => {
  test.skip(!liveEnabled, '仅在显式开启真实供应商验收时运行')
  test.describe.configure({ mode: 'serial', timeout: 180_000 })

  test('文字回答、真实追问、结束和反馈报告', async ({ page, request, context }) => {
    await login(request, context)
    await createInterview(page, 'TEXT')
    await page.getByLabel('面试回答').fill(
      'HashMap 并发写入可能出现数据覆盖和结构不一致。应优先使用 ConcurrentHashMap，并注意复合操作仍需使用原子 API。',
    )
    await page.getByRole('button', { name: '发送', exact: true }).click()
    await expect(page.locator('.question-bubble p').nth(1)).toBeVisible({ timeout: 60_000 })
    await completeAndGenerateFeedback(page)
  })

  test('测试麦克风、真实 WebSocket、ASR、TTS 和反馈报告', async ({ page, request, context }) => {
    test.skip(!process.env.INTERVIEW_E2E_AUDIO_FILE, '缺少测试麦克风 WAV 文件')
    const token = await login(request, context)
    await createInterview(page, 'CASCADE_VOICE')
    await page.getByText('我同意本轮语音采集和模型转写').click()
    await page.getByRole('button', { name: '开始语音回答' }).click()
    await expect(page.getByText('正在录音', { exact: true })).toBeVisible({ timeout: 30_000 })
    await page.waitForTimeout(5_000)
    await page.getByRole('button', { name: '结束语音回答' }).click()
    await expect(page.getByText('语音已转写，请检查后发送')).toBeVisible({ timeout: 60_000 })
    await expect(page.getByLabel('面试回答')).not.toHaveValue('')
    await page.getByRole('button', { name: '确认转写并发送' }).click()
    await expect(page.locator('.question-bubble p').nth(1)).toBeVisible({ timeout: 90_000 })

    await expect.poll(async () => {
      const response = await request.get('/api/v1/voice-capabilities', {
        headers: { Authorization: `Bearer ${token}` },
      })
      if (!response.ok()) return 'HTTP_' + response.status()
      const body = await response.json()
      return `${body.providerObservations?.ASR?.status}/${body.providerObservations?.TTS?.status}`
    }, { timeout: 60_000 }).toBe('PASS/PASS')

    await completeAndGenerateFeedback(page)
  })
})
