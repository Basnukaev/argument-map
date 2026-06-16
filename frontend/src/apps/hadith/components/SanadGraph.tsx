import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import {
  ReactFlow,
  Background,
  BackgroundVariant,
  Controls,
  Panel,
  MarkerType,
  type Edge,
} from '@xyflow/react';
import { BookOpen, Loader2, Network } from 'lucide-react';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import { useT, type DictKey } from '@/shared/i18n';
import SanadGraphNode, { type SanadNode } from './SanadGraphNode';
import NarratorPanel from './NarratorPanel';
import { layoutSanad } from '@/apps/hadith/utils/sanadLayout';
import { edgeStroke, RELIABILITY_TOKENS } from '@/apps/hadith/sanadTokens';
import type {
  NarratorData,
  ReliabilityGrade,
  SanadFlowNodeData,
  SanadGraphNodeData,
  SanadGraphResponse,
  TransmitterRole,
} from '@/apps/hadith/types';

// Стабильная ссылка между рендерами (иначе React Flow предупреждает и
// пересоздаёт типы узлов на каждый render).
const nodeTypes = { sanad: SanadGraphNode };

// Подмножество степеней для компактной легенды (полный список — в панели).
const LEGEND_GRADES: ReliabilityGrade[] = ['SAHABI', 'THIQA', 'SADUQ', 'DAIF'];

interface SanadGraphProps {
  /**
   * Self-fetch режим: компонент сам запрашивает граф по hadithId. Игнорируется,
   * если передан проп `graph` (controlled-режим).
   */
  hadithId?: string;
  /**
   * Controlled-режим данных: готовый граф передаётся снаружи (admin-превью
   * извлечённого ИИ иснада ИЛИ lifted-фетч страницы хадиса). Если проп
   * присутствует (даже `null`) — внутренний fetch отключается; `null`/пустые
   * узлы трактуются как empty-state.
   */
  graph?: SanadGraphResponse | null;
  /**
   * Controlled-режим выбора: страница владеет selected-state (единая панель
   * для клика по графу И по тексту иснада). Если передан — клик по узлу
   * пробрасывается наверх вместо открытия внутренней панели; внутренняя
   * NarratorPanel не рендерится (панелью владеет родитель).
   */
  onNarratorSelect?: (data: SanadFlowNodeData) => void;
  /**
   * hadithId текущей страницы — для version-узлов: совпадение с
   * version.hadithId помечает узел «вы здесь» (не-кликабелен). Клик по чужому
   * version-узлу навигирует на detail той передачи.
   */
  currentHadithId?: string;
}

/**
 * Read-only визуализация иснада через React Flow. Граф строится бэкендом
 * (дедуплицированные узлы + синтетический Пророк ﷺ), раскладка — dagre TB.
 * Клик на передатчике открывает биографию в боковой панели.
 *
 * Два режима:
 *  - self-fetch (`hadithId` задан, `graph` НЕ передан) — как на странице хадиса;
 *  - controlled (`graph` передан) — рендер переданных данных без fetch
 *    (admin-превью извлечённого ИИ иснада).
 */
