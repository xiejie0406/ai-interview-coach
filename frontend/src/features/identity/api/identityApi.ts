import { apiRequest, type RuoYiResult } from '../../../shared/api/client';
import type { RuoYiInfo } from '../../../shared/session/sessionApi';

export type LoginRequest = { username: string; password: string; code: string; uuid: string };
export type CaptchaView = { captchaEnabled: boolean; uuid?: string; img?: string };

export const identityApi = {
  getCaptcha(signal?: AbortSignal) {
    return apiRequest<RuoYiResult<CaptchaView>>('/captchaImage', { signal });
  },

  login(request: LoginRequest, signal?: AbortSignal) {
    return apiRequest<RuoYiResult<never>>('/login', { method: 'POST', body: JSON.stringify(request), signal });
  },

  getInfo(signal?: AbortSignal) {
    return apiRequest<RuoYiResult<RuoYiInfo> & RuoYiInfo>('/getInfo', { signal });
  },

  getRouters(signal?: AbortSignal) {
    return apiRequest<RuoYiResult<Record<string, unknown>[]>>('/getRouters', { signal });
  },
};
