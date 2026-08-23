import { Link } from 'react-router-dom';
import { PageFrame } from '../../../shared/components/PageFrame';
import { useSession } from '../../../shared/session/SessionProvider';

export function AccountPage() {
  const session = useSession();
  return (
    <PageFrame eyebrow="ACCOUNT" title="账号与个人空间" description="用户、角色和权限来自 RuoYi /getInfo；页面不接受 user_id、role 或 tenant 参数覆盖。">
      <section className="panel form-stack account-form">
        <div className="attempt-meta"><span className="status-chip">{session.account?.status ?? 'UNKNOWN'}</span><span>RuoYi userId {session.account?.userId}</span></div>
        <label>用户名<input value={session.account?.username ?? ''} readOnly /></label>
        <label>显示名称<input value={session.account?.displayName ?? ''} readOnly /></label>
        <label>邮箱<input value={session.account?.email ?? ''} readOnly /></label>
        <p className="muted">角色：{session.roles.join('、') || '无'}；权限：{session.permissions.length} 项</p>
        <Link to="/app" className="button button-primary">返回业务首页</Link>
      </section>
    </PageFrame>
  );
}
