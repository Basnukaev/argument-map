import { CircleHelp, CheckCircle2, Lock, type LucideIcon } from 'lucide-react';
import type { DictKey } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

export type QuestionStatus = NonNullable<
  components['schemas']['QuestionResponse']['status']
>;

export interface QuestionStatusToken {
  /** Заливка + текст для «solid» бейджа (active state, активная вкладка). */
  badge: string;
  /** Иконка статуса. */
  Icon: LucideIcon;
  /** Ключ короткой подписи (Открыт / Отвечен / Закрыт). */
  labelKey: DictKey;
  /** Ключ tooltip-подсказки «что означает этот статус». */
  hintKey: DictKey;
}

/**
 * Единые токены статусов Q&A - переиспользуются списком, detail-страницей
 * и status-switcher'ом, чтобы цвета/иконки/подписи не расходились между
 * местами. Палитра семантическая (ok/accent/ink), как и у узлов графа.
 */
export const QUESTION_STATUS_TOKENS: Record<QuestionStatus, QuestionStatusToken> = {
  OPEN: {
    badge: 'bg-ok-100 text-ok-700',
    Icon: CircleHelp,
    labelKey: 'qa.status.OPEN',
    hintKey: 'qa.status.OPEN.hint',
  },
  ANSWERED: {
    badge: 'bg-accent-100 text-accent-700',
    Icon: CheckCircle2,
    labelKey: 'qa.status.ANSWERED',
    hintKey: 'qa.status.ANSWERED.hint',
  },
  CLOSED: {
    badge: 'bg-ink-100 text-ink-600',
    Icon: Lock,
    labelKey: 'qa.status.CLOSED',
    hintKey: 'qa.status.CLOSED.hint',
  },
};

export const QUESTION_STATUS_ORDER: readonly QuestionStatus[] = [
  'OPEN',
  'ANSWERED',
  'CLOSED',
];
