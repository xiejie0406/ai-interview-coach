import { expect, test, type Page, type Route } from '@playwright/test'
import type { QuoteImageTask, SelectionCombo } from '@/api/fashion/types'

const imageBody = '<svg xmlns="http://www.w3.org/2000/svg" width="320" height="400"><rect width="100%" height="100%" fill="#dde7f0"/></svg>'
const combo: SelectionCombo = { id: '200', quoteId: '100', comboNo: 'C-200', name: '四品类搭配', categoryCount: 4,
  setQty: 100, selected: true, sortNo: 0, lockedSlots: [], visualHash: 'a'.repeat(64), rowVersion: 4, details: [] }
const checks = ['slot_count', 'style', 'color', 'logo', 'completeness', 'pose', 'quality']

function task(sourceMode: 'sample'|'upload', id: string): QuoteImageTask {
  return { id, quoteId: '100', comboId: '200', imageType: 'model', sourceMode,
    sourceLabel: sourceMode === 'sample' ? '体验样例' : '外部上传', inputHash: 'e'.repeat(64),
    inputs: { schema_version: '1.0', combo_id: '200', combo_visual_hash: 'a'.repeat(64),
      slots: [{ slot_code: 'SLOT-1', image_key: 'materials/a.png', image_hash: 'b'.repeat(64) }] },
    parameters: {}, requestedCount: 1, results: [{ no: 1, status: 'success', object_key: `quote-images/${id}/1.png`,
      sha256: 'c'.repeat(64), width: 768, height: 1024, allow_proposal: true, allow_ecommerce: false, reviews: [] }],
    status: 'success', displayStatus: 'pending_review', stale: false, retryCount: 0,
    billingStatus: 'not_applicable', createTime: '2026-09-13T00:00:00Z', rowVersion: 2 }
}

async function installMock(page: Page) {
  const tasks: QuoteImageTask[] = []
  await page.route('**/dev-api/**', async (route: Route) => {
    const request=route.request(),method=request.method(),path=new URL(request.url()).pathname.replace('/dev-api','')
    const ok=(data:unknown)=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)})
    if(path==='/captchaImage')return ok({code:200,captchaEnabled:false})
    if(path==='/login'&&method==='POST')return ok({code:200,token:'image-token'})
    if(path==='/getInfo')return ok({code:200,user:{userId:1,userName:'operator',nickName:'图片运营',avatar:''},roles:['fashion_operator'],permissions:['fashion:image:list','fashion:image:create','fashion:image:review'],isDefaultModifyPwd:false,isPasswordExpired:false,pwdChrtype:0})
    if(path==='/getRouters')return ok({code:200,data:[{name:'Fashion',path:'/fashion',component:'Layout',redirect:'noRedirect',alwaysShow:true,meta:{title:'智能选品'},children:[{name:'FashionImage',path:'image',component:'fashion/image/index',meta:{title:'图片工作台'}}]}]})
    if(path==='/fashion/quotes/100/images'&&method==='GET')return ok({code:200,data:{quoteId:'100',quoteRowVersion:3,quoteStatus:'draft',providerEnabled:false,providerReason:'provider_disabled',monthlyBudget:10,monthSettledCost:0,combinations:[combo],tasks}})
    if(path==='/fashion/quotes/100/images'&&method==='POST'){const created=task('sample','301');tasks.unshift(created);return ok({code:200,data:created})}
    if(path==='/fashion/quotes/100/images/upload'&&method==='POST'){const uploaded=task('upload','302');tasks.unshift(uploaded);return ok({code:200,data:uploaded})}
    if(path.endsWith('/original')||path.endsWith('/content'))return route.fulfill({status:200,contentType:'image/svg+xml',body:imageBody})
    if(path==='/fashion/quotes/100/images/301/reviews'&&method==='POST'){tasks[0].results[0].reviews.push({decision:'pass',reviewer:'1',time:'2026-09-13T01:00:00Z',reason:undefined,checklist:checks,input_hash:'e'.repeat(64)});tasks[0].displayStatus='not_adopted';tasks[0].rowVersion++;return ok({code:200,data:tasks[0]})}
    if(path==='/fashion/quotes/100/images/301/results/1/adopt'&&method==='POST'){tasks[0].displayStatus='adopted';tasks[0].rowVersion++;return ok({code:200,data:tasks[0]})}
    return route.fulfill({status:404,contentType:'application/json',body:JSON.stringify({code:404,msg:`${method} ${path}`})})
  })
}

test('图片工作台区分样例与上传并要求逐项复核后采用', async ({page}) => {
  await installMock(page)
  await page.goto('/login');await page.getByRole('button',{name:'登 录'}).click();await page.waitForURL(url=>!url.pathname.endsWith('/login'))
  await page.goto('/fashion/image?quoteId=100')
  await expect(page.getByText('真实 Provider 默认关闭')).toBeVisible()
  await expect(page.getByRole('button',{name:'真实 Provider 生成'})).toBeDisabled()
  await page.getByRole('button',{name:'体验样例任务'}).click()
  await expect(page.getByText('体验样例').first()).toBeVisible()
  await expect(page.getByText('待复核').first()).toBeVisible()
  for(const label of ['槽位数量','款式','颜色','印花/Logo','鞋帽完整性','姿态自然','关键细节与质量'])await page.getByText(label,{exact:true}).click()
  await page.getByRole('button',{name:'复核通过'}).click();await expect(page.getByText('已记录人工复核通过')).toBeVisible()
  await page.getByRole('button',{name:'采用'}).click();await page.locator('.el-message-box').getByRole('button',{name:'确定'}).click();await expect(page.getByText('图片已采用')).toBeVisible()
  await page.locator('input[type=file]').evaluate((element: HTMLInputElement) => {
    const transfer = new DataTransfer()
    transfer.items.add(new File(['mock-image'], 'external.png', { type: 'image/png' }))
    element.files = transfer.files
    element.dispatchEvent(new Event('change', { bubbles: true }))
  })
  await expect(page.getByText('外部上传').first()).toBeVisible()
  await page.reload();await expect(page.getByText('外部上传').first()).toBeVisible()
  await page.screenshot({path:'../output/playwright/fashion-production/image-flow.png',fullPage:true})
})
