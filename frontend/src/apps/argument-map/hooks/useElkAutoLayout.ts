import { useCallback, useEffect, useRef, useState } from 'react';
import type { Dispatch, SetStateAction, RefObject } from 'react';
import type { ReactFlowInstance } from '@xyflow/react';
import type { NodeCardNode } from '@/apps/argument-map/components/graph/NodeCard';
import type { CustomEdgeEdge } from '@/apps/argument-map/components/graph/CustomEdge';
import { apiPatchRaw } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import { applyLayout } from '@/apps/argument-map/utils/graphLayout';

interface Args {
  lastNodesRef: RefObject<NodeCardNode[]>;
  edgesRef: RefObject<CustomEdgeEdge[]>;
  rfInstanceRef: RefObject<ReactFlowInstance<NodeCardNode, CustomEdgeEdge> | null>;
  setNodes: Dispatch<SetStateAction<NodeCardNode[]>>;
}

interface Result {
  triggerElkRelayout: () => Promise<void>;
  layoutPending: boolean;
}

/**
 * ELK one-shot relayout trigger. Called from GraphPanels when user picks ELK
 * in the layout-menu. Computes new positions, applies them locally, then PATCHes
 * all nodes in parallel. Calls fitView after layout to keep the graph visible.
 *
 * Extracted from GraphCanvas (audit 2026-05-20 Minor #10).
 */
export function useElkAutoLayout({
  lastNodesRef,
  edgesRef,
  rfInstanceRef,
  setNodes,
}: Args): Result {
  const t = useT();
  const [layoutPending, setLayoutPending] = useState(false);
  // Cleanup для отложенного fitView - если user navigate away в течение 50ms
  // после ELK relayout, fitView выполнялся бы на torn-down RF instance
  // (защищено через ?., но таймер не cleanup'ался)
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

  const triggerElkRelayout = useCallback(async () => {
    if (lastNodesRef.current.length === 0) return;
    setLayoutPending(true);
    try {
      const currentNodes = lastNodesRef.current;
      const laidOut = await applyLayout(currentNodes, edgesRef.current, 'elk', 'LR');
      setNodes(laidOut);
      // PATCH все узлы параллельно - Promise.allSettled чтобы partial failures
      // не блокировали (graceful degradation: при ошибке next ELK-trigger
      // перерассчитает позиции)
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
      // fitView после layout - иначе ELK может разложить узлы за viewport
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
  }, [lastNodesRef, edgesRef, rfInstanceRef, setNodes, t]);

  return { triggerElkRelayout, layoutPending };
}
