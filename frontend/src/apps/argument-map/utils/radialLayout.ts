import { hierarchy, tree, type HierarchyPointNode } from 'd3-hierarchy';
import type { NodeCardNode } from '@/apps/argument-map/components/graph/NodeCard';
import type { CustomEdgeEdge } from '@/apps/argument-map/components/graph/CustomEdge';

/**
 * Radial layout через d3-hierarchy с polar-coords интерпретацией.
 *
 * **Почему не ELK radial**: ELK radial оптимизирован для small
 * point-like nodes (ER-диаграммы). Для прямоугольных NodeCard
 * 288×140+ алгоритм не умеет разводить - нет collision detection
 * на углах прямоугольника при размещении на окружности. На наших
 * аргумент-картах ELK radial складывает узлы в кучу (наблюдалось
 * на screenshots Phase 1 + Phase 1.5).
 *
 * **Алгоритм**:
 * 1. Строим дерево от root (QUESTION без incoming или first node).
 *    Используем BFS чтобы избежать циклов - аргумент-граф формально
 *    DAG, но визуально мы рисуем его как tree-of-thought.
 * 2. d3-tree кладёт узлы в декартовых координатах [x, y], где x
 *    интерпретируется как **angle** в [0, 2π], а y - как **radius**
 *    (hop-distance × ringGap).
 * 3. Конвертируем polar → cartesian: `(cos(θ) · r, sin(θ) · r)`.
 * 4. Центрируем root в (0, 0) - React Flow ставит origin в верхний
 *    левый угол node'а, компенсируем половиной width/height.
 *
 * **Separation** function учитывает что на больших радиусах одного
 * и того же angular gap соответствует пропорционально больше
 * пространства - делим базовое separation на depth узла.
 */

const DEFAULT_NODE_WIDTH = 288;
const DEFAULT_NODE_HEIGHT = 140;
const RING_GAP_BASE = 360;
const RING_GAP_DENSE = 460;
const DENSE_RING_THRESHOLD = 7;

interface TreeNodeData {
  id: string;
  children: TreeNodeData[];
}

/**
 * BFS-обход графа от root, собирает дерево (один путь к каждому
 * узлу - игнорирует cross-edges чтобы не получить cycles). Узлы
 * не достижимые из root попадают в отдельный virtual-root flat list.
 */
function buildTreeFromGraph(
  rootId: string,
  edges: ReadonlyArray<CustomEdgeEdge>,
  allNodeIds: ReadonlyArray<string>,
): TreeNodeData {
  const adjacency = new Map<string, string[]>();
  for (const e of edges) {
    if (!adjacency.has(e.source)) adjacency.set(e.source, []);
    adjacency.get(e.source)!.push(e.target);
    // также добавляем reverse - radial tree должен ловить и
    // "родительские" связи (например ARGUMENT→CLAIM где CLAIM это
    // semantic parent ARGUMENT'а, но edge направлен от ребёнка
    // к родителю по семантике reasoning)
    if (!adjacency.has(e.target)) adjacency.set(e.target, []);
    adjacency.get(e.target)!.push(e.source);
  }

  const visited = new Set<string>();
  const queue: TreeNodeData[] = [];

  function buildNode(id: string): TreeNodeData {
    visited.add(id);
    const node: TreeNodeData = { id, children: [] };
    queue.push(node);
    return node;
  }

  const root = buildNode(rootId);
  let head = 0;
  while (head < queue.length) {
    const current = queue[head++];
    if (!current) continue;
    const neighbors = adjacency.get(current.id) ?? [];
    for (const n of neighbors) {
      if (!visited.has(n)) {
        current.children.push(buildNode(n));
      }
    }
  }

  // Disconnected узлы (orphans, не достижимы из root) - добавляем
  // как прямых детей root'а, чтобы они всё равно попали на canvas
  for (const id of allNodeIds) {
    if (!visited.has(id)) {
      root.children.push(buildNode(id));
    }
  }
  return root;
}

/**
 * Находит root узел для radial - QUESTION без incoming edges
 * (семантический корень reasoning), иначе первый QUESTION,
 * иначе первый узел.
 */
