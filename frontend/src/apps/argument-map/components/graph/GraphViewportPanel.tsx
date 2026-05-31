import { useCallback, useMemo } from 'react';
import { useNodes, useEdges, useStore, useReactFlow } from '@xyflow/react';
import type { Node, Edge } from '@xyflow/react';
import ZoomControls from '@/apps/argument-map/components/graph/ZoomControls';
import MinimapCard from '@/apps/argument-map/components/graph/MinimapCard';
import type { MinimapNode, MinimapEdge, MinimapViewport } from '@/apps/argument-map/components/graph/MinimapCard';
import type { components } from '@/shared/api/types';

type NodeDto = components['schemas']['NodeResponse'];

const DEFAULT_NODE_W = 288;
const DEFAULT_NODE_H = 120;

interface GraphViewportPanelProps {
  detailOpen?: boolean;
}

function GraphViewportPanel({ detailOpen = false }: GraphViewportPanelProps) {
  const rfNodes = useNodes();
  const rfEdges = useEdges();
  const [tx, ty, zoom] = useStore((s) => s.transform);
  const canvasW = useStore((s) => s.width);
  const canvasH = useStore((s) => s.height);
  const { setViewport, fitView, zoomTo } = useReactFlow();

  const minimapNodes: MinimapNode[] = useMemo(
    () =>
      rfNodes.map((n: Node) => {
        const data = n.data as NodeDto | undefined;
        const w = n.measured?.width ?? n.width ?? DEFAULT_NODE_W;
        const h = n.measured?.height ?? n.height ?? DEFAULT_NODE_H;
        return {
          id: n.id,
          x: n.position.x,
          y: n.position.y,
          w,
          h,
          type: (data?.nodeType ?? 'CLAIM') as MinimapNode['type'],
          selected: n.selected ?? false,
        };
      }),
    [rfNodes],
  );

  const minimapEdges: MinimapEdge[] = useMemo(
    () => rfEdges.map((e: Edge) => ({ from: e.source, to: e.target })),
    [rfEdges],
  );

  const viewport: MinimapViewport = useMemo(
    () => ({
      x: -tx / zoom,
      y: -ty / zoom,
      w: canvasW / zoom,
      h: canvasH / zoom,
    }),
    [tx, ty, zoom, canvasW, canvasH],
  );

  const selectedCount = rfNodes.filter((n) => n.selected).length;
  const hasSelection = selectedCount > 0;

  const handleViewportChange = useCallback(
    (v: MinimapViewport) => {
      setViewport({ x: -v.x * zoom, y: -v.y * zoom, zoom });
    },
    [zoom, setViewport],
  );

  const handleZoomChange = useCallback(
    (z: number) => {
      zoomTo(z);
    },
    [zoomTo],
  );

  const handleFit = useCallback(() => {
    fitView({ padding: 0.2 });
  }, [fitView]);

  const handleFitSelection = useCallback(() => {
    const selectedNodes = rfNodes.filter((n) => n.selected);
    if (selectedNodes.length === 0) return;
    fitView({
      padding: 0.3,
      nodes: selectedNodes,
    });
  }, [rfNodes, fitView]);

  return (
    <div
      className={`absolute bottom-3 z-10 flex flex-col items-end gap-2.5 ${
        detailOpen ? 'end-[416px]' : 'end-3'
      }`}
    >
      <ZoomControls
        zoom={zoom}
        min={0.2}
        max={1.5}
        step={0.1}
        onZoomChange={handleZoomChange}
        onFit={handleFit}
        onFitSelection={handleFitSelection}
        hasSelection={hasSelection}
      />
      <MinimapCard
        nodes={minimapNodes}
        edges={minimapEdges}
        viewport={viewport}
        zoom={zoom}
        onViewportChange={handleViewportChange}
        onCenterOnSelection={hasSelection ? handleFitSelection : undefined}
      />
    </div>
  );
}

export default GraphViewportPanel;
