import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider, type DataRouter } from 'react-router-dom';
import type { ReactNode } from 'react';
import { ApiClientError } from '../shared/api/client';
import { SessionProvider } from '../shared/session/SessionProvider';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => {
        if (failureCount >= 1) return false;
        if (!(error instanceof ApiClientError)) return false;
        return error.retryable && ![401, 403, 404, 409, 410, 412, 428, 429].includes(error.status);
      },
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
    mutations: { retry: false },
  },
});

export function AppProviders({ router }: { router: DataRouter; children?: ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <RouterProvider router={router} />
      </SessionProvider>
    </QueryClientProvider>
  );
}
