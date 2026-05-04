import { describe, it, expect } from 'vitest';
import {
  EDGE_MATRIX,
  getAllowedEdgeTypes,
  isEdgeAllowed,
  getContextualEdgeLabel,
  NODE_TYPE_EMOJI,
  NODE_TYPE_LABEL,
  EDGE_TYPE_ICON,
  type NodeType,
  type EdgeType,
} from './edgeRules';

const NODE_TYPES: NodeType[] = ['QUESTION', 'CLAIM', 'ARGUMENT', 'EVIDENCE'];
const EDGE_TYPES: EdgeType[] = ['SUPPORTS', 'REFUTES', 'QUALIFIES', 'INVALIDATES', 'RESPONDS_TO'];

describe('EDGE_MATRIX (ADR-010)', () => {
  it('повторяет ровно матрицу из ADR-010', () => {
    // выборочные ячейки - матрица большая, прогоняем характерные пары
    expect(EDGE_MATRIX.QUESTION.QUESTION).toEqual(['QUALIFIES']);
    expect(EDGE_MATRIX.QUESTION.EVIDENCE).toEqual([]);
    expect(EDGE_MATRIX.CLAIM.CLAIM).toEqual(['SUPPORTS', 'REFUTES', 'QUALIFIES']);
    expect(EDGE_MATRIX.CLAIM.QUESTION).toEqual(['RESPONDS_TO']);
    expect(EDGE_MATRIX.ARGUMENT.ARGUMENT).toEqual(['INVALIDATES']);
    expect(EDGE_MATRIX.EVIDENCE.ARGUMENT).toEqual(['SUPPORTS', 'REFUTES', 'INVALIDATES']);
    expect(EDGE_MATRIX.EVIDENCE.EVIDENCE).toEqual([]);
  });

  it('isEdgeAllowed согласован с матрицей для всех 80 пар', () => {
    for (const from of NODE_TYPES) {
      for (const to of NODE_TYPES) {
        const allowed = EDGE_MATRIX[from][to];
        for (const edge of EDGE_TYPES) {
          expect(isEdgeAllowed(from, edge, to)).toBe(allowed.includes(edge));
        }
      }
    }
  });

  it('запрещает QUESTION → CLAIM SUPPORTS (вопрос не утверждает)', () => {
    expect(isEdgeAllowed('QUESTION', 'SUPPORTS', 'CLAIM')).toBe(false);
  });

  it('запрещает CLAIM → ARGUMENT (нельзя порождать аргумент из тезиса)', () => {
    expect(getAllowedEdgeTypes('CLAIM', 'ARGUMENT')).toEqual([]);
  });

  it('запрещает EVIDENCE → EVIDENCE (нужен ARGUMENT-посредник)', () => {
    expect(getAllowedEdgeTypes('EVIDENCE', 'EVIDENCE')).toEqual([]);
  });
});

describe('getContextualEdgeLabel', () => {
  it('EVIDENCE SUPPORTS → "доказывает"', () => {
    expect(getContextualEdgeLabel('EVIDENCE', 'SUPPORTS', 'CLAIM')).toBe('доказывает');
    expect(getContextualEdgeLabel('EVIDENCE', 'SUPPORTS', 'ARGUMENT')).toBe('доказывает');
  });

  it('ARGUMENT SUPPORTS CLAIM → "поддерживает"', () => {
    expect(getContextualEdgeLabel('ARGUMENT', 'SUPPORTS', 'CLAIM')).toBe('поддерживает');
  });

  it('CLAIM SUPPORTS CLAIM → "согласуется с"', () => {
    expect(getContextualEdgeLabel('CLAIM', 'SUPPORTS', 'CLAIM')).toBe('согласуется с');
  });

  it('REFUTES варианты по контексту', () => {
    expect(getContextualEdgeLabel('EVIDENCE', 'REFUTES', 'CLAIM')).toBe('опровергает');
    expect(getContextualEdgeLabel('ARGUMENT', 'REFUTES', 'CLAIM')).toBe('противоречит');
    expect(getContextualEdgeLabel('CLAIM', 'REFUTES', 'CLAIM')).toBe('несовместим с');
  });

  it('INVALIDATES всегда "аннулирует"', () => {
    expect(getContextualEdgeLabel('EVIDENCE', 'INVALIDATES', 'ARGUMENT')).toBe('аннулирует');
    expect(getContextualEdgeLabel('ARGUMENT', 'INVALIDATES', 'ARGUMENT')).toBe('аннулирует');
  });

  it('QUALIFIES: CLAIM→CLAIM "сужает", остальное "уточняет"', () => {
    expect(getContextualEdgeLabel('CLAIM', 'QUALIFIES', 'CLAIM')).toBe('сужает');
    expect(getContextualEdgeLabel('QUESTION', 'QUALIFIES', 'CLAIM')).toBe('уточняет');
    expect(getContextualEdgeLabel('QUESTION', 'QUALIFIES', 'ARGUMENT')).toBe('уточняет');
  });

  it('RESPONDS_TO → "отвечает на"', () => {
    expect(getContextualEdgeLabel('CLAIM', 'RESPONDS_TO', 'QUESTION')).toBe('отвечает на');
  });
});

describe('маркеры', () => {
  it('NODE_TYPE_EMOJI определён для всех типов', () => {
    for (const t of NODE_TYPES) {
      expect(NODE_TYPE_EMOJI[t]).toBeTruthy();
    }
  });

  it('NODE_TYPE_LABEL содержит русские метки для всех типов', () => {
    expect(NODE_TYPE_LABEL).toEqual({
      QUESTION: 'Вопрос',
      CLAIM: 'Тезис',
      ARGUMENT: 'Довод',
      EVIDENCE: 'Свид.',
    });
  });

  it('EDGE_TYPE_ICON определён для всех типов', () => {
    for (const t of EDGE_TYPES) {
      expect(EDGE_TYPE_ICON[t]).toBeTruthy();
    }
  });
});
