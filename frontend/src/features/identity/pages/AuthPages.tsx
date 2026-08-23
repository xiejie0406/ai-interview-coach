import { useEffect, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { identityApi } from '../api/identityApi';
import { ApiErrorNotice } from '../../../shared/components/AsyncState';
import { useSession } from '../../../shared/session/SessionProvider';
import { setRuoYiToken } from '../../../shared/session/ruoyiToken';

function safeReturnTo(value: unknown) {
  if (typeof value !== 'string') return '/app';
  return value.startsWith('/app') || value.startsWith('/admin') ? value : '/app';
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const session = useSession();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [uuid, setUuid] = useState('');
  const [captcha, setCaptcha] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const captchaQuery = useQuery({ queryKey: ['ruoyi', 'captcha'], queryFn: async ({ signal }) => (await identityApi.getCaptcha(signal)).data, staleTime: 0 });

  useEffect(() => {
    if (!captchaQuery.data) return;
    setUuid(captchaQuery.data.uuid ?? '');
    setCaptcha(captchaQuery.data.img);
  }, [captchaQuery.data]);

  useEffect(() => {
    if (session.status === 'authenticated') navigate('/app', { replace: true });
  }, [navigate, session.status]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy || !uuid) return;
    setBusy(true);
    setError(undefined);
    try {
      const response = await identityApi.login({ username: username.trim(), password, code: code.trim(), uuid });
      const token = response.token;
      if (!token) throw new Error('RuoYi 登录响应缺少 Token');
      setRuoYiToken(token);
      await session.refreshSession();
      setPassword('');
      navigate(safeReturnTo((location.state as { returnTo?: unknown } | null)?.returnTo), { replace: true });
    } catch (cause) {
      setError(cause);
      setCode('');
      void captchaQuery.refetch();
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-page"><div className="auth-card">
      <span className="eyebrow">RUOYI PLATFORM</span><h1>登录训练空间</h1>
      <p className="muted">账号、验证码、Token、权限和菜单统一由 RuoYi 管理。</p>
      <form onSubmit={submit} className="form-stack">
        <label htmlFor="login-username">用户名<input id="login-username" required autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} /></label>
        <label htmlFor="login-password">密码<input id="login-password" required type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>
        <label htmlFor="login-code">验证码<div className="captcha-row"><input id="login-code" required autoComplete="off" value={code} onChange={(event) => setCode(event.target.value)} /><button type="button" className="captcha-image-button" onClick={() => void captchaQuery.refetch()} aria-label="刷新验证码">{captcha ? <img src={`data:image/gif;base64,${captcha}`} alt="RuoYi 验证码" /> : '刷新'}</button></div></label>
        {captchaQuery.error ? <ApiErrorNotice error={captchaQuery.error} onRetry={() => void captchaQuery.refetch()} /> : null}
        {error !== undefined ? <ApiErrorNotice error={error} /> : null}
        <button className="button button-primary" disabled={busy || captchaQuery.isPending}>{busy ? '登录中…' : '登录'}</button>
      </form>
      <p className="auth-switch">账号注册由 RuoYi 管理，主页不提供第二套注册入口。</p>
      <p className="auth-switch"><Link to="/auth/recover">无法登录？查看找回状态</Link></p>
    </div></div>
  );
}

export function RegisterPage() {
  return <div className="auth-page"><div className="auth-card"><span className="eyebrow">RUOYI ACCOUNT</span><h1>账号注册</h1><p className="muted">主页不创建 AI 自建账号。请由 RuoYi 管理员按平台流程开通账号。</p><Link to="/auth/login" className="button button-primary">返回登录</Link></div></div>;
}

export function RecoverPage() {
  return <div className="auth-page"><div className="auth-card"><span className="eyebrow">ACCOUNT RECOVERY</span><h1>账号找回</h1><p className="muted">当前由 RuoYi 平台管理员处理账号找回，主页不会收集或保存额外密码/验证码。</p><Link to="/auth/login" className="button button-primary">返回登录</Link></div></div>;
}
