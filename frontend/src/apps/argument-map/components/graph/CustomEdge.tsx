import { memo } from 'react';
import { BaseEdge, EdgeLabelRenderer, getBezierPath, getSmoothStepPath } from '@xyflow/react';
import type { EdgeProps, Edge } from '@xyflow/react';
import { getContextualEdgeLabelKey, EDGE_TYPE_META } from '@/apps/argument-map/utils/edgeRules';
import { useT } from '@/shared/i18n';
import type { EdgeType, NodeType } from '@/apps/argument-map/utils/edgeRules';
import { EDGE_TYPE_TOKENS } from '@/shared/utils/designTokens';
import { useLayoutPresetStore } from '@/shared/stores/layoutPresetStore';

export type CustomEdgeData = {
  edgeType: EdgeType;
  fromType: NodeType;
  toType: NodeType;
  rationale?: string;
  showLabel?: boolean;
  /**
   * Кривизна Безье для этого ребра. Вычисляется один раз в buildFlow
   * через computeSiblingCurvatures — параллельные рёбра между той же
   * парой узлов получают разные значения и расходятся веером.
   * Default 0.25 (стандартная кривизна RF).
   */
  curvature?: number;
};

export type CustomEdgeEdge = Edge<CustomEdgeData, 'argumentEdge'>;

function CustomEdge(props: EdgeProps<CustomEdgeEdge>) {
  const {
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
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

  // curvature вычислена в buildFlow (computeSiblingCurvatures) и передана
  // через data — не нужен useEdges() в каждом компоненте (O(E²) per rerender)
  const curvature = data?.curvature ?? 0.25;

  // Routing зависит от preset: для tree-* (Sugiyama-layered с ELK
  // ORTHOGONAL) канонично рисовать рёбра smoothstep'ом со скруглёнными
  // углами 12px - это match'ит ортогональный routing layout-движка
  // и убирает диагональные bezier-кривые «через весь экран» (web Claude
  // claim в скринах). Для radial preset bezier выглядит органичнее
  // (дуги от центра к периферии).
  const preset = useLayoutPresetStore((s) => s.preset);
  const useOrthogonal = preset === 'tree-tb' || preset === 'tree-lr';

  const [edgePath, labelX, labelY] = useOrthogonal
    ? getSmoothStepPath({
        sourceX,
        sourceY,
        sourcePosition,
        targetX,
        targetY,
        targetPosition,
        borderRadius: 12,
        offset: 20,
      })
    : getBezierPath({
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

export default memo(CustomEdge);
