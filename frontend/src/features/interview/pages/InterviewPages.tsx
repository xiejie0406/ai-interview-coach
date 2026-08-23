import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { interviewApi, type InterviewCommand, type InterviewPlanView, type InterviewQuestion, type InterviewSetup } from '../api/interviewApi';
import { useInterviewStream } from '../hooks/useInterviewStream';
import { voiceApi, type VoiceCodec, type VoicePreflight, type VoiceSessionHandle } from '../../voice/api/voiceApi';
import { MicrophoneConsent } from '../../voice/input/MicrophoneConsent';
import { Recorder } from '../../voice/input/Recorder';
import { TranscriptEditor } from '../../voice/input/TranscriptEditor';
import { AudioPlayer } from '../../voice/output/AudioPlayer';
import { useVoiceSocket } from '../../voice/hooks/useVoiceSocket';
import { VoiceTurnStatus } from '../../voice/output/VoiceTurnStatus';
import { useVoiceUiStore } from '../../voice/store/voiceUiStore';
import { versionEtag } from '../../../shared/api/client';
import { queryKeys } from '../../../shared/api/queryKeys';
import { ApiErrorNotice, AsyncState, ReconnectBanner } from '../../../shared/components/AsyncState';
import { PageFrame } from '../../../shared/components/PageFrame';
import { useOperationKey } from '../../../shared/hooks/useOperationKey';
import { useSession } from '../../../shared/session/SessionProvider';

