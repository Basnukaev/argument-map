import { useState } from 'react';
import { X, Pencil, ChevronDown, ChevronRight } from 'lucide-react';
import Button from '@/components/ui/Button';
import { apiGetRaw, apiPatchRaw, ApiError } from '@/api/client';
import type { components } from '@/api/types';
import { NODE_TYPE_EMOJI, NODE_TYPE_LABEL, type NodeType } from '@/utils/edgeRules';

type NodeDto = components['schemas']['NodeResponse'];
type RevisionDto = components['schemas']['RevisionResponse'];
type NodeStatus = NonNullable<NodeDto['status']>;
type RevisionsState =
  | { kind: 'not-loaded' }
  | { kind: 'loading' }
  | { kind: 'loaded'; revisions: RevisionDto[] }
  | { kind: 'error'; message: string };

interface Props {
  node: NodeDto;
  onClose: () => void;
  /** вызывается после успешного PATCH - чтобы родитель refetch'нул граф */
  onUpdated: () => void;
  /** если true - панель сразу открывается в режиме редактирования контента
   * (используется когда пользователь нажал "Редактировать" в контекстном меню) */
  initialEditing?: boolean;
}

const STATUS_LABEL: Record<NodeStatus, string> = {
  STANDING: 'Устоявшийся',
  DISPUTED: 'Спорный',
  REFUTED: 'Опровергнут',
  UNVERIFIED: 'Не оценён',
};

