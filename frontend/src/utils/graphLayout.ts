import dagre from 'dagre';
import type { NodeCardNode } from '@/components/graph/NodeCard';
import type { CustomEdgeEdge } from '@/components/graph/CustomEdge';

const NODE_WIDTH = 288;
const NODE_HEIGHT = 140;
const FRESH_GAP_X = 400;
const FRESH_GAP_Y = 80;

/**
 * Считает позиции узлов с учётом сохранённых на беке `posX`/`posY`:
 *
 * - Все узлы имеют `posX`/`posY` → используем их as-is. Это типичный
 *   случай при перезагрузке графа после ручных drag'ов
 * - Ни у одного нет → dagre считает все позиции с нуля. Свежий граф
 *   до первого drag'а
 * - Смешанно (часть имеет, часть нет) → сохранённые узлы стоят на
 *   своих координатах, новые расставляются столбцом справа от
 *   существующего layout. Не красиво, но не теряет ручные позиции.
 *   Пользователь после создания нового узла сразу drag'ет его в
 *   нужное место и фиксирует все
 *
 * Direction `LR` (корень слева, цепочки вправо) применяется только
 * в режиме "ни у одного нет".
 */
export function layoutGraph(
  nodes: NodeCardNode[],
  edges: CustomEdgeEdge[],
  direction: 'LR' | 'TB' = 'LR',
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

  // смешанный: сохранённые as-is + столбец справа для новых
  const savedNodes = nodes.filter(hasSaved);
  const maxX = Math.max(...savedNodes.map((n) => n.data.posX as number));
  const minY = Math.min(...savedNodes.map((n) => n.data.posY as number));
  const freshIds = nodes
    .filter((n) => !hasSaved(n))
    .map((n) => n.id);

  return nodes.map((node) => {
    if (hasSaved(node)) {
      return {
        ...node,
        position: { x: node.data.posX as number, y: node.data.posY as number },
      };
    }
    const idx = freshIds.indexOf(node.id);
    return {
      ...node,
      position: {
        x: maxX + FRESH_GAP_X,
        y: minY + idx * (NODE_HEIGHT + FRESH_GAP_Y),
      },
    };
  });
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
