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

> **Структурные изменения (Сессия 25):** после cleanup marathon
> frontend перенесён в `src/apps/{argument-map,library,admin}/` +
> `src/shared/` структуру (ADR-022). Backend разделён по
> responsibility: ShamelaImportService удалён, разнесён на
> MasterSyncService + BookImportService + WorkDirManager. DTO
> переименованы под `*Response` convention (B-04). Старые пути в
> закрытых этапах ниже могут ссылаться на pre-marathon структуру -
> это история, не актуально для текущих изменений.

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

Cross-cutting добавление: `frontend/src/apps/argument-map/utils/attachmentTokens.ts`
(после Сессии 25 apps/ reorg) - источник истины для
`SOURCE_TYPE_LABEL`/`ICON`/`HINT`/`ORDER`,
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
- [~] **13.c.2: author-picker в AddSourceModal create-mode** -
      **wontfix ADR-018**: устаревает с library, авторы будут
      резолвиться через `Book.authority` + общий CitationPicker,
      не ручной ввод
- [~] **13.d: пересоздать seed-мавлид под новую модель** -
      **wontfix ADR-018**: устаревает, при появлении library
      демо-данные пересоздадутся через library import
      (shamela parser) или ручной upload PDF
- [~] **13.e.2: финальная документация** - **wontfix ADR-018**:
      финальное обновление ui-guidelines под текущую форму «Цитаты»
      не нужно, потому что library pivot переориентирует UI вокруг
      book-citation. ui-guidelines обновится на Этапе 18 при
      переписывании argument-map citation-flow на CitationPicker

**Этап 13 закрыт частично-достаточно** (13.0/13.a/13.b/13.c.1).
Оставшиеся подэтапы устаревают с приходом library, см. ADR-018.

## Этап 14. Library MVP - доменная модель и базовые эндпоинты

**Зачем:** заложить фундамент платформы. См. `vision.md` и
`architecture-platform.md` (раздел Library).

- [x] **14.a: liquibase миграция 16** - добавить таблицы
      `lib_books`, `lib_chapters`, `lib_pages`, `lib_image_regions`
      с FK + индексами + CHECK constraints. ADR-019 формализован.
      163 IT зелёных через `./mvnw verify`
- [x] **14.b: доменные records + JDBC repositories** - `Book`,
      `Chapter`, `Page`, `ImageRegion` с RowMapper'ами, IT через
      Testcontainers (30 новых IT, всего 193 зелёных)
- [x] **14.c: BookService + REST**:
  - `POST /api/v1/library/books` - создать книгу с metadata
  - `GET /api/v1/library/books?q=&type=` - список/поиск/фильтр
  - `GET /api/v1/library/books/{id}` - книга с деревом chapters
  - `GET /api/v1/library/books/{id}/pages?from=&to=` - постраничный
    список (summary без content)
  - `GET /api/v1/library/pages/{id}` - конкретная страница с regions
  - `DELETE /api/v1/library/books/{id}` - удалить книгу (каскадно)
  - 14 ServiceIT + 17 ControllerIT, всего 225 IT в проекте.
    Curl smoke: POST/GET/DELETE/фильтры/404/OpenAPI работают
- [x] **14.d: ADR-019 на доменный пакет library** - принят в 14.a
      коммите вместе с миграцией. Полная формализация
      (architecture.md / api-contract.md / glossary.md) - в 14.d
      коммите следом

## Этап 15. Library - shamela импорт через desktop-API

**Зачем:** автоматический импорт классических трудов. Главный путь
расширения библиотеки.

**История пересмотра** (Сессия 21): первоначальный план был
HTML-парсинг shamela.ws через jsoup. Шесть попыток (curl, WebFetch,
flaresolverr v3.3.21/v3.4.6 с прокси и без, session-mode, прогрев)
показали что `shamela.ws/book/X` под агрессивным Cloudflare managed
challenge неразрешимым в текущей конфигурации. Параллельная сессия
выполнила mitmproxy-реверс desktop-клиента shamela 4 - получили
официальное API (6 endpoints, статический api_key). План
переписан на ETL через это API: см. ADR-020.

- [x] **15.1: миграция 17 + ADR-020 + architecture-platform.md** -
      `lib_shamela_category/author/book/page/title/sync_state`
      staging-таблицы. Двухслойная схема: staging (зеркало
      shamela API) + целевая модель `lib_books`/`Authority`
      (заполняется маппером)
