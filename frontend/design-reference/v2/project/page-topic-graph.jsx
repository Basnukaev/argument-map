// Redesigned TopicGraphPage — the central feature of the product.
//
// Layout: slim breadcrumb + toolbar header, full-bleed canvas with
// SVG-rendered edges and absolute-positioned node cards, right detail
// panel for the selected node, bottom-right minimap, floating "Add"
// action menu in bottom-left.

const TOPIC = {
  title: 'Дозволенность пятничного гусля для участвующих в джум‘а',
  description: 'Разбор позиций мазхабов: фард vs сильно желательно.',
};

const NODES = [
  // root
  { id: 'q1', type: 'QUESTION', status: 'STANDING',
    title: 'Является ли гусль пятницы фардом для участвующего в джум‘а?',
    x: 30, y: 280, w: 260, h: 80 },
  // claims
  { id: 'c1', type: 'CLAIM',    status: 'STANDING',
    title: 'Большинство: сильно желательно (сунна мустахабба).',
    x: 340, y: 110, w: 240, h: 84 },
  { id: 'c2', type: 'CLAIM',    status: 'DISPUTED', selected: true,
    title: 'Захириты и часть саляфов: обязательное (фард).',
    body: 'Опора: ٱلْغُسْلُ يَوْمَ ٱلْجُمُعَةِ وَاجِبٌ — буквально «обязанность».',
    x: 340, y: 270, w: 240, h: 96 },
  { id: 'c3', type: 'CLAIM',    status: 'UNVERIFIED',
    title: 'Только для тех, у кого запах тела или ритуальная нечистота.',
    x: 340, y: 440, w: 240, h: 84 },
  // arguments & evidence
  { id: 'a1', type: 'ARGUMENT', status: 'STANDING',
    title: '«Обязанность» толкуется как «крайне желательное», по аналогии с правом гостя.',
    x: 660, y: 60, w: 260, h: 84 },
  { id: 'e1', type: 'EVIDENCE', status: 'STANDING',
    title: 'Бухари 858, Муслим 846 (от Абу Са‘ида).',
    x: 660, y: 180, w: 260, h: 64 },
  { id: 'a2', type: 'ARGUMENT', status: 'DISPUTED',
    title: 'Буквальное прочтение «ваджиб» в речи Пророка ﷺ.',
    x: 660, y: 280, w: 260, h: 76 },
  { id: 'e2', type: 'EVIDENCE', status: 'REFUTED',
    title: 'Передача Ибн ‘Умара: «Кто пришёл на джум‘а — пусть совершит гусль».',
    x: 660, y: 380, w: 260, h: 76 },
  { id: 'a3', type: 'ARGUMENT', status: 'UNVERIFIED',
    title: 'Гипотеза: контекст — устранение запаха в тесной мечети.',
    x: 660, y: 480, w: 260, h: 76 },
];

const EDGES = [
  { from: 'c1', to: 'q1', kind: 'RESPONDS_TO' },
  { from: 'c2', to: 'q1', kind: 'RESPONDS_TO' },
  { from: 'c3', to: 'q1', kind: 'RESPONDS_TO' },
  { from: 'a1', to: 'c1', kind: 'SUPPORTS' },
  { from: 'e1', to: 'c1', kind: 'SUPPORTS' },
  { from: 'a2', to: 'c2', kind: 'SUPPORTS' },
  { from: 'e2', to: 'c2', kind: 'REFUTES' },
  { from: 'a3', to: 'c3', kind: 'SUPPORTS' },
];

const EDGE_TOKENS = {
  SUPPORTS:    { color: 'var(--c-ok-500)',     label: 'supports', dash: '0' },
  REFUTES:     { color: 'var(--c-err-500)',    label: 'refutes',  dash: '0' },
  INVALIDATES: { color: 'var(--c-err-700)',    label: 'invalidates', dash: '0' },
  QUALIFIES:   { color: 'var(--c-accent-500)', label: 'qualifies', dash: '4 3' },
  RESPONDS_TO: { color: 'var(--c-ink-400)',    label: 'responds', dash: '0' },
};

