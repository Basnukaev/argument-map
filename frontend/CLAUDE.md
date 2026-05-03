# Argument Map — Frontend (Claude Code config)

## Контекст: это монорепа

Этот `CLAUDE.md` — конфиг для работы над **фронтенд-частью** проекта
Argument Map. Полная структура репозитория описана в `../README.md`.

Правила:
- Работать **только** в пределах папки `frontend/`. Не создавать, не
  редактировать и не удалять файлы в корне репы и в `../backend/`
  без явного запроса пользователя.
- Общая документация проекта лежит в `../docs/` — её читаем, но
  редактируем только когда изменения явно относятся к продукту в целом
  (новый ADR, обновление roadmap, запись в progress).
- Фронтенд-специфичная документация — в `frontend/docs/`.
- Корневой `docker-compose.yml` общий — не копировать в `frontend/`.

## О проекте

SPA-фронтенд для Argument Map. Визуализация графа аргументов через
React Flow в стиле Miro / Obsidian: drag-and-drop узлов, кастомные
карточки с цветом по статусу, типизированные стрелки между узлами.

Общается с бэкенд API по контракту из `../docs/api-contract.md`. Бэкенд
полностью самодостаточен — фронт это только UI поверх него.

## Стек

- **React 19** + **TypeScript** (strict mode)
- **Vite** — сборка и dev-сервер
- **React Flow** (`@xyflow/react`) — визуализация графа
- **Tailwind CSS** — стилизация (utility-first)
- **React Router v7** — клиентский роутинг
- **Zustand** — стейт-менеджмент (простая альтернатива Redux)
- **openapi-typescript** — генерация TS-типов из `/v3/api-docs` бэка
- **Vitest** + **React Testing Library** — тесты
- **MSW** (Mock Service Worker) — моки API в тестах

## Документация

Перед началом работы **обязательно** прочитать.

### Общая документация проекта (`../docs/`)
- `../docs/progress.md` — журнал сессий, последние 2-3 записи
- `../docs/roadmap.md` — что сделано, что в работе, что дальше
- `../docs/decisions.md` — принятые архитектурные решения (ADR)
- `../docs/gotchas.md` — известные ловушки и подводные камни проекта
- `../docs/architecture.md` — высокоуровневая архитектура, доменные сущности
- `../docs/glossary.md` — термины проекта
- `../docs/api-contract.md` — **контракт API**, источник истины для фронта
- `../docs/session-workflow.md` — компактный чек-лист сессии
- `../docs/git-workflow.md` — Conventional Commits, правила ветвления

### Фронтенд-специфичная документация (`docs/`)
- `docs/coding-standards.md` — стандарты TypeScript/React, SOLID, KISS,
  правила хуков и компонентов
- `docs/ui-guidelines.md` — дизайн-система: цвета статусов, типы рёбер,
  спецификация кастомных узлов и страниц

## Работа с документацией (КРИТИЧНО)

Эти правила обеспечивают непрерывность работы между сессиями. Контекст
одной сессии теряется, поэтому документация — это "память" проекта.

### В начале каждой сессии
1. Прочитать `../docs/progress.md` — последние 2-3 записи
2. Прочитать `../docs/roadmap.md` — найти текущую задачу
3. Прочитать `../docs/decisions.md` — не нарушать принятые решения
4. Прочитать `../docs/gotchas.md` — не наступить на известные грабли
5. Прочитать `../docs/api-contract.md` — сверить ожидания по API
6. Кратко озвучить пользователю: *"Вижу, последний раз делали X,
   следующая задача — Y. Продолжаем?"*

### По ходу работы
- Принял архитектурное решение? → дописать ADR в `../docs/decisions.md`
- Наткнулся на неочевидный подводный камень? → дописать в `../docs/gotchas.md`
- Выполнил пункт из roadmap? → проставить `[x]` в `../docs/roadmap.md`
- Появились новые термины? → обновить `../docs/glossary.md`
- Изменилась архитектура продукта? → обновить `../docs/architecture.md`
- Изменился API-контракт со стороны фронта (новое ожидание от бэка)? →
  обсудить с бэком, обновить `../docs/api-contract.md` совместно
- Изменились фронтенд-специфичные правила? → обновить файл в `docs/`

### Эволюция документов
Те же правила что в `backend/CLAUDE.md`. Кратко:
- `roadmap.md` — карта, а не рельсы; добавлять подзадачи свободно,
  удалять/переносить — обсуждать
- `gotchas.md` — добавлять свободно, старые не редактировать
- `decisions.md` — новые ADR свободно, старые не править. Заменять
  через новый ADR со статусом "заменяет ADR-N"
- `architecture.md` — изменять с осторожностью, серьёзные правки
  обсуждать
- `api-contract.md` — это совместный документ с бэком, изменения только
  при явном изменении контракта
- `progress.md` — только добавление новых записей сверху

### В конце каждой сессии (ОБЯЗАТЕЛЬНО)
Перед завершением создать запись в `../docs/progress.md`:

```markdown
## YYYY-MM-DD — Сессия N (frontend)
### Сделано
- конкретные выполненные задачи со ссылками на файлы/коммиты

### Решения
- принятые по ходу решения (если достойно ADR — также в decisions.md)

### Проблемы
- с чем столкнулись, как решили (если не решили — пометить "открыто")

### Следующий шаг
- конкретная следующая задача, чтобы новая сессия начала сразу с неё
```

