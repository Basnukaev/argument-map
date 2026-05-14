import type { ReactNode } from 'react';

type Props = {
  children: ReactNode;
  className?: string;
};

/**
 * Bidi isolate для LTR-фрагментов внутри RTL-контейнера. Latin/cyrillic
 * текст внутри читается слева-направо, но позиция в RTL-потоке - у
 * правого борта (variant D «всё к правому борту»).
 */
export function Bdi({ children, className = '' }: Props) {
  return (
    <bdi dir="ltr" className={`text-[13px] text-slate-900 ${className}`}>
      {children}
    </bdi>
  );
}
