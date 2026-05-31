/* ================================================================
   PART 2: LOGIN, REGISTER, ARGUMENT GRAPH
   ================================================================ */

/* ----------------------------------------------------------------
   LOGIN — fixed dark primary button, clearer hierarchy
   ---------------------------------------------------------------- */
const Login = ({ dark = false }) => (
  <div className="ab" data-theme={dark ? "dark" : "light"} style={{
    display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center",
    padding: "40px 24px", height: "100%"
  }}>
    <Brand size={64} dark={dark}/>
    <p style={{
      marginTop: 16, fontSize: 14, color: "var(--text-muted)",
      letterSpacing: "0.04em"
    }}>Аргумент-карта · платформа для научной работы</p>

    <div className="card" style={{
      marginTop: 40, width: 420, padding: 32, boxShadow: "var(--shadow-md)"
    }}>
      <h2 className="t-h2" style={{fontSize:24}}>Вход</h2>
      <p className="t-muted" style={{marginTop:6, fontSize:14}}>Введите email и пароль</p>

      <div style={{marginTop:24}}>
        <label style={{display:"block", fontSize:13, fontWeight:600, color:"var(--text-strong)", marginBottom:6}}>
          Email <span style={{color:"var(--status-err)"}}>*</span>
        </label>
        <input className="input input--lg" defaultValue="admin@argumentmap.local"/>
      </div>

      <div style={{marginTop:16}}>
        <label style={{display:"block", fontSize:13, fontWeight:600, color:"var(--text-strong)", marginBottom:6}}>
          Пароль <span style={{color:"var(--status-err)"}}>*</span>
        </label>
        <input className="input input--lg" type="password" defaultValue="••••••••"/>
      </div>

      <button className="btn btn--primary btn--lg" style={{width:"100%", marginTop:24}}>
        Войти
      </button>
    </div>

    <p style={{marginTop:20, fontSize:14, color:"var(--text-muted)"}}>
      Нет аккаунта? <a href="#" style={{color:"var(--brand-500)", fontWeight:600, textDecoration:"none"}}>Регистрация</a>
    </p>
  </div>
);

/* ----------------------------------------------------------------
   ARGUMENT GRAPH — main feature with all fixes
   - tooltips on toolbar
   - clarified vote counter with icons + tooltips
   - hash ID hidden in metadata only
   - high-contrast edges in dark
   ---------------------------------------------------------------- */

/* Graph node */
const GNode = ({ type, label, body, x, y, votes = 0, selected = false, w = 200 }) => {
  const labels = {
    question: { ic: <Ic.network/>, txt: "ВОПРОС" },
    thesis:   { ic: "📣", txt: "ТЕЗИС" },
    argument: { ic: "💬", txt: "ДОВОД" },
    evidence: { ic: "📜", txt: "СВИДЕТЕЛЬСТВО" }
  };
  const L = labels[type];
  return (
    <div className={`node node--${type} ${selected ? "node--selected" : ""}`}
         style={{left: x, top: y, width: w}}>
      <div className="node__head">
        <span className="pill" style={{
          background: `var(--node-${type})`,
          color: `var(--node-${type}-ink)`,
          padding: "2px 8px"
        }}>{typeof L.ic === "string" ? L.ic : L.ic} {L.txt}</span>
        <span style={{
          fontSize: 11, color: "var(--text-meta)",
          display: "inline-flex", alignItems: "center", gap: 4
        }} title="Статус: не оценён">
          <span style={{
            width: 6, height: 6, borderRadius: 3,
            background: "var(--text-faint)", display: "inline-block"
          }}/>
          не оценён
        </span>
      </div>
      <div className="node__body">{body}</div>
      <div className="node__foot">
        <span className="node__vote" title="Голоса за / против">
          <button className="node__vote-btn" aria-label="За"><Ic.arrowUp/></button>
          <span style={{minWidth:16, textAlign:"center", fontWeight:600, color:"var(--text-base)"}}>{votes}</span>
          <button className="node__vote-btn" aria-label="Против"><Ic.arrowDown/></button>
        </span>
      </div>
    </div>
  );
};

/* SVG edge between two nodes */
const Edge = ({ x1, y1, x2, y2, kind = "supports", label }) => {
  const dx = x2 - x1, dy = y2 - y1;
  const cx = x1 + dx * 0.5, cy = y1 + dy * 0.5;
  // bezier curve for smoother edge
  const path = `M ${x1} ${y1} C ${x1 + dx*0.4} ${y1}, ${x2 - dx*0.4} ${y2}, ${x2} ${y2}`;
  const icons = { supports: "✓", attacks: "✗", rebuts: "⊘", clarifies: "»" };
  return (
    <>
      <path d={path} className={`edge edge--${kind}`}/>
      {label && (
        <g transform={`translate(${cx},${cy})`}>
          <circle r="11" fill="var(--bg-card)" stroke={`var(--edge-${kind})`} strokeWidth="1.5"/>
          <text x="0" y="4" textAnchor="middle" fontSize="11" fill={`var(--edge-${kind})`} fontWeight="600">{icons[kind] || label}</text>
        </g>
      )}
    </>
  );
};

