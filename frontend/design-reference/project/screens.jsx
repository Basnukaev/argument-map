// Screen-level mockups for Argument Map showcase.

// === Topics list screen ===

const TOPICS = [
  { id: "t1", title: "Дозволенность празднования мавлида ан-Наби", desc: "Разбор позиций учёных от Ибн Таймийи до современных мнений. Аргументы за и против, классификация уровней практики.", author: "Анас И.", color: "indigo", date: "2 мая", nodes: 24, edges: 31, status: { STANDING: 8, DISPUTED: 3, REFUTED: 5, UNVERIFIED: 8 } },
  { id: "t2", title: "Ранние оптические аргументы Альхазена против эманативной теории зрения", desc: "Систематизация доводов из «Книги оптики», сравнение с Евклидом и Птолемеем.", author: "М. Тарасов", color: "teal", date: "28 апр", nodes: 17, edges: 22, status: { STANDING: 11, DISPUTED: 2, REFUTED: 1, UNVERIFIED: 3 } },
  { id: "t3", title: "Применимость доктрины crown copyright в open data", desc: "Юридический разбор: где заканчивается государственное право и начинается общественное достояние.", author: "Е. Полякова", color: "amber", date: "26 апр", nodes: 19, edges: 26, status: { STANDING: 4, DISPUTED: 9, REFUTED: 2, UNVERIFIED: 4 } },
  { id: "t4", title: "Критика тезиса о слабой версии антропного принципа", desc: "Логические разрывы в формулировке Картера. Контраргументы Сасскинда и Смолина.", author: "К. Левин", color: "violet", date: "21 апр", nodes: 12, edges: 14, status: { STANDING: 5, DISPUTED: 1, REFUTED: 4, UNVERIFIED: 2 } },
  { id: "t5", title: "Допустимость закята с криптовалютных активов", desc: "Фикх современных финансов. Позиции AAOIFI и индивидуальных муфтиев.", author: "Анас И.", color: "indigo", date: "18 апр", nodes: 31, edges: 42, status: { STANDING: 14, DISPUTED: 8, REFUTED: 3, UNVERIFIED: 6 } },
  { id: "t6", title: "Проблема демаркации научного знания: от Поппера до Лаудана", desc: "Историко-философский разбор. Кризис критериев, тупик логического позитивизма.", author: "Д. Хабибуллина", color: "rose", date: "12 апр", nodes: 28, edges: 38, status: { STANDING: 9, DISPUTED: 11, REFUTED: 2, UNVERIFIED: 6 } },
];

const TopicMiniGraph = ({ status }) => {
  // tiny illustrative graph using status counts
  const nodes = [
    { x: 60, y: 18, c: "indigo", r: 5 },
    { x: 28, y: 50, c: "emerald", r: 4 },
    { x: 60, y: 64, c: status.DISPUTED > 4 ? "amber" : "emerald", r: 4.5 },
    { x: 96, y: 50, c: status.REFUTED > 3 ? "red" : "emerald", r: 4 },
    { x: 14, y: 80, c: "emerald", r: 3 },
    { x: 44, y: 86, c: status.DISPUTED > 4 ? "amber" : "slate", r: 3 },
    { x: 78, y: 86, c: status.REFUTED > 3 ? "red" : "emerald", r: 3 },
    { x: 108, y: 80, c: "slate", r: 3 },
  ];
  const fills = { indigo: "#6366f1", emerald: "#10b981", amber: "#f59e0b", red: "#ef4444", slate: "#cbd5e1" };
  const edges = [[0,1],[0,2],[0,3],[1,4],[2,5],[3,6],[3,7]];
  return (
    <svg viewBox="0 0 124 100" className="w-full h-full">
      <defs>
        <pattern id="dotmini" width="8" height="8" patternUnits="userSpaceOnUse">
          <circle cx="1" cy="1" r="0.7" fill="rgba(15,23,42,0.10)" />
        </pattern>
      </defs>
      <rect width="124" height="100" fill="url(#dotmini)" />
      {edges.map(([a, b], i) => (
        <line key={i} x1={nodes[a].x} y1={nodes[a].y} x2={nodes[b].x} y2={nodes[b].y} stroke="#94a3b8" strokeWidth="1" strokeOpacity="0.7" />
      ))}
      {nodes.map((n, i) => (
        <circle key={i} cx={n.x} cy={n.y} r={n.r} fill={fills[n.c]} stroke="white" strokeWidth="1.5" />
      ))}
    </svg>
  );
};

