import { Handle, Position } from '@xyflow/react';
import type { NodeProps, Node } from '@xyflow/react';
import { CircleHelp, Megaphone, MessageSquareQuote, FileText } from 'lucide-react';
import type { ComponentType } from 'react';
import type { components } from '@/api/types';

type NodeDto = components['schemas']['NodeResponse'];
type NodeType = NonNullable<NodeDto['nodeType']>;
type NodeStatus = NonNullable<NodeDto['status']>;

export type NodeCardData = NodeDto;
export type NodeCardNode = Node<NodeCardData, 'argumentNode'>;

const STATUS_CLASSES: Record<NodeStatus, string> = {
  STANDING: 'bg-green-50 border-green-500',
  DISPUTED: 'bg-amber-50 border-amber-500',
  REFUTED: 'bg-red-50 border-red-500',
  UNVERIFIED: 'bg-gray-50 border-gray-400',
};

const TYPE_META: Record<
  NodeType,
  { label: string; Icon: ComponentType<{ className?: string; size?: number }> }
> = {
  QUESTION: { label: 'Вопрос', Icon: CircleHelp },
  CLAIM: { label: 'Тезис', Icon: Megaphone },
  ARGUMENT: { label: 'Довод', Icon: MessageSquareQuote },
  EVIDENCE: { label: 'Свидетельство', Icon: FileText },
};

const MAX_PREVIEW_LEN = 150;

function NodeCard({ data, selected }: NodeProps<NodeCardNode>) {
  const status: NodeStatus = data.status ?? 'UNVERIFIED';
  const nodeType: NodeType = data.nodeType ?? 'CLAIM';
  const meta = TYPE_META[nodeType];
  const Icon = meta.Icon;

  const fullContent = data.content ?? '';
  const preview =
    fullContent.length > MAX_PREVIEW_LEN ? `${fullContent.slice(0, MAX_PREVIEW_LEN)}…` : fullContent;

  const ringClass = selected ? 'ring-2 ring-blue-400 ring-offset-2' : '';

  // 8 handles: на каждой стороне (top/right/bottom/left) пара source+target.
  // React Flow при drag из source создаёт линию, при drop на target - коннект.
  // Сами handles стандартные `react-flow__handle` 6x6px - ставим Tailwind классы
  // через `!` чтобы переопределить и подсветить, group-hover даёт Miro-эффект:
  // при наведении на узел все 4 точки одновременно подсвечиваются
  const handleClass =
    '!w-3 !h-3 !bg-blue-500 !border-2 !border-white opacity-0 group-hover:opacity-100 transition-opacity';

  return (
    <div
      className={`group w-72 rounded-lg border-2 shadow-sm transition-shadow ${STATUS_CLASSES[status]} ${ringClass}`}
      title={fullContent}
    >
      <Handle type="source" position={Position.Top} id="top-source" className={handleClass} />
      <Handle type="target" position={Position.Top} id="top-target" className={handleClass} />
      <Handle type="source" position={Position.Right} id="right-source" className={handleClass} />
      <Handle type="target" position={Position.Right} id="right-target" className={handleClass} />
      <Handle type="source" position={Position.Bottom} id="bottom-source" className={handleClass} />
      <Handle type="target" position={Position.Bottom} id="bottom-target" className={handleClass} />
      <Handle type="source" position={Position.Left} id="left-source" className={handleClass} />
      <Handle type="target" position={Position.Left} id="left-target" className={handleClass} />

      <div className="flex items-center gap-2 border-b border-current/10 px-3 py-2">
        <Icon className="text-current" size={16} />
        <span className="text-xs font-semibold uppercase tracking-wide text-current/70">
          {meta.label}
        </span>
      </div>

      <div className="px-3 py-2 text-sm text-gray-900">
        {preview ? (
          <p className="whitespace-pre-wrap break-words">{preview}</p>
        ) : (
          <p className="italic text-gray-500">(пусто)</p>
        )}
      </div>
    </div>
  );
}

export default NodeCard;
