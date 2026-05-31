import { useState, useRef, useEffect } from 'react';
import { Minus, Plus, ChevronDown, Scan, Maximize } from 'lucide-react';
import { useT } from '@/shared/i18n';
import type { DictKey } from '@/shared/i18n';

/* ────────────────────────────────────────────────────────────────────── */
/* Types                                                                  */
/* ────────────────────────────────────────────────────────────────────── */

interface ZoomControlsProps {
  /** Current zoom factor (1 = 100%) */
  zoom: number;
  min?: number;
  max?: number;
  step?: number;
  onZoomChange?: (zoom: number) => void;
  onFit?: () => void;
  onFitSelection?: () => void;
  onFullscreen?: () => void;
  /** Controls "Fit selection" preset visibility */
  hasSelection?: boolean;
}

/* ────────────────────────────────────────────────────────────────────── */
/* Preset definitions                                                     */
/* ────────────────────────────────────────────────────────────────────── */

type PresetItem =
  | { kind: 'zoom'; value: number; kbd: string | null }
  | { kind: 'divider' }
  | { kind: 'action'; id: 'fit' | 'fitSelection'; label: string; kbd: string };

const ZOOM_VALUES = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 2] as const;

function buildPresets(
  t: (key: DictKey) => string,
  hasSelection: boolean,
): PresetItem[] {
  const items: PresetItem[] = ZOOM_VALUES.map((v) => ({
    kind: 'zoom' as const,
    value: v,
    kbd: v === 1 ? '⌘ 0' : null,
  }));

  items.push({ kind: 'divider' });
  items.push({
    kind: 'action',
    id: 'fit',
    label: t('graph.fit_view'),
    kbd: '⌘ 1',
  });

  if (hasSelection) {
    items.push({
      kind: 'action',
      id: 'fitSelection',
      label: t('graph.zoom_fit_selection'),
      kbd: '⌘ 2',
    });
  }

  return items;
}

/* ────────────────────────────────────────────────────────────────────── */
/* Tooltip wrapper                                                        */
/* ────────────────────────────────────────────────────────────────────── */

/**
 * Inline tooltip (CSS-only, no portal). Shows above the trigger on hover
 * after a short delay. Used instead of `title=` for richer content (kbd chip).
 */
function Tooltip({
  label,
  kbd,
  children,
}: {
  label: string;
  kbd?: string;
  children: React.ReactNode;
}) {
  return (
    <span className="group/tip relative inline-flex">
      {children}
      <span
        role="tooltip"
        className="pointer-events-none absolute bottom-full left-1/2 z-50 mb-1.5 -translate-x-1/2 whitespace-nowrap rounded-md border border-bd bg-card px-2 py-1 text-[11px] font-medium text-strong opacity-0 shadow-sm transition-opacity duration-150 group-hover/tip:opacity-100"
      >
        <span className="flex items-center gap-1.5">
          {label}
          {kbd && (
            <kbd className="inline-flex items-center rounded-[3px] bg-subtle px-[5px] py-px font-mono text-[10.5px] font-medium text-muted">
              {kbd}
            </kbd>
          )}
        </span>
      </span>
    </span>
  );
}

/* ────────────────────────────────────────────────────────────────────── */
/* Helpers                                                                */
/* ────────────────────────────────────────────────────────────────────── */

function formatPercent(zoom: number): string {
  return `${Math.round(zoom * 100)}%`;
}

/** Clamp + round to step to avoid floating-point drift */
function clampZoom(value: number, min: number, max: number, step: number): number {
  const rounded = Math.round(value / step) * step;
  return Math.min(max, Math.max(min, rounded));
}

/** Check approximate equality for highlighting current preset */
function isCurrentPreset(zoom: number, preset: number): boolean {
  return Math.abs(zoom - preset) < 0.005;
}

/* ────────────────────────────────────────────────────────────────────── */
/* Component                                                              */
/* ────────────────────────────────────────────────────────────────────── */

