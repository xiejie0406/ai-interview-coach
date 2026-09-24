import { FASHION_API_PREFIX, fashionRequest } from './client'
import type { CatalogImportBatchView, CatalogImportKind, RuoYiResult } from './types'

export interface CatalogImportPreviewOptions {
  kind: CatalogImportKind
  file: File
  sourceCode: string
  categoryCode?: string
  warehouseCode?: string
  asOf: string
}

const segment = (kind: CatalogImportKind) => kind === 'price' ? 'prices' : 'stocks'

export function previewCatalogImport(options: CatalogImportPreviewOptions) {
  const body = new FormData()
  body.append('file', options.file)
  body.append('sourceCode', options.sourceCode)
  body.append('asOf', options.asOf)
  if (options.categoryCode) body.append('categoryCode', options.categoryCode)
  if (options.warehouseCode) body.append('warehouseCode', options.warehouseCode)
  return fashionRequest<RuoYiResult<CatalogImportBatchView>, FormData>({
    path: `imports/${segment(options.kind)}/preview`, method: 'post', body, timeoutMs: 120_000
  })
}

export function getCatalogImport(kind: CatalogImportKind, batchId: string) {
  return fashionRequest<RuoYiResult<CatalogImportBatchView>>({
    path: `imports/${segment(kind)}/${batchId}`
  })
}

export function publishCatalogImport(kind: CatalogImportKind, batchId: string, rowVersion: number) {
  return fashionRequest<RuoYiResult<CatalogImportBatchView>, { rowVersion: number }>({
    path: `imports/${segment(kind)}/${batchId}/publish`, method: 'post', body: { rowVersion }, timeoutMs: 120_000
  })
}

export function restoreCatalogImport(
  kind: CatalogImportKind,
  sourceBatchId: string,
  requestKey: string,
  operatorNote?: string
) {
  return fashionRequest<RuoYiResult<CatalogImportBatchView>, { requestKey: string; operatorNote?: string }>({
    path: `imports/${segment(kind)}/${sourceBatchId}/restore`, method: 'post',
    body: { requestKey, operatorNote }, timeoutMs: 120_000
  })
}

export function catalogTemplateUrl(kind: CatalogImportKind) {
  const base = String(import.meta.env.VITE_APP_BASE_API ?? '').replace(/\/$/, '')
  return `${base}${FASHION_API_PREFIX}/imports/${segment(kind)}/template`
}
