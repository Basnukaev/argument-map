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

## Этап 10. Редактирование рёбер

Зачем: после этапа 9 рёбра можно создавать (drag-create / модалка) и
удалять, но менять их параметры было нельзя. Reconnect концов и edit
типа/обоснования - естественное расширение Miro UX.

- [x] **Reconnect edges** через `PATCH /api/v1/edges/{id}` (ADR-014).
      Перетаскивание конца ребра на другой handle/узел. Тип ребра
      сохраняется, валидация ADR-010 на новой паре. Optimistic update
      через `reconnectEdge` чтобы убрать flicker
- [x] **EdgeDetailsPanel** - аналог `NodeDetailsPanel` для рёбер.
      Открывается при выборе одного ребра, показывает превью from/to
      узлов, тип с контекстной подписью, обоснование, метаданные.
      Edit-режим: radio-buttons с допустимыми типами для пары
      (ADR-010), textarea для rationale, PATCH с только изменёнными
      полями. "Редактировать" в контекстном меню edge открывает
      панель сразу в edit-режиме (по аналогии с Node)

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

## Этап 11. Визуальная полировка по дизайн-референсу

Зачем: в `frontend/design-reference/` лежит handoff-бандл от
Claude Design - HTML/jsx прототип с проработанным визуалом всех
существующих компонентов (status-bar слева, TypeChip, StatusBadge,
градиентные header панелей, мини-граф в TopicCard и т.д.).
Стилизация без изменения функциональности или API.

- [x] **Подэтап 1: документация и токены** - ui-guidelines.md,
      glossary.md, decisions.md (ADR-015 status-bar), roadmap.md
- [x] **Подэтап 2: UI-примитивы** - Button (расширить), Badge,
      StatusBadge (с data-testid), TypeChip, Kbd, IconButton, Card,
      `src/utils/designTokens.ts`
- [x] **Подэтап 3: NodeCard** - status-bar слева, TypeChip+StatusBadge
      в header, line-clamp-2, hover/selected по тени, 4 handles
      сохранить
- [x] **Подэтап 4: CustomEdge** - выровнять цвета и стили под
      EDGE_TYPE_TOKENS из дизайна
- [x] **Подэтап 5: AddNodeModal + AddEdgeModal** - тип в grid-карточках,
      NodePicker для from/to, Kbd в footer
- [x] **Подэтап 6: NodeDetailsPanel + EdgeDetailsPanel** - градиент
      header, collapse-секции, diff-блоки в истории
- [x] **Подэтап 7а: бэк - nodeCount/edgeCount** - расширение
      TopicResponse, новый агрегатный SQL в TopicRepository,
      api-contract.md, ADR на расширение
- [x] **Подэтап 7b: TopicListPage** - topbar+нав, сетка карточек
      с мини-графом SVG, авторская аватарка, бейдж count, поиск
- [x] **Подэтап 8: GraphScreen layout** - левый вертикальный toolbar,
      floating легенда/zoom/hotkeys, breadcrumb в topbar

## Этап 12. Привязка источников и авторитетов к узлам через UI

Зачем: бэк-API готов с Этапа 5 (`POST /nodes/{id}/sources`, `/authorities`),
но в `NodeDetailsPanel` секции "Источники"/"Авторитеты" были placeholder.
Это центральная domain-фича проекта - исламская аргументация без шариатских
источников и мнений учёных бессмысленна.

- [x] **Подэтап 12.a: реальные секции "Источники"/"Авторитеты" в
      `NodeDetailsPanel`** - lazy-загрузка через `GET /nodes/{id}/sources`
      + параллельный `GET /sources` (справочник для матчинга), карточки
      источников (kind/title/citation/quote/context), строки авторитетов
      с avatar+stance бейджем, удаление через `DELETE` с optimistic-update.
      `PanelSection` расширен опциональным `onFirstOpen` callback
- [x] **Подэтап 12.b: `AddSourceModal` с поиском** - кнопка "Привязать
      источник" → модалка. Локальная фильтрация справочника (q-параметр
      бэка не используется на MVP-объёме). Опциональные поля `quote`/
      `context` при привязке. Conditional render модалки `{open &&
      <Modal/>}` - state свежий каждое открытие, без useEffect-сброса
