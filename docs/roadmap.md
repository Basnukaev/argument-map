# Roadmap

Карта работ по проекту. Структура:
- Этапы 0-N - последовательность активных задач, выполняемых строго
  или почти строго по порядку. Полностью закрытые этапы остаются
  историей. Внутри этапа - плоский список пунктов; для full-stack
  этапов допустимы подразделы `Бэк`/`Фронт`
- `Cross-cutting / инфраструктура` - сквозные куски кода которые не
  привязаны к одному этапу (тосты, общие UI-компоненты). Каждый с
  пометкой "введено в этапе X"
- `Бэклог` - идеи и задачи без привязки к этапу: либо после-MVP,
  либо требуют сначала других фич. Когда созревает - переходит в
  активный этап или становится новым этапом

Правило для записи: микро-фикс (≤2 коммитов) остаётся только в
git log; средняя фича (3+ коммитов или новый файл/модуль) попадает
в roadmap (текущий этап, Cross-cutting или Бэклог по смыслу).

## Этап 0. Инициализация проекта

- [x] Сгенерировать Spring Boot проект (Spring Initializr): Java 21,
      Spring Boot 3.5.0, зависимости: Web, JDBC, Liquibase, PostgreSQL Driver,
      Testcontainers, Validation
- [x] Настроить `application.yml` (datasource, Liquibase, профили `local`/`test`)
- [x] Проверить, что приложение поднимается и Liquibase подключается к БД

## Этап 1. Схема БД (Liquibase)

Каждая миграция — отдельный changeset, автор `Abdula Basnukaev`.

- [x] `20260413-01-create-extensions` — `CREATE EXTENSION IF NOT EXISTS "uuid-ossp"`
- [x] `20260413-02-create-users-table` — пока минимальная (id, username, email)
- [x] `20260413-03-create-topics-table`
- [x] `20260413-04-create-nodes-table` + индексы по `topic_id`, `status`
- [x] `20260413-05-add-root-node-fk-to-topics` (циркулярный FK добавляем отдельно)
- [x] `20260413-06-create-edges-table` + индексы по `from_node_id`, `to_node_id`, `edge_type`
- [x] `20260413-07-create-sources-table` + GIN-индекс на `metadata`
- [x] `20260413-08-create-authorities-table`
- [x] `20260413-09-create-node-sources-table`
- [x] `20260413-10-create-node-authorities-table`
- [x] `20260413-11-create-revisions-table`
- [x] Интеграционный smoke-тест: Testcontainers поднимает Postgres, Liquibase
      прогоняет все миграции без ошибок (`ArgumentMapApplicationTests.contextLoads`)

## Этап 2. Доменная модель и репозитории

- [x] Java records для всех сущностей (`Topic`, `Node`, `Edge`, `Source`,
      `Authority`, `NodeSource`, `NodeAuthority`, `Revision`)
- [x] Enum'ы: `NodeType`, `EdgeType`, `NodeStatus`, `SourceType`, `Stance`,
      `Reliability`
- [x] Репозитории на JDBC Template + RowMapper:
  - [x] `TopicRepository`
  - [x] `NodeRepository`
  - [x] `EdgeRepository`
  - [x] `SourceRepository`
  - [x] `AuthorityRepository`
  - [x] `NodeSourceRepository`
  - [x] `NodeAuthorityRepository`
  - [x] `RevisionRepository`
- [x] Интеграционные тесты на каждый репозиторий (CRUD)
- [x] Утилита `JdbcTimes` (конвертация `Instant ↔ OffsetDateTime` для
      TIMESTAMPTZ, см. gotcha в `gotchas.md`)
- [x] Привязка `maven-failsafe-plugin` в `pom.xml` (чтобы `./mvnw verify`
      запускал `*IT`-тесты)

## Этап 3. Бизнес-логика

- [x] `TopicService` — создание темы сразу с корневым вопросом (транзакционно)
- [x] `NodeService` — создание, редактирование (с записью в `revisions`), удаление
- [x] `EdgeService` — создание/удаление рёбер
- [x] `GraphService` — загрузка полного графа темы одним запросом
- [x] `StatusCalculationService` — MVP-алгоритм пересчёта статусов
      (см. `architecture.md`)
- [x] Тесты на каждый сервис, особенно на алгоритм пересчёта статусов
      (сценарии: простая поддержка, простое опровержение, цепочка,
      `INVALIDATES`, циклы)
- [x] Доменные исключения (`TopicNotFoundException`, `NodeNotFoundException`,
      `EdgeNotFoundException`, `InvalidEdgeException`)

## Этап 4. REST API

