# Backend - Claude Code config

Бэк-специфичные правила. Общие правила репо (стэк, layout,
команды, conventional commits) - в корневом `../CLAUDE.md`.
Правила документации - в `../docs/doc-hygiene.md`

## Контекст проекта

Платформа цифровых инструментов для исламских учёных и студентов
(см. `../docs/vision.md`). Бэкенд - один Spring Boot,
обслуживающий три приложения: argument-map (граф аргументации),
library (книги + цитирование), Q&A (планируется). API-first

Стратегическое решение - ADR-018 (platform pivot). Не «argument
mapping tool с исламским use-case», а **платформа** где
library - фундамент, argument-map / Q&A / будущие приложения
строятся поверх

Работа **только в пределах** `backend/`. Корень и `../frontend/`
не трогать без явного запроса

## Структура пакетов

```
ru.basnukaev.argumentmap/
├── config              Spring-конфигурация
├── domain              records предметной области
├── repository          JDBC-репозитории
├── service             бизнес-логика argument-map (TopicService и пр.)
├── web                 REST controllers + DTO + mappers
│   ├── controller
│   ├── dto
│   └── mapper
├── exception           кастомные исключения + GlobalExceptionHandler
└── library             library-домен
    ├── domain          Book / Chapter / Page / ImageRegion records
    ├── repository      BookRepository / ChapterRepository / etc
    ├── service         BookService и пр.
    ├── shamela         shamela ETL
    │   ├── api         ShamelaApiClient + dto
    │   ├── etl         readers + extractor + 6 staging DAO
    │   └── service     mapper (5 классов + DaoSupport)
    ├── pdf             PdfSourceProvider + PdfService + PdfController
    └── storage         ObjectStorageService + S3ClientConfig + MinIO
```

## После коммита - чек-лист документации

После **каждого** `feat`/`fix` коммита проверь:

| Что произошло | Что обновить |
|---|---|
| Закрыт пункт roadmap | `../docs/roadmap.md` `[x]` |
| Закрыт целый этап | `../docs/roadmap.md` - сжать в строку (см. `../docs/doc-hygiene.md` Принцип 3) |
| Принято решение между альтернативами | новый ADR в `../docs/decisions.md` |
| Миграция БД / новая колонка | ADR + `../docs/architecture.md` |
| Новый/изменённый REST endpoint, поле DTO | `../docs/api-contract.md` |
| Поймал баг через линтер/тесты/IT который может повториться | `../docs/gotchas.md` (симптом / причина / решение) |
| Новое доменное понятие | `../docs/glossary.md` |
| Изменились бэкенд-правила | `backend/docs/*` |

ADR / gotcha / api-contract пишутся **сразу**, не в конце сессии.
Принципы эволюции каждого документа - в `../docs/doc-hygiene.md`

### Триггеры для ADR

Почти наверняка нужен новый ADR если в коммите было:

- Выбор между ≥2 рассмотренных подходов (есть rejected
  alternatives)
- Изменение схемы БД (миграция Liquibase)
- Изменение контракта API (новое поле / эндпоинт)
- Решение «не делаем X сейчас, отложим до Y» (явный YAGNI)
- Введение новой инфраструктурной системы

### Триггеры для gotcha

Если что-то из этого ловили и потратили время:

- «Failsafe не запускает IT» / «Liquibase не применяет миграцию»
- Spring/Hibernate/JDBC ведёт себя не как ожидалось
- Странные ошибки типов в Java/Spring (`Instant` vs `OffsetDateTime`)
- Тесты ломаются от чего-то что выглядит несвязанным

Не должно быть так что фикс делается, gotcha не записан, через
две недели наступаем на тех же граблях

## Code review после крупных этапов (mandatory)

После каждого крупного этапа (закрытие Этапа N целиком либо
закрытие N подэтапов одной темы) **обязательно** вызвать
`/superpowers:requesting-code-review`:

- После закрытия Этапа целиком (миграция + domain + service + REST + IT)
- После каждых 5-7 атомарных commit'ов на одну тему
- Перед обновлением roadmap записи в «Закрытые этапы»
- Перед финальным handoff коммитом сессии

