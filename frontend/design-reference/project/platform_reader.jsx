// =============================================================================
// PLATFORM — sections 29 BookReader, 30 CitationPicker
// =============================================================================

// Real Arabic content samples
const BUKHARI_PAGE_TEXT = [
  { num: "١", t: "حَدَّثَنَا الْحُمَيْدِيُّ عَبْدُ اللهِ بْنُ الزُّبَيْرِ قَالَ: حَدَّثَنَا سُفْيَانُ، قَالَ: حَدَّثَنَا يَحْيَى بْنُ سَعِيدٍ الْأَنْصَارِيُّ، قَالَ: أَخْبَرَنِي مُحَمَّدُ بْنُ إِبْرَاهِيمَ التَّيْمِيُّ، أَنَّهُ سَمِعَ عَلْقَمَةَ بْنَ وَقَّاصٍ اللَّيْثِيَّ يَقُولُ: سَمِعْتُ عُمَرَ بْنَ الْخَطَّابِ رَضِيَ اللهُ عَنْهُ عَلَى الْمِنْبَرِ قَالَ:" },
  { num: "—", t: "سَمِعْتُ رَسُولَ اللهِ ﷺ يَقُولُ: «إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى، فَمَنْ كَانَتْ هِجْرَتُهُ إِلَى دُنْيَا يُصِيبُهَا أَوْ إِلَى امْرَأَةٍ يَنْكِحُهَا، فَهِجْرَتُهُ إِلَى مَا هَاجَرَ إِلَيْهِ»."},
  { num: "٢", t: "حَدَّثَنَا عَبْدُ اللهِ بْنُ يُوسُفَ، قَالَ: أَخْبَرَنَا مَالِكٌ، عَنْ هِشَامِ بْنِ عُرْوَةَ، عَنْ أَبِيهِ، عَنْ عَائِشَةَ أُمِّ الْمُؤْمِنِينَ رَضِيَ اللهُ عَنْهَا، أَنَّ الْحَارِثَ بْنَ هِشَامٍ رَضِيَ اللهُ عَنْهُ سَأَلَ رَسُولَ اللهِ ﷺ فَقَالَ:"},
  { num: "—", t: "يَا رَسُولَ اللهِ، كَيْفَ يَأْتِيكَ الْوَحْيُ؟ فَقَالَ رَسُولُ اللهِ ﷺ: «أَحْيَانًا يَأْتِينِي مِثْلَ صَلْصَلَةِ الْجَرَسِ — وَهُوَ أَشَدُّهُ عَلَيَّ — فَيُفْصَمُ عَنِّي وَقَدْ وَعَيْتُ عَنْهُ مَا قَالَ، وَأَحْيَانًا يَتَمَثَّلُ لِيَ الْمَلَكُ رَجُلًا فَيُكَلِّمُنِي فَأَعِي مَا يَقُولُ»."},
];

const BUKHARI_TREE = [
  { lvl: 0, ar: "بَدْءُ الْوَحْيِ", ru: "Книга начала откровения", n: 1, open: true, active: true },
  { lvl: 1, ar: "كَيْفَ بَدَأَ الْوَحْيُ", ru: "Как началось откровение", n: 1, open: true },
  { lvl: 2, ar: "بَابُ ١", ru: "Намерения · хадис №1", page: 41, active: true },
  { lvl: 2, ar: "بَابُ ٢", ru: "Получение откровения · №2", page: 43 },
  { lvl: 2, ar: "بَابُ ٣", ru: "Первое откровение · №3", page: 45 },
  { lvl: 0, ar: "كِتَابُ الْإِيمَانِ", ru: "Книга веры", n: 2, count: 51 },
  { lvl: 0, ar: "كِتَابُ الْعِلْمِ", ru: "Книга знания", n: 3, count: 76 },
  { lvl: 0, ar: "كِتَابُ الْوُضُوءِ", ru: "Книга омовения", n: 4, count: 113 },
  { lvl: 0, ar: "كِتَابُ الْغُسْلِ", ru: "Книга полного омовения", n: 5, count: 31 },
  { lvl: 0, ar: "كِتَابُ التَّيَمُّمِ", ru: "Книга очищения песком", n: 6, count: 8 },
];

const ChapterTreeRow = ({ row }) => (
  <button className={cx(
    "w-full flex items-start gap-1.5 py-1 pr-2 text-left rounded text-[12px] leading-tight",
    row.active ? "bg-indigo-50" : "hover:bg-slate-50",
    row.lvl === 0 && "font-medium",
    row.lvl === 2 && "text-[11.5px]"
  )} style={{ paddingLeft: 8 + row.lvl * 14 }}>
    {row.lvl < 2 && (
      <I.ChevronRight size={11} strokeWidth={2}
        className={cx("mt-0.5 shrink-0 transition-transform",
          row.open ? "rotate-90 text-slate-500" : "text-slate-400"
        )}
      />
    )}
    {row.lvl === 2 && <span className="w-[11px] shrink-0" />}
    <div className="flex-1 min-w-0">
      <div className={cx("flex items-baseline gap-1.5", row.lvl < 2 ? "" : "")}>
        <span dir="rtl" className={cx("font-naskh truncate",
          row.lvl === 0 ? "text-[15px] text-slate-900" :
          row.lvl === 1 ? "text-[14px] text-slate-700" :
                          "text-[13px] text-slate-600"
        )}>{row.ar}</span>
        {row.n != null && row.lvl === 0 && <span className="text-[10px] font-mono text-slate-400 tabular shrink-0">№{row.n}</span>}
        {row.page && <span className="text-[10px] font-mono text-slate-400 tabular ml-auto shrink-0">{row.page}</span>}
      </div>
      <div className={cx("truncate", row.active ? "text-indigo-700" : "text-slate-500",
        row.lvl === 0 ? "text-[11px]" : "text-[10.5px]"
      )}>
        {row.ru}{row.count && ` · ${row.count} хадисов`}
      </div>
    </div>
  </button>
);

