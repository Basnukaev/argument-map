// Top-level App composing the showcase document.

const { useState } = React;

const Hero = () => (
  <div className="relative bg-gradient-to-b from-white to-slate-50 border-b border-slate-200 overflow-hidden">
    <div className="relative max-w-[1380px] mx-auto px-10 pt-14 pb-12">
      <div className="flex items-center gap-3 mb-8">
        <div className="h-8 w-8 rounded-md bg-indigo-600 grid place-items-center text-white"><I.Network size={16} /></div>
        <span className="text-[15px] font-bold tracking-tight">Argument Map</span>
        <span className="text-[11px] font-mono text-slate-500">v 0.1 · design showcase</span>
        <span className="ml-auto text-[11px] font-mono text-slate-500 tracking-wider uppercase">Light · Desktop 1280+ · RU/AR</span>
      </div>

      <div className="grid grid-cols-12 gap-10 items-end">
        <div className="col-span-8">
          <h1 className="text-[48px] font-bold leading-[1.05] tracking-tight text-slate-900 text-balance">
            Структурированный разбор дискуссий — <span className="text-indigo-600">через граф аргументации</span>.
          </h1>
          <p className="mt-5 text-[15px] leading-relaxed text-slate-600 text-pretty max-w-2xl">
            Десктопный инструмент для исследователей, студентов, богословов и аналитиков. С первоклассной поддержкой арабских первоисточников — Коран, девять канонических сборников хадисов и классические труды библиотеки Шамиля. Цвет статуса узла виден с одного взгляда: где консенсус, где спор, где опровергнутое.
          </p>
          <div className="mt-6 flex items-center gap-3">
            <Button variant="primary" size="lg" icon="Plus">Создать тему</Button>
            <Button variant="secondary" size="lg" icon="BookOpen">Документация API</Button>
            {window.LanguageSwitcher && <window.LanguageSwitcher current="RU" />}
          </div>
        </div>
        <div className="col-span-4">
          <dl className="grid grid-cols-2 gap-x-5 gap-y-3">
            {[
              ["Разделов showcase", "27"],
              ["Узлов в демо-графе", "10"],
              ["Типов рёбер", "5"],
              ["Языков UI", "RU · EN · AR"],
              ["Тема", "только светлая"],
              ["Стек", "React · Tailwind"],
            ].map(([k, v]) => (
              <div key={k} className="flex items-baseline justify-between gap-3 border-b border-slate-200 pb-1.5">
                <dt className="text-[12px] text-slate-500">{k}</dt>
                <dd className="text-[12px] font-mono font-medium text-slate-900 tabular">{v}</dd>
              </div>
            ))}
          </dl>
        </div>
      </div>

      {/* Preview card — browser window */}
      <div className="mt-12 rounded-xl border border-slate-200 bg-white overflow-hidden shadow-xl">
        <div className="h-10 px-4 border-b border-slate-200 bg-slate-50 flex items-center gap-2">
          <span className="h-2.5 w-2.5 rounded-full bg-red-400" />
          <span className="h-2.5 w-2.5 rounded-full bg-amber-400" />
          <span className="h-2.5 w-2.5 rounded-full bg-emerald-400" />
          <span className="ml-3 text-[11px] font-mono text-slate-500">argumentmap.app/topics/mawlid-permissibility</span>
          <span className="ml-auto text-[11px] font-mono text-slate-500 uppercase tracking-wider">demo · мавлид</span>
        </div>
        <div className="h-[600px] overflow-hidden">
          <Graph height={600} />
        </div>
      </div>
    </div>
  </div>
);

// === Design tokens section ===

const Swatch = ({ name, hex, varname, light }) => (
  <div className="flex flex-col gap-1.5">
    <div className={cx("h-16 rounded-md border border-slate-200", light && "ring-1 ring-inset ring-slate-200/40")} style={{ background: hex }} />
    <div className="flex items-center justify-between text-[11px]">
      <span className="font-medium text-slate-800">{name}</span>
      <span className="font-mono text-slate-500">{hex}</span>
    </div>
    {varname && <span className="font-mono text-[10px] text-slate-400">{varname}</span>}
  </div>
);

