import { createBrowserRouter } from 'react-router-dom';
import { RootLayout } from './layout/RootLayout';
import { NotFoundPage, RootErrorPage } from './errors/RootErrorPage';
import { RequireSession } from '../shared/session/RequireSession';
import { HomePage } from '../features/landing/pages/HomePage';
import { LoginPage, RecoverPage, RegisterPage } from '../features/identity/pages/AuthPages';
import { AccountPage } from '../features/identity/pages/AccountPage';
import { DashboardPage } from '../features/dashboard/pages/DashboardPage';
import { QuestionDetailPage, QuestionListPage } from '../features/catalog/pages/CatalogPages';
import { PracticeHistoryPage, PracticePage } from '../features/practice/pages/PracticePage';
import { InterviewPlanPage, InterviewRoomPage, InterviewSetupPage } from '../features/interview/pages/InterviewPages';
import { InterviewReportRoutePage, ReportPage } from '../features/evaluation/pages/ReportPage';
import { LearningPage, LearningPlanPage, ReportLearningPlanPage } from '../features/learning/pages/LearningPage';
import { DeletionRequestPage, ExportRequestPage, PrivacyPage } from '../features/privacy/pages/PrivacyPage';
import { BillingPage, OrderStatusPage, PricingPage, UsagePage } from '../features/billing/pages/BillingPage';
import { AdminAuditPage, AdminCatalogPage, AdminOperationsPage, AdminPage, AdminQuestionPage } from '../features/admin/pages/AdminPage';
import { StatusPage } from '../features/status/pages/StatusPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
    errorElement: <RootErrorPage />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'auth/login', element: <LoginPage /> },
      { path: 'auth/register', element: <RegisterPage /> },
      { path: 'auth/recover', element: <RecoverPage /> },
      { path: 'questions', element: <QuestionListPage /> },
      { path: 'questions/:questionId', element: <QuestionDetailPage /> },
      { path: 'pricing', element: <PricingPage /> },
      { path: 'status', element: <StatusPage /> },
      {
        element: <RequireSession />,
        children: [
          { path: 'app', element: <DashboardPage /> },
          { path: 'app/questions/:questionId/practice', element: <PracticePage /> },
          { path: 'app/practice', element: <PracticeHistoryPage /> },
          { path: 'app/practice/:questionId', element: <PracticePage /> },
          { path: 'app/interviews/new', element: <InterviewSetupPage /> },
          { path: 'app/interview-plans/:planId', element: <InterviewPlanPage /> },
          { path: 'app/interviews/:interviewId', element: <InterviewRoomPage /> },
          { path: 'app/reports/:reportId', element: <ReportPage /> },
          { path: 'app/interviews/:interviewId/report', element: <InterviewReportRoutePage /> },
          { path: 'app/learning', element: <LearningPage /> },
          { path: 'app/learning/plans/:planId', element: <LearningPlanPage /> },
          { path: 'app/learning/reports/:reportId', element: <ReportLearningPlanPage /> },
          { path: 'app/usage', element: <UsagePage /> },
          { path: 'app/billing/orders/:orderId', element: <OrderStatusPage /> },
          { path: 'app/billing/*', element: <BillingPage /> },
          { path: 'app/settings/account', element: <AccountPage /> },
          { path: 'app/settings/privacy/requests/:requestId', element: <DeletionRequestPage /> },
          { path: 'app/settings/privacy/exports/:requestId', element: <ExportRequestPage /> },
          { path: 'app/settings/privacy', element: <PrivacyPage /> },
          { path: 'admin', element: <AdminPage /> },
          { path: 'admin/catalog', element: <AdminCatalogPage /> },
          { path: 'admin/catalog/:questionId', element: <AdminQuestionPage /> },
          { path: 'admin/operations/:view', element: <AdminOperationsPage /> },
          { path: 'admin/audit', element: <AdminAuditPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
