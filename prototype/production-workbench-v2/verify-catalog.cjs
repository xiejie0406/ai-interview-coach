/* 主数据界面验收：独立浏览器上下文，真实填写表单，不写入真实业务。 */
const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');
const { chromium } = require(path.join(process.env.PROTOTYPE_NODE_MODULES || 'C:/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules', 'playwright'));
const output = path.join(__dirname, 'output', 'playwright');
fs.mkdirSync(output, { recursive: true });
const url = pathToFileURL(path.join(__dirname, 'index.html')).href;
const results = [], errors = [];
const graphOnly = process.argv.includes('--graph-only');
const copyOnly = process.argv.includes('--copy-only');
const calendarHistoryOnly = process.argv.includes('--calendar-history-only');
function assert(value, message) { if (!value) throw new Error(message); }
async function check(label, fn) { if (graphOnly && !label.includes('共同批次') && !label.includes('浏览器异常')) return; if (copyOnly && !label.includes('返工源单复制') && !label.includes('浏览器异常')) return; if (calendarHistoryOnly && !label.includes('历史日历') && !label.includes('浏览器异常')) return; try { const detail = await fn(); results.push({ label, result: 'Pass', detail }); console.log('PASS ' + label); } catch (error) { results.push({ label, result: 'Fail', error: error.message }); console.log('FAIL ' + label + ': ' + error.message); } }
async function nav(page, view) { await page.locator('.nav-item[href="#' + view + '"]').click(); }
async function action(page, name, id) { await page.locator('[data-action="' + name + '"]' + (id ? '[data-id="' + id + '"]' : '')).first().click(); }
async function save(page) { await page.locator('button[form="catalog-form"]').click(); }
async function close(page) { await page.keyboard.press('Escape'); }
async function snap(page, name) { await page.screenshot({ path: path.join(output, name + '.png'), fullPage: true, animations: 'disabled' }); }
(async function () {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PROTOTYPE_BROWSER || 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  async function fresh(fn) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'zh-CN', timezoneId: 'Asia/Shanghai', reducedMotion: 'reduce' });
    const page = await context.newPage(); page.setDefaultTimeout(6500); page.on('pageerror', e => errors.push(e.message));
    try { await page.goto(url); await page.waitForSelector('.nav-item'); return await fn(page); } finally { await context.close(); }
  }
  try {
    await check('已执行人机时的历史日历保护与未来变更', () => fresh(async page => {
      const fixture = await page.evaluate(() => {
        ['A10', 'B10'].forEach(id => { const t = A.state.tasks.find(x => x.id === id); Object.assign(t, { status: 'done', good: 100, bad: 0, reported: 100, actualStart: id === 'A10' ? 0 : 120, actualEnd: 240, actualRelease: 240, releases: [{ at: 240, good: 100 }], activity: [{ start: id === 'A10' ? 0 : 120, end: 240 }] }); });
        A.state.now = 300; const start = Engine.report(A.state, 'A20', 'start');
        A.state.now = 480; const first = Engine.report(A.state, 'A20', 'report', { good: 100, bad: 0 }); const second = Engine.report(A.state, 'B20', 'report', { good: 100, bad: 0 });
        const ids = new Set(['A20', 'B20']); const personMinutes = Operations.actualSegments('person', [0, 1440], ids).reduce((n, g) => n + g.end - g.start, 0); const machineMinutes = Operations.actualSegments('machine', [0, 1440], ids).reduce((n, g) => n + g.end - g.start, 0);
        A.render(); return { start, first, second, personMinutes, machineMinutes, calendar: JSON.stringify(A.state.resources.find(r => r.id === 'P3')), revision: A.state.revision };
      });
      assert(fixture.start.ok && fixture.first.ok && fixture.second.ok, 'H1实际开报工预置失败：' + JSON.stringify(fixture)); assert(fixture.personMinutes === 60 && fixture.machineMinutes === 180, 'H1应为60人分钟/180机分钟');
      await nav(page, 'people'); await action(page, 'cat-calendar-edit', 'P3'); await page.locator('#cat-add-exception').click(); await page.locator('#cat-off-0-start').fill('2026-09-14T13:00'); await page.locator('#cat-off-0-end').fill('2026-09-14T14:00'); await page.locator('#cat-reason').fill('验收尝试改写历史请假'); await save(page);
      const blocked = await page.locator('.cat-form-error').innerText(); assert(blocked.includes('历史日历'), '过去请假未被阻止');
      const unchanged = await page.evaluate(() => ({ calendar: JSON.stringify(A.state.resources.find(r => r.id === 'P3')), revision: A.state.revision, personMinutes: Operations.actualSegments('person', [0, 1440], new Set(['A20', 'B20'])).reduce((n, g) => n + g.end - g.start, 0) })); assert(unchanged.calendar === fixture.calendar && unchanged.revision === fixture.revision && unchanged.personMinutes === 60, '拒绝后日历或历史人时被改动');
      await snap(page, 'catalog-calendar-history-blocked'); await page.locator('#cat-off-0-start').fill('2026-09-15T09:00'); await page.locator('#cat-off-0-end').fill('2026-09-15T10:00'); await page.locator('#cat-reason').fill('验收未来培训时段'); await save(page);
      const future = await page.evaluate(() => ({ unavailable: A.state.resources.find(r => r.id === 'P3').unavailable, revision: A.state.revision, personMinutes: Operations.actualSegments('person', [0, 1440], new Set(['A20', 'B20'])).reduce((n, g) => n + g.end - g.start, 0) })); assert(future.unavailable.some(w => w[0] === 1500 && w[1] === 1560), '未来无活动时段变更未保存'); assert(future.personMinutes === 60, '未来日历变更影响过去人时');
      await action(page, 'cat-calendar-edit', 'P3'); await page.locator('#cal-0-1-start').fill('14:00'); await page.locator('#cat-reason').fill('验收过去班次变更'); await save(page); assert((await page.locator('.cat-form-error').innerText()).includes('历史日历'), '过去出勤窗口修改未被拒绝'); await close(page);
      await nav(page, 'resources'); await action(page, 'cat-calendar-edit', 'F1'); await page.locator('#cat-add-exception').click(); await page.locator('#cat-off-0-start').fill('2026-09-14T14:00'); await page.locator('#cat-off-0-end').fill('2026-09-14T15:00'); await page.locator('#cat-reason').fill('验收过去设备维修'); await save(page); assert((await page.locator('.cat-form-error').innerText()).includes('历史日历'), '过去设备停机修改未被拒绝');
      const finalMinutes = await page.evaluate(() => Operations.actualSegments('machine', [0, 1440], new Set(['A20', 'B20'])).reduce((n, g) => n + g.end - g.start, 0)); assert(finalMinutes === 180, '拒绝后历史机时改变');
      return { fixture: '前置产出预置；H1通过Engine.report实际开工和报工完成；日历变更经真实UI', personMinutesBefore: fixture.personMinutes, machineMinutesBefore: fixture.machineMinutes, historicalLeaveBlocked: blocked, future, historicalShiftBlocked: true, historicalMaintenanceBlocked: true, machineMinutesAfter: finalMinutes };
    }));
    await check('已执行及返工源单复制隔离', () => fresh(async page => {
      const fixture = await page.evaluate(() => {
        const source = A.state.tasks.filter(t => t.orderId === 'O-100');
        source.forEach(t => Object.assign(t, { status: 'done', good: t.qty, bad: 0, reported: t.qty, actualStart: 1, actualEnd: 2, actualRelease: 2, lastActionAt: 2, activity: [{ start: 1, end: 2, personId: 'P1' }], releases: [{ at: 2, good: t.qty }], recoveredGood: 0, disposedBad: 0, dispositionCount: 0, lock: 'all', lockAssignment: { taskId: t.id, start: 1, end: 2 } }));
        const last = source.find(t => t.id === 'A30'); last.good = 94; last.bad = 6; last.releases = [{ at: 2, good: 94 }];
        const rework = Engine.dispose(A.state, 'A30', 'rework', 2, '复制验收既有返工');
        const scrap = Engine.dispose(A.state, 'A30', 'scrap', 4, '复制验收既有补产');
        last.recoveredGood = 1;
        const snapshot = JSON.stringify(A.state.tasks.filter(t => t.orderId === 'O-100'));
        A.render();
        return { rework, scrap, normalCount: source.length, totalCount: A.state.tasks.filter(t => t.orderId === 'O-100').length, snapshot };
      });
      assert(fixture.rework.ok && fixture.scrap.ok, '返工与补产测试预置失败'); assert(fixture.totalCount > fixture.normalCount, '测试源单没有派生任务');
      await nav(page, 'orders'); await action(page, 'cat-new-order'); await page.locator('#cat-orderId').fill('O-COPY-CLEAN'); await page.locator('#cat-customer').fill('执行隔离复制验收');
      await page.locator('#line-template-0').selectOption('O-100::A'); await page.locator('#line-qty-0').fill('80'); await page.locator('#cat-add-line').click(); await page.locator('#line-template-1').selectOption('O-100::B'); await page.locator('#line-qty-1').fill('60'); await save(page);
      const result = await page.evaluate(() => ({ tasks: A.state.tasks.filter(t => t.orderId === 'O-COPY-CLEAN'), original: JSON.stringify(A.state.tasks.filter(t => t.orderId === 'O-100')), order: A.state.orders.find(o => o.id === 'O-COPY-CLEAN') }));
      assert(result.tasks.length === fixture.normalCount && result.tasks.length === 6, '新订单继承了返工或补产工序'); assert(result.original === fixture.snapshot, '复制修改了原订单执行事实');
      const executionKeys = ['activity', 'releases', 'lastActionAt', 'disposedBad', 'recoveredGood', 'dispositionCount', 'recoveryChainId', 'recoveryOf', 'sourceRejectedTask', 'recoveryReason', 'actualStart', 'actualEnd', 'actualRelease', 'lockAssignment', 'consumed'];
      for (const t of result.tasks) {
        assert(t.status === 'ready' && t.good === 0 && t.bad === 0 && t.reported === 0 && t.lock === 'none' && t.batchId === null, '新任务带有执行状态：' + t.id);
        assert(!executionKeys.some(key => Object.prototype.hasOwnProperty.call(t, key)), '新任务携带执行/返工字段：' + t.id);
        assert(!t.op.includes('返工') && !t.op.includes('补产'), '新任务工艺名称包含派生工序');
        assert(t.deps.every(d => result.tasks.some(x => x.id === d.taskId)), '新订单依赖仍指向原订单');
      }
      const a = result.tasks.find(t => t.op === '精密加工'), b = result.tasks.find(t => t.op === '数控车削'); assert(a.qty === 80 && a.run === 192 && b.qty === 60 && b.deps[0].qty === 40 && b.deps[0].taskId === a.id, '复制后的数量、工时或跨产品门槛错误');
      await action(page, 'cat-order-detail', 'O-COPY-CLEAN'); await snap(page, 'catalog-copy-clean');
      return { fixture: '测试预置已执行源单，Engine.dispose 生成返工及补产链；订单创建通过真实 UI', sourceNormalTasks: fixture.normalCount, sourceAllTasks: fixture.totalCount, newTaskCount: result.tasks.length, excludedExecutionKeys: executionKeys, sourceUnchanged: true, tasks: result.tasks };
    }));
    await check('新增多产品订单与独立工艺实例', () => fresh(async page => {
      await nav(page, 'orders'); await action(page, 'cat-new-order');
      await page.locator('#cat-orderId').fill('O-CAT-01'); await page.locator('#cat-customer').fill('工艺验收客户'); await page.locator('#line-template-0').selectOption('O-100::A'); await page.locator('#line-qty-0').fill('80');
      await page.locator('#cat-add-line').click(); await page.locator('#line-template-1').selectOption('O-100::B'); await page.locator('#line-qty-1').fill('60'); await save(page);
      const data = await page.evaluate(() => { const o = A.state.orders.find(x => x.id === 'O-CAT-01'); return { order: o, tasks: A.state.tasks.filter(t => t.orderId === o.id), revision: A.state.revision }; });
      assert(data.order.lines.length === 2 && data.tasks.length === 6, '产品和工序数量不正确'); assert(data.tasks.every(t => !t.batchId && t.status === 'ready'), '复制任务不应继承批次或已执行状态');
      const source = data.tasks.find(t => t.op === '精密加工'), target = data.tasks.find(t => t.op === '数控车削'); assert(target.deps[0].taskId === source.id && target.deps[0].qty === 40, '跨产品数量依赖未重新映射'); assert(source.run === 192, '数量变更未重算按件时长');
      await action(page, 'cat-order-edit', 'O-CAT-01'); await page.locator('#cat-lineQty-0').fill('120'); await save(page);
      const edited = await page.evaluate(id => A.state.tasks.find(t => t.id === id), source.id); assert(edited.qty === 120 && edited.run === 288, '编辑产品数量未同步工序工作量');
      await action(page, 'cat-order-detail', 'O-CAT-01'); assert((await page.locator('.ui-drawer').innerText()).includes('120'), '订单详情没有更新数量'); await snap(page, 'catalog-order-detail'); return data;
    }));
    await check('工艺参数修改保留有效工时并参与排程', () => fresh(async page => {
      await nav(page, 'routes'); await action(page, 'cat-task-edit', 'A10'); await page.locator('#cat-rate').fill('50'); await page.locator('#cat-setup').fill('15'); await save(page);
      const data = await page.evaluate(() => { const t = A.state.tasks.find(x => x.id === 'A10'); const p = Engine.plan(A.state); return { task: t, assignment: p.assignments.find(x => x.taskId === 'A10'), issues: p.issues }; });
      assert(data.task.run === 120 && data.task.rate === 50 && Number.isFinite(data.task.run), '小时产能未驱动工时变化'); assert(data.assignment.end - data.assignment.start === 135, '新排程未使用准备15+加工120'); return { run: data.task.run, end: data.assignment.end };
    }));
    await check('循环关系拒绝且原依赖不变', () => fresh(async page => {
      await nav(page, 'routes'); await action(page, 'cat-dependency-new'); await page.locator('#cat-target').selectOption('A10'); await page.locator('#cat-source').selectOption('A30'); await save(page);
      const message = await page.locator('.cat-form-error').innerText(); assert(message.includes('循环'), '未指出工艺循环'); assert(await page.evaluate(() => A.state.tasks.find(t => t.id === 'A10').deps.length) === 0, '拒绝后仍更改了依赖'); return { message };
    }));
    await check('数量门槛编辑与共同批次网络层级', () => fresh(async page => {
      await nav(page, 'routes'); await action(page, 'cat-dependency-edit', 'B10::0'); await page.locator('#cat-threshold').fill('40'); await page.locator('#cat-lag').fill('10'); await save(page);
      const dep = await page.evaluate(() => A.state.tasks.find(t => t.id === 'B10').deps[0]); assert(dep.qty === 40 && dep.lag === 10 && !dep.consume, '门槛语义保存错误');
      const layout = await page.evaluate(() => Object.fromEntries(['A20', 'B20', 'A30'].map(id => [id, document.querySelector('.cat-node[data-id="' + id + '"]').offsetLeft]))); assert(layout.A20 === layout.B20 && layout.A30 > layout.A20, '共享批次未在同层，或后继未传播'); await snap(page, 'catalog-routes-final'); return { dep, layout };
    }));
    await check('逐批转序编辑与逐时供给守恒', () => fresh(async page => {
      await nav(page, 'routes'); await page.locator('[data-cat-filter="routeOrder"]').selectOption('O-400'); await action(page, 'cat-dependency-edit', 'T20::0');
      assert(await page.locator('#cat-relation').inputValue() === 'consume', '现有消耗关系未正确显示'); await page.locator('#cat-transferQty').fill('10'); await save(page);
      const data = await page.evaluate(() => { const plan = Engine.plan(A.state); return { dep: A.state.tasks.find(t => t.id === 'T20').deps[0], assignment: plan.assignments.find(a => a.taskId === 'T20'), issues: Engine.validate(A.state, plan.assignments) }; });
      assert(data.dep.consume && data.dep.transferQty === 10 && data.dep.ratio === 1, '逐批消耗参数未保存'); assert(data.assignment.segments.filter(s => s.kind === 'run').length === 6, '应该生成6个转移段'); assert(!data.issues.some(x => x.severity === 'error'), JSON.stringify(data.issues)); assert((await page.locator('main').innerText()).includes('逐批 10 件'), '关系表未区分逐批消耗'); await snap(page, 'catalog-transfer-flow'); return { dep: data.dep, segments: data.assignment.segments };
    }));
    await check('人员技能编辑导致不合格人员无法分配', () => fresh(async page => {
      await nav(page, 'people'); await action(page, 'cat-resource-edit', 'P1'); await page.locator('#cat-skills').fill('精密加工:2'); await save(page);
      const data = await page.evaluate(() => { const p = Engine.plan(A.state); return { issues: p.issues, a10: p.assignments.find(a => a.taskId === 'A10'), revision: A.state.revision }; }); assert(!data.a10, '二级人员仍被安排三级任务'); assert(data.issues.some(i => i.taskId === 'A10'), '未给出A10资源问题'); return data;
    }));
    await check('人员请假日历阻断原计划并影响重排', () => fresh(async page => {
      await nav(page, 'people'); await action(page, 'cat-calendar-edit', 'P1'); await page.locator('#cat-add-exception').click(); await page.locator('#cat-off-0-start').fill('2026-09-14T08:00'); await page.locator('#cat-off-0-end').fill('2026-09-18T17:00'); await page.locator('#cat-reason').fill('验收：整周请假'); await save(page);
      const data = await page.evaluate(() => ({ resource: A.state.resources.find(r => r.id === 'P1'), before: Engine.validate(A.state, A.state.draft.assignments), after: Engine.plan(A.state) })); assert(data.resource.unavailable.length === 1, '请假未保存'); assert(data.before.some(i => i.severity === 'error'), '旧草稿未失效'); assert(!data.after.assignments.some(a => a.taskId === 'A10'), '请假期间仍排给张工'); return { unavailable: data.resource.unavailable, issueCount: data.before.length };
    }));
    await check('设备资料空能力可保存及维修窗口阻断', () => fresh(async page => {
      await nav(page, 'resources'); await action(page, 'cat-resource-edit', 'M1'); await page.locator('#cat-name').fill('M1 维修验收机台'); await save(page); assert(await page.evaluate(() => A.state.resources.find(r => r.id === 'M1').name) === 'M1 维修验收机台', '设备名称无法保存');
      await action(page, 'cat-calendar-edit', 'M1'); await page.locator('#cat-add-exception').click(); await page.locator('#cat-off-0-start').fill('2026-09-14T08:00'); await page.locator('#cat-off-0-end').fill('2026-09-18T17:00'); await page.locator('#cat-reason').fill('验收：主轴维修'); await save(page);
      const data = await page.evaluate(() => Engine.plan(A.state)); assert(!data.assignments.some(a => a.machineId === 'M1'), '维修中的设备仍被分配'); return { issues: data.issues };
    }));
    await check('合法拆批再合批与共享占用去重', () => fresh(async page => {
      await nav(page, 'batches'); await action(page, 'cat-batch-split', 'H1'); await page.locator('#cat-reason').fill('验收重新组合批次'); await save(page); assert(await page.evaluate(() => A.state.batches.length) === 0, '未拆批');
      await action(page, 'cat-batch-new'); await page.locator('#cat-batchId').fill('H-CAT'); await page.locator('#cat-machineId').selectOption('F1'); await page.locator('input[name="members"][value="A20"]').check(); await page.locator('input[name="members"][value="B20"]').check(); await save(page);
      const data = await page.evaluate(() => { const p = Engine.plan(A.state); const a = p.assignments.find(x => x.taskId === 'A20'), b = p.assignments.find(x => x.taskId === 'B20'); return { batch: A.state.batches[0], a, b, issues: Engine.validate(A.state, p.assignments) }; }); assert(data.batch.taskIds.length === 2 && data.a.start === data.b.start && data.a.end === data.b.end, '成员没有共享同一周期'); assert(!data.issues.some(x => x.severity === 'error'), JSON.stringify(data.issues)); await snap(page, 'catalog-batch-final'); return { batch: data.batch, start: data.a.start, end: data.a.end };
    }));
    await check('合批超容量拒绝且成员保持独立', () => fresh(async page => {
      await nav(page, 'batches'); await action(page, 'cat-batch-split', 'H1'); await page.locator('#cat-reason').fill('验收容量约束'); await save(page);
      await nav(page, 'orders'); await action(page, 'cat-order-edit', 'O-100'); await page.locator('#cat-lineQty-0').fill('121'); await save(page);
      await nav(page, 'batches'); await action(page, 'cat-batch-new'); await page.locator('#cat-machineId').selectOption('F1'); await page.locator('input[name="members"][value="A20"]').check(); await page.locator('input[name="members"][value="B20"]').check(); await save(page);
      const message = await page.locator('.cat-form-error').innerText(); assert(message.includes('容量'), '超量未被拒绝'); assert(await page.evaluate(() => A.state.batches.length) === 0, '拒绝仍创建批次'); return { message };
    }));
    await check('不兼容工艺禁止合批', () => fresh(async page => {
      await nav(page, 'batches'); await action(page, 'cat-batch-split', 'H1'); await page.locator('#cat-reason').fill('验收配方不兼容'); await save(page);
      await nav(page, 'routes'); await action(page, 'cat-task-edit', 'B20'); await page.locator('#cat-compatible').fill('HT-220/60'); await save(page);
      await nav(page, 'batches'); await action(page, 'cat-batch-new'); await page.locator('#cat-machineId').selectOption('F1'); await page.locator('input[name="members"][value="A20"]').check(); await page.locator('input[name="members"][value="B20"]').check(); await save(page);
      const message = await page.locator('.cat-form-error').innerText(); assert(message.includes('兼容'), '不同配方未拒绝'); return { message };
    }));
    await check('窄屏多产品表单及日历表单无整体溢出', () => fresh(async page => {
      await nav(page, 'orders'); await action(page, 'cat-new-order'); await page.locator('#cat-add-line').click(); await page.setViewportSize({ width: 390, height: 844 });
      const data = await page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth, dialog: document.querySelector('.ui-panel').getBoundingClientRect().width })); assert(data.scroll <= data.width + 1 && data.dialog <= data.width, '多产品表单窄屏溢出'); await snap(page, 'catalog-order-form-mobile'); await close(page);
      await page.setViewportSize({ width: 1440, height: 1000 }); await nav(page, 'people'); await action(page, 'cat-calendar-edit', 'P1'); await page.locator('#cat-add-exception').click(); await page.setViewportSize({ width: 390, height: 844 });
      const cal = await page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth, bodyScroll: document.querySelector('.ui-panel-body').scrollWidth, bodyWidth: document.querySelector('.ui-panel-body').clientWidth })); assert(cal.scroll <= cal.width + 1 && cal.bodyScroll <= cal.bodyWidth + 1, '日历表单横向溢出'); await snap(page, 'catalog-calendar-mobile'); return { order: data, calendar: cal };
    }));
    await check('主数据流程浏览器异常', async () => { assert(!errors.length, errors.join('\n')); return { errors }; });
  } finally { fs.writeFileSync(path.join(output, calendarHistoryOnly ? 'catalog-calendar-history-results.json' : copyOnly ? 'catalog-copy-results.json' : graphOnly ? 'catalog-graph-results.json' : 'catalog-results.json'), JSON.stringify({ at: new Date().toISOString(), results, errors }, null, 2)); await browser.close(); }
  const fail = results.filter(x => x.result === 'Fail').length; console.log(JSON.stringify({ pass: results.length - fail, fail })); if (fail) process.exitCode = 1;
})().catch(error => { console.error(error); process.exitCode = 1; });
