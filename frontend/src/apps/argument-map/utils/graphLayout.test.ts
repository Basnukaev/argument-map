import { describe, it, expect, vi } from 'vitest';

// Mock elkjs до импорта SUT - чтобы applyLayout('elk') в тестах не
// тянул реальный ~200KB bundle и не падал в jsdom. Возвращает
// фиксированные позиции по индексу
vi.mock('elkjs/lib/elk.bundled.js', () => {
  class MockELK {
    async layout(graph: { children?: Array<{ id: string }> }) {
      return {
        ...graph,
        children: (graph.children ?? []).map((c, i) => ({
          ...c,
          x: 999 + i,
          y: 888 + i,
        })),
      };
    }
  }
  return { default: MockELK };
});

import { layoutGraph, applyLayout } from './graphLayout';
import type { NodeCardNode } from '@/apps/argument-map/components/graph/NodeCard';
import type { CustomEdgeEdge } from '@/apps/argument-map/components/graph/CustomEdge';

function makeNode(id: string): NodeCardNode {
  return {
    id,
    type: 'argumentNode',
    position: { x: 0, y: 0 },
    data: { id, content: id, nodeType: 'CLAIM', status: 'UNVERIFIED' },
  };
}

function makeEdge(id: string, from: string, to: string): CustomEdgeEdge {
  return {
    id,
    source: from,
    target: to,
    type: 'argumentEdge',
    data: { edgeType: 'SUPPORTS', fromType: 'CLAIM', toType: 'CLAIM' },
  };
}

describe('layoutGraph', () => {
  it('возвращает столько же узлов сколько на входе', () => {
    const nodes = [makeNode('a'), makeNode('b'), makeNode('c')];
    const edges = [makeEdge('e1', 'a', 'b'), makeEdge('e2', 'b', 'c')];

    const result = layoutGraph(nodes, edges);

    expect(result).toHaveLength(3);
    expect(result.map((n) => n.id)).toEqual(['a', 'b', 'c']);
  });

  it('расставляет узлы по разным позициям (не все в (0,0))', () => {
    const nodes = [makeNode('a'), makeNode('b')];
    const edges = [makeEdge('e1', 'a', 'b')];

    const result = layoutGraph(nodes, edges);

    expect(result[0]!.position).not.toEqual(result[1]!.position);
  });

  it('LR-направление: целевой узел правее источника', () => {
    const nodes = [makeNode('source'), makeNode('target')];
    const edges = [makeEdge('e1', 'source', 'target')];

    const result = layoutGraph(nodes, edges, 'LR');

    const src = result.find((n) => n.id === 'source')!;
    const tgt = result.find((n) => n.id === 'target')!;
    expect(tgt.position.x).toBeGreaterThan(src.position.x);
  });

  it('сохраняет данные узла нетронутыми', () => {
    const node = makeNode('a');
    const result = layoutGraph([node], []);
    expect(result[0]!.data).toBe(node.data);
    expect(result[0]!.type).toBe('argumentNode');
  });

  it('обрабатывает пустой граф без падения', () => {
    expect(layoutGraph([], [])).toEqual([]);
  });

  it('если у всех узлов есть posX/posY - layout уважает их', () => {
    const a = makeNode('a');
    a.data = { ...a.data, posX: 100, posY: 200 };
    const b = makeNode('b');
    b.data = { ...b.data, posX: 500, posY: -50 };

    const result = layoutGraph([a, b], [makeEdge('e', 'a', 'b')]);

    expect(result.find((n) => n.id === 'a')!.position).toEqual({ x: 100, y: 200 });
    expect(result.find((n) => n.id === 'b')!.position).toEqual({ x: 500, y: -50 });
  });

  it('смешанный режим: сохранённые узлы остаются on their positions, fresh - справа', () => {
    const a = makeNode('a');
    a.data = { ...a.data, posX: 100, posY: 200 };
    const b = makeNode('b');
    b.data = { ...b.data, posX: 500, posY: 300 };
    const fresh = makeNode('fresh'); // без posX/posY

    const result = layoutGraph([a, b, fresh], [makeEdge('e', 'a', 'b')]);

    // сохранённые остаются на своих местах
    expect(result.find((n) => n.id === 'a')!.position).toEqual({ x: 100, y: 200 });
    expect(result.find((n) => n.id === 'b')!.position).toEqual({ x: 500, y: 300 });
    // fresh - правее всех сохранённых (maxX = 500 + gap 400 = 900)
    const freshPos = result.find((n) => n.id === 'fresh')!.position;
    expect(freshPos.x).toBeGreaterThan(500);
    expect(freshPos.x).toBe(900);
  });

  it('смешанный режим: несколько fresh узлов столбцом', () => {
    const saved = makeNode('saved');
    saved.data = { ...saved.data, posX: 0, posY: 0 };
    const f1 = makeNode('f1');
    const f2 = makeNode('f2');

    const result = layoutGraph([saved, f1, f2], []);

    const f1Pos = result.find((n) => n.id === 'f1')!.position;
    const f2Pos = result.find((n) => n.id === 'f2')!.position;
    expect(f1Pos.x).toBe(f2Pos.x); // одна колонка
    expect(f2Pos.y).toBeGreaterThan(f1Pos.y); // f2 ниже f1
  });
});

describe('applyLayout (preset)', () => {
  it('preset=tree-tb - возвращает ELK позиции через моки elkjs', async () => {
    const nodes = [makeNode('a'), makeNode('b')];
    const edges = [makeEdge('e', 'a', 'b')];
    const result = await applyLayout(nodes, edges, 'tree-tb');
    expect(result.nodes).toHaveLength(2);
    expect(result.nodes[0]!.position).toEqual({ x: 999, y: 888 });
    expect(result.nodes[1]!.position).toEqual({ x: 1000, y: 889 });
  });

  it('preset=tree-lr - тот же layered ELK, но direction RIGHT', async () => {
    const nodes = [makeNode('a'), makeNode('b')];
    const result = await applyLayout(nodes, [], 'tree-lr');
    expect(result.nodes).toHaveLength(2);
    expect(result.nodes[0]!.position).toEqual({ x: 999, y: 888 });
  });

  it('preset=radial - d3-hierarchy radial layout', async () => {
    const nodes = [makeNode('a'), makeNode('b'), makeNode('c')];
    const edges = [makeEdge('e1', 'a', 'b'), makeEdge('e2', 'a', 'c')];
    const result = await applyLayout(nodes, edges, 'radial');
    expect(result.nodes).toHaveLength(3);
    // root (первый узел при absence QUESTION) в (-w/2, -h/2)
    expect(result.nodes[0]!.position).toBeDefined();
  });

  it('пустой граф - возвращает пустые массивы для любого preset', async () => {
    expect((await applyLayout([], [], 'tree-tb')).nodes).toEqual([]);
    expect((await applyLayout([], [], 'tree-lr')).nodes).toEqual([]);
    expect((await applyLayout([], [], 'radial')).nodes).toEqual([]);
  });

  it('default preset (без аргумента) - tree-tb', async () => {
    const nodes = [makeNode('a')];
    const result = await applyLayout(nodes, []);
    expect(result.nodes).toHaveLength(1);
  });
});
