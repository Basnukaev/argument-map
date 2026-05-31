/* ================================================================
   PART 4: SETTINGS, ADMIN, STATES (loading/empty/error), MOBILE
   ================================================================ */

/* ----------------------------------------------------------------
   SETTINGS
   ---------------------------------------------------------------- */
const Settings = ({ dark = false }) => (
  <div className="ab" data-theme={dark ? "dark" : "light"}>
    <Header active="Темы" dark={dark}/>
    <div className="settings-page">
      <div className="t-eyebrow">НАСТРОЙКИ</div>
      <h1 className="t-h1" style={{marginTop: 8}}>Настройки приложения</h1>
      <p className="t-muted" style={{marginTop: 8, fontSize: 15}}>
        Изменения применяются мгновенно и сохраняются между сессиями
      </p>

      <div className="settings-section" style={{marginTop: 24}}>
        <div className="settings-row" style={{display: "block", borderBottom: "none"}}>
          <div className="settings-row__title">Тема</div>
          <div className="settings-row__hint" style={{marginBottom: 16}}>
            Светлая, тёмная или по системе. Семантические токены переключаются автоматически
          </div>
          <div className="row" style={{gap: 8}}>
            <button className="btn btn--secondary"><Ic.settings/> Системная</button>
            <button className="btn btn--primary"><Ic.sun/> Светлая</button>
            <button className="btn btn--secondary"><Ic.moon/> Тёмная</button>
          </div>
        </div>
      </div>

      <div className="settings-section">
        <div style={{marginBottom: 16}}>
          <div className="settings-row__title">Пара шрифтов</div>
          <div className="settings-row__hint">Sans-serif для UI и serif для книжных заголовков. Применяется к латинице и кириллице</div>
        </div>
        <div style={{display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12}}>
          {[
            ["Manrope + Source Serif", "дефолт · geometric sans + редакционный serif", true],
            ["Inter + Lora", "самая популярная пара · neutral sans + friendly serif", false],
            ["Inter + Source Serif", "classic editorial", false],
            ["Inter + Literata", "Google Books · book-optimized serif с opsz", false],
            ["IBM Plex Sans + Source Serif", "corporate clean · технический документный feel", false],
            ["IBM Plex Sans + Literata", "minimal tech + book serif", false]
          ].map(([name, desc, sel], i) => (
            <div key={i} className="card" style={{
              padding: 16, cursor: "pointer",
              borderColor: sel ? "var(--brand-500)" : "var(--border-subtle)",
              boxShadow: sel ? "0 0 0 2px oklch(52% 0.20 270 / 0.12)" : "none"
            }}>
              <div className="row row--between" style={{marginBottom: 4}}>
                <div style={{fontWeight: 600, color: "var(--text-strong)"}}>{name}</div>
                <div style={{fontSize: 24, fontWeight: 700, fontFamily: "Source Serif 4"}}>Aa</div>
              </div>
              <div className="t-meta">{desc}</div>
            </div>
          ))}
        </div>
      </div>

      <div className="settings-section">
        <div className="settings-row">
          <div>
            <div className="settings-row__title">Вес заголовков</div>
            <div className="settings-row__hint">Толщина названий книг в карточках. От 300 (тонкий) до 900 (чёрный)</div>
          </div>
          <div className="row" style={{gap: 12}}>
            <div className="slider">
              <div className="slider__track"><div className="slider__fill" style={{width: "50%"}}/></div>
              <div className="slider__thumb" style={{left: "calc(50% - 8px)"}}/>
            </div>
            <span className="slider__val">550</span>
          </div>
        </div>
        <div className="settings-row">
          <div>
            <div className="settings-row__title">Вес UI-текста</div>
            <div className="settings-row__hint">Толщина body-текста в навигации, кнопках, лейблах</div>
          </div>
          <div className="row" style={{gap: 12}}>
            <div className="slider">
              <div className="slider__track"><div className="slider__fill" style={{width: "50%"}}/></div>
              <div className="slider__thumb" style={{left: "calc(50% - 8px)"}}/>
            </div>
            <span className="slider__val">550</span>
          </div>
        </div>
        <div className="settings-row">
          <div>
            <div className="settings-row__title">Плотность чтения</div>
            <div className="settings-row__hint">Множитель vertical rhythm для prose в reader. Меньше — компактнее, больше — больше воздуха</div>
          </div>
          <div className="row" style={{gap: 12}}>
            <div className="slider">
              <div className="slider__track"><div className="slider__fill" style={{width: "50%"}}/></div>
              <div className="slider__thumb" style={{left: "calc(50% - 8px)"}}/>
            </div>
            <span className="slider__val">1.00×</span>
          </div>
        </div>
      </div>

      <div className="settings-section">
        <div style={{marginBottom: 16}}>
          <div className="settings-row__title">Арабский шрифт</div>
          <div className="settings-row__hint">Naskh / Kufi гарнитуры для арабских текстов — названий книг, цитат, bismillah-логотипа</div>
        </div>
        <div style={{display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12}}>
          {[
            ["Scheherazade New", "academic naskh · SIL", true],
            ["Amiri", "дефолт · hand-revival Bulaq press", false],
            ["Noto Naskh Arabic", "Google Noto · modern naskh", false],
            ["Reem Kufi", "geometric kufi · современный display-стиль", false]
          ].map(([name, desc, sel], i) => (
            <div key={i} className="card" style={{
              padding: 14, cursor: "pointer",
              borderColor: sel ? "var(--brand-500)" : "var(--border-subtle)",
              boxShadow: sel ? "0 0 0 2px oklch(52% 0.20 270 / 0.12)" : "none"
            }}>
              <div className="row row--between">
                <div>
                  <div style={{fontWeight: 600, color: "var(--text-strong)", marginBottom: 2}}>{name}</div>
                  <div className="t-meta">{desc}</div>
                </div>
                <div style={{fontFamily: "Scheherazade New", fontSize: 28}} dir="rtl">المعارف</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="settings-section">
        <div className="settings-row">
          <div>
            <div className="settings-row__title">Огласовки (Tashkeel)</div>
            <div className="settings-row__hint">Если включено — харакāт скрываются по умолчанию в reader</div>
          </div>
          <div className="segmented">
            <button className="segmented__opt" aria-pressed="true">Показывать</button>
            <button className="segmented__opt">Скрывать</button>
          </div>
        </div>
        <div className="settings-row">
          <div>
            <div className="settings-row__title">Транслитерация</div>
            <div className="settings-row__hint">Показывать латинскую транслитерацию рядом с арабскими словами</div>
          </div>
          <label className="row" style={{gap: 8, cursor: "pointer"}}>
            <input type="checkbox" defaultChecked/>
            <span>Включить</span>
          </label>
        </div>
        <div className="settings-row">
          <div>
            <div className="settings-row__title">Двуязычный режим узлов</div>
            <div className="settings-row__hint">Узлы могут иметь арабский оригинал и русский перевод</div>
          </div>
          <div className="segmented">
            <button className="segmented__opt">Оригинал</button>
            <button className="segmented__opt">Перевод</button>
            <button className="segmented__opt" aria-pressed="true">Оба</button>
          </div>
        </div>
      </div>

      <div style={{padding: 20, background: "oklch(98% 0.02 28 / 0.5)", border: "1px solid oklch(85% 0.10 28)", borderRadius: 12, marginTop: 24}}>
        <div className="row" style={{gap: 8, marginBottom: 8}}>
          <span style={{color: "var(--status-err)", fontSize: 16}}>⚠</span>
          <strong style={{color: "var(--status-err)"}}>Опасная зона</strong>
        </div>
        <p className="t-muted" style={{margin: "0 0 12px", fontSize: 13}}>
          Сброс удалит все user-preferences с сервера и вернёт значения по умолчанию. Это действие необратимо.
        </p>
        <button className="btn btn--danger">↻ Сбросить все настройки</button>
      </div>
    </div>
  </div>
);

/* ----------------------------------------------------------------
   ADMIN dashboard
   ---------------------------------------------------------------- */
const Admin = ({ dark = false }) => (
  <div className="ab" data-theme={dark ? "dark" : "light"}>
    <Header active="Админ" dark={dark}/>
    <div className="admin-page">
      <div className="topics-head" style={{marginBottom: 24}}>
        <div>
          <div className="t-eyebrow">АДМИН · ИМПОРТ</div>
          <h1 className="t-h1" style={{marginTop: 8}}>Каталог Shamela</h1>
          <p className="t-muted" style={{marginTop: 8, fontSize: 15, maxWidth: 600}}>
            Импорт книг из каталога shamela.ws через desktop-API. Поиск в staging, импорт по одной книге за клик.
          </p>
        </div>
        <div className="row" style={{gap: 8}}>
          <button className="icon-btn"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/><circle cx="5" cy="12" r="1"/></svg></button>
          <button className="btn btn--secondary"><Ic.download/> Из файла</button>
          <button className="btn btn--primary">↻ Синхронизировать каталог</button>
        </div>
      </div>

      <div className="admin-stats">
        <div className="stat-card">
          <div className="stat-card__label">Master version</div>
          <div className="stat-card__value">1261</div>
          <div className="stat-card__sub">Последний sync: 18 мая</div>
        </div>
        <div className="stat-card">
          <div className="stat-card__label">Категорий</div>
          <div className="stat-card__value">40</div>
        </div>
        <div className="stat-card">
          <div className="stat-card__label">Авторов</div>
          <div className="stat-card__value">3 187</div>
        </div>
        <div className="stat-card">
          <div className="stat-card__label">Книг в staging</div>
          <div className="stat-card__value">8 589</div>
        </div>
        <div className="stat-card" style={{background: "oklch(95% 0.05 155)", borderColor: "oklch(80% 0.10 155)"}}>
          <div className="stat-card__label" style={{color: "var(--status-ok)"}}>Замаплено</div>
          <div className="stat-card__value" style={{color: "var(--status-ok)"}}>2 / 8 589</div>
          <div className="stat-card__sub" style={{color: "var(--status-ok)"}}>● v1261 · sync 18 мая</div>
        </div>
      </div>

      <div className="card" style={{marginBottom: 16}}>
        <div className="settings-row__title" style={{marginBottom: 12}}>Поиск в каталоге Shamela</div>
        <div className="search-input" style={{maxWidth: "none"}}>
          <span className="search-icon"><Ic.search/></span>
          <input placeholder="Поиск по названию или id · до 50 результатов"/>
        </div>
      </div>

      <div className="card" style={{padding: 0, overflow: "hidden"}}>
        <div style={{padding: "12px 16px", borderBottom: "1px solid var(--border-subtle)"}}>
          <div className="settings-row__title">Лог импорта</div>
        </div>
        {[
          ["14:22:08", "OK", "sync-master: ничего нового (v 8517)", "ok"],
          ["14:18:45", "OK", "import-book/1503 → 4720 стр., 239 глав", "ok"],
          ["14:18:45", "OK", "map-book/1503 → lib_books/02bcfa43-d269…", "ok"],
          ["14:12:11", "WARN", "import-book/23901 → 6 страниц без printedPage", "warn"],
          ["14:02:54", "ERR", "import-book/77810 → 422: PDF не найден на archive.org", "err"]
        ].map(([t, lvl, msg, kind], i) => (
          <div key={i} className="log-line">
            <span className="log-line__time">{t}</span>
            <span className={`log-line__lvl pill pill--${kind === "ok" ? "ok" : kind === "warn" ? "warn" : "err"}`}>{lvl}</span>
            <span className="log-line__msg">{msg}</span>
          </div>
        ))}
      </div>
    </div>
  </div>
);

/* ----------------------------------------------------------------
   STATES: Loading, Empty, Error
   ---------------------------------------------------------------- */
const StateLoading = ({ dark = false }) => (
  <div className="ab" data-theme={dark ? "dark" : "light"}>
    <Header active="Темы" dark={dark}/>
    <div className="topics-page">
      <div className="topics-head">
        <div>
          <div className="sk sk-line" style={{width: 200, height: 12}}/>
          <div className="sk sk-line" style={{width: 380, height: 32, marginTop: 12}}/>
          <div className="sk sk-line" style={{width: 280, height: 14, marginTop: 12}}/>
        </div>
        <div className="row" style={{gap: 8}}>
          <div className="sk" style={{width: 140, height: 36, borderRadius: 8}}/>
          <div className="sk" style={{width: 140, height: 36, borderRadius: 8}}/>
        </div>
      </div>
      <div className="sk sk-line" style={{width: 380, height: 36, marginTop: 24}}/>
      <div className="topics-grid" style={{marginTop: 24}}>
        {[0,1,2,3,4,5].map(i => (
          <div className="sk-card" key={i}>
            <div className="sk" style={{height: 130, marginBottom: 16}}/>
            <div className="sk sk-line" style={{width: "70%", height: 16}}/>
            <div className="sk sk-line" style={{width: "100%", height: 12}}/>
            <div className="sk sk-line" style={{width: "60%", height: 12}}/>
            <div className="row row--between" style={{marginTop: 16}}>
              <div className="sk sk-line" style={{width: 60, height: 10}}/>
              <div className="sk sk-line" style={{width: 60, height: 10}}/>
            </div>
          </div>
        ))}
      </div>
    </div>
  </div>
);

const StateEmpty = ({ dark = false }) => (
  <div className="ab" data-theme={dark ? "dark" : "light"}>
    <Header active="Темы" dark={dark}/>
    <div className="topics-page">
      <div className="topics-head">
        <div>
          <div className="t-eyebrow">АРГУМЕНТАЦИЯ · ТЕМЫ</div>
          <h1 className="t-h1" style={{marginTop: 8}}>Темы аргументации</h1>
        </div>
      </div>

      <div className="state-block" style={{padding: "120px 24px"}}>
        <div className="state-illus" style={{width: 120, height: 120}}>
          <svg width="64" height="64" viewBox="0 0 80 80" fill="none" stroke="currentColor" strokeWidth="1.5">
            <circle cx="40" cy="20" r="6" fill="var(--node-thesis-bd)" stroke="none"/>
            <circle cx="20" cy="50" r="5" fill="var(--node-evidence-bd)" stroke="none" opacity="0.4"/>
            <circle cx="60" cy="50" r="5" fill="var(--node-evidence-bd)" stroke="none" opacity="0.4"/>
            <line x1="40" y1="26" x2="22" y2="46" stroke="var(--edge-supports)" strokeDasharray="3 3"/>
            <line x1="40" y1="26" x2="58" y2="46" stroke="var(--edge-supports)" strokeDasharray="3 3"/>
          </svg>
        </div>
        <h2 className="state-block__title">Начните свой первый аргумент</h2>
        <p className="state-block__body">
          Темы — это структурированные дискуссии, где аргументы становятся узлами, а связи — отношениями. 
          Создайте корневой вопрос и развивайте дискуссию.
        </p>
        <div className="state-block__actions">
          <button className="btn btn--primary"><Ic.plus/> Создать первую тему</button>
          <button className="btn btn--secondary"><Ic.download/> Импортировать</button>
          <button className="btn btn--ghost">Шаблоны →</button>
        </div>
      </div>
    </div>
  </div>
);

const StateError = ({ kind = "404", dark = false }) => {
  const variants = {
    "404": {
      illus: <Ic.ghost style={{width: 56, height: 56}}/>,
      title: "Здесь ничего нет",
      body: "Возможно, тема удалена или вы зашли по старой ссылке.",
      actions: [["Вернуться к темам", "primary"], ["На главную", "ghost"]]
    },
    "network": {
      illus: <Ic.wifi style={{width: 56, height: 56}}/>,
      title: "Нет соединения",
      body: "Проверьте подключение и попробуйте снова. Мы делаем автоматический повтор через 5с… (3/5)",
      actions: [["Повторить сейчас", "primary"]]
    },
    "permission": {
      illus: <Ic.lock style={{width: 48, height: 48}}/>,
      title: "Тема приватная",
      body: "Запросите доступ у автора или вернитесь к публичным темам.",
      actions: [["Запросить доступ", "primary"], ["Назад", "ghost"]]
    }
  };
  const v = variants[kind];

  return (
    <div className="ab" data-theme={dark ? "dark" : "light"}>
      <Header active="Темы" dark={dark}/>
      <div className="state-block">
        <div className="state-illus" style={{width: 120, height: 120}}>{v.illus}</div>
        <h2 className="state-block__title">{v.title}</h2>
        <p className="state-block__body">{v.body}</p>
        <div className="state-block__actions">
          {v.actions.map(([label, kind], i) => (
            <button key={i} className={`btn btn--${kind}`}>{label}</button>
          ))}
        </div>
      </div>
    </div>
  );
};

/* ----------------------------------------------------------------
   MOBILE versions — proper hit targets, vertical density
   ---------------------------------------------------------------- */
const MobileFrame = ({ children, dark = false, lang = "ru" }) => (
  <div className="ab" data-theme={dark ? "dark" : "light"} style={{background: "#dde0e6", padding: 32, display: "flex", justifyContent: "center", alignItems: "center", height: "100%"}}>
    <div className="mob-frame" dir={lang === "ar" ? "rtl" : "ltr"}>
      {children}
    </div>
  </div>
);

const MobHeader = ({ title, action }) => (
  <div className="mob-header">
    <button className="icon-btn" style={{width: 44, height: 44}}>
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M3 6h18M3 12h18M3 18h18"/></svg>
    </button>
    <Brand size={32}/>
    <button className="icon-btn" style={{width: 44, height: 44}}>
      <Ic.search/>
    </button>
  </div>
);

const MobTopics = ({ dark = false }) => (
  <MobileFrame dark={dark}>
    <MobHeader/>
    <div className="mob-title">
      <div className="t-eyebrow" style={{fontSize: 10}}>АРГУМЕНТАЦИЯ · ТЕМЫ</div>
      <h1>Темы аргументации</h1>
      <p>3 активных</p>
    </div>
    <div style={{padding: "0 16px"}}>
      <div className="search-input" style={{maxWidth: "none", height: 44}}>
        <span className="search-icon"><Ic.search/></span>
        <input placeholder="Поиск..."/>
      </div>
    </div>
    <div className="mob-list">
      {[
        { title: "Дозволенность Мавлида ан-Наби", desc: "Спор о дозволенности празднования", nodes: 11, depth: 11, vis: "private", date: "22 мая" },
        { title: "Тест 1", desc: "1223", nodes: 6, depth: 5, vis: "public", date: "24 мая" },
        { title: "123", desc: "13", nodes: 3, depth: 2, vis: "private", date: "22 мая" }
      ].map((c, i) => (
        <div key={i} className="topic-card" style={{flexDirection: "row", alignItems: "stretch"}}>
          <div style={{
            width: 90, background: "var(--bg-subtle)",
            display: "flex", alignItems: "center", justifyContent: "center",
            borderRight: "1px solid var(--border-subtle)"
          }}>
            <MiniGraph/>
          </div>
          <div style={{flex: 1, padding: 14}}>
            <div className="topic-card__title" style={{fontSize: 15, marginBottom: 4}}>
              {c.title}
              <span style={{color: "var(--text-muted)"}}>{c.vis === "public" ? <Ic.globe/> : <Ic.lock/>}</span>
            </div>
            <div className="topic-card__desc" style={{fontSize: 13, marginBottom: 8}}>{c.desc}</div>
            <div className="row" style={{gap: 12, fontSize: 12, color: "var(--text-meta)"}}>
              <span style={{display:"inline-flex",alignItems:"center",gap:4}}><Ic.network/> {c.nodes}·{c.depth}</span>
              <span style={{display:"inline-flex",alignItems:"center",gap:4}}><Ic.calendar/> {c.date}</span>
            </div>
          </div>
        </div>
      ))}
    </div>
    {/* Floating FAB */}
    <button className="btn btn--primary" style={{
      position: "absolute", bottom: 24, right: 16,
      width: 56, height: 56, borderRadius: 28,
      boxShadow: "var(--shadow-lg)",
      padding: 0
    }}>
      <Ic.plus style={{width: 24, height: 24}}/>
    </button>
  </MobileFrame>
);

const MobGraph = ({ dark = false }) => (
  <MobileFrame dark={dark}>
    <div className="mob-header">
      <button className="icon-btn" style={{width: 44, height: 44}}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="m15 18-6-6 6-6"/></svg>
      </button>
      <div style={{flex: 1, minWidth: 0, paddingLeft: 8}}>
        <div style={{fontSize: 14, fontWeight: 600, color: "var(--text-strong)", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis"}}>
          Дозволенность Мавлида
        </div>
        <div className="t-meta" style={{fontSize: 11}}>11 узлов · 11 уровней</div>
      </div>
      <button className="icon-btn" style={{width: 44, height: 44}}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><circle cx="12" cy="12" r="1"/><circle cx="12" cy="5" r="1"/><circle cx="12" cy="19" r="1"/></svg>
      </button>
    </div>
    {/* Vertical-only layout for mobile */}
    <div style={{flex: 1, padding: 16, background: "var(--bg-canvas)", overflow: "auto", height: "calc(100% - 56px - 60px)"}}>
      <div style={{display: "flex", flexDirection: "column", alignItems: "center", gap: 14}}>
        <div className="node node--thesis" style={{position: "relative", width: "100%"}}>
          <div className="node__head">
            <span className="pill pill--thesis">📣 ТЕЗИС</span>
          </div>
          <div className="node__body">Мавлид является дозволенной практикой</div>
        </div>
        <svg width="2" height="20"><line x1="1" y1="0" x2="1" y2="20" stroke="var(--edge-supports)" strokeWidth="2"/></svg>
        <div className="node node--argument" style={{position: "relative", width: "100%"}}>
          <div className="node__head">
            <span className="pill pill--argument">💬 ДОВОД</span>
            <span style={{fontSize: 11, color: "var(--status-ok)"}}>✓ supports</span>
          </div>
          <div className="node__body">Это «бид'а хасана» — хорошее нововведение</div>
          <div className="node__foot">
            <span className="node__vote">
              <button className="node__vote-btn"><Ic.arrowUp/></button>
              <span style={{fontWeight:600}}>2</span>
              <button className="node__vote-btn"><Ic.arrowDown/></button>
            </span>
          </div>
        </div>
        <svg width="2" height="20"><line x1="1" y1="0" x2="1" y2="20" stroke="var(--edge-supports)" strokeWidth="2"/></svg>
        <div className="node node--evidence" style={{position: "relative", width: "100%"}}>
          <div className="node__head">
            <span className="pill pill--evidence">📜 СВИДЕТЕЛЬСТВО</span>
          </div>
          <div className="node__body">Трактат имама ас-Суюти «Хусн уль-максид»…</div>
        </div>
      </div>
    </div>
    {/* Bottom action bar — thumb zone */}
    <div style={{
      position: "absolute", bottom: 0, left: 0, right: 0,
      padding: "12px 16px",
      background: "var(--bg-card)",
      borderTop: "1px solid var(--border-subtle)",
      display: "flex", gap: 8
    }}>
      <button className="btn btn--secondary" style={{flex: 1, height: 44}}><Ic.plus/> Узел</button>
      <button className="btn btn--secondary" style={{flex: 1, height: 44}}><Ic.link/> Связь</button>
      <button className="btn btn--ghost" style={{width: 44, height: 44, padding: 0}}><Ic.maximize/></button>
    </div>
  </MobileFrame>
);

const MobReader = ({ dark = false }) => (
  <MobileFrame dark={dark}>
    <div className="mob-header">
      <button className="icon-btn" style={{width: 44, height: 44}}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="m15 18-6-6 6-6"/></svg>
      </button>
      <div style={{flex: 1, minWidth: 0, paddingLeft: 8}}>
        <div style={{fontSize: 13, fontWeight: 600, color: "var(--text-strong)", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis", fontFamily: "Scheherazade New"}} dir="rtl">
          تفسير ابن كثير
        </div>
        <div className="t-meta" style={{fontSize: 11}}>стр. 1 / 4710</div>
      </div>
      <button className="icon-btn" style={{width: 44, height: 44}}><Ic.settings/></button>
    </div>
    <div style={{padding: "20px 16px", direction: "rtl", overflow: "auto", height: "calc(100% - 56px - 60px)"}}>
      <h3 style={{fontFamily: "Scheherazade New", fontSize: 22, color: "var(--text-strong)", margin: "0 0 16px", textAlign: "center"}}>
        مقدمة الناشر
      </h3>
      <p style={{fontFamily: "Scheherazade New", fontSize: 19, lineHeight: 1.85, color: "var(--text-base)"}}>
        الحمد لله رب العالمين، والصلاة والسلام على عبده ورسوله محمد، وعلى آله وصحبه وسلَّم تسليمًا كثيرًا.
      </p>
      <p style={{fontFamily: "Scheherazade New", fontSize: 19, lineHeight: 1.85, color: "var(--text-base)"}}>
        أما بعد؛ فإن كتاب «تفسير القرآن العظيم» للحافظ ابن كثير من أحسن وأنفع كتب تفسير القرآن.
      </p>
    </div>
    <div style={{
      position: "absolute", bottom: 0, left: 0, right: 0,
      padding: "10px 16px",
      background: "var(--bg-card)",
      borderTop: "1px solid var(--border-subtle)",
      display: "flex", gap: 8, alignItems: "center"
    }}>
      <button className="btn btn--ghost" style={{width: 44, height: 44, padding: 0}}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="m15 18-6-6 6-6"/></svg>
      </button>
      <div style={{flex: 1, textAlign: "center", fontSize: 13, color: "var(--text-muted)"}}>
        <span className="t-mono">1 / 4710</span>
      </div>
      <button className="btn btn--ghost" style={{width: 44, height: 44, padding: 0}}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="m9 18 6-6-6-6"/></svg>
      </button>
    </div>
  </MobileFrame>
);

Object.assign(window, {
  Settings, Admin,
  StateLoading, StateEmpty, StateError,
  MobileFrame, MobHeader, MobTopics, MobGraph, MobReader
});
