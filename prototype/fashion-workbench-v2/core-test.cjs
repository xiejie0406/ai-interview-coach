/* 独立业务回归：仅用虚构夹具和内存存储，不启动浏览器或访问外部服务。 */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');

const corePath = path.join(__dirname, 'core.js');
const source = fs.readFileSync(corePath, 'utf8');
const sourceHash = crypto.createHash('sha256').update(source).digest('hex');
const NOW = Date.parse('2026-09-11T04:00:00.000Z');
const NOW_ISO = new Date(NOW).toISOString();
class FixedDate extends Date {
  constructor(...args) { super(...(args.length ? args : [NOW])); }
  static now() { return NOW; }
}

function fresh() {
  const records = new Map();
  const storage = {
    fail: false,
    getItem(key) { return records.get(key) ?? null; },
    setItem(key, value) {
      if (this.fail) throw new Error('测试注入：存储已满');
      records.set(key, value);
    },
  };
  const sandbox = { module: { exports: {} }, localStorage: storage, Date: FixedDate, console, Blob, URL, setTimeout };
  vm.runInNewContext(source, sandbox, { filename: corePath });
  const F = sandbox.module.exports;
  F.state = F.initial();
  F.state.products = [
    product('shirt', '上衣', 8000, [{ size: 'M', stock: 300 }, { size: 'L', stock: 300 }]),
    product('pants', '裤子', 6000, [{ size: 'M', stock: 300 }, { size: 'L', stock: 300 }]),
    product('hat', '帽子', 2000, [{ size: '均码', stock: 300 }]),
    product('shoes', '鞋', 9000, [{ size: '42', stock: 300 }]),
  ];
  F.state.brief.budget = 3000000;
  F.state.quote = { mode: 'alternatives', discountType: 'percent', discount: 5, fee: 30000, taxMode: 'included', taxRate: 13, feeTaxable: true, validDays: 7, note: '测试商务说明', exception: null };
  F.state.looks = [look(F, 'four', ['shirt', 'pants', 'hat', 'shoes'], 100)];
  F.state.brief.confirmed = true;
  return { F, storage, records };
}

function product(id, category, price, sizes) {
  return {
    id, sku: id, style: id, name: '测试' + category, category, color: '白色', unit: category === '鞋' ? '双' : category === '帽子' ? '顶' : '件',
    price, source: '测试商品源', warehouse: '主仓',
    sizes: sizes.map(row => ({ ...row, sku: id + '-' + row.size, price, stockAsOf: NOW_ISO, priceAsOf: NOW_ISO })),
    image: 'data:image/png;base64,AA==', imageVersion: 1, imageAllowed: true, crop: null,
    status: 'active', confirmed: true, tags: ['休闲', '团建'], season: '四季', material: '测试资料',
  };
}

function look(F, id, productIds, qty) {
  const allocations = Object.fromEntries(productIds.map(productId => {
    const sizes = F.product(productId).sizes;
    const base = Math.floor(qty / sizes.length);
    return [productId, Object.fromEntries(sizes.map((row, i) => [row.size, base + (i < qty % sizes.length ? 1 : 0)]))];
  }));
  return { id, name: id, group: productIds.length, productIds, locks: [], selected: true, qty, allocations, reason: '独立测试夹具', revision: 1, imageId: null, imageMode: 'original' };
}

function noErrors(result) { assert.equal(result.errors.length, 0, result.errors.join('；')); }
function hasError(result, pattern) { assert.ok(result.errors.some(message => pattern.test(message)), '未拒绝预期错误，实际：' + result.errors.join('；')); }
function sameValue(a, b) { assert.equal(JSON.stringify(a), JSON.stringify(b)); }
function refuseSnapshot(F, pattern) { assert.throws(() => F.createSnapshot(), pattern); }

const cases = [];
const check = (id, ac, name, run) => cases.push({ id, ac, name, run });

check('CORE-01', 'AC20', '金额精度和非法输入', () => {
  const { F } = fresh();
  assert.equal(F.parseMoney('19.99'), 1999);
  assert.equal(F.parseMoney('0.01'), 1);
  for (const bad of ['19.995', '-1', '1e3', 'NaN', 'Infinity', '']) assert.throws(() => F.parseMoney(bad));
});

