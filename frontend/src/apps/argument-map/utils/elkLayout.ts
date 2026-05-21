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

const elk = new ELK();

/**
 * Layout-preset выражает выбранную пользователем форму графа. Внутри
 * мапится в (algorithm, direction, spacing, edgeRouting, constraints).
 * См. ADR об argument-map layout: presets вместо raw algorithm choice -
 * UX не требует от пользователя знаний ELK/dagre.
 *
 * - `tree-tb`: канонический Sugiyama TOP→BOTTOM (как Kialo, Rationale).
 *   QUESTION сверху, EVIDENCE внизу через layerConstraint.
 * - `tree-lr`: тот же layered алгоритм, но LEFT→RIGHT для wide screens
 *   или длинных reasoning chains.
 * - `radial`: ELK radial - root в центре, слои как кольца. Удобно для
 *   20+ узлов и презентационных скриншотов.
 */
export type LayoutPreset = 'tree-tb' | 'tree-lr' | 'radial';

interface ElkPresetConfig {
  algorithm: 'layered' | 'radial';
  direction?: 'DOWN' | 'RIGHT';
  /** Per-preset layoutOptions для ELK (mergee к base) */
  options: Record<string, string>;
}

/**
 * Base options общие для layered presets - Sugiyama best-practice:
 * BRANDES_KOEPF placement (2001) даёт straight long-edges без ломки,
 * NETWORK_SIMPLEX layering минимизирует общую длину рёбер,
 * LAYER_SWEEP crossing minimization - стандарт для interactive UI.
 * ORTHOGONAL routing рисует edges под 90° (вместо bezier хаоса).
 */
const LAYERED_BASE: Record<string, string> = {
  'elk.algorithm': 'layered',
  'elk.edgeRouting': 'ORTHOGONAL',
  'elk.spacing.nodeNode': '80',
  'elk.layered.spacing.nodeNodeBetweenLayers': '120',
  'elk.layered.spacing.edgeNodeBetweenLayers': '40',
  'elk.layered.crossingMinimization.strategy': 'LAYER_SWEEP',
  'elk.layered.nodePlacement.strategy': 'BRANDES_KOEPF',
  'elk.layered.layering.strategy': 'NETWORK_SIMPLEX',
  'elk.layered.considerModelOrder.strategy': 'NODES_AND_EDGES',
};

const PRESET_CONFIG: Record<LayoutPreset, ElkPresetConfig> = {
  'tree-tb': {
    algorithm: 'layered',
    direction: 'DOWN',
    options: { ...LAYERED_BASE, 'elk.direction': 'DOWN' },
  },
  'tree-lr': {
    algorithm: 'layered',
    direction: 'RIGHT',
    options: { ...LAYERED_BASE, 'elk.direction': 'RIGHT' },
  },
  radial: {
    algorithm: 'radial',
    options: {
      'elk.algorithm': 'radial',
      'elk.spacing.nodeNode': '100',
      'elk.radial.compactor': 'RADIAL_COMPACTION',
    },
  },
};

/**
 * Semantic-aware layer constraints. Argument map имеет естественную
 * иерархию типов (Toulmin, Carneades), которую алгоритм сам по edges
 * не выводит. Явно прибиваем QUESTION к верхнему слою (FIRST_SEPARATE)
 * и EVIDENCE к нижнему (LAST_SEPARATE). CLAIM/ARGUMENT остаются
 * `NONE` - сами лягут между по topological rank.
 *
 * `FIRST_SEPARATE` (а не просто `FIRST`) гарантирует, что узлы этого
 * типа займут отдельный слой, даже если есть edge от такого узла к
 * другому того же типа.
 */
type NodeTypeLayerConstraint = 'FIRST_SEPARATE' | 'NONE' | 'LAST_SEPARATE';

const TYPE_LAYER_CONSTRAINT: Record<string, NodeTypeLayerConstraint> = {
  QUESTION: 'FIRST_SEPARATE',
  CLAIM: 'NONE',
  ARGUMENT: 'NONE',
  EVIDENCE: 'LAST_SEPARATE',
};

function layerConstraintFor(node: NodeCardNode): NodeTypeLayerConstraint {
  const nodeType = node.data?.nodeType ?? 'CLAIM';
  return TYPE_LAYER_CONSTRAINT[nodeType] ?? 'NONE';
}

/**
 * Применяет ELK-раскладку для выбранного preset'а. Возвращает узлы с
 * новыми позициями (top-left, как ожидает React Flow). Edges -
 * неизменно, layout считается только под позиции; React Flow + наш
 * CustomEdge сами роутят рёбра по handles.
 *
 * Для layered-preset'ов добавляется per-node `layerConstraint` по
 * семантическому типу (QUESTION top / EVIDENCE bottom). Для radial -
 * constraint игнорируется (layers концепции нет, узлы кладутся
 * концентрическими окружностями вокруг root).
 */
export async function applyElkLayout(
  nodes: NodeCardNode[],
  edges: CustomEdgeEdge[],
  preset: LayoutPreset = 'tree-tb',
): Promise<{ nodes: NodeCardNode[]; edges: CustomEdgeEdge[] }> {
  if (nodes.length === 0) return { nodes: [], edges };

  const config = PRESET_CONFIG[preset];
  const isLayered = config.algorithm === 'layered';

  const elkGraph: ElkNode = {
    id: 'root',
    layoutOptions: config.options,
    children: nodes.map(
      (n): ElkNode => ({
        id: n.id,
        width: DEFAULT_NODE_WIDTH,
        height: DEFAULT_NODE_HEIGHT,
        layoutOptions: isLayered
          ? { 'elk.layered.layering.layerConstraint': layerConstraintFor(n) }
          : undefined,
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
