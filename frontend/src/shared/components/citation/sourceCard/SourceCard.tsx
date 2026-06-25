import type { components } from '@/shared/api/types';
import { useT } from '@/shared/i18n';
import { Bdi } from './Bdi';
import { CARD_SHELL } from './cardShell';
import { Collapsible } from './Collapsible';
import { FlexValue } from './FlexValue';
import { HadithGradesSection } from './HadithGradesSection';
import { HijriYear } from './HijriYear';
import { PrimaryButton } from './PrimaryButton';
import { QuoteBlock } from './QuoteBlock';
import { RtlRow } from './RtlRow';
import { SourceCardHeader } from './SourceCardHeader';

/**
 * Минимальный contract для renderable citation link. Структурно
 * совместим с {@code NodeSourceResponse} и {@code QuestionSourceResponse}
 * (и любым будущим *SourceResponse, например для answers). Используется
 * вместо двойного type cast `as unknown as NodeSourceResponse`.
 */
export type SourceCardLink = {
  citation?: components['schemas']['CitationResponse'] | null;
  quote?: string | null;
  context?: string | null;
};

type Props = {
  link: SourceCardLink;
  /** Translit / ru title для header (например source.title) - fallback на book.title */
  titleLatin?: string | null;
  onDelete?: () => void;
  onPrimaryAction?: () => void;
  primaryActionLabel?: string;
  /** Если передан - title в header'е становится button, click открывает
   *  `SourceDetailPanel`. Caller отвечает за вызов `useSourceDetailPanelStore.openWith` */
  onTitleClick?: () => void;
  /** Опционально - id и тип source. Если sourceType === 'HADITH' - под
   *  метаданными показывается секция multi-grading оценок учёных */
  sourceId?: string | null;
  sourceType?: string | null;
};

const SEP = (
  <span aria-hidden className="px-1 text-xs text-ink-400">
    ·
  </span>
);

/**
 * Source card - layout локаль-aware (Wikipedia infobox style).
 *
 * Карточка наследует direction от html.dir (локаль интерфейса). RtlRow
 * кладёт label на start-edge, value на end-edge - в LTR это «label слева,
 * value справа», в RTL «value слева, label справа». Values (FlexValue)
 * сами выбирают шрифт + bidi isolate по script: arabic → Noto Naskh +
 * lang="ar", latin/cyrillic → Inter в `<bdi dir="ltr">`. Quote блок -
 * `dir="auto"`, один компонент работает для arabic, russian и english.
 */
export function SourceCard({
  link,
  titleLatin,
  onDelete,
  onPrimaryAction,
  primaryActionLabel,
  onTitleClick,
  sourceId,
  sourceType,
}: Props) {
  const t = useT();
  const c = link.citation ?? {};
  const { authority, book, muhaqqiq, publisher, publicationPlace, location, pdf } = c;

  const headerTitle = titleLatin ?? book?.title ?? '—';

  // LocationRef заполнен только для TEXT-цитат (pageId). Для PDF/PDF_LINK
  // (ADR-067, FILE_ONLY книги archive.org) локатор живёт в PdfRef — страница
  // в pdf.pageNumber, том в pdf.fileIndex (0-based ordinal), bbox = выделенная
  // область. См. DtoMappers.toLocationRef/toPdfRef.
  const page =
    location?.printedPage ??
    (location?.pageNumber != null
      ? String(location.pageNumber)
      : pdf?.pageNumber != null
        ? String(pdf.pageNumber)
        : null);
  // ВНИМАНИЕ: pdf.fileIndex — сырой 0-based ordinal файла в книге (cover может
  // быть index 0). PdfViewer пересчитывает «Том N» по filename-like файлам без
  // cover, поэтому здесь показываем индекс как есть. Точная навигация — через
  // deep-link (onPrimaryAction), он передаёт fileIndex напрямую.
  const volume = !location && pdf?.fileIndex != null ? String(pdf.fileIndex) : null;
  const hasRegion = !location && pdf?.bbox != null;

  return (
    <div className={CARD_SHELL}>
      <SourceCardHeader title={headerTitle} onDelete={onDelete} onTitleClick={onTitleClick} />

      <QuoteBlock
        part={location?.part ?? null}
        page={page}
        volume={volume}
        hasRegion={hasRegion}
        quote={link.quote ?? null}
        context={link.context ?? null}
      />

      <Collapsible title={t('cite.label.metadata')} defaultOpen>
        {authority && (
          <RtlRow label={t('cite.label.author')}>
            <FlexValue text={authority.fullName ?? authority.name} size={15} weight={600} />
          </RtlRow>
        )}

        {authority?.deathYearHijri != null && (
          <RtlRow label={t('cite.label.death_year')}>
            <HijriYear hijri={authority.deathYearHijri} />
          </RtlRow>
        )}

        {book && (
          <RtlRow label={t('cite.label.title')}>
            <FlexValue text={book.title} size={16} weight={700} />
          </RtlRow>
        )}

        {muhaqqiq && (
          <RtlRow label={t('cite.label.muhaqqiq')}>
            <FlexValue text={muhaqqiq.fullName ?? muhaqqiq.name} size={14} />
          </RtlRow>
        )}

        {(publisher || publicationPlace) && (
          <RtlRow label={t('cite.label.publisher')}>
            {/* LTR wrapper - pair publisher · place в логическом порядке внутри RTL row */}
            <span dir="ltr" className="inline-flex items-baseline gap-1">
              <FlexValue text={publisher?.name} />
              {publisher && publicationPlace && SEP}
              <FlexValue text={publicationPlace?.name} />
            </span>
          </RtlRow>
        )}

        {book?.editionNumber != null && (
          <RtlRow label={t('cite.label.edition')}>
            <Bdi>
              {book.editionNumber}
              {t('cite.edition.suffix')}
            </Bdi>
          </RtlRow>
        )}

        {(book?.publishedYearHijri != null || book?.publishedYearGregorian != null) && (
          <RtlRow label={t('cite.label.year')} last>
            <HijriYear
              hijri={book?.publishedYearHijri}
              gregorian={book?.publishedYearGregorian}
            />
          </RtlRow>
        )}
      </Collapsible>

      {sourceId && sourceType === 'HADITH' && (
        <HadithGradesSection sourceId={sourceId} sourceType={sourceType} />
      )}

      {onPrimaryAction && (
        <div className="mt-3" dir="ltr">
          <PrimaryButton full onClick={onPrimaryAction}>
            {primaryActionLabel ?? t('cite.action.gotoSource')}
          </PrimaryButton>
        </div>
      )}
    </div>
  );
}