- [x] DTO + мапперы (ручные, без MapStruct — слишком мало маппинга)
- [x] Контроллеры (см. эскиз в `architecture.md`)
- [x] Глобальный `@ControllerAdvice` с Problem Details (RFC 7807)
- [x] Валидация входных DTO (`@Valid`, аннотации)
- [x] OpenAPI-спецификация через `springdoc-openapi`
- [x] Интеграционные тесты контроллеров через `MockMvc` + Testcontainers
- [x] `X-User-Id` заголовок через `@CurrentUser` + argument resolver
      (ADR-006)
- [x] `api-contract.md` — описаны все эндпоинты v1

## Этап 5. Справочники и поиск

- [x] `SourceService` + REST: CRUD, поиск по названию (`?q=...`)
- [x] `AuthorityService` + REST: CRUD, поиск по имени (`?q=...`)
- [x] Привязка источников и авторитетов к узлам через
      `NodeSourceService` / `NodeAuthorityService`
- [x] Бизнес-валидация: `reliability` только для `SourceType.HADITH`
      (`InvalidSourceException` → 422)
- [x] `api-contract.md` — заполнены секции Sources/Authorities/привязок

## Этап 6. Улучшения бэкенда (после MVP)

- [ ] Полнотекстовый поиск по содержимому узлов (Postgres `tsvector`)
- [ ] Реализация Dung's argumentation framework для продвинутого пересчёта
- [ ] Импорт/экспорт темы в JSON (для бэкапа и обмена)
- [ ] Аутентификация и авторизация (Spring Security, JWT)
- [ ] Голосование за вес аргументов

## Этап 7. Фронтенд - MVP графа

Появился как отдельная папка `frontend/` в корне репы (см. ADR-005).
Начат после стабилизации бэкенд-API (Этапы 4-5 завершены).

- [x] Выбрать фреймворк — **ADR-008** (React 19 + TypeScript + Vite)
- [x] Выбрать библиотеку визуализации графа — **ADR-009** (React Flow,
      `@xyflow/react`)
- [x] Создать `frontend/CLAUDE.md`, `frontend/docs/coding-standards.md`,
      `frontend/docs/ui-guidelines.md`
- [x] Инициализация проекта: Vite + React 19 + TypeScript strict,
      Tailwind v4, React Router v7, Zustand 5, ESLint 9 flat config,
      Prettier, Vitest 3 + RTL + jsdom + jest-dom + MSW
- [x] CORS-настройка на беке для dev (`app.cors.allowed-origins` в
      `application.yml`, `WebMvcConfig.addCorsMappings`)
- [x] Генерация TS-типов из OpenAPI бэка через `openapi-typescript`
      (`src/api/types.ts`, скрипт `npm run generate-api`)
- [x] `src/api/client.ts` — типизированный fetch-клиент с `X-User-Id`
      заголовком (ADR-006), парсингом Problem Details (RFC 7807) и
      классом `ApiError`
- [x] Страница `/topics` — список тем (`GET /api/v1/topics`,
      4 ViewState: loading / empty / list / error, карточки с title и
      датой создания)
- [x] Страница `/topics/new` — создание темы (`POST /api/v1/topics`,
      форма title/description/rootQuestion, валидация полей через
      `errors[]` Problem Details, redirect на `/topics/{newId}`)
- [x] Страница `/topics/{id}` — граф темы (`GET /api/v1/topics/{id}/graph`)
      на React Flow с базовыми узлами и рёбрами + загрузочные/error/empty
      состояния, MiniMap, Controls, fitView
- [x] Кастомные узлы (`src/components/graph/NodeCard.tsx`): карточки с
      цветом по статусу
      (`STANDING`/`DISPUTED`/`REFUTED`/`UNVERIFIED`), иконкой по типу
      (lucide-react: `CircleHelp`/`Megaphone`/`MessageSquareQuote`/
      `FileText`), контентом (truncate 150 символов с tooltip)
- [x] Кастомные рёбра (`src/components/graph/CustomEdge.tsx`):
      `SUPPORTS` (зелёный), `REFUTES` (красный), `INVALIDATES`
      (тёмно-красный жирный пунктир, ADR-007), `QUALIFIES` (синий),
      `RESPONDS_TO` (тонкий серый полупрозрачный). Bezier-путь, badge с
      подписью типа
- [x] Автолейаут через `dagre` (`src/utils/graphLayout.ts`,
      горизонтально LR, корень слева, `nodesep: 60, ranksep: 120`)
- [x] Добавление узла через модалку (`POST /api/v1/nodes`,
      `AddNodeModal.tsx`, кнопка "+ Узел" в toolbar)
