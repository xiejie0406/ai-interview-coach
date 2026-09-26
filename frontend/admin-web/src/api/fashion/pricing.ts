import { fashionRequest } from './client'
import type { QuotePricingWorkspace, RuoYiResult } from './types'

export interface QuotePricingBody {
  mode: 'alternatives' | 'combined'
  taxMode: 'included' | 'excluded'
  taxRate?: string
  feeTaxable: boolean
  discountType: 'percent' | 'fixed'
  discountRate: string
  fixedDiscount: string
  freight: string
  validDays: number
  publicNote?: string
  selectedComboIds: string[]
  lines: Array<{ detailId: string; qty: number; quotePrice: string }>
  rowVersion: number
}

export const getQuotePricing = (quoteId: string) =>
  fashionRequest<RuoYiResult<QuotePricingWorkspace>>({ path: `quotes/${quoteId}/pricing` })
export const saveQuotePricing = (quoteId: string, body: QuotePricingBody) =>
  fashionRequest<RuoYiResult<QuotePricingWorkspace>, QuotePricingBody>({ path: `quotes/${quoteId}/pricing`, method: 'put', body })
export const requestQuoteApproval = (quoteId: string, inputHash: string, reason: string, rowVersion: number) =>
  fashionRequest<RuoYiResult<QuotePricingWorkspace>, { inputHash: string; reason: string; rowVersion: number }>({
    path: `quotes/${quoteId}/pricing/approval-request`, method: 'post', body: { inputHash, reason, rowVersion }
  })
export const approveQuote = (quoteId: string, inputHash: string, reason: string, rowVersion: number) =>
  fashionRequest<RuoYiResult<QuotePricingWorkspace>, { inputHash: string; reason: string; rowVersion: number }>({
    path: `quotes/${quoteId}/pricing/approval`, method: 'post', body: { inputHash, reason, rowVersion }
  })
export const confirmQuote = (quoteId: string, inputHash: string, rowVersion: number) =>
  fashionRequest<RuoYiResult<QuotePricingWorkspace>, { inputHash: string; rowVersion: number }>({
    path: `quotes/${quoteId}/pricing/confirm`, method: 'post', body: { inputHash, rowVersion }
  })
