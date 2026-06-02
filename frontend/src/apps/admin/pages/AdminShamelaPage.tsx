import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import {
  AlertCircle,
  ArrowLeft,
  BookOpen,
  CheckCircle2,
  Download,
  ExternalLink,
  FileUp,
  Loader2,
  RefreshCw,
  Search,
  Settings,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Header from '@/shared/components/layout/Header';
import { apiGetRaw, apiPostRaw, ApiError } from '@/shared/api/client';
import type { components } from '@/shared/api/types';
import { toast } from '@/shared/stores/toastStore';
import { hasArabicScript, useT, useFormatDate, useNumberFormat } from '@/shared/i18n';
import FileUploadModal from '@/apps/admin/components/FileUploadModal';

type SyncStatus = components['schemas']['SyncStatusResponse'];
type SearchResult = components['schemas']['StagingBookSearchResponse'];
type ImportBookResponse = components['schemas']['ImportBookResponse'];
type MapBookResponse = components['schemas']['MapBookResponse'];
type SyncMasterResponse = components['schemas']['SyncMasterResponse'];
type BackfillResponse = components['schemas']['BackfillBibliographyResponse'];

const SEARCH_DEBOUNCE_MS = 300;

function AdminShamelaPage() {
  const t = useT();
  const formatDate = useFormatDate();
  const formatNumber = useNumberFormat();
  const formatDateTime = (iso: string | undefined): string =>
    iso ? formatDate(iso, 'short') : t('admin.last_sync_never_short');
  const [status, setStatus] = useState<SyncStatus | null>(null);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [statusLoading, setStatusLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);

  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchResult[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [importingId, setImportingId] = useState<number | null>(null);

  const debounceRef = useRef<number | null>(null);
  const [reloadStatusToken, setReloadStatusToken] = useState(0);
  const [backfilling, setBackfilling] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);

  // Все setState идут в Promise-callbacks - lint react-hooks/set-state-in-effect
  // запрещает только sync setState в теле эффекта. reloadStatusToken
  // триггерит refetch после sync/import (мутации статуса)
  useEffect(() => {
    const controller = new AbortController();
    apiGetRaw<SyncStatus>('/api/v1/admin/shamela/sync-status', { signal: controller.signal })
      .then((next) => {
        setStatus(next);
        setStatusError(null);
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        setStatusError(formatError(e, t('admin.status_load_error')));
      })
      .finally(() => {
        if (!controller.signal.aborted) setStatusLoading(false);
      });
    return () => controller.abort();
  }, [reloadStatusToken, t]);

  // Debounced search: пустой query → results просто не рендерятся (derived
  // state), не нужен синхронный setState reset в теле эффекта
  useEffect(() => {
    if (query.trim().length === 0) {
      return;
    }
    const controller = new AbortController();
    if (debounceRef.current !== null) {
      window.clearTimeout(debounceRef.current);
    }
    debounceRef.current = window.setTimeout(() => {
      const url = `/api/v1/admin/shamela/search?q=${encodeURIComponent(query.trim())}&limit=50`;
      apiGetRaw<SearchResult[]>(url, { signal: controller.signal })
        .then((rows) => {
          setResults(rows ?? []);
          setSearchError(null);
          setSearchLoading(false);
        })
        .catch((e: unknown) => {
          if (controller.signal.aborted) return;
          setSearchError(formatError(e, t('admin.search_failed')));
          setResults([]);
          setSearchLoading(false);
        });
    }, SEARCH_DEBOUNCE_MS);

    return () => {
      controller.abort();
      if (debounceRef.current !== null) {
        window.clearTimeout(debounceRef.current);
      }
    };
  }, [query, t]);

  const onQueryChange = (value: string) => {
    setQuery(value);
    setSearchError(null);
    if (value.trim().length === 0) {
      setResults([]);
      setSearchLoading(false);
    } else {
      setSearchLoading(true);
    }
  };

  const onBackfillBibliography = async () => {
    setBackfilling(true);
    try {
      const res = await apiPostRaw<BackfillResponse>(
        '/api/v1/admin/shamela/backfill-bibliography',
        undefined,
      );
      toast.success(
        t('admin.backfill_done')
          .replace('{scanned}', String(res.scanned ?? 0))
          .replace('{updated}', String(res.updated ?? 0))
          .replace('{skipped}', String(res.skipped ?? 0)),
      );
    } catch (e) {
      toast.error(formatError(e, t('admin.backfill_failed')));
    } finally {
      setBackfilling(false);
    }
  };

  const onSyncMaster = async () => {
    setSyncing(true);
    try {
      const res = await apiPostRaw<SyncMasterResponse>(
        '/api/v1/admin/shamela/sync-master',
        undefined,
      );
      if (res.changed) {
        toast.success(
          t('admin.sync_done')
            .replace('{version}', String(res.currentVersion ?? ''))
            .replace('{books}', String(res.booksCount ?? 0))
            .replace('{authors}', String(res.authorsCount ?? 0)),
        );
      } else {
        toast.info(
          t('admin.sync_uptodate').replace('{version}', String(res.currentVersion ?? '')),
        );
      }
      setReloadStatusToken((n) => n + 1);
    } catch (e) {
      toast.error(formatShamelaError(e, t));
    } finally {
      setSyncing(false);
    }
  };

  const onImport = async (bookId: number) => {
    setImportingId(bookId);
    try {
      const imported = await apiPostRaw<ImportBookResponse>(
        `/api/v1/admin/shamela/import-book/${bookId}`,
        undefined,
      );
      const mapped = await apiPostRaw<MapBookResponse>(
        `/api/v1/admin/shamela/map-book/${bookId}`,
        undefined,
      );
      toast.success(
        t('admin.import_done')
          .replace('{pages}', String(imported.pagesCount ?? 0))
          .replace('{titles}', String(imported.titlesCount ?? 0))
          .replace('{id}', mapped.bookId?.slice(0, 8) ?? ''),
      );
      setResults((prev) =>
        prev.map((r) => (r.bookId === bookId ? { ...r, isMapped: true } : r)),
      );
      setReloadStatusToken((n) => n + 1);
    } catch (e) {
      toast.error(formatError(e, `${t('admin.import_failed')}: ${bookId}`));
    } finally {
      setImportingId(null);
    }
  };

  const catalogReady = (status?.booksCount ?? 0) > 0;
  const showEmptyHero = !statusLoading && !statusError && status !== null && !catalogReady;
  const showSearchUI = catalogReady;

  return (
    <main className="min-h-screen bg-bg">
      <Header />

      <div className="mx-auto max-w-[1380px] px-3 py-6 sm:px-6 sm:py-8">
        <div className="mb-4">
          <Link to="/admin" className="text-sm text-ink-500 hover:text-accent-700">
            <span className="inline-flex items-center gap-1">
              <ArrowLeft size={14} aria-hidden /> {t('admin.dashboard.back_link')}
            </span>
          </Link>
        </div>
        {/* === Editorial header: eyebrow + serif h1 + descriptor === */}
        <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
          <div className="min-w-0 flex-1">
            <div className="mb-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-500">
              {t('admin.eyebrow')}
            </div>
            <h1 className="font-serif text-[28px] font-semibold leading-tight tracking-tight text-ink-900">
              {t('admin.hero_title')}
            </h1>
            <p className="mt-1.5 max-w-[680px] text-sm text-ink-500">
              {t('admin.subtitle')}
            </p>
          </div>
          <div className="flex items-center gap-2">
            {/* Порядок: вторичные → primary. Primary CTA (sync) anchored
                к правому краю - конвенция admin tools. Backfill metadata -
                shamela-специфичное действие, видно когда каталог готов.
                PDF-импорт и audit теперь first-class на /admin дашборде,
                поэтому буферное ••• overflow-меню убрано. */}
            {catalogReady && (
              <Button
                variant="ghost"
                icon={Settings}
                onClick={onBackfillBibliography}
                disabled={backfilling}
              >
                {t('admin.backfill_action')}
              </Button>
            )}
            <Button
              variant="secondary"
              icon={FileUp}
              onClick={() => setUploadOpen(true)}
            >
              {t('admin.file_upload.section_action_short')}
            </Button>
            <Button icon={RefreshCw} onClick={onSyncMaster} disabled={syncing}>
              {syncing ? t('admin.sync_in_progress') : t('admin.sync_button')}
            </Button>
          </div>
        </header>

        {/* === Status states === */}
        {statusLoading && (
          <div className="mb-8 flex items-center gap-2 rounded-lg border border-border bg-elevated px-5 py-4 text-sm text-ink-500">
            <Loader2 size={16} className="animate-spin" aria-hidden="true" />
            {t('admin.loading_status')}
          </div>
        )}

        {statusError && (
          <div className="mb-8 flex items-start gap-3 rounded-lg border border-err-500/40 bg-err-100 px-5 py-4 text-err-700">
            <AlertCircle size={20} className="mt-0.5 shrink-0" aria-hidden="true" />
            <div>
              <p className="font-semibold">{t('admin.status_load_error')}</p>
              <p className="mt-1 text-sm">{statusError}</p>
            </div>
          </div>
        )}

        {showEmptyHero && (
          <EmptyCatalogHero onSync={onSyncMaster} syncing={syncing} />
        )}

        {status && catalogReady && (
          <StatusStrip
            status={status}
            formatNumber={formatNumber}
            formatDateTime={formatDateTime}
          />
        )}

        {/* === Hero search === */}
        {showSearchUI && (
          <section className="mb-5">
            {/* Без иконки в h2: одна иконка в input уже служит visual
                anchor'ом секции. попытка align'нуть h2-icon с input-icon
                ловит micro-delta (разный размер + 1px border input'а) и
                читается как «почти-выровнено», что хуже чем чистая
                типографика без шума */}
            <h2 className="mb-2 text-sm font-semibold text-ink-900">
              {t('admin.search_in_catalog')}
            </h2>
            <HeroSearchInput
              query={query}
              onQueryChange={onQueryChange}
              searchLoading={searchLoading}
              matchCount={results.length}
              hint={t('admin.search_placeholder')}
              ariaLabel={t('admin.search_aria')}
              matchCountTemplate={t('admin.hero.match_count')}
            />
          </section>
        )}

        {/* === Search results / error / not-found === */}
        {searchError && (
          <div className="mb-8 rounded-lg border border-err-500/40 bg-err-100 px-4 py-3 text-sm text-err-700">
            {searchError}
          </div>
        )}

        {!searchError && query.trim().length > 0 && results.length === 0 && !searchLoading && (
          <p className="mb-8 text-sm text-ink-500">{t('topic.list.not_found')}</p>
        )}

        {results.length > 0 && (
          <ResultsTable
            results={results}
            onImport={onImport}
            importingId={importingId}
          />
        )}
      </div>

      {uploadOpen && (
        <FileUploadModal
          open
          onClose={() => setUploadOpen(false)}
          onUploaded={() => setReloadStatusToken((n) => n + 1)}
        />
      )}
    </main>
  );
}

// ====================================================================
//                          Sub-components
// ====================================================================

interface StatusStripProps {
  status: SyncStatus;
  formatNumber: (n: number) => string;
  formatDateTime: (iso: string | undefined) => string;
}

function StatusStrip({ status, formatNumber, formatDateTime }: StatusStripProps) {
  const t = useT();
  const mapped = status.mappedBooksCount ?? 0;
  const total = status.booksCount ?? 0;
  const mappedText = `${formatNumber(mapped)} / ${formatNumber(total)}`;
  const version = status.masterVersion?.toString() ?? '0';
  const lastSync = formatDateTime(status.lastSyncedAt);

  // 5 stat'ов + status chip как 6-я колонка (как в design-reference v2).
  // divide-x на grid даёт vertical hairlines между ячейками - визуальное
  // разделение метрик которое нужно в dense data strip. без gap-x:
  // divide-x не работает корректно с gap-x (расходятся 1px borders и gap)
  return (
    <section className="mb-8 overflow-hidden rounded-lg border border-border bg-elevated">
      <div className="grid grid-cols-2 divide-y divide-border sm:grid-cols-3 sm:divide-y-0 sm:[&>*]:border-s sm:[&>*]:border-border sm:[&>*:first-child]:border-s-0 lg:grid-cols-[repeat(5,minmax(0,1fr))_auto]">
        <Stat
          label={t('admin.master_version')}
          value={version}
          hint={
            <>
              {t('admin.last_sync')}: <bdi dir="ltr">{lastSync}</bdi>
            </>
          }
        />
        <Stat label={t('admin.categories')} value={status.categoriesCount?.toString() ?? '0'} />
        <Stat label={t('admin.authors')} value={formatNumber(status.authorsCount ?? 0)} />
        <Stat label={t('admin.books_in_staging')} value={formatNumber(total)} />
        <Stat label={t('admin.mapped_count')} value={mappedText} accent />
        <div className="col-span-2 flex items-center justify-center px-5 py-4 sm:col-span-3 sm:justify-end lg:col-span-1">
          <span className="inline-flex items-center gap-1.5 rounded-full border border-ok-500/30 bg-ok-100 px-2.5 py-0.5 text-[11px] font-medium text-ok-700 whitespace-nowrap">
            <span className="h-1.5 w-1.5 rounded-full bg-ok-500" aria-hidden />
            <span className="font-mono tabular-nums">
              <bdi dir="ltr">
                v{version} · sync {lastSync}
              </bdi>
            </span>
          </span>
        </div>
      </div>
    </section>
  );
}

interface HeroSearchInputProps {
  query: string;
  onQueryChange: (v: string) => void;
  searchLoading: boolean;
  matchCount: number;
  hint: string;
  ariaLabel: string;
  matchCountTemplate: string;
}

function HeroSearchInput({
  query,
  onQueryChange,
  searchLoading,
  matchCount,
  hint,
  ariaLabel,
  matchCountTemplate,
}: HeroSearchInputProps) {
  const active = query.trim().length > 0;
  return (
    <div
      className={`flex h-10 max-w-2xl items-center rounded-md bg-elevated transition-all ${
        active
          ? 'border-[1.5px] border-accent-500 shadow-[0_0_0_3px_color-mix(in_srgb,var(--c-accent-500)_15%,transparent)]'
          : 'border border-border-strong focus-within:border-accent-500 focus-within:ring-2 focus-within:ring-accent-500/20'
      }`}
    >
      <Search size={15} className="ms-3 text-ink-400" aria-hidden="true" />
      <input
        type="search"
        value={query}
        onChange={(e) => onQueryChange(e.target.value)}
        placeholder={hint}
        className="flex-1 bg-transparent px-3 text-sm text-ink-900 outline-none placeholder:text-ink-400"
        aria-label={ariaLabel}
      />
      {searchLoading ? (
        <Loader2 size={14} className="me-3 animate-spin text-ink-400" aria-hidden="true" />
      ) : (
        active &&
        matchCount > 0 && (
          <span className="me-3 font-mono text-xs tabular-nums text-ink-500">
            {matchCountTemplate.replace('{n}', String(matchCount))}
          </span>
        )
      )}
    </div>
  );
}

interface ResultsTableProps {
  results: SearchResult[];
  onImport: (bookId: number) => void;
  importingId: number | null;
}

function ResultsTable({ results, onImport, importingId }: ResultsTableProps) {
  const t = useT();
  // grid template одинаков для header и row - sync через CSS variable не нужен,
  // inline style гарантирует выравнивание колонок. Min 668px - на mobile
  // <668px скроллируется горизонтально внутри parent overflow-x-auto
  const gridCols = '88px 1fr 220px 80px 200px';
  return (
    <div className="mb-8 overflow-x-auto rounded-lg border border-border bg-elevated">
      <div className="min-w-[668px]">
        <div
          className="sticky top-0 z-[1] grid items-center gap-3 border-b border-border bg-sunken px-4 py-2 text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500"
          style={{ gridTemplateColumns: gridCols }}
        >
          <span>{t('admin.table.id')}</span>
          <span>{t('admin.table.name')}</span>
          <span>{t('admin.table.author')}</span>
          <span>{t('admin.table.major')}</span>
          {/* Заголовок СТАТУС намеренно left-aligned (как остальные)
              хотя кнопки в content прижаты к правому краю. Консистентность
              всех headers важнее «нависания» header над content - типовой
              компромисс для action-колонок в data-tables */}
          <span>{t('admin.table.status')}</span>
        </div>
        <ul className="divide-y divide-border">
          {results.map((r) => (
            <SearchResultRow
              key={r.bookId}
              result={r}
              onImport={onImport}
              isImporting={importingId === r.bookId}
              gridCols={gridCols}
            />
          ))}
        </ul>
      </div>
    </div>
  );
}

interface SearchResultRowProps {
  result: SearchResult;
  onImport: (bookId: number) => void;
  isImporting: boolean;
  gridCols: string;
}

function SearchResultRow({ result, onImport, isImporting, gridCols }: SearchResultRowProps) {
  const t = useT();
  // dir="auto" - браузер сам определит направление по первому сильному символу.
  // hasArabicScript для переключения font-naskh (dir="auto" шрифт не меняет)
  const arabicName = hasArabicScript(result.name ?? undefined);
  const arabicAuthor = hasArabicScript(result.authorName ?? undefined);

  return (
    <li>
      <div
        className="grid items-center gap-3 px-4 py-2.5 transition-colors hover:bg-sunken/60"
        style={{ gridTemplateColumns: gridCols }}
      >
        <div className="font-mono text-xs text-ink-500 tabular-nums">
          <bdi dir="ltr">{result.bookId}</bdi>
        </div>
        <div
          dir="auto"
          className={`min-w-0 truncate text-sm font-medium leading-snug text-ink-900 ${
            arabicName ? 'font-naskh text-base' : ''
          }`}
          title={result.name ?? ''}
        >
          {result.name ?? t('reader.no_book_title')}
        </div>
        <div
          dir="auto"
          className={`min-w-0 truncate text-xs text-ink-600 ${arabicAuthor ? 'font-naskh text-sm' : ''}`}
          title={result.authorName ?? ''}
        >
          {result.authorName ?? '—'}
        </div>
        <div className="font-mono text-xs text-ink-500 tabular-nums">
          <bdi dir="ltr">v{result.majorRelease}</bdi>
        </div>
        <div className="flex justify-end">
          {result.isMapped ? (
            // Чип «Импортирована» + кнопка «В библиотеке» = дублирование:
            // обе сущности говорят «книга уже у нас». достаточно одной
            // кнопки с галкой - и status marker (✓) и actionable navigation
            <Link to="/books">
              <Button variant="ghost" size="sm" icon={CheckCircle2} iconRight={ExternalLink}>
                {t('admin.in_library')}
              </Button>
            </Link>
          ) : (
            <Button
              icon={Download}
              size="sm"
              onClick={() => result.bookId !== undefined && onImport(result.bookId)}
              disabled={isImporting}
            >
              {isImporting ? t('admin.importing') : t('admin.import')}
            </Button>
          )}
        </div>
      </div>
    </li>
  );
}

interface EmptyCatalogHeroProps {
  onSync: () => void;
  syncing: boolean;
}

function EmptyCatalogHero({ onSync, syncing }: EmptyCatalogHeroProps) {
  const t = useT();
  return (
    <section className="mb-8 flex flex-col items-center gap-4 rounded-lg border border-border bg-elevated px-6 py-10 text-center sm:flex-row sm:text-start">
      <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-accent-50 text-accent-600">
        <BookOpen size={24} aria-hidden />
      </div>
      <div className="min-w-0 flex-1">
        <h2 className="font-serif text-xl font-semibold text-ink-900">
          {t('admin.empty_catalog_hero.title')}
        </h2>
        <p className="mt-1 max-w-[560px] text-sm text-ink-500">
          {t('admin.empty_catalog_hero.body')}
        </p>
      </div>
      <Button icon={RefreshCw} size="lg" onClick={onSync} disabled={syncing}>
        {syncing ? t('admin.sync_in_progress') : t('admin.empty_catalog_hero.action')}
      </Button>
    </section>
  );
}

interface StatProps {
  label: string;
  value: string;
  hint?: React.ReactNode;
  /** accent для главной (headline) метрики - например, замаплено / всего */
  accent?: boolean;
}

function Stat({ label, value, hint, accent = false }: StatProps) {
  return (
    <div className="min-w-0 px-5 py-4">
      <div className="text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500">
        {label}
      </div>
      <div
        className={`mt-1 font-mono text-[22px] font-bold leading-none tabular-nums ${
          accent ? 'text-accent-600' : 'text-ink-900'
        }`}
      >
        {value}
      </div>
      {hint && <div className="mt-1.5 text-[11px] text-ink-400">{hint}</div>}
    </div>
  );
}

function formatError(e: unknown, fallback: string): string {
  if (e instanceof ApiError) {
    return `${e.problem.title}${e.problem.detail ? ': ' + e.problem.detail : ''}`;
  }
  if (e instanceof Error) return e.message;
  return fallback;
}

/**
 * Локализованный mapping для shamela-specific ошибок. Сырое Problem
 * Details detail от бэка показывать юзеру плохо (содержит технические
 * детали вроде маскированного api_key). Мапим по problem.type → понятное
 * сообщение на текущей локали.
 *
 * Маркеры problem.type (slug в конце URL):
 * - shamela-api-error - 502 от внешнего dev.shamela.ws (недоступен/VPN)
 * - shamela-archive-error - битый ZIP/SQLite архив с патчем
 * - shamela-reader-error - ошибка чтения SQLite после распаковки
 */
function formatShamelaError(e: unknown, t: (k: import('@/shared/i18n').DictKey) => string): string {
  if (e instanceof ApiError) {
    const type = e.problem.type ?? '';
    if (type.endsWith('/shamela-api-error')) {
      return t('admin.sync.error.shamela_unreachable');
    }
    if (type.endsWith('/shamela-archive-error')) {
      return t('admin.sync.error.shamela_archive');
    }
    if (type.endsWith('/shamela-reader-error')) {
      return t('admin.sync.error.shamela_reader');
    }
    return `${e.problem.title}${e.problem.detail ? ': ' + e.problem.detail : ''}`;
  }
  if (e instanceof Error) return e.message;
  return t('common.unknown_error');
}

export default AdminShamelaPage;
