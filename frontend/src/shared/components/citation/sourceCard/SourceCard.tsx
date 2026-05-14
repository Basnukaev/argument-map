import type { components } from '@/shared/api/types';
import { useT } from '@/shared/i18n';
import { Bdi } from './Bdi';
import { CARD_SHELL } from './cardShell';
import { Collapsible } from './Collapsible';
import { FlexValue } from './FlexValue';
import { HijriYear } from './HijriYear';
import { PrimaryButton } from './PrimaryButton';
import { QuoteBlock } from './QuoteBlock';
import { RtlRow } from './RtlRow';
import { SourceCardHeader } from './SourceCardHeader';

type NodeSourceDto = components['schemas']['NodeSourceResponse'];

type Props = {
  link: NodeSourceDto;
  /** Translit / ru title для header (например source.title) - fallback на book.title */
  titleLatin?: string | null;
  onDelete?: () => void;
  onPrimaryAction?: () => void;
  primaryActionLabel?: string;
};

const SEP = (
  <span aria-hidden className="px-1 text-xs text-slate-400">
    ·
  </span>
);

/**
 * Source card - всё к правому борту (variant D из Claude Design handoff).
 *
 * Карточка под `dir="rtl"`. Values (FlexValue) сами выбирают шрифт + bidi
 * isolate по script: arabic → Noto Naskh + lang="ar", latin/cyrillic → Inter
 * в `<bdi dir="ltr">`. Quote блок использует `dir="auto"` → один компонент
 * правильно рендерит arabic, russian и english citations.
 *
 * Header и primary action в LTR-subtree чтобы chip, russian title, кнопка
 * рендерились нормально
 */
export function SourceCard({
  link,
  titleLatin,
  onDelete,
  onPrimaryAction,
  primaryActionLabel,
}: Props) {
  const t = useT();
  const c = link.citation ?? {};
  const { authority, book, muhaqqiq, publisher, publicationPlace, location } = c;

  const headerTitle = titleLatin ?? book?.title ?? '—';

  return (
    <div className={CARD_SHELL} dir="rtl">
      <div dir="ltr">
        <SourceCardHeader title={headerTitle} onDelete={onDelete} />
      </div>

      <QuoteBlock
        part={location?.part ?? null}
        page={location?.printedPage ?? (location?.pageNumber != null ? String(location.pageNumber) : null)}
        quote={link.quote ?? null}
        context={link.context ?? null}
      />

      <Collapsible title={t('cite.label.metadata')}>
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
