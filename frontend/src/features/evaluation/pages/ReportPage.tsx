import { useCallback, useMemo, useState, type FormEvent } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { evaluationApi } from '../api/evaluationApi';
import { useEvaluationStream } from '../hooks/useEvaluationStream';
import { queryKeys } from '../../../shared/api/queryKeys';
import { ApiErrorNotice, AsyncState, ReconnectBanner } from '../../../shared/components/AsyncState';
import { PageFrame } from '../../../shared/components/PageFrame';
import { useOperationKey } from '../../../shared/hooks/useOperationKey';
import { useSession } from '../../../shared/session/SessionProvider';

const terminalStates = new Set(['READY', 'PARTIAL', 'FAILED', 'CANCELLED']);

export function ReportPage() {
  const { reportId } = useParams();
  const { queryScope } = useSession();
  const queryClient = useQueryClient();
  const reportKey = useMemo(() => queryKeys.evaluation.report(queryScope, reportId ?? 'missing'), [queryScope, reportId]);
  const reportQuery = useQuery({
    queryKey: reportKey,
    queryFn: ({ signal }) => evaluationApi.getReport(reportId!, signal),
    enabled: Boolean(reportId),
  });
  const report = reportQuery.data;
  const refetchReport = reportQuery.refetch;
  const recoverReport = useCallback(async () => { await refetchReport(); }, [refetchReport]);
  const onDurableEvent = useCallback(() => { void queryClient.invalidateQueries({ queryKey: reportKey, exact: true }); }, [queryClient, reportKey]);
  const stream = useEvaluationStream({ evaluationId: report?.evaluationId, enabled: Boolean(report && !terminalStates.has(report.state)), recoverSnapshot: recoverReport, onDurableEvent });
  return (
    <PageFrame eyebrow="EVIDENCE REPORT" title="你的面试报告" description={`报告 ${reportId ?? '未知'} · 结论、证据引用、版本和限制分开呈现`}>
      <AsyncState loading={reportQuery.isPending} error={reportQuery.error} onRetry={() => void reportQuery.refetch()}>
        {report && <>
          <ReconnectBanner visible={!['CONNECTED', 'IDLE'].includes(stream.state)} exhausted={stream.state === 'EXHAUSTED'} onRecover={stream.reconnect} />
          <section className="panel report-status">
            <span className="card-kicker">{report.state}</span>
            <h2>{reportStateTitle(report.state)}</h2>
            {!terminalStates.has(report.state) && <p className="muted">进度提示不是完成事实。需要 durable event 或手动读取后续报告状态。</p>}
            {!terminalStates.has(report.state) && <button type="button" className="button button-secondary" onClick={() => void reportQuery.refetch()}>手动刷新状态</button>}
          </section>
          {(report.state === 'READY' || report.state === 'PARTIAL') && <div className="report-grid">
            <section className="panel report-sections">
              <span className="card-kicker">REPORT VERSION {report.reportVersionId ?? '未返回'}</span>
              {(report.sections ?? []).map((section) => <article className="report-section" key={section.sectionId}><h2>{section.title}</h2><p>{section.body}</p><small>判断引用：{section.judgementRefs.join('、') || '无'} · 证据引用：{section.evidenceRefs.join('、') || '无'}</small></article>)}
              {!report.sections?.length && <p className="muted">报告已进入可读状态，但当前没有 section。</p>}
            </section>
            <aside className="panel">
              <span className="card-kicker">三项以内的下一步</span>
              {(report.actions ?? []).length ? <ol>{report.actions!.map((action, index) => <li key={`${index}:${action}`}>{action}</li>)}</ol> : <p className="muted">没有可靠的行动建议。</p>}
              <h2>限制</h2>
              {(report.limitations ?? []).length ? <ul>{report.limitations!.map((limitation, index) => <li key={`${index}:${limitation}`}>{limitation}</li>)}</ul> : <p className="muted">服务端未返回额外限制。</p>}
              <EvaluationFeedback evaluationId={report.evaluationId} />
              <Link to={`/app/learning/reports/${report.id}`} className="button button-secondary">查看学习循环</Link>
            </aside>
          </div>}
        </>}
      </AsyncState>
    </PageFrame>
  );
}

export function InterviewReportRoutePage() {
  const { interviewId } = useParams();
  const { queryScope } = useSession();
  const report = useQuery({
    queryKey: queryKeys.evaluation.interviewReport(queryScope, interviewId ?? 'missing'),
    queryFn: ({ signal }) => evaluationApi.getInterviewReport(interviewId!, signal),
    enabled: Boolean(interviewId),
  });
  if (report.data) return <Navigate replace to={`/app/reports/${report.data.id}`} />;
  return <PageFrame eyebrow="REPORT RECOVERY" title="读取面试报告" description={`按 owner-scoped 映射读取面试 ${interviewId ?? '未知'} 的报告根。`}><AsyncState loading={report.isPending} error={report.error} onRetry={() => void report.refetch()}><div className="state-card"><strong>报告映射尚未返回</strong><small>不会把 interviewId 当作 reportId；请稍后重读或返回会话快照。</small><Link className="button button-primary" to={`/app/interviews/${interviewId ?? ''}`}>返回会话快照</Link></div></AsyncState></PageFrame>;
}

function EvaluationFeedback({ evaluationId }: { evaluationId: string }) {
  const operation = useOperationKey();
  const [type, setType] = useState<'INACCURATE' | 'UNHELPFUL' | 'MISSING_CONTEXT' | 'OTHER'>('UNHELPFUL');
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const [submitted, setSubmitted] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy || submitted) return;
    const operationName = `evaluation-feedback:${evaluationId}`;
    setBusy(true);
    setError(undefined);
    try {
      await evaluationApi.submitFeedback(evaluationId, { type, ...(comment.trim() ? { comment: comment.trim() } : {}) }, operation.keyFor(operationName));
      operation.markSucceeded(operationName);
      setSubmitted(true);
    } catch (cause) {
      setError(cause);
    } finally {
      setBusy(false);
    }
  }

  if (submitted) return <div className="inline-success" role="status">反馈已记录；历史评测版本不会被覆盖。</div>;
  return <form className="form-stack" onSubmit={submit}><label htmlFor="evaluation-feedback-type">报告反馈<select id="evaluation-feedback-type" value={type} onChange={(event) => setType(event.target.value as typeof type)}><option value="UNHELPFUL">帮助不大</option><option value="INACCURATE">判断不准确</option><option value="MISSING_CONTEXT">缺少上下文</option><option value="OTHER">其他</option></select></label><label htmlFor="evaluation-feedback-comment">补充说明（可选）<textarea id="evaluation-feedback-comment" maxLength={2000} rows={3} value={comment} onChange={(event) => setComment(event.target.value)} /></label>{Boolean(error) && <ApiErrorNotice error={error} />}<button type="submit" className="button button-ghost" disabled={busy}>{busy ? '提交中…' : '提交反馈'}</button></form>;
}

function reportStateTitle(state: string) {
  return ({ PENDING: '评测任务等待执行', RUNNING: '评测与报告正在生成', READY: '报告已准备好', PARTIAL: '报告部分完成', FAILED: '报告生成失败', CANCELLED: '报告任务已取消' } as Record<string, string>)[state] ?? '未知报告状态';
}
