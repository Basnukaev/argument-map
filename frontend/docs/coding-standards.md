# Стандарты кода (frontend)

Этот документ описывает принципы, которым следует код фронтенда.
Применяется ко всему новому коду. При ревью — проверять соответствие
этому документу.

## Базовые принципы

### SOLID — в контексте React

**S — Single Responsibility Principle**
Один компонент — одна ответственность. Если `TopicGraph` рендерит
canvas, обрабатывает клики, открывает модалки и делает API-запросы —
разделить: `GraphCanvas` (только canvas), `useGraphActions` (хук для
действий), `useTopicGraphData` (загрузка данных).

**O — Open/Closed**
Кастомные узлы и рёбра React Flow — через регистрацию в `nodeTypes` /
`edgeTypes`. Добавление нового типа узла = новый компонент + запись в
реестре, не правка существующих.

**L — Liskov**
Реализации интерфейсов / хуков должны соответствовать контракту. Если
хук возвращает `{ data, loading, error }` — все реализации должны вести
себя одинаково в этих полях.

**I — Interface Segregation**
Маленькие пропсы лучше больших. Если компонент использует только 2-3
поля из объекта — передавать их явно, не весь объект.

**D — Dependency Inversion**
Компоненты не должны зависеть от конкретной реализации API-клиента.
Использовать хуки (`useApiClient`) или контекст для подмены в тестах.

### KISS, DRY, YAGNI, Composition over Inheritance
Те же правила что в `backend/docs/coding-standards.md`. В контексте
React: композиция компонентов (children, render props), не наследование
классов (которых у нас и нет — функциональные компоненты).

### Правило трёх (DRY)
Не выделять общий компонент / хук при втором повторении. На третьем —
становится паттерном, можно выносить.

## TypeScript правила

### Strict mode
В `tsconfig.json`:
```json
{
  "compilerOptions": {
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "noImplicitOverride": true,
    "exactOptionalPropertyTypes": true
  }
}
```

### Никаких `any`
- Всегда явный тип или `unknown`
- `unknown` требует narrowing перед использованием — это правильно
- Если очень-очень нужен `any` — `// FIXME: type properly` + строка
  обоснования

### Без TypeScript `enum`
Union literal types вместо:
```ts
// плохо
enum NodeStatus {
  STANDING = 'STANDING', DISPUTED = 'DISPUTED', ...
}

// хорошо
type NodeStatus = 'STANDING' | 'DISPUTED' | 'REFUTED' | 'UNVERIFIED';

const ALL_STATUSES: readonly NodeStatus[] = [
  'STANDING', 'DISPUTED', 'REFUTED', 'UNVERIFIED'
] as const;
```

Причины: `enum` создаёт runtime-объект, плохо tree-shake'ается, не
сериализуется в JSON естественно. Union literal types — чисто
compile-time, бесплатны.

### Пропсы — через `interface`
Для пропсов компонентов — `interface` (расширяется), для остальных
типов — `type` (композиции через `&`, `|`).

```tsx
interface NodeCardProps {
  node: NodeResponse;
  onSelect?: (id: string) => void;
}

type NodeStatus = 'STANDING' | 'DISPUTED' | 'REFUTED' | 'UNVERIFIED';
```

### Утилитные типы — активно
- `Partial<T>`, `Required<T>` — для PATCH-запросов и формы
- `Pick<T, K>`, `Omit<T, K>` — для производных типов
- `Record<K, V>` — для объектов-словарей
- `ReadonlyArray<T>` / `readonly T[]` — для иммутабельных списков

### Generic'и где уместно
Для API-обёрток, хуков работы со списками, форм:
```ts
function useApiList<T>(endpoint: string): {
  data: T[]; loading: boolean; error: ProblemDetail | null;
}
```

### Обработка `null` / `undefined`
- API ответы могут содержать `null` для опциональных полей —
  типизировать как `string | null`, не `string | undefined`
- Внутренние состояния — `undefined` для "ещё не загружено"
- `??` (nullish coalescing) предпочтительнее `||` для дефолтов

## React правила

### Только функциональные компоненты
Class components не использовать. Если возникает соблазн — это hook или
композиция компонентов.

### Хуки

