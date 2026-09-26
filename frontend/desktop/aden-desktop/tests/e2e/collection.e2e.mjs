import { _electron as electron } from 'playwright'
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join, resolve, relative, isAbsolute } from 'node:path'
import { deflateSync, inflateRawSync } from 'node:zlib'

// 安装版技术集成测试：商品/API/存储/导出均为真实隔离后端；仅原生文件对话框定向替换。
for (const name of ['ADEN_E2E_API_ORIGIN', 'ADEN_E2E_WORKSPACE_NAME', 'ADEN_E2E_DESKTOP_EXE', 'ADEN_COLLECTION_FIXTURE_ORIGIN']) {
  if (!process.env[name]) throw new Error(`缺少 ${name}；本脚本要求安装版与隔离 collection runner`)
}
const origin = process.env.ADEN_E2E_API_ORIGIN
assert.equal(new URL(origin).hostname, '127.0.0.1', '只允许本轮 loopback 合成后端')
const resultsRoot = resolve('test-results'); mkdirSync(resultsRoot, { recursive: true })
const temporary = mkdtempSync(join(resultsRoot, 'aden-collection-e2e-'))
const evidence = resolve(process.env.ADEN_E2E_EVIDENCE_DIR || join(temporary, '证据'))
mkdirSync(evidence, { recursive: true })
const imagePath = join(temporary, '人工合成商品图.png')
const png = makePng(); writeFileSync(imagePath, png)
const xlsxPath = join(evidence, '安装版商品采集.xlsx'); const zipPath = join(evidence, '安装版商品采集.zip')
const checks = []; const rendererErrors = []
let app; let page; let token; let base; let manualItem; let historyItem
const runStamp = Date.now()
const manualTitle = `=SUM(1,2) 安装版人工商品 ${runStamp}`
const historyTitle = `安装版历史快照合成商品 ${runStamp}`
try {
  const login = await api('/login', 'POST', { username: 'aden_e2e', password: 'admin123', code: '', uuid: '' })
  assert.equal(login.code, 200); assert.ok(login.token); token = login.token
  const spaces = await api('/api/v1/aden/workspaces')
  const workspace = spaces.items.find(s => s.displayName === process.env.ADEN_E2E_WORKSPACE_NAME)
  assert.ok(workspace, '隔离 Workspace 必须存在')
  base = `/api/v1/aden/collection/workspaces/${workspace.workspaceId}`
  app = await electron.launch({ executablePath: process.env.ADEN_E2E_DESKTOP_EXE,
    args: [`--user-data-dir=${join(temporary, 'profile')}`], env: { ...process.env, ADEN_API_BASE_URL: origin } })
  page = await app.firstWindow(); page.setDefaultTimeout(20_000)
  page.on('pageerror', error => rendererErrors.push(error.message))
  page.on('dialog', dialog => dialog.accept())
  await app.evaluate(({ dialog }, paths) => {
    const originalMessageBox = dialog.showMessageBox.bind(dialog)
    dialog.showMessageBox = async (...args) => {
      const options = args[args.length - 1]
      if (options.title === '连接商品采集扩展') return { response: 0, checkboxChecked: false }
      return originalMessageBox(...args)
    }
    dialog.showOpenDialog = async () => ({ canceled: false, filePaths: [paths.imagePath] })
    dialog.showSaveDialog = async (...args) => {
      const options = args[args.length - 1]
      return { canceled: false, filePath: options.defaultPath.endsWith('.zip') ? paths.zipPath : paths.xlsxPath }
    }
  }, { imagePath, zipPath, xlsxPath })
  await page.getByRole('heading', { name: '登录 Aden' }).waitFor()
  await page.getByLabel('用户名').fill('aden_e2e'); await page.getByLabel('密码').fill('admin123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.getByRole('heading', { name: '任务中心' }).waitFor()
  await page.getByText(process.env.ADEN_E2E_WORKSPACE_NAME, { exact: false }).first().waitFor()
  if (process.env.ADEN_COLLECTION_DESKTOP_ONLY === '1') {
    checks.push({ id: 'JD-AC-13', assertion: '本次仅定向桌面复验，未执行扩展探针', result: 'NotRun' })
  } else {
    const { runExtensionProbe } = await import('./collection-extension-probe.mjs')
    const extensionResult = await runExtensionProbe({ app, page, evidenceDir: evidence })
    checks.push({ id: 'JD-AC-13', assertion: '独立扩展闭环探针；详细断言与结果见extensionResult', result: 'Pass', extensionResult })
  }
  if (process.env.ADEN_COLLECTION_EXTENSION_ONLY !== '1') {
  await page.getByRole('link', { name: '商品库', exact: true }).click()
  await page.getByRole('heading', { name: '商品库', exact: true }).waitFor(); await idle()
  await capture('JD-AC-19-新增前商品库.png')

  await page.getByRole('button', { name: '手动新增', exact: true }).click()
  await page.getByRole('button', { name: '保存商品', exact: true }).click()
  assert.equal(await page.getByLabel('标题（必填）').evaluate(input => input.validity.valueMissing), true)
  await page.getByLabel('标题（必填）').fill(manualTitle)
  await page.getByLabel('价格（可留空）').fill('-1')
  await page.getByRole('button', { name: '保存商品', exact: true }).click()
  assert.equal(await page.getByLabel('价格（可留空）').evaluate(input => input.validity.patternMismatch), true)
  assert.equal((await api(`${base}/items?q=${encodeURIComponent(manualTitle)}`)).items.length, 0)
  await capture('JD-AC-19-非法价格拒绝.png')
  await page.getByLabel('价格（可留空）').fill('123.4500')
  await page.getByLabel('来源链接（仅记录）').fill('https://example.invalid/record-only')
  await page.locator('form.collection-form').getByLabel('备注', { exact: true }).fill('合成测试：来源链接不得被自动访问')
  await page.getByRole('button', { name: '保存商品', exact: true }).click()
  await page.locator('.collection-detail').getByRole('heading', { name: manualTitle, exact: true }).waitFor(); await idle()
  manualItem = (await api(`${base}/items?q=${encodeURIComponent(manualTitle)}`)).items[0]
  assert.ok(manualItem); assert.equal(manualItem.platform, 'MANUAL'); assert.equal(manualItem.status, 'SAVED')
  const beforeImage = await api(`${base}/items/${manualItem.itemId}`)
  assert.equal(beforeImage.snapshots[0].fields.price, '123.4500')
  checks.push({ id: 'JD-AC-19', assertion: '安装版人工新增经真实 API/MySQL 回读，来源 MANUAL、价格精确文本保留', result: 'Pass' })

  await detailButton('图片').click()
  await detailButton('添加本地图片').click(); await page.getByText('已保存 1 张本地图片', { exact: true }).waitFor(); await idle()
  await page.locator('.collection-images img').waitFor()
  await page.waitForFunction(() => [...document.querySelectorAll('.collection-images img')].every(i => i.complete && i.naturalWidth > 0))
  const withImage = await api(`${base}/items/${manualItem.itemId}`)
  const snapshot = withImage.snapshots.find(s => s.snapshotId === withImage.snapshotId)
  assert.equal(snapshot.images.length, 1); assert.equal(snapshot.images[0].source, 'MANUAL'); assert.equal(snapshot.images[0].status, 'SAVED')
  const savedImage = await apiBytes(`${base}/assets/${snapshot.images[0].assetId}`)
  assert.deepEqual(savedImage, png, '图片必须保存实际字节')
  await capture('JD-AC-16-人工图片真实回看.png')
  checks.push({ id: 'JD-AC-16', assertion: '人工图片字节与上传文件一致；安装版解码并显示已存图片（不覆盖真页8图2失败场景）', result: 'Pass' })

  await detailButton('设主图').click()
  await page.locator('.collection-detail').getByLabel('备注', { exact: true }).fill('安装版保存的人工备注')
  await page.getByLabel('标签（逗号分隔）').fill('合成,已检查')
  await page.getByLabel('纠错标题').fill('人工纠错标题')
  await page.getByLabel('纠错价格').fill('99.9900')
  await saveCuration()
  await detailButton('图片').click(); await detailButton('移除', true).click(); await saveCuration()
  let curated = await api(`${base}/items/${manualItem.itemId}`)
  assert.deepEqual(curated.curation.removedImageIds, [snapshot.images[0].imageId])
  assert.equal(curated.snapshots[0].fields.title, manualTitle, '整理不能覆盖原始事实')
  await detailButton('图片').click(); await detailButton('恢复', true).click(); await detailButton('设主图').click(); await saveCuration()
  curated = await api(`${base}/items/${manualItem.itemId}`)
  assert.deepEqual(curated.curation.removedImageIds, []); assert.equal(curated.curation.overrides.price, '99.9900')
  await capture('JD-AC-18-人工补充与图片恢复.png')
  checks.push({ id: 'JD-AC-18', assertion: '主图/移除/恢复/人工纠错真实保存，原始字段保持不变', result: 'Pass' })

  // 通过测试 API 准备两个版本；不冒充 Chrome 插件捕获验证。
  const captures = []
  for (const [version, price] of [[1, '11.00'], [2, '22.00']]) {
    const prepared = await api(`${base}/captures`, 'POST', { captureId: randomUUID(), source: 'JD', documentId: `fixture-document-${version}`, parserVersion: 'synthetic-api-test',
      fields: { title: historyTitle, sku: '90071992547409931234', price, priceText: `合成价格 ${price}`, sourceUrl: `${process.env.ADEN_COLLECTION_FIXTURE_ORIGIN}/item/90071992547409931234.html` },
      blocks: [{ type: 'text', text: `版本 ${version} 的已加载商品内容 <script>不得执行</script>` }], images: [], completeness: 'LOADED_ONLY' })
    await api(`${base}/captures/${prepared.snapshotId}/complete`, 'POST', { generation: prepared.generation, failures: [] })
    captures.push(prepared)
  }
  assert.equal(captures[0].itemId, captures[1].itemId); historyItem = captures[1]
  await page.getByRole('button', { name: '关闭详情', exact: true }).click()
  await page.getByRole('button', { name: '查询 / 刷新', exact: true }).click(); await idle()
  await page.getByRole('button', { name: historyTitle, exact: true }).click(); await idle()
  await page.getByLabel('采集版本').selectOption(captures[0].snapshotId)
  await detailButton('内容快照').click()
  await page.getByText('版本 1 的已加载商品内容 <script>不得执行</script>', { exact: true }).waitFor()
  assert.equal(await page.locator('.collection-detail form').count(), 0, '历史快照不得出现当前整理表单')
  await capture('JD-AC-17-历史版本只读回看.png')
  checks.push({ id: 'JD-AC-17', assertion: '测试 API 准备同 SKU 两次捕获，安装版切换旧版本并按文本回看；不证明真实插件采集', result: 'Pass' })

  await page.getByRole('button', { name: '关闭详情', exact: true }).click()
  await page.getByRole('checkbox', { name: `选择 ${manualTitle}`, exact: true }).check()
  await page.getByRole('button', { name: '移入回收站', exact: true }).click(); await idle()
  assert.equal((await api(`${base}/items/${manualItem.itemId}`)).deleted, true)
  assert.equal(await page.getByRole('button', { name: manualTitle, exact: true }).count(), 0)
  await page.getByLabel('范围').selectOption({ label: '回收站' }); await idle()
  await page.getByRole('checkbox', { name: `选择 ${manualTitle}`, exact: true }).check()
  await capture('JD-AC-20-回收站.png')
  await page.getByRole('button', { name: '恢复所选', exact: true }).click(); await idle()
  await page.getByLabel('范围').selectOption({ label: '商品库' }); await idle()
  assert.equal((await api(`${base}/items/${manualItem.itemId}`)).deleted, false)
  await page.getByRole('button', { name: manualTitle, exact: true }).waitFor()
  checks.push({ id: 'JD-AC-20', assertion: '安装版删除/回收站/恢复与真实后端状态一致；未测试并发迟到上传', result: 'Pass' })

  await page.getByRole('checkbox', { name: `选择 ${manualTitle}`, exact: true }).check()
  await page.getByRole('checkbox', { name: `选择 ${historyTitle}`, exact: true }).check()
  await page.getByRole('button', { name: '导出 Excel', exact: true }).click(); await page.getByText('导出文件已保存', { exact: true }).waitFor(); await idle()
  const workbook = unzip(readFileSync(xlsxPath)); validateWorkbook(workbook)
  await page.getByRole('button', { name: 'Excel + 图片 ZIP', exact: true }).click(); await page.getByText('导出文件已保存', { exact: true }).waitFor(); await idle()
  const bundle = unzip(readFileSync(zipPath)); validateWorkbook(unzip(bundle.get('商品采集.xlsx')))
  const exportedImages = [...bundle.entries()].filter(([name]) => name.startsWith('图片/'))
  assert.equal(exportedImages.length, 1); assert.deepEqual(exportedImages[0][1], png)
  const jsonSnapshots = [...bundle.entries()].filter(([name]) => name.startsWith('内容快照/'))
  assert.equal(jsonSnapshots.length, 2)
  const frozen = jsonSnapshots.map(([, bytes]) => JSON.parse(bytes.toString('utf8')))
  assert.equal(frozen.find(s => s.itemId === manualItem.itemId).curation.overrides.title, '人工纠错标题')
  assert.equal(frozen.find(s => s.itemId === historyItem.itemId).snapshot.fields.sku, '90071992547409931234')
  await capture('JD-AC-21-导出成功.png')
  checks.push({ id: 'JD-AC-21', assertion: '真实 XLSX ZIP 逐项解包：四工作表/两商品/精确长SKU/公式文本/一张图片字节/两快照JSON，未测试并发撤权', result: 'Pass' })

  await page.getByRole('button', { name: manualTitle, exact: true }).click(); await idle()
  await app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].setContentSize(1024, 900))
  await capture('JD-AC-24-安装版1024商品库.png')
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), '1024视口不应整页横向溢出')
  await app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].setContentSize(1440, 1000))
  await capture('JD-AC-24-安装版1440商品库.png')
  checks.push({ id: 'JD-AC-24', assertion: '指定安装版执行上述闭环；1024/1440截图；原生文件选择与保存对话框定向替换，不覆盖安装升级卸载或真实Chrome扩展', result: 'Pass' })
  }
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await page.getByRole('heading', { name: '登录 Aden' }).waitFor()
  assert.deepEqual(rendererErrors, [])
  writeFileSync(join(evidence, 'collection-e2e-result.json'), JSON.stringify({ result: 'Pass', installedExe: process.env.ADEN_E2E_DESKTOP_EXE,
    syntheticOnly: true, nativeFileDialogsSubstituted: true, nativePairDialogSubstituted: true, checks, rendererErrors, completedAt: new Date().toISOString() }, null, 2))
  process.stdout.write('ADEN_COLLECTION_E2E_OK\n')
} catch (error) {
  if (page) await page.screenshot({ path: join(evidence, 'collection-e2e-failure.png'), fullPage: true }).catch(() => {})
  writeFileSync(join(evidence, 'collection-e2e-result.json'), JSON.stringify({ result: 'Fail', checks, error: error.message, rendererErrors, completedAt: new Date().toISOString() }, null, 2))
  throw error
} finally {
  if (app) await app.close()
  const part = relative(resultsRoot, temporary)
  if (part.startsWith('..') || isAbsolute(part)) throw new Error('拒绝清理 test-results 之外目录')
  // 默认无指定证据目录时保留证据，只清理独立 Electron profile 和合成上传图。
  const profile = join(temporary, 'profile'); rmSync(profile, { recursive: true, force: true }); rmSync(imagePath, { force: true })
}

