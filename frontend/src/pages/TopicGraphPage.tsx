import { Link, useParams } from 'react-router';
import Button from '@/components/ui/Button';

function TopicGraphPage() {
  const { topicId } = useParams<{ topicId: string }>();

  return (
    <main className="min-h-screen bg-gray-50 p-8">
      <div className="mx-auto max-w-7xl">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-3xl font-bold text-gray-900">Граф темы</h1>
          <Link to="/topics">
            <Button variant="secondary">К списку</Button>
          </Link>
        </div>
        <p className="text-gray-600">
          ID темы: <code className="rounded bg-gray-200 px-2 py-1">{topicId}</code>
        </p>
        <p className="mt-2 text-gray-600">React Flow граф появится на следующих шагах</p>
      </div>
    </main>
  );
}

export default TopicGraphPage;
