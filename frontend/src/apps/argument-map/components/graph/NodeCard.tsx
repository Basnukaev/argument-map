import { Handle, Position } from '@xyflow/react';
import type { NodeProps, Node } from '@xyflow/react';
import { MoreHorizontal } from 'lucide-react';
import StatusBadge from '@/shared/components/ui/StatusBadge';
import TypeChip from '@/shared/components/ui/TypeChip';
import { STATUS_TOKENS, type NodeStatus, type NodeType } from '@/shared/utils/designTokens';
import type { components } from '@/shared/api/types';

type NodeDto = components['schemas']['NodeResponse'];

export type NodeCardData = NodeDto;
export type NodeCardNode = Node<NodeCardData, 'argumentNode'>;

const MAX_PREVIEW_LEN = 220;

function NodeCard({ data, selected }: NodeProps<NodeCardNode>) {
  const status: NodeStatus = data.status ?? 'UNVERIFIED';
  const nodeType: NodeType = data.nodeType ?? 'CLAIM';
  const statusToken = STATUS_TOKENS[status];

  const fullContent = data.content ?? '';
  // первая строка трактуется как заголовок, остаток - как body. Если перенос
  // отсутствует - всё считается заголовком (короткие узлы выглядят чище)
  const newlineIndex = fullContent.indexOf('\n');
  const title =
    newlineIndex === -1 ? fullContent : fullContent.slice(0, newlineIndex);
  const body = newlineIndex === -1 ? '' : fullContent.slice(newlineIndex + 1).trim();
  const truncatedBody =
    body.length > MAX_PREVIEW_LEN ? `${body.slice(0, MAX_PREVIEW_LEN)}…` : body;

  // Handle hit-area расширена до 28×28 через ::before inset-[-8px] - удобно
  // попадать мышкой даже в визуально-12×12 точки. Видимы только на hover/select
  const handleClass =
    '!w-3 !h-3 !bg-white !border-[1.5px] !border-indigo-500 opacity-0 group-hover:opacity-100 transition-opacity cursor-crosshair before:absolute before:inset-[-8px] before:content-[""]';

  return (
    <div
      className={`group relative w-[280px] rounded-xl border bg-white transition-shadow ${
        selected
          ? 'border-indigo-500 shadow-[0_0_0_3px_rgba(99,102,241,0.18),0_8px_20px_rgba(15,23,42,0.10)]'
          : 'border-slate-200 shadow-[0_1px_2px_rgba(15,23,42,0.04),0_2px_6px_rgba(15,23,42,0.04)] hover:border-slate-300 hover:shadow-[0_4px_12px_rgba(15,23,42,0.10)]'
      }`}
      title={fullContent}
    >
      <div
        data-testid="status-bar"
        className={`absolute left-0 top-0 bottom-0 w-[5px] rounded-l-xl ${statusToken.bar}`}
        aria-hidden="true"
      />

      <Handle type="source" position={Position.Top} id="top" className={handleClass} />
      <Handle type="source" position={Position.Right} id="right" className={handleClass} />
      <Handle type="source" position={Position.Bottom} id="bottom" className={handleClass} />
      <Handle type="source" position={Position.Left} id="left" className={handleClass} />

      <div className="pl-4 pr-3 py-3">
        <div className="mb-1.5 flex items-center gap-2">
          <TypeChip type={nodeType} size="sm" />
          <span className="flex-1" />
          <StatusBadge status={status} size="sm" />
          <button
            type="button"
            tabIndex={-1}
            aria-label="Действия"
            className="-mr-1 text-slate-400 hover:text-slate-700 transition-colors"
            onClick={(e) => e.stopPropagation()}
          >
            <MoreHorizontal size={14} aria-hidden="true" />
          </button>
        </div>

        {title ? (
          <p className="text-[13px] font-semibold leading-snug text-slate-900 text-pretty whitespace-pre-wrap break-words">
            {title}
          </p>
        ) : (
          <p className="text-[13px] italic text-slate-400">(пусто)</p>
        )}

        {truncatedBody && (
          <p className="mt-1 text-[12px] leading-relaxed text-slate-600 line-clamp-2 text-pretty whitespace-pre-wrap break-words">
            {truncatedBody}
          </p>
        )}
      </div>
    </div>
  );
}

export default NodeCard;
