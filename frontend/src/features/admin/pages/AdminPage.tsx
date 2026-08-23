import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { adminApi, type OperationsProjection, type OperationsView } from '../api/adminApi';
import { queryKeys } from '../../../shared/api/queryKeys';
import { ApiErrorNotice, AsyncState } from '../../../shared/components/AsyncState';
import { PageFrame } from '../../../shared/components/PageFrame';
import { useOperationKey } from '../../../shared/hooks/useOperationKey';
import { versionEtag } from '../../../shared/api/client';
import { useSession } from '../../../shared/session/SessionProvider';

const operationsViews = new Set<OperationsView>(['providers', 'jobs', 'cost', 'quality']);

export function AdminPage() {
  return <PageFrame eyebrow="CONTROL PLANE" title="运营与内容治理" description="路由 guard 只改善体验；服务端仍逐次校验角色、作用域、reason 与审计。"><div className="admin-grid"><Link to="/admin/catalog" className="admin-card"><strong>题库版本</strong><span>草稿、审核、发布与下线</span></Link><Link to="/admin/operations/providers" className="admin-card"><strong>Provider 健康</strong><span>脱敏投影</span></Link><Link to="/admin/operations/jobs" className="admin-card"><strong>后台 Job</strong><span>状态与积压投影</span></Link><Link to="/admin/operations/cost" className="admin-card"><strong>成本</strong><span>不展示用户正文</span></Link><Link to="/admin/operations/quality" className="admin-card"><strong>质量</strong><span>Golden 与失败聚合</span></Link><Link to="/admin/audit" className="admin-card"><strong>审计与隐私请求</strong><span>仅脱敏事实</span></Link></div></PageFrame>;
}

export function AdminOperationsPage() {
  const { view } = useParams();
  const { queryScope } = useSession();
  const validView = operationsViews.has(view as OperationsView) ? view as OperationsView : undefined;
  const projection = useQuery({ queryKey: queryKeys.operations.projection(queryScope, validView ?? 'invalid'), queryFn: ({ signal }) => adminApi.getOperationsProjection(validView!, signal), enabled: Boolean(validView) });
  return <PageFrame eyebrow="ADMIN OPERATIONS" title={validView ?? '未知运营投影'} description="响应由服务端按固定 schema 脱敏；不渲染未知字段。">{!validView ? <div className="state-card error-state"><strong>不支持的投影视图</strong><Link className="button button-primary" to="/admin">返回后台</Link></div> : <AsyncState loading={projection.isPending} error={projection.error} onRetry={() => void projection.refetch()}>{projection.data && <ProjectionView projection={projection.data} />}</AsyncState>}</PageFrame>;
}

function ProjectionView({ projection }: { projection: OperationsProjection }) {
  return <section className="panel"><span className="card-kicker">v{projection.projectionVersion} · {projection.stale ? 'STALE' : 'CURRENT'}</span><p className="muted">生成于 {new Date(projection.generatedAt).toLocaleString()}</p><div className="learning-list">{projection.view === 'providers' && projection.items.map((item) => <div className="learning-item" key={`${item.capability}:${item.providerAlias}:${item.modelAlias}`}><span className="status-chip">{item.state}</span><div><h2>{item.capability}</h2><p className="muted">{item.providerAlias}/{item.modelAlias} · config {item.configVersion} · p95 {item.latencyP95Ms ?? 'N/A'} ms</p></div></div>)}{projection.view === 'jobs' && projection.items.map((item) => <div className="learning-item" key={`${item.jobType}:${item.state}`}><span className="status-chip">{item.state}</span><div><h2>{item.jobType}</h2><p className="muted">{item.count} 项 · 最老 {item.oldestAgeSeconds}s</p></div></div>)}{projection.view === 'cost' && projection.items.map((item) => <div className="learning-item" key={`${item.capability}:${item.currency}`}><span className="status-chip">{item.invocationCount} calls</span><div><h2>{item.capability}</h2><p className="muted">{formatMinor(item.currency, item.currencyExponent, item.amountMinor)}</p></div></div>)}{projection.view === 'quality' && projection.items.map((item) => <div className="learning-item" key={`${item.agentRole}:${item.schemaVersion}:${item.goldenSetVersion}`}><span className="status-chip">{item.status}</span><div><h2>{item.agentRole}</h2><p className="muted">schema {item.schemaVersion} · golden {item.goldenSetVersion} · n={item.sampleSize} · {item.passRateBasisPoints === null ? '无通过率' : `${item.passRateBasisPoints / 100}%`}</p></div></div>)}</div></section>;
}

export function AdminCatalogPage() {
  const { queryScope } = useSession();
  const questions = useQuery({ queryKey: queryKeys.operations.adminQuestions(queryScope), queryFn: ({ signal }) => adminApi.listQuestions(signal) });
  return <PageFrame eyebrow="CATALOG GOVERNANCE" title="题库版本工作流" description="发布版与新草稿并存；公开接口不会泄漏答案门。"><AsyncState loading={questions.isPending} error={questions.error} empty={Boolean(questions.data && !questions.data.items.length)} onRetry={() => void questions.refetch()}><div className="admin-grid">{(questions.data?.items ?? []).map((item) => <Link className="admin-card" to={`/admin/catalog/${item.id}`} key={item.id}><strong>{item.stableKey}</strong><span>{item.state} · v{item.version} · draft {item.currentDraftVersion?.versionNo ?? '-'} · published {item.currentPublishedVersion?.versionNo ?? '-'}</span></Link>)}</div></AsyncState></PageFrame>;
}

