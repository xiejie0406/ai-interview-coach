const RUOYI_TOKEN_KEY = 'ruoyi-token';

export function getRuoYiToken(): string | null {
  if (typeof window === 'undefined') return null;
  return window.localStorage.getItem(RUOYI_TOKEN_KEY);
}

export function setRuoYiToken(token: string) {
  if (typeof window !== 'undefined') window.localStorage.setItem(RUOYI_TOKEN_KEY, token);
}

export function clearRuoYiToken() {
  if (typeof window !== 'undefined') window.localStorage.removeItem(RUOYI_TOKEN_KEY);
}

