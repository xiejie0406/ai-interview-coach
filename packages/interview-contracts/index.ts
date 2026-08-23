export type InterviewMode = 'TEXT' | 'CASCADE_VOICE'
export type InterviewState = 'READY' | 'IN_PROGRESS' | 'PAUSED' | 'COMPLETING' | 'COMPLETED' | 'CANCELLED' | 'FAILED_RECOVERABLE' | 'FAILED_FINAL'
export type Difficulty = 'JUNIOR' | 'MID' | 'SENIOR'

export interface QuestionSummary {
  id: string
  versionId: string
  title: string
  category: string
  difficulty: Difficulty
  estimatedMinutes?: number
  status: 'PUBLISHED'
  version: number
}

export interface InterviewPlanView {
  id: string
  planVersionNo: number
  state: 'DRAFT' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED'
  mode: InterviewMode
  questionCount: number
  followUpBudget: number
  estimatedUsage: { unit: 'TEXT_SESSION' | 'VOICE_SECOND'; quantity: number; estimateVersion: string }
  expiresAt: string
  version: number
}

export interface InterviewSnapshot {
  id: string
  planId: string
  mode: InterviewMode
  state: InterviewState
  turns: Array<{ turnId: string; sequence: number; state: string; questionText?: string | null }>
  lastStableTurnSequence: number
  allowedCommands: string[]
  voiceSummary: null | { state: string; transcriptId: string | null; audioArtifactId: string | null }
  version: number
}

export type VoiceUiState = 'IDLE' | 'PREFLIGHTING' | 'READY' | 'LISTENING' | 'RECORDED_LOCAL' | 'TRANSCRIBING' | 'CONFIRMING' | 'DEGRADED' | 'CANCELLED'

export interface RecordedAudio {
  localPath?: string
  blob?: Blob
  mimeType: string
  durationMs: number
  sizeBytes: number
  platform: 'web' | 'h5' | 'wechat' | 'app'
}
