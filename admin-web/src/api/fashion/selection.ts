import { fashionRequest } from './client'
import type {
  FashionRun,
  RuoYiResult,
  SelectionApplyResult,
  SelectionCombo,
  SelectionWorkspace,
} from './types'
import type { RunCapability } from './agent'

export const getSelectionWorkspace = (quoteId: string) =>
  fashionRequest<RuoYiResult<SelectionWorkspace>>({ path: `quotes/${quoteId}/selection` })

export const getSelectionCapability = () =>
  fashionRequest<RuoYiResult<RunCapability>>({ path: 'ai/runs/capabilities/selection-styling' })

export const createSelectionRun = (
  quoteId: string,
  requestKey: string,
  baseComboId?: string,
  comboVisualHash?: string,
) => fashionRequest<RuoYiResult<FashionRun>, Record<string, string | undefined>>({
  path: 'ai/runs/selection-styling',
  method: 'post',
  body: { quoteId, requestKey, baseComboId, comboVisualHash },
})

export const applySelectionRun = (
  runId: string,
  requestKey: string,
  quoteRowVersion: number,
  comboVisualHashes: Record<string, string>,
) => fashionRequest<RuoYiResult<SelectionApplyResult>, Record<string, unknown>>({
  path: `ai/runs/${runId}/apply-selection`,
  method: 'post',
  body: { requestKey, quoteRowVersion, comboVisualHashes },
})

export const updateComboLocks = (
  quoteId: string,
  combo: SelectionCombo,
  quoteRowVersion: number,
  lockedSlots: string[],
) => fashionRequest<RuoYiResult<SelectionWorkspace>, Record<string, unknown>>({
  path: `quotes/${quoteId}/selection/combinations/${combo.id}/locks`,
  method: 'put',
  body: {
    lockedSlots,
    quoteRowVersion,
    comboRowVersion: combo.rowVersion,
    visualHash: combo.visualHash,
  },
})

export const replaceComboCandidate = (
  quoteId: string,
  combo: SelectionCombo,
  quoteRowVersion: number,
  slotCode: string,
  candidateRef: string,
) => fashionRequest<RuoYiResult<SelectionWorkspace>, Record<string, unknown>>({
  path: `quotes/${quoteId}/selection/combinations/${combo.id}/replace`,
  method: 'post',
  body: {
    slotCode,
    candidateRef,
    quoteRowVersion,
    comboRowVersion: combo.rowVersion,
    comboVisualHash: combo.visualHash,
  },
})
