import type { HTMLAttributes, ReactNode } from 'react';

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
  arabic = false,
}: {
  children: ReactNode;
  arabic?: boolean;
}) {
  // Non-arabic title использует --font-book-title (EB Garamond) - классический
  // Garamond revival с тёплым книжным характером. Для cyrillic (например
  // «Священный Коран») EB Garamond не имеет subset → fallback на
  // Source Serif 4 (тёплый serif с cyrillic). font-weight 500 (medium)
  // вместо 600 чтобы не был «острым жирным» - book titles на корешках
  // обычно набираются в нормальном весе, не bold
  const fontClass = arabic
    ? 'font-arabic text-md font-semibold'
    : 'font-book-title text-md font-medium tracking-normal';
  return (
    <h3 dir="auto" className={`text-ink-900 leading-tight m-0 ${fontClass}`}>
      {children}
    </h3>
  );
}
Card.Title = CardTitle;

function CardMeta({
  children,
  arabic = false,
}: {
  children: ReactNode;
  arabic?: boolean;
}) {
  const fontClass = arabic ? 'font-arabic text-sm' : '';
  return (
    <div dir="auto" className={`text-xs text-ink-600 ${fontClass}`}>
      {children}
    </div>
  );
}
Card.Meta = CardMeta;

export default Card;
