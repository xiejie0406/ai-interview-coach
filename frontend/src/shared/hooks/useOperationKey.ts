import { useCallback, useRef } from 'react';
import { newIdempotencyKey } from '../api/client';

export function useOperationKey() {
  const keysRef = useRef(new Map<string, string>());

  const keyFor = useCallback((operation: string) => {
    const existing = keysRef.current.get(operation);
    if (existing) return existing;
    const next = newIdempotencyKey();
    keysRef.current.set(operation, next);
    return next;
  }, []);

  const markSucceeded = useCallback((operation: string) => {
    keysRef.current.delete(operation);
  }, []);

  const abandon = useCallback((operation: string) => {
    keysRef.current.delete(operation);
  }, []);

  return { keyFor, markSucceeded, abandon };
}
