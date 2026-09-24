import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './tests/e2e/fashion',
  outputDir: '../output/playwright/fashion-production/artifacts',
  reporter: [
    ['list'],
    ['html', { outputFolder: '../output/playwright/fashion-production/html', open: 'never' }]
  ],
  timeout: 120_000,
  workers: 1,
  expect: {
    timeout: 10_000
  },
  use: {
    baseURL: 'http://127.0.0.1:4175',
    trace: 'retain-on-failure'
  },
  projects: [
    {
      name: 'chrome',
      use: { ...devices['Desktop Chrome'], channel: 'chrome' }
    }
  ],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 4175',
    url: 'http://127.0.0.1:4175/login',
    reuseExistingServer: false,
    timeout: 120_000
  }
})
