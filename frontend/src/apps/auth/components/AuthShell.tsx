import type { ReactNode } from 'react';
import { useT } from '@/shared/i18n';

interface Props {
  title: string;
  subtitle?: string;
  children: ReactNode;
  footer?: ReactNode;
}

/**
 * Single auth-page wrapper - hero-style центрированный layout с
 * bismillah brand mark, заголовком и Card-формой. Используется обеими
 * pages (Login / Register) для единообразия.
 *
 * Не использует Header (auth pages умышленно standalone - убирает
 * нав-distraction в момент authentication)
 */
function AuthShell({ title, subtitle, children, footer }: Props) {
  const t = useT();
  return (
    <main className="flex min-h-screen flex-col items-center bg-bg px-4 py-10 sm:py-16">
      <div className="w-full max-w-md">
        <div className="mb-8 flex flex-col items-center text-center">
          <span
            dir="ltr"
            className="inline-flex h-12 w-auto min-w-12 items-center justify-center rounded-md bg-accent-600 px-3 font-arabic text-xl leading-none text-ink-0 shadow-sh1"
          >
            ﷽
          </span>
          <p className="mt-3 text-xs text-ink-500">{t('auth.brand.tagline')}</p>
        </div>
        <div className="rounded-lg border border-border bg-elevated p-6 shadow-sh2 sm:p-8">
          <h1 className="font-serif text-[24px] font-semibold leading-tight text-ink-900">
            {title}
          </h1>
          {subtitle && (
            <p className="mt-1 text-sm text-ink-500">{subtitle}</p>
          )}
          <div className="mt-6">{children}</div>
        </div>
        {footer && (
          <div className="mt-4 text-center text-sm text-ink-600">{footer}</div>
        )}
      </div>
    </main>
  );
}

export default AuthShell;
