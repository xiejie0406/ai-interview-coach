import { fashionRequest, type FashionQuery } from './client'
import type { FashionQuote, QuoteTier, RequirementFields, RuoYiResult } from './types'

export interface QuotePage { items: FashionQuote[]; total: number; page: number; pageSize: number }
export interface QuoteQuery extends FashionQuery { status?: string; keyword?: string; page: number; pageSize: number }
export interface QuoteDraftBody {
  customerId: string
  title: string
  requirementText?: string
  requirements: RequirementFields
  requestedQty: number
  budget?: number
  budgetBasis: 'total' | 'per_set'
  quoteMode: string
  progressive: boolean
  tiers: QuoteTier[]
  warehouseCode: string
  rowVersion: number
}

export const listQuotes = (params: QuoteQuery, signal?: AbortSignal) =>
  fashionRequest<RuoYiResult<QuotePage>>({ path: 'quotes', params, signal })
export const getQuote = (id: string) => fashionRequest<RuoYiResult<FashionQuote>>({ path: `quotes/${id}` })
export const createQuote = (body: QuoteDraftBody) =>
  fashionRequest<RuoYiResult<FashionQuote>, QuoteDraftBody>({ path: 'quotes', method: 'post', body })
export const updateQuote = (id: string, body: QuoteDraftBody) =>
  fashionRequest<RuoYiResult<FashionQuote>, QuoteDraftBody>({ path: `quotes/${id}`, method: 'put', body })
export const copyQuote = (id: string) => fashionRequest<RuoYiResult<FashionQuote>>({ path: `quotes/${id}/copy`, method: 'post' })
export const closeQuote = (id: string, rowVersion: number) =>
  fashionRequest<RuoYiResult<FashionQuote>, { rowVersion: number }>({ path: `quotes/${id}/close`, method: 'put', body: { rowVersion } })
