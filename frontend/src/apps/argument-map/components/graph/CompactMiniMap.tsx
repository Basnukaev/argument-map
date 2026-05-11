import { useState, useRef, useCallback } from 'react';
import { useNodes, useEdges, useStore, useReactFlow } from '@xyflow/react';
import type { Node, Edge } from '@xyflow/react';
import { Maximize2, Minimize2 } from 'lucide-react';
import type { NodeType, EdgeType } from '@/apps/argument-map/utils/edgeRules';
import type { components } from '@/shared/api/types';

type NodeDto = components['schemas']['NodeResponse'];

// fill узла на minimap - по типу. Цветной "квадратик типа" даёт
// читаемую палитру: фиолет=Вопрос, синий=Тезис, янтарь=Довод, зелёный=Свид.
// Совпадает с шапкой NodeCard (см. AddNodeModal radio-list)
const TYPE_FILL: Record<NodeType, string> = {
  QUESTION: '#c4b5fd',
  CLAIM: '#93c5fd',
  ARGUMENT: '#fcd34d',
  EVIDENCE: '#6ee7b7',
};

// stroke узла на minimap - по статусу. Серый для UNVERIFIED не отвлекает,
// цвет загорается когда статус известен
const STATUS_STROKE: Record<NonNullable<NodeDto['status']>, string> = {
  STANDING: '#16a34a',
  DISPUTED: '#d97706',
  REFUTED: '#dc2626',
  UNVERIFIED: '#6b7280',
};

// hex рёбер - совпадают со stroke в CustomEdge
const EDGE_HEX: Record<EdgeType, string> = {
  SUPPORTS: '#22c55e',
  REFUTES: '#ef4444',
  INVALIDATES: '#b91c1c',
  QUALIFIES: '#3b82f6',
  RESPONDS_TO: '#9ca3af',
};

const PAD = 60;
// Размеры контейнера. Compact - достаточный чтобы видеть структуру,
// expanded - в 2x для рассмотрения деталей
const COMPACT_W = 240;
const COMPACT_H = 170;
const EXPANDED_W = 480;
const EXPANDED_H = 340;

import { getBoundingBox, expandBounds, type BBox } from '@/apps/argument-map/utils/graphBounds';

/**
 * Кастомная мини-карта: показывает узлы (как уменьшенные NodeCard через
 * SVG rect+иконку), рёбра как цветные линии, viewport-rectangle (где
 * сейчас камера) с синей обводкой. Click на minimap центрирует камеру
 * там же. Toggle развернуть/свернуть для большего размера
 */
