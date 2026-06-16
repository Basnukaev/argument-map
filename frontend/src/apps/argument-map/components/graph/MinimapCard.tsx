import { useState, useRef, useMemo, useCallback } from 'react';
import { Crosshair, Eye, EyeOff, Minimize2, Maximize2 } from 'lucide-react';
import { useT } from '@/shared/i18n';

// ── Types ────────────────────────────────────────────────────────────

export interface MinimapNode {
  id: string;
  x: number;
  y: number;
  w: number;
  h: number;
  type?: 'QUESTION' | 'CLAIM' | 'ARGUMENT' | 'EVIDENCE';
  selected?: boolean;
}

export interface MinimapEdge {
  from: string;
  to: string;
}

export interface MinimapViewport {
  x: number;
  y: number;
  w: number;
  h: number;
}

interface MinimapCardProps {
  nodes: MinimapNode[];
  edges?: MinimapEdge[];
  viewport: MinimapViewport;
  canvasBounds?: { w: number; h: number };
  zoom?: number;
  onViewportChange?: (v: MinimapViewport) => void;
  onCenterOnSelection?: () => void;
  collapsed?: boolean;
  defaultCollapsed?: boolean;
  onCollapsedChange?: (collapsed: boolean) => void;
  showEdges?: boolean;
}

// ── Constants ────────────────────────────────────────────────────────

const EXPANDED_W = 240;
const COLLAPSED_W = 168;
const CANVAS_H = 150;
const HEADER_H = 32;
const FOOTER_H = 28;
const COLLAPSED_H = 40;
const BOUNDS_PAD = 1.2;

const NODE_TYPE_COLOR: Record<string, string> = {
  QUESTION: 'var(--node-question-ink)',
  CLAIM: 'var(--node-thesis-ink)',
  ARGUMENT: 'var(--node-argument-ink)',
  EVIDENCE: 'var(--node-evidence-ink)',
};

type NodeTypeKey = 'QUESTION' | 'CLAIM' | 'ARGUMENT' | 'EVIDENCE';

// ── Helpers ──────────────────────────────────────────────────────────

function pluralRu(n: number, one: string, few: string, many: string): string {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return one;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return few;
  return many;
}

function clamp(v: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, v));
}

/** Derive canvas bounds from node extents with padding, or use provided. */
function deriveBounds(
  nodes: MinimapNode[],
  canvasBounds?: { w: number; h: number },
): { w: number; h: number } {
  if (canvasBounds) return canvasBounds;
  if (nodes.length === 0) return { w: 800, h: 600 };
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  for (const n of nodes) {
    if (n.x < minX) minX = n.x;
    if (n.y < minY) minY = n.y;
    if (n.x + n.w > maxX) maxX = n.x + n.w;
    if (n.y + n.h > maxY) maxY = n.y + n.h;
  }
  const rawW = maxX - minX;
  const rawH = maxY - minY;
  return { w: rawW * BOUNDS_PAD, h: rawH * BOUNDS_PAD };
}

/** Compute projection scale + offset to center content in the minimap area. */
function useProjection(
  nodes: MinimapNode[],
  bounds: { w: number; h: number },
  areaW: number,
  areaH: number,
) {
  return useMemo(() => {
    const s = Math.min(areaW / bounds.w, areaH / bounds.h);

    // Content origin: min x/y across all nodes (or 0 if empty)
    let minX = 0;
    let minY = 0;
    if (nodes.length > 0) {
      minX = Infinity;
      minY = Infinity;
      for (const n of nodes) {
        if (n.x < minX) minX = n.x;
        if (n.y < minY) minY = n.y;
      }
      // With BOUNDS_PAD, shift origin so content is centered
      let maxX = -Infinity;
      let maxY = -Infinity;
      for (const n of nodes) {
        if (n.x + n.w > maxX) maxX = n.x + n.w;
        if (n.y + n.h > maxY) maxY = n.y + n.h;
      }
      const contentW = maxX - minX;
      const contentH = maxY - minY;
      // Offset to center the scaled content within the area
      const ofsX = (areaW - contentW * s) / 2;
      const ofsY = (areaH - contentH * s) / 2;
      // Центрированный origin под BOUNDS_PAD: bounds расширены симметрично
      // вокруг контента, поэтому drag/click клампим к этому origin'у, а НЕ к
      // raw minX. Иначе диапазон асимметричен (весь pad на дальней стороне) и
      // вьюпорт защёлкивается неконсистентно у краёв (#6, audit).
      const padMinX = minX - (bounds.w - contentW) / 2;
      const padMinY = minY - (bounds.h - contentH) / 2;
      return { s, minX, minY, ofsX, ofsY, padMinX, padMinY };
    }
    return { s, minX: 0, minY: 0, ofsX: 0, ofsY: 0, padMinX: 0, padMinY: 0 };
  }, [nodes, bounds, areaW, areaH]);
}

