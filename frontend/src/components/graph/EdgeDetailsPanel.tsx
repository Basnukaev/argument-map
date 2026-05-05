import { useState } from 'react';
import { X, Pencil } from 'lucide-react';
import Button from '@/components/ui/Button';
import { apiPatchRaw, ApiError } from '@/api/client';
import type { components } from '@/api/types';
import {
  EDGE_TYPE_ICON,
  getAllowedEdgeTypes,
  getContextualEdgeLabel,
  NODE_TYPE_EMOJI,
  NODE_TYPE_LABEL,
  type EdgeType,
  type NodeType,
} from '@/utils/edgeRules';

type EdgeDto = components['schemas']['EdgeResponse'];
type NodeDto = components['schemas']['NodeResponse'];

interface Props {
  edge: EdgeDto;
  fromNode: NodeDto;
  toNode: NodeDto;
  onClose: () => void;
  /** вызывается после успешного PATCH - чтобы родитель refetch'нул граф */
  onUpdated: () => void;
  /** если true - открыться сразу в режиме редактирования
   * (используется когда пользователь нажал "Редактировать" в контекстном меню) */
  initialEditing?: boolean;
}

const EDGE_TYPE_LABEL: Record<EdgeType, { label: string; hint: string }> = {
  SUPPORTS: { label: 'Поддерживает', hint: 'Аргумент за тезис' },
  REFUTES: { label: 'Опровергает', hint: 'Аргумент против' },
  INVALIDATES: { label: 'Аннулирует', hint: 'Жёсткое мета-опровержение (kill)' },
  QUALIFIES: { label: 'Уточняет', hint: 'Сужает применимость' },
  RESPONDS_TO: { label: 'Отвечает', hint: 'Реплика-ответ' },
};

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
  return content.length > PREVIEW_LEN ? `${content.slice(0, PREVIEW_LEN)}…` : content || '(без содержимого)';
}

