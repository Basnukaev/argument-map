import { useEffect, useMemo, useState } from 'react';
import {
  ReactFlow,
  Background,
  BackgroundVariant,
  Controls,
  Panel,
  MarkerType,
  type Edge,
} from '@xyflow/react';
import { Loader2, Network } from 'lucide-react';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import { useT, type DictKey } from '@/shared/i18n';
import SanadGraphNode, { type SanadNode } from './SanadGraphNode';
import NarratorPanel from './NarratorPanel';
import { layoutSanad } from '@/apps/hadith/utils/sanadLayout';
import { edgeStroke, RELIABILITY_TOKENS } from '@/apps/hadith/sanadTokens';
import type { ReliabilityGrade, SanadFlowNodeData, SanadGraphResponse } from '@/apps/hadith/types';

// Стабильная ссылка между рендерами (иначе React Flow предупреждает и
// пересоздаёт типы узлов на каждый render).
const nodeTypes = { sanad: SanadGraphNode };

// Подмножество степеней для компактной легенды (полный список — в панели).
const LEGEND_GRADES: ReliabilityGrade[] = ['SAHABI', 'THIQA', 'SADUQ', 'DAIF'];

interface SanadGraphProps {
  hadithId: string;
}

/**
 * Read-only визуализация иснада через React Flow. Граф строится бэкендом
 * (дедуплицированные узлы + синтетический Пророк ﷺ), раскладка — dagre TB.
 * Клик на передатчике открывает биографию в боковой панели.
 */
function SanadGraph({ hadithId }: SanadGraphProps) {
  const t = useT();
  const [graph, setGraph] = useState<SanadGraphResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<SanadFlowNodeData | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    // Сброс состояния при смене hadithId: новый граф = чистый старт
    // (loading-плейсхолдер без stale-данных предыдущего хадиса).
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setGraph(null);
    setError(null);
    setSelected(null);
    apiGetRaw<SanadGraphResponse>(`/api/v1/hadith/hadiths/${hadithId}/sanad-graph`, {
      signal: controller.signal,
    })
      .then(setGraph)
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        setError(e instanceof ApiError ? e.problem.title : String(e));
      });
    return () => controller.abort();
  }, [hadithId]);

  // dagre-раскладка + маппинг дорогие, а React Flow перерисовывается часто
  // при pan/zoom — memo по graph (реальная перф-проблема, не превентивный memo).
  const { rfNodes, rfEdges } = useMemo(() => {
    if (!graph) return { rfNodes: [] as SanadNode[], rfEdges: [] as Edge[] };
    const nodes: SanadNode[] = graph.nodes.map((n) => ({
      id: n.id,
      type: 'sanad',
      position: { x: 0, y: 0 },
      data: { ...n.data, role: n.role },
      draggable: false,
    }));
    const edges: Edge[] = graph.edges.map((e) => {
      const stroke = edgeStroke(e.data.chainGrade);
      return {
        id: e.id,
        source: e.source,
        target: e.target,
        type: 'smoothstep',
        label: e.data.transmissionPhrase ?? undefined,
        labelStyle: {
          fontFamily: 'var(--font-arabic)',
          fontSize: 13,
          fontWeight: 600,
          fill: 'var(--c-ink-700)',
        },
        labelBgStyle: { fill: 'var(--c-bg-elevated)', fillOpacity: 0.92 },
        labelBgPadding: [4, 2] as [number, number],
        labelBgBorderRadius: 4,
        style: { stroke, strokeWidth: e.data.onPrimaryChain ? 2.4 : 1.6 },
        markerEnd: { type: MarkerType.ArrowClosed, color: stroke, width: 16, height: 16 },
      };
    });
    return { rfNodes: layoutSanad(nodes, edges), rfEdges: edges };
  }, [graph]);

  if (error) {
    return (
      <div className="flex h-full items-center justify-center p-6 text-center text-sm text-err-700">
        {error}
      </div>
    );
  }
  if (!graph) {
    return (
      <div className="flex h-full items-center justify-center gap-2 text-sm text-ink-500">
        <Loader2 size={16} className="animate-spin" aria-hidden /> {t('hadith.graph.loading')}
      </div>
    );
  }
  if (graph.nodes.length === 0) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-2 text-sm text-ink-500">
        <Network size={28} className="text-ink-300" aria-hidden />
        {t('hadith.graph.empty')}
      </div>
    );
  }

  return (
    <div className="relative h-full w-full">
      <ReactFlow<SanadNode, Edge>
        nodes={rfNodes}
        edges={rfEdges}
        nodeTypes={nodeTypes}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        minZoom={0.3}
        maxZoom={1.75}
        nodesDraggable={false}
        nodesConnectable={false}
        edgesFocusable={false}
        elementsSelectable
        proOptions={{ hideAttribution: true }}
        onNodeClick={(_, node) => {
          if (node.data.role !== 'PROPHET') setSelected(node.data);
        }}
        onPaneClick={() => setSelected(null)}
      >
        <Background variant={BackgroundVariant.Dots} gap={20} size={1} />
        <Controls showInteractive={false} />

        <Panel
          position="top-left"
          className="max-w-[260px] rounded-md border border-border-strong bg-elevated/95 p-3 text-xs shadow-sh1 backdrop-blur"
        >
          <div className="mb-2">
            <div className="mb-1 font-semibold text-ink-700">{t('hadith.graph.legend_chains')}</div>
            <ul className="space-y-1">
              {graph.sanads.map((s) => (
                <li key={s.id} className="flex items-center gap-2" dir="auto">
                  <span
                    className="inline-block h-1.5 w-4 rounded-full"
                    style={{ backgroundColor: edgeStroke(s.chainGrade) }}
                  />
                  <span className="text-ink-700">{s.collectionRu ?? s.collectionAr ?? '—'}</span>
                  {s.primaryChain && (
                    <span className="rounded-sm bg-accent-50 px-1 text-[10px] font-medium text-accent-700">
                      {t('hadith.graph.primary')}
                    </span>
                  )}
                </li>
              ))}
            </ul>
          </div>

          <div className="mb-2">
            <div className="mb-1 font-semibold text-ink-700">
              {t('hadith.graph.legend_reliability')}
            </div>
            <ul className="grid grid-cols-2 gap-x-2 gap-y-1">
              {LEGEND_GRADES.map((g) => (
                <li key={g} className="flex items-center gap-1.5">
                  <span className={`inline-block h-2 w-2 rounded-full ${RELIABILITY_TOKENS[g].dot}`} />
                  <span className="text-ink-600">{t(`hadith.reliability.${g}` as DictKey)}</span>
                </li>
              ))}
            </ul>
          </div>

          <p className="text-[11px] leading-snug text-ink-500">
            {t('hadith.graph.transmission_hint')}
          </p>
        </Panel>
      </ReactFlow>

      {selected && <NarratorPanel data={selected} onClose={() => setSelected(null)} />}
    </div>
  );
}

export default SanadGraph;