check('CORE-02', 'AC11', '100套四品类含税报价为24050元', () => {
  const { F } = fresh();
  const result = F.quoteGroups(); noErrors(result);
  const group = result.groups[0];
  assert.equal(group.subtotal, 2500000); assert.equal(group.discount, 125000);
  assert.equal(group.fee, 30000); assert.equal(group.tax, 0); assert.equal(group.total, 2405000); assert.equal(group.perSet, 24050);
});

check('CORE-03', 'AC20', '19.99元3件优惠7.5%加0.99元为56.46元', () => {
  const { F } = fresh();
  Object.assign(F.state.quote, { discount: 7.5, fee: 99 });
  const result = F.totals(5997); noErrors(result);
  assert.equal(result.discount, 450); assert.equal(result.total, 5646);
});

check('CORE-04', 'AC20', '百分率舍入与独立整数分参照一致', () => {
  const { F } = fresh();
  F.state.quote.fee = 0;
  for (const basisPoints of [1, 15, 115, 750, 1001, 1337, 3333, 9595]) {
    F.state.quote.discount = basisPoints / 100;
    for (let subtotal = 1; subtotal <= 10000; subtotal++) {
      const expected = Number((BigInt(subtotal) * BigInt(basisPoints) + 5000n) / 10000n);
      assert.equal(F.totals(subtotal).discount, expected, `分金额=${subtotal}，优惠基点=${basisPoints}`);
    }
  }
});

check('CORE-05', 'AC23', '未税折后商品和应税运费税基为105元', () => {
  const { F } = fresh();
  Object.assign(F.state.quote, { discount: 5, fee: 1000, taxMode: 'excluded', taxRate: 13, feeTaxable: true });
  const result = F.totals(10000); noErrors(result);
  assert.equal(result.tax, 1365); assert.equal(result.total, 11865);
});

check('CORE-06', 'AC23', '含税模式不重复加税', () => {
  const { F } = fresh();
  Object.assign(F.state.quote, { discount: 5, fee: 1000, taxMode: 'included', taxRate: 13 });
  const result = F.totals(10000); noErrors(result);
  assert.equal(result.tax, 0); assert.equal(result.total, 10500);
});

check('CORE-07', 'AC23', '已含税或不再加税的运费不进入未税税基', () => {
  const { F } = fresh();
  Object.assign(F.state.quote, { discount: 5, fee: 1000, taxMode: 'excluded', taxRate: 13, feeTaxable: false });
  const result = F.totals(10000); noErrors(result);
  assert.equal(result.tax, 1235); assert.equal(result.total, 11735);
});

check('CORE-08', 'AC20/23', '优惠率及税率超过两位百分数小数必须拒绝', () => {
  const { F } = fresh();
  F.state.quote.discount = 7.555;
  hasError(F.totals(10000), /优惠|精度|小数/);
  Object.assign(F.state.quote, { discount: 5, taxMode: 'excluded', taxRate: 13.333 });
  hasError(F.totals(10000), /税率|精度|小数/);
});

check('CORE-09', 'AC25', '商品单价为零即使有运费也不能正式报价', () => {
  const { F } = fresh();
  F.state.looks = [look(F, 'single', ['hat'], 1)];
  F.product('hat').price = 0; F.product('hat').sizes[0].price = 0;
  refuseSnapshot(F, /零|单价|大于|价格/);
});

check('CORE-10', 'AC25', '100%商品折扣不能由有运费及负责人例外绕过', () => {
  const { F } = fresh();
  F.state.role = 'manager'; F.state.quote.discount = 100; F.approveException('测试例外');
  refuseSnapshot(F, /100|零|折扣|优惠|大于/);
});

check('CORE-11', 'AC20', '固定优惠超商品金额被拒绝', () => {
  const { F } = fresh();
  Object.assign(F.state.quote, { discountType: 'fixed', discount: 10001, fee: 0 });
  hasError(F.totals(10000), /超过商品|优惠后商品金额必须大于零/);
});

