import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useSession } from '../../shared/session/SessionProvider';

const publicNavItems = [
  ['/', '首页'],
  ['/questions', '题库'],
  ['/pricing', '价格'],
] as const;

const privateNavItems = [
  ['/app', '学习面板'],
  ['/app/practice', '练习记录'],
  ['/app/interviews/new', '模拟面试'],
  ['/app/learning', '学习计划'],
] as const;

export function RootLayout() {
  const session = useSession();
  const navigate = useNavigate();
  const [logoutBusy, setLogoutBusy] = useState(false);
  const [logoutError, setLogoutError] = useState(false);
  const [online, setOnline] = useState(() => navigator.onLine);
  const navItems = session.status === 'authenticated' ? [...publicNavItems, ...privateNavItems] : publicNavItems;

  useEffect(() => {
    const markOnline = () => setOnline(true);
    const markOffline = () => setOnline(false);
    window.addEventListener('online', markOnline);
    window.addEventListener('offline', markOffline);
    return () => {
      window.removeEventListener('online', markOnline);
      window.removeEventListener('offline', markOffline);
    };
  }, []);

  useEffect(() => {
    if (session.status === 'authenticated') setLogoutError(false);
  }, [session.status]);

  async function logout() {
    if (logoutBusy) return;
    setLogoutBusy(true);
    setLogoutError(false);
    try {
      await session.logout();
      navigate('/', { replace: true });
    } catch {
      setLogoutError(true);
    } finally {
      setLogoutBusy(false);
    }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <Link to="/" className="brand" aria-label="AI Interview Coach 首页">
          <span className="brand-mark">AI</span>
          <span>Interview Coach</span>
        </Link>
        <nav className="main-nav" aria-label="主导航">
          {navItems.map(([to, label]) => (
            <NavLink key={to} to={to} end={to === '/' || to === '/app'} className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="topbar-actions">
          <NavLink to="/status" className="status-pill"><span className="status-dot" />服务状态</NavLink>
          {session.status === 'authenticated' ? (
            <>
              <NavLink to="/app/settings/account" className="account-label">{session.account?.displayName}</NavLink>
              <button type="button" className="button button-small button-ghost" disabled={logoutBusy} onClick={() => void logout()}>{logoutBusy ? '退出中…' : '退出'}</button>
            </>
          ) : (
            <NavLink to="/auth/login" className="button button-small button-ghost">登录</NavLink>
          )}
        </div>
      </header>
      {!online && <div className="global-warning" role="status">当前离线。页面不会在后台继续录音或重放写操作；恢复网络后请先读取服务端状态。</div>}
      {logoutError && <div className="global-warning" role="alert">退出请求未成功，服务端会话状态未被假定为已注销。请检查网络后重试。</div>}
      <main className="main-content"><Outlet /></main>
      <footer className="footer">
        <span>练习反馈，不是招聘结论。</span>
        <nav aria-label="页脚导航"><Link to="/app/settings/privacy">隐私与数据</Link><Link to="/status">系统状态</Link></nav>
      </footer>
    </div>
  );
}
