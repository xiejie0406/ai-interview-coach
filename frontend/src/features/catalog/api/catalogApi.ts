import { apiRequest, apiRequestWithMeta, type ApiResponse } from '../../../shared/api/client';

export type Difficulty = 'JUNIOR' | 'MID' | 'SENIOR';

export type QuestionSummary = {
  id: string;
  versionId: string;
  title: string;
  category: string;
  difficulty: Difficulty;
  estimatedMinutes?: number;
  status: 'PUBLISHED';
  version: number;
};

export type QuestionDetail = QuestionSummary & {
  prompt: string;
  referenceAnswer: string[];
  systemAnswer: string[];
  hasUserAnswer: boolean;
};

export type QuestionPage = {
  items: QuestionSummary[];
  nextCursor?: string | null;
  hasMore: boolean;
};

export type QuestionFilters = {
  query?: string;
  category?: string;
  difficulty?: Difficulty;
  cursor?: string;
  limit?: number;
};

export function serializeQuestionFilters(filters: QuestionFilters) {
  const params = new URLSearchParams();
  if (filters.query) params.set('query', filters.query);
  if (filters.category) params.set('category', filters.category);
  if (filters.difficulty) params.set('difficulty', filters.difficulty);
  if (filters.cursor) params.set('cursor', filters.cursor);
  params.set('limit', String(filters.limit ?? 20));
  return params.toString();
}

export const catalogApi = {
  search(filters: QuestionFilters, signal?: AbortSignal) {
    return apiRequest<QuestionPage>(`/questions?${serializeQuestionFilters(filters)}`, { signal });
  },

  getQuestion(id: string, signal?: AbortSignal): Promise<ApiResponse<QuestionDetail>> {
    return apiRequestWithMeta<QuestionDetail>(`/questions/${encodeURIComponent(id)}`, { signal });
  },

  createQuestion(input: { category: string; difficulty: Difficulty; title: string; prompt: string; systemAnswer: string }) {
    return apiRequest<{ id: string }>('/questions', { method: 'POST', body: JSON.stringify(input) });
  },

  saveMyAnswer(id: string, answer: string) {
    return apiRequest<void>(`/questions/${encodeURIComponent(id)}/my-answer`, {
      method: 'PUT', body: JSON.stringify({ answer }),
    });
  },
};
