import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import {
  Network,
  Plus,
  Search,
  Calendar,
  AlertCircle,
  Loader2,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import { apiGet, ApiError } from '@/shared/api/client';
import type { AsyncState } from '@/shared/types/async';
import type { components } from '@/shared/api/types';

type Topic = components['schemas']['TopicResponse'];

const DATE_FORMAT = new Intl.DateTimeFormat('ru-RU', {
  day: 'numeric',
  month: 'short',
});

function formatShortDate(iso?: string): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return DATE_FORMAT.format(d);
}

function TopicListPage() {
  const [state, setState] = useState<AsyncState<Topic[]>>({ kind: 'loading' });
  const [search, setSearch] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    apiGet('/api/v1/topics', { signal: controller.signal })
      .then((topics) => {
        setState({ kind: 'success', data: (topics ?? []) as Topic[] });
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

  const filteredTopics = useMemo(() => {
    if (state.kind !== 'success') return [];
    if (!search.trim()) return state.data;
    const q = search.trim().toLowerCase();
    return state.data.filter(
      (t) =>
        (t.title ?? '').toLowerCase().includes(q) ||
        (t.description ?? '').toLowerCase().includes(q),
    );
  }, [state, search]);

  return (
    <main className="min-h-screen bg-slate-50/60">
      <Header />

      <div className="mx-auto max-w-[1380px] px-6 py-8">
        <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="text-[28px] font-bold tracking-tight text-slate-900">
              Темы аргументации
            </h1>
            {state.kind === 'success' && (
              <p className="mt-1 text-[13px] text-slate-500">
                Структурированные дискуссии в виде графа ·{' '}
                <span className="font-mono font-semibold text-slate-700">
                  {state.data.length} актив{state.data.length === 1 ? 'ная' : 'ных'}
                </span>
              </p>
            )}
          </div>
          <Link to="/topics/new">
            <Button icon={Plus}>Создать тему</Button>
          </Link>
        </div>

        <div className="mb-6 flex items-center gap-3">
          <div className="flex h-9 max-w-md flex-1 items-center rounded-md border border-slate-300 bg-white transition-colors focus-within:border-indigo-500 focus-within:ring-2 focus-within:ring-indigo-500/20">
            <Search size={16} className="ml-3 text-slate-400" aria-hidden="true" />
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Поиск по теме или описанию"
              className="flex-1 bg-transparent px-3 text-[13px] text-slate-900 outline-none placeholder:text-slate-400"
              aria-label="Поиск тем"
            />
          </div>
        </div>

        {state.kind === 'loading' && (
          <div className="flex items-center justify-center gap-2 py-20 text-[13px] text-slate-500">
            <Loader2 size={16} className="animate-spin" aria-hidden="true" />
            Загрузка
          </div>
        )}

        {state.kind === 'error' && (
          <Card className="mx-auto max-w-2xl border-red-200 bg-red-50 p-5">
            <div className="flex items-start gap-3">
              <AlertCircle size={20} className="mt-0.5 shrink-0 text-red-600" aria-hidden="true" />
              <div>
                <p className="font-semibold text-red-900">Ошибка</p>
                <p className="mt-1 text-[13px] text-red-800">{state.message}</p>
              </div>
            </div>
          </Card>
        )}

        {state.kind === 'success' && state.data.length === 0 && (
          <Card className="mx-auto max-w-2xl p-12 text-center">
            <p className="text-[15px] text-slate-700">Пока нет тем. Создай первую</p>
            <Link to="/topics/new" className="mt-4 inline-block">
              <Button icon={Plus}>Создать тему</Button>
            </Link>
          </Card>
        )}

        {state.kind === 'success' && state.data.length > 0 && filteredTopics.length === 0 && (
          <p className="text-center text-[13px] text-slate-500">
            Ничего не найдено по запросу "{search}"
          </p>
        )}

        {state.kind === 'success' && filteredTopics.length > 0 && (
          <ul className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {filteredTopics
              .filter((t): t is Topic & { id: string } => Boolean(t.id))
              .map((topic) => (
                <li key={topic.id}>
                  <TopicCard topic={topic} />
                </li>
              ))}
          </ul>
        )}
      </div>
    </main>
  );
}

interface TopicCardProps {
  topic: Topic & { id: string };
}

function TopicCard({ topic }: TopicCardProps) {
  const nodeCount = topic.nodeCount ?? 0;
  const edgeCount = topic.edgeCount ?? 0;
  const date = formatShortDate(topic.createdAt);

  return (
    <Link
      to={`/topics/${topic.id}`}
      aria-label={topic.title ?? '(без названия)'}
      className="group block focus:outline-none"
    >
      <Card className="overflow-hidden transition-all hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-md group-focus-visible:ring-2 group-focus-visible:ring-indigo-500 group-focus-visible:ring-offset-2">
        <div className="relative h-[110px] border-b border-slate-100 bg-gradient-to-br from-slate-50 to-white">
          <TopicMiniGraph nodeCount={nodeCount} edgeCount={edgeCount} />
          <div className="absolute right-2 top-2 inline-flex h-5 items-center gap-1 rounded-md border border-slate-200 bg-white/90 px-1.5 text-[10px] font-medium text-slate-600 backdrop-blur">
            <Network size={10} aria-hidden="true" />
            {nodeCount} · {edgeCount}
          </div>
        </div>
        <div className="p-4">
          <h2 className="line-clamp-2 text-[14px] font-semibold leading-snug text-slate-900 transition-colors group-hover:text-indigo-700">
            {topic.title ?? '(без названия)'}
          </h2>
          {topic.description && (
            <p className="mt-1.5 line-clamp-2 text-[12px] leading-relaxed text-slate-500">
              {topic.description}
            </p>
          )}
          <div className="mt-3 flex items-center justify-between text-[11px] text-slate-500">
            <span className="font-mono">{topic.id.slice(0, 8)}</span>
            {date && (
              <span className="inline-flex items-center gap-1">
                <Calendar size={11} aria-hidden="true" />
                {date}
              </span>
            )}
          </div>
        </div>
      </Card>
    </Link>
  );
}

interface MiniGraphProps {
  nodeCount: number;
  edgeCount: number;
}

/**
 * Декоративный мини-граф для карточки темы. Не отражает реальную структуру -
 * просто визуальный акцент, говорящий "это граф". Точки масштабируются по
 * количеству узлов: пустая тема = только корень, до 8 точек у крупной темы.
 * Цвета пока статичные (indigo/emerald/slate). Когда бэк начнёт возвращать
 * statusCounts (см. ADR-016, открытый вопрос) - можно покрасить точки по
 * status-распределению
 */
function TopicMiniGraph({ nodeCount, edgeCount: _edgeCount }: MiniGraphProps) {
  const dots = [
    { x: 60, y: 18, c: '#6366f1', r: 5 },
    { x: 28, y: 50, c: '#10b981', r: 4 },
    { x: 60, y: 64, c: '#10b981', r: 4.5 },
    { x: 96, y: 50, c: '#10b981', r: 4 },
    { x: 14, y: 80, c: '#cbd5e1', r: 3 },
    { x: 44, y: 86, c: '#cbd5e1', r: 3 },
    { x: 78, y: 86, c: '#cbd5e1', r: 3 },
    { x: 108, y: 80, c: '#cbd5e1', r: 3 },
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

  // показываем столько точек сколько есть узлов (минимум 1 - корень,
  // максимум 8 - размер декоративного шаблона)
  const visibleDots = Math.max(1, Math.min(dots.length, nodeCount));

  return (
    <svg viewBox="0 0 124 100" className="h-full w-full" aria-hidden="true">
      <defs>
        <pattern id="dotmini" width="8" height="8" patternUnits="userSpaceOnUse">
          <circle cx="1" cy="1" r="0.7" fill="rgba(15, 23, 42, 0.10)" />
        </pattern>
      </defs>
      <rect width="124" height="100" fill="url(#dotmini)" />
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
            stroke="#94a3b8"
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
          stroke="white"
          strokeWidth="1.5"
        />
      ))}
    </svg>
  );
}

export default TopicListPage;
