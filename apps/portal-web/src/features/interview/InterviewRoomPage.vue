<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiRequest, apiRequestWithMeta, jsonBody, newOperationKey } from '@/shared/api/client'

type Turn = {
  turnId: string
  sequence: number
  state: string
  kind?: 'PRIMARY' | 'FOLLOW_UP' | 'CLARIFICATION'
  questionText?: string | null
  answerVersionId?: string | null
}
type Snapshot = {
  id: string
  state: string
  mode: 'TEXT' | 'CASCADE_VOICE'
  turns: Turn[]
  allowedCommands: string[]
  version: number
  failureCode?: string | null
}
type Policy = { purpose: string; versionId: string; requiredForRegistration: boolean }
type Preflight = { enabled: boolean; consentRequired: boolean; supportedCodecs: string[]; maxDurationSeconds: number; maxBytes: number; unavailableReasonCode?: string | null }
type VoiceHandle = { voiceSessionId: string; websocketPath: string; socketTicket: string; expiresAt: string; codec: string; socketGeneration: number; initialServerSequence: number; maxInFlightChunks: number; maxBufferedDurationMs: number; maxChunkBytes: number; maxDurationSeconds: number; maxBytes: number }
type TranscriptFinal = { transcriptId: string; transcriptVersionId: string; text: string; transcriptVersion: number; lowConfidenceSpans: Array<{ startInclusive: number; endExclusive: number; confidence: number }> }
type ServerEnvelope = { type: string; sequence: number; data: Record<string, any> }
type LocalAnswer = { text: string; source: 'TEXT' | 'VOICE'; durationMs?: number }
type OperationAccepted = { operationId: string; statusUrl: string }

const route = useRoute()
const router = useRouter()
const snapshot = ref<Snapshot | null>(null)
const etag = ref('')
const answer = ref('')
const error = ref('')
const loading = ref(false)
const busy = ref(false)
const waitingForInterviewer = ref(false)
const voiceNoticeAccepted = ref(false)
const voiceLanguage = ref('普通话')
const autoPlayQuestion = ref(false)
const draftSaved = ref(true)
const messagesElement = ref<HTMLElement | null>(null)
const localAnswers = ref<Record<string, LocalAnswer>>({})
const voiceState = ref<'IDLE'|'PREPARING'|'LISTENING'|'TRANSCRIBING'|'REVIEWING'|'UNAVAILABLE'>('IDLE')
const recordedBytes = ref(0)
const transcript = ref<TranscriptFinal | null>(null)
const ttsState = ref<'IDLE'|'BUFFERING'|'PLAYING'|'FAILED'>('IDLE')

let mediaStream: MediaStream | null = null
let recorder: MediaRecorder | null = null
let socket: WebSocket | null = null
let voiceHandle: VoiceHandle | null = null
let clientSequence = 0
let lastServerSequence = 0
let totalBytes = 0
let totalDuration = 0
let chunkQueue: Promise<void> = Promise.resolve()
let ttsChunks: Uint8Array[] = []
let pendingChunks = new Map<number, number>()
let serverPaused = false
let ttsAudio: HTMLAudioElement | null = null
let ttsObjectUrl: string | null = null
let disposed = false
let draftTimer: number | undefined

const interviewId = computed(() => String(route.params.interviewId ?? ''))
const orderedTurns = computed(() => [...(snapshot.value?.turns ?? [])].sort((left, right) => left.sequence - right.sequence))
const currentTurn = computed(() => snapshot.value?.state === 'IN_PROGRESS'
  ? [...orderedTurns.value].reverse().find(turn => turn.state === 'QUESTION_COMMITTED')
  : undefined)
const terminal = computed(() => ['COMPLETED', 'CANCELLED', 'FAILED_FINAL'].includes(snapshot.value?.state ?? ''))
const completedCount = computed(() => orderedTurns.value.filter(turn => ['ANSWER_CONFIRMED', 'CLOSED', 'SKIPPED'].includes(turn.state)).length)
const currentSequence = computed(() => currentTurn.value?.sequence ?? orderedTurns.value.at(-1)?.sequence ?? 0)
const lowConfidence = computed(() => transcript.value?.lowConfidenceSpans?.length ?? 0)
const sessionTitle = computed(() => `${snapshot.value?.mode === 'CASCADE_VOICE' ? '语音' : '文字'}模拟面试 · ${interviewId.value.slice(0, 8)}`)
const stateLabel = computed(() => ({
  READY: '待开始', IN_PROGRESS: '进行中', PAUSED: '已暂停', COMPLETING: '结束处理中',
  COMPLETED: '已完成', CANCELLED: '已取消', FAILED_RECOVERABLE: '可恢复失败', FAILED_FINAL: '已失败',
}[snapshot.value?.state ?? ''] ?? '加载中'))

async function scrollToBottom() {
  await nextTick()
  if (messagesElement.value) messagesElement.value.scrollTop = messagesElement.value.scrollHeight
}

function applySnapshot(response: { data: Snapshot; etag?: string }) {
  snapshot.value = response.data
  etag.value = response.etag ?? `"v${response.data.version}"`
}

