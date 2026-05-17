import { useEffect, useRef, useState } from 'react';
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
  Download,
  FileImage,
  FileCode,
} from 'lucide-react';
import IconButton from '@/shared/components/ui/IconButton';
import Kbd from '@/shared/components/ui/Kbd';
import { useHotkey } from '@/shared/hooks/useHotkey';
import { useT } from '@/shared/i18n';
import {
  buildExportFilename,
  exportGraphAsPng,
  exportGraphAsSvg,
} from '@/apps/argument-map/utils/graphExport';
import { toast } from '@/shared/stores/toastStore';

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
  /** Заголовок темы - используется в имени экспортированного файла */
  topicTitle?: string | null;
  /** Контейнер графа - источник DOM-snapshot для html-to-image. Если null,
   * экспорт fallback на `.react-flow__viewport` через querySelector */
  graphContainerRef?: React.RefObject<HTMLElement | null>;
}

/**
 * Три статичных Panel поверх React Flow:
 * - top-left: вертикальная toolbar (Add Node/Edge, toggle labels, Delete, Export)
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
  topicTitle,
  graphContainerRef,
}: Props) {
  const t = useT();
  const [exportMenuOpen, setExportMenuOpen] = useState(false);
  const [exporting, setExporting] = useState<false | 'png' | 'svg'>(false);
  const exportMenuRef = useRef<HTMLDivElement>(null);

  // Dismiss popover при клике вне меню и при Escape
  useEffect(() => {
    if (!exportMenuOpen) return;
    function onPointerDown(e: PointerEvent) {
      if (exportMenuRef.current && !exportMenuRef.current.contains(e.target as Node)) {
        setExportMenuOpen(false);
      }
    }
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [exportMenuOpen]);

  useHotkey('escape', () => setExportMenuOpen(false), { enabled: exportMenuOpen });

  async function handleExport(format: 'png' | 'svg') {
    setExportMenuOpen(false);
    if (!rfInstance) return;

    // fitView перед export гарантирует что все узлы попадут в кадр,
    // иначе экспортируется только текущий viewport. padding 0.1 даёт
    // лёгкий бордюр чтобы box-shadow узлов не обрезался по краям
    rfInstance.fitView({ padding: 0.1 });
    // 100ms даёт React Flow завершить transition после fitView перед
    // снимком DOM. Без задержки html-to-image захватывает intermediate state
    await new Promise((r) => setTimeout(r, 150));

    // Берём элемент graph viewport - либо из ref, либо fallback querySelector.
    // `.react-flow__viewport` содержит nodes + edges (без overlay panels)
    const container =
      graphContainerRef?.current ??
      (document.querySelector('.react-flow') as HTMLElement | null);
    if (!container) {
      toast.error(t('graph.export.error'));
      return;
    }

    const filename = buildExportFilename(topicTitle, format);
    setExporting(format);
    try {
      if (format === 'png') {
        await exportGraphAsPng(container, filename);
      } else {
        await exportGraphAsSvg(container, filename);
      }
      toast.success(t('graph.export.success').replace('{filename}', filename));
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      toast.error(`${t('graph.export.error')}: ${msg}`);
    } finally {
      setExporting(false);
    }
  }

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
        {/* Export dropdown - кнопка + всплывающее меню справа от toolbar.
           Position relative parent + absolute popover чтобы меню оставалось
           привязанным к кнопке при scroll/resize графа */}
        <div ref={exportMenuRef} className="relative">
          <IconButton
            icon={Download}
            label={t('graph.export.hint')}
            size="md"
            active={exportMenuOpen}
            disabled={exporting !== false || !rfInstance}
            onClick={() => setExportMenuOpen((v) => !v)}
          />
          {exportMenuOpen && (
            <div
              role="menu"
              aria-label={t('graph.export.button')}
              className="absolute start-full top-0 z-50 ms-2 min-w-48 rounded-md border border-border bg-elevated py-1 shadow-sh3"
            >
              <button
                type="button"
                role="menuitem"
                onClick={() => void handleExport('png')}
                className="flex w-full items-center gap-2 px-3 py-2 text-start text-sm text-ink-700 hover:bg-ink-100"
              >
                <FileImage size={14} className="shrink-0 text-ink-500" aria-hidden />
                {t('graph.export.png')}
              </button>
              <button
                type="button"
                role="menuitem"
                onClick={() => void handleExport('svg')}
                className="flex w-full items-center gap-2 px-3 py-2 text-start text-sm text-ink-700 hover:bg-ink-100"
              >
                <FileCode size={14} className="shrink-0 text-ink-500" aria-hidden />
                {t('graph.export.svg')}
              </button>
            </div>
          )}
        </div>
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
