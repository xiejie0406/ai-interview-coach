/* 采购原型的独立浏览器验收：仅操作本地演示 UI，不连接真实账号或外发。 */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createHash } = require('node:crypto');
const { pathToFileURL } = require('node:url');
const assert = require('node:assert/strict');
const runtime = process.env.PROTOTYPE_NODE_MODULES || 'C:/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
const { chromium } = require(path.join(runtime, 'playwright'));
const executablePath = process.env.PROTOTYPE_BROWSER || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const base = process.env.PROTOTYPE_BASE_URL || 'http://127.0.0.1:8789/02-agent-desktop-execution/sourcing-v2/';
const output = path.join(__dirname, 'output', 'playwright');
const runId = new Date().toISOString().replace(/[:.]/g, '-');
const filter = process.argv[2] || '';
const results = [], screenshots = [], runtimeErrors = [], blockedRequests = [];
let pagesVisited = 0;
const environment = { node: process.version, playwright: require(path.join(runtime, 'playwright/package.json')).version, executablePath, base, locale: 'zh-CN', timezone: 'Asia/Shanghai', mode: '独立无登录态 Chrome，仅本地原型 UI；不是真实平台或生产验收' };
function hashes(){return Object.fromEntries(['app.js','data.js','style.css','index.html'].map(file=>[file,{sha256:createHash('sha256').update(fs.readFileSync(path.join(__dirname,file))).digest('hex'),modifiedAt:fs.statSync(path.join(__dirname,file)).mtime.toISOString()}]));}
environment.sourceFilesAtStart=hashes();
const escape = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' })[c]);
const action = (p, name) => p.locator('[data-action="' + name + '"]');
const sleepUI = p => p.waitForTimeout(100);
async function contains(locator, text) { assert((await locator.innerText()).includes(text), '页面缺少：' + text); }
async function snapshot(p, name, screenshot = true) {
  const stem = 'qa-' + runId + '-' + name.replace(/[\\/:*?"<>|]/g,'-');
  fs.writeFileSync(path.join(output, stem + '.txt'), await p.locator('body').ariaSnapshot(), 'utf8');
  if (screenshot) { await p.screenshot({ path: path.join(output, stem + '.png'), fullPage: true, animations: 'disabled' }); screenshots.push({ name, file: stem + '.png' }); }
  return stem;
}
async function goto(p, route) { await p.goto(base + '#' + route); await p.locator('main').waitFor(); pagesVisited += 1; await sleepUI(p); }
async function nav(p, route) { await p.locator('.sidebar a[href="#' + route + '"]').click(); await sleepUI(p); }
async function readState(p) { return p.evaluate(() => JSON.parse(localStorage.getItem('aden-sourcing-v2'))); }
async function saveDownload(p, locator, name) {
  const [download] = await Promise.all([p.waitForEvent('download'), locator.click()]);
  const filename = 'qa-' + runId + '-' + name;
  await download.saveAs(path.join(output, filename));
  const text = fs.readFileSync(path.join(output, filename), 'utf8');
  assert(text.length > 20, '下载内容为空');
  return { filename, suggestedFilename: download.suggestedFilename(), text };
}
async function check(label, callback, p) {
  const started = Date.now();
  try { const detail = await callback(); results.push({ label, status:'Pass', elapsedMs:Date.now()-started, detail }); console.log('PASS ' + label); return true; }
  catch (error) { let artifact; if (p && !p.isClosed()) try { artifact = await snapshot(p, 'failure-' + results.length); } catch (_) {} results.push({ label, status:'Fail', elapsedMs:Date.now()-started, error:error.stack, artifact }); console.log('FAIL ' + label + ': ' + error.message); return false; }
}
async function contextPage(browser) {
  const context = await browser.newContext({ viewport:{width:1440,height:1000}, locale:'zh-CN', timezoneId:'Asia/Shanghai', reducedMotion:'reduce', acceptDownloads:true });
  await context.route('**/*', async route => { const url = route.request().url(); if (url.startsWith(base.split('/02-agent')[0]) || /^(data:|blob:|file:)/.test(url)) await route.continue(); else { blockedRequests.push(url); await route.abort(); } });
  const p = await context.newPage(); p.setDefaultTimeout(6500);
  p.on('pageerror', error => runtimeErrors.push({ url:p.url(), error:error.message }));
  p.on('console', message => { if(message.type()==='error') runtimeErrors.push({ url:p.url(), error:message.text() }); });
  return {context,p};
}
async function suite(browser, name, callback) {
  if(filter && filter!=='flows' && !name.includes(filter)) return;
  const {context,p} = await contextPage(browser);
  try { await goto(p,'dashboard'); await snapshot(p,name+'-initial',false); await check(name,()=>callback(p),p); }
  finally { await context.close(); }
}
async function layouts(browser) {
  if(filter && !'布局'.includes(filter)) return;
  const {context,p}=await contextPage(browser);
  const routes=['dashboard','tasks','new','candidates/AD-260911-001','conversations/AD-260911-001','quotes/AD-260911-001','suppliers','connections','rules','activity','task/AD-260911-003'];
  try {
    for(const width of [1440,375]) for(const route of routes) await check('布局 '+width+' '+route,async()=>{
      await p.setViewportSize({width,height:width<600?812:1000}); await goto(p,route);
      const layout=await p.evaluate(()=>({viewport:innerWidth,document:document.documentElement.scrollWidth,body:document.body.scrollWidth,headingTop:document.querySelector('h1').getBoundingClientRect().top,topbarBottom:document.querySelector('.topbar').getBoundingClientRect().bottom}));
      assert(layout.document<=width+1,'整页横向溢出：'+JSON.stringify(layout));
      assert(layout.headingTop>=layout.topbarBottom,'页面标题被固定顶部栏遮挡：'+JSON.stringify(layout));
      const selected=['dashboard','new','conversations/AD-260911-001','quotes/AD-260911-001'].includes(route);
      if(selected) await snapshot(p,route.split('/')[0]+'-'+width);
      assert((await p.locator('h1').innerText()).length>0,'页面没有标题');
      if(width===375) { await p.getByRole('button',{name:'展开导航',exact:true}).click(); await p.locator('.sidebar .nav-item').first().waitFor({state:'visible'}); await p.keyboard.press('Escape'); }
      return layout;
    },p);
  } finally {await context.close();}
}
async function arithmetic() {
  if(filter && !'金额'.includes(filter)) return;
  const sandbox={window:{},Date,BigInt}; vm.createContext(sandbox); vm.runInContext(fs.readFileSync(path.join(__dirname,'data.js'),'utf8'),sandbox); const D=sandbox.window.AdenData;
  await check('金额 / 独立 VM 精确金额与所有比较约束（非浏览器）',()=>{
    const t={...D.initial().tasks[0],quantity:1000,budget:18};
    const q={...D.quote(t,D.suppliers[0],false),unitPrice:17,processing:2,packaging:.30,shipping:450,fixed:100,moq:100};
    assert.equal(D.cost(t,q),19850); assert(D.assessment(t,q).reasons.includes('超过含税到货预算上限'));
    assert.equal(D.amountMinor(.035,10000),35000);
    assert.equal(D.cost({...t,quantity:10000},{...q,unitPrice:.035,processing:0,packaging:0,shipping:0,fixed:0}),350);
    assert.equal(D.cost(t,{...q,shipping:null}),null);
    assert.equal(D.amountMinor(.0000001,1000),null);
    const validTask={...t,budget:50}; assert(D.assessment(validTask,q).valid);
    const variants=[['shipping',null,'运费待确认'],['taxBasis',null,'税费口径待确认'],['currency','USD','币种不一致'],['quantity',999,'数量口径不一致'],['unit','米','计量单位不一致'],['destination','北京','收货地不一致'],['specification','其他规格','规格条件不一致'],['moq',1001,'未达到起订量'],['validUntil','2020-01-01','报价已过期'],['leadStartDate',null,'交期起算或运输条件待确认'],['leadMilestone','出货','交期起算或运输条件待确认']];
    const outputs=variants.map(([field,value,reason])=>{ const actual=D.assessment(validTask,{...q,[field]:value}); assert(!actual.valid,field+'未被拦截'); assert(actual.reasons.includes(reason),field+'原因缺失'); return {field,value,reasons:actual.reasons,total:actual.total}; });
    const arrival=D.assessment({...validTask,deadline:D.day(3)},q); assert(arrival.reasons.includes('预计到货超过最晚交货要求'));
    return { cost:19850, packagingPrecision:350, variants:outputs, arrival };
  });
}
function report() {
  const failed=results.filter(r=>r.status==='Fail').length;
  const data={runId,startedAt:runId,command:process.execPath+' '+__filename+(filter?' '+filter:''),exitCode:failed?1:0,environment,summary:{passed:results.length-failed,failed},results,screenshots,runtimeErrors,blockedRequests,limitations:['只验证本地演示数据和状态迁移；不验证真实商品采集、客服发送或回复获取。','生产 AC-18 跨空间服务端权限、AC-19 外部提示注入防护、AC-20 删除与备份数据生命周期未验证。','未验证原生 Windows Runner、网络在途发送、模型准确率、平台许可、性能 SLA。']};
  const file='qa-results-'+runId+'.json'; fs.writeFileSync(path.join(output,file),JSON.stringify(data,null,2),'utf8'); fs.writeFileSync(path.join(output,'qa-results.json'),JSON.stringify(data,null,2),'utf8');
  const history=fs.readdirSync(output).filter(f=>/^qa-results-.*\.json$/.test(f)).sort().map(f=>({file:f,...JSON.parse(fs.readFileSync(path.join(output,f),'utf8'))}));
  const recent=new Map();for(const run of history)for(const result of run.results){if(result.label==='验证执行器')continue;recent.set(result.label,{...result,runId:run.runId,evidence:run.file});}
  const coverage={generatedAt:new Date().toISOString(),scope:'各验证项最近一次实测，允许定向复验；不把未执行项算作本轮通过',passed:[...recent.values()].filter(x=>x.status==='Pass').length,failed:[...recent.values()].filter(x=>x.status==='Fail').length,results:[...recent.values()],history:history.map(h=>({runId:h.runId,file:h.file,summary:h.summary})),failureNotes:['2026-09-10T17-09-01-312Z：Windows快照文件名包含路径分隔符导致执行器失败；该错误发生于业务流程前，后续修正为安全文件名。','2026-09-10T17-11-07-423Z：手工报价测试未填新增单位和收货地，应用正确阻止比较；暂停测试定位了已消失的开始按钮。两项均保留初次失败，后续按最终可见UI修正测试并定向复验。'],limitations:data.limitations};
  fs.writeFileSync(path.join(output,'qa-coverage.json'),JSON.stringify(coverage,null,2),'utf8');
  const rows=results.map(r=>`<tr><td>${escape(r.label)}</td><td class="${r.status.toLowerCase()}">${r.status}</td><td>${r.elapsedMs} ms</td><td><pre>${escape(r.error||JSON.stringify(r.detail??{},null,2))}</pre>${r.artifact?`<a href="${r.artifact}.png">失败截图</a> · <a href="${r.artifact}.txt">实际 DOM 快照</a>`:''}</td></tr>`).join('');
  const latestImages=new Map();for(const h of history)for(const s of h.screenshots||[])if(!s.name.startsWith('failure'))latestImages.set(s.name,s);
  const html=`<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Aden 本地原型验证报告</title><style>body{font:15px/1.7 system-ui,sans-serif;background:#f5f7f2;color:#273024;margin:0}main{max-width:1220px;margin:auto;padding:36px 24px}h1{font-size:30px}section{background:white;padding:24px;border:1px solid #dce3d5;border-radius:12px;margin:20px 0}table{border-collapse:collapse;width:100%}td,th{text-align:left;vertical-align:top;border-bottom:1px solid #dde3d8;padding:12px}pre{white-space:pre-wrap;word-break:break-word;font-size:12px;margin:0}a{color:#385d2b}.pass{color:#386d28;font-weight:700}.fail{color:#b82f2f;font-weight:700}.gallery{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:16px}.gallery img{width:100%;border:1px solid #ddd;max-height:320px;object-fit:contain;object-position:top}.scroll{overflow:auto}code{overflow-wrap:anywhere}.note{color:#65522a;background:#fff5d8;padding:14px;border-radius:8px}</style><main><h1>Aden 本地交互原型验证</h1><p>所有验证项最近结果：${coverage.passed} Pass / ${coverage.failed} Fail · <a href="qa-coverage.json">汇总原始记录</a></p><p>本次定向运行 ${escape(runId)} · ${data.summary.passed} Pass / ${failed} Fail · 退出码 ${data.exitCode}</p><p class="note">这是隔离浏览器中的本地演示验证，不代表真实平台联机、用户验收或生产就绪。</p><section><h2>全部验收项最近结果</h2><div class="scroll"><table><thead><tr><th>验证项</th><th>最近结果</th><th>原始证据</th></tr></thead><tbody>${coverage.results.map(r=>`<tr><td>${escape(r.label)}</td><td class="${r.status.toLowerCase()}">${r.status}</td><td><a href="${r.evidence}">${escape(r.runId)}</a></td></tr>`).join('')}</tbody></table></div></section><section><h2>本次实际结果</h2><div class="scroll"><table><thead><tr><th>验证项</th><th>结果</th><th>耗时</th><th>实际输出 / 失败证据</th></tr></thead><tbody>${rows}</tbody></table></div></section><section><h2>环境与命令</h2><pre>${escape(JSON.stringify(environment,null,2))}</pre><p><code>${escape(data.command)}</code></p><p><a href="${file}">本次 JSON 原始记录</a></p></section><section><h2>关键页面截图</h2><div class="gallery">${[...latestImages.values()].map(s=>`<a href="${s.file}"><img src="${s.file}" alt="${escape(s.name)}"><div>${escape(s.name)}</div></a>`).join('')}</div></section><section><h2>所有运行历史（保留初次失败）</h2>${coverage.failureNotes.map(n=>`<p>${escape(n)}</p>`).join('')}${history.map(h=>`<p><a href="${h.file}">${escape(h.runId)}</a>：${h.summary.passed} Pass / ${h.summary.failed} Fail${h.results.filter(r=>r.status==='Fail').map(r=>`<br><span class="fail">${escape(r.label)}</span>：${escape((r.error||'').split('\n')[0])}`).join('')}</p>`).join('')}</section><section><h2>未覆盖的生产验证</h2><ul>${data.limitations.map(x=>`<li>${escape(x)}</li>`).join('')}</ul></section></main></html>`;
  fs.writeFileSync(path.join(output,'qa-report.html'),html,'utf8'); console.log(JSON.stringify({file,summary:data.summary,exitCode:data.exitCode})); process.exitCode=data.exitCode;
}
async function createTask(p, options={}) {
  await goto(p,'new');
  if(options.template) await p.locator('[data-template="'+options.template+'"]').click();
  if(options.title) await p.locator('#title').fill(options.title);
  if(options.spec) await p.locator('#spec').fill(options.spec);
  if(options.quantity) await p.locator('#quantity').fill(String(options.quantity));
  if(options.budget) await p.locator('#budget').fill(String(options.budget));
  if(options.category) await p.locator('#category').selectOption(options.category);
  if(options.targetQuotes && await p.locator('#targetQuotes').count()) await p.locator('#targetQuotes').fill(String(options.targetQuotes));
  await p.getByRole('button',{name:'复核采购计划',exact:false}).click();
  await p.locator('[data-action="start"]').waitFor();
  const id=decodeURIComponent(p.url().split('#task/')[1]); assert(id,'新任务没有独立任务路由');
  return id;
}
async function collectTask(p,id) { await goto(p,'task/'+id); await action(p,'start').click(); await p.locator('.badge').filter({hasText:'待筛选'}).waitFor(); await goto(p,'candidates/'+id); }
async function readyInquiries(p,id,firstOnly=false) {
  await goto(p,'candidates/'+id); const candidates=p.locator('[data-candidate]'); const count=await candidates.count();
  for(let i=0;i<(firstOnly?1:count);i++) await candidates.nth(i).check();
  await action(p,'prepare-inquiries').click(); await p.locator('.review-card').first().waitFor();
}
async function sendChecked(p,firstOnly=false) { const boxes=p.locator('[data-review-check]');await boxes.first().waitFor();const count=await boxes.count();for(let i=0;i<(firstOnly?1:count);i++)await boxes.nth(i).check();await action(p,'confirm-send').click();await p.locator('.ui-overlay').waitFor({state:'detached'}); }
async function applyScenario(p,name) {await action(p,'scenario').first().click();await p.locator('[data-scenario="'+name+'"]').click();await p.locator('.ui-overlay').waitFor({state:'detached'});}
async function chooseConv(p,id,supplier) {await goto(p,'conversations/'+id);await p.locator('.conv-button').filter({hasText:supplier}).click();await sleepUI(p);}
async function editQuote(p,id,supplier,values) {await goto(p,'quotes/'+id);await p.locator('tr').filter({hasText:supplier}).locator('[data-action="quote-evidence"]').click();await action(p,'edit-quote').click();for(const [key,value] of Object.entries(values)){const el=p.locator('#quote-'+key);if(['taxBasis','currency','leadMilestone'].includes(key))await el.selectOption(value);else await el.fill(String(value));}await action(p,'save-quote').click();await p.locator('.ui-overlay').waitFor({state:'detached'});}
async function flows(browser) {
  await suite(browser,'表单 / 必填、数量校验、草稿与刷新',async p=>{
    await action(p,'new').click(); await p.getByRole('button',{name:'复核采购计划',exact:false}).click();
    assert((await p.locator('#form-errors').innerText()).length>0,'空表单未反馈校验错误');
    await p.locator('[data-template="bag"]').click(); await p.locator('#title').fill('QA 包装袋草稿'); await p.locator('#quantity').fill('0');
    await p.getByRole('button',{name:'复核采购计划',exact:false}).click(); assert((await p.locator('#error-quantity').innerText()).length>0,'数量0未被拒绝');
    await p.locator('#quantity').fill('1000'); await p.locator('#spec').fill('QA 原色牛皮纸，单色标记'); await p.reload();
    assert.equal(await p.locator('#title').inputValue(),'QA 包装袋草稿'); assert.equal(await p.locator('#quantity').inputValue(),'1000'); assert.equal(await p.locator('#spec').inputValue(),'QA 原色牛皮纸，单色标记');
    await action(p,'save-draft').click(); await goto(p,'tasks'); assert.equal(await p.locator('tbody tr').count(),3,'保存草稿创建或启动了执行任务'); await goto(p,'new'); assert.equal(await p.locator('#title').inputValue(),'QA 包装袋草稿');
    return {draft:'QA 包装袋草稿',quantity:1000,persistedAfterRefresh:true};
  });
  await suite(browser,'闭环 / 保温杯采集、退回重审、AI缺项追问、报价选择与快照',async p=>{
    const id=await createTask(p,{template:'cup',title:'QA 完整保温杯采购',targetQuotes:2});
    await contains(p.locator('main'),'待复核计划'); await contains(p.locator('main'),'100 件');
    await collectTask(p,id); assert.equal(await p.locator('.candidate-card').count(),3); await contains(p.locator('.candidate-grid'),'温度器物'); assert(!(await p.locator('.candidate-grid').innerText()).includes('T 恤'),'保温杯混入T恤');
    await action(p,'candidate-detail').first().click();await contains(p.locator('.ui-panel'),'商品与来源');await p.keyboard.press('Escape');assert.equal(await p.locator('#app').getAttribute('inert'),null);
    await readyInquiries(p,id); await action(p,'confirm-send').click(); assert.equal(await p.locator('.ui-overlay').count(),1,'未勾选仍发出');
    const original=await p.locator('[data-review-text]').first().inputValue(); assert(!original.includes('45'),'询价泄露内部预算45');
    await p.locator('[data-review-check]').first().check(); await p.locator('[data-review-text]').first().fill(original+' 请明确激光位置。');assert.equal(await p.locator('[data-review-check]').first().isChecked(),false,'编辑正文未使确认失效');
    await action(p,'reject-inquiry').first().click();await action(p,'confirm-reject').click();await contains(p.locator('#reject-error'),'退回原因');await p.locator('#reject-reason').fill('请补充礼盒颜色后重审');await action(p,'confirm-reject').click();await contains(p.locator('.chat-body'),'请补充礼盒颜色后重审');
    await action(p,'review-inquiries').click();await p.locator('[data-review-text]').first().fill(original+' 礼盒为白色。');await sendChecked(p);
    await contains(p.locator('.chat-header'),'等待回复');assert.equal(await p.locator('.chat-body .message.out').count(),1);await p.reload();assert.equal(await p.locator('.chat-body .message.out').count(),1,'刷新重复发送');
    await action(p,'load-reply').click();await contains(p.locator('.quote-inspector'),'运费待确认');await contains(p.locator('.quote-inspector .total'),'待确认');
    await action(p,'followup').click();const followText=await p.locator('.ui-panel .review-card').innerText();assert(followText.includes('本批运费')&&followText.includes('是否含税'));assert(!followText.includes('请提供含税单价'),'追问重复全部已答问题');
    await action(p,'send-followup').click();assert.equal(await p.locator('.ui-overlay').count(),1,'追问未经确认仍发送');await p.locator('#followup-confirm').check();await action(p,'send-followup').click();await contains(p.locator('.chat-header'),'等待补充回复');await action(p,'load-reply').click();await contains(p.locator('.quote-inspector'),'同口径 · 可比较');await snapshot(p,'chat-complete');
    await chooseConv(p,id,'日常杯业');await action(p,'load-reply').click();await contains(p.locator('.quote-inspector'),'同口径 · 可比较');
    await chooseConv(p,id,'归山金属制品');await action(p,'load-reply').click();await contains(p.locator('.quote-inspector'),'未达到起订量');
    await goto(p,'quotes/'+id);await contains(p.locator('.quote-summary'),'2 份符合比较条件');assert.equal(await p.locator('tr').filter({hasText:'归山金属制品'}).locator('[data-action="select-quote"]').isDisabled(),true);
    await snapshot(p,'quotes-ready');const chosen=p.locator('tr').filter({hasText:'日常杯业'});await chosen.locator('[data-action="select-quote"]').click();await action(p,'confirm-selection').click();await contains(p.locator('#selection-error'),'选择理由');await p.locator('#selection-reason').fill('=QA 交期更快，费用条件完整');await p.locator('#selection-confirm').check();await action(p,'confirm-selection').click();await contains(p.locator('main'),'已保存采购建议');
    await action(p,'saved-selection').click();await contains(p.locator('.ui-panel'),'完整结果');await contains(p.locator('.ui-panel'),'¥3,760.00');await p.keyboard.press('Escape');
    await editQuote(p,id,'日常杯业',{unitPrice:40});await action(p,'saved-selection').click();await contains(p.locator('.ui-panel'),'当前报价已有变化');await contains(p.locator('.ui-panel'),'¥3,760.00');await p.keyboard.press('Escape');await p.reload();
    const csv=await saveDownload(p,action(p,'export-selection'),'selection.csv');assert(csv.text.includes('3760'),'快照导出被当前报价更改');assert(csv.text.includes("'=QA"),'CSV公式未转义');
    const quotes=await saveDownload(p,action(p,'export-quotes'),'quotes.csv');assert(quotes.text.includes('日常杯业')&&quotes.text.includes('未达到起订量'));
    await goto(p,'task/'+id);await contains(p.locator('main'),'已完成');assert.equal(await action(p,'pause').count(),0);assert.equal(await action(p,'stop').count(),0);
    await goto(p,'conversations/AD-260911-001');await contains(p.locator('.chat-header'),'织序服饰工厂');assert(!(await p.locator('.chat-body').innerText()).includes('礼盒为白色'),'跨任务消息串联');
    return {task:id,candidates:3,validQuotes:2,savedTotal:3760,csv:csv.filename,quoteCsv:quotes.filename,taskIsolation:true};
  });
  await suite(browser,'品类 / 包装袋与部分结果确认',async p=>{
    const id=await createTask(p,{template:'bag',title:'QA 包装袋采购',targetQuotes:3});await collectTask(p,id);await contains(p.locator('.candidate-grid'),'方寸包装');assert(!(await p.locator('.candidate-grid').innerText()).includes('T 恤'));
    await readyInquiries(p,id,true);await sendChecked(p,true);await action(p,'load-reply').click();await action(p,'followup').click();await p.locator('#followup-confirm').check();await action(p,'send-followup').click();await action(p,'load-reply').click();await goto(p,'quotes/'+id);await contains(p.locator('main'),'¥845.00');await action(p,'select-quote').click();await contains(p.locator('.ui-panel'),'当前仅 1 份');await p.locator('#selection-reason').fill('本轮只收到一家完整回复，先保存部分结果');await action(p,'confirm-selection').click();await contains(p.locator('#selection-error'),'明确确认');await p.locator('#selection-confirm').check();await action(p,'confirm-selection').click();await action(p,'saved-selection').click();await contains(p.locator('.ui-panel'),'部分结果');
    return {category:'包装袋',validQuotes:1,targetQuotes:3,total:845,outcome:'部分结果'};
  });
  await suite(browser,'未知品类 / 空状态、手工来源、安全文本与低单价精度',async p=>{
    const id=await createTask(p,{title:'QA 工业垫片',spec:'不锈钢 M8 垫片，平面无毛刺',quantity:10000,budget:1,category:'auto',targetQuotes:1});await collectTask(p,id);assert.equal(await p.locator('.candidate-card').count(),0);await contains(p.locator('main'),'没有匹配的示例商品');assert(!(await p.locator('main').innerText()).includes('织序'));
    await snapshot(p,'unknown-empty');await action(p,'manual-candidate').first().click();await action(p,'save-candidate').click();await contains(p.locator('#manual-error'),'请填写');
    const name='<img src=x onerror=alert(1)> 垫片厂';await p.locator('#manual-name').fill(name);await p.locator('#manual-contact').fill('=采购接待');await p.locator('#manual-price').fill('0.035');await p.locator('#manual-url').fill('javascript:alert(1)');await action(p,'save-candidate').click();await contains(p.locator('#manual-error'),'http://');
    await p.locator('#manual-url').fill('https://example.com/product/washer');await action(p,'save-candidate').click();await contains(p.locator('.candidate-grid'),name);assert.equal(await p.locator('.candidate-card img').count(),0,'用户文本被当HTML解析');
    await action(p,'prepare-inquiries').click();await sendChecked(p);await action(p,'load-reply').click();await contains(p.locator('.ui-panel'),'记录供应商报价');
    for(const [key,val]of Object.entries({unitPrice:'0.035',processing:'0',packaging:'0',shipping:'0',fixed:'0',moq:'1',leadDays:'3',leadStartDate:new Date().toLocaleDateString('en-CA',{timeZone:'Asia/Shanghai'}),validUntil:'2027-01-01',leadBasis:'规格确认后',original:'=供应商说明：10000个×0.035，全部费用含税且运费免费，规格确认后3天到货。'}))await p.locator('#quote-'+key).fill(val);
    await p.locator('#quote-taxBasis').selectOption('含税');await p.locator('#quote-leadMilestone').selectOption('到货');await p.locator('#quote-unit').selectOption('件');await p.locator('#quote-destination').fill('杭州');await action(p,'save-quote').click();await goto(p,'quotes/'+id);await contains(p.locator('.quote-summary'),'¥350.00');await contains(p.locator('tbody'),'0.035');
    const csv=await saveDownload(p,action(p,'export-quotes'),'manual-precision.csv');assert(csv.text.includes('0.035'));assert(csv.text.includes("'=供应商说明"),'原话CSV公式未转义');assert(!blockedRequests.length);
    return {task:id,unknownCategory:true,manualCandidate:name,preciseTotal:350,csv:csv.filename};
  });
  await suite(browser,'控制 / 暂停、接管、交还、停止与任务隔离',async p=>{
    const id='AD-260911-003';await goto(p,'task/'+id);await action(p,'pause').click();await contains(p.locator('.recovery-box'),'已暂停');assert.equal(await action(p,'start').count(),0,'暂停仍显示启动采集');await goto(p,'candidates/'+id);await action(p,'manual-candidate').first().click();assert.equal(await p.locator('.ui-overlay').count(),0,'暂停时仍新增候选');await goto(p,'task/'+id);
    await action(p,'resume').first().click();await action(p,'confirm-resume').click();assert.equal(await p.locator('.ui-overlay').count(),1);await p.locator('#resume-confirm').check();await action(p,'confirm-resume').click();await contains(p.locator('main'),'待复核计划');await collectTask(p,id);await readyInquiries(p,id,true);await sendChecked(p,true);
    await action(p,'takeover').click();await contains(p.locator('.recovery-box'),'控制权在采购人员手中');assert.equal(await action(p,'load-reply').isDisabled(),true);await p.reload();assert.equal(await action(p,'load-reply').isDisabled(),true);
    await action(p,'handback').first().click();await p.locator('#resume-confirm').check();await action(p,'confirm-handback').click();assert.equal(await action(p,'load-reply').isEnabled(),true);assert.equal(await p.locator('.message.out').count(),1,'交还重放首次发送');
    await goto(p,'task/'+id);await action(p,'stop').click();await contains(p.locator('.ui-panel'),'已停止是本次执行终态');await action(p,'confirm-stop').click();await contains(p.locator('.recovery-box'),'已停止');await goto(p,'conversations/'+id);assert.equal(await action(p,'load-reply').isDisabled(),true);await p.reload();await contains(p.locator('.recovery-box'),'已停止');
    await goto(p,'task/AD-260911-001');assert.equal(await action(p,'stop').isEnabled(),true,'停止一个任务影响其他任务');
    return {paused:true,takeover:true,handbackRetainedOneMessage:true,stoppedTerminalAfterRefresh:true};
  });
  await suite(browser,'异常 / 发送未知、回查已发与未发、不盲重试',async p=>{
    const id='AD-260911-002';await goto(p,'task/'+id);await applyScenario(p,'unknown');await readyInquiries(p,id,true);await sendChecked(p,true);await contains(p.locator('.chat-header'),'发送结果未知');assert.equal(await action(p,'load-reply').count(),0);await snapshot(p,'unknown-send');await p.reload();await contains(p.locator('.chat-header'),'发送结果未知');assert.equal(await p.locator('.message.out').count(),1);
    await action(p,'reconcile').first().click();await action(p,'reconcile-unsent').click();await contains(p.locator('.chat-header'),'待确认发送');await action(p,'review-inquiries').click();assert.equal(await p.locator('[data-review-check]').first().isChecked(),false);await p.keyboard.press('Escape');
    await applyScenario(p,'unknown');await action(p,'review-inquiries').click();await sendChecked(p,true);await action(p,'reconcile').first().click();await action(p,'reconcile-sent').click();await contains(p.locator('.chat-header'),'等待回复');assert.equal(await p.locator('.message.out').count(),2);await p.reload();assert.equal(await p.locator('.message.out').count(),2);await contains(p.locator('.chat-body'),'回查未发送');await contains(p.locator('.chat-body'),'回查已发送');
    return {firstAttempt:'人工回查未发送',secondAttempt:'示例回查已发送',recordsAfterReload:2};
  });
  await suite(browser,'环境 / 离线与登录失效恢复、只读说明',async p=>{
    const id='AD-260911-003';for(const scenario of ['offline','auth','readonly']){await goto(p,'task/'+id);await applyScenario(p,scenario);await action(p,'start').click();assert.equal(await p.locator('.loading-progress').count(),0,'受限环境启动采集');await contains(p.locator('.recovery-box'),'执行等待处理');await goto(p,'connections');await contains(p.locator('main'),'未真实接入');
      if(scenario==='offline')await action(p,'machine-toggle').click();if(scenario==='auth')await action(p,'login-toggle').click();await p.locator('[data-action="repair-task"][data-task="'+id+'"]').click();await goto(p,'task/'+id);assert.equal(await p.locator('.recovery-box').count(),0,'修复后异常仍未清除');await contains(p.locator('main'),'待复核计划');}
    return {offline:'恢复演示在线后核验任务',auth:'完成演示登录核验后恢复',readonly:'显式本地能力修复',realConnections:0};
  });
  await suite(browser,'人工 / AI模板循环、订金要求与询价截止',async p=>{
    const id='AD-260911-001';await goto(p,'conversations/'+id);await applyScenario(p,'loop');await contains(p.locator('.chat-header'),'需要人工处理');assert.equal(await action(p,'followup').isDisabled(),true);await contains(p.locator('.chat-body'),'具体价格请看商品页面');
    await applyScenario(p,'deposit');await contains(p.locator('.chat-body'),'请先支付 500 元订金');assert.equal(await action(p,'followup').isDisabled(),true);await goto(p,'task/'+id);await contains(p.locator('main'),'未作任何承诺');
    await applyScenario(p,'timeout');await contains(p.locator('main'),'询价已截止');await goto(p,'quotes/'+id);await contains(p.locator('main'),'1 份符合比较条件');
    return {loop:'人工处理',deposit:'保留原话且未承诺',timeout:'保留既有报价'};
  });
  await suite(browser,'检索 / 空状态、规则仅影响新任务、键盘弹窗',async p=>{
    await goto(p,'tasks');await p.getByRole('textbox',{name:'搜索任务'}).fill('QA不存在的项目');await contains(p.locator('main'),'没有匹配的任务');
    await goto(p,'suppliers');await p.getByRole('textbox',{name:'搜索供应商'}).fill('QA不存在供应商');await contains(p.locator('main'),'没有找到供应商');
    await goto(p,'rules');await p.locator('#rule-followup').uncheck();await p.locator('#rule-rounds').selectOption('1');await action(p,'save-rules').click();await goto(p,'new');assert.equal(await p.locator('[data-draft="followup"]').isChecked(),false);await goto(p,'task/AD-260911-001');await contains(p.locator('main'),'已授权 · 最多 2 轮');
    await action(p,'scenario').click();assert.equal(await p.locator('#app').getAttribute('inert'),'');await p.keyboard.press('Escape');assert.equal(await p.locator('.ui-overlay').count(),0);assert.equal(await p.locator('#app').getAttribute('inert'),null);
    return {taskSearchEmpty:true,supplierSearchEmpty:true,newDefaultOnly:true,escapeClosed:true};
  });
  await suite(browser,'报价 / 可见费用缺项、币种、MOQ、预算、有效期与交期条件',async p=>{
    const id='AD-260911-001',supplier='白川制衣';const cases=[
      [{shipping:''},'运费待确认'],[{shipping:60,currency:'USD'},'币种不一致'],[{currency:'CNY',moq:1000},'未达到起订量'],[{moq:100,unitPrice:100},'超过含税到货预算上限'],[{unitPrice:25.2,validUntil:'2020-01-01'},'报价已过期'],[{validUntil:'2027-01-01',leadMilestone:'出货',transitDays:''},'交期起算或运输条件待确认'],[{transitDays:30},'预计到货超过最晚交货要求'],[{transitDays:2,quantity:299},'数量口径不一致']
    ];const checked=[];for(const [values,reason] of cases){await editQuote(p,id,supplier,values);const row=p.locator('tr').filter({hasText:supplier});await contains(row,reason);assert.equal(await row.locator('[data-action="select-quote"]').isDisabled(),true);checked.push({input:values,visibleReason:reason});}
    await editQuote(p,id,supplier,{quantity:300,leadMilestone:'到货',transitDays:''});await contains(p.locator('tr').filter({hasText:supplier}),'同口径可比较');return checked;
  });
  await suite(browser,'自然语言 / 明确字段提取、原文保留与未知不补造',async p=>{
    await goto(p,'new');await action(p,'parse-natural').click();await contains(p.locator('#natural-error'),'先写下');await action(p,'natural-example').click();assert.equal(await p.locator('#quantity').inputValue(),'300');assert.equal(await p.locator('#budget').inputValue(),'30');assert.equal(await p.locator('#destination').inputValue(),'杭州');assert.equal(await p.locator('#category').inputValue(),'tshirt');await contains(p.locator('.natural-entry'),'本地规则演示');assert((await p.locator('#natural-request').inputValue()).includes('数量 300 件'));
    const unknown='帮我找些实验器材，总预算200元，差不多下周交付。';await p.locator('#natural-request').fill(unknown);await action(p,'parse-natural').click();for(const field of ['quantity','budget','spec','destination','deadline','enquiryDeadline'])assert.equal(await p.locator('#'+field).inputValue(),'','未知字段被补造：'+field);assert.equal(await p.locator('#category').inputValue(),'other');await contains(p.locator('.natural-entry'),'待您填写确认');await p.reload();assert.equal(await p.locator('#natural-request').inputValue(),unknown);await p.getByRole('button',{name:'复核采购计划',exact:false}).click();assert((await p.locator('#form-errors').innerText()).length>0,'未知需求绕过结构化校验');await snapshot(p,'natural-unknown');return {known:{quantity:300,budget:30,destination:'杭州'},unknownRetained:unknown,missingFieldsRemainEmpty:true};
  });
  await suite(browser,'直开 / file协议加载、草稿保存与刷新',async p=>{
    const url=pathToFileURL(path.join(__dirname,'index.html')).href;await p.goto(url);await p.locator('main').waitFor();await contains(p.locator('main'),'采购工作台');await action(p,'new').click();await p.locator('[data-template="cup"]').click();await p.locator('#title').fill('QA 文件直开采购草稿');await action(p,'save-draft').click();await p.reload();assert.equal(await p.locator('#title').inputValue(),'QA 文件直开采购草稿');assert.equal(await p.locator('#quantity').inputValue(),'100');await snapshot(p,'file-direct-new');return {url,title:await p.title(),persistedAfterRefresh:true};
  });
}
(async()=>{fs.mkdirSync(output,{recursive:true});let browser;try{await arithmetic();browser=await chromium.launch({headless:true,executablePath});environment.chromium=browser.version();await layouts(browser);await flows(browser);if(pagesVisited)await check('运行时 / JavaScript 与外部请求',()=>{assert.equal(runtimeErrors.length,0,JSON.stringify(runtimeErrors));assert.equal(blockedRequests.length,0,JSON.stringify(blockedRequests));return {pagesVisited,runtimeErrors,blockedRequests};});environment.sourceFilesAtEnd=hashes();await check('受验版本 / 源码哈希稳定',()=>{assert.deepEqual(environment.sourceFilesAtEnd,environment.sourceFilesAtStart,'验证期间应用源码变化，需要在最终稳定版本复验');return environment.sourceFilesAtEnd;});}catch(error){results.push({label:'验证执行器',status:'Fail',error:error.stack});}finally{if(browser)await browser.close();report();}})();
