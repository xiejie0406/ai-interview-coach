import { expect, test } from '@playwright/test'

test('管理端入口与 Fashion 客户端不直连 Python Runtime', async ({ page }) => {
  const browserOrigin = 'http://127.0.0.1:4175'
  const foreignRequests: string[] = []

  page.on('request', request => {
    const url = new URL(request.url())
    if (url.origin !== browserOrigin) foreignRequests.push(url.href)
  })

  await page.goto('/login', { waitUntil: 'domcontentloaded' })
  await expect(page.locator('#app')).toBeAttached()
  await expect(page.locator('svg#__svg__icons__dom__ symbol#icon-user')).toHaveCount(1)

  const clientSourceResponse = await page.request.get('/src/api/fashion/client.ts')
  expect(clientSourceResponse.ok()).toBe(true)
  const clientSource = await clientSourceResponse.text()

  expect(clientSource).toContain('/fashion')
  expect(clientSource).not.toContain('VITE_FASHION_AI_RUNTIME_URL')
  expect(clientSource).not.toContain('FASHION_PROVIDER_SECRET')
  expect(clientSource).not.toContain('http://127.0.0.1:8000')
  expect(foreignRequests).toEqual([])
})
