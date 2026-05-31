import { describe, it, expect } from 'vitest';
import { DICTIONARY } from '@/shared/i18n';

/**
 * Гарантирует, что словарь RU и AR не расходятся: каждый UI-ключ переведён
 * на оба языка, и ни одно значение не пустое. Ловит частую ошибку «добавил
 * ключ в один блок, забыл во второй» (включая будущие фичи).
 */
describe('i18n dictionary parity', () => {
  it('RU и AR имеют идентичный набор ключей', () => {
    const ruKeys = Object.keys(DICTIONARY.ru);
    const arKeys = Object.keys(DICTIONARY.ar);
    const ruSet = new Set(ruKeys);
    const arSet = new Set(arKeys);

    const missingInAr = ruKeys.filter((k) => !arSet.has(k)).sort();
    const missingInRu = arKeys.filter((k) => !ruSet.has(k)).sort();

    // toEqual даёт читаемый diff с конкретными недостающими ключами
    expect({ missingInAr, missingInRu }).toEqual({ missingInAr: [], missingInRu: [] });
  });

  it('нет пустых значений в RU и AR', () => {
    const empties: string[] = [];
    for (const [k, v] of Object.entries(DICTIONARY.ru)) {
      if (typeof v !== 'string' || v.trim() === '') empties.push(`ru:${k}`);
    }
    for (const [k, v] of Object.entries(DICTIONARY.ar)) {
      if (typeof v !== 'string' || v.trim() === '') empties.push(`ar:${k}`);
    }
    expect(empties).toEqual([]);
  });
});
