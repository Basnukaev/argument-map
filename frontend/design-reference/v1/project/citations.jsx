// Citations / Подкрепления — design variants for Argument Map ADR-026+027.
// Section adds to Components Reference: A NodeCard indicators, B Side-panel sections,
// C Panel header, D Naming recommendation.

const SAMPLE_LIB = [
  { type: "library", book: "اَلْمَجْمُوعُ شَرْحُ ٱلْمُهَذَّبِ", bookRu: "аль-Маджму' шарх аль-Мухаззаб",
    author: "ан-Навави", vol: "II", page: "147", lines: "8–14",
    quote: "الْأَصْلُ فِي الْعِبَادَاتِ التَّوْقِيفُ",
    quoteRu: "Основа в актах поклонения — следование тексту, без добавлений",
    shamela: "shamela.ws/book/15211/147" },
  { type: "library", book: "صَحِيحُ ٱلْبُخَارِيِّ", bookRu: "Сахих аль-Бухари",
    author: "аль-Бухари", vol: "I", page: "9", lines: "1–4", hadith: "№ 1",
    quote: "إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ",
    quoteRu: "Поистине, дела — только по намерениям",
    shamela: "shamela.ws/book/1681/9" },
  { type: "freeform", title: "Фатва Постоянного Комитета № 4262",
    url: "alifta.gov.sa/fatwa/4262",
    quoteRu: "Запрещается выделение определённого дня для…",
    note: "Скопировано из официальной публикации, доступно онлайн." },
  { type: "freeform", title: "Личные записи лекций",
    url: null,
    quoteRu: "Шейх упомянул, что…",
    note: "Из памяти, требует проверки." },
];

// ---------- A. NodeCard variants ----------

const NodeCardWithFooterChips = ({ title, body, lib = 0, free = 0, selected }) => (
  <div
    className={cx(
      "relative rounded-xl bg-white border transition-shadow",
      selected
        ? "border-indigo-500 shadow-[0_0_0_3px_rgba(99,102,241,0.18),0_8px_20px_rgba(15,23,42,0.10)]"
        : "border-slate-200 shadow-[0_1px_2px_rgba(15,23,42,0.04),0_2px_6px_rgba(15,23,42,0.04)]",
    )}
    style={{ width: 280 }}
  >
    <div className="absolute left-0 top-0 bottom-0 w-[5px] rounded-l-xl bg-emerald-500" />
    <div className="pl-4 pr-3 py-3">
      <div className="flex items-center gap-2 mb-1.5">
        <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-bold uppercase tracking-wider bg-indigo-100 text-indigo-700">
          <I.Megaphone size={11} /> CLAIM
        </span>
        <span className="flex-1" />
        <StatusBadge status="STANDING" size="sm" />
      </div>
      <div className="text-[13px] font-semibold text-slate-900 leading-snug">{title}</div>
      {body && <div className="mt-1 text-[12px] text-slate-600 line-clamp-2">{body}</div>}
      {(lib > 0 || free > 0) && (
        <div className="mt-2 pt-2 border-t border-slate-100 flex items-center gap-1.5">
          {lib > 0 && (
            <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded bg-indigo-50 text-indigo-700 border border-indigo-200 text-[10.5px] font-mono font-semibold">
              <I.BookOpen size={10} /> {lib}
            </span>
          )}
          {free > 0 && (
            <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded bg-slate-50 text-slate-600 border border-slate-200 text-[10.5px] font-mono font-semibold">
              <I.Quote size={10} /> {free}
            </span>
          )}
          <span className="ml-auto text-[10px] text-slate-400">подкрепления</span>
        </div>
      )}
    </div>
  </div>
);

