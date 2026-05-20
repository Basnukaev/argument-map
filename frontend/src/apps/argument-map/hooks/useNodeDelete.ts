import { useCallback, useState } from 'react';
import type { Dispatch, SetStateAction } from 'react';
import { apiDeleteRaw, apiDeleteWithBody, apiPost, ApiError } from '@/shared/api/client';
import { apiPatchRaw } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import { useGraphSelectionStore } from '@/shared/stores/graphSelectionStore';
import type { components } from '@/shared/api/types';

type NodeDto = components['schemas']['NodeResponse'];
type BulkDeleteResponse = components['schemas']['BulkDeleteResponse'];

interface Args {
  rootNodeId: string | null;
  rawNodeDtos: NodeDto[];
  setSelectedNodeIds: Dispatch<SetStateAction<string[]>>;
  setSelectedEdgeIds: Dispatch<SetStateAction<string[]>>;
  onRefetch: () => void;
}

interface Result {
  runDelete: (nodeIds: string[], edgeIds: string[]) => Promise<boolean>;
  deleteOneNode: (nodeId: string) => Promise<void>;
  deleteOneEdge: (edgeId: string) => Promise<void>;
  deleting: boolean;
}

/**
 * Unified delete logic for nodes and edges.
 *
 * runDelete — unified entry point used by context-menu, hotkey (Del/Backspace),
 * and toolbar bulk-delete. No window.confirm: intent expressed through explicit
 * action; reversibility via Undo toast for 5 seconds (Gmail/Slack pattern).
 * Bulk node delete uses single POST /api/v1/nodes/bulk; edges deleted individually
 * first (to avoid 404 cascade from node delete).
 *
 * deleteOneNode / deleteOneEdge — thin wrappers that guard against root-node
 * deletion and delegate to runDelete.
 *
 * Extracted from GraphCanvas (audit 2026-05-20 Minor #10).
 */
