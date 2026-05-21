/**
 * Helper для distribute'а edge handles по сторонам узлов (top/right/
 * bottom/left) на основе их **взаимных позиций**. Используется в двух
 * местах:
 *
 * - `graphPlacement.ts:buildFlow` - при первом mount, после layoutGraph
 *   даёт sync dagre-позиции
 * - `useAutoLayout.ts:triggerRelayout` - после ELK relayout, чтобы
 *   handles отразили новые координаты
 *
 * Без переcчёта handles после relayout рёбра рисуются bezier'ом через
 * СТАРЫЕ handles → НОВЫЕ позиции узлов и уходят петлёй за пределы
 * viewport (наблюдалось на radial-preset до этого fix'а).
 */

export type HandleSide = 'top' | 'right' | 'bottom' | 'left';

const OPPOSITE: Record<HandleSide, HandleSide> = {
  top: 'bottom',
  bottom: 'top',
  left: 'right',
  right: 'left',
};

/**
 * Считает source/target handles по углу между центрами узлов. Source
 * handle — сторона исходного узла, обращённая к target'у. Target
 * handle — противоположная сторона target узла (face-to-face).
 *
 * Чисто геометрия, без знания layout-направления — годится для любого
 * preset'а (vertical / horizontal / radial).
 */
export function pickHandlesByPosition(
  srcPos: { x: number; y: number } | undefined,
  tgtPos: { x: number; y: number } | undefined,
): { source: HandleSide; target: HandleSide } {
  if (!srcPos || !tgtPos) {
    // Fallback для отсутствующих позиций — bottom→top как наиболее
    // частый случай для argument tree (Sugiyama TB по default)
    return { source: 'bottom', target: 'top' };
  }
  const dx = tgtPos.x - srcPos.x;
  const dy = tgtPos.y - srcPos.y;
  let source: HandleSide;
  if (Math.abs(dx) > Math.abs(dy)) {
    source = dx > 0 ? 'right' : 'left';
  } else {
    source = dy > 0 ? 'bottom' : 'top';
  }
  return { source, target: OPPOSITE[source] };
}