const Graph = ({ dark = false, selected = false }) => (
  <div className="ab" data-theme={dark ? "dark" : "light"}>
    <Header active="Темы" dark={dark}/>

    <div className="graph-sub">
      <div className="graph-sub__path">
        <button className="icon-btn"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m12 19-7-7 7-7M19 12H5"/></svg></button>
        <span>К списку</span>
        <span style={{color:"var(--text-faint)"}}>/</span>
        <strong>Дозволенность Мавлида ан-Наби</strong>
        <span>Спор о дозволенности празднования дня рождения Пророка ﷺ</span>
      </div>
      <div className="row" style={{gap:8}}>
        <span className="pill pill--neutral"><Ic.lock/> Приватная</span>
        <button className="icon-btn"><Ic.settings/></button>
      </div>
    </div>

    <div className="graph-body" style={{height: "calc(100% - 112px)", position: "relative"}}>
      {/* Toolbar left */}
      <div className="graph-toolbar">
        <button className="icon-btn" data-tooltip="Добавить узел (N)"><Ic.plus/></button>
        <button className="icon-btn" data-tooltip="Создать связь (E)"><Ic.link/></button>
        <button className="icon-btn" data-tooltip="Скрыть неактивные"><Ic.eyeOff/></button>
        <div className="graph-toolbar__sep"/>
        <button className="icon-btn" data-tooltip="Раскладка"><Ic.tree/></button>
        <button className="icon-btn" data-tooltip="Экспорт"><Ic.download/></button>
        <div className="graph-toolbar__sep"/>
        <button className="icon-btn" data-tooltip="Удалить выбранное" style={{color:"var(--status-err)"}}><Ic.trash/></button>
      </div>

      {/* Help (top-right) */}
      <div className="graph-help">
        <span className="graph-help__item"><span className="kbd">⏎⏎</span> детали</span>
        <span className="graph-help__item"><span className="kbd">Del</span> удалить</span>
        <span className="graph-help__item"><span className="kbd">RMB</span> меню</span>
      </div>

      {/* Zoom controls */}
      <div className="graph-zoom">
        <button className="icon-btn" title="Уменьшить"><Ic.zoomOut/></button>
        <button className="icon-btn" title="Увеличить"><Ic.zoomIn/></button>
        <button className="icon-btn" title="По размеру"><Ic.maximize/></button>
      </div>

      {/* Edges SVG */}
      <svg className="edge-svg">
        <Edge x1={260} y1={310} x2={400} y2={290} kind="clarifies" label="»"/>
        <Edge x1={260} y1={485} x2={400} y2={310} kind="neutral"/>
        <Edge x1={260} y1={625} x2={400} y2={520} kind="neutral"/>
        <Edge x1={500} y1={290} x2={650} y2={290} kind="supports" label="✓"/>
        <Edge x1={500} y1={485} x2={650} y2={290} kind="attacks" label="✗"/>
        <Edge x1={500} y1={485} x2={650} y2={485} kind="attacks" label="✗"/>
        <Edge x1={500} y1={680} x2={650} y2={290} kind="supports" label="✓"/>
        <Edge x1={500} y1={680} x2={650} y2={680} kind="supports" label="✓"/>
        <Edge x1={500} y1={835} x2={650} y2={290} kind="supports" label="✓"/>
        <Edge x1={500} y1={835} x2={650} y2={835} kind="supports" label="✓"/>
        <Edge x1={780} y1={290} x2={900} y2={310} kind="supports" label="✓"/>
        <Edge x1={780} y1={485} x2={900} y2={310} kind="rebuts" label="⊘"/>
      </svg>

      {/* Nodes — left column (questions) */}
      <GNode type="question" body="Дозволено ли мусульманам праздновать Мавлид ан-Наби?" x={60} y={290}/>
      <GNode type="question" body="Не приводит ли празднование к харамным практикам (смешение полов, ширк, излишество)?" x={60} y={465}/>
      <GNode type="question" body="asd" x={60} y={605}/>

      {/* Center — thesis + arguments */}
      <GNode type="thesis" body="Мавлид является дозволенной практикой" x={400} y={250} w={250}/>
      <GNode type="argument" body="Это «бид'а хасана» (хорошее нововведение) — отдельная категория в богословии" x={400} y={460} w={250} votes={2}/>
      <GNode type="argument" body="Сахаба и саляф не праздновали Мавлид" x={400} y={650} w={250} votes={3} selected={selected}/>
      <GNode type="argument" body="Любая бид'а в религии есть заблуждение — так сказал Пророк ﷺ" x={400} y={815} w={250} votes={1}/>

      {/* Right column — evidence */}
      <GNode type="evidence" body='Трактат имама ас-Суюти «Хусн уль-максид фи амаль аль-маулид» с богословским разделением бид‘а на пять видов' x={680} y={250} w={250}/>
      <GNode type="evidence" body="Хадис: «Каждое нововведение — бид'а, и каждая бид'а — заблуждение» (Муслим)" x={680} y={460} w={250}/>
      <GNode type="evidence" body="Хадис: «Не уверует никто из вас, пока я не стану ему любимее, чем его отец…» (Бухари, Муслим)" x={680} y={815} w={250}/>

      {/* Mini-map */}
      <div className="graph-minimap">
        <svg width="220" height="140" viewBox="0 0 220 140">
          <rect width="220" height="140" fill="var(--bg-subtle)"/>
          {/* viewport indicator */}
          <rect x="10" y="10" width="200" height="120" fill="none" stroke="var(--brand-500)" strokeWidth="2" strokeDasharray="4 3"/>
          {/* dots */}
          {[[20,40],[20,70],[20,95],[80,30],[80,60],[80,85],[80,115],[140,40],[140,60],[140,100]].map((p,i) => (
            <circle key={i} cx={p[0]} cy={p[1]} r="3.5" fill={i < 3 ? "var(--node-question-bd)" : i < 7 ? "var(--node-thesis-bd)" : "var(--node-evidence-bd)"}/>
          ))}
        </svg>
      </div>

      {/* Selection bar */}
      {selected && (
        <div className="selection-bar">
          <span className="selection-bar__label">
            <span style={{display:"inline-flex",alignItems:"center",gap:6,padding:"4px 8px",background:"var(--brand-100)",color:"var(--brand-700)",borderRadius:6,fontWeight:600}}>
              Выбрано <strong>1</strong>
            </span>
          </span>
          <div className="selection-bar__sep"/>
          <button className="btn btn--ghost"><Ic.trash/> Удалить</button>
          <button className="btn btn--ghost">Изменить статус</button>
          <div className="selection-bar__sep"/>
          <button className="icon-btn"><Ic.x/></button>
        </div>
      )}

      {/* Detail rail */}
      {selected && (
        <div className="detail-rail">
          <div className="detail-head">
            <div className="detail-head__top">
              <div>
                <div className="detail-head__title">
                  <span className="pill pill--argument">💬 ДОВОД</span>
                </div>
              </div>
              <button className="icon-btn"><Ic.x/></button>
            </div>
            <div style={{marginTop:8}}>
              <span className="pill pill--neutral">Не оценён</span>
            </div>
          </div>

          <div className="detail-section">
            <div className="detail-section__head">
              <div className="detail-section__title">Содержание</div>
              <button className="icon-btn" title="Редактировать"><Ic.edit/></button>
            </div>
            <p className="t-body" style={{margin:0, fontSize:14, lineHeight:1.55}}>
              Сахаба и саляф не праздновали Мавлид
            </p>
          </div>

          <div className="detail-section">
            <div className="detail-section__head">
              <div className="detail-section__title">Опора <span className="detail-section__count">0</span></div>
            </div>
            <p className="t-meta" style={{margin:"0 0 12px"}}>К узлу не привязано ни одной опоры</p>
            <button className="btn btn--primary" style={{width:"100%"}}>
              <Ic.book/> Привести источник
            </button>
            <button className="btn btn--ghost" style={{width:"100%", marginTop:6}}>
              <Ic.plus/> Свободный
            </button>
          </div>

          <div className="detail-section">
            <div className="detail-section__title">Метаданные</div>
            <div className="detail-meta-row">
              <span className="detail-meta-row__k">Создан</span>
              <span className="detail-meta-row__v" style={{fontFamily:"inherit"}}>22 мая 2026 · 01:10</span>
            </div>
            <div className="detail-meta-row">
              <span className="detail-meta-row__k">Автор</span>
              <span className="detail-meta-row__v">admin</span>
            </div>
            <div className="detail-meta-row">
              <span className="detail-meta-row__k">ID</span>
              <span className="detail-meta-row__v">9af3e9b7</span>
            </div>
          </div>
        </div>
      )}
    </div>
  </div>
);

Object.assign(window, { Login, Graph, GNode, Edge });
