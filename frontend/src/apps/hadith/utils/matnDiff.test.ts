import { describe, it, expect } from 'vitest';
import { wordDiff, hasWordDiff } from './matnDiff';

describe('wordDiff', () => {
  it('идентичные строки → всё same', () => {
    const ops = wordDiff('بسم الله الرحمن', 'بسم الله الرحمن');
    expect(ops.every((o) => o.type === 'same')).toBe(true);
    expect(ops.map((o) => o.text)).toEqual(['بسم', 'الله', 'الرحمن']);
  });

  it('замена слова → add (вариант) + del (база), общие same', () => {
    const ops = wordDiff('الأعمال بالنيات', 'الأعمال بالنية');
    const adds = ops.filter((o) => o.type === 'add').map((o) => o.text);
    const dels = ops.filter((o) => o.type === 'del').map((o) => o.text);
    const sames = ops.filter((o) => o.type === 'same').map((o) => o.text);
    expect(sames).toContain('الأعمال');
    expect(adds).toContain('بالنية');
    expect(dels).toContain('بالنيات');
  });

  it('различие только в огласовках → считается same', () => {
    const ops = wordDiff('كَتَبَ', 'كتب');
    expect(ops).toHaveLength(1);
    expect(ops[0]!.type).toBe('same');
  });

  it('добавленное в конце слово → add', () => {
    const ops = wordDiff('بسم الله', 'بسم الله الرحمن');
    expect(ops.filter((o) => o.type === 'add').map((o) => o.text)).toEqual(['الرحمن']);
  });

  it('пустые входы обрабатываются', () => {
    expect(wordDiff('', '')).toEqual([]);
    expect(wordDiff('', 'بسم').map((o) => o.type)).toEqual(['add']);
    expect(wordDiff('بسم', '').map((o) => o.type)).toEqual(['del']);
  });

  it('hasWordDiff: true при словесном расхождении, false при идентичности и отличии лишь в огласовках', () => {
    expect(hasWordDiff('الأعمال بالنيات', 'الأعمال بالنية')).toBe(true);
    expect(hasWordDiff('بسم الله', 'بسم الله')).toBe(false);
    expect(hasWordDiff('كَتَبَ', 'كتب')).toBe(false);
  });
});
