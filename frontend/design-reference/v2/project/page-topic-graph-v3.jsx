// TopicGraph v3 — same layout as v2, but:
//  · Type chips и edge pills используют семантические токены
//    (--c-type-* и --c-edge-*), которые сами переключаются в тёмной теме.
//    Больше нет «светящихся» лавандовых пиллов на тёмном фоне.
//  · Удалена статус-легенда снизу-слева (статусы теперь только в карточке узла).
//  · Лёгкие правки расстояний/контраста для тёмной темы.

const V3_NODES = [
  { id: 'q1', type: 'QUESTION', status: 'UNVERIFIED', title: '123',
    x: 340, y: 360, w: 240, h: 78 },
  { id: 'c1', type: 'CLAIM', status: 'UNVERIFIED', title: '123',
    x: 470, y: 170, w: 240, h: 78 },
  { id: 'a1', type: 'ARGUMENT', status: 'UNVERIFIED', title: '12',
    x: 710, y: 360, w: 240, h: 78 },
  { id: 'e1', type: 'EVIDENCE', status: 'UNVERIFIED', title: '123',
    x: 510, y: 560, w: 240, h: 88, selected: true },
];

const V3_EDGES = [
  { from: 'q1', to: 'c1', kind: 'QUALIFIES' },
  { from: 'e1', to: 'c1', kind: 'SUPPORTS' },
  { from: 'e1', to: 'a1', kind: 'PROVES' },
];

// Все 4 типа используют 2 семантических токена: abstract (Q/C/A) и empirical (E).
// Цвет внутри chip-а решает тема — через CSS-переменные.
const V3_TYPE_TOKENS = {
  QUESTION: { bgVar: 'var(--c-type-abstract-bg)',  fgVar: 'var(--c-type-abstract-fg)',  icon: 'help-circle', label: 'Вопрос' },
  CLAIM:    { bgVar: 'var(--c-type-abstract-bg)',  fgVar: 'var(--c-type-abstract-fg)',  icon: 'quote',       label: 'Тезис' },
  ARGUMENT: { bgVar: 'var(--c-type-abstract-bg)',  fgVar: 'var(--c-type-abstract-fg)',  icon: 'sparkles',    label: 'Довод' },
  EVIDENCE: { bgVar: 'var(--c-type-empirical-bg)', fgVar: 'var(--c-type-empirical-fg)', icon: 'file-text',   label: 'Свидетельство' },
};

const V3_EDGE_TOKENS = {
  SUPPORTS:    { color: 'var(--c-edge-supports)',  bg: 'var(--c-edge-supports-bg)',  label: 'поддерживает', mark: '✓' },
  REFUTES:     { color: 'var(--c-edge-refutes)',   bg: 'var(--c-edge-refutes-bg)',   label: 'опровергает',  mark: '✗' },
  INVALIDATES: { color: 'var(--c-edge-refutes)',   bg: 'var(--c-edge-refutes-bg)',   label: 'аннулирует',   mark: '⊘' },
  QUALIFIES:   { color: 'var(--c-edge-qualifies)', bg: 'var(--c-edge-qualifies-bg)', label: 'уточняет',     mark: '»' },
  PROVES:      { color: 'var(--c-edge-supports)',  bg: 'var(--c-edge-supports-bg)',  label: 'доказывает',   mark: '✓' },
  RESPONDS_TO: { color: 'var(--c-edge-responds)',  bg: 'var(--c-edge-responds-bg)',  label: 'отвечает',     mark: '↩' },
};

const V3_STATUS_TOKENS = {
  STANDING:   { dot: 'var(--c-ok-500)',   label: 'Устоявшийся' },
  DISPUTED:   { dot: 'var(--c-warn-500)', label: 'Спорный' },
  REFUTED:    { dot: 'var(--c-err-500)',  label: 'Опровергнут' },
  UNVERIFIED: { dot: 'var(--c-ink-400)',  label: 'Не оценён' },
};

function TopicGraphV3Board() {
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

      <div style={{ flex: 1, display: 'flex', overflow: 'hidden', minHeight: 0 }}>
        <V3Canvas />
        <V3DetailPanel nodeId="e1" />
      </div>
    </div>
  );
}

