/* ================================================================
   PART 3: LIBRARY, READER, Q&A, HADITH, COLLECTIONS
   ================================================================ */

/* ----------------------------------------------------------------
   LIBRARY CATALOG
   ---------------------------------------------------------------- */
const BookCard = ({ title, hash, lang = "ar", color = "#1e3a8a", letter, favorited = false }) => (
  <div className="card card--interactive" style={{padding: 0, overflow: "hidden", position: "relative"}}>
    <div style={{
      height: 180, background: color,
      display: "flex", alignItems: "center", justifyContent: "center",
      fontFamily: "Scheherazade New", fontSize: 80, color: "rgba(255,255,255,0.9)",
      borderBottom: "1px solid var(--border-subtle)"
    }}>
      {letter}
    </div>
    {/* Floating actions — appear on hover */}
    <div style={{position: "absolute", top: 8, right: 8, display: "flex", gap: 4}}>
      <button className="icon-btn" style={{background: "rgba(255,255,255,0.95)", boxShadow: "var(--shadow-sm)"}}>
        {favorited ? <Ic.heart fill="currentColor" style={{color: "var(--status-err)"}}/> : <Ic.heart/>}
      </button>
      <button className="icon-btn" style={{background: "rgba(255,255,255,0.95)", boxShadow: "var(--shadow-sm)"}}>
        <Ic.edit/>
      </button>
    </div>
    <div style={{padding: 16}}>
      <div style={{display: "flex", gap: 6, marginBottom: 8}}>
        <span className="pill pill--neutral">КНИГА</span>
        <span className="pill pill--neutral">{lang.toUpperCase()}</span>
        <span className="pill pill--neutral"><Ic.globe/></span>
      </div>
      <div style={{fontFamily: "Scheherazade New", fontSize: 18, color: "var(--text-strong)", lineHeight: 1.4, marginBottom: 4}} dir="rtl">
        {title}
      </div>
      <div className="hash hash--always">{hash}</div>
    </div>
  </div>
);

const Library = ({ dark = false }) => (
  <div className="ab" data-theme={dark ? "dark" : "light"}>
    <Header active="Библиотека" dark={dark}/>
    <div className="topics-page">
      <div className="topics-head">
        <div>
          <div className="t-eyebrow">БИБЛИОТЕКА · КАТАЛОГ</div>
          <h1 className="t-h1" style={{marginTop: 8}}>Библиотека</h1>
          <p className="t-muted" style={{marginTop: 8, fontSize: 15, maxWidth: 600}}>
            Классические труды и пользовательские книги. Найдите источник, читайте PDF, ссылайтесь в аргументах · 2 книг доступно
          </p>
        </div>
        <button className="btn btn--secondary"><Ic.download/> Импорт из Shamela</button>
      </div>

      <div className="topics-filters">
        <div className="search-input">
          <span className="search-icon"><Ic.search/></span>
          <input placeholder="Поиск по названию книги"/>
        </div>
        <div className="dropdown">
          <button className="dropdown__trigger" style={{minWidth: 180}}>
            <span>Все авторы</span><span className="dropdown__chevron"><Ic.chevron/></span>
          </button>
        </div>
        <div className="dropdown" style={{marginLeft: "auto"}}>
          <button className="dropdown__trigger">
            <span>Сначала новые</span><span className="dropdown__chevron"><Ic.chevron/></span>
          </button>
        </div>
      </div>

      <div className="row" style={{gap: 8, marginBottom: 16}}>
        <div className="segmented">
          <button className="segmented__opt" aria-pressed="true">Все</button>
          <button className="segmented__opt">Мои</button>
          <button className="segmented__opt">Разделяемые</button>
          <button className="segmented__opt">Публичные</button>
        </div>
      </div>

      <div className="row" style={{gap: 8, marginBottom: 24, flexWrap: "wrap"}}>
        <div className="segmented">
          <button className="segmented__opt" aria-pressed="true">Все типы</button>
          <button className="segmented__opt">Книга</button>
          <button className="segmented__opt">Сборник хадисов</button>
          <button className="segmented__opt">Коран</button>
          <button className="segmented__opt">Статья</button>
          <button className="segmented__opt">Рукопись</button>
        </div>
      </div>

      <div style={{display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 16}}>
        <BookCard title="التحفة المكية في توضيح أهم القواعد الفقهية" hash="ddcb68d4" color="#1e3a8a" letter="ا" favorited/>
        <BookCard title="تفسير ابن كثير - ط ابن الجوزي" hash="499b4fbf" color="#7c2d12" letter="ت"/>
      </div>
    </div>
  </div>
);

