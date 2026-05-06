import { useState, useRef, useCallback } from 'react';
import { useNodes, useEdges, useStore, useReactFlow } from '@xyflow/react';
import type { Node, Edge } from '@xyflow/react';
import { Maximize2, Minimize2 } from 'lucide-react';
import { NODE_TYPE_META, type NodeType, type EdgeType } from '@/utils/edgeRules';
import type { components } from '@/api/types';

type NodeDto = components['schemas']['NodeResponse'];
type NodeStatus = NonNullable<NodeDto['status']>;

const STATUS_FILL: Record<NodeStatus, string> = {
  STANDING: '#dcfce7',
  DISPUTED: '#fef3c7',
  REFUTED: '#fee2e2',
  UNVERIFIED: '#f9fafb',
};

const STATUS_STROKE: Record<NodeStatus, string> = {
  STANDING: '#16a34a',
  DISPUTED: '#d97706',
  REFUTED: '#dc2626',
  UNVERIFIED: '#9ca3af',
};

const TYPE_FILL_HEAD: Record<NodeType, string> = {
  QUESTION: '#a78bfa',
  CLAIM: '#3b82f6',
  ARGUMENT: '#f59e0b',
  EVIDENCE: '#10b981',
};

// hex для рёбер - совпадают с EDGE_HEX в CustomEdge
const EDGE_HEX: Record<EdgeType, string> = {
  SUPPORTS: '#22c55e',
  REFUTES: '#ef4444',
  INVALIDATES: '#b91c1c',
  QUALIFIES: '#3b82f6',
  RESPONDS_TO: '#9ca3af',
};

const PAD = 16;
// размеры виджета: компактный и развёрнутый. Высота auto чтобы сохранить
// пропорции графа без растяжения
const COMPACT_W = 280;
const COMPACT_H = 200;
const EXPANDED_W = 520;
const EXPANDED_H = 380;

interface BBox {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}

function getBoundingBox(nodes: Node[]): BBox | null {
  if (nodes.length === 0) return null;
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  for (const n of nodes) {
    const w = n.measured?.width ?? n.width ?? 288;
    const h = n.measured?.height ?? n.height ?? 120;
    if (n.position.x < minX) minX = n.position.x;
    if (n.position.y < minY) minY = n.position.y;
    if (n.position.x + w > maxX) maxX = n.position.x + w;
    if (n.position.y + h > maxY) maxY = n.position.y + h;
  }
  return { minX, minY, maxX, maxY };
}

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
  let view: BBox;
  if (bbox) {
    view = {
      minX: Math.min(bbox.minX, viewport.x) - PAD,
      minY: Math.min(bbox.minY, viewport.y) - PAD,
      maxX: Math.max(bbox.maxX, viewport.x + viewport.w) + PAD,
      maxY: Math.max(bbox.maxY, viewport.y + viewport.h) + PAD,
    };
  } else {
    view = { minX: 0, minY: 0, maxX: canvasW, maxY: canvasH };
  }
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
      className="absolute right-3 top-3 z-10 rounded-md border border-gray-300 bg-white shadow-md"
      style={{ width: W }}
    >
      <div className="flex items-center justify-between border-b border-gray-200 px-2 py-1">
        <span className="text-xs font-medium text-gray-500">Мини-карта</span>
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          aria-label={expanded ? 'Свернуть мини-карту' : 'Развернуть мини-карту'}
          className="rounded p-0.5 text-gray-500 hover:bg-gray-100 hover:text-gray-700"
        >
          {expanded ? <Minimize2 size={14} /> : <Maximize2 size={14} />}
        </button>
      </div>
      <svg
        ref={svgRef}
        width={W}
        height={H}
        viewBox={`${view.minX} ${view.minY} ${viewW} ${viewH}`}
        preserveAspectRatio="xMidYMid meet"
        onClick={handleClick}
        className="block cursor-pointer"
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
              strokeWidth={Math.max(2, viewW / W * 1.5)}
              opacity={0.75}
              vectorEffect="non-scaling-stroke"
            />
          );
        })}

        {/* узлы */}
        {nodes.map((n: Node) => {
          const data = n.data as NodeDto | undefined;
          const nodeType: NodeType = data?.nodeType ?? 'CLAIM';
          const status: NodeStatus = data?.status ?? 'UNVERIFIED';
          const w = n.measured?.width ?? n.width ?? 288;
          const h = n.measured?.height ?? n.height ?? 120;
          const headH = 26;
          return (
            <g key={n.id}>
              <rect
                x={n.position.x}
                y={n.position.y}
                width={w}
                height={h}
                rx={6}
                fill={STATUS_FILL[status]}
                stroke={STATUS_STROKE[status]}
                strokeWidth={2}
                vectorEffect="non-scaling-stroke"
              />
              {/* "шапка" узла - цвет по типу */}
              <rect
                x={n.position.x}
                y={n.position.y}
                width={w}
                height={headH}
                rx={6}
                fill={TYPE_FILL_HEAD[nodeType]}
                opacity={0.25}
              />
              <text
                x={n.position.x + 8}
                y={n.position.y + headH - 8}
                fontSize={Math.max(11, viewW / W * 11)}
                fill="#1f2937"
                fontWeight={600}
                style={{ userSelect: 'none' }}
              >
                {NODE_TYPE_META[nodeType].label}
              </text>
            </g>
          );
        })}

        {/* viewport rectangle - где сейчас камера */}
        <rect
          x={viewport.x}
          y={viewport.y}
          width={viewport.w}
          height={viewport.h}
          fill="rgba(59,130,246,0.08)"
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
