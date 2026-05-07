// Static graph renderer — positions are absolute, edges are SVG bezier curves drawn behind cards.
// Used in the main "Граф темы" mockup. Mawlid permissibility demo.

const GRAPH_W = 1440;
const GRAPH_H = 820;

// Node positions (top-left of card). Card is 280x ~120 (auto height).
const DEMO_NODES = [
  { id: "q-root",   type: "QUESTION", status: "UNVERIFIED", x: 580,  y: 30,   title: "Дозволено ли празднование мавлида ан-Наби?",
    body: "Корневой вопрос темы. Допустима ли практика отмечания дня рождения Пророка Мухаммада ﷺ?", h: 110 },

  { id: "c-main",   type: "CLAIM",    status: "DISPUTED",   x: 580,  y: 200,  title: "Мавлид является дозволенной практикой",
    body: "Главный тезис обсуждения. Поддерживается частью учёных как форма выражения любви к Пророку.", h: 110 },

  { id: "a-love",   type: "ARGUMENT", status: "STANDING",   x: 230,  y: 380,  title: "Это выражение любви к Пророку ﷺ",
    body: "Любовь к Пророку — часть веры. Радость о его рождении — естественная её манифестация.", h: 110 },

  { id: "a-bidah",  type: "ARGUMENT", status: "REFUTED",    x: 940,  y: 380,  title: "Это новшество, не известное саляфам",
    body: "Праздник не отмечался ни Пророком, ни сподвижниками, ни первыми тремя поколениями.", h: 110 },

  { id: "a-monday", type: "ARGUMENT", status: "STANDING",   x: 580,  y: 380,  title: "Пророк ﷺ постился по понедельникам",
    body: "Поясняя пост, упомянул что родился в этот день — то есть выделял его особым образом.", h: 110 },

  { id: "e-monday", type: "EVIDENCE", status: "STANDING",   x: 580,  y: 555,  title: "Сахих Муслим, хадис №1162",
    body: "«В этот день я был рождён, и в этот день мне было ниспослано откровение».", h: 110 },

  { id: "e-bidah",  type: "EVIDENCE", status: "REFUTED",    x: 940,  y: 555,  title: "Хадис «Каждое новшество — заблуждение»",
    body: "Сунан Абу Дауд №4607. Цитируется в опровержение, но контекст спорный.", h: 110 },

  { id: "e-love",   type: "EVIDENCE", status: "STANDING",   x: 230,  y: 555,  title: "Ибн Касир, «Аль-Бидая ва-н-нихая»",
    body: "Историк упоминает раннее распространение мавлида в Ирбиле без порицания современников.", h: 110 },

  { id: "q-scope",  type: "QUESTION", status: "UNVERIFIED", x: 940,  y: 730,  title: "Какие именно практики имеются в виду?",
    body: "Уточняющий вопрос: чтение жизнеописания vs смешанные собрания vs религ. процессии.", h: 90 },

  { id: "c-scope",  type: "CLAIM",    status: "STANDING",   x: 230,  y: 730,  title: "Допустимо чтение сиры в этот день",
    body: "Узкий тезис: ограниченная форма — без излишеств — не вызывает разногласий.", h: 90 },
];

// Edges: { from, to, type, label?, fromHandle?, toHandle?, labelOffset? }
const DEMO_EDGES = [
  { from: "c-main",   to: "q-root",   type: "RESPONDS_TO", label: "отвечает на" },
  { from: "a-love",   to: "c-main",   type: "SUPPORTS",    label: "поддерживает" },
  { from: "a-monday", to: "c-main",   type: "SUPPORTS",    label: "доказывает" },
  { from: "a-bidah",  to: "c-main",   type: "REFUTES",     label: "опровергает" },
  { from: "e-monday", to: "a-monday", type: "SUPPORTS",    label: "источник" },
  { from: "e-love",   to: "a-love",   type: "SUPPORTS",    label: "согласуется с" },
  { from: "e-bidah",  to: "a-bidah",  type: "SUPPORTS",    label: "цитируется" },
  { from: "c-scope",  to: "a-bidah",  type: "INVALIDATES", label: "аннулирует" },
  { from: "q-scope",  to: "a-bidah",  type: "QUALIFIES",   label: "уточняет" },
  { from: "c-scope",  to: "q-scope",  type: "RESPONDS_TO", label: "отвечает на" },
];