export function InterviewSetupPage() {
  const operation = useOperationKey();
  const navigate = useNavigate();
  const [setup, setSetup] = useState<InterviewSetup>({
    targetRole: 'AI_APPLICATION',
    targetLevel: 'MID',
    topics: ['Java', 'AI Agent'],
    durationMinutes: 10,
    mode: 'TEXT',
  });
  const [topicsText, setTopicsText] = useState('Java, AI Agent');
  const [plan, setPlan] = useState<InterviewPlanView>();
  const [planEtag, setPlanEtag] = useState<string>();
  const [busy, setBusy] = useState<'create' | 'confirm' | 'cancel' | 'session'>();
  const [error, setError] = useState<unknown>();
  const [validationError, setValidationError] = useState('');
  const setupLocked = plan?.state === 'DRAFT' || plan?.state === 'CONFIRMED';

  async function createPlan(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    const topics = topicsText.split(',').map((item) => item.trim()).filter(Boolean);
    if (topics.length < 1 || topics.length > 9) {
      setValidationError('主题必须包含 1–9 个非空项。');
      return;
    }
    const request = { ...setup, topics };
    const operationName = `interview-plan:create:${JSON.stringify(request)}`;
    setBusy('create');
    setError(undefined);
    setValidationError('');
    try {
      const response = await interviewApi.createPlan(request, operation.keyFor(operationName));
      setSetup(request);
      setPlan(response.data);
      setPlanEtag(response.etag ?? versionEtag(response.data.version));
      operation.markSucceeded(operationName);
    } catch (cause) {
      setError(cause);
    } finally {
      setBusy(undefined);
    }
  }

  async function applyPlanCommand(command: 'confirm' | 'cancel') {
    if (!plan || !planEtag || busy) return;
    const operationName = `interview-plan:${command}:${plan.id}:${plan.version}`;
    setBusy(command);
    setError(undefined);
    try {
      const response = await interviewApi.applyPlanCommand(plan.id, command, plan.estimatedUsage.estimateVersion, planEtag, operation.keyFor(operationName));
      setPlan(response.data);
      setPlanEtag(response.etag ?? versionEtag(response.data.version));
      operation.markSucceeded(operationName);
    } catch (cause) {
      setError(cause);
    } finally {
      setBusy(undefined);
    }
  }

  async function createSession() {
    if (!plan || plan.state !== 'CONFIRMED' || busy) return;
    const operationName = `interview-session:create:${plan.id}:${plan.planVersionNo}`;
    setBusy('session');
    setError(undefined);
    try {
      const response = await interviewApi.createSession(
        plan.id,
        plan.planVersionNo,
        operation.keyFor(operationName),
      );
      operation.markSucceeded(operationName);
      navigate(`/app/interviews/${encodeURIComponent(response.data.id)}?mode=${plan.mode}`, { replace: true });
    } catch (cause) {
      setError(cause);
    } finally {
      setBusy(undefined);
    }
  }

  return (
    <PageFrame eyebrow="INTERVIEW SETUP" title="配置一次真实但可控的练习" description="计划、估算、确认和会话创建是分开的服务端事实。">
      <form onSubmit={createPlan} className="setup-grid">
        <div className="panel form-stack">
          <label>目标方向<select disabled={setupLocked} value={setup.targetRole} onChange={(event) => setSetup((current) => ({ ...current, targetRole: event.target.value as InterviewSetup['targetRole'] }))}><option value="JAVA_BACKEND">Java 后端</option><option value="AI_APPLICATION">AI 应用</option><option value="AGENT_ENGINEER">Agent 工程</option></select></label>
          <label>目标级别<select disabled={setupLocked} value={setup.targetLevel} onChange={(event) => setSetup((current) => ({ ...current, targetLevel: event.target.value as InterviewSetup['targetLevel'] }))}><option value="JUNIOR">初级</option><option value="MID">中级</option><option value="SENIOR">高级</option></select></label>
          <label>主题（逗号分隔，1–9 项）<input disabled={setupLocked} value={topicsText} onChange={(event) => setTopicsText(event.target.value)} /></label>
          <label>时长<select disabled={setupLocked} value={setup.durationMinutes} onChange={(event) => setSetup((current) => ({ ...current, durationMinutes: Number(event.target.value) }))}><option value={10}>10 分钟</option><option value={25}>25 分钟</option><option value={45}>45 分钟</option></select></label>
          <label>交互模式<select disabled={setupLocked} value={setup.mode} onChange={(event) => setSetup((current) => ({ ...current, mode: event.target.value as InterviewSetup['mode'] }))}><option value="TEXT">文本</option><option value="CASCADE_VOICE">级联语音（需服务端同意）</option></select></label>
          <button className="button button-primary" disabled={Boolean(busy) || setupLocked}>{busy === 'create' ? '生成中…' : setupLocked ? '计划已锁定' : '生成计划与用量估算'}</button>
        </div>
        <div className="panel plan-preview">
          <span className="card-kicker">计划预览</span>
          {!plan ? <p className="muted">生成后才显示服务端题量、追问预算、用量估算、过期时间和版本。</p> : <>
            <h2>{plan.mode === 'CASCADE_VOICE' ? '级联语音' : '文本'} · {plan.questionCount} 题</h2>
            <ul><li>状态：{plan.state}</li><li>追问预算：{plan.followUpBudget}</li><li>预计用量：{plan.estimatedUsage.quantity} {plan.estimatedUsage.unit}</li><li>过期：{new Date(plan.expiresAt).toLocaleString()}</li></ul>
            {plan.state === 'DRAFT' && <div className="form-actions"><button type="button" className="button button-ghost" disabled={Boolean(busy)} onClick={() => void applyPlanCommand('cancel')}>取消计划</button><button type="button" className="button button-secondary" disabled={Boolean(busy)} onClick={() => void applyPlanCommand('confirm')}>确认估算与计划</button></div>}
            {plan.state === 'CONFIRMED' && <div className="form-actions"><Link className="button button-ghost" to={`/app/interview-plans/${encodeURIComponent(plan.id)}`}>打开计划快照</Link><button type="button" className="button button-primary" disabled={Boolean(busy)} onClick={() => void createSession()}>{busy === 'session' ? '创建会话中…' : '创建面试会话'}</button></div>}
          </>}
        </div>
      </form>
      {Boolean(error) && <ApiErrorNotice error={error} />}
      {validationError && <div className="inline-error" role="alert">{validationError}</div>}
      <div className="form-actions"><Link to="/app" className="button button-ghost">返回面板</Link></div>
    </PageFrame>
  );
}

