// Islamic-context extensions for Argument Map showcase.
// Bilingual cards, Arabic tokens, source library, authorities, RTL demo, settings.

const AR = {
  bismillah: "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ",
  niyyah: "إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى",
  niyyahShort: "إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ",
  ayatAlKursi: "ٱللَّهُ لَا إِلَـٰهَ إِلَّا هُوَ ٱلْحَيُّ ٱلْقَيُّومُ ۚ لَا تَأْخُذُهُۥ سِنَةٌ وَلَا نَوْمٌ",
  monday: "ذَاكَ يَوْمٌ وُلِدْتُ فِيهِ، وَيَوْمٌ بُعِثْتُ فِيهِ",
  bidah: "كُلُّ بِدْعَةٍ ضَلَالَةٌ",
  suyuti: "حُسْنُ الْمَقْصِدِ فِي عَمَلِ الْمَوْلِدِ",
  ibnTaymiyyah: "ابْنُ تَيْمِيَّةَ",
  ibnHajar: "ابْنُ حَجَرٍ الْعَسْقَلَانِيُّ",
  asSuyuti: "جَلَالُ الدِّينِ السُّيُوطِيُّ",
  ibnKathir: "ابْنُ كَثِيرٍ",
  hadith: "حَدِيثٌ",
  ayah: "آيَةٌ",
  argMap: "خَرِيطَةُ الْحُجَجِ",
};

// === Hadith grades ============================================================

const HADITH_GRADE = {
  SAHIH:  { key: "SAHIH",  label: "Сахих",     ar: "صَحِيحٌ",   tone: "emerald", desc: "Достоверный" },
  HASAN:  { key: "HASAN",  label: "Хасан",     ar: "حَسَنٌ",     tone: "amber",   desc: "Хороший" },
  DAIF:   { key: "DAIF",   label: "Даиф",      ar: "ضَعِيفٌ",    tone: "red",     desc: "Слабый" },
  MAWDU:  { key: "MAWDU",  label: "Мауду",     ar: "مَوْضُوعٌ",  tone: "rose",    desc: "Подложный" },
};

const GradeBadge = ({ grade, size = "md" }) => {
  const g = HADITH_GRADE[grade];
  return (
    <Badge tone={g.tone} size={size}>
      <span className="font-mono font-bold tracking-wider">{g.label.toUpperCase()}</span>
      <span className="font-naskh text-[12px] mx-0.5">{g.ar}</span>
    </Badge>
  );
};

// === Bilingual NodeCard =======================================================

const BilingualNodeCard = ({
  type = "EVIDENCE",
  status = "STANDING",
  title,
  ar,
  arFont = "naskh",
  translation,
  citation,
  grade,
  authority,
  width = 320,
  selected = false,
}) => {
  const t = NODE_TYPE[type];
  const s = STATUS[status];
  const Icon = I[t.icon];
  return (
    <div
      style={{ width }}
      className={cx(
        "relative rounded-xl bg-white border transition-shadow overflow-hidden",
        selected
          ? "border-indigo-500 shadow-[0_0_0_3px_rgba(99,102,241,0.18),0_8px_20px_rgba(15,23,42,0.10)]"
          : "border-slate-200 shadow-[0_1px_2px_rgba(15,23,42,0.04),0_2px_6px_rgba(15,23,42,0.04)]"
      )}
    >
      <div className={cx("absolute left-0 top-0 bottom-0 w-[5px]", s.bar)} />
      <div className="pl-4 pr-3 py-3">
        <div className="flex items-center gap-2 mb-1.5">
          <span className={cx("inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-bold uppercase tracking-wider", t.chipBg, t.chipText)}>
            <Icon size={11} />
            {t.label}
          </span>
          {grade && <GradeBadge grade={grade} size="sm" />}
          <span className="flex-1" />
          <StatusBadge status={status} size="sm" />
        </div>

        {title && <div className="text-[12px] font-semibold leading-snug text-slate-900 mb-2 text-pretty">{title}</div>}

        {/* Arabic */}
        <div dir="rtl" className={cx("font-" + arFont, "arabic-text text-[18px] text-slate-900 text-pretty mb-2")}>
          {ar}
        </div>

        <div className="h-px bg-slate-100 my-2" />

        {/* Translation */}
        <div className="text-[12px] leading-relaxed text-slate-700 text-pretty">{translation}</div>

        {/* Citation */}
        {citation && (
          <div className="mt-2 pt-2 border-t border-slate-100 flex items-center gap-1.5 text-[11px] font-mono text-slate-500">
            <I.Quote size={11} />
            <span className="truncate">{citation}</span>
          </div>
        )}

        {/* Authority pin */}
        {authority && (
          <div className="mt-2 flex items-center gap-1.5 px-2 py-1 rounded-md bg-emerald-50 border border-emerald-200">
            <I.Pin size={11} className="text-emerald-700" />
            <span className="text-[11px] text-emerald-900 font-medium">{authority.name}</span>
            <Badge tone="emerald" size="sm">{authority.stance}</Badge>
          </div>
        )}
      </div>
    </div>
  );
};

// === Language switcher ========================================================

const LANGS = [
  { code: "RU", label: "Русский", native: "Русский", flag: "RU" },
  { code: "EN", label: "English", native: "English", flag: "EN" },
  { code: "AR", label: "العربية", native: "العربية", flag: "AR", rtl: true },
];

const LanguageSwitcher = ({ open = false, current = "RU" }) => (
  <div className="relative inline-block">
    <button className="inline-flex items-center gap-1.5 h-8 px-2.5 rounded-md border border-slate-300 bg-white hover:bg-slate-50 text-[12px] font-medium text-slate-700">
      <I.Languages size={14} className="text-slate-500" />
      <span>{current}</span>
      <I.ChevronDown size={12} className="text-slate-400" />
    </button>
    {open && (
      <div className="absolute top-full left-0 mt-1 w-[200px] rounded-lg bg-white border border-slate-200 shadow-xl py-1 z-10">
        <div className="px-3 py-1.5 text-[10px] font-semibold uppercase tracking-wider text-slate-500 border-b border-slate-100">Язык интерфейса</div>
        {LANGS.map((l) => (
          <button key={l.code} className={cx(
            "w-full flex items-center gap-3 px-3 py-2 text-left transition-colors",
            l.code === current ? "bg-indigo-50/60" : "hover:bg-slate-50"
          )}>
            <span className="font-mono text-[10px] font-bold text-slate-500 w-6">{l.code}</span>
            <span className={cx("text-[13px] flex-1", l.rtl ? "font-arabic-sans text-right" : "")} dir={l.rtl ? "rtl" : "ltr"}>{l.native}</span>
            {l.code === current && <I.Check size={14} className="text-indigo-600" />}
          </button>
        ))}
        <div className="border-t border-slate-100 mt-1 pt-1">
          <button className="w-full flex items-center gap-2 px-3 py-1.5 text-left hover:bg-slate-50">
            <I.Settings size={13} className="text-slate-500" />
            <span className="text-[12px] text-slate-700">Настройки языка</span>
          </button>
        </div>
      </div>
    )}
  </div>
);

// Add Languages icon (lucide)
if (!I.Languages) {
  I.Languages = (p) => (
    <svg xmlns="http://www.w3.org/2000/svg" width={p.size || 20} height={p.size || 20} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className={p.className}>
      <path d="m5 8 6 6" /><path d="m4 14 6-6 2-3" /><path d="M2 5h12" /><path d="M7 2h1" /><path d="m22 22-5-10-5 10" /><path d="M14 18h6" />
    </svg>
  );
}
if (!I.Library) {
  I.Library = (p) => (
    <svg xmlns="http://www.w3.org/2000/svg" width={p.size || 20} height={p.size || 20} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className={p.className}>
      <path d="m16 6 4 14" /><path d="M12 6v14" /><path d="M8 8v12" /><path d="M4 4v16" />
    </svg>
  );
}
if (!I.BookText) {
  I.BookText = (p) => (
    <svg xmlns="http://www.w3.org/2000/svg" width={p.size || 20} height={p.size || 20} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className={p.className}>
      <path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20" /><path d="M8 7h6" /><path d="M8 11h8" />
    </svg>
  );
}
if (!I.GraduationCap) {
  I.GraduationCap = (p) => (
    <svg xmlns="http://www.w3.org/2000/svg" width={p.size || 20} height={p.size || 20} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className={p.className}>
      <path d="M22 10v6" /><path d="M2 10 12 5l10 5-10 5z" /><path d="M6 12v5c3 3 9 3 12 0v-5" />
    </svg>
  );
}
if (!I.ExternalLink) {
  I.ExternalLink = (p) => (
    <svg xmlns="http://www.w3.org/2000/svg" width={p.size || 20} height={p.size || 20} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className={p.className}>
      <path d="M15 3h6v6" /><path d="M10 14 21 3" /><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
    </svg>
  );
}

// === Arabic typography tokens =================================================