const TokensSection = () => (
  <Section title="Design tokens" kicker="02 — основа" hint="Палитры, типографика, скругления, тени. Левая граница, статус, тип — атомы продукта.">
    <SubSection title="Палитра статусов" hint="Главная визуальная фича. Один взгляд → понятно состояние узла.">
      <div className="grid grid-cols-4 gap-4">
        {Object.values(STATUS).map((s) => {
          const Icon = I[s.icon];
          return (
            <Card key={s.key} className="overflow-hidden">
              <div className={cx("h-1.5", s.bar)} />
              <div className="p-4">
                <div className="flex items-center gap-2">
                  <div className={cx("h-8 w-8 rounded grid place-items-center", s.badgeBg, s.text)}><Icon size={16} /></div>
                  <div>
                    <div className="text-[13px] font-semibold text-slate-900">{s.label}</div>
                    <div className="font-mono text-[10px] text-slate-500">{s.key}</div>
                  </div>
                </div>
                <p className="mt-2 text-[11px] text-slate-600 leading-relaxed">{s.description}</p>
                <div className="mt-3 flex items-center gap-1.5">
                  <StatusBadge status={s.key} size="sm" />
                </div>
              </div>
            </Card>
          );
        })}
      </div>
    </SubSection>

    <SubSection title="Палитра типов узлов">
      <div className="grid grid-cols-4 gap-4">
        {Object.values(NODE_TYPE).map((t) => {
          const Icon = I[t.icon];
          return (
            <Card key={t.key} className="p-4">
              <div className="flex items-center gap-2.5">
                <div className={cx("h-9 w-9 rounded-md grid place-items-center", t.chipBg, t.chipText)}>
                  <Icon size={18} />
                </div>
                <div>
                  <div className="text-[13px] font-bold text-slate-900">{t.label}</div>
                  <div className="font-mono text-[10px] text-slate-500">{t.full}</div>
                </div>
              </div>
              <p className="mt-2.5 text-[11px] text-slate-600 leading-relaxed">{t.description}</p>
              <div className="mt-2 text-[11px] italic text-slate-500 border-l-2 border-slate-200 pl-2 line-clamp-2">«{t.example}»</div>
            </Card>
          );
        })}
      </div>
    </SubSection>

    <SubSection title="Палитра рёбер">
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
    </SubSection>

    <SubSection title="Базовая палитра">
      <div className="grid grid-cols-6 gap-3">
        <Swatch name="indigo-600" hex="#4f46e5" varname="--brand-primary" />
        <Swatch name="slate-900" hex="#0f172a" varname="--text-heading" />
        <Swatch name="slate-700" hex="#334155" varname="--text-body" />
        <Swatch name="slate-500" hex="#64748b" varname="--text-muted" />
        <Swatch name="slate-200" hex="#e2e8f0" varname="--border" light />
        <Swatch name="slate-50"  hex="#f8fafc" varname="--surface-canvas" light />
        <Swatch name="emerald-500" hex="#10b981" varname="--status-standing" />
        <Swatch name="amber-500" hex="#f59e0b" varname="--status-disputed" />
        <Swatch name="red-500" hex="#ef4444" varname="--status-refuted" />
        <Swatch name="red-700" hex="#b91c1c" varname="--edge-invalidates" />
        <Swatch name="blue-500" hex="#3b82f6" varname="--edge-qualifies" />
        <Swatch name="slate-400" hex="#94a3b8" varname="--edge-responds-to" />
      </div>
    </SubSection>

    <div className="grid grid-cols-2 gap-6">
      <SubSection title="Типографика" className="mb-0">
        <Card className="p-6 divide-y divide-slate-100">
          <div className="pb-4">
            <div className="text-[32px] font-bold tracking-tight text-slate-900 leading-tight">Heading 1 — 32 / 700</div>
            <div className="text-[10px] font-mono text-slate-400 mt-1">Inter · tight tracking · headlines</div>
          </div>
          <div className="py-4">
            <div className="text-[24px] font-semibold text-slate-900">Heading 2 — 24 / 600</div>
            <div className="text-[10px] font-mono text-slate-400 mt-1">section titles</div>
          </div>
          <div className="py-4">
            <div className="text-[18px] font-semibold text-slate-900">Heading 3 — 18 / 600</div>
            <div className="text-[10px] font-mono text-slate-400 mt-1">subsection / panel headers</div>
          </div>
          <div className="py-4">
            <div className="text-[14px] text-slate-700 leading-relaxed">Body — 14 / 400 · leading 1.5. Основной текст узлов, описаний, метаданных.</div>
            <div className="text-[10px] font-mono text-slate-400 mt-1">paragraphs, node body</div>
          </div>
          <div className="py-4">
            <div className="text-[12px] font-medium text-slate-500">Small / metadata — 12 / 500</div>
            <div className="text-[10px] font-mono text-slate-400 mt-1">timestamps, counters, hints</div>
          </div>
          <div className="pt-4">
            <div className="text-[12px] font-mono font-medium text-slate-700">JetBrains Mono — 12 / 500 · ID, code, версии</div>
            <div className="text-[10px] font-mono text-slate-400 mt-1">node_8f3a2c1d-b…2e</div>
          </div>
        </Card>
      </SubSection>

      <SubSection title="Скругления, тени, spacing" className="mb-0">
        <Card className="p-6 space-y-5">
          <div>
            <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-2">Скругления</div>
            <div className="flex items-end gap-3">
              {[{name:"rounded-md",px:"6px"},{name:"rounded-lg",px:"8px"},{name:"rounded-xl",px:"12px"}].map((r, i) => (
                <div key={r.name} className="flex flex-col items-center gap-1.5">
                  <div className="h-16 w-16 bg-indigo-50 border border-indigo-200" style={{ borderRadius: r.px }} />
                  <span className="text-[11px] font-mono text-slate-700">{r.name}</span>
                  <span className="text-[10px] text-slate-500">{r.px}</span>
                </div>
              ))}
            </div>
          </div>
          <div>
            <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-2">Тени</div>
            <div className="flex items-end gap-4">
              <div className="h-14 w-24 rounded-md bg-white shadow-[0_1px_2px_rgba(15,23,42,0.04)] border border-slate-100 grid place-items-center text-[11px] font-mono text-slate-600">sm</div>
              <div className="h-14 w-24 rounded-md bg-white shadow-md grid place-items-center text-[11px] font-mono text-slate-600">md</div>
              <div className="h-14 w-24 rounded-md bg-white shadow-xl grid place-items-center text-[11px] font-mono text-slate-600">xl</div>
            </div>
          </div>
          <div>
            <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-2">Spacing scale</div>
            <div className="flex items-end gap-3">
              {[2,4,6,8,12].map((s) => (
                <div key={s} className="flex flex-col items-center gap-1.5">
                  <div className="bg-indigo-200" style={{ height: s * 4 + "px", width: s * 4 + "px" }} />
                  <span className="text-[10px] font-mono text-slate-500">{s} · {s*4}px</span>
                </div>
              ))}
            </div>
          </div>
        </Card>
      </SubSection>
    </div>
  </Section>
);

