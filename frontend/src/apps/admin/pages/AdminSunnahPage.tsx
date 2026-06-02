import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  BookOpen,
  CheckCircle2,
  Download,
  Loader2,
  Network,
  ScrollText,
  ServerCrash,
  X,
} from 'lucide-react';
import Header from '@/shared/components/layout/Header';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import Modal from '@/shared/components/ui/Modal';
import FilterChips from '@/shared/components/ui/FilterChips';
import LoadMoreButton from '@/shared/components/ui/LoadMoreButton';
import { apiGetRaw, apiPostRaw, ApiError, formatApiError } from '@/shared/api/client';
import { invalidateCache } from '@/shared/hooks/queryCache';
import { usePagedSearch } from '@/shared/hooks/usePagedSearch';
import { useIsMobile } from '@/shared/hooks/useViewport';
import { toast } from '@/shared/stores/toastStore';
import { hasArabicScript, useT, type DictKey } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type CollectionPreview = components['schemas']['SunnahCollectionPreview'];
type BrowseItem = components['schemas']['SunnahHadithBrowseItem'];
type HadithPreview = components['schemas']['SunnahHadithPreview'];
type ImportResponse = components['schemas']['SunnahImportResponse'];

const PAGE_SIZE = 20;

/** State коллекций: 503 (не сконфигурировано) ловим явно по статусу. */
type CollectionsState =
  | { kind: 'loading' }
  | { kind: 'not_configured' }
  | { kind: 'error'; message: string }
  | { kind: 'success'; data: CollectionPreview[] };

/** Объект превью: какой хадис открыт + статус его загрузки. */
type PreviewObject = {
  collection: string;
  number: string;
  state:
    | { kind: 'loading' }
    | { kind: 'error'; message: string }
    | { kind: 'success'; data: HadithPreview };
};

/**
 * AdminSunnahPage (route `/admin/sunnah`) — фазированный, проверяемый
 * импорт хадисов из дампа sunnah.com.
 *
 * Философия (бриф владельца): наполнение должно быть ПРОВЕРЯЕМЫМ и
 * ПОФАЗНЫМ — импортируем по одному хадису, только когда на 100% уверены
 * что workflow корректен. Перед commit'ом показываем PREVIEW: как
 * результат будет выглядеть в НАШЕМ формате (тот же вид что и реальная
 * страница хадиса — naskh/RTL matn, бейдж статуса, оценки учёных, граф
 * иснада позже). Никакого слепого bulk-импорта как primary-действия.
 *
 * Все endpoints возвращают 503 если дамп не сконфигурирован на сервере —
 * показываем дружелюбный «Импорт Sunnah не настроен», не падаем.
 */
