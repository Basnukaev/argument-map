import type { components } from '@/api/types';

type CreateEdgeRequest = components['schemas']['CreateEdgeRequest'];
type CreateNodeRequest = components['schemas']['CreateNodeRequest'];

export type EdgeType = CreateEdgeRequest['edgeType'];
export type NodeType = CreateNodeRequest['nodeType'];

/**
 * Матрица допустимых пар (fromType, edgeType, toType). Источник истины - ADR-010.
 * Должна совпадать с backend EdgeSemantics; рассинхрон ловится тестами.
 */
export const EDGE_MATRIX: Record<NodeType, Record<NodeType, readonly EdgeType[]>> = {
  QUESTION: {
    QUESTION: ['QUALIFIES'],
    CLAIM: ['QUALIFIES'],
    ARGUMENT: ['QUALIFIES'],
    EVIDENCE: [],
  },
  CLAIM: {
    QUESTION: ['RESPONDS_TO'],
    CLAIM: ['SUPPORTS', 'REFUTES', 'QUALIFIES'],
    ARGUMENT: [],
    EVIDENCE: [],
  },
  ARGUMENT: {
    QUESTION: [],
    CLAIM: ['SUPPORTS', 'REFUTES'],
    ARGUMENT: ['INVALIDATES'],
    EVIDENCE: [],
  },
  EVIDENCE: {
    QUESTION: [],
    CLAIM: ['SUPPORTS', 'REFUTES'],
    ARGUMENT: ['SUPPORTS', 'REFUTES', 'INVALIDATES'],
    EVIDENCE: [],
  },
};

export function getAllowedEdgeTypes(fromType: NodeType, toType: NodeType): readonly EdgeType[] {
  return EDGE_MATRIX[fromType][toType];
}

export function isEdgeAllowed(fromType: NodeType, edgeType: EdgeType, toType: NodeType): boolean {
  return getAllowedEdgeTypes(fromType, toType).includes(edgeType);
}

/**
 * Контекстная подпись ребра по тройке (fromType, edgeType, toType) - ADR-010,
 * раздел "Контекстные подписи рёбер". Если конкретного контекста нет,
 * возвращается дефолтный label типа.
 */
export function getContextualEdgeLabel(
  fromType: NodeType,
  edgeType: EdgeType,
  toType: NodeType,
): string {
  if (edgeType === 'SUPPORTS') {
    if (fromType === 'EVIDENCE') return 'доказывает';
    if (fromType === 'ARGUMENT' && toType === 'CLAIM') return 'поддерживает';
    if (fromType === 'CLAIM' && toType === 'CLAIM') return 'согласуется с';
  }
  if (edgeType === 'REFUTES') {
    if (fromType === 'EVIDENCE') return 'опровергает';
    if (fromType === 'ARGUMENT' && toType === 'CLAIM') return 'противоречит';
    if (fromType === 'CLAIM' && toType === 'CLAIM') return 'несовместим с';
  }
  if (edgeType === 'INVALIDATES') return 'аннулирует';
  if (edgeType === 'QUALIFIES') {
    if (fromType === 'CLAIM' && toType === 'CLAIM') return 'сужает';
    return 'уточняет';
  }
  if (edgeType === 'RESPONDS_TO') return 'отвечает на';
  return EDGE_DEFAULT_LABEL[edgeType];
}

const EDGE_DEFAULT_LABEL: Record<EdgeType, string> = {
  SUPPORTS: 'поддерживает',
  REFUTES: 'опровергает',
  INVALIDATES: 'аннулирует',
  QUALIFIES: 'уточняет',
  RESPONDS_TO: 'отвечает',
};

/**
 * Эмодзи для типа узла - используется в местах где нельзя положить SVG-иконку
 * (например, внутри `<option>`). Эмодзи 📢 и 💬 визуально близкие, поэтому
 * рядом всегда показываем `NODE_TYPE_LABEL` для однозначности.
 */
export const NODE_TYPE_EMOJI: Record<NodeType, string> = {
  QUESTION: '❓',
  CLAIM: '📢',
  ARGUMENT: '💬',
  EVIDENCE: '📄',
};

/**
 * Короткие русские метки типов узлов. Должны совпадать с заголовками в
 * `NodeCard` (Вопрос/Тезис/Довод/Свидетельство).
 */
export const NODE_TYPE_LABEL: Record<NodeType, string> = {
  QUESTION: 'Вопрос',
  CLAIM: 'Тезис',
  ARGUMENT: 'Довод',
  EVIDENCE: 'Свид.',
};

/**
 * Юникод-маркер типа ребра - используется на бейджах когда подписи скрыты.
 */
export const EDGE_TYPE_ICON: Record<EdgeType, string> = {
  SUPPORTS: '✓',
  REFUTES: '✗',
  INVALIDATES: '⊗',
  QUALIFIES: '↳',
  RESPONDS_TO: '↩',
};