**Базовые:**
- `useState` — локальное состояние
- `useEffect` — побочные эффекты, подписки, синхронизация с внешними
  системами
- `useRef` — мутабельная ссылка, доступ к DOM
- `useCallback`, `useMemo` — оптимизация ре-рендеров

**`useMemo` / `useCallback` — только при реальной проблеме**
Не использовать превентивно (YAGNI). Сначала измерить, потом
оптимизировать. Профилировать через React DevTools.

Исключение: пропсы для `React.memo`-обёрнутых компонентов, объекты
для `nodeTypes` / `edgeTypes` React Flow (см. ниже) — там нужна
ссылочная стабильность.

**Кастомные хуки:**
- Имя начинается с `use`
- Извлекают логику, не UI
- Возвращают объект или массив — единообразно по проекту
- Принимают параметры явно, не через контекст

```tsx
function useTopicGraph(topicId: string) {
  const [graph, setGraph] = useState<GraphResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ProblemDetail | null>(null);
  // ...
  return { graph, loading, error, refresh };
}
```

### Один компонент — один файл
Маленькие helper-компоненты допустимы внутри файла, если используются
только там.

### Размер компонента
- До 100 строк (включая JSX) — норма
- 100-200 — повод подумать о разделении (выделить под-компоненты,
  хуки)
- 200+ — точно надо разделить

### Props destructuring в параметрах
```tsx
// хорошо
function NodeCard({ node, onSelect }: NodeCardProps) {
  return <div onClick={() => onSelect?.(node.id)}>...</div>;
}

// плохо
function NodeCard(props: NodeCardProps) {
  return <div onClick={() => props.onSelect?.(props.node.id)}>...</div>;
}
```

### `key` в списках — UUID, не индекс
```tsx
// хорошо
{nodes.map(node => <NodeCard key={node.id} node={node} />)}

// плохо
{nodes.map((node, i) => <NodeCard key={i} node={node} />)}
```

Индекс ломается при reorder / insert / delete посередине списка.

**Допустимые исключения** (index keys корректны):

- **Парсер-сегменты** — `key={i}` для результатов string parsing
  (`InlineCitationBody`, `FloatingActionBar` split). Список
  перегенерируется целиком когда меняется input, reorder невозможен
- **Статические visualizations** — фиксированные SVG элементы
  (`TopicListPage` dots/lines), placeholder log items
  (`AdminShamelaPage` activity log) - список immutable константа

Если используешь index key - **обязательно** комментарий "почему" и
почему reorder невозможен.

### Условный рендеринг
- `{condition && <X />}` — для простых случаев
- Тернарник для двух вариантов: `{loading ? <Spinner /> : <Content />}`
- `if/else` с `return` в начале функции — для сложных ветвлений
  (early return для loading / error)

### Forms
- Контролируемые компоненты по умолчанию
- Для сложных форм — `react-hook-form` (рассмотреть отдельным ADR при
  появлении первой нетривиальной формы)
- Валидация на клиенте — те же правила что у бэка (Bean Validation),
  но "best effort". Серверная валидация — источник истины

### Hotkeys (keyboard shortcuts)

Все глобальные / контекстные keyboard shortcuts регистрируются через
`useHotkey` из `@/shared/hooks/useHotkey` — обёртка над
`react-hotkeys-hook` (ADR-036).

**НЕ использовать `addEventListener('keydown')` или
`document.addEventListener('keydown')`** — фронт мигрирован полностью
(Сессия 38). Единственное исключение — inline `onKeyDown={...}` на
одном конкретном `<input>` для Enter-to-submit семантики (PageJump,
PdfViewer) — это форма-bound локальная логика, не global hotkey.

```tsx
import { useHotkey } from '@/shared/hooks/useHotkey';

// глобальный hotkey, не срабатывает в input/textarea
useHotkey('alt+k', () => togglePalette());

// submit формы по Cmd/Ctrl+Enter (работает в textarea)
useHotkey('mod+enter', () => formRef.current?.requestSubmit(), {
  enableOnFormTags: true,
  enabled: open,  // только когда модалка открыта
});

// Escape в picker'е с input'ом
useHotkey('escape', onClose, { enableOnFormTags: true });
```

**Modifier `mod`** — cross-platform: `⌘` на Mac, `Ctrl` на Win/Linux.
Использовать вместо `meta+enter,ctrl+enter` — короче и канонично.

