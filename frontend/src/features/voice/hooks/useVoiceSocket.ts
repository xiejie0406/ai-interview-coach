import { useCallback, useEffect, useRef, useState } from 'react';
import type { VoiceSessionHandle } from '../api/voiceApi';
import { getRuoYiToken } from '../../../shared/session/ruoyiToken';

export type VoiceSocketState = 'IDLE' | 'BLOCKED_CONTRACT' | 'CONNECTING' | 'AUTHENTICATING' | 'OPEN' | 'PAUSED_BACKPRESSURE' | 'RESYNC_REQUIRED' | 'DISCONNECTED' | 'FAILED' | 'CANCELLED' | 'DEGRADED';

type ServerEnvelope = {
  messageId: string;
  type: string;
  voiceSessionId: string;
  sessionId: string;
  turnId: string;
  generation: number;
  sequence: number;
  ackSequence: number;
  occurredAt: string;
  schemaVersion: 1;
  data: Record<string, unknown>;
};

export function useVoiceSocket({ handle, sessionId, turnId, enabled, contractReady }: {
  handle?: VoiceSessionHandle;
  sessionId: string;
  turnId: string;
  enabled: boolean;
  contractReady: boolean;
}) {
  const socketRef = useRef<WebSocket | null>(null);
  const handleRef = useRef(handle);
  const sessionIdRef = useRef(sessionId);
  const turnIdRef = useRef(turnId);
  const clientSequenceRef = useRef(0);
  const lastServerSequenceRef = useRef(handle?.initialServerSequence ?? 0);
  const pendingAudioRef = useRef(new Map<number, number>());
  const serverPausedRef = useRef(false);
  const helloAcceptedRef = useRef(false);
  const [state, setState] = useState<VoiceSocketState>('IDLE');
  const [lastMessage, setLastMessage] = useState<ServerEnvelope>();
  const [remainingWindow, setRemainingWindow] = useState(handle?.maxInFlightChunks ?? 0);

  handleRef.current = handle;
  sessionIdRef.current = sessionId;
  turnIdRef.current = turnId;

  const sendEnvelope = useCallback((type: string, data: Record<string, unknown>, trackAudioDurationMs?: number) => {
    const socket = socketRef.current;
    const current = handleRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN || !current) return false;
    clientSequenceRef.current += 1;
    const sequence = clientSequenceRef.current;
    const envelope = {
      messageId: createMessageId(),
      type,
      voiceSessionId: current.voiceSessionId,
      sessionId: sessionIdRef.current,
      turnId: turnIdRef.current,
      generation: current.socketGeneration,
      sequence,
      ackSequence: lastServerSequenceRef.current,
      occurredAt: new Date().toISOString(),
      schemaVersion: 1,
      data,
    };
    socket.send(JSON.stringify(envelope));
    if (trackAudioDurationMs !== undefined) pendingAudioRef.current.set(sequence, trackAudioDurationMs);
    return true;
  }, []);

  useEffect(() => {
    if (!enabled) { setState('IDLE'); return; }
    if (!contractReady) { setState('BLOCKED_CONTRACT'); return; }
    if (!handle || !handle.websocketPath.startsWith('/ws/v1/') || handle.protocolVersion !== 1
        || handle.socketTicket.length < 32 || Date.parse(handle.expiresAt) <= Date.now()) {
      setState('FAILED');
      return;
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const token = getRuoYiToken();
    if (!token) { setState('FAILED'); return; }
    // 浏览器 WebSocket 不能自定义 Authorization header；使用同一个 RuoYi JWT
    // 作为受控子协议传给握手拦截器，不创建独立 ticket/session 认证事实源。
    const socket = new WebSocket(`${protocol}//${window.location.host}${handle.websocketPath}`, [
      'aic.voice.v1', `ruoyi-bearer.${token}`,
    ]);
    socketRef.current = socket;
    clientSequenceRef.current = 0;
    lastServerSequenceRef.current = handle.initialServerSequence;
    pendingAudioRef.current.clear();
    serverPausedRef.current = false;
    helloAcceptedRef.current = false;
    setRemainingWindow(handle.maxInFlightChunks);
    setState('CONNECTING');
    socket.onopen = () => {
      setState('AUTHENTICATING');
      sendEnvelope('client.hello', { socketTicket: handle.socketTicket, protocolVersion: 1, resumeFromServerSequence: handle.initialServerSequence, capabilities: ['BASE64_AUDIO', 'TTS_PLAYBACK', 'TRANSCRIPT_REVIEW'] });
    };
    socket.onmessage = (event) => {
      let parsed: unknown;
      try { parsed = JSON.parse(String(event.data)); }
      catch { closeForResync(socket, 'INVALID_JSON', setState); return; }
      if (!isServerEnvelope(parsed) || parsed.voiceSessionId !== handle.voiceSessionId
          || parsed.sessionId !== sessionId || parsed.turnId !== turnId
          || parsed.generation !== handle.socketGeneration || parsed.schemaVersion !== 1) {
        closeForResync(socket, 'ENVELOPE_MISMATCH', setState);
        return;
      }
      if (parsed.sequence <= lastServerSequenceRef.current) return;
      if (parsed.sequence !== lastServerSequenceRef.current + 1) {
        closeForResync(socket, 'SERVER_SEQUENCE_GAP', setState);
        return;
      }
      lastServerSequenceRef.current = parsed.sequence;
      setLastMessage(parsed);
      if (parsed.type === 'server.hello') {
        helloAcceptedRef.current = true;
        setState('OPEN');
      } else if (parsed.type === 'server.ack') {
        const accepted = numericField(parsed.data, 'acceptedClientSequence');
        if (accepted !== undefined) for (const sequence of pendingAudioRef.current.keys()) if (sequence <= accepted) pendingAudioRef.current.delete(sequence);
        const remaining = numericField(parsed.data, 'remainingWindow');
        if (remaining !== undefined) setRemainingWindow(remaining);
        if (!serverPausedRef.current && pendingAudioRef.current.size < handle.maxInFlightChunks) setState('OPEN');
      } else if (parsed.type === 'server.flow-control') {
        serverPausedRef.current = parsed.data.paused === true;
        setState(serverPausedRef.current ? 'PAUSED_BACKPRESSURE' : 'OPEN');
      } else if (parsed.type === 'server.nack' || parsed.type === 'server.resync-required') {
        closeForResync(socket, parsed.type, setState);
      } else if (parsed.type === 'voice.turn.degraded' || parsed.type === 'speech.failed') {
        setState('DEGRADED');
      } else if (parsed.type === 'server.terminal') {
        setState(parsed.data.recoverable === true ? 'RESYNC_REQUIRED' : 'FAILED');
        socket.close(1000, 'server_terminal');
      }
    };
    socket.onerror = () => setState('FAILED');
    socket.onclose = () => setState((current) => ['FAILED', 'CANCELLED', 'RESYNC_REQUIRED', 'DEGRADED'].includes(current) ? current : 'DISCONNECTED');
    const stopWhenOffline = () => { setState('DISCONNECTED'); socket.close(1001, 'network_offline'); };
    window.addEventListener('offline', stopWhenOffline);
    return () => {
      window.removeEventListener('offline', stopWhenOffline);
      socket.close(1000, 'component_unmounted');
      socketRef.current = null;
      pendingAudioRef.current.clear();
      helloAcceptedRef.current = false;
    };
  }, [contractReady, enabled, handle, sendEnvelope, sessionId, turnId]);

  const startAudio = useCallback((sampleRate: number, channelCount: number) => {
    const current = handleRef.current;
    if (!current || !helloAcceptedRef.current || serverPausedRef.current) return false;
    return sendEnvelope('client.audio.start', { codec: current.codec, sampleRate, channelCount });
  }, [sendEnvelope]);

  const sendAudio = useCallback((bytes: ArrayBuffer, durationMs: number) => {
    const current = handleRef.current;
    if (!current || !helloAcceptedRef.current || serverPausedRef.current
        || bytes.byteLength === 0 || bytes.byteLength > current.maxChunkBytes
        || durationMs <= 0 || pendingAudioRef.current.size >= current.maxInFlightChunks
        || pendingBufferedDuration(pendingAudioRef.current) + durationMs > current.maxBufferedDurationMs) {
      if (current && pendingAudioRef.current.size >= current.maxInFlightChunks) setState('PAUSED_BACKPRESSURE');
      return false;
    }
    const sent = sendEnvelope('client.audio.chunk', { bytesBase64: arrayBufferToBase64(bytes), durationMs }, durationMs);
    if (sent) setRemainingWindow(Math.max(0, current.maxInFlightChunks - pendingAudioRef.current.size));
    return sent;
  }, [sendEnvelope]);

  const stopAudio = useCallback((totalBytes: number, totalDurationMs: number) => {
    if (!helloAcceptedRef.current || totalBytes <= 0 || totalDurationMs <= 0) return false;
    return sendEnvelope('client.audio.stop', { totalBytes, totalDurationMs });
  }, [sendEnvelope]);

  const cancelAudio = useCallback((reasonCode = 'USER_CANCELLED_RECORDING') => {
    sendEnvelope('client.audio.cancel', { reasonCode });
    pendingAudioRef.current.clear();
    setState('CANCELLED');
    socketRef.current?.close(1000, 'user_cancelled');
  }, [sendEnvelope]);

  const cancelTts = useCallback((reasonCode = 'USER_CANCELLED_PLAYBACK') => sendEnvelope('client.tts.cancel', { reasonCode }), [sendEnvelope]);
  return { state, lastMessage, remainingWindow, startAudio, sendAudio, stopAudio, cancelAudio, cancelTts };
}