/* ----------------------------------------------------------------
   READER (text mode)
   ---------------------------------------------------------------- */
const Reader = ({ dark = false }) => (
  <div className="ab" data-theme={dark ? "dark" : "light"}>
    <Header active="Библиотека" dark={dark}/>
    <div className="reader-shell" style={{height: "calc(100% - 56px)"}}>
      <aside className="reader-toc">
        <button className="btn btn--ghost" style={{padding: "0 8px", marginBottom: 16, fontSize: 13}}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="m12 19-7-7 7-7M19 12H5"/></svg>
          К списку
        </button>
        <div className="reader-toc__title">Содержание</div>
        <div className="stack" style={{gap: 2}}>
          <div className="reader-toc__item reader-toc__item--active" dir="rtl">مقدمة الناشر</div>
          <div className="reader-toc__item" dir="rtl">مقدمة المحقق</div>
          <div className="reader-toc__item" dir="rtl">مقدمة المؤلف</div>
          <div className="reader-toc__item" dir="rtl">كتاب فضائل القرآن</div>
          <div className="reader-toc__item" dir="rtl">مقدمة مفيدة تذكر في أول التفسير</div>
          <div className="reader-toc__item" dir="rtl">سورة الفاتحة</div>
          <div className="reader-toc__item" dir="rtl">تفسير سورة البقرة</div>
          <div className="reader-toc__item" dir="rtl">سورة آل عمران</div>
          <div className="reader-toc__item" dir="rtl">سورة النساء</div>
        </div>
      </aside>
      <main className="reader-main">
        <div className="reader-head" dir="rtl">
          <div className="row" style={{justifyContent: "space-between", marginBottom: 16}}>
            <span className="pill pill--neutral"><Ic.book/> КНИГА · 4710 стр.</span>
            <span className="pill pill--ok"><Ic.globe/> Публичная</span>
          </div>
          <h2 className="t-h2" style={{fontFamily: "Scheherazade New", fontSize: 32}}>
            تفسير ابن كثير - ط ابن الجوزي
          </h2>
          <div className="stack" style={{gap: 8, marginTop: 16}}>
            <div className="row" style={{gap: 8}}>
              <span className="t-meta" style={{minWidth: 90}}>Автор:</span>
              <span style={{fontFamily: "Scheherazade New", fontSize: 18, color: "var(--text-strong)"}}>ابن كثير</span>
            </div>
            <div className="row" style={{gap: 8}}>
              <span className="t-meta" style={{minWidth: 90}}>Тахкик:</span>
              <span style={{fontFamily: "Scheherazade New", fontSize: 18, color: "var(--text-strong)"}}>حكمت بن بشير بن ياسين</span>
            </div>
            <div className="row" style={{gap: 8}}>
              <span className="t-meta" style={{minWidth: 90}}>Издатель:</span>
              <span style={{fontFamily: "Scheherazade New", fontSize: 16, color: "var(--text-base)"}}>دار ابن الجوزي للنشر والتوزيع · السعودية</span>
            </div>
          </div>
        </div>

        <div className="reader-controls">
          <div className="row" style={{gap: 8}}>
            <button className="btn btn--ghost">← Предыдущая</button>
            <div className="page-input">
              <span className="t-meta">Стр.</span>
              <input className="input" defaultValue="1" style={{width: 60, textAlign: "center"}}/>
              <span className="t-meta">/ 4710</span>
            </div>
            <div className="dropdown">
              <button className="dropdown__trigger" style={{minWidth: 140}} dir="rtl">
                <span style={{fontFamily: "Scheherazade New", fontSize: 16}}>المقدمة</span>
                <span className="dropdown__chevron"><Ic.chevron/></span>
              </button>
            </div>
            <button className="btn btn--ghost">Следующая →</button>
          </div>
          <div className="row" style={{gap: 8}}>
            <div className="segmented">
              <button className="segmented__opt" aria-pressed="true">Текст</button>
              <button className="segmented__opt">PDF</button>
            </div>
            <button className="btn btn--ghost">Без огласовок</button>
          </div>
        </div>

        <div className="reader-body">
          <h2>مقدمة الناشر</h2>
          <p>الحمد لله رب العالمين، والصلاة والسلام على عبده ورسوله محمد، وعلى آله وصحبه وسلَّم تسليمًا كثيرًا.</p>
          <p>أما بعد؛ فإن كتاب «تفسير القرآن العظيم» للحافظ ابن كثير ﷺ من أحسن وأنفع كتب تفسير القرآن، وأوسعها وأكثرها تداولًا وانتشارًا.</p>
          <p>ولا يزال كتابه مقصد اهتمام للراغبين في معرفة تفسير كتاب الله تعالى بالمأثور عن السلف، من تفسير القرآن بالقرآن، وتفسيره بالسنة، وأقوال الصحابة والتابعين.</p>
        </div>
      </main>
    </div>
  </div>
);

