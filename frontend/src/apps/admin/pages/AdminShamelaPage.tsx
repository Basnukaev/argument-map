import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import {
  AlertCircle,
  CheckCircle2,
  Database,
  Download,
  ExternalLink,
  FileUp,
  Loader2,
  RefreshCw,
  Search,
  Settings,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
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
  // Локализованный formatDateTime - использует useFormatDate в short stylа.
  // При null/undefined возвращает «никогда» из словаря (раньше был хардкод)
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

  /**
   * Все setState идут в Promise-callbacks (.then/.catch) - это асинхронные
   * хвосты, не synchronous body of effect. ESLint правило
   * react-hooks/set-state-in-effect запрещает только sync setState в теле
   * эффекта. Через `reloadStatusToken` триггерим повторный fetch после
   * sync-master / import-book (вместо вызова shared async функции,
   * который lint считает potential synchronous setState из call graph)
   */
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

  /**
   * Debounced search при изменении query. Empty-query сценарий не
   * требует state-сброса - в JSX через `query.trim().length > 0` гард
   * results просто не показываются. Это derived state вместо
   * synchronous setState reset (правило set-state-in-effect)
   */
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
      toast.error(formatError(e, t('common.unknown_error')));
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

  return (
    <main className="min-h-screen bg-bg">
      <Header />

      <div className="mx-auto max-w-[1380px] px-6 py-6">
        <div className="mb-6 flex items-end justify-between gap-4">
          <div>
            <h1 className="flex items-center gap-2.5 text-xl font-bold tracking-tight text-ink-900">
              <Settings size={20} className="text-accent-600" aria-hidden />
              {t('admin.title')} · Shamela
            </h1>
            <p className="mt-1 text-sm text-ink-500">{t('admin.subtitle')}</p>
          </div>
          {status && (
            <span
              className={`inline-flex items-center gap-1.5 rounded-sm px-2.5 py-1 text-xs font-medium ${
                (status.booksCount ?? 0) > 0
                  ? 'bg-ok-100 text-ok-700 border border-ok-500/40'
                  : 'bg-warn-100 text-warn-700 border border-warn-500/40'
              }`}
            >
              <span className={`h-1.5 w-1.5 rounded-full ${(status.booksCount ?? 0) > 0 ? 'bg-ok-500' : 'bg-warn-500'}`} />
              {(status.booksCount ?? 0) > 0 ? t('admin.status_ready') : t('admin.status_empty')}
            </span>
          )}
        </div>

        {/* Sync-status dashboard */}
        <Card className="mb-6 p-5">
          {statusLoading && (
            <div className="flex items-center gap-2 text-sm text-ink-500">
              <Loader2 size={16} className="animate-spin" aria-hidden="true" />
              {t('admin.loading_status')}
            </div>
          )}
          {statusError && (
            <div className="flex items-start gap-3 text-err-700">
              <AlertCircle size={20} className="mt-0.5 shrink-0 text-err-700" aria-hidden="true" />
              <div>
                <p className="font-semibold">{t('admin.status_load_error')}</p>
                <p className="mt-1 text-sm">{statusError}</p>
              </div>
            </div>
          )}
          {status && (
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div className="grid flex-1 grid-cols-2 gap-x-8 gap-y-3 sm:grid-cols-4">
                <Stat
                  label={t('admin.master_version')}
                  value={status.masterVersion?.toString() ?? '0'}
                  hint={<>{t('admin.last_sync')}: <bdi dir="ltr">{formatDateTime(status.lastSyncedAt)}</bdi></>}
                />
                <Stat
                  label={t('admin.categories')}
                  value={status.categoriesCount?.toString() ?? '0'}
                />
                <Stat
                  label={t('admin.authors')}
                  value={formatNumber(status.authorsCount ?? 0)}
                />
                <Stat
                  label={t('admin.books_in_staging')}
                  value={formatNumber(status.booksCount ?? 0)}
                  hint={`${t('admin.mapped_count')}: ${formatNumber(status.mappedBooksCount ?? 0)}`}
                />
              </div>
              <div className="flex flex-wrap gap-2">
                <Button icon={RefreshCw} onClick={onSyncMaster} disabled={syncing}>
                  {syncing ? t('admin.sync_in_progress') : t('admin.sync_button')}
                </Button>
                <Button
                  variant="secondary"
                  icon={Settings}
                  onClick={onBackfillBibliography}
                  disabled={backfilling}
                >
                  {backfilling ? t('common.loading') : t('admin.backfill_action')}
                </Button>
              </div>
            </div>
          )}
        </Card>

        {/* File upload section - альтернатива shamela import */}
        <Card className="mb-6 flex flex-wrap items-center justify-between gap-4 p-5">
          <div className="min-w-0 flex-1">
            <h2 className="flex items-center gap-2 text-base font-semibold text-ink-900">
              <FileUp size={16} className="text-accent-600" aria-hidden />
              {t('admin.file_upload.section_title')}
            </h2>
            <p className="mt-1 text-sm text-ink-500">
              {t('admin.file_upload.section_subtitle')}
            </p>
          </div>
          <Button icon={FileUp} onClick={() => setUploadOpen(true)}>
            {t('admin.file_upload.section_action')}
          </Button>
        </Card>

        {uploadOpen && (
          <FileUploadModal
            open
            onClose={() => setUploadOpen(false)}
            onUploaded={() => setReloadStatusToken((n) => n + 1)}
          />
        )}

        {/* Search section */}
        <div className="mb-4">
          <h2 className="mb-2 flex items-center gap-2 text-base font-semibold text-ink-900">
            <Database size={16} className="text-accent-600" aria-hidden />
            {t('admin.search_in_catalog')}
          </h2>
          {(status?.booksCount ?? 0) === 0 && !statusLoading && (
            <p className="mb-3 text-sm text-ink-500">
              {t('admin.empty_catalog_hint')}{' '}
              <button
                type="button"
                onClick={onSyncMaster}
                className="text-accent-600 underline hover:text-accent-700"
              >
                {t('admin.sync_action_link')}
              </button>{' '}
              {t('admin.empty_catalog_hint_2')}
            </p>
          )}
          <div className="flex h-9 max-w-xl items-center rounded-md border border-border-strong bg-elevated transition-colors focus-within:border-accent-500 focus-within:ring-2 focus-within:ring-accent-500/20">
            <Search size={16} className="ms-3 text-ink-400" aria-hidden="true" />
            <input
              type="search"
              value={query}
              onChange={(e) => onQueryChange(e.target.value)}
              placeholder={t('admin.search_placeholder')}
              className="flex-1 bg-transparent px-3 text-sm text-ink-900 outline-none placeholder:text-ink-400"
              aria-label={t('admin.search_aria')}
            />
            {searchLoading && (
              <Loader2 size={14} className="me-3 animate-spin text-ink-400" aria-hidden="true" />
            )}
          </div>
        </div>

        {searchError && (
          <Card className="mb-4 border-err-500/40 bg-err-100 p-4">
            <p className="text-sm text-err-700">{searchError}</p>
          </Card>
        )}

        {!searchError && query.trim().length > 0 && results.length === 0 && !searchLoading && (
          <p className="text-sm text-ink-500">{t('topic.list.not_found')}</p>
        )}

        {results.length > 0 && (
          <ul className="space-y-2">
            {results.map((r) => (
              <SearchResultRow
                key={r.bookId}
                result={r}
                onImport={onImport}
                isImporting={importingId === r.bookId}
              />
            ))}
          </ul>
        )}

        <ActivityLog />
      </div>
    </main>
  );
}

