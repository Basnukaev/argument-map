import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ReactFlow,
  Background,
  Panel,
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
import {
  Plus,
  Trash2,
  Eye,
  EyeOff,
  Pencil,
  ArrowUp,
  ArrowDown,
  Link2,
  ZoomIn,
  ZoomOut,
  Maximize,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import IconButton from '@/shared/components/ui/IconButton';
import Kbd from '@/shared/components/ui/Kbd';
import ContextMenu, { type ContextMenuItem } from '@/shared/components/ui/ContextMenu';
import { STATUS_TOKENS } from '@/shared/utils/designTokens';
import NodeCard, { type NodeCardNode, type NodeCardData } from '@/apps/argument-map/components/graph/NodeCard';
import CustomEdge, { type CustomEdgeEdge } from '@/apps/argument-map/components/graph/CustomEdge';
import AddNodeModal, { type AutoEdgeSpec } from '@/apps/argument-map/components/graph/AddNodeModal';
import AddEdgeModal from '@/apps/argument-map/components/graph/AddEdgeModal';
import NodeDetailsPanel from '@/apps/argument-map/components/graph/NodeDetailsPanel';
import EdgeDetailsPanel from '@/apps/argument-map/components/graph/EdgeDetailsPanel';
import CompactMiniMap from '@/apps/argument-map/components/graph/CompactMiniMap';
import {
  getAllowedEdgeTypes,
  getRelatedNodeOptions,
  isEdgeAllowed,
  NODE_TYPE_LABEL,
} from '@/apps/argument-map/utils/edgeRules';
import { buildFlow, findFreePosition, sameIds } from '@/apps/argument-map/utils/graphPlacement';
import { apiDeleteRaw, apiPatchRaw, ApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
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
function GraphCanvas({ graph, topicId, onRefetch }: Props) {
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
    // eslint-disable-next-line react-hooks/refs
    () => buildFlow(graph, showEdgeLabels, lastNodesRef.current),
    [graph, showEdgeLabels],
  );

  const [nodes, setNodes, onNodesChange] = useNodesState<NodeCardNode>(initial.nodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<CustomEdgeEdge>(initial.edges);

  useEffect(() => {
    lastNodesRef.current = nodes;
  }, [nodes]);

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
  const [deleting, setDeleting] = useState(false);

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

  // счётчики z-index для "на передний/задний план". Локально пока открыт
  // граф - при refetch сбрасываются на дефолт RF
  const zRef = useRef({ max: 10, min: 0 });

  function bringNodeToFront(id: string) {
    zRef.current.max += 1;
    const z = zRef.current.max;
    setNodes((nds) => nds.map((n) => (n.id === id ? { ...n, zIndex: z } : n)));
  }
  function sendNodeToBack(id: string) {
    zRef.current.min -= 1;
    const z = zRef.current.min;
    setNodes((nds) => nds.map((n) => (n.id === id ? { ...n, zIndex: z } : n)));
  }
  function bringEdgeToFront(id: string) {
    zRef.current.max += 1;
    const z = zRef.current.max;
    setEdges((eds) => eds.map((e) => (e.id === id ? { ...e, zIndex: z } : e)));
  }
  function sendEdgeToBack(id: string) {
    zRef.current.min -= 1;
    const z = zRef.current.min;
    setEdges((eds) => eds.map((e) => (e.id === id ? { ...e, zIndex: z } : e)));
  }

  const rawNodeDtos = useMemo(() => graph.nodes ?? [], [graph.nodes]);

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
          `${NODE_TYPE_LABEL[fromNode.nodeType]} → ${NODE_TYPE_LABEL[toNode.nodeType]}: эту пару нельзя соединить (см. ADR-010)`,
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
    [rawNodeDtos],
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
        toast.warning('Узел не может ссылаться на себя');
        return;
      }

      const fromNode = rawNodeDtos.find((n) => n.id === newConnection.source);
      const toNode = rawNodeDtos.find((n) => n.id === newConnection.target);
      if (!fromNode?.nodeType || !toNode?.nodeType) return;

      const edgeType = (oldEdge.data as { edgeType?: EdgeDto['edgeType'] } | undefined)?.edgeType;
      if (!edgeType) return;

      if (!isEdgeAllowed(fromNode.nodeType, edgeType, toNode.nodeType)) {
        toast.warning(
          `${NODE_TYPE_LABEL[fromNode.nodeType]} → ${NODE_TYPE_LABEL[toNode.nodeType]}: тип "${edgeType}" недопустим для этой пары (см. ADR-010)`,
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
          toast.error(`Не удалось пересоединить: ${msg}`);
          onRefetch();
        });
    },
    [rawNodeDtos, onRefetch, setEdges],
  );

  // drag-end - PATCH с координатами, оптимистично. Ошибка - toast
  const handleNodeDragStop = useCallback(
    (_event: React.MouseEvent, node: Node) => {
      apiPatchRaw(`/api/v1/nodes/${node.id}`, {
        posX: node.position.x,
        posY: node.position.y,
      }).catch((e: unknown) => {
        const msg = e instanceof ApiError ? e.problem.title : (e as Error).message;
        toast.error(`Не удалось сохранить позицию: ${msg}`);
      });
    },
    [],
  );

  // удаление одного узла/ребра по id из контекстного меню. Без window.confirm:
  // явный пункт меню уже выражает намерение
  async function deleteOneNode(nodeId: string) {
    try {
      await apiDeleteRaw(`/api/v1/nodes/${nodeId}`);
      onRefetch();
    } catch (e: unknown) {
      if (e instanceof ApiError && e.status === 404) {
        onRefetch();
        return;
      }
      const msg = e instanceof ApiError ? e.problem.title : (e as Error).message;
      toast.error(`Не удалось удалить узел: ${msg}`);
    }
  }

  async function deleteOneEdge(edgeId: string) {
    try {
      await apiDeleteRaw(`/api/v1/edges/${edgeId}`);
      onRefetch();
    } catch (e: unknown) {
      if (e instanceof ApiError && e.status === 404) {
        onRefetch();
        return;
      }
      const msg = e instanceof ApiError ? e.problem.title : (e as Error).message;
      toast.error(`Не удалось удалить связь: ${msg}`);
    }
  }

  // правый клик на pane - "Создать узел здесь" с координатами курсора
  const handlePaneContextMenu = useCallback(
    (event: React.MouseEvent | MouseEvent) => {
      event.preventDefault();
      const flowPos = rfInstance?.screenToFlowPosition({
        x: event.clientX,
        y: event.clientY,
      });
      setContextMenu({
        x: event.clientX,
        y: event.clientY,
        header: 'Холст',
        items: [
          {
            id: 'create-node',
            label: 'Создать узел здесь',
            icon: Plus,
            onClick: () => {
              if (flowPos) setNodeDraft({ posX: flowPos.x, posY: flowPos.y });
              setAddNodeOpen(true);
            },
          },
        ],
      });
    },
    [rfInstance],
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
      const relatedOptions = anchorType ? getRelatedNodeOptions(anchorType) : [];

      const relatedItems: ContextMenuItem[] = [...relatedOptions].map((opt) => ({
        id: `add-${opt.newNodeType}-${opt.edgeType}-${opt.direction}`,
        label: opt.label,
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
      items.push(
        {
          id: 'edit-node',
          label: 'Редактировать',
          icon: Pencil,
          onClick: () => {
            setDetailNodeId(node.id);
            setDetailEdgeId(null);
            setEditTargetNodeId(node.id);
            setEditTargetEdgeId(null);
          },
        },
        {
          id: 'bring-front',
          label: 'На передний план',
          icon: ArrowUp,
          onClick: () => bringNodeToFront(node.id),
        },
        {
          id: 'send-back',
          label: 'На задний план',
          icon: ArrowDown,
          onClick: () => sendNodeToBack(node.id),
        },
        {
          id: 'delete-node',
          label: 'Удалить',
          icon: Trash2,
          danger: true,
          onClick: () => void deleteOneNode(node.id),
        },
      );

      setContextMenu({
        x: event.clientX,
        y: event.clientY,
        header: 'Узел',
        items,
      });
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [setNodes],
  );

  // правый клик на ребре - "Редактировать", z-order, "Удалить"
  const handleEdgeContextMenu = useCallback(
    (event: React.MouseEvent, edge: Edge) => {
      event.preventDefault();
      setContextMenu({
        x: event.clientX,
        y: event.clientY,
        header: 'Связь',
        items: [
          {
            id: 'edit-edge',
            label: 'Редактировать',
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
            label: 'На передний план',
            icon: ArrowUp,
            onClick: () => bringEdgeToFront(edge.id),
          },
          {
            id: 'send-back',
            label: 'На задний план',
            icon: ArrowDown,
            onClick: () => sendEdgeToBack(edge.id),
          },
          {
            id: 'delete-edge',
            label: 'Удалить',
            icon: Trash2,
            danger: true,
            onClick: () => void deleteOneEdge(edge.id),
          },
        ],
      });
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [setEdges, setNodes],
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

  // Escape с очередью: фокус в sidebar -> закрыть; иначе selection -> снять;
  // иначе панель -> закрыть. Modal/ContextMenu закроются сами
  useEffect(() => {
    function onEsc(e: KeyboardEvent) {
      if (e.key !== 'Escape') return;

      if (document.querySelector('dialog[open]')) return;
      if (contextMenu) return;

      const active = document.activeElement;
      const inSidebar =
        active instanceof HTMLElement && active.closest('aside[role="complementary"]');
      const hasSelection = selectedNodeIds.length > 0 || selectedEdgeIds.length > 0;
      const hasDetail = detailNodeId !== null || detailEdgeId !== null;

      if (inSidebar && hasDetail) {
        closeDetail();
        e.preventDefault();
        return;
      }
      if (hasSelection) {
        setSelectedNodeIds([]);
        setSelectedEdgeIds([]);
        setNodes((nds) => nds.map((n) => ({ ...n, selected: false })));
        setEdges((eds) => eds.map((edge) => ({ ...edge, selected: false })));
        e.preventDefault();
        return;
      }
      if (hasDetail) {
        closeDetail();
        e.preventDefault();
      }
    }
    document.addEventListener('keydown', onEsc);
    return () => document.removeEventListener('keydown', onEsc);
  }, [
    selectedNodeIds.length,
    selectedEdgeIds.length,
    detailNodeId,
    detailEdgeId,
    contextMenu,
    closeDetail,
    setNodes,
    setEdges,
  ]);

  async function handleDelete() {
    if (selectedCount === 0) return;
    const confirmed = window.confirm(
      `Удалить ${selectedNodeIds.length} узл(а) и ${selectedEdgeIds.length} связ(и)?`,
    );
    if (!confirmed) return;

    setDeleting(true);
    try {
      // рёбра первыми чтобы не получить 404 если узел уже удалит ребро каскадом
      for (const edgeId of selectedEdgeIds) {
        try {
          await apiDeleteRaw(`/api/v1/edges/${edgeId}`);
        } catch (e: unknown) {
          if (!(e instanceof ApiError && e.status === 404)) throw e;
        }
      }
      for (const nodeId of selectedNodeIds) {
        try {
          await apiDeleteRaw(`/api/v1/nodes/${nodeId}`);
        } catch (e: unknown) {
          if (!(e instanceof ApiError && e.status === 404)) throw e;
        }
      }
      setSelectedNodeIds([]);
      setSelectedEdgeIds([]);
      onRefetch();
    } catch (e: unknown) {
      const msg = e instanceof ApiError ? e.problem.title : (e as Error).message;
      window.alert(`Не удалось удалить: ${msg}`);
    } finally {
      setDeleting(false);
    }
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
          <p className="text-gray-500">В этом графе пока нет узлов</p>
          <Button onClick={() => setAddNodeOpen(true)}>Добавить первый узел</Button>
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
          onInit={setRfInstance}
          onPaneContextMenu={handlePaneContextMenu}
          onNodeContextMenu={handleNodeContextMenu}
          onEdgeContextMenu={handleEdgeContextMenu}
          onNodeDoubleClick={handleNodeDoubleClick}
          onEdgeDoubleClick={handleEdgeDoubleClick}
          connectionMode={ConnectionMode.Loose}
          onSelectionChange={handleSelectionChange}
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          fitView
          minZoom={0.2}
          maxZoom={1.5}
          elevateNodesOnSelect={false}
          elevateEdgesOnSelect={false}
          proOptions={{ hideAttribution: true }}
        >
          <Background gap={24} size={1} />
          <CompactMiniMap />

          {/* Левая вертикальная toolbar */}
          <Panel
            position="top-left"
            className="!m-3 flex w-12 flex-col items-center gap-1 rounded-md border border-slate-200 bg-white/95 py-2 shadow-md backdrop-blur"
          >
            <IconButton
              icon={Plus}
              label="Добавить узел"
              size="md"
              onClick={() => setAddNodeOpen(true)}
            />
            <IconButton
              icon={Link2}
              label={canAddEdge ? 'Создать связь' : 'Нужно минимум 2 узла'}
              size="md"
              disabled={!canAddEdge}
              onClick={openAddEdge}
            />
            <div className="my-1 h-px w-7 bg-slate-200" />
            <IconButton
              icon={showEdgeLabels ? Eye : EyeOff}
              label={showEdgeLabels ? 'Скрыть подписи рёбер' : 'Показать подписи рёбер'}
              size="md"
              active={showEdgeLabels}
              onClick={() => setShowEdgeLabels((v) => !v)}
            />
            <div className="my-1 h-px w-7 bg-slate-200" />
            <IconButton
              icon={Trash2}
              label={
                selectedCount === 0
                  ? 'Удалить (выберите узлы или связи)'
                  : `Удалить (${selectedCount})`
              }
              size="md"
              disabled={selectedCount === 0 || deleting}
              onClick={handleDelete}
              className={selectedCount > 0 && !deleting ? '!text-red-600 hover:!bg-red-50' : ''}
            />
          </Panel>

          <Panel
            position="top-right"
            className="!m-3 flex items-center gap-3 rounded-md border border-slate-200 bg-white/95 px-3 py-2 text-[11px] text-slate-600 shadow-sm backdrop-blur"
          >
            <span className="inline-flex items-center gap-1">
              <Kbd>2клик</Kbd> детали
            </span>
            <span className="inline-flex items-center gap-1">
              <Kbd>Del</Kbd> удалить
            </span>
            <span className="inline-flex items-center gap-1">
              <Kbd>ПКМ</Kbd> меню
            </span>
          </Panel>

          <Panel
            position="bottom-left"
            className="!m-3 max-w-[280px] rounded-md border border-slate-200 bg-white/95 p-3 shadow-md backdrop-blur"
          >
            <div className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-slate-500">
              Статусы
            </div>
            <div className="grid grid-cols-2 gap-x-3 gap-y-1.5">
              {(Object.keys(STATUS_TOKENS) as Array<keyof typeof STATUS_TOKENS>).map((key) => {
                const token = STATUS_TOKENS[key];
                return (
                  <div key={key} className="flex items-center gap-1.5 text-[11px] text-slate-700">
                    <span className={`h-2.5 w-3 rounded-sm ${token.bar}`} aria-hidden="true" />
                    {token.label}
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
                label="Уменьшить"
                size="sm"
                onClick={() => rfInstance.zoomOut()}
              />
              <IconButton
                icon={ZoomIn}
                label="Увеличить"
                size="sm"
                onClick={() => rfInstance.zoomIn()}
              />
              <div className="mx-1 h-5 w-px bg-slate-200" />
              <IconButton
                icon={Maximize}
                label="По размеру"
                size="sm"
                onClick={() => rfInstance.fitView({ padding: 0.2 })}
              />
            </Panel>
          )}
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
    </>
  );
}

export default GraphCanvas;
