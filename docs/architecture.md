# Архитектура

## Цель проекта

Построить инструмент для структурированного разбора сложных дискуссий.
Устные обсуждения страдают от типичных проблем: оппоненты забывают свои и
чужие аргументы, повторяются, теряют контекст того, что уже было опровергнуто.
Argument Map решает это через граф, где каждый тезис, аргумент и источник —
отдельный узел с явно обозначенными связями.

## Высокоуровневая архитектура

```
┌──────────────────────┐      ┌──────────────────┐      ┌──────────────┐
│  Frontend            │─────▶│   REST API       │─────▶│  PostgreSQL  │
│  React 19+React Flow │      │  Spring Boot     │      │   (граф)     │
└──────────────────────┘      └──────────────────┘      └──────────────┘
                                      │
                                      ├─ GraphService (обход графа, пересчёт статусов)
                                      ├─ NodeService / EdgeService
                                      ├─ SourceService (источники)
                                      └─ AuthorityService (учёные/эксперты)
```

API-first: бэкенд полностью самодостаточен и тестируем через REST.
Фронтенд - SPA на React 19 + Vite, визуализация графа через React
Flow (`@xyflow/react`). Подробности в `frontend/CLAUDE.md`.

## Ключевые принципы

### 1. Граф как данные

Граф хранится в двух таблицах: `nodes` и `edges`. Типизация — через
дискриминатор (колонка `node_type` / `edge_type`). Это даёт гибкость:
новый тип узла или связи добавляется без изменения схемы.

### 2. Трёхуровневая модель цитирования (ADR-002 + ADR-017)

`sources` и `authorities` — справочники, а не узлы графа:
- Один хадис цитируется в десятках тем → не хотим дублирования
- У учёного есть свой профиль (эпоха, мазхаб, биография), который не
  является "аргументом"
- Источники и авторитеты переиспользуемы и ищутся независимо от тем

К узлу привязывается **только** `Source` (через M:N `node_sources`).
`Authority` нормализованный справочник, ссылается из `sources.authority_id`
(M:1) — то есть труд знает своего автора, но напрямую к узлу автор не
крепится. Это снимает дублирование: «Ас-Суюти + книга X» и «Ас-Суюти как
авторитет» это один акт цитирования, а не два (см. ADR-017).

Иерархия (от справочников к привязке):
- `Authority` (учёный) — master data: имя, эра, мазхаб, био
- `Source` (труд) — `authority_id NULLABLE`, `sourceType`, `title`,
  `citation`, `reliability` (для HADITH), `metadata` (jsonb)
- `NodeSource` (привязка) — `quote` (точная цитата), `context`
  (как подкрепляет узел), `location` (страница / номер хадиса /
  сура:аят)

`QUESTION`-узлы по семантике не имеют ни источников, ни авторитетов —
вопрос это констатация проблемы, не утверждение, и stance к нему не
клеится. Бэк-валидация этого не вводит (либеральная по атомарным
операциям), фронт не показывает соответствующую секцию для
`nodeType == QUESTION`.

### 3. Мета-аргументы — обычные узлы

Пример из изначального обсуждения:
> "Сказанное в противовес учёному нерелевантно, потому что 6, 7"

Это обычный узел-аргумент, связанный ребром `INVALIDATES` с другим
аргументом. У мета-аргумента могут быть свои источники, свои
мета-мета-аргументы и т.д. Граф не ограничен глубиной.

### 4. Status

- **`status`** — результат обхода графа (`STANDING`, `DISPUTED`, `REFUTED`,
  `UNVERIFIED`). Вычисляется `StatusCalculationService` от структуры
  графа и статусов узлов. Это *выход* алгоритма.

> Поле `weight` (1-10) было в ранней версии, но удалено (ADR-011).
> Алгоритм пересчёта статуса оперирует только структурой графа,
> субъективные числа не имеют смысла без объективного критерия.
> В будущем (Stage 6) появится категориальная разметка для ARGUMENT/EVIDENCE
> ("факт", "мнение", "цитата") и/или voting от сообщества - они станут
> объективными входами в расчёт.

