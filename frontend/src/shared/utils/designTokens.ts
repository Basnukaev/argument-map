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
    bar: 'bg-emerald-500',
    bg: 'bg-emerald-50',
    text: 'text-emerald-700',
    badgeBg: 'bg-emerald-100',
    badgeText: 'text-emerald-800',
    badgeBorder: 'border-emerald-200',
    ring: 'ring-emerald-500/30',
    Icon: Check,
  },
  DISPUTED: {
    key: 'DISPUTED',
    labelKey: 'status.DISPUTED',
    bar: 'bg-amber-500',
    bg: 'bg-amber-50',
    text: 'text-amber-700',
    badgeBg: 'bg-amber-100',
    badgeText: 'text-amber-900',
    badgeBorder: 'border-amber-200',
    ring: 'ring-amber-500/30',
    Icon: AlertTriangle,
  },
  REFUTED: {
    key: 'REFUTED',
    labelKey: 'status.REFUTED',
    bar: 'bg-red-500',
    bg: 'bg-red-50',
    text: 'text-red-700',
    badgeBg: 'bg-red-100',
    badgeText: 'text-red-800',
    badgeBorder: 'border-red-200',
    ring: 'ring-red-500/30',
    Icon: XCircle,
  },
  UNVERIFIED: {
    key: 'UNVERIFIED',
    labelKey: 'status.UNVERIFIED',
    bar: 'bg-slate-400',
    bg: 'bg-slate-50',
    text: 'text-slate-600',
    badgeBg: 'bg-slate-100',
    badgeText: 'text-slate-700',
    badgeBorder: 'border-slate-200',
    ring: 'ring-slate-400/30',
    Icon: Circle,
  },
};

export interface NodeTypeToken {
  key: NodeType;
  labelKey: DictKey;
  hintKey: DictKey;
  chipBg: string;
  chipText: string;
  headerGradient: string;
  iconBg: string;
  iconText: string;
  Icon: LucideIcon;
}

export const NODE_TYPE_TOKENS: Record<NodeType, NodeTypeToken> = {
  QUESTION: {
    key: 'QUESTION',
    labelKey: 'node.type.QUESTION',
    hintKey: 'node.type.QUESTION.hint',
    chipBg: 'bg-violet-100',
    chipText: 'text-violet-700',
    headerGradient: 'from-violet-50/70 to-white',
    iconBg: 'bg-violet-100',
    iconText: 'text-violet-700',
    Icon: CircleHelp,
  },
  CLAIM: {
    key: 'CLAIM',
    labelKey: 'node.type.CLAIM',
    hintKey: 'node.type.CLAIM.hint',
    chipBg: 'bg-indigo-100',
    chipText: 'text-indigo-700',
    headerGradient: 'from-indigo-50/70 to-white',
    iconBg: 'bg-indigo-100',
    iconText: 'text-indigo-700',
    Icon: Megaphone,
  },
  ARGUMENT: {
    key: 'ARGUMENT',
    labelKey: 'node.type.ARGUMENT',
    hintKey: 'node.type.ARGUMENT.hint',
    chipBg: 'bg-sky-100',
    chipText: 'text-sky-700',
    headerGradient: 'from-sky-50/70 to-white',
    iconBg: 'bg-sky-100',
    iconText: 'text-sky-700',
    Icon: MessageSquareQuote,
  },
  EVIDENCE: {
    key: 'EVIDENCE',
    labelKey: 'node.type.EVIDENCE',
    hintKey: 'node.type.EVIDENCE.hint',
    chipBg: 'bg-teal-100',
    chipText: 'text-teal-700',
    headerGradient: 'from-teal-50/70 to-white',
    iconBg: 'bg-teal-100',
    iconText: 'text-teal-700',
    Icon: FileText,
  },
};

export interface EdgeTypeToken {
  key: EdgeType;
  labelKey: DictKey;
  stroke: string;
  strokeWidth: number;
  strokeDasharray?: string;
  opacity?: number;
  badgeBg: string;
  badgeText: string;
  badgeBorder: string;
}

export const EDGE_TYPE_TOKENS: Record<EdgeType, EdgeTypeToken> = {
  SUPPORTS: {
    key: 'SUPPORTS',
    labelKey: 'edge.type.SUPPORTS',
    stroke: '#10b981',
    strokeWidth: 2,
    badgeBg: 'bg-emerald-50',
    badgeText: 'text-emerald-700',
    badgeBorder: 'border-emerald-200',
  },
  REFUTES: {
    key: 'REFUTES',
    labelKey: 'edge.type.REFUTES',
    stroke: '#ef4444',
    strokeWidth: 2,
    badgeBg: 'bg-red-50',
    badgeText: 'text-red-700',
    badgeBorder: 'border-red-200',
  },
  INVALIDATES: {
    key: 'INVALIDATES',
    labelKey: 'edge.type.INVALIDATES',
    stroke: '#b91c1c',
    strokeWidth: 3,
    strokeDasharray: '8 4',
    badgeBg: 'bg-red-50',
    badgeText: 'text-red-800',
    badgeBorder: 'border-red-300',
  },
  QUALIFIES: {
    key: 'QUALIFIES',
    labelKey: 'edge.type.QUALIFIES',
    stroke: '#3b82f6',
    strokeWidth: 2,
    badgeBg: 'bg-blue-50',
    badgeText: 'text-blue-700',
    badgeBorder: 'border-blue-200',
  },
  RESPONDS_TO: {
    key: 'RESPONDS_TO',
    labelKey: 'edge.type.RESPONDS_TO',
    stroke: '#94a3b8',
    strokeWidth: 1.5,
    opacity: 0.7,
    badgeBg: 'bg-slate-50',
    badgeText: 'text-slate-600',
    badgeBorder: 'border-slate-200',
  },
};