/* ----------------------------------------------------------------
   Q&A LIST + DETAIL
   ---------------------------------------------------------------- */
const QAList = ({ dark = false }) => (
  <div className="ab" data-theme={dark ? "dark" : "light"}>
    <Header active="Q&A" dark={dark}/>
    <div className="qa-page">
      <div className="topics-head" style={{marginBottom: 24}}>
        <div>
          <div className="t-eyebrow">Q&A · ОБСУЖДЕНИЯ</div>
          <h1 className="t-h1" style={{marginTop: 8}}>Вопросы и ответы</h1>
          <p className="t-muted" style={{marginTop: 8, fontSize: 15}}>1 вопрос в обсуждении</p>
        </div>
        <button className="btn btn--primary"><Ic.plus/> Задать вопрос</button>
      </div>

      <div className="topics-filters">
        <div className="search-input">
          <span className="search-icon"><Ic.search/></span>
          <input placeholder="Поиск по заголовку"/>
        </div>
        <div className="segmented">
          <button className="segmented__opt" aria-pressed="true">Все</button>
          <button className="segmented__opt">Открытые</button>
          <button className="segmented__opt">Отвечено</button>
          <button className="segmented__opt">Закрытые</button>
        </div>
        <div className="dropdown" style={{marginLeft: "auto"}}>
          <button className="dropdown__trigger">
            <span>Сначала новые</span><span className="dropdown__chevron"><Ic.chevron/></span>
          </button>
        </div>
      </div>

      <div className="qa-item">
        <span className="pill pill--ok">ОТКРЫТ</span>
        <div className="qa-item__main">
          <div className="qa-item__title">11223</div>
          <div className="qa-item__desc">sada</div>
          <div className="qa-item__foot">
            <span style={{display:"inline-flex",alignItems:"center",gap:4}}><Ic.calendar/> 25 мая</span>
            <span style={{display:"inline-flex",alignItems:"center",gap:4}}><Ic.book/> 1 источник</span>
            <span style={{display:"inline-flex",alignItems:"center",gap:4}}>💬 0 ответов</span>
          </div>
        </div>
      </div>
    </div>
  </div>
);

