import { useCallback, useEffect, useRef, useState } from 'react';
import type { InterviewEventEnvelope, InterviewQuestion } from '../api/interviewApi';
import { getRuoYiToken } from '../../../shared/session/ruoyiToken';

export type InterviewStreamState = 'IDLE' | 'CONNECTING' | 'CONNECTED' | 'RECOVERING' | 'DISCONNECTED' | 'EXHAUSTED';

function isEnvelope(value: unknown): value is InterviewEventEnvelope {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as Partial<InterviewEventEnvelope>;
  return typeof candidate.eventId === 'string'
    && typeof candidate.type === 'string'
    && typeof candidate.streamId === 'string'
    && typeof candidate.aggregateId === 'string'
    && typeof candidate.aggregateVersion === 'number'
    && typeof candidate.sequence === 'number'
    && typeof candidate.schemaVersion === 'number'
    && (candidate.durability === 'DURABLE' || candidate.durability === 'EPHEMERAL')
    && Boolean(candidate.data && typeof candidate.data === 'object');
}

function readQuestion(event: InterviewEventEnvelope): InterviewQuestion | undefined {
  if (event.type !== 'interview.question.committed') return undefined;
  const { turnId, turnSequence, text } = event.data;
  if (typeof turnId !== 'string' || typeof turnSequence !== 'number' || typeof text !== 'string') return undefined;
  return { turnId, turnSequence, text };
}

export function useInterviewStream({
  interviewId,
  enabled,
  recoverSnapshot,
  onDurableEvent,
  onQuestion,
}: {
  interviewId?: string;
  enabled: boolean;
  recoverSnapshot: () => Promise<unknown>;
  onDurableEvent?: (event: InterviewEventEnvelope) => void;
  onQuestion?: (question: InterviewQuestion) => void;
}) {
  const [state, setState] = useState<InterviewStreamState>('IDLE');
  const [manualGeneration, setManualGeneration] = useState(0);
  const lastSequenceRef = useRef(0);
  const lastEventIdRef = useRef('');

  useEffect(() => {
    if (!enabled || !interviewId) {
      setState('IDLE');
      return;
    }

    lastSequenceRef.current = 0;
    const streamInterviewId = interviewId;

    let cancelled = false;
    let controller: AbortController | undefined;
    let timer: number | undefined;
    let failures = 0;

    const recoverThenReconnect = async () => {
      setState('RECOVERING');
      try {
        await recoverSnapshot();
      } catch {
        // 快照错误由所属页面呈现；流在恢复成功前保持停止。
      }
      if (cancelled) return;
      if (!navigator.onLine) {
        setState('DISCONNECTED');
        return;
      }
      if (failures >= 3) {
        setState('EXHAUSTED');
        return;
      }
      timer = window.setTimeout(connect, Math.min(4_000, 500 * 2 ** failures));
    };

    const process = (message: MessageEvent<string>) => {
      if (message.lastEventId) lastEventIdRef.current = message.lastEventId;
      let parsed: unknown;
      try {
        parsed = JSON.parse(message.data);
      } catch {
        return;
      }
      if (!isEnvelope(parsed) || parsed.schemaVersion !== 1) return;

      const ephemeral = parsed.durability === 'EPHEMERAL';
      if (!ephemeral) {
        if (parsed.sequence <= lastSequenceRef.current) return;
        if (lastSequenceRef.current > 0 && parsed.sequence > lastSequenceRef.current + 1) {
          failures += 1;
          controller?.abort();
          void recoverThenReconnect();
          return;
        }
        lastSequenceRef.current = parsed.sequence;
        onDurableEvent?.(parsed);
      }
      const question = readQuestion(parsed);
      if (question) onQuestion?.(question);
      if (parsed.type === 'interview.stream.terminal') {
        controller?.abort();
        setState('DISCONNECTED');
        void recoverSnapshot();
      }
    };

    async function connect() {
      if (cancelled) return;
      const token = getRuoYiToken();
      if (!token) {
        setState('EXHAUSTED');
        return;
      }
      controller = new AbortController();
      setState('CONNECTING');
      try {
        const headers: Record<string, string> = {
          Accept: 'text/event-stream',
          Authorization: `Bearer ${token}`,
        };
        if (lastEventIdRef.current) headers['Last-Event-ID'] = lastEventIdRef.current;
        const response = await fetch(`/api/v1/streams/interviews/${encodeURIComponent(streamInterviewId)}`, {
          headers,
          credentials: 'same-origin',
          signal: controller.signal,
        });
        if (response.status === 410) {
          failures += 1;
          lastEventIdRef.current = '';
          await recoverThenReconnect();
          return;
        }
        if (!response.ok || !response.body) throw new Error(`SSE_HTTP_${response.status}`);
        setState('CONNECTED');
        failures = 0;
        await readEventStream(response.body, process, controller.signal);
        if (!cancelled && !controller.signal.aborted) {
          timer = window.setTimeout(() => void connect(), 500);
        }
      } catch (error) {
        if (cancelled || controller.signal.aborted) return;
        failures += 1;
        setState('DISCONNECTED');
        await recoverThenReconnect();
      }
    }

    void connect();
    return () => {
      cancelled = true;
      controller?.abort();
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [enabled, interviewId, manualGeneration, onDurableEvent, onQuestion, recoverSnapshot]);

  const recover = useCallback(() => {
    lastSequenceRef.current = 0;
    lastEventIdRef.current = '';
    setManualGeneration((current) => current + 1);
  }, []);

  return { state, lastSequence: lastSequenceRef.current, recover };
}

async function readEventStream(
  stream: ReadableStream<Uint8Array>,
  onMessage: (message: MessageEvent<string>) => void,
  signal: AbortSignal,
) {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let eventName = '';
  let eventId = '';
  let data: string[] = [];

  const dispatch = () => {
    if (data.length > 0) {
      onMessage(new MessageEvent(eventName || 'message', {
        data: data.join('\n'),
        lastEventId: eventId,
      }));
    }
    eventName = '';
    eventId = '';
    data = [];
  };

  try {
    while (!signal.aborted) {
      const { done, value } = await reader.read();
      buffer += decoder.decode(value, { stream: !done });
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() ?? '';
      for (const line of lines) {
        if (!line) { dispatch(); continue; }
        if (line.startsWith(':')) continue;
        const separator = line.indexOf(':');
        const field = separator < 0 ? line : line.slice(0, separator);
        const raw = separator < 0 ? '' : line.slice(separator + 1).replace(/^ /, '');
        if (field === 'event') eventName = raw;
        else if (field === 'id') eventId = raw;
        else if (field === 'data') data.push(raw);
      }
      if (done) { dispatch(); break; }
    }
  } finally {
    reader.releaseLock();
  }
}
