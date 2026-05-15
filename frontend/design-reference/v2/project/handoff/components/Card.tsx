import type { HTMLAttributes, ReactNode } from 'react';
import { clsx } from 'clsx';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  selected?: boolean;
  /** Interactive cards get cursor-pointer + hover state. Default true. */
  interactive?: boolean;
}

/**
 * Card — a discrete content unit with hover/click affordance.
 *
 * For non-interactive surfaces (sidebars, detail panels) use a plain
 * <section> instead of forcing a Card with `interactive={false}`.
 *
 * Common sub-components live on the namespace:
 *   <Card>
 *     <Card.Cover>...</Card.Cover>
 *     <Card.Body>
 *       <Card.Eyebrow>BOOK · AR</Card.Eyebrow>
 *       <Card.Title>Title</Card.Title>
 *       <Card.Meta>Author · Year</Card.Meta>
 *     </Card.Body>
 *   </Card>
 */
export function Card({ selected, interactive = true, className, children, ...rest }: CardProps) {
  return (
    <div
      className={clsx(
        'bg-elevated rounded-lg overflow-hidden border',
        selected ? 'border-accent-600 border-[1.5px]' : 'border-border',
        interactive && 'cursor-pointer hover:border-border-strong',
        'flex flex-col',
        className,
      )}
      {...rest}
    >
      {children}
    </div>
  );
}

function CardCover({ color, children }: { color?: string; children?: ReactNode }) {
  return (
    <div
      className="aspect-[5/3] grid place-items-center relative overflow-hidden"
      style={{ background: color || 'var(--c-accent-600)' }}
    >
      <div className="font-serif text-3xl font-semibold text-white/95 tracking-tight relative">
        {children}
      </div>
    </div>
  );
}
Card.Cover = CardCover;

function CardBody({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={clsx('p-4 flex flex-col gap-1', className)}>
      {children}
    </div>
  );
}
Card.Body = CardBody;

function CardEyebrow({ children }: { children: ReactNode }) {
  return <div className="flex items-center gap-1 text-xs text-ink-500">{children}</div>;
}
Card.Eyebrow = CardEyebrow;

function CardTitle({ children, arabic = false }: { children: ReactNode; arabic?: boolean }) {
  return (
    <h3
      dir="auto"
      className={clsx(
        'font-semibold text-ink-900 leading-tight m-0',
        arabic ? 'font-arabic text-md' : 'font-serif text-base',
      )}
    >
      {children}
    </h3>
  );
}
Card.Title = CardTitle;

function CardMeta({ children, arabic = false }: { children: ReactNode; arabic?: boolean }) {
  return (
    <div dir="auto" className={clsx('text-xs text-ink-600', arabic && 'font-arabic text-sm')}>
      {children}
    </div>
  );
}
Card.Meta = CardMeta;