const ReaderHeader = ({ title_ar, title_ru, author_ar, author_ru, type, lang }) => {
  const tone = BOOK_TYPE_TONES[type];
  return (
    <div className="px-6 py-4 border-b border-slate-200 bg-white flex items-start gap-4">
      <button className="h-8 w-8 rounded-md text-slate-500 hover:bg-slate-100 grid place-items-center shrink-0 mt-0.5">
        <I.ArrowLeft size={14} strokeWidth={1.75} />
      </button>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5 text-[11px] text-slate-500 mb-1">
          <I.Library size={11} strokeWidth={1.75} />
          <span>Библиотека</span>
          <I.ChevronRight size={10} strokeWidth={1.75} className="text-slate-400" />
          <span className={cx("inline-flex items-center gap-1 h-[18px] px-1.5 rounded text-[10px] font-medium", tone.bg, tone.text)}>
            <span className={cx("h-1 w-1 rounded-full", tone.dot)} />
            {type}
          </span>
          <span className="text-slate-400 font-mono uppercase tracking-wider text-[10px]">{lang}</span>
        </div>
        {title_ar ? (
          <div dir="rtl" className="font-naskh text-[26px] leading-tight font-bold text-slate-900">{title_ar}</div>
        ) : null}
        <div className={cx("text-slate-500", title_ar ? "text-[12.5px] mt-0.5" : "text-[18px] font-bold text-slate-900")}>{title_ru}</div>
        {author_ar && (
          <div className="mt-2 flex items-baseline gap-3 text-[11.5px]">
            <span dir="rtl" className="font-naskh text-[14px] text-slate-700">{author_ar}</span>
            <span className="italic text-slate-500">{author_ru}</span>
          </div>
        )}
      </div>
      <div className="flex items-center gap-1.5 shrink-0">
        <button className="h-8 px-2.5 rounded-md text-[12px] font-medium text-slate-600 hover:bg-slate-100 inline-flex items-center gap-1.5">
          <I.Pin size={12} strokeWidth={1.75} />
          В коллекцию
        </button>
        <button className="h-8 px-3 rounded-md text-[12px] font-medium bg-indigo-600 text-white hover:bg-indigo-700 inline-flex items-center gap-1.5 shadow-[0_1px_2px_rgba(79,70,229,0.3)]">
          <I.Quote size={12} strokeWidth={1.75} />
          Процитировать
        </button>
      </div>
    </div>
  );
};

const PageToolbar = ({ page, total, onArabic = false, mode }) => (
  <div className="px-6 h-11 border-b border-slate-100 flex items-center gap-2 text-[12px] bg-slate-50/60">
    <button className="h-7 w-7 grid place-items-center rounded text-slate-500 hover:bg-white hover:text-slate-900"><I.ArrowLeft size={13} strokeWidth={1.75} /></button>
    <div className="flex items-center gap-1.5 px-2 h-7 rounded bg-white border border-slate-200">
      <span className="text-slate-500">стр.</span>
      <input value={onArabic ? "٤١" : page} readOnly className="w-10 text-center font-mono tabular text-[12.5px] bg-transparent outline-none" />
      <span className="text-slate-400">/</span>
      <span className="font-mono tabular text-[12px] text-slate-600">{onArabic ? "٤ ١٢٧" : total}</span>
    </div>
    <button className="h-7 w-7 grid place-items-center rounded text-slate-500 hover:bg-white hover:text-slate-900"><I.ArrowRight size={13} strokeWidth={1.75} /></button>
    <span className="h-5 w-px bg-slate-200 mx-1" />
    {mode && (
      <div className="inline-flex h-7 rounded bg-slate-100 p-0.5">
        {[
          { k: "text", l: "текст" },
          { k: "image", l: "скан" },
          { k: "split", l: "оба" },
        ].map((m) => (
          <button key={m.k} className={cx(
            "h-6 px-2 rounded text-[11px] font-medium",
            mode === m.k ? "bg-white text-slate-900 shadow-[0_1px_2px_rgba(15,23,42,0.06)]" : "text-slate-500"
          )}>{m.l}</button>
        ))}
      </div>
    )}
    <div className="ml-auto flex items-center gap-1">
      <button className="h-7 px-2 rounded text-[11px] font-medium text-slate-500 hover:bg-white inline-flex items-center gap-1"><I.ZoomOut size={11} /></button>
      <span className="text-[10.5px] font-mono text-slate-500 tabular px-1">100%</span>
      <button className="h-7 px-2 rounded text-[11px] font-medium text-slate-500 hover:bg-white inline-flex items-center gap-1"><I.ZoomIn size={11} /></button>
      <span className="h-5 w-px bg-slate-200 mx-1" />
      <button className="h-7 px-2 rounded text-[11px] font-medium text-slate-500 hover:bg-white">Tashkeel · авто</button>
    </div>
  </div>
);

const TreeSidebar = ({ rows = BUKHARI_TREE }) => (
  <div className="w-[280px] shrink-0 bg-slate-50/60 border-r border-slate-200 flex flex-col">
    <div className="h-12 px-3 border-b border-slate-200 flex items-center gap-2">
      <span className="text-[11px] font-mono uppercase tracking-wider text-slate-500">Содержание</span>
      <span className="text-[10.5px] font-mono text-slate-400 tabular">97 китабов</span>
      <button className="ml-auto h-6 w-6 rounded grid place-items-center text-slate-400 hover:bg-white hover:text-slate-700"><I.ChevronsUpDown size={11} /></button>
    </div>
    <div className="px-3 py-2 border-b border-slate-200">
      <div className="relative">
        <I.Search size={11} className="absolute left-2 top-1/2 -translate-y-1/2 text-slate-400" />
        <input className="h-7 w-full pl-7 pr-2 text-[11.5px] rounded border border-slate-200 bg-white outline-none focus:border-indigo-300 placeholder:text-slate-400" placeholder="Поиск по главам…" />
      </div>
    </div>
    <div className="flex-1 overflow-auto p-1.5 space-y-0.5 scroll-shadow">
      {rows.map((r, i) => <ChapterTreeRow key={i} row={r} />)}
    </div>
  </div>
);

