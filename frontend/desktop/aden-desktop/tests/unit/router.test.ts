// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { router } from '../../src/renderer/src/router'
import { useSessionStore } from '../../src/renderer/src/stores/session'

describe('renderer router', () => {
  it('uses only the five approved routes and guards private views', async () => {
    setActivePinia(createPinia())
    const paths = router.getRoutes().filter((route) => route.path !== '/').map((route) => route.path).sort()
    expect(paths).toEqual(['/about', '/capabilities', '/login', '/tasks', '/tasks/:taskId'])
    useSessionStore().phase = 'anonymous'
    await router.push('/tasks')
    expect(router.currentRoute.value.path).toBe('/login')
  })
})