check('CORE-12', 'AC06/12', '预算未填写表示不设上限', () => {
  const { F } = fresh();
  F.state.brief.budget = null;
  noErrors(F.quoteGroups());
});

check('CORE-13', 'AC19', '尺码总数90或110均不能冒充100套', () => {
  for (const total of [90, 110]) {
    const { F } = fresh();
    F.state.looks[0].allocations.shirt = { M: 60, L: total - 60 };
    hasError(F.calcLook(F.state.looks[0]), /尺码合计/);
  }
});

check('CORE-14', 'AC13/19', '款式总量充足但M码不足仍拒绝', () => {
  const { F } = fresh();
  F.product('shirt').sizes[0].stock = 49;
  assert.ok(F.stock(F.product('shirt')) >= 100);
  hasError(F.calcLook(F.state.looks[0]), /shirt-M库存不足/);
});

check('CORE-15', 'AC19', '负数、小数或不存在尺码不能进入正式报价', () => {
  for (const allocations of [{ M: -1, L: 101 }, { M: 49.5, L: 50.5 }, { M: 50, XXX: 50 }]) {
    const { F } = fresh(); F.state.looks[0].allocations.shirt = allocations;
    assert.ok(F.calcLook(F.state.looks[0]).errors.length > 0);
  }
});

check('CORE-16', 'AC12', '备选60套与50套不累计消耗帽子库存', () => {
  const { F } = fresh();
  F.product('hat').sizes[0].stock = 90;
  F.state.looks = [look(F, 'option60', ['hat'], 60), look(F, 'option50', ['hat'], 50)];
  const result = F.quoteGroups(); noErrors(result);
  assert.equal(result.groups.length, 2); assert.equal(result.total, null);
});

check('CORE-17', 'AC12', '合并60套与50套需110顶并拒绝90顶库存', () => {
  const { F } = fresh();
  F.product('hat').sizes[0].stock = 90;
  F.state.looks = [look(F, 'option60', ['hat'], 60), look(F, 'option50', ['hat'], 50)];
  F.state.quote.mode = 'combined';
  hasError(F.quoteGroups(), /合并需求 110 超过库存 90/);
});

check('CORE-18', 'AC12', '合并库存恰足且折扣运费整单仅计算一次', () => {
  const { F } = fresh();
  F.product('hat').sizes[0].stock = 110;
  F.state.looks = [look(F, 'option60', ['hat'], 60), look(F, 'option50', ['hat'], 50)];
  F.state.quote.mode = 'combined';
  const result = F.quoteGroups(); noErrors(result);
  assert.equal(result.groups[0].lines.length, 1); assert.equal(result.groups[0].lines[0].qty, 110);
  assert.equal(result.groups[0].subtotal, 220000); assert.equal(result.groups[0].discount, 11000); assert.equal(result.total, 239000);
});

check('CORE-19', 'AC12/25', '合并固定优惠按整单判定而非先按每个候选扣减', () => {
  const { F } = fresh();
  F.product('hat').price = 1000; F.product('hat').sizes[0].price = 1000;
  F.state.looks = [look(F, 'a', ['hat'], 1), look(F, 'b', ['hat'], 1)];
  Object.assign(F.state.quote, { mode: 'combined', discountType: 'fixed', discount: 1500, fee: 0 });
  F.state.role = 'manager'; F.approveException('合并整单测试');
  const result = F.quoteGroups(); noErrors(result);
  assert.equal(result.total, 500);
});

check('CORE-20', 'AC13', '库存超过24小时被拒绝', () => {
  const { F } = fresh(); F.product('shirt').sizes[0].stockAsOf = new Date(NOW - 86400001).toISOString();
  hasError(F.calcLook(F.state.looks[0]), /24 小时|过期|时效/);
});

check('CORE-21', 'AC13', '库存正好24小时仍在有效边界', () => {
  const { F } = fresh(); F.product('shirt').sizes[0].stockAsOf = new Date(NOW - 86400000).toISOString();
  noErrors(F.calcLook(F.state.looks[0]));
});

