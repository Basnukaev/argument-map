import type { NarratorRole, ReliabilityGrade } from './types';

/**
 * Визуальные токены графа иснада. Цвета держим здесь, локализованные
 * подписи — в словаре (i18n). Арабский термин (ثقة/صدوق/...) показываем
 * как есть — это стандарт науки о хадисах, узнаётся независимо от локали.
 */

export interface ReliabilityToken {
  /** Арабский термин надёжности (на бейдже). */
  ar: string;
  /** Tailwind-классы чипа (фон + текст). */
  chip: string;
  /** Tailwind-класс точки-индикатора (для легенды). */
  dot: string;
}

export const RELIABILITY_TOKENS: Record<ReliabilityGrade, ReliabilityToken> = {
  THIQA: { ar: 'ثقة', chip: 'bg-emerald-100 text-emerald-800', dot: 'bg-emerald-500' },
  SADUQ: { ar: 'صدوق', chip: 'bg-sky-100 text-sky-800', dot: 'bg-sky-500' },
  MAQBUL: { ar: 'مقبول', chip: 'bg-teal-100 text-teal-800', dot: 'bg-teal-500' },
  DAIF: { ar: 'ضعيف', chip: 'bg-amber-100 text-amber-800', dot: 'bg-amber-500' },
  MATRUK: { ar: 'متروك', chip: 'bg-rose-100 text-rose-800', dot: 'bg-rose-500' },
  SAHABI: { ar: 'صحابي', chip: 'bg-violet-100 text-violet-800', dot: 'bg-violet-500' },
  UNKNOWN: { ar: 'مجهول', chip: 'bg-ink-100 text-ink-600', dot: 'bg-ink-400' },
};

/** Цвет верхней полоски карточки узла по роли. */
export const ROLE_STRIP: Record<NarratorRole, string> = {
  PROPHET: 'bg-emerald-400',
  COMPANION: 'bg-violet-400',
  NARRATOR: 'bg-accent-400',
  COLLECTOR: 'bg-amber-400',
  // VERSION-узлы рендерятся отдельным компонентом (без верхней полоски).
  VERSION: 'bg-sky-400',
};

/**
 * Цвет ребра (SVG stroke) по степени достоверности цепи. Hex, т.к.
 * React Flow рисует stroke инлайном, а не Tailwind-классом.
 */
const GRADE_STROKE: Record<string, string> = {
  SAHIH: '#10b981',
  HASAN: '#0ea5e9',
  DAIF: '#f59e0b',
  MAUDU: '#e11d48',
};

export function edgeStroke(grade: string | null): string {
  return (grade && GRADE_STROKE[grade]) || '#94a3b8';
}