const NodeCardWithCornerBadge = ({ title, body, lib = 0, free = 0 }) => {
  const total = lib + free;
  return (
    <div className="relative rounded-xl bg-white border border-slate-200 shadow-[0_1px_2px_rgba(15,23,42,0.04),0_2px_6px_rgba(15,23,42,0.04)]" style={{ width: 280 }}>
      <div className="absolute left-0 top-0 bottom-0 w-[5px] rounded-l-xl bg-emerald-500" />
      {total > 0 && (
        <div className={cx(
          "absolute -top-2 -right-2 h-6 min-w-6 px-1.5 rounded-full flex items-center justify-center gap-1 text-[10.5px] font-mono font-bold shadow-sm ring-2 ring-white",
          lib > 0 ? "bg-indigo-600 text-white" : "bg-slate-500 text-white",
        )}>
          {lib > 0 ? <I.BookOpen size={10} /> : <I.Quote size={10} />}
          {total}
        </div>
      )}
      <div className="pl-4 pr-3 py-3">
        <div className="flex items-center gap-2 mb-1.5">
          <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-bold uppercase tracking-wider bg-indigo-100 text-indigo-700">
            <I.Megaphone size={11} /> CLAIM
          </span>
          <span className="flex-1" />
          <StatusBadge status="STANDING" size="sm" />
        </div>
        <div className="text-[13px] font-semibold text-slate-900 leading-snug">{title}</div>
        {body && <div className="mt-1 text-[12px] text-slate-600 line-clamp-2">{body}</div>}
      </div>
    </div>
  );
};

const NodeCardWithInlineTypeChip = ({ title, body, lib = 0, free = 0 }) => {
  const total = lib + free;
  return (
    <div className="relative rounded-xl bg-white border border-slate-200 shadow-[0_1px_2px_rgba(15,23,42,0.04),0_2px_6px_rgba(15,23,42,0.04)]" style={{ width: 280 }}>
      <div className="absolute left-0 top-0 bottom-0 w-[5px] rounded-l-xl bg-emerald-500" />
      <div className="pl-4 pr-3 py-3">
        <div className="flex items-center gap-2 mb-1.5">
          <span className="inline-flex items-center gap-1 pl-1.5 h-5 rounded text-[10px] font-bold uppercase tracking-wider bg-indigo-100 text-indigo-700">
            <I.Megaphone size={11} /> CLAIM
            {total > 0 && (
              <span className="ml-1 pl-1.5 pr-1.5 h-5 flex items-center gap-0.5 rounded-r bg-indigo-200/70 text-indigo-800 normal-case tracking-normal font-mono">
                {lib > 0 ? <I.BookOpen size={9} /> : <I.Quote size={9} />}
                {total}
              </span>
            )}
          </span>
          <span className="flex-1" />
          <StatusBadge status="STANDING" size="sm" />
        </div>
        <div className="text-[13px] font-semibold text-slate-900 leading-snug">{title}</div>
        {body && <div className="mt-1 text-[12px] text-slate-600 line-clamp-2">{body}</div>}
      </div>
    </div>
  );
};

const TradeoffTag = ({ tone, children }) => {
  const tones = {
    pro: "bg-emerald-50 text-emerald-800 border-emerald-200",
    con: "bg-rose-50 text-rose-800 border-rose-200",
    neu: "bg-slate-50 text-slate-700 border-slate-200",
  };
  const Icon = tone === "pro" ? I.Check : tone === "con" ? I.X : I.Info;
  return (
    <span className={cx("inline-flex items-start gap-1.5 px-2 py-1 rounded-md border text-[11.5px] leading-snug", tones[tone])}>
      <Icon size={11} className="mt-[2px] shrink-0" />
      <span>{children}</span>
    </span>
  );
};

const VariantCard = ({ index, name, recommended, children, pros = [], cons = [], notes = [] }) => (
  <div className={cx(
    "rounded-xl border bg-white overflow-hidden flex flex-col",
    recommended ? "border-indigo-500 ring-2 ring-indigo-200" : "border-slate-200",
  )}>
    <div className="px-4 py-2.5 border-b border-slate-100 flex items-center gap-2 bg-gradient-to-b from-white to-slate-50/40">
      <span className="inline-flex items-center justify-center h-6 w-6 rounded-md bg-slate-900 text-white text-[11px] font-mono font-bold">{index}</span>
      <span className="text-[13px] font-semibold text-slate-900">{name}</span>
      {recommended && (
        <span className="ml-auto inline-flex items-center gap-1 px-1.5 h-5 rounded bg-indigo-600 text-white text-[10px] font-bold uppercase tracking-wider">
          <I.Star size={10} /> рекомендую
        </span>
      )}
    </div>
    <div className="p-5 bg-gradient-to-b from-slate-50/60 to-white flex-1 grid place-items-center">
      {children}
    </div>
    <div className="px-4 py-3 border-t border-slate-100 space-y-1.5">
      {pros.map((p, i) => <TradeoffTag key={`p${i}`} tone="pro">{p}</TradeoffTag>)}
      {cons.map((c, i) => <TradeoffTag key={`c${i}`} tone="con">{c}</TradeoffTag>)}
      {notes.map((n, i) => <TradeoffTag key={`n${i}`} tone="neu">{n}</TradeoffTag>)}
    </div>
  </div>
);

