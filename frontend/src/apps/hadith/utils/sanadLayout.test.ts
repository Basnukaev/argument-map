import { describe, it, expect } from 'vitest';
import type { Edge, Node } from '@xyflow/react';
import { layoutSanad } from './sanadLayout';

describe('layoutSanad', () => {
  it('раскладывает цепь сверху вниз: корень выше своих потомков', () => {
    const nodes: Node[] = [
      { id: 'prophet', position: { x: 0, y: 0 }, data: {} },
      { id: 'a', position: { x: 0, y: 0 }, data: {} },
      { id: 'b', position: { x: 0, y: 0 }, data: {} },
    ];
    const edges: Edge[] = [
      { id: 'e1', source: 'prophet', target: 'a' },
      { id: 'e2', source: 'a', target: 'b' },
    ];

    const out = layoutSanad(nodes, edges);
    const y = (id: string) => out.find((n) => n.id === id)!.position.y;

    expect(out).toHaveLength(3);
    // TB-направление: Пророк ﷺ сверху, цепь спускается вниз.
    expect(y('prophet')).toBeLessThan(y('a'));
    expect(y('a')).toBeLessThan(y('b'));
  });

  it('разводит развилку на одном уровне (fan-out у общего звена)', () => {
    const nodes: Node[] = [
      { id: 'root', position: { x: 0, y: 0 }, data: {} },
      { id: 'left', position: { x: 0, y: 0 }, data: {} },
      { id: 'right', position: { x: 0, y: 0 }, data: {} },
    ];
    const edges: Edge[] = [
      { id: 'e1', source: 'root', target: 'left' },
      { id: 'e2', source: 'root', target: 'right' },
    ];

    const out = layoutSanad(nodes, edges);
    const left = out.find((n) => n.id === 'left')!;
    const right = out.find((n) => n.id === 'right')!;

    // Дети общего звена стоят на одном ранге (одинаковый y), но разнесены по x.
    expect(left.position.y).toBe(right.position.y);
    expect(left.position.x).not.toBe(right.position.x);
  });

  it('пустой граф → пустой результат', () => {
    expect(layoutSanad([], [])).toEqual([]);
  });
});
