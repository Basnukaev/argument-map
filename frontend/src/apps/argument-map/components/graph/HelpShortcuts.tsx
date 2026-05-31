import { useState, useRef, useEffect, useCallback } from 'react';
import { HelpCircle, Pin } from 'lucide-react';
import { useT } from '@/shared/i18n';

type Shortcut = {
  group?: string;
  label: string;
  keys: string[];
};

interface HelpShortcutsProps {
  shortcuts: Shortcut[];
  position?: 'down' | 'up';
  align?: 'right' | 'left';
  title?: string;
  trigger?: 'hover' | 'click';
}

/** Флаги «первая строка в своей группе» — для рендера group-label. */
function computeGroupLabelFlags(shortcuts: Shortcut[]): boolean[] {
  const flags: boolean[] = [];
  let prev: string | undefined;
  for (const s of shortcuts) {
    flags.push(s.group !== undefined && s.group !== prev);
    if (s.group !== undefined) prev = s.group;
  }
  return flags;
}

/**
 * Compact help-shortcuts popover for graph canvas.
 * Trigger is a ?-icon button; popover lists grouped keyboard shortcuts.
 * Supports hover and click trigger modes, pinned state, and directional positioning.
 */
function HelpShortcuts({
  shortcuts,
  position = 'down',
  align = 'right',
  title = 'Шорткаты',
  trigger = 'hover',
}: HelpShortcutsProps) {
  const t = useT();
  const [open, setOpen] = useState(false);
  const [pinned, setPinned] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const hoverTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const isOpen = pinned || open;

  // Click-outside handler for click trigger mode and pinned state
  useEffect(() => {
    if (trigger === 'hover' && !pinned) return;
    if (!isOpen) return;

    function onPointerDown(e: PointerEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
        setPinned(false);
      }
    }
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [trigger, pinned, isOpen]);

  // Escape dismiss for click trigger and pinned state
  useEffect(() => {
    if (!isOpen) return;
    if (trigger === 'hover' && !pinned) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        setOpen(false);
        setPinned(false);
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [isOpen, trigger, pinned]);

  // Cleanup hover timeout on unmount
  useEffect(() => {
    return () => {
      if (hoverTimeoutRef.current) clearTimeout(hoverTimeoutRef.current);
    };
  }, []);

  const handleMouseEnter = useCallback(() => {
    if (trigger !== 'hover') return;
    if (hoverTimeoutRef.current) {
      clearTimeout(hoverTimeoutRef.current);
      hoverTimeoutRef.current = null;
    }
    setOpen(true);
  }, [trigger]);

  const handleMouseLeave = useCallback(() => {
    if (trigger !== 'hover') return;
    if (pinned) return;
    // Small delay so the user can move from trigger to popover
    hoverTimeoutRef.current = setTimeout(() => setOpen(false), 80);
  }, [trigger, pinned]);

  const handleTriggerClick = useCallback(() => {
    if (trigger !== 'click') return;
    setOpen((v) => !v);
  }, [trigger]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        if (trigger === 'click') {
          setOpen((v) => !v);
        } else {
          setOpen(true);
        }
      }
    },
    [trigger],
  );

  const handlePinClick = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    setPinned((v) => !v);
  }, []);

  // Popover positioning CSS
  const popoverPositionClass =
    position === 'down'
      ? align === 'right'
        ? 'top-[calc(100%+10px)] right-0'
        : 'top-[calc(100%+10px)] left-0'
      : align === 'right'
        ? 'bottom-[calc(100%+10px)] right-0'
        : 'bottom-[calc(100%+10px)] left-0';

  // Animation origin for translateY direction
  const translateFrom = position === 'down' ? '-translate-y-1' : 'translate-y-1';

  // Group consecutive shortcuts by group field. Вычисляем флаги до
  // рендера — мутация let во время render запрещена
  // (react-hooks/immutability, React Compiler).
  const groupLabelFlags = computeGroupLabelFlags(shortcuts);

  return (
    <div
      ref={containerRef}
      className="relative"
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      {/* Trigger button */}
      <div
        role="button"
        tabIndex={0}
        aria-label={title}
        onClick={handleTriggerClick}
        onKeyDown={handleKeyDown}
        className={`inline-flex h-9 w-9 cursor-pointer items-center justify-center rounded-[9px] border bg-card shadow-sm transition-all duration-150 ${
          isOpen
            ? 'border-bd-strong text-strong'
            : 'border-bd text-meta hover:border-bd-strong hover:text-strong'
        } active:scale-[0.96]`}
      >
        <HelpCircle size={16} aria-hidden="true" />
      </div>

      {/* Popover */}
      <div
        role="dialog"
        aria-label={title}
        // Когда закрыт — прячем из a11y-дерева и tab-order (иначе SR
        // озвучивает скрытый диалог, а Tab достаёт невидимый pin).
        aria-hidden={!isOpen}
        inert={!isOpen}
        className={`group/popover absolute z-50 min-w-[260px] rounded-[12px] border border-bd-strong bg-card p-[14px] shadow-lg transition-all duration-150 ${popoverPositionClass} ${
          isOpen
            ? 'pointer-events-auto translate-y-0 opacity-100'
            : `pointer-events-none opacity-0 ${translateFrom}`
        }`}
      >
        {/* Header */}
        <div className="mb-2 flex items-center justify-between">
          <span className="text-[11px] font-medium uppercase tracking-[0.08em] text-meta">
            {title}
          </span>
          <button
            type="button"
            aria-label={pinned ? t('graph.shortcuts_unpin') : t('graph.shortcuts_pin')}
            onClick={handlePinClick}
            className={`p-0.5 transition-opacity ${
              pinned
                ? 'text-brand-500 opacity-100'
                : 'text-meta opacity-0 hover:text-brand-500 group-hover/popover:opacity-100'
            }`}
          >
            <Pin size={12} aria-hidden="true" />
          </button>
        </div>

        {/* Shortcut rows */}
        {shortcuts.map((shortcut, idx) => {
          const showGroupLabel = groupLabelFlags[idx] ?? false;

          return (
            <div key={`${shortcut.label}-${idx}`}>
              {showGroupLabel && (
                <div
                  className={`px-1.5 pb-1 font-mono text-[10.5px] font-medium uppercase tracking-[0.05em] text-meta ${
                    idx === 0 ? 'pt-0' : 'pt-2'
                  }`}
                >
                  {shortcut.group}
                </div>
              )}
              <div className="flex items-center gap-3 rounded-[6px] px-1.5 py-[7px] hover:bg-hover">
                <span className="flex-1 text-[13px] font-normal text-strong">
                  {shortcut.label}
                </span>
                <span className="inline-flex gap-[3px]">
                  {shortcut.keys.map((key, ki) => (
                    <kbd
                      key={`${key}-${ki}`}
                      className="inline-flex min-w-[18px] items-center justify-center rounded-[4px] border border-bd bg-subtle px-1.5 py-[3px] text-center font-mono text-[11px] font-medium text-strong"
                    >
                      {key}
                    </kbd>
                  ))}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default HelpShortcuts;