const SectionA_NodeCardVariants = () => (
  <SubBlock
    title="A · Индикатор на NodeCard"
    hint="Различить: 0 / только-свободные / library-backed / смешано. Места мало."
  >
    <div className="grid grid-cols-3 gap-4">
      <VariantCard
        index="A1"
        name="Footer micro-row"
        recommended
        pros={[
          "Раздельный счёт lib vs free — сразу видно соотношение",
          "Цветовая семантика: индиго = library, slate = свободные",
          "Помещается на компактных карточках в графе",
        ]}
        cons={["+1 строка высоты (~24 px)"]}
      >
        <NodeCardWithFooterChips
          title="Мавлид является дозволенной практикой"
          body="Главный тезис обсуждения. Подкрепляется фетвами и хадисами."
          lib={3} free={1}
        />
      </VariantCard>
      <VariantCard
        index="A2"
        name="Угловой бейдж"
        pros={[
          "Не отнимает высоту, просто торчит из угла",
          "Цвет бейджа = доминирующий тип (lib=индиго, free=slate)",
        ]}
        cons={[
          "Только суммарное N — нельзя одновременно показать lib и free",
          "Цвет угла конкурирует с line-stroke selection-state",
        ]}
      >
        <NodeCardWithCornerBadge
          title="Мавлид является дозволенной практикой"
          body="Главный тезис обсуждения. Подкрепляется фетвами и хадисами."
          lib={3} free={1}
        />
      </VariantCard>
      <VariantCard
        index="A3"
        name="Встроенная в TypeChip"
        pros={[
          "Нулевой overhead — счётчик внутри уже существующего чипа",
          "Один взгляд: тип + кол-во подкреплений рядом",
        ]}
        cons={[
          "TypeChip становится тяжелее визуально",
          "Не различает lib vs free без второго слота",
        ]}
      >
        <NodeCardWithInlineTypeChip
          title="Мавлид является дозволенной практикой"
          body="Главный тезис обсуждения. Подкрепляется фетвами и хадисами."
          lib={3} free={1}
        />
      </VariantCard>
    </div>
    <div className="mt-4 rounded-lg border border-slate-200 bg-white p-4">
      <div className="text-[11px] font-mono uppercase tracking-wider text-slate-500 mb-3">4 состояния — для всех вариантов</div>
      <div className="grid grid-cols-4 gap-3">
        <NodeCardWithFooterChips title="Без подкреплений" body="Тезис без источников — слабая позиция." />
        <NodeCardWithFooterChips title="Только свободные" body="Цитаты без библиотечной привязки." free={2} />
        <NodeCardWithFooterChips title="Только из библиотеки" body="Сильно: можно перейти в книгу." lib={3} />
        <NodeCardWithFooterChips title="Смешанный набор" body="3 из библиотеки и 1 ручная цитата." lib={3} free={1} />
      </div>
    </div>
  </SubBlock>
);

// ---------- B. Side-panel section variants ----------

const LibraryCite = ({ c, compact }) => (
  <div className="rounded-lg border border-slate-200 bg-white relative overflow-hidden hover:border-indigo-300 transition-colors">
    <div className="absolute left-0 top-0 bottom-0 w-[3px] bg-indigo-600" />
    <div className="pl-3.5 pr-3 py-3">
      <div className="flex items-center gap-1.5 mb-2 flex-wrap">
        <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded bg-indigo-50 text-indigo-700 border border-indigo-200 text-[10px] font-bold uppercase tracking-wider">
          <I.BookOpen size={10} /> Из библиотеки
        </span>
        {c.hadith && (
          <span className="inline-flex items-center px-1.5 h-5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200 text-[10px] font-mono font-semibold">
            {c.hadith}
          </span>
        )}
        <span className="ml-auto font-mono text-[10px] text-slate-500 tabular-nums">т. {c.vol} · стр. {c.page} · стр. {c.lines}</span>
      </div>
      <div className="flex items-baseline gap-2 mb-1">
        <span dir="rtl" className="font-naskh text-[15px] font-bold text-slate-900 truncate">{c.book}</span>
      </div>
      <div className="text-[11.5px] text-slate-500 italic mb-2">{c.bookRu} — {c.author}</div>
      {!compact && (
        <>
          <div dir="rtl" className="font-naskh text-[15px] text-slate-800 leading-[1.85] mb-1">«{c.quote}»</div>
          <div className="text-[12px] text-slate-600 italic">«{c.quoteRu}»</div>
        </>
      )}
      <div className="mt-2.5 pt-2.5 border-t border-slate-100 flex items-center gap-2">
        <button className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded text-[11.5px] font-semibold bg-indigo-600 text-white hover:bg-indigo-700">
          <I.ExternalLink size={11} /> Перейти в книгу
        </button>
        <span className="text-[10px] font-mono text-slate-400 truncate">{c.shamela}</span>
      </div>
    </div>
  </div>
);

