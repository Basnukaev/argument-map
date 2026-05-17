# Design System - правила и паттерны

Source of truth для UI решений во frontend. Используется в комплекте с:
- **`design-audit.md`** - анализ текущего состояния, baseline
- **`i18n-guide.md`** - RTL, локали, арабский контент
- **`coding-standards.md`** - общие фронт-конвенции (TypeScript, hooks, тесты)
- **`ui-guidelines.md`** - React Flow и graph-специфичные правила

При любом UI вопросе - сначала смотри сюда. При конфликте: i18n-guide > design-system > coding-standards.

---

## 1. Typography

### Шрифты в проекте

```css
--font-ui:     'Manrope', system-ui, sans-serif  /* всё UI: nav, body, buttons */
--font-serif:  'Source Serif 4', Georgia, serif   /* editorial: h1 hero, EmptyHero */
--font-mono:   'JetBrains Mono', monospace        /* tabular-nums, IDs, version */
--font-arabic: 'Amiri', 'Noto Naskh Arabic', serif /* арабский контент */
```

### Scale

| Назначение | Размер | Weight | Шрифт | Tailwind | Применение |
|---|---|---|---|---|---|
| **eyebrow** | 11px | 600 | UI | `text-[11px] font-semibold uppercase tracking-[0.12em]` | секция/раздел marker над h1 |
| **h1 hero** | 28px | 600 | **serif** | `font-serif text-[28px] font-semibold leading-tight tracking-tight` | главный заголовок страницы |
| **h2 section** | 14px | 600 | UI | `text-sm font-semibold` | заголовок секции внутри страницы |
| **h3 sub-section** | 12px | 600 | UI uppercase | `text-xs font-semibold uppercase tracking-[0.08em]` | подсекции в side panels (СВЯЗЬ, МЕТАДАННЫЕ) |
| **body** | 14px | 400 | UI | `text-sm` | основной текст |
| **body-large** | 15-16px | 400 | UI | `text-base` | акцентные блоки (descriptions в forms) |
| **caption / hint** | 11-12px | 400 | UI | `text-xs text-ink-500` | подсказки, метаданные |
| **stat-value** | 22px | 700 | **mono** | `font-mono text-[22px] font-bold tabular-nums` | числа в metric strips |
| **stat-label** | 10px | 600 | UI uppercase | `text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500` | label для stat |
| **table-header** | 10px | 600 | UI uppercase | `text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500` | заголовки колонок data-tables |
| **arabic-body** | 14-16px | 400-500 | **arabic** | `font-naskh text-base` | арабский контент в карточках |

### Правила

- **h1 hero всегда serif**. Это editorial signature проекта (scholarly platform, не SaaS-tool)
- **числа всегда tabular-nums**, иначе колонки в data-tables «прыгают». В Tailwind = `tabular-nums`
- **uppercase tracking 0.08-0.12em** для всех eyebrow/label - tracking даёт «дыхание» прописным буквам
- **арабский контент** - всегда `dir="auto"` + `font-naskh` через `hasArabicScript()` helper

---

## 2. Color

### Семантика

| Token | Назначение |
|---|---|
| `accent-*` (indigo) | brand цвет, primary buttons, focus rings, **headline metric**, links |
| `ok-*` (green) | success states, импортировано, активный статус |
| `warn-*` (yellow) | warnings, частичные проблемы |
| `err-*` (red) | errors, destructive actions |
| `ink-*` (gray scale 0-900) | text, borders, backgrounds, divider |

### Использование accent (indigo)

**Используй accent**:
- primary CTA button
- focus ring на input'ах
- active state navigation
- **headline metric** в data strips (1 акцентная цифра среди обычных)
- ссылки на которые нужно обратить внимание

**НЕ используй accent**:
- для каждой второй вещи на странице
- для статусов (есть ok/warn/err)
- для типов узлов (есть TypeChip с собственными tokens)

