/* 本地结构验证：独立四档 / 长名称 / 多尺码样本，不证明桌面 Office 排版。 */
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm'), assert = require('node:assert/strict');
const deps = 'C:/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
const PptxGenJS = require(path.join(deps, 'pptxgenjs'));
const JSZip = require(path.join(deps, 'jszip'));
const sharp = require(path.join(deps, 'sharp'));
const xml = require(path.join(deps, 'xml-js'));
const state = { role: 'sales', snapshots: [], products: [], priceVersion: 2, stockVersion: 3 };
const F = { state, pages: {}, requireRole: roles => assert(roles.includes(state.role)), esc: x => String(x), uid: prefix => `${prefix}-test`, commit: (_action, mutate) => mutate(state) };
const context = { F, console, URL, Date, setTimeout, clearTimeout }; context.globalThis = context;
vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'delivery.js'), 'utf8'), context);
async function run() {
  const png = await sharp({ create: { width: 600, height: 300, channels: 3, background: '#c7d4bc' } }).png().toBuffer();
  const image = `data:image/png;base64,${png.toString('base64')}`;
  const s = { id: 'QUOTE-STRUCTURE-TEST', revision: 2, createdAt: '2026-09-11T08:00:00.000Z', validUntil: '2026-09-18T08:00:00.000Z', stockAsOf: '2026-09-11T07:45:00.000Z', priceVersion: 2, stockVersion: 3, clientName: '结构测试团购客户', title: '四档服饰搭配与多尺码报价验证', mode: 'alternatives', prototype: true, cost: 'INTERNAL_COST_SENTINEL', internalNote: 'INTERNAL_NOTE_SENTINEL', brief: { scene: '秋季团购', style: '通勤', season: '秋季', budget: 30000, text: '按真实尺码数量报价。' }, quote: { note: '现货以确认时库存为准', internalNote: 'INTERNAL_NOTE_SENTINEL' }, template: { brand: '织选', color: '#355B49', contact: '演示顾问', phone: '400-000-0000', version: 2, logo: image }, groups: [] };
  const categories = ['上衣', '裤子', '帽子', '鞋'], prices = [8000, 6000, 2000, 9000];
  const longName = '超长商品名称验证：秋季团购商务休闲纯棉可水洗长袖基础服饰含完整版型材质与洗护描述'.repeat(5);
  for (let n = 1; n <= 4; n++) {
    const lines = [];
    for (let i = 0; i < n; i++) {
      const count = i === 0 && n === 4 ? 20 : 2;
      for (let j = 0; j < count; j++) lines.push({ productId: `p${i}`, sku: `STYLE-${n}-${i}-${j}`, name: n === 4 && i === 0 ? longName : `演示${categories[i]}`, color: '苔绿', size: `尺码${j + 1}`, category: categories[i], unit: i === 3 ? '双' : '件', qty: 100 / count, price: prices[i], amount: prices[i] * 100 / count, image, crop: { x: 0, y: 0, w: 1, h: 1 }, imageVersion: 1, cost: 'INTERNAL_COST_SENTINEL' });
    }
    const subtotal = prices.slice(0, n).reduce((a, b) => a + b, 0) * 100, discount = subtotal * .05, fee = 30000, total = subtotal - discount + fee;
    s.groups.push({ id: `g${n}`, name: `${n} 品类独立备选`, group: n, qty: 100, reason: '同色系搭配，完整保留客户要求与单品资料。', lines, subtotal, discount, fee, total, perSet: Math.round(total / 100), modelImage: n > 2 ? image : null, imageLabel: 'AI 搭配效果示意 · 原型样例' });
  }
  state.snapshots.push(s);
  state.products = categories.map((category, i) => ({ id: `p${i}`, imageAllowed: true, imageVersion: 1 }));
  assert.doesNotThrow(() => F.delivery.checkExport(s));
  assert.throws(() => F.delivery.checkExport({ ...s, revision: 99 }), /完整版本/);
  state.products[0].imageAllowed = false;
  assert.throws(() => F.delivery.checkExport(s), /禁用/);
  state.products[0].imageAllowed = true;
  state.media = [{ url: image, links: [{ productId: 'p0', allowed: false }] }];
  assert.throws(() => F.delivery.checkExport(s), /历史版本使用的素材已禁用/, '更换新主图后，已撤回的历史原图仍应阻断');
  state.media = [];
  const generated = await F.delivery.buildPptx(s, { PptxGenJS, imageResolver: async () => ({ data: image, width: 600, height: 300 }) });
  const buffer = await generated.pptx.write({ outputType: 'nodebuffer', compression: true });
  const zip = await JSZip.loadAsync(buffer);
  const slideNames = Object.keys(zip.files).filter(x => /^ppt\/slides\/slide\d+\.xml$/.test(x));
  assert.equal(slideNames.length, generated.pages.length);
  const docs = await Promise.all(Object.keys(zip.files).filter(x => /\.xml$/.test(x)).map(async file => ({ file, text: await zip.file(file).async('string') })));
  docs.forEach(d => assert.doesNotThrow(() => xml.xml2js(d.text, { compact: true }), d.file));
  const body = docs.map(d => d.text).join('\n');
  assert(!body.includes('INTERNAL_COST_SENTINEL')); assert(!body.includes('INTERNAL_NOTE_SENTINEL'));
  assert(body.includes('<a:tbl>'), '报价表必须为可编辑 OOXML table');
  assert(body.includes('<a:t>'), '必须含可编辑文本');
  assert(body.includes('24,050.00'), '四品类报价必须等于 24,050.00');
  assert(!body.includes('合并采购应付'), '独立备选不能出现合并应付');
  assert(Object.keys(zip.files).some(x => /^ppt\/media\/image[-\d]+\.png$/.test(x)), 'PNG 必须嵌入');
  const extracted = docs.filter(x => /^ppt\/slides\/slide\d+\.xml$/.test(x.file)).sort((a, b) => Number(a.file.match(/slide(\d+)/)[1]) - Number(b.file.match(/slide(\d+)/)[1])).map(d => xml.xml2js(d.text, { compact: true }));
  const findNamed = (node, name, out = []) => { if (Array.isArray(node)) node.forEach(n => findNamed(n, name, out)); else if (node && typeof node === 'object') for (const [k, v] of Object.entries(node)) { if (k === name) out.push(...[].concat(v)); else findNamed(v, name, out); } return out; };
  for (const doc of extracted) {
    for (const picture of findNamed(doc, 'p:pic')) {
      const transform = picture['p:spPr']['a:xfrm'], width = Number(transform['a:ext']._attributes.cx), height = Number(transform['a:ext']._attributes.cy);
      assert(Math.abs(width / height - 2) < .00001, 'PPT 图片对象必须保持独立素材的 2:1 宽高比');
    }
    for (const frame of findNamed(doc, 'p:graphicFrame')) {
      const transform = frame['p:xfrm'], position = transform['a:off']._attributes, extent = transform['a:ext']._attributes;
      assert(Number(position.x) + Number(extent.cx) <= 13.334 * 914400 && Number(position.y) + Number(extent.cy) <= 7.501 * 914400, '实际 OOXML 表格边界不得超出版面');
    }
  }
  const extractText = (node, out = []) => { if (Array.isArray(node)) node.forEach(n => extractText(n, out)); else if (node && typeof node === 'object') for (const [k, v] of Object.entries(node)) { if (k === 'a:t') { for (const item of [].concat(v)) out.push(item._text || ''); } else extractText(v, out); } return out; };
  const allText = extracted.flatMap(x => extractText(x)).join('');
  for (const group of s.groups) for (const line of group.lines) assert(allText.includes(line.sku), `缺少 ${line.sku}`);
  const findRows = (node, out = []) => { if (Array.isArray(node)) node.forEach(n => findRows(n, out)); else if (node && typeof node === 'object') for (const [k, v] of Object.entries(node)) { if (k === 'a:tr') out.push(...[].concat(v)); else findRows(v, out); } return out; };
  const nameFragments = extracted.flatMap((doc, i) => generated.pages[i].kind === 'details' ? findRows(doc).map(row => extractText([].concat(row['a:tc'])[1]).join('').replace(/[\r\n]/g, '')).filter(value => value !== '商品名称') : []).join('');
  assert(nameFragments.includes(longName), '长商品名必须可从报价表名称列续页文本完整还原');
  assert(generated.pages.filter(x => x.kind === 'details').length > s.groups.length, '长名称、多尺码必须续页');
  for (const p of generated.pages) for (const e of p.elements) { assert(e.x >= 0 && e.y >= 0 && e.x + e.w <= 13.334 && e.y + e.h <= 7.501, `越界 ${p.title}`); }
  const csv = F.delivery.csv(s);
  assert(!csv.includes('INTERNAL_')); assert(!csv.includes('合并采购应付'));
  const formula = structuredClone(s); formula.clientName = '=HYPERLINK("evil")';
  assert(F.delivery.csv(formula).includes("'=HYPERLINK"), 'CSV 必须阻止公式注入');
  state.priceVersion = 100;
  assert(F.delivery.assetStatus(s).warnings.some(x => x.includes('新版本')));
  assert.equal(F.delivery.csv(s), csv, '最新价格变化不能改变历史 CSV');
  const combined = structuredClone(s); combined.mode = 'combined';
  const components = combined.groups.map(g => ({ id: g.id, name: g.name, group: g.group, qty: g.qty, reason: g.reason, lines: g.lines, modelImage: g.modelImage, imageLabel: g.imageLabel }));
  const combinedLines = components.flatMap(g => g.lines), subtotal = combinedLines.reduce((n, l) => n + l.amount, 0), discount = subtotal * .05, fee = 30000;
  combined.groups = [{ id: 'combined', group: 4, name: '合并采购', qty: 400, lines: combinedLines, subtotal, discount, fee, tax: 0, total: subtotal - discount + fee, perSet: 0, components }];
  assert(F.delivery.pages(combined).some(x => x.kind === 'combined'));
  assert.equal(F.delivery.pages(combined).filter(x => /^look-[1-4]$/.test(x.kind)).length, 4, '合并采购仍保留每个组合的图片布局');
  assert.equal(F.delivery.pages(combined).filter(x => x.kind === 'combined').flatMap(x => x.elements).filter(x => x.type === 'tableRow' && x.cells.some(c => c.startsWith('附加费用 / 运费'))).length, 1, '合并采购费用只能显示一次');
  assert(F.delivery.csv(combined).includes('合并采购应付'));
  assert.equal((F.delivery.csv(combined).match(/合并采购应付/g) || []).length, 1);
  const taxQuote = structuredClone(combined); taxQuote.quote.taxMode = 'excluded'; taxQuote.quote.taxRate = 13; taxQuote.groups[0].tax = Math.round(taxQuote.groups[0].total * .13); taxQuote.groups[0].total += taxQuote.groups[0].tax;
  assert(F.delivery.csv(taxQuote).includes('加收税额（13%）'));
  assert(F.delivery.pages(taxQuote).some(p => p.elements.some(e => e.type === 'tableRow' && e.cells.includes('加收税额（13%）'))));
  taxQuote.quote.feeTaxable = false;
  assert(F.delivery.csv(taxQuote).includes('附加费用已含税 / 不再加税'));
  const expired = structuredClone(s); expired.id = 'QUOTE-EXPIRED'; expired.validUntil = new Date(Date.now() - 1000).toISOString(); state.snapshots.push(expired);
  assert.throws(() => F.delivery.checkExport(expired), /已过期/);
  assert.doesNotThrow(() => F.delivery.checkExport(expired, { internal: true }));
  assert(F.delivery.csv(expired).includes('已过期，仅供内部核对'));
  assert(F.delivery.pages(expired).every(p => p.elements.some(e => e.text === '已过期 · 仅供内部核对')));
  const frozen = JSON.stringify(s);
  assert.throws(() => F.delivery.voidSnapshot(s, ' '), /作废原因/);
  F.delivery.voidSnapshot(s, '客户取消本次采购');
  assert.throws(() => F.delivery.checkExport(s), /报价已作废/);
  assert.throws(() => F.delivery.voidSnapshot(s, '尝试重新作废'), /已经作废/);
  assert.equal(JSON.stringify(s), frozen, '作废事件不能修改不可变快照内容'); state.snapshotLifecycle = [];
  const destination = path.join(__dirname, 'evidence'); fs.mkdirSync(destination, { recursive: true });
  fs.writeFileSync(path.join(destination, 'delivery-structure-sample.pptx'), buffer);
  const report = { checkedAt: new Date().toISOString(), library: 'PptxGenJS 4.0.1', slideCount: slideNames.length, editableTables: (body.match(/<a:tbl>/g) || []).length, mediaCount: Object.keys(zip.files).filter(x => /^ppt\/media\/.+\.png$/.test(x)).length, requirements: ['1–4 independent alternative layouts', '24,050.00 four-category quote', 'all size SKU text present', 'long names continued without dropping characters', 'editable text and tables', 'embedded PNG with source aspect ratio', 'actual OOXML table coordinates stay within slide', 'no internal sentinel in OOXML', 'no implicit sum in alternatives', 'CSV formula escaping', 'historical exports fixed after price update', 'revoked-image export blocked', 'unknown snapshot rejected', 'combined components retain all individual visual layouts', 'combined fee and payment occur once', 'explicit excluded-tax rows'], limitation: 'Independent fixture validates generated OOXML and pure export logic. Does not prove core quote checks, browser download flow, or PowerPoint/WPS native rendering.' };
  fs.writeFileSync(path.join(destination, 'delivery-structure-check.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report, null, 2));
}
run().catch(e => { console.error(e); process.exitCode = 1; });
