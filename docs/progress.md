# Журнал работы

Хронологический лог сессий. Новые записи — **сверху**.

Формат записи:
```
## YYYY-MM-DD — Сессия N
### Сделано
### Решения
### Проблемы
### Следующий шаг
```

---

## 2026-05-03 — Сессия 5 (backend) — REST API (Этап 4)

### Сделано
- Добавлена зависимость `springdoc-openapi-starter-webmvc-ui:2.8.0`
  в `pom.xml`. Spring Boot 3.5 совместим
- `GlobalExceptionHandler` (`@RestControllerAdvice`) с Problem Details
  (RFC 7807) для всех доменных исключений + Bean Validation +
  `DataIntegrityViolation`. Spring сам выставляет
  `Content-Type: application/problem+json`
- Новое исключение `MissingUserHeaderException` для невалидного
  / отсутствующего `X-User-Id`
- 9 DTO в `web/dto/`:
  - `CreateTopicRequest`, `TopicResponse`
  - `CreateNodeRequest`, `UpdateNodeRequest`, `NodeResponse`
  - `CreateEdgeRequest`, `EdgeResponse`
  - `RevisionResponse`, `GraphResponse`
- `DtoMappers` (`web/mapper/`) — статические методы маппинга
  `domain → DTO`, без MapStruct (объёма мало)
- `@CurrentUser` аннотация + `CurrentUserArgumentResolver` —
  читает `X-User-Id`, парсит UUID, инжектит в контроллерные методы.
  Существование пользователя не валидирует здесь — пускаем БД-FK
  поймать на write (→ 422)
- `WebMvcConfig` — регистрация резолвера в Spring MVC
- 3 контроллера в `web/controller/`:
  - `TopicController` — POST/GET/GET-one/DELETE/GET-graph
  - `NodeController` — POST/PATCH/DELETE/GET-revisions
  - `EdgeController` — POST/DELETE
- 4 интеграционных теста (29 тестов всего):
  - `TopicControllerIT` — 10 тестов
  - `NodeControllerIT` — 9 тестов
  - `EdgeControllerIT` — 7 тестов
  - `OpenApiIT` — 3 теста (доступность `/v3/api-docs` со всеми
    эндпоинтами; редирект `/swagger-ui.html`; загрузка
    `/swagger-ui/index.html`)
- `api-contract.md` обновлён v1 — описаны все эндпоинты + примеры
  запросов/ответов + список Problem Details type-кодов
- Все 104 теста проходят (`./mvnw verify`)

