import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import {
  AlertCircle,
  ArrowLeft,
  BookOpen,
  ChevronDown,
  CircleSlash,
  Database,
  Download,
  Loader2,
  Pause,
  Play,
  ScrollText,
  Search,
  Sparkles,
  Users,
} from 'lucide-react';
import Header from '@/shared/components/layout/Header';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import Field from '@/shared/components/ui/Field';
import { apiGetRaw, apiPostRaw, ApiError, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { hasArabicScript, useT, useNumberFormat, type DictKey } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type CatalogEntry = components['schemas']['AlminasaCatalogEntryResponse'];
type ImportStatus = components['schemas']['AlminasaImportStatusResponse'];
type CrawlStatus = components['schemas']['AlminasaCrawlStatusResponse'];
type DryRun = components['schemas']['AlminasaDryRunResponse'];

const POLL_MS = 3000;

/** Локальное состояние dry-run превью одного хадиса. 404/422 — inline, не toast. */
type DryRunState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'not_found' }
  | { kind: 'invalid'; message: string }
  | { kind: 'error'; message: string }
  | { kind: 'success'; data: DryRun };

/**
 * AdminHadithImportPage (route `/admin/hadith-import`) — админка импорта
 * застейдженных alminasa-данных в доменные `hd_*` (ADR-060, План 5).
 *
 * Четыре секции:
 *  1. Краулер — статус (IDLE/RUNNING/PAUSED/FAILED/COMPLETED) + прогресс
 *     fetched/total + start/pause. Поллинг 3с ПОКА RUNNING.
 *  2. Каталог — 12 сборников, прогресс staged→mapped, кнопка «Маппинг»
 *     per-book + «Импорт рави» + «Маппинг всех». Импорт-кнопки disabled
 *     пока crawl RUNNING (мягкий guard, фикс M3) И пока импорт RUNNING.
 *  3. Статус импорта — IDLE: сводка последнего прогона; RUNNING: live
 *     processedSoFar + спиннер. Поллинг 3с пока RUNNING; по завершении —
 *     рефетч каталога (mappedCount обновился).
 *  4. Dry-run — превью маппинга одного хадиса ДО записи: поля, цепь
 *     иснада, counts сателлитов. 404/422 — явные inline-сообщения.
 *
 * Философия (бриф владельца): наполнение ПРОВЕРЯЕМОЕ и ПОФАЗНОЕ — dry-run
 * показывает результат маппинга прежде чем что-либо записывается в БД.
 */