const ArabicTypographySection = () => (
  <SubSection title="Арабская типографика" hint="Учёный не доверяет переводу без оригинала. Шрифты и размеры подобраны под классическое чтение.">
    <Card className="overflow-hidden">
      <div className="grid grid-cols-2">
        {/* Quranic */}
        <div className="p-6 border-r border-b border-slate-200 bg-gradient-to-br from-emerald-50/40 to-white">
          <div className="flex items-center justify-between mb-3">
            <div>
              <div className="text-[11px] font-mono font-semibold text-emerald-700 uppercase tracking-wider">Coranic · Amiri</div>
              <div className="text-[12px] text-slate-500 mt-0.5">Классический насх для текста Корана</div>
            </div>
            <Badge tone="emerald" size="sm">Quran</Badge>
          </div>
          <div dir="rtl" className="font-amiri arabic-text text-[28px] text-slate-900 mt-4 leading-[2]">{AR.bismillah}</div>
          <div className="mt-3 font-mono text-[10px] text-slate-500">font-family: Amiri · 28px / 700 · line-height 2.0</div>
        </div>
        {/* Hadith */}
        <div className="p-6 border-b border-slate-200 bg-gradient-to-br from-slate-50 to-white">
          <div className="flex items-center justify-between mb-3">
            <div>
              <div className="text-[11px] font-mono font-semibold text-slate-700 uppercase tracking-wider">Hadith / Classical · Noto Naskh</div>
              <div className="text-[12px] text-slate-500 mt-0.5">Хадисы и классические трактаты</div>
            </div>
            <Badge tone="slate" size="sm">Hadith</Badge>
          </div>
          <div dir="rtl" className="font-naskh arabic-text text-[22px] text-slate-900 mt-4 leading-[1.9]">{AR.niyyahShort}</div>
          <div className="mt-3 font-mono text-[10px] text-slate-500">font-family: Noto Naskh Arabic · 22px / 500 · line-height 1.9</div>
        </div>
        {/* UI sans */}
        <div className="p-6 border-r border-slate-200">
          <div className="flex items-center justify-between mb-3">
            <div>
              <div className="text-[11px] font-mono font-semibold text-indigo-700 uppercase tracking-wider">UI · Noto Sans Arabic</div>
              <div className="text-[12px] text-slate-500 mt-0.5">Кнопки, навигация, заголовки в RTL-режиме</div>
            </div>
            <Badge tone="indigo" size="sm">UI</Badge>
          </div>
          <div dir="rtl" className="font-arabic-sans text-slate-900 mt-4 space-y-2">
            <div className="text-[24px] font-bold">{AR.argMap}</div>
            <div className="text-[14px]">إنشاء موضوع جديد · حفظ · مشاركة</div>
          </div>
          <div className="mt-3 font-mono text-[10px] text-slate-500">font-family: Noto Sans Arabic · 14–24px · UI weights</div>
        </div>
        {/* Display */}
        <div className="p-6">
          <div className="flex items-center justify-between mb-3">
            <div>
              <div className="text-[11px] font-mono font-semibold text-violet-700 uppercase tracking-wider">Display · Reem Kufi</div>
              <div className="text-[12px] text-slate-500 mt-0.5">Акценты, заголовки секций, обложки</div>
            </div>
            <Badge tone="violet" size="sm">Display</Badge>
          </div>
          <div dir="rtl" className="font-kufi text-slate-900 mt-4 text-[36px] font-bold leading-tight">{AR.argMap}</div>
          <div className="mt-3 font-mono text-[10px] text-slate-500">font-family: Reem Kufi · 36px / 700 · headlines</div>
        </div>
      </div>
    </Card>
    <div className="mt-4 grid grid-cols-3 gap-3">
      <Card className="p-4">
        <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-1">Без огласовок · sans-haraqat</div>
        <div dir="rtl" className="font-naskh text-[20px] text-slate-900 leading-[1.9]">إنما الأعمال بالنيات</div>
        <div className="mt-2 text-[11px] text-slate-500">Для опытных читателей. Toggle в Settings.</div>
      </Card>
      <Card className="p-4">
        <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-1">С огласовками · with-haraqat</div>
        <div dir="rtl" className="font-naskh text-[20px] text-slate-900 leading-[1.9]">إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ</div>
        <div className="mt-2 text-[11px] text-slate-500">Дефолт. Точное чтение, начальный уровень.</div>
      </Card>
      <Card className="p-4">
        <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-1">Транслит · transliteration</div>
        <div className="text-[16px] italic text-slate-900 leading-relaxed">Innamā al-aʿmālu bi-n-niyyāt</div>
        <div className="mt-2 text-[11px] text-slate-500">Для имён авторитетов и терминов.</div>
      </Card>
    </div>
  </SubSection>
);

// === Authorities ============================================================

const AUTHORITIES = [
  { ar: "ابْنُ تَيْمِيَّةَ", name: "Ибн Таймия", trans: "Ibn Taymiyyah", era: "VII–VIII в.х.", years: "1263–1328", madhhab: "ханбалитский", color: "rose", initials: "ابت", stance: "Opposes" },
  { ar: "ابْنُ حَجَرٍ الْعَسْقَلَانِيُّ", name: "Ибн Хаджар аль-Аскаляни", trans: "Ibn Ḥajar al-ʿAsqalānī", era: "VIII–IX в.х.", years: "1372–1449", madhhab: "шафиитский", color: "emerald", initials: "ابح", stance: "Holds" },
  { ar: "جَلَالُ الدِّينِ السُّيُوطِيُّ", name: "Джалал ад-Дин ас-Суюти", trans: "Jalāl al-Dīn al-Suyūṭī", era: "IX в.х.", years: "1445–1505", madhhab: "шафиитский", color: "emerald", initials: "السي", stance: "Holds" },
  { ar: "ابْنُ كَثِيرٍ", name: "Ибн Касир", trans: "Ibn Kathīr", era: "VIII в.х.", years: "1300–1373", madhhab: "шафиитский", color: "indigo", initials: "ابك", stance: "Neutral" },
  { ar: "الْإِمَامُ مَالِكٌ", name: "Имам Малик", trans: "Imam Mālik", era: "II в.х.", years: "711–795", madhhab: "маликитский", color: "amber", initials: "ام", stance: "Holds" },
  { ar: "ابْنُ الْحَاجِّ", name: "Ибн аль-Хадж", trans: "Ibn al-Ḥāj", era: "VIII в.х.", years: "1250–1336", madhhab: "маликитский", color: "rose", initials: "ابح", stance: "Opposes" },
];

const AuthorityCard = ({ a, showStance = false }) => {
  const stanceMap = {
    Holds: { tone: "emerald", label: "Поддерживает", ar: "يَقْبَلُ" },
    Opposes: { tone: "red", label: "Возражает", ar: "يُعَارِضُ" },
    Neutral: { tone: "slate", label: "Нейтрален", ar: "حَيَادِيٌّ" },
  };
  const colors = {
    emerald: "bg-emerald-100 text-emerald-700",
    rose: "bg-rose-100 text-rose-700",
    indigo: "bg-indigo-100 text-indigo-700",
    amber: "bg-amber-100 text-amber-800",
    slate: "bg-slate-100 text-slate-700",
  };
  const st = stanceMap[a.stance];
  return (
    <Card className="p-4" style={{ width: 280, height: 156 }}>
      <div className="flex items-start gap-3">
        <div className={cx("h-12 w-12 rounded-full grid place-items-center font-naskh text-[16px] font-bold ring-2 ring-white shadow-sm shrink-0", colors[a.color])}>
          {a.initials}
        </div>
        <div className="flex-1 min-w-0">
          <div dir="rtl" className="font-naskh text-[18px] font-bold text-slate-900 leading-tight truncate">{a.ar}</div>
          <div className="text-[12px] font-semibold text-slate-700 mt-0.5 truncate">{a.name}</div>
          <div className="text-[10px] font-mono text-slate-500 truncate">{a.trans}</div>
        </div>
      </div>
      <div className="mt-3 flex items-center gap-1.5 text-[11px] text-slate-600">
        <I.Calendar size={11} className="text-slate-400" />
        <span>{a.years}</span>
        <span className="text-slate-400">·</span>
        <span className="font-mono text-[10px]">{a.era}</span>
      </div>
      <div className="mt-2 flex items-center gap-1.5">
        <Badge tone="indigo" size="sm" icon="GraduationCap">{a.madhhab}</Badge>
        {showStance && <Badge tone={st.tone} size="sm">{st.label}</Badge>}
      </div>
    </Card>
  );
};

const AuthoritiesSection = () => (
  <Section id="authorities" title="Авторитеты · учёные и их позиции" kicker="14 — authorities" hint="Каждый авторитет — first-class сущность. Имя на арабском, эпоха, мазхаб, stance при привязке.">
    <SubSection title="Карточки авторитетов">
      <div className="grid grid-cols-3 gap-4">
        {AUTHORITIES.map((a) => <AuthorityCard key={a.name} a={a} />)}
      </div>
    </SubSection>
    <SubSection title="Stance — позиция авторитета по узлу">
      <Card className="p-5">
        <div className="flex items-center justify-between mb-4">
          <div>
            <div className="text-[12px] font-semibold text-slate-700">Привязано к узлу</div>
            <div className="text-[14px] font-semibold text-slate-900 mt-0.5">CLAIM · «Мавлид является дозволенной практикой»</div>
          </div>
          <Button variant="secondary" size="sm" icon="Plus">Привязать авторитета</Button>
        </div>
        <div className="grid grid-cols-3 gap-3">
          <AuthorityCard a={AUTHORITIES[1]} showStance />
          <AuthorityCard a={AUTHORITIES[2]} showStance />
          <AuthorityCard a={AUTHORITIES[0]} showStance />
        </div>
      </Card>
    </SubSection>
  </Section>
);

// === Bilingual node cards section ===========================================

const BilingualSection = () => (
  <Section id="bilingual" title="Двуязычные карточки · оригинал + перевод" kicker="04b — bilingual" hint="EVIDENCE и ARGUMENT с арабским первоисточником и параллельным переводом. Toggle оригинал / перевод / оба.">
    <div className="grid grid-cols-3 gap-5">
      <BilingualNodeCard
        type="EVIDENCE"
        status="STANDING"
        title="Аят аль-Курси"
        ar={AR.ayatAlKursi}
        arFont="amiri"
        translation="«Аллах — нет божества кроме Него, Живого, Поддерживающего жизнь. Не клонит Его в дрёму и не одолевает сон…»"
        citation="Коран · Аль-Бакара 2:255"
        grade={null}
      />
      <BilingualNodeCard
        type="EVIDENCE"
        status="STANDING"
        title="Хадис о намерениях"
        ar={AR.niyyah}
        arFont="naskh"
        translation="«Поистине, дела (оцениваются) только по намерениям, и каждому человеку — то, что он намеревался…»"
        citation="Бухари 1 · Муслим 1907"
        grade="SAHIH"
      />
      <BilingualNodeCard
        type="EVIDENCE"
        status="STANDING"
        title="Хадис о понедельнике"
        ar={AR.monday}
        arFont="naskh"
        translation="«Это день, в который я родился, и день, в который мне было ниспослано откровение»"
        citation="Сахих Муслим 1162"
        grade="SAHIH"
      />
      <BilingualNodeCard
        type="EVIDENCE"
        status="REFUTED"
        title="Хадис о новшестве"
        ar={AR.bidah}
        arFont="naskh"
        translation="«Каждое новшество — заблуждение». Контекст хадиса — предмет дискуссии."
        citation="Сунан Абу Дауд 4607"
        grade="SAHIH"
      />
      <BilingualNodeCard
        type="ARGUMENT"
        status="STANDING"
        title="Цитата ас-Суюти"
        ar="فَإِنَّ أَصْلَ عَمَلِ الْمَوْلِدِ الَّذِي هُوَ اجْتِمَاعُ النَّاسِ ... بِدْعَةٌ حَسَنَةٌ"
        arFont="naskh"
        translation="«Основание мавлида — собрание людей, чтение Корана и сообщений о рождении Пророка ﷺ — благое нововведение, за которое его автор будет вознаграждён»"
        citation="Хусн уль-максид · т. 1 · стр. 22 · shamela.ws"
        authority={{ name: "ас-Суюти", stance: "Holds" }}
      />
      <BilingualNodeCard
        type="ARGUMENT"
        status="REFUTED"
        title="Цитата Ибн Таймии"
        ar="وَأَمَّا اتِّخَاذُهُ مَوْسِمًا ... فَهَذَا لَمْ يَفْعَلْهُ السَّلَفُ"
        arFont="naskh"
        translation="«Что касается превращения [дня рождения] в праздник — этого не делали саляфы, хотя для этого был и повод, и отсутствие препятствий»"
        citation="Иктидаъ ас-сырат · т. 2 · стр. 124 · shamela.ws"
        authority={{ name: "Ибн Таймия", stance: "Opposes" }}
      />
    </div>

    <SubSection title="Toggle режима — оригинал · перевод · оба" className="mt-10">
      <div className="grid grid-cols-3 gap-4 items-start">
        {[
          { mode: "ar", label: "Только оригинал", desc: "Для тех кто читает свободно" },
          { mode: "both", label: "Оригинал + перевод", desc: "Дефолт · академическая работа" },
          { mode: "tr", label: "Только перевод", desc: "Беглый просмотр / обзор темы" },
        ].map((m) => (
          <div key={m.mode}>
            <div className="mb-2 inline-flex items-center gap-1 rounded-md bg-slate-100 p-0.5">
              {["ar", "both", "tr"].map((opt) => (
                <span key={opt} className={cx(
                  "px-2 h-6 inline-flex items-center text-[11px] font-medium rounded",
                  opt === m.mode ? "bg-white text-slate-900 shadow-sm" : "text-slate-600"
                )}>
                  {opt === "ar" ? "ع" : opt === "tr" ? "ru" : "ع/ru"}
                </span>
              ))}
            </div>
            <div className="text-[10px] font-mono text-slate-500 mb-2">{m.label} · {m.desc}</div>
            {m.mode === "ar" && (
              <div className="rounded-xl border-l-[5px] border-emerald-500 border border-slate-200 bg-white p-3" style={{ width: 280 }}>
                <div className="flex items-center gap-1.5 mb-2">
                  <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-bold bg-teal-100 text-teal-700">EVIDENCE</span>
                  <GradeBadge grade="SAHIH" size="sm" />
                </div>
                <div dir="rtl" className="font-naskh text-[19px] text-slate-900 arabic-text">{AR.niyyahShort}</div>
                <div className="mt-2 pt-2 border-t border-slate-100 font-mono text-[11px] text-slate-500">Бухари 1</div>
              </div>
            )}
            {m.mode === "both" && (
              <BilingualNodeCard
                type="EVIDENCE" status="STANDING"
                ar={AR.niyyahShort} arFont="naskh"
                translation="Поистине, дела — по намерениям…"
                citation="Бухари 1 · Муслим 1907" grade="SAHIH" width={280}
              />
            )}
            {m.mode === "tr" && (
              <NodeCard type="EVIDENCE" status="STANDING" title="«Поистине, дела — по намерениям…»" body="Сахих аль-Бухари, хадис №1. Один из самых известных хадисов." width={280} />
            )}
          </div>
        ))}
      </div>
    </SubSection>
  </Section>
);

