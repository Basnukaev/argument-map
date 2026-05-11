import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link as LinkIcon, AlertCircle } from 'lucide-react';
import Modal from '@/shared/components/ui/Modal';
import Button from '@/shared/components/ui/Button';
import Kbd from '@/shared/components/ui/Kbd';
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
    if (!fromNodeId) return 'Выбери исходный узел';
    if (!toNodeId) return 'Выбери целевой узел';
    if (fromNodeId === toNodeId) return 'Исходный и целевой узлы должны различаться';
    if (!allowedTypes.includes(effectiveEdgeType)) {
      return 'Эту пару узлов нельзя соединить выбранным типом';
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
      setError(formatApiError(e, 'Не удалось создать связь'));
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Новая связь" maxWidth="max-w-xl">
      <form onSubmit={handleSubmit} className="space-y-5">
        <fieldset disabled={submitting} className="space-y-3">
          <div>
            <label
              htmlFor="edge-from"
              className="mb-1.5 block text-[12px] font-medium text-slate-700"
            >
              Откуда
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
              className="mb-1.5 block text-[12px] font-medium text-slate-700"
            >
              Куда
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
          <legend className="text-[12px] font-medium text-slate-700">Тип связи</legend>
          {!pairSelected && (
            <p className="text-[11px] text-slate-500">Сначала выбери оба узла</p>
          )}
          {pairSelected && !pairAllowed && (
            <p className="rounded-md border border-amber-300 bg-amber-50 p-2 text-[12px] text-amber-900">
              Эту пару узлов нельзя соединить
              {fromNode?.nodeType && toNode?.nodeType
                ? ` (${fromNode.nodeType} → ${toNode.nodeType})`
                : ''}
              . См. ADR-010.
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
                        ? 'border-indigo-500 bg-indigo-50/60 ring-1 ring-indigo-400'
                        : 'border-slate-300 hover:bg-slate-50'
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
                        className="accent-indigo-600"
                      />
                    </div>
                    <span className="text-[11px] font-semibold leading-tight text-slate-900">
                      {meta.label}
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
            className="mb-1.5 block text-[12px] font-medium text-slate-700"
          >
            Обоснование (необязательно)
          </label>
          <textarea
            id="edge-rationale"
            value={rationale}
            onChange={(e) => setRationale(e.target.value)}
            rows={3}
            maxLength={2000}
            disabled={submitting}
            className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-[13px] text-slate-900 placeholder:text-slate-400 outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
            placeholder="Зачем эта связь нужна — поможет другим читателям"
          />
        </div>

        {error && (
          <div className="rounded-md border border-red-300 bg-red-50 p-3 text-[12px] text-red-800">
            {error}
          </div>
        )}

        <div className="flex items-center justify-between border-t border-slate-200 pt-3">
          <span className="hidden items-center gap-1 text-[11px] text-slate-500 sm:inline-flex">
            {pairSelected && !pairAllowed ? (
              <span className="inline-flex items-center gap-1 text-red-600">
                <AlertCircle size={12} aria-hidden="true" /> запрещённая пара
              </span>
            ) : (
              <>
                <Kbd>⌘</Kbd>
                <Kbd>↵</Kbd> создать
              </>
            )}
          </span>
          <div className="ml-auto flex items-center gap-2">
            <Button
              type="button"
              variant="ghost"
              onClick={handleClose}
              disabled={submitting}
            >
              Отмена
            </Button>
            <Button type="submit" icon={LinkIcon} disabled={submitting || !pairAllowed}>
              {submitting ? 'Создаём' : 'Создать'}
            </Button>
          </div>
        </div>
      </form>
    </Modal>
  );
}

export default AddEdgeModal;
