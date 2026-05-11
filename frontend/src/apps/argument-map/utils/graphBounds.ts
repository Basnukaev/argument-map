import type { Node } from '@xyflow/react';

export interface BBox {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}

const DEFAULT_NODE_W = 288;
const DEFAULT_NODE_H = 120;

/**
 * Вычисляет bounding box узлов с учётом их измеренных размеров
 * (`measured.width/height` после mount или `width/height` если задано).
 * Fallback на дефолты NodeCard 288x120 если RF ещё не измерил.
 * Возвращает null для пустого массива.
 */
export function getBoundingBox(nodes: ReadonlyArray<Node>): BBox | null {
  if (nodes.length === 0) return null;
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  for (const n of nodes) {
    const w = n.measured?.width ?? n.width ?? DEFAULT_NODE_W;
    const h = n.measured?.height ?? n.height ?? DEFAULT_NODE_H;
    if (n.position.x < minX) minX = n.position.x;
    if (n.position.y < minY) minY = n.position.y;
    if (n.position.x + w > maxX) maxX = n.position.x + w;
    if (n.position.y + h > maxY) maxY = n.position.y + h;
  }
  return { minX, minY, maxX, maxY };
}

/**
 * Объединяет два bbox-rectangle с дополнительным padding.
 * Используется в CompactMiniMap чтобы viewport и узлы оба влезли
 * в viewBox с отступом.
 */
export function expandBounds(a: BBox, b: BBox, padding = 0): BBox {
  return {
    minX: Math.min(a.minX, b.minX) - padding,
    minY: Math.min(a.minY, b.minY) - padding,
    maxX: Math.max(a.maxX, b.maxX) + padding,
    maxY: Math.max(a.maxY, b.maxY) + padding,
  };
}