- [x] **15.2: ShamelaApiClient + ShamelaArchiveExtractor** -
      `java.net.http.HttpClient` (4 метода), распаковка через
      `java.util.zip.ZipInputStream` с защитой от Zip Slip.
      Конфиг в `application.yml`: блок `shamela:`. `sqlite-jdbc 3.45.3.0`
      добавлен. `ShamelaHttpClientConfig` подхватывает HTTPS_PROXY
      из env с fix `jdk.http.auth.tunneling.disabledSchemes` для
      Basic-auth через CONNECT. Live-IT @Tag("live") (исключён из
      обычного verify) подтверждает работающий end-to-end pipeline
      через corporate-прокси: master-0-1261.zip скачан с PK-сигнатурой
- [x] **15.3: SQLite readers + DAO** - `ShamelaMasterReader` читает
      category/author/book.sqlite, `ShamelaBookReader` - {bookId}.sqlite
      (page+title). `SqliteValueParser` (null-safe TEXT→Long/Integer/
      Boolean, "99999"→null для года). 6 DAO с `ON CONFLICT(id)
      DO UPDATE` батчами 1000, JSONB через `?::jsonb` cast в SQL,
      composite PK для page/title. 84 теста (19 parser + 13 master
      reader + 9 book reader + 43 DAO IT)
- [x] **15.4: ShamelaImportService.syncMaster + importBook** -
      оркестрация ETL pipeline. `syncMaster()` читает
      `sync_state.master_version`, дёргает `fetchMasterMetadata`,
      пропускает download если version не изменилась, иначе скачивает
      master-zip (3 SQLite), распаковывает, читает, bulk-upsert в
      Category/Author/Book DAO, обновляет sync_state. Cleanup workdir
      в `finally`. `importBook(long)` находит book в `lib_shamela_book`,
      строит детерминированный URL `https://ready.shamela.ws/books-store/{id}-{major}.zip`,
      скачивает, читает page+title, bulk-upsert. Идемпотентность через
      `ON CONFLICT DO UPDATE` в DAO. `MasterSyncResult`/`BookImportResult`
      records, `ShamelaImportException` для ошибок уровня сервиса.
      Тесты: 6 IT с `@MockitoBean ShamelaApiClient` + Testcontainers
      postgres + fixture-zip собираются программно через
      `DriverManager(jdbc:sqlite:)`. `ShamelaImportServiceLiveIT`
      `@Tag("live")` для реальной shamela API. 274 IT зелёных
- [x] **15.5: ShamelaToLibraryMapper** - `shamela_book` → `lib_books`
      + `Authority` (резолвинг по нормализованному name с trim+collapse,
      fallback на anonymous Authority `shamela:anonymous` с
      if-not-exists). `shamela_title` (parent_id tree) → `lib_chapters`
      топологически через BFS, защита от orphan parent_id.
      `shamela_page.content` (raw HTML) → `lib_pages.text_content`,
      page_number = shamela_page.id, chapter_id = NULL на MVP, skip
      blank/whitespace. `lib_books.metadata` jsonb получает
      `{shamela_book_id, shamela_major_release, pdf_links}`,
      re-import detection через GIN-индекс на metadata. @Transactional
      на mapBook. Idempotent skip при re-import (защищает FK от
      node_sources). 10 IT через @SpringBootTest + Testcontainers
      без моков. Расширения existing repos: AuthorityRepository.findByName
      (exact match), BookRepository.findByShamelaBookId,
      ShamelaTitleDao.findAllByBookId, ShamelaPageDao.findAllByBookId.
      284 IT зелёных
- [x] **15.7: search + sync-status admin endpoints** - доделка 15.6
      под admin UI на фронте. `GET /api/v1/admin/shamela/search?q=&limit=`
      ищет в `lib_shamela_book` через ILIKE с обогащением (JOIN на
      author + EXISTS для isMapped через GIN-индекс на metadata).
      `GET /api/v1/admin/shamela/sync-status` отдаёт masterVersion,
      lastSyncedAt, counts для всех staging-таблиц + mappedBooksCount.
      Расширения repos: `ShamelaBookDao.searchByName(q, limit)` +
      `countAll()`, `ShamelaCategoryDao.countAll()`,
      `ShamelaAuthorDao.countAll()`, `BookRepository.countMappedFromShamela()`.
      6 новых IT через MockMvc + Testcontainers (search с реальной БД,
      sync-status, validation, tombstone exclusion, limit). 302 IT
      зелёных
