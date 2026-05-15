import {
  CircleHelp,
  Megaphone,
  MessageSquareQuote,
  FileText,
  Check,
  X,
  Ban,
  ChevronsRight,
  Reply,
  type LucideIcon,
} from 'lucide-react';
import type { components } from '@/shared/api/types';
import type { DictKey } from '@/shared/i18n';

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
 * раздел "Контекстные подписи рёбер". Возвращает DictKey для перевода в
 * месте вызова через useT() - поэтому функция остаётся чистой и тестируется
 * на стороне ключей. Если конкретного контекста нет - дефолтный ключ типа.
 */
export function getContextualEdgeLabelKey(
  fromType: NodeType,
  edgeType: EdgeType,
  toType: NodeType,
): DictKey {
  if (edgeType === 'SUPPORTS') {
    if (fromType === 'EVIDENCE') return 'edge.label.proves';
    if (fromType === 'ARGUMENT' && toType === 'CLAIM') return 'edge.label.supports';
    if (fromType === 'CLAIM' && toType === 'CLAIM') return 'edge.label.agrees';
  }
  if (edgeType === 'REFUTES') {
    if (fromType === 'EVIDENCE') return 'edge.label.refutes';
    if (fromType === 'ARGUMENT' && toType === 'CLAIM') return 'edge.label.contradicts';
    if (fromType === 'CLAIM' && toType === 'CLAIM') return 'edge.label.incompatible';
  }
  if (edgeType === 'INVALIDATES') return 'edge.label.invalidates';
  if (edgeType === 'QUALIFIES') {
    if (fromType === 'CLAIM' && toType === 'CLAIM') return 'edge.label.narrows';
    return 'edge.label.qualifies';
  }
  if (edgeType === 'RESPONDS_TO') return 'edge.label.responds';
  return EDGE_DEFAULT_LABEL_KEY[edgeType];
}

const EDGE_DEFAULT_LABEL_KEY: Record<EdgeType, DictKey> = {
  SUPPORTS: 'edge.label.supports',
  REFUTES: 'edge.label.refutes',
  INVALIDATES: 'edge.label.invalidates',
  QUALIFIES: 'edge.label.qualifies',
  RESPONDS_TO: 'edge.label.responds_short',
};

/**
 * Метаданные типа узла для UI: lucide-иконка совпадает с тем что показывает
 * NodeCard на графе - так пользователь видит один и тот же символ и в карточке,
 * и при выборе типа в модалке. Решает проблему близких эмодзи 📢/💬
 * (Тезис/Довод) в шрифте операционной системы.
 *
 * Лейблы/подсказки - через DictKey, переводятся в render через useT().
 * Для коротких меток (toast/alert без иконки) тот же labelKey - в RU
 * это «Вопрос/Тезис/Довод/Свидетельство», в AR соответствующие переводы.
 */
export const NODE_TYPE_META: Record<NodeType, { labelKey: DictKey; hintKey: DictKey; Icon: LucideIcon }> = {
  QUESTION: { labelKey: 'node.type.QUESTION', hintKey: 'node.type.QUESTION.hint', Icon: CircleHelp },
  CLAIM: { labelKey: 'node.type.CLAIM', hintKey: 'node.type.CLAIM.hint', Icon: Megaphone },
  ARGUMENT: { labelKey: 'node.type.ARGUMENT', hintKey: 'node.type.ARGUMENT.hint', Icon: MessageSquareQuote },
  EVIDENCE: { labelKey: 'node.type.EVIDENCE', hintKey: 'node.type.EVIDENCE.hint', Icon: FileText },
};

/**
 * Опция "Добавить связанный узел" в контекстном меню. Описывает что именно
 * создать вокруг текущего узла. direction='incoming' значит новый узел
 * становится from в новом ребре (anchor=to), 'outgoing' - наоборот
 */
export interface RelatedNodeOption {
  newNodeType: NodeType;
  edgeType: EdgeType;
  direction: 'incoming' | 'outgoing';
  labelKey: DictKey;
}

/**
 * Контекстные пункты "Добавить ..." для правого клика по узлу. Возвращает
 * самые осмысленные варианты с учётом матрицы ADR-010 - не полный
 * декартов произведение, а ручной curated список под обычные паттерны
 * аргументации (за/против, мета, уточнение)
 */
export function getRelatedNodeOptions(anchorType: NodeType): readonly RelatedNodeOption[] {
  switch (anchorType) {
    case 'CLAIM':
      return [
        { newNodeType: 'ARGUMENT', edgeType: 'SUPPORTS', direction: 'incoming', labelKey: 'related.supports_argument' },
        { newNodeType: 'ARGUMENT', edgeType: 'REFUTES', direction: 'incoming', labelKey: 'related.refutes_argument' },
        { newNodeType: 'EVIDENCE', edgeType: 'SUPPORTS', direction: 'incoming', labelKey: 'related.supports_evidence' },
        { newNodeType: 'EVIDENCE', edgeType: 'REFUTES', direction: 'incoming', labelKey: 'related.refutes_evidence' },
        { newNodeType: 'QUESTION', edgeType: 'QUALIFIES', direction: 'incoming', labelKey: 'related.qualifies_question' },
      ];
    case 'ARGUMENT':
      return [
        { newNodeType: 'ARGUMENT', edgeType: 'INVALIDATES', direction: 'incoming', labelKey: 'related.invalidates_argument' },
        { newNodeType: 'EVIDENCE', edgeType: 'INVALIDATES', direction: 'incoming', labelKey: 'related.invalidates_evidence' },
      ];
    case 'EVIDENCE':
      // К свидетельству ничего не подключается напрямую (по ADR-010 EVIDENCE -
      // только источник, не target). Меню остаётся базовым: edit/delete
      return [];
    case 'QUESTION':
      return [
        { newNodeType: 'CLAIM', edgeType: 'RESPONDS_TO', direction: 'incoming', labelKey: 'related.responds_claim' },
        { newNodeType: 'QUESTION', edgeType: 'QUALIFIES', direction: 'incoming', labelKey: 'related.qualifies_question' },
      ];
    default:
      return [];
  }
}

/**
 * Метаданные типа ребра для UI: lucide-иконка + tailwind-цвет совпадают с
 * CustomEdge (стрелки на графе). Используется в AddEdgeModal radio-list и
 * EdgeDetailsPanel header / edit-режим
 */
export const EDGE_TYPE_META: Record<
  EdgeType,
  { labelKey: DictKey; hintKey: DictKey; Icon: LucideIcon; colorClass: string }
> = {
  SUPPORTS: { labelKey: 'edge.type.SUPPORTS', hintKey: 'edge.type.SUPPORTS.hint', Icon: Check, colorClass: 'text-green-600' },
  REFUTES: { labelKey: 'edge.type.REFUTES', hintKey: 'edge.type.REFUTES.hint', Icon: X, colorClass: 'text-red-600' },
  INVALIDATES: { labelKey: 'edge.type.INVALIDATES', hintKey: 'edge.type.INVALIDATES.hint', Icon: Ban, colorClass: 'text-red-800' },
  QUALIFIES: { labelKey: 'edge.type.QUALIFIES', hintKey: 'edge.type.QUALIFIES.hint', Icon: ChevronsRight, colorClass: 'text-blue-600' },
  RESPONDS_TO: { labelKey: 'edge.type.RESPONDS_TO', hintKey: 'edge.type.RESPONDS_TO.hint', Icon: Reply, colorClass: 'text-gray-500' },
};
