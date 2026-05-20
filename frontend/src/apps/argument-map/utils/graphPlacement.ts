import { MarkerType } from '@xyflow/react';
import { EDGE_TYPE_TOKENS } from '@/shared/utils/designTokens';
import { layoutGraph } from '@/apps/argument-map/utils/graphLayout';
import type { NodeCardNode } from '@/apps/argument-map/components/graph/NodeCard';
import type { CustomEdgeEdge } from '@/apps/argument-map/components/graph/CustomEdge';
import type { components } from '@/shared/api/types';

type GraphResponse = components['schemas']['GraphResponse'];
type NodeDto = components['schemas']['NodeResponse'];
type EdgeDto = components['schemas']['EdgeResponse'];

/**
 * Цвета стрелок на конце ребра. Те же что у линии CustomEdge - один
 * источник истины (EDGE_TYPE_TOKENS), чтобы стрелка и линия всегда
 * совпадали по цвету.
 */
export const EDGE_ARROW_COLOR: Record<NonNullable<EdgeDto['edgeType']>, string> = {
  SUPPORTS: EDGE_TYPE_TOKENS.SUPPORTS.stroke,
  REFUTES: EDGE_TYPE_TOKENS.REFUTES.stroke,
  INVALIDATES: EDGE_TYPE_TOKENS.INVALIDATES.stroke,
  QUALIFIES: EDGE_TYPE_TOKENS.QUALIFIES.stroke,
  RESPONDS_TO: EDGE_TYPE_TOKENS.RESPONDS_TO.stroke,
};

/**
 * Поверхностное сравнение массивов id - чтобы не пере-устанавливать
 * selection state если содержимое не изменилось.
 */
export function sameIds(a: string[], b: string[]): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
  return true;
}

// размеры под NodeCard (w-72 = 288px, высота переменная). 120 - типичная
// высота с заголовком + 2 строками текста; 40-60 - воздух между узлами
export const NODE_W = 288;
export const NODE_H = 120;
const PLACE_GAP_X = 60;
const PLACE_GAP_Y = 40;

interface XY {
  x: number;
  y: number;
}

/**
 * Подбирает позицию для нового узла рядом с anchor так чтобы не
 * накладываться на существующие. Сначала пробует базовую точку (на
 * правильной стороне от anchor), потом расходится по спирали по Y и X
 * пока не найдёт свободное место. Если всё пространство занято в
 * пределах разумного - возвращает базовую позицию.
 *
 * direction='incoming' - новый узел слева от anchor (он source ребра);
 * 'outgoing' - справа (он target). Совпадает с lr-ориентацией dagre.
 */
export function findFreePosition(
  anchor: XY,
  direction: 'incoming' | 'outgoing',
  existing: ReadonlyArray<{ position: XY }>,
): XY {
  const baseDx = direction === 'incoming' ? -(NODE_W + PLACE_GAP_X) : NODE_W + PLACE_GAP_X;
  const stepY = NODE_H + PLACE_GAP_Y;
  const stepX = NODE_W + PLACE_GAP_X;

  function overlaps(x: number, y: number): boolean {
    return existing.some((n) => {
      return Math.abs(n.position.x - x) < NODE_W && Math.abs(n.position.y - y) < NODE_H;
    });
  }

  const SHELLS = 6;
  for (let shell = 0; shell <= SHELLS; shell++) {
    const yOffsets = shell === 0 ? [0] : [shell, -shell];
    const xMultipliers = shell <= 2 ? [0] : [0, 0.5, -0.5];

    for (const xMul of xMultipliers) {
      for (const yMul of yOffsets) {
        const dirSign = direction === 'incoming' ? -1 : 1;
        const dx = baseDx + xMul * stepX * dirSign;
        const dy = yMul * stepY;
        const x = anchor.x + dx;
        const y = anchor.y + dy;
        if (!overlaps(x, y)) return { x, y };
      }
    }
  }
  return { x: anchor.x + baseDx, y: anchor.y };
}

/**
 * Вычисляет кривизну Безье для каждого ребра с учётом «братских» рёбер
 * между той же парой узлов (source+target в том же порядке).
 *
 * Логика:
 * - 1 ребро → стандартная кривизна 0.25
 * - N рёбер → равномерно от 0.1 до 0.6, расходятся веером
 *
 * Сортируем siblings по id для детерминированного порядка: одинаковый
 * индекс между рендерами → нет дрожания кривых при обновлении графа.
 *
 * Вызывается ОДИН РАЗ в buildFlow для всего массива рёбер — в отличие
 * от прежнего useSiblingCurvature, который вызывал useEdges() в каждом
 * edge-компоненте (O(E²) per rerender при N рёбрах).
 *
 * @returns Map<edgeId, curvature>
 */
