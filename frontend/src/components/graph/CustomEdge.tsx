import { BaseEdge, EdgeLabelRenderer, getBezierPath } from '@xyflow/react';
import type { EdgeProps, Edge } from '@xyflow/react';
import type { components } from '@/api/types';

type EdgeDto = components['schemas']['EdgeResponse'];
type EdgeType = NonNullable<EdgeDto['edgeType']>;

export type CustomEdgeData = {
  edgeType: EdgeType;
  rationale?: string;
};

export type CustomEdgeEdge = Edge<CustomEdgeData, 'argumentEdge'>;

const TYPE_LABELS: Record<EdgeType, string> = {
  SUPPORTS: 'поддерживает',
  REFUTES: 'опровергает',
  INVALIDATES: 'аннулирует',
  QUALIFIES: 'уточняет',
  RESPONDS_TO: 'отвечает',
};

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
  const label = TYPE_LABELS[edgeType];

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
          className={`pointer-events-none absolute rounded border px-1.5 py-0.5 text-[10px] uppercase tracking-wide ${style.badge}`}
          style={{
            transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
          }}
        >
          {label}
        </div>
      </EdgeLabelRenderer>
    </>
  );
}

export default CustomEdge;