export function InterviewPlanPage() {
  const { planId } = useParams();
  const navigate = useNavigate();
  const { queryScope } = useSession();
  const operation = useOperationKey();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const planQuery = useQuery({
    queryKey: queryKeys.interview.plan(queryScope, planId ?? 'missing'),
    queryFn: ({ signal }) => interviewApi.getPlan(planId!, signal),
    enabled: Boolean(planId),
  });
  const plan = planQuery.data?.data;

  async function createSession() {
    if (!plan || plan.state !== 'CONFIRMED' || busy) return;
    const operationName = `interview-session:create:${plan.id}:${plan.planVersionNo}`;
    setBusy(true);
    setError(undefined);
    try {
      const response = await interviewApi.createSession(plan.id, plan.planVersionNo,
        operation.keyFor(operationName));
      operation.markSucceeded(operationName);
      navigate(`/app/interviews/${encodeURIComponent(response.data.id)}?mode=${plan.mode}`, { replace: true });
    } catch (cause) {
      setError(cause);
    } finally {
      setBusy(false);
    }
  }

  return <PageFrame eyebrow="INTERVIEW PLAN" title="计划快照" description={`计划 ${planId ?? '未知'} · 刷新后只采用服务端状态`}><AsyncState loading={planQuery.isPending} error={planQuery.error} onRetry={() => void planQuery.refetch()}>{plan && <section className="panel plan-preview"><span className="card-kicker">{plan.state} · PLAN VERSION {plan.planVersionNo}</span><h2>{plan.mode === 'CASCADE_VOICE' ? '级联语音' : '文本'} · {plan.questionCount} 题</h2><ul><li>追问预算：{plan.followUpBudget}</li><li>预计用量：{plan.estimatedUsage.quantity} {plan.estimatedUsage.unit}</li><li>过期：{new Date(plan.expiresAt).toLocaleString()}</li></ul>{plan.state === 'CONFIRMED' && <button type="button" className="button button-primary" disabled={busy} onClick={() => void createSession()}>{busy ? '创建会话中…' : '创建面试会话'}</button>}{Boolean(error) && <ApiErrorNotice error={error} onRecover={() => void planQuery.refetch()} />}</section>}</AsyncState></PageFrame>;
}

