import { BaseEdge, EdgeLabelRenderer, getBezierPath } from '@xyflow/react';
import type { EdgeProps, Edge } from '@xyflow/react';
import { getContextualEdgeLabelKey, EDGE_TYPE_META } from '@/apps/argument-map/utils/edgeRules';
import { useT } from '@/shared/i18n';
import type { EdgeType, NodeType } from '@/apps/argument-map/utils/edgeRules';
import { EDGE_TYPE_TOKENS } from '@/shared/utils/designTokens';

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

  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
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
          className={`pointer-events-none absolute flex items-center gap-1 rounded-md border bg-elevated px-1.5 py-0.5 text-xs font-medium uppercase tracking-wide shadow-[0_1px_2px_rgba(15,23,42,0.06)] ${token.badgeBg} ${token.badgeText} ${token.badgeBorder} ${edgeType === 'INVALIDATES' ? 'font-bold' : ''}`}
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