function nodeAnchor(node, side) {
  const w = 280;
  const h = node.h || 120;
  switch (side) {
    case "top":    return { x: node.x + w / 2, y: node.y };
    case "bottom": return { x: node.x + w / 2, y: node.y + h };
    case "left":   return { x: node.x,         y: node.y + h / 2 };
    case "right":  return { x: node.x + w,     y: node.y + h / 2 };
    default:       return { x: node.x + w / 2, y: node.y + h / 2 };
  }
}

function pickAnchors(from, to) {
  // pick nearest sides by direction.
  const fc = { x: from.x + 140, y: from.y + (from.h || 120) / 2 };
  const tc = { x: to.x + 140,   y: to.y + (to.h || 120) / 2 };
  const dx = tc.x - fc.x;
  const dy = tc.y - fc.y;
  let fSide, tSide;
  if (Math.abs(dy) > Math.abs(dx)) {
    fSide = dy > 0 ? "bottom" : "top";
    tSide = dy > 0 ? "top" : "bottom";
  } else {
    fSide = dx > 0 ? "right" : "left";
    tSide = dx > 0 ? "left" : "right";
  }
  return [nodeAnchor(from, fSide), nodeAnchor(to, tSide), fSide, tSide];
}

function bezierPath(p1, p2, fSide, tSide) {
  const tension = 70;
  const off = (side, p) => {
    switch (side) {
      case "top":    return { x: p.x, y: p.y - tension };
      case "bottom": return { x: p.x, y: p.y + tension };
      case "left":   return { x: p.x - tension, y: p.y };
      case "right":  return { x: p.x + tension, y: p.y };
    }
  };
  const c1 = off(fSide, p1);
  const c2 = off(tSide, p2);
  return `M ${p1.x} ${p1.y} C ${c1.x} ${c1.y}, ${c2.x} ${c2.y}, ${p2.x} ${p2.y}`;
}

function midPoint(p1, p2, fSide, tSide) {
  // approximate midpoint of bezier — use control-point average
  const tension = 70;
  const off = (side, p) => {
    switch (side) {
      case "top":    return { x: p.x, y: p.y - tension };
      case "bottom": return { x: p.x, y: p.y + tension };
      case "left":   return { x: p.x - tension, y: p.y };
      case "right":  return { x: p.x + tension, y: p.y };
    }
  };
  const c1 = off(fSide, p1);
  const c2 = off(tSide, p2);
  // t=0.5 cubic bezier
  const x = 0.125 * p1.x + 0.375 * c1.x + 0.375 * c2.x + 0.125 * p2.x;
  const y = 0.125 * p1.y + 0.375 * c1.y + 0.375 * c2.y + 0.125 * p2.y;
  return { x, y };
}

const EdgeBadge = ({ x, y, type, label, selected }) => {
  const t = EDGE_TYPE[type];
  const Icon = I[t.icon];
  return (
    <foreignObject x={x - 70} y={y - 13} width={140} height={28} style={{ overflow: "visible" }}>
      <div className="flex justify-center">
        <div className={cx(
          "inline-flex items-center gap-1 rounded-md border px-1.5 py-0.5 text-[10px] font-medium shadow-sm bg-white whitespace-nowrap",
          t.badgeBorder, t.badgeText,
          selected && "ring-2 ring-indigo-400 ring-offset-1",
        )}>
          <Icon size={10} />
          {label || t.label}
        </div>
      </div>
    </foreignObject>
  );
};

