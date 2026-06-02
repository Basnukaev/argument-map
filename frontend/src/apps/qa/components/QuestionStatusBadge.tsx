import { useT } from '@/shared/i18n';
import {
  QUESTION_STATUS_TOKENS,
  type QuestionStatus,
} from '@/apps/qa/utils/statusTokens';

type Size = 'sm' | 'md';

interface Props {
  status: QuestionStatus;
  size?: Size;
  /** Показывать ли native-tooltip с описанием статуса (по умолчанию да). */
  withHint?: boolean;
}

const SIZE_CLASSES: Record<Size, string> = {
  sm: 'h-5 gap-1 px-1.5 text-[11px]',
  md: 'h-6 gap-1.5 px-2 text-xs',
};

const ICON_SIZE: Record<Size, number> = { sm: 11, md: 13 };

/**
 * Бейдж статуса вопроса (OPEN / ANSWERED / CLOSED) с иконкой и
 * tooltip-подсказкой. Единый вид для списка и detail-страницы.
 */
function QuestionStatusBadge({ status, size = 'md', withHint = true }: Props) {
  const t = useT();
  const token = QUESTION_STATUS_TOKENS[status];
  const Icon = token.Icon;
  return (
    <span
      data-testid="question-status-badge"
      data-status={status}
      title={withHint ? t(token.hintKey) : undefined}
      className={`inline-flex items-center rounded-sm font-semibold uppercase tracking-wider ${SIZE_CLASSES[size]} ${token.badge}`}
    >
      <Icon size={ICON_SIZE[size]} aria-hidden />
      {t(token.labelKey)}
    </span>
  );
}

export default QuestionStatusBadge;
