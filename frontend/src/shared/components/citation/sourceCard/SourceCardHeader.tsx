import { BookOpen, Trash2 } from 'lucide-react';
import { Chip } from './Chip';
import { useT } from '@/shared/i18n';

type Props = {
  title: string;
  onDelete?: () => void;
  /** Если передан - title рендерится как кнопка открывающая
   *  `SourceDetailPanel` (или другой callback). Без обработчика - inert div */
  onTitleClick?: () => void;
};

/** «📖 chip(library) · {title}» строка с кнопкой удалить.
 *  Title опционально clickable - открывает SourceDetailPanel */
export function SourceCardHeader({ title, onDelete, onTitleClick }: Props) {
  const t = useT();
  return (
    <div className="mb-1.5 flex items-center justify-between gap-2.5">
      <div className="flex min-w-0 items-center gap-2.5">
        <Chip icon={BookOpen}>{t('cite.chip.library')}</Chip>
        {onTitleClick ? (
          <button
            type="button"
            onClick={onTitleClick}
            className="truncate rounded text-start text-sm font-semibold text-ink-900 transition-colors hover:text-accent-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
          >
            {title}
          </button>
        ) : (
          <div className="truncate text-sm font-semibold text-ink-900">
            {title}
          </div>
        )}
      </div>
      {onDelete && (
        <button
          type="button"
          aria-label={t('cite.action.detach')}
          onClick={onDelete}
          className="rounded p-1 text-ink-400 transition-colors hover:bg-err-100 hover:text-err-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
        >
          <Trash2 size={13} aria-hidden />
        </button>
      )}
    </div>
  );
}
