import type { EpochContext } from './operator-events'

export interface CollectionImage { imageId: string; group: string; order: number; status: string; assetId?: string; error?: string }
export interface CollectionSnapshot {
  snapshotId: string; coreTaskId?: string; generation: number; source: string; createdAt: string; status: string
  fields: Record<string, unknown>
  blocks: { type: string; text?: string; imageId?: string }[]
  images: CollectionImage[]
}
export interface CollectionCuration {
  snapshotId?: string
  revision: number; notes: string; tags: string[]; overrides: Record<string, string>
  removedImageIds: string[]; mainImageId: string | null; imageOrder: string[]
}
export interface CollectionItem { itemId: string; title: string; platform: string; sku?: string; snapshotId: string; version: number; deleted: boolean; updatedAt: string; status: string; generation: number; price?: string; priceText?: string; specification?: string; thumbnailAssetId?: string; imageSaved?: number; imageTotal?: number }
export interface CollectionVersionSummary { snapshotId: string; source: string; createdAt: string; status: string; assetManifestVersion?: number }
export interface CollectionVersionPage { items: CollectionVersionSummary[]; total: number; page: number; pageSize: number }
export interface CollectionDetail extends CollectionItem { snapshots: CollectionSnapshot[]; currentSnapshot: CollectionSnapshot; snapshotCount: number; curation: CollectionCuration }
export interface CollectionPage { items: CollectionItem[]; total: number; page: number; pageSize: number }
export interface CollectionBatch { results: { itemId: string; success: boolean; error?: string }[] }
export type CollectionAction = 'list' | 'detail' | 'versions' | 'snapshot' | 'create' | 'curation' | 'trash' | 'restore' | 'image' | 'upload' | 'export'
export interface CollectionOperation extends EpochContext { action: CollectionAction; input: Record<string, unknown> }
export interface CollectionApi {
  onOpenLibrary(listener: () => void): () => void
  execute<T = unknown>(operation: CollectionOperation): Promise<{ value: T; context: { workspaceId: string | null; sessionEpoch: number; workspaceEpoch: number } }>
}
