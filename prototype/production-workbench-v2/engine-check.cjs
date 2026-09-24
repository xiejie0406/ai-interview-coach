/* 本地业务验算：node engine-check.cjs，无浏览器或外部服务。 */
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
vm.runInThisContext(fs.readFileSync(path.join(__dirname, 'vendor/solver.global.js'), 'utf8'));
vm.runInThisContext(fs.readFileSync(path.join(__dirname, 'engine.js'), 'utf8'));
const E = globalThis.Engine;
const copy = x => JSON.parse(JSON.stringify(x));
const task = (s, id) => s.tasks.find(x => x.id === id);
const planOf = (s, id) => s.published.assignments.find(x => x.taskId === id);
const results = [];
const args = process.argv.slice(2), filter = args.find(x => !x.startsWith('--') && args[args.indexOf(x) - 1] !== '--output');
function check(name, fn) { if (filter && !name.includes(filter)) return; try { fn(); results.push({ name, pass: true }); } catch (e) { results.push({ name, pass: false, error: e.stack }); } }
const noErrors = (s, assignments) => assert.deepEqual(E.validate(s, assignments).filter(x => x.severity === 'error'), []);
const has = (issues, code) => assert.ok(issues.some(x => x.code === code), `缺少 ${code}: ${JSON.stringify(issues)}`);

check('首单时间与人机阶段严格复现 PRD 第 13 节', () => {
  const s = E.createState();
  noErrors(s, s.published.assignments);
  assert.deepEqual(['A10', 'B10', 'A20', 'B20', 'A30', 'B30'].map(id => [id, planOf(s, id).start, planOf(s, id).end]), [['A10', 0, 240], ['B10', 120, 240], ['A20', 300, 480], ['B20', 300, 480], ['A30', 480, 540], ['B30', 1440, 1500]]);
  const uses = E.occupied(s.published.assignments);
  assert.equal(uses.filter(x => x.resourceId === 'F1').reduce((n, x) => n + x.end - x.start, 0), 180);
  assert.equal(uses.filter(x => x.resourceId === 'P3').reduce((n, x) => n + x.end - x.start, 0), 60);
  assert.equal(E.metrics(s, s.published.assignments).orders.find(o => o.id === 'O-100').late, 960);
});

check('正排不修改共享输入与正式版本', () => {
  const s = E.createState(), before = JSON.stringify(s), result = E.plan(s);
  noErrors(s, result.assignments); assert.equal(JSON.stringify(s), before); assert.equal(result.method, 'serial-finite-candidates-milp');
});

check('50 件计划门槛 10:00 生效，提前一分钟被拒', () => {
  const s = E.createState();
  assert.equal(E.releaseAt(task(s, 'A10'), planOf(s, 'A10'), 50), 120);
  has(E.adjust(s, 'B10', { start: 119 }).issues, 'DEPENDENCY');
});

check('同批只占一次资源，但部分成员被移动会阻断', () => {
  const s = E.createState(), a = copy(s.published.assignments), b = a.find(x => x.taskId === 'B20');
  b.start++; b.segments[0].start++;
  has(E.validate(s, a), 'BATCH_SYNC');
});

check('自动运行期间王工可另做人工任务，装卸期间不行', () => {
  const s = E.createState(), t = { ...copy(task(s, 'F10')), id: 'W-CLEAN', workshop: '热处理车间', wc: '热处理中心', skill: '工装整理', minLevel: 2, qty: 30, run: 60, rate: 30, interruptible: false };
  s.tasks.push(t); const result = E.plan(s); s.draft.assignments = result.assignments;
  noErrors(s, E.adjust(s, t.id, { start: 330 }).assignments);
  has(E.adjust(s, t.id, { start: 310 }).issues, 'RESOURCE_CONFLICT');
});