- [x] Добавление связи через модалку (`POST /api/v1/edges`,
      `AddEdgeModal.tsx`, кнопка "+ Связь" disabled пока узлов <2)
- [x] Удаление узла и связи (`DELETE /api/v1/nodes/{id}`,
      `DELETE /api/v1/edges/{id}`, кнопка "Удалить (N)" в toolbar -
      рёбра удаляются первыми, потом узлы; 404 = already gone)
- [x] Боковая панель деталей узла (`NodeDetailsPanel.tsx`): открывается
      при выборе одного узла, показывает тип+эмодзи, бейдж статуса,
      полное содержание, метаданные (createdAt/updatedAt/author/id) и
      lazy-секцию "История изменений" через
      `GET /api/v1/nodes/{id}/revisions`
- [x] Редактирование контента узла (`PATCH /api/v1/nodes/{id}`,
      `apiPatchRaw` в client.ts, кнопка "Редактировать" в панели,
      onUpdated → refetch графа, сохранение selected по id чтобы
      панель не закрывалась)
- [ ] Базовый layout: header, footer (кроме страницы графа), общий
      контейнер (роутинг между страницами уже работает)

## Этап 8. Семантика связей и логическая валидация

Зачем: разрешено любое сочетание (fromNodeType, edgeType, toNodeType),
включая бессмысленные ("вопрос опровергает тезис"). Нужны логические
правила и более читаемая визуализация цепочек.

### Бэк
- [x] **Матрица допустимых пар** `(fromType, edgeType, toType)` -
      `EdgeSemantics` + валидация в `EdgeService.createEdge`. Нарушение
      → 422 `invalid-edge`. Юнит-тесты `EdgeSemanticsTest` (96
      динамических) + IT в `EdgeServiceIT`/`EdgeControllerIT`
- [x] **ADR-010** на семантику типов связей с матрицей и табличкой
      контекстных подписей
- [x] Дополнить `architecture.md` секцией "Семантика связей"

### Фронт
- [x] Та же матрица в `src/utils/edgeRules.ts` + `getAllowedEdgeTypes`
      / `isEdgeAllowed` / `getContextualEdgeLabel`. Фильтрация
      `edgeType` в `AddEdgeModal` под выбранную пару, заглушка "Эту
      пару узлов нельзя соединить" с упоминанием ADR-010
- [x] Контекстные подписи рёбер - `CustomEdge` использует
      `getContextualEdgeLabel(fromType, edgeType, toType)`
- [x] Эмодзи (📢❓💬📄) в селектах `AddEdgeModal` вместо `[CLAIM]`/
      `[QUESTION]` префиксов
- [x] Toggle "Подписи рёбер" в toolbar (Eye/EyeOff). Юникод-маркер
      ✓/✗/⊗/↳/↩ на бейдже остаётся всегда, текст подписи скрывается.
      Сохранение в localStorage (`argmap.showEdgeLabels`)

## Этап 9. Miro-подобный UX в графе

Зачем: текущий toolbar с модалками работает, но неудобен. В Miro
пользователь создаёт связи перетаскиванием, использует контекстные
меню и управляет z-order'ом.

- [x] **4 handles** на узле (top/right/bottom/left). 4 handle'а
      type='source', `connectionMode='loose'` - source↔source.
      Невидимые до hover на узел (group-hover в Tailwind), 12px
      визуально + 28px hit-area через ::before
- [x] **Drag-create ребра**: drag из handle → линия за курсором →
      drop на handle другого узла → проверка матрицы ADR-010 →
      AddEdgeModal с предзаполненными from/to (если разрешено).
      Запрещённая пара показывает toast.warning с указанием пары
      и ссылкой на ADR-010, модалка не открывается
- [x] **Контекстное меню (правый клик)** через универсальный
      `ContextMenu`:
      - на pane: "Создать узел здесь" (открывает AddNodeModal)
      - на узле: "Редактировать" (выделяет узел → панель деталей),
        "На передний план", "На задний план", "Удалить"
      - на ребре: "На передний план", "На задний план", "Удалить"
- [x] **Z-index управление** через контекстное меню. `zRef` =
      useRef({max, min}) - локально. После refetch сбрасывается
      к дефолту RF
- [x] **Сохранение позиций узлов после drag** - full-stack
      (ADR-012): миграция БД `pos_x`/`pos_y` (DOUBLE PRECISION
      nullable), PATCH `/api/v1/nodes/{id}` принимает opt
      `posX`+`posY` без revision, фронт `onNodeDragStop` → PATCH
      (оптимистично), `layoutGraph` mixed-режим: сохранённые as-is,
      fresh - столбцом справа
