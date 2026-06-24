# Permissions, RBAC и Audit log (ADR-043)

Детальная модель per-entity authorization и аудита мутаций. Вынесено из
`backend/CLAUDE.md` (doc-hygiene Принцип 2). Решения: ADR-043 + три
amendment'а (books / Q&A guards / audit). Краткая выжимка - в
`backend/CLAUDE.md`.

## Permissions (ADR-043, Этап 22)

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
  удалить только себя (self-leave). **`GET .../members` — authenticated-only**
  (ADR-064 follow-up, P1-4): вынесен из guest-view permitAll в
  `SecurityConfig` правилом `requestMatchers(GET, "/api/v1/topics/*/members")
  .authenticated()` (раньше guest-глоба → действует в prod и dev/test). Аноним
  → 401, иначе username/UUID участников PUBLIC темы утекали бы. За гейтом всё
  ещё работает per-entity `assertCanRead`
- **Audit log** (кто что менял когда + permission changes) - **отложен**.
  Сейчас trace только через `revisions` для контента и стандартный
  request log
- **Existing tests с X-User-Id** - продолжают работать т.к. они
  создавали тему сами и оперировали с тем же userId (default PRIVATE +
  owner = full access). Кто читал/удалял без header - теперь должен
  передавать X-User-Id (или 400 missing-user-header в dev/test)

## Library books (ADR-043 Amendment, Этап 22.c)

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
  TopicMemberRepository. **`GET .../members` — authenticated-only** (зеркало
  topics, ADR-064 follow-up P1-4): `requestMatchers(GET,
  "/api/v1/library/books/*/members").authenticated()` раньше guest-глоба
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

## Q&A guards (ADR-043 Amendment, Этап 22.c)

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

## Audit log (ADR-043 Amendment 3, Этап 22.d)

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
