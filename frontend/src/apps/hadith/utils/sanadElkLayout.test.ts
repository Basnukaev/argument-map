import { describe, it, expect } from 'vitest';
import type { Edge, Node } from '@xyflow/react';
import { applySanadElkLayout } from './sanadElkLayout';

// Реальный ELK (без mock) — проверяем именно геометрию ортогональной
// раскладки: разведение параллельных ветвей (Проблема 2) и bend-points,
// которыми рёбра огибают карточки (Проблема 1). Граф крошечный → быстро.

function node(id: string): Node {
  return { id, type: 'sanad', position: { x: 0, y: 0 }, data: {} };
}
function edge(id: string, source: string, target: string, phrase?: string): Edge {
  return {
    id,
    source,
    target,
    type: 'sanad',
    data: phrase ? { transmissionPhrase: phrase } : {},
  };
}

// Внутренние изломы = points без startPoint/endPoint (длина − 2).
function interiorBends(e: Edge): number {
  const pts = (e.data as { points?: unknown[] } | undefined)?.points;
  return pts ? Math.max(0, pts.length - 2) : 0;
}

describe('applySanadElkLayout', () => {
  it('пустой граф — без падения', async () => {
    const { nodes, edges } = await applySanadElkLayout([], []);
    expect(nodes).toEqual([]);
    expect(edges).toEqual([]);
  });

  it('линейная цепь: source выше target (layered DOWN, без инверсии направления)', async () => {
    const nodes = [node('p'), node('a'), node('b')];
    const edges = [edge('e1', 'p', 'a', 'عن'), edge('e2', 'a', 'b', 'حدثنا')];
    const res = await applySanadElkLayout(nodes, edges);
    const y = Object.fromEntries(res.nodes.map((n) => [n.id, n.position.y]));
    // Пророк/ранний рави сверху, поздний — ниже (семантика цепи передачи).
    expect(y.p!).toBeLessThan(y.a!);
    expect(y.a!).toBeLessThan(y.b!);
  });

  it('разветвление: параллельные ветви разводятся по горизонтали (Проблема 2)', async () => {
    // p → a; a раздваивается на b и c.
    const nodes = [node('p'), node('a'), node('b'), node('c')];
    const edges = [edge('e1', 'p', 'a'), edge('e2', 'a', 'b'), edge('e3', 'a', 'c')];
    const res = await applySanadElkLayout(nodes, edges);
    const pos = Object.fromEntries(res.nodes.map((n) => [n.id, n.position]));
    // b и c — один слой (близкий y), но разнесены по x (не наложены друг на друга).
    expect(Math.abs(pos.b!.y - pos.c!.y)).toBeLessThan(20);
    expect(Math.abs(pos.b!.x - pos.c!.x)).toBeGreaterThan(100);
  });

  it('разветвление: хотя бы одно расходящееся ребро огибает карточку (bend-points, Проблема 1)', async () => {
    const nodes = [node('p'), node('a'), node('b'), node('c')];
    const edges = [edge('e1', 'p', 'a'), edge('e2', 'a', 'b'), edge('e3', 'a', 'c')];
    const res = await applySanadElkLayout(nodes, edges);
    const totalBends = res.edges.reduce((sum, e) => sum + interiorBends(e), 0);
    expect(totalBends).toBeGreaterThan(0);
  });

  it('каждое ребро несёт полную полилинию ELK (start→…→end, ≥2 точки)', async () => {
    const nodes = [node('p'), node('a')];
    const edges = [edge('e1', 'p', 'a', 'عن')];
    const res = await applySanadElkLayout(nodes, edges);
    const pts = (res.edges[0]!.data as { points?: unknown[] }).points;
    expect(Array.isArray(pts)).toBe(true);
    expect(pts!.length).toBeGreaterThanOrEqual(2);
  });

  it('сохраняет transmissionPhrase в data ребра', async () => {
    const nodes = [node('p'), node('a')];
    const edges = [edge('e1', 'p', 'a', 'أنبأ')];
    const res = await applySanadElkLayout(nodes, edges);
    expect((res.edges[0]!.data as { transmissionPhrase?: string }).transmissionPhrase).toBe('أنبأ');
  });
});
