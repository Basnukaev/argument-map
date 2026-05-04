import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import Button from '@/components/ui/Button';
import { apiGet, ApiError } from '@/api/client';
import type { components } from '@/api/types';

type Topic = components['schemas']['TopicResponse'];

type ViewState =
  | { kind: 'loading' }
  | { kind: 'success'; topics: Topic[] }
  | { kind: 'error'; message: string };

function TopicListPage() {
  const [state, setState] = useState<ViewState>({ kind: 'loading' });

  useEffect(() => {
    const controller = new AbortController();
    apiGet('/api/v1/topics', { signal: controller.signal })
      .then((topics) => {
        setState({ kind: 'success', topics: topics ?? [] });
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        const message =
          e instanceof ApiError
            ? `${e.problem.title}${e.problem.detail ? ': ' + e.problem.detail : ''}`
            : e instanceof Error
              ? e.message
              : 'Не удалось загрузить темы';
        setState({ kind: 'error', message });
      });
    return () => controller.abort();
  }, []);

  return (
    <main className="min-h-screen bg-gray-50 p-8">
      <div className="mx-auto max-w-5xl">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-3xl font-bold text-gray-900">Темы</h1>
          <Link to="/topics/new">
            <Button>Создать тему</Button>
          </Link>
        </div>

        {state.kind === 'loading' && <p className="text-gray-500">Загрузка</p>}

        {state.kind === 'error' && (
          <div className="rounded-md border border-red-300 bg-red-50 p-4 text-red-800">
            <p className="font-medium">Ошибка</p>
            <p className="mt-1 text-sm">{state.message}</p>
          </div>
        )}

        {state.kind === 'success' && state.topics.length === 0 && (
          <p className="text-gray-600">Пока нет тем. Создай первую</p>
        )}

        {state.kind === 'success' && state.topics.length > 0 && (
          <ul className="space-y-3">
            {state.topics
              .filter((t): t is Topic & { id: string } => Boolean(t.id))
              .map((topic) => (
                <li key={topic.id}>
                  <Link
                    to={`/topics/${topic.id}`}
                    className="block rounded-md border border-gray-200 bg-white p-4 transition-colors hover:border-blue-400 hover:bg-blue-50"
                  >
                    <h2 className="text-lg font-semibold text-gray-900">
                      {topic.title ?? '(без названия)'}
                    </h2>
                    {topic.description && (
                      <p className="mt-1 text-sm text-gray-600">{topic.description}</p>
                    )}
                    <p className="mt-2 text-xs text-gray-500">
                      {topic.createdAt
                        ? new Date(topic.createdAt).toLocaleString('ru-RU')
                        : ''}
                    </p>
                  </Link>
                </li>
              ))}
          </ul>
        )}
      </div>
    </main>
  );
}

export default TopicListPage;
