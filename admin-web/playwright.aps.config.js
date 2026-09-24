import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './tests/e2e/aps',
  outputDir: '../output/playwright/aps-imp01/artifacts',
  reporter: [
    ['list'],
    [
      'html',
      { outputFolder: '../output/playwright/aps-imp01/html', open: 'never' }
    ]
  ],
  timeout: 120_000,
  expect: {
    timeout: 30_000
  },
  use: {
    baseURL: 'http://127.0.0.1:4174',
    trace: 'retain-on-failure'
  },
  projects: [
    {
      name: 'chrome',
      // 复用工作站已安装的稳定 Chrome；Playwright 自带 Chromium 不作为本 PoC 前置下载。
      use: { ...devices['Desktop Chrome'], channel: 'chrome' }
    }
  ],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 4174',
    url: 'http://127.0.0.1:4174',
    reuseExistingServer: false,
    timeout: 120_000
  }
})
