import { memo } from 'react';
import { BaseEdge, EdgeLabelRenderer, getBezierPath, getSmoothStepPath } from '@xyflow/react';
import type { EdgeProps, Edge } from '@xyflow/react';
import { getContextualEdgeLabelKey, EDGE_TYPE_META } from '@/apps/argument-map/utils/edgeRules';
import { useT } from '@/shared/i18n';
import type { EdgeType, NodeType } from '@/apps/argument-map/utils/edgeRules';
import { EDGE_TYPE_TOKENS } from '@/shared/utils/designTokens';
import { useLayoutPresetStore } from '@/shared/stores/layoutPresetStore';
import { useEdgeStyleStore } from '@/shared/stores/edgeStyleStore';
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

  // Routing зависит от preset + edgeStyle (user toggle) + наличия
  // bend points из layout:
  //
  // 1) tree-* + edgeStyle=orthogonal + bendPoints → строим path
  //    строго по точкам со скруглёнными углами 12px. Match'ит
  //    точный routing ELK - инженерный look, читается как блок-схема.
  //
  // 2) tree-* + edgeStyle=orthogonal без bendPoints (initial render
  //    до relayout) → fallback на getSmoothStepPath — геометрическая
  //    аппроксимация.
  //
  // 3) tree-* + edgeStyle=smooth → bezier с curvature. Мягкий look,
  //    игнорирует ortho bend points. Toggle для пользователей которым
  //    инженерный стиль не нравится.
  //
  // 4) radial → всегда bezier с curvature, edgeStyle игнорируется.
  //    Дуги к концентрическим кольцам естественнее ортогональных
  //    углов в полярной топологии.
  const preset = useLayoutPresetStore((s) => s.preset);
  const edgeStyle = useEdgeStyleStore((s) => s.edgeStyle);
  const isTree = preset === 'tree-tb' || preset === 'tree-lr';
  const useOrthogonal = isTree && edgeStyle === 'orthogonal';
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
        // interactionWidth=24 даёт invisible click-path 24px вокруг
        // visible линии - сильно облегчает выделение когда несколько
        // рёбер близко (например все идут к одному handle узла).
        // Default RF = 20, мы поднимаем до 24 для нашего edge-density.
        interactionWidth={24}
        style={{
          stroke: token.stroke,
          // selected: +3px strokeWidth + drop-shadow accent halo -
          // выделенное ребро визуально отделяется от соседей в пучке,
          // понятно «вот это активно».
          strokeWidth: selected ? token.strokeWidth + 3 : token.strokeWidth,
          strokeDasharray: token.strokeDasharray,
          opacity: token.opacity ?? 1,
          filter: selected ? 'drop-shadow(0 0 4px var(--c-accent-500))' : undefined,
        }}
      />
      {/* Visible reconnect endpoints при selected - круги на source и
          target points. Пользователь видит «вот за это можно тянуть»
          для смены handle. Реальная reconnect-логика - в onReconnect
          у ReactFlow root (handleReconnect в GraphCanvas). */}
      {selected && (
        <>
          <circle
            cx={sourceX}
            cy={sourceY}
            r={6}
            className="fill-elevated stroke-accent-600"
            strokeWidth={2}
          />
          <circle
            cx={targetX}
            cy={targetY}
            r={6}
            className="fill-elevated stroke-accent-600"
            strokeWidth={2}
          />
        </>
      )}
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