// === Source Library =========================================================

const SOURCE_TABS = [
  { key: "quran", label: "Коран", icon: "BookOpen", count: "6,236 аятов", domain: "quran.com" },
  { key: "hadith", label: "Хадисы", icon: "ScrollText", count: "9 сборников", domain: "sunnah.com" },
  { key: "books", label: "Книги (Шамиля)", icon: "Library", count: "100k+ томов", domain: "shamela.ws" },
  { key: "free", label: "Свободная цитата", icon: "Quote", count: "ручной ввод", domain: "—" },
];

const SourcePickerQuran = () => {
  const surahs = [
    { num: 1, ar: "الْفَاتِحَةُ", name: "Аль-Фатиха", verses: 7 },
    { num: 2, ar: "الْبَقَرَةُ", name: "Аль-Бакара", verses: 286, active: true },
    { num: 3, ar: "آلُ عِمْرَانَ", name: "Аль-Имран", verses: 200 },
    { num: 4, ar: "النِّسَاءُ", name: "Ан-Ниса", verses: 176 },
    { num: 5, ar: "الْمَائِدَةُ", name: "Аль-Маида", verses: 120 },
    { num: 18, ar: "الْكَهْفُ", name: "Аль-Кахф", verses: 110 },
    { num: 36, ar: "يس", name: "Я-Син", verses: 83 },
    { num: 112, ar: "الْإِخْلَاصُ", name: "Аль-Ихлас", verses: 4 },
  ];
  const verses = [
    { v: 152, ar: "فَاذْكُرُونِي أَذْكُرْكُمْ وَاشْكُرُوا لِي وَلَا تَكْفُرُونِ", tr: "Поминайте Меня, и Я буду помнить о вас. Благодарите Меня и не будьте неблагодарны." },
    { v: 255, ar: "ٱللَّهُ لَا إِلَـٰهَ إِلَّا هُوَ ٱلْحَيُّ ٱلْقَيُّومُ ۚ لَا تَأْخُذُهُۥ سِنَةٌ وَلَا نَوْمٌ", tr: "Аллах — нет божества, кроме Него, Живого, Поддерживающего жизнь…", selected: true },
    { v: 256, ar: "لَا إِكْرَاهَ فِي الدِّينِ ۖ قَد تَّبَيَّنَ الرُّشْدُ مِنَ الْغَيِّ", tr: "Нет принуждения в религии. Прямой путь уже отличился от заблуждения." },
    { v: 286, ar: "لَا يُكَلِّفُ اللَّهُ نَفْسًا إِلَّا وُسْعَهَا ۚ لَهَا مَا كَسَبَتْ وَعَلَيْهَا مَا اكْتَسَبَتْ", tr: "Аллах не возлагает на душу больше того, что она может вынести…" },
  ];
  return (
    <div className="grid grid-cols-[280px_1fr] h-[460px]">
      {/* Surah list */}
      <div className="border-r border-slate-200 flex flex-col">
        <div className="p-3 border-b border-slate-200">
          <Input icon="Search" placeholder="Поиск суры…" />
        </div>
        <div className="flex-1 overflow-auto">
          {surahs.map((s) => (
            <button key={s.num} className={cx(
              "w-full flex items-center gap-3 px-3 py-2 text-left border-b border-slate-100 hover:bg-slate-50 transition-colors",
              s.active && "bg-emerald-50/60 border-l-[3px] border-l-emerald-500"
            )}>
              <span className="font-mono text-[10px] font-bold text-slate-500 w-6 text-right">{s.num}.</span>
              <div className="flex-1 min-w-0">
                <div dir="rtl" className="font-amiri text-[16px] font-bold text-slate-900 leading-tight">{s.ar}</div>
                <div className="text-[11px] text-slate-600">{s.name}</div>
              </div>
              <span className="font-mono text-[10px] text-slate-400">{s.verses}</span>
            </button>
          ))}
        </div>
      </div>
      {/* Verses */}
      <div className="flex flex-col">
        <div className="p-3 border-b border-slate-200 flex items-center gap-2">
          <I.BookOpen size={14} className="text-emerald-700" />
          <span className="text-[13px] font-semibold text-slate-900">Аль-Бакара</span>
          <span dir="rtl" className="font-naskh text-[14px] text-slate-700">سُورَةُ الْبَقَرَةِ</span>
          <span className="text-[11px] text-slate-500">· 286 аятов · мадинская</span>
          <span className="ml-auto text-[10px] font-mono text-slate-400">источник: quran.com</span>
        </div>
        <div className="flex-1 overflow-auto p-3 space-y-2 bg-slate-50/40">
          {verses.map((v) => (
            <label key={v.v} className={cx(
              "flex gap-3 p-3 rounded-lg border bg-white cursor-pointer transition-colors",
              v.selected ? "border-emerald-400 ring-1 ring-emerald-300" : "border-slate-200 hover:border-slate-300"
            )}>
              <div className="flex flex-col items-center pt-0.5">
                <input type="checkbox" defaultChecked={v.selected} className="accent-emerald-600" />
                <span className="font-mono text-[10px] font-bold text-emerald-700 mt-1">{v.v}</span>
              </div>
              <div className="flex-1 min-w-0">
                <div dir="rtl" className="font-amiri text-[20px] arabic-text text-slate-900 leading-[2]">{v.ar}</div>
                <div className="mt-1.5 text-[12px] leading-relaxed text-slate-600">{v.tr}</div>
              </div>
            </label>
          ))}
        </div>
        <div className="p-3 border-t border-slate-200 bg-white flex items-center justify-between">
          <div className="text-[11px] text-slate-600 inline-flex items-center gap-1.5">
            <I.Quote size={11} className="text-emerald-700" />
            Будет вставлено: <span className="font-mono font-semibold">Коран 2:255</span>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="ghost" size="sm">Отмена</Button>
            <Button variant="primary" size="sm" icon="Link">Привязать аят</Button>
          </div>
        </div>
      </div>
    </div>
  );
};