check('CORE-22', 'AC13', '无效库存时间不能绕过过期核验', () => {
  const { F } = fresh(); F.product('shirt').sizes[0].stockAsOf = 'not-a-date';
  hasError(F.calcLook(F.state.looks[0]), /时间|时效|更新|库存/);
});

check('CORE-23', 'AC03/13', '未来超过5分钟的库存时间被拒绝', () => {
  const { F } = fresh(); F.product('shirt').sizes[0].stockAsOf = new Date(NOW + 3600000).toISOString();
  hasError(F.calcLook(F.state.looks[0]), /时间|未来|库存/);
});

check('CORE-24', 'AC22', '客户专属表完全缺价不回退批发价', () => {
  const { F } = fresh(); F.state.clients[0].priceTable = '客户专属表';
  assert.equal(F.price(F.product('hat'), '均码'), null);
  hasError(F.quoteGroups(), /客户指定价格|专属|缺价|价格表/);
});

check('CORE-25', 'AC22', '客户专属价取SKU值且缺失尺码仍不可报价', () => {
  const { F } = fresh(); F.state.clients[0].priceTable = '客户专属表';
  F.product('shirt').sizes[0].priceTables = { 客户专属表: 7000 };
  assert.equal(F.price(F.product('shirt'), 'M'), 7000);
  assert.equal(F.price(F.product('shirt'), 'L'), null);
});

check('CORE-26', 'AC25', '销售不能批准自己的超限例外', () => {
  const { F } = fresh(); F.state.quote.discount = 11;
  assert.throws(() => F.approveException('销售自行同意'), /权限/);
  hasError(F.quoteGroups(), /10%|负责人/);
});

check('CORE-27', 'AC25', '负责人例外只对当次内容签名生效', () => {
  const { F } = fresh(); F.state.role = 'manager'; F.state.quote.discount = 11; F.approveException('测试商务优惠');
  assert.equal(F.validException(), true); noErrors(F.quoteGroups());
  F.state.quote.fee += 1;
  assert.equal(F.validException(), false); hasError(F.quoteGroups(), /10%|负责人/);
});

check('CORE-28', 'AC25', '已到期的例外不能继续生效', () => {
  const { F } = fresh(); F.state.role = 'manager'; F.state.quote.discount = 11; F.approveException('测试到期');
  F.state.quote.exception.expiresAt = new Date(NOW - 1).toISOString();
  assert.equal(F.validException(), false);
});

check('CORE-29', 'AC14', '确认快照与后续价格库存和输入隔离', () => {
  const { F } = fresh(); const snapshot = F.createSnapshot(); const before = JSON.stringify(snapshot);
  F.product('shirt').sizes[0].price = 8800; F.state.quote.fee = 9900;
  F.state.looks[0].allocations.shirt.M = 40; F.state.priceVersion++; F.state.stockVersion++;
  assert.equal(JSON.stringify(snapshot), before); assert.equal(JSON.stringify(F.state.snapshots[0]), before);
});

check('CORE-30', 'AC14/17', '返回给导出方的快照对象不能改写已保存快照', () => {
  const { F } = fresh(); const snapshot = F.createSnapshot(); const before = JSON.stringify(F.state.snapshots[0]);
  try { snapshot.groups[0].total = 1; } catch (error) { assert.ok(error instanceof TypeError || /read only|只读/.test(error.message)); }
  assert.equal(JSON.stringify(F.state.snapshots[0]), before);
});

check('CORE-31', 'AC16', '对客快照不带成本和客户内部备注或例外原因', () => {
  const { F } = fresh();
  F.product('shirt').cost = 12345; F.state.clients[0].note = 'PRIVATE-CUSTOMER-NOTE';
  F.state.role = 'manager'; F.state.quote.discount = 11; F.approveException('PRIVATE-EXCEPTION-REASON');
  const text = JSON.stringify(F.createSnapshot());
  for (const fragment of ['PRIVATE-CUSTOMER-NOTE', 'PRIVATE-EXCEPTION-REASON', '"cost":']) assert.ok(!text.includes(fragment));
});

