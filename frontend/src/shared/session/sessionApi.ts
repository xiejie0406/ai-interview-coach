import { apiRequest, type RuoYiResult } from '../api/client';

export type RuoYiUser = {
  userId: number;
  userName: string;
  nickName?: string;
  avatar?: string;
  status?: string;
  email?: string;
  phonenumber?: string;
  deptId?: number;
};

export type SessionAccountView = {
  userId: string;
  username: string;
  displayName: string;
  avatar?: string;
  email?: string;
  status?: string;
  roles: string[];
  permissions: string[];
};

export type RuoYiInfo = { user: RuoYiUser; roles?: string[]; permissions?: string[] };
export type RuoYiMenu = Record<string, unknown>;

export const sessionApi = {
  async getInfo(signal?: AbortSignal) {
    const result = await apiRequest<RuoYiResult<RuoYiInfo> & RuoYiInfo>('/getInfo', { signal });
    const info = result;
    if (!info?.user) throw new Error('RuoYi /getInfo 未返回当前用户');
    return {
      account: {
        userId: String(info.user.userId),
        username: info.user.userName,
        displayName: info.user.nickName || info.user.userName,
        avatar: info.user.avatar,
        email: info.user.email,
        status: info.user.status,
        roles: info.roles ?? [],
        permissions: info.permissions ?? [],
      } satisfies SessionAccountView,
      roles: info.roles ?? [],
      permissions: info.permissions ?? [],
    };
  },

  getRouters(signal?: AbortSignal) {
    return apiRequest<RuoYiResult<RuoYiMenu[]>>('/getRouters', { signal });
  },

  logout(signal?: AbortSignal) {
    return apiRequest<RuoYiResult<unknown>>('/logout', { method: 'POST', signal });
  },
};
