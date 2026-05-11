import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ArrowLeft, AlertCircle, Loader2 } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import GraphCanvas from '@/apps/argument-map/components/graph/GraphCanvas';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import type { AsyncState } from '@/shared/types/async';
import type { components } from '@/shared/api/types';

type GraphResponse = components['schemas']['GraphResponse'];

/**
 * Страница графа аргументации. Тонкий orchestrator: грузит граф темы,
 * показывает loading/error/header, при успехе делегирует рендеринг и
 * все взаимодействия в {@link GraphCanvas}.
 *
 * refreshKey - механизм refetch'а: GraphCanvas вызывает `onRefetch` после
 * мутаций (создание узла, удаление и т.п.), page инкрементирует ключ
 * и useEffect перетягивает свежий граф.
 */
function TopicGraphPage() {
  const { topicId } = useParams<{ topicId: string }>();
  const [state, setState] = useState<AsyncState<GraphResponse>>({ kind: 'loading' });
  const [refreshKey, setRefreshKey] = useState(0);

  const refetch = useCallback(() => setRefreshKey((k) => k + 1), []);

  useEffect(() => {
    if (!topicId) return;
    const controller = new AbortController();
    apiGetRaw<GraphResponse>(`/api/v1/topics/${topicId}/graph`, {
      signal: controller.signal,
    })
      .then((graph) => {
        setState({ kind: 'success', data: graph });
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        const message =
          e instanceof ApiError
            ? `${e.problem.title}${e.problem.detail ? ': ' + e.problem.detail : ''}`
            : e instanceof Error
              ? e.message
              : 'Не удалось загрузить граф';
        setState({ kind: 'error', message });
      });
    return () => controller.abort();
  }, [topicId, refreshKey]);

  const topicTitle =
    state.kind === 'success' ? (state.data.topic?.title ?? 'Граф темы') : 'Граф темы';
  const topicDescription =
    state.kind === 'success' ? state.data.topic?.description : undefined;

  return (
    <div className="flex h-screen flex-col bg-slate-50/60">
      <header className="flex items-center gap-3 border-b border-slate-200 bg-white px-4 py-2">
        <Link
          to="/topics"
          aria-label="К списку"
          className="inline-flex h-8 items-center gap-1.5 rounded-md px-2.5 text-[13px] font-medium text-slate-700 transition-colors hover:bg-slate-100"
        >
          <ArrowLeft size={14} aria-hidden="true" />К списку
        </Link>
        <span className="text-slate-300">/</span>
        <h1
          className="truncate text-[14px] font-semibold text-slate-900"
          title={topicDescription || topicTitle}
        >
          {topicTitle}
        </h1>
        {topicDescription && (
          <p className="hidden truncate text-[12px] text-slate-500 md:block">
            {topicDescription}
          </p>
        )}
      </header>

      <main className="relative flex-1 overflow-hidden">
        {state.kind === 'loading' && (
          <div className="absolute inset-0 flex items-center justify-center gap-2 text-[13px] text-slate-500">
            <Loader2 size={16} className="animate-spin" aria-hidden="true" />
            Загрузка графа
          </div>
        )}

        {state.kind === 'error' && (
          <div className="absolute inset-0 flex items-center justify-center p-8">
            <Card className="max-w-lg border-red-200 bg-red-50 p-5">
              <div className="flex items-start gap-3">
                <AlertCircle size={20} className="mt-0.5 shrink-0 text-red-600" aria-hidden="true" />
                <div>
                  <p className="font-semibold text-red-900">Ошибка</p>
                  <p className="mt-1 text-[13px] text-red-800">{state.message}</p>
                </div>
              </div>
            </Card>
          </div>
        )}

        {state.kind === 'success' && topicId && (
          <GraphCanvas graph={state.data} topicId={topicId} onRefetch={refetch} />
        )}
      </main>
    </div>
  );
}

export default TopicGraphPage;
