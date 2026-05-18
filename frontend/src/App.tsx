import { lazy, Suspense, useEffect } from 'react';
import { Routes, Route, Navigate } from 'react-router';
import TopicListPage from '@/apps/argument-map/pages/TopicListPage';
import CreateTopicPage from '@/apps/argument-map/pages/CreateTopicPage';
import BookListPage from '@/apps/library/pages/BookListPage';
import BookReaderPage from '@/apps/library/pages/BookReaderPage';
import AdminShamelaPage from '@/apps/admin/pages/AdminShamelaPage';
import AdminPageEditorPage from '@/apps/admin/pages/AdminPageEditorPage';
import AdminAuditPage from '@/apps/admin/pages/AdminAuditPage';
import QuestionListPage from '@/apps/qa/pages/QuestionListPage';
import CreateQuestionPage from '@/apps/qa/pages/CreateQuestionPage';
import QuestionDetailPage from '@/apps/qa/pages/QuestionDetailPage';
import SettingsPage from '@/apps/settings/pages/SettingsPage';
import LoginPage from '@/apps/auth/pages/LoginPage';
import RegisterPage from '@/apps/auth/pages/RegisterPage';
import ProtectedRoute from '@/shared/components/auth/ProtectedRoute';
import Toaster from '@/shared/components/ui/Toaster';
import CommandPalette from '@/shared/components/layout/CommandPalette';
import { usePaletteStore } from '@/shared/stores/paletteStore';
import { useHotkey } from '@/shared/hooks/useHotkey';
import { useAuthStore } from '@/shared/stores/authStore';

// TopicGraphPage тянет тяжёлые зависимости (React Flow, dagre, lucide-icons,
// все компоненты графа). Loading через React.lazy выкидывает их из initial
// bundle - страницы списка/создания темы загружаются быстрее, граф
// подгружается только при переходе на /topics/{id}
const TopicGraphPage = lazy(() => import('@/apps/argument-map/pages/TopicGraphPage'));

function GraphFallback() {
  return (
    <div className="flex h-screen items-center justify-center text-ink-500">
      Загрузка графа
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

  useHotkey('alt+k', togglePalette, { enableOnFormTags: true });

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
    <>
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
              <Suspense fallback={<GraphFallback />}>
                <TopicGraphPage />
              </Suspense>
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
      <CommandPalette open={paletteOpen} onClose={closePalette} />
      <Toaster />
    </>
  );
}

export default App;
