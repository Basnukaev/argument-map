// Components Reference — comprehensive catalog of all primitives for Claude Code.
// Organized by category with code snippets, variants, props.

const { useState: useRefState } = React;

// === Helpers ===============================================================

const CodeBlock = ({ children }) => (
  <pre className="bg-slate-900 text-slate-100 text-[12px] font-mono leading-relaxed rounded-md p-3 overflow-x-auto">
    <code>{children}</code>
  </pre>
);

const Row = ({ children, className = "" }) => (
  <div className={cx("flex flex-wrap items-center gap-3", className)}>{children}</div>
);

const Demo = ({ title, code, children, span = 1 }) => (
  <div className={cx("rounded-lg border border-slate-200 bg-white", span === 2 && "col-span-2")}>
    <div className="px-4 py-2.5 border-b border-slate-100 flex items-center justify-between rounded-t-lg">
      <span className="text-[12px] font-semibold text-slate-700">{title}</span>
      <span className="text-[10px] font-mono text-slate-400">demo</span>
    </div>
    <div className="p-5 bg-slate-50/40 min-h-[80px] flex flex-wrap items-start gap-3">
      {children}
    </div>
    {code && (
      <div className="border-t border-slate-100 rounded-b-lg overflow-hidden">
        <CodeBlock>{code}</CodeBlock>
      </div>
    )}
  </div>
);

const PropTable = ({ rows }) => (
  <div className="rounded-md border border-slate-200 bg-white overflow-hidden">
    <table className="w-full text-[12px]">
      <thead className="bg-slate-50">
        <tr className="text-left text-slate-600">
          <th className="px-3 py-2 font-semibold">Prop</th>
          <th className="px-3 py-2 font-semibold">Type</th>
          <th className="px-3 py-2 font-semibold">Default</th>
          <th className="px-3 py-2 font-semibold">Note</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-slate-100">
        {rows.map((r, i) => (
          <tr key={i} className="text-slate-700">
            <td className="px-3 py-2 font-mono text-slate-900">{r[0]}</td>
            <td className="px-3 py-2 font-mono text-indigo-700">{r[1]}</td>
            <td className="px-3 py-2 font-mono text-slate-500">{r[2]}</td>
            <td className="px-3 py-2 text-slate-600">{r[3]}</td>
          </tr>
        ))}
      </tbody>
    </table>
  </div>
);

const Block = ({ title, kicker, children, id }) => (
  <section id={id} className="mb-16">
    {kicker && <div className="text-[11px] font-mono font-semibold tracking-wider uppercase text-indigo-600 mb-1.5">{kicker}</div>}
    <h2 className="text-[26px] font-bold tracking-tight text-slate-900 mb-1">{title}</h2>
    <div className="h-px bg-slate-200 mb-7" />
    {children}
  </section>
);

const SubBlock = ({ title, hint, children }) => (
  <div className="mb-9">
    <div className="flex items-baseline justify-between gap-6 mb-3">
      <h3 className="text-[14px] font-semibold text-slate-900">{title}</h3>
      {hint && <span className="text-[12px] text-slate-500">{hint}</span>}
    </div>
    {children}
  </div>
);

// === HERO ==================================================================

const RefHero = () => (
  <div className="bg-white border-b border-slate-200">
    <div className="max-w-[1280px] mx-auto px-8 pt-12 pb-10">
      <div className="flex items-center gap-3 mb-7">
        <div className="h-8 w-8 rounded-md bg-indigo-600 grid place-items-center text-white"><I.Network size={16} /></div>
        <span className="text-[14px] font-bold tracking-tight">Argument Map</span>
        <span className="text-[11px] font-mono text-slate-500">/ Components Reference</span>
        <span className="ml-auto text-[11px] font-mono text-slate-400 uppercase tracking-wider">for Claude Code · v 0.1</span>
      </div>
      <h1 className="text-[40px] font-bold leading-[1.1] tracking-tight text-slate-900 max-w-3xl">
        Полный справочник UI-компонентов
      </h1>
      <p className="mt-3 text-[15px] text-slate-600 max-w-2xl leading-relaxed">
        Атомарные primitives дизайн-системы Argument Map: кнопки, формы, селекты, бейджи, оверлеи. Каждый компонент с вариантами, состояниями и кодом-сниппетом, готовым к копированию.
      </p>
      <div className="mt-7 flex flex-wrap gap-2 text-[11px] font-mono">
        {[
          ["React 18", "bg-indigo-100 text-indigo-700"],
          ["Tailwind 3", "bg-sky-100 text-sky-700"],
          ["Inter", "bg-slate-100 text-slate-700"],
          ["lucide-style SVG", "bg-slate-100 text-slate-700"],
          ["Light theme", "bg-amber-100 text-amber-800"],
        ].map(([t, c]) => <span key={t} className={cx("px-2 py-0.5 rounded", c)}>{t}</span>)}
      </div>
    </div>
  </div>
);

// === TOC ===================================================================

const TOC = () => {
  const items = [
    ["foundation", "Основа", ["colors", "Палитра"], ["typography", "Типографика"], ["radius-shadow", "Скругления, тени"]],
    ["actions", "Действия", ["button", "Button"], ["icon-button", "IconButton"], ["split-button", "SplitButton"], ["kbd", "Kbd"]],
    ["forms", "Формы", ["input", "Input"], ["textarea", "Textarea"], ["select", "Select"], ["combobox", "ComboBox"]],
    ["display", "Дисплей", ["badge", "Badge"], ["statusbadge", "StatusBadge"], ["typechip", "TypeChip"], ["avatar", "Avatar"], ["card", "Card"]],
    ["overlay", "Оверлеи", ["dropdown", "Dropdown"], ["menu", "Menu"], ["tooltip", "Tooltip"]],
    ["domain", "Доменные", ["status", "STATUS tokens"], ["node-type", "NODE_TYPE tokens"], ["edge-type", "EDGE_TYPE tokens"]],
  ];
  return (
    <nav className="sticky top-0 z-30 bg-white/90 backdrop-blur border-b border-slate-200">
      <div className="max-w-[1280px] mx-auto px-8 h-11 flex items-center gap-5 overflow-x-auto">
        {items.map((group) => (
          <div key={group[0]} className="flex items-center gap-1.5 whitespace-nowrap">
            <span className="text-[10px] font-mono uppercase tracking-wider text-slate-400">{group[1]}</span>
            {group.slice(2).map(([id, label]) => (
              <a key={id} href={`#${id}`} className="text-[11.5px] text-slate-600 hover:text-indigo-700 px-1.5 py-1 rounded hover:bg-slate-100">{label}</a>
            ))}
          </div>
        ))}
      </div>
    </nav>
  );
};

