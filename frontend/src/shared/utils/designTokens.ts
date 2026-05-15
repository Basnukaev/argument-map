import {
  Check,
  AlertTriangle,
  XCircle,
  Circle,
  CircleHelp,
  Megaphone,
  MessageSquareQuote,
  FileText,
  type LucideIcon,
} from 'lucide-react';
import type { components } from '@/shared/api/types';
import type { DictKey } from '@/shared/i18n';

type NodeDto = components['schemas']['NodeResponse'];
type EdgeDto = components['schemas']['EdgeResponse'];

export type NodeStatus = NonNullable<NodeDto['status']>;
export type NodeType = NonNullable<NodeDto['nodeType']>;
export type EdgeType = NonNullable<EdgeDto['edgeType']>;

/**
 * v2 design tokens. Все цвета через семантические Tailwind-classы
 * (ok-/warn-/err-/ink-/accent-/type-abstract-/type-empirical-/edge-*)
 * которые в свою очередь резолвятся через CSS-переменные --c-* и
 * автоматически переключаются на [data-theme="dark"].
 *
 * Токены содержат `labelKey` (DictKey) вместо строки label - чтобы
 * UI-компоненты переводили через useT() на текущую локаль.
 */
export interface StatusToken {
  key: NodeStatus;
  labelKey: DictKey;
  bar: string;
  bg: string;
  text: string;
  badgeBg: string;
  badgeText: string;
  badgeBorder: string;
  ring: string;
  Icon: LucideIcon;
}

export const STATUS_TOKENS: Record<NodeStatus, StatusToken> = {
  STANDING: {
    key: 'STANDING',
    labelKey: 'status.STANDING',
    bar: 'bg-ok-500',
    bg: 'bg-ok-100',
    text: 'text-ok-700',
    badgeBg: 'bg-ok-100',
    badgeText: 'text-ok-700',
    badgeBorder: 'border-ok-500/30',
    ring: 'ring-ok-500/30',
    Icon: Check,
  },
  DISPUTED: {
    key: 'DISPUTED',
    labelKey: 'status.DISPUTED',
    bar: 'bg-warn-500',
    bg: 'bg-warn-100',
    text: 'text-warn-700',
    badgeBg: 'bg-warn-100',
    badgeText: 'text-warn-700',
    badgeBorder: 'border-warn-500/30',
    ring: 'ring-warn-500/30',
    Icon: AlertTriangle,
  },
  REFUTED: {
    key: 'REFUTED',
    labelKey: 'status.REFUTED',
    bar: 'bg-err-500',
    bg: 'bg-err-100',
    text: 'text-err-700',
    badgeBg: 'bg-err-100',
    badgeText: 'text-err-700',
    badgeBorder: 'border-err-500/30',
    ring: 'ring-err-500/30',
    Icon: XCircle,
  },
  UNVERIFIED: {
    key: 'UNVERIFIED',
    labelKey: 'status.UNVERIFIED',
    bar: 'bg-ink-400',
    bg: 'bg-ink-100',
    text: 'text-ink-600',
    badgeBg: 'bg-ink-100',
    badgeText: 'text-ink-600',
    badgeBorder: 'border-ink-300',
    ring: 'ring-ink-400/30',
    Icon: Circle,
  },
};

export interface NodeTypeToken {
  key: NodeType;
  labelKey: DictKey;
  hintKey: DictKey;
  chipBg: string;
  chipText: string;
  /** Solid background для panel header (вместо градиента из v1) */
  headerBg: string;
  iconBg: string;
  iconText: string;
  Icon: LucideIcon;
}

/**
 * Per v2 design system: QUESTION/CLAIM/ARGUMENT - "abstract" type family,
 * EVIDENCE - "empirical" type family. Различие концептуальное (теоретическое
 * утверждение vs наблюдение), и оно отражено в цвете chip.
 * Иконка остаётся per-тип для visual cue.
 */