const FreeformCite = ({ c, compact }) => (
  <div className="rounded-lg border border-slate-200 bg-slate-50/50 px-3 py-3">
    <div className="flex items-center gap-1.5 mb-2 flex-wrap">
      <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded bg-slate-100 text-slate-700 border border-slate-200 text-[10px] font-bold uppercase tracking-wider">
        <I.Quote size={10} /> Свободная
      </span>
      {c.url ? (
        <span className="font-mono text-[10px] text-slate-500 truncate ml-auto">{c.url}</span>
      ) : (
        <span className="inline-flex items-center gap-1 text-[10px] text-amber-700 ml-auto">
          <I.AlertCircle size={10} /> без URL
        </span>
      )}
    </div>
    <div className="text-[12.5px] font-semibold text-slate-900 leading-snug">{c.title}</div>
    {!compact && (
      <>
        <div className="mt-1.5 text-[12px] text-slate-700 italic">«{c.quoteRu}»</div>
        {c.note && <div className="mt-1.5 text-[11px] text-slate-500">{c.note}</div>}
      </>
    )}
  </div>
);

const SidePanelB1 = () => (
  <div className="rounded-lg border border-slate-200 bg-white overflow-hidden" style={{ width: 380 }}>
    <div className="px-4 py-3 border-b border-slate-200 flex items-center gap-2">
      <I.Anchor size={14} className="text-indigo-600" />
      <span className="text-[13px] font-semibold text-slate-900">Опора</span>
      <span className="text-[10px] font-mono text-slate-500 tabular-nums">4</span>
      <span className="ml-auto inline-flex items-center gap-1 text-[10.5px] font-mono text-slate-500">
        <I.BookOpen size={10} className="text-indigo-600" /> 2
        <span className="text-slate-300 mx-0.5">·</span>
        <I.Quote size={10} /> 2
      </span>
    </div>
    <div className="p-3 space-y-2.5 max-h-[480px] overflow-y-auto">
      {SAMPLE_LIB.map((c, i) => c.type === "library" ? <LibraryCite key={i} c={c} /> : <FreeformCite key={i} c={c} />)}
      <button className="w-full inline-flex items-center justify-center gap-1.5 h-9 rounded-lg border border-dashed border-slate-300 text-[12px] font-medium text-slate-600 hover:text-indigo-700 hover:border-indigo-300">
        <I.Plus size={13} /> Добавить подкрепление
      </button>
    </div>
  </div>
);

const SidePanelB2 = () => {
  const [tab, setTab] = useRefState("lib");
  const lib = SAMPLE_LIB.filter(c => c.type === "library");
  const free = SAMPLE_LIB.filter(c => c.type === "freeform");
  return (
    <div className="rounded-lg border border-slate-200 bg-white overflow-hidden" style={{ width: 380 }}>
      <div className="px-4 py-3 border-b border-slate-100 flex items-center gap-2">
        <I.Anchor size={14} className="text-indigo-600" />
        <span className="text-[13px] font-semibold text-slate-900">Опора</span>
        <span className="text-[10px] font-mono text-slate-500 tabular-nums">{SAMPLE_LIB.length}</span>
      </div>
      <div className="px-3 pt-2 border-b border-slate-200 flex gap-1">
        {[
          { k: "lib", label: "Из библиотеки", n: lib.length, ic: "BookOpen", tone: "indigo" },
          { k: "free", label: "Свободные", n: free.length, ic: "Quote", tone: "slate" },
        ].map((t) => {
          const Ic = I[t.ic];
          const active = tab === t.k;
          return (
            <button key={t.k} onClick={() => setTab(t.k)} className={cx(
              "inline-flex items-center gap-1.5 px-3 h-8 border-b-2 -mb-px text-[12px] font-medium",
              active
                ? (t.tone === "indigo" ? "border-indigo-600 text-indigo-700" : "border-slate-600 text-slate-900")
                : "border-transparent text-slate-500 hover:text-slate-800"
            )}>
              <Ic size={12} /> {t.label}
              <span className="ml-1 text-[10px] font-mono px-1.5 h-[18px] inline-flex items-center rounded bg-slate-100 text-slate-600">{t.n}</span>
            </button>
          );
        })}
      </div>
      <div className="p-3 space-y-2.5">
        {(tab === "lib" ? lib : free).map((c, i) =>
          c.type === "library" ? <LibraryCite key={i} c={c} compact /> : <FreeformCite key={i} c={c} compact />
        )}
      </div>
    </div>
  );
};

