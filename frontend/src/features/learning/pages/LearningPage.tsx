import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { learningApi } from '../api/learningApi';
import { queryKeys } from '../../../shared/api/queryKeys';
import { ApiErrorNotice, AsyncState } from '../../../shared/components/AsyncState';
import { PageFrame } from '../../../shared/components/PageFrame';
import { useOperationKey } from '../../../shared/hooks/useOperationKey';
import { versionEtag } from '../../../shared/api/client';
import { useSession } from '../../../shared/session/SessionProvider';

export function LearningPage() {
  const { queryScope } = useSession();
  const dashboard = useQuery({ queryKey: queryKeys.learning.dashboard(queryScope), queryFn: ({ signal }) => learningApi.getDashboard(signal) });
  const plans = useQuery({ queryKey: queryKeys.learning.plans(queryScope), queryFn: ({ signal }) => learningApi.listPlans(undefined, signal) });
  const data = dashboard.data;
  const empty = Boolean(data && plans.data && !data.todayItems.length && !data.trends.length && !data.recentReports.length && !plans.data.items.length);
  return (
    <PageFrame eyebrow="LEARNING LOOP" title="把弱项变成下一次练习" description="建议是候选，不是命令；不可比或证据不足必须明确显示。">
      <AsyncState loading={dashboard.isPending || plans.isPending} error={dashboard.error ?? plans.error} empty={empty} onRetry={() => { void dashboard.refetch(); void plans.refetch(); }}>
        {data && <div className="learning-list">
          <section className="panel">
            <span className="card-kicker">TODAY · {data.todayItems.length} · {data.refreshState}</span>
            <p className="muted">投影版本 {data.projectionVersion} · 生成于 {new Date(data.projectionGeneratedAt).toLocaleString()}。STALE/FAILED 不会伪装成最新进度。</p>
          </section>
          {data.todayItems.map((item) => <div className="learning-item" key={item.learningItemId}><span className="status-chip">{item.state}</span><div><h2>{item.questionTitle}</h2><p className="muted">{item.scheduledAt ? new Date(item.scheduledAt).toLocaleString() : '未排期'} · {item.reasonCodes.join('、') || '无原因码'}</p><Link className="text-link" to={`/app/learning/plans/${item.learningPlanId}`}>打开所属计划</Link></div></div>)}
          <div className="section-heading"><h2>趋势可比性</h2></div>
          {data.trends.map((trend) => <div className="learning-item" key={`${trend.dimensionId}:${trend.reasonCode}`}><span className={`status-chip ${trend.comparable ? '' : 'pending'}`}>{trend.comparable ? trend.direction : '不可比'}</span><div><h2>{trend.dimensionId}</h2><p className="muted">{trend.reasonCode} · 样本 {trend.sampleCount}</p></div></div>)}
          <div className="section-heading"><h2>近期报告</h2></div>
          {data.recentReports.map((report) => <div className="learning-item" key={report.reportId}><span className="status-chip">{report.state}</span><div><h2>报告 {report.reportId}</h2><p className="muted">限制项 {report.limitationCount} · {report.completedAt ? new Date(report.completedAt).toLocaleString() : '尚未完成'}</p><Link className="text-link" to={`/app/reports/${report.reportId}`}>查看报告</Link></div></div>)}
          <div className="section-heading"><h2>学习计划</h2></div>
          {(plans.data?.items ?? []).map((plan) => <div className="learning-item" key={plan.id}><span className="status-chip">{plan.state}</span><div><h2>{plan.items.length} 个学习项</h2><p className="muted">来源报告版本 {plan.sourceReportVersionId} · v{plan.version}</p><Link className="text-link" to={`/app/learning/plans/${plan.id}`}>恢复计划</Link></div></div>)}
        </div>}
      </AsyncState>
      <div className="form-actions"><Link to="/questions" className="button button-secondary">去题库练习</Link></div>
    </PageFrame>
  );
}