const QADetail = ({ dark = false }) => (
  <div className="ab" data-theme={dark ? "dark" : "light"}>
    <Header active="Q&A" dark={dark}/>
    <div className="qa-page">
      <button className="btn btn--ghost" style={{marginBottom: 16, fontSize: 13}}>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="m12 19-7-7 7-7M19 12H5"/></svg>
        К списку вопросов
      </button>

      <div className="t-eyebrow">Q&A · ВОПРОС</div>
      <div className="row" style={{marginTop: 8, gap: 8}}>
        <span className="pill pill--ok">ОТКРЫТ</span>
        <span className="t-meta">25 мая</span>
      </div>
      <h1 className="t-h1" style={{marginTop: 12}}>11223</h1>
      <p className="t-body" style={{marginTop: 8, fontSize: 16}}>sada</p>

      <div className="row" style={{marginTop: 16, gap: 12}}>
        <span className="t-meta">Статус:</span>
        <div className="segmented">
          <button className="segmented__opt" aria-pressed="true">Открыт</button>
          <button className="segmented__opt">Отвечен</button>
          <button className="segmented__opt">Закрыт</button>
        </div>
      </div>

      <div className="card" style={{marginTop: 24}}>
        <div className="row row--between" style={{marginBottom: 16}}>
          <div className="detail-section__title">Источники <span className="detail-section__count">1</span></div>
          <button className="btn btn--secondary"><Ic.book/> Привести источник</button>
        </div>
        <div className="card" style={{padding: 16, background: "var(--bg-subtle)"}}>
          <div className="row row--between" style={{marginBottom: 8}}>
            <span className="pill pill--neutral"><Ic.book/> ИЗ БИБЛИОТЕКИ</span>
            <span className="t-meta">(без названия)</span>
            <button className="icon-btn"><Ic.trash/></button>
          </div>
          <div style={{padding: 12, background: "var(--bg-card)", borderRadius: 6, border: "1px solid var(--border-subtle)", direction: "rtl"}}>
            <div className="row row--between" style={{marginBottom: 8}}>
              <span className="t-meta">стр. 3</span>
              <span style={{fontFamily: "Scheherazade New", fontSize: 14, color: "var(--text-meta)"}}>المقدمة</span>
            </div>
            <p style={{fontFamily: "Scheherazade New", fontSize: 18, lineHeight: 1.7, margin: 0, color: "var(--text-strong)"}}>
              الحمد لله رب العالمين، والصلاة والسلام ع
            </p>
          </div>
        </div>
      </div>

      <div className="card" style={{marginTop: 24}}>
        <div className="detail-section__title" style={{marginBottom: 12}}>Ответы</div>
        <p className="t-meta">Пока нет ответов</p>

        <div style={{marginTop: 16}}>
          <label style={{display:"block", fontSize:13, fontWeight:600, color:"var(--text-strong)", marginBottom:6}}>
            Опубликовать ответ
          </label>
          <textarea className="textarea" placeholder="Поделитесь своим ответом..." style={{minHeight: 120}}/>
          <div className="row row--between" style={{marginTop: 8}}>
            <span className="t-meta">0 / 10000</span>
            <button className="btn btn--primary" disabled>💬 Опубликовать ответ</button>
          </div>
        </div>
      </div>
    </div>
  </div>
);

/* ----------------------------------------------------------------
   HADITH detail
   ---------------------------------------------------------------- */
