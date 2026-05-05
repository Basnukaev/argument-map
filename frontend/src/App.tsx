import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router';
import TopicListPage from '@/pages/TopicListPage';
import CreateTopicPage from '@/pages/CreateTopicPage';
import Toaster from '@/components/ui/Toaster';

// TopicGraphPage тянет тяжёлые зависимости (React Flow, dagre, lucide-icons,
// все компоненты графа). Loading через React.lazy выкидывает их из initial
// bundle - страницы списка/создания темы загружаются быстрее, граф
// подгружается только при переходе на /topics/{id}
const TopicGraphPage = lazy(() => import('@/pages/TopicGraphPage'));

function GraphFallback() {
  return (
    <div className="flex h-screen items-center justify-center text-gray-500">
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
      </Routes>
      <Toaster />
    </>
  );
}

export default App;
