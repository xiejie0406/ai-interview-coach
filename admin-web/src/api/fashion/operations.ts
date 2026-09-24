import { fashionRequest } from './client'
import type { FashionOperationsSnapshot, RetentionDryRun, RuoYiResult } from './types'

export const getFashionOperations = (limit = 50, signal?: AbortSignal) =>
  fashionRequest<RuoYiResult<FashionOperationsSnapshot>>({ path: 'operations/overview', params: { limit }, signal })

export const getRetentionDryRun = (signal?: AbortSignal) =>
  fashionRequest<RuoYiResult<RetentionDryRun>>({ path: 'operations/retention/dry-run', signal })
