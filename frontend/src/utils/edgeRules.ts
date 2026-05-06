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
 * Короткие русские метки типов узлов. Должны совпадать с заголовками в
 * `NodeCard` (Вопрос/Тезис/Довод/Свидетельство). Используется в текстовых
 * сообщениях без иконки (toast'ы, alerts). Для UI с иконкой бери
 * NODE_TYPE_META.label - там полноценная метка вместе с lucide-иконкой
 */
export const NODE_TYPE_LABEL: Record<NodeType, string> = {
  QUESTION: 'Вопрос',
  CLAIM: 'Тезис',
  ARGUMENT: 'Довод',
  EVIDENCE: 'Свид.',
};

/**
 * Метаданные типа узла для UI: lucide-иконка совпадает с тем что показывает
 * NodeCard на графе - так пользователь видит один и тот же символ и в карточке,
 * и при выборе типа в модалке. Решает проблему близких эмодзи 📢/💬
 * (Тезис/Довод) в шрифте операционной системы.
 */
export const NODE_TYPE_META: Record<NodeType, { label: string; hint: string; Icon: LucideIcon }> = {
  QUESTION: { label: 'Вопрос', hint: 'Корневой или уточняющий вопрос', Icon: CircleHelp },
  CLAIM: { label: 'Тезис', hint: 'Утверждение которое доказывают', Icon: Megaphone },
  ARGUMENT: { label: 'Довод', hint: 'Аргумент за/против тезиса', Icon: MessageSquareQuote },
  EVIDENCE: { label: 'Свидетельство', hint: 'Хадис, цитата, факт', Icon: FileText },
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
  label: string;
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
        { newNodeType: 'ARGUMENT', edgeType: 'SUPPORTS', direction: 'incoming', label: 'Подтверждающий довод' },
        { newNodeType: 'ARGUMENT', edgeType: 'REFUTES', direction: 'incoming', label: 'Опровергающий довод' },
        { newNodeType: 'EVIDENCE', edgeType: 'SUPPORTS', direction: 'incoming', label: 'Подтверждающее свидетельство' },
        { newNodeType: 'EVIDENCE', edgeType: 'REFUTES', direction: 'incoming', label: 'Опровергающее свидетельство' },
        { newNodeType: 'QUESTION', edgeType: 'QUALIFIES', direction: 'incoming', label: 'Уточняющий вопрос' },
      ];
    case 'ARGUMENT':
      return [
        { newNodeType: 'ARGUMENT', edgeType: 'INVALIDATES', direction: 'incoming', label: 'Аннулирующий довод' },
        { newNodeType: 'EVIDENCE', edgeType: 'INVALIDATES', direction: 'incoming', label: 'Аннулирующее свидетельство' },
      ];
    case 'EVIDENCE':
      // К свидетельству ничего не подключается напрямую (по ADR-010 EVIDENCE -
      // только источник, не target). Меню остаётся базовым: edit/delete
      return [];
    case 'QUESTION':
      return [
        { newNodeType: 'CLAIM', edgeType: 'RESPONDS_TO', direction: 'incoming', label: 'Тезис-ответ' },
        { newNodeType: 'QUESTION', edgeType: 'QUALIFIES', direction: 'incoming', label: 'Уточняющий вопрос' },
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
  { label: string; hint: string; Icon: LucideIcon; colorClass: string }
> = {
  SUPPORTS: { label: 'Поддерживает', hint: 'Аргумент за тезис', Icon: Check, colorClass: 'text-green-600' },
  REFUTES: { label: 'Опровергает', hint: 'Аргумент против', Icon: X, colorClass: 'text-red-600' },
  INVALIDATES: { label: 'Аннулирует', hint: 'Жёсткое мета-опровержение (kill)', Icon: Ban, colorClass: 'text-red-800' },
  QUALIFIES: { label: 'Уточняет', hint: 'Сужает применимость', Icon: ChevronsRight, colorClass: 'text-blue-600' },
  RESPONDS_TO: { label: 'Отвечает', hint: 'Реплика-ответ', Icon: Reply, colorClass: 'text-gray-500' },
};