// ---- Reader A · Sahih Bukhari (Arabic, RTL) -----------------------------------
const ReaderA = () => (
  <div className="bg-white border border-slate-200 rounded-lg overflow-hidden shadow-[0_2px_8px_-2px_rgba(15,23,42,0.06)]">
    <div className="flex h-[640px]">
      <TreeSidebar />
      <div className="flex-1 flex flex-col min-w-0">
        <ReaderHeader
          title_ar="صَحِيحُ الْبُخَارِيِّ"
          title_ru="Сахих аль-Бухари · полная редакция"
          author_ar="مُحَمَّدُ بْنُ إِسْمَاعِيلَ الْبُخَارِيُّ"
          author_ru="Мухаммад б. Исмаил аль-Бухари (194–256 г.х.)"
          type="Хадисы"
          lang="ar"
        />
        <PageToolbar page={41} total="4 127" onArabic={true} />
        <div className="flex-1 overflow-auto bg-[#fbfaf6] py-8 px-12">
          <div className="max-w-[640px] mx-auto" dir="rtl">
            {/* page chapter header */}
            <div className="text-center mb-6 pb-4 border-b-2 border-double border-slate-300">
              <div className="font-naskh text-[22px] font-bold text-slate-900">بَابُ كَيْفَ كَانَ بَدْءُ الْوَحْيِ إِلَى رَسُولِ اللهِ ﷺ</div>
              <div className="text-[11.5px] text-slate-500 mt-1 italic">Глава · Как началось откровение Посланнику Аллаха ﷺ</div>
            </div>
            {/* selection highlight on hadith 1 */}
            <div className="space-y-4 font-naskh arabic-text text-[19px] text-slate-900 relative">
              {BUKHARI_PAGE_TEXT.map((row, i) => (
                <div key={i} className="flex items-start gap-3">
                  <span className="font-mono text-[13px] text-slate-400 shrink-0 mt-1.5 tabular w-5 text-center">{row.num}</span>
                  <p className={cx("flex-1 leading-[1.95]", i === 1 && "bg-yellow-100/70 rounded px-2 py-1 -mx-2 ring-1 ring-yellow-300/50")}>
                    {row.t}
                  </p>
                </div>
              ))}
              {/* floating cite button anchored to selection */}
              <div className="absolute" style={{ left: -12, top: 132 }} dir="ltr">
                <div className="bg-slate-900 text-white rounded-md shadow-[0_8px_20px_-4px_rgba(15,23,42,0.4)] flex items-center overflow-hidden text-[11.5px]">
                  <button className="h-8 px-2.5 hover:bg-slate-800 inline-flex items-center gap-1.5 font-medium">
                    <I.Quote size={11} strokeWidth={2} />
                    Процитировать
                  </button>
                  <div className="h-4 w-px bg-slate-700" />
                  <button className="h-8 px-2 hover:bg-slate-800" title="Скопировать"><I.Copy size={11} strokeWidth={1.75} /></button>
                  <button className="h-8 px-2 hover:bg-slate-800" title="Перевод"><I.Sparkles size={11} strokeWidth={1.75} /></button>
                </div>
                <div className="text-[10px] font-mono text-slate-400 mt-1 pl-2">выделено · 47 слов</div>
              </div>
            </div>
            {/* page footer */}
            <div className="mt-10 pt-4 border-t border-slate-200 flex items-center justify-between text-[10.5px] font-mono text-slate-400">
              <span>صَحِيحُ الْبُخَارِيِّ — كِتَابُ بَدْءِ الْوَحْيِ — حَدِيثُ ١</span>
              <span className="tabular">٤١ / ٤ ١٢٧</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
);

// ---- Reader B · Russian tafsir (LTR) ------------------------------------------
const TAFSIR_RU_TREE = [
  { lvl: 0, ru: "Том 1 · Открывающая и Корова", n: 1, open: true, active: true },
  { lvl: 1, ru: "Сура 1 · аль-Фатиха", page: 12 },
  { lvl: 1, ru: "Сура 2 · аль-Бакара", page: 18, active: true, open: true },
  { lvl: 2, ru: "Аяты 1–7 · муттакун", page: 18 },
  { lvl: 2, ru: "Аяты 21–25 · призыв ко всем людям", page: 64 },
  { lvl: 2, ru: "Аят 255 · аль-Курси", page: 312, active: true },
  { lvl: 0, ru: "Том 2 · Семейство Имрана", n: 2, count: 4 },
  { lvl: 0, ru: "Том 3 · Женщины и Трапеза", n: 3, count: 2 },
];

const ReaderB = () => (
  <div className="bg-white border border-slate-200 rounded-lg overflow-hidden shadow-[0_2px_8px_-2px_rgba(15,23,42,0.06)]">
    <div className="flex h-[640px]">
      <TreeSidebar rows={TAFSIR_RU_TREE.map(r => ({ ...r, ar: r.ru, ru: "" }))} />
      <div className="flex-1 flex flex-col min-w-0">
        <ReaderHeader
          title_ru="Тафсир Ибн Касира · перевод Э. Кулиева"
          author_ru="Ибн Касир ад-Димашки (701–774 г.х.) · переводчик Эльмир Кулиев"
          type="Книга"
          lang="ru"
        />
        <PageToolbar page={312} total={2104} />
        <div className="flex-1 overflow-auto bg-white py-8 px-12">
          <div className="max-w-[680px] mx-auto">
            <div className="mb-6">
              <div className="text-[10.5px] font-mono uppercase tracking-wider text-slate-400 mb-1">Том 1 · Сура 2 · Аят 255</div>
              <h1 className="text-[24px] font-bold text-slate-900 leading-tight">Аят аль-Курси · комментарий</h1>
            </div>
            <div className="rounded-md border-l-2 border-emerald-500 bg-emerald-50/50 px-4 py-3 mb-5">
              <div dir="rtl" className="font-naskh text-[20px] text-slate-900 leading-[1.9] mb-2">
                اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ
              </div>
              <div className="text-[12.5px] italic text-slate-700 leading-relaxed">
                «Аллах — нет божества, кроме Него, Живого, Поддерживающего жизнь. Им не овладевают ни дремота, ни сон…»
              </div>
              <div className="text-[10.5px] font-mono text-emerald-700 mt-1.5">Коран · 2:255 · фрагмент</div>
            </div>
            <p className="text-[14px] leading-[1.8] text-slate-800 mb-3.5">
              Этот аят называется <strong>«Аят аль-Курси»</strong> и занимает особое место в Коране — Пророк ﷺ
              назвал его величайшим аятом Книги Аллаха <a className="text-indigo-600 underline decoration-indigo-300 underline-offset-2" href="#">[1]</a>.
              Ибн Касир приводит сообщение Убаййа б. Ка‘ба, в котором Посланник Аллаха ﷺ спросил его: «Какой
              аят из Книги Аллаха — величайший?»
            </p>
            <p className="text-[14px] leading-[1.8] text-slate-800 mb-3.5">
              Слова <em>«Живого, Поддерживающего жизнь»</em> — это два из величайших имён Аллаха. <em>аль-Хайй</em> —
              Тот, чья жизнь не прерывается; <em>аль-Каййум</em> — Тот, кем существует всё сущее. Сочетание этих
              двух имён обнимает совершенство самобытия и совершенство всякого иного бытия, поддерживаемого Им
              <a className="text-indigo-600 underline decoration-indigo-300 underline-offset-2" href="#">[2]</a>.
            </p>
            <div className="my-6 grid grid-cols-12 gap-4">
              <aside className="col-span-4 border-l-2 border-amber-400 pl-3 py-1">
                <div className="text-[10px] font-mono uppercase tracking-wider text-amber-700 mb-1">маргиналия переводчика</div>
                <p className="text-[11.5px] leading-relaxed text-slate-600 italic">
                  В русской традиции «аль-Каййум» иногда передают как «Вседержитель» — но этот вариант
                  смешивается с христианской терминологией. Кулиев предпочитает «Поддерживающий жизнь».
                </p>
              </aside>
              <p className="col-span-8 text-[14px] leading-[1.8] text-slate-800">
                Далее автор раскрывает смысл слов <em>«Им не овладевают ни дремота, ни сон»</em>, отмечая, что
                <em> сина</em> — лёгкая дремота, предшествующая сну, тогда как <em>наум</em> — сам сон. Аллах
                свободен от обоих несовершенств; Он не дремлет и не спит. Это прямо контрастирует с
                антропоморфными представлениями, известными у иудеев того времени.
              </p>
            </div>
            <div className="text-[11px] font-mono text-slate-400 mt-8 pt-4 border-t border-slate-200 flex justify-between">
              <span>[1] Сахих Муслим · 810 · сообщение от Убаййа б. Ка‘ба</span>
              <span className="tabular">312 / 2 104</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
);