check('CORE-32', 'AC08/18/28', '正式报价拒绝组合版本不一致的已采用图片', () => {
  const { F } = fresh(); const item = F.state.looks[0];
  item.imageId = 'image-1'; item.imageMode = 'model'; item.revision = 2;
  F.state.imageJobs = [{ id: 'image-1', lookId: item.id, lookRevision: 1, productVersions: { shirt: 1, pants: 1, hat: 1, shoes: 1 }, status: 'approved', image: 'data:image/png;base64,AA==', source: 'external', kind: 'model', review: 'approved' }];
  hasError(F.quoteGroups(), /图片|图像|复核|版本/);
});

check('CORE-33', 'AC28/31', '外部上传图不得标为模型实时生成或样例图', () => {
  const { F } = fresh(); const item = F.state.looks[0]; item.imageId = 'upload-1'; item.imageMode = 'model';
  F.state.imageJobs = [{ id: 'upload-1', lookId: item.id, lookRevision: item.revision, productVersions: { shirt: 1, pants: 1, hat: 1, shoes: 1 }, status: 'approved', image: 'data:image/png;base64,AA==', source: 'external', kind: 'model', review: 'approved' }];
  const result = F.quoteGroups(); noErrors(result);
  assert.match(result.groups[0].imageLabel, /外部|上传/); assert.doesNotMatch(result.groups[0].imageLabel, /示例|样例|实时生成/);
});

check('CORE-34', 'AC26', '持久化失败保持旧内存状态和存储', () => {
  const { F, storage, records } = fresh(); F.save(); const before = JSON.stringify(F.state); const stored = [...records.values()][0];
  storage.fail = true;
  assert.throws(() => F.commit('测试修改', state => { state.quote.fee += 1; }), /存储已满/);
  assert.equal(JSON.stringify(F.state), before); assert.equal([...records.values()][0], stored);
});

check('CORE-35', 'AC26', '损坏但顶层合法的备份不覆盖可用状态', () => {
  const mutations = [
    ['尺码集合为空', data => { data.products[0].sizes = null; }],
    ['库存为负数', data => { data.products[0].sizes[0].stock = -1; }],
    ['方案引用不存在商品', data => { data.looks[0].productIds = ['missing-product']; }],
    ['快照集合不是数组', data => { data.snapshots = 'not-an-array'; }],
    ['缺失图片任务集合', data => { delete data.imageJobs; }],
  ];
  for (const [mutationName, mutate] of mutations) {
    const { F } = fresh(); const before = JSON.stringify(F.state); const damaged = JSON.parse(before);
    mutate(damaged);
    assert.throws(() => F.restore(JSON.stringify(damaged)), /备份|尺码|数据|结构|商品|引用/, mutationName);
    assert.equal(JSON.stringify(F.state), before);
  }
});

check('CORE-36', 'AC26', '正常备份完整恢复且未知版本被拒绝', () => {
  const { F } = fresh(); F.createSnapshot(); const original = JSON.stringify(F.state);
  F.state.quote.fee = 99; F.restore(original); sameValue(F.state, JSON.parse(original));
  const bad = JSON.parse(original); bad.schema = 900;
  assert.throws(() => F.restore(JSON.stringify(bad)), /备份/); sameValue(F.state, JSON.parse(original));
});

check('CORE-37', 'AC06/31', '新空白方案不会沿用样例300元运费', () => {
  const { F } = fresh(); F.newProject('新空白报价', 'client-1', false);
  assert.equal(F.state.quote.fee, 0);
});

check('CORE-38', 'AC07', '预算无解时不生成超预算的正式候选', () => {
  const { F } = fresh(); F.state.brief.budget = 100; F.state.brief.groups = [1]; F.state.brief.perGroup = 1;
  let generated;
  try { generated = F.generateLooks(); } catch (error) { assert.match(error.message, /预算|满足|候选/); return; }
  assert.ok(generated.length === 0 || generated.every(item => F.calcLook(item).errors.every(message => !/预算/.test(message))), '生成了明确超预算候选');
});

check('CORE-39', 'AC07', '候选不足不通过取模重复凑数', () => {
  const { F } = fresh(); F.state.brief.groups = [1]; F.state.brief.perGroup = 3;
  const generated = F.generateLooks();
  assert.equal(new Set(generated.map(item => item.productIds.join('|'))).size, generated.length);
});