**Зачем:** reviewer ловит issues которые subagent мог пропустить -
subtle SQL bugs, missing permission checks, integer overflow,
inaccurate комментарии, race conditions, dead code, отложенные
issues не зафиксированные в backlog.

**Workflow:**
1. Закрылся этап → коммит-handoff `feat(backend): этап N closed`
2. Вызвать `/superpowers:requesting-code-review` с BASE/HEAD SHA
3. Прочитать reviewer's report - Critical/Important/Minor
4. Закрыть **все Critical** и **все Important** в отдельных fix-коммитах
5. **Только после этого** обновлять `roadmap.md` запись в «Закрытые»
   и делать handoff `docs: handoff Сессии N`

Если reviewer flag'нул Issue которое **намеренно** не делаем -
зафиксировать в `docs/backlog.md` либо в комментарии в коде с
обоснованием. Иначе reviewer flag'нет тот же Issue снова на
следующем цикле (см. как было с shared MinIO Testcontainer).

См. memory `feedback_no_self_context_tracking.md` - я не должен
сам решать когда останавливаться, но code review между крупными
этапами - часть workflow, не stopping point.

## Соглашения по Java/Spring

### Общие

- Все комментарии, логи, JavaDoc - на русском. Имена классов /
  методов / переменных - на английском
- JavaDoc только для нетривиальной логики - не ради JavaDoc
- Комментарии объясняют **почему**, не **что**. Если код
  самодокументируемый - комментарии не нужны
- Импорты вместо полных квалифицированных имён

### Liquibase

- Автор миграций всегда `Abdula Basnukaev`
- Формат changeset id: `YYYYMMDD-NN-short-description` (например,
  `20260413-01-create-topics-table`)
- Каждая миграция - отдельный файл в
  `src/main/resources/db/changelog/changes/`
- Мастер-файл: `src/main/resources/db/changelog/db.changelog-master.xml`
- `<rollback>` там где имеет смысл
- Индексы в той же миграции что и таблица, если очевидны
- Символ `&` в comment / SQL экранировать `&amp;` или оборачивать
  в `<![CDATA[ ... ]]>` (gotcha)

### База данных

- **Без JPA/Hibernate**. Только JDBC Template + ручной маппинг
  через `RowMapper`
- snake_case для таблиц и колонок
- Первичные ключи - UUID (`uuid` PostgreSQL)
- Timestamps - `timestamptz`, с дефолтом `now()` где уместно
- Soft delete только там где явно требуется. История изменений -
  через `revisions`

### REST API

Подробно - в `backend/docs/api-design.md` и `../docs/api-contract.md`

- DTO `*Request` / `*Response` (выбранная конвенция)
- Problem Details RFC 7807 через `@ControllerAdvice` глобально
- Валидация через Bean Validation (`@Valid` + аннотации)
- `@CurrentUser UUID userId` извлекается из SecurityContext (Bearer JWT
  через `JwtAuthenticationFilter`, или X-User-Id fallback в dev/test
  через `XUserIdAuthenticationFilter`). ADR-040 заменил ADR-006
  заглушку. API аннотации не изменилось

### Pagination + filters (GET-list endpoints)

Все GET-list endpoints возвращают `PagedResponse<T>` обёртку
(`web.dto.PagedResponse`) - не raw array. Контракт описан в
`../docs/api-contract.md` секция «Пагинация GET-list endpoints»

- **Helpers:** `PagedResponse<T>.of(items, page, size, total)` -
  computes totalPages/hasNext/hasPrev. `PageRequest.from(page, size)` -
  парсит query-params: default page=0, size=20, MAX_SIZE=100 (clamp).
  Negative/zero/null → default
- **Repository pattern:** для каждого list endpoint два метода -
  `findPage(filters..., int limit, int offset)` и
  `countFiltered(filters...)`. WHERE clause **один источник истины**
  через private `appendFilters(StringBuilder, List<Object>, filters...)`
  helper - чтобы count не разъезжался с select. Сортировка по умолчанию
  `created_at DESC` (новые сверху) - кроме authorities (`name ASC`
  для исторического порядка)