- [x] **Подэтап 12.c: inline-создание нового Source** - кнопка "Создать
      новый источник" в той же модалке переключает в create-mode. Форма
      sourceType (5 вариантов в grid) + title + citation + reliability
      (показ только для `HADITH`). Submit делает 2 запроса последовательно:
      `POST /sources` → `POST /nodes/{id}/sources`. Фронт-валидация
      строже бэка - требует reliability для HADITH (бэк допускает null
      для legacy-импорта)
- [x] **Подэтап 12.d: `AddAuthorityModal` с stance + create** - симметрично
      sources, но stance (`HOLDS`/`OPPOSES`/`NEUTRAL`) обязателен при
      привязке. StancePicker - 3 кнопки с цветовым кодированием
      (emerald/red/slate). Create-form: name + era + madhab + bio
- [x] **Подэтап 12.e: документация** - этот раздел roadmap, обновление
      ui-guidelines (секции реализованы, новые компоненты), gotcha
      про `react-hooks/set-state-in-effect` + conditional render как
      идиома для модалок, запись в progress

Cross-cutting добавление: `frontend/src/utils/attachmentTokens.ts` -
источник истины для `SOURCE_TYPE_LABEL`/`ICON`/`HINT`/`ORDER`,
`STANCE_LABEL`/`BADGE_STYLES`/`RADIO_STYLES`/`ORDER`. Используется
панелью и обеими модалками. По аналогии с `designTokens.ts` для
node/edge.

## Этап 13. Адаптация фронта под трёхуровневую модель цитирования (ADR-017)

После Этапа 12 встал domain-вопрос «что показывать у разных типов
узлов». Эволюционировал в радикальное решение - объединить Source и
Authority под одной концепцией «цитата» (`Authority → Source →
NodeSource`). Бэк перестроен в Сессии 19 (backend) с миграцией 15
и удалением `node_authorities` / `Stance` / эндпоинтов
`/nodes/{id}/authorities`. Этот этап - адаптация фронта.

- [x] **13.0: инфраструктура** - рестарт бэка с миграцией 15, `./mvnw
      verify` (все IT pass), `npm run generate-api` регенерировал
      `types.ts` под новую схему. `Source.authorityId`,
      `NodeSource.location` пришли. `NodeAuthorityResponse`,
      `AttachAuthorityRequest`, enum `Stance` исчезли
- [x] **13.a: удаление AddAuthorityModal + чистка ссылок** - удалены
      `AddAuthorityModal.tsx`/`.test.tsx`, секция «Авторитеты» в
      `NodeDetailsPanel`, `STANCE_*` токены в `attachmentTokens.ts`,
      использования `NodeAuthorityDto`. -1031 строка
- [x] **13.b: секция Цитаты с трёхуровневой иерархией + скрытие для
      QUESTION** - переименование «Источники» → «Цитаты», условный
      рендер `{nodeType !== 'QUESTION' && ...}`, обогащённая карточка
      (автор через `Source.authorityId` + lookup в /authorities + чип
      эра/мазхаб; title + location в meta-строке; quote с
      RTL-detection через Unicode-диапазоны Arabic; context). 7 новых
      тестов
- [x] **13.c.1: поле location в AttachFields** - опциональное «Место
      в источнике» (до 200 символов) в обоих режимах модалки. Пустая
      строка конвертируется в undefined
- [ ] **13.c.2: author-picker в AddSourceModal create-mode** - выбор
      существующего `Authority` или inline-create нового с полями
      name/era/madhab/bio. `authorityId` передаётся в POST /sources.
      В минимальном виде: radio «Без автора / Из справочника /
      Создать нового» + dropdown в режиме «Из справочника» + мини-
      форма в режиме «Создать нового»
- [ ] **13.d: пересоздать seed-мавлид под новую модель** - в
      `scripts/seed-mawlid.sh` создавать `Authority`-сущности
      сначала (Ибн Хаджар, ас-Суюти, Ибн Таймия, Имам Малик и т.д.),
      затем `Source` с `authorityId`, привязки с `location`. Старая
      seed-логика (через node_authorities) удалена миграцией 15
- [ ] **13.e.2: финальная документация** - после 13.c.2 и 13.d
      обновить ui-guidelines под new card layout «Цитаты», запись в
      progress.md «Сессия 20 (frontend)» о завершении Этапа 13,
      сверка api-contract про location

