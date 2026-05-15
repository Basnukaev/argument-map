// Redesigned TopicListPage.
// Topics are the unit of work — give them more presence than book cards.
// Mini-graph preview uses real design-system colors so the user can read
// "balance of standing/disputed claims" at a glance.

const SAMPLE_TOPICS = [
  {
    id: 't1',
    title: 'Дозволенность мавлида',
    description: 'Разбор позиций классических мазхабов: дозволенность торжественного отмечания дня рождения Пророка ﷺ.',
    nodes: 18, edges: 24,
    distribution: { standing: 5, disputed: 8, refuted: 3, unverified: 2 },
    date: '12 мая',
    activity: 12,
  },
  {
    id: 't2',
    title: 'Виды бид‘а: лугавийа и шар‘ийа',
    description: 'Различие между языковым и шариатским пониманием «нововведения» у Ибн Таймийи и аш-Шатыби.',
    nodes: 11, edges: 14,
    distribution: { standing: 7, disputed: 2, refuted: 1, unverified: 1 },
    date: '8 мая',
    activity: 5,
  },
  {
    id: 't3',
    title: 'Обязательность гусля для джум‘а',
    description: 'Хадис «Гусль пятницы — обязанность» — буквальное прочтение vs толкование «сильно желательно».',
    nodes: 9, edges: 12,
    distribution: { standing: 3, disputed: 4, refuted: 1, unverified: 1 },
    date: '3 мая',
    activity: 0,
  },
  {
    id: 't4',
    title: 'Манхадж ас-салаф в усуль ат-тафсир',
    description: 'Методология тафсира классического периода: иерархия источников и пределы иджтихада.',
    nodes: 27, edges: 41,
    distribution: { standing: 14, disputed: 8, refuted: 2, unverified: 3 },
    date: '28 апр',
    activity: 18,
    pinned: true,
  },
  {
    id: 't5',
    title: 'Положение об обязанности молитвы за неоставлением',
    description: '',
    nodes: 4, edges: 3,
    distribution: { standing: 1, disputed: 0, refuted: 0, unverified: 3 },
    date: '24 апр',
    activity: 0,
  },
  {
    id: 't6',
    title: 'Шарх «Манхадж ат-талибин» — модерация знатоков',
    description: 'Открытая тема: правки и комментарии участников.',
    nodes: 0, edges: 0,
    distribution: { standing: 0, disputed: 0, refuted: 0, unverified: 0 },
    date: 'сегодня',
    activity: 1,
    empty: true,
  },
];

function TopicListBoard() {
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

      <main style={{ flex: 1, overflow: 'auto', padding: '24px 32px 48px' }}>
        <div style={{
          display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between',
          gap: 20, marginBottom: 6,
        }}>
          <div>
            <h1 style={{
              margin: 0,
              fontFamily: 'var(--font-serif)',
              fontWeight: 600, fontSize: 28,
              letterSpacing: '-0.01em',
            }}>Темы дискуссии</h1>
            <div style={{ fontSize: 13, color: 'var(--c-ink-500)', marginTop: 4 }}>
              Карты аргументации классических вопросов фикха и усуль ·{' '}
              <span className="mono" style={{ color: 'var(--c-ink-700)', fontWeight: 600 }}>
                {SAMPLE_TOPICS.length}
              </span>{' '}
              активных
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-secondary">
              <Icon name="file-text" size={13} />
              Экспорт
            </button>
            <button className="btn btn-primary">
              <Icon name="sparkles" size={13} />
              Новая тема
            </button>
          </div>
        </div>

        {/* Tabs + filter */}
        <div style={{
          display: 'flex', alignItems: 'center', gap: 16, marginTop: 16, marginBottom: 16,
          borderBottom: 'var(--br-hair)',
        }}>
          {['Все темы', 'Мои', 'Закреплённые', 'Архив'].map((t, i) => (
            <button key={t} style={{
              padding: '8px 0',
              fontSize: 13, fontWeight: 500,
              color: i === 0 ? 'var(--c-ink-900)' : 'var(--c-ink-500)',
              borderBottom: i === 0 ? '2px solid var(--c-accent-600)' : '2px solid transparent',
              marginBottom: -1,
            }}>
              {t}{' '}
              <span className="mono" style={{
                fontSize: 11, color: 'var(--c-ink-400)', fontWeight: 400, marginLeft: 4,
              }}>{i === 0 ? SAMPLE_TOPICS.length : i === 1 ? 3 : i === 2 ? 1 : 12}</span>
            </button>
          ))}
          <span style={{ flex: 1 }} />
          <div style={{
            display: 'flex', alignItems: 'center', gap: 6,
            height: 30, padding: '0 10px', width: 280,
            background: 'var(--c-bg-elevated)',
            border: 'var(--br-soft)',
            borderRadius: 'var(--r-sm)',
          }}>
            <Icon name="search" size={13} style={{ color: 'var(--c-ink-500)' }} />
            <span style={{ flex: 1, fontSize: 12, color: 'var(--c-ink-400)' }}>
              Найти по названию
            </span>
            <span className="kbd">/</span>
          </div>
        </div>

        {/* Grid */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))',
          gap: 16,
        }}>
          {SAMPLE_TOPICS.map((t) => <TopicCard key={t.id} topic={t} />)}
        </div>
      </main>
    </div>
  );
}

