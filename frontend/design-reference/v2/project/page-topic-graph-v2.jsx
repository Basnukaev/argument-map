// TopicGraph v2 — мердж твоего нынешнего стиля графа и моего v1.
// Берём у твоего:
//   · pill-type chip + status pill + kebab внутри карточки узла
//   · edge-label как outlined pill с галочкой/шевроном
//   · вертикальная плавающая left-toolbar (add/link/eye/delete)
//   · keyboard hints как pill сверху справа
//   · zoom controls по центру внизу как floating buttons
//   · expandable minimap справа внизу
//   · status legend bottom-left
//   · правая панель: collapsible sections, source-card блоком, action footer
// Берём у v1:
//   · общий цветовой и типографический язык
//   · реакция на тёмную тему через var(--c-*)
//   · корректная навигация AppHeader

const V2_NODES = [
  { id: 'q1', type: 'QUESTION', status: 'UNVERIFIED', title: '123',
    x: 340, y: 360, w: 240, h: 78 },
  { id: 'c1', type: 'CLAIM', status: 'UNVERIFIED', title: '123',
    x: 470, y: 170, w: 240, h: 78 },
  { id: 'a1', type: 'ARGUMENT', status: 'UNVERIFIED', title: '12',
    x: 710, y: 360, w: 240, h: 78 },
  { id: 'e1', type: 'EVIDENCE', status: 'UNVERIFIED', title: '123',
    x: 510, y: 560, w: 240, h: 88, selected: true },
];

const V2_EDGES = [
  { from: 'q1', to: 'c1', kind: 'QUALIFIES' },
  { from: 'e1', to: 'c1', kind: 'SUPPORTS' },
  { from: 'e1', to: 'a1', kind: 'PROVES' },
];

// Type pill design — soft fill, no border, icon left of label.
// Все кроме EVIDENCE — фиолетовые тона; EVIDENCE — изумруд.
const V2_TYPE_TOKENS = {
  QUESTION: { bg: '#ede9fe', fg: '#5b21b6', icon: 'help-circle', label: 'Вопрос' },
  CLAIM:    { bg: '#ede9fe', fg: '#5b21b6', icon: 'quote',       label: 'Тезис' },
  ARGUMENT: { bg: '#ede9fe', fg: '#5b21b6', icon: 'sparkles',    label: 'Довод' },
  EVIDENCE: { bg: '#d1fae5', fg: '#065f46', icon: 'file-text',   label: 'Свидетельство' },
};

// Edge style — outlined pill with leading icon.
const V2_EDGE_TOKENS = {
  SUPPORTS:    { color: '#16a34a', bg: '#f0fdf4', icon: 'check',  label: 'поддерживает' },
  REFUTES:     { color: '#dc2626', bg: '#fef2f2', icon: 'x',      label: 'опровергает' },
  INVALIDATES: { color: '#991b1b', bg: '#fef2f2', icon: 'x',      label: 'аннулирует' },
  QUALIFIES:   { color: '#2563eb', bg: '#eff6ff', icon: 'chevrons-right', label: 'уточняет', dash: '0' },
  PROVES:      { color: '#15803d', bg: '#f0fdf4', icon: 'check',  label: 'доказывает' },
  RESPONDS_TO: { color: '#525252', bg: '#fafafa', icon: 'arrow-left', label: 'отвечает' },
};

const V2_STATUS_TOKENS = {
  STANDING:   { dot: 'var(--c-ok-500)',   label: 'Устоявшийся' },
  DISPUTED:   { dot: 'var(--c-warn-500)', label: 'Спорный' },
  REFUTED:    { dot: 'var(--c-err-500)',  label: 'Опровергнут' },
  UNVERIFIED: { dot: 'var(--c-ink-300)',  label: 'Не оценён' },
};

function TopicGraphV2Board() {
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

      {/* Topic crumb */}
      <div style={{
        flex: 'none',
        height: 44,
        display: 'flex', alignItems: 'center', gap: 10,
        padding: '0 24px',
        background: 'var(--c-ink-0)',
        borderBottom: 'var(--br-hair)',
      }}>
        <button className="btn btn-ghost btn-sm">
          <Icon name="arrow-left" size={13} />
          К списку
        </button>
        <span style={{ color: 'var(--c-ink-300)' }}>/</span>
        <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--c-ink-900)' }}>фыв</span>
        <span style={{ fontSize: 12, color: 'var(--c-ink-500)' }}>фыв</span>
      </div>

      {/* Canvas + right panel */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden', minHeight: 0 }}>
        <V2Canvas />
        <V2DetailPanel nodeId="e1" />
      </div>
    </div>
  );
}