function V3Canvas() {
  const nodeCenters = Object.fromEntries(
    V3_NODES.map((n) => [n.id, { x: n.x + n.w / 2, y: n.y + n.h / 2 }])
  );

  return (
    <div style={{
      flex: 1, position: 'relative',
      background: 'var(--c-bg)',
      overflow: 'hidden',
    }}>
      <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }} aria-hidden="true">
        <defs>
          <pattern id="v3dotbg" width="20" height="20" patternUnits="userSpaceOnUse">
            <circle cx="1" cy="1" r="0.7" fill="var(--c-ink-200)" />
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#v3dotbg)" opacity="0.7" />
      </svg>

      <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', pointerEvents: 'none' }}>
        <defs>
          {Object.entries(V3_EDGE_TOKENS).map(([k, t]) => (
            <marker key={k} id={`v3arr-${k}`} viewBox="0 0 10 10"
              refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill={t.color} />
            </marker>
          ))}
        </defs>
        {V3_EDGES.map((e, i) => {
          const t = V3_EDGE_TOKENS[e.kind];
          const from = nodeCenters[e.from];
          const to = nodeCenters[e.to];
          const dx = to.x - from.x;
          const dy = to.y - from.y;
          const dist = Math.sqrt(dx*dx + dy*dy);
          const ctrlOffset = Math.min(80, dist * 0.35);
          const c1y = from.y + (dy > 0 ? -ctrlOffset : ctrlOffset);
          const c2y = to.y + (dy > 0 ? -ctrlOffset : ctrlOffset);
          const midX = (from.x + to.x) / 2;
          const midY = (from.y + to.y) / 2;
          return (
            <g key={i}>
              <path
                d={`M ${from.x} ${from.y} C ${from.x} ${c1y}, ${to.x} ${c2y}, ${to.x} ${to.y}`}
                fill="none"
                stroke={t.color}
                strokeWidth={1.6}
                opacity={0.85}
                markerEnd={`url(#v3arr-${e.kind})`}
              />
              <V3EdgePill x={midX} y={midY} kind={e.kind} />
            </g>
          );
        })}
      </svg>

      {V3_NODES.map((n) => <V3NodeCard key={n.id} node={n} />)}

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
        <V3ToolBtn icon="sparkles" label="Добавить узел" />
        <V3ToolBtn icon="graph" label="Добавить связь" />
        <V3ToolBtn icon="eye" label="Скрыть метки" active />
        <div style={{ height: 1, background: 'var(--c-ink-150)', margin: '2px 4px' }} />
        <V3ToolBtn icon="x" label="Удалить" danger />
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
        <V3Minimap />
      </div>
    </div>
  );
}

