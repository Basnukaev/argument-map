import { Handle, Position } from '@xyflow/react';
import type { NodeProps, Node } from '@xyflow/react';
import { MoreHorizontal } from 'lucide-react';
import StatusBadge from '@/shared/components/ui/StatusBadge';
import TypeChip from '@/shared/components/ui/TypeChip';
import { STATUS_TOKENS, type NodeStatus, type NodeType } from '@/shared/utils/designTokens';
import { hasArabicScript } from '@/shared/i18n';
import type { components } from '@/shared/api/types';
import VoteWidget from './VoteWidget';

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

  // Направление текста узла - через dir="auto" (браузер сам определит по
  // первому сильному символу). Шрифт - через эвристику, т.к. dir="auto"
  // шрифт не переключает. Layout самой карточки (canvas, позиции, handles)
  // остаётся LTR независимо от языка контента - граф пространственный.
  const isArabicContent = hasArabicScript(fullContent);
  const titleClass = isArabicContent
    ? 'font-naskh text-sm font-semibold leading-[1.8] text-ink-900 text-pretty whitespace-pre-wrap break-words text-start'
    : 'text-sm font-semibold leading-snug text-ink-900 text-pretty whitespace-pre-wrap break-words text-start';
  const bodyClass = isArabicContent
    ? 'mt-1 font-naskh text-sm leading-[1.85] text-ink-600 line-clamp-2 text-pretty whitespace-pre-wrap break-words text-start'
    : 'mt-1 text-xs leading-relaxed text-ink-600 line-clamp-2 text-pretty whitespace-pre-wrap break-words text-start';

  // Handle hit-area расширена до 28×28 через ::before inset-[-8px] - удобно
  // попадать мышкой даже в визуально-12×12 точки. Видимы только на hover/select
  const handleClass =
    '!w-3 !h-3 !bg-elevated !border-[1.5px] !border-accent-500 opacity-0 group-hover:opacity-100 transition-opacity cursor-crosshair before:absolute before:inset-[-8px] before:content-[""]';

  return (
    <div
      className={`group relative w-[280px] rounded-md border bg-elevated transition-shadow ${
        selected
          ? 'border-accent-500 ring-2 ring-accent-500/30 shadow-sh3'
          : 'border-border shadow-sh1 hover:border-border-strong hover:shadow-sh2'
      }`}
      title={fullContent}
    >
      <div
        data-testid="status-bar"
        className={`absolute left-0 top-0 bottom-0 w-[5px] rounded-l-md ${statusToken.bar}`}
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
            aria-label="actions"
            className="-mr-1 text-ink-400 hover:text-ink-700 transition-colors"
            onClick={(e) => e.stopPropagation()}
          >
            <MoreHorizontal size={14} aria-hidden="true" />
          </button>
        </div>

        {title ? (
          <p dir="auto" className={titleClass}>
            {title}
          </p>
        ) : (
          <p className="text-sm italic text-ink-400">(...)</p>
        )}

        {truncatedBody && (
          <p dir="auto" className={bodyClass}>
            {truncatedBody}
          </p>
        )}

        {(nodeType === 'ARGUMENT' || nodeType === 'EVIDENCE') && (
          <div className="mt-2 flex justify-end">
            <VoteWidget
              nodeId={data.id ?? ''}
              upvotes={data.voteUpvotes ?? 0}
              downvotes={data.voteDownvotes ?? 0}
              score={data.voteScore ?? 0}
              userVote={data.userVote ?? null}
            />
          </div>
        )}
      </div>
    </div>
  );
}

export default NodeCard;
