import request from '@/utils/request'

export const listAiRoles = project => request({ url: '/ai/role/list', method: 'get', params: project ? { project } : {} })
export const listAiRoleSecretOptions = project => request({ url: '/ai/role/secret-options', method: 'get', params: { project } })
export const createAiRole = data => request({ url: '/ai/role', method: 'post', data, headers: { repeatSubmit: false } })
export const listAiRoleVersions = id => request({ url: `/ai/role/${id}/versions`, method: 'get' })
export const createAiRoleVersion = (id, data) => request({ url: `/ai/role/${id}/versions`, method: 'post', data, headers: { repeatSubmit: false } })
export const publishAiRoleVersion = (id, version, expectedRowVersion) => request({ url: `/ai/role/${id}/versions/${version}/publish`, method: 'post', data: { expectedRowVersion }, headers: { repeatSubmit: false } })