- [x] `elevateNodesOnSelect={false}` чтобы явный zIndex из
      контекстного меню не перебивался автоматическим elevate'ом
      RF при выделении

## Cross-cutting / инфраструктура

Сквозные куски кода которые не привязаны к одному этапу. Каждый с
пометкой "введено в этапе X".

- [x] **Modal** (`src/components/ui/Modal.tsx`) - на нативном
      `<dialog>` с focus trap, Escape, role=dialog. Введён в
      **этапе 7** для AddNodeModal/AddEdgeModal
- [x] **Toast-система** (`src/stores/toastStore.ts` +
      `src/components/ui/Toaster.tsx`) - Zustand-store с 4 типами
      (`error`/`warning`/`info`/`success`) и auto-dismiss. API:
      `toast.warning('...')` без хука, можно из любого callback.
      Введена в **этапе 9** как ответ на drag-create запрещённых
      пар, теперь общая инфраструктура для алертов
- [x] **ContextMenu** (`src/components/ui/ContextMenu.tsx`) -
      универсальный компонент для правых кликов с поддержкой
      header, danger-пунктов, иконок lucide. Введён в **этапе 9**
      для меню pane/node/edge

## Бэклог

Идеи и задачи без привязки к этапу. Когда задача созревает - переходит
в активный этап или становится новым этапом.

### Фронт
- [ ] Привязка источников/авторитетов к узлам через UI
      (`POST /api/v1/nodes/{id}/sources`, `/authorities`)
- [ ] Полнотекстовый поиск (когда появится на беке, Этап 6)
- [ ] Экспорт графа в PNG / SVG
- [ ] Тёмная тема
- [ ] Аутентификация (когда появится на беке, Этап 6)
- [ ] Локализация (i18n) при появлении второй локали
- [ ] **UI-полировка `AddEdgeModal`:** заменить нативные `<select>`
      на кастомный dropdown с lucide-иконками (CircleHelp /
      Megaphone / MessageSquareQuote / FileText). Сейчас эмодзи
      📢/💬 (Тезис/Довод) визуально близки, различаются только
      текстовой меткой. Также: цветовая индикация типа узла в
      опции, подсветка выбранной пары на самом графе
- [ ] **Smart edge routing** (опционально, если 4-handles + dagre
      мало) - elkjs или custom edge с pathfinding
- [x] **Сохранение `sourceHandle`/`targetHandle` для edge** -
      full-stack (ADR-013): миграция 14 (2 nullable VARCHAR(20)
      в `edges`), Edge модель/DTO/RowMapper, EdgeService.createEdge
      имеет перегрузку с handle параметрами. Фронт `onConnect`
      передаёт `connection.sourceHandle`/`targetHandle` в POST
      /edges, при рендере edge использует эти поля на верхнем
      уровне RF Edge. Реализовано в сессии 15 (F.a-c)
- [x] **Координаты при "Создать здесь"** из контекстного меню
      pane - сделано через `screenToFlowPosition` для конверсии
      viewport→flow + AddNodeModal делает PATCH с координатами
      после POST. POST /nodes на беке расширять не стал (ADR не
      нужен, два оптимистичных запроса работают). Реализовано в
      сессии 15 (`c09b6f5`)
- [ ] **Reconnect edges** - перетащить конец существующего ребра
      на другую точку handle. Два варианта реализации:
      - A: PATCH /api/v1/edges/{id} с full update (fromNodeId/
        toNodeId/edgeType/rationale/sourceHandle/targetHandle) +
        повторная валидация EdgeSemantics. Бэк-долг ~60 мин. Чище
        долгосрочно, нет гонок. Требует ADR-014
      - B: DELETE + POST на фронте в onReconnect. ~20 мин, без
        бэк-изменений. Минусы: id ребра меняется, теоретическая
        гонка refetch между DELETE и POST
- [ ] **Z-index full-stack persistence** для узлов и рёбер
      (миграция + поле + DTO + фронт). Сейчас локально, при refetch
      теряется. Делать только если станет критично - z-order между
      сессиями редко важен
- [ ] **Code-split TopicGraphPage через React.lazy** - bundle 552kB
      / gzip 180kB подбирается к 600kB. Lazy-импорт упасёт initial
      bundle до ~150kB

### Бэк
- [ ] Пагинация для GET-list эндпоинтов (`/sources`, `/authorities`) -
      пока не нужна, справочники маленькие
- [ ] Фильтрация `?type=`, `?reliability=`, `?era=`, `?madhab=` -
      пока есть только `?q=`
- [ ] **springdoc + @CurrentUser** правильно в OpenAPI - сейчас
      параметр `userId` показывается как query (gotcha в
      `gotchas.md`). Бэк-долг с этапа 4
