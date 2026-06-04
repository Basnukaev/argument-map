# Design spec: Расширение системы ролей USER → {USER, STUDENT, SCHOLAR, ADMIN}

**Дата:** 2026-05-20
**Автор:** Абдула + brainstorming
**Статус:** approved, ожидает implementation plan
**Связанные ADR (existing):** ADR-040 (Spring Security + JWT auth),
ADR-043 (per-entity permissions: TopicMember/BookMember), ADR-047
(refresh token rotation)
**Связанные ADR (будут созданы при implementation):** ADR-049 (roles
expansion), ADR-050 (role elevation / promotion flow)

---

## Контекст

Сейчас в `users.role` CHECK constraint ровно две роли - `USER` и
`ADMIN` (миграция 32, `20260517-32-extend-users-for-auth.xml`).
ADMIN bypass всех permission checks в `PermissionService`, USER -
все остальное.

В исламской платформе нужна более тонкая градация: не все
authenticated user'ы должны иметь право оценивать иснады (хадис
grade'ы) и не все должны писать ответы в Q&A. Это семантический
gate, не technical - связано с академической репутацией и
ответственностью пользователя за публикуемый контент.

**Важно не путать с ADR-043:** TopicMember/BookMember с ролями
`MEMBER`/`EDITOR` - это **per-entity ACL** (кто может писать в эту
конкретную тему). Этот spec - про **global user role** (кто в
принципе может в системе делать определённые действия). Два
ортогональных механизма. SCHOLAR без EDITOR на topic не может
писать в private topic, но может на любых читаемых ему source'ах
ставить хадис grade'ы. EDITOR USER на topic может писать в этот
topic, но не может grade'нуть хадис.

## Цель

Расширить enum ролей до 4-х уровневой иерархии с явной семантикой:

| Роль    | Что может (помимо предыдущих) | Use case |
|---------|---|---|
| USER    | read + vote на узлы | дефолт для регистрации |
| STUDENT | + комментировать узлы, отвечать в Q&A (createAnswer / createQuestion) | начинающий участник |
| SCHOLAR | + ставить hadith grade'ы, оценивать иснады | quality contributor |
| ADMIN   | + full bypass + admin pages + user management | platform staff |

Иерархия монотонна - каждая следующая роль включает все способности
предыдущей (SCHOLAR может всё что STUDENT, и т.д.). Это даёт чистый
ordinal compare на стороне service / frontend через single helper
`hasRoleAtLeast(role, STUDENT)`.

## Не входит в этот spec