check('技能不够的空闲人员不能顶替，改派合格人员可排', () => {
  const s = E.createState();
  has(E.adjust(s, 'C10', { personId: 'P7' }).issues, 'SKILL');
  const r = s.resources.find(r => r.id === 'P7'); r.skills.手工装配 = 3;
  s.resources.find(r => r.id === 'P5').unavailable.push([0, E.HORIZON]);
  const result = E.plan(s); noErrors(s, result.assignments); assert.equal(result.assignments.find(a => a.taskId === 'C10').personId, 'P7');
});

check('请假后寻找其他可用窗口，并反映到下游', () => {
  const s = E.createState(); s.resources.find(r => r.id === 'P2').unavailable.push([120, 240]); s.revision++;
  const result = E.plan(s); noErrors(s, result.assignments);
  assert.ok(result.assignments.find(a => a.taskId === 'B10').start >= 300);
  assert.ok(result.assignments.find(a => a.taskId === 'A20').start >= result.assignments.find(a => a.taskId === 'B10').end);
});

check('批次超容量与不兼容配方分别阻断', () => {
  const s = E.createState(); task(s, 'A20').qty = 101; has(E.validate(s, s.published.assignments), 'BATCH_CAPACITY');
  task(s, 'A20').qty = 100; task(s, 'B20').compatible = 'HT-OTHER'; has(E.validate(s, s.published.assignments), 'BATCH_COMPATIBILITY');
});

check('循环依赖与缺少前置不进入求解', () => {
  const s = E.createState(); task(s, 'A10').deps.push({ taskId: 'A30', type: 'finish', qty: 0, lag: 0 }); has(E.plan(s).issues, 'DEPENDENCY_CYCLE');
  task(s, 'A10').deps[0].taskId = 'NOT-FOUND'; has(E.plan(s).issues, 'DEPENDENCY_MISSING');
});

check('五日没有合法窗口时，订单交期显示无法可靠估算', () => {
  const s = E.createState(); task(s, 'A10').run = 1000;
  const result = E.plan(s); has(result.issues, 'NO_CANDIDATE');
  assert.equal(E.metrics(s, result.assignments).orders.find(o => o.id === 'O-100').end, null);
});

check('草稿快照时间锁优先于旧正式计划', () => {
  const s = E.createState(), t = task(s, 'A10'), snap = E.adjust(s, 'A10', { start: 1440 }).assignments.find(a => a.taskId === 'A10');
  t.lock = 'time'; t.lockAssignment = copy(snap);
  const result = E.plan(s); noErrors(s, result.assignments);
  assert.equal(result.assignments.find(a => a.taskId === 'A10').start, 1440);
});

check('资源锁允许合法移动，不允许换人', () => {
  const s = E.createState(), t = task(s, 'F10'); t.lock = 'resource'; t.lockAssignment = copy(planOf(s, 'F10'));
  has(E.adjust(s, 'F10', { personId: 'P5' }).issues, 'LOCK_RESOURCE');
});

check('全锁遇现实停机保留锁定且阻止发布', () => {
  const s = E.createState(); task(s, 'A10').lock = 'all'; s.resources.find(r => r.id === 'M1').unavailable.push([0, 60]); s.revision++;
  const result = E.plan(s); has(result.issues, 'CALENDAR'); assert.equal(result.assignments.find(a => a.taskId === 'A10').start, 0);
  s.draft = { assignments: result.assignments, basedOn: s.published.id, revision: s.revision }; assert.equal(E.publish(s).ok, false);
});

check('发布阻断旧版本基线和日历修订，失败不修改状态', () => {
  const s = E.createState(), old = copy(s.draft); assert.equal(E.publish(s, '测试新版本').ok, true); s.draft = old;
  const before = JSON.stringify(s); assert.equal(E.publish(s).ok, false); assert.equal(JSON.stringify(s), before);
  s.draft.basedOn = s.published.id; s.revision++; assert.equal(E.publish(s).ok, false);
});

check('合法发布独立快照且保留历史，不随草稿修改漂移', () => {
  const s = E.createState(); assert.equal(E.publish(s, '审查通过').ok, true);
  s.draft.assignments[0].start = 999;
  assert.equal(s.published.assignments[0].start, 0); assert.equal(s.versions[1].assignments[0].start, 0);
});

