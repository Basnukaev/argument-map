import { useState } from 'react';
import { apiPatchRaw, ApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import type { NodeStatus } from '@/shared/utils/designTokens';

interface Args {
  onRefetch: () => void;
}

interface Result {
  runBulkStatusChange: (targetIds: string[], status: NodeStatus) => Promise<void>;
  bulkBusy: boolean;
}

/**
 * Bulk status-change for selected nodes.
 *
 * Parallel PATCH /api/v1/nodes/{id} with {status}. Partial-failure aware:
 * uses Promise.allSettled, counts successes/failures, shows combined toast.
 * Permission-aware: if all requests return 403 forbidden-topic-write, shows
 * explicit "no permission" toast instead of generic error.
 *
 * Extracted from GraphCanvas (audit 2026-05-20 Minor #10).
 */
export function useBulkNodeActions({ onRefetch }: Args): Result {
  const t = useT();
  const [bulkBusy, setBulkBusy] = useState(false);

  // Bulk-status: parallel PATCH /api/v1/nodes/{id} с {status}. Partial-failure
  // aware - используем Promise.allSettled, считаем успехи/провалы, выдаём
  // комбинированный toast. После - onRefetch синхронизирует UI
  async function runBulkStatusChange(targetIds: string[], status: NodeStatus): Promise<void> {
    if (targetIds.length === 0) {
      toast.warning(t('bulk_actions.warn.no_writable_nodes'));
      return;
    }
    setBulkBusy(true);
    try {
      const results = await Promise.allSettled(
        targetIds.map((id) => apiPatchRaw(`/api/v1/nodes/${id}`, { status })),
      );
      const successes = results.filter((r) => r.status === 'fulfilled').length;
      const failures = results.length - successes;

      if (successes === 0) {
        // permission-aware error: если все 403 (отозвали права во время
        // выделения - типичный сценарий: owner SHARED-темы убрал EDITOR
        // membership пока пользователь делал bulk action) - показываем
        // explicit "нет прав" вместо generic "не удалось"
        const firstFailure = results.find(
          (r): r is PromiseRejectedResult => r.status === 'rejected',
        );
        const reason = firstFailure?.reason;
        if (reason instanceof ApiError && reason.is('forbidden-topic-write')) {
          toast.error(t('bulk_actions.error.permission_denied'));
        } else {
          toast.error(t('bulk_actions.error.all_failed'));
        }
      } else if (failures > 0) {
        toast.warning(
          t('bulk_actions.success.status_updated_partial')
            .replace('{success}', String(successes))
            .replace('{total}', String(results.length)),
        );
      } else {
        toast.success(
          t('bulk_actions.success.status_updated').replace('{count}', String(successes)),
        );
      }
      onRefetch();
    } finally {
      setBulkBusy(false);
    }
  }

  return { runBulkStatusChange, bulkBusy };
}
