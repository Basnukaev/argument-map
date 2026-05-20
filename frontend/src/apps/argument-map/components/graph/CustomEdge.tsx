import { BaseEdge, EdgeLabelRenderer, getBezierPath, useEdges } from '@xyflow/react';
import type { EdgeProps, Edge } from '@xyflow/react';
import { getContextualEdgeLabelKey, EDGE_TYPE_META } from '@/apps/argument-map/utils/edgeRules';
import { useT } from '@/shared/i18n';
import type { EdgeType, NodeType } from '@/apps/argument-map/utils/edgeRules';
import { EDGE_TYPE_TOKENS } from '@/shared/utils/designTokens';

/**
 * Рассчитывает кривизну Безье для конкретного ребра с учётом «братских»
 * рёбер между той же парой узлов (source+target в том же порядке).
 *
 * Логика:
 * - 1 ребро → стандартная кривизна 0.25
 * - N рёбер → равномерно распределяем от 0.1 до 0.6, чтобы кривые
 *   расходились веером и не сливались в одну линию
 *
 * Сортируем siblings по id для детерминированного порядка: одинаковый
 * индекс между рендерами → нет дрожания кривых при обновлении графа
 */
function useSiblingCurvature(
  currentEdgeId: string,
  source: string,
  target: string,
): number {
  const allEdges = useEdges();

  const siblings = allEdges
    .filter((e) => e.source === source && e.target === target)
    .sort((a, b) => a.id.localeCompare(b.id));

  if (siblings.length <= 1) return 0.25;

  const myIndex = siblings.findIndex((e) => e.id === currentEdgeId);
  const count = siblings.length;
  const minCurv = 0.1;
  const maxCurv = 0.6;
  // равномерно от minCurv до maxCurv: при count=2 → [0.1, 0.6]
  // при count=3 → [0.1, 0.35, 0.6], и т.д.
  return minCurv + (myIndex / (count - 1)) * (maxCurv - minCurv);
}

export type CustomEdgeData = {
  edgeType: EdgeType;
  fromType: NodeType;
  toType: NodeType;
  rationale?: string;
  showLabel?: boolean;
};

export type CustomEdgeEdge = Edge<CustomEdgeData, 'argumentEdge'>;

function CustomEdge(props: EdgeProps<CustomEdgeEdge>) {
  const {
    id,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    source,
    target,
    data,
    selected,
    markerEnd,
  } = props;

  const t = useT();
  const edgeType = data?.edgeType ?? 'SUPPORTS';
  const token = EDGE_TYPE_TOKENS[edgeType];
  const Icon = EDGE_TYPE_META[edgeType].Icon;
  const showLabel = data?.showLabel ?? true;
  const label =
    data?.fromType && data?.toType
      ? t(getContextualEdgeLabelKey(data.fromType, edgeType, data.toType))
      : '';

  // curvature рассчитывается с учётом параллельных рёбер между той же
  // парой узлов: они получают разные значения и расходятся веером
  const curvature = useSiblingCurvature(id, source, target);

  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    curvature,
  });

  return (
    <>
      <BaseEdge
        path={edgePath}
        markerEnd={markerEnd}
        style={{
          stroke: token.stroke,
          strokeWidth: selected ? token.strokeWidth + 1 : token.strokeWidth,
          strokeDasharray: token.strokeDasharray,
          opacity: token.opacity ?? 1,
        }}
      />
      <EdgeLabelRenderer>
        <div
          className={`pointer-events-none absolute flex items-center gap-1 rounded-md border bg-elevated px-1.5 py-0.5 text-xs font-medium uppercase tracking-wide shadow-sh2 ${token.badgeBg} ${token.badgeText} ${token.badgeBorder} ${edgeType === 'INVALIDATES' ? 'font-bold' : ''}`}
          style={{
            transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
          }}
        >
          <Icon size={12} strokeWidth={2.5} aria-hidden="true" className="shrink-0" />
          {showLabel && label && <span>{label}</span>}
        </div>
      </EdgeLabelRenderer>
    </>
  );
}

export default CustomEdge;
