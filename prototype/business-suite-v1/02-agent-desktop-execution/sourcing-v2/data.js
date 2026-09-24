(function () {
  'use strict';
  const day = (offset = 0) => { const d = new Date(); d.setDate(d.getDate() + offset); return [d.getFullYear(), String(d.getMonth() + 1).padStart(2, '0'), String(d.getDate()).padStart(2, '0')].join('-'); };
  const suppliers = [
    { id: 's-t1', name: '织序服饰工厂', city: '广东 · 广州', category: 'tshirt', contact: '织序接待助手', peer: 'AI 客服', price: 23.8, process: 2, pack: .5, shipping: 90, fixed: 0, moq: 100, days: 8, platform: '1688', score: '规格待补充', color: '#dfe6de' },
    { id: 's-t2', name: '白川制衣', city: '浙江 · 杭州', category: 'tshirt', contact: '陈经理', peer: '人工客服', price: 25.2, process: 1.2, pack: .3, shipping: 60, fixed: 0, moq: 100, days: 7, platform: '1688', score: '已核对报价', color: '#e9e5db' },
    { id: 's-t3', name: '原本纺织', city: '江苏 · 苏州', category: 'tshirt', contact: '在线接待', peer: '身份未知', price: 21.5, process: 1.8, pack: .5, shipping: 120, fixed: 150, moq: 500, days: 12, platform: '供应商网站', score: '起订量较高', color: '#dadfe2' },
    { id: 's-b1', name: '方寸包装', city: '浙江 · 温州', category: 'bag', contact: '方寸报价助手', peer: 'AI 客服', price: .62, process: .08, pack: .02, shipping: 45, fixed: 80, moq: 500, days: 6, platform: '1688', score: '定制包装', color: '#e4dece' },
    { id: 's-b2', name: '青禾纸品', city: '广东 · 东莞', category: 'bag', contact: '王女士', peer: '人工客服', price: .68, process: .06, pack: .01, shipping: 35, fixed: 50, moq: 500, days: 5, platform: '供应商网站', score: '可提供样品', color: '#dbe3d3' },
    { id: 's-b3', name: '森本包装', city: '江苏 · 无锡', category: 'bag', contact: '商务咨询', peer: '身份未知', price: .58, process: .1, pack: .02, shipping: 65, fixed: 100, moq: 2000, days: 9, platform: '1688', score: '批量生产', color: '#e7ddd2' },
    { id: 's-c1', name: '温度器物', city: '浙江 · 永康', category: 'cup', contact: '温度智能接待', peer: 'AI 客服', price: 32.5, process: 3, pack: 1.5, shipping: 80, fixed: 0, moq: 50, days: 7, platform: '1688', score: '定制礼品', color: '#cedcd7' },
    { id: 's-c2', name: '日常杯业', city: '浙江 · 武义', category: 'cup', contact: '李经理', peer: '人工客服', price: 34, process: 2, pack: 1, shipping: 60, fixed: 0, moq: 50, days: 6, platform: '供应商网站', score: '现货可定制', color: '#d9dce3' },
    { id: 's-c3', name: '归山金属制品', city: '广东 · 潮州', category: 'cup', contact: '业务接待', peer: '身份未知', price: 28.8, process: 2.5, pack: 1.8, shipping: 100, fixed: 100, moq: 300, days: 10, platform: '1688', score: '工厂直供', color: '#e4ded6' }
  ];
  const categories = { tshirt: '定制 T 恤', bag: '包装袋', cup: '保温杯', other: '其他品类' };
  const templates = {
    tshirt: { title: '秋季品牌活动 · 定制 T 恤采购', spec: '白色纯棉 220g，圆领短袖，胸前单色 LOGO，M/L/XL 各 100 件', quantity: 300, budget: 30 },
    bag: { title: '门店补货 · 牛皮纸包装袋', spec: '原色牛皮纸 120g，26×12×32cm，单色 LOGO，扭绳手提', quantity: 1000, budget: 1.2 },
    cup: { title: '客户礼赠 · 定制保温杯', spec: '500ml，316 内胆，哑光白色，激光 LOGO，独立礼盒', quantity: 100, budget: 45 }
  };
  function classify(value) { if (/包装袋|纸袋|手提袋|牛皮纸/i.test(value)) return 'bag'; if (/保温杯|水杯|杯业|内胆/i.test(value)) return 'cup'; if (/t\s*恤|短袖|制衣/i.test(value)) return 'tshirt'; return 'other'; }
  function candidates(task) { return suppliers.filter(s => s.category === task.category && task.platforms.includes(s.platform)).slice(0, task.maxCandidates).map(s => ({ ...s, id: task.id + ':' + s.id, supplierId: s.id, selected: false, spec: task.spec, source: '示例商品页 · ' + s.platform, sourceUrl: '', captured: new Date().toISOString() })); }
  function quote(task, candidate, incomplete) {
    return { quantity: task.quantity, unit: task.unit || '件', destination: task.destination || '杭州', specification: task.spec, currency: 'CNY', taxBasis: incomplete ? null : '含税', unitPrice: candidate.price, processing: candidate.process, packaging: candidate.pack, shipping: incomplete ? null : candidate.shipping, fixed: candidate.fixed, leadDays: candidate.days, leadBasis: '规格与样品确认完成后', leadStartDate: day(), leadMilestone: '到货', transitDays: null, leadText: '规格与样品确认完成后 ' + candidate.days + ' 天到货（示例测算前提：' + day() + ' 确认）', validUntil: day(14), moq: candidate.moq, receivedAt: new Date().toISOString(), source: '供应商示例回复', original: '' };
  }
  function quoteText(q) {
    return `按您要求的规格：${q.specification}；数量 ${q.quantity} ${q.unit || '件'}，起订 ${q.moq} ${q.unit || '件'}，送达${q.destination || '杭州'}。单价 ¥${q.unitPrice}，加工 ¥${q.processing}/${q.unit || '件'}，包装 ¥${q.packaging}/${q.unit || '件'}，本批固定费用 ¥${q.fixed}。${q.shipping == null ? '运费待确认' : '运费 ¥' + q.shipping}；${q.taxBasis || '税费口径待确认'}。交期：${q.leadText || '待核验起算条件与到货含义'}；报价有效至 ${q.validUntil}。以上为供应商示例陈述，不表示已经下单。`;
  }
  function makeTask(id, type, status) { return { id, ...templates[type], category: type, unit: '件', destination: '杭州', deadline: day(21), enquiryDeadline: day(7), targetQuotes: 3, maxCandidates: 3, platforms: ['1688', '供应商网站'], followup: true, maxRounds: 2, created: new Date().toISOString(), status, owner: 'agent', error: null, candidates: [], conversations: [], events: [], selectedQuote: null }; }
  function makeConversation(task, c, stage) {
    const text = `您好，我们计划采购 ${task.quantity} ${task.unit || '件'}${categories[task.category]}，规格：${task.spec}，收货城市${task.destination || '杭州'}，最晚交货要求 ${task.deadline}。请提供含税单价、加工费、包装费、运费、本批固定费用、起订量、交期起算条件（出货或到货）和报价有效期。请仅提供报价，本次咨询不构成采购承诺。`;
    const conv = { id: task.id + ':conv:' + c.supplierId, taskId: task.id, candidateId: c.id, supplierId: c.supplierId, name: c.name, peer: c.peer, contact: c.contact, status: stage || 'review', inquiry: text, reason: '', messages: [], quote: null, followupCount: 0, checked: false, savedDraft: '', revision: 1 };
    if (stage === 'replied') { conv.messages.push({ direction: 'out', text, time: new Date().toISOString(), kind: '已确认发送 · 示例' }); conv.quote = quote(task, c, c.peer === 'AI 客服'); conv.quote.original = quoteText(conv.quote); conv.messages.push({ direction: 'in', text: conv.quote.original, time: new Date().toISOString(), kind: c.peer + ' · 示例回复' }); }
    return conv;
  }
  // 单价保留六位小数，以 BigInt 计算每个行项目，再四舍五入到分并汇总。
  function amountMinor(value, quantity = 1) {
    const s = String(value); if (!/^\d+(\.\d{1,6})?$/.test(s) || !Number.isSafeInteger(quantity) || quantity < 0) return null;
    const [whole,fraction=''] = s.split('.'); const micro = BigInt(whole) * 1000000n + BigInt(fraction.padEnd(6,'0'));
    const cents = (micro * BigInt(quantity) + 5000n) / 10000n; return cents <= BigInt(Number.MAX_SAFE_INTEGER) ? Number(cents) : null;
  }
  function cost(task,q) { if(!q)return null;const lines=[amountMinor(q.unitPrice,task.quantity),amountMinor(q.processing,task.quantity),amountMinor(q.packaging,task.quantity),amountMinor(q.shipping),amountMinor(q.fixed)];return lines.every(n=>n!==null)?lines.reduce((n,x)=>n+x,0)/100:null; }
  function assessment(task, q) {
    if (!q) return { valid: false, reasons: ['尚未取得报价'], total: null };
    const reasons = [];
    ['unitPrice', 'processing', 'packaging', 'shipping', 'fixed'].forEach(k => { if (typeof q[k] !== 'number' || !Number.isFinite(q[k]) || q[k] < 0) reasons.push(({ unitPrice:'单价', processing:'加工费', packaging:'包装费', shipping:'运费', fixed:'本批固定费用' })[k] + '待确认'); });
    if (q.taxBasis !== '含税') reasons.push('税费口径待确认');
    if (q.currency !== 'CNY') reasons.push('币种不一致');
    if (q.quantity !== task.quantity) reasons.push('数量口径不一致');
    if (q.unit !== (task.unit || '件')) reasons.push('计量单位不一致');
    if (q.destination !== (task.destination || '杭州')) reasons.push('收货地不一致');
    if (!q.specification || q.specification !== task.spec) reasons.push('规格条件不一致');
    if (!Number.isInteger(q.leadDays) || q.leadDays < 0) reasons.push('交期待确认');
    const startValid = typeof q.leadStartDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(q.leadStartDate) && !Number.isNaN(Date.parse(q.leadStartDate));
    const leadValid = q.leadBasis && startValid && ['出货','到货'].includes(q.leadMilestone) && (q.leadMilestone === '到货' || Number.isInteger(q.transitDays) && q.transitDays >= 0);
    if (!leadValid) reasons.push('交期起算或运输条件待确认');
    if (!q.validUntil || !/^\d{4}-\d{2}-\d{2}$/.test(q.validUntil)) reasons.push('有效期待确认');
    else if (q.validUntil < day()) reasons.push('报价已过期');
    if (!Number.isInteger(q.moq) || q.moq < 1) reasons.push('起订量待确认');
    else if (q.moq > task.quantity) reasons.push('未达到起订量');
    let expectedArrival = null;
    if (leadValid && Number.isInteger(q.leadDays)) { const d = new Date(q.leadStartDate + 'T12:00:00'); d.setDate(d.getDate() + q.leadDays + (q.leadMilestone === '出货' ? q.transitDays : 0)); expectedArrival = [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-'); if(expectedArrival > task.deadline)reasons.push('预计到货超过最晚交货要求'); }
    const numeric = ['unitPrice','processing','packaging','shipping','fixed'].every(k => typeof q[k] === 'number' && Number.isFinite(q[k]) && q[k] >= 0);
    const total = numeric && q.taxBasis === '含税' && q.currency === 'CNY' && q.quantity === task.quantity && q.specification === task.spec && q.unit === (task.unit || '件') && q.destination === (task.destination || '杭州') ? cost(task,q) : null;
    if (numeric && total === null && q.taxBasis==='含税') reasons.push('金额精度或比较口径待核验');
    const cap = amountMinor(task.budget,task.quantity);
    if(cap===null||cap<=0)reasons.push('预算上限待确认');
    else if (total !== null && Math.round(total*100) > cap) reasons.push('超过含税到货预算上限');
    return { valid: reasons.length === 0, reasons, total, expectedArrival };
  }
  function initial() {
    const a = makeTask('AD-260911-001', 'tshirt', 'comparing'); a.candidates = candidates(a); a.candidates.forEach(c => c.selected = true); a.conversations = a.candidates.map(c => makeConversation(a, c, 'replied'));
    a.events = [{ time: new Date().toISOString(), text: '示例任务已采集 3 个候选，已取得 3 份回复，其中 1 份费用待确认。', type: 'seed' }];
    const b = makeTask('AD-260911-002', 'bag', 'candidates'); b.candidates = candidates(b); b.events = [{ time: new Date().toISOString(), text: '示例采集完成，等待选择供应商。', type: 'seed' }];
    const c = makeTask('AD-260911-003', 'cup', 'plan'); c.events = [{ time: new Date().toISOString(), text: '采购计划已整理，等待复核后启动。', type: 'seed' }];
    return { version: 2, activeTask: a.id, tasks: [a,b,c], draft: null, connection: { computer: 'online', login: 'valid' }, rules: { followup: true, maxRounds: 3 }, supplierNotes: {}, logs: [], savedAt: null };
  }
  window.AdenData = { day, suppliers, categories, templates, classify, candidates, quote, quoteText, makeConversation, assessment, cost, amountMinor, initial };
}());