**Помечать сессии префиксом `(frontend)`**, чтобы в общем журнале было
видно к какой части монорепы относится запись.

### При приближении к лимиту контекста
Если чувствуешь, что контекст заполняется — **не дожидаясь просьбы**
предложи пользователю сохранить состояние в `progress.md` максимально
подробно, включая все незавершённые мысли и следующий конкретный шаг.

## Соглашения по коду

### Общие правила
- **Язык:** комментарии и тексты UI на русском, имена компонентов,
  функций, переменных — на английском.
- **TypeScript strict mode** обязателен (`strict: true`,
  `noUncheckedIndexedAccess: true`).
- **Никаких `any`** — всегда явный тип или `unknown`.
- **Без enum'ов** — union literal types вместо них:
  `type NodeStatus = 'STANDING' | 'DISPUTED' | 'REFUTED' | 'UNVERIFIED'`.
- **Импорты:** абсолютные через alias `@` = `src/`. Не использовать
  длинные относительные пути типа `../../../components/ui/Button`.

### React
- Только функциональные компоненты, никаких class components.
- Хуки: `useState`, `useEffect`, `useCallback`, `useMemo`, `useRef`,
  кастомные `useXxx`.
- `useMemo` / `useCallback` — только при реальной проблеме
  производительности, не превентивно (YAGNI).
- Один компонент — один файл.
- Компонент > 100 строк — подумать о разделении.
- Props destructuring в параметрах функции:
  ```tsx
  function NodeCard({ node, onSelect }: Props) { ... }
  ```
- `key` в списках — UUID из данных, **не** индекс массива.

### CSS
- Только Tailwind utility classes. Никаких `.css`/`.scss` файлов
  отдельно.
- Если набор классов повторяется в 3+ местах — выделить компонент или
  использовать `cva` (class-variance-authority) для вариантов.

### Именование
- Компоненты: `PascalCase` (`TopicCard`, `ArgumentNode`, `GraphCanvas`).
- Хуки: `useXxx` (`useTopicGraph`, `useApiClient`).
- Сторы: `useXxxStore` (`useGraphStore`, `useTopicStore`).
- Типы / интерфейсы: `PascalCase` (`TopicResponse`, `GraphViewData`).
- Утилиты: `camelCase` (`formatDate`, `truncateContent`).
- Файлы компонентов: `PascalCase.tsx`.
- Файлы утилит / хуков / сторов: `camelCase.ts`.

### Структура папок
```
src/
├── api/          — сгенерированные типы из OpenAPI + fetch-обёртки
├── components/   — переиспользуемые UI-компоненты
│   ├── ui/       — базовые (Button, Input, Card, Modal)
│   └── graph/    — компоненты для React Flow (кастомные узлы, рёбра)
├── pages/        — страницы (TopicList, TopicGraph, CreateTopic)
├── stores/       — Zustand сторы
├── hooks/        — кастомные хуки
├── types/        — общие TypeScript типы
├── utils/        — утилиты
└── App.tsx       — корневой компонент с роутингом
```

### Тесты
- **Vitest** + **React Testing Library** + **MSW** для моков API.
- Тестировать поведение пользователя (`render`, `userEvent`, `screen`),
  не implementation details.
- Не тестировать стили / layout / pixel positions.
- Тесты компонентов в `*.test.tsx` рядом с компонентом.
- Утилиты — `*.test.ts` рядом.

## Локальная разработка

### Запуск Postgres + бэкенд
Postgres из корневого `docker-compose up`, бэкенд из `../backend/`
(`./mvnw spring-boot:run`). Смотри `../README.md`.

### Фронт
```bash
npm install
npm run dev          # dev server (Vite, HMR)
npm run build        # production build
npm run preview      # preview production build
npm run test         # тесты
npm run lint         # ESLint
npm run format       # Prettier
npm run generate-api # генерация типов из OpenAPI бэка
```

### Бэкенд API
- Base URL настраивается через env: `VITE_API_URL`
  (по умолчанию `http://localhost:9090`)
- Vite dev-сервер проксирует `/api/*` на бэк (настраивается в
  `vite.config.ts`)
- Типы генерируются из `/v3/api-docs` бэка через `openapi-typescript`
- Контракт зафиксирован в `../docs/api-contract.md` — сверяться при
  расхождениях; OpenAPI и контракт-документ должны совпадать

## Работа с задачами

Следуем task-driven подходу: каждая крупная задача оформляется как
запись в `../docs/roadmap.md` (Этап 7) с чек-листом подзадач. По мере
выполнения — отмечать.

## Что НЕ делать

- Не использовать `any`
- Не использовать class components
- Не писать отдельные CSS/SCSS файлы (только Tailwind utility classes)
- Не использовать TypeScript `enum` (union literal types вместо)
- Не хардкодить API URL — только через `VITE_API_URL`
- Не лезть в `../backend/` и корень репы без явного запроса
- Не превентивно `useMemo`/`useCallback` без замеренной проблемы
- Не использовать индекс массива как `key` в списках

## Git-коммиты

Используем Conventional Commits с scope для монорепы:
- `<тип>(frontend): описание`
- Типы: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`, `style`,
  `perf`, `build`, `ci`
- Примеры:
    - `feat(frontend): add topic graph page`
    - `fix(frontend): handle empty graph rendering`
    - `chore(frontend): bump react-flow to 12.x`
    - `docs: update ADR-009`
- Scope `(frontend)` обязателен для изменений в `frontend/`
- Scope не нужен для изменений в корне репы или общей документации