- [x] **15.6: REST endpoints + финальная документация** - 3 admin
      endpoints под `/api/v1/admin/shamela/*`:
      `POST /sync-master` (вызов syncMaster),
      `POST /import-book/{id}` (вызов importBook),
      `POST /map-book/{id}` (вызов mapBook с @CurrentUser).
      `ShamelaAdminController` + 3 response-DTO + `ShamelaAdminMappers`.
      Расширен `GlobalExceptionHandler`: ApiException→502,
      Archive/Reader→500, ImportException→500, NotFound→404 (для
      cleanup-маппинга введён `ShamelaNotFoundException extends
      ShamelaImportException`). 12 IT через MockMvc + @MockitoBean.
      api-contract.md секция «Shamela Admin API» + 4 новых термина в
      glossary.md (staging, master-version, major/minor release,
      idempotent skip, anonymous Authority). PDF download endpoint
      и bulk endpoints отложены - см. «Что не реализовано» в
      api-contract.md. **296 IT зелёных**

## Этап 16. Library - PDF/EPUB upload

**Зачем:** второй способ добавления книг. Покрывает случаи когда
shamela не имеет нужной книги.

- [ ] **16.a: Apache Tika dependency** + `FileImportService` -
      извлечение текста и metadata из PDF/EPUB. Tika автодетектит
      формат
- [ ] **16.b: REST endpoint** `POST /api/v1/library/imports/file`
      multipart/form-data с PDF/EPUB файлом. Размер до 50MB
- [ ] **16.c: MinIO для хранения исходных файлов** - в
      docker-compose добавить MinIO. Загруженные PDF хранятся как
      attachment к Book для возможности re-extract или скачивания
- [ ] **16.d: PDF page-by-page extraction** - текст постранично,
      `Page.page_number` соответствует физической странице PDF
- [ ] **16.e: тесты** - IT с зафиксированными PDF/EPUB-фикстурами

## Этап 17. Library - image-сканы + OCR

**Зачем:** третий и самый сложный способ добавления книг. Для
сканов рукописей или редких книг где текст недоступен.

- [ ] **17.a: PageImageService** - upload изображений-страниц через
      `POST /api/v1/library/books/{id}/pages` (multipart, по одной
      странице за раз)
- [ ] **17.b: Tess4j integration** - OCR арабского через `ara`
      training data. `OcrService` извлекает текст из image, обновляет
      `Page.text_content`. Async через `@Async` + фоновый таск-runner
- [ ] **17.c: ImageRegion API** - `POST /api/v1/library/pages/{id}/regions`
      для создания выделенного региона (x/y/w/h + extracted_text).
      Фронт рисует прямоугольник, бэк сохраняет
- [ ] **17.d: re-OCR endpoint** - возможность перезапустить OCR
      для страницы (когда модель Tesseract обновится или нужен
      manual fix)
- [ ] **17.e: ADR на OCR pipeline** - выбор Tesseract, fallback на
      ручной ввод, точки расширения (Google Vision как option в
      будущем)

## Этап 18. Library frontend - читалка с цитированием + интеграция с argument-map

**Зачем:** пользовательский интерфейс библиотеки. Без этого все
backend-этапы не имеют смысла. Параллельно переключаем
argument-map на library citation вместо ручной формы.