- Earned roles (автоматическое присвоение SCHOLAR после N approved
  grade'ов) - см. Open questions
- Делегирование (SCHOLAR назначает STUDENT'а)
- Per-domain scholar (SCHOLAR_HADITH vs SCHOLAR_FIQH) - YAGNI до
  реального запроса
- Soft-revoke роли с retention истории (history of role changes -
  отложено в backlog, базовый audit log этого даст)

## 1. Current state inventory

### users.role в БД

- Колонка: `users.role VARCHAR(20) NOT NULL DEFAULT 'USER'`
- CHECK: `users_role_check CHECK (role IN ('USER', 'ADMIN'))`
- Миграция: `backend/src/main/resources/db/changelog/changes/20260517-32-extend-users-for-auth.xml`
- DevUserSeeder создаёт seed user с ролью ADMIN
  (`backend/src/main/java/ru/basnukaev/argumentmap/auth/DevUserSeeder.java:58`)
- UserService.register() жёстко проставляет `UserRole.USER`
  (`backend/src/main/java/ru/basnukaev/argumentmap/auth/service/UserService.java:62`)

### Backend - где живут роли

- `UserRole.java` - final class со String константами USER/ADMIN
  + `isValid()`. Не enum (намеренно: соответствует CHECK
  constraint, не требует JDBC мэппинга)
- `User.java` record содержит `String role`
- `AuthenticatedUser.java` (principal в SecurityContext) содержит
  `String role` - кладётся в JWT claims через `JwtService.buildToken`
  (claim `CLAIM_ROLE`), читается обратно в `validateToken`
- `SecurityContextUtils.currentRoleOrAnonymous()` - helper для
  Service слоя; fallback на `UserRole.USER` если контекст пустой

### Backend - где встречаются проверки ADMIN

`grep "UserRole.ADMIN.equals"`:

1. `PermissionService` - 6 мест (canRead/Write Topic/Book, isOwner,
   isBookOwner) - bypass visibility checks
2. `TopicService` - 3 места (admin может listAll, archive any topic)
3. `HadithGradeService.assertCanModify` - admin может update/delete
   чужой grade
4. `AnswerService.assertAuthorOrAdmin` - admin может edit/delete
   чужой answer
5. `QuestionService.assertAuthorOrAdmin` - admin может edit/delete
   чужой question
6. `BookService` - 2 места (admin может listAll books bypass visibility)
7. `AuditLogController` - 3 места (admin only endpoint
   `GET /api/v1/audit/admin`) - бросает `AdminOnlyException`

### Backend - где встречается USER role explicitly

`grep "UserRole.USER"`:

1. `UserService.register` - default role при создании user'а
2. `SecurityContextUtils.currentRoleOrAnonymous` - 2 места (fallback
   на USER для anonymous и для principal без role)

### Frontend - где встречается role

- `frontend/src/shared/stores/authStore.ts:8` - `type AuthRole = 'USER' | 'ADMIN'`
- `frontend/src/shared/stores/authStore.ts:64` - валидация persisted user
- `frontend/src/shared/components/auth/ProtectedRoute.tsx` -
  `requireRole?: 'USER' | 'ADMIN'`, прямой compare на 'ADMIN'
- `frontend/src/App.tsx:165-184` - 3 admin route'а
  (`/admin/shamela`, `/admin/library/pages/.../edit`, `/admin/audit`)
- `frontend/src/apps/argument-map/pages/TopicGraphPage.tsx:92` -
  `const isAdmin = currentUser?.role === 'ADMIN'`
- `frontend/src/apps/library/pages/BookReaderPage.tsx:332` - то же
- `frontend/src/shared/components/citation/sourceCard/HadithGradesSection.tsx:201` -
  скрывает delete/edit grade если не author и не ADMIN
- `frontend/src/shared/hooks/useOnboardingProgress.ts:152` - ADMIN
  bypass для onboarding logic (комментарий)
- `frontend/src/shared/api/types.ts:2010, 2349` - generated types
  `role?: "USER" | "ADMIN"` (регенерируются из OpenAPI)

## 2. Migration plan

### Liquibase миграция 49

**ID:** `20260520-49-expand-user-roles`
**Файл:** `backend/src/main/resources/db/changelog/changes/20260520-49-expand-user-roles.xml`
**Регистрация:** последняя строка в `db.changelog-master.xml`

Содержимое changeset'а (author `Abdula Basnukaev`):
- DROP constraint `users_role_check`
- ADD constraint `users_role_check CHECK (role IN ('USER','STUDENT','SCHOLAR','ADMIN'))`
- `<rollback>`: обратный DDL восстанавливающий `IN ('USER', 'ADMIN')`

**Backfill rule:**
- existing `USER` → остаётся `USER` (никаких автоматических
  upgrades - elevation только через admin)
- existing `ADMIN` → остаётся `ADMIN`
- Никаких новых rows миграция не создаёт

**Rollback caveat:** если в БД уже есть rows со STUDENT/SCHOLAR -
rollback упадёт на CHECK violation. Это acceptable: rollback
fence нужен только в первые часы после deploy миграции. Если есть
rows с новыми ролями, restore сначала requires `UPDATE users SET role='USER' WHERE role IN ('STUDENT','SCHOLAR')`.

## 3. Backend changes

### 3.1 UserRole - расширение

Добавить константы STUDENT/SCHOLAR + статичную `LEVEL` map (USER=0,
STUDENT=1, SCHOLAR=2, ADMIN=3) + helper `hasRoleAtLeast(actor, required)` -
ordinal compare по LEVEL. `isValid` - проверка ключа в LEVEL.

### 3.2 PermissionService - новые assert методы

Добавить в существующий `PermissionService`:
- `assertCanAddHadithGrade(role)` - требует SCHOLAR+, бросает `ForbiddenRoleException`
- `assertCanCreateAnswer(role)` - требует STUDENT+
- `assertCanCreateQuestion(role)` - требует STUDENT+
- `assertIsAdmin(role, actorUserId)` - бросает `AdminOnlyException` (existing)

Новый `ForbiddenRoleException` маппится в 403 с problem-detail
`forbidden-role-insufficient` (поля `role`, `requiredRole`).
GlobalExceptionHandler - новая ветка.

### 3.3 Service-слой gating

**HadithGradeService.addGrade**:
- Сейчас принимает `actorUserId` - добавить `String actorRole`
- Вызвать `permissionService.assertCanAddHadithGrade(actorRole)`
  первой строкой
- Сохранена backward compat overload без role - помечается как
  internal/deprecated (как сделано в AnswerService для updateAnswer)
- HadithGradeController читает role через
  `SecurityContextUtils.currentRoleOrAnonymous()`

**AnswerService.createAnswer**:
- Сейчас принимает `authorId` - добавить `String actorRole`
- Вызвать `assertCanCreateAnswer(role)` первой строкой
- AnswerController - read role + передать

**QuestionService.createQuestion**:
- Аналогично - добавить role gate

**NodeService comment endpoints** (если есть отдельный comment
сервис) - на текущий момент комментарии нет отдельной сущности
(content в самом node + revisions). YAGNI: если comment-like
fucn появится отдельно - сразу gate под STUDENT.

**NodeVoteService.vote** - **НЕ gate'им**. USER может voting.
Это часть базового read-experience.

### 3.4 Список impacted controllers / services

Бэкенд (changes inside):

| Файл | Что меняется |
|---|---|
| `auth/domain/UserRole.java` | + 2 константы STUDENT/SCHOLAR, + LEVEL map, + `hasRoleAtLeast` |
| `service/PermissionService.java` | + 4 assert методов (Add grade / Create answer / Create question / IsAdmin) |
| `service/HadithGradeService.java` | `addGrade` overload с `actorRole`, call assertCanAddHadithGrade |
| `qa/service/AnswerService.java` | `createAnswer` overload с `actorRole`, call assertCanCreateAnswer |
| `qa/service/QuestionService.java` | `createQuestion` overload с `actorRole`, call assertCanCreateQuestion |
| `web/controller/HadithGradeController.java` | read role + pass to service |
| `qa/web/controller/AnswerController.java` | read role + pass to service |
| `qa/web/controller/QuestionController.java` | read role + pass to service |
| `auth/service/UserService.java` | + `changeRole(UUID userId, String newRole, UUID actorUserId)` |
| `auth/repository/UserRepository.java` | + `updateRole(UUID, String)` |
| `auth/web/AuthController.java` (или новый UserController) | + PATCH `/users/{id}/role` |
| `exception/ForbiddenRoleException.java` | новый класс (403) |
| `exception/GlobalExceptionHandler.java` | + handler для ForbiddenRoleException |
| `auth/web/dto/MeResponse.java`, `AuthResponse.java` | расширить `@Schema(allowableValues = {"USER","STUDENT","SCHOLAR","ADMIN"})` |
| `auth/DevUserSeeder.java` | seed остаётся ADMIN, добавить опционально STUDENT/SCHOLAR test users в local profile (за флагом) |

## 4. Frontend changes

### 4.1 authStore - расширение AuthRole

- `AuthRole` union literal расширить до `'USER' | 'STUDENT' | 'SCHOLAR' | 'ADMIN'`
- Добавить const `ROLE_LEVEL: Record<AuthRole, number>` (USER=0..ADMIN=3)
- Export `hasRoleAtLeast(actor, required)` для shared usage
- `readPersistedUser` - валидировать против всех 4 значений вместо
  hardcoded `'USER' | 'ADMIN'`

### 4.2 ProtectedRoute - generalize requireRole

- `requireRole?: AuthRole` (вместо текущего `'USER' | 'ADMIN'`)
- Условие redirect: `requireRole && !hasRoleAtLeast(user.role, requireRole)`
- Backward compat: existing `<ProtectedRoute requireRole="ADMIN">` остаётся рабочим

**Альтернатива (отвергнута):** отдельные `ScholarRoute` / `StudentRoute`.
Дублирует bootstrap-логику. ProtectedRoute уже параметризован - cleaner generalize.

### 4.3 useAuthStore - derived helpers

Selector-функции для composable usage:
- `selectCanAddGrade(state) = hasRoleAtLeast(state.user?.role, 'SCHOLAR')`
- `selectCanCreateAnswer(state) = hasRoleAtLeast(state.user?.role, 'STUDENT')`
- `selectIsAdmin(state) = state.user?.role === 'ADMIN'`

Usage: `const canAddGrade = useAuthStore(selectCanAddGrade)`.

### 4.4 UI - locked actions

Принцип: action всегда виден (educational), но disabled с tooltip
объясняющим почему. Не hide - frustrating для user'а не понимающего
почему кнопки нет.

Места:
- `HadithGradesSection` - кнопка «Добавить grade» disabled +
  tooltip «Только учёные (SCHOLAR) могут оценивать хадисы» если `!canAddGrade`
- `AnswerComposer` / `CreateAnswerForm` - textarea + submit disabled + banner
- `CreateQuestionPage` - аналогично

Реализация: reusable `<RoleLockedAction requireRole="SCHOLAR" reason="...">`
wrapper. Если `!canAct` - оборачивает children в disabled-wrapper +
Tooltip. Иначе - render children без overhead.

### 4.5 Admin user management

Новая страница `/admin/users` под `<ProtectedRoute requireRole="ADMIN">`
(не часть SettingsPage - admin tool, не user preferences).

UI: поиск (email/username), таблица (username, email, role select,
enabled toggle, createdAt). При изменении role - PATCH
`/api/v1/users/{id}/role` + toast «Роль обновлена. Изменение вступит
в силу при следующем входе пользователя» (см. JWT caveat 7.3).

## 5. REST endpoints

### 5.1 PATCH /api/v1/users/{id}/role (новый)

**Auth:** ADMIN only (через `assertIsAdmin`)

**Request:**
```json
{ "role": "SCHOLAR" }
```

**Response 200:**
```json
{
  "id": "uuid",
  "username": "...",
  "email": "...",
  "role": "SCHOLAR",
  "updatedAt": "iso8601"
}
```

**Errors:**
- 400 `invalid-role` - значение не в whitelist
- 403 `forbidden-admin-only` - actor не admin
- 404 `user-not-found` - target id не существует
- 422 `cannot-demote-last-admin` - попытка downgrade единственного
  оставшегося админа (safety check)

**Audit:** обязательно `AuditLogService.logUpdate` с diff на
`role` field. AuditEntityType.USER (если ещё нет - добавить enum
value, миграция к таблице audit_log не требуется, type - просто
строка). Сразу в той же транзакции.

### 5.2 GET /api/v1/users (новый, admin only)

**Auth:** ADMIN only

**Query params:**
- `role=SCHOLAR` - фильтр по роли
- `enabled=true|false` - фильтр по enabled
- `q=text` - поиск по email/username (LIKE)
- `page`, `size` - стандартный pagination (PagedResponse)

**Response 200:** `PagedResponse<UserSummary>` где `UserSummary = {id, username, email, role, enabled, createdAt}`. Без password_hash.

### 5.3 Расширение GET /api/v1/auth/me, /login, /register, /refresh

DTOs - расширить allowed values в `@Schema`:
```java
@Schema(allowableValues = {"USER", "STUDENT", "SCHOLAR", "ADMIN"})
String role
```

Семантика не меняется - role уже передаётся, просто допустимых значений теперь 4.

### 5.4 api-contract.md updates

В разделе «Аутентификация» в `docs/api-contract.md`:
- Обновить `allowedValues` для role у каждого endpoint'а где он есть
- Добавить новый раздел «Управление пользователями (admin only)»:
  - `PATCH /api/v1/users/{id}/role`
  - `GET /api/v1/users`
- Добавить новый problem-detail тип `forbidden-role-insufficient`
  с шаблоном `{role, requiredRole}`

### 5.5 OpenAPI / generated types

После backend changes - запустить `npm run generate-api` в
`frontend/`. `types.ts` обновится: `role?: "USER" | "STUDENT" | "SCHOLAR" | "ADMIN"` в местах MeResponse / AuthResponse.

## 6. Test plan

### Backend IT (~25 новых)

`PermissionServiceIT`: матрица `hasRoleAtLeast` (user<student=false,
scholar>=student=true, admin>=all=true, invalid=false) + 4 assert
методов × {USER, STUDENT, SCHOLAR, ADMIN} actor.

`HadithGradeServiceIT`: `addGrade` × {USER throws, STUDENT throws,
SCHOLAR succeeds, ADMIN succeeds}.

`AnswerServiceIT` / `QuestionServiceIT`: createAnswer / createQuestion
× {USER throws, STUDENT succeeds, SCHOLAR succeeds, ADMIN succeeds}.

`UserServiceIT`: `register_defaultRoleIsUser`, `changeRole_byAdmin_persistsNewRole`,
`changeRole_byNonAdmin_throwsAdminOnly`, `changeRole_lastAdminDowngrade_throws422`,
`changeRole_invalidRole_throws400`.

`UserControllerIT` (MockMvc): PATCH `/users/{id}/role` matrix
(200/403/404/400) + GET `/users` с фильтрами (admin=200, user=403).

`AuthMigrationIT` (изоляционный): `migration49_acceptsAllFourRoles`,
`migration49_rejectsInvalidRole`, `migration49_preservesExistingRows`.

### Frontend Vitest (~15 новых)

`authStore.test.ts`: hasRoleAtLeast матрица + readPersistedUser
принимает все 4 + отбрасывает невалидное.

`ProtectedRoute.test.tsx`: requireRole=SCHOLAR × {USER redirect,
SCHOLAR renders, ADMIN renders (hierarchy)}; requireRole=STUDENT × USER redirect.

`HadithGradesSection.test.tsx`: кнопка «Добавить grade» disabled
при USER / enabled при SCHOLAR / tooltip с reason на hover.

`AnswerComposer.test.tsx`: textarea disabled при USER / submit
enabled при STUDENT.

`AdminUsersPage.test.tsx` (новый): рендерится только для ADMIN,
Select смены role вызывает PATCH, toast после успеха.

### Acceptance / E2E (manual playwright)

1. Login как USER → попробовать добавить grade → disabled
   tooltip → попробовать ответить в Q&A → disabled
2. Admin (login как `admin@argumentmap.local`) → /admin/users
   → найти USER → change role to STUDENT → toast
3. Re-login USER (logout / login) → теперь Q&A работает,
   grade'ы по-прежнему locked
4. Admin → change role STUDENT → SCHOLAR
5. Re-login → grade'ы работают, Q&A работает
6. Admin → попробовать demote самого себя (если он единственный
   ADMIN) → 422 toast «нельзя downgrade единственного админа»

## 7. Migration / deployment

### 7.1 Default role for new registration

Остаётся USER (как сейчас в `UserService.register`). Не меняется -
elevation explicit через admin.

### 7.2 Как admin elevate user

Через `/admin/users` страницу (см. 4.5). Изменения через PATCH
`/api/v1/users/{id}/role`.

### 7.3 JWT contains role - propagation

**Caveat:** JWT issued ДО смены роли содержит старую роль. У нас
access TTL 15 мин - в worst case user будет иметь старую роль 15
мин после promotion.

**Решения (в порядке предпочтения):**

1. **Acceptable as-is (recommended).** Admin при promotion видит
   toast «Изменение вступит в силу при следующем входе либо
   refresh access token (до 15 мин)». User просто перелогинится
   если хочет immediate effect. Refresh flow с rotation (ADR-047)
   уже даёт fresh role при следующем `/auth/refresh` - access
   обновится через 15 мин автоматически.

2. **Force re-login.** При role change - revoke все refresh
   tokens user'а через `RefreshTokenRepository.revokeAllByUserId(.., REASON_ROLE_CHANGE)`.
   На следующем request у user'а access протухнет → попытка
   refresh → 401 (refresh revoked) → redirect на /login.
   **Минус:** более жёсткий UX. **Плюс:** zero stale role.

3. **Server-side role lookup в фильтре.** Каждый request
   `JwtAuthenticationFilter` делает `userRepository.findById` для
   refresh role из БД. Игнорировать role из JWT claim. **Минус:**
   N+1 query per request - дороже всех остальных. Отвергаем.

**Решение:** вариант 1 (acceptable as-is) для MVP. Если в
practice окажется болезненным - добавим toggle на admin'ской
кнопке «применить немедленно» который делает revokeAllByUserId.

### 7.4 Rollout sequence

1. Backend deploy с миграцией 49 + новой логикой PermissionService
2. Бэкенд готов принимать новые роли, но никто их пока не имеет
3. Frontend deploy с новым ProtectedRoute / RoleLockedAction /
   AdminUsersPage
4. Admin вручную promot'ит первых SCHOLAR'ов / STUDENT'ов
5. ADR-049 в `docs/decisions.md`

## 8. Acceptance criteria

- [ ] Миграция 49 применена, `users_role_check` CHECK принимает все 4 значения
- [ ] `UserRole.LEVEL` + `hasRoleAtLeast` покрыты unit-тестами
- [ ] `PermissionService.assertCanAddHadithGrade/CreateAnswer/CreateQuestion/IsAdmin` все 4 метода + тесты
- [ ] `HadithGradeService.addGrade` gated по SCHOLAR+ - USER/STUDENT получают 403
- [ ] `AnswerService.createAnswer` + `QuestionService.createQuestion` gated по STUDENT+
- [ ] PATCH `/api/v1/users/{id}/role` работает, ADMIN-only, audit logged
- [ ] GET `/api/v1/users` работает с фильтрами, ADMIN-only, PagedResponse
- [ ] DTOs MeResponse / AuthResponse расширены 4-значениями + регенерированы frontend types
- [ ] `ProtectedRoute` принимает любую AuthRole через `requireRole`, ordinal-compare
- [ ] `<RoleLockedAction>` wrapper готов, used в HadithGradesSection / AnswerComposer
- [ ] `/admin/users` страница работает: list, search, role select, toast
- [ ] Тесты backend (25 новых IT) и frontend (15 новых Vitest) зелёные
- [ ] api-contract.md обновлён, ADR-049 написан
- [ ] Manual playwright smoke по acceptance journey пройден
- [ ] Независимый code review (OMC reviewer / `/code-review`) сделан

## 9. Risks / open questions

### Q1: Boolean grant vs earned role?

**Status:** для MVP - **boolean grant** (admin click → SCHOLAR).
Earned (после N approved grade'ов) - over-engineering на старте
(нет approval flow для grade'ов вообще).

**Future:** если объём вырастет до 100+ active users, ввести
opt-in promotion request flow («запросить SCHOLAR») с admin
review. Backlog item.

### Q2: SCHOLAR делегирует STUDENT для review?

**Status:** не делаем. ADR-043 уже даёт per-topic EDITOR - этого
достаточно для delegation в scope конкретной темы. Cross-topic
SCHOLAR→STUDENT delegation - over-engineering до запроса.

### Q3: Existing answer authors - что с ними после деплоя?

**Контекст:** до деплоя все registered users были USER. Они уже
писали answer'ы. После деплоя `createAnswer` требует STUDENT+.

**Решение:**
- Existing answers сохраняются (БД row не трогаем)
- USER не сможет писать **новые** answer'ы пока admin не upgrade'нет
  их до STUDENT+. UI чётко показывает почему (RoleLockedAction
  с reason)
- Admin может выполнить batch upgrade SQL (ad-hoc) - всех текущих
  active answer authors поднять до STUDENT:
  `UPDATE users SET role='STUDENT' WHERE id IN (SELECT DISTINCT author_id FROM answers)`
  и аналогично для question_authors. Это admin-side ручное
  действие, не миграция (не записываем в чанжсет - admin policy decision)

### Q4: NodeVoteService - gate под STUDENT?

**Решение:** **нет.** USER может voting. Vote - lightweight
mechanism (like / dislike), не требует expertise. Gate'ить под
STUDENT даст ощущение «у меня нет голоса» - откатывает
democratization платформы.

### Q5: Audit log - нужен новый AuditEntityType.USER?

**Решение:** да. Добавить `USER` в `AuditEntityType` enum (Java
side). Таблица `audit_log.entity_type` - varchar, не enum, новых
DDL не требует. Это logCreate/logUpdate для user mgmt операций
(role change, enabled toggle). audit-log read endpoint
`GET /api/v1/audit/admin?entityType=USER` сразу работает.

### Q6: SettingsPage vs отдельная /admin/users страница?

**Решение:** **отдельная страница `/admin/users`** под
`<ProtectedRoute requireRole="ADMIN">`. Settings - per-user
preferences (theme, locale). User management - admin tool, не
preferences, смешивать неестественно.

### Q7: Локализация role names

Names в БД и API - English (`USER`, `STUDENT`, `SCHOLAR`, `ADMIN`).
В UI через `useT()`:
- `role.user` → «Участник»
- `role.student` → «Студент»
- `role.scholar` → «Учёный»
- `role.admin` → «Администратор»

i18n словарь обновляется в `shared/i18n/dictionary.ts` для ru/ar/en.

## 10. Estimated effort

| Фаза | Что | Commits | Часы |
|---|---|---|---|
| 49.a | Миграция 49 + UserRole expanded + ForbiddenRoleException + unit-тесты | 2 | 1.5 |
| 49.b | PermissionService 4 assert + IT | 2 | 1.5 |
| 49.c | HadithGradeService gating + AnswerService + QuestionService + IT | 3 | 2.5 |
| 49.d | UserService.changeRole + repository + IT + 422 last-admin safety | 2 | 2 |
| 49.e | UserController (PATCH role + GET list) + MockMvc IT | 2 | 2 |
| 49.f | api-contract.md update + ADR-049 + DTO @Schema | 1 | 1 |
| 49.g | Frontend authStore + hasRoleAtLeast + ProtectedRoute generalize + tests | 2 | 2 |
| 49.h | RoleLockedAction wrapper + HadithGradesSection / AnswerComposer / CreateQuestionPage integration + tests | 3 | 3 |
| 49.i | AdminUsersPage + route + tests + i18n role names | 2 | 2.5 |
| 49.j | Manual playwright smoke + code review + handoff | 1 | 1.5 |
| **Итого** | | **20 commits** | **~19.5 ч** |

Реалистично - **2-3 сессии** плотной работы. Естественные точки
handoff: после 49.f (бэк готов), после 49.i (фронт готов перед
smoke). Если время поджимает - разбить на:
- Сессия A: 49.a → 49.f (backend complete, ADR + api-contract)
- Сессия B: 49.g → 49.j (frontend + smoke + handoff)

## Decomposition (для implementation plan)

Предлагаемые подэтапы (см. таблицу выше):

1. **49.a** - миграция 49 + UserRole расширение + ForbiddenRoleException
2. **49.b** - PermissionService новые assert методы + IT
3. **49.c** - gating в HadithGradeService / AnswerService / QuestionService + IT
4. **49.d** - UserService.changeRole + repository + last-admin safety
5. **49.e** - UserController PATCH + GET list + MockMvc IT
6. **49.f** - api-contract.md + ADR-049 + DTO @Schema + regenerate frontend types
7. **49.g** - frontend authStore extensions + ProtectedRoute generalize + tests
8. **49.h** - RoleLockedAction wrapper + integration в action sites
9. **49.i** - AdminUsersPage + route + i18n
10. **49.j** - manual playwright smoke + независимый code review (OMC / `/code-review`) + handoff