// === FOUNDATION ============================================================

const ColorsSection = () => (
  <Block id="colors" kicker="01 — foundation" title="Палитра">
    <SubBlock title="Brand & neutrals">
      <div className="grid grid-cols-6 gap-3">
        {[
          ["indigo-600", "#4f46e5", "brand"],
          ["slate-900", "#0f172a", "heading"],
          ["slate-700", "#334155", "body"],
          ["slate-500", "#64748b", "muted"],
          ["slate-200", "#e2e8f0", "border"],
          ["slate-50",  "#f8fafc", "canvas"],
        ].map(([n, h, role]) => (
          <div key={n} className="flex flex-col gap-1.5">
            <div className="h-14 rounded-md border border-slate-200" style={{ background: h }} />
            <div className="flex items-center justify-between text-[11px]">
              <span className="font-medium text-slate-800">{n}</span>
              <span className="font-mono text-slate-500">{h}</span>
            </div>
            <span className="font-mono text-[10px] text-slate-400">{role}</span>
          </div>
        ))}
      </div>
    </SubBlock>
    <SubBlock title="Status palette" hint="Главная визуальная фича: статус узла видим с одного взгляда.">
      <div className="grid grid-cols-4 gap-3">
        {[
          ["emerald-500", "#10b981", "STANDING · Устоявшийся"],
          ["amber-500",   "#f59e0b", "DISPUTED · Спорный"],
          ["red-500",     "#ef4444", "REFUTED · Опровергнут"],
          ["slate-400",   "#94a3b8", "UNVERIFIED · Не оценён"],
        ].map(([n, h, role]) => (
          <div key={n} className="flex items-center gap-3 rounded-md border border-slate-200 bg-white p-3">
            <div className="h-10 w-10 rounded" style={{ background: h }} />
            <div className="flex-1">
              <div className="text-[12px] font-semibold text-slate-900">{role}</div>
              <div className="font-mono text-[10.5px] text-slate-500">{n} · {h}</div>
            </div>
          </div>
        ))}
      </div>
    </SubBlock>
    <SubBlock title="Edge palette" hint="Те же оттенки, что и статусы — отношения наследуют тон, чтобы граф читался цельно.">
      <div className="grid grid-cols-5 gap-3">
        {[
          ["emerald-500", "#10b981", "SUPPORTS"],
          ["red-500",     "#ef4444", "REFUTES"],
          ["red-700",     "#b91c1c", "INVALIDATES"],
          ["blue-500",    "#3b82f6", "QUALIFIES"],
          ["slate-400",   "#94a3b8", "RESPONDS_TO"],
        ].map(([n, h, role]) => (
          <div key={n} className="flex flex-col gap-1.5">
            <div className="h-10 rounded" style={{ background: h }} />
            <div className="text-[11px] font-semibold text-slate-800">{role}</div>
            <div className="font-mono text-[10px] text-slate-500">{h}</div>
          </div>
        ))}
      </div>
    </SubBlock>
  </Block>
);

const TypographySection = () => (
  <Block id="typography" kicker="01 — foundation" title="Типографика">
    <div className="grid grid-cols-2 gap-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 divide-y divide-slate-100">
        <div className="pb-4">
          <div className="text-[32px] font-bold tracking-tight text-slate-900">Heading 1</div>
          <div className="font-mono text-[10px] text-slate-400 mt-1">32 / 700 · -0.02em · Inter</div>
        </div>
        <div className="py-4">
          <div className="text-[24px] font-semibold text-slate-900">Heading 2</div>
          <div className="font-mono text-[10px] text-slate-400 mt-1">24 / 600 · sections</div>
        </div>
        <div className="py-4">
          <div className="text-[18px] font-semibold text-slate-900">Heading 3</div>
          <div className="font-mono text-[10px] text-slate-400 mt-1">18 / 600 · subsections</div>
        </div>
        <div className="py-4">
          <div className="text-[14px] text-slate-700 leading-relaxed">Body — основной текст карточек, описаний, метаданных.</div>
          <div className="font-mono text-[10px] text-slate-400 mt-1">14 / 400 · leading 1.5</div>
        </div>
        <div className="py-4">
          <div className="text-[12px] font-medium text-slate-500">Small / metadata · 12 / 500</div>
          <div className="font-mono text-[10px] text-slate-400 mt-1">timestamps, counters, hints</div>
        </div>
        <div className="pt-4">
          <div className="font-mono text-[12px] font-medium text-slate-700">JetBrains Mono · IDs, code, версии</div>
          <div className="font-mono text-[10px] text-slate-400 mt-1">node_8f3a2c1d-b…2e</div>
        </div>
      </div>
      <div>
        <CodeBlock>{`text-[32px] font-bold tracking-tight text-slate-900
text-[24px] font-semibold text-slate-900
text-[18px] font-semibold text-slate-900
text-[14px] text-slate-700 leading-relaxed
text-[12px] font-medium text-slate-500
font-mono text-[12px] text-slate-700`}</CodeBlock>
        <div className="mt-4 rounded-lg border border-slate-200 bg-white p-5">
          <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-2">Шрифты</div>
          <ul className="space-y-2 text-[13px]">
            <li><span className="font-mono text-[11px] text-slate-500 mr-2">font-sans</span> Inter — 400, 500, 600, 700, 800</li>
            <li><span className="font-mono text-[11px] text-slate-500 mr-2">font-mono</span> JetBrains Mono — 400, 500, 600</li>
            <li><span className="font-mono text-[11px] text-slate-500 mr-2">font-amiri</span> Amiri — арабский (исторический)</li>
            <li><span className="font-mono text-[11px] text-slate-500 mr-2">font-naskh</span> Noto Naskh Arabic — академический</li>
          </ul>
        </div>
      </div>
    </div>
  </Block>
);

