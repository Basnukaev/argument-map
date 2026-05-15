import type { ReactNode } from 'react';
import { Label } from './Label';

type Props = {
  label: string;
  children: ReactNode;
  last?: boolean;
};

/**
 * Строка label/value в карточке метаданных. Локаль-aware:
 *
 *   LTR-локаль:  Label                    value value value
 *   RTL-локаль:  value value value                    Label
 *
 * Label на start-edge (LTR=left, RTL=right) - читается первым по
 * direction локали. Value заполняет flex-1 с text-end - притягивается
 * к противоположному, end-edge борту. Так делают Wikipedia infoboxes
 * и обычные key-value metadata списки.
 */
export function RtlRow({ label, children, last = false }: Props) {
  return (
    <div
      className={`flex items-baseline justify-between gap-3 py-2 ${
        last ? '' : 'border-b border-slate-100'
      }`}
    >
      <Label className="shrink-0">{label}</Label>
      <div className="flex min-w-0 flex-1 flex-wrap items-baseline justify-end gap-0.5 text-end leading-[1.6] text-slate-900">
        {children}
      </div>
    </div>
  );
}