function AdminHadithImportPage() {
  const t = useT();

  const [catalog, setCatalog] = useState<CatalogEntry[] | null>(null);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [importStatus, setImportStatus] = useState<ImportStatus | null>(null);
  const [crawlStatus, setCrawlStatus] = useState<CrawlStatus | null>(null);

  /** Какое именно действие импорта сейчас запускается (для disabled/spinner). */
  const [launchingAction, setLaunchingAction] = useState<string | null>(null);
  const [crawlBusy, setCrawlBusy] = useState(false);

  const importRunning = importStatus?.status === 'RUNNING';
  const crawlRunning = crawlStatus?.status === 'RUNNING';

  const refetchCatalog = useCallback(async () => {
    try {
      const data = await apiGetRaw<CatalogEntry[]>('/api/v1/admin/alminasa/catalog');
      setCatalog(data ?? []);
      setCatalogError(null);
    } catch (e) {
      setCatalogError(formatApiError(e, t('admin.hadith.catalog_load_failed')));
    }
  }, [t]);

  const refetchImportStatus = useCallback(async () => {
    try {
      const data = await apiGetRaw<ImportStatus>('/api/v1/admin/alminasa/import/status');
      setImportStatus(data);
    } catch {
      // status-поллинг тихий: не зашумляем UI временной ошибкой, следующий tick дочинит
    }
  }, []);

  const refetchCrawlStatus = useCallback(async () => {
    try {
      const data = await apiGetRaw<CrawlStatus>('/api/v1/admin/alminasa/crawl/status');
      setCrawlStatus(data);
    } catch {
      // crawl-поллинг тихий, как и import-status
    }
  }, []);

  // Начальная загрузка всех трёх источников. setState внутри refetch-колбэков
  // асинхронный (после await), не синхронный — легитимный mount-fetch; правило
  // глушим точечно, как и в других fetch-эффектах проекта (С64).
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refetchCatalog();
    void refetchImportStatus();
    void refetchCrawlStatus();
  }, [refetchCatalog, refetchImportStatus, refetchCrawlStatus]);

  // Поллинг статуса импорта пока RUNNING. По переходу RUNNING→IDLE рефетчим
  // каталог (mappedCount обновился). prevRunning отслеживает фронт перехода.
  const prevImportRunning = useRef(false);
  useEffect(() => {
    if (!importRunning) {
      // Переход RUNNING→IDLE: финальный рефетч каталога + статуса.
      if (prevImportRunning.current) {
        prevImportRunning.current = false;
        void refetchCatalog();
        void refetchImportStatus();
      }
      return;
    }
    prevImportRunning.current = true;
    const id = setInterval(() => {
      void refetchImportStatus();
    }, POLL_MS);
    return () => clearInterval(id);
  }, [importRunning, refetchImportStatus, refetchCatalog]);

  // Поллинг статуса краулера пока RUNNING.
  useEffect(() => {
    if (!crawlRunning) return;
    const id = setInterval(() => {
      void refetchCrawlStatus();
    }, POLL_MS);
    return () => clearInterval(id);
  }, [crawlRunning, refetchCrawlStatus]);

  const onCrawlStart = async () => {
    setCrawlBusy(true);
    try {
      const next = await apiPostRaw<CrawlStatus>('/api/v1/admin/alminasa/crawl/start', undefined);
      setCrawlStatus(next);
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        toast.info(t('admin.hadith.crawl_already_running'));
        void refetchCrawlStatus();
      } else {
        toast.error(formatApiError(e, t('admin.hadith.crawl_start_failed')));
      }
    } finally {
      setCrawlBusy(false);
    }
  };

  const onCrawlPause = async () => {
    setCrawlBusy(true);
    try {
      const next = await apiPostRaw<CrawlStatus>('/api/v1/admin/alminasa/crawl/pause', undefined);
      setCrawlStatus(next);
    } catch (e) {
      toast.error(formatApiError(e, t('admin.hadith.crawl_pause_failed')));
    } finally {
      setCrawlBusy(false);
    }
  };

  /**
   * Общий запуск импорта (рави / хадисы-все / хадисы-по-сборнику). После
   * POST — немедленный рефетч статуса (он отдаёт RUNNING-claim), дальнейший
   * прогресс снимает поллинг. 409 → toast «импорт уже идёт».
   */
  const launchImport = async (action: string, path: string) => {
    setLaunchingAction(action);
    try {
      const next = await apiPostRaw<ImportStatus>(path, undefined);
      setImportStatus(next);
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        toast.info(t('admin.hadith.import_already_running'));
        void refetchImportStatus();
      } else {
        toast.error(formatApiError(e, t('admin.hadith.import_launch_failed')));
      }
    } finally {
      setLaunchingAction(null);
    }
  };

  const onImportNarrators = () =>
    launchImport('narrators', '/api/v1/admin/alminasa/import/narrators');
  const onImportAllHadiths = () =>
    launchImport('all', '/api/v1/admin/alminasa/import/hadiths');
  const onImportBook = (bookId: number) =>
    launchImport(`book-${bookId}`, `/api/v1/admin/alminasa/import/hadiths?bookId=${bookId}`);

  // Импорт-кнопки заблокированы пока крутится crawl ИЛИ импорт.
  const importDisabled = crawlRunning || importRunning;
  const importDisabledHint = crawlRunning
    ? t('admin.hadith.import_blocked_by_crawl')
    : importRunning
      ? t('admin.hadith.import_blocked_running')
      : undefined;

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

        <header className="mb-6">
          <div className="mb-1 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-500">
            <Sparkles size={13} aria-hidden /> {t('admin.hadith.eyebrow')}
          </div>
          <h1 className="font-serif text-[28px] font-semibold leading-tight tracking-tight text-ink-900">
            {t('admin.hadith.title')}
          </h1>
          <p className="mt-1.5 max-w-[680px] text-sm text-ink-500">
            {t('admin.hadith.subtitle')}
          </p>
        </header>

        <div className="flex flex-col gap-8">
          <CrawlerSection
            status={crawlStatus}
            busy={crawlBusy}
            onStart={onCrawlStart}
            onPause={onCrawlPause}
          />

          <CatalogSection
            catalog={catalog}
            error={catalogError}
            importDisabled={importDisabled}
            importDisabledHint={importDisabledHint}
            launchingAction={launchingAction}
            onImportNarrators={onImportNarrators}
            onImportAllHadiths={onImportAllHadiths}
            onImportBook={onImportBook}
          />

          <ImportStatusSection status={importStatus} />

          <DryRunSection />
        </div>
      </div>
    </main>
  );
}

