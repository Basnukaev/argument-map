import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router';
import TopicListPage from '@/apps/argument-map/pages/TopicListPage';
import CreateTopicPage from '@/apps/argument-map/pages/CreateTopicPage';
import BookListPage from '@/apps/library/pages/BookListPage';
import BookReaderPage from '@/apps/library/pages/BookReaderPage';
import AdminShamelaPage from '@/apps/admin/pages/AdminShamelaPage';
import Toaster from '@/shared/components/ui/Toaster';

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
        <Route path="/admin/shamela" element={<AdminShamelaPage />} />
      </Routes>
      <Toaster />
    </>
  );
}

export default App;