// === Components library ===

const ComponentsSection = () => (
  <Section title="Library компонентов" kicker="03 — основа" hint="Атомарные UI-блоки. Inter + lucide-style icons. Никаких сторонних UI-китов.">
    <SubSection title="Кнопки">
      <Card className="p-6">
        <div className="grid grid-cols-[120px_1fr] gap-y-4 items-center">
          <span className="text-[11px] font-mono text-slate-500">Primary</span>
          <div className="flex items-center gap-2">
            <Button variant="primary" size="lg" icon="Plus">Создать тему</Button>
            <Button variant="primary" icon="Save">Сохранить</Button>
            <Button variant="primary" size="sm">Action</Button>
            <Button variant="primary" size="sm" disabled>Disabled</Button>
          </div>

          <span className="text-[11px] font-mono text-slate-500">Secondary</span>
          <div className="flex items-center gap-2">
            <Button variant="secondary" size="lg" icon="Eye">Превью</Button>
            <Button variant="secondary" icon="History">Ревизии</Button>
            <Button variant="secondary" size="sm" iconRight="ChevronDown">Сортировка</Button>
            <Button variant="secondary" size="sm" disabled>Disabled</Button>
          </div>

          <span className="text-[11px] font-mono text-slate-500">Ghost</span>
          <div className="flex items-center gap-2">
            <Button variant="ghost" icon="ArrowLeft">Назад</Button>
            <Button variant="ghost" icon="Edit">Редактировать</Button>
            <Button variant="link">Перейти к теме</Button>
          </div>

          <span className="text-[11px] font-mono text-slate-500">Danger</span>
          <div className="flex items-center gap-2">
            <Button variant="danger" icon="Trash">Удалить тему</Button>
            <Button variant="danger-ghost" size="sm" icon="Trash">Удалить узел</Button>
          </div>

          <span className="text-[11px] font-mono text-slate-500">Icon</span>
          <div className="flex items-center gap-2">
            <IconButton icon="Search" label="Поиск" />
            <IconButton icon="Settings" label="Настройки" />
            <IconButton icon="Plus" label="Добавить" active />
            <IconButton icon="Trash" label="Удалить" />
          </div>
        </div>
      </Card>
    </SubSection>

    <SubSection title="Бейджи и статусы">
      <Card className="p-6 space-y-4">
        <div className="flex flex-wrap gap-2 items-center">
          <span className="text-[11px] font-mono text-slate-500 mr-2 w-24">Status:</span>
          {Object.keys(STATUS).map((s) => <StatusBadge key={s} status={s} />)}
        </div>
        <div className="flex flex-wrap gap-2 items-center">
          <span className="text-[11px] font-mono text-slate-500 mr-2 w-24">Type:</span>
          {Object.keys(NODE_TYPE).map((t) => <TypeChip key={t} type={t} />)}
        </div>
        <div className="flex flex-wrap gap-2 items-center">
          <span className="text-[11px] font-mono text-slate-500 mr-2 w-24">Tones:</span>
          {["slate","indigo","emerald","amber","red","blue","violet","sky","teal"].map((tone) => (
            <Badge key={tone} tone={tone}>{tone}</Badge>
          ))}
        </div>
        <div className="flex flex-wrap gap-2 items-center">
          <span className="text-[11px] font-mono text-slate-500 mr-2 w-24">Sizes:</span>
          <Badge tone="indigo" size="sm">small</Badge>
          <Badge tone="indigo" size="md">medium</Badge>
          <Badge tone="indigo" size="lg" icon="Sparkles">large with icon</Badge>
        </div>
        <div className="flex flex-wrap gap-2 items-center">
          <span className="text-[11px] font-mono text-slate-500 mr-2 w-24">Hotkeys:</span>
          <Kbd>⌘</Kbd>+<Kbd>K</Kbd>
          <span className="ml-3" />
          <Kbd>⇧</Kbd>+<Kbd>N</Kbd>
          <span className="ml-3" />
          <Kbd>Esc</Kbd>
          <span className="ml-3" />
          <Kbd>Del</Kbd>
        </div>
      </Card>
    </SubSection>

    <SubSection title="Поля ввода">
      <div className="grid grid-cols-3 gap-4">
        <Card className="p-5"><Input label="Default" placeholder="Введите текст…" /></Card>
        <Card className="p-5"><Input label="С иконкой" icon="Search" placeholder="Поиск по графу…" /></Card>
        <Card className="p-5"><Input label="С ошибкой" defaultValue="bad input" error="Должно быть не короче 8 символов" /></Card>
        <Card className="p-5"><Input label="Disabled" defaultValue="readonly" disabled /></Card>
        <Card className="p-5"><Input label="С suffix" defaultValue="280" suffix="px" /></Card>
        <Card className="p-5"><Input label="С подсказкой" placeholder="Имя темы" hint="От 3 до 120 символов" /></Card>
      </div>
      <div className="mt-4 grid grid-cols-2 gap-4">
        <Card className="p-5">
          <Textarea label="Содержание узла" placeholder="2–4 предложения…" rows={3} />
        </Card>
        <Card className="p-5">
          <Textarea label="Обоснование связи" hint="Опционально. Зачем эта связь нужна?" rows={3} defaultValue="Любовь к Пророку — часть веры. Радость о его рождении — естественная её манифестация." />
        </Card>
      </div>
    </SubSection>

    <SubSection title="NodeSelect — кастомный пикер узла">
      <Card className="p-5 grid grid-cols-2 gap-3">
        <NodePicker label="Откуда" value="Пророк ﷺ постился по понедельникам" status="STANDING" type="ARGUMENT" />
        <NodePicker label="Куда" value="Мавлид является дозволенной практикой" status="DISPUTED" type="CLAIM" />
      </Card>
    </SubSection>

    <SubSection title="Тосты — 4 типа">
      <div className="grid grid-cols-2 gap-4">
        <Toast tone="success" icon="CheckCircle" title="Узел создан" body="ARGUMENT добавлен и связан с CLAIM «Мавлид является дозволенной практикой»." action="Открыть" />
        <Toast tone="info"    icon="Info"        title="Авто-сохранение" body="Изменения графа сохранены 2 секунды назад." />
        <Toast tone="warning" icon="AlertTriangle" title="Несовместимый тип связи" body="RESPONDS_TO допустимо только между QUESTION и ARGUMENT." action="Подробнее (ADR-010)" />
        <Toast tone="error"   icon="XCircle"    title="Не удалось сохранить" body="Сервер вернул 409 Conflict — узел был обновлён в другой вкладке." action="Перезагрузить" />
      </div>
    </SubSection>
  </Section>
);

