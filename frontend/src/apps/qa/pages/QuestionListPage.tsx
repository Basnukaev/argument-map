import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router';
import {
  AlertCircle,
  CheckCircle2,
  HelpCircle,
  Loader2,
  Plus,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import ListToolbar from '@/shared/components/ui/ListToolbar';
import SearchInput from '@/shared/components/ui/SearchInput';
import FilterChips from '@/shared/components/ui/FilterChips';
import SortSelect from '@/shared/components/ui/SortSelect';
import LoadMoreButton from '@/shared/components/ui/LoadMoreButton';
import QuestionStatusBadge from '@/apps/qa/components/QuestionStatusBadge';
import VoteWidget from '@/shared/components/ui/VoteWidget';
import { usePagedSearch } from '@/shared/hooks/usePagedSearch';
import { useT, useFormatDate, hasArabicScript, type DictKey } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type Question = components['schemas']['QuestionResponse'];
type Status = NonNullable<Question['status']>;
type SortKey = 'recent' | 'popular' | 'alphabetical';

const PAGE_SIZE = 20;

const FILTER_LABEL: Record<'ALL' | Status, DictKey> = {
  ALL: 'qa.list.filter_all',
  OPEN: 'qa.list.filter_open',
  ANSWERED: 'qa.list.filter_answered',
  CLOSED: 'qa.list.filter_closed',
};

function QuestionListPage() {
  const t = useT();
  const formatDate = useFormatDate();
  const [statusFilter, setStatusFilter] = useState<Status | 'ALL'>('ALL');
  // Vision 49d Section 2.1 - server-side sort
  const [sort, setSort] = useState<SortKey>('recent');

  /**
   * Backend поддерживает server-side ?status=, ?q= и ?sort= (см.
   * api-contract). usePagedSearch владеет search-инпутом (debounced →
   * ?q=) и пагинацией; статус/сорт передаются через deps - смена любого
   * рефетчит page 0. SWR-кэш: возврат на страницу не перезагружает.
   */
  const buildUrl = useCallback(
    (page: number, q: string): string => {
      const params = new URLSearchParams();
      params.set('page', String(page));
      params.set('size', String(PAGE_SIZE));
      params.set('sort', sort);
      if (q) params.set('q', q);
      if (statusFilter !== 'ALL') params.set('status', statusFilter);
      return `/api/v1/questions?${params.toString()}`;
    },
    [statusFilter, sort],
  );

  const { state, searchInput, setSearchInput, loadMore, loadingMore } =
    usePagedSearch<Question>({
      buildUrl,
      deps: [statusFilter, sort],
      fallbackError: t('qa.list.load_failed'),
    });

  const statusChips = useMemo(
    () =>
      (['ALL', 'OPEN', 'ANSWERED', 'CLOSED'] as const).map((s) => ({
        value: s,
        label: t(FILTER_LABEL[s]),
      })),
    [t],
  );

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
              {t('qa.list.eyebrow')}
            </div>
            <h1 className="font-serif text-[28px] font-semibold leading-tight tracking-tight text-ink-900">
              {t('qa.list.title')}
            </h1>
            {state.kind === 'success' && (
              <p className="mt-1.5 max-w-[680px] text-sm text-ink-500">
                <span className="font-medium text-ink-700">
                  <bdi dir="ltr">{state.data.totalElements}</bdi>
                </span>{' '}
                {t('qa.list.subtitle')}
              </p>
            )}
          </div>
          <Link to="/qa/new">
            <Button icon={Plus}>{t('qa.list.create_button')}</Button>
          </Link>
        </header>

        <ListToolbar
          className="mb-4"
          search={
            <SearchInput
              value={searchInput}
              onChange={setSearchInput}
              placeholder={t('qa.list.search_placeholder')}
              ariaLabel={t('common.search')}
              className="w-full"
            />
          }
          filters={
            <FilterChips
              options={statusChips}
              value={statusFilter}
              onChange={(v) => setStatusFilter(v as Status | 'ALL')}
              ariaLabel={t('qa.list.filter_all')}
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

        {state.kind === 'success' && state.data.items.length === 0 && (
          <Card className="mx-auto max-w-2xl p-12 text-center">
            <HelpCircle
              size={32}
              className="mx-auto mb-3 text-ink-300"
              aria-hidden
            />
            <p className="text-sm text-ink-500">
              {searchInput.trim() ? t('topic.list.not_found') : t('qa.list.empty')}
            </p>
          </Card>
        )}

        {state.kind === 'success' && state.data.items.length > 0 && (
          <>
          <ul className="flex flex-col gap-3">
            {state.data.items
              .filter((q): q is Question & { id: string } => Boolean(q.id))
              .map((qn) => {
                const isArabic = qn.title ? hasArabicScript(qn.title) : false;
                const status = qn.status ?? 'OPEN';
                return (
                  <li key={qn.id}>
                    <Link
                      to={`/qa/${qn.id}`}
                      className="block rounded-md focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 focus-visible:ring-offset-2 focus-visible:ring-offset-bg"
                    >
                      <Card interactive className="flex h-full flex-col p-4">
                        <div className="flex items-start justify-between gap-3">
                          <p
                            className={`min-w-0 flex-1 text-[15px] font-semibold leading-snug text-ink-900 ${isArabic ? 'font-arabic' : 'font-serif'}`}
                            dir="auto"
                          >
                            {qn.title}
                          </p>
                          <QuestionStatusBadge status={status} size="sm" />
                        </div>

                        {qn.body && (
                          <p
                            className="mt-1.5 line-clamp-2 text-xs leading-relaxed text-ink-500"
                            dir="auto"
                          >
                            {qn.body}
                          </p>
                        )}

                        {/* Meta line: дата активности + accepted-индикатор +
                            виджет голосования. updatedAt = last activity
                            (полезнее createdAt при скане списка обсуждений).
                            acceptedAnswerId marker - быстрый сигнал «есть
                            принятый ответ». mt-auto прижимает meta к низу для
                            равной высоты карточек. VoteWidget прижат к end
                            (ms-auto); голосование не должно навигировать -
                            preventDefault на обёртке (Link) + stopPropagation
                            в самом виджете, как в TopicListPage. */}
                        <div className="mt-auto flex flex-wrap items-center gap-x-2.5 gap-y-1 pt-3 text-xs text-ink-400">
                          <bdi dir="ltr">
                            {qn.updatedAt
                              ? `${t('qa.list.card.updated_prefix')} ${formatDate(qn.updatedAt, 'short')}`
                              : qn.createdAt
                                ? formatDate(qn.createdAt, 'short')
                                : ''}
                          </bdi>
                          {qn.acceptedAnswerId && (
                            <>
                              <span aria-hidden>·</span>
                              <span className="inline-flex items-center gap-1 font-medium text-ok-700">
                                <CheckCircle2 size={12} aria-hidden />
                                {t('qa.list.card.has_accepted')}
                              </span>
                            </>
                          )}
                          <div
                            className="ms-auto"
                            onClick={(e) => e.preventDefault()}
                          >
                            <VoteWidget
                              voteUrl={`/api/v1/questions/${qn.id}/vote`}
                              score={qn.voteScore ?? 0}
                              userVote={qn.userVote ?? null}
                              stopPropagation
                              ariaLabel={t('vote.question.aria_widget')}
                              upvoteLabel={t('vote.question.upvote_tooltip')}
                              downvoteLabel={t('vote.question.downvote_tooltip')}
                            />
                          </div>
                        </div>
                      </Card>
                    </Link>
                  </li>
                );
              })}
          </ul>

          {/* Search теперь server-side (?q=) - load-more работает с любым
              query, новые items приходят уже отфильтрованные бэком. */}
          <LoadMoreButton
            onClick={loadMore}
            loading={loadingMore}
            hasNext={state.data.hasNext}
            shownCount={state.data.items.length}
            totalCount={state.data.totalElements}
          />
          </>
        )}
      </div>
    </main>
  );
}

export default QuestionListPage;
