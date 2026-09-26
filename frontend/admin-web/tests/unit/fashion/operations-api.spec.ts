import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fashionRequest } from '@/api/fashion/client'
import { getFashionOperations, getRetentionDryRun } from '@/api/fashion/operations'

vi.mock('@/api/fashion/client', () => ({ fashionRequest: vi.fn() }))

describe('Fashion operations API', () => {
  beforeEach(() => vi.mocked(fashionRequest).mockReset())
  it('uses separate read and retention dry-run Java endpoints', async () => {
    vi.mocked(fashionRequest).mockResolvedValue({ code: 200, data: {} } as never)
    await getFashionOperations(30)
    await getRetentionDryRun()
    expect(vi.mocked(fashionRequest).mock.calls.map(([request]) => request)).toEqual([
      { path: 'operations/overview', params: { limit: 30 }, signal: undefined },
      { path: 'operations/retention/dry-run', signal: undefined }
    ])
  })
})