const SourcePickerHadith = () => {
  const collections = [
    { ar: "صَحِيحُ الْبُخَارِيِّ", name: "Сахих аль-Бухари", n: 7563, active: true },
    { ar: "صَحِيحُ مُسْلِمٍ", name: "Сахих Муслим", n: 7470 },
    { ar: "سُنَنُ التِّرْمِذِيِّ", name: "Сунан ат-Тирмизи", n: 3956 },
    { ar: "سُنَنُ أَبِي دَاوُدَ", name: "Сунан Абу Дауд", n: 5274 },
    { ar: "سُنَنُ النَّسَائِيِّ", name: "Сунан ан-Насаи", n: 5662 },
    { ar: "سُنَنُ ابْنِ مَاجَهْ", name: "Сунан Ибн Маджа", n: 4341 },
    { ar: "مُوَطَّأُ مَالِكٍ", name: "Муватта Малика", n: 1851 },
    { ar: "مُسْنَدُ أَحْمَدَ", name: "Муснад Ахмада", n: 27647 },
  ];
  const sanad = ["аль-Хумайди", "Суфьян", "Яхья ибн Саид", "Мухаммад ибн Ибрахим", "Алькама ибн Ваккас", "Умар ибн аль-Хаттаб ↬"];
  return (
    <div className="grid grid-cols-[280px_1fr] h-[460px]">
      <div className="border-r border-slate-200 flex flex-col">
        <div className="p-3 border-b border-slate-200">
          <Input icon="Search" placeholder="Поиск по сборникам…" />
        </div>
        <div className="flex-1 overflow-auto">
          {collections.map((c) => (
            <button key={c.name} className={cx(
              "w-full flex items-center gap-3 px-3 py-2 text-left border-b border-slate-100 hover:bg-slate-50",
              c.active && "bg-indigo-50/60 border-l-[3px] border-l-indigo-500"
            )}>
              <I.ScrollText size={14} className="text-slate-500" />
              <div className="flex-1 min-w-0">
                <div dir="rtl" className="font-naskh text-[14px] font-bold text-slate-900 leading-tight truncate">{c.ar}</div>
                <div className="text-[11px] text-slate-600">{c.name}</div>
              </div>
              <span className="font-mono text-[10px] text-slate-400">{c.n.toLocaleString()}</span>
            </button>
          ))}
        </div>
      </div>
      <div className="flex flex-col">
        <div className="p-3 border-b border-slate-200 flex items-center gap-2 flex-wrap">
          <I.ScrollText size={14} className="text-indigo-700" />
          <span className="text-[13px] font-semibold text-slate-900">Сахих аль-Бухари · Книга откровения · хадис</span>
          <Badge tone="indigo" size="sm">№1</Badge>
          <span className="ml-auto text-[10px] font-mono text-slate-400">источник: sunnah.com</span>
        </div>
        <div className="flex-1 overflow-auto p-4 bg-slate-50/40">
          <div className="bg-white rounded-lg border border-emerald-300 ring-1 ring-emerald-200 p-4">
            <div className="flex items-center gap-2 mb-3">
              <Badge tone="indigo" size="sm">Бухари 1</Badge>
              <GradeBadge grade="SAHIH" size="sm" />
              <span className="ml-auto text-[10px] font-mono text-slate-500">USC-MSA · sunnah.com/bukhari:1</span>
            </div>
            <div dir="rtl" className="font-naskh text-[20px] arabic-text text-slate-900 leading-[2.1]">
              {AR.niyyah}
            </div>
            <div className="mt-3 pt-3 border-t border-slate-100 text-[12px] leading-relaxed text-slate-700">
              «Поистине, дела (оцениваются) только по намерениям, и каждому человеку — то, что он намеревался. Тот, чьё переселение было ради Аллаха и Его Посланника, — переселился к Аллаху и Его Посланнику. А тот, чьё переселение было ради мирского, что он стремится получить, или ради женщины, на которой хочет жениться, — переселение того к тому, к чему он переселился.»
            </div>
            <div className="mt-3">
              <div className="text-[10px] font-semibold uppercase tracking-wider text-slate-500 mb-1.5">Иснад · цепочка передатчиков</div>
              <div className="flex items-center gap-1.5 flex-wrap">
                {sanad.map((s, i) => (
                  <React.Fragment key={i}>
                    <span className="inline-flex items-center h-6 px-2 rounded-md bg-slate-100 text-[11px] font-medium text-slate-700 border border-slate-200">
                      {s}
                    </span>
                    {i < sanad.length - 1 && <I.ChevronRight size={11} className="text-slate-400" />}
                  </React.Fragment>
                ))}
              </div>
            </div>
            <div className="mt-3 grid grid-cols-3 gap-2 text-[11px]">
              <div className="rounded border border-slate-200 p-2">
                <div className="font-mono text-[9px] text-slate-500 uppercase">Параллели</div>
                <div className="font-semibold text-slate-800 mt-0.5">Муслим 1907 · Тирмизи 1647</div>
              </div>
              <div className="rounded border border-slate-200 p-2">
                <div className="font-mono text-[9px] text-slate-500 uppercase">Передатчиков</div>
                <div className="font-semibold text-slate-800 mt-0.5">6 в иснаде · все надёжные</div>
              </div>
              <div className="rounded border border-slate-200 p-2">
                <div className="font-mono text-[9px] text-slate-500 uppercase">Ат-Тахридж</div>
                <div className="font-semibold text-slate-800 mt-0.5">dorar.net</div>
              </div>
            </div>
          </div>
        </div>
        <div className="p-3 border-t border-slate-200 bg-white flex items-center justify-between">
          <div className="text-[11px] text-slate-600 inline-flex items-center gap-1.5">
            <I.Quote size={11} className="text-indigo-700" />
            Будет вставлено: <span className="font-mono font-semibold">Бухари 1</span>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="ghost" size="sm">Отмена</Button>
            <Button variant="primary" size="sm" icon="Link">Привязать хадис</Button>
          </div>
        </div>
      </div>
    </div>
  );
};

const SourcePickerBooks = () => {
  const books = [
    { ar: "مَجْمُوعُ الْفَتَاوَى", name: "Маджму' аль-Фатава", author: "Ибн Таймия", era: "VIII в.х.", vols: 37, active: true },
    { ar: "حُسْنُ الْمَقْصِدِ فِي عَمَلِ الْمَوْلِدِ", name: "Хусн уль-максид фи амаль аль-маулид", author: "ас-Суюти", era: "IX в.х.", vols: 1 },
    { ar: "الْبِدَايَةُ وَالنِّهَايَةُ", name: "Аль-Бидая ва-н-нихая", author: "Ибн Касир", era: "VIII в.х.", vols: 14 },
    { ar: "اقْتِضَاءُ الصِّرَاطِ الْمُسْتَقِيمِ", name: "Иктидаъ ас-сырат аль-мустаким", author: "Ибн Таймия", era: "VIII в.х.", vols: 2 },
    { ar: "الْمَدْخَلُ", name: "Аль-Мадхаль", author: "Ибн аль-Хадж", era: "VIII в.х.", vols: 4 },
  ];
  return (
    <div className="grid grid-cols-[320px_1fr] h-[460px]">
      <div className="border-r border-slate-200 flex flex-col">
        <div className="p-3 border-b border-slate-200 space-y-2">
          <Input icon="Search" placeholder="Название книги или автор…" />
          <div className="flex items-center gap-1 text-[11px]">
            <button className="px-2 h-6 rounded bg-slate-900 text-white">Все</button>
            <button className="px-2 h-6 rounded text-slate-600 hover:bg-slate-100">Фикх</button>
            <button className="px-2 h-6 rounded text-slate-600 hover:bg-slate-100">Хадис</button>
            <button className="px-2 h-6 rounded text-slate-600 hover:bg-slate-100">Тафсир</button>
          </div>
        </div>
        <div className="flex-1 overflow-auto">
          {books.map((b) => (
            <button key={b.name} className={cx(
              "w-full flex items-start gap-3 p-3 text-left border-b border-slate-100 hover:bg-slate-50",
              b.active && "bg-amber-50/60 border-l-[3px] border-l-amber-500"
            )}>
              <div className="h-14 w-10 rounded bg-gradient-to-br from-amber-200 to-amber-100 border border-amber-300 grid place-items-center shrink-0">
                <I.BookText size={18} className="text-amber-800" />
              </div>
              <div className="flex-1 min-w-0">
                <div dir="rtl" className="font-naskh text-[14px] font-bold text-slate-900 leading-tight line-clamp-1">{b.ar}</div>
                <div className="text-[12px] font-semibold text-slate-700 line-clamp-1">{b.name}</div>
                <div className="text-[11px] text-slate-500 mt-0.5">{b.author} · {b.era}</div>
                <div className="text-[10px] font-mono text-amber-700 mt-1">📚 {b.vols} том · shamela.ws</div>
              </div>
            </button>
          ))}
        </div>
      </div>
      <div className="flex flex-col">
        <div className="p-3 border-b border-slate-200 flex items-center gap-2 flex-wrap">
          <I.Library size={14} className="text-amber-700" />
          <span className="text-[13px] font-semibold text-slate-900">Маджму' аль-Фатава</span>
          <span dir="rtl" className="font-naskh text-[14px] text-slate-700">مَجْمُوعُ الْفَتَاوَى</span>
          <Badge tone="amber" size="sm">Ибн Таймия</Badge>
          <span className="ml-auto text-[10px] font-mono text-slate-400">источник: shamela.ws/book/22203</span>
        </div>
        <div className="flex-1 overflow-auto p-4 bg-slate-50/40">
          <div className="flex items-center gap-2 mb-3">
            <span className="text-[11px] font-semibold text-slate-700">Том</span>
            <select className="h-7 px-2 rounded border border-slate-300 bg-white text-[12px] font-mono">
              <option>27</option>
            </select>
            <span className="text-[11px] font-semibold text-slate-700 ml-2">Страница</span>
            <select className="h-7 px-2 rounded border border-slate-300 bg-white text-[12px] font-mono">
              <option>152</option>
            </select>
            <Button variant="ghost" size="sm" iconRight="ExternalLink">Открыть в Шамиле</Button>
          </div>
          <div className="bg-white rounded border border-slate-300 p-5 shadow-sm">
            <div className="text-center text-[10px] font-mono text-slate-500 mb-2 pb-2 border-b border-slate-200">— مَجْمُوعُ الْفَتَاوَى · ج‍ ٢٧ · ص ١٥٢ —</div>
            <div dir="rtl" className="font-naskh arabic-text text-[18px] text-slate-900 leading-[2.2]">
              وَأَمَّا اتِّخَاذُهُ مَوْسِمًا مِنْ بَعْضِ أَوْقَاتِ السَّنَةِ، نَظِيرَ مَوْلِدِ النَّبِيِّ ﷺ، فَهَذَا لَمْ يَفْعَلْهُ السَّلَفُ، مَعَ قِيَامِ الْمُقْتَضِي لَهُ وَعَدَمِ الْمَانِعِ مِنْهُ لَوْ كَانَ خَيْرًا. وَلَوْ كَانَ هَذَا خَيْرًا مَحْضًا أَوْ رَاجِحًا لَكَانَ السَّلَفُ ﵃ أَحَقَّ بِهِ مِنَّا...
            </div>
            <div className="mt-3 pt-3 border-t border-slate-100 text-[12px] leading-relaxed text-slate-700">
              «Что касается превращения [дня рождения Пророка ﷺ] в один из праздников года — этого не делали саляфы, хотя для этого был и повод, и отсутствие препятствий, если бы это было благом. Если бы это было чистым благом или преобладающим — саляфы (да будет доволен ими Аллах) имели бы больше прав на это, чем мы…»
            </div>
          </div>
        </div>
        <div className="p-3 border-t border-slate-200 bg-white flex items-center justify-between">
          <div className="text-[11px] text-slate-600 inline-flex items-center gap-1.5">
            <I.Quote size={11} className="text-amber-700" />
            Будет вставлено: <span className="font-mono font-semibold">Маджму' аль-Фатава, т. 27, стр. 152</span>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="ghost" size="sm">Отмена</Button>
            <Button variant="primary" size="sm" icon="Link">Привязать выдержку</Button>
          </div>
        </div>
      </div>
    </div>
  );
};

const SourcePickerModal = ({ activeTab = "quran" }) => (
  <div className="w-[920px] rounded-lg bg-white shadow-2xl border border-slate-200 overflow-hidden">
    <div className="px-5 py-3 border-b border-slate-200 flex items-center justify-between">
      <div>
        <h3 className="text-[15px] font-semibold text-slate-900">Привязать источник</h3>
        <p className="text-[11px] text-slate-500 mt-0.5">Источник станет EVIDENCE-узлом и будет связан с выбранным узлом темы.</p>
      </div>
      <IconButton icon="X" label="Закрыть" size="sm" />
    </div>
    <div className="px-5 pt-3 border-b border-slate-200 bg-slate-50/40">
      <div className="flex items-center gap-1">
        {SOURCE_TABS.map((t) => {
          const Icon = I[t.icon];
          const active = t.key === activeTab;
          return (
            <button key={t.key} className={cx(
              "inline-flex items-center gap-1.5 px-3 h-9 text-[12px] font-medium border-b-2 transition-colors",
              active ? "border-indigo-600 text-indigo-700" : "border-transparent text-slate-600 hover:text-slate-900"
            )}>
              <Icon size={14} />
              {t.label}
              <span className="font-mono text-[10px] text-slate-400 ml-1">{t.count}</span>
            </button>
          );
        })}
      </div>
    </div>
    {activeTab === "quran" && <SourcePickerQuran />}
    {activeTab === "hadith" && <SourcePickerHadith />}
    {activeTab === "books" && <SourcePickerBooks />}
  </div>
);

