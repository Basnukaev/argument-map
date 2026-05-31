/* ================================================================
   ARGUMENT MAP — ARTBOARDS
   All redesigned screens. Each artboard is a self-contained design.
   ================================================================ */

const { useState } = React;

/* ---------- ICONS (shared) ---------- */
const Ic = {
  search: (p) => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>,
  chevron: (p) => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="m6 9 6 6 6-6"/></svg>,
  plus: (p) => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M12 5v14M5 12h14"/></svg>,
  link: (p) => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>,
  eyeOff: (p) => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M9.88 9.88a3 3 0 1 0 4.24 4.24"/><path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68"/><path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61"/><path d="m2 2 20 20"/></svg>,
  tree: (p) => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><rect x="3" y="14" width="6" height="6"/><rect x="15" y="14" width="6" height="6"/><rect x="9" y="4" width="6" height="6"/><path d="M12 10v4M6 14v-2h12v2"/></svg>,
  download: (p) => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m7 10 5 5 5-5"/><path d="M12 15V3"/></svg>,
  trash: (p) => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>,
  bell: (p) => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/></svg>,
  sun: (p) => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/></svg>,
  moon: (p) => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>,
  settings: (p) => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/></svg>,
  globe: (p) => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg>,
  lock: (p) => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>,
  users: (p) => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>,
  network: (p) => <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><rect x="9" y="2" width="6" height="6" rx="1"/><rect x="2" y="16" width="6" height="6" rx="1"/><rect x="16" y="16" width="6" height="6" rx="1"/><path d="M12 8v3M5 16v-2h14v2"/></svg>,
  calendar: (p) => <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/></svg>,
  arrowUp: (p) => <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="m18 15-6-6-6 6"/></svg>,
  arrowDown: (p) => <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="m6 9 6 6 6-6"/></svg>,
  check: (p) => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M20 6 9 17l-5-5"/></svg>,
  x: (p) => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M18 6 6 18M6 6l12 12"/></svg>,
  zoomIn: (p) => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5M11 8v6M8 11h6"/></svg>,
  zoomOut: (p) => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5M8 11h6"/></svg>,
  maximize: (p) => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/></svg>,
  book: (p) => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg>,
  edit: (p) => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4z"/></svg>,
  heart: (p) => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>,
  hash: (p) => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M4 9h16M4 15h16M10 3 8 21M16 3l-2 18"/></svg>,
  wifi: (p) => <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M5 12.55a11 11 0 0 1 14.08 0"/><path d="M1.42 9a16 16 0 0 1 21.16 0"/><path d="M8.53 16.11a6 6 0 0 1 6.95 0"/><circle cx="12" cy="20" r="1"/><path d="m2 2 20 20"/></svg>,
  ghost: (p) => <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M9 10h.01M15 10h.01M12 2a8 8 0 0 0-8 8v12l3-3 2.5 2 2.5-2 2.5 2 2.5-2 3 3V10a8 8 0 0 0-8-8z"/></svg>,
};

/* ---------- BRAND LOGO ---------- */
const Brand = ({ size = 36, dark = false }) => (
  <div style={{
    width: size, height: size, borderRadius: 8,
    background: dark ? "oklch(28% 0.08 270)" : "oklch(52% 0.20 270)",
    display: "flex", alignItems: "center", justifyContent: "center",
    flexShrink: 0
  }}>
    <span style={{ color: "white", fontSize: size*0.45, fontFamily: "Scheherazade New", fontWeight: 600 }}>﷽</span>
  </div>
);

/* ---------- HEADER (reusable) ---------- */
const Header = ({ active = "Темы", dark = false, lang = "ru" }) => {
  const items = lang === "ar"
    ? ["الإدارة", "المجموعات", "الأحاديث", "الأسئلة والأجوبة", "المكتبة", "المواضيع"]
    : ["Темы", "Библиотека", "Q&A", "Хадисы", "Коллекции", "Админ"];
  return (
    <div className="header" dir={lang === "ar" ? "rtl" : "ltr"}>
      <Brand dark={dark}/>
      <nav className="header__nav">
        {items.map(it => (
          <a key={it} href="#" aria-current={it === active ? "page" : undefined}>{it}</a>
        ))}
      </nav>
      <div className="header__utility">
        <div className="header__utility-group">
          <button className="icon-btn" title="Поиск (Alt)"><Ic.search/></button>
          <span className="kbd">⌘K</span>
        </div>
        <div className="vdivider"/>
        <div className="header__utility-group">
          <div className="segmented">
            <button className="segmented__opt" aria-pressed={lang==="ru"}>RU</button>
            <button className="segmented__opt" aria-pressed={lang==="ar"}>AR</button>
          </div>
        </div>
        <div className="vdivider"/>
        <div className="header__utility-group">
          <button className="icon-btn" title="Настройки"><Ic.settings/></button>
          <button className="icon-btn" title="Тема">{dark ? <Ic.moon/> : <Ic.sun/>}</button>
          <button className="icon-btn" title="Уведомления"><Ic.bell/></button>
        </div>
        <div className="avatar">AD</div>
      </div>
    </div>
  );
};

