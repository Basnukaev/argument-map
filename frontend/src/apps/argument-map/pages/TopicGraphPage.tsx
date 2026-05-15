import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ArrowLeft, AlertCircle, Loader2 } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import GraphCanvas from '@/apps/argument-map/components/graph/GraphCanvas';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import { useT } from '@/shared/i18n';
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
  const t = useT();
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
              : t('common.error');
        setState({ kind: 'error', message });
      });
    return () => controller.abort();
  }, [topicId, refreshKey, t]);

  const fallbackTopicTitle = t('nav.topics');
  const topicTitle =
    state.kind === 'success' ? (state.data.topic?.title ?? fallbackTopicTitle) : fallbackTopicTitle;
  const topicDescription =
    state.kind === 'success' ? state.data.topic?.description : undefined;

  return (
    <div className="flex h-screen flex-col bg-bg">
      <Header />
      {/* Secondary crumb-bar под глобальным AppHeader: "К списку / Тема / описание".
          Per design-reference TopicGraphPage v3 - граф наследует AppHeader как
          и остальные страницы, и плюс свой локальный crumb для контекста темы */}
      <div className="flex flex-none items-center gap-3 border-b border-border bg-elevated px-4 py-2">
        <Link
          to="/topics"
          aria-label={t('graph.back_to_list')}
          className="inline-flex h-7 items-center gap-1.5 rounded-sm px-2 text-xs font-medium text-ink-700 transition-colors hover:bg-ink-100 hover:text-ink-900"
        >
          <ArrowLeft size={13} aria-hidden />
          {t('graph.back_to_list')}
        </Link>
        <span className="text-ink-300">/</span>
        <h1
          dir="auto"
          className="truncate text-sm font-semibold text-ink-900"
          title={topicDescription || topicTitle}
        >
          {topicTitle}
        </h1>
        {topicDescription && (
          <p dir="auto" className="hidden truncate text-xs text-ink-500 md:block">
            {topicDescription}
          </p>
        )}
      </div>

      <main className="relative flex-1 overflow-hidden">
        {state.kind === 'loading' && (
          <div className="absolute inset-0 flex items-center justify-center gap-2 text-sm text-ink-500">
            <Loader2 size={16} className="animate-spin" aria-hidden />
            {t('common.loading')}
          </div>
        )}

        {state.kind === 'error' && (
          <div className="absolute inset-0 flex items-center justify-center p-8">
            <Card className="max-w-lg p-5 border-err-500/40 bg-err-100">
              <div className="flex items-start gap-3">
                <AlertCircle size={20} className="mt-0.5 shrink-0 text-err-700" aria-hidden />
                <div>
                  <p className="font-semibold text-err-700">{t('common.error')}</p>
                  <p className="mt-1 text-sm text-err-700">{state.message}</p>
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