// === Inline citations =======================================================

const InlineCitations = () => (
  <SubSection title="Inline citations · цитаты внутри текста узла">
    <div className="grid grid-cols-2 gap-5">
      <Card className="p-5">
        <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-2">Режим редактирования</div>
        <div className="rounded-md border border-slate-300 bg-white">
          <div className="px-2 py-1.5 border-b border-slate-200 flex items-center gap-1 bg-slate-50">
            <IconButton icon="BookOpen" label="Аят" size="sm" />
            <IconButton icon="ScrollText" label="Хадис" size="sm" />
            <IconButton icon="Library" label="Книга" size="sm" />
            <IconButton icon="Quote" label="Свободная цитата" size="sm" />
            <div className="w-px h-5 bg-slate-200 mx-0.5" />
            <Button variant="ghost" size="xs" icon="Plus">Вставить ссылку</Button>
            <span className="ml-auto font-mono text-[10px] text-slate-400">cursor at line 2:34</span>
          </div>
          <div className="p-3 text-[13px] leading-relaxed text-slate-800 min-h-[120px]">
            Намерение определяет религиозную ценность поступка. Это следует из хадиса о намерениях <span className="inline-flex items-center gap-0.5 px-1 rounded bg-indigo-100 text-indigo-700 text-[10px] font-mono font-bold align-super">[1]</span>, а также из общего правила фикха об оценке деяний. Аль-Бухари ставит этот хадис первым в своём сборнике <span className="inline-flex items-center gap-0.5 px-1 rounded bg-indigo-100 text-indigo-700 text-[10px] font-mono font-bold align-super">[2]</span>, а также<span className="inline-block w-px h-4 bg-indigo-600 ml-0.5 align-text-bottom animate-pulse" />
          </div>
        </div>
        <div className="mt-2 text-[11px] text-slate-500">Кнопка «Вставить ссылку» открывает Source picker. Маркер вставляется в позиции курсора.</div>
      </Card>
      <Card className="p-5">
        <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-2">Режим просмотра</div>
        <div className="text-[13px] leading-relaxed text-slate-800">
          Намерение определяет религиозную ценность поступка. Это следует из хадиса о намерениях <a className="font-mono text-[10px] font-bold text-indigo-600 hover:text-indigo-800 align-super">[1]</a>, а также из общего правила фикха об оценке деяний. Аль-Бухари ставит этот хадис первым в своём сборнике <a className="font-mono text-[10px] font-bold text-indigo-600 hover:text-indigo-800 align-super">[2]</a>.
        </div>
        <div className="mt-3 text-[10px] font-semibold uppercase tracking-wider text-slate-500">Hover-popover на маркере [1]</div>
        <div className="mt-2 rounded-lg border border-slate-200 bg-white shadow-xl p-3 max-w-[340px]">
          <div className="flex items-center gap-2 mb-2">
            <I.ScrollText size={13} className="text-indigo-700" />
            <span className="text-[11px] font-semibold text-slate-700">Хадис</span>
            <GradeBadge grade="SAHIH" size="sm" />
            <span className="ml-auto font-mono text-[10px] text-slate-400">[1]</span>
          </div>
          <div dir="rtl" className="font-naskh text-[16px] text-slate-900 leading-[1.9] line-clamp-2">{AR.niyyahShort}</div>
          <div className="mt-1.5 text-[12px] text-slate-700 line-clamp-2">«Поистине, дела — только по намерениям…»</div>
          <div className="mt-2 pt-2 border-t border-slate-100 flex items-center justify-between">
            <span className="font-mono text-[10px] text-slate-500">Бухари 1 · Муслим 1907</span>
            <Button variant="link" size="xs" iconRight="ExternalLink">Открыть</Button>
          </div>
        </div>
      </Card>
    </div>
  </SubSection>
);

// === Source detail panel (800px) ============================================

const SourceDetailPanel = () => (
  <Card className="overflow-hidden flex flex-col" style={{ width: 800, height: 720 }}>
    <div className="bg-gradient-to-b from-emerald-50/60 to-white p-5 border-b border-slate-200 relative">
      <div className="absolute top-3 right-3"><IconButton icon="X" label="Закрыть" size="sm" /></div>
      <div className="flex items-center gap-2 mb-2">
        <div className="h-8 w-8 rounded-md bg-emerald-100 text-emerald-700 grid place-items-center"><I.ScrollText size={16} /></div>
        <div>
          <div className="text-[10px] font-mono font-semibold text-slate-500 uppercase tracking-wider">Hadith · sunnah.com</div>
          <div className="text-[14px] font-semibold text-slate-900">Сахих аль-Бухари · хадис №1</div>
        </div>
        <div className="ml-auto flex items-center gap-1.5">
          <GradeBadge grade="SAHIH" size="md" />
          <Badge tone="indigo" size="md">Бухари</Badge>
          <Badge tone="slate" size="md">Книга откровения</Badge>
        </div>
      </div>
    </div>

    <div className="grid grid-cols-2 flex-1 min-h-0">
      <div className="border-r border-slate-200 p-5 overflow-auto bg-emerald-50/20">
        <div className="text-[10px] font-mono uppercase tracking-wider text-emerald-700 mb-3 flex items-center gap-1.5">
          <span className="font-naskh text-[14px] not-italic">العَرَبِيَّة</span> · оригинал
        </div>
        <div dir="rtl" className="font-naskh arabic-text text-[20px] text-slate-900 leading-[2.2]">
          {AR.niyyah} ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى. فَمَنْ كَانَتْ هِجْرَتُهُ إِلَى اللَّهِ وَرَسُولِهِ فَهِجْرَتُهُ إِلَى اللَّهِ وَرَسُولِهِ، وَمَنْ كَانَتْ هِجْرَتُهُ لِدُنْيَا يُصِيبُهَا أَوِ امْرَأَةٍ يَنْكِحُهَا فَهِجْرَتُهُ إِلَى مَا هَاجَرَ إِلَيْهِ.
        </div>
        <div className="mt-4 pt-3 border-t border-emerald-200/60">
          <div className="text-[10px] font-mono uppercase tracking-wider text-slate-600 mb-1.5">Тажвид · фразы для запоминания</div>
          <div dir="rtl" className="font-amiri text-[16px] text-emerald-900 leading-[2]">إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ</div>
        </div>
      </div>
      <div className="p-5 overflow-auto">
        <div className="text-[10px] font-mono uppercase tracking-wider text-slate-600 mb-3">Перевод · USC-MSA</div>
        <div className="text-[14px] leading-relaxed text-slate-800 text-pretty">
          «Поистине, дела (оцениваются) только по намерениям, и каждому человеку — то, что он намеревался. Тот, чьё переселение было ради Аллаха и Его Посланника, — переселился к Аллаху и Его Посланнику. А тот, чьё переселение было ради мирского, что он стремится получить, или ради женщины, на которой хочет жениться, — переселение того к тому, к чему он переселился.»
        </div>
        <div className="mt-4 pt-3 border-t border-slate-100">
          <div className="text-[10px] font-mono uppercase tracking-wider text-slate-600 mb-1.5">Параллельный перевод</div>
          <div className="text-[12px] leading-relaxed italic text-slate-600">«Verily, actions are by intentions, and every person shall have what they intended…»</div>
        </div>
      </div>
    </div>

    {/* Sanad */}
    <div className="px-5 py-3 border-t border-slate-200 bg-slate-50/60">
      <div className="text-[10px] font-mono uppercase tracking-wider text-slate-600 mb-2 flex items-center gap-1.5">
        <I.Users size={11} /> Иснад · цепочка передатчиков
      </div>
      <div className="flex items-center gap-1 flex-wrap text-[11px]">
        {["аль-Хумайди", "Суфьян", "Яхья ибн Саид", "Мухаммад", "Алькама", "Умар ↬"].map((s, i, arr) => (
          <React.Fragment key={i}>
            <span className="inline-flex items-center h-6 px-2 rounded-md bg-white border border-slate-300 font-medium text-slate-700">{s}</span>
            {i < arr.length - 1 && <I.ChevronRight size={11} className="text-slate-400" />}
          </React.Fragment>
        ))}
      </div>
    </div>

    <div className="px-5 py-3 border-t border-slate-200 bg-white flex items-center justify-between">
      <div className="text-[12px] text-slate-700">
        <span className="text-slate-500">Использовано в этой теме:</span> <span className="font-mono font-semibold">3 узла</span>
        <span className="ml-3 text-slate-400">·</span>
        <span className="ml-3 text-slate-500">CLAIM «Намерение определяет деяние»</span>
      </div>
      <div className="flex items-center gap-2">
        <Button variant="ghost" size="sm" iconRight="ExternalLink">sunnah.com</Button>
        <Button variant="secondary" size="sm" icon="Copy">Скопировать цитату</Button>
        <Button variant="primary" size="sm" icon="Link">Привязать к узлу</Button>
      </div>
    </div>
  </Card>
);

// === Library overview =======================================================

const LIBRARY_ITEMS = [
  { kind: "ayah", grade: null, ar: "ٱللَّهُ لَا إِلَـٰهَ إِلَّا هُوَ ٱلْحَيُّ ٱلْقَيُّومُ", tr: "Аллах — нет божества кроме Него…", cit: "Коран 2:255", src: "quran.com", uses: 8 },
  { kind: "hadith", grade: "SAHIH", ar: AR.niyyahShort, tr: "Поистине, дела — по намерениям…", cit: "Бухари 1 · Муслим 1907", src: "sunnah.com", uses: 14 },
  { kind: "hadith", grade: "SAHIH", ar: AR.monday, tr: "Это день, в который я родился…", cit: "Сахих Муслим 1162", src: "sunnah.com", uses: 5 },
  { kind: "book", grade: null, ar: "وَأَمَّا اتِّخَاذُهُ مَوْسِمًا...", tr: "«Что касается превращения в праздник — этого не делали саляфы…»", cit: "Маджму' аль-Фатава · т.27 · с.152", src: "shamela.ws", uses: 3 },
  { kind: "book", grade: null, ar: "فَإِنَّ أَصْلَ عَمَلِ الْمَوْلِدِ...", tr: "«Основание мавлида — благое нововведение, за которое его автор будет вознаграждён…»", cit: "Хусн уль-максид · т.1 · с.22", src: "shamela.ws", uses: 2 },
  { kind: "hadith", grade: "DAIF", ar: "كُلُّ بِدْعَةٍ ضَلَالَةٌ", tr: "«Каждое новшество — заблуждение». Контекст спорный.", cit: "Сунан Абу Дауд 4607", src: "sunnah.com", uses: 7 },
  { kind: "ayah", grade: null, ar: "لَا إِكْرَاهَ فِي الدِّينِ", tr: "Нет принуждения в религии.", cit: "Коран 2:256", src: "quran.com", uses: 4 },
  { kind: "free", grade: null, ar: "—", tr: "Резолюция AAOIFI Sharia Standard No. 21 — параграф о намерении в финансовых контрактах.", cit: "AAOIFI · 2023", src: "ручной", uses: 2 },
];

