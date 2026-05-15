// Argument Map — extension pack 3
// Onboarding, Topic settings, Multi-select, Sanad, Multi-grading,
// Translator, Tashkeel, Cross-refs, Extra states, Print preview.

const _AR2 = {
  niyyahShort: "إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ",
  niyyahNoTashkeel: "إنما الأعمال بالنيات",
  prophet: "النَّبِيُّ ﷺ",
  umar: "عُمَرُ بْنُ الْخَطَّابِ",
  alqama: "عَلْقَمَةُ بْنُ وَقَّاصٍ",
  muhammadIbnIbrahim: "مُحَمَّدُ بْنُ إِبْرَاهِيمَ التَّيْمِيُّ",
  yahya: "يَحْيَى بْنُ سَعِيدٍ الْأَنْصَارِيُّ",
  sufyan: "سُفْيَانُ بْنُ عُيَيْنَةَ",
  humaydi: "الْحُمَيْدِيُّ",
  bukhari: "الْإِمَامُ الْبُخَارِيُّ",
};

// === 1. Onboarding ===========================================================

const OnboardingHint = ({ x, y, width = 220, anchor = "tl", text }) => (
  <div className="absolute z-20" style={{ left: x, top: y, width }}>
    <div className="rounded-lg bg-slate-900 text-white shadow-2xl px-3 py-2 text-[12px] leading-snug">
      {text}
    </div>
    <div className="absolute" style={{
      left: anchor.includes("l") ? -1 : "auto",
      right: anchor.includes("r") ? -1 : "auto",
      top: anchor.includes("t") ? "100%" : "auto",
      bottom: anchor.includes("b") ? "100%" : "auto",
    }}>
      <svg width="60" height="50" className={anchor.includes("b") ? "rotate-180" : ""}>
        <path d="M 4 4 Q 30 30, 50 44" stroke="#0f172a" strokeWidth="1.5" strokeDasharray="3 3" fill="none" />
        <circle cx="50" cy="44" r="3" fill="#0f172a" />
      </svg>
    </div>
  </div>
);

const OnboardingChecklist = ({ collapsed = false }) => {
  if (collapsed) {
    return (
      <div className="absolute right-4 bottom-4 z-30">
        <div className="inline-flex items-center gap-1.5 rounded-full bg-emerald-100 border border-emerald-300 text-emerald-800 px-3 h-8 text-[12px] font-semibold shadow-sm">
          <I.CheckCircle size={14} />
          Tour завершён
        </div>
      </div>
    );
  }
  const items = [
    { done: false, label: "Создайте первый тезис (CLAIM)" },
    { done: false, label: "Привяжите доказательство — аят или хадис" },
    { done: false, label: "Добавьте контр-аргумент" },
    { done: false, label: "Оцените статус узла" },
  ];
  return (
    <div className="absolute right-4 bottom-4 z-30 w-[300px] rounded-lg border border-slate-200 bg-white shadow-xl">
      <div className="px-3 py-2 border-b border-slate-200 flex items-center gap-2">
        <I.Sparkles size={14} className="text-indigo-600" />
        <span className="text-[12px] font-semibold text-slate-900">Начало работы</span>
        <span className="ml-auto text-[10px] font-mono text-slate-500">0/4</span>
        <button className="text-slate-400 hover:text-slate-700"><I.X size={13} /></button>
      </div>
      <div className="p-2.5 space-y-1">
        {items.map((it, i) => (
          <label key={i} className="flex items-start gap-2 px-2 py-1.5 rounded hover:bg-slate-50 cursor-pointer">
            <input type="checkbox" className="mt-0.5 accent-indigo-600" defaultChecked={it.done} />
            <span className={cx("text-[12px] leading-relaxed", it.done ? "text-slate-400 line-through" : "text-slate-700")}>{it.label}</span>
          </label>
        ))}
      </div>
      <div className="px-3 py-2 border-t border-slate-100 flex items-center justify-between">
        <a className="text-[11px] text-slate-500 hover:text-slate-700">Пропустить тур</a>
        <Button variant="link" size="xs">Документация →</Button>
      </div>
    </div>
  );
};

const OnboardingGraphScreen = () => (
  <div className="rounded-lg border border-slate-200 bg-white overflow-hidden">
    <div className="h-12 px-4 border-b border-slate-200 flex items-center gap-3 bg-white">
      <Button variant="ghost" size="sm" icon="ArrowLeft">Темы</Button>
      <span className="text-slate-300">/</span>
      <I.BookOpen size={15} className="text-slate-500" />
      <span className="text-[13px] font-semibold text-slate-900 truncate">Условия принятия поклонения</span>
      <Badge tone="indigo" size="sm" icon="Sparkles">новая</Badge>
      <div className="ml-auto flex items-center gap-2">
        <Button variant="ghost" size="sm" icon="Settings" />
        <Avatar name="Анас И." color="indigo" size="sm" />
      </div>
    </div>
    <div className="flex" style={{ height: 540 }}>
      <div className="w-14 border-r border-slate-200 bg-white flex flex-col items-center py-3 gap-1">
        <div className="relative">
          <button className="h-10 w-10 rounded-md flex items-center justify-center bg-indigo-600 text-white ring-4 ring-indigo-100 animate-pulse">
            <I.Plus size={18} />
          </button>
        </div>
        <button className="h-10 w-10 rounded-md flex items-center justify-center text-slate-500 hover:bg-slate-100"><I.Link size={18} /></button>
        <button className="h-10 w-10 rounded-md flex items-center justify-center text-slate-500 hover:bg-slate-100"><I.Move size={18} /></button>
        <div className="my-2 h-px w-8 bg-slate-200" />
        <button className="h-10 w-10 rounded-md flex items-center justify-center text-slate-500 hover:bg-slate-100"><I.Eye size={18} /></button>
      </div>
      <div className="flex-1 dot-grid relative bg-slate-50/40 overflow-hidden">
        {/* Lone root question node */}
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2">
          <NodeCard type="QUESTION" status="UNVERIFIED" title="Каковы условия принятия акта поклонения?" body="Корневой вопрос темы. От него вы построите тезисы и доводы." width={320} selected showHandles />
        </div>

        {/* Hint 1: from toolbar to node */}
        <OnboardingHint x={70} y={120} width={220} anchor="tl" text="Перетащи отсюда чтобы создать узел. Или нажми N." />
        {/* Hint 2: from node handle to empty */}
        <OnboardingHint x={760} y={170} width={230} anchor="tr" text="Тяни от края узла чтобы провести связь к новому или существующему узлу." />
        {/* Hint 3: empty area */}
        <OnboardingHint x={120} y={420} width={230} anchor="bl" text="Правый клик в любом месте — контекстное меню с быстрыми действиями." />

        {/* Decorative arrows */}
        <svg className="absolute inset-0 pointer-events-none" width="100%" height="100%">
          <defs>
            <pattern id="onb-grid" width="22" height="22" patternUnits="userSpaceOnUse">
              <circle cx="1" cy="1" r="0.6" fill="rgba(15,23,42,0.06)" />
            </pattern>
          </defs>
        </svg>

        <OnboardingChecklist />
      </div>
    </div>
  </div>
);