/* ================================================================
   ARTBOARD 1: FOUNDATIONS
   ================================================================ */
const Foundations = ({ dark = false }) => (
  <div className="ab" data-theme={dark ? "dark" : "light"}>
    <div className="found-grid">
      <div className="found-section">
        <h3>Color tokens — Brand & status</h3>
        <div className="swatch-grid">
          {[["brand-500","Primary",{bg:"var(--brand-500)"}],
            ["brand-700","Hover/Active",{bg:"var(--brand-700)"}],
            ["status-ok","OK",{bg:"var(--status-ok)"}],
            ["status-warn","Warn",{bg:"var(--status-warn)"}],
            ["status-err","Error",{bg:"var(--status-err)"}],
            ["status-info","Info",{bg:"var(--status-info)"}]
          ].map(([n,l,s]) => (
            <div className="swatch" key={n}>
              <div className="swatch__color" style={{background:s.bg}}/>
              <div className="swatch__meta">
                <div className="swatch__name">{l}</div>
                <div className="swatch__val">--{n}</div>
              </div>
            </div>
          ))}
        </div>

        <h3 style={{marginTop:28}}>Node colors (graph)</h3>
        <div className="swatch-grid">
          {[["node-question","QUESTION","var(--node-question)","var(--node-question-bd)"],
            ["node-thesis","THESIS","var(--node-thesis)","var(--node-thesis-bd)"],
            ["node-argument","ARGUMENT","var(--node-argument)","var(--node-argument-bd)"],
            ["node-evidence","EVIDENCE","var(--node-evidence)","var(--node-evidence-bd)"]
          ].map(([n,l,bg,bd]) => (
            <div className="swatch" key={n} style={{borderColor:bd}}>
              <div className="swatch__color" style={{background:bg}}/>
              <div className="swatch__meta">
                <div className="swatch__name">{l}</div>
                <div className="swatch__val">--{n}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="found-section">
        <h3>Type scale</h3>
        <div className="type-row"><span className="type-row__label">3xl/700</span><span style={{fontSize:36,fontWeight:700,color:"var(--text-strong)"}}>Темы аргументации</span></div>
        <div className="type-row"><span className="type-row__label">2xl/700</span><span style={{fontSize:28,fontWeight:700,color:"var(--text-strong)"}}>Дозволенность Мавлида</span></div>
        <div className="type-row"><span className="type-row__label">xl/600</span><span style={{fontSize:22,fontWeight:600,color:"var(--text-strong)"}}>Заголовок секции</span></div>
        <div className="type-row"><span className="type-row__label">base/400</span><span style={{fontSize:15,color:"var(--text-base)"}}>Тело текста · 4.5:1 контраст</span></div>
        <div className="type-row"><span className="type-row__label">sm/muted</span><span style={{fontSize:13,color:"var(--text-muted)"}}>Вторичный — meta, captions</span></div>
        <div className="type-row"><span className="type-row__label">serif</span><span className="t-serif" style={{fontSize:18,color:"var(--text-strong)"}}>Source Serif · для длинного чтения</span></div>
        <div className="type-row"><span className="type-row__label">ar</span><span style={{fontFamily:"Scheherazade New",fontSize:22,color:"var(--text-strong)"}}>إنما الأعمال بالنيات</span></div>

        <h3 style={{marginTop:28}}>Buttons — все states</h3>
        <div className="button-row">
          <button className="btn btn--primary">Primary</button>
          <button className="btn btn--primary" disabled>Disabled (0.45 opacity)</button>
          <button className="btn btn--secondary">Secondary</button>
          <button className="btn btn--ghost">Ghost</button>
          <button className="btn btn--danger">Danger</button>
        </div>

        <h3 style={{marginTop:28}}>Pills</h3>
        <div className="button-row">
          <span className="pill pill--question"><Ic.network/> ВОПРОС</span>
          <span className="pill pill--thesis">📣 ТЕЗИС</span>
          <span className="pill pill--argument">💬 ДОВОД</span>
          <span className="pill pill--evidence">📜 СВИДЕТЕЛЬСТВО</span>
          <span className="pill pill--ok">SAHIH</span>
          <span className="pill pill--warn">WARN</span>
          <span className="pill pill--err">ERR</span>
        </div>
      </div>
    </div>
  </div>
);

/* ================================================================
   ARTBOARD 2: TOPICS LIST
   ================================================================ */
const TopicCard = ({ title, desc, depth, nodes, vis = "private", date, hash, locale = "ru" }) => {
  const visIcon = vis === "public" ? <Ic.globe/> : vis === "shared" ? <Ic.users/> : <Ic.lock/>;
  return (
    <div className="topic-card">
      <div className="topic-card__thumb">
        <MiniGraph/>
        <div className="topic-card__stats">
          <Ic.network/> {nodes}·{depth}
        </div>
      </div>
      <div className="topic-card__body">
        <div className="topic-card__title">
          {title}
          <span className="topic-card__vis" style={{color:"var(--text-muted)"}}>{visIcon}</span>
        </div>
        <div className="topic-card__desc">{desc}</div>
      </div>
      <div className="topic-card__foot">
        <span style={{display:"inline-flex",alignItems:"center",gap:4}}><Ic.calendar/> {date}</span>
        <span className="hash">{hash}</span>
      </div>
    </div>
  );
};

const MiniGraph = () => (
  <svg width="120" height="90" viewBox="0 0 120 90">
    <line x1="60" y1="20" x2="30" y2="50" stroke="var(--edge-supports)" strokeWidth="1.5"/>
    <line x1="60" y1="20" x2="60" y2="50" stroke="var(--edge-supports)" strokeWidth="1.5"/>
    <line x1="60" y1="20" x2="90" y2="50" stroke="var(--edge-attacks)" strokeWidth="1.5"/>
    <line x1="30" y1="50" x2="20" y2="75" stroke="var(--edge-neutral)" strokeWidth="1"/>
    <line x1="60" y1="50" x2="60" y2="75" stroke="var(--edge-supports)" strokeWidth="1.5"/>
    <circle cx="60" cy="20" r="6" fill="var(--node-thesis-bd)"/>
    <circle cx="30" cy="50" r="5" fill="var(--node-evidence-bd)"/>
    <circle cx="60" cy="50" r="5" fill="var(--node-evidence-bd)"/>
    <circle cx="90" cy="50" r="5" fill="var(--node-evidence-bd)"/>
    <circle cx="20" cy="75" r="4" fill="var(--node-question-bd)" opacity="0.6"/>
    <circle cx="60" cy="75" r="4" fill="var(--node-evidence-bd)"/>
  </svg>
);

const TopicsList = ({ dark = false, lang = "ru" }) => {
  const T = lang === "ar" ? {
    eyebrow: "الحجاج · المواضيع", title: "مواضيع الحجاج",
    sub: "نقاشات منظمة على هيئة رسم بياني · 3 نشط",
    importBtn: "استيراد موضوع", createBtn: "إنشاء موضوع",
    searchPh: "بحث بالموضوع أو الوصف", sortLbl: "الترتيب", sortVal: "الأحدث أولاً",
    cards: [
      { title: "Тест 1", desc: "1223", date: "24 مايو", hash: "2a65afd7", nodes: 6, depth: 5, vis: "public" },
      { title: "Дозволенность Мавлида ан-Наби", desc: "Спор о дозволенности празднования дня рождения Пророка ﷺ", date: "22 مايو", hash: "2284853b", nodes: 11, depth: 11, vis: "private" },
      { title: "123", desc: "13", date: "22 مايو", hash: "d19cf51b", nodes: 3, depth: 2, vis: "private" }
    ]
  } : {
    eyebrow: "АРГУМЕНТАЦИЯ · ТЕМЫ", title: "Темы аргументации",
    sub: "Структурированные дискуссии в виде графа · 3 активных",
    importBtn: "Импортировать", createBtn: "Создать тему",
    searchPh: "Поиск по теме или описанию", sortLbl: "Сортировка", sortVal: "Сначала новые",
    cards: [
      { title: "Тест 1", desc: "1223", date: "24 мая", hash: "2a65afd7", nodes: 6, depth: 5, vis: "public" },
      { title: "Дозволенность Мавлида ан-Наби", desc: "Спор о дозволенности празднования дня рождения Пророка ﷺ", date: "22 мая", hash: "2284853b", nodes: 11, depth: 11, vis: "private" },
      { title: "123", desc: "13", date: "22 мая", hash: "d19cf51b", nodes: 3, depth: 2, vis: "private" }
    ]
  };

  return (
    <div className="ab" data-theme={dark ? "dark" : "light"} dir={lang === "ar" ? "rtl" : "ltr"}>
      <Header active={lang==="ar" ? "المواضيع" : "Темы"} dark={dark} lang={lang}/>
      <div className="topics-page">
        <div className="topics-head">
          <div>
            <div className="t-eyebrow">{T.eyebrow}</div>
            <h1 className="t-h1" style={{marginTop:8}}>{T.title}</h1>
            <p className="t-muted" style={{marginTop:8, fontSize:15}}>{T.sub}</p>
          </div>
          <div className="row" style={{gap:8}}>
            <button className="btn btn--secondary"><Ic.download/> {T.importBtn}</button>
            <button className="btn btn--primary"><Ic.plus/> {T.createBtn}</button>
          </div>
        </div>

        <div className="topics-filters">
          <div className="search-input">
            <span className="search-icon"><Ic.search/></span>
            <input placeholder={T.searchPh}/>
          </div>
          <div className="row" style={{gap:8}}>
            <span className="t-meta">{T.sortLbl}</span>
            <div className="dropdown">
              <button className="dropdown__trigger">
                <span>{T.sortVal}</span>
                <span className="dropdown__chevron"><Ic.chevron/></span>
              </button>
            </div>
          </div>
        </div>

        <div className="topics-grid">
          {T.cards.map((c, i) => <TopicCard key={i} {...c} locale={lang}/>)}
        </div>
      </div>
    </div>
  );
};

Object.assign(window, { Ic, Brand, Header, MiniGraph, TopicCard, Foundations, TopicsList });
