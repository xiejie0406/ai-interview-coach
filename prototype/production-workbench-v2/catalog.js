(function () {
  'use strict';
  var Views = window.Views = window.Views || {};
  var Actions = window.Actions = window.Actions || {};
  var PageEvents = window.PageEvents = window.PageEvents || {};
  var epoch = new Date(2026, 8, 14, 8, 0).getTime();
  var modeNames = { manual: '纯人工', machine: '人机协同', auto: '自动运行 / 人工装卸', batch: '固定周期批处理' };
  var statusNames = { ready: '待开工', running: '生产中', paused: '已暂停', done: '已完成' };
  function e(value) { return A.e(value); }
  function state() { return A.state; }
  function tasksFor(orderId, lineId) { return state().tasks.filter(function (t) { return t.orderId === orderId && (!lineId || t.lineId === lineId); }); }
  function routeTasks(orderId, lineId) { return tasksFor(orderId, lineId).filter(function (t) { return !t.recoveryChainId && !t.recoveryOf; }); }
  function resource(id) { return state().resources.find(function (r) { return r.id === id; }); }
  function task(id) { return state().tasks.find(function (t) { return t.id === id; }); }
  function order(id) { return state().orders.find(function (o) { return o.id === id; }); }
  function uid(prefix) { var n = Date.now().toString(36).toUpperCase(); return prefix + '-' + n + '-' + Math.random().toString(36).slice(2, 5).toUpperCase(); }
  function copy(value) { return JSON.parse(JSON.stringify(value)); }
  function locked(t) { return t.status !== 'ready' || Number(t.good || 0) + Number(t.bad || 0) + Number(t.reported || 0) > 0; }
  function values(form) { return Object.fromEntries(new FormData(form).entries()); }
  function input(name, label, value, type, attrs) { return '<div class="field"><label for="cat-' + e(name) + '">' + e(label) + '</label><input id="cat-' + e(name) + '" name="' + e(name) + '" type="' + (type || 'text') + '" value="' + e(value) + '" ' + (attrs || '') + '></div>'; }
  function options(items, selected) { return items.map(function (x) { var v = Array.isArray(x) ? x[0] : x; var text = Array.isArray(x) ? x[1] : x; return '<option value="' + e(v) + '"' + (String(v) === String(selected) ? ' selected' : '') + '>' + e(text) + '</option>'; }).join(''); }
  function select(name, label, items, selected, attrs) { if (name === 'priority') { items = [['100', '100 · 紧急'], ['80', '80 · 高'], ['60', '60 · 普通'], ['50', '50 · 常规']]; if (Number(selected) <= 3) selected = ({ 1: 100, 2: 80, 3: 60 })[selected] || 60; if (!items.some(function (x) { return Number(x[0]) === Number(selected); })) items.push([String(selected), String(selected)]); } return '<div class="field"><label for="cat-' + e(name) + '">' + e(label) + '</label><select id="cat-' + e(name) + '" name="' + e(name) + '" ' + (attrs || '') + '>' + options(items, selected) + '</select></div>'; }
  function change(message, type, taskId) { state().revision = Number(state().revision || 0) + 1; A.audit(type || 'master-data', message, taskId); UI.closeModal(); A.commit(message + '；草稿需重新排程与校验'); }
  function showError(form, text) { var el = form.querySelector('.cat-form-error'); el.textContent = text; el.hidden = false; el.scrollIntoView({ block: 'nearest' }); }
  function modal(title, body, onSave, wide, submitLabel) {
    var panel = UI.modal(title, '<form id="catalog-form">' + body + '<p class="cat-form-error" role="alert" hidden></p></form>', '<button type="button" class="btn" data-close-modal>取消</button><button type="submit" form="catalog-form" class="btn primary">' + A.icon('check') + e(submitLabel || '保存变更') + '</button>');
    panel.classList.add('cat-dialog'); if (wide) panel.classList.add('cat-wide-dialog');
    var form = panel.querySelector('form');
    form.addEventListener('submit', function (event) { event.preventDefault(); if (form.reportValidity()) onSave(form); });
    return form;
  }
  function datetime(mins) { var d = new Date(epoch + Number(mins || 0) * 60000); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0') + 'T' + String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0'); }
  function minute(value) { return Math.round((new Date(value).getTime() - epoch) / 60000); }
  function head(title, actions) { return '<div class="page-heading"><div><h1>' + e(title) + '</h1></div><div class="actions">' + (actions || '') + '</div></div>'; }
  function summary(items) { return '<div class="cat-summary">' + items.map(function (x) { return '<div><span>' + e(x[0]) + '</span><strong>' + e(x[1]) + '</strong></div>'; }).join('') + '</div>'; }
  function sub(title, extra) { return '<div class="section-heading"><h2>' + e(title) + '</h2>' + (extra || '') + '</div>'; }
  function dateLabel(n) { return '9 月 ' + (14 + n) + ' 日'; }
  function taskLabel(t) { return t.id + ' · ' + t.product + ' / ' + t.op; }
  function badgeStatus(t) { return A.badge(statusNames[t.status] || t.status, t.status === 'done' ? 'success' : t.status === 'running' ? 'info' : t.status === 'paused' ? 'warning' : 'neutral'); }
  function allocations() { return state().draft && state().draft.assignments || []; }
  function dueText(o) { var m = Engine.metrics(state(), allocations()).orders.find(function (x) { return x.id === o.id; }); return !m || m.end == null ? A.badge('尚未完整排程', 'warning') : '<span>' + e(A.fmt(m.end)) + '</span>' + (m.late > 0 ? '<div class="cell-sub negative">延期 ' + e(A.hours(m.late)) + ' 小时</div>' : '<div class="cell-sub positive">预计按期</div>'); }
  function filter(key, label, list) { return '<label class="cat-filter"><span>' + e(label) + '</span><select class="control" data-cat-filter="' + e(key) + '">' + options(list, A.filters[key] || '') + '</select></label>'; }
  function connectFilters() { document.querySelectorAll('[data-cat-filter]').forEach(function (el) { el.addEventListener('change', function () { A.filters[el.dataset.catFilter] = el.value; A.render(); }); }); }

  Views.orders = function () {
    var query = (A.filters.orderSearch || '').toLowerCase();
    var shown = state().orders.filter(function (o) { var text = [o.id, o.customer].concat(o.lines.map(function (l) { return l.product; })).join(' ').toLowerCase(); var bucket = o.priority >= 90 ? 1 : o.priority >= 70 ? 2 : 3; return text.includes(query) && (!A.filters.orderPriority || String(bucket) === A.filters.orderPriority); }).map(function (o) { return Object.assign({}, o, { priority: o.priority >= 90 ? 1 : o.priority >= 70 ? 2 : 3 }); });
    return head('生产订单', A.btn('cat-new-order', '', '新建生产订单', 'plus', 'primary')) + summary([['订单', state().orders.length], ['产品订单行', state().orders.reduce(function (n, o) { return n + o.lines.length; }, 0)], ['工序任务', state().tasks.length], ['未完整排程', Engine.metrics(state(), allocations()).orders.filter(function (x) { return x.end == null; }).length]]) + '<div class="toolbar"><div class="toolbar-left"><label class="search-field">' + A.icon('search') + '<span class="sr-only">搜索订单、客户或产品</span><input id="cat-order-search" placeholder="订单、客户或产品" value="' + e(A.filters.orderSearch || '') + '"></label>' + filter('orderPriority', '优先级', [['', '全部'], ['1', '1 · 紧急'], ['2', '2 · 高'], ['3', '3 · 普通']]) + '</div><span class="muted">' + shown.length + ' 张订单</span></div>' + (shown.length ? A.table(['订单 / 客户', '产品及需求数量', '工序', '生产承诺', '草稿预计完成', '优先级', '操作'], shown.map(function (o) { return ['<strong>' + e(o.id) + '</strong><div class="cell-sub">' + e(o.customer) + '</div>', o.lines.map(function (l) { return '<div>' + e(l.product) + ' <b>' + e(l.qty) + '</b> ' + e(l.unit || '件') + '</div>'; }).join(''), tasksFor(o.id).length + ' 道', e(A.fmt(o.due)), dueText(o), A.badge(o.priority === 1 ? '紧急' : o.priority === 2 ? '高' : '普通', o.priority === 1 ? 'danger' : o.priority === 2 ? 'warning' : 'neutral'), '<div class="actions">' + A.btn('cat-order-detail', o.id, '查看', 'arrow-up-right', 'small') + A.btn('cat-order-edit', o.id, '编辑', 'pencil', 'small') + '</div>']; })) : A.empty('没有匹配的生产订单'));
  };
  PageEvents.orders = function () { connectFilters(); var field = document.getElementById('cat-order-search'); if (field) field.addEventListener('change', function () { A.filters.orderSearch = field.value; A.render(); }); };

  function templates() { var list = []; state().orders.forEach(function (o) { o.lines.forEach(function (l) { if (routeTasks(o.id, l.id).length) list.push({ id: o.id + '::' + l.id, order: o, line: l }); }); }); return list; }
  function orderLineRow(index, templateList) {
    return '<div class="cat-order-line" data-line-index="' + index + '"><div class="field"><label for="line-template-' + index + '">工艺来源</label><select id="line-template-' + index + '" name="template-' + index + '" required>' + options(templateList.map(function (t) { return [t.id, t.line.product + ' · ' + (t.line.route || t.order.id)]; })) + '</select></div><div class="field"><label for="line-product-' + index + '">产品名称</label><input id="line-product-' + index + '" name="product-' + index + '" required maxlength="60" value="' + e(templateList[0] && templateList[0].line.product || '') + '"></div><div class="field"><label for="line-qty-' + index + '">需求数量（件）</label><input id="line-qty-' + index + '" name="qty-' + index + '" type="number" min="1" max="100000" step="1" required value="100"></div><button type="button" class="icon-btn cat-remove-line" aria-label="移除此产品行" title="移除此产品行">' + A.icon('trash-2') + '</button></div>';
  }
  Actions['cat-new-order'] = function () {
    var list = templates();
    if (!list.length) { UI.toast('暂无可复制的产品工艺'); return; }
    var count = 1;
    var form = modal('新建多产品生产订单', '<div class="form-grid">' + input('orderId', '生产订单号', 'O-' + (100 + state().orders.length + 1), 'text', 'required maxlength="40"') + input('customer', '客户', '', 'text', 'required maxlength="80"') + input('due', '生产承诺时间', datetime(3420), 'datetime-local', 'required') + select('priority', '优先级', [['1', '1 · 紧急'], ['2', '2 · 高'], ['3', '3 · 普通']], '3') + '</div><div class="section-heading"><h2>产品与工艺</h2><button type="button" class="btn small" id="cat-add-line">' + A.icon('plus') + '添加产品</button></div><div id="cat-order-lines">' + orderLineRow(0, list) + '</div><p class="cat-note">复制的工艺形成独立任务。同一来源的跨产品依赖在相关产品同时选入时保留；缺少来源产品时不复制该外部依赖，可在工艺路线中补建。</p>', function (f) {
      var v = values(f); var id = v.orderId.trim(); var due = minute(v.due);
      if (!id || state().orders.some(function (o) { return o.id === id; })) { showError(f, '订单号不能为空或与已有订单重复'); return; }
      if (!Number.isFinite(due) || due < state().now) { showError(f, '生产承诺不能早于当前业务时间'); return; }
      var rows = Array.from(f.querySelectorAll('.cat-order-line')); if (!rows.length) { showError(f, '订单至少保留一个产品'); return; }
      var newOrder = { id: id, customer: v.customer.trim(), due: due, priority: Number(v.priority), lines: [] }; var newTasks = []; var mapping = {}; var picked = [];
      rows.forEach(function (row) { var i = row.dataset.lineIndex; var chosen = list.find(function (x) { return x.id === v['template-' + i]; }); var line = { id: uid('L'), product: v['product-' + i].trim(), qty: Number(v['qty-' + i]), unit: '件', route: chosen.line.route || '复制工艺' }; newOrder.lines.push(line); picked.push({ chosen: chosen, line: line }); });
      if (picked.some(function (p) { return !p.line.product || !Number.isInteger(p.line.qty) || p.line.qty <= 0; })) { showError(f, '请填写产品名称与正整数数量'); return; }
      picked.forEach(function (p, pickIndex) {
        routeTasks(p.chosen.order.id, p.chosen.line.id).forEach(function (original) {
          // 只复制工艺定义，现场事实及返工/补产链永远不属于新订单模板。
          var t = { id: uid('T'), orderId: id, lineId: p.line.id, product: p.line.product, qty: p.line.qty, op: original.op, workshop: original.workshop, wc: original.wc, mode: original.mode, skill: original.skill, minLevel: original.minLevel, machines: copy(original.machines || []), rate: original.rate, setup: original.setup, run: original.run, unload: original.unload, deps: [], compatible: original.compatible || '', interruptible: !!original.interruptible, batchId: null, status: 'ready', good: 0, bad: 0, reported: 0, lock: 'none' };
          if (t.mode === 'manual' || t.mode === 'machine') t.run = Math.ceil(t.qty / t.rate * 60);
          mapping[pickIndex + '::' + original.id] = t.id; newTasks.push({ task: t, original: original, pickIndex: pickIndex });
        });
      });
      newTasks.forEach(function (entry) { entry.task.deps = (entry.original.deps || []).map(function (d) {
        var local = mapping[entry.pickIndex + '::' + d.taskId]; var candidates = newTasks.filter(function (n) { return n.original.id === d.taskId; }); var target = local || (candidates.length === 1 ? candidates[0].task.id : null); if (!target) return null;
        var mapped = Object.assign({}, d, { taskId: target }); if (mapped.type === 'quantity') { var newSource = newTasks.find(function (n) { return n.task.id === target; }); mapped.qty = Math.min(newSource.task.qty, Math.max(1, Math.ceil(Number(d.qty) * newSource.task.qty / Math.max(1, Number(newSource.original.qty))))); } return mapped;
      }).filter(Boolean); });
      state().orders.push(newOrder); state().tasks.push.apply(state().tasks, newTasks.map(function (x) { return x.task; })); A.filters.routeOrder = id; change('已建立 ' + id + '，含 ' + newOrder.lines.length + ' 个产品、' + newTasks.length + ' 道任务', 'order');
    }, true, '创建订单');
    function bindRows() { form.querySelectorAll('.cat-remove-line').forEach(function (b) { b.onclick = function () { if (form.querySelectorAll('.cat-order-line').length === 1) { showError(form, '订单至少保留一个产品'); return; } b.closest('.cat-order-line').remove(); }; }); form.querySelectorAll('[name^="template-"]').forEach(function (s) { s.onchange = function () { var t = list.find(function (x) { return x.id === s.value; }); s.closest('.cat-order-line').querySelector('[name^="product-"]').value = t.line.product; }; }); UI.icons(); }
    form.querySelector('#cat-add-line').onclick = function () { form.querySelector('#cat-order-lines').insertAdjacentHTML('beforeend', orderLineRow(count++, list)); bindRows(); }; bindRows();
  };
  Actions['cat-order-detail'] = function (button) {
    var o = order(button.dataset.id); if (!o) return;
    var list = tasksFor(o.id);
    var panel = UI.drawer(o.id + ' · ' + o.customer, '<dl class="cat-detail"><div><dt>生产承诺</dt><dd>' + e(A.fmt(o.due)) + '</dd></div><div><dt>草稿预计完成</dt><dd>' + dueText(o) + '</dd></div></dl><div class="section">' + sub('产品订单行') + A.table(['产品', '数量', '工艺版本'], o.lines.map(function (l) { return [e(l.product), e(l.qty) + ' ' + e(l.unit || '件'), e(l.route || '未命名')]; })) + '</div><div class="section">' + sub('工序任务') + A.table(['任务 / 工序', '数量 / 合格', '状态', '操作'], list.map(function (t) { return ['<b>' + e(t.op) + '</b><div class="cell-sub">' + e(t.id) + ' · ' + e(t.product) + '</div>', e(t.qty) + ' / ' + e(t.good || 0), badgeStatus(t), A.btn('cat-task-detail', t.id, '详情', 'arrow-up-right', 'small')]; })) + '</div>', A.btn('cat-open-routes', o.id, '查看工艺网络', 'git-branch', 'primary'));
    panel.classList.add('cat-detail-drawer');
  };
  Actions['cat-order-edit'] = function (button) {
    var o = order(button.dataset.id); if (!o) return; var ownTasks = tasksFor(o.id); var quantitiesEditable = !ownTasks.some(function (t) { return locked(t) || t.batchId; });
    modal('编辑订单 · ' + o.id, '<div class="form-grid">' + input('customer', '客户', o.customer, 'text', 'required maxlength="80"') + input('due', '生产承诺时间', datetime(o.due), 'datetime-local', 'required') + select('priority', '优先级', [['1', '1 · 紧急'], ['2', '2 · 高'], ['3', '3 · 普通']], o.priority) + '</div>' + sub('产品需求数量') + o.lines.map(function (l, i) { return input('lineQty-' + i, l.product + '（件）', l.qty, 'number', 'required min="1" step="1" max="100000"' + (quantitiesEditable ? '' : ' disabled')); }).join('') + (!quantitiesEditable ? '<p class="cat-note">订单已有执行记录或合批成员，产品数量暂不可修改。</p>' : ''), function (f) {
      var v = values(f); var due = minute(v.due); if (!Number.isFinite(due) || due < 0) { showError(f, '承诺时间无效'); return; }
      if (quantitiesEditable) {
        var proposed = {}; o.lines.forEach(function (l, i) { proposed[l.id] = Number(v['lineQty-' + i]); });
        if (Object.values(proposed).some(function (q) { return !Number.isInteger(q) || q < 1; })) { showError(f, '产品数量必须为正整数'); return; }
        var broken = state().tasks.find(function (t) { return (t.deps || []).some(function (d) { var source = task(d.taskId); return d.type === 'quantity' && source && source.orderId === o.id && Number(d.qty) > proposed[source.lineId]; }); });
        if (broken) { showError(f, '新数量小于 ' + broken.id + ' 的前置释放门槛，请先修改工艺依赖'); return; }
        o.lines.forEach(function (l) { l.qty = proposed[l.id]; ownTasks.filter(function (t) { return t.lineId === l.id; }).forEach(function (t) { t.qty = l.qty; if (t.mode === 'manual' || t.mode === 'machine') t.run = Math.ceil(t.qty / t.rate * 60); }); });
      }
      o.customer = v.customer.trim(); o.due = due; o.priority = Number(v.priority); change('已修改订单 ' + o.id, 'order');
    });
  };
  Actions['cat-open-routes'] = function (button) { A.filters.routeOrder = button.dataset.id; UI.closeDrawer(); A.go('routes'); };
  Actions['cat-task-detail'] = function (button) { A.task(button.dataset.id); };

  function graph(list) {
    if (!list.length) return A.empty('暂无工序任务');
    var ids = new Set(list.map(function (t) { return t.id; })); var depths = {}; var visiting = new Set();
    function depth(t) { if (depths[t.id] != null) return depths[t.id]; if (visiting.has(t.id)) return 0; visiting.add(t.id); var ds = (t.deps || []).filter(function (d) { return ids.has(d.taskId); }).map(function (d) { return depth(task(d.taskId)) + 1; }); visiting.delete(t.id); return depths[t.id] = ds.length ? Math.max.apply(Math, ds) : 0; }
    list.forEach(depth);
    // 同批成员要等全部前置就绪，用共同层级表现同步加工，再传播后置层级。
    for (var pass = 0; pass < list.length * 2; pass++) {
      var changed = false;
      (state().batches || []).forEach(function (b) { var members = b.taskIds.filter(function (id) { return ids.has(id); }); if (!members.length) return; var max = Math.max.apply(Math, members.map(function (id) { return depths[id]; })); members.forEach(function (id) { if (depths[id] < max) { depths[id] = max; changed = true; } }); });
      list.forEach(function (t) { (t.deps || []).forEach(function (d) { if (ids.has(d.taskId) && depths[t.id] <= depths[d.taskId]) { depths[t.id] = depths[d.taskId] + 1; changed = true; } }); });
      if (!changed) break;
    }
    var columns = []; list.forEach(function (t) { (columns[depths[t.id]] = columns[depths[t.id]] || []).push(t); });
    for (var c = 0; c < columns.length; c++) if (!columns[c]) columns[c] = [];
    var width = Math.max(700, columns.length * 258 + 24); var height = Math.max(215, Math.max.apply(Math, columns.map(function (c) { return c.length; })) * 138 + 55); var positions = {};
    columns.forEach(function (c, ci) { c.forEach(function (t, ri) { positions[t.id] = { x: ci * 258 + 18, y: ri * 138 + 42 }; }); });
    var links = ''; list.forEach(function (t) { (t.deps || []).forEach(function (d) {
      if (!positions[d.taskId]) return; var a = positions[d.taskId]; var b = positions[t.id]; var x1 = a.x + 224, y1 = a.y + 47, x2 = b.x, y2 = b.y + 47;
      var path = depths[t.id] - depths[d.taskId] > 1 ? 'M' + x1 + ',' + y1 + ' H' + (x1 + 12) + ' V28 H' + (x2 - 12) + ' V' + y2 + ' H' + x2 : 'M' + x1 + ',' + y1 + ' C' + (x1 + 20) + ',' + y1 + ' ' + (x2 - 20) + ',' + y2 + ' ' + x2 + ',' + y2;
      links += '<path d="' + path + '" class="' + (d.type === 'quantity' ? 'cat-quantity-path' : '') + '" marker-end="url(#cat-arrow)"><title>' + e(d.consume ? '每批 ' + d.transferQty + ' 件转序消耗' : d.type === 'quantity' ? '合格 ' + d.qty + ' 件后释放' : '前置完成后释放') + '</title></path>';
    }); });
    return '<div class="cat-graph-scroll" tabindex="0" aria-label="工艺依赖网络，可横向滚动"><div class="cat-graph" style="width:' + width + 'px;height:' + height + 'px"><svg class="cat-graph-lines" viewBox="0 0 ' + width + ' ' + height + '" aria-hidden="true"><defs><marker id="cat-arrow" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto"><path d="M0 0 L6 3 L0 6 Z" fill="#8fa5af" stroke="none"/></marker></defs>' + links + '</svg>' + columns.map(function (c, ci) { return '<span class="cat-stage" style="left:' + (ci * 258 + 18) + 'px">工序层级 ' + (ci + 1) + '</span>'; }).join('') + list.map(function (t) { var p = positions[t.id]; return '<button class="cat-node ' + (t.batchId ? 'cat-batch-node' : '') + '" style="left:' + p.x + 'px;top:' + p.y + 'px" data-action="cat-task-edit" data-id="' + e(t.id) + '"><span class="cat-node-product">' + e(t.product) + ' <b>' + e(t.qty) + ' 件</b></span><strong>' + e(t.op) + '</strong><span class="cat-node-meta">' + e(t.wc) + ' · ' + e(modeNames[t.mode]) + '</span>' + (t.batchId ? '<small>' + A.icon('layers') + e(t.batchId) + '</small>' : '<small>' + e(t.skill) + ' ≥ ' + e(t.minLevel || 1) + ' 级</small>') + '</button>'; }).join('') + '</div></div>';
  }
  Views.routes = function () {
    var selected = order(A.filters.routeOrder) || state().orders[0]; if (!selected) return head('工艺路线') + A.empty('请先建立生产订单');
    A.filters.routeOrder = selected.id; var list = tasksFor(selected.id); var edges = []; list.forEach(function (t) { (t.deps || []).forEach(function (d, i) { edges.push({ target: t, dep: d, index: i }); }); });
    return head('工艺路线', A.btn('cat-dependency-new', selected.id, '添加工序关系', 'git-branch', 'primary')) + '<div class="toolbar"><div class="toolbar-left">' + filter('routeOrder', '生产订单', state().orders.map(function (o) { return [o.id, o.id + ' · ' + o.customer]; })) + '</div><div class="cat-legend"><span><i class="cat-line-solid"></i>完成依赖</span><span><i class="cat-line-dashed"></i>数量释放</span><span>' + A.badge('共享批次', 'info') + '</span></div></div>' + graph(list) + '<div class="section">' + sub('前置与数量释放关系', '<span class="muted">' + edges.length + ' 条关系</span>') + (edges.length ? A.table(['前置工序', '后置工序', '释放条件', '等待 / 搬运', '操作'], edges.map(function (x) { var source = task(x.dep.taskId); return [source ? e(taskLabel(source)) : A.badge('丢失前置 ' + x.dep.taskId, 'danger'), e(taskLabel(x.target)), x.dep.type === 'quantity' ? A.badge('合格 ≥ ' + x.dep.qty + ' 件', 'info') : '前置全部完成', e(x.dep.lag || 0) + ' 分钟', A.btn('cat-dependency-edit', x.target.id + '::' + x.index, '编辑', 'pencil', 'small') + ' ' + A.btn('cat-dependency-remove', x.target.id + '::' + x.index, '移除', 'unlink', 'small')]; })) : A.empty('当前产品间没有前置关系')) + '</div><div class="section">' + sub('工艺参数') + A.table(['产品 / 工序', '车间 / 中心', '模式与技能', '标准时间', '批次兼容组', '操作'], list.map(function (t) { return ['<b>' + e(t.op) + '</b><div class="cell-sub">' + e(t.product) + ' · ' + e(t.id) + '</div>', e(t.workshop) + '<div class="cell-sub">' + e(t.wc) + '</div>', e(modeNames[t.mode]) + '<div class="cell-sub">' + e(t.skill) + ' ≥ ' + e(t.minLevel || 1) + ' 级</div>', t.mode === 'batch' ? e(t.run) + ' 分钟 / 批' : e(t.rate) + ' 件 / 小时', e(t.compatible || '—'), A.btn('cat-task-edit', t.id, '编辑工艺', 'pencil', 'small')]; })) + '</div>';
  };
  PageEvents.routes = function () {
    connectFilters(); var edges = []; tasksFor(A.filters.routeOrder).forEach(function (t) { (t.deps || []).forEach(function (d) { edges.push(d); }); });
    var table = document.querySelector('main .section .data-table');
    if (table) edges.forEach(function (d, i) { if (d.consume && table.tBodies[0] && table.tBodies[0].rows[i]) table.tBodies[0].rows[i].cells[2].innerHTML = A.badge('逐批 ' + d.transferQty + ' 件', 'info') + '<div class="cell-sub">消耗比例 ' + e(d.ratio) + '，产出逐批放行</div>'; });
  };
  function dependencyForm(targetId, index) {
    var target = task(targetId); var original = target && index != null ? target.deps[index] : null;
    if (target && locked(target)) { UI.toast('已执行任务的依赖不可修改'); return; }
    var defaultOrder = A.filters.routeOrder; var candidates = state().tasks.filter(function (t) { return !locked(t); });
    var form = modal(original ? '编辑工序关系' : '添加工序关系', select('target', '后置工序', candidates.map(function (t) { return [t.id, taskLabel(t)]; }), targetId || (candidates.find(function (t) { return t.orderId === defaultOrder; }) || {}).id, 'required' + (original ? ' disabled' : '')) + select('source', '前置工序（允许跨产品 / 跨订单）', state().tasks.map(function (t) { return [t.id, taskLabel(t)]; }), original && original.taskId, 'required') + '<div class="form-grid">' + select('relation', '释放规则', [['finish', '前置全部完成'], ['quantity', '指定合格数量']], original ? original.type : 'finish') + input('threshold', '释放门槛（件）', original && original.type === 'quantity' ? original.qty : 50, 'number', 'min="1" step="1"') + input('lag', '等待 / 搬运（自然分钟）', original ? original.lag || 0 : 0, 'number', 'min="0" max="14400" step="1" required') + '</div><p class="cat-note">数量释放仅作为开工门槛，不消耗前置产品。前置累计合格达到门槛后，才允许后置开工。</p>', function (f) {
      var v = values(f); var dest = task(original ? targetId : v.target); var source = task(v.source); var qty = Number(v.relation === 'consume' ? f.elements.transferQty.value : v.threshold); var lag = Number(v.lag);
      if (!dest || !source || dest.id === source.id) { showError(f, '前置与后置必须是两道不同工序'); return; }
      if (locked(dest)) { showError(f, '后置工序已执行，不能修改依赖'); return; }
      if (v.relation !== 'finish' && (!Number.isInteger(qty) || qty < 1 || qty > source.qty)) { showError(f, '数量门槛必须为 1 至前置计划量 ' + source.qty + ' 的整数'); return; }
      if (v.relation !== 'finish' && (source.mode === 'batch' || source.mode === 'auto')) { showError(f, '固定周期或自动工序整批完成后才释放，请选择前置全部完成'); return; }
      if (v.relation === 'consume') {
        var ratio = Number(f.elements.ratio.value), transferQty = Number(f.elements.transferQty.value);
        if (!['manual', 'machine'].includes(dest.mode) || dest.setup || dest.unload || dest.batchId) { showError(f, '逐批转序要求后置为按件人工 / 人机工序，准备和卸料为 0，且不在共享批次中'); return; }
        if (!Number.isFinite(ratio) || ratio <= 0 || !Number.isInteger(transferQty) || transferQty <= 0 || transferQty > source.qty || dest.qty * ratio % 1 || transferQty / ratio % 1) { showError(f, '用量比例和转移批量必须能换算为整件，转移量不能超过前置数量'); return; }
        if ((dest.deps || []).some(function (d, i) { return d.consume && !(original && i === index); })) { showError(f, '当前后置工序已有一个消耗来源，暂不支持多来源同时消耗'); return; }
        var reserved = state().tasks.reduce(function (n, t) { return n + (t.deps || []).reduce(function (sum, d, i) { return sum + (d.consume && d.taskId === source.id && !(original && t.id === targetId && i === index) ? t.qty * Number(d.ratio || 1) : 0); }, 0); }, 0);
        if (reserved + dest.qty * ratio > source.qty) { showError(f, '前置供给超分配：已预留 ' + reserved + '，本次需用 ' + dest.qty * ratio + '，前置可产 ' + source.qty); return; }
      }
      var nextDeps = copy(dest.deps || []); if (original) nextDeps.splice(index, 1);
      if (nextDeps.some(function (d) { return d.taskId === source.id; })) { showError(f, '相同前置已经存在，不可重复添加'); return; }
      function reaches(id, sought, visited) { if (id === sought) return true; if (visited.has(id)) return false; visited.add(id); var t = task(id); return t && (t.deps || []).some(function (d) { return reaches(d.taskId, sought, visited); }); }
      if (reaches(source.id, dest.id, new Set())) { showError(f, '该关系会形成工艺循环，无法保存'); return; }
      if (source.batchId && source.batchId === dest.batchId) { showError(f, '同一共享批次内成员不能互设前置，请先拆批'); return; }
      var relation = { taskId: source.id, type: v.relation === 'finish' ? 'finish' : 'quantity', qty: v.relation === 'finish' ? source.qty : qty, lag: lag };
      if (v.relation === 'consume') { relation.consume = true; relation.ratio = Number(f.elements.ratio.value); relation.transferQty = Number(f.elements.transferQty.value); relation.qty = relation.transferQty; }
      nextDeps.push(relation); dest.deps = nextDeps; change('已更新 ' + dest.id + ' 的工序关系', 'routing', dest.id);
    }, true);
    form.elements.relation.querySelector('option[value=quantity]').textContent = '数量门槛（不消耗）';
    form.elements.relation.insertAdjacentHTML('beforeend', '<option value="consume">逐批转序（消耗）</option>');
    if (original && original.consume) form.elements.relation.value = 'consume';
    form.querySelector('.form-grid').insertAdjacentHTML('beforeend', input('ratio', '每件后置消耗前置数量', original && original.ratio || 1, 'number', 'min="0.01" max="1000" step="0.01"') + input('transferQty', '每次转移批量（前置件）', original && original.transferQty || 20, 'number', 'min="1" step="1"'));
    form.querySelector('.cat-note').textContent = '数量门槛只决定开工时机；逐批转序会预留并消耗前置产出，各段必须等待对应批次合格放行。';
    function updateThreshold() { var m = form.elements.relation.value; form.elements.threshold.disabled = m !== 'quantity'; form.elements.threshold.required = m === 'quantity'; form.elements.ratio.disabled = m !== 'consume'; form.elements.transferQty.disabled = m !== 'consume'; form.elements.ratio.required = m === 'consume'; form.elements.transferQty.required = m === 'consume'; }
    form.elements.relation.onchange = updateThreshold; updateThreshold();
  }
  Actions['cat-dependency-new'] = function (button) { A.filters.routeOrder = button.dataset.id; dependencyForm(null, null); };
  Actions['cat-dependency-edit'] = function (button) { var parts = button.dataset.id.split('::'); dependencyForm(parts[0], Number(parts[1])); };
  Actions['cat-dependency-remove'] = function (button) { var parts = button.dataset.id.split('::'); var t = task(parts[0]); var index = Number(parts[1]); if (!t || !t.deps[index]) return; if (locked(t)) { UI.toast('已有执行记录，不能移除关系'); return; } modal('移除工序关系', '<p>移除 ' + e(taskLabel(task(t.deps[index].taskId) || { id: t.deps[index].taskId, product: '', op: '' })) + ' 对 ' + e(taskLabel(t)) + ' 的约束。</p>' + input('reason', '移除原因', '', 'text', 'required minlength="2" maxlength="120"'), function (f) { t.deps.splice(index, 1); change('移除 ' + t.id + ' 前置关系：' + values(f).reason.trim(), 'routing', t.id); }, false, '确认移除'); };
  Actions['cat-task-edit'] = function (button) {
    var t = task(button.dataset.id); if (!t) return;
    if (locked(t)) { A.task(t.id); return; }
    if (t.batchId) { UI.toast('工序属于共享批次，修改工艺前请先拆批'); return; }
    var form = modal('工艺参数 · ' + t.id, '<p class="cat-note">' + e(t.product) + ' · 需求 ' + e(t.qty) + ' 件 · ' + e(t.orderId) + '</p><div class="form-grid">' + input('op', '工序名称', t.op, 'text', 'required maxlength="60"') + select('mode', '加工模式', Object.keys(modeNames).map(function (k) { return [k, modeNames[k]]; }), t.mode) + input('workshop', '执行车间', t.workshop, 'text', 'required maxlength="40"') + input('wc', '工作中心', t.wc, 'text', 'required maxlength="40"') + input('skill', '所需技能', t.skill, 'text', 'required maxlength="40"') + input('minLevel', '最低技能等级', t.minLevel || 1, 'number', 'min="1" max="5" step="1" required') + input('rate', '组合小时产能（件 / 小时）', t.rate || 1, 'number', 'min="0.1" max="100000" step="0.1" required') + input('run', '固定周期运行（分钟）', t.run || 60, 'number', 'min="1" max="14400" step="1" required') + input('setup', '准备 / 装载（分钟）', t.setup || 0, 'number', 'min="0" max="1440" step="1" required') + input('unload', '卸料（分钟）', t.unload || 0, 'number', 'min="0" max="1440" step="1" required') + input('compatible', '批次兼容组 / 配方', t.compatible || '', 'text', 'maxlength="80"') + '</div><fieldset class="cat-checkbox-field"><legend>候选设备</legend>' + state().resources.filter(function (r) { return r.type === 'machine'; }).map(function (r) { return '<label><input type="checkbox" name="machines" value="' + e(r.id) + '"' + (t.machines.includes(r.id) ? ' checked' : '') + '> ' + e(r.name) + ' <small>' + e(r.wc) + '</small></label>'; }).join('') + '</fieldset><label class="cat-check"><input type="checkbox" name="interruptible"' + (t.interruptible ? ' checked' : '') + '>允许跨工作窗口分段</label>', function (f) {
      var v = values(f); v.rate = f.elements.rate.value; v.run = f.elements.run.value; var machines = new FormData(f).getAll('machines');
      if (v.mode !== 'manual' && !machines.length) { showError(f, '设备工序至少选择一台候选设备'); return; }
      var wc = v.wc.trim(), skill = v.skill.trim(), level = Number(v.minLevel);
      if (!state().resources.some(function (r) { return r.type === 'person' && r.workshop === v.workshop.trim() && r.wc === wc && Number(r.skills[skill] || 0) >= level; })) { showError(f, '该车间 / 工作中心没有满足技能等级的人员，请先维护人员或修改工艺要求'); return; }
      if (v.mode === 'batch' && !v.compatible.trim()) { showError(f, '批处理工序必须填写兼容组 / 配方'); return; }
      Object.assign(t, { op: v.op.trim(), mode: v.mode, workshop: v.workshop.trim(), wc: wc, skill: skill, minLevel: level, rate: Number(v.rate), run: ['manual', 'machine'].includes(v.mode) ? Math.ceil(t.qty / Number(v.rate) * 60) : Number(v.run), setup: Number(v.setup), unload: Number(v.unload), compatible: v.compatible.trim(), machines: v.mode === 'manual' ? [] : machines, interruptible: new FormData(f).has('interruptible') }); change('已更新工艺 ' + t.id, 'routing', t.id);
    }, true);
    function updateFields() { var m = form.elements.mode.value; form.elements.run.disabled = m !== 'batch' && m !== 'auto'; form.elements.rate.disabled = m === 'batch'; form.querySelectorAll('input[name=machines]').forEach(function (x) { x.disabled = m === 'manual'; }); }
    // 保留未使用的工时参数，切换模式时仍可复用。
    form.elements.mode.addEventListener('change', function () { updateFields(); }); updateFields();
  };

  function calendarMini(r) {
    return '<div class="cat-calendar-mini">' + [0, 1, 2, 3, 4].map(function (day) { var start = day * 1440; var windows = (r.available || []).filter(function (x) { return x[1] > start && x[0] < start + 720; }); var blocked = (r.unavailable || []).filter(function (x) { return x[1] > start && x[0] < start + 720; }); return '<div><span>' + (14 + day) + '</span><b title="' + e(dateLabel(day)) + '">' + windows.map(function (w) { return '<i style="left:' + Math.max(0, (w[0] - start) / 7.2) + '%;width:' + Math.min(100, (Math.min(start + 720, w[1]) - Math.max(start, w[0])) / 7.2) + '%"></i>'; }).join('') + blocked.map(function (w) { return '<i class="cat-calendar-blocked" style="left:' + Math.max(0, (w[0] - start) / 7.2) + '%;width:' + Math.min(100, (Math.min(start + 720, w[1]) - Math.max(start, w[0])) / 7.2) + '%"></i>'; }).join('') + '</b></div>'; }).join('') + '</div>';
  }
  function capacityMinutes(r) { var sum = 0; (r.available || []).forEach(function (w) { var intervals = [w.slice()]; (r.unavailable || []).forEach(function (u) { var next = []; intervals.forEach(function (x) { if (u[1] <= x[0] || u[0] >= x[1]) next.push(x); else { if (u[0] > x[0]) next.push([x[0], u[0]]); if (u[1] < x[1]) next.push([u[1], x[1]]); } }); intervals = next; }); sum += intervals.reduce(function (n, x) { return n + x[1] - x[0]; }, 0); }); return sum; }
  function calendarHistoryConflict(r, available, unavailable) {
    var plans = state().published && state().published.assignments || [];
    function usable(windows, exceptions, at) { return windows.some(function (w) { return at >= w[0] && at + 1 <= w[1]; }) && !exceptions.some(function (w) { return at < w[1] && w[0] < at + 1; }); }
    for (var i = 0; i < state().tasks.length; i++) {
      var t = state().tasks[i]; if (!(t.activity || []).length) continue;
      var assignment = plans.find(function (a) { return a.taskId === t.id; });
      if (!assignment || ![assignment.personId, assignment.machineId].includes(r.id) && !(assignment.segments || []).some(function (g) { return g.personId === r.id || g.machineId === r.id; })) continue;
      for (var j = 0; j < t.activity.length; j++) {
        var activity = t.activity[j], end = activity.end == null ? state().now : activity.end;
        for (var at = Math.floor(activity.start); at < end; at++) {
          if (usable(r.available || [], r.unavailable || [], at) !== usable(available, unavailable, at)) return { taskId: t.id, at: at };
        }
      }
    }
    return null;
  }
  function resourceView(type) {
    var all = state().resources.filter(function (r) { return r.type === type; }); var wsKey = type === 'person' ? 'peopleWorkshop' : 'machineWorkshop'; var shown = all.filter(function (r) { return !A.filters[wsKey] || r.workshop === A.filters[wsKey]; });
    return head(type === 'person' ? '人员与技能日历' : '设备与工作中心', A.btn(type === 'person' ? 'cat-person-new' : 'cat-resource-new', '', type === 'person' ? '新增人员' : '新增设备', 'plus', 'primary')) + summary([[type === 'person' ? '具体人员' : '生产设备', all.length], ['工作中心', new Set(all.map(function (r) { return r.wc; })).size], ['本周净可用' + (type === 'person' ? '人时' : '机时'), A.hours(all.reduce(function (n, r) { return n + capacityMinutes(r); }, 0))], ['日历例外', all.reduce(function (n, r) { return n + (r.unavailable || []).length; }, 0)]]) + '<div class="toolbar"><div class="toolbar-left">' + filter(wsKey, '车间', [['', '全部车间']].concat(Array.from(new Set(all.map(function (r) { return r.workshop; }))).map(function (w) { return [w, w]; }))) + '</div><span class="muted">2026/09/14 – 09/18</span></div>' + (shown.length ? A.table([type === 'person' ? '人员' : '设备', '车间 / 工作中心', type === 'person' ? '技能与等级' : '能力 / 适用工艺', '本周可用窗口', type === 'person' ? '净可用人时' : '净可用机时', '操作'], shown.map(function (r) { return ['<strong>' + e(r.name) + '</strong><div class="cell-sub">' + e(r.id) + '</div>', e(r.workshop) + '<div class="cell-sub">' + e(r.wc) + '</div>', (type === 'machine' ? '<div>批容量 ' + e(r.capacity || 1) + ' ' + e(r.unit || '件') + '</div>' : '') + '<div class="cat-skill-tags">' + Object.keys(r.skills || {}).map(function (k) { return A.badge(k + ' · ' + r.skills[k] + ' 级', 'info'); }).join('') + '</div>', calendarMini(r), e(A.hours(capacityMinutes(r))), '<div class="actions">' + A.btn('cat-resource-edit', r.id, '编辑', 'pencil', 'small') + A.btn('cat-calendar-edit', r.id, '日历', 'calendar-days', 'small') + '</div>']; })) : A.empty('该车间暂无资源'));
  }
  Views.resources = function () { return resourceView('machine'); };
  Views.people = function () { return resourceView('person'); };
  PageEvents.resources = connectFilters; PageEvents.people = connectFilters;
  function editResource(r, type) {
    var isNew = !r; type = r ? r.type : type;
    r = r || { id: uid(type === 'person' ? 'P' : 'M'), name: '', type: type, workshop: state().resources[0] && state().resources[0].workshop || '', wc: state().resources[0] && state().resources[0].wc || '', skills: {}, capacity: 1, unit: '件', available: [0, 1, 2, 3, 4].flatMap(function (d) { return [[d * 1440, d * 1440 + 240], [d * 1440 + 300, d * 1440 + 540]]; }), unavailable: [] };
    modal((isNew ? '新增' : '编辑') + (type === 'person' ? '人员' : '设备'), '<div class="form-grid">' + input('resourceId', '资源编号', r.id, 'text', 'required maxlength="50"' + (!isNew ? ' disabled' : '')) + input('name', type === 'person' ? '姓名' : '设备名称', r.name, 'text', 'required maxlength="60"') + input('workshop', '所属车间', r.workshop, 'text', 'required maxlength="40"') + input('wc', '可执行工作中心', r.wc, 'text', 'required maxlength="40"') + (type === 'machine' ? input('capacity', '批容量（件）', r.capacity, 'number', 'required min="1" max="100000" step="1"') : '') + '</div><div class="field"><label for="cat-skills">技能 / 能力与等级</label><textarea id="cat-skills" name="skills" required placeholder="加工:3&#10;检验:2">' + e(Object.keys(r.skills).map(function (k) { return k + ':' + r.skills[k]; }).join('\n')) + '</textarea><small>每行一项，格式：技能名称:等级，等级 1–5。</small></div>', function (f) {
      var v = values(f); var skills = {}; var lines = v.skills.trim() ? v.skills.trim().split(/\n/) : []; var bad = false;
      lines.forEach(function (line) { var match = line.trim().match(/^([^:：]+)[:：]([1-5])$/); if (!match || skills[match[1].trim()]) bad = true; else skills[match[1].trim()] = Number(match[2]); });
      if (bad || type === 'person' && !lines.length) { showError(f, '技能格式有误或名称重复，示例：加工:3；等级为 1–5'); return; }
      var id = isNew ? v.resourceId.trim() : r.id;
      if (!id || isNew && resource(id)) { showError(f, '资源编号为空或重复'); return; }
      Object.assign(r, { id: id, name: v.name.trim(), workshop: v.workshop.trim(), wc: v.wc.trim(), skills: skills, capacity: type === 'machine' ? Number(v.capacity) : 1 }); if (isNew) state().resources.push(r); change((isNew ? '新增资源 ' : '更新资源 ') + r.name, 'resource');
    });
    if (type === 'machine') document.getElementById('cat-skills').required = false;
  }
  Actions['cat-person-new'] = function () { editResource(null, 'person'); };
  Actions['cat-resource-new'] = function () { editResource(null, 'machine'); };
  Actions['cat-resource-edit'] = function (button) { var r = resource(button.dataset.id); if (r) editResource(r); };
  function timeOnly(minutes) { return datetime(minutes).slice(11); }
  function calendarSlot(day, slot, range) { return '<div class="cat-calendar-slot"><label for="cal-' + day + '-' + slot + '-start">' + (slot === 0 ? '上午' : '下午') + '开始</label><input id="cal-' + day + '-' + slot + '-start" name="cal-' + day + '-' + slot + '-start" type="time" value="' + (range ? timeOnly(range[0]) : '') + '"><label for="cal-' + day + '-' + slot + '-end">结束</label><input id="cal-' + day + '-' + slot + '-end" name="cal-' + day + '-' + slot + '-end" type="time" value="' + (range ? timeOnly(range[1]) : '') + '"></div>'; }
  function exceptionRow(i, range) { return '<div class="cat-exception" data-exception="' + i + '">' + input('off-' + i + '-start', '不可用开始', range ? datetime(range[0]) : datetime(120), 'datetime-local', 'required') + input('off-' + i + '-end', '不可用结束', range ? datetime(range[1]) : datetime(240), 'datetime-local', 'required') + '<button type="button" class="icon-btn cat-remove-exception" aria-label="移除不可用时段" title="移除不可用时段">' + A.icon('trash-2') + '</button></div>'; }
  Actions['cat-calendar-edit'] = function (button) {
    var r = resource(button.dataset.id); if (!r) return; var count = (r.unavailable || []).length;
    var extra = (r.available || []).filter(function (w) { return w[0] < 0 || w[0] >= 7200; });
    var form = modal('工作日历 · ' + r.name, '<div class="cat-calendar-editor">' + [0, 1, 2, 3, 4].map(function (day) { var windows = (r.available || []).filter(function (w) { return w[0] >= day * 1440 && w[0] < (day + 1) * 1440; }); return '<fieldset><legend>' + dateLabel(day) + '</legend>' + calendarSlot(day, 0, windows[0]) + calendarSlot(day, 1, windows[1]) + '</fieldset>'; }).join('') + '</div><div class="section-heading"><h2>' + (r.type === 'person' ? '请假 / 培训' : '维修 / 停机') + '</h2><button class="btn small" id="cat-add-exception" type="button">' + A.icon('plus') + '添加时段</button></div><div id="cat-exceptions">' + (r.unavailable || []).map(function (w, i) { return exceptionRow(i, w); }).join('') + '</div>' + input('reason', '日历变更说明', '', 'text', 'required minlength="2" maxlength="160"') + '<p class="cat-note">开始与结束都清空表示该半天不出勤。不可用时段从工作窗口中扣减，保存后需要重新评估草稿。</p>', function (f) {
      var v = values(f); var available = extra.slice(); var invalid = '';
      [0, 1, 2, 3, 4].forEach(function (day) { [0, 1].forEach(function (slot) { var start = v['cal-' + day + '-' + slot + '-start']; var end = v['cal-' + day + '-' + slot + '-end']; if (!start && !end) return; if (!start || !end || end <= start) { invalid = dateLabel(day) + ' 的工作窗口无效，开始必须早于结束'; return; } var s = start.split(':').map(Number), z = end.split(':').map(Number); available.push([day * 1440 + s[0] * 60 + s[1] - 480, day * 1440 + z[0] * 60 + z[1] - 480]); }); });
      available.sort(function (a, b) { return a[0] - b[0]; }); for (var i = 1; i < available.length; i++) if (available[i][0] < available[i - 1][1]) invalid = '工作时段不可重叠';
      var unavailable = Array.from(f.querySelectorAll('[data-exception]')).map(function (row) { var i = row.dataset.exception; return [minute(v['off-' + i + '-start']), minute(v['off-' + i + '-end'])]; });
      if (unavailable.some(function (w) { return !Number.isFinite(w[0]) || !Number.isFinite(w[1]) || w[1] <= w[0]; })) invalid = '不可用时段的开始必须早于结束';
      if (invalid) { showError(f, invalid); return; }
      unavailable.sort(function (a, b) { return a[0] - b[0]; }); var merged = []; unavailable.forEach(function (w) { var last = merged[merged.length - 1]; if (last && w[0] <= last[1]) last[1] = Math.max(last[1], w[1]); else merged.push(w); });
      var history = calendarHistoryConflict(r, available, merged);
      if (history) { showError(f, '不能改写已有生产活动的历史日历：' + history.taskId + '，' + A.fmt(history.at) + '。请将变更限定在尚无活动记录的未来时段。'); return; }
      r.available = available; r.unavailable = merged; change('日历变更 ' + r.name + '：' + v.reason.trim(), 'calendar');
    }, true);
    function bind() { form.querySelectorAll('.cat-remove-exception').forEach(function (b) { b.onclick = function () { b.closest('.cat-exception').remove(); }; }); UI.icons(); }
    form.querySelector('#cat-add-exception').onclick = function () { form.querySelector('#cat-exceptions').insertAdjacentHTML('beforeend', exceptionRow(count++)); bind(); }; bind();
  };

  function batchIssue(members, machine) {
    if (members.length < 2) return '至少选择两个工序任务组成共享批次';
    var first = members[0];
    if (members.some(function (t) { return t.mode !== 'batch' || !t.compatible || t.compatible !== first.compatible; })) return '所有成员必须是相同兼容组的固定周期批处理';
    if (members.some(function (t) { return locked(t) || t.batchId; })) return '成员已开工、已报工或已加入其他批次';
    if (members.some(function (t) { return t.wc !== first.wc || t.skill !== first.skill || Number(t.run) !== Number(first.run) || Number(t.setup) !== Number(first.setup) || Number(t.unload) !== Number(first.unload); })) return '成员工作中心、装卸技能与准备 / 周期 / 卸料时长必须一致';
    if (!machine || machine.type !== 'machine' || members.some(function (t) { return !t.machines.includes(machine.id); })) return '请选择所有成员共同允许使用的设备';
    if (members.reduce(function (n, t) { return n + Number(t.qty); }, 0) > Number(machine.capacity)) return '成员总量超过设备批容量 ' + machine.capacity + ' 件';
    var ids = new Set(members.map(function (t) { return t.id; }));
    function dependsOnMember(id, seen) { if (seen.has(id)) return false; seen.add(id); var t = task(id); return t && (t.deps || []).some(function (d) { return ids.has(d.taskId) || dependsOnMember(d.taskId, seen); }); }
    if (members.some(function (t) { return dependsOnMember(t.id, new Set()); })) return '合批成员之间存在直接或间接前置依赖，不能同批加工';
    return '';
  }
  Views.batches = function () {
    var batches = state().batches || []; var candidates = state().tasks.filter(function (t) { return t.mode === 'batch' && !t.batchId && !locked(t); });
    return head('共享加工批次', A.btn('cat-batch-new', '', '创建共享批次', 'layers', 'primary')) + summary([['共享批次', batches.length], ['已合批工序', batches.reduce(function (n, b) { return n + b.taskIds.length; }, 0)], ['待组合任务', candidates.length], ['兼容组', new Set(state().tasks.filter(function (t) { return t.mode === 'batch'; }).map(function (t) { return t.compatible; })).size]]) + (batches.length ? A.table(['批次 / 兼容组', '产品与成员', '设备', '数量 / 容量', '固定周期', '状态', '操作'], batches.map(function (b) { var members = b.taskIds.map(task).filter(Boolean); var machine = resource(b.machineId); var q = members.reduce(function (n, t) { return n + t.qty; }, 0); return ['<b>' + e(b.id) + '</b><div class="cell-sub">' + e(b.compatibility) + '</div>', members.map(function (t) { return '<div>' + e(t.product) + ' · ' + e(t.qty) + ' 件 <span class="muted">' + e(t.id) + '</span></div>'; }).join(''), e(machine ? machine.name : b.machineId), e(q) + ' / ' + e(machine ? machine.capacity : b.capacity) + ' 件', e(b.setup) + ' + ' + e(b.run) + ' + ' + e(b.unload) + ' 分钟', members.some(locked) ? A.badge('已有执行', 'info') : A.badge('未开工', 'neutral'), '<div class="actions">' + A.btn('cat-batch-detail', b.id, '详情', 'arrow-up-right', 'small') + A.btn('cat-batch-split', b.id, '拆批', 'ungroup', 'small') + '</div>']; })) : A.empty('尚未建立共享批次')) + '<div class="section">' + sub('待组合工序') + (candidates.length ? A.table(['产品 / 工序', '数量', '中心 / 兼容组', '准备 + 运行 + 卸料', '候选设备'], candidates.map(function (t) { return [e(t.product) + ' / ' + e(t.op), e(t.qty) + ' 件', e(t.wc) + '<div class="cell-sub">' + e(t.compatible) + '</div>', e(t.setup) + ' + ' + e(t.run) + ' + ' + e(t.unload) + ' 分钟', (t.machines || []).map(function (id) { var r = resource(id); return e(r ? r.name : id); }).join('、')]; })) : A.empty('没有可加入共享批次的工序')) + '</div>';
  };
  Actions['cat-batch-new'] = function () {
    var candidates = state().tasks.filter(function (t) { return t.mode === 'batch' && !t.batchId && !locked(t); }); var machines = state().resources.filter(function (r) { return r.type === 'machine'; });
    if (candidates.length < 2) { UI.toast('至少需要两道未合批、未开工的批处理工序'); return; }
    var form = modal('创建共享加工批次', '<div class="form-grid">' + input('batchId', '批次编号', uid('H'), 'text', 'required maxlength="50"') + select('machineId', '共享设备', machines.map(function (r) { return [r.id, r.name + ' · 容量 ' + r.capacity + ' 件']; }), machines[0] && machines[0].id, 'required') + '</div><fieldset class="cat-batch-pick"><legend>批次成员</legend>' + candidates.map(function (t) { return '<label><input type="checkbox" name="members" value="' + e(t.id) + '"><span><b>' + e(t.product) + ' · ' + e(t.op) + '</b><small>' + e(t.id) + ' · ' + e(t.compatible) + ' · ' + e(t.wc) + '</small></span><strong>' + e(t.qty) + ' 件</strong></label>'; }).join('') + '</fieldset><div id="cat-batch-check" class="cat-live-check" role="status"></div>', function (f) {
      var v = values(f); var selected = new FormData(f).getAll('members').map(task).filter(Boolean); var machine = resource(v.machineId); var problem = batchIssue(selected, machine);
      if (problem) { showError(f, problem); return; } var id = v.batchId.trim(); if (!id || state().batches.some(function (b) { return b.id === id; })) { showError(f, '批次编号为空或重复'); return; }
      var first = selected[0]; var b = { id: id, taskIds: selected.map(function (t) { return t.id; }), machineId: machine.id, capacity: Number(machine.capacity), compatibility: first.compatible, run: Number(first.run), setup: Number(first.setup), unload: Number(first.unload) }; state().batches.push(b); selected.forEach(function (t) { t.batchId = id; }); change('已建立共享批次 ' + id + '，' + selected.reduce(function (n, t) { return n + t.qty; }, 0) + ' 件', 'batch');
    }, true, '创建批次');
    function check() { var selected = new FormData(form).getAll('members').map(task).filter(Boolean); var machine = resource(form.elements.machineId.value); var issue = batchIssue(selected, machine); var el = form.querySelector('#cat-batch-check'); el.classList.toggle('cat-check-pass', !issue); el.textContent = '已选 ' + selected.length + ' 道 / ' + selected.reduce(function (n, t) { return n + t.qty; }, 0) + ' 件。' + (issue || '兼容与容量校验通过；排程时再校验共同资源时窗。'); }
    form.addEventListener('change', check); check();
  };
  Actions['cat-batch-detail'] = function (button) { var b = state().batches.find(function (x) { return x.id === button.dataset.id; }); if (!b) return; var machine = resource(b.machineId); var members = b.taskIds.map(task).filter(Boolean); var ass = allocations().find(function (x) { return x.batchId === b.id; }); UI.drawer('共享批次 · ' + b.id, '<dl class="cat-detail"><div><dt>兼容组</dt><dd>' + e(b.compatibility) + '</dd></div><div><dt>设备</dt><dd>' + e(machine ? machine.name : b.machineId) + '</dd></div><div><dt>成员数量</dt><dd>' + members.reduce(function (n, t) { return n + t.qty; }, 0) + ' / ' + e(b.capacity) + ' 件</dd></div><div><dt>加工周期</dt><dd>准备 ' + e(b.setup) + ' + 运行 ' + e(b.run) + ' + 卸料 ' + e(b.unload) + ' 分钟</dd></div><div><dt>草稿时间</dt><dd>' + (ass ? e(A.fmt(ass.start)) + ' 至 ' + e(A.fmt(ass.end)) : '未排程') + '</dd></div></dl><div class="section">' + A.table(['成员产品', '工序 / 数量', '合格', '状态'], members.map(function (t) { return [e(t.product), e(t.op) + ' / ' + e(t.qty), e(t.good || 0), badgeStatus(t)]; })) + '</div>', A.btn('cat-batch-split', b.id, '拆分共享批次', 'ungroup')); };
  Actions['cat-batch-split'] = function (button) { var b = state().batches.find(function (x) { return x.id === button.dataset.id; }); if (!b) return; var members = b.taskIds.map(task).filter(Boolean); if (members.some(locked)) { UI.toast('批次已有执行记录，不能拆分'); return; } modal('拆分共享批次 · ' + b.id, '<p>将 ' + members.length + ' 道工序恢复为独立批处理任务，保留各产品数量与工艺。原正式计划仍保留，草稿需重新排程。</p>' + input('reason', '拆批原因', '', 'text', 'required minlength="2" maxlength="160"'), function (f) { members.forEach(function (t) { t.batchId = null; }); state().batches = state().batches.filter(function (x) { return x.id !== b.id; }); change('拆分批次 ' + b.id + '：' + values(f).reason.trim(), 'batch'); }, false, '确认拆批'); };
}());
