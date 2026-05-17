import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import {
  HelpCircle,
  Plus,
  Search,
  AlertCircle,
  Loader2,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import { useT, useFormatDate, hasArabicScript, type DictKey } from '@/shared/i18n';
import type { AsyncState } from '@/shared/types/async';
import type { components } from '@/shared/api/types';

type Question = components['schemas']['QuestionResponse'];
type Status = NonNullable<Question['status']>;

const STATUS_BADGE: Record<Status, string> = {
  OPEN: 'bg-ok-100 text-ok-700',
  ANSWERED: 'bg-accent-100 text-accent-700',
  CLOSED: 'bg-ink-100 text-ink-600',
};

const STATUS_LABEL: Record<Status, DictKey> = {
  OPEN: 'qa.status.OPEN',
  ANSWERED: 'qa.status.ANSWERED',
  CLOSED: 'qa.status.CLOSED',
};

const FILTER_LABEL: Record<'ALL' | Status, DictKey> = {
  ALL: 'qa.list.filter_all',
  OPEN: 'qa.list.filter_open',
  ANSWERED: 'qa.list.filter_answered',
  CLOSED: 'qa.list.filter_closed',
};

function QuestionListPage() {
  const t = useT();
  const formatDate = useFormatDate();
  const [state, setState] = useState<AsyncState<Question[]>>({ kind: 'loading' });
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<Status | 'ALL'>('ALL');

  useEffect(() => {
    const controller = new AbortController();
    apiGetRaw<Question[]>('/api/v1/questions', { signal: controller.signal })
      .then((questions) => {
        setState({ kind: 'success', data: questions ?? [] });
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        const message =
          e instanceof ApiError
            ? `${e.problem.title}${e.problem.detail ? ': ' + e.problem.detail : ''}`
            : e instanceof Error
              ? e.message
              : 'failed';
        setState({ kind: 'error', message });
      });
    return () => controller.abort();
  }, []);

  const filtered = useMemo(() => {
    if (state.kind !== 'success') return [];
    const q = search.trim().toLowerCase();
    return state.data.filter((qn) => {
      if (statusFilter !== 'ALL' && qn.status !== statusFilter) return false;
      if (!q) return true;
      return (qn.title ?? '').toLowerCase().includes(q);
    });
  }, [state, search, statusFilter]);

  return (
    <main className="min-h-screen bg-bg">
      <Header />
      <div className="mx-auto max-w-[1380px] px-6 py-8">
        <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
          <div className="min-w-0 flex-1">
            <div className="mb-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-500">
              {t('qa.list.eyebrow')}
            </div>
            <h1 className="font-serif text-[28px] font-semibold leading-tight tracking-tight text-ink-900">
              {t('qa.list.title')}
            </h1>
            {state.kind === 'success' && (
              <p className="mt-1.5 max-w-[680px] text-sm text-ink-500">
                <span className="font-medium text-ink-700">
                  <bdi dir="ltr">{state.data.length}</bdi>
                </span>{' '}
                {t('qa.list.subtitle')}
              </p>
            )}
          </div>
          <Link to="/qa/new">
            <Button icon={Plus}>{t('qa.list.create_button')}</Button>
          </Link>
        </header>

        <div className="mb-4 flex flex-wrap items-center gap-2">
          <div className="flex h-9 max-w-md flex-1 items-center rounded-md border border-border-strong bg-elevated transition-colors focus-within:border-accent-500 focus-within:ring-2 focus-within:ring-accent-500/20">
            <Search size={16} className="ms-3 text-ink-400" aria-hidden />
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={t('qa.list.search_placeholder')}
              className="h-full flex-1 bg-transparent px-3 text-sm text-ink-900 outline-none placeholder:text-ink-400"
              dir="auto"
            />
          </div>
          <div className="flex gap-1">
            {(['ALL', 'OPEN', 'ANSWERED', 'CLOSED'] as const).map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => setStatusFilter(s)}
                className={`inline-flex h-7 items-center rounded-sm px-3 text-xs font-medium transition-colors ${
                  statusFilter === s
                    ? 'bg-accent-50 text-accent-700'
                    : 'text-ink-600 hover:bg-ink-100 hover:text-ink-900'
                }`}
              >
                {t(FILTER_LABEL[s])}
              </button>
            ))}
          </div>
        </div>

        {state.kind === 'loading' && (
          <div className="flex items-center gap-2 text-sm text-ink-500">
            <Loader2 size={16} className="animate-spin" aria-hidden />
            {t('common.loading')}
          </div>
        )}

        {state.kind === 'error' && (
          <Card className="mx-auto max-w-2xl border-err-500/40 bg-err-100 p-5">
            <div className="flex items-start gap-3">
              <AlertCircle size={18} className="mt-0.5 text-err-700" aria-hidden />
              <div>
                <p className="text-sm font-semibold text-err-700">{t('common.error')}</p>
                <p className="mt-1 text-xs text-err-700">{state.message}</p>
              </div>
            </div>
          </Card>
        )}

        {state.kind === 'success' && state.data.length === 0 && (
          <Card className="mx-auto max-w-2xl p-12 text-center">
            <HelpCircle
              size={32}
              className="mx-auto mb-3 text-ink-300"
              aria-hidden
            />
            <p className="text-sm text-ink-500">{t('qa.list.empty')}</p>
          </Card>
        )}

        {state.kind === 'success' && state.data.length > 0 && filtered.length === 0 && (
          <p className="text-center text-sm text-ink-500">
            {t('topic.list.not_found')}
          </p>
        )}

        {state.kind === 'success' && filtered.length > 0 && (
          <ul className="flex flex-col gap-2">
            {filtered
              .filter((q): q is Question & { id: string } => Boolean(q.id))
              .map((qn) => {
                const isArabic = qn.title ? hasArabicScript(qn.title) : false;
                const status = qn.status ?? 'OPEN';
                return (
                  <li key={qn.id}>
                    <Link
                      to={`/qa/${qn.id}`}
                      className="block focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 focus-visible:ring-offset-2 rounded-md"
                    >
                      <Card interactive className="p-4">
                        <div className="flex items-start gap-3">
                          <span
                            className={`inline-flex items-center rounded-sm px-1.5 py-0.5 text-xs font-semibold uppercase tracking-wider ${STATUS_BADGE[status]}`}
                          >
                            {t(STATUS_LABEL[status])}
                          </span>
                          <div className="min-w-0 flex-1">
                            <p
                              className={`text-sm font-semibold text-ink-900 ${isArabic ? 'font-arabic' : ''}`}
                              dir="auto"
                            >
                              {qn.title}
                            </p>
                            {qn.body && (
                              <p
                                className="mt-1 line-clamp-2 text-xs text-ink-500"
                                dir="auto"
                              >
                                {qn.body}
                              </p>
                            )}
                            <p className="mt-2 text-xs text-ink-400">
                              <bdi dir="ltr">
                                {qn.createdAt ? formatDate(qn.createdAt, 'short') : ''}
                              </bdi>
                            </p>
                          </div>
                        </div>
                      </Card>
                    </Link>
                  </li>
                );
              })}
          </ul>
        )}
      </div>
    </main>
  );
}

export default QuestionListPage;