**`useKey: true`** в default options делает буквенные hotkey'ы
layout-independent (event.code → KeyK не зависит от ru/ar/en
раскладки). Отдельно включать не нужно.

**preventDefault gotcha:** для Escape когда есть native `<dialog
showModal()>` — ставить `preventDefault: false` в опциях и звать
`e.preventDefault()` вручную внутри callback только когда реально
обрабатываем. Иначе native dialog Esc не закроется. См. `useGraphEscape`.

**UI display** — `<ShortcutHint keys="mod+enter" />` рендерит правильный
glyph для платформы (Mac/Win/Linux) автоматически. Не хардкодить `⌘`
в JSX.

```tsx
import ShortcutHint from '@/shared/components/ui/ShortcutHint';

<button>
  <Search size={13} />
  <ShortcutHint keys="alt+k" />
</button>
```

## React Flow специфика

### `nodeTypes` / `edgeTypes` — вне компонента
Чтобы сохранять ссылочную стабильность между ре-рендерами:

```tsx
// файл-модуль уровень
const nodeTypes = {
  argument: ArgumentNode,
  claim: ClaimNode,
  question: QuestionNode,
  evidence: EvidenceNode,
} as const;

const edgeTypes = {
  supports: SupportsEdge,
  refutes: RefutesEdge,
  // ...
} as const;

function GraphCanvas() {
  return <ReactFlow nodeTypes={nodeTypes} edgeTypes={edgeTypes} />;
}
```

Если объявить внутри компонента — на каждый ре-рендер новая ссылка,
React Flow перестраивает всё. Это медленно и ломает анимации.

### Controlled flow
- `onNodesChange` / `onEdgesChange` — обрабатывать через
  `applyNodeChanges` / `applyEdgeChanges` из `@xyflow/react`
- Состояние графа — в Zustand-сторе, не локальный `useState`
  (нужно делиться с боковыми панелями, тулбарами)

### Автолейаут
- Через `dagre` (горизонтальный слева-направо: корневой `QUESTION`
  слева, ответы и аргументы — правее)
- Запускать на загрузке графа и при добавлении узла, дальше —
  ручное расположение пользователя

## Именование

- **Компоненты:** `PascalCase` — `TopicCard`, `ArgumentNode`,
  `GraphCanvas`, `NodeDetailPanel`
- **Хуки:** `useXxx` — `useTopicGraph`, `useApiClient`,
  `useDebouncedSearch`
- **Сторы (Zustand):** `useXxxStore` — `useGraphStore`, `useTopicStore`
- **Типы / интерфейсы:** `PascalCase` — `TopicResponse`,
  `GraphViewData`
- **Утилиты:** `camelCase` — `formatDate`, `truncateContent`,
  `nodeTypeToIcon`
- **Файлы компонентов:** `PascalCase.tsx` — `TopicCard.tsx`
- **Файлы утилит / хуков / сторов:** `camelCase.ts` — `useTopicGraph.ts`
- **Тесты:** `*.test.tsx` / `*.test.ts` рядом с тестируемым файлом

## Обработка ошибок

### API ошибки — Problem Details
Бэк возвращает RFC 7807. Парсить как:
```ts
type ProblemDetail = {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  errors?: Array<{ field: string; message: string }>;
};
```

Показывать пользователю `title` + `detail`. `errors[]` — для подсветки
полей формы.

### `try` / `catch` в async
В async-функциях — `try` / `catch`, не `.catch()`. Нагляднее:
```ts
async function loadTopic(id: string) {
  try {
    const topic = await api.getTopic(id);
    setTopic(topic);
  } catch (err) {
    setError(err as ProblemDetail);
  }
}
```

### Error boundaries
- На уровне страниц (через `ErrorBoundary` из `react-error-boundary`)
- Не на уровне отдельных компонентов — слишком гранулярно
- Корневой `App` тоже оборачиваем для catch всего непойманного

### Оптимистичные обновления
**Не делать в MVP** (YAGNI). Когда понадобится — отдельно проектируем
для конкретного use-case (например, drag-and-drop узлов где задержка
заметна).

## Тесты

