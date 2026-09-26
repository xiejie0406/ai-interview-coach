import { chromium } from 'playwright'
import { createServer } from 'node:http'
import { readFile, mkdir, mkdtemp, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { dirname, resolve, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'
import assert from 'node:assert/strict'

// 独立 Chromium + 安装版 Host 技术联调；显式合成 origin，不冒充京东。
export async function runExtensionProbe({ app, page, evidenceDir }) {
  const origin = process.env.ADEN_COLLECTION_FIXTURE_ORIGIN
  assert(origin, '缺少隔离夹具 origin')
  const extensionRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../../aden-collector-extension')
  const temp = await mkdtemp(join(tmpdir(), 'aden-collector-probe-'))
  const extension = join(temp, 'extension')
  execFileSync(process.execPath, [join(extensionRoot, 'scripts/prepare-test.mjs'), extension, origin])
  const image = await readFile(join(extensionRoot, 'fixtures/sample.png'))
  let html = await readFile(join(extensionRoot, 'fixtures/detail.html'), 'utf8')
  const fixtureSku=`8${Date.now()}`
  html=html.replaceAll('100000000000001',fixtureSku)
  html = html.replace('src="sample.png"', 'src="image-1.png"').replace('<aside>', `<div data-product-gallery>${Array.from({length:7},(_,i)=>`<img src="image-${i+2}.png">`).join('')}</div><aside>`)
  const requests = new Map()
  let failImages = true
  const server = createServer((req, res) => {
    const path = req.url.split('?')[0]
    if (path.endsWith('.png')) {
      const count = (requests.get(path) || 0) + 1; requests.set(path,count)
      if (failImages && /image-[78]\.png/.test(path) && count > 1) { res.writeHead(503);res.end('synthetic unavailable');return }
      res.writeHead(200, {'Content-Type':'image/png','Cache-Control':'no-store'});res.end(image)
    } else { res.writeHead(200, {'Content-Type':'text/html; charset=utf-8'});res.end(html.replace('src="sample.png" alt="合成详情图"','src="image-1.png" alt="合成详情图"')) }
  })
  await new Promise((resolveReady,reject)=>{server.once('error',reject);server.listen(Number(new URL(origin).port),'127.0.0.1',resolveReady)})
  let context
  const result = { synthetic:true, fixtureSku, interaction:'后台扩展面板DOM派发，源标签保持活跃；不代替Chrome原生侧栏人工验收', browser:'', profile:temp, events:[], screenshots:[], errors:[] }
  await mkdir(evidenceDir,{recursive:true})
  const persist=()=>writeFile(join(evidenceDir,'扩展闭环结果.json'),JSON.stringify(result,null,2))
  const stage=async(name)=>{
    result.stage=name;result.stageAt=new Date().toISOString()
    result.events.push({step:'stage',name,at:result.stageAt})
    await persist()
  }
  try {
    await stage('launch-chromium')
    context = await chromium.launchPersistentContext(join(temp,'profile'), {headless:false,
      ...(process.env.ADEN_COLLECTION_CHROMIUM ? { executablePath: process.env.ADEN_COLLECTION_CHROMIUM } : {}),
      args:[`--disable-extensions-except=${extension}`,`--load-extension=${extension}`],
      viewport:{width:1100,height:800},env:{...process.env}})
    context.setDefaultTimeout(15000)
    context.setDefaultNavigationTimeout(15000)
    result.browser=context.browser()?.version() || 'persistent Chromium'
    await stage('wait-extension-worker')
    const worker=context.serviceWorkers()[0] || await context.waitForEvent('serviceworker')
    const id = new URL(worker.url()).host
    assert.equal(id,'mnejmmlalapfhnanlnckfdhmfpbahidm')
    await stage('inspect-worker-api')
    let workerApis
    for(let attempt=0;attempt<50;attempt++){
      workerApis=await worker.evaluate(()=>({
        runtimeKeys:Object.keys(globalThis.chrome?.runtime||{}),
        connectNative:typeof globalThis.chrome?.runtime?.connectNative,
        storageSession:typeof globalThis.chrome?.storage?.session?.get,
      })).catch(error=>({evaluateError:error.message}))
      if(workerApis.connectNative==='function'&&workerApis.storageSession==='function')break
      await new Promise(resolveReady=>setTimeout(resolveReady,100))
    }
    result.events.push({step:'workerApiReady',url:worker.url(),...workerApis})
    const runOfflineProbe=process.env.ADEN_COLLECTION_OFFLINE_PROBE==='1'&&workerApis.connectNative==='function'&&workerApis.storageSession==='function'
    if(process.env.ADEN_COLLECTION_OFFLINE_PROBE==='1'&&!runOfflineProbe)result.events.push({step:'offlineSpool',status:'Blocked',reason:'Worker自动化执行realm未提供Native API，跳过故障注入；主链路从真实面板继续',...workerApis})
    // 可选的明确故障注入仅位于 Native 传输边界。服务端与 IndexedDB 都保持真实。
    // 在第一份真实字节发出前报告本地断连，后续恢复必须真正上传到后端。
    if(runOfflineProbe) await worker.evaluate(()=>{
      globalThis.__adenProbeFault={dropNextChunk:false,injected:0}
      const connect=chrome.runtime.connectNative.bind(chrome.runtime)
      chrome.runtime.connectNative=(name)=>{
        const port=connect(name)
        const listeners=[]
        return {
          onDisconnect:port.onDisconnect,
          onMessage:{addListener(listener){listeners.push(listener);port.onMessage.addListener(listener)}},
          postMessage(message){
            if(globalThis.__adenProbeFault.dropNextChunk&&message.type==='asset.chunk'){
              globalThis.__adenProbeFault.dropNextChunk=false;globalThis.__adenProbeFault.injected++
              queueMicrotask(()=>listeners.forEach(listener=>listener({protocolVersion:1,messageId:message.messageId,ok:false,error:{code:'PROBE_TRANSPORT_DISCONNECTED',message:'技术测试故障注入：Native分片发送前断连'}})))
              return
            }
            port.postMessage(message)
          },
        }
      }
    })
    await stage('open-fixture-source')
    const source=await context.newPage()
    await source.goto(`${origin}/detail.html`)
    await source.locator('[data-product-gallery] img').last().waitFor()
    await source.waitForFunction(()=>Array.from(document.images).every(x=>x.complete))
    let navigations=0
    source.on('framenavigated', f=>{if(f===source.mainFrame())navigations++})
    await stage('open-extension-panel')
    const panel=await context.newPage()
    await panel.goto(`chrome-extension://${id}/panel.html`)
    // locator.click 会激活 panel 普通标签，导致 current() 解析扩展页；侧栏本身没有该问题。
    // 此独立技术探针用 DOM click 执行面板真实处理器，明确不验证浏览器 chrome 工具栏交互。
    await source.bringToFront()
    const click=async(selector)=>{
      await stage(`panel-action:${selector}`)
      return panel.locator(selector).evaluate(element=>{
        if(element.disabled)throw new Error(`按钮不可用：${element.id}`)
        element.click()
      })
    }
    const panelScreenshot=async(file)=>{
      await stage(`screenshot:${file}`)
      await panel.bringToFront()
      try { await panel.screenshot({path:join(evidenceDir,file),timeout:5000}) }
      finally {
        if(!source.isClosed()){
          await source.bringToFront()
          // 失焦遮蔽会清除 connected。直接执行真实连接处理器，保留重复确认对话框。
          await stage('reconnect-after-screenshot')
          await panel.locator('#connect').evaluate(async element=>{await element.onclick()})
          await panel.waitForFunction(()=>document.querySelector('#status').textContent.includes('已连接'),null,{timeout:65000,polling:100})
        }
      }
    }
    const waitCapture=async(previous)=>{
      await stage(`wait-capture-terminal:after-${previous||'none'}`)
      const deadline=Date.now()+120000
      let job=null
      while(Date.now()<deadline){
        job=await panel.evaluate(async()=> (await chrome.storage.session.get('job')).job)
        if(job?.captureId&&job.captureId!==previous&&job.running===false){
          if(!job.contentSaved||!job.result){
            result.captureWaitFailure={previous,job,panelStatus:await panel.locator('#status').innerText(),at:new Date().toISOString()}
            await persist()
          }
          assert.equal(job.contentSaved,true,job.message)
          assert(job.result,job.message)
          return job
        }
        await new Promise(resolvePoll=>setTimeout(resolvePoll,200))
      }
      result.captureWaitFailure={previous,job:job??null,panelStatus:await panel.locator('#status').innerText().catch(error=>`读取失败：${error.message}`),at:new Date().toISOString()}
      await persist()
      throw new Error(`采集在120秒内未形成新终态：${result.captureWaitFailure.panelStatus}`)
    }
    await click('#connect')
    await panel.waitForFunction(()=>document.querySelector('#status').textContent.includes('已连接'),null,{timeout:65000,polling:100})
    // 仅修改本地合成 DOM，不导航页面、不伪造后端响应。
    await source.evaluate(()=>document.querySelector('[data-current-sku]').setAttribute('data-current-sku','999999999999'))
    const mismatch=await panel.evaluate(()=>chrome.runtime.sendMessage({action:'preview'}))
    assert.equal(mismatch.ok,false,'当前规格SKU与页面SKU不同必须拒绝预览')
    await click('#preview')
    assert.match(mismatch.error,/SKU 不一致/)
    await panel.waitForFunction(()=>/SKU 不一致/.test(document.querySelector('#status').textContent),null,{polling:100})
    assert.equal(await panel.locator('#capture').isDisabled(),true)
    await stage('screenshot-sku-rejection')
    await panelScreenshot('UAT-JD-14-SKU变化拒绝.png')
    result.screenshots.push('UAT-JD-14-SKU变化拒绝.png')
    result.events.push({step:'skuMismatchRejected',error:mismatch.error,captureDisabled:true})
    await source.evaluate(sku=>document.querySelector('[data-current-sku]').setAttribute('data-current-sku',sku),fixtureSku)
    await source.evaluate(()=>{const challenge=document.createElement('div');challenge.dataset.captcha='synthetic';challenge.id='probe-captcha';challenge.textContent='合成验证提示';document.body.append(challenge)})
    const challenged=await panel.evaluate(()=>chrome.runtime.sendMessage({action:'preview'}))
    assert.equal(challenged.ok,false,'验证页必须拒绝预览')
    await click('#preview')
    assert.match(challenged.error,/登录或验证.*手动处理/)
    await panel.waitForFunction(()=>/登录或验证.*手动处理/.test(document.querySelector('#status').textContent),null,{polling:100})
    assert.equal(await panel.locator('#capture').isDisabled(),true)
    await stage('screenshot-challenge-rejection')
    await panelScreenshot('UAT-JD-23-验证页拒绝.png')
    result.screenshots.push('UAT-JD-23-验证页拒绝.png')
    result.events.push({step:'challengeRejected',error:challenged.error,captureDisabled:true})
    await source.evaluate(()=>document.querySelector('#probe-captcha').remove())
    result.events.push({step:'fixtureSetup',changes:['temporary SKU mismatch','temporary data-captcha'],restoredSku:fixtureSku,scope:'探针负例准备；不是插件自动操作'})
    const rawPreview=await panel.evaluate(()=>chrome.runtime.sendMessage({action:'preview'}))
    assert.equal(rawPreview.ok,true,rawPreview.error)
    assert.doesNotMatch(JSON.stringify(rawPreview.data),/用户收货地址不采集|"secret"|data-personal/,'原始预览不得包含个人区块或脚本内容')
    result.events.push({step:'previewPrivacy',personalTextExcluded:true,scriptSecretExcluded:true})
    await click('#preview')
    await panel.waitForFunction(()=>document.querySelector('#title').textContent.includes('合成商品'),null,{polling:100})
    await click('#capture')
    const first=await waitCapture(null)
    assert.doesNotMatch(JSON.stringify(first.context.payload),/用户收货地址不采集|"secret"|data-personal/,'实际提交payload不得包含个人区块或脚本内容')
    await panel.waitForFunction(()=>document.querySelector('#status').textContent.includes('部分保存'),null,{polling:100})
    const partial=await panel.locator('#status').innerText()
    result.events.push({step:'partial',message:partial,navigations,failures:await panel.locator('#failures').innerText()})
    assert.equal(navigations,0)
    const sourcePosition=await source.evaluate(()=>({scrollY:window.scrollY,sku:document.querySelector('[data-current-sku]').getAttribute('data-current-sku')}))
    assert.equal(sourcePosition.scrollY,0,'插件不得自动滚动源页')
    assert.equal(sourcePosition.sku,fixtureSku,'插件不得切换当前SKU')
    result.events.push({step:'noAutomaticSourceActions',navigations,scrollY:sourcePosition.scrollY,sku:sourcePosition.sku})
    assert.match(partial,/部分/)
    assert.equal(first.saved,6)
    assert.equal(first.total,8)
    assert.equal(first.failures.length,2)
    await stage('screenshot-partial-capture')
    await panelScreenshot('UAT-JD-13-16-扩展部分保存.png')
    result.screenshots.push('UAT-JD-13-16-扩展部分保存.png')
    failImages=false
    await click('#retry')
    await panel.waitForFunction(()=>document.querySelector('#status').textContent.includes('重试完成'),null,{timeout:120000,polling:100})
    result.events.push({step:'retry',message:await panel.locator('#status').innerText()})
    await stage('screenshot-image-retry')
    await panelScreenshot('UAT-JD-16-重试保存.png')
    result.screenshots.push('UAT-JD-16-重试保存.png')
    await click('#capture')
    await panel.locator('#captureDialog').waitFor({state:'visible'})
    await stage('screenshot-duplicate-confirmation')
    await panelScreenshot('UAT-JD-17-重复确认.png')
    result.screenshots.push('UAT-JD-17-重复确认.png')
    await click('#confirmCapture')
    const second=await waitCapture(first.captureId)
    assert.equal(second.saved,8)
    assert.equal(second.failures.length,0)
    result.events.push({step:'version',message:await panel.locator('#status').innerText()})
    if(runOfflineProbe){
      // 只采主图使丢片位置确定，保持产品源页面与后端数据真实。
      await panel.evaluate(()=>document.querySelectorAll('input[type=checkbox]').forEach(input=>{input.checked=input.value==='MAIN'}))
      await worker.evaluate(()=>{globalThis.__adenProbeFault.dropNextChunk=true})
      await click('#capture')
      await panel.locator('#captureDialog').waitFor({state:'visible'})
      await click('#confirmCapture')
      const staged=await waitCapture(second.captureId)
      assert.equal(staged.saved,0)
      assert.equal(staged.failures.length,1)
      const stored=await panel.evaluate(async captureId=>{
        const {IndexedDbStore}=await import(chrome.runtime.getURL('spool.js'))
        return (await new IndexedDbStore().list()).filter(row=>row.captureId===captureId).map(row=>({kind:row.kind,byteLength:row.bytes?.byteLength,expiresAt:row.expiresAt}))
      },staged.captureId)
      assert(stored.some(row=>row.kind==='asset'&&row.byteLength===image.length),'真实IndexedDB必须存有未上传图片字节')
      // 关闭源页后恢复；任何后续 HTTP 图片请求均会使此断言失败。
      await source.close()
      const imageRequestsBefore=[...requests.values()].reduce((sum,count)=>sum+count,0)
      // 重新连接后再查询暂存，不能使用旧身份静默读取。
      await click('#connect')
      await panel.waitForFunction(()=>document.querySelector('#status').textContent.includes('已连接'),null,{timeout:65000,polling:100})
      await click('#pending')
      await panel.waitForFunction(id=>Array.from(document.querySelector('#pendingList').options).some(option=>option.value===id),staged.captureId,{polling:100})
      await panel.locator('#pendingList').selectOption(staged.captureId)
      await click('#resume')
      await panel.waitForFunction(()=>document.querySelector('#status').textContent.includes('离线暂存已全部保存'),null,{timeout:120000,polling:100})
      assert.equal([...requests.values()].reduce((sum,count)=>sum+count,0),imageRequestsBefore,'离线恢复不得再次访问源站图片')
      const persisted=await panel.evaluate(async captureId=>{
        const {IndexedDbStore}=await import(chrome.runtime.getURL('spool.js'))
        return (await new IndexedDbStore().list()).filter(row=>row.captureId===captureId).length
      },staged.captureId)
      assert.equal(persisted,0,'后端确认完成后应清理暂存')
      await stage('screenshot-offline-restore')
      await panelScreenshot('UAT-JD-22-真实IDB离线字节恢复.png')
      result.screenshots.push('UAT-JD-22-真实IDB离线字节恢复.png')
      result.events.push({step:'offlineSpool',fault:'Native发送前明确故障注入；后端与IndexedDB真实',injected:await worker.evaluate(()=>globalThis.__adenProbeFault.injected),stored,afterRestoreEntries:persisted,sourceRequestsDuringRestore:0})
    }
    if(!source.isClosed())await source.close()
    await click('#library')
    await page.getByRole('heading',{name:'商品库',exact:true}).waitFor({timeout:15000})
    result.events.push({step:'sourceClosed',value:true})
    await page.getByLabel('库内搜索').fill(fixtureSku)
    await page.getByRole('button',{name:'查询 / 刷新',exact:true}).click()
    await page.getByRole('button',{name:'合成商品 · 无真实交易',exact:true}).waitFor()
    await page.getByRole('button',{name:'合成商品 · 无真实交易',exact:true}).click()
    await page.getByLabel('采集版本').selectOption(second.captureId)
    await page.locator('.collection-detail').getByRole('button',{name:'图片',exact:true}).click()
    await page.waitForFunction(()=>{
      const images=[...document.querySelectorAll('.collection-images img')]
      return images.length===8&&images.every(img=>img.complete&&img.naturalWidth>0&&img.src.startsWith('data:image/'))
    })
    await page.bringToFront()
    await page.screenshot({path:join(evidenceDir,'UAT-JD-16-关闭源页后八张历史图片.png'),timeout:10000})
    result.screenshots.push('UAT-JD-16-关闭源页后八张历史图片.png')
    result.events.push({step:'offlineImageReview',decodedLocalImages:8,sourceClosed:true,snapshotId:second.captureId})
    await page.getByRole('button',{name:'关闭详情',exact:true}).click()
    await page.getByLabel('库内搜索').fill('')
    await page.getByRole('button',{name:'查询 / 刷新',exact:true}).click()
    await stage('complete')
    return result
  } catch(error) {
    result.errors.push(error.message)
    result.failedStage=result.stage
    result.errorStack=error.stack
    result.failedAt=new Date().toISOString()
    await persist()
    if(context) for(const [index,p] of context.pages().entries()) {
      const file=`扩展失败-${index}.png`
      await p.bringToFront().catch(()=>{})
      await p.screenshot({path:join(evidenceDir,file),timeout:5000}).then(()=>result.screenshots.push(file)).catch(screenshotError=>{
        result.events.push({step:'failure-screenshot',url:p.url(),file,error:screenshotError.message})
      })
      await persist()
    }
    throw error
  } finally {
    await persist()
    await context?.close()
    await new Promise(resolveClosed=>server.close(resolveClosed))
    // 保留本次受限profile供故障定位；不递归删除其他浏览器数据。
  }
}