// === Node card matrix ===

const NodeMatrix = () => {
  const sampleContent = {
    QUESTION: { title: "Можно ли отмечать день рождения Пророка ﷺ?", body: "Уточняющий вопрос: какие именно практики мавлида допустимы, какие порицаемы?" },
    CLAIM: { title: "Мавлид является дозволенной практикой", body: "Главный тезис обсуждения. Подкрепляется фетвами и хадисами." },
    ARGUMENT: { title: "Это выражение любви к Пророку ﷺ", body: "Любовь — часть веры; радость о его рождении — естественная её манифестация." },
    EVIDENCE: { title: "Сахих Муслим, хадис №1162", body: "«В этот день я был рождён, и в этот день мне было ниспослано откровение»." },
  };
  return (
    <Section title="Карточки узлов · 4 типа × 4 статуса" kicker="04 — узлы" hint="Цветная полоса слева — статус. Капсула типа в шапке. Хэндлы — на hover/select.">
      <div className="grid grid-cols-[140px_repeat(4,1fr)] gap-4 items-start">
        <div />
        {Object.values(STATUS).map((s) => (
          <div key={s.key} className="text-center">
            <div className={cx("inline-flex items-center gap-1.5 px-2.5 h-7 rounded-md font-semibold text-[12px]", s.badgeBg, s.text)}>
              {React.createElement(I[s.icon], { size: 13 })}
              {s.label}
            </div>
          </div>
        ))}
        {Object.values(NODE_TYPE).map((t) => (
          <React.Fragment key={t.key}>
            <div className="pt-3">
              <div className="flex items-center gap-2">
                <div className={cx("h-8 w-8 rounded-md grid place-items-center", t.chipBg, t.chipText)}>
                  {React.createElement(I[t.icon], { size: 16 })}
                </div>
                <div>
                  <div className="text-[13px] font-bold text-slate-900">{t.label}</div>
                  <div className="font-mono text-[10px] text-slate-500">{t.full}</div>
                </div>
              </div>
            </div>
            {Object.keys(STATUS).map((s) => (
              <div key={t.key + s} className="flex justify-center">
                <NodeCard type={t.key} status={s} title={sampleContent[t.key].title} body={sampleContent[t.key].body} width={260} />
              </div>
            ))}
          </React.Fragment>
        ))}
      </div>

      <div className="mt-10">
        <h3 className="text-[14px] font-semibold text-slate-900 mb-3">Состояния карточки</h3>
        <Card className="p-8 bg-slate-50">
          <div className="grid grid-cols-4 gap-6 items-start">
            <div className="flex flex-col items-center gap-2">
              <NodeCard type="CLAIM" status="STANDING" title="Default" body="Базовое состояние карточки на холсте." width={240} />
              <span className="text-[11px] font-mono text-slate-500">Default</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <NodeCard type="CLAIM" status="STANDING" title="Hover" body="Подсветка границы и хэндлы видны." width={240} hovered showHandles />
              <span className="text-[11px] font-mono text-slate-500">Hover</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <NodeCard type="CLAIM" status="STANDING" title="Selected" body="Indigo-кольцо вокруг карточки + хэндлы." width={240} selected showHandles />
              <span className="text-[11px] font-mono text-slate-500">Selected</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <NodeCard type="CLAIM" status="STANDING" title="Compact" width={240} compact />
              <span className="text-[11px] font-mono text-slate-500">Compact (зум &lt; 60%)</span>
            </div>
          </div>
        </Card>
      </div>
    </Section>
  );
};