### Background hierarchy

- `bg-bg` - страница (light: cream / dark: deep gray)
- `bg-elevated` - cards, panels, modals (приподняты над bg)
- `bg-sunken` - inner sections внутри elevated (table headers, search container)
- `bg-paper` - reader, читалка-mode

---

## 3. Spacing

### Шкала (Tailwind)

| Token | px | Применение |
|---|---|---|
| `gap-1` / `p-1` | 4 | inline между chip-icon-text |
| `gap-2` / `p-2` | 8 | стандартный inline gap |
| `gap-3` / `p-3` | 12 | section внутри card |
| `gap-4` / `p-4` | 16 | gap между cards |
| `p-5` | 20 | внутренний padding cards/strips |
| `p-6` / `gap-6` | 24 | hero margin-bottom, page padding-x |
| `py-8` | 32 | page padding-y top |
| `mb-8` | 32 | gap между major sections (hero / strip / search / results) |

### Правила

- **container max-width**: `max-w-[1380px]` для admin/data-dense страниц, `max-w-7xl` (1280px) для consumer pages, `max-w-3xl` (768px) для forms
- **page padding**: `px-6 py-8` стандарт
- **hero → content gap**: `mb-6` (24px) минимум
- **section → section gap**: `mb-8` (32px) для разделения major blocks

---

## 4. Hero pattern (применяется ВО ВСЕХ list/form/detail pages)

```tsx
<header className="mb-6 flex flex-wrap items-end justify-between gap-4">
  <div className="min-w-0 flex-1">
    {/* eyebrow - категория экрана, верхний регистр, tracking */}
    <div className="mb-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-500">
      {t('eyebrow.key')}
    </div>
    {/* h1 - editorial serif, не bold sans */}
    <h1 className="font-serif text-[28px] font-semibold leading-tight tracking-tight text-ink-900">
      {t('hero_title.key')}
    </h1>
    {/* sub-line - context, count, descriptor */}
    <p className="mt-1.5 max-w-[680px] text-sm text-ink-500">
      {t('subtitle.key')} · <span className="font-medium text-ink-700">{count}</span> {t('count_suffix.key')}
    </p>
  </div>
  {/* actions: secondary → primary, primary anchored к правому краю */}
  <div className="flex items-center gap-2">
    {/* optional overflow menu IconButton: <IconButton icon={MoreHorizontal} ... /> */}
    {/* optional secondary: <Button variant="secondary" ...>... */}
    <Button icon={...}>{t('primary_action.key')}</Button>
  </div>
</header>
```

**Правила**:
- **primary CTA на правом краю**, secondary слева от неё, overflow `•••` самый левый
- count в sub-line **без pill** - inline число выделяется font-medium text-ink-700 (см. `feedback_handoff_ui_checks` про consistency)
- `min-w-0 flex-1` на левой колонке чтобы текст truncate'ился при узком экране

---

## 5. Section header pattern (внутри страниц / panels)

```tsx
<h2 className="mb-2 text-sm font-semibold text-ink-900">
  {t('section_title.key')}
</h2>
```

**ИЛИ** uppercase eyebrow стиль (для side panels):

```tsx
<h3 className="text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500">
  {t('subsection.key')}
</h3>
```

**Правило**: один стиль на странице. Не миксовать `h2 text-sm` и `h3 uppercase` в одной плоскости. Eyebrow-style - для side panel «дисциплины» (NodeDetailsPanel), normal h2 - для основной поверхности (admin/topic-list).

---

## 6. Data table pattern

Когда у тебя ≥10 items с одинаковой структурой (id, name, author, action) - **table, не cards**. Cards для browse-mode (книги, темы) с ≤10 items per row.