// ── Sub-components ───────────────────────────────────────────────────

/** Edge rendered as a CSS-positioned rotated line between two node centers. */
function EdgeLine({
  fromNode,
  toNode,
  s,
  minX,
  minY,
  ofsX,
  ofsY,
}: {
  fromNode: MinimapNode;
  toNode: MinimapNode;
  s: number;
  minX: number;
  minY: number;
  ofsX: number;
  ofsY: number;
}) {
  const x1 = (fromNode.x + fromNode.w / 2 - minX) * s + ofsX;
  const y1 = (fromNode.y + fromNode.h / 2 - minY) * s + ofsY;
  const x2 = (toNode.x + toNode.w / 2 - minX) * s + ofsX;
  const y2 = (toNode.y + toNode.h / 2 - minY) * s + ofsY;

  const dx = x2 - x1;
  const dy = y2 - y1;
  const length = Math.sqrt(dx * dx + dy * dy);
  const angle = Math.atan2(dy, dx) * (180 / Math.PI);

  return (
    <div
      className="absolute pointer-events-none"
      style={{
        left: x1,
        top: y1,
        width: length,
        height: 1,
        backgroundColor: 'var(--text-meta)',
        opacity: 0.35,
        transformOrigin: '0 0',
        transform: `rotate(${angle}deg)`,
      }}
    />
  );
}

/** Type stat dot + count in the footer. */
function TypeStat({ type, count }: { type: NodeTypeKey; count: number }) {
  return (
    <span className="flex items-center gap-1">
      <span
        className="inline-block rounded-full"
        style={{
          width: 6,
          height: 6,
          backgroundColor: NODE_TYPE_COLOR[type],
        }}
      />
      <span className="text-[10.5px] font-medium text-meta">{count}</span>
    </span>
  );
}

// ── Main Component ───────────────────────────────────────────────────