const LibraryItemCard = ({ item }) => {
  const kindMeta = {
    ayah:   { icon: "BookOpen",   tone: "emerald", label: "Аят" },
    hadith: { icon: "ScrollText", tone: "indigo", label: "Хадис" },
    book:   { icon: "Library",    tone: "amber", label: "Книга" },
    free:   { icon: "Quote",      tone: "slate", label: "Свободная" },
  };
  const m = kindMeta[item.kind];
  return (
    <Card className="p-4 hover:shadow-md hover:-translate-y-0.5 transition-all">
      <div className="flex items-center gap-2 mb-2">
        <Badge tone={m.tone} size="sm" icon={m.icon}>{m.label}</Badge>
        {item.grade && <GradeBadge grade={item.grade} size="sm" />}
        <span className="ml-auto inline-flex items-center gap-1 text-[10px] font-mono text-slate-500">
          <I.Network size={10} />{item.uses}
        </span>
      </div>
      {item.ar !== "—" && (
        <div dir="rtl" className="font-naskh arabic-text text-[16px] text-slate-900 leading-[1.95] line-clamp-2 mb-2">{item.ar}</div>
      )}
      <div className="text-[12px] leading-relaxed text-slate-600 line-clamp-2">{item.tr}</div>
      <div className="mt-2 pt-2 border-t border-slate-100 flex items-center justify-between">
        <span className="font-mono text-[10px] font-semibold text-slate-700 truncate">{item.cit}</span>
        <span className="font-mono text-[10px] text-slate-400 inline-flex items-center gap-0.5"><I.ExternalLink size={9} />{item.src}</span>
      </div>
    </Card>
  );
};

const LibraryOverview = () => (
  <div className="rounded-lg border border-slate-200 bg-white overflow-hidden">
    <div className="h-12 px-5 border-b border-slate-200 flex items-center gap-3 bg-white">
      <Button variant="ghost" size="sm" icon="ArrowLeft">Темы</Button>
      <span className="text-slate-300">/</span>
      <I.Library size={15} className="text-slate-500" />
      <span className="text-[13px] font-semibold text-slate-900">Моя библиотека</span>
      <span className="font-mono text-[11px] text-slate-500 ml-1">· 247 источников</span>
      <div className="ml-auto flex items-center gap-2">
        <LanguageSwitcher current="RU" />
        <Button variant="secondary" size="sm" icon="ExternalLink">Импорт из Шамилы</Button>
        <Button variant="primary" size="sm" icon="Plus">Добавить источник</Button>
      </div>
    </div>

    <div className="px-8 py-6">
      <div className="flex items-center gap-1 mb-4 border-b border-slate-200 -mx-2 px-2">
        {[
          { k: "all",    label: "Все",        n: 247, active: true },
          { k: "ayah",   label: "Аяты",       n: 64,  icon: "BookOpen" },
          { k: "hadith", label: "Хадисы",     n: 122, icon: "ScrollText" },
          { k: "book",   label: "Книги",      n: 38,  icon: "Library" },
          { k: "auth",   label: "Авторитеты", n: 23,  icon: "Users" },
        ].map((t) => {
          const Icon = t.icon ? I[t.icon] : null;
          return (
            <button key={t.k} className={cx(
              "inline-flex items-center gap-1.5 px-3 h-9 text-[12px] font-medium border-b-2 transition-colors -mb-px",
              t.active ? "border-indigo-600 text-indigo-700" : "border-transparent text-slate-600 hover:text-slate-900"
            )}>
              {Icon && <Icon size={13} />}
              {t.label}
              <span className="font-mono text-[10px] text-slate-400 ml-1">{t.n}</span>
            </button>
          );
        })}
      </div>
      <div className="flex items-center gap-3 mb-5">
        <Input icon="Search" placeholder="Поиск по арабскому, переводу, цитате…" className="flex-1 max-w-md" />
        <Button variant="secondary" size="sm" icon="Filter">Grade: все</Button>
        <Button variant="secondary" size="sm" iconRight="ChevronDown">Эпоха</Button>
        <Button variant="secondary" size="sm" iconRight="ChevronDown">Мазхаб</Button>
        <span className="ml-auto text-[11px] text-slate-500 font-mono">8 из 247 показано</span>
      </div>
      <div className="grid grid-cols-4 gap-4">
        {LIBRARY_ITEMS.map((it, i) => <LibraryItemCard key={i} item={it} />)}
      </div>
    </div>
  </div>
);

const SourceLibrarySection = () => (
  <Section id="sources" title="Source Library · работа с первоисточниками" kicker="13 — sources" hint="Цитата без том/страница — не цитата. Хадис без иснада — не хадис. Целая секция продукта.">
    <SubSection title="Source picker · модалка привязки источника">
      <div className="space-y-6">
        <div>
          <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-2">Tab: Коран · поиск по сурам и аятам</div>
          <div className="checkerboard rounded-lg p-6 flex justify-center"><SourcePickerModal activeTab="quran" /></div>
        </div>
        <div>
          <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-2">Tab: Хадисы · 9 сборников · sunnah.com</div>
          <div className="checkerboard rounded-lg p-6 flex justify-center"><SourcePickerModal activeTab="hadith" /></div>
        </div>
        <div>
          <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-2">Tab: Книги · аль-Мактаба аш-Шамиля · shamela.ws</div>
          <div className="checkerboard rounded-lg p-6 flex justify-center"><SourcePickerModal activeTab="books" /></div>
        </div>
      </div>
    </SubSection>

    <InlineCitations />

    <SubSection title="Source detail panel · 800px параллельный просмотр">
      <div className="checkerboard rounded-lg p-6 flex justify-center"><SourceDetailPanel /></div>
    </SubSection>

    <SubSection title="Library overview · /library — личная коллекция">
      <LibraryOverview />
    </SubSection>
  </Section>
);

// === RTL graph mockup =======================================================

const RTLGraphScreen = () => (
  <div dir="rtl" className="rounded-lg border border-slate-200 bg-white overflow-hidden font-arabic-sans">
    <div className="h-12 px-4 border-b border-slate-200 flex items-center justify-between bg-white">
      <div className="flex items-center gap-2 min-w-0">
        <button className="inline-flex items-center gap-1.5 h-8 px-2.5 rounded-md text-slate-700 hover:bg-slate-100 text-[13px]">
          <I.ArrowRight size={14} />
          الْمَوَاضِيعُ
        </button>
        <span className="text-slate-300">/</span>
        <I.BookOpen size={15} className="text-slate-500" />
        <span className="text-[13px] font-semibold text-slate-900 truncate">حُكْمُ الِاحْتِفَالِ بِالْمَوْلِدِ النَّبَوِيِّ</span>
        <Badge tone="slate" size="sm">v3</Badge>
        <Badge tone="emerald" size="sm" icon="Check">مَحْفُوظٌ</Badge>
      </div>
      <div className="flex items-center gap-2">
        <LanguageSwitcher current="AR" />
        <Button variant="secondary" size="sm" icon="History">المُرَاجَعَاتُ</Button>
        <Button variant="primary" size="sm" icon="Sparkles">مُشَارَكَةٌ</Button>
        <Avatar name="Анас И." color="indigo" size="sm" />
      </div>
    </div>

    <div className="flex" style={{ height: 600 }}>
      {/* Right toolbar (was left in LTR) */}
      <div className="flex-1 relative overflow-hidden">
        <Graph height={600} />
        {/* Legend — bottom-right in RTL becomes bottom-left visually but we keep semantic */}
        <div className="absolute right-4 bottom-4 bg-white/95 backdrop-blur border border-slate-200 rounded-md shadow-md p-3 max-w-[280px]" dir="rtl">
          <div className="text-[10px] font-semibold uppercase tracking-wider text-slate-500 mb-2">المِفْتَاحُ</div>
          <div className="grid grid-cols-2 gap-x-3 gap-y-1.5 font-arabic-sans">
            <div className="flex items-center gap-1.5 text-[12px] text-slate-700"><span className="h-2.5 w-3 rounded-sm bg-emerald-500" />ثَابِتٌ</div>
            <div className="flex items-center gap-1.5 text-[12px] text-slate-700"><span className="h-2.5 w-3 rounded-sm bg-amber-500" />مُتَنَازَعٌ</div>
            <div className="flex items-center gap-1.5 text-[12px] text-slate-700"><span className="h-2.5 w-3 rounded-sm bg-red-500" />مَدْحُوضٌ</div>
            <div className="flex items-center gap-1.5 text-[12px] text-slate-700"><span className="h-2.5 w-3 rounded-sm bg-slate-400" />غَيْرُ مُقَيَّمٍ</div>
          </div>
        </div>
        <div className="absolute bottom-4 left-1/2 translate-x-1/2 bg-white/95 backdrop-blur border border-slate-200 rounded-md shadow-md flex items-center gap-0.5 p-1" dir="ltr">
          <IconButton icon="ZoomOut" label="−" size="sm" />
          <span className="px-2 text-[11px] font-mono font-semibold text-slate-700 tabular-nums w-12 text-center">86 %</span>
          <IconButton icon="ZoomIn" label="+" size="sm" />
        </div>
        <div className="absolute top-4 left-4 bg-white/95 backdrop-blur border border-slate-200 rounded-md shadow-sm px-3 py-2 flex items-center gap-3 text-[11px] text-slate-600 font-arabic-sans" dir="rtl">
          <span className="inline-flex items-center gap-1"><Kbd>N</Kbd>عُقْدَةٌ</span>
          <span className="inline-flex items-center gap-1"><Kbd>E</Kbd>صِلَةٌ</span>
          <span className="inline-flex items-center gap-1"><Kbd>Del</Kbd>حَذْفٌ</span>
        </div>
      </div>
      <div className="w-14 border-l border-slate-200 bg-white flex flex-col items-center py-3 gap-1">
        <RTLToolbarBtn icon="Plus" label="إِضَافَةُ عُقْدَةٍ" />
        <RTLToolbarBtn icon="Link" label="إِنْشَاءُ صِلَةٍ" />
        <RTLToolbarBtn icon="Move" label="تَحْرِيكٌ" active />
        <div className="my-2 h-px w-8 bg-slate-200" />
        <RTLToolbarBtn icon="Eye" label="التَّسْمِيَاتُ" active />
        <RTLToolbarBtn icon="Hash" label="الشَّبَكَةُ" active />
        <div className="my-2 h-px w-8 bg-slate-200" />
        <RTLToolbarBtn icon="Trash" label="حَذْفٌ" danger />
      </div>
    </div>
  </div>
);