/**
 * Activity log - console-style блок с timeline последних admin-операций.
 * Бэк пока не отдаёт реальный лог (потребуется /api/v1/admin/shamela/activity
 * endpoint), здесь placeholder с примерами форматов. Когда бэк появится -
 * заменить items на fetch'd данные. Структура повторяет дизайн-референс v3.
 */
function ActivityLog() {
  const t = useT();
  const items: ReadonlyArray<{
    time: string;
    kind: 'ok' | 'warn' | 'err';
    message: string;
  }> = [
    { time: '14:22:08', kind: 'ok', message: 'sync-master: ничего нового (v 8517)' },
    { time: '14:18:45', kind: 'ok', message: 'import-book/1503 → 4720 стр., 239 глав' },
    { time: '14:18:45', kind: 'ok', message: 'map-book/1503 → lib_books/02bcfa43-d269…' },
    { time: '14:12:11', kind: 'warn', message: 'import-book/23901 → 6 страниц без printedPage' },
    { time: '14:02:54', kind: 'err', message: 'import-book/77810 → 422: PDF не найден на archive.org' },
  ];
  const kindClass: Record<'ok' | 'warn' | 'err', string> = {
    ok: 'text-ok-700 bg-ok-100',
    warn: 'text-warn-700 bg-warn-100',
    err: 'text-err-700 bg-err-100',
  };
  const kindLabel: Record<'ok' | 'warn' | 'err', string> = {
    ok: 'OK',
    warn: 'WARN',
    err: 'ERR',
  };
  return (
    <section className="mt-8">
      <h2 className="mb-3 text-base font-semibold text-ink-900">
        {t('admin.activity_log')}
      </h2>
      <Card className="overflow-hidden p-0">
        <ul className="divide-y divide-border font-mono text-xs">
          {items.map((it, i) => (
            <li
              key={i}
              className="flex items-baseline gap-3 px-4 py-2 text-ink-800"
            >
              <span className="text-ink-500 tabular-nums">{it.time}</span>
              <span
                className={`inline-flex h-5 items-center rounded-sm px-1.5 text-xs font-bold uppercase ${kindClass[it.kind]}`}
              >
                {kindLabel[it.kind]}
              </span>
              <span className="flex-1 break-all">{it.message}</span>
            </li>
          ))}
        </ul>
      </Card>
      <p className="mt-2 text-xs text-ink-400">
        {t('admin.activity_log_placeholder_hint')}
      </p>
    </section>
  );
}

