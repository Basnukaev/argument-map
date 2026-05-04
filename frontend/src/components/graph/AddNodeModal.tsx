import { useState } from 'react';
import type { FormEvent } from 'react';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import { apiPost, ApiError } from '@/api/client';
import type { components } from '@/api/types';

type CreateNodeRequest = components['schemas']['CreateNodeRequest'];
type NodeType = CreateNodeRequest['nodeType'];

interface Props {
  open: boolean;
  topicId: string;
  onClose: () => void;
  /** вызывается после успешного создания - для refetch графа */
  onCreated: () => void;
}

const TYPE_OPTIONS: Array<{ value: NodeType; label: string; hint: string }> = [
  { value: 'QUESTION', label: 'Вопрос', hint: 'Корневой или уточняющий вопрос' },
  { value: 'CLAIM', label: 'Тезис', hint: 'Утверждение которое доказывают' },
  { value: 'ARGUMENT', label: 'Довод', hint: 'Аргумент за/против тезиса' },
  { value: 'EVIDENCE', label: 'Свидетельство', hint: 'Хадис, цитата, факт' },
];

function AddNodeModal({ open, topicId, onClose, onCreated }: Props) {
  const [nodeType, setNodeType] = useState<NodeType>('CLAIM');
  const [content, setContent] = useState('');
  const [weight, setWeight] = useState(5);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function reset() {
    setNodeType('CLAIM');
    setContent('');
    setWeight(5);
    setError(null);
    setSubmitting(false);
  }

  function handleClose() {
    if (submitting) return;
    reset();
    onClose();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await apiPost('/api/v1/nodes', {
        topicId,
        nodeType,
        content: content.trim(),
        weight,
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
        setError('Не удалось создать узел');
      }
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Новый узел">
      <form onSubmit={handleSubmit} className="space-y-4">
        <fieldset disabled={submitting} className="space-y-2">
          <legend className="text-sm font-medium text-gray-700">Тип</legend>
          <div className="grid grid-cols-2 gap-2">
            {TYPE_OPTIONS.map((option) => {
              const selected = nodeType === option.value;
              return (
                <label
                  key={option.value}
                  className={`flex cursor-pointer flex-col rounded-md border p-2 transition-colors ${
                    selected
                      ? 'border-blue-500 bg-blue-50'
                      : 'border-gray-200 hover:border-gray-400'
                  }`}
                >
                  <input
                    type="radio"
                    name="nodeType"
                    value={option.value}
                    checked={selected}
                    onChange={() => setNodeType(option.value)}
                    className="sr-only"
                  />
                  <span className="font-medium text-gray-900">{option.label}</span>
                  <span className="mt-0.5 text-xs text-gray-500">{option.hint}</span>
                </label>
              );
            })}
          </div>
        </fieldset>

        <div>
          <label htmlFor="node-content" className="mb-1 block text-sm font-medium text-gray-700">
            Содержание
          </label>
          <textarea
            id="node-content"
            value={content}
            onChange={(e) => setContent(e.target.value)}
            required
            rows={4}
            maxLength={10000}
            disabled={submitting}
            className="block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
          />
        </div>

        <div>
          <label htmlFor="node-weight" className="mb-1 block text-sm font-medium text-gray-700">
            Вес: <span className="font-mono">{weight}/10</span>
          </label>
          <input
            id="node-weight"
            type="range"
            min={1}
            max={10}
            step={1}
            value={weight}
            onChange={(e) => setWeight(Number(e.target.value))}
            disabled={submitting}
            className="w-full"
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
          <Button type="submit" disabled={submitting || !content.trim()}>
            {submitting ? 'Создаём' : 'Создать'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default AddNodeModal;
