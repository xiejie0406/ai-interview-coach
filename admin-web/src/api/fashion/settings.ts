import { fashionRequest } from './client'

export type FashionSettingType = 'INTEGER' | 'DECIMAL' | 'DICTIONARY_CODE'

export interface FashionSetting {
  key: string
  name: string
  description: string
  type: FashionSettingType
  value: string
  defaultValue: string
  dictionaryType?: string
}

export interface FashionSettingChange {
  key: string
  beforeValue: string
  afterValue: string
  operator: string
}

export interface RuoYiResult<T> {
  code: number
  msg?: string
  data: T
}

export function listFashionSettings(signal?: AbortSignal) {
  return fashionRequest<RuoYiResult<FashionSetting[]>>({
    path: 'settings',
    signal
  })
}

export function updateFashionSetting(key: string, value: string) {
  return fashionRequest<RuoYiResult<FashionSettingChange>, { key: string, value: string }>({
    path: 'settings',
    method: 'put',
    body: { key, value }
  })
}
