import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Panel,
  MarkerType,
  ConnectionMode,
  useNodesState,
  useEdgesState,
  type ReactFlowProps,
  type Node,
  type Edge,
  type Connection,
} from '@xyflow/react';
import { Plus, Trash2, Eye, EyeOff, Pencil, ArrowUp, ArrowDown } from 'lucide-react';
import Button from '@/components/ui/Button';
import ContextMenu, { type ContextMenuItem } from '@/components/ui/ContextMenu';
import NodeCard, { type NodeCardNode, type NodeCardData } from '@/components/graph/NodeCard';
import CustomEdge, { type CustomEdgeEdge } from '@/components/graph/CustomEdge';
import AddNodeModal from '@/components/graph/AddNodeModal';
import AddEdgeModal from '@/components/graph/AddEdgeModal';
import NodeDetailsPanel from '@/components/graph/NodeDetailsPanel';
import { layoutGraph } from '@/utils/graphLayout';
import { apiDeleteRaw, apiGetRaw, apiPatchRaw, ApiError } from '@/api/client';
import { getAllowedEdgeTypes, NODE_TYPE_LABEL } from '@/utils/edgeRules';
import { toast } from '@/stores/toastStore';
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

// Цвета статусов для MiniMap (для самого узла - в NodeCard через Tailwind).
// Дублирование с NodeCard приемлемо: MiniMap получает простой hex
const STATUS_MINIMAP_COLOR: Record<NonNullable<NodeCardData['status']>, string> = {
  STANDING: '#22c55e',
  DISPUTED: '#f59e0b',
  REFUTED: '#ef4444',
  UNVERIFIED: '#9ca3af',
};

// Цвета маркеров-стрелок на конце ребра. Совпадают со stroke в CustomEdge,
// чтобы стрелка была того же цвета что и линия
const EDGE_ARROW_COLOR: Record<NonNullable<EdgeDto['edgeType']>, string> = {
  SUPPORTS: '#22c55e',
  REFUTES: '#ef4444',
  INVALIDATES: '#b91c1c',
  QUALIFIES: '#3b82f6',
  RESPONDS_TO: '#9ca3af',
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

  const initial = useMemo(() => buildFlow(graph, showEdgeLabels), [graph, showEdgeLabels]);

  const [nodes, setNodes, onNodesChange] = useNodesState<NodeCardNode>(initial.nodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<CustomEdgeEdge>(initial.edges);
  const [addNodeOpen, setAddNodeOpen] = useState(false);
  const [addEdgeOpen, setAddEdgeOpen] = useState(false);
  // preset для AddEdgeModal: из drag-create приходят оба, из "+ Связь"
  // с выделенным узлом - только from. Поля опциональные
  const [edgeDraft, setEdgeDraft] = useState<{ from?: string; to?: string } | null>(null);
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

      setEdgeDraft({ from: connection.source, to: connection.target });
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

  // правый клик на pane - "Создать узел здесь"
  const handlePaneContextMenu = useCallback(
    (event: React.MouseEvent | MouseEvent) => {
      event.preventDefault();
      setContextMenu({
        x: event.clientX,
        y: event.clientY,
        header: 'Холст',
        items: [
          {
            id: 'create-node',
            label: 'Создать узел здесь',
            icon: Plus,
            onClick: () => setAddNodeOpen(true),
          },
        ],
      });
    },
    [],
  );

  // правый клик на узле - "Редактировать", "Удалить"
  const handleNodeContextMenu = useCallback(
    (event: React.MouseEvent, node: Node) => {
      event.preventDefault();
      setContextMenu({
        x: event.clientX,
        y: event.clientY,
        header: 'Узел',
        items: [
          {
            id: 'edit-node',
            label: 'Редактировать',
            icon: Pencil,
            onClick: () => {
              // выделяем узел - откроется боковая панель деталей
              setNodes((nds) =>
                nds.map((n) => ({ ...n, selected: n.id === node.id })),
              );
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
        ],
      });
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [setNodes],
  );

  // правый клик на ребре - "Удалить"
  const handleEdgeContextMenu = useCallback(
    (event: React.MouseEvent, edge: Edge) => {
      event.preventDefault();
      setContextMenu({
        x: event.clientX,
        y: event.clientY,
        header: 'Связь',
        items: [
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
    [],
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

  // панель деталей открыта только при выборе ровно одного узла без рёбер
  const detailNode = useMemo(() => {
    if (selectedNodeIds.length !== 1 || selectedEdgeIds.length !== 0) return null;
    return rawNodeDtos.find((n) => n.id === selectedNodeIds[0]) ?? null;
  }, [selectedNodeIds, selectedEdgeIds, rawNodeDtos]);

  const closeDetail = useCallback(() => {
    // снимаем выделение через RF state - onSelectionChange сам почистит ids
    setNodes((nds) => nds.map((n) => ({ ...n, selected: false })));
  }, [setNodes]);

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
          onNodeDragStop={handleNodeDragStop}
          onPaneContextMenu={handlePaneContextMenu}
          onNodeContextMenu={handleNodeContextMenu}
          onEdgeContextMenu={handleEdgeContextMenu}
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
          <MiniMap
            pannable
            zoomable
            position="top-right"
            className="!bg-white !border !border-gray-300"
            nodeColor={(node: Node) => {
              const data = node.data as NodeCardData | undefined;
              const status = data?.status ?? 'UNVERIFIED';
              return STATUS_MINIMAP_COLOR[status];
            }}
            nodeStrokeColor="#1f2937"
            nodeStrokeWidth={3}
            nodeBorderRadius={4}
            maskColor="rgba(0,0,0,0.08)"
          />
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
        open={addNodeOpen}
        topicId={topicId}
        onClose={() => setAddNodeOpen(false)}
        onCreated={onRefetch}
      />

      <AddEdgeModal
        // key включает edgeDraft чтобы переоткрытие с другими initial values
        // дало чистый state без useEffect-сброса (eslint set-state-in-effect)
        key={`addEdge-${edgeDraft?.from ?? ''}-${edgeDraft?.to ?? ''}`}
        open={addEdgeOpen}
        nodes={rawNodeDtos}
        initialFromId={edgeDraft?.from}
        initialToId={edgeDraft?.to}
        onClose={closeAddEdge}
        onCreated={onRefetch}
      />

      {detailNode && (
        <NodeDetailsPanel
          // key включает updatedAt чтобы после save компонент перемонтировался
          // с чистым state (свернутая история, не-loaded ревизии)
          key={`${detailNode.id}-${detailNode.updatedAt ?? ''}`}
          node={detailNode}
          onClose={closeDetail}
          onUpdated={onRefetch}
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

function buildFlow(
  graph: GraphResponse,
  showEdgeLabels: boolean,
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

  return { nodes: layoutGraph(rawNodes, rawEdges), edges: rawEdges };
}

export default TopicGraphPage;
