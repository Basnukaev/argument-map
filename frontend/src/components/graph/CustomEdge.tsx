import { BaseEdge, EdgeLabelRenderer, getBezierPath } from '@xyflow/react';
import type { EdgeProps, Edge } from '@xyflow/react';
import { getContextualEdgeLabel, EDGE_TYPE_META } from '@/utils/edgeRules';
import type { EdgeType, NodeType } from '@/utils/edgeRules';

export type CustomEdgeData = {
  edgeType: EdgeType;
  fromType: NodeType;
  toType: NodeType;
  rationale?: string;
  showLabel?: boolean;
};

export type CustomEdgeEdge = Edge<CustomEdgeData, 'argumentEdge'>;

interface StyleSpec {
  stroke: string;
  strokeWidth: number;
  strokeDasharray?: string;
  opacity?: number;
  badge: string;
}

const TYPE_STYLES: Record<EdgeType, StyleSpec> = {
  SUPPORTS: { stroke: '#22c55e', strokeWidth: 2, badge: 'bg-green-100 text-green-900 border-green-400' },
  REFUTES: { stroke: '#ef4444', strokeWidth: 2, badge: 'bg-red-100 text-red-900 border-red-400' },
  INVALIDATES: {
    stroke: '#b91c1c',
    strokeWidth: 3,
    strokeDasharray: '8 4',
    badge: 'bg-red-200 text-red-950 border-red-700 font-bold',
  },
  QUALIFIES: { stroke: '#3b82f6', strokeWidth: 2, badge: 'bg-blue-100 text-blue-900 border-blue-400' },
  RESPONDS_TO: {
    stroke: '#9ca3af',
    strokeWidth: 1.5,
    opacity: 0.7,
    badge: 'bg-gray-100 text-gray-700 border-gray-400',
  },
};

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

  const edgeType = data?.edgeType ?? 'SUPPORTS';
  const style = TYPE_STYLES[edgeType];
  const Icon = EDGE_TYPE_META[edgeType].Icon;
  const showLabel = data?.showLabel ?? true;
  const label =
    data?.fromType && data?.toType
      ? getContextualEdgeLabel(data.fromType, edgeType, data.toType)
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
          stroke: style.stroke,
          strokeWidth: selected ? style.strokeWidth + 1 : style.strokeWidth,
          strokeDasharray: style.strokeDasharray,
          opacity: style.opacity ?? 1,
        }}
      />
      <EdgeLabelRenderer>
        <div
          className={`pointer-events-none absolute flex items-center gap-1 rounded border px-1.5 py-0.5 text-[10px] uppercase tracking-wide ${style.badge}`}
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
