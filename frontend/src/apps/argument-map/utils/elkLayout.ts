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
 *
 * Spacings подняты до 100/160 (с прежних 80/120) - реальный
 * EVIDENCE-узел с хадисом ~320×180, фикс наложений на v_tree/g_tree.
 * unnecessaryBendpoints=true срезает лишние изломы ортогональных
 * рёбер. portConstraints=FIXED_ORDER сохраняет логический порядок
 * handles вокруг узла при routing.
 */
const LAYERED_BASE: Record<string, string> = {
  'elk.algorithm': 'layered',
  'elk.edgeRouting': 'ORTHOGONAL',
  'elk.spacing.nodeNode': '100',
  'elk.layered.spacing.nodeNodeBetweenLayers': '160',
  'elk.layered.spacing.edgeNodeBetweenLayers': '50',
  'elk.layered.spacing.edgeEdgeBetweenLayers': '20',
  'elk.layered.crossingMinimization.strategy': 'LAYER_SWEEP',
  'elk.layered.nodePlacement.strategy': 'BRANDES_KOEPF',
  'elk.layered.layering.strategy': 'NETWORK_SIMPLEX',
  'elk.layered.considerModelOrder.strategy': 'NODES_AND_EDGES',
  'elk.layered.unnecessaryBendpoints': 'true',
  'elk.portConstraints': 'FIXED_ORDER',
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
    // Radial: POLYLINE edge routing - ORTHOGONAL давал острые углы
    // вокруг колец (90° + центральная симметрия = визуальная грязь).
    // POLYLINE рисует прямые сегменты с плавными перегибами.
    // centerOnRoot + явный root узел через node.layoutOptions (см.
    // applyElkLayout) - без этого ELK выбирал random первый узел
    // как центр и кольца не формировались.
    options: {
      'elk.algorithm': 'radial',
      'elk.edgeRouting': 'POLYLINE',
      'elk.spacing.nodeNode': '120',
      'elk.radial.compactor': 'RADIAL_COMPACTION',
      'elk.radial.optimizationCriteria': 'EDGE_LENGTH',
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
 * Находит root-узел для radial preset'а - QUESTION без incoming edges
 * (т.е. на него ничто не отвечает, это «голова» вопроса). Если таких
 * несколько - берётся первый. Если нет вообще QUESTION'ов - первый
 * узел графа (degenerate case, edge cases в reasoning без вопросов).
 */
function findRadialRoot(nodes: NodeCardNode[], edges: CustomEdgeEdge[]): string {
  const incoming = new Set<string>();
  for (const e of edges) {
    incoming.add(e.target);
  }
  const questions = nodes.filter((n) => n.data?.nodeType === 'QUESTION');
  const questionWithoutIn = questions.find((q) => !incoming.has(q.id));
  if (questionWithoutIn) return questionWithoutIn.id;
  if (questions.length > 0 && questions[0]) return questions[0].id;
  return nodes[0]?.id ?? '';
}

/**
 * Возвращает размеры узла. Приоритет: React-Flow measured (реальные
 * post-mount размеры через `node.measured.width/height`) → fallback
 * на DEFAULT. Без этого EVIDENCE с длинным хадисом (реально ~320×200)
 * раскладывался как 288×140 и налегал на соседей (см. v_tree.png).
 */
function nodeSize(n: NodeCardNode): { width: number; height: number } {
  const measured = (n as { measured?: { width?: number; height?: number } }).measured;
  return {
    width: measured?.width ?? DEFAULT_NODE_WIDTH,
    height: measured?.height ?? DEFAULT_NODE_HEIGHT,
  };
}

/**
 * Применяет ELK-раскладку для выбранного preset'а. Возвращает узлы с
 * новыми позициями (top-left, как ожидает React Flow). Edges -
 * неизменно, layout считается только под позиции; React Flow + наш
 * CustomEdge сами роутят рёбра по handles.
 *
 * Для layered-preset'ов добавляется per-node `layerConstraint` по
 * семантическому типу (QUESTION top / EVIDENCE bottom). Для radial -
 * constraint игнорируется (layers концепции нет), вместо этого
 * проставляется явный root через `elk.radial.rootNode` на нужный node.
 */
export async function applyElkLayout(
  nodes: NodeCardNode[],
  edges: CustomEdgeEdge[],
  preset: LayoutPreset = 'tree-tb',
): Promise<{ nodes: NodeCardNode[]; edges: CustomEdgeEdge[] }> {
  if (nodes.length === 0) return { nodes: [], edges };

  const config = PRESET_CONFIG[preset];
  const isLayered = config.algorithm === 'layered';
  const radialRootId = !isLayered ? findRadialRoot(nodes, edges) : '';

  const elkGraph: ElkNode = {
    id: 'root',
    layoutOptions: config.options,
    children: nodes.map((n): ElkNode => {
      const { width, height } = nodeSize(n);
      let layoutOptions: Record<string, string> | undefined;
      if (isLayered) {
        layoutOptions = {
          'elk.layered.layering.layerConstraint': layerConstraintFor(n),
        };
      } else if (n.id === radialRootId) {
        // ELK radial: указываем root через layoutOptions узла -
        // алгоритм центрирует именно его, остальные узлы ложатся
        // концентрическими кольцами по hop-distance от root'а.
        layoutOptions = { 'elk.radial.rootNode': 'true' };
      }
      return { id: n.id, width, height, layoutOptions };
    }),
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

  // Извлекаем bend points из результата ELK. edge.sections даёт
  // координаты startPoint + bendPoints[] + endPoint для каждого
  // ортогонального ребра. Эти точки описывают точный path который
  // ELK *задумал* при routing - используются в CustomEdge для
  // precise SVG rendering (вместо геометрической аппроксимации
  // через getSmoothStepPath от handles).
  const bendsByEdgeId = new Map<string, Array<{ x: number; y: number }>>();
  for (const elkEdge of result.edges ?? []) {
    const sections = elkEdge.sections ?? [];
    const first = sections[0];
    if (!first) continue;
    const bends = (first.bendPoints ?? []).map((p) => ({ x: p.x, y: p.y }));
    if (bends.length > 0) {
      bendsByEdgeId.set(elkEdge.id, bends);
    }
  }

  return {
    nodes: nodes.map((n) => {
      const pos = positionById.get(n.id);
      // защитный fallback: если ELK по какой-то причине не вернул
      // координат - оставляем существующие, чтобы узел не прыгнул в (0,0)
      return pos ? { ...n, position: pos } : n;
    }),
    edges: edges.map((e) => {
      const bends = bendsByEdgeId.get(e.id);
      return {
        ...e,
        data: e.data
          ? { ...e.data, bendPoints: bends }
          : (bends ? { bendPoints: bends } : e.data) as typeof e.data,
      };
    }),
  };
}