```tsx
<div className="overflow-hidden rounded-lg border border-border bg-elevated">
  {/* sticky header */}
  <div
    className="sticky top-0 z-[1] grid items-center gap-3 border-b border-border bg-sunken px-4 py-2 text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500"
    style={{ gridTemplateColumns: '88px 1fr 220px 80px 200px' }}
  >
    <span>{t('table.id')}</span>
    <span>{t('table.name')}</span>
    <span>{t('table.author')}</span>
    <span>{t('table.major')}</span>
    <span>{t('table.status')}</span>  {/* left-aligned даже если actions справа */}
  </div>
  <ul className="divide-y divide-border">
    {rows.map((r) => (
      <li key={r.id}>
        <div className="grid items-center gap-3 px-4 py-2.5 transition-colors hover:bg-sunken/60"
             style={{ gridTemplateColumns: '88px 1fr 220px 80px 200px' }}>
          {/* ID - mono tabular */}
          {/* name - dir="auto" + naskh если arabic */}
          {/* author - то же */}
          {/* major - mono */}
          {/* action - flex justify-end */}
        </div>
      </li>
    ))}
  </ul>
</div>
```

**Правила**:
- gridTemplateColumns **inline** (не Tailwind class), чтобы header и rows синхронизировались точно
- header СТАТУС / ДЕЙСТВИЕ **left-aligned** даже если кнопки прижаты к правому краю - консистентность всех headers важнее «нависания» (см. session 2026-05-17 admin redesign feedback)
- hover state `hover:bg-sunken/60` - подсветка row при наведении
- если 50+ rows: добавить sticky top header, lazy-load или pagination

---

## 7. Stat strip pattern (metric dashboard)

```tsx
<section className="mb-8 overflow-hidden rounded-lg border border-border bg-elevated">
  <div className="grid grid-cols-2 divide-y divide-border sm:grid-cols-3 sm:divide-y-0 sm:[&>*]:border-s sm:[&>*]:border-border sm:[&>*:first-child]:border-s-0 lg:grid-cols-[repeat(N,minmax(0,1fr))_auto]">
    <Stat label="X" value="123" />
    <Stat label="Y" value="456" hint="extra context" />
    ...
    <Stat label="Z" value="9 / 10" accent />  {/* headline metric */}
    {/* optional 6-я колонка - status chip / live indicator */}
  </div>
</section>
```

