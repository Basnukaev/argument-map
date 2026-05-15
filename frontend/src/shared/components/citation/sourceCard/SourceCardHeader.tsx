import { BookOpen, Trash2 } from 'lucide-react';
import { Chip } from './Chip';
import { useT } from '@/shared/i18n';

type Props = {
  title: string;
  onDelete?: () => void;
};

/** «📖 chip(library) · {title}» строка с кнопкой удалить */
export function SourceCardHeader({ title, onDelete }: Props) {
  const t = useT();
  return (
    <div className="mb-1.5 flex items-center justify-between gap-2.5">
      <div className="flex min-w-0 items-center gap-2.5">
        <Chip icon={BookOpen}>{t('cite.chip.library')}</Chip>
        <div className="truncate text-sm font-semibold text-ink-900">
          {title}
        </div>
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