function V3NodeCard({ node }) {
  const t = V3_TYPE_TOKENS[node.type];
  const s = V3_STATUS_TOKENS[node.status];
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
          background: t.bgVar,
          color: t.fgVar,
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
          <span style={{ width: 6, height: 6, borderRadius: '50%', background: s.dot }} />
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

function V3EdgePill({ x, y, kind }) {
  const t = V3_EDGE_TOKENS[kind];
  const w = t.label.length * 6.5 + 32;
  const h = 22;
  return (
    <g transform={`translate(${x - w/2} ${y - h/2})`}>
      <rect x="0" y="0" width={w} height={h} rx={h/2}
            fill={t.bg} stroke={t.color} strokeWidth="1" />
      <text x={w/2} y={h/2 + 3.5}
            textAnchor="middle"
            fill={t.color}
            fontSize="10.5"
            fontWeight="600"
            style={{ fontFamily: 'var(--font-ui)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
        {t.mark} {t.label}
      </text>
    </g>
  );
}

function V3ToolBtn({ icon, label, active = false, danger = false }) {
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

function V3Minimap() {
  const minX = Math.min(...V3_NODES.map((n) => n.x));
  const minY = Math.min(...V3_NODES.map((n) => n.y));
  const maxX = Math.max(...V3_NODES.map((n) => n.x + n.w));
  const maxY = Math.max(...V3_NODES.map((n) => n.y + n.h));
  const w = maxX - minX, h = maxY - minY;
  return (
    <svg viewBox={`${minX - 30} ${minY - 30} ${w + 60} ${h + 60}`}
         style={{ width: '100%', height: '100%' }}>
      {V3_EDGES.map((e, i) => {
        const a = V3_NODES.find((n) => n.id === e.from);
        const b = V3_NODES.find((n) => n.id === e.to);
        const t = V3_EDGE_TOKENS[e.kind];
        return (
          <line key={i}
            x1={a.x + a.w/2} y1={a.y + a.h/2}
            x2={b.x + b.w/2} y2={b.y + b.h/2}
            stroke={t.color} strokeWidth="3" opacity="0.6" />
        );
      })}
      {V3_NODES.map((n) => {
        const tok = V3_TYPE_TOKENS[n.type];
        return (
          <rect key={n.id}
            x={n.x} y={n.y} width={n.w} height={n.h}
            rx={10}
            fill={tok.fgVar}
            opacity={n.selected ? 1 : 0.7}
            stroke={n.selected ? 'var(--c-accent-600)' : 'none'}
            strokeWidth="14" />
        );
      })}
    </svg>
  );
}

function V3DetailPanel({ nodeId }) {
  const node = V3_NODES.find((n) => n.id === nodeId);
  if (!node) return null;
  const t = V3_TYPE_TOKENS[node.type];
  const s = V3_STATUS_TOKENS[node.status];

  return (
    <aside style={{
      flex: 'none',
      width: 380,
      background: 'var(--c-ink-0)',
      borderInlineStart: 'var(--br-hair)',
      overflow: 'auto',
      display: 'flex', flexDirection: 'column',
    }}>
      <header style={{ padding: '14px 16px 12px', borderBottom: 'var(--br-hair)' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
          <span style={{
            width: 28, height: 28,
            borderRadius: 'var(--r-sm)',
            background: t.bgVar,
            color: t.fgVar,
            display: 'grid', placeItems: 'center',
            flex: 'none',
          }}>
            <Icon name={t.icon} size={14} />
          </span>
          <div style={{ flex: 1 }}>
            <div style={{
              fontSize: 11, fontWeight: 600,
              color: t.fgVar,
              textTransform: 'uppercase',
              letterSpacing: '0.06em',
            }}>EVIDENCE · {t.label}</div>
            <div className="mono" style={{ fontSize: 11, color: 'var(--c-ink-500)', marginTop: 2 }}>176c6b90</div>
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

      <CollapseSectionV3 title="Содержание" defaultOpen>
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
      </CollapseSectionV3>

      <CollapseSectionV3 title="Метаданные" defaultOpen>
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
      </CollapseSectionV3>

      <CollapseSectionV3 title="Опора · 1" defaultOpen>
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
              background: 'var(--c-type-abstract-bg)',
              color: 'var(--c-type-abstract-fg)',
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

          <button className="btn btn-secondary" style={{ justifyContent: 'center' }}>
            <Icon name="external-link" size={12} />
            Перейти к источнику
          </button>
        </div>
      </CollapseSectionV3>

      <div style={{
        marginTop: 'auto',
        padding: '12px 16px',
        borderTop: 'var(--br-hair)',
        display: 'flex', gap: 8,
        background: 'var(--c-ink-0)',
        position: 'sticky', bottom: 0,
      }}>
        <button className="btn btn-primary" style={{ flex: 1, justifyContent: 'center' }}>
          <Icon name="quote" size={12} />
          Привести источник
        </button>
        <button className="btn btn-secondary" title="Добавить свободный источник (вне библиотеки)">
          <Icon name="sparkles" size={12} />
          Свободный
        </button>
      </div>
    </aside>
  );
}

function CollapseSectionV3({ title, defaultOpen, children }) {
  const [open, setOpen] = React.useState(defaultOpen);
  return (
    <section style={{ borderBottom: 'var(--br-hair)' }}>
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

window.TopicGraphV3Board = TopicGraphV3Board;
