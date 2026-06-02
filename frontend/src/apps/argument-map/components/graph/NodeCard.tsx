import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps, Node } from '@xyflow/react';
import StatusBadge from '@/shared/components/ui/StatusBadge';
import TypeChip from '@/shared/components/ui/TypeChip';
import InlineCitationBody from '@/apps/argument-map/components/citation/InlineCitationBody';
import { STATUS_TOKENS, type NodeStatus, type NodeType } from '@/shared/utils/designTokens';
import { hasArabicScript } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type NodeDto = components['schemas']['NodeResponse'];

export type NodeCardData = NodeDto;
export type NodeCardNode = Node<NodeCardData, 'argumentNode'>;

const MAX_PREVIEW_LEN = 220;

/**
 * Эффективный язык оригинала. Если бэк прислал явный originalLang -
 * используем его, иначе определяем по содержимому через hasArabicScript
 * (только так в MVP - 'ar' либо 'ru'). 'en' через эвристику не угадаем,
 * но это редкий кейс в проекте (контент в основном RU/AR).
 */
function resolveOriginalLang(content: string, explicitLang?: string): 'ar' | 'ru' | 'en' {
  if (explicitLang === 'ar' || explicitLang === 'ru' || explicitLang === 'en') {
    return explicitLang;
  }
  return hasArabicScript(content) ? 'ar' : 'ru';
}

function truncate(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

function NodeCard({ data, selected }: NodeProps<NodeCardNode>) {
  const status: NodeStatus = data.status ?? 'UNVERIFIED';
  const nodeType: NodeType = data.nodeType ?? 'CLAIM';
  const statusToken = STATUS_TOKENS[status];

  // Карточка всегда показывает только оригинальный content. Поле
  // data.translations с бэка игнорируем (двуязычный режим выпилен).
  const fullContent = data.content ?? '';
  const originalLang = resolveOriginalLang(fullContent, data.originalLang);

  // первая строка трактуется как заголовок, остаток - как body. Если перенос
  // отсутствует - всё считается заголовком (короткие узлы выглядят чище)
  const newlineIndex = fullContent.indexOf('\n');
  const title =
    newlineIndex === -1 ? fullContent : fullContent.slice(0, newlineIndex);
  const body = newlineIndex === -1 ? '' : fullContent.slice(newlineIndex + 1).trim();
  const truncatedBody = truncate(body, MAX_PREVIEW_LEN);

  // Стили для оригинала - арабский получает naskh + увеличенный leading.
  // Direction через dir="auto" - браузер сам определит по первому
  // strong-символу. Layout самой карточки остаётся LTR независимо от
  // содержимого
  const isOriginalArabic = originalLang === 'ar';
  const titleClass = isOriginalArabic
    ? 'font-naskh text-sm font-semibold leading-[1.8] text-ink-900 text-pretty whitespace-pre-wrap break-words text-start'
    : 'text-sm font-semibold leading-snug text-ink-900 text-pretty whitespace-pre-wrap break-words text-start';
  const bodyClass = isOriginalArabic
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
        <div className="mb-2.5 flex items-center gap-2">
          <TypeChip type={nodeType} size="sm" />
          <span className="flex-1" />
          <StatusBadge status={status} size="sm" />
        </div>

        {title ? (
          <p dir="auto" className={titleClass}>
            {title}
          </p>
        ) : (
          <p className="text-sm italic text-ink-400">(...)</p>
        )}

        {truncatedBody && (
          <InlineCitationBody
            body={truncatedBody}
            citations={data.inlineCitations}
            dir="auto"
            className={`block ${bodyClass}`}
          />
        )}
      </div>
    </div>
  );
}

export default memo(NodeCard);
