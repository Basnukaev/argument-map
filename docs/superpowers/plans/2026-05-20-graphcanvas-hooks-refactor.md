# GraphCanvas Hook Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract three self-contained logical groups from `GraphCanvas.tsx` (990 lines) into custom hooks, reducing the file to ~600-700 lines while preserving 100% behavioral parity.

**Architecture:** Three hooks are extracted — `useNodeDelete`, `useBulkNodeActions`, and `useElkAutoLayout` — placed in `frontend/src/apps/argument-map/hooks/`. Each hook receives its dependencies as arguments and returns stable callbacks. `GraphCanvas.tsx` imports and calls the hooks, wiring returned values into JSX and event handlers.

**Tech Stack:** React 19, TypeScript strict, Vitest + RTL + MSW, `useCallback` for stable function references.

---

## Coupling Analysis (read before coding)

Before extracting, understand what each group touches:

### `useNodeDelete` candidates (lines ~392–504)
- `runDelete(nodeIds, edgeIds)` — async, uses: `rootNodeId`, `rawNodeDtos`, `setDeleting`, `setSelectedNodeIds`, `setSelectedEdgeIds`, `onRefetch`, `t`, and calls `restoreNodeFromSnapshot`
- `restoreNodeFromSnapshot(snapshot)` — async, uses: `onRefetch`, `t`
- `deleteOneNode(nodeId)` — useCallback, calls `runDelete`; deps: `rootNodeId`, `t` (and `runDelete` indirectly)
- `deleteOneEdge(edgeId)` — plain function, calls `runDelete`
- State owned: `deleting` (boolean)

**What hook returns:** `{ runDelete, deleteOneNode, deleteOneEdge, deleting }`

### `useBulkNodeActions` candidates (lines ~513–556)
- `runBulkStatusChange(targetIds, status)` — async, uses: `setBulkBusy`, `onRefetch`, `t`
- State owned: `bulkBusy` (boolean)

**What hook returns:** `{ runBulkStatusChange, bulkBusy }`

### `useElkAutoLayout` candidates (lines ~136–167)
- `triggerElkRelayout()` — async useCallback, uses: `lastNodesRef`, `edgesRef`, `rfInstanceRef`, `setNodes`, `setLayoutPending`, `t`
- State owned: `layoutPending` (boolean)

**What hook returns:** `{ triggerElkRelayout, layoutPending }`

### Do NOT extract
- `handleDelete()` — thin wrapper over `runDelete(selectedNodeIds, selectedEdgeIds)`; it sits at call-site where `selectedNodeIds`/`selectedEdgeIds` live. Keep in GraphCanvas.
- `handleNodeContextMenu`, `handleEdgeContextMenu`, `handlePaneContextMenu` — context menus build `items` arrays inline, heavy coupling to local state setters and hook-returned functions. Leave in GraphCanvas.

---

## File Map

**Create:**
- `frontend/src/apps/argument-map/hooks/useNodeDelete.ts`
- `frontend/src/apps/argument-map/hooks/useBulkNodeActions.ts`
- `frontend/src/apps/argument-map/hooks/useElkAutoLayout.ts`

**Modify:**
- `frontend/src/apps/argument-map/components/graph/GraphCanvas.tsx` — remove extracted code, import and call the three hooks

---

## Task 1: Extract `useElkAutoLayout`

**Files:**
- Create: `frontend/src/apps/argument-map/hooks/useElkAutoLayout.ts`
- Modify: `frontend/src/apps/argument-map/components/graph/GraphCanvas.tsx`

This hook is the most independent — it only needs refs and setters from RF state, and returns a single callback + loading flag.

- [ ] **Step 1: Create `useElkAutoLayout.ts`**

```typescript
import { useCallback, useRef, useState } from 'react';
import type { Dispatch, SetStateAction, MutableRefObject } from 'react';
import type { ReactFlowInstance } from '@xyflow/react';
import type { NodeCardNode } from '@/apps/argument-map/components/graph/NodeCard';
import type { CustomEdgeEdge } from '@/apps/argument-map/components/graph/CustomEdge';
import { apiPatchRaw } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import { applyLayout } from '@/apps/argument-map/utils/graphLayout';

interface Args {
  lastNodesRef: MutableRefObject<NodeCardNode[]>;
  edgesRef: MutableRefObject<CustomEdgeEdge[]>;
  rfInstanceRef: MutableRefObject<ReactFlowInstance<NodeCardNode, CustomEdgeEdge> | null>;
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
      setTimeout(() => rfInstanceRef.current?.fitView({ padding: 0.15 }), 50);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      toast.error(`${t('layout.failed')}: ${msg}`);
    } finally {
      setLayoutPending(false);
    }
  }, [lastNodesRef, edgesRef, rfInstanceRef, setNodes, t]);

  return { triggerElkRelayout, layoutPending };
}
```

- [ ] **Step 2: Update `GraphCanvas.tsx` — import hook, remove extracted code, wire result**

Find and remove the `layoutPending` state declaration and `triggerElkRelayout` useCallback from GraphCanvas.tsx (lines ~92 and ~136–167). Add import and call site.