## Бэклог

Идеи и задачи без привязки к этапу. Когда задача созревает - переходит
в активный этап или становится новым этапом.

### Фронт
- [x] **Привязка источников/авторитетов к узлам через UI** -
      реализовано в Этапе 12 (4 коммита feat + docs)
- [ ] Полнотекстовый поиск (когда появится на беке, Этап 6)
- [ ] Экспорт графа в PNG / SVG
- [ ] Тёмная тема
- [ ] Аутентификация (когда появится на беке, Этап 6)
- [ ] Локализация (i18n) при появлении второй локали
- [x] **UI-полировка radio-list типов** в AddNodeModal/AddEdgeModal/
      EdgeDetailsPanel/NodeDetailsPanel - lucide-иконки вместо
      эмодзи (📢/💬 → Megaphone/MessageSquareQuote и т.п.). Извлечён
      NODE_TYPE_META + EDGE_TYPE_META в edgeRules.ts, иконки
      совпадают с NodeCard и CustomEdge на графе. Реализовано в
      сессии 16
- [x] **Кастомный NodeSelect для выбора узла** - заменяет нативный
      `<select>` "Откуда"/"Куда" в AddEdgeModal. Триггер показывает
      lucide-иконку типа + content, dropdown с теми же опциями плюс
      цветной dot статуса узла. Закрывается по клику вне, Escape,
      выбору. excludeId фильтрует уже выбранный узел. Реализовано
      в сессии 16
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
- [ ] **Z-index full-stack persistence** для узлов и рёбер
      (миграция + поле + DTO + фронт). Сейчас локально, при refetch
      теряется. Делать только если станет критично - z-order между
      сессиями редко важен
- [x] **Code-split TopicGraphPage через React.lazy** - реализовано
      в сессии 16. Initial bundle упал с 567kB / gzip 183kB до 248kB
      / gzip 79kB (-2.3×). Граф (RF, dagre, графовые компоненты) -
      отдельный chunk 319kB / gzip 104kB, подгружается при переходе
      на `/topics/{id}`. Suspense fallback показывает "Загрузка графа"

### Будущие фичи (исламский контекст и расширения из дизайн-референса)

В `frontend/design-reference/project/islamic.jsx` и `extras.jsx`
дизайн показывает большое количество секций про работу с исламскими
текстами, sanad-цепочками, multi-grading и пр. Текущая итерация
визуальной полировки (Этап 11) их **не включает** - это спецификация
будущих этапов. Каждая секция здесь - заготовка под будущий ADR
и подэтап.

- [ ] **Привязка источников к узлам через UI** - модалка/picker
      выбора из справочника + привязка к узлу. Базовая
      инфраструктура есть (бэк: `POST /api/v1/nodes/{id}/sources`),
      нужна UI-часть. Минимум для MVP исламской работы
      _(из дизайн-референса: AddSourceContextMenu в `islamic.jsx`)_
- [ ] **Привязка авторитетов к узлам** - аналогично источникам.
      Будет показывать stance (HOLDS/OPPOSES/NEUTRAL) учёного на
      узел _(из дизайн-референса: AuthoritiesSection,
      AuthorityCard)_
- [ ] **Source picker для Корана** - таб "Коран" с навигацией по
      сурам, выбор аята, inline-вставка с цитатой и переводом.
      Бэк не готов: нужна интеграция с источниками типа
      quran.com или локальный mushaf-датасет _(SourcePickerQuran)_
- [ ] **Source picker для хадисов** - таб "Хадисы" с 9 сборниками
      (Бухари, Муслим, Тирмизи и т.д.), фильтр по grade
      (sahih/hasan/daif), показ иснада. Потенциальная интеграция
      с sunnah.com _(SourcePickerHadith)_
- [ ] **Source picker для книг** - таб "Книги" с навигацией том/
      страница, интеграция с shamela.ws. Самая большая работа
      из source pickers _(SourcePickerBooks)_
- [ ] **Source detail panel** - параллельная боковая панель
      (800px) с полным содержанием цитируемого источника,
      контекстом и метаданными _(SourceDetailPanel)_
- [ ] **Library overview** - страница `/library` с обзором
      источников темы _(LibraryOverview)_