export function computeSiblingCurvatures(
  edges: ReadonlyArray<{ id: string; source: string; target: string }>,
): Map<string, number> {
  const MIN_CURV = 0.1;
  const MAX_CURV = 0.6;
  const DEFAULT_CURV = 0.25;

  // группируем по ключу «source→target», сортируем по id
  const groups = new Map<string, string[]>();
  for (const e of edges) {
    const key = `${e.source}→${e.target}`;
    let group = groups.get(key);
    if (!group) {
      group = [];
      groups.set(key, group);
    }
    group.push(e.id);
  }
  for (const group of groups.values()) {
    group.sort((a, b) => a.localeCompare(b));
  }

  const result = new Map<string, number>();
  for (const e of edges) {
    const key = `${e.source}→${e.target}`;
    const siblings = groups.get(key)!;
    if (siblings.length <= 1) {
      result.set(e.id, DEFAULT_CURV);
    } else {
      const myIndex = siblings.indexOf(e.id);
      // defensive guard: если ребро не нашло себя (concurrent removal),
      // возвращаем дефолт вместо отрицательного значения
      if (myIndex < 0) {
        result.set(e.id, DEFAULT_CURV);
      } else {
        const count = siblings.length;
        result.set(e.id, MIN_CURV + (myIndex / (count - 1)) * (MAX_CURV - MIN_CURV));
      }
    }
  }
  return result;
}

/**
 * Собирает RF nodes/edges из бэк-DTO. Применяет layoutGraph (dagre или
 * mixed-mode при наличии сохранённых позиций), прокидывает в data
 * рёбер типы концов для CustomEdge стилизации, проставляет markerEnd.
 *
 * Кривизна Безье (curvature) для параллельных рёбер между той же парой
 * узлов вычисляется здесь один раз через computeSiblingCurvatures и
 * кладётся в edge.data.curvature — CustomEdge читает готовое значение
 * без подписки на весь store рёбер.
 *
 * @param previousNodes - последний snapshot nodes (для mixed-режима
 *   layoutGraph: fresh-узлы получают dagre-позиции, сохранённые остаются)
 */
export function buildFlow(
  graph: GraphResponse,
  showEdgeLabels: boolean,
  previousNodes: ReadonlyArray<NodeCardNode> = [],
): { nodes: NodeCardNode[]; edges: CustomEdgeEdge[] } {
  const rawNodes: NodeCardNode[] = (graph.nodes ?? [])
    .filter((n): n is NodeDto & { id: string } => Boolean(n.id))
    .map((n) => ({
      id: n.id,
      type: 'argumentNode' as const,
      position: { x: 0, y: 0 },
      // zIndex из бэка - persisted stacking order (миграция 40).
      // 0 - default для узлов которые ни разу не trogали bring-to-front /
      // send-to-back. ReactFlow поддерживает положительные и отрицательные
      // значения, выше = ближе к viewer
      zIndex: n.zIndex ?? 0,
      data: n,
    }));

  const nodeTypeById = new Map<string, NonNullable<NodeDto['nodeType']>>();
  for (const n of rawNodes) {
    if (n.data.nodeType) nodeTypeById.set(n.id, n.data.nodeType);
  }

  // предварительно строим список source/target чтобы вычислить кривизну
  const edgeSrcTarget = (graph.edges ?? [])
    .filter(
      (e): e is EdgeDto & { id: string; fromNodeId: string; toNodeId: string } =>
        Boolean(e.id && e.fromNodeId && e.toNodeId),
    )
    .map((e) => ({ id: e.id, source: e.fromNodeId, target: e.toNodeId }));

  const curvatureMap = computeSiblingCurvatures(edgeSrcTarget);

  const rawEdges: CustomEdgeEdge[] = (graph.edges ?? [])
    .filter(
      (e): e is EdgeDto & { id: string; fromNodeId: string; toNodeId: string } =>
        Boolean(e.id && e.fromNodeId && e.toNodeId),
    )
    .map((e) => {
      const edgeType = e.edgeType ?? 'SUPPORTS';
      const fromType = nodeTypeById.get(e.fromNodeId) ?? 'CLAIM';
      const toType = nodeTypeById.get(e.toNodeId) ?? 'CLAIM';
      return {
        id: e.id,
        source: e.fromNodeId,
        target: e.toNodeId,
        sourceHandle: e.sourceHandle ?? undefined,
        targetHandle: e.targetHandle ?? undefined,
        type: 'argumentEdge' as const,
        data: {
          edgeType,
          fromType,
          toType,
          rationale: e.rationale,
          showLabel: showEdgeLabels,
          curvature: curvatureMap.get(e.id) ?? 0.25,
        },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: EDGE_ARROW_COLOR[edgeType],
          width: 18,
          height: 18,
        },
      };
    });

  return { nodes: layoutGraph(rawNodes, rawEdges, 'LR', previousNodes), edges: rawEdges };
}