const EmptyLibraryScreen = () => (
  <div className="rounded-lg border border-slate-200 bg-white overflow-hidden">
    <div className="h-12 px-5 border-b border-slate-200 flex items-center gap-3">
      <Button variant="ghost" size="sm" icon="ArrowLeft">Темы</Button>
      <span className="text-slate-300">/</span>
      <I.Library size={15} className="text-slate-500" />
      <span className="text-[13px] font-semibold text-slate-900">Моя библиотека</span>
      <span className="font-mono text-[11px] text-slate-500 ml-1">· 0 источников</span>
    </div>
    <div className="px-8 py-16 grid place-items-center bg-slate-50/40" style={{ minHeight: 420 }}>
      <div className="max-w-[520px] text-center">
        <div className="mx-auto mb-5 inline-flex">
          <svg width="180" height="120" viewBox="0 0 180 120">
            <rect x="20" y="30" width="30" height="80" rx="3" fill="#fef3c7" stroke="#fbbf24" strokeWidth="1.5" />
            <rect x="55" y="20" width="28" height="90" rx="3" fill="#dbeafe" stroke="#60a5fa" strokeWidth="1.5" />
            <rect x="88" y="40" width="32" height="70" rx="3" fill="#dcfce7" stroke="#34d399" strokeWidth="1.5" />
            <rect x="125" y="25" width="34" height="85" rx="3" fill="#fce7f3" stroke="#f472b6" strokeWidth="1.5" />
            <line x1="10" y1="112" x2="170" y2="112" stroke="#94a3b8" strokeWidth="2" strokeLinecap="round" />
          </svg>
        </div>
        <h3 className="text-[18px] font-semibold text-slate-900">У тебя пока нет источников</h3>
        <p className="mt-1.5 text-[13px] text-slate-500 leading-relaxed">Добавь Коран, хадисы и классические труды чтобы привязывать их к узлам в темах.</p>
        <div className="mt-6 grid grid-cols-3 gap-3">
          {[
            { icon: "BookOpen",   label: "Коран",    desc: "quran.com",   tone: "emerald" },
            { icon: "ScrollText", label: "Хадисы",   desc: "sunnah.com",  tone: "indigo" },
            { icon: "Library",    label: "Шамиля",   desc: "shamela.ws",  tone: "amber" },
          ].map((c) => {
            const Icon = I[c.icon];
            const bg = { emerald: "bg-emerald-50 border-emerald-200 hover:bg-emerald-100/70", indigo: "bg-indigo-50 border-indigo-200 hover:bg-indigo-100/70", amber: "bg-amber-50 border-amber-200 hover:bg-amber-100/70" }[c.tone];
            const fg = { emerald: "text-emerald-700", indigo: "text-indigo-700", amber: "text-amber-700" }[c.tone];
            return (
              <button key={c.label} className={cx("rounded-lg border p-4 transition text-left", bg)}>
                <Icon size={22} className={fg} />
                <div className="mt-2 text-[13px] font-semibold text-slate-900">{c.label}</div>
                <div className="text-[10px] font-mono text-slate-500 mt-0.5">{c.desc}</div>
              </button>
            );
          })}
        </div>
        <Button variant="ghost" size="sm" icon="Plus" className="mt-3">Добавить вручную</Button>
      </div>
    </div>
  </div>
);

const OnboardingSection = () => (
  <Section id="onboarding" title="Onboarding · первый запуск" kicker="17 — onboarding" hint="Подсказки появляются на новой теме и исчезают после первого взаимодействия с указанным элементом.">
    <SubSection title="Граф · только что созданная тема">
      <OnboardingGraphScreen />
    </SubSection>
    <SubSection title="Library overview · пустая">
      <EmptyLibraryScreen />
    </SubSection>
    <SubSection title="Tour завершён · свёрнутая капсула">
      <Card className="p-8 bg-slate-50/40 relative h-[120px]">
        <OnboardingChecklist collapsed />
      </Card>
    </SubSection>
  </Section>
);

// === 2. Topic settings drawer ================================================

const TopicSettingsDrawer = () => (
  <div className="relative rounded-lg border border-slate-200 overflow-hidden" style={{ height: 720 }}>
    {/* dimmed canvas behind */}
    <div className="absolute inset-0 dot-grid bg-slate-50/40" />
    <div className="absolute inset-0 bg-slate-900/30 backdrop-blur-[1px]" />
    {/* Drawer */}
    <div className="absolute right-0 top-0 bottom-0 w-[480px] bg-white border-l border-slate-200 shadow-2xl flex flex-col">
      <div className="px-5 py-3 border-b border-slate-200 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <I.Settings size={16} className="text-slate-600" />
          <h3 className="text-[14px] font-semibold text-slate-900">Настройки темы</h3>
        </div>
        <IconButton icon="X" label="Закрыть" size="sm" />
      </div>
      <div className="flex-1 overflow-auto px-5 py-4 space-y-5">
        <div>
          <Input label="Название темы" defaultValue="Дозволенность празднования мавлида" />
        </div>
        <div>
          <Textarea label="Описание" rows={3} defaultValue="Разбор аргументов за и против празднования дня рождения Пророка ﷺ с привлечением хадисов, фетв и мнений учёных." />
        </div>
        <div>
          <label className="text-[12px] font-semibold text-slate-700 inline-flex items-center gap-1.5">
            Корневой вопрос
            <I.Lock size={11} className="text-slate-400" />
          </label>
          <div className="mt-1.5 rounded-md border border-slate-200 bg-slate-50 p-3">
            <div className="flex items-center gap-1.5 mb-1">
              <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-bold bg-violet-100 text-violet-700">QUESTION</span>
              <Badge tone="slate" size="sm">root</Badge>
            </div>
            <div className="text-[13px] font-medium text-slate-800">Дозволено ли празднование мавлида ан-Наби?</div>
            <div className="text-[11px] text-slate-500 mt-1">Корневой узел не пересоздаётся. Изменить можно только текст внутри.</div>
          </div>
        </div>

        <div className="border-t border-slate-100 pt-4">
          <div className="text-[12px] font-semibold text-slate-700 mb-2">Видимость</div>
          <div className="space-y-2">
            <label className="flex items-start gap-2.5 p-3 rounded-md border border-indigo-300 ring-1 ring-indigo-300 bg-indigo-50/50 cursor-pointer">
              <input type="radio" name="vis" defaultChecked className="mt-0.5 accent-indigo-600" />
              <div>
                <div className="text-[13px] font-medium text-slate-900 inline-flex items-center gap-1.5"><I.Lock size={12} /> Private</div>
                <div className="text-[11px] text-slate-500 mt-0.5">Только вы видите эту тему.</div>
              </div>
            </label>
            <label className="flex items-start gap-2.5 p-3 rounded-md border border-slate-200 cursor-pointer hover:bg-slate-50">
              <input type="radio" name="vis" className="mt-0.5 accent-indigo-600" />
              <div className="flex-1">
                <div className="text-[13px] font-medium text-slate-900 inline-flex items-center gap-1.5"><I.Users size={12} /> Shared</div>
                <div className="text-[11px] text-slate-500 mt-0.5 mb-2">Доступ по списку email.</div>
                <Input icon="Mail" placeholder="email@example.com, ещё@..." />
              </div>
            </label>
            <label className="flex items-start gap-2.5 p-3 rounded-md border border-slate-200 cursor-pointer hover:bg-slate-50">
              <input type="radio" name="vis" className="mt-0.5 accent-indigo-600" />
              <div>
                <div className="text-[13px] font-medium text-slate-900 inline-flex items-center gap-1.5"><I.Eye size={12} /> Public</div>
                <div className="text-[11px] text-slate-500 mt-0.5">По прямой ссылке. <span className="text-amber-700">Поисковики могут индексировать.</span></div>
              </div>
            </label>
          </div>
        </div>

        <div className="border-t border-slate-100 pt-4">
          <div className="text-[12px] font-semibold text-slate-700 mb-2">Метаданные</div>
          <dl className="text-[12px] grid grid-cols-[120px_1fr] gap-y-1.5">
            <dt className="text-slate-500">Создана</dt><dd className="text-slate-800">12 марта 2026, 14:22</dd>
            <dt className="text-slate-500">Обновлена</dt><dd className="text-slate-800">2 минуты назад</dd>
            <dt className="text-slate-500">Автор</dt><dd className="text-slate-800">Анас Ибрагимов</dd>
            <dt className="text-slate-500">ID</dt>
            <dd className="font-mono text-[11px] text-slate-700 inline-flex items-center gap-1.5">
              topic_8f3a2c1d-b27e-4f49…
              <button className="text-slate-400 hover:text-slate-700"><I.Copy size={11} /></button>
            </dd>
          </dl>
        </div>

        <div className="border-t border-red-200 pt-4">
          <div className="text-[12px] font-semibold text-red-700 mb-2 inline-flex items-center gap-1.5"><I.AlertTriangle size={12} /> Опасная зона</div>
          <div className="rounded-md border border-red-200 bg-red-50/60 p-3">
            <div className="text-[12px] text-slate-700 leading-relaxed">Удаление темы безвозвратно удаляет все её узлы, связи и историю. Источники остаются в библиотеке.</div>
            <div className="mt-2 flex items-center gap-2">
              <Input placeholder="Введите название темы для подтверждения" />
              <Button variant="danger" size="sm" icon="Trash">Удалить</Button>
            </div>
          </div>
        </div>
      </div>
      <div className="px-5 py-3 border-t border-slate-200 bg-slate-50 flex items-center justify-end gap-2">
        <Button variant="ghost">Отмена</Button>
        <Button variant="primary" icon="Save">Сохранить</Button>
      </div>
    </div>
  </div>
);

const TopicSettingsSection = () => (
  <Section id="topic-settings" title="Topic settings · редактирование темы" kicker="18 — topic" hint="Drawer открывается по шестерёнке в top-bar или клику на title в breadcrumbs.">
    <TopicSettingsDrawer />
  </Section>
);

// === 3. Multi-select =========================================================