async function load(showLoading = true) {
  if (!interviewId.value) {
    error.value = '缺少面试 ID'
    return
  }
  if (showLoading) loading.value = true
  error.value = ''
  try {
    applySnapshot(await apiRequestWithMeta<Snapshot>(`/interviews/${encodeURIComponent(interviewId.value)}`))
    await scrollToBottom()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '面试会话恢复失败'
  } finally {
    if (showLoading) loading.value = false
  }
}

async function startInterview() {
  if (!snapshot.value) return
  busy.value = true
  error.value = ''
  try {
    applySnapshot(await apiRequestWithMeta<Snapshot>(`/interviews/${snapshot.value.id}/commands/start`, {
      method: 'POST', headers: { 'If-Match': etag.value, 'Idempotency-Key': newOperationKey() },
    }))
    await scrollToBottom()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '开始面试失败'
  } finally {
    busy.value = false
  }
}

async function waitForNextTurn(previousTurnId: string) {
  waitingForInterviewer.value = true
  try {
    for (let attempt = 0; attempt < 15 && !disposed; attempt += 1) {
      await new Promise(resolve => window.setTimeout(resolve, attempt === 0 ? 500 : 900))
      const response = await apiRequestWithMeta<Snapshot>(`/interviews/${encodeURIComponent(interviewId.value)}`)
      applySnapshot(response)
      const active = response.data.turns.find(turn => turn.state === 'QUESTION_COMMITTED')
      if (active?.turnId !== previousTurnId || ['COMPLETED', 'CANCELLED', 'FAILED_FINAL'].includes(response.data.state)) {
        await scrollToBottom()
        return
      }
    }
    error.value = 'AI 面试官仍在生成下一步。当前回答已经提交，可稍后刷新或重新进入会话恢复。'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '等待 AI 面试官回复失败'
  } finally {
    waitingForInterviewer.value = false
  }
}

async function submitAnswer() {
  const activeTurn = currentTurn.value
  const value = answer.value.trim()
  if (!snapshot.value || !activeTurn || !value) return

  busy.value = true
  error.value = ''
  const submittedAnswer: LocalAnswer = {
    text: value,
    source: transcript.value ? 'VOICE' : 'TEXT',
    durationMs: transcript.value ? totalDuration : undefined,
  }
  try {
    if (transcript.value) {
      await apiRequest(`/transcripts/${encodeURIComponent(transcript.value.transcriptId)}/commands/confirm`, {
        method: 'POST',
        headers: { 'If-Match': `"v${transcript.value.transcriptVersion}"`, 'Idempotency-Key': newOperationKey() },
        body: jsonBody({ transcriptVersionId: transcript.value.transcriptVersionId, correctedText: value, lowConfidenceAcknowledged: true }),
      })
    } else {
      await apiRequest(`/interviews/${snapshot.value.id}/answers`, {
        method: 'POST',
        headers: { 'If-Match': etag.value, 'Idempotency-Key': newOperationKey() },
        body: jsonBody({ turnId: activeTurn.turnId, turnSequence: activeTurn.sequence, text: value }),
      })
    }
    localAnswers.value = { ...localAnswers.value, [activeTurn.turnId]: submittedAnswer }
    answer.value = ''
    transcript.value = null
    cleanupVoice(true)
    voiceState.value = 'IDLE'
    await scrollToBottom()
    await waitForNextTurn(activeTurn.turnId)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '回答提交失败'
  } finally {
    busy.value = false
  }
}

async function command(name: string) {
  if (!snapshot.value) return
  cancelVoice('SESSION_COMMAND')
  busy.value = true
  error.value = ''
  try {
    const response = await apiRequestWithMeta<Snapshot | OperationAccepted>(`/interviews/${snapshot.value.id}/commands/${name}`, {
      method: 'POST', headers: { 'If-Match': etag.value, 'Idempotency-Key': newOperationKey() }, body: jsonBody({}),
    })
    if ('turns' in response.data) applySnapshot({ data: response.data, etag: response.etag })
    else await load(false)
    await scrollToBottom()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '面试操作失败'
  } finally {
    busy.value = false
  }
}

