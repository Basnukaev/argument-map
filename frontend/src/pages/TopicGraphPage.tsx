import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router';
import {
  ReactFlow,
  Background,
  Controls,
  Panel,
  MarkerType,
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
import { Plus, Trash2, Eye, EyeOff, Pencil, ArrowUp, ArrowDown } from 'lucide-react';
import Button from '@/components/ui/Button';
import ContextMenu, { type ContextMenuItem } from '@/components/ui/ContextMenu';
import NodeCard, { type NodeCardNode, type NodeCardData } from '@/components/graph/NodeCard';
import CustomEdge, { type CustomEdgeEdge } from '@/components/graph/CustomEdge';
import AddNodeModal from '@/components/graph/AddNodeModal';
import AddEdgeModal from '@/components/graph/AddEdgeModal';
import NodeDetailsPanel from '@/components/graph/NodeDetailsPanel';
import EdgeDetailsPanel from '@/components/graph/EdgeDetailsPanel';
import CompactMiniMap from '@/components/graph/CompactMiniMap';
import { layoutGraph } from '@/utils/graphLayout';
import { apiDeleteRaw, apiGetRaw, apiPatchRaw, ApiError } from '@/api/client';
import {
  getAllowedEdgeTypes,
  getRelatedNodeOptions,
  isEdgeAllowed,
  NODE_TYPE_LABEL,
} from '@/utils/edgeRules';
import type { AutoEdgeSpec } from '@/components/graph/AddNodeModal';
import { toast } from '@/stores/toastStore';
import { EDGE_TYPE_TOKENS } from '@/utils/designTokens';
import type { components } from '@/api/types';

type GraphResponse = components['schemas']['GraphResponse'];
type NodeDto = components['schemas']['NodeResponse'];
type EdgeDto = components['schemas']['EdgeResponse'];

// nodeTypes/edgeTypes должны быть стабильными ссылками между рендерами,
// иначе React Flow ругается и пере-инициализируется (см coding-standards)
const nodeTypes: ReactFlowProps['nodeTypes'] = { argumentNode: NodeCard };
const edgeTypes: ReactFlowProps['edgeTypes'] = { argumentEdge: CustomEdge };

type ViewState =
  | { kind: 'loading' }
  | { kind: 'success'; graph: GraphResponse }
  | { kind: 'error'; message: string };

function TopicGraphPage() {
  const { topicId } = useParams<{ topicId: string }>();
  const [state, setState] = useState<ViewState>({ kind: 'loading' });
  const [refreshKey, setRefreshKey] = useState(0);

  const refetch = useCallback(() => setRefreshKey((k) => k + 1), []);

  useEffect(() => {
    if (!topicId) return;
    const controller = new AbortController();
    apiGetRaw<GraphResponse>(`/api/v1/topics/${topicId}/graph`, {
      signal: controller.signal,
    })
      .then((graph) => {
        setState({ kind: 'success', graph });
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        const message =
          e instanceof ApiError
            ? `${e.problem.title}${e.problem.detail ? ': ' + e.problem.detail : ''}`
            : e instanceof Error
              ? e.message
              : 'Не удалось загрузить граф';
        setState({ kind: 'error', message });
      });
    return () => controller.abort();
  }, [topicId, refreshKey]);

  return (
    <div className="flex h-screen flex-col bg-gray-50">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-3">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">
            {state.kind === 'success' ? state.graph.topic?.title ?? 'Граф темы' : 'Граф темы'}
          </h1>
          {state.kind === 'success' && state.graph.topic?.description && (
            <p className="text-sm text-gray-500">{state.graph.topic.description}</p>
          )}
        </div>
        <Link to="/topics">
          <Button variant="secondary">К списку</Button>
        </Link>
      </header>

      <main className="relative flex-1 overflow-hidden">
        {state.kind === 'loading' && (
          <div className="absolute inset-0 flex items-center justify-center text-gray-500">
            Загрузка графа
          </div>
        )}

        {state.kind === 'error' && (
          <div className="absolute inset-0 flex items-center justify-center p-8">
            <div className="max-w-lg rounded-md border border-red-300 bg-red-50 p-4 text-red-800">
              <p className="font-medium">Ошибка</p>
              <p className="mt-1 text-sm">{state.message}</p>
            </div>
          </div>
        )}

        {state.kind === 'success' && topicId && (
          <Graph graph={state.graph} topicId={topicId} onRefetch={refetch} />
        )}
      </main>
    </div>
  );
}

// Цвета маркеров-стрелок на конце ребра. Берём из EDGE_TYPE_TOKENS чтобы
// стрелка была того же цвета что и линия в CustomEdge - один источник
const EDGE_ARROW_COLOR: Record<NonNullable<EdgeDto['edgeType']>, string> = {
  SUPPORTS: EDGE_TYPE_TOKENS.SUPPORTS.stroke,
  REFUTES: EDGE_TYPE_TOKENS.REFUTES.stroke,
  INVALIDATES: EDGE_TYPE_TOKENS.INVALIDATES.stroke,
  QUALIFIES: EDGE_TYPE_TOKENS.QUALIFIES.stroke,
  RESPONDS_TO: EDGE_TYPE_TOKENS.RESPONDS_TO.stroke,
};

interface GraphProps {
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

function Graph({ graph, topicId, onRefetch }: GraphProps) {
  const [showEdgeLabels, setShowEdgeLabels] = useState<boolean>(readShowLabels);

  useEffect(() => {
    window.localStorage.setItem(SHOW_LABELS_LS_KEY, String(showEdgeLabels));
  }, [showEdgeLabels]);

  // ref на последний RF-state, чтобы при rebuild графа (новый refetch)
  // переиспользовать уже размещённые позиции узлов. Без этого fresh-узлы
  // (без posX/posY на бэке) при каждом mixed-layout переезжают в столбец
  // справа - даже если они уже стоят на читаемых dagre-местах
  const lastNodesRef = useRef<NodeCardNode[]>([]);

  // Чтение ref'а в useMemo - сознательно: нам нужен последний snapshot
  // позиций для passive layout-hint, не для реактивности. Если бы мы
  // делали useState - перерасчёт buildFlow срабатывал бы на каждый
  // node-drag, что дороже
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
  // но эти позиции живут только в RF-state. При следующем refetch и
  // создании нового узла (получает свой posX/posY на бэке) layoutGraph
  // переходит в mixed-режим - сохранённые остаются, а dagre-узлы прыгают
  // столбцом справа. Чтобы сохранить layout стабильным - сразу PATCH'им
  // все узлы без координат. Через ~2 сек граф становится full-saved
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
        // не блокирующая ошибка - узел останется без координат на бэке,
        // на следующем рефетче снова попадёт в эту ветку
      });
    }
    // намеренно не зависим от initial.nodes - они могли поменяться от
    // showEdgeLabels (build перерасчёт), но позиции уже зафиксированы
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [graph]);
  const [addNodeOpen, setAddNodeOpen] = useState(false);
  const [addEdgeOpen, setAddEdgeOpen] = useState(false);
  // черновик для AddNodeModal: координаты "Создать здесь" из меню pane
  // и/или предустановленный тип + autoEdge для "Добавить связанный X"
  // из меню узла. Поля опциональные, можно использовать в любых сочетаниях
  const [nodeDraft, setNodeDraft] = useState<{
    posX?: number;
    posY?: number;
    nodeType?: NodeCardNode['data']['nodeType'];
    autoEdge?: AutoEdgeSpec;
  } | null>(null);
  // RF instance нужен для screenToFlowPosition (конверсия viewport-координат
  // курсора в координаты канваса с учётом zoom/pan)
  const [rfInstance, setRfInstance] = useState<ReactFlowInstance<NodeCardNode, CustomEdgeEdge> | null>(null);
  // preset для AddEdgeModal: из drag-create приходят from/to и handle-стороны,
  // из "+ Связь" с выделенным узлом - только from. Поля опциональные
  const [edgeDraft, setEdgeDraft] = useState<{
    from?: string;
    to?: string;
    sourceHandle?: string;
    targetHandle?: string;
  } | null>(null);
  const [selectedNodeIds, setSelectedNodeIds] = useState<string[]>([]);
  const [selectedEdgeIds, setSelectedEdgeIds] = useState<string[]>([]);
  const [deleting, setDeleting] = useState(false);

  // контекстное меню (правый клик). Хранится как { координаты + items }
  // или null когда меню закрыто
  const [contextMenu, setContextMenu] = useState<{
    x: number;
    y: number;
    items: ContextMenuItem[];
    header?: string;
  } | null>(null);

  const closeContextMenu = useCallback(() => setContextMenu(null), []);

  // id узла/ребра, для которого открыта панель деталей. Не зависит от
  // selection: drag триггерит selection без открытия панели. Открывается
  // через double-click или "Редактировать" в контекстном меню
  const [detailNodeId, setDetailNodeId] = useState<string | null>(null);
  const [detailEdgeId, setDetailEdgeId] = useState<string | null>(null);

  // флаг "начать редактирование сразу" для NodeDetailsPanel - срабатывает
  // когда пользователь нажал "Редактировать" в контекстном меню узла.
  // Сбрасывается при закрытии панели или смене выделения
  const [editTargetNodeId, setEditTargetNodeId] = useState<string | null>(null);
  // аналогично для EdgeDetailsPanel
  const [editTargetEdgeId, setEditTargetEdgeId] = useState<string | null>(null);

  // счётчики z-index для "на передний/задний план". Не сохраняются на беке -
  // только локально пока открыт граф. При refetch сбрасываются на дефолт RF.
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

  // reconnect - перетащить конец существующего ребра на другой handle/узел.
  // Тип ребра сохраняется, меняются только концы и handle'ы. Если новая пара
  // (fromType, edgeType, toType) запрещена матрицей ADR-010 - toast, ребро
  // не меняется. Иначе optimistic update local state через reconnectEdge,
  // потом PATCH /edges/{id} + refetch (ADR-014).
  //
  // Без optimistic update RF откатывал бы ребро на исходное место сразу
  // после drop, и только через ~100мс (после refetch) ребро становилось бы
  // в новое положение - заметный flicker. С reconnectEdge - ребро встаёт в
  // новое место мгновенно, refetch синхронизирует с бэк-state в фоне
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

      // мгновенно обновляем local state - ребро встаёт в новое место без flicker.
      // cast: oldEdge - это всегда наш CustomEdgeEdge (RF не знает narrow-тип
      // из дженерика onReconnect), reconnectEdge возвращает массив того же типа
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
          // refetch вернёт ребро к серверному состоянию - оптимистичное
          // обновление будет откачено
          onRefetch();
        });
    },
    [rawNodeDtos, onRefetch, setEdges],
  );

  // drag-end - отправляем PATCH с координатами. Не ждём ответ, оптимистично.
  // При ошибке - toast, рефетч пересчитает layout с прежними координатами
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

  // удаление одного узла или ребра по id - вызывается из контекстного меню
  // Без window.confirm, потому что отдельный контекстный пункт уже выражает
  // намерение пользователя
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
      // конвертируем viewport-координаты в координаты канваса RF (учёт
      // zoom/pan) - после создания узел встанет точно туда куда нажали
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

  // double-click на узле/ребре открывает панель деталей. Single click
  // только выделяет (через RF onSelectionChange) - drag не открывает панель
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

  // правый клик на узле - "Добавить связанный X" по матрице ADR-010 +
  // "Редактировать", z-order, "Удалить"
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
          // ищем свободную позицию рядом с anchor. Используем lastNodesRef
          // (а не nodes из closure) - useCallback не пересоздавался после
          // предыдущего create, и nodes из closure был бы устаревшим
          // snapshot'ом без только что добавленных узлов
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
            // открываем панель и переходим сразу в edit-режим через
            // editTargetNodeId. detailEdgeId сбрасываем чтобы не было
            // двух одновременных panels
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

  // правый клик на ребре - "Редактировать", "На передний/задний план", "Удалить"
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
              // открываем панель ребра в edit-режиме
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

  // RF onSelectionChange срабатывает при каждом setNodes - даже если selection
  // не изменилась. Inline `({nodes, edges}) => setSelectedNodeIds(nodes.map(...))`
  // создавал новые [] массивы каждый раз -> useState считает их разными ->
  // re-render -> снова onSelectionChange -> infinite loop. Решение: stable
  // callback через useCallback + функциональный update со сравнением содержимого
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

  // панель деталей узла открыта когда detailNodeId выставлен (через
  // double-click или контекстное меню "Редактировать"). Не зависит от
  // selection - можно перетаскивать узел без всплывания панели
  const detailNode = useMemo(() => {
    if (!detailNodeId) return null;
    return rawNodeDtos.find((n) => n.id === detailNodeId) ?? null;
  }, [detailNodeId, rawNodeDtos]);

  const rawEdgeDtos = useMemo(() => graph.edges ?? [], [graph.edges]);

  // панель деталей ребра открыта когда detailEdgeId выставлен.
  // Содержит сам edge dto + резолвленные from/to узлы для превью
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

  // Escape с очередью:
  // 1. если фокус внутри sidebar → сразу закрыть его (юзер кликнул в панель)
  // 2. иначе если есть выделение → снять выделение
  // 3. иначе если открыта панель → закрыть её
  // Open dialog (Modal) или ContextMenu - пропускаем, они закроются сами
  useEffect(() => {
    function onEsc(e: KeyboardEvent) {
      if (e.key !== 'Escape') return;

      // нативный <dialog open> закроется сам (showModal API)
      if (document.querySelector('dialog[open]')) return;
      // ContextMenu имеет свой Esc-обработчик через onClose
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
      // рёбра первыми - так не получим 404 если узел уже удалит ребро каскадом
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

  // initial меняется при перезагрузке графа (после мутаций) - синхронизируем,
  // сохраняя выделение по id (чтобы панель деталей не закрывалась после PATCH)
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
    // selectedNodeIds/Ids - намеренно не в deps: иначе любой клик пере-инициализировал бы граф.
    // initial меняется только при refetch, и тогда восстанавливаем selection по последнему известному snapshot.
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
          // не подкидывать selected узел/ребро поверх остальных - z-order
          // полностью контролируется явным zIndex (контекстное меню E.d)
          elevateNodesOnSelect={false}
          elevateEdgesOnSelect={false}
          proOptions={{ hideAttribution: true }}
        >
          <Background gap={24} size={1} />
          <Controls position="bottom-right" showInteractive={false} />
          <CompactMiniMap />
          <Panel position="top-left" className="!m-3 flex gap-2">
            <Button onClick={() => setAddNodeOpen(true)} className="!px-3 !py-1.5 text-sm">
              <Plus size={16} className="mr-1" /> Узел
            </Button>
            <Button
              onClick={openAddEdge}
              disabled={!canAddEdge}
              variant="secondary"
              className="!px-3 !py-1.5 text-sm"
              title={canAddEdge ? undefined : 'Нужно минимум 2 узла'}
            >
              <Plus size={16} className="mr-1" /> Связь
            </Button>
            <Button
              onClick={handleDelete}
              disabled={selectedCount === 0 || deleting}
              variant="danger"
              className="!px-3 !py-1.5 text-sm"
            >
              <Trash2 size={16} className="mr-1" />
              {deleting ? 'Удаляем' : `Удалить${selectedCount > 0 ? ` (${selectedCount})` : ''}`}
            </Button>
            <Button
              onClick={() => setShowEdgeLabels((v) => !v)}
              variant="secondary"
              className="!px-3 !py-1.5 text-sm"
              title={showEdgeLabels ? 'Скрыть подписи рёбер' : 'Показать подписи рёбер'}
              aria-label={showEdgeLabels ? 'Скрыть подписи' : 'Показать подписи'}
              aria-pressed={showEdgeLabels}
            >
              {showEdgeLabels ? <Eye size={16} /> : <EyeOff size={16} />}
            </Button>
          </Panel>
        </ReactFlow>
      )}

      <AddNodeModal
        // key включает все поля nodeDraft чтобы каждое открытие давало
        // чистый initial state. autoEdge кодируем в key через anchorNodeId+edgeType
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
        // key включает edgeDraft чтобы переоткрытие с другими initial values
        // дало чистый state без useEffect-сброса (eslint set-state-in-effect)
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
          // key включает updatedAt и editTarget чтобы компонент
          // перемонтировался при save (чистый state) и при клике
          // "Редактировать" из контекстного меню (открыться в editing)
          key={`${detailNode.id}-${detailNode.updatedAt ?? ''}-${editTargetNodeId === detailNode.id ? 'edit' : 'view'}`}
          node={detailNode}
          onClose={closeDetail}
          onUpdated={onRefetch}
          initialEditing={editTargetNodeId === detailNode.id}
        />
      )}

      {detailEdge && (
        <EdgeDetailsPanel
          // key включает edgeType+rationale (после save они меняются - чистый
          // mount) и editTarget (чтобы при клике из меню панель открылась в editing).
          // Edge не имеет updatedAt - используем сами поля как маркер изменения
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

// поверхностное сравнение массивов id - чтобы не пере-устанавливать
// selectedNodeIds/selectedEdgeIds если содержимое не изменилось
function sameIds(a: string[], b: string[]): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
  return true;
}

// размеры под NodeCard (w-72 = 288px, высота переменная). 120 - типичная
// высота с заголовком + 2 строками текста; 40 - воздух между узлами
const NODE_W = 288;
const NODE_H = 120;
const PLACE_GAP_X = 60;
const PLACE_GAP_Y = 40;

interface XY {
  x: number;
  y: number;
}

/**
 * Подбирает позицию для нового узла рядом с anchor так чтобы не
 * накладываться на существующие. Сначала пробует базовую точку (на
 * правильной стороне от anchor), потом расходится по спирали по Y и X
 * пока не найдёт свободное место. Если всё пространство занято в
 * пределах разумного - возвращает базовую позицию (узлы наложатся,
 * юзер dragнет).
 *
 * direction='incoming' - новый узел слева от anchor (он source ребра);
 * 'outgoing' - справа (он target). Это совпадает с естественной
 * лево-вправо ориентацией dagre LR
 */
function findFreePosition(
  anchor: XY,
  direction: 'incoming' | 'outgoing',
  existing: ReadonlyArray<{ position: XY }>,
): XY {
  const baseDx = direction === 'incoming' ? -(NODE_W + PLACE_GAP_X) : NODE_W + PLACE_GAP_X;
  const stepY = NODE_H + PLACE_GAP_Y;
  const stepX = NODE_W + PLACE_GAP_X;

  function overlaps(x: number, y: number): boolean {
    return existing.some((n) => {
      // bbox-overlap: считаем что ВСЕ узлы NODE_W × NODE_H. Реальные размеры
      // могут немного отличаться, но для размещения - достаточно
      return Math.abs(n.position.x - x) < NODE_W && Math.abs(n.position.y - y) < NODE_H;
    });
  }

  // спиральный обход вокруг базовой точки: сначала по вертикали с малым
  // шагом, потом расходимся горизонтально (всё на той же стороне от anchor)
  // shells - количество "колец" вокруг anchor; каждое кольцо добавляет
  // больше вариантов. Ограничиваем 6 чтобы не уйти в бесконечность
  const SHELLS = 6;
  for (let shell = 0; shell <= SHELLS; shell++) {
    // в shell=0 пробуем только базовую точку. Дальше - расширяем
    const yOffsets = shell === 0 ? [0] : [shell, -shell];
    const xMultipliers = shell <= 2 ? [0] : [0, 0.5, -0.5];

    for (const xMul of xMultipliers) {
      for (const yMul of yOffsets) {
        // dx: на правильной стороне от anchor + опциональный перенос
        // вглубь (xMul × stepX). Знак xMul по направлению (incoming←,
        // outgoing→), чтобы спираль расходилась "от anchor"
        const dirSign = direction === 'incoming' ? -1 : 1;
        const dx = baseDx + xMul * stepX * dirSign;
        const dy = yMul * stepY;
        const x = anchor.x + dx;
        const y = anchor.y + dy;
        if (!overlaps(x, y)) return { x, y };
      }
    }
  }
  // всё занято - даём базовую позицию
  return { x: anchor.x + baseDx, y: anchor.y };
}

function buildFlow(
  graph: GraphResponse,
  showEdgeLabels: boolean,
  previousNodes: ReadonlyArray<NodeCardNode> = [],
): { nodes: NodeCardNode[]; edges: CustomEdgeEdge[] } {
  const rawNodes: NodeCardNode[] = (graph.nodes ?? [])
    .filter((n): n is NodeDto & { id: string } => Boolean(n.id))
    .map((n) => ({
      id: n.id,
      type: 'argumentNode' as const,
      position: { x: 0, y: 0 },
      data: n,
    }));

  // быстрый поиск типа узла по id - нужен чтобы прокинуть в data ребра
  const nodeTypeById = new Map<string, NonNullable<NodeDto['nodeType']>>();
  for (const n of rawNodes) {
    if (n.data.nodeType) nodeTypeById.set(n.id, n.data.nodeType);
  }

  const rawEdges: CustomEdgeEdge[] = (graph.edges ?? [])
    .filter(
      (e): e is EdgeDto & { id: string; fromNodeId: string; toNodeId: string } =>
        Boolean(e.id && e.fromNodeId && e.toNodeId),
    )
    .map((e) => {
      const edgeType = e.edgeType ?? 'SUPPORTS';
      const fromType = nodeTypeById.get(e.fromNodeId) ?? 'CLAIM';
      const toType = nodeTypeById.get(e.toNodeId) ?? 'CLAIM';
      return {
        id: e.id,
        source: e.fromNodeId,
        target: e.toNodeId,
        // sourceHandle/targetHandle на верхнем уровне - RF использует
        // их для рендера ребра от конкретной точки. Если null - RF
        // применит auto-routing по позициям узлов (как раньше)
        sourceHandle: e.sourceHandle ?? undefined,
        targetHandle: e.targetHandle ?? undefined,
        type: 'argumentEdge' as const,
        data: {
          edgeType,
          fromType,
          toType,
          rationale: e.rationale,
          showLabel: showEdgeLabels,
        },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: EDGE_ARROW_COLOR[edgeType],
          width: 18,
          height: 18,
        },
      };
    });

  return { nodes: layoutGraph(rawNodes, rawEdges, 'LR', previousNodes), edges: rawEdges };
}

export default TopicGraphPage;