function AdminSunnahPage() {
  const t = useT();
  const isMobile = useIsMobile();
  const [collection, setCollection] = useState<string | null>(null);
  const [collectionsState, setCollectionsState] = useState<CollectionsState>({ kind: 'loading' });
  const [preview, setPreview] = useState<PreviewObject | null>(null);
  const [importingKey, setImportingKey] = useState<string | null>(null);
  /** Локально помеченные импортированными (number) — апдейт без рефетча. */
  const [importedLocal, setImportedLocal] = useState<Set<string>>(new Set());

  // Сборники грузим вручную (не через useApiQuery), чтобы отличить 503
  // «дамп не сконфигурирован» по статусу, а не угадывать по тексту ошибки.
  useEffect(() => {
    const controller = new AbortController();
    apiGetRaw<CollectionPreview[]>('/api/v1/admin/sunnah/collections', {
      signal: controller.signal,
    })
      .then((data) => setCollectionsState({ kind: 'success', data: data ?? [] }))
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        if (e instanceof ApiError && e.status === 503) {
          setCollectionsState({ kind: 'not_configured' });
          return;
        }
        setCollectionsState({
          kind: 'error',
          message: formatApiError(e, t('admin.sunnah.collections_load_failed')),
        });
      });
    return () => controller.abort();
  }, [t]);

  const collectionChips = useMemo(() => {
    if (collectionsState.kind !== 'success') return [];
    return collectionsState.data
      .filter((c) => c.name)
      .map((c) => ({
        value: c.name as string,
        label: c.titleEn || c.titleAr || (c.name as string),
        count: c.totalHadith ?? undefined,
      }));
  }, [collectionsState]);

  // Авто-выбор первого сборника когда список загрузился и ничего не выбрано.
  const effectiveCollection =
    collection ?? (collectionChips.length > 0 ? collectionChips[0]!.value : null);

  const openPreview = async (number: string) => {
    if (!effectiveCollection) return;
    setPreview({ collection: effectiveCollection, number, state: { kind: 'loading' } });
    try {
      const data = await apiGetRaw<HadithPreview>(
        `/api/v1/admin/sunnah/preview/${effectiveCollection}/${number}`,
      );
      setPreview({ collection: effectiveCollection, number, state: { kind: 'success', data } });
    } catch (e) {
      const message =
        e instanceof ApiError ? formatSunnahError(e, t) : t('admin.sunnah.preview_load_failed');
      setPreview({ collection: effectiveCollection, number, state: { kind: 'error', message } });
    }
  };

  const doImport = async (col: string, number: string) => {
    const key = `${col}/${number}`;
    setImportingKey(key);
    try {
      const res = await apiPostRaw<ImportResponse>(
        `/api/v1/admin/sunnah/import/${col}/${number}`,
        undefined,
      );
      if ((res.inserted ?? 0) > 0) {
        toast.success(t('admin.sunnah.import_done').replace('{name}', res.collectionName ?? col));
      } else if ((res.skippedExisting ?? 0) > 0) {
        toast.info(t('admin.sunnah.import_skipped_existing'));
      } else {
        toast.info(t('admin.sunnah.import_skipped_invalid'));
      }
      // Помечаем локально импортированным + инвалидируем кэш списка чтобы
      // при ревизите бейдж «уже импортирован» был свежим с бэка.
      setImportedLocal((prev) => new Set(prev).add(number));
      invalidateCache((k) => k.startsWith('/api/v1/admin/sunnah/collections/'));
    } catch (e) {
      const message =
        e instanceof ApiError ? formatSunnahError(e, t) : t('admin.sunnah.import_failed');
      toast.error(message);
    } finally {
      setImportingKey(null);
    }
  };

  const closePreview = () => setPreview(null);

  return (
    <main className="min-h-screen bg-bg">
      <Header />

      <div className="mx-auto max-w-[1380px] px-3 py-6 sm:px-6 sm:py-8">
        <header className="mb-6">
          <div className="mb-1 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-500">
            <ScrollText size={13} aria-hidden /> {t('admin.sunnah.eyebrow')}
          </div>
          <h1 className="font-serif text-[28px] font-semibold leading-tight tracking-tight text-ink-900">
            {t('admin.sunnah.title')}
          </h1>
          <p className="mt-1.5 max-w-[680px] text-sm text-ink-500">
            {t('admin.sunnah.subtitle')}
          </p>
        </header>

        {collectionsState.kind === 'not_configured' ? (
          <NotConfiguredState />
        ) : (
          <>
            {collectionsState.kind === 'loading' && (
              <div className="flex items-center gap-2 py-6 text-sm text-ink-500">
                <Loader2 size={16} className="animate-spin" aria-hidden />
                {t('admin.sunnah.loading_collections')}
              </div>
            )}

            {collectionsState.kind === 'error' && (
              <Card className="mb-5 border-err-500/40 bg-err-100 p-5">
                <div className="flex items-start gap-3 text-err-700">
                  <AlertCircle size={20} className="mt-0.5 shrink-0" aria-hidden />
                  <div className="text-sm">{collectionsState.message}</div>
                </div>
              </Card>
            )}

            {collectionChips.length > 0 && (
              <section className="mb-5">
                <h2 className="mb-2 text-sm font-semibold text-ink-900">
                  {t('admin.sunnah.collection_picker')}
                </h2>
                <FilterChips
                  options={collectionChips}
                  value={effectiveCollection}
                  onChange={(v) => {
                    setCollection(v);
                    setImportedLocal(new Set());
                    setPreview(null);
                  }}
                  ariaLabel={t('admin.sunnah.collection_picker')}
                />
              </section>
            )}

            {/* Browse list + (desktop) preview side panel */}
            <div
              className={
                preview && !isMobile
                  ? 'grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,440px)]'
                  : ''
              }
            >
              <div className="min-w-0">
                {effectiveCollection && (
                  <BrowseList
                    // key — пересоздать список (и его usePagedSearch) при
                    // смене сборника, чтобы аккумулированные страницы не
                    // подмешивались между сборниками
                    key={effectiveCollection}
                    collection={effectiveCollection}
                    importedLocal={importedLocal}
                    activeNumber={preview?.number ?? null}
                    onRowClick={openPreview}
                  />
                )}
              </div>

              {preview && !isMobile && (
                <aside className="min-w-0">
                  <div className="sticky top-6">
                    <PreviewPanel
                      preview={preview}
                      importing={importingKey === `${preview.collection}/${preview.number}`}
                      onImport={doImport}
                      onClose={closePreview}
                      locallyImported={importedLocal.has(preview.number)}
                    />
                  </div>
                </aside>
              )}
            </div>
          </>
        )}
      </div>

      {/* Mobile: preview as full-screen modal */}
      {preview && isMobile && (
        <Modal
          open
          onClose={closePreview}
          title={t('admin.sunnah.preview_title').replace('{number}', preview.number)}
        >
          <PreviewBody
            preview={preview}
            importing={importingKey === `${preview.collection}/${preview.number}`}
            onImport={doImport}
            locallyImported={importedLocal.has(preview.number)}
          />
        </Modal>
      )}
    </main>
  );
}

