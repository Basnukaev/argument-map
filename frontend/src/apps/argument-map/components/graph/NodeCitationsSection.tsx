import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import {
  Anchor,
  AlertCircle,
  BookOpen,
  ExternalLink,
  Plus,
  Quote,
  Trash2,
  User as UserIcon,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import PanelSection from '@/apps/argument-map/components/graph/PanelSection';
import AddSourceModal from '@/apps/argument-map/components/graph/AddSourceModal';
import CitationPicker from '@/shared/components/citation/CitationPicker';
import { apiGetRaw, apiDeleteRaw, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { SOURCE_TYPE_LABEL } from '@/apps/argument-map/utils/attachmentTokens';
import { hasArabicScript } from '@/apps/argument-map/components/graph/nodeDetailsUtils';
import type { components } from '@/shared/api/types';

type SourceDto = components['schemas']['SourceResponse'];
type AuthorityDto = components['schemas']['AuthorityResponse'];
type NodeSourceDto = components['schemas']['NodeSourceResponse'];
type CitationDto = components['schemas']['CitationResponse'];

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
}

function NodeCitationsSection({ nodeId, nodeContent, onCountsChange }: Props) {
  const [state, setState] = useState<SourcesState>({ kind: 'loading' });
  const [addSourceOpen, setAddSourceOpen] = useState(false);
  const [citationPickerOpen, setCitationPickerOpen] = useState(false);

  useEffect(() => {
    if (!nodeId) return;
    let cancelled = false;
    Promise.all([
      apiGetRaw<NodeSourceDto[]>(`/api/v1/nodes/${nodeId}/sources`),
      apiGetRaw<SourceDto[]>(`/api/v1/sources`),
      apiGetRaw<AuthorityDto[]>(`/api/v1/authorities`),
    ])
      .then(([links, sources, authorities]) => {
        if (cancelled) return;
        const sourceLookup = new Map<string, SourceDto>();
        for (const src of sources) {
          if (src.id) sourceLookup.set(src.id, src);
        }
        const authorityLookup = new Map<string, AuthorityDto>();
        for (const a of authorities) {
          if (a.id) authorityLookup.set(a.id, a);
        }
        setState({
          kind: 'loaded',
          data: { links, sourceLookup, authorityLookup },
        });
        onCountsChange?.(computeCounts(links));
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setState({ kind: 'error', message: formatApiError(e, 'Не удалось загрузить опору') });
      });
    return () => {
      cancelled = true;
    };
  }, [nodeId, onCountsChange]);

  async function detachSource(sourceId: string) {
    if (!nodeId) return;
    if (state.kind !== 'loaded') return;
    const previous = state.data.links;
    const next = previous.filter((l) => l.sourceId !== sourceId);
    setState({ kind: 'loaded', data: { ...state.data, links: next } });
    onCountsChange?.(computeCounts(next));
    try {
      await apiDeleteRaw(`/api/v1/nodes/${nodeId}/sources/${sourceId}`);
    } catch (e: unknown) {
      toast.error(formatApiError(e, 'Не удалось отвязать подкрепление'));
      setState({ kind: 'loaded', data: { ...state.data, links: previous } });
      onCountsChange?.(computeCounts(previous));
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
      onCountsChange?.(computeCounts(links));
    } catch (e: unknown) {
      toast.error(formatApiError(e, 'Не удалось обновить опору'));
    }
  }

  return (
    <>
      <PanelSection
        icon={Anchor}
        title="Опора"
        count={state.kind === 'loaded' ? state.data.links.length : undefined}
        defaultOpen={false}
      >
        <CitationsList state={state} onDetach={detachSource} />
        <div className="mt-2 flex gap-2">
          <Button
            type="button"
            size="sm"
            icon={BookOpen}
            onClick={() => setCitationPickerOpen(true)}
            disabled={!nodeId}
            className="flex-1 justify-center"
          >
            Привести источник
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            icon={Plus}
            onClick={() => setAddSourceOpen(true)}
            disabled={!nodeId}
            className="flex-1 justify-center"
          >
            Свободный
          </Button>
        </div>
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
          nodeId={nodeId}
          nodeContent={nodeContent}
          onClose={() => setCitationPickerOpen(false)}
          onCreated={reloadSources}
        />
      )}
    </>
  );
}

