import request from '@/utils/interviewRequest'

export function listInterviewQuestions(params) {
  const normalized = Object.fromEntries(
    Object.entries(params ?? {}).filter(([, value]) => value !== '' && value !== null && value !== undefined)
  )
  return request({ url: '/questions', method: 'get', params: normalized })
}

export function getInterviewQuestion(questionId) {
  return request({ url: `/questions/${encodeURIComponent(questionId)}`, method: 'get' })
}

export function createInterviewQuestion(data) {
  return request({ url: '/questions', method: 'post', data })
}