function repeatCurrentQuestion() {
  const element = document.getElementById(`turn-${currentTurn.value?.turnId ?? ''}`)
  element?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function markDraftChanged() {
  draftSaved.value = false
  window.clearTimeout(draftTimer)
  draftTimer = window.setTimeout(() => { draftSaved.value = true }, 500)
}

function clearAnswer() {
  answer.value = ''
  transcript.value = null
  voiceState.value = 'IDLE'
  cleanupVoice(true)
}

async function grantVoicePolicies() {
  const policies = (await apiRequest<{ policies: Policy[] }>('/policies/current')).policies
  for (const purpose of ['VOICE_CAPTURE', 'MODEL_PROCESSING']) {
    const policy = policies.find(item => item.purpose === purpose)
    if (!policy) throw new Error(`缺少 ${purpose} 服务端政策`)
    await apiRequest(`/consents/${purpose}/grants`, {
      method: 'POST', headers: { 'Idempotency-Key': newOperationKey() },
      body: jsonBody({ policyVersionId: policy.versionId, acknowledgement: true }),
    })
  }
}

function supportedCodecs() {
  return ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/wav'].filter(type => MediaRecorder.isTypeSupported(type))
}

async function startVoice() {
  if (!voiceNoticeAccepted.value || !snapshot.value || !currentTurn.value) {
    error.value = '请先确认本轮语音采集和模型转写说明。'
    return
  }
  error.value = ''
  voiceState.value = 'PREPARING'
  transcript.value = null
  answer.value = ''
  try {
    await grantVoicePolicies()
    const codecs = supportedCodecs()
    if (!codecs.length) throw new Error('当前浏览器没有可用录音格式')
    const preflight = await apiRequest<Preflight>(`/interviews/${snapshot.value.id}/voice-preflight`, {
      method: 'POST', body: jsonBody({ turnId: currentTurn.value.turnId, codecCandidates: codecs }),
    })
    if (!preflight.enabled || preflight.consentRequired) throw new Error(preflight.unavailableReasonCode || '服务端语音能力不可用')
    const codec = codecs.find(item => preflight.supportedCodecs.includes(item))
    if (!codec) throw new Error('服务端不支持当前录音格式')
    voiceHandle = await apiRequest<VoiceHandle>(`/interviews/${snapshot.value.id}/voice-sessions`, {
      method: 'POST', headers: { 'Idempotency-Key': newOperationKey() },
      body: jsonBody({ turnId: currentTurn.value.turnId, codec, expectedSessionVersion: snapshot.value.version }),
    })
    connectVoiceSocket(voiceHandle)
  } catch (cause) {
    voiceState.value = 'UNAVAILABLE'
    error.value = `${cause instanceof Error ? cause.message : '语音准备失败'}，可直接输入文字回答。`
    cleanupVoice(false)
  }
}

function connectVoiceSocket(handle: VoiceHandle) {
  releaseTtsAudio()
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const token = localStorage.getItem('ruoyi-token')
  if (!token) { voiceState.value = 'UNAVAILABLE'; error.value = '登录状态已失效，请重新登录。'; return }
  socket = new WebSocket(`${protocol}//${location.host}${handle.websocketPath}`, ['aic.voice.v1', `ruoyi-bearer.${token}`])
  clientSequence = 0
  lastServerSequence = handle.initialServerSequence
  totalBytes = 0
  totalDuration = 0
  recordedBytes.value = 0
  pendingChunks.clear()
  serverPaused = false
  socket.onopen = () => sendVoice('client.hello', { socketTicket: handle.socketTicket, protocolVersion: 1, resumeFromServerSequence: handle.initialServerSequence, capabilities: ['BASE64_AUDIO', 'TRANSCRIPT_REVIEW'] })
  socket.onmessage = event => void handleServerMessage(JSON.parse(String(event.data)))
  socket.onerror = () => { error.value = '语音连接失败，可直接输入文字回答。'; voiceState.value = 'UNAVAILABLE' }
  socket.onclose = () => { if (!['REVIEWING', 'UNAVAILABLE', 'IDLE'].includes(voiceState.value)) voiceState.value = 'UNAVAILABLE' }
}

async function handleServerMessage(message: ServerEnvelope) {
  if (message.sequence !== lastServerSequence + 1) {
    error.value = '语音消息序号异常，请改用文字回答。'
    cancelVoice('SERVER_SEQUENCE_GAP')
    return
  }
  lastServerSequence = message.sequence
  if (message.type === 'server.hello') {
    try {
      await beginCapture()
    } catch (cause) {
      error.value = `${cause instanceof Error ? cause.message : '无法打开麦克风'}，可直接输入文字回答。`
      voiceState.value = 'UNAVAILABLE'
      cleanupVoice(true)
    }
  }
  else if (message.type === 'voice.turn.state' && message.data.state === 'TRANSCRIBING') voiceState.value = 'TRANSCRIBING'
  else if (message.type === 'asr.final') {
    transcript.value = message.data as TranscriptFinal
    answer.value = transcript.value.text
    voiceState.value = 'REVIEWING'
    cleanupVoice(false)
  } else if (message.type === 'speech.failed' || message.type === 'voice.turn.degraded') {
    error.value = `语音识别失败（${message.data.reasonCode || 'UNKNOWN'}），可直接输入文字回答。`
    voiceState.value = 'UNAVAILABLE'
    cleanupVoice(false)
  } else if (message.type === 'server.nack' || message.type === 'server.resync-required') {
    error.value = '语音连接需要重新同步，请改用文字或重新录音。'
    cancelVoice('SERVER_RESYNC_REQUIRED')
  } else if (message.type === 'server.ack') {
    const accepted = Number(message.data.acceptedClientSequence)
    if (Number.isSafeInteger(accepted)) for (const sequence of pendingChunks.keys()) if (sequence <= accepted) pendingChunks.delete(sequence)
    if (serverPaused && pendingChunks.size < (voiceHandle?.maxInFlightChunks ?? 8)) {
      serverPaused = false
      if (recorder?.state === 'paused') recorder.resume()
    }
  } else if (message.type === 'server.flow-control') {
    serverPaused = message.data.paused === true
    if (serverPaused && recorder?.state === 'recording') recorder.pause()
    if (!serverPaused && recorder?.state === 'paused') recorder.resume()
  } else if (message.type === 'tts.state') {
    if (message.data.state === 'STARTED') { ttsChunks = []; ttsState.value = 'BUFFERING' }
    else if (message.data.state === 'COMPLETED') { if (!ttsChunks.length) ttsState.value = 'FAILED' }
    else if (message.data.state === 'FAILED') { releaseTtsAudio(); ttsState.value = 'FAILED' }
    else if (message.data.state === 'CANCELLED') { releaseTtsAudio(); ttsState.value = 'IDLE' }
  } else if (message.type === 'tts.chunk') {
    ttsChunks.push(base64ToBytes(String(message.data.bytesBase64 ?? '')))
    if (message.data.endOfOutput === true) playTts()
  }
}

async function beginCapture() {
  if (!voiceHandle) return
  mediaStream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true }, video: false })
  recorder = new MediaRecorder(mediaStream, { mimeType: voiceHandle.codec })
  recorder.ondataavailable = event => {
    if (event.data.size <= 0) return
    chunkQueue = chunkQueue.then(async () => {
      const bytes = await event.data.arrayBuffer()
      if (bytes.byteLength > voiceHandle!.maxChunkBytes || totalBytes + bytes.byteLength > voiceHandle!.maxBytes
          || totalDuration + 250 > voiceHandle!.maxDurationSeconds * 1000
          || serverPaused || pendingChunks.size >= (voiceHandle!.maxInFlightChunks ?? 8)
          || pendingBufferedDuration() + 250 > (voiceHandle!.maxBufferedDurationMs ?? 4000)) {
        cancelVoice('AUDIO_LIMIT_OR_BACKPRESSURE')
        return
      }
      totalBytes += bytes.byteLength
      totalDuration += 250
      recordedBytes.value = totalBytes
      const sequence = sendVoice('client.audio.chunk', { bytesBase64: arrayBufferToBase64(bytes), durationMs: 250 })
      if (sequence) pendingChunks.set(sequence, 250)
    })
  }
  recorder.onstop = () => { chunkQueue.then(() => sendVoice('client.audio.stop', { totalBytes, totalDurationMs: totalDuration })).catch(() => cancelVoice('CHUNK_SEND_FAILED')) }
  recorder.onerror = () => cancelVoice('RECORDER_ERROR')
  sendVoice('client.audio.start', { codec: voiceHandle.codec, sampleRate: 48000, channelCount: 1 })
  recorder.start(250)
  voiceState.value = 'LISTENING'
}

