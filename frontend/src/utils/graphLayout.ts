import dagre from 'dagre';
import type { NodeCardNode } from '@/components/graph/NodeCard';
import type { CustomEdgeEdge } from '@/components/graph/CustomEdge';

const NODE_WIDTH = 288;
const NODE_HEIGHT = 140;

/**
 * Считает позиции узлов:
 * - если у ВСЕХ узлов есть сохранённые `posX`/`posY` (data.posX/data.posY),
 *   используем их as-is - это означает что пользователь уже расставлял
 *   узлы вручную и фронт должен уважать его layout
 * - если хотя бы один узел без позиций (новый, ещё не drag'нут) - dagre
 *   считает все позиции с нуля. Direction `LR` - корень слева, цепочки
 *   вправо. Параметром можно сменить на `TB`
 *
 * Логика "all or nothing" компромисс: при добавлении нового узла dagre
 * пересчитает весь граф, что собьёт ручные позиции. Альтернатива -
 * комбинированный layout (сохранённые + dagre для новых) - сложнее и
 * пока не нужна. После создания узла фронт обычно сразу drag'ет его в
 * нужное место, что записывает позиции для всех.
 */
export function layoutGraph(
  nodes: NodeCardNode[],
  edges: CustomEdgeEdge[],
  direction: 'LR' | 'TB' = 'LR',
): NodeCardNode[] {
  const allHaveSaved = nodes.length > 0 && nodes.every(
    (n) => typeof n.data.posX === 'number' && typeof n.data.posY === 'number',
  );

  if (allHaveSaved) {
    return nodes.map((node) => ({
      ...node,
      position: { x: node.data.posX as number, y: node.data.posY as number },
    }));
  }

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
