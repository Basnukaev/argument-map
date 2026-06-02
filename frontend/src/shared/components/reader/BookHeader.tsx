import { useState } from 'react';
import { BookOpen } from 'lucide-react';
import type { components } from '@/shared/api/types';
import { hasArabicScript, useT, useLocaleStore } from '@/shared/i18n';
import { Bdi, FlexValue, HijriYear, RtlRow } from '@/shared/components/citation/sourceCard';

type BookDetail = components['schemas']['BookDetailResponse'];

interface Props {
  book: BookDetail;
  pagesCount: number;
  children?: React.ReactNode;
}

/**
 * Header страницы чтения книги.
 *
 * Структура:
 * - Topline: book type chip + pages count (следует локали интерфейса)
 * - Title - arabic в font-naskh RTL, latin в LTR (через dir="auto" wrap'ы)
 * - Metadata box: Автор / Год смерти / Тахкик / Издатель · Место / Издание / Год
 *   через RtlRow - локаль-aware: в RU labels слева, values справа; в AR
 *   labels справа, values слева. Wikipedia infobox-style
 *
 * Labels переводятся через useT(). На ar-локали будут «المؤلف / التحقيق /
 * الناشر / الطبعة / السنة» вместо ru аналогов
 */
function BookHeader({ book, pagesCount, children }: Props) {
  const t = useT();
  const locale = useLocaleStore((s) => s.locale);
  const isArabic = book.language === 'ar';
  const title = book.title ?? '(без названия)';
  const titleIsArabic = hasArabicScript(title);

  // Book-type label translatable: «Книга» / «كتاب» / etc
  const bookType = book.bookType ?? 'BOOK';
  const typeKey = `book.type.${bookType}` as const;
  const typeLabel = t(typeKey);

  // Shamela «بطاقة الكتاب» хранит строки через CR (\r) либо CRLF. CSS
  // white-space: pre-line ломает строки только по \n, поэтому
  // нормализуем CR(LF) → LF чтобы каждое поле (الكتاب / رسالة / إعداد /
  // إشراف / العام الجامعي / عدد الصفحات) встало на свою строку как на shamela.
  const descriptionText = book.description?.replace(/\r\n?/g, '\n') ?? null;

  // Structured metadata если хоть одно поле есть. Иначе fallback на
  // raw `description` (shamela bibliography text). Thesis-поля (рисала)
  // тоже считаются structured - для них тоже не показываем raw текст.
  const hasThesisMetadata = Boolean(
    book.thesisDegree ?? book.thesisSupervisor ?? book.thesisInstitution
  );
  const hasStructuredMetadata = Boolean(
    book.authority ?? book.muhaqqiq ?? book.publisher ?? book.publicationPlace ??
    book.editionNumber ?? book.publishedYearHijri ?? book.publishedYearGregorian
  ) || hasThesisMetadata;

  return (
    <div className="mb-4 flex items-start justify-between gap-4">
      {/* Маленький thumbnail реальной обложки (book.coverUrl, напр.
          archive.org). При 404/ошибке загрузки graceful-скрывается
          (onError → не рендерим) - decorative, fallback = просто без него. */}
      {book.coverUrl && <CoverThumb url={book.coverUrl} />}
      <div className="min-w-0 flex-1">
        <div
          className="mb-1 flex items-center gap-2 text-xs uppercase tracking-wide text-ink-500"
          dir={locale === 'ar' ? 'rtl' : 'ltr'}
        >
          <BookOpen size={12} aria-hidden="true" />
          <span>{typeLabel}</span>
          <span className="text-ink-300">·</span>
          <span className="font-mono">
            {pagesCount} {t('book.pages_count_suffix')}
          </span>
        </div>

        <h1
          className={
            titleIsArabic
              ? 'font-naskh text-xl font-bold leading-tight text-ink-900'
              : 'text-lg font-bold leading-tight text-ink-900'
          }
          dir={titleIsArabic ? 'rtl' : 'ltr'}
        >
          {title}
        </h1>

        {hasStructuredMetadata && (
          <div
            className="mt-3 rounded-lg border border-border bg-ink-50/40 px-3.5 py-1.5"
          >
            {book.authority && (
              <RtlRow label={t('cite.label.author')}>
                <FlexValue
                  text={book.authority.fullName ?? book.authority.name}
                  size={14}
                  weight={600}
                />
              </RtlRow>
            )}
            {book.authority?.deathYearHijri != null && (
              <RtlRow label={t('cite.label.death_year')}>
                <HijriYear hijri={book.authority.deathYearHijri} />
              </RtlRow>
            )}
            {book.muhaqqiq && (
              <RtlRow label={t('cite.label.muhaqqiq')}>
                <FlexValue text={book.muhaqqiq.fullName ?? book.muhaqqiq.name} size={13} />
              </RtlRow>
            )}
            {(book.publisher || book.publicationPlace) && (
              <RtlRow label={t('cite.label.publisher')}>
                {/* LTR wrapper - чтобы pair publisher · place читалась в логическом порядке
                    (publisher слева, place справа) внутри RTL контейнера */}
                <span dir="ltr" className="inline-flex items-baseline gap-1">
                  <FlexValue text={book.publisher?.name} />
                  {book.publisher && book.publicationPlace && (
                    <span aria-hidden className="text-xs text-ink-400">·</span>
                  )}
                  <FlexValue text={book.publicationPlace?.name} />
                </span>
              </RtlRow>
            )}
            {book.editionNumber != null && (
              <RtlRow label={t('cite.label.edition')}>
                <Bdi>
                  {book.editionNumber}
                  {t('cite.edition.suffix')}
                </Bdi>
              </RtlRow>
            )}
            {(book.publishedYearHijri != null || book.publishedYearGregorian != null) && (
              <RtlRow label={t('cite.label.year')} last={!hasThesisMetadata}>
                <HijriYear
                  hijri={book.publishedYearHijri}
                  gregorian={book.publishedYearGregorian}
                />
              </RtlRow>
            )}
            {/* Thesis (рисала) rows - для академических диссертаций. Каждое
                поле своей строкой как остальные, НЕ сырым текстом. */}
            {book.thesisDegree && (
              <RtlRow label={t('cite.label.thesis_degree')}>
                <FlexValue text={book.thesisDegree} size={13} />
              </RtlRow>
            )}
            {book.thesisSupervisor && (
              <RtlRow label={t('cite.label.thesis_supervisor')}>
                <FlexValue text={book.thesisSupervisor} size={13} />
              </RtlRow>
            )}
            {book.thesisInstitution && (
              <RtlRow label={t('cite.label.thesis_institution')} last>
                <FlexValue text={book.thesisInstitution} size={13} />
              </RtlRow>
            )}
          </div>
        )}

        {/* Raw bibliography (shamela «بطاقة الكتاب») - ТОЛЬКО fallback когда
            structured-поля пусты. Если парсер извлёк muhaqqiq/publisher/
            edition/year - они уже в metadata-box выше, дублировать сырой
            текст не нужно (был баг дублирования). Цель: наполнять нашу
            таблицу через ShamelaBibliographyParser, а не дампить текст. */}
        {!hasStructuredMetadata && descriptionText && (
          <p
            className={
              isArabic
                ? 'book-bibliography mt-2 whitespace-pre-line font-naskh text-sm leading-relaxed text-ink-600'
                : 'book-bibliography mt-2 whitespace-pre-line text-sm leading-relaxed text-ink-600'
            }
            dir={isArabic ? 'rtl' : 'ltr'}
          >
            {descriptionText}
          </p>
        )}
      </div>
      {children && <div className="shrink-0">{children}</div>}
    </div>
  );
}

/** Decorative cover thumbnail в header'е ридера. onError = книга без
 *  валидной обложки → скрываем (не показываем broken-image иконку). */
function CoverThumb({ url }: { url: string }) {
  const [failed, setFailed] = useState(false);
  if (failed) return null;
  return (
    <img
      src={url}
      alt=""
      className="h-20 w-14 shrink-0 rounded-sm border border-border object-cover shadow-sh1"
      onError={() => setFailed(true)}
    />
  );
}

export default BookHeader;
