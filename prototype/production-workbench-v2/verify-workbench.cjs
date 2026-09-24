const fs=require('fs'),path=require('path');
const {pathToFileURL}=require('url');
const {chromium}=require('C:/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const url=pathToFileURL(path.join(__dirname,'index.html')).href;
const output=path.join(__dirname,'output','playwright');fs.mkdirSync(output,{recursive:true});
const results=[],errors=[];
function assert(v,m){if(!v)throw new Error(m);}
async function check(label,fn){try{const detail=await fn();results.push({label,result:'Pass',detail});console.log('PASS '+label);}catch(error){results.push({label,result:'Fail',error:error.message});console.log('FAIL '+label+' '+error.message);}}
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
 const context=await browser.newContext({viewport:{width:1440,height:1000},locale:'zh-CN',timezoneId:'Asia/Shanghai',reducedMotion:'reduce',acceptDownloads:true});
 const page=await context.newPage();page.setDefaultTimeout(6000);page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(url);await page.waitForSelector('.task-bar');
  await check('任务详情锁定及撤销恢复',async()=>{
   await page.locator('[data-action="task-detail"][data-id="A10"]').first().click();await page.locator('#task-lock').selectOption('all');await page.locator('[data-action="preview-task"]').click();await page.locator('[data-action="apply-adjustment"]').click();
   assert(await page.evaluate(()=>A.state.tasks.find(t=>t.id==='A10').lock)==='all','锁定未生效');
   await page.locator('[data-action="undo-plan"]').click();assert(await page.evaluate(()=>A.state.tasks.find(t=>t.id==='A10').lock)==='none','撤销未恢复锁定配置');
   return {lock:'none',start:await page.evaluate(()=>A.state.draft.assignments.find(a=>a.taskId==='A10').start)};
  });
  await check('解除锁定与调整保持基线',async()=>{
   await page.locator('[data-action="gantt-mode"][data-id="person"]').click();
   await page.locator('[data-action="task-detail"][data-id="F10"]').first().click();await page.locator('#task-lock').selectOption('all');await page.locator('[data-action="preview-task"]').click();await page.locator('[data-action="apply-adjustment"]').click();
   await page.locator('[data-action="task-detail"][data-id="F10"]').count().then(async n=>{if(n)await page.locator('[data-action="task-detail"][data-id="F10"]').first().click();else{await page.locator('[data-action="gantt-mode"][data-id="person"]').click();await page.locator('[data-action="task-detail"][data-id="F10"]').first().click();}});
   await page.locator('#task-lock').selectOption('none');await page.locator('[data-action="preview-task"]').click();await page.locator('[data-action="apply-adjustment"]').click();assert(await page.evaluate(()=>A.state.tasks.find(t=>t.id==='F10').lock)==='none','未解除锁定');assert(await page.evaluate(()=>A.state.published.id)==='V01','正式版本不应被调整');
  });
  await check('多个标签页禁止覆盖最新本地计划',async()=>{
   const second=await context.newPage();await second.goto(url);await second.waitForSelector('.task-bar');
   const before=await second.evaluate(()=>A.state._writeToken);await page.locator('[data-action="run-plan"]').first().click();await page.waitForFunction(()=>!A.planning);
   const latest=await page.evaluate(()=>A.state._writeToken);assert(latest!==before,'首页面未写入新版本');
   await second.locator('[data-action="run-plan"]').first().click();assert((await second.locator('#ui-panel-title').innerText()).includes('其他页面'),'陈旧页面未阻止');
   assert(await second.evaluate(()=>JSON.parse(localStorage.getItem('business-prototype-v1:production-workbench-v2.0'))._writeToken)===latest,'新版本被覆盖');
   await second.locator('[data-action="reload-storage"]').click();assert(await second.evaluate(()=>A.state._writeToken)===latest,'未载入新版本');await second.close();return {protected:true};
  });
  await page.locator('[data-action="gantt-mode"][data-id="machine"]').click();
  for(const viewport of [{width:1440,height:1000},{width:390,height:844}]){
   await page.setViewportSize(viewport);
   await check('工作台最终布局 '+viewport.width,async()=>{
    const bounds=await page.evaluate(()=>({scroll:document.documentElement.scrollWidth,width:innerWidth,toolbar:[...document.querySelectorAll('.gantt-toolbar button,.gantt-toolbar select')].map(el=>{const a=el.getBoundingClientRect(),b=el.closest('.gantt-toolbar').getBoundingClientRect();return {label:el.getAttribute('aria-label')||el.textContent.trim(),within:a.left>=b.left-1&&a.right<=b.right+1};}),icons:[...document.querySelectorAll('i[data-lucide]')].map(i=>i.dataset.lucide)}));
    assert(bounds.scroll<=bounds.width+1,'整体横向溢出');assert(bounds.toolbar.every(t=>t.within),'工具栏控件被裁切 '+JSON.stringify(bounds.toolbar));assert(!bounds.icons.length,'未渲染图标');
    await page.screenshot({path:path.join(output,'workbench-final-'+viewport.width+'.png'),fullPage:true});return bounds;
   });
  }
  await check('窄屏抽屉与键盘关闭',async()=>{
   await page.locator('[data-action="task-detail"][data-id="A10"]').first().click();
   const panel=await page.locator('.ui-drawer').boundingBox();assert(panel.x>=0&&panel.x+panel.width<=391,'抽屉超出屏幕');
   await page.screenshot({path:path.join(output,'task-drawer-mobile.png'),fullPage:true});await page.keyboard.press('Escape');assert(await page.locator('.ui-overlay').count()===0,'Escape未关闭');
  });
  await check('新增逻辑运行异常',async()=>{assert(!errors.length,errors.join('\n'));return {errors};});
 }finally{fs.writeFileSync(path.join(output,'workbench-results.json'),JSON.stringify({at:new Date().toISOString(),results},null,2));await browser.close();}
 if(results.some(r=>r.result==='Fail'))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});
