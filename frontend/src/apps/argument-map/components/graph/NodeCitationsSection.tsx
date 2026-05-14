import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import {
  Anchor,
  AlertCircle,
  BookOpen,
  ChevronDown,
  ExternalLink,
  Plus,
  Quote,
  Trash2,
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
    <div className="space-y-1.5">
      {links.map((link, idx) => {
        const source = link.sourceId ? sourceLookup.get(link.sourceId) : undefined;
        const authorityFallback = source?.authorityId
          ? authorityLookup.get(source.authorityId)
          : undefined;
        const key = link.sourceId ?? `${link.nodeId}-${idx}`;
        if (isLibraryMode(link.mode)) {
          return <LibraryCite key={key} link={link} source={source} onDetach={onDetach} />;
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

/**
 * Пара строк: arabic (RTL/naskh) + transliteration (LTR). Принцип P1-P2
 * mixed-script: каждая строка - один dominant direction, arabic и
 * transliteration в разных строках, не inline mixing.
 */
function ScriptPair({
  arabic,
  translit,
  arabicClass = 'text-[14px] font-medium text-slate-900 leading-snug',
  translitClass = 'text-[12px] text-slate-600 italic',
}: {
  arabic?: string | null;
  translit?: string | null;
  arabicClass?: string;
  translitClass?: string;
}) {
  const showArabic = arabic && hasArabicScript(arabic);
  const showTranslit = translit && translit !== arabic && !hasArabicScript(translit);
  if (!showArabic && !showTranslit) return null;
  return (
    <>
      {showArabic && (
        <div dir="rtl" className={`font-naskh text-start ${arabicClass}`}>
          {arabic}
        </div>
      )}
      {showTranslit && (
        <div dir="ltr" className={`text-start ${translitClass}`}>
          {translit}
        </div>
      )}
    </>
  );
}

interface LibraryCiteProps {
  link: NodeSourceDto;
  source: SourceDto | undefined;
  onDetach: (sourceId: string) => void;
}

/**
 * Library-backed citation card (mode TEXT/PDF/REGION). Native <details>
 * collapse - <summary> одна LTR строка с компактной локацией, развёрнутый
 * контент - 6 структурных блоков. Каждая строка - один direction (P1).
 * Arabic content и cyrillic transliteration в разных строках (P2).
 */
function LibraryCite({ link, source, onDetach }: LibraryCiteProps) {
  const navigate = useNavigate();
  const c: CitationDto = link.citation ?? {};
  const { authority, book, muhaqqiq, publisher, publicationPlace, location, pdf } = c;

  const deepLink = buildDeepLink(link);

  // Summary - однострочный preview для свёрнутого state. Только LTR содержимое:
  // ru transliteration (если есть source.title) + locator (Т.X стр.Y / PDF стр.Y)
  const summaryTitle = pickLatinTitle({ sourceTitle: source?.title, bookTitle: book?.title });
  const locator = buildLocator({ location, pdf });

  // Editions: publisher · place · edition в одну LTR строку
  const editionParts: string[] = [];
  if (publisher?.name) editionParts.push(`изд. ${publisher.name}`);
  if (publicationPlace?.name) editionParts.push(publicationPlace.name);
  if (book?.editionNumber != null) editionParts.push(`${book.editionNumber}-е изд.`);

  // Years в LTR mono. Arabic indicators هـ оборачиваются в isolate spans,
  // иначе они flip'ают соседние цифры (bidi reorder strong RTL chars)
  const yearHijri = book?.publishedYearHijri;
  const yearGregorian = book?.publishedYearGregorian;
  const hasYears = yearHijri != null || yearGregorian != null;

  return (
    <details className="group/c relative overflow-hidden rounded-md border border-slate-200 bg-white open:border-indigo-300">
      <div className="absolute bottom-0 start-0 top-0 w-[3px] bg-indigo-600" />
      <summary className="flex cursor-pointer list-none items-center gap-2 py-2 pe-2 ps-3.5 hover:bg-slate-50/50">
        <span
          className="inline-flex items-center gap-1 rounded border border-indigo-200 bg-indigo-50 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wider text-indigo-700"
          aria-label="Из библиотеки"
        >
          <BookOpen size={11} aria-hidden="true" />
          Из библиотеки
        </span>
        <span className="flex-1 truncate text-[12.5px] text-slate-800" dir="ltr">
          {summaryTitle}
          {locator && <span className="ms-1.5 font-mono text-[11px] text-slate-500">· {locator}</span>}
        </span>
        <button
          type="button"
          aria-label="Отвязать опору"
          onClick={(e) => {
            e.preventDefault();
            if (link.sourceId) onDetach(link.sourceId);
          }}
          className="rounded p-1 text-slate-400 opacity-0 transition-opacity hover:bg-red-50 hover:text-red-600 focus:opacity-100 group-hover/c:opacity-100"
        >
          <Trash2 size={13} aria-hidden="true" />
        </button>
        <ChevronDown
          size={14}
          className="text-slate-400 transition-transform group-open/c:rotate-180"
          aria-hidden="true"
        />
      </summary>

      <div className="space-y-2 border-t border-slate-100 px-3.5 py-2.5">
        {/* Author block: pair arabic full_name / cyrillic short */}
        {authority && (
          <div className="space-y-0.5">
            <ScriptPair
              arabic={authority.fullName}
              translit={authority.name}
              arabicClass="text-[14px] font-medium text-slate-900 leading-snug"
              translitClass="text-[12px] font-medium text-slate-800 leading-snug"
            />
            {authority.deathYearHijri != null && (
              <div dir="ltr" className="text-start font-mono text-[11px] text-slate-500">
                г. смерти: {authority.deathYearHijri}{' '}
                <span dir="rtl" style={{ unicodeBidi: 'isolate' }} className="font-naskh">
                  هـ
                </span>
              </div>
            )}
          </div>
        )}

        {/* Book title block: pair arabic title / ru transliteration */}
        <div className="space-y-0.5">
          <ScriptPair
            arabic={book?.title}
            translit={pickLatinTitle({ sourceTitle: source?.title, bookTitle: book?.title })}
            arabicClass="text-[15px] font-bold text-slate-900 leading-snug"
            translitClass="text-[12.5px] font-semibold text-slate-800 leading-snug"
          />
        </div>

        {/* Muhaqqiq block: «тахкик» label LTR + arabic/translit pair */}
        {muhaqqiq && (
          <div className="space-y-0.5">
            <div dir="ltr" className="text-start text-[11px] uppercase tracking-wider text-slate-500">
              тахкик
            </div>
            <ScriptPair
              arabic={muhaqqiq.fullName ?? muhaqqiq.name}
              translit={
                muhaqqiq.fullName && !hasArabicScript(muhaqqiq.fullName)
                  ? muhaqqiq.fullName
                  : muhaqqiq.name && !hasArabicScript(muhaqqiq.name)
                    ? muhaqqiq.name
                    : null
              }
              arabicClass="text-[13px] text-slate-800 leading-snug"
              translitClass="text-[12px] text-slate-700"
            />
          </div>
        )}

        {/* Publisher/place/edition - LTR строка */}
        {editionParts.length > 0 && (
          <div dir="ltr" className="text-start text-[12px] text-slate-600">
            {editionParts.join(' · ')}
          </div>
        )}

        {/* Years - LTR mono. Arabic indicators isolated чтобы не flip'ать цифры */}
        {hasYears && (
          <div dir="ltr" className="text-start font-mono text-[11px] text-slate-500">
            {yearHijri != null && (
              <span>
                {yearHijri}{' '}
                <span dir="rtl" style={{ unicodeBidi: 'isolate' }} className="font-naskh">
                  هـ
                </span>
              </span>
            )}
            {yearHijri != null && yearGregorian != null && <span> / </span>}
            {yearGregorian != null && <span>{yearGregorian} м.</span>}
          </div>
        )}

        {/* Location - LTR mono. Том может быть arabic ("المقدمة") - тогда отдельной RTL строкой */}
        {location && (
          <LocationRows location={location} />
        )}

        {/* Quote - native script ScriptPair (quote arabic + ru translation если есть. translation поле future, сейчас null) */}
        {link.quote && (
          <div className="border-s-2 border-indigo-200 ps-2">
            <ScriptPair
              arabic={hasArabicScript(link.quote) ? link.quote : null}
              translit={hasArabicScript(link.quote) ? null : link.quote}
              arabicClass="text-[14px] text-slate-700 leading-loose"
              translitClass="text-[12.5px] italic text-slate-700 leading-relaxed"
            />
          </div>
        )}

        {/* Context (LTR) */}
        {link.context && (
          <div dir="ltr" className="text-start text-[11px] text-slate-500">
            {link.context}
          </div>
        )}

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
    </details>
  );
}

/**
 * Location rows. Если part arabic - две строки (arabic том + LTR mono стр).
 * Иначе одна LTR mono строка с part + стр.
 */
function LocationRows({
  location,
}: {
  location: NonNullable<CitationDto['location']>;
}) {
  const part = location.part ?? null;
  const printedPage = location.printedPage ?? null;
  const pageNumber = location.pageNumber ?? null;
  const pageDisplay = printedPage ?? (pageNumber != null ? String(pageNumber) : null);
  const partIsArabic = part && hasArabicScript(part);

  if (!part && !pageDisplay) return null;

  if (partIsArabic) {
    return (
      <div className="space-y-0.5">
        <div dir="rtl" className="font-naskh text-start text-[12px] text-slate-600">
          {part}
        </div>
        {pageDisplay && (
          <div dir="ltr" className="text-start font-mono text-[11px] text-slate-500">
            стр. {pageDisplay}
          </div>
        )}
      </div>
    );
  }
  const parts: string[] = [];
  if (part) parts.push(`Т. ${part}`);
  if (pageDisplay) parts.push(`стр. ${pageDisplay}`);
  return (
    <div dir="ltr" className="text-start font-mono text-[11px] text-slate-500">
      {parts.join(' · ')}
    </div>
  );
}

/**
 * Локатор для свёрнутого summary - короткий LTR `Т.X стр.Y` или `PDF стр.N`.
 * Если part arabic - используем page only (иначе summary становится mixed-script).
 */
function buildLocator({
  location,
  pdf,
}: {
  location?: CitationDto['location'];
  pdf?: CitationDto['pdf'];
}): string | null {
  if (pdf?.pageNumber != null) return `PDF стр. ${pdf.pageNumber}`;
  if (!location) return null;
  const printedPage = location.printedPage ?? (location.pageNumber != null ? String(location.pageNumber) : null);
  const part = location.part;
  if (part && !hasArabicScript(part)) {
    if (printedPage) return `Т. ${part} · стр. ${printedPage}`;
    return `Т. ${part}`;
  }
  if (printedPage) return `стр. ${printedPage}`;
  return null;
}

/**
 * Выбрать latin/cyrillic title для summary и translit row. Приоритет:
 * 1. source.title - manual ru transliteration через AddSourceModal
 * 2. fallback: book.title если он сам не arabic (manually-created book)
 * 3. иначе null - summary покажет только locator
 */
function pickLatinTitle({
  sourceTitle,
  bookTitle,
}: {
  sourceTitle?: string;
  bookTitle?: string | null;
}): string {
  if (sourceTitle && !hasArabicScript(sourceTitle)) return sourceTitle;
  if (bookTitle && !hasArabicScript(bookTitle)) return bookTitle;
  return '(книга)';
}

interface FreeformCiteProps {
  link: NodeSourceDto;
  source: SourceDto | undefined;
  authority: AuthorityDto | undefined;
  onDetach: (sourceId: string) => void;
}

/**
 * Freeform citation card (mode LEGACY). Symmetric details collapse - summary
 * однострочный с title + freeform marker, развёрнутый - все детали.
 */
function FreeformCite({ link, source, authority, onDetach }: FreeformCiteProps) {
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
    <details className="group/c rounded-md border border-slate-200 bg-slate-50/60 open:bg-slate-50">
      <summary className="flex cursor-pointer list-none items-center gap-2 px-2.5 py-2 hover:bg-slate-100/60">
        <span
          className="inline-flex items-center gap-1 rounded border border-slate-200 bg-slate-100 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wider text-slate-700"
          aria-label="Свободная"
        >
          <Quote size={11} aria-hidden="true" />
          Свободная
        </span>
        <span className="flex-1 truncate text-[12.5px] text-slate-800" dir="ltr">
          {title}
        </span>
        {!hasUrl && sourceType === 'URL' && (
          <span
            className="inline-flex items-center gap-1 text-[11px] text-amber-700"
            title="URL не указан"
          >
            <AlertCircle size={12} aria-hidden="true" />
          </span>
        )}
        <button
          type="button"
          aria-label="Отвязать опору"
          onClick={(e) => {
            e.preventDefault();
            if (link.sourceId) onDetach(link.sourceId);
          }}
          className="rounded p-1 text-slate-400 opacity-0 transition-opacity hover:bg-red-50 hover:text-red-600 focus:opacity-100 group-hover/c:opacity-100"
        >
          <Trash2 size={13} aria-hidden="true" />
        </button>
        <ChevronDown
          size={14}
          className="text-slate-400 transition-transform group-open/c:rotate-180"
          aria-hidden="true"
        />
      </summary>

      <div className="space-y-1.5 border-t border-slate-200 px-2.5 py-2">
        <div dir="ltr" className="text-start font-mono text-[10.5px] uppercase tracking-wide text-slate-500">
          {kindLabel}
        </div>

        {authority && (
          <div dir="ltr" className="text-start text-[12px] text-slate-700">
            <span className="font-medium">{authority.name}</span>
            {authorMeta && <span className="ms-1.5 font-mono text-[11px] text-slate-500">· {authorMeta}</span>}
          </div>
        )}

        {(citation || snapshot) && (
          <div dir="ltr" className="text-start font-mono text-[11px] text-slate-500">
            {citation}
            {citation && snapshot && ' · '}
            {snapshot}
          </div>
        )}

        {quote && (
          <div className="border-s-2 border-slate-300 ps-2">
            <ScriptPair
              arabic={hasArabicScript(quote) ? quote : null}
              translit={hasArabicScript(quote) ? null : quote}
              arabicClass="text-[13px] text-slate-700 leading-loose"
              translitClass="text-[12px] italic text-slate-600 leading-relaxed"
            />
          </div>
        )}

        {link.context && (
          <div dir="ltr" className="text-start text-[11px] text-slate-500">
            {link.context}
          </div>
        )}
      </div>
    </details>
  );
}

export default NodeCitationsSection;
