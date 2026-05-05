import { Routes, Route, Navigate } from 'react-router';
import TopicListPage from '@/pages/TopicListPage';
import CreateTopicPage from '@/pages/CreateTopicPage';
import TopicGraphPage from '@/pages/TopicGraphPage';
import Toaster from '@/components/ui/Toaster';

function App() {
  return (
    <>
      <Routes>
        <Route path="/" element={<Navigate to="/topics" replace />} />
        <Route path="/topics" element={<TopicListPage />} />
        <Route path="/topics/new" element={<CreateTopicPage />} />
        <Route path="/topics/:topicId" element={<TopicGraphPage />} />
      </Routes>
      <Toaster />
    </>
  );
}

export default App;
