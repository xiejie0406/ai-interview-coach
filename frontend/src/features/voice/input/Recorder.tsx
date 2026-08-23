import { useEffect, useRef, useState } from 'react';
import { useVoiceUiStore } from '../store/voiceUiStore';

export function Recorder({ enabled, supportedCodecs, maxDurationSeconds, maxBytes, onRecorded, onCancelled, streaming = false, onStarted, onChunk, onStopped }: {
  enabled: boolean;
  supportedCodecs: string[];
  maxDurationSeconds?: number;
  maxBytes?: number;
  onRecorded?: (audio: Blob) => void;
  onCancelled?: (reason?: string) => void;
  streaming?: boolean;
  onStarted?: (sampleRate: number, channelCount: number) => boolean;
  onChunk?: (bytes: ArrayBuffer, durationMs: number) => boolean;
  onStopped?: (totalBytes: number, totalDurationMs: number) => void;
}) {
  const recorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const byteCountRef = useRef(0);
  const tooLargeRef = useRef(false);
  const cancelledRef = useRef(false);
  const disposedRef = useRef(false);
  const streamedBytesRef = useRef(0);
  const streamedDurationRef = useRef(0);
  const pendingChunksRef = useRef(0);
  const [seconds, setSeconds] = useState(0);
  const [error, setError] = useState('');
  const { state, setState, degrade, cancel: markCancelled } = useVoiceUiStore();

  useEffect(() => {
    if (state !== 'LISTENING') return;
    const timer = window.setInterval(() => {
      setSeconds((value) => {
        const next = value + 1;
        if (maxDurationSeconds && next >= maxDurationSeconds && recorderRef.current?.state === 'recording') recorderRef.current.stop();
        return next;
      });
    }, 1000);
    return () => window.clearInterval(timer);
  }, [maxDurationSeconds, state]);

  useEffect(() => {
    if (!enabled && recorderRef.current?.state === 'recording') cancelRecording('CONSENT_REVOKED');
  }, [enabled]);

  useEffect(() => {
    const stopWhenOffline = () => {
      if (recorderRef.current?.state === 'recording') cancelRecording('NETWORK_OFFLINE');
    };
    window.addEventListener('offline', stopWhenOffline);
    return () => window.removeEventListener('offline', stopWhenOffline);
  }, []);

  useEffect(() => () => {
    disposedRef.current = true;
    cancelledRef.current = true;
    if (recorderRef.current?.state === 'recording') recorderRef.current.stop();
    stopTracks();
  }, []);

  function stopTracks() {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  }

  async function start() {
    if (!enabled || !navigator.mediaDevices?.getUserMedia || state === 'LISTENING' || recorderRef.current) return;
    setError('');
    setSeconds(0);
    chunksRef.current = [];
    byteCountRef.current = 0;
    streamedBytesRef.current = 0;
    streamedDurationRef.current = 0;
    pendingChunksRef.current = 0;
    tooLargeRef.current = false;
    cancelledRef.current = false;
    setState('REQUESTING_PERMISSION');
    try {
      const approvedCodec = supportedCodecs.find((codec) => MediaRecorder.isTypeSupported(codec));
      if (!approvedCodec) {
        degrade('NO_PREFLIGHT_APPROVED_CODEC');
        return;
      }
      const stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true }, video: false });
      if (!enabled || disposedRef.current) {
        stream.getTracks().forEach((track) => track.stop());
        markCancelled('CONSENT_REVOKED_DURING_PERMISSION');
        return;
      }
      streamRef.current = stream;
      const recorder = new MediaRecorder(stream, { mimeType: approvedCodec });
      recorderRef.current = recorder;
      recorder.ondataavailable = (event) => {
        if (cancelledRef.current || event.data.size <= 0) return;
        if (streaming) {
          pendingChunksRef.current += 1;
          void event.data.arrayBuffer().then((bytes) => {
            if (cancelledRef.current || disposedRef.current) return;
            streamedBytesRef.current += bytes.byteLength;
            streamedDurationRef.current += 250;
            if ((maxBytes && streamedBytesRef.current > maxBytes)
                || (onChunk && !onChunk(bytes, 250))) {
              setError('语音分片未被服务端接受，已停止采集并切换为文本。');
              tooLargeRef.current = true;
              cancelRecording('AUDIO_BACKPRESSURE_OR_LIMIT');
            }
          }).catch(() => cancelRecording('CHUNK_READ_FAILED'))
            .finally(() => { pendingChunksRef.current = Math.max(0, pendingChunksRef.current - 1); });
          return;
        }
        byteCountRef.current += event.data.size;
        if (maxBytes && byteCountRef.current > maxBytes) {
          tooLargeRef.current = true;
          chunksRef.current = [];
          if (recorder.state === 'recording') recorder.stop();
          return;
        }
        chunksRef.current.push(event.data);
      };
      recorder.onerror = () => {
        setError('录音发生错误，已停止采集并切换为文本。');
        cancelledRef.current = true;
        chunksRef.current = [];
        byteCountRef.current = 0;
        degrade('RECORDER_ERROR');
        if (recorder.state === 'recording') recorder.stop();
        stopTracks();
      };
      recorder.onstop = () => {
        const cancelled = cancelledRef.current || disposedRef.current;
        const blob = new Blob(cancelled ? [] : chunksRef.current, { type: recorder.mimeType });
        chunksRef.current = [];
        byteCountRef.current = 0;
        recorderRef.current = null;
        stopTracks();
        if (cancelled) return;
        if (streaming) {
          const notifyStopped = () => {
            if (pendingChunksRef.current > 0 && !cancelledRef.current) {
              window.setTimeout(notifyStopped, 0);
              return;
            }
            if (!cancelledRef.current) onStopped?.(streamedBytesRef.current, streamedDurationRef.current);
          };
          notifyStopped();
          return;
        }
        if (tooLargeRef.current) {
          setError(`录音超过服务端允许的 ${maxBytes} bytes，已停止并丢弃。`);
          degrade('AUDIO_TOO_LARGE');
          return;
        }
        if (blob.size === 0) {
          degrade('EMPTY_AUDIO');
          return;
        }
        if (maxBytes && blob.size > maxBytes) {
          setError(`录音超过服务端允许的 ${maxBytes} bytes，未进入上传链路。`);
          degrade('AUDIO_TOO_LARGE');
          return;
        }
        setState('RECORDED_LOCAL');
        onRecorded?.(blob);
      };
      if (streaming && onStarted && !onStarted(48000, 1)) {
        cancelledRef.current = true;
        recorderRef.current = null;
        stopTracks();
        degrade('VOICE_SOCKET_NOT_READY');
        return;
      }
      recorder.start(250);
      setState('LISTENING');
    } catch (cause) {
      const denied = cause instanceof DOMException && cause.name === 'NotAllowedError';
      setError(denied ? '麦克风权限被拒绝，当前仍可使用文本。' : '无法打开麦克风，当前仍可使用文本。');
      cancelledRef.current = true;
      chunksRef.current = [];
      byteCountRef.current = 0;
      if (recorderRef.current?.state === 'recording') recorderRef.current.stop();
      recorderRef.current = null;
      degrade(denied ? 'MICROPHONE_DENIED' : 'MICROPHONE_UNAVAILABLE');
      stopTracks();
    }
  }

  function stop() {
    if (recorderRef.current?.state === 'recording') recorderRef.current.stop();
  }

  function cancelRecording(reason = 'USER_CANCELLED_RECORDING') {
    cancelledRef.current = true;
    chunksRef.current = [];
    byteCountRef.current = 0;
    if (recorderRef.current?.state === 'recording') recorderRef.current.stop();
    stopTracks();
    markCancelled(reason);
    onCancelled?.();
  }

  return (
    <div className="recorder-card">
      <div><strong>{state === 'LISTENING' ? `正在${streaming ? '流式' : '本地'}录音 ${seconds}s` : '语音回答'}</strong><small>只有点击开始后才请求麦克风；分片按服务端窗口发送。</small></div>
      {state === 'LISTENING' ? <div className="recorder-actions"><button type="button" className="button button-ghost" onClick={() => cancelRecording()}>取消并丢弃</button><button type="button" className="button button-danger" onClick={stop}>结束录音</button></div> : <button type="button" className="button button-secondary" disabled={!enabled || state === 'REQUESTING_PERMISSION'} onClick={() => void start()}>开始录音</button>}
      {error && <div className="inline-error" role="alert">{error}</div>}
    </div>
  );
}