function TopicCard({ topic }) {
  return (
    <a style={{
      display: 'flex', flexDirection: 'column',
      background: 'var(--c-bg-elevated)',
      border: topic.pinned ? '1px solid var(--c-accent-600)' : 'var(--br-hair)',
      borderRadius: 'var(--r-lg)',
      overflow: 'hidden',
      cursor: 'pointer',
    }}>
      {/* mini-graph preview */}
      <div style={{
        position: 'relative',
        aspectRatio: '5 / 2',
        background: topic.pinned
          ? 'linear-gradient(135deg, var(--c-accent-50), var(--c-paper))'
          : 'var(--c-paper)',
        borderBottom: 'var(--br-hair)',
      }}>
        {topic.empty ? (
          <div style={{
            position: 'absolute', inset: 0,
            display: 'grid', placeItems: 'center',
            color: 'var(--c-ink-400)',
            fontSize: 11, fontStyle: 'italic',
          }}>
            (граф пуст)
          </div>
        ) : (
          <MiniGraph distribution={topic.distribution} />
        )}
        {topic.pinned && (
          <div style={{
            position: 'absolute', top: 8, insetInlineStart: 8,
            display: 'inline-flex', alignItems: 'center', gap: 4,
            padding: '2px 6px', borderRadius: 3,
            background: 'var(--c-accent-600)', color: 'var(--c-ink-0)',
            fontSize: 10, fontWeight: 600,
          }}>
            <Icon name="pin" size={10} /> Закреплено
          </div>
        )}
        <div style={{
          position: 'absolute', top: 8, insetInlineEnd: 8,
          display: 'flex', gap: 4,
        }}>
          <span style={{
            padding: '2px 6px', borderRadius: 3,
            background: 'var(--c-bg-elevated)',
            border: 'var(--br-hair)',
            fontSize: 10, fontWeight: 600,
            color: 'var(--c-ink-700)',
            fontFamily: 'var(--font-mono)',
            display: 'inline-flex', alignItems: 'center', gap: 4,
          }}>
            <Icon name="graph" size={10} />
            {topic.nodes}·{topic.edges}
          </span>
        </div>
      </div>

      <div style={{ padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: 6 }}>
        <h3 style={{
          margin: 0,
          fontFamily: 'var(--font-serif)',
          fontSize: 16, fontWeight: 600, lineHeight: 1.3,
          color: 'var(--c-ink-900)',
          letterSpacing: '-0.005em',
        }}>{topic.title}</h3>

        {topic.description && (
          <p style={{
            margin: 0,
            fontSize: 13,
            lineHeight: 1.5,
            color: 'var(--c-ink-600)',
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
          }}>{topic.description}</p>
        )}

        <div style={{
          marginTop: 6,
          paddingTop: 10,
          borderTop: 'var(--br-hair)',
          display: 'flex', alignItems: 'center', gap: 8,
          fontSize: 11,
          color: 'var(--c-ink-500)',
        }}>
          <StatusBreakdown distribution={topic.distribution} />
          <span style={{ flex: 1 }} />
          {topic.activity > 0 && (
            <span style={{
              display: 'inline-flex', alignItems: 'center', gap: 3,
              color: 'var(--c-accent-600)', fontWeight: 500,
            }}>
              <span style={{
                width: 6, height: 6, borderRadius: '50%',
                background: 'var(--c-accent-600)',
              }} />
              {topic.activity}
            </span>
          )}
          <span>{topic.date}</span>
        </div>
      </div>
    </a>
  );
}

