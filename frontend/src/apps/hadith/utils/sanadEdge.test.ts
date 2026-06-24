import { describe, it, expect } from 'vitest';
import { visibleTransmissionPhrase } from './sanadEdge';

/**
 * Гейт подписи-формулы ребра (бэклог turuq-graph): пустой/пробельный/null
 * `transmissionPhrase` НЕ должен рисовать чип — иначе пустой бокс с
 * фоном+рамкой+тенью вырождается в «чёрный квадратик» на точках
 * ветвления/слияния в PNG-экспорте turuq-графа. turuq version-/merge-рёбра
 * как раз приходят с null или пустой формулой.
 *
 * Тестируем чистый предикат, а не рендер: React Flow рисует подписи-рёбра
 * через измерение layout, которого нет в jsdom (тот же приём, что в
 * SanadGraph.test — рёбра в jsdom не проверяются по контенту).
 */
describe('visibleTransmissionPhrase', () => {
  it('непустая формула → возвращается как есть (чип рисуется)', () => {
    expect(visibleTransmissionPhrase('حدثنا')).toBe('حدثنا');
    expect(visibleTransmissionPhrase('عن')).toBe('عن');
  });

  it('null → null (чип НЕ рисуется)', () => {
    expect(visibleTransmissionPhrase(null)).toBeNull();
  });

  it('undefined → null (чип НЕ рисуется)', () => {
    expect(visibleTransmissionPhrase(undefined)).toBeNull();
  });

  it('пустая строка → null (turuq version-/merge-ребро без формулы)', () => {
    expect(visibleTransmissionPhrase('')).toBeNull();
  });

  it('только пробелы → null (нет пустого бокса в экспорте)', () => {
    expect(visibleTransmissionPhrase('   ')).toBeNull();
    expect(visibleTransmissionPhrase('\t\n ')).toBeNull();
  });
});