const MultiSelectScreen = () => (
  <div className="rounded-lg border border-slate-200 bg-white overflow-hidden">
    <div className="h-10 px-4 border-b border-slate-200 flex items-center gap-2 bg-white">
      <span className="font-mono text-[11px] text-slate-500">/topics/mawlid · multi-select state</span>
    </div>
    <div className="relative dot-grid bg-slate-50/40 overflow-hidden" style={{ height: 540 }}>
      {/* 3 selected nodes scattered */}
      <div className="absolute" style={{ top: 60, left: 80 }}>
        <div className="relative">
          <div className="absolute -inset-1 bg-indigo-100/70 rounded-xl" />
          <NodeCard type="ARGUMENT" status="STANDING" title="Это выражение любви к Пророку ﷺ" body="Любовь — часть веры; радость о его рождении — естественная её манифестация." width={260} selected showHandles />
        </div>
      </div>
      <div className="absolute" style={{ top: 220, left: 380 }}>
        <div className="relative">
          <div className="absolute -inset-1 bg-indigo-100/70 rounded-xl" />
          <NodeCard type="EVIDENCE" status="STANDING" title="Сахих Муслим, хадис №1162" body="«В этот день я был рождён, и в этот день мне было ниспослано откровение»." width={260} selected showHandles />
        </div>
      </div>
      <div className="absolute" style={{ top: 80, left: 700 }}>
        <div className="relative">
          <div className="absolute -inset-1 bg-indigo-100/70 rounded-xl" />
          <NodeCard type="ARGUMENT" status="DISPUTED" title="Прямого указания от саляфов нет" body="Праздник не передаётся от первых трёх поколений как практика общины." width={260} selected showHandles />
        </div>
      </div>

      {/* Lasso rectangle (decorative) */}
      <div className="absolute pointer-events-none" style={{ top: 40, left: 60, width: 920, height: 360 }}>
        <div className="w-full h-full border-[1.5px] border-dashed border-indigo-500/60 bg-indigo-500/5 rounded" />
      </div>

      {/* Floating action bar */}
      <div className="absolute bottom-5 left-1/2 -translate-x-1/2 flex items-center gap-2 rounded-lg bg-slate-900 text-white shadow-2xl px-2 py-1.5">
        <div className="px-2.5 inline-flex items-center gap-1.5 text-[12px]">
          <I.MousePointer2 size={13} />
          <span>Выбрано <span className="font-mono font-bold text-indigo-300">3</span></span>
        </div>
        <div className="w-px h-5 bg-white/20" />
        <button className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded text-[12px] font-medium hover:bg-white/10">
          <I.Trash size={12} className="text-red-300" /> Удалить
        </button>
        <div className="relative">
          <button className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded text-[12px] font-medium bg-white/10">
            <I.CheckCircle size={12} /> Изменить статус
            <I.ChevronUp size={11} />
          </button>
          {/* Open menu */}
          <div className="absolute bottom-full mb-1.5 left-0 w-[220px] rounded-md bg-white text-slate-900 shadow-xl border border-slate-200 py-1">
            <div className="px-3 py-1.5 text-[10px] font-semibold uppercase tracking-wider text-slate-500 border-b border-slate-100">Применить ко всем 3</div>
            {Object.values(STATUS).map((s) => {
              const Icon = I[s.icon];
              return (
                <button key={s.key} className="w-full flex items-center gap-2.5 px-3 py-2 text-left hover:bg-slate-50">
                  <span className={cx("h-5 w-5 rounded grid place-items-center", s.badgeBg, s.text)}><Icon size={12} /></span>
                  <span className="text-[12px] font-medium text-slate-800 flex-1">{s.label}</span>
                  <span className="font-mono text-[10px] text-slate-400">{s.key.slice(0, 4)}</span>
                </button>
              );
            })}
          </div>
        </div>
        <button className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded text-[12px] font-medium hover:bg-white/10">
          <I.Boxes size={12} /> Сгруппировать
        </button>
        <div className="w-px h-5 bg-white/20" />
        <button className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded text-[12px] font-medium hover:bg-white/10">
          <I.X size={12} /> Снять
        </button>
      </div>
    </div>
    <div className="px-5 py-3 bg-slate-50/60 border-t border-slate-200 flex items-center gap-4 text-[11px] text-slate-600">
      <span className="inline-flex items-center gap-1.5"><Kbd>⌘</Kbd>+клик · добавить в выделение</span>
      <span className="inline-flex items-center gap-1.5"><Kbd>⇧</Kbd>+drag · лассо</span>
      <span className="inline-flex items-center gap-1.5"><Kbd>⌘</Kbd>+<Kbd>A</Kbd> · выделить всё</span>
      <span className="inline-flex items-center gap-1.5"><Kbd>Esc</Kbd> · снять</span>
    </div>
  </div>
);

const MultiSelectSection = () => (
  <Section id="multi-select" title="Multi-select · работа с несколькими узлами" kicker="19 — multi-select" hint="Cmd/Ctrl+клик добавляет в выделение. Shift+drag — лассо.">
    <MultiSelectScreen />
  </Section>
);

// === 4. Sanad explorer =======================================================

const SANAD = [
  { ar: _AR2.prophet, name: "Пророк ﷺ", role: "источник откровения", tier: "—", tier_tone: "violet", trust: "—", trust_tone: "violet", link: "sama'", link_tone: "emerald" },
  { ar: _AR2.umar, name: "Умар ибн аль-Хаттаб", trans: "ʿUmar b. al-Khaṭṭāb", role: "сахаби", tier: "Сахабий", tier_tone: "emerald", trust: "адил", trust_tone: "emerald", link: "sama'", link_tone: "emerald", note: "Слышал лично от Пророка ﷺ" },
  { ar: _AR2.alqama, name: "Алькама ибн Ваккас аль-Лейси", trans: "ʿAlqama b. Waqqāṣ", role: "табии", tier: "Табии", tier_tone: "indigo", trust: "сикъа", trust_tone: "emerald", link: "sama'", link_tone: "emerald" },
  { ar: _AR2.muhammadIbnIbrahim, name: "Мухаммад ибн Ибрахим ат-Тейми", trans: "Muḥammad b. Ibrāhīm al-Taymī", role: "табии-табиин", tier: "Атбау Табиин", tier_tone: "indigo", trust: "сикъа", trust_tone: "emerald", link: "sama'", link_tone: "emerald" },
  { ar: _AR2.yahya, name: "Яхья ибн Саид аль-Ансари", trans: "Yaḥyā b. Saʿīd al-Anṣārī", role: "имам, кади Медины", tier: "Имам", tier_tone: "indigo", trust: "сикъа сабт", trust_tone: "emerald", link: "sama'", link_tone: "emerald" },
  { ar: _AR2.sufyan, name: "Суфьян ибн Уйайна", trans: "Sufyān b. ʿUyayna", role: "имам Мекки", tier: "Имам", tier_tone: "indigo", trust: "сикъа хафиз", trust_tone: "emerald", link: "ʿanʿana", link_tone: "amber", note: "Передал «'ан» — рассматривается как муттасил при отсутствии тадлиса" },
  { ar: _AR2.humaydi, name: "аль-Хумайди (Абдуллах)", trans: "al-Ḥumaydī", role: "ученик аш-Шафии", tier: "Шейх Бухари", tier_tone: "indigo", trust: "сикъа хафиз", trust_tone: "emerald", link: "haddathana", link_tone: "emerald" },
  { ar: _AR2.bukhari, name: "Имам аль-Бухари", trans: "al-Bukhārī", role: "сахиб ас-Сахих", tier: "Хафиз", tier_tone: "violet", trust: "сикъа имам", trust_tone: "emerald", link: "—", link_tone: "slate" },
];

const tierClasses = {
  emerald: "bg-emerald-100 text-emerald-700 border-emerald-200",
  indigo: "bg-indigo-100 text-indigo-700 border-indigo-200",
  violet: "bg-violet-100 text-violet-700 border-violet-200",
  amber: "bg-amber-100 text-amber-800 border-amber-200",
  red: "bg-red-100 text-red-700 border-red-200",
  slate: "bg-slate-100 text-slate-700 border-slate-200",
};

