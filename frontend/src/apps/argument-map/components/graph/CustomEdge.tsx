import { memo } from 'react';
import { BaseEdge, EdgeLabelRenderer, getBezierPath, getSmoothStepPath } from '@xyflow/react';
import type { EdgeProps, Edge } from '@xyflow/react';
import { getContextualEdgeLabelKey, EDGE_TYPE_META } from '@/apps/argument-map/utils/edgeRules';
import { useT } from '@/shared/i18n';
import type { EdgeType, NodeType } from '@/apps/argument-map/utils/edgeRules';
import { EDGE_TYPE_TOKENS } from '@/shared/utils/designTokens';
import { useLayoutPresetStore } from '@/shared/stores/layoutPresetStore';
import {
  buildRoundedOrthogonalPath,
  pickLabelPosition,
} from '@/apps/argument-map/utils/orthogonalPath';

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
  /**
   * Bend points из ELK layout - координаты изломов ортогонального
   * пути в системе React Flow. Заполнено для tree-presets после
   * `applyLayout`, undefined для radial. CustomEdge использует их
   * для precise rendering вместо геометрической smoothstep
   * аппроксимации - точно match'ит layout движок.
   */
  bendPoints?: Array<{ x: number; y: number }>;
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

  // Routing зависит от preset + наличия bend points из layout:
  //
  // 1) tree-* + есть bendPoints (от ELK ORTHOGONAL) → строим path
  //    строго по этим точкам со скруглёнными углами 12px. Это match'ит
  //    точный routing алгоритма - в т.ч. правильные обходы вокруг
  //    соседних узлов, без диагональных bezier «через весь экран».
  //
  // 2) tree-* без bendPoints (initial render до relayout) → fallback
  //    на getSmoothStepPath — геометрическая аппроксимация. Хуже но
  //    приемлемо для коротких рёбер.
  //
  // 3) radial → bezier с curvature. Дуги от центра к периферии
  //    смотрятся естественнее ортогональных углов на радиальной
  //    раскладке.
  const preset = useLayoutPresetStore((s) => s.preset);
  const useOrthogonal = preset === 'tree-tb' || preset === 'tree-lr';
  const bendPoints = data?.bendPoints;

  let edgePath: string;
  let labelX: number;
  let labelY: number;

  if (useOrthogonal && bendPoints && bendPoints.length > 0) {
    const points = [
      { x: sourceX, y: sourceY },
      ...bendPoints,
      { x: targetX, y: targetY },
    ];
    edgePath = buildRoundedOrthogonalPath(points, 12);
    const labelPos = pickLabelPosition(points);
    labelX = labelPos.x;
    labelY = labelPos.y;
  } else if (useOrthogonal) {
    [edgePath, labelX, labelY] = getSmoothStepPath({
      sourceX,
      sourceY,
      sourcePosition,
      targetX,
      targetY,
      targetPosition,
      borderRadius: 12,
      offset: 20,
    });
  } else {
    [edgePath, labelX, labelY] = getBezierPath({
      sourceX,
      sourceY,
      sourcePosition,
      targetX,
      targetY,
      targetPosition,
      curvature,
    });
  }

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