// ====================================================================
//                          Crawler section
// ====================================================================

interface CrawlerSectionProps {
  status: CrawlStatus | null;
  busy: boolean;
  onStart: () => void;
  onPause: () => void;
}

function CrawlerSection({ status, busy, onStart, onPause }: CrawlerSectionProps) {
  const t = useT();
  const formatNumber = useNumberFormat();
  const running = status?.status === 'RUNNING';
  const fetched = status?.fetchedCount ?? 0;
  const total = status?.totalHits ?? 0;
  const pct = total > 0 ? Math.min(100, Math.round((fetched / total) * 100)) : 0;

  return (
    <section>
      <SectionHeader icon={Database} title={t('admin.hadith.crawler_title')} />
      <Card className="p-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0 flex-1">
            <div className="mb-2 flex items-center gap-2">
              <CrawlStatusBadge status={status?.status} />
              {status?.startedAt && (
                <span className="text-[11px] text-ink-400">
                  <bdi dir="ltr">{status.startedAt}</bdi>
                </span>
              )}
            </div>

            <div className="flex items-baseline gap-2 font-mono text-sm text-ink-700 tabular-nums">
              <bdi dir="ltr">{formatNumber(fetched)}</bdi>
              <span className="text-ink-400">/</span>
              <bdi dir="ltr">{total > 0 ? formatNumber(total) : '—'}</bdi>
              {total > 0 && <span className="text-xs text-ink-400">({pct}%)</span>}
            </div>

            {total > 0 && (
              <div className="mt-2 h-1.5 w-full max-w-[420px] overflow-hidden rounded-full bg-ink-100">
                <div
                  className="h-full rounded-full bg-accent-500 transition-[width]"
                  style={{ width: `${pct}%` }}
                />
              </div>
            )}

            <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-ink-500">
              <span>
                {t('admin.hadith.crawl_cursor')}:{' '}
                <span className="font-mono text-ink-600">
                  <bdi dir="ltr">{status?.lastSortId ?? '—'}</bdi>
                </span>
              </span>
            </div>

            {status?.error && (
              <p className="mt-2 text-sm text-err-700">{status.error}</p>
            )}
          </div>

          <div className="flex shrink-0 items-center gap-2">
            <Button icon={Play} onClick={onStart} disabled={busy || running}>
              {t('admin.hadith.crawl_start')}
            </Button>
            <Button variant="secondary" icon={Pause} onClick={onPause} disabled={busy || !running}>
              {t('admin.hadith.crawl_pause')}
            </Button>
          </div>
        </div>
      </Card>
    </section>
  );
}

