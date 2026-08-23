import { create } from 'zustand';
import { SESSION_CLEARED_EVENT } from '../../../shared/api/client';

export type VoiceUiState =
  | 'IDLE'
  | 'PREFLIGHTING'
  | 'READY'
  | 'REQUESTING_PERMISSION'
  | 'LISTENING'
  | 'RECORDED_LOCAL'
  | 'CONNECTING'
  | 'TRANSCRIBING'
  | 'REVIEWING'
  | 'THINKING'
  | 'SPEAKING'
  | 'CANCELLING'
  | 'CANCELLED'
  | 'DISCONNECTED'
  | 'RECOVERING'
  | 'DEGRADED'
  | 'FAILED';

type VoiceStore = {
  state: VoiceUiState;
  deviceId?: string;
  degradationReason?: string;
  setState: (state: VoiceUiState) => void;
  selectDevice: (deviceId?: string) => void;
  degrade: (reason: string) => void;
  fail: (reason: string) => void;
  disconnect: (reason?: string) => void;
  cancel: (reason?: string) => void;
  reset: () => void;
};

export const useVoiceUiStore = create<VoiceStore>((set) => ({
  state: 'IDLE',
  setState: (state) => set({ state, degradationReason: undefined }),
  selectDevice: (deviceId) => set({ deviceId }),
  degrade: (degradationReason) => set({ state: 'DEGRADED', degradationReason }),
  fail: (degradationReason) => set({ state: 'FAILED', degradationReason }),
  disconnect: (degradationReason) => set({ state: 'DISCONNECTED', degradationReason }),
  cancel: (degradationReason) => set({ state: 'CANCELLED', degradationReason }),
  reset: () => set({ state: 'IDLE', deviceId: undefined, degradationReason: undefined }),
}));

if (typeof window !== 'undefined') {
  window.addEventListener(SESSION_CLEARED_EVENT, () => useVoiceUiStore.getState().reset());
}
