import { BookOpen } from 'lucide-react';
import type { components } from '@/shared/api/types';
import { formatShamelaBibliography } from '@/shared/components/reader/utils';

type BookDetail = components['schemas']['BookDetailResponse'];

interface Props {
  book: BookDetail;
  pagesCount: number;
  children?: React.ReactNode;
}

function BookHeader({ book, pagesCount, children }: Props) {
  const isArabic = book.language === 'ar';
  return (
    <div className="mb-4 flex items-start justify-between gap-4">
      <div className="min-w-0 flex-1">
        <div className="mb-1 flex items-center gap-2 text-[11px] uppercase tracking-wide text-slate-500">
          <BookOpen size={12} aria-hidden="true" />
          {book.bookType ?? 'BOOK'}
          <span className="text-slate-300">·</span>
          <span className="font-mono">{pagesCount} стр.</span>
        </div>
        <h1
          className={
            isArabic
              ? 'font-naskh text-[26px] font-bold leading-tight text-slate-900'
              : 'text-[22px] font-bold leading-tight text-slate-900'
          }
          dir={isArabic ? 'rtl' : 'ltr'}
        >
          {book.title ?? '(без названия)'}
        </h1>
        {book.description && (
          <p
            className={
              isArabic
                ? 'book-bibliography mt-2 font-naskh text-[14px] leading-relaxed text-slate-600'
                : 'book-bibliography mt-2 text-[13px] leading-relaxed text-slate-600'
            }
            dir={isArabic ? 'rtl' : 'ltr'}
          >
            {isArabic ? formatShamelaBibliography(book.description) : book.description}
          </p>
        )}
      </div>
      {children && <div className="shrink-0">{children}</div>}
    </div>
  );
}

export default BookHeader;
