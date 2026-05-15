import { describe, it, expect } from 'vitest';
import {
  EDGE_MATRIX,
  getAllowedEdgeTypes,
  isEdgeAllowed,
  getContextualEdgeLabelKey,
  NODE_TYPE_LABEL,
  EDGE_TYPE_META,
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

describe('getContextualEdgeLabelKey', () => {
  it('EVIDENCE SUPPORTS → edge.label.proves', () => {
    expect(getContextualEdgeLabelKey('EVIDENCE', 'SUPPORTS', 'CLAIM')).toBe('edge.label.proves');
    expect(getContextualEdgeLabelKey('EVIDENCE', 'SUPPORTS', 'ARGUMENT')).toBe('edge.label.proves');
  });

  it('ARGUMENT SUPPORTS CLAIM → edge.label.supports', () => {
    expect(getContextualEdgeLabelKey('ARGUMENT', 'SUPPORTS', 'CLAIM')).toBe('edge.label.supports');
  });

  it('CLAIM SUPPORTS CLAIM → edge.label.agrees', () => {
    expect(getContextualEdgeLabelKey('CLAIM', 'SUPPORTS', 'CLAIM')).toBe('edge.label.agrees');
  });

  it('REFUTES варианты по контексту', () => {
    expect(getContextualEdgeLabelKey('EVIDENCE', 'REFUTES', 'CLAIM')).toBe('edge.label.refutes');
    expect(getContextualEdgeLabelKey('ARGUMENT', 'REFUTES', 'CLAIM')).toBe('edge.label.contradicts');
    expect(getContextualEdgeLabelKey('CLAIM', 'REFUTES', 'CLAIM')).toBe('edge.label.incompatible');
  });

  it('INVALIDATES всегда edge.label.invalidates', () => {
    expect(getContextualEdgeLabelKey('EVIDENCE', 'INVALIDATES', 'ARGUMENT')).toBe('edge.label.invalidates');
    expect(getContextualEdgeLabelKey('ARGUMENT', 'INVALIDATES', 'ARGUMENT')).toBe('edge.label.invalidates');
  });

  it('QUALIFIES: CLAIM→CLAIM narrows, остальное qualifies', () => {
    expect(getContextualEdgeLabelKey('CLAIM', 'QUALIFIES', 'CLAIM')).toBe('edge.label.narrows');
    expect(getContextualEdgeLabelKey('QUESTION', 'QUALIFIES', 'CLAIM')).toBe('edge.label.qualifies');
    expect(getContextualEdgeLabelKey('QUESTION', 'QUALIFIES', 'ARGUMENT')).toBe('edge.label.qualifies');
  });

  it('RESPONDS_TO → edge.label.responds', () => {
    expect(getContextualEdgeLabelKey('CLAIM', 'RESPONDS_TO', 'QUESTION')).toBe('edge.label.responds');
  });
});

describe('маркеры', () => {
  it('NODE_TYPE_LABEL содержит русские метки для всех типов', () => {
    expect(NODE_TYPE_LABEL).toEqual({
      QUESTION: 'Вопрос',
      CLAIM: 'Тезис',
      ARGUMENT: 'Довод',
      EVIDENCE: 'Свид.',
    });
  });

  it('EDGE_TYPE_META содержит Icon, labelKey, hintKey, colorClass для всех типов', () => {
    for (const t of EDGE_TYPES) {
      const meta = EDGE_TYPE_META[t];
      expect(meta).toBeDefined();
      expect(meta.Icon).toBeTruthy();
      expect(meta.labelKey).toBeTruthy();
      expect(meta.hintKey).toBeTruthy();
      expect(meta.colorClass).toMatch(/^text-/);
    }
  });
});