`Stat`-компонент - см. `apps/admin/pages/AdminShamelaPage.tsx` (можно extraction'нуть в `shared/components/ui/Stat.tsx` если используется в 2+ местах).

**Правила**:
- 3-6 метрик в одной полосе. Если меньше 3 - **не нужна** strip, лучше inline в sub-line. Если больше 6 - **разбить** на две полосы
- ровно **одна** accent метрика (headline) - которая показывает «где мы в процессе». Без accent - пусть глаз сам выбирает
- divide-x **без gap-x** (gap расходится с divider'ом), padding **внутри** Stat (не на родителе)

---

## 8. Form pattern (CreateTopicPage эталон)

```tsx
<div className="grid grid-cols-1 gap-8 lg:grid-cols-[1fr_320px]">
  {/* form column */}
  <Card>
    <form>
      <Field label="..." required hint="...">
        <input type="text" ... />
      </Field>
      ...
      <div className="flex justify-end gap-3">
        <Button variant="ghost">{t('common.cancel')}</Button>
        <Button type="submit">{t('common.submit')}</Button>
      </div>
    </form>
  </Card>

  {/* hint panel - opcional but recommended for non-trivial forms */}
  <aside>
    <div className="rounded-lg border border-border bg-paper p-5">
      <div className="mb-2 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.1em] text-ink-500">
        <Icon /> {t('hint.eyebrow')}
      </div>
      <p className="text-sm leading-relaxed text-ink-700">
        {t('hint.body')} ...
      </p>
      <p className="mt-3 italic text-xs text-ink-500">
        {t('hint.example')}
      </p>
    </div>
  </aside>
</div>
```

**Правила**:
- 2-column layout `1fr 320px` на ≥lg экранах. Hint panel = 320px правая колонка
- Hint panel в `bg-paper` (не elevated) - отличается от form-card визуально, читается как «отдельный голос»
- Для **простых** forms (1-2 поля без контекстных нюансов) hint panel **не нужен** - оверkill

---

## 9. Empty state pattern

Два уровня:

**Лёгкий** - inline сообщение под search/filter:
```tsx
<p className="text-sm text-ink-500">{t('list.not_found')}</p>
```

**Большой** - illustrated panel для важных моментов (404, пустая основная сущность):
```tsx
<section className="mb-8 flex flex-col items-center gap-4 rounded-lg border border-border bg-elevated px-6 py-10 text-center sm:flex-row sm:text-start">
  <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-accent-50 text-accent-600">
    <Icon size={24} aria-hidden />
  </div>
  <div className="min-w-0 flex-1">
    <h2 className="font-serif text-xl font-semibold text-ink-900">
      {t('empty.title')}
    </h2>
    <p className="mt-1 max-w-[560px] text-sm text-ink-500">
      {t('empty.body')}
    </p>
  </div>
  <Button icon={...} size="lg">{t('empty.action')}</Button>
</section>
```

**Правила**:
- большой illustrated panel - **в важных моментах**: 404, первое использование (нет данных вообще), error фатальный
- лёгкий inline - для search-no-results, фильтр-no-results
- для 404 / error **не показывать** raw error text / UUID наружу - дать понятное user-facing сообщение + CTA

---

## 10. Button hierarchy

```
primary (indigo bg)     - главное действие на экране, одно за раз
secondary (white bg)    - secondary action, рядом с primary
ghost (transparent)     - tertiary actions, в strip'ах кнопок
danger (red bg)         - destructive primary
danger-ghost            - destructive secondary
link                    - inline в тексте
```

**Правило-якорь**: primary CTA **всегда на крайнем правом ребре** контейнера действий. Overflow `•••` слева от secondary. Цепочка: `[•••] [secondary] [primary]` всегда читается слева→направо как «больше опций → альтернатива → главное».

**Sizes**: `xs` (h-7) для inline в table-cells, `sm` (h-8) для action в card, `md` (h-9) default, `lg` (h-11) для hero CTA empty state.

---

## 11. Modal pattern

Использовать существующий `Modal` / `FormModal` из `shared/components/ui/`. Эталоны:

- **AddNodeModal** (`apps/argument-map/components/graph/`) - выбор типа (radio-cards) + textarea + footer с keybinding hint
- **CommandPalette** (`shared/components/layout/`) - search + list + keybinding footer

Общие правила:
- header: title слева + close × справа
- footer: keybinding hint слева + action buttons справа
- Esc закрывает, Enter подтверждает primary action

---

## 12. RTL и i18n

См. `i18n-guide.md` - **обязательно** перед UI работой.

Краткое напоминание:
- Tailwind logical classes (`ms-*`, `me-*`, `text-start`, `border-s`) - не физические
- `dir="auto"` на user content
- font-naskh через `hasArabicScript()` для арабского контента
- все строки через `useT(key)` - никакого хардкода в JSX
- Иконки нав-стрелок (`ChevronLeft/Right`) - **по локали интерфейса**, не по контенту
- даты через `useFormatDate`, числа через `useNumberFormat`

---

## 13. Что НЕ переделываем

Эти UI элементы работают - **не трогать без отдельной оценки**:

- React Flow граф (`TopicGraphPage` canvas) - flagship
- `BookListPage` covers (большие arabic single-letter с цветом) - уникальная фишка
- `CommandPalette` - эталон UX
- `NodeDetailsPanel` / `EdgeDetailsPanel` - reference для side panels
- `Header` navigation - cross-cutting, требует отдельной сессии при изменении

---

## История правок

- **2026-05-17**: документ создан после редизайна `AdminShamelaPage` как baseline для systematic design pass (см. `design-audit.md`)