export function AdminQuestionPage() {
  const { questionId } = useParams();
  const { queryScope } = useSession();
  const operation = useOperationKey();
  const [reasonCode, setReasonCode] = useState('CONTENT_REVIEWED');
  const [busy, setBusy] = useState<string>();
  const [error, setError] = useState<unknown>();
  const detail = useQuery({ queryKey: queryKeys.operations.adminQuestion(queryScope, questionId ?? 'missing'), queryFn: ({ signal }) => adminApi.getQuestion(questionId!, signal), enabled: Boolean(questionId) });
  const data = detail.data?.data;
  async function command(value: 'submit-review' | 'reject-review' | 'publish' | 'retire') {
    if (!data || busy || !reasonCode.trim()) return;
    const name = `catalog:${data.question.id}:${value}:v${data.question.version}`;
    setBusy(name); setError(undefined);
    try {
      await adminApi.applyQuestionCommand(data.question.id, value, { questionVersionId: data.draft?.id, rubricVersionId: data.rubric?.id, reasonCode: reasonCode.trim() }, detail.data?.etag ?? versionEtag(data.question.version), operation.keyFor(name));
      operation.markSucceeded(name);
      await detail.refetch();
    } catch (cause) { setError(cause); } finally { setBusy(undefined); }
  }
  const state = data?.question.state ?? 'DRAFT';
  return <PageFrame eyebrow="CATALOG VERSION" title={data?.draft?.title ?? data?.published?.title ?? '题目工作流'} description={`Question ${questionId ?? '未知'}；内容命令由确定性状态机裁决。`}><AsyncState loading={detail.isPending} error={detail.error} onRetry={() => void detail.refetch()}>{data && <><section className="panel"><span className="card-kicker">{state} · v{data.question.version}</span><h2>{data.question.stableKey}</h2><p className="muted">draft {data.question.currentDraftVersion?.contentHash ?? '无'} · published {data.question.currentPublishedVersion?.contentHash ?? '无'}</p><label htmlFor="catalog-reason">审计原因码<input id="catalog-reason" pattern="[A-Z][A-Z0-9_]*" maxLength={96} value={reasonCode} onChange={(event) => setReasonCode(event.target.value.toUpperCase())} /></label><div className="form-actions">{['DRAFT', 'PUBLISHED_WITH_DRAFT'].includes(state) && <button className="button button-secondary" disabled={Boolean(busy)} onClick={() => void command('submit-review')}>提交审核</button>}{['IN_REVIEW', 'PUBLISHED_WITH_REVIEW'].includes(state) && <><button className="button button-ghost" disabled={Boolean(busy)} onClick={() => void command('reject-review')}>驳回审核</button><button className="button button-primary" disabled={Boolean(busy) || !data.draft || !data.rubric} onClick={() => void command('publish')}>发布版本</button></>}{state === 'PUBLISHED' && <button className="button button-danger" disabled={Boolean(busy)} onClick={() => void command('retire')}>下线</button>}</div>{Boolean(error) && <ApiErrorNotice error={error} onRecover={() => void detail.refetch()} />}</section>{data.draft && <section className="panel"><span className="card-kicker">DRAFT v{data.draft.versionNo}</span><h2>{data.draft.title}</h2><p>{data.draft.stem}</p><p className="muted">答案点 {data.draft.answerPoints.length} · 误区 {data.draft.misconceptions.length} · 追问模板 {data.draft.followUpTemplates.length}</p></section>}{data.rubric && <section className="panel"><span className="card-kicker">RUBRIC v{data.rubric.versionNo}</span>{data.rubric.dimensions.map((item) => <div key={item.code}><h2>{item.code}</h2><p>{item.description}</p><small>{item.criteria.join('；')}</small></div>)}</section>}</>}</AsyncState></PageFrame>;
}

export function AdminAuditPage() {
  const { queryScope } = useSession();
  const audit = useQuery({ queryKey: queryKeys.operations.audit(queryScope), queryFn: ({ signal }) => adminApi.listAuditEvents(signal) });
  return <PageFrame eyebrow="AUDIT" title="脱敏审计事实" description="只显示 actor、动作、哈希资源引用、结果与 correlation ID。"><AsyncState loading={audit.isPending} error={audit.error} empty={Boolean(audit.data && !audit.data.items.length)} onRetry={() => void audit.refetch()}><div className="learning-list">{(audit.data?.items ?? []).map((item) => <div className="learning-item" key={item.id}><span className="status-chip">{item.outcome}</span><div><h2>{item.actionCode}</h2><p className="muted">{item.actorType}/{item.actorId ?? 'system'} · {item.resourceType}/{item.resourceIdHash.slice(0, 12)}… · {item.reasonCode ?? '无原因码'} · {new Date(item.occurredAt).toLocaleString()}</p><small>Correlation {item.correlationId}</small></div></div>)}</div></AsyncState></PageFrame>;
}

function formatMinor(currency: string, exponent: number, amountMinor: number) {
  const amount = amountMinor / 10 ** exponent;
  try { return new Intl.NumberFormat('zh-CN', { style: 'currency', currency, currencyDisplay: 'code', minimumFractionDigits: exponent, maximumFractionDigits: exponent }).format(amount); }
  catch { return `${currency} ${amount.toFixed(exponent)}`; }
}