function isServerEnvelope(value: unknown): value is ServerEnvelope {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as Partial<ServerEnvelope>;
  return typeof candidate.messageId === 'string' && typeof candidate.type === 'string'
    && typeof candidate.voiceSessionId === 'string' && typeof candidate.sessionId === 'string'
    && typeof candidate.turnId === 'string' && typeof candidate.generation === 'number'
    && typeof candidate.sequence === 'number' && typeof candidate.ackSequence === 'number'
    && typeof candidate.occurredAt === 'string' && candidate.schemaVersion === 1
    && Boolean(candidate.data && typeof candidate.data === 'object');
}

function numericField(data: Record<string, unknown>, name: string) {
  const value = data[name];
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 ? value : undefined;
}

function pendingBufferedDuration(pending: Map<number, number>) {
  let total = 0;
  for (const duration of pending.values()) total += duration;
  return total;
}

function closeForResync(socket: WebSocket, reason: string, setState: (state: VoiceSocketState) => void) {
  setState('RESYNC_REQUIRED');
  socket.close(4409, reason.slice(0, 120));
}

function createMessageId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  const bytes = globalThis.crypto?.getRandomValues?.(new Uint8Array(16));
  if (bytes) {
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (token) => {
    const value = Math.floor(Math.random() * 16);
    return (token === 'x' ? value : (value & 0x3) | 0x8).toString(16);
  });
}

function arrayBufferToBase64(bytes: ArrayBuffer) {
  const view = new Uint8Array(bytes);
  let binary = '';
  const stride = 0x8000;
  for (let index = 0; index < view.length; index += stride) binary += String.fromCharCode(...view.subarray(index, Math.min(index + stride, view.length)));
  return btoa(binary);
}
