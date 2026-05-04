import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Panel,
  MarkerType,
  useNodesState,
  useEdgesState,
  type ReactFlowProps,
  type Node,
} from '@xyflow/react';
import { Plus, Trash2, Eye, EyeOff } from 'lucide-react';
import Button from '@/components/ui/Button';
import NodeCard, { type NodeCardNode, type NodeCardData } from '@/components/graph/NodeCard';
import CustomEdge, { type CustomEdgeEdge } from '@/components/graph/CustomEdge';
import AddNodeModal from '@/components/graph/AddNodeModal';
import AddEdgeModal from '@/components/graph/AddEdgeModal';
import NodeDetailsPanel from '@/components/graph/NodeDetailsPanel';
import { layoutGraph } from '@/utils/graphLayout';
import { apiDeleteRaw, apiGetRaw, ApiError } from '@/api/client';
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
  const [selectedNodeIds, setSelectedNodeIds] = useState<string[]>([]);
  const [selectedEdgeIds, setSelectedEdgeIds] = useState<string[]>([]);
  const [deleting, setDeleting] = useState(false);

  const rawNodeDtos = useMemo(() => graph.nodes ?? [], [graph.nodes]);
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

  // initial меняется при перезагрузке графа (после мутаций) - синхронизируем
  useEffect(() => {
    setNodes(initial.nodes);
    setEdges(initial.edges);
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
          onSelectionChange={({ nodes: ns, edges: es }) => {
            setSelectedNodeIds(ns.map((n) => n.id));
            setSelectedEdgeIds(es.map((e) => e.id));
          }}
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          fitView
          minZoom={0.2}
          maxZoom={1.5}
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
              onClick={() => setAddEdgeOpen(true)}
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
        open={addEdgeOpen}
        nodes={rawNodeDtos}
        onClose={() => setAddEdgeOpen(false)}
        onCreated={onRefetch}
      />

      {detailNode && <NodeDetailsPanel node={detailNode} onClose={closeDetail} />}
    </>
  );
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