function V2Canvas() {
  const nodeCenters = Object.fromEntries(
    V2_NODES.map((n) => [n.id, { x: n.x + n.w / 2, y: n.y + n.h / 2 }])
  );

  return (
    <div style={{
      flex: 1, position: 'relative',
      background: 'var(--c-bg)',
      overflow: 'hidden',
    }}>
      {/* dot grid background */}
      <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }} aria-hidden="true">
        <defs>
          <pattern id="v2dotbg" width="20" height="20" patternUnits="userSpaceOnUse">
            <circle cx="1" cy="1" r="0.7" fill="var(--c-ink-200)" />
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#v2dotbg)" opacity="0.7" />
      </svg>

      {/* Edges */}
      <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', pointerEvents: 'none' }}>
        <defs>
          {Object.entries(V2_EDGE_TOKENS).map(([k, t]) => (
            <marker key={k} id={`v2arr-${k}`} viewBox="0 0 10 10"
              refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill={t.color} />
            </marker>
          ))}
        </defs>
        {V2_EDGES.map((e, i) => {
          const t = V2_EDGE_TOKENS[e.kind];
          const from = nodeCenters[e.from];
          const to = nodeCenters[e.to];
          // Vertical-bias bezier — produces nice arching curves like your screenshots
          const dx = to.x - from.x;
          const dy = to.y - from.y;
          const dist = Math.sqrt(dx*dx + dy*dy);
          const ctrlOffset = Math.min(80, dist * 0.35);
          const c1x = from.x;
          const c1y = from.y + (dy > 0 ? -ctrlOffset : ctrlOffset);
          const c2x = to.x;
          const c2y = to.y + (dy > 0 ? -ctrlOffset : ctrlOffset);
          const midX = (from.x + to.x) / 2;
          const midY = (from.y + to.y) / 2;
          return (
            <g key={i}>
              <path
                d={`M ${from.x} ${from.y} C ${c1x} ${c1y}, ${c2x} ${c2y}, ${to.x} ${to.y}`}
                fill="none"
                stroke={t.color}
                strokeWidth={1.8}
                strokeDasharray={t.dash}
                markerEnd={`url(#v2arr-${e.kind})`}
              />
              <V2EdgePill x={midX} y={midY} kind={e.kind} />
            </g>
          );
        })}
      </svg>

      {/* Nodes */}
      {V2_NODES.map((n) => <V2NodeCard key={n.id} node={n} />)}

      {/* LEFT vertical toolbar */}
      <div style={{
        position: 'absolute',
        top: 16, insetInlineStart: 16,
        display: 'flex', flexDirection: 'column', gap: 4,
        background: 'var(--c-bg-elevated)',
        border: 'var(--br-hair)',
        borderRadius: 'var(--r-md)',
        boxShadow: 'var(--sh-2)',
        padding: 4,
      }}>
        <V2ToolBtn icon="sparkles" label="Добавить узел" />
        <V2ToolBtn icon="graph" label="Добавить связь" />
        <V2ToolBtn icon="eye" label="Скрыть метки" active />
        <div style={{ height: 1, background: 'var(--c-ink-150)', margin: '2px 4px' }} />
        <V2ToolBtn icon="x" label="Удалить" danger />
      </div>

      {/* TOP-RIGHT keyboard hints */}
      <div style={{
        position: 'absolute',
        top: 16, insetInlineEnd: 16,
        display: 'flex', alignItems: 'center', gap: 8,
        background: 'var(--c-bg-elevated)',
        border: 'var(--br-hair)',
        borderRadius: 'var(--r-md)',
        padding: '6px 10px',
        boxShadow: 'var(--sh-1)',
      }}>
        {[
          ['2x',  'детали'],
          ['Del', 'удалить'],
          ['RMB', 'меню'],
        ].map(([k, l]) => (
          <span key={k} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <span className="kbd" style={{ minWidth: 24 }}>{k}</span>
            <span style={{ fontSize: 11, color: 'var(--c-ink-600)' }}>{l}</span>
          </span>
        ))}
      </div>

      {/* BOTTOM-CENTER zoom controls */}
      <div style={{
        position: 'absolute',
        bottom: 16, left: '50%', transform: 'translateX(-50%)',
        display: 'flex', alignItems: 'center', gap: 14,
      }}>
        {['minimize', 'maximize', 'columns'].map((ico) => (
          <button key={ico}
            aria-label={ico}
            style={{
              width: 32, height: 32, borderRadius: '50%',
              background: 'var(--c-bg-elevated)',
              border: 'var(--br-hair)',
              boxShadow: 'var(--sh-2)',
              display: 'grid', placeItems: 'center',
              color: 'var(--c-ink-700)',
            }}>
            <Icon name={ico} size={14} />
          </button>
        ))}
      </div>

      {/* BOTTOM-LEFT status legend */}
      <div style={{
        position: 'absolute',
        bottom: 16, insetInlineStart: 16,
        background: 'var(--c-bg-elevated)',
        border: 'var(--br-hair)',
        borderRadius: 'var(--r-md)',
        boxShadow: 'var(--sh-1)',
        padding: '10px 12px',
        maxWidth: 230,
      }}>
        <div style={{
          fontSize: 10, fontWeight: 600,
          color: 'var(--c-ink-500)',
          textTransform: 'uppercase', letterSpacing: '0.08em',
          marginBottom: 6,
        }}>Статусы</div>
        <div style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: '4px 12px',
          fontSize: 11,
          color: 'var(--c-ink-700)',
        }}>
          {Object.values(V2_STATUS_TOKENS).map((s) => (
            <span key={s.label} style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
              <span style={{ width: 8, height: 8, borderRadius: '50%', background: s.dot }} />
              {s.label}
            </span>
          ))}
        </div>
      </div>

      {/* BOTTOM-RIGHT collapsible minimap */}
      <div style={{
        position: 'absolute',
        bottom: 16, insetInlineEnd: 16,
        width: 180, height: 130,
        background: 'var(--c-bg-elevated)',
        border: '1.5px dashed color-mix(in srgb, var(--c-accent-500) 50%, transparent)',
        borderRadius: 'var(--r-md)',
        padding: 6,
        boxShadow: 'var(--sh-2)',
      }}>
        <button style={{
          position: 'absolute',
          top: -10, insetInlineStart: -10,
          width: 20, height: 20, borderRadius: '50%',
          background: 'var(--c-bg-elevated)',
          border: 'var(--br-hair)',
          boxShadow: 'var(--sh-1)',
          display: 'grid', placeItems: 'center',
        }}>
          <Icon name="maximize" size={10} />
        </button>
        <V2Minimap />
      </div>
    </div>
  );
}

