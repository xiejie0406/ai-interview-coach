(function () {
  'use strict';
  const F = window.F, IO = window.FIO;
  if (!F || !IO) throw new Error('商品模块依赖 core.js 和 file-io.js');
  const e = F.esc, $ = id => document.getElementById(id);
  const ui = { search: '', category: '', status: '', sort: 'name', page: 1, importKind: 'product', sheets: [], sheet: 0, mapping: {}, batch: null, fileName: '', source: '', warehouse: '', categoryScope: '', priceTable: '批发价', includeDrafts: true, asOf: '', mediaFilter: 'all', mediaSearch: '', mediaBusy: false, mediaProgress: '', mediaMap: [] };
  const canEdit = () => ['operator', 'manager', 'admin'].includes(F.state.role);
  const guard = () => F.requireRole(['operator', 'manager', 'admin']);
  const invoke = fn => async ev => { try { await fn(ev); } catch (err) { F.notify(err.message || '操作未完成，请重试', 'error'); } };
  const options = (values, selected, first) => `${first != null ? `<option value="">${e(first)}</option>` : ''}${values.map(v => `<option value="${e(v)}"${v === selected ? ' selected' : ''}>${e(v)}</option>`).join('')}`;
  const date = t => t && Number.isFinite(Date.parse(t)) ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : '尚未核实';
  const localTime = t => { const d = t ? new Date(t) : new Date(); return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 23); };
  const label = kind => ({ product: '商品资料', price: '价格全量', stock: '库存全量' }[kind]);
  const statusName = s => ({ active: '在售', inactive: '已下架', draft: '资料草稿' }[s] || s);
  const batchName = s => ({ ready: '待确认', invalid: '校验失败', applied: '已生效', cancelled: '已取消' }[s] || s);
  const basePrice = (p, s) => Object.prototype.hasOwnProperty.call(s, 'price') ? s.price : p.price;
  function priceRange(p) { const v = p.sizes.map(s => basePrice(p, s)).filter(n => Number.isInteger(n) && n > 0); return !v.length ? '待补价格' : Math.min(...v) === Math.max(...v) ? F.money(v[0]) : `${F.money(Math.min(...v))} 起`; }
  function issues(p) { const out = []; if (!p.image || !p.imageAllowed) out.push('待补可用主图'); if (p.sizes.some(s => !Number.isInteger(basePrice(p, s)) || basePrice(p, s) <= 0)) out.push('待补价格'); if (p.sizes.some(s => !Number.isInteger(s.stock) || s.stock < 0)) out.push('库存未知'); if (!p.confirmed) out.push('属性待确认'); return out; }
  function heading(title, desc, actions = '') { return `<div class="page-heading"><div><p class="eyebrow">商品与资料</p><h1>${title}</h1><p>${desc}</p></div><div class="cat-actions">${actions}</div></div>`; }
  function table(head, rows) { return `<div class="table-wrap" tabindex="0" aria-label="明细表，可横向滚动"><table class="cat-table"><thead><tr>${head.map(h => `<th>${h}</th>`).join('')}</tr></thead><tbody>${rows.map(r => `<tr>${r.map(c => `<td>${c}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`; }
  function empty(title, text, action = '') { return `<div class="cat-empty">${F.icon('package')}<h3>${title}</h3><p>${text}</p>${action}</div>`; }
  function downloadCSV(rows, name) { F.download(new Blob([IO.csv(rows)], { type: 'text/csv;charset=utf-8' }), name); }
  function renderCatalog() {
    const all = F.state.products, q = ui.search.toLowerCase();
    let products = all.filter(p => (!q || [p.name, p.sku, p.style, p.color, ...p.sizes.map(s => s.sku), ...(p.tags || [])].join(' ').toLowerCase().includes(q)) && (!ui.category || p.category === ui.category) && (!ui.status || (ui.status === 'incomplete' ? issues(p).length : p.status === ui.status)));
    products.sort((a, b) => ui.sort === 'price' ? (Math.min(...a.sizes.map(s => s.price ?? a.price ?? Infinity)) - Math.min(...b.sizes.map(s => s.price ?? b.price ?? Infinity))) : ui.sort === 'stock' ? F.stock(b) - F.stock(a) : a.name.localeCompare(b.name, 'zh-CN'));
    const pages = Math.max(1, Math.ceil(products.length / 12)); ui.page = Math.min(ui.page, pages);
    F.main.innerHTML = heading('商品中心', '把款式、尺码和可供数据放在一起，选品更有依据。', `${canEdit() ? '<button class="btn secondary" id="cat-new">新建商品</button>' : ''}<button class="btn primary" id="cat-import">${F.icon('upload')} 导入与更新</button>`) +
      `<div class="cat-metrics"><div class="cat-metric"><span>颜色款</span><strong>${all.length}</strong><span>${all.reduce((n, p) => n + p.sizes.length, 0)} 个尺码 SKU</span></div><div class="cat-metric"><span>当前在售</span><strong>${all.filter(p => p.status === 'active').length}</strong><span>参与候选筛选</span></div><div class="cat-metric"><span>待完善资料</span><strong>${all.filter(p => issues(p).length).length}</strong><span>图片、价格、库存或属性</span></div><div class="cat-metric"><span>库存版本</span><strong>V${F.state.stockVersion}</strong><span>${date(F.state.stockAsOf)}</span></div></div>` +
      `<form id="cat-filter" class="cat-toolbar"><label class="field cat-search">搜索商品<input id="cat-search" type="search" placeholder="商品名、款号、SKU、颜色或标签" value="${e(ui.search)}"></label><label class="field">品类<select id="cat-category">${options(IO.CATEGORIES, ui.category, '全部品类')}</select></label><label class="field">商品状态<select id="cat-status"><option value="">全部状态</option>${[['active', '在售'], ['draft', '资料草稿'], ['inactive', '已下架'], ['incomplete', '待完善']].map(([v, t]) => `<option value="${v}"${ui.status === v ? ' selected' : ''}>${t}</option>`).join('')}</select></label><label class="field">排序<select id="cat-sort">${[['name', '商品名称'], ['price', '价格从低到高'], ['stock', '库存从多到少']].map(([v, t]) => `<option value="${v}"${ui.sort === v ? ' selected' : ''}>${t}</option>`).join('')}</select></label><button class="btn secondary" type="submit">查询</button></form>` +
      (products.length ? `<div class="cat-cards">${products.slice((ui.page - 1) * 12, ui.page * 12).map(p => `<article class="cat-card"><button class="cat-photo-btn" data-detail="${e(p.id)}" aria-label="查看 ${e(p.name)}">${F.photo(p)}</button><div class="cat-card-body"><div class="cat-card-meta"><span class="cat-muted">${e(p.style)} · ${e(p.color)}</span><span class="badge">${statusName(p.status)}</span></div><h3>${e(p.name)}</h3><div class="cat-tags">${[p.category, ...(p.tags || []).slice(0, 2)].map(t => `<span class="cat-tag">${e(t)}</span>`).join('')}</div><div class="cat-card-meta"><span class="cat-card-price">${priceRange(p)}</span><span class="cat-muted">${F.stock(p)} ${e(p.unit || '件')}可售</span></div><span class="cat-muted">${p.sizes.length} 个尺码 · ${e(p.source)}</span>${issues(p).length ? `<span class="cat-warning-dot cat-muted">${e(issues(p).join(' · '))}</span>` : '<span class="cat-muted">资料齐全 · 可参与选品</span>'}<div class="cat-card-foot"><span class="cat-muted">${e(p.warehouse)}</span><button class="btn secondary" data-detail="${e(p.id)}">${canEdit() ? '查看 / 编辑' : '查看详情'}</button></div></div></article>`).join('')}</div>` : empty('没有符合条件的商品', '调整关键词或筛选条件，或导入新的商品资料。')) +
      `<div class="cat-pager"><span class="cat-muted">共 ${products.length} 个颜色款，每页 12 款</span><div class="cat-pager-controls"><button class="btn secondary" id="cat-prev"${ui.page <= 1 ? ' disabled' : ''}>上一页</button><span>${ui.page} / ${pages}</span><button class="btn secondary" id="cat-next"${ui.page >= pages ? ' disabled' : ''}>下一页</button></div></div>`;
    F.main.querySelectorAll('.cat-card').forEach((card, index) => {
      const p = products[(ui.page - 1) * 12 + index];
      card.querySelector('.cat-card-price').nextElementSibling.textContent = F.stock(p) == null ? '库存待核实' : `${F.stock(p)} ${p.unit || '件'}可售`;
    });
    $('cat-import').onclick = () => F.go('imports'); if ($('cat-new')) $('cat-new').onclick = () => showNewProduct();
    $('cat-filter').onsubmit = ev => { ev.preventDefault(); ui.search = $('cat-search').value.trim(); ui.category = $('cat-category').value; ui.status = $('cat-status').value; ui.sort = $('cat-sort').value; ui.page = 1; renderCatalog(); };
    ['cat-category', 'cat-status', 'cat-sort'].forEach(id => $(id).onchange = () => $('cat-filter').requestSubmit());
    F.main.querySelectorAll('[data-detail]').forEach(b => b.onclick = () => showProduct(b.dataset.detail));
    $('cat-prev').onclick = () => { ui.page--; renderCatalog(); }; $('cat-next').onclick = () => { ui.page++; renderCatalog(); };
  }
  function showProduct(id) {
    const p = F.product(id), editable = canEdit(); if (!p) throw new Error('商品已不存在');
    const formField = (name, title, value, extra = '') => `<label>${title}<input name="${name}" value="${e(value || '')}" ${extra} ${editable ? '' : 'disabled'}></label>`;
    F.modal({ title: `${p.name} · ${p.color}`, body: `<div class="cat-detail"><div class="cat-detail-image">${F.photo(p)}<p class="cat-muted">主图 V${p.imageVersion} · ${p.imageAllowed ? '已确认可用' : '待确认使用范围'}</p><button class="btn secondary" id="cat-detail-media">管理图片素材</button></div><form id="cat-detail-form" class="cat-form"><div class="cat-fields">${formField('name', '商品名称', p.name, 'required maxlength="120"')}<label>品类<select name="category"${editable ? '' : ' disabled'}>${options(IO.CATEGORIES, p.category)}</select></label>${formField('style', '款号（标识固定）', p.style, 'readonly')}${formField('color', '颜色（换色请新建）', p.color, 'readonly')}${formField('material', '材质（以商品资料为准）', p.material)}${formField('season', '季节', p.season)}${formField('tags', '人工确认标签（逗号分隔）', (p.tags || []).join('，'))}${formField('source', '数据来源', p.source, 'readonly')}${formField('jdId', '京东商品 ID', p.jdId)}${formField('jdUrl', '京东商品链接', p.jdUrl, 'type="url"')}<label class="cat-check wide"><input type="checkbox" name="confirmed" ${p.confirmed ? 'checked' : ''}${editable ? '' : ' disabled'}>我已核对商品属性，推荐使用这些人工确认值</label></div><p class="cat-muted">尺码是核价和库存最小单位。价格与库存请到全量更新中心维护。</p>${table(['尺码', 'SKU', '含税销售价', '可售库存'], p.sizes.map(s => [e(s.size), e(s.sku), Number.isInteger(s.price ?? p.price) ? F.money(s.price ?? p.price) : '未提供', Number.isInteger(s.stock) ? s.stock : '未知']))}<div class="cat-note"><p>价格 V${F.state.priceVersion} · ${date(F.state.priceAsOf)}<br>库存 V${F.state.stockVersion} · ${date(F.state.stockAsOf)}</p></div>${editable ? `<details><summary>新增尺码 SKU</summary><p class="cat-muted">只增加资料草稿，不猜测价格库存；补齐专用更新后才可参与报价。</p><div class="cat-new-size"><label>新 SKU<input id="cat-add-sku" maxlength="80"></label><label>尺码<input id="cat-add-size" maxlength="20"></label><button type="button" class="btn secondary" id="cat-add-size-btn">新增尺码</button></div></details>` : ''}<p id="cat-detail-error" class="cat-inline-error" role="alert"></p></form></div>`, footer: `<button class="btn secondary" id="cat-detail-close">关闭</button>${editable ? `<button class="btn secondary" id="cat-toggle">${p.status === 'active' ? '下架商品' : '上架商品'}</button><button class="btn primary" type="submit" form="cat-detail-form">保存资料</button>` : ''}`, bind: () => {
      $('cat-detail-form').querySelector('.cat-table thead th:nth-child(3)').textContent = '基础销售价 / 税口径';
      $('cat-detail-form').querySelectorAll('.cat-table tbody tr').forEach((tr, i) => { const s = p.sizes[i], amount = basePrice(p, s); tr.cells[2].textContent = `${Number.isInteger(amount) ? F.money(amount) : '未提供'} / ${s.priceTaxMode === 'excluded' ? '未税' : '含税'}`; });
      $('cat-detail-close').onclick = F.closeModal; $('cat-detail-media').onclick = () => { F.closeModal(); ui.mediaSearch = p.style; F.go('media'); };
      $('cat-detail-form').onsubmit = invoke(async ev => { ev.preventDefault(); guard(); const fd = new FormData(ev.target); try { IO.validJD(fd.get('jdId').trim(), fd.get('jdUrl').trim()); const name = fd.get('name').trim(); if (!name) throw new Error('商品名不能为空'); const changed = ['name', 'category', 'material', 'season', 'tags'].some(k => (k === 'tags' ? p.tags.join('，') : p[k] || '') !== fd.get(k)); await F.commit('保存商品资料', s => { const x = s.products.find(v => v.id === id); ['name', 'category', 'material', 'season', 'jdId', 'jdUrl'].forEach(k => x[k] = fd.get(k).trim()); x.tags = [...new Set(fd.get('tags').split(/[，,、;]/).map(v => v.trim()).filter(Boolean))]; x.confirmed = fd.has('confirmed'); s.productVersion++; if (changed) F.invalidateImages([id], '商品资料已修改，需重新核对', s); }); F.closeModal(); F.render(); F.notify('商品资料已保存'); } catch (err) { $('cat-detail-error').textContent = err.message; } });
      if (editable) {
        $('cat-toggle').onclick = invoke(() => { guard(); const action = p.status === 'active' ? '下架' : '上架'; if (action === '上架' && issues(p).length) { $('cat-detail-error').textContent = `上架前请处理：${issues(p).join('、')}。价格库存全量范围可选择包含草稿。`; return; } F.modal({ title: `${action}商品`, body: `<p>${e(p.name)} · ${e(p.color)}</p><div class="cat-note${action === '下架' ? ' warning' : ''}">${action === '下架' ? '下架后停止加入新候选，已选草稿在报价前重新检查。历史报价仍保留，可从商品详情重新上架。' : '上架后可参与候选搭配，仍按客户需求检查价格、尺码和库存。'}</div>`, footer: '<button class="btn secondary" id="cat-toggle-back">返回</button><button class="btn primary" id="cat-toggle-confirm">确认' + action + '</button>', bind: () => { $('cat-toggle-back').onclick = () => showProduct(id); $('cat-toggle-confirm').onclick = invoke(async () => { guard(); await F.commit(action + '商品', s => { s.products.find(x => x.id === id).status = action === '下架' ? 'inactive' : 'active'; s.productVersion++; }); F.closeModal(); F.render(); F.notify('商品已' + action); }); } }); });
        $('cat-add-size-btn').onclick = invoke(async () => { guard(); const sku = $('cat-add-sku').value.trim(), size = $('cat-add-size').value.trim(); if (!sku || !size) { $('cat-detail-error').textContent = '新 SKU 和尺码均必填'; return; } if (F.state.products.some(x => x.source === p.source && x.sizes.some(s => s.sku === sku)) || p.sizes.some(s => s.size === size)) { $('cat-detail-error').textContent = 'SKU 或该款尺码已存在'; return; } await F.commit('新增尺码资料', s => { s.products.find(x => x.id === id).sizes.push({ sku, size, price: null, stock: null }); s.productVersion++; }); F.render(); showProduct(id); });
      }
    } });
  }
  function showNewProduct() {
    guard();
    F.modal({ title: '新建商品资料', body: `<form id="cat-new-form" class="cat-form"><div class="cat-fields">${[['name', '商品名称'], ['style', '款号'], ['color', '颜色'], ['sku', '首个尺码 SKU'], ['size', '尺码'], ['source', '数据来源'], ['warehouse', '仓库']].map(([k, t]) => `<label>${t}<input name="${k}" required maxlength="${k === 'name' ? 120 : 80}" value="${k === 'source' ? '演示供应商' : k === 'warehouse' ? '主仓' : ''}"></label>`).join('')}<label>品类<select name="category">${options(IO.CATEGORIES, '上衣')}</select></label><label>单位<select name="unit">${options(['件', '条', '顶', '双'], '件')}</select></label></div><div class="cat-note">新商品保存为资料草稿。接下来在更新中心补全价格和库存，再上传确认原图。</div><p id="cat-new-error" class="cat-inline-error" role="alert"></p></form>`, footer: '<button class="btn secondary" id="cat-new-cancel">取消</button><button class="btn primary" type="submit" form="cat-new-form">保存草稿</button>', bind: () => { $('cat-new-cancel').onclick = F.closeModal; $('cat-new-form').onsubmit = invoke(async ev => { ev.preventDefault(); guard(); const row = Object.fromEntries(new FormData(ev.target)); row._row = 2; Object.keys(row).forEach(k => { if (typeof row[k] === 'string') row[k] = row[k].trim(); }); const batch = IO.validate(F.state, 'product', [row], {}, ''); if (!batch.valid) { $('cat-new-error').textContent = batch.errors.map(e => e.message).join('；'); return; } batch.id = F.uid('manual'); await F.commit('新建商品资料', s => IO.apply(s, batch)); F.closeModal(); F.render(); F.notify('商品草稿已保存，可继续补充价格库存与图片'); }); } });
  }
  function currentScope() { return { source: ui.source, warehouse: ui.warehouse, category: ui.categoryScope, includeDrafts: ui.includeDrafts, priceTable: ui.priceTable, taxMode: ui.taxMode || 'included' }; }
  function requiredFields(kind) { return kind === 'product' ? ['source', 'sku', 'style', 'name', 'category', 'color', 'size', 'unit', 'warehouse', 'material', 'season', 'tags', 'jdId', 'jdUrl'] : kind === 'price' ? ['source', 'sku', 'warehouse', 'unit', 'priceTable', 'currency', 'tax', 'price'] : ['source', 'sku', 'warehouse', 'unit', 'stock']; }
  function templateRows(kind, sample) {
    const fields = requiredFields(kind), rows = [fields.map(k => IO.FIELDS[k][0])];
    if (!sample) return rows;
    if (kind === 'product') {
      F.state.products.slice(0, 2).forEach(p => p.sizes.forEach(s => rows.push(fields.map(k => k === 'sku' ? s.sku : k === 'size' ? s.size : k === 'tags' ? (p.tags || []).join('，') : p[k] || ''))));
      if (rows.length === 1) rows.push(['演示供应商', 'TS101-W-M', 'TS101', '商务纯棉短袖', '上衣', '白色', 'M', '件', '主仓', '棉', '四季', '商务', '', '']);
    } else {
      IO.scopeRows(F.state, currentScope()).forEach(({ p, s }) => {
        const storedMode = ui.priceTable === '批发价' ? s.priceTaxMode || 'included' : s.priceTableTaxModes?.[ui.priceTable] || 'included';
        const amount = ui.priceTable === '批发价' ? basePrice(p, s) : s.priceTables?.[ui.priceTable];
        const price = amount == null || storedMode !== (ui.taxMode || 'included') ? '' : (amount / 100).toFixed(2);
        rows.push(fields.map(k => ({ source: p.source, sku: s.sku, warehouse: p.warehouse, unit: p.unit, priceTable: ui.priceTable, currency: 'CNY', tax: ui.taxMode === 'excluded' ? '未税' : '含税', price, stock: Number.isInteger(s.stock) ? s.stock : '' }[k])));
      });
    }
    return rows;
  }
  function renderImports() {
    const sources = [...new Set(F.state.products.map(p => p.source))]; if (!ui.source) ui.source = sources[0] || '';
    const warehouses = [...new Set(F.state.products.filter(p => p.source === ui.source).map(p => p.warehouse))]; if (!warehouses.includes(ui.warehouse)) ui.warehouse = warehouses[0] || '';
    if (!ui.asOf) ui.asOf = localTime();
    const sheet = ui.sheets[ui.sheet], full = ui.importKind !== 'product', count = IO.scopeRows(F.state, currentScope()).length;
    const priceTables = [...new Set(['批发价', ...(F.state.clients || []).map(c => c.priceTable).filter(Boolean), ...F.state.products.flatMap(p => p.sizes.flatMap(s => Object.keys(s.priceTables || {})))])];
    F.main.innerHTML = heading('导入与更新', '先校验和查看差异，再让完整数据一次生效。', '<button class="btn secondary" id="imp-catalog">返回商品中心</button>') +
      `<div class="cat-tabs" role="tablist" aria-label="导入类型">${['product', 'price', 'stock'].map(k => `<button role="tab" aria-selected="${ui.importKind === k}" data-import-kind="${k}">${label(k)}</button>`).join('')}</div>` +
      `${!canEdit() ? '<div class="cat-note warning" style="margin-bottom:20px">当前为销售角色，可查看批次与下载模板。修改和发布请切换为商品运营或业务负责人。</div>' : ''}<div class="cat-import-layout"><section class="panel"><h2 class="cat-step-title"><span class="cat-step-num">1</span>${full ? '确定全量范围' : '准备商品资料'}</h2>` +
      (full ? `<div class="cat-form"><div class="cat-fields"><label>来源<select id="imp-source">${options(sources, ui.source)}</select></label><label>仓库<select id="imp-warehouse">${options(warehouses, ui.warehouse)}</select></label><label>品类范围<select id="imp-category">${options(IO.CATEGORIES, ui.categoryScope, '全部品类')}</select></label><label>数据业务时间<input id="imp-time" type="datetime-local" value="${e(ui.asOf)}"></label>${ui.importKind === 'price' ? `<label class="wide">价格表<select id="imp-price-table">${options(priceTables, ui.priceTable)}</select></label>` : ''}<label class="cat-check wide"><input id="imp-drafts" type="checkbox"${ui.includeDrafts ? ' checked' : ''}>范围包含资料草稿（为新增商品补价和库存）</label></div></div><p class="cat-muted">本范围应完整覆盖 <strong>${count}</strong> 个 SKU · ${ui.importKind === 'price' ? `${e(ui.priceTable)} / 人民币 / 含税单价；空价需明确补齐，不回退其他表` : '业务确认的可售数量，显式 0 表示缺货'}</p>` : '<p class="cat-muted">按来源 + SKU 识别已有资料，按款号与颜色归并尺码。只更新映射后的非空资料字段，价格库存单独维护。</p>') +
      `<div class="cat-actions"><button class="btn secondary" id="imp-template">${F.icon('download')} 下载空白模板</button><button class="btn secondary" id="imp-example">下载${full ? '本范围全量' : '示例'} CSV</button></div><label class="cat-upload">${F.icon('upload')}<strong>选择 Excel 或 CSV 文件</strong><span class="cat-muted">UTF-8 CSV / XLSX · 单批最多 10000 行 · 12 MB</span><input id="imp-file" type="file" accept=".xlsx,.csv"${canEdit() ? '' : ' disabled'}></label>` +
      (sheet ? `<div class="cat-file-state">已读取 ${e(ui.fileName)} · ${sheet.rows.length - 1} 行数据${ui.sheets.length > 1 ? `<label>选择工作表 <select id="imp-sheet">${ui.sheets.map((s, i) => `<option value="${i}"${i === ui.sheet ? ' selected' : ''}>${e(s.name)}</option>`).join('')}</select></label>` : ''}</div><h2 class="cat-step-title" style="margin-top:24px"><span class="cat-step-num">2</span>核对字段映射</h2><div class="cat-map">${requiredFields(ui.importKind).map(k => `<label>${e(IO.FIELDS[k][0])}<select data-map="${k}"><option value="-1">不导入此列</option>${sheet.rows[0].map((h, i) => `<option value="${i}"${ui.mapping[k] === i ? ' selected' : ''}>${e(h || '空白列 ' + (i + 1))}</option>`).join('')}</select></label>`).join('')}</div><button class="btn primary" id="imp-validate"${canEdit() ? '' : ' disabled'}>校验并预览差异</button>` : '') +
      `<p id="imp-error" class="cat-inline-error" role="alert"></p></section><aside class="panel"><h2 class="cat-subheading">更新规则</h2><ol class="cat-list">${full ? '<li>所选范围内的每个 SKU 都须出现一次。</li><li>缺行、重复、未知 SKU 或空值使整批不生效。</li><li>价格与库存独立发布，业务时间各自保留。</li><li>确认前数据有变化，必须重新校验。</li><li>恢复历史会保留原业务时间，并留下恢复记录。</li>' : '<li>新颜色款保存为资料草稿。</li><li>已有 SKU 的款号、颜色及尺码标识固定。</li><li>同款颜色的多尺码行应使用相同属性。</li><li>价格和库存列不会覆盖已有商务数据。</li><li>完善原图、价、库存和人工属性后上架。</li>'}</ol><h2 class="cat-subheading" style="margin-top:26px">最近批次</h2><div class="cat-history">${F.state.batches.slice(0, 6).map(b => `<div class="cat-history-item"><div class="cat-card-meta"><strong>${label(b.kind)}</strong><span class="cat-batch-status ${e(b.status)}">${batchName(b.status)}</span></div><p>${e(b.filename || '页面维护')}<br>${date(b.appliedAt || b.createdAt)}</p><button class="btn secondary" data-view-batch="${e(b.id)}">查看批次</button>${b.kind !== 'product' && b.status === 'applied' && canEdit() ? `<button class="btn secondary" data-restore="${e(b.id)}">恢复生效前版本</button>` : ''}</div>`).join('') || '<p class="cat-muted">尚无导入记录。选择模板即可开始。</p>'}</div></aside></div><div id="imp-preview"></div>`;
    $('imp-catalog').onclick = () => F.go('catalog');
    F.main.querySelectorAll('[data-import-kind]').forEach(b => b.onclick = () => { ui.importKind = b.dataset.importKind; ui.batch = null; renderImports(); });
    if (full) {
      $('imp-time').step = '0.001';
      if (ui.importKind === 'price') {
        $('imp-price-table').closest('label').insertAdjacentHTML('afterend', `<label class="wide">本批价格税费口径<select id="imp-tax-mode"><option value="included"${ui.taxMode !== 'excluded' ? ' selected' : ''}>含税销售价</option><option value="excluded"${ui.taxMode === 'excluded' ? ' selected' : ''}>未税销售价（报价另行明确加税）</option></select></label>`);
        $('imp-price-table').closest('.cat-form').nextElementSibling.innerHTML = `本范围应完整覆盖 <strong>${count}</strong> 个 SKU · ${e(ui.priceTable)} / 人民币 / ${ui.taxMode === 'excluded' ? '未税' : '含税'}单价。切换税口径后模板单价留空，请填写业务确认的新价格，不自动换算。`;
        $('imp-tax-mode').onchange = () => { ui.taxMode = $('imp-tax-mode').value; ui.batch = null; renderImports(); };
      }
      ['imp-source', 'imp-warehouse', 'imp-category'].forEach((id, i) => $(id).onchange = () => { ui[['source', 'warehouse', 'categoryScope'][i]] = $(id).value; ui.batch = null; renderImports(); });
      $('imp-time').onchange = () => { ui.asOf = $('imp-time').value; ui.batch = null; renderPreview(); }; $('imp-drafts').onchange = () => { ui.includeDrafts = $('imp-drafts').checked; ui.batch = null; renderImports(); };
      if ($('imp-price-table')) $('imp-price-table').onchange = () => { ui.priceTable = $('imp-price-table').value; ui.batch = null; renderImports(); };
    }
    $('imp-template').onclick = () => downloadCSV(templateRows(ui.importKind, false), `${label(ui.importKind)}-空白模板.csv`);
    $('imp-example').onclick = () => downloadCSV(templateRows(ui.importKind, true), `${label(ui.importKind)}-${full ? '本范围完整数据' : '示例'}.csv`);
    $('imp-file').onchange = invoke(async ev => { guard(); const file = ev.target.files[0]; if (!file) return; $('imp-error').textContent = '正在解析文件…'; try { const sheets = await IO.readFile(file); ui.sheets = sheets; ui.sheet = 0; ui.mapping = IO.autoMapping(sheets[0].rows[0]); ui.fileName = file.name; ui.batch = null; renderImports(); } catch (err) { $('imp-error').textContent = err.message; } });
    if ($('imp-sheet')) $('imp-sheet').onchange = () => { ui.sheet = Number($('imp-sheet').value); ui.mapping = IO.autoMapping(ui.sheets[ui.sheet].rows[0]); ui.batch = null; renderImports(); };
    F.main.querySelectorAll('[data-map]').forEach(s => s.onchange = () => { ui.mapping[s.dataset.map] = Number(s.value); ui.batch = null; renderPreview(); });
    if ($('imp-validate')) $('imp-validate').onclick = invoke(async () => { guard(); try { const mapped = requiredFields(ui.importKind).filter(k => ui.mapping[k] >= 0).map(k => ui.mapping[k]); if (new Set(mapped).size !== mapped.length) throw new Error('同一文件列不能映射给多个字段'); const rows = IO.records(sheet.rows, Object.fromEntries(requiredFields(ui.importKind).map(k => [k, ui.mapping[k] ?? -1]))); const batch = IO.validate(F.state, ui.importKind, rows, currentScope(), full ? new Date(ui.asOf).toISOString() : ''); batch.id = F.uid('batch'); batch.filename = ui.fileName; batch.createdAt = new Date().toISOString(); await F.commit('校验导入文件', s => s.batches.unshift(F.clone(batch))); ui.batch = batch; renderImports(); $('imp-preview').scrollIntoView({ behavior: 'smooth', block: 'start' }); } catch (err) { $('imp-error').textContent = err.message; } });
    F.main.querySelectorAll('[data-view-batch]').forEach(b => b.onclick = () => { ui.batch = F.clone(F.state.batches.find(x => x.id === b.dataset.viewBatch)); renderPreview(); $('imp-preview').scrollIntoView({ block: 'start' }); });
    const restores = (F.state.dataVersions || []).filter(v => v.restoredFrom).slice(0, 4);
    if (restores.length && canEdit()) F.main.querySelector('aside .cat-history').insertAdjacentHTML('beforeend', restores.map(v => `<div class="cat-history-item"><strong>${label(v.kind)} · 恢复记录 V${v.version}</strong><p>${e(v.scope.source)} / ${e(v.scope.warehouse)}<br>${date(v.createdAt)}<br>原业务时间仍按原数据判断</p><button class="btn secondary" data-restore="${e(v.id)}">恢复到此操作之前</button></div>`).join(''));
    F.main.querySelectorAll('[data-restore]').forEach(b => b.onclick = () => restoreBatch(b.dataset.restore));
    renderPreview();
  }
  function renderPreview() {
    if (!$('imp-preview')) return; const b = ui.batch; if (!b) { $('imp-preview').innerHTML = ''; return; }
    const full = b.kind !== 'product';
    $('imp-preview').innerHTML = `<section class="panel cat-import-preview" style="margin-top:22px"><h2 class="cat-step-title"><span class="cat-step-num">3</span>校验结果与差异 <span class="cat-batch-status ${e(b.status)}">${batchName(b.status)}</span></h2><p class="cat-muted">${e(b.filename || '页面维护')} · 批次 ${e(b.id)}${full ? ` · ${e(b.scope.source)} / ${e(b.scope.warehouse)} / ${e(b.scope.category || '全部品类')} · 业务时间 ${date(b.asOf)}` : ''}</p><div class="cat-metrics"><div class="cat-metric"><span>文件行数</span><strong>${b.input.length}</strong></div><div class="cat-metric"><span>${full ? '全量覆盖' : '有效变化'}</span><strong>${full ? b.covered + ' / ' + b.expected : b.changes.length}</strong></div><div class="cat-metric"><span>阻断错误</span><strong>${b.errors.length}</strong></div><div class="cat-metric"><span>${full ? '覆盖率' : '非阻断提示'}</span><strong>${full ? b.coverage + '%' : b.warnings.length}</strong></div></div>` +
      (b.errors.length ? `<div class="cat-note error"><strong>整批未生效，请修正文件后重新校验。</strong><ul class="cat-errors">${b.errors.slice(0, 50).map(x => `<li>${x.row ? `第 ${x.row} 行 · ` : ''}${e(x.sku || '')} ${e(x.message)}</li>`).join('')}</ul>${b.errors.length > 50 ? '<p>页面显示前 50 个错误，下载报告可查看全部。</p>' : ''}</div>` : '') +
      (b.warnings.length ? `<div class="cat-note warning"><ul>${b.warnings.slice(0, 10).map(w => `<li>第 ${w.row} 行：${e(w.message)}</li>`).join('')}</ul></div>` : '') +
      (b.changes.length ? `<h3>差异明细</h3>${full ? table(['SKU', '商品 / 尺码', '更新前', '更新后', '差异'], b.changes.slice(0, 50).map(c => [e(c.sku), `${e(c.name)} / ${e(c.size)}`, c.before == null ? '未知' : b.kind === 'price' ? F.money(c.before) : c.before, b.kind === 'price' ? F.money(c.after) : c.after, c.before == null ? '首次提供' : b.kind === 'price' ? F.money(c.after - c.before) : (c.after - c.before > 0 ? '+' : '') + (c.after - c.before)])) : table(['来源', 'SKU', '商品', '变化'], b.changes.slice(0, 50).map(c => [e(c.row.source), e(c.row.sku), e(c.row.name || c.before?.name), e(c.action)]))}<p class="cat-muted">页面显示前 ${Math.min(50, b.changes.length)} 条；完整差异可下载 CSV。</p>` : '') +
      `<div class="cat-actions"><button class="btn secondary" id="imp-report">下载完整校验报告</button>${b.changes.length ? '<button class="btn secondary" id="imp-diff">下载全部差异</button>' : ''}${b.status === 'ready' && canEdit() ? '<button class="btn secondary" id="imp-cancel">取消批次</button><button class="btn primary" id="imp-apply">确认整批生效</button>' : ''}</div><p id="imp-apply-error" class="cat-inline-error" role="alert"></p></section>`;
    if (b.kind === 'price') $('imp-preview').querySelector('.cat-step-title').insertAdjacentHTML('afterend', `<div class="cat-note">价格表：${e(b.scope.priceTable || '批发价')} · ${b.scope.taxMode === 'excluded' ? '未税销售价' : '含税销售价'} · 此口径与价格值一同版本化</div>`);
    $('imp-report').onclick = () => downloadCSV([['批次', '行号', 'SKU', '结果', '说明'], ...(b.errors.length ? b.errors.map(x => [b.id, x.row || '批次', x.sku, '错误', x.message]) : [[b.id, '', '', '通过', '整批校验通过；是否已生效以批次状态为准']]), ...b.warnings.map(x => [b.id, x.row, '', '提示', x.message])], `校验报告-${b.id}.csv`);
    if ($('imp-diff')) $('imp-diff').onclick = () => downloadCSV(full ? [['SKU', '商品', '尺码', '更新前', '更新后'], ...b.changes.map(c => [c.sku, c.name, c.size, c.before == null ? '未知' : b.kind === 'price' ? (c.before / 100).toFixed(2) : c.before, b.kind === 'price' ? (c.after / 100).toFixed(2) : c.after])] : [['来源', 'SKU', '商品', '变化'], ...b.changes.map(c => [c.row.source, c.row.sku, c.row.name || c.before?.name, c.action])], `差异-${b.id}.csv`);
    if ($('imp-cancel')) $('imp-cancel').onclick = invoke(async () => { guard(); await F.commit('取消导入批次', s => { const x = s.batches.find(x => x.id === b.id); if (x.status !== 'ready') throw new Error('批次状态已变化'); x.status = 'cancelled'; }); ui.batch.status = 'cancelled'; renderImports(); });
    if ($('imp-apply')) $('imp-apply').onclick = invoke(async () => {
      guard(); const button = $('imp-apply'); button.disabled = true;
      try {
        await F.commit('发布' + label(b.kind) + '批次', s => {
          const changedIds = b.kind === 'product' ? b.changes.filter(c => c.before && ['name', 'category', 'material', 'season', 'tags'].some(k => c.row[k] && c.row[k] !== (k === 'tags' ? (c.before.tags || []).join('，') : c.before[k]))).map(c => c.before.id) : [];
          IO.apply(s, b);
          if (changedIds.length) F.invalidateImages([...new Set(changedIds)], '商品属性导入有变化，请重新核对相关图片', s);
        });
        ui.batch = F.clone(F.state.batches.find(x => x.id === b.id)); F.render(); F.notify(`${label(b.kind)}已整批生效，历史报价保留原值`);
      } catch (err) { $('imp-apply-error').textContent = err.message; button.disabled = false; }
    });
  }
  function restoreBatch(id) {
    guard(); const v = F.state.dataVersions?.find(x => x.id === id); if (!v) { F.notify('该批次没有可恢复的数据版本', 'error'); return; }
    F.modal({ title: `恢复${label(v.kind)}生效前版本`, body: `<p>将恢复 <strong>${e(v.scope.source)} / ${e(v.scope.warehouse)} / ${e(v.scope.category || '全部品类')}</strong> 范围内 ${v.before.length} 个 SKU 的${v.kind === 'price' ? '价格' : '库存'}。</p><div class="cat-note warning">恢复只影响本类数据，历史报价和其他来源保留。原业务时间同时恢复，旧库存仍可能过期。恢复操作另建新版本，可再次恢复操作前状态。</div><p id="imp-restore-error" class="cat-inline-error" role="alert"></p>`, footer: '<button class="btn secondary" id="imp-restore-cancel">取消</button><button class="btn primary" id="imp-restore-confirm">确认恢复并记录</button>', bind: () => { $('imp-restore-cancel').onclick = F.closeModal; $('imp-restore-confirm').onclick = invoke(async () => { guard(); try { await F.commit('恢复历史' + label(v.kind), s => IO.restore(s, id)); F.closeModal(); F.render(); F.notify('已恢复历史数据并保留原业务时间'); } catch (err) { $('imp-restore-error').textContent = err.message; } }); } });
  }
  F.pages.catalog = renderCatalog; F.pages.imports = renderImports;
  F.catalog = { showProduct, issues, templateRows, ui };
})();

(function () {
  'use strict';
  const F = window.F, IO = window.FIO, ui = F.catalog.ui, e = F.esc, $ = id => document.getElementById(id);
  const guard = () => F.requireRole(['operator', 'manager', 'admin']);
  const canEdit = () => ['operator', 'manager', 'admin'].includes(F.state.role);
  const invoke = fn => async ev => { try { await fn(ev); } catch (err) { F.notify(err.message || '操作未完成', 'error'); } };
  const opts = (rows, value) => rows.map(([v, name]) => `<option value="${e(v)}"${v === value ? ' selected' : ''}>${e(name)}</option>`).join('');
  const status = m => m.status === 'failed' ? '上传失败' : m.links?.some(l => l.allowed) ? '已确认可用' : m.links?.length ? '已关联 · 待确认使用' : '待匹配';
  const size = n => n > 1024 * 1024 ? (n / 1024 / 1024).toFixed(1) + ' MB' : Math.round(n / 1024) + ' KB';
  function fileSuggestions(filename) {
    const mapping = (F.state.mediaMappings || []).filter(r => r.filename.toLowerCase() === filename.toLowerCase());
    const hits = [];
    for (const r of mapping) for (const p of F.state.products) if (p.source === r.source && (r.sku ? p.sizes.some(s => s.sku === r.sku) || p.sku === r.sku : p.style === r.style && p.color === r.color)) hits.push(p.id);
    if (hits.length) return [...new Set(hits)];
    const stem = filename.replace(/\.[^.]+$/, '').toLowerCase();
    for (const p of F.state.products) {
      const names = [p.sku, p.style + '_' + p.color, ...p.sizes.map(s => s.sku), p.jdId].filter(Boolean).map(v => String(v).toLowerCase());
      if (names.some(n => stem === n || stem.startsWith(n + '_') || stem.startsWith(n + '-'))) hits.push(p.id);
    }
    return [...new Set(hits)];
  }
  function renderMedia() {
    const all = F.state.media || [], query = ui.mediaSearch.toLowerCase();
    const list = all.filter(m => (!query || [m.name, ...(m.aliases || []), ...(m.links || []).map(l => { const p = F.product(l.productId); return p ? `${p.name} ${p.style} ${p.color}` : ''; })].join(' ').toLowerCase().includes(query)) && (ui.mediaFilter === 'all' || (ui.mediaFilter === 'failed' ? m.status === 'failed' : ui.mediaFilter === 'unmatched' ? m.status !== 'failed' && !m.links?.length : m.links?.some(l => l.allowed))));
    F.main.innerHTML = `<div class="page-heading"><div><p class="eyebrow">商品与资料</p><h1>图片素材</h1><p>将下载的商品图片与颜色款对应，确认后用于选品和客户提案。</p></div><button class="btn secondary" id="med-catalog">返回商品中心</button></div><div class="cat-metrics"><div class="cat-metric"><span>本地素材</span><strong>${all.filter(m => m.status !== 'failed').length}</strong><span>相同内容仅保留一份</span></div><div class="cat-metric"><span>待人工匹配</span><strong>${all.filter(m => m.status !== 'failed' && !m.links?.length).length}</strong><span>建议匹配也需核对颜色</span></div><div class="cat-metric"><span>已确认可用</span><strong>${all.filter(m => m.links?.some(l => l.allowed)).length}</strong><span>按商品分别保留关联</span></div><div class="cat-metric"><span>失败项</span><strong>${all.filter(m => m.status === 'failed').length}</strong><span>重新选择修正后的文件</span></div></div><div class="cat-import-layout"><section class="panel"><h2 class="cat-step-title">${F.icon('image')} 上传商品原图</h2><p class="cat-muted">先在商品详情维护京东 ID / 链接，通过 Chrome 图片插件下载，再把本地文件上传到这里。</p><label class="cat-upload">${F.icon('upload')}<strong>${ui.mediaBusy ? e(ui.mediaProgress) : '选择图片或 ZIP 压缩包'}</strong><span class="cat-muted">JPG / PNG / WebP · 单图 8 MB · 最多 100 项 · ZIP 展开后 40 MB</span><input id="med-files" type="file" accept=".jpg,.jpeg,.png,.webp,.zip" multiple${!canEdit() || ui.mediaBusy ? ' disabled' : ''}></label><p id="med-upload-state" class="cat-form-status" role="status">${e(ui.mediaProgress)}</p><div class="cat-note">上传只读取本地文件。系统不会在此操作京东账号。大图会另存为最长边 1400 像素的原型预览，原文件仍保留在你的电脑。</div></section><aside class="panel"><h2 class="cat-subheading">用映射表减少手工查找</h2><p class="cat-muted">文件名精确匹配来源 + SKU。京东 ID 可以对应多个颜色款，系统不会自动确认错色风险。</p><div class="cat-actions"><button class="btn secondary" id="med-template">下载映射表模板</button><button class="btn secondary" id="med-map-list">查看已导入映射</button></div><label class="cat-upload" style="padding:16px">上传映射 CSV / XLSX<input id="med-mapping" type="file" accept=".csv,.xlsx"${canEdit() ? '' : ' disabled'}></label><p class="cat-muted">已保存 ${(F.state.mediaMappings || []).length} 条映射建议</p><p id="med-map-error" class="cat-form-status" role="alert"></p></aside></div><form id="med-filter" class="cat-toolbar" style="margin-top:26px"><label class="field cat-search">搜索素材<input id="med-search" type="search" placeholder="文件名、商品名或款号" value="${e(ui.mediaSearch)}"></label><label class="field">素材状态<select id="med-status">${opts([['all', '全部状态'], ['unmatched', '待匹配'], ['allowed', '已确认可用'], ['failed', '上传失败']], ui.mediaFilter)}</select></label><button class="btn secondary" type="submit">查询</button></form><div class="cat-media-grid">${list.map(m => `<article class="cat-media-card">${m.url ? `<img src="${e(m.url)}" alt="${e(m.name)}" loading="lazy">` : `<div class="cat-media-placeholder">${F.icon('image')}</div>`}<div class="cat-media-info"><span class="cat-batch-status ${m.status === 'failed' ? 'invalid' : ''}">${status(m)}</span><h3 style="margin-top:10px">${e(m.name)}</h3><p>${m.width ? `${m.width} × ${m.height} · ${size(m.bytes)}<br>` : ''}${m.links?.length ? m.links.map(l => { const p = F.product(l.productId); return p ? `${e(p.style)} / ${e(p.color)} · ${l.usage === 'main' ? '主图' : '细节图'}` : '商品已不存在'; }).join('<br>') : m.suggestions?.length ? `找到 ${m.suggestions.length} 个匹配建议，待核对` : '尚未关联商品'}</p>${m.warning ? `<p class="cat-warning-dot">${e(m.warning)}</p>` : ''}${m.error ? `<p>${e(m.error)}</p>` : ''}<div class="cat-actions">${m.status === 'failed' ? `<button class="btn secondary" data-retry="${e(m.id)}"${canEdit() ? '' : ' disabled'}>重试失败项</button>` : `<button class="btn ${m.links?.length ? 'secondary' : 'primary'}" data-media-edit="${e(m.id)}">${canEdit() ? m.links?.length ? '查看 / 管理' : '匹配商品' : '查看素材'}</button>`}</div></div></article>`).join('')}</div>${!list.length ? '<div class="cat-empty"><h3>还没有符合条件的素材</h3><p>上传真实商品图片，或调整筛选条件。商品示例图可在商品中心查看。</p></div>' : ''}<input id="med-retry-file" type="file" accept=".jpg,.jpeg,.png,.webp" hidden>`;
    $('med-catalog').onclick = () => F.go('catalog');
    $('med-filter').onsubmit = ev => { ev.preventDefault(); ui.mediaSearch = $('med-search').value.trim(); ui.mediaFilter = $('med-status').value; renderMedia(); }; $('med-status').onchange = () => $('med-filter').requestSubmit();
    $('med-files').onchange = invoke(ev => ingestFiles(Array.from(ev.target.files)));
    $('med-template').onclick = () => { const p = F.state.products[0]; const rows = [['来源', 'SKU', '款号', '颜色', '京东ID', '京东链接', '文件名', '图片用途'], p ? [p.source, p.sizes[0]?.sku || p.sku, p.style, p.color, p.jdId || '', p.jdUrl || '', (p.sizes[0]?.sku || p.sku) + '.jpg', '主图'] : ['演示供应商', 'TS101-W-M', 'TS101', '白色', '', '', 'TS101-W-M.jpg', '主图']]; F.download(new Blob([IO.csv(rows)], { type: 'text/csv;charset=utf-8' }), '京东图片-商品映射模板.csv'); };
    $('med-mapping').onchange = invoke(async ev => { guard(); const file = ev.target.files[0]; if (!file) return; try { const sheets = await IO.readFile(file); if (sheets.length > 1) throw new Error('图片映射文件请只保留一个有数据的工作表'); const rows = IO.records(sheets[0].rows, IO.autoMapping(sheets[0].rows[0])); const seen = new Set(); for (const r of rows) { if (!r.filename || !r.source || (!r.sku && !(r.style && r.color))) throw new Error(`第 ${r._row} 行缺文件名、来源或 SKU / 款号颜色`); if (/[\\/]/.test(r.filename)) throw new Error(`第 ${r._row} 行只填文件名，不填目录`); if (r._error) throw new Error(`第 ${r._row} 行：${r._error}`); IO.validJD(r.jdId || '', r.jdUrl || ''); const products = F.state.products.filter(p => p.source === r.source && (r.sku ? p.sizes.some(s => s.sku === r.sku) || p.sku === r.sku : p.style === r.style && p.color === r.color)); if (products.length !== 1) throw new Error(`第 ${r._row} 行不能唯一找到颜色款，请核对来源和 SKU`); const k = JSON.stringify([r.filename.toLowerCase(), products[0].id]); if (seen.has(k)) throw new Error(`第 ${r._row} 行图片关联重复`); seen.add(k); r.productId = products[0].id; if (r.usage && !['主图', '细节图'].includes(r.usage)) throw new Error(`第 ${r._row} 行图片用途须为主图或细节图`); } await F.commit('导入京东图片映射', s => { s.mediaMappings ||= []; for (const r of rows) { const i = s.mediaMappings.findIndex(v => v.filename.toLowerCase() === r.filename.toLowerCase() && v.productId === r.productId); if (i >= 0) s.mediaMappings[i] = r; else s.mediaMappings.push(r); } }); for (const m of F.state.media) if (!m.links?.length && m.status !== 'failed') m.suggestions = fileSuggestions(m.name); F.save(); renderMedia(); F.notify(`已保存 ${rows.length} 条映射建议，图片仍需人工确认`); } catch (err) { $('med-map-error').textContent = err.message; } });
    $('med-map-list').onclick = () => F.modal({ title: '已导入的图片映射建议', body: `<div class="table-wrap" tabindex="0"><table class="cat-table"><thead><tr><th>文件名</th><th>来源</th><th>SKU / 颜色款</th><th>京东 ID</th></tr></thead><tbody>${(F.state.mediaMappings || []).map(r => `<tr><td>${e(r.filename)}</td><td>${e(r.source)}</td><td>${e(r.sku || r.style + '/' + r.color)}</td><td>${e(r.jdId || '未提供')}</td></tr>`).join('')}</tbody></table></div>${!F.state.mediaMappings?.length ? '<p>尚无映射，下载模板填写后上传即可。</p>' : ''}`, footer: '<button class="btn secondary" id="med-map-close">关闭</button>', bind: () => $('med-map-close').onclick = F.closeModal });
    F.main.querySelectorAll('[data-media-edit]').forEach(b => b.onclick = () => showMedia(b.dataset.mediaEdit));
    F.main.querySelectorAll('[data-retry]').forEach(b => b.onclick = () => { guard(); $('med-retry-file').dataset.retryId = b.dataset.retry; $('med-retry-file').click(); });
    $('med-retry-file').onchange = invoke(ev => ingestFiles(Array.from(ev.target.files), ev.target.dataset.retryId));
  }
  async function decodeImage(file) {
    if (file.size > 8 * 1024 * 1024) throw new Error('图片超过 8 MB，请压缩或替换后重试');
    const buffer = await file.arrayBuffer(), bytes = new Uint8Array(buffer);
    let mime = '';
    if (bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255) mime = 'image/jpeg';
    else if ([137, 80, 78, 71, 13, 10, 26, 10].every((v, i) => bytes[i] === v)) mime = 'image/png';
    else if (String.fromCharCode(...bytes.slice(0, 4)) === 'RIFF' && String.fromCharCode(...bytes.slice(8, 12)) === 'WEBP') mime = 'image/webp';
    if (!mime || !/\.(jpe?g|png|webp)$/i.test(file.name)) throw new Error('文件内容与支持的 JPG / PNG / WebP 类型不符');
    const sha = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', buffer))).map(v => v.toString(16).padStart(2, '0')).join('');
    const objectUrl = URL.createObjectURL(new Blob([buffer], { type: mime }));
    let image;
    try { image = await new Promise((resolve, reject) => { const img = new Image(), timer = setTimeout(() => reject(new Error('图片解码超时，请重新保存原图后重试')), 15000); img.onload = () => { clearTimeout(timer); resolve(img); }; img.onerror = () => { clearTimeout(timer); reject(new Error('图片损坏或无法解码，请替换文件后重试')); }; img.src = objectUrl; });
      const width = image.naturalWidth, height = image.naturalHeight;
      if (!width || !height || width * height > 32 * 1024 * 1024) throw new Error('图片像素超过 3200 万或尺寸无效');
      const scale = Math.min(1, 1400 / Math.max(width, height));
      const canvas = document.createElement('canvas'); canvas.width = Math.max(1, Math.round(width * scale)); canvas.height = Math.max(1, Math.round(height * scale));
      const ctx = canvas.getContext('2d'); ctx.fillStyle = '#ffffff'; ctx.fillRect(0, 0, canvas.width, canvas.height); ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
      const url = canvas.toDataURL(mime === 'image/png' && width * height < 600000 ? 'image/png' : 'image/jpeg', 0.85);
      const warning = [Math.min(width, height) < 400 ? '短边不足 400 像素，建议只作参考' : '', height / width > 2.5 ? '疑似详情长图，推荐关联为细节图' : '', scale < 1 ? `原型预览已缩至 ${canvas.width} × ${canvas.height}` : ''].filter(Boolean).join('；');
      return { hash: sha, url, width, height, previewWidth: canvas.width, previewHeight: canvas.height, bytes: file.size, mime, warning };
    } finally { URL.revokeObjectURL(objectUrl); }
  }
  async function ingestFiles(input, retryId) {
    guard(); if (ui.mediaBusy || !input.length) return;
    if (input.length > 100) throw new Error('每次最多上传 100 个文件');
    ui.mediaBusy = true; ui.mediaProgress = '正在检查文件…'; renderMedia();
    let success = 0, failed = 0, duplicate = 0;
    try {
      const files = [];
      for (const file of input) {
        if (/\.zip$/i.test(file.name)) {
          if (file.size > 20 * 1024 * 1024) throw new Error('ZIP 文件超过 20 MB');
          const zip = await IO.unzip(await file.arrayBuffer(), 100);
          for (const item of Object.values(zip.files).filter(f => !f.dir)) {
            if (/__MACOSX|(^|\/)\./.test(item.name)) continue;
            if (!/\.(jpe?g|png|webp)$/i.test(item.name)) continue;
            files.push(new File([await item.async('arraybuffer')], item.name.split('/').pop(), { type: '' }));
          }
        } else files.push(file);
      }
      if (!files.length) throw new Error('压缩包没有 JPG / PNG / WebP 图片');
      if (files.length > 100 || files.reduce((n, f) => n + f.size, 0) > 40 * 1024 * 1024) throw new Error('本批图片超过 100 项或合计 40 MB');
      for (let i = 0; i < files.length; i++) {
        guard(); const file = files[i]; ui.mediaProgress = `正在处理 ${i + 1} / ${files.length}：${file.name}`;
        if ($('med-upload-state')) $('med-upload-state').textContent = ui.mediaProgress;
        try {
          const decoded = await decodeImage(file);
          const existing = F.state.media.find(m => m.hash === decoded.hash && m.status !== 'failed');
          if (existing) {
            await F.commit('图片内容去重', s => {
              const m = s.media.find(m => m.id === existing.id); m.aliases ||= [];
              if (file.name !== m.name && !m.aliases.includes(file.name)) m.aliases.push(file.name);
              if (retryId) {
                const at = s.media.findIndex(m => m.id === retryId && m.status === 'failed');
                if (at >= 0) { s.mediaRetries ||= []; s.mediaRetries.unshift({ ...s.media[at], resolvedTo: m.id, resolvedAt: new Date().toISOString() }); s.media.splice(at, 1); }
              }
            }); duplicate++; continue;
          }
          const newItem = { id: retryId || F.uid('media'), name: file.name, ...decoded, status: 'uploaded', links: [], suggestions: fileSuggestions(file.name), createdAt: new Date().toISOString(), uploadedBy: F.state.role };
          await F.commit(retryId ? '重试图片上传' : '上传商品图片', s => { const at = retryId ? s.media.findIndex(m => m.id === retryId) : -1; if (at >= 0) s.media[at] = newItem; else s.media.unshift(newItem); }); success++;
        } catch (err) {
          // 保存失败项本身也须原子成功；存储配额失败不能被报告为图片已入库。
          await F.commit('记录图片失败项', s => { const item = { id: retryId || F.uid('media-failed'), name: file.name, status: 'failed', error: err.message, createdAt: new Date().toISOString(), links: [] }; const at = retryId ? s.media.findIndex(m => m.id === retryId) : -1; if (at >= 0) s.media[at] = item; else s.media.unshift(item); }); failed++;
        }
      }
      ui.mediaProgress = `已入库 ${success} 张，内容去重 ${duplicate} 张，失败 ${failed} 项。`;
    } catch (err) { ui.mediaProgress = `本批停止：${err.message}。已成功入库 ${success} 张。`; }
    finally { ui.mediaBusy = false; renderMedia(); }
  }
  function showMedia(id) {
    const m = F.state.media.find(x => x.id === id); if (!m) throw new Error('素材不存在');
    const products = [...F.state.products].sort((a, b) => Number((m.suggestions || []).includes(b.id)) - Number((m.suggestions || []).includes(a.id)));
    const first = m.links?.[0]?.productId || m.suggestions?.[0] || products[0]?.id || '';
    const existing = m.links?.find(l => l.productId === first);
    F.modal({ title: '核对图片与颜色款', body: `<form id="med-edit-form" class="cat-form"><div class="cat-detail"><div><img class="cat-media-preview" src="${e(m.url)}" alt="${e(m.name)}"><p class="cat-muted">${e(m.name)} · ${m.width} × ${m.height}<br>上传于 ${new Date(m.createdAt).toLocaleString('zh-CN')}</p>${m.warning ? `<div class="cat-note warning">${e(m.warning)}</div>` : ''}</div><div class="cat-form"><label>关联商品<select id="med-product" name="productId" required>${opts(products.map(p => [p.id, `${(m.suggestions || []).includes(p.id) ? '建议 · ' : ''}${p.style} / ${p.color} · ${p.name}`]), first)}</select></label><div id="med-product-preview"></div><div class="cat-fields"><label>图片用途<select id="med-usage" name="usage">${opts([['main', '主图（替换当前主图）'], ['detail', '细节图（新增关联）']], existing?.usage || 'main')}</select></label><label>来源京东 ID<input id="med-jd-id" name="jdId" value="${e(existing?.jdId || F.product(first)?.jdId || '')}" maxlength="20"></label><label class="wide">来源京东链接<input id="med-jd-url" name="jdUrl" type="url" value="${e(existing?.jdUrl || F.product(first)?.jdUrl || '')}"></label></div><label class="cat-check"><input name="confirmed" type="checkbox"${existing?.confirmed ? ' checked' : ''} required>已核对款式、颜色和商品对应关系</label><label class="cat-check"><input name="allowed" type="checkbox"${existing?.allowed ? ' checked' : ''}>已确认可用于企业选品、AI 制图和客户提案</label><div class="cat-note">主图替换或禁用会使使用它的 AI 图片需要重新复核；新增未使用的细节图保留已有图像状态。历史报价不换图。</div><p id="med-edit-error" class="cat-inline-error" role="alert"></p></div></div>${m.links?.length ? `<h3>已有商品关联</h3><div class="cat-history">${m.links.map(l => `<div class="cat-history-item"><strong>${e(F.product(l.productId)?.name || l.productId)} / ${e(F.product(l.productId)?.color || '')}</strong><p>${l.usage === 'main' ? '主图' : '细节图'} · ${l.allowed ? '可用于提案和生成' : '已禁用 / 未确认使用'}</p>${canEdit() && l.allowed ? `<button type="button" class="btn secondary" data-media-disable="${e(l.productId)}">禁用此商品关联</button>` : ''}</div>`).join('')}</div>` : ''}</form>`, footer: `<button class="btn secondary" id="med-close">关闭</button><button class="btn secondary" id="med-download">下载预览图片</button>${canEdit() ? '<button class="btn primary" type="submit" form="med-edit-form">保存关联与使用范围</button>' : ''}`, bind: () => {
      $('med-close').onclick = F.closeModal;
      $('med-download').onclick = invoke(async () => { const blob = await (await fetch(m.url)).blob(); F.download(blob, '预览-' + m.name.replace(/\.[^.]+$/, '') + (blob.type === 'image/png' ? '.png' : '.jpg')); });
      const update = () => { const p = F.product($('med-product').value); const link = m.links?.find(l => l.productId === p?.id); const mapped = (F.state.mediaMappings || []).find(r => r.productId === p?.id && r.filename.toLowerCase() === m.name.toLowerCase()); $('med-product-preview').innerHTML = p ? `<div class="cat-note"><strong>${e(p.style)} · ${e(p.color)}</strong><br>来源：${e(p.source)} · 已有主图 V${p.imageVersion}</div>` : ''; if (p) { $('med-jd-id').value = link?.jdId || mapped?.jdId || p.jdId || ''; $('med-jd-url').value = link?.jdUrl || mapped?.jdUrl || p.jdUrl || ''; $('med-usage').value = link?.usage || (mapped?.usage === '细节图' ? 'detail' : 'main'); } };
      $('med-product').onchange = update; update();
      if (!canEdit()) $('med-edit-form').querySelectorAll('input,select').forEach(n => n.disabled = true);
      $('med-edit-form').onsubmit = invoke(async ev => { ev.preventDefault(); guard(); const fd = new FormData(ev.target), productId = fd.get('productId'), usage = fd.get('usage'), allowed = fd.has('allowed'), confirmed = fd.has('confirmed'); try { if (!productId || !confirmed) throw new Error('请选择商品并确认款式颜色'); IO.validJD(fd.get('jdId').trim(), fd.get('jdUrl').trim()); if (usage === 'main' && !allowed) throw new Error('作为商品主图需要先确认使用范围；也可保存为待用细节图'); await F.commit('确认商品图片关联', s => { const item = s.media.find(x => x.id === id), p = s.products.find(x => x.id === productId); if (!item || !p) throw new Error('素材或商品已变化，请重新打开'); item.links ||= []; const at = item.links.findIndex(l => l.productId === productId); const link = { productId, usage, allowed, confirmed, jdId: fd.get('jdId').trim(), jdUrl: fd.get('jdUrl').trim(), confirmedAt: new Date().toISOString(), confirmedBy: s.role }; if (at >= 0) item.links[at] = link; else item.links.push(link); if (usage === 'main') { const changed = p.image !== item.url || !p.imageAllowed; for (const other of s.media) for (const l of other.links || []) if (other.id !== id && l.productId === productId && l.usage === 'main') l.usage = 'detail'; p.image = item.url; p.crop = null; p.imageAllowed = true; p.mainMediaId = id; if (changed) { p.imageVersion++; p.confirmed = false; F.invalidateImages([productId], '商品主图已更换，请重新核对商品属性及生成图', s); } } s.productVersion++; }); F.closeModal(); F.render(); F.notify(usage === 'main' ? '主图已更新，请在商品详情复核属性后使用' : '细节图关联已保存'); } catch (err) { $('med-edit-error').textContent = err.message; } });
      document.querySelectorAll('[data-media-disable]').forEach(button => button.onclick = invoke(() => { guard(); const productId = button.dataset.mediaDisable, p = F.product(productId); F.modal({ title: '禁用图片使用', body: `<p>禁用 ${e(p?.name || '')} / ${e(p?.color || '')} 与本素材的使用关联。</p><div class="cat-note warning">相关主图停止用于新提案，AI 图片需重新复核。素材和历史报价仍保留，可通过重新确认关联恢复使用。</div>`, footer: '<button class="btn secondary" id="med-disable-back">返回</button><button class="btn primary" id="med-disable-confirm">确认禁用</button>', bind: () => { $('med-disable-back').onclick = () => showMedia(id); $('med-disable-confirm').onclick = invoke(async () => { guard(); await F.commit('禁用商品图片关联', s => { const item = s.media.find(x => x.id === id), x = s.products.find(x => x.id === productId); const l = item.links.find(l => l.productId === productId); l.allowed = false; l.disabledAt = new Date().toISOString(); if (x.mainMediaId === id || x.image === item.url) { x.imageAllowed = false; x.imageVersion++; F.invalidateImages([productId], '商品原图已禁用，需重新选择可用素材', s); } }); F.closeModal(); F.render(); F.notify('已禁用此商品的素材使用关联'); }); } }); }));
    } });
  }
  F.pages.media = renderMedia;
  F.catalog.ingestFiles = ingestFiles;
  F.catalog.fileSuggestions = fileSuggestions;
})();