- [ ] **Inline citations** - формат `[1]` в тексте с popover,
      привязанные к node-source records _(InlineCitations)_
- [ ] **Sanad explorer** - визуализация цепочки передатчиков
      хадиса (8-звенная от Пророка ﷺ до составителя). Каждое
      звено - карточка передатчика (имя/поколение/tier). Связи
      типизированы (`sama'`/`'an'ana`/`haddathana`/`мункати'`).
      Альтернативные пути. Серьёзная доменная фича - потребует
      расширения доменной модели (новые сущности `Rawi`, `Sanad`,
      `SanadLink`) _(SanadExplorer, SANAD demo data)_
- [ ] **Multi-grading хадисов** - один хадис может быть оценён
      несколькими учёными по-разному (Бухари: sahih, Тирмизи:
      hasan). Сейчас `Reliability` - single-value. Расширение
      на M:N таблицу `hadith_grades` (rawi/scholar/grade/source)
      _(MultiGradingSection, SCHOLAR_GRADES demo)_
- [ ] **Bilingual карточки** - двуязычный режим узла
      (EVIDENCE/ARGUMENT с арабским оригиналом + русским
      переводом). Toggle режима оригинал/перевод/оба. Требует
      RTL-поддержки и naskh-шрифтов _(BilingualNodeCard)_
- [ ] **Translator attribution** - при показе перевода аята/
      хадиса - указание переводчика (Кулиев, Sahih International,
      Османов и т.д.). Dropdown переключения переводов
      _(TranslatorSection)_
- [ ] **Tashkeel toggle** - на canvas карточки можно отключить
      огласовки (`harakat`) для краткости. Side-by-side
      сравнение с/без _(TashkeelSection)_
- [ ] **RTL-режим** - для арабского UI: зеркальный layout
      графа, RTL-toolbar, naskh-/kufi-шрифты. Большая работа,
      выделить в отдельный этап _(RTLGraphScreen, RTLSection)_
- [ ] **Language switcher (RU/EN/AR)** - в header или settings.
      Идёт в комплекте с i18n и RTL _(LanguageSwitcher)_
- [ ] **Settings screen** - язык, выбор арабского шрифта,
      размер текста, тогглы tashkeel/транслит, drag-приоритет
      источников _(SettingsScreen)_
- [ ] **Onboarding** - 4-шаговый чеклист для новой темы
      ("создай корневой вопрос", "добавь тезис-ответ" и т.д.) +
      hint-указатели на canvas _(OnboardingChecklist,
      OnboardingHint)_
- [ ] **Topic settings drawer** - 480px drawer над затемнённым
      canvas: title/desc, корневой вопрос (lock), радио
      Private/Shared/Public, метаданные, danger zone
      _(TopicSettingsDrawer)_. Требует расширения Topic на
      бэке полем `visibility` (после auth)
- [ ] **Multi-select с floating action bar** - лассо или
      Shift+click несколько узлов, всплывающая action-bar для
      массовых операций (изменить статус, переместить,
      удалить, экспорт) _(MultiSelectScreen)_
- [ ] **Cross-references drawer** - 600px drawer "узел
      использован в N темах": группировка по темам, прыжок в
      граф. Cross-topic graph-навигация. Требует backend
      аггрегата по cross-topic ссылкам _(CrossRefDrawer)_
- [ ] **Print preview** - A4-toolbar с тогглами (включить узлы,
      источники, иснады) + полноценная печатная страница темы.
      Граф как SVG, источники в академическом формате
      _(PrintPreviewSection)_

### Бэк
- [ ] Пагинация для GET-list эндпоинтов (`/sources`, `/authorities`) -
      пока не нужна, справочники маленькие
- [ ] Фильтрация `?type=`, `?reliability=`, `?era=`, `?madhab=` -
      пока есть только `?q=`
- [x] **springdoc + @CurrentUser** правильно в OpenAPI - реализовано
      в сессии 16 через `OperationCustomizer` в
      `config/OpenApiConfig.java`. Удаляет автогенерированный
      `query.userId` и добавляет `header X-User-Id` (required,
      uuid). После regen-api фронт получает правильную типизацию
      `parameters.header['X-User-Id']: string`. Гочча в `gotchas.md`
      помечена как Update/решённая