check('现场只接正式任务，实际合格门槛不由计划时间自动释放', () => {
  const s = E.createState(); s.now = 120; assert.equal(E.report(s, 'B10', 'start').ok, false);
  s.now = 0; assert.equal(E.report(s, 'A10', 'start').ok, true);
  s.now = 120; assert.equal(E.report(s, 'A10', 'report', { good: 49 }).ok, true); assert.equal(E.report(s, 'B10', 'start').ok, false);
  assert.equal(E.report(s, 'A10', 'report', { good: 1 }).ok, true); assert.equal(E.report(s, 'B10', 'start').ok, true);
});

check('暂停原因必填、暂停不能报工、已执行任务不重排', () => {
  const s = E.createState(); E.report(s, 'A10', 'start');
  assert.equal(E.report(s, 'A10', 'pause').ok, false); assert.equal(E.report(s, 'A10', 'pause', { reason: '刀具异常' }).ok, true);
  s.now = 60; assert.equal(E.report(s, 'A10', 'report', { good: 1 }).ok, false);
  has(E.adjust(s, 'A10', { start: 1440 }).issues, 'LOCK_ALL'); assert.equal(E.report(s, 'A10', 'resume').ok, true);
});

check('重复报工流水与累计超量都不会二次记账', () => {
  const s = E.createState(); E.report(s, 'A10', 'start'); s.now = 240;
  assert.equal(E.report(s, 'A10', 'report', { good: 40, eventId: 'scan-001' }).ok, true);
  assert.equal(E.report(s, 'A10', 'report', { good: 40, eventId: 'scan-001' }).ok, false); assert.equal(task(s, 'A10').good, 40);
  assert.equal(E.report(s, 'A10', 'report', { good: 61 }).ok, false); assert.equal(task(s, 'A10').reported, 40);
});

check('完整现场闭环：A完成不能关闭双产品订单，B完成才齐套', () => {
  const s = E.createState(), act = (id, action, at, values) => { s.now = at; const r = E.report(s, id, action, values); assert.equal(r.ok, true, `${id}/${action}: ${r.message}`); };
  act('A10', 'start', 0); act('A10', 'report', 120, { good: 50 }); act('B10', 'start', 120);
  act('A10', 'report', 240, { good: 50 }); act('B10', 'report', 240, { good: 100 });
  act('A20', 'start', 300); assert.equal(task(s, 'B20').status, 'running');
  act('A20', 'report', 480, { good: 100 }); act('B20', 'report', 480, { good: 100 });
  act('A30', 'start', 480); act('A30', 'report', 540, { good: 100 });
  assert.equal(E.metrics(s, s.published.assignments).orders.find(o => o.id === 'O-100').good, 100);
  act('B30', 'start', 1440); act('B30', 'report', 1500, { good: 100 });
  assert.equal(E.metrics(s, s.published.assignments).orders.find(o => o.id === 'O-100').good, 200);
});

check('连续转移批：09/10/11 点各释放20件，下序每批30分钟', () => {
  const s = E.createState();
  assert.deepEqual(planOf(s, 'T20').segments.map(g => [g.start, g.end, g.qty]), [[60, 90, 20], [120, 150, 20], [180, 210, 20]]);
  const a = copy(s.published.assignments), target = a.find(x => x.taskId === 'T20'); target.segments[1].start = 90; target.segments[1].end = 120;
  has(E.validate(s, a), 'TRANSFER_STARVATION');
});

check('连续转移批实际生产不可提前消费第二批', () => {
  const s = E.createState(); assert.equal(E.report(s, 'T10', 'start').ok, true);
  s.now = 60; E.report(s, 'T10', 'report', { good: 20 }); assert.equal(E.report(s, 'T20', 'start').ok, true);
  s.now = 90; assert.equal(E.report(s, 'T20', 'report', { good: 20 }).ok, true);
  s.now = 150; assert.equal(E.report(s, 'T20', 'report', { good: 20 }).ok, false);
  s.now = 120; E.report(s, 'T10', 'report', { good: 20 }); s.now = 150; assert.equal(E.report(s, 'T20', 'report', { good: 20 }).ok, true);
  assert.equal(task(s, 'T20').consumed.T10, 40);
});