const StrengthBar = ({ lib, free }) => {
  const total = lib + free; if (total === 0) return null;
  return (
    <div className="flex h-1.5 rounded-full overflow-hidden bg-slate-100" title={`Library: ${lib}, Free: ${free}`}>
      <div className="bg-indigo-500" style={{ width: `${(lib / total) * 100}%` }} />
      <div className="bg-slate-400" style={{ width: `${(free / total) * 100}%` }} />
    </div>
  );
};

const SidePanelB3 = () => (
  <div className="rounded-lg border border-slate-200 bg-white overflow-hidden" style={{ width: 380 }}>
    <div className="px-4 py-3 border-b border-slate-200">
      <div className="flex items-center gap-2">
        <I.Anchor size={14} className="text-indigo-600" />
        <span className="text-[13px] font-semibold text-slate-900">Опора</span>
        <span className="ml-auto inline-flex items-center gap-2 text-[10.5px] font-mono text-slate-500">
          <span className="inline-flex items-center gap-1"><I.BookOpen size={10} className="text-indigo-600" /> 2</span>
          <span className="inline-flex items-center gap-1"><I.Quote size={10} /> 2</span>
        </span>
      </div>
      <div className="mt-2"><StrengthBar lib={2} free={2} /></div>
      <div className="mt-1.5 flex items-center justify-between text-[10px] font-mono text-slate-500">
        <span>50% из библиотеки</span>
        <span className="text-amber-700">средняя сила</span>
      </div>
    </div>
    <div className="divide-y divide-slate-100">
      {SAMPLE_LIB.map((c, i) => (
        <details key={i} className="group/c">
          <summary className="px-4 py-2.5 flex items-center gap-2 cursor-pointer hover:bg-slate-50">
            {c.type === "library" ? (
              <span className="h-1.5 w-1.5 rounded-full bg-indigo-600 shrink-0" />
            ) : (
              <span className="h-1.5 w-1.5 rounded-full bg-slate-400 shrink-0" />
            )}
            <span className="text-[12.5px] text-slate-800 truncate flex-1">
              {c.type === "library" ? `${c.bookRu} · т. ${c.vol} · стр. ${c.page}` : c.title}
            </span>
            <I.ChevronDown size={12} className="text-slate-400 group-open/c:rotate-180 transition-transform" />
          </summary>
          <div className="px-4 pb-3 pt-1">
            {c.type === "library" ? <LibraryCite c={c} /> : <FreeformCite c={c} />}
          </div>
        </details>
      ))}
    </div>
  </div>
);

const SectionB_SidePanel = () => (
  <SubBlock
    title="B · Секция «Опора» в side-panel"
    hint="Раздельное представление library-backed vs freeform с явной визуальной иерархией."
  >
    <div className="grid grid-cols-3 gap-4">
      <VariantCard
        index="B1"
        name="Единый список с типизированными карточками"
        recommended
        pros={[
          "Сразу всё видно, без переключений",
          "Разная толщина рамки слева = разная «сила» источника",
          "Library — белая, freeform — приглушённый slate",
        ]}
        cons={["При >10 цитат — длинный скролл"]}
      >
        <SidePanelB1 />
      </VariantCard>
      <VariantCard
        index="B2"
        name="Табы: Библиотека / Свободные"
        pros={[
          "Чисто и компактно, особенно при многих цитатах",
          "Активный таб подсвечен своим цветом",
        ]}
        cons={[
          "Нужно переключаться — состав смешанных подкреплений не виден",
          "Скрывает свободные за вторым кликом (плохо для DISPUTED-узлов)",
        ]}
      >
        <SidePanelB2 />
      </VariantCard>
      <VariantCard
        index="B3"
        name="Accordion со strength-bar"
        pros={[
          "Strength-bar сверху показывает соотношение lib/free",
          "Список — одна строка на цитату, разворачивается по клику",
          "Хорошо для длинных списков",
        ]}
        cons={[
          "Требуется дополнительный клик чтобы прочитать цитату",
          "Strength-bar добавляет интерпретацию («средняя сила») — может быть оверклейм",
        ]}
      >
        <SidePanelB3 />
      </VariantCard>
    </div>
  </SubBlock>
);