function ZoomControls({
  zoom,
  min = 0.1,
  max = 5,
  step = 0.1,
  onZoomChange,
  onFit,
  onFitSelection,
  onFullscreen,
  hasSelection = false,
}: ZoomControlsProps) {
  const t = useT();
  const [presetsOpen, setPresetsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  const atMin = zoom <= min + 0.001;
  const atMax = zoom >= max - 0.001;

  /* ── Outside-click dismiss ─────────────────────────────────────── */
  useEffect(() => {
    if (!presetsOpen) return;
    function onPointerDown(e: PointerEvent) {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(e.target as Node) &&
        triggerRef.current &&
        !triggerRef.current.contains(e.target as Node)
      ) {
        setPresetsOpen(false);
      }
    }
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [presetsOpen]);

  /* ── Escape dismiss ────────────────────────────────────────────── */
  useEffect(() => {
    if (!presetsOpen) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        setPresetsOpen(false);
        triggerRef.current?.focus();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [presetsOpen]);

  /* ── Handlers ──────────────────────────────────────────────────── */
  function handleZoomIn() {
    if (atMax) return;
    onZoomChange?.(clampZoom(zoom + step, min, max, step));
  }

  function handleZoomOut() {
    if (atMin) return;
    onZoomChange?.(clampZoom(zoom - step, min, max, step));
  }

  function handlePresetClick(item: PresetItem) {
    if (item.kind === 'zoom') {
      onZoomChange?.(item.value);
    } else if (item.kind === 'action') {
      if (item.id === 'fit') onFit?.();
      if (item.id === 'fitSelection') onFitSelection?.();
    }
    setPresetsOpen(false);
  }

  const presets = buildPresets(t, hasSelection);

  /* ── Render ────────────────────────────────────────────────────── */
  return (
    <div className="relative inline-flex items-center gap-0.5 rounded-[10px] border border-bd bg-card p-1 shadow-sm">
      {/* Zoom out */}
      <Tooltip label={t('graph.zoom_out')} kbd={'⌘−'}>
        <button
          type="button"
          aria-label={t('graph.zoom_out')}
          disabled={atMin}
          onClick={handleZoomOut}
          className="inline-flex h-[30px] w-[30px] items-center justify-center rounded-[6px] text-body transition-colors hover:bg-hover focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 disabled:cursor-not-allowed disabled:opacity-35"
        >
          <Minus size={14} aria-hidden="true" />
        </button>
      </Tooltip>

      {/* Percentage / presets trigger */}
      <div ref={dropdownRef} className="relative">
        <button
          ref={triggerRef}
          type="button"
          aria-label={t('graph.zoom_presets')}
          aria-haspopup="menu"
          aria-expanded={presetsOpen}
          onClick={() => setPresetsOpen((v) => !v)}
          className={`inline-flex h-[30px] min-w-[56px] items-center justify-center gap-0.5 rounded-[6px] px-1.5 font-mono text-[12.5px] font-medium text-body transition-colors hover:bg-hover focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 ${presetsOpen ? 'bg-hover' : ''}`}
        >
          {formatPercent(zoom)}
          <ChevronDown
            size={12}
            aria-hidden="true"
            className={`text-muted transition-transform duration-150 ${presetsOpen ? 'rotate-180' : ''}`}
          />
        </button>

        {/* Presets dropdown */}
        {presetsOpen && (
          <div
            role="menu"
            aria-label={t('graph.zoom_presets')}
            className="absolute bottom-full left-1/2 z-50 mb-1.5 min-w-[180px] -translate-x-1/2 rounded-[10px] border border-bd-strong bg-card p-[5px] shadow-md"
          >
            {presets.map((item, idx) => {
              if (item.kind === 'divider') {
                return (
                  <div
                    key={`divider-${idx}`}
                    className="mx-1 my-1 h-px bg-bd"
                    aria-hidden="true"
                  />
                );
              }

              const isCurrent =
                item.kind === 'zoom' && isCurrentPreset(zoom, item.value);

              const label =
                item.kind === 'zoom' ? formatPercent(item.value) : item.label;

              const kbd = item.kbd;

              return (
                <button
                  key={item.kind === 'zoom' ? item.value : item.id}
                  type="button"
                  role="menuitem"
                  onClick={() => handlePresetClick(item)}
                  className="flex w-full items-center gap-2.5 rounded-[6px] px-2.5 py-2 text-start text-[13px] text-body transition-colors hover:bg-hover focus:outline-none focus-visible:bg-hover"
                >
                  {/* Active dot */}
                  <span className="flex w-[6px] shrink-0 justify-center">
                    {isCurrent && (
                      <span className="h-1.5 w-1.5 rounded-full bg-accent-600" />
                    )}
                  </span>
                  <span className={`flex-1 ${isCurrent ? 'font-semibold' : 'font-medium'}`}>
                    {label}
                  </span>
                  {kbd && (
                    <kbd
                      className={`font-mono text-[10.5px] font-medium rounded-[3px] px-[5px] py-px ${
                        isCurrent
                          ? 'bg-accent-100 text-accent-700'
                          : 'bg-subtle text-muted'
                      }`}
                    >
                      {kbd}
                    </kbd>
                  )}
                </button>
              );
            })}
          </div>
        )}
      </div>

      {/* Zoom in */}
      <Tooltip label={t('graph.zoom_in')} kbd={'⌘+'}>
        <button
          type="button"
          aria-label={t('graph.zoom_in')}
          disabled={atMax}
          onClick={handleZoomIn}
          className="inline-flex h-[30px] w-[30px] items-center justify-center rounded-[6px] text-body transition-colors hover:bg-hover focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 disabled:cursor-not-allowed disabled:opacity-35"
        >
          <Plus size={14} aria-hidden="true" />
        </button>
      </Tooltip>

      {/* Vertical divider */}
      <div className="mx-1 h-[18px] w-px bg-bd" aria-hidden="true" />

      {/* Fit all */}
      <Tooltip label={t('graph.fit_view')} kbd={'⌘ 1'}>
        <button
          type="button"
          aria-label={t('graph.fit_view')}
          onClick={() => onFit?.()}
          className="inline-flex h-[30px] w-[30px] items-center justify-center rounded-[6px] text-body transition-colors hover:bg-hover focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
        >
          <Scan size={15} aria-hidden="true" />
        </button>
      </Tooltip>

      {/* Fullscreen — only render when handler provided */}
      {onFullscreen && (
        <Tooltip label={t('graph.zoom_fullscreen')} kbd="F">
          <button
            type="button"
            aria-label={t('graph.zoom_fullscreen')}
            onClick={onFullscreen}
            className="inline-flex h-[30px] w-[30px] items-center justify-center rounded-[6px] text-body transition-colors hover:bg-hover focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
          >
            <Maximize size={15} aria-hidden="true" />
          </button>
        </Tooltip>
      )}
    </div>
  );
}

export default ZoomControls;