// === Edge variants ===

const EdgeVariantsSection = () => {
  const samples = [
    { from: { type: "ARGUMENT", status: "STANDING", title: "Пророк ﷺ постился по понедельникам" }, to: { type: "CLAIM", status: "DISPUTED", title: "Мавлид является дозволенной практикой" }, type: "SUPPORTS", label: "поддерживает" },
    { from: { type: "ARGUMENT", status: "REFUTED", title: "Это новшество, не известное саляфам" }, to: { type: "CLAIM", status: "DISPUTED", title: "Мавлид является дозволенной практикой" }, type: "REFUTES", label: "опровергает" },
    { from: { type: "CLAIM", status: "STANDING", title: "Допустимо чтение сиры в этот день" }, to: { type: "ARGUMENT", status: "REFUTED", title: "Это новшество, не известное саляфам" }, type: "INVALIDATES", label: "аннулирует" },
    { from: { type: "QUESTION", status: "UNVERIFIED", title: "Какие именно практики имеются в виду?" }, to: { type: "ARGUMENT", status: "REFUTED", title: "Это новшество, не известное саляфам" }, type: "QUALIFIES", label: "уточняет" },
    { from: { type: "CLAIM", status: "DISPUTED", title: "Мавлид является дозволенной практикой" }, to: { type: "QUESTION", status: "UNVERIFIED", title: "Дозволено ли празднование мавлида?" }, type: "RESPONDS_TO", label: "отвечает на" },
  ];
  return (
    <Section title="Типы рёбер · 5 вариантов" kicker="05 — связи" hint="Bezier-кривые с компактным наконечником. Подпись в badge посередине ребра.">
      <div className="space-y-4">
        {samples.map((s, i) => {
          const t = EDGE_TYPE[s.type];
          const Icon = I[t.icon];
          return (
            <Card key={i} className="p-5">
              <div className="flex items-center gap-4">
                <div className="w-[180px] shrink-0">
                  <div className="flex items-center gap-1.5 mb-1">
                    <Icon size={14} style={{ color: t.color }} />
                    <span className="text-[13px] font-semibold text-slate-900">{t.label}</span>
                  </div>
                  <div className="font-mono text-[10px] text-slate-500 mb-2">{t.key}</div>
                  <p className="text-[11px] text-slate-600 leading-relaxed">{t.description}</p>
                </div>
                <NodeCard type={s.from.type} status={s.from.status} title={s.from.title} width={250} compact />
                <div className="flex-1 flex flex-col items-center justify-center min-w-[140px]">
                  <svg width="100%" height="60" viewBox="0 0 240 60" preserveAspectRatio="none">
                    <defs>
                      <marker id={`arrowdemo-${t.key}`} viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
                        <path d="M 0 1 L 9 5 L 0 9 z" fill={t.color} opacity={t.opacity || 1} />
                      </marker>
                    </defs>
                    <path d="M 4 30 C 80 30, 160 30, 230 30" fill="none" stroke={t.color} strokeWidth={t.width * 1.6} strokeOpacity={t.opacity || 1} strokeDasharray={t.style === "dashed" ? "8 5" : undefined} markerEnd={`url(#arrowdemo-${t.key})`} strokeLinecap="round" />
                  </svg>
                  <div className={cx("inline-flex items-center gap-1 rounded-md border px-2 h-6 text-[11px] font-medium bg-white -mt-9 shadow-sm", t.badgeBorder, t.badgeText)}>
                    <Icon size={11} />
                    {s.label}
                  </div>
                </div>
                <NodeCard type={s.to.type} status={s.to.status} title={s.to.title} width={250} compact />
              </div>
            </Card>
          );
        })}
      </div>
    </Section>
  );
};

// === Modals & menus section ===