function findRoot(
  nodes: ReadonlyArray<NodeCardNode>,
  edges: ReadonlyArray<CustomEdgeEdge>,
): string {
  const incoming = new Set<string>();
  for (const e of edges) incoming.add(e.target);
  const questions = nodes.filter((n) => n.data?.nodeType === 'QUESTION');
  const noIn = questions.find((q) => !incoming.has(q.id));
  if (noIn) return noIn.id;
  if (questions[0]) return questions[0].id;
  return nodes[0]?.id ?? '';
}

function maxDepth(root: HierarchyPointNode<TreeNodeData>): number {
  let max = 0;
  root.each((n) => {
    if (n.depth > max) max = n.depth;
  });
  return max;
}

/**
 * Считает количество узлов в каждом radial-кольце (по depth) - чтобы
 * адаптивно увеличить ringGap для плотных колец.
 */
function countByDepth(root: HierarchyPointNode<TreeNodeData>): Map<number, number> {
  const counts = new Map<number, number>();
  root.each((n) => {
    counts.set(n.depth, (counts.get(n.depth) ?? 0) + 1);
  });
  return counts;
}

export function applyRadialLayout(
  nodes: ReadonlyArray<NodeCardNode>,
  edges: ReadonlyArray<CustomEdgeEdge>,
): NodeCardNode[] {
  if (nodes.length === 0) return [];
  if (nodes.length === 1) {
    // Один узел - в центре viewport. React Flow ставит origin
    // в верхний левый угол, компенсируем половиной размера
    const n = nodes[0];
    if (!n) return [];
    return [{ ...n, position: { x: -DEFAULT_NODE_WIDTH / 2, y: -DEFAULT_NODE_HEIGHT / 2 } }];
  }

  const rootId = findRoot(nodes, edges);
  if (!rootId) return [...nodes];

  const allIds = nodes.map((n) => n.id);
  const treeData = buildTreeFromGraph(rootId, edges, allIds);

  // Решаем нужен ли dense ringGap - если на любом уровне >7 узлов,
  // увеличиваем gap чтобы было место для угловых интервалов
  const tmpRoot = hierarchy(treeData);
  const tmpLayout = tree<TreeNodeData>().size([2 * Math.PI, 1])(tmpRoot);
  const depthCounts = countByDepth(tmpLayout);
  const maxPerRing = Math.max(...depthCounts.values());
  const ringGap = maxPerRing > DENSE_RING_THRESHOLD ? RING_GAP_DENSE : RING_GAP_BASE;

  const root = hierarchy(treeData);
  const layout = tree<TreeNodeData>()
    .size([2 * Math.PI, Math.max(maxDepth(tmpLayout) * ringGap, ringGap)])
    .separation((a, b) => {
      // Узлы общего родителя - база 1, разных родителей - больше
      // (чтобы между sub-tree'ями был визуальный gap). Делим на
      // depth - на больших радиусах меньший angular gap даёт ту
      // же визуальную дистанцию
      const base = a.parent === b.parent ? 1 : 2;
      return base / Math.max(a.depth, 1);
    });
  const laidOut = layout(root);

  const positionById = new Map<string, { x: number; y: number }>();
  laidOut.each((node) => {
    const angle = node.x;
    const radius = node.y;
    // -Math.PI/2 поворачивает 0° с восточной стороны (default d3)
    // на северную - root узел сверху смотрится естественнее
    positionById.set(node.data.id, {
      x: Math.cos(angle - Math.PI / 2) * radius,
      y: Math.sin(angle - Math.PI / 2) * radius,
    });
  });

  // Корень в (0,0), остальные относительно него. React Flow
  // ожидает top-left угол node'а - компенсируем размером.
  // Используем measured если доступен, иначе DEFAULT.
  return nodes.map((n) => {
    const pos = positionById.get(n.id);
    if (!pos) return n;
    const measured = (n as { measured?: { width?: number; height?: number } }).measured;
    const w = measured?.width ?? DEFAULT_NODE_WIDTH;
    const h = measured?.height ?? DEFAULT_NODE_HEIGHT;
    return {
      ...n,
      position: { x: pos.x - w / 2, y: pos.y - h / 2 },
    };
  });
}
