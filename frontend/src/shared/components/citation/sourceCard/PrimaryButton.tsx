import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { ExternalLink } from 'lucide-react';

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  full?: boolean;
  children: ReactNode;
};

/**
 * "Перейти к источнику" - outline-стиль кнопка в SourceCard.
 *
 * Per design-reference v3 (`TopicGraphPage v3`): кнопка не primary-filled,
 * а **outline** - белый/elevated фон, accent-700 текст, accent-100 рамка.
 * Visual distinction: primary CTA внутри Detail Panel - это уже сама
 * статус-логика узла; переход в библиотеку - secondary action.
 */
export function PrimaryButton({
  full = false,
  children,
  className = '',
  ...rest
}: Props) {
  return (
    <button
      type="button"
      className={
        'inline-flex items-center justify-center gap-2 rounded-sm ' +
        'border border-accent-100 bg-elevated px-3.5 py-2 text-sm font-semibold text-accent-700 ' +
        'transition-colors hover:bg-accent-50 hover:border-accent-500 ' +
        'focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 focus-visible:ring-offset-2 ' +
        (full ? 'w-full ' : '') +
        className
      }
      {...rest}
    >
      <ExternalLink size={14} aria-hidden />
      {children}
    </button>
  );
}