check('CORE-40', 'AC07', '递进档位保留同一序列基础商品', () => {
  const { F } = fresh(); F.state.brief.groups = [1, 2, 3, 4]; F.state.brief.perGroup = 1;
  const generated = F.generateLooks(); assert.equal(generated.length, 4);
  for (let i = 1; i < generated.length; i++) sameValue(generated[i].productIds.slice(0, i), generated[i - 1].productIds);
});

check('CORE-41', 'AC14', '报价有效期超出1至30天不能确认', () => {
  for (const validDays of [0, 31, 1.5]) {
    const { F } = fresh(); F.state.quote.validDays = validDays;
    refuseSnapshot(F, /有效期|天|整数/);
  }
});

check('CORE-42', 'AC07', '一个上衣搭配三个裤子可以形成三个不同候选', () => {
  const { F } = fresh();
  F.state.products = [product('shirt', '上衣', 1000, [{ size: 'M', stock: 100 }]),
    product('pants-a', '裤子', 2000, [{ size: 'M', stock: 100 }]),
    product('pants-b', '裤子', 2500, [{ size: 'M', stock: 100 }]),
    product('pants-c', '裤子', 3000, [{ size: 'M', stock: 100 }])];
  Object.assign(F.state.brief, { qty: 1, groups: [2], perGroup: 3, budget: 100000 });
  Object.assign(F.state.quote, { discount: 0, fee: 0 });
  const generated = F.generateLooks();
  assert.equal(generated.length, 3);
  assert.equal(new Set(generated.map(item => item.productIds.join('|'))).size, 3);
});

check('CORE-43', 'AC07', '排序优先组合超预算时仍找到存在的可行搭配', () => {
  const { F } = fresh();
  F.state.products = [product('shirt-cheap', '上衣', 1000, [{ size: 'M', stock: 100 }]),
    product('shirt-pricey', '上衣', 9000, [{ size: 'M', stock: 100 }]),
    product('pants-pricey', '裤子', 9000, [{ size: 'M', stock: 100 }]),
    product('pants-cheap', '裤子', 1000, [{ size: 'M', stock: 100 }])];
  F.product('shirt-pricey').tags = ['其他']; F.product('pants-cheap').tags = ['其他'];
  Object.assign(F.state.brief, { qty: 1, groups: [2], perGroup: 1, budget: 5000, progressive: true });
  Object.assign(F.state.quote, { discount: 0, fee: 0 });
  const generated = F.generateLooks();
  assert.equal(generated.length, 1); assert.equal(F.calcLook(generated[0]).total, 2000);
});

check('CORE-44', 'AC07/13', '库存过期商品不能混入已满足硬条件的候选', () => {
  const { F } = fresh(); F.state.brief.groups = [1]; F.state.brief.perGroup = 1;
  for (const row of F.product('shirt').sizes) row.stockAsOf = new Date(NOW - 2 * 86400000).toISOString();
  let generated;
  try { generated = F.generateLooks(); } catch (error) { assert.match(error.message, /库存|时效|候选|满足|预算/); return; }
  assert.ok(generated.length === 0 || generated.every(item => F.calcLook(item).errors.length === 0), '生成了库存已过期候选');
});

let passed = 0;
const failed = [];
console.log(`core.js SHA256 ${sourceHash}`);
console.log(`Node ${process.version}; 固定业务时间 ${NOW_ISO}; 仅内存存储和虚构数据`);
for (const test of cases) {
  try { test.run(); passed++; console.log(`PASS ${test.id} [${test.ac}] ${test.name}`); }
  catch (error) {
    const detail = String(error.message).split('\n').slice(0, 8).join(' | ').slice(0, 1000);
    failed.push({ id: test.id, ac: test.ac, name: test.name, detail });
    console.log(`FAIL ${test.id} [${test.ac}] ${test.name}: ${detail}`);
  }
}
console.log(JSON.stringify({ total: cases.length, passed, failed: failed.length, failedIds: failed.map(test => test.id), sourceHash }, null, 2));
if (failed.length) process.exitCode = 1;
