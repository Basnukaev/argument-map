import { useState } from 'react';
import type { FormEvent } from 'react';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import NodeSelect from '@/components/graph/NodeSelect';
import { apiPost, ApiError } from '@/api/client';
import type { components } from '@/api/types';
import {
  EDGE_TYPE_META,
  getAllowedEdgeTypes,
  type EdgeType,
  type NodeType,
} from '@/utils/edgeRules';

type NodeDto = components['schemas']['NodeResponse'];

interface Props {
  open: boolean;
  nodes: NodeDto[];
  /** предзаполнение для drag-create через handles */
  initialFromId?: string;
  initialToId?: string;
  /** id точек подключения (top/right/bottom/left) - только для drag-create */
  initialSourceHandle?: string;
  initialTargetHandle?: string;
  onClose: () => void;
  onCreated: () => void;
}

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
    ? (allowedTypes.includes(edgeType) ? edgeType : allowedTypes[0]!)
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
      if (e instanceof ApiError) {
        const fieldErrors = e.problem.errors?.map((er) => `${er.field}: ${er.message}`).join('; ');
        setError(fieldErrors || e.problem.detail || e.problem.title);
      } else if (e instanceof Error) {
        setError(e.message);
      } else {
        setError('Не удалось создать связь');
      }
      setSubmitting(false);
    }
  }

  const inputClass =
    'block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200';

  return (
    <Modal open={open} onClose={handleClose} title="Новая связь" maxWidth="max-w-xl">
      <form onSubmit={handleSubmit} className="space-y-4">
        <fieldset disabled={submitting} className="space-y-3">
          <div>
            <label htmlFor="edge-from" className="mb-1 block text-sm font-medium text-gray-700">
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
            <label htmlFor="edge-to" className="mb-1 block text-sm font-medium text-gray-700">
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
          <legend className="text-sm font-medium text-gray-700">Тип связи</legend>
          {!pairSelected && (
            <p className="text-xs text-gray-500">Сначала выбери оба узла</p>
          )}
          {pairSelected && !pairAllowed && (
            <p className="rounded-md border border-amber-300 bg-amber-50 p-2 text-xs text-amber-900">
              Эту пару узлов нельзя соединить
              {fromNode?.nodeType && toNode?.nodeType
                ? ` (${fromNode.nodeType} → ${toNode.nodeType})`
                : ''}
              . См. ADR-010.
            </p>
          )}
          {pairAllowed && (
            <div className="space-y-1.5">
              {allowedTypes.map((value) => {
                const meta = EDGE_TYPE_META[value];
                const { Icon } = meta;
                const selected = effectiveEdgeType === value;
                return (
                  <label
                    key={value}
                    className={`flex cursor-pointer items-start gap-2 rounded-md border p-2 transition-colors ${
                      selected
                        ? 'border-blue-500 bg-blue-50'
                        : 'border-gray-200 hover:border-gray-400'
                    }`}
                  >
                    <input
                      type="radio"
                      name="edgeType"
                      value={value}
                      checked={selected}
                      onChange={() => setEdgeType(value)}
                      className="sr-only"
                    />
                    <Icon
                      size={20}
                      strokeWidth={2.5}
                      className={`mt-0.5 shrink-0 ${meta.colorClass}`}
                      aria-hidden="true"
                    />
                    <div className="flex-1">
                      <div className="font-medium text-gray-900">{meta.label}</div>
                      <div className="text-xs text-gray-500">{meta.hint}</div>
                    </div>
                  </label>
                );
              })}
            </div>
          )}
        </fieldset>

        <div>
          <label htmlFor="edge-rationale" className="mb-1 block text-sm font-medium text-gray-700">
            Обоснование (необязательно)
          </label>
          <textarea
            id="edge-rationale"
            value={rationale}
            onChange={(e) => setRationale(e.target.value)}
            rows={2}
            maxLength={2000}
            disabled={submitting}
            className={inputClass}
            placeholder="Почему эта связь?"
          />
        </div>

        {error && (
          <div className="rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800">
            {error}
          </div>
        )}

        <div className="flex justify-end gap-2 border-t border-gray-200 pt-3">
          <Button type="button" variant="secondary" onClick={handleClose} disabled={submitting}>
            Отмена
          </Button>
          <Button type="submit" disabled={submitting || !pairAllowed}>
            {submitting ? 'Создаём' : 'Создать'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default AddEdgeModal;
