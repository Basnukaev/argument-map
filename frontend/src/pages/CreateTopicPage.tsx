import { Link } from 'react-router';
import Button from '@/components/ui/Button';

function CreateTopicPage() {
  return (
    <main className="min-h-screen bg-gray-50 p-8">
      <div className="mx-auto max-w-2xl">
        <h1 className="mb-6 text-3xl font-bold text-gray-900">Создание темы</h1>
        <p className="mb-6 text-gray-600">Форма создания темы (заглушка)</p>
        <Link to="/topics">
          <Button variant="secondary">Назад к списку</Button>
        </Link>
      </div>
    </main>
  );
}

export default CreateTopicPage;