// ---------- C. Panel header ----------

const PanelHeaderC1 = () => (
  <div className="rounded-lg border border-slate-200 bg-white overflow-hidden" style={{ width: 380 }}>
    <div className="px-4 pt-4 pb-3 border-b border-slate-200">
      <div className="flex items-center gap-2 mb-2">
        <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-bold uppercase tracking-wider bg-indigo-100 text-indigo-700">
          <I.Megaphone size={11} /> CLAIM
        </span>
        <StatusBadge status="STANDING" size="sm" />
      </div>
      <h2 className="text-[16px] font-bold text-slate-900 leading-tight">Мавлид является дозволенной практикой</h2>
      <div className="mt-2 flex items-center gap-x-3 gap-y-1 text-[11px] text-slate-500 flex-wrap">
        <span className="inline-flex items-center gap-1.5">
          <I.Anchor size={11} className="text-indigo-600" />
          <span className="font-mono font-semibold text-slate-700">4</span> подкрепления
          <span className="text-slate-300">(</span>
          <I.BookOpen size={10} className="text-indigo-600" /> <span className="font-mono">2</span>
          <span className="text-slate-300">·</span>
          <I.Quote size={10} /> <span className="font-mono">2</span>
          <span className="text-slate-300">)</span>
        </span>
        <span className="text-slate-300">·</span>
        <span className="inline-flex items-center gap-1"><I.History size={11} /> <span className="font-mono">12</span> правок</span>
        <span className="text-slate-300">·</span>
        <span className="inline-flex items-center gap-1"><I.MessageSquare size={11} /> <span className="font-mono">3</span> обсуждения</span>
      </div>
    </div>
    <div className="p-4 text-[12px] text-slate-400 italic">…дальше идут секции…</div>
  </div>
);

const PanelHeaderC2 = () => (
  <div className="rounded-lg border border-slate-200 bg-white overflow-hidden" style={{ width: 380 }}>
    <div className="px-4 pt-4 pb-2">
      <div className="flex items-center gap-2 mb-2">
        <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-bold uppercase tracking-wider bg-indigo-100 text-indigo-700">
          <I.Megaphone size={11} /> CLAIM
        </span>
        <StatusBadge status="STANDING" size="sm" />
      </div>
      <h2 className="text-[16px] font-bold text-slate-900 leading-tight">Мавлид является дозволенной практикой</h2>
    </div>
    <div className="px-4 pb-4 grid grid-cols-3 gap-2">
      {[
        { ic: "Anchor", n: 4, sub: "опора", tone: "indigo" },
        { ic: "History", n: 12, sub: "правок", tone: "slate" },
        { ic: "MessageSquare", n: 3, sub: "обсужд.", tone: "slate" },
      ].map((s, i) => {
        const Ic = I[s.ic];
        return (
          <div key={i} className={cx(
            "rounded-md border px-2.5 py-2 text-center",
            s.tone === "indigo" ? "border-indigo-200 bg-indigo-50/60" : "border-slate-200 bg-slate-50/60",
          )}>
            <div className={cx("inline-flex items-center justify-center mb-0.5", s.tone === "indigo" ? "text-indigo-700" : "text-slate-500")}>
              <Ic size={12} />
            </div>
            <div className="text-[18px] font-bold tabular-nums leading-none text-slate-900">{s.n}</div>
            <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mt-1">{s.sub}</div>
          </div>
        );
      })}
    </div>
    <div className="px-4 pb-3 border-t border-slate-100 pt-2 flex items-center gap-2 text-[10.5px] font-mono text-slate-500">
      <I.BookOpen size={10} className="text-indigo-600" /> 2 из библиотеки
      <span className="text-slate-300">·</span>
      <I.Quote size={10} /> 2 свободные
    </div>
  </div>
);

