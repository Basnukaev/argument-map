import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import {
  Pencil,
  ChevronDown,
  ChevronRight,
  X,
  MessageSquareQuote,
  Info,
  History,
  Quote,
  Trash2,
  Plus,
  User as UserIcon,
  Link as LinkIcon,
  type LucideIcon,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import IconButton from '@/shared/components/ui/IconButton';
import StatusBadge from '@/shared/components/ui/StatusBadge';
import { apiGetRaw, apiPatchRaw, apiDeleteRaw, ApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import type { components } from '@/shared/api/types';
import { NODE_TYPE_TOKENS, type NodeType, type NodeStatus } from '@/shared/utils/designTokens';
import { SOURCE_TYPE_LABEL } from '@/apps/argument-map/utils/attachmentTokens';
import AddSourceModal from './AddSourceModal';

type NodeDto = components['schemas']['NodeResponse'];
type RevisionDto = components['schemas']['RevisionResponse'];
type SourceDto = components['schemas']['SourceResponse'];
type AuthorityDto = components['schemas']['AuthorityResponse'];
type NodeSourceDto = components['schemas']['NodeSourceResponse'];

interface CitationsData {
  links: NodeSourceDto[];
  /** sourceId → SourceDto - название/тип/citation/authorityId */
  sourceLookup: Map<string, SourceDto>;
  /** authorityId → AuthorityDto - имя/эра/мазхаб (нужно для трёхуровневой
   * модели ADR-017: Source.authorityId → Authority) */
  authorityLookup: Map<string, AuthorityDto>;
}

type SourcesState =
  | { kind: 'not-loaded' }
  | { kind: 'loading' }
  | { kind: 'loaded'; data: CitationsData }
  | { kind: 'error'; message: string };

/** Содержит ли строка арабские символы - триггер для RTL/naskh-рендера.
 * Покрывает блоки Unicode: Arabic, Arabic Supplement, Arabic Extended-A,
 * Arabic Presentation Forms-A/B (huruf, harakat, formed/joined glyphs). */
// eslint-disable-next-line no-irregular-whitespace -- U+FEFF в Arabic Presentation Forms-B
const ARABIC_SCRIPT = /[؀-ۿݐ-ݿࢠ-ࣿﭐ-﷿ﹰ-﻿]/;

function hasArabicScript(text?: string): boolean {
  if (!text) return false;
  return ARABIC_SCRIPT.test(text);
}

type RevisionsState =
  | { kind: 'not-loaded' }
  | { kind: 'loading' }
  | { kind: 'loaded'; revisions: RevisionDto[] }
  | { kind: 'error'; message: string };

interface Props {
  node: NodeDto;
  onClose: () => void;
  /** вызывается после успешного PATCH - чтобы родитель refetch'нул граф */
  onUpdated: () => void;
  /** если true - панель сразу открывается в режиме редактирования контента */
  initialEditing?: boolean;
}

const DATE_FORMAT = new Intl.DateTimeFormat('ru-RU', {
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

function formatDate(iso?: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return DATE_FORMAT.format(d);
}

function shortId(id?: string): string {
  if (!id) return '—';
  return id.slice(0, 8);
}

function errorMessage(e: unknown, fallback: string): string {
  if (e instanceof ApiError) {
    return e.problem.detail || e.problem.title || fallback;
  }
  if (e instanceof Error) return e.message;
  return fallback;
}

interface SectionProps {
  icon: LucideIcon;
  title: string;
  count?: number | string;
  defaultOpen?: boolean;
  /** вызывается при первом раскрытии секции - удобно для lazy-load */
  onFirstOpen?: () => void;
  children: ReactNode;
}

function PanelSection({
  icon: Icon,
  title,
  count,
  defaultOpen = true,
  onFirstOpen,
  children,
}: SectionProps) {
  const [open, setOpen] = useState(defaultOpen);
  const firedRef = useRef(false);

  useEffect(() => {
    if (open && !firedRef.current) {
      firedRef.current = true;
      onFirstOpen?.();
    }
  }, [open, onFirstOpen]);

  return (
    <section className="border-t border-slate-200">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex w-full items-center gap-2 px-5 py-3 text-left transition-colors hover:bg-slate-50"
      >
        <Icon size={14} className="text-slate-500" aria-hidden="true" />
        <span className="text-[12px] font-semibold uppercase tracking-wider text-slate-700">
          {title}
        </span>
        {count !== undefined && (
          <span className="text-[11px] font-mono text-slate-400">{count}</span>
        )}
        <ChevronDown
          size={14}
          className={`ml-auto text-slate-400 transition-transform ${open ? '' : '-rotate-90'}`}
          aria-hidden="true"
        />
      </button>
      {open && <div className="px-5 pb-4">{children}</div>}
    </section>
  );
}

function NodeDetailsPanel({ node, onClose, onUpdated, initialEditing = false }: Props) {
  const nodeType: NodeType = node.nodeType ?? 'CLAIM';
  const typeToken = NODE_TYPE_TOKENS[nodeType];
  const TypeIcon = typeToken.Icon;
  const status: NodeStatus = node.status ?? 'UNVERIFIED';
  const content = node.content ?? '';
  const wasUpdated =
    node.updatedAt && node.createdAt && node.updatedAt !== node.createdAt;

  const [editing, setEditing] = useState(initialEditing);
  const [draft, setDraft] = useState(content);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const [historyOpen, setHistoryOpen] = useState(false);
  const [revisionsState, setRevisionsState] = useState<RevisionsState>({ kind: 'not-loaded' });

  const [sourcesState, setSourcesState] = useState<SourcesState>({ kind: 'not-loaded' });
  const [addSourceOpen, setAddSourceOpen] = useState(false);

  async function loadSources() {
    if (!node.id) return;
    setSourcesState({ kind: 'loading' });
    try {
      const [links, sources, authorities] = await Promise.all([
        apiGetRaw<NodeSourceDto[]>(`/api/v1/nodes/${node.id}/sources`),
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
      setSourcesState({
        kind: 'loaded',
        data: { links, sourceLookup, authorityLookup },
      });
    } catch (e: unknown) {
      setSourcesState({ kind: 'error', message: errorMessage(e, 'Не удалось загрузить цитаты') });
    }
  }

  async function detachSource(sourceId: string) {
    if (!node.id) return;
    if (sourcesState.kind !== 'loaded') return;
    const previous = sourcesState.data.links;
    const next = previous.filter((l) => l.sourceId !== sourceId);
    setSourcesState({
      kind: 'loaded',
      data: { ...sourcesState.data, links: next },
    });
    try {
      await apiDeleteRaw(`/api/v1/nodes/${node.id}/sources/${sourceId}`);
    } catch (e: unknown) {
      toast.error(errorMessage(e, 'Не удалось отвязать цитату'));
      setSourcesState({
        kind: 'loaded',
        data: { ...sourcesState.data, links: previous },
      });
    }
  }

  function toggleHistory() {
    if (historyOpen) {
      setHistoryOpen(false);
      return;
    }
    setHistoryOpen(true);
    if (revisionsState.kind === 'not-loaded') {
      void loadRevisions();
    }
  }

  async function loadRevisions() {
    if (!node.id) return;
    setRevisionsState({ kind: 'loading' });
    try {
      const list = await apiGetRaw<RevisionDto[]>(`/api/v1/nodes/${node.id}/revisions`);
      setRevisionsState({ kind: 'loaded', revisions: list });
    } catch (e: unknown) {
      const message =
        e instanceof ApiError
          ? e.problem.detail || e.problem.title
          : e instanceof Error
            ? e.message
            : 'Не удалось загрузить историю';
      setRevisionsState({ kind: 'error', message });
    }
  }

  function startEdit() {
    setDraft(content);
    setSaveError(null);
    setEditing(true);
  }

  function cancelEdit() {
    if (saving) return;
    setEditing(false);
    setSaveError(null);
  }

  async function save() {
    if (!node.id) return;
    const trimmed = draft.trim();
    if (!trimmed || trimmed === content) {
      setEditing(false);
      return;
    }
    setSaving(true);
    setSaveError(null);
    try {
      await apiPatchRaw<NodeDto>(`/api/v1/nodes/${node.id}`, { content: trimmed });
      setEditing(false);
      onUpdated();
    } catch (e: unknown) {
      if (e instanceof ApiError) {
        const fieldErrors = e.problem.errors?.map((er) => `${er.field}: ${er.message}`).join('; ');
        setSaveError(fieldErrors || e.problem.detail || e.problem.title);
      } else if (e instanceof Error) {
        setSaveError(e.message);
      } else {
        setSaveError('Не удалось сохранить');
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <aside
      role="complementary"
      aria-label="Детали узла"
      className="absolute right-0 top-0 bottom-0 z-10 flex w-[400px] flex-col border-l border-slate-200 bg-white shadow-xl"
    >
      <header
        className={`relative border-b border-slate-200 bg-gradient-to-b ${typeToken.headerGradient} p-5`}
      >
        <div className="absolute right-3 top-3">
          <IconButton icon={X} label="Закрыть панель" size="sm" onClick={onClose} />
        </div>
        <div className="flex items-center gap-2">
          <span
            className={`grid h-8 w-8 place-items-center rounded-md ${typeToken.iconBg} ${typeToken.iconText}`}
          >
            <TypeIcon size={16} aria-hidden="true" />
          </span>
          <div className="flex flex-col">
            <h2 className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
              {typeToken.key} · {typeToken.label}
            </h2>
            <span className="font-mono text-[12px] text-slate-400">{shortId(node.id)}</span>
          </div>
        </div>
        <div className="mt-3 flex items-center gap-2">
          <StatusBadge status={status} size="lg" />
        </div>
      </header>

      <div className="flex-1 overflow-y-auto">
        <PanelSection icon={MessageSquareQuote} title="Содержание" defaultOpen>
          {!editing ? (
            <div>
              {content ? (
                <p className="whitespace-pre-wrap break-words text-[14px] leading-relaxed text-slate-800 text-pretty">
                  {content}
                </p>
              ) : (
                <p className="text-[14px] italic text-slate-400">(пусто)</p>
              )}
              <Button
                type="button"
                variant="ghost"
                size="sm"
                icon={Pencil}
                onClick={startEdit}
                className="-ml-2 mt-3"
              >
                Редактировать
              </Button>
            </div>
          ) : (
            <div className="space-y-2">
              <textarea
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                rows={6}
                maxLength={10000}
                disabled={saving}
                aria-label="Содержание узла"
                className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-[13px] text-slate-900 outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
              />
              {saveError && (
                <div className="rounded-md border border-red-300 bg-red-50 p-2 text-[12px] text-red-800">
                  {saveError}
                </div>
              )}
              <div className="flex justify-end gap-2">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={cancelEdit}
                  disabled={saving}
                >
                  Отмена
                </Button>
                <Button
                  type="button"
                  size="sm"
                  onClick={save}
                  disabled={saving || !draft.trim()}
                >
                  {saving ? 'Сохраняем' : 'Сохранить'}
                </Button>
              </div>
            </div>
          )}
        </PanelSection>

        <PanelSection icon={Info} title="Метаданные" defaultOpen>
          <dl className="grid grid-cols-[100px_1fr] gap-x-3 gap-y-2 text-[12px]">
            <dt className="text-slate-500">Создан</dt>
            <dd className="text-slate-700">{formatDate(node.createdAt)}</dd>

            {wasUpdated && (
              <>
                <dt className="text-slate-500">Обновлён</dt>
                <dd className="text-slate-700">{formatDate(node.updatedAt)}</dd>
              </>
            )}

            <dt className="text-slate-500">Автор</dt>
            <dd className="font-mono text-slate-700" title={node.createdBy}>
              {shortId(node.createdBy)}
            </dd>

            <dt className="text-slate-500">ID</dt>
            <dd className="font-mono text-slate-700" title={node.id}>
              {shortId(node.id)}
            </dd>
          </dl>
        </PanelSection>

        {nodeType !== 'QUESTION' && (
          <PanelSection
            icon={Quote}
            title="Цитаты"
            count={sourcesState.kind === 'loaded' ? sourcesState.data.links.length : undefined}
            defaultOpen={false}
            onFirstOpen={loadSources}
          >
            <CitationsContent state={sourcesState} onDetach={detachSource} />
            <Button
              type="button"
              variant="ghost"
              size="sm"
              icon={Plus}
              onClick={() => setAddSourceOpen(true)}
              disabled={!node.id}
              className="mt-2 w-full justify-center"
            >
              Привязать цитату
            </Button>
          </PanelSection>
        )}

        <section className="border-t border-slate-200">
          <button
            type="button"
            onClick={toggleHistory}
            aria-expanded={historyOpen}
            className="flex w-full items-center gap-2 px-5 py-3 text-left transition-colors hover:bg-slate-50"
          >
            <History size={14} className="text-slate-500" aria-hidden="true" />
            <span className="text-[12px] font-semibold uppercase tracking-wider text-slate-700">
              История изменений
            </span>
            {revisionsState.kind === 'loaded' && (
              <span className="text-[11px] font-mono text-slate-400">
                {revisionsState.revisions.length}
              </span>
            )}
            {historyOpen ? (
              <ChevronDown size={14} className="ml-auto text-slate-400" aria-hidden="true" />
            ) : (
              <ChevronRight size={14} className="ml-auto text-slate-400" aria-hidden="true" />
            )}
          </button>

          {historyOpen && (
            <div className="px-5 pb-4 space-y-2">
              {revisionsState.kind === 'loading' && (
                <p className="text-[12px] text-slate-500">Загрузка</p>
              )}
              {revisionsState.kind === 'error' && (
                <p className="text-[12px] text-red-700">Ошибка: {revisionsState.message}</p>
              )}
              {revisionsState.kind === 'loaded' && revisionsState.revisions.length === 0 && (
                <p className="text-[12px] italic text-slate-500">Изменений ещё не было</p>
              )}
              {revisionsState.kind === 'loaded' &&
                revisionsState.revisions.length > 0 &&
                [...revisionsState.revisions]
                  .sort((a, b) => (b.changedAt ?? '').localeCompare(a.changedAt ?? ''))
                  .map((r) => (
                    <article
                      key={r.id}
                      className="overflow-hidden rounded-md border border-slate-200 bg-white"
                    >
                      <header className="flex items-center justify-between border-b border-slate-200 bg-slate-50 px-3 py-1.5 text-[11px]">
                        <span className="font-mono text-slate-500">ревизия</span>
                        <span className="text-slate-500">
                          {formatDate(r.changedAt)} ·{' '}
                          <span className="font-mono" title={r.changedBy}>
                            {shortId(r.changedBy)}
                          </span>
                        </span>
                      </header>
                      <div className="divide-y divide-slate-100 text-[12px] font-mono">
                        {r.contentBefore && (
                          <div className="bg-red-50/40 px-3 py-1.5 text-red-800 whitespace-pre-wrap break-words">
                            <span className="select-none text-red-500">- </span>
                            {r.contentBefore}
                          </div>
                        )}
                        {r.contentAfter && (
                          <div className="bg-emerald-50/40 px-3 py-1.5 text-emerald-800 whitespace-pre-wrap break-words">
                            <span className="select-none text-emerald-600">+ </span>
                            {r.contentAfter}
                          </div>
                        )}
                      </div>
                    </article>
                  ))}
            </div>
          )}
        </section>
      </div>

      {addSourceOpen && node.id && (
        <AddSourceModal
          nodeId={node.id}
          onClose={() => setAddSourceOpen(false)}
          onAttached={loadSources}
        />
      )}
    </aside>
  );
}

interface CitationsContentProps {
  state: SourcesState;
  onDetach: (sourceId: string) => void;
}

function CitationsContent({ state, onDetach }: CitationsContentProps) {
  if (state.kind === 'not-loaded' || state.kind === 'loading') {
    return <p className="text-[12px] text-slate-500">Загрузка</p>;
  }
  if (state.kind === 'error') {
    return <p className="text-[12px] text-red-700">Ошибка: {state.message}</p>;
  }
  const { links, sourceLookup, authorityLookup } = state.data;
  if (links.length === 0) {
    return (
      <p className="text-[12px] italic text-slate-500">
        К узлу не привязано ни одной цитаты
      </p>
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
        const authority = source?.authorityId
          ? authorityLookup.get(source.authorityId)
          : undefined;
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

            {context && (
              <div className="mt-1 text-[11px] text-slate-500">{context}</div>
            )}
          </article>
        );
      })}
    </div>
  );
}

export default NodeDetailsPanel;