const TopicCard = ({ topic }) => (
  <Card className="group hover:shadow-md transition-all hover:-translate-y-0.5 hover:border-slate-300 cursor-pointer overflow-hidden">
    <div className="relative h-[110px] bg-gradient-to-br from-slate-50 to-white border-b border-slate-100">
      <TopicMiniGraph status={topic.status} />
      <div className="absolute top-2 right-2 inline-flex items-center gap-1 bg-white/90 backdrop-blur border border-slate-200 rounded-md h-5 px-1.5 text-[10px] font-medium text-slate-600">
        <I.Network size={10} />
        {topic.nodes} · {topic.edges}
      </div>
    </div>
    <div className="p-4">
      <h3 className="text-[14px] font-semibold text-slate-900 leading-snug line-clamp-2 group-hover:text-indigo-700 transition-colors">{topic.title}</h3>
      <p className="mt-1.5 text-[12px] leading-relaxed text-slate-500 line-clamp-2">{topic.desc}</p>
      <div className="mt-3 flex items-center justify-between">
        <div className="inline-flex items-center gap-1.5">
          <Avatar name={topic.author} color={topic.color} size="sm" />
          <span className="text-[11px] text-slate-600 font-medium">{topic.author}</span>
        </div>
        <div className="inline-flex items-center gap-1 text-[11px] text-slate-500">
          <I.Calendar size={11} />
          {topic.date}
        </div>
      </div>
      <div className="mt-3 pt-3 border-t border-slate-100 flex items-center gap-1.5">
        <span className="text-[10px] uppercase tracking-wide text-slate-400 font-semibold mr-1">статус</span>
        {Object.entries(topic.status).map(([k, n]) => n > 0 && (
          <span key={k} className={cx("inline-flex items-center gap-1 text-[10px] font-mono font-semibold", STATUS[k].text)} title={STATUS[k].label}>
            <span className={cx("h-2 w-2 rounded-sm", STATUS[k].bar)} />
            {n}
          </span>
        ))}
      </div>
    </div>
  </Card>
);

const TopicsScreen = () => (
  <div className="rounded-lg border border-slate-200 bg-white overflow-hidden">
    {/* Top bar */}
    <div className="h-12 px-5 border-b border-slate-200 flex items-center justify-between bg-white">
      <div className="flex items-center gap-3">
        <div className="inline-flex items-center gap-2">
          <div className="h-7 w-7 rounded-md bg-indigo-600 grid place-items-center text-white"><I.Network size={16} /></div>
          <span className="font-bold text-[14px] text-slate-900 tracking-tight">Argument Map</span>
        </div>
        <div className="h-5 w-px bg-slate-200" />
        <nav className="flex items-center gap-1 text-[12px]">
          <span className="px-2.5 h-7 inline-flex items-center rounded-md bg-slate-100 text-slate-900 font-medium">Темы</span>
          <span className="px-2.5 h-7 inline-flex items-center rounded-md text-slate-600 hover:bg-slate-50">Авторитеты</span>
          <span className="px-2.5 h-7 inline-flex items-center rounded-md text-slate-600 hover:bg-slate-50">Источники</span>
        </nav>
      </div>
      <div className="flex items-center gap-2">
        <IconButton icon="Search" label="Поиск" size="sm" />
        <IconButton icon="Settings" label="Настройки" size="sm" />
        <Avatar name="Анас И." color="indigo" />
      </div>
    </div>

    <div className="px-10 py-8">
      <div className="flex items-end justify-between mb-6">
        <div>
          <h1 className="text-[28px] font-bold tracking-tight text-slate-900">Темы аргументации</h1>
          <p className="text-[13px] text-slate-500 mt-1">Структурированные дискуссии в виде графа · <span className="font-mono font-semibold text-slate-700">{TOPICS.length} активных</span></p>
        </div>
        <Button variant="primary" icon="Plus">Создать тему</Button>
      </div>

      <div className="flex items-center gap-3 mb-6">
        <Input icon="Search" placeholder="Поиск по теме, автору, тегам…" className="flex-1 max-w-md" />
        <div className="flex items-center gap-1 ml-auto">
          <Button variant="secondary" size="sm" icon="Filter">Фильтр</Button>
          <Button variant="secondary" size="sm" iconRight="ChevronDown">Сортировка: новые</Button>
          <div className="h-7 mx-1 w-px bg-slate-200" />
          <IconButton icon="Layers" label="Сетка" size="sm" active />
          <IconButton icon="ListTree" label="Список" size="sm" />
        </div>
      </div>

      <div className="grid grid-cols-3 gap-5">
        {TOPICS.map((t) => <TopicCard key={t.id} topic={t} />)}
      </div>
    </div>
  </div>
);

// === Topic creation screen ===