function finishVoice() { if (recorder?.state === 'recording') recorder.stop() }
function cancelVoice(reason = 'USER_CANCELLED_RECORDING') {
  if (socket?.readyState === WebSocket.OPEN && voiceState.value === 'LISTENING') sendVoice('client.audio.cancel', { reasonCode: reason })
  cleanupVoice(true)
  if (voiceState.value !== 'REVIEWING') voiceState.value = 'IDLE'
}
function cancelTts() {
  if (socket?.readyState === WebSocket.OPEN) {
    sendVoice('client.tts.cancel', { reasonCode: 'USER_CANCELLED_PLAYBACK' })
  }
  releaseTtsAudio()
  ttsState.value = 'IDLE'
}
function playTts() {
  const blob = new Blob(ttsChunks, { type: 'audio/ogg;codecs=opus' })
  releaseTtsAudio()
  ttsObjectUrl = URL.createObjectURL(blob)
  ttsAudio = new Audio(ttsObjectUrl)
  ttsAudio.onended = () => { releaseTtsAudio(); ttsState.value = 'IDLE' }
  ttsAudio.onerror = () => { releaseTtsAudio(); ttsState.value = 'FAILED' }
  ttsState.value = 'PLAYING'
  void ttsAudio.play().catch(() => { ttsState.value = 'FAILED' })
}
function releaseTtsAudio() {
  ttsAudio?.pause()
  ttsAudio = null
  if (ttsObjectUrl) URL.revokeObjectURL(ttsObjectUrl)
  ttsObjectUrl = null
  ttsChunks = []
}
function pendingBufferedDuration() {
  let total = 0
  pendingChunks.forEach(duration => { total += duration })
  return total
}
function cleanupVoice(closeSocket: boolean) {
  if (recorder?.state === 'recording') recorder.stop()
  mediaStream?.getTracks().forEach(track => track.stop())
  recorder = null
  mediaStream = null
  const currentSocket = socket
  if (closeSocket && currentSocket && currentSocket.readyState <= WebSocket.OPEN) currentSocket.close(1000, 'client_cleanup')
  if (closeSocket) socket = null
  pendingChunks.clear()
}
function sendVoice(type: string, data: Record<string, unknown>) {
  if (!socket || socket.readyState !== WebSocket.OPEN || !voiceHandle) return 0
  clientSequence += 1
  const sequence = clientSequence
  socket.send(JSON.stringify({ messageId: crypto.randomUUID(), type, voiceSessionId: voiceHandle.voiceSessionId, sessionId: snapshot.value?.id, turnId: currentTurn.value?.turnId, generation: voiceHandle.socketGeneration, sequence, ackSequence: lastServerSequence, occurredAt: new Date().toISOString(), schemaVersion: 1, data }))
  return sequence
}
function arrayBufferToBase64(buffer: ArrayBuffer) {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (let index = 0; index < bytes.length; index += 0x8000) binary += String.fromCharCode(...bytes.subarray(index, Math.min(index + 0x8000, bytes.length)))
  return btoa(binary)
}
function base64ToBytes(value: string) {
  const binary = atob(value)
  return Uint8Array.from(binary, character => character.charCodeAt(0))
}
function formatDuration(durationMs?: number) {
  if (!durationMs) return ''
  const totalSeconds = Math.max(1, Math.round(durationMs / 1000))
  return `${String(Math.floor(totalSeconds / 60)).padStart(2, '0')}:${String(totalSeconds % 60).padStart(2, '0')}`
}