### Решения
- Маппинг через статический utility-класс `DtoMappers` — KISS, нет
  MapStruct (соглашение из roadmap "ручные, без MapStruct — слишком
  мало маппинга")
- `createdBy` в Response DTO — UUID, не вложенный объект `UserSummary`.
  Если фронту понадобится `username` — добавим `UserSummary` позже.
  KISS до явного use-case
- `@CurrentUser` + argument resolver вместо `@RequestHeader` на каждом
  методе — DRY. Параметр контроллера выглядит как `UUID userId`,
  без шумной аннотации заголовка
- В резолвере UUID не валидируется на существование в БД —
  FK-нарушение поймёт `INSERT` в репозитории, переведётся в 422 через
  `GlobalExceptionHandler`. Меньше круглых походов к БД, документировано
  в `api-contract.md`
- `ProblemDetail` из Spring Framework 6 — нативно поддержан Spring
  Boot 3, не нужны сторонние библиотеки. `setProperty("errors", ...)`
  для расширения тела `validation` ошибки
- Для `MethodArgumentNotValidException` написан кастомный обработчик
  — Spring Boot по умолчанию сам отвечает Problem Details, но без поля
  `errors[]`, которое требует api-design.md
- `DELETE` эндпоинты не требуют `X-User-Id` — не нужно знать "кто",
  достаточно "что". Авторизация (Этап 6) добавит контроль "может ли
  этот юзер удалять"

### Проблемы
- Нет

### Следующий шаг
**Этап 5 из roadmap: справочники и поиск.**

Задачи по roadmap:
- `SourceService` + REST: CRUD, поиск по названию/типу
- `AuthorityService` + REST: CRUD, поиск по имени/эпохе/мазхабу
- Привязка источников и авторитетов к узлам через
  `NodeSourceService` / `NodeAuthorityService`

Эндпоинты по `architecture.md`:
- `POST /api/v1/sources` — добавить источник
- `GET /api/v1/sources?q=...` — поиск
- `POST /api/v1/nodes/{id}/sources` — привязать
- `POST /api/v1/authorities` / `GET /api/v1/authorities?q=...`
- `POST /api/v1/nodes/{id}/authorities` — привязать со `stance`

Уже готово на Этапе 2: `SourceRepository`, `AuthorityRepository`,
`NodeSourceRepository`, `NodeAuthorityRepository` — с `searchByTitle` /
`searchByName`. Нужны сервисы (тонкие, без сложной логики), DTO,
контроллеры.

### Важные нюансы для Этапа 5
- Поиск через `?q=...` (как зарезервировано в `api-design.md`)
- Пагинация по правилам `api-design.md` — offset-based с `page`/`size`
  / `sort`. Для MVP списки могут быть без пагинации (KISS), но
  посмотреть на объём — если очерёдно 1000+ источников, добавить
- `NodeSource`/`NodeAuthority` — composite-key, отдельные эндпоинты
  с двумя параметрами в URL (`/nodes/{nodeId}/sources/{sourceId}`)
- Метаданные `sources.metadata` (jsonb) — в DTO как `Map<String,
  Object>` или сырая строка JSON. Решить: `Object` (Jackson сам
  парсит/сериализует) проще для фронта
- Привязка источника к узлу — POST с телом `{quote, context}`
- `Reliability` enum (`SAHIH`/`HASAN`/`DAIF`) только для
  `SourceType.HADITH`. Валидация бизнес-правила в сервисе:
  `reliability != null` запрещён для не-`HADITH`

---

## 2026-05-03 — Сессия 4 (backend) — сервисный слой (Этап 3)

### Сделано
- Брейнсторм Этапа 3 → дизайн в
  `docs/superpowers/specs/2026-05-03-stage-3-services-design.md`
- 2 новых ADR:
  - **ADR-006** — `createdBy` через HTTP-заголовок `X-User-Id` до
    появления Spring Security (Этап 6)
  - **ADR-007** — вклад типов рёбер в алгоритм пересчёта статусов:
    `SUPPORTS`/`REFUTES` — обычные, `INVALIDATES` — kill-switch,
    `QUALIFIES`/`RESPONDS_TO` — не входят
- Уточнение правила 1 в `architecture.md`: узел без влияющих входящих
  рёбер сохраняет текущий статус (вместо принудительного `UNVERIFIED`).
  Это поддерживает будущую ручную пометку статуса и делает алгоритм
  устойчивым к сценарию "удалили последнее ребро" — статус не
  обнуляется, а отражает фактическое состояние графа
- 4 доменных исключения в `exception/`:
  `TopicNotFoundException`, `NodeNotFoundException`,
  `EdgeNotFoundException`, `InvalidEdgeException`
- 5 сервисов в `service/`:
  - `TopicService` — `createTopic` (создаёт root QUESTION
    транзакционно, обходя циркулярный FK), `getTopic`, `listTopics`,
    `deleteTopic`
  - `NodeService` — `createNode`, `updateContent` (пишет revision),
    `deleteNode` (триггерит recalc), `getRevisions`
  - `EdgeService` — `createEdge` (валидация self-loop / cross-topic,
    триггерит recalc), `deleteEdge` (триггерит recalc)
  - `GraphService` — `getGraph(topicId) → GraphView{topic, nodes,
    edges}` (плоская форма, как у graph-библиотек React Flow / Cytoscape)
  - `StatusCalculationService` — фикспоинт-итерация в памяти, в БД
    пишутся только дельты, `MAX_ITERATIONS = max(20, nodes*2)`
- `GraphView` record (`service/GraphView.java`)
- 75 тестов всего (было 46 после Этапа 2):
  - `StatusCalculationServiceTest` — 14 unit-тестов (моки), все
    сценарии из testing-strategy.md
  - `StatusCalculationServiceIT` — 3 интеграционных
  - `TopicServiceIT` — 6
  - `NodeServiceIT` — 9 (включая recalc через `deleteNode`)
  - `EdgeServiceIT` — 9 (включая recalc через `createEdge`/`deleteEdge`,
    cross-topic, self-loop)
  - `GraphServiceIT` — 3
- Все коммиты по смыслу (5 коммитов на этап)
- Сохранена feedback-память в
  `~/.claude/projects/.../memory/feedback_decision_authority.md` —
  правило "решаю сам, спрашиваю только при дилеммах; ADR только когда
  через месяц возникнет вопрос почему"

### Решения
- Дизайн зафиксирован в spec-документе со ссылками на ADR-006/007
- Транзакционность: `@Transactional` строго на сервисах, не на
  репозиториях/контроллерах. `StatusCalculationService` без аннотации
  (присоединяется к транзакции вызывающего)
- `TopicService.createTopic` пишет root-узел через `NodeRepository`
  напрямую (а не через `NodeService.createNode`), потому что
  `NodeService` валидирует "тема существует", а тема ещё в незакоммиченной
  транзакции
- `EdgeService.deleteEdge` извлекает `topicId` через
  `nodeRepository.findById(existing.fromNodeId())` до удаления —
  иначе после удаления неоткуда взять topicId для пересчёта
- `NodeService.deleteNode` аналогично — `findById` до `deleteById`
- Алгоритм статусов: фикспоинт по графу в памяти, batch-update в БД
  только дельт. `INVALIDATES` от STANDING-источника = kill (REFUTED
  безусловно, бьёт STANDING supports). `QUALIFIES`/`RESPONDS_TO` —
  не влияют

### Проблемы
- В первой версии алгоритма "узел без влияющих рёбер → UNVERIFIED"
  ломал тесты с STANDING-источниками: алгоритм сбрасывал источник в
  UNVERIFIED, и цепочка не работала. Решено: уточнено правило 1 в
  `architecture.md` — узел без влияющих рёбер сохраняет статус. Это
  совместимо с буквой "пока не оценён" из оригинального правила и
  открывает дорогу к будущей ручной пометке (Этап 6+)
- Spring DI требовал явного добавления `StatusCalculationService` в
  конструкторы `EdgeService`/`NodeService` на шаге 6 — не упало, но
  потребовало внимательности с порядком реализации (сначала SCS,
  потом подключение)

### Следующий шаг
**Этап 4 из roadmap: REST API.**

Задачи по roadmap:
- DTO + ручные мапперы (без MapStruct — слишком мало маппинга по
  ADR-неявному соглашению Этапа 4)
- Контроллеры по эскизу из `architecture.md`:
  - `POST /api/v1/topics` → `TopicService.createTopic`
  - `GET /api/v1/topics`, `GET /api/v1/topics/{id}`,
    `DELETE /api/v1/topics/{id}`
  - `GET /api/v1/topics/{id}/graph` → `GraphService.getGraph`
  - `POST /api/v1/nodes`, `PATCH /api/v1/nodes/{id}`,
    `DELETE /api/v1/nodes/{id}`, `GET /api/v1/nodes/{id}/revisions`
  - `POST /api/v1/edges`, `DELETE /api/v1/edges/{id}`
- Глобальный `@ControllerAdvice` с маппингом доменных исключений на
  HTTP-коды:
  - `*NotFoundException` → 404
  - `InvalidEdgeException` → 422
  - `DataIntegrityViolationException` → 422 (FK нарушения)
  - `MethodArgumentNotValidException` → 400 (Bean Validation)
- Bean Validation на DTO через `@Valid`/`@NotNull`/`@NotBlank`/`@Size`
- OpenAPI-спецификация через `springdoc-openapi` (надо добавить
  зависимость в pom.xml — обсудить перед добавлением, см. CLAUDE.md
  "Не добавлять зависимости без обсуждения")
- `X-User-Id` заголовок (ADR-006): извлечение в контроллере, валидация
  существования юзера в `users`, проброс UUID в сервис. Возможно через
  `HandlerMethodArgumentResolver` или `@RequestHeader` на каждом методе
  (обсудить)
- Интеграционные тесты контроллеров через `MockMvc` + Testcontainers

### Важные нюансы для Этапа 4
- `api-contract.md` обновлять синхронно с каждым новым эндпоинтом
- Имена JSON-полей — `camelCase` (Jackson default OK, но проверить)
- Не возвращать доменные `Node`/`Edge`/`Topic` напрямую — DTO
  (`NodeResponse`, `TopicResponse`, и т.д.)
- DTO-структура для `GraphView` — `GraphResponse{topic, nodes[], edges[]}`,
  плоская
- `Idempotency-Key` для POST не делаем (запланировано на потом)
- Пагинация для `GET /api/v1/topics` пока не нужна, но при появлении
  скриниться по правилам `api-design.md`

---

## 2026-04-20 — Сессия 3 (backend) — доменная модель и репозитории

### Сделано
- Enum'ы в `backend/src/main/java/ru/basnukaev/argumentmap/domain/`:
  `NodeType`, `EdgeType`, `NodeStatus`, `SourceType`, `Stance`, `Reliability`
- Java records (все иммутабельные, без Lombok):
  `Topic`, `Node`, `Edge`, `Source`, `Authority`, `NodeSource`, `NodeAuthority`,
  `Revision`. Timestamps — `Instant`, id — `UUID`
- JDBC-репозитории в `repository/`:
  - `TopicRepository` — save/findById/findAll/updateRootNodeId/deleteById
  - `NodeRepository` — save/findById/findByTopicId/update/updateStatus/deleteById
  - `EdgeRepository` — save/findById/findBy{From,To}NodeId/findByTopicId(JOIN)/deleteById
  - `SourceRepository` — CRUD + searchByTitle (ILIKE), metadata через `?::jsonb`
  - `AuthorityRepository` — CRUD + searchByName
  - `NodeSourceRepository` — save/findByIds/findByNodeId/findBySourceId/delete
  - `NodeAuthorityRepository` — аналогично со `stance`
  - `RevisionRepository` — save/findById/findByNodeId (без delete — журнал)
- Утилита `repository.JdbcTimes` — конвертация `Instant ↔ OffsetDateTime`
  для колонок `TIMESTAMPTZ` (см. gotcha)
- Интеграционные тесты на каждый репозиторий (`*IT.java`), Testcontainers
  Postgres 16, `@Transactional` + rollback. Фикстуры через
  `jdbcTemplate.update(...)`, не через тестируемый репозиторий
  (testing-strategy.md). Всего 45 тестов, `./mvnw verify` — зелёные
- Привязка `maven-failsafe-plugin` в `pom.xml` — без неё `verify` не
  запускал `*IT`-тесты (объявление есть в Spring Boot parent, но только
  в `pluginManagement`)
- `TestcontainersConfiguration` сделан `public`, чтобы импортировать
  из под-пакета `repository`
- Добавлены 2 gotcha в `docs/gotchas.md`:
  1. PG JDBC не выводит SQL-тип для `Instant` (нужен `OffsetDateTime`)
  2. Failsafe plugin в Spring Boot parent требует явного `<execution>`

### Решения
- **Контракт `save(T)`:** репозиторий принимает полностью заполненный
  record (id + timestamps). Генерация id и вычисление `Instant.now()` —
  ответственность сервисного слоя. Репозиторий остаётся тупым CRUD,
  тесты детерминированы (точные assertions по timestamp), политика
  генерации изолирована
- **Instant в доменных моделях, OffsetDateTime на границе с JDBC:**
  доменная модель не знает о JDBC-ограничениях. Конвертация вынесена
  в утилиту `JdbcTimes` рядом с репозиториями
- **jsonb через `?::jsonb` cast в SQL:** проще `PGobject`, работает
  для nullable значений, читабельно. Проверено тестом
  `metadataJsonb_isQueryableWithJsonbOperators` с оператором `@>`
- **Композитный PK у M:N таблиц:** `NodeSource` и `NodeAuthority` не
  имеют surrogate id. Методы `findByIds(a, b)` и `delete(a, b)` работают
  по паре ключей напрямую
- **`findByTopicId` у `EdgeRepository` — через JOIN `nodes`:** рёбра
  не содержат прямого `topic_id`, выбираются через `e.from_node_id =
  n.id`. Инвариант "ребро не пересекает границу темы" будет проверяться
  в `EdgeService` при создании (Этап 3)
- **`RevisionRepository` без `deleteById`:** revisions — исторический
  журнал, удалять только каскадно через удаление узла (что уже настроено
  в миграции 11). Принцип YAGNI
- **Reliability как enum (новый):** в roadmap не был в списке — добавил
  в том же духе, что остальные enum'ы, чтобы покрыть CHECK-ограничение
  `reliability IN ('SAHIH','HASAN','DAIF')`. Уже упоминался в прошлом
  progress (сессия 2)

### Проблемы
- `PSQLException: Can't infer the SQL type to use for an instance of
  java.time.Instant` — pgjdbc не маппит `Instant` через `setObject`
  без явного Types. Решено утилитой `JdbcTimes.odt(Instant)`
  (`OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)`). Записано в
  `gotchas.md`
- `./mvnw verify` не запускал `*IT`-тесты — Spring Boot parent объявляет
  Failsafe в `pluginManagement`, но не привязывает goal'ы. Решено
  явным `<execution>` в `pom.xml`. Записано в `gotchas.md`

### Следующий шаг
**Этап 3 из roadmap: бизнес-логика (сервисный слой).**

Задачи по roadmap:
- `TopicService` — создание темы с корневым вопросом транзакционно.
  Паттерн: создать `Topic` без `root_node_id`, создать `Node`
  (QUESTION), `topicRepository.updateRootNodeId(...)` — всё в одной
  транзакции (`@Transactional` на методе)
- `NodeService` — создание/редактирование/удаление, запись в `revisions`
  при каждом редактировании (`content_before` = старое, `content_after`
  = новое). Использовать `Instant.now()` для timestamps здесь
- `EdgeService` — создание/удаление рёбер. Валидация: оба узла в одной
  теме (инвариант, используемый в `EdgeRepository.findByTopicId`)
- `GraphService` — загрузка всего графа темы одним-двумя запросами
  (узлы темы + рёбра темы). Возвращает агрегат `{nodes, edges}`
- `StatusCalculationService` — MVP-алгоритм пересчёта из `architecture.md`:
  1. Без входящих рёбер → `UNVERIFIED`
  2. Supports все от `REFUTED` + есть `STANDING` refute → `REFUTED`
  3. Есть `STANDING` supports И `STANDING` refutes → `DISPUTED`
  4. Есть `STANDING` supports, нет `STANDING` refutes → `STANDING`
  5. `INVALIDATES` — жёстче `REFUTES`
- Тесты сервисов: unit с Mockito для мапперов/логики, integration через
  Testcontainers для транзакционности
- Особое внимание — fixture-графам для `StatusCalculationService` (см.
  testing-strategy.md): минимум 4 сценария + 4 граничных

### Важные нюансы для Этапа 3
- На сервисах — `@Transactional`, не на репозиториях и не на контроллерах
  (см. coding-standards.md)
- Не использовать `@Transactional(readOnly = true)` вперемешку с `true` —
  разделять явно
- Доменные исключения (`TopicNotFoundException`, `NodeNotFoundException`,
  `InvalidEdgeException`) — в пакете `ru.basnukaev.argumentmap.exception`
- Начать рекомендую с `TopicService` — самая простая операция-с-транзакцией,
  задаёт шаблон. Потом `NodeService`, потом `EdgeService`, потом
  `GraphService`, потом `StatusCalculationService` (самый сложный)

---

## 2026-04-20 — Сессия 2 (backend) — Liquibase-миграции схемы БД

### Сделано
- Создано 11 changeset-файлов в `backend/src/main/resources/db/changelog/changes/`:
  - `20260413-01-create-extensions.xml` — `uuid-ossp`
  - `20260413-02-create-users-table.xml` — минимальные `users` (id, username,
    email, created_at)
  - `20260413-03-create-topics-table.xml` — `topics` с `root_node_id` без FK
    (циркулярная зависимость topics↔nodes)
  - `20260413-04-create-nodes-table.xml` — `nodes` + CHECK на
    `node_type`/`status`/`weight`, индексы на `topic_id`, `status`, `created_by`
  - `20260413-05-add-root-node-fk-to-topics.xml` — замыкающий FK
    `topics.root_node_id → nodes.id ON DELETE SET NULL` + индекс
  - `20260413-06-create-edges-table.xml` — `edges` + CHECK на `edge_type`,
    индексы на `from_node_id`, `to_node_id`, `edge_type`, `created_by`
  - `20260413-07-create-sources-table.xml` — `sources` + `reliability` CHECK,
    GIN-индекс на `metadata`
  - `20260413-08-create-authorities-table.xml` — `authorities` + GIN на `metadata`,
    индексы на `name`, `era`, `madhab`
  - `20260413-09-create-node-sources-table.xml` — M:N с композитным PK + индекс
    на `source_id`
  - `20260413-10-create-node-authorities-table.xml` — M:N со `stance`
    CHECK + индекс на `authority_id`
  - `20260413-11-create-revisions-table.xml` — история изменений узлов
- Обновлён `db.changelog-master.xml` — `<include>` всех 11 файлов в порядке
  применения
- Smoke-тест `ArgumentMapApplicationTests.contextLoads()` проходит:
  Testcontainers поднимает Postgres 16-alpine, Liquibase прогоняет 11 changeset'ов
  (`Run: 11, Previously run: 0`), BUILD SUCCESS
- У каждого changeset'а прописан `<rollback>` (обратимость миграции)

### Решения
- Формат миграций: XML с raw `<sql>` внутри `<changeSet>`. Нативные теги
  Liquibase (`<createTable>` и т.п.) не используем — `<sql>` проще и лучше
  переносит CHECK constraints, GIN-индексы и композитные PK
- Циркулярный FK `topics.root_node_id → nodes.id` вынесен в отдельную
  миграцию 05 (см. gotchas.md)
- Enum'ы хранятся как `TEXT + CHECK` (см. antipatterns.md), значения uppercase
  для консистенции с Java enum (`.name()`)
- `reliability` в `sources` — uppercase `SAHIH/HASAN/DAIF` (в `er-diagram.md`
  было lowercase, но uppercase лучше ложится на Java-enum — уточнение
  документации будет в отдельном коммите при необходимости)
- Индексы на FK создаются в той же миграции, что и таблица (antipatterns.md)
- `ON DELETE CASCADE` — для дочерних сущностей (`nodes.topic_id`, `edges.*`,
  `node_sources.*`, `node_authorities.*`, `revisions.node_id`)
- `ON DELETE SET NULL` — для `topics.root_node_id` (удаление корневого узла
  не должно удалять тему)
- Все `timestamp` поля — `TIMESTAMPTZ` с `DEFAULT now()`

### Проблемы
- XML parse error в миграции 07: символ `&` в комментарии должен
  экранироваться (`&amp;`). Решено: переформулировал комментарий без
  спецсимволов. На будущее — или CDATA, или `&amp;` в XML-комментариях

### Следующий шаг
**Этап 2 из roadmap: доменная модель и репозитории.**

Ждём подтверждения пользователя перед стартом Этапа 2. Задачи этапа:
- Java records для всех сущностей (`Topic`, `Node`, `Edge`, `Source`,
  `Authority`, `NodeSource`, `NodeAuthority`, `Revision`)
- Enum'ы: `NodeType`, `EdgeType`, `NodeStatus`, `SourceType`, `Stance`,
  `Reliability` (SAHIH/HASAN/DAIF)
- JDBC Template репозитории с RowMapper'ами
- Интеграционные тесты на каждый репозиторий (CRUD), фикстуры через
  `jdbcTemplate.update(...)` (см. testing-strategy.md)

---

## 2026-04-20 — Сессия 1.5 (backend) — укрепление фундамента

### Сделано
- Создан `.editorconfig` в корне репы (единообразие отступов,
  окончания строк)
- Создан `.gitattributes` в корне репы + нормализация line endings
  (защита от CRLF/LF проблем на Windows+WSL)
- Установлен `spring.profiles.default: local` в application.yml
  (приложение стартует корректно из IDE и jar, не только из Maven)
- Добавлен `spring-boot-starter-actuator` в pom.xml
  (для /actuator/health и будущих метрик)
- Синхронизирован API-префикс `/api/v1/` в architecture.md
  (был `/api/`, расходился с api-design.md и api-contract.md)
- Добавлено примечание о порядке ADR в decisions.md
- Создан `docs/session-workflow.md` — компактный чек-лист сессии
- Создан `backend/docs/testing-strategy.md` — стратегия тестирования,
  включая подход к тестированию графовых обходов
- Создан `docs/git-workflow.md` — Conventional Commits, scope
  для монорепы, правила ветвления
- Создан `.github/workflows/README.md` — заготовка для будущего CI

### Решения
- Дефолтный профиль = local (чтобы не ломалось при запуске из IDE)
- Actuator добавлен сейчас, а не позже — документация уже ссылается на него
- Testing strategy зафиксирована до начала написания тестов

### Проблемы
- Нет

### Следующий шаг
**Этап 1 из roadmap: Liquibase-миграции схемы БД.**

Создать миграции по списку из roadmap:
1. `20260413-01-create-extensions` (uuid-ossp)
2. `20260413-02-create-users-table`
3. `20260413-03-create-topics-table`
4. `20260413-04-create-nodes-table` + индексы
5. `20260413-05-add-root-node-fk-to-topics` (циркулярный FK, см. gotchas.md)
6. `20260413-06-create-edges-table` + индексы
7. `20260413-07-create-sources-table` + GIN-индекс на metadata
8. `20260413-08-create-authorities-table`
9. `20260413-09-create-node-sources-table`
10. `20260413-10-create-node-authorities-table`
11. `20260413-11-create-revisions-table`
12. Smoke-тест: Testcontainers + Liquibase прогоняет все миграции

Автор всех changeset'ов: `Abdula Basnukaev`.
Формат: TEXT + CHECK constraints для enum'ов (см. antipatterns.md).
Индексы на FK — в той же миграции (см. antipatterns.md).
TIMESTAMPTZ, не TIMESTAMP (см. antipatterns.md).

---

## 2026-04-13 — Сессия 1 (backend)

### Сделано
- Установлены инструменты в WSL: OpenJDK 21.0.10, Maven 3.8.7
- Сгенерирован Spring Boot проект через Spring Initializr (версия 3.5.0):
  - `pom.xml` с зависимостями: web, jdbc, validation, liquibase, postgresql,
    testcontainers (включая `spring-boot-testcontainers` для `@ServiceConnection`)
  - Maven Wrapper (`mvnw`)
  - Главный класс `ArgumentMapApplication`
  - Тестовая конфигурация Testcontainers (`TestcontainersConfiguration`,
    `TestArgumentMapApplication`, `ArgumentMapApplicationTests`)
- Настроен `application.yml`:
  - Профиль `local` — подключение к Postgres из `docker-compose.yml`
  - Профиль `test` — заглушка, datasource через Testcontainers `@ServiceConnection`
  - Сервер на порту 9090 (8080 занят)
- Создан пустой `db.changelog-master.xml` с валидной структурой
- Создана папка `db/changelog/changes/` для будущих миграций
- Проверен успешный запуск: Tomcat на :9090, HikariPool подключился
  к Postgres, Liquibase прочитал changelog — "Database is up to date"
- Добавлен ADR-004 (Maven vs Gradle) в `decisions.md`
- Проставлены `[x]` на пунктах Этапа 0 в `roadmap.md`
- В `CLAUDE.md` добавлен раздел "Git-коммиты" с Conventional Commits

### Решения
- ADR-004: Maven вместо Gradle — привычный стек, совместимость с экосистемой
- Spring Boot 3.5.0 вместо 3.3.x — Initializr требует >=3.5.0 (3.3/3.4
  больше не поддерживаются на start.spring.io)
- Порт 9090 вместо дефолтного 8080 — порт 8080 занят на машине разработчика
- Добавлена зависимость `spring-boot-testcontainers` — идёт автоматически
  из Initializr при выборе Testcontainers, предоставляет `@ServiceConnection`

### Проблемы
- Java и Maven не были установлены в WSL — установлены через apt
- Spring Initializr больше не поддерживает Spring Boot 3.3/3.4 — использовали 3.5.0

### Следующий шаг
**Этап 1 из `roadmap.md`: Liquibase-миграции схемы БД.**

Создать миграции по списку из roadmap (extensions, users, topics, nodes,
edges, sources, authorities, node_sources, node_authorities, revisions).
Каждая миграция — отдельный файл в `src/main/resources/db/changelog/changes/`.
Smoke-тест через Testcontainers.

---

## 2026-04-13 — Сессия 0.5 (reorg → монорепа)

### Сделано
- Реорганизована структура проекта в монорепу с независимыми подпапками:
  - Корень: `README.md`, `docker-compose.yml`, `.gitignore`, `docs/` (общее)
  - `backend/` — Java/Spring Boot часть со своим `CLAUDE.md` и `docs/`
  - `frontend/` — появится на Этапе 7
- Документация разделена на общую (продуктовую) и специфичную для технологии:
  - Общее (`docs/`): architecture, er-diagram, glossary, roadmap, progress,
    decisions, gotchas, api-contract
  - Бэкенд (`backend/docs/`): coding-standards, antipatterns, api-design
- Создан `docs/api-contract.md` — пустой шаблон источника истины для
  контракта между беком и фронтом
- Добавлен **ADR-005** в `decisions.md` — решение о монорепе
- Расширен Этап 7 в `roadmap.md` — вместо заглушки полноценный план
  фронтенда (выбор фреймворка, библиотеки графов, подготовка, MVP)
- Обновлён `backend/CLAUDE.md`:
  - Добавлен раздел "Контекст: это монорепа" с правилами границ
  - Пути к общей документации через `../docs/`
  - Сессии помечаются префиксом `(backend)` в общем journal
- Создан корневой `README.md` с описанием структуры и принципов

### Решения
- ADR-005: монорепа с двумя независимыми папками, без специализированных
  инструментов (Nx/Turborepo). Простая модель, каждая часть независима.
- Claude Code запускается внутри подпапки (`cd backend && claude`),
  не в корне репы — читает свой локальный `CLAUDE.md`
- Сессии в общем `progress.md` помечаются префиксом `(backend)` / `(frontend)`
  для визуального разделения

### Проблемы
- Нет

### Следующий шаг
**Этап 0 из `docs/roadmap.md`: инициализация Spring Boot проекта.**

Важно: работать **внутри `backend/`**. Код Spring Boot проекта создаётся
в `backend/`, не в корне репы.

1. `cd backend`
2. Сгенерировать Maven-проект: Java 21, Spring Boot 3.3+, зависимости:
   `spring-boot-starter-web`, `spring-boot-starter-jdbc`, `liquibase-core`,
   `postgresql`, `spring-boot-starter-validation`, `spring-boot-starter-test`,
   `testcontainers`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`
3. Настроить `application.yml` с профилями `local` и `test`:
   - `local`: подключение к Postgres из корневого `docker-compose.yml`
     (`jdbc:postgresql://localhost:5432/argumentmap`, user/pass `argmap/argmap`)
   - `test`: заглушка, настоящая конфигурация Testcontainers появится на Этапе 1
4. Создать пустой `db.changelog-master.xml` с валидной структурой
5. Убедиться что `./mvnw spring-boot:run` поднимает приложение и Liquibase
   успешно подключается
6. Первый коммит: `chore(backend): initial spring boot project setup`

Также добавить ADR-004 (Maven vs Gradle) в `../docs/decisions.md`.

После Этапа 0 — переход к Этапу 1 (Liquibase-миграции схемы БД).

---

## 2026-04-13 — Сессия 0 (инициализация)

### Сделано
- Обсуждена идея проекта: API-first инструмент для argument mapping
- Выбран стек: Java 21, Spring Boot 3.3+, PostgreSQL 16, Liquibase, JDBC Template, Testcontainers
- Спроектирована архитектура и доменная модель (Topic, Node, Edge, Source, Authority, Revision)
- Создана полная документация проекта:
  - `CLAUDE.md` — конфиг для Claude Code
  - `docs/architecture.md`, `docs/er-diagram.md`, `docs/glossary.md` — архитектура и термины
  - `docs/roadmap.md` — план работ по этапам
  - `docs/decisions.md` — три первых ADR
  - `docs/gotchas.md` — шаблон + первые ловушки
  - `docs/progress.md` — журнал сессий (этот файл)
  - `docs/coding-standards.md` — принципы, SOLID, правила Java-кода, комментариев, тестов
  - `docs/antipatterns.md` — что не делаем в Java/SQL/REST
  - `docs/api-design.md` — правила дизайна REST API
- Настроен `docker-compose.yml` с Postgres 16

### Решения
- См. `docs/decisions.md`:
  - ADR-001: JDBC Template вместо JPA
  - ADR-002: Source и Authority как отдельные справочники, не узлы графа
  - ADR-003: Граф в двух таблицах (nodes + edges) с дискриминатором

### Проблемы
- Нет

### Следующий шаг
**Этап 0 из `docs/roadmap.md`: инициализация Spring Boot проекта.**

Конкретно:
1. Сгенерировать Maven-проект (Spring Initializr или вручную): Java 21,
   Spring Boot 3.3+, зависимости: `spring-boot-starter-web`,
   `spring-boot-starter-jdbc`, `liquibase-core`, `postgresql`,
   `spring-boot-starter-validation`, `spring-boot-starter-test`,
   `testcontainers`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`
2. Настроить `application.yml` с профилями `local` и `test`:
   - `local`: подключение к Postgres из `docker-compose.yml`
     (`jdbc:postgresql://localhost:5432/argumentmap`, user/pass `argmap/argmap`)
   - `test`: Testcontainers поднимает свой Postgres
3. Создать пустой `db.changelog-master.xml`
4. Убедиться что `./mvnw spring-boot:run` поднимает приложение и Liquibase
   успешно подключается (без миграций — это Этап 1)
5. Создать первый коммит: `chore: initial spring boot project setup`

После этого — переход к Этапу 1 (Liquibase-миграции схемы БД).