check('上游同一产出不能被多个下游超预留', () => {
  const s = E.createState(), extra = copy(task(s, 'T20')); extra.id = 'T30'; s.tasks.push(extra);
  has(E.plan(s).issues, 'MATERIAL_OVERALLOCATION');
});

check('不支持的消耗转移组合显式阻断，不拼接伪计划', () => {
  const s = E.createState(); task(s, 'T20').setup = 15; has(E.plan(s).issues, 'TRANSFER_UNSUPPORTED');
});

check('实际运行逾期仍占用人员设备，不能按旧计划结束自动释放', () => {
  const s = E.createState(); assert.equal(E.report(s, 'A10', 'start').ok, true); s.now = 300;
  assert.equal(E.report(s, 'D10', 'start').ok, false);
  s.now = 240; E.report(s, 'A10', 'report', { good: 100 }); s.now = 300;
  assert.equal(E.report(s, 'D10', 'start').ok, true);
});

check('人员技能在发布后降低，现场开工仍阻断', () => {
  const s = E.createState(); s.resources.find(r => r.id === 'P1').skills.精密加工 = 2;
  assert.equal(E.report(s, 'A10', 'start').ok, false);
});

check('迟到放行的下一转移批还需加工时间，不能立即补报', () => {
  const s = E.createState(); E.report(s, 'T10', 'start'); s.now = 60; E.report(s, 'T10', 'report', { good: 20 }); E.report(s, 'T20', 'start');
  s.now = 90; E.report(s, 'T20', 'report', { good: 20 });
  s.now = 150; assert.equal(E.report(s, 'T10', 'report', { good: 20 }).ok, true);
  assert.equal(E.report(s, 'T20', 'report', { good: 20 }).ok, false);
  s.now = 180; assert.equal(E.report(s, 'T20', 'report', { good: 20 }).ok, true);
});

check('实际暂停和午休都不创造虚假产能', () => {
  const s = E.createState(); E.report(s, 'A10', 'start'); s.now = 60; E.report(s, 'A10', 'pause', { reason: '设备检查' });
  s.now = 120; E.report(s, 'A10', 'resume'); s.now = 240; assert.equal(E.report(s, 'A10', 'report', { good: 100 }).ok, false);
  assert.equal(E.actualProgress(s, task(s, 'A10')).qty, 75); s.now = 300; assert.equal(E.actualProgress(s, task(s, 'A10')).qty, 75);
  s.now = 360; assert.equal(E.report(s, 'A10', 'report', { good: 100 }).ok, true);
});

check('真实人机分段随暂停顺延，日报可复用同一计算结果', () => {
  const s = E.createState(), t = task(s, 'A20');
  Object.assign(t, { actualStart: 300, status: 'running', activity: [{ start: 300, end: 340 }, { start: 370, end: null }] }); s.now = 510;
  const progress = E.actualProgress(s, t);
  assert.equal(progress.qty, 100);
  assert.deepEqual(progress.actualSegments.map(g => [g.start, g.end, g.kind, g.personId]), [[300, 330, 'setup', 'P3'], [330, 340, 'run', null], [370, 480, 'run', null], [480, 510, 'unload', 'P3']]);
  assert.equal(progress.actualSegments.filter(g => g.personId).reduce((n, g) => n + g.end - g.start, 0), 60);
});

check('执行逾期和未处置不良不沿用旧计划冒充可靠交期', () => {
  const s = E.createState(); s.now = 1501;
  assert.equal(E.metrics(s, s.published.assignments).orders.find(o => o.id === 'O-100').end, null);
  s.now = 0; task(s, 'A30').bad = 1;
  assert.equal(E.metrics(s, s.published.assignments).orders.find(o => o.id === 'O-100').end, null);
});