In the imports section, add:
```typescript
import { useElkAutoLayout } from '@/apps/argument-map/hooks/useElkAutoLayout';
```

Replace the `layoutPending` state + `triggerElkRelayout` useCallback block with:
```typescript
  const { triggerElkRelayout, layoutPending } = useElkAutoLayout({
    lastNodesRef,
    edgesRef,
    rfInstanceRef,
    setNodes,
  });
```

The `rfInstanceRef` type annotation in the hook is:
```typescript
MutableRefObject<ReactFlowInstance<NodeCardNode, CustomEdgeEdge> | null>
```
Make sure the import of `MutableRefObject` is added to the hook file (it's already in the code above).

- [ ] **Step 3: TypeScript check**

```bash
cd /home/basnukaev/projects/argument-map/frontend && npx tsc --noEmit -p tsconfig.app.json 2>&1 | head -40
```

Expected: no errors (or only pre-existing errors unrelated to these files).

---

## Task 2: Extract `useNodeDelete`

**Files:**
- Create: `frontend/src/apps/argument-map/hooks/useNodeDelete.ts`
- Modify: `frontend/src/apps/argument-map/components/graph/GraphCanvas.tsx`

This hook owns the `deleting` boolean state and exposes `runDelete`, `deleteOneNode`, `deleteOneEdge`.

- [ ] **Step 1: Create `useNodeDelete.ts`**

```typescript
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
      // рёбра первыми чтобы не получить 404 если узел уже удалит ребро каскадом
      for (const edgeId of edgeIds) {
        try {
          await apiDeleteRaw(`/api/v1/edges/${edgeId}`);
        } catch (e: unknown) {
          if (!(e instanceof ApiError && e.status === 404)) throw e;
        }
      }

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
```

- [ ] **Step 2: Update `GraphCanvas.tsx` — import hook, remove extracted code, wire result**

Add to imports:
```typescript
import { useNodeDelete } from '@/apps/argument-map/hooks/useNodeDelete';
```

Remove from GraphCanvas.tsx:
- `const [deleting, setDeleting] = useState(false);` declaration
- The entire `restoreNodeFromSnapshot` async function
- The entire `runDelete` async function
- The `deleteOneNode` useCallback
- The `deleteOneEdge` plain function

Add after `rawNodeDtos` and `rootNodeId` are declared:
```typescript
  const { runDelete, deleteOneNode, deleteOneEdge, deleting } = useNodeDelete({
    rootNodeId,
    rawNodeDtos,
    setSelectedNodeIds,
    setSelectedEdgeIds,
    onRefetch,
  });
```

Note: `setSelectedNodeIds` and `setSelectedEdgeIds` are declared just above with `useState`. The hook call can go right after those declarations.

- [ ] **Step 3: TypeScript check**

```bash
cd /home/basnukaev/projects/argument-map/frontend && npx tsc --noEmit -p tsconfig.app.json 2>&1 | head -40
```

Expected: no errors.

---

## Task 3: Extract `useBulkNodeActions`

**Files:**
- Create: `frontend/src/apps/argument-map/hooks/useBulkNodeActions.ts`
- Modify: `frontend/src/apps/argument-map/components/graph/GraphCanvas.tsx`

This is the simplest extraction — `runBulkStatusChange` plus its `bulkBusy` state.

- [ ] **Step 1: Create `useBulkNodeActions.ts`**

```typescript
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
```

- [ ] **Step 2: Update `GraphCanvas.tsx` — import hook, remove extracted code, wire result**

Add to imports:
```typescript
import { useBulkNodeActions } from '@/apps/argument-map/hooks/useBulkNodeActions';
```

Remove from GraphCanvas.tsx:
- `const [bulkBusy, setBulkBusy] = useState(false);`
- The entire `runBulkStatusChange` async function

Add after `useNodeDelete` call:
```typescript
  const { runBulkStatusChange, bulkBusy } = useBulkNodeActions({ onRefetch });
```

- [ ] **Step 3: Remove unused imports from GraphCanvas.tsx**

After extraction, `GraphCanvas.tsx` may no longer directly use some imports. Check and remove unused ones:
- `apiDeleteRaw` — moved to `useNodeDelete`
- `apiDeleteWithBody` — moved to `useNodeDelete`
- `apiPost` — moved to `useNodeDelete`
- `BulkDeleteResponse` type — moved to `useNodeDelete`
- `NodeStatus` type from `designTokens` — moved to `useBulkNodeActions`

Keep in GraphCanvas.tsx:
- `apiPatchRaw` — still used for reconnect, node-drag-stop, and the backfill effect
- `ApiError` — still used in `handleReconnect` and `handleNodeDragStop`
- `useGraphSelectionStore` — still used in `handleSelectionChange` and `clearSelection`

- [ ] **Step 4: TypeScript check**

```bash
cd /home/basnukaev/projects/argument-map/frontend && npx tsc --noEmit -p tsconfig.app.json 2>&1 | head -40
```

Expected: no errors.

---

## Task 4: Final verification

**Files:**
- No new files
- Verify all tests pass, lint clean, build succeeds

- [ ] **Step 1: Run lint + build + tests**

```bash
cd /home/basnukaev/projects/argument-map/frontend && npm run lint && npm run build && npm test -- --run 2>&1 | tail -30
```

Expected:
- Lint: 0 errors, 0 warnings (or same pre-existing warnings as before)
- Build: success
- Tests: same number as before (578+), all passing

- [ ] **Step 2: Check GraphCanvas line count**

```bash
wc -l /home/basnukaev/projects/argument-map/frontend/src/apps/argument-map/components/graph/GraphCanvas.tsx
```

Expected: roughly 620–700 lines (was 1000, moved ~300 lines to three hooks).

- [ ] **Step 3: Commit (three atomic commits, one per hook extraction)**

Commit 1 (after Task 1):
```bash
git add frontend/src/apps/argument-map/hooks/useElkAutoLayout.ts \
        frontend/src/apps/argument-map/components/graph/GraphCanvas.tsx
git commit -m "$(cat <<'EOF'
refactor(frontend): extract useElkAutoLayout from GraphCanvas

ELK one-shot relayout trigger (layoutPending state + triggerElkRelayout
callback) extracted into useElkAutoLayout hook. GraphCanvas reduced ~30
lines. Behavioral parity preserved, existing tests unmodified.

Closes audit Minor #10 (partial).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Commit 2 (after Task 2):
```bash
git add frontend/src/apps/argument-map/hooks/useNodeDelete.ts \
        frontend/src/apps/argument-map/components/graph/GraphCanvas.tsx
git commit -m "$(cat <<'EOF'
refactor(frontend): extract useNodeDelete from GraphCanvas

runDelete, deleteOneNode, deleteOneEdge, restoreNodeFromSnapshot and
deleting state extracted into useNodeDelete hook. GraphCanvas reduced
~120 lines. Behavioral parity preserved, existing tests unmodified.

Closes audit Minor #10 (partial).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Commit 3 (after Task 3 + Task 4):
```bash
git add frontend/src/apps/argument-map/hooks/useBulkNodeActions.ts \
        frontend/src/apps/argument-map/components/graph/GraphCanvas.tsx
git commit -m "$(cat <<'EOF'
refactor(frontend): extract useBulkNodeActions from GraphCanvas

runBulkStatusChange and bulkBusy state extracted into useBulkNodeActions
hook. GraphCanvas drops from 990 to ~650 lines total. All three hooks
live in apps/argument-map/hooks/. Lint, build, and 578+ tests passing.

Closes audit Minor #10.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review

### Spec coverage

| Requirement | Task |
|---|---|
| Extract bulk operations (lines ~499-545) → `useBulkNodeActions` | Task 3 |
| Extract ELK layout trigger (lines ~136-163) → `useElkAutoLayout` | Task 1 |
| Extract delete logic (lines ~392-497) → `useNodeDelete` | Task 2 |
| Behavioral parity — no UX/behavior changes | Tasks 1-3 preserve all logic verbatim |
| Hooks return stable functions via `useCallback` | `deleteOneNode` is wrapped in `useCallback`; `triggerElkRelayout` is wrapped in `useCallback` |
| Existing tests pass without modification | Task 4 verification |
| TypeScript strict, no `any` | Checked in each task |
| `handleNodeContextMenu` deps array remains correct | `deleteOneNode` is still stable via `useCallback([rootNodeId, t])` |
| `nodeTypes`/`edgeTypes` module-level constants untouched | Not in extracted sections |

### Placeholder scan — none found

### Type consistency

- `NodeDto` = `components['schemas']['NodeResponse']` used consistently in `useNodeDelete`
- `BulkDeleteResponse` = `components['schemas']['BulkDeleteResponse']` used in `useNodeDelete`
- `NodeStatus` imported from `@/shared/utils/designTokens` in `useBulkNodeActions`
- `NodeCardNode`, `CustomEdgeEdge` types imported from their respective component files in `useElkAutoLayout`
- All `MutableRefObject` refs typed correctly in `useElkAutoLayout`
- `runDelete` return type `Promise<boolean>` consistent between definition in `useNodeDelete` and usage in `GraphCanvas` (`handleDelete`)

### Potential issue: `runDelete` in `deleteOneNode` eslint-disable comment

The original code had:
```typescript
// runDelete - plain async function, recreated each render - using stable
// indirect deps instead. rootNodeId и t - stable enough (topic ref / i18n)
// eslint-disable-next-line react-hooks/exhaustive-deps
[rootNodeId, t],
```

This comment + eslint-disable is preserved in `useNodeDelete.ts`. Since `runDelete` is now defined inside the hook (not inside a component), and `deleteOneNode` is a `useCallback` inside the hook, the eslint-disable is still appropriate.

### Potential issue: `useGraphSelectionStore` import in `useNodeDelete`

`useGraphSelectionStore` is called as `useGraphSelectionStore.getState().clearSelection()` — imperative access, not reactive subscription. This is correct usage (no render cycle issues) and works in a hook context.
