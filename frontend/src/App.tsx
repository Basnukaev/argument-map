import { lazy, Suspense, useEffect } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router';
import TopicListPage from '@/apps/argument-map/pages/TopicListPage';
import CreateTopicPage from '@/apps/argument-map/pages/CreateTopicPage';
import LoginPage from '@/apps/auth/pages/LoginPage';
import RegisterPage from '@/apps/auth/pages/RegisterPage';
import ProtectedRoute from '@/shared/components/auth/ProtectedRoute';
import Toaster from '@/shared/components/ui/Toaster';
import SourceDetailPanel from '@/shared/components/citation/SourceDetailPanel';
import OnboardingChecklist from '@/shared/components/onboarding/OnboardingChecklist';
// Lazy: только грузим chunk при первом открытии Alt+K palette
const CommandPalette = lazy(() => import('@/shared/components/layout/CommandPalette'));
import { usePaletteStore } from '@/shared/stores/paletteStore';
import { useHotkey } from '@/shared/hooks/useHotkey';
import { useAuthStore } from '@/shared/stores/authStore';
import ErrorBoundary from '@/shared/components/ErrorBoundary';

// Route-level code splitting (ADR-045). Heavy pages вынесены в отдельные
// chunks через React.lazy:
//   - TopicGraphPage  - React Flow + dagre + lucide
//   - BookListPage / BookReaderPage - react-pdf
//   - AdminPageEditorPage - Tiptap + extensions (heaviest)
//   - AdminShamelaPage / AdminAuditPage - admin-only
//   - QuestionListPage / QuestionDetailPage / CreateQuestionPage - QA app
//   - SettingsPage - settings-only
// USER на /topics не тянет admin/library/QA/reader chunks в initial bundle.
// Pages оставленные eager: TopicListPage (landing после login),
// CreateTopicPage (тоже из start flow), LoginPage / RegisterPage (public).
const TopicGraphPage = lazy(() => import('@/apps/argument-map/pages/TopicGraphPage'));
const BookListPage = lazy(() => import('@/apps/library/pages/BookListPage'));
const BookReaderPage = lazy(() => import('@/apps/library/pages/BookReaderPage'));
const AdminShamelaPage = lazy(() => import('@/apps/admin/pages/AdminShamelaPage'));
const AdminPageEditorPage = lazy(() => import('@/apps/admin/pages/AdminPageEditorPage'));
const AdminAuditPage = lazy(() => import('@/apps/admin/pages/AdminAuditPage'));
const QuestionListPage = lazy(() => import('@/apps/qa/pages/QuestionListPage'));
const CreateQuestionPage = lazy(() => import('@/apps/qa/pages/CreateQuestionPage'));
const QuestionDetailPage = lazy(() => import('@/apps/qa/pages/QuestionDetailPage'));
const SettingsPage = lazy(() => import('@/apps/settings/pages/SettingsPage'));

function PageFallback({ label = 'Загрузка' }: { label?: string }) {
  return (
    <div className="flex h-screen items-center justify-center text-ink-500">
      {label}
    </div>
  );
}

function App() {
  // Глобальный Alt+K - открыть Command Palette. Listener живёт в
  // App (а не в Header), чтобы работать на любом route, включая
  // TopicGraphPage у которого свой top-bar без AppHeader.
  //
  // Был Cmd/Ctrl+K, но Chrome на Win/Linux перехватывает Ctrl+K как
  // native accelerator (search via default engine) - даже capture +
  // preventDefault не освобождают. Alt+K чистый, не конфликтует с
  // menubar accelerators и не зарезервирован браузерами.
  //
  // `useKey: true` (default в useHotkey) использует event.code → KeyK,
  // что делает hotkey layout-independent: работает на ru/ar/en
  // раскладках одинаково (баг #2)
  const togglePalette = usePaletteStore((s) => s.toggle);
  const paletteOpen = usePaletteStore((s) => s.open);
  const closePalette = usePaletteStore((s) => s.hide);

  // Alt+K не открывает palette на auth страницах (/login, /register) -
  // там нет авторизованных команд, и palette вводила бы в заблуждение
  const { pathname } = useLocation();
  const isAuthPage = pathname === '/login' || pathname === '/register';

  useHotkey('alt+k', () => {
    if (isAuthPage) return;
    togglePalette();
  }, { enableOnFormTags: true }, [isAuthPage, togglePalette]);

  // На mount - bootstrap auth (попытка refresh + /me). Запускается один раз
  // даже в StrictMode dev double-render (initialized флаг защищает от
  // повторного запроса). После завершения - либо user в store, либо null,
  // ProtectedRoute дальше решает редирект на /login. Без этого initial
  // request - protected routes флешат на /login на refresh страницы
  const loadCurrentUser = useAuthStore((s) => s.loadCurrentUser);
  const initialized = useAuthStore((s) => s.initialized);
  useEffect(() => {
    if (!initialized) {
      void loadCurrentUser();
    }
  }, [initialized, loadCurrentUser]);

  return (
    <ErrorBoundary>
      <Suspense fallback={<PageFallback />}>
        <Routes>
          {/* Public routes - login/register не за ProtectedRoute,
              доступны всегда */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          <Route path="/" element={<Navigate to="/topics" replace />} />
          <Route
            path="/topics"
            element={
              <ProtectedRoute>
                <TopicListPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/topics/new"
            element={
              <ProtectedRoute>
                <CreateTopicPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/topics/:topicId"
            element={
              <ProtectedRoute>
                <TopicGraphPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/books"
            element={
              <ProtectedRoute>
                <BookListPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/books/:bookId"
            element={
              <ProtectedRoute>
                <BookReaderPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/qa"
            element={
              <ProtectedRoute>
                <QuestionListPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/qa/new"
            element={
              <ProtectedRoute>
                <CreateQuestionPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/qa/:questionId"
            element={
              <ProtectedRoute>
                <QuestionDetailPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/shamela"
            element={
              <ProtectedRoute requireRole="ADMIN">
                <AdminShamelaPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/library/pages/:pageId/edit"
            element={
              <ProtectedRoute requireRole="ADMIN">
                <AdminPageEditorPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/audit"
            element={
              <ProtectedRoute requireRole="ADMIN">
                <AdminAuditPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/settings"
            element={
              <ProtectedRoute>
                <SettingsPage />
              </ProtectedRoute>
            }
          />
        </Routes>
      </Suspense>
      {paletteOpen && (
        <Suspense fallback={null}>
          <CommandPalette open={paletteOpen} onClose={closePalette} />
        </Suspense>
      )}
      <SourceDetailPanel />
      <OnboardingChecklist />
      <Toaster />
    </ErrorBoundary>
  );
}

export default App;
