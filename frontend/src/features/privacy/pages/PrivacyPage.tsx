import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { privacyApi, type ConsentPurpose, type DeletionPreflightView, type PrivacyScope } from '../api/privacyApi';
import { queryKeys } from '../../../shared/api/queryKeys';
import { ApiErrorNotice, AsyncState } from '../../../shared/components/AsyncState';
import { PageFrame } from '../../../shared/components/PageFrame';
import { useOperationKey } from '../../../shared/hooks/useOperationKey';
import { versionEtag } from '../../../shared/api/client';
import { useSession } from '../../../shared/session/SessionProvider';

const privacyScopes: PrivacyScope[] = ['PRACTICE', 'INTERVIEW', 'VOICE', 'REPORT', 'LEARNING', 'BILLING_PROFILE', 'ACCOUNT'];

export function PrivacyPage() {
  const { queryScope } = useSession();
  const navigate = useNavigate();
  const operation = useOperationKey();
  const inventory = useQuery({ queryKey: queryKeys.privacy.inventory(queryScope), queryFn: ({ signal }) => privacyApi.getDataInventory(signal) });
  const policies = useQuery({ queryKey: queryKeys.privacy.policies, queryFn: ({ signal }) => privacyApi.getCurrentPolicies(signal) });
  const consents = useQuery({ queryKey: queryKeys.privacy.consents(queryScope), queryFn: ({ signal }) => privacyApi.getConsents(signal) });
  const [scope, setScope] = useState<PrivacyScope>('INTERVIEW');
  const [preflight, setPreflight] = useState<DeletionPreflightView>();
  const [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState<string>();
  const [error, setError] = useState<unknown>();

  async function changeConsent(purpose: ConsentPurpose, policyVersionId: string, grant: boolean) {
    if (busy) return;
    const name = `consent:${purpose}:${grant ? 'grant' : 'revoke'}:${policyVersionId}`;
    setBusy(name); setError(undefined);
    try {
      if (grant) await privacyApi.grantConsent(purpose, policyVersionId, operation.keyFor(name));
      else await privacyApi.revokeConsent(purpose, policyVersionId, operation.keyFor(name));
      operation.markSucceeded(name);
      await consents.refetch();
    } catch (cause) { setError(cause); } finally { setBusy(undefined); }
  }

  async function requestExport() {
    if (busy) return;
    const name = `privacy:export:${scope}`;
    setBusy(name); setError(undefined);
    try {
      const result = await privacyApi.requestExport({ scope }, operation.keyFor(name));
      operation.markSucceeded(name);
      navigate(`/app/settings/privacy/exports/${result.id}`);
    } catch (cause) { setError(cause); } finally { setBusy(undefined); }
  }

  async function runDeletionPreflight() {
    if (busy) return;
    setBusy('deletion-preflight'); setError(undefined); setPreflight(undefined); setConfirmation('');
    try { setPreflight(await privacyApi.preflightDeletion({ scope })); }
    catch (cause) { setError(cause); }
    finally { setBusy(undefined); }
  }

  async function submitDeletion() {
    if (!preflight || preflight.stepUpRequired || preflight.blockers.length || busy) return;
    const name = `privacy:delete:${scope}:${preflight.challengeId}`;
    setBusy(name); setError(undefined);
    try {
      const result = await privacyApi.requestDeletion({ scope, challengeId: preflight.challengeId, confirmationResponse: confirmation }, operation.keyFor(name));
      operation.markSucceeded(name);
      navigate(`/app/settings/privacy/requests/${result.data.id}`);
    } catch (cause) { setError(cause); } finally { setBusy(undefined); }
  }

  const consentByPurpose = new Map((consents.data ?? []).map((item) => [item.purpose, item]));
  return (
    <PageFrame eyebrow="PRIVACY & DATA" title="你知道数据由谁负责" description="语音、转写、回答、报告和账务具有不同 owner、保留与删除边界。">
      <AsyncState loading={inventory.isPending} error={inventory.error} empty={Boolean(inventory.data && inventory.data.length === 0)} onRetry={() => void inventory.refetch()}>
        <div className="privacy-grid">{(inventory.data ?? []).map((item) => <section className="panel data-class-card" key={item.code}><span className="card-kicker">{item.classification}</span><h2>{item.code}</h2><p>{item.retentionDescription}</p><small className="muted">删除 owner：{item.deletionOwner}</small></section>)}</div>
      </AsyncState>
      <section className="panel privacy-actions-panel">
        <h2>同意记录</h2>
        <AsyncState loading={policies.isPending || consents.isPending} error={policies.error ?? consents.error} onRetry={() => { void policies.refetch(); void consents.refetch(); }}>
          {(policies.data?.policies ?? []).map((policy) => { const current = consentByPurpose.get(policy.purpose); return <div className="toggle-row" key={policy.purpose}><span><strong>{policy.title}</strong><small>{policy.summary} · policy {policy.versionId}</small></span><button type="button" className="button button-ghost" disabled={Boolean(busy) || (Boolean(current?.granted) && !policy.revocable)} onClick={() => void changeConsent(policy.purpose, current?.granted ? current.policyVersionId : policy.versionId, !current?.granted)}>{current?.granted ? (policy.revocable ? '撤回' : '已授予') : '授予'}</button></div>; })}
        </AsyncState>
      </section>
      <section className="panel privacy-actions-panel">
        <h2>导出与删除</h2>
        <label htmlFor="privacy-scope">数据范围<select id="privacy-scope" value={scope} onChange={(event) => { setScope(event.target.value as PrivacyScope); setPreflight(undefined); setConfirmation(''); }}>{privacyScopes.map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
        <div className="form-actions"><button type="button" className="button button-secondary" disabled={Boolean(busy)} onClick={() => void requestExport()}>申请导出</button><button type="button" className="button button-danger" disabled={Boolean(busy)} onClick={() => void runDeletionPreflight()}>删除预检</button></div>
        {preflight && <div className="notice"><strong>服务端确认提示</strong><p>{preflight.confirmationPrompt}</p><small>挑战到期：{new Date(preflight.expiresAt).toLocaleString()}</small>{preflight.blockers.length > 0 && <p>阻断：{preflight.blockers.join('、')}</p>}{preflight.stepUpRequired ? <p>此范围需要 step-up；认证流程尚未完成前不会提交删除。</p> : <><label htmlFor="deletion-confirmation">按服务端提示输入<input id="deletion-confirmation" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} /></label><button type="button" className="button button-danger" disabled={!confirmation || Boolean(busy) || preflight.blockers.length > 0} onClick={() => void submitDeletion()}>提交删除请求</button></>}</div>}
        {Boolean(error) && <ApiErrorNotice error={error} />}
      </section>
    </PageFrame>
  );
}

export function ExportRequestPage() {
  const { requestId } = useParams();
  const { queryScope } = useSession();
  const request = useQuery({ queryKey: queryKeys.privacy.export(queryScope, requestId ?? 'missing'), queryFn: ({ signal }) => privacyApi.getExport(requestId!, signal), enabled: Boolean(requestId) });
  return <PageFrame eyebrow="EXPORT STATUS" title="数据导出进度" description="只有 READY 状态和未过期同源下载地址可以下载。"><AsyncState loading={request.isPending} error={request.error} onRetry={() => void request.refetch()}>{request.data && <section className="panel"><span className="card-kicker">{request.data.state}</span><h2>Export {request.data.id}</h2><p className="muted">申请 {new Date(request.data.requestedAt).toLocaleString()} · 范围 {request.data.scope.scope}</p>{request.data.state === 'READY' && request.data.downloadUrl ? <a className="button button-primary" href={request.data.downloadUrl}>下载导出文件</a> : <button type="button" className="button button-secondary" onClick={() => void request.refetch()}>读取最新状态</button>}</section>}</AsyncState></PageFrame>;
}

export function DeletionRequestPage() {
  const { requestId } = useParams();
  const { queryScope } = useSession();
  const operation = useOperationKey();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const request = useQuery({ queryKey: queryKeys.privacy.deletion(queryScope, requestId ?? 'missing'), queryFn: ({ signal }) => privacyApi.getDeletionRequest(requestId!, signal), enabled: Boolean(requestId) });
  const view = request.data?.data;
  async function cancel() {
    if (!view || !view.cancellable || busy) return;
    const name = `privacy:delete:cancel:${view.id}:v${view.version}`;
    setBusy(true); setError(undefined);
    try {
      await privacyApi.cancelDeletionRequest(view.id, request.data?.etag ?? versionEtag(view.version), operation.keyFor(name));
      operation.markSucceeded(name);
      await request.refetch();
    } catch (cause) { setError(cause); } finally { setBusy(false); }
  }
  return <PageFrame eyebrow="DELETION TIMELINE" title="删除请求进度" description="异步删除允许部分失败、保留窗口或 legal hold；页面不会提前显示完成。"><AsyncState loading={request.isPending} error={request.error} onRetry={() => void request.refetch()}>{view && <section className="panel"><span className="card-kicker">{view.state} · v{view.version}</span><h2>Request {view.id}</h2><p className="muted">申请 {new Date(view.requestedAt).toLocaleString()} · 目标 {view.dueAt ? new Date(view.dueAt).toLocaleString() : '尚未确定'} · 范围 {view.scope.scope}</p><div className="learning-list">{view.steps.map((step, index) => <div className="learning-item" key={`${step.owner}:${index}`}><span className="status-chip">{step.state}</span><div><h2>{step.owner}</h2><p className="muted">影响数量：{step.affectedCount ?? '未返回'}{step.reasonCode ? ` · ${step.reasonCode}` : ''}</p></div></div>)}</div><div className="form-actions"><button type="button" className="button button-secondary" onClick={() => void request.refetch()}>读取最新状态</button>{view.cancellable && <button type="button" className="button button-danger" disabled={busy} onClick={() => void cancel()}>隐藏前取消</button>}</div>{Boolean(error) && <ApiErrorNotice error={error} />}</section>}</AsyncState><div className="form-actions"><Link className="button button-ghost" to="/app/settings/privacy">返回隐私中心</Link></div></PageFrame>;
}
