import type { ReactNode } from 'react';
import { Label } from './Label';

type Props = {
  label: string;
  /** Не используется в inline-варианте - оставлен для обратной совместимости вызовов */
  last?: boolean;
  children: ReactNode;
};

/**
 * Строка label/value метаданных в shamela inline формате:
 *
 *   RU:  Автор: Исмаил ибн Касир Дамашки
 *   AR:  إسماعيل بن عمر بن كثير الدمشقي :المؤلف
 *
 * label и value на одной строке через двоеточие. Direction всей строки
 * наследуется от родителя (html.dir по локали). Этот формат совпадает
 * и с арабской academic-сноской (shamela inline), и с ГОСТ-сноской на
 * русском.
 */
export function RtlRow({ label, children }: Props) {
  return (
    <div className="flex flex-wrap items-baseline gap-x-1.5 py-0.5 leading-[1.7] text-sm">
      <Label className="shrink-0">{label}:</Label>
      <div className="flex min-w-0 flex-wrap items-baseline gap-0.5 text-ink-900">
        {children}
      </div>
    </div>
  );
}
