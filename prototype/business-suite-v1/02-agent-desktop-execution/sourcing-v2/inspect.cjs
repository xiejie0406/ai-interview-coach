/* 本地原型的浏览器观察入口；只加载演示页面，不接入任何真实业务平台。 */
const path = require('node:path');
const fs = require('node:fs');
const { chromium } = require(path.join(process.env.PROTOTYPE_NODE_MODULES || 'C:/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules', 'playwright'));
const output = path.join(__dirname, 'output', 'playwright');
const route = process.argv[2] || 'dashboard';
const width = Number(process.argv[3] || 1440);

(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PROTOTYPE_BROWSER || 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const page = await browser.newPage({ viewport: { width, height: width < 600 ? 812 : 1000 }, reducedMotion: 'reduce', locale: 'zh-CN' });
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  try {
    await page.goto('http://127.0.0.1:8789/02-agent-desktop-execution/sourcing-v2/#' + route);
    await page.locator('main').waitFor({ state: 'visible', timeout: 10000 });
    const screenshot = path.join(output, route.split('/')[0] + '-' + width + '.png');
    await page.screenshot({ path: screenshot, fullPage: true, animations: 'disabled' });
    console.log(JSON.stringify({ url: page.url(), title: await page.title(), errors, screenshot, layout: await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth })) }, null, 2));
    console.log(await page.locator('body').ariaSnapshot());
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