const STATUS_BADGE: Record<NodeStatus, string> = {
  STANDING: 'bg-green-100 text-green-900 border-green-500',
  DISPUTED: 'bg-amber-100 text-amber-900 border-amber-500',
  REFUTED: 'bg-red-100 text-red-900 border-red-500',
  UNVERIFIED: 'bg-gray-100 text-gray-700 border-gray-400',
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

function NodeDetailsPanel({ node, onClose, onUpdated, initialEditing = false }: Props) {
  const nodeType: NodeType = node.nodeType ?? 'CLAIM';
  const status: NodeStatus = node.status ?? 'UNVERIFIED';
  const content = node.content ?? '';
  const wasUpdated =
    node.updatedAt && node.createdAt && node.updatedAt !== node.createdAt;

  const [editing, setEditing] = useState(initialEditing);
  const [draft, setDraft] = useState(content);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const [historyOpen, setHistoryOpen] = useState(false);
  const [revisionsState, setRevisionsState] = useState<RevisionsState>({ kind: 'not-loaded' });

  // Состояние истории намеренно не сбрасывается на изменение node.updatedAt -
  // вместо этого в TopicGraphPage компонент перерендеривается через key,
  // что даёт чистое монтирование без каскадных setState в effect.

  function toggleHistory() {
    if (historyOpen) {
      setHistoryOpen(false);
      return;
    }
    setHistoryOpen(true);
    if (revisionsState.kind === 'not-loaded') {
      void loadRevisions();
    }
  }

  async function loadRevisions() {
    if (!node.id) return;
    setRevisionsState({ kind: 'loading' });
    try {
      const list = await apiGetRaw<RevisionDto[]>(`/api/v1/nodes/${node.id}/revisions`);
      setRevisionsState({ kind: 'loaded', revisions: list });
    } catch (e: unknown) {
      const message =
        e instanceof ApiError
          ? e.problem.detail || e.problem.title
          : e instanceof Error
            ? e.message
            : 'Не удалось загрузить историю';
      setRevisionsState({ kind: 'error', message });
    }
  }

  function startEdit() {
    setDraft(content);
    setSaveError(null);
    setEditing(true);
  }

  function cancelEdit() {
    if (saving) return;
    setEditing(false);
    setSaveError(null);
  }

  async function save() {
    if (!node.id) return;
    const trimmed = draft.trim();
    if (!trimmed || trimmed === content) {
      setEditing(false);
      return;
    }
    setSaving(true);
    setSaveError(null);
    try {
      await apiPatchRaw<NodeDto>(`/api/v1/nodes/${node.id}`, { content: trimmed });
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

  return (
    <aside
      role="complementary"
      aria-label="Детали узла"
      className="absolute right-0 top-0 bottom-0 z-10 flex w-96 flex-col border-l-2 border-gray-200 bg-white shadow-xl"
    >
      <header className="flex items-center justify-between gap-2 border-b border-gray-200 px-4 py-3">
        <div className="flex items-center gap-2 min-w-0">
          <span className="text-xl" aria-hidden="true">
            {NODE_TYPE_EMOJI[nodeType]}
          </span>
          <h2 className="text-base font-semibold text-gray-900 truncate">
            {NODE_TYPE_LABEL[nodeType]}
          </h2>
          <span
            className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${STATUS_BADGE[status]}`}
            data-testid="status-badge"
          >
            {STATUS_LABEL[status]}
          </span>
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

      <div className="flex-1 overflow-y-auto px-4 py-3 space-y-5">
        <section>
          <div className="mb-2 flex items-center justify-between">
            <h3 className="text-xs font-semibold uppercase tracking-wide text-gray-500">
              Содержание
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
            <div className="space-y-2">
              <textarea
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                rows={6}
                maxLength={10000}
                disabled={saving}
                aria-label="Содержание узла"
                className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
              />
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
                  disabled={saving || !draft.trim()}
                  className="!px-3 !py-1.5 text-sm"
                >
                  {saving ? 'Сохраняем' : 'Сохранить'}
                </Button>
              </div>
            </div>
          ) : content ? (
            <p className="whitespace-pre-wrap break-words text-sm text-gray-900">{content}</p>
          ) : (
            <p className="text-sm italic text-gray-500">(пусто)</p>
          )}
        </section>

        <section>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-500">
            Метаданные
          </h3>
          <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-sm">
            <dt className="text-gray-500">Создан</dt>
            <dd className="text-gray-900">{formatDate(node.createdAt)}</dd>

            {wasUpdated && (
              <>
                <dt className="text-gray-500">Обновлён</dt>
                <dd className="text-gray-900">{formatDate(node.updatedAt)}</dd>
              </>
            )}

            <dt className="text-gray-500">Автор</dt>
            <dd className="font-mono text-xs text-gray-700" title={node.createdBy}>
              {shortId(node.createdBy)}
            </dd>

            <dt className="text-gray-500">ID</dt>
            <dd className="font-mono text-xs text-gray-700" title={node.id}>
              {shortId(node.id)}
            </dd>
          </dl>
        </section>

        <section>
          <button
            type="button"
            onClick={toggleHistory}
            aria-expanded={historyOpen}
            className="flex w-full items-center gap-1 text-xs font-semibold uppercase tracking-wide text-gray-500 hover:text-gray-700"
          >
            {historyOpen ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
            История изменений
          </button>

          {historyOpen && (
            <div className="mt-2 space-y-2 text-sm">
              {revisionsState.kind === 'loading' && (
                <p className="text-gray-500">Загрузка</p>
              )}
              {revisionsState.kind === 'error' && (
                <p className="text-red-700">Ошибка: {revisionsState.message}</p>
              )}
              {revisionsState.kind === 'loaded' && revisionsState.revisions.length === 0 && (
                <p className="italic text-gray-500">Изменений ещё не было</p>
              )}
              {revisionsState.kind === 'loaded' &&
                revisionsState.revisions.length > 0 &&
                [...revisionsState.revisions]
                  .sort((a, b) => (b.changedAt ?? '').localeCompare(a.changedAt ?? ''))
                  .map((r) => (
                    <article
                      key={r.id}
                      className="rounded-md border border-gray-200 bg-gray-50 p-2 text-xs"
                    >
                      <header className="mb-1 flex items-center justify-between text-gray-500">
                        <time dateTime={r.changedAt}>{formatDate(r.changedAt)}</time>
                        <span className="font-mono" title={r.changedBy}>
                          {shortId(r.changedBy)}
                        </span>
                      </header>
                      {r.contentBefore && (
                        <p className="rounded bg-red-100 px-2 py-1 text-red-900 line-through whitespace-pre-wrap break-words">
                          {r.contentBefore}
                        </p>
                      )}
                      <p className="mt-1 rounded bg-green-100 px-2 py-1 text-green-900 whitespace-pre-wrap break-words">
                        {r.contentAfter ?? ''}
                      </p>
                    </article>
                  ))}
            </div>
          )}
        </section>
      </div>
    </aside>
  );
}

export default NodeDetailsPanel;