const CreateTopicScreen = () => (
  <div className="rounded-lg border border-slate-200 bg-white overflow-hidden">
    <div className="h-12 px-5 border-b border-slate-200 flex items-center gap-3 bg-white">
      <Button variant="ghost" size="sm" icon="ArrowLeft">Темы</Button>
      <span className="text-slate-300">/</span>
      <span className="text-[13px] font-medium text-slate-900">Новая тема</span>
    </div>
    <div className="px-10 py-12 bg-slate-50/60 min-h-[640px]">
      <div className="max-w-[640px] mx-auto">
        <h1 className="text-[28px] font-bold tracking-tight text-slate-900">Создание темы</h1>
        <p className="text-[13px] text-slate-500 mt-1.5">Тема — это контейнер для структурированной дискуссии. Корневой вопрос станет первым узлом графа.</p>

        <Card className="mt-8 p-7">
          <div className="space-y-5">
            <Input label="Название темы" placeholder="Например, «Дозволенность мавлида»" defaultValue="Применимость аналогии (кияс) в современных финансовых контрактах" />
            <Textarea label="Описание" hint="Кратко опишите контекст и границы дискуссии. Можно добавить позже." rows={3} defaultValue="Можно ли распространять кияс с классических рибавийных товаров на криптоактивы? Сравнение позиций AAOIFI, Дар уль-Ифта Египта и индивидуальных муфтиев Залива." />
            <div className="relative">
              <Textarea
                label="Корневой вопрос темы"
                hint="Это будет первый узел графа — узел типа QUESTION. Можно изменить позже."
                rows={3}
                defaultValue="Является ли применение кияса к криптоактивам методологически обоснованным?"
              />
              <span className="absolute right-0 top-0 inline-flex items-center gap-1 text-[11px] font-mono text-violet-700 bg-violet-50 border border-violet-200 px-2 h-5 rounded">
                <I.CircleHelp size={11} /> QUESTION
              </span>
            </div>
            <div>
              <label className="text-[12px] font-medium text-slate-700">Теги <span className="text-slate-400">— опционально</span></label>
              <div className="mt-1.5 flex flex-wrap gap-2 items-center min-h-[36px] rounded-md border border-slate-300 px-2 py-1.5 bg-white">
                <Badge tone="indigo" icon="Tag" size="md">фикх</Badge>
                <Badge tone="indigo" icon="Tag" size="md">финансы</Badge>
                <Badge tone="indigo" icon="Tag" size="md">кияс</Badge>
                <input className="flex-1 min-w-[120px] outline-none text-[12px] bg-transparent" placeholder="Добавить тег…" />
              </div>
            </div>
          </div>
        </Card>

        <div className="mt-6">
          <p className="text-[11px] uppercase tracking-wider font-semibold text-slate-500 mb-2.5 flex items-center gap-1.5"><I.Eye size={12} /> Превью корневого узла</p>
          <NodeCard
            type="QUESTION"
            status="UNVERIFIED"
            title="Является ли применение кияса к криптоактивам методологически обоснованным?"
            body="Этот узел будет создан автоматически как корневой узел темы."
            width={420}
          />
        </div>

        <div className="mt-8 flex items-center justify-between">
          <Button variant="ghost">Отмена</Button>
          <div className="flex items-center gap-2">
            <Button variant="secondary" icon="Save">Сохранить как черновик</Button>
            <Button variant="primary" iconRight="ArrowRight">Создать тему</Button>
          </div>
        </div>
      </div>
    </div>
  </div>
);

// === Main graph screen ===