function CompactMiniMap() {
  const nodes = useNodes();
  const edges = useEdges();
  // RF Transform = [tx, ty, zoom] (tuple, не объект)
  const [tx, ty, zoom] = useStore((s) => s.transform);
  const canvasW = useStore((s) => s.width);
  const canvasH = useStore((s) => s.height);
  const { setViewport } = useReactFlow();

  const [expanded, setExpanded] = useState(false);
  const svgRef = useRef<SVGSVGElement | null>(null);

  const W = expanded ? EXPANDED_W : COMPACT_W;
  const H = expanded ? EXPANDED_H : COMPACT_H;

  const bbox = getBoundingBox(nodes);

  // viewport-rectangle в координатах canvas (тех же что и узлы):
  // top-left = inverse-transform экранного (0,0)
  // size = размер canvas-вьюпорта в canvas-координатах
  const viewport = {
    x: -tx / zoom,
    y: -ty / zoom,
    w: canvasW / zoom,
    h: canvasH / zoom,
  };

  // итоговый viewBox - объединение bbox узлов и текущего viewport,
  // чтобы оба влезли с отступом
  const viewportBox: BBox = {
    minX: viewport.x,
    minY: viewport.y,
    maxX: viewport.x + viewport.w,
    maxY: viewport.y + viewport.h,
  };
  const view: BBox = bbox
    ? expandBounds(bbox, viewportBox, PAD)
    : { minX: 0, minY: 0, maxX: canvasW, maxY: canvasH };
  const viewW = view.maxX - view.minX;
  const viewH = view.maxY - view.minY;

  // карта позиций для отрисовки рёбер (центры узлов)
  const centers = new Map<string, { cx: number; cy: number }>();
  for (const n of nodes) {
    const w = n.measured?.width ?? n.width ?? 288;
    const h = n.measured?.height ?? n.height ?? 120;
    centers.set(n.id, {
      cx: n.position.x + w / 2,
      cy: n.position.y + h / 2,
    });
  }

  // click на minimap → переместить камеру так чтобы клик стал центром.
  // вычисляем точку в canvas-координатах через relative click + viewBox scale
  const handleClick = useCallback(
    (event: React.MouseEvent<SVGSVGElement>) => {
      const svg = svgRef.current;
      if (!svg) return;
      const rect = svg.getBoundingClientRect();
      const clickX = event.clientX - rect.left;
      const clickY = event.clientY - rect.top;
      // viewBox занимает полный svg, scale одинаков по обеим осям
      const scaleX = viewW / rect.width;
      const scaleY = viewH / rect.height;
      const flowX = view.minX + clickX * scaleX;
      const flowY = view.minY + clickY * scaleY;
      // центрируем canvas-вьюпорт на (flowX, flowY): tx = -flowX*zoom + canvasW/2
      setViewport({
        x: -flowX * zoom + canvasW / 2,
        y: -flowY * zoom + canvasH / 2,
        zoom,
      });
    },
    [viewW, viewH, view.minX, view.minY, zoom, canvasW, canvasH, setViewport],
  );

  return (
    <div
      className="absolute right-3 bottom-3 z-10 overflow-hidden rounded-md border border-slate-200 bg-white shadow-md"
      style={{ width: W }}
    >
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          setExpanded((v) => !v);
        }}
        aria-label={expanded ? 'Свернуть мини-карту' : 'Развернуть мини-карту'}
        className="absolute right-1 top-1 z-10 rounded bg-white/90 p-1 text-gray-500 shadow-sm hover:bg-white hover:text-gray-700"
      >
        {expanded ? <Minimize2 size={14} /> : <Maximize2 size={14} />}
      </button>
      <svg
        ref={svgRef}
        width={W}
        height={H}
        viewBox={`${view.minX} ${view.minY} ${viewW} ${viewH}`}
        preserveAspectRatio="xMidYMid meet"
        onClick={handleClick}
        className="block cursor-pointer bg-gray-50"
        aria-label="Мини-карта графа"
      >
        {/* рёбра под узлами */}
        {edges.map((e: Edge) => {
          const a = centers.get(e.source);
          const b = centers.get(e.target);
          if (!a || !b) return null;
          const edgeType = ((e.data as { edgeType?: EdgeType } | undefined)?.edgeType) ?? 'SUPPORTS';
          return (
            <line
              key={e.id}
              x1={a.cx}
              y1={a.cy}
              x2={b.cx}
              y2={b.cy}
              stroke={EDGE_HEX[edgeType]}
              strokeWidth={2}
              opacity={0.85}
              vectorEffect="non-scaling-stroke"
            />
          );
        })}

        {/* узлы как простые заполненные rect - цвет по типу,
            обводка по статусу. Без текста: на масштабах compact он
            нечитаем, в expanded - всё равно мелковат. Тип читается через цвет */}
        {nodes.map((n: Node) => {
          const data = n.data as NodeDto | undefined;
          const nodeType: NodeType = data?.nodeType ?? 'CLAIM';
          const status = data?.status ?? 'UNVERIFIED';
          const w = n.measured?.width ?? n.width ?? 288;
          const h = n.measured?.height ?? n.height ?? 120;
          return (
            <rect
              key={n.id}
              x={n.position.x}
              y={n.position.y}
              width={w}
              height={h}
              rx={8}
              fill={TYPE_FILL[nodeType]}
              stroke={STATUS_STROKE[status]}
              strokeWidth={status === 'UNVERIFIED' ? 1 : 3}
              vectorEffect="non-scaling-stroke"
            />
          );
        })}

        {/* viewport rectangle - где сейчас камера */}
        <rect
          x={viewport.x}
          y={viewport.y}
          width={viewport.w}
          height={viewport.h}
          fill="rgba(59,130,246,0.10)"
          stroke="#3b82f6"
          strokeWidth={2}
          strokeDasharray="6 4"
          vectorEffect="non-scaling-stroke"
          pointerEvents="none"
        />
      </svg>
    </div>
  );
}

export default CompactMiniMap;