// ---- Reader C · Image scan with OCR overlay -----------------------------------
const SCAN_TREE = [
  { lvl: 0, ar: "—", ru: "Манускрипт · полные сканы", n: null, open: true, active: true },
  { lvl: 1, ar: "—", ru: "Стр. 1–24 · введение", page: 1 },
  { lvl: 1, ar: "—", ru: "Стр. 25–47 · фасль 1", page: 25, active: true },
  { lvl: 2, ar: "—", ru: "Регион OCR · стр. 31", page: 31, active: true },
  { lvl: 1, ar: "—", ru: "Стр. 48–112 · фасль 2", page: 48 },
  { lvl: 1, ar: "—", ru: "Стр. 113–218 · комментарии", page: 113 },
];

const ReaderC = () => (
  <div className="bg-white border border-slate-200 rounded-lg overflow-hidden shadow-[0_2px_8px_-2px_rgba(15,23,42,0.06)]">
    <div className="flex h-[680px]">
      <TreeSidebar rows={SCAN_TREE.map(r => ({ ...r, ar: r.ru, ru: "" }))} />
      <div className="flex-1 flex flex-col min-w-0">
        <ReaderHeader
          title_ru="Манускрипт · Мадина 1287 г.х."
          author_ru="неустановлен · OCR проверьте вручную"
          type="Скан"
          lang="ar"
        />
        <PageToolbar page={31} total={218} mode="split" />
        <div className="flex-1 overflow-auto bg-slate-100 p-6">
          <div className="grid grid-cols-2 gap-5 max-w-[860px] mx-auto">
            {/* image scan with regions */}
            <div className="relative bg-white shadow-[0_4px_16px_-4px_rgba(15,23,42,0.18)] rounded-sm overflow-hidden border border-slate-300">
              <div className="aspect-[3/4] relative bg-[#f4ecd8]">
                {/* fake parchment texture */}
                <svg className="absolute inset-0 w-full h-full opacity-[0.18]" preserveAspectRatio="none" viewBox="0 0 100 100">
                  <defs>
                    <filter id="noise"><feTurbulence baseFrequency="0.9" /></filter>
                  </defs>
                  <rect width="100%" height="100%" filter="url(#noise)" />
                </svg>
                {/* fake arabic text lines */}
                <div className="absolute inset-0 p-6 flex flex-col gap-2.5" dir="rtl">
                  {Array.from({length: 14}).map((_,i)=>(
                    <div key={i} className="h-2 bg-slate-700/70 rounded-full" style={{
                      width: `${[88, 92, 76, 84, 95, 70, 88, 80, 92, 78, 85, 90, 65, 72][i]}%`,
                      marginLeft: i % 3 === 0 ? '0' : i % 3 === 1 ? '4%' : '8%'
                    }} />
                  ))}
                </div>
                {/* page corners */}
                <div className="absolute top-2 right-3 font-mono text-[10px] text-slate-700/60" dir="rtl">٣١</div>
                {/* OCR region overlays */}
                <div className="absolute border-2 border-indigo-500 bg-indigo-500/15 rounded-sm" style={{ top: '28%', right: '8%', width: '74%', height: '14%' }}>
                  <div className="absolute -top-5 right-0 bg-indigo-600 text-white text-[10px] font-mono px-1.5 py-0.5 rounded-t flex items-center gap-1">
                    <span>region #2</span>
                    <I.Quote size={9} strokeWidth={2} />
                    <span>3 цитаты</span>
                  </div>
                </div>
                <div className="absolute border-2 border-emerald-500 bg-emerald-500/15 rounded-sm" style={{ top: '56%', right: '12%', width: '60%', height: '8%' }}>
                  <div className="absolute -top-5 right-0 bg-emerald-600 text-white text-[10px] font-mono px-1.5 py-0.5 rounded-t">region #5 · 1 цитата</div>
                </div>
                {/* active drawing rect */}
                <div className="absolute border-2 border-dashed border-amber-500 bg-amber-500/10 rounded-sm" style={{ top: '74%', right: '20%', width: '54%', height: '10%' }}>
                  <div className="absolute -bottom-7 right-0 bg-amber-500 text-white text-[10px] font-mono px-1.5 py-0.5 rounded">рисуется… отпустите</div>
                </div>
              </div>
            </div>
            {/* OCR text panel */}
            <div className="bg-white border border-slate-200 rounded-sm flex flex-col overflow-hidden">
              <div className="px-3 h-9 border-b border-slate-200 flex items-center gap-2 bg-slate-50">
                <I.Sparkles size={11} className="text-indigo-600" />
                <span className="text-[11.5px] font-medium text-slate-700">OCR текст · автоматически</span>
                <span className="text-[10px] font-mono text-amber-700 bg-amber-100 px-1.5 rounded ml-1">проверьте</span>
                <button className="ml-auto text-[10px] font-mono text-slate-500 hover:text-slate-900">скрыть</button>
              </div>
              <div className="flex-1 p-4 overflow-auto" dir="rtl">
                <div className="font-naskh arabic-text text-[16px] text-slate-700 leading-[1.95] space-y-3">
                  <p className="opacity-60">قَالَ الْمُؤَلِّفُ رَحِمَهُ اللَّهُ: الْحَمْدُ لِلَّهِ الَّذِي هَدَانَا لِهَذَا، وَمَا كُنَّا لِنَهْتَدِيَ لَوْلَا أَنْ هَدَانَا اللَّهُ.</p>
                  <p className="bg-indigo-50 -mx-2 px-2 py-1.5 rounded ring-1 ring-indigo-200">
                    وَأَمَّا مَسْأَلَةُ الِاحْتِفَالِ بِمَوْلِدِ النَّبِيِّ ﷺ فَقَدِ اخْتَلَفَ فِيهَا الْعُلَمَاءُ عَلَى أَقْوَالٍ، أَشْهَرُهَا قَوْلَانِ.
                    <span className="block text-[10px] font-mono text-indigo-700 mt-1.5 not-italic" dir="ltr">region #2 · подсвечено</span>
                  </p>
                  <p className="opacity-60">الْأَوَّلُ: قَوْلُ مَنْ مَنَعَهُ مُطْلَقًا، وَهُوَ مَذْهَبُ جُمْهُورِ الْحَنَابِلَةِ.</p>
                </div>
              </div>
              {/* popover for active region draw */}
              <div className="border-t border-slate-200 p-3 bg-amber-50/40">
                <div className="text-[10.5px] font-mono uppercase tracking-wider text-amber-800 mb-2 flex items-center gap-1.5">
                  <I.Crosshair size={11} />
                  Процитировать этот регион?
                </div>
                <textarea readOnly value="…извлечённый OCR текст для нового региона будет здесь после распознавания…" className="w-full h-14 text-[11px] rounded border border-amber-200 bg-white p-2 outline-none resize-none" />
                <div className="flex items-center gap-2 mt-2">
                  <button className="h-7 px-2.5 rounded text-[11px] font-medium bg-slate-900 text-white inline-flex items-center gap-1"><I.Quote size={10} />Процитировать</button>
                  <button className="h-7 px-2.5 rounded text-[11px] font-medium text-slate-600 hover:bg-white">Отмена</button>
                  <span className="ml-auto text-[10px] font-mono text-slate-500">или нарисуйте новый регион</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
);

