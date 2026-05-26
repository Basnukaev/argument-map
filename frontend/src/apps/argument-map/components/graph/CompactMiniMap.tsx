import { useState, useRef, useCallback } from 'react';
import { useNodes, useEdges, useStore, useReactFlow } from '@xyflow/react';
import type { Node, Edge } from '@xyflow/react';
import { Maximize2, Minimize2, ZoomIn, ZoomOut, Maximize } from 'lucide-react';
import IconButton from '@/shared/components/ui/IconButton';
import type { NodeType, EdgeType } from '@/apps/argument-map/utils/edgeRules';
import { useT } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type NodeDto = components['schemas']['NodeResponse'];

// Fill узла на minimap - через CSS variables, чтобы темизация и
// семантика были сквозные с остальным графом. Per v2 design system
// QUESTION/CLAIM/ARGUMENT - abstract type-family, EVIDENCE - empirical.
const TYPE_FILL: Record<NodeType, string> = {
  QUESTION: 'var(--c-type-abstract-fg)',
  CLAIM: 'var(--c-type-abstract-fg)',
  ARGUMENT: 'var(--c-type-abstract-fg)',
  EVIDENCE: 'var(--c-type-empirical-fg)',
};

// stroke узла - по статусу через semantic var(--c-{status}-500). Серый
// (ink-400) для UNVERIFIED не отвлекает, статусный цвет загорается только
// когда оценка известна.
const STATUS_STROKE: Record<NonNullable<NodeDto['status']>, string> = {
  STANDING: 'var(--c-ok-500)',
  DISPUTED: 'var(--c-warn-500)',
  REFUTED: 'var(--c-err-500)',
  UNVERIFIED: 'var(--c-ink-400)',
};

// Edge stroke - те же edge-* токены что и в CustomEdge и EDGE_TYPE_TOKENS
const EDGE_HEX: Record<EdgeType, string> = {
  SUPPORTS: 'var(--c-edge-supports)',
  REFUTES: 'var(--c-edge-refutes)',
  INVALIDATES: 'var(--c-edge-refutes)',
  QUALIFIES: 'var(--c-edge-qualifies)',
  RESPONDS_TO: 'var(--c-edge-responds)',
};

const PAD = 60;
// Размеры контейнера. Compact - достаточный чтобы видеть структуру,
// expanded - в 2x для рассмотрения деталей
const COMPACT_W = 240;
const COMPACT_H = 170;
const EXPANDED_W = 480;
const EXPANDED_H = 340;

import { getBoundingBox, expandBounds, type BBox } from '@/apps/argument-map/utils/graphBounds';

interface MiniMapProps {
  /** Если true (узел/ребро selected, открыт detail panel) - сдвиг карты
   *  на 416px от inline-end края чтобы не перекрываться панелью w-[400px] */
  detailOpen?: boolean;
}

/**
 * Кастомная мини-карта: показывает узлы (как уменьшенные NodeCard через
 * SVG rect+иконку), рёбра как цветные линии, viewport-rectangle (где
 * сейчас камера) с синей обводкой. Click на minimap центрирует камеру
 * там же. Toggle развернуть/свернуть для большего размера.
 *
 * Позиционирование per design-reference v3: всегда snap к inline-end
 * краю (правый в LTR, левый в RTL). Когда detail panel открыт - shift
 * на ширину panel + gap, чтобы visible одновременно с панелью.
 */