### 5. Семантика связей: матрица допустимых пар

Каждое ребро `(fromType, edgeType, toType)` должно соответствовать
матрице (см. ADR-010). Бэк отклоняет несоответствующие пары через 422
`invalid-edge`, фронт фильтрует варианты в UI.

| FROM \ TO | QUESTION | CLAIM | ARGUMENT | EVIDENCE |
|---|---|---|---|---|
| **QUESTION** | QUALIFIES | QUALIFIES | QUALIFIES | — |
| **CLAIM** | RESPONDS_TO | SUPPORTS, REFUTES, QUALIFIES | — | — |
| **ARGUMENT** | — | SUPPORTS, REFUTES | INVALIDATES | — |
| **EVIDENCE** | — | SUPPORTS, REFUTES | SUPPORTS, REFUTES, INVALIDATES | — |

Прочерк = пара запрещена. Семантика по ролям:
- `QUESTION` пассивен: только `QUALIFIES` других, или получает `RESPONDS_TO`
- `CLAIM` центральный: связывается с другими CLAIM, может отвечать на QUESTION
- `ARGUMENT` за/против CLAIM, мета-опровергаем через `INVALIDATES`
- `EVIDENCE` доказательная база: SUPPORTS/REFUTES → CLAIM/ARGUMENT,
  основание для `INVALIDATES` чужого аргумента

UI-подписи рёбер зависят от пары узлов (доказывает/поддерживает/
согласуется для SUPPORTS, опровергает/противоречит/несовместим для
REFUTES, и т.д.) - таблица в ADR-010.

### 6. История изменений

Каждое редактирование узла пишется в `revisions`. Это важно для темы,
где формулировка аргумента может эволюционировать, и нужно видеть,
кто и что менял.

### 7. Координаты узла на канвасе

`Node` хранит `posX`/`posY` (DOUBLE PRECISION nullable, миграция 13,
ADR-012). Layout графа в Miro-UX (этап 9) персистентный: drag-and-drop
на фронте сразу сохраняет координаты через `PATCH /api/v1/nodes/{id}`
с `posX`+`posY`. При следующей загрузке графа фронт уважает
сохранённые координаты.

Узлы без позиции (`null/null`) - новые, ещё не перетаскивались.
Фронт расставляет их через `dagre` (если все без позиций) или
столбцом справа от `max(posX)` (mixed-режим, если часть сохранена).

Layout общий для всех пользователей (как в коллаборативных Miro/
Figma на shared доске). Если потребуется персональный layout per-user
- отдельный ADR с таблицей `node_positions(node_id, user_id, pos_x,
pos_y)`. Сейчас не нужно - проект на MVP-стадии без авторизации.

## Доменные сущности

### Topic
Контейнер для одной дискуссии. Имеет корневой узел (`root_node_id`) — обычно
это `QUESTION`.

### Node
Полиморфный узел графа. Типы:
- `QUESTION` — корневой вопрос темы
- `CLAIM` — тезис/утверждение
- `ARGUMENT` — довод (поддерживающий или опровергающий)
- `EVIDENCE` — фактическое свидетельство (альтернатива — через связь с Source)

Поля: `content`, `status`, `created_by`, `created_at`, `updated_at`.
(Поле `weight` удалено - см. ADR-011)

### Edge
Типизированное направленное ребро. Типы:
- `SUPPORTS` — поддерживает
- `REFUTES` — опровергает
- `QUALIFIES` — уточняет/ограничивает применимость
- `INVALIDATES` — объявляет узел нерелевантным (мета-уровень)
- `RESPONDS_TO` — прямой ответ (для диалоговой структуры)

Опциональное поле `rationale` — короткое объяснение, почему эта связь
существует.

