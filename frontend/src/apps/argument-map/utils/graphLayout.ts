import dagre from 'dagre';
import type { NodeCardNode } from '@/apps/argument-map/components/graph/NodeCard';
import type { CustomEdgeEdge } from '@/apps/argument-map/components/graph/CustomEdge';
import type { LayoutAlgorithm } from '@/shared/stores/layoutAlgorithmStore';

const NODE_WIDTH = 288;
const NODE_HEIGHT = 140;
const FRESH_GAP_X = 400;
const FRESH_GAP_Y = 80;

/**
 * Считает позиции узлов с учётом сохранённых на беке `posX`/`posY`:
 *
 * - Все узлы имеют `posX`/`posY` → используем их as-is. Это типичный
 *   случай при перезагрузке графа после ручных drag'ов или backfill
 * - Ни у одного нет → dagre считает все позиции с нуля. Свежий граф
 *   до первого drag'а
 * - Смешанно (часть имеет, часть нет) → сохранённые узлы стоят на
 *   своих координатах, для остальных:
 *   1. если узел уже был в `previousNodes` (с предыдущего рендера) -
 *      берём ту позицию, чтобы при refetch узел не прыгнул;
 *   2. иначе кладём в столбец справа от saved-кластера. Это запасной
 *      путь - в норме backfill уже проставил все posX/posY на бэке
 *
 * Direction `LR` (корень слева, цепочки вправо) применяется только
 * в режиме "ни у одного нет".
 */
export function layoutGraph(
  nodes: NodeCardNode[],
  edges: CustomEdgeEdge[],
  direction: 'LR' | 'TB' = 'LR',
  previousNodes: ReadonlyArray<NodeCardNode> = [],
): NodeCardNode[] {
  if (nodes.length === 0) return [];

  const hasSaved = (n: NodeCardNode) =>
    typeof n.data.posX === 'number' && typeof n.data.posY === 'number';

  const allSaved = nodes.every(hasSaved);
  const noneSaved = nodes.every((n) => !hasSaved(n));

  if (allSaved) {
    return nodes.map((node) => ({
      ...node,
      position: { x: node.data.posX as number, y: node.data.posY as number },
    }));
  }

  if (noneSaved) {
    return dagreLayout(nodes, edges, direction);
  }

  // смешанный: сохранённые as-is, fresh узлы - на их предыдущих позициях
  // если они там были (refetch-сценарий), либо столбцом справа
  const previousPos = new Map<string, { x: number; y: number }>();
  for (const p of previousNodes) {
    previousPos.set(p.id, { x: p.position.x, y: p.position.y });
  }

  const savedNodes = nodes.filter(hasSaved);
  const maxX = savedNodes.length > 0
    ? Math.max(...savedNodes.map((n) => n.data.posX as number))
    : 0;
  const minY = savedNodes.length > 0
    ? Math.min(...savedNodes.map((n) => n.data.posY as number))
    : 0;
  const freshUnknownIds = nodes
    .filter((n) => !hasSaved(n) && !previousPos.has(n.id))
    .map((n) => n.id);

  return nodes.map((node) => {
    if (hasSaved(node)) {
      return {
        ...node,
        position: { x: node.data.posX as number, y: node.data.posY as number },
      };
    }
    const prev = previousPos.get(node.id);
    if (prev) {
      // узел уже был на канвасе - сохраняем его позицию между refetch'ами
      return { ...node, position: prev };
    }
    // совершенно новый узел без сохранённых координат и без previous-position -
    // ставим в столбец справа. На практике этот путь почти не используется
    // благодаря backfill posX/posY на TopicGraphPage
    const idx = freshUnknownIds.indexOf(node.id);
    return {
      ...node,
      position: {
        x: maxX + FRESH_GAP_X,
        y: minY + idx * (NODE_HEIGHT + FRESH_GAP_Y),
      },
    };
  });
}

/**
 * Async-вариант с переключателем алгоритма. Для `dagre` - синхронно
 * (см. `layoutGraph`) обёрнуто в Promise. Для `elk` - lazy-import
 * (bundle splitting: elkjs ~200KB не попадает в initial chunk) и
 * перезаписывает позиции узлов через ORTHOGONAL edge routing.
 *
 * `previousNodes` имеет смысл только для `dagre` mixed-режима;
 * `elk` пересчитывает весь граф целиком
 */
export async function applyLayout(
  nodes: NodeCardNode[],
  edges: CustomEdgeEdge[],
  algorithm: LayoutAlgorithm = 'dagre',
  direction: 'LR' | 'TB' = 'LR',
  previousNodes: ReadonlyArray<NodeCardNode> = [],
): Promise<NodeCardNode[]> {
  if (nodes.length === 0) return [];
  if (algorithm === 'elk') {
    // Lazy import - elkjs ~200KB gzipped; не нагружаем initial bundle
    // для пользователей которые остаются на dagre (default)
    const { applyElkLayout } = await import('./elkLayout');
    const { nodes: laidOut } = await applyElkLayout(nodes, edges, {
      // RIGHT для LR (наша default direction для tree-of-thought),
      // DOWN для TB
      direction: direction === 'LR' ? 'RIGHT' : 'DOWN',
    });
    return laidOut;
  }
  return layoutGraph(nodes, edges, direction, previousNodes);
}

function dagreLayout(
  nodes: NodeCardNode[],
  edges: CustomEdgeEdge[],
  direction: 'LR' | 'TB',
): NodeCardNode[] {
  const g = new dagre.graphlib.Graph();
  g.setDefaultEdgeLabel(() => ({}));
  g.setGraph({ rankdir: direction, nodesep: 60, ranksep: 120 });

  nodes.forEach((node) => {
    g.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT });
  });
  edges.forEach((edge) => {
    g.setEdge(edge.source, edge.target);
  });

  dagre.layout(g);

  return nodes.map((node) => {
    const pos = g.node(node.id);
    return {
      ...node,
      // dagre отдаёт центр - React Flow ждёт верхний левый угол
      position: { x: pos.x - NODE_WIDTH / 2, y: pos.y - NODE_HEIGHT / 2 },
    };
  });
}
