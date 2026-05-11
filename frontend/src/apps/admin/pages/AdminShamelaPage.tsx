import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import {
  AlertCircle,
  CheckCircle2,
  Database,
  Download,
  ExternalLink,
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

type SyncStatus = components['schemas']['SyncStatusResponse'];
type SearchResult = components['schemas']['StagingBookSearchResponse'];
type ImportBookResponse = components['schemas']['ImportBookResponse'];
type MapBookResponse = components['schemas']['MapBookResponse'];
type SyncMasterResponse = components['schemas']['SyncMasterResponse'];

const SEARCH_DEBOUNCE_MS = 300;

const DATE_FORMAT = new Intl.DateTimeFormat('ru-RU', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});

function formatDateTime(iso: string | undefined): string {
  if (!iso) return 'никогда';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return DATE_FORMAT.format(d);
}

/**
 * Эвристика арабского текста для RTL/naskh - тот же regex что в
 * BookReaderPage. Имена авторов и названия книг shamela почти все
 * на арабском, но могут попадаться римские транслиты.
 */
function isArabic(text: string | undefined): boolean {
  if (!text) return false;
  return /[؀-ۿ]/.test(text);
}

function AdminShamelaPage() {
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
        setStatusError(formatError(e, 'Не удалось загрузить статус'));
      })
      .finally(() => {
        if (!controller.signal.aborted) setStatusLoading(false);
      });
    return () => controller.abort();
  }, [reloadStatusToken]);

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
          setSearchError(formatError(e, 'Поиск не удался'));
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
  }, [query]);

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

  const onSyncMaster = async () => {
    setSyncing(true);
    try {
      const res = await apiPostRaw<SyncMasterResponse>(
        '/api/v1/admin/shamela/sync-master',
        undefined,
      );
      if (res.changed) {
        toast.success(
          `Каталог обновлён до версии ${res.currentVersion} · ` +
            `${res.booksCount} книг, ${res.authorsCount} авторов`,
        );
      } else {
        toast.info(`Каталог уже актуален (версия ${res.currentVersion})`);
      }
      setReloadStatusToken((n) => n + 1);
    } catch (e) {
      toast.error(formatError(e, 'Sync каталога не удался'));
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
        `Импортировано: ${imported.pagesCount} стр., ${imported.titlesCount} глав. ` +
          `Маплено в lib_books · откройте /books/${mapped.bookId?.slice(0, 8)}…`,
      );
      setResults((prev) =>
        prev.map((r) => (r.bookId === bookId ? { ...r, isMapped: true } : r)),
      );
      setReloadStatusToken((n) => n + 1);
    } catch (e) {
      toast.error(formatError(e, `Импорт книги ${bookId} не удался`));
    } finally {
      setImportingId(null);
    }
  };

  return (
    <main className="min-h-screen bg-slate-50/60">
      <Header />

      <div className="mx-auto max-w-[1380px] px-6 py-8">
        <div className="mb-6">
          <h1 className="flex items-center gap-2.5 text-[28px] font-bold tracking-tight text-slate-900">
            <Settings size={26} className="text-indigo-600" aria-hidden="true" />
            Админ · Shamela
          </h1>
          <p className="mt-1 text-[13px] text-slate-500">
            Импорт книг из каталога shamela.ws через desktop-API.
            Поиск в staging, импорт по одной книге за клик
          </p>
        </div>

        {/* Sync-status dashboard */}
        <Card className="mb-6 p-5">
          {statusLoading && (
            <div className="flex items-center gap-2 text-[13px] text-slate-500">
              <Loader2 size={16} className="animate-spin" aria-hidden="true" />
              Загрузка статуса
            </div>
          )}
          {statusError && (
            <div className="flex items-start gap-3 text-red-800">
              <AlertCircle size={20} className="mt-0.5 shrink-0 text-red-600" aria-hidden="true" />
              <div>
                <p className="font-semibold">Ошибка загрузки статуса</p>
                <p className="mt-1 text-[13px]">{statusError}</p>
              </div>
            </div>
          )}
          {status && (
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div className="grid flex-1 grid-cols-2 gap-x-8 gap-y-3 sm:grid-cols-4">
                <Stat
                  label="Master version"
                  value={status.masterVersion?.toString() ?? '0'}
                  hint={`Последний sync: ${formatDateTime(status.lastSyncedAt)}`}
                />
                <Stat
                  label="Категорий"
                  value={status.categoriesCount?.toString() ?? '0'}
                />
                <Stat
                  label="Авторов"
                  value={(status.authorsCount ?? 0).toLocaleString('ru-RU')}
                />
                <Stat
                  label="Книг в staging"
                  value={(status.booksCount ?? 0).toLocaleString('ru-RU')}
                  hint={`Замаплено: ${(status.mappedBooksCount ?? 0).toLocaleString('ru-RU')}`}
                />
              </div>
              <Button icon={RefreshCw} onClick={onSyncMaster} disabled={syncing}>
                {syncing ? 'Синхронизация…' : 'Синхронизировать каталог'}
              </Button>
            </div>
          )}
        </Card>

        {/* Search section */}
        <div className="mb-4">
          <h2 className="mb-2 flex items-center gap-2 text-[16px] font-semibold text-slate-900">
            <Database size={18} className="text-indigo-600" aria-hidden="true" />
            Поиск в каталоге shamela
          </h2>
          {(status?.booksCount ?? 0) === 0 && !statusLoading && (
            <p className="mb-3 text-[13px] text-slate-500">
              Каталог пуст. Сначала запусти{' '}
              <button
                type="button"
                onClick={onSyncMaster}
                className="text-indigo-600 underline hover:text-indigo-800"
              >
                синхронизацию
              </button>{' '}
              чтобы загрузить ~8500 книг в staging
            </p>
          )}
          <div className="flex h-9 max-w-xl items-center rounded-md border border-slate-300 bg-white transition-colors focus-within:border-indigo-500 focus-within:ring-2 focus-within:ring-indigo-500/20">
            <Search size={16} className="ml-3 text-slate-400" aria-hidden="true" />
            <input
              type="search"
              value={query}
              onChange={(e) => onQueryChange(e.target.value)}
              placeholder="Поиск по названию или id · до 50 результатов"
              className="flex-1 bg-transparent px-3 text-[13px] text-slate-900 outline-none placeholder:text-slate-400"
              aria-label="Поиск книг shamela"
            />
            {searchLoading && (
              <Loader2 size={14} className="mr-3 animate-spin text-slate-400" aria-hidden="true" />
            )}
          </div>
        </div>

        {searchError && (
          <Card className="mb-4 border-red-200 bg-red-50 p-4">
            <p className="text-[13px] text-red-800">{searchError}</p>
          </Card>
        )}

        {!searchError && query.trim().length > 0 && results.length === 0 && !searchLoading && (
          <p className="text-[13px] text-slate-500">Ничего не найдено по запросу "{query}"</p>
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
      </div>
    </main>
  );
}

interface StatProps {
  label: string;
  value: string;
  hint?: string;
}

function Stat({ label, value, hint }: StatProps) {
  return (
    <div>
      <div className="text-[11px] uppercase tracking-wide text-slate-500">{label}</div>
      <div className="mt-0.5 font-mono text-[20px] font-bold text-slate-900">{value}</div>
      {hint && <div className="mt-0.5 text-[11px] text-slate-400">{hint}</div>}
    </div>
  );
}

interface SearchResultRowProps {
  result: SearchResult;
  onImport: (bookId: number) => void;
  isImporting: boolean;
}

function SearchResultRow({ result, onImport, isImporting }: SearchResultRowProps) {
  const arabicName = isArabic(result.name);
  const arabicAuthor = isArabic(result.authorName ?? undefined);

  return (
    <li>
      <Card className="flex flex-wrap items-center gap-4 p-4 transition-colors hover:border-slate-300">
        <div className="min-w-0 flex-1">
          <div
            className={
              arabicName
                ? 'font-naskh text-[18px] font-semibold leading-snug text-slate-900'
                : 'text-[15px] font-semibold leading-snug text-slate-900'
            }
            dir={arabicName ? 'rtl' : 'ltr'}
          >
            {result.name ?? '(без названия)'}
          </div>
          <div className="mt-1 flex flex-wrap items-center gap-2 text-[12px] text-slate-500">
            {result.authorName && (
              <span
                className={arabicAuthor ? 'font-naskh' : ''}
                dir={arabicAuthor ? 'rtl' : 'ltr'}
              >
                {result.authorName}
              </span>
            )}
            <span className="font-mono text-slate-400">id={result.bookId}</span>
            <span className="font-mono text-slate-400">major={result.majorRelease}</span>
          </div>
        </div>
        {result.isMapped ? (
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1 rounded-md bg-emerald-50 px-2 py-0.5 text-[11px] font-medium text-emerald-700 border border-emerald-200">
              <CheckCircle2 size={12} aria-hidden="true" />
              Импортирована
            </span>
            <Link to="/books">
              <Button variant="ghost" size="sm" iconRight={ExternalLink}>
                В библиотеке
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
            {isImporting ? 'Импорт…' : 'Импортировать'}
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
