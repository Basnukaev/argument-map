import type { ReactNode } from 'react';
import { useLocaleStore } from '@/shared/i18n';
import { Label } from './Label';

type Props = {
  label: string;
  children: ReactNode;
  last?: boolean;
};

/**
 * Строка label/value в карточке метаданных. Формат зависит от локали:
 *
 * **RU-локаль (compact inline, как исламская academic-сноска):**
 *   ```
 *   Автор: Исмаил ибн Касир Дамашки
 *   Год смерти: 774 ﻫ
 *   Тахкик: Сами ибн Мухаммад
 *   ```
 *   label и value прижаты через двоеточие, без табличной структуры
 *
 * **AR-локаль (infobox, как shamela карточка):**
 *   ```
 *   إسماعيل بن عمر بن كثير الدمشقي              المؤلف
 *   774 ﻫ                                    سنة الوفاة
 *   ```
 *   label справа (start-edge в RTL), value слева (end-edge), borders
 *   между строками
 *
 * Выбор: shamela inline-формат естественен для арабской academic-
 * традиции и для русского «сноски ГОСТ-стиля» одновременно. Western
 * infobox - универсальный fallback для AR-локали (где компактная
 * inline-форма уже встроена через RTL-direction)
 */
export function RtlRow({ label, children, last = false }: Props) {
  const locale = useLocaleStore((s) => s.locale);

  if (locale === 'ru') {
    return (
      <div className="flex flex-wrap items-baseline gap-x-1.5 py-0.5 leading-[1.7] text-[13px]">
        <Label className="shrink-0">{label}:</Label>
        <div className="flex min-w-0 flex-wrap items-baseline gap-0.5 text-slate-900">
          {children}
        </div>
      </div>
    );
  }

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
