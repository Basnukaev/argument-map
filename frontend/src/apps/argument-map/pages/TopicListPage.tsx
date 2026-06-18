import { useCallback, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import {
  Network,
  Plus,
  Calendar,
  AlertCircle,
  Loader2,
  Download,
  Upload,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import ListToolbar from '@/shared/components/ui/ListToolbar';
import SearchInput from '@/shared/components/ui/SearchInput';
import SortSelect from '@/shared/components/ui/SortSelect';
import Pagination from '@/shared/components/ui/Pagination';
import {
  apiGetRaw,
  apiPostMultipart,
  API_BASE_URL,
  ApiError,
  formatApiError,
} from '@/shared/api/client';
import { usePagedList } from '@/shared/hooks/usePagedList';
import { useT, useFormatDate } from '@/shared/i18n';
import { useIsAuthenticated } from '@/shared/stores/authStore';
import { toast } from '@/shared/stores/toastStore';
import type { components } from '@/shared/api/types';
import VisibilityBadge from '@/apps/argument-map/components/VisibilityBadge';
import VoteWidget from '@/shared/components/ui/VoteWidget';

type Topic = components['schemas']['TopicResponse'];
type TopicImportResponse = components['schemas']['TopicImportResponse'];
type SortKey = 'recent' | 'popular' | 'alphabetical';

const PAGE_SIZE = 20;

function TopicListPage() {
  const t = useT();
  const navigate = useNavigate();
  // Guest view (roadmap 49.G): аноним видит каталог read-only, write-CTA
  // (создать тему, импорт) скрыты - вход через «Войти» в хедере.
  const isAuthenticated = useIsAuthenticated();
  const fileInputRef = useRef<HTMLInputElement>(null);
  // Поиск client-side по уже загруженной странице: бэк /api/v1/topics не
  // поддерживает ?q= (см. api-contract). Отдельное state, НЕ через
  // usePagedList.searchInput — иначе ввод сбрасывал бы на стр.1 (hook
  // рефетчит при смене debouncedQuery). При активном поиске пагинация
  // скрыта (фильтрация одной страницы — листать смысла нет; server-side
  // ?q= для тем — backlog).
  const [search, setSearch] = useState('');
  const [importBusy, setImportBusy] = useState(false);
  // Vision 49d Section 2.1 - sort через server-side ?sort= param
  const [sort, setSort] = useState<SortKey>('recent');
  // Bump'ается после импорта темы - принудительный refetch page 0 (hook
  // не экспонирует refetch, deps-смена = единственный способ).
  const [refreshKey, setRefreshKey] = useState(0);

  const buildUrl = useCallback(
    (page: number): string =>
      `/api/v1/topics?page=${page}&size=${PAGE_SIZE}&sort=${sort}`,
    [sort],
  );

  const { state, page, goToPage } = usePagedList<Topic>({
    buildUrl,
    deps: [sort, refreshKey],
    fallbackError: 'Не удалось загрузить темы',
  });

  /**
   * Trigger native file picker. `<input type="file">` стилизуется плохо -
   * храним hidden и click'аем программно из обычной кнопки
   */
  const triggerFilePicker = () => {
    fileInputRef.current?.click();
  };

  /**
   * Прочитать выбранный файл и POST'ом multipart отправить на /import.
   * При успехе - toast.success с кнопкой "Открыть" → navigate на новую
   * тему. Warnings из ответа показываются как отдельный toast.warning
   */
  const handleFileSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    // reset value сразу - чтобы повторный выбор того же файла триггерил
    // onChange (browser optimizes away identical selection)
    e.target.value = '';
    if (!file) return;

    setImportBusy(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const response = await apiPostMultipart<TopicImportResponse>(
        '/api/v1/topics/import',
        formData,
      );
      if (response.warnings && response.warnings.length > 0) {
        toast.warning(t('topic.import.warning_no_book'));
      }
      toast.success(t('topic.import.success'), {
        label: t('topic.import.open'),
        onClick: () => navigate(`/topics/${response.topicId}`),
      });
      // refetch topic list - новая тема должна появиться в каталоге.
      // Bump refreshKey (в deps usePagedList) → hook рефетчит текущую страницу.
      setRefreshKey((k) => k + 1);
    } catch (err: unknown) {
      if (err instanceof ApiError && err.is('unsupported-format-version')) {
        toast.error(t('topic.import.error_format'));
      } else {
        toast.error(formatApiError(err, t('topic.import.failed')));
      }
    } finally {
      setImportBusy(false);
    }
  };

  const filteredTopics = useMemo(() => {
    if (state.kind !== 'success') return [];
    if (!search.trim()) return state.data.items;
    const q = search.trim().toLowerCase();
    return state.data.items.filter(
      (t) =>
        (t.title ?? '').toLowerCase().includes(q) ||
        (t.description ?? '').toLowerCase().includes(q),
    );
  }, [state, search]);

  const sortOptions = useMemo(
    () => [
      { value: 'recent', label: t('common.sort.recent') },
      { value: 'popular', label: t('common.sort.popular') },
      { value: 'alphabetical', label: t('common.sort.alphabetical') },
    ],
    [t],
  );

  return (
    <main className="min-h-screen bg-bg">
      <Header />

      <div className="mx-auto max-w-[1380px] px-3 py-6 sm:px-6 sm:py-8">
        <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
          <div className="min-w-0 flex-1">
            <div className="mb-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-500">
              {t('topic.list.eyebrow')}
            </div>
            <h1 className="font-serif text-[28px] font-semibold leading-tight tracking-tight text-ink-900">
              {t('topic.list.title')}
            </h1>
            <p className="mt-1.5 max-w-[680px] text-sm text-ink-500">
              {t('topic.list.subtitle_active')}
              {state.kind === 'success' && (
                <>
                  {' '}·{' '}
                  <span className="font-medium text-ink-700">
                    <bdi dir="ltr">{state.data.totalElements}</bdi>{' '}
                    {t('topic.list.aria_topic_count')}
                  </span>
                </>
              )}
            </p>
          </div>
          {isAuthenticated && (
            <div className="flex flex-wrap items-center gap-2">
              <Button
                icon={Upload}
                variant="ghost"
                onClick={triggerFilePicker}
                disabled={importBusy}
              >
                {t('topic.import.button')}
              </Button>
              <Link to="/topics/new">
                <Button icon={Plus}>{t('topic.list.create_button')}</Button>
              </Link>
              <input
                ref={fileInputRef}
                type="file"
                accept="application/json,.json"
                onChange={handleFileSelected}
                className="hidden"
                aria-hidden
              />
            </div>
          )}
        </header>

        <ListToolbar
          className="mb-6"
          search={
            <SearchInput
              value={search}
              onChange={setSearch}
              placeholder={t('topic.list.search_placeholder')}
              ariaLabel={t('common.search')}
              className="w-full"
            />
          }
          sort={
            <SortSelect
              value={sort}
              onChange={(v) => setSort(v as SortKey)}
              options={sortOptions}
            />
          }
        />

        {state.kind === 'loading' && (
          <div className="flex items-center justify-center gap-2 py-20 text-sm text-ink-500">
            <Loader2 size={16} className="animate-spin" aria-hidden />
            {t('common.loading')}
          </div>
        )}

        {state.kind === 'error' && (
          <Card className="mx-auto max-w-2xl p-5 border-err-500/40 bg-err-100">
            <div className="flex items-start gap-3">
              <AlertCircle
                size={20}
                className="mt-0.5 shrink-0 text-err-700"
                aria-hidden
              />
              <div>
                <p className="font-semibold text-err-700">{t('common.error')}</p>
                <p className="mt-1 text-sm text-err-700">{state.message}</p>
              </div>
            </div>
          </Card>
        )}

        {state.kind === 'success' && state.data.items.length === 0 && (
          <Card className="mx-auto max-w-2xl p-12 text-center">
            <p className="text-base text-ink-700">{t('topic.list.empty')}</p>
            {isAuthenticated && (
              <Link to="/topics/new" className="mt-4 inline-block">
                <Button icon={Plus}>{t('topic.list.create_button')}</Button>
              </Link>
            )}
          </Card>
        )}

        {state.kind === 'success' &&
          state.data.items.length > 0 &&
          filteredTopics.length === 0 && (
            <p className="text-center text-sm text-ink-500">
              {t('topic.list.not_found')}
            </p>
          )}

        {state.kind === 'success' && filteredTopics.length > 0 && (
          <>
            <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {filteredTopics
                .filter((t): t is Topic & { id: string } => Boolean(t.id))
                .map((topic) => (
                  <li key={topic.id}>
                    <TopicCard topic={topic} />
                  </li>
                ))}
            </ul>

            {/*
              Пагинация скрыта при активном client-side search: листать
              страницы пока выборка отфильтрована по одной странице смысла
              нет (server-side ?q= для тем — backlog).
            */}
            {!search.trim() && (
              <Pagination
                page={page}
                totalPages={state.data.totalPages}
                totalElements={state.data.totalElements}
                pageSize={PAGE_SIZE}
                onPageChange={goToPage}
              />
            )}
          </>
        )}
      </div>
    </main>
  );
}

interface TopicCardProps {
  topic: Topic & { id: string };
}

function TopicCard({ topic }: TopicCardProps) {
  const t = useT();
  const formatDate = useFormatDate();
  const nodeCount = topic.nodeCount ?? 0;
  const edgeCount = topic.edgeCount ?? 0;
  const date = formatDate(topic.createdAt, 'short');
  const fallbackTitle = t('reader.no_book_title');
  const title = topic.title ?? fallbackTitle;

  /**
   * Скачать тему через прямой fetch с absolute URL + создать ObjectURL +
   * программный клик по `<a download>` (стандартный браузерный паттерн).
   * stopPropagation на event - чтобы не сработал navigate из обёрточного
   * `<Link>`
   */
  const handleExport = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    try {
      const dto = await apiGetRaw<unknown>(`/api/v1/topics/${topic.id}/export`);
      const blob = new Blob([JSON.stringify(dto, null, 2)], {
        type: 'application/json',
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `topic-${topic.id.slice(0, 8)}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      // освобождение памяти - после клика, без задержки blob может
      // не успеть открыться в некоторых браузерах. setTimeout 0ms даёт
      // event loop отработать download trigger
      setTimeout(() => URL.revokeObjectURL(url), 0);
    } catch (err: unknown) {
      toast.error(formatApiError(err, t('topic.export.failed')));
    }
    // explicitly mark unused - URL.createObjectURL не используется
    // вне этой функции. API_BASE_URL для будущих direct-URL fetch'ей
    void API_BASE_URL;
  };

  return (
    <Link
      to={`/topics/${topic.id}`}
      aria-label={title}
      className="group block focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 focus-visible:ring-offset-2 focus-visible:ring-offset-bg rounded-md"
    >
      <Card interactive className="overflow-hidden">
        <div className="relative h-[110px] border-b border-border bg-bg">
          <TopicMiniGraph nodeCount={nodeCount} edgeCount={edgeCount} />
          <div className="absolute end-2 top-2 inline-flex h-5 items-center gap-1 rounded-sm border border-border bg-elevated/90 px-1.5 text-xs font-medium text-ink-600 backdrop-blur">
            <Network size={10} aria-hidden />
            {nodeCount} · {edgeCount}
          </div>
          <button
            type="button"
            onClick={handleExport}
            title={t('topic.export.button')}
            aria-label={t('topic.export.button')}
            className="absolute start-2 top-2 inline-flex h-6 w-6 items-center justify-center rounded-sm border border-border bg-elevated/90 text-ink-600 opacity-0 transition-opacity hover:bg-ink-50 hover:text-ink-900 focus:opacity-100 group-hover:opacity-100 backdrop-blur"
          >
            <Download size={12} aria-hidden />
          </button>
        </div>
        <Card.Body>
          <div className="flex items-start justify-between gap-2">
            <h2
              dir="auto"
              className="line-clamp-2 flex-1 text-sm font-semibold leading-snug text-ink-900 transition-colors group-hover:text-accent-700"
            >
              {title}
            </h2>
            <VisibilityBadge
              visibility={topic.visibility}
              compact
              className="mt-0.5 shrink-0"
            />
          </div>
          {topic.description && (
            <p
              dir="auto"
              className="mt-1.5 line-clamp-2 text-xs leading-relaxed text-ink-500"
            >
              {topic.description}
            </p>
          )}
          <div className="mt-3 flex items-center justify-between gap-2 text-xs text-ink-500">
            <span className="font-mono">
              <bdi dir="ltr">{topic.id.slice(0, 8)}</bdi>
            </span>
            {date && (
              <span className="inline-flex items-center gap-1">
                <Calendar size={11} aria-hidden />
                <bdi>{date}</bdi>
              </span>
            )}
          </div>
          {/* Голосование за тему. stopPropagation - карточка обёрнута в Link,
              клик по кнопкам не должен триггерить navigate. e.preventDefault
              в самом widget'е не нужен: stopPropagation на onClick блокирует
              всплытие до Link. */}
          <div className="mt-2 flex justify-end" onClick={(e) => e.preventDefault()}>
            <VoteWidget
              voteUrl={`/api/v1/topics/${topic.id}/vote`}
              score={topic.voteScore ?? 0}
              userVote={topic.userVote ?? null}
              stopPropagation
              ariaLabel={t('vote.topic.aria_widget')}
              upvoteLabel={t('vote.topic.upvote_tooltip')}
              downvoteLabel={t('vote.topic.downvote_tooltip')}
            />
          </div>
        </Card.Body>
      </Card>
    </Link>
  );
}

interface MiniGraphProps {
  nodeCount: number;
  edgeCount: number;
}

/**
 * Декоративный мини-граф для карточки темы. Не отражает реальную структуру,
 * визуальный акцент типа "это граф". Цвета через CSS-переменные -
 * автоматически переключаются в dark theme.
 */
function TopicMiniGraph({ nodeCount, edgeCount: _edgeCount }: MiniGraphProps) {
  const dots = [
    { x: 60, y: 18, c: 'var(--c-accent-600)', r: 5 },
    { x: 28, y: 50, c: 'var(--c-ok-500)', r: 4 },
    { x: 60, y: 64, c: 'var(--c-ok-500)', r: 4.5 },
    { x: 96, y: 50, c: 'var(--c-ok-500)', r: 4 },
    { x: 14, y: 80, c: 'var(--c-ink-300)', r: 3 },
    { x: 44, y: 86, c: 'var(--c-ink-300)', r: 3 },
    { x: 78, y: 86, c: 'var(--c-ink-300)', r: 3 },
    { x: 108, y: 80, c: 'var(--c-ink-300)', r: 3 },
  ];
  const lines: ReadonlyArray<readonly [number, number]> = [
    [0, 1],
    [0, 2],
    [0, 3],
    [1, 4],
    [2, 5],
    [3, 6],
    [3, 7],
  ];

  const visibleDots = Math.max(1, Math.min(dots.length, nodeCount));

  return (
    <svg viewBox="0 0 124 100" className="h-full w-full" aria-hidden="true">
      {lines.map(([a, b], i) => {
        if (a >= visibleDots || b >= visibleDots) return null;
        const da = dots[a]!;
        const db = dots[b]!;
        return (
          <line
            key={i}
            x1={da.x}
            y1={da.y}
            x2={db.x}
            y2={db.y}
            stroke="var(--c-ink-300)"
            strokeWidth="1"
            strokeOpacity="0.7"
          />
        );
      })}
      {dots.slice(0, visibleDots).map((n, i) => (
        <circle
          key={i}
          cx={n.x}
          cy={n.y}
          r={n.r}
          fill={n.c}
          stroke="var(--c-bg-elevated)"
          strokeWidth="1.5"
        />
      ))}
    </svg>
  );
}

export default TopicListPage;
