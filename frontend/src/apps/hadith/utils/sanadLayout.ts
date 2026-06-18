import dagre from 'dagre';
import type { Edge, Node } from '@xyflow/react';

// Размеры карточки узла должны совпадать с SanadGraphNode (w-[240px]).
// Экспортируются: PNG-экспорт (SanadGraph.handleExportPng) подставляет их в
// getNodesBounds, когда measured-размеры узлов ещё пусты (иначе ширина графа
// схлопывается к 0 и экспорт кропает карточки).
export const NODE_WIDTH = 240;
export const NODE_HEIGHT = 108;

/**
 * Раскладка графа иснада сверху вниз (TB): Пророк ﷺ сверху, составители
 * сборников снизу. Это семантическое направление цепи передачи (в
 * отличие от LR в графе аргументации). Чистый dagre — позиции иснада не
 * сохраняются и не редактируются пользователем (граф read-only), поэтому
 * persisted-positions логика argument-map'а здесь не нужна.
 */
export function layoutSanad<N extends Node, E extends Edge>(
  nodes: N[],
  edges: E[],
): N[] {
  if (nodes.length === 0) return [];

  const g = new dagre.graphlib.Graph();
  g.setDefaultEdgeLabel(() => ({}));
  g.setGraph({ rankdir: 'TB', nodesep: 56, ranksep: 84, marginx: 24, marginy: 24 });

  nodes.forEach((node) => {
    g.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT });
  });
  edges.forEach((edge) => {
    g.setEdge(edge.source, edge.target);
  });

  dagre.layout(g);

  return nodes.map((node) => {
    const pos = g.node(node.id);
    // dagre отдаёт центр узла, React Flow ждёт верхний левый угол.
    return {
      ...node,
      position: { x: pos.x - NODE_WIDTH / 2, y: pos.y - NODE_HEIGHT / 2 },
    };
  });
}