interface CitationsListProps {
  state: SourcesState;
  onDetach: (sourceId: string) => void;
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
  if (link.mode === 'PDF' && c.pdf?.fileId && c.pdf.pageNumber != null) {
    const bbox = c.pdf.bbox as
      | { x?: number; y?: number; width?: number; height?: number }
      | undefined;
    const bboxStr =
      bbox && bbox.x != null
        ? `&bbox=${bbox.x},${bbox.y},${bbox.width},${bbox.height}`
        : '';
    return `/books/${c.book.id}?pdf=1&pdfPageNumber=${c.pdf.pageNumber}${bboxStr}`;
  }
  return null;
}

function isLibraryMode(mode: NodeSourceDto['mode']): boolean {
  return mode === 'TEXT' || mode === 'PDF' || mode === 'REGION';
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

function CitationsList({ state, onDetach }: CitationsListProps) {
  if (state.kind === 'not-loaded' || state.kind === 'loading') {
    return <p className="text-[12px] text-slate-500">Загрузка</p>;
  }
  if (state.kind === 'error') {
    return <p className="text-[12px] text-red-700">Ошибка: {state.message}</p>;
  }
  const { links, sourceLookup, authorityLookup } = state.data;
  if (links.length === 0) {
    return (
      <p className="text-[12px] italic text-slate-500">К узлу не привязано ни одной опоры</p>
    );
  }
  return (
    <div className="space-y-2">
      {links.map((link, idx) => {
        const source = link.sourceId ? sourceLookup.get(link.sourceId) : undefined;
        const authorityFallback = source?.authorityId
          ? authorityLookup.get(source.authorityId)
          : undefined;
        const key = link.sourceId ?? `${link.nodeId}-${idx}`;
        if (isLibraryMode(link.mode)) {
          return <LibraryCite key={key} link={link} onDetach={onDetach} />;
        }
        return (
          <FreeformCite
            key={key}
            link={link}
            source={source}
            authority={authorityFallback}
            onDetach={onDetach}
          />
        );
      })}
    </div>
  );
}

interface LibraryCiteProps {
  link: NodeSourceDto;
  onDetach: (sourceId: string) => void;
}

/**
 * Карточка library-backed подкрепления (mode TEXT/PDF/REGION) - блочный
 * рендер structured citation из ADR-028. Каждое поле в своём div со
 * правильным dir (RTL для arabic) и шрифтом (font-naskh для арабского).
 * Условный рендер - пропускаем блок если nested ref = null.
 */
function LibraryCite({ link, onDetach }: LibraryCiteProps) {
  const navigate = useNavigate();
  const c: CitationDto = link.citation ?? {};
  const { authority, book, muhaqqiq, publisher, publicationPlace, location, pdf } = c;

  const bookTitle = book?.title ?? '(книга недоступна)';
  const bookIsArabic = hasArabicScript(bookTitle);
  const quote = link.quote;
  const quoteIsArabic = hasArabicScript(quote);
  const deepLink = buildDeepLink(link);

  // Композиция publisher · place · edition в одну строку - все опциональны
  const editionParts: string[] = [];
  if (publisher?.name) editionParts.push(`изд. ${publisher.name}`);
  if (publicationPlace?.name) editionParts.push(publicationPlace.name);
  if (book?.editionNumber != null) editionParts.push(`${book.editionNumber}-е изд.`);

  // Композиция годов хиджри / григорианский
  const yearParts: string[] = [];
  if (book?.publishedYearHijri != null) yearParts.push(`${book.publishedYearHijri} هـ`);
  if (book?.publishedYearGregorian != null) yearParts.push(`${book.publishedYearGregorian} м.`);

  // Композиция location: Т.{part} · стр.{printedPage}
  const locParts: string[] = [];
  if (location?.part) locParts.push(`Т.${location.part}`);
  if (location?.printedPage) {
    locParts.push(`стр.${location.printedPage}`);
  } else if (location?.pageNumber != null) {
    locParts.push(`стр.${location.pageNumber}`);
  }
  if (pdf?.pageNumber != null) locParts.push(`PDF стр.${pdf.pageNumber}`);

  return (
    <article className="group relative overflow-hidden rounded-md border border-slate-200 bg-white transition-colors hover:border-indigo-300">
      <div className="absolute bottom-0 left-0 top-0 w-[3px] bg-indigo-600" />
      <div className="space-y-1.5 py-2.5 pl-3.5 pr-2.5">
        {/* Header: бейдж + удалить */}
        <div className="flex items-start gap-2">
          <span className="inline-flex items-center gap-1.5 rounded border border-indigo-200 bg-indigo-50 px-2 py-0.5 text-[11px] font-bold uppercase tracking-wider text-indigo-700">
            <BookOpen size={13} aria-hidden="true" />
            Из библиотеки
          </span>
          <span className="flex-1" />
          <button
            type="button"
            aria-label="Отвязать опору"
            onClick={() => link.sourceId && onDetach(link.sourceId)}
            className="rounded p-1 text-slate-400 opacity-0 transition-opacity hover:bg-red-50 hover:text-red-600 focus:opacity-100 group-hover:opacity-100"
          >
            <Trash2 size={14} aria-hidden="true" />
          </button>
        </div>

        {/* Author block: full_name + (т.{deathYearHijri} هـ). Fallback на short name */}
        {authority && (
          <div
            className={`flex items-center gap-1.5 text-[12.5px] text-slate-800 ${
              authority.fullName && hasArabicScript(authority.fullName)
                ? 'font-naskh text-[14px]'
                : ''
            }`}
            dir={authority.fullName && hasArabicScript(authority.fullName) ? 'rtl' : 'ltr'}
          >
            <UserIcon size={13} className="text-slate-400 shrink-0" aria-hidden="true" />
            <span className="font-medium">
              {authority.fullName ?? authority.name}
            </span>
            {authority.deathYearHijri != null && (
              <span className="text-slate-500" dir="rtl">
                (т.{authority.deathYearHijri} هـ)
              </span>
            )}
          </div>
        )}

        {/* Book title block */}
        <div
          className={`text-[12.5px] font-semibold leading-snug text-slate-900 ${
            bookIsArabic ? 'font-naskh text-[14px]' : ''
          }`}
          dir={bookIsArabic ? 'rtl' : 'ltr'}
        >
          {bookTitle}
        </div>

        {/* Muhaqqiq block - тахкик: {fullName ?? name} */}
        {muhaqqiq && (
          <div
            className={`text-[12px] text-slate-700 ${
              (muhaqqiq.fullName ?? muhaqqiq.name) &&
              hasArabicScript(muhaqqiq.fullName ?? muhaqqiq.name)
                ? 'font-naskh text-[13px]'
                : ''
            }`}
            dir={
              (muhaqqiq.fullName ?? muhaqqiq.name) &&
              hasArabicScript(muhaqqiq.fullName ?? muhaqqiq.name)
                ? 'rtl'
                : 'ltr'
            }
          >
            <span className="text-slate-500">тахкик:</span>{' '}
            <span className="font-medium">{muhaqqiq.fullName ?? muhaqqiq.name}</span>
          </div>
        )}

        {/* Publisher · place · edition block */}
        {editionParts.length > 0 && (
          <div className="text-[12px] text-slate-600">{editionParts.join(' · ')}</div>
        )}

        {/* Years block - хиджри / григорианский */}
        {yearParts.length > 0 && (
          <div className="font-mono text-[11px] text-slate-500" dir="ltr">
            {yearParts.join(' / ')}
          </div>
        )}

        {/* Location block */}
        {locParts.length > 0 && (
          <div className="font-mono text-[11px] text-slate-500">{locParts.join(' · ')}</div>
        )}

        {/* Quote block */}
        {quote && (
          <div
            dir={quoteIsArabic ? 'rtl' : 'ltr'}
            className={`mt-1 border-l-2 border-indigo-200 pl-2 text-[12.5px] leading-relaxed text-slate-700 ${
              quoteIsArabic ? 'font-naskh text-[14px] not-italic leading-loose' : 'italic'
            }`}
          >
            «{quote}»
          </div>
        )}

        {/* Context */}
        {link.context && (
          <div className="text-[11px] text-slate-500">{link.context}</div>
        )}

        {/* Deep link */}
        {deepLink && (
          <button
            type="button"
            onClick={() => navigate(deepLink)}
            className="mt-1 inline-flex items-center gap-1.5 rounded bg-indigo-600 px-2.5 py-1.5 text-[12px] font-semibold text-white shadow-sm transition-colors hover:bg-indigo-700"
          >
            <ExternalLink size={14} aria-hidden="true" />
            Перейти к источнику
          </button>
        )}
      </div>
    </article>
  );
}

interface FreeformCiteProps {
  link: NodeSourceDto;
  source: SourceDto | undefined;
  authority: AuthorityDto | undefined;
  onDetach: (sourceId: string) => void;
}

/**
 * Карточка freeform подкрепления (mode LEGACY) - привязка через
 * существующий AddSourceModal. Slate background, badge «Свободная»,
 * legacySnapshot из бэка как location.
 */
function FreeformCite({ link, source, authority, onDetach }: FreeformCiteProps) {
  const sourceType = source?.sourceType;
  const kindLabel = sourceType ? SOURCE_TYPE_LABEL[sourceType] : 'источник';
  const title = source?.title ?? '(удалён из справочника)';
  const citation = source?.citation;
  const quote = link.quote;
  const isRtl = hasArabicScript(quote);
  const authorMeta = authority
    ? [authority.era, authority.madhab].filter(Boolean).join(' · ')
    : undefined;
  const hasUrl = sourceType === 'URL' && Boolean(citation);
  const snapshot = link.legacySnapshot;

  return (
    <article className="group rounded-md border border-slate-200 bg-slate-50/60 px-2.5 py-2.5">
      <div className="mb-1.5 flex items-start gap-2">
        <span className="inline-flex items-center gap-1.5 rounded border border-slate-200 bg-slate-100 px-2 py-0.5 text-[11px] font-bold uppercase tracking-wider text-slate-700">
          <Quote size={13} aria-hidden="true" />
          Свободная
        </span>
        <span className="font-mono text-[11px] uppercase tracking-wide text-slate-500">
          {kindLabel}
        </span>
        <span className="flex-1" />
        {!hasUrl && sourceType === 'URL' && (
          <span
            className="inline-flex items-center gap-1.5 text-[11px] text-amber-700"
            title="URL не указан"
          >
            <AlertCircle size={13} aria-hidden="true" />
            без URL
          </span>
        )}
        <button
          type="button"
          aria-label="Отвязать опору"
          onClick={() => link.sourceId && onDetach(link.sourceId)}
          className="rounded p-1 text-slate-400 opacity-0 transition-opacity hover:bg-red-50 hover:text-red-600 focus:opacity-100 group-hover:opacity-100"
        >
          <Trash2 size={14} aria-hidden="true" />
        </button>
      </div>

      {authority && (
        <div className="mb-1.5 flex items-center gap-1.5 text-[12px]">
          <UserIcon size={13} className="text-slate-400" aria-hidden="true" />
          <span className="font-medium text-slate-700">{authority.name}</span>
          {authorMeta && (
            <span className="font-mono text-[11px] text-slate-500">· {authorMeta}</span>
          )}
        </div>
      )}

      <div className="text-[12.5px] font-semibold leading-snug text-slate-900">{title}</div>

      {(citation || snapshot) && (
        <div className="mt-0.5 font-mono text-[11px] text-slate-500">
          {citation}
          {citation && snapshot && ' · '}
          {snapshot}
        </div>
      )}

      {quote && (
        <div
          dir={isRtl ? 'rtl' : 'ltr'}
          className={`mt-1.5 border-l-2 border-slate-300 pl-2 text-[12px] italic leading-relaxed text-slate-600 ${
            isRtl ? 'font-naskh text-[13px] not-italic leading-loose' : ''
          }`}
        >
          «{quote}»
        </div>
      )}

      {link.context && (
        <div className="mt-1.5 text-[11px] text-slate-500">{link.context}</div>
      )}
    </article>
  );
}

export default NodeCitationsSection;