const SanadLink = ({ kind, tone }) => {
  const stroke = { emerald: "#10b981", amber: "#f59e0b", red: "#ef4444", slate: "#94a3b8" }[tone] || "#94a3b8";
  const dashed = kind === "ʿanʿana";
  return (
    <div className="relative h-10 flex items-center">
      <svg width="40" height="40" viewBox="0 0 40 40" className="absolute left-1/2 -translate-x-1/2">
        <line x1="20" y1="0" x2="20" y2="32" stroke={stroke} strokeWidth={dashed ? 2 : 3} strokeDasharray={dashed ? "5 4" : undefined} strokeLinecap="round" />
        <polygon points="20,40 14,30 26,30" fill={stroke} />
      </svg>
      <span className="ml-[55%] text-[10px] font-mono text-slate-500 inline-flex items-center gap-1">
        <span className={cx("h-1.5 w-1.5 rounded-full")} style={{ background: stroke }} />
        {kind}
      </span>
    </div>
  );
};

const SanadCard = ({ p, last }) => (
  <>
    <div className="flex items-start gap-3 rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
      <div className="h-12 w-12 rounded-full bg-gradient-to-br from-indigo-100 to-indigo-50 border border-indigo-200 grid place-items-center shrink-0">
        <I.User size={18} className="text-indigo-700" />
      </div>
      <div className="flex-1 min-w-0">
        <div dir="rtl" className="font-naskh text-[18px] font-bold text-slate-900 leading-tight truncate">{p.ar}</div>
        <div className="flex items-baseline gap-2 mt-0.5">
          <span className="text-[12px] font-semibold text-slate-800 truncate">{p.name}</span>
          {p.trans && <span className="font-mono text-[10px] text-slate-500 truncate">{p.trans}</span>}
        </div>
        <div className="text-[11px] text-slate-500">{p.role}</div>
        <div className="mt-1.5 flex items-center gap-1.5 flex-wrap">
          {p.tier !== "—" && <span className={cx("inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-semibold border", tierClasses[p.tier_tone])}>{p.tier}</span>}
          {p.trust !== "—" && <span className={cx("inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-semibold border", tierClasses[p.trust_tone])}><I.ShieldCheck size={10} /> {p.trust}</span>}
        </div>
        {p.note && <div className="mt-1.5 text-[11px] text-slate-600 italic leading-snug">{p.note}</div>}
      </div>
    </div>
    {!last && <SanadLink kind={SANAD[SANAD.indexOf(p) + 1].link} tone={SANAD[SANAD.indexOf(p) + 1].link_tone} />}
  </>
);

const SanadExplorer = () => (
  <Card className="overflow-hidden">
    <div className="px-5 py-3 border-b border-slate-200 bg-gradient-to-r from-emerald-50/50 to-white flex items-center justify-between flex-wrap gap-3">
      <div className="flex items-center gap-2">
        <I.GitBranch size={16} className="text-emerald-700" />
        <div>
          <div className="text-[13px] font-semibold text-slate-900">Иснад · Бухари №1 «Поистине, дела по намерениям»</div>
          <div className="text-[11px] text-slate-500">7 передатчиков · sama' цепочка с одной 'ан'аной</div>
        </div>
      </div>
      <div className="flex items-center gap-2 flex-wrap">
        <Badge tone="emerald" size="md" icon="ShieldCheck">Иснад: сахих</Badge>
        <Button variant="secondary" size="sm" iconRight="ChevronDown">Альтернативные пути (3)</Button>
      </div>
    </div>
    <div className="grid grid-cols-[1fr_320px]">
      <div className="p-5 bg-slate-50/30">
        <div className="max-w-[480px] mx-auto">
          {SANAD.map((p, i) => <SanadCard key={i} p={p} last={i === SANAD.length - 1} />)}
        </div>
      </div>
      <div className="border-l border-slate-200 p-5 space-y-4 bg-white">
        <div>
          <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-2">Легенда связей</div>
          <div className="space-y-2 text-[11px]">
            <div className="flex items-center gap-2"><svg width="14" height="22"><line x1="7" y1="0" x2="7" y2="22" stroke="#10b981" strokeWidth="3" strokeLinecap="round" /></svg><span className="text-slate-700"><span className="font-mono">sama'</span> · слышал лично</span></div>
            <div className="flex items-center gap-2"><svg width="14" height="22"><line x1="7" y1="0" x2="7" y2="22" stroke="#10b981" strokeWidth="3" strokeLinecap="round" /></svg><span className="text-slate-700"><span className="font-mono">haddathana</span> · «нам рассказал»</span></div>
            <div className="flex items-center gap-2"><svg width="14" height="22"><line x1="7" y1="0" x2="7" y2="22" stroke="#f59e0b" strokeWidth="2" strokeDasharray="4 3" strokeLinecap="round" /></svg><span className="text-slate-700"><span className="font-mono">ʿanʿana</span> · «от …» — слабее</span></div>
            <div className="flex items-center gap-2"><svg width="14" height="22"><line x1="7" y1="0" x2="7" y2="22" stroke="#ef4444" strokeWidth="3" strokeLinecap="round" /></svg><span className="text-slate-700"><span className="font-mono">мункати'</span> · разрыв (опасно)</span></div>
          </div>
        </div>
        <div className="border-t border-slate-100 pt-3">
          <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-2">Альтернативные пути</div>
          <div className="space-y-1.5">
            {[
              { src: "Сахих Муслим 1907", tone: "emerald" },
              { src: "Сунан Тирмизи 1647", tone: "emerald" },
              { src: "Сунан Абу Дауд 2201", tone: "emerald" },
            ].map((a) => (
              <div key={a.src} className="flex items-center justify-between rounded-md border border-slate-200 bg-white px-2.5 py-1.5">
                <span className="text-[12px] text-slate-700">{a.src}</span>
                <Badge tone={a.tone} size="sm">сахих</Badge>
              </div>
            ))}
          </div>
        </div>
        <div className="border-t border-slate-100 pt-3">
          <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-2">Перекрёстные ссылки</div>
          <div className="text-[11px] text-slate-600 leading-relaxed">Этот хадис в dorar.net разобран в <a className="text-indigo-600 hover:underline">энциклопедии аль-джами'</a>. 47 учёных оценили иснад.</div>
        </div>
      </div>
    </div>
  </Card>
);

const SanadSection = () => (
  <Section id="sanad" title="Sanad · цепочка передатчиков" kicker="20 — sanad" hint="Каждый передатчик — мини-карточка с поколением и оценкой надёжности. Линия — тип передачи (sama' / 'an'ana).">
    <SanadExplorer />
  </Section>
);

// === 5. Multiple grading =====================================================

const SCHOLAR_GRADES = [
  { scholar: "аль-Бухари", scholar_ar: "الْبُخَارِيُّ", era: "III в.х.", grade: "SAHIH", reason: "Включил в свой Сахих первым номером — высочайшая степень достоверности по его методологии.", source: "Сахих аль-Бухари, глава 1" },
  { scholar: "Муслим", scholar_ar: "مُسْلِمٌ", era: "III в.х.", grade: "SAHIH", reason: "Включил в Сахих муслим (1907). Подтвердил иснад через несколько путей.", source: "Сахих Муслим 1907" },
  { scholar: "ат-Тирмизи", scholar_ar: "التِّرْمِذِيُّ", era: "III в.х.", grade: "HASAN", reason: "В одной из версий через Хаммад ибн Зейд оценил как «хасан сахих» — уровень ниже основной градации Бухари.", source: "Сунан Тирмизи 1647" },
  { scholar: "ан-Навави", scholar_ar: "النَّوَوِيُّ", era: "VII в.х.", grade: "SAHIH", reason: "В шарх Муслима подтвердил оценку Муслима, добавил что хадис мутаватир по смыслу.", source: "Шарх Сахих Муслим, т. 13" },
];

