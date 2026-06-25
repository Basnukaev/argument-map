import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import {
  ReactFlow,
  Background,
  BackgroundVariant,
  Controls,
  Panel,
  MarkerType,
  getNodesBounds,
  getViewportForBounds,
  type Edge,
  type ReactFlowInstance,
} from '@xyflow/react';
import { BookOpen, ImageDown, Loader2, Maximize, Minimize, Network, GitBranch, PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import { useT, type DictKey } from '@/shared/i18n';
import { useThemeStore } from '@/shared/stores/themeStore';
import { exportGraphAsPngHighRes } from '@/shared/utils/graphExport';
import { toast } from '@/shared/stores/toastStore';
import SanadGraphNode, { type SanadNode } from './SanadGraphNode';
import SanadCustomEdge from './SanadCustomEdge';
import NarratorPanel from './NarratorPanel';
import { layoutSanad, NODE_WIDTH, NODE_HEIGHT } from '@/apps/hadith/utils/sanadLayout';
import { applySanadElkLayout } from '@/apps/hadith/utils/sanadElkLayout';
import { edgeStroke, RELIABILITY_TOKENS } from '@/apps/hadith/sanadTokens';
import type {
  NarratorData,
  ReliabilityGrade,
  SanadFlowNodeData,
  SanadGraphNodeData,
  SanadGraphResponse,
  SanadSummaryDto,
  TransmitterRole,
} from '@/apps/hadith/types';

/**
 * FB-7: полная цепочка через узел — обход ВВЕРХ (к корню/Пророку) И ВНИЗ
 * (к сборникам) от любого узла. Возвращает все узлы/рёбра путей, проходящих
 * через `nodeId` (node-hover подсветка «от а до я», а не только текущие 2 узла).
 */
function collectChainThroughNode(
  nodeId: string,
  edges: Edge[],
): { nodeIds: Set<string>; edgeIds: Set<string> } {
  const nodeIds = new Set<string>([nodeId]);
  const edgeIds = new Set<string>();
  const byTarget = new Map<string, Edge[]>();
  const bySource = new Map<string, Edge[]>();
  for (const e of edges) {
    const t = byTarget.get(e.target) ?? [];
    t.push(e);
    byTarget.set(e.target, t);
    const s = bySource.get(e.source) ?? [];
    s.push(e);
    bySource.set(e.source, s);
  }
  // вверх: target→source (к корню)
  const up: string[] = [nodeId];
  while (up.length > 0) {
    const cur = up.shift()!;
    for (const e of byTarget.get(cur) ?? []) {
      edgeIds.add(e.id);
      if (!nodeIds.has(e.source)) {
        nodeIds.add(e.source);
        up.push(e.source);
      }
    }
  }
  // вниз: source→target (к сборникам)
  const down: string[] = [nodeId];
  while (down.length > 0) {
    const cur = down.shift()!;
    for (const e of bySource.get(cur) ?? []) {
      edgeIds.add(e.id);
      if (!nodeIds.has(e.target)) {
        nodeIds.add(e.target);
        down.push(e.target);
      }
    }
  }
  return { nodeIds, edgeIds };
}

/**
 * Цепочка, проходящая через РЕБРО source→target: вверх от source (к корню)
 * и вниз от target (к сборникам), плюс само ребро. В отличие от
 * collectChainThroughNode НЕ спускается к «сёстрам» source (другим его
 * детям) — поэтому клик по ветке у madar'а подсвечивает только ЭТУ цепь,
 * а не весь веер (Абдула С64: клик по стрелке выделял все стрелки).
 */
function collectChainThroughEdge(
  source: string,
  target: string,
  edgeId: string,
  edges: Edge[],
): { nodeIds: Set<string>; edgeIds: Set<string> } {
  const nodeIds = new Set<string>([source, target]);
  const edgeIds = new Set<string>([edgeId]);
  const byTarget = new Map<string, Edge[]>();
  const bySource = new Map<string, Edge[]>();
  for (const e of edges) {
    const t = byTarget.get(e.target) ?? [];
    t.push(e);
    byTarget.set(e.target, t);
    const s = bySource.get(e.source) ?? [];
    s.push(e);
    bySource.set(e.source, s);
  }
  // вверх от source (к корню/Пророку)
  const up: string[] = [source];
  while (up.length > 0) {
    const cur = up.shift()!;
    for (const e of byTarget.get(cur) ?? []) {
      edgeIds.add(e.id);
      if (!nodeIds.has(e.source)) {
        nodeIds.add(e.source);
        up.push(e.source);
      }
    }
  }
  // вниз от target (к сборникам)
  const down: string[] = [target];
  while (down.length > 0) {
    const cur = down.shift()!;
    for (const e of bySource.get(cur) ?? []) {
      edgeIds.add(e.id);
      if (!nodeIds.has(e.target)) {
        nodeIds.add(e.target);
        down.push(e.target);
      }
    }
  }
  return { nodeIds, edgeIds };
}

/**
 * Обходит граф в обратном направлении (от конечного узла к корню) и
 * возвращает множества id узлов и рёбер, принадлежащих пути этого санада.
 * Используется для подсветки цепи по клику в легенде.
 */
function collectSanadPath(
  collectorNodeId: string,
  edges: Edge[],
): { nodeIds: Set<string>; edgeIds: Set<string> } {
  const nodeIds = new Set<string>();
  const edgeIds = new Set<string>();

  // Строим индекс target → список рёбер (для обхода вверх по цепи).
  const byTarget = new Map<string, Edge[]>();
  for (const e of edges) {
    const list = byTarget.get(e.target) ?? [];
    list.push(e);
    byTarget.set(e.target, list);
  }

  // BFS вверх: начинаем с collectorNodeId, идём к корню через target→source.
  const queue: string[] = [collectorNodeId];
  nodeIds.add(collectorNodeId);
  while (queue.length > 0) {
    const current = queue.shift()!;
    const incoming = byTarget.get(current) ?? [];
    for (const e of incoming) {
      edgeIds.add(e.id);
      if (!nodeIds.has(e.source)) {
        nodeIds.add(e.source);
        queue.push(e.source);
      }
    }
  }

  return { nodeIds, edgeIds };
}

// Стабильная ссылка между рендерами (иначе React Flow предупреждает и
// пересоздаёт типы узлов/рёбер на каждый render).
const nodeTypes = { sanad: SanadGraphNode };
const edgeTypes = { sanad: SanadCustomEdge };

/**
 * Что подсвечено по клику: либо узел (вся цепь через него), либо ребро
 * (только цепь, проходящая ИМЕННО через это ребро — без сестринских веток).
 */
type HighlightSeed =
  | { kind: 'node'; nodeId: string }
  | { kind: 'edge'; source: string; target: string; edgeId: string };

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
   * Controlled-режим выбора: страница владеет selected-state (единая карточка
   * для двойного клика по графу И клика по тексту иснада). При двойном клике по
   * узлу графа вызывается этот колбэк; карточку (`selectedNarrator`) рендерит
   * САМ граф внутри своего контейнера — чтобы она была видна в полноэкранном
   * режиме (С64: раньше родитель рендерил панель снаружи fullscreen-элемента).
   */
  onNarratorSelect?: (data: SanadFlowNodeData) => void;
  /**
   * Controlled-режим: содержимое карточки передатчика (родитель владеет
   * состоянием — клик из текста ИЛИ двойной клик из графа). Рендерится внутри
   * контейнера графа. Активен только вместе с `onNarratorSelect`.
   */
  selectedNarrator?: SanadFlowNodeData | null;
  /** Форма имени из текста иснада (muted-строка «في الإسناد») — controlled. */
  selectedTextForm?: string;
  /** Закрыть карточку — controlled (родитель сбрасывает selectedNarrator). */
  onNarratorClose?: () => void;
  /**
   * hadithId текущей страницы — для version-узлов: совпадение с
   * version.hadithId помечает узел «вы здесь» (не-кликабелен). Двойной клик по
   * чужому version-узлу навигирует на detail той передачи.
   */
  currentHadithId?: string;
  /**
   * Роль пользователя — гейт ADMIN inline-правки полей рави в NarratorPanel
   * (курация Фаза 5.b) + индикатор «отредактировано» на узле. undefined =
   * аноним (правка скрыта). Только в controlled-режиме выбора (panel наверху).
   */
  role?: string;
  /**
   * Курация Фаза 5.b — рефетч графа после ADMIN-правки в графе: поля рави в
   * панели ИЛИ формулы передачи звена на чипе ребра. Граф пересобирается с
   * EFFECTIVE-значениями (узлы/рёбра отражают правку). Родитель владеет fetch'ем
   * графа (lifted), потому рефетч — его колбэк.
   */
  onGraphEdited?: () => void;
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
  selectedNarrator,
  selectedTextForm,
  onNarratorClose,
  currentHadithId,
  role,
  onGraphEdited,
}: SanadGraphProps) {
  const t = useT();
  const navigate = useNavigate();
  const { effectiveTheme } = useThemeStore();
  // Controlled-режим данных определяется по присутствию пропа `graph` (даже `null`).
  const controlled = graphProp !== undefined;
  // Controlled-режим выбора: родитель владеет панелью передатчика.
  const selectionControlled = onNarratorSelect !== undefined;
  const [fetchedGraph, setFetchedGraph] = useState<SanadGraphResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<SanadFlowNodeData | null>(null);
  // FB-7/С64 граф: одиночный клик по узлу/ребру → подсветить цепь. Узел →
  // вся цепь через него; ребро → только цепь, проходящая через ЭТО ребро (без
  // сестринских веток madar'а). Снимается кликом по пустому полю; клик по
  // другому элементу переключает подсветку.
  const [highlight, setHighlight] = useState<HighlightSeed | null>(null);
  // id активного (подсвеченного) санада из легенды; null = нет подсветки.
  const [activeSanadId, setActiveSanadId] = useState<string | null>(null);
  // Панель легенды: свёрнута по умолчанию (развёрнутая перекрывает левые узлы
  // графа — на них нельзя кликнуть/навести); разворачивается кнопкой.
  const [legendCollapsed, setLegendCollapsed] = useState(true);

  // Fullscreen: ref на корневой div + state для иконки переключения.
  const containerRef = useRef<HTMLDivElement>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);

  // React Flow instance (через onInit) — нужен для PNG-экспорта: считать
  // bounds всех узлов и трансформ, чтобы захватить ПОЛНЫЙ граф (не вьюпорт).
  const rfInstanceRef = useRef<ReactFlowInstance<SanadNode, Edge> | null>(null);
  const [exporting, setExporting] = useState(false);

  // В controlled-режиме источник данных — проп; иначе результат внутреннего fetch.
  const graph = controlled ? graphProp : fetchedGraph;

  useEffect(() => {
    function onFsChange() {
      setIsFullscreen(!!document.fullscreenElement);
    }
    document.addEventListener('fullscreenchange', onFsChange);
    return () => document.removeEventListener('fullscreenchange', onFsChange);
  }, []);

  const handleFullscreen = useCallback(() => {
    if (!document.fullscreenElement) {
      containerRef.current?.requestFullscreen().catch(() => {
        // requestFullscreen отклонён (напр. iframe без allow="fullscreen")
      });
    } else {
      document.exitFullscreen().catch(() => {});
    }
  }, []);

  // PNG-экспорт всего графа в высоком разрешении (запрос: «PNG в неограниченном
  // разрешении»). Считаем bounds всех узлов + трансформ, который вписывает их
  // в кадр размера = реальный размер графа; pixelRatio 2 даёт ретина-плотность
  // без апскейл-мыла. Захват по bounds, НЕ по видимой части вьюпорта.
  const handleExportPng = useCallback(async () => {
    const instance = rfInstanceRef.current;
    if (!instance) return;
    const nodes = instance.getNodes();
    if (nodes.length === 0) return;

    // `.react-flow__viewport` содержит все узлы + рёбра (RF не виртуализирует),
    // именно к нему применяется transform; html-to-image кадрирует по width/height.
    const viewport = containerRef.current?.querySelector(
      '.react-flow__viewport',
    ) as HTMLElement | null;
    if (!viewport) {
      toast.error(t('graph.export.error'));
      return;
    }

    // Padding в CSS-px вокруг графа, чтобы крайние узлы/тени не обрезались.
    const padding = 24;
    // getNodesBounds читает measured-размеры узла; на момент экспорта measured
    // бывает пуст (узлы домеряются асинхронно) → ширина одиночной колонки
    // схлопывалась к 0 и PNG кропал карточки по горизонтали. Подставляем
    // известные размеры карточки иснада (как в sanadLayout) с приоритетом
    // реального measured, если он уже есть.
    const sizedNodes = nodes.map((n) => {
      const width = n.measured?.width || n.width || NODE_WIDTH;
      const height = n.measured?.height || n.height || NODE_HEIGHT;
      return { ...n, width, height, measured: { width, height } };
    });
    const bounds = getNodesBounds(sizedNodes);
    const imageWidth = Math.ceil(bounds.width) + padding * 2;
    const imageHeight = Math.ceil(bounds.height) + padding * 2;
    // zoom=1 (полный размер, без даунскейла) — высокое разрешение даёт pixelRatio.
    const transform = getViewportForBounds(bounds, imageWidth, imageHeight, 1, 1, padding);
    const filename = `isnad-${hadithId ?? currentHadithId ?? graph?.hadithId ?? 'graph'}.png`;

    setExporting(true);
    try {
      await exportGraphAsPngHighRes(viewport, filename, {
        bounds,
        transform,
        imageWidth,
        imageHeight,
        pixelRatio: 2,
      });
      toast.success(t('graph.export.success').replace('{filename}', filename));
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      toast.error(`${t('graph.export.error')}: ${msg}`);
    } finally {
      setExporting(false);
    }
  }, [hadithId, currentHadithId, graph, t]);

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

  // Построение RF-узлов/рёбер + dagre-раскладка (мгновенный fallback, ниже
  // подменяется ELK по готовности). memo по graph: маппинг + dagre дорогие,
  // RF перерисовывается часто при pan/zoom (реальная перф-проблема, не превентивный memo).
  const { rfNodes: dagreNodes, rfEdges: dagreEdges } = useMemo(() => {
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
        type: 'sanad',
        // Подпись-формула живёт в data: у кастомного ребра RF-встроенный label
        // не работает — SanadCustomEdge рендерит её сам (+ bend-points из ELK).
        // Курация Фаза 5.b: hadithId/position/role/onGraphEdited прокидываем для
        // ADMIN inline-правки формулы передачи звена прямо на чипе ребра.
        data: {
          transmissionPhrase: e.data.transmissionPhrase ?? undefined,
          hadithId: graph.hadithId,
          position: e.data.position,
          overridden: e.data.transmissionPhraseOverridden ?? false,
          role,
          onGraphEdited,
        },
        // Единая толщина: толщина значит ТОЛЬКО подсветку (+1 в highlight-memo),
        // а не primaryChain — в turuq все цепи primary, разнотолщинность читалась
        // как «почему это ребро жирное?» (С64).
        style: { stroke, strokeWidth: 1.8 },
        markerEnd: { type: MarkerType.ArrowClosed, color: stroke, width: 16, height: 16 },
      };
    });
    return { rfNodes: layoutSanad(nodes, edges), rfEdges: edges };
  }, [graph, currentHadithId, role, onGraphEdited]);

  // ELK ортогональная раскладка (async): рёбра огибают карточки (Проблема 1),
  // параллельные рёбра разводятся (Проблема 2), подписи-формулы садятся на
  // середину сегмента, не на узел (Проблема 3). dagre выше — мгновенный
  // fallback; ELK подменяет позиции + bend-points по готовности.
  const [elkLayout, setElkLayout] = useState<{ nodes: SanadNode[]; edges: Edge[] } | null>(null);
  useEffect(() => {
    // Сброс на смену графа: не показывать ELK-позиции прошлого графа, пока
    // считаем новый (eslint-disable — тот же приём, что в fetch-эффекте выше).
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setElkLayout(null);
    if (dagreNodes.length === 0) return;
    let cancelled = false;
    applySanadElkLayout(dagreNodes, dagreEdges)
      .then((res) => {
        if (!cancelled) setElkLayout(res);
      })
      .catch(() => {
        // ELK упал — остаёмся на dagre fallback (граф читаем, просто без ortho).
      });
    return () => {
      cancelled = true;
    };
  }, [dagreNodes, dagreEdges]);

  // ELK по готовности, иначе dagre. Кормят highlight-memo ниже.
  const baseNodes = elkLayout?.nodes ?? dagreNodes;
  const baseEdges = elkLayout?.edges ?? dagreEdges;

  // Двойной клик по узлу → карточка передатчика. React Flow onNodeDoubleClick
  // на наших кастомных узлах не срабатывает, а onNodeClick на dblclick зовётся
  // лишь раз (второй клик RF проглатывает) — поэтому слушаем НАТИВНЫЙ dblclick
  // на контейнере и находим узел по data-id (С64).
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    function onDblClick(e: MouseEvent) {
      // DOM-таргет dblclick — `.react-flow__pane` (overlay поверх узлов), а слой
      // узлов pointer-events:none → hit-тест по DOM не работает. Переводим курсор
      // в координаты графа (screenToFlowPosition учитывает pan/zoom) и ищем узел,
      // в чьи границы попали.
      const inst = rfInstanceRef.current;
      if (!inst) return;
      const p = inst.screenToFlowPosition({ x: e.clientX, y: e.clientY });
      const node = baseNodes.find((n) => {
        const w = n.measured?.width ?? NODE_WIDTH;
        const h = n.measured?.height ?? NODE_HEIGHT;
        return (
          p.x >= n.position.x && p.x <= n.position.x + w &&
          p.y >= n.position.y && p.y <= n.position.y + h
        );
      });
      const d = node?.data;
      if (!d || d.role === 'PROPHET') return;
      // Version-узел: навигация на detail той передачи (чужой узел; свой
      // «вы здесь» — не-кликабелен).
      if (d.role === 'VERSION') {
        if (!d.isCurrent) navigate(`/hadith/hadiths/${d.hadithId}`);
        return;
      }
      // Передатчик → карточка биографии. Controlled: наверх (родитель кладёт в
      // selectedNarrator, карточку рендерит сам граф — видна в fullscreen);
      // self-fetch: внутренняя карточка.
      if (selectionControlled && onNarratorSelect) onNarratorSelect(d);
      else setSelected(d);
    }
    // Capture-фаза: React Flow глушит dblclick на узле в bubble-фазе, поэтому
    // ловим сверху вниз ДО него.
    container.addEventListener('dblclick', onDblClick, true);
    return () => container.removeEventListener('dblclick', onDblClick, true);
  }, [baseNodes, navigate, selectionControlled, onNarratorSelect]);

  // Подсветка пути активного санада. Отдельный memo: пересчитывается только
  // при смене активного санада, не при pan/zoom (baseNodes/baseEdges стабильны).
  const { rfNodes, rfEdges } = useMemo(() => {
    // Приоритет: клик-подсветка (узел=вся цепь через него; ребро=только цепь
    // через ЭТО ребро) > активная цепь (легенда) > база.
    if (highlight) {
      const { nodeIds, edgeIds } =
        highlight.kind === 'node'
          ? collectChainThroughNode(highlight.nodeId, baseEdges)
          : collectChainThroughEdge(highlight.source, highlight.target, highlight.edgeId, baseEdges);
      const nodes = baseNodes.map((n) => ({
        ...n,
        style: nodeIds.has(n.id)
          ? { opacity: 1 }
          : { opacity: 0.18, filter: 'grayscale(0.6)' },
      }));
      const edges = baseEdges.map((e) =>
        edgeIds.has(e.id)
          ? {
              ...e,
              data: { ...e.data, dimmed: false },
              style: {
                ...e.style,
                opacity: 1,
                strokeWidth: Number(e.style?.strokeWidth ?? 1.6) + 1,
              },
            }
          : {
              // Чужие рёбра И их подписи приглушаем — цепочка читается чисто.
              ...e,
              data: { ...e.data, dimmed: true },
              style: { ...e.style, opacity: 0.12 },
            },
      );
      return { rfNodes: nodes, rfEdges: edges };
    }
    if (!activeSanadId || !graph) {
      return { rfNodes: baseNodes, rfEdges: baseEdges };
    }
    const activeSanad = graph.sanads.find((s: SanadSummaryDto) => s.id === activeSanadId);
    if (!activeSanad?.collectorNodeId) {
      return { rfNodes: baseNodes, rfEdges: baseEdges };
    }
    const { nodeIds, edgeIds } = collectSanadPath(activeSanad.collectorNodeId, baseEdges);

    const highlightedNodes = baseNodes.map((n) => ({
      ...n,
      style: nodeIds.has(n.id)
        ? { opacity: 1 }
        : { opacity: 0.2, filter: 'grayscale(0.6)' },
    }));
    const highlightedEdges = baseEdges.map((e) => {
      if (!edgeIds.has(e.id)) {
        return { ...e, data: { ...e.data, dimmed: true }, style: { ...e.style, opacity: 0.15 } };
      }
      // Активное ребро — подчёркиваем шириной.
      return { ...e, data: { ...e.data, dimmed: false }, style: { ...e.style, opacity: 1, strokeWidth: Number(e.style?.strokeWidth ?? 1.6) + 1 } };
    });
    return { rfNodes: highlightedNodes, rfEdges: highlightedEdges };
  }, [highlight, activeSanadId, graph, baseNodes, baseEdges]);

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

  // Такхридж/вариант: граф есть, но 0 рави И 0 version-узлов
  // (только сборник/пророк — пустая структурная оболочка).
  // Корпус-wide ~996 хадисов (~3%) — это VARIANT без собственной цепи передачи.
  // VERSION-узлы (параллельные передачи) — реальный контент, граф рендерится.
  // Граф КОРРЕКТЕН, данные не сломаны; показываем информативный empty-state.
  const hasRawi = graph.nodes.some(
    (n) => n.role === 'NARRATOR' || n.role === 'COMPANION' || n.role === 'VERSION',
  );
  if (!hasRawi) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 px-6 text-center">
        <GitBranch size={28} className="text-ink-300" aria-hidden />
        <p className="max-w-sm text-sm leading-relaxed text-ink-500">
          {t('hadith.sanad.empty_takhrij')}
        </p>
      </div>
    );
  }

  // Ярлык «основная» информативен лишь когда основная цепь ровно одна (режим
  // одного хадиса). В merged-turuq все цепи помечены primary → ярлык не несёт
  // информации и его скрываем (легенда подписывает цепи сборником).
  const primaryBadgeMeaningful =
    graph.sanads.filter((s) => s.primaryChain).length === 1;

  return (
    <div
      ref={containerRef}
      data-theme={effectiveTheme}
      className="relative h-full w-full bg-app [&:fullscreen]:h-screen [&:fullscreen]:w-screen"
    >
      <ReactFlow<SanadNode, Edge>
        nodes={rfNodes}
        edges={rfEdges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onInit={(instance) => {
          rfInstanceRef.current = instance;
        }}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        minZoom={0.1}
        maxZoom={4}
        nodesDraggable={false}
        nodesConnectable={false}
        edgesFocusable={false}
        elementsSelectable
        // Двойной клик = открыть карточку передатчика, поэтому zoom-on-dblclick
        // отключён (иначе зум сдвигает узел между кликами и onNodeDoubleClick
        // не срабатывает на узле, С64).
        zoomOnDoubleClick={false}
        proOptions={{ hideAttribution: true }}
        onNodeClick={(_, node) => {
          // Одиночный клик = подсветить цепь через узел (карточка — по двойному,
          // нативный dblclick-эффект ниже). Пророк — корень: подсветка = весь
          // граф, поэтому пропускаем.
          if (node.data.role === 'PROPHET') return;
          setHighlight({ kind: 'node', nodeId: node.id });
        }}
        onEdgeClick={(_, edge) =>
          setHighlight({ kind: 'edge', source: edge.source, target: edge.target, edgeId: edge.id })
        }
        onPaneClick={() => {
          // Клик по пустому полю снимает подсветку цепи + закрывает карточку.
          setHighlight(null);
          if (selectionControlled) onNarratorClose?.();
          else setSelected(null);
        }}
      >
        <Background variant={BackgroundVariant.Dots} gap={20} size={1} />
        <Controls showInteractive={false} />

        {/* Кнопки графа — top-right, graph-chrome исключён из RTL-logical
            (граф не зеркалится, физические классы допустимы, см.
            frontend/CLAUDE.md): PNG-экспорт + полноэкранный режим. */}
        <Panel position="top-right" className="flex items-center gap-1">
          <button
            type="button"
            aria-label={t('hadith.graph.export_png')}
            title={t('hadith.graph.export_png')}
            onClick={() => void handleExportPng()}
            disabled={exporting}
            className="inline-flex h-8 w-8 items-center justify-center rounded-[8px] border border-bd bg-card shadow-sm text-meta transition-colors hover:bg-hover hover:text-strong focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {exporting ? (
              <Loader2 size={15} className="animate-spin" aria-hidden />
            ) : (
              <ImageDown size={15} aria-hidden />
            )}
          </button>
          <button
            type="button"
            aria-label={isFullscreen ? t('graph.fullscreen_exit') : t('graph.fullscreen_enter')}
            onClick={handleFullscreen}
            className="inline-flex h-8 w-8 items-center justify-center rounded-[8px] border border-bd bg-card shadow-sm text-meta transition-colors hover:bg-hover hover:text-strong focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
          >
            {isFullscreen ? <Minimize size={15} aria-hidden /> : <Maximize size={15} aria-hidden />}
          </button>
        </Panel>

        {/* Легенда: свёрнутая версия — иконка-кнопка для разворота. */}
        {legendCollapsed ? (
          <Panel position="top-left">
            <button
              type="button"
              aria-label={t('hadith.graph.legend_show')}
              onClick={() => setLegendCollapsed(false)}
              className="inline-flex h-8 w-8 items-center justify-center rounded-[8px] border border-bd bg-card shadow-sm text-meta transition-colors hover:bg-hover hover:text-strong focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
            >
              <PanelLeftOpen size={15} aria-hidden />
            </button>
          </Panel>
        ) : (
          <Panel position="top-left">
            {/* Весь блок легенды ограничен по высоте + scroll, чтобы не вылезать
                за нижний край вьюпорта (C13 fix). max-h: ~80vh минус отступы. */}
            <div className="flex max-h-[calc(80vh-2rem)] max-w-[260px] flex-col overflow-y-auto rounded-md border border-border-strong bg-elevated/95 p-3 text-xs shadow-sh1 backdrop-blur">
              {/* Заголовок с кнопкой сворачивания */}
              <div className="mb-2 flex items-center justify-between gap-2">
                <span className="font-semibold text-ink-700">{t('hadith.graph.legend_chains')}</span>
                <button
                  type="button"
                  aria-label={t('hadith.graph.legend_hide')}
                  onClick={() => setLegendCollapsed(true)}
                  className="inline-flex h-5 w-5 shrink-0 items-center justify-center rounded text-ink-400 transition-colors hover:bg-ink-100 hover:text-ink-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
                >
                  <PanelLeftClose size={13} aria-hidden />
                </button>
              </div>

              {/* Список цепей — без отдельного max-h: весь блок уже ограничен. */}
              <ul className="mb-2 space-y-0.5">
                {graph.sanads.map((s) => {
                  const isActive = activeSanadId === s.id;
                  return (
                    <li key={s.id}>
                      <button
                        type="button"
                        onClick={() => setActiveSanadId(isActive ? null : s.id)}
                        className={`flex w-full items-center gap-2 rounded px-1 py-0.5 text-start transition-colors ${
                          isActive
                            ? 'bg-accent-50 ring-1 ring-accent-300'
                            : 'hover:bg-ink-50'
                        }`}
                        dir="auto"
                        title={isActive ? t('hadith.graph.chain_deselect') : t('hadith.graph.chain_select')}
                      >
                        <span
                          className="inline-block h-1.5 w-4 shrink-0 rounded-full"
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
                      </button>
                    </li>
                  );
                })}
              </ul>

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

              {/* Управление графом — что делают клик/двойной клик (С64). */}
              <div className="mt-2 border-t border-border pt-2">
                <div className="mb-1 font-semibold text-ink-700">
                  {t('hadith.graph.controls_title')}
                </div>
                <ul className="space-y-0.5 text-[11px] leading-snug text-ink-500">
                  <li>{t('hadith.graph.controls_click')}</li>
                  <li>{t('hadith.graph.controls_dblclick')}</li>
                  <li>{t('hadith.graph.controls_pane')}</li>
                </ul>
              </div>
            </div>
          </Panel>
        )}
      </ReactFlow>

      {/* Карточка передатчика — ВСЕГДА внутри контейнера графа (видна в
          fullscreen, С64). Controlled: данные из родителя (selectedNarrator);
          self-fetch: внутренний selected. */}
      {selectionControlled
        ? selectedNarrator && (
            <NarratorPanel
              data={selectedNarrator}
              textForm={selectedTextForm}
              onClose={() => onNarratorClose?.()}
              role={role}
              onEdited={onGraphEdited}
            />
          )
        : selected && (
            <NarratorPanel
              data={selected}
              onClose={() => setSelected(null)}
              role={role}
              onEdited={onGraphEdited}
            />
          )}
    </div>
  );
}

export default SanadGraph;