const ModalsSection = () => (
  <Section title="Модалки и контекстные меню" kicker="11 — overlays" hint="Натуральный <dialog> с backdrop, focus trap, Esc/Cmd+Enter.">
    <SubSection title="AddNode · AddEdge">
      <div className="grid grid-cols-2 gap-6 items-start">
        <div className="checkerboard rounded-lg p-8 flex justify-center"><AddNodeModal /></div>
        <div className="checkerboard rounded-lg p-8 flex justify-center"><AddEdgeModal /></div>
      </div>
    </SubSection>

    <SubSection title="Контекстное меню">
      <div className="grid grid-cols-3 gap-6 items-start">
        <div>
          <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-2">Правый клик по холсту</div>
          <ContextMenu
            title="Холст"
            items={[
              { icon: "Plus", label: "Создать узел здесь", kbd: "N" },
              { icon: "CircleHelp", label: "Создать QUESTION" },
              { icon: "Megaphone", label: "Создать CLAIM" },
              { icon: "MessageSquareQuote", label: "Создать ARGUMENT" },
              { icon: "FileText", label: "Создать EVIDENCE" },
              "divider",
              { icon: "Crosshair", label: "К корневому вопросу", kbd: "⌘0" },
              { icon: "Maximize", label: "Уместить в экран", kbd: "⌘1" },
            ]}
          />
        </div>
        <div>
          <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-2">Правый клик по узлу</div>
          <ContextMenu
            title="CLAIM · Мавлид является дозволенной…"
            items={[
              { icon: "Edit", label: "Редактировать", kbd: "↵" },
              { icon: "Link", label: "Создать связанный…", submenu: true },
              { icon: "Copy", label: "Дублировать", kbd: "⌘D" },
              "divider",
              { icon: "ChevronUp", label: "На передний план" },
              { icon: "ChevronDown", label: "На задний план" },
              { icon: "Pin", label: "Закрепить позицию" },
              "divider",
              { icon: "Trash", label: "Удалить узел", kbd: "Del", danger: true },
            ]}
          />
        </div>
        <div>
          <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-2">Правый клик по ребру</div>
          <ContextMenu
            title="EDGE · аннулирует"
            items={[
              { icon: "Edit", label: "Изменить тип…" },
              { icon: "Quote", label: "Добавить обоснование" },
              { icon: "Refresh", label: "Развернуть направление" },
              "divider",
              { icon: "EyeOff", label: "Скрыть подпись" },
              "divider",
              { icon: "Unlink", label: "Удалить связь", kbd: "Del", danger: true },
            ]}
          />
        </div>
      </div>
    </SubSection>
  </Section>
);

// === States ===

const StatesSection = () => (
  <Section title="Состояния — empty / loading / error" kicker="12 — states" hint="Не забываем про граничные случаи.">
    <div className="grid grid-cols-3 gap-5">
      {/* Empty */}
      <Card className="overflow-hidden">
        <div className="px-4 py-2 border-b border-slate-200 text-[11px] font-mono text-slate-500">EMPTY · /topics/{`{id}`}</div>
        <div className="dot-grid h-[400px] grid place-items-center bg-slate-50/60 relative">
          <div className="text-center max-w-[280px]">
            <div className="mx-auto mb-4 inline-flex">
              <svg width="120" height="80" viewBox="0 0 120 80">
                <line x1="60" y1="22" x2="30" y2="55" stroke="#cbd5e1" strokeWidth="1.5" strokeDasharray="4 3" />
                <line x1="60" y1="22" x2="60" y2="55" stroke="#cbd5e1" strokeWidth="1.5" strokeDasharray="4 3" />
                <line x1="60" y1="22" x2="90" y2="55" stroke="#cbd5e1" strokeWidth="1.5" strokeDasharray="4 3" />
                <rect x="42" y="10" width="36" height="22" rx="6" fill="white" stroke="#a78bfa" strokeWidth="1.5" />
                <rect x="14" y="46" width="32" height="20" rx="6" fill="white" stroke="#cbd5e1" strokeWidth="1.5" strokeDasharray="3 3" />
                <rect x="46" y="46" width="28" height="20" rx="6" fill="white" stroke="#cbd5e1" strokeWidth="1.5" strokeDasharray="3 3" />
                <rect x="76" y="46" width="32" height="20" rx="6" fill="white" stroke="#cbd5e1" strokeWidth="1.5" strokeDasharray="3 3" />
              </svg>
            </div>
            <div className="text-[15px] font-semibold text-slate-900">Граф пуст</div>
            <p className="mt-1 text-[12px] text-slate-500 leading-relaxed">Начните с корневого вопроса. От него вы построите тезисы, аргументы и свидетельства.</p>
            <Button variant="primary" size="sm" icon="Plus" className="mt-4">Добавить первый узел</Button>
          </div>
        </div>
      </Card>

      {/* Loading */}
      <Card className="overflow-hidden">
        <div className="px-4 py-2 border-b border-slate-200 text-[11px] font-mono text-slate-500">LOADING · /topics</div>
        <div className="p-4 space-y-3 h-[400px]">
          {[0,1,2,3].map((i) => (
            <div key={i} className="rounded-lg border border-slate-200 p-3 flex gap-3 animate-pulse">
              <div className="h-16 w-20 rounded bg-slate-200" />
              <div className="flex-1 space-y-2">
                <div className="h-3 rounded bg-slate-200 w-5/6" />
                <div className="h-2.5 rounded bg-slate-100 w-full" />
                <div className="h-2.5 rounded bg-slate-100 w-3/4" />
                <div className="flex items-center gap-2 pt-1.5">
                  <div className="h-5 w-5 rounded-full bg-slate-200" />
                  <div className="h-2.5 rounded bg-slate-100 w-20" />
                </div>
              </div>
            </div>
          ))}
        </div>
      </Card>

      {/* Error */}
      <Card className="overflow-hidden">
        <div className="px-4 py-2 border-b border-slate-200 text-[11px] font-mono text-slate-500">ERROR · Problem Details (RFC 7807)</div>
        <div className="p-4 h-[400px] grid place-items-center">
          <div className="w-full">
            <div className="rounded-lg border border-red-200 bg-red-50/60 p-4">
              <div className="flex items-start gap-3">
                <div className="h-9 w-9 rounded-md bg-red-100 text-red-700 grid place-items-center shrink-0"><I.AlertCircle size={18} /></div>
                <div className="flex-1 min-w-0">
                  <div className="text-[14px] font-semibold text-red-900">Конфликт версий узла</div>
                  <div className="text-[12px] text-red-800 mt-1 leading-relaxed">Узел был обновлён в другой вкладке. Перезагрузите данные или объедините изменения вручную.</div>
                  <div className="mt-3 rounded border border-red-200 bg-white p-2.5 font-mono text-[11px] leading-relaxed text-slate-700">
                    <div><span className="text-red-700">type:</span> https://argumentmap.app/probs/version-conflict</div>
                    <div><span className="text-red-700">status:</span> 409</div>
                    <div><span className="text-red-700">title:</span> Conflict</div>
                    <div><span className="text-red-700">instance:</span> /nodes/8f3a2c1d</div>
                    <div><span className="text-red-700">field:</span> body</div>
                  </div>
                </div>
              </div>
              <div className="mt-3 flex items-center gap-2">
                <Button variant="danger" size="sm" icon="Refresh">Перезагрузить</Button>
                <Button variant="secondary" size="sm">Объединить</Button>
              </div>
            </div>
          </div>
        </div>
      </Card>
    </div>
  </Section>
);

