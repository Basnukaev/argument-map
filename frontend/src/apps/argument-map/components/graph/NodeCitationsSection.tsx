import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import {
  Anchor,
  AlertCircle,
  BookOpen,
  ChevronDown,
  Plus,
  Quote,
  Trash2,
  User as UserIcon,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import PanelSection from '@/apps/argument-map/components/graph/PanelSection';
import AddSourceModal from '@/apps/argument-map/components/graph/AddSourceModal';
import CitationPicker from '@/shared/components/citation/CitationPicker';
import { SourceCard } from '@/shared/components/citation/sourceCard';
import { apiGetRaw, apiDeleteRaw, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useSourceDetailPanelStore } from '@/shared/stores/sourceDetailPanelStore';
import { SOURCE_TYPE_LABEL } from '@/apps/argument-map/utils/attachmentTokens';
import { hasArabicScript, useT } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type SourceDto = components['schemas']['SourceResponse'];
type AuthorityDto = components['schemas']['AuthorityResponse'];
type NodeSourceDto = components['schemas']['NodeSourceResponse'];

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
  const t = useT();
  const [state, setState] = useState<SourcesState>({ kind: 'loading' });
  const [addSourceOpen, setAddSourceOpen] = useState(false);
  const [citationPickerOpen, setCitationPickerOpen] = useState(false);

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
      apiGetRaw<SourceDto[]>(`/api/v1/sources`, { signal: controller.signal }),
      apiGetRaw<AuthorityDto[]>(`/api/v1/authorities`, { signal: controller.signal }),
    ])
      .then(([links, sources, authorities]) => {
        if (controller.signal.aborted) return;
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

  return (
    <>
      <PanelSection
        icon={Anchor}
        title={t('node.section.support')}
        count={state.kind === 'loaded' ? state.data.links.length : undefined}
        defaultOpen={false}
      >
        <CitationsList state={state} onDetach={detachNodeSource} />
        {/* Vertical stack - текст кнопки "Привести источник" длинный и не
            вмещается в side-by-side layout в узком detail panel (~360px) */}
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
            icon={Plus}
            onClick={() => setAddSourceOpen(true)}
            disabled={!nodeId}
            full
          >
            {t('node.citation_add_free')}
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
          targetType="nodes"
          targetId={nodeId}
          targetLabel={nodeContent}
          onClose={() => setCitationPickerOpen(false)}
          onCreated={reloadSources}
        />
      )}
    </>
  );
}

interface CitationsListProps {
  state: SourcesState;
  /** Передаётся nodeSourceId (link.id) - FK variant A */
  onDetach: (nodeSourceId: string) => void;
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
  return (
    <div className="space-y-2">
      {links.map((link, idx) => {
        const source = link.sourceId ? sourceLookup.get(link.sourceId) : undefined;
        const authorityFallback = source?.authorityId
          ? authorityLookup.get(source.authorityId)
          : undefined;
        const key = link.sourceId ?? `${link.nodeId}-${idx}`;
        if (isLibraryMode(link.mode)) {
          const deepLink = buildDeepLink(link);
          const titleLatin = pickLatinTitle(source, link.citation?.book?.title);
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
            <SourceCard
              key={key}
              link={link}
              titleLatin={titleLatin}
              onDelete={link.id ? () => onDetach(link.id!) : undefined}
              onPrimaryAction={deepLink ? () => navigate(deepLink) : undefined}
              onTitleClick={openPanel}
              sourceId={source?.id ?? link.sourceId}
              sourceType={source?.sourceType}
            />
          );
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

interface FreeformCiteProps {
  link: NodeSourceDto;
  source: SourceDto | undefined;
  authority: AuthorityDto | undefined;
  /** Передаётся nodeSourceId (link.id) - FK variant A */
  onDetach: (nodeSourceId: string) => void;
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
