import { useCallback, useEffect, useMemo, useState } from 'react';
import { useViewTracking } from '@/shared/hooks/useViewTracking';
import { Link, useParams } from 'react-router';
import {
  ArrowLeft,
  AlertCircle,
  BookOpen,
  Loader2,
  Lock,
  Settings,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import IconButton from '@/shared/components/ui/IconButton';
import Header from '@/shared/components/layout/Header';
import GraphCanvas from '@/apps/argument-map/components/graph/GraphCanvas';
import VisibilityBadge from '@/apps/argument-map/components/VisibilityBadge';
import TopicVoteWidget from '@/apps/argument-map/components/TopicVoteWidget';
import TopicSettingsDrawer from '@/apps/argument-map/components/TopicSettingsDrawer';
import type { TopicVisibility } from '@/apps/argument-map/components/VisibilityRadioGroup';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import { hasArabicScript, useT } from '@/shared/i18n';
import { useAuthStore } from '@/shared/stores/authStore';
import type { AsyncState } from '@/shared/types/async';
import type { components } from '@/shared/api/types';

type GraphResponse = components['schemas']['GraphResponse'];
// Локальное расширение AsyncState для 404 - illustrated empty state
// вместо generic error card (см. design-system.md empty state pattern)
type TopicState = AsyncState<GraphResponse> | { kind: 'not-found' };

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
  const currentUser = useAuthStore((s) => s.user);
  const [state, setState] = useState<TopicState>({ kind: 'loading' });
  const [refreshKey, setRefreshKey] = useState(0);
  const [settingsOpen, setSettingsOpen] = useState(false);

  // Vision 49d Phase 2 — track view для popularity ranking
  useViewTracking('topics', topicId ?? null);

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
        // 404 → специальный illustrated state. Не показываем raw UUID
        // в сообщении - пользователю не нужны технические детали
        if (e instanceof ApiError && e.status === 404) {
          setState({ kind: 'not-found' });
          return;
        }
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
  const topic = state.kind === 'success' ? state.data.topic : undefined;
  const topicTitle = topic?.title ?? fallbackTopicTitle;
  const topicDescription = topic?.description;
  const visibility = (topic?.visibility ?? 'PRIVATE') as TopicVisibility;

  // Простой permission check на основании topic.createdBy. Бэк - источник
  // истины и сам бросает 403 при попытке write без прав (мы локализуем
  // через formatPermissionError). UI hint - чтобы пользователь видел read-only
  // не сделав запрос. ADMIN bypass'ит проверки на бэке - тут не отслеживаем,
  // он просто увидит кнопки и они отработают.
  const isOwner = useMemo(
    () => Boolean(currentUser && topic?.createdBy && currentUser.id === topic.createdBy),
    [currentUser, topic?.createdBy],
  );
  const isAdmin = currentUser?.role === 'ADMIN';
  // canWrite на frontend - rough estimate. EDITOR membership определяется
  // через GET /members - не вытягиваем на каждый рендер. Точная семантика
  // принадлежит бэку. Здесь только скрываем явно ненужные UI кнопки
  // для не-owner на PRIVATE темах (она не должна была загрузиться, но
  // защитный slot)
  const canWriteOptimistic = isOwner || isAdmin || visibility !== 'PRIVATE';
  const canManage = isOwner || isAdmin;

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
        {/* Title + description выравниваются по baseline между собой -
            два text-* разных размеров (sm vs xs) на items-center
            разъезжаются по вертикали (web-design feedback). Обёрнуто
            в свой flex чтобы не задеть позиционирование Link/badges
            справа (для них items-center правильнее). */}
        <div className="flex min-w-0 flex-1 items-baseline gap-3">
          {/* font-arabic для arabic titles - top bar compact, font-serif
              тут не подходит (смешает functional/editorial registers).
              hasArabicScript detect по unicode range вместо локали интерфейса */}
          <h1
            dir="auto"
            className={`truncate text-sm font-semibold text-ink-900 ${hasArabicScript(topicTitle) ? 'font-arabic' : ''}`}
            title={topicDescription || topicTitle}
          >
            {topicTitle}
          </h1>
          {topicDescription && (
            <p
              dir="auto"
              className={`hidden truncate text-xs text-ink-500 md:block ${hasArabicScript(topicDescription) ? 'font-arabic' : ''}`}
            >
              {topicDescription}
            </p>
          )}
        </div>
        {state.kind === 'success' && (
          <div className="ms-auto flex items-center gap-2">
            {topic?.id && (
              <TopicVoteWidget
                topicId={topic.id}
                score={topic.voteScore ?? 0}
                userVote={topic.userVote ?? null}
                onVoteChanged={refetch}
              />
            )}
            {!canWriteOptimistic && (
              <span
                title={t('topic.permission.read_only_hint')}
                className="inline-flex items-center gap-1 rounded-sm border border-border bg-elevated px-1.5 py-0.5 text-xs font-medium text-ink-500"
              >
                <Lock size={11} aria-hidden />
                {t('topic.permission.read_only')}
              </span>
            )}
            <VisibilityBadge visibility={visibility} />
            {canManage && (
              <IconButton
                icon={Settings}
                label={t('topic.settings.open_action')}
                size="sm"
                onClick={() => setSettingsOpen(true)}
              />
            )}
          </div>
        )}
      </div>

      <main className="relative flex-1 overflow-hidden">
        {state.kind === 'loading' && (
          <div className="absolute inset-0 flex items-center justify-center gap-2 text-sm text-ink-500">
            <Loader2 size={16} className="animate-spin" aria-hidden />
            {t('common.loading')}
          </div>
        )}

        {state.kind === 'not-found' && (
          <div className="absolute inset-0 flex items-center justify-center p-8">
            <section className="flex max-w-xl flex-col items-center gap-5 rounded-lg border border-border bg-elevated px-8 py-12 text-center">
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-accent-50 text-accent-600">
                <BookOpen size={28} aria-hidden />
              </div>
              <div>
                <h2 className="font-serif text-2xl font-semibold text-ink-900">
                  {t('topic.not_found_hero.title')}
                </h2>
                <p className="mt-2 max-w-[440px] text-sm text-ink-500">
                  {t('topic.not_found_hero.body')}
                </p>
              </div>
              <Link to="/topics">
                <Button icon={ArrowLeft}>
                  {t('topic.not_found_hero.action')}
                </Button>
              </Link>
            </section>
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
          <GraphCanvas
            graph={state.data}
            topicId={topicId}
            onRefetch={refetch}
            canWrite={canWriteOptimistic}
          />
        )}
      </main>

      {settingsOpen && topic && (
        // key включает title/description/visibility/algorithm: при refetch
        // через onChanged drawer перемонтируется и draft'ы инициализируются
        // из свежих props (см. CLAUDE.md - key-trick для reset state)
        <TopicSettingsDrawer
          key={`${topic.id}|${topic.title}|${topic.description}|${topic.visibility}|${topic.statusAlgorithm}`}
          open={settingsOpen}
          topic={topic}
          canManage={canManage}
          isAdmin={isAdmin}
          onClose={() => setSettingsOpen(false)}
          onChanged={refetch}
        />
      )}
    </div>
  );
}

export default TopicGraphPage;