check('末端返工新任务重排发布，合格回记不重复计算交付', () => {
  const s = E.createState();
  for (const id of ['A10', 'B10', 'A20', 'B20']) Object.assign(task(s, id), { good: 100, reported: 100, status: 'done', actualEnd: planOf(s, id).end, releases: [{ at: planOf(s, id).end, good: 100 }] });
  Object.assign(task(s, 'A30'), { good: 98, bad: 2, reported: 100, status: 'done', actualEnd: 540 }); s.now = 540;
  assert.equal(E.metrics(s, s.published.assignments).orders.find(o => o.id === 'O-100').good, 98);
  const disposition = E.dispose(s, 'A30', 'rework', 2, '外观复检'); assert.equal(disposition.ok, true, disposition.message);
  assert.equal(E.report(s, disposition.created[0], 'start').ok, false);
  const result = E.plan(s); noErrors(s, result.assignments); s.draft = { assignments: result.assignments, basedOn: s.published.id, revision: s.revision }; assert.equal(E.publish(s).ok, true);
  const a = planOf(s, disposition.created[0]); s.now = a.start; assert.equal(E.report(s, a.taskId, 'start').ok, true);
  s.now = a.end; assert.equal(E.report(s, a.taskId, 'report', { good: 2 }).ok, true);
  assert.equal(task(s, 'A30').good, 98); assert.equal(task(s, 'A30').recoveredGood, 2);
  assert.equal(E.metrics(s, s.published.assignments).orders.find(o => o.id === 'O-100').good, 100);
});

check('末端报废补产复制完整产品路线，不把检验当生产', () => {
  const s = E.createState(); Object.assign(task(s, 'A30'), { good: 96, bad: 4, reported: 100, status: 'done' });
  const r = E.dispose(s, 'A30', 'scrap', 4, '尺寸超差报废'); assert.equal(r.ok, true, r.message); assert.equal(r.created.length, 3);
  const made = r.created.map(id => task(s, id)); assert.ok(made.some(x => x.mode === 'machine')); assert.ok(made.some(x => x.mode === 'batch')); assert.equal(made.filter(x => x.recoveryOf === 'A30').length, 1);
  assert.equal(E.dispose(s, 'A30', 'scrap', 1, '重复处置').ok, false);
});

check('报废补产全链实际执行后回记原产品，路线历史保留', () => {
  const s = E.createState();
  for (const id of ['A10', 'B10', 'A20', 'B20']) Object.assign(task(s, id), { good: 100, reported: 100, status: 'done', actualEnd: planOf(s, id).end, releases: [{ at: planOf(s, id).end, good: 100 }] });
  Object.assign(task(s, 'A30'), { good: 96, bad: 4, reported: 100, status: 'done', actualEnd: 540 }); s.now = 540;
  const disposition = E.dispose(s, 'A30', 'scrap', 4, '整链补产'); assert.equal(disposition.ok, true);
  const result = E.plan(s); noErrors(s, result.assignments); s.draft = { assignments: result.assignments, basedOn: s.published.id, revision: s.revision }; assert.equal(E.publish(s).ok, true);
  for (const id of disposition.created) {
    const a = planOf(s, id); s.now = a.start; const start = E.report(s, id, 'start'); assert.equal(start.ok, true, start.message);
    s.now = a.end; const report = E.report(s, id, 'report', { good: 4 }); assert.equal(report.ok, true, report.message);
  }
  assert.equal(task(s, 'A30').good, 96); assert.equal(task(s, 'A30').bad, 4); assert.equal(task(s, 'A30').recoveredGood, 4);
  assert.equal(E.metrics(s, s.published.assignments).orders.find(o => o.id === 'O-100').good, 100);
});

check('暂停的设备任务保留机台，逾期现场未确认时禁止再发布', () => {
  const s = E.createState(); E.report(s, 'A10', 'start'); s.now = 30; E.report(s, 'A10', 'pause', { reason: '工件卡滞' }); s.now = 300;
  assert.equal(E.report(s, 'D10', 'start').ok, false); has(E.validate(s, s.published.assignments), 'ACTUAL_OVERRUN');
  assert.equal(E.publish(s).ok, false);
});

