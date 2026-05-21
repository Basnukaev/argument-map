/**
 * SVG path-builder для ортогональных рёбер по последовательности точек
 * от ELK layout. Используется в CustomEdge для precise rendering -
 * траектория точно match'ит то, что задумал routing-движок (избегает
 * соседних узлов, разводит параллельные рёбра).
 *
 * Углы скругляются Q-командой (quadratic bezier) с заданным радиусом,
 * чтобы не было резких 90° изломов которые на больших increment'ах
 * выглядят грубо.
 */

export interface Point {
  x: number;
  y: number;
}

/**
 * Строит SVG path по последовательности точек с округлёнными углами.
 *
 * Алгоритм:
 * 1. Старт в первой точке (M).
 * 2. Для каждой средней точки `points[i]` (i > 0, i < N-1):
 *    - Берём подход с предыдущей точки, обрезаем до `radius` перед
 *      углом (L до точки за radius пикселей)
 *    - Скругляем Q-кривой через сам угол к точке за radius пикселей
 *      на следующем сегменте
 * 3. Финальный L к последней точке.
 *
 * Если segment короче 2×radius - угол скругляем по половине segment'а
 * (чтобы не было «выхода» за границы исходного path).
 */
export function buildRoundedOrthogonalPath(
  points: ReadonlyArray<Point>,
  radius: number = 12,
): string {
  if (points.length < 2) return '';
  if (points.length === 2) {
    const [a, b] = points as [Point, Point];
    return `M ${a.x} ${a.y} L ${b.x} ${b.y}`;
  }

  const first = points[0]!;
  let path = `M ${first.x} ${first.y}`;

  for (let i = 1; i < points.length - 1; i++) {
    const prev = points[i - 1]!;
    const current = points[i]!;
    const next = points[i + 1]!;

    // Доступный radius - ограничен половиной обеих ребер примыкающих
    // к углу. Чтобы скругление не залезало за середину сегмента.
    const distPrev = Math.hypot(current.x - prev.x, current.y - prev.y);
    const distNext = Math.hypot(next.x - current.x, next.y - current.y);
    const r = Math.min(radius, distPrev / 2, distNext / 2);

    // Точка на ребре prev→current на расстоянии r ДО угла
    const t1 = r / distPrev;
    const ax = current.x - (current.x - prev.x) * t1;
    const ay = current.y - (current.y - prev.y) * t1;

    // Точка на ребре current→next на расстоянии r ПОСЛЕ угла
    const t2 = r / distNext;
    const bx = current.x + (next.x - current.x) * t2;
    const by = current.y + (next.y - current.y) * t2;

    path += ` L ${ax} ${ay} Q ${current.x} ${current.y} ${bx} ${by}`;
  }

  const last = points[points.length - 1]!;
  path += ` L ${last.x} ${last.y}`;

  return path;
}

/**
 * Выбирает позицию label'а ребра - середину самого длинного сегмента.
 * Это даёт стабильное место без наложения на ноды (потому что
 * сегменты между bend points идут НЕ через ноды по построению ELK
 * ORTHOGONAL routing).
 *
 * Без этого helper'а CustomEdge брал бы геометрический центр всего
 * пути, который для Г-образных рёбер часто попадает прямо на узел.
 */
export function pickLabelPosition(points: ReadonlyArray<Point>): Point {
  if (points.length < 2) {
    return points[0] ?? { x: 0, y: 0 };
  }
  let bestFrom = points[0]!;
  let bestTo = points[1]!;
  let bestLength = 0;
  for (let i = 0; i < points.length - 1; i++) {
    const a = points[i]!;
    const b = points[i + 1]!;
    const length = Math.hypot(b.x - a.x, b.y - a.y);
    if (length > bestLength) {
      bestLength = length;
      bestFrom = a;
      bestTo = b;
    }
  }
  return {
    x: (bestFrom.x + bestTo.x) / 2,
    y: (bestFrom.y + bestTo.y) / 2,
  };
}