// ====================================================================
//                          Sub-components
// ====================================================================

interface BrowseListProps {
  /** Гарантированно не-null — компонент монтируется только когда сборник выбран. */
  collection: string;
  /** Локально помеченные импортированными (из родителя). */
  importedLocal: ReadonlySet<string>;
  activeNumber: string | null;
  onRowClick: (number: string) => void;
}

/**
 * BrowseList владеет своим usePagedSearch — монтируется только когда
 * сборник выбран (collection != null), поэтому fetch на `.../null/hadiths`
 * на initial mount страницы не возникает. Родитель ремонтирует компонент
 * через `key={collection}` при смене сборника.
 */
function BrowseList({ collection, importedLocal, activeNumber, onRowClick }: BrowseListProps) {
  const t = useT();

  const buildUrl = useCallback(
    (page: number): string => {
      const params = new URLSearchParams();
      params.set('page', String(page));
      params.set('size', String(PAGE_SIZE));
      return `/api/v1/admin/sunnah/collections/${collection}/hadiths?${params.toString()}`;
    },
    [collection],
  );

  // Поиска нет (дамп листается постранично) — debouncedQuery игнорируем.
  const { state, loadMore, loadingMore } = usePagedSearch<BrowseItem>({
    buildUrl: (page) => buildUrl(page),
    deps: [collection],
    fallbackError: t('admin.sunnah.browse_load_failed'),
  });

  const isImported = (item: BrowseItem): boolean =>
    Boolean(item.alreadyImported) || (item.number != null && importedLocal.has(item.number));

  if (state.kind === 'loading') {
    return (
      <div className="flex items-center gap-2 py-12 text-sm text-ink-500">
        <Loader2 size={16} className="animate-spin" aria-hidden /> {t('common.loading')}
      </div>
    );
  }

  if (state.kind === 'error') {
    return (
      <Card className="border-err-500/40 bg-err-100 p-5">
        <div className="flex items-start gap-3 text-err-700">
          <AlertCircle size={20} className="mt-0.5 shrink-0" aria-hidden />
          <div className="text-sm">{state.message}</div>
        </div>
      </Card>
    );
  }

  if (state.kind !== 'success') return null;

  if (state.data.items.length === 0) {
    return (
      <div className="rounded-md border border-dashed border-border-strong bg-elevated/50 p-8 text-center text-sm text-ink-500">
        {t('admin.sunnah.browse_empty')}
      </div>
    );
  }

  return (
    <>
      <ul className="space-y-2">
        {state.data.items.map((item) => (
          <li key={item.number}>
            <BrowseRow
              item={item}
              imported={isImported(item)}
              active={activeNumber === item.number}
              onClick={() => item.number != null && onRowClick(item.number)}
            />
          </li>
        ))}
      </ul>
      <LoadMoreButton
        onClick={loadMore}
        loading={loadingMore}
        hasNext={state.data.hasNext}
        shownCount={state.data.items.length}
        totalCount={state.data.totalElements}
      />
    </>
  );
}