### Vitest + React Testing Library
- Имитация поведения пользователя через `userEvent`
- Запросы к DOM через `screen.getByRole`, `screen.getByLabelText` —
  не `getByTestId` без необходимости (test-ids — последний выбор,
  лучше семантические селекторы)
- `waitFor` для асинхронных проверок

### Что тестировать
- Компоненты — пользовательские взаимодействия (клик, ввод, отправка),
  отображение разных состояний (loading / error / data)
- Сторы — логика мутаций состояния
- Утилиты — чистые функции, граничные случаи
- Хуки — через `renderHook` (если хук содержит нетривиальную логику)

### Что НЕ тестировать
- Стили / layout / pixel positions
- Implementation details (внутренние useState, имена пропсов)
- Сторонние библиотеки (React Router, React Flow — у них свои тесты)

### Моки API через MSW
```ts
// src/test/mocks/handlers.ts
import { http, HttpResponse } from 'msw';

export const handlers = [
  http.get('/api/v1/topics', () => HttpResponse.json([
    { id: '...', title: 'Test', ... }
  ])),
];
```

MSW перехватывает запросы на уровне fetch, что максимально близко к
реальной работе.

### Структура теста — AAA
- **Arrange** — подготовка (render, setup mocks)
- **Act** — действие (userEvent.click, type)
- **Assert** — проверка (expect)

Без комментариев `// arrange / act / assert` — пустые строки
достаточно.

## RTL и bidi

Проект двуязычный (RU/AR). Локаль UI и язык контента - **разные**
вещи, нельзя выводить одно из другого.

**Полный гайд с примерами, анти-паттернами и чек-листом перед PR -
в `frontend/docs/i18n-guide.md`**. Ниже - краткая выжимка.

### Локаль интерфейса vs язык контента

- **UI-строки** (DICTIONARY, лейблы, кнопки) - направление от
  `useLocaleStore` (`<html dir>`). Tailwind logical classes
  (`ms-*`, `me-*`, `text-start`, `border-s`) сами адаптируются
- **Контент из API** (названия книг, цитаты, имена авторов,
  содержимое узлов) - направление через `dir="auto"`, браузер
  определяет по первому сильному символу
- Шрифт `font-naskh` для арабского - через эвристику
  `hasArabicScript(text)` из `@/shared/i18n` (`dir="auto"` шрифт
  не переключает)

### Физические направленные классы запрещены

Использовать только логические:

| Физический | Логический |
|---|---|
| `ml-*` / `mr-*` | `ms-*` / `me-*` |
| `pl-*` / `pr-*` | `ps-*` / `pe-*` |
| `left-*` / `right-*` | `start-*` / `end-*` |
| `text-left` / `text-right` | `text-start` / `text-end` |
| `border-l*` / `border-r*` | `border-s*` / `border-e*` |
| `rounded-l-*` / `rounded-r-*` | `rounded-s-*` / `rounded-e-*` |

`inset-x-*` оставлять как есть (симметричный, не направленный).

### Что зеркалится, что нет

**Зеркалится** (используем логические классы):
- поток UI-текста и блоков, выравнивание, отступы
- сайдбары, drawer'ы, панели деталей
- toolbar'ы пагинации (стрелки навигации - по локали UI)

**НЕ зеркалится**:
- логотип/бренд - оборачивать в `dir="ltr"` чтобы заблокировать
  bidi-flip от родителя
- ненаправленные иконки (плюс, шестерёнка, лупа, корзина, закрытие)
- числа, моноширинные ID, код
- **граф React Flow целиком** (canvas, позиции узлов, мини-карта) -
  это пространственная структура. Меняется только язык текста
  внутри узлов (`dir="auto"` + шрифт по `hasArabicScript`) и
  направление UI-панелей вокруг канваса

### Mixed-content и `<bdi>`

В одной строке несколько скриптов / цифры / пунктуация - собирать
из bidi-изолированных кусков с явными разделителями. Без `<bdi>`
Unicode Bidi Algorithm может склеить цифры с соседними RTL-символами,
и «8 мая 2026 г.» превращается в «мая 2026 г. в 8».

```tsx
<dd className="font-mono text-slate-700">
  <bdi dir="ltr">{formatDate(createdAt)}</bdi>
</dd>

<span>
  <bdi>{publisher}</bdi>
  <span aria-hidden> · </span>
  <bdi>{place}</bdi>
</span>
```