- **Service:** методы `listPage(filters..., int limit, int offset)` +
  `countFiltered(filters...)`. Валидация фильтров (enum-whitelist,
  combination rules типа reliability требует type=HADITH) - **здесь**,
  не в Repository. Невалидная комбинация → `IllegalArgumentException`
  (handler → 400), не `InvalidXyzException` (он бы дал 422 - тот
  семантический код для нарушения payload invariants)
- **Controller:** принимает `@RequestParam(required=false)
  Integer page, Integer size` + фильтры, конструирует через
  `PageRequest.from`, вызывает service дважды (items + count), мэппит,
  возвращает `PagedResponse.of()`. Старый `findAll()` в Repository
  сохраняем где используется internal callers (ETL/import/scheduled
  jobs) - они не нуждаются в pagination
- **Тесты:** IT кейсы для каждого endpoint - `paginated_returnsCorrectPage`,
  `sizeOverMax_clampsTo100` (где уместно), `filterBy*_returnsOnlyMatching`,
  `invalidFilterCombo_returns400` (где есть combination rules)
- **Не использовать** Spring Data Pageable / PagingAndSortingRepository -
  на проекте JDBC Template, не плодим dep'ы. Простой record-helper
  достаточен

### AI editing (ADR-042, Этап 17.e)

LLM расставляет структуру (хадис-боксы, ayah-боксы, headings) поверх
text_content через Anthropic Claude. Optional enhancement — без ключа
платформа работает (formatted_content=null). Tesseract OCR удалён
(ADR-057) — image-сканы хранятся как субстрат для будущего AI-recognition.

**Детали:** `backend/docs/ai-editing.md` (env vars config, async
pipeline `aiEditTaskExecutor`, retry policy Resilience4j, state machine
в `lib_pages.ai_edit_status`, prompt template, graceful degradation,
live IT тест).

### Security (ADR-040)

- **Spring Security 6** + **jjwt 0.12.x** (HS256)
- Auth endpoints под `/api/v1/auth/*` - публичные
- Все mutating endpoints требуют principal (Bearer JWT в prod, либо
  X-User-Id в dev/test/local profile)
- `auth.jwt.secret` в prod через env `AUTH_JWT_SECRET` минимум 256 бит
  (`openssl rand -hex 32` для генерации). dev placeholder в
  `application.yml` падает при попытке shipping в prod через
  IllegalStateException на старте
- Access token TTL 15 мин, refresh TTL 7 дней (HttpOnly+Secure+
  SameSite=Strict cookie)
- Roles: `USER` / `ADMIN` (CHECK constraint). RBAC permissions
  per-entity - ADR-043 (Этап 22)

### Rate limit + Actuator security (ADR-046 + ADR-048)

Дополнительные security слои: rate limit на `/auth/login` и
`/auth/register` (in-memory sliding window, ADR-046), и actuator
basic auth в prod profile (ADR-048).

**Детали:** `backend/docs/auth-security.md` (RateLimitFilter,
`auth.rate-limit.*` properties, IP extraction; ActuatorSecurityConfig,
prod/dev profile difference, ACTUATOR_USERNAME/PASSWORD env vars).

### Permissions (ADR-043, Этап 22)

Per-entity authorization для тем. `topics.visibility` - PRIVATE
(только owner) / SHARED (owner + topic_members) / PUBLIC (read для
всех authenticated, write только owner + EDITOR member). ADMIN
bypass всех visibility checks.

- **PermissionService** - `canReadTopic` / `canWriteTopic` / `isOwner`
  + `assertCan*` (бросают `TopicAccessDeniedException` /
  `TopicWriteAccessDeniedException` → 403 forbidden-topic-access/write)
- **Где живут проверки** - Service-слой (не Controller). Сервис
  принимает `(userId, role)` и сам ассертит. Старые сигнатуры (без
  role) оставлены для internal callers (TopicImportService, scheduled
  jobs, IT) - они не делают permission check
- **Controllers** читают role из SecurityContext через
  `SecurityContextUtils.currentRole()` helper (не вводим новый
  ArgumentResolver - principal уже в SecurityContext через
  AuthenticatedUser, helper его экстрактит)