const NODE_TYPE_TOKENS = {
  QUESTION: { bg: 'var(--c-accent-50)', fg: 'var(--c-accent-700)', accent: 'var(--c-accent-600)', icon: 'help-circle', label: 'Вопрос' },
  CLAIM:    { bg: 'var(--c-bg-elevated)', fg: 'var(--c-ink-800)',  accent: 'var(--c-ink-700)',   icon: 'quote',       label: 'Тезис' },
  ARGUMENT: { bg: 'var(--c-warn-100)',  fg: 'var(--c-warn-700)',   accent: 'var(--c-warn-500)',  icon: 'sparkles',    label: 'Довод' },
  EVIDENCE: { bg: 'var(--c-ok-100)',    fg: 'var(--c-ok-700)',     accent: 'var(--c-ok-500)',    icon: 'file-text',   label: 'Свид.' },
};

const STATUS_BAR_TOKENS = {
  STANDING:   'var(--c-ok-500)',
  DISPUTED:   'var(--c-warn-500)',
  REFUTED:    'var(--c-err-500)',
  UNVERIFIED: 'var(--c-ink-300)',
};

function TopicGraphBoard() {
  return (
    <div style={{
      width: '100%', height: '100%',
      display: 'flex', flexDirection: 'column',
      background: 'var(--c-bg)',
      fontFamily: 'var(--font-ui)',
      color: 'var(--c-ink-900)',
      overflow: 'hidden',
    }}>
      <AppHeader currentPath="/topics" />

      {/* Topic-page header */}
      <div style={{
        flex: 'none',
        display: 'flex', alignItems: 'center', gap: 12,
        padding: '10px 24px',
        background: 'var(--c-ink-0)',
        borderBottom: 'var(--br-hair)',
        minHeight: 44,
      }}>
        <button className="btn btn-ghost btn-sm">
          <Icon name="arrow-left" size={13} />
          К списку
        </button>
        <span style={{ color: 'var(--c-ink-300)' }}>/</span>
        <h1 style={{
          margin: 0, fontSize: 14, fontWeight: 600, color: 'var(--c-ink-900)',
          fontFamily: 'var(--font-serif)', letterSpacing: '-0.005em',
        }}>{TOPIC.title}</h1>
        <span style={{ width: 1, height: 18, background: 'var(--c-ink-150)' }} />
        <span style={{ fontSize: 12, color: 'var(--c-ink-500)', flex: 1 }}>
          {TOPIC.description}
        </span>

        {/* Status legend */}
        <div style={{ display: 'flex', gap: 12, alignItems: 'center', fontSize: 11, color: 'var(--c-ink-500)' }}>
          {[
            ['STANDING', 'Устоявшийся', 'var(--c-ok-500)'],
            ['DISPUTED', 'Спорный', 'var(--c-warn-500)'],
            ['REFUTED', 'Опровергнут', 'var(--c-err-500)'],
          ].map(([k, label, c]) => (
            <span key={k} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
              <span style={{ width: 7, height: 7, borderRadius: '50%', background: c }} />
              {label}
            </span>
          ))}
        </div>
      </div>

      {/* Toolbar */}
      <div style={{
        flex: 'none',
        display: 'flex', alignItems: 'center', gap: 4,
        padding: '6px 16px',
        background: 'var(--c-bg)',
        borderBottom: 'var(--br-hair)',
      }}>
        <button className="btn btn-primary btn-sm">
          <Icon name="sparkles" size={13} /> Узел
          <span className="kbd">N</span>
        </button>
        <button className="btn btn-secondary btn-sm">
          <Icon name="graph" size={13} /> Связь
          <span className="kbd">E</span>
        </button>
        <span style={{ width: 1, height: 18, background: 'var(--c-ink-150)', margin: '0 4px' }} />
        <button className="btn btn-ghost btn-sm">
          <Icon name="columns" size={13} /> Авто-раскладка
        </button>
        <button className="btn btn-ghost btn-sm btn-icon" aria-label="undo"><Icon name="arrow-left" size={13} /></button>
        <span style={{ flex: 1 }} />
        <div style={{
          display: 'inline-flex', alignItems: 'center', gap: 8,
          padding: '0 8px',
          fontSize: 11, color: 'var(--c-ink-500)',
        }}>
          <span className="mono">{NODES.length} узлов · {EDGES.length} рёбер</span>
        </div>
        <span style={{ width: 1, height: 18, background: 'var(--c-ink-150)', margin: '0 4px' }} />
        <div style={{ display: 'flex', gap: 2 }}>
          <button className="btn btn-ghost btn-sm btn-icon" aria-label="filter"><Icon name="search" size={13} /></button>
          <button className="btn btn-ghost btn-sm btn-icon" aria-label="show labels"><Icon name="eye" size={13} /></button>
          <button className="btn btn-ghost btn-sm btn-icon" aria-label="export"><Icon name="file-text" size={13} /></button>
        </div>
      </div>

      {/* Body */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden', minHeight: 0 }}>
        {/* Canvas */}
        <GraphCanvas />

        {/* Right detail panel — for the currently-selected node */}
        <NodeDetailsPanel nodeId="c2" />
      </div>
    </div>
  );
}

function GraphCanvas() {
  // Center point of a node for edge endpoints
  const nodeCenters = Object.fromEntries(
    NODES.map((n) => [n.id, { x: n.x + n.w / 2, y: n.y + n.h / 2 }])
  );

  return (
    <div style={{
      flex: 1, position: 'relative',
      background: 'var(--c-bg)',
      overflow: 'hidden',
    }}>
      {/* dot grid */}
      <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }} aria-hidden="true">
        <defs>
          <pattern id="dotbg" width="24" height="24" patternUnits="userSpaceOnUse">
            <circle cx="1" cy="1" r="0.6" fill="var(--c-ink-200)" />
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#dotbg)" opacity="0.5" />
      </svg>

      {/* Edges layer */}
      <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', pointerEvents: 'none' }}>
        <defs>
          {Object.entries(EDGE_TOKENS).map(([k, t]) => (
            <marker key={k} id={`arr-${k}`} viewBox="0 0 10 10"
              refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill={t.color} />
            </marker>
          ))}
        </defs>
        {EDGES.map((e, i) => {
          const t = EDGE_TOKENS[e.kind];
          const from = nodeCenters[e.from];
          const to = nodeCenters[e.to];
          // Bezier from -> to with horizontal control points
          const cx1 = from.x + (to.x - from.x) * 0.45;
          const cx2 = from.x + (to.x - from.x) * 0.55;
          const midX = (from.x + to.x) / 2;
          const midY = (from.y + to.y) / 2;
          return (
            <g key={i}>
              <path
                d={`M ${from.x} ${from.y} C ${cx1} ${from.y}, ${cx2} ${to.y}, ${to.x} ${to.y}`}
                fill="none"
                stroke={t.color}
                strokeWidth={1.5}
                strokeDasharray={t.dash}
                opacity={0.85}
                markerEnd={`url(#arr-${e.kind})`}
              />
              <g transform={`translate(${midX} ${midY})`}>
                <rect x="-32" y="-9" width="64" height="18" rx="9"
                      fill="var(--c-bg-elevated)" stroke={t.color} strokeWidth="1" opacity="0.95" />
                <text x="0" y="3" textAnchor="middle"
                      fontSize="10" fontWeight="600" fill={t.color}
                      style={{ fontFamily: 'var(--font-mono)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                  {t.label}
                </text>
              </g>
            </g>
          );
        })}
      </svg>

      {/* Nodes layer */}
      {NODES.map((n) => <NodeCard key={n.id} node={n} />)}

      {/* Floating bottom-left: add menu hint */}
      <div style={{
        position: 'absolute', left: 16, bottom: 16,
        display: 'flex', flexDirection: 'column', gap: 6,
      }}>
        <div style={{
          background: 'var(--c-bg-elevated)',
          border: 'var(--br-hair)',
          borderRadius: 'var(--r-md)',
          boxShadow: 'var(--sh-2)',
          padding: 4, display: 'flex', gap: 2,
        }}>
          <button className="btn btn-ghost btn-sm btn-icon" aria-label="zoom in"><Icon name="maximize" size={13} /></button>
          <button className="btn btn-ghost btn-sm btn-icon" aria-label="zoom out"><Icon name="minimize" size={13} /></button>
          <div style={{ width: 1, background: 'var(--c-ink-150)', margin: '4px 2px' }} />
          <button className="btn btn-ghost btn-sm btn-icon" aria-label="fit"><Icon name="columns" size={13} /></button>
        </div>
      </div>

      {/* Floating bottom-right: minimap */}
      <Minimap />
    </div>
  );
}

function NodeCard({ node }) {
  const typeTok = NODE_TYPE_TOKENS[node.type];
  const statusColor = STATUS_BAR_TOKENS[node.status];
  return (
    <div style={{
      position: 'absolute',
      left: node.x, top: node.y,
      width: node.w, minHeight: node.h,
      background: 'var(--c-bg-elevated)',
      border: node.selected ? '1.5px solid var(--c-accent-600)' : 'var(--br-soft)',
      borderRadius: 'var(--r-md)',
      boxShadow: node.selected
        ? '0 0 0 4px color-mix(in srgb, var(--c-accent-600) 18%, transparent), var(--sh-3)'
        : 'var(--sh-1)',
      overflow: 'hidden',
      cursor: 'pointer',
      display: 'flex',
    }}>
      {/* status bar */}
      <div style={{
        width: 4, flex: 'none',
        background: statusColor,
      }} />
      <div style={{ flex: 1, padding: 10, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
          <span style={{
            display: 'inline-flex', alignItems: 'center', gap: 3,
            padding: '2px 6px',
            borderRadius: 3,
            background: typeTok.bg,
            color: typeTok.fg,
            fontSize: 10, fontWeight: 600,
            textTransform: 'uppercase', letterSpacing: '0.04em',
          }}>
            <Icon name={typeTok.icon} size={10} />
            {typeTok.label}
          </span>
          <span style={{ flex: 1 }} />
          <span style={{ fontSize: 9, color: 'var(--c-ink-400)', fontFamily: 'var(--font-mono)' }}>{node.id}</span>
        </div>
        <div dir="auto" style={{
          fontSize: 12.5,
          lineHeight: 1.4,
          color: 'var(--c-ink-900)',
          fontWeight: node.type === 'QUESTION' ? 600 : 500,
        }}>
          {node.title}
        </div>
        {node.body && (
          <div dir="auto" style={{
            marginTop: 6,
            fontSize: 11,
            lineHeight: 1.45,
            color: 'var(--c-ink-600)',
            fontFamily: /[\u0600-\u06FF]/.test(node.body) ? 'var(--font-arabic)' : undefined,
          }}>
            {node.body}
          </div>
        )}
      </div>
    </div>
  );
}

function Minimap() {
  // Compute bbox of all nodes
  const minX = Math.min(...NODES.map((n) => n.x));
  const minY = Math.min(...NODES.map((n) => n.y));
  const maxX = Math.max(...NODES.map((n) => n.x + n.w));
  const maxY = Math.max(...NODES.map((n) => n.y + n.h));
  const w = maxX - minX, h = maxY - minY;
  const SCALE = 0.12;
  const mw = w * SCALE, mh = h * SCALE;
  return (
    <div style={{
      position: 'absolute', right: 16, bottom: 16,
      width: mw + 16, height: mh + 16,
      background: 'var(--c-bg-elevated)',
      border: 'var(--br-soft)',
      borderRadius: 'var(--r-sm)',
      boxShadow: 'var(--sh-2)',
      padding: 8,
    }}>
      <svg viewBox={`${minX} ${minY} ${w} ${h}`} width={mw} height={mh}>
        {EDGES.map((e, i) => {
          const a = NODES.find((n) => n.id === e.from);
          const b = NODES.find((n) => n.id === e.to);
          return <line key={i} x1={a.x+a.w/2} y1={a.y+a.h/2} x2={b.x+b.w/2} y2={b.y+b.h/2}
                       stroke="var(--c-ink-300)" strokeWidth="2" opacity="0.6" />;
        })}
        {NODES.map((n) => (
          <rect key={n.id}
            x={n.x} y={n.y} width={n.w} height={n.h}
            rx={4}
            fill={STATUS_BAR_TOKENS[n.status]}
            stroke={n.selected ? 'var(--c-accent-600)' : 'none'}
            strokeWidth="14"
            opacity={n.selected ? 1 : 0.55} />
        ))}
        {/* viewport indicator */}
        <rect x={minX + 40} y={minY + 40} width={400} height={300}
          fill="none" stroke="var(--c-accent-600)" strokeWidth="6" />
      </svg>
    </div>
  );
}

function NodeDetailsPanel({ nodeId }) {
  const node = NODES.find((n) => n.id === nodeId);
  if (!node) return null;
  const typeTok = NODE_TYPE_TOKENS[node.type];
  const statusColor = STATUS_BAR_TOKENS[node.status];

  return (
    <aside style={{
      flex: 'none',
      width: 320,
      background: 'var(--c-ink-0)',
      borderInlineStart: 'var(--br-hair)',
      overflow: 'auto',
      display: 'flex', flexDirection: 'column',
    }}>
      <header style={{
        padding: '12px 14px 10px',
        borderBottom: 'var(--br-hair)',
        display: 'flex', alignItems: 'center', gap: 8,
      }}>
        <Icon name={typeTok.icon} size={14} style={{ color: typeTok.fg }} />
        <span style={{ fontSize: 11, fontWeight: 600, color: typeTok.fg, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
          {typeTok.label}
        </span>
        <span style={{ width: 1, height: 14, background: 'var(--c-ink-150)' }} />
        <span style={{
          display: 'inline-flex', alignItems: 'center', gap: 4,
          fontSize: 11, color: 'var(--c-ink-700)',
        }}>
          <span style={{ width: 6, height: 6, borderRadius: '50%', background: statusColor }} />
          Спорный
        </span>
        <span style={{ flex: 1 }} />
        <button className="btn btn-ghost btn-sm btn-icon"><Icon name="x" size={14} /></button>
      </header>

      <div style={{ padding: '14px 16px 8px' }}>
        <div className="mono" style={{ fontSize: 10, color: 'var(--c-ink-400)', marginBottom: 6 }}>{node.id}</div>
        <p style={{
          margin: 0,
          fontFamily: 'var(--font-serif)',
          fontSize: 15, lineHeight: 1.45,
          color: 'var(--c-ink-900)',
          fontWeight: 500,
        }}>
          {node.title}
        </p>
        {node.body && (
          <p dir="auto" style={{
            marginTop: 8, marginBottom: 0,
            fontSize: 13, lineHeight: 1.55,
            color: 'var(--c-ink-700)',
            fontFamily: /[\u0600-\u06FF]/.test(node.body) ? 'var(--font-arabic)' : undefined,
          }}>{node.body}</p>
        )}
      </div>

      {/* Citations */}
      <section style={{ padding: '14px 16px 0', borderTop: 'var(--br-hair)' }}>
        <PanelHeading>Источники · 2</PanelHeading>
        <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'grid', gap: 6 }}>
          {[
            { ico: 'book',     title: 'صحيح البخاري', sub: 'хадис 858 · Абу Са‘ид', grade: 'صحيح', ok: true },
            { ico: 'file-text', title: 'تفسير ابن كثير', sub: 'т. 1, стр. 1548',         grade: 'حسن',  ok: true },
          ].map((s, i) => (
            <li key={i} style={{
              padding: 8,
              border: 'var(--br-hair)',
              borderRadius: 'var(--r-sm)',
              display: 'flex', alignItems: 'center', gap: 8,
            }}>
              <Icon name={s.ico} size={14} style={{ color: 'var(--c-ink-500)' }} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div dir="auto" style={{
                  fontSize: 13, fontWeight: 500,
                  fontFamily: 'var(--font-arabic)',
                }}>{s.title}</div>
                <div style={{ fontSize: 11, color: 'var(--c-ink-500)' }}>{s.sub}</div>
              </div>
              <span className="chip chip-ok" style={{ fontFamily: 'var(--font-arabic)', fontSize: 11 }}>{s.grade}</span>
            </li>
          ))}
        </ul>
        <button className="btn btn-ghost btn-sm" style={{ marginTop: 8 }}>
          <Icon name="sparkles" size={12} /> Добавить источник
        </button>
      </section>

      {/* Edges */}
      <section style={{ padding: '14px 16px 0' }}>
        <PanelHeading>Связи · 3</PanelHeading>
        <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'grid', gap: 4 }}>
          {[
            { kind: 'RESPONDS_TO', dir: 'out', target: 'q1', label: 'отвечает на «Является ли гусль фардом?»' },
            { kind: 'SUPPORTS',    dir: 'in',  target: 'a2', label: 'аргумент: буквальное прочтение «ваджиб»' },
            { kind: 'REFUTES',     dir: 'in',  target: 'e2', label: 'свидетельство: передача Ибн ‘Умара' },
          ].map((e, i) => {
            const t = EDGE_TOKENS[e.kind];
            return (
              <li key={i} style={{
                display: 'flex', alignItems: 'center', gap: 8,
                padding: '5px 6px',
                fontSize: 12,
              }}>
                <span style={{
                  display: 'inline-flex', alignItems: 'center', gap: 3,
                  padding: '1px 5px',
                  fontSize: 9, fontFamily: 'var(--font-mono)',
                  fontWeight: 700, letterSpacing: '0.04em',
                  color: t.color,
                  background: 'var(--c-bg-elevated)',
                  border: `1px solid color-mix(in srgb, ${t.color} 35%, transparent)`,
                  borderRadius: 3,
                  textTransform: 'uppercase',
                }}>
                  {e.dir === 'out' ? '→' : '←'} {t.label}
                </span>
                <span style={{
                  flex: 1,
                  color: 'var(--c-ink-700)',
                  overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                }}>{e.label}</span>
              </li>
            );
          })}
        </ul>
      </section>

      {/* Meta */}
      <section style={{ padding: '14px 16px 16px', marginTop: 'auto', borderTop: 'var(--br-hair)' }}>
        <PanelHeading>Метаданные</PanelHeading>
        <dl style={{
          margin: 0, display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          rowGap: 4, columnGap: 8,
          fontSize: 11,
        }}>
          <dt style={{ color: 'var(--c-ink-500)' }}>Создан</dt>
          <dd style={{ margin: 0, color: 'var(--c-ink-800)' }}>3 мая, 14:22</dd>
          <dt style={{ color: 'var(--c-ink-500)' }}>Автор</dt>
          <dd style={{ margin: 0, color: 'var(--c-ink-800)' }}>МА</dd>
          <dt style={{ color: 'var(--c-ink-500)' }}>Ревизий</dt>
          <dd style={{ margin: 0, color: 'var(--c-ink-800)' }} className="mono">7</dd>
          <dt style={{ color: 'var(--c-ink-500)' }}>Позиция</dt>
          <dd style={{ margin: 0, color: 'var(--c-ink-800)' }} className="mono">340, 270</dd>
        </dl>
      </section>
    </aside>
  );
}

function PanelHeading({ children }) {
  return (
    <h4 style={{
      margin: '0 0 8px',
      fontSize: 10, fontWeight: 600,
      letterSpacing: '0.1em', textTransform: 'uppercase',
      color: 'var(--c-ink-500)',
    }}>{children}</h4>
  );
}

window.TopicGraphBoard = TopicGraphBoard;