interface BrowseRowProps {
  item: BrowseItem;
  imported: boolean;
  active: boolean;
  onClick: () => void;
}

function BrowseRow({ item, imported, active, onClick }: BrowseRowProps) {
  const t = useT();
  const arabic = hasArabicScript(item.textArSnippet ?? undefined);
  const snippet = item.textArSnippet || item.textEnSnippet || '—';

  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`block w-full rounded-md border bg-elevated p-3 text-start transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 ${
        active ? 'border-accent-600 border-[1.5px]' : 'border-border hover:border-border-strong'
      }`}
    >
      <div className="mb-1.5 flex items-center gap-2">
        <span className="font-mono text-xs text-ink-500 tabular-nums">
          <bdi dir="ltr">№{item.number}</bdi>
        </span>
        {imported ? (
          <span className="inline-flex items-center gap-1 rounded-sm bg-emerald-100 px-1.5 py-0.5 text-[11px] font-semibold text-emerald-700">
            <CheckCircle2 size={11} aria-hidden /> {t('admin.sunnah.badge_imported')}
          </span>
        ) : (
          <span className="rounded-sm bg-ink-100 px-1.5 py-0.5 text-[11px] font-semibold text-ink-600">
            {t('admin.sunnah.badge_new')}
          </span>
        )}
      </div>
      <p
        dir={arabic ? 'rtl' : 'auto'}
        className={`line-clamp-2 leading-relaxed text-ink-800 ${
          arabic ? 'font-arabic text-base' : 'text-sm'
        }`}
      >
        {snippet}
      </p>
    </button>
  );
}

interface PreviewPanelProps {
  preview: PreviewObject;
  importing: boolean;
  onImport: (col: string, number: string) => void;
  onClose: () => void;
  locallyImported: boolean;
}

function PreviewPanel({ preview, importing, onImport, onClose, locallyImported }: PreviewPanelProps) {
  const t = useT();
  return (
    <section className="overflow-hidden rounded-lg border border-border bg-elevated">
      <header className="flex items-center justify-between gap-2 border-b border-border bg-sunken px-4 py-2.5">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-ink-900">
          <BookOpen size={14} aria-hidden />
          {t('admin.sunnah.preview_title').replace('{number}', preview.number)}
        </h2>
        <button
          type="button"
          onClick={onClose}
          aria-label={t('common.close')}
          className="grid h-7 w-7 place-items-center rounded-sm text-ink-400 transition-colors hover:bg-ink-100 hover:text-ink-700"
        >
          <X size={15} aria-hidden />
        </button>
      </header>
      <div className="px-4 py-4">
        <PreviewBody
          preview={preview}
          importing={importing}
          onImport={onImport}
          locallyImported={locallyImported}
        />
      </div>
    </section>
  );
}