async function api(path, method = 'GET', body) {
  const response = await fetch(origin + path, { method, redirect: 'error', headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body === undefined ? undefined : JSON.stringify(body) })
  if (!response.ok) throw new Error(`合成API ${method} ${path} 返回 ${response.status}: ${(await response.text()).slice(0, 600)}`)
  return response.json()
}
async function apiBytes(path) {
  const response = await fetch(origin + path, { redirect: 'error', headers: { Authorization: `Bearer ${token}` } })
  assert.equal(response.status, 200); return Buffer.from(await response.arrayBuffer())
}
async function idle() { await page.getByText('正在处理，请稍候…', { exact: true }).waitFor({ state: 'hidden' }); const errors = await page.locator('[role=alert]').allTextContents(); assert.deepEqual(errors, [], `界面错误：${errors.join('；')}`) }
async function capture(name) {
  await app.evaluate(({ BrowserWindow }) => { const window = BrowserWindow.getAllWindows()[0]; if (window.isMinimized()) window.restore(); window.show(); window.focus() })
  await page.bringToFront()
  await page.screenshot({ path: join(evidence, name), timeout: 15_000 })
}
function detailButton(name, exact = true) { return page.locator('.collection-detail').getByRole('button', { name, exact }) }
async function saveCuration() { await detailButton('保存人工补充及图片整理').click(); await page.getByText('人工补充已保存，原始快照保持不变', { exact: true }).waitFor(); await idle() }

