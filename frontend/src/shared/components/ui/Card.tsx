import type { HTMLAttributes, ReactNode } from 'react';
import { hasArabicScript } from '@/shared/i18n';

/**
 * v2 Card - дискретная content-единица с hover/click affordance.
 *
 * Старое API (просто div-обёртка):
 *   <Card>...</Card>
 *
 * Новое API с namespace для типовых паттернов (BookCard / TopicCard):
 *   <Card onClick={...}>
 *     <Card.Cover color={book.accent}>{book.title[0]}</Card.Cover>
 *     <Card.Body>
 *       <Card.Eyebrow><Chip>BOOK</Chip> AR</Card.Eyebrow>
 *       <Card.Title arabic={book.lang === 'ar'}>{book.title}</Card.Title>
 *       <Card.Meta>{book.author}</Card.Meta>
 *     </Card.Body>
 *   </Card>
 *
 * Для не-интерактивных surfaces (sidebars, detail panels) использовать
 * plain <section>, не Card с interactive=false.
 */
type Props = HTMLAttributes<HTMLDivElement> & {
  selected?: boolean;
  /** Интерактивные карточки получают cursor-pointer + hover state */
  interactive?: boolean;
  children: ReactNode;
};

function Card({
  selected = false,
  interactive = false,
  className = '',
  children,
  ...rest
}: Props) {
  const borderClass = selected
    ? 'border-accent-600 border-[1.5px]'
    : 'border-border';
  const interactiveClass = interactive
    ? 'cursor-pointer hover:border-border-strong transition-colors'
    : '';
  return (
    <div
      className={`bg-elevated rounded-md border shadow-sh1 ${borderClass} ${interactiveClass} ${className}`}
      {...rest}
    >
      {children}
    </div>
  );
}

function CardCover({
  color,
  children,
}: {
  color?: string;
  children?: ReactNode;
}) {
  return (
    <div
      className="aspect-[5/3] grid place-items-center relative overflow-hidden rounded-t-md"
      style={{ background: color || 'var(--c-accent-600)' }}
    >
      <div className="font-serif text-3xl font-semibold text-white/95 tracking-tight relative">
        {children}
      </div>
    </div>
  );
}
Card.Cover = CardCover;

function CardBody({
  children,
  className = '',
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={`p-4 flex flex-col gap-1 ${className}`}>{children}</div>
  );
}
Card.Body = CardBody;

function CardEyebrow({ children }: { children: ReactNode }) {
  return (
    <div className="flex items-center gap-1 text-xs text-ink-500">
      {children}
    </div>
  );
}
Card.Eyebrow = CardEyebrow;

function CardTitle({
  children,
  arabic,
  clamp = false,
}: {
  children: ReactNode;
  /**
   * Если не передан - детектится автоматически из children по
   * Arabic Unicode range. Принципиально: язык контента ≠ локаль UI
   * ≠ field language в DB. Книга может иметь `language='ar'` (оригинал
   * на арабском) но отображаемое имя «Священный Коран» - кириллица.
   * Amiri не имеет глифов для кириллицы → fallback на browser-default
   * serif. Поэтому решаем по фактическому содержимому, не по метаданным.
   */
  arabic?: boolean;
  /**
   * Опт-ин: клампит заголовок ровно в 2 строки (line-clamp-2) и
   * резервирует high под 2 строки (min-height = 2 × fs × lh), чтобы
   * карточки в grid'е имели одинаковую высоту независимо от длины
   * названия (1-строчные и 2-строчные занимают одинаковую вертикаль).
   * Используется в BookListPage. По умолчанию off - другие callers
   * (auto-grow) не затрагиваются.
   */
  clamp?: boolean;
}) {
  const isArabic =
    arabic ?? (typeof children === 'string' && hasArabicScript(children));
  // Параметры из design-reference/v2/project/page-book-list.jsx:201-203.
  // Arabic: font-arabic (Amiri) + 18px + weight 600.
  // Non-arabic: font-serif (Source Serif 4 Variable) + 15px + weight 600
  // + line-height 1.3. font-optical-sizing: auto активирует opsz axis
  // Source Serif 4 - браузер выбирает display vs body cut автоматически.
  const clampClass = clamp ? ' line-clamp-2' : '';
  if (isArabic) {
    const lineHeight = 1.3;
    const fontSize = 18;
    return (
      <h3
        dir="auto"
        className={`text-ink-900 m-0 font-arabic${clampClass}`}
        style={{
          fontSize,
          fontWeight: 600,
          lineHeight,
          ...(clamp ? { minHeight: 2 * fontSize * lineHeight } : null),
        }}
      >
        {children}
      </h3>
    );
  }
  const lineHeight = 1.3;
  const fontSize = 15;
  return (
    <h3
      dir="auto"
      className={`text-ink-900 m-0 font-serif book-title${clampClass}`}
      style={{
        fontSize,
        lineHeight,
        fontOpticalSizing: 'auto',
        ...(clamp ? { minHeight: 2 * fontSize * lineHeight } : null),
      }}
    >
      {children}
    </h3>
  );
}
Card.Title = CardTitle;

function CardMeta({
  children,
  arabic = false,
  className = '',
}: {
  children: ReactNode;
  arabic?: boolean;
  /** Доп. классы - напр. `mt-auto` чтобы прижать meta к низу
   *  flex-колонки Card.Body (равная высота карточек в grid'е). */
  className?: string;
}) {
  const fontClass = arabic ? 'font-arabic text-sm' : '';
  return (
    <div dir="auto" className={`text-xs text-ink-600 ${fontClass} ${className}`}>
      {children}
    </div>
  );
}
Card.Meta = CardMeta;

export default Card;
