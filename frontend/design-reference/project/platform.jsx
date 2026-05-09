// =============================================================================
// PLATFORM PIVOT — sections 27-30 (Must)
// 27 Platform shell · 28 BookListPage · 29 BookReader · 30 CitationPicker
// =============================================================================

// ---------- shared bits ----------

const PlatformPivotIntro = () => (
  <div className="bg-gradient-to-b from-indigo-50/60 to-white border-y border-indigo-100">
    <div className="max-w-[1380px] mx-auto px-10 py-12">
      <div className="flex items-baseline gap-4 mb-6">
        <span className="text-[10px] font-mono tracking-[0.2em] text-indigo-600 uppercase">part ii · platform pivot</span>
        <span className="h-px flex-1 bg-indigo-200" />
        <span className="text-[10px] font-mono tracking-[0.2em] text-indigo-400 uppercase">sections 27 → 35</span>
      </div>
      <div className="grid grid-cols-12 gap-8">
        <div className="col-span-7">
          <h2 className="text-[40px] leading-[1.05] font-bold tracking-tight text-slate-900 text-balance">
            Argument Map больше не один продукт.<br/>
            Это <span className="italic font-serif text-indigo-700">платформа</span> для исламской науки.
          </h2>
          <p className="mt-5 text-[14px] leading-[1.7] text-slate-600 text-pretty max-w-[60ch]">
            В корне — <strong className="text-slate-900">библиотека книг и цитирования</strong>. Поверх растут
            приложения: argument-map (граф аргументации), Q&A с источниками, всё последующее. Главный
            принцип — <em className="text-slate-900">точная атрибуция</em>: любая цитата ссылается на
            конкретное место в книге библиотеки.
          </p>
        </div>
        <div className="col-span-5">
          <div className="rounded-lg border border-indigo-200/70 bg-white p-5 shadow-[0_1px_0_rgba(15,23,42,0.04),0_8px_24px_-12px_rgba(79,70,229,0.18)]">
            <div className="text-[10px] font-mono tracking-wider text-indigo-600 uppercase mb-3">архитектура</div>
            <div className="space-y-2.5">
              {[
                { app: "argument-map", desc: "граф аргументации", live: true },
                { app: "library", desc: "books + citation", live: true, anchor: true },
                { app: "qa", desc: "fiqh / aqida Q&A", soon: true },
              ].map((r) => (
                <div key={r.app} className="flex items-center gap-3 text-[12px]">
                  <div className={cx(
                    "h-1.5 w-1.5 rounded-full",
                    r.anchor ? "bg-indigo-600 ring-2 ring-indigo-200" : r.soon ? "bg-slate-300" : "bg-emerald-500"
                  )} />
                  <code className="font-mono text-[12px] text-slate-900 w-32">{r.app}</code>
                  <span className="text-slate-500 flex-1">{r.desc}</span>
                  {r.anchor && <span className="text-[10px] font-mono text-indigo-600 uppercase tracking-wider">anchor</span>}
                  {r.soon && <span className="text-[10px] font-mono text-slate-400 uppercase tracking-wider">soon</span>}
                  {!r.anchor && !r.soon && <span className="text-[10px] font-mono text-emerald-600 uppercase tracking-wider">live</span>}
                </div>
              ))}
            </div>
            <div className="mt-4 pt-4 border-t border-slate-100 text-[11px] text-slate-500 leading-relaxed">
              Один React SPA в <code className="font-mono text-[10.5px] text-slate-700">frontend/</code>,
              общий <code className="font-mono text-[10.5px] text-slate-700">Header</code>, разные pages.
              Не monorepo с <code className="font-mono text-[10.5px] text-slate-700">apps/*</code> — отложено.
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
);

// ===== 27 — Platform Shell ===================================================

const PlatformHeaderMock = ({ active = "topics", state = "default" }) => {
  const tabs = [
    { key: "topics", label: "Темы", icon: "Network", count: 12 },
    { key: "library", label: "Библиотека", icon: "Library", count: 47 },
    { key: "qa", label: "Q&A", icon: "MessageSquareQuote", soon: true },
  ];
  return (
    <div className="bg-white border border-slate-200 rounded-lg shadow-[0_1px_2px_rgba(15,23,42,0.04)]">
      <div className="h-14 px-5 flex items-center gap-1">
        {/* logo */}
        <div className="flex items-center gap-2.5 pr-5 border-r border-slate-200 mr-4">
          <div className="h-7 w-7 rounded-md bg-gradient-to-br from-indigo-600 to-indigo-700 grid place-items-center text-white shadow-[0_1px_2px_rgba(79,70,229,0.4)]">
            <I.Network size={15} strokeWidth={2} />
          </div>
          <div className="flex flex-col leading-none">
            <span className="text-[13px] font-bold tracking-tight text-slate-900">Аргумент</span>
            <span className="text-[9px] font-mono tracking-[0.18em] text-slate-500 uppercase">platform · v0.4</span>
          </div>
        </div>
        {/* nav */}
        <nav className="flex items-center gap-0.5">
          {tabs.map((t) => {
            const Ic = I[t.icon];
            const isActive = state === "default" && active === t.key;
            const isHover = state === "hover" && t.key === "library";
            const isDisabled = t.soon;
            return (
              <div key={t.key} className="relative group">
                <button
                  className={cx(
                    "h-9 px-3 rounded-md inline-flex items-center gap-2 text-[13px] font-medium transition-colors",
                    isActive && "bg-indigo-50 text-indigo-700",
                    isHover && "bg-slate-100 text-slate-900",
                    !isActive && !isHover && !isDisabled && "text-slate-600 hover:text-slate-900 hover:bg-slate-50",
                    isDisabled && "text-slate-400 cursor-not-allowed"
                  )}
                  disabled={isDisabled}
                >
                  <Ic size={14} strokeWidth={1.75} />
                  {t.label}
                  {t.count != null && (
                    <span className={cx(
                      "ml-1 text-[10.5px] font-mono tabular px-1.5 h-[18px] inline-flex items-center rounded",
                      isActive ? "bg-indigo-100 text-indigo-700" : "bg-slate-100 text-slate-500"
                    )}>{t.count}</span>
                  )}
                  {isDisabled && (
                    <span className="ml-1 text-[9.5px] font-mono uppercase tracking-wider px-1.5 h-[18px] inline-flex items-center rounded bg-slate-100 text-slate-400 border border-slate-200">soon</span>
                  )}
                </button>
                {isActive && <div className="absolute -bottom-px left-3 right-3 h-[2px] bg-indigo-600 rounded-t" />}
                {isDisabled && state === "default" && t.key === "qa" && (
                  <div className="absolute top-full mt-2 left-1/2 -translate-x-1/2 whitespace-nowrap text-[11px] bg-slate-900 text-white px-2 py-1 rounded shadow-md opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-10">
                    скоро · в разработке
                    <div className="absolute -top-1 left-1/2 -translate-x-1/2 h-2 w-2 bg-slate-900 rotate-45" />
                  </div>
                )}
              </div>
            );
          })}
        </nav>
        {/* center: optional context breadcrumb */}
        <div className="flex-1 flex items-center justify-center px-6">
          {active === "library" && (
            <div className="text-[11.5px] text-slate-500 flex items-center gap-1.5">
              <span>Библиотека</span>
              <I.ChevronRight size={11} strokeWidth={1.5} className="text-slate-400" />
              <span className="font-naskh text-[14px] text-slate-700" dir="rtl">صحيح البخاري</span>
              <I.ChevronRight size={11} strokeWidth={1.5} className="text-slate-400" />
              <span className="text-slate-700">стр. 41</span>
            </div>
          )}
        </div>
        {/* right cluster */}
        <div className="flex items-center gap-1">
          <button className="h-9 px-2.5 rounded-md inline-flex items-center gap-1.5 text-[12px] font-medium text-slate-600 hover:bg-slate-50">
            <span className="font-mono text-[11px] uppercase tracking-wider">RU</span>
            <I.ChevronDown size={11} strokeWidth={2} className="text-slate-400" />
          </button>
          <div className="h-5 w-px bg-slate-200 mx-1" />
          <button className="h-9 w-9 rounded-md inline-flex items-center justify-center text-slate-500 hover:bg-slate-50 hover:text-slate-700 relative">
            <I.AlertCircle size={15} strokeWidth={1.75} />
            <span className="absolute top-2 right-2 h-1.5 w-1.5 rounded-full bg-amber-500 ring-2 ring-white" />
          </button>
          <button className="h-9 pl-1 pr-2 rounded-md inline-flex items-center gap-2 hover:bg-slate-50">
            <div className="h-7 w-7 rounded-full bg-gradient-to-br from-emerald-500 to-emerald-700 grid place-items-center text-white text-[11px] font-bold">МА</div>
            <I.ChevronDown size={11} strokeWidth={2} className="text-slate-400" />
          </button>
        </div>
      </div>
    </div>
  );
};

