import type { Dispatch, SetStateAction } from 'react';
import type { NodeCardNode } from '@/apps/argument-map/components/graph/NodeCard';
import type { CustomEdgeEdge } from '@/apps/argument-map/components/graph/CustomEdge';
import { apiPostRaw, ApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';

interface Args {
  /** ref на текущее состояние nodes - читается для optimistic compute */
  nodesRef: { current: NodeCardNode[] };
  setNodes: Dispatch<SetStateAction<NodeCardNode[]>>;
  setEdges: Dispatch<SetStateAction<CustomEdgeEdge[]>>;
  /** refetch графа после persistent z-order change */
  onRefetch: () => void;
}

interface ZOrderHandlers {
  bringNodeToFront: (id: string) => void;
  sendNodeToBack: (id: string) => void;
  bringEdgeToFront: (id: string) => void;
  sendEdgeToBack: (id: string) => void;
}

/**
 * Z-order операции для узлов и рёбер графа.
 *
 * Узлы - persistent через POST /api/v1/nodes/{id}/z-order/{bring-to-front|send-to-back}.
 * Сервер возвращает новый zIndex (max+1 / min-1 от всех узлов темы),
 * refetch синхронизирует значение. Optimistic local update даёт мгновенный
 * visual feedback - до refetch'а узел уже наверху/внизу.
 *
 * Рёбра - персистится через POST /api/v1/edges/{id}/z-order/* (mirror nodes).
 * Optimistic update + refetch синхронизирует server-computed z_index.
 *
 * Вынесено из GraphCanvas (audit 2026-05-18 I-2) - 40 LOC self-contained
 * логики без зависимости от остального RF state. Тестируется отдельно.
 */
export function useGraphZOrder({
  nodesRef,
  setNodes,
  setEdges,
  onRefetch,
}: Args): ZOrderHandlers {
  const t = useT();

  function bringNodeToFront(id: string): void {
    const optimisticZ =
      nodesRef.current.reduce((acc, n) => Math.max(acc, n.zIndex ?? 0), 0) + 1;
    setNodes((nds) => nds.map((n) => (n.id === id ? { ...n, zIndex: optimisticZ } : n)));
    apiPostRaw(`/api/v1/nodes/${id}/z-order/bring-to-front`, {})
      .then(() => onRefetch())
      .catch((e: unknown) => {
        const msg = e instanceof ApiError ? e.problem.title : (e as Error).message;
        toast.error(`${t('graph.toast.update_failed')}: ${msg}`);
        onRefetch();
      });
  }

  function sendNodeToBack(id: string): void {
    const optimisticZ =
      nodesRef.current.reduce((acc, n) => Math.min(acc, n.zIndex ?? 0), 0) - 1;
    setNodes((nds) => nds.map((n) => (n.id === id ? { ...n, zIndex: optimisticZ } : n)));
    apiPostRaw(`/api/v1/nodes/${id}/z-order/send-to-back`, {})
      .then(() => onRefetch())
      .catch((e: unknown) => {
        const msg = e instanceof ApiError ? e.problem.title : (e as Error).message;
        toast.error(`${t('graph.toast.update_failed')}: ${msg}`);
        onRefetch();
      });
  }

  function bringEdgeToFront(id: string): void {
    setEdges((eds) =>
      eds.map((e) => {
        if (e.id !== id) return e;
        const maxZ = eds.reduce((acc, x) => Math.max(acc, x.zIndex ?? 0), 0);
        return { ...e, zIndex: maxZ + 1 };
      }),
    );
    apiPostRaw(`/api/v1/edges/${id}/z-order/bring-to-front`, {})
      .then(() => onRefetch())
      .catch((e: unknown) => {
        const msg = e instanceof ApiError ? e.problem.title : (e as Error).message;
        toast.error(`${t('graph.toast.update_failed')}: ${msg}`);
        onRefetch();
      });
  }

  function sendEdgeToBack(id: string): void {
    setEdges((eds) =>
      eds.map((e) => {
        if (e.id !== id) return e;
        const minZ = eds.reduce((acc, x) => Math.min(acc, x.zIndex ?? 0), 0);
        return { ...e, zIndex: minZ - 1 };
      }),
    );
    apiPostRaw(`/api/v1/edges/${id}/z-order/send-to-back`, {})
      .then(() => onRefetch())
      .catch((e: unknown) => {
        const msg = e instanceof ApiError ? e.problem.title : (e as Error).message;
        toast.error(`${t('graph.toast.update_failed')}: ${msg}`);
        onRefetch();
      });
  }

  return { bringNodeToFront, sendNodeToBack, bringEdgeToFront, sendEdgeToBack };
}
