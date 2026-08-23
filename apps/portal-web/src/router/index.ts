import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('@/views/HomePage.vue') },
    { path: '/login', component: () => import('@/features/identity/LoginPage.vue') },
    { path: '/register', component: () => import('@/features/identity/RegisterPage.vue') },
    { path: '/questions', component: () => import('@/features/catalog/QuestionListPage.vue') },
    { path: '/questions/:questionId', component: () => import('@/features/catalog/QuestionDetailPage.vue') },
    { path: '/interviews/new', component: () => import('@/features/interview/InterviewSetupPage.vue'), meta: { requiresSession: true } },
    { path: '/interviews/:interviewId', component: () => import('@/features/interview/InterviewRoomPage.vue'), meta: { requiresSession: true } },
    { path: '/account', component: () => import('@/views/AccountPage.vue'), meta: { requiresSession: true } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  if (!to.meta.requiresSession) return true
  const { useSessionStore } = await import('@/stores/session')
  const session = useSessionStore()
  await session.ensureLoaded()
  return session.account ? true : { path: '/login', query: { redirect: to.fullPath } }
})

export default router
