import { Loader2 } from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import { useT } from '@/shared/i18n';

interface Props {
  onClick: () => void;
  loading: boolean;
  hasNext: boolean;
  /** Сколько элементов сейчас показано (для строки «Показано N из M»). */
  shownCount?: number;
  /** Всего элементов на бэке. */
  totalCount?: number;
}

/**
 * LoadMoreButton - единая нижняя секция пагинации для list-страниц:
 * центрированная кнопка «Показать ещё» (со спиннером при loading) плюс
 * тонкая строка «Показано N из M».
 *
 * Поведение:
 * - !hasNext → кнопка не рендерится; но если передан totalCount, строка
 *   счётчика всё равно показывается (юзер видит сколько всего загружено).
 * - hasNext → кнопка + счётчик.
 * - ничего не передано (нет totalCount и !hasNext) → рендерит null.
 *
 * Счётчик обёрнут в <bdi dir="ltr"> - цифры всегда LTR даже в RTL-локали.
 */
function LoadMoreButton({
  onClick,
  loading,
  hasNext,
  shownCount,
  totalCount,
}: Props) {
  const t = useT();
  const showCounter = shownCount != null && totalCount != null;
  if (!hasNext && !showCounter) return null;

  const counterText = showCounter
    ? t('common.shown_of')
        .replace('{shown}', String(shownCount))
        .replace('{total}', String(totalCount))
    : null;

  return (
    <div className="mt-6 flex flex-col items-center gap-2">
      {hasNext && (
        <Button
          variant="secondary"
          onClick={onClick}
          disabled={loading}
          icon={loading ? Loader2 : undefined}
        >
          {loading ? t('common.loading') : t('common.load_more')}
        </Button>
      )}
      {counterText && (
        <p className="text-xs text-ink-500">
          <bdi dir="ltr">{counterText}</bdi>
        </p>
      )}
    </div>
  );
}

export default LoadMoreButton;
