# Roadmap

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
- [ ] TODO после-MVP: пагинация для GET-list эндпоинтов
      (`/sources`, `/authorities`) — пока не нужна, справочники маленькие
- [ ] TODO после-MVP: фильтрация `?type=`, `?reliability=`, `?era=`,
      `?madhab=` — пока есть только `?q=`

## Этап 6. Улучшения (после MVP)

- [ ] Полнотекстовый поиск по содержимому узлов (Postgres `tsvector`)
- [ ] Реализация Dung's argumentation framework для продвинутого пересчёта
- [ ] Импорт/экспорт темы в JSON (для бэкапа и обмена)
- [ ] Аутентификация и авторизация (Spring Security, JWT)
- [ ] Голосование за вес аргументов

## Этап 7. Фронтенд

Появится как отдельная папка `frontend/` в корне репы (см. ADR-005).
Запускается после стабилизации бэкенд-API (Этапы 4-5 завершены —
`api-contract.md` v1 заполнен).

### Подготовка
- [x] Выбрать фреймворк — **ADR-008** (React 19 + TypeScript + Vite)
- [x] Выбрать библиотеку визуализации графа — **ADR-009** (React Flow,
      `@xyflow/react`)
- [x] Создать `frontend/CLAUDE.md`, `frontend/docs/coding-standards.md`,
      `frontend/docs/ui-guidelines.md`
- [x] Инициализация проекта: Vite + React 19 + TypeScript strict,
      Tailwind v4, React Router v7, Zustand 5, ESLint 9 flat config,
      Prettier, Vitest 3 + RTL + jsdom + jest-dom + MSW
- [x] CORS-настройка на беке для dev (`app.cors.allowed-origins` в
      `application.yml`, `WebMvcConfig.addCorsMappings`). Решено не
      делать Vite proxy - фронт ходит напрямую через `VITE_API_URL`,
      CORS на беке - идентично продакшну
- [x] Генерация TS-типов из OpenAPI бэка через `openapi-typescript`
      (`src/api/types.ts`, скрипт `npm run generate-api`)
- [ ] Базовый layout: header, footer (кроме страницы графа), общий
      контейнер (роутинг между страницами уже работает)

### MVP фронта
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
      `FileText`), контентом (truncate 150 символов с tooltip), весом
      (10-точечная диаграмма)
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
- [ ] Боковая панель деталей узла: контент, вес, источники, авторитеты,
      ревизии
- [ ] Редактирование контента узла (`PATCH /api/v1/nodes/{id}`)

### Позже (после-MVP фронта)
- [ ] Привязка источников / авторитетов к узлам через UI
      (`POST /api/v1/nodes/{id}/sources`, `/authorities`)
- [ ] Полнотекстовый поиск (когда появится на беке, Этап 6)
- [ ] Экспорт графа в PNG / SVG
- [ ] Тёмная тема
- [ ] Аутентификация (когда появится на беке, Этап 6)
- [ ] Локализация (i18n) при появлении второй локали

## Этап 8. Семантика связей и логическая валидация

Зачем: сейчас разрешено любое сочетание (fromNodeType, edgeType, toNodeType),
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
      (сделано в коммите 8c92e32)

### Фронт
- [x] Та же матрица в `src/utils/edgeRules.ts` + `getAllowedEdgeTypes`
      / `isEdgeAllowed` / `getContextualEdgeLabel`. Фильтрация
      `edgeType` в `AddEdgeModal` под выбранную пару, заглушка "Эту
      пару узлов нельзя соединить" с упоминанием ADR-010
- [x] Контекстные подписи рёбер - `CustomEdge` использует
      `getContextualEdgeLabel(fromType, edgeType, toType)`
- [x] Эмодзи (📢❓💬📄) в селектах `AddEdgeModal` вместо `[CLAIM]`/
      `[QUESTION]` префиксов. lucide-SVG в `<option>` использовать
      нельзя - эмодзи как первый шаг (custom dropdown - на потом если
      потребуется)
- [x] **Toggle "Подписи рёбер"** в toolbar (Eye/EyeOff). Юникод-маркер
      ✓/✗/⊗/↳/↩ на бейдже остаётся всегда, текст подписи скрывается.
      Сохранение в localStorage (`argmap.showEdgeLabels`)

## Этап 9. Miro-подобный UX в графе

Зачем: текущий toolbar с модалками работает, но неудобен. В Miro
пользователь создаёт связи перетаскиванием, использует контекстные
меню и управляет z-order'ом. Это ключевой UX продукта.

- [ ] **4 handles** на узле (top/right/bottom/left) вместо текущих 2
      (top/bottom). React Flow умеет автоматически выбирать handle'ы
      для красивого роутинга при >2
- [ ] **Drag-create ребра**: hover на узел показывает 4 точки `+`,
      drag из точки → линия за курсором → drop на другой узел →
      открывается AddEdgeModal с предзаполненными from/to (или сразу
      создаётся ребро SUPPORTS, тип меняется потом)
- [ ] **Контекстное меню (правый клик)**:
      - на pane: "Создать узел здесь" (с координатами)
      - на узле: "Редактировать", "Удалить", "На передний план",
        "На задний план"
      - на ребре: "Изменить тип", "Удалить"
- [ ] **Z-index управление**: `zIndex` на node/edge через контекстное
      меню. State `maxZIndex` инкрементируется при "на передний
      план", `minZIndex` декрементируется при "на задний"
- [ ] (опционально, если простого dagre-роутинга мало) **Smart edge
      routing** с обходом препятствий - либо `elkjs` вместо dagre,
      либо custom edge с pathfinding. Делается только если
      4 handles не дают приемлемого результата. После реализации -
      AddEdgeModal через toolbar остаётся только как запасной вариант
- [ ] Сохранять позиции узлов после drag - PATCH
      `/api/v1/nodes/{id}/position` (новый эндпоинт на беке) или
      просто PATCH `/api/v1/nodes/{id}` с новыми полями `posX`/`posY`.
      Сейчас при refetch dagre заново пересчитывает - пользовательский
      drag теряется