- **Topic members** - `TopicMemberService` + REST endpoints
  `/api/v1/topics/{id}/members[/...]`. Только owner может add/update
  role/remove (EDITOR не может - privilege escalation). MEMBER может
  удалить только себя (self-leave)
- **Audit log** (кто что менял когда + permission changes) - **отложен**.
  Сейчас trace только через `revisions` для контента и стандартный
  request log
- **Existing tests с X-User-Id** - продолжают работать т.к. они
  создавали тему сами и оперировали с тем же userId (default PRIVATE +
  owner = full access). Кто читал/удалял без header - теперь должен
  передавать X-User-Id (или 400 missing-user-header в dev/test)

#### Library books (ADR-043 Amendment, Этап 22.c)

Расширение visibility/members модели на `lib_books`. **Default
PUBLIC** для existing rows (в отличие от topics PRIVATE) - shamela
ETL и старые user-uploads - open library. Новые user-uploads через
`FileImportService.importPdf` → **PRIVATE** (черновики приватны).

- **PermissionService** - `canReadBook` / `canWriteBook` / `isBookOwner`
  + `assertCanReadBook` / `assertCanWriteBook` / `assertIsBookOwner`.
  Те же exception/HTTP паттерны что у topics: `BookAccessDeniedException`
  → 403 forbidden-book-access, `BookWriteAccessDeniedException` → 403
  forbidden-book-write, `BookMemberNotFoundException` → 404
- **Book members** - `BookMemberService` + REST
  `/api/v1/library/books/{id}/members[/...]`. Mirror TopicMember (owner
  add/update/remove, MEMBER self-leave). `BookMemberRepository` mirror
  TopicMemberRepository
- **BookRepository** - `findVisibleToUserPage` / `countVisibleToUser`
  для visibility filter (PUBLIC OR created_by=? OR SHARED+EXISTS
  lib_book_members). Старый `findPage` без filter оставлен для
  internal (shamela sync, admin)
- **BookController endpoints обязательно требуют X-User-Id** на GET
  `/books`, GET `/books/{id}`, PATCH `/books/{id}`, DELETE
  `/books/{id}`, PATCH `/books/{id}/visibility`. Listings показывают
  только видимое user'у. Existing IT обновлены - все 49 `new Book(...)`
  получили 17-й аргумент `BookVisibility.PUBLIC` через python-script
  patch

#### Q&A guards (ADR-043 Amendment, Этап 22.c)

**НЕ добавляем visibility model** - questions/answers по дизайну open
discussion (видны всем authenticated). Защищаем только mutating через
author/admin guard:

- `QuestionService.updateQuestion(id, ..., actorUserId, actorRole)` +
  `deleteQuestion(id, actorUserId, actorRole)` - guards. Старые
  без actor оставлены для internal callers
- `AnswerService.updateAnswer(id, body, actorUserId, actorRole)` +
  `deleteAnswer(id, actorUserId, actorRole)` - аналогично
- Exceptions: `QuestionWriteAccessDeniedException` → 403
  forbidden-question-write, `AnswerWriteAccessDeniedException` → 403
  forbidden-answer-write. Не автор и не ADMIN → 403
- Frontend получает 403 с типизированным problem-detail - локализация
  через `permissionErrors` helper (existing для topics)
- **Private Q&A** (visibility model для questions/answers) **отложен**
  в 22.e - расширим если возникнет use-case закрытых учёных групп

#### Audit log (ADR-043 Amendment 3, Этап 22.d)

Event-sourcing lite аудит мутаций. **Synchronous** в той же транзакции
что и main flow - rollback main откатит audit. Manual logging (не Spring
AOP) - каждый mutation-сайт явно вызывает `auditLogService.log*`.

- **AuditLogService** методы:
  - `logCreate(entityType, entityId, parentType?, parentId?, actor,
    snapshot)`
  - `logUpdate(...., Map<String, FieldDiff>)` - FieldDiff(old, new)
  - `logDelete(...., snapshot)`
  - `logVisibilityChange(entityType, entityId, actor, old, new)`
  - `logMemberAdd/Remove/RoleChange(memberEntityType, memberId,
    parentType, parentId, actor, userId, role)`