Помощник `Bdi` в `@/shared/components/citation/sourceCard` - готовая
обёртка для LTR-значений внутри RTL-карточек.

### Единый источник эвристики

`hasArabicScript` и `getTextDirection` - из `@/shared/i18n` (модуль
`shared/i18n/script.ts`). Inline regex'ы `/[؀-ۿ]/` в компонентах
запрещены. `isArabicText` в `shared/components/reader/utils`
сохраняется как алиас для читаемости reader-кода.

## Responsive

UI должен работать на mobile (375px+) и tablet (768px+) viewport.
Фаза 1 (Modal, Header, NodeDetailsPanel, Select) - Сессия 39.
Фаза 2 (BookReader drawer, PdfViewer 2-row, CitationPicker tabs,
list/create padding, table h-scroll, filter chips overflow) -
Сессия 40. Обе сжаты в roadmap closed-stages

### Mobile-first + breakpoint prefixes

Базовые стили - **для mobile**, breakpoint prefix добавляет desktop:

```tsx
// правильно: mobile base + md: override
<div className="flex flex-col gap-2 md:flex-row md:gap-4">

// неправильно: desktop base + max-md: override
<div className="flex flex-row gap-4 max-md:flex-col max-md:gap-2">
```

Tailwind v4 default breakpoints:
- `sm:` 640px
- `md:` 768px (mobile/tablet boundary)
- `lg:` 1024px
- `xl:` 1280px

### Когда CSS, когда JS

**CSS-only (предпочтительно)** - стили / визуальный layout:

```tsx
// показать/скрыть, поменять direction, gap, padding
<nav className="hidden md:flex" />
<div className="flex flex-col md:flex-row" />
```

**`useIsMobile()` hook** из `@/shared/hooks/useViewport` - когда
нужна **другая структура компонента** или conditional event handler:

```tsx
// модалка fullscreen на mobile vs centered на desktop
const isMobile = useIsMobile();
const dialogClass = isMobile
  ? 'm-0 h-dvh w-screen'
  : 'm-auto max-w-lg rounded-lg';

// hamburger menu vs inline nav
{isMobile && <IconButton icon={Menu} onClick={openDrawer} />}
<nav className="hidden md:flex">{links}</nav>
```

Не использовать `useIsMobile()` там, где breakpoint prefix достаточен -
это runtime overhead + SSR-incompatibility hazard

### Modal / overlay pattern на mobile

Базовый `Modal` (shared/components/ui/Modal) автоматически
переключается:
- mobile: full-screen (`inset-0`, `h-dvh`, `w-screen`), back-arrow
  в header вместо close-X
- desktop: centered (`m-auto`), max-w, rounded

Все custom overlay (NodeDetailsPanel, EdgeDetailsPanel, бизнес-
панели) - **должны** делать тот же switch через `useIsMobile()`:
right-side panel на mobile блокирует основной content. Либо
fullscreen overlay (как NodeDetailsPanel), либо bottom-sheet
(`fixed bottom-0 inset-x-0 max-h-[80vh] rounded-t-2xl`)

### `dvh` вместо `vh`

Mobile browser address-bar collapsing - на iOS Safari / Chrome
`100vh` шире viewport когда bar развёрнут. Использовать `h-dvh`
(dynamic viewport height) - корректное значение в обоих состояниях.
Modal Фазы 1 уже использует

### Grid responsive (cards layout)

Стандартный паттерн для list-страниц:

```tsx
<ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
```

Mobile-first: на 375px одна колонка, на >=640 две, на >=1024 три,
на >=1280 четыре. `gap-4` константен - визуальный rhythm не должен
прыгать на breakpoints. См. `BookListPage` (5 cols max),
`TopicListPage` (3 cols max)

### Container padding на mobile

`px-3 py-6 sm:px-6 sm:py-8` - mobile padding 12px/24px, desktop
24px/32px. На 375px это даёт +24px content width vs константного
px-6. Применяется ко всем `<main>` containers списочных и
create-страниц

### Filter chips - overflow-x-auto на mobile

6+ chips в filter bar не помещаются в 375px. Стандарт mobile -
horizontal scroll (iOS Safari / Android Chrome знакомый gesture):

