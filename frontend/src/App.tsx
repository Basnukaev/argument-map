import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router';
import TopicListPage from '@/apps/argument-map/pages/TopicListPage';
import CreateTopicPage from '@/apps/argument-map/pages/CreateTopicPage';
import BookListPage from '@/apps/library/pages/BookListPage';
import BookReaderPage from '@/apps/library/pages/BookReaderPage';
import AdminShamelaPage from '@/apps/admin/pages/AdminShamelaPage';
import AdminPageEditorPage from '@/apps/admin/pages/AdminPageEditorPage';
import QuestionListPage from '@/apps/qa/pages/QuestionListPage';
import CreateQuestionPage from '@/apps/qa/pages/CreateQuestionPage';
import QuestionDetailPage from '@/apps/qa/pages/QuestionDetailPage';
import SettingsPage from '@/apps/settings/pages/SettingsPage';
import Toaster from '@/shared/components/ui/Toaster';
import CommandPalette from '@/shared/components/layout/CommandPalette';
import { usePaletteStore } from '@/shared/stores/paletteStore';
import { useHotkey } from '@/shared/hooks/useHotkey';

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

  return (
    <>
      <Routes>
        <Route path="/" element={<Navigate to="/topics" replace />} />
        <Route path="/topics" element={<TopicListPage />} />
        <Route path="/topics/new" element={<CreateTopicPage />} />
        <Route
          path="/topics/:topicId"
          element={
            <Suspense fallback={<GraphFallback />}>
              <TopicGraphPage />
            </Suspense>
          }
        />
        <Route path="/books" element={<BookListPage />} />
        <Route path="/books/:bookId" element={<BookReaderPage />} />
        <Route path="/qa" element={<QuestionListPage />} />
        <Route path="/qa/new" element={<CreateQuestionPage />} />
        <Route path="/qa/:questionId" element={<QuestionDetailPage />} />
        <Route path="/admin/shamela" element={<AdminShamelaPage />} />
        <Route path="/admin/library/pages/:pageId/edit" element={<AdminPageEditorPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Routes>
      <CommandPalette open={paletteOpen} onClose={closePalette} />
      <Toaster />
    </>
  );
}

export default App;
