import { useStore } from '@xyflow/react';
import type { MiniMapNodeProps } from '@xyflow/react';
import { NODE_TYPE_META, type NodeType } from '@/utils/edgeRules';
import type { NodeCardData } from '@/components/graph/NodeCard';

type NodeStatus = NonNullable<NodeCardData['status']>;

const STATUS_CLASSES: Record<NodeStatus, string> = {
  STANDING: 'bg-green-50 border-green-500',
  DISPUTED: 'bg-amber-50 border-amber-500',
  REFUTED: 'bg-red-50 border-red-500',
  UNVERIFIED: 'bg-gray-50 border-gray-400',
};

const PREVIEW_LEN = 150;

// Виртуальные размеры контента - совпадают с реальным NodeCard (w-72 = 288px,
// высота ~120px без weight-блока). Внутри foreignObject рендерим в этих размерах,
// потом масштабируем к фактическим размерам узла на minimap. Шрифты, иконки
// и пропорции остаются такими же как на канвасе - просто меньше
const VIRTUAL_W = 288;
const VIRTUAL_H = 120;

/**
 * Узел на MiniMap - уменьшенная копия NodeCard через `foreignObject` +
 * CSS transform scale. RF default рисует прямоугольники, что не даёт
 * представления о содержимом. Здесь миникарта становится миниверсией
 * самого канваса
 */
function GraphMiniMapNode({ id, x, y, width, height }: MiniMapNodeProps) {
  const data = useStore((state) => {
    const node = state.nodeLookup.get(id);
    return node?.data as NodeCardData | undefined;
  });

  if (!data) return null;

  const status: NodeStatus = data.status ?? 'UNVERIFIED';
  const nodeType: NodeType = data.nodeType ?? 'CLAIM';
  const meta = NODE_TYPE_META[nodeType];
  const Icon = meta.Icon;

  const fullContent = data.content ?? '';
  const preview =
    fullContent.length > PREVIEW_LEN ? `${fullContent.slice(0, PREVIEW_LEN)}…` : fullContent;

  const scaleX = width / VIRTUAL_W;
  const scaleY = height / VIRTUAL_H;

  return (
    <foreignObject x={x} y={y} width={width} height={height} style={{ pointerEvents: 'none' }}>
      <div
        style={{
          width: VIRTUAL_W,
          height: VIRTUAL_H,
          transform: `scale(${scaleX}, ${scaleY})`,
          transformOrigin: 'top left',
        }}
        className={`flex flex-col overflow-hidden rounded-lg border-2 ${STATUS_CLASSES[status]}`}
      >
        <div className="flex items-center gap-2 border-b border-current/10 px-3 py-2">
          <Icon size={16} />
          <span className="text-xs font-semibold uppercase tracking-wide text-current/70">
            {meta.label}
          </span>
        </div>
        <div className="flex-1 overflow-hidden px-3 py-2 text-sm text-gray-900">
          {preview ? (
            <p className="whitespace-pre-wrap break-words">{preview}</p>
          ) : (
            <p className="italic text-gray-500">(пусто)</p>
          )}
        </div>
      </div>
    </foreignObject>
  );
}

export default GraphMiniMapNode;
