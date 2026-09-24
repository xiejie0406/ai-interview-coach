/* 现场闭环使用真实页面操作；evaluate 仅读取状态用于断言。 */
const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');
const { chromium } = require(path.join(process.env.PROTOTYPE_NODE_MODULES || 'C:/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules', 'playwright'));
const output = path.join(__dirname, 'output', 'playwright');
fs.mkdirSync(output, { recursive: true });
const results = [], errors = [];
const assert = (value, message) => { if (!value) throw new Error(message); };
async function check(label, fn) { const detail = await fn(); results.push({ label, result: 'Pass', detail }); console.log('PASS ' + label); }
const url = pathToFileURL(path.join(__dirname, 'index.html')).href;
(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PROTOTYPE_BROWSER || 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'zh-CN', timezoneId: 'Asia/Shanghai', reducedMotion: 'reduce', acceptDownloads: true });
  const page = await context.newPage(); page.setDefaultTimeout(7000);
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  const nav = view => page.locator('.nav-item[href="#' + view + '"]').click();
  const action = (name, id) => page.locator('[data-action="' + name + '"]' + (id ? '[data-id="' + id + '"]' : '')).first().click();
  const submit = () => page.locator('.ops-form button[type="submit"]').click();
  const snapshot = async name => { await page.waitForSelector('.ui-toast', { state: 'detached', timeout: 7000 }); await page.screenshot({ path: path.join(output, 'operations-' + name + '.png'), fullPage: true, animations: 'disabled' }); };
  const readTask = id => page.evaluate(taskId => JSON.parse(JSON.stringify(A.state.tasks.find(t => t.id === taskId))), id);
  async function clock(value) { await action('ops-clock'); await page.locator('.ops-form input[name="time"]').fill(value); await submit(); await page.waitForSelector('.ui-overlay', { state: 'detached' }); }
  async function start(id) { await action('ops-start', id); await submit(); await page.waitForSelector('.ui-overlay', { state: 'detached' }); }
  async function report(id, good, bad = 0, advance = false, expectFailure = false) {
    await action('ops-report', id); await page.locator('.ops-form input[name="good"]').fill(String(good)); await page.locator('.ops-form input[name="bad"]').fill(String(bad));
    if (bad) await page.locator('.ops-form textarea[name="reason"]').fill('外观检验不合格，待返工复检');
    if (advance) { const input = page.locator('.ops-form input[name="advance"]'); if (await input.count()) await input.check(); }
    await submit();
    if (expectFailure) { const message = await page.locator('[data-ops-error]').innerText(); assert(message.length > 0, '失败应有原因'); await page.keyboard.press('Escape'); return message; }
    await page.waitForSelector('.ui-overlay', { state: 'detached' });
  }
  try {
    await page.goto(url); await page.waitForSelector('.nav-item'); await nav('execution');
    await check('前置未放行开工失败，时间与任务状态不变', async () => {
      const before = await page.evaluate(() => ({ now: A.state.now, tasks: JSON.stringify(A.state.tasks), records: JSON.stringify(A.state.operationRecords || []) }));
      await action('ops-start', 'B10'); await submit(); const message = await page.locator('[data-ops-error]').innerText();
      assert(message.includes('前置'), message); await page.keyboard.press('Escape');
      const after = await page.evaluate(() => ({ now: A.state.now, tasks: JSON.stringify(A.state.tasks), records: JSON.stringify(A.state.operationRecords || []) }));
      assert(JSON.stringify(before) === JSON.stringify(after), '拒绝开工改变了状态'); return { message, now: after.now };
    });
    await check('A10开工、暂停与恢复有连续审计记录', async () => {
      await start('A10'); await clock('2026-09-14T08:30'); await action('ops-pause', 'A10'); await page.locator('.ops-form select[name="reason"]').selectOption('质量异常'); await page.locator('.ops-form textarea[name="detail"]').fill('核对首件后立即恢复'); await submit();
      assert((await readTask('A10')).status === 'paused', '暂停未生效'); await action('ops-resume', 'A10');
      const t = await readTask('A10'); assert(t.status === 'running', '恢复未生效'); assert(t.activity.length === 2, '未保留暂停前后活动段'); return { activity: t.activity };
    });
    await check('A10合格50件释放跨产品B10，时间经过不代替报工', async () => {
      await clock('2026-09-14T10:00');
      await action('ops-start', 'B10'); await submit(); assert((await page.locator('[data-ops-error]').innerText()).includes('前置'), '只有时间经过不应放行'); await page.keyboard.press('Escape');
      await report('A10', 50); await start('B10'); assert((await readTask('A10')).good === 50, 'A10数量错误'); assert((await readTask('B10')).status === 'running', 'B10未开工'); await snapshot('half-release'); return { aGood: 50, bStatus: 'running' };
    });
    await check('两产品加工完成后同炉成员同步开工', async () => {
      await clock('2026-09-14T12:00'); await report('A10', 50); await report('B10', 100); await start('A20');
      const state = await page.evaluate(() => ({ now: A.state.now, a: A.state.tasks.find(t => t.id === 'A20').status, b: A.state.tasks.find(t => t.id === 'B20').status }));
      assert(state.now === 300 && state.a === 'running' && state.b === 'running', '共享批未同步开工'); return state;
    });
    await check('自动批次卸料前报工失败且数量不变', async () => {
      await clock('2026-09-14T15:30'); const before = await readTask('A20'); const message = await report('A20', 100, 0, false, true); const after = await readTask('A20');
      assert(before.good === after.good && before.reported === after.reported, '拒绝报工改变数量'); assert(message.includes('卸载') || message.includes('不足'), message); return { message };
    });
    await check('同炉卸料后分别回记成员，3机时和1人时去重', async () => {
      await clock('2026-09-14T16:00'); await report('A20', 100); await report('B20', 100);
      const data = await page.evaluate(() => { const ids = new Set(['A20', 'B20']); const sum = type => Operations.actualSegments(type, [-480, 960], ids).reduce((n, s) => n + s.end - s.start, 0); return { personMinutes: sum('person'), machineMinutes: sum('machine') }; });
      assert(data.personMinutes === 60 && data.machineMinutes === 180, JSON.stringify(data)); await snapshot('shared-batch'); return data;
    });
    await check('末端不良阻止订单合格齐套，B次日完成', async () => {
      await start('A30'); await clock('2026-09-14T17:00'); await report('A30', 98, 2); await start('B30'); await report('B30', 100, 0, true);
      const order = await page.evaluate(() => Engine.metrics(A.state, A.state.published.assignments).orders.find(o => o.id === 'O-100'));
      assert(order.good === 198 && order.qty === 200, JSON.stringify(order)); return order;
    });
    let recoveryId;
    await check('不良处置创建返工任务并追溯原任务', async () => {
      await action('ops-dispose', 'A30'); await page.locator('.ops-form select[name="kind"]').selectOption('rework'); await page.locator('.ops-form input[name="qty"]').fill('2'); await page.locator('.ops-form textarea[name="reason"]').fill('两件外观返修后复检'); await submit(); await page.waitForSelector('.ui-overlay', { state: 'detached' });
      const data = await page.evaluate(() => ({ source: A.state.tasks.find(t => t.id === 'A30'), recovery: A.state.tasks.find(t => t.recoveryOf === 'A30'), published: A.state.published.assignments }));
      recoveryId = data.recovery.id; assert(data.source.disposedBad === 2 && data.source.good === 98, '处置不应直接增加合格'); assert(!data.published.some(a => a.taskId === recoveryId), '返工未发布前进入正式派工'); return { source: data.source.id, recovery: recoveryId };
    });
    await check('返工重新排程发布为正式任务', async () => {
      await nav('schedule'); await action('run-plan'); await page.waitForFunction(() => !A.planning); await action('review-publish');
      await page.locator('#publish-note').fill('末端2件返工新增派工，原正式执行记录保持'); await page.locator('#publish-confirm').check(); await action('confirm-publish'); await page.waitForSelector('.ui-overlay', { state: 'detached' });
      const data = await page.evaluate(id => ({ version: A.state.published.id, plan: A.state.published.assignments.find(a => a.taskId === id) }), recoveryId); assert(data.plan, '返工没有正式安排'); assert(data.version === 'V02', '未形成新版本'); await nav('execution'); return data;
    });
    await check('返工实际开工报合格后原订单200件齐套，不重复计数', async () => {
      await start(recoveryId); await report(recoveryId, 2, 0, true);
      const data = await page.evaluate(() => ({ source: A.state.tasks.find(t => t.id === 'A30'), order: Engine.metrics(A.state, A.state.published.assignments).orders.find(o => o.id === 'O-100'), records: A.state.operationRecords.length }));
      assert(data.source.good === 98 && data.source.recoveredGood === 2, '原始良品或回收量错误'); assert(data.order.good === 200, '最终合格量错误或被重复累加'); await snapshot('recovery-complete'); return { sourceGood: data.source.good, recovered: data.source.recoveredGood, orderGood: data.order.good, records: data.records };
    });
    await check('日报数量跨日归属，人工与设备占用关联执行', async () => {
      await nav('daily'); await page.locator('[data-ops-filter="date"]').fill('2026-09-14'); await page.locator('[data-ops-filter="date"]').dispatchEvent('change');
      const first = await page.evaluate(() => { const d = Operations.dailyData(); return { finalGood: d.finalGood, person: d.actualPerson, machine: d.actualMachine }; });
      assert(first.finalGood === 98 && first.person === 480 && first.machine === 540, JSON.stringify(first));
      await page.locator('[data-ops-filter="date"]').fill('2026-09-15'); await page.locator('[data-ops-filter="date"]').dispatchEvent('change');
      const next = await page.evaluate(() => { const d = Operations.dailyData(); return { finalGood: d.finalGood, recoveryRows: d.rows.filter(g => g.tasks.some(t => t.recoveryOf)).length }; });
      assert(next.finalGood === 102 && next.recoveryRows === 1, JSON.stringify(next)); await snapshot('daily-recovery'); return { first, next };
    });
    await check('多技能人力按员工去重，半日累积守恒', async () => {
      await nav('capacity'); await page.locator('[data-ops-filter="granularity"]').selectOption('half');
      const data = await page.evaluate(() => { const d = Operations.capacityData(); return { people: d.people.length, available: d.available, sum: d.buckets.reduce((n, b) => n + b.available, 0), slots: d.buckets.length, booked: d.booked }; });
      assert(data.available === data.people * 40 * 60 && data.sum === data.available && data.slots === 10, JSON.stringify(data)); await snapshot('capacity-half-days'); return data;
    });
    await check('恢复旧版只形成新草稿，正式版本与实际不变', async () => {
      const before = await page.evaluate(() => ({ published: JSON.stringify(A.state.published), actual: JSON.stringify(A.state.tasks.map(t => [t.id, t.good, t.bad, t.status, t.recoveredGood])), records: JSON.stringify(A.state.operationRecords) }));
      await nav('versions'); await action('ops-restore', 'V01'); await submit(); await page.waitForSelector('.ui-overlay', { state: 'detached' });
      const after = await page.evaluate(() => ({ published: JSON.stringify(A.state.published), actual: JSON.stringify(A.state.tasks.map(t => [t.id, t.good, t.bad, t.status, t.recoveredGood])), records: JSON.stringify(A.state.operationRecords) }));
      assert(JSON.stringify(before) === JSON.stringify(after), '恢复草稿改写了正式计划或实际历史'); return { current: 'V02', restoredDraft: 'V01' };
    });
    await check('审计筛选和实际CSV下载', async () => {
      await nav('audit'); await page.locator('[data-ops-filter="type"]').selectOption('report');
      const text = await page.locator('main').innerText(); assert(text.includes('A10') && text.includes('A30'), '报工审计缺任务');
      const wait = page.waitForEvent('download'); await action('ops-audit-csv'); const download = await wait; await download.saveAs(path.join(output, 'operations-audit.csv')); return { file: 'operations-audit.csv' };
    });
    await check('刷新后生产与回收数量持久化', async () => { await page.reload(); await page.waitForSelector('.nav-item'); const t = await readTask('A30'); assert(t.good === 98 && t.recoveredGood === 2, '刷新丢失实际'); return { good: t.good, recovered: t.recoveredGood }; });
    await check('浏览器运行无异常', async () => { assert(!errors.length, errors.join('\n')); return { errors }; });
  } catch (error) {
    results.push({ label: '现场流程中断', result: 'Fail', error: error.message }); console.error(error);
    await page.screenshot({ path: path.join(output, 'operations-failure.png'), fullPage: true }).catch(() => {}); process.exitCode = 1;
  } finally {
    fs.writeFileSync(path.join(output, 'operations-results.json'), JSON.stringify({ at: new Date().toISOString(), results, errors }, null, 2)); await browser.close();
  }
  console.log(JSON.stringify({ pass: results.filter(r => r.result === 'Pass').length, fail: results.filter(r => r.result === 'Fail').length }));
})().catch(error => { console.error(error); process.exitCode = 1; });
