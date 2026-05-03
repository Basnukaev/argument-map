import { Link } from 'react-router';
import Button from '@/components/ui/Button';

function TopicListPage() {
  return (
    <main className="min-h-screen bg-gray-50 p-8">
      <div className="mx-auto max-w-5xl">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-3xl font-bold text-gray-900">Темы</h1>
          <Link to="/topics/new">
            <Button>Создать тему</Button>
          </Link>
        </div>
        <p className="text-gray-600">Список тем (заглушка). Подключим к API на следующих шагах</p>
      </div>
    </main>
  );
}

export default TopicListPage;
