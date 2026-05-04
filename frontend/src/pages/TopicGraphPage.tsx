import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Panel,
  useNodesState,
  useEdgesState,
  type ReactFlowProps,
  type Node,
} from '@xyflow/react';
import { Plus } from 'lucide-react';
import Button from '@/components/ui/Button';
import NodeCard, { type NodeCardNode, type NodeCardData } from '@/components/graph/NodeCard';
import CustomEdge, { type CustomEdgeEdge } from '@/components/graph/CustomEdge';
import AddNodeModal from '@/components/graph/AddNodeModal';
import { layoutGraph } from '@/utils/graphLayout';
import { apiGetRaw, ApiError } from '@/api/client';
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

interface GraphProps {
  graph: GraphResponse;
  topicId: string;
  onRefetch: () => void;
}

function Graph({ graph, topicId, onRefetch }: GraphProps) {
  const initial = useMemo(() => buildFlow(graph), [graph]);

  const [nodes, setNodes, onNodesChange] = useNodesState<NodeCardNode>(initial.nodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<CustomEdgeEdge>(initial.edges);
  const [addNodeOpen, setAddNodeOpen] = useState(false);

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
          </Panel>
        </ReactFlow>
      )}

      <AddNodeModal
        open={addNodeOpen}
        topicId={topicId}
        onClose={() => setAddNodeOpen(false)}
        onCreated={onRefetch}
      />
    </>
  );
}

function buildFlow(graph: GraphResponse): { nodes: NodeCardNode[]; edges: CustomEdgeEdge[] } {
  const rawNodes: NodeCardNode[] = (graph.nodes ?? [])
    .filter((n): n is NodeDto & { id: string } => Boolean(n.id))
    .map((n) => ({
      id: n.id,
      type: 'argumentNode' as const,
      position: { x: 0, y: 0 },
      data: n,
    }));

  const rawEdges: CustomEdgeEdge[] = (graph.edges ?? [])
    .filter(
      (e): e is EdgeDto & { id: string; fromNodeId: string; toNodeId: string } =>
        Boolean(e.id && e.fromNodeId && e.toNodeId),
    )
    .map((e) => ({
      id: e.id,
      source: e.fromNodeId,
      target: e.toNodeId,
      type: 'argumentEdge' as const,
      data: { edgeType: e.edgeType ?? 'SUPPORTS', rationale: e.rationale },
    }));

  return { nodes: layoutGraph(rawNodes, rawEdges), edges: rawEdges };
}

export default TopicGraphPage;
