import type { ReactNode } from 'react';
import { Label } from './Label';

type Props = {
  label: string;
  children: ReactNode;
  last?: boolean;
};

/**
 * Строка label/value внутри variant-D карточки (контейнер `dir="rtl"`).
 *
 * Value получает `flex-1` и сидит на start edge → в RTL это **правый
 * край**. Label сидит на end edge → в RTL это **левый край**.
 *
 *   ┌────────────────────────────────────────────┐
 *   │ Label                       value value value│   ← visually
 *   └────────────────────────────────────────────┘
 *
 * `text-align: start` (logical) - cyrillic значения внутри `<Bdi>` читаются
 * LTR, но aligned к правому борту row
 */
export function RtlRow({ label, children, last = false }: Props) {
  return (
    <div
      className={`flex items-baseline justify-between gap-3 py-2 ${
        last ? '' : 'border-b border-slate-100'
      }`}
    >
      <div className="flex min-w-0 flex-1 flex-wrap items-baseline justify-start gap-0.5 text-start leading-[1.6] text-slate-900">
        {children}
      </div>
      <Label className="shrink-0 text-end">{label}</Label>
    </div>
  );
}
