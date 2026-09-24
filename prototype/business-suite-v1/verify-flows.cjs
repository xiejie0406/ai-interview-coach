/* 对原型的关键状态迁移做真实浏览器操作；不访问真实业务接口。 */
const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');
const runtime = process.env.PROTOTYPE_NODE_MODULES || 'C:/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
const { chromium } = require(path.join(runtime, 'playwright'));
const executablePath = process.env.PROTOTYPE_BROWSER || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const output = path.join(__dirname, 'output', 'playwright');
const results = [], failures = [], errors = [];
const assert = (value, message) => { if (!value) throw new Error(message); };
async function check(label, callback) {
  try { const detail = await callback(); results.push({ label, result: 'Pass', detail }); console.log('PASS ' + label); }
  catch (e) { failures.push({ label, result: 'Fail', error: e.message }); console.log('FAIL ' + label + ': ' + e.message); }
}
const localState = (page, key) => page.evaluate(k => window.UI.load(k, null), key);
async function route(page, name) { await page.locator('.sidebar a.nav-item[href="#' + name + '"]').click(); await page.waitForTimeout(80); }
async function download(page, selector, filename) {
  const [file] = await Promise.all([page.waitForEvent('download'), page.locator(selector).click()]);
  await file.saveAs(path.join(output, filename)); assert(fs.statSync(path.join(output, filename)).size > 30, '导出为空'); return file.suggestedFilename();
}
(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, executablePath });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'zh-CN', reducedMotion: 'reduce', acceptDownloads: true });
  const p = await context.newPage(); p.setDefaultTimeout(6000);
  p.on('pageerror', e => errors.push(e.message));
  p.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  try {
    await p.goto(pathToFileURL(path.join(__dirname, '01-production-scheduling/index.html')).href);
    const psKey = 'business-suite-production-v1.2';
    await check('排程 / 有冲突时禁止发布', async () => {
      await route(p, 'versions'); await p.locator('[data-action="publish"]').click();
      assert((await p.locator('#ui-modal').innerText()).includes('暂不可发布'), '存在冲突但未阻止发布');
      assert(await p.locator('[data-action="confirm-publish"]').count() === 0, '阻断弹窗仍可确认发布');
      await p.keyboard.press('Escape');
    });
    await check('排程 / 生成可行草稿并保留锁定任务', async () => {
      await route(p, 'schedule'); await p.locator('[data-action="generate"]').click(); await p.locator('[data-action="run-generate"]').click();
      await p.locator('#ui-modal').waitFor({ state: 'detached' });
      const s = await localState(p, psKey), lock = s.tasks.find(t => t.id === 'OP-001');
      assert(lock.resource === 'CNC-01' && lock.start === 0 && lock.locked, '人工锁定任务被改变');
      assert(s.tasks.length === 9, '应安排9个齐料订单');
      for (const a of s.tasks) for (const b of s.tasks) if (a.id !== b.id && a.resource === b.resource) assert(!(a.start < b.start + b.hours && b.start < a.start + a.hours), '生成计划存在设备重叠');
      assert(s.publishedId === 'V001', '生成草稿擅自改变现场版本');
      return { tasks: s.tasks.length, lockedTask: lock };
    });
    await check('排程 / 发布确认与现场开暂停完工', async () => {
      await route(p, 'versions'); await p.locator('[data-action="publish"]').click();
      await p.locator('[data-action="confirm-publish"]').click();
      assert((await p.locator('#publish-error').innerText()).length > 0, '未确认影响仍允许发布');
      await p.locator('#publish-confirm').check(); await p.locator('[data-action="confirm-publish"]').click();
      assert((await localState(p, psKey)).publishedId !== 'V001', '正式版本未生成');
      await route(p, 'execution'); await p.locator('[data-action="start-task"][data-id="OP-001"]').click();
      await p.locator('[data-action="pause-task"][data-id="OP-001"]').click(); await p.locator('[data-action="confirm-pause"]').click();
      assert((await localState(p, psKey)).execution['OP-001'].status === 'paused', '暂停未记录');
      await p.locator('[data-action="resume-task"][data-id="OP-001"]').click();
      await p.locator('[data-action="complete-task"][data-id="OP-001"]').click(); await p.locator('[data-action="confirm-complete"]').click();
      await p.reload(); assert((await localState(p, psKey)).execution['OP-001'].status === 'done', '完工刷新后丢失');
    });
    await check('排程 / 新订单校验、筛选与下载', async () => {
      await route(p, 'orders'); await p.locator('[data-action="new-order"]').click(); await p.locator('[data-action="create-order"]').click();
      assert((await p.locator('#new-error').innerText()).length > 0, '空产品未校验');
      await p.locator('#new-product').fill('验证连接板'); await p.locator('[data-action="create-order"]').click();
      await p.locator('#order-search').fill('验证连接板'); assert(await p.locator('.data-table tbody tr').count() === 1, '搜索未过滤订单');
      await p.locator('#order-search').fill('不存在的订单'); assert((await p.locator('.empty-state').innerText()).includes('没有'), '搜索空态未显示');
      await route(p, 'schedule'); return download(p, '[data-action="export"]', 'checked-schedule.csv');
    });
    await check('排程 / 手机抽屉可见与键盘关闭', async () => {
      await p.setViewportSize({ width: 390, height: 844 }); await p.locator('.gantt-task[data-id="OP-002"]').click();
      const box = await p.locator('#ui-drawer').boundingBox(); assert(box.x >= 0 && box.x + box.width <= 391, '抽屉越出屏幕');
      await p.screenshot({ path: path.join(output, 'scheduling-mobile-drawer.png'), fullPage: false }); await p.keyboard.press('Escape');
      assert(await p.locator('#ui-drawer').count() === 0, 'Escape未关闭'); await p.setViewportSize({ width: 1440, height: 1000 });
    });

    await p.goto(pathToFileURL(path.join(__dirname, '02-agent-desktop-execution/index.html')).href);
    const adenKey = 'aden-business-prototype-v1'; let newTaskId;
    await check('Aden / 创建任务与暂停、恢复、接管', async () => {
      await p.locator('[data-act="new-task"]').click(); await p.locator('#new-task-name').fill('测试采购500件通勤T恤');
      await p.locator('#new-task-goal').fill('需要500件纯棉通勤T恤，单件预算45元，发往杭州，请比较交期与运费。');
      await p.locator('[data-act="create-task"]').click(); await p.locator('[data-act="pause"]').waitFor();
      newTaskId = (await localState(p, adenKey)).tasks[0].id;
      await p.locator('[data-act="pause"]').click(); assert((await localState(p, adenKey)).tasks[0].status === 'paused', '暂停未生效');
      await p.locator('[data-act="resume"]').first().click(); await p.locator('[data-act="takeover"]').click();
      assert((await localState(p, adenKey)).tasks[0].status === 'takeover', '人工接管未暂停执行');
      await p.locator('[data-act="resume"]').first().click();
      await p.screenshot({ path: path.join(output, 'aden-running-desktop.png'), fullPage: true });
    });
    await check('Aden / 审批拒绝、修改重提、同意与归档', async () => {
      await p.locator('[data-act="advance"]').click(); await p.locator('[data-act="task-approval"]').first().click();
      await p.locator('[data-act="approval-reject"]').click(); assert((await p.locator('#approval-error').innerText()).includes('拒绝原因'), '未填写拒绝原因仍拒绝');
      await p.locator('#approval-comment').fill('请补充收货地址'); await p.locator('[data-act="approval-reject"]').click();
      assert((await localState(p, adenKey)).tasks[0].status === 'failed', '拒绝后仍自动运行');
      await p.locator('[data-act="task-revise"]').click(); await p.locator('#revised-content').fill('采购500件纯棉通勤T恤，预算45元，收货地址杭州滨江，确认报价含税含运费。');
      await p.locator('[data-act="resubmit-approval"]').click(); await p.locator('[data-act="approval-approve"]').click();
      assert((await localState(p, adenKey)).tasks[0].status === 'waiting', '同意后未进入外部等待');
      await p.locator('[data-act="receive"]').click(); await p.locator('[data-act="advance"]').click();
      await p.reload(); assert((await localState(p, adenKey)).tasks.find(t => t.id === newTaskId).status === 'done', '完成状态未持久化');
    });
    await check('Aden / 离线机器阻止任务恢复', async () => {
      await route(p, 'tasks'); await p.locator('[data-act="open-run"][data-id="AD-0910-026"]').first().click(); await p.locator('[data-act="pause"]').click();
      await route(p, 'machines'); await p.locator('[data-act="machine-detail"][data-id="Runner-01"]').click(); await p.locator('[data-act="toggle-machine"]').click();
      await p.locator('.sidebar a[href^="#run"]').click(); await p.locator('[data-act="resume"]').first().click();
      assert((await p.locator('#ui-modal').innerText()).includes('离线'), '离线未被拦截');
      assert((await localState(p, adenKey)).tasks.find(t => t.id === 'AD-0910-026').status === 'paused', '离线时仍恢复运行');
      await p.keyboard.press('Escape'); await route(p, 'machines'); await p.locator('[data-act="machine-detail"][data-id="Runner-01"]').click(); await p.locator('[data-act="toggle-machine"]').click();
    });
    await check('Aden / 客服客户事实与审批后消息一致', async () => {
      await route(p, 'service'); await p.locator('[data-act="customer"][data-id="C-1036"]').click();
      const reply = '周先生您好，白色T恤L码目前可用库存286件，可满足您200件需求，最终库存以确认订单时为准。';
      await p.locator('#reply-draft').fill(reply); await p.locator('[data-act="submit-reply"]').click();
      assert((await p.locator('#ui-drawer').innerText()).includes('杭州艾米'), '审批收件人错误');
      await p.locator('[data-act="approval-approve"]').click();
      const db = await localState(p, adenKey); assert(db.messages['C-1036'].at(-1).content === reply, '已审批内容与会话不一致');
      assert((await p.locator('.conversation').innerText()).includes(reply), '消息未出现在当前会话');
      await route(p, 'procurement'); await p.locator('[data-act="compare"]').click();
      assert((await p.locator('#ui-drawer').innerText()).includes('到仓总金额'), '没有实际比价内容'); await p.keyboard.press('Escape');
      return download(p, '[data-act="export-quotes"]', 'checked-aden-quotes.csv');
    });

    await p.goto(pathToFileURL(path.join(__dirname, '03-ai-fashion-styling-quotation/index.html')).href);
    const fashionKey = 'zhixuan-prototype-v1';
    await check('服装 / 报价金额、折扣、运费和税联动', async () => {
      await p.locator('#open-quote').click();
      assert((await p.locator('#quote-totals').innerText()).includes('26,231.82'), '默认总额应为26231.82');
      await p.locator('#quote-discount').fill('90'); assert((await p.locator('#quote-totals').innerText()).includes('24,869.04'), '90%成交系数计算不符');
      await p.locator('#quote-discount').fill('95');
      return { base: 24120, net: 22914, freight: 300, tax: 3017.82, total: 26231.82 };
    });
    await check('服装 / 库存阻断与报价快照独立', async () => {
      const qty = p.locator('[data-row-qty]').first(); await qty.fill('9999');
      assert(await p.locator('#save-quote').isDisabled(), '超库存仍能保存');
      assert((await p.locator('#quote-errors').innerText()).includes('超出可售库存'), '库存错误未说明');
      await qty.fill('30'); await p.locator('#save-quote').click(); await p.locator('#delivery-version').waitFor();
      assert((await p.locator('.document-total').innerText()).includes('26,231.82'), '保存报价金额改变');
      await p.screenshot({ path: path.join(output, 'fashion-quotation-desktop.png'), fullPage: true });
      await p.locator('[data-go="quotes"]').click(); await p.locator('#quote-discount').fill('90'); await p.locator('[data-version]').first().click();
      assert((await p.locator('.document-total').innerText()).includes('26,231.82'), '旧快照受新折扣影响');
      const saved = (await localState(p, fashionKey)).snapshots[0]; assert(saved.total === 2623182 && saved.quote.discount === 95, '历史金额或条款未冻结');
      await p.reload(); assert((await p.locator('.document-total').innerText()).includes('26,231.82'), '历史快照刷新丢失');
      return download(p, '#delivery-csv', 'checked-fashion-quote.csv');
    });
    await check('服装 / 锁定重搭、商品搜索和空态', async () => {
      await route(p, 'workbench'); await p.locator('[data-lock="0-0"]').click();
      const before = (await localState(p, fashionKey)).looks[0].ids[0];
      await p.locator('[data-remix="0"]').click();
      assert((await localState(p, fashionKey)).looks[0].ids[0] === before, '重搭更改了锁定商品');
      await p.reload(); assert((await localState(p, fashionKey)).looks[0].locks.includes(0), '锁定刷新丢失');
      await route(p, 'catalog'); await p.locator('#catalog-search').fill('不存在的测试商品');
      assert((await p.locator('#catalog-results').innerText()).includes('没有'), '商品搜索未提供空态');
      await p.locator('#catalog-search').fill('黑色'); assert(await p.locator('.catalog-card').count() > 0, '商品搜索未返回匹配项');
    });
    await check('运行时异常', async () => { assert(errors.length === 0, errors.join('\n')); return { errors }; });
  } finally {
    await browser.close();
    fs.writeFileSync(path.join(output, 'flow-results.json'), JSON.stringify({ date: new Date().toISOString(), browser: executablePath, results, failures, errors }, null, 2));
  }
  console.log(JSON.stringify({ passed: results.length, failed: failures.length })); process.exitCode = failures.length ? 1 : 0;
})().catch(e => { console.error(e); process.exitCode = 1; });