const BookReaderSection = () => (
  <Section
    id="reader"
    title="Book reader · /books/{id}"
    kicker="29 — library / reader"
    hint="Two-column: дерево глав sticky слева, читалка справа. Три mockup'а: арабская печатная (Бухари RTL), русский тафсир LTR, скан рукописи с OCR-overlay."
  >
    <div className="space-y-8">
      <div>
        <div className="flex items-baseline gap-3 mb-3">
          <span className="text-[10px] font-mono uppercase tracking-wider text-slate-500">A</span>
          <h3 className="text-[14px] font-semibold text-slate-900">Сахих аль-Бухари · арабская печатная книга</h3>
          <span className="text-[11px] text-slate-500">RTL · naskh · выделение текста с floating cite-button</span>
        </div>
        <ReaderA />
      </div>
      <div>
        <div className="flex items-baseline gap-3 mb-3">
          <span className="text-[10px] font-mono uppercase tracking-wider text-slate-500">B</span>
          <h3 className="text-[14px] font-semibold text-slate-900">Тафсир Ибн Касира · перевод</h3>
          <span className="text-[11px] text-slate-500">LTR · кириллица + аят-блок RTL · footnote-cites · переводческая маргиналия</span>
        </div>
        <ReaderB />
      </div>
      <div>
        <div className="flex items-baseline gap-3 mb-3">
          <span className="text-[10px] font-mono uppercase tracking-wider text-slate-500">C</span>
          <h3 className="text-[14px] font-semibold text-slate-900">Image scan · рукопись + OCR</h3>
          <span className="text-[11px] text-slate-500">split mode · регионы поверх изображения · автоматический OCR справа</span>
        </div>
        <ReaderC />
      </div>
    </div>
  </Section>
);

// ===== 30 — Citation Picker (THE centerpiece) =================================

const TARGETS = [
  { kind: "topic", id: "mawlid", label: "Дозволенность мавлида", count: 18, active: true },
  { kind: "node",  id: "claim-1", label: "Тезис · мавлид дозволен", parent: "mawlid", indent: 1, selected: true },
  { kind: "node",  id: "arg-1",   label: "Аргумент · ас-Суюти, аль-Хави", parent: "mawlid", indent: 1 },
  { kind: "node",  id: "claim-2", label: "Антитезис · бид‘а ляйса минха", parent: "mawlid", indent: 1 },
  { kind: "topic", id: "talqin",  label: "Тальким после погребения", count: 4 },
  { kind: "topic", id: "qunut",   label: "Кунут в фаджр-намазе", count: 11 },
  { kind: "qa",    id: "qa-1",    label: "Q&A · Можно ли праздновать день рождения?", count: 2 },
];

