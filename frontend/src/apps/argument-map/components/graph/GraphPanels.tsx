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
  Network,
  Loader2,
  Check,
} from 'lucide-react';
import IconButton from '@/shared/components/ui/IconButton';
import Button from '@/shared/components/ui/Button';
import Modal from '@/shared/components/ui/Modal';
import Kbd from '@/shared/components/ui/Kbd';
import { useHotkey } from '@/shared/hooks/useHotkey';
import { useT } from '@/shared/i18n';
import {
  buildExportFilename,
  exportGraphAsPng,
  exportGraphAsSvg,
} from '@/apps/argument-map/utils/graphExport';
import { toast } from '@/shared/stores/toastStore';
import {
  useLayoutPresetStore,
  type LayoutPreset,
} from '@/shared/stores/layoutPresetStore';
import { useEdgeStyleStore } from '@/shared/stores/edgeStyleStore';

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
  /** Read-only: скрыть mutating кнопки (Add Node / Add Edge / Delete).
   * Export, zoom, label toggle - всегда доступны. Default true */
  canWrite?: boolean;
  /** Layout сейчас пересчитывается (loading indicator на кнопке) */
  layoutPending?: boolean;
  /** Триггер re-layout с выбранным preset'ом - вызывается при выборе
   * формы в меню. Owner логики - GraphCanvas (там state nodes/edges
   * и rfInstance). Один callback для всех presets - preset проходит
   * как аргумент, mapping в ELK config живёт в elkLayout.ts */
  onApplyPreset?: (preset: LayoutPreset) => void | Promise<void>;
  /** Триггер сброса ручных позиций. POST /topics/{id}/reset-layout
   * → SET posX=NULL, posY=NULL для всех узлов темы, после чего
   * onApplyPreset(current preset) применяет свежий layout */
  onResetLayout?: () => void | Promise<void>;
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
  canWrite = true,
  layoutPending = false,
  onApplyPreset,
  onResetLayout,
}: Props) {
  const t = useT();
  const [exportMenuOpen, setExportMenuOpen] = useState(false);
  const [exporting, setExporting] = useState<false | 'png' | 'svg'>(false);
  const exportMenuRef = useRef<HTMLDivElement>(null);
  const [layoutMenuOpen, setLayoutMenuOpen] = useState(false);
  const layoutMenuRef = useRef<HTMLDivElement>(null);
  const preset = useLayoutPresetStore((s) => s.preset);
  const setPreset = useLayoutPresetStore((s) => s.setPreset);
  const edgeStyle = useEdgeStyleStore((s) => s.edgeStyle);
  const setEdgeStyle = useEdgeStyleStore((s) => s.setEdgeStyle);
  const [resetConfirmOpen, setResetConfirmOpen] = useState(false);

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

  // Layout-menu - тот же паттерн что export: click-outside + Esc dismiss.
  // Отдельный useEffect (а не общий с export) - state у каждого свой,
  // listener короче и pointerdown срабатывает только на нужное меню
  useEffect(() => {
    if (!layoutMenuOpen) return;
    function onPointerDown(e: PointerEvent) {
      if (layoutMenuRef.current && !layoutMenuRef.current.contains(e.target as Node)) {
        setLayoutMenuOpen(false);
      }
    }
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [layoutMenuOpen]);

  useHotkey('escape', () => setLayoutMenuOpen(false), { enabled: layoutMenuOpen });

  function pickPreset(next: LayoutPreset) {
    setLayoutMenuOpen(false);
    if (next === preset) return;
    setPreset(next);
    if (onApplyPreset) {
      void onApplyPreset(next);
    }
  }

  /** Re-apply current preset (используется кнопкой «Применить заново»
   * и после reset-layout flow для свежей раскладки без смены preset'а) */
  function reapplyCurrentPreset() {
    setLayoutMenuOpen(false);
    if (onApplyPreset) {
      void onApplyPreset(preset);
    }
  }

  async function confirmResetLayout() {
    setResetConfirmOpen(false);
    setLayoutMenuOpen(false);
    if (!onResetLayout) return;
    try {
      await onResetLayout();
    } finally {
      // После очистки posX/posY на бэке применяем current preset
      // чтобы юзер сразу увидел разложенный заново граф
      if (onApplyPreset) await onApplyPreset(preset);
    }
  }

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
        {canWrite && (
          <>
            <IconButton icon={Plus} label={t('graph.add_node')} size="md" onClick={onAddNode} />
            <IconButton
              icon={Link2}
              label={canAddEdge ? t('graph.create_edge') : t('graph.need_two_nodes')}
              size="md"
              disabled={!canAddEdge}
              onClick={onAddEdge}
            />
            <div className="my-1 h-px w-7 bg-ink-200" />
          </>
        )}
        <IconButton
          icon={showEdgeLabels ? Eye : EyeOff}
          label={showEdgeLabels ? t('graph.hide_edge_labels') : t('graph.show_edge_labels')}
          size="md"
          active={showEdgeLabels}
          onClick={onToggleLabels}
        />
        {/* Layout-preset dropdown - 3 формы графа (Tree TB / Tree LR /
           Radial). Mapping в ELK config - в elkLayout.ts (type-aware
           constraints, ORTHOGONAL routing, BRANDES_KOEPF placement).
           Reset secondary action очищает все ручные posX/posY и
           применяет current preset заново. */}
        <div ref={layoutMenuRef} className="relative">
          <IconButton
            icon={layoutPending ? Loader2 : Network}
            label={t('layout.menu_label')}
            size="md"
            active={layoutMenuOpen}
            onClick={() => setLayoutMenuOpen((v) => !v)}
            className={layoutPending ? '[&_svg]:animate-spin' : ''}
          />
          {layoutMenuOpen && (
            <div
              role="menu"
              aria-label={t('layout.menu_label')}
              className="absolute start-full top-0 z-50 ms-2 min-w-72 rounded-md border border-border bg-elevated py-1 shadow-sh3"
            >
              <div className="px-3 pt-2 pb-1 text-xs font-semibold uppercase tracking-wide text-ink-500">
                {t('layout.preset.label')}
              </div>
              {(['tree-tb', 'tree-lr', 'radial'] as const).map((p) => (
                <button
                  key={p}
                  type="button"
                  role="menuitemradio"
                  aria-checked={preset === p}
                  onClick={() => pickPreset(p)}
                  className="flex w-full items-start justify-between gap-2 px-3 py-2 text-start text-sm text-ink-700 hover:bg-ink-100"
                >
                  <span className="flex-1">
                    <span className="block font-medium">{t(`layout.preset.${p}` as const)}</span>
                    <span className="block text-xs text-ink-500 mt-0.5">
                      {t(`layout.preset.${p}_description` as const)}
                    </span>
                  </span>
                  {preset === p && (
                    <Check size={14} className="mt-1 shrink-0 text-accent-600" aria-hidden />
                  )}
                </button>
              ))}
              <div className="my-1 border-t border-border" />
              {/* Edge style toggle - применим только для tree-presets.
                 Radial всегда bezier (curves естественнее в polar
                 топологии), для него секция dimmed. */}
              <div className="px-3 pt-2 pb-1 text-xs font-semibold uppercase tracking-wide text-ink-500">
                {t('layout.edge_style.label')}
              </div>
              {(['orthogonal', 'smooth'] as const).map((style) => {
                const disabled = preset === 'radial';
                return (
                  <button
                    key={style}
                    type="button"
                    role="menuitemradio"
                    aria-checked={edgeStyle === style}
                    disabled={disabled}
                    onClick={() => setEdgeStyle(style)}
                    className={`flex w-full items-start justify-between gap-2 px-3 py-2 text-start text-sm hover:bg-ink-100 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:bg-transparent ${edgeStyle === style ? 'text-ink-900' : 'text-ink-700'}`}
                  >
                    <span className="flex-1">
                      <span className="block font-medium">
                        {t(`layout.edge_style.${style}` as const)}
                      </span>
                      <span className="block text-xs text-ink-500 mt-0.5">
                        {t(`layout.edge_style.${style}_description` as const)}
                      </span>
                    </span>
                    {edgeStyle === style && !disabled && (
                      <Check size={14} className="mt-1 shrink-0 text-accent-600" aria-hidden />
                    )}
                  </button>
                );
              })}
              <div className="my-1 border-t border-border" />
              {canWrite && (
                <button
                  type="button"
                  onClick={reapplyCurrentPreset}
                  className="flex w-full items-center gap-2 px-3 py-2 text-start text-sm text-ink-700 hover:bg-ink-100"
                >
                  {t('layout.reapply')}
                </button>
              )}
              {canWrite && onResetLayout && (
                <button
                  type="button"
                  onClick={() => setResetConfirmOpen(true)}
                  className="flex w-full items-center gap-2 px-3 py-2 text-start text-sm text-err-700 hover:bg-err-50"
                >
                  {t('layout.reset_manual')}
                </button>
              )}
              <div className="border-t border-border px-3 py-2 text-xs text-ink-500">
                {t('layout.preset.hint')}
              </div>
            </div>
          )}
        </div>
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
        {canWrite && (
          <>
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
          </>
        )}
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
      {resetConfirmOpen && (
        <Modal
          open
          onClose={() => setResetConfirmOpen(false)}
          title={t('layout.reset_confirm_title')}
        >
          <p className="text-sm text-ink-700">{t('layout.reset_confirm_body')}</p>
          <div className="mt-4 flex justify-end gap-2">
            <Button variant="ghost" size="sm" onClick={() => setResetConfirmOpen(false)}>
              {t('layout.reset_cancel')}
            </Button>
            <Button variant="danger" size="sm" onClick={confirmResetLayout}>
              {t('layout.reset_confirm_action')}
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}

export default GraphPanels;
