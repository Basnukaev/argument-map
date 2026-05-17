import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link as LinkIcon, AlertCircle } from 'lucide-react';
import FormModal from '@/shared/components/ui/FormModal';
import NodeSelect from '@/apps/argument-map/components/graph/NodeSelect';
import { apiPost, formatApiError } from '@/shared/api/client';
import type { components } from '@/shared/api/types';
import {
  EDGE_TYPE_META,
  getAllowedEdgeTypes,
  type EdgeType,
  type NodeType,
} from '@/apps/argument-map/utils/edgeRules';
import { EDGE_TYPE_TOKENS } from '@/shared/utils/designTokens';
import { useT } from '@/shared/i18n';

type NodeDto = components['schemas']['NodeResponse'];

interface Props {
  open: boolean;
  nodes: NodeDto[];
  initialFromId?: string;
  initialToId?: string;
  initialSourceHandle?: string;
  initialTargetHandle?: string;
  onClose: () => void;
  onCreated: () => void;
}

const ALL_EDGE_TYPES: readonly EdgeType[] = [
  'SUPPORTS',
  'REFUTES',
  'INVALIDATES',
  'QUALIFIES',
  'RESPONDS_TO',
];

function AddEdgeModal({
  open,
  nodes,
  initialFromId = '',
  initialToId = '',
  initialSourceHandle,
  initialTargetHandle,
  onClose,
  onCreated,
}: Props) {
  const t = useT();
  const [fromNodeId, setFromNodeId] = useState(initialFromId);
  const [toNodeId, setToNodeId] = useState(initialToId);
  const [edgeType, setEdgeType] = useState<EdgeType>('SUPPORTS');
  const [rationale, setRationale] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fromNode = nodes.find((n) => n.id === fromNodeId);
  const toNode = nodes.find((n) => n.id === toNodeId);
  const allowedTypes: readonly EdgeType[] =
    fromNode?.nodeType && toNode?.nodeType
      ? getAllowedEdgeTypes(fromNode.nodeType as NodeType, toNode.nodeType as NodeType)
      : [];
  const pairSelected = Boolean(fromNodeId && toNodeId && fromNodeId !== toNodeId);
  const pairAllowed = pairSelected && allowedTypes.length > 0;

  // если выбранный пользователем тип не подходит под текущую пару -
  // подставляем первый разрешённый. Это derived state без useEffect/setState.
  const effectiveEdgeType: EdgeType = pairAllowed
    ? allowedTypes.includes(edgeType)
      ? edgeType
      : allowedTypes[0]!
    : edgeType;

  function reset() {
    setFromNodeId(initialFromId);
    setToNodeId(initialToId);
    setEdgeType('SUPPORTS');
    setRationale('');
    setError(null);
    setSubmitting(false);
  }

  function handleClose() {
    if (submitting) return;
    reset();
    onClose();
  }

  function validate(): string | null {
    if (!fromNodeId || !toNodeId) return t('edge.error.required');
    if (fromNodeId === toNodeId) return t('edge.error.self_loop');
    if (!allowedTypes.includes(effectiveEdgeType)) {
      return t('edge.error.disallowed_pair');
    }
    return null;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await apiPost('/api/v1/edges', {
        fromNodeId,
        toNodeId,
        edgeType: effectiveEdgeType,
        rationale: rationale.trim() || undefined,
        sourceHandle: initialSourceHandle,
        targetHandle: initialTargetHandle,
      });
      reset();
      onCreated();
      onClose();
    } catch (e: unknown) {
      setError(formatApiError(e, t('edge.error.create_failed')));
      setSubmitting(false);
    }
  }

  return (
    <FormModal
      open={open}
      onClose={handleClose}
      title={t('graph.title_new_edge')}
      maxWidth="max-w-xl"
      onSubmit={handleSubmit}
      submitting={submitting}
      submitDisabled={!pairAllowed}
      submitLabel={t('common.create')}
      submittingLabel={t('common.saving')}
      submitIcon={LinkIcon}
      error={error}
      hotkeyHint={
        pairSelected && !pairAllowed ? (
          <span className="inline-flex items-center gap-1 text-err-700">
            <AlertCircle size={12} aria-hidden="true" /> запрещённая пара
          </span>
        ) : undefined
      }
    >
      <fieldset disabled={submitting} className="space-y-3">
          <div>
            <label
              htmlFor="edge-from"
              className="mb-1.5 block text-xs font-medium text-ink-700"
            >
              {t('edge.field.from')}
            </label>
            <NodeSelect
              id="edge-from"
              value={fromNodeId}
              onChange={setFromNodeId}
              options={nodes}
              disabled={submitting}
            />
          </div>

          <div>
            <label
              htmlFor="edge-to"
              className="mb-1.5 block text-xs font-medium text-ink-700"
            >
              {t('edge.field.to')}
            </label>
            <NodeSelect
              id="edge-to"
              value={toNodeId}
              onChange={setToNodeId}
              options={nodes}
              excludeId={fromNodeId}
              disabled={submitting}
            />
          </div>
        </fieldset>

        <fieldset disabled={submitting} className="space-y-2">
          <legend className="text-xs font-medium text-ink-700">{t('edge.section.type')}</legend>
          {!pairSelected && (
            <p className="text-xs text-ink-500">{t('edge.select_both_nodes')}</p>
          )}
          {pairSelected && !pairAllowed && (
            <p className="rounded-md border border-warn-500/40 bg-warn-100 p-2 text-xs text-warn-700">
              {t('edge.error.disallowed_pair')}
              {fromNode?.nodeType && toNode?.nodeType
                ? ` (${fromNode.nodeType} → ${toNode.nodeType})`
                : ''}
            </p>
          )}
          {pairAllowed && (
            <div
              className="grid gap-2"
              style={{ gridTemplateColumns: `repeat(${allowedTypes.length}, minmax(0, 1fr))` }}
            >
              {ALL_EDGE_TYPES.filter((t) => allowedTypes.includes(t)).map((value) => {
                const meta = EDGE_TYPE_META[value];
                const token = EDGE_TYPE_TOKENS[value];
                const Icon = meta.Icon;
                const selected = effectiveEdgeType === value;
                return (
                  <label
                    key={value}
                    className={`flex cursor-pointer flex-col gap-1.5 rounded-md border p-2 transition-colors ${
                      selected
                        ? 'border-accent-500 bg-accent-50/60 ring-1 ring-accent-500/30'
                        : 'border-border-strong hover:bg-ink-50'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <Icon
                        size={14}
                        strokeWidth={2.5}
                        style={{ color: token.stroke }}
                        aria-hidden="true"
                      />
                      <input
                        type="radio"
                        name="edgeType"
                        value={value}
                        checked={selected}
                        onChange={() => setEdgeType(value)}
                        className="accent-accent-500"
                      />
                    </div>
                    <span className="text-xs font-semibold leading-tight text-ink-900">
                      {t(meta.labelKey)}
                    </span>
                    <svg width="100%" height="8" aria-hidden="true">
                      <line
                        x1="2"
                        y1="4"
                        x2="100%"
                        y2="4"
                        stroke={token.stroke}
                        strokeWidth={token.strokeWidth}
                        strokeDasharray={token.strokeDasharray}
                        strokeOpacity={token.opacity ?? 1}
                      />
                    </svg>
                  </label>
                );
              })}
            </div>
          )}
        </fieldset>

        <div>
          <label
            htmlFor="edge-rationale"
            className="mb-1.5 block text-xs font-medium text-ink-700"
          >
            {t('edge.field.rationale_optional')}
          </label>
          <textarea
            id="edge-rationale"
            value={rationale}
            onChange={(e) => setRationale(e.target.value)}
            rows={3}
            maxLength={2000}
            disabled={submitting}
            className="block w-full rounded-md border border-border-strong bg-elevated px-3 py-2 text-sm text-ink-900 placeholder:text-ink-400 outline-none transition-colors focus:border-accent-500 focus:ring-2 focus:ring-accent-500/20"
            placeholder={t('edge.rationale_placeholder')}
          />
        </div>

    </FormModal>
  );
}

export default AddEdgeModal;
