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
  ArrowLeft,
  ArrowRight,
  type LucideIcon,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import IconButton from '@/shared/components/ui/IconButton';
import { apiPatchRaw, formatApiError } from '@/shared/api/client';
import { useLocaleStore, useFormatDate, useT } from '@/shared/i18n';
import type { components } from '@/shared/api/types';
import {
  EDGE_TYPE_META,
  getAllowedEdgeTypes,
  getContextualEdgeLabelKey,
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

function shortId(id?: string): string {
  if (!id) return '—';
  return id.slice(0, 8);
}

const PREVIEW_LEN = 80;

function nodePreview(node: NodeDto, emptyText: string): string {
  const content = node.content ?? '';
  return content.length > PREVIEW_LEN
    ? `${content.slice(0, PREVIEW_LEN)}…`
    : content || emptyText;
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
    <section className="border-t border-border">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex w-full items-center gap-2 px-5 py-3 text-start transition-colors hover:bg-ink-50"
      >
        <Icon size={14} className="text-ink-500" aria-hidden="true" />
        <span className="text-xs font-semibold uppercase tracking-wider text-ink-700">
          {title}
        </span>
        <ChevronDown
          size={14}
          className={`ms-auto text-ink-400 transition-transform ${open ? '' : '-rotate-90'}`}
          aria-hidden="true"
        />
      </button>
      {open && <div className="px-5 pb-4">{children}</div>}
    </section>
  );
}

interface NodeMiniProps {
  node: NodeDto;
  emptyText: string;
}

function NodeMini({ node, emptyText }: NodeMiniProps) {
  const nodeType: NodeType = node.nodeType ?? 'CLAIM';
  const status: NodeStatus = node.status ?? 'UNVERIFIED';
  const typeToken = NODE_TYPE_TOKENS[nodeType];
  const statusToken = STATUS_TOKENS[status];
  return (
    <div className="relative flex-1 overflow-hidden rounded-md border border-border bg-ink-50/40 px-2.5 py-2">
      <div
        className={`absolute start-0 top-0 bottom-0 w-[3px] rounded-s-md ${statusToken.bar}`}
        aria-hidden="true"
      />
      <div className={`text-xs font-semibold uppercase tracking-wider ${typeToken.chipText}`}>
        {nodeType}
      </div>
      <div dir="auto" className="line-clamp-2 text-xs font-medium leading-snug text-ink-800">
        {nodePreview(node, emptyText)}
      </div>
    </div>
  );
}

// Edge-specific solid header background. Per v2 design system 01-system.md
// anti-pattern: никаких decorative gradients. Используем edge-*-bg токены -
// мягкий tinted фон, который автоматически переключается в dark theme.
function headerBgFor(edgeType: EdgeType): string {
  switch (edgeType) {
    case 'SUPPORTS':
      return 'bg-edge-supports-bg';
    case 'REFUTES':
    case 'INVALIDATES':
      return 'bg-edge-refutes-bg';
    case 'QUALIFIES':
      return 'bg-edge-qualifies-bg';
    case 'RESPONDS_TO':
      return 'bg-edge-responds-bg';
  }
}

