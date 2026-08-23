import type { VoiceUiState } from '../store/voiceUiStore';

const labels: Record<VoiceUiState, string> = {
  IDLE: '等待语音准备',
  PREFLIGHTING: '正在检查能力与同意',
  READY: '可以由用户手势开始采集',
  REQUESTING_PERMISSION: '请求麦克风权限',
  LISTENING: '正在本地采集',
  RECORDED_LOCAL: '录音仅保留在浏览器内存',
  CONNECTING: '正在建立语音连接',
  TRANSCRIBING: '正在转写',
  REVIEWING: '等待确认转写',
  THINKING: '正在生成下一步',
  SPEAKING: '正在播放问题',
  CANCELLING: '正在停止采集',
  CANCELLED: '语音操作已取消',
  DISCONNECTED: '语音连接已断开',
  RECOVERING: '正在恢复服务端状态',
  DEGRADED: '已降级为文本',
  FAILED: '语音链路失败',
};

export function VoiceTurnStatus({ state, reason }: { state: VoiceUiState; reason?: string }) {
  return <div className={`voice-status ${state.toLowerCase()}`} role="status" aria-live="polite"><span className="status-dot" /><strong>{labels[state]}</strong>{reason && <small>{reason}</small>}</div>;
}
