/* 浏览器证据脚本：仅打开本目录的示例页面，不连接业务服务。 */
const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');
const runtime = process.env.PROTOTYPE_NODE_MODULES || 'C:/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
const { chromium } = require(path.join(runtime, 'playwright'));
const executablePath = process.env.PROTOTYPE_BROWSER || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const output = path.join(__dirname, 'output', 'playwright');
const allProjects = ['01-production-scheduling', '02-agent-desktop-execution', '03-ai-fashion-styling-quotation'];
const projects = process.argv[2] ? allProjects.filter(name => name.startsWith(process.argv[2])) : allProjects;
const results = [];
const failures = [];
fs.mkdirSync(output, { recursive: true });

function assert(condition, message) { if (!condition) throw new Error(message); }
async function record(label, work) {
  try { const detail = await work(); results.push({ label, result: 'Pass', detail }); console.log('PASS ' + label); }
  catch (error) { failures.push({ label, result: 'Fail', error: error.message }); console.log('FAIL ' + label + ': ' + error.message); }
}
async function metrics(page) {
  return page.evaluate(() => ({
    title: document.title,
    heading: document.querySelector('h1,.page-title')?.textContent.trim(),
    textLength: (document.querySelector('main,.content')?.innerText || '').length,
    width: innerWidth, scrollWidth: document.documentElement.scrollWidth,
    missingImages: Array.from(document.images).filter(img => !img.complete || img.naturalWidth === 0).map(img => img.getAttribute('src')),
    remainingIcons: Array.from(document.querySelectorAll('[data-lucide]')).filter(el => el.tagName.toLowerCase() !== 'svg').map(el => el.getAttribute('data-lucide')),
  }));
}
async function audit(page, label, capture) {
  await page.waitForTimeout(120);
  await page.evaluate(() => {
    const pending = Array.from(document.images).map(img => { img.loading = 'eager'; return img.decode().catch(() => {}); });
    return Promise.race([Promise.all(pending), new Promise(resolve => setTimeout(resolve, 3000))]);
  });
  const data = await metrics(page);
  assert(data.textLength > 50, '主内容为空或未渲染');
  assert(data.scrollWidth <= data.width + 1, '页面横向溢出：' + data.scrollWidth + ' > ' + data.width);
  assert(data.missingImages.length === 0, '图片未加载：' + data.missingImages.join(', '));
  assert(data.remainingIcons.length === 0, '图标未渲染：' + data.remainingIcons.join(', '));
  if (capture) await page.screenshot({ path: path.join(output, label + '.png'), fullPage: true, animations: 'disabled' });
  return data;
}

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'zh-CN', reducedMotion: 'reduce', acceptDownloads: true });
  const page = await context.newPage();
  page.setDefaultTimeout(6000);
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()); });
  try {
    for (const project of projects) {
      await page.setViewportSize({ width: 1440, height: 1000 });
      await page.goto(pathToFileURL(path.join(__dirname, project, 'index.html')).href);
      await page.waitForSelector('.sidebar .nav-item', { timeout: 10000 });
      const navSelector = '.sidebar button.nav-item,.sidebar a.nav-item[href^="#"]';
      const count = await page.locator(navSelector).count();
      await record(project + ' / 桌面首屏', () => audit(page, project + '-desktop', true));
      const homeURL = page.url();
      for (let index = 0; index < count; index++) {
        const item = page.locator(navSelector).nth(index);
        const title = (await item.innerText()).trim();
        await record(project + ' / 导航 / ' + title, async () => { await item.click(); return audit(page, project + '-view-' + index, false); });
      }
      await page.goto(homeURL);
      await page.setViewportSize({ width: 390, height: 844 });
      await record(project + ' / 手机首屏', () => audit(page, project + '-mobile', true));
      for (let index = 0; index < count; index++) {
        await record(project + ' / 手机导航 ' + (index + 1), async () => {
          const toggle = page.locator('[data-toggle-nav],#menu-button,.mobile-menu').first();
          if (await toggle.isVisible()) await toggle.click();
          const item = page.locator(navSelector).nth(index);
          await item.click();
          return audit(page, project + '-mobile-' + index, false);
        });
      }
    }
    if (projects.length === allProjects.length) {
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.goto(pathToFileURL(path.join(__dirname, 'index.html')).href);
    for (const key of ['scheduling', 'aden', 'fashion']) {
      await record('总入口 / 切换 / ' + key, async () => {
        await page.locator('[data-project="' + key + '"]').click();
        const frame = page.frameLocator('#prototype-frame');
        await frame.locator('.sidebar').waitFor();
        assert(await frame.locator('.content,main').first().innerText(), '子页面无内容');
        assert((await page.locator('[data-project="' + key + '"]').getAttribute('aria-selected')) === 'true', '选中状态未更新');
      });
    }
    await page.setViewportSize({ width: 390, height: 844 });
    await record('总入口 / 手机切换', async () => {
      await page.locator('[data-project="scheduling"]').click();
      await page.frameLocator('#prototype-frame').locator('.sidebar').waitFor();
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), '入口横向溢出');
      await page.screenshot({ path: path.join(output, 'suite-mobile.png'), fullPage: true });
    });
    }
    await record('浏览器运行异常', async () => { assert(errors.length === 0, errors.join('\n')); return { errors }; });
  } finally {
    await browser.close();
    fs.writeFileSync(path.join(output, 'smoke-results' + (process.argv[2] ? '-' + process.argv[2] : '') + '.json'), JSON.stringify({ date: new Date().toISOString(), browser: executablePath, results, failures, errors }, null, 2));
  }
  console.log(JSON.stringify({ passed: results.length, failed: failures.length }));
  process.exitCode = failures.length ? 1 : 0;
})().catch(error => { console.error(error); process.exitCode = 1; });