function V2NodeCard({ node }) {
  const t = V2_TYPE_TOKENS[node.type];
  const s = V2_STATUS_TOKENS[node.status];
  return (
    <div style={{
      position: 'absolute',
      left: node.x, top: node.y,
      width: node.w, minHeight: node.h,
      background: 'var(--c-bg-elevated)',
      border: node.selected
        ? '2px solid var(--c-accent-600)'
        : 'var(--br-soft)',
      borderRadius: 'var(--r-md)',
      boxShadow: node.selected
        ? '0 0 0 4px color-mix(in srgb, var(--c-accent-600) 16%, transparent), var(--sh-2)'
        : 'var(--sh-1)',
      overflow: 'hidden',
    }}>
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8,
        padding: '8px 10px 6px',
      }}>
        <span style={{
          display: 'inline-flex', alignItems: 'center', gap: 4,
          padding: '3px 8px',
          background: t.bg,
          color: t.fg,
          borderRadius: 'var(--r-sm)',
          fontSize: 10, fontWeight: 600,
          textTransform: 'uppercase', letterSpacing: '0.04em',
        }}>
          <Icon name={t.icon} size={11} />
          {t.label}
        </span>
        <span style={{ flex: 1 }} />
        <span style={{
          display: 'inline-flex', alignItems: 'center', gap: 4,
          padding: '2px 7px',
          background: 'var(--c-bg-elevated)',
          border: 'var(--br-hair)',
          borderRadius: 99,
          fontSize: 10, fontWeight: 500,
          color: 'var(--c-ink-700)',
        }}>
          <span style={{ width: 6, height: 6, borderRadius: '50%', background: s.dot, border: '1px solid color-mix(in srgb, currentColor 30%, transparent)' }} />
          {s.label}
        </span>
        <button aria-label="more" style={{
          width: 18, height: 18, borderRadius: 'var(--r-sm)',
          display: 'grid', placeItems: 'center',
          color: 'var(--c-ink-500)',
        }}>
          <Icon name="menu" size={12} />
        </button>
      </div>
      <div style={{
        padding: '0 14px 12px',
        fontSize: 13,
        fontWeight: 500,
        color: 'var(--c-ink-900)',
      }}>
        {node.title}
      </div>
    </div>
  );
}

