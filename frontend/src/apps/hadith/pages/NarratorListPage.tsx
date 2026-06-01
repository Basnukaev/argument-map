import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { Users, Search, Loader2, ArrowLeft } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import { useT, type DictKey } from '@/shared/i18n';
import type { AsyncState } from '@/shared/types/async';
import { RELIABILITY_TOKENS } from '@/apps/hadith/sanadTokens';
import type { NarratorResponseDto, Paged, ReliabilityGrade } from '@/apps/hadith/types';

const PAGE_SIZE = 30;
const GRADES: ReliabilityGrade[] = ['SAHABI', 'THIQA', 'SADUQ', 'MAQBUL', 'DAIF', 'MATRUK', 'UNKNOWN'];
const SEARCH_DEBOUNCE_MS = 300;

/**
 * Каталог передатчиков (علم الرجال). Поиск по имени + фильтр по степени
 * надёжности. Карточка ведёт на NarratorDetailPage.
 */
function NarratorListPage() {
  const t = useT();
  const [state, setState] = useState<AsyncState<Paged<NarratorResponseDto>>>({ kind: 'loading' });
  // searchInput - что печатает юзер; searchQ - debounced (триггерит запрос).
  // Раньше запрос шёл на каждый keystroke - спам API.
  const [searchInput, setSearchInput] = useState('');
  const [searchQ, setSearchQ] = useState('');
  const [grade, setGrade] = useState<ReliabilityGrade | 'ALL'>('ALL');
  const [loadingMore, setLoadingMore] = useState(false);

  // Debounce: после 300ms простоя sync'аем searchInput → searchQ
  useEffect(() => {
    const handle = setTimeout(() => {
      setSearchQ(searchInput.trim());
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(handle);
  }, [searchInput]);

  // НЕ сбрасываем в loading на refetch (избегаем flash-to-spinner +
  // react-hooks set-state-in-effect) - старый список виден пока грузится.
  useEffect(() => {
    const controller = new AbortController();
    const params = new URLSearchParams();
    params.set('page', '0');
    params.set('size', String(PAGE_SIZE));
    if (searchQ) params.set('q', searchQ);
    if (grade !== 'ALL') params.set('reliability', grade);
    apiGetRaw<Paged<NarratorResponseDto>>(`/api/v1/hadith/narrators?${params.toString()}`, {
      signal: controller.signal,
    })
      .then((d) => setState({ kind: 'success', data: d }))
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        setState({ kind: 'error', message: e instanceof ApiError ? e.problem.title : String(e) });
      });
    return () => controller.abort();
  }, [searchQ, grade]);

  /** Load More - подгружает следующую страницу, аппендит к existing list */
  const loadMore = () => {
    if (state.kind !== 'success' || !state.data.hasNext || loadingMore) return;
    const nextPage = state.data.page + 1;
    setLoadingMore(true);
    const params = new URLSearchParams();
    params.set('page', String(nextPage));
    params.set('size', String(PAGE_SIZE));
    if (searchQ) params.set('q', searchQ);
    if (grade !== 'ALL') params.set('reliability', grade);
    apiGetRaw<Paged<NarratorResponseDto>>(`/api/v1/hadith/narrators?${params.toString()}`)
      .then((resp) => {
        setState((prev) =>
          prev.kind === 'success'
            ? { kind: 'success', data: { ...resp, items: [...prev.data.items, ...resp.items] } }
            : prev,
        );
      })
      .catch((e: unknown) => {
        setState({ kind: 'error', message: e instanceof ApiError ? e.problem.title : String(e) });
      })
      .finally(() => setLoadingMore(false));
  };

  return (
    <main className="min-h-screen bg-bg">
      <Header />
      <div className="mx-auto max-w-[1380px] px-3 py-6 sm:px-6 sm:py-8">
        <div className="mb-4">
          <Link to="/hadith/hadiths" className="text-sm text-ink-500 hover:text-accent-700">
            <span className="inline-flex items-center gap-1">
              <ArrowLeft size={14} aria-hidden /> {t('nav.hadith')}
            </span>
          </Link>
        </div>

        <header className="mb-6">
          <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-ink-500">
            <Users size={12} aria-hidden /> {t('hadith.narrators.title')}
          </div>
          <h1 className="mt-1 text-2xl font-semibold text-ink-900">{t('hadith.narrators.title')}</h1>
          <p className="mt-1 text-sm text-ink-600">{t('hadith.narrators.subtitle')}</p>
        </header>

        <div className="mb-6 flex flex-wrap items-center gap-3">
          <div className="flex h-9 max-w-md flex-1 items-center rounded-md border border-border-strong bg-elevated focus-within:border-accent-500">
            <Search size={15} className="ms-3 text-ink-400" aria-hidden />
            <input
              type="search"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder={t('hadith.narrators.search')}
              dir="auto"
              className="flex-1 bg-transparent px-3 text-sm text-ink-900 outline-none"
            />
          </div>
          <select
            value={grade}
            onChange={(e) => setGrade(e.target.value as typeof grade)}
            className="h-9 rounded-md border border-border-strong bg-elevated px-2 text-sm text-ink-900 outline-none focus:border-accent-500"
          >
            <option value="ALL">{t('hadith.narrators.filter_all')}</option>
            {GRADES.map((g) => (
              <option key={g} value={g}>
                {t(`hadith.reliability.${g}` as DictKey)}
              </option>
            ))}
          </select>
        </div>

        {state.kind === 'loading' && (
          <div className="flex items-center gap-2 py-12 text-sm text-ink-500">
            <Loader2 size={16} className="animate-spin" /> {t('common.loading')}
          </div>
        )}

        {state.kind === 'error' && (
          <Card className="border-err-500/40 bg-err-100 p-5">
            <div className="text-sm text-ink-900">{state.message}</div>
          </Card>
        )}

        {state.kind === 'success' &&
          (state.data.items.length === 0 ? (
            <div className="rounded-md border border-dashed border-border-strong bg-elevated/50 p-8 text-center text-sm text-ink-500">
              {t('hadith.narrators.empty')}
            </div>
          ) : (
            <>
            <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {state.data.items.map((nr) => {
                const rel = nr.reliabilityGrade ? RELIABILITY_TOKENS[nr.reliabilityGrade] : null;
                return (
                  <li key={nr.id}>
                    <Link
                      to={`/hadith/narrators/${nr.id}`}
                      className="block rounded-md focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
                    >
                      <Card interactive className="p-4">
                        <div className="flex items-start justify-between gap-2">
                          <div className="font-arabic text-lg leading-tight text-ink-900" dir="rtl">
                            {nr.nameAr}
                          </div>
                          {rel && nr.reliabilityGrade && (
                            <span
                              className={`shrink-0 rounded-sm px-1.5 py-0.5 font-arabic text-xs font-semibold ${rel.chip}`}
                              dir="rtl"
                              title={t(`hadith.reliability.${nr.reliabilityGrade}` as DictKey)}
                            >
                              {rel.ar}
                            </span>
                          )}
                        </div>
                        <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-ink-500">
                          {nr.kunya && (
                            <span className="font-arabic" dir="rtl">
                              {nr.kunya}
                            </span>
                          )}
                          {nr.yearDeathHijri != null && (
                            <span>
                              {t('hadith.graph.died')} {nr.yearDeathHijri} {t('hadith.graph.hijri')}
                            </span>
                          )}
                        </div>
                      </Card>
                    </Link>
                  </li>
                );
              })}
            </ul>
            {state.data.hasNext && (
              <div className="mt-4 flex justify-center">
                <button
                  type="button"
                  onClick={loadMore}
                  disabled={loadingMore}
                  className="inline-flex items-center gap-2 rounded-md border border-border-strong bg-elevated px-4 py-2 text-sm font-medium text-ink-700 hover:bg-ink-100 disabled:opacity-50"
                >
                  {loadingMore && <Loader2 size={14} className="animate-spin" />}
                  {t('common.load_more')}
                </button>
              </div>
            )}
            </>
          ))}
      </div>
    </main>
  );
}

export default NarratorListPage;