function CompactMiniMap({ detailOpen = false }: MiniMapProps) {
  const t = useT();
  const nodes = useNodes();
  const edges = useEdges();
  // RF Transform = [tx, ty, zoom] (tuple, не объект)
  const [tx, ty, zoom] = useStore((s) => s.transform);
  const canvasW = useStore((s) => s.width);
  const canvasH = useStore((s) => s.height);
  const { setViewport, zoomIn, zoomOut, fitView } = useReactFlow();

  const [expanded, setExpanded] = useState(false);
  const svgRef = useRef<SVGSVGElement | null>(null);

  const W = expanded ? EXPANDED_W : COMPACT_W;
  const H = expanded ? EXPANDED_H : COMPACT_H;

  const bbox = getBoundingBox(nodes);

  // viewport-rectangle в координатах canvas (тех же что и узлы):
  // top-left = inverse-transform экранного (0,0)
  // size = размер canvas-вьюпорта в canvas-координатах
  const viewport = {
    x: -tx / zoom,
    y: -ty / zoom,
    w: canvasW / zoom,
    h: canvasH / zoom,
  };

  // итоговый viewBox - объединение bbox узлов и текущего viewport,
  // чтобы оба влезли с отступом
  const viewportBox: BBox = {
    minX: viewport.x,
    minY: viewport.y,
    maxX: viewport.x + viewport.w,
    maxY: viewport.y + viewport.h,
  };
  const view: BBox = bbox
    ? expandBounds(bbox, viewportBox, PAD)
    : { minX: 0, minY: 0, maxX: canvasW, maxY: canvasH };
  const viewW = view.maxX - view.minX;
  const viewH = view.maxY - view.minY;

  // карта позиций для отрисовки рёбер (центры узлов)
  const centers = new Map<string, { cx: number; cy: number }>();
  for (const n of nodes) {
    const w = n.measured?.width ?? n.width ?? 288;
    const h = n.measured?.height ?? n.height ?? 120;
    centers.set(n.id, {
      cx: n.position.x + w / 2,
      cy: n.position.y + h / 2,
    });
  }

  // click на minimap → переместить камеру так чтобы клик стал центром.
  // вычисляем точку в canvas-координатах через relative click + viewBox scale
  const handleClick = useCallback(
    (event: React.MouseEvent<SVGSVGElement>) => {
      const svg = svgRef.current;
      if (!svg) return;
      const rect = svg.getBoundingClientRect();
      const clickX = event.clientX - rect.left;
      const clickY = event.clientY - rect.top;
      // viewBox занимает полный svg, scale одинаков по обеим осям
      const scaleX = viewW / rect.width;
      const scaleY = viewH / rect.height;
      const flowX = view.minX + clickX * scaleX;
      const flowY = view.minY + clickY * scaleY;
      // центрируем canvas-вьюпорт на (flowX, flowY): tx = -flowX*zoom + canvasW/2
      setViewport({
        x: -flowX * zoom + canvasW / 2,
        y: -flowY * zoom + canvasH / 2,
        zoom,
      });
    },
    [viewW, viewH, view.minX, view.minY, zoom, canvasW, canvasH, setViewport],
  );

  return (
    <div
      // bottom-3 на inline-end (right в LTR / left в RTL). При открытом
      // detail panel (w-[400px] на end-0) сдвигаемся на 416px чтобы оба
      // элемента были visible одновременно
      className={`absolute bottom-3 z-10 overflow-hidden rounded-md border border-border bg-elevated shadow-sh3 ${
        detailOpen ? 'end-[416px]' : 'end-3'
      }`}
      style={{ width: W }}
    >
      {/* Zoom toolbar row над SVG: -, +, fit, expand. Прижат к MiniMap
         как visual cluster - один блок controls graph viewport'а.
         Раньше zoom-controls жили отдельным Panel'ом с фикс offset'ом
         mb-[182px] - при expanded MiniMap (336px высота) контролы
         скрывались за ним. В composite-блоке этой проблемы нет:
         row всегда сверху SVG и масштабируется с ним. */}
      <div className="flex items-center justify-end gap-0.5 border-b border-border bg-elevated px-1 py-1">
        <IconButton
          icon={ZoomOut}
          label={t('graph.zoom_out')}
          size="sm"
          onClick={() => zoomOut()}
        />
        <IconButton
          icon={ZoomIn}
          label={t('graph.zoom_in')}
          size="sm"
          onClick={() => zoomIn()}
        />
        <IconButton
          icon={Maximize}
          label={t('graph.fit_by_size')}
          size="sm"
          onClick={() => fitView({ padding: 0.2 })}
        />
        <div className="mx-0.5 h-5 w-px bg-ink-200" />
        <IconButton
          icon={expanded ? Minimize2 : Maximize2}
          label={t('graph.minimap')}
          size="sm"
          onClick={(e: React.MouseEvent) => {
            e.stopPropagation();
            setExpanded((v) => !v);
          }}
        />
      </div>
      <svg
        ref={svgRef}
        width={W}
        height={H}
        viewBox={`${view.minX} ${view.minY} ${viewW} ${viewH}`}
        preserveAspectRatio="xMidYMid meet"
        onClick={handleClick}
        className="block cursor-pointer bg-ink-50"
        aria-label={t('graph.minimap_aria')}
      >
        {/* рёбра под узлами */}
        {edges.map((e: Edge) => {
          const a = centers.get(e.source);
          const b = centers.get(e.target);
          if (!a || !b) return null;
          const edgeType = ((e.data as { edgeType?: EdgeType } | undefined)?.edgeType) ?? 'SUPPORTS';
          return (
            <line
              key={e.id}
              x1={a.cx}
              y1={a.cy}
              x2={b.cx}
              y2={b.cy}
              stroke={EDGE_HEX[edgeType]}
              strokeWidth={2}
              opacity={0.85}
              vectorEffect="non-scaling-stroke"
            />
          );
        })}

        {/* узлы как простые заполненные rect - цвет по типу,
            обводка по статусу. Без текста: на масштабах compact он
            нечитаем, в expanded - всё равно мелковат. Тип читается через цвет */}
        {nodes.map((n: Node) => {
          const data = n.data as NodeDto | undefined;
          const nodeType: NodeType = data?.nodeType ?? 'CLAIM';
          const status = data?.status ?? 'UNVERIFIED';
          const w = n.measured?.width ?? n.width ?? 288;
          const h = n.measured?.height ?? n.height ?? 120;
          return (
            <rect
              key={n.id}
              x={n.position.x}
              y={n.position.y}
              width={w}
              height={h}
              rx={8}
              fill={TYPE_FILL[nodeType]}
              fillOpacity={0.4}
              stroke={STATUS_STROKE[status]}
              strokeWidth={status === 'UNVERIFIED' ? 1 : 3}
              vectorEffect="non-scaling-stroke"
            />
          );
        })}

        {/* Viewport-индикатор камеры. Цвет через accent-500 - в обоих темах
            читаемый. fill / stroke - тот же token с разной opacity */}
        <rect
          x={viewport.x}
          y={viewport.y}
          width={viewport.w}
          height={viewport.h}
          fill="var(--c-accent-500)"
          fillOpacity={0.1}
          stroke="var(--c-accent-500)"
          strokeWidth={2}
          strokeDasharray="6 4"
          vectorEffect="non-scaling-stroke"
          pointerEvents="none"
        />
      </svg>
    </div>
  );
}

export default CompactMiniMap;
