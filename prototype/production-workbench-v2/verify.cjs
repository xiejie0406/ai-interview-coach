/* 本地浏览器验收：使用隔离上下文，不接触用户账号或真实业务服务。 */
const fs = require('fs');
const path = require('path');
const {pathToFileURL}=require('url');
const {chromium}=require(path.join(process.env.PROTOTYPE_NODE_MODULES||'C:/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules','playwright'));
const output=path.join(__dirname,'output','playwright');
fs.mkdirSync(output,{recursive:true});
const url=pathToFileURL(path.join(__dirname,'index.html')).href;
const views=['schedule','orders','routes','resources','people','batches','conflicts','execution','daily','capacity','versions','audit'];
const results=[],errors=[];
function assert(value,message){if(!value)throw new Error(message);}
async function check(label,fn){try{const detail=await fn();results.push({label,result:'Pass',detail});console.log('PASS '+label);}catch(error){results.push({label,result:'Fail',error:error.message});console.log('FAIL '+label+': '+error.message);}}
async function audit(page,name,screenshot=true){
  await page.waitForTimeout(100);
  const data=await page.evaluate(()=>({title:document.title,heading:document.querySelector('main h1')?.textContent,text:document.querySelector('main')?.innerText.length,width:innerWidth,scroll:document.documentElement.scrollWidth,icons:[...document.querySelectorAll('i[data-lucide]')].map(i=>i.dataset.lucide),errors:document.querySelector('.app-error')?.innerText,bars:document.querySelectorAll('.task-bar').length}));
  assert(data.text>60,'主内容为空');assert(!data.errors,data.errors);assert(data.scroll<=data.width+1,`整体横向溢出 ${data.scroll}/${data.width}`);assert(!data.icons.length,'未渲染图标 '+data.icons.join(','));
  if(screenshot)await page.screenshot({path:path.join(output,name+'.png'),fullPage:true,animations:'disabled'});
  return data;
}
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:process.env.PROTOTYPE_BROWSER||'C:/Program Files/Google/Chrome/Application/chrome.exe'});
  const context=await browser.newContext({viewport:{width:1440,height:1000},locale:'zh-CN',timezoneId:'Asia/Shanghai',reducedMotion:'reduce',acceptDownloads:true});
  const page=await context.newPage();page.setDefaultTimeout(7000);
  page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
  try{
    await page.goto(url);await page.waitForSelector('.nav-item');
    await check('首单工艺及人机计算',async()=>{
      const data=await page.evaluate(()=>({issues:Engine.validate(A.state,A.state.draft.assignments),order:Engine.metrics(A.state,A.state.draft.assignments).orders.find(o=>o.id==='O-100'),transfer:A.state.draft.assignments.find(a=>a.taskId==='T20')}));
      assert(!data.issues.some(i=>i.severity==='error'),JSON.stringify(data.issues));assert(data.order.end===1500,'首单应9/15 09:00完成');assert(data.transfer.segments.filter(s=>s.kind==='run').length===3,'转移批应3个分段');return data;
    });
    for(const view of views){await page.locator(`.nav-item[href="#${view}"]`).click();await check('桌面 / '+view,()=>audit(page,'desktop-'+view));}
    await page.locator('.nav-item[href="#schedule"]').click();
    for(const mode of ['machine','person','workshop']){await page.locator(`[data-action="gantt-mode"][data-id="${mode}"]`).click();await check('甘特视角 / '+mode,async()=>{const info=await audit(page,'gantt-'+mode);assert(info.bars>0,'甘特任务为空');return info;});}
    await check('任务抽屉与前置冲突拒绝',async()=>{
      await page.locator('[data-action="task-detail"][data-id="B10"]').first().click();await page.locator('#task-start').fill('2026-09-14T08:00');await page.locator('[data-action="preview-task"]').click();
      const message=await page.locator('#task-form-error').innerText();assert(message.length>0,'应拒绝前置未完成的调整');assert(await page.evaluate(()=>A.state.draft.assignments.find(a=>a.taskId==='B10').start)===120,'非法调整不得改变候选');await page.keyboard.press('Escape');return {message};
    });
    await check('锁定与重排',async()=>{
      await page.locator('[data-action="task-detail"][data-id="A10"]').first().click();await page.locator('#task-lock').selectOption('all');await page.locator('[data-action="preview-task"]').click();await page.locator('[data-action="apply-adjustment"]').click();
      await page.locator('[data-action="run-plan"]').first().click();await page.waitForFunction(()=>!A.planning);
      const data=await page.evaluate(()=>({task:A.state.tasks.find(t=>t.id==='A10'),plan:A.state.draft.assignments.find(a=>a.taskId==='A10'),issues:Engine.validate(A.state,A.state.draft.assignments)}));
      assert(data.task.lock==='all','锁定未保存');assert(data.plan.start===0&&data.plan.machineId==='M1'&&data.plan.personId==='P1','重排改变锁定任务');assert(!data.issues.some(i=>i.severity==='error'),'重排存在冲突');return {lock:data.task.lock,start:data.plan.start};
    });
    await check('发布确认与版本持久化',async()=>{
      await page.locator('[data-action="review-publish"]').click();await page.locator('[data-action="confirm-publish"]').click();assert((await page.locator('#publish-error').innerText()).length>0,'缺说明/确认应阻止');
      await page.locator('#publish-note').fill('浏览器验收：人员设备与工序依赖已复核');await page.locator('#publish-confirm').check();await page.locator('[data-action="confirm-publish"]').click();
      const version=await page.evaluate(()=>A.state.published.id);assert(version==='V02','应发布V02');await page.reload();await page.waitForSelector('.nav-item');assert(await page.evaluate(()=>A.state.published.id)===version,'刷新未保持版本');return {version};
    });
    await page.locator('.nav-item[href="#daily"]').click();await check('日报实际CSV下载',async()=>{const wait=page.waitForEvent('download');await page.locator('[data-action="ops-daily-csv"]').click();const download=await wait;const file=path.join(output,'daily.csv');await download.saveAs(file);const text=fs.readFileSync(file,'utf8');assert(text.includes('计划')&&text.includes('车间'),'CSV内容不完整');return {bytes:Buffer.byteLength(text)};});
    await page.locator('.nav-item[href="#capacity"]').click();await check('人力累计表数据与导出',async()=>{const wait=page.waitForEvent('download');await page.locator('[data-action="ops-capacity-csv"]').click();const download=await wait;await download.saveAs(path.join(output,'capacity.csv'));return audit(page,'capacity-detail');});
    await page.setViewportSize({width:390,height:844});
    for(const view of views){await page.locator('[data-toggle-nav]').click();await page.locator(`.nav-item[href="#${view}"]`).click();await check('窄屏 / '+view,()=>audit(page,'mobile-'+view));}
    await page.setViewportSize({width:768,height:1024});await page.locator('[data-toggle-nav]').click();await page.locator('.nav-item[href="#schedule"]').click();await check('平板排程',()=>audit(page,'tablet-schedule'));
    await page.setViewportSize({width:900,height:430});await check('横屏排程',()=>audit(page,'landscape-schedule'));
    await page.setViewportSize({width:1440,height:1000});await page.locator('.nav-item[href="#daily"]').click();await page.emulateMedia({media:'print'});await check('日报打印媒体',async()=>{await page.screenshot({path:path.join(output,'daily-print.png'),fullPage:true});return {heading:await page.locator('h1').innerText()};});await page.emulateMedia({media:'screen'});
    await check('浏览器运行异常',async()=>{assert(!errors.length,errors.join('\n'));return {errors};});
  }finally{
    fs.writeFileSync(path.join(output,'browser-results.json'),JSON.stringify({at:new Date().toISOString(),results,errors},null,2));await browser.close();
  }
  const failed=results.filter(r=>r.result==='Fail');console.log(JSON.stringify({pass:results.length-failed.length,fail:failed.length}));if(failed.length)process.exitCode=1;
})().catch(error=>{console.error(error);process.exitCode=1;});