check('不可中断任务不跨午休拼工时，可中断任务拆成合法运行段', () => {
  const s = E.createState(), t = task(s, 'F10'); t.run = 180; t.interruptible = false;
  s.resources.find(r => r.id === 'P5').available = [[120, 240], [300, 420]];
  s.resources.find(r => r.id === 'P7').available = [[120, 240], [300, 420]];
  has(E.plan(s).issues, 'NO_CANDIDATE'); t.interruptible = true;
  const result = E.plan(s), a = result.assignments.find(x => x.taskId === t.id); assert.ok(a); assert.ok(a.segments.length >= 2);
  assert.equal(a.segments.reduce((n, g) => n + g.end - g.start, 0), 180);
});

check('正式一致性：拆批未发布不能将正式共享批按单任务开工', () => {
  const s = E.createState(); s.batches = []; task(s, 'A20').batchId = null; task(s, 'B20').batchId = null; s.revision++; s.now = 300;
  const before = JSON.stringify(s), result = E.report(s, 'A20', 'start');
  assert.equal(result.ok, false); assert.match(result.message, /批次归属.*重新排程并发布/); assert.equal(JSON.stringify(s), before);
});

check('正式一致性：同批增删成员未发布都阻止启动', () => {
  const s = E.createState(), extra = copy(task(s, 'A20')); extra.id = 'A20-EXTRA'; extra.qty = 1; s.tasks.push(extra); s.batches[0].taskIds.push(extra.id); s.now = 300;
  assert.match(E.report(s, 'A20', 'start').message, /成员集合/);
  s.tasks.pop(); s.batches[0].taskIds = ['A20']; assert.match(E.report(s, 'A20', 'start').message, /成员集合/);
});

check('正式一致性：未发布工艺名称和周期变更均阻止执行', () => {
  const s = E.createState(); task(s, 'A10').op = '另一加工工艺';
  assert.match(E.report(s, 'A10', 'start').message, /工艺参数.*重新排程并发布/);
  task(s, 'A10').op = '精密加工'; task(s, 'A10').run = 120;
  assert.equal(E.report(s, 'A10', 'start').ok, false);
  s.revision++; const result = E.plan(s); noErrors(s, result.assignments); s.draft = { assignments: result.assignments, basedOn: s.published.id, revision: s.revision };
  assert.equal(E.publish(s).ok, true); assert.equal(E.report(s, 'A10', 'start').ok, true);
});

check('正式一致性：恢复执行也检查已发布工艺，纯计划锁定不误阻现场', () => {
  const s = E.createState(), t = task(s, 'A10'); t.lock = 'time'; t.lockAssignment = E.adjust(s, 'A10', { start: 1440 }).assignments.find(a => a.taskId === t.id);
  assert.equal(E.report(s, 'A10', 'start').ok, true); s.now = 30; E.report(s, 'A10', 'pause', { reason: '短暂停机' });
  t.rate = 50; assert.equal(E.report(s, 'A10', 'resume').ok, false); t.rate = 25; assert.equal(E.report(s, 'A10', 'resume').ok, true);
});

check('正式一致性：旧无快照版本仍校验正式工时与当前工艺', () => {
  const s = E.createState(); delete s.published.taskSpecs; delete s.published.batchSpecs; task(s, 'A10').run = 120;
  const before = JSON.stringify(s), result = E.report(s, 'A10', 'start'); assert.equal(result.ok, false); assert.match(result.message, /工时与工艺不符/); assert.equal(JSON.stringify(s), before);
});

const failed = results.filter(r => !r.pass);
const output = JSON.stringify({ at: new Date().toISOString(), checks: results.length, passed: results.length - failed.length, failed: failed.length, results }, null, 2);
const outputIndex = args.indexOf('--output');
if (outputIndex >= 0 && args[outputIndex + 1]) { const target = path.resolve(args[outputIndex + 1]); fs.mkdirSync(path.dirname(target), { recursive: true }); fs.writeFileSync(target, output + '\n'); }
console.log(output);
if (failed.length) process.exitCode = 1;
