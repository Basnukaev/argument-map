import dagre from 'dagre';
import type { NodeCardNode } from '@/components/graph/NodeCard';
import type { CustomEdgeEdge } from '@/components/graph/CustomEdge';

const NODE_WIDTH = 288;
const NODE_HEIGHT = 140;

/**
 * Считает позиции узлов через dagre.
 *
 * Параметр `direction` задаёт ориентацию: `LR` (слева направо, корень
 * слева - дефолт), `TB` (сверху вниз). Для argument-map дефолт LR -
 * корневой QUESTION слева, ответы и аргументы расходятся вправо.
 */
export function layoutGraph(
  nodes: NodeCardNode[],
  edges: CustomEdgeEdge[],
  direction: 'LR' | 'TB' = 'LR',
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
