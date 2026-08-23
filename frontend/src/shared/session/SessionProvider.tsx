import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiClientError, SESSION_CLEARED_EVENT, SESSION_EXPIRED_EVENT } from '../api/client';
import { queryKeys, type PrincipalQueryScope } from '../api/queryKeys';
import { clearRuoYiToken, getRuoYiToken } from './ruoyiToken';
import { sessionApi, type SessionAccountView } from './sessionApi';

type SessionStatus = 'loading' | 'authenticated' | 'anonymous' | 'unavailable';
type SessionContextValue = {
  status: SessionStatus;
  account: SessionAccountView | null;
  roles: string[];
  permissions: string[];
  routers: Record<string, unknown>[];
  queryScope: PrincipalQueryScope;
  refreshSession: () => Promise<void>;
  logout: () => Promise<void>;
  clearSensitiveState: () => Promise<void>;
};
const SessionContext = createContext<SessionContextValue | null>(null);
type SessionQueryValue = { account: SessionAccountView; roles: string[]; permissions: string[]; routers: Record<string, unknown>[] } | null;

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [generation, setGeneration] = useState(0);
  const lastPrincipalRef = useRef('anonymous');
  const sessionQuery = useQuery<SessionQueryValue>({
    queryKey: queryKeys.session.current,
    queryFn: async ({ signal }) => {
      if (!getRuoYiToken()) return null;
      try {
        const info = await sessionApi.getInfo(signal);
        const routers = await sessionApi.getRouters(signal);
        return { account: info.account, roles: info.roles, permissions: info.permissions, routers: routers.data ?? [] };
      } catch (error) {
        if (error instanceof ApiClientError && error.status === 401) return null;
        throw error;
      }
    },
    retry: false,
    staleTime: 60_000,
  });
  const account = sessionQuery.data?.account ?? null;
  const principalIdentity = account?.userId ?? 'anonymous';

  useEffect(() => {
    if (sessionQuery.isPending || principalIdentity === lastPrincipalRef.current) return;
    lastPrincipalRef.current = principalIdentity;
    void queryClient.cancelQueries({ predicate: (query) => query.queryKey[0] === 'scope' });
    queryClient.removeQueries({ predicate: (query) => query.queryKey[0] === 'scope' });
    setGeneration((current) => current + 1);
  }, [principalIdentity, queryClient, sessionQuery.isPending]);

  const clearSensitiveState = useCallback(async () => {
    clearRuoYiToken();
    await queryClient.cancelQueries({ predicate: (query) => query.queryKey[0] === 'scope' });
    queryClient.removeQueries({ predicate: (query) => query.queryKey[0] === 'scope' });
    queryClient.setQueryData<SessionQueryValue>(queryKeys.session.current, null);
    lastPrincipalRef.current = 'anonymous';
    setGeneration((current) => current + 1);
    window.dispatchEvent(new Event(SESSION_CLEARED_EVENT));
  }, [queryClient]);

  useEffect(() => {
    const onSessionExpired = () => { void clearSensitiveState(); };
    window.addEventListener(SESSION_EXPIRED_EVENT, onSessionExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onSessionExpired);
  }, [clearSensitiveState]);

  const refreshSession = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: queryKeys.session.current, exact: true });
  }, [queryClient]);

  const logout = useCallback(async () => {
    try {
      if (getRuoYiToken()) await sessionApi.logout();
    } finally {
      await clearSensitiveState();
    }
  }, [clearSensitiveState]);

  const status: SessionStatus = sessionQuery.isPending ? 'loading' : sessionQuery.isError ? 'unavailable' : account ? 'authenticated' : 'anonymous';
  const queryScope = useMemo<PrincipalQueryScope>(() => ['scope', generation, account?.userId ?? 'anonymous', account?.userId ?? 'anonymous'], [account?.userId, generation]);
  const value = useMemo<SessionContextValue>(() => ({
    status,
    account,
    roles: sessionQuery.data?.roles ?? [],
    permissions: sessionQuery.data?.permissions ?? [],
    routers: sessionQuery.data?.routers ?? [],
    queryScope,
    refreshSession,
    logout,
    clearSensitiveState,
  }), [account, clearSensitiveState, logout, queryScope, refreshSession, sessionQuery.data?.permissions, sessionQuery.data?.roles, sessionQuery.data?.routers, status]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession() {
  const value = useContext(SessionContext);
  if (!value) throw new Error('useSession must be used within SessionProvider');
  return value;
}
