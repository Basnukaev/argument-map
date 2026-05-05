import { useNodes, useEdges, Panel } from '@xyflow/react';
import type { Node, Edge } from '@xyflow/react';
import { NODE_TYPE_META, EDGE_TYPE_META, type NodeType, type EdgeType } from '@/utils/edgeRules';
import type { components } from '@/api/types';

type NodeDto = components['schemas']['NodeResponse'];

const VIEWBOX_W = 240;
const VIEWBOX_H = 180;
const PADDING = 8;

// Цвет fill узла по типу - контрастный, чтобы на minimap легко считывался домен.
// Разные типы → разные цвета. Статус показывается через stroke-color (см. STATUS_STROKE).
// При taillwind colour palette для соответствия NodeCard
const TYPE_FILL: Record<NodeType, string> = {
  QUESTION: '#a78bfa',  // violet-400
  CLAIM: '#3b82f6',     // blue-500
  ARGUMENT: '#f59e0b',  // amber-500
  EVIDENCE: '#10b981',  // emerald-500
};

// Stroke по статусу - контур поверх fill даёт информацию о двух осях сразу
const STATUS_STROKE: Record<NonNullable<NodeDto['status']>, string> = {
  STANDING: '#16a34a',   // green-600
  DISPUTED: '#d97706',   // amber-600
  REFUTED: '#dc2626',    // red-600
  UNVERIFIED: '#6b7280', // gray-500
};

interface BoundingBox {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}

function getBoundingBox(nodes: Node[]): BoundingBox | null {
  if (nodes.length === 0) return null;
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  for (const n of nodes) {
    const w = n.measured?.width ?? n.width ?? 200;
    const h = n.measured?.height ?? n.height ?? 80;
    if (n.position.x < minX) minX = n.position.x;
    if (n.position.y < minY) minY = n.position.y;
    if (n.position.x + w > maxX) maxX = n.position.x + w;
    if (n.position.y + h > maxY) maxY = n.position.y + h;
  }
  return { minX, minY, maxX, maxY };
}

/**
 * Кастомный mini-map с отрисовкой и узлов, и рёбер. RF MiniMap из коробки
 * рёбра не показывает - получается набор серых квадратиков без структуры.
 *
 * Не имеет viewport-rectangle и pan/zoom: для MVP важна именно навигационная
 * картинка структуры, а не живой viewport-tracker. Если позже понадобится -
 * можно вернуться к стандартному MiniMap либо расширить этот компонент
 * через useStore({ transform })
 */
function GraphMiniMap() {
  const nodes = useNodes();
  const edges = useEdges();
  const bbox = getBoundingBox(nodes);

  if (!bbox || nodes.length === 0) {
    return null;
  }

  const graphW = bbox.maxX - bbox.minX;
  const graphH = bbox.maxY - bbox.minY;
  if (graphW <= 0 || graphH <= 0) return null;

  // вписываем bounding-box в viewbox с одинаковым масштабом по обеим осям
  // (preserveAspectRatio в SVG это бы тоже сделал, но удобнее иметь явный scale)
  const scale = Math.min(
    (VIEWBOX_W - PADDING * 2) / graphW,
    (VIEWBOX_H - PADDING * 2) / graphH,
  );
  const offsetX = PADDING + ((VIEWBOX_W - PADDING * 2) - graphW * scale) / 2;
  const offsetY = PADDING + ((VIEWBOX_H - PADDING * 2) - graphH * scale) / 2;

  function project(x: number, y: number): [number, number] {
    return [offsetX + (x - bbox!.minX) * scale, offsetY + (y - bbox!.minY) * scale];
  }

  // карта позиций узлов для подсчёта центров (источник/конец ребра)
  const nodePositions = new Map<string, { cx: number; cy: number }>();
  for (const n of nodes) {
    const w = n.measured?.width ?? n.width ?? 200;
    const h = n.measured?.height ?? n.height ?? 80;
    const [x, y] = project(n.position.x + w / 2, n.position.y + h / 2);
    nodePositions.set(n.id, { cx: x, cy: y });
  }

  return (
    <Panel position="top-right" className="!m-3">
      <div className="rounded-md border border-gray-300 bg-white shadow-md">
        <svg
          width={VIEWBOX_W}
          height={VIEWBOX_H}
          viewBox={`0 0 ${VIEWBOX_W} ${VIEWBOX_H}`}
          className="block"
          aria-label="Мини-карта графа"
        >
          {/* рёбра рисуем первыми чтобы они были под узлами */}
          {edges.map((e: Edge) => {
            const a = nodePositions.get(e.source);
            const b = nodePositions.get(e.target);
            if (!a || !b) return null;
            const edgeType = ((e.data as { edgeType?: EdgeType } | undefined)?.edgeType) ?? 'SUPPORTS';
            const color = EDGE_TYPE_META[edgeType]?.colorClass
              ? edgeColorFromMeta(edgeType)
              : '#9ca3af';
            return (
              <line
                key={e.id}
                x1={a.cx}
                y1={a.cy}
                x2={b.cx}
                y2={b.cy}
                stroke={color}
                strokeWidth={1.2}
                opacity={0.7}
              />
            );
          })}
          {nodes.map((n: Node) => {
            const data = n.data as NodeDto | undefined;
            const nodeType: NodeType = data?.nodeType ?? 'CLAIM';
            const status = data?.status ?? 'UNVERIFIED';
            const w = n.measured?.width ?? n.width ?? 200;
            const h = n.measured?.height ?? n.height ?? 80;
            const [x, y] = project(n.position.x, n.position.y);
            const sw = Math.max(2, w * scale);
            const sh = Math.max(2, h * scale);
            return (
              <rect
                key={n.id}
                x={x}
                y={y}
                width={sw}
                height={sh}
                rx={2}
                fill={TYPE_FILL[nodeType]}
                stroke={STATUS_STROKE[status]}
                strokeWidth={1}
              >
                <title>
                  {NODE_TYPE_META[nodeType].label}: {data?.content?.slice(0, 80) ?? ''}
                </title>
              </rect>
            );
          })}
        </svg>
      </div>
    </Panel>
  );
}

// Маппинг tailwind text-color → hex для SVG (SVG не понимает класс tailwind)
const EDGE_HEX: Record<EdgeType, string> = {
  SUPPORTS: '#16a34a',
  REFUTES: '#dc2626',
  INVALIDATES: '#991b1b',
  QUALIFIES: '#2563eb',
  RESPONDS_TO: '#9ca3af',
};

function edgeColorFromMeta(type: EdgeType): string {
  return EDGE_HEX[type];
}

export default GraphMiniMap;