- **Где логировать** - в role-overload методе сервиса (где известен
  actor userId). Legacy-перегрузки без role не пишут audit (используются
  только в тестах и internal callers). EdgeService - audit в
  `createEdge(... userId)` legacy потому что родительский путь через
  userId единственный, не через role
- **Snapshot - только key fields** (title/content/visibility), не
  полный entity. Полные snapshots для `nodes.content`/`metadataJson`
  раздуют jsonb. Для debugging достаточно identifying fields
- **REST endpoints**:
  - `GET /api/v1/audit/topics/{id}` - assertCanWrite (только owner +
    EDITOR, не SHARED/PUBLIC MEMBER)
  - `GET /api/v1/audit/books/{id}` - assertCanWriteBook
  - `GET /api/v1/audit/me` - любой authenticated, фильтр по
    actor=current
  - `GET /api/v1/audit/admin?entityType=&actorId=&dateFrom=&dateTo=` -
    ADMIN only через `AdminOnlyException` → 403 forbidden-admin-only
- **Retention policy** (Code review round 3 #5) - `AuditLogRetentionJanitor`
  в `service/`, `@ConditionalOnProperty(audit.retention.enabled=true)`,
  cron 02:00 ежедневно (override через AUDIT_RETENTION_CRON). По
  умолчанию выключен (compliance retention prod-only). Retention
  `audit.retention.retention-days` (default 365, минимум 7 - валидация
  в AuditRetentionProperties). В prod включается:
  `AUDIT_RETENTION_ENABLED=true`. AuditLogRepository.deleteOlderThan
  один DELETE statement, без soft-delete
- **Не покрыто:** admin UI (отложен 22.e/backlog), async logging
  (через outbox если performance overhead станет ощутимым)

### Hadith grades + Authority.type (миграция 47)

`HadithGradeService.addGrade` валидирует семантическую роль authority —
оценивать хадис может только `SCHOLAR`. Whitelist в
`domain.AuthorityType`: SCHOLAR / MUHAQQIQ / PUBLISHER / AUTHOR / OTHER.

**Детали:** `backend/docs/hadith-grades.md` (validation logic,
`InvalidScholarAuthorityException`, CHECK constraint, ETL поведение
ShamelaAuthorityResolver, backward compat, `lib_publishers` +
`lib_muhaqqiqs` отдельные таблицы).

### Транзакции

- `@Transactional` только на сервисном слое
- `@Transactional(readOnly = true)` для read-only методов
- **НИКОГДА** не ставить `@Transactional` на `@Scheduled` напрямую
  (см. antipatterns.md)
- Избегать вложенных транзакций

### Тесты

- **Интеграционные тесты** - через Testcontainers (PostgreSQL),
  **не** H2
- Именование `ClassNameTest` / `ClassNameIT`
- `@Tag("live")` для тестов работающих с внешним API (shamela,
  archive.org) - исключаются из обычного `verify`
- DBRider при необходимости простой подготовки фикстур
- Минимум: покрыть сервисы бизнес-логики и репозитории

Подробно - в `backend/docs/coding-standards.md` и
`backend/docs/testing-strategy.md`

## Что НЕ делать

- Не использовать JPA/Hibernate (только JDBC Template)
- Не использовать Lombok без крайней необходимости (Java records)
- Не использовать H2 в тестах (только Testcontainers)
- Не ставить `@Transactional` на `@Scheduled` методы напрямую
- Не добавлять зависимости без обсуждения
- Не писать бесполезные комментарии вида `// увеличиваем счётчик`
- Не коммитить закомментированный код
- Не лезть в `../frontend/` и корень репы без явного запроса

Полный список - в `backend/docs/antipatterns.md`

## Когда запускать `./mvnw verify`

См. корневой `../CLAUDE.md` раздел «Когда что запускать (cadence)»
- правило применяется к бэку без специфики. Кратко: **не на каждом
чихе**, только в конце логической фазы / перед коммитом крупного
изменения / при конкретном сигнале о возможной поломке (миграция,
изменение DTO/контроллера, рефакторинг >1 слоя). Мелкие правки одного
класса - не запускать