export function InterviewRoomPage() {
  const { interviewId } = useParams();
  const [searchParams] = useSearchParams();
  const modeParam = searchParams.get('mode');
  const requestedVoiceMode = modeParam === 'voice' || modeParam === 'CASCADE_VOICE';
  const { queryScope } = useSession();
  const queryClient = useQueryClient();
  const operation = useOperationKey();
  const snapshotKey = useMemo(() => queryKeys.interview.snapshot(queryScope, interviewId ?? 'missing'), [interviewId, queryScope]);
  const snapshotQuery = useQuery({
    queryKey: snapshotKey,
    queryFn: ({ signal }) => interviewApi.recoverSession(interviewId!, signal),
    enabled: Boolean(interviewId),
  });
  const [question, setQuestion] = useState<InterviewQuestion>();
  const [answer, setAnswer] = useState('');
  const [pendingCommand, setPendingCommand] = useState<InterviewCommand>();
  const [busyAction, setBusyAction] = useState<string>();
  const [error, setError] = useState<unknown>();
  const [message, setMessage] = useState('');

  const refetchSnapshot = snapshotQuery.refetch;
  const recoverSnapshot = useCallback(async () => {
    await refetchSnapshot();
  }, [refetchSnapshot]);
  const onDurableEvent = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: snapshotKey, exact: true });
  }, [queryClient, snapshotKey]);
  const onQuestion = useCallback((next: InterviewQuestion) => setQuestion(next), []);
  const stream = useInterviewStream({
    interviewId,
    enabled: Boolean(snapshotQuery.data?.data && !['COMPLETED', 'CANCELLED', 'FAILED_FINAL'].includes(snapshotQuery.data.data.state)),
    recoverSnapshot,
    onDurableEvent,
    onQuestion,
  });

  const snapshot = snapshotQuery.data?.data;
  const voiceMode = snapshot ? snapshot.mode === 'CASCADE_VOICE' : requestedVoiceMode;
  const etag = snapshotQuery.data?.etag ?? (snapshot ? versionEtag(snapshot.version) : undefined);

  useEffect(() => {
    if (!snapshot) {
      setQuestion(undefined);
      return;
    }
    const current = [...snapshot.turns].reverse().find((turn) =>
      turn.state === 'QUESTION_COMMITTED' || turn.state === 'ANSWER_CONFIRMED');
    if (current?.questionText) {
      setQuestion({ turnId: current.turnId, turnSequence: current.sequence, text: current.questionText });
    } else if (!current) {
      setQuestion(undefined);
    }
  }, [snapshot]);

  async function runCommand(command: InterviewCommand) {
    if (!snapshot || !etag || busyAction) return;
    const operationName = `interview:${command}:${snapshot.id}:${snapshot.version}`;
    setBusyAction(command);
    setError(undefined);
    setMessage('');
    try {
      const response = await interviewApi.applyCommand(snapshot.id, command, etag, operation.keyFor(operationName));
      operation.markSucceeded(operationName);
      if ('statusUrl' in response.data) setMessage(`操作已受理：${response.data.operationId}。这不表示操作已完成。`);
      setPendingCommand(undefined);
      await snapshotQuery.refetch();
    } catch (cause) {
      setError(cause);
    } finally {
      setBusyAction(undefined);
    }
  }

  async function submitAnswer(event: FormEvent) {
    event.preventDefault();
    if (!snapshot || !etag || !question || !answer.trim() || busyAction
      || !snapshot.allowedCommands.includes('SUBMIT_ANSWER')) return;
    const operationName = `interview:answer:${snapshot.id}:${question.turnId}:${question.turnSequence}`;
    setBusyAction('answer');
    setError(undefined);
    setMessage('');
    try {
      const response = await interviewApi.submitAnswer(snapshot.id, { turnId: question.turnId, turnSequence: question.turnSequence, text: answer.trim() }, etag, operation.keyFor(operationName));
      operation.markSucceeded(operationName);
      setAnswer('');
      setMessage(`回答已受理为操作 ${response.data.operationId}；等待 durable 事件或快照确认下一题。`);
      await snapshotQuery.refetch();
    } catch (cause) {
      setError(cause);
    } finally {
      setBusyAction(undefined);
    }
  }

  const allowed = useMemo(() => new Set(snapshot?.allowedCommands ?? []), [snapshot?.allowedCommands]);
  return (
    <PageFrame eyebrow="INTERVIEW ROOM" title={voiceMode ? '级联语音面试室' : '文本面试室'} description={`会话 ${interviewId ?? '未知'} · 快照与 durable event 是恢复依据`}>
      <AsyncState loading={snapshotQuery.isPending} error={snapshotQuery.error} onRetry={() => void snapshotQuery.refetch()}>
        {snapshot && <>
          <ReconnectBanner visible={!['CONNECTED', 'IDLE'].includes(stream.state)} exhausted={stream.state === 'EXHAUSTED'} onRecover={() => { void recoverSnapshot(); stream.recover(); }} />
          <div className="room-layout">
            <aside className="panel room-meta">
              <span className="card-kicker">{snapshot.state}</span>
              <strong>稳定轮次 {snapshot.lastStableTurnSequence}</strong>
              <span className="muted">计划 v{snapshot.planVersionNo} · 预留 {snapshot.reservation.state} · 报告 {snapshot.report.state}</span>
              <span className="muted">待处理 Job：{snapshot.pendingJobIds.length}</span>
              {snapshot.failureCode && <span className="inline-error">{snapshot.failureCode}</span>}
              {snapshot.report.id && <Link className="button button-secondary" to={`/app/reports/${snapshot.report.id}`}>查看报告</Link>}
              {(['start', 'pause', 'resume', 'skip'] as InterviewCommand[]).map((command) => allowed.has(command.toUpperCase() as Uppercase<InterviewCommand>) && <button key={command} type="button" className="button button-ghost" disabled={Boolean(busyAction)} onClick={() => void runCommand(command)}>{commandLabel(command)}</button>)}
              {(['complete', 'cancel'] as InterviewCommand[]).map((command) => allowed.has(command.toUpperCase() as Uppercase<InterviewCommand>) && <button key={command} type="button" className={command === 'cancel' ? 'button button-danger' : 'button button-secondary'} disabled={Boolean(busyAction)} onClick={() => setPendingCommand(command)}>{commandLabel(command)}</button>)}
            </aside>
            <section className="panel timeline">
              {question ? <div className="message agent-message"><span className="speaker">面试官 · Turn {question.turnSequence}</span><p>{question.text}</p></div> : <div className="state-card"><strong>等待已提交的问题</strong><small>页面不会用示例问题替代 `interview.question.committed` 事实。</small></div>}
              {snapshot.mode === 'CASCADE_VOICE' && <VoicePreparation interviewId={snapshot.id} snapshotVersion={snapshot.version} question={question} />}
              <form onSubmit={submitAnswer} className="composer">
                <label htmlFor="interview-answer">文本回答</label>
                <textarea id="interview-answer" required rows={7} maxLength={30_000} disabled={!question || snapshot.state !== 'IN_PROGRESS' || !allowed.has('SUBMIT_ANSWER')} value={answer} onChange={(event) => setAnswer(event.target.value)} placeholder="语音不可用时可始终使用文本…" />
                <div className="form-actions"><span className="muted">提交后不自动重放；状态不明时先恢复快照。</span><button disabled={!question || !answer.trim() || Boolean(busyAction) || snapshot.state !== 'IN_PROGRESS' || !allowed.has('SUBMIT_ANSWER')} className="button button-primary">{busyAction === 'answer' ? '提交中…' : '提交回答'}</button></div>
              </form>
              {pendingCommand && <div className="confirmation-card" role="alertdialog" aria-modal="false"><strong>确认{commandLabel(pendingCommand)}？</strong><small>这是服务端状态命令，不等同于停止本地播放或录音。</small><div className="form-actions"><button type="button" className="button button-ghost" onClick={() => setPendingCommand(undefined)}>返回</button><button type="button" className="button button-danger" onClick={() => void runCommand(pendingCommand)}>确认</button></div></div>}
              {Boolean(error) && <ApiErrorNotice error={error} onRecover={() => void recoverSnapshot()} />}
              {message && <div className="inline-success" role="status">{message}</div>}
            </section>
          </div>
        </>}
      </AsyncState>
    </PageFrame>
  );
}

