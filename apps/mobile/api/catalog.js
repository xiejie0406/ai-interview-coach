import { interviewRequest } from '@/utils/interviewRequest'

export function listQuestions(params = {}) {
  const query = Object.entries(params).filter(([, value]) => value).map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`).join('&')
  return interviewRequest({ url: `/questions?limit=20${query ? `&${query}` : ''}` })
}

export function getQuestion(questionId) {
  return interviewRequest({ url: `/questions/${encodeURIComponent(questionId)}` })
}