export function LearningPlanPage() {
  const { planId } = useParams();
  const { queryScope } = useSession();
  const operation = useOperationKey();
  const [busy, setBusy] = useState<string>();
  const [error, setError] = useState<unknown>();
  const planQuery = useQuery({ queryKey: queryKeys.learning.plan(queryScope, planId ?? 'missing'), queryFn: ({ signal }) => learningApi.getPlan(planId!, signal), enabled: Boolean(planId) });
  const plan = planQuery.data?.data;

  async function applyPlanCommand(command: 'confirm' | 'cancel') {
    if (!plan || busy) return;
    const name = `learning-plan:${plan.id}:${command}:v${plan.version}`;
    setBusy(name);
    setError(undefined);
    try {
      await learningApi.applyPlanCommand(plan.id, command, planQuery.data?.etag ?? versionEtag(plan.version), operation.keyFor(name));
      operation.markSucceeded(name);
      await planQuery.refetch();
    } catch (cause) { setError(cause); } finally { setBusy(undefined); }
  }

  async function applyItemCommand(itemId: string, itemVersion: number, command: 'complete' | 'skip') {
    if (!plan || busy) return;
    const name = `learning-item:${itemId}:${command}:v${itemVersion}`;
    setBusy(name);
    setError(undefined);
    try {
      await learningApi.applyItemCommand(itemId, command, {}, versionEtag(itemVersion), operation.keyFor(name));
      operation.markSucceeded(name);
      await planQuery.refetch();
    } catch (cause) { setError(cause); } finally { setBusy(undefined); }
  }

  return <PageFrame eyebrow="LEARNING PLAN" title="可恢复学习计划" description={`计划 ${planId ?? '未知'} 的服务端权威快照`}><AsyncState loading={planQuery.isPending} error={planQuery.error} onRetry={() => void planQuery.refetch()}>{plan && <><section className="panel"><span className="card-kicker">{plan.state} · v{plan.version}</span><h2>{plan.items.length} 个学习项</h2><p className="muted">来源报告版本 {plan.sourceReportVersionId}</p>{plan.limitations.length > 0 && <ul>{plan.limitations.map((item) => <li key={item}>{item}</li>)}</ul>}<div className="form-actions">{plan.state === 'CANDIDATE' && <button className="button button-primary" disabled={Boolean(busy)} onClick={() => void applyPlanCommand('confirm')}>确认计划</button>}{!['COMPLETED', 'CANCELLED'].includes(plan.state) && <button className="button button-danger" disabled={Boolean(busy)} onClick={() => void applyPlanCommand('cancel')}>取消计划</button>}</div></section>{plan.items.map((item) => <div className="learning-item" key={item.id}><span className="status-chip">{item.state}</span><div><h2>{item.questionTitle}</h2><p className="muted">{item.reasonCodes.join('、') || '无原因码'} · {item.scheduledAt ? new Date(item.scheduledAt).toLocaleString() : '未排期'} · v{item.version}</p>{plan.state === 'CONFIRMED' && ['PENDING', 'IN_PROGRESS'].includes(item.state) && <div className="form-actions"><button className="button button-secondary" disabled={Boolean(busy)} onClick={() => void applyItemCommand(item.id, item.version, 'complete')}>标记完成</button><button className="button button-ghost" disabled={Boolean(busy)} onClick={() => void applyItemCommand(item.id, item.version, 'skip')}>跳过</button></div>}</div></div>)}{Boolean(error) && <ApiErrorNotice error={error} onRecover={() => void planQuery.refetch()} />}</>}</AsyncState></PageFrame>;
}

export function ReportLearningPlanPage() {
  const { reportId } = useParams();
  const navigate = useNavigate();
  const operation = useOperationKey();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  async function createCandidate() {
    if (!reportId || busy) return;
    const name = `learning-plan:create:${reportId}`;
    setBusy(true);
    setError(undefined);
    try {
      const result = await learningApi.createPlanCandidate(reportId, operation.keyFor(name));
      operation.markSucceeded(name);
      navigate(`/app/learning/plans/${result.data.id}`);
    } catch (cause) { setError(cause); } finally { setBusy(false); }
  }
  return <PageFrame eyebrow="LEARNING CANDIDATE" title="从报告生成学习计划候选" description={`报告 ${reportId ?? '未知'}；候选需要用户确认才生效。`}><section className="panel"><p className="muted">Learning Coach 只能引用服务端 allowlist 中存在的题目，并必须保留证据限制；不会自动确认计划。</p>{Boolean(error) && <ApiErrorNotice error={error} />}<div className="form-actions"><Link className="button button-ghost" to={`/app/reports/${reportId ?? ''}`}>返回报告</Link><button className="button button-primary" disabled={!reportId || busy} onClick={() => void createCandidate()}>{busy ? '生成中…' : '生成候选'}</button></div></section></PageFrame>;
}
