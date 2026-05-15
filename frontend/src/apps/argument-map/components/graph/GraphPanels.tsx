import { Panel, type ReactFlowInstance } from '@xyflow/react';
import {
  Plus,
  Trash2,
  Eye,
  EyeOff,
  Link2,
  ZoomIn,
  ZoomOut,
  Maximize,
} from 'lucide-react';
import IconButton from '@/shared/components/ui/IconButton';
import Kbd from '@/shared/components/ui/Kbd';
import { useT } from '@/shared/i18n';

interface Props {
  showEdgeLabels: boolean;
  onToggleLabels: () => void;
  canAddEdge: boolean;
  onAddNode: () => void;
  onAddEdge: () => void;
  selectedCount: number;
  deleting: boolean;
  onDelete: () => void;
  rfInstance: ReactFlowInstance<never, never> | null;
}

/**
 * Три статичных Panel поверх React Flow:
 * - top-left: вертикальная toolbar (Add Node/Edge, toggle labels, Delete)
 * - top-right: hotkeys-hint
 * - bottom-center: zoom controls (только если rfInstance готов)
 *
 * Status-легенда удалена per design-reference v3 - дублирует информацию
 * с StatusBadge на узлах и засоряет canvas.
 *
 * MiniMap живёт отдельно в CompactMiniMap (bottom-end, shift'нется когда
 * detail panel открыт).
 */
function GraphPanels({
  showEdgeLabels,
  onToggleLabels,
  canAddEdge,
  onAddNode,
  onAddEdge,
  selectedCount,
  deleting,
  onDelete,
  rfInstance,
}: Props) {
  const t = useT();
  return (
    <>
      <Panel
        position="top-left"
        className="!m-3 flex w-12 flex-col items-center gap-1 rounded-md border border-border bg-elevated/95 py-2 shadow-md backdrop-blur"
      >
        <IconButton icon={Plus} label={t('graph.add_node')} size="md" onClick={onAddNode} />
        <IconButton
          icon={Link2}
          label={canAddEdge ? t('graph.create_edge') : t('graph.need_two_nodes')}
          size="md"
          disabled={!canAddEdge}
          onClick={onAddEdge}
        />
        <div className="my-1 h-px w-7 bg-ink-200" />
        <IconButton
          icon={showEdgeLabels ? Eye : EyeOff}
          label={showEdgeLabels ? t('graph.hide_edge_labels') : t('graph.show_edge_labels')}
          size="md"
          active={showEdgeLabels}
          onClick={onToggleLabels}
        />
        <div className="my-1 h-px w-7 bg-ink-200" />
        <IconButton
          icon={Trash2}
          label={
            selectedCount === 0
              ? t('graph.delete_hint_empty')
              : `${t('graph.delete_count')} (${selectedCount})`
          }
          size="md"
          disabled={selectedCount === 0 || deleting}
          onClick={onDelete}
          className={selectedCount > 0 && !deleting ? '!text-err-700 hover:!bg-err-100' : ''}
        />
      </Panel>

      <Panel
        position="top-right"
        className="!m-3 flex items-center gap-3 rounded-md border border-border bg-elevated/95 px-3 py-2 text-xs text-ink-600 shadow-sm backdrop-blur"
      >
        <span className="inline-flex items-center gap-1">
          <Kbd>2x</Kbd> {t('graph.hint_details')}
        </span>
        <span className="inline-flex items-center gap-1">
          <Kbd>Del</Kbd> {t('graph.hint_delete')}
        </span>
        <span className="inline-flex items-center gap-1">
          <Kbd>RMB</Kbd> {t('graph.hint_context_menu')}
        </span>
      </Panel>

      {rfInstance && (
        <Panel
          position="bottom-center"
          className="!m-3 flex items-center gap-0.5 rounded-md border border-border bg-elevated/95 p-1 shadow-md backdrop-blur"
        >
          <IconButton
            icon={ZoomOut}
            label={t('graph.zoom_out')}
            size="sm"
            onClick={() => rfInstance.zoomOut()}
          />
          <IconButton
            icon={ZoomIn}
            label={t('graph.zoom_in')}
            size="sm"
            onClick={() => rfInstance.zoomIn()}
          />
          <div className="mx-1 h-5 w-px bg-ink-200" />
          <IconButton
            icon={Maximize}
            label={t('graph.fit_by_size')}
            size="sm"
            onClick={() => rfInstance.fitView({ padding: 0.2 })}
          />
        </Panel>
      )}
    </>
  );
}

export default GraphPanels;
