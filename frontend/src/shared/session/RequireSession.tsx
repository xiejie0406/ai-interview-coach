import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { AsyncState } from '../components/AsyncState';
import { useSession } from './SessionProvider';

export function RequireSession() {
  const session = useSession();
  const location = useLocation();
  if (session.status === 'loading') {
    return <div className="route-state"><AsyncState loading><span /></AsyncState></div>;
  }
  if (session.status === 'unavailable') {
    return (
      <div className="route-state">
        <AsyncState error={new Error('SESSION_LOOKUP_UNAVAILABLE')} onRetry={() => void session.refreshSession()}><span /></AsyncState>
      </div>
    );
  }
  if (session.status !== 'authenticated') {
    return <Navigate to="/auth/login" replace state={{ returnTo: `${location.pathname}${location.search}` }} />;
  }
  return <Outlet />;
}
