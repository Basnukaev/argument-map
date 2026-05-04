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
  const weight = data.weight ?? 0;

  const ringClass = selected ? 'ring-2 ring-blue-400 ring-offset-2' : '';

  return (
    <div
      className={`w-72 rounded-lg border-2 shadow-sm transition-shadow ${STATUS_CLASSES[status]} ${ringClass}`}
      title={fullContent}
    >
      <Handle type="target" position={Position.Top} className="!bg-gray-500" />

      <div className="flex items-center gap-2 border-b border-current/10 px-3 py-2">
        <Icon className="text-current" size={16} />
        <span className="text-xs font-semibold uppercase tracking-wide text-current/70">
          {meta.label}
        </span>
      </div>

      <div className="px-3 py-2 text-sm text-gray-900">
        {preview ? <p className="whitespace-pre-wrap break-words">{preview}</p> : (
          <p className="italic text-gray-500">(пусто)</p>
        )}
      </div>

      <div className="flex items-center justify-between border-t border-current/10 px-3 py-1.5 text-xs text-current/70">
        <WeightBar weight={weight} />
      </div>

      <Handle type="source" position={Position.Bottom} className="!bg-gray-500" />
    </div>
  );
}

function WeightBar({ weight }: { weight: number }) {
  const clamped = Math.max(0, Math.min(10, Math.round(weight)));
  const dots = Array.from({ length: 10 }, (_, i) => i < clamped);
  return (
    <div className="flex items-center gap-1.5" title={`Вес: ${clamped}/10`}>
      <div className="flex gap-0.5">
        {dots.map((on, i) => (
          <span
            key={i}
            className={`h-1.5 w-1.5 rounded-full ${on ? 'bg-current/70' : 'bg-current/15'}`}
          />
        ))}
      </div>
      <span className="font-mono">{clamped}/10</span>
    </div>
  );
}

export default NodeCard;
