import { useState } from 'react';
import { Quote, Plus, Trash2, User as UserIcon, Link as LinkIcon } from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import PanelSection from '@/apps/argument-map/components/graph/PanelSection';
import AddSourceModal from '@/apps/argument-map/components/graph/AddSourceModal';
import { apiGetRaw, apiDeleteRaw, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { SOURCE_TYPE_LABEL } from '@/apps/argument-map/utils/attachmentTokens';
import { hasArabicScript } from '@/apps/argument-map/components/graph/nodeDetailsUtils';
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
}

/**
 * Секция "Цитаты" - lazy-loaded список цитат (NodeSource), их источников
 * (Source) и авторитетов (Authority) с возможностью detach и привязать
 * новую цитату через {@link AddSourceModal}.
 *
 * Lazy-load: данные грузятся только при первом раскрытии PanelSection
 * (onFirstOpen) - не блокируем рендер панели для узлов без цитат.
 */
function NodeCitationsSection({ nodeId }: Props) {
  const [state, setState] = useState<SourcesState>({ kind: 'not-loaded' });
  const [addSourceOpen, setAddSourceOpen] = useState(false);

  async function loadSources() {
    if (!nodeId) return;
    setState({ kind: 'loading' });
    try {
      const [links, sources, authorities] = await Promise.all([
        apiGetRaw<NodeSourceDto[]>(`/api/v1/nodes/${nodeId}/sources`),
        apiGetRaw<SourceDto[]>(`/api/v1/sources`),
        apiGetRaw<AuthorityDto[]>(`/api/v1/authorities`),
      ]);
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
    } catch (e: unknown) {
      setState({ kind: 'error', message: formatApiError(e, 'Не удалось загрузить цитаты') });
    }
  }

  async function detachSource(sourceId: string) {
    if (!nodeId) return;
    if (state.kind !== 'loaded') return;
    const previous = state.data.links;
    const next = previous.filter((l) => l.sourceId !== sourceId);
    setState({ kind: 'loaded', data: { ...state.data, links: next } });
    try {
      await apiDeleteRaw(`/api/v1/nodes/${nodeId}/sources/${sourceId}`);
    } catch (e: unknown) {
      toast.error(formatApiError(e, 'Не удалось отвязать цитату'));
      setState({ kind: 'loaded', data: { ...state.data, links: previous } });
    }
  }

  return (
    <>
      <PanelSection
        icon={Quote}
        title="Цитаты"
        count={state.kind === 'loaded' ? state.data.links.length : undefined}
        defaultOpen={false}
        onFirstOpen={loadSources}
      >
        <CitationsList state={state} onDetach={detachSource} />
        <Button
          type="button"
          variant="ghost"
          size="sm"
          icon={Plus}
          onClick={() => setAddSourceOpen(true)}
          disabled={!nodeId}
          className="mt-2 w-full justify-center"
        >
          Привязать цитату
        </Button>
      </PanelSection>

      {addSourceOpen && nodeId && (
        <AddSourceModal
          nodeId={nodeId}
          onClose={() => setAddSourceOpen(false)}
          onAttached={loadSources}
        />
      )}
    </>
  );
}

interface CitationsListProps {
  state: SourcesState;
  onDetach: (sourceId: string) => void;
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
      <p className="text-[12px] italic text-slate-500">К узлу не привязано ни одной цитаты</p>
    );
  }
  return (
    <div className="space-y-2">
      {links.map((link) => {
        const source = link.sourceId ? sourceLookup.get(link.sourceId) : undefined;
        const sourceType = source?.sourceType;
        const kindLabel = sourceType ? SOURCE_TYPE_LABEL[sourceType] : 'источник';
        const title = source?.title ?? '(удалён из справочника)';
        const citation = source?.citation;
        const quote = link.quote;
        const location = link.location;
        const context = link.context;
        const authority = source?.authorityId ? authorityLookup.get(source.authorityId) : undefined;
        const authorMeta = authority
          ? [authority.era, authority.madhab].filter(Boolean).join(' · ')
          : undefined;
        const isRtl = hasArabicScript(quote);
        return (
          <article
            key={link.sourceId}
            className="group rounded-md border border-slate-200 bg-slate-50/60 p-3"
          >
            <div className="mb-1 flex items-center justify-between gap-2">
              <span className="font-mono text-[11px] font-semibold uppercase text-slate-500">
                {kindLabel}
              </span>
              <div className="flex items-center gap-1">
                <LinkIcon size={12} className="text-slate-400" aria-hidden="true" />
                <button
                  type="button"
                  aria-label="Отвязать цитату"
                  onClick={() => link.sourceId && onDetach(link.sourceId)}
                  className="rounded p-1 text-slate-400 opacity-0 transition-opacity hover:bg-red-50 hover:text-red-600 focus:opacity-100 group-hover:opacity-100"
                >
                  <Trash2 size={12} aria-hidden="true" />
                </button>
              </div>
            </div>

            {authority && (
              <div className="mb-1.5 flex items-center gap-1.5 text-[11px]">
                <UserIcon size={11} className="text-slate-400" aria-hidden="true" />
                <span className="font-medium text-slate-700">{authority.name}</span>
                {authorMeta && (
                  <span className="font-mono text-[10px] text-slate-500">· {authorMeta}</span>
                )}
              </div>
            )}

            <div className="text-[12px] font-semibold text-slate-800">{title}</div>

            {(citation || location) && (
              <div className="mt-0.5 font-mono text-[11px] text-slate-500">
                {citation}
                {citation && location && ' · '}
                {location && <span title="место в источнике">{location}</span>}
              </div>
            )}

            {quote && (
              <div
                dir={isRtl ? 'rtl' : 'ltr'}
                className={`mt-1 border-l-2 border-slate-300 pl-2 text-[12px] italic leading-relaxed text-slate-600 ${
                  isRtl ? 'font-serif text-[13px] not-italic leading-loose' : ''
                }`}
              >
                «{quote}»
              </div>
            )}

            {context && <div className="mt-1 text-[11px] text-slate-500">{context}</div>}
          </article>
        );
      })}
    </div>
  );
}

export default NodeCitationsSection;
