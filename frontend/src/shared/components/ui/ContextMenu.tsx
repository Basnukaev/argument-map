import { useEffect, useRef } from 'react';
import type { ComponentType, ReactNode } from 'react';

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

  useEffect(() => {
    function onPointerDown(event: PointerEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        onClose();
      }
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose();
    }
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [onClose]);

  return (
    <div
      ref={ref}
      role="menu"
      style={{ left: x, top: y }}
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
