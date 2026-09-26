import request from '@/utils/request'

const base = kind => kind === 'AI' ? '/ai/secret' : '/system/platformSecret'

export const listManagedSecrets = (kind, project) => request({
  url: `${base(kind)}/list`, method: 'get', params: project ? { project } : {}
})

export const saveManagedSecret = (kind, data) => request({
  url: base(kind), method: 'post', data, headers: { repeatSubmit: false }
})

export const disableManagedSecret = (kind, alias, reason, expectedRowVersion) => request({
  url: `${base(kind)}/${encodeURIComponent(alias)}/disable`, method: 'post',
  data: { reason, expectedRowVersion }, headers: { repeatSubmit: false }
})

export const stagePreviousPlatformSecret = (alias, expectedRowVersion) => request({
  url: `/system/platformSecret/${encodeURIComponent(alias)}/stage-previous`, method: 'post',
  data: { expectedRowVersion }, headers: { repeatSubmit: false }
})

export const managedSecretAudit = (kind, alias) => request({
  url: `${base(kind)}/${encodeURIComponent(alias)}/audit`, method: 'get'
})

export const listManagedSecretVersions = (kind, alias) => request({
  url: `${base(kind)}/${encodeURIComponent(alias)}/versions`, method: 'get'
})

export const restoreManagedSecretVersion = (kind, alias, sourceVersion, expectedRowVersion, reason) => request({
  url: `${base(kind)}/${encodeURIComponent(alias)}/restore`, method: 'post',
  data: { sourceVersion, expectedRowVersion, reason }, headers: { repeatSubmit: false }
})
