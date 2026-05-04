import { useState } from 'react';
import type { FormEvent } from 'react';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import { apiPost, ApiError } from '@/api/client';
import type { components } from '@/api/types';

type CreateEdgeRequest = components['schemas']['CreateEdgeRequest'];
type EdgeType = CreateEdgeRequest['edgeType'];
type NodeDto = components['schemas']['NodeResponse'];

interface Props {
  open: boolean;
  nodes: NodeDto[];
  onClose: () => void;
  onCreated: () => void;
}

const TYPE_OPTIONS: Array<{ value: EdgeType; label: string; hint: string }> = [
  { value: 'SUPPORTS', label: 'Поддерживает', hint: 'Аргумент за тезис' },
  { value: 'REFUTES', label: 'Опровергает', hint: 'Аргумент против' },
  {
    value: 'INVALIDATES',
    label: 'Аннулирует',
    hint: 'Жёсткое мета-опровержение (kill)',
  },
  { value: 'QUALIFIES', label: 'Уточняет', hint: 'Сужает применимость' },
  { value: 'RESPONDS_TO', label: 'Отвечает', hint: 'Реплика-ответ' },
];

const PREVIEW_LEN = 60;

function previewContent(node: NodeDto): string {
  const content = node.content ?? '';
  const trimmed = content.length > PREVIEW_LEN ? `${content.slice(0, PREVIEW_LEN)}…` : content;
  return `[${node.nodeType ?? '?'}] ${trimmed || '(без содержимого)'}`;
}

function AddEdgeModal({ open, nodes, onClose, onCreated }: Props) {
  const [fromNodeId, setFromNodeId] = useState('');
  const [toNodeId, setToNodeId] = useState('');
  const [edgeType, setEdgeType] = useState<EdgeType>('SUPPORTS');
  const [rationale, setRationale] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function reset() {
    setFromNodeId('');
    setToNodeId('');
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
        edgeType,
        rationale: rationale.trim() || undefined,
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

  const selectClass =
    'block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200';

  return (
    <Modal open={open} onClose={handleClose} title="Новая связь" maxWidth="max-w-xl">
      <form onSubmit={handleSubmit} className="space-y-4">
        <fieldset disabled={submitting} className="space-y-3">
          <div>
            <label htmlFor="edge-from" className="mb-1 block text-sm font-medium text-gray-700">
              Откуда
            </label>
            <select
              id="edge-from"
              value={fromNodeId}
              onChange={(e) => setFromNodeId(e.target.value)}
              className={selectClass}
              required
            >
              <option value="">- выбрать узел -</option>
              {nodes
                .filter((n): n is NodeDto & { id: string } => Boolean(n.id))
                .map((n) => (
                  <option key={n.id} value={n.id}>
                    {previewContent(n)}
                  </option>
                ))}
            </select>
          </div>

          <div>
            <label htmlFor="edge-to" className="mb-1 block text-sm font-medium text-gray-700">
              Куда
            </label>
            <select
              id="edge-to"
              value={toNodeId}
              onChange={(e) => setToNodeId(e.target.value)}
              className={selectClass}
              required
            >
              <option value="">- выбрать узел -</option>
              {nodes
                .filter((n): n is NodeDto & { id: string } => Boolean(n.id) && n.id !== fromNodeId)
                .map((n) => (
                  <option key={n.id} value={n.id}>
                    {previewContent(n)}
                  </option>
                ))}
            </select>
          </div>
        </fieldset>

        <fieldset disabled={submitting} className="space-y-2">
          <legend className="text-sm font-medium text-gray-700">Тип связи</legend>
          <div className="space-y-1.5">
            {TYPE_OPTIONS.map((option) => {
              const selected = edgeType === option.value;
              return (
                <label
                  key={option.value}
                  className={`flex cursor-pointer items-start gap-2 rounded-md border p-2 transition-colors ${
                    selected
                      ? 'border-blue-500 bg-blue-50'
                      : 'border-gray-200 hover:border-gray-400'
                  }`}
                >
                  <input
                    type="radio"
                    name="edgeType"
                    value={option.value}
                    checked={selected}
                    onChange={() => setEdgeType(option.value)}
                    className="mt-0.5"
                  />
                  <div className="flex-1">
                    <div className="font-medium text-gray-900">{option.label}</div>
                    <div className="text-xs text-gray-500">{option.hint}</div>
                  </div>
                </label>
              );
            })}
          </div>
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
            className={selectClass}
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
          <Button type="submit" disabled={submitting}>
            {submitting ? 'Создаём' : 'Создать'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default AddEdgeModal;
