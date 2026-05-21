import { useCallback, useEffect, useRef, useState } from 'react';
import type { Dispatch, SetStateAction, RefObject } from 'react';
import type { ReactFlowInstance } from '@xyflow/react';
import type { NodeCardNode } from '@/apps/argument-map/components/graph/NodeCard';
import type { CustomEdgeEdge } from '@/apps/argument-map/components/graph/CustomEdge';
import { apiPatchRaw } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import { applyLayout } from '@/apps/argument-map/utils/graphLayout';
import type { LayoutAlgorithm } from '@/shared/stores/layoutAlgorithmStore';

interface Args {
  lastNodesRef: RefObject<NodeCardNode[]>;
  edgesRef: RefObject<CustomEdgeEdge[]>;
  rfInstanceRef: RefObject<ReactFlowInstance<NodeCardNode, CustomEdgeEdge> | null>;
  setNodes: Dispatch<SetStateAction<NodeCardNode[]>>;
}

interface Result {
  triggerRelayout: (algorithm: LayoutAlgorithm) => Promise<void>;
  layoutPending: boolean;
}

/**
 * One-shot relayout trigger для любого из двух алгоритмов (dagre/elk).
 * Вызывается из GraphPanels когда user выбирает алгоритм в меню. Считает
 * новые позиции, применяет локально, PATCH'ит все узлы параллельно, потом
 * fitView для сохранения видимости.
 *
 * Для dagre передаётся forceLayout=true — без него `layoutGraph` бы вернул
 * сохранённые позиции as-is (allSaved early return) и меню стало бы no-op.
 *
 * Extracted from GraphCanvas (audit 2026-05-20 Minor #10).
 */
export function useAutoLayout({
  lastNodesRef,
  edgesRef,
  rfInstanceRef,
  setNodes,
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
    async (algorithm: LayoutAlgorithm) => {
      if (lastNodesRef.current.length === 0) return;
      setLayoutPending(true);
      try {
        const currentNodes = lastNodesRef.current;
        const laidOut = await applyLayout(
          currentNodes,
          edgesRef.current,
          algorithm,
          'LR',
          [],
          // forceLayout=true для dagre - игнорирует saved posX/posY,
          // считает с нуля. ELK всегда forces, флаг ему semantic no-op
          true,
        );
        setNodes(laidOut);
        const results = await Promise.allSettled(
          laidOut.map((n) =>
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
