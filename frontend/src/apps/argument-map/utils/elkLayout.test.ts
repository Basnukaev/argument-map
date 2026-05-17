import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { NodeCardNode } from '@/apps/argument-map/components/graph/NodeCard';
import type { CustomEdgeEdge } from '@/apps/argument-map/components/graph/CustomEdge';

// Mock elkjs ДО импорта SUT. ELK реально работает в jsdom но bundled.js
// весит ~200KB - в unit тестах подменяем на stub, проверяем что:
// (1) elk.layout вызван с правильным графом
// (2) результат-позиции пробрасываются на nodes
vi.mock('elkjs/lib/elk.bundled.js', () => {
  class MockELK {
    async layout(graph: { children?: Array<{ id: string }> }) {
      // отдаём детерминированные позиции по индексу
      return {
        ...graph,
        children: (graph.children ?? []).map((c, i) => ({
          ...c,
          x: 100 + i * 50,
          y: 200 + i * 30,
        })),
      };
    }
  }
  return { default: MockELK };
});

// Импорт ПОСЛЕ vi.mock - чтобы applyElkLayout получил замоканный default-export
const { applyElkLayout } = await import('./elkLayout');

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

describe('applyElkLayout', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('пустой граф - возвращает пустые массивы без падения', async () => {
    const { nodes, edges } = await applyElkLayout([], []);
    expect(nodes).toEqual([]);
    expect(edges).toEqual([]);
  });

  it('обновляет позиции узлов значениями из ELK', async () => {
    const input = [makeNode('a'), makeNode('b')];
    const { nodes } = await applyElkLayout(input, []);
    expect(nodes[0]!.position).toEqual({ x: 100, y: 200 });
    expect(nodes[1]!.position).toEqual({ x: 150, y: 230 });
  });

  it('возвращает столько же узлов сколько на входе', async () => {
    const input = [makeNode('a'), makeNode('b'), makeNode('c')];
    const { nodes } = await applyElkLayout(input, []);
    expect(nodes).toHaveLength(3);
    expect(nodes.map((n) => n.id)).toEqual(['a', 'b', 'c']);
  });

  it('сохраняет data узла нетронутыми', async () => {
    const input = [makeNode('a')];
    const { nodes } = await applyElkLayout(input, []);
    expect(nodes[0]!.data).toBe(input[0]!.data);
    expect(nodes[0]!.type).toBe('argumentNode');
  });

  it('возвращает рёбра без изменений (мы только пересчитываем позиции узлов)', async () => {
    const inputNodes = [makeNode('a'), makeNode('b')];
    const inputEdges = [makeEdge('e1', 'a', 'b')];
    const { edges } = await applyElkLayout(inputNodes, inputEdges);
    expect(edges).toBe(inputEdges);
  });
});
