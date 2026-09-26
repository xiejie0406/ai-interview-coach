import { _electron as electron } from 'playwright'
import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'

const required = ['ADEN_E2E_API_ORIGIN', 'ADEN_E2E_WORKSPACE_NAME', 'ADEN_E2E_RUNNER_TOKEN']
for (const name of required) if (!process.env[name]) throw new Error(`缺少 ${name}`)

const profileRoot = resolve('test-results')
const profile = mkdtempSync(resolve(profileRoot, 'aden-e2e-profile-'))
const installedExe = process.env.ADEN_E2E_DESKTOP_EXE
const evidenceDir = process.env.ADEN_E2E_EVIDENCE_DIR
if (evidenceDir) mkdirSync(evidenceDir, { recursive: true })
let app
let page
const rendererErrors = []
try {
  app = await electron.launch({
    executablePath: installedExe || resolve('node_modules/electron/dist/electron.exe'),
    args: installedExe ? [`--user-data-dir=${profile}`] : [`--user-data-dir=${profile}`, resolve('out/main/index.js')],
    env: { ...process.env, ADEN_API_BASE_URL: process.env.ADEN_E2E_API_ORIGIN }
  })
  page = await app.firstWindow()
  page.on('console', (message) => {
    process.stderr.write(`[renderer:${message.type()}] ${message.text()}\n`)
    if (message.type() === 'error') rendererErrors.push(new Error(message.text()))
  })
  page.on('pageerror', (error) => {
    rendererErrors.push(error)
    process.stderr.write(`[renderer:error] ${error.stack ?? error.message}\n`)
  })
  await page.getByRole('heading', { name: '登录 Aden' }).waitFor()
  await capture('ADEN-PKG-UAT-03-login.png')
  await page.getByLabel('用户名').fill('aden_e2e')
  await page.getByLabel('密码').fill('admin123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.getByRole('heading', { name: '任务中心' }).waitFor()
  await page.getByText(process.env.ADEN_E2E_WORKSPACE_NAME, { exact: false }).first().waitFor()
  await capture('ADEN-PKG-UAT-03-workspace.png')

  await createAndSubmit(page, 'E2E 正常成功', '执行确定性成功 fixture', '成功')
  runSimulator()
  await taskCard(page, 'E2E 正常成功').getByText('SUCCEEDED', { exact: true }).waitFor({ timeout: 20_000 })
  await capture('ADEN-PKG-UAT-04-succeeded.png')

  await createAndSubmit(page, 'E2E 安全点取消', '执行可取消 fixture', '安全点取消')
  const cancelCard = taskCard(page, 'E2E 安全点取消')
  page.once('dialog', (dialog) => dialog.accept())
  await cancelCard.getByRole('button', { name: '请求取消' }).click()
  runSimulator()
  await cancelCard.getByText('CANCELED', { exact: true }).waitFor({ timeout: 20_000 })
  await capture('ADEN-PKG-UAT-05-canceled.png')

  await page.getByRole('button', { name: '退出登录' }).click()
  await page.getByRole('heading', { name: '登录 Aden' }).waitFor()
  await capture('ADEN-PKG-UAT-03-logout.png')
  await page.waitForTimeout(250)
  if (rendererErrors.length > 0) throw new Error(`renderer 存在 ${rendererErrors.length} 个未处理异常`)
  if (evidenceDir) writeFileSync(join(evidenceDir, 'e2e-result.json'), JSON.stringify({
    result: 'Pass',
    installedExe: installedExe || null,
    rendererUrl: page.url(),
    syntheticTasks: ['E2E 正常成功', 'E2E 安全点取消'],
    finalStates: ['SUCCEEDED', 'CANCELED'],
    completedAt: new Date().toISOString()
  }, null, 2))
  process.stdout.write('ADEN_SYNTHETIC_E2E_OK\n')
} catch (error) {
  if (page) {
    process.stderr.write(`[renderer:url] ${page.url()}\n`)
    process.stderr.write(`[renderer:text] ${(await page.locator('body').innerText().catch(() => '')).slice(0, 2_000)}\n`)
    await page.screenshot({ path: resolve('test-results/synthetic-e2e-failure.png'), fullPage: true }).catch(() => {})
  }
  throw error
} finally {
  if (app) await app.close()
  if (!profile.startsWith(`${profileRoot}\\`)) throw new Error('拒绝清理 test-results 之外的 Electron profile')
  rmSync(profile, { recursive: true, force: true })
}

async function capture(name) {
  if (evidenceDir) {
    await page.bringToFront()
    await page.screenshot({
      path: join(evidenceDir, name), fullPage: false, animations: 'disabled', timeout: 60_000
    })
  }
}

async function createAndSubmit(page, title, instruction, expectedLabel) {
  await page.getByLabel('标题').fill(title)
  await page.getByLabel('指令').fill(instruction)
  await page.getByLabel('预期结果').selectOption({ label: expectedLabel })
  await page.getByRole('button', { name: '创建 DRAFT' }).click()
  const card = taskCard(page, title)
  await card.getByText('DRAFT', { exact: true }).waitFor()
  await card.getByRole('button', { name: '提交验证' }).click()
  await card.getByText('QUEUED', { exact: true }).waitFor()
}

function taskCard(page, title) {
  return page.locator('.task-card').filter({ hasText: title })
}

function runSimulator() {
  const result = spawnSync('uv', ['run', '--frozen', 'aden-runner', '--simulator-once'], {
    cwd: resolve('../../../python/aden-runner'),
    env: {
      ...process.env,
      ADEN_RUNNER_ORIGIN: process.env.ADEN_E2E_API_ORIGIN,
      ADEN_RUNNER_CREDENTIAL_TOKEN: process.env.ADEN_E2E_RUNNER_TOKEN
    },
    encoding: 'utf8',
    windowsHide: true,
    timeout: 20_000
  })
  if (result.status !== 0) {
    throw new Error(`Runner simulator 失败（exit=${result.status}）\nstdout:\n${result.stdout}\nstderr:\n${result.stderr}`)
  }
}