const Graph = ({ nodes = DEMO_NODES, edges = DEMO_EDGES, selectedNode, selectedEdge, showLabels = true, height = GRAPH_H, width = GRAPH_W }) => {
  const nodeMap = Object.fromEntries(nodes.map((n) => [n.id, n]));
  return (
    <div className="relative dot-grid bg-slate-50" style={{ width: "100%", height }}>
      <svg width="100%" height={height} viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="xMidYMid meet" className="absolute inset-0 pointer-events-none">
        <defs>
          {Object.values(EDGE_TYPE).map((t) => (
            <marker
              key={t.key}
              id={`arrow-${t.key}`}
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerWidth="7"
              markerHeight="7"
              orient="auto-start-reverse"
            >
              <path d="M 0 1 L 9 5 L 0 9 z" fill={t.color} opacity={t.opacity || 1} />
            </marker>
          ))}
        </defs>

        {edges.map((e, i) => {
          const from = nodeMap[e.from];
          const to = nodeMap[e.to];
          if (!from || !to) return null;
          const t = EDGE_TYPE[e.type];
          const [p1, p2, fSide, tSide] = pickAnchors(from, to);
          const d = bezierPath(p1, p2, fSide, tSide);
          const mid = midPoint(p1, p2, fSide, tSide);
          const isSelected = selectedEdge === i;
          return (
            <g key={i}>
              <path
                d={d}
                fill="none"
                stroke={t.color}
                strokeWidth={t.width}
                strokeOpacity={t.opacity || 1}
                strokeDasharray={t.style === "dashed" ? "6 5" : undefined}
                markerEnd={`url(#arrow-${t.key})`}
                strokeLinecap="round"
              />
              {showLabels && <EdgeBadge x={mid.x} y={mid.y} type={e.type} label={e.label} selected={isSelected} />}
            </g>
          );
        })}
      </svg>

      {/* Render nodes as positioned cards */}
      {nodes.map((n) => (
        <div
          key={n.id}
          className="absolute"
          style={{
            left: `${(n.x / width) * 100}%`,
            top: n.y,
            width: `${(280 / width) * 100}%`,
          }}
        >
          <NodeCard
            type={n.type}
            status={n.status}
            title={n.title}
            body={n.body}
            selected={selectedNode === n.id}
            width="100%"
          />
        </div>
      ))}
    </div>
  );
};

// MiniMap (corner)
const MiniMap = ({ nodes = DEMO_NODES, viewport }) => {
  const w = 220, h = 140;
  const padding = 30;
  const xs = nodes.map((n) => n.x);
  const ys = nodes.map((n) => n.y);
  const minX = Math.min(...xs) - 50;
  const maxX = Math.max(...xs) + 280 + 50;
  const minY = Math.min(...ys) - 30;
  const maxY = Math.max(...ys) + 130 + 30;
  const sx = (w - padding * 2) / (maxX - minX);
  const sy = (h - padding * 2) / (maxY - minY);
  const scale = Math.min(sx, sy);
  return (
    <div className="bg-white/95 backdrop-blur border border-slate-200 rounded-md shadow-md overflow-hidden" style={{ width: w, height: h }}>
      <div className="px-2 py-1 border-b border-slate-200 flex items-center justify-between bg-slate-50/80">
        <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">Карта графа</span>
        <I.Maximize size={11} className="text-slate-400" />
      </div>
      <svg width={w} height={h - 22} viewBox={`0 0 ${w} ${h - 22}`}>
        {nodes.map((n) => {
          const t = NODE_TYPE[n.type];
          const s = STATUS[n.status];
          const fill =
            n.status === "STANDING" ? "#10b981" :
            n.status === "DISPUTED" ? "#f59e0b" :
            n.status === "REFUTED"  ? "#ef4444" : "#cbd5e1";
          return (
            <rect
              key={n.id}
              x={(n.x - minX) * scale + padding}
              y={(n.y - minY) * scale + padding - 11}
              width={280 * scale}
              height={(n.h || 120) * scale}
              rx={2}
              fill={fill}
              fillOpacity={0.9}
              stroke="white"
              strokeWidth={0.5}
            />
          );
        })}
        {viewport && (
          <rect x={viewport.x} y={viewport.y} width={viewport.w} height={viewport.h} fill="rgba(99,102,241,0.10)" stroke="#6366f1" strokeWidth={1} />
        )}
      </svg>
    </div>
  );
};

window.Graph = Graph;
window.MiniMap = MiniMap;
window.DEMO_NODES = DEMO_NODES;
window.DEMO_EDGES = DEMO_EDGES;
