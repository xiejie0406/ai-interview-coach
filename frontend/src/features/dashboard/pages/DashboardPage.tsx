import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { learningApi } from '../../learning/api/learningApi';
import { queryKeys } from '../../../shared/api/queryKeys';
import { AsyncState } from '../../../shared/components/AsyncState';
import { PageFrame } from '../../../shared/components/PageFrame';
import { useSession } from '../../../shared/session/SessionProvider';

export function DashboardPage() {
  const { queryScope, account } = useSession();
  const dashboard = useQuery({
    queryKey: queryKeys.learning.dashboard(queryScope),
    queryFn: ({ signal }) => learningApi.getDashboard(signal),
  });
  const data = dashboard.data;
  const empty = Boolean(data && data.todayItems.length === 0 && data.recentReports.length === 0 && data.trends.length === 0);
  return (
    <PageFrame eyebrow="YOUR PRACTICE SPACE" title={`${account?.displayName ?? '你好'}，今天从一个小问题开始`} description="面板只展示服务端 tenant-scoped projection；投影过期时不会用假数据填充。" actions={<Link to="/app/interviews/new" className="button button-primary">开始短面试</Link>}>
      <AsyncState loading={dashboard.isPending} error={dashboard.error} empty={empty} onRetry={() => void dashboard.refetch()}>
        {data && <>
          <div className="dashboard-grid">
            <section className="panel accent-panel">
              <span className="card-kicker">今日任务</span>
              <div className="metric-row"><strong>{data.todayItems.length}</strong><span>项服务端建议</span></div>
              <p className="muted">Dashboard 契约尚未冻结任务项字段，因此当前只展示可验证数量，不推断题目、时间或动作。</p>
              <Link to="/app/learning" className="text-link">查看学习循环 →</Link>
            </section>
            <section className="panel">
              <span className="card-kicker">最近状态</span>
              <div className="metric-row"><strong>{data.recentReports.length}</strong><span>份报告投影</span></div>
              <div className="metric-row"><strong>v{data.projectionVersion}</strong><span>投影版本</span></div>
            </section>
          </div>
          <section className="panel trend-panel">
            <span className="card-kicker">可比趋势</span>
            {data.trends.length === 0 ? <p className="muted">没有足够的可比证据。</p> : data.trends.map((trend) => (
              <div className="metric-row" key={`${trend.dimensionId}:${trend.reasonCode}`}>
                <strong>{trend.comparable ? '可比' : '不可比'}</strong>
                <span>{trend.dimensionId} · {trend.reasonCode}</span>
              </div>
            ))}
          </section>
        </>}
      </AsyncState>
      <div className="section-heading"><h2>安全入口</h2></div>
      <div className="next-grid">
        <Link to="/questions" className="next-card"><strong>浏览版本化题库</strong><span>按方向、级别和难度筛选</span></Link>
        <Link to="/app/settings/privacy" className="next-card"><strong>查看隐私数据清单</strong><span>不同 owner 分别说明保留与删除</span></Link>
        <Link to="/app/usage" className="next-card"><strong>查看可用额度</strong><span>开始前先读取服务端权益</span></Link>
      </div>
    </PageFrame>
  );
}