const SectionC_PanelHeader = () => (
  <SubBlock
    title="C · Header боковой панели"
    hint="Бейдж «N подкреплений» видный сразу при открытии — не приходится разворачивать секцию."
  >
    <div className="grid grid-cols-2 gap-4">
      <VariantCard
        index="C1"
        name="Inline meta-row"
        recommended
        pros={[
          "Минимум пикселей — встраивается между h2 и контентом",
          "Раздельные счётчики lib (📖 2) · free (❝ 2) в одной строке",
          "Точки-разделители делают всё сканируемым",
        ]}
        cons={["При >5 метриках строка переполняется"]}
      >
        <PanelHeaderC1 />
      </VariantCard>
      <VariantCard
        index="C2"
        name="Statistics tiles"
        pros={[
          "Каждая метрика — отдельный визуальный объект",
          "Индиго-плашка для опоры выделяется первой",
          "Хорошо когда метрик много или нужно подчеркнуть «4»",
        ]}
        cons={[
          "Занимает ~70px высоты — много для боковой панели",
          "Slate-плашки правок/обсуждений могут отвлекать",
        ]}
      >
        <PanelHeaderC2 />
      </VariantCard>
    </div>
  </SubBlock>
);

// ---------- D. Naming ----------

const NamingCard = ({ name, gloss, recommended, ar, pros, cons }) => (
  <div className={cx(
    "rounded-lg border bg-white p-4",
    recommended ? "border-indigo-500 ring-2 ring-indigo-200" : "border-slate-200",
  )}>
    <div className="flex items-baseline gap-2 mb-1.5">
      <span className="text-[18px] font-bold text-slate-900">{name}</span>
      {ar && <span dir="rtl" className="font-naskh text-[15px] text-slate-500">{ar}</span>}
      {recommended && (
        <span className="ml-auto inline-flex items-center gap-1 px-1.5 h-5 rounded bg-indigo-600 text-white text-[10px] font-bold uppercase tracking-wider">
          <I.Star size={10} /> рекомендую
        </span>
      )}
    </div>
    <div className="text-[12px] text-slate-600 italic mb-2">{gloss}</div>
    <ul className="space-y-1 text-[11.5px] leading-snug">
      {pros.map((p, i) => <li key={`p${i}`} className="flex items-start gap-1.5 text-emerald-800"><I.Check size={11} className="mt-[2px] shrink-0" />{p}</li>)}
      {cons.map((c, i) => <li key={`c${i}`} className="flex items-start gap-1.5 text-rose-800"><I.X size={11} className="mt-[2px] shrink-0" />{c}</li>)}
    </ul>
  </div>
);

