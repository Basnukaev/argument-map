import { useRef, useState } from 'react';
import { MoreVertical } from 'lucide-react';
import IconButton from '@/shared/components/ui/IconButton';
import ContextMenu, { type ContextMenuItem } from '@/shared/components/ui/ContextMenu';

interface Props {
  /** Пункты меню (тот же контракт что у ContextMenu). */
  items: ContextMenuItem[];
  /** a11y-подпись на кнопке-триггере (kebab). */
  label: string;
  size?: 'sm' | 'md' | 'lg';
}

/**
 * Кнопка-«kebab» (три точки), открывающая выпадающее меню действий.
 * Переиспользует {@link ContextMenu} (Escape / клик-вне / стили), но
 * позиционирует меню относительно кнопки-триггера, а не курсора - для
 * «overflow actions» в шапках/карточках без захламления интерфейса.
 */
function OverflowMenu({ items, label, size = 'md' }: Props) {
  const triggerRef = useRef<HTMLButtonElement>(null);
  const [pos, setPos] = useState<{ x: number; y: number } | null>(null);

  function open() {
    const rect = triggerRef.current?.getBoundingClientRect();
    if (!rect) return;
    // Привязываем правый край меню к правому краю кнопки. ContextMenu
    // рендерится через left/top, поэтому даём приблизительный сдвиг
    // влево на типовую ширину меню; для RTL логика остаётся корректной,
    // т.к. координаты в viewport-пространстве, а меню min-w-44 (~176px).
    setPos({ x: rect.right - 176, y: rect.bottom + 4 });
  }

  return (
    <>
      <IconButton
        ref={triggerRef}
        icon={MoreVertical}
        label={label}
        size={size}
        onClick={() => (pos ? setPos(null) : open())}
        aria-haspopup="menu"
        aria-expanded={pos !== null}
      />
      {pos && (
        <ContextMenu x={pos.x} y={pos.y} items={items} onClose={() => setPos(null)} />
      )}
    </>
  );
}

export default OverflowMenu;