**Архитектура - один SPA, не monorepo с apps/\*** (пересмотрено в
Сессии 23 после первой попытки реструктуризации). ADR-018 определяет
платформенный pivot как продуктовое видение, не как обязательную
физическую раскладку кода. Один `frontend/` с React Router, разные
разделы как разные `pages/`, общая навигация в header. Monorepo с
apps/* добавляется только когда возникнет конкретная потребность
(другая команда / разные домены / разный стек / огромный бандл).
См. Сессия 23 в progress.md для контекста решения.

- [~] **18.a (старое): monorepo реструктуризация** - **wontfix**,
      отменено в Сессии 23. Single-page подход в `frontend/` достаточен
- [x] **18.a (новое): AdminShamelaPage для импорта книг через UI**
      (Сессия 23 после фидбека "почему нет UI для импорта") -
      `/admin/shamela` со sync-status dashboard, live-search через
      `GET /admin/shamela/search` (debounce 300ms), карточками
      результатов с кнопкой Импортировать (последовательно
      `POST import-book` + `POST map-book` с toast feedback).
      RTL+naskh для арабских названий. Header расширен NavLink
      "Админ". setState через Promise tails, derived state для
      empty-query reset (правило react-hooks/set-state-in-effect)
- [x] **18.b: общий header с навигацией** - `components/layout/Header.tsx`
      извлечён из `TopicListPage`. NavLink на Темы / Библиотека / Q&A
      (placeholder). Реюзается во всех full-page разделах
- [x] **18.c: BookListPage** - `/books` страница. `BookSummary[]` через
      `GET /api/v1/library/books`, сетка карточек с title (RTL+naskh
      если `language="ar"`), badge bookType + языковой код, локальный
      поиск по title + фильтр bookType (5 типов + "все"). Empty state
      с инструкцией про admin endpoint
- [x] **18.h: миграция 19 + source-first нумерация страниц** -
      ADR-021. После UX-проверки Тафсира Ибн Касира выявлено что
      lib_pages.page_number = shamela_page.id (internal counter)
      не соответствует оригинальному изданию. Миграция 19 добавляет
      `printed_page TEXT` (маркер реальной книги "47"/"أ"),
      `part TEXT` (том/juz' "1"/"المقدمة"), `pdf_page_number INTEGER`
      (физ. страница PDF, NULL до этапа PDF integration). Index
      (book_id, part) для dropdown селектора томов. Mapper заполняет
      printed_page+part из shamela_page. PageRepository
      findDistinctPartsByBookId. PageSummary/PageResponse расширены.
      306 IT (+3 новых: save_withPrintedPageAndPart,
      findDistinctPartsByBookId, mapBook_persistsPrintedPageAndPart).
      Sub-chapters также починены - чисто frontend bug с
      double-tree-build (backend hierarchy через parent_chapter_id
      работала, фронт сбрасывал children из API). Springdoc-openapi
      теряет self-referential properties в schema - зафиксировано
      gotcha
- [x] **18.d: BookReader** - `/books/{id}` страница. Two-column layout:
      left side-panel (sticky 280px) с chapters tree (рекурсивный из
      flat ChapterResponse через `buildChapterTree` group-by-parent
      + topological sort, защита от orphan parent_id). Main area с
      book header (title naskh для арабского), pagination toolbar
      (prev / page X of Y / next), PageView через
      `dangerouslySetInnerHTML` (shamela HTML, sanitize TODO для
      Этапа 16). Loading state в event handlers (react-hooks/
      set-state-in-effect rule). Эвристика арабского текста через
      Unicode 0x0600-0x06FF
## Этап 25. PDF Viewer source-agnostic

**Зачем:** ADR-021 source-first - электронная версия должна
ссылаться на оригинал. Реализуется поэтапно (см. spec
`docs/superpowers/specs/2026-05-11-pdf-viewer-source-agnostic.md`):

- [x] **25.a: backend skeleton** - `PdfSourceProvider` interface +
      `PdfLinksSourceProvider` (читает metadata.pdf_links, покрывает
      shamela через archive.org CDN и future archive.org-direct) +
      `PdfService` роутер + 2 REST endpoints (`/info`, streaming с
      Range header). 7 IT через MockMvc + @MockitoBean.
      `PdfNotAvailableException` → 404 `pdf-not-available`.
      `filename` не возвращается клиенту (защита от обхода endpoint).
      Default chunk 1MB
- [ ] **25.b: object storage foundation** (ADR-024) - заменяет
      изначальный план "MinIO cache". Persistent S3-compatible storage
      с Postgres catalog. Не cache, а permanent storage с versioning.
      4 bucket'а по criticality: `library-imported-books`,
      `library-user-uploads`, `library-page-images`, `derived-artifacts`
  - [x] **25.b.1**: ADR-024 + architecture-platform.md секция
        "Object storage" + roadmap split на под-этапы. Docs-only коммит
  - [ ] **25.b.2**: Liquibase миграция 21 - таблица `library_files`
        (file_id, book_id, bucket, storage_key, source_url, source_type,
        content_hash SHA-256, size_bytes, etag, downloaded_at,
        last_verified_at, shamela_major_release, metadata jsonb,
        deleted_at). Repository + IT через Testcontainers
  - [ ] **25.b.3**: docker-compose MinIO сервис на pin'нутой версии
        `minio/minio:RELEASE.2025-07-23T15-54-02Z-cpuv1` + mc-init
        контейнер создающий 4 bucket'а с versioning ON. `application.yml`
        блок `storage:` + `ObjectStorageProperties`
        @ConfigurationProperties + `S3Client` Spring bean из AWS SDK v2
  - [ ] **25.b.4**: `ObjectStorageService` - API put/get/getRange/exists/
        delete (soft, через library_files.deleted_at). SHA-256 hash
        verification на каждый put и get. Unit-тесты на hash logic +
        IT через Testcontainers MinIO container
  - [ ] **25.b.5**: интеграция в `PdfLinksSourceProvider` - check
        catalog → если в bucket'е, return MinIO stream. Если нет -
        download upstream → put в `library-imported-books` + insert
        library_files. Existing tempDir cache удаляется
  - [ ] **25.b.6**: lazy Range streaming - chunks из MinIO напрямую
        через `getRange(start, end)`. AWS SDK v2 поддерживает Range
        на уровне `GetObjectRequest.range("bytes=...")`. Заменяет
        текущий full-download-then-serve паттерн в Provider'е
- [x] **25.c: react-pdf install + viewer** - npm install,
      worker setup в vite.config.ts, PdfViewer.tsx компонент,
      toggle 📃/📕 в reader (стиль по platform_reader.jsx PageToolbar).
      Реализовано в Сессии 24
- [x] **25.d.1: cover skip + multi-volume dropdown** (Сессия 26
      bug fix). `PdfFileInfo.isCover` boolean маркирует обложку
      (convention shamela/archive.org - first file при `cover: 1`).
      Frontend PdfViewer пропускает cover по умолчанию (выбирает
      первый не-cover файл), показывает dropdown селектор томов
      для multi-volume книг. Labels: арабские шамеловские
      (المقدمة) как есть; filename-like (`01_113015`) → "Том N".
      Fix bug'а - юзер видел 3 страницы cover вместо тысяч контента
- [ ] **25.d.2: text↔pdf page sync** - internal pageNumber →
      pdfPageNumber mapping с fallback на physical=internal если null.
      Требует Tier 1 admin page-mapping flow для заполнения
      `pdf_page_number` в `lib_pages`
- [ ] **25.d.3: PDF UX полировка** (после Сессии 26 фидбека Абдулы):
  - [x] **25.d.3.1: PDF download кнопка** - возможность скачать
        текущий PDF файл целиком (всегда мог через бэк, нужна
        UI-кнопка с `download` attribute)
  - [x] **25.d.3.2: page jump в PDF mode** - re-use `PageJump`
        компонент чтобы юзер мог сразу прыгнуть на pdf-страницу N,
        как в обычной читалке
  - [x] **25.d.3.3: loading flicker fix** - при prev/next в PDF
        не показывать «… Loading …» при каждом переходе (бросается
        в глаза). Сохранять предыдущую страницу пока новая грузится
        (PDF.js placeholder strategy)
  - [x] **25.d.3.4: chapters tree linies на правую сторону для RTL** -
        сейчас vertical depth rail слева, в RTL контексте логически
        неправильно (отступ должен начинаться справа от текста).
        Использовать `border-inline-start` (RTL-aware logical
        property) либо вообще убрать линии по примеру shamela
  - [x] **25.d.3.5: dropdown стиль из design-reference** - сейчас
        дефолтный `<select>`, не вписывается. Проверить как dropdown'ы
        выглядят в `design-reference/project/`, привести в соответствие
- [ ] **25.d.4: Inline PDF preview redesign (по shamela паттерну)** -
      кардинальное переустройство reader'а. Вместо tab toggle
      Text/PDF - кнопка PDF на каждой странице text mode, при клике
      открывается **inline preview** PDF этой страницы внизу (snapshot,
      не full reader). В preview - кнопка «развернуть на весь экран»
      → full PDF reader (как сейчас) с кнопкой «Назад к тексту» для
      возврата на ту же текстовую страницу. См. `shamela_page_view.png`
      + `after_click_on_pdf_icon_shamela.png` для UX-референса.
      Требует `pdfPageNumber` mapping (25.d.2 / 25.e) чтобы кнопка
      «📕 PDF» на каждой text-page знала какую PDF-страницу открыть
- [ ] **25.d.5: Lazy streaming через backend** - сейчас
      `PdfLinksSourceProvider.downloadFile` качает **весь PDF**
      (10-100MB) на наш бэк целиком при первом запросе. Потом отдаёт
      chunks с Range. Это значит первый PDF-клик ждёт 30-60 сек
      пока бэк скачает с archive.org. Lazy streaming: бэк форвардит
      Range-request frontend → archive.org, отдаёт chunks по мере
      получения. Trade-off: больше latency на каждый chunk, но
      первая страница за 1-2 сек. Заменяет временный in-process
      cache. Связано с ADR-023 (long-process миграция)
- [ ] **25.e: admin manual page-mapping** (Tier 1, опционально)
- [ ] **25.f: region selection** через react-image-crop +
      `POST /api/v1/library/pages/{id}/regions` (после CitationPicker
      18.f)

## Этап 18 (продолжение)

- [ ] **18.e: ImagePageRenderer** - отдельный mode для image-сканов:
      картинка + overlay для OCR-текста + рисование regions через
      react-image-crop. Релевантно после Этапа 17 OCR
- [ ] **18.f: CitationPicker** - переиспользуемый компонент.
      После Сессии 25 apps/ reorg - живёт в
      `frontend/src/shared/components/citation/CitationPicker.tsx`
      (shared между argument-map и Q&A apps). Выделил фрагмент в
      reader → opens picker → выбор приложения и контекста
      (какой узел / ответ)
- [ ] **18.g: Argument-map переключение на CitationPicker** -
      кнопка «Привязать цитату» в NodeDetailsPanel открывает
      CitationPicker. Старый AddSourceModal с ручной формой
      удаляется или становится fallback для свободных цитат

## Этап 19. Q&A - первое полностью новое приложение

**Зачем:** проверить платформенность фундамента. Если library
позволяет легко собрать новое приложение - архитектура работает.

- [ ] **19.a: бэкенд Q&A модуль** - `Question`, `Answer`,
      `AnswerCitation` сущности. Базовый CRUD
- [ ] **19.b: `apps/qa/` фронт** - страницы `/qa` (список вопросов),
      `/qa/{id}` (вопрос + ответы со ссылками)
- [ ] **19.c: интеграция с library через CitationPicker** - тот же
      компонент что в argument-map. Если работает - это валидация
      что фундамент правильный

## Этап 20+. Аутентификация и далее

После library + 2 приложения встаёт вопрос пользователей.

- [ ] **20: Spring Security + JWT** - реальная аутентификация
- [ ] **21: Многопользовательский режим** - private/shared/public
      visibility для тем, books, ответов
- [ ] **22+: Open list** - sanad explorer, multi-grading, RTL UI,
      экспорт PDF/SVG, mobile, advanced search

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

### Responsive / mobile-планшетная адаптация (будущая итерация)

UI сейчас спроектирован под desktop (виewport 1280+). При работе над
mobile/tablet нужно пересмотреть:

- **Select.maxVisibleItems** в `shared/components/ui/Select.tsx` -
  сейчас default 12 (без scrollbar при ≤12 опций). На мелком viewport
  или с большим zoom 12 опций могут не уместиться вертикально - получим
  overflow без scrollbar. Сделать adaptive: либо count меньше для
  small screens (через breakpoint hook), либо CSS-based max-height
  через `min(64rem, 50vh)` чтобы scrollbar appearance зависел от
  реальной высоты viewport, не от count
- **BookReaderPage layout** - двухколонник 280px sidebar + main
  сейчас. На mobile нужно либо drawer/sheet для chapters tree, либо
  bottom-tabs. PdfViewer внутри bottom-sheet (h-65vh) на mobile
  занимает весь экран - нужна другая UX flow
- **Sticky text toolbar** (Сессия 27) - sticky top-2 z-30 работает
  на desktop. Mobile: нужно учесть browser bottom address-bar
  collapsing, sticky может прыгать. Возможно `position: sticky`
  заменить на `position: fixed top-0` с padding на main
- **PdfViewer toolbar** - 6+ items в одну строку (prev/next + page
  input + zoom + download + PDF tab). На mobile нужно либо вынести
  в overflow menu, либо переключить на вертикальный stack

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
