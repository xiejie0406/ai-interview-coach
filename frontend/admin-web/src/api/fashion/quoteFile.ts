import { fashionRequest } from './client'
import type { DeliveryFile, DeliveryFileType, DeliveryWorkspace, RuoYiResult } from './types'

export const getDeliveryWorkspace = (quoteId: string, signal?: AbortSignal) =>
  fashionRequest<RuoYiResult<DeliveryWorkspace>>({ path: `quotes/${quoteId}/files`, signal })

export const requestDeliveryFile = (quoteId: string, fileType: DeliveryFileType) =>
  fashionRequest<RuoYiResult<DeliveryFile>, { fileType: DeliveryFileType; purpose: 'customer'; rendererVersion: string }>({
    path: `quotes/${quoteId}/files`, method: 'post',
    body: { fileType, purpose: 'customer', rendererVersion: 'fashion-delivery-1.0' }
  })

export const retryDeliveryFile = (quoteId: string, fileId: string, rowVersion: number) =>
  fashionRequest<RuoYiResult<DeliveryFile>>({
    path: `quotes/${quoteId}/files/${fileId}/retry`, method: 'post', params: { rowVersion }
  })

export const downloadDeliveryArtifact = (quoteId: string, fileId: string, artifactNo: number) =>
  fashionRequest<Blob>({ path: `quotes/${quoteId}/files/${fileId}/artifacts/${artifactNo}`, responseType: 'blob', timeoutMs: 120_000 })

export const extendDeliveryRetention = (quoteId: string, fileId: string, rowVersion: number, retainUntil: string) =>
  fashionRequest<RuoYiResult<DeliveryFile>, { rowVersion: number; retainUntil: string }>({
    path: `quotes/${quoteId}/files/${fileId}/retention`, method: 'post', body: { rowVersion, retainUntil }
  })