function unzip(bytes) {
  assert.ok(Buffer.isBuffer(bytes), 'ZIP 文件必须存在')
  let end = bytes.length - 22
  while (end >= Math.max(0, bytes.length - 65557) && bytes.readUInt32LE(end) !== 0x06054b50) end--
  assert.ok(end >= 0, '缺少 ZIP 中央目录')
  const count = bytes.readUInt16LE(end + 10); let offset = bytes.readUInt32LE(end + 16); const entries = new Map()
  for (let i = 0; i < count; i++) {
    assert.equal(bytes.readUInt32LE(offset), 0x02014b50)
    const method = bytes.readUInt16LE(offset + 10); const size = bytes.readUInt32LE(offset + 20)
    const nameLength = bytes.readUInt16LE(offset + 28); const extraLength = bytes.readUInt16LE(offset + 30); const commentLength = bytes.readUInt16LE(offset + 32)
    const local = bytes.readUInt32LE(offset + 42); const name = bytes.subarray(offset + 46, offset + 46 + nameLength).toString('utf8')
    assert.ok(!name.startsWith('/') && !name.includes('..') && !name.includes('\\'), 'ZIP 文件名必须安全')
    const start = local + 30 + bytes.readUInt16LE(local + 26) + bytes.readUInt16LE(local + 28)
    const compressed = bytes.subarray(start, start + size)
    assert.ok(method === 0 || method === 8, '仅支持普通 ZIP 压缩')
    const raw = method === 0 ? compressed : inflateRawSync(compressed)
    assert.equal(raw.length, bytes.readUInt32LE(offset + 24)); assert.equal(crc32(raw), bytes.readUInt32LE(offset + 16))
    entries.set(name, raw); offset += 46 + nameLength + extraLength + commentLength
  }
  return entries
}
function validateWorkbook(entries) {
  const workbook = entries.get('xl/workbook.xml').toString('utf8')
  for (const name of ['商品汇总', '图片清单', '快照说明', '异常清单']) assert.ok(workbook.includes(name), `缺少工作表 ${name}`)
  const strings = entries.get('xl/sharedStrings.xml').toString('utf8')
  for (const value of [manualTitle, '人工纠错标题', '90071992547409931234', '安装版保存的人工备注']) assert.ok(strings.includes(value), `导出缺少 ${value}`)
  const products = entries.get('xl/worksheets/sheet1.xml').toString('utf8')
  assert.equal((products.match(/<row\b/g) || []).length, 3, '商品汇总应为表头+两商品')
  for (const [name, bytes] of entries) if (/xl\/worksheets\/sheet\d+\.xml/.test(name)) assert.ok(!/<f(?:\s|>)/.test(bytes.toString('utf8')), '页面字符串不得变成Excel公式')
}
function crc32(bytes) { let crc = 0xffffffff; for (const byte of bytes) { crc ^= byte; for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0) } return (crc ^ 0xffffffff) >>> 0 }
function makePng() {
  const chunk = (name, data) => { const type = Buffer.from(name); const length = Buffer.alloc(4); length.writeUInt32BE(data.length); const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(Buffer.concat([type, data]))); return Buffer.concat([length, type, data, crc]) }
  const header = Buffer.alloc(13); header.writeUInt32BE(2, 0); header.writeUInt32BE(2, 4); header[8] = 8; header[9] = 2
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', header), chunk('IDAT', deflateSync(Buffer.from([0, 0, 128, 64, 255, 200, 100, 0, 255, 200, 100, 0, 128, 64]))), chunk('IEND', Buffer.alloc(0))])
}