```tsx
<div className="-mx-3 flex overflow-x-auto px-3 sm:mx-0 sm:overflow-visible sm:px-0">
  <div className="flex gap-1 shrink-0">
    {chips.map((c) => (
      <button key={c} className="... whitespace-nowrap">{c}</button>
    ))}
  </div>
</div>
```

`-mx-3 px-3` cancellation - scrollbar идёт edge-to-edge без обрезки
padding parent'а. `whitespace-nowrap` на chip обязателен - иначе
длинные локализации (arabic) ломают на 2 строки

### Drawer pattern на mobile (chapters sidebar и т.п.)

Inline sidebar (280px aside слева) на mobile занимает половину
viewport - неприемлемо. Переезжает в `Modal` (full-screen из
Фазы 1), открывается из кнопки в content:

```tsx
// Desktop: inline. Mobile: hidden - открывается через button + Modal
<aside className="hidden w-[280px] shrink-0 md:block">
  <Card className="sticky top-6 max-h-[calc(100dvh-7rem)] overflow-y-auto p-4">
    {chaptersContent}
  </Card>
</aside>

{/* В toolbar - кнопка «Главы» которая открывает drawer */}
<Button icon={List} className="md:hidden" onClick={() => setOpen(true)}>
  Главы
</Button>

{isMobile && open && (
  <Modal open onClose={() => setOpen(false)} title="Главы">
    {chaptersContent}
  </Modal>
)}
```

`chaptersContent` - extracted JSX, переиспользуется между inline и
drawer. Не дублировать markup

### Table h-scroll на mobile

Data table с фиксированной grid-template (например 668px суммарно)
на 375px не помещается. Решение - `overflow-x-auto` wrapper +
`min-w-[668px]` inner:

```tsx
<div className="overflow-x-auto rounded-lg border border-border bg-elevated">
  <div className="min-w-[668px]">
    <div className="sticky top-0 grid ..." style={{ gridTemplateColumns }}>
      {/* headers */}
    </div>
    <ul>{/* rows */}</ul>
  </div>
</div>
```

Sticky header работает корректно - двигается синхронно при
horizontal scroll. См. `AdminShamelaPage::ResultsTable`

### Tab switcher для multi-column модалок

3-колоночные модалки (BooksList + Reader + SelectionPanel)
на mobile превращаются в tab switcher вместо 3 столбцов. Auto-switch
табов после действия (`books → reader` после выбора книги):

```tsx
const [tab, setTab] = useState<'books' | 'reader' | 'selection'>('books');

return (
  <>
    <div role="tablist" className="flex border-b sm:hidden">
      <TabButton active={tab === 'books'} onClick={() => setTab('books')}>Книги</TabButton>
      {/* ... */}
    </div>
    <div className="flex flex-1 gap-3">
      <aside className={`${isMobile && tab !== 'books' ? 'hidden' : 'flex'} sm:flex`}>
        ...
      </aside>
      {/* center, right - аналогично */}
    </div>
  </>
);
```

Не использовать conditional render `{tab === 'X' && <Panel/>}` -
unmount теряет internal state форм. Использовать CSS toggle через
hidden/flex classes. См. `CitationPicker.tsx`

### Testing

`window.matchMedia` polyfill добавлен в `src/test-setup.ts` -
default `matches=false` (desktop). Тесты которым нужно эмулировать
mobile - переопределяют через свой `beforeEach` (см.
`Modal.test.tsx::stubMatchMedia`)

## Код-ревью

При ревью кода проверять в порядке:
1. **Правильность** — делает то, что должен?
2. **Соответствие архитектуре** — не нарушает ADR?
3. **Соответствие стандартам** — этот документ
4. **TypeScript** — `any` нет? Типы точные? `null` обработан?
5. **React** — хуки используются правильно? `key` в списках?
6. **Тесты** — есть покрытие ключевого поведения?
7. **Доступность (a11y)** — семантический HTML, `aria-*`,
   keyboard navigation
8. **Читаемость** — понятно через месяц?

Формат комментариев ревью:
- 🔴 Критично — нельзя мерджить
- 🟡 Важно — желательно исправить
- 🟢 Совет — на усмотрение автора
