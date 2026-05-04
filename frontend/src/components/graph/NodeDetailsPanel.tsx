import { X } from 'lucide-react';
import type { components } from '@/api/types';
import { NODE_TYPE_EMOJI, NODE_TYPE_LABEL, type NodeType } from '@/utils/edgeRules';

type NodeDto = components['schemas']['NodeResponse'];
type NodeStatus = NonNullable<NodeDto['status']>;

interface Props {
  node: NodeDto;
  onClose: () => void;
}

const STATUS_LABEL: Record<NodeStatus, string> = {
  STANDING: 'Устоявшийся',
  DISPUTED: 'Спорный',
  REFUTED: 'Опровергнут',
  UNVERIFIED: 'Не оценён',
};

// Цвета бейджа - те же что для карточки узла, чтобы была визуальная связка
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

// UUID показываем сокращённо (первые 8 символов) с full в tooltip
function shortId(id?: string): string {
  if (!id) return '—';
  return id.slice(0, 8);
}

function NodeDetailsPanel({ node, onClose }: Props) {
  const nodeType: NodeType = node.nodeType ?? 'CLAIM';
  const status: NodeStatus = node.status ?? 'UNVERIFIED';
  const content = node.content ?? '';
  const wasUpdated =
    node.updatedAt && node.createdAt && node.updatedAt !== node.createdAt;

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
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-500">
            Содержание
          </h3>
          {content ? (
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
      </div>
    </aside>
  );
}

export default NodeDetailsPanel;
