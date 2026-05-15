import { clsx } from 'clsx';

export interface Chapter {
  id: string;
  title: string;
  page: number;
  current?: boolean;
  children?: Chapter[];
}

/**
 * ChapterTree — recursive collapsible chapter list.
 *
 *   <ChapterTree
 *     chapters={book.chapters}
 *     arabicFont={book.language === 'ar'}
 *     onClick={(c) => navigate(`/books/${id}?page=${c.page}`)}
 *   />
 *
 * The current chapter is highlighted with an inset left accent and the
 * accent text color. Indentation per depth handled internally.
 */
export function ChapterTree({
  chapters,
  arabicFont = false,
  depth = 0,
  onClick,
}: {
  chapters: Chapter[];
  arabicFont?: boolean;
  depth?: number;
  onClick?: (chapter: Chapter) => void;
}) {
  return (
    <ul className="list-none m-0 p-0">
      {chapters.map((c) => {
        const isCurrent = c.current;
        return (
          <li key={c.id}>
            <button
              type="button"
              onClick={() => onClick?.(c)}
              style={{ paddingInlineStart: 12 + depth * 16, paddingInlineEnd: 12 }}
              className={clsx(
                'w-full flex items-baseline justify-between gap-3 py-1 text-start',
                'border-s-2',
                arabicFont ? (depth === 0 ? 'text-sm font-arabic' : 'text-[13px] font-arabic') : (depth === 0 ? 'text-[13px]' : 'text-xs'),
                depth === 0 ? 'font-medium' : 'font-normal',
                arabicFont ? 'leading-[1.55]' : 'leading-snug',
                isCurrent
                  ? 'bg-accent-50 text-accent-700 border-s-accent-600'
                  : 'border-s-transparent hover:bg-ink-50 ' + (depth === 0 ? 'text-ink-900' : 'text-ink-700'),
              )}
            >
              <span dir="auto" className="truncate">{c.title}</span>
              <span className="font-mono text-[10px] text-ink-400 flex-none">{c.page}</span>
            </button>
            {c.children && (
              <ChapterTree
                chapters={c.children}
                arabicFont={arabicFont}
                depth={depth + 1}
                onClick={onClick}
              />
            )}
          </li>
        );
      })}
    </ul>
  );
}
