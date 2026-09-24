(function () {
  'use strict';
  var Views = window.Views = window.Views || {};
  var Actions = window.Actions = window.Actions || {};
  var PageEvents = window.PageEvents = window.PageEvents || {};
  var BASE = Date.UTC(2026, 8, 14, 8, 0);
  var statusNames = { ready: '待开工', running: '进行中', paused: '已暂停', done: '已报完' };
  var statusTones = { ready: 'neutral', running: 'info', paused: 'warning', done: 'success' };
  function clone(value) { return JSON.parse(JSON.stringify(value)); }
  function state() { return window.A.state; }
  function esc(value) { return window.A.e(value); }
  function num(value) { return Number(value) || 0; }
  function round(value) { return Math.round(value * 100) / 100; }
  function hours(value) { return round(value / 60).toFixed(2); }
  function uniq(values) { return Array.from(new Set(values)); }
  function resource(id) { return state().resources.find(function (r) { return r.id === id; }); }
  function resourceName(id) { var r = resource(id); return r ? r.name : (id || '—'); }
  function task(id) { return state().tasks.find(function (t) { return t.id === id; }); }
  function assignments(source) { return source === 'draft' ? state().draft.assignments : state().published.assignments; }
  function assignment(id, source) { return assignments(source).find(function (a) { return a.taskId === id; }); }
  function dateString(minute) { return new Date(BASE + num(minute) * 60000).toISOString().slice(0, 10); }
  function datetime(minute) { return new Date(BASE + num(minute) * 60000).toISOString().slice(0, 16); }
  function minuteOf(value) { return Math.round((Date.parse(value + (value.length === 10 ? 'T00:00:00Z' : ':00Z')) - BASE) / 60000); }
  function bounds(date) { var start = minuteOf(date); return [start, start + 1440]; }
  function filters(view, defaults) {
    var A = window.A; A.filters = A.filters || {};
    A.filters[view] = Object.assign({}, defaults, A.filters[view] || {});
    return A.filters[view];
  }
  function option(value, label, selected) { return '<option value="' + esc(value) + '"' + (String(value) === String(selected) ? ' selected' : '') + '>' + esc(label) + '</option>'; }
  function selectFilter(name, label, values, selected) { return '<label class="ops-filter"><span>' + esc(label) + '</span><select data-ops-filter="' + name + '">' + values.map(function (v) { return option(v[0], v[1], selected); }).join('') + '</select></label>'; }
  function inputFilter(name, label, value, type) { return '<label class="ops-filter"><span>' + esc(label) + '</span><input data-ops-filter="' + name + '" type="' + (type || 'text') + '" value="' + esc(value) + '"></label>'; }
  function workshops() { return [['', '全部车间']].concat(uniq(state().tasks.map(function (t) { return t.workshop; }).filter(Boolean)).map(function (w) { return [w, w]; })); }
  function heading(title, meta, actions) { return '<header class="ops-heading"><div><h1>' + esc(title) + '</h1><p>' + esc(meta) + '</p></div><div class="actions">' + (actions || '') + '</div></header>'; }
  function button(action, id, label, icon, cls) { return window.A.btn(action, id, label, icon, cls || ''); }
  function metric(label, value, foot, tone) { return '<div class="ops-metric"><span>' + esc(label) + '</span><strong class="' + (tone || '') + '">' + esc(value) + '</strong><small>' + esc(foot || '') + '</small></div>'; }
  function table(headers, rows) { return rows.length ? window.A.table(headers, rows) : window.A.empty('暂无符合条件的记录'); }
  function section(title, body, meta) { return '<section class="ops-section"><div class="ops-section-heading"><h2>' + esc(title) + '</h2>' + (meta ? '<span>' + esc(meta) + '</span>' : '') + '</div>' + body + '</section>'; }
  function bindFilters(view) {
    document.querySelectorAll('[data-ops-filter]').forEach(function (input) {
      input.addEventListener('change', function () { window.A.filters[view][input.dataset.opsFilter] = input.value; window.A.render(); });
    });
  }
  function errorBox(panel, message) { var element = panel.querySelector('[data-ops-error]'); if (element) { element.textContent = message; element.hidden = false; } else window.UI.toast(message); }
  function modalForm(title, body, submit, onSubmit) {
    var panel = window.UI.modal(title, '<form class="ops-form">' + body + '<p class="ops-form-error" data-ops-error role="alert" hidden></p><div class="ops-form-actions"><button type="button" class="btn" data-close-modal>取消</button><button class="btn primary" type="submit">' + esc(submit) + '</button></div></form>');
    panel.querySelector('form').addEventListener('submit', function (event) { event.preventDefault(); onSubmit(new FormData(event.currentTarget), panel, event.currentTarget); });
    return panel;
  }
  function field(name, label, value, type, extra) { return '<label class="field"><span>' + esc(label) + '</span><input name="' + name + '" type="' + (type || 'text') + '" value="' + esc(value) + '" ' + (extra || '') + '></label>'; }
  function crop(interval, range) { var start = Math.max(interval[0], range[0]), end = Math.min(interval[1], range[1]); return end > start ? [start, end] : null; }
  function union(intervals) {
    var sorted = intervals.filter(function (i) { return i && i[1] > i[0]; }).map(function (i) { return i.slice(); }).sort(function (a, b) { return a[0] - b[0]; });
    var out = [];
    sorted.forEach(function (i) { var last = out[out.length - 1]; if (last && i[0] <= last[1]) last[1] = Math.max(last[1], i[1]); else out.push(i); });
    return out;
  }
  function subtract(available, blocked) {
    var result = union(available);
    union(blocked).forEach(function (b) {
      var next = [];
      result.forEach(function (a) { if (b[1] <= a[0] || b[0] >= a[1]) next.push(a); else { if (b[0] > a[0]) next.push([a[0], b[0]]); if (b[1] < a[1]) next.push([b[1], a[1]]); } });
      result = next;
    });
    return result;
  }
  function duration(intervals) { return intervals.reduce(function (total, i) { return total + i[1] - i[0]; }, 0); }
  function availableMinutes(r, range) { return duration(subtract((r.available || []).map(function (i) { return crop(i, range); }).filter(Boolean), r.unavailable || [])); }
  function resourceSegments(list, type, range, taskIds) {
    var seen = new Set(), result = [];
    list.forEach(function (a) {
      if (taskIds && !taskIds.has(a.taskId)) return;
      (a.segments || []).forEach(function (s) {
        var id = type === 'person' ? s.personId : s.machineId;
        if (!id) return;
        var cut = crop([s.start, s.end], range); if (!cut) return;
        var key = [a.batchId || a.taskId, id, s.kind, s.start, s.end].join('|');
        if (seen.has(key)) return; seen.add(key);
        result.push({ resourceId: id, start: cut[0], end: cut[1], taskId: a.taskId, batchId: a.batchId, kind: s.kind });
      });
    });
    return result;
  }
  function segmentMinutes(segments) { return segments.reduce(function (total, s) { return total + s.end - s.start; }, 0); }
  function recordAction(taskId, action, values, previous) {
    var s = state(), t = task(taskId), a = assignment(taskId), members = t.batchId && action !== 'report' ? s.tasks.filter(function (other) { return other.batchId === t.batchId; }) : [t];
    s.operationRecords = s.operationRecords || [];
    members.filter(function (member) { return action === 'report' || previous[member.id] !== member.status; }).forEach(function (member) {
      var assigned = assignment(member.id) || a;
      s.operationRecords.push({ id: 'OP-' + Date.now() + '-' + s.operationRecords.length, taskId: member.id, action: action, minute: s.now,
        good: action === 'report' ? num(values.good) : 0, bad: action === 'report' ? num(values.bad) : 0, reason: values.reason || '',
        statusAfter: member.status, priorStatus: previous[member.id], personId: assigned && assigned.personId, machineId: assigned && assigned.machineId,
        versionId: s.published.id, batchId: member.batchId || null, assignment: assigned ? clone(assigned) : null });
    });
  }
  function performReport(taskId, action, values, panel) {
    var previous = {}; state().tasks.forEach(function (t) { previous[t.id] = t.status; });
    var result = window.Engine.report(state(), taskId, action, values || {});
    if (!result.ok) { if (panel) errorBox(panel, result.message); else window.UI.toast(result.message); return false; }
    recordAction(taskId, action, values || {}, previous);
    if (panel) window.UI.closeModal();
    window.A.commit(result.message || '现场记录已保存'); return true;
  }
  function executionRows() {
    var f = filters('execution', { workshop: '', person: '', status: '', search: '' });
    return assignments().map(function (a) { return { a: a, t: task(a.taskId) }; }).filter(function (item) {
      if (!item.t) return false;
      return (!f.workshop || item.t.workshop === f.workshop) && (!f.person || (item.a.segments || []).some(function (s) { return s.personId === f.person; })) && (!f.status || item.t.status === f.status) && (!f.search || [item.t.id, item.t.orderId, item.t.product, item.t.op].join(' ').toLowerCase().includes(f.search.toLowerCase()));
    }).sort(function (a, b) { return ({ running: 0, paused: 1, ready: 2, done: 3 }[a.t.status] - { running: 0, paused: 1, ready: 2, done: 3 }[b.t.status]) || a.a.start - b.a.start; });
  }
  function overdueRunning() { return state().tasks.filter(function (t) { var a = assignment(t.id); return t.status === 'running' && a && state().now > a.end; }); }
  function overdueNotice() { var late = overdueRunning(); return late.length ? '<div class="notice warning">' + window.A.icon('triangle-alert') + '<span>' + late.length + ' 道工序超出计划结束仍在执行：' + esc(late.map(function (t) { return t.id; }).join('、')) + '。资源尚未确认释放。</span></div>' : ''; }
  Views.execution = function () {
    var s = state(), f = filters('execution', { workshop: '', person: '', status: '', search: '' }), rows = executionRows();
    var clock = '<div class="ops-clock"><span>' + window.A.icon('clock-3') + '现场时间 <strong>' + esc(window.A.fmt(s.now)) + '</strong></span>' + button('ops-clock', '', '调整时间', 'calendar-clock') + button('ops-tick', '30', '+30 分钟', 'skip-forward') + '</div>';
    var toolbar = '<div class="ops-toolbar">' + selectFilter('workshop', '车间', workshops(), f.workshop) + selectFilter('person', '操作人员', [['', '全部人员']].concat(s.resources.filter(function (r) { return r.type === 'person'; }).map(function (r) { return [r.id, r.name]; })), f.person) + selectFilter('status', '任务状态', [['', '全部状态']].concat(Object.keys(statusNames).map(function (key) { return [key, statusNames[key]]; })), f.status) + inputFilter('search', '任务 / 订单 / 产品', f.search) + '</div>';
    var metrics = '<div class="ops-metrics">' + metric('当前执行', rows.filter(function (r) { return r.t.status === 'running'; }).length, '工序任务') + metric('等待处理', rows.filter(function (r) { return r.t.status === 'paused'; }).length, '暂停任务', 'ops-amber') + metric('已报完', rows.filter(function (r) { return r.t.status === 'done'; }).length, '按任务数量') + metric('正式版本', s.published.id, '现场执行依据') + '</div>';
    var body = rows.map(function (r) {
      var t = r.t, a = r.a, total = num(t.good) + num(t.bad), percent = Math.min(100, total / Math.max(1, t.qty) * 100);
      var actions = t.status === 'ready' ? button('ops-start', t.id, '开工', 'play', 'primary small') : t.status === 'running' ? button('ops-report', t.id, '报工', 'clipboard-check', 'primary small') + button('ops-pause', t.id, '暂停', 'pause', 'small') : t.status === 'paused' ? button('ops-resume', t.id, '恢复', 'play', 'small') : window.A.badge('记录完成', 'success');
      if (t.status === 'done' && num(t.bad) > num(t.disposedBad)) actions += button('ops-dispose', t.id, '不良处置', 'git-pull-request', 'small');
      actions += button('ops-task', t.id, '详情', 'panel-right-open', 'small ghost');
      return ['<button class="ops-task-link" data-action="ops-task" data-id="' + esc(t.id) + '">' + esc(t.op) + '</button><small class="ops-sub">' + esc(t.id) + ' · ' + esc(t.product) + (t.batchId ? ' · ' + esc(t.batchId) : '') + '</small>', esc(t.orderId) + '<small class="ops-sub">' + esc(t.workshop) + ' / ' + esc(t.wc) + '</small>', esc(uniq((a.segments || []).map(function (seg) { return seg.personId ? resourceName(seg.personId) : ''; }).filter(Boolean)).join('、') || '无人工段') + '<small class="ops-sub">' + esc(resourceName(a.machineId)) + '</small>', '<span class="ops-nowrap">' + esc(window.A.fmt(a.start)) + '</span><small class="ops-sub">至 ' + esc(window.A.fmt(a.end)) + '</small>', window.A.badge(statusNames[t.status] || t.status, statusTones[t.status]), '<div class="ops-quantity"><span><b>' + esc(total) + '</b> / ' + esc(t.qty) + '</span><div class="ops-progress"><i style="width:' + percent + '%"></i></div><small>合格 ' + esc(t.good) + ' · 不良 ' + esc(t.bad) + '</small></div>', '<div class="ops-row-actions">' + actions + '</div>'];
    });
    return heading('现场执行', '正式计划 ' + s.published.id + ' · ' + rows.length + ' 道工序', button('ops-execution-csv', '', '导出', 'download')) + clock + overdueNotice() + metrics + toolbar + table(['工序 / 产品', '订单 / 车间', '执行资源', '正式时间', '状态', '数量进度', '操作'], body);
  };
  Actions['ops-task'] = function (buttonElement) { window.A.task(buttonElement.dataset.id); };
  Actions['ops-clock'] = function () {
    modalForm('现场业务时间', field('time', '日期与时间', datetime(state().now), 'datetime-local', 'required') , '更新时间', function (data, panel) {
      var value = minuteOf(data.get('time'));
      if (!Number.isFinite(value) || value < state().now) { errorBox(panel, '现场时间不能早于当前时间。'); return; }
      state().now = value; window.A.audit('clock', '现场时间更新为 ' + window.A.fmt(value)); window.UI.closeModal(); window.A.commit('现场时间已更新');
    });
  };
  Actions['ops-tick'] = function (element) { state().now += num(element.dataset.id); window.A.audit('clock', '现场时间推进至 ' + window.A.fmt(state().now)); window.A.commit(); };
  Actions['ops-start'] = function (element) {
    var id = element.dataset.id, a = assignment(id), t = task(id);
    var body = '<div class="ops-confirm-detail"><strong>' + esc(t.product + ' · ' + t.op) + '</strong><p>' + esc(window.A.fmt(a.start) + ' 至 ' + window.A.fmt(a.end)) + '</p><p>' + esc(resourceName(a.personId) + ' / ' + resourceName(a.machineId)) + '</p></div>';
    if (state().now < a.start) body += '<label class="ops-check"><input name="advance" type="checkbox" checked>将现场时间推进至 ' + esc(window.A.fmt(a.start)) + '</label>';
    if (t.batchId) body += '<p class="ops-inline-note">' + esc(t.batchId) + ' 批次成员同步开工</p>';
    modalForm('确认开工', body, '开工', function (data, panel) {
      var oldNow = state().now; if (data.get('advance')) state().now = Math.max(state().now, a.start);
      if (!performReport(id, 'start', {}, panel)) state().now = oldNow;
    });
  };
  Actions['ops-pause'] = function (element) {
    modalForm('暂停工序', '<label class="field"><span>暂停原因</span><select name="reason" required><option value="">请选择</option><option>设备故障</option><option>缺料等待</option><option>质量异常</option><option>人员调度</option><option>其他</option></select></label><label class="field"><span>现场说明</span><textarea name="detail" maxlength="300"></textarea></label>', '确认暂停', function (data, panel) { performReport(element.dataset.id, 'pause', { reason: data.get('reason') + (data.get('detail') ? '：' + data.get('detail') : '') }, panel); });
  };
  Actions['ops-resume'] = function (element) { performReport(element.dataset.id, 'resume', {}); };
  Actions['ops-report'] = function (element) {
    var id = element.dataset.id, t = task(id), a = assignment(id), remaining = Math.max(0, t.qty - num(t.good) - num(t.bad));
    var progress = typeof window.Engine.actualProgress === 'function' ? window.Engine.actualProgress(state(), t) : null;
    var canReport = progress ? Math.max(0, progress.qty - num(t.good) - num(t.bad)) : remaining;
    var eventId = 'REPORT-' + id + '-' + Date.now();
    var body = '<div class="ops-confirm-detail"><strong>' + esc(t.product + ' · ' + t.op) + '</strong><p>已报合格 ' + esc(t.good) + '，不良 ' + esc(t.bad) + '，待报 ' + remaining + '</p></div><div class="form-grid">' + field('good', '本次合格数', remaining, 'number', 'min="0" max="' + remaining + '" step="1" required') + field('bad', '本次不良数', 0, 'number', 'min="0" max="' + remaining + '" step="1" required') + '</div><label class="field"><span>报工说明 / 不良原因</span><textarea name="reason" maxlength="300"></textarea></label>';
    if (state().now < a.end) body += '<label class="ops-check"><input type="checkbox" name="advance">推进至计划结束 ' + esc(window.A.fmt(a.end)) + '</label>';
    body += '<p class="ops-inline-note">当前可报上限 ' + canReport + ' 件 · 已报 ' + (num(t.good) + num(t.bad)) + ' / ' + t.qty + '</p>';
    modalForm('数量报工', body, '提交报工', function (data, panel) {
      var good = Number(data.get('good')), bad = Number(data.get('bad')), reason = String(data.get('reason') || '').trim();
      if (![good, bad].every(function (v) { return Number.isInteger(v) && v >= 0; }) || good + bad < 1 || good + bad > remaining) { errorBox(panel, '本次合格与不良合计须在 1 至 ' + remaining + ' 之间。'); return; }
      if (bad && !reason) { errorBox(panel, '请填写不良原因。'); return; }
      var oldNow = state().now; if (data.get('advance')) state().now = Math.max(state().now, a.end);
      if (!performReport(id, 'report', { good: good, bad: bad, reason: reason, eventId: eventId }, panel)) state().now = oldNow;
    });
  };
  Actions['ops-dispose'] = function (element) {
    var t = task(element.dataset.id), remaining = Math.max(0, num(t.bad) - num(t.disposedBad));
    var body = '<div class="ops-confirm-detail"><strong>' + esc(t.product + ' · ' + t.op) + '</strong><p>不良 ' + num(t.bad) + ' · 已处置 ' + num(t.disposedBad) + ' · 待处置 ' + remaining + '</p></div><label class="field"><span>处置方式</span><select name="kind"><option value="rework">返工复检</option><option value="scrap">报废并补产</option></select></label>' + field('qty', '本次处置数量', remaining, 'number', 'min="1" max="' + remaining + '" step="1" required') + '<label class="field"><span>质量处置原因</span><textarea name="reason" required maxlength="300"></textarea></label><p class="ops-inline-note">生成的返工 / 补产任务需重新排程发布。</p>';
    modalForm('末道不良处置', body, '生成任务', function (data, panel) {
      var qty = Number(data.get('qty'));
      if (!Number.isInteger(qty) || qty < 1 || qty > remaining) { errorBox(panel, '处置数量须在 1 至 ' + remaining + ' 之间。'); return; }
      if (typeof window.Engine.dispose !== 'function') { errorBox(panel, '当前引擎尚未提供质量处置接口。'); return; }
      var result = window.Engine.dispose(state(), t.id, data.get('kind'), qty, String(data.get('reason')).trim());
      if (!result.ok) { errorBox(panel, result.message); return; }
      window.UI.closeModal(); window.A.commit(result.message || '已生成返工 / 补产任务');
    });
  };
  Actions['ops-execution-csv'] = function () { window.A.csv('现场任务-' + dateString(state().now) + '.csv', ['任务', '订单', '产品', '工序', '车间', '人员', '设备', '开始', '结束', '状态', '需求', '合格', '不良'], executionRows().map(function (r) { return [r.t.id, r.t.orderId, r.t.product, r.t.op, r.t.workshop, resourceName(r.a.personId), resourceName(r.a.machineId), window.A.fmt(r.a.start), window.A.fmt(r.a.end), statusNames[r.t.status], r.t.qty, r.t.good, r.t.bad]; })); };
  PageEvents.execution = function () { bindFilters('execution'); };

  // 已发生作业复用引擎阶段计算；数量按报工业务日期归属。
  function actualSegments(type, range, taskIds) {
    var s = state(), records = s.operationRecords || [], result = [], grouped = {};
    s.tasks.forEach(function (t) {
      if (taskIds && !taskIds.has(t.id)) return;
      var progress = typeof window.Engine.actualProgress === 'function' ? window.Engine.actualProgress(s, t) : null;
      if (progress && Array.isArray(progress.actualSegments)) {
        progress.actualSegments.forEach(function (seg) {
          var id = type === 'person' ? seg.personId : seg.machineId;
          var interval = id && crop([seg.start, seg.end], range); if (!interval) return;
          var key = [seg.batchId || t.batchId || t.id, id, seg.kind].join('|');
          grouped[key] = grouped[key] || { resourceId: id, taskId: t.id, intervals: [] };
          grouped[key].intervals.push(interval);
        });
        return;
      }
      var timeline = records.filter(function (r) { return r.taskId === t.id; }).sort(function (a, b) { return a.minute - b.minute; });
      var active = null, snap = null;
      function addUntil(end) {
        if (active === null || !snap) return;
        (snap.segments || []).forEach(function (seg) {
          var id = type === 'person' ? seg.personId : seg.machineId; if (!id) return;
          var interval = crop([Math.max(active, seg.start), Math.min(end, seg.end)], range); if (!interval) return;
          var key = [snap.batchId || t.id, id, seg.kind].join('|');
          grouped[key] = grouped[key] || { resourceId: id, taskId: t.id, intervals: [] };
          grouped[key].intervals.push(interval);
        });
        if (end > snap.end) {
          var last = (snap.segments || []).slice().sort(function (a, b) { return b.end - a.end; })[0];
          var heldId = last && (type === 'person' ? last.personId : last.machineId);
          var overrun = heldId && crop([Math.max(active, snap.end), end], range);
          if (overrun) {
            var heldKey = [snap.batchId || t.id, heldId, 'overrun'].join('|');
            grouped[heldKey] = grouped[heldKey] || { resourceId: heldId, taskId: t.id, intervals: [] };
            grouped[heldKey].intervals.push(overrun);
          }
        }
      }
      if (t.activity && t.activity.length) {
        t.activity.forEach(function (activity) {
          active = activity.start;
          var startRecord = timeline.find(function (r) { return (r.action === 'start' || r.action === 'resume') && r.minute === activity.start; });
          snap = startRecord && startRecord.assignment || assignment(t.id);
          addUntil(activity.end == null ? s.now : activity.end);
        });
      } else {
        timeline.forEach(function (r) {
          if (r.action === 'start' || r.action === 'resume') { if (active !== null) addUntil(r.minute); active = r.minute; snap = r.assignment || assignment(t.id); }
          if (r.action === 'pause' || (r.action === 'report' && r.statusAfter === 'done')) { addUntil(r.minute); active = null; }
        });
        if (active !== null) addUntil(s.now);
      }
    });
    Object.values(grouped).forEach(function (group) { union(group.intervals).forEach(function (interval) { result.push({ resourceId: group.resourceId, taskId: group.taskId, start: interval[0], end: interval[1] }); }); });
    return result;
  }
  function dailyData() {
    var s = state(), f = filters('daily', { date: dateString(s.now), workshop: '' }), range = bounds(f.date);
    if (!Number.isFinite(range[0])) return { f: f, error: '请选择生产日期。' };
    var baseline = s.dailyBaselines && s.dailyBaselines[f.date], list = baseline ? baseline.assignments : s.published.assignments;
    var records = (s.operationRecords || []).filter(function (r) { return r.action === 'report' && r.minute >= range[0] && r.minute < range[1]; });
    var groups = {};
    var referenceTasks = baseline && baseline.tasks || s.tasks;
    var dailyTasks = referenceTasks.concat(s.tasks.filter(function (t) { return !referenceTasks.some(function (prior) { return prior.id === t.id; }); }));
    dailyTasks.filter(function (t) { return !f.workshop || t.workshop === f.workshop; }).forEach(function (t) {
      var a = list.find(function (item) { return item.taskId === t.id; });
      var reports = records.filter(function (r) { return r.taskId === t.id; });
      var intersects = a && (a.segments || []).some(function (seg) { return crop([seg.start, seg.end], range); });
      var actualTask = task(t.id), activeToday = actualTask && (actualTask.activity || []).some(function (activity) { return crop([activity.start, activity.end == null ? s.now : activity.end], range); });
      if (!intersects && !reports.length && !activeToday) return;
      var key = t.batchId ? t.batchId : t.id;
      if (!groups[key]) groups[key] = { id: key, tasks: [], ids: new Set(), workshop: t.workshop, wc: t.wc, op: t.op, planned: 0, good: 0, bad: 0, products: [], records: [] };
      var g = groups[key]; g.tasks.push(t); g.ids.add(t.id); g.products.push(t.product + ' × ' + t.qty);
      if (a) {
        if (t.batchId || num(t.unload) > 0) { if (a.end >= range[0] && a.end < range[1]) g.planned += t.qty; }
        else g.planned += (a.segments || []).filter(function (seg) { return seg.kind === 'run' && seg.end >= range[0] && seg.end < range[1]; }).reduce(function (sum, seg) { return sum + num(seg.qty); }, 0);
      }
      g.good += reports.reduce(function (n, r) { return n + r.good; }, 0); g.bad += reports.reduce(function (n, r) { return n + r.bad; }, 0); g.records = g.records.concat(reports);
    });
    var rows = Object.values(groups).map(function (g) {
      g.planPerson = segmentMinutes(resourceSegments(list, 'person', range, g.ids)); g.planMachine = segmentMinutes(resourceSegments(list, 'machine', range, g.ids));
      g.actualPerson = segmentMinutes(actualSegments('person', range, g.ids)); g.actualMachine = segmentMinutes(actualSegments('machine', range, g.ids));
      g.people = uniq(resourceSegments(list, 'person', range, g.ids).map(function (seg) { return resourceName(seg.resourceId); })).join('、');
      return g;
    });
    var ids = new Set(rows.flatMap(function (r) { return Array.from(r.ids); }));
    var finalIds = new Set(s.tasks.filter(function (t) { return t.recoveryOf || !s.tasks.some(function (other) { return !other.recoveryOf && other.orderId === t.orderId && other.lineId === t.lineId && (other.deps || []).some(function (d) { return d.taskId === t.id; }); }); }).map(function (t) { return t.id; }));
    return { f: f, range: range, rows: rows, version: baseline ? baseline.versionId : s.published.id,
      planPerson: segmentMinutes(resourceSegments(list, 'person', range, ids)), planMachine: segmentMinutes(resourceSegments(list, 'machine', range, ids)),
      actualPerson: segmentMinutes(actualSegments('person', range, ids)), actualMachine: segmentMinutes(actualSegments('machine', range, ids)),
      finalGood: records.filter(function (r) { return ids.has(r.taskId) && finalIds.has(r.taskId); }).reduce(function (sum, r) { return sum + r.good; }, 0) };
  }
  Views.daily = function () {
    var d = dailyData();
    if (d.error) return heading('车间每日生产表', '') + '<div class="ops-toolbar">' + inputFilter('date', '生产日期', d.f.date, 'date') + selectFilter('workshop', '车间', workshops(), d.f.workshop) + '</div>' + window.A.empty(d.error);
    var rows = d.rows.map(function (g) { return [esc(g.workshop) + '<small class="ops-sub">' + esc(g.wc) + '</small>', '<button class="ops-task-link" data-action="ops-task" data-id="' + esc(g.tasks[0].id) + '">' + esc(g.op) + '</button><small class="ops-sub">' + esc(g.id) + '</small>', esc(g.products.join(' / ')), esc(g.people || '—'), '<strong>' + g.planned + '</strong>', '<strong>' + g.good + '</strong><small class="ops-sub">不良 ' + g.bad + '</small>', hours(g.planPerson) + ' / ' + hours(g.actualPerson), hours(g.planMachine) + ' / ' + hours(g.actualMachine), g.planned ? window.A.badge(Math.round(g.good / g.planned * 100) + '%', g.good >= g.planned ? 'success' : 'neutral') : window.A.badge('当日无完工计划', 'neutral')]; });
    return heading('车间每日生产表', d.f.date + ' · 日计划基线 ' + d.version, button('ops-daily-csv', '', '导出日报', 'download') + button('ops-print', '', '打印', 'printer')) + '<div class="ops-toolbar">' + inputFilter('date', '生产日期', d.f.date, 'date') + selectFilter('workshop', '车间', workshops(), d.f.workshop) + '</div>' + '<div class="ops-metrics">' + metric('计划人工', hours(d.planPerson) + ' h', '实际记录 ' + hours(d.actualPerson) + ' h') + metric('计划设备', hours(d.planMachine) + ' h', '实际记录 ' + hours(d.actualMachine) + ' h') + metric('工序合格产出', d.rows.reduce(function (n, g) { return n + g.good; }, 0), '各工序数量合计') + metric('末道合格产出', d.finalGood, '各产品末道数量合计') + '</div>' + section('工作中心 / 工序明细', table(['车间 / 中心', '工序 / 批次', '产品及数量', '人员', '计划完成', '实报数量', '人时 计划 / 实际', '机时 计划 / 实际', '数量达成'], rows), '人时、机时按阶段去重') + '<p class="ops-footnote">基线 ' + esc(d.version) + ' · 统计区间 ' + esc(d.f.date) + ' 00:00–24:00 · 数量以本次报工日期归属</p>';
  };
  PageEvents.daily = function () {
    bindFilters('daily');
    var s = state(), date = window.A.filters.daily.date; s.dailyBaselines = s.dailyBaselines || {};
    if (Number.isFinite(minuteOf(date)) && !s.dailyBaselines[date]) { s.dailyBaselines[date] = { versionId: s.published.id, assignments: clone(s.published.assignments), tasks: clone(s.tasks), at: new Date().toISOString() }; window.A.commit(); }
  };
  Actions['ops-daily-csv'] = function () { var d = dailyData(); window.A.csv('车间每日生产表-' + d.f.date + '.csv', ['日期', '基线', '车间', '工作中心', '工序', '任务或批次', '产品', '人员', '计划完成数量', '合格数量', '不良数量', '计划人时', '实际记录人时', '计划机时', '实际记录机时'], d.rows.map(function (g) { return [d.f.date, d.version, g.workshop, g.wc, g.op, g.id, g.products.join(' / '), g.people, g.planned, g.good, g.bad, hours(g.planPerson), hours(g.actualPerson), hours(g.planMachine), hours(g.actualMachine)]; })); };
  Actions['ops-print'] = function () { window.print(); };

  function demandMinutes(t) {
    var remaining = Math.max(0, t.qty - num(t.good) - num(t.bad));
    var run = num(t.run) || (num(t.rate) > 0 ? remaining / t.rate * 60 : 0);
    return num(t.setup) + num(t.unload) + (t.mode === 'auto' || t.mode === 'batch' ? 0 : run);
  }
  function capacityData() {
    var s = state(), f = filters('capacity', { from: '2026-09-14', to: '2026-09-18', workshop: '', granularity: 'day', source: 'published', search: '' });
    var from = minuteOf(f.from), to = minuteOf(f.to) + 1440;
    if (!Number.isFinite(from) || !Number.isFinite(to) || to <= from || to - from > 31 * 1440) return { f: f, error: '请选择不超过 31 天且结束不早于开始的日期区间。' };
    var range = [from, to], list = assignments(f.source), employees = s.resources.filter(function (r) { return r.type === 'person' && (!f.workshop || r.workshop === f.workshop) && (!f.search || [r.name, r.id, Object.keys(r.skills || {}).join(' ')].join(' ').includes(f.search)); });
    var segments = resourceSegments(list, 'person', range), employeeIds = new Set(employees.map(function (r) { return r.id; }));
    segments = segments.filter(function (seg) { return employeeIds.has(seg.resourceId); });
    var people = employees.map(function (r) {
      var own = segments.filter(function (seg) { return seg.resourceId === r.id; }), available = availableMinutes(r, range), booked = segmentMinutes(own), occupied = duration(union(own.map(function (seg) { return [seg.start, seg.end]; }))), conflict = booked > occupied;
      var outside = own.some(function (seg) { return availableMinutes(r, [seg.start, seg.end]) < seg.end - seg.start; });
      return { r: r, available: available, booked: booked, remaining: available - booked, conflict: conflict || outside, own: own };
    });
    var pending = s.tasks.filter(function (t) { return t.status !== 'done' && !list.some(function (a) { return a.taskId === t.id; }) && (!f.workshop || t.workshop === f.workshop); });
    var pendingSeen = new Set(), pendingMinutes = 0;
    pending.forEach(function (t) { var key = t.batchId || t.id; if (!pendingSeen.has(key)) { pendingSeen.add(key); pendingMinutes += demandMinutes(t); } });
    var buckets = [];
    for (var start = from; start < to; start += (f.granularity === 'half' ? 720 : 1440)) {
      var end = Math.min(to, start + (f.granularity === 'half' ? 720 : 1440));
      var available = employees.reduce(function (sum, r) { return sum + availableMinutes(r, [start, end]); }, 0);
      var booked = segmentMinutes(segments.map(function (seg) { var clip = crop([seg.start, seg.end], [start, end]); return clip ? { start: clip[0], end: clip[1] } : null; }).filter(Boolean));
      var conflicts = people.filter(function (p) {
        var current = p.own.map(function (seg) { return crop([seg.start, seg.end], [start, end]); }).filter(Boolean);
        return duration(current) > duration(union(current)) || current.some(function (i) { return availableMinutes(p.r, i) < i[1] - i[0]; });
      }).length;
      buckets.push({ start: start, end: end, label: dateString(start) + (f.granularity === 'half' ? (new Date(BASE + start * 60000).getUTCHours() < 12 ? ' 上午' : ' 下午') : ''), available: available, booked: booked, conflicts: conflicts });
    }
    var cumulativeAvailable = 0, cumulativeBooked = 0;
    buckets.forEach(function (b) { cumulativeAvailable += b.available; cumulativeBooked += b.booked; b.cumAvailable = cumulativeAvailable; b.cumBooked = cumulativeBooked; });
    return { f: f, range: range, people: people, pending: pending, pendingMinutes: pendingMinutes, buckets: buckets, available: people.reduce(function (sum, p) { return sum + p.available; }, 0), booked: people.reduce(function (sum, p) { return sum + p.booked; }, 0) };
  }
  function loadBar(booked, available, conflict) {
    var ratio = available > 0 ? booked / available : booked ? 2 : 0;
    return '<div class="ops-load"><div class="ops-load-track"><i class="' + (conflict || ratio > 1 ? 'over' : ratio > 0.85 ? 'busy' : '') + '" style="width:' + Math.min(100, ratio * 100) + '%"></i></div><span>' + Math.round(ratio * 100) + '%</span>' + (conflict ? window.A.badge('时段冲突', 'danger') : '') + '</div>';
  }
  Views.capacity = function () {
    var d = capacityData(), f = d.f;
    var toolbar = '<div class="ops-toolbar">' + inputFilter('from', '开始日期', f.from, 'date') + inputFilter('to', '结束日期', f.to, 'date') + selectFilter('workshop', '归属车间', workshops(), f.workshop) + selectFilter('source', '计划版本', [['published', '当前正式'], ['draft', '当前草稿']], f.source) + selectFilter('granularity', '时间粒度', [['day', '按日'], ['half', '按半日']], f.granularity) + inputFilter('search', '人员 / 技能', f.search) + '</div>';
    if (d.error) return heading('人力累计产能', '按员工及实际可用时段汇总') + toolbar + window.A.empty(d.error);
    var metrics = '<div class="ops-metrics">' + metric('可用产能', hours(d.available) + ' h', d.people.length + ' 名员工，按人员去重') + metric('已排人工', hours(d.booked) + ' h', '已分配阶段占用') + metric('未排需求', hours(d.pendingMinutes) + ' h', d.pending.length + ' 道待分配工序', 'ops-amber') + metric('剩余容量', hours(d.available - d.booked) + ' h', '可用减已排', d.booked > d.available ? 'ops-red' : '') + '</div>';
    var chart = '<div class="ops-capacity-chart" role="img" aria-label="每日已排工时与可用工时比较">' + d.buckets.map(function (b) { return '<div class="ops-chart-row"><span>' + esc(b.label.slice(5)) + '</span>' + loadBar(b.booked, b.available, b.conflicts > 0) + '<span class="ops-chart-value">' + hours(b.booked) + ' / ' + hours(b.available) + ' h</span></div>'; }).join('') + '</div>';
    var people = d.people.map(function (p) { return [esc(p.r.name) + '<small class="ops-sub">' + esc(p.r.id) + '</small>', esc(p.r.workshop), '<span class="ops-skills">' + Object.keys(p.r.skills || {}).map(function (skill) { return esc(skill) + ' ' + esc(p.r.skills[skill]) + '级'; }).join(' · ') + '</span>', hours(p.available), hours(p.booked), '<span class="' + (p.remaining < 0 ? 'ops-red' : '') + '">' + hours(p.remaining) + '</span>', loadBar(p.booked, p.available, p.conflict), button('ops-person-load', p.r.id, '任务', 'list-filter', 'small ghost')]; });
    var cumulative = d.buckets.map(function (b) { return [esc(b.label), hours(b.available), hours(b.booked), hours(b.available - b.booked), hours(b.cumAvailable), hours(b.cumBooked), hours(b.cumAvailable - b.cumBooked), b.conflicts ? window.A.badge(b.conflicts + ' 人冲突', 'danger') : window.A.badge('无时段冲突', 'success')]; });
    var pending = d.pending.map(function (t) { return [esc(t.id), esc(t.product + ' · ' + t.op), esc(t.workshop + ' / ' + t.wc), esc(t.skill || '按工艺'), t.qty, hours(demandMinutes(t)) + (t.batchId ? '（批共享）' : ''), button('ops-task', t.id, '详情', 'panel-right-open', 'small ghost')]; });
    return heading('人力累计产能', f.from + ' 至 ' + f.to + ' · ' + (f.source === 'draft' ? '当前草稿' : '正式版本 ' + state().published.id), button('ops-capacity-csv', '', '导出产能', 'download') + button('ops-print', '', '打印', 'printer')) + toolbar + overdueNotice() + metrics + section('时段负荷', chart, '已排 / 可用工时') + section('人员容量明细', table(['员工', '归属车间', '有效技能', '可用 h', '已排 h', '剩余 h', '负荷与冲突', ''], people)) + section('累计工时表', table(['时段', '当期可用', '当期已排', '当期剩余', '累计可用', '累计已排', '累计剩余', '时段检查'], cumulative)) + section('未排工序需求', pending.length ? table(['任务', '产品 / 工序', '执行车间 / 中心', '所需技能', '任务量', '待分配人时', ''], pending) : '<div class="ops-clear-state">' + window.A.icon('circle-check') + '<span>当前范围内工序均已分配</span></div>', '未指定员工的需求单列，不摊入每个候选人的负荷');
  };
  PageEvents.capacity = function () { bindFilters('capacity'); };
  Actions['ops-capacity-csv'] = function () {
    var d = capacityData(); if (d.error) return window.UI.toast(d.error);
    window.A.csv('人力累计产能-' + d.f.from + '-' + d.f.to + '.csv', ['记录类型', '人员或时段', '车间', '可用人时', '已排人时', '剩余人时', '累计可用', '累计已排', '累计剩余', '时段冲突'], d.people.map(function (p) { return ['员工', p.r.name, p.r.workshop, hours(p.available), hours(p.booked), hours(p.remaining), '', '', '', p.conflict ? '是' : '否']; }).concat(d.buckets.map(function (b) { return ['时段', b.label, d.f.workshop || '全部', hours(b.available), hours(b.booked), hours(b.available - b.booked), hours(b.cumAvailable), hours(b.cumBooked), hours(b.cumAvailable - b.cumBooked), b.conflicts]; })).concat([['去重汇总', d.people.length + '人', d.f.workshop || '全部', hours(d.available), hours(d.booked), hours(d.available - d.booked), '', '', '', ''], ['未排需求', d.pending.length + '道工序', d.f.workshop || '全部', '', hours(d.pendingMinutes), '', '', '', '', '未分配到个人']]));
  };
  Actions['ops-person-load'] = function (element) {
    var d = capacityData(), person = d.people.find(function (p) { return p.r.id === element.dataset.id; }); if (!person) return;
    window.UI.drawer(person.r.name + ' · 人工占用', table(['工序', '阶段', '开始', '结束', '人时'], person.own.map(function (seg) { var t = task(seg.taskId); return [esc(t ? t.product + ' · ' + t.op : seg.taskId), esc({ setup: '准备', run: '运行', unload: '卸料' }[seg.kind] || seg.kind), esc(window.A.fmt(seg.start)), esc(window.A.fmt(seg.end)), hours(seg.end - seg.start)]; })));
  };

  function versions() {
    var s = state(), byId = {};
    (s.versions || []).forEach(function (v) { byId[v.id] = v; }); byId[s.published.id] = Object.assign({ note: '当前正式', at: '' }, byId[s.published.id] || {}, s.published);
    return Object.values(byId).sort(function (a, b) { return String(b.at || '').localeCompare(String(a.at || '')); });
  }
  function versionById(id) { if (id === 'draft') return { id: 'draft', assignments: state().draft.assignments, note: '当前草稿' }; return versions().find(function (v) { return v.id === id; }) || { id: id, assignments: [] }; }
  function diffData() {
    var s = state(), f = filters('versions', { base: s.published.id, compare: 'draft' });
    if (!versions().some(function (v) { return v.id === f.base; })) f.base = s.published.id;
    if (f.compare !== 'draft' && !versions().some(function (v) { return v.id === f.compare; })) f.compare = 'draft';
    var base = versionById(f.base), compare = versionById(f.compare), ids = uniq(base.assignments.concat(compare.assignments).map(function (a) { return a.taskId; }));
    var changes = ids.map(function (id) {
      var a = base.assignments.find(function (v) { return v.taskId === id; }), b = compare.assignments.find(function (v) { return v.taskId === id; });
      var changed = !a || !b || ['start', 'end', 'machineId', 'personId', 'batchId'].some(function (key) { return a[key] !== b[key]; }) || JSON.stringify(a.segments) !== JSON.stringify(b.segments);
      return { id: id, a: a, b: b, type: !a ? '新增' : !b ? '移出' : '调整', changed: changed };
    }).filter(function (item) { return item.changed; });
    return { f: f, base: base, compare: compare, changes: changes, before: window.Engine.metrics(s, base.assignments), after: window.Engine.metrics(s, compare.assignments) };
  }
  function assignmentText(a) { return a ? '<span>' + esc(window.A.fmt(a.start) + ' 至 ' + window.A.fmt(a.end)) + '</span><small class="ops-sub">' + esc(resourceName(a.personId) + ' / ' + resourceName(a.machineId)) + '</small>' : '<span class="muted">未排</span>'; }
  Views.versions = function () {
    var d = diffData(), s = state(), list = versions(), selection = list.map(function (v) { return [v.id, v.id + (v.id === s.published.id ? ' · 当前正式' : '')]; });
    var changes = d.changes.map(function (c) { var t = task(c.id); return ['<button class="ops-task-link" data-action="ops-task" data-id="' + esc(c.id) + '">' + esc(c.id) + '</button><small class="ops-sub">' + esc(t ? t.product + ' · ' + t.op : '') + '</small>', window.A.badge(c.type, c.type === '移出' ? 'warning' : 'info'), assignmentText(c.a), assignmentText(c.b), c.a && c.b ? '<span class="' + (c.b.end > c.a.end ? 'ops-red' : '') + '">' + (c.b.end - c.a.end > 0 ? '+' : '') + (c.b.end - c.a.end) + ' 分钟</span>' : '—']; });
    var orderRows = (d.after.orders || []).map(function (o) { var prior = (d.before.orders || []).find(function (old) { return old.id === o.id; }); return [esc(o.id), prior && prior.end != null ? esc(window.A.fmt(prior.end)) : '无法可靠估算', o.end != null ? esc(window.A.fmt(o.end)) : '无法可靠估算', o.end != null && prior && prior.end != null ? (o.end - prior.end > 0 ? '+' : '') + (o.end - prior.end) + ' 分钟' : '—', o.late ? window.A.badge('延期 ' + o.late + ' 分钟', 'warning') : window.A.badge(o.end == null ? '待排' : '交期内', o.end == null ? 'neutral' : 'success')]; });
    var history = list.map(function (v) { return ['<strong>' + esc(v.id) + '</strong>' + (v.id === s.published.id ? '<small class="ops-sub">当前正式</small>' : ''), esc(v.at ? new Date(v.at).toLocaleString('zh-CN', { hour12: false }) : '初始基线'), esc(v.note || '—'), v.assignments.length, esc(v.revision), button('ops-restore', v.id, '恢复为草稿', 'rotate-ccw', 'small')]; });
    return heading('计划版本与影响', '当前正式 ' + s.published.id + ' · 草稿基于 ' + s.draft.basedOn, button('ops-new-draft', '', '新建草稿', 'file-plus-2') + button('ops-publish', '', '发布计划', 'send', 'primary')) + '<div class="ops-toolbar">' + selectFilter('base', '比较基线', selection, d.f.base) + selectFilter('compare', '候选版本', [['draft', '当前草稿']].concat(selection), d.f.compare) + button('ops-diff-csv', '', '导出差异', 'download') + '</div>' + '<div class="ops-metrics">' + metric('变更工序', d.changes.length, '时间、资源或分段变化') + metric('候选未排', d.after.unplanned, '基线 ' + d.before.unplanned + ' 道') + metric('人工占用', round(d.after.personHours) + ' h', '基线 ' + round(d.before.personHours) + ' h') + metric('设备占用', round(d.after.machineHours) + ' h', '基线 ' + round(d.before.machineHours) + ' h') + '</div>' + section('任务差异', changes.length ? table(['任务', '变更', d.base.id, d.compare.id === 'draft' ? '当前草稿' : d.compare.id, '完工变化'], changes) : '<div class="ops-clear-state">' + window.A.icon('git-compare-arrows') + '<span>两个版本的任务安排一致</span></div>') + section('订单交期影响', table(['订单', '基线预计完工', '候选预计完工', '变化', '候选交期'], orderRows)) + section('正式版本记录', table(['版本', '发布时间', '说明', '工序数', '数据版本', '操作'], history));
  };
  PageEvents.versions = function () { bindFilters('versions'); };
  function restoreModal(version, title) {
    modalForm(title, '<div class="ops-confirm-detail"><strong>' + esc(version.id) + '</strong><p>' + version.assignments.length + ' 道工序</p></div><label class="field"><span>草稿说明</span><textarea name="note" maxlength="300" required>基于 ' + esc(version.id) + ' 重新编排</textarea></label>', '创建草稿', function (data) {
      state().draft = { assignments: clone(version.assignments), basedOn: state().published.id, revision: state().revision, note: String(data.get('note')) };
      window.A.audit('draft', String(data.get('note')) + '，实际生产记录保持原值'); window.UI.closeModal(); window.A.commit('新草稿已创建');
    });
  }
  Actions['ops-new-draft'] = function () { restoreModal(versionById(state().published.id), '从当前正式计划新建草稿'); };
  Actions['ops-restore'] = function (element) { restoreModal(versionById(element.dataset.id), '恢复旧版本为新草稿'); };
  Actions['ops-publish'] = function () {
    var issues = window.Engine.validate(state(), state().draft.assignments), errors = issues.filter(function (i) { return i.severity !== 'warning'; });
    var body = '<div class="ops-confirm-detail"><strong>草稿基于 ' + esc(state().draft.basedOn) + '</strong><p>' + state().draft.assignments.length + ' 道工序 · ' + errors.length + ' 项阻断</p></div>';
    if (errors.length) body += '<ul class="ops-issue-list">' + errors.slice(0, 8).map(function (i) { return '<li>' + esc(i.message) + '</li>'; }).join('') + '</ul>';
    body += '<label class="field"><span>发布说明</span><textarea name="note" maxlength="300" required></textarea></label>';
    modalForm('发布生产计划', body, '确认发布', function (data, panel) {
      var result = window.Engine.publish(state(), String(data.get('note')).trim());
      if (!result.ok) { errorBox(panel, result.message); return; }
      window.UI.closeModal(); window.A.commit(result.message || '生产计划已发布');
    });
  };
  Actions['ops-diff-csv'] = function () { var d = diffData(); window.A.csv('计划差异-' + d.base.id + '-' + d.compare.id + '.csv', ['任务', '变更', '基线开始', '基线结束', '基线人员', '基线设备', '候选开始', '候选结束', '候选人员', '候选设备'], d.changes.map(function (c) { return [c.id, c.type, c.a ? window.A.fmt(c.a.start) : '', c.a ? window.A.fmt(c.a.end) : '', c.a ? resourceName(c.a.personId) : '', c.a ? resourceName(c.a.machineId) : '', c.b ? window.A.fmt(c.b.start) : '', c.b ? window.A.fmt(c.b.end) : '', c.b ? resourceName(c.b.personId) : '', c.b ? resourceName(c.b.machineId) : '']; })); };

  function auditRows() {
    var f = filters('audit', { type: '', search: '', from: '', to: '' });
    return (state().events || []).filter(function (event) { var date = String(event.at || '').slice(0, 10); return (!f.type || event.type === f.type) && (!f.search || [event.id, event.message, event.taskId, event.type].join(' ').toLowerCase().includes(f.search.toLowerCase())) && (!f.from || date >= f.from) && (!f.to || date <= f.to); }).slice().reverse();
  }
  Views.audit = function () {
    var s = state(), f = filters('audit', { type: '', search: '', from: '', to: '' }), rows = auditRows();
    var body = rows.map(function (event) { return [esc(event.at ? new Date(event.at).toLocaleString('zh-CN', { hour12: false }) : '—'), window.A.badge(event.type || '操作', 'neutral'), '<span class="ops-audit-message">' + esc(event.message) + '</span><small class="ops-sub">' + esc(event.id || '') + '</small>', event.taskId ? '<button class="ops-task-link" data-action="ops-task" data-id="' + esc(event.taskId) + '">' + esc(event.taskId) + '</button>' : '—']; });
    return heading('操作审计', rows.length + ' 条记录 · 本地工作台', button('ops-audit-csv', '', '导出审计', 'download')) + '<div class="ops-toolbar">' + selectFilter('type', '事件类型', [['', '全部类型']].concat(uniq(s.events.map(function (event) { return event.type; })).map(function (type) { return [type, type]; })), f.type) + inputFilter('search', '记录 / 任务 / 内容', f.search) + inputFilter('from', '开始日期', f.from, 'date') + inputFilter('to', '结束日期', f.to, 'date') + button('ops-audit-reset', '', '清除筛选', 'filter-x') + '</div>' + table(['操作时间', '事件', '操作内容', '关联任务'], body);
  };
  PageEvents.audit = function () { bindFilters('audit'); };
  Actions['ops-audit-reset'] = function () { window.A.filters.audit = { type: '', search: '', from: '', to: '' }; window.A.render(); };
  Actions['ops-audit-csv'] = function () { window.A.csv('操作审计.csv', ['记录ID', '操作时间', '事件类型', '内容', '关联任务'], auditRows().map(function (event) { return [event.id, event.at, event.type, event.message, event.taskId || '']; })); };
  window.Operations = { capacityData: capacityData, dailyData: dailyData, actualSegments: actualSegments, resourceSegments: resourceSegments, availableMinutes: availableMinutes, demandMinutes: demandMinutes };
}());
