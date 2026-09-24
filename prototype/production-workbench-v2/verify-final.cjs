/* 最终差异复验：数量标签、订单入口、执行图标与统一入口。 */
const fs = require('fs');
const path = require('path');
const {pathToFileURL} = require('url');
const {chromium} = require('C:/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const output = path.join(__dirname, 'output', 'playwright');
const results = [], errors = [];
function assert(value, message) { if (!value) throw new Error(message); }
async function check(label, fn) { try { results.push({label, result:'Pass', detail:await fn()}); console.log('PASS '+label); } catch(error) { results.push({label, result:'Fail', error:error.message}); console.log('FAIL '+label+' '+error.message); } }
(async () => {
  const browser = await chromium.launch({headless:true, executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
  const context = await browser.newContext({viewport:{width:1440,height:1000}, locale:'zh-CN', timezoneId:'Asia/Shanghai', reducedMotion:'reduce'});
  const page = await context.newPage(); page.setDefaultTimeout(7000); page.on('pageerror', e => errors.push(e.message));
  try {
    await page.goto(pathToFileURL(path.join(__dirname,'index.html')).href); await page.waitForSelector('.task-bar');
    await check('同炉设备条显示整批200件', async () => {
      const text = await page.locator('.task-bar[data-id="A20"][data-segment-start="330"]').innerText();
      assert(text.includes('200件'),text); return {text};
    });
    await check('订单交期入口打开对应详情', async () => {
      await page.locator('[data-action="cat-order-detail"][data-id="O-100"]').click();
      const title = await page.locator('#ui-panel-title').innerText(); assert(title.includes('O-100'),title);
      await page.keyboard.press('Escape'); return {title};
    });
    await page.locator('[data-action="gantt-mode"][data-id="person"]').click();
    await check('连续转移三段各显示20件', async () => {
      const labels = await page.locator('.task-bar[data-id="T20"]').allInnerTexts();
      assert(labels.length===3 && labels.every(t=>t.includes('20件')),JSON.stringify(labels));
      await page.waitForFunction(()=>!document.querySelector('.ui-toast'));
      await page.screenshot({path:path.join(output,'workbench-person-final.png'),fullPage:true}); return {labels};
    });
    await page.locator('[data-action="gantt-mode"][data-id="machine"]').click();
    await check('最终桌面和窄屏工作台截图',async()=>{
      const layouts=[];
      for(const viewport of [{width:1440,height:1000},{width:390,height:844}]){
        await page.setViewportSize(viewport);await page.waitForFunction(()=>!document.querySelector('.ui-toast'));
        const info=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth,icons:document.querySelectorAll('i[data-lucide]').length}));
        assert(info.scroll<=info.width+1&&!info.icons,'溢出或图标异常');
        await page.screenshot({path:path.join(output,'workbench-final-'+viewport.width+'.png'),fullPage:true});layouts.push(info);
      }return layouts;
    });
    await page.setViewportSize({width:1440,height:1000});
    await check('已执行任务抽屉图标正常渲染',async()=>{
      const start=await page.evaluate(()=>Engine.report(A.state,'A10','start'));assert(start.ok,start.message);
      await page.locator('[data-action="task-detail"][data-id="A10"]').first().click();
      assert(await page.locator('.ui-drawer i[data-lucide]').count()===0,'锁定图标未渲染');
      assert(await page.locator('#task-start').isDisabled(),'已开工应禁用调整');await page.keyboard.press('Escape');return start;
    });
    await check('三项目入口连接排程V2并保持其他项目入口',async()=>{
      await page.goto(pathToFileURL(path.join(__dirname,'../business-suite-v1/index.html')).href+'#scheduling');
      await page.frameLocator('#prototype-frame').locator('.task-bar').first().waitFor();
      assert((await page.locator('#prototype-frame').getAttribute('src')).includes('production-workbench-v2'),'未路由新版');
      await page.locator('[data-project="fashion"]').click();
      await page.waitForFunction(()=>document.querySelector('#prototype-frame').getAttribute('src')==='03-ai-fashion-styling-quotation/index.html');
      assert((await page.locator('#prototype-frame').getAttribute('src'))==='03-ai-fashion-styling-quotation/index.html','服装入口变化');
      await page.locator('[data-project="aden"]').click();
      await page.waitForFunction(()=>document.querySelector('#prototype-frame').getAttribute('src')==='02-agent-desktop-execution/index.html');
      assert((await page.locator('#prototype-frame').getAttribute('src'))==='02-agent-desktop-execution/index.html','Agent入口变化');
      await page.locator('[data-project="scheduling"]').click();await page.frameLocator('#prototype-frame').locator('.task-bar').first().waitFor();
      return {src:await page.locator('#prototype-frame').getAttribute('src')};
    });
    await check('最终差异运行无页面异常',async()=>{assert(!errors.length,errors.join('\n'));return {errors};});
  }finally{
    fs.writeFileSync(path.join(output,'final-results.json'),JSON.stringify({at:new Date().toISOString(),browser:browser.version(),results},null,2));await browser.close();
  }
  if(results.some(r=>r.result==='Fail'))process.exitCode=1;
})().catch(error=>{console.error(error);process.exitCode=1;});
