import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import type { ComponentType, ReactNode } from 'react';
import { useHotkey } from '@/shared/hooks/useHotkey';
import { clampMenuPosition } from './contextMenuPosition';

export interface ContextMenuItem {
  /** уникальный id - используется как key */
  id: string;
  label: string;
  icon?: ComponentType<{ size?: number; className?: string }>;
  /** для деструктивных пунктов (удаление) - красная подпись */
  danger?: boolean;
  disabled?: boolean;
  /** если true - рендерит горизонтальную линию-разделитель вместо пункта */
  separator?: boolean;
  onClick?: () => void;
}

interface Props {
  /** координаты в viewport (clientX/clientY) */
  x: number;
  y: number;
  items: ContextMenuItem[];
  onClose: () => void;
  /** опциональный заголовок над пунктами - например, тип узла */
  header?: ReactNode;
}

/**
 * Универсальное контекстное меню. Закрывается при клике/правом-клике
 * вне меню и при Escape. Позиционируется через fixed по clientX/Y -
 * подходит для onContextMenu событий.
 */
function ContextMenu({ x, y, items, onClose, header }: Props) {
  const ref = useRef<HTMLDivElement>(null);
  // Позиция после viewport-clamp. Старт с raw clientX/Y — затем
  // useLayoutEffect измеряет реальные размеры меню и пересчитывает до
  // paint (без визуального «прыжка»). Если меню у правого/нижнего края
  // viewport — сдвигаем внутрь, чтобы оно не уехало off-screen.
  const [pos, setPos] = useState<{ left: number; top: number }>({ left: x, top: y });

  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    setPos(
      clampMenuPosition(
        x,
        y,
        rect.width,
        rect.height,
        window.innerWidth,
        window.innerHeight,
      ),
    );
  }, [x, y, items, header]);

  useEffect(() => {
    function onPointerDown(event: PointerEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        onClose();
      }
    }
    document.addEventListener('pointerdown', onPointerDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
    };
  }, [onClose]);

  useHotkey('escape', onClose, { enableOnFormTags: true });

  return (
    <div
      ref={ref}
      role="menu"
      style={{ left: pos.left, top: pos.top }}
      className="fixed z-50 min-w-44 rounded-md border border-border bg-elevated py-1 shadow-sh3"
    >
      {header && (
        <div className="border-b border-border px-3 py-1.5 text-xs text-ink-500">
          {header}
        </div>
      )}
      {items.map((item) => {
        if (item.separator) {
          return <hr key={item.id} className="my-1 border-t border-border" />;
        }
        const Icon = item.icon;
        const colorClass = item.danger
          ? 'text-err-700 hover:bg-err-100'
          : 'text-ink-800 hover:bg-ink-100';
        return (
          <button
            key={item.id}
            type="button"
            role="menuitem"
            disabled={item.disabled}
            onClick={() => {
              item.onClick?.();
              onClose();
            }}
            className={`flex w-full items-center gap-2 px-3 py-1.5 text-start text-sm transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${colorClass}`}
          >
            {Icon && <Icon size={14} />}
            <span>{item.label}</span>
          </button>
        );
      })}
    </div>
  );
}

export default ContextMenu;
