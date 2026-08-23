export type PrincipalQueryScope = readonly ['scope', number, string, string];

export const queryKeys = {
  session: {
    current: ['session', 'current'] as const,
  },
  catalog: {
    questions: (scope: PrincipalQueryScope, filters: string) => [...scope, 'catalog', 'questions', filters] as const,
    question: (scope: PrincipalQueryScope, id: string) => [...scope, 'catalog', 'question', id] as const,
  },
  practice: {
    history: (scope: PrincipalQueryScope) => [...scope, 'practice', 'history'] as const,
  },
  interview: {
    plan: (scope: PrincipalQueryScope, id: string) => [...scope, 'interview', 'plan', id] as const,
    snapshot: (scope: PrincipalQueryScope, id: string) => [...scope, 'interview', 'snapshot', id] as const,
  },
  evaluation: {
    evaluation: (scope: PrincipalQueryScope, id: string) => [...scope, 'evaluation', id] as const,
    report: (scope: PrincipalQueryScope, id: string) => [...scope, 'report', id] as const,
    interviewReport: (scope: PrincipalQueryScope, interviewId: string) => [...scope, 'interview', interviewId, 'report'] as const,
  },
  learning: {
    dashboard: (scope: PrincipalQueryScope) => [...scope, 'learning', 'dashboard'] as const,
    plans: (scope: PrincipalQueryScope) => [...scope, 'learning', 'plans'] as const,
    plan: (scope: PrincipalQueryScope, id: string) => [...scope, 'learning', 'plan', id] as const,
  },
  billing: {
    plans: (scope: PrincipalQueryScope) => [...scope, 'billing', 'plans'] as const,
    entitlements: (scope: PrincipalQueryScope) => [...scope, 'billing', 'entitlements'] as const,
    usage: (scope: PrincipalQueryScope) => [...scope, 'billing', 'usage'] as const,
    order: (scope: PrincipalQueryScope, id: string) => [...scope, 'billing', 'order', id] as const,
  },
  privacy: {
    policies: ['governance', 'policies', 'current'] as const,
    consents: (scope: PrincipalQueryScope) => [...scope, 'governance', 'consents'] as const,
    inventory: (scope: PrincipalQueryScope) => [...scope, 'privacy', 'inventory'] as const,
    export: (scope: PrincipalQueryScope, id: string) => [...scope, 'privacy', 'export', id] as const,
    deletion: (scope: PrincipalQueryScope, id: string) => [...scope, 'privacy', 'deletion', id] as const,
  },
  operations: {
    status: ['operations', 'status'] as const,
    health: ['operations', 'health'] as const,
    projection: (scope: PrincipalQueryScope, view: string) => [...scope, 'operations', view] as const,
    adminQuestions: (scope: PrincipalQueryScope) => [...scope, 'admin', 'catalog', 'questions'] as const,
    adminQuestion: (scope: PrincipalQueryScope, id: string) => [...scope, 'admin', 'catalog', 'question', id] as const,
    audit: (scope: PrincipalQueryScope) => [...scope, 'admin', 'audit'] as const,
  },
};