const HadithDetail = ({ dark = false }) => (
  <div className="ab" data-theme={dark ? "dark" : "light"}>
    <Header active="Хадисы" dark={dark}/>
    <div className="qa-page" style={{maxWidth: 980}}>
      <button className="btn btn--ghost" style={{marginBottom: 16, fontSize: 13}}>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="m12 19-7-7 7-7M19 12H5"/></svg>
        Хадисы
      </button>

      <div className="row row--between" style={{alignItems: "flex-start"}}>
        <div>
          <div className="t-eyebrow"><Ic.book/> HADITH 1</div>
        </div>
        <span className="pill pill--ok"><Ic.check/> CANONICAL</span>
      </div>

      <h1 style={{fontFamily: "Scheherazade New", fontSize: 36, color: "var(--text-strong)", lineHeight: 1.5, direction: "rtl", textAlign: "right", marginTop: 16}}>
        إنما الأعمال بالنيات وإنما لكل امرئٍ ما نوى
      </h1>

      <div className="card" style={{marginTop: 24}}>
        <div className="detail-section__title" style={{marginBottom: 16}}>
          Иснады <span className="detail-section__count">1</span>
        </div>
        <div style={{background: "var(--bg-subtle)", padding: 16, borderRadius: 8, border: "1px solid var(--border-subtle)"}}>
          <div className="row" style={{marginBottom: 12}}>
            <span className="pill pill--thesis">Основной</span>
            <span className="pill pill--ok">SAHIH</span>
          </div>
          {[
            ["0", "سمعت", "f7af1fa8", "Услышал от"],
            ["1", "عن", "89220bf9", "От"],
            ["2", "حدثني", "9fac16cb", "Рассказал мне"],
            ["3", "أخبرنا", "45e8606f", "Сообщил нам"],
            ["4", "حدثنا", "e485ce63", "Рассказали нам"]
          ].map(([idx, ar, hash, hint]) => (
            <div key={idx} className="isnad-link" title={hint}>
              <span className="isnad-link__idx">#{idx}</span>
              <span className="isnad-link__name" dir="rtl">{ar}</span>
              <span className="isnad-link__hash">{hash}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="card" style={{marginTop: 16}}>
        <div className="detail-section__title" style={{marginBottom: 16}}>
          Тексты вариаций <span className="detail-section__count">2</span>
        </div>
        <div style={{background: "var(--bg-subtle)", padding: 16, borderRadius: 8, marginBottom: 12, border: "1px solid var(--border-subtle)"}}>
          <span className="pill pill--thesis" style={{marginBottom: 12, display: "inline-flex"}}>Основной</span>
          <p style={{fontFamily: "Scheherazade New", fontSize: 22, color: "var(--text-strong)", direction: "rtl", textAlign: "right", margin: "12px 0", lineHeight: 1.7}}>
            إنما الأعمال بالنيات وإنما لكل امرئٍ ما نوى
          </p>
          <p style={{color: "var(--text-base)", fontSize: 15, fontFamily: "Source Serif 4", lineHeight: 1.6, margin: 0}}>
            Дела оцениваются по намерениям, и каждому достанется лишь то, что он намеревался получить.
          </p>
        </div>
        <div style={{background: "var(--bg-subtle)", padding: 16, borderRadius: 8, border: "1px solid var(--border-subtle)"}}>
          <p style={{fontFamily: "Scheherazade New", fontSize: 22, color: "var(--text-strong)", direction: "rtl", textAlign: "right", margin: "0 0 12px", lineHeight: 1.7}}>
            الأعمال بالنية
          </p>
          <p style={{color: "var(--text-base)", fontSize: 15, fontFamily: "Source Serif 4", lineHeight: 1.6, margin: "0 0 4px"}}>
            Дела — по намерению
          </p>
          <p className="t-meta" style={{fontStyle: "italic"}}>Краткая версия opener</p>
        </div>
      </div>
    </div>
  </div>
);

/* ----------------------------------------------------------------
   COLLECTIONS
   ---------------------------------------------------------------- */
const Collections = ({ dark = false, empty = false }) => (
  <div className="ab" data-theme={dark ? "dark" : "light"}>
    <Header active="Коллекции" dark={dark}/>
    <div className="topics-page">
      <button className="btn btn--ghost" style={{marginBottom: 16, fontSize: 13}}>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="m12 19-7-7 7-7M19 12H5"/></svg>
        Библиотека
      </button>
      <div className="t-eyebrow"><Ic.heart/> МОИ КОЛЛЕКЦИИ</div>
      <h1 className="t-h1" style={{marginTop: 8}}>Мои коллекции</h1>
      <p className="t-muted" style={{marginTop: 8}}>Книги, добавленные в избранное и тематические коллекции</p>

      <div style={{display: "grid", gridTemplateColumns: "280px 1fr", gap: 24, marginTop: 24}}>
        <aside className="stack" style={{gap: 4}}>
          <div className="row row--between" style={{padding: "10px 12px", background: "var(--brand-100)", borderRadius: 8, color: "var(--brand-700)", fontWeight: 600, fontSize: 14}}>
            <span>Избранное</span>
            <span style={{background: "var(--bg-card)", padding: "1px 8px", borderRadius: 999, fontSize: 12}}>{empty ? 0 : 1}</span>
          </div>
          <button className="btn btn--ghost" style={{justifyContent: "flex-start", padding: "8px 12px", fontWeight: 500}}>
            <Ic.plus/> Создать коллекцию
          </button>
        </aside>

        <div>
          {empty ? (
            <div className="card" style={{textAlign: "center", padding: "64px 24px"}}>
              <div className="state-illus"><Ic.heart style={{width: 40, height: 40}}/></div>
              <h3 className="t-h3" style={{marginBottom: 8}}>Коллекция «Избранное» пуста</h3>
              <p className="t-muted" style={{margin: "0 auto 20px", maxWidth: 320}}>
                Добавьте книги через ♡ на карточке в библиотеке.
              </p>
              <button className="btn btn--primary"><Ic.book/> Перейти в библиотеку</button>
            </div>
          ) : (
            <div style={{display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 12}}>
              <div className="card" style={{padding: 12, display: "flex", alignItems: "center", gap: 12}}>
                <div style={{width: 40, height: 56, background: "#1e3a8a", color: "white", display: "flex", alignItems: "center", justifyContent: "center", borderRadius: 4, fontFamily: "Scheherazade New", fontSize: 24}}>ا</div>
                <div style={{flex: 1, minWidth: 0}}>
                  <div style={{fontFamily: "Scheherazade New", fontSize: 16, color: "var(--text-strong)", lineHeight: 1.3, marginBottom: 2}} dir="rtl">التحفة المكية</div>
                  <div className="t-meta">25.05.2026</div>
                </div>
                <button className="icon-btn"><Ic.trash/></button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  </div>
);

Object.assign(window, { BookCard, Library, Reader, QAList, QADetail, HadithDetail, Collections });