const RTLToolbarBtn = ({ icon, label, active, danger }) => {
  const Icon = I[icon];
  return (
    <Tooltip label={label} side="left">
      <button className={cx(
        "h-10 w-10 rounded-md flex items-center justify-center transition-colors border",
        active ? "bg-indigo-50 text-indigo-700 border-indigo-200" :
        danger ? "text-red-600 hover:bg-red-50 border-transparent" :
        "text-slate-600 hover:bg-slate-100 hover:text-slate-900 border-transparent",
      )}>
        <Icon size={18} />
      </button>
    </Tooltip>
  );
};

const RTLSection = () => (
  <Section id="rtl" title="Граф · RTL demo · полностью зеркальный режим" kicker="15 — i18n / RTL" hint="Весь UI зеркалится. Иконки направления тоже. Стрелки рёбер сохраняют логическое направление.">
    <RTLGraphScreen />
    <div className="mt-5 grid grid-cols-3 gap-4">
      <Card className="p-4">
        <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-2">Что зеркалится</div>
        <ul className="text-[12px] text-slate-700 space-y-1.5 leading-relaxed">
          <li>• Top-bar, side-bar, side-panels, модалки, меню</li>
          <li>• Иконки <code className="text-[11px] bg-slate-100 px-1 rounded">ArrowLeft</code>, <code className="text-[11px] bg-slate-100 px-1 rounded">ChevronRight</code> — flip</li>
          <li>• Текст — выравнивание `text-right`, `dir="rtl"`</li>
          <li>• Хлебные крошки — обратный порядок</li>
        </ul>
      </Card>
      <Card className="p-4">
        <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-2">Что НЕ зеркалится</div>
        <ul className="text-[12px] text-slate-700 space-y-1.5 leading-relaxed">
          <li>• Стрелки на графе — содержат семантику from→to</li>
          <li>• Иконки-смыслы (<code className="text-[11px] bg-slate-100 px-1 rounded">Plus</code>, <code className="text-[11px] bg-slate-100 px-1 rounded">Trash</code>)</li>
          <li>• Координаты узлов на холсте</li>
          <li>• Минимапа · ZoomIn/Out — оставлены LTR</li>
        </ul>
      </Card>
      <Card className="p-4">
        <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-2">Mixed-direction safety</div>
        <p className="text-[12px] text-slate-700 leading-relaxed">
          Поля с арабским контентом получают <code className="text-[11px] bg-slate-100 px-1 rounded">dir="rtl"</code> автоматически. Карточка узла в LTR-теме рендерит арабскую цитату RTL внутри себя — без переключения родительского контейнера.
        </p>
      </Card>
    </div>
  </Section>
);

// === Settings ===============================================================

