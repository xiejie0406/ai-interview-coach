import { useCallback, useEffect, useRef, useState } from 'react';
import { SESSION_EXPIRED_EVENT } from '../../../shared/api/client';
import { getRuoYiToken } from '../../../shared/session/ruoyiToken';

export type EvaluationStreamState = 'IDLE' | 'CONNECTING' | 'CONNECTED' | 'RECOVERING' | 'DISCONNECTED' | 'EXHAUSTED' | 'FORBIDDEN';

export type EvaluationEvent = {
  eventId: string;
  type: string;
  streamId?: string;
  aggregateId: string;
  aggregateVersion?: number;
  sequence: number;
  occurredAt: string;
  schemaVersion: number;
  correlationId: string;
  durability?: 'DURABLE' | 'EPHEMERAL';
  data: Record<string, unknown>;
};

export function useEvaluationStream({ evaluationId, enabled, recoverSnapshot, onDurableEvent }: {
  evaluationId?: string;
  enabled: boolean;
  recoverSnapshot: () => Promise<unknown>;
  onDurableEvent?: (event: EvaluationEvent) => void;
}) {
  const [state, setState] = useState<EvaluationStreamState>('IDLE');
  const [generation, setGeneration] = useState(0);
  const lastEventIdRef = useRef<string | undefined>(undefined);
  const lastSequenceRef = useRef(0);

  useEffect(() => {
    if (!enabled || !evaluationId) { setState('IDLE'); return; }
    const controller = new AbortController();
    let cancelled = false;
    let failures = 0;

    async function recover() {
      setState('RECOVERING');
      await recoverSnapshot();
      lastEventIdRef.current = undefined;
      lastSequenceRef.current = 0;
    }

    async function connect() {
      while (!cancelled && failures < 3) {
        if (!navigator.onLine) { setState('DISCONNECTED'); return; }
        setState('CONNECTING');
        try {
          const headers = new Headers({ Accept: 'text/event-stream' });
          const token = getRuoYiToken();
          if (token) headers.set('Authorization', `Bearer ${token}`);
          if (lastEventIdRef.current) headers.set('Last-Event-ID', lastEventIdRef.current);
          const response = await fetch(`/api/v1/streams/evaluations/${encodeURIComponent(evaluationId!)}`, { credentials: 'omit', headers, signal: controller.signal });
          if (response.status === 401 || response.status === 403) {
            if (response.status === 401) window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT, { detail: { code: 'SESSION_EXPIRED' } }));
            setState('FORBIDDEN');
            return;
          }
          if (response.status === 410) { failures += 1; await recover(); continue; }
          if (!response.ok || !response.body) throw new Error(`evaluation_stream_http_${response.status}`);
          setState('CONNECTED');
          await consumeSse(response.body, (frame) => {
            const event = parseEvaluationEvent(frame.data);
            if (!event || event.schemaVersion !== 1) return;
            if (event.durability !== 'EPHEMERAL') {
              if (event.sequence <= lastSequenceRef.current) return;
              if (lastSequenceRef.current > 0 && event.sequence !== lastSequenceRef.current + 1) throw new StreamGapError();
              lastSequenceRef.current = event.sequence;
              lastEventIdRef.current = frame.id || event.eventId;
              onDurableEvent?.(event);
            }
          }, controller.signal);
          if (!cancelled) throw new Error('evaluation_stream_ended');
        } catch (cause) {
          if (cancelled || controller.signal.aborted) return;
          failures += 1;
          setState('DISCONNECTED');
          if (cause instanceof StreamGapError) await recover();
          if (failures >= 3) break;
          await delay(Math.min(4_000, 500 * 2 ** failures), controller.signal);
        }
      }
      if (!cancelled) setState('EXHAUSTED');
    }

    void connect();
    return () => { cancelled = true; controller.abort(); };
  }, [enabled, evaluationId, generation, onDurableEvent, recoverSnapshot]);

  const reconnect = useCallback(() => {
    lastEventIdRef.current = undefined;
    lastSequenceRef.current = 0;
    setGeneration((current) => current + 1);
  }, []);
  return { state, reconnect, lastEventId: lastEventIdRef.current, lastSequence: lastSequenceRef.current };
}

async function consumeSse(stream: ReadableStream<Uint8Array>, onFrame: (frame: { id?: string; event?: string; data: string }) => void, signal: AbortSignal) {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  try {
    while (!signal.aborted) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');
      let boundary = buffer.indexOf('\n\n');
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        const frame = parseSseBlock(block);
        if (frame) onFrame(frame);
        boundary = buffer.indexOf('\n\n');
      }
    }
  } finally {
    reader.releaseLock();
  }
}

function parseSseBlock(block: string) {
  let id: string | undefined;
  let event: string | undefined;
  const data: string[] = [];
  for (const line of block.split('\n')) {
    if (!line || line.startsWith(':')) continue;
    const separator = line.indexOf(':');
    const field = separator < 0 ? line : line.slice(0, separator);
    const value = separator < 0 ? '' : line.slice(separator + 1).replace(/^ /, '');
    if (field === 'id') id = value;
    else if (field === 'event') event = value;
    else if (field === 'data') data.push(value);
  }
  return data.length ? { id, event, data: data.join('\n') } : undefined;
}

function parseEvaluationEvent(value: string): EvaluationEvent | undefined {
  let parsed: unknown;
  try { parsed = JSON.parse(value); } catch { return undefined; }
  if (!parsed || typeof parsed !== 'object') return undefined;
  const item = parsed as Partial<EvaluationEvent>;
  return typeof item.eventId === 'string' && typeof item.type === 'string'
    && typeof item.aggregateId === 'string' && typeof item.sequence === 'number'
    && typeof item.occurredAt === 'string' && typeof item.schemaVersion === 'number'
    && typeof item.correlationId === 'string' && Boolean(item.data && typeof item.data === 'object')
    ? item as EvaluationEvent : undefined;
}

function delay(milliseconds: number, signal: AbortSignal) {
  return new Promise<void>((resolve) => {
    const timer = window.setTimeout(resolve, milliseconds);
    signal.addEventListener('abort', () => { window.clearTimeout(timer); resolve(); }, { once: true });
  });
}

class StreamGapError extends Error {}
