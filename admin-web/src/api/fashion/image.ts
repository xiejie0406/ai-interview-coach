import { fashionRequest } from './client'
import type { QuoteImageTask, QuoteImageWorkspace, RuoYiResult, SelectionCombo } from './types'

export interface QuoteImageCreateBody {
  comboId: string
  imageType: 'model' | 'styling' | 'ecommerce' | 'composition'
  sourceMode: 'sample' | 'provider' | 'composition'
  requestedCount: number
  parameters: Record<string, string | boolean>
  requestKey: string
  quoteRowVersion: number
  comboRowVersion: number
  comboVisualHash: string
}

export const getQuoteImageWorkspace = (quoteId: string, signal?: AbortSignal) =>
  fashionRequest<RuoYiResult<QuoteImageWorkspace>>({ path: `quotes/${quoteId}/images`, signal })

export const createQuoteImageTask = (quoteId: string, body: QuoteImageCreateBody) =>
  fashionRequest<RuoYiResult<QuoteImageTask>, QuoteImageCreateBody>({
    path: `quotes/${quoteId}/images`, method: 'post', body,
  })

export const uploadQuoteImage = (
  quoteId: string,
  combo: SelectionCombo,
  quoteRowVersion: number,
  imageType: QuoteImageCreateBody['imageType'],
  requestKey: string,
  file: File,
) => {
  const body = new FormData()
  body.append('file', file)
  return fashionRequest<RuoYiResult<QuoteImageTask>, FormData>({
    path: `quotes/${quoteId}/images/upload`, method: 'post', body,
    params: { comboId: combo.id, imageType, requestKey, quoteRowVersion,
      comboRowVersion: combo.rowVersion, comboVisualHash: combo.visualHash },
  })
}

export const cancelQuoteImageTask = (quoteId: string, task: QuoteImageTask) =>
  fashionRequest<RuoYiResult<QuoteImageTask>>({
    path: `quotes/${quoteId}/images/${task.id}/cancel`, method: 'post', params: { rowVersion: task.rowVersion },
  })

export const reviewQuoteImage = (
  quoteId: string,
  task: QuoteImageTask,
  resultNo: number,
  decision: 'pass' | 'reject',
  checklist: string[],
  reason?: string,
) => fashionRequest<RuoYiResult<QuoteImageTask>, Record<string, unknown>>({
  path: `quotes/${quoteId}/images/${task.id}/reviews`, method: 'post',
  body: { resultNo, decision, checklist, reason, rowVersion: task.rowVersion },
})

export const adoptQuoteImage = (quoteId: string, task: QuoteImageTask, resultNo: number) =>
  fashionRequest<RuoYiResult<QuoteImageTask>>({
    path: `quotes/${quoteId}/images/${task.id}/results/${resultNo}/adopt`, method: 'post',
    params: { rowVersion: task.rowVersion },
  })

export const getQuoteImageResultContent = (quoteId: string, taskId: string, resultNo: number) =>
  fashionRequest<Blob>({ path: `quotes/${quoteId}/images/${taskId}/results/${resultNo}/content`, responseType: 'blob' })

export const getQuoteImageOriginalContent = (quoteId: string, comboId: string, slotCode: string) =>
  fashionRequest<Blob>({ path: `quotes/${quoteId}/images/combinations/${comboId}/slots/${slotCode}/original`, responseType: 'blob' })
