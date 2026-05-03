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
