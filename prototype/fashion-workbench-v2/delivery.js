/* 织选：报价快照预览与文件交付。金额和页面数据只读取已确认快照。 */
(function (global) {
  'use strict';
  const F = global.F;
  if (!F) throw new Error('delivery.js 需要先加载 core.js');
  const W = 13.333333, H = 7.5;
  const PALETTE = { green: '355B49', ink: '24352E', muted: '5D6E63', cream: 'F8F7F2', white: 'FFFFFF', line: 'DCE2D9', pale: 'EAF0E7', amber: '875B21' };
  const MODE = { alternatives: '备选报价 · 客户任选，各方案独立核算', combined: '合并采购 · 按选中组合累计采购' };
  const ui = { selected: null, projectId: null, page: 0, busy: false, token: 0, message: '', error: '' };
  const money = n => `¥${(Number(n || 0) / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  const str = x => String(x == null ? '' : x);
  const date = x => x ? new Date(x).toLocaleString('zh-CN', { hour12: false }) : '未记录';
  const mode = s => MODE[s.mode] || (s.mode === 'combined' ? MODE.combined : MODE.alternatives);
  const esc = x => F.esc(str(x));
  const safeName = x => str(x).replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_').slice(0, 90) || '报价方案';
  const safeColor = x => /^#?[\da-f]{6}$/i.test(str(x)) ? str(x).replace(/^#/, '').toUpperCase() : PALETTE.green;
  const taxLabel = s => s.quote?.taxMode === 'excluded' ? `未税销售价，${s.quote.feeTaxable === false ? '商品优惠后金额加税，附加费用已含税 / 不再加税' : '商品优惠后金额与应税附加费用合计加税'} ${s.quote.taxRate}%` : '含税销售价，不重复加税';
  const feeLabel = s => s.quote?.feeName || `附加费用 / 运费（${s.quote?.taxMode === 'excluded' && s.quote?.feeTaxable !== false ? '应税' : '已含税 / 不再加税'}）`;
  function validity(s) {
    const event = (F.state.snapshotLifecycle || []).find(x => x.snapshotId === s.id && x.status === 'voided');
    return { voided: !!event, event, expired: new Date(s.validUntil).getTime() < Date.now() };
  }
  const template = s => ({ brand: s.template?.brand || s.brand || '织选', color: safeColor(s.template?.color), logo: s.template?.logo || '', contact: s.template?.contact || s.contact || '', phone: s.template?.phone || '', version: s.template?.version || 1 });
  const chars = text => Array.from(str(text)).reduce((n, c) => n + (/[^\x00-\xff]/.test(c) ? 2 : 1), 0);
  function wrap(text, capacity) {
    const lines = [];
    for (const paragraph of str(text).split('\n')) {
      let line = '', width = 0;
      for (const c of Array.from(paragraph)) {
        const n = /[^\x00-\xff]/.test(c) ? 2 : 1;
        if (width + n > capacity && line) { lines.push(line); line = ''; width = 0; }
        line += c; width += n;
      }
      lines.push(line);
    }
    return lines;
  }
  function txt(p, text, x, y, w, h, size = 16, extra = {}) { p.elements.push({ type: 'text', text: str(text), x, y, w, h, size, color: PALETTE.ink, ...extra }); }
  function box(p, x, y, w, h, fill, extra = {}) { p.elements.push({ type: 'box', x, y, w, h, fill, ...extra }); }
  function picture(p, image, crop, x, y, w, h, alt) { if (image) p.elements.push({ type: 'image', image, crop, x, y, w, h, alt: str(alt) }); }
  function page(s, kind, title, subtitle = '') {
    const t = template(s), p = { kind, title, subtitle, background: PALETTE.cream, elements: [] };
    box(p, .44, .42, .055, .52, t.color);
    const headingSize = Math.min(24, Math.max(12, 1300 / Math.max(1, chars(title))));
    txt(p, title, .68, .39, 11.2, .64, headingSize, { bold: true });
    if (subtitle) txt(p, subtitle, .68, 1.0, 11.9, .42, 10.5, { color: PALETTE.muted });
    box(p, .65, 6.98, 12.03, .015, PALETTE.line);
    txt(p, `${t.brand}  /  ${s.id} · V${s.revision}`, .68, 7.1, 8.4, .18, 8.5, { color: PALETTE.muted });
    const v = validity(s);
    txt(p, v.voided ? '已作废 · 禁止对客交付' : v.expired ? '已过期 · 仅供内部核对' : '演示数据 · 供原型评审', 10.3, 7.1, 2.3, .18, 8.5, { align: 'right', color: v.voided || v.expired ? PALETTE.amber : PALETTE.muted });
    return p;
  }
  function uniqueProducts(g) {
    const seen = new Set();
    return g.lines.filter(l => { const key = l.productId || `${l.name}|${l.color}|${l.image}`; if (seen.has(key)) return false; seen.add(key); return true; });
  }
  function displayGroups(s) { return s.mode === 'combined' ? s.groups.flatMap(g => g.components?.every(c => Array.isArray(c.lines)) ? g.components : [g]) : s.groups; }
  function tablePages(s, title, subtitle, columns, records, kind = 'details') {
    const pages = [], totalWidth = columns.reduce((n, c) => n + c.w, 0), font = 10.5;
    let p, y;
    function next() {
      p = page(s, kind, `${title}${pages.length ? `（续 ${pages.length}）` : ''}`, subtitle); pages.push(p); y = 1.67;
      p.elements.push({ type: 'tableRow', cells: columns.map(c => c.label), widths: columns.map(c => c.w), x: .68, y, w: totalWidth, h: .45, size: font, header: true, fill: template(s).color }); y += .45;
    }
    next();
    records.forEach((record, ri) => {
      const allLines = columns.map((c, i) => wrap(record[i], Math.max(5, Math.floor((c.w - .14) * 72 / font * 1.7))));
      const pieces = Math.max(...allLines.map(a => Math.ceil(a.length / 4)), 1);
      for (let part = 0; part < pieces; part++) {
        const cells = allLines.map((a, i) => a.slice(part * 4, part * 4 + 4).join('\n') || (part && i === 0 ? `↳ ${ri + 1} 续` : ''));
        const lines = Math.max(...cells.map(c => c.split('\n').length), 1);
        const h = Math.max(.43, lines * font * 1.35 / 72 + .16);
        if (y + h > 6.64) next();
        p.elements.push({ type: 'tableRow', cells, widths: columns.map(c => c.w), x: .68, y, w: totalWidth, h, size: font, stripe: ri % 2 === 1 }); y += h;
      }
    });
    return pages;
  }
  function paragraphPages(s, title, subtitle, paragraphs, kind) {
    const result = [];
    const lines = paragraphs.flatMap(x => [...wrap(x, 100), '']);
    for (let i = 0; i < lines.length || i === 0; i += 17) {
      const p = page(s, kind, title + (i ? '（续页）' : ''), subtitle);
      txt(p, lines.slice(i, i + 17).join('\n'), .85, 1.68, 11.65, 4.9, 15, { lineSpacing: 1.45 }); result.push(p);
    }
    return result;
  }
  function buildPages(snapshot) {
    const s = snapshot, t = template(s), pages = [];
    if (!Array.isArray(s.groups) || !s.groups.length) throw new Error('该报价版本没有可交付组合，请回报价确认创建完整版本。');
    const shown = displayGroups(s), combined = s.mode === 'combined';
    const cover = page(s, 'cover', t.brand, 'FASHION SELECTION & QUOTATION');
    box(cover, .68, 1.65, 7.35, 4.85, t.color);
    txt(cover, '为每一次采购，\n搭配合适的选择。', 1.04, 2.06, 6.6, 1.35, 30, { color: PALETTE.white, bold: true });
    txt(cover, s.title, 1.04, 3.63, 6.45, 1.2, Math.min(21, Math.max(12, 1300 / Math.max(1, chars(s.title)))), { color: PALETTE.white, bold: true });
    txt(cover, `${s.clientName}\n${mode(s)}\n生成日期 ${date(s.createdAt)}`, 1.04, 5.02, 6.4, 1.05, 13, { color: PALETTE.white });
    const hero = shown.find(g => g.modelImage)?.modelImage || shown[0].lines[0]?.image;
    const crop = shown.some(g => g.modelImage) ? null : shown[0].lines[0]?.crop;
    box(cover, 8.28, 1.65, 4.35, 4.85, PALETTE.pale);
    picture(cover, hero, crop, 8.55, 1.9, 3.82, 3.95, '报价商品示意');
    txt(cover, '商品与图片均为原型演示素材', 8.52, 6.05, 3.85, .2, 10, { color: PALETTE.muted, align: 'center' });
    if (t.logo) picture(cover, t.logo, null, 11.2, .4, 1.4, .55, '企业 Logo');
    pages.push(cover);
    const b = s.brief || {};
    pages.push(...paragraphPages(s, '需求与报价口径', `${s.clientName} · 报价版本 V${s.revision}`, [
      `方案主题：${s.title}`, `采购场景：${b.scene || '按客户已确认需求'}；风格：${b.style || '未指定'}；季节：${b.season || '未指定'}`,
      `品类档位：${[...new Set(shown.map(g => g.group))].join(' / ')} 品类；本次 ${shown.length} 个${combined ? '采购组合' : '候选'}。各组合采购量见比较页。`,
      `采购总预算：${money(b.budget)}（含附加费用及适用税额，最终按对应报价模式判断）。`,
      `报价模式：${mode(s)}。${s.mode === 'combined' ? '合并采购按已选组合及其采购量汇总。' : '所有候选是不同选择，各候选金额与库存分别核算，不累计成客户应付金额。'}`,
      `币种：人民币 CNY。税费口径：${taxLabel(s)}。`,
      b.text ? `需求补充：${b.text}` : '', b.exclude ? `排除项：${b.exclude}` : ''
    ].filter(Boolean), 'brief'));
    const compareRows = shown.map(g => { const subtotal = g.lines.reduce((n, l) => n + l.amount, 0); return [g.name, `${g.group} 品类`, uniqueProducts(g).map(l => l.category).join(' + '), `${g.qty} 套`, money(combined ? Math.round(subtotal / g.qty) : g.perSet), money(combined ? subtotal : g.total)]; });
    pages.push(...tablePages(s, combined ? '采购组合一览' : '方案比较', combined ? '下表为各组合商品金额；优惠、费用及税额仅在合并账单中计算一次。' : mode(s), [{ label: combined ? '采购组合' : '候选方案', w: 2.7 }, { label: '档位', w: 1.05 }, { label: '组成', w: 3.15 }, { label: '采购量', w: 1.2 }, { label: combined ? '每套商品均价' : '平均每套', w: 1.95 }, { label: combined ? '商品合计' : '对应应付', w: 1.95 }], compareRows, 'comparison'));
    shown.forEach((g, gi) => {
      const p = page(s, `look-${g.group}`, g.name, `${g.group} 品类 · ${g.qty} 套 · ${mode(s)}`), products = uniqueProducts(g);
      const withModel = !!g.modelImage;
      const layouts = g.group === 1 ? [[.86, 1.7, 5.65, 4.55]] : g.group === 2 ? [[.86, 1.7, 3.42, 4.55], [4.52, 1.7, 3.42, 4.55]] : g.group === 3 ? [[.86, 1.7, 3.36, 4.55], [4.45, 1.7, 3.36, 2.15], [4.45, 4.07, 3.36, 2.18]] : [[.86, 1.7, 3.36, 2.15], [4.45, 1.7, 3.36, 2.15], [.86, 4.07, 3.36, 2.18], [4.45, 4.07, 3.36, 2.18]];
      products.slice(0, 4).forEach((l, i) => {
        const [x, y, w, h] = layouts[i] || layouts[0];
        box(p, x, y, w, h, PALETTE.white);
        picture(p, l.image, l.crop, x + .12, y + .12, w - .24, h - .7, `${l.category} ${l.name}`);
        txt(p, `${i + 1}. ${l.category} · ${l.color}`, x + .16, y + h - .47, w - .32, .22, 12, { bold: true });
        txt(p, `SKU / 尺码与金额见明细页`, x + .16, y + h - .24, w - .32, .15, 8.5, { color: PALETTE.muted });
      });
      const sx = g.group === 1 ? 6.82 : 8.1, sw = g.group === 1 ? 5.75 : 4.45;
      if (withModel) {
        box(p, sx, 1.7, sw, 3.38, PALETTE.pale);
        picture(p, g.modelImage, null, sx + .14, 1.85, sw - .28, 2.88, g.imageLabel || 'AI 搭配效果示意');
        txt(p, g.imageLabel || 'AI 搭配效果示意', sx + .12, 4.81, sw - .24, .17, 9.5, { align: 'center', color: PALETTE.muted });
      } else {
        txt(p, '好搭配，\n从真实商品开始。', sx + .06, 1.96, sw - .1, 1.2, 25, { bold: true, color: t.color });
        txt(p, '本方案采用商品原图拼版。\n实际商品以 SKU 与规格明细为准。', sx + .08, 3.4, sw - .16, .9, 13, { color: PALETTE.muted });
      }
      const goodsSubtotal = g.lines.reduce((n, l) => n + l.amount, 0);
      txt(p, money(combined ? Math.round(goodsSubtotal / g.qty) : g.perSet), sx + .08, 5.22, sw - .16, .52, 27, { color: t.color, bold: true });
      txt(p, combined ? `每套商品均价 · 优惠及费用见合并账单` : `平均每套 / 含费用   ·   应付 ${money(g.total)}`, sx + .08, 5.86, sw - .16, .39, 11, { color: PALETTE.muted });
      pages.push(p);
      if (g.reason) pages.push(...paragraphPages(s, `${g.name} · 搭配说明`, `${g.group} 品类 · 方案 ${gi + 1}`, [g.reason, `商品：${products.map(l => `${l.name}（${l.color}）`).join('；')}`], 'reason'));
      if (combined) return;
      pages.push(...tablePages(s, `${g.name} · SKU 报价明细`, `人民币 · ${taxLabel(s)} · 数量为具体尺码采购量`, [
        { label: 'SKU / 款号', w: 2.2 }, { label: '商品名称', w: 3.05 }, { label: '颜色 / 尺码', w: 1.75 }, { label: '数量', w: 1.3 }, { label: '销售单价', w: 1.8 }, { label: '行金额', w: 1.9 }
      ], g.lines.map(l => [l.sku, l.name, `${l.color} / ${l.size}`, `${l.qty} ${l.unit || '件'}`, money(l.price), money(l.amount)])));
      pages.push(...tablePages(s, `${g.name} · 金额确认`, `${g.qty} 套 · 平均每套 ${money(g.perSet)}，平均值仅作展示`, [{ label: '金额项目', w: 8.3 }, { label: '人民币金额', w: 3.7 }], [
        ['商品合计', money(g.subtotal)], ['整单优惠（仅作用于商品合计）', `− ${money(g.discount)}`], [feeLabel(s), money(g.fee)], ...(s.quote?.taxMode === 'excluded' ? [[`加收税额（${s.quote.taxRate}%）`, money(g.tax)]] : []), [`本${s.mode === 'combined' ? '组合分摊' : '备选方案'}应付`, money(g.total)]
      ], 'totals'));
    });
    if (combined) {
      for (const g of s.groups) {
        pages.push(...tablePages(s, '合并采购 · SKU 明细', '同一 SKU 的采购数量已累计；金额固定于本次报价快照', [
          { label: 'SKU / 款号', w: 2.2 }, { label: '商品名称', w: 3.05 }, { label: '颜色 / 尺码', w: 1.75 }, { label: '数量', w: 1.3 }, { label: '销售单价', w: 1.8 }, { label: '行金额', w: 1.9 }
        ], g.lines.map(l => [l.sku, l.name, `${l.color} / ${l.size}`, `${l.qty} ${l.unit || '件'}`, money(l.price), money(l.amount)])));
        pages.push(...tablePages(s, '合并采购账单', `${taxLabel(s)} · 优惠、附加费用和适用税额只计一次`, [{ label: '金额项目', w: 8.3 }, { label: '人民币金额', w: 3.7 }], [
          ['合并商品金额', money(g.subtotal)], ['整单优惠', `− ${money(g.discount)}`], [feeLabel(s), money(g.fee)], ...(s.quote?.taxMode === 'excluded' ? [[`加收税额（${s.quote.taxRate}%）`, money(g.tax)]] : []), ['合并采购应付', money(g.total)]
        ], 'combined'));
      }
    }
    pages.push(...paragraphPages(s, '商务说明与联系方式', '报价有效期与库存核对时间分别记录', [
      `报价日期：${date(s.createdAt)}；有效至：${date(s.validUntil)}。`,
      `库存核对时间：${date(s.stockAsOf)}；价格版本 P${s.priceVersion}，库存版本 S${s.stockVersion}。报价不预占库存，正式订货时须再次确认可售数量与交期。`,
      `图片说明：${shown.some(g => g.modelImage) ? '采用的模特或搭配图为 AI 搭配效果示意，非实拍；未列入 SKU 清单的陪衬服饰不包含在报价中。' : '采用商品原图拼版。'} 实际商品以原图、SKU、颜色和尺码规格为准。`,
      `对客备注：${s.quote?.note || '现货报价，以订单确认时库存为准。'}`,
      `联系：${t.contact || '销售顾问'}${t.phone ? ` / ${t.phone}` : ''}。`,
      '原型评审说明：本文件使用演示商品数据与示意素材，不构成真实供货承诺。',
      `企业模板：V${t.version}。本次文件对应固定报价快照，外部编辑不会自动回写系统。`
    ], 'terms'));
    pages.forEach((p, i) => txt(p, `${String(i + 1).padStart(2, '0')} / ${pages.length}`, 9.02, 7.1, 1.1, .18, 8.5, { color: PALETTE.muted, align: 'right' }));
    return pages;
  }
  function canonical(s) {
    const saved = F.state.snapshots.find(x => x.id === s?.id);
    if (!saved || JSON.stringify(saved) !== JSON.stringify(s)) throw new Error('仅允许交付报价确认生成的完整版本，请先回报价确认。');
    return saved;
  }
  function assetStatus(s) {
    const blocks = [], warnings = [];
    for (const g of s.groups) for (const l of g.lines) {
      const p = F.state.products.find(x => l.productId ? x.id === l.productId : x.sizes?.some(z => z.sku === l.sku));
      const withdrawn = (F.state.media || []).some(m => m.url === l.image && m.links?.some(link => link.productId === (l.productId || p?.id) && link.allowed === false));
      if (withdrawn) blocks.push(`${l.name} 在该历史版本使用的素材已禁用，请创建使用新素材的修订版。`);
      if (p && p.imageAllowed === false) blocks.push(`${l.name} 的图片已被禁用，请修订方案后重新确认。`);
      if (p && l.imageVersion && p.imageVersion !== l.imageVersion) warnings.push(`${l.name} 的当前图片已更新；重下历史版本仍保留当时图片。`);
      if (!l.image) blocks.push(`${l.name} 缺少快照商品图。`);
    }
    const v = validity(s);
    if (v.voided) blocks.push(`报价已作废：${v.event.reason}。历史内容保留；请复制为新草稿后重新确认。`);
    if (v.expired) warnings.push('此报价已过有效期，只能下载标记为“已过期”的内部核对文件。重新对客交付请复制为新草稿并重新确认。');
    if (s.priceVersion !== F.state.priceVersion || s.stockVersion !== F.state.stockVersion) warnings.push('商品价格或库存已有新版本；下载历史文件保留原报价，新的客户承诺请创建修订版。');
    return { blocks: [...new Set(blocks)], warnings: [...new Set(warnings)] };
  }
  function checkExport(s, options = {}) {
    F.requireRole(['sales', 'admin', 'manager']);
    canonical(s);
    const issues = assetStatus(s);
    if (issues.blocks.length) throw new Error(issues.blocks.join('\n'));
    if (validity(s).expired && !options.internal) throw new Error('报价已过期，禁止正式对客导出；请选择明确标记的内部下载或创建修订版。');
    return issues;
  }
  function cropRect(crop, width, height) {
    if (!crop) return { x: 0, y: 0, w: width, h: height };
    if (Array.isArray(crop)) crop = { x: crop[0], y: crop[1], w: crop[2], h: crop[3] };
    if (crop.cols || crop.columns) {
      const cols = crop.cols || crop.columns, rows = crop.rows || 1, i = crop.index || 0;
      return { x: (crop.col ?? i % cols) * width / cols, y: (crop.row ?? Math.floor(i / cols)) * height / rows, w: width / cols, h: height / rows };
    }
    const x = Number(crop.x || 0), y = Number(crop.y || 0), w = Number(crop.w ?? crop.width ?? 1), h = Number(crop.h ?? crop.height ?? 1);
    return w <= 1 && h <= 1 ? { x: x * width, y: y * height, w: w * width, h: h * height } : { x, y, w, h };
  }
  const imageCache = new Map();
  function sourceAllowed(source) {
    if (/^data:image\/(png|jpeg|webp);base64,/i.test(source)) return true;
    if (/^data:image\/svg\+xml[;,]/i.test(source)) return true;
    try { const u = new URL(source, global.location?.href || 'http://localhost/'); return u.origin === global.location?.origin && ['http:', 'https:', 'file:'].includes(u.protocol); } catch { return false; }
  }
  async function imageData(source, crop) {
    const key = `${source}|${JSON.stringify(crop)}`;
    if (imageCache.has(key)) return imageCache.get(key);
    if (!sourceAllowed(source)) throw new Error('图片来源不受支持，请上传 PNG、JPEG 或 WebP 后重新生成报价。');
    const pending = new Promise((resolve, reject) => {
      const img = new Image(), timeout = setTimeout(() => reject(new Error('图片读取超过 15 秒，请检查素材后重试。')), 15000);
      img.onload = () => {
        clearTimeout(timeout);
        try {
          const r = cropRect(crop, img.naturalWidth, img.naturalHeight);
          if (r.w <= 0 || r.h <= 0 || r.x < 0 || r.y < 0 || r.x + r.w > img.naturalWidth + 1 || r.y + r.h > img.naturalHeight + 1) throw new Error('商品图片裁切区域不合法');
          const scale = Math.min(1, 1600 / Math.max(r.w, r.h));
          const canvas = document.createElement('canvas'); canvas.width = Math.max(1, Math.round(r.w * scale)); canvas.height = Math.max(1, Math.round(r.h * scale));
          canvas.getContext('2d').drawImage(img, r.x, r.y, r.w, r.h, 0, 0, canvas.width, canvas.height);
          resolve({ data: canvas.toDataURL('image/png'), width: canvas.width, height: canvas.height });
        } catch (error) { reject(global.location?.protocol === 'file:' ? new Error('浏览器限制直接打开本地文件时的图片处理。请通过本地预览服务打开原型，再重试图片导出。') : error); }
      };
      img.onerror = () => { clearTimeout(timeout); reject(new Error('商品图片读取失败，请检查本地素材或上传图片后重新生成版本。')); };
      img.src = source;
    });
    imageCache.set(key, pending);
    try { return await pending; } catch (error) { imageCache.delete(key); throw error; }
  }
  function previewElement(e) {
    const common = `left:${e.x / W * 100}%;top:${e.y / H * 100}%;width:${e.w / W * 100}%;height:${e.h / H * 100}%;`;
    if (e.type === 'box') return `<div class="delivery-el delivery-shape" style="${common}background:#${e.fill}"></div>`;
    if (e.type === 'text') return `<div class="delivery-el delivery-text" style="${common}font-size:${e.size / (W * 72) * 100}cqw;color:#${e.color};font-weight:${e.bold ? 700 : 400};text-align:${e.align || 'left'};line-height:${e.lineSpacing || 1.3}">${esc(e.text)}</div>`;
    if (e.type === 'image') return `<div class="delivery-el delivery-image" style="${common}"><img data-delivery-image="${esc(e.image)}" data-crop="${esc(JSON.stringify(e.crop || null))}" alt="${esc(e.alt)}"></div>`;
    if (e.type === 'tableRow') return `<div class="delivery-el delivery-table-row ${e.header ? 'is-header' : ''}" style="${common}font-size:${e.size / (W * 72) * 100}cqw;background:#${e.header ? e.fill : e.stripe ? PALETTE.pale : PALETTE.white}">${e.cells.map((c, i) => `<div style="width:${e.widths[i] / e.w * 100}%">${esc(c)}</div>`).join('')}</div>`;
    return '';
  }
  function previewHTML(p) { return `<div class="delivery-slide" role="img" aria-label="第 ${ui.page + 1} 页：${esc(p.title)}" style="background:#${p.background}">${p.elements.map(previewElement).join('')}</div>`; }
  async function hydratePreview(root) {
    await Promise.allSettled([...root.querySelectorAll('[data-delivery-image]')].map(async el => {
      try { const value = await imageData(el.dataset.deliveryImage, JSON.parse(el.dataset.crop)); el.src = value.data; }
      catch { el.replaceWith(Object.assign(document.createElement('span'), { className: 'delivery-image-error', textContent: '图片不可用 · 导出前请修复' })); }
    }));
  }
  async function buildPptx(s, options = {}) {
    const Ctor = options.PptxGenJS || global.PptxGenJS || global.pptxgen;
    if (typeof Ctor !== 'function') throw new Error('PPT 组件尚未加载，请刷新本地页面重试。');
    const pptx = new Ctor(); pptx.defineLayout({ name: 'ZHIXUAN_WIDE', width: W, height: H }); pptx.layout = 'ZHIXUAN_WIDE';
    pptx.author = template(s).brand; pptx.subject = '演示商品报价 · 固定报价版本'; pptx.title = s.title; pptx.company = template(s).brand; pptx.lang = 'zh-CN';
    pptx.theme = { headFontFace: 'Microsoft YaHei', bodyFontFace: 'Microsoft YaHei', lang: 'zh-CN' };
    const pages = buildPages(s), resolveImage = options.imageResolver || imageData;
    for (let pi = 0; pi < pages.length; pi++) {
      options.checkCancelled?.();
      const p = pages[pi], slide = pptx.addSlide(); slide.background = { color: p.background };
      for (const e of p.elements) {
        if (e.type === 'box') slide.addShape(pptx.ShapeType.rect, { x: e.x, y: e.y, w: e.w, h: e.h, line: { color: e.fill, transparency: 100 }, fill: { color: e.fill } });
        if (e.type === 'text') slide.addText(e.text, { x: e.x, y: e.y, w: e.w, h: e.h, fontSize: e.size, fontFace: 'Microsoft YaHei', color: e.color, bold: !!e.bold, margin: 0, breakLine: false, valign: 'top', align: e.align || 'left', lineSpacingMultiple: e.lineSpacing || 1.3, paraSpaceAfterPt: 0, isTextBox: true });
        if (e.type === 'image') {
          const img = await resolveImage(e.image, e.crop); options.checkCancelled?.();
          const ratio = Math.min(e.w / img.width, e.h / img.height), w = img.width * ratio, h = img.height * ratio;
          slide.addImage({ data: img.data, x: e.x + (e.w - w) / 2, y: e.y + (e.h - h) / 2, w, h, altText: e.alt });
        }
        if (e.type === 'tableRow') slide.addTable([e.cells.map(c => ({ text: c, options: { breakLine: false } }))], { x: e.x, y: e.y, w: e.w, h: e.h, colW: e.widths, rowH: e.h, fontSize: e.size, fontFace: 'Microsoft YaHei', color: e.header ? PALETTE.white : PALETTE.ink, bold: !!e.header, fill: e.header ? template(s).color : e.stripe ? PALETTE.pale : PALETTE.white, margin: [.07, .07, .04, .07], border: { type: 'solid', color: PALETTE.line, pt: .5 }, autoPage: false, valign: 'mid', paraSpaceAfterPt: 0, lineSpacingMultiple: 1.15 });
      }
      options.progress?.(pi + 1, pages.length);
    }
    return { pptx, pages };
  }
  function csv(s) {
    const v = validity(s);
    const rows = [['报价版本', s.id, `V${s.revision}`], ['版本状态', v.voided ? '已作废，禁止对客交付' : v.expired ? '已过期，仅供内部核对，禁止对客交付' : '已确认'], ['客户', s.clientName], ['方案', s.title], ['报价模式', mode(s)], ['报价日期', date(s.createdAt)], ['有效至', date(s.validUntil)], ['库存核对时间', date(s.stockAsOf)], ['币种 / 税费', `人民币 CNY / ${taxLabel(s)}`], ['数据说明', '演示数据，仅用于原型评审'], [], ['候选方案', '品类档位', '采购套数', 'SKU', '商品名称', '颜色', '尺码', '单位', '采购数量', '销售单价（元）', '行金额（元）']];
    s.groups.forEach(g => {
      g.lines.forEach(l => rows.push([g.name, g.group, g.qty, l.sku, l.name, l.color, l.size, l.unit, l.qty, (l.price / 100).toFixed(2), (l.amount / 100).toFixed(2)]));
      rows.push([g.name, '商品合计', (g.subtotal / 100).toFixed(2)], [g.name, '优惠金额', (g.discount / 100).toFixed(2)], [g.name, feeLabel(s), (g.fee / 100).toFixed(2)]);
      if (s.quote?.taxMode === 'excluded') rows.push([g.name, `加收税额（${s.quote.taxRate}%）`, (g.tax / 100).toFixed(2)]);
      rows.push([g.name, s.mode === 'combined' ? '合并采购应付' : '对应应付', (g.total / 100).toFixed(2)], ...(g.perSet ? [[g.name, '平均每套（含费用）', (g.perSet / 100).toFixed(2)]] : []), []);
    });
    rows.push(['对客备注', s.quote?.note || ''], ['联系方式', template(s).contact, template(s).phone]);
    return '\uFEFF' + rows.map(row => row.map(value => { const v = str(value); return `"${(/^[\s]*[=+@\-]/.test(v) ? "'" + v : v).replace(/"/g, '""')}"`; }).join(',')).join('\r\n');
  }
  function diff(s, previous) {
    if (!previous) return ['这是本客户的首个报价版本。'];
    const changes = [];
    if (s.clientName !== previous.clientName) changes.push(`客户：${previous.clientName} → ${s.clientName}`);
    if (s.title !== previous.title) changes.push(`主题：${previous.title} → ${s.title}`);
    if (s.mode !== previous.mode) changes.push(`报价口径：${mode(previous)} → ${mode(s)}`);
    if (s.priceVersion !== previous.priceVersion) changes.push(`价格数据：P${previous.priceVersion} → P${s.priceVersion}`);
    if (s.stockVersion !== previous.stockVersion) changes.push(`库存数据：S${previous.stockVersion} → S${s.stockVersion}`);
    if (JSON.stringify(s.template) !== JSON.stringify(previous.template)) changes.push(`企业模板：V${template(previous).version} → V${template(s).version}`);
    s.groups.forEach(g => {
      const old = previous.groups.find(x => x.id === g.id || x.name === g.name && x.group === g.group);
      if (!old) return changes.push(`新增 ${g.name}（${g.group} 品类）`);
      if (g.qty !== old.qty) changes.push(`${g.name} 采购量：${old.qty} → ${g.qty} 套`);
      if (g.total !== old.total) changes.push(`${g.name} 应付：${money(old.total)} → ${money(g.total)}`);
      if (JSON.stringify(g.lines.map(l => [l.sku, l.qty, l.price, l.imageVersion])) !== JSON.stringify(old.lines.map(l => [l.sku, l.qty, l.price, l.imageVersion]))) changes.push(`${g.name} 的商品、尺码数量、销售单价或图片版本有调整。`);
      if (g.modelImage !== old.modelImage || g.imageLabel !== old.imageLabel || JSON.stringify(g.components) !== JSON.stringify(old.components)) changes.push(`${g.name} 的交付图片或采购组合已调整。`);
    });
    previous.groups.filter(g => !s.groups.some(x => x.id === g.id || x.name === g.name && x.group === g.group)).forEach(g => changes.push(`移除 ${g.name}`));
    if (str(s.quote?.note) !== str(previous.quote?.note)) changes.push('对客备注已调整。');
    if (s.validUntil !== previous.validUntil) changes.push(`有效期更新为 ${date(s.validUntil)}`);
    return changes.length ? changes : ['商品、金额、图片与模板未变化；创建了新的确认记录。'];
  }
  function rememberExport(s, type, filename) {
    F.commit('下载报价文件', next => { next.history ||= []; next.history.unshift({ id: F.uid('export'), type: 'export', format: type, snapshotId: s.id, revision: s.revision, filename, createdAt: new Date().toISOString() }); });
  }
  async function performExport(s, type) {
    if (ui.busy) return;
    ui.error = '';
    try {
      const internal = validity(s).expired;
      checkExport(s, { internal });
      ui.busy = true; const token = ++ui.token; ui.message = '正在准备交付文件…'; F.render();
      const checkCancelled = () => { if (token !== ui.token) throw new Error('已取消本次生成，报价版本已保留，可重新下载。'); };
      const filename = `${internal ? '已过期_仅内部核对_' : ''}${safeName(s.title)}_${safeName(s.id)}_V${s.revision}`;
      let blob, extension;
      if (type === 'pptx') {
        const result = await buildPptx(s, { checkCancelled, progress: (n, count) => { ui.message = `正在生成第 ${n} / ${count} 页`; const el = document.querySelector('[data-export-status]'); if (el) el.textContent = ui.message; } });
        checkCancelled(); blob = await result.pptx.write({ outputType: 'blob', compression: true }); extension = 'pptx';
      } else if (type === 'csv') { blob = new Blob([csv(s)], { type: 'text/csv;charset=utf-8' }); extension = 'csv'; }
      else {
        if (!global.JSZip) throw new Error('图片打包组件尚未加载，请刷新重试。');
        const zip = new global.JSZip(), manifest = ['织选报价图片包', `版本 ${s.id} / V${s.revision}`, ...(internal ? ['已过期 · 仅供内部核对，禁止对客交付。'] : []), '原型演示素材；AI 图片为搭配效果示意，实际商品以原图与规格为准。', '仅包含该报价快照使用的商品原图与已采用图片。', ''];
        const groups = displayGroups(s);
        for (let gi = 0; gi < groups.length; gi++) {
          const g = groups[gi], folder = `${gi + 1}_${safeName(g.name)}`;
          for (const [i, l] of uniqueProducts(g).entries()) { const img = await imageData(l.image, l.crop); checkCancelled(); const name = `${folder}/${i + 1}_${safeName(l.sku)}_${safeName(l.category)}_商品原图.png`; zip.file(name, img.data.split(',')[1], { base64: true }); manifest.push(`${name} — ${l.name} / ${l.color}`); }
          if (g.modelImage) { const img = await imageData(g.modelImage, null); checkCancelled(); const name = `${folder}/AI搭配效果示意.png`; zip.file(name, img.data.split(',')[1], { base64: true }); manifest.push(`${name} — ${g.imageLabel || 'AI 搭配效果示意'}`); }
        }
        zip.file('图片说明.txt', manifest.join('\r\n')); zip.file('报价明细.csv', csv(s));
        blob = await zip.generateAsync({ type: 'blob', compression: 'DEFLATE' }); extension = 'zip';
      }
      checkCancelled(); checkExport(s, { internal });
      F.download(blob, `${filename}.${extension}`); rememberExport(s, type, `${filename}.${extension}`);
      ui.message = `${extension.toUpperCase()} 文件已交由浏览器下载 · 报价版本 V${s.revision}`; F.notify(ui.message);
    } catch (error) { ui.error = error.message || '文件生成失败，可基于同一报价版本重试。'; F.notify(ui.error, 'error'); }
    finally { ui.busy = false; F.render(); }
  }
  function voidSnapshot(s, reason) {
    F.requireRole(['sales', 'manager', 'admin']); canonical(s);
    const value = str(reason).trim();
    if (!value || value.length > 500) throw new Error('请填写 1–500 字的作废原因。');
    if (validity(s).voided) throw new Error('此报价已经作废，不能恢复原版本；可复制创建新草稿。');
    F.commit('作废报价版本', state => { state.snapshotLifecycle ||= []; state.snapshotLifecycle.unshift({ id: F.uid('quote-event'), snapshotId: s.id, status: 'voided', reason: value, at: new Date().toISOString(), role: state.role }); });
  }
  function voidDialog(s) {
    F.modal({ title: `作废报价 V${s.revision}`, body: `<p>作废后保留本版本的全部历史，停止文件下载；原编号不能恢复有效。需要重新报价时，可复制成新草稿。</p><label class="field" style="margin-top:16px">作废原因<textarea id="delivery-void-reason" rows="4" maxlength="500" required></textarea></label><p id="delivery-void-error" class="delivery-form-error" role="alert"></p>`, footer: '<button class="btn secondary" id="delivery-void-cancel">取消</button><button class="btn primary" id="delivery-void-save">确认作废</button>', bind: () => {
      document.getElementById('delivery-void-cancel').onclick = F.closeModal;
      document.getElementById('delivery-void-save').onclick = () => { try { voidSnapshot(s, document.getElementById('delivery-void-reason').value); F.closeModal(); F.notify('报价已作废，历史内容保留。'); F.render(); } catch (error) { document.getElementById('delivery-void-error').textContent = error.message; } };
    } });
  }
  function copySnapshot(s) {
    F.requireRole(['sales', 'manager', 'admin']); canonical(s);
    const client = F.state.clients.find(c => c.id === s.brief?.clientId) || F.state.clients.find(c => c.name === s.clientName);
    if (!client) throw new Error('历史报价的客户资料已移除，请先在客户管理恢复该客户。');
    const groups = displayGroups(s), prepared = groups.map(g => {
      const productIds = [], allocations = {};
      for (const line of g.lines) {
        const p = F.state.products.find(p => line.productId ? p.id === line.productId : p.sizes?.some(size => size.sku === line.sku));
        if (!p) throw new Error(`历史商品 ${line.name} 已移除，请先恢复商品资料后复制。`);
        if (!productIds.includes(p.id)) productIds.push(p.id);
        allocations[p.id] ||= {}; allocations[p.id][line.size] = (allocations[p.id][line.size] || 0) + line.qty;
      }
      return { id: F.uid('look'), name: g.name, group: g.group, productIds, locks: [], selected: true, qty: g.qty, allocations, allocationsConfirmed: false, reason: g.reason || '', revision: 1, imageId: null, imageMode: 'original' };
    });
    F.archiveProject();
    F.commit('从历史报价复制新草稿', state => {
      state.projectId = F.uid('project');
      state.brief = { ...F.clone(s.brief), clientId: client.id, title: `${s.title} · 修订`, confirmed: false };
      state.looks = prepared; state.imageJobs = []; state.quote = { ...F.clone(s.quote), exception: null };
      state.projects.unshift({ id: state.projectId, status: 'active', fromSnapshotId: s.id, updatedAt: new Date().toISOString(), brief: F.clone(state.brief), looks: F.clone(state.looks), quote: F.clone(state.quote), imageJobs: [] });
    });
    F.notify('已从历史版本复制新草稿。请确认需求，并按最新商品、价格、库存重新核对。'); F.go('brief');
  }
  function editTemplate() {
    try { F.requireRole(['admin', 'manager']); } catch (error) { return F.notify(error.message, 'error'); }
    const saved = F.state.settings.template || {}, base = { brand: saved.brand || F.state.settings.brand || '织选', color: '#' + safeColor(saved.color), contact: saved.contact || F.state.settings.contact || '', phone: saved.phone || F.state.settings.phone || '', logo: saved.logo || '', version: saved.version || 1 };
    let logo = base.logo, logoLoading = false, logoToken = 0;
    F.modal({ title: '企业交付模板', body: `<p class="delivery-help">设置用于下一次确认的报价版本；已确认历史版本保留原模板。固定 16:9 版式支持一至四品类。</p><form id="delivery-template-form"><div class="grid"><label class="field">企业名称<input name="brand" value="${esc(base.brand)}" maxlength="30" required></label><label class="field">主题颜色<input name="color" type="color" value="${esc(base.color)}"></label><label class="field">联系人<input name="contact" value="${esc(base.contact)}" maxlength="60" required></label><label class="field">联系方式<input name="phone" value="${esc(base.phone)}" maxlength="40" required></label></div><label class="field">企业 Logo（PNG / JPEG / WebP，≤ 1 MB）<input name="logo" type="file" accept="image/png,image/jpeg,image/webp"></label><div id="delivery-logo-status" class="delivery-help">${logo ? '已配置 Logo' : '未配置 Logo，将展示企业名称'}</div><button type="button" class="btn secondary" id="delivery-clear-logo">使用文字标识</button><p class="delivery-form-error" role="alert" id="delivery-template-error"></p></form>`, footer: '<button class="btn secondary" data-close>取消</button><button class="btn primary" type="submit" form="delivery-template-form">保存模板</button>', bind: () => {
      const form = document.getElementById('delivery-template-form'), error = document.getElementById('delivery-template-error');
      document.querySelector('[data-close]').onclick = F.closeModal;
      document.getElementById('delivery-clear-logo').onclick = () => { logoToken++; logoLoading = false; logo = ''; form.elements.logo.value = ''; document.getElementById('delivery-logo-status').textContent = '将使用企业文字标识'; };
      form.elements.logo.onchange = async event => {
        const file = event.target.files[0]; if (!file) return;
        const token = ++logoToken; logoLoading = true;
        try {
          if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type) || file.size > 1024 * 1024) throw new Error('请选择 1 MB 以内的 PNG、JPEG 或 WebP 图片。');
          const data = await new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = () => reject(new Error('Logo 读取失败')); reader.readAsDataURL(file); });
          await imageData(data); if (token !== logoToken) return; logo = data; document.getElementById('delivery-logo-status').textContent = `已载入 ${file.name}`; error.textContent = '';
        } catch (e) { if (token === logoToken) { error.textContent = e.message; form.elements.logo.value = ''; } }
        finally { if (token === logoToken) logoLoading = false; }
      };
      form.onsubmit = event => {
        event.preventDefault(); if (!form.reportValidity()) return;
        try {
          if (logoLoading) throw new Error('Logo 正在读取，请稍候再保存。');
          const data = new FormData(form), next = { brand: str(data.get('brand')).trim(), color: str(data.get('color')), contact: str(data.get('contact')).trim(), phone: str(data.get('phone')).trim(), logo, version: base.version + 1 };
          if (!next.brand || !next.contact || !next.phone) throw new Error('企业名称、联系人和联系方式不能为空。');
          F.requireRole(['admin', 'manager']); F.commit('更新企业交付模板', state => { state.settings.template = next; }); F.closeModal(); F.notify(`已保存模板 V${next.version}，下一次确认报价时生效。`); F.render();
        } catch (e) { error.textContent = e.message; }
      };
    } });
  }
  function render() {
    const snapshots = [...(F.state.snapshots || [])].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt) || b.revision - a.revision);
    if (ui.projectId !== F.state.projectId || !snapshots.some(s => s.id === ui.selected)) { ui.projectId = F.state.projectId; ui.selected = snapshots.find(x => !F.state.projectId || x.projectId === F.state.projectId)?.id || snapshots[0]?.id || null; ui.page = 0; if (ui.selected) ui.error = ''; }
    const s = snapshots.find(x => x.id === ui.selected);
    const buttons = `<button class="btn secondary" data-template>${F.icon('settings')} 企业模板</button><button class="btn secondary" data-quote>${F.icon('arrow-left')} 返回报价确认</button>`;
    if (!s) {
      F.main.innerHTML = `<div class="page-heading"><div><p class="eyebrow">DELIVERY STUDIO</p><h1>让方案成为一份好提案</h1><p>确认商品、图片与报价后，在这里预览并下载客户交付文件。</p></div><div class="delivery-actions">${buttons}</div></div><section class="panel delivery-empty"><div class="delivery-empty-icon">${F.icon('presentation')}</div><h2>还没有已确认的报价版本</h2><p>先完成报价检查，系统会将商品、金额、图片与模板保存为同一版本。</p><button class="btn primary" data-create>检查并生成报价版本</button><p class="delivery-help">支持可编辑 PPTX、报价 CSV 及整组图片 ZIP。</p>${ui.error ? `<p role="alert" class="delivery-form-error">${esc(ui.error)}</p>` : ''}</section>`;
      bindCommon(); return;
    }
    let pages;
    try { pages = buildPages(s); } catch (error) { F.main.innerHTML = `<section class="panel empty"><h2>此报价版本无法预览</h2><p role="alert">${esc(error.message)}</p><button class="btn secondary" data-quote>返回报价确认</button></section>`; bindCommon(); return; }
    ui.page = Math.min(ui.page, pages.length - 1);
    const status = assetStatus(s), v = validity(s), index = snapshots.indexOf(s), previous = snapshots.slice(index + 1).find(x => x.clientName === s.clientName), changes = diff(s, previous), history = (F.state.history || []).filter(x => x.type === 'export' && x.snapshotId === s.id);
    F.main.innerHTML = `<div class="page-heading"><div><p class="eyebrow">DELIVERY STUDIO</p><h1>报价与提案交付</h1><p>固定报价版本，一份清晰、可编辑的客户方案。</p></div><div class="delivery-actions">${buttons}</div></div>
      <section class="panel delivery-version-bar"><div><label for="delivery-version">报价版本</label><select id="delivery-version" ${ui.busy ? 'disabled' : ''}>${snapshots.map(x => `<option value="${esc(x.id)}" ${x.id === s.id ? 'selected' : ''}>V${x.revision} · ${validity(x).voided ? '已作废 · ' : validity(x).expired ? '已过期 · ' : ''}${esc(x.clientName)} · ${esc(date(x.createdAt))}</option>`).join('')}</select></div><div><span class="badge">${v.voided ? '已作废' : v.expired ? '已过期 · 仅内部' : '已确认'}</span><span class="badge">${s.mode === 'combined' ? '合并采购' : '独立备选'}</span><span class="badge">${pages.length} 页 · 16:9</span><span class="badge">模板 V${template(s).version}</span></div><div class="delivery-actions"><button class="btn secondary" data-copy-snapshot ${ui.busy ? 'disabled' : ''}>复制为新草稿</button><button class="btn secondary" data-void-snapshot ${ui.busy || v.voided ? 'disabled' : ''}>作废版本</button></div></section>
      ${status.blocks.length ? `<section class="delivery-notice is-error" role="alert"><strong>交付前需要修复</strong>${status.blocks.map(x => `<p>${esc(x)}</p>`).join('')}<button class="btn secondary" data-quote>修订报价</button></section>` : ''}
      ${status.warnings.length ? `<details class="delivery-notice"><summary>历史版本提示 · ${status.warnings.length} 项</summary>${status.warnings.map(x => `<p>${esc(x)}</p>`).join('')}</details>` : ''}
      <div class="delivery-workspace"><aside class="panel delivery-outline"><h2>方案目录</h2><p>${esc(s.title)}</p><nav aria-label="幻灯片目录">${pages.map((p, i) => `<button class="delivery-page-tab ${i === ui.page ? 'active' : ''}" data-page="${i}" aria-current="${i === ui.page ? 'page' : 'false'}"><span>${String(i + 1).padStart(2, '0')}</span><span>${esc(p.title)}</span></button>`).join('')}</nav></aside>
      <section class="delivery-preview-area" aria-label="客户提案预览"><div class="delivery-preview-toolbar"><strong>${esc(pages[ui.page].title)}</strong><div><button class="btn secondary" aria-label="上一页" data-prev ${ui.page === 0 ? 'disabled' : ''}>${F.icon('chevron-left')}</button><span>${ui.page + 1} / ${pages.length}</span><button class="btn secondary" aria-label="下一页" data-next ${ui.page === pages.length - 1 ? 'disabled' : ''}>${F.icon('chevron-right')}</button></div></div><div id="delivery-preview">${previewHTML(pages[ui.page])}</div><p class="delivery-help">内容预览与 PPT 使用同一报价快照。桌面演示软件中的字体换行可能有所差异。</p>
      <div class="panel delivery-export-panel"><div><h2>${v.expired ? '仅供内部核对下载' : '下载本版本'}</h2><p>${v.expired ? '文件保留原日期并标注已过期，禁止再次对客交付。' : '标题、说明、SKU 与金额表可编辑，图片嵌入文件。'}</p></div><div class="delivery-actions"><button class="btn primary" data-export="pptx" ${ui.busy || status.blocks.length ? 'disabled' : ''}>${F.icon('download')} ${v.expired ? '内部 PPTX' : '下载 PPTX'}</button><button class="btn secondary" data-export="csv" ${ui.busy || status.blocks.length ? 'disabled' : ''}>${v.expired ? '内部 CSV' : '报价 CSV'}</button><button class="btn secondary" data-export="zip" ${ui.busy || status.blocks.length ? 'disabled' : ''}>${v.expired ? '内部图片 ZIP' : '图片 ZIP'}</button></div><p class="delivery-export-status" data-export-status role="status">${esc(ui.message || '导出保持当前版本的报价日期、金额和图片。')}</p>${ui.busy ? '<button class="btn secondary" data-cancel-export>取消生成</button>' : ''}${ui.error ? `<p class="delivery-form-error" role="alert">${esc(ui.error)}</p>` : ''}<button class="btn secondary" data-create ${ui.busy ? 'disabled' : ''}>检查并确认当前草稿为新版本</button></div></section></div>
      <div class="delivery-bottom-grid"><section class="panel"><h2>版本变化</h2><p class="delivery-help">${previous ? `与 V${previous.revision} 比较` : '报价版本记录'}</p><ul class="delivery-diff">${changes.map(x => `<li>${esc(x)}</li>`).join('')}</ul></section><section class="panel"><h2>本版本下载记录</h2>${history.length ? `<ul class="delivery-history">${history.slice(0, 6).map(x => `<li><strong>${esc(x.format.toUpperCase())}</strong><span>${esc(date(x.createdAt))}</span><small>${esc(x.filename)}</small></li>`).join('')}</ul>` : '<p class="delivery-help">尚无下载记录。下载不代表客户接受或成交。</p>'}<p class="delivery-help">库存核对 ${esc(date(s.stockAsOf))}<br>报价有效至 ${esc(date(s.validUntil))}</p></section></div>`;
    bindCommon(); hydratePreview(F.main);
    document.getElementById('delivery-version').onchange = e => { ui.selected = e.target.value; ui.page = 0; ui.error = ''; ui.message = ''; F.render(); };
    F.main.querySelectorAll('[data-page]').forEach(el => el.onclick = () => { ui.page = Number(el.dataset.page); F.render(); });
    F.main.querySelector('[data-prev]').onclick = () => { ui.page = Math.max(0, ui.page - 1); F.render(); };
    F.main.querySelector('[data-next]').onclick = () => { ui.page = Math.min(pages.length - 1, ui.page + 1); F.render(); };
    F.main.querySelectorAll('[data-export]').forEach(el => el.onclick = () => performExport(s, el.dataset.export));
    F.main.querySelector('[data-copy-snapshot]').onclick = () => { try { copySnapshot(s); } catch (error) { F.notify(error.message, 'error'); } };
    F.main.querySelector('[data-void-snapshot]').onclick = () => voidDialog(s);
    F.main.querySelector('[data-cancel-export]')?.addEventListener('click', () => { ui.token++; ui.message = '正在取消，报价版本将保留。'; F.render(); });
  }
  function bindCommon() {
    F.main.querySelectorAll('[data-template]').forEach(el => el.onclick = editTemplate);
    F.main.querySelectorAll('[data-quote]').forEach(el => el.onclick = () => F.go('quotes'));
    F.main.querySelectorAll('[data-create]').forEach(el => el.onclick = async () => {
      try { const s = await F.createSnapshot(); ui.selected = s?.id || F.state.snapshots[0]?.id; ui.projectId = F.state.projectId; ui.page = 0; ui.error = ''; F.notify('报价检查通过，已保存可交付版本。'); F.render(); }
      catch (error) { ui.error = error.message; F.notify(error.message, 'error'); F.render(); }
    });
  }
  F.delivery = { pages: buildPages, buildPptx, csv, diff, assetStatus, validity, checkExport, voidSnapshot, copySnapshot, export: performExport, imageData, cropRect, selectSnapshot: id => { ui.selected = id; ui.projectId = F.state.projectId; ui.page = 0; ui.error = ''; ui.message = ''; } };
  F.pages.delivery = render;
})(typeof window !== 'undefined' ? window : globalThis);
