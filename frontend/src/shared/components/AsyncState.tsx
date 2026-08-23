import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ApiClientError } from '../api/client';

type ErrorPresentation = { title: string; detail: string; retryAllowed: boolean };

function presentError(error: unknown, publicResource: boolean, sessionError: boolean): ErrorPresentation {
  if (!(error instanceof ApiClientError)) {
    return { title: '服务暂时不可用', detail: '连接没有成功，未保存的内容仍保留在当前页面。', retryAllowed: true };
  }
  if (publicResource && [401, 404, 501].includes(error.status)) return { title: '公开能力暂不可用', detail: '服务端当前没有返回该公开资源；这不会清除当前登录会话。', retryAllowed: false };
  if (error.status === 401 && !sessionError) return { title: '当前能力暂不可用', detail: '服务端拒绝了该菜单的数据请求；当前登录会话保持不变。', retryAllowed: false };
  if (error.status === 401) return { title: '登录状态已过期', detail: '敏感内存和连接已停止；重新登录后先读取服务端快照。', retryAllowed: false };
  if (error.status === 403) return { title: '当前操作被拒绝', detail: '请检查同意、权益或角色；策略拒绝不会通过重试绕过。', retryAllowed: false };
  if (error.status === 404) return { title: '没有找到可访问的数据', detail: '资源可能不存在、已下线，或服务端为保护作用域隐藏了它。', retryAllowed: false };
  if ([409, 412, 428].includes(error.status)) return { title: '服务端状态已经变化', detail: '请重新读取最新状态，再决定是否合并或继续。', retryAllowed: false };
  if (error.status === 410) return { title: '恢复游标或内容已过期', detail: '需要读取最新快照；未确认的语音片段应重新录制。', retryAllowed: false };
  if (error.status === 422) return { title: '请求内容无法处理', detail: '请检查当前状态、确认项和必填内容后重新提交。', retryAllowed: false };
  if (error.status === 501) return { title: '该能力尚未启用', detail: '服务端前置能力或安全配置尚未闭合，当前不会执行替代成功流程。', retryAllowed: false };
  if (error.status === 429) {
    const wait = error.retryAfterSeconds === undefined ? '稍后' : `${error.retryAfterSeconds} 秒后`;
    return { title: '请求过于频繁', detail: `请在${wait}手动重试，重试同一操作时复用原幂等键。`, retryAllowed: true };
  }
  return {
    title: error.message || '服务暂时不可用',
    detail: error.retryable ? '可以有限重试；如状态不明，先读取服务端快照。' : '请按页面提供的恢复入口处理。',
    retryAllowed: error.retryable,
  };
}

export function ApiErrorNotice({ error, onRetry, onRecover, publicResource = false, sessionError = true }: { error: unknown; onRetry?: () => void; onRecover?: () => void; publicResource?: boolean; sessionError?: boolean }) {
  const presentation = presentError(error, publicResource, sessionError);
  const apiError = error instanceof ApiClientError ? error : undefined;
  const [retryCountdown, setRetryCountdown] = useState(apiError?.status === 429 ? apiError.retryAfterSeconds ?? 1 : 0);
  useEffect(() => {
    setRetryCountdown(apiError?.status === 429 ? apiError.retryAfterSeconds ?? 1 : 0);
  }, [apiError?.retryAfterSeconds, apiError?.status]);
  useEffect(() => {
    if (retryCountdown <= 0) return;
    const timer = window.setTimeout(() => setRetryCountdown((current) => Math.max(0, current - 1)), 1000);
    return () => window.clearTimeout(timer);
  }, [retryCountdown]);
  return (
    <div className="state-card error-state" role="alert">
      <strong>{presentation.title}</strong>
      <small>{presentation.detail}</small>
      {apiError && <small className="error-reference">{apiError.code} · Correlation {apiError.correlationId ?? '未返回'}</small>}
      <div className="state-actions">
        {onRecover && <button type="button" className="button button-secondary" onClick={onRecover}>读取最新状态</button>}
        {onRetry && presentation.retryAllowed && <button type="button" className="button button-ghost" disabled={retryCountdown > 0} onClick={onRetry}>{retryCountdown > 0 ? `${retryCountdown}s 后可重试` : '重试'}</button>}
        {apiError?.status === 401 && sessionError && <Link className="button button-primary" to="/auth/login">重新登录</Link>}
      </div>
    </div>
  );
}

export function AsyncState({ loading, error, empty, children, onRetry, onRecover, publicResource = false, sessionError = false }: {
  loading?: boolean;
  error?: unknown;
  empty?: boolean;
  children: ReactNode;
  onRetry?: () => void;
  onRecover?: () => void;
  publicResource?: boolean;
  sessionError?: boolean;
}) {
  if (loading) return <div className="state-card" role="status" aria-live="polite"><span className="spinner" />正在加载…</div>;
  if (error) return <ApiErrorNotice error={error} onRetry={onRetry} onRecover={onRecover} publicResource={publicResource} sessionError={sessionError} />;
  if (empty) return <div className="state-card"><strong>这里还没有内容</strong><small>完成一次练习后，反馈和下一步会显示在这里。</small></div>;
  return <>{children}</>;
}

export function PermissionDenied() {
  return <div className="state-card error-state"><strong>没有访问权限</strong><small>服务端没有授权当前账号查看这个资源。</small></div>;
}

export function ReconnectBanner({ visible, exhausted = false, onRecover }: { visible: boolean; exhausted?: boolean; onRecover?: () => void }) {
  if (!visible) return null;
  return (
    <div className="reconnect-banner" role="status">
      <span>{exhausted ? '自动恢复已停止。' : '连接已中断。'} 服务端快照仍是状态真相。</span>
      {onRecover && <button type="button" className="button button-small button-ghost" onClick={onRecover}>读取快照</button>}
    </div>
  );
}
