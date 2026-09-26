import { fashionRequest } from './client'
import type { FashionAgent, FashionAgentVersion, FashionProduct, FashionQuote, FashionRun, RuoYiResult } from './types'

export interface AgentCreateBody { agentCode: string; name: string; agentType: string; description?: string }
export interface AgentVersionBody {
  providerCode: string
  modelName: string
  systemInstruction: string
  modelConfig: Record<string, unknown>
  tools: unknown[]
  handoffs: unknown[]
  inputSchema: Record<string, unknown>
  outputSchema: Record<string, unknown>
  guardrails: Record<string, unknown>
  maxSteps: number
  timeoutSeconds: number
}
export interface RunCapability { enabled: boolean; reason?: string }

export const listAgents = () => fashionRequest<RuoYiResult<FashionAgent[]>>({ path: 'ai/agents' })
export const listAgentVersions = (agentId: string) => fashionRequest<RuoYiResult<FashionAgentVersion[]>>({ path: `ai/agents/${agentId}/versions` })
export const createAgent = (body: AgentCreateBody) => fashionRequest<RuoYiResult<FashionAgent>, AgentCreateBody>({ path: 'ai/agents', method: 'post', body })
export const createAgentVersion = (agentId: string, body: AgentVersionBody) => fashionRequest<RuoYiResult<FashionAgentVersion>, AgentVersionBody>({ path: `ai/agents/${agentId}/versions`, method: 'post', body })
export const publishAgentVersion = (agentId: string, versionId: string, rowVersion: number) => fashionRequest<RuoYiResult<FashionAgentVersion>, { rowVersion: number }>({ path: `ai/agents/${agentId}/versions/${versionId}/publish`, method: 'post', body: { rowVersion } })
export const getRequirementCapability = () => fashionRequest<RuoYiResult<RunCapability>>({ path: 'ai/runs/capabilities/requirement-analysis' })
export const getProductAttributeCapability = () => fashionRequest<RuoYiResult<RunCapability>>({ path: 'ai/runs/capabilities/product-attribute-suggestion' })
export const createRequirementRun = (quoteId: string, sourceText: string, requestKey: string) => fashionRequest<RuoYiResult<FashionRun>, { quoteId: string; sourceText: string; requestKey: string }>({ path: 'ai/runs/requirement-analysis', method: 'post', body: { quoteId, sourceText, requestKey } })
export const createProductAttributeRun = (productId: string, requestKey: string) => fashionRequest<RuoYiResult<FashionRun>, { productId: string; requestKey: string }>({ path: 'ai/runs/product-attribute-suggestion', method: 'post', body: { productId, requestKey } })
export const getRun = (id: string) => fashionRequest<RuoYiResult<FashionRun>>({ path: `ai/runs/${id}` })
export const cancelRun = (id: string, rowVersion: number) => fashionRequest<RuoYiResult<FashionRun>, { rowVersion: number }>({ path: `ai/runs/${id}/cancel`, method: 'put', body: { rowVersion } })
export const applyRequirementRun = (id: string, requestKey: string, quoteRowVersion: number) => fashionRequest<RuoYiResult<FashionQuote>, { requestKey: string; quoteRowVersion: number }>({ path: `ai/runs/${id}/apply-requirement`, method: 'post', body: { requestKey, quoteRowVersion } })
export const applyProductAttributeRun = (id: string, requestKey: string, productRowVersion: number) => fashionRequest<RuoYiResult<FashionProduct>, { requestKey: string; productRowVersion: number }>({ path: `ai/runs/${id}/apply-product-attributes`, method: 'post', body: { requestKey, productRowVersion } })
