const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');
const runtime = process.env.PROTOTYPE_NODE_MODULES || 'C:/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
const { chromium } = require(path.join(runtime, 'playwright'));
const results = [], failures = [];
const output = path.join(__dirname, 'output/playwright');
const assert = (value, message) => { if (!value) throw new Error(message); };
async function assertImagesContained(page) {
  const bad = await page.evaluate(() => Array.from(document.querySelectorAll('.mini-board img')).filter(img => {
    const a = img.getBoundingClientRect(), b = img.parentElement.getBoundingClientRect();
    return a.bottom > b.bottom + 1 || a.top < b.top - 1 || a.right > b.right + 1 || a.left < b.left - 1;
  }).map(img => img.alt));
  assert(!bad.length, '商品图片越出拼图区：' + bad.join('、'));
}
async function check(label, fn) { try { await fn(); results.push({ label, result: 'Pass' }); console.log('PASS ' + label); } catch (e) { failures.push({ label, result: 'Fail', error: e.message }); console.log('FAIL ' + label + ': ' + e.message); } }
(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PROTOTYPE_BROWSER || 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, locale: 'zh-CN' }); page.setDefaultTimeout(6000);
  try {
    await page.goto(pathToFileURL(path.join(__dirname, '03-ai-fashion-styling-quotation/index.html')).href);
    await page.locator('#open-quote').click();
    await check('报价 / 过期有效期阻断保存', async () => {
      await page.locator('#quote-valid').fill('2026-09-01'); await page.locator('#quote-valid').blur();
      assert(await page.locator('#save-quote').isDisabled(), '过期仍可保存');
      assert((await page.locator('#quote-errors').innerText()).includes('2026-09-10'), '缺少基准日期解释');
      await page.locator('#quote-valid').fill('2026-09-30'); await page.locator('#quote-valid').blur();
    });
    await check('报价 / 无效草稿无法绕过交付限制', async () => {
      await page.locator('[data-row-qty]').first().fill('9999');
      assert(await page.locator('#preview-draft').isDisabled(), '无效报价仍能从报价页预览');
      await page.locator('.sidebar a[href="#delivery"]').click();
      assert(await page.locator('#delivery-csv').isDisabled(), '直接进入交付仍可导出');
      assert(await page.locator('#print-delivery').isDisabled(), '直接进入交付仍可打印');
      await page.locator('.sidebar a[href="#quotes"]').click(); await page.locator('[data-row-qty]').first().fill('30');
    });
    await check('报价 / 有效版本打印布局', async () => {
      await page.locator('#save-quote').click(); await page.locator('#delivery-version').waitFor();
      await assertImagesContained(page);
      await page.screenshot({ path: path.join(output, 'fashion-quotation-desktop.png'), fullPage: true });
      await page.emulateMedia({ media: 'print' });
      await page.evaluate(() => { document.querySelectorAll('img').forEach(img => { img.loading = 'eager'; }); });
      await page.waitForTimeout(300);
      assert(await page.locator('.delivery-sheet').isVisible(), '打印隐藏了报价正文');
      assert(!(await page.locator('.sidebar').isVisible()), '打印仍包含导航');
      assert((await page.locator('.document-total').innerText()).includes('26,231.82'), '打印金额错误');
      await assertImagesContained(page);
      await page.screenshot({ path: path.join(output, 'fashion-quotation-print.png'), fullPage: true });
    });
  } finally {
    await browser.close(); fs.writeFileSync(path.join(output, 'delivery-results.json'), JSON.stringify({ date: new Date().toISOString(), results, failures }, null, 2));
  }
  console.log(JSON.stringify({ passed: results.length, failed: failures.length })); process.exitCode = failures.length ? 1 : 0;
})().catch(e => { console.error(e); process.exitCode = 1; });
