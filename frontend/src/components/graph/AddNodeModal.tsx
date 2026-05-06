import { useState } from 'react';
import type { FormEvent } from 'react';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import { apiPost, apiPatchRaw, ApiError } from '@/api/client';
import { toast } from '@/stores/toastStore';
import { NODE_TYPE_META, type EdgeType, type NodeType } from '@/utils/edgeRules';
import type { components } from '@/api/types';

type NodeResponse = components['schemas']['NodeResponse'];

/**
 * Параметры авто-связки. Если передано - после успешного POST /nodes
 * сразу делаем POST /edges. direction='incoming' значит новый узел
 * становится from в новом ребре (anchor=to), 'outgoing' - наоборот
 */
export interface AutoEdgeSpec {
  anchorNodeId: string;
  edgeType: EdgeType;
  direction: 'incoming' | 'outgoing';
}

interface Props {
  open: boolean;
  topicId: string;
  onClose: () => void;
  /** вызывается после успешного создания - для refetch графа */
  onCreated: () => void;
  /** опциональные координаты на канвасе для нового узла. Если переданы,
   * после успешного POST дополнительно PATCH'им posX/posY. Используется
   * при создании через "Создать узел здесь" из контекстного меню pane */
  initialPosX?: number;
  initialPosY?: number;
  /** предустановленный тип узла - блокирует выбор типа в форме. Используется
   * при "Добавить связанный X" из контекстного меню узла */
  initialNodeType?: NodeType;
  /** автоматически создать ребро после создания узла. Тип ребра валидирован
   * через ADR-010 на стороне вызывающего (контекстное меню) */
  autoEdge?: AutoEdgeSpec;
}

const TYPE_ORDER: readonly NodeType[] = ['QUESTION', 'CLAIM', 'ARGUMENT', 'EVIDENCE'];

function AddNodeModal({
  open,
  topicId,
  onClose,
  onCreated,
  initialPosX,
  initialPosY,
  initialNodeType,
  autoEdge,
}: Props) {
  const [nodeType, setNodeType] = useState<NodeType>(initialNodeType ?? 'CLAIM');
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const lockNodeType = initialNodeType !== undefined;

  function reset() {
    setNodeType(initialNodeType ?? 'CLAIM');
    setContent('');
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
      const created = await apiPost('/api/v1/nodes', {
        topicId,
        nodeType,
        content: content.trim(),
      }) as NodeResponse;

      // если задан autoEdge - создаём ребро. Не блокирующая ошибка:
      // узел уже есть, юзер при необходимости свяжет вручную
      if (autoEdge && created.id) {
        const fromId = autoEdge.direction === 'incoming' ? created.id : autoEdge.anchorNodeId;
        const toId = autoEdge.direction === 'incoming' ? autoEdge.anchorNodeId : created.id;
        try {
          await apiPost('/api/v1/edges', {
            fromNodeId: fromId,
            toNodeId: toId,
            edgeType: autoEdge.edgeType,
          });
        } catch {
          toast.warning('Узел создан, но связь не удалось добавить - привяжи вручную');
        }
      }

      // если переданы координаты - сразу PATCH'им (POST не принимает
      // posX/posY чтобы не делать full-stack изменения для одной фичи).
      // Игнорируем ошибку второго запроса - узел уже создан, в худшем
      // случае встанет в дефолтное место и пользователь dragнет его сам
      if (initialPosX !== undefined && initialPosY !== undefined && created.id) {
        try {
          await apiPatchRaw(`/api/v1/nodes/${created.id}`, {
            posX: initialPosX,
            posY: initialPosY,
          });
        } catch {
          // позиционирование - не блокирующая ошибка
        }
      }

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
        <fieldset disabled={submitting || lockNodeType} className="space-y-2">
          <legend className="text-sm font-medium text-gray-700">
            Тип{lockNodeType ? ' (зафиксирован)' : ''}
          </legend>
          <div className="grid grid-cols-2 gap-2">
            {TYPE_ORDER.map((value) => {
              const meta = NODE_TYPE_META[value];
              const { Icon } = meta;
              const selected = nodeType === value;
              if (lockNodeType && !selected) return null;
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
                    name="nodeType"
                    value={value}
                    checked={selected}
                    onChange={() => setNodeType(value)}
                    className="sr-only"
                  />
                  <Icon
                    size={18}
                    className={`mt-0.5 shrink-0 ${selected ? 'text-blue-600' : 'text-gray-500'}`}
                    aria-hidden="true"
                  />
                  <span className="flex flex-col">
                    <span className="font-medium text-gray-900">{meta.label}</span>
                    <span className="mt-0.5 text-xs text-gray-500">{meta.hint}</span>
                  </span>
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