const RadiusShadowSection = () => (
  <Block id="radius-shadow" kicker="01 — foundation" title="Скругления, тени, отступы">
    <div className="grid grid-cols-3 gap-5">
      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-3">Скругления</div>
        <div className="flex items-end gap-3">
          {[{n:"rounded",px:"4"},{n:"rounded-md",px:"6"},{n:"rounded-lg",px:"8"},{n:"rounded-xl",px:"12"}].map((r) => (
            <div key={r.n} className="flex flex-col items-center gap-1.5">
              <div className="h-14 w-14 bg-indigo-50 border border-indigo-200" style={{ borderRadius: r.px + "px" }} />
              <span className="font-mono text-[10px] text-slate-600">{r.n}</span>
              <span className="font-mono text-[9px] text-slate-400">{r.px}px</span>
            </div>
          ))}
        </div>
      </div>
      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-3">Тени</div>
        <div className="flex items-end gap-3">
          <div className="h-14 w-14 rounded-md bg-white shadow-[0_1px_2px_rgba(15,23,42,0.04)] border border-slate-100 grid place-items-center font-mono text-[10px] text-slate-600">sm</div>
          <div className="h-14 w-14 rounded-md bg-white shadow-md grid place-items-center font-mono text-[10px] text-slate-600">md</div>
          <div className="h-14 w-14 rounded-md bg-white shadow-lg grid place-items-center font-mono text-[10px] text-slate-600">lg</div>
          <div className="h-14 w-14 rounded-md bg-white shadow-xl grid place-items-center font-mono text-[10px] text-slate-600">xl</div>
        </div>
      </div>
      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-3">Spacing · 4px scale</div>
        <div className="flex items-end gap-3">
          {[2,4,6,8,12].map((s) => (
            <div key={s} className="flex flex-col items-center gap-1.5">
              <div className="bg-indigo-200" style={{ height: s * 4, width: s * 4 }} />
              <span className="font-mono text-[10px] text-slate-500">{s} · {s*4}px</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  </Block>
);

// === ACTIONS ===============================================================

const ButtonSection = () => (
  <Block id="button" kicker="02 — actions" title="Button">
    <SubBlock title="Варианты">
      <Demo
        title="Все 6 вариантов · size md"
        code={`<Button variant="primary">Сохранить</Button>
<Button variant="secondary">Отмена</Button>
<Button variant="ghost">Очистить</Button>
<Button variant="danger" icon="Trash">Удалить</Button>
<Button variant="danger-ghost" icon="Trash">Удалить (ghost)</Button>
<Button variant="link">Открыть в новой вкладке</Button>`}
      >
        <Button variant="primary">Сохранить</Button>
        <Button variant="secondary">Отмена</Button>
        <Button variant="ghost">Очистить</Button>
        <Button variant="danger" icon="Trash">Удалить</Button>
        <Button variant="danger-ghost" icon="Trash">Удалить (ghost)</Button>
        <Button variant="link">Открыть в новой вкладке</Button>
      </Demo>
    </SubBlock>
    <SubBlock title="Размеры">
      <Demo
        title="sm · md · lg"
        code={`<Button size="sm" variant="primary">Маленькая</Button>
<Button size="md" variant="primary">Средняя</Button>
<Button size="lg" variant="primary">Большая</Button>`}
      >
        <Button size="sm" variant="primary">Маленькая</Button>
        <Button size="md" variant="primary">Средняя</Button>
        <Button size="lg" variant="primary">Большая</Button>
      </Demo>
    </SubBlock>
    <SubBlock title="С иконкой">
      <Demo
        title="icon, iconRight, only-icon"
        code={`<Button icon="Plus" variant="primary">Создать</Button>
<Button iconRight="ArrowRight" variant="secondary">Дальше</Button>
<Button icon="Download" variant="secondary" />`}
      >
        <Button icon="Plus" variant="primary">Создать</Button>
        <Button iconRight="ArrowRight" variant="secondary">Дальше</Button>
        <Button icon="Download" variant="secondary" />
      </Demo>
    </SubBlock>
    <SubBlock title="Состояния">
      <Demo
        title="loading, disabled"
        code={`<Button disabled variant="primary">Недоступно</Button>
<Button disabled variant="secondary">Недоступно</Button>
<Button disabled variant="danger">Недоступно</Button>`}
      >
        <Button disabled variant="primary">Недоступно</Button>
        <Button disabled variant="secondary">Недоступно</Button>
        <Button disabled variant="danger">Недоступно</Button>
      </Demo>
    </SubBlock>
    <SubBlock title="API">
      <PropTable rows={[
        ["variant", '"primary"|"secondary"|"ghost"|"danger"|"danger-ghost"|"link"', '"primary"', "Внешний вид"],
        ["size", '"xs"|"sm"|"md"|"lg"', '"md"', "Высота: 28/32/36/44px"],
        ["icon", "string", "—", "Имя иконки слева"],
        ["iconRight", "string", "—", "Имя иконки справа"],
        ["full", "boolean", "false", "w-full — на всю ширину"],
        ["disabled", "boolean", "false", "Стандартный disabled"],
        ["children", "ReactNode", "—", "Если не передать — кнопка иконкой"],
      ]} />
    </SubBlock>
  </Block>
);

const IconButtonSection = () => (
  <Block id="icon-button" kicker="02 — actions" title="IconButton">
    <SubBlock title="Базовый">
      <Demo
        title="Размеры · sm md lg"
        code={`<IconButton icon="Settings" label="Настройки" size="sm" />
<IconButton icon="Settings" label="Настройки" size="md" />
<IconButton icon="Settings" label="Настройки" size="lg" />`}
      >
        <IconButton icon="Settings" label="Настройки" size="sm" />
        <IconButton icon="Settings" label="Настройки" size="md" />
        <IconButton icon="Settings" label="Настройки" size="lg" />
      </Demo>
    </SubBlock>
    <SubBlock title="Варианты">
      <Demo
        title="ghost (default) · outline · solid"
        code={`<IconButton icon="Star" label="В избранное" variant="ghost" />
<IconButton icon="Star" label="В избранное" variant="solid" />`}
      >
        <IconButton icon="Star" label="В избранное" variant="ghost" />
        <IconButton icon="Star" label="В избранное" variant="solid" />
      </Demo>
    </SubBlock>
    <SubBlock title="Active">
      <Demo
        title="active=true — для toggle-кнопок"
        code={`<IconButton icon="Sidebar" label="Скрыть панель" active />`}
      >
        <IconButton icon="Sidebar" label="Свернуть" active />
        <IconButton icon="Sidebar" label="Развернуть" />
      </Demo>
    </SubBlock>
  </Block>
);

const SplitButtonSection = () => (
  <Block id="split-button" kicker="02 — actions" title="SplitButton">
    <Demo
      title="Основное действие + дропдаун альтернатив"
      code={`<SplitButton
  icon="Plus"
  variant="primary"
  onPrimary={() => addNode()}
  menuItems={[
    { label: "Тезис",        icon: "Megaphone",         onClick: () => add("CLAIM") },
    { label: "Аргумент",     icon: "MessageSquareQuote", onClick: () => add("ARGUMENT") },
    { label: "Свидетельство", icon: "FileText",          onClick: () => add("EVIDENCE") },
    { separator: true },
    { label: "Импорт из JSON", icon: "Upload",           shortcut: "⌘ I", onClick: () => {} },
  ]}
>
  Добавить узел
</SplitButton>`}
    >
      <SplitButton
        icon="Plus"
        variant="primary"
        onPrimary={() => {}}
        menuItems={[
          { label_group: "Типы узлов" },
          { label: "Тезис", icon: "Megaphone", onClick: () => {} },
          { label: "Аргумент", icon: "MessageSquareQuote", onClick: () => {} },
          { label: "Свидетельство", icon: "FileText", onClick: () => {} },
          { label: "Вопрос", icon: "CircleHelp", onClick: () => {} },
          { separator: true },
          { label: "Импорт из JSON", icon: "Upload", shortcut: "⌘ I", onClick: () => {} },
        ]}
      >
        Добавить узел
      </SplitButton>
      <SplitButton
        icon="Save"
        variant="secondary"
        onPrimary={() => {}}
        menuItems={[
          { label: "Сохранить как...", icon: "Save", shortcut: "⇧⌘S", onClick: () => {} },
          { label: "Экспорт PDF", icon: "Download", onClick: () => {} },
        ]}
      >
        Сохранить
      </SplitButton>
    </Demo>
  </Block>
);

const KbdSection = () => (
  <Block id="kbd" kicker="02 — actions" title="Kbd">
    <Demo
      title="Клавиатурные сочетания"
      code={`<Kbd>⌘</Kbd> <Kbd>K</Kbd>
<Kbd>Shift</Kbd> <Kbd>Enter</Kbd>
<Kbd>Esc</Kbd>`}
    >
      <div className="flex items-center gap-1.5"><Kbd>⌘</Kbd><Kbd>K</Kbd></div>
      <div className="flex items-center gap-1.5"><Kbd>Shift</Kbd><Kbd>Enter</Kbd></div>
      <Kbd>Esc</Kbd>
      <Kbd>Tab</Kbd>
    </Demo>
  </Block>
);

// === FORMS =================================================================

const InputSection = () => {
  const [v, setV] = useRefState("Мавлид");
  return (
    <Block id="input" kicker="03 — forms" title="Input">
      <SubBlock title="Размеры">
        <Demo title="Базовый · с иконкой · с suffix" code={`<Input placeholder="Базовый" />
<Input icon="Search" placeholder="С иконкой" />
<Input placeholder="С суффиксом" suffix="шт." />`}>
          <div className="w-44"><Input placeholder="Базовый" /></div>
          <div className="w-52"><Input icon="Search" placeholder="С иконкой" /></div>
          <div className="w-52"><Input placeholder="С суффиксом" suffix="шт." /></div>
        </Demo>
      </SubBlock>
      <SubBlock title="С label, hint, иконкой">
        <Demo
          title="label · hint · icon"
          code={`<Input
  label="Название темы"
  hint="Кратко и по сути"
  icon="MessageSquareQuote"
  value={v}
  onChange={(e) => setV(e.target.value)}
/>`}
        >
          <div className="w-72">
            <Input label="Название темы" hint="Кратко и по сути" icon="MessageSquareQuote" value={v} onChange={(e) => setV(e.target.value)} />
          </div>
        </Demo>
      </SubBlock>
      <SubBlock title="Состояния">
        <Demo
          title="error · disabled · readonly"
          code={`<Input error="Уже существует" defaultValue="Дубликат" />
<Input disabled placeholder="Недоступно" />
<Input readOnly defaultValue="Только для чтения" />`}
        >
          <div className="w-60"><Input error="Уже существует" defaultValue="Дубликат" /></div>
          <div className="w-60"><Input disabled placeholder="Недоступно" /></div>
          <div className="w-60"><Input readOnly defaultValue="Только для чтения" /></div>
        </Demo>
      </SubBlock>
    </Block>
  );
};

const TextareaSection = () => (
  <Block id="textarea" kicker="03 — forms" title="Textarea">
    <Demo
      title="С подсчётом символов"
      code={`<Textarea
  label="Описание темы"
  placeholder="..."
  rows={4}
  maxLength={500}
/>`}
    >
      <div className="w-[480px]">
        <Textarea label="Описание темы" placeholder="Опишите контекст темы и основные стороны спора..." rows={4} maxLength={500} />
      </div>
    </Demo>
  </Block>
);

const SelectSection = () => {
  const [v1, setV1] = useRefState("STANDING");
  const [v2, setV2] = useRefState(null);
  return (
    <Block id="select" kicker="03 — forms" title="Select">
      <SubBlock title="Базовый">
        <Demo
          title="С иконкой в опциях"
          code={`<Select
  label="Статус узла"
  value={v}
  onChange={setV}
  options={[
    { value: "STANDING",   label: "Устоявшийся", icon: "Check" },
    { value: "DISPUTED",   label: "Спорный",     icon: "AlertTriangle" },
    { value: "REFUTED",    label: "Опровергнут", icon: "XCircle" },
    { value: "UNVERIFIED", label: "Не оценён",   icon: "Circle" },
  ]}
/>`}
        >
          <Select
            label="Статус узла"
            value={v1}
            onChange={setV1}
            options={[
              { value: "STANDING",   label: "Устоявшийся", icon: "Check",         description: "Поддержан, не опровергнут" },
              { value: "DISPUTED",   label: "Спорный",     icon: "AlertTriangle", description: "Есть противоречивые доводы" },
              { value: "REFUTED",    label: "Опровергнут", icon: "XCircle",       description: "Опровержение принято" },
              { value: "UNVERIFIED", label: "Не оценён",   icon: "Circle",        description: "Нет источников" },
            ]}
          />
        </Demo>
      </SubBlock>
      <SubBlock title="Размеры">
        <Demo
          title="sm · md · lg"
          code={`<Select size="sm" options={...} />
<Select size="md" options={...} />
<Select size="lg" options={...} />`}
        >
          <Select size="sm" width={140} value={v2} onChange={setV2} options={[
            { value: "ru", label: "Русский" }, { value: "en", label: "English" }, { value: "ar", label: "العربية" },
          ]} placeholder="Язык" />
          <Select size="md" width={160} value={v2} onChange={setV2} options={[
            { value: "ru", label: "Русский" }, { value: "en", label: "English" }, { value: "ar", label: "العربية" },
          ]} placeholder="Язык" />
          <Select size="lg" width={180} value={v2} onChange={setV2} options={[
            { value: "ru", label: "Русский" }, { value: "en", label: "English" }, { value: "ar", label: "العربية" },
          ]} placeholder="Язык" />
        </Demo>
      </SubBlock>
      <SubBlock title="Группы и сепараторы">
        <Demo
          title="label_group + separator в options"
          code={`<Select options={[
  { label_group: "Сунниты" },
  { value: "bukhari",  label: "Сахих аль-Бухари" },
  { value: "muslim",   label: "Сахих Муслим" },
  { separator: true },
  { label_group: "Шииты" },
  { value: "kafi",     label: "Аль-Кафи" },
]} />`}
        >
          <Select width={240} placeholder="Сборник хадисов" options={[
            { label_group: "Сунниты" },
            { value: "bukhari", label: "Сахих аль-Бухари", icon: "Library" },
            { value: "muslim",  label: "Сахих Муслим", icon: "Library" },
            { value: "tirmidhi", label: "Сунан ат-Тирмизи", icon: "Library" },
            { separator: true },
            { label_group: "Шииты" },
            { value: "kafi", label: "Аль-Кафи", icon: "Library" },
          ]} />
        </Demo>
      </SubBlock>
      <SubBlock title="Ошибка">
        <Demo
          title="error prop"
          code={`<Select error="Обязательное поле" placeholder="Выберите статус" options={...} />`}
        >
          <Select width={240} error="Обязательное поле" placeholder="Выберите статус" options={[
            { value: "a", label: "Опция A" }, { value: "b", label: "Опция B" },
          ]} />
        </Demo>
      </SubBlock>
    </Block>
  );
};

const ComboBoxSection = () => {
  const [v, setV] = useRefState(null);
  const longList = [
    { value: "bukhari", label: "Сахих аль-Бухари", icon: "Library" },
    { value: "muslim", label: "Сахих Муслим", icon: "Library" },
    { value: "abu-dawud", label: "Сунан Абу Дауда", icon: "Library" },
    { value: "tirmidhi", label: "Сунан ат-Тирмизи", icon: "Library" },
    { value: "nasai", label: "Сунан ан-Насаи", icon: "Library" },
    { value: "ibn-majah", label: "Сунан Ибн Маджа", icon: "Library" },
    { value: "malik", label: "Муватта Малика", icon: "Library" },
    { value: "ahmad", label: "Муснад Ахмада", icon: "Library" },
    { value: "darimi", label: "Сунан ад-Дарими", icon: "Library" },
  ];
  return (
    <Block id="combobox" kicker="03 — forms" title="ComboBox">
      <Demo
        title="Select с поиском — для длинных списков"
        code={`<ComboBox
  label="Источник"
  value={v}
  onChange={setV}
  options={[/* 9 hadith collections */]}
  searchPlaceholder="Найти сборник..."
/>`}
      >
        <ComboBox label="Источник" value={v} onChange={setV} options={longList} searchPlaceholder="Найти сборник..." placeholder="Выберите сборник" />
      </Demo>
    </Block>
  );
};

// === DISPLAY ===============================================================

const BadgeSection = () => (
  <Block id="badge" kicker="04 — display" title="Badge">
    <SubBlock title="Цвета">
      <Demo
        title="6 базовых цветов"
        code={`<Badge tone="slate">Default</Badge>
<Badge tone="indigo">Brand</Badge>
<Badge tone="emerald">Success</Badge>
<Badge tone="amber">Warning</Badge>
<Badge tone="red">Danger</Badge>
<Badge tone="sky">Info</Badge>`}
      >
        <Badge tone="slate">Default</Badge>
        <Badge tone="indigo">Brand</Badge>
        <Badge tone="emerald">Success</Badge>
        <Badge tone="amber">Warning</Badge>
        <Badge tone="red">Danger</Badge>
        <Badge tone="sky">Info</Badge>
      </Demo>
    </SubBlock>
    <SubBlock title="С иконкой">
      <Demo
        title="icon · sm/md/lg"
        code={`<Badge tone="emerald" icon="Check">Принят</Badge>
<Badge tone="indigo" icon="GitBranch">Версия 4</Badge>
<Badge tone="amber" icon="AlertTriangle" size="lg">Внимание</Badge>`}
      >
        <Badge tone="emerald" icon="Check">Принят</Badge>
        <Badge tone="indigo" icon="GitBranch">Версия 4</Badge>
        <Badge tone="amber" icon="AlertTriangle" size="lg">Внимание</Badge>
        <Badge tone="red" icon="XCircle">Опровергнут</Badge>
      </Demo>
    </SubBlock>
  </Block>
);

const StatusBadgeSection = () => (
  <Block id="statusbadge" kicker="04 — display" title="StatusBadge">
    <Demo
      title="4 статуса узла — STATUS-токены"
      code={`<StatusBadge status="STANDING" />
<StatusBadge status="DISPUTED" />
<StatusBadge status="REFUTED" />
<StatusBadge status="UNVERIFIED" />`}
    >
      <StatusBadge status="STANDING" />
      <StatusBadge status="DISPUTED" />
      <StatusBadge status="REFUTED" />
      <StatusBadge status="UNVERIFIED" />
    </Demo>
    <SubBlock title="Размеры">
      <Demo title="sm · md" code={`<StatusBadge status="STANDING" size="sm" />
<StatusBadge status="STANDING" size="md" />`}>
        <StatusBadge status="STANDING" size="sm" />
        <StatusBadge status="STANDING" size="md" />
      </Demo>
    </SubBlock>
  </Block>
);

const TypeChipSection = () => (
  <Block id="typechip" kicker="04 — display" title="TypeChip">
    <Demo
      title="4 типа узла — NODE_TYPE токены"
      code={`<TypeChip type="QUESTION" />
<TypeChip type="CLAIM" />
<TypeChip type="ARGUMENT" />
<TypeChip type="EVIDENCE" />`}
    >
      <TypeChip type="QUESTION" />
      <TypeChip type="CLAIM" />
      <TypeChip type="ARGUMENT" />
      <TypeChip type="EVIDENCE" />
    </Demo>
  </Block>
);

const AvatarSection = () => (
  <Block id="avatar" kicker="04 — display" title="Avatar">
    <SubBlock title="Цвета">
      <Demo
        title="7 цветов"
        code={`<Avatar name="Ибн Таймия" color="indigo" />
<Avatar name="Ан-Навави" color="emerald" />
<Avatar name="Аль-Албани" color="amber" />`}
      >
        <Avatar name="Ибн Таймия" color="indigo" />
        <Avatar name="Ан-Навави" color="emerald" />
        <Avatar name="Аль-Албани" color="amber" />
        <Avatar name="Ибн Хаджар" color="rose" />
        <Avatar name="Ибн Касир" color="sky" />
        <Avatar name="Аль-Газали" color="violet" />
        <Avatar name="Ибн Кудама" color="teal" />
      </Demo>
    </SubBlock>
    <SubBlock title="Размеры">
      <Demo title="sm · md · lg" code={`<Avatar name="A B" size="sm" />
<Avatar name="A B" size="md" />
<Avatar name="A B" size="lg" />`}>
        <Avatar name="A B" size="sm" />
        <Avatar name="A B" size="md" />
        <Avatar name="A B" size="lg" />
      </Demo>
    </SubBlock>
  </Block>
);

const CardSection = () => (
  <Block id="card" kicker="04 — display" title="Card">
    <Demo
      title="Базовый контейнер"
      code={`<Card className="p-5">
  <h3 className="text-[15px] font-semibold">Заголовок карточки</h3>
  <p className="text-[13px] text-slate-600 mt-2">Описание</p>
</Card>`}
    >
      <Card className="p-5 w-64">
        <h3 className="text-[15px] font-semibold text-slate-900">Заголовок карточки</h3>
        <p className="text-[13px] text-slate-600 mt-2">Стандартная карточка: white фон, slate-200 рамка, мягкая тень, скругление xl.</p>
      </Card>
      <Card className="p-5 w-64">
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-[15px] font-semibold text-slate-900">С хедером</h3>
          <Badge tone="emerald" icon="Check">Активна</Badge>
        </div>
        <p className="text-[13px] text-slate-600">Композиция с другими примитивами.</p>
      </Card>
    </Demo>
  </Block>
);

// === OVERLAYS ==============================================================

const DropdownSection = () => (
  <Block id="dropdown" kicker="05 — overlays" title="Dropdown">
    <SubBlock title="Generic с render-prop триггером">
      <Demo
        title="Любой триггер — кнопка, ссылка, аватар"
        code={`<Dropdown
  align="left"
  trigger={(open, toggle) => (
    <Button variant="secondary" iconRight={open ? "ChevronUp" : "ChevronDown"} onClick={toggle}>
      Фильтр
    </Button>
  )}
>
  <DropdownLabel>Сортировка</DropdownLabel>
  <DropdownItem icon="ArrowDown" selected>По дате (новые)</DropdownItem>
  <DropdownItem icon="ArrowUp">По дате (старые)</DropdownItem>
  <DropdownSeparator />
  <DropdownItem icon="Star">По популярности</DropdownItem>
</Dropdown>`}
      >
        <Dropdown
          trigger={(open, toggle) => (
            <Button variant="secondary" iconRight={open ? "ChevronUp" : "ChevronDown"} onClick={toggle}>
              Фильтр
            </Button>
          )}
        >
          <DropdownLabel>Сортировка</DropdownLabel>
          <DropdownItem icon="ArrowDown" selected>По дате (новые)</DropdownItem>
          <DropdownItem icon="ArrowUp">По дате (старые)</DropdownItem>
          <DropdownSeparator />
          <DropdownItem icon="Star">По популярности</DropdownItem>
          <DropdownItem icon="MessageSquare">По обсуждаемости</DropdownItem>
        </Dropdown>
        <Dropdown
          align="right"
          trigger={(open, toggle) => (
            <button onClick={toggle} className="inline-flex items-center gap-2 px-2.5 h-8 rounded-md hover:bg-slate-100">
              <Avatar name="Анна Петрова" color="indigo" size="sm" />
              <span className="text-[13px] font-medium">Анна Петрова</span>
              <I.ChevronDown size={14} className="text-slate-400" />
            </button>
          )}
        >
          <div className="px-3 py-2.5 border-b border-slate-100">
            <div className="text-[13px] font-semibold text-slate-900">Анна Петрова</div>
            <div className="text-[11px] text-slate-500">anna@example.com</div>
          </div>
          <DropdownItem icon="User">Профиль</DropdownItem>
          <DropdownItem icon="Settings" shortcut="⌘ ,">Настройки</DropdownItem>
          <DropdownSeparator />
          <DropdownItem icon="LogOut" danger>Выйти</DropdownItem>
        </Dropdown>
      </Demo>
    </SubBlock>
    <SubBlock title="API">
      <PropTable rows={[
        ["trigger", "(open, toggle) => ReactNode", "—", "Render-prop, обязателен"],
        ["align", '"left"|"right"', '"left"', "Выравнивание панели"],
        ["width", "number", "220", "Минимальная ширина панели"],
        ["open", "boolean", "—", "Controlled mode"],
        ["onOpenChange", "(open) => void", "—", "Колбэк изменения"],
        ["defaultOpen", "boolean", "false", "Uncontrolled начальное"],
      ]} />
    </SubBlock>
    <SubBlock title="DropdownItem · все варианты">
      <Demo
        title="icon · description · shortcut · selected · danger · disabled"
        code={`<DropdownItem icon="Edit">Редактировать</DropdownItem>
<DropdownItem icon="Star" shortcut="⌘ B">В избранное</DropdownItem>
<DropdownItem icon="FileText" description="Версия от 15 мая">Открыть архив</DropdownItem>
<DropdownItem icon="Check" selected>Текущий выбор</DropdownItem>
<DropdownItem icon="Trash" danger>Удалить</DropdownItem>
<DropdownItem icon="Lock" disabled>Заблокировано</DropdownItem>`}
      >
        <div className="w-64 rounded-md border border-slate-200 bg-white shadow-sm py-1">
          <DropdownItem icon="Edit">Редактировать</DropdownItem>
          <DropdownItem icon="Star" shortcut="⌘ B">В избранное</DropdownItem>
          <DropdownItem icon="FileText" description="Версия от 15 мая">Открыть архив</DropdownItem>
          <DropdownItem icon="Check" selected>Текущий выбор</DropdownItem>
          <DropdownSeparator />
          <DropdownItem icon="Trash" danger>Удалить</DropdownItem>
        </div>
      </Demo>
    </SubBlock>
  </Block>
);

const MenuSection = () => (
  <Block id="menu" kicker="05 — overlays" title="Menu">
    <Demo
      title="IconButton-триггер для контекстного меню"
      code={`<Menu icon="MoreHorizontal" label="Действия" align="right">
  <DropdownItem icon="Edit" shortcut="E">Редактировать</DropdownItem>
  <DropdownItem icon="Copy" shortcut="⌘ D">Дублировать</DropdownItem>
  <DropdownItem icon="Share2">Поделиться</DropdownItem>
  <DropdownSeparator />
  <DropdownItem icon="Trash" danger>Удалить</DropdownItem>
</Menu>`}
    >
      <Menu icon="MoreHorizontal" label="Действия" align="right">
        <DropdownItem icon="Edit" shortcut="E">Редактировать</DropdownItem>
        <DropdownItem icon="Copy" shortcut="⌘ D">Дублировать</DropdownItem>
        <DropdownItem icon="Share2">Поделиться</DropdownItem>
        <DropdownSeparator />
        <DropdownItem icon="Trash" danger>Удалить</DropdownItem>
      </Menu>
      <Menu icon="MoreVertical" label="Меню" align="left">
        <DropdownItem icon="Eye">Просмотр</DropdownItem>
        <DropdownItem icon="Download">Экспорт</DropdownItem>
      </Menu>
      <Menu icon="Filter" label="Фильтры" align="right" size="sm">
        <DropdownLabel>По статусу</DropdownLabel>
        <DropdownItem icon="CheckCircle" selected>Принятые</DropdownItem>
        <DropdownItem icon="AlertCircle">Оспариваемые</DropdownItem>
        <DropdownItem icon="XCircle">Опровергнутые</DropdownItem>
      </Menu>
    </Demo>
  </Block>
);

const TooltipSection = () => (
  <Block id="tooltip" kicker="05 — overlays" title="Tooltip">
    <Demo
      title="4 стороны — top · right · bottom · left"
      code={`<Tooltip label="Подсказка сверху" side="top">
  <IconButton icon="Info" label="Info" />
</Tooltip>`}
    >
      <Tooltip label="Сверху (по умолчанию)" side="top"><IconButton icon="Info" label="Info top" /></Tooltip>
      <Tooltip label="Справа" side="right"><IconButton icon="Info" label="Info right" /></Tooltip>
      <Tooltip label="Снизу" side="bottom"><IconButton icon="Info" label="Info bottom" /></Tooltip>
      <Tooltip label="Слева" side="left"><IconButton icon="Info" label="Info left" /></Tooltip>
    </Demo>
  </Block>
);

// === DOMAIN TOKENS =========================================================

const StatusTokensSection = () => (
  <Block id="status" kicker="06 — domain" title="STATUS — токены статусов узлов">
    <div className="grid grid-cols-4 gap-3">
      {Object.values(STATUS).map((s) => {
        const Icon = I[s.icon];
        return (
          <div key={s.key} className="rounded-lg border border-slate-200 bg-white overflow-hidden">
            <div className={cx("h-1.5", s.bar)} />
            <div className="p-4">
              <div className="flex items-center gap-2 mb-2">
                <div className={cx("h-8 w-8 rounded grid place-items-center", s.badgeBg, s.text)}><Icon size={16} /></div>
                <div>
                  <div className="text-[13px] font-semibold text-slate-900">{s.label}</div>
                  <div className="font-mono text-[10px] text-slate-500">{s.key}</div>
                </div>
              </div>
              <p className="text-[11px] text-slate-600 leading-relaxed">{s.description}</p>
            </div>
          </div>
        );
      })}
    </div>
    <div className="mt-5">
      <CodeBlock>{`// Доступ к токенам:
STATUS.STANDING.label     // "Принят"
STATUS.STANDING.bar       // "bg-emerald-500" — для левой полоски карточки
STATUS.STANDING.badgeBg   // "bg-emerald-50" — фон бейджа
STATUS.STANDING.text      // "text-emerald-700" — текст
STATUS.STANDING.icon      // "CheckCircle" — имя иконки`}</CodeBlock>
    </div>
  </Block>
);

const NodeTypeTokensSection = () => (
  <Block id="node-type" kicker="06 — domain" title="NODE_TYPE — токены типов узлов">
    <div className="grid grid-cols-4 gap-3">
      {Object.values(NODE_TYPE).map((t) => {
        const Icon = I[t.icon];
        return (
          <div key={t.key} className="rounded-lg border border-slate-200 bg-white p-4">
            <div className="flex items-center gap-2.5 mb-2">
              <div className={cx("h-9 w-9 rounded-md grid place-items-center", t.chipBg, t.chipText)}><Icon size={18} /></div>
              <div>
                <div className="text-[13px] font-bold text-slate-900">{t.label}</div>
                <div className="font-mono text-[10px] text-slate-500">{t.full}</div>
              </div>
            </div>
            <p className="text-[11px] text-slate-600 leading-relaxed">{t.description}</p>
            <div className="mt-2 text-[11px] italic text-slate-500 border-l-2 border-slate-200 pl-2 line-clamp-2">«{t.example}»</div>
          </div>
        );
      })}
    </div>
  </Block>
);

const EdgeTypeTokensSection = () => (
  <Block id="edge-type" kicker="06 — domain" title="EDGE_TYPE — токены типов рёбер">
    <Card className="p-5">
      <div className="grid grid-cols-5 gap-5">
        {Object.values(EDGE_TYPE).map((t) => {
          const Icon = I[t.icon];
          return (
            <div key={t.key} className="flex flex-col gap-2">
              <div className="flex items-center gap-1.5">
                <Icon size={14} style={{ color: t.color }} />
                <span className="text-[12px] font-semibold text-slate-900">{t.label}</span>
              </div>
              <svg width="100%" height="22" viewBox="0 0 200 22" preserveAspectRatio="none">
                <line x1="2" y1="11" x2="182" y2="11" stroke={t.color} strokeWidth={t.width * 1.4} strokeOpacity={t.opacity || 1} strokeDasharray={t.style === "dashed" ? "8 5" : undefined} strokeLinecap="round" />
                <polygon points="182,5 196,11 182,17" fill={t.color} opacity={t.opacity || 1} />
              </svg>
              <div className="font-mono text-[10px] text-slate-500">{t.key}</div>
              <p className="text-[11px] text-slate-600 leading-relaxed">{t.description}</p>
            </div>
          );
        })}
      </div>
    </Card>
  </Block>
);

// === ICONS =================================================================

const IconsSection = () => {
  const iconNames = Object.keys(I).sort();
  const [q, setQ] = useRefState("");
  const filtered = iconNames.filter((n) => !q || n.toLowerCase().includes(q.toLowerCase()));
  return (
    <Block id="icons" kicker="07 — assets" title={`Icons · ${iconNames.length} штук`}>
      <div className="mb-5 max-w-sm">
        <Input value={q} onChange={(e) => setQ(e.target.value)} icon="Search" placeholder="Поиск иконки..." />
      </div>
      <div className="grid grid-cols-8 gap-2">
        {filtered.map((name) => {
          const Icon = I[name];
          return (
            <div key={name} className="flex flex-col items-center gap-1.5 p-3 rounded-md border border-slate-200 bg-white hover:border-indigo-300 hover:bg-indigo-50/30 cursor-default group">
              <Icon size={18} className="text-slate-700 group-hover:text-indigo-700" />
              <span className="font-mono text-[10px] text-slate-500 truncate w-full text-center">{name}</span>
            </div>
          );
        })}
      </div>
      <div className="mt-4 text-[12px] text-slate-500">
        Использование: <code className="font-mono text-slate-700">{`<I.${filtered[0] || "Plus"} size={16} />`}</code> или через имя <code className="font-mono text-slate-700">{`<Button icon="${filtered[0] || "Plus"}">...</Button>`}</code>.
      </div>
    </Block>
  );
};

// === APP ===================================================================

const RefApp = () => (
  <div className="min-h-screen bg-slate-50">
    <RefHero />
    <TOC />
    <main className="max-w-[1280px] mx-auto px-8 py-12">
      {/* Foundation */}
      <ColorsSection />
      <TypographySection />
      <RadiusShadowSection />

      {/* Actions */}
      <ButtonSection />
      <IconButtonSection />
      <SplitButtonSection />
      <KbdSection />

      {/* Forms */}
      <InputSection />
      <TextareaSection />
      <SelectSection />
      <ComboBoxSection />

      {/* Display */}
      <BadgeSection />
      <StatusBadgeSection />
      <TypeChipSection />
      <AvatarSection />
      <CardSection />

      {/* Overlays */}
      <DropdownSection />
      <MenuSection />
      <TooltipSection />

      {/* Domain */}
      <StatusTokensSection />
      <NodeTypeTokensSection />
      <EdgeTypeTokensSection />

      {/* Assets */}
      <IconsSection />
    </main>

    <footer className="py-10 px-8 border-t border-slate-200 bg-white">
      <div className="max-w-[1280px] mx-auto flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-6 w-6 rounded bg-indigo-600 grid place-items-center text-white"><I.Network size={13} /></div>
          <span className="text-[13px] font-bold text-slate-900">Argument Map · Components Reference</span>
        </div>
        <div className="text-[11px] font-mono text-slate-500">
          <a href="Argument%20Map.html" className="hover:text-indigo-700">← вернуться к полному showcase</a>
        </div>
      </div>
    </footer>
  </div>
);

const refRoot = ReactDOM.createRoot(document.getElementById("root"));
refRoot.render(<RefApp />);