function iconBgFor(edgeType: EdgeType): { bg: string; text: string } {
  switch (edgeType) {
    case 'SUPPORTS':
      return { bg: 'bg-ok-100', text: 'text-ok-700' };
    case 'REFUTES':
      return { bg: 'bg-err-100', text: 'text-err-700' };
    case 'INVALIDATES':
      return { bg: 'bg-err-100', text: 'text-err-700' };
    case 'QUALIFIES':
      return { bg: 'bg-edge-qualifies-bg', text: 'text-edge-qualifies' };
    case 'RESPONDS_TO':
      return { bg: 'bg-ink-100', text: 'text-ink-700' };
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
  // Стрелка from→to отражает направление структурного UI, не текста.
  // В RTL-локали смыслово стрелка идёт справа (from) налево (to)
  const locale = useLocaleStore((s) => s.locale);
  const formatDate = useFormatDate();
  const t = useT();
  const FlowArrow = locale === 'ar' ? ArrowLeft : ArrowRight;
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
      setSaveError(formatApiError(e, t('common.error')));
    } finally {
      setSaving(false);
    }
  }

  const contextualLabel = t(getContextualEdgeLabelKey(fromType, currentEdgeType, toType));
  const currentMeta = EDGE_TYPE_META[currentEdgeType];
  const HeaderIcon = currentMeta.Icon;
  const iconBg = iconBgFor(currentEdgeType);

  return (
    <aside
      role="complementary"
      aria-label={t('graph.details_aria_edge')}
      className="absolute end-0 top-0 bottom-0 z-10 flex w-[400px] flex-col border-s border-border bg-elevated shadow-sh4"
    >
      <header
        className={`relative border-b border-border ${headerBgFor(currentEdgeType)} p-5`}
      >
        <div className="absolute end-3 top-3">
          <IconButton icon={X} label={t('common.close')} size="sm" onClick={onClose} />
        </div>
        <div className="flex items-center gap-2">
          <span className={`grid h-8 w-8 place-items-center rounded-md ${iconBg.bg} ${iconBg.text}`}>
            <HeaderIcon size={16} aria-hidden="true" />
          </span>
          <div className="flex min-w-0 flex-col">
            <h2 className="truncate text-xs font-semibold uppercase tracking-wider text-ink-500">
              EDGE · {t(currentMeta.labelKey)}
            </h2>
            <span className="font-mono text-xs text-ink-400">
              <bdi dir="ltr">{shortId(edge.id)}</bdi>
            </span>
          </div>
        </div>
        <p className="mt-2 text-xs text-ink-700">
          <span className="font-semibold text-ink-800">{contextualLabel}</span>
        </p>
      </header>

      <div className="flex-1 overflow-y-auto">
        <PanelSection icon={Network} title={t('edge.section.connection')} defaultOpen>
          <div className="flex items-center gap-2">
            <NodeMini node={fromNode} emptyText={t('node.empty_content')} />
            <FlowArrow size={20} className="shrink-0 text-ink-400" aria-hidden="true" />
            <NodeMini node={toNode} emptyText={t('node.empty_content')} />
          </div>
        </PanelSection>

        <PanelSection icon={Layers} title={t('edge.section.type')} defaultOpen>
          <div className="mb-2 flex items-center justify-between">
            {!editing ? (
              <p className="text-sm text-ink-900">
                <span className="font-semibold">{t(currentMeta.labelKey)}</span>
                <span className="text-ink-500"> — {t(currentMeta.hintKey)}</span>
              </p>
            ) : (
              <p className="text-xs text-ink-500">{t('edge.pick_new_type')}</p>
            )}
            {!editing && (
              <Button
                type="button"
                variant="link"
                size="xs"
                icon={Pencil}
                onClick={startEdit}
              >
                {t('common.edit')}
              </Button>
            )}
          </div>

          {editing && (
            <div className="space-y-1.5">
              {allowedTypes.length === 0 && (
                <p className="rounded-md border border-warn-500/40 bg-warn-100 p-2 text-xs text-warn-700">
                  {t('edge.error.disallowed_pair')}
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
                        ? 'border-accent-500 bg-accent-50/60 ring-1 ring-accent-500/30'
                        : 'border-border-strong hover:bg-ink-50'
                    }`}
                  >
                    <input
                      type="radio"
                      name="edgeType"
                      value={value}
                      checked={selected}
                      onChange={() => setDraftType(value)}
                      disabled={saving}
                      className="mt-1 accent-accent-500"
                    />
                    <div className="flex-1">
                      <div className="flex items-center gap-1.5">
                        <Icon
                          size={13}
                          strokeWidth={2.5}
                          style={{ color: token.stroke }}
                          aria-hidden="true"
                        />
                        <span className="text-xs font-semibold text-ink-900">
                          {t(meta.labelKey)}
                        </span>
                      </div>
                      <p className="mt-0.5 text-xs leading-relaxed text-ink-500">
                        {t(meta.hintKey)}
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

        <PanelSection icon={Quote} title={t('edge.section.rationale')} defaultOpen>
          {editing ? (
            <textarea
              value={draftRationale}
              onChange={(e) => setDraftRationale(e.target.value)}
              rows={3}
              maxLength={2000}
              disabled={saving}
              aria-label={t('edge.rationale_aria')}
              placeholder={t('edge.rationale_placeholder')}
              className="block w-full rounded-md border border-border-strong bg-elevated px-3 py-2 text-sm text-ink-900 outline-none transition-colors focus:border-accent-500 focus:ring-2 focus:ring-accent-500/20"
            />
          ) : currentRationale ? (
            <p dir="auto" className="whitespace-pre-wrap break-words text-sm leading-relaxed text-ink-800">
              {currentRationale}
            </p>
          ) : (
            <p className="text-sm italic text-ink-500">{t('node.empty_rationale')}</p>
          )}
        </PanelSection>

        {editing && (
          <div className="border-t border-border px-5 py-3 space-y-2">
            {saveError && (
              <div className="rounded-md border border-err-500/40 bg-err-100 p-2 text-xs text-err-700">
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
                {t('common.cancel')}
              </Button>
              <Button
                type="button"
                size="sm"
                onClick={save}
                disabled={saving || allowedTypes.length === 0}
              >
                {saving ? t('common.saving') : t('common.save')}
              </Button>
            </div>
          </div>
        )}

        <PanelSection icon={Info} title={t('edge.section.metadata')} defaultOpen={false}>
          <dl className="grid grid-cols-[100px_1fr] gap-x-3 gap-y-2 text-xs">
            <dt className="text-ink-500">{t('edge.created_at')}</dt>
            <dd className="text-ink-700">
              <bdi dir="ltr">{formatDate(edge.createdAt)}</bdi>
            </dd>

            <dt className="text-ink-500">{t('edge.author')}</dt>
            <dd className="font-mono text-ink-700" title={edge.createdBy}>
              <bdi dir="ltr">{shortId(edge.createdBy)}</bdi>
            </dd>

            <dt className="text-ink-500">{t('edge.id')}</dt>
            <dd className="font-mono text-ink-700" title={edge.id}>
              <bdi dir="ltr">{shortId(edge.id)}</bdi>
            </dd>
          </dl>
        </PanelSection>
      </div>
    </aside>
  );
}

export default EdgeDetailsPanel;