const SettingsScreen = () => (
  <div className="rounded-lg border border-slate-200 bg-white overflow-hidden">
    <div className="h-12 px-5 border-b border-slate-200 flex items-center gap-3">
      <Button variant="ghost" size="sm" icon="ArrowLeft">Назад</Button>
      <span className="text-slate-300">/</span>
      <I.Settings size={15} className="text-slate-500" />
      <span className="text-[13px] font-semibold text-slate-900">Настройки</span>
    </div>
    <div className="grid grid-cols-[220px_1fr]" style={{ minHeight: 720 }}>
      <div className="border-r border-slate-200 bg-slate-50/40 py-4">
        {[
          { label: "Профиль", icon: "User" },
          { label: "Язык и регион", icon: "Languages", active: true },
          { label: "Арабский текст", icon: "BookText", active: false, sub: true },
          { label: "Источники", icon: "Library" },
          { label: "Горячие клавиши", icon: "Command" },
          { label: "Уведомления", icon: "AlertCircle" },
          { label: "API ключи", icon: "Hash" },
        ].map((it) => {
          const Icon = I[it.icon];
          return (
            <button key={it.label} className={cx(
              "w-full flex items-center gap-2.5 px-5 py-2 text-[13px] text-left transition-colors",
              it.active ? "bg-white border-r-2 border-indigo-600 text-indigo-700 font-medium -mr-px" : "text-slate-700 hover:bg-white",
              it.sub && "pl-12 text-[12px]"
            )}>
              {!it.sub && <Icon size={14} className="text-slate-500" />}
              {it.label}
            </button>
          );
        })}
      </div>
      <div className="p-8 max-w-[720px]">
        <div className="mb-8">
          <h2 className="text-[20px] font-bold text-slate-900">Язык и арабский текст</h2>
          <p className="text-[13px] text-slate-500 mt-1">Эти настройки применяются глобально и сохраняются в профиле.</p>
        </div>

        <div className="space-y-6">
          {/* Language */}
          <div>
            <label className="text-[13px] font-semibold text-slate-800">Язык интерфейса</label>
            <p className="text-[12px] text-slate-500 mt-0.5 mb-3">При выборе арабского — UI становится RTL.</p>
            <div className="grid grid-cols-3 gap-2 max-w-md">
              {LANGS.map((l) => (
                <label key={l.code} className={cx(
                  "rounded-md border p-3 cursor-pointer transition-colors",
                  l.code === "RU" ? "border-indigo-500 bg-indigo-50/60 ring-1 ring-indigo-400" : "border-slate-300 hover:bg-slate-50"
                )}>
                  <div className="flex items-center justify-between mb-1.5">
                    <span className="font-mono text-[10px] font-bold text-slate-500">{l.code}</span>
                    <input type="radio" name="lang" defaultChecked={l.code === "RU"} className="accent-indigo-600" />
                  </div>
                  <div className={cx("text-[14px] font-medium text-slate-900", l.rtl && "font-arabic-sans text-right")} dir={l.rtl ? "rtl" : "ltr"}>{l.native}</div>
                </label>
              ))}
            </div>
          </div>

          {/* Arabic font */}
          <div>
            <label className="text-[13px] font-semibold text-slate-800">Шрифт для арабского текста</label>
            <p className="text-[12px] text-slate-500 mt-0.5 mb-3">Выбранный шрифт применяется в карточках узлов и Source-панели.</p>
            <div className="grid grid-cols-2 gap-3">
              {[
                { font: "amiri", name: "Amiri", desc: "Классический насх с огласовками. Рекомендован для Корана.", active: true },
                { font: "naskh", name: "Noto Naskh Arabic", desc: "Современный насх. Отлично читается на экранах." },
                { font: "scheherazade", name: "Scheherazade New", desc: "Большие огласовки. Для начинающих читателей." },
                { font: "kufi", name: "Reem Kufi", desc: "Геометричный куфи. Только для display, не для текста." },
              ].map((f) => (
                <label key={f.font} className={cx(
                  "rounded-md border p-3 cursor-pointer transition-colors",
                  f.active ? "border-indigo-500 bg-indigo-50/60 ring-1 ring-indigo-400" : "border-slate-300 hover:bg-slate-50"
                )}>
                  <div className="flex items-center justify-between mb-1.5">
                    <span className="text-[12px] font-semibold text-slate-900">{f.name}</span>
                    <input type="radio" name="ar-font" defaultChecked={f.active} className="accent-indigo-600" />
                  </div>
                  <div dir="rtl" className={cx("text-[20px] text-slate-900 leading-[1.9] mb-1", "font-" + f.font)}>{AR.bismillah}</div>
                  <div className="text-[11px] text-slate-500 leading-relaxed">{f.desc}</div>
                </label>
              ))}
            </div>
          </div>

          {/* Size slider */}
          <div>
            <div className="flex items-baseline justify-between">
              <label className="text-[13px] font-semibold text-slate-800">Размер арабского текста</label>
              <span className="font-mono text-[12px] font-semibold text-indigo-700">115%</span>
            </div>
            <input type="range" min="100" max="150" defaultValue="115" className="w-full max-w-md mt-2 accent-indigo-600" />
            <div className="flex justify-between max-w-md mt-1 text-[10px] font-mono text-slate-500">
              <span>100%</span><span>125%</span><span>150%</span>
            </div>
            <div className="mt-3 max-w-md p-3 rounded-md border border-slate-200 bg-slate-50">
              <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-1.5">Превью</div>
              <div dir="rtl" className="font-naskh text-slate-900" style={{ fontSize: "23px", lineHeight: 1.95 }}>{AR.niyyahShort}</div>
            </div>
          </div>

          {/* Toggles */}
          <div className="space-y-3">
            {[
              { label: "Показывать огласовки (харакат)", desc: "Выключите для опытных читателей. Текст останется без диакритики.", on: true },
              { label: "Транслитерация имён авторитетов", desc: "Показывать «Ibn Taymiyyah» рядом с «ابن تيمية» и «Ибн Таймия».", on: true },
              { label: "Параллельный английский перевод", desc: "Для двуязычных команд. Третий столбец в Source-панели.", on: false },
              { label: "Авто-RTL для арабских полей в RU/EN UI", desc: "Поле автоматически становится RTL при вводе арабских символов.", on: true },
            ].map((t) => (
              <div key={t.label} className="flex items-start gap-4 py-3 border-t border-slate-100">
                <div className="flex-1">
                  <div className="text-[13px] font-medium text-slate-800">{t.label}</div>
                  <div className="text-[11px] text-slate-500 mt-0.5">{t.desc}</div>
                </div>
                <button className={cx(
                  "relative h-5 w-9 rounded-full transition-colors shrink-0",
                  t.on ? "bg-indigo-600" : "bg-slate-300"
                )}>
                  <span className={cx(
                    "absolute top-0.5 h-4 w-4 rounded-full bg-white shadow transition-transform",
                    t.on ? "translate-x-4" : "translate-x-0.5"
                  )} />
                </button>
              </div>
            ))}
          </div>

          {/* Default sources priority */}
          <div>
            <label className="text-[13px] font-semibold text-slate-800">Приоритет источников по умолчанию</label>
            <p className="text-[12px] text-slate-500 mt-0.5 mb-3">При неоднозначности — какой источник выбирать первым. Drag для изменения порядка.</p>
            <div className="space-y-1.5 max-w-md">
              {[
                { src: "quran.com", label: "Коран · quran.com", icon: "BookOpen" },
                { src: "sunnah.com", label: "Хадисы · sunnah.com", icon: "ScrollText" },
                { src: "shamela.ws", label: "Книги · shamela.ws", icon: "Library" },
                { src: "dorar.net", label: "Энциклопедии хадисов · dorar.net", icon: "BookText" },
              ].map((s, i) => {
                const Icon = I[s.icon];
                return (
                  <div key={s.src} className="flex items-center gap-2 px-3 py-2 rounded-md border border-slate-200 bg-white">
                    <I.GripVertical size={14} className="text-slate-400" />
                    <span className="font-mono text-[10px] font-bold text-slate-400 w-4">{i + 1}.</span>
                    <Icon size={14} className="text-slate-500" />
                    <span className="text-[12px] font-medium text-slate-800 flex-1">{s.label}</span>
                    <I.ExternalLink size={11} className="text-slate-400" />
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
);

const SettingsSection = () => (
  <Section id="settings" title="Настройки · язык и арабский текст" kicker="16 — preferences" hint="Шрифт, размер, огласовки, транслитерация, приоритет источников.">
    <SettingsScreen />
  </Section>
);

// === New toasts =============================================================

const NewToastsSection = () => (
  <Section id="islamic-toasts" title="Уведомления · Islamic context" kicker="17 — toasts+" hint="Контекстные тосты для работы с источниками и хадисами.">
    <div className="grid grid-cols-2 gap-4 max-w-3xl">
      <div className="flex items-start gap-3 rounded-md border border-emerald-200 bg-white shadow-md w-full p-3">
        <div className="h-12 w-9 rounded bg-gradient-to-br from-amber-200 to-amber-100 border border-amber-300 grid place-items-center shrink-0">
          <I.BookText size={16} className="text-amber-800" />
        </div>
        <div className="flex-1 min-w-0">
          <div className="text-[13px] font-semibold text-emerald-900">Источник из Шамилы импортирован</div>
          <div className="text-[12px] text-slate-600 leading-relaxed mt-0.5">«Маджму' аль-Фатава», т. 27, стр. 152 — добавлен в библиотеку и привязан к узлу.</div>
          <Button variant="link" size="xs" className="mt-1.5 -ml-1">Открыть в библиотеке</Button>
        </div>
        <button className="text-slate-400 hover:text-slate-700 -mt-0.5"><I.X size={14} /></button>
      </div>

      <div className="flex items-start gap-3 rounded-md border border-sky-200 bg-white shadow-md w-full p-3">
        <div className="h-7 w-7 rounded-md bg-sky-50 text-sky-600 grid place-items-center shrink-0"><I.Network size={16} /></div>
        <div className="flex-1 min-w-0">
          <div className="text-[13px] font-semibold text-sky-900">Хадис уже используется в графе</div>
          <div className="text-[12px] text-slate-600 leading-relaxed mt-0.5">Найдено <span className="font-mono font-semibold">3 узла</span>, цитирующих этот хадис в текущей теме.</div>
          <Button variant="link" size="xs" className="mt-1.5 -ml-1">Показать узлы</Button>
        </div>
        <button className="text-slate-400 hover:text-slate-700 -mt-0.5"><I.X size={14} /></button>
      </div>

      <div className="flex items-start gap-3 rounded-md border border-amber-200 bg-white shadow-md w-full p-3">
        <div className="h-7 w-7 rounded-md bg-amber-50 text-amber-700 grid place-items-center shrink-0"><I.AlertTriangle size={16} /></div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5">
            <div className="text-[13px] font-semibold text-amber-900">Хадис имеет статус DAIF</div>
            <GradeBadge grade="DAIF" size="sm" />
          </div>
          <div className="text-[12px] text-slate-600 leading-relaxed mt-0.5">Этот хадис классифицируется учёными как слабый. Будьте осторожны при использовании в качестве доказательства.</div>
          <Button variant="link" size="xs" className="mt-1.5 -ml-1">Обоснование оценки →</Button>
        </div>
        <button className="text-slate-400 hover:text-slate-700 -mt-0.5"><I.X size={14} /></button>
      </div>

      <div className="flex items-start gap-3 rounded-md border border-emerald-200 bg-white shadow-md w-full p-3">
        <div className="h-7 w-7 rounded-md bg-emerald-50 text-emerald-700 grid place-items-center shrink-0"><I.CheckCircle size={16} /></div>
        <div className="flex-1 min-w-0">
          <div className="text-[13px] font-semibold text-emerald-900">Иснад верифицирован</div>
          <div className="text-[12px] text-slate-600 leading-relaxed mt-0.5">Цепочка передатчиков подтверждена через dorar.net · 6/6 надёжных рави.</div>
        </div>
        <button className="text-slate-400 hover:text-slate-700 -mt-0.5"><I.X size={14} /></button>
      </div>
    </div>
  </Section>
);

// === Add Source modal & extended context menu ===============================

const AddSourceContextMenu = () => (
  <Section id="add-source-menu" title="Контекстное меню · «Добавить источник»" kicker="11b — context+" hint="Быстрые пресеты для всех типов первоисточников.">
    <div className="grid grid-cols-2 gap-6">
      <Card className="p-5 bg-slate-50/50">
        <div className="text-[11px] font-mono uppercase tracking-wider text-slate-500 mb-3">«Создать связанный X» · подменю</div>
        <div className="flex justify-center">
          <ContextMenu
            title="Добавить источник"
            items={[
              { icon: "BookOpen", label: "Аят Корана", kbd: "K" },
              { icon: "ScrollText", label: "Хадис", kbd: "H" },
              { icon: "Library", label: "Цитата из книги (Шамиля)", kbd: "B" },
              { icon: "Users", label: "Мнение учёного", kbd: "A" },
              { icon: "Quote", label: "Свободная цитата", kbd: "Q" },
              "divider",
              { icon: "ExternalLink", label: "Импортировать по URL…" },
            ]}
          />
        </div>
      </Card>
      <Card className="p-5 bg-slate-50/50">
        <div className="text-[11px] font-mono uppercase tracking-wider text-slate-500 mb-3">Каждый пункт открывает Source picker на нужной вкладке</div>
        <div className="space-y-2">
          {[
            { icon: "BookOpen", label: "Аят Корана", arrow: "→ Source picker · tab Коран" },
            { icon: "ScrollText", label: "Хадис", arrow: "→ Source picker · tab Хадисы" },
            { icon: "Library", label: "Цитата из книги", arrow: "→ Source picker · tab Книги" },
            { icon: "Users", label: "Мнение учёного", arrow: "→ Authority picker" },
            { icon: "Quote", label: "Свободная цитата", arrow: "→ AddSourceModal (ручной ввод)" },
          ].map((it) => {
            const Icon = I[it.icon];
            return (
              <div key={it.label} className="flex items-center gap-2 text-[12px]">
                <Icon size={14} className="text-slate-500" />
                <span className="font-medium text-slate-800 w-44">{it.label}</span>
                <I.ArrowRight size={12} className="text-slate-400" />
                <span className="text-slate-600 font-mono text-[11px]">{it.arrow}</span>
              </div>
            );
          })}
        </div>
      </Card>
    </div>

    <SubSection title="AddSourceModal · ручной ввод (Free Quote)" className="mt-8">
      <div className="checkerboard rounded-lg p-6 flex justify-center">
        <div className="w-[560px] rounded-lg bg-white shadow-2xl border border-slate-200 overflow-hidden">
          <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between">
            <div>
              <h3 className="text-[16px] font-semibold text-slate-900">Добавить свободную цитату</h3>
              <p className="text-[12px] text-slate-500 mt-0.5">Используйте, когда источник не входит в Коран / sunnah.com / Шамилу.</p>
            </div>
            <IconButton icon="X" label="Закрыть" size="sm" />
          </div>
          <div className="p-6 space-y-4">
            <Input label="Заголовок источника" defaultValue="AAOIFI Sharia Standard No. 21" />
            <div className="grid grid-cols-2 gap-3">
              <Input label="Автор / организация" defaultValue="AAOIFI" />
              <Input label="Год" defaultValue="2023" />
            </div>
            <Textarea label="Текст цитаты" rows={3} defaultValue="Параграф 4.2: «Намерение в финансовых контрактах оценивается по совокупности формы и содержания…»" />
            <Textarea label="Оригинал на арабском (опционально)" rows={2} placeholder="Вставьте арабский оригинал здесь…" />
            <Input label="URL / DOI" icon="Link" defaultValue="https://aaoifi.com/standard/no-21" />
            <Input label="Точная цитата (для подписи)" defaultValue="AAOIFI · Standard 21 · §4.2 · 2023" hint="Появится в карточке узла под текстом." />
          </div>
          <div className="px-6 py-4 bg-slate-50 border-t border-slate-200 flex items-center justify-end gap-2">
            <Button variant="ghost">Отмена</Button>
            <Button variant="primary" icon="Plus">Добавить источник</Button>
          </div>
        </div>
      </div>
    </SubSection>
  </Section>
);

// === Lang switcher subsection (for Components section) ======================

const LangSwitcherSubsection = () => (
  <Section id="lang-switcher" title="Language switcher · переключатель языка" kicker="03b — i18n" hint="Закрытое и открытое состояние. AR-опция отображается RTL.">
    <Card className="p-8">
      <div className="flex items-start gap-12">
        <div>
          <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-2">Closed</div>
          <div className="flex flex-col gap-3">
            <LanguageSwitcher current="RU" />
            <LanguageSwitcher current="EN" />
            <LanguageSwitcher current="AR" />
          </div>
        </div>
        <div>
          <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-2">Open</div>
          <div className="relative h-[200px] w-[220px]">
            <LanguageSwitcher current="RU" open />
          </div>
        </div>
        <div className="flex-1">
          <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-2">Поведение</div>
          <ul className="text-[12px] text-slate-700 space-y-1.5 leading-relaxed">
            <li>• <strong>RU / EN</strong> — UI остаётся LTR. Арабский контент в полях рендерится RTL автоматически.</li>
            <li>• <strong>AR</strong> — весь интерфейс становится RTL. Иконки направления (Chevron, Arrow) зеркалятся.</li>
            <li>• Стрелки на графе сохраняют логическое направление from→to независимо от языка.</li>
            <li>• Минимапа, ZoomIn/ZoomOut и слой холста остаются LTR — это содержательные координаты.</li>
            <li>• Хоткеи N/E/Del не меняются — единые во всех локалях.</li>
          </ul>
        </div>
      </div>
    </Card>
  </Section>
);

window.LanguageSwitcher = LanguageSwitcher;
window.BilingualNodeCard = BilingualNodeCard;
window.GradeBadge = GradeBadge;
window.HADITH_GRADE = HADITH_GRADE;
window.AR = AR;
window.ArabicTypographySection = ArabicTypographySection;
window.LangSwitcherSubsection = LangSwitcherSubsection;
window.BilingualSection = BilingualSection;
window.SourceLibrarySection = SourceLibrarySection;
window.AuthoritiesSection = AuthoritiesSection;
window.RTLSection = RTLSection;
window.SettingsSection = SettingsSection;
window.NewToastsSection = NewToastsSection;
window.AddSourceContextMenu = AddSourceContextMenu;