// A miniature graph rendered as inline SVG, position-deterministic from
// the distribution counts. This is the "decorative-but-informative" version
// of the original topic card preview.
function MiniGraph({ distribution }) {
  const total = Object.values(distribution).reduce((a, b) => a + b, 0);
  // Build a fixed set of slots; map distribution into them
  const positions = [
    { x: 60, y: 30, r: 10, isRoot: true },
    { x: 30, y: 60, r: 7 },
    { x: 90, y: 60, r: 7 },
    { x: 120, y: 45, r: 6 },
    { x: 150, y: 70, r: 7 },
    { x: 180, y: 35, r: 6 },
    { x: 210, y: 65, r: 7 },
    { x: 240, y: 40, r: 6 },
    { x: 50, y: 92, r: 5 },
    { x: 90, y: 95, r: 5 },
    { x: 140, y: 95, r: 5 },
    { x: 200, y: 92, r: 5 },
  ];
  const lines = [
    [0, 1], [0, 2], [0, 3], [2, 4], [3, 5], [5, 6], [6, 7],
    [1, 8], [1, 9], [4, 10], [6, 11],
  ];

  // Assign status colors deterministically based on distribution
  const list = [];
  Object.entries(distribution).forEach(([status, count]) => {
    for (let i = 0; i < count; i++) list.push(status);
  });
  const statusFor = (idx) => {
    if (idx === 0) return 'standing';
    return list[idx % list.length] || 'unverified';
  };
  const colorFor = (status) => ({
    standing:   'var(--c-ok-500)',
    disputed:   'var(--c-warn-500)',
    refuted:    'var(--c-err-500)',
    unverified: 'var(--c-ink-300)',
  }[status]);

  if (total === 0) return null;

  const visibleCount = Math.min(positions.length, Math.max(1, total));

  return (
    <svg viewBox="0 0 280 120" preserveAspectRatio="xMidYMid slice"
         style={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }}
         aria-hidden="true">
      {lines.map(([a, b], i) => {
        if (a >= visibleCount || b >= visibleCount) return null;
        return (
          <line key={i}
            x1={positions[a].x} y1={positions[a].y}
            x2={positions[b].x} y2={positions[b].y}
            stroke="var(--c-ink-300)" strokeWidth="1" strokeOpacity="0.5" />
        );
      })}
      {positions.slice(0, visibleCount).map((p, i) => {
        const c = colorFor(statusFor(i));
        return (
          <g key={i}>
            <circle cx={p.x} cy={p.y} r={p.r + 2} fill="var(--c-bg-elevated)" />
            <circle cx={p.x} cy={p.y} r={p.r} fill={c} stroke="var(--c-bg-elevated)" strokeWidth="1" />
            {p.isRoot && <circle cx={p.x} cy={p.y} r={p.r - 4} fill="var(--c-bg-elevated)" opacity="0.7" />}
          </g>
        );
      })}
    </svg>
  );
}

function StatusBreakdown({ distribution }) {
  const items = [
    { k: 'standing', label: distribution.standing, color: 'var(--c-ok-500)' },
    { k: 'disputed', label: distribution.disputed, color: 'var(--c-warn-500)' },
    { k: 'refuted',  label: distribution.refuted,  color: 'var(--c-err-500)' },
  ].filter((it) => it.label > 0);

  if (!items.length) return <span style={{ color: 'var(--c-ink-400)' }}>—</span>;

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      {items.map((it) => (
        <span key={it.k} style={{
          display: 'inline-flex', alignItems: 'center', gap: 4,
          fontFamily: 'var(--font-mono)', fontSize: 11,
        }}>
          <span style={{
            width: 6, height: 6, borderRadius: '50%', background: it.color,
          }} />
          {it.label}
        </span>
      ))}
    </div>
  );
}

window.TopicListBoard = TopicListBoard;