function CrawlStatusBadge({ status }: { status: string | undefined }) {
  const t = useT();
  const s = status ?? 'IDLE';
  const className = crawlBadgeClass(s);
  const labelKey: DictKey =
    s === 'RUNNING'
      ? 'admin.hadith.status_running'
      : s === 'PAUSED'
        ? 'admin.hadith.status_paused'
        : s === 'FAILED'
          ? 'admin.hadith.status_failed'
          : s === 'COMPLETED'
            ? 'admin.hadith.status_completed'
            : 'admin.hadith.status_idle';
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-semibold uppercase tracking-wider ${className}`}
    >
      {s === 'RUNNING' && <Loader2 size={11} className="animate-spin" aria-hidden />}
      {t(labelKey)}
    </span>
  );
}

function crawlBadgeClass(status: string): string {
  switch (status) {
    case 'RUNNING':
      return 'bg-accent-50 text-accent-700';
    case 'PAUSED':
      return 'bg-amber-100 text-amber-700';
    case 'FAILED':
      return 'bg-rose-100 text-rose-700';
    case 'COMPLETED':
      return 'bg-emerald-100 text-emerald-700';
    default:
      return 'bg-ink-100 text-ink-600';
  }
}

// ====================================================================
//                          Catalog section
// ====================================================================

interface CatalogSectionProps {
  catalog: CatalogEntry[] | null;
  error: string | null;
  importDisabled: boolean;
  importDisabledHint: string | undefined;
  launchingAction: string | null;
  onImportNarrators: () => void;
  onImportAllHadiths: () => void;
  onImportBook: (bookId: number) => void;
}

function CatalogSection({
  catalog,
  error,
  importDisabled,
  importDisabledHint,
  launchingAction,
  onImportNarrators,
  onImportAllHadiths,
  onImportBook,
}: CatalogSectionProps) {
  const t = useT();

  return (
    <section>
      <SectionHeader icon={BookOpen} title={t('admin.hadith.catalog_title')} />

      {/* Глобальные действия импорта */}
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <DisabledHintWrap disabled={importDisabled} hint={importDisabledHint}>
          <Button
            variant="secondary"
            icon={Users}
            onClick={onImportNarrators}
            disabled={importDisabled || launchingAction === 'narrators'}
          >
            {launchingAction === 'narrators' && (
              <Loader2 size={14} className="animate-spin" aria-hidden />
            )}
            {t('admin.hadith.import_narrators')}
          </Button>
        </DisabledHintWrap>
        <DisabledHintWrap disabled={importDisabled} hint={importDisabledHint}>
          <Button
            icon={Download}
            onClick={onImportAllHadiths}
            disabled={importDisabled || launchingAction === 'all'}
          >
            {launchingAction === 'all' && (
              <Loader2 size={14} className="animate-spin" aria-hidden />
            )}
            {t('admin.hadith.import_all_books')}
          </Button>
        </DisabledHintWrap>
      </div>

      {error && (
        <Card className="border-err-500/40 bg-err-100 p-5">
          <div className="flex items-start gap-3 text-err-700">
            <AlertCircle size={20} className="mt-0.5 shrink-0" aria-hidden />
            <div className="text-sm">{error}</div>
          </div>
        </Card>
      )}

      {!error && catalog === null && (
        <div className="flex items-center gap-2 py-8 text-sm text-ink-500">
          <Loader2 size={16} className="animate-spin" aria-hidden /> {t('common.loading')}
        </div>
      )}

      {!error && catalog !== null && (
        <CatalogTable
          catalog={catalog}
          importDisabled={importDisabled}
          importDisabledHint={importDisabledHint}
          launchingAction={launchingAction}
          onImportBook={onImportBook}
        />
      )}
    </section>
  );
}

interface CatalogTableProps {
  catalog: CatalogEntry[];
  importDisabled: boolean;
  importDisabledHint: string | undefined;
  launchingAction: string | null;
  onImportBook: (bookId: number) => void;
}

function CatalogTable({
  catalog,
  importDisabled,
  importDisabledHint,
  launchingAction,
  onImportBook,
}: CatalogTableProps) {
  const t = useT();
  const gridCols = '64px 1fr 120px 120px 160px';
  return (
    <div className="overflow-x-auto rounded-lg border border-border bg-elevated">
      <div className="min-w-[720px]">
        <div
          className="sticky top-0 z-[1] grid items-center gap-3 border-b border-border bg-sunken px-4 py-2 text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500"
          style={{ gridTemplateColumns: gridCols }}
        >
          <span>{t('admin.hadith.col_book_id')}</span>
          <span>{t('admin.hadith.col_collection')}</span>
          <span>{t('admin.hadith.col_staged')}</span>
          <span>{t('admin.hadith.col_mapped')}</span>
          <span>{t('admin.hadith.col_action')}</span>
        </div>
        <ul className="divide-y divide-border">
          {catalog.map((entry) => (
            <CatalogRow
              key={entry.bookId}
              entry={entry}
              gridCols={gridCols}
              importDisabled={importDisabled}
              importDisabledHint={importDisabledHint}
              launching={launchingAction === `book-${entry.bookId}`}
              onImportBook={onImportBook}
            />
          ))}
        </ul>
      </div>
    </div>
  );
}

interface CatalogRowProps {
  entry: CatalogEntry;
  gridCols: string;
  importDisabled: boolean;
  importDisabledHint: string | undefined;
  launching: boolean;
  onImportBook: (bookId: number) => void;
}

function CatalogRow({
  entry,
  gridCols,
  importDisabled,
  importDisabledHint,
  launching,
  onImportBook,
}: CatalogRowProps) {
  const t = useT();
  const formatNumber = useNumberFormat();
  const staged = entry.stagedCount ?? 0;
  const mapped = entry.mappedCount ?? 0;
  const pct = staged > 0 ? Math.min(100, Math.round((mapped / staged) * 100)) : 0;

  return (
    <li>
      <div
        className="grid items-center gap-3 px-4 py-2.5 transition-colors hover:bg-sunken/60"
        style={{ gridTemplateColumns: gridCols }}
      >
        <div className="font-mono text-xs text-ink-500 tabular-nums">
          <bdi dir="ltr">{entry.bookId}</bdi>
        </div>
        <div className="min-w-0">
          <div dir="rtl" className="truncate font-naskh text-base text-ink-900" title={entry.nameAr ?? ''}>
            {entry.nameAr ?? '—'}
          </div>
          <div dir="auto" className="truncate text-xs text-ink-500" title={entry.nameRu ?? ''}>
            {entry.nameRu ?? '—'}
          </div>
        </div>
        <div className="font-mono text-xs text-ink-700 tabular-nums">
          <bdi dir="ltr">{formatNumber(staged)}</bdi>
        </div>
        <div className="min-w-0">
          <div className="font-mono text-xs text-ink-700 tabular-nums">
            <bdi dir="ltr">{formatNumber(mapped)}</bdi>
          </div>
          {staged > 0 && (
            <div className="mt-1 h-1 w-full max-w-[88px] overflow-hidden rounded-full bg-ink-100">
              <div
                className="h-full rounded-full bg-accent-500"
                style={{ width: `${pct}%` }}
              />
            </div>
          )}
        </div>
        <div className="flex justify-end">
          <DisabledHintWrap disabled={importDisabled} hint={importDisabledHint}>
            <Button
              size="sm"
              icon={Download}
              onClick={() => entry.bookId !== undefined && onImportBook(entry.bookId)}
              disabled={importDisabled || launching}
            >
              {launching && <Loader2 size={13} className="animate-spin" aria-hidden />}
              {t('admin.hadith.map_book')}
            </Button>
          </DisabledHintWrap>
        </div>
      </div>
    </li>
  );
}

// ====================================================================
//                          Import-status section
// ====================================================================

interface ImportStatusSectionProps {
  status: ImportStatus | null;
}

function ImportStatusSection({ status }: ImportStatusSectionProps) {
  const t = useT();
  const formatNumber = useNumberFormat();

  if (status === null) {
    return (
      <section>
        <SectionHeader icon={ScrollText} title={t('admin.hadith.import_status_title')} />
        <div className="flex items-center gap-2 py-4 text-sm text-ink-500">
          <Loader2 size={16} className="animate-spin" aria-hidden /> {t('common.loading')}
        </div>
      </section>
    );
  }

  const running = status.status === 'RUNNING';

  return (
    <section>
      <SectionHeader icon={ScrollText} title={t('admin.hadith.import_status_title')} />
      <Card className="p-5">
        {running ? (
          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-2 text-sm font-semibold text-accent-700">
              <Loader2 size={16} className="animate-spin" aria-hidden />
              {t('admin.hadith.import_running')}
            </div>
            <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-ink-700">
              <span>
                {t('admin.hadith.import_kind')}:{' '}
                <span className="font-semibold">{importKindLabel(status.kind, t)}</span>
              </span>
              {status.bookIdFilter != null && (
                <span>
                  {t('admin.hadith.import_book_filter')}:{' '}
                  <span className="font-mono tabular-nums">
                    <bdi dir="ltr">{status.bookIdFilter}</bdi>
                  </span>
                </span>
              )}
              <span>
                {t('admin.hadith.import_processed')}:{' '}
                <span className="font-mono font-semibold tabular-nums">
                  <bdi dir="ltr">{formatNumber(status.processedSoFar ?? 0)}</bdi>
                </span>
              </span>
            </div>
          </div>
        ) : (
          <ImportSummary status={status} />
        )}
      </Card>
    </section>
  );
}

function ImportSummary({ status }: { status: ImportStatus }) {
  const t = useT();
  const formatNumber = useNumberFormat();
  const failures = status.failures ?? [];
  const neverRan =
    status.kind == null &&
    !status.error &&
    (status.narratorsProcessed ?? 0) === 0 &&
    (status.hadithsProcessed ?? 0) === 0 &&
    failures.length === 0;

  if (neverRan) {
    return <p className="text-sm text-ink-500">{t('admin.hadith.import_never_ran')}</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      {status.error && (
        <div className="flex items-start gap-2 rounded-md border border-err-500/40 bg-err-100 px-3 py-2 text-err-700">
          <AlertCircle size={16} className="mt-0.5 shrink-0" aria-hidden />
          <div className="text-sm">{status.error}</div>
        </div>
      )}

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <SummaryStat
          label={t('admin.hadith.summary_narrators')}
          value={formatNumber(status.narratorsProcessed ?? 0)}
          failed={status.narratorsFailed ?? 0}
          formatNumber={formatNumber}
        />
        <SummaryStat
          label={t('admin.hadith.summary_hadiths')}
          value={formatNumber(status.hadithsProcessed ?? 0)}
          failed={status.hadithsFailed ?? 0}
          formatNumber={formatNumber}
        />
        <SummaryStat
          label={t('admin.hadith.summary_crossrefs')}
          value={formatNumber(status.crossrefsResolved ?? 0)}
          formatNumber={formatNumber}
        />
        <SummaryStat
          label={t('admin.hadith.summary_relations')}
          value={formatNumber(status.relationsResolved ?? 0)}
          formatNumber={formatNumber}
        />
      </div>

      {failures.length > 0 && <FailuresList failures={failures} />}
    </div>
  );
}

interface SummaryStatProps {
  label: string;
  value: string;
  failed?: number;
  formatNumber: (n: number) => string;
}

function SummaryStat({ label, value, failed, formatNumber }: SummaryStatProps) {
  return (
    <div className="min-w-0">
      <div className="text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500">
        {label}
      </div>
      <div className="mt-0.5 font-mono text-lg font-bold leading-none text-ink-900 tabular-nums">
        <bdi dir="ltr">{value}</bdi>
      </div>
      {failed != null && failed > 0 && (
        <div className="mt-0.5 font-mono text-[11px] text-err-700 tabular-nums">
          <bdi dir="ltr">−{formatNumber(failed)}</bdi>
        </div>
      )}
    </div>
  );
}

function FailuresList({ failures }: { failures: string[] }) {
  const t = useT();
  const [open, setOpen] = useState(false);
  return (
    <div className="rounded-md border border-border bg-sunken">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="flex w-full items-center gap-2 px-3 py-2 text-start text-sm font-semibold text-ink-700 transition-colors hover:text-ink-900"
      >
        <ChevronDown
          size={15}
          aria-hidden
          className={`transition-transform ${open ? 'rotate-180' : ''}`}
        />
        {t('admin.hadith.summary_failures').replace('{count}', String(failures.length))}
      </button>
      {open && (
        <ul className="max-h-60 space-y-1 overflow-y-auto border-t border-border px-3 py-2">
          {failures.map((f, i) => (
            <li key={`${i}-${f}`} className="font-mono text-[11px] leading-relaxed text-ink-600">
              {f}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ====================================================================
//                          Dry-run section
// ====================================================================

function DryRunSection() {
  const t = useT();
  const [input, setInput] = useState('');
  const [activeId, setActiveId] = useState<string | null>(null);
  const [state, setState] = useState<DryRunState>({ kind: 'idle' });

  const runDryRun = async () => {
    const id = input.trim();
    if (!id) return;
    setActiveId(id);
    setState({ kind: 'loading' });
    try {
      const data = await apiGetRaw<DryRun>(
        `/api/v1/admin/alminasa/dry-run/${encodeURIComponent(id)}`,
      );
      setState({ kind: 'success', data });
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        setState({ kind: 'not_found' });
      } else if (e instanceof ApiError && e.status === 422) {
        setState({
          kind: 'invalid',
          message: e.problem.detail || e.problem.title || t('admin.hadith.dry_run_invalid'),
        });
      } else {
        setState({ kind: 'error', message: formatApiError(e, t('admin.hadith.dry_run_failed')) });
      }
    }
  };

  return (
    <section>
      <SectionHeader icon={Search} title={t('admin.hadith.dry_run_title')} />
      <p className="mb-3 text-sm text-ink-500">{t('admin.hadith.dry_run_hint')}</p>

      <form
        className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-end"
        onSubmit={(e) => {
          e.preventDefault();
          void runDryRun();
        }}
      >
        <div className="min-w-0 sm:w-64">
          <Field label={t('admin.hadith.dry_run_id_label')}>
            <Field.Input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="146-1"
              dir="ltr"
            />
          </Field>
        </div>
        <div className="shrink-0">
          <Button
            type="submit"
            icon={state.kind === 'loading' ? undefined : Search}
            disabled={input.trim() === '' || state.kind === 'loading'}
          >
            {state.kind === 'loading' && (
              <Loader2 size={15} className="animate-spin" aria-hidden />
            )}
            {t('admin.hadith.dry_run_preview')}
          </Button>
        </div>
      </form>

      {/* key-remount панели по hadithId — сбрасывает collapsible-состояние
          между разными хадисами */}
      {state.kind !== 'idle' && (
        <DryRunPanel key={activeId ?? 'none'} state={state} />
      )}
    </section>
  );
}

function DryRunPanel({ state }: { state: Exclude<DryRunState, { kind: 'idle' }> }) {
  const t = useT();

  if (state.kind === 'loading') {
    return (
      <div className="flex items-center gap-2 py-6 text-sm text-ink-500">
        <Loader2 size={16} className="animate-spin" aria-hidden /> {t('common.loading')}
      </div>
    );
  }

  if (state.kind === 'not_found') {
    return (
      <div className="flex flex-col items-center gap-3 rounded-lg border border-dashed border-border-strong bg-elevated px-6 py-10 text-center">
        <span className="grid h-12 w-12 place-items-center rounded-full bg-warn-100 text-warn-700">
          <CircleSlash size={22} aria-hidden />
        </span>
        <p className="text-sm text-ink-500">{t('admin.hadith.dry_run_not_found')}</p>
      </div>
    );
  }

  if (state.kind === 'invalid' || state.kind === 'error') {
    return (
      <Card className="border-err-500/40 bg-err-100 p-5">
        <div className="flex items-start gap-3 text-err-700">
          <AlertCircle size={20} className="mt-0.5 shrink-0" aria-hidden />
          <div className="text-sm">{state.message}</div>
        </div>
      </Card>
    );
  }

  return <DryRunResult data={state.data} />;
}

function DryRunResult({ data }: { data: DryRun }) {
  const t = useT();
  const formatNumber = useNumberFormat();
  const arabicMatn = hasArabicScript(data.matnPreview ?? undefined);

  return (
    <div className="flex flex-col gap-5 rounded-lg border border-border bg-elevated p-5">
      {/* Hadith fields */}
      <div className="flex flex-wrap gap-x-6 gap-y-2 text-sm">
        <FieldPair label={t('admin.hadith.dr_external_id')} value={data.externalId} mono />
        <FieldPair label={t('admin.hadith.dr_collection')} value={data.collectionSlug} mono />
        <FieldPair label={t('admin.hadith.dr_number')} value={data.primaryNumber?.toString()} mono />
        <FieldPair label={t('admin.hadith.dr_status')} value={data.status} />
        <FieldPair label={t('admin.hadith.dr_type')} value={data.hadithType} arabic />
        {data.chapterAr && (
          <FieldPair label={t('admin.hadith.dr_chapter')} value={data.chapterAr} arabic />
        )}
      </div>

      {/* Matn preview */}
      {data.matnPreview && (
        <div>
          <div className="mb-1 text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500">
            {t('admin.hadith.dr_matn')}
          </div>
          <p
            dir={arabicMatn ? 'rtl' : 'auto'}
            className={`leading-loose text-ink-900 ${arabicMatn ? 'font-arabic text-lg' : 'text-base'}`}
          >
            {data.matnPreview}
          </p>
        </div>
      )}

      {/* Chain */}
      <div>
        <div className="mb-1.5 text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500">
          {t('admin.hadith.dr_chain')}
        </div>
        <ChainTable chain={data.chain ?? []} />
      </div>

      {/* Satellite counts */}
      <div className="grid grid-cols-2 gap-3 border-t border-border pt-4 sm:grid-cols-4">
        <SummaryStat
          label={t('admin.hadith.dr_editions')}
          value={formatNumber(data.editionsCount ?? 0)}
          formatNumber={formatNumber}
        />
        <SummaryStat
          label={t('admin.hadith.dr_crossrefs')}
          value={formatNumber(data.crossrefsCount ?? 0)}
          formatNumber={formatNumber}
        />
        <SummaryStat
          label={t('admin.hadith.dr_rulings')}
          value={formatNumber(data.rulingsCount ?? 0)}
          formatNumber={formatNumber}
        />
        <SummaryStat
          label={t('admin.hadith.dr_explanations')}
          value={formatNumber(data.explanationsCount ?? 0)}
          formatNumber={formatNumber}
        />
      </div>
    </div>
  );
}

function ChainTable({ chain }: { chain: components['schemas']['ChainLink'][] }) {
  const t = useT();
  if (chain.length === 0) {
    return (
      <div className="rounded-md border border-dashed border-border-strong bg-sunken p-4 text-center text-sm text-ink-500">
        {t('admin.hadith.dr_chain_empty')}
      </div>
    );
  }
  const gridCols = '56px 1fr 120px';
  return (
    <div className="overflow-hidden rounded-md border border-border">
      <div
        className="grid gap-3 border-b border-border bg-sunken px-3 py-1.5 text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500"
        style={{ gridTemplateColumns: gridCols }}
      >
        <span>{t('admin.hadith.dr_chain_position')}</span>
        <span>{t('admin.hadith.dr_chain_narrator')}</span>
        <span>{t('admin.hadith.dr_chain_formula')}</span>
      </div>
      <ul className="divide-y divide-border">
        {chain.map((link) => (
          <li
            key={`${link.position ?? 0}-${link.externalId ?? ''}`}
            className="grid items-center gap-3 px-3 py-1.5"
            style={{ gridTemplateColumns: gridCols }}
          >
            <span className="font-mono text-xs text-ink-500 tabular-nums">
              <bdi dir="ltr">{link.position ?? 0}</bdi>
            </span>
            <span dir="rtl" className="truncate font-naskh text-sm text-ink-900" title={link.nameAr ?? ''}>
              {link.nameAr ?? '—'}
            </span>
            <span dir="rtl" className="truncate font-naskh text-sm text-ink-600" title={link.formula ?? ''}>
              {link.formula ?? '—'}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

// ====================================================================
//                          Shared sub-components
// ====================================================================

interface SectionHeaderProps {
  icon: typeof BookOpen;
  title: string;
}

function SectionHeader({ icon: Icon, title }: SectionHeaderProps) {
  return (
    <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-ink-900">
      <Icon size={15} aria-hidden className="text-ink-500" />
      {title}
    </h2>
  );
}

interface FieldPairProps {
  label: string;
  value: string | undefined;
  mono?: boolean;
  arabic?: boolean;
}

function FieldPair({ label, value, mono, arabic }: FieldPairProps) {
  if (!value) return null;
  return (
    <div className="flex items-baseline gap-1.5">
      <span className="text-[11px] font-semibold uppercase tracking-wider text-ink-500">
        {label}
      </span>
      <span
        dir={arabic ? 'rtl' : mono ? 'ltr' : 'auto'}
        className={`text-ink-800 ${mono ? 'font-mono text-xs tabular-nums' : arabic ? 'font-naskh' : 'text-sm'}`}
      >
        {value}
      </span>
    </div>
  );
}

interface DisabledHintWrapProps {
  disabled: boolean;
  hint: string | undefined;
  children: React.ReactNode;
}

/**
 * Tooltip-обёртка для disabled-кнопки: Button при disabled ставит
 * pointer-events-none, поэтому `title` на самой кнопке не сработает.
 * Вешаем title на родительский span (он принимает pointer-события).
 */
function DisabledHintWrap({ disabled, hint, children }: DisabledHintWrapProps) {
  return (
    <span title={disabled && hint ? hint : undefined} className="inline-flex">
      {children}
    </span>
  );
}

// ====================================================================
//                          Helpers
// ====================================================================

function importKindLabel(kind: string | undefined, t: (k: DictKey) => string): string {
  switch (kind) {
    case 'NARRATORS':
      return t('admin.hadith.kind_narrators');
    case 'HADITHS':
      return t('admin.hadith.kind_hadiths');
    case 'ALL':
      return t('admin.hadith.kind_all');
    default:
      return kind ?? '—';
  }
}

export default AdminHadithImportPage;