onMounted(() => void load())
onBeforeUnmount(() => {
  disposed = true
  window.clearTimeout(draftTimer)
  cancelVoice('COMPONENT_UNMOUNTED')
  releaseTtsAudio()
})
</script>

<template>
  <section class="interview-workbench">
    <aside class="session-column" aria-label="面试会话">
      <header class="column-header">
        <div><strong>面试会话</strong><small>当前与历史面试</small></div>
        <button class="square-action" type="button" aria-label="新建面试" @click="router.push('/interviews/new')">＋</button>
      </header>
      <div class="session-search"><input type="search" placeholder="搜索会话" disabled aria-label="搜索会话" /></div>
      <div class="session-list">
        <p class="list-label">当前会话</p>
        <button class="session-entry active" type="button">
          <strong>{{ sessionTitle }}</strong>
          <span><b>{{ stateLabel }}</b><small>第 {{ currentSequence || 0 }} 题</small></span>
          <em>{{ currentTurn?.questionText ?? '等待 AI 面试官发问' }}</em>
        </button>
        <p class="list-label">历史会话</p>
        <div class="session-empty">当前服务尚未提供会话列表接口。完成接口后将在这里恢复历史会话。</div>
      </div>
    </aside>

    <main class="conversation-column">
      <header class="conversation-header">
        <div class="interviewer">
          <span class="interviewer-avatar">AI</span>
          <div><h1>AI 面试官</h1><p>依据你的回答继续追问</p></div>
        </div>
        <div class="conversation-actions">
          <span class="question-counter">第 {{ currentSequence || 0 }} 题</span>
          <button class="quiet-action" type="button" :disabled="!currentTurn" @click="repeatCurrentQuestion">定位当前问题</button>
        </div>
      </header>

      <div ref="messagesElement" class="message-stream" aria-live="polite">
        <div class="message-inner">
          <div v-if="loading" class="stream-state">正在恢复面试会话…</div>
          <div v-else-if="error" class="stream-error" role="alert">{{ error }}<button type="button" @click="load()">重新加载</button></div>

          <article class="chat-message ai-message">
            <span class="message-avatar">AI</span>
            <div class="message-content">
              <small>AI 面试官</small>
              <p v-if="snapshot?.state === 'READY'">面试计划已经准备好。点击“开始面试”后，我会发送第一道问题。</p>
              <p v-else>你好。我会根据你的回答动态追问。你可以使用文字或语音回答，正式发送前都可以修改。</p>
            </div>
          </article>

          <template v-for="turn in orderedTurns" :key="turn.turnId">
            <article :id="`turn-${turn.turnId}`" class="chat-message ai-message">
              <span class="message-avatar">AI</span>
              <div class="message-content">
                <small>AI 面试官 · 问题 {{ turn.sequence }}</small>
                <div class="question-message">
                  <b>{{ turn.kind === 'FOLLOW_UP' ? '追问' : turn.kind === 'CLARIFICATION' ? '澄清' : `问题 ${turn.sequence}` }}</b>
                  <p>{{ turn.questionText ?? '问题正在生成中…' }}</p>
                </div>
              </div>
            </article>

            <article v-if="localAnswers[turn.turnId]" class="chat-message user-message">
              <div class="message-content">
                <small>你</small>
                <p>{{ localAnswers[turn.turnId].text }}</p>
                <em>{{ localAnswers[turn.turnId].source === 'VOICE' ? `语音回答 · ${formatDuration(localAnswers[turn.turnId].durationMs)} · 已确认转写` : '文字回答 · 已发送' }}</em>
              </div>
            </article>
            <div v-else-if="['ANSWER_CONFIRMED', 'CLOSED'].includes(turn.state)" class="answer-recovery-note">该题已回答；当前会话快照未返回历史答案正文。</div>
            <div v-else-if="turn.state === 'SKIPPED'" class="answer-recovery-note">该题已跳过。</div>
          </template>

          <div v-if="waitingForInterviewer" class="thinking-state"><span></span><span></span><span></span><em>AI 面试官正在分析回答并生成下一步</em></div>
          <div v-if="snapshot?.state === 'PAUSED'" class="stream-state">面试已暂停，恢复后可继续回答当前问题。</div>
          <div v-if="snapshot?.state === 'COMPLETED'" class="completion-state"><strong>面试已完成</strong><span>本次 MVP 到此结束，当前版本暂不生成评分报告。</span></div>
        </div>
      </div>

      <footer class="composer-area">
        <div v-if="snapshot?.state === 'READY'" class="start-panel"><button class="send-action" type="button" :disabled="busy" @click="startInterview">{{ busy ? '正在开始…' : '开始面试' }}</button></div>
        <div v-else-if="currentTurn && !terminal" class="composer-shell">
          <label v-if="snapshot?.mode === 'CASCADE_VOICE'" class="voice-consent"><input v-model="voiceNoticeAccepted" type="checkbox" />我同意本轮语音采集和模型转写；失败后仍可输入文字</label>
          <div v-if="voiceState === 'LISTENING'" class="recording-state">
            <div><strong>正在聆听</strong><span>已上传 {{ recordedBytes }} bytes</span></div>
            <button class="danger-action" type="button" @click="finishVoice">结束回答</button>
          </div>
              <div v-else-if="voiceState === 'TRANSCRIBING' || voiceState === 'PREPARING'" class="recording-state"><div><strong>{{ voiceState === 'PREPARING' ? '正在准备语音…' : '正在整理语音转写…' }}</strong><span>完成后可检查和修改文字</span></div></div>
              <div v-if="ttsState !== 'IDLE'" class="recording-state"><div><strong>{{ ttsState === 'PLAYING' ? '正在播放下一题' : ttsState === 'BUFFERING' ? '正在准备题目语音' : '题目语音不可用' }}</strong><span>文字问题仍可继续使用</span></div><button v-if="ttsState === 'PLAYING' || ttsState === 'BUFFERING'" type="button" @click="cancelTts">停止播放</button></div>
          <template v-else>
            <div v-if="transcript" class="transcript-notice"><strong>语音已转写，请检查后发送</strong><span v-if="lowConfidence">{{ lowConfidence }} 处低置信术语需要确认</span></div>
            <textarea v-model="answer" rows="3" maxlength="30000" aria-label="面试回答" placeholder="输入回答，或点击语音开始说话……" @input="markDraftChanged" @keydown.ctrl.enter.prevent="submitAnswer" />
            <div class="composer-toolbar">
              <div class="composer-tools">
                <button v-if="snapshot?.mode === 'CASCADE_VOICE'" class="quiet-action" type="button" :disabled="busy || voiceState === 'REVIEWING'" @click="startVoice">语音回答</button>
                <button class="quiet-action" type="button" :disabled="busy || !answer" @click="clearAnswer">清空</button>
                <span class="draft-label">{{ draftSaved ? '草稿已保存' : '正在保存草稿' }}</span>
              </div>
              <button class="send-action" type="button" :disabled="busy || waitingForInterviewer || !answer.trim()" @click="submitAnswer">{{ busy ? '正在发送…' : transcript ? '确认转写并发送' : '发送' }}</button>
            </div>
          </template>
        </div>
        <p v-if="currentTurn && !terminal" class="composer-caption">发送后将成为正式面试回答，AI 会继续追问。Ctrl + Enter 快速发送。</p>
      </footer>
    </main>

    <aside class="inspector-column" aria-label="面试概览">
      <header class="column-header"><div><strong>面试概览</strong><small>进度与会话设置</small></div><span class="online-state">在线</span></header>
      <div class="inspector-scroll">
        <section class="info-section">
          <h2>基本信息</h2>
          <dl><dt>会话状态</dt><dd>{{ stateLabel }}</dd><dt>面试方式</dt><dd>{{ snapshot?.mode === 'CASCADE_VOICE' ? '语音 + 文字' : '文字' }}</dd><dt>当前问题</dt><dd>第 {{ currentSequence || 0 }} 题</dd><dt>已完成</dt><dd>{{ completedCount }} 题</dd></dl>
        </section>
        <section class="info-section">
          <h2>考察进度</h2>
          <ol class="turn-progress">
            <li v-for="turn in orderedTurns" :key="turn.turnId" :class="{ done: ['ANSWER_CONFIRMED','CLOSED','SKIPPED'].includes(turn.state), current: turn.turnId === currentTurn?.turnId }"><span>{{ turn.sequence }}</span><p><b>{{ turn.kind === 'FOLLOW_UP' ? '动态追问' : `问题 ${turn.sequence}` }}</b><small>{{ turn.state === 'QUESTION_COMMITTED' ? '等待回答' : turn.state }}</small></p></li>
          </ol>
        </section>
        <section class="info-section">
          <h2>会话设置</h2>
          <label class="setting-row"><span><b>语音识别</b><small>识别语言和方言</small></span><select v-model="voiceLanguage" :disabled="snapshot?.mode !== 'CASCADE_VOICE'"><option>自动识别</option><option>普通话</option><option>粤语</option><option>四川话</option><option>中英混合</option></select></label>
          <label class="setting-row"><span><b>自动播放问题</b><small>待 TTS 输出能力接入</small></span><input v-model="autoPlayQuestion" type="checkbox" disabled /></label>
        </section>
        <section class="info-section">
          <h2>会话操作</h2>
          <div class="session-actions">
            <button v-if="snapshot?.allowedCommands?.includes('PAUSE')" class="secondary-action" type="button" :disabled="busy" @click="command('pause')">暂停面试</button>
            <button v-if="snapshot?.allowedCommands?.includes('RESUME')" class="secondary-action" type="button" :disabled="busy" @click="command('resume')">继续面试</button>
            <button v-if="snapshot?.allowedCommands?.includes('SKIP')" class="secondary-action" type="button" :disabled="busy" @click="command('skip')">跳过当前题</button>
            <button v-if="snapshot?.allowedCommands?.includes('COMPLETE')" class="danger-outline" type="button" :disabled="busy" @click="command('complete')">提前结束</button>
          </div>
        </section>
      </div>
    </aside>
  </section>