### Source
Справочник источников:
- `QURAN` (сура, аят)
- `HADITH` (сборник, номер, степень достоверности — sahih/hasan/daif)
- `BOOK` / `ARTICLE` / `URL`

Поля:
- `authorityId` (`NULLABLE`) — FK на `Authority` (автор труда). Пусто для
  `QURAN` и анонимных текстов
- `bookId` (`NULLABLE`) — FK на `lib_books`. Заполнен только для
  `sourceType=BOOK` (CHECK enforce'ит). Один Source per
  `(sourceType, bookId)` через `UNIQUE INDEX uq_sources_book_per_type`
  (one-source-per-book идемпотентность для citation flow). См. ADR-026
- `metadata` (jsonb) — для тип-специфичных данных

### Authority
Справочник учёных/экспертов. Поля: имя, биография, эпоха, мазхаб (для
исламского контекста), `metadata` (jsonb). К узлу не привязывается
напрямую — приходит через `Source.authorityId` (см. ADR-017).

### NodeSource
M:N связь узел↔источник. Поля:
- `quote` — точная цитата
- `context` — как эта цитата подкрепляет узел
- `location` — точное место в источнике (страница, том+страница,
  номер хадиса, сура:аят)

Раньше существовала параллельная связь `NodeAuthority` со `stance`
(`HOLDS`/`OPPOSES`/`NEUTRAL`) — удалена в ADR-017. Stance имплицитно
выражается направлением рёбер графа (`SUPPORTS`/`REFUTES`).

### Revision
История изменений содержимого узла: кто, когда, что было до, что стало.

## Алгоритм пересчёта статусов

**MVP-версия** (простая, жадная):

1. Узел без входящих "влияющих" рёбер (`SUPPORTS`/`REFUTES`/`INVALIDATES`)
   сохраняет текущий статус. Default — `UNVERIFIED` (проставляется при
   создании узла); ручная пометка статуса (потенциально на Этапе 6+)
   переживёт пересчёт, пока на узел никто не ссылается влияющим ребром.
2. `INVALIDATES` от `STANDING`-источника — жёсткий kill: цель → `REFUTED`
   безусловно, даже если у цели есть `STANDING` supports (см. ADR-007).
3. Узел с входящими `SUPPORTS`/`REFUTES` рёбрами:
   - есть `STANDING` supports и `STANDING` refutes → `DISPUTED`
   - есть `STANDING` supports, нет `STANDING` refutes → `STANDING`
   - нет `STANDING` supports, есть `STANDING` refutes → `REFUTED`
   - все источники не-`STANDING` (`REFUTED`/`UNVERIFIED`) → `UNVERIFIED`
4. `QUALIFIES` и `RESPONDS_TO` рёбра в алгоритм не входят (ADR-007).

`INVALIDATES` действует на ребро или узел как "жёсткое" опровержение,
сильнее чем `REFUTES`.

**Будущая версия:** реализация на основе Dung's argumentation framework
(grounded / preferred / stable extensions). Это классика формальной
теории аргументации, хорошо документирована.

Пересчёт запускается при:
- Создании/удалении ребра
- Изменении статуса связанного узла (каскадно)

Для MVP — синхронный пересчёт в рамках транзакции. Если граф станет
большим — вынести в асинхронную задачу.

## REST API (предварительный эскиз)

```
POST   /api/v1/topics                       — создать тему
GET    /api/v1/topics/{id}                  — получить тему с корневым узлом
GET    /api/v1/topics/{id}/graph            — получить весь граф темы

POST   /api/v1/nodes                        — создать узел
PATCH  /api/v1/nodes/{id}                   — обновить содержимое (→ revision)
DELETE /api/v1/nodes/{id}                   — удалить узел (каскад)

POST   /api/v1/edges                        — создать связь (→ пересчёт статусов)
DELETE /api/v1/edges/{id}                   — удалить связь

POST   /api/v1/sources                      — добавить источник в справочник
GET    /api/v1/sources?q=...                — поиск по справочнику
POST   /api/v1/nodes/{id}/sources           — привязать источник к узлу

POST   /api/v1/authorities                  — добавить учёного
GET    /api/v1/authorities?q=...            — поиск
POST   /api/v1/nodes/{id}/authorities       — привязать учёного к узлу

POST   /api/v1/library/books                — создать книгу
GET    /api/v1/library/books?q=&type=       — список книг с фильтрами
GET    /api/v1/library/books/{id}           — книга с деревом chapters
GET    /api/v1/library/books/{id}/pages     — постраничный список
GET    /api/v1/library/pages/{id}           — страница со всеми регионами
DELETE /api/v1/library/books/{id}           — удалить книгу (каскад)
```

Детальный OpenAPI-контракт — следующий шаг после Liquibase-миграции.



## Library - доменный пакет (Этап 14, ADR-019)

> **Source of truth:** полное описание library архитектуры -
> `architecture-platform.md`. Этот раздел - краткий обзор для
> argument-map контекста. При обновлениях library обновлять оба
> файла одновременно (D-02 audit) или вынести описание полностью
> в platform-файл с минимальной ссылкой здесь.

С Этапа 14 в проекте появился доменный пакет `library` - фундамент
платформы (см. `vision.md`). Кодовая структура:
`ru.basnukaev.argumentmap.library.{domain,repository,service,web}` -
изолирован от существующего argument-map кода.

Таблицы (миграция 16):
- `lib_books` - книги/труды/тексты с `book_type` discriminator
  (`QURAN`/`HADITH_COLLECTION`/`BOOK`/`ARTICLE`/`MANUSCRIPT`),
  опциональным `authority_id`, jsonb `metadata` с GIN-индексом
- `lib_chapters` - иерархия глав через self-FK `parent_chapter_id`,
  `start_page_number` (миграция 18) для кликабельной навигации
  chapter → page
- `lib_pages` - страницы с `text_content` и/или `image_url`,
  UNIQUE(`book_id`, `page_number`), CHECK `lib_pages_content_present`.
  Source-first нумерация (миграция 19, ADR-021): `printed_page TEXT`
  и `part TEXT` (маркер реального издания + том/juz' для multi-volume,
  что показывается пользователю), `pdf_page_number INTEGER` (физическая
  страница PDF оригинала, заполняется будущим этапом PDF integration).
  `page_number` остаётся internal counter для URL-state и navigation
  order
- `lib_image_regions` - регионы на скане с нормализованными
  координатами (0..1), CHECK `lib_image_regions_bounds`

Academic citation metadata (миграция 24, ADR-028):

- `lib_publishers (id, name UNIQUE, created_at)` - справочник издательств
- `lib_publication_places (id, name UNIQUE, created_at)` - справочник
  городов публикации
- `lib_muhaqqiqs (id, name UNIQUE, full_name, created_at)` - справочник
  редакторов тахкика
- `lib_books` расширена 6 полями: 3 FK на справочники (`muhaqqiq_id`,
  `publisher_id`, `publication_place_id` с `ON DELETE SET NULL`) +
  3 per-book скаляра (`edition_number`, `published_year_hijri`,
  `published_year_gregorian` с CHECK для sanity ranges)
- `authorities` расширена 2 полями: `full_name TEXT` (полное имя с
  куньей/насабом/нисбой) и `death_year_hijri INTEGER` для academic
  first-mention footnote

Citation response (ADR-028): `NodeSourceRepository.findByNodeIdWithLocation`
делает 9 LEFT JOIN (sources → lib_books → authorities → lib_muhaqqiqs/
lib_publishers/lib_publication_places + lib_pages для TEXT mode +
lib_image_regions + второй lib_pages для REGION mode), возвращает
structured `CitationDetail` (27 raw полей). DTO `CitationResponse`
содержит 8 nullable nested refs (authority/book/muhaqqiq/publisher/
publicationPlace/location/pdf/region) - frontend рисует каждый блок
отдельно с правильным RTL/naskh.

Rich text storage (ADR-039, Этап 17.0): `lib_pages` расширяется
nullable колонкой `formatted_content jsonb` (миграция 32) для
хранения **ProseMirror JSON** - результата работы Tiptap editor.
`text_content` остаётся для full-text search (через будущий
Elasticsearch, см. backlog) и для backward compat. При чтении: если
`formatted_content` not null - reader/editor работают с
ProseMirror-документом который может содержать custom nodes
(`HadithBox`, `AyahBox`, `Marginalia`, `Footnote`, `DecoratedHeading`,
`PageNumber`, `Tashkeel` mark, `ColorHighlight` mark); если null -
frontend оборачивает `text_content` в минимальный paragraph-doc
прозрачно. Никакой миграции для существующих Shamela ETL / PDFBox
импортов не требуется. OCR pipeline Этапа 17 заполняет
`formatted_content` через AI editing pass который размечает raw
OCR output как структурированный документ для красивого tahqiq-
рендера. Обоснование выбора Tiptap (vs Lexical / Slate / CKEditor /
TinyMCE) - в ADR-039.

REST под `/api/v1/library/*`. Cross-domain зависимости только через
service-фасад (например `BookService` валидирует `authority_id`
через существующий `AuthorityRepository` из argument-map-домена).

Архитектурные детали и обоснование решений - в
`architecture-platform.md`. Решение оформлено как ADR-019.

## Frontend (apps/ + shared/)

С Phase 2 cleanup marathon (Сессия 25, 2026-05-11) фронт перешёл на
структуру `apps/{argument-map,library,admin}/` + `shared/` под
ADR-018 platform pivot. Каждое app - self-contained: pages,
components, utils, hooks - не пересекаются между apps. Cross-app
зависимости только через `shared/`.

```
frontend/src/
  apps/
    argument-map/   - граф аргументации
      pages/        TopicListPage, TopicGraphPage, CreateTopicPage
      components/   graph/* (NodeCard, CustomEdge, NodeDetailsPanel,
                    Add*Modal, CompactMiniMap, NodeSelect)
      utils/        edgeRules, graphLayout, attachmentTokens
    library/        - библиотека книг shamela + PDF
      pages/        BookListPage, BookReaderPage
      components/   PdfViewer
    admin/          - админ-tooling
      pages/        AdminShamelaPage
  shared/
    api/            client.ts (apiGet/Post/Patch/Delete/Raw,
                    ApiError, formatApiError), types.ts (autogen)
    components/
      layout/       Header
      ui/           Button, Card, Modal, ContextMenu, Toaster,
                    StatusBadge, TypeChip, Badge, IconButton, Kbd
    stores/         toastStore
    utils/          designTokens
    types/          async.ts (AsyncState<T,E>)
  App.tsx, main.tsx, index.css, test-setup.ts
```

Когда придёт `apps/qa/` (Этап 19), он встанет естественно рядом с
существующими тремя - shared/ уже содержит всё необходимое для нового
app.

### Backend boundaries (после Phase 1 cleanup marathon)

Shamela ETL разнесён на specific responsibilities:

- `library/shamela/service/`
  - `ShamelaMasterSyncService` - syncMaster (каталог)
  - `ShamelaBookImportService` - importBook (страницы + заголовки)
  - `ShamelaWorkDirManager` - workdir lifecycle + sqlite lookup
  - `ShamelaToLibraryMapper` - orchestrator маппинга staging→domain
- `library/shamela/service/mapper/`
  - `ShamelaAuthorityResolver`, `ShamelaBookMetadataBuilder`,
    `ShamelaChapterMapper`, `ShamelaPageMapper`, `ShamelaMapperUtils`
- `library/shamela/repository/`
  - `ShamelaDaoSupport` - утилиты для 5 DAO (nullable setters/getters,
    BATCH_SIZE, sumAffected)
  - DAOs (Author, Book, Category, Page, Title, SyncState)

## Authentication (Этап 21.a, ADR-040)

Stateless JWT через Spring Security 6 + jjwt 0.12.6 (HS256).

```
┌──────────────┐   POST /auth/login          ┌────────────────┐
│ Frontend SPA │ ─────────────────────────▶  │ AuthController │
│              │ ◀────── 200 + access JWT ── │                │
│              │   + Set-Cookie refresh      └────────────────┘
│              │
│              │   Authorization:            ┌────────────────────────┐
│              │   Bearer <access>           │ JwtAuthenticationFilter│
│              │ ─────────────────────────▶  │ ↓ validates            │
│              │                             │ SecurityContextHolder  │
│              │                             │ ↓                      │
│              │                             │ @CurrentUser UUID      │
│              │                             │ (CurrentUserArgument-  │
│              │                             │  Resolver реад из      │
│              │                             │  SecurityContext)      │
└──────────────┘                             └────────────────────────┘
```

Package: `ru.basnukaev.argumentmap.auth/`
- `domain/` - `User` / `UserRole` / `AuthTokens` / `AuthenticatedUser` records
- `repository/UserRepository` - JDBC, case-insensitive email/username lookup
- `service/`
  - `JwtService` - generate/validate access+refresh, HS256 через jjwt
  - `AuthService` - login / refresh flow (BCrypt password verify)
  - `UserService` - register / lookup / enable-disable
- `web/`
  - `AuthController` - 5 endpoints (register / login / refresh / logout / me)
  - `dto/` - RegisterRequest / LoginRequest / AuthResponse / MeResponse
  - `security/`
    - `SecurityConfig` - SecurityFilterChain (STATELESS, CSRF off)
    - `JwtAuthenticationFilter` - Authorization: Bearer
    - `XUserIdAuthenticationFilter` - dev/test/local profile fallback
    - `JwtAuthenticationEntryPoint` - 401 Problem Details
- `DevUserSeeder` - admin@argumentmap.local / admin12345 для dev

Roles: `USER`/`ADMIN` (CHECK constraint + index). RBAC per-entity - см.
Permissions ниже (ADR-043).

Refresh token rotation - **no** в MVP (см. ADR-040 «Открытые вопросы»).
Refresh blacklist - **no** в MVP. Multiple sessions per user - допустимо.

## Permissions / Visibility model (Этап 22 + 22.c, ADR-043 + Amendment)

Per-entity authorization для topics и library books. Hybrid model:
visibility-enum в БД + M:N таблица членов для co-editing. Q&A
questions/answers - open discussion + author/admin guards (без
visibility model).

**Топология данных:**

```
topics                                  topic_members
┌──────────────────────────────┐        ┌──────────────────────────────┐
│ id                           │        │ id                           │
│ created_by  (owner UUID)     │ ←──────│ topic_id (FK CASCADE)        │
│ visibility  (CHECK 3 values) │        │ user_id  (FK CASCADE)        │
│ ...                          │        │ role     (CHECK MEMBER|EDITOR│
└──────────────────────────────┘        │ added_at / added_by          │
                                        │ UNIQUE (topic_id, user_id)   │
                                        └──────────────────────────────┘
```

**Матрица доступа (permission matrix):**

| visibility | role          | can read | can write | can manage members | can delete topic |
|------------|---------------|----------|-----------|--------------------|------------------|
| PRIVATE    | owner         | ✓        | ✓         | ✓                  | ✓                |
| PRIVATE    | other USER    | ✗        | ✗         | ✗                  | ✗                |
| SHARED     | owner         | ✓        | ✓         | ✓                  | ✓                |
| SHARED     | EDITOR member | ✓        | ✓         | ✗                  | ✗                |
| SHARED     | MEMBER member | ✓        | ✗         | ✗                  | ✗                |
| SHARED     | non-member    | ✗        | ✗         | ✗                  | ✗                |
| PUBLIC     | owner         | ✓        | ✓         | ✓                  | ✓                |
| PUBLIC     | EDITOR member | ✓        | ✓         | ✗                  | ✗                |
| PUBLIC     | other USER    | ✓        | ✗         | ✗                  | ✗                |
| any        | ADMIN         | ✓        | ✓         | ✓                  | ✓                |

**Где живут проверки:**

- `service/PermissionService` - `canReadTopic` / `canWriteTopic` /
  `isOwner` + `assertCanRead` / `assertCanWrite` / `assertIsOwner`
  (бросают `TopicAccessDeniedException` / `TopicWriteAccessDeniedException`)
- `service/TopicService.getTopic|deleteTopic|updateVisibility` -
  перегрузки с (userId, role) делают assert
- `service/NodeService.createNode|update*|deleteNode` - перегрузки с
  (userId, role) делают assertCanWrite на parent topic
- `service/EdgeService.createEdge|updateEdge|deleteEdge` - то же
- `service/TopicMemberService` - бизнес-логика add/list/update/remove
  членов
- `web/controller/*` - читают role из SecurityContext через
  `SecurityContextUtils.currentRole()` (helper - не вводим новый
  ArgumentResolver), передают в Service-методы
- `exception/GlobalExceptionHandler` - 403 Problem Details с topicId/
  userId в properties

**Почему ассерты в Service, а не Controller через @PreAuthorize:**

- логика переиспользуется в future GraphQL / CLI / scheduled jobs - один
  PermissionService на все каналы
- @PreAuthorize expressions с custom PermissionEvaluator сложно
  тестировать
- легче composability: `assertCanWrite` вызывается из
  `TopicMemberService.addMember` (тот же ассерт)

**Endpoints управления:**

- `PATCH /api/v1/topics/{id}/visibility` (owner)
- `POST/GET/PATCH/DELETE /api/v1/topics/{id}/members[/...]`

**Что отложено** (Этап 22.d):

- frontend UI для book members + visibility (зеркало 22.b
  TopicMembersModal/VisibilityRadioGroup для books)
- private Q&A (visibility model для questions/answers если возникнет
  use-case закрытых учёных групп)
- audit log (кто что менял когда + permission changes) - отдельная таблица

### Library books (ADR-043 Amendment, Этап 22.c)

Та же модель как topics с одним отличием:

| Аспект | Topics | Library books |
|--------|--------|---------------|
| Default visibility | **PRIVATE** | **PUBLIC** (open library) |
| Таблица членов | `topic_members` | `lib_book_members` |
| Owner column | `topics.created_by` | `lib_books.created_by` (уже NOT NULL с миграции 16) |
| Service | `TopicMemberService` | `BookMemberService` |
| Exception типы | `Topic*` (403 forbidden-topic-*) | `Book*` (403 forbidden-book-*) |

Default PUBLIC для books - shamela ETL загружает книги в batch и user
ожидает увидеть их сразу. Новые user-uploads через
`FileImportService.importPdf` → **PRIVATE** (user черновики приватны).

REST endpoints mirror topics:
- `GET/POST/PATCH/DELETE /api/v1/library/books/{id}` - все требуют
  X-User-Id для permission check
- `PATCH /api/v1/library/books/{id}/visibility` (owner only)
- `POST/GET/PATCH/DELETE /api/v1/library/books/{id}/members[/...]`

### Q&A author/admin guards (ADR-043 Amendment, Этап 22.c)

Questions/answers - **открытая дискуссия** (видны всем authenticated,
visibility model не добавляется). Mutating операции защищены только
guards в Service-слое:

- `QuestionService.updateQuestion/deleteQuestion(.., userId, role)` -
  автор (asked_by) или ADMIN
- `AnswerService.updateAnswer/deleteAnswer(.., userId, role)` -
  автор (author_id) или ADMIN
- 403 `forbidden-question-write` / `forbidden-answer-write` если не
  автор и не ADMIN

Когда понадобится private Q&A (закрытые группы учёных) - расширим
отдельной миграцией по тому же visibility/members паттерну (22.d).
