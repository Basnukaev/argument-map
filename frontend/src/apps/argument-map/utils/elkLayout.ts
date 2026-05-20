import ELK, { type ElkNode, type ElkExtendedEdge } from 'elkjs/lib/elk.bundled.js';
import type { NodeCardNode } from '@/apps/argument-map/components/graph/NodeCard';
import type { CustomEdgeEdge } from '@/apps/argument-map/components/graph/CustomEdge';

/**
 * Размеры под NodeCard (см. `graphPlacement.ts`). ELK сам не знает
 * реальных размеров отрисованного DOM - даём ему те же дефолты что
 * dagre. Если в будущем перейдём на dynamic measure (через
 * `node.measured.width/height` из React Flow) - сюда прокинуть.
 */
const DEFAULT_NODE_WIDTH = 288;
const DEFAULT_NODE_HEIGHT = 140;

/**
 * Single ELK instance. Bundled-вариант (без Web Worker) выбран ради
 * простоты: для типичных графов (<200 узлов) layout считается
 * мгновенно на main thread. Worker-вариант полезен только при
 * визуально заметной задержке - в этот момент переключиться на
 * `elkjs/lib/elk-api.js` + workerUrl
 */
const elk = new ELK();

export type ElkAlgorithm = 'layered' | 'mrtree' | 'force' | 'stress';
export type ElkDirection = 'DOWN' | 'UP' | 'LEFT' | 'RIGHT';

export interface ElkLayoutOptions {
  algorithm?: ElkAlgorithm;
  direction?: ElkDirection;
  /** Расстояние между узлами (одного уровня) */
  nodeSpacing?: number;
  /** Расстояние между уровнями (только для `layered`) */
  layerSpacing?: number;
}

/**
 * Считает layout графа через ELK. Возвращает узлы с новыми позициями
 * (top-left, как ожидает React Flow); рёбра возвращаются неизменно -
 * мы используем ELK только ради позиций узлов, чтобы edges с
 * 4-handles и кастомным CustomEdge работали как раньше.
 *
 * Async: ELK API всегда промисный, даже в bundled-варианте без worker
 *
 * @param algorithm - `layered` (по умолчанию) аналогичен dagre, но
 *   лучше route'ит edges; `mrtree` - для древовидных; `force` - для
 *   связных кластеров; `stress` - для семантически близких групп
 */
export async function applyElkLayout(
  nodes: NodeCardNode[],
  edges: CustomEdgeEdge[],
  options: ElkLayoutOptions = {},
): Promise<{ nodes: NodeCardNode[]; edges: CustomEdgeEdge[] }> {
  if (nodes.length === 0) return { nodes: [], edges };

  const algorithm = options.algorithm ?? 'layered';
  const direction = options.direction ?? 'RIGHT';
  const nodeSpacing = options.nodeSpacing ?? 80;
  const layerSpacing = options.layerSpacing ?? 120;

  // Граф для ELK: id + width/height обязательны для children
  const elkGraph: ElkNode = {
    id: 'root',
    layoutOptions: {
      'elk.algorithm': algorithm,
      'elk.direction': direction,
      // SPLINE = плавные кривые между узлами без угловых изломов -
      // визуально приятнее и лучше разводит пучки рёбер при high-degree
      'elk.edgeRouting': 'SPLINE',
      'elk.spacing.nodeNode': String(nodeSpacing),
      'elk.layered.spacing.nodeNodeBetweenLayers': String(layerSpacing),
      // Hierarchical algorithm для layered - уменьшает crossings
      'elk.layered.crossingMinimization.semiInteractive': 'true',
    },
    children: nodes.map(
      (n): ElkNode => ({
        id: n.id,
        width: DEFAULT_NODE_WIDTH,
        height: DEFAULT_NODE_HEIGHT,
      }),
    ),
    edges: edges.map(
      (e): ElkExtendedEdge => ({
        id: e.id,
        sources: [e.source],
        targets: [e.target],
      }),
    ),
  };

  const result = await elk.layout(elkGraph);
  const positionById = new Map<string, { x: number; y: number }>();
  for (const child of result.children ?? []) {
    positionById.set(child.id, { x: child.x ?? 0, y: child.y ?? 0 });
  }

  return {
    nodes: nodes.map((n) => {
      const pos = positionById.get(n.id);
      // защитный fallback: если ELK по какой-то причине не вернул
      // координат - оставляем существующие, чтобы узел не прыгнул в (0,0)
      return pos ? { ...n, position: pos } : n;
    }),
    edges,
  };
}