function SanadGraph({
  hadithId,
  graph: graphProp,
  onNarratorSelect,
  currentHadithId,
}: SanadGraphProps) {
  const t = useT();
  const navigate = useNavigate();
  // Controlled-режим данных определяется по присутствию пропа `graph` (даже `null`).
  const controlled = graphProp !== undefined;
  // Controlled-режим выбора: родитель владеет панелью передатчика.
  const selectionControlled = onNarratorSelect !== undefined;
  const [fetchedGraph, setFetchedGraph] = useState<SanadGraphResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<SanadFlowNodeData | null>(null);

  // В controlled-режиме источник данных — проп; иначе результат внутреннего fetch.
  const graph = controlled ? graphProp : fetchedGraph;

  useEffect(() => {
    // Fetch только в self-fetch режиме: проп `graph` не передан и есть hadithId.
    if (controlled || !hadithId) return;
    const controller = new AbortController();
    // Сброс состояния при смене hadithId: новый граф = чистый старт
    // (loading-плейсхолдер без stale-данных предыдущего хадиса).
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setFetchedGraph(null);
    setError(null);
    setSelected(null);
    apiGetRaw<SanadGraphResponse>(`/api/v1/hadith/hadiths/${hadithId}/sanad-graph`, {
      signal: controller.signal,
    })
      .then(setFetchedGraph)
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        setError(e instanceof ApiError ? e.problem.title : String(e));
      });
    return () => controller.abort();
  }, [controlled, hadithId]);

  // dagre-раскладка + маппинг дорогие, а React Flow перерисовывается часто
  // при pan/zoom — memo по graph (реальная перф-проблема, не превентивный memo).
  const { rfNodes, rfEdges } = useMemo(() => {
    if (!graph) return { rfNodes: [] as SanadNode[], rfEdges: [] as Edge[] };
    const nodes: SanadNode[] = graph.nodes.map((n) => {
      // Version-узлы: data с бэка null, смысловые поля в n.version. Помечаем
      // «свой» узел (isCurrent) сравнением с hadithId страницы.
      const data: SanadGraphNodeData =
        n.role === 'VERSION' && n.version
          ? {
              ...n.version,
              role: 'VERSION',
              isCurrent: currentHadithId != null && n.version.hadithId === currentHadithId,
            }
          : { ...(n.data as NarratorData), role: n.role as TransmitterRole };
      return {
        id: n.id,
        type: 'sanad',
        position: { x: 0, y: 0 },
        data,
        draggable: false,
      };
    });
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
  }, [graph, currentHadithId]);

  if (error) {
    return (
      <div className="flex h-full items-center justify-center p-6 text-center text-sm text-err-700">
        {error}
      </div>
    );
  }
  // Loading-плейсхолдер — только в self-fetch режиме (ждём ответ). В controlled
  // режиме `null`/пустой граф = сразу empty-state (нечего ждать).
  if (!controlled && !graph) {
    return (
      <div className="flex h-full items-center justify-center gap-2 text-sm text-ink-500">
        <Loader2 size={16} className="animate-spin" aria-hidden /> {t('hadith.graph.loading')}
      </div>
    );
  }
  if (!graph || graph.nodes.length === 0) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-2 text-sm text-ink-500">
        <Network size={28} className="text-ink-300" aria-hidden />
        {t('hadith.graph.empty')}
      </div>
    );
  }

  // Ярлык «основная» информативен лишь когда основная цепь ровно одна (режим
  // одного хадиса). В merged-turuq все цепи помечены primary → ярлык не несёт
  // информации и его скрываем (легенда подписывает цепи сборником).
  const primaryBadgeMeaningful =
    graph.sanads.filter((s) => s.primaryChain).length === 1;

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
          const d = node.data;
          if (d.role === 'PROPHET') return;
          // Version-узел: навигация на detail той передачи (чужой узел);
          // свой («вы здесь») — не-кликабелен.
          if (d.role === 'VERSION') {
            if (!d.isCurrent) navigate(`/hadith/hadiths/${d.hadithId}`);
            return;
          }
          // Controlled-выбор: пробрасываем наверх (единая панель страницы);
          // иначе открываем внутреннюю панель (self-fetch экраны / admin-превью).
          if (selectionControlled) onNarratorSelect(d);
          else setSelected(d);
        }}
        onPaneClick={() => {
          if (!selectionControlled) setSelected(null);
        }}
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
                  {/* «основная» осмысленна, только когда выделяет ОДНУ цепь среди
                      прочих. В turuq-режиме («Все пути») каждая цепь — основная
                      своего хадиса, ярлык на всех = шум, поэтому скрываем. */}
                  {s.primaryChain && primaryBadgeMeaningful && (
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

          <div className="mb-2 flex items-start gap-1.5 text-[11px] leading-snug text-ink-500">
            <BookOpen size={12} className="mt-0.5 shrink-0 text-sky-500" aria-hidden />
            <span>{t('hadith.graph.legend_version')}</span>
          </div>

          <p className="text-[11px] leading-snug text-ink-500">
            {t('hadith.graph.transmission_hint')}
          </p>
        </Panel>
      </ReactFlow>

      {/* Внутренняя панель — только если выбором владеет сам граф (не controlled). */}
      {!selectionControlled && selected && (
        <NarratorPanel data={selected} onClose={() => setSelected(null)} />
      )}
    </div>
  );
}

export default SanadGraph;