function MinimapCard({
  nodes,
  edges,
  viewport,
  canvasBounds,
  zoom = 1,
  onViewportChange,
  onCenterOnSelection,
  collapsed: controlledCollapsed,
  defaultCollapsed = false,
  onCollapsedChange,
  showEdges: controlledShowEdges,
}: MinimapCardProps) {
  const t = useT();

  // ── Collapsed state (controlled / uncontrolled) ──
  const [internalCollapsed, setInternalCollapsed] = useState(defaultCollapsed);
  const isCollapsed = controlledCollapsed ?? internalCollapsed;
  const setCollapsed = useCallback(
    (v: boolean) => {
      if (controlledCollapsed === undefined) setInternalCollapsed(v);
      onCollapsedChange?.(v);
    },
    [controlledCollapsed, onCollapsedChange],
  );

  // ── Show edges state (controlled / uncontrolled) ──
  const [internalShowEdges, setInternalShowEdges] = useState(true);
  const edgesVisible =
    controlledShowEdges !== undefined ? controlledShowEdges : internalShowEdges;
  const toggleEdges = useCallback(() => {
    setInternalShowEdges((v) => !v);
  }, []);

  // ── Derived data ──
  const bounds = useMemo(
    () => deriveBounds(nodes, canvasBounds),
    [nodes, canvasBounds],
  );

  const hasSelection = nodes.some((n) => n.selected);
  const selectedCount = nodes.filter((n) => n.selected).length;

  // Type counts for footer
  const typeCounts = useMemo(() => {
    const counts: Partial<Record<NodeTypeKey, number>> = {};
    for (const n of nodes) {
      const nodeType = n.type ?? 'CLAIM';
      counts[nodeType] = (counts[nodeType] ?? 0) + 1;
    }
    return counts;
  }, [nodes]);

  // Node lookup for edges
  const nodeMap = useMemo(() => {
    const map = new Map<string, MinimapNode>();
    for (const n of nodes) map.set(n.id, n);
    return map;
  }, [nodes]);

  const canvasW = EXPANDED_W;
  const canvasH = CANVAS_H;

  // Full canvas projection
  const proj = useProjection(nodes, bounds, canvasW, canvasH);

  // Collapsed mini-preview projection (44x28)
  const miniProj = useProjection(nodes, bounds, 44, 28);

  // ── Drag state ──
  const dragRef = useRef<{
    startX: number;
    startY: number;
    vpStartX: number;
    vpStartY: number;
  } | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const canvasRef = useRef<HTMLDivElement>(null);

  // ── Viewport projection helpers ──
  const vpRect = useCallback(
    (p: { s: number; minX: number; minY: number; ofsX: number; ofsY: number }) => ({
      left: (viewport.x - p.minX) * p.s + p.ofsX,
      top: (viewport.y - p.minY) * p.s + p.ofsY,
      width: viewport.w * p.s,
      height: viewport.h * p.s,
    }),
    [viewport],
  );

  const fullVp = vpRect(proj);
  const miniVp = vpRect(miniProj);

  // ── Viewport drag handlers ──
  const handleVpPointerDown = useCallback(
    (e: React.PointerEvent) => {
      e.stopPropagation();
      e.preventDefault();
      (e.target as HTMLElement).setPointerCapture(e.pointerId);
      dragRef.current = {
        startX: e.clientX,
        startY: e.clientY,
        vpStartX: viewport.x,
        vpStartY: viewport.y,
      };
      setIsDragging(true);
    },
    [viewport.x, viewport.y],
  );

  const handleVpPointerMove = useCallback(
    (e: React.PointerEvent) => {
      if (!dragRef.current || !onViewportChange) return;
      const dx = (e.clientX - dragRef.current.startX) / proj.s;
      const dy = (e.clientY - dragRef.current.startY) / proj.s;
      const newX = clamp(
        dragRef.current.vpStartX + dx,
        proj.padMinX,
        proj.padMinX + bounds.w - viewport.w,
      );
      const newY = clamp(
        dragRef.current.vpStartY + dy,
        proj.padMinY,
        proj.padMinY + bounds.h - viewport.h,
      );
      onViewportChange({ x: newX, y: newY, w: viewport.w, h: viewport.h });
    },
    [onViewportChange, proj, bounds, viewport.w, viewport.h],
  );

  const handleVpPointerUp = useCallback(
    (e: React.PointerEvent) => {
      (e.target as HTMLElement).releasePointerCapture(e.pointerId);
      dragRef.current = null;
      setIsDragging(false);
    },
    [],
  );

  // ── Canvas click → pan viewport center ──
  const handleCanvasClick = useCallback(
    (e: React.MouseEvent) => {
      if (!onViewportChange || !canvasRef.current) return;
      const rect = canvasRef.current.getBoundingClientRect();
      const clickX = e.clientX - rect.left;
      const clickY = e.clientY - rect.top;
      // Convert screen coords to canvas coords
      const canvasX = (clickX - proj.ofsX) / proj.s + proj.minX;
      const canvasY = (clickY - proj.ofsY) / proj.s + proj.minY;
      // Center viewport on click point, clamped
      const newX = clamp(
        canvasX - viewport.w / 2,
        proj.padMinX,
        proj.padMinX + bounds.w - viewport.w,
      );
      const newY = clamp(
        canvasY - viewport.h / 2,
        proj.padMinY,
        proj.padMinY + bounds.h - viewport.h,
      );
      onViewportChange({ x: newX, y: newY, w: viewport.w, h: viewport.h });
    },
    [onViewportChange, proj, bounds, viewport.w, viewport.h],
  );

  const zoomLabel = `${Math.round(zoom * 100)}%`;

  // ── Collapsed state rendering ──
  if (isCollapsed) {
    return (
      <div
        className="overflow-hidden rounded-[12px] border border-bd bg-card shadow-sm"
        style={{
          width: COLLAPSED_W,
          height: COLLAPSED_H,
          transition: 'width 250ms cubic-bezier(.2,.7,.3,1)',
        }}
      >
        <div className="flex items-center gap-2 p-[4px_4px_4px_6px]">
          {/* Clickable body area */}
          <button
            type="button"
            className="flex flex-1 items-center gap-2 rounded-[5px] hover:bg-hover transition-colors"
            onClick={() => setCollapsed(false)}
          >
            {/* Mini preview */}
            <div
              className="relative shrink-0 overflow-hidden rounded-[4px] border border-bd bg-canvas"
              style={{ width: 44, height: 28 }}
            >
              {/* Mini nodes */}
              {nodes.map((n) => {
                const px = (n.x - miniProj.minX) * miniProj.s + miniProj.ofsX;
                const py = (n.y - miniProj.minY) * miniProj.s + miniProj.ofsY;
                const pw = Math.max(1, n.w * miniProj.s);
                const ph = Math.max(1, n.h * miniProj.s);
                return (
                  <div
                    key={n.id}
                    className="absolute rounded-[1px]"
                    style={{
                      left: px,
                      top: py,
                      width: pw,
                      height: ph,
                      backgroundColor: NODE_TYPE_COLOR[n.type ?? 'CLAIM'],
                    }}
                  />
                );
              })}
              {/* Mini viewport rect */}
              <div
                className="absolute rounded-[2px]"
                style={{
                  left: miniVp.left,
                  top: miniVp.top,
                  width: miniVp.width,
                  height: miniVp.height,
                  border: '1px solid var(--brand-500)',
                  backgroundColor: 'color-mix(in oklch, var(--brand-500) 18%, transparent)',
                }}
              />
            </div>
            {/* Meta column */}
            <div className="flex flex-col items-start">
              <span className="font-mono text-[11.5px] font-medium text-strong">
                {zoomLabel}
              </span>
              <span className="text-[10px] text-meta">
                {nodes.length} {pluralRu(nodes.length, t('graph.minimap_node_one'), t('graph.minimap_node_few'), t('graph.minimap_node_many'))}
              </span>
            </div>
          </button>
          {/* Expand button */}
          <button
            type="button"
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[5px] text-meta hover:bg-hover hover:text-strong transition-colors"
            onClick={() => setCollapsed(false)}
            aria-label={t('graph.minimap_expand')}
          >
            <Maximize2 size={13} />
          </button>
        </div>
      </div>
    );
  }

  // ── Expanded state rendering ──
  return (
    <div
      className="overflow-hidden rounded-[12px] border border-bd bg-card shadow-sm"
      style={{
        width: EXPANDED_W,
        transition: 'width 250ms cubic-bezier(.2,.7,.3,1)',
      }}
    >
      {/* Header (32px) */}
      <div
        className="flex items-center gap-2 border-b border-bd"
        style={{ height: HEADER_H, padding: '6px 6px 6px 12px' }}
      >
        <span
          className="me-auto font-medium uppercase text-meta"
          style={{
            fontSize: 11,
            letterSpacing: '0.08em',
          }}
        >
          {t('graph.minimap_title')}
        </span>
        {/* Center on selection button */}
        {hasSelection && onCenterOnSelection && (
          <button
            type="button"
            className="flex h-6 w-6 items-center justify-center rounded-[5px] bg-transparent text-meta hover:bg-hover hover:text-strong transition-colors"
            onClick={onCenterOnSelection}
            aria-label={t('graph.minimap_center_selection')}
          >
            <Crosshair size={13} />
          </button>
        )}
        {/* Edges toggle */}
        <button
          type="button"
          className="flex h-6 w-6 items-center justify-center rounded-[5px] bg-transparent text-meta hover:bg-hover hover:text-strong transition-colors"
          onClick={toggleEdges}
          aria-label={edgesVisible ? t('graph.minimap_hide_edges') : t('graph.minimap_show_edges')}
        >
          {edgesVisible ? <Eye size={13} /> : <EyeOff size={13} />}
        </button>
        {/* Collapse */}
        <button
          type="button"
          className="flex h-6 w-6 items-center justify-center rounded-[5px] bg-transparent text-meta hover:bg-hover hover:text-strong transition-colors"
          onClick={() => setCollapsed(true)}
          aria-label={t('graph.minimap_collapse')}
        >
          <Minimize2 size={13} />
        </button>
      </div>

      {/* Canvas area (150px). overflow-hidden клиппит viewport-rect / узлы /
          рёбра строго в границах карты — при сильном zoom-out синий
          прямоугольник вьюпорта раздувается, но больше не наезжает на
          header («ОБЗОР») сверху и footer («99%» / счётчик) снизу. */}
      <div
        ref={canvasRef}
        className="relative cursor-pointer overflow-hidden"
        style={{
          height: CANVAS_H,
          backgroundColor: 'var(--bg-canvas)',
          backgroundImage:
            'radial-gradient(circle, var(--border-subtle) 1px, transparent 1px)',
          backgroundSize: '14px 14px',
        }}
        onClick={handleCanvasClick}
      >
        {/* Edges */}
        {edgesVisible &&
          edges?.map((edge, i) => {
            const fromNode = nodeMap.get(edge.from);
            const toNode = nodeMap.get(edge.to);
            if (!fromNode || !toNode) return null;
            return (
              <EdgeLine
                key={`${edge.from}-${edge.to}-${i}`}
                fromNode={fromNode}
                toNode={toNode}
                s={proj.s}
                minX={proj.minX}
                minY={proj.minY}
                ofsX={proj.ofsX}
                ofsY={proj.ofsY}
              />
            );
          })}

        {/* Nodes */}
        {nodes.map((n) => {
          const px = (n.x - proj.minX) * proj.s + proj.ofsX;
          const py = (n.y - proj.minY) * proj.s + proj.ofsY;
          const pw = n.w * proj.s;
          const ph = n.h * proj.s;
          return (
            <div
              key={n.id}
              className="absolute rounded-[2px] pointer-events-none"
              style={{
                left: px,
                top: py,
                width: pw,
                height: ph,
                backgroundColor: NODE_TYPE_COLOR[n.type ?? 'CLAIM'],
                boxShadow: n.selected
                  ? '0 0 0 1.5px var(--brand-500), 0 0 6px 0 var(--brand-500)'
                  : undefined,
              }}
            />
          );
        })}

        {/* Viewport rect */}
        <div
          className="absolute rounded-[3px]"
          style={{
            left: fullVp.left,
            top: fullVp.top,
            width: fullVp.width,
            height: fullVp.height,
            border: '1.5px solid var(--brand-500)',
            backgroundColor: isDragging
              ? 'color-mix(in oklch, var(--brand-500) 22%, transparent)'
              : 'color-mix(in oklch, var(--brand-500) 12%, transparent)',
            cursor: isDragging ? 'grabbing' : 'grab',
            transition: isDragging ? 'none' : 'background-color 150ms',
          }}
          onPointerDown={handleVpPointerDown}
          onPointerMove={handleVpPointerMove}
          onPointerUp={handleVpPointerUp}
          onClick={(e) => e.stopPropagation()}
        />
      </div>

      {/* Footer (28px) */}
      <div
        className="flex items-center gap-2 border-t border-bd"
        style={{ height: FOOTER_H, padding: '6px 10px' }}
      >
        {/* Left: type stats or selection banner */}
        {hasSelection ? (
          <span
            className="font-medium uppercase"
            style={{
              fontSize: 10.5,
              letterSpacing: '0.04em',
              backgroundColor: 'color-mix(in oklch, var(--brand-500) 15%, transparent)',
              color: 'var(--brand-500)',
              padding: '2px 6px',
              borderRadius: 4,
            }}
          >
            {t('graph.minimap_selected')} {selectedCount}
          </span>
        ) : (
          <span className="flex items-center gap-2">
            {(
              ['QUESTION', 'CLAIM', 'ARGUMENT', 'EVIDENCE'] as const
            ).map((type) => {
              const count = typeCounts[type];
              if (!count) return null;
              return <TypeStat key={type} type={type} count={count} />;
            })}
          </span>
        )}

        {/* Right: zoom badge */}
        <span className="flex-1 text-end">
          <span
            className="inline-block rounded-[4px] bg-subtle font-mono text-[11px] font-medium"
            style={{ padding: '2px 6px' }}
          >
            {zoomLabel}
          </span>
        </span>
      </div>
    </div>
  );
}

export default MinimapCard;