function VoicePreparation({ interviewId, snapshotVersion, question }: { interviewId: string; snapshotVersion: number; question?: InterviewQuestion }) {
  const voice = useVoiceUiStore();
  const operation = useOperationKey();
  const [localConsent, setLocalConsent] = useState(false);
  const [preflight, setPreflight] = useState<VoicePreflight>();
  const [handle, setHandle] = useState<VoiceSessionHandle>();
  const [transcript, setTranscript] = useState<{ transcriptId: string; transcriptVersionId: string; text: string; transcriptVersion: number; lowConfidenceSpans: Array<{ startInclusive: number; endExclusive: number; confidence: number }> }>();
  const [ttsAudio, setTtsAudio] = useState<Blob>();
  const ttsChunks = useRef<Uint8Array[]>([]);
  const [error, setError] = useState<unknown>();
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const socket = useVoiceSocket({
    handle,
    sessionId: interviewId,
    turnId: question?.turnId ?? '',
    enabled: Boolean(handle && localConsent),
    contractReady: true,
  });

  useEffect(() => {
    setLocalConsent(false);
    setPreflight(undefined);
    setHandle(undefined);
    setTranscript(undefined);
    setTtsAudio(undefined);
    ttsChunks.current = [];
    setError(undefined);
    voice.reset();
  }, [question?.turnId, snapshotVersion, voice.reset]);

  useEffect(() => () => voice.reset(), [voice.reset]);

  useEffect(() => {
    if (socket.state === 'CONNECTING' || socket.state === 'AUTHENTICATING') voice.setState('CONNECTING');
    if (socket.state === 'PAUSED_BACKPRESSURE') voice.setState('CANCELLING');
    if (socket.state === 'DISCONNECTED' || socket.state === 'FAILED') voice.degrade('VOICE_SOCKET_DISCONNECTED');
    const message = socket.lastMessage;
    if (!message) return;
    if (message.type === 'asr.final') {
      const data = message.data;
      if (typeof data.transcriptId === 'string' && typeof data.transcriptVersionId === 'string' && typeof data.text === 'string') {
        setTranscript({
          transcriptId: data.transcriptId,
          transcriptVersionId: data.transcriptVersionId,
          text: data.text,
          transcriptVersion: typeof data.transcriptVersion === 'number' ? data.transcriptVersion : 1,
          lowConfidenceSpans: Array.isArray(data.lowConfidenceSpans) ? data.lowConfidenceSpans as Array<{ startInclusive: number; endExclusive: number; confidence: number }> : [],
        });
        voice.setState('REVIEWING');
      }
    } else if (message.type === 'tts.state') {
      const state = message.data.state;
      if (state === 'STARTED') { ttsChunks.current = []; setTtsAudio(undefined); voice.setState('SPEAKING'); }
      if (state === 'FAILED') voice.degrade(String(message.data.reasonCode ?? 'TTS_FAILED'));
      if (state === 'CANCELLED') { ttsChunks.current = []; setTtsAudio(undefined); voice.cancel('TTS_CANCELLED'); }
    } else if (message.type === 'tts.chunk' && typeof message.data.bytesBase64 === 'string') {
      const bytes = base64ToBytes(message.data.bytesBase64);
      ttsChunks.current.push(bytes);
      if (message.data.endOfOutput === true) {
        setTtsAudio(new Blob(ttsChunks.current.map((bytes) => bytes as unknown as BlobPart), { type: 'audio/ogg;codecs=opus' }));
      }
    }
  }, [socket.lastMessage, socket.state, voice]);

  async function prepare() {
    if (!localConsent || !question || busy) return;
    setBusy(true);
    setError(undefined);
    setTranscript(undefined);
    voice.setState('PREFLIGHTING');
    try {
      const codecs = supportedVoiceCodecs();
      if (!codecs.length) {
        voice.degrade('NO_SUPPORTED_RECORDER_CODEC');
        return;
      }
      const result = await voiceApi.preflight(interviewId, question.turnId, codecs);
      setPreflight(result);
      if (!result.enabled) {
        voice.degrade('VOICE_DISABLED');
        return;
      }
      if (result.consentRequired) {
        voice.degrade('SERVER_CONSENT_REQUIRED');
        return;
      }
      const codec = codecs.find((candidate) => result.supportedCodecs.includes(candidate));
      if (!codec) {
        voice.degrade('UNSUPPORTED_CODEC');
        return;
      }
      const operationName = `voice-session:open:${interviewId}:${question.turnId}:${snapshotVersion}:${codec}`;
      const opened = await voiceApi.openSession(interviewId, question.turnId, codec, snapshotVersion, operation.keyFor(operationName));
      setHandle(opened);
      operation.markSucceeded(operationName);
      voice.setState('READY');
    } catch (cause) {
      setError(cause);
      voice.degrade('VOICE_PREPARATION_FAILED');
    } finally {
      setBusy(false);
    }
  }

  function revokeLocalConsent(granted: boolean) {
    setLocalConsent(granted);
    if (!granted) {
      setTranscript(undefined);
      setPreflight(undefined);
      setHandle(undefined);
      voice.cancel('LOCAL_CONSENT_REVOKED');
    } else {
      voice.reset();
    }
  }

  async function confirmTranscript(correctedText: string, lowConfidenceAcknowledged: boolean) {
    if (!transcript) return;
    setBusy(true);
    setError(undefined);
    try {
      const operationName = `voice-transcript:confirm:${transcript.transcriptId}:${transcript.transcriptVersion}`;
      const result = await voiceApi.confirmTranscript(
        transcript.transcriptId,
        { transcriptVersionId: transcript.transcriptVersionId, correctedText, lowConfidenceAcknowledged },
        `"v${transcript.transcriptVersion}"`, operation.keyFor(operationName));
      operation.markSucceeded(operationName);
      setMessage(`转写已确认，已受理下一步操作 ${result.nextStepOperation.operationId}。`);
      setTranscript(undefined);
      socket.cancelAudio('TRANSCRIPT_CONFIRMED');
      voice.setState('THINKING');
    } catch (cause) {
      setError(cause);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="voice-stack">
      <VoiceTurnStatus state={voice.state} reason={voice.degradationReason} />
      <MicrophoneConsent granted={localConsent} onChange={revokeLocalConsent} />
      {!handle && <button type="button" className="button button-secondary" disabled={!localConsent || !question || busy} onClick={() => void prepare()}>{busy ? '检查中…' : '执行服务端 preflight 并获取 handle'}</button>}
      {handle && <Recorder
        enabled={localConsent && socket.state === 'OPEN'}
        streaming
        supportedCodecs={preflight?.supportedCodecs ?? []}
        maxDurationSeconds={preflight?.maxDurationSeconds}
        maxBytes={preflight?.maxBytes}
        onStarted={(sampleRate, channelCount) => socket.startAudio(sampleRate, channelCount)}
        onChunk={(bytes, durationMs) => socket.sendAudio(bytes, durationMs)}
        onStopped={(totalBytes, totalDurationMs) => { socket.stopAudio(totalBytes, totalDurationMs); voice.setState('TRANSCRIBING'); }}
        onCancelled={(reason) => socket.cancelAudio(reason)}
      />}
      {transcript && <TranscriptEditor
        initialText={transcript.text}
        lowConfidenceRanges={transcript.lowConfidenceSpans.map((span) => ({ start: span.startInclusive, end: span.endExclusive }))}
        onConfirm={(text, acknowledged) => void confirmTranscript(text, acknowledged)}
        onCancel={() => { setTranscript(undefined); socket.cancelAudio('TRANSCRIPT_REVIEW_CANCELLED'); voice.reset(); }}
      />}
      {ttsAudio && <AudioPlayer audio={ttsAudio} onEnded={() => { setTtsAudio(undefined); voice.setState('IDLE'); }} onCancelled={() => { socket.cancelTts(); setTtsAudio(undefined); voice.cancel('TTS_PLAYBACK_CANCELLED'); }} />}
      {handle && <small className="muted">Handle {handle.voiceSessionId} · {handle.codec} · {new Date(handle.expiresAt).toLocaleTimeString()} 过期</small>}
      {message && <div className="inline-success" role="status">{message}</div>}
      {Boolean(error) && <ApiErrorNotice error={error} />}
    </div>
  );
}

function base64ToBytes(value: string) {
  const binary = atob(value);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function supportedVoiceCodecs(): VoiceCodec[] {
  if (typeof MediaRecorder === 'undefined') return [];
  const candidates: VoiceCodec[] = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/wav'];
  return candidates.filter((codec) => MediaRecorder.isTypeSupported(codec));
}

function commandLabel(command: InterviewCommand) {
  return ({ start: '开始', pause: '暂停', resume: '继续', skip: '跳过本题', complete: '主动完成', cancel: '取消会话' } as const)[command];
}
