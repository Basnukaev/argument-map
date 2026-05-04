import { X } from 'lucide-react';
import type { components } from '@/api/types';
import { NODE_TYPE_EMOJI, NODE_TYPE_LABEL, type NodeType } from '@/utils/edgeRules';

type NodeDto = components['schemas']['NodeResponse'];

interface Props {
  node: NodeDto;
  onClose: () => void;
}

function NodeDetailsPanel({ node, onClose }: Props) {
  const nodeType: NodeType = node.nodeType ?? 'CLAIM';
  const content = node.content ?? '';

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

      <div className="flex-1 overflow-y-auto px-4 py-3">
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
      </div>
    </aside>
  );
}

export default NodeDetailsPanel;
