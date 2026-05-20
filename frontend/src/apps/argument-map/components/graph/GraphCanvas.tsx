import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ReactFlow,
  Background,
  ConnectionMode,
  useNodesState,
  useEdgesState,
  reconnectEdge,
  type ReactFlowProps,
  type Node,
  type Edge,
  type Connection,
  type ReactFlowInstance,
} from '@xyflow/react';
import { Plus, Trash2, Pencil, ArrowUp, ArrowDown } from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import ContextMenu, { type ContextMenuItem } from '@/shared/components/ui/ContextMenu';
import NodeCard, { type NodeCardNode, type NodeCardData } from '@/apps/argument-map/components/graph/NodeCard';
import CustomEdge, { type CustomEdgeEdge } from '@/apps/argument-map/components/graph/CustomEdge';
import AddNodeModal, { type AutoEdgeSpec } from '@/apps/argument-map/components/graph/AddNodeModal';
import AddEdgeModal from '@/apps/argument-map/components/graph/AddEdgeModal';
import NodeDetailsPanel from '@/apps/argument-map/components/graph/NodeDetailsPanel';
import EdgeDetailsPanel from '@/apps/argument-map/components/graph/EdgeDetailsPanel';
import CompactMiniMap from '@/apps/argument-map/components/graph/CompactMiniMap';
import GraphPanels from '@/apps/argument-map/components/graph/GraphPanels';
import FloatingActionBar from '@/apps/argument-map/components/graph/FloatingActionBar';
import { useGraphEscape } from '@/apps/argument-map/hooks/useGraphEscape';
import { useGraphZOrder } from '@/apps/argument-map/hooks/useGraphZOrder';
import { useElkAutoLayout } from '@/apps/argument-map/hooks/useElkAutoLayout';
import { useNodeDelete } from '@/apps/argument-map/hooks/useNodeDelete';
import { useBulkNodeActions } from '@/apps/argument-map/hooks/useBulkNodeActions';
import { useHotkey } from '@/shared/hooks/useHotkey';
import {
  getAllowedEdgeTypes,
  getRelatedNodeOptions,
  isEdgeAllowed,
  NODE_TYPE_META,
} from '@/apps/argument-map/utils/edgeRules';
import { buildFlow, findFreePosition, sameIds } from '@/apps/argument-map/utils/graphPlacement';
import { apiPatchRaw, ApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import { useThemeStore } from '@/shared/stores/themeStore';
import type { components } from '@/shared/api/types';

type GraphResponse = components['schemas']['GraphResponse'];
type EdgeDto = components['schemas']['EdgeResponse'];

// nodeTypes/edgeTypes - стабильные ссылки между рендерами, иначе RF
// ругается и пере-инициализируется (см coding-standards).
const nodeTypes: ReactFlowProps['nodeTypes'] = { argumentNode: NodeCard };
const edgeTypes: ReactFlowProps['edgeTypes'] = { argumentEdge: CustomEdge };

interface Props {
  graph: GraphResponse;
  topicId: string;
  onRefetch: () => void;
  /**
   * Может ли текущий пользователь писать в тему. Если false - кнопки
   * Add Node / Add Edge / Delete скрываются, context-menu mutating
   * actions недоступны. Подсветка read-only - в TopicGraphPage header.
   * Default: true для backwards compat (тесты GraphCanvas без props)
   */
  canWrite?: boolean;
}

const SHOW_LABELS_LS_KEY = 'argmap.showEdgeLabels';

function readShowLabels(): boolean {
  if (typeof window === 'undefined') return true;
  const raw = window.localStorage.getItem(SHOW_LABELS_LS_KEY);
  return raw === null ? true : raw === 'true';
}

/**
 * Главный canvas с графом аргументации - React Flow renderer + все
 * interaction handlers (drag, connect, reconnect, context menus,
 * delete) + панели и модалки.
 *
 * Не зависит от страничного шаблона: page (`TopicGraphPage`) только
 * грузит данные и передаёт сюда `graph` + `topicId` + `onRefetch`.
 */
function GraphCanvas({ graph, topicId, onRefetch, canWrite = true }: Props) {
  const t = useT();
  // React Flow `colorMode` prop переключает CSS-vars пакета (controls,
  // background dots, attribution) под light/dark. Без него controls
  // остаются светлыми на тёмной странице. Berührется effectiveTheme
  // через themeStore - не reactive к смене темы без re-render канваса
  const effectiveTheme = useThemeStore((s) => s.effectiveTheme);
  const [showEdgeLabels, setShowEdgeLabels] = useState<boolean>(readShowLabels);

  useEffect(() => {
    window.localStorage.setItem(SHOW_LABELS_LS_KEY, String(showEdgeLabels));
  }, [showEdgeLabels]);

  // ref на последний RF-state, чтобы при rebuild графа (новый refetch)
  // переиспользовать уже размещённые позиции узлов. Без этого fresh-узлы
  // (без posX/posY на бэке) при каждом mixed-layout переезжают в столбец
  // справа - даже если они уже стоят на читаемых dagre-местах
  const lastNodesRef = useRef<NodeCardNode[]>([]);

  // Чтение ref'а в useMemo - сознательно: нужен последний snapshot
  // позиций для passive layout-hint, не для реактивности
  const initial = useMemo(
    () => buildFlow(graph, showEdgeLabels, lastNodesRef.current),
    [graph, showEdgeLabels],
  );

  const [nodes, setNodes, onNodesChange] = useNodesState<NodeCardNode>(initial.nodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<CustomEdgeEdge>(initial.edges);

  useEffect(() => {
    lastNodesRef.current = nodes;
  }, [nodes]);

  // edges ref - чтобы triggerElkRelayout (useCallback с минимальным deps)
  // читал свежие edges без пере-создания на каждый edge-change
  const edgesRef = useRef<CustomEdgeEdge[]>(edges);
  useEffect(() => {
    edgesRef.current = edges;
  }, [edges]);

  // rfInstance ref - rfInstance state declaration ниже (после initial state),
  // циклические зависимости избегаются через ref. fitView читает свежее
  // значение в момент callback выполнения
  const rfInstanceRef = useRef<ReactFlowInstance<NodeCardNode, CustomEdgeEdge> | null>(null);

  // ELK re-layout - one-shot trigger при переключении алгоритма на elk.
  // НЕ запускается на каждый refetch (т.к. posX/posY уже сохранены и
  // dagre/layoutGraph их уважает). Вызывается из GraphPanels при click
  // на ELK в layout-menu. После применения - PATCH'ит новые координаты
  // на бэк, дальше работает как обычные сохранённые позиции
  const { triggerElkRelayout, layoutPending } = useElkAutoLayout({
    lastNodesRef,
    edgesRef,
    rfInstanceRef,
    setNodes,
  });

  // Узлы из бэка без posX/posY - dagre проставляет им позиции на фронте,
  // но эти позиции живут только в RF-state. Чтобы layout был стабильным
  // между refetches - сразу PATCH'им все узлы без координат
  useEffect(() => {
    const freshFromBackend = (graph.nodes ?? []).filter(
      (n) => n.id && (n.posX == null || n.posY == null),
    );
    if (freshFromBackend.length === 0) return;
    for (const dto of freshFromBackend) {
      const layouted = initial.nodes.find((n) => n.id === dto.id);
      if (!layouted) continue;
      apiPatchRaw(`/api/v1/nodes/${dto.id}`, {
        posX: layouted.position.x,
        posY: layouted.position.y,
      }).catch(() => {
        // не блокирующая ошибка - на следующем рефетче снова попадёт сюда
      });
    }
    // намеренно не зависим от initial.nodes - они меняются от showEdgeLabels
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [graph]);
  const [addNodeOpen, setAddNodeOpen] = useState(false);
  const [addEdgeOpen, setAddEdgeOpen] = useState(false);
  const [nodeDraft, setNodeDraft] = useState<{
    posX?: number;
    posY?: number;
    nodeType?: NodeCardNode['data']['nodeType'];
    autoEdge?: AutoEdgeSpec;
  } | null>(null);
  const [rfInstance, setRfInstance] = useState<ReactFlowInstance<NodeCardNode, CustomEdgeEdge> | null>(null);
  const [edgeDraft, setEdgeDraft] = useState<{
    from?: string;
    to?: string;
    sourceHandle?: string;
    targetHandle?: string;
  } | null>(null);
  const [selectedNodeIds, setSelectedNodeIds] = useState<string[]>([]);
  const [selectedEdgeIds, setSelectedEdgeIds] = useState<string[]>([]);

  const [contextMenu, setContextMenu] = useState<{
    x: number;
    y: number;
    items: ContextMenuItem[];
    header?: string;
  } | null>(null);

  const closeContextMenu = useCallback(() => setContextMenu(null), []);

  // detailNodeId/detailEdgeId - открыта ли панель деталей. Не зависит от
  // selection: drag триггерит selection без открытия панели. Открывается
  // через double-click или "Редактировать" в контекстном меню
  const [detailNodeId, setDetailNodeId] = useState<string | null>(null);
  const [detailEdgeId, setDetailEdgeId] = useState<string | null>(null);

  // editTargetNodeId/editTargetEdgeId - флаг "сразу в edit-режим" для
  // соответствующей панели. Срабатывает при "Редактировать" в context menu
  const [editTargetNodeId, setEditTargetNodeId] = useState<string | null>(null);
  const [editTargetEdgeId, setEditTargetEdgeId] = useState<string | null>(null);

  // Z-order операции вынесены в useGraphZOrder hook - persistent node z-order
  // через POST /api/v1/nodes/{id}/z-order/..., локальный edge z-order через
  // edgeZRef в hook'е. Optimistic update + refetch
  const {
    bringNodeToFront,
    sendNodeToBack,
    bringEdgeToFront,
    sendEdgeToBack,
  } = useGraphZOrder({
    nodesRef: lastNodesRef,
    setNodes,
    setEdges,
    onRefetch,
  });

  const rawNodeDtos = useMemo(() => graph.nodes ?? [], [graph.nodes]);

  // корневой узел темы - его удаление запрещено (бэк бросает 409
  // NodeIsRootException, см. #1). Используем для (а) скрытия пункта
  // "Удалить" в context menu, (б) фильтрации в bulk-delete из toolbar
  const rootNodeId = graph.topic?.rootNodeId ?? null;

  const { runDelete, deleteOneNode, deleteOneEdge, deleting } = useNodeDelete({
    rootNodeId,
    rawNodeDtos,
    setSelectedNodeIds,
    setSelectedEdgeIds,
    onRefetch,
  });

  // drag из handle одного узла на handle другого - проверяем матрицу
  // ADR-010 ДО открытия модалки. Запрещённую пару показываем тостом
  const handleConnect = useCallback(
    (connection: Connection) => {
      if (!connection.source || !connection.target) return;
      if (connection.source === connection.target) return;

      const fromNode = rawNodeDtos.find((n) => n.id === connection.source);
      const toNode = rawNodeDtos.find((n) => n.id === connection.target);
      if (!fromNode?.nodeType || !toNode?.nodeType) return;

      const allowed = getAllowedEdgeTypes(fromNode.nodeType, toNode.nodeType);
      if (allowed.length === 0) {
        toast.warning(
          `${t(NODE_TYPE_META[fromNode.nodeType].labelKey)} → ${t(NODE_TYPE_META[toNode.nodeType].labelKey)}: ${t('edge.error.disallowed_pair')}`,
        );
        return;
      }

      setEdgeDraft({
        from: connection.source,
        to: connection.target,
        sourceHandle: connection.sourceHandle ?? undefined,
        targetHandle: connection.targetHandle ?? undefined,
      });
      setAddEdgeOpen(true);
    },
    [rawNodeDtos, t],
  );

  function closeAddEdge() {
    setAddEdgeOpen(false);
    setEdgeDraft(null);
  }

  // кнопка "+ Связь" в toolbar: если выделен один узел - предзаполнить "Откуда"
  function openAddEdge() {
    if (selectedNodeIds.length === 1) {
      setEdgeDraft({ from: selectedNodeIds[0] });
    }
    setAddEdgeOpen(true);
  }

  // reconnect: перетащить конец существующего ребра на другой handle/узел.
  // Тип ребра сохраняется. Optimistic local update + PATCH + refetch (ADR-014)
  const handleReconnect = useCallback(
    (oldEdge: Edge, newConnection: Connection) => {
      if (!newConnection.source || !newConnection.target) return;
      if (newConnection.source === newConnection.target) {
        toast.warning(t('edge.error.self_loop'));
        return;
      }

      const fromNode = rawNodeDtos.find((n) => n.id === newConnection.source);
      const toNode = rawNodeDtos.find((n) => n.id === newConnection.target);
      if (!fromNode?.nodeType || !toNode?.nodeType) return;

      const edgeType = (oldEdge.data as { edgeType?: EdgeDto['edgeType'] } | undefined)?.edgeType;
      if (!edgeType) return;

      if (!isEdgeAllowed(fromNode.nodeType, edgeType, toNode.nodeType)) {
        toast.warning(
          `${t(NODE_TYPE_META[fromNode.nodeType].labelKey)} → ${t(NODE_TYPE_META[toNode.nodeType].labelKey)}: ${t('edge.error.disallowed_pair')}`,
        );
        return;
      }

      setEdges((eds) => reconnectEdge(oldEdge as CustomEdgeEdge, newConnection, eds));

      apiPatchRaw(`/api/v1/edges/${oldEdge.id}`, {
        fromNodeId: newConnection.source,
        toNodeId: newConnection.target,
        sourceHandle: newConnection.sourceHandle ?? undefined,
        targetHandle: newConnection.targetHandle ?? undefined,
      })
        .then(() => onRefetch())
        .catch((e: unknown) => {
          const msg =
            e instanceof ApiError
              ? `${e.problem.title}${e.problem.detail ? ': ' + e.problem.detail : ''}`
              : (e as Error).message;
          toast.error(`${t('graph.toast.update_failed')}: ${msg}`);
          onRefetch();
        });
    },
    [rawNodeDtos, onRefetch, setEdges, t],
  );

  // drag-end - PATCH с координатами, оптимистично. Ошибка - toast
  const handleNodeDragStop = useCallback(
    (_event: React.MouseEvent, node: Node) => {
      apiPatchRaw(`/api/v1/nodes/${node.id}`, {
        posX: node.position.x,
        posY: node.position.y,
      }).catch((e: unknown) => {
        const msg = e instanceof ApiError ? e.problem.title : (e as Error).message;
        toast.error(`${t('graph.toast.update_failed')}: ${msg}`);
      });
    },
    [t],
  );

  const { runBulkStatusChange, bulkBusy } = useBulkNodeActions({ onRefetch });

  // правый клик на pane - "Создать узел здесь" с координатами курсора.
  // Для read-only режима меню не показываем (single mutating action - create)
  const handlePaneContextMenu = useCallback(
    (event: React.MouseEvent | MouseEvent) => {
      event.preventDefault();
      if (!canWrite) return;
      const flowPos = rfInstance?.screenToFlowPosition({
        x: event.clientX,
        y: event.clientY,
      });
      setContextMenu({
        x: event.clientX,
        y: event.clientY,
        header: t('graph.ctx.canvas'),
        items: [
          {
            id: 'create-node',
            label: t('graph.ctx.create_here'),
            icon: Plus,
            onClick: () => {
              if (flowPos) setNodeDraft({ posX: flowPos.x, posY: flowPos.y });
              setAddNodeOpen(true);
            },
          },
        ],
      });
    },
    [rfInstance, t, canWrite],
  );

  function closeAddNode() {
    setAddNodeOpen(false);
    setNodeDraft(null);
  }

  // double-click открывает панель деталей. Single click - только выделяет
  const handleNodeDoubleClick = useCallback(
    (_event: React.MouseEvent, node: Node) => {
      setDetailNodeId(node.id);
      setDetailEdgeId(null);
      setEditTargetNodeId(null);
      setEditTargetEdgeId(null);
    },
    [],
  );

  const handleEdgeDoubleClick = useCallback(
    (_event: React.MouseEvent, edge: Edge) => {
      setDetailEdgeId(edge.id);
      setDetailNodeId(null);
      setEditTargetNodeId(null);
      setEditTargetEdgeId(null);
    },
    [],
  );

  // правый клик на узле - "Добавить связанный X" + "Редактировать", z-order, "Удалить"
  const handleNodeContextMenu = useCallback(
    (event: React.MouseEvent, node: Node) => {
      event.preventDefault();
      const data = node.data as NodeCardData | undefined;
      const anchorType = data?.nodeType;
      const relatedOptions =
        canWrite && anchorType ? getRelatedNodeOptions(anchorType) : [];

      const relatedItems: ContextMenuItem[] = [...relatedOptions].map((opt) => ({
        id: `add-${opt.newNodeType}-${opt.edgeType}-${opt.direction}`,
        label: t(opt.labelKey),
        icon: Plus,
        onClick: () => {
          // lastNodesRef (а не nodes из closure) - useCallback не пере-создавался
          // после предыдущего create, closure nodes - устаревший snapshot
          const currentNodes = lastNodesRef.current;
          const anchor = currentNodes.find((n) => n.id === node.id) ?? node;
          const pos = findFreePosition(anchor.position, opt.direction, currentNodes);
          setNodeDraft({
            posX: pos.x,
            posY: pos.y,
            nodeType: opt.newNodeType,
            autoEdge: {
              anchorNodeId: node.id,
              edgeType: opt.edgeType,
              direction: opt.direction,
            },
          });
          setAddNodeOpen(true);
        },
      }));

      const items: ContextMenuItem[] = [...relatedItems];
      if (relatedItems.length > 0) {
        items.push({ id: 'sep-related', label: '', separator: true });
      }
      items.push({
        id: 'edit-node',
        label: t('common.edit'),
        icon: Pencil,
        onClick: () => {
          setDetailNodeId(node.id);
          setDetailEdgeId(null);
          setEditTargetNodeId(node.id);
          setEditTargetEdgeId(null);
        },
      });
      // bring-to-front / send-to-back теперь персистится на бэк (миграция 40)
      // и требует write permission - скрываем для read-only
      if (canWrite) {
        items.push(
          {
            id: 'bring-front',
            label: t('graph.ctx.bring_front'),
            icon: ArrowUp,
            onClick: () => bringNodeToFront(node.id),
          },
          {
            id: 'send-back',
            label: t('graph.ctx.send_back'),
            icon: ArrowDown,
            onClick: () => sendNodeToBack(node.id),
          },
        );
      }
      // удаление недоступно для корневого узла темы (бэк бы вернул 409
      // NodeIsRootException, но UX лучше скрыть пункт + показать hint).
      // А также скрываем для read-only пользователей
      if (canWrite) {
        if (node.id !== rootNodeId) {
          items.push({
            id: 'delete-node',
            label: t('common.delete'),
            icon: Trash2,
            danger: true,
            onClick: () => void deleteOneNode(node.id),
          });
        } else {
          items.push({
            id: 'delete-node-root-disabled',
            label: `${t('common.delete')} · ${t('graph.root.delete_hint')}`,
            icon: Trash2,
            disabled: true,
            onClick: () => {},
          });
        }
      }

      setContextMenu({
        x: event.clientX,
        y: event.clientY,
        header: t('graph.ctx.section_node'),
        items,
      });
    },
    [canWrite, rootNodeId, t, bringNodeToFront, sendNodeToBack, deleteOneNode],
  );

  // правый клик на ребре - "Редактировать", z-order, "Удалить"
  const handleEdgeContextMenu = useCallback(
    (event: React.MouseEvent, edge: Edge) => {
      event.preventDefault();
      const items: ContextMenuItem[] = [
        {
          id: 'edit-edge',
          label: t('common.edit'),
          icon: Pencil,
          onClick: () => {
            setDetailEdgeId(edge.id);
            setDetailNodeId(null);
            setEditTargetEdgeId(edge.id);
            setEditTargetNodeId(null);
          },
        },
        {
          id: 'bring-front',
          label: t('graph.ctx.bring_front'),
          icon: ArrowUp,
          onClick: () => bringEdgeToFront(edge.id),
        },
        {
          id: 'send-back',
          label: t('graph.ctx.send_back'),
          icon: ArrowDown,
          onClick: () => sendEdgeToBack(edge.id),
        },
      ];
      if (canWrite) {
        items.push({
          id: 'delete-edge',
          label: t('common.delete'),
          icon: Trash2,
          danger: true,
          onClick: () => void deleteOneEdge(edge.id),
        });
      }
      setContextMenu({
        x: event.clientX,
        y: event.clientY,
        header: t('graph.ctx.section_edge'),
        items,
      });
    },
    [canWrite, t, bringEdgeToFront, sendEdgeToBack, deleteOneEdge],
  );

  // RF onSelectionChange срабатывает при каждом setNodes даже если selection
  // не изменилась. Inline-callback создавал новые [] - infinite loop. Решение:
  // stable callback + функциональный update со сравнением содержимого
  const handleSelectionChange = useCallback(
    ({ nodes: ns, edges: es }: { nodes: Node[]; edges: { id: string }[] }) => {
      const nextNodeIds = ns.map((n) => n.id);
      const nextEdgeIds = es.map((e) => e.id);
      setSelectedNodeIds((prev) => (sameIds(prev, nextNodeIds) ? prev : nextNodeIds));
      setSelectedEdgeIds((prev) => (sameIds(prev, nextEdgeIds) ? prev : nextEdgeIds));
    },
    [],
  );
  const canAddEdge = rawNodeDtos.length >= 2;
  const selectedCount = selectedNodeIds.length + selectedEdgeIds.length;

  // detailNode - резолвим dto из rawNodeDtos по detailNodeId
  const detailNode = useMemo(() => {
    if (!detailNodeId) return null;
    return rawNodeDtos.find((n) => n.id === detailNodeId) ?? null;
  }, [detailNodeId, rawNodeDtos]);

  const rawEdgeDtos = useMemo(() => graph.edges ?? [], [graph.edges]);

  // detailEdge - dto + резолвленные from/to для превью
  const detailEdge = useMemo(() => {
    if (!detailEdgeId) return null;
    const edge = rawEdgeDtos.find((e) => e.id === detailEdgeId);
    if (!edge) return null;
    const fromNode = rawNodeDtos.find((n) => n.id === edge.fromNodeId);
    const toNode = rawNodeDtos.find((n) => n.id === edge.toNodeId);
    if (!fromNode || !toNode) return null;
    return { edge, fromNode, toNode };
  }, [detailEdgeId, rawEdgeDtos, rawNodeDtos]);

  const closeDetail = useCallback(() => {
    setDetailNodeId(null);
    setDetailEdgeId(null);
    setEditTargetNodeId(null);
    setEditTargetEdgeId(null);
  }, []);

  const clearSelection = useCallback(() => {
    setSelectedNodeIds([]);
    setSelectedEdgeIds([]);
    setNodes((nds) => nds.map((n) => ({ ...n, selected: false })));
    setEdges((eds) => eds.map((edge) => ({ ...edge, selected: false })));
  }, [setNodes, setEdges]);

  useGraphEscape({
    hasSelection: selectedNodeIds.length > 0 || selectedEdgeIds.length > 0,
    hasDetail: detailNodeId !== null || detailEdgeId !== null,
    hasContextMenu: contextMenu !== null,
    onClearSelection: clearSelection,
    onCloseDetail: closeDetail,
  });

  // Del/Backspace - удалить выделенные узлы/рёбра. useHotkey по default
  // не срабатывает в input/textarea (enableOnFormTags=false) и использует
  // event.code для layout-independence. Игнорируем когда открыт modal или
  // контекстное меню - они обработают Esc своими хотками
  useHotkey(
    'delete,backspace',
    () => {
      if (document.querySelector('dialog[open]')) return;
      if (contextMenu) return;
      if (selectedNodeIds.length === 0 && selectedEdgeIds.length === 0) return;
      void handleDelete();
    },
    {},
    [selectedNodeIds, selectedEdgeIds, contextMenu, rootNodeId],
  );

  // Public entry для Del/Backspace и toolbar-кнопки. Без `window.confirm`:
  // намерение выражено явным нажатием Del; reversibility через Undo toast
  // (см. runDelete). Унифицирует hotkey + context menu + toolbar bulk-delete
  async function handleDelete() {
    if (selectedCount === 0) return;
    await runDelete(selectedNodeIds, selectedEdgeIds);
  }

  // initial меняется при refetch - синхронизируем nodes/edges, сохраняя
  // выделение по id (чтобы панель деталей не закрывалась после PATCH)
  useEffect(() => {
    setNodes(
      initial.nodes.map((n) =>
        selectedNodeIds.includes(n.id) ? { ...n, selected: true } : n,
      ),
    );
    setEdges(
      initial.edges.map((e) =>
        selectedEdgeIds.includes(e.id) ? { ...e, selected: true } : e,
      ),
    );
    // selectedNodeIds/Ids - намеренно не в deps: иначе каждый клик пере-инициализировал бы граф
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initial.nodes, initial.edges, setNodes, setEdges]);

  const isEmpty = initial.nodes.length === 0;

  return (
    <>
      {isEmpty ? (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-4 p-8 text-center">
          <p className="text-ink-500">{t('graph.empty')}</p>
          {canWrite && (
            <Button onClick={() => setAddNodeOpen(true)}>{t('graph.add_first_node')}</Button>
          )}
        </div>
      ) : (
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={handleConnect}
          onReconnect={handleReconnect}
          onNodeDragStop={handleNodeDragStop}
          onInit={(inst) => {
            setRfInstance(inst);
            rfInstanceRef.current = inst;
          }}
          onPaneContextMenu={handlePaneContextMenu}
          onNodeContextMenu={handleNodeContextMenu}
          onEdgeContextMenu={handleEdgeContextMenu}
          onNodeDoubleClick={handleNodeDoubleClick}
          onEdgeDoubleClick={handleEdgeDoubleClick}
          connectionMode={ConnectionMode.Loose}
          // Shift на Linux/Win, Meta (⌘) на Mac - стандартная multi-select комба
          // для добавления узла к существующему выделению через клик
          multiSelectionKeyCode={['Shift', 'Meta']}
          onSelectionChange={handleSelectionChange}
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          fitView
          minZoom={0.2}
          maxZoom={1.5}
          elevateNodesOnSelect={false}
          elevateEdgesOnSelect={false}
          colorMode={effectiveTheme}
          proOptions={{ hideAttribution: true }}
        >
          <Background gap={24} size={1} />
          <CompactMiniMap detailOpen={!!detailNode || !!detailEdge} />
          <GraphPanels
            showEdgeLabels={showEdgeLabels}
            onToggleLabels={() => setShowEdgeLabels((v) => !v)}
            canAddEdge={canAddEdge}
            onAddNode={() => setAddNodeOpen(true)}
            onAddEdge={openAddEdge}
            selectedCount={selectedCount}
            deleting={deleting}
            onDelete={handleDelete}
            rfInstance={rfInstance as ReactFlowInstance<never, never> | null}
            topicTitle={graph.topic?.title}
            canWrite={canWrite}
            layoutPending={layoutPending}
            onApplyElkLayout={triggerElkRelayout}
          />
        </ReactFlow>
      )}

      <AddNodeModal
        key={`addNode-${nodeDraft?.posX ?? ''}-${nodeDraft?.posY ?? ''}-${nodeDraft?.nodeType ?? ''}-${nodeDraft?.autoEdge?.anchorNodeId ?? ''}-${nodeDraft?.autoEdge?.edgeType ?? ''}`}
        open={addNodeOpen}
        topicId={topicId}
        initialPosX={nodeDraft?.posX}
        initialPosY={nodeDraft?.posY}
        initialNodeType={nodeDraft?.nodeType}
        autoEdge={nodeDraft?.autoEdge}
        onClose={closeAddNode}
        onCreated={onRefetch}
      />

      <AddEdgeModal
        key={`addEdge-${edgeDraft?.from ?? ''}-${edgeDraft?.to ?? ''}-${edgeDraft?.sourceHandle ?? ''}-${edgeDraft?.targetHandle ?? ''}`}
        open={addEdgeOpen}
        nodes={rawNodeDtos}
        initialFromId={edgeDraft?.from}
        initialToId={edgeDraft?.to}
        initialSourceHandle={edgeDraft?.sourceHandle}
        initialTargetHandle={edgeDraft?.targetHandle}
        onClose={closeAddEdge}
        onCreated={onRefetch}
      />

      {detailNode && (
        <NodeDetailsPanel
          key={`${detailNode.id}-${detailNode.updatedAt ?? ''}-${editTargetNodeId === detailNode.id ? 'edit' : 'view'}`}
          node={detailNode}
          onClose={closeDetail}
          onUpdated={onRefetch}
          initialEditing={editTargetNodeId === detailNode.id}
        />
      )}

      {detailEdge && (
        <EdgeDetailsPanel
          key={`${detailEdge.edge.id}-${detailEdge.edge.edgeType}-${detailEdge.edge.rationale ?? ''}-${editTargetEdgeId === detailEdge.edge.id ? 'edit' : 'view'}`}
          edge={detailEdge.edge}
          fromNode={detailEdge.fromNode}
          toNode={detailEdge.toNode}
          onClose={closeDetail}
          onUpdated={onRefetch}
          initialEditing={editTargetEdgeId === detailEdge.edge.id}
        />
      )}

      {contextMenu && (
        <ContextMenu
          x={contextMenu.x}
          y={contextMenu.y}
          items={contextMenu.items}
          header={contextMenu.header}
          onClose={closeContextMenu}
        />
      )}

      {/* FloatingActionBar - bottom-center pill, появляется когда выделение
         >0. runBulkStatusChange фильтрует ids перед PATCH'ем (root тоже
         можно менять статусом - в отличие от delete). canWrite пропагируется
         внутрь и бар скрывается для read-only пользователей */}
      <FloatingActionBar
        nodeCount={selectedNodeIds.length}
        edgeCount={selectedEdgeIds.length}
        canWrite={canWrite}
        busy={deleting || bulkBusy}
        onDelete={() => void handleDelete()}
        onChangeStatus={(status) => void runBulkStatusChange(selectedNodeIds, status)}
        onClear={clearSelection}
      />
    </>
  );
}

export default GraphCanvas;
