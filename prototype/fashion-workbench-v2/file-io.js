/* 商品文件边界：纯规则可在 Node 校验，浏览器部分只解析用户选中的本地文件。 */
(function (root) {
  'use strict';
  const API = {};
  const CATEGORIES = ['上衣', '裤子', '帽子', '鞋'];
  const FIELDS = {
    source: ['来源', '数据来源', '供应商', 'source'], sku: ['SKU', 'sku', '内部SKU'],
    style: ['款号', '内部款号', 'style'], name: ['商品名', '商品名称', 'name'],
    category: ['品类', '一级品类', 'category'], color: ['颜色', 'color'], size: ['尺码', 'size'],
    unit: ['单位', '计价单位', 'unit'], warehouse: ['仓库', 'warehouse'], price: ['销售价', '价格', 'price'],
    stock: ['可售库存', '库存', '可售数量', 'stock'], currency: ['币种', 'currency'],
    tax: ['含税口径', '税费口径', 'tax'], priceTable: ['价格表', 'priceTable'],
    material: ['材质', 'material'], season: ['季节', 'season'], tags: ['标签', 'tags'],
    jdId: ['京东ID', '京东商品ID', 'jdId'], jdUrl: ['京东链接', 'jdUrl'],
    filename: ['文件名', 'filename'], usage: ['图片用途', '用途', 'usage']
  };
  const clean = v => String(v == null ? '' : v).trim();
  const clone = v => JSON.parse(JSON.stringify(v));
  const key = (source, sku) => JSON.stringify([source, sku]);
  function cents(value) {
    const t = clean(value);
    if (!/^(0|[1-9]\d{0,8})(\.\d{1,2})?$/.test(t)) throw new Error('销售价须为非负金额，最多两位小数');
    const [a, b = ''] = t.split('.');
    return Number(a) * 100 + Number(b.padEnd(2, '0'));
  }
  function integer(value) {
    const t = clean(value);
    if (!/^(0|[1-9]\d{0,8})$/.test(t)) throw new Error('可售库存须为 0–999999999 的整数，空白不代表 0');
    return Number(t);
  }
  function parseCSV(text) {
    text = String(text).replace(/^\uFEFF/, '');
    const rows = []; let row = [], cell = '', quoted = false, closed = false;
    for (let i = 0; i < text.length; i++) {
      const c = text[i];
      if (quoted) {
        if (c === '"' && text[i + 1] === '"') { cell += '"'; i++; }
        else if (c === '"') { quoted = false; closed = true; }
        else cell += c;
      } else if (c === '"') {
        if (cell || closed) throw new Error(`CSV 第 ${rows.length + 1} 行引号位置错误`);
        quoted = true;
      } else if (c === ',' || c === '\n' || c === '\r') {
        row.push(cell); cell = ''; closed = false;
        if (c !== ',') { if (c === '\r' && text[i + 1] === '\n') i++; rows.push(row); row = []; }
      } else {
        if (closed) throw new Error(`CSV 第 ${rows.length + 1} 行引号后存在多余字符`);
        cell += c;
      }
    }
    if (quoted) throw new Error('CSV 引号未闭合，请重新保存文件');
    if (row.length || cell || closed) { row.push(cell); rows.push(row); }
    return rows.filter(r => r.some(v => clean(v)));
  }
  function csv(rows) {
    return '\uFEFF' + rows.map(row => row.map(value => {
      let t = String(value == null ? '' : value);
      if (/^[=+@\-\t\r]/.test(t)) t = "'" + t;
      return '"' + t.replace(/"/g, '""') + '"';
    }).join(',')).join('\r\n');
  }
  function autoMapping(headers) {
    return Object.fromEntries(Object.entries(FIELDS).map(([field, aliases]) => [field, headers.findIndex(h => aliases.some(a => a.toLowerCase() === clean(h).toLowerCase()))]));
  }
  function records(rows, mapping) {
    const headers = rows[0] || [];
    if (!headers.length) throw new Error('文件中没有表头');
    if (rows.length > 10001) throw new Error('本地原型每批最多 10000 行，请拆分或使用生产服务');
    return rows.slice(1).map((r, index) => {
      const out = { _row: index + 2 };
      for (const [field, col] of Object.entries(mapping)) if (col >= 0) out[field] = clean(r[col]);
      if (r.length > headers.length && r.slice(headers.length).some(clean)) out._error = '行列数超过表头，请检查 CSV 分隔符';
      if (Object.values(out).some(v => typeof v === 'string' && /^=/.test(v))) out._error = '禁止公式；请复制为值后重新上传';
      return out;
    }).filter(r => Object.entries(r).some(([k, v]) => k !== '_row' && v));
  }
  function skuRows(state, all = false) {
    return state.products.filter(p => all || p.status === 'active').flatMap(p => p.sizes.map(s => ({ p, s, key: key(p.source, s.sku) })));
  }
  function scopeRows(state, scope) {
    return skuRows(state, true).filter(({ p }) => (p.status === 'active' || (scope.includeDrafts && p.status === 'draft')) && p.source === scope.source && p.warehouse === scope.warehouse && (!scope.category || p.category === scope.category));
  }
  function otherTable(kind, scope) { return kind === 'price' && scope?.priceTable && scope.priceTable !== '批发价'; }
  function taxModeOf(row, scope) { return otherTable('price', scope) ? row.s.priceTableTaxModes?.[scope.priceTable] || 'included' : row.s.priceTaxMode || 'included'; }
  function valueOf(row, kind, scope) { return otherTable(kind, scope) ? row.s.priceTables?.[scope.priceTable] ?? null : Object.prototype.hasOwnProperty.call(row.s, kind) ? row.s[kind] : row.p[kind] ?? null; }
  function setValue(row, kind, scope, value, asOf, taxMode = scope?.taxMode || 'included') {
    if (otherTable(kind, scope)) { row.s.priceTables ||= {}; row.s.priceTableAsOf ||= {}; row.s.priceTables[scope.priceTable] = value; row.s.priceTableAsOf[scope.priceTable] = asOf; }
    else { row.s[kind] = value; row.s[kind + 'AsOf'] = asOf; }
    if (kind === 'price') { if (otherTable(kind, scope)) { row.s.priceTableTaxModes ||= {}; row.s.priceTableTaxModes[scope.priceTable] = taxMode; } else row.s.priceTaxMode = taxMode; }
  }
  function dataTime(state, row, kind, scope) { return otherTable(kind, scope) ? row.s.priceTableAsOf?.[scope.priceTable] || null : row.s[kind + 'AsOf'] || row.p[kind + 'AsOf'] || state[kind + 'AsOf']; }
  function signature(state, scope, kind) {
    return JSON.stringify(scopeRows(state, scope).map(r => [r.key, r.p.id, r.s.size, r.p.unit, valueOf(r, kind, scope), dataTime(state, r, kind, scope), kind === 'price' ? taxModeOf(r, scope) : null]).sort((a, b) => a[0].localeCompare(b[0])));
  }
  function validJD(id, url) {
    if (id && !/^\d{5,20}$/.test(id)) throw new Error('京东商品 ID 须为 5–20 位数字');
    if (url) {
      let u; try { u = new URL(url); } catch (_) { throw new Error('京东链接格式错误'); }
      if (u.protocol !== 'https:' || !/(^|\.)jd\.com$/.test(u.hostname)) throw new Error('京东链接须为 https:// 下的 jd.com 域名');
      const m = u.pathname.match(/\/(\d+)\.html/);
      if (id && m && id !== m[1]) throw new Error('京东链接与商品 ID 不一致');
    }
  }
  function validate(state, kind, input, scope, asOf) {
    const errors = [], warnings = [], changes = [], seen = new Set();
    const add = (row, message, sku = '') => errors.push({ row, sku, message });
    const all = new Map(skuRows(state, true).map(r => [r.key, r]));
    const target = kind === 'product' ? [] : scopeRows(state, scope);
    const expected = new Set(target.map(r => r.key));
    if (!input.length) add(0, '文件没有有效数据行');
    if (!['product', 'price', 'stock'].includes(kind)) add(0, '未知导入类型');
    if (kind !== 'product') {
      if (!scope.source || !scope.warehouse) add(0, '请选择来源和仓库以确定全量范围');
      if (!target.length) add(0, '范围内没有在售 SKU，请先完善商品资料并上架');
      const time = Date.parse(asOf);
      if (!Number.isFinite(time)) add(0, '请填写有效业务时间');
      else if (time > Date.now() + 300000) add(0, '业务时间不能晚于当前时间 5 分钟');
      else if (target.some(r => Number.isFinite(Date.parse(dataTime(state, r, kind, scope))) && Date.parse(dataTime(state, r, kind, scope)) > time)) add(0, '旧业务时间不能覆盖范围内较新的有效数据；历史恢复请使用版本记录');
    }
    const groups = new Map();
    input.forEach(r => {
      const beforeErrors = errors.length;
      if (r._error) add(r._row, r._error, r.sku);
      if (!r.source || !r.sku) { add(r._row, '来源和 SKU 必填', r.sku); return; }
      if (r.sku.length > 80 || r.source.length > 80) add(r._row, '来源或 SKU 超过 80 字符', r.sku);
      const k = key(r.source, r.sku), existing = all.get(k);
      if (seen.has(k)) add(r._row, '文件内来源 + SKU 重复', r.sku);
      seen.add(k);
      if (kind === 'product') {
        ['style', 'name', 'category', 'color', 'size', 'unit', 'warehouse'].forEach(f => { if (!r[f] && !existing) add(r._row, `${FIELDS[f][0]}为新商品必填字段`, r.sku); });
        if (r.category && !CATEGORIES.includes(r.category)) add(r._row, '品类只能为上衣、裤子、帽子、鞋', r.sku);
        if (r.name && r.name.length > 120) add(r._row, '商品名不能超过 120 字符', r.sku);
        if (existing && r.size && r.size !== existing.s.size) add(r._row, '已有 SKU 的尺码不能改名，请新增 SKU', r.sku);
        if (existing && ((r.style && r.style !== existing.p.style) || (r.color && r.color !== existing.p.color))) add(r._row, '已有 SKU 的款号和颜色不能改为另一个颜色款', r.sku);
        try { validJD(r.jdId || '', r.jdUrl || ''); } catch (e) { add(r._row, e.message, r.sku); }
        if (r.price || r.stock) warnings.push({ row: r._row, message: `${r.sku} 的价格和库存列不会写入，请使用独立全量更新` });
        const groupKey = JSON.stringify([r.source, r.style || existing?.p.style, r.color || existing?.p.color]);
        const group = groups.get(groupKey) || { sizes: new Map() };
        for (const f of ['name', 'category', 'unit', 'warehouse', 'material', 'season', 'tags', 'jdId', 'jdUrl']) {
          if (r[f] && group[f] && r[f] !== group[f]) add(r._row, `同一颜色款的${FIELDS[f][0]}在多行中冲突`, r.sku);
          if (r[f]) group[f] = r[f];
        }
        if (group.sizes.has(r.size) && group.sizes.get(r.size) !== r.sku) add(r._row, '同一颜色款的尺码对应多个 SKU', r.sku);
        group.sizes.set(r.size, r.sku); groups.set(groupKey, group);
        const sibling = state.products.find(p => p.source === r.source && p.style === r.style && p.color === r.color);
        if (!existing && sibling?.sizes.some(s => s.size === r.size)) add(r._row, '该颜色款已有此尺码，请使用原 SKU', r.sku);
        if (errors.length === beforeErrors) changes.push({ key: k, row: clone(r), before: existing ? clone(existing.p) : null, action: existing ? '更新资料' : '新增 SKU' });
      } else {
        if (!existing) { add(r._row, '未知 SKU，先导入商品资料；本批不会创建商品', r.sku); return; }
        if (!expected.has(k)) add(r._row, 'SKU 不在本次来源 / 仓库 / 品类全量范围', r.sku);
        if (r.warehouse !== scope.warehouse) add(r._row, '仓库与更新范围不一致', r.sku);
        if (r.unit !== existing.p.unit) add(r._row, '计价单位与商品资料不一致', r.sku);
        let value;
        try { value = kind === 'price' ? cents(r.price) : integer(r.stock); } catch (e) { add(r._row, e.message, r.sku); }
        if (kind === 'price') {
          if (value === 0) add(r._row, '首期正式销售不允许零价', r.sku);
          if (!['CNY', '人民币'].includes(r.currency)) add(r._row, '币种须为 CNY 或人民币', r.sku);
          if (r.tax !== (scope.taxMode === 'excluded' ? '未税' : '含税')) add(r._row, '价格税费口径与所选批次范围不一致', r.sku);
          if (r.priceTable !== (scope.priceTable || '批发价')) add(r._row, '价格表与所选全量范围不一致', r.sku);
        }
        if (errors.length === beforeErrors) changes.push({ key: k, productId: existing.p.id, sku: r.sku, size: existing.s.size, name: existing.p.name, before: valueOf(existing, kind, scope), after: value });
      }
    });
    if (kind !== 'product') for (const r of target) if (!seen.has(r.key)) add(0, '全量缺少 SKU，缺行不会保留或归零', r.s.sku);
    const covered = [...expected].filter(k => seen.has(k)).length;
    return { kind, input: clone(input), scope: clone(scope), asOf, errors, warnings, changes, expected: expected.size, covered,
      coverage: expected.size ? Math.round(covered / expected.size * 10000) / 100 : 0,
      baseline: kind === 'product' ? state.productVersion : signature(state, scope, kind),
      productVersion: state.productVersion, valid: !errors.length, status: errors.length ? 'invalid' : 'ready' };
  }
  function apply(state, batch) {
    if (!batch.valid || batch.status !== 'ready') throw new Error('只有完整校验通过的批次可以生效');
    if (state.batches.some(b => b.id === batch.id && b.status === 'applied')) throw new Error('此批次已经生效，请勿重复发布');
    if (batch.productVersion !== state.productVersion || (batch.kind !== 'product' && batch.baseline !== signature(state, batch.scope, batch.kind))) throw new Error('校验后商品或同范围数据已变化，请重新校验');
    const checked = validate(state, batch.kind, batch.input, batch.scope, batch.asOf);
    if (!checked.valid) throw new Error(checked.errors[0].message);
    if (batch.kind === 'product') {
      for (const c of checked.changes) {
        const r = c.row;
        let p = state.products.find(p => p.source === r.source && p.style === (r.style || c.before?.style) && p.color === (r.color || c.before?.color));
        if (!p) { p = { id: 'product-' + batch.id + '-' + state.products.length, sku: r.style + '-' + r.color, style: r.style, source: r.source, color: r.color, name: r.name, category: r.category, unit: r.unit, warehouse: r.warehouse, price: null, sizes: [], image: '', crop: null, imageAllowed: false, imageVersion: 1, confirmed: false, status: 'draft', tags: [], season: '四季', material: '' }; state.products.push(p); }
        for (const f of ['name', 'category', 'unit', 'warehouse', 'material', 'season', 'jdId', 'jdUrl']) if (r[f]) p[f] = r[f];
        if (r.tags) p.tags = [...new Set(r.tags.split(/[，,、;]/).map(clean).filter(Boolean))];
        if (!p.sizes.some(s => s.sku === r.sku)) p.sizes.push({ sku: r.sku, size: r.size, stock: null, price: null });
      }
      state.productVersion++;
    } else {
      const kind = batch.kind;
      state.dataVersions ||= [];
      const before = scopeRows(state, batch.scope).map(r => ({ productId: r.p.id, sku: r.s.sku, value: valueOf(r, kind, batch.scope), asOf: dataTime(state, r, kind, batch.scope), taxMode: kind === 'price' ? taxModeOf(r, batch.scope) : null }));
      for (const c of checked.changes) {
        const p = state.products.find(p => p.id === c.productId), s = p.sizes.find(s => s.sku === c.sku);
        setValue({ p, s }, kind, batch.scope, c.after, batch.asOf);
      }
      state[kind + 'Version']++;
      // 全局时间取在售 SKU 最早有效时间，局部更新不能伪装为全部库存已刷新。
      const times = skuRows(state).map(r => dataTime(state, r, kind)).filter(t => Number.isFinite(Date.parse(t))).sort((a, b) => Date.parse(a) - Date.parse(b));
      if (times.length) state[kind + 'AsOf'] = times[0];
      state.dataVersions.unshift({ id: batch.id, kind, scope: clone(batch.scope), before, after: checked.changes.map(c => ({ productId: c.productId, sku: c.sku, value: c.after, asOf: batch.asOf, taxMode: batch.scope.taxMode || 'included' })), asOf: batch.asOf, version: state[kind + 'Version'], createdAt: new Date().toISOString() });
    }
    const saved = { ...clone(batch), status: 'applied', appliedAt: new Date().toISOString() };
    const index = state.batches.findIndex(b => b.id === batch.id);
    if (index >= 0) state.batches[index] = saved; else state.batches.unshift(saved);
    return checked.changes.length;
  }
  function restore(state, id) {
    const version = state.dataVersions?.find(v => v.id === id);
    if (!version) throw new Error('历史版本不存在');
    const kind = version.kind, current = scopeRows(state, version.scope);
    const keys = current.map(r => r.p.id + '\0' + r.s.sku).sort();
    const oldKeys = version.before.map(r => r.productId + '\0' + r.sku).sort();
    if (JSON.stringify(keys) !== JSON.stringify(oldKeys)) throw new Error('范围内 SKU 已增减，请通过新的全量文件恢复；不能部分回退');
    const before = current.map(r => ({ productId: r.p.id, sku: r.s.sku, value: valueOf(r, kind, version.scope), asOf: dataTime(state, r, kind, version.scope), taxMode: kind === 'price' ? taxModeOf(r, version.scope) : null }));
    version.before.forEach(r => { const p = state.products.find(p => p.id === r.productId), s = p.sizes.find(s => s.sku === r.sku); setValue({ p, s }, kind, version.scope, r.value, r.asOf, r.taxMode || 'included'); });
    state[kind + 'Version']++;
    const times = skuRows(state).map(r => dataTime(state, r, kind)).filter(t => Number.isFinite(Date.parse(t))).sort((a, b) => Date.parse(a) - Date.parse(b));
    state[kind + 'AsOf'] = times[0] || state[kind + 'AsOf'];
    state.dataVersions.unshift({ id: 'restore-' + Date.now(), kind, scope: clone(version.scope), before, after: clone(version.before), version: state[kind + 'Version'], createdAt: new Date().toISOString(), restoredFrom: id, asOf: state[kind + 'AsOf'] });
  }
  const xml = text => { const doc = new DOMParser().parseFromString(text, 'application/xml'); if (doc.getElementsByTagName('parsererror').length) throw new Error('XLSX XML 格式损坏'); return doc; };
  const els = (node, name) => Array.from(node.getElementsByTagNameNS('*', name));
  function colIndex(ref) { let v = 0; for (const c of ref.toUpperCase().replace(/\d/g, '')) v = v * 26 + c.charCodeAt(0) - 64; return v - 1; }
  async function unzip(buffer, maxFiles = 200) {
    if (!root.JSZip) throw new Error('本地 ZIP 组件未加载，请重新打开原型');
    // 先读取中央目录并检查展开尺寸，避免校验 CRC 时在限额检查前解压整个包。
    const zip = await root.JSZip.loadAsync(buffer, { checkCRC32: false });
    const files = Object.values(zip.files).filter(f => !f.dir);
    if (files.length > maxFiles) throw new Error(`压缩包超过 ${maxFiles} 个文件限制`);
    let size = 0;
    for (const f of files) {
      const path = f.unsafeOriginalName || f.name;
      if (/(^|[\\/])\.\.([\\/]|$)|^[\\/]|^[A-Za-z]:/.test(path)) throw new Error('压缩包含越界路径，已拒绝整个文件');
      size += f._data?.uncompressedSize || 0;
      if (size > 40 * 1024 * 1024) throw new Error('解压后总内容超过 40 MB');
    }
    return zip;
  }
  async function readFile(file) {
    if (file.size > 12 * 1024 * 1024) throw new Error('表格文件超过本地原型 12 MB 限制');
    if (/\.csv$/i.test(file.name)) return [{ name: file.name, rows: parseCSV(await file.text()) }];
    if (!/\.xlsx$/i.test(file.name)) throw new Error('请选择 UTF-8 CSV 或 .xlsx；旧 .xls 请另存为 .xlsx');
    const zip = await unzip(await file.arrayBuffer(), 300);
    const workbook = zip.file('xl/workbook.xml'), relFile = zip.file('xl/_rels/workbook.xml.rels');
    if (!workbook || !relFile) throw new Error('文件不是有效的 XLSX 工作簿');
    const rels = new Map(els(xml(await relFile.async('string')), 'Relationship').map(e => [e.getAttribute('Id'), e.getAttribute('Target')]));
    const sharedFile = zip.file('xl/sharedStrings.xml');
    const shared = sharedFile ? els(xml(await sharedFile.async('string')), 'si').map(e => els(e, 't').map(t => t.textContent).join('')) : [];
    const output = [];
    for (const sheet of els(xml(await workbook.async('string')), 'sheet')) {
      const rid = sheet.getAttribute('r:id') || sheet.getAttributeNS('http://schemas.openxmlformats.org/officeDocument/2006/relationships', 'id');
      const target = rels.get(rid) || ''; const path = target.startsWith('/') ? target.slice(1) : 'xl/' + target;
      if (target.includes('..') || /https?:/i.test(target) || !zip.file(path)) throw new Error('工作表关联无效或包含外部路径');
      const doc = xml(await zip.file(path).async('string')), rows = [];
      for (const r of els(doc, 'row')) {
        const row = [];
        for (const c of els(r, 'c')) {
          if (els(c, 'f').length) throw new Error(`工作表 ${sheet.getAttribute('name')} 的 ${c.getAttribute('r')} 含公式，请复制为值`);
          const index = colIndex(c.getAttribute('r') || 'A1');
          if (index < 0 || index > 100) throw new Error('工作表超过 101 列，请仅保留导入字段');
          const type = c.getAttribute('t'), v = els(c, 'v')[0]?.textContent || '';
          row[index] = type === 's' ? shared[Number(v)] || '' : type === 'inlineStr' ? els(c, 't').map(t => t.textContent).join('') : v;
        }
        if (row.some(clean)) rows.push(Array.from(row, v => v || ''));
        if (rows.length > 10001) throw new Error('每个工作表最多支持 10000 行数据');
      }
      if (rows.length) output.push({ name: sheet.getAttribute('name'), rows });
    }
    if (!output.length) throw new Error('工作簿中没有可导入的数据');
    return output;
  }
  Object.assign(API, { CATEGORIES, FIELDS, clean, cents, integer, parseCSV, csv, autoMapping, records, skuRows, scopeRows, validate, apply, restore, validJD, signature, readFile, unzip });
  if (typeof module !== 'undefined' && module.exports) module.exports = API;
  root.FIO = API;
})(typeof window !== 'undefined' ? window : globalThis);