const MultiGradingSection = () => (
  <Section id="multi-grading" title="Множественный grading · разные оценки от разных учёных" kicker="21 — grading" hint="У хадиса может быть несколько оценок. Расхождение — само по себе сигнал.">
    <SubSection title="Чипы оценок · в карточке и в popover">
      <div className="grid grid-cols-2 gap-5">
        <Card className="p-4">
          <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-2">EVIDENCE-карточка</div>
          <div className="rounded-xl border border-slate-200 bg-white relative overflow-hidden" style={{ width: 320 }}>
            <div className="absolute left-0 top-0 bottom-0 w-[5px] bg-emerald-500" />
            <div className="pl-4 pr-3 py-3">
              <div className="flex items-center gap-1.5 mb-2 flex-wrap">
                <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-bold bg-teal-100 text-teal-700">EVIDENCE</span>
                <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-bold bg-amber-100 text-amber-800 border border-amber-300" title="Оценки расходятся">
                  <I.AlertTriangle size={10} /> расхождение
                </span>
              </div>
              <div dir="rtl" className="font-naskh text-[18px] arabic-text text-slate-900 mb-2">{_AR2.niyyahShort}</div>
              <div className="text-[12px] text-slate-700">«Поистине, дела — по намерениям…»</div>
              <div className="mt-2 pt-2 border-t border-slate-100">
                <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-1.5">Оценки</div>
                <div className="flex items-center gap-1 flex-wrap">
                  <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-mono font-bold bg-emerald-50 text-emerald-700 border border-emerald-300">SAHIH · Бухари</span>
                  <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-mono font-bold bg-amber-50 text-amber-800 border border-amber-300">HASAN · Тирмизи</span>
                  <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-mono font-bold bg-emerald-50 text-emerald-700 border border-emerald-300">SAHIH · Навави</span>
                  <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-mono font-medium text-slate-500 hover:bg-slate-50 cursor-pointer">+1 ещё</span>
                </div>
              </div>
            </div>
          </div>
        </Card>
        <Card className="p-4">
          <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-2">Mini-popover при клике на чип</div>
          <div className="rounded-lg border border-slate-200 bg-white shadow-xl p-3 w-[320px]">
            <div className="flex items-center gap-2 mb-2">
              <div className="h-9 w-9 rounded-full bg-amber-100 text-amber-800 grid place-items-center font-naskh font-bold">التر</div>
              <div>
                <div className="text-[12px] font-semibold text-slate-900">ат-Тирмизи</div>
                <div dir="rtl" className="font-naskh text-[13px] text-slate-700">{SCHOLAR_GRADES[2].scholar_ar}</div>
              </div>
              <div className="ml-auto inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-mono font-bold bg-amber-50 text-amber-800 border border-amber-300">HASAN</div>
            </div>
            <div className="text-[11px] text-slate-500 mb-2">III в. хиджры · автор Сунан · муфассир, мухаддис</div>
            <div className="text-[12px] text-slate-700 leading-relaxed">{SCHOLAR_GRADES[2].reason}</div>
            <div className="mt-2 pt-2 border-t border-slate-100 flex items-center justify-between">
              <span className="font-mono text-[10px] text-slate-500">{SCHOLAR_GRADES[2].source}</span>
              <Button variant="link" size="xs" iconRight="ExternalLink">dorar.net</Button>
            </div>
          </div>
        </Card>
      </div>
    </SubSection>

    <SubSection title="Source detail · секция «Оценки»">
      <Card className="overflow-hidden">
        <div className="px-5 py-3 border-b border-slate-200 flex items-center gap-2">
          <I.GitCompareArrows size={14} className="text-amber-700" />
          <span className="text-[13px] font-semibold text-slate-900">Оценки этого хадиса</span>
          <Badge tone="amber" size="sm" icon="AlertTriangle">расхождение между Тирмизи и большинством</Badge>
        </div>
        <div className="overflow-auto">
          <table className="w-full text-[12px]">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200 text-left">
                <th className="px-4 py-2 font-semibold text-slate-700">Учёный</th>
                <th className="px-4 py-2 font-semibold text-slate-700">Эпоха</th>
                <th className="px-4 py-2 font-semibold text-slate-700">Оценка</th>
                <th className="px-4 py-2 font-semibold text-slate-700">Обоснование</th>
                <th className="px-4 py-2 font-semibold text-slate-700">Цитата</th>
              </tr>
            </thead>
            <tbody>
              {SCHOLAR_GRADES.map((s) => {
                const tone = s.grade === "SAHIH" ? "emerald" : s.grade === "HASAN" ? "amber" : "red";
                const cls = { emerald: "bg-emerald-50 text-emerald-700 border-emerald-300", amber: "bg-amber-50 text-amber-800 border-amber-300", red: "bg-red-50 text-red-700 border-red-300" }[tone];
                return (
                  <tr key={s.scholar} className="border-b border-slate-100 hover:bg-slate-50/60">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <div className="h-7 w-7 rounded-full bg-indigo-100 text-indigo-700 grid place-items-center font-naskh text-[11px] font-bold">{s.scholar_ar.slice(0, 3)}</div>
                        <div>
                          <div className="font-semibold text-slate-900">{s.scholar}</div>
                          <div dir="rtl" className="font-naskh text-[12px] text-slate-600">{s.scholar_ar}</div>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3 font-mono text-[11px] text-slate-600">{s.era}</td>
                    <td className="px-4 py-3"><span className={cx("inline-flex px-1.5 h-5 rounded text-[10px] font-mono font-bold border items-center", cls)}>{s.grade}</span></td>
                    <td className="px-4 py-3 text-slate-700 max-w-[400px] leading-snug">{s.reason}</td>
                    <td className="px-4 py-3 font-mono text-[11px] text-slate-600">{s.source}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Card>
    </SubSection>
  </Section>
);

// === 6. Translator attribution ==============================================

const TranslatorSection = () => (
  <Section id="translator" title="Атрибуция переводчика" kicker="22 — translator" hint="Перевод — это интерпретация. Кто переводил — указано всегда.">
    <SubSection title="Малая подпись под переводом">
      <div className="grid grid-cols-3 gap-4">
        {[
          { who: "Кулиев", lang: "RU", body: "«Поистине, дела — только по намерениям…»" },
          { who: "Sahih International", lang: "EN", body: "«Verily, actions are by intentions, and every person shall have what they intended…»" },
          { who: "Османов", lang: "RU", body: "«Воистину, поступки [оцениваются] лишь по намерениям…»" },
        ].map((t) => (
          <Card key={t.who} className="p-4">
            <div dir="rtl" className="font-naskh text-[16px] arabic-text text-slate-900 mb-2 leading-[1.95]">{_AR2.niyyahShort}</div>
            <div className="text-[12px] text-slate-700 leading-relaxed">{t.body}</div>
            <div className="mt-2 pt-2 border-t border-slate-100 font-mono text-[10px] text-slate-500 flex items-center justify-between">
              <span>— пер. {t.who}</span>
              <span className="text-slate-400">{t.lang}</span>
            </div>
          </Card>
        ))}
      </div>
    </SubSection>

    <SubSection title="Переключатель в Source-detail panel">
      <Card className="p-5 max-w-[640px]">
        <div className="flex items-center justify-between mb-3">
          <div className="text-[12px] font-semibold text-slate-700">Перевод</div>
          <div className="relative">
            <button className="inline-flex items-center gap-2 h-8 px-3 rounded-md border border-slate-300 bg-white hover:bg-slate-50 text-[12px] font-medium text-slate-800">
              <I.Languages size={13} className="text-slate-500" />
              Кулиев
              <I.ChevronDown size={12} className="text-slate-400" />
            </button>
            <div className="absolute top-full mt-1 right-0 w-[280px] rounded-md bg-white border border-slate-200 shadow-xl py-1 z-10">
              <div className="px-3 py-1.5 text-[10px] font-semibold uppercase tracking-wider text-slate-500 border-b border-slate-100">Доступные переводы</div>
              {[
                { name: "Кулиев", lang: "RU", current: true, year: "2002" },
                { name: "Османов", lang: "RU", year: "1995" },
                { name: "Крачковский", lang: "RU", year: "1963 · академический" },
                { name: "Sahih International", lang: "EN", year: "2004" },
                { name: "Yusuf Ali", lang: "EN", year: "1934" },
              ].map((t) => (
                <button key={t.name} className={cx("w-full flex items-center gap-2 px-3 py-2 text-left", t.current ? "bg-indigo-50/60" : "hover:bg-slate-50")}>
                  <span className="font-mono text-[10px] font-bold text-slate-400 w-6">{t.lang}</span>
                  <div className="flex-1">
                    <div className="text-[12px] font-medium text-slate-800">{t.name}</div>
                    <div className="text-[10px] text-slate-500">{t.year}</div>
                  </div>
                  {t.current && <I.Check size={12} className="text-indigo-600" />}
                </button>
              ))}
              <div className="border-t border-slate-100 mt-1 pt-1">
                <button className="w-full flex items-center gap-2 px-3 py-1.5 hover:bg-slate-50">
                  <I.Plus size={12} className="text-slate-500" />
                  <span className="text-[12px] text-slate-700">Добавить новый перевод…</span>
                </button>
              </div>
            </div>
          </div>
        </div>
        <div className="text-[13px] text-slate-800 leading-relaxed">«Поистине, дела (оцениваются) только по намерениям, и каждому человеку — то, что он намеревался…»</div>
        <div className="mt-2 font-mono text-[10px] text-slate-500">— пер. Кулиев · 2002</div>
      </Card>
    </SubSection>
  </Section>
);

// === 7. Tashkeel toggle ======================================================

const TashkeelSection = () => (
  <Section id="tashkeel" title="Tashkeel · огласовки on/off" kicker="23 — tashkeel" hint="Опытные читатели часто отключают харакат — текст становится плотнее и читается быстрее.">
    <Card className="p-6">
      <div className="flex items-center justify-between mb-5 pb-4 border-b border-slate-100">
        <div>
          <div className="text-[14px] font-semibold text-slate-900">Показывать огласовки (харакат)</div>
          <div className="text-[12px] text-slate-500 mt-0.5">Глобальная настройка из Settings · применяется ко всем арабским текстам</div>
        </div>
        <button className="relative h-6 w-11 rounded-full bg-indigo-600">
          <span className="absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white shadow translate-x-5 transition-transform" />
        </button>
      </div>
      <div className="grid grid-cols-2 gap-5">
        <div className="rounded-lg border border-emerald-300 ring-1 ring-emerald-200 bg-white p-5">
          <div className="text-[10px] font-mono uppercase tracking-wider text-emerald-700 mb-3">С огласовками · default</div>
          <div dir="rtl" className="font-amiri text-[26px] text-slate-900 leading-[2.1] mb-3">{_AR2.niyyahShort}</div>
          <div className="text-[11px] text-slate-600 leading-relaxed">Точное чтение. Рекомендовано для Корана и для начинающих читателей. По умолчанию.</div>
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <div className="text-[10px] font-mono uppercase tracking-wider text-slate-700 mb-3">Без огласовок · sans-haraqat</div>
          <div dir="rtl" className="font-amiri text-[26px] text-slate-900 leading-[2.1] mb-3">{_AR2.niyyahNoTashkeel}</div>
          <div className="text-[11px] text-slate-600 leading-relaxed">Плотнее, быстрее. Для опытных читателей. Хадисы, классическая проза, заголовки.</div>
        </div>
      </div>

      <div className="mt-6">
        <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-2">Применение в карточке узла</div>
        <div className="grid grid-cols-2 gap-4">
          <div className="rounded-xl border-l-[5px] border-emerald-500 border border-slate-200 bg-white p-3" style={{ width: 320 }}>
            <div className="flex items-center gap-1.5 mb-1.5">
              <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-bold bg-teal-100 text-teal-700">EVIDENCE</span>
              <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-mono font-bold bg-emerald-50 text-emerald-700 border border-emerald-300">SAHIH</span>
            </div>
            <div dir="rtl" className="font-naskh text-[19px] arabic-text text-slate-900">{_AR2.niyyahShort}</div>
            <div className="text-[11px] text-slate-600 mt-1.5">«…дела по намерениям»</div>
          </div>
          <div className="rounded-xl border-l-[5px] border-emerald-500 border border-slate-200 bg-white p-3" style={{ width: 320 }}>
            <div className="flex items-center gap-1.5 mb-1.5">
              <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-bold bg-teal-100 text-teal-700">EVIDENCE</span>
              <span className="inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-mono font-bold bg-emerald-50 text-emerald-700 border border-emerald-300">SAHIH</span>
            </div>
            <div dir="rtl" className="font-naskh text-[19px] arabic-text text-slate-900">{_AR2.niyyahNoTashkeel}</div>
            <div className="text-[11px] text-slate-600 mt-1.5">«…дела по намерениям»</div>
          </div>
        </div>
      </div>
    </Card>
  </Section>
);

// === 8. Cross-references =====================================================

const CrossRefDrawer = () => (
  <div className="rounded-lg border border-slate-200 bg-white shadow-xl overflow-hidden flex flex-col" style={{ width: 600, height: 640 }}>
    <div className="px-5 py-3 border-b border-slate-200 flex items-center gap-2">
      <I.Network size={15} className="text-indigo-700" />
      <div className="flex-1 min-w-0">
        <div className="text-[13px] font-semibold text-slate-900">Хадис о намерениях · использовано 14 раз</div>
        <div className="text-[11px] text-slate-500">Бухари 1 · Муслим 1907 · в 6 темах</div>
      </div>
      <IconButton icon="X" label="Закрыть" size="sm" />
    </div>
    <div className="px-5 py-3 border-b border-slate-200 bg-slate-50/40">
      <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-2">В темах</div>
      <div className="space-y-1">
        {[
          { topic: "Дозволенность мавлида", n: 3, current: true },
          { topic: "Намерение в финансовых контрактах", n: 5 },
          { topic: "Условия принятия поклонения", n: 2 },
          { topic: "Иджтихад в современном фикхе", n: 2 },
          { topic: "Хадисы 40 ан-Навави · разбор", n: 1 },
          { topic: "Этика делового партнёрства", n: 1 },
        ].map((t) => (
          <button key={t.topic} className={cx(
            "w-full flex items-center gap-2 px-2.5 py-1.5 rounded-md text-left text-[12px] transition",
            t.current ? "bg-indigo-50 text-indigo-800 font-medium" : "text-slate-700 hover:bg-white"
          )}>
            <I.BookOpen size={12} className="text-slate-500" />
            <span className="flex-1 truncate">{t.topic}</span>
            <span className="font-mono text-[10px] text-slate-500">{t.n} узл.</span>
            <I.ChevronRight size={11} className="text-slate-400" />
          </button>
        ))}
      </div>
    </div>
    <div className="flex-1 overflow-auto p-4 space-y-3">
      <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-1">Узлы в теме «Дозволенность мавлида»</div>
      {[
        { type: "CLAIM", status: "STANDING", title: "Намерение определяет религиозную ценность поступка", topic: "Дозволенность мавлида" },
        { type: "ARGUMENT", status: "STANDING", title: "Любое поклонение требует чистого намерения", topic: "Дозволенность мавлида" },
        { type: "ARGUMENT", status: "DISPUTED", title: "Намерение не санкционирует саму форму поклонения", topic: "Дозволенность мавлида" },
      ].map((n, i) => {
        const t = NODE_TYPE[n.type], s = STATUS[n.status];
        const Icon = I[t.icon];
        return (
          <div key={i} className="rounded-lg border border-slate-200 bg-white p-3 hover:border-indigo-300 hover:shadow-sm transition cursor-pointer">
            <div className="flex items-center gap-2 mb-1.5">
              <span className={cx("inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-bold", t.chipBg, t.chipText)}>
                <Icon size={11} /> {t.label}
              </span>
              <StatusBadge status={n.status} size="sm" />
              <span className="ml-auto text-[10px] font-mono text-slate-500">{n.topic}</span>
            </div>
            <div className="text-[13px] font-medium text-slate-900 leading-snug">{n.title}</div>
            <div className="mt-2 flex items-center justify-end">
              <Button variant="link" size="xs" iconRight="ArrowRight">Открыть в графе</Button>
            </div>
          </div>
        );
      })}
    </div>
    <div className="px-5 py-3 border-t border-slate-200 bg-slate-50 flex items-center justify-between">
      <span className="text-[11px] text-slate-500">Группировка по темам</span>
      <Button variant="ghost" size="sm" iconRight="ExternalLink">Экспорт списка</Button>
    </div>
  </div>
);

const CrossRefSection = () => (
  <Section id="crossref" title="Cross-references · drill-down «использовано в N»" kicker="24 — crossref" hint="Клик по счётчику в Library card открывает список всех узлов которые цитируют этот источник.">
    <div className="checkerboard rounded-lg p-6 flex justify-center"><CrossRefDrawer /></div>
  </Section>
);

// === 9. Extra states =========================================================

const ExtraStatesSection = () => (
  <Section id="extra-states" title="Состояния · Source picker · Library · Authorities" kicker="25 — states+" hint="Empty / loading / error для исламских модулей.">
    <div className="grid grid-cols-2 gap-5">
      {/* Source picker empty */}
      <Card className="overflow-hidden">
        <div className="px-4 py-2 border-b border-slate-200 text-[11px] font-mono text-slate-500">EMPTY · Source picker</div>
        <div className="p-8 grid place-items-center bg-slate-50/40 text-center" style={{ minHeight: 320 }}>
          <div className="max-w-[360px]">
            <div className="mx-auto mb-3 inline-flex">
              <svg width="80" height="60" viewBox="0 0 80 60">
                <rect x="10" y="14" width="14" height="40" rx="2" fill="#fef3c7" stroke="#f59e0b" strokeWidth="1.5" />
                <rect x="26" y="8"  width="14" height="46" rx="2" fill="#dbeafe" stroke="#60a5fa" strokeWidth="1.5" />
                <rect x="42" y="18" width="14" height="36" rx="2" fill="#dcfce7" stroke="#34d399" strokeWidth="1.5" />
                <rect x="58" y="12" width="14" height="42" rx="2" fill="#fce7f3" stroke="#f472b6" strokeWidth="1.5" />
              </svg>
            </div>
            <div className="text-[14px] font-semibold text-slate-900">Найди источник чтобы привязать</div>
            <p className="mt-1 text-[12px] text-slate-500 leading-relaxed">Открой Коран, Бухари или импортируй книгу из Шамилы.</p>
            <div className="mt-3 flex flex-wrap gap-2 justify-center">
              <Button variant="secondary" size="sm" icon="BookOpen">Коран</Button>
              <Button variant="secondary" size="sm" icon="ScrollText">Бухари</Button>
              <Button variant="secondary" size="sm" icon="ExternalLink">Шамиля</Button>
            </div>
          </div>
        </div>
      </Card>

      {/* Loading Шамиля */}
      <Card className="overflow-hidden">
        <div className="px-4 py-2 border-b border-slate-200 text-[11px] font-mono text-slate-500">LOADING · Шамиля</div>
        <div className="p-4 space-y-2 bg-white" style={{ minHeight: 320 }}>
          <div className="flex items-center gap-2 px-2 py-1.5 rounded bg-amber-50 border border-amber-200 text-[11px] text-amber-800">
            <span className="inline-block h-3 w-3 rounded-full border-2 border-amber-500 border-t-transparent animate-spin" />
            Загружаем из shamela.ws… Это может занять до 10 секунд.
          </div>
          {[0, 1, 2, 3, 4].map((i) => (
            <div key={i} className="flex items-start gap-3 p-3 rounded border border-slate-200 animate-pulse">
              <div className="h-12 w-9 rounded bg-amber-100" />
              <div className="flex-1 space-y-1.5">
                <div className="h-3 rounded bg-slate-200 w-2/3" />
                <div className="h-2.5 rounded bg-slate-100 w-1/2" />
                <div className="h-2.5 rounded bg-slate-100 w-1/3" />
              </div>
            </div>
          ))}
        </div>
      </Card>

      {/* Error Шамиля */}
      <Card className="overflow-hidden">
        <div className="px-4 py-2 border-b border-slate-200 text-[11px] font-mono text-slate-500">ERROR · shamela.ws недоступен</div>
        <div className="p-4 grid place-items-center" style={{ minHeight: 320 }}>
          <div className="rounded-lg border border-red-200 bg-red-50/60 p-4 max-w-[420px]">
            <div className="flex items-start gap-3">
              <div className="h-9 w-9 rounded-md bg-red-100 text-red-700 grid place-items-center shrink-0"><I.AlertCircle size={18} /></div>
              <div className="flex-1">
                <div className="text-[13px] font-semibold text-red-900">Не удалось получить страницу с shamela.ws</div>
                <div className="text-[12px] text-red-800 leading-relaxed mt-1">Возможно сайт временно недоступен или цитируемая страница не существует.</div>
                <div className="mt-3 rounded border border-red-200 bg-white p-2.5 font-mono text-[11px] text-slate-700">
                  <div><span className="text-red-700">type:</span> /probs/upstream-unavailable</div>
                  <div><span className="text-red-700">status:</span> 503</div>
                  <div><span className="text-red-700">upstream:</span> shamela.ws</div>
                </div>
                <div className="mt-3 flex items-center gap-2">
                  <Button variant="danger" size="sm" icon="Refresh">Повторить</Button>
                  <Button variant="secondary" size="sm" icon="Edit">Ввести вручную</Button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </Card>

      {/* Empty authorities */}
      <Card className="overflow-hidden">
        <div className="px-4 py-2 border-b border-slate-200 text-[11px] font-mono text-slate-500">EMPTY · Authorities</div>
        <div className="p-8 grid place-items-center bg-slate-50/40 text-center" style={{ minHeight: 320 }}>
          <div className="max-w-[360px]">
            <div className="mx-auto mb-3 inline-flex">
              <svg width="100" height="60" viewBox="0 0 100 60">
                {[
                  { x: 12, fill: "#e0e7ff" },
                  { x: 40, fill: "#fce7f3" },
                  { x: 68, fill: "#fef3c7" },
                ].map((c, i) => (
                  <g key={i}>
                    <circle cx={c.x + 10} cy="22" r="10" fill={c.fill} stroke="#94a3b8" strokeWidth="1.5" strokeDasharray="3 3" />
                    <rect x={c.x} y="38" width="20" height="3" rx="1.5" fill="#e2e8f0" />
                    <rect x={c.x + 2} y="44" width="16" height="2.5" rx="1.25" fill="#e2e8f0" />
                  </g>
                ))}
              </svg>
            </div>
            <div className="text-[14px] font-semibold text-slate-900">Добавь учёных чьи мнения важны</div>
            <p className="mt-1 text-[12px] text-slate-500 leading-relaxed">Авторитеты привязываются к узлам с указанием позиции (Holds / Opposes / Neutral).</p>
            <Button variant="primary" size="sm" icon="Plus" className="mt-3">Добавить авторитета</Button>
          </div>
        </div>
      </Card>
    </div>
  </Section>
);

// === 10. Print preview =======================================================

const PrintPreviewSection = () => (
  <Section id="print" title="Print / Export · A4 предпросмотр" kicker="26 — print" hint="Академический output для научной работы. Граф как SVG, источники в академическом формате.">
    <div className="rounded-lg border border-slate-200 bg-slate-100 overflow-hidden">
      {/* Toolbar */}
      <div className="px-5 py-3 border-b border-slate-200 bg-white flex items-center gap-3 flex-wrap">
        <span className="text-[12px] font-semibold text-slate-700 mr-2">Включить:</span>
        <button className="inline-flex items-center gap-1.5 h-8 px-3 rounded-md border border-slate-300 bg-white hover:bg-slate-50 text-[12px]">
          <I.Check size={12} className="text-emerald-600" /> Узлы <span className="font-mono text-[10px] text-slate-500">все</span>
          <I.ChevronDown size={11} className="text-slate-400" />
        </button>
        <button className="inline-flex items-center gap-1.5 h-8 px-3 rounded-md border border-slate-300 bg-white hover:bg-slate-50 text-[12px]">
          <I.Check size={12} className="text-emerald-600" /> Источники
        </button>
        <button className="inline-flex items-center gap-1.5 h-8 px-3 rounded-md border border-slate-300 bg-white hover:bg-slate-50 text-[12px] text-slate-500">
          <span className="h-3.5 w-3.5 rounded border border-slate-400" /> Иснады
        </button>
        <div className="w-px h-5 bg-slate-200 mx-1" />
        <span className="text-[12px] text-slate-700">Размер:</span>
        <button className="inline-flex items-center gap-1 h-8 px-2.5 rounded-md border border-slate-300 bg-white text-[12px] font-mono">A4 <I.ChevronDown size={11} className="text-slate-400" /></button>
        <button className="inline-flex items-center gap-1 h-8 px-2.5 rounded-md border border-slate-300 bg-white text-[12px] font-mono">Книжная <I.ChevronDown size={11} className="text-slate-400" /></button>
        <span className="ml-auto inline-flex items-center gap-2">
          <Button variant="secondary" size="sm" icon="Eye">Превью в браузере</Button>
          <Button variant="primary" size="sm" icon="Download">Скачать PDF</Button>
        </span>
      </div>

      {/* A4 page */}
      <div className="p-10 grid place-items-center bg-[#e2e8f0]">
        <div className="bg-white shadow-2xl" style={{ width: 794, minHeight: 1123, padding: "60px 70px" }}>
          {/* Page header */}
          <div className="border-b-2 border-slate-900 pb-4 mb-6">
            <div className="text-[10px] font-mono uppercase tracking-[0.18em] text-slate-500 mb-1">Argument Map · структурированный разбор</div>
            <h1 className="text-[32px] font-bold tracking-tight text-slate-900 leading-tight" style={{ fontFamily: 'Georgia, "Times New Roman", serif' }}>
              Дозволенность празднования мавлида ан-Наби
            </h1>
            <p className="mt-2 italic text-[14px] text-slate-600 leading-relaxed">
              Разбор аргументов за и против празднования дня рождения Пророка ﷺ с привлечением хадисов, фетв и мнений учёных.
            </p>
            <div className="mt-3 flex items-center gap-4 text-[11px] font-mono text-slate-500">
              <span>Автор: А. Ибрагимов</span>
              <span>·</span>
              <span>Узлов: 10</span>
              <span>·</span>
              <span>Источников: 7</span>
              <span>·</span>
              <span>Версия: v3</span>
            </div>
          </div>

          {/* Graph snapshot */}
          <div className="mb-6">
            <h2 className="text-[16px] font-bold text-slate-900 mb-2" style={{ fontFamily: 'Georgia, serif' }}>1. Граф аргументации</h2>
            <div className="rounded border border-slate-300 overflow-hidden">
              <svg viewBox="0 0 700 320" className="w-full" style={{ background: "#fafafa" }}>
                <defs>
                  <marker id="pp-arr-em" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
                    <path d="M 0 1 L 9 5 L 0 9 z" fill="#10b981" />
                  </marker>
                  <marker id="pp-arr-rd" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
                    <path d="M 0 1 L 9 5 L 0 9 z" fill="#ef4444" />
                  </marker>
                  <marker id="pp-arr-sl" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
                    <path d="M 0 1 L 9 5 L 0 9 z" fill="#94a3b8" />
                  </marker>
                </defs>
                {/* Edges */}
                <path d="M 350 60 C 350 90, 350 100, 350 130" stroke="#94a3b8" strokeWidth="1.5" fill="none" markerEnd="url(#pp-arr-sl)" />
                <path d="M 130 220 C 200 200, 280 180, 330 165" stroke="#10b981" strokeWidth="2" fill="none" markerEnd="url(#pp-arr-em)" />
                <path d="M 580 220 C 510 200, 430 180, 380 165" stroke="#ef4444" strokeWidth="2" fill="none" markerEnd="url(#pp-arr-rd)" />
                <path d="M 130 290 C 130 260, 130 240, 130 230" stroke="#10b981" strokeWidth="2" fill="none" markerEnd="url(#pp-arr-em)" />
                <path d="M 580 290 C 580 260, 580 240, 580 230" stroke="#ef4444" strokeWidth="2" fill="none" markerEnd="url(#pp-arr-rd)" />

                {/* Root question */}
                <g transform="translate(290 30)">
                  <rect x="0" y="0" width="120" height="36" rx="6" fill="#faf5ff" stroke="#a78bfa" strokeWidth="1.5" />
                  <rect x="0" y="0" width="4" height="36" fill="#94a3b8" />
                  <text x="14" y="15" fontSize="8" fontWeight="700" fill="#7c3aed">QUESTION</text>
                  <text x="14" y="28" fontSize="9" fill="#0f172a">Дозволено ли празднование?</text>
                </g>
                {/* Claim */}
                <g transform="translate(280 130)">
                  <rect x="0" y="0" width="140" height="42" rx="6" fill="#fffbeb" stroke="#f59e0b" strokeWidth="1.5" />
                  <rect x="0" y="0" width="4" height="42" fill="#f59e0b" />
                  <text x="14" y="15" fontSize="8" fontWeight="700" fill="#d97706">CLAIM</text>
                  <text x="14" y="28" fontSize="9" fill="#0f172a">Мавлид является</text>
                  <text x="14" y="38" fontSize="9" fill="#0f172a">дозволенной практикой</text>
                </g>
                {/* Argument for */}
                <g transform="translate(50 220)">
                  <rect x="0" y="0" width="160" height="42" rx="6" fill="#f0fdf4" stroke="#10b981" strokeWidth="1.5" />
                  <rect x="0" y="0" width="4" height="42" fill="#10b981" />
                  <text x="14" y="15" fontSize="8" fontWeight="700" fill="#059669">ARGUMENT</text>
                  <text x="14" y="28" fontSize="9" fill="#0f172a">Это выражение любви</text>
                  <text x="14" y="38" fontSize="9" fill="#0f172a">к Пророку ﷺ</text>
                </g>
                {/* Argument against */}
                <g transform="translate(490 220)">
                  <rect x="0" y="0" width="160" height="42" rx="6" fill="#fef2f2" stroke="#ef4444" strokeWidth="1.5" />
                  <rect x="0" y="0" width="4" height="42" fill="#ef4444" />
                  <text x="14" y="15" fontSize="8" fontWeight="700" fill="#dc2626">ARGUMENT</text>
                  <text x="14" y="28" fontSize="9" fill="#0f172a">Не известно от</text>
                  <text x="14" y="38" fontSize="9" fill="#0f172a">саляфов</text>
                </g>
                {/* Evidence STANDING */}
                <g transform="translate(50 290)">
                  <rect x="0" y="0" width="160" height="22" rx="6" fill="#f0fdfa" stroke="#10b981" strokeWidth="1.5" />
                  <rect x="0" y="0" width="4" height="22" fill="#10b981" />
                  <text x="14" y="15" fontSize="8" fill="#0f172a">Сахих Муслим 1162</text>
                </g>
                {/* Evidence REFUTED */}
                <g transform="translate(490 290)">
                  <rect x="0" y="0" width="160" height="22" rx="6" fill="#fef2f2" stroke="#ef4444" strokeWidth="1.5" />
                  <rect x="0" y="0" width="4" height="22" fill="#ef4444" />
                  <text x="14" y="15" fontSize="8" fill="#0f172a">Маджму' аль-Фатава, т.27</text>
                </g>
              </svg>
            </div>
            <div className="mt-2 text-[10px] text-slate-500 italic">Рис. 1. Структура аргументации. Зелёная заливка — поддержка, красная — опровержение, янтарная — спорный статус.</div>
          </div>

          {/* Sources */}
          <div className="mb-6">
            <h2 className="text-[16px] font-bold text-slate-900 mb-2" style={{ fontFamily: 'Georgia, serif' }}>2. Источники</h2>
            <ol className="space-y-2 text-[11.5px] text-slate-800 leading-relaxed" style={{ fontFamily: 'Georgia, "Times New Roman", serif' }}>
              <li className="flex gap-2">
                <span className="font-mono text-[11px] text-slate-500 w-6 shrink-0">[1]</span>
                <div>
                  <span>Аль-Бухари, Мухаммад ибн Исмаил. </span>
                  <em>Сахих аль-Бухари</em>, хадис №1, «Книга откровения». III в. хиджры.
                  <div className="font-mono text-[10px] text-slate-500 mt-0.5">URL: sunnah.com/bukhari:1</div>
                </div>
              </li>
              <li className="flex gap-2">
                <span className="font-mono text-[11px] text-slate-500 w-6 shrink-0">[2]</span>
                <div>
                  <span>Муслим ибн аль-Хаджжадж. </span>
                  <em>Сахих Муслим</em>, хадис №1162, глава о посте в понедельник.
                  <div className="font-mono text-[10px] text-slate-500 mt-0.5">URL: sunnah.com/muslim:1162</div>
                </div>
              </li>
              <li className="flex gap-2">
                <span className="font-mono text-[11px] text-slate-500 w-6 shrink-0">[3]</span>
                <div>
                  <span>ас-Суюти, Джалал ад-Дин. </span>
                  <em>Хусн уль-максид фи амаль аль-маулид</em>. Том 1, стр. 22. IX в. хиджры.
                  <div className="font-mono text-[10px] text-slate-500 mt-0.5">URL: shamela.ws/book/22203/22</div>
                </div>
              </li>
              <li className="flex gap-2">
                <span className="font-mono text-[11px] text-slate-500 w-6 shrink-0">[4]</span>
                <div>
                  <span>Ибн Таймия, Такы ад-Дин Ахмад. </span>
                  <em>Маджму' аль-Фатава</em>. Том 27, стр. 152.
                  <div className="font-mono text-[10px] text-slate-500 mt-0.5">URL: shamela.ws/book/22203/27#152</div>
                </div>
              </li>
              <li className="flex gap-2">
                <span className="font-mono text-[11px] text-slate-500 w-6 shrink-0">[5]</span>
                <div>
                  <span>Коран. </span>Сура «Аль-Бакара» 2:152, 2:255.
                  <div className="font-mono text-[10px] text-slate-500 mt-0.5">URL: quran.com/2/255</div>
                </div>
              </li>
            </ol>
          </div>

          {/* Footer */}
          <div className="mt-12 pt-3 border-t border-slate-300 flex items-center justify-between text-[10px] font-mono text-slate-500">
            <span>Сгенерировано 7 мая 2026, 14:32</span>
            <span>Argument Map · argumentmap.app</span>
            <span>Стр. 1 / 3</span>
          </div>
        </div>
      </div>
    </div>
  </Section>
);

// === Exports ================================================================

window.OnboardingSection = OnboardingSection;
window.TopicSettingsSection = TopicSettingsSection;
window.MultiSelectSection = MultiSelectSection;
window.SanadSection = SanadSection;
window.MultiGradingSection = MultiGradingSection;
window.TranslatorSection = TranslatorSection;
window.TashkeelSection = TashkeelSection;
window.CrossRefSection = CrossRefSection;
window.ExtraStatesSection = ExtraStatesSection;
window.PrintPreviewSection = PrintPreviewSection;