interface StatProps {
  label: string;
  value: string;
  hint?: React.ReactNode;
}

function Stat({ label, value, hint }: StatProps) {
  return (
    <div>
      <div className="text-xs uppercase tracking-wider text-ink-500 font-semibold">
        {label}
      </div>
      <div className="mt-1 font-mono text-lg font-bold text-ink-900 tabular-nums">
        {value}
      </div>
      {hint && <div className="mt-0.5 text-xs text-ink-400">{hint}</div>}
    </div>
  );
}

interface SearchResultRowProps {
  result: SearchResult;
  onImport: (bookId: number) => void;
  isImporting: boolean;
}

function SearchResultRow({ result, onImport, isImporting }: SearchResultRowProps) {
  const t = useT();
  // dir="auto" - браузер сам определит направление по первому сильному символу.
  // Шрифт font-naskh всё равно через эвристику (dir="auto" шрифт не переключает).
  const arabicName = hasArabicScript(result.name ?? undefined);
  const arabicAuthor = hasArabicScript(result.authorName ?? undefined);

  return (
    <li>
      <Card className="flex flex-wrap items-center gap-4 p-4 transition-colors hover:border-border-strong">
        <div className="min-w-0 flex-1">
          <div
            dir="auto"
            className={
              arabicName
                ? 'font-naskh text-md font-semibold leading-snug text-ink-900'
                : 'text-base font-semibold leading-snug text-ink-900'
            }
          >
            {result.name ?? t('reader.no_book_title')}
          </div>
          <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-ink-500">
            {result.authorName && (
              <span dir="auto" className={arabicAuthor ? 'font-naskh' : ''}>
                {result.authorName}
              </span>
            )}
            <span className="font-mono text-ink-400">
              <bdi dir="ltr">id={result.bookId}</bdi>
            </span>
            <span className="font-mono text-ink-400">
              <bdi dir="ltr">major={result.majorRelease}</bdi>
            </span>
          </div>
        </div>
        {result.isMapped ? (
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1 rounded-md bg-ok-100 px-2 py-0.5 text-xs font-medium text-ok-700 border border-ok-500/40">
              <CheckCircle2 size={12} aria-hidden="true" />
              {t('admin.imported')}
            </span>
            <Link to="/books">
              <Button variant="ghost" size="sm" iconRight={ExternalLink}>
                {t('admin.in_library')}
              </Button>
            </Link>
          </div>
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
      </Card>
    </li>
  );
}

function formatError(e: unknown, fallback: string): string {
  if (e instanceof ApiError) {
    return `${e.problem.title}${e.problem.detail ? ': ' + e.problem.detail : ''}`;
  }
  if (e instanceof Error) return e.message;
  return fallback;
}

export default AdminShamelaPage;
