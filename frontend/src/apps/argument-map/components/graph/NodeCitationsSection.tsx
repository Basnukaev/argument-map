import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import {
  Anchor,
  AlertCircle,
  BookOpen,
  ChevronDown,
  ExternalLink,
  Plus,
  Quote,
  ScrollText,
  Trash2,
  User as UserIcon,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import PanelSection from '@/apps/argument-map/components/graph/PanelSection';
import AddSourceModal from '@/apps/argument-map/components/graph/AddSourceModal';
import HadithPickerModal from '@/apps/argument-map/components/graph/HadithPickerModal';
import CitationPicker from '@/shared/components/citation/CitationPicker';
import { buildPdfDeepLinkQuery } from '@/shared/components/citation/pdfRegion';
import { SourceCard } from '@/shared/components/citation/sourceCard';
import { apiGetRaw, apiPostRaw, apiDeleteRaw, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import {
  useSourceDetailPanelStore,
  type SourceDetailCitation,
} from '@/shared/stores/sourceDetailPanelStore';
import { SOURCE_TYPE_LABEL } from '@/apps/argument-map/utils/attachmentTokens';
import { hasArabicScript, useT } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type SourceDto = components['schemas']['SourceResponse'];
type AuthorityDto = components['schemas']['AuthorityResponse'];
type NodeSourceDto = components['schemas']['NodeSourceResponse'];
type PagedSources = components['schemas']['PagedResponseSourceResponse'];
type PagedAuthorities = components['schemas']['PagedResponseAuthorityResponse'];

interface CitationsData {
  links: NodeSourceDto[];
  sourceLookup: Map<string, SourceDto>;
  authorityLookup: Map<string, AuthorityDto>;
}

type SourcesState =
  | { kind: 'not-loaded' }
  | { kind: 'loading' }
  | { kind: 'loaded'; data: CitationsData }
  | { kind: 'error'; message: string };

interface Props {
  nodeId: string | undefined;
  nodeContent: string;
  onCountsChange?: (counts: { lib: number; free: number }) => void;
  /** FB-2: гость/не-EDITOR — без кнопок «Привести источник» и detach (read-only). */
  canWrite?: boolean;
}

function NodeCitationsSection({ nodeId, nodeContent, onCountsChange, canWrite = true }: Props) {
  const t = useT();
  const [state, setState] = useState<SourcesState>({ kind: 'loading' });
  const [addSourceOpen, setAddSourceOpen] = useState(false);
  const [citationPickerOpen, setCitationPickerOpen] = useState(false);
  const [hadithPickerOpen, setHadithPickerOpen] = useState(false);

  // ref на callback - чтобы effect не fire'ил при каждом render parent'а
  // если он передаёт inline callback. Это fix для duplicate requests:
  // раньше onCountsChange был в deps, parent менялся → effect перезапускался → 2x fetch
  const onCountsChangeRef = useRef(onCountsChange);
  useEffect(() => {
    onCountsChangeRef.current = onCountsChange;
  }, [onCountsChange]);

  useEffect(() => {
    if (!nodeId) return;
    // AbortController отменяет in-flight fetch на cleanup. В **production**
    // StrictMode mount'ит один раз - один запрос на network. В **dev**
    // StrictMode дважды mount'ит для catching side-effects, в network tab
    // видно 2 запроса (первый отменён, второй complete) - это by-design
    // React и не warning. См. https://react.dev/reference/react/StrictMode
    const controller = new AbortController();
    Promise.all([
      apiGetRaw<NodeSourceDto[]>(`/api/v1/nodes/${nodeId}/sources`, { signal: controller.signal }),
      apiGetRaw<PagedSources>(`/api/v1/sources?size=100`, { signal: controller.signal }),
      apiGetRaw<PagedAuthorities>(`/api/v1/authorities?size=100`, { signal: controller.signal }),
    ])
      .then(([links, sourcesPage, authoritiesPage]) => {
        if (controller.signal.aborted) return;
        const sourceLookup = new Map<string, SourceDto>();
        for (const src of sourcesPage.items ?? []) {
          if (src.id) sourceLookup.set(src.id, src);
        }
        const authorityLookup = new Map<string, AuthorityDto>();
        for (const a of authoritiesPage.items ?? []) {
          if (a.id) authorityLookup.set(a.id, a);
        }
        setState({
          kind: 'loaded',
          data: { links, sourceLookup, authorityLookup },
        });
        onCountsChangeRef.current?.(computeCounts(links));
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        setState({ kind: 'error', message: formatApiError(e, t('common.list_search_failed')) });
      });
    return () => {
      controller.abort();
    };
  }, [nodeId, t]);

  /** Detach по surrogate nodeSourceId (миграция 25 FK variant A) */
  async function detachNodeSource(nodeSourceId: string) {
    if (!nodeId) return;
    if (state.kind !== 'loaded') return;
    const previous = state.data.links;
    const next = previous.filter((l) => l.id !== nodeSourceId);
    setState({ kind: 'loaded', data: { ...state.data, links: next } });
    onCountsChangeRef.current?.(computeCounts(next));
    try {
      await apiDeleteRaw(`/api/v1/nodes/${nodeId}/sources/${nodeSourceId}`);
    } catch (e: unknown) {
      toast.error(formatApiError(e, t('common.unknown_error')));
      setState({ kind: 'loaded', data: { ...state.data, links: previous } });
      onCountsChangeRef.current?.(computeCounts(previous));
    }
  }

  async function reloadSources() {
    if (!nodeId) return;
    try {
      const links = await apiGetRaw<NodeSourceDto[]>(`/api/v1/nodes/${nodeId}/sources`);
      setState((prev) => {
        if (prev.kind !== 'loaded') return prev;
        return { kind: 'loaded', data: { ...prev.data, links } };
      });
      onCountsChangeRef.current?.(computeCounts(links));
    } catch (e: unknown) {
      toast.error(formatApiError(e, t('graph.toast.update_failed')));
    }
  }

  /**
   * Прикрепить хадис как опору. Бэкенд (под-проект #2.A) переиспользует
   * мост Source: создаёт node_sources-строку с привязкой к хадису.
   * POST бросает 403 при нехватке прав — пробрасываем дальше, чтобы
   * HadithPickerModal не закрылся (catch в handlePick отсутствует —
   * ошибка показывается тостом тут).
   */
  async function attachHadith(hadithId: string) {
    if (!nodeId) return;
    try {
      await apiPostRaw(`/api/v1/nodes/${nodeId}/hadith-citations`, { hadithId });
      await reloadSources();
      toast.success(t('node.citation_hadith_attached'));
    } catch (e: unknown) {
      toast.error(formatApiError(e, t('graph.toast.update_failed')));
      throw e;
    }
  }

  return (
    <>
      <PanelSection
        icon={Anchor}
        title={t('node.section.support')}
        count={state.kind === 'loaded' ? state.data.links.length : undefined}
        defaultOpen={false}
      >
        {/* FB-2: detach (×) скрыт для гостя/не-EDITOR — onDetach передаётся
            только при canWrite. SourceCard/HadithCite/FreeformCite рисуют ×
            лишь когда onDetach определён (бэк всё равно отдавал 403 — убираем
            мёртвую кнопку из read-only UI). Primary write (edit/add) скрыты С63. */}
        <CitationsList state={state} onDetach={canWrite ? detachNodeSource : undefined} />
        {/* Vertical stack - текст кнопки "Привести источник" длинный и не
            вмещается в side-by-side layout в узком detail panel (~360px) */}
        {canWrite && (
        <div className="mt-2 flex flex-col gap-2">
          <Button
            type="button"
            size="sm"
            icon={BookOpen}
            onClick={() => setCitationPickerOpen(true)}
            disabled={!nodeId}
            full
          >
            {t('node.citation_add_library')}
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            icon={ScrollText}
            onClick={() => setHadithPickerOpen(true)}
            disabled={!nodeId}
            full
          >
            {t('node.citation_add_hadith')}
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            icon={Plus}
            onClick={() => setAddSourceOpen(true)}
            disabled={!nodeId}
            full
          >
            {t('node.citation_add_free')}
          </Button>
        </div>
        )}
      </PanelSection>

      {addSourceOpen && nodeId && (
        <AddSourceModal
          nodeId={nodeId}
          onClose={() => setAddSourceOpen(false)}
          onAttached={reloadSources}
        />
      )}

      {citationPickerOpen && nodeId && (
        <CitationPicker
          targetType="nodes"
          targetId={nodeId}
          targetLabel={nodeContent}
          onClose={() => setCitationPickerOpen(false)}
          onCreated={reloadSources}
        />
      )}

      {hadithPickerOpen && nodeId && (
        <HadithPickerModal
          open={hadithPickerOpen}
          onClose={() => setHadithPickerOpen(false)}
          onSelect={attachHadith}
        />
      )}
    </>
  );
}

interface CitationsListProps {
  state: SourcesState;
  /**
   * Передаётся nodeSourceId (link.id) - FK variant A. `undefined` =
   * read-only (гость/не-EDITOR): detach-× не рендерится во всех карточках.
   */
  onDetach?: (nodeSourceId: string) => void;
}

function buildDeepLink(link: NodeSourceDto): string | null {
  const c = link.citation;
  if (!c?.book?.id) return null;
  if (link.mode === 'TEXT' && c.location?.pageId) {
    const rangeStart = c.location.rangeStart;
    const rangeEnd = c.location.rangeEnd;
    const range = rangeStart != null && rangeEnd != null ? `&highlight=${rangeStart}-${rangeEnd}` : '';
    return `/books/${c.book.id}?pageId=${c.location.pageId}${range}`;
  }
  // PDF (старый bbox-on-page) + PDF_LINK (REGION-цитата по PDF_LINK, ADR-067)
  // используют один deep-link формат. fileIndex включаем когда задан —
  // multi-volume книги иначе открываются на первом томе (latent bug).
  if ((link.mode === 'PDF' || link.mode === 'PDF_LINK') && c.pdf?.pageNumber != null) {
    const bbox = c.pdf.bbox as
      | { x?: number; y?: number; width?: number; height?: number }
      | undefined;
    return `/books/${c.book.id}${buildPdfDeepLinkQuery({
      pageNumber: c.pdf.pageNumber,
      fileIndex: c.pdf.fileIndex,
      bbox,
    })}`;
  }
  return null;
}

function isLibraryMode(mode: NodeSourceDto['mode']): boolean {
  return mode === 'TEXT' || mode === 'PDF' || mode === 'PDF_LINK' || mode === 'REGION';
}

function computeCounts(links: NodeSourceDto[]): { lib: number; free: number } {
  let lib = 0;
  let free = 0;
  for (const l of links) {
    if (isLibraryMode(l.mode)) {
      lib += 1;
    } else {
      free += 1;
    }
  }
  return { lib, free };
}

/**
 * Выбрать latin/cyrillic title - source.title если cyrillic, иначе fallback на book.title.
 * Используется для header карточки чтобы chip + title в LTR-subtree рендерились
 * консистентно (русское название а не arabic title в RTL-флоу header'а)
 */
function pickLatinTitle(source: SourceDto | undefined, bookTitle?: string | null): string {
  const st = source?.title;
  if (st && !hasArabicScript(st)) return st;
  if (bookTitle && !hasArabicScript(bookTitle)) return bookTitle;
  return '(книга)';
}

/**
 * Локатор для свёрнутой строки библиотечной цитаты: «Том N · стр. M · ▢ область».
 * Зеркалит логику QuoteBlock (page из printedPage/pageNumber/pdf.pageNumber,
 * том из pdf.fileIndex когда LocationRef пуст, область при наличии bbox), но
 * собирает плоскую строку для compact-summary вместо infobox-разметки.
 */
function buildLibraryLocator(link: NodeSourceDto, t: ReturnType<typeof useT>): string | null {
  const c = link.citation;
  if (!c) return null;
  const { location, pdf } = c;
  const parts: string[] = [];
  const volume = !location && pdf?.fileIndex != null ? String(pdf.fileIndex) : null;
  if (volume != null) parts.push(`${t('cite.volume.short')} ${volume}`);
  const page =
    location?.printedPage ??
    (location?.pageNumber != null
      ? String(location.pageNumber)
      : pdf?.pageNumber != null
        ? String(pdf.pageNumber)
        : null);
  if (page != null) parts.push(`${t('cite.page.short')} ${page}`);
  if (!location && pdf?.bbox != null) parts.push(`▢ ${t('cite.region.label')}`);
  return parts.length > 0 ? parts.join(' · ') : null;
}

interface GroupHeaderProps {
  title: string;
  count: number;
}

/** Маленький uppercase-заголовок группы с counter'ом («ХАДИСЫ (2)»). */
function GroupHeader({ title, count }: GroupHeaderProps) {
  return (
    <div className="flex items-center gap-1.5 px-0.5 pt-1 text-xs font-semibold uppercase tracking-wide text-ink-400">
      <span>{title}</span>
      <span className="font-mono tracking-normal text-ink-400">
        (<bdi dir="ltr">{count}</bdi>)
      </span>
    </div>
  );
}

interface CompactRowProps {
  /** Краткий заголовок (latin/cyrillic предпочтительно, arabic — dir="auto"). */
  title: string;
  /** Локатор справа: «стр. 3», «Том 2 · стр. 5 · ▢ область», коллекция+№. */
  locator?: string | null;
  /** Видимый текст основного действия (свёрнутая строка). Если нет —
   *  кнопка icon-only, доступное имя берётся из primaryAriaLabel. */
  primaryLabel?: string;
  /** Доступное имя кнопки основного действия (для icon-only — [→]). */
  primaryAriaLabel?: string;
  /** Основное действие из свёрнутой строки ([открыть] / [→]). */
  onPrimaryAction?: () => void;
  /** Полное содержимое, раскрывается по клику на строку. Detach × живёт
   *  внутри развёрнутой карточки (SourceCard / HadithCite / FreeformCite). */
  children: ReactNode;
}

/**
 * Компактная свёрнутая строка опоры. Единый shape для всех 3 типов (хадис /
 * книга / свободная) — в этом весь смысл редизайна: вместо трёх разных
 * карточек один сканируемый ряд. Click по строке раскрывает полную карточку
 * (matn / quote / метаданные + detach ×) внутри. Primary action в свёрнутом
 * ряду не триггерит раскрытие (stopPropagation).
 */
function CompactRow({
  title,
  locator,
  primaryLabel,
  primaryAriaLabel,
  onPrimaryAction,
  children,
}: CompactRowProps) {
  const [open, setOpen] = useState(false);
  return (
    <div className="group/row rounded-md border border-border bg-ink-50/60">
      <div className="flex items-center gap-2 px-2.5 py-2">
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          className="flex min-w-0 flex-1 items-center gap-2 text-start focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
        >
          <ChevronDown
            size={13}
            aria-hidden="true"
            className={`shrink-0 text-ink-400 transition-transform ${open ? 'rotate-180' : ''}`}
          />
          <span
            className={`truncate text-xs text-ink-800 ${
              hasArabicScript(title) ? 'font-naskh' : 'font-medium'
            }`}
            dir="auto"
          >
            {title}
          </span>
          {locator && (
            <span className="shrink-0 truncate font-mono text-xs text-ink-500" dir="auto">
              {locator}
            </span>
          )}
        </button>
        {onPrimaryAction && (
          <button
            type="button"
            aria-label={primaryLabel ? undefined : primaryAriaLabel}
            onClick={(e) => {
              e.stopPropagation();
              onPrimaryAction();
            }}
            className="inline-flex shrink-0 items-center gap-1 rounded px-1.5 py-0.5 text-xs font-medium text-accent-700 transition-colors hover:bg-accent-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
          >
            <ExternalLink size={12} aria-hidden="true" />
            {primaryLabel && <span>{primaryLabel}</span>}
          </button>
        )}
      </div>
      {open && <div className="border-t border-border p-2.5">{children}</div>}
    </div>
  );
}

/** Общие пропсы, прокидываемые из CitationsList в каждый Row. */
interface CitationGroupProps {
  sourceLookup: Map<string, SourceDto>;
  authorityLookup: Map<string, AuthorityDto>;
  onDetach?: (nodeSourceId: string) => void;
  navigate: ReturnType<typeof useNavigate>;
  openSourceDetail: (citation: SourceDetailCitation) => void;
}

function CitationsList({ state, onDetach }: CitationsListProps) {
  const t = useT();
  const navigate = useNavigate();
  const openSourceDetail = useSourceDetailPanelStore((s) => s.openWith);
  if (state.kind === 'not-loaded' || state.kind === 'loading') {
    return <p className="text-xs text-ink-500">{t('common.loading')}</p>;
  }
  if (state.kind === 'error') {
    return <p className="text-xs text-err-700">{t('common.error')}: {state.message}</p>;
  }
  const { links, sourceLookup, authorityLookup } = state.data;
  if (links.length === 0) {
    return (
      <p className="text-xs italic text-ink-500">{t('node.citations_empty')}</p>
    );
  }

  // Группировка по типу источника. Хадис проверяем первым (его mode не
  // библиотечный, но семантически это отдельная группа). Порядок внутри
  // группы — исходный порядок links.
  const hadiths: NodeSourceDto[] = [];
  const library: NodeSourceDto[] = [];
  const freeform: NodeSourceDto[] = [];
  for (const link of links) {
    if (link.hadith) hadiths.push(link);
    else if (isLibraryMode(link.mode)) library.push(link);
    else freeform.push(link);
  }

  const groupProps = { sourceLookup, authorityLookup, onDetach, navigate, openSourceDetail };

  return (
    <div className="space-y-3">
      {hadiths.length > 0 && (
        <section className="space-y-1.5">
          <GroupHeader title={t('cite.group.hadith')} count={hadiths.length} />
          {hadiths.map((link, idx) => (
            <HadithRow key={link.id ?? `h-${idx}`} link={link} {...groupProps} />
          ))}
        </section>
      )}
      {library.length > 0 && (
        <section className="space-y-1.5">
          <GroupHeader title={t('cite.group.library')} count={library.length} />
          {library.map((link, idx) => (
            <LibraryRow key={link.id ?? link.sourceId ?? `l-${idx}`} link={link} {...groupProps} />
          ))}
        </section>
      )}
      {freeform.length > 0 && (
        <section className="space-y-1.5">
          <GroupHeader title={t('cite.group.freeform')} count={freeform.length} />
          {freeform.map((link, idx) => (
            <FreeformRow key={link.id ?? link.sourceId ?? `f-${idx}`} link={link} {...groupProps} />
          ))}
        </section>
      )}
    </div>
  );
}

interface RowProps extends CitationGroupProps {
  link: NodeSourceDto;
}

/** Свёрнутая строка библиотечной опоры → разворачивается в полный SourceCard. */
function LibraryRow({ link, sourceLookup, onDetach, navigate, openSourceDetail }: RowProps) {
  const t = useT();
  const source = link.sourceId ? sourceLookup.get(link.sourceId) : undefined;
  const deepLink = buildDeepLink(link);
  const titleLatin = pickLatinTitle(source, link.citation?.book?.title);
  // Свёрнутая строка показывает РЕАЛЬНОЕ название книги (часто арабское) —
  // pickLatinTitle/«(книга)» оставляем только для LTR-заголовка SourceCard.
  const displayTitle = source?.title || link.citation?.book?.title || titleLatin;
  const locator = buildLibraryLocator(link, t);
  const openPanel = link.sourceId
    ? () =>
        openSourceDetail({
          sourceId: link.sourceId!,
          nodeSourceId: link.id,
          quote: link.quote ?? undefined,
          context: link.context ?? undefined,
        })
    : undefined;
  return (
    <CompactRow
      title={displayTitle}
      locator={locator}
      primaryAriaLabel={t('cite.action.gotoSource')}
      onPrimaryAction={deepLink ? () => navigate(deepLink) : undefined}
    >
      <SourceCard
        link={link}
        titleLatin={titleLatin}
        onDelete={onDetach && link.id ? () => onDetach(link.id!) : undefined}
        onPrimaryAction={deepLink ? () => navigate(deepLink) : undefined}
        onTitleClick={openPanel}
        sourceId={source?.id ?? link.sourceId}
        sourceType={source?.sourceType}
      />
    </CompactRow>
  );
}

/** Свёрнутая строка хадис-опоры → разворачивается в полную карточку HadithCite. */
function HadithRow({ link, onDetach, navigate }: RowProps) {
  const t = useT();
  const h = link.hadith;
  const title = h?.collectionName ?? t('node.citation_hadith_label');
  const locator = h?.primaryNumber != null ? `№${h.primaryNumber}` : null;
  return (
    <CompactRow
      title={title}
      locator={locator}
      primaryLabel={t('node.citation_hadith_open')}
      onPrimaryAction={h?.hadithId ? () => navigate(`/hadith/hadiths/${h.hadithId}`) : undefined}
    >
      <HadithCite link={link} onDetach={onDetach} navigate={navigate} />
    </CompactRow>
  );
}

/** Свёрнутая строка свободной опоры → разворачивается в полную карточку FreeformCite. */
function FreeformRow({ link, sourceLookup, authorityLookup, onDetach }: RowProps) {
  const source = link.sourceId ? sourceLookup.get(link.sourceId) : undefined;
  const authorityFallback = source?.authorityId
    ? authorityLookup.get(source.authorityId)
    : undefined;
  const title = source?.title ?? '(удалён из справочника)';
  return (
    <CompactRow title={title}>
      <FreeformCite
        link={link}
        source={source}
        authority={authorityFallback}
        onDetach={onDetach}
      />
    </CompactRow>
  );
}

/** Цвет статус-бэйджа хадиса (зеркалит HadithListPage/HadithPickerModal). */
function hadithStatusClass(status: string | undefined): string {
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

interface HadithCiteProps {
  link: NodeSourceDto;
  /** nodeSourceId (link.id) - FK variant A. `undefined` = read-only: × скрыт. */
  onDetach?: (nodeSourceId: string) => void;
  navigate: ReturnType<typeof useNavigate>;
}

/**
 * Карточка опоры-хадиса. Хадис привязан к узлу через мост Source
 * (под-проект #2.A) — это всё ещё node_sources-строка с link.id,
 * поэтому detach работает так же как для остальных опор. Превью matn
 * рендерится naskh + RTL, ссылка ведёт в Hadith Explorer.
 */
function HadithCite({ link, onDetach, navigate }: HadithCiteProps) {
  const t = useT();
  const h = link.hadith;
  if (!h) return null;
  return (
    <div className="group/h rounded-md border border-border bg-ink-50/60 p-2.5">
      <div className="flex items-center gap-2">
        <span
          className="inline-flex items-center gap-1 rounded border border-border bg-ink-100 px-1.5 py-0.5 text-xs font-bold uppercase tracking-wider text-ink-700"
          aria-label={t('node.citation_hadith_aria')}
        >
          <ScrollText size={11} aria-hidden="true" />
          {t('node.citation_hadith_label')}
        </span>
        {h.collectionName && (
          <span className="truncate text-xs font-medium text-ink-600" dir="auto">
            {h.collectionName}
          </span>
        )}
        {h.primaryNumber != null && (
          <span className="font-mono text-xs text-ink-500">№{h.primaryNumber}</span>
        )}
        {h.status && (
          <span
            className={`rounded-sm px-1.5 py-0.5 text-xs font-semibold ${hadithStatusClass(h.status)}`}
          >
            {h.status}
          </span>
        )}
        {onDetach && (
          <button
            type="button"
            aria-label={t('node.citation_detach_aria')}
            onClick={() => link.id && onDetach(link.id)}
            className="ms-auto rounded p-1 text-ink-400 opacity-0 transition-opacity hover:bg-err-100 hover:text-err-700 focus:opacity-100 group-hover/h:opacity-100"
          >
            <Trash2 size={13} aria-hidden="true" />
          </button>
        )}
      </div>

      {h.previewMatn && (
        <p className="mt-2 font-arabic text-base leading-loose text-ink-900 line-clamp-3" dir="rtl">
          {h.previewMatn}
        </p>
      )}

      {h.hadithId && (
        <button
          type="button"
          onClick={() => navigate(`/hadith/hadiths/${h.hadithId}`)}
          className="mt-2 inline-flex items-center gap-1 text-xs font-medium text-accent-700 hover:underline"
        >
          <ExternalLink size={12} aria-hidden="true" /> {t('node.citation_hadith_open')}
        </button>
      )}
    </div>
  );
}

interface FreeformCiteProps {
  link: NodeSourceDto;
  source: SourceDto | undefined;
  authority: AuthorityDto | undefined;
  /** nodeSourceId (link.id) - FK variant A. `undefined` = read-only: × скрыт. */
  onDetach?: (nodeSourceId: string) => void;
}

/**
 * Freeform citation card (mode LEGACY) - native details/summary collapse.
 * Простой LTR без bidi сложности - freeform обычно urls/articles/quotes
 * на одном языке. Если будет arabic quote - QuoteBlock-like behavior через
 * dir="auto" на самой quote string
 */
function FreeformCite({ link, source, authority, onDetach }: FreeformCiteProps) {
  const t = useT();
  const sourceType = source?.sourceType;
  const kindLabel = sourceType ? SOURCE_TYPE_LABEL[sourceType] : 'источник';
  const title = source?.title ?? '(удалён из справочника)';
  const citation = source?.citation;
  const quote = link.quote;
  const authorMeta = authority
    ? [authority.era, authority.madhab].filter(Boolean).join(' · ')
    : undefined;
  const hasUrl = sourceType === 'URL' && Boolean(citation);
  const snapshot = link.legacySnapshot;

  return (
    <details className="group/c rounded-md border border-border bg-ink-50/60 open:bg-ink-50">
      <summary className="flex cursor-pointer list-none items-center gap-2 px-2.5 py-2 hover:bg-ink-100/60">
        <span
          className="inline-flex items-center gap-1 rounded border border-border bg-ink-100 px-1.5 py-0.5 text-xs font-bold uppercase tracking-wider text-ink-700"
          aria-label={t('node.citation_free_aria')}
        >
          <Quote size={11} aria-hidden="true" />
          Свободная
        </span>
        <span className="flex-1 truncate text-xs text-ink-800" dir="ltr">
          {title}
        </span>
        {!hasUrl && sourceType === 'URL' && (
          <span
            className="inline-flex items-center gap-1 text-xs text-warn-700"
            title="URL не указан"
          >
            <AlertCircle size={12} aria-hidden="true" />
          </span>
        )}
        {onDetach && (
          <button
            type="button"
            aria-label={t('node.citation_detach_aria')}
            onClick={(e) => {
              e.preventDefault();
              if (link.id) onDetach(link.id);
            }}
            className="rounded p-1 text-ink-400 opacity-0 transition-opacity hover:bg-err-100 hover:text-err-700 focus:opacity-100 group-hover/c:opacity-100"
          >
            <Trash2 size={13} aria-hidden="true" />
          </button>
        )}
        <ChevronDown
          size={14}
          className="text-ink-400 transition-transform group-open/c:rotate-180"
          aria-hidden="true"
        />
      </summary>

      <div className="space-y-1.5 border-t border-border px-2.5 py-2">
        {/* kindLabel - chip-type (BOOK/HADITH/URL), всегда LTR uppercase */}
        <div dir="ltr" className="text-start font-mono text-xs uppercase tracking-wide text-ink-500">
          {kindLabel}
        </div>

        {/* authority/citation/context - контент из API, может быть на любом
            языке. dir="auto" определит направление по первому сильному
            символу. Шрифт font-naskh - через эвристику hasArabicScript */}
        {authority && (
          <div dir="auto" className="text-xs text-ink-700">
            <UserIcon size={11} className="me-1 inline text-ink-400" aria-hidden="true" />
            <span
              className={
                hasArabicScript(authority.name) ? 'font-naskh font-medium' : 'font-medium'
              }
            >
              {authority.name}
            </span>
            {authorMeta && (
              <span className="ms-1.5 font-mono text-xs text-ink-500">
                <span aria-hidden>·</span> <bdi>{authorMeta}</bdi>
              </span>
            )}
          </div>
        )}

        {(citation || snapshot) && (
          <div dir="auto" className="font-mono text-xs text-ink-500">
            {citation && <bdi>{citation}</bdi>}
            {citation && snapshot && <span aria-hidden>{' · '}</span>}
            {snapshot && <bdi>{snapshot}</bdi>}
          </div>
        )}

        {quote && (
          <div
            dir="auto"
            className={
              hasArabicScript(quote)
                ? 'border-s-2 border-border-strong ps-2 font-naskh text-sm leading-loose text-ink-700 text-start'
                : 'border-s-2 border-border-strong ps-2 text-xs italic leading-relaxed text-ink-600 text-start'
            }
          >
            «{quote}»
          </div>
        )}

        {link.context && (
          <div dir="auto" className="text-xs text-ink-500">
            {link.context}
          </div>
        )}
      </div>
    </details>
  );
}

export default NodeCitationsSection;