export const NODE_TYPE_TOKENS: Record<NodeType, NodeTypeToken> = {
  QUESTION: {
    key: 'QUESTION',
    labelKey: 'node.type.QUESTION',
    hintKey: 'node.type.QUESTION.hint',
    chipBg: 'bg-type-abstract-bg',
    chipText: 'text-type-abstract-fg',
    headerBg: 'bg-type-abstract-bg',
    iconBg: 'bg-type-abstract-bg',
    iconText: 'text-type-abstract-fg',
    Icon: CircleHelp,
  },
  CLAIM: {
    key: 'CLAIM',
    labelKey: 'node.type.CLAIM',
    hintKey: 'node.type.CLAIM.hint',
    chipBg: 'bg-type-abstract-bg',
    chipText: 'text-type-abstract-fg',
    headerBg: 'bg-type-abstract-bg',
    iconBg: 'bg-type-abstract-bg',
    iconText: 'text-type-abstract-fg',
    Icon: Megaphone,
  },
  ARGUMENT: {
    key: 'ARGUMENT',
    labelKey: 'node.type.ARGUMENT',
    hintKey: 'node.type.ARGUMENT.hint',
    chipBg: 'bg-type-abstract-bg',
    chipText: 'text-type-abstract-fg',
    headerBg: 'bg-type-abstract-bg',
    iconBg: 'bg-type-abstract-bg',
    iconText: 'text-type-abstract-fg',
    Icon: MessageSquareQuote,
  },
  EVIDENCE: {
    key: 'EVIDENCE',
    labelKey: 'node.type.EVIDENCE',
    hintKey: 'node.type.EVIDENCE.hint',
    chipBg: 'bg-type-empirical-bg',
    chipText: 'text-type-empirical-fg',
    headerBg: 'bg-type-empirical-bg',
    iconBg: 'bg-type-empirical-bg',
    iconText: 'text-type-empirical-fg',
    Icon: FileText,
  },
};

export interface EdgeTypeToken {
  key: EdgeType;
  labelKey: DictKey;
  /** CSS var name для stroke - резолвится в React Flow runtime */
  stroke: string;
  strokeWidth: number;
  strokeDasharray?: string;
  opacity?: number;
  badgeBg: string;
  badgeText: string;
  badgeBorder: string;
}

/**
 * Edge strokes используются как `style={{ stroke: token.stroke }}` в
 * React Flow. CSS-var (var(--c-edge-supports)) подхватывает тему.
 */
export const EDGE_TYPE_TOKENS: Record<EdgeType, EdgeTypeToken> = {
  SUPPORTS: {
    key: 'SUPPORTS',
    labelKey: 'edge.type.SUPPORTS',
    stroke: 'var(--c-edge-supports)',
    strokeWidth: 2,
    badgeBg: 'bg-edge-supports-bg',
    badgeText: 'text-edge-supports',
    badgeBorder: 'border-edge-supports/30',
  },
  REFUTES: {
    key: 'REFUTES',
    labelKey: 'edge.type.REFUTES',
    stroke: 'var(--c-edge-refutes)',
    strokeWidth: 2,
    badgeBg: 'bg-edge-refutes-bg',
    badgeText: 'text-edge-refutes',
    badgeBorder: 'border-edge-refutes/30',
  },
  INVALIDATES: {
    key: 'INVALIDATES',
    labelKey: 'edge.type.INVALIDATES',
    stroke: 'var(--c-edge-refutes)',
    strokeWidth: 3,
    strokeDasharray: '8 4',
    badgeBg: 'bg-edge-refutes-bg',
    badgeText: 'text-edge-refutes',
    badgeBorder: 'border-edge-refutes/40',
  },
  QUALIFIES: {
    key: 'QUALIFIES',
    labelKey: 'edge.type.QUALIFIES',
    stroke: 'var(--c-edge-qualifies)',
    strokeWidth: 2,
    badgeBg: 'bg-edge-qualifies-bg',
    badgeText: 'text-edge-qualifies',
    badgeBorder: 'border-edge-qualifies/30',
  },
  RESPONDS_TO: {
    key: 'RESPONDS_TO',
    labelKey: 'edge.type.RESPONDS_TO',
    stroke: 'var(--c-edge-responds)',
    strokeWidth: 1.5,
    opacity: 0.7,
    badgeBg: 'bg-edge-responds-bg',
    badgeText: 'text-edge-responds',
    badgeBorder: 'border-edge-responds/30',
  },
};
