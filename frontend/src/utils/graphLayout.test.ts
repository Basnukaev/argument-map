import { describe, it, expect } from 'vitest';
import { layoutGraph } from './graphLayout';
import type { NodeCardNode } from '@/components/graph/NodeCard';
import type { CustomEdgeEdge } from '@/components/graph/CustomEdge';

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

  it('если хотя бы у одного узла нет posX/posY - dagre считает все', () => {
    const saved = makeNode('saved');
    saved.data = { ...saved.data, posX: 999, posY: 999 };
    const fresh = makeNode('fresh'); // без posX/posY

    const result = layoutGraph([saved, fresh], [makeEdge('e', 'saved', 'fresh')]);

    // saved узел НЕ окажется в (999, 999) - dagre перетрёт обоих
    expect(result.find((n) => n.id === 'saved')!.position).not.toEqual({ x: 999, y: 999 });
  });
});
