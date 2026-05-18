import { useRef } from 'react';
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
 * Рёбра - локальный z-order (не персистится на бэк, отдельный пункт
 * backlog). Hook хранит ref edgeZRef со счётчиками max/min для каждого
 * (re)mount графа. После refetch счётчики сбрасываются - acceptable,
 * пока persistence не добавлен.
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
  const edgeZRef = useRef({ max: 10, min: 0 });

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
    edgeZRef.current.max += 1;
    const z = edgeZRef.current.max;
    setEdges((eds) => eds.map((e) => (e.id === id ? { ...e, zIndex: z } : e)));
  }

  function sendEdgeToBack(id: string): void {
    edgeZRef.current.min -= 1;
    const z = edgeZRef.current.min;
    setEdges((eds) => eds.map((e) => (e.id === id ? { ...e, zIndex: z } : e)));
  }

  return { bringNodeToFront, sendNodeToBack, bringEdgeToFront, sendEdgeToBack };
}