const PlatformShellSection = () => (
  <Section
    id="platform-shell"
    title="Platform shell · глобальная панель"
    kicker="27 — platform shell"
    hint="Заменит topbar TopicListPage. Логотип + 3 NavLink (Темы / Библиотека / Q&A placeholder) + lang + аватар. Активный таб подчёркивается, disabled показывает tooltip «скоро»."
  >
    <div className="space-y-6">
      {/* states stack */}
      <div className="space-y-3">
        <div className="flex items-center gap-3">
          <span className="text-[10px] font-mono uppercase tracking-wider text-slate-500 w-32">default · /topics</span>
          <span className="h-px flex-1 bg-slate-200" />
          <span className="text-[11px] text-slate-500">«Темы» активная — она и есть текущий argument-map</span>
        </div>
        <PlatformHeaderMock active="topics" state="default" />
      </div>
      <div className="space-y-3">
        <div className="flex items-center gap-3">
          <span className="text-[10px] font-mono uppercase tracking-wider text-slate-500 w-32">hover · /books</span>
          <span className="h-px flex-1 bg-slate-200" />
          <span className="text-[11px] text-slate-500">наведение на «Библиотеку», breadcrumb с открытой книгой в центре</span>
        </div>
        <PlatformHeaderMock active="library" state="hover" />
      </div>
      <div className="space-y-3">
        <div className="flex items-center gap-3">
          <span className="text-[10px] font-mono uppercase tracking-wider text-slate-500 w-32">disabled · soon</span>
          <span className="h-px flex-1 bg-slate-200" />
          <span className="text-[11px] text-slate-500">Q&A пока недоступен · tooltip раскрыт</span>
        </div>
        <div className="relative">
          <PlatformHeaderMock active="topics" state="default" />
          <div className="absolute top-[58px] left-[345px] z-20">
            <div className="text-[11px] bg-slate-900 text-white px-2 py-1 rounded shadow-md whitespace-nowrap">
              скоро · в разработке
              <div className="absolute -top-1 left-1/2 -translate-x-1/2 h-2 w-2 bg-slate-900 rotate-45" />
            </div>
          </div>
        </div>
      </div>

      {/* avatar dropdown variant */}
      <div className="grid grid-cols-12 gap-6">
        <div className="col-span-7">
          <div className="text-[11px] font-mono uppercase tracking-wider text-slate-500 mb-3">avatar dropdown · открытое меню</div>
          <div className="relative">
            <PlatformHeaderMock active="topics" state="default" />
            <div className="absolute right-4 top-[58px] z-20 w-72 bg-white border border-slate-200 rounded-lg shadow-[0_8px_28px_-8px_rgba(15,23,42,0.18)] py-1.5">
              <div className="px-3 py-2.5 flex items-center gap-3 border-b border-slate-100">
                <div className="h-9 w-9 rounded-full bg-gradient-to-br from-emerald-500 to-emerald-700 grid place-items-center text-white text-[12.5px] font-bold">МА</div>
                <div className="flex-1 min-w-0">
                  <div className="text-[13px] font-semibold text-slate-900 truncate">Муса аль-Ансари</div>
                  <div className="text-[11px] text-slate-500 truncate">m.ansari@example.org</div>
                </div>
              </div>
              {[
                { ic: "User", l: "Профиль" },
                { ic: "Settings", l: "Настройки" },
                { ic: "BookOpen", l: "Мои коллекции", k: "12" },
                { ic: "ShieldCheck", l: "Admin · библиотека", muted: true },
              ].map((r) => {
                const Ic = I[r.ic];
                return (
                  <button key={r.l} className={cx("w-full px-3 h-8 flex items-center gap-2.5 text-[12.5px] hover:bg-slate-50", r.muted ? "text-slate-500" : "text-slate-700")}>
                    <Ic size={13} strokeWidth={1.5} className="text-slate-400" />
                    <span className="flex-1 text-left">{r.l}</span>
                    {r.k && <span className="text-[10.5px] font-mono text-slate-400">{r.k}</span>}
                  </button>
                );
              })}
              <div className="my-1 border-t border-slate-100" />
              <button className="w-full px-3 h-8 flex items-center gap-2.5 text-[12.5px] text-slate-700 hover:bg-slate-50">
                <I.Lock size={13} strokeWidth={1.5} className="text-slate-400" />
                Выйти
              </button>
            </div>
          </div>
        </div>
        <div className="col-span-5">
          <div className="text-[11px] font-mono uppercase tracking-wider text-slate-500 mb-3">notification flyout</div>
          <div className="relative">
            <PlatformHeaderMock active="library" state="default" />
            <div className="absolute right-[88px] top-[58px] z-20 w-80 bg-white border border-slate-200 rounded-lg shadow-[0_8px_28px_-8px_rgba(15,23,42,0.18)] overflow-hidden">
              <div className="px-3 h-9 flex items-center justify-between border-b border-slate-100">
                <span className="text-[12px] font-semibold text-slate-900">Уведомления</span>
                <span className="text-[10.5px] font-mono text-slate-400">3 новых</span>
              </div>
              {[
                { tone: "amber", ic: "AlertCircle", t: "Shamela каталог обновлён", b: "+ 47 новых книг доступны для импорта", time: "5м" },
                { tone: "emerald", ic: "CheckCircle", t: "OCR завершён", b: "«Манускрипт Мадинский» — 218 стр.", time: "1ч" },
                { tone: "indigo", ic: "MessageSquareQuote", t: "Цитата привязана", b: "Узел «Запрет» получил источник из Бухари", time: "вчера" },
              ].map((n, i) => {
                const Ic = I[n.ic];
                return (
                  <div key={i} className={cx("px-3 py-2.5 flex gap-2.5 border-b border-slate-100 last:border-0", i === 0 && "bg-amber-50/40")}>
                    <Ic size={14} strokeWidth={1.75} className={cx("mt-0.5 shrink-0",
                      n.tone === "amber" ? "text-amber-600" : n.tone === "emerald" ? "text-emerald-600" : "text-indigo-600"
                    )} />
                    <div className="flex-1 min-w-0">
                      <div className="text-[12px] font-medium text-slate-900">{n.t}</div>
                      <div className="text-[11px] text-slate-500 mt-0.5 truncate">{n.b}</div>
                    </div>
                    <span className="text-[10px] font-mono text-slate-400 shrink-0">{n.time}</span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </div>
  </Section>
);

// ===== 28 — BookListPage =====================================================

const BOOK_TYPE_TONES = {
  Коран:    { bg: "bg-emerald-100", text: "text-emerald-800", dot: "bg-emerald-500" },
  Хадисы:   { bg: "bg-indigo-100",  text: "text-indigo-800",  dot: "bg-indigo-500" },
  Книга:    { bg: "bg-slate-100",   text: "text-slate-800",   dot: "bg-slate-500" },
  PDF:      { bg: "bg-amber-100",   text: "text-amber-800",   dot: "bg-amber-500" },
  Скан:     { bg: "bg-rose-100",    text: "text-rose-800",    dot: "bg-rose-500" },
};

const BOOKS = [
  { id: "qrn", title_ar: "الْقُرْآنُ الْكَرِيمُ", title_ru: "Священный Коран", type: "Коран", lang: "ar", author_ar: "—", author_ru: "—", chapters: 114, pages: 604, src: "quran.com", featured: true },
  { id: "buk", title_ar: "صَحِيحُ الْبُخَارِيِّ", title_ru: "Сахих аль-Бухари", type: "Хадисы", lang: "ar", author_ar: "مُحَمَّدُ بْنُ إِسْمَاعِيلَ الْبُخَارِيُّ", author_ru: "Мухаммад б. Исмаил аль-Бухари", died: "256 г.х.", chapters: 97, pages: 4127, src: "shamela · 1681", featured: true },
  { id: "msl", title_ar: "صَحِيحُ مُسْلِمٍ", title_ru: "Сахих Муслим", type: "Хадисы", lang: "ar", author_ar: "مُسْلِمُ بْنُ الْحَجَّاجِ", author_ru: "Муслим б. аль-Хаджжадж", died: "261 г.х.", chapters: 56, pages: 1893, src: "shamela · 1727" },
  { id: "rys", title_ar: "رِيَاضُ الصَّالِحِينَ", title_ru: "Сады праведных", type: "Хадисы", lang: "ar", author_ar: "النَّوَوِيُّ", author_ru: "ан-Навави", died: "676 г.х.", chapters: 19, pages: 632, src: "shamela · 1031" },
  { id: "ibk", title_ar: "تَفْسِيرُ ابْنِ كَثِيرٍ", title_ru: "Тафсир Ибн Касира", type: "Книга", lang: "ar", author_ar: "ابْنُ كَثِيرٍ الدِّمَشْقِيُّ", author_ru: "Ибн Касир ад-Димашки", died: "774 г.х.", chapters: 114, pages: 4416, src: "shamela · 6481" },
  { id: "ibk-ru", title_ar: null, title_ru: "Тафсир Ибн Касира · перевод", type: "Книга", lang: "ru", author_ar: null, author_ru: "Ибн Касир (пер. Кулиев Э.)", chapters: 114, pages: 2104, src: "user upload" },
  { id: "fbr", title_ar: "فَتْحُ الْبَارِي", title_ru: "Фатх аль-Бари", type: "Книга", lang: "ar", author_ar: "ابْنُ حَجَرٍ الْعَسْقَلَانِيُّ", author_ru: "Ибн Хаджар аль-‘Аскаляни", died: "852 г.х.", chapters: 13, pages: 7124, src: "shamela · 1672" },
  { id: "mvt", title_ar: "الْمُوَطَّأُ", title_ru: "аль-Муватта", type: "Хадисы", lang: "ar", author_ar: "مَالِكُ بْنُ أَنَسٍ", author_ru: "Малик б. Анас", died: "179 г.х.", chapters: 61, pages: 1024, src: "shamela · 22799" },
  { id: "msc", title_ar: null, title_ru: "Манускрипт · Мадина 1287 г.х.", type: "Скан", lang: "ar", author_ar: null, author_ru: "неустановлен", chapters: null, pages: 218, src: "user scan · OCR pending" },
  { id: "abd", title_ar: null, title_ru: "Хукмы Аллаха · конспект лекций", type: "PDF", lang: "ru", author_ar: null, author_ru: "А. Г. Тагирьянов", chapters: 8, pages: 142, src: "user PDF" },
];

const BookCard = ({ book, hover = false, decorative = "shamela" }) => {
  const tone = BOOK_TYPE_TONES[book.type];
  const isAr = book.lang === "ar";
  return (
    <div className={cx(
      "group relative bg-white border border-slate-200 rounded-lg overflow-hidden transition-all",
      hover ? "shadow-[0_8px_24px_-8px_rgba(15,23,42,0.18)] -translate-y-0.5 border-slate-300" : "shadow-[0_1px_2px_rgba(15,23,42,0.04)] hover:shadow-[0_4px_14px_-4px_rgba(15,23,42,0.12)]"
    )}>
      {/* cover area */}
      <div className={cx(
        "relative h-32 border-b border-slate-200 overflow-hidden",
        book.type === "Коран" && "bg-gradient-to-br from-emerald-50 to-emerald-100",
        book.type === "Хадисы" && "bg-gradient-to-br from-indigo-50 to-indigo-100",
        book.type === "Книга" && "bg-gradient-to-br from-slate-50 to-slate-100",
        book.type === "PDF" && "bg-gradient-to-br from-amber-50 to-amber-100",
        book.type === "Скан" && "bg-gradient-to-br from-rose-50 to-rose-100"
      )}>
        {book.type !== "PDF" && book.type !== "Скан" ? (
          // decorative arabic glyph
          <div className="absolute inset-0 grid place-items-center font-naskh select-none opacity-25" style={{ fontSize: 76, lineHeight: 1, color: "rgb(15 23 42 / 0.6)" }} dir="rtl">
            {book.id === "qrn" ? "ﷲ" : book.id === "buk" ? "خ" : book.id === "msl" ? "م" : book.id === "rys" ? "ن" : book.id === "ibk" ? "ت" : book.id === "fbr" ? "ف" : book.id === "mvt" ? "م" : "ك"}
          </div>
        ) : book.type === "Скан" ? (
          <div className="absolute inset-0 p-3">
            <div className="h-full w-full bg-white/80 border border-rose-200 rounded shadow-[inset_0_0_0_1px_rgba(0,0,0,0.02)] relative overflow-hidden">
              <div className="absolute inset-0 p-2 space-y-1" dir="rtl">
                {Array.from({length:8}).map((_,i)=>(
                  <div key={i} className="h-1 bg-rose-200/70 rounded-full" style={{width: `${65+Math.random()*30}%`, marginLeft: i%2 ? '0' : 'auto'}}/>
                ))}
              </div>
              <div className="absolute top-1 right-1 text-[8px] font-mono text-rose-700/60 bg-white/80 px-1 rounded">scan · ٢١٨</div>
            </div>
          </div>
        ) : (
          <div className="absolute inset-0 p-3">
            <div className="h-full w-full bg-white border border-amber-300 rounded relative">
              <div className="h-full p-2 space-y-1">
                {Array.from({length:6}).map((_,i)=>(
                  <div key={i} className="h-1 bg-amber-200 rounded" style={{width: `${50+Math.random()*40}%`}}/>
                ))}
              </div>
              <div className="absolute top-1 right-1 text-[8px] font-mono text-amber-700 bg-white/80 px-1 rounded">PDF</div>
            </div>
          </div>
        )}
        {/* type pill */}
        <div className="absolute top-2 left-2 flex items-center gap-1.5">
          <div className={cx("h-[20px] px-1.5 inline-flex items-center gap-1 rounded text-[10.5px] font-medium", tone.bg, tone.text)}>
            <span className={cx("h-1.5 w-1.5 rounded-full", tone.dot)} />
            {book.type}
          </div>
          <div className="h-[20px] px-1.5 inline-flex items-center rounded bg-white/90 backdrop-blur text-[10px] font-mono uppercase tracking-wider text-slate-700 border border-white">
            {book.lang}
          </div>
        </div>
        {book.featured && (
          <div className="absolute top-2 right-2 h-[20px] px-1.5 inline-flex items-center gap-1 rounded bg-white/90 backdrop-blur text-[10px] font-medium text-indigo-700 border border-white">
            <I.Pin size={9} strokeWidth={2} />
            закреплено
          </div>
        )}
      </div>
      {/* meta */}
      <div className="p-3.5">
        {isAr && book.title_ar ? (
          <>
            <div dir="rtl" className="font-naskh arabic-text text-[20px] leading-[1.3] font-semibold text-slate-900 line-clamp-1">{book.title_ar}</div>
            <div className="text-[11.5px] text-slate-500 mt-0.5 line-clamp-1">{book.title_ru}</div>
          </>
        ) : (
          <div className="text-[14px] leading-snug font-semibold text-slate-900 line-clamp-2 min-h-[40px]">{book.title_ru}</div>
        )}
        {/* author */}
        <div className="mt-2.5 pt-2.5 border-t border-slate-100">
          {book.author_ar ? (
            <div className="flex items-baseline justify-between gap-2">
              <div dir="rtl" className="font-naskh text-[13px] text-slate-700 truncate">{book.author_ar}</div>
              <div className="text-[10.5px] font-mono text-slate-400 shrink-0">{book.died}</div>
            </div>
          ) : null}
          <div className={cx("text-[11px] text-slate-500 truncate", book.author_ar && "italic mt-0.5")}>{book.author_ru}</div>
        </div>
        {/* counts */}
        <div className="mt-2.5 flex items-center gap-3 text-[10.5px] font-mono text-slate-500">
          {book.chapters != null && (
            <span className="inline-flex items-center gap-1"><I.ListTree size={10} strokeWidth={1.75} />{book.chapters} гл.</span>
          )}
          <span className="inline-flex items-center gap-1"><I.FileText size={10} strokeWidth={1.75} />{book.pages.toLocaleString("ru-RU")} стр.</span>
          <span className="ml-auto text-[9.5px] uppercase tracking-wider text-slate-400 truncate">{book.src}</span>
        </div>
      </div>
    </div>
  );
};

const BookListToolbar = ({ filter = null, addOpen = false }) => (
  <div className="bg-white border border-slate-200 rounded-lg overflow-visible">
    <div className="h-14 px-4 flex items-center gap-2 border-b border-slate-100">
      <div className="flex items-center gap-2 mr-2">
        <h2 className="text-[15px] font-bold text-slate-900">Библиотека</h2>
        <span className="text-[11px] font-mono text-slate-400">/books</span>
      </div>
      <span className="text-[11px] text-slate-500">{BOOKS.length} книг · 47 в каталоге shamela ожидают импорта</span>
      <div className="flex-1" />
      <div className="relative w-72">
        <I.Search size={13} strokeWidth={1.75} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
        <input className="h-8 w-full pl-8 pr-3 text-[12px] rounded-md border border-slate-200 bg-slate-50 focus:bg-white focus:border-indigo-300 focus:ring-1 focus:ring-indigo-200 outline-none placeholder:text-slate-400" placeholder="Найти книгу или автора…" />
      </div>
      <div className="relative">
        <button className={cx(
          "h-8 px-3 inline-flex items-center gap-1.5 rounded-md text-[12px] font-medium",
          addOpen ? "bg-indigo-600 text-white" : "bg-slate-900 text-white hover:bg-slate-800"
        )}>
          <I.Plus size={13} strokeWidth={2} />
          Добавить книгу
          <I.ChevronDown size={11} strokeWidth={2} className="opacity-70" />
        </button>
        {addOpen && (
          <div className="absolute right-0 top-full mt-1.5 w-72 bg-white border border-slate-200 rounded-lg shadow-[0_8px_28px_-8px_rgba(15,23,42,0.18)] py-1.5 z-20">
            <div className="px-3 py-1.5 text-[10px] font-mono uppercase tracking-wider text-slate-400">Импорт каталога</div>
            {[
              { ic: "Library", t: "Из shamela.ws", d: "по ID или поиску, admin", admin: true },
              { ic: "BookOpen", t: "Quran-import", d: "quran.com → 114 сур" },
              { ic: "ScrollText", t: "Sunnah-import", d: "sunnah.com → 9 сводов" },
            ].map((r) => {
              const Ic = I[r.ic];
              return (
                <button key={r.t} className="w-full px-3 py-2 flex items-start gap-2.5 hover:bg-slate-50 text-left">
                  <Ic size={14} strokeWidth={1.5} className="text-indigo-600 mt-0.5" />
                  <div className="flex-1 min-w-0">
                    <div className="text-[12.5px] font-medium text-slate-900 flex items-center gap-1.5">{r.t}{r.admin && <span className="text-[9px] font-mono uppercase tracking-wider px-1 rounded bg-amber-100 text-amber-700">admin</span>}</div>
                    <div className="text-[11px] text-slate-500">{r.d}</div>
                  </div>
                </button>
              );
            })}
            <div className="my-1 border-t border-slate-100" />
            <div className="px-3 py-1.5 text-[10px] font-mono uppercase tracking-wider text-slate-400">Свои файлы</div>
            {[
              { ic: "FileText", t: "Загрузить PDF/EPUB", d: "drag&drop, OCR опционален" },
              { ic: "Layers", t: "Загрузить сканы страниц", d: "JPG/PNG, OCR в очереди" },
            ].map((r) => {
              const Ic = I[r.ic];
              return (
                <button key={r.t} className="w-full px-3 py-2 flex items-start gap-2.5 hover:bg-slate-50 text-left">
                  <Ic size={14} strokeWidth={1.5} className="text-slate-500 mt-0.5" />
                  <div className="flex-1 min-w-0">
                    <div className="text-[12.5px] font-medium text-slate-900">{r.t}</div>
                    <div className="text-[11px] text-slate-500">{r.d}</div>
                  </div>
                </button>
              );
            })}
          </div>
        )}
      </div>
    </div>
    {/* filter row */}
    <div className="h-11 px-4 flex items-center gap-2 text-[12px]">
      <span className="text-[11px] font-mono uppercase tracking-wider text-slate-400 mr-1">тип</span>
      {["Все", "Коран", "Хадисы", "Книга", "PDF", "Скан"].map((t, i) => (
        <button key={t} className={cx(
          "h-7 px-2.5 rounded-md text-[11.5px] font-medium inline-flex items-center gap-1.5",
          (i === 0 && !filter) || filter === t
            ? "bg-slate-900 text-white"
            : "text-slate-600 hover:bg-slate-100"
        )}>
          {t !== "Все" && <span className={cx("h-1.5 w-1.5 rounded-full", BOOK_TYPE_TONES[t]?.dot || "bg-slate-300")} />}
          {t}
          {t !== "Все" && <span className="text-[10px] font-mono opacity-60">{BOOKS.filter(b => b.type === t).length}</span>}
        </button>
      ))}
      <span className="h-5 w-px bg-slate-200 mx-1" />
      <button className="h-7 px-2.5 rounded-md text-[11.5px] font-medium text-slate-600 hover:bg-slate-100 inline-flex items-center gap-1.5">
        <I.Filter size={11} strokeWidth={1.75} />
        язык
        <I.ChevronDown size={10} strokeWidth={2} className="opacity-60" />
      </button>
      {filter && (
        <span className="ml-auto inline-flex items-center gap-1 h-6 px-2 rounded bg-indigo-50 border border-indigo-200 text-[11px] text-indigo-700">
          активен: {filter}
          <I.X size={10} strokeWidth={2} className="cursor-pointer" />
        </span>
      )}
      <span className="ml-auto text-[10.5px] font-mono text-slate-400">сортировка · по автору</span>
    </div>
  </div>
);

const BookListPageSection = () => (
  <Section
    id="library"
    title="Library overview · /books"
    kicker="28 — library / books"
    hint="Каталог всех книг платформы. Это НЕ старый Source Library с пикерами — это полноценная страница со своей навигацией. Карточки показывают арабский title наскхом, тип+язык, автора (арабский+транслит), счётчики."
  >
    <div className="space-y-8">
      <BookListToolbar filter={null} addOpen={true} />
      <div>
        <div className="text-[10px] font-mono uppercase tracking-wider text-slate-400 mb-3">сетка карточек · default</div>
        <div className="grid grid-cols-4 gap-4">
          {BOOKS.slice(0, 4).map((b, i) => <BookCard key={b.id} book={b} hover={i === 1} />)}
          {BOOKS.slice(4, 8).map((b) => <BookCard key={b.id} book={b} />)}
          {BOOKS.slice(8).map((b) => <BookCard key={b.id} book={b} />)}
        </div>
      </div>

      {/* states grid */}
      <div className="grid grid-cols-3 gap-5">
        {/* loading skeleton */}
        <div>
          <div className="text-[10px] font-mono uppercase tracking-wider text-slate-400 mb-3">loading · skeleton</div>
          <div className="grid grid-cols-2 gap-3">
            {Array.from({length:4}).map((_,i)=>(
              <div key={i} className="bg-white border border-slate-200 rounded-lg overflow-hidden animate-pulse">
                <div className="h-24 bg-slate-100" />
                <div className="p-3 space-y-2">
                  <div className="h-3 bg-slate-100 rounded w-full" />
                  <div className="h-2.5 bg-slate-100 rounded w-2/3" />
                  <div className="pt-2 border-t border-slate-100 space-y-1.5">
                    <div className="h-2 bg-slate-100 rounded w-full" />
                    <div className="h-2 bg-slate-100 rounded w-1/2" />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
        {/* empty */}
        <div>
          <div className="text-[10px] font-mono uppercase tracking-wider text-slate-400 mb-3">empty · БД пуста</div>
          <div className="bg-white border border-slate-200 border-dashed rounded-lg p-8 text-center min-h-[280px] grid place-items-center">
            <div>
              <div className="h-12 w-12 mx-auto rounded-lg bg-slate-100 grid place-items-center text-slate-400 mb-3">
                <I.Library size={20} strokeWidth={1.5} />
              </div>
              <div className="text-[14px] font-semibold text-slate-900 mb-1.5">Библиотека пуста</div>
              <div className="text-[11.5px] text-slate-500 mb-4 max-w-[34ch] mx-auto leading-relaxed">
                Импортируйте первую книгу — из <code className="font-mono text-[10.5px] bg-slate-100 px-1 rounded">shamela.ws</code> через admin или загрузите PDF / сканы напрямую.
              </div>
              <div className="flex items-center gap-2 justify-center">
                <button className="h-8 px-3 inline-flex items-center gap-1.5 rounded-md text-[12px] font-medium bg-slate-900 text-white">
                  <I.Plus size={12} strokeWidth={2} />
                  Импортировать
                </button>
                <button className="h-8 px-3 inline-flex items-center gap-1.5 rounded-md text-[12px] font-medium text-slate-600 hover:bg-slate-100">
                  Открыть админку
                </button>
              </div>
            </div>
          </div>
        </div>
        {/* error */}
        <div>
          <div className="text-[10px] font-mono uppercase tracking-wider text-slate-400 mb-3">error · sync failed</div>
          <div className="bg-white border border-rose-200 rounded-lg p-5 min-h-[280px] flex flex-col">
            <div className="flex items-start gap-3">
              <div className="h-9 w-9 rounded-md bg-rose-100 grid place-items-center text-rose-700 shrink-0">
                <I.AlertTriangle size={16} strokeWidth={1.75} />
              </div>
              <div className="flex-1 min-w-0">
                <div className="text-[13px] font-semibold text-rose-900">Не удалось загрузить каталог</div>
                <div className="text-[11.5px] text-rose-700 mt-0.5">Cloudflare-блок на shamela.ws · прокси не настроен</div>
              </div>
            </div>
            <code className="mt-3 block text-[10.5px] font-mono text-slate-700 bg-slate-50 border border-slate-200 rounded px-2 py-1.5 leading-relaxed">
              GET https://shamela.ws/api/v1/catalog<br/>
              <span className="text-rose-600">→ 403 cf-mitigated · ray a3f7c2…</span>
            </code>
            <div className="mt-3 text-[11.5px] text-slate-600 leading-relaxed">
              Локальный кеш каталога: <strong className="text-slate-900">8 471 книг</strong>, обновлён 4 дня назад. Можно работать оффлайн или повторить попытку.
            </div>
            <div className="mt-auto pt-3 flex items-center gap-2">
              <button className="h-7 px-2.5 inline-flex items-center gap-1 rounded text-[11.5px] font-medium bg-slate-900 text-white"><I.Refresh size={11} strokeWidth={1.75} />Повторить</button>
              <button className="h-7 px-2.5 inline-flex items-center rounded text-[11.5px] font-medium text-slate-600 hover:bg-slate-100">Использовать кеш</button>
              <button className="ml-auto h-7 px-2.5 inline-flex items-center rounded text-[11.5px] font-medium text-slate-500 hover:bg-slate-100">Подробности</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Section>
);

window.PlatformPivotIntro = PlatformPivotIntro;
window.PlatformShellSection = PlatformShellSection;
window.BookListPageSection = BookListPageSection;
window.BOOKS = BOOKS;
window.BOOK_TYPE_TONES = BOOK_TYPE_TONES;
