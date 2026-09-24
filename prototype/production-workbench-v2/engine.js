(function (root) {
  'use strict';
  const HORIZON = 4 * 1440 + 540;
  const clone = value => JSON.parse(JSON.stringify(value));
  const overlap = (a, b) => a.start < b.end && b.start < a.end;
  const num = value => Number.isFinite(value);
  const issue = (code, message, taskId, resourceId, severity = 'error') => ({ code, message, taskId, resourceId, severity });
  const taskBy = (s, id) => s.tasks.find(t => t.id === id);
  const resourceBy = (s, id) => s.resources.find(r => r.id === id);
  const reference = (s, id) => (s.published?.assignments || []).find(a => a.taskId === id);
  const lockReference = (s, t) => fixed(t) ? reference(s, t.id) : t.lockAssignment || reference(s, t.id);
  const fixed = t => ['running', 'paused', 'done'].includes(t.status);
  const signature = a => JSON.stringify([a.start, a.end, a.machineId, a.personId, a.segments]);
  const available = (r, start, end) => !!r && r.available.some(w => start >= w[0] && end <= w[1]) && !r.unavailable.some(w => start < w[1] && w[0] < end);
  const occupied = assignments => {
    const seen = new Set();
    return assignments.flatMap(a => a.segments.flatMap(g => [g.machineId, g.personId].filter(Boolean).map(resourceId => ({ ...g, resourceId, taskId: a.taskId, batchId: a.batchId })))).filter(g => {
      const key = `${g.batchId || g.taskId}|${g.resourceId}|${g.kind}|${g.start}|${g.end}`;
      if (seen.has(key)) return false;
      seen.add(key); return true;
    });
  };
  const duration = t => ({ setup: Math.ceil(t.setup || 0), run: Math.ceil(t.run || (t.rate > 0 ? t.qty / t.rate * 60 : 0)), unload: Math.ceil(t.unload || 0) });
  const runNeedsPerson = t => !['auto', 'batch'].includes(t.mode);
  const hasPerson = t => t.mode === 'manual' || t.mode === 'machine' || (t.setup || 0) > 0 || (t.unload || 0) > 0;
  const canPerson = (r, t) => r.type === 'person' && r.workshop === t.workshop && r.wc === t.wc && (r.skills[t.skill] || 0) >= (t.minLevel || 1);
  const taskSpec = t => ({ id: t.id, orderId: t.orderId, lineId: t.lineId, product: t.product, op: t.op, qty: t.qty, workshop: t.workshop, wc: t.wc, mode: t.mode, skill: t.skill, minLevel: t.minLevel, machines: [...t.machines].sort(), rate: t.rate, setup: t.setup, run: t.run, unload: t.unload, deps: clone(t.deps || []).sort((a, b) => a.taskId.localeCompare(b.taskId)), batchId: t.batchId || null, compatible: t.compatible || '', interruptible: !!t.interruptible });
  const batchSpec = b => ({ id: b.id, taskIds: [...b.taskIds].sort(), machineId: b.machineId, capacity: b.capacity, compatibility: b.compatibility, run: b.run, setup: b.setup, unload: b.unload });
  const consumerDep = t => (t.deps || []).find(d => d.consume);
  function allocation(s, t, d) {
    let offset = 0;
    for (const other of s.tasks) {
      if (other.id === t.id) break;
      const link = (other.deps || []).find(x => x.consume && x.taskId === d.taskId);
      if (link) offset += other.qty * (link.ratio || 1);
    }
    return { offset, amount: t.qty * (d.ratio || 1) };
  }
  function productionAt(t, a, at) {
    if (!a || at < a.start) return 0;
    if (['auto', 'batch'].includes(t.mode)) return at >= a.end ? t.qty : 0;
    const spans = a.segments.filter(g => g.kind === 'run');
    const total = spans.reduce((n, g) => n + g.end - g.start, 0);
    const elapsed = spans.reduce((n, g) => n + Math.max(0, Math.min(at, g.end) - g.start), 0);
    return total ? Math.min(t.qty, Math.floor(t.qty * elapsed / total + 1e-7)) : 0;
  }
  function releaseAt(t, a, qty) {
    if (!a) return Infinity;
    if (qty <= 0) return a.start;
    if (['auto', 'batch'].includes(t.mode)) return a.end;
    const run = a.segments.filter(g => g.kind === 'run');
    let remaining = run.reduce((n, g) => n + g.end - g.start, 0) * qty / t.qty;
    for (const g of run) {
      if (remaining <= g.end - g.start) return g.start + Math.ceil(remaining - 1e-8);
      remaining -= g.end - g.start;
    }
    return a.end;
  }
  function dependencyTime(s, t, assignments) {
    return (t.deps || []).reduce((earliest, d) => {
      const prev = taskBy(s, d.taskId), a = assignments.find(x => x.taskId === d.taskId);
      if (!prev || !a) return Infinity;
      return Math.max(earliest, (d.type === 'quantity' ? releaseAt(prev, a, d.qty) : a.end) + (d.lag || 0));
    }, Math.max(0, s.now || 0));
  }
  function dataIssues(s) {
    const out = [], ids = new Set();
    for (const t of s.tasks) {
      if (ids.has(t.id)) out.push(issue('TASK_DUPLICATE', '工序任务 ID 重复', t.id));
      ids.add(t.id);
      if (!num(t.qty) || t.qty <= 0 || !num(t.run) || t.run < 0 || !num(t.rate) || t.rate < 0 || (!t.run && !t.rate)) out.push(issue('PROCESS_DATA', '缺少有效数量、工时或产能', t.id));
      if (![t.setup, t.unload].every(v => num(v) && v >= 0)) out.push(issue('PROCESS_DATA', '准备/卸载时间必须是非负分钟数', t.id));
      if (!s.orders.some(o => o.id === t.orderId && o.lines.some(l => l.id === t.lineId))) out.push(issue('ORDER_LINK', '工序没有有效订单产品行', t.id));
      if (!['manual', 'machine', 'auto', 'batch'].includes(t.mode)) out.push(issue('MODE', '不支持的工艺模式', t.id));
      if (t.mode === 'batch' && !t.machines.some(id => (resourceBy(s, id)?.capacity || 0) >= t.qty)) out.push(issue('BATCH_CAPACITY', '单独加工批也不能超过设备容量', t.id));
      for (const d of t.deps || []) {
        const prev = taskBy(s, d.taskId);
        if (!prev) out.push(issue('DEPENDENCY_MISSING', '前置工序不存在', t.id));
        if (!['finish', 'quantity'].includes(d.type)) out.push(issue('DEPENDENCY_UNSUPPORTED', '本原型仅支持完成前置与合格数量门槛，连续消耗转移批尚未实现', t.id));
        if (d.type === 'quantity' && (!num(d.qty) || d.qty <= 0 || (prev && d.qty > prev.qty))) out.push(issue('QUANTITY_THRESHOLD', '数量门槛必须大于零且不超过前置工序数量', t.id));
        if (!num(d.lag) || d.lag < 0) out.push(issue('DEPENDENCY_LAG', '前置等待时间必须为非负分钟', t.id));
        if (d.consume) {
          if (!['manual', 'machine'].includes(t.mode) || t.setup || t.unload || t.batchId || (t.deps || []).filter(x => x.consume).length !== 1 || d.type !== 'quantity' || !num(d.ratio) || d.ratio <= 0 || !num(d.transferQty) || d.transferQty <= 0 || d.transferQty > (prev?.qty || 0) || t.qty * d.ratio % 1 || d.transferQty / d.ratio % 1) out.push(issue('TRANSFER_UNSUPPORTED', '连续转移批仅支持一个按件来源、人工/人机固定速率、零装卸、整件换算；不支持组合未排产', t.id));
        }
      }
      if (t.batchId && !s.batches.some(b => b.id === t.batchId && b.taskIds.includes(t.id))) out.push(issue('BATCH_LINK', '工序与共享批次归属不一致', t.id));
      if (t.restartSetup > 0) out.push(issue('UNSUPPORTED_RECOVERY', '恢复准备工时尚未建模，请采用不可中断任务', t.id));
    }
    const visiting = new Set(), done = new Set();
    function visit(t) {
      if (visiting.has(t.id)) { out.push(issue('DEPENDENCY_CYCLE', '工序依赖形成循环', t.id)); return; }
      if (done.has(t.id)) return;
      visiting.add(t.id);
      (t.deps || []).forEach(d => { const p = taskBy(s, d.taskId); if (p) visit(p); });
      visiting.delete(t.id); done.add(t.id);
    }
    s.tasks.forEach(visit);
    for (const p of s.tasks) {
      const demand = s.tasks.reduce((n, t) => n + (t.deps || []).filter(d => d.consume && d.taskId === p.id).reduce((sum, d) => sum + t.qty * (d.ratio || 1), 0), 0);
      if (demand > p.qty) out.push(issue('MATERIAL_OVERALLOCATION', `${p.id} 产出 ${p.qty} 件被下游预留 ${demand} 件，禁止重复分配`, p.id));
    }
    for (const r of s.resources) {
      const windows = [...(r.available || []), ...(r.unavailable || [])];
      if (windows.some(w => !Array.isArray(w) || !num(w[0]) || !num(w[1]) || w[0] >= w[1])) out.push(issue('CALENDAR_DATA', '资源日历窗口无效', null, r.id));
    }
    for (const b of s.batches) {
      const members = b.taskIds.map(id => taskBy(s, id)).filter(Boolean), machine = resourceBy(s, b.machineId);
      if (!members.length || members.length !== b.taskIds.length || new Set(b.taskIds).size !== b.taskIds.length) out.push(issue('BATCH_MEMBER', `批次 ${b.id} 成员无效或重复`));
      if (!machine || machine.type !== 'machine') out.push(issue('BATCH_MACHINE', `批次 ${b.id} 缺少有效设备`));
      if (members.reduce((n, t) => n + t.qty, 0) > Math.min(b.capacity || 0, machine?.capacity || 0)) out.push(issue('BATCH_CAPACITY', `批次 ${b.id} 超过设备/批次容量`, members[0]?.id, b.machineId));
      if (members.some(t => t.mode !== 'batch' || t.batchId !== b.id || !b.compatibility || t.compatible !== b.compatibility || t.workshop !== members[0].workshop || t.wc !== members[0].wc || t.skill !== members[0].skill || !t.machines.includes(b.machineId))) out.push(issue('BATCH_COMPATIBILITY', `批次 ${b.id} 工艺、技能、中心或配方不兼容`, members[0]?.id));
      if (members.some(t => ['setup', 'run', 'unload'].some(k => t[k] !== b[k]))) out.push(issue('BATCH_DURATION', `批次 ${b.id} 各成员工时必须与批次一致`, members[0]?.id));
      if (members.some(t => (t.deps || []).some(d => b.taskIds.includes(d.taskId)))) out.push(issue('BATCH_DEPENDENCY', `批次 ${b.id} 内部前置无法同时加工`, members[0]?.id));
    }
    return out;
  }
  function validate(s, assignments) {
    const out = dataIssues(s), seen = new Set();
    for (const a of assignments) {
      const t = taskBy(s, a.taskId);
      if (!t) { out.push(issue('UNKNOWN_TASK', '计划引用已移除的工序', a.taskId)); continue; }
      if (seen.has(t.id)) out.push(issue('ASSIGNMENT_DUPLICATE', '工序重复排入计划', t.id));
      seen.add(t.id);
      if (!num(a.start) || !num(a.end) || a.start < 0 || a.end <= a.start || a.end > HORIZON || !a.segments?.length) { out.push(issue('TIME_RANGE', '计划时间或分段无效/超出五日范围', t.id)); continue; }
      if (a.batchId !== (t.batchId || null)) out.push(issue('BATCH_PLAN', '计划与当前批次归属不一致', t.id));
      const lengths = { setup: 0, run: 0, unload: 0 }, expected = duration(t);
      let last = a.start, phaseOrder = -1;
      for (const g of a.segments) {
        if (!num(g.start) || !num(g.end) || g.end <= g.start || g.start < last || g.start < a.start || g.end > a.end || !Object.hasOwn(lengths, g.kind)) { out.push(issue('SEGMENT_SHAPE', '工序阶段顺序、时间或类型无效', t.id)); continue; }
        last = g.end; lengths[g.kind] += g.end - g.start;
        if (['setup', 'run', 'unload'].indexOf(g.kind) < phaseOrder) out.push(issue('SEGMENT_ORDER', '阶段必须按准备、运行、卸载排序', t.id));
        phaseOrder = ['setup', 'run', 'unload'].indexOf(g.kind);
        const needsMachine = t.mode !== 'manual', needsPerson = g.kind === 'run' ? runNeedsPerson(t) : hasPerson(t);
        if (needsMachine && (!g.machineId || !t.machines.includes(g.machineId))) out.push(issue('MACHINE_CAPABILITY', '设备不在工艺候选范围', t.id, g.machineId));
        if (!needsMachine && g.machineId) out.push(issue('MACHINE_UNNEEDED', '纯人工任务不应占用设备', t.id));
        if (needsPerson && !g.personId) out.push(issue('PERSON_MISSING', '该阶段缺少具名作业人员', t.id));
        if (!needsPerson && g.personId) out.push(issue('AUTO_LABOR', '自动运行阶段不得重复占用装卸人员', t.id, g.personId));
        if (g.machineId !== a.machineId || (g.personId && g.personId !== a.personId)) out.push(issue('SEGMENT_RESOURCE', '工序与阶段分配资源不一致', t.id));
        for (const id of [g.machineId, g.personId].filter(Boolean)) {
          const r = resourceBy(s, id);
          if (!r) { out.push(issue('RESOURCE_MISSING', '资源已移除', t.id, id)); continue; }
          if ((id === g.machineId && r.type !== 'machine') || (id === g.personId && !canPerson(r, t))) out.push(issue('SKILL', '资源类型、人员技能或所属工作中心不满足要求', t.id, id));
          if (!available(r, g.start, g.end)) out.push(issue('CALENDAR', '任务落在休息、请假、停机或不可用时段', t.id, id));
        }
      }
      if (a.start !== a.segments[0].start || a.end !== a.segments.at(-1).end) out.push(issue('BOUNDARY', '工序起止与分段边界不一致', t.id));
      for (const kind of Object.keys(lengths)) if (lengths[kind] !== expected[kind]) out.push(issue('DURATION', `${kind} 阶段工时与工艺不符`, t.id));
      if (Math.abs(a.segments.filter(g => g.kind === 'run').reduce((n, g) => n + (g.qty || 0), 0) - t.qty) > 1e-6) out.push(issue('SEGMENT_QUANTITY', '运行分段数量与工序总量不守恒', t.id));
      if (!t.interruptible && a.segments.some((g, i) => i > 0 && g.start !== a.segments[i - 1].end)) out.push(issue('INTERRUPTION', '不可中断任务包含中断', t.id));
      const earliest = (t.deps || []).reduce((n, d) => {
        const p = taskBy(s, d.taskId), pa = assignments.find(x => x.taskId === d.taskId);
        return Math.max(n, !p || !pa ? Infinity : (d.type === 'quantity' ? releaseAt(p, pa, d.qty) : pa.end) + (d.lag || 0));
      }, 0);
      if (a.start < earliest) out.push(issue('DEPENDENCY', '前置完成/合格数量门槛或等待时间未满足', t.id));
      const cd = consumerDep(t);
      if (cd) {
        const p = taskBy(s, cd.taskId), pa = assignments.find(x => x.taskId === cd.taskId), claim = allocation(s, t, cd);
        let material = claim.offset;
        for (const g of a.segments.filter(x => x.kind === 'run')) {
          material += g.qty * cd.ratio;
          if (!p || !pa || g.start < releaseAt(p, pa, material) + cd.lag) out.push(issue('TRANSFER_STARVATION', '该转移段开始前尚未释放对应已预留上游数量', t.id));
          if (g.qty * cd.ratio > cd.transferQty + 1e-6) out.push(issue('TRANSFER_SIZE', '转移段超过设置的转移批量', t.id));
        }
      }
      const old = lockReference(s, t);
      if (old && (fixed(t) || t.lock === 'all') && signature(old) !== signature(a)) out.push(issue('LOCK_ALL', '已执行或全锁工序禁止重排', t.id));
      if (old && t.lock === 'time' && (old.start !== a.start || old.end !== a.end)) out.push(issue('LOCK_TIME', '时间锁定工序起止不得改变', t.id));
      if (old && t.lock === 'resource' && (old.machineId !== a.machineId || old.personId !== a.personId)) out.push(issue('LOCK_RESOURCE', '资源锁定工序不能换人或换设备', t.id));
      if (['running', 'paused'].includes(t.status) && s.now > a.end && t.reported < t.qty) out.push(issue('ACTUAL_OVERRUN', '任务实际执行已超过计划结束，先处理现场异常/报工，不能按旧结束时间发布资源交接', t.id));
    }
    for (const t of s.tasks) if (!seen.has(t.id)) out.push(issue('UNPLANNED', '工序未排入五日计划，订单交期无法可靠估算', t.id));
    for (const b of s.batches) {
      const plans = b.taskIds.map(id => assignments.find(a => a.taskId === id)).filter(Boolean);
      const phaseSignature = a => JSON.stringify(a.segments.map(g => [g.start, g.end, g.kind, g.machineId, g.personId]));
      if (plans.length && (plans.length !== b.taskIds.length || plans.some(a => a.machineId !== b.machineId || phaseSignature(a) !== phaseSignature(plans[0])))) out.push(issue('BATCH_SYNC', `批次 ${b.id} 必须共享同一完整资源时段`, plans[0]?.taskId));
    }
    const uses = occupied(assignments);
    for (let i = 0; i < uses.length; i++) for (let j = i + 1; j < uses.length; j++) if (uses[i].resourceId === uses[j].resourceId && overlap(uses[i], uses[j])) out.push(issue('RESOURCE_CONFLICT', `${uses[i].taskId} 与 ${uses[j].taskId} 重复占用同一资源`, uses[j].taskId, uses[j].resourceId));
    return out;
  }
  function makeCandidate(s, t, earliest, machineId, personId, uses, strictStart = false) {
    const d = duration(t), machine = machineId ? resourceBy(s, machineId) : null, person = personId ? resourceBy(s, personId) : null;
    const required = kind => [machine, (kind === 'run' ? runNeedsPerson(t) : hasPerson(t)) ? person : null].filter(Boolean);
    const free = (kind, start, end) => required(kind).every(r => available(r, start, end) && !uses.some(u => u.resourceId === r.id && overlap(u, { start, end })));
    for (let start = Math.ceil(earliest); start < HORIZON; start++) {
      if (strictStart && start !== Math.ceil(earliest)) return null;
      const segments = []; let cursor = start, failed = false;
      for (const kind of ['setup', 'run', 'unload']) {
        let remaining = d[kind];
        if (!remaining) continue;
        if (!t.interruptible || kind !== 'run') {
          if (!free(kind, cursor, cursor + remaining)) { failed = true; break; }
          segments.push({ start: cursor, end: cursor + remaining, kind, machineId, personId: required(kind).includes(person) && person ? personId : null, qty: kind === 'run' ? t.qty : 0 });
          cursor += remaining;
        } else {
          while (remaining > 0 && cursor < HORIZON) {
            if (!free(kind, cursor, cursor + 1)) { cursor++; continue; }
            const begin = cursor;
            while (remaining > 0 && cursor < HORIZON && free(kind, cursor, cursor + 1)) { cursor++; remaining--; }
            segments.push({ start: begin, end: cursor, kind, machineId, personId: runNeedsPerson(t) ? personId : null, qty: t.qty * (cursor - begin) / d.run });
          }
          if (remaining || (strictStart && segments[0]?.start !== start)) { failed = true; break; }
        }
      }
      if (!failed && segments.length && cursor <= HORIZON && segments[0].start === start) return { taskId: t.id, start, end: cursor, machineId, personId, segments, batchId: t.batchId || null };
    }
    return null;
  }
  function makeTransferCandidate(s, t, earliest, machineId, personId, uses, assignments, strictStart = false) {
    const d = consumerDep(t), p = taskBy(s, d.taskId), pa = assignments.find(a => a.taskId === p.id), claim = allocation(s, t, d);
    const segments = []; let output = 0, cursor = earliest, runUsed = 0;
    while (output < t.qty) {
      const qty = Math.min(d.transferQty / d.ratio, t.qty - output);
      const until = releaseAt(p, pa, claim.offset + (output + qty) * d.ratio) + d.lag;
      const run = Math.round(duration(t).run * (output + qty) / t.qty) - runUsed;
      const local = { ...t, qty, run, interruptible: false };
      const c = makeCandidate(s, local, Math.max(cursor, until), machineId, personId, uses, strictStart && output === 0);
      if (!c || (strictStart && output === 0 && c.start !== earliest)) return null;
      segments.push(...c.segments); cursor = c.end; output += qty; runUsed += run;
    }
    return { taskId: t.id, start: segments[0].start, end: cursor, machineId, personId, segments, batchId: null };
  }
  function plan(s, options = {}) {
    const baseIssues = dataIssues(s);
    if (options.direction === 'backward') return { assignments: clone(s.draft.assignments), issues: [issue('UNSUPPORTED_BACKWARD', '本原型支持五日正排；未实现可靠倒排，正式计划未变')] };
    if (baseIssues.length) return { assignments: clone(s.draft.assignments), issues: baseIssues };
    if (!root.solver?.Solve) return { assignments: [], issues: [issue('SOLVER_MISSING', '本地约束求解器未加载，未生成计划')] };
    const assignments = [], pending = new Set(s.tasks.map(t => t.id)), failures = [];
    const units = s.tasks.filter(t => !t.batchId).map(t => [t]).concat(s.batches.map(b => b.taskIds.map(id => taskBy(s, id))));
    for (const unit of units) if (unit.some(t => fixed(t) || t.lock === 'all')) {
      for (const t of unit) { const old = lockReference(s, t); if (old) assignments.push(clone(old)); pending.delete(t.id); }
    }
    const byPriority = (a, b) => {
      const oa = s.orders.find(o => o.id === a[0].orderId), ob = s.orders.find(o => o.id === b[0].orderId);
      return options.priority === 'due' || options.strategy === 'due' ? oa.due - ob.due || ob.priority - oa.priority : ob.priority - oa.priority || oa.due - ob.due;
    };
    let rounds = 0;
    while (pending.size && rounds++ <= s.tasks.length) {
      const ready = units.filter(u => u.some(t => pending.has(t.id)) && u.every(t => (t.deps || []).every(d => assignments.some(a => a.taskId === d.taskId)))).sort(byPriority);
      if (!ready.length) break;
      const unit = ready[0], t = unit[0], b = t.batchId ? s.batches.find(x => x.id === t.batchId) : null;
      const earliest = Math.max(...unit.map(x => dependencyTime(s, x, assignments)));
      const ref = unit.map(x => [x, lockReference(s, x)]).filter(([, a]) => a);
      const resourceLocks = ref.filter(([x]) => x.lock === 'resource');
      const timeLocks = ref.filter(([x]) => x.lock === 'time');
      const machineIds = t.mode === 'manual' ? [null] : b ? [b.machineId] : t.machines;
      const personIds = hasPerson(t) ? s.resources.filter(r => unit.every(x => canPerson(r, x))).map(r => r.id) : [null];
      const uses = occupied(assignments), candidates = [];
      for (const machineId of machineIds) for (const personId of personIds) {
        if (resourceLocks.some(([, a]) => a.machineId !== machineId || a.personId !== personId)) continue;
        if (timeLocks.some(([, a]) => a.start < earliest || a.start !== timeLocks[0][1].start)) continue;
        const start = timeLocks.length ? timeLocks[0][1].start : earliest;
        const c = consumerDep(t) ? makeTransferCandidate(s, t, start, machineId, personId, uses, assignments, !!timeLocks.length) : makeCandidate(s, t, start, machineId, personId, uses, !!timeLocks.length);
        if (c && !timeLocks.some(([, a]) => c.end !== a.end)) candidates.push(c);
      }
      if (!candidates.length) failures.push(issue('NO_CANDIDATE', `${unit.map(x => x.id).join(' / ')} 在五日内没有满足技能、日历、锁定和资源占用的候选；不代表全局已证明无解`, t.id));
      else {
        const model = { optimize: 'cost', opType: 'min', constraints: { choose: { equal: 1 } }, variables: {}, binaries: {} };
        candidates.forEach((c, i) => { model.variables[`c${i}`] = { choose: 1, cost: c.end * 1000 + c.start + i / 100 }; model.binaries[`c${i}`] = 1; });
        const result = root.solver.Solve(model);
        const index = candidates.findIndex((_, i) => result[`c${i}`] > 0.5);
        if (!result.feasible || index < 0) failures.push(issue('SOLVER_NO_CANDIDATE', '候选选择未返回可行解，未填入虚假计划', t.id));
        else for (const member of unit) {
          const c = clone(candidates[index]); c.taskId = member.id;
          c.segments.forEach(g => { if (g.kind === 'run') g.qty *= member.qty / t.qty; });
          assignments.push(c);
        }
      }
      unit.forEach(x => pending.delete(x.id));
    }
    return { assignments, issues: [...failures, ...validate(s, assignments)], basedOn: s.published?.id || null, revision: s.revision, method: 'serial-finite-candidates-milp', horizon: HORIZON };
  }
  function adjust(s, taskId, changes) {
    const assignments = clone(s.draft.assignments), target = assignments.find(a => a.taskId === taskId);
    if (!target) return { assignments, issues: [issue('UNPLANNED', '请先为该工序生成计划', taskId)] };
    const delta = changes.start === undefined ? 0 : Number(changes.start) - target.start;
    for (const a of assignments.filter(a => a.taskId === taskId || (target.batchId && a.batchId === target.batchId))) {
      a.start += delta; a.end += delta;
      if (changes.machineId !== undefined) a.machineId = changes.machineId || null;
      if (changes.personId !== undefined) a.personId = changes.personId || null;
      a.segments.forEach(g => { g.start += delta; g.end += delta; if (g.machineId && changes.machineId !== undefined) g.machineId = a.machineId; if (g.personId && changes.personId !== undefined) g.personId = a.personId; });
    }
    return { assignments, issues: validate(s, assignments), basedOn: s.published.id, revision: s.revision };
  }
  function metrics(s, assignments) {
    const errors = validate(s, assignments).filter(x => x.severity === 'error');
    const orders = s.orders.map(o => {
      const tasks = s.tasks.filter(t => t.orderId === o.id), plans = assignments.filter(a => tasks.some(t => t.id === a.taskId));
      const staleExecution = tasks.some(t => t.status !== 'done' && plans.find(a => a.taskId === t.id)?.end < s.now);
      const unresolvedQuality = tasks.some(t => (t.bad || 0) > (t.disposedBad || 0));
      const unreliable = plans.length !== tasks.length || staleExecution || unresolvedQuality || errors.some(e => !e.taskId || tasks.some(t => t.id === e.taskId));
      const end = unreliable || !tasks.length ? null : Math.max(...plans.map(a => a.end), ...tasks.map(t => t.actualEnd || 0));
      const good = o.lines.reduce((n, l) => {
        const route = tasks.filter(t => t.lineId === l.id), terminal = route.filter(t => !route.some(x => x.deps.some(d => d.taskId === t.id)));
        const original = terminal.filter(t => !t.recoveryChainId && !t.recoveryOf);
        return n + Math.min(l.qty, ...(original.length ? original.map(t => (t.good || 0) + (t.recoveredGood || 0)) : [0]));
      }, 0);
      return { id: o.id, end, late: end === null ? 0 : Math.max(0, end - o.due), good, qty: o.lines.reduce((n, l) => n + l.qty, 0), reason: staleExecution ? '实际执行已经落后计划，需重新评估剩余作业' : unresolvedQuality ? '不良产出尚未形成完整处置方案' : unreliable ? '计划缺项或存在硬约束错误' : '' };
    });
    const uses = occupied(assignments);
    return { orders, machineHours: uses.filter(u => resourceBy(s, u.resourceId)?.type === 'machine').reduce((n, u) => n + u.end - u.start, 0) / 60, personHours: uses.filter(u => resourceBy(s, u.resourceId)?.type === 'person').reduce((n, u) => n + u.end - u.start, 0) / 60, unplanned: s.tasks.filter(t => !assignments.some(a => a.taskId === t.id)).length };
  }
  const event = (s, type, message, taskId) => s.events.push({ id: `EV${Date.now()}-${s.events.length + 1}`, at: new Date().toISOString(), type, message, taskId });
  function publish(s, note = '') {
    if (s.draft.basedOn !== s.published.id) return { ok: false, message: '草稿基线已过期，请基于当前正式版本重新生成或恢复草稿' };
    if (s.draft.revision !== s.revision) return { ok: false, message: '订单、工艺或资源日历已经变化，请重排/重新校验生成当前修订草稿' };
    const issues = validate(s, s.draft.assignments).filter(i => i.severity === 'error');
    if (issues.length) return { ok: false, message: `发布被阻止：${issues[0].message}（共 ${issues.length} 项）`, issues };
    const id = `V${String(s.versions.length + 1).padStart(2, '0')}`;
    const version = { id, at: new Date().toISOString(), assignments: clone(s.draft.assignments), taskSpecs: s.tasks.map(taskSpec), batchSpecs: s.batches.map(batchSpec), revision: s.revision, note: String(note || '已复核并发布') };
    s.versions.push(clone(version)); s.published = clone(version);
    s.draft = { assignments: clone(version.assignments), basedOn: id, revision: s.revision };
    event(s, 'publish', `${id} 发布：${version.note}`);
    return { ok: true, message: `${id} 已发布到本地现场计划` };
  }
  function actualDependencies(s, t) {
    for (const d of t.deps || []) {
      const p = taskBy(s, d.taskId), qty = d.consume ? allocation(s, t, d).offset + Math.min(d.transferQty, t.qty * d.ratio) : d.type === 'quantity' ? d.qty : p?.qty;
      if (!p || p.good + (p.recoveredGood || 0) < qty || (d.type === 'finish' && p.status !== 'done')) return `前置 ${d.taskId} 合格数量未达到 ${qty} 件`;
      const release = (p.releases || []).find(r => r.good >= qty)?.at ?? p.actualEnd;
      if (release === undefined || s.now < release + (d.lag || 0)) return `前置 ${d.taskId} 的实际放行/等待时间未满足`;
    }
    return null;
  }
  function actualProgress(s, t, at = s.now) {
    const a = reference(s, t.id);
    if (!a || t.actualStart === undefined) return { qty: 0, kind: 'setup', resourceIds: [], complete: false, actualSegments: [] };
    const d = duration(t), cd = consumerDep(t), phases = [];
    if (cd) {
      const p = taskBy(s, cd.taskId), claim = allocation(s, t, cd); let produced = 0, previousRun = 0;
      while (produced < t.qty) {
        const qty = Math.min(cd.transferQty / cd.ratio, t.qty - produced), material = claim.offset + (produced + qty) * cd.ratio;
        const release = (p.releases || []).find(r => r.good >= material)?.at;
        const minutes = Math.round(d.run * (produced + qty) / t.qty) - previousRun;
        phases.push({ kind: 'run', minutes, qty, earliest: release === undefined ? Infinity : release + cd.lag });
        produced += qty; previousRun += minutes;
      }
    } else for (const kind of ['setup', 'run', 'unload']) if (d[kind]) phases.push({ kind, minutes: d[kind], qty: kind === 'run' ? t.qty : 0, earliest: 0 });
    const resourcesFor = kind => [a.machineId, (kind === 'run' ? runNeedsPerson(t) : hasPerson(t)) ? a.personId : null].filter(Boolean);
    let index = 0, elapsed = 0, output = 0; const actualSegments = [];
    for (let minute = t.actualStart; minute < Math.min(at, HORIZON) && index < phases.length; minute++) {
      const p = phases[index];
      if (minute < p.earliest || !(t.activity || []).some(x => minute >= x.start && minute < (x.end ?? at)) || !resourcesFor(p.kind).every(id => available(resourceBy(s, id), minute, minute + 1))) continue;
      const ids = resourcesFor(p.kind), personId = ids.includes(a.personId) ? a.personId : null;
      const last = actualSegments.at(-1);
      if (last && last.end === minute && last.kind === p.kind && last.personId === personId) last.end++;
      else actualSegments.push({ start: minute, end: minute + 1, kind: p.kind, machineId: a.machineId, personId, qty: 0, taskId: t.id, batchId: t.batchId || null });
      elapsed++;
      if (p.kind === 'run' && !['auto', 'batch'].includes(t.mode)) output += p.qty / p.minutes;
      if (elapsed >= p.minutes) { index++; elapsed = 0; }
    }
    const current = phases[Math.min(index, phases.length - 1)], complete = index === phases.length;
    return { qty: ['auto', 'batch'].includes(t.mode) ? complete ? t.qty : 0 : Math.min(t.qty, Math.floor(output + 1e-6)), kind: current?.kind || 'run', resourceIds: resourcesFor(current?.kind || 'run'), complete, actualSegments };
  }
  function report(s, taskId, action, values = {}) {
    const t = taskBy(s, taskId), a = (s.published?.assignments || []).find(x => x.taskId === taskId);
    if (!t || !a) return { ok: false, message: '该任务没有正式发布的派工记录' };
    const fail = message => ({ ok: false, message });
    if (!num(s.now) || s.now < 0 || s.now > HORIZON || s.now < (t.lastActionAt ?? 0)) return fail('模拟时刻无效或早于该任务已记录动作，不可倒写现场历史');
    const members = t.batchId ? s.tasks.filter(x => x.batchId === t.batchId) : [t];
    if (action === 'start' || action === 'resume') {
      const formalBatch = a.batchId || null, currentBatch = t.batchId || null;
      const mismatch = message => fail(`${message}；请重新排程并发布后执行，当前正式版本未被修改`);
      if (formalBatch !== currentBatch) return mismatch('当前批次归属与正式分配不一致');
      if (formalBatch) {
        const formalIds = s.published.assignments.filter(x => x.batchId === formalBatch).map(x => x.taskId).sort();
        const currentIds = members.map(x => x.id).sort(), b = s.batches.find(x => x.id === formalBatch);
        if (!b || JSON.stringify(formalIds) !== JSON.stringify(currentIds) || JSON.stringify(formalIds) !== JSON.stringify([...b.taskIds].sort())) return mismatch('当前共享批次成员集合与正式分配不一致');
        const publishedBatch = s.published.batchSpecs?.find(x => x.id === formalBatch);
        if (publishedBatch && JSON.stringify(publishedBatch) !== JSON.stringify(batchSpec(b))) return mismatch('当前批次设备、配方或周期与正式版本不一致');
      }
      for (const member of members) {
        const publishedSpec = s.published.taskSpecs?.find(x => x.id === member.id);
        if (publishedSpec && JSON.stringify(publishedSpec) !== JSON.stringify(taskSpec(member))) return mismatch(`${member.id} 当前工艺参数或前置关系与正式版本不一致`);
      }
      // 旧本地版本没有工艺快照时，仍复验与正式分段有关的硬约束；计划锁定和日历历史不作为工艺身份。
      const compatibilityCodes = new Set(['PROCESS_DATA', 'MODE', 'MACHINE_CAPABILITY', 'MACHINE_UNNEEDED', 'PERSON_MISSING', 'AUTO_LABOR', 'SEGMENT_RESOURCE', 'SKILL', 'DURATION', 'SEGMENT_QUANTITY', 'BATCH_LINK', 'BATCH_COMPATIBILITY', 'BATCH_DURATION', 'BATCH_MACHINE', 'BATCH_CAPACITY', 'BATCH_PLAN', 'BATCH_SYNC', 'DEPENDENCY', 'DEPENDENCY_MISSING', 'QUANTITY_THRESHOLD']);
      const incompatible = validate(s, s.published.assignments).find(x => compatibilityCodes.has(x.code) && members.some(m => m.id === x.taskId));
      if (incompatible) return mismatch(incompatible.message);
      if (members.some(x => action === 'start' ? x.status !== 'ready' : !['paused', 'done'].includes(x.status))) return fail('当前状态不能执行该动作');
      if (s.now < a.start) return fail('尚未到正式计划开工时间');
      for (const m of members.filter(x => x.status !== 'done')) { const reason = actualDependencies(s, m); if (reason) return fail(reason); }
      const phase = a.segments[0];
      const requiredIds = action === 'resume' ? actualProgress(s, t).resourceIds : [phase.personId, phase.machineId].filter(Boolean);
      if (a.personId && !canPerson(resourceBy(s, a.personId) || { type: 'missing' }, t)) return fail('正式派工人员的技能或工作中心已不满足当前工艺，须重新安排');
      if (a.machineId && (!t.machines.includes(a.machineId) || resourceBy(s, a.machineId)?.type !== 'machine')) return fail('正式计划设备已经不在当前工艺可用范围');
      for (const id of requiredIds) {
        const r = resourceBy(s, id);
        if (!r || !available(r, s.now, s.now + 1)) return fail(`资源 ${r?.name || id} 当前不在可用班次或存在请假/停机`);
        const busy = s.tasks.find(x => {
          if (x.id === t.id || (t.batchId && x.batchId === t.batchId) || !['running', 'paused'].includes(x.status)) return false;
          return actualProgress(s, x).resourceIds.filter(rid => x.status !== 'paused' || resourceBy(s, rid)?.type === 'machine').includes(id);
        });
        if (busy) return fail(`资源 ${r.name} 正在执行 ${busy.id}`);
      }
      members.filter(x => x.status !== 'done').forEach(x => { x.status = 'running'; if (action === 'start') x.actualStart = s.now; x.activity ||= []; x.activity.push({ start: s.now, end: null }); });
    } else if (action === 'pause') {
      if (t.status !== 'running') return fail('只有执行中的任务可以暂停');
      if (!String(values.reason || '').trim()) return fail('请填写暂停原因');
      members.filter(x => x.status === 'running').forEach(x => { x.status = 'paused'; if (x.activity?.at(-1)) x.activity.at(-1).end = s.now; });
    } else if (action === 'report') {
      if (t.status !== 'running') return fail('请先开工或恢复再登记产出');
      const dependencyError = actualDependencies(s, t); if (dependencyError) return fail(dependencyError);
      const good = Number(values.good || 0), bad = Number(values.bad || 0);
      if (![good, bad].every(x => Number.isInteger(x) && x >= 0) || good + bad <= 0) return fail('合格与不合格数量须为非负整数，且合计大于零');
      if (t.reported + good + bad > t.qty) return fail('累计报工超过任务数量；重复或超量记录已阻止');
      if (t.reported + good + bad > actualProgress(s, t).qty) return fail('实际开停、班次与上游放行尚不足以形成该数量；自动加工须完成卸载');
      const cd = consumerDep(t);
      if (cd) {
        const p = taskBy(s, cd.taskId), claim = allocation(s, t, cd), needed = (t.reported + good + bad) * cd.ratio;
        if (p.good < claim.offset + needed) return fail(`上游 ${p.id} 实际合格放行量不足，禁止提前消耗后续转移批`);
        const released = (p.releases || []).find(r => r.good >= claim.offset + needed)?.at;
        if (released === undefined || s.now < released + cd.lag) return fail('该批实际上游释放及转运等待尚未满足');
      }
      if (values.eventId && s.events.some(e => e.reportId === values.eventId)) return fail('该报工流水已登记，不会重复入账');
      t.good += good; t.bad += bad; t.reported += good + bad; t.releases ||= []; if (good) { t.releases.push({ at: s.now, good: t.good }); t.actualRelease = s.now; }
      if (cd) { t.consumed ||= {}; t.consumed[cd.taskId] = t.reported * cd.ratio; }
      if (t.recoveryOf && good) {
        const original = taskBy(s, t.recoveryOf);
        original.recoveredGood = (original.recoveredGood || 0) + good;
        original.releases ||= []; original.releases.push({ at: s.now, good: original.good + original.recoveredGood });
      }
      if (t.reported === t.qty) { t.status = 'done'; t.actualEnd = s.now; if (t.activity?.at(-1)) t.activity.at(-1).end = s.now; }
    } else return fail('未知现场动作');
    event(s, action, `${t.id} ${action}${values.reason ? `：${values.reason}` : ''}`, t.id);
    if (action === 'report') t.lastActionAt = s.now;
    else members.forEach(x => { x.lastActionAt = s.now; });
    if (values.eventId) s.events.at(-1).reportId = values.eventId;
    return { ok: true, message: action === 'report' ? `已登记 ${t.id} 合格 ${values.good || 0} / 不合格 ${values.bad || 0}` : `${t.batchId || t.id} 状态已更新` };
  }
  function dispose(s, taskId, kind, qty, reason) {
    const t = taskBy(s, taskId), amount = Number(qty);
    if (!t || t.status !== 'done' || !['rework', 'scrap'].includes(kind)) return { ok: false, message: '仅对已报完末端任务执行返工或报废补产' };
    if (!String(reason || '').trim() || !Number.isInteger(amount) || amount <= 0 || amount > t.bad - (t.disposedBad || 0)) return { ok: false, message: '请填写原因和有效的不良未处置数量' };
    if (s.tasks.some(x => !x.recoveryChainId && x.orderId === t.orderId && x.lineId === t.lineId && x.deps.some(d => d.taskId === t.id))) return { ok: false, message: '本原型仅支持末端检验不良处置；中间工序需独立质量流程' };
    const originalId = t.recoveryOf || t.id, chain = `${taskId}-${kind === 'rework' ? 'R' : 'S'}${(t.dispositionCount || 0) + 1}`;
    const source = kind === 'rework' ? [t] : s.tasks.filter(x => x.orderId === t.orderId && x.lineId === t.lineId && !x.recoveryChainId && !x.recoveryOf);
    if (source.some(x => consumerDep(x))) return { ok: false, message: '消耗型转移链补产需要重建物料分配，本原型尚未支持此组合' };
    const map = new Map(source.map((x, i) => [x.id, `${chain}-${i + 1}`]));
    const created = source.map(x => {
      const next = clone(x), totalRun = duration(x).run;
      Object.assign(next, { id: map.get(x.id), qty: amount, op: `${x.op} · ${kind === 'rework' ? '返工' : '补产'}`, batchId: null, lock: 'none', status: 'ready', good: 0, bad: 0, reported: 0, disposedBad: 0, recoveredGood: 0, releases: [], activity: [], recoveryChainId: chain, sourceRejectedTask: t.id, recoveryReason: String(reason), run: x.mode === 'batch' || x.mode === 'auto' ? totalRun : Math.max(1, Math.ceil(totalRun * amount / x.qty)), deps: x.deps.map(d => ({ ...d, taskId: map.get(d.taskId) || d.taskId })) });
      for (const key of ['actualStart', 'actualEnd', 'actualRelease', 'lastActionAt', 'lockAssignment', 'consumed', 'recoveryOf']) delete next[key];
      if (x.id === (kind === 'rework' ? t.id : originalId)) next.recoveryOf = originalId;
      return next;
    });
    if (!created.some(x => x.recoveryOf)) return { ok: false, message: '未找到可回记的末端产品任务，未创建补产链' };
    const candidate = clone(s); candidate.tasks.push(...created);
    const problems = dataIssues(candidate); if (problems.length) return { ok: false, message: `处置方案未通过校验：${problems[0].message}` };
    s.tasks.push(...created); t.disposedBad = (t.disposedBad || 0) + amount; t.dispositionCount = (t.dispositionCount || 0) + 1; s.revision++;
    event(s, 'disposition', `${t.id} ${kind === 'rework' ? '返工' : '报废补产'} ${amount} 件：${reason}`, t.id);
    return { ok: true, message: `已创建 ${created.length} 道${kind === 'rework' ? '返工' : '补产'}任务；须重排并发布后执行`, created: created.map(x => x.id) };
  }
  function createState() {
    const shifts = Array.from({ length: 5 }, (_, d) => [[d * 1440, d * 1440 + 240], [d * 1440 + 300, d * 1440 + 540]]).flat();
    const resources = [
      ['M1', 'M1 精密加工中心', 'machine', '机加工车间', '加工一中心', {}, 1],
      ['M2', 'M2 数控车床', 'machine', '机加工车间', '加工二中心', {}, 1],
      ['F1', 'F1 热处理炉', 'machine', '热处理车间', '热处理中心', {}, 200],
      ['W1', 'W1 焊接工作站', 'machine', '装配车间', '焊接中心', {}, 1],
      ['P1', '张工', 'person', '机加工车间', '加工一中心', { 精密加工: 3 }, 1],
      ['P2', '李工', 'person', '机加工车间', '加工二中心', { 数控车削: 3 }, 1],
      ['P3', '王工', 'person', '热处理车间', '热处理中心', { 炉装卸: 3, 工装整理: 2 }, 1],
      ['P4', '赵工', 'person', '质量车间', '检验中心', { 成品检验: 3 }, 1],
      ['P5', '陈工', 'person', '装配车间', '装配中心', { 手工装配: 3 }, 1],
      ['P6', '刘工', 'person', '装配车间', '焊接中心', { 精密焊接: 3 }, 1],
      ['P7', '周工', 'person', '装配车间', '装配中心', { 手工装配: 2 }, 1],
      ['P8', '孙工', 'person', '流转示范车间', '前加工中心', { 小批加工: 3 }, 1],
      ['P9', '吴工', 'person', '流转示范车间', '后加工中心', { 小批复核: 3 }, 1]
    ].map(([id, name, type, workshop, wc, skills, capacity]) => ({ id, name, type, workshop, wc, skills, capacity, unit: '件', available: clone(shifts), unavailable: [] }));
    const orders = [
      { id: 'O-100', customer: '远航装备', due: 540, priority: 100, lines: [{ id: 'A', product: 'A 精密壳体', qty: 100, unit: '件', route: 'RT-A V1' }, { id: 'B', product: 'B 配套轴套', qty: 100, unit: '件', route: 'RT-B V1' }] },
      { id: 'O-200', customer: '北辰自动化', due: 1980, priority: 80, lines: [{ id: 'C', product: 'C 控制组件', qty: 80, unit: '件', route: 'RT-C V2' }, { id: 'D', product: 'D 安装支架', qty: 40, unit: '件', route: 'RT-D V1' }] },
      { id: 'O-300', customer: '海川精工', due: 3420, priority: 60, lines: [{ id: 'E', product: 'E 定位夹具', qty: 60, unit: '件', route: 'RT-E V1' }, { id: 'F', product: 'F 装配底座', qty: 30, unit: '件', route: 'RT-F V1' }] },
      { id: 'O-400', customer: '连续转移批示范', due: 540, priority: 50, lines: [{ id: 'T', product: 'T 流转件', qty: 60, unit: '件', route: 'RT-T V1' }] }
    ];
    const tasks = [];
    function add(id, orderId, lineId, op, mode, skill, personId, machines, rate, deps = [], extra = {}) {
      const o = orders.find(x => x.id === orderId), l = o.lines.find(x => x.id === lineId), r = resources.find(x => x.id === personId);
      tasks.push({ id, orderId, lineId, product: l.product, op, qty: l.qty, workshop: r.workshop, wc: r.wc, mode, skill, minLevel: 3, machines, rate, setup: 0, run: Math.ceil(l.qty / rate * 60), unload: 0, deps, batchId: null, lock: 'none', status: 'ready', good: 0, bad: 0, reported: 0, compatible: '', interruptible: false, ...extra });
    }
    const dep = (taskId, type = 'finish', qty = 0) => ({ taskId, type, qty, lag: 0 });
    add('A10', 'O-100', 'A', '精密加工', 'machine', '精密加工', 'P1', ['M1'], 25);
    add('B10', 'O-100', 'B', '数控车削', 'machine', '数控车削', 'P2', ['M2'], 50, [dep('A10', 'quantity', 50)]);
    add('A20', 'O-100', 'A', '同炉热处理', 'batch', '炉装卸', 'P3', ['F1'], 50, [dep('A10')], { batchId: 'H1', setup: 30, run: 120, unload: 30, compatible: 'HT-180/120' });
    add('B20', 'O-100', 'B', '同炉热处理', 'batch', '炉装卸', 'P3', ['F1'], 50, [dep('B10')], { batchId: 'H1', setup: 30, run: 120, unload: 30, compatible: 'HT-180/120' });
    add('A30', 'O-100', 'A', '成品检验', 'manual', '成品检验', 'P4', [], 100, [dep('A20')]);
    add('B30', 'O-100', 'B', '成品检验', 'manual', '成品检验', 'P4', [], 100, [dep('B20')]);
    add('C10', 'O-200', 'C', '组件装配', 'manual', '手工装配', 'P5', [], 40, [], { interruptible: true });
    add('C20', 'O-200', 'C', '焊接连接', 'machine', '精密焊接', 'P6', ['W1'], 40, [dep('C10')], { setup: 15 });
    add('C30', 'O-200', 'C', '电气检验', 'manual', '成品检验', 'P4', [], 80, [dep('C20')]);
    add('D10', 'O-200', 'D', '支架加工', 'machine', '精密加工', 'P1', ['M1'], 40, [], { setup: 15 });
    add('D20', 'O-200', 'D', '支架检验', 'manual', '成品检验', 'P4', [], 80, [dep('D10')]);
    add('E10', 'O-300', 'E', '夹具加工', 'machine', '数控车削', 'P2', ['M2'], 30, [], { setup: 30 });
    add('E20', 'O-300', 'E', '夹具检验', 'manual', '成品检验', 'P4', [], 60, [dep('E10')]);
    add('F10', 'O-300', 'F', '底座装配', 'manual', '手工装配', 'P5', [], 15, [], { minLevel: 2, interruptible: true });
    add('F20', 'O-300', 'F', '底座检验', 'manual', '成品检验', 'P4', [], 60, [dep('F10')]);
    add('T10', 'O-400', 'T', '前序每小时放行20件', 'manual', '小批加工', 'P8', [], 20);
    add('T20', 'O-400', 'T', '后序按20件转移批加工', 'manual', '小批复核', 'P9', [], 40, [{ ...dep('T10', 'quantity', 20), consume: true, ratio: 1, transferQty: 20 }], { interruptible: true });
    const state = { schema: 2, revision: 0, now: 0, orders, tasks, resources, batches: [{ id: 'H1', taskIds: ['A20', 'B20'], machineId: 'F1', capacity: 200, compatibility: 'HT-180/120', run: 120, setup: 30, unload: 30 }], draft: { assignments: [], basedOn: 'V00', revision: 0 }, published: { id: 'V00', assignments: [], revision: 0 }, versions: [], events: [] };
    const result = plan(state); state.draft.assignments = result.assignments;
    const initial = publish(state, '初始基线：A/B 数量门槛、共享炉批与具名人员');
    if (!initial.ok) throw new Error(initial.message);
    return state;
  }
  root.Engine = { createState, plan, validate, metrics, adjust, publish, report, dispose, occupied, productionAt, releaseAt, duration, actualProgress, HORIZON };
})(typeof window !== 'undefined' ? window : globalThis);