export function useNodeDelete({
  rootNodeId,
  rawNodeDtos,
  setSelectedNodeIds,
  setSelectedEdgeIds,
  onRefetch,
}: Args): Result {
  const t = useT();
  const [deleting, setDeleting] = useState(false);

  // Re-create узла из snapshot после undo. POST /nodes восстанавливает
  // type/content (без id - бэк выдаст новый), затем PATCH прокидывает posX/posY.
  // Edges НЕ восстанавливаются - re-create меняет id, а edges указывают на старые
  // id. Это документировано в hint'е к "Отменить" кнопке (см. i18n
  // graph.node.undo_no_edges_hint). Прагматичный trade-off: undo нужен для
  // случайных удалений leaf-узлов где edges и так минимальны
  async function restoreNodeFromSnapshot(snapshot: NodeDto): Promise<void> {
    if (!snapshot.topicId || !snapshot.nodeType || snapshot.content === undefined) return;
    try {
      const created = (await apiPost('/api/v1/nodes', {
        topicId: snapshot.topicId,
        nodeType: snapshot.nodeType,
        content: snapshot.content,
      })) as NodeDto;
      if (
        created.id &&
        snapshot.posX !== undefined &&
        snapshot.posY !== undefined
      ) {
        try {
          await apiPatchRaw(`/api/v1/nodes/${created.id}`, {
            posX: snapshot.posX,
            posY: snapshot.posY,
          });
        } catch {
          // позиционирование не блокирует восстановление
        }
      }
      onRefetch();
    } catch (e: unknown) {
      const msg = e instanceof ApiError ? e.problem.title : (e as Error).message;
      toast.error(`${t('graph.node.undo_failed')}: ${msg}`);
    }
  }

  /**
   * Единая точка удаления узлов/рёбер. Используется из context menu (один
   * узел/ребро) и хоткея Del/Backspace (bulk-selection). Семантика silent:
   * никакого `window.confirm` - намерение уже выражено через явный пункт
   * меню или Del-нажатие, плюс показанный toast с Undo даёт reversibility
   * на 5 секунд (паттерн Gmail/Slack).
   *
   * Возвращает true если хоть что-то реально удалили (для очистки selection).
   */
  async function runDelete(nodeIds: string[], edgeIds: string[]): Promise<boolean> {
    // фильтруем корневой узел локально для undo-snapshot и early-exit;
    // бэк сам пропустит корень (skippedRootIds) и не бросит 409
    const nodesToDelete = nodeIds.filter((id) => id !== rootNodeId);
    const rootSkippedLocally = nodesToDelete.length !== nodeIds.length;

    // snapshot для undo - до удаления, иначе rawNodeDtos уже не содержит
    const nodeSnapshots = nodesToDelete
      .map((id) => rawNodeDtos.find((n) => n.id === id))
      .filter((n): n is NodeDto => !!n);

    if (nodesToDelete.length === 0 && edgeIds.length === 0) {
      if (rootSkippedLocally) toast.warning(t('graph.root.delete_skipped_toast'));
      return false;
    }

    setDeleting(true);
    try {
      // рёбра первыми чтобы не получить 404 если узел уже удалит ребро каскадом.
      // Параллельный allSettled вместо последовательного await loop — для bulk
      // выделений в 10-20 рёбер разница ощутима (N round trips → 1 round trip).
      // 404 толерируем (race с node-каскадом), все остальные ошибки — fatal.
      const edgeResults = await Promise.allSettled(
        edgeIds.map((edgeId) => apiDeleteRaw(`/api/v1/edges/${edgeId}`)),
      );
      const fatalEdgeError = edgeResults
        .filter((r): r is PromiseRejectedResult => r.status === 'rejected')
        .map((r) => r.reason as unknown)
        .find((e) => !(e instanceof ApiError && e.status === 404));
      if (fatalEdgeError) throw fatalEdgeError;

      // узлы - один bulk DELETE /api/v1/nodes/bulk вместо N individual requests.
      // Бэк пишет единственную BULK_DELETE запись в audit log. Корневые узлы
      // пропускаются сервером (skippedRootIds) - дублируем предупреждение если
      // бэк вернул skipped.
      let serverSkippedRoot = false;
      if (nodesToDelete.length > 0) {
        const bulkResult = await apiDeleteWithBody<BulkDeleteResponse>(
          '/api/v1/nodes/bulk',
          { nodeIds: nodesToDelete },
        );
        serverSkippedRoot = (bulkResult.skippedRootIds?.length ?? 0) > 0;
      }

      setSelectedNodeIds([]);
      setSelectedEdgeIds([]);
      useGraphSelectionStore.getState().clearSelection();
      onRefetch();

      // undo - только если удалили хотя бы один узел (рёбра restoring не
      // реализован: они дешевле в воссоздании руками, и без полного snapshot
      // edges-таблицы при bulk-delete восстановление было бы хрупким)
      if (nodeSnapshots.length > 0) {
        const message =
          nodeSnapshots.length === 1
            ? t('graph.node.deleted_toast')
            : t('graph.node.deleted_toast_multi').replace('{count}', String(nodeSnapshots.length));
        // 5 секунд TTL для destructive action recovery (паттерн Gmail /
        // Slack / macOS Finder) - явно больше дефолтных 3 сек на success
        // toast: пользователю нужно прочитать сообщение и среагировать
        // на Undo, что не успеть за 3 сек особенно при bulk-delete
        toast.success(
          message,
          {
            label: t('graph.node.deleted_undo'),
            hint: t('graph.node.undo_no_edges_hint'),
            onClick: () => {
              void Promise.all(nodeSnapshots.map((s) => restoreNodeFromSnapshot(s)));
            },
          },
          { ttl: 5000 },
        );
      } else if (edgeIds.length > 0) {
        toast.success(t('graph.edge.deleted_toast'));
      }

      if (rootSkippedLocally || serverSkippedRoot) {
        toast.warning(t('graph.root.delete_skipped_toast'));
      }
      return true;
    } catch (e: unknown) {
      // permission-aware: при отзыве прав в момент удаления показываем
      // explicit "нет прав", иначе generic delete_failed с titlemessage.
      // Mirror runBulkStatusChange behavior (Code review round 4 #4)
      if (e instanceof ApiError && e.is('forbidden-topic-write')) {
        toast.error(t('bulk_actions.error.permission_denied'));
      } else {
        const msg = e instanceof ApiError ? e.problem.title : (e as Error).message;
        toast.error(`${t('graph.toast.delete_failed')}: ${msg}`);
      }
      return false;
    } finally {
      setDeleting(false);
    }
  }

  const deleteOneNode = useCallback(
    async (nodeId: string) => {
      // защитный barrier: context menu уже скрывает пункт удаления для корня,
      // но если новая точка входа добавится - не дать сделать заведомо
      // обречённый запрос (бэк бросит 409 NodeIsRootException)
      if (nodeId === rootNodeId) {
        toast.warning(t('graph.root.delete_hint'));
        return;
      }
      await runDelete([nodeId], []);
    },
    // runDelete - plain async function, recreated each render - using stable
    // indirect deps instead. rootNodeId и t - stable enough (topic ref / i18n)
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [rootNodeId, t],
  );

  async function deleteOneEdge(edgeId: string): Promise<void> {
    await runDelete([], [edgeId]);
  }

  return { runDelete, deleteOneNode, deleteOneEdge, deleting };
}
