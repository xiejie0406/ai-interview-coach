import { interviewRequest, operationKey } from '@/utils/interviewRequest'

export function login(username, password, code = '', uuid = '') {
  return interviewRequest({ url: '/login', method: 'POST', data: { username, password, code, uuid } }).then((result) => {
    if (!result?.token) throw new Error('RuoYi 登录响应缺少 Token')
    uni.setStorageSync('App-Token', result.token)
    return result
  })
}

export function currentAccount() {
  return interviewRequest({ url: '/getInfo' })
}

export function currentPolicies() {
  return interviewRequest({ url: '/policies/current' })
}

export function registerAccount(data) {
  return Promise.reject(new Error('主页不提供第二套注册体系，请由 RuoYi 管理员开通账号'))
}
