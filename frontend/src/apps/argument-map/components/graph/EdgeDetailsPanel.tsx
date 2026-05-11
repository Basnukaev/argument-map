import { useState } from 'react';
import type { ReactNode } from 'react';
import {
  Pencil,
  ChevronDown,
  X,
  Network,
  Layers,
  Quote,
  Info,
  ArrowRight,
  type LucideIcon,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import IconButton from '@/shared/components/ui/IconButton';
import { apiPatchRaw, formatApiError } from '@/shared/api/client';
import type { components } from '@/shared/api/types';
import {
  EDGE_TYPE_META,
  getAllowedEdgeTypes,
  getContextualEdgeLabel,
  type EdgeType,
  type NodeType,
} from '@/apps/argument-map/utils/edgeRules';
import {
  EDGE_TYPE_TOKENS,
  NODE_TYPE_TOKENS,
  STATUS_TOKENS,
  type NodeStatus,
} from '@/shared/utils/designTokens';

type EdgeDto = components['schemas']['EdgeResponse'];
type NodeDto = components['schemas']['NodeResponse'];

interface Props {
  edge: EdgeDto;
  fromNode: NodeDto;
  toNode: NodeDto;
  onClose: () => void;
  onUpdated: () => void;
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

const PREVIEW_LEN = 80;

function nodePreview(node: NodeDto): string {
  const content = node.content ?? '';
  return content.length > PREVIEW_LEN
    ? `${content.slice(0, PREVIEW_LEN)}…`
    : content || '(без содержимого)';
}

interface SectionProps {
  icon: LucideIcon;
  title: string;
  defaultOpen?: boolean;
  children: ReactNode;
}

function PanelSection({ icon: Icon, title, defaultOpen = true, children }: SectionProps) {
  const [open, setOpen] = useState(defaultOpen);
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

interface NodeMiniProps {
  node: NodeDto;
}

function NodeMini({ node }: NodeMiniProps) {
  const nodeType: NodeType = node.nodeType ?? 'CLAIM';
  const status: NodeStatus = node.status ?? 'UNVERIFIED';
  const typeToken = NODE_TYPE_TOKENS[nodeType];
  const statusToken = STATUS_TOKENS[status];
  return (
    <div className="relative flex-1 overflow-hidden rounded-md border border-slate-200 bg-slate-50/40 px-2.5 py-2">
      <div
        className={`absolute left-0 top-0 bottom-0 w-[3px] rounded-l-md ${statusToken.bar}`}
        aria-hidden="true"
      />
      <div className={`text-[10px] font-semibold uppercase tracking-wider ${typeToken.chipText}`}>
        {nodeType}
      </div>
      <div className="line-clamp-2 text-[12px] font-medium leading-snug text-slate-800">
        {nodePreview(node)}
      </div>
    </div>
  );
}

// edge gradient background for header - подбираем по edge type через токены
function gradientFor(edgeType: EdgeType): string {
  switch (edgeType) {
    case 'SUPPORTS':
      return 'from-emerald-50/70 to-white';
    case 'REFUTES':
    case 'INVALIDATES':
      return 'from-red-50/70 to-white';
    case 'QUALIFIES':
      return 'from-blue-50/70 to-white';
    case 'RESPONDS_TO':
      return 'from-slate-50/70 to-white';
  }
}

function iconBgFor(edgeType: EdgeType): { bg: string; text: string } {
  switch (edgeType) {
    case 'SUPPORTS':
      return { bg: 'bg-emerald-100', text: 'text-emerald-700' };
    case 'REFUTES':
      return { bg: 'bg-red-100', text: 'text-red-700' };
    case 'INVALIDATES':
      return { bg: 'bg-red-100', text: 'text-red-800' };
    case 'QUALIFIES':
      return { bg: 'bg-blue-100', text: 'text-blue-700' };
    case 'RESPONDS_TO':
      return { bg: 'bg-slate-100', text: 'text-slate-700' };
  }
}

function EdgeDetailsPanel({
  edge,
  fromNode,
  toNode,
  onClose,
  onUpdated,
  initialEditing = false,
}: Props) {
  const fromType: NodeType = fromNode.nodeType ?? 'CLAIM';
  const toType: NodeType = toNode.nodeType ?? 'CLAIM';
  const currentEdgeType: EdgeType = edge.edgeType ?? 'SUPPORTS';
  const currentRationale = edge.rationale ?? '';

  const allowedTypes = getAllowedEdgeTypes(fromType, toType);

  const [editing, setEditing] = useState(initialEditing);
  const [draftType, setDraftType] = useState<EdgeType>(currentEdgeType);
  const [draftRationale, setDraftRationale] = useState(currentRationale);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  function startEdit() {
    setDraftType(currentEdgeType);
    setDraftRationale(currentRationale);
    setSaveError(null);
    setEditing(true);
  }

  function cancelEdit() {
    if (saving) return;
    setEditing(false);
    setSaveError(null);
  }

  async function save() {
    if (!edge.id) return;
    const trimmedRationale = draftRationale.trim();
    const typeChanged = draftType !== currentEdgeType;
    const rationaleChanged = trimmedRationale !== currentRationale;
    if (!typeChanged && !rationaleChanged) {
      setEditing(false);
      return;
    }

    const body: Record<string, string> = {};
    if (typeChanged) body.edgeType = draftType;
    if (rationaleChanged && trimmedRationale) body.rationale = trimmedRationale;
    if (Object.keys(body).length === 0) {
      setEditing(false);
      return;
    }

    setSaving(true);
    setSaveError(null);
    try {
      await apiPatchRaw<EdgeDto>(`/api/v1/edges/${edge.id}`, body);
      setEditing(false);
      onUpdated();
    } catch (e: unknown) {
      setSaveError(formatApiError(e, 'Не удалось сохранить'));
    } finally {
      setSaving(false);
    }
  }

  const contextualLabel = getContextualEdgeLabel(fromType, currentEdgeType, toType);
  const currentMeta = EDGE_TYPE_META[currentEdgeType];
  const HeaderIcon = currentMeta.Icon;
  const iconBg = iconBgFor(currentEdgeType);

  return (
    <aside
      role="complementary"
      aria-label="Детали связи"
      className="absolute right-0 top-0 bottom-0 z-10 flex w-[400px] flex-col border-l border-slate-200 bg-white shadow-xl"
    >
      <header
        className={`relative border-b border-slate-200 bg-gradient-to-b ${gradientFor(currentEdgeType)} p-5`}
      >
        <div className="absolute right-3 top-3">
          <IconButton icon={X} label="Закрыть панель" size="sm" onClick={onClose} />
        </div>
        <div className="flex items-center gap-2">
          <span className={`grid h-8 w-8 place-items-center rounded-md ${iconBg.bg} ${iconBg.text}`}>
            <HeaderIcon size={16} aria-hidden="true" />
          </span>
          <div className="flex min-w-0 flex-col">
            <h2 className="truncate text-[10px] font-semibold uppercase tracking-wider text-slate-500">
              EDGE · {currentMeta.label}
            </h2>
            <span className="font-mono text-[12px] text-slate-400">{shortId(edge.id)}</span>
          </div>
        </div>
        <p className="mt-2 text-[12px] text-slate-700">
          <span className="font-semibold text-slate-800">{contextualLabel}</span>
        </p>
      </header>

      <div className="flex-1 overflow-y-auto">
        <PanelSection icon={Network} title="Связь" defaultOpen>
          <div className="flex items-center gap-2">
            <NodeMini node={fromNode} />
            <ArrowRight size={20} className="shrink-0 text-slate-400" aria-hidden="true" />
            <NodeMini node={toNode} />
          </div>
        </PanelSection>

        <PanelSection icon={Layers} title="Тип связи" defaultOpen>
          <div className="mb-2 flex items-center justify-between">
            {!editing ? (
              <p className="text-[13px] text-slate-900">
                <span className="font-semibold">{currentMeta.label}</span>
                <span className="text-slate-500"> — {currentMeta.hint}</span>
              </p>
            ) : (
              <p className="text-[12px] text-slate-500">Выбери новый тип связи</p>
            )}
            {!editing && (
              <Button
                type="button"
                variant="link"
                size="xs"
                icon={Pencil}
                onClick={startEdit}
              >
                Редактировать
              </Button>
            )}
          </div>

          {editing && (
            <div className="space-y-1.5">
              {allowedTypes.length === 0 && (
                <p className="rounded-md border border-amber-300 bg-amber-50 p-2 text-[12px] text-amber-900">
                  Текущая пара узлов не допускает ни один тип связи (см. ADR-010)
                </p>
              )}
              {allowedTypes.map((value) => {
                const meta = EDGE_TYPE_META[value];
                const token = EDGE_TYPE_TOKENS[value];
                const Icon = meta.Icon;
                const selected = draftType === value;
                return (
                  <label
                    key={value}
                    className={`flex cursor-pointer items-start gap-2 rounded-md border p-2 transition-colors ${
                      selected
                        ? 'border-indigo-500 bg-indigo-50/60 ring-1 ring-indigo-400'
                        : 'border-slate-300 hover:bg-slate-50'
                    }`}
                  >
                    <input
                      type="radio"
                      name="edgeType"
                      value={value}
                      checked={selected}
                      onChange={() => setDraftType(value)}
                      disabled={saving}
                      className="mt-1 accent-indigo-600"
                    />
                    <div className="flex-1">
                      <div className="flex items-center gap-1.5">
                        <Icon
                          size={13}
                          strokeWidth={2.5}
                          style={{ color: token.stroke }}
                          aria-hidden="true"
                        />
                        <span className="text-[12px] font-semibold text-slate-900">
                          {meta.label}
                        </span>
                      </div>
                      <p className="mt-0.5 text-[11px] leading-relaxed text-slate-500">
                        {meta.hint}
                      </p>
                    </div>
                    <svg width="34" height="14" className="mt-1 shrink-0" aria-hidden="true">
                      <line
                        x1="2"
                        y1="7"
                        x2="32"
                        y2="7"
                        stroke={token.stroke}
                        strokeWidth={token.strokeWidth}
                        strokeOpacity={token.opacity ?? 1}
                        strokeDasharray={token.strokeDasharray}
                      />
                    </svg>
                  </label>
                );
              })}
            </div>
          )}
        </PanelSection>

        <PanelSection icon={Quote} title="Обоснование" defaultOpen>
          {editing ? (
            <textarea
              value={draftRationale}
              onChange={(e) => setDraftRationale(e.target.value)}
              rows={3}
              maxLength={2000}
              disabled={saving}
              aria-label="Обоснование связи"
              placeholder="Зачем эта связь нужна — поможет другим читателям"
              className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-[13px] text-slate-900 outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
            />
          ) : currentRationale ? (
            <p className="whitespace-pre-wrap break-words text-[13px] leading-relaxed text-slate-800">
              {currentRationale}
            </p>
          ) : (
            <p className="text-[13px] italic text-slate-500">(не указано)</p>
          )}
        </PanelSection>

        {editing && (
          <div className="border-t border-slate-200 px-5 py-3 space-y-2">
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
                disabled={saving || allowedTypes.length === 0}
              >
                {saving ? 'Сохраняем' : 'Сохранить'}
              </Button>
            </div>
          </div>
        )}

        <PanelSection icon={Info} title="Метаданные" defaultOpen={false}>
          <dl className="grid grid-cols-[100px_1fr] gap-x-3 gap-y-2 text-[12px]">
            <dt className="text-slate-500">Создана</dt>
            <dd className="text-slate-700">{formatDate(edge.createdAt)}</dd>

            <dt className="text-slate-500">Автор</dt>
            <dd className="font-mono text-slate-700" title={edge.createdBy}>
              {shortId(edge.createdBy)}
            </dd>

            <dt className="text-slate-500">ID</dt>
            <dd className="font-mono text-slate-700" title={edge.id}>
              {shortId(edge.id)}
            </dd>
          </dl>
        </PanelSection>
      </div>
    </aside>
  );
}

export default EdgeDetailsPanel;