const CitationPickerMock = ({ variant = "preselected" }) => {
  const tabs = [
    { k: "quran", l: "Коран", ic: "BookOpen", count: 1 },
    { k: "hadith", l: "Хадисы", ic: "ScrollText", count: 6, active: true },
    { k: "books", l: "Книги", ic: "Library", count: 38 },
    { k: "free", l: "Свободный текст", ic: "Edit", count: 0 },
  ];
  return (
    <div className="bg-white border border-slate-300 rounded-xl overflow-hidden shadow-[0_24px_60px_-20px_rgba(15,23,42,0.30)]" style={{ width: 980 }}>
      {/* modal header */}
      <div className="px-5 h-12 border-b border-slate-200 flex items-center gap-3">
        <div className="h-7 w-7 rounded-md bg-indigo-100 grid place-items-center text-indigo-700">
          <I.Quote size={14} strokeWidth={1.75} />
        </div>
        <div className="flex-1 min-w-0">
          <div className="text-[14px] font-bold text-slate-900 leading-none">Привязать цитату</div>
          <div className="text-[11px] text-slate-500 mt-0.5">
            {variant === "preselected" && <>из <span className="font-naskh text-[12px] text-slate-700" dir="rtl">صَحِيحُ الْبُخَارِيِّ</span> · к узлу «Тезис · мавлид дозволен»</>}
            {variant === "empty" && <>выберите источник и узел темы для привязки</>}
            {variant === "image" && <>image-region · ручной OCR + книжный контекст</>}
            {variant === "error" && <>не выбрана цель · нельзя привязать</>}
          </div>
        </div>
        <button className="h-8 w-8 rounded-md grid place-items-center text-slate-500 hover:bg-slate-100"><I.X size={14} strokeWidth={1.75} /></button>
      </div>
      {/* tabs */}
      <div className="px-5 border-b border-slate-200 flex items-stretch gap-0.5">
        {tabs.map((t) => {
          const Ic = I[t.ic];
          const isActive = (variant === "image" && t.k === "books") || (variant !== "image" && t.active);
          const isFreeActive = variant === "empty" && t.k === "free";
          return (
            <button key={t.k} className={cx(
              "h-11 px-3 inline-flex items-center gap-1.5 text-[12.5px] font-medium border-b-2 -mb-px",
              isActive || isFreeActive ? "border-indigo-600 text-indigo-700" : "border-transparent text-slate-600 hover:text-slate-900"
            )}>
              <Ic size={12} strokeWidth={1.75} />
              {t.l}
              {t.count > 0 && <span className={cx("ml-0.5 text-[10px] font-mono px-1.5 h-[18px] inline-flex items-center rounded",
                isActive || isFreeActive ? "bg-indigo-100 text-indigo-700" : "bg-slate-100 text-slate-500"
              )}>{t.count}</span>}
            </button>
          );
        })}
      </div>
      {/* body */}
      <div className="grid grid-cols-12 h-[480px]">
        {/* LEFT — mini reader (60%) */}
        <div className="col-span-7 border-r border-slate-200 flex flex-col min-h-0">
          {variant === "empty" ? (
            <div className="flex-1 grid place-items-center px-8 text-center">
              <div>
                <div className="h-11 w-11 mx-auto rounded-md bg-slate-100 grid place-items-center text-slate-400 mb-2.5"><I.Search size={18} strokeWidth={1.5} /></div>
                <div className="text-[13.5px] font-semibold text-slate-900 mb-1">Найдите место в книге</div>
                <div className="text-[11.5px] text-slate-500 max-w-[42ch] mx-auto leading-relaxed">
                  Открывайте книги по табам, переходите на нужную страницу — выделение текста автоматически попадёт в цитату справа.
                </div>
                <div className="mt-4 flex items-center justify-center gap-1.5">
                  <span className="text-[10.5px] font-mono text-slate-400">подсказка:</span>
                  <kbd className="text-[10px] font-mono px-1.5 h-5 inline-flex items-center rounded border border-slate-300 bg-slate-50 text-slate-700">⌘</kbd>
                  <kbd className="text-[10px] font-mono px-1.5 h-5 inline-flex items-center rounded border border-slate-300 bg-slate-50 text-slate-700">K</kbd>
                  <span className="text-[10.5px] text-slate-500">— быстрый поиск по библиотеке</span>
                </div>
              </div>
            </div>
          ) : variant === "image" ? (
            <>
              {/* image with region */}
              <div className="px-3 h-9 border-b border-slate-100 flex items-center gap-2 text-[11.5px] bg-slate-50/60">
                <span className="font-mono text-slate-500 truncate">scan · Манускрипт · стр. 31 · region #2</span>
                <span className="ml-auto text-[10.5px] font-mono text-slate-400 tabular">crop 412×84 px</span>
              </div>
              <div className="flex-1 bg-slate-100 p-4 overflow-auto grid place-items-center">
                <div className="bg-[#f4ecd8] w-full max-w-md aspect-[3/2] relative rounded-sm shadow-[0_4px_16px_-4px_rgba(15,23,42,0.2)] border border-slate-300 overflow-hidden">
                  <div className="absolute inset-0 p-5 flex flex-col gap-2" dir="rtl">
                    {Array.from({length: 6}).map((_,i) => (
                      <div key={i} className="h-2 bg-slate-700/70 rounded-full" style={{ width: `${75+Math.random()*20}%` }} />
                    ))}
                  </div>
                  <div className="absolute border-2 border-indigo-500 bg-indigo-500/20 rounded" style={{ top: '38%', right: '8%', width: '78%', height: '24%' }}>
                    <div className="absolute -top-5 right-0 bg-indigo-600 text-white text-[10px] font-mono px-1.5 rounded-t">region #2</div>
                  </div>
                </div>
              </div>
              <div className="px-3 py-2.5 border-t border-slate-200 bg-amber-50/40 text-[11px] flex items-center gap-2">
                <I.AlertCircle size={12} className="text-amber-700" />
                <span className="text-amber-900">OCR не распознан — введите текст вручную справа</span>
                <button className="ml-auto text-[11px] font-medium text-indigo-700 hover:underline">Запустить OCR повторно</button>
              </div>
            </>
          ) : (
            <>
              {/* preselected — Bukhari mini-reader */}
              <div className="px-3 h-9 border-b border-slate-100 flex items-center gap-2 bg-slate-50/60">
                <button className="h-6 px-2 rounded text-[11px] font-medium text-slate-600 hover:bg-white flex items-center gap-1"><I.ArrowLeft size={10} />к списку книг</button>
                <span className="text-[11px] text-slate-400">·</span>
                <span className="font-naskh text-[13.5px] text-slate-800 truncate" dir="rtl">صَحِيحُ الْبُخَارِيِّ — كِتَابُ بَدْءِ الْوَحْيِ</span>
                <span className="ml-auto text-[10px] font-mono text-slate-400 tabular">стр. 41 / 4 127</span>
              </div>
              <div className="flex-1 overflow-auto bg-[#fbfaf6] p-6">
                <div dir="rtl" className="font-naskh arabic-text text-[16px] text-slate-900 leading-[1.95] space-y-3">
                  <p>
                    حَدَّثَنَا الْحُمَيْدِيُّ، قَالَ: حَدَّثَنَا سُفْيَانُ، عَنْ يَحْيَى بْنِ سَعِيدٍ، عَنْ مُحَمَّدِ بْنِ إِبْرَاهِيمَ التَّيْمِيِّ، أَنَّهُ سَمِعَ عَلْقَمَةَ بْنَ وَقَّاصٍ.
                  </p>
                  <p className="bg-yellow-100/80 px-2 py-1.5 rounded ring-1 ring-yellow-300/60">
                    سَمِعْتُ رَسُولَ اللهِ ﷺ يَقُولُ: «إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى».
                    <span className="block text-[10px] font-mono text-amber-800 mt-1.5" dir="ltr">выделено · 14 слов · автоматически в правую панель</span>
                  </p>
                  <p>
                    فَمَنْ كَانَتْ هِجْرَتُهُ إِلَى دُنْيَا يُصِيبُهَا أَوْ إِلَى امْرَأَةٍ يَنْكِحُهَا، فَهِجْرَتُهُ إِلَى مَا هَاجَرَ إِلَيْهِ.
                  </p>
                </div>
              </div>
              <div className="px-3 h-8 border-t border-slate-200 flex items-center gap-2 text-[10.5px] font-mono text-slate-500 bg-slate-50/60">
                <I.ArrowLeft size={11} />
                <span>стр. 40</span>
                <span className="text-slate-300">·</span>
                <span className="font-bold text-slate-700">стр. 41</span>
                <span className="text-slate-300">·</span>
                <span>стр. 42</span>
                <I.ArrowRight size={11} />
                <span className="ml-auto">или ⌘+стрелка</span>
              </div>
            </>
          )}
        </div>
        {/* RIGHT — citation preview (40%) */}
        <div className="col-span-5 flex flex-col min-h-0">
          <div className="px-4 py-3 border-b border-slate-100">
            <div className="text-[10px] font-mono uppercase tracking-wider text-slate-400">источник</div>
            {variant === "empty" ? (
              <div className="mt-1 text-[12.5px] text-slate-400 italic">не выбран</div>
            ) : variant === "image" ? (
              <>
                <div className="mt-1 text-[13px] font-semibold text-slate-900">Манускрипт · Мадина 1287 г.х.</div>
                <div className="text-[11px] text-slate-500">неустановлен автор · стр. 31 · region #2</div>
              </>
            ) : (
              <>
                <div className="mt-1 flex items-baseline justify-between gap-2">
                  <span dir="rtl" className="font-naskh text-[15px] font-semibold text-slate-900">صَحِيحُ الْبُخَارِيِّ</span>
                  <span className="text-[10.5px] font-mono text-slate-400 shrink-0 tabular">стр. 41 · хадис №1</span>
                </div>
                <div className="text-[11px] text-slate-500 italic">Мухаммад б. Исмаил аль-Бухари · 256 г.х.</div>
              </>
            )}
          </div>
          <div className="flex-1 overflow-auto px-4 py-3.5 space-y-3.5">
            <div>
              <label className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-1 block">Цитата · можно править</label>
              {variant === "image" ? (
                <textarea defaultValue="…введите OCR-текст вручную или загрузите распознавание…" rows={3} className="w-full text-[12px] rounded-md border border-amber-300 bg-amber-50/40 px-3 py-2 leading-relaxed outline-none focus:border-amber-500 focus:ring-1 focus:ring-amber-300 placeholder:text-slate-400" />
              ) : variant === "empty" ? (
                <div className="text-[12px] text-slate-400 italic px-3 py-2 border border-dashed border-slate-200 rounded-md bg-slate-50">
                  — будет заполнено выделением слева —
                </div>
              ) : (
                <div className="rounded-md border border-slate-200 bg-slate-50 p-3" dir="rtl">
                  <p className="font-naskh text-[15px] text-slate-900 leading-[1.85]">«إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى».</p>
                  <p className="text-[11px] text-slate-500 mt-1.5 italic" dir="ltr">«Поистине, дела — по намерениям, и каждому достанется то, что он намеревался».</p>
                </div>
              )}
            </div>
            <div>
              <label className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-1 block">Location · необязательно</label>
              <input defaultValue={variant === "preselected" ? "т. 1, стр. 41 · изд. Дар аль-кутуб, 1422 г.х." : ""} placeholder="например: т. 3, стр. 245" className="w-full h-8 text-[12px] rounded-md border border-slate-200 bg-white px-2.5 outline-none focus:border-indigo-300 placeholder:text-slate-400" />
            </div>
            <div>
              <label className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-1 block">Контекст · зачем эта цитата подкрепляет узел</label>
              <textarea
                defaultValue={variant === "preselected" ? "Хадис о намерениях обосновывает оценку любого новшества по цели практикующего — на этом строится тезис о допустимости мавлида." : ""}
                placeholder="Объясните, как эта цитата соотносится с тезисом или аргументом узла"
                rows={3}
                className="w-full text-[12px] rounded-md border border-slate-200 bg-white px-2.5 py-1.5 leading-relaxed outline-none focus:border-indigo-300 placeholder:text-slate-400"
              />
            </div>
            {/* target picker */}
            <div>
              <label className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-1 block flex items-center gap-1.5">
                Куда привязать?
                {variant === "error" && <span className="text-rose-600 normal-case font-sans tracking-normal">обязательно</span>}
              </label>
              <div className={cx(
                "rounded-md border bg-white max-h-44 overflow-auto",
                variant === "error" ? "border-rose-400 ring-2 ring-rose-100" : "border-slate-200"
              )}>
                {TARGETS.map((t) => (
                  <button key={t.id} className={cx(
                    "w-full px-2.5 h-8 flex items-center gap-2 text-[12px] text-left hover:bg-slate-50",
                    t.indent && "pl-7",
                    t.selected && variant !== "error" && variant !== "empty" && "bg-indigo-50",
                    t.selected && variant === "empty" && ""
                  )}>
                    {t.kind === "topic" && <I.Network size={11} strokeWidth={1.75} className="text-slate-400 shrink-0" />}
                    {t.kind === "node" && <I.CornerDownRight size={11} strokeWidth={1.75} className="text-slate-400 shrink-0" />}
                    {t.kind === "qa" && <I.MessageSquareQuote size={11} strokeWidth={1.75} className="text-slate-400 shrink-0" />}
                    <span className={cx("flex-1 truncate", t.selected && variant !== "error" && variant !== "empty" ? "text-indigo-800 font-medium" : "text-slate-700")}>{t.label}</span>
                    {t.count != null && <span className="text-[10px] font-mono text-slate-400 tabular shrink-0">{t.count}</span>}
                    {t.selected && variant !== "error" && variant !== "empty" && <I.Check size={11} strokeWidth={2} className="text-indigo-600 shrink-0" />}
                  </button>
                ))}
              </div>
              {variant === "error" && (
                <div className="mt-1.5 text-[11px] text-rose-700 flex items-center gap-1.5">
                  <I.AlertCircle size={11} />
                  Выберите узел или вопрос — цитата привязывается к чему-то конкретному
                </div>
              )}
            </div>
            {/* edge type if node target */}
            {(variant === "preselected" || variant === "image") && (
              <div>
                <label className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-1 block">Тип связи</label>
                <div className="inline-flex h-8 rounded-md border border-slate-200 bg-white p-0.5 text-[11px]">
                  <button className="h-7 px-2.5 rounded font-medium bg-emerald-50 text-emerald-700 inline-flex items-center gap-1.5"><I.Check size={11} />supports</button>
                  <button className="h-7 px-2.5 rounded font-medium text-slate-500 inline-flex items-center gap-1.5"><I.AlertTriangle size={11} />qualifies</button>
                  <button className="h-7 px-2.5 rounded font-medium text-slate-500 inline-flex items-center gap-1.5"><I.X size={11} />refutes</button>
                </div>
              </div>
            )}
          </div>
          <div className="px-4 py-2.5 border-t border-slate-200 bg-slate-50/60 flex items-center gap-2">
            <span className="text-[10.5px] font-mono text-slate-500">⏎ привязать · esc отмена</span>
            <div className="ml-auto flex items-center gap-1.5">
              <button className="h-8 px-3 rounded-md text-[12px] font-medium text-slate-600 hover:bg-slate-100">Отмена</button>
              <button className={cx(
                "h-8 px-4 rounded-md text-[12px] font-semibold inline-flex items-center gap-1.5",
                variant === "error" || variant === "empty"
                  ? "bg-slate-200 text-slate-400 cursor-not-allowed"
                  : "bg-indigo-600 text-white shadow-[0_1px_2px_rgba(79,70,229,0.3)] hover:bg-indigo-700"
              )} disabled={variant === "error" || variant === "empty"}>
                <I.Link size={11} strokeWidth={2} />
                Привязать цитату
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

const CitationPickerSection = () => (
  <Section
    id="citation-picker"
    title="Citation Picker · сердце платформы"
    kicker="30 — citation picker"
    hint="Главный компонент после pivot'а. Заменяет старые SourcePickerQuran/Hadith/Books и AddSourceModal. Универсальная привязка цитаты из библиотеки в любое приложение (узел графа / вопрос Q&A)."
  >
    <div className="space-y-2 mb-6">
      <div className="rounded-lg border border-amber-300 bg-amber-50/70 px-4 py-3 flex items-start gap-3">
        <I.Sparkles size={14} className="text-amber-700 mt-0.5 shrink-0" />
        <div className="text-[12px] text-amber-900 leading-relaxed">
          <strong>Триггеры:</strong> (1) выделение в BookReaderPage → floating «процитировать» → этот picker
          с предзаполненным текстом · (2) в NodeDetailsPanel «привязать цитату» → picker без выделения,
          юзер сам ищет место в книге.
        </div>
      </div>
    </div>

    <div className="space-y-10">
      {/* Variant 1 — preselected (default flow) */}
      <div>
        <div className="flex items-baseline gap-3 mb-3">
          <span className="text-[10px] font-mono uppercase tracking-wider text-slate-500">A</span>
          <h3 className="text-[14px] font-semibold text-slate-900">С предвыделенным текстом · open from BookReader</h3>
          <span className="text-[11px] text-slate-500">основной сценарий — выделение слева → готовая цитата справа → выбор узла</span>
        </div>
        <div className="bg-slate-900/5 dot-grid rounded-xl p-8 grid place-items-center">
          <CitationPickerMock variant="preselected" />
        </div>
      </div>

      {/* Variant 2 — empty (open from NodeDetailsPanel) */}
      <div>
        <div className="flex items-baseline gap-3 mb-3">
          <span className="text-[10px] font-mono uppercase tracking-wider text-slate-500">B</span>
          <h3 className="text-[14px] font-semibold text-slate-900">Без выделения · open from NodeDetailsPanel</h3>
          <span className="text-[11px] text-slate-500">reader пустой — юзер ищет цитату · табы показывают доступные источники</span>
        </div>
        <div className="bg-slate-900/5 dot-grid rounded-xl p-8 grid place-items-center">
          <CitationPickerMock variant="empty" />
        </div>
      </div>

      {/* Variant 3 — image region */}
      <div>
        <div className="flex items-baseline gap-3 mb-3">
          <span className="text-[10px] font-mono uppercase tracking-wider text-slate-500">C</span>
          <h3 className="text-[14px] font-semibold text-slate-900">Image region · из скана</h3>
          <span className="text-[11px] text-slate-500">прямоугольный crop вместо textRange · ручной OCR справа когда автомат не сработал</span>
        </div>
        <div className="bg-slate-900/5 dot-grid rounded-xl p-8 grid place-items-center">
          <CitationPickerMock variant="image" />
        </div>
      </div>

      {/* Variant 4 — validation error */}
      <div>
        <div className="flex items-baseline gap-3 mb-3">
          <span className="text-[10px] font-mono uppercase tracking-wider text-slate-500">D</span>
          <h3 className="text-[14px] font-semibold text-slate-900">Validation error · target не выбран</h3>
          <span className="text-[11px] text-slate-500">кнопка disabled · target picker подсвечен красным · inline error</span>
        </div>
        <div className="bg-slate-900/5 dot-grid rounded-xl p-8 grid place-items-center">
          <CitationPickerMock variant="error" />
        </div>
      </div>
    </div>
  </Section>
);

window.BookReaderSection = BookReaderSection;
window.CitationPickerSection = CitationPickerSection;
