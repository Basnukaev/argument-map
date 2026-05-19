# backend architecture audit 2026-05-18

deep audit фокусируется на глобальных архитектурных проблемах (а не на
basic smells вроде long methods / dead code, которые закрыты предыдущими
clean-up раундами 11 мая и stability audit 18 мая)

baseline: 871/879 tests pass (8 pre-existing 401-vs-400 IT failures, см.
`2026-05-18-backend-audit.md` секция I-1)

## Глобальные находки

### 1. [Important] Layering violation - controllers напрямую обращаются к repository

**Pattern:** classic layering boundary leak (controller → repository, минуя
service)

**Где:**
- `web/controller/NodeController.java:103-107, 121-125, 138-142` - PATCH,
  bringToFront, sendToBack дёргают `nodeVoteRepository`,
  `nodeSourceRepository`, `nodeTranslationRepository` напрямую чтобы
  enrich'нуть NodeResponse (stats/userVote/citations/translations)
- `web/controller/TopicController.java:129-134` - graph endpoint тоже
  тянет stats/votes/citations/translations через repos напрямую
- `web/controller/AuditLogController.java:19` - `UserRepository`

**Что не так:** controller знает о JDBC-уровне данных, обходит сервисный
слой. это нарушает single-responsibility (контроллер = orchestration +
mapping, не запросы) и блочит будущие cross-cutting concerns - например
если завтра захочется кеш на vote-stats / лог-обогащение, придётся
добавлять в каждом controller endpoint вручную

**Recommendation:** ввести `NodeProjectionService` (или extend `GraphService`)
с методом `enrich(Node, UUID currentUser)` → `EnrichedNode` (или сразу
NodeResponse). Кейс PATCH-then-enrich использует один метод, можно
переиспользовать в bringToFront/sendToBack тоже

**Effort:** S (~30-50 LOC service + edits в 4 controller методах)
**Risk:** low (additive - тесты не ломаются, REST контракт неизменен)

### 2. [Important] Primitive obsession для (UUID userId, String role) - 30+ методов

**Pattern:** primitive obsession (Java records под рукой, не использованы)

**Где:** 158 occurrences в main source. Pattern - `(UUID userId, String role)`
либо `(UUID actorUserId, String actorRole)`:
- `PermissionService` 12 методов
- `BookService`, `NodeService`, `EdgeService`, `TopicService`,
  `TopicMemberService`, `BookMemberService`, `NodeTranslationService`,
  `QuestionService`, `AnswerService`, `HadithGradeService` - всё имеет
  параметры (userId, role) пробрасываемые сквозь call chains
- controllers повторяют 3-строчный prologue:
  `UUID userId = @CurrentUser; String role = SecurityContextUtils.currentRole();`

**Что не так:**
- Два параметра логически образуют один концепт «actor» (кто действует) -
  но передаются раздельно. при добавлении например username (для audit
  log human-readable) или userAgent (для security forensics) потребуется
  обновить десятки сигнатур