interface PreviewBodyProps {
  preview: PreviewObject;
  importing: boolean;
  onImport: (col: string, number: string) => void;
  locallyImported: boolean;
}

function PreviewBody({ preview, importing, onImport, locallyImported }: PreviewBodyProps) {
  const t = useT();

  if (preview.state.kind === 'loading') {
    return (
      <div className="flex items-center gap-2 py-10 text-sm text-ink-500">
        <Loader2 size={16} className="animate-spin" aria-hidden /> {t('common.loading')}
      </div>
    );
  }

  if (preview.state.kind === 'error') {
    return (
      <div className="flex items-start gap-3 rounded-md border border-err-500/40 bg-err-100 px-4 py-3 text-err-700">
        <AlertCircle size={18} className="mt-0.5 shrink-0" aria-hidden />
        <div className="text-sm">{preview.state.message}</div>
      </div>
    );
  }

  const d = preview.state.data;
  const importedAlready = Boolean(d.alreadyImported) || locallyImported;
  const importable = d.importable !== false;
  const matnAr = d.matnAr || d.normalizedMatn || '';
  const arabicMatn = hasArabicScript(matnAr);
  const hasStructure =
    d.structure &&
    (d.structure.bookNameAr ||
      d.structure.bookNameEn ||
      d.structure.chapterTitleAr ||
      d.structure.chapterTitleEn);

  return (
    <div className="flex flex-col gap-5">
      {/* Flags row */}
      {(importedAlready || !importable) && (
        <div className="flex flex-wrap gap-2">
          {importedAlready && (
            <span className="inline-flex items-center gap-1.5 rounded-sm bg-emerald-100 px-2 py-1 text-xs font-semibold text-emerald-700">
              <CheckCircle2 size={13} aria-hidden /> {t('admin.sunnah.flag_already_imported')}
            </span>
          )}
          {!importable && (
            <span className="inline-flex items-center gap-1.5 rounded-sm bg-rose-100 px-2 py-1 text-xs font-semibold text-rose-700">
              <AlertCircle size={13} aria-hidden /> {t('admin.sunnah.flag_not_importable')}
            </span>
          )}
        </div>
      )}

      {/* Status badge — same colors as real hadith view */}
      {d.status && (
        <div>
          <span
            className={`inline-flex items-center rounded-sm px-2 py-0.5 text-xs font-semibold uppercase tracking-wider ${statusClass(
              d.status,
            )}`}
          >
            {d.status}
          </span>
        </div>
      )}

      {/* Arabic matn — naskh / RTL like the real hadith page */}
      {matnAr && (
        <div>
          <div className="mb-1 text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500">
            {t('admin.sunnah.preview_matn_ar')}
          </div>
          <p
            dir={arabicMatn ? 'rtl' : 'auto'}
            className={`leading-loose text-ink-900 ${arabicMatn ? 'font-arabic text-lg' : 'text-base'}`}
          >
            {matnAr}
          </p>
        </div>
      )}

      {/* English matn */}
      {d.matnEn && (
        <div>
          <div className="mb-1 text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500">
            {t('admin.sunnah.preview_matn_en')}
          </div>
          <p dir="auto" className="text-sm leading-relaxed text-ink-700">
            {d.matnEn}
          </p>
        </div>
      )}

      {/* Scholar gradings */}
      {d.grades && d.grades.length > 0 && (
        <div>
          <div className="mb-1.5 text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500">
            {t('admin.sunnah.preview_grades')}
          </div>
          <ul className="space-y-1.5">
            {d.grades.map((g, i) => (
              <li
                key={`${g.scholar ?? ''}-${i}`}
                className="flex items-center justify-between gap-3 rounded-sm border border-border bg-sunken px-2.5 py-1.5"
              >
                <span dir="auto" className="min-w-0 truncate text-sm text-ink-800">
                  {g.scholar ?? '—'}
                </span>
                <span className="shrink-0 rounded-sm bg-ink-100 px-1.5 py-0.5 text-xs font-semibold text-ink-700">
                  {g.grade ?? '—'}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Structure: book + chapter */}
      {hasStructure && d.structure && (
        <div>
          <div className="mb-1.5 text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500">
            {t('admin.sunnah.preview_structure')}
          </div>
          <div className="rounded-md border border-border bg-sunken px-3 py-2 text-sm">
            <StructureLine
              label={t('admin.sunnah.preview_book')}
              ar={d.structure.bookNameAr}
              en={d.structure.bookNameEn}
            />
            <StructureLine
              label={t('admin.sunnah.preview_chapter')}
              ar={d.structure.chapterTitleAr}
              en={d.structure.chapterTitleEn}
            />
          </div>
        </div>
      )}

      {/* Isnad graph placeholder (isnad === null for now) */}
      <div>
        <div className="mb-1.5 flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500">
          <Network size={12} aria-hidden /> {t('admin.sunnah.preview_isnad')}
        </div>
        <div className="rounded-md border border-dashed border-border-strong bg-sunken px-3 py-4 text-center text-xs text-ink-500">
          {t('admin.sunnah.preview_isnad_placeholder')}
        </div>
      </div>

      {/* Import CTA */}
      <div className="border-t border-border pt-4">
        <Button
          icon={importedAlready ? CheckCircle2 : Download}
          full
          disabled={importedAlready || !importable || importing}
          onClick={() => onImport(preview.collection, preview.number)}
        >
          {importing
            ? t('admin.sunnah.importing')
            : importedAlready
              ? t('admin.sunnah.already_imported_cta')
              : t('admin.sunnah.import_cta')}
        </Button>
      </div>
    </div>
  );
}

interface StructureLineProps {
  label: string;
  ar?: string;
  en?: string;
}

function StructureLine({ label, ar, en }: StructureLineProps) {
  if (!ar && !en) return null;
  const arabic = hasArabicScript(ar ?? undefined);
  return (
    <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5 py-0.5">
      <span className="text-[11px] font-semibold text-ink-500">{label}:</span>
      {ar && (
        <span dir="rtl" className={`text-ink-800 ${arabic ? 'font-arabic' : ''}`}>
          {ar}
        </span>
      )}
      {en && (
        <span dir="auto" className="text-ink-600">
          {ar ? `· ${en}` : en}
        </span>
      )}
    </div>
  );
}

function NotConfiguredState() {
  const t = useT();
  return (
    <div className="flex flex-col items-center gap-4 rounded-lg border border-dashed border-border-strong bg-elevated px-6 py-12 text-center">
      <span className="grid h-14 w-14 place-items-center rounded-full bg-warn-100 text-warn-700">
        <ServerCrash size={26} aria-hidden />
      </span>
      <div>
        <h2 className="font-serif text-xl font-semibold text-ink-900">
          {t('admin.sunnah.not_configured_title')}
        </h2>
        <p className="mt-1.5 max-w-[480px] text-sm text-ink-500">
          {t('admin.sunnah.not_configured_body')}
        </p>
      </div>
    </div>
  );
}

// ====================================================================
//                          Helpers
// ====================================================================

/** Цвета бейджа статуса — те же что в HadithListPage statusClass. */
function statusClass(status: string | undefined): string {
  switch (status) {
    case 'CANONICAL':
      return 'bg-emerald-100 text-emerald-700';
    case 'WEAK':
      return 'bg-amber-100 text-amber-700';
    case 'FABRICATED':
      return 'bg-rose-100 text-rose-700';
    default:
      return 'bg-ink-100 text-ink-700';
  }
}

function formatSunnahError(e: ApiError, t: (k: DictKey) => string): string {
  if (e.status === 503) return t('admin.sunnah.not_configured_short');
  return e.problem.detail || e.problem.title || t('admin.sunnah.import_failed');
}

export default AdminSunnahPage;
