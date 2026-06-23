import { memo } from 'react';
import {
  BaseEdge,
  EdgeLabelRenderer,
  getSmoothStepPath,
  type Edge,
  type EdgeProps,
} from '@xyflow/react';
import {
  buildRoundedOrthogonalPath,
  pickLabelPosition,
} from '@/apps/argument-map/utils/orthogonalPath';

export type SanadEdgeData = {
  /** Арабская формула передачи (حدثنا / عن / أخبرنا …) — подпись на ребре. */
  transmissionPhrase?: string;
  /**
   * Изломы ортогонального пути из ELK (sanadElkLayout). Заполнены после
   * async-раскладки; до неё undefined → fallback на getSmoothStepPath
   * (геометрическая аппроксимация, как было на dagre).
   */
  bendPoints?: Array<{ x: number; y: number }>;
  /** Подсветка цепи по клику: приглушить чужие подписи (линия — через style.opacity). */
  dimmed?: boolean;
};

export type SanadCustomEdgeType = Edge<SanadEdgeData, 'sanad'>;

/**
 * Ребро графа иснада с ортогональной маршрутизацией по ELK bend-points.
 * Рисует path строго по изломам, которые ELK проложил ОГИБАЯ карточки
 * (Проблема 1 Абдулы), подпись-формулу ставит на середину самого длинного
 * сегмента — гарантированно не на узле (Проблема 3).
 *
 * Линия (stroke/strokeWidth/opacity) приходит через `style` — там же живёт
 * подсветка цепи по клику (SanadGraph highlight useMemo). Подпись приглушается
 * отдельно через `data.dimmed`, т.к. рендерится не в SVG, а в EdgeLabelRenderer.
 */
function SanadEdge(props: EdgeProps<SanadCustomEdgeType>) {
  const {
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    data,
    style,
    markerEnd,
  } = props;

  const bendPoints = data?.bendPoints;
  const phrase = data?.transmissionPhrase;
  const dimmed = data?.dimmed ?? false;

  let edgePath: string;
  let labelX: number;
  let labelY: number;

  if (bendPoints && bendPoints.length > 0) {
    const points = [
      { x: sourceX, y: sourceY },
      ...bendPoints,
      { x: targetX, y: targetY },
    ];
    edgePath = buildRoundedOrthogonalPath(points, 10);
    const pos = pickLabelPosition(points);
    labelX = pos.x;
    labelY = pos.y;
  } else {
    [edgePath, labelX, labelY] = getSmoothStepPath({
      sourceX,
      sourceY,
      sourcePosition,
      targetX,
      targetY,
      targetPosition,
      borderRadius: 10,
    });
  }

  return (
    <>
      <BaseEdge path={edgePath} markerEnd={markerEnd} style={style} interactionWidth={24} />
      {phrase && (
        <EdgeLabelRenderer>
          <div
            dir="rtl"
            className="pointer-events-none absolute rounded-[4px] border border-border-strong bg-elevated px-1 font-arabic text-[13px] font-semibold leading-tight text-ink-700 shadow-sh1"
            style={{
              transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
              opacity: dimmed ? 0.12 : 1,
            }}
          >
            {phrase}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
}

export default memo(SanadEdge);