// === Side-panels section ===

const PanelsSection = () => (
  <Section title="Боковые панели · детали узла и ребра" kicker="09 — детали" hint="Открываются справа при выделении одного элемента.">
    <div className="grid grid-cols-2 gap-6 items-start">
      <div>
        <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-2 flex items-center gap-2">
          <span className="font-mono">/topics/{`{id}`}</span> · selected = node_8f3a2c1d
        </div>
        <div className="checkerboard rounded-lg p-6"><NodeSidePanel /></div>
      </div>
      <div>
        <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-2 flex items-center gap-2">
          <span className="font-mono">/topics/{`{id}`}</span> · selected = edge_2c4e9a (edit)
        </div>
        <div className="checkerboard rounded-lg p-6"><EdgeSidePanel /></div>
      </div>
    </div>
  </Section>
);

// === Top-of-page nav ===

const SECTIONS = [
  { id: "refresh-notes", label: "Refresh" },
  { id: "tokens",      label: "Tokens" },
  { id: "components",  label: "Components" },
  { id: "nodes",       label: "Nodes" },
  { id: "bilingual-refresh", label: "Bilingual+" },
  { id: "edges",       label: "Edges" },
  { id: "canvas-refresh", label: "Canvas" },
  { id: "topics",      label: "Topics" },
  { id: "graph",       label: "Graph" },
  { id: "panels",      label: "Panels" },
  { id: "modals",      label: "Modals" },
  { id: "sources",     label: "Sources" },
  { id: "source-detail-refresh", label: "Source+" },
  { id: "library-refresh", label: "Library+" },
  { id: "authorities", label: "Authorities" },
  { id: "rtl",         label: "RTL" },
  { id: "settings-refresh", label: "Settings" },
  { id: "states",      label: "States" },
  { id: "onboarding",  label: "Onboarding" },
  { id: "sanad",       label: "Sanad" },
  { id: "multi-grading", label: "Grading" },
  { id: "tashkeel",    label: "Tashkeel" },
  { id: "crossref",    label: "Cross-refs" },
  { id: "print",       label: "Print" },
  { id: "platform-pivot", label: "★ Platform" },
  { id: "platform-shell", label: "Shell" },
  { id: "books",       label: "Books" },
  { id: "reader",      label: "Reader" },
  { id: "citation-picker", label: "Cite" },
  { id: "add-book",    label: "Add book" },
  { id: "image-regions", label: "Regions" },
  { id: "admin-shamela", label: "Admin" },
  { id: "qa",          label: "Q&A" },
  { id: "platform-home", label: "Home" },
];

const TopNav = () => (
  <div className="sticky top-0 z-40 bg-white/90 backdrop-blur border-b border-slate-200">
    <div className="max-w-[1380px] mx-auto px-10 h-12 flex items-center gap-4">
      <div className="inline-flex items-center gap-2 mr-2">
        <div className="h-6 w-6 rounded bg-indigo-600 grid place-items-center text-white"><I.Network size={13} strokeWidth={1.5} /></div>
        <span className="text-[13px] font-bold tracking-tight text-slate-900">Argument Map</span>
      </div>
      <nav className="flex items-center gap-0.5 overflow-x-auto">
        {SECTIONS.map((s) => (
          <a key={s.id} href={`#${s.id}`} className="px-2.5 h-7 inline-flex items-center rounded-md text-[11px] font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-100">{s.label}</a>
        ))}
      </nav>
      <span className="ml-auto text-[10px] font-mono text-slate-400 tracking-wider uppercase">light · 1280+ · ru/ar</span>
    </div>
  </div>
);