const SectionD_Naming = () => (
  <SubBlock
    title="D · Имя секции"
    hint="Подходит ли «Цитаты» для исламского контекста? Альтернативы и обоснование."
  >
    <div className="grid grid-cols-3 gap-3">
      <NamingCard
        name="Опора"
        ar="مُسْتَنَدٌ"
        gloss="То, на что опирается тезис — нассы Корана и Сунны, фетвы, источники"
        recommended
        pros={[
          "Прямой смысловой эквивалент مُسْتَنَدٌ / دَلِيلٌ",
          "Не сводится к «цитированию» — включает мнения учёных",
          "Покрывает оба типа: библиотека и свободное",
        ]}
        cons={["Менее очевидно для нерелигиозного юзера"]}
      />
      <NamingCard
        name="Подкрепления"
        gloss="Нейтральное аргументационное слово"
        pros={[
          "Универсально: годится для научных и фикх-узлов",
          "Хорошо ложится в Tooltip / labels",
        ]}
        cons={[
          "Менее насыщенно семантически",
          "Звучит как калька с «backings»",
        ]}
      />
      <NamingCard
        name="Источники"
        gloss="Прямая, нейтральная формулировка"
        pros={[
          "Понятно с первой секунды",
          "Привычно по academic-стилю",
        ]}
        cons={[
          "Сильно перегружено: НИ источник, НИ нумерация, ничего из ислам. традиции не подразумевает",
          "Конфликтует со словом «source» (книга в библиотеке)",
        ]}
      />
      <NamingCard
        name="Доказательная база"
        gloss="Юридический термин, ближе к фикх-стилю"
        pros={[
          "Подчёркивает «доказательность»",
          "Хорошо для DISPUTED/REFUTED статусов",
        ]}
        cons={[
          "Длинно — не помещается в badge",
          "Звучит формально-юридически, не подходит для QUESTION",
        ]}
      />
      <NamingCard
        name="Дȧлиль"
        ar="دَلِيلٌ"
        gloss="Шариатский термин «доказательство»"
        pros={[
          "Точный технический термин в усуль аль-фикх",
          "Уважительно к традиции",
        ]}
        cons={[
          "Кириллица + транслит — UI становится неровным",
          "Не покрывает академические/научные узлы",
        ]}
      />
      <NamingCard
        name="Сноски"
        gloss="Из академического вёрсточного словаря"
        pros={["Нейтрально, привычно для научных текстов"]}
        cons={[
          "Семантически слабо — «footnotes» это формат, а не доказательство",
          "Не отражает важности привязки",
        ]}
      />
    </div>
    <div className="mt-5 rounded-lg border border-indigo-200 bg-indigo-50/40 p-4">
      <div className="flex items-center gap-2 mb-2">
        <I.Star size={14} className="text-indigo-600" />
        <span className="text-[13px] font-bold text-indigo-900">Рекомендация: «Опора»</span>
      </div>
      <p className="text-[12.5px] text-slate-700 leading-relaxed">
        В контексте исламской науки нассы (Коран и хадисы) — это <em>опора</em> для худжжи (доказательной силы) тезиса.
        Слово укоренено и в академическом, и в фикхском дискурсе («опираться на», «опора»), универсально покрывает
        и Бухари с привязкой по странице, и свободную ссылку на фетву, и научную статью. Семантически богаче чем
        «Цитаты», но без перегруза «Дȧлиля» или «Доказательной базы».
      </p>
      <p className="mt-2 text-[12.5px] text-slate-700 leading-relaxed">
        В UI: header пилюлю и tooltip — <span className="font-mono text-[12px]">Опора</span>; на NodeCard — иконка{" "}
        <I.Anchor size={11} className="inline -mt-0.5 text-indigo-600" /> якоря (опора = anchor, метафорически прозрачно
        и работает в обоих языках).
      </p>
    </div>
  </SubBlock>
);

// ---------- Wrapper ----------

const CitationsBlock = () => (
  <Block id="citations" kicker="08 — domain · adr-026 / adr-027" title="Опора · подкрепления узла">
    <div className="rounded-lg border border-slate-200 bg-white p-4 mb-8">
      <div className="flex items-baseline gap-2 mb-2">
        <span className="text-[13px] font-bold text-slate-900">Контекст</span>
        <span className="text-[10px] font-mono text-slate-400">ADR-026 + ADR-027</span>
      </div>
      <div className="grid grid-cols-2 gap-4 text-[12.5px] text-slate-700 leading-relaxed">
        <div className="flex gap-3">
          <span className="shrink-0 inline-flex items-center justify-center h-7 w-7 rounded bg-indigo-100 text-indigo-700"><I.BookOpen size={14} /></span>
          <div>
            <div className="font-semibold text-slate-900 mb-0.5">Из библиотеки <span className="font-mono text-[11px] text-slate-500">(positional)</span></div>
            <p className="text-slate-600">Привязка к импортированной книге shamela.ws с book + page + precise text range. Сильное доказательство — deep link с подсветкой фрагмента.</p>
          </div>
        </div>
        <div className="flex gap-3">
          <span className="shrink-0 inline-flex items-center justify-center h-7 w-7 rounded bg-slate-100 text-slate-600"><I.Quote size={14} /></span>
          <div>
            <div className="font-semibold text-slate-900 mb-0.5">Свободное <span className="font-mono text-[11px] text-slate-500">(AddSourceModal)</span></div>
            <p className="text-slate-600">URL статьи, ручной ввод хадиса, цитата по памяти. Без библиотечной привязки. Слабее как доказательство — но иногда единственный путь.</p>
          </div>
        </div>
      </div>
    </div>
    <SectionA_NodeCardVariants />
    <SectionB_SidePanel />
    <SectionC_PanelHeader />
    <SectionD_Naming />
  </Block>
);

window.CitationsBlock = CitationsBlock;
