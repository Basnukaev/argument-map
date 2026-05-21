import { useCallback, useEffect, useRef, useState } from 'react';
import type { Dispatch, SetStateAction, RefObject } from 'react';
import type { ReactFlowInstance } from '@xyflow/react';
import type { NodeCardNode } from '@/apps/argument-map/components/graph/NodeCard';
import type { CustomEdgeEdge } from '@/apps/argument-map/components/graph/CustomEdge';
import { apiPatchRaw } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import { applyLayout } from '@/apps/argument-map/utils/graphLayout';
import { pickHandlesByPosition } from '@/apps/argument-map/utils/graphHandles';
import type { LayoutPreset } from '@/shared/stores/layoutPresetStore';

interface Args {
  lastNodesRef: RefObject<NodeCardNode[]>;
  edgesRef: RefObject<CustomEdgeEdge[]>;
  rfInstanceRef: RefObject<ReactFlowInstance<NodeCardNode, CustomEdgeEdge> | null>;
  setNodes: Dispatch<SetStateAction<NodeCardNode[]>>;
  setEdges: Dispatch<SetStateAction<CustomEdgeEdge[]>>;
}

interface Result {
  triggerRelayout: (preset: LayoutPreset) => Promise<void>;
  layoutPending: boolean;
}

/**
 * One-shot relayout trigger для выбранного preset'а формы графа.
 * Вызывается из GraphPanels когда user picks preset в меню. Считает
 * новые позиции через ELK с type-aware constraints (QUESTION top,
 * EVIDENCE bottom для tree-presets), применяет локально, PATCH'ит
 * все узлы параллельно, потом fitView для сохранения видимости.
 *
 * Detailed preset → ELK config mapping в elkLayout.ts.
 */
export function useAutoLayout({
  lastNodesRef,
  edgesRef,
  rfInstanceRef,
  setNodes,
  setEdges,
}: Args): Result {
  const t = useT();
  const [layoutPending, setLayoutPending] = useState(false);
  const fitViewTimerRef = useRef<number | null>(null);
  useEffect(
    () => () => {
      if (fitViewTimerRef.current != null) {
        window.clearTimeout(fitViewTimerRef.current);
        fitViewTimerRef.current = null;
      }
    },
    [],
  );

  const triggerRelayout = useCallback(
    async (preset: LayoutPreset) => {
      if (lastNodesRef.current.length === 0) return;
      setLayoutPending(true);
      try {
        const currentNodes = lastNodesRef.current;
        const result = await applyLayout(currentNodes, edgesRef.current, preset);
        setNodes(result.nodes);

        // Recompute edge handles по новым positions + накатываем bend
        // points из layout-результата (для tree-presets ELK даёт bends,
        // для radial они undefined → CustomEdge fallback'нется на bezier).
        // Без recompute handles рёбра bezier'ятся через старые handles
        // → новые позиции и петлями уходят за viewport.
        const posById = new Map(result.nodes.map((n) => [n.id, n.position]));
        const bendsById = new Map(
          result.edges.map((e) => [e.id, e.data?.bendPoints]),
        );
        setEdges((prev) =>
          prev.map((e) => {
            const { source, target } = pickHandlesByPosition(
              posById.get(e.source),
              posById.get(e.target),
            );
            const bendPoints = bendsById.get(e.id);
            return {
              ...e,
              sourceHandle: source,
              targetHandle: target,
              data: e.data ? { ...e.data, bendPoints } : e.data,
            };
          }),
        );

        const results = await Promise.allSettled(
          result.nodes.map((n) =>
            apiPatchRaw(`/api/v1/nodes/${n.id}`, {
              posX: n.position.x,
              posY: n.position.y,
            }),
          ),
        );
        const failed = results.filter((r) => r.status === 'rejected').length;
        if (failed > 0) {
          toast.warning(t('layout.partial_save_failed').replace('{count}', String(failed)));
        }
        toast.success(t('layout.applied'));
        if (fitViewTimerRef.current != null) {
          window.clearTimeout(fitViewTimerRef.current);
        }
        fitViewTimerRef.current = window.setTimeout(() => {
          rfInstanceRef.current?.fitView({ padding: 0.15 });
          fitViewTimerRef.current = null;
        }, 50);
      } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : String(e);
        toast.error(`${t('layout.failed')}: ${msg}`);
      } finally {
        setLayoutPending(false);
      }
    },
    [lastNodesRef, edgesRef, rfInstanceRef, setNodes, t],
  );

  return { triggerRelayout, layoutPending };
}
