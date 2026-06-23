import ELK, { type ElkNode, type ElkExtendedEdge } from 'elkjs/lib/elk.bundled.js';
import type { Edge, Node } from '@xyflow/react';
import { NODE_WIDTH, NODE_HEIGHT } from './sanadLayout';

const elk = new ELK();

/**
 * ELK layered-раскладка графа иснада (TB: Пророк ﷺ сверху, сборники снизу).
 *
 * Зачем ELK вместо dagre (С64, проблемы Абдулы с графом):
 *  - Проблема 1: рёбра пересекали карточки. `elk.edgeRouting=ORTHOGONAL`
 *    + `edgeNodeBetweenLayers` прокладывает рёбра под 90°, ОГИБАЯ узлы
 *    (bend-points), а не по прямой через них.
 *  - Проблема 2: параллельные рёбра накладывались. `edgeEdgeBetweenLayers`
 *    разводит их в стороны.
 *  - Проблема 3: подписи-формулы передачи неоднозначно липли к рёбрам.
 *    Передаём ELK размер label'а → он резервирует место, а CustomEdge
 *    ставит подпись на середину самого длинного сегмента (pickLabelPosition).
 *
 * Проще argument-map ELK (apps/argument-map/utils/elkLayout.ts): НЕТ
 * layerConstraint по типам узлов (у иснада нет семантических ярусов —
 * Пророк просто топологический корень), НЕТ инверсии направления рёбер
 * (source=ранний рави/Пророк уже совпадает с ELK layered DOWN, тогда как
 * argument-map инвертирует child→parent), и как следствие НЕТ reverse
 * bend-points.
 *
 * Async (ELK работает в Web-Worker-стиле). Вызывающий держит dagre-раскладку
 * как мгновенный fallback и подменяет ELK-результатом по готовности.
 */

const SANAD_LAYOUT_OPTIONS: Record<string, string> = {
  'elk.algorithm': 'layered',
  'elk.direction': 'DOWN',
  'elk.edgeRouting': 'ORTHOGONAL',
  // Горизонтальный зазор узлов в слое (turuq-дерево «Все пути»).
  'elk.spacing.nodeNode': '90',
  // Вертикальный зазор между слоями цепи.
  'elk.layered.spacing.nodeNodeBetweenLayers': '120',
  // ГЛАВНОЕ для Проблемы 1: зазор ребро↔карточка → рёбра огибают узлы.
  'elk.layered.spacing.edgeNodeBetweenLayers': '40',
  // Проблема 2: разведение параллельных рёбер.
  'elk.layered.spacing.edgeEdgeBetweenLayers': '22',
  'elk.layered.crossingMinimization.strategy': 'LAYER_SWEEP',
  'elk.layered.nodePlacement.strategy': 'BRANDES_KOEPF',
  // BALANCED усредняет 4 прохода выравнивания → родитель центрируется над
  // веером детей. Так Пророк и основная цепь идут по ЦЕНТРУ, а не по краю
  // (С64: спина «прибивалась» к правому краю фан-аута).
  'elk.layered.nodePlacement.bk.fixedAlignment': 'BALANCED',
  'elk.layered.considerModelOrder.strategy': 'NODES_AND_EDGES',
  // Срезает лишние изломы ортогональных рёбер — чище путь.
  'elk.layered.unnecessaryBendpoints': 'true',
  // Веер из одного узла идёт общим стволом и ветвится один раз, а не «гребёнкой»
  // вертикалей из разнесённых портов (С64: «убери отступы»).
  'elk.layered.mergeEdges': 'true',
};

/**
 * Возвращает узлы с ELK-позициями (top-left, как ждёт React Flow) и рёбра
 * с `data.bendPoints` — координатами изломов ортогонального пути, которые
 * SanadCustomEdge превращает в SVG-path.
 */
export async function applySanadElkLayout<N extends Node, E extends Edge>(
  nodes: N[],
  edges: E[],
): Promise<{ nodes: N[]; edges: E[] }> {
  if (nodes.length === 0) return { nodes, edges };

  const elkGraph: ElkNode = {
    id: 'root',
    layoutOptions: SANAD_LAYOUT_OPTIONS,
    children: nodes.map(
      (n): ElkNode => ({ id: n.id, width: NODE_WIDTH, height: NODE_HEIGHT }),
    ),
    edges: edges.map((e): ElkExtendedEdge => {
      const phrase = (e.data as { transmissionPhrase?: string } | undefined)
        ?.transmissionPhrase;
      // Сообщаем ELK размер подписи-формулы (арабский текст ~9px/символ),
      // иначе routing её не учитывает и подпись липнет к узлу.
      const labels = phrase
        ? [{ id: `${e.id}-l`, width: Math.max(44, phrase.length * 9), height: 22 }]
        : [];
      // Без инверсии: source (ранний рави / Пророк) выше target — совпадает
      // с ELK layered DOWN. (argument-map тут инвертирует, иснаду не нужно.)
      return { id: e.id, sources: [e.source], targets: [e.target], labels };
    }),
  };

  const result = await elk.layout(elkGraph);

  const posById = new Map<string, { x: number; y: number }>();
  for (const child of result.children ?? []) {
    posById.set(child.id, { x: child.x ?? 0, y: child.y ?? 0 });
  }

  // ПОЛНАЯ полилиния ребра в координатах ELK: startPoint (порт на нижней
  // границе source) → bendPoints → endPoint (порт на верхней границе target).
  // Берём именно ELK-порты, а НЕ RF-хэндл (bottom-center): при веере из одного
  // узла ELK разносит порты по нижней грани, и путь остаётся ортогональным
  // (С64: ранее склейка center→bend давала диагонали). Узлы тоже ELK-позиции —
  // координаты совпадают, ребро точно стыкуется с границами карточек.
  const pointsById = new Map<string, Array<{ x: number; y: number }>>();
  for (const elkEdge of result.edges ?? []) {
    const section = (elkEdge.sections ?? [])[0];
    if (!section) continue;
    const pts: Array<{ x: number; y: number }> = [];
    if (section.startPoint) pts.push({ x: section.startPoint.x, y: section.startPoint.y });
    for (const b of section.bendPoints ?? []) pts.push({ x: b.x, y: b.y });
    if (section.endPoint) pts.push({ x: section.endPoint.x, y: section.endPoint.y });
    if (pts.length >= 2) pointsById.set(elkEdge.id, pts);
  }

  return {
    nodes: nodes.map((n) => {
      const pos = posById.get(n.id);
      // Защитный fallback: ELK не вернул координат → оставляем как есть,
      // чтобы узел не прыгнул в (0,0).
      return pos ? { ...n, position: pos } : n;
    }),
    edges: edges.map((e) => {
      const points = pointsById.get(e.id);
      return { ...e, data: { ...(e.data as object), points } } as E;
    }),
  };
}