function EdgeDetailsPanel({ edge, fromNode, toNode, onClose, onUpdated, initialEditing = false }: Props) {
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

    // отправляем только изменённые поля - так бэк не получает лишнее.
    // rationale пустой не очищается через PATCH (null = не передано),
    // поэтому если пользователь стёр текст - проигнорируем (UI ограничение MVP)
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

  const contextualLabel = getContextualEdgeLabel(fromType, currentEdgeType, toType);

  return (
    <aside
      role="complementary"
      aria-label="Детали связи"
      className="absolute right-0 top-0 bottom-0 z-10 flex w-96 flex-col border-l-2 border-gray-200 bg-white shadow-xl"
    >
      <header className="flex items-center justify-between gap-2 border-b border-gray-200 px-4 py-3">
        <div className="flex min-w-0 items-center gap-2">
          <span className="text-xl" aria-hidden="true">
            {EDGE_TYPE_ICON[currentEdgeType]}
          </span>
          <h2 className="truncate text-base font-semibold text-gray-900">
            {EDGE_TYPE_LABEL[currentEdgeType].label}
          </h2>
          <span className="text-xs text-gray-500">{contextualLabel}</span>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="Закрыть панель"
          className="rounded p-1 text-gray-500 hover:bg-gray-100 hover:text-gray-700"
        >
          <X size={18} />
        </button>
      </header>

      <div className="flex-1 space-y-5 overflow-y-auto px-4 py-3">
        <section>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-500">
            Откуда
          </h3>
          <div className="rounded-md border border-gray-200 bg-gray-50 p-2 text-sm">
            <div className="flex items-center gap-1 text-xs text-gray-500">
              <span aria-hidden="true">{NODE_TYPE_EMOJI[fromType]}</span>
              <span>{NODE_TYPE_LABEL[fromType]}</span>
            </div>
            <p className="mt-1 whitespace-pre-wrap break-words text-gray-900">
              {nodePreview(fromNode)}
            </p>
          </div>
        </section>

        <section>
          <div className="mb-2 flex items-center justify-between">
            <h3 className="text-xs font-semibold uppercase tracking-wide text-gray-500">
              Тип связи
            </h3>
            {!editing && (
              <button
                type="button"
                onClick={startEdit}
                className="inline-flex items-center gap-1 text-xs text-blue-600 hover:text-blue-800"
              >
                <Pencil size={12} />
                Редактировать
              </button>
            )}
          </div>

          {editing ? (
            <div className="space-y-1.5">
              {allowedTypes.length === 0 && (
                <p className="rounded-md border border-amber-300 bg-amber-50 p-2 text-xs text-amber-900">
                  Текущая пара узлов не допускает ни один тип связи (см. ADR-010)
                </p>
              )}
              {allowedTypes.map((value) => {
                const meta = EDGE_TYPE_LABEL[value];
                const selected = draftType === value;
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
                      onChange={() => setDraftType(value)}
                      disabled={saving}
                      className="mt-0.5"
                    />
                    <div className="flex-1">
                      <div className="font-medium text-gray-900">{meta.label}</div>
                      <div className="text-xs text-gray-500">{meta.hint}</div>
                    </div>
                  </label>
                );
              })}
            </div>
          ) : (
            <p className="text-sm text-gray-900">
              <span className="font-medium">{EDGE_TYPE_LABEL[currentEdgeType].label}</span>
              <span className="text-gray-500"> - {EDGE_TYPE_LABEL[currentEdgeType].hint}</span>
            </p>
          )}
        </section>

        <section>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-500">
            Куда
          </h3>
          <div className="rounded-md border border-gray-200 bg-gray-50 p-2 text-sm">
            <div className="flex items-center gap-1 text-xs text-gray-500">
              <span aria-hidden="true">{NODE_TYPE_EMOJI[toType]}</span>
              <span>{NODE_TYPE_LABEL[toType]}</span>
            </div>
            <p className="mt-1 whitespace-pre-wrap break-words text-gray-900">
              {nodePreview(toNode)}
            </p>
          </div>
        </section>

        <section>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-500">
            Обоснование
          </h3>
          {editing ? (
            <textarea
              value={draftRationale}
              onChange={(e) => setDraftRationale(e.target.value)}
              rows={3}
              maxLength={2000}
              disabled={saving}
              aria-label="Обоснование связи"
              placeholder="Почему эта связь?"
              className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
            />
          ) : currentRationale ? (
            <p className="whitespace-pre-wrap break-words text-sm text-gray-900">
              {currentRationale}
            </p>
          ) : (
            <p className="text-sm italic text-gray-500">(не указано)</p>
          )}
        </section>

        {editing && (
          <div className="space-y-2">
            {saveError && (
              <div className="rounded-md border border-red-300 bg-red-50 p-2 text-xs text-red-800">
                {saveError}
              </div>
            )}
            <div className="flex justify-end gap-2">
              <Button
                type="button"
                variant="secondary"
                onClick={cancelEdit}
                disabled={saving}
                className="!px-3 !py-1.5 text-sm"
              >
                Отмена
              </Button>
              <Button
                type="button"
                onClick={save}
                disabled={saving || allowedTypes.length === 0}
                className="!px-3 !py-1.5 text-sm"
              >
                {saving ? 'Сохраняем' : 'Сохранить'}
              </Button>
            </div>
          </div>
        )}

        <section>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-500">
            Метаданные
          </h3>
          <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-sm">
            <dt className="text-gray-500">Создана</dt>
            <dd className="text-gray-900">{formatDate(edge.createdAt)}</dd>

            <dt className="text-gray-500">Автор</dt>
            <dd className="font-mono text-xs text-gray-700" title={edge.createdBy}>
              {shortId(edge.createdBy)}
            </dd>

            <dt className="text-gray-500">ID</dt>
            <dd className="font-mono text-xs text-gray-700" title={edge.id}>
              {shortId(edge.id)}
            </dd>
          </dl>
        </section>
      </div>
    </aside>
  );
}

export default EdgeDetailsPanel;
