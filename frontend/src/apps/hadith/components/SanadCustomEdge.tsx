import { memo } from 'react';
import {
  BaseEdge,
  EdgeLabelRenderer,
  getSmoothStepPath,
  type Edge,
  type EdgeProps,
} from '@xyflow/react';
import { pickLabelPosition, type Point } from '@/apps/argument-map/utils/orthogonalPath';

/** Прямой ортогональный path (90°-углы без скругления) по точкам ELK. */
function buildSharpOrthogonalPath(points: ReadonlyArray<Point>): string {
  if (points.length < 2) return '';
  const [first, ...rest] = points;
  return `M ${first!.x} ${first!.y} ` + rest.map((p) => `L ${p.x} ${p.y}`).join(' ');
}

export type SanadEdgeData = {
  /** Арабская формула передачи (حدثنا / عن / أخبرنا …) — подпись на ребре. */
  transmissionPhrase?: string;
  /**
   * ПОЛНАЯ ортогональная полилиния из ELK (startPoint→bends→endPoint, в flow-
   * координатах). Заполнена после async-раскладки; до неё undefined → fallback
   * на getSmoothStepPath. Рисуем строго по ней (не склеиваем с RF-хэндлом) —
   * иначе веер из узла даёт диагонали (С64).
   */
  points?: Array<{ x: number; y: number }>;
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

  const points = data?.points;
  const phrase = data?.transmissionPhrase;
  const dimmed = data?.dimmed ?? false;

  let edgePath: string;
  let labelX: number;
  let labelY: number;

  if (points && points.length >= 2) {
    // Прямые 90°-углы строго по ELK-полилинии (порты на границах карточек).
    edgePath = buildSharpOrthogonalPath(points);
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
      borderRadius: 0,
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
