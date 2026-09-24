'use strict';
// 独立无登录浏览器，只对本地原型和生成的测试文件操作。
const fs = require('node:fs'), path = require('node:path'), os = require('node:os'), assert = require('node:assert/strict');
const runtime = 'C:/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
const { chromium } = require(path.join(runtime, 'playwright'));
const JSZip = require(path.join(runtime, 'jszip'));
const IO = require('./file-io.js');
const output = path.resolve(__dirname, '../../output/playwright/fashion-catalog');
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'fashion-catalog-check-'));
const esc = v => String(v).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;' }[c]));
async function xlsx(rows, filename) {
  const zip = new JSZip();
  zip.file('[Content_Types].xml', '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/></Types>');
  zip.file('xl/workbook.xml', '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="库存全量" sheetId="1" r:id="rId1"/></sheets></workbook>');
  zip.file('xl/_rels/workbook.xml.rels', '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Target="worksheets/sheet1.xml" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"/></Relationships>');
  zip.file('xl/worksheets/sheet1.xml', '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>' + rows.map((r, i) => '<row r="' + (i + 1) + '">' + r.map((v, j) => `<c r="${String.fromCharCode(65 + j)}${i + 1}" t="inlineStr"><is><t>${esc(v)}</t></is></c>`).join('') + '</row>').join('') + '</sheetData></worksheet>');
  fs.writeFileSync(filename, await zip.generateAsync({ type: 'nodebuffer' }));
}
(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, acceptDownloads: true });
  const page = await context.newPage(), errors = [], results = [];
  page.on('pageerror', err => errors.push(err.message));
  async function pass(name) { results.push(name); console.log('PASS ' + name); }
  try {
    await page.goto('file:///' + path.join(__dirname, 'index.html').replace(/\\/g, '/') + '#catalog');
    await page.locator('#cat-search').waitFor();
    assert.equal(await page.locator('#cat-new').count(), 0); await pass('sales has no catalog write action');
    await page.locator('#role-shortcut').click(); await page.locator('#set-role').selectOption('operator'); await page.locator('#settings-save').click();
    await page.locator('[data-go="catalog"]').first().click();
    await page.locator('#cat-search').fill('Polo'); await page.locator('#cat-filter button').click(); assert.equal(await page.locator('.cat-card').count(), 2);
    await page.locator('#cat-search').fill(''); await page.locator('#cat-filter button').click();
    await page.screenshot({ path: path.join(output, 'catalog-1440.png'), fullPage: true }); await pass('catalog search and 1440px layout');
    await page.locator('[data-detail]').first().click(); await page.locator('#cat-detail-form input[name="tags"]').fill('已核对，团购'); await page.locator('button[form="cat-detail-form"]').click();
    assert.ok(await page.evaluate(() => F.state.products.some(p => p.tags.includes('已核对')))); await pass('operator product edit persists');
    await page.locator('#cat-import').click(); await page.locator('[data-import-kind="price"]').click();
    const initial = await page.evaluate(() => JSON.parse(JSON.stringify(F.state.products.map(p => p.sizes.map(s => ({ sku: s.sku, price: s.price, stock: s.stock }))))));
    const download = page.waitForEvent('download'); await page.locator('#imp-example').click(); const d = await download; const csvPath = path.join(tmp, 'price-complete.csv'); await d.saveAs(csvPath);
    const rows = IO.parseCSV(fs.readFileSync(csvPath, 'utf8')); rows[1][rows[0].indexOf('销售价')] = '81.25'; fs.writeFileSync(csvPath, IO.csv(rows));
    await page.locator('#imp-file').setInputFiles(csvPath); await page.locator('#imp-validate').click();
    await page.locator('#imp-apply').waitFor(); await page.locator('#imp-apply').click();
    assert.equal(await page.evaluate(() => F.state.products[0].sizes[0].price), 8125); assert.deepEqual(await page.evaluate(() => F.state.products.map(p => p.sizes.map(s => s.stock))), initial.map(p => p.map(s => s.stock))); await pass('download/reupload actual CSV atomically updates prices only');
    const missingPath = path.join(tmp, 'price-missing.csv'); fs.writeFileSync(missingPath, IO.csv(rows.slice(0, -1))); await page.locator('#imp-file').setInputFiles(missingPath); await page.locator('#imp-validate').click(); assert.equal(await page.locator('#imp-apply').count(), 0); assert.match(await page.locator('#imp-preview').innerText(), /全量缺少 SKU/); await pass('missing full CSV blocks publication');
    await page.locator('[data-import-kind="stock"]').click();
    const stockData = await page.evaluate(() => F.catalog.templateRows('stock', true)); stockData[1][4] = '0';
    const xlsxPath = path.join(tmp, 'stock-complete.xlsx'); await xlsx(stockData, xlsxPath);
    await page.locator('#imp-file').setInputFiles(xlsxPath); await page.locator('#imp-validate').click(); await page.locator('#imp-apply').click();
    assert.equal(await page.evaluate(() => F.state.products[0].sizes[0].stock), 0); assert.equal(await page.evaluate(() => F.state.products[0].sizes[0].price), 8125); await pass('actual XLSX parses and updates explicit zero stock independently');
    await page.locator('[data-go="customers"]').first().click(); await page.locator('[data-customer]').first().click(); await page.locator('#c-price').fill('团购未税协议价'); await page.locator('#c-save').click();
    await page.locator('[data-go="imports"]').first().click(); await page.locator('[data-import-kind="price"]').click(); await page.locator('#imp-price-table').selectOption('团购未税协议价'); await page.locator('#imp-tax-mode').selectOption('excluded');
    const taxDownload = page.waitForEvent('download'); await page.locator('#imp-example').click(); const taxFile = await taxDownload; const taxPath = path.join(tmp, 'customer-price.csv'); await taxFile.saveAs(taxPath);
    const customerRows = IO.parseCSV(fs.readFileSync(taxPath, 'utf8')); assert.ok(customerRows.slice(1).every(r => r[r.length - 1] === '' && r[r.length - 2] === '未税')); customerRows.slice(1).forEach(r => r[r.length - 1] = '66.66'); fs.writeFileSync(taxPath, IO.csv(customerRows));
    await page.locator('#imp-file').setInputFiles(taxPath); await page.locator('#imp-validate').click(); await page.locator('#imp-apply').click();
    assert.equal(await page.evaluate(() => F.state.products[0].sizes[0].priceTables['团购未税协议价']), 6666); assert.equal(await page.evaluate(() => F.state.products[0].sizes[0].priceTableTaxModes['团购未税协议价']), 'excluded'); assert.equal(await page.evaluate(() => F.state.products[0].sizes[0].price), 8125); await pass('UI customer price table and untaxed import remain isolated from wholesale');
    await page.locator('[data-restore]').first().click(); await page.locator('#imp-restore-confirm').click(); assert.equal(await page.evaluate(() => F.state.products[0].sizes[0].priceTables['团购未税协议价']), null);
    await page.getByRole('button', { name: '恢复到此操作之前', exact: true }).first().click(); await page.locator('#imp-restore-confirm').click(); assert.equal(await page.evaluate(() => F.state.products[0].sizes[0].priceTables['团购未税协议价']), 6666); assert.equal(await page.evaluate(() => F.state.products[0].sizes[0].priceTableTaxModes['团购未税协议价']), 'excluded'); await pass('historical restore and undo-restore are both operable with original tax metadata');
    await page.screenshot({ path: path.join(output, 'imports-1440.png'), fullPage: true });
    await page.locator('[data-go="media"]').first().click();
    await page.locator('#med-files').setInputFiles(path.join(__dirname, 'assets/catalog-grid.png')); await page.waitForFunction(() => !F.catalog.ui.mediaBusy && F.state.media.some(m => m.status === 'uploaded'));
    assert.equal(await page.evaluate(() => F.state.media.filter(m => m.status === 'uploaded').length), 1);
    await page.locator('#med-files').setInputFiles(path.join(__dirname, 'assets/catalog-grid.png')); await page.waitForFunction(() => !F.catalog.ui.mediaBusy); assert.equal(await page.evaluate(() => F.state.media.length), 1); await pass('actual image upload and SHA256 deduplication');
    await page.locator('[data-media-edit]').first().click(); await page.locator('#med-edit-form input[name="confirmed"]').check(); await page.locator('#med-edit-form input[name="allowed"]').check(); const id = await page.locator('#med-product').inputValue(); const v = await page.evaluate(id => F.product(id).imageVersion, id); await page.locator('button[form="med-edit-form"]').click(); assert.equal(await page.evaluate(id => F.product(id).imageVersion, id), v + 1); assert.equal(await page.evaluate(id => F.product(id).confirmed, id), false); await pass('manual main-image assignment changes version and requires attribute review');
    const badPath = path.join(tmp, 'broken.png'); fs.writeFileSync(badPath, 'broken image'); await page.locator('#med-files').setInputFiles(badPath); await page.waitForFunction(() => !F.catalog.ui.mediaBusy); assert.equal(await page.locator('[data-retry]').count(), 1); await pass('corrupted image retained as retryable failure');
    await page.locator('[data-retry]').first().click(); await page.locator('#med-retry-file').setInputFiles(path.join(__dirname, 'assets/catalog-grid.png')); await page.waitForFunction(() => !F.catalog.ui.mediaBusy); assert.equal(await page.locator('[data-retry]').count(), 0); assert.equal(await page.evaluate(() => F.state.media.length), 1); assert.equal(await page.evaluate(() => F.state.mediaRetries.length), 1); await pass('failed-item retry deduplicates content and retains recovery history');
    const zip = new JSZip(); zip.file('nested/商品.png', fs.readFileSync(path.join(__dirname, 'assets/catalog-grid.png'))); const zipPath = path.join(tmp, 'images.zip'); fs.writeFileSync(zipPath, await zip.generateAsync({ type: 'nodebuffer' })); await page.locator('#med-files').setInputFiles(zipPath); await page.waitForFunction(() => !F.catalog.ui.mediaBusy); assert.equal(await page.evaluate(() => F.state.media.filter(m => m.status === 'uploaded').length), 1); await pass('ZIP image extraction deduplicates existing content');
    await page.screenshot({ path: path.join(output, 'media-1440.png'), fullPage: true });
    await page.setViewportSize({ width: 375, height: 812 }); await page.goto('file:///' + path.join(__dirname, 'index.html').replace(/\\/g, '/') + '#catalog'); await page.locator('#cat-search').waitFor();
    await page.waitForTimeout(3500); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false); await page.screenshot({ path: path.join(output, 'catalog-375.png'), fullPage: true });
    await page.goto('file:///' + path.join(__dirname, 'index.html').replace(/\\/g, '/') + '#imports'); await page.locator('#imp-file').waitFor(); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false); await page.screenshot({ path: path.join(output, 'imports-375.png'), fullPage: true }); await pass('catalog/imports 375px no page-level horizontal overflow');
    await page.emulateMedia({ reducedMotion: 'reduce' }); assert.equal(await page.evaluate(() => matchMedia('(prefers-reduced-motion: reduce)').matches), true); await pass('reduced-motion environment renders without errors');
    assert.deepEqual(errors, []); await pass('no browser runtime exception during exercised flows');
    fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify({ date: new Date().toISOString(), browser: await browser.version(), entry: 'file://', results, errors, fixtures: tmp, limitations: ['测试数据与独立临时浏览器，不证明生产权限或真实京东采集', '本轮未做屏幕阅读器人工验收'] }, null, 2));
  } catch (err) { await page.screenshot({ path: path.join(output, 'failure.png'), fullPage: true }).catch(() => {}); fs.writeFileSync(path.join(output, 'failure.txt'), String(err.stack)); throw err; }
  finally { await context.close(); await browser.close(); }
})().catch(err => { console.error(err); process.exitCode = 1; });
