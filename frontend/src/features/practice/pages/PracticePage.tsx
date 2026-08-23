import { useEffect, useState, type FormEvent } from 'react';
import { Link, useBlocker, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { catalogApi } from '../../catalog/api/catalogApi';
import { practiceApi, type PracticeAttemptView } from '../api/practiceApi';
import { versionEtag } from '../../../shared/api/client';
import { queryKeys } from '../../../shared/api/queryKeys';
import { AsyncState, ApiErrorNotice } from '../../../shared/components/AsyncState';
import { PageFrame } from '../../../shared/components/PageFrame';
import { useOperationKey } from '../../../shared/hooks/useOperationKey';
import { useSession } from '../../../shared/session/SessionProvider';

export function PracticePage() {
  const { questionId } = useParams();
  const { queryScope } = useSession();
  const operation = useOperationKey();
  const questionQuery = useQuery({
    queryKey: queryKeys.catalog.question(queryScope, questionId ?? 'missing'),
    queryFn: ({ signal }) => catalogApi.getQuestion(questionId!, signal),
    enabled: Boolean(questionId),
  });
  const [attempt, setAttempt] = useState<PracticeAttemptView>();
  const [etag, setEtag] = useState<string>();
  const [answer, setAnswer] = useState('');
  const [lastSavedAnswer, setLastSavedAnswer] = useState('');
  const [busyAction, setBusyAction] = useState<'start' | 'draft' | 'submit'>();
  const [message, setMessage] = useState('');
  const [error, setError] = useState<unknown>();
  const dirty = Boolean(attempt && attempt.state === 'DRAFT' && answer !== lastSavedAnswer);
  const navigationBlocker = useBlocker(dirty);

  useEffect(() => {
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (!dirty) return;
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', beforeUnload);
    return () => window.removeEventListener('beforeunload', beforeUnload);
  }, [dirty]);

  function acceptAttempt(next: PracticeAttemptView, responseEtag?: string) {
    setAttempt(next);
    setEtag(responseEtag ?? versionEtag(next.version));
  }

  async function start() {
    const question = questionQuery.data?.data;
    if (!question || busyAction) return;
    const operationName = `practice:start:${question.versionId}`;
    setBusyAction('start');
    setError(undefined);
    setMessage('');
    try {
      const response = await practiceApi.start(question.versionId, operation.keyFor(operationName));
      acceptAttempt(response.data, response.etag);
      operation.markSucceeded(operationName);
      setMessage('练习已按当前发布版本建立。');
    } catch (cause) {
      setError(cause);
    } finally {
      setBusyAction(undefined);
    }
  }

  async function recoverAttempt() {
    if (!attempt) return;
    setError(undefined);
    try {
      const history = await practiceApi.listHistory();
      const recovered = history.find((item) => item.id === attempt.id);
      if (!recovered) {
        setMessage('服务端历史列表未返回当前练习；本地文本没有自动提交，请返回题库重新确认内容版本。');
        return;
      }
      acceptAttempt(recovered);
      setMessage('已读取服务端练习状态；请检查本地文本后再决定是否保存。');
    } catch (cause) {
      setError(cause);
    }
  }

  async function saveDraft() {
    if (!attempt || !etag || busyAction) return;
    const operationName = `practice:draft:${attempt.id}:${attempt.version}`;
    setBusyAction('draft');
    setError(undefined);
    setMessage('');
    try {
      const response = await practiceApi.saveDraft(attempt.id, answer, etag, operation.keyFor(operationName));
      acceptAttempt(response.data, response.etag);
      setLastSavedAnswer(answer);
      operation.markSucceeded(operationName);
      setMessage('草稿已保存到服务端。');
    } catch (cause) {
      setError(cause);
    } finally {
      setBusyAction(undefined);
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!attempt || !etag || !answer.trim() || busyAction) return;
    const operationName = `practice:submit:${attempt.id}:${attempt.version}`;
    setBusyAction('submit');
    setError(undefined);
    setMessage('');
    try {
      const response = await practiceApi.submit(attempt.id, answer.trim(), etag, operation.keyFor(operationName));
      acceptAttempt(response.data, response.etag);
      setLastSavedAnswer(answer.trim());
      operation.markSucceeded(operationName);
      setMessage('回答版本已提交；评测仍以服务端返回的状态为准。');
    } catch (cause) {
      setError(cause);
    } finally {
      setBusyAction(undefined);
    }
  }

  const question = questionQuery.data?.data;
  return (
    <PageFrame eyebrow="PRACTICE" title={question?.title ?? '单题练习'} description="先建立版本化练习，再显式保存草稿或提交不可变回答。">
      <AsyncState loading={questionQuery.isPending} error={questionQuery.error} onRetry={() => void questionQuery.refetch()}>
        {question && <div className="practice-layout">
          <div className="panel question-prompt">
            <span className="card-kicker">版本 {question.version} · {question.difficulty}</span>
            <p className="question-body">{question.prompt}</p>
            {!attempt && <button type="button" className="button button-primary" disabled={Boolean(busyAction)} onClick={() => void start()}>{busyAction === 'start' ? '建立中…' : '按此版本开始答题'}</button>}
          </div>
          {attempt && <form onSubmit={submit}>
            <div className="attempt-meta"><span className="status-chip">{attempt.state}</span><span>Attempt {attempt.id}</span><span>v{attempt.version}</span></div>
            <label className="answer-box" htmlFor="practice-answer">
              <span>你的回答</span>
              <textarea id="practice-answer" required minLength={1} maxLength={20_000} rows={12} disabled={attempt.state !== 'DRAFT'} value={answer} onChange={(event) => setAnswer(event.target.value)} placeholder="用自己的话回答…" />
              <small>{answer.length} / 20000 字</small>
            </label>
            <div className="form-actions">
              <Link to="/questions" className="button button-ghost">离开</Link>
              <button type="button" disabled={Boolean(busyAction) || attempt.state !== 'DRAFT'} className="button button-secondary" onClick={() => void saveDraft()}>{busyAction === 'draft' ? '保存中…' : '保存草稿'}</button>
              <button disabled={Boolean(busyAction) || attempt.state !== 'DRAFT' || !answer.trim()} className="button button-primary">{busyAction === 'submit' ? '提交中…' : '提交回答'}</button>
            </div>
          </form>}
          {Boolean(error) && <ApiErrorNotice error={error} onRetry={busyAction ? undefined : attempt ? undefined : () => void start()} onRecover={attempt ? () => void recoverAttempt() : undefined} />}
          {message && <div className="inline-success" role="status">{message}</div>}
          {attempt?.evaluationStatus && <div className="notice">评测状态：{attempt.evaluationStatus}</div>}
          {navigationBlocker.state === 'blocked' && <div className="confirmation-card" role="alertdialog" aria-modal="false"><strong>有未保存的本地修改</strong><small>离开会丢弃当前页面中尚未保存的文本。</small><div className="form-actions"><button type="button" className="button button-ghost" onClick={() => navigationBlocker.reset?.()}>继续编辑</button><button type="button" className="button button-danger" onClick={() => navigationBlocker.proceed?.()}>丢弃并离开</button></div></div>}
        </div>}
      </AsyncState>
    </PageFrame>
  );
}

export function PracticeHistoryPage() {
  const { queryScope } = useSession();
  const history = useQuery({
    queryKey: queryKeys.practice.history(queryScope),
    queryFn: ({ signal }) => practiceApi.listHistory(signal),
  });
  return (
    <PageFrame eyebrow="PRACTICE HISTORY" title="单题练习记录" description="只展示当前 tenant 的服务端练习状态；回答正文不会进入列表 query key。">
      <AsyncState loading={history.isPending} error={history.error} empty={Boolean(history.data && history.data.length === 0)} onRetry={() => void history.refetch()}>
        <div className="learning-list">{(history.data ?? []).map((attempt) => <article className="learning-item" key={attempt.id}><span className="status-chip">{attempt.state}</span><div><h2>Attempt {attempt.id}</h2><p className="muted">Question version {attempt.questionVersionId} · 回答版本 {attempt.answerVersions.length} · 评测 {attempt.evaluationStatus ?? '未返回'}</p></div></article>)}</div>
      </AsyncState>
      <div className="form-actions"><Link to="/questions" className="button button-secondary">选择新题目</Link></div>
    </PageFrame>
  );
}