const ToolbarBtn = ({ icon, label, active, kbd, danger }) => {
  const Icon = I[icon];
  return (
    <Tooltip label={kbd ? `${label} · ${kbd}` : label} side="right">
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

const GraphScreen = () => (
  <div className="rounded-lg border border-slate-200 bg-white overflow-hidden">
    {/* Top bar */}
    <div className="h-12 px-4 border-b border-slate-200 flex items-center justify-between bg-white">
      <div className="flex items-center gap-2 min-w-0">
        <Button variant="ghost" size="sm" icon="ArrowLeft">Темы</Button>
        <span className="text-slate-300">/</span>
        <I.BookOpen size={15} className="text-slate-500" />
        <span className="text-[13px] font-semibold text-slate-900 truncate">Дозволенность празднования мавлида ан-Наби</span>
        <Badge tone="slate" size="sm">v3</Badge>
        <Badge tone="emerald" size="sm" icon="Check">сохранено</Badge>
      </div>
      <div className="flex items-center gap-2">
        <div className="hidden md:flex items-center gap-1 mr-2">
          <Avatar name="Анас И." color="indigo" size="sm" />
          <Avatar name="М. Тарасов" color="teal" size="sm" />
          <Avatar name="Е. П." color="amber" size="sm" />
        </div>
        <Button variant="secondary" size="sm" icon="History">Ревизии</Button>
        <Button variant="secondary" size="sm" icon="Eye">Превью</Button>
        <Button variant="primary" size="sm" icon="Sparkles">Поделиться</Button>
        <IconButton icon="Settings" label="Настройки" size="sm" />
      </div>
    </div>

    <div className="flex" style={{ height: 820 }}>
      {/* Left toolbar */}
      <div className="w-14 border-r border-slate-200 bg-white flex flex-col items-center py-3 gap-1">
        <ToolbarBtn icon="Plus" label="Добавить узел" kbd="N" />
        <ToolbarBtn icon="Link" label="Создать связь" kbd="E" />
        <ToolbarBtn icon="Lasso" label="Выделить область" />
        <ToolbarBtn icon="Move" label="Переместить" active />
        <div className="my-2 h-px w-8 bg-slate-200" />
        <ToolbarBtn icon="Eye" label="Подписи рёбер" active />
        <ToolbarBtn icon="Filter" label="Фильтр статусов" />
        <ToolbarBtn icon="Hash" label="Сетка" active />
        <div className="my-2 h-px w-8 bg-slate-200" />
        <ToolbarBtn icon="History" label="Откатить" kbd="⌘Z" />
        <ToolbarBtn icon="Trash" label="Удалить" kbd="Del" danger />
        <div className="flex-1" />
        <ToolbarBtn icon="ScrollText" label="Шпаргалка" />
      </div>

      {/* Canvas */}
      <div className="relative flex-1 overflow-hidden">
        <Graph />
        {/* Floating legend (bottom-left) */}
        <div className="absolute left-4 bottom-4 bg-white/95 backdrop-blur border border-slate-200 rounded-md shadow-md p-3 max-w-[280px]">
          <div className="text-[10px] font-semibold uppercase tracking-wider text-slate-500 mb-2">Легенда</div>
          <div className="grid grid-cols-2 gap-x-3 gap-y-1.5">
            {Object.values(STATUS).map((s) => (
              <div key={s.key} className="flex items-center gap-1.5 text-[11px] text-slate-700">
                <span className={cx("h-2.5 w-3 rounded-sm", s.bar)} />
                {s.label}
              </div>
            ))}
          </div>
        </div>
        {/* Floating zoom controls (bottom-center) */}
        <div className="absolute bottom-4 left-1/2 -translate-x-1/2 bg-white/95 backdrop-blur border border-slate-200 rounded-md shadow-md flex items-center gap-0.5 p-1">
          <IconButton icon="ZoomOut" label="Уменьшить" size="sm" />
          <span className="px-2 text-[11px] font-mono font-semibold text-slate-700 tabular-nums w-12 text-center">86 %</span>
          <IconButton icon="ZoomIn" label="Увеличить" size="sm" />
          <div className="w-px h-5 bg-slate-200 mx-1" />
          <IconButton icon="Maximize" label="По размеру" size="sm" />
          <IconButton icon="Crosshair" label="К корню" size="sm" />
        </div>
        {/* MiniMap (bottom-right) */}
        <div className="absolute bottom-4 right-4">
          <MiniMap viewport={{ x: 30, y: 20, w: 120, h: 70 }} />
        </div>
        {/* Hotkeys hint (top-right) */}
        <div className="absolute top-4 right-4 bg-white/95 backdrop-blur border border-slate-200 rounded-md shadow-sm px-3 py-2 flex items-center gap-3 text-[11px] text-slate-600">
          <span className="inline-flex items-center gap-1"><Kbd>N</Kbd> узел</span>
          <span className="inline-flex items-center gap-1"><Kbd>E</Kbd> связь</span>
          <span className="inline-flex items-center gap-1"><Kbd>Del</Kbd> удалить</span>
          <span className="inline-flex items-center gap-1"><Kbd>⌘</Kbd><Kbd>Z</Kbd> отмена</span>
        </div>
      </div>
    </div>
  </div>
);

// === Side panel: NODE details ===

const NodePanelSection = ({ icon, title, count, children, defaultOpen = true, lazy }) => {
  const [open, setOpen] = React.useState(defaultOpen);
  const Icon = I[icon];
  return (
    <div className="border-t border-slate-200">
      <button onClick={() => setOpen(!open)} className="w-full px-5 py-3 flex items-center gap-2 text-left hover:bg-slate-50 transition-colors">
        <Icon size={14} className="text-slate-500" />
        <span className="text-[12px] font-semibold uppercase tracking-wider text-slate-700">{title}</span>
        {count !== undefined && <span className="text-[11px] font-mono text-slate-400">{count}</span>}
        <span className="ml-auto">
          <I.ChevronDown size={14} className={cx("text-slate-400 transition-transform", !open && "-rotate-90")} />
        </span>
      </button>
      {open && (
        <div className="px-5 pb-4">
          {lazy && !children ? (
            <div className="py-6 flex items-center justify-center text-[12px] text-slate-400">
              <I.Loader size={14} className="animate-spin mr-2" /> Загрузка истории…
            </div>
          ) : children}
        </div>
      )}
    </div>
  );
};

const NodeSidePanel = ({ editing = false }) => (
  <Card className="overflow-hidden" style={{ width: 400 }}>
    {/* header */}
    <div className="bg-gradient-to-b from-emerald-50/70 to-white p-5 border-b border-slate-200 relative">
      <div className="absolute top-3 right-3">
        <IconButton icon="X" label="Закрыть" size="sm" />
      </div>
      <div className="flex items-center gap-2">
        <div className="h-8 w-8 rounded-md bg-emerald-100 text-emerald-700 grid place-items-center"><I.Megaphone size={16} /></div>
        <div className="flex flex-col">
          <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">CLAIM · тезис</span>
          <span className="text-[13px] font-mono text-slate-400">node_8f3a2c1d</span>
        </div>
      </div>
      <div className="mt-3 flex items-center gap-2">
        <StatusBadge status="STANDING" size="lg" />
        <Badge tone="slate" size="md" icon="Hash">мавлид</Badge>
        <Badge tone="slate" size="md" icon="Hash">фикх</Badge>
      </div>
    </div>

    {/* content */}
    <NodePanelSection icon="MessageSquareQuote" title="Содержание" defaultOpen>
      {!editing ? (
        <div>
          <p className="text-[14px] leading-relaxed text-slate-800 text-pretty">
            Чтение жизнеописания Пророка ﷺ (сиры) в день, традиционно ассоциируемый с его рождением, без сопутствующих нововведений в обрядовой практике, является дозволенным действием.
          </p>
          <Button variant="ghost" size="sm" icon="Edit" className="mt-3 -ml-2">Редактировать</Button>
        </div>
      ) : (
        <div>
          <Textarea rows={5} defaultValue="Чтение жизнеописания Пророка ﷺ (сиры) в день, традиционно ассоциируемый с его рождением, без сопутствующих нововведений в обрядовой практике, является дозволенным действием." />
          <div className="mt-2 flex items-center gap-2 justify-end">
            <Button variant="ghost" size="sm">Отмена</Button>
            <Button variant="primary" size="sm" icon="Save">Сохранить</Button>
          </div>
        </div>
      )}
    </NodePanelSection>

    <NodePanelSection icon="Info" title="Метаданные">
      <dl className="grid grid-cols-[100px_1fr] gap-y-2 gap-x-3 text-[12px]">
        <dt className="text-slate-500">ID</dt>
        <dd className="font-mono text-slate-700 truncate">node_8f3a2c1d-b…2e</dd>
        <dt className="text-slate-500">Создан</dt>
        <dd className="text-slate-700">12 апр 2026, 14:22 · <span className="text-slate-500">Анас И.</span></dd>
        <dt className="text-slate-500">Обновлён</dt>
        <dd className="text-slate-700">2 мая 2026, 09:14 · <span className="text-slate-500">М. Тарасов</span></dd>
        <dt className="text-slate-500">Версия</dt>
        <dd className="text-slate-700 font-mono">v.4</dd>
        <dt className="text-slate-500">Связей</dt>
        <dd className="text-slate-700"><span className="font-mono font-semibold">3</span> входящих · <span className="font-mono font-semibold">2</span> исходящих</dd>
      </dl>
    </NodePanelSection>

    <NodePanelSection icon="Quote" title="Источники" count="2" defaultOpen>
      <div className="space-y-2">
        {[
          { kind: "хадис", title: "Сахих Муслим, №1162", quote: "В этот день я был рождён, и в этот день мне было ниспослано откровение." },
          { kind: "историч.", title: "Ибн Касир, «Аль-Бидая ва-н-нихая», т.13", quote: "Историк не порицает практику ранних мавлидов в Ирбиле в эпоху Музаффара…" },
        ].map((s, i) => (
          <div key={i} className="rounded-md border border-slate-200 bg-slate-50/60 p-3">
            <div className="flex items-center justify-between mb-1">
              <span className="text-[11px] font-mono font-semibold text-slate-500 uppercase">{s.kind}</span>
              <I.Link size={12} className="text-slate-400" />
            </div>
            <div className="text-[12px] font-semibold text-slate-800">{s.title}</div>
            <div className="mt-1 text-[12px] italic text-slate-600 border-l-2 border-slate-300 pl-2 leading-relaxed">«{s.quote}»</div>
          </div>
        ))}
        <Button variant="ghost" size="sm" icon="Plus" full>Привязать источник</Button>
      </div>
    </NodePanelSection>

    <NodePanelSection icon="Users" title="Авторитеты" count="3">
      <div className="space-y-1.5">
        {[
          { name: "Ибн Хаджар аль-Аскаляни", stance: "Holds", years: "1372–1449", color: "emerald" },
          { name: "Ас-Суюты", stance: "Holds", years: "1445–1505", color: "emerald" },
          { name: "Ибн Таймия", stance: "Opposes", years: "1263–1328", color: "rose" },
        ].map((a) => (
          <div key={a.name} className="flex items-center gap-2 py-1.5">
            <Avatar name={a.name} color={a.color} size="sm" />
            <div className="flex-1 min-w-0">
              <div className="text-[12px] font-medium text-slate-800 truncate">{a.name}</div>
              <div className="text-[11px] text-slate-500 font-mono">{a.years}</div>
            </div>
            <Badge tone={a.stance === "Holds" ? "emerald" : a.stance === "Opposes" ? "red" : "slate"} size="sm">
              {a.stance === "Holds" ? "Поддерживает" : a.stance === "Opposes" ? "Возражает" : "Нейтрально"}
            </Badge>
          </div>
        ))}
      </div>
    </NodePanelSection>

    <NodePanelSection icon="History" title="История изменений" count="4">
      <div className="space-y-2">
        <div className="rounded-md border border-slate-200 bg-white overflow-hidden">
          <div className="px-3 py-1.5 bg-slate-50 border-b border-slate-200 flex items-center justify-between text-[11px]">
            <span className="font-mono text-slate-500">v.3 → v.4</span>
            <span className="text-slate-500">2 мая, 09:14 · М. Тарасов</span>
          </div>
          <div className="text-[12px] font-mono divide-y divide-slate-100">
            <div className="px-3 py-1.5 bg-red-50/40 text-red-800">- ...является сомнительной практикой...</div>
            <div className="px-3 py-1.5 bg-emerald-50/40 text-emerald-800">+ ...является дозволенным действием.</div>
          </div>
        </div>
        <button className="w-full text-[12px] text-indigo-700 font-medium hover:underline py-2">Показать все 4 ревизии</button>
      </div>
    </NodePanelSection>
  </Card>
);

// === Side panel: EDGE details ===

const EdgeSidePanel = () => (
  <Card className="overflow-hidden" style={{ width: 400 }}>
    <div className="bg-gradient-to-b from-red-50/70 to-white p-5 border-b border-slate-200 relative">
      <div className="absolute top-3 right-3"><IconButton icon="X" label="Закрыть" size="sm" /></div>
      <div className="flex items-center gap-2">
        <div className="h-8 w-8 rounded-md bg-red-100 text-red-700 grid place-items-center"><I.Slash size={16} /></div>
        <div className="flex flex-col">
          <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">EDGE · аннулирует</span>
          <span className="text-[13px] font-mono text-slate-400">edge_2c4e9a</span>
        </div>
      </div>
      <Badge tone="red" size="lg" className="mt-3">INVALIDATES · kill-switch</Badge>
    </div>

    <NodePanelSection icon="Network" title="Связь" defaultOpen>
      <div className="flex items-center gap-2">
        <div className="flex-1 rounded-md border-l-4 border-emerald-500 border border-slate-200 px-2.5 py-2 bg-emerald-50/40">
          <div className="text-[10px] font-semibold uppercase tracking-wider text-emerald-700">CLAIM</div>
          <div className="text-[12px] font-medium text-slate-800 leading-snug line-clamp-2">Допустимо чтение сиры в этот день</div>
        </div>
        <div className="flex flex-col items-center text-red-700">
          <span className="text-[9px] font-mono font-bold tracking-wider uppercase">аннулирует</span>
          <svg width="40" height="14"><line x1="2" y1="7" x2="32" y2="7" stroke="#b91c1c" strokeWidth="2.5" strokeDasharray="5 4" /><polygon points="32,2 38,7 32,12" fill="#b91c1c" /></svg>
        </div>
        <div className="flex-1 rounded-md border-l-4 border-red-500 border border-slate-200 px-2.5 py-2 bg-red-50/40">
          <div className="text-[10px] font-semibold uppercase tracking-wider text-red-700">ARGUMENT</div>
          <div className="text-[12px] font-medium text-slate-800 leading-snug line-clamp-2">Это новшество, не известное саляфам</div>
        </div>
      </div>
    </NodePanelSection>

    <NodePanelSection icon="Layers" title="Тип связи" defaultOpen>
      <div className="space-y-1.5">
        {Object.values(EDGE_TYPE).map((t) => {
          const selected = t.key === "INVALIDATES";
          const disabled = t.key === "RESPONDS_TO";
          const Icon = I[t.icon];
          return (
            <label key={t.key} className={cx(
              "flex items-start gap-3 rounded-md border p-2.5 transition-colors cursor-pointer",
              selected ? "border-indigo-400 bg-indigo-50/40 ring-1 ring-indigo-300" : "border-slate-200 hover:bg-slate-50",
              disabled && "opacity-40 cursor-not-allowed"
            )}>
              <input type="radio" name="edge-type" defaultChecked={selected} disabled={disabled} className="mt-1 accent-indigo-600" />
              <div className="flex-1">
                <div className="flex items-center gap-1.5">
                  <Icon size={13} style={{ color: t.color }} />
                  <span className="text-[12px] font-semibold text-slate-800">{t.label}</span>
                  {selected && <Badge tone="indigo" size="sm">выбрано</Badge>}
                  {disabled && <Badge tone="slate" size="sm">ADR-010</Badge>}
                </div>
                <p className="text-[11px] text-slate-500 mt-0.5 leading-relaxed">{t.description}</p>
              </div>
              <svg width="34" height="14" className="mt-1 shrink-0">
                <line x1="2" y1="7" x2="26" y2="7" stroke={t.color} strokeWidth={t.width} strokeOpacity={t.opacity || 1} strokeDasharray={t.style === "dashed" ? "4 3" : undefined} />
                <polygon points="26,3 32,7 26,11" fill={t.color} opacity={t.opacity || 1} />
              </svg>
            </label>
          );
        })}
      </div>
    </NodePanelSection>

    <NodePanelSection icon="Quote" title="Обоснование">
      <Textarea rows={3} defaultValue="Уточняющий тезис о допустимости узкой формы (только сиры) делает общий аргумент о бид'а методологически несостоятельным — он бьёт по практике, которая уже выведена из-под него." />
    </NodePanelSection>

    <div className="px-5 py-4 border-t border-slate-200 flex items-center justify-between">
      <Button variant="danger-ghost" size="sm" icon="Trash">Удалить связь</Button>
      <Button variant="primary" size="sm" icon="Save">Сохранить</Button>
    </div>
  </Card>
);

// === Modals ===

const AddNodeModal = () => (
  <div className="relative w-[560px] rounded-lg bg-white shadow-2xl border border-slate-200 overflow-hidden">
    <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between">
      <div>
        <h3 className="text-[16px] font-semibold text-slate-900">Добавить узел</h3>
        <p className="text-[12px] text-slate-500 mt-0.5">Создаст новый узел в текущей теме. Можно сразу связать с существующим.</p>
      </div>
      <IconButton icon="X" label="Закрыть" size="sm" />
    </div>
    <div className="p-6 space-y-5">
      <div>
        <label className="text-[12px] font-medium text-slate-700">Тип узла</label>
        <div className="mt-2 grid grid-cols-4 gap-2">
          {Object.values(NODE_TYPE).map((t, i) => {
            const Icon = I[t.icon];
            const selected = t.key === "ARGUMENT";
            return (
              <label key={t.key} className={cx(
                "rounded-md border p-3 flex flex-col gap-1.5 cursor-pointer transition-colors",
                selected ? "border-indigo-500 bg-indigo-50/60 ring-1 ring-indigo-400" : "border-slate-300 hover:bg-slate-50"
              )}>
                <div className="flex items-center justify-between">
                  <div className={cx("h-7 w-7 rounded grid place-items-center", t.chipBg, t.chipText)}>
                    <Icon size={15} />
                  </div>
                  <input type="radio" name="node-type" defaultChecked={selected} className="accent-indigo-600" />
                </div>
                <div className="text-[12px] font-semibold text-slate-900">{t.label}</div>
                <div className="text-[10px] text-slate-500 leading-relaxed line-clamp-2">{t.description}</div>
              </label>
            );
          })}
        </div>
      </div>
      <Textarea
        label="Содержание"
        hint="2–4 предложения. Можно отредактировать позже."
        rows={4}
        defaultValue="Поскольку Пророк ﷺ не запретил выделение определённого дня для богослужения (постом по понедельникам), отсутствие порицания со стороны сподвижников образует молчаливое согласие."
      />
      <div className="grid grid-cols-2 gap-3">
        <Input label="Привязать к узлу" suffix="↓" defaultValue="CLAIM · Мавлид является дозволенной…" />
        <div>
          <label className="text-[12px] font-medium text-slate-700">Связь</label>
          <div className="mt-1.5 h-9 rounded-md border border-slate-300 px-3 flex items-center justify-between text-[13px] text-slate-700">
            <span className="inline-flex items-center gap-1.5"><I.PlusCircle size={14} className="text-emerald-600" />поддерживает</span>
            <I.ChevronDown size={14} className="text-slate-400" />
          </div>
        </div>
      </div>
    </div>
    <div className="px-6 py-4 bg-slate-50 border-t border-slate-200 flex items-center justify-between">
      <span className="text-[11px] text-slate-500 inline-flex items-center gap-1"><Kbd>⌘</Kbd><Kbd>↵</Kbd> создать</span>
      <div className="flex items-center gap-2">
        <Button variant="ghost">Отмена</Button>
        <Button variant="primary" icon="Plus">Создать узел</Button>
      </div>
    </div>
  </div>
);

const NodePicker = ({ label, value, status, type }) => {
  const t = NODE_TYPE[type];
  const s = STATUS[status];
  const Icon = I[t.icon];
  return (
    <div>
      <label className="text-[12px] font-medium text-slate-700">{label}</label>
      <div className="mt-1.5 h-11 rounded-md border border-slate-300 bg-white px-3 flex items-center gap-2 text-[13px]">
        <Icon size={15} className={t.chipText} />
        <span className={cx("h-2 w-2 rounded-full", s.bar)} />
        <span className="text-[10px] font-mono font-semibold text-slate-500 uppercase">{t.label}</span>
        <span className="text-slate-800 truncate flex-1">{value}</span>
        <I.ChevronsUpDown size={14} className="text-slate-400" />
      </div>
    </div>
  );
};

const AddEdgeModal = () => (
  <div className="relative w-[600px] rounded-lg bg-white shadow-2xl border border-slate-200 overflow-hidden">
    <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between">
      <div>
        <h3 className="text-[16px] font-semibold text-slate-900">Создать связь</h3>
        <p className="text-[12px] text-slate-500 mt-0.5">Направленная: от узла-источника к узлу-цели.</p>
      </div>
      <IconButton icon="X" label="Закрыть" size="sm" />
    </div>
    <div className="p-6 space-y-5">
      <div className="grid grid-cols-[1fr_auto_1fr] gap-3 items-end">
        <NodePicker label="Откуда" value="Пророк ﷺ постился по понедельникам" status="STANDING" type="ARGUMENT" />
        <div className="pb-2.5 text-slate-400"><I.ArrowRight size={20} /></div>
        <NodePicker label="Куда" value="Мавлид является дозволенной практикой" status="DISPUTED" type="CLAIM" />
      </div>

      <div>
        <label className="text-[12px] font-medium text-slate-700">Тип связи</label>
        <div className="mt-2 grid grid-cols-5 gap-2">
          {Object.values(EDGE_TYPE).map((t) => {
            const Icon = I[t.icon];
            const selected = t.key === "SUPPORTS";
            const disabled = t.key === "RESPONDS_TO" || t.key === "INVALIDATES";
            return (
              <Tooltip key={t.key} label={disabled ? "Недопустимо для пары ARGUMENT → CLAIM (ADR-010)" : t.description}>
                <label className={cx(
                  "rounded-md border p-2 flex flex-col gap-1.5 transition-colors w-full",
                  selected ? "border-indigo-500 bg-indigo-50/60 ring-1 ring-indigo-400" : "border-slate-300 hover:bg-slate-50",
                  disabled && "opacity-40 cursor-not-allowed pointer-events-none",
                )}>
                  <div className="flex items-center justify-between">
                    <Icon size={14} style={{ color: t.color }} />
                    <input type="radio" disabled={disabled} defaultChecked={selected} name="edge-modal-type" className="accent-indigo-600" />
                  </div>
                  <div className="text-[11px] font-semibold text-slate-900 leading-tight">{t.label}</div>
                  <svg width="100%" height="8">
                    <line x1="2" y1="4" x2="100%" y2="4" stroke={t.color} strokeWidth={t.width} strokeDasharray={t.style === "dashed" ? "4 3" : undefined} />
                  </svg>
                </label>
              </Tooltip>
            );
          })}
        </div>
      </div>

      <Textarea
        label="Обоснование"
        hint="Опционально. Зачем эта связь нужна — поможет другим читателям."
        rows={3}
        placeholder="Например: «Пророк выделял понедельник как день своего рождения, постясь в этот день. Это создаёт прецедент для выделения дня…»"
      />
    </div>
    <div className="px-6 py-4 bg-slate-50 border-t border-slate-200 flex items-center justify-between">
      <div className="text-[11px] text-red-600 inline-flex items-center gap-1.5">
        <I.AlertCircle size={12} /> Связи RESPONDS_TO и INVALIDATES недопустимы для пары ARGUMENT → CLAIM
      </div>
      <div className="flex items-center gap-2">
        <Button variant="ghost">Отмена</Button>
        <Button variant="primary" icon="Link">Создать связь</Button>
      </div>
    </div>
  </div>
);

// === Context menu ===

const ContextMenu = ({ items, title }) => (
  <div className="w-[260px] rounded-lg bg-white shadow-2xl border border-slate-200 overflow-hidden py-1">
    {title && (
      <div className="px-3 py-1.5 text-[10px] font-semibold uppercase tracking-wider text-slate-500 border-b border-slate-100 mb-1">{title}</div>
    )}
    {items.map((it, i) => {
      if (it === "divider") return <div key={i} className="my-1 h-px bg-slate-100" />;
      const Icon = it.icon ? I[it.icon] : null;
      return (
        <button
          key={i}
          className={cx(
            "w-full px-3 py-1.5 flex items-center gap-2.5 text-[13px] text-left transition-colors",
            it.danger ? "text-red-700 hover:bg-red-50" : "text-slate-800 hover:bg-slate-100",
          )}
        >
          {Icon && <Icon size={14} className={it.danger ? "text-red-500" : "text-slate-500"} />}
          <span className="flex-1">{it.label}</span>
          {it.kbd && <span className="text-[10px] font-mono text-slate-400">{it.kbd}</span>}
          {it.submenu && <I.ChevronRight size={13} className="text-slate-400" />}
        </button>
      );
    })}
  </div>
);

// === Toasts ===

const Toast = ({ tone, icon, title, body, action }) => {
  const Icon = I[icon];
  const tones = {
    success: { bg: "bg-emerald-50", border: "border-emerald-200", text: "text-emerald-900", iconText: "text-emerald-600" },
    info:    { bg: "bg-sky-50",     border: "border-sky-200",     text: "text-sky-900",     iconText: "text-sky-600" },
    warning: { bg: "bg-amber-50",   border: "border-amber-200",   text: "text-amber-900",   iconText: "text-amber-600" },
    error:   { bg: "bg-red-50",     border: "border-red-200",     text: "text-red-900",     iconText: "text-red-600" },
  };
  const t = tones[tone];
  return (
    <div className={cx("flex items-start gap-3 rounded-md border bg-white shadow-md w-[360px] p-3", t.border)}>
      <div className={cx("h-7 w-7 rounded-md grid place-items-center shrink-0", t.bg, t.iconText)}><Icon size={16} /></div>
      <div className="flex-1 min-w-0">
        <div className={cx("text-[13px] font-semibold", t.text)}>{title}</div>
        <div className="text-[12px] text-slate-600 leading-relaxed mt-0.5">{body}</div>
        {action && <Button variant="link" size="xs" className="mt-1.5 -ml-1">{action}</Button>}
      </div>
      <button className="text-slate-400 hover:text-slate-700 transition-colors -mt-0.5"><I.X size={14} /></button>
    </div>
  );
};

window.TopicsScreen = TopicsScreen;
window.CreateTopicScreen = CreateTopicScreen;
window.GraphScreen = GraphScreen;
window.NodeSidePanel = NodeSidePanel;
window.EdgeSidePanel = EdgeSidePanel;
window.AddNodeModal = AddNodeModal;
window.AddEdgeModal = AddEdgeModal;
window.ContextMenu = ContextMenu;
window.Toast = Toast;