</template>

<style scoped>
.interview-workbench{display:grid;grid-template-columns:248px minmax(520px,1fr) 318px;height:calc(100dvh - 68px);min-height:650px;overflow:hidden;background:#f7f8fa;border-top:1px solid #e4e8ef}.session-column,.inspector-column{min-width:0;min-height:0;display:flex;flex-direction:column;background:#fff}.session-column{border-right:1px solid #e1e5ec}.inspector-column{border-left:1px solid #e1e5ec}.column-header{min-height:70px;padding:13px 16px;display:flex;align-items:center;justify-content:space-between;gap:12px;border-bottom:1px solid #edf0f4}.column-header strong{display:block;font-size:15px}.column-header small{display:block;color:#7b8497;font-size:12px}.square-action{width:44px;height:44px;border:0;border-radius:12px;color:#fff;background:#3157d5;font-size:23px}.session-search{padding:12px 14px 7px}.session-search input{min-height:42px;background:#f7f8fb}.session-list,.inspector-scroll{min-height:0;overflow:auto}.session-list{padding:4px 10px 18px}.list-label{margin:14px 10px 7px;color:#7b8497;font-size:12px;font-weight:700}.session-entry{width:100%;padding:12px;text-align:left;border:1px solid transparent;border-radius:12px;color:#172033;background:transparent}.session-entry.active{border-color:#d8e0ff;background:#eef2ff}.session-entry strong,.session-entry em{display:block}.session-entry span{display:flex;justify-content:space-between;gap:8px;margin-top:4px;font-size:12px}.session-entry b{color:#187a5e}.session-entry small{color:#7b8497}.session-entry em{margin-top:5px;overflow:hidden;color:#697386;font-size:12px;font-style:normal;text-overflow:ellipsis;white-space:nowrap}.session-empty{padding:20px 12px;color:#7b8497;font-size:13px;line-height:1.7}
.conversation-column{min-width:0;min-height:0;display:grid;grid-template-rows:auto minmax(0,1fr) auto;background:#fbfbfc}.conversation-header{min-height:70px;padding:12px 24px;display:flex;align-items:center;justify-content:space-between;gap:16px;background:rgba(255,255,255,.95);border-bottom:1px solid #edf0f4}.interviewer{display:flex;align-items:center;gap:11px}.interviewer-avatar,.message-avatar{display:grid;place-items:center;border-radius:50%;font-weight:800}.interviewer-avatar{width:38px;height:38px;color:#fff;background:#3157d5;font-size:13px}.interviewer h1{margin:0;font-size:16px}.interviewer p{margin:1px 0 0;color:#7b8497;font-size:12px}.conversation-actions{display:flex;align-items:center;gap:8px}.question-counter,.online-state,.draft-label{padding:4px 9px;border-radius:999px;font-size:12px}.question-counter{color:#526078;background:#eff1f5}.online-state{color:#187a5e;background:#eaf7f2}.message-stream{min-height:0;overflow-y:auto;scroll-behavior:smooth;padding:28px clamp(20px,5vw,70px) 36px}.message-inner{width:min(820px,100%);margin:0 auto}.chat-message{display:grid;grid-template-columns:34px minmax(0,1fr);gap:11px;margin-bottom:27px}.message-avatar{width:34px;height:34px;color:#3157d5;background:#eef2ff;font-size:11px}.message-content{min-width:0}.message-content>small{display:block;margin-bottom:5px;color:#7b8497;font-size:12px}.message-content>p{margin:0;white-space:pre-wrap}.question-message{margin-top:9px;padding:16px 18px;border-left:3px solid #3157d5;border-radius:0 12px 12px 0;background:#fff;box-shadow:0 8px 22px rgba(23,32,51,.05)}.question-message b{color:#3157d5;font-size:12px}.question-message p{margin:5px 0 0;font-size:17px;line-height:1.75}.user-message{display:flex;justify-content:flex-end;padding-left:70px}.user-message .message-content{max-width:min(680px,90%);padding:12px 15px;border-radius:14px 14px 4px 14px;background:#eef2ff}.user-message .message-content>small{text-align:right}.user-message em{display:block;margin-top:7px;color:#697386;font-size:12px;font-style:normal}.answer-recovery-note{margin:-16px 0 24px 45px;color:#8a93a4;font-size:12px}.thinking-state{display:flex;align-items:center;gap:4px;margin:0 0 24px 45px;color:#697386}.thinking-state span{width:6px;height:6px;border-radius:50%;background:#98a2b3;animation:thinking 1.1s infinite}.thinking-state span:nth-child(2){animation-delay:.12s}.thinking-state span:nth-child(3){animation-delay:.24s}.thinking-state em{margin-left:5px;font-size:13px;font-style:normal}@keyframes thinking{50%{transform:translateY(-3px);opacity:.55}}.stream-state,.stream-error,.completion-state{margin:0 0 22px 45px;padding:13px;border-radius:10px}.stream-state{color:#53617a;background:#f0f2f6}.stream-error{color:#a72d27;background:#fff1f0}.stream-error button{margin-left:8px;border:0;color:inherit;background:transparent;text-decoration:underline}.completion-state{display:grid;gap:3px;color:#166534;background:#ecfdf3}
.composer-area{padding:10px clamp(16px,4vw,54px) 16px;background:linear-gradient(to top,#fbfbfc 85%,rgba(251,251,252,0))}.composer-shell,.start-panel{width:min(860px,100%);margin:0 auto}.composer-shell{padding:10px;border:1px solid #d8dde7;border-radius:18px;background:#fff;box-shadow:0 10px 30px rgba(23,32,51,.09)}.composer-shell textarea{min-height:64px;max-height:160px;padding:8px 10px;resize:none;border:0;background:transparent}.composer-shell textarea:focus{outline:0}.voice-consent{display:flex;grid-template-columns:none;align-items:flex-start;gap:8px;padding:4px 8px 8px;color:#526078;font-size:12px;font-weight:500}.voice-consent input{width:auto;margin-top:3px}.composer-toolbar,.recording-state{display:flex;align-items:center;justify-content:space-between;gap:10px}.composer-tools,.session-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.quiet-action,.send-action,.danger-action,.danger-outline{min-height:42px;padding:8px 13px;border-radius:10px;font-weight:700}.quiet-action{border:1px solid transparent;color:#697386;background:transparent}.quiet-action:hover{color:#172033;background:#f5f6f8}.send-action{min-width:78px;border:0;color:#fff;background:#3157d5}.danger-action{border:1px solid #ffd7dc;color:#bf3e49;background:#fff0f2}.danger-outline{border:1px solid #e8b8be;color:#ad303b;background:#fff}.draft-label{color:#697386;background:#eff1f5}.composer-caption{margin:7px 0 0;text-align:center;color:#7b8497;font-size:12px}.recording-state{min-height:76px;padding:7px 9px}.recording-state strong,.recording-state span{display:block}.recording-state span{color:#697386;font-size:12px}.transcript-notice{display:flex;justify-content:space-between;gap:8px;padding:3px 9px 0;font-size:12px}.transcript-notice span{color:#a46016}.start-panel{text-align:center}.start-panel .send-action{padding-inline:30px}
.inspector-scroll{padding:16px}.info-section{padding:15px 0;border-bottom:1px solid #edf0f4}.info-section:first-child{padding-top:0}.info-section:last-child{border-bottom:0}.info-section h2{margin:0 0 11px;font-size:13px}.info-section dl{display:grid;grid-template-columns:1fr auto;gap:8px 12px;margin:0;font-size:13px}.info-section dt{color:#7b8497}.info-section dd{margin:0;text-align:right;font-weight:650}.turn-progress{display:grid;gap:10px;margin:0;padding:0;list-style:none}.turn-progress li{display:grid;grid-template-columns:22px minmax(0,1fr);gap:8px;color:#7b8497}.turn-progress li>span{width:22px;height:22px;display:grid;place-items:center;border-radius:50%;background:#eef0f4;font-size:11px;font-weight:750}.turn-progress p{margin:0}.turn-progress b,.turn-progress small{display:block;font-size:12px}.turn-progress li.done{color:#172033}.turn-progress li.done>span{color:#187a5e;background:#eaf7f2}.turn-progress li.current{color:#172033}.turn-progress li.current>span{color:#fff;background:#3157d5}.setting-row{min-height:54px;display:flex;grid-template-columns:none;align-items:center;justify-content:space-between;gap:10px;font-weight:400}.setting-row+.setting-row{border-top:1px solid #edf0f4}.setting-row span b,.setting-row span small{display:block}.setting-row span b{font-size:13px}.setting-row span small{color:#7b8497;font-size:12px}.setting-row select{width:126px;padding:7px 8px}.setting-row>input{width:auto}.session-actions{align-items:stretch}.session-actions button{flex:1}.secondary-action{border:1px solid #cad3ea;border-radius:10px;padding:9px 12px;color:#25365f;background:#fff}
@media(max-width:1120px){.interview-workbench{grid-template-columns:220px minmax(500px,1fr)}.inspector-column{display:none}}@media(max-width:760px){.interview-workbench{display:block;height:auto;min-height:calc(100dvh - 68px);overflow:visible}.session-column,.inspector-column{display:none}.conversation-column{min-height:calc(100dvh - 68px)}.conversation-header{padding:11px 14px}.conversation-actions .quiet-action{display:none}.message-stream{padding:22px 14px 30px}.user-message{padding-left:28px}.composer-area{position:sticky;bottom:0;padding:9px 10px 12px}.composer-caption{display:none}.composer-toolbar{align-items:flex-end}}@media(prefers-reduced-motion:reduce){*{animation:none!important;scroll-behavior:auto!important}}
</style>
