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
import { STATUS_TOKENS } from '@/shared/utils/designTokens';
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
 * Четыре статичных Panel поверх React Flow:
 * - top-left: вертикальная toolbar (Add Node/Edge, toggle labels, Delete)
 * - top-right: hotkeys-hint
 * - bottom-left: легенда статусов
 * - bottom-center: zoom controls (только если rfInstance готов)
 *
 * Вынесено из {@link GraphCanvas} чтобы основной компонент держал только
 * stateful логику. Все эти панели чистые презентационные (UI + callbacks).
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
        className="!m-3 flex w-12 flex-col items-center gap-1 rounded-md border border-slate-200 bg-white/95 py-2 shadow-md backdrop-blur"
      >
        <IconButton icon={Plus} label={t('graph.add_node')} size="md" onClick={onAddNode} />
        <IconButton
          icon={Link2}
          label={canAddEdge ? t('graph.create_edge') : t('graph.need_two_nodes')}
          size="md"
          disabled={!canAddEdge}
          onClick={onAddEdge}
        />
        <div className="my-1 h-px w-7 bg-slate-200" />
        <IconButton
          icon={showEdgeLabels ? Eye : EyeOff}
          label={showEdgeLabels ? t('graph.hide_edge_labels') : t('graph.show_edge_labels')}
          size="md"
          active={showEdgeLabels}
          onClick={onToggleLabels}
        />
        <div className="my-1 h-px w-7 bg-slate-200" />
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
          className={selectedCount > 0 && !deleting ? '!text-red-600 hover:!bg-red-50' : ''}
        />
      </Panel>

      <Panel
        position="top-right"
        className="!m-3 flex items-center gap-3 rounded-md border border-slate-200 bg-white/95 px-3 py-2 text-[11px] text-slate-600 shadow-sm backdrop-blur"
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

      <Panel
        position="bottom-left"
        className="!m-3 max-w-[280px] rounded-md border border-slate-200 bg-white/95 p-3 shadow-md backdrop-blur"
      >
        <div className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-slate-500">
          {t('status.legend')}
        </div>
        <div className="grid grid-cols-2 gap-x-3 gap-y-1.5">
          {(Object.keys(STATUS_TOKENS) as Array<keyof typeof STATUS_TOKENS>).map((key) => {
            const token = STATUS_TOKENS[key];
            return (
              <div key={key} className="flex items-center gap-1.5 text-[11px] text-slate-700">
                <span className={`h-2.5 w-3 rounded-sm ${token.bar}`} aria-hidden="true" />
                {t(token.labelKey)}
              </div>
            );
          })}
        </div>
      </Panel>

      {rfInstance && (
        <Panel
          position="bottom-center"
          className="!m-3 flex items-center gap-0.5 rounded-md border border-slate-200 bg-white/95 p-1 shadow-md backdrop-blur"
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
          <div className="mx-1 h-5 w-px bg-slate-200" />
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