const App = () => (
  <div className="min-h-screen bg-slate-50">
    <TopNav />
    <Hero />

    <div id="tokens">
      <TokensSection />
      {window.ArabicTypographySection && <window.ArabicTypographySection />}
    </div>
    <div className="bg-white border-y border-slate-200" id="components"><ComponentsSection /></div>
    <div id="lang-switcher" className="border-b border-slate-200">{window.LangSwitcherSubsection && <window.LangSwitcherSubsection />}</div>
    <div id="nodes"><NodeMatrix /></div>

    <div className="bg-white border-y border-slate-200" id="bilingual">{window.BilingualSection && <window.BilingualSection />}</div>
    <div id="edges"><EdgeVariantsSection /></div>

    {/* Screen mockups */}
    <Section id="topics" title="Экран · Список тем" kicker="06 — screen" hint="Сетка карточек тем с превью графа, счётчиками и поиском.">
      <TopicsScreen />
    </Section>
    <div className="bg-white border-y border-slate-200">
      <Section id="create" title="Экран · Создание темы" kicker="07 — screen" hint="Чистая форма + превью корневого узла.">
        <CreateTopicScreen />
      </Section>
    </div>
    <Section id="graph" title="Экран · Граф темы" kicker="08 — screen · main" hint="Сердце приложения. 10 узлов, 10 связей, демо-тема — мавлид.">
      <GraphScreen />
    </Section>

    <div className="bg-white border-y border-slate-200" id="panels"><PanelsSection /></div>
    <div id="modals"><ModalsSection /></div>
    {window.AddSourceContextMenu && <window.AddSourceContextMenu />}
    <div id="sources" className="bg-white border-y border-slate-200">{window.SourceLibrarySection && <window.SourceLibrarySection />}</div>

    <div id="authorities">{window.AuthoritiesSection && <window.AuthoritiesSection />}</div>
    <div id="rtl" className="bg-white border-y border-slate-200">{window.RTLSection && <window.RTLSection />}</div>

    {window.SettingsSection && <window.SettingsSection />}
    {window.NewToastsSection && <div className="bg-white border-y border-slate-200"><window.NewToastsSection /></div>}
    <div id="states"><StatesSection /></div>

    {window.OnboardingSection && <div className="bg-white border-y border-slate-200"><window.OnboardingSection /></div>}
    {window.TopicSettingsSection && <window.TopicSettingsSection />}
    {window.MultiSelectSection && <div className="bg-white border-y border-slate-200"><window.MultiSelectSection /></div>}
    {window.SanadSection && <window.SanadSection />}
    {window.MultiGradingSection && <div className="bg-white border-y border-slate-200"><window.MultiGradingSection /></div>}
    {window.TranslatorSection && <window.TranslatorSection />}
    {window.TashkeelSection && <div className="bg-white border-y border-slate-200"><window.TashkeelSection /></div>}
    {window.CrossRefSection && <window.CrossRefSection />}
    {window.ExtraStatesSection && <div className="bg-white border-y border-slate-200"><window.ExtraStatesSection /></div>}
    {window.PrintPreviewSection && <window.PrintPreviewSection />}

    {/* ============ PLATFORM PIVOT (sections 27+) ============ */}
    {window.PlatformPivotIntro && <div id="platform-pivot" className="bg-white border-y border-slate-200"><window.PlatformPivotIntro /></div>}
    {window.PlatformShellSection && <window.PlatformShellSection />}
    {window.BookListPageSection && <div className="bg-white border-y border-slate-200"><window.BookListPageSection /></div>}
    {window.BookReaderSection && <window.BookReaderSection />}
    {window.CitationPickerSection && <div className="bg-white border-y border-slate-200"><window.CitationPickerSection /></div>}
    {window.AddBookSection && <window.AddBookSection />}
    {window.ImageRegionsSection && <div className="bg-white border-y border-slate-200"><window.ImageRegionsSection /></div>}
    {window.ShamelaAdminSection && <window.ShamelaAdminSection />}
    {window.QASection && <div className="bg-white border-y border-slate-200"><window.QASection /></div>}
    {window.PlatformHomeSection && <window.PlatformHomeSection />}

    <footer className="py-12 px-10 border-t border-slate-200 bg-white">
      <div className="max-w-[1380px] mx-auto flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-6 w-6 rounded bg-indigo-600 grid place-items-center text-white"><I.Network size={13} strokeWidth={1.5} /></div>
          <span className="text-[13px] font-bold text-slate-900">Argument Map</span>
          <span className="text-[11px] font-mono text-slate-500">· design showcase</span>
        </div>
        <div className="text-[11px] font-mono text-slate-500">React 18 · Tailwind · Inter · JetBrains Mono · React Flow</div>
      </div>
    </footer>
  </div>
);

const root = ReactDOM.createRoot(document.getElementById("root"));
root.render(<App />);