- Тесты вынуждены передавать оба и легко перепутать порядок (был
  incident в early ADR-043 раунде - правда поймали code review'ом)
- `Actor` record как value object сделал бы API чище и расширяемым

**Recommendation:** ввести `auth.domain.Actor` record (`UUID userId, String role`)
с factory methods `Actor.user(uuid)`, `Actor.admin(uuid)`, `Actor.from(authenticated)`.
Постепенный rollout - сначала добавить новые перегрузки `assertCanWrite(topicId, Actor)`,
затем мигрировать call sites. Старые `(UUID userId, String role)` остаются как
deprecated wrappers

**Effort:** L (additive поэтапно), но immediate value от `Actor.from(authenticated)`
helper в controllers + переписки 5-7 hot methods

**Risk:** low (additive)

### 3. [Important] Anaemic domain - records pure data bags, business invariants в services

**Pattern:** anaemic domain model

**Где:** все 27 domain records (`Node`, `Source`, `Book`, `Edge`, etc.) -
zero behavior. Все invariants проверяются в services:
- `SourceRepository.upsertByBookId():157` проверяет `source.bookId() == null
  || source.sourceType() != SourceType.BOOK` - инвариант что upsert-by-book
  требует BOOK source. Этот предикат повторяется в `HadithGradeService:69`
  для HADITH
- `PermissionService.canReadTopic(topic, userId)` - dispatch на
  `topic.visibility()` через 3 if-statement. Можно как метод на Topic
  `topic.allowsRead(userId, members)` либо visitor

**Что не так:** records OK как DTO, но invariants дробятся между сервисами.
domain rules «source-with-grades должен быть HADITH», «book source требует
non-null bookId» живут в SourceRepository и HadithGradeService раздельно -
если завтра добавится третий caller, проверка дублируется в третьем
месте

**Recommendation:** добавить минимальные guard-методы на records:
- `Source.requireType(SourceType expected)` бросает `IllegalStateException` -
  заменяет explicit if в services
- `Source.requiresBookLink()` boolean - business invariant (только для
  BOOK), используется в репо upsertByBookId

Не превращать records в god-object - только real invariants как short
expression-bodied static helpers. JPA здесь не возвращаем

**Effort:** S (3-5 helper методов в records, 5-7 call sites замениваются)
**Risk:** low (behavior identical, тесты pass)

### 4. [Minor] Visibility constants - dead-code-prone equality dispatch

**Pattern:** Switch-on-string дублируется в PermissionService (для Topic
и Book - почти идентичные ветки)

**Где:**
- `TopicVisibility` / `BookVisibility` - константы (String) + `isValid()`
- `PermissionService.canReadTopic(Topic, UUID)` строки 79-91 и
  `canReadBook(Book, UUID)` строки 181-193 структурно идентичны -
  одни и те же три ветки PRIVATE/SHARED/PUBLIC, отличается только
  тип member repository

**Что не так:** генерик `Visibility` strategy не введён - копия логики в
двух классах одного service. при добавлении 3-го entity (например
Question visibility) - copy-paste третий раз

**Recommendation:** ввести `VisibilityPolicy<T>` interface (или generic helper
`PermissionChecker.check(visibility, ownerId, actorId, isMember)`) - один
canonical алгоритм. Сейчас 2 копии терпимо но при 3-м entity точно
рефакторить

**Effort:** M (рефакторинг PermissionService, не breaking)
**Risk:** medium (touches central permissions logic - все тесты тематические)

**Решение в этой подсессии:** не делать. отложить до момента когда
появится 3-я entity с visibility (см. backlog ниже)

### 5. [Minor] EnumValidator duplication

**Pattern:** validation patterns

**Где:** `TopicVisibility.isValid`, `BookVisibility.isValid`,
`UserRole.isValid`, `AuditEntityType.isValid` - все имеют одинаковую
форму: проверка строки против whitelist

**Что не так:** копия валидации, лёгкая ошибка пропустить новый enum-член
при добавлении. Опасно для `AuditEntityType.isValid` - 12 веток через
`||` chain

**Recommendation:** generic helper `Enums.isValidName(Class<? extends Enum<?>>, String)`
для случаев где enum существует. Для constant-classes (TopicVisibility/UserRole) -
оставить как есть (CHECK constraint в БД диктует строковую форму, не enum)

**Effort:** S
**Risk:** low

**Решение в этой подсессии:** не делать. косметика по сравнению с (1)-(3)

### 6. [Info] SOLID assessment

- **SRP:** BookService 482 LOC - 3 концерна (CRUD книги, visibility,
  academic metadata, pages). Можно разбить на `BookCrudService` +
  `BookAcademicService` + `BookPageService`. Но цена/польза - выживаемо
  как есть. flag в backlog
- **OCP:** PdfSourceProvider - **уже хороший Strategy** (PdfLinksSourceProvider,
  UserUploadProvider, future EPUB), extension через новый @Component +
  @Order. ничего fixать
- **LSP:** наследования почти нет (хорошо). PdfSourceProvider реализации
  семантически substitutable
- **ISP:** PdfSourceProvider 4 метода (supports/getMetadata/locateFile/openStream) -
  на границе. Можно разбить на `PdfMetadataProvider` + `PdfStreamProvider` но
  локатор и стример логически связаны. flag не нужен
- **DIP:** Spring DI хорошо, concrete classes только когда они final values
  (DtoMappers etc.). никаких сломов

### 7. [Info] Event-driven opportunities

**Где:** AuditLogService явно sync (документировано: «в той же транзакции
для consistency»). Но 7 mutation сервисов теперь напрямую зависят от
`AuditLogService` - tight coupling

**Что не так (не баг, design choice):** при добавлении нового entity с audit -
ещё один сервис получит dependency. альтернатива - `ApplicationEventPublisher`
+ `@TransactionalEventListener(phase=BEFORE_COMMIT)` бы развязал

**Recommendation:** flag только. Текущий design defensible (manual control,
debug-friendly). Не менять без user-feedback signal

### 8. [Info] Bounded contexts соблюдаются

- `argumentmap/{domain,service,repository,web}` - argument-map context
- `argumentmap/library/...` - library context
- `argumentmap/qa/...` - Q&A context
- `argumentmap/auth/...` - auth context

cross-context dependencies проверены: `library/service/BookService` импортит
`argumentmap.domain.AuditEntityType` + `argumentmap.service.{PermissionService,
AuditLogService}` (acceptable - audit/permissions общие cross-cutting).
qa-сервисы зависят от topic/permission лишь через `PermissionService`.
никаких circular imports не нашёл

## Fixed в этой подсессии

### Fix 1: Layering violation - extract NodeProjectionService

NodeController и TopicController больше не зависят от
NodeVoteRepository/NodeSourceRepository/NodeTranslationRepository напрямую.

- Новый `service.NodeProjectionService` с методами `enrichSingle(Node, UUID)` +
  `enrichBatch(List<UUID>, UUID)` - один источник истины для votes/citations/translations
- NodeController сокращается на ~15 LOC (4-строчный enrichment fragment повторялся в 4
  методах - PATCH, bringToFront, sendToBack)
- TopicController graph endpoint тоже использует service

### Fix 2: Actor value object - centralize (userId, role) duplication

Новый `auth.domain.Actor` record с `Actor.from(AuthenticatedUser)`. Controllers перестают
писать `UUID + currentRole()` 3-строчный prologue.

- additive - старые сигнатуры `(UUID, String)` остаются работать (deprecation позже)
- `SecurityContextUtils.currentActor()` - single читалка из SecurityContext
- 8 контроллеров используют `Actor` напрямую в первом раунде, остальные мигрируются позже

### Fix 3: Domain guards on Source record

Добавлены guard-методы:
- `Source.requireType(SourceType expected)` - бросает `InvalidSourceException` если
  тип не совпадает. используется в `SourceRepository.upsertByBookId` и
  `HadithGradeService.addGrade`
- `Source.isHadith()`, `Source.requiresBookLink()` - читабельные predicates

### Fix 4: VisibilityPolicy unifies Topic+Book read/write logic

Один canonical алгоритм `VisibilityPolicy.canRead(visibility, ownerId, actorId, isMember)` +
`canWrite(visibility, ownerId, actorId, memberRole)`. PermissionService теперь делегирует
в helper - дублирующиеся 12-строчные блоки между canReadTopic/canReadBook убраны

## Recommendations (отложенное в backlog)

- **BookService split** - BookCrudService + BookAcademicService + BookPageService.
  Effort=M, готовы fixing когда BookService перевалит 600 LOC
- **EnumValidator generic helper** - косметика, low priority
- **Event-driven audit** - только если AuditLogService coupling начнёт мешать
- **BookService 13/14/15-arg constructor overloads** - реальный builder pattern
  упростит. Effort=M

## Метрики (после fixes)

| Метрика | До | После |
|---|---|---|
| BookService LOC | 482 | 482 (не трогали) |
| NodeController repo deps | 3 | 0 |
| TopicController repo deps | 3 | 0 |
| (UUID, String role) signatures | 158 | 158 (не breaking) - additive Actor |
| Source type guards разбросанные | 2 mest | 0 (через Source.requireType) |
| PermissionService LOC | 260 | ~180 (после VisibilityPolicy) |

## Acceptance criteria

- `./mvnw verify` BUILD SUCCESS (baseline 871/879 preserved)
- 3-5 атомарных коммитов
- audit report + 1-2 ADRs если уместно
