import { useState } from 'react';
import type { FormEvent } from 'react';
import { Plus } from 'lucide-react';
import Modal from '@/shared/components/ui/Modal';
import Button from '@/shared/components/ui/Button';
import Kbd from '@/shared/components/ui/Kbd';
import { apiPost, apiPatchRaw, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { NODE_TYPE_TOKENS, type NodeType } from '@/shared/utils/designTokens';
import type { EdgeType } from '@/apps/argument-map/utils/edgeRules';
import type { components } from '@/shared/api/types';

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
  /** опциональные координаты на канвасе для нового узла */
  initialPosX?: number;
  initialPosY?: number;
  /** предустановленный тип узла - блокирует выбор типа в форме */
  initialNodeType?: NodeType;
  /** автоматически создать ребро после создания узла */
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
      const created = (await apiPost('/api/v1/nodes', {
        topicId,
        nodeType,
        content: content.trim(),
      })) as NodeResponse;

      if (autoEdge && created.id) {
        const fromId =
          autoEdge.direction === 'incoming' ? created.id : autoEdge.anchorNodeId;
        const toId =
          autoEdge.direction === 'incoming' ? autoEdge.anchorNodeId : created.id;
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
      setError(formatApiError(e, 'Не удалось создать узел'));
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Новый узел">
      <form onSubmit={handleSubmit} className="space-y-5">
        <fieldset disabled={submitting || lockNodeType} className="space-y-2">
          <legend className="text-[12px] font-medium text-slate-700">
            Тип{lockNodeType ? ' (зафиксирован)' : ''}
          </legend>
          <div className={`mt-2 grid gap-2 ${lockNodeType ? 'grid-cols-1' : 'grid-cols-4'}`}>
            {TYPE_ORDER.map((value) => {
              const token = NODE_TYPE_TOKENS[value];
              const Icon = token.Icon;
              const selected = nodeType === value;
              if (lockNodeType && !selected) return null;
              return (
                <label
                  key={value}
                  className={`flex cursor-pointer flex-col gap-1.5 rounded-md border p-3 transition-colors ${
                    selected
                      ? 'border-indigo-500 bg-indigo-50/60 ring-1 ring-indigo-400'
                      : 'border-slate-300 hover:bg-slate-50'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span
                      className={`grid h-7 w-7 place-items-center rounded ${token.chipBg} ${token.chipText}`}
                    >
                      <Icon size={15} aria-hidden="true" />
                    </span>
                    <input
                      type="radio"
                      name="nodeType"
                      value={value}
                      checked={selected}
                      onChange={() => setNodeType(value)}
                      className="accent-indigo-600"
                    />
                  </div>
                  <span className="text-[12px] font-semibold text-slate-900">
                    {token.label}
                  </span>
                  <span className="line-clamp-2 text-[10px] leading-relaxed text-slate-500">
                    {token.hint}
                  </span>
                </label>
              );
            })}
          </div>
        </fieldset>

        <div>
          <label htmlFor="node-content" className="mb-1.5 block text-[12px] font-medium text-slate-700">
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
            className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-[13px] text-slate-900 placeholder:text-slate-400 outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
          />
          <span className="mt-1 block text-[11px] text-slate-500">
            2-4 предложения. Можно отредактировать позже.
          </span>
        </div>

        {error && (
          <div className="rounded-md border border-red-300 bg-red-50 p-3 text-[12px] text-red-800">
            {error}
          </div>
        )}

        <div className="flex items-center justify-between border-t border-slate-200 pt-3">
          <span className="hidden items-center gap-1 text-[11px] text-slate-500 sm:inline-flex">
            <Kbd>⌘</Kbd>
            <Kbd>↵</Kbd> создать
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
            <Button
              type="submit"
              icon={Plus}
              disabled={submitting || !content.trim()}
            >
              {submitting ? 'Создаём' : 'Создать'}
            </Button>
          </div>
        </div>
      </form>
    </Modal>
  );
}

export default AddNodeModal;