function V2EdgePill({ x, y, kind }) {
  const t = V2_EDGE_TOKENS[kind];
  // Render as foreignObject-ish: an SVG-friendly pill
  const w = t.label.length * 6.5 + 28;
  const h = 22;
  return (
    <g transform={`translate(${x - w/2} ${y - h/2})`}>
      <rect x="0" y="0" width={w} height={h} rx={h/2}
            fill="var(--c-bg-elevated)" stroke={t.color} strokeWidth="1" />
      <text x={w/2} y={h/2 + 3.5}
            textAnchor="middle"
            fill={t.color}
            fontSize="10.5"
            fontWeight="600"
            style={{ fontFamily: 'var(--font-ui)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
        ✓ {t.label}
      </text>
    </g>
  );
}

function V2ToolBtn({ icon, label, active = false, danger = false }) {
  const color = danger ? 'var(--c-err-500)' : active ? 'var(--c-accent-700)' : 'var(--c-ink-700)';
  const bg    = active ? 'var(--c-accent-50)' : 'transparent';
  return (
    <button title={label} aria-label={label} style={{
      width: 32, height: 32,
      display: 'grid', placeItems: 'center',
      borderRadius: 'var(--r-sm)',
      background: bg,
      color,
    }}>
      <Icon name={icon} size={15} />
    </button>
  );
}

function V2Minimap() {
  const minX = Math.min(...V2_NODES.map((n) => n.x));
  const minY = Math.min(...V2_NODES.map((n) => n.y));
  const maxX = Math.max(...V2_NODES.map((n) => n.x + n.w));
  const maxY = Math.max(...V2_NODES.map((n) => n.y + n.h));
  const w = maxX - minX, h = maxY - minY;
  return (
    <svg viewBox={`${minX - 30} ${minY - 30} ${w + 60} ${h + 60}`}
         style={{ width: '100%', height: '100%' }}>
      {V2_EDGES.map((e, i) => {
        const a = V2_NODES.find((n) => n.id === e.from);
        const b = V2_NODES.find((n) => n.id === e.to);
        const t = V2_EDGE_TOKENS[e.kind];
        return (
          <line key={i}
            x1={a.x + a.w/2} y1={a.y + a.h/2}
            x2={b.x + b.w/2} y2={b.y + b.h/2}
            stroke={t.color} strokeWidth="3" opacity="0.6" />
        );
      })}
      {V2_NODES.map((n) => {
        const tok = V2_TYPE_TOKENS[n.type];
        return (
          <rect key={n.id}
            x={n.x} y={n.y} width={n.w} height={n.h}
            rx={10}
            fill={tok.fg}
            opacity={n.selected ? 1 : 0.7}
            stroke={n.selected ? 'var(--c-accent-600)' : 'none'}
            strokeWidth="14" />
        );
      })}
    </svg>
  );
}

function V2DetailPanel({ nodeId }) {
  const node = V2_NODES.find((n) => n.id === nodeId);
  if (!node) return null;
  const t = V2_TYPE_TOKENS[node.type];
  const s = V2_STATUS_TOKENS[node.status];

  return (
    <aside style={{
      flex: 'none',
      width: 380,
      background: 'var(--c-ink-0)',
      borderInlineStart: 'var(--br-hair)',
      overflow: 'auto',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* Header */}
      <header style={{
        padding: '14px 16px 12px',
        borderBottom: 'var(--br-hair)',
      }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
          <span style={{
            width: 28, height: 28,
            borderRadius: 'var(--r-sm)',
            background: t.bg,
            color: t.fg,
            display: 'grid', placeItems: 'center',
            flex: 'none',
          }}>
            <Icon name={t.icon} size={14} />
          </span>
          <div style={{ flex: 1 }}>
            <div style={{
              fontSize: 11, fontWeight: 600,
              color: t.fg,
              textTransform: 'uppercase',
              letterSpacing: '0.06em',
            }}>EVIDENCE · {t.label}</div>
            <div className="mono" style={{ fontSize: 11, color: 'var(--c-ink-500)', marginTop: 2 }}>
              {node.id === 'e1' ? '176c6b90' : node.id}
            </div>
          </div>
          <button className="btn btn-ghost btn-sm btn-icon"><Icon name="x" size={14} /></button>
        </div>

        <div style={{ marginTop: 10, display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{
            display: 'inline-flex', alignItems: 'center', gap: 5,
            padding: '3px 9px',
            background: 'var(--c-bg-elevated)',
            border: 'var(--br-hair)',
            borderRadius: 99,
            fontSize: 11, fontWeight: 500,
            color: 'var(--c-ink-700)',
          }}>
            <span style={{ width: 7, height: 7, borderRadius: '50%', background: s.dot }} />
            {s.label}
          </span>
          <span style={{ flex: 1 }} />
          {[
            ['pin', '1', 'опора'],
            ['book', '1', ''],
            ['quote', '0', ''],
          ].map(([ico, n, l], i) => (
            <span key={i} style={{
              display: 'inline-flex', alignItems: 'center', gap: 3,
              fontSize: 11, color: 'var(--c-ink-600)',
            }}>
              <Icon name={ico} size={11} />
              <span className="mono">{n}</span>
              {l && <span>{l}</span>}
            </span>
          ))}
        </div>
      </header>

      <CollapseSection title="Содержание" defaultOpen>
        <p style={{
          margin: 0,
          fontSize: 14,
          color: 'var(--c-ink-900)',
          lineHeight: 1.5,
          fontFamily: 'var(--font-serif)',
        }}>
          {node.title}
        </p>
        <button className="btn btn-ghost btn-sm" style={{ marginTop: 8, padding: 0 }}>
          <Icon name="quote" size={12} /> Редактировать
        </button>
      </CollapseSection>

      <CollapseSection title="Метаданные" defaultOpen>
        <dl style={{
          margin: 0,
          display: 'grid',
          gridTemplateColumns: 'auto 1fr',
          rowGap: 6, columnGap: 12,
          fontSize: 12,
        }}>
          <dt style={{ color: 'var(--c-ink-500)' }}>Создан</dt>
          <dd style={{ margin: 0, color: 'var(--c-ink-800)' }}>8 мая 2026 г. в 01:23</dd>
          <dt style={{ color: 'var(--c-ink-500)' }}>Автор</dt>
          <dd style={{ margin: 0, color: 'var(--c-ink-800)' }} className="mono">14561248</dd>
          <dt style={{ color: 'var(--c-ink-500)' }}>ID</dt>
          <dd style={{ margin: 0, color: 'var(--c-ink-800)' }} className="mono">176c6b90</dd>
        </dl>
      </CollapseSection>

      <CollapseSection title="Опора · 1" defaultOpen>
        <div style={{
          background: 'var(--c-bg-elevated)',
          border: 'var(--br-hair)',
          borderRadius: 'var(--r-md)',
          padding: 12,
          display: 'flex', flexDirection: 'column', gap: 10,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{
              display: 'inline-flex', alignItems: 'center', gap: 4,
              padding: '2px 7px',
              background: '#ede9fe', color: '#5b21b6',
              borderRadius: 'var(--r-sm)',
              fontSize: 10, fontWeight: 600,
              textTransform: 'uppercase', letterSpacing: '0.04em',
            }}>
              <Icon name="book" size={10} />
              Из библиотеки
            </span>
            <span style={{ flex: 1, fontSize: 12, fontWeight: 500, color: 'var(--c-ink-900)' }}>
              Тафсир Ибн Касира
            </span>
            <button className="btn btn-ghost btn-sm btn-icon" aria-label="remove">
              <Icon name="x" size={11} />
            </button>
          </div>

          {/* Citation quote block */}
          <div style={{
            position: 'relative',
            background: 'var(--c-paper)',
            border: 'var(--br-hair)',
            borderRadius: 'var(--r-sm)',
            padding: '12px 14px',
          }}>
            <div style={{
              position: 'absolute',
              top: 6, insetInlineStart: 10,
              fontSize: 10, color: 'var(--c-ink-500)',
              fontFamily: 'var(--font-mono)',
            }}>стр. 3</div>
            <div style={{
              position: 'absolute',
              top: 6, insetInlineEnd: 10,
              fontSize: 11, color: 'var(--c-ink-500)',
              fontFamily: 'var(--font-arabic)',
            }}>المقدمة</div>
            <p dir="rtl" style={{
              margin: '14px 0 0',
              fontFamily: 'var(--font-arabic)',
              fontSize: 14,
              lineHeight: 1.9,
              color: 'var(--c-ink-900)',
              textAlign: 'justify',
            }}>
              الحمد لله رب العالمين، والصلاة والسلام على عبده ورسوله محمد، وعلى آله وصحبه وسلَّم تسليمًا كثيرًا
            </p>
          </div>

          {/* Sub-metadata */}
          <div style={{
            paddingTop: 6,
            borderTop: 'var(--br-hair)',
            fontSize: 11,
          }}>
            <div style={{
              fontWeight: 600,
              color: 'var(--c-ink-500)',
              textTransform: 'uppercase',
              letterSpacing: '0.06em',
              marginBottom: 6,
            }}>Метаданные</div>
            <dl style={{
              margin: 0,
              display: 'grid', gridTemplateColumns: 'auto 1fr',
              rowGap: 3, columnGap: 12,
            }}>
              {[
                ['Автор', 'إسماعيل بن عمر بن كثير الدمشقي'],
                ['Год смерти', '774 ه'],
                ['Название', 'تفسير ابن كثير - ط ابن الجوزي'],
                ['Тахкик', 'سامي بن محمد السلامة'],
                ['Издатель', 'Дар Тайба · Эр-Рияд'],
                ['Издание', '2-е изд.'],
                ['Год', '1420 ه / 1999 г.'],
              ].map(([k, v]) => (
                <React.Fragment key={k}>
                  <dt style={{
                    color: 'var(--c-ink-500)',
                    textTransform: 'uppercase',
                    fontSize: 10, fontWeight: 600,
                    letterSpacing: '0.04em',
                  }}>{k}</dt>
                  <dd dir="auto" style={{
                    margin: 0, color: 'var(--c-ink-800)',
                    fontFamily: /[\u0600-\u06FF]/.test(v) ? 'var(--font-arabic)' : undefined,
                    fontSize: /[\u0600-\u06FF]/.test(v) ? 13 : 11,
                  }}>{v}</dd>
                </React.Fragment>
              ))}
            </dl>
          </div>

          <button className="btn btn-primary btn-sm">
            <Icon name="sparkles" size={11} />
            Перейти к источнику
          </button>
        </div>
      </CollapseSection>

      {/* Action footer */}
      <div style={{
        marginTop: 'auto',
        padding: '12px 16px',
        borderTop: 'var(--br-hair)',
        display: 'flex', gap: 8,
        background: 'var(--c-ink-0)',
        position: 'sticky', bottom: 0,
      }}>
        <button className="btn btn-primary" style={{ flex: 1 }}>
          <Icon name="quote" size={12} />
          Привести источник
        </button>
        <button className="btn btn-secondary">
          <Icon name="sparkles" size={12} />
          Свободный
        </button>
      </div>
    </aside>
  );
}

function CollapseSection({ title, defaultOpen, children }) {
  const [open, setOpen] = React.useState(defaultOpen);
  return (
    <section style={{
      borderBottom: 'var(--br-hair)',
    }}>
      <button onClick={() => setOpen((o) => !o)} style={{
        width: '100%',
        display: 'flex', alignItems: 'center', gap: 6,
        padding: '10px 16px',
        fontSize: 10, fontWeight: 600,
        color: 'var(--c-ink-500)',
        textTransform: 'uppercase', letterSpacing: '0.08em',
      }}>
        <Icon name="quote" size={11} style={{ color: 'var(--c-ink-500)' }} />
        {title}
        <span style={{ flex: 1 }} />
        <Icon name={open ? 'chevron-down' : 'chevron-right'} size={12} style={{ color: 'var(--c-ink-500)' }} />
      </button>
      {open && (
        <div style={{ padding: '0 16px 16px' }}>
          {children}
        </div>
      )}
    </section>
  );
}

window.TopicGraphV2Board = TopicGraphV2Board;
