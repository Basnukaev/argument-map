# API-контракт

Этот документ - **источник истины** для контракта между бэкендом и фронтендом.
Обе команды (обе части монорепы) ориентируются на этот файл.

## Статус

✅ **v1, Этапы 4-5** - реализованы все эндпоинты MVP: темы, узлы, рёбра,
граф, ревизии, источники, авторитеты, привязка источников и авторитетов
к узлам.

OpenAPI-спецификация: `/v3/api-docs` (JSON), Swagger UI: `/swagger-ui/index.html`.

## Принцип работы

1. Перед реализацией нового эндпоинта - записать его контракт здесь
2. Бэкенд реализует согласно записанному контракту
3. Фронтенд пишет клиент согласно записанному контракту
4. Любое изменение контракта - это **совместное** решение, затрагивающее
   обе стороны. Обсуждать до изменения, не после.
5. OpenAPI-спецификация (`/v3/api-docs` на бэке) должна **всегда**
   соответствовать этому документу. Если расходятся - это bug.

## Связанные документы

- [`backend/docs/api-design.md`](../backend/docs/api-design.md) - правила
  дизайна REST API (формат, пагинация, ошибки, версионирование)
- [`decisions.md`](decisions.md) - ADR-006 про `X-User-Id`
- `frontend/docs/api-client.md` (появится позже) - как фронт использует API

## Базовые решения

- **Base URL:** `/api/v1`
- **Формат:** JSON, UTF-8, `camelCase` поля
- **Даты:** ISO 8601 с таймзоной (`2026-04-13T10:30:00Z`)
- **ID:** UUID v4 как строки
- **Ошибки:** RFC 7807 Problem Details (`Content-Type:
  application/problem+json`)
- **Аутентификация:** Bearer JWT через `Authorization: Bearer <token>`
  (ADR-040, Этап 21.a). Получение токена - `POST /api/v1/auth/login`
  (см. секцию Auth ниже). Refresh token - HttpOnly cookie. В dev/test/local
  profile **дополнительно** работает legacy `X-User-Id` header
  (см. подсекцию ниже) - до завершения Этапа 21.b (frontend login UI)

### Аутентификация - Bearer JWT (Этап 21.a, ADR-040)

Все endpoint'ы за исключением:

- `/api/v1/auth/login`, `/register`, `/refresh`, `/logout` (auth flow)
- `/actuator/health`, `/actuator/health/**`, `/actuator/info` (LB
  liveness/readiness probes + CI/CD deploy verification). Остальные
  `/actuator/**` endpoint'ы в prod profile требуют **basic auth**
  через env `ACTUATOR_USERNAME` / `ACTUATOR_PASSWORD` (ADR-048,
  отдельный SecurityFilterChain). В dev/test/local actuator открыт
  на чтение без auth
- `/v3/api-docs/**`, `/swagger-ui/**` (OpenAPI docs)
- `OPTIONS /**` (CORS preflight)
- `GET /api/**` в dev/local/test profile (transitional, см. ADR-040)

требуют валидный access token в header:

```
Authorization: Bearer <jwt>
```

Token содержит UUID пользователя в `sub` claim + username/email/role +
`jti` (UUID, уникальный per-token, требуется для refresh rotation).
Срок жизни access - 15 минут, refresh - 7 дней. Refresh - single-use
(ADR-047), при каждом `/auth/refresh` выдаётся новый refresh, старый
revoked в БД.

- Header отсутствует - `401 Unauthorized`
- Token истёк - `401 Unauthorized` (тип `unauthorized`)
- Token подделан / некорректный - `401 Unauthorized` (тип `unauthorized`)
- Пользователь disabled - `401 Unauthorized`

### Заголовок `X-User-Id` (dev/test fallback, ADR-040)

В profile `local` / `dev` / `test` (default для local dev и для всех
integration тестов) дополнительно работает legacy путь через
`X-User-Id: <uuid>` header. Если SecurityContext пуст и header
содержит существующего user'а в `users` - principal устанавливается
автоматически. В prod profile фильтр не активируется.

Назначение - не ломать existing integration тесты и frontend dev до
появления login UI в Этапе 21.b. После - удалить полностью.

### Пагинация GET-list endpoints

Все list endpoints возвращают обёртку `PagedResponse<T>` (Этап
pagination):

```json
{
  "items": [ /* массив T */ ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3,
  "hasNext": true,
  "hasPrev": false
}
```

- `page` - 0-based номер текущей страницы (echo из запроса)
- `size` - количество элементов на страницу (echo из запроса после
  clamp/default)
- `totalElements` - общее количество строк удовлетворяющих фильтрам
- `totalPages` - `ceil(totalElements/size)`, минимум 1
- `hasNext` - есть ли следующая страница (`page+1 < totalPages`)
- `hasPrev` - есть ли предыдущая (`page > 0`)

**Query параметры pagination (одинаковы для всех list endpoints):**

- `?page=N` - 0-based номер страницы. Default `0`. Отрицательные →
  reset to 0
- `?size=N` - элементов на страницу. Default `20`, **max `100`**
  (значения > 100 clamps до 100). 0 или отрицательное → default

**Без `page/size` параметров** - возвращается первая страница
(`?page=0&size=20`) обёрнутая в `PagedResponse`. Это **breaking change**
для frontend относительно raw-array ответа до Этапа pagination;
backward compat не поддерживается (нет prod).

**Покрытые endpoints:**

- `GET /api/v1/sources` (см. ниже фильтры `?type=&reliability=`)
- `GET /api/v1/authorities` (`?era=`)
- `GET /api/v1/topics` (`?visibility=`)
- `GET /api/v1/library/books` (`?type=&authorityId=&publisherId=`)
- `GET /api/v1/questions` (`?status=&q=` уже были)

## Эндпоинты

### Auth (ADR-040, Этап 21.a)

Все эндпоинты под `/api/v1/auth/*` - публичные (не требуют
аутентификации). Возвращают `Set-Cookie: refresh_token=...` где
применимо (HttpOnly + Secure + SameSite=Strict).

**Rate limiting** (ADR-046): `/auth/login` и `/auth/register` защищены
custom in-memory sliding-window filter. Default disabled (dev/test/local
работают без настройки), в prod включается через
`AUTH_RATE_LIMIT_ENABLED=true`. При превышении лимита:

- HTTP `429 Too Many Requests`
- Header `Retry-After: <seconds>` (целое число секунд до снятия
  lockout)
- Body Problem Details:
  ```json
  {
    "type": "https://argumentmap.example/errors/too-many-requests",
    "title": "Слишком много попыток",
    "status": 429,
    "detail": "Превышен лимит N попыток в минуту. Повторите через X сек.",
    "retryAfterSeconds": 900
  }
  ```

Configurable через env: `AUTH_RATE_LIMIT_LOGIN` (default 5/min),
`AUTH_RATE_LIMIT_REGISTER` (default 3/min), `AUTH_RATE_LIMIT_LOCKOUT`
(default `PT15M`). IP resolution: `X-Forwarded-For` (first) >
`X-Real-IP` > raw `remoteAddr` с port-stripping. Whitelist
`127.0.0.1` + `::1` для CI/smoke. Фронтенд должен обработать 429:
показать пользователю текст из `detail` + отключить форму на
`retryAfterSeconds` секунд (либо до user reload).

#### POST /api/v1/auth/register

Регистрация нового пользователя. Сразу выдаёт access + refresh.

**Запрос:**
```json
{
  "email": "user@example.com",
  "username": "user1",
  "password": "min8chars"
}
```

Валидация: email формат RFC, username 3..50 ASCII буквы/цифры/`_-`,
password 8..100 символов.

**Ответ:** `201 Created`
```json
{
  "accessToken": "<jwt>",
  "accessTokenExpiresAt": "2026-05-17T18:18:27Z",
  "user": {
    "id": "<uuid>",
    "username": "user1",
    "email": "user@example.com",
    "role": "USER"
  }
}
```
+ `Set-Cookie: refresh_token=<jwt>; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=604800`

**Ошибки:**
- 400 `validation` - невалидные поля
- 409 `email-already-taken` - email уже зарегистрирован
- 409 `username-already-taken` - username занят
- 429 `too-many-requests` - превышен rate limit (если enabled)

#### POST /api/v1/auth/login

Логин по email + password.

**Запрос:**
```json
{ "email": "user@example.com", "password": "..." }
```

**Ответ:** `200 OK` тот же формат что у register + cookie.

**Ошибки:**
- 401 `invalid-credentials` - неверный email или пароль, либо disabled
- 429 `too-many-requests` - превышен rate limit (если enabled)

#### POST /api/v1/auth/refresh

Обновление пары access+refresh через refresh cookie. **Single-use
rotation** (ADR-047) - каждый refresh используется один раз, на
выходе возвращается **новый** refresh в Set-Cookie. Попытка повторно
использовать уже rotated refresh = security incident, revoke всех
сессий user'а.

**Запрос:** body пустой, `Cookie: refresh_token=<jwt>` обязателен.

**Ответ:** `200 OK` тот же формат что login + новый
`Set-Cookie: refresh_token=...` (заменяет старый в browser).

**Ошибки:**
- 401 `invalid-token` - cookie отсутствует / невалидный / expired /
  reuse рottated токена (steal detected) / БД-запись revoked

#### POST /api/v1/auth/logout

Revoke incoming refresh в БД + очистка refresh cookie (ADR-047).
Идемпотентно - если cookie отсутствует или токен уже revoked,
просто очищает cookie без ошибки. Access token не invalidates
(короткоживущий, истечёт сам).

**Запрос:** body пустой, `Cookie: refresh_token=<jwt>` optional.

**Ответ:** `204 No Content` + `Set-Cookie: refresh_token=; Max-Age=0`

#### GET /api/v1/auth/me

Текущий аутентифицированный пользователь.

**Заголовки:** `Authorization: Bearer <jwt>` (обязательно)

**Ответ:** `200 OK`
```json
{
  "id": "<uuid>",
  "username": "user1",
  "email": "user@example.com",
  "role": "USER"
}
```

**Ошибки:**
- 401 - не аутентифицирован

### User preferences (Settings screen)

Хранение пользовательских настроек интерфейса. Каждое поле -
независимый key/value под текущим user_id.

**Whitelisted keys** (валидация в `UserPreferenceService`):

| Ключ | Тип | Допустимые значения |
|------|-----|---------------------|
| `locale` | string | `ru` \| `ar` \| `en` |
| `arabicFont` | string | `naskh` \| `kufi` \| `tahoma` |
| `textSize` | string | `small` \| `medium` \| `large` \| `xl` |
| `theme` | string | `system` \| `light` \| `dark` |
| `hideTashkeelByDefault` | boolean | `true` \| `false` |
| `transliteration` | boolean | `true` \| `false` |

Все endpoints требуют authenticated user. Изоляция read/write только
свои prefs (фильтр по `user_id` в репозитории).

#### GET /api/v1/preferences

Возвращает все текущие preferences user'а как `Map<String, Object>`.
Если ключ не установлен - в ответе отсутствует (фронт применяет дефолт).

**Ответ:** `200 OK`
```json
{
  "locale": "ar",
  "textSize": "large",
  "transliteration": true
}
```

#### PUT /api/v1/preferences

Bulk update. Тело - `Map<String, Object>` с одним или несколькими ключами.
Все ключи валидируются до записи (атомарно через `@Transactional`:
ошибка → rollback всех upsert'ов).

**Запрос:**
```json
{
  "locale": "ar",
  "textSize": "large"
}
```

**Ответ:** `200 OK` - обновлённая map.

**Ошибки:**
- 400 `illegal-argument` - неизвестный ключ или невалидное значение
- 401 - не аутентифицирован

#### PUT /api/v1/preferences/{key}

Обновить один ключ. Тело - конверт `{value: ...}` для типизации
(boolean/string/number).

**Запрос:**
```json
{ "value": "ar" }
```

**Ответ:** `200 OK` - вся map после изменения.

#### DELETE /api/v1/preferences/{key}

Удалить ключ - revert на default (фронт читает getAll и не находит
ключа, применяет дефолтное значение).

**Ответ:** `204 No Content`.

**Ошибки:**
- 400 `illegal-argument` - неизвестный ключ
- 401 - не аутентифицирован

### Темы (Topics)

#### POST /api/v1/topics

Создать тему с корневым узлом-вопросом.

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Запрос:**
```json
{
  "title": "Мавлид это бид'а?",
  "description": "Разбор аргументов сторон",
  "rootQuestion": "Является ли празднование мавлида нововведением?",
  "visibility": "PRIVATE"
}
```
- `title`: string, 1-200 символов, обязательно
- `description`: string, 0-2000 символов, опционально (`null` допустимо)
- `rootQuestion`: string, 1-10000 символов, обязательно
- `visibility`: enum `PRIVATE` / `SHARED` / `PUBLIC`, опционально
  (default `PRIVATE` если не передан, ADR-043)

**Ответ (201 Created):**
- Заголовок `Location: /api/v1/topics/{id}`
- Тело:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Мавлид это бид'а?",
  "description": "Разбор аргументов сторон",
  "rootNodeId": "550e8400-e29b-41d4-a716-446655440001",
  "createdBy": "550e8400-e29b-41d4-a716-446655440002",
  "createdAt": "2026-05-03T10:00:00Z",
  "visibility": "PRIVATE",
  "nodeCount": 1,
  "edgeCount": 0
}
```

**Ошибки:**
- `400` - невалидные поля или отсутствует `X-User-Id`
- `422` - `X-User-Id` указывает на несуществующего пользователя

#### GET /api/v1/topics

Список тем **видимых текущему user'у** (ADR-043) с пагинацией. USER видит:
- свои темы (любого visibility)
- SHARED где он member
- все PUBLIC

ADMIN видит все темы без фильтра.

**Заголовки:** `X-User-Id: <uuid>` (обязательно для visibility-фильтра)

**Параметры:**
- `visibility` (опционально) - whitelist `PRIVATE` / `SHARED` / `PUBLIC`.
  Фильтрует **внутри** set'а уже видимых пользователю (USER+visibility=
  PRIVATE = только свои PRIVATE; USER+visibility=PUBLIC = все PUBLIC).
  Невалидное значение → `400 illegal-argument`
- `page` (опционально, default 0), `size` (default 20, max 100)

**Сортировка:** `created_at DESC` (последние созданные сверху - для UI
list page consistency с sources/questions). Старый internal endpoint
findVisibleToUserWithCounts остался с ASC для обратной совместимости

**Ответ (200 OK):** `PagedResponse<TopicResponse>`.

#### GET /api/v1/topics/{topicId}

Одна тема по id.

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Ответ (200 OK):** `TopicResponse`.

**Ошибки:**
- `403 forbidden-topic-access` - нет прав на чтение (PRIVATE не owner,
  SHARED не member)
- `404` - тема не найдена

#### PATCH /api/v1/topics/{topicId}

Partial update title / description темы (backlog tech debt #10).
**Только owner или EDITOR** (через `assertCanWrite`). PUBLIC viewer без
EDITOR membership получит 403.

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Запрос:**
```json
{
  "title": "Новое название",
  "description": "Новое описание"
}
```

PATCH-семантика per-field:

- `null` (или поле отсутствует) - не редактировать (no change)
- non-null - заменить текущее значение

Поле `description` принимает пустую строку `""` (clear description).
Поле `title` non-null обязано пройти `@Size(1..200)` - blank отвергается
(тема обязана иметь название). Если оба поля совпадают с текущими -
no-op (audit_log запись не пишется).

**Ответ (200 OK):** `TopicResponse` с обновлёнными полями + `nodeCount`
/ `edgeCount`

**Audit log (ADR-043 Amendment 3):** запись `UPDATE` с `FieldDiff(old,
new)` только для реально изменившихся полей (через
`AuditLogService.diff().compare(...)`)

**Ошибки:**
- `400` - validation: пустой/слишком длинный `title`, слишком длинный
  `description`
- `403 forbidden-topic-access` - PRIVATE non-owner non-member
- `403 forbidden-topic-write` - PUBLIC viewer без EDITOR / SHARED MEMBER
  (без EDITOR)
- `404` - тема не найдена

#### PATCH /api/v1/topics/{topicId}/visibility

Сменить visibility темы. **Только owner или ADMIN** (ADR-043).

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Запрос:**
```json
{ "visibility": "PUBLIC" }
```
- `visibility`: `PRIVATE` / `SHARED` / `PUBLIC`, обязательно

**Ответ (200 OK):** `TopicResponse` с обновлённым visibility.

**Ошибки:**
- `400` - невалидное значение
- `403 forbidden-topic-write` - не owner и не ADMIN
- `404` - тема не найдена

#### PATCH /api/v1/topics/{topicId}/status-algorithm

Сменить алгоритм пересчёта статусов узлов темы (ADR-044). **Только owner
или ADMIN**. Side effect - сразу запускает пересчёт всех узлов под новым
алгоритмом в той же транзакции

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Запрос:**
```json
{ "algorithm": "DUNG_GROUNDED" }
```
- `algorithm`: `MVP` (default fixpoint, см. ADR-007) либо `DUNG_GROUNDED`
  (grounded labelling Dung's framework, см. ADR-044), обязательно

**Ответ (200 OK):** `TopicResponse` с обновлённым `statusAlgorithm`. Если
значение совпадает с текущим - no-op (audit не пишется, recalculate не
запускается)

**Ошибки:**
- `400` - невалидное значение (не MVP / DUNG_GROUNDED)
- `403 forbidden-topic-write` - не owner и не ADMIN
- `404` - тема не найдена

**Семантические заметки:**

- `MVP` учитывает SUPPORTS/REFUTES/INVALIDATES, выставляет STANDING/
  DISPUTED/REFUTED/UNVERIFIED. Поддерживает пометку «свежий узел»
  через UNVERIFIED
- `DUNG_GROUNDED` учитывает только attack-edges (REFUTES + INVALIDATES),
  игнорирует SUPPORTS/QUALIFIES/RESPONDS_TO. Mapping label → status:
  IN → STANDING, OUT → REFUTED, UNDEC → DISPUTED. UNVERIFIED не
  используется - все ноды получают определённый label

#### DELETE /api/v1/topics/{topicId}

Удалить тему. **Только owner или ADMIN** (ADR-043). EDITOR не может
удалять, даже на SHARED. Каскадно удаляет узлы, рёбра, ревизии, привязки,
topic_members.

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Ответ (204 No Content):** без тела.

**Ошибки:**
- `403 forbidden-topic-write` - не owner и не ADMIN
- `404` - тема не найдена

#### GET /api/v1/topics/{topicId}/graph

Получить весь граф темы (узлы + рёбра) в плоской форме - так, как
потребляют graph-библиотеки (React Flow, Cytoscape).

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Ответ (200 OK):**
```json
{
  "topic": { ... TopicResponse ... },
  "nodes": [ { ... NodeResponse ... } ],
  "edges": [ { ... EdgeResponse ... } ]
}
```

**Ошибки:**
- `403 forbidden-topic-access` - нет прав на чтение темы
- `404` - тема не найдена

### Topic members (ADR-043, Этап 22)

Управление со-редакторами SHARED-тем. Поле `role`: `MEMBER` (read-only)
или `EDITOR` (read+write).

#### POST /api/v1/topics/{topicId}/members

Добавить user как member темы. **Только owner или ADMIN**.

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Запрос:**
```json
{
  "userId": "...",
  "role": "EDITOR"
}
```

**Ответ (201 Created):** `TopicMemberResponse`
```json
{
  "id": "...",
  "topicId": "...",
  "userId": "...",
  "role": "EDITOR",
  "addedAt": "2026-05-17T10:00:00Z",
  "addedBy": "..."
}
```

**Ошибки:**
- `400` - невалидная роль / owner добавляется как member / user уже member
- `403 forbidden-topic-write` - не owner и не ADMIN
- `404` - тема не найдена

#### GET /api/v1/topics/{topicId}/members

Список членов темы. Доступно всем кто имеет read access к теме (см.
правила ADR-043).

**Ответ (200 OK):** массив `TopicMemberResponse`.

**Ошибки:**
- `403 forbidden-topic-access` - нет read доступа
- `404` - тема не найдена

#### PATCH /api/v1/topics/{topicId}/members/{memberId}

Сменить роль члена темы. **Только owner или ADMIN**.

**Запрос:**
```json
{ "role": "EDITOR" }
```

**Ответ (200 OK):** обновлённый `TopicMemberResponse`.

**Ошибки:**
- `400` - невалидная роль
- `403 forbidden-topic-write` - не owner и не ADMIN
- `404 topic-member-not-found` - запись не существует или относится к
  другой теме

#### DELETE /api/v1/topics/{topicId}/members/{memberId}

Удалить члена. **Owner или ADMIN** удаляет любого, **member может
удалить только себя** (self-leave). EDITOR не может удалить других
EDITOR'ов.

**Ответ (204 No Content):** без тела.

**Ошибки:**
- `403 forbidden-topic-write` - не owner и не self-leave
- `404 topic-member-not-found` - запись не существует или относится к
  другой теме

### Узлы (Nodes)

#### POST /api/v1/nodes

Создать узел в теме. Триггерит пересчёт статусов темы (но новый узел
без рёбер не меняет статусы соседей).

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Запрос:**
```json
{
  "topicId": "uuid",
  "nodeType": "QUESTION|CLAIM|ARGUMENT|EVIDENCE",
  "content": "string, 1-10000 символов"
}
```

**Ответ (201 Created):**
- Заголовок `Location: /api/v1/nodes/{id}`
- Тело: `NodeResponse` (см. ниже)

**Ошибки:**
- `400` - невалидные поля
- `404` - тема не найдена

#### PATCH /api/v1/nodes/{nodeId}

Обновить узел. Все поля опциональные, но **хотя бы одно** должно быть
указано. Если только `content` - пишется revision (before/after), не
триггерит пересчёт статусов. Если только `posX`+`posY` - меняются
координаты на канвасе, не пишется revision, не меняется `updatedAt`.
Если оба - оба применяются последовательно, ответ содержит финальное
состояние узла.

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Запрос:**
```json
{
  "content": "новое содержимое",
  "posX": 100.5,
  "posY": -42.0
}
```
- `content`: 1-10000 символов, опционально
- `posX`: число, опционально - X-координата на канвасе
- `posY`: число, опционально - Y-координата на канвасе. `posX` и
  `posY` всегда вместе (один без другого игнорируется)

**Ответ (200 OK):** обновлённый `NodeResponse`.

**Ошибки:**
- `400` - невалидное содержимое или пустое тело без полей
- `404` - узел не найден

#### DELETE /api/v1/nodes/{nodeId}

Удалить узел. Каскадно удаляет входящие/исходящие рёбра, ревизии,
привязки. Триггерит пересчёт статусов темы.

**Ответ (204 No Content):** без тела.

**Ошибки:**
- `404` - узел не найден

#### DELETE /api/v1/nodes/bulk

Групповое удаление узлов одним запросом. Все узлы должны принадлежать
одной теме. Корневые узлы (`topics.root_node_id`) пропускаются и
возвращаются в `skippedRootIds` - не fail'ят весь запрос. Несуществующие
id - 404 (rollback всей транзакции).

Backlog «Bulk audit log consolidation» - вместо N отдельных DELETE'ов
(каждый пишет свой `audit_log` row, что в admin UI выглядит как N
несвязанных событий) - один `BULK_DELETE` audit row с массивом
`entityIds` в `changes` JSON. Один пересчёт статусов на topic, один
permission-чек

**Заголовки:** `X-User-Id: <uuid>` (обязательно). Требуется
`canWriteTopic` на theme

**Тело:**
```json
{
  "nodeIds": ["uuid1", "uuid2", "..."]
}
```

`nodeIds` - non-empty, max 100 (защита от случайной DoS-нагрузки)

**Ответ (200 OK):**
```json
{
  "deletedIds": ["uuid1", "uuid2"],
  "skippedRootIds": ["uuid3"]
}
```

**Ошибки:**
- `400` - пустой/слишком большой список, узлы из разных тем
- `403` - `forbidden-topic-access` / `forbidden-topic-write`
- `404` - один из узлов не найден (вся транзакция rollback)

#### GET /api/v1/nodes/{nodeId}/revisions

История изменений содержимого узла, в хронологическом порядке.

**Ответ (200 OK):** массив `RevisionResponse`.

**Ошибки:**
- `404` - узел не найден

#### POST /api/v1/nodes/{nodeId}/z-order/bring-to-front

Ставит узел на передний план относительно других узлов темы.
Сервер вычисляет `z_index = MAX(z_index по теме) + 1` и присваивает
узлу. Клиенту не нужно знать текущий max - один POST без подсчёта.

Семантически идемпотентный: повторный вызов всё ещё ставит узел
выше других (max+1 относительно новых max), даже если узел уже
был сверху.

Не пишет revision и не меняет `updatedAt` - z-order это UI affordance,
не доменное изменение содержимого (по аналогии с `updatePosition`).

**Заголовки:** `X-User-Id: <uuid>` (обязательно). Требуется `canWriteTopic`.

**Запрос:** пустое тело (можно `{}`).

**Ответ (200 OK):** обновлённый `NodeResponse` с новым `zIndex`.

**Ошибки:**
- `403` - `forbidden-topic-access` (нет доступа к private теме) или
  `forbidden-topic-write` (есть read но нет write)
- `404` - `node-not-found`

#### POST /api/v1/nodes/{nodeId}/z-order/send-to-back

Парный endpoint. `z_index = MIN(z_index по теме) - 1`. Контракт
идентичный `bring-to-front` - заголовки, запрос, ответ, ошибки.

### Multi-translation узлов (миграция 45, translator attribution)

Один узел может иметь несколько переводов от разных переводчиков
(Кулиев, Sahih International, Османов и т.д.) на разных языках. Каждый
перевод имеет attribution (имя переводчика, опциональное - анонимный
допустим) и `isDefault` флаг (какой показывать по умолчанию).

**Permission:** все mutating - canWriteTopic (та же permission что и
узел сам). GET - canReadTopic.

**Уникальность:** один переводчик (либо `null` translator) - один перевод
для узла на язык. Дубль → 409 `node-translation-duplicate`. Внутри одного
узла одновременно только один `isDefault=true`.

#### POST /api/v1/nodes/{nodeId}/translations

Добавить новый перевод.

**Тело:**
```json
{
  "translatorName": "Кулиев",
  "language": "ru",
  "body": "Деяния оцениваются по намерениям",
  "isDefault": false
}
```
`translatorName` - nullable / пустая = анонимный. `language` ∈
`{ru, en}`. `body` non-blank. `isDefault` - optional, default false.

**Спецсемантика:** первый перевод узла всегда `isDefault=true` независимо
от поля в payload (узел не может быть без default-перевода пока есть
хоть один). Если клиент явно передал `isDefault=true` и в узле уже был
default - фон-перевод теряет флаг atomically.

**Ответ:** 201 + `NodeTranslationRef`.

**Ошибки:**
- `400 invalid language` - language ∉ {ru, en}
- `400 illegal-argument` - blank body
- `403 forbidden-topic-access`/`forbidden-topic-write` - не owner/EDITOR
- `404 node-not-found`
- `409 node-translation-duplicate` - translator+language уже есть

#### GET /api/v1/nodes/{nodeId}/translations

Список переводов узла. Сортировка: default первым, далее по
`created_at ASC`. Возвращает `NodeTranslationRef[]`.

#### PATCH /api/v1/nodes/translations/{translationId}

Обновить body и/или translatorName. `isDefault` НЕ меняется через PATCH -
для смены default используйте отдельный action `POST .../default`.

**Тело:**
```json
{
  "translatorName": "Османов",
  "body": "Деяния по намерениям"
}
```
- `translatorName` null / пустая = переводчик становится анонимным
- `body` null / пустая / blank = body остаётся прежним (не очищается)

**Ответ:** 200 + `NodeTranslationRef`.

**Ошибки:** 403, 404 `node-translation-not-found`.

#### POST /api/v1/nodes/translations/{translationId}/default

Atomic action: пометить перевод как default, снять флаг с остальных
переводов того же узла. Отдельный POST action а не PATCH с `isDefault=true`
потому что меняет state других переводов узла (не партиальный update).
Запрос без тела.

**Ответ:** 200 + `NodeTranslationRef` (с `isDefault=true`).

**Ошибки:** 403, 404 `node-translation-not-found`.

#### DELETE /api/v1/nodes/translations/{translationId}

Удалить перевод. Если удалённый был default - oldest из оставшихся
переводов автоматически становится новым default (узел не остаётся без
default-перевода пока есть хоть один).

**Ответ:** 204.

**Ошибки:** 403, 404 `node-translation-not-found`.

### Голосование за вес аргументов (миграция 38)

Пользователи могут голосовать за/против узлов (`EVIDENCE`/`ARGUMENT`)
усиливая или ослабляя их в графе. Один user - один голос на один node,
значения `+1` (upvote, поддержать) и `-1` (downvote, не согласен).
Нейтральная позиция не сохраняется отдельно - вместо неё row удаляется.
Голоса агрегируются и попадают в `NodeResponse.vote*` поля при загрузке
графа.

**Permission модель:** vote требует только read-access к topic'у узла -
голос это reaction, не write. Анонимный user не может голосовать
(вернётся 401 / 400 missing-user-header).

**Влияние на статусы:** vote'ы НЕ участвуют в StatusCalculation (Dung-style
аргументация остаётся неизменной). Голоса - параллельный сигнал силы
от сообщества.

**MVP 3-point scale** `{-1, +1}`. Возможное расширение до 5-point
`{-2..+2}` (категории силы) - в backlog'е.

#### POST /api/v1/nodes/{nodeId}/vote

Upsert голоса вызывающего user'а. Идемпотентен - повторный POST с тем
же weight оставляет всё как есть; POST с другим weight меняет.

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Запрос:**
```json
{ "weight": 1 }
```
- `weight`: int, обязательно. Допустимые значения: `1` или `-1`.

**Ответ (201 Created):**
```json
{
  "nodeId": "uuid",
  "upvotes": 4,
  "downvotes": 1,
  "score": 3,
  "userVote": 1
}
```

**Ошибки:**
- `400 invalid-vote` - weight не из `{-1, +1}`
- `400` - validation (weight отсутствует)
- `404 node-not-found` - узла нет
- `403 forbidden-topic-access` - не видит тему (PRIVATE чужого user'а)

#### DELETE /api/v1/nodes/{nodeId}/vote

Снять голос. Идемпотентен - если голоса не было, возвращает 204
без ошибки.

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Ответ:** `204 No Content`

**Ошибки:**
- `404 node-not-found` - узла нет
- `403 forbidden-topic-access` - не видит тему

#### GET /api/v1/nodes/{nodeId}/votes

Текущая статистика голосов + персональный голос вызывающего user'а.

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Ответ (200 OK):** `NodeVoteStatsResponse` - та же схема что у POST.
`userVote` = `null` если вызывающий user не голосовал.

**Ошибки:**
- `404 node-not-found`
- `403 forbidden-topic-access`

### Рёбра (Edges)

#### POST /api/v1/edges

Создать ребро между двумя узлами одной темы. Триггерит пересчёт
статусов темы.

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Запрос:**
```json
{
  "fromNodeId": "uuid",
  "toNodeId": "uuid",
  "edgeType": "SUPPORTS|REFUTES|QUALIFIES|INVALIDATES|RESPONDS_TO",
  "rationale": "string, 0-2000 символов, опционально",
  "sourceHandle": "top|right|bottom|left, опционально",
  "targetHandle": "top|right|bottom|left, опционально"
}
```
- `sourceHandle`/`targetHandle` - id точки подключения ребра на
  сторонах узлов (для UI с 4-handles React Flow). Опциональные:
  если не указаны, фронт применит auto-routing по позициям

**Ответ (201 Created):**
- Заголовок `Location: /api/v1/edges/{id}`
- Тело: `EdgeResponse`

**Ошибки:**
- `400` - невалидные поля
- `404` - один из узлов не найден
- `422` - бизнес-нарушение:
  - `invalid-edge`: ребро от узла к самому себе
  - `invalid-edge`: узлы из разных тем

#### PATCH /api/v1/edges/{edgeId}

Частичное обновление ребра. Поля null/отсутствующие сохраняют текущее
значение, не-null - применяются. Финальное состояние ребра валидируется
целиком (selfloop, граница темы, матрица ADR-010). Если валидация не
проходит - 422 и ребро в БД не меняется (всё-или-ничего, ADR-014).
Используется фронтом для reconnect (перетаскивание конца ребра на
другой handle).

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Запрос:** все поля опциональные (хотя бы одно должно быть указано):
```json
{
  "fromNodeId": "uuid",
  "toNodeId": "uuid",
  "edgeType": "SUPPORTS|REFUTES|QUALIFIES|INVALIDATES|RESPONDS_TO",
  "rationale": "string, 0-2000 символов",
  "sourceHandle": "top|right|bottom|left",
  "targetHandle": "top|right|bottom|left"
}
```

Замечание: через PATCH нельзя "очистить" rationale/sourceHandle/
targetHandle (выставить null) - null трактуется как "не передано". Для
очистки в MVP не предусмотрено, потребуется отдельный feature.

**Ответ (200 OK):** `EdgeResponse` с финальным состоянием ребра.

**Поведение пересчёта статусов:**
- Если изменился `fromNodeId`/`toNodeId`/`edgeType` - пересчёт темы
- Если только `rationale`/`sourceHandle`/`targetHandle` - пересчёт не нужен

**Ошибки:**
- `400` - все поля null (`illegal-argument`) или невалидные значения
  полей (`validation`)
- `404` - ребро не найдено (`edge-not-found`) или один из новых
  `fromNodeId`/`toNodeId` указывает на несуществующий узел (`node-not-found`)
- `422` - `invalid-edge`:
  - ребро от узла к самому себе
  - узлы из разных тем
  - тип связи недопустим для пары типов узлов (ADR-010)

#### DELETE /api/v1/edges/{edgeId}

Удалить ребро. Триггерит пересчёт статусов темы.

**Ответ (204 No Content):** без тела.

**Ошибки:**
- `404` - ребро не найдено

#### POST /api/v1/edges/{edgeId}/z-order/bring-to-front

Ставит ребро на передний план относительно других рёбер темы.
Сервер вычисляет `z_index = MAX(z_index рёбер темы) + 1` и присваивает
ребру. Тема определяется через from-узел ребра. Клиенту не нужно знать
текущий max - один POST без подсчёта.

Не пишет revision и не меняет `updatedAt` - z-order это UI affordance,
не доменное изменение. Mirror `POST /nodes/{id}/z-order/bring-to-front`.

**Заголовки:** `X-User-Id: <uuid>` (обязательно). Требуется `canWriteTopic`.

**Запрос:** пустое тело (можно `{}`).

**Ответ (200 OK):** обновлённый `EdgeResponse` с новым `zIndex`.

**Ошибки:**
- `403` - `forbidden-topic-access` (нет доступа к private теме) или
  `forbidden-topic-write` (есть read но нет write)
- `404` - `edge-not-found`

#### POST /api/v1/edges/{edgeId}/z-order/send-to-back

Парный endpoint. `z_index = MIN(z_index рёбер темы) - 1`. Контракт
идентичный `bring-to-front` - заголовки, запрос, ответ, ошибки.

### Источники (Sources)

#### POST /api/v1/sources

Создать источник в справочнике.

**Запрос:**
```json
{
  "sourceType": "QURAN|HADITH|BOOK|ARTICLE|URL",
  "title": "Сахих аль-Бухари",
  "citation": "том 1, хадис 4",
  "reliability": "SAHIH|HASAN|DAIF",
  "authorityId": "uuid|null",
  "metadata": { "collection": "bukhari", "book": 1, "hadith": 4 }
}
```
- `title`: 1-500 символов, обязательно
- `citation`: до 2000 символов, опционально
- `reliability`: только для `sourceType=HADITH`. Для других типов
  обязан быть `null` - иначе 422 (`invalid-source`)
- `authorityId`: UUID учёного-автора труда, опционально. Должен ссылаться
  на существующего `Authority` - иначе 404 (`authority-not-found`).
  Для `QURAN` и анонимных текстов остаётся `null`
- `metadata`: произвольный JSON-объект, опционально

**Ответ (201 Created):**
- Заголовок `Location: /api/v1/sources/{id}`
- Тело: `SourceResponse`

**Ошибки:**
- `400` - невалидные поля
- `404` - указан `authorityId` несуществующего учёного
- `422` - `reliability` указан для не-`HADITH`

#### GET /api/v1/sources

Список источников или поиск по названию. **Пагинация** через
`?page=&size=` (см. секцию «Пагинация GET-list endpoints»).

**Параметры:**
- `q` (опционально) - подстрока для поиска по `title` (case-insensitive)
- `type` (опционально) - whitelist: `QURAN` / `HADITH` / `BOOK` / `ARTICLE` / `URL`
- `reliability` (опционально) - whitelist: `SAHIH` / `HASAN` / `DAIF`.
  **Допустим только когда `type=HADITH`** (или type не задан); иначе
  `400 illegal-argument` («фильтр reliability допустим только при
  type=HADITH»)
- `page` (опционально, default 0) - 0-based номер страницы
- `size` (опционально, default 20, max 100) - элементов на страницу

**Сортировка:** `created_at DESC` (новые источники сверху)

**Ответ (200 OK):** `PagedResponse<SourceResponse>`.

**Пример:**
```bash
curl 'http://localhost:9090/api/v1/sources?type=HADITH&reliability=SAHIH&size=10'
```

#### GET /api/v1/sources/{sourceId}

Один источник.

**Ошибки:** `404` - не найден (`source-not-found`).

#### DELETE /api/v1/sources/{sourceId}

Удалить источник. Каскадно удаляет привязки к узлам.

**Ответ:** `204 No Content`.

**Ошибки:** `404`.

### Авторитеты (Authorities)

#### POST /api/v1/authorities

Создать авторитет.

**Запрос:**
```json
{
  "name": "Ибн Таймия",
  "bio": "Известный учёный...",
  "era": "XIII-XIV век",
  "madhab": "ханбалитский",
  "metadata": { "birth_year": 1263 },
  "type": "SCHOLAR"
}
```
- `name`: 1-500 символов, обязательно
- `bio`: до 10000 символов, опционально
- `era`: до 100 символов, опционально
- `madhab`: до 100 символов, опционально
- `metadata`: произвольный JSON, опционально
- `type`: whitelist `SCHOLAR / MUHAQQIQ / PUBLISHER / AUTHOR / OTHER`,
  опционально (default `SCHOLAR`). Семантическая роль авторитета -
  определяет, можно ли использовать его как scholar в оценке хадиса
  (`HadithGrade.addGrade` принимает только `SCHOLAR`)

**Ответ (201 Created):**
- Заголовок `Location: /api/v1/authorities/{id}`
- Тело: `AuthorityResponse` (включая поле `type`)

**Ошибки:**
- `400` - невалидные поля
- `400 invalid-authority-type` (property `invalidType`) - `type` не в
  whitelist

#### GET /api/v1/authorities

Список авторитетов с пагинацией и фильтрами.

**Параметры:**
- `q` (опционально) - подстрока для поиска по `name` (case-insensitive)
- `era` (опционально) - exact match по эпохе (свободный текст,
  например `XIII-XIV век`, `сахаба`, `табиины`). Без enum-whitelist
- `page` (опционально, default 0) - 0-based страница
- `size` (опционально, default 20, max 100)

**Note:** `madhab` как фильтр **не в MVP**. Свободный текст и
variability (ханбалитский / Hanbali / حنبلي) делают фильтр без
нормализации малополезным. Когда понадобится - вводим master-data
таблицу мазхабов с FK.

**Сортировка:** `name ASC` (исторический порядок справочника учёных)

**Ответ (200 OK):** `PagedResponse<AuthorityResponse>`.

#### GET /api/v1/authorities/{authorityId}

Один авторитет. `404` если не найден (`authority-not-found`).

#### DELETE /api/v1/authorities/{authorityId}

Удаление + каскад привязок. `204` или `404`.

#### PATCH /api/v1/authorities/{authorityId}

Частичное обновление авторитета. Поля null/отсутствующие сохраняют текущее
значение, не-null - применяются.

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Запрос:** все поля опциональные:
```json
{
  "name": "Ибн Таймия",
  "bio": "Известный учёный...",
  "era": "XIII-XIV век",
  "madhab": "ханбалитский",
  "metadata": { "birth_year": 1263 },
  "type": "SCHOLAR"
}
```
- Поля: те же ограничения что у `POST /api/v1/authorities`
- `type`: whitelist `SCHOLAR / MUHAQQIQ / PUBLISHER / AUTHOR / OTHER`

**Ответ (200 OK):** `AuthorityResponse` (полный record с обновлёнными полями).

**Ошибки:**
- `400` - невалидные поля или `type` не в whitelist (`invalid-authority-type`,
  property `invalidType`)
- `404` - авторитет не найден (`authority-not-found`)

### Привязка источников к узлам

#### POST /api/v1/nodes/{nodeId}/sources

Привязать источник к узлу.

**Запрос:**
```json
{
  "sourceId": "uuid",
  "quote": "точная цитата",
  "context": "комментарий по использованию",
  "location": "стр. 42 / т.3 с.137 / Бухари 2010 / 7:54"
}
```
- `quote`: до 10000 символов, опционально - точная цитата (на арабском
  для нассов, RTL рендерится на фронте)
- `context`: до 2000 символов, опционально - как цитата подкрепляет узел
- `location`: до 200 символов, опционально - точное место в источнике
  (страница, том+страница, номер хадиса, сура:аят)

**Ответ (201 Created):** `NodeSourceResponse`.

**Ошибки:**
- `400` - невалидные поля
- `404` - узел или источник не найден

#### GET /api/v1/nodes/{nodeId}/sources

Список источников, привязанных к узлу. `404` если узел не найден.

**Ответ (200 OK):** массив `NodeSourceResponse`.

#### DELETE /api/v1/nodes/{nodeId}/sources/{nodeSourceId}

Отвязать конкретный citation link по surrogate id (ADR-029).
`nodeSourceId` - значение `NodeSourceResponse.id`, не `sourceId`.
Точечный detach позволяет удалить один из N citation'ов на ту же пару
(node, source) - например снять цитату с т.1 стр.45 оставив т.2 стр.110.

**Ответ:** `204 No Content`. `404` - привязка не найдена.

> **Breaking change** (Сессия 32, ADR-029): раньше path был
> `/sources/{sourceId}` и удалял все citation'ы пары (node, source).
> Теперь обязателен `nodeSourceId` (UUID самой связи).

### Привязка авторитетов к узлам — удалено в ADR-017

Эндпоинты `POST/GET/DELETE /api/v1/nodes/{nodeId}/authorities` удалены.
Авторитет теперь приходит к узлу транзитивно через `Source.authorityId`
(см. ADR-017). Чтобы выразить «учёный X стоит за тезисом Y» - создаётся
`Source` с `authorityId = X.id` и привязывается к узлу `Y` через
`POST /api/v1/nodes/{Y}/sources`. Для отрицательной позиции учёного -
`REFUTES`-ребро на узел.

`POST/GET/DELETE /api/v1/authorities` (master data CRUD) сохраняется -
используется для поиска и inline-создания авторитета при создании
`Source`.

## Общие типы ответов

### TopicResponse
```json
{
  "id": "uuid",
  "title": "string",
  "description": "string|null",
  "rootNodeId": "uuid|null",
  "createdBy": "uuid",
  "createdAt": "iso8601",
  "visibility": "PRIVATE|SHARED|PUBLIC",
  "statusAlgorithm": "MVP|DUNG_GROUNDED",
  "nodeCount": 12,
  "edgeCount": 18
}
```

`nodeCount` / `edgeCount` (int) - агрегаты числа узлов и рёбер темы.
Заполняются на всех эндпоинтах возвращающих TopicResponse:
- `GET /api/v1/topics` (list) - один SQL с агрегатными
  LEFT JOIN-подзапросами для всех тем сразу
- `GET /api/v1/topics/{id}` (one) - тот же SQL с фильтром по id
- `POST /api/v1/topics` (create) - дополнительный запрос после
  транзакции создания, чтобы вернуть честные значения
  (1 узел = корневой вопрос, 0 рёбер). Через TopicService.getTopicWithCounts

См. ADR-016. Для отображения карточки темы с мини-графом во фронте.

`statusAlgorithm` (string) - алгоритм пересчёта статусов узлов темы
(ADR-044). Default `MVP` (см. ADR-007), `DUNG_GROUNDED` для grounded
labelling Dung's framework. Меняется через `PATCH /topics/{id}/status-
algorithm` (owner only)

### NodeResponse
```json
{
  "id": "uuid",
  "topicId": "uuid",
  "nodeType": "QUESTION|CLAIM|ARGUMENT|EVIDENCE",
  "content": "string",
  "status": "STANDING|DISPUTED|REFUTED|UNVERIFIED",
  "posX": 123.45,
  "posY": -67.89,
  "zIndex": 0,
  "createdBy": "uuid",
  "createdAt": "iso8601",
  "updatedAt": "iso8601",
  "voteUpvotes": 0,
  "voteDownvotes": 0,
  "voteScore": 0,
  "userVote": null,
  "inlineCitations": [
    {
      "ordinal": 1,
      "nodeSourceId": "uuid",
      "sourceId": "uuid",
      "sourceType": "QURAN|HADITH|BOOK|ARTICLE|URL",
      "title": "string|null",
      "citation": "string|null",
      "quote": "string|null",
      "reliability": "SAHIH|HASAN|DAIF|null"
    }
  ],
  "originalLang": "ar|ru|en|null",
  "translations": [
    {
      "id": "uuid",
      "translatorName": "string|null",
      "language": "ru|en",
      "body": "string",
      "isDefault": true
    }
  ]
}
```
`posX`/`posY` - координаты узла на канвасе графа. `null` для
узлов, которые ещё не перетаскивались (фронт применит автолейаут).

`zIndex` - stacking order (миграция 40). Default 0 для всех узлов
до первого `bring-to-front` / `send-to-back`. Управляется через
два dedicated endpoint - см. секцию ниже.

`voteUpvotes`/`voteDownvotes`/`voteScore` - агрегаты голосов
(миграция 38, см. секцию «Голосование за вес аргументов»).
Заполняются на GET `/api/v1/topics/{id}/graph` (bulk-load один SQL
на весь граф) и mutating endpoints на узлах. `userVote` ∈ `{-1, +1, null}`
- голос вызывающего user'а, `null` если не голосовал. На POST `/api/v1/nodes`
для нового узла поля заполняются нулями (голосов ещё нет).

`inlineCitations` (array) - лёгкие ссылки на node_sources для рендеринга
inline-маркеров `[N]` в `content`. Подход A (implicit ordinal): фронт
парсит `[1]`, `[2]` в content и находит ref по `ordinal` (1-based,
совпадает с порядком `node_sources.created_at ASC`). Bulk-load на GET
`/topics/{id}/graph` (один SQL на весь граф, не N+1). Mutating endpoints
(POST/PATCH `/api/v1/nodes`) подгружают citations точечно для одного
узла. Если у узла нет node_sources - пустой массив. `reliability`
заполняется только для `sourceType=HADITH` - frontend показывает
SAHIH/HASAN/DAIF в popover. `title` fallback chain: `book.title →
source.title`.

`originalLang` (миграция 44) - язык оригинала (nullable). null означает
что frontend авто-определит по `hasArabicScript(content)` (MVP - только
AR vs RU; для EN-оригинала задавать явно). На POST/PATCH принимаются
через `CreateNodeRequest` / `UpdateNodeRequest`. В UpdateNodeRequest
пустая строка означает «очистить» поле, отсутствие в payload - «не
менять».

`translations` (миграция 45, multi-translation 1:N) - список переводов
узла с attribution переводчика. Один узел может иметь несколько переводов
от разных переводчиков (Кулиев, Sahih International, Османов и т.д.) на
разных языках. Bulk-load на GET `/topics/{id}/graph` (один SQL на весь
граф, не N+1). На POST/PATCH `/api/v1/nodes` подгружаются точечно для
одного узла. Сортировка: default-перевод первым, далее по created_at ASC.
- `translatorName` - имя переводчика. nullable (анонимный переводчик)
- `language` ∈ `{ru, en}` - язык перевода
- `body` - текст перевода (NOT NULL)
- `isDefault` - какой перевод показывать по умолчанию (один на узел)

CRUD - см. секцию «Multi-translation узлов» ниже.

### NodeVoteStatsResponse
```json
{
  "nodeId": "uuid",
  "upvotes": 4,
  "downvotes": 1,
  "score": 3,
  "userVote": 1
}
```
Возвращается на POST/GET /api/v1/nodes/{id}/vote(s).
`score` = `upvotes - downvotes` (может быть отрицательным).
`userVote` ∈ `{-1, +1, null}`.

### EdgeResponse
```json
{
  "id": "uuid",
  "fromNodeId": "uuid",
  "toNodeId": "uuid",
  "edgeType": "SUPPORTS|REFUTES|QUALIFIES|INVALIDATES|RESPONDS_TO",
  "rationale": "string|null",
  "sourceHandle": "string|null",
  "targetHandle": "string|null",
  "createdBy": "uuid",
  "createdAt": "iso8601",
  "zIndex": 0
}
```
`sourceHandle`/`targetHandle` - id точки подключения ребра на
сторонах узлов. `null` для рёбер созданных не через drag-create
(например, через bulk-импорт) - фронт применит auto-routing.

`zIndex` (миграция 40) - stacking order ребра относительно других рёбер
темы. Default 0. Управляется через
`POST /edges/{id}/z-order/bring-to-front` и `send-to-back`.

### RevisionResponse
```json
{
  "id": "uuid",
  "nodeId": "uuid",
  "contentBefore": "string|null",
  "contentAfter": "string",
  "changedBy": "uuid",
  "changedAt": "iso8601"
}
```

### SourceResponse
```json
{
  "id": "uuid",
  "sourceType": "QURAN|HADITH|BOOK|ARTICLE|URL",
  "title": "string",
  "citation": "string|null",
  "reliability": "SAHIH|HASAN|DAIF|null",
  "authorityId": "uuid|null",
  "metadata": { ... } | null,
  "createdAt": "iso8601"
}
```

### AuthorityResponse
```json
{
  "id": "uuid",
  "name": "string",
  "bio": "string|null",
  "era": "string|null",
  "madhab": "string|null",
  "metadata": { ... } | null,
  "createdAt": "iso8601",
  "fullName": "string|null",
  "deathYearHijri": "integer|null",
  "type": "SCHOLAR | MUHAQQIQ | PUBLISHER | AUTHOR | OTHER"
}
```

`type` (миграция 47) - семантическая роль authority. До этой миграции
был flat namespace. `HadithGradeService.addGrade` принимает только
authorities с `type=SCHOLAR` (см. ошибку `invalid-scholar-authority`).

### NodeSourceResponse
```json
{
  "nodeId": "uuid",
  "sourceId": "uuid",
  "quote": "string|null",
  "context": "string|null",
  "location": "string|null",
  "createdAt": "iso8601"
}
```

`NodeAuthorityResponse` удалён (ADR-017).

### GraphResponse
```json
{
  "topic": { ... TopicResponse ... },
  "nodes": [ { ... NodeResponse ... } ],
  "edges": [ { ... EdgeResponse ... } ]
}
```

### ErrorResponse (Problem Details, RFC 7807)
```json
{
  "type": "https://argumentmap.example/errors/<code>",
  "title": "Короткое название",
  "status": 404,
  "detail": "Подробное описание",
  "instance": "/api/v1/..."
}
```

Известные `type`-коды:
- `topic-not-found` (404)
- `node-not-found` (404)
- `edge-not-found` (404)
- `source-not-found` (404)
- `authority-not-found` (404)
- `book-not-found` (404)
- `page-not-found` (404)
- `invalid-edge` (422)
- `invalid-source` (422)
- `invalid-book` (422)
- `missing-user-header` (400)
- `data-integrity-violation` (422)
- `validation` (400) - дополнительно поле `errors`
- `illegal-argument` (400)
- `node-is-root` (409) - попытка `DELETE /api/v1/nodes/{id}` где
  `id` совпадает с `topics.root_node_id`. Дополнительные properties
  `nodeId` и `topicId`. Чтобы удалить корневой узел - удалите тему
  целиком через `DELETE /api/v1/topics/{topicId}`

Для `validation`:
```json
{
  "type": "https://argumentmap.example/errors/validation",
  "title": "Ошибка валидации",
  "status": 400,
  "detail": "Запрос содержит невалидные поля",
  "instance": "/api/v1/nodes",
  "errors": [
    { "field": "content", "message": "не должно быть пустым" },
    { "field": "topicId", "message": "не может быть null" }
  ]
}
```

## Library - книги и цитирование (ADR-019, Этап 14)

Префикс - `/api/v1/library`.

### POST /api/v1/library/books - создать книгу

Заголовки: `X-User-Id` (required, UUID).

Request body:
```json
{
  "bookType": "BOOK",
  "title": "Маджму' аль-Фатава",
  "authorityId": "uuid-of-ibn-taymiyya-or-null",
  "language": "ar",
  "description": "37-томный сборник",
  "metadata": { "shamela_id": 12345, "volumes": 37 }
}
```
Поля:
- `bookType` (required): `QURAN` / `HADITH_COLLECTION` / `BOOK` /
  `ARTICLE` / `MANUSCRIPT`
- `title` (required, non-blank, ≤500): заголовок труда
- `authorityId` (optional): автор/составитель из справочника
  `authorities`. NULL для Корана. 404 `authority-not-found` если
  передали несуществующий
- `language` (required, non-blank, ≤32): свободная строка (`ar`,
  `ru`, `ar+ru`, BCP-47)
- `description` (optional, ≤5000): короткое описание
- `metadata` (optional): произвольный JSON для тип-специфики

Response 201, body - полный `BookResponse` (ADR-043 Amendment добавил `visibility`):
```json
{
  "id": "...",
  "bookType": "BOOK",
  "title": "...",
  "authorityId": "...",
  "language": "ar",
  "description": "...",
  "metadata": { ... },
  "createdBy": "uuid-of-user",
  "createdAt": "ISO-8601",
  "updatedAt": "ISO-8601",
  "visibility": "PUBLIC"
}
```
Header `Location: /api/v1/library/books/{id}`.

**visibility** (ADR-043 Amendment, Этап 22.c):
- REST POST устанавливает `PUBLIC` по умолчанию (open library)
- File-import flow (`POST /api/v1/library/imports/file`) -
  устанавливает `PRIVATE` (user-upload черновики приватны)
- Сменить можно через `PATCH /api/v1/library/books/{id}/visibility`
  (только owner / ADMIN)

Ошибки:
- 400 `validation` - blank title, missing bookType, bad metadata JSON
- 400 `missing-user-header` - нет `X-User-Id`
- 404 `authority-not-found` - `authorityId` указан, но запись отсутствует

### GET /api/v1/library/books - пагинированный список

**Заголовки:** `X-User-Id: <uuid>` (обязательно с 22.c для visibility-фильтра)

Query:
- `q` (optional, ILIKE по title)
- `type` (optional, `BookType` enum)
- `authorityId` (optional, UUID) - фильтр по автору
- `publisherId` (optional, UUID) - фильтр по издателю (academic справочник)
- `page` (optional, default 0), `size` (optional, default 20, max 100)

Сортировка: `created_at DESC` (новые сверху).

**Visibility-фильтр** (ADR-043 Amendment): user видит PUBLIC + свои
PRIVATE + SHARED где он member. ADMIN видит все.

Response 200 - `PagedResponse<BookSummary>` (без description, metadata,
updatedAt - они в детальном GET; включает `createdBy` и `visibility`):
```json
{
  "items": [
    {
      "id": "...",
      "bookType": "QURAN",
      "title": "Священный Коран",
      "authorityId": null,
      "language": "ar",
      "createdBy": "...",
      "createdAt": "...",
      "visibility": "PUBLIC"
    }
  ],
  "page": 0, "size": 20, "totalElements": 42, "totalPages": 3,
  "hasNext": true, "hasPrev": false
}
```

`createdBy` добавлен 2026-05-19 (backlog tech debt round 4 #8) для
точного фильтра «Мои» на фронте - был approximation
`visibility === 'PRIVATE'`, теперь strict `createdBy === currentUser.id`.

### GET /api/v1/library/books/{id} - книга с деревом chapters

Response 200 - `BookDetailResponse` (поля как в `BookResponse` +
поле `chapters`, рекурсивное дерево):
```json
{
  "id": "...",
  "bookType": "BOOK",
  "title": "Книга",
  "authorityId": null,
  "language": "ar",
  "description": null,
  "metadata": null,
  "createdBy": "...",
  "createdAt": "...",
  "updatedAt": "...",
  "chapters": [
    {
      "id": "...",
      "title": "Том 1",
      "orderIndex": 0,
      "parentChapterId": null,
      "children": [
        {
          "id": "...",
          "title": "Глава 1.1",
          "orderIndex": 0,
          "parentChapterId": "...",
          "children": []
        }
      ]
    }
  ]
}
```

> **Примечание (springdoc-openapi gap):** поле `children` в `ChapterResponse`
> приходит в runtime JSON (LibraryDtoMappers строит nested tree), но
> отсутствует в `/v3/api-docs` schema из-за известного ограничения
> springdoc-openapi 2.x на self-referential properties. Frontend
> восстанавливает type через intersection:
> `type Chapter = components['schemas']['ChapterResponse'] & { children?: Chapter[] }`
> (см. `gotchas.md`). Тип Chapter определён в
> `frontend/src/apps/library/components/ChapterList.tsx`.

Ошибки: 404 `book-not-found`.

### PATCH /api/v1/library/books/{id} - правка academic metadata (20.d)

Partial update 6 academic полей: мухаккик, издатель, место издания,
номер издания, год хиджры, год григориан. Title/authority/description
не меняются через этот endpoint.

PATCH-семантика:

- **String** поля (`muhaqqiqName`, `publisherName`, `publicationPlaceName`):
  `null` или отсутствие в JSON = no change (keep existing FK), пустая
  строка `""` = clear FK to null, non-empty trimmed = `findOrCreate(name)`
  в соответствующем справочнике + replace FK
- **Integer** поля (`editionNumber`, `publishedYearHijri`,
  `publishedYearGregorian`): `null` или отсутствие = no change,
  значение = replace. Очистить Integer через JSON нельзя (acceptable
  edge case)

Тело запроса (`UpdateBookRequest`, все поля optional):
```json
{
  "muhaqqiqName": "حكمت بن بشير بن ياسين",
  "publisherName": "دار ابن الجوزي للنشر والتوزيع",
  "publicationPlaceName": "السعودية",
  "editionNumber": 1,
  "publishedYearHijri": 1431,
  "publishedYearGregorian": 1999
}
```

Ответ `200 OK`: full `BookDetailResponse` с обновлёнными nested
`muhaqqiq`/`publisher`/`publicationPlace` refs.

**Заголовки:** `X-User-Id: <uuid>` (обязательно с 22.c для permission check)

Ошибки:
- `404 book-not-found` - книга не существует
- `403 forbidden-book-write` - не owner / EDITOR / ADMIN (ADR-043 Amendment)

### PATCH /api/v1/library/books/{id}/visibility - сменить visibility (22.c)

Меняет уровень доступа книги (ADR-043 Amendment). **Только owner или
ADMIN** - EDITOR этого не может (privilege-escalation).

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

Тело запроса (`UpdateBookVisibilityRequest`):
```json
{ "visibility": "SHARED" }
```
Allowed values: `PRIVATE` / `SHARED` / `PUBLIC`.

Ответ `200 OK`: full `BookResponse` с обновлённым `visibility`.

Ошибки:
- `400 validation` - invalid value
- `403 forbidden-book-write` - не owner и не ADMIN
- `404 book-not-found`

### POST /api/v1/library/books/{bookId}/members - добавить члена (22.c)

Добавить user как `MEMBER` или `EDITOR` SHARED-книги (ADR-043 Amendment).
**Только owner или ADMIN** (privilege-escalation guard).

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

Тело запроса (`AddBookMemberRequest`):
```json
{
  "userId": "uuid-of-collaborator",
  "role": "MEMBER"
}
```
- `userId` (required UUID): user который добавляется
- `role` (required): `MEMBER` (read-only) | `EDITOR` (read + write
  PATCH metadata)

Ответ `201 Created`, `BookMemberResponse`:
```json
{
  "id": "...",
  "bookId": "...",
  "userId": "...",
  "role": "MEMBER",
  "addedAt": "ISO-8601",
  "addedBy": "uuid-of-owner"
}
```
Header `Location: /api/v1/library/books/{bookId}/members/{memberId}`.

Ошибки:
- `400 validation` - invalid role / missing userId
- `400 illegal-argument` - owner добавляет сам себя; user уже member
  (UNIQUE constraint)
- `403 forbidden-book-write` - не owner и не ADMIN
- `404 book-not-found`

### GET /api/v1/library/books/{bookId}/members - список членов (22.c)

Все члены книги. Видеть могут все кто имеет read-доступ к книге.

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

Ответ `200 OK`: `BookMemberResponse[]` (sorted by `addedAt`).

Ошибки:
- `403 forbidden-book-access` - нет read-доступа к книге
- `404 book-not-found`

### PATCH /api/v1/library/books/{bookId}/members/{memberId} - сменить роль (22.c)

**Только owner или ADMIN**.

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

Тело запроса (`UpdateBookMemberRequest`):
```json
{ "role": "EDITOR" }
```

Ответ `200 OK`: обновлённый `BookMemberResponse`.

Ошибки:
- `400 validation` - invalid role
- `403 forbidden-book-write` - не owner и не ADMIN
- `404 book-not-found` или `book-member-not-found`

### DELETE /api/v1/library/books/{bookId}/members/{memberId} - удалить члена (22.c)

**Owner или ADMIN** могут удалить любого. **MEMBER** может удалить
только себя (self-leave).

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

Ответ `204 No Content`.

Ошибки:
- `403 forbidden-book-write` - не self-leave и не owner/ADMIN
- `404 book-not-found` или `book-member-not-found`

### GET /api/v1/library/muhaqqiqs?q={query}&limit={n} - autocomplete (20.d)

Search мухаккиков по подстроке имени (case-insensitive ILIKE по `name`
и `full_name`). Используется фронтом для autocomplete в BookEditModal,
чтобы избежать typo-дублей.

Параметры:
- `q` (опционально) - подстрока для поиска. Если не указан - возвращает
  первые `limit` записей в алфавитном порядке
- `limit` (опционально, default 20, max 100) - макс. количество

Ответ `200 OK`: массив `MuhaqqiqResponse {id, name, fullName}`.

### GET /api/v1/library/publishers?q={query}&limit={n} - autocomplete (20.d)

Аналогично muhaqqiqs, но по `lib_publishers`. Поиск только по `name`
(нет `full_name`). DTO: `PublisherResponse {id, name}`.

### GET /api/v1/library/publication-places?q={query}&limit={n} - autocomplete (20.d)

Аналогично publishers, по `lib_publication_places`. DTO:
`PublicationPlaceResponse {id, name}`.

### DELETE /api/v1/library/books/{id} - удалить книгу

Каскад через FK на `lib_chapters`/`lib_pages`/`lib_image_regions` +
`lib_book_members`. **Только owner или ADMIN** (ADR-043 Amendment) -
EDITOR этого не может.

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

Response 204 (success), 404 `book-not-found`, 403 `forbidden-book-write`
если не owner и не ADMIN.

### GET /api/v1/library/books/{bookId}/pages?from={N}&to={M} - страницы

Query: `from` (optional, default 1), `to` (optional, default
`from + 49`). Сортировка по `pageNumber`.

Response 200 - массив `PageSummary` (без `textContent` и `imageUrl`,
они тяжёлые - запрашиваются по одной странице через `GET /pages/{id}`):
```json
[
  {
    "id": "...",
    "pageNumber": 1,
    "printedPage": "47",
    "part": "1",
    "chapterId": "...",
    "hasText": true,
    "hasImage": false
  }
]
```

`pageNumber` - internal navigation counter (1..N), используется для
URL-state и навигации prev/next. `printedPage` (nullable TEXT) - маркер
страницы в оригинальном бумажном издании (может быть число, арабская
буква, римское число) - **что показываем пользователю**. `part`
(nullable TEXT) - том/juz' для многотомных книг (`"1"`, `"المقدمة"`).
Оба поля добавлены в миграции 19 для source-first нумерации
(ADR-021); legacy-страницы до миграции имеют NULL.

Ошибки: 404 `book-not-found`.

### GET /api/v1/library/pages/{id} - конкретная страница

Не вложенный путь, потому что page id уникален в системе
(симметрично `/api/v1/nodes/{id}` без topic в пути).

Response 200 - `PageResponse`:
```json
{
  "id": "...",
  "bookId": "...",
  "chapterId": "...",
  "pageNumber": 12,
  "printedPage": "47",
  "part": "1",
  "pdfPageNumber": null,
  "textContent": "...",
  "imageUrl": "https://...",
  "formattedContent": {
    "type": "doc",
    "content": [...]
  },
  "imageRegions": [
    {
      "id": "...",
      "x": 0.1,
      "y": 0.1,
      "width": 0.5,
      "height": 0.5,
      "extractedText": "..."
    }
  ],
  "createdAt": "...",
  "updatedAt": "..."
}
```

`pdfPageNumber` (nullable INTEGER) - физическая страница в PDF
оригинале (для cross-referencing когда подключим PDF integration).
Сейчас всегда `null` - ETL pipeline не скачивает PDF на MVP.
Заполняется в будущем (Этап PDF integration).

`formattedContent` (миграция 33, ADR-039) - ProseMirror JSON, Tiptap
output. NULL для legacy Shamela/PDFBox-импортированных страниц - фронт
оборачивает `textContent` в minimal paragraph-document через
`wrapPlainTextAsDoc` (RichTextRenderer fallback).

Координаты `imageRegions` нормализованы (0..1), не пиксельные.

Ошибки: 404 `page-not-found`.

### PATCH /api/v1/library/pages/{id}/formatted-content - сохранить ProseMirror JSON (Этап 17.0)

ADR-039 (Tiptap rich text editor). Сохраняет `formatted_content` jsonb
поле для admin editor flow. Backend не валидирует ProseMirror schema -
принимает любой синтаксически валидный JSON (schema валидация на
фронте через Tiptap extensions).

Body - `UpdateFormattedContentRequest`:
```json
{
  "formattedContent": {
    "type": "doc",
    "content": [
      {
        "type": "hadithBox",
        "attrs": {"source": "Бухари 1", "grade": "sahih"},
        "content": [
          {"type": "paragraph", "content": [{"type": "text", "text": "..."}]}
        ]
      }
    ]
  }
}
```

Response 200 - `PageResponse` (см. выше) с обновлённым `formattedContent`.
`text_content` не трогается - сохраняется для FTS (будущий ES) и для
fallback render. `updated_at` bump.

Требует X-User-Id header (либо Bearer JWT в prod) - mutating endpoint
под Spring Security `.anyRequest().authenticated()`.

Ошибки:
- 404 `page-not-found` - id не найден
- 400 - syntactically invalid JSON в body

### POST /api/v1/library/books/{bookId}/pages - upload page image (Этап 17.a)

ADR-041 (OCR pipeline). Третий способ внести страницу в библиотеку
после shamela ETL и file import PDF. Image-сканы для рукописей и
редких книг где text layer отсутствует. Один файл - одна страница.

Multipart/form-data:
- `file` (required) - image binary. Content-type whitelist:
  `image/jpeg`, `image/png`, `image/webp`, `image/tiff`. Размер до
  20MB (Spring multipart limit)
- `pageNumber` (required, query param) - internal номер страницы
  (1-based). Если page с таким номером уже существует - upload
  обновляет pointer на новый scan (S3 versioning сохранит историю
  previous). Иначе создаётся placeholder Page с `text_content=""`
  (CHECK constraint satisfied)

Header: `X-User-Id` (или Bearer JWT в prod) - mutating endpoint.

`200 OK` - `PageResponse` (расширен 6 image/OCR полями - см. ниже):
```json
{
  "id": "uuid",
  "bookId": "uuid",
  "pageNumber": 1,
  "imageBucket": "library-page-images",
  "imageStorageKey": "{bookId}/page-1.jpg",
  "imageUploadedAt": "2026-05-17T22:00:00Z",
  "ocrStatus": "PENDING",
  ...
}
```

После upload OCR **не** запускается автоматически - триггер отдельный
endpoint (см. ниже). Это позволяет batch-uploader залить все страницы
рукописи + затем триггерить OCR пачкой.

Ошибки:
- 404 `book-not-found` - bookId не существует
- 413 `payload-too-large` - >20MB
- 415 `unsupported-media-type` - MIME вне whitelist
- 422 `page-image-error` - pageNumber<=0, пустой файл, чтение из stream

### POST /api/v1/library/pages/{pageId}/ocr - триггер async OCR (Этап 17.b)

ADR-041. Запускает Tesseract OCR через `OcrService.recognizeAsync` -
работа уходит в bounded `ocrTaskExecutor` (core=2, max=4). Frontend
получает 202 Accepted сразу, опрашивает GET endpoint для статуса.

Тело: пустое.

`202 Accepted` - `OcrJobResponse`:
```json
{
  "pageId": "uuid",
  "status": "PENDING",
  "startedAt": null,
  "completedAt": null,
  "hasImage": true
}
```

`status` на момент response - текущее значение `lib_pages.ocr_status`.
Реальное PROCESSING может быть выставлено async через миллисекунду.
Polling endpoint показывает актуальное значение. Re-trigger допустим
(полезно для зависших PROCESSING при рестарте backend'а).

Ошибки:
- 404 `page-not-found` - pageId не существует

### GET /api/v1/library/pages/{pageId}/ocr - статус OCR (Этап 17.b)

ADR-041. Polling endpoint для статуса. Frontend опрашивает каждые
2-3 сек пока `status=PROCESSING`, переключается на DONE/FAILED →
стопает polling.

`200 OK` - `OcrJobResponse` (см. выше). `hasImage=false` означает что
у страницы нет scan upload - precondition OCR не выполнен. Frontend
может скрыть кнопку «Запустить OCR».

Ошибки:
- 404 `page-not-found`

### POST /api/v1/library/pages/{pageId}/regions - создать ImageRegion (Этап 17.c)

ADR-041. Выделенный прямоугольник на странице-скане - связь между
physical area и semantic content (хадис-бокс, marginalia, footnote).
Используется ImagePageRenderer (18.e) для overlay визуализации.

Body - `CreateImageRegionRequest`:
```json
{
  "x": 0.1,
  "y": 0.2,
  "width": 0.3,
  "height": 0.4,
  "extractedText": "хадис текст или null"
}
```

Координаты нормализованные (0..1) - не пиксельные. Bean Validation
проверяет каждое поле (0..1, width/height > 0). DB CHECK constraint
дополнительно гарантирует `x+width <= 1 AND y+height <= 1`.

`201 Created` + Location `/api/v1/library/pages/regions/{id}` +
`ImageRegionResponse{id, x, y, width, height, extractedText}`.

Ошибки:
- 404 `page-not-found`
- 400 - Bean Validation (out of range single field)
- 422 `data-integrity-violation` - sum out of bounds

### GET /api/v1/library/pages/{pageId}/regions - список регионов (Этап 17.c)

`200 OK` - `List<ImageRegionResponse>` отсортированные по `created_at`.

Ошибки:
- 404 `page-not-found`

### DELETE /api/v1/library/pages/regions/{regionId} - удалить регион (Этап 17.c)

`204 No Content`. Update/PATCH намеренно не реализован - regions
immutable. Изменить координаты = удалить + создать новый.

Ошибки:
- 404 `image-region-not-found`

### POST /api/v1/library/pages/{pageId}/ai-edit - триггер AI edit (Этап 17.e)

ADR-042. Запускает Anthropic Claude через `AiEditService.enhanceAsync` -
работа уходит в bounded `aiEditTaskExecutor` (core=2, max=4, queue=50).
Frontend получает 202 Accepted сразу, опрашивает GET endpoint для
статуса. Преобразует OCR raw text (`text_content`) в ProseMirror
JSON (`formatted_content`).

Тело: пустое.

`202 Accepted` - `AiEditJobResponse`:
```json
{
  "pageId": "uuid",
  "status": "PENDING",
  "startedAt": null,
  "completedAt": null,
  "hasTextContent": true
}
```

`status` на момент response - текущее значение `lib_pages.ai_edit_status`.
Реальное PROCESSING/DONE выставляются background async. Polling
endpoint показывает актуальное значение.

Re-trigger допустим - после DONE если результат не понравился, либо
после FAILED для retry. `AiEditService.enhance` idempotent на state
machine.

Ошибки:
- 404 `page-not-found` - pageId не существует
- 503 `ai-edit-not-configured` - `ANTHROPIC_API_KEY=disabled`.
  Detail: «AI editing не настроен - установите ANTHROPIC_API_KEY
  env var»
- 502 `anthropic-api-error` - Anthropic API вернул не-2xx (после 3
  retry попыток через Resilience4j). Property `upstreamStatus` -
  HTTP-код от Anthropic
- 503 `anthropic-api-error` - IOException/timeout при connection к
  Anthropic (statusCode=0 в Exception)

### GET /api/v1/library/pages/{pageId}/ai-edit - статус AI edit (Этап 17.e)

ADR-042. Polling endpoint. Frontend опрашивает каждые 2-3 сек пока
`status=PROCESSING`, переключается на DONE/FAILED → стопает polling.

`200 OK` - `AiEditJobResponse` (см. выше). `hasTextContent=false`
означает что у страницы нет text_content - precondition AI edit не
выполнен (например image-page без OCR пока). Frontend может скрыть
кнопку.

При `status="DONE"` - `formatted_content` страницы заполнен валидным
ProseMirror JSON. Чтение через `GET /pages/{id}` либо
`/library/books/{bookId}/pages?from=X&to=Y` (см. выше).

Ошибки:
- 404 `page-not-found`

### Что **не** реализовано в Этапе 17

- re-OCR endpoint (17.d) - перезапустить OCR на DONE/FAILED странице
  через тот же `POST /pages/{id}/ocr` (idempotent на уровне state
  machine), отдельный resource path не нужен
- ~~AI editing pass (17.e)~~ - **реализовано в Сессии 43**: ADR-042
  Anthropic Claude single-provider + миграция 35 + POST/GET
  `/pages/{id}/ai-edit` (см. выше)
- Cron retry hung PROCESSING (>10 минут) - manual через polling +
  re-trigger пока что
- Frontend UI кнопка для AI edit - отдельный подэтап в будущем (sub
  17.e.f). Сейчас только REST endpoint + curl example в
  `backend/CLAUDE.md`

### Что **не** реализовано в Этапе 14

- POST для chapters/pages/imageRegions - страницы и главы создаются
  только в составе книги через будущие import endpoints (Этапы 15-17)
- PATCH/PUT для books/chapters/pages - вернёмся когда понадобится
- ~~multipart upload для image-сканов~~ - **реализовано в 17.a** (см. выше)

## Shamela Admin API (ADR-020, Этапы 15.6 + 15.7)

ETL-импорт каталога shamela.ws через desktop-API. Двухслойная схема
(см. ADR-020): сырые данные едут в `lib_shamela_*` (staging), потом
маппятся в доменную `lib_books`/`Authority`/`lib_chapters`/`lib_pages`.

⚠️ MVP - без role-check авторизации. `X-User-Id` берётся в `map-book`
для `created_by`, но admin-роли не проверяются. Spring Security в
Этапе 20.

### POST /api/v1/admin/shamela/sync-master

Синхронизация каталога: master-zip с 3 SQLite (category/author/book) →
bulk-upsert в `lib_shamela_*`. Идемпотентен через master_version.

Тело: пустое.

`200 OK`:
```json
{
  "changed": true,
  "previousVersion": 0,
  "currentVersion": 1261,
  "categoriesCount": 50,
  "authorsCount": 25000,
  "booksCount": 8500
}
```

При `changed=false` все counts равны 0.

Ошибки:
- `502 shamela-api-error` - shamela API недоступна
- `500 shamela-archive-error` - битый zip / Zip Slip
- `500 shamela-reader-error` - битый SQLite
- `500 shamela-import-error` - прочие

### POST /api/v1/admin/shamela/import-book/{bookId}

Загрузка контента конкретной книги (page+title) в staging.
`{bookId}` - integer id из shamela. Книга должна существовать в
`lib_shamela_book` (нужен предварительный sync-master).

Тело: пустое.

`200 OK`:
```json
{
  "bookId": 41557,
  "majorRelease": 4,
  "pagesCount": 320,
  "titlesCount": 18
}
```

Ошибки:
- `400 illegal-argument` - bookId < 1
- `404 shamela-not-found` - книга не в `lib_shamela_book`
- `502 shamela-api-error` / `500 shamela-archive-error`/`shamela-reader-error`

### POST /api/v1/admin/shamela/map-book/{bookId}

Маппинг книги из staging в доменную модель. После успеха книга
появляется в `GET /api/v1/library/books/{uuid}`.

Заголовки: `X-User-Id: <uuid>` (обязательный, для created_by).

Тело: пустое.

`200 OK` (новая книга):
```json
{
  "bookId": "550e8400-e29b-41d4-a716-446655440000",
  "shamelaBookId": 41557,
  "created": true,
  "authorityId": "660e8400-...",
  "chaptersCount": 18,
  "pagesCount": 320
}
```

`200 OK` (re-import idempotent skip): то же, но `created=false` и
counts=0.

Ошибки:
- `400 illegal-argument` - bookId < 1
- `400 missing-user-header` - нет/невалидный X-User-Id
- `404 shamela-not-found` - книга не в `lib_shamela_book`
- `500 shamela-import-error`

### POST /api/v1/admin/shamela/backfill-bibliography

Прогнать `ShamelaBibliographyParser` (Этап 20.c) по всем книгам с
`metadata->>'shamela_book_id' IS NOT NULL`. Для каждой выловленной
academic-metadata выполняется `findOrCreate` в `lib_muhaqqiqs` /
`lib_publishers` / `lib_publication_places` + UPDATE на `lib_books`.

Non-destructive merge: если parser не вернул значение для поля,
существующее значение FK сохраняется. Если parser вернул - перезаписывает
(включая admin-edited - см. Этап 20.d follow-up).

Используется один раз после деплоя 20.c parser, чтобы добить existing
книги импортированные до его появления. Новые `map-book` calls автоматически
заполняют metadata через mapper.

Тело: пустое. Заголовки: не требуются.

`200 OK`:
```json
{
  "scanned": 3,
  "updated": 3,
  "skipped": 0
}
```

- `scanned` - всего shamela-sourced книг проверено
- `updated` - книги где parser нашёл хотя бы одно поле и был UPDATE
- `skipped` - blank description / нет markers / backfill упал на конкретной книге

### GET /api/v1/admin/shamela/search?q={query}&limit={n}

Поиск книг в `lib_shamela_book` для admin-страницы фронта. Substring
match на `name` через ILIKE с обогащением: имя автора через JOIN на
`lib_shamela_author` + флаг `isMapped` через EXISTS-проверку в
`lib_books` (через GIN-индекс на `metadata`).

Параметры:
- `q` (обязательный) - substring для поиска
- `limit` (опциональный, default 20, max 100)

`200 OK`:
```json
[
  {
    "bookId": 41557,
    "name": "صحيح البخاري",
    "authorName": "Аль-Бухари",
    "majorRelease": 4,
    "isMapped": true
  }
]
```

Сортировка: точные substring-совпадения сначала (через ILIKE на
исходный q), потом по `LENGTH(name)`, потом по id. Tombstoned записи
(`deleted_at IS NOT NULL`) исключаются.

Ошибки:
- `400 illegal-argument` - q пустой/отсутствует

### GET /api/v1/admin/shamela/sync-status

Состояние ETL для admin dashboard. Без параметров.

`200 OK`:
```json
{
  "masterVersion": 1261,
  "lastSyncedAt": "2026-05-09T14:43:00+00:00",
  "categoriesCount": 50,
  "authorsCount": 25000,
  "booksCount": 8500,
  "mappedBooksCount": 3
}
```

`lastSyncedAt = null` для свежей БД. `booksCount` - всего книг в
staging (доступно для импорта), `mappedBooksCount` - сколько уже в
`lib_books` (доступно в `/books`).

## PDF Viewer API (ADR-021, Этап 25.a)

Source-agnostic streaming PDF для книг с привязанным источником.
На MVP поддерживается один `PdfSourceProvider` - `PdfLinksSourceProvider`,
который читает `metadata.pdf_links` (формат shamela через archive.org
CDN: `{root, files: ["filename"], cover, size}`). Будущие провайдеры
(MinIO upload, прямой archive.org, IIIF) подключаются через тот же
interface без изменения API.

### GET /api/v1/library/books/{bookId}/pdf/info

Метаданные PDF: список файлов (multi-volume), label каждого тома,
размер. Не качает PDF.

Response 200 - `PdfInfoResponse`:
```json
{
  "hasCover": true,
  "totalSizeBytes": 135102734,
  "files": [
    {"index": 0, "label": "00_113015", "isCover": true, "sizeBytes": null, "pageCount": null},
    {"index": 1, "label": "المقدمة", "isCover": false, "sizeBytes": null, "pageCount": null},
    {"index": 2, "label": "01_113015", "isCover": false, "sizeBytes": null, "pageCount": null}
  ]
}
```

`isCover` помечает обложку - по convention shamela/archive.org она
лежит в `files[0]` когда metadata содержит `"cover": 1`. Фронт по
умолчанию выбирает первый файл с `isCover=false` чтобы юзер не видел
3-страничную обложку вместо тысячи страниц контента.

`filename` НЕ возвращается клиенту - чтобы фронт не мог собрать
прямую ссылку на CDN в обход бэка. Это даст возможность будущему
audit/rate-limit/кешу работать с reality того что все запросы идут
через нас.

Ошибки: 404 `book-not-found`, 404 `pdf-not-available` (книга без
PDF-источника).

### GET /api/v1/library/books/{bookId}/pdf?fileIndex={N}

Streaming PDF с поддержкой Range header (RFC 7233). PDF.js на фронте
запрашивает chunks по 64KB-1MB, не качает весь файл сразу (~50MB
типичный shamela).

Query: `fileIndex` (optional, default 0) - индекс файла из info.files.

Headers:
- `Range: bytes=START-END` (optional) - частичная загрузка
- `Range: bytes=START-` (optional) - open-ended до конца файла

Response 200 (full) или 206 Partial Content (range):
- `Accept-Ranges: bytes`
- `Content-Type: application/pdf`
- `Content-Length` - размер выдаваемого chunk'а
- `Content-Range: bytes START-END/TOTAL` (только при 206)

Сервер ограничивает chunk до 1MB (`DEFAULT_CHUNK_SIZE`). Если клиент
запросит `bytes=0-10000000` (10MB) - вернёт 1MB и `Content-Range`
покажет реально отданный диапазон. PDF.js делает следующий запрос на
оставшееся.

**Lazy streaming (25.d.5, ADR-023 amendment):** для books из shamela /
archive.org backend с поддержкой Range проксирует chunks напрямую -
не качает полный PDF в backend на первом запросе. Cache hit
(уже скачанная книга) идёт мгновенно из MinIO Range, cache miss с
Range заголовком форвардится upstream Range request к archive.org с
streaming bytes через backend без буферизации полного файла. Cache
miss без Range (admin smoke / full download) синхронно скачивает +
кеширует.

Ошибки:
- 404 `book-not-found`, `pdf-not-available` (книга без PDF-источника
  или fileIndex out of range)
- 416 `range-not-satisfiable` - Range start >= размера файла
  (properties: `start`, `totalSize`)
- 503 - circuit breaker `pdfDownload` открыт (archive.org недоступен,
  fail fast вместо 5-минутного blocking)

### Что **не** реализовано в PDF Viewer

- **MinIO tee при cache miss + Range** - при первом Range request к
  не-кешированной книге сейчас идёт upstream forward без записи в
  MinIO (см. ADR-023 Amendment). Tee для постепенного cache fill -
  во второй итерации если будет реальный production traffic
- **PDF page count для shamela books** - sizeBytes и pageCount в
  `info.files` для PdfLinks книг всегда null. User-upload PDF имеет
  pageCount (PDFBox extraction в FileImportService)
- **Region selection** (25.f) - выделение прямоугольников на скане
  для region-based citation. После CitationPicker

### Что **не** реализовано в shamela admin

- `GET /admin/shamela/book/{id}/pdf/{fileIndex}` - lazy PDF download
  через `StreamingResponseBody` + tempfile cleanup. Согласовано с
  ADR-020 «PDF lazy»

## File import API (ADR-035, Этап 16)

Второй способ добавления книг в library - пользователь загружает PDF
через multipart upload, бэк извлекает текст постранично через PDFBox,
создаёт `Book` + `Page[]` + сохраняет оригинал в `library-user-uploads`
bucket. Альтернативa shamela ETL для случаев когда нужной книги в
shamela нет.

### POST /api/v1/library/imports/file - загрузка PDF

Content-Type: `multipart/form-data`. Поля:

| Поле | Тип | Required | Описание |
|---|---|---|---|
| `file` | binary | yes | PDF файл (`application/pdf`), до 50MB |
| `title` | string | no | Override title (иначе берётся из PDF metadata, fallback на filename без расширения) |
| `authorityId` | UUID | no | FK на существующего автора |
| `language` | string | no | ISO 639-1 из whitelist `ar\|ru\|en` (mirror frontend FileUploadModal), default `"ar"` если blank. 16.h - unsupported значение → 422 `file-import-error` |
| `description` | string | no | Свободный текст |
| `muhaqqiqName` | string | no | Имя мухаккика (محقق). 16.g - `findOrCreate` в `lib_muhaqqiqs` |
| `publisherName` | string | no | Имя издателя. 16.g - `findOrCreate` в `lib_publishers` |
| `publicationPlaceName` | string | no | Город/страна издания. 16.g - `findOrCreate` в `lib_publication_places` |
| `editionNumber` | integer | no | Номер издания (1..99). 16.g |
| `publishedYearHijri` | integer | no | Год издания по хиджре (1..9999). 16.g |
| `publishedYearGregorian` | integer | no | Год издания по григориану (1..9999). 16.g |
| `X-User-Id` (header) | UUID | yes | Загрузчик книги |

**Academic-поля (16.g):** если хотя бы одно заполнено, бэк вызывает
13-args `BookService.createBook` с `findOrCreate` по справочникам.
Иначе - 7-args без academic FK (legacy путь). Mirror диапазонов из
`CreateBookRequest`/`UpdateBookRequest`. Out-of-range → 422
`file-import-error` (ручная валидация в controller, т.к. Bean
Validation для `@RequestParam` в проекте не настроена).

Response 201 - `FileImportResponse`:
```json
{
  "bookId": "60ae04bc-cc91-489c-b416-2ac9f886fa1b",
  "fileId": "ab2e4b5c-a76b-7cb9-d585-24e40ea8ec2d",
  "pageCount": 247,
  "contentHash": "c6c0b50cc5133e627b3056cd6d52e61a3d8fd55a5790a45f49d7e37dd4da027e",
  "sizeBytes": 4587023,
  "bucket": "library-user-uploads",
  "storageKey": "60ae04bc-cc91-489c-b416-2ac9f886fa1b/uploaded.pdf"
}
```

Headers: `Location: /api/v1/library/books/{bookId}`

Side-effects:
- Новая строка в `lib_books` с `book_type=BOOK`, `metadata.user_uploaded=true`,
  `metadata.original_filename`, `metadata.pdf_page_count`
- N строк в `lib_pages` где N = phys-страниц PDF.
  `page_number = pdf_page_number = i+1` (1-based), `chapter_id=null`
  (PDF без chapter outline), `text_content` = PDFTextStripper output
  (может быть пустой строкой для scanned-images PDF)
- PDF blob в `library-user-uploads` bucket по ключу `{bookId}/{filename}`
  (filename sanitized: path stripped, пробелы -> `_`)
- Строка в `library_files` с `source_type=USER_UPLOAD`, `source_url=null`,
  `content_hash` SHA-256

**После upload книга сразу доступна на чтение** (Этап 16.h, post-review
fix Сессия 38) через существующие PDF endpoints:
- `GET /api/v1/library/books/{bookId}/pdf/info` - вернёт single-file
  metadata (`label` = filename без расширения, `pageCount` из
  `metadata.pdf_page_count`)
- `GET /api/v1/library/books/{bookId}/pdf?fileIndex=0` - streaming
  PDF из `library-user-uploads` bucket (Range-aware)

Резолвинг через `UserUploadProvider` (`@Order(50)`, выше приоритет
чем `PdfLinksSourceProvider` для shamela-книг).

Ошибки:
- 400 `missing-user-header` - нет `X-User-Id`
- 413 `payload-too-large` - превышен лимит 50MB (Spring multipart
  enforce'ит до парсинга controller'а)
- 415 `unsupported-media-type` - content type не `application/pdf`
- 422 `file-import-error` - corrupted PDF, encrypted PDF, 0-страничный
  PDF, пустой файл, language вне whitelist `ar|ru|en` (16.h)

### Что **не** реализовано в Этапе 16

- **EPUB import** - см. ADR-035 «EPUB отложен». 95% контента -
  PDF, EPUB добавим когда появится UX-кейс. MIME whitelist сейчас
  только `application/pdf`
- **Password-protected PDF decrypt** - encrypted PDF возвращают 422.
  Decrypt с password добавим если будет запрос
- **OCR для scanned-images PDF** - страницы без extractable text
  получают пустую `text_content`. OCR pipeline планируется в
  Этапе 17 (image-сканы)
- **Auto-chapter detection** - PDF outline (bookmarks) не парсится.
  Все pages создаются с `chapter_id=null`. Будущая фича когда
  понадобится navigation tree из PDF
- Async POST endpoints через `@Async`/queue - на MVP синхронные
- Bulk endpoints (`POST /map-books?ids=...`) - до решения bulk vs
  lazy после фронт-валидации

## Citation API (ADR-026 + ADR-027, Этап 18.f)

Дополняет существующий `POST /api/v1/nodes/{nodeId}/sources` для positional
citation flow с привязкой к book/page/PDF. Используется CitationPicker на
фронте.

### POST /api/v1/nodes/{nodeId}/citations

Создаёт citation с positional pointer в одном из трёх режимов
(TEXT/PDF/REGION). Ensure-or-create Source для (sourceType=BOOK, bookId),
insert в node_sources с positional полями.

**Request (TEXT mode):**
```json
{
  "bookId": "uuid",
  "pageId": "uuid",
  "rangeStart": 0,
  "rangeEnd": 87,
  "quote": "وأرى أن لا تكون البدعة...",
  "context": "Ибн Касир признаёт..."
}
```

**Request (PDF mode):**
```json
{
  "bookId": "uuid",
  "pdfFileId": "uuid",
  "pdfPageNumber": 47,
  "pdfBbox": {"x": 0.12, "y": 0.23, "width": 0.65, "height": 0.05},
  "quote": "...",
  "context": "..."
}
```

`pdfBbox` нормализован 0-1 относительно page viewport (zoom-invariant).

**Request (REGION mode, future):**
```json
{
  "bookId": "uuid",
  "imageRegionId": "uuid",
  "context": "..."
}
```

**Ответ (201 Created):** расширенный `NodeSourceResponse` (см. ниже).

**Ошибки:**
- `400 invalid-citation` - не ровно один режим / range invalid / bbox invalid
- `404 node-not-found` / `book-not-found` / `page-not-found`
- `404 pdf-not-available` - PDF не существует или soft-deleted
- `404 image-region-not-found`

### NodeSourceResponse (рефакторен в Этапе 20.a, ADR-028; surrogate id - ADR-029)

```json
{
  "id": "uuid",
  "nodeId": "uuid",
  "sourceId": "uuid",
  "quote": "string|null",
  "context": "string|null",
  "mode": "TEXT|PDF|REGION|LEGACY",
  "citation": {
    "authority": {
      "id": "uuid",
      "name": "ابن كثير",
      "fullName": "إسماعيل بن عمر بن كثير الدمشقي",
      "deathYearHijri": 774
    } | null,
    "book": {
      "id": "uuid",
      "title": "تفسير القرآن العظيم",
      "language": "ar",
      "editionNumber": 2,
      "publishedYearHijri": 1420,
      "publishedYearGregorian": 1999
    } | null,
    "muhaqqiq": {
      "id": "uuid",
      "name": "السلامة",
      "fullName": "سامي بن محمد السلامة"
    } | null,
    "publisher": {
      "id": "uuid",
      "name": "Дар Тайба"
    } | null,
    "publicationPlace": {
      "id": "uuid",
      "name": "Эр-Рияд"
    } | null,
    "location": {
      "pageId": "uuid",
      "part": "1",
      "printedPage": "145",
      "pageNumber": 145,
      "rangeStart": 100,
      "rangeEnd": 200
    } | null,
    "pdf": {
      "fileId": "uuid",
      "pageNumber": 47,
      "bbox": {...}
    } | null,
    "region": {
      "id": "uuid",
      "printedPage": "13",
      "pageNumber": 7
    } | null
  } | null,
  "createdAt": "iso8601",
  "hadith": {
    "hadithId": "uuid",
    "primaryNumber": 1,
    "collectionName": "Сахих аль-Бухари",
    "previewMatn": "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ",
    "status": "CANONICAL"
  } | null
}
```

`id` (ADR-029) - surrogate UUID PK для отдельного citation link. Нужен
для точечного `DELETE` (см. ниже) и idempotency key для будущего
PATCH-обновления quote/context.

`citation` - structured nested объект, каждый из 8 nested refs nullable.
Frontend проверяет каждый и рендерит соответствующий блок (RTL/naskh для
arabic полей, monospace для location). Старые плоские поля (location,
pageId, rangeStart, rangeEnd, pdfFileId, pdfPageNumber, pdfBbox,
imageRegionId, bookId) **удалены** - единственный источник истины в
nested citation.

`mode` derived из заполненности positional полей:
- TEXT когда `pageId` set
- PDF когда `pdfFileId` set
- REGION когда `imageRegionId` set
- LEGACY когда все positional NULL (citation = null или все nested refs null)

`hadith` (под-проект #2) — не-null **только** если source это мост хадиса
(`hd_hadiths.source_id` указывает на этот source, sourceType=HADITH). Несёт
хадис-специфику для рендера карточки без доп. GET: `hadithId`, `primaryNumber`
(nullable), `collectionName` (nameRu→nameAr→slug, nullable), `previewMatn`
(диакритизированный text_ar первичного matn, nullable), `status`
(CANONICAL/VARIANT/WEAK/FABRICATED — для бейджа). Для обычных source-опор
(BOOK/etc) поле = null. Обогащение делается batch'ем в `GET
/nodes/{id}/sources` (reverse lookup `findBySourceIds` + batch previewMatn,
без N+1).

### GET /api/v1/nodes/{nodeId}/sources (обновлён)

Возвращает массив **расширенного NodeSourceResponse** (с positional полями
и computed location). Существующие clients игнорируют новые поля - backward
compatible.

## Q&A API (ADR-032, Этап 19.a)

Второе приложение платформы (после argument-map и library). На MVP -
standalone Question CRUD без attached sources. Source attach
(`question_sources` аналог `node_sources`) отложен на Этап 19.b.

### POST /api/v1/questions - создать вопрос

Заголовки: `X-User-Id: <uuid>` (обязательный, для `asked_by`).

Тело (`CreateQuestionRequest`):
```json
{
  "title": "Каково положение хадиса о намазе?",
  "body": "Подскажите достоверность хадиса с разными цепочками."
}
```

Валидация:
- `title` обязателен, NotBlank, до 500 символов
- `body` опционален, до 10000 символов

Ответ `201 Created` (`QuestionResponse`):
```json
{
  "id": "uuid",
  "title": "...",
  "body": "...|null",
  "status": "OPEN|ANSWERED|CLOSED",
  "askedBy": "uuid|null",
  "createdAt": "iso8601",
  "updatedAt": "iso8601"
}
```

### GET /api/v1/questions

Список вопросов с пагинацией. Сортировка - сначала самые новые
(`created_at DESC`). Использует partial индекс
`idx_questions_status_created`.

Параметры:
- `status` (опционально) - фильтр по `OPEN`/`ANSWERED`/`CLOSED`
- `q` (опционально) - case-insensitive ILIKE по `title`
- `page` (опционально, default 0), `size` (default 20, max 100)

Ответ `200 OK`: `PagedResponse<QuestionResponse>`.

### GET /api/v1/questions/{id}

Detail вопроса. Ошибка `404 question-not-found`.

### PATCH /api/v1/questions/{id}

Partial update title/body/status. `null` поля = no change. Валидация
размеров та же что в Create.

**Заголовки:** `X-User-Id: <uuid>` (обязательно с 22.c для author/admin guard)

**Permission** (ADR-043 Amendment, Этап 22.c): только автор вопроса
(`askedBy`) или ADMIN могут редактировать.

Тело (`UpdateQuestionRequest`, все optional):
```json
{
  "title": "...",
  "body": "...",
  "status": "OPEN|ANSWERED|CLOSED"
}
```

Ответ `200 OK` - обновлённый `QuestionResponse`.

Ошибки:
- `403 forbidden-question-write` - не автор и не ADMIN
- `404 question-not-found`

### DELETE /api/v1/questions/{id}

Hard delete (MVP без soft delete + audit).

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Permission** (ADR-043 Amendment): только автор или ADMIN.

Ответ `204 No Content`.

Ошибки:
- `403 forbidden-question-write` - не автор и не ADMIN
- `404 question-not-found`

### POST /api/v1/questions/{questionId}/citations - привязать positional citation (19.b)

Создаёт citation в одном из трёх режимов (TEXT/PDF/REGION). Структура
запроса и ошибки **идентичны** `POST /api/v1/nodes/{nodeId}/citations`
(ADR-027) - reuse того же `CitationRequest` DTO + `NodeCitationService`-
аналог `QuestionCitationService` с identical валидацией.

Request body (`CitationRequest`):

```json
{
  "bookId":   "uuid-required",
  "pageId":   "uuid (TEXT mode)",
  "rangeStart": 0,
  "rangeEnd":   87,
  "pdfFileId":  "uuid (PDF mode)",
  "pdfPageNumber": 47,
  "pdfBbox":   { "x": 0.1, "y": 0.2, "width": 0.5, "height": 0.04 },
  "imageRegionId": "uuid (REGION mode)",
  "quote": "опциональный текст",
  "context": "опциональный комментарий"
}
```

Ровно один из (`pageId`/`pdfFileId`/`imageRegionId`) обязателен.
Response `201 Created` с `QuestionSourceResponse` (структура аналогична
`NodeSourceResponse`, но с `questionId` вместо `nodeId`, без поля
`legacySnapshot` - freeform mode для questions не реализован).

Ошибки:
- `404 question-not-found` - вопрос с таким id не существует
- `404 book-not-found`/`page-not-found`/`pdf-not-available`/`image-region-not-found`
- `400 invalid-citation` - валидация (>1 mode, range_end<=range_start,
  pageId не принадлежит bookId, etc)

### GET /api/v1/questions/{questionId}/sources - список citations вопроса

Возвращает `List<QuestionSourceResponse>` с structured citation (9 LEFT
JOIN на authority/book/muhaqqiq/publisher/publication_place/page/region).
Сортировка по `created_at`. Идентичная схема `GET /api/v1/nodes/{id}/sources`.

### DELETE /api/v1/questions/sources/{questionSourceId} - detach citation

Удаление по surrogate UUID id citation link'а (ADR-029 FK variant A).
Response `204 No Content`. Ошибка `400 invalid-citation` если link не
найден (semantically should be 404, оставлено для unified error handler -
см. roadmap «Этап 6 нормализация error codes»).

### Что **не** реализовано в Этапе 19.b

- Freeform LEGACY citation для questions (нет `POST /api/v1/questions/
  {id}/sources` legacy endpoint). Schema поддерживает, controller -
  только если появится UX-кейс
- Soft delete + audit (после auth)
- Full-text search по body (сейчас только title ILIKE)

## Answers API (ADR-034, Этап 19.c)

Ответы на questions. На MVP - простая структура: создание/чтение/
редактирование/удаление + accept-answer flow. Voting + comments
отложены на 19.d/19.e.

### POST /api/v1/questions/{questionId}/answers - создать ответ

Заголовки: `X-User-Id: <uuid>` (обязательный, для `author_id`).

Тело (`CreateAnswerRequest`):
```json
{ "body": "Согласно хадису..." }
```

Валидация: `body` обязателен, NotBlank, до 10000 символов.

Ответ `201 Created` (`AnswerResponse`):
```json
{
  "id": "uuid",
  "questionId": "uuid",
  "body": "...",
  "authorId": "uuid",
  "createdAt": "iso8601",
  "updatedAt": "iso8601",
  "accepted": false
}
```

Поле `accepted` - derived, рассчитывается сравнением `answer.id ==
question.acceptedAnswerId`. На новом ответе всегда `false`.

Ошибки:
- `404 question-not-found` - вопрос не существует
- `400` - body пустой / превышает лимит

### GET /api/v1/questions/{questionId}/answers - список ответов

Сортировка: принятый ответ первым (если есть), остальные по
`created_at` ASC. Возвращает `List<AnswerResponse>`.

Ошибки: `404 question-not-found`.

### PATCH /api/v1/answers/{answerId} - редактировать ответ

**Заголовки:** `X-User-Id: <uuid>` (обязательно с 22.c)

**Permission** (ADR-043 Amendment, Этап 22.c): только автор ответа
(`authorId`) или ADMIN могут редактировать.

Тело (`UpdateAnswerRequest`): только `body` обязателен (NotBlank,
до 10000).

Ответ `200 OK` - обновлённый `AnswerResponse`.

Ошибки:
- `404 answer-not-found`
- `400` - body пустой
- `403 forbidden-answer-write` - не автор и не ADMIN

### DELETE /api/v1/answers/{answerId} - удалить ответ

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Permission** (ADR-043 Amendment): только автор или ADMIN.

Ответ `204 No Content`. Ошибки:
- `404 answer-not-found`
- `403 forbidden-answer-write` - не автор и не ADMIN

### POST /api/v1/questions/{questionId}/accepted-answer/{answerId}

Принять ответ как accepted. Атомарно обновляет
`questions.accepted_answer_id = answerId` + `status = 'ANSWERED'`.

Ответ `200 OK` - обновлённый `QuestionResponse` (с
`acceptedAnswerId` и `status = 'ANSWERED'`).

Ошибки:
- `404 question-not-found` / `404 answer-not-found`
- `400 illegal-argument` - ответ принадлежит другому вопросу
  (`Ответ X не принадлежит вопросу Y`)

### DELETE /api/v1/questions/{questionId}/accepted-answer

Снять принятие ответа. Атомарно очищает `accepted_answer_id = NULL`
+ переводит `status = 'OPEN'`.

Ответ `200 OK` - обновлённый `QuestionResponse`.

Ошибки: `404 question-not-found`.

### Расширение QuestionResponse

С Этапа 19.c в `QuestionResponse` добавлено поле:

```
"acceptedAnswerId": "uuid|null"
```

Не `null` означает что у вопроса есть принятый ответ (обычно когда
`status = ANSWERED`).

### Что **не** реализовано в Этапе 19.c

- Voting на answers (up/down votes) - откладывается на отдельный этап
- Comments на answers - откладывается на отдельный этап
- Nested answers / threading - не в скоупе MVP
- Edit history / revisions для answers (как для nodes есть revisions)
- Soft delete (hard delete на MVP)

## Answer sources API (ADR-033 итерация 3, Этап 19.d)

Citation на ответы - параллельная иерархия `answer_sources` рядом с
`question_sources` и `node_sources`. Та же семантика, тот же
`CitationRequest`, тот же 9-LEFT-JOIN structured response. 3-я
итерация валидирует что platform pivot (ADR-018) масштабируется
без перехода на generic citations table.

### POST /api/v1/answers/{answerId}/citations - привязать positional citation

Создаёт citation в одном из трёх режимов (TEXT/PDF/REGION). Request
body, валидация и ошибки идентичны
`POST /api/v1/questions/{id}/citations` и
`POST /api/v1/nodes/{id}/citations` (ADR-027) - reuse того же
`CitationRequest` DTO + `AnswerCitationService`-аналог
`QuestionCitationService` с identical логикой.

Response `201 Created` с `AnswerSourceResponse` (структура аналогична
`QuestionSourceResponse`, но с `answerId` вместо `questionId`):

```json
{
  "id": "uuid",
  "answerId": "uuid",
  "sourceId": "uuid",
  "quote": "...",
  "context": "...",
  "mode": "TEXT|PDF|REGION",
  "citation": { "...": "9-полевая structured citation (ADR-028)" },
  "createdAt": "iso-instant"
}
```

Ошибки:
- `404 answer-not-found` - ответ с таким id не существует
- `404 book-not-found`/`page-not-found`/`pdf-not-available`/`image-region-not-found`
- `400 invalid-citation` - валидация (>1 mode, range_end<=range_start,
  pageId не принадлежит bookId, etc)

### GET /api/v1/answers/{answerId}/sources - список citations ответа

Возвращает `List<AnswerSourceResponse>` с structured citation. Сортировка
по `created_at`. Идентичная схема `GET /api/v1/questions/{id}/sources`.

### DELETE /api/v1/answers/{answerId}/sources/{answerSourceId} - detach citation

Удаление по surrogate UUID id citation link'а (ADR-029 FK variant A).
URL hierarchy сохраняет `answerId` под будущую авторизацию по владельцу
ответа (зеркало QuestionCitationController). Response `204 No Content`.
Ошибка `404 source-not-found` если link не найден.

### Что **не** реализовано в Этапе 19.d (перенесено сюда)

- Freeform LEGACY citation для answers (нет
  `POST /api/v1/answers/{id}/sources` legacy endpoint). Schema
  поддерживает - добавим если появится UX-кейс
- Soft delete + audit (после auth)

## Hadith Explorer API (sanad graph)

Домен `hd_*` (Hadith Chains Explorer, ADR-049). Эндпоинты read-only,
данные публичные. Доменная модель - в spec
`docs/superpowers/specs/2026-05-20-hadith-explorer-design.md`.

### GET /api/v1/hadith/collections

Список сборников `hd_collections` (под-проект #1, chip-фильтр). Возвращает
`List<CollectionResponse>` = `{ id, slug, nameAr, nameEn, nameRu, totalHadith
(заявленный объём), hadithCount (реально импортировано в hd_hadiths) }`.

### GET /api/v1/hadith/hadiths

Список, `PagedResponse<HadithResponse>`. Фильтры: `q` (подстрока по
normalized matn), `status` (CANONICAL/VARIANT/WEAK/FABRICATED), `collectionId`
(UUID сборника — переименован с `bookId`). `sort` (whitelist): `recent`
(default, created_at DESC), `number` (primary_number ASC), `alphabetical`
(normalized_matn ASC — арабский алфавитный). `HadithResponse.previewMatn` —
текст первичного matn (диакритизированный, очищенный) для карточки списка
(красивее folded normalizedMatn; nullable; batch-load, без N+1).

### POST /api/v1/nodes/{nodeId}/hadith-citations

Прикрепляет хадис из `hd_*` к узлу как опору (под-проект #2). Body
`AttachHadithCitationRequest {hadithId}`. Переиспользует мост
`Hadith.sourceId → sources.id`: ensure-source (создаёт `Source` sourceType=HADITH,
title «`<сборник> №<номер>`», идемпотентно — один Source на хадис) + линкует в
`node_sources`. authz: assertCanWrite на тему узла (ADR-043) → 403
forbidden-topic-write (или forbidden-topic-access если read закрыт). 404
hadith-not-found. **201** `HadithCitationResponse {nodeSourceId, nodeId,
hadithId, sourceId}`. List/detach хадис-опор — через существующий
`GET/DELETE /api/v1/nodes/{id}/sources` (хадис-опора = node_source с HADITH-
источником). Рендер хадис-опоры на фронте (matn-сниппет + ссылка) — follow-up
(обогащение source-списка `hadithId`/previewMatn).

### GET /api/v1/hadith/hadiths/{id}/detail

Bundled detail: hadith + sanads (с narrator-link'ами) + matns + `grades`
(курируемые оценки учёных `[{scholar, grade, note}]` из
`hd_hadiths.metadata.grades`) в одном payload (N+1 avoidance). 404
`hadith-not-found`.

### GET /api/v1/hadith/hadiths/{id}/sanad-graph

Граф иснада, преднастроенный под React Flow. **Узлы дедуплицированы**
(один narrator = один узел даже если он в нескольких цепях); сверху
добавляется синтетический корень `prophet` (Пророк ﷺ), соединённый со
сподвижником (position 0). Рёбра дедуплицируются по `source->target`,
видимая подпись берётся из primary chain, число цепей через ребро -
в `sanadCount`.

**Response** `SanadGraphResponse`:

```jsonc
{
  "hadithId": "uuid",
  "nodes": [
    { "id": "prophet", "role": "PROPHET",
      "data": { "narratorId": null, "nameAr": "النبي محمد ﷺ", "tier": 0 } },
    { "id": "narrator-{uuid}", "role": "COMPANION|NARRATOR|COLLECTOR",
      "data": {
        "narratorId": "uuid", "nameAr": "...", "nameRu": "...",
        "kunya": "...", "laqab": "...", "yearDeathHijri": 143,
        "reliabilityGrade": "THIQA|SADUQ|MAQBUL|DAIF|MATRUK|SAHABI|UNKNOWN",
        "reliabilityComment": "...", "generation": "...",
        "collection": "Сахих аль-Бухари (только COLLECTOR)", "tier": 4
      } }
  ],
  "edges": [
    { "id": "edge-0", "source": "prophet", "target": "narrator-{uuid}",
      "data": { "transmissionPhrase": "سَمِعْتُ", "chainGrade": "SAHIH",
                "onPrimaryChain": true, "sanadCount": 3 } }
  ],
  "sanads": [
    { "id": "uuid", "collectionRu": "Сахих аль-Бухари",
      "collectionAr": "صحيح البخاري", "chainGrade": "SAHIH",
      "primaryChain": true, "collectorNodeId": "narrator-{uuid}" }
  ]
}
```

- `role`: `PROPHET` (синтетический корень) / `COMPANION` (position 0) /
  `COLLECTOR` (составитель, есть в `compiledById` sanad'а) / `NARRATOR`.
- `tier` - глубина (Пророк ﷺ = 0, далее position+1) для семантики.
- `data.collection` заполняется только для `COLLECTOR` (из metadata sanad'а).
- 404 `hadith-not-found` если хадиса нет.

Питает компонент `SanadGraph` (React Flow, dagre TB layout, read-only).

### GET /api/v1/hadith/narrators

Каталог передатчиков, `PagedResponse<NarratorResponse>`. Фильтры: `q`
(подстрока по name_ar_normalized), `reliability` (THIQA / SADUQ / MAQBUL /
DAIF / MATRUK / SAHABI / UNKNOWN).

### GET /api/v1/hadith/narrators/{id}

Биография передатчика. 404 `narrator-not-found`.

### GET /api/v1/hadith/narrators/{id}/transmitted

Хадисы, в иснадах которых встречается этот передатчик (علم الرجال) —
через `hd_sanad_narrators → hd_sanads → hd_hadiths` (DISTINCT, т.к. один
хадис может ссылаться на narrator'а в нескольких своих цепях).
`PagedResponse<HadithResponse>`, сортировка `created_at DESC`. Paginated —
у плодовитых сподвижников счёт хадисов идёт на тысячи. 404
`narrator-not-found`.

## Sunnah Import Admin API (ADR-052, Phase 5 шаг 2.d)

Импорт корпуса хадисов из дампа sunnah.com в `hd_*`. **ADMIN-only** (как
audit admin). Источник (`SunnahDataSource`) активен только при
`sunnah.dump.enabled=true` (+ `SUNNAH_DUMP_URL/USERNAME/PASSWORD` →
MySQL с загруженным `db/00-samplegitdb.sql`). Если не сконфигурирован — **503**
`sunnah-dump-not-configured`. **Bulk-policy gate:** импорт строго по одному
сборнику явным вызовом.

### GET /api/v1/admin/sunnah/collections

Превью каталога сборников из источника (до импорта). `List<SunnahCollectionPreview>`
= `{ name, titleEn, titleAr, totalHadith, hasBooks, hasChapters }`. ADMIN-only
(403 `forbidden-admin-only`). 503 если источник не настроен.

### POST /api/v1/admin/sunnah/import/{collection}

Импорт одного сборника: источник → `sn_staging_*` → `SunnahToHadithMapper`
→ `hd_collections`/`hd_hadiths`/`hd_matns`. **Идемпотентно** (по
`(collection_id, primary_number)` — уже импортированные/курируемые
пропускаются). Ответ `SunnahImportResponse` =
`{ collectionName, inserted, skippedExisting, skippedInvalid }`. ADMIN-only
(403). 503 если источник не настроен.

Импортированные хадисы — `status=VARIANT` (не выдаются за канон); текст ar/en
в `hd_matns`, grades (если есть) в `hd_hadiths.metadata`, структура книга/глава
в `hd_matns.metadata`. Структурный иснад НЕ извлекается (sunnah даёт matn+isnad
блобом — отдельная стадия IsnadExtraction, шаг 3).

## Hadith grades API (multi-grading)

Один и тот же хадис (source с `sourceType=HADITH`) может быть оценён
разными учёными по-разному. Существующее single-value поле
`sources.reliability` остаётся как primary/legacy. Расширение - таблица
`hadith_grades` с whitelist оценок `SAHIH/HASAN/DAIF/MAUDU` и unique
constraint `(source_id, scholar_id)` - один scholar даёт одну оценку
конкретному источнику.

`MAUDU` («выдуманный») - четвёртая категория, отсутствует в legacy enum
`Reliability` (используется только в multi-grading через
`HadithGradeValue`).

**Permission rules:**

- `POST` / `GET` - любой authenticated user
- `PATCH` / `DELETE` - только автор оценки (`createdBy`) либо `ADMIN`

### POST /api/v1/sources/{sourceId}/grades

**Body** `CreateHadithGradeRequest`:

```json
{
  "scholarId": "uuid",
  "grade": "SAHIH | HASAN | DAIF | MAUDU",
  "gradeCitation": "string (max 500, optional)",
  "comment": "string (max 5000, optional)"
}
```

**Response 201 Created** + `Location: /api/v1/sources/grades/{gradeId}`,
тело `HadithGradeResponse` (без denormalized scholar полей - фронт
получит их через `GET /sources/{id}/grades`).

**Ошибки:**

- 400 `invalid-hadith-grade` - source не HADITH (нельзя grade'нуть
  BOOK / QURAN / ARTICLE) либо `grade` null
- 400 `invalid-scholar-authority` - resolved authority имеет
  `type != SCHOLAR` (например PUBLISHER, MUHAQQIQ). properties
  `authorityId`, `actualType`, `expectedType=SCHOLAR`. Оценивать
  хадис может только учёный, не издательство и не тахкик
- 400 validation - missing `scholarId` / `grade`
- 404 `source-not-found` / `authority-not-found`
- 409 `hadith-grade-duplicate` - тот же scholar уже оценил
  (properties `sourceId`, `scholarId`)

### GET /api/v1/sources/{sourceId}/grades

**Response 200** - `HadithGradeResponse[]` (массив, не paged - реалистичный
N для одного хадиса <50, pagination избыточна). Сортировка `created_at ASC`.
Содержит denormalized scholar info (`scholarName`, `scholarFullName`,
`scholarDeathYearHijri`) через JOIN с `authorities` - один SQL без N+1.

**Ошибки:** 404 `source-not-found`.

### PATCH /api/v1/sources/grades/{gradeId}

**Body** `UpdateHadithGradeRequest`:

```json
{
  "grade": "SAHIH | HASAN | DAIF | MAUDU",
  "gradeCitation": "string (max 500, optional)",
  "comment": "string (max 5000, optional)"
}
```

`grade` обязателен. `gradeCitation` / `comment` - replace-semantics
(передать `null` чтобы очистить).

**Response 200** - `HadithGradeResponse`.

**Ошибки:**

- 400 validation - missing `grade`
- 403 `forbidden-hadith-grade-write` - не автор и не ADMIN
  (properties `gradeId`, `userId`)
- 404 `hadith-grade-not-found`

### DELETE /api/v1/sources/grades/{gradeId}

**Response 204 No Content**.

**Ошибки:** 403 `forbidden-hadith-grade-write` / 404 `hadith-grade-not-found`.

### HadithGradeResponse

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | id записи |
| `sourceId` | UUID | FK на `sources` |
| `scholarId` | UUID | FK на `authorities` |
| `scholarName` | string \| null | denormalized, заполняется только в GET list |
| `scholarFullName` | string \| null | полное имя учёного из `authorities.full_name` |
| `scholarDeathYearHijri` | integer \| null | год смерти по хиджре |
| `grade` | string | `SAHIH` / `HASAN` / `DAIF` / `MAUDU` |
| `gradeCitation` | string \| null | где scholar дал эту оценку (книга/страница) |
| `comment` | string \| null | комментарий учёного |
| `createdAt` | timestamp | UTC |
| `createdBy` | UUID | автор записи в системе |

## Audit log API (ADR-043 Amendment 3, Этап 22.d)

Просмотр аудит-трейла мутаций per-entity. Все mutations
(create/update/delete + visibility/member changes) на topics / nodes /
edges / books / questions / answers / members пишутся в `audit_log`
synchronous в той же транзакции что и main flow.

**Permission rules:**

- `/audit/topics/{id}` и `/audit/books/{id}` - owner + EDITOR
  (privileged даже на SHARED/PUBLIC)
- `/audit/me` - любой authenticated user (видит только свои actions)
- `/audit/admin` - только `UserRole.ADMIN`

### GET /api/v1/audit/topics/{topicId}

audit темы + всех её child entities (nodes/edges/topic_members).
Query: `?page=&size=` (default 0/20, max size=100).

**Response 200** - `PagedResponse<AuditLogResponse>`:

```json
{
  "items": [
    {
      "id": "uuid",
      "entityType": "TOPIC",
      "entityId": "uuid",
      "parentEntityType": null,
      "parentEntityId": null,
      "action": "CREATE",
      "actorUserId": "uuid",
      "actorUsername": "string",
      "changes": "{\"created\":{\"title\":\"...\",\"visibility\":\"PUBLIC\"}}",
      "createdAt": "2026-05-18T12:00:00Z"
    }
  ],
  "page": 0, "size": 20,
  "totalElements": 5, "totalPages": 1,
  "hasNext": false, "hasPrev": false
}
```

**Ошибки:**

- 403 forbidden-topic-access - не owner и не EDITOR (либо приватная
  тема не для вас)
- 403 forbidden-topic-write - можете читать, но не писать (member
  без EDITOR)
- 403 forbidden-deleted-topic-audit - тема удалена, но preserved
  audit_log rows видны только ADMIN (compliance forensics). Бывший
  owner получает 403, не 404 - чтобы не leak'ать существование
  удалённого ресурса бывшему доступу. Properties: `topicId`, `userId`
- 404 topic-not-found - тема не существует И audit пустой

### GET /api/v1/audit/books/{bookId}

Mirror /audit/topics для книг. Permission: `assertCanWriteBook`.
Ошибки: 403 forbidden-book-access / forbidden-book-write /
403 forbidden-deleted-book-audit (удалённая книга, не-ADMIN) /
404 book-not-found.

### GET /api/v1/audit/me

audit-trail current user'а - что он делал. Любой authenticated.
Query: `?page=&size=`.

### GET /api/v1/audit/admin

Admin-only endpoint с фильтрами:

- `entityType` - whitelist `TOPIC` / `NODE` / `EDGE` / `BOOK` /
  `QUESTION` / `ANSWER` / `TOPIC_MEMBER` / `BOOK_MEMBER` /
  `*_SOURCE`. Невалидный → 400 illegal-argument
- `actorId` - UUID
- `dateFrom`, `dateTo` - ISO-8601 instants (`2026-05-18T10:00:00Z`)
- `page`, `size`

**Ошибки:**

- 403 forbidden-admin-only - не ADMIN
- 400 illegal-argument - невалидный entityType / dateFrom / dateTo

### AuditLogResponse

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | id записи в audit_log |
| `entityType` | string | `TOPIC` / `NODE` / `EDGE` / etc |
| `entityId` | UUID | id затронутой сущности |
| `parentEntityType` | string \| null | parent для child entities (TOPIC для node/edge) |
| `parentEntityId` | UUID \| null | id parent'а |
| `action` | string | `CREATE` / `UPDATE` / `DELETE` / `VISIBILITY_CHANGE` / `MEMBER_ADD` / `MEMBER_REMOVE` / `MEMBER_ROLE_CHANGE` |
| `actorUserId` | UUID | кто выполнил действие |
| `actorUsername` | string \| null | JOIN с users.username для UI |
| `changes` | string (JSON) | raw jsonb - формат зависит от action (см. ниже) |
| `createdAt` | timestamp | UTC |

**Формат `changes` JSON по action:**

- `CREATE`: `{"created": {...snapshot}}`
- `UPDATE`: `{"field": {"old": X, "new": Y}, ...}`
- `DELETE`: `{"deleted": {...snapshot}}`
- `VISIBILITY_CHANGE`: `{"visibility": {"old": "PRIVATE", "new": "PUBLIC"}}`
- `MEMBER_ADD` / `MEMBER_REMOVE`: `{"userId": "uuid", "role": "EDITOR"}`
- `MEMBER_ROLE_CHANGE`: `{"userId": "uuid", "role": {"old": "MEMBER", "new": "EDITOR"}}`

Frontend парсит `changes` по `action` - схема не валидируется
backend'ом (changes это free-form jsonb).

## Topic export/import API (ADR-037, Этап 6)

JSON-сериализация темы целиком для backup и обмена между инстансами.
Формат - см. ADR-037.

### GET /api/v1/topics/{topicId}/export - выгрузить тему как JSON

**Заголовки:** не требуется `X-User-Id` (read-only)

**Ответ (200 OK):**
- `Content-Type: application/json`
- `Content-Disposition: attachment; filename="topic-{shortId}.json"`
  (первые 8 символов UUID)
- Тело: `TopicExportDto`:
```json
{
  "formatVersion": "1.0",
  "exportedAt": "2026-05-17T10:00:00Z",
  "topic": {
    "id": "550e8400-...",
    "title": "...",
    "description": "...",
    "rootNodeId": "550e8400-...",
    "createdBy": "...",
    "createdAt": "..."
  },
  "nodes": [
    { "id": "...", "topicId": "...", "nodeType": "QUESTION",
      "content": "...", "status": "UNVERIFIED",
      "posX": 100.0, "posY": 200.0,
      "createdBy": "...", "createdAt": "...", "updatedAt": "..." }
  ],
  "edges": [
    { "id": "...", "fromNodeId": "...", "toNodeId": "...",
      "edgeType": "SUPPORTS", "rationale": "...",
      "sourceHandle": "right", "targetHandle": "left",
      "createdBy": "...", "createdAt": "..." }
  ],
  "nodeSources": [
    { "id": "...", "nodeId": "...", "sourceId": "...",
      "quote": "...", "context": "...", "location": "...",
      "pageId": null, "rangeStart": null, "rangeEnd": null,
      "pdfFileId": null, "pdfPageNumber": null, "pdfBbox": null,
      "imageRegionId": null,
      "createdAt": "..." }
  ],
  "sources": [
    { "id": "...", "sourceType": "BOOK", "title": "...",
      "citation": "...", "reliability": null,
      "authorityId": "...", "bookId": "...", "metadata": null,
      "createdAt": "..." }
  ],
  "authorities": [
    { "id": "...", "name": "Имам Малик", "bio": null,
      "era": "ранний", "madhab": "малики", "metadata": null,
      "createdAt": "...", "fullName": "Малик ибн Анас",
      "deathYearHijri": 179 }
  ],
  "books": [
    { "id": "...", "title": "...", "authorityId": "..." }
  ]
}
```

Дедупликация: sources/authorities/books собираются как unique по
id (один source привязан к N узлам → в DTO один раз). Books - hint
only (id+title+authorityId), полная сериализация исключена (книги
это shared library resource, ADR-019). Revisions намеренно
исключены - история не нужна для обмена/backup.

**Ошибки:** 404 `topic-not-found`

### POST /api/v1/topics/import - импортировать тему из JSON

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Content-Type:** один из двух вариантов
- `application/json` - тело это сразу `TopicExportDto` (для curl /
  programmatic flow)
- `multipart/form-data` - поле `file` содержит JSON (для UI
  `<input type="file">`)

**Ответ (201 Created):**
```json
{
  "topicId": "новый-uuid",
  "importedNodes": 5,
  "importedEdges": 4,
  "importedNodeSources": 3,
  "importedSources": 2,
  "importedAuthorities": 1,
  "warnings": [
    "source 'Книга X' ссылается на книгу id=... которая отсутствует в библиотеке этого инстанса - импортирован без bookId (deep link на reader работать не будет)"
  ]
}
```

Семантика:
- **UUID remapping** - все импортируемые id пере-генерируются. Old→New
  mapping применяется к FK references (edges.fromNodeId/toNodeId,
  node_sources.nodeId/sourceId)
- **createdBy** новой темы и всех её сущностей - всегда импортирующий
  `X-User-Id`, не значение из payload (security)
- **Authorities** - find-or-create по name. Дубликатов не плодим
- **Books** - find-or-skip по UUID. Если книги нет на target инстансе -
  source создаётся без bookId + warning. quote/title сохраняются
- **Positional refs** (pageId/pdfFileId/imageRegionId) - null'ифицируются
  если source без bookId (warning). quote/context/location остаются как
  fallback
- `formatVersion` whitelist: `{"1.0"}`. Иной → 422
  `unsupported-format-version` с `receivedVersion` + `supportedVersions`
  properties в Problem Details

**Ошибки:**
- 400 - missing X-User-Id / невалидный JSON / topic=null
- 422 `unsupported-format-version` - formatVersion не в whitelist

## История изменений контракта

| Дата | Версия API | Что изменилось | Причина |
|------|------------|----------------|---------|
| 2026-06-01 | v1 | **Security: NodeCitation sibling-path write-guard (ADR-043, замыкание sweep).** `POST /api/v1/nodes/{nodeId}/citations` (structured citation через CitationPicker) — sibling freeform `/sources` (attach), тот же контентный mutating-эффект (insert в `node_sources`), но БЕЗ write-guard'а → guard на `/sources` обходился через `/citations`. Теперь требует `@CurrentUser` + `assertCanWrite` на тему узла (`NodeCitationService.createCitation` role-aware overload, +PermissionService dep). non-writer → 403 `forbidden-topic-write`, non-reader на PRIVATE → 403 `forbidden-topic-access`, node-not-found → 404 (lookup до permission-check). +403 IT; NodeCitationControllerIT обновлён (X-User-Id обязателен). Найдено automated security review коммита 5f27689 | Sibling-path bypass: ADR-043 guard на одном пути бесполезен если параллельный путь к той же мутации не закрыт. Канон тот же (Service-слой, controller прокидывает @CurrentUser+role, legacy overload для internal/IT) |
| 2026-06-01 | v1 | **Thesis (рисала) metadata для книг.** Миграция 58 ALTER `lib_books` ADD `thesis_degree` / `thesis_supervisor` / `thesis_institution` (TEXT nullable). `BookDetailResponse` расширен этими 3 полями (nullable, заполнены только для shamela-диссертаций). `ShamelaBibliographyParser` теперь распознаёт academic-thesis markers в «بطاقة الكتاب»: `رسالة:` → degree (ماجستير/دكتوراه) + institution (split по «،»/« - »), `إشراف:` → supervisor, `العام الجامعي:` → published_year_hijri. `ShamelaToLibraryMapper` (import) + `ShamelaBibliographyBackfillService` (backfill existing, новый `BookRepository.updateThesisMetadata`) наполняют structured-колонки. Frontend `BookHeader` рендерит thesis строками (РАБОТА/НАУЧНЫЙ РУКОВОДИТЕЛЬ/УЧЕБНОЕ ЗАВЕДЕНИЕ) внутри metadata-box, i18n RU/AR. `Book` record получил 3 поля + backward-compat 17-арг конструктор (existing call-sites не тронуты) | Часть shamela-книг - академические рисала (магистерские/докторские). Их «بطاقة الكتاب» несёт degree/supervisor/institution которые НЕ ложились в publisher/muhaqqiq. Раньше эти данные дампились сырым текстом в description (создавая дубль со structured-таблицей у книг с распарсенным автором). Принцип: наполнять нашу таблицу через parser, а НЕ вставлять текст shamela. Per-book scalars (не reusable), TEXT прямо на lib_books без справочника |
| 2026-06-01 | v1 | **Security: закрытие 3 оставшихся citation authorization-дыр (ADR-043 coverage gaps, продолжение).** Code review после багоохоты нашёл 3 эндпоинта того же класса, что и фикс #6 (node-source), но не закрытые. Закрыто без изменения схемы БД / DTO (только permission-checks + parent-scoped SQL): (A) **NodeSource topic-level authz** — `POST /api/v1/nodes/{nodeId}/sources` (attach) + `DELETE .../{nodeSourceId}` (detach) теперь требуют `assertCanWrite` на тему узла, `GET .../sources` (list) — `assertCanRead` (раньше любой authenticated вешал/снимал/читал citation на узлах чужих PRIVATE/SHARED тем). Все три теперь требуют `@CurrentUser` (X-User-Id); non-reader → 403 `forbidden-topic-access`, non-writer → 403 `forbidden-topic-write`. Detach остаётся node-scoped (404 при mismatch); (B) **Q&A question citations** — `POST /api/v1/questions/{questionId}/citations` (create) + `DELETE .../sources/{questionSourceId}` (detach) теперь требуют автора вопроса (`asked_by`) или ADMIN; non-author → 403 `forbidden-question-write`. Detach стал question-scoped (`DELETE ... WHERE id=? AND question_id=?`) — citation чужого вопроса по голому id → 404. `GET .../sources` остаётся без guard (open discussion); (C) **Q&A answer citations** — `POST /api/v1/answers/{answerId}/citations` (create) + `DELETE .../sources/{answerSourceId}` (detach) теперь требуют автора ответа (`author_id`) или ADMIN; non-author → 403 `forbidden-answer-write`. Detach стал answer-scoped (`DELETE ... WHERE id=? AND answer_id=?`) → 404 при mismatch. `GET .../sources` без guard. Все 3 фикса с IT (403/404 + happy-path owner/author + ADMIN allow) | Code review #2 после commit 8dc88ad: тот же системный паттерн (ADR-043 permission-модель не вызывалась). Канон: проверки в Service-слое, controller прокидывает `@CurrentUser` + `SecurityContextUtils.currentRoleOrAnonymous()`. Q&A — author/admin guard (mutating only), не visibility. NodeSource — topic-scoped read/write как у NodeVote/NodeController. Legacy overloads без auth оставлены для internal callers / IT. Новые repo-методы `deleteByIdAndQuestion`/`deleteByIdAndAnswer` (mirror `NodeSourceRepository.deleteByIdAndNode`) — только новый SQL, без DDL |
| 2026-06-01 | v1 | **Security: закрытие authorization-дыр (ADR-043 coverage gaps).** Multi-agent багоохота выявила эндпоинты, не вызывавшие permission-модель. Закрыто без изменения схемы БД / DTO (только добавлены permission-checks): (1) `GET /api/v1/topics/{id}/export` — теперь требует `@CurrentUser` + `assertCanRead` (раньше любой мог скачать PRIVATE тему целиком; non-owner PRIVATE → 403 `forbidden-topic-access`, anonymous → 401); (2) `GET /api/v1/library/books/{bookId}/pdf` и `/pdf/info` — `PdfService.loadBook` теперь зовёт `assertCanReadBook` (IDOR: PRIVATE PDF качался перебором bookId; non-owner → 403 `forbidden-book-access`); (3) `POST /api/v1/questions/{qId}/accepted-answer/{aId}` и `DELETE .../accepted-answer` — accept/revoke теперь требуют автора вопроса или ADMIN (раньше любой STUDENT+ мог принять ответ на чужой вопрос; non-author → 403 `forbidden-question-write`); (4) `PATCH /api/v1/library/pages/{id}/formatted-content`, `POST /api/v1/library/pages/{id}/ai-edit`, `POST /api/v1/library/books/{bookId}/pages` — теперь требуют `assertCanWriteBook` на parent book (раньше любой мог переписать контент/жечь AI budget на чужой PRIVATE книге; non-writer → 403 `forbidden-book-write`); (5) `GET /api/v1/nodes/{id}/votes` — теперь `assertCanRead` на тему узла (раньше утекали агрегаты голосов узлов приватных тем; non-reader → 403 `forbidden-topic-access`); (6) `DELETE /api/v1/nodes/{nodeId}/sources/{nodeSourceId}` — теперь node-scoped (`DELETE ... WHERE id=? AND node_id=?`; раньше можно было удалить citation чужого узла по голому surrogate id; mismatch → 404). Новый helper `SecurityContextUtils.currentUserIdOrNull()` для service-слоя (PdfService читает principal из SecurityContext, не @CurrentUser). Все 6 фиксов с IT-тестами (403/404 + happy-path) | Багоохота (235 агентов, 48 подтверждённых findings, 3-линзовая adversarial-верификация). Системный паттерн: ADR-043 ввёл permission-модель, но часть эндпоинтов (library/PDF + Q&A accept) её не вызывала. Канон: проверки в Service-слое через PermissionService.assertCan*, controller прокидывает @CurrentUser + SecurityContextUtils.currentRoleOrAnonymous(). Legacy overloads без auth оставлены для internal callers / IT |
| 2026-05-19 | v1 | `Authority.type` whitelist для семантической роли (review round 3 #4). Миграция 47 ALTER `authorities` ADD COLUMN `type VARCHAR(20) NOT NULL DEFAULT 'SCHOLAR'` + CHECK `IN ('SCHOLAR','MUHAQQIQ','PUBLISHER','AUTHOR','OTHER')` + индекс. Backfill: все existing rows получают `SCHOLAR` (publishers и muhaqqiqs живут в отдельных таблицах `lib_publishers`/`lib_muhaqqiqs` с ADR-028, дублей нет). `AuthorityResponse` расширен полем `type`. `CreateAuthorityRequest` принимает optional `type` (default SCHOLAR). `HadithGradeService.addGrade` валидирует resolved scholar.type==SCHOLAR - попытка использовать `PUBLISHER`/`MUHAQQIQ`/`AUTHOR`/`OTHER` как scholar → 400 `invalid-scholar-authority` (properties `authorityId`, `actualType`, `expectedType=SCHOLAR`). Новая ошибка 400 `invalid-authority-type` (property `invalidType`) при создании authority с не-whitelist `type`. Total backend tests 998/998 pass | Backlog «Authority.type column для HadithGrade scholar validation» закрыт. Семантика: `sahih` от имени издательства - неверно. БД-уровень + сервисная валидация enforce constraint, не оставляя UI. Memory `feedback_no_prod_no_backward_compat` - сразу NOT NULL без NULL-fallback. ShamelaAuthorityResolver теперь явно ставит `AUTHOR` (книжный контекст), TopicImportService оставляет `null` → DB default SCHOLAR (старые экспорты не несут type-семантику) |
| 2026-05-19 | v1 | Refresh token rotation single-use (ADR-047). Миграция 46 - новая таблица `refresh_tokens(id UUID PK, user_id FK CASCADE, token_hash VARCHAR(64) UNIQUE, issued_at, expires_at, revoked_at, replaced_by FK self, revocation_reason VARCHAR(50))` + 3 индекса (partial по user/expires где revoked_at NULL, hash hot path). **Breaking semantic change**: `POST /api/v1/auth/refresh` теперь возвращает Set-Cookie с **новым** refresh-value (raw JWT) - старый помечен revoked в БД. Reuse уже rotated refresh = 401 + revoke всей chain user'а (steal detection через log.warn). `POST /api/v1/auth/logout` теперь читает refresh cookie и revoke'нет токен в БД (идемпотентно, no-op если cookie отсутствует). JWT structure расширен `jti` claim (UUID) - предотвращает collision двух токенов выпущенных в одну миллисекунду. SHA-256 hex hashing (не bcrypt) - refresh validated каждые ~15min, bcrypt медленный + JWT signature уже high-entropy. **Existing browser cookies с refresh от пред версии не валидны** после deploy - user просто re-login. Без новых error types (только семантика invalid-token расширена на reuse/revoked cases) | Reviewer round 5 Cross-cutting Important #4 закрыт. ADR-040 «открытый вопрос rotation» решён. Rejected: JWT blacklist в Redis (нет Redis в стэке), no-rotation (accept risk - unacceptable для user content + admin actions), sliding TTL без single-use (не закрывает stolen-vector), bcrypt вместо SHA-256 (performance). Memory `feedback_no_prod_no_backward_compat` - breaking change без backward compat |
| 2026-05-18 | v1 | Multi-translation 1:N узлов (translator attribution). Миграция 45 - новая таблица `node_translations (id UUID PK, node_id FK CASCADE, translator_name varchar(200) NULL, language varchar(5) CHECK ru\|en, body text NOT NULL, is_default boolean DEFAULT false, created_at, created_by FK users)` + 2 индекса (`node_id`, partial по `is_default=true`) + 2 partial unique indexes для `(node_id, translator_name, language)` когда NULL и not-NULL (Postgres UNIQUE считает все NULL разными). **Breaking change**: миграция 45 удаляет колонки `nodes.translation`/`nodes.translation_lang` (теперь в child-таблице), переносит existing 1:1 переводы как первую строку с `translator_name=NULL` и `is_default=true`. `originalLang` остаётся на nodes - это свойство оригинала. `NodeResponse` потерял поля `translation`/`translationLang`, получил `translations: NodeTranslationRef[]` (id, translatorName, language, body, isDefault). Default-перевод первым, затем по created_at ASC. Bulk-load в `GET /topics/{id}/graph` через `NodeTranslationRepository.findByNodeIds` (один SQL на весь граф, не N+1). `CreateNodeRequest`/`UpdateNodeRequest` потеряли translation/translationLang (переводы добавляются после создания узла через child endpoints). 5 новых endpoint под `/api/v1/nodes`: `POST /{nodeId}/translations` (CreateNodeTranslationRequest{translatorName?, language, body, isDefault?}, 201 + Location), `GET /{nodeId}/translations` (NodeTranslationRef[]), `PATCH /translations/{id}` (UpdateNodeTranslationRequest{translatorName?, body?}), `POST /translations/{id}/default` (atomic switch default), `DELETE /translations/{id}` (204, auto-promote oldest при удалении default). Permission: canWriteTopic на mutating, canReadTopic на GET. Спецсемантика: первый перевод узла всегда `isDefault=true` независимо от payload (узел не может быть без default-перевода). Новые ошибки: 404 `node-translation-not-found`, 409 `node-translation-duplicate` (translator+language существует, properties nodeId/translatorName/language). 19 IT для service + 10 IT для controller. Frontend: NodeCard читает `data.translations[]`, dropdown показывается только при >1 переводов, single - просто label с именем переводчика | Backlog «Translator attribution» закрыт. Учёные часто переводят аяты/хадисы по-разному (Кулиев vs Sahih International vs Османов) - один MVP перевод (миграция 44) не покрывал реальный use-case. Memory `feedback_no_prod_no_backward_compat` - ломаем single-translation 1:1 contract без backward compat: миграция переносит existing данные и frontend регенерирует types. Без ADR - чистое расширение domain (1:1 → 1:N) без architectural alternatives |
| 2026-05-18 | v1 | Bilingual карточки узлов. Миграция 44 ALTER `nodes` добавляет 3 nullable колонки: `translation` (text), `translation_lang` varchar(5) CHECK IN ('ru','en'), `original_lang` varchar(5) CHECK IN ('ar','ru','en'). `NodeResponse` расширен полями `translation` / `translationLang` / `originalLang` (все nullable). `CreateNodeRequest` принимает их как optional с @Pattern whitelist валидацией. `UpdateNodeRequest`: те же поля + семантика "пустая строка = очистить, null/отсутствие = не менять". Двойная валидация: @Pattern в DTO (быстрый 400) + NodeService.validateBilingual (защита от прямого вызова сервиса). Правило: translation NOT NULL → translationLang обязателен. NodeService updateContent получил перегрузку с boxed-семантикой через sentinel `NodeService.NoChange.INSTANCE` чтобы отличить "не пришло в PATCH" от "пришло null (очистить)". Revision НЕ пишется для translation/lang (это metadata, не version-controlled содержимое). Whitelist `UserPreferenceService.ALLOWED_VALUES` расширен ключом `bilingualMode` ('original'\|'translation'\|'both') - frontend persist'ит режим отображения через тот же `/api/v1/preferences/bilingualMode`. originalLang null → frontend auto-detect через hasArabicScript(content) | Backlog «Bilingual карточки - двуязычный режим узла» закрыт. MVP - один перевод на узел. Multi-translation (M:N table с разными переводчиками одного аята: Кулиев, Sahih International, Османов) - в backlog Translator attribution. Без ADR - чистое расширение domain без architectural alternatives |
| 2026-05-18 | v1 | Multi-grading хадисов. Миграция 43 - новая таблица `hadith_grades (id UUID PK, source_id FK CASCADE, scholar_id FK RESTRICT на authorities, grade VARCHAR(20) CHECK SAHIH/HASAN/DAIF/MAUDU, grade_citation VARCHAR(500), comment text, created_at, created_by FK users, UNIQUE(source_id, scholar_id))` + 2 индекса. 4 новых REST endpoint под `/api/v1/sources`: `POST /{sourceId}/grades` (CreateHadithGradeRequest{scholarId, grade, gradeCitation?, comment?} → 201 HadithGradeResponse + Location), `GET /{sourceId}/grades` (HadithGradeResponse[] с denormalized scholar info через JOIN на authorities - один SQL без N+1), `PATCH /grades/{gradeId}` (UpdateHadithGradeRequest, author либо ADMIN), `DELETE /grades/{gradeId}` (204, author либо ADMIN). Новый enum `HadithGradeValue` отдельно от legacy `Reliability` - добавлен `MAUDU` («выдуманный») как четвёртая категория для multi-grading. Существующее `sources.reliability` single-value не трогаем (legacy primary). Новые ошибки: 400 `invalid-hadith-grade` (source не HADITH либо grade null), 404 `hadith-grade-not-found`, 409 `hadith-grade-duplicate` (тот же scholar уже оценил, properties sourceId/scholarId), 403 `forbidden-hadith-grade-write` (не автор/не ADMIN, properties gradeId/userId) | Backlog «Multi-grading хадисов» закрыт. Учёные часто расходятся в оценке - один хадис может быть SAHIH по Бухари, HASAN по Тирмизи. M:N модель source × scholar с unique constraint защищает от дублей. Без ADR - чистое расширение domain без architectural alternatives |
| 2026-05-18 | v1 | Settings screen - user preferences API. Миграция 42 - новая таблица `user_preferences (id UUID PK, user_id FK CASCADE, key VARCHAR(100), value jsonb, updated_at, UNIQUE(user_id, key))` + индекс. 4 новых endpoint под `/api/v1/preferences`: `GET` возвращает `Map<String, Object>` всех текущих prefs user'а (десериализация jsonb через ObjectMapper); `PUT` принимает Map для bulk update (валидация всех ключей до записи, @Transactional rollback на ошибку); `PUT /{key}` принимает конверт `{value: ...}` для одного ключа (boolean/string/number); `DELETE /{key}` удаляет ключ - revert на дефолт. Whitelisted keys валидируются в `UserPreferenceService.ALLOWED_VALUES`: `locale` (ru/ar/en), `arabicFont` (naskh/kufi/tahoma), `textSize` (small/medium/large/xl), `theme` (system/light/dark) - enum string; `hideTashkeelByDefault`, `transliteration` - boolean. Невалидный ключ или значение → 400 `illegal-argument`. Все endpoints под current user через `@CurrentUser UUID userId` - изоляция читает/пишет только свои prefs. Upsert через `INSERT ON CONFLICT (user_id, key) DO UPDATE` - повторная запись обновляет value + updated_at | Backlog «Settings screen» закрыт. JSONB store для гибкости (расширение whitelist - только правка кода сервиса, новая миграция не нужна). LocalStorage cache на фронте для FOUC prevention, backend wins на login. ADR не пишем - решение очевидное (key-value table + whitelist), без alternatives to compare |
| 2026-05-18 | v1 | Inline citations `[N]` в тексте узла. `NodeResponse` расширен полем `inlineCitations: InlineCitationRef[]` (после `userVote`). Новый schema `InlineCitationRef{ordinal: int 1-based, nodeSourceId, sourceId, sourceType, title, citation, quote, reliability}`. Bulk-load в GET `/topics/{id}/graph` через `NodeSourceRepository.findInlineCitationsForNodes(nodeIds)` - один SQL на весь граф (JOIN sources + lib_books, ORDER BY node_id, created_at ASC). Mutating endpoints на узлах (POST `/nodes`, PATCH `/nodes/{id}`, z-order) подгружают через `findInlineCitationsForNode(nodeId)` точечно. Title fallback chain: `book.title → source.title`. `reliability` отдаётся только для HADITH. Подход A (implicit ordinal): фронт парсит `[1]`, `[2]` в `content` и резолвит по `ordinal` совпадающему с порядком node_sources.created_at ASC. Без миграции БД - feature чисто на запросном уровне. Backward-compat: для узлов без node_sources массив пустой | Backlog «Inline citations - формат [1] в тексте с popover» закрыт. Подход implicit ordinal выбран как MVP (простой, достаточно для начала); если возникнут reorder issues - переход на mixed `[#sourceId]` без breaking change |
| 2026-05-18 | v1 | Dung's argumentation framework opt-in (ADR-044, Этап 6). Миграция 41 ALTER `topics` добавляет колонку `status_algorithm VARCHAR(20) NOT NULL DEFAULT 'MVP'` + CHECK constraint `IN ('MVP','DUNG_GROUNDED')`. `TopicResponse` расширен полем `statusAlgorithm: MVP\|DUNG_GROUNDED`. Новый endpoint `PATCH /api/v1/topics/{id}/status-algorithm` (`UpdateTopicStatusAlgorithmRequest{algorithm}`, owner only, 200 OK + recalc side effect) - триггерит пересчёт всех узлов под новым алгоритмом в той же транзакции. No-op для same value (не пишет audit, не recalc). 400 для невалидного enum через Pattern в DTO; 403 `forbidden-topic-write` для не-owner / не-ADMIN; 404 для unknown topic. Audit как UPDATE action с FieldDiff statusAlgorithm {old, new}. DUNG_GROUNDED игнорирует SUPPORTS/QUALIFIES/RESPONDS_TO (только attack-edges REFUTES + INVALIDATES), mapping IN→STANDING/OUT→REFUTED/UNDEC→DISPUTED. UNVERIFIED не используется в Dung'е. **Frontend UI toggle отложен в backlog** - сейчас доступен только через curl. Дефолт MVP сохраняет existing behavior, нет breaking change | ADR-044: opt-in переключение через колонку. Rejected: replace MVP полностью (breaking), preferred/stable semantics (multiple extensions, сложнее mapping), bipolar argumentation (over-engineering MVP). Backlog Этап 6 «Реализация Dung's framework» закрыт |
| 2026-05-18 | v1 | Audit log per-entity (Этап 22.d, ADR-043 Amendment 3). Миграция 39 - новая таблица `audit_log (id, entity_type, entity_id, parent_entity_type, parent_entity_id, action, actor_user_id REFERENCES users, changes jsonb, metadata jsonb, created_at)` + 4 индекса. 4 новых REST endpoint под `/api/v1/audit/*`: `GET /topics/{id}` (owner+EDITOR через assertCanWrite), `GET /books/{id}` (owner+EDITOR через assertCanWriteBook), `GET /me` (любой authenticated, свои actions), `GET /admin?entityType=&actorId=&dateFrom=&dateTo=` (ADMIN только). Все возвращают `PagedResponse<AuditLogResponse>` сортировка `created_at DESC`. Новый DTO `AuditLogResponse{id, entityType, entityId, parentEntityType, parentEntityId, action, actorUserId, actorUsername, changes (raw json string), createdAt}` - username bulk-load JOIN (не N+1). Audit пишется synchronous в той же транзакции что и mutation через `AuditLogService.logCreate/Update/Delete/VisibilityChange/MemberAdd/MemberRemove/MemberRoleChange` из integration в TopicService/NodeService/EdgeService/BookService/QuestionService/AnswerService/TopicMemberService/BookMemberService. Format `changes` зависит от action: CREATE/DELETE `{created\|deleted: snapshot}`, UPDATE `{field: {old, new}}`, VISIBILITY_CHANGE `{visibility: {old, new}}`, MEMBER_* `{userId, role: scalar или {old, new}}`. Новые ошибки: `403 forbidden-admin-only` (AdminOnlyException) - не ADMIN | ADR-043 Amendment 3: закрытие долга «audit log отложен» из Amendment 2. Event-sourcing lite - 1 row на каждую mutation для compliance + debugging. Synchronous в той же transaction - rollback main flow откатит audit. Manual logging (не Spring AOP) - явный контроль snapshot/diff'а. Admin UI отложен до 22.e/backlog |
| 2026-05-18 | v1 | Голосование за вес аргументов. Миграция 38 - новая таблица `node_votes (id UUID PK, node_id FK CASCADE, user_id FK CASCADE, weight SMALLINT CHECK IN (-1,1), voted_at TIMESTAMPTZ, UNIQUE(node_id, user_id))` + 2 индекса. 3 новых REST endpoint под `/api/v1/nodes/{id}`: `POST /vote` (CreateNodeVoteRequest{weight: -1\|+1} → 201 NodeVoteStatsResponse{nodeId, upvotes, downvotes, score, userVote}, upsert через ON CONFLICT), `DELETE /vote` (204, идемпотентен), `GET /votes` (NodeVoteStatsResponse). `NodeResponse` расширен 4 полями: `voteUpvotes`/`voteDownvotes`/`voteScore` (int), `userVote` (Integer nullable - голос вызывающего user'а). В `GET /api/v1/topics/{id}/graph` поля заполняются bulk-load из NodeVoteRepository (2 SQL на весь граф - не N+1). Новая ошибка `400 invalid-vote` (weight ∉ {-1,+1}). Permission: vote требует canReadTopic (видишь узел - можешь vote, не write-access, голос это reaction). Голоса НЕ влияют на StatusCalculation - сигнал силы аргумента ортогональный Dung-style status. MVP 3-point scale; 5-point {-2..+2} в backlog | Backlog «Голосование за вес аргументов» закрыт. Пользователи могут усиливать или ослаблять `EVIDENCE`/`ARGUMENT` узлы. Frontend - компактный VoteWidget в NodeCard footer (только для ARGUMENT/EVIDENCE - QUESTION/CLAIM не голосуются), upvote/downvote toggle с optimistic UI |
| 2026-05-18 | v1 | Этап 22.c - RBAC permissions per-entity для library books + Q&A guards (ADR-043 Amendment). Миграция 37 ALTER `lib_books` добавляет колонку `visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC'` (CHECK PRIVATE/SHARED/PUBLIC, в отличие от topics с default PRIVATE - books default PUBLIC для open library); 2 индекса (`visibility`, `created_by+visibility`); новая таблица `lib_book_members` (структура mirror `topic_members`: id, book_id FK CASCADE, user_id FK CASCADE, role CHECK MEMBER/EDITOR, added_at, added_by, UNIQUE book+user). **Breaking semantic change для library books**: GET endpoints у `/library/books` теперь требуют X-User-Id (visibility-фильтр в SQL: PUBLIC OR created_by=? OR (SHARED AND EXISTS lib_book_members)); GET `/library/books/{id}` возвращает 403 `forbidden-book-access` для PRIVATE чужого user'а; DELETE/PATCH книги возвращает 403 `forbidden-book-write` если не owner/EDITOR/ADMIN. `BookResponse`/`BookSummaryResponse`/`BookDetailResponse` расширены полем `visibility: PRIVATE\|SHARED\|PUBLIC`. **POST `/library/books` (REST) устанавливает visibility=PUBLIC по умолчанию**, **POST `/library/imports/file` (PDF upload) устанавливает PRIVATE** (user-uploads приватны). 5 новых book endpoint: `PATCH /api/v1/library/books/{id}/visibility` (UpdateBookVisibilityRequest, owner only); `POST /api/v1/library/books/{id}/members` (AddBookMemberRequest{userId, role}, owner only, 201); `GET /api/v1/library/books/{id}/members` (BookMemberResponse[], requires read access); `PATCH /api/v1/library/books/{id}/members/{memberId}` (UpdateBookMemberRequest{role}, owner only); `DELETE /api/v1/library/books/{id}/members/{memberId}` (owner или self-leave). **Q&A guards**: PATCH/DELETE `/api/v1/questions/{id}` и PATCH/DELETE `/api/v1/answers/{id}` теперь требуют X-User-Id (author check) и возвращают 403 `forbidden-question-write` / `forbidden-answer-write` если actor не автор и не ADMIN. Visibility-модель для questions/answers НЕ добавляется (open discussion semantics). Новые ошибки: `403 forbidden-book-access`/`forbidden-book-write` (с bookId/userId в properties), `404 book-member-not-found`, `403 forbidden-question-write`/`forbidden-answer-write` | ADR-043 Amendment: extend visibility/members pattern на books (тот же 3-уровневый model с PUBLIC-default), Q&A через author guards без visibility model. Rejected: default PRIVATE для books (shamela ETL ожидает massive batch видимым сразу), shared `topic_members` для books (нарушает separation), полный visibility для questions (over-engineering для public discussion). Audit log отложен в 22.d |
| 2026-05-18 | v1 | Pagination + filters для всех GET-list endpoints. **Breaking change**: `GET /api/v1/sources`, `/authorities`, `/topics`, `/library/books`, `/questions` теперь возвращают `PagedResponse<T>{items, page, size, totalElements, totalPages, hasNext, hasPrev}` вместо raw array. Default `page=0&size=20`, max `size=100` (значения сверху clamps до 100). Новые фильтры: **sources** `?type=` (whitelist QURAN/HADITH/BOOK/ARTICLE/URL) + `?reliability=` (whitelist SAHIH/HASAN/DAIF, допустим только при type=HADITH иначе 400 illegal-argument); **authorities** `?era=` (свободный текст exact match); **topics** `?visibility=` (whitelist PRIVATE/SHARED/PUBLIC внутри set'а уже видимых ADR-043); **library/books** `?authorityId=` + `?publisherId=` (UUID FK фильтры); **questions** - `?status=` уже было, добавлена только pagination. Helper'ы `PagedResponse<T>.of()` + `PageRequest.from()` в `web.dto`. Repository паттерн: `findPage(...) + countFiltered(...)` с общим `appendFilters()` helper для одного источника истины WHERE clause. Сортировка единая по умолчанию `created_at DESC` (новые сверху) кроме authorities (`name ASC` для исторического порядка справочника). Старый `findAll` сохранён где используется internal callers (TopicImportService, shamela ETL). Существующие 11 list-related IT обновлены на `$.items`, добавлено 14 новых IT (pagination, filters, invalid combos). Total backend tests 701/701 pass | Backlog task созрел - справочники растут. Hardcoded array выдавал бы все sources/authorities на каждом GET. Memory `feedback_no_prod_no_backward_compat` - ломаем raw-array contract смело, frontend обновляем в той же сессии (минимально 1 page как smoke, остальные в backlog) |
| 2026-05-17 | v1 | Этап 22 - RBAC permissions per-entity (ADR-043). Миграция 36 ALTER `topics` добавляет колонку `visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE'` (CHECK PRIVATE/SHARED/PUBLIC) + 2 индекса; новая таблица `topic_members` (id, topic_id FK CASCADE, user_id FK CASCADE, role CHECK MEMBER/EDITOR, added_at, added_by, UNIQUE topic+user). **Breaking semantic change**: GET endpoints у topics теперь требуют X-User-Id даже на read - visibility check возвращает 403 `forbidden-topic-access` для приватных тем чужого user'а; список `GET /api/v1/topics` фильтруется по UNION (own + shared-member + public). DELETE темы возвращает 403 `forbidden-topic-write` если не owner/ADMIN (раньше любой мог удалять). `TopicResponse` расширен полем `visibility: PRIVATE|SHARED|PUBLIC`. `CreateTopicRequest` принимает опциональное `visibility` (default PRIVATE). 5 новых endpoint: `PATCH /api/v1/topics/{id}/visibility` (UpdateTopicVisibilityRequest, owner only); `POST /api/v1/topics/{id}/members` (AddTopicMemberRequest{userId, role}, owner only, 201); `GET /api/v1/topics/{id}/members` (TopicMemberResponse[], requires read access); `PATCH /api/v1/topics/{id}/members/{memberId}` (UpdateTopicMemberRequest{role}, owner only); `DELETE /api/v1/topics/{id}/members/{memberId}` (owner или self-leave). Новые ошибки: `403 forbidden-topic-access`, `403 forbidden-topic-write` (с topicId/userId в properties), `404 topic-member-not-found`. ADMIN role (из ADR-040) bypass всех visibility checks. NodeController/EdgeController endpoints DELETE/PATCH/GET тоже требуют @CurrentUser для permission check на parent topic | ADR-043: hybrid visibility-модель (3 уровня) + topic_members M:N для co-editing. Rejected: полный RBAC (over-engineering), org/team ownership (нет user-base), per-action permissions (избыточная гранулярность). MVP scope - только topics; library books / Q&A questions добавятся отдельной миграцией если понадобится. Audit log отложен |
| 2026-05-17 | v1 | Этап 17.e - AI editing pass backend (ADR-042). Миграция 35 ALTER `lib_pages` добавляет 3 nullable колонки: `ai_edit_status` (CHECK PENDING/PROCESSING/DONE/FAILED) + `ai_edit_started_at`/`ai_edit_completed_at` + partial index по status WHERE NOT NULL. **2 новых endpoint'a**: `POST /api/v1/library/pages/{pageId}/ai-edit` (триггер async через Anthropic Claude, returns 202 `AiEditJobResponse{pageId, status, startedAt, completedAt, hasTextContent}`); `GET /api/v1/library/pages/{pageId}/ai-edit` (polling). Pre-flight check `AnthropicClient.isEnabled()` - если `ANTHROPIC_API_KEY=disabled` (default) → 503 `ai-edit-not-configured` синхронно вместо background FAILED. Успешный AI edit пишет ProseMirror JSON в существующую `lib_pages.formatted_content` (миграция 33). Новые ошибки: 503 `ai-edit-not-configured` (config), 502/503 `anthropic-api-error` (upstream Anthropic non-2xx либо IO). AiEditService через `@Async("aiEditTaskExecutor")` (core=2, max=4, queue=50). Resilience4j Retry instance `anthropicApi` (3 attempts, exponential backoff). Prompt template в `resources/prompts/ai-edit-tahqiq.txt` - few-shot examples + правила распознавания (hadith/ayah/heading/footnote/colorHighlight). Frontend UI кнопка отложена | ADR-042: Anthropic Claude single-provider MVP. Rejected: OpenAI (слабее arabic), Gemini (no JSON guarantee + lock-in), local LLM (heavy GPU), HF API (rate limits), Anthropic Java SDK (heavy dep). Triggers revisit: cost/quality/privacy/availability |
| 2026-05-17 | v1 | Этап 17.a-c - OCR pipeline backend (ADR-041). Миграция 34 ALTER `lib_pages` добавляет 6 nullable колонок: `image_bucket`/`image_storage_key`/`image_uploaded_at` (pointer на скан в MinIO bucket `library-page-images`) + `ocr_status` (CHECK constraint PENDING/PROCESSING/DONE/FAILED) + `ocr_started_at`/`ocr_completed_at`. **3 новых endpoint'a**: `POST /api/v1/library/books/{bookId}/pages` multipart (file image/jpeg|png|webp|tiff + pageNumber query, до 20MB, возвращает PageResponse 200, либо создаёт placeholder Page либо обновляет existing); `POST /api/v1/library/pages/{pageId}/ocr` (триггер async Tesseract OCR, returns 202 OcrJobResponse{pageId, status, startedAt, completedAt, hasImage}); `GET /api/v1/library/pages/{pageId}/ocr` (polling status). **3 endpoint'a для ImageRegion** (17.c): `POST /api/v1/library/pages/{pageId}/regions` (body CreateImageRegionRequest{x,y,width,height,extractedText?} normalized 0..1, 201 + Location), `GET /api/v1/library/pages/{pageId}/regions` (list sorted by created_at), `DELETE /api/v1/library/pages/regions/{regionId}` (204, update намеренно нет - regions immutable). `PageResponse` расширен 6 image/OCR полями (`imageBucket`/`imageStorageKey`/`imageUploadedAt`/`ocrStatus`/`ocrStartedAt`/`ocrCompletedAt`). Новые ошибки: 422 `page-image-error` (empty file / wrong MIME / zero pageNumber), 415 `unsupported-media-type` (для page image). OcrService через Tess4j 5.13.0 + @Async с dedicated `ocrTaskExecutor` (core=2, max=4). Tesseract сам - system dependency (`apt install tesseract-ocr tesseract-ocr-ara`), docs в backend/CLAUDE.md | ADR-041: Tess4j (Tesseract Java wrapper) выбран как OCR engine - free, offline, supports ara/rus/eng, минимальная Java integration. Rejected: GCV (paid + cloud), PaddleOCR (Python-only), Tika (тонкая обёртка над Tess4j). Triggers revisit: PaddleOCR microservice если quality arabic < 70% на manuscripts |
| 2026-05-17 | v1 | Этап 17.0 - Tiptap rich text editor MVP (ADR-039). Новый endpoint `PATCH /api/v1/library/pages/{id}/formatted-content` принимает `UpdateFormattedContentRequest{formattedContent: JsonNode}` и сохраняет ProseMirror JSON в новой `lib_pages.formatted_content jsonb NULL` колонке (миграция 33). Backend не валидирует ProseMirror schema - принимает любой валидный JSON (валидация на фронте через Tiptap extensions). `PageResponse` расширен полем `formattedContent: JsonNode \| null`. Backward compat: NULL для legacy Shamela/PDFBox страниц → фронт оборачивает `textContent` в minimal paragraph-doc через `wrapPlainTextAsDoc`. `text_content` не трогается (FTS + fallback). Требует X-User-Id/JWT auth | ADR-039: Tiptap (на ProseMirror) выбран как rich text editor платформы. Первый custom extension - HadithBox с source/grade attrs. Подготовка к Этапу 17 OCR pipeline (структурированное хранение AI editing output) |
| 2026-05-17 | v1 | Этап 21.a Spring Security + JWT (ADR-040). Новый namespace `/api/v1/auth/*` с 5 endpoints: `POST /register` (RegisterRequest{email/username/password}, validation: email/3..50 ASCII username/8..100 password), `POST /login` (LoginRequest{email/password}), `POST /refresh` (CookieValue refresh_token, no-rotation MVP), `POST /logout` (clear cookie), `GET /me` (требует Bearer). Response DTO `AuthResponse{accessToken, accessTokenExpiresAt, user{id,username,email,role}}` + Set-Cookie refresh_token (HttpOnly+Secure+SameSite=Strict+Path=/+Max-Age=604800). Access TTL 15мин, refresh TTL 7д, HS256 signature через `auth.jwt.secret` (env AUTH_JWT_SECRET в prod). Новые error types: `401 invalid-credentials` (login fail или disabled), `401 invalid-token` (JWT тампер/expired/невалидный), `401 unauthorized` (no auth on protected endpoint), `409 email-already-taken`, `409 username-already-taken`, `404 user-not-found`. **Breaking semantic change**: ВСЕ mutating endpoints теперь требуют либо `Authorization: Bearer <jwt>`, либо (в dev/local/test profile) X-User-Id fallback. Запрос без обоих → 401 (раньше 400 `missing-user-header`). Existing IT обновлены: 4 теста с `missing-user-header` → 401. `GET /api/**` в dev/local/test profile остаётся permitAll (transitional до Этапа 21.b). Migration 32 `users` ALTER + password_hash/role/enabled/updated_at. OpenAPI X-User-Id header теперь required=false (Bearer JWT - основной путь) | ADR-040 JWT-based auth. Этап 21.a backend foundation, Этап 21.b frontend login UI следующей сессией |
| 2026-05-20 | v1 | Vision 49d Phase 2 view tracking endpoints: `POST /api/v1/topics/{id}/views`, `POST /api/v1/questions/{id}/views`, `POST /api/v1/library/books/{id}/views`. Atomic `UPDATE {table} SET view_count = view_count + 1 WHERE id = ?`. Anonymous-allowed (guest views тоже count). Idempotent (no-op для deleted entity). Migration 51 добавила view_count BIGINT NOT NULL DEFAULT 0 + index DESC во все 3 tables. Anti-spam (1 view per session) defer в Phase 2.b - in-memory Set либо Redis | Vision 49d 2.1 Phase 2: denormalized counter для popularity ranking O(log N) sorting. Phase 1 ?sort=popular пока на computed subquery, переключение на view_count column после data накопится |
| 2026-05-20 | v1 | Phase A.7 admin user listing: `GET /api/v1/users?role=&q=&page=&size=` (ADMIN-only, 403 forbidden-admin-only для non-admin). Filters: role whitelist (USER/STUDENT/SCHOLAR/ADMIN), q substring username OR email (case-insensitive). Returns `PagedResponse<UserResponse>`. `UserRepository.findPage/countFiltered` standard pattern (см. backend/CLAUDE.md Pagination). 11/11 UserControllerIT pass (7 existing + 4 new list endpoint coverage) | Vision 49d Phase A.7 - admin user management UI page. Поддерживает admin manual elevation USER → STUDENT/SCHOLAR через UI |
| 2026-05-20 | v1 | Sort параметр добавлен к 3 list endpoints (Vision 49d Section 2.1 Phase 1): `GET /api/v1/topics?sort=`, `GET /api/v1/questions?sort=`, `GET /api/v1/library/books?sort=`. Whitelist: "recent" (default, backward-compat), "popular" (Topics: node_count DESC; Q&A: COUNT(answers) DESC; Books: citation_count = sum 3 параллельных иерархий цитат DESC), "alphabetical" (title ASC). Invalid sort → silent fallback "recent". Phase 1 use computed subqueries без денормализованных counters (Phase 2 заменит на indexed columns). Все existing IT preserved (73 tests pass across 3 controllers). Без миграции — pure SQL-уровень | Vision 49d Section 2.1: «должна быть пагинация и рейтинг тем чтобы знали что популярное/нужное». Backward-compat default - "recent" сохраняет existing behavior. Frontend UI dropdown - Phase 1.b отдельным commit'ом |
| 2026-05-20 | v1 | Новые endpoints для personal book collections (Vision 49d Section 2.2): `POST /api/v1/library/collections` (body `{bookId, collectionName?}`, idempotent - повтор возвращает existing entry без 409), `DELETE /api/v1/library/collections/{bookId}?name=` (idempotent - 204 даже если не было), `GET /api/v1/library/collections?name=` (свои entries, optional фильтр), `GET /api/v1/library/collections/names` (уникальные имена коллекций). Default collection name "Избранное" если не передан. DTOs: `AddToCollectionRequest{bookId, collectionName?}`, `CollectionEntryResponse{id, bookId, collectionName, addedAt}`. Все endpoints scoped к `@CurrentUser` - user видит только свои. Migration 50 `user_book_collections` (M:N user × book × collection_name, UNIQUE (user_id, book_id, collection_name)) | Vision 49d Section 2.2: Library становится общим каталогом, пользователи добавляют книги в personal collections (Избранное + тематические "Тафсир", "Хадис"). См. spec `docs/superpowers/specs/2026-05-20-vision-expansion-49d.md` |
| 2026-05-20 | v1 | Новый endpoint `PATCH /api/v1/users/{id}/role` (ADMIN only). Request `ChangeRoleRequest{newRole}` whitelist USER/STUDENT/SCHOLAR/ADMIN. Response 200 `UserResponse{id, username, email, role, enabled, createdAt, updatedAt}`. Errors: 403 `forbidden-admin-only` (non-ADMIN actor), 404 user-not-found, 400 invalid role или admin self-downgrade (lockout protection). Audit log entry через AuditAction.UPDATE с changes `{role: {old, new}}`, entityType="USER". Vision 49d Phase A.4 | Roles spec: admin elevation USER → STUDENT/SCHOLAR/ADMIN. Сама admin elevation - prerequisite для последующих gates на createAnswer (STUDENT+) и createQuestion (STUDENT+). Migration 49 уже расширила CHECK constraint. См. `docs/superpowers/specs/2026-05-20-roles-system-design.md` |
| 2026-05-20 | v1 | `POST /api/v1/sources/{sourceId}/grades` теперь требует **SCHOLAR+** роль (Vision 49d Phase A.3, ADR forthcoming). Non-SCHOLAR (USER/STUDENT) → 403 `forbidden-insufficient-role` с deтails `{currentRole, requiredRole, userId}`. ADMIN bypass через hierarchy. Legacy `HadithGradeService.addGrade(...)` overload без role параметра оставлен для internal callers (ETL/import/seed), production REST идёт через role-aware overload. `MeResponse.role` allowableValues расширен до 4 ролей | Roles A.3: первый applied gate. HadithGradeServiceIT 17/17 + HadithGradeControllerIT 12/12 (3 new tests on role gate). См. roles spec |
| 2026-05-19 | v1 | `EdgeResponse` получил поле `zIndex` (int, default 0). Добавлены 2 endpoint: `POST /api/v1/edges/{id}/z-order/bring-to-front` (z_index = MAX рёбер темы + 1) и `POST /api/v1/edges/{id}/z-order/send-to-back` (MIN - 1). Запросы без тела, ответ - обновлённый EdgeResponse. Permission: `assertCanWrite` (forbidden-topic-access если нет read, forbidden-topic-write если нет write). 404 `edge-not-found`. Не пишет revision и не меняет `updatedAt`. Mirror контракта `POST /nodes/{id}/z-order/*` | Backlog item «Z-index full-stack persistence для рёбер». До этого edge z-order сбрасывался при refetch (локальный zRef counter). Закрывает оставшийся долг после узлового z-order (миграция 40) |
| 2026-05-18 | v1 | `NodeResponse` получил поле `zIndex` (int, default 0). Добавлены 2 endpoint: `POST /api/v1/nodes/{id}/z-order/bring-to-front` (z_index = MAX темы + 1) и `POST /api/v1/nodes/{id}/z-order/send-to-back` (MIN - 1). Запросы без тела, ответ - обновлённый NodeResponse. Permission: `assertCanWrite` (forbidden-topic-access если нет read, forbidden-topic-write если нет write). 404 `node-not-found`. Не пишет revision и не меняет `updatedAt` | Backlog item «Z-index full-stack persistence для узлов». До этого z-order сбрасывался при refetch. Edges остаются с локальным zRef counter - отдельный backlog item, редко критично |
| 2026-05-17 | v1 | Этап 16.h post-review fix - после `POST /api/v1/library/imports/file` книга **сразу** доступна на чтение через существующие `GET /api/v1/library/books/{bookId}/pdf/info` (single-file metadata) и `GET /pdf?fileIndex=0` (streaming). До фикса возвращали 404 `pdf-not-available`. Параметр `language` получил whitelist `ar\|ru\|en` - вне whitelist → 422 `file-import-error` (mirror frontend FileUploadModal). Новых endpoints нет | Critical issue code review Сессии 37: `PdfLinksSourceProvider.supports` проверял `metadata.pdf_links` который `FileImportService` не пишет. Новый `UserUploadProvider` (@Order=50) опрашивает `library_files` по (book_id, source_type=USER_UPLOAD). Контракт language исправляет drift между frontend whitelist и backend acceptance |
| 2026-05-17 | v1 | `POST /api/v1/library/imports/file` расширен 6 опциональными academic полями (`muhaqqiqName`/`publisherName`/`publicationPlaceName`/`editionNumber`/`publishedYearHijri`/`publishedYearGregorian`) с теми же диапазонами что в `CreateBookRequest` (edition 1..99, year 1..9999). Если хотя бы одно заполнено - бэк через 13-args `BookService.createBook` делает `findOrCreate` в `lib_muhaqqiqs`/`lib_publishers`/`lib_publication_places`, иначе legacy 7-args путь без FK. Out-of-range диапазон → 422 `file-import-error` (ручная валидация в controller, Bean Validation для `@RequestParam` в проекте не настроена) | Этап 16.g: закрытие MVP-разрыва 16.b/f. Пользователь больше не должен после upload вторым шагом открывать BookEditModal для добавления тахкика. Mirror паттерна AddSourceModal 20.e |
| 2026-05-17 | v1 | Новый endpoint `POST /api/v1/library/imports/file` (multipart/form-data, до 50MB, только `application/pdf`). Поля: `file` (required), опциональные `title`/`authorityId`/`language`/`description`, header `X-User-Id`. Response 201 - `FileImportResponse{bookId, fileId, pageCount, contentHash, sizeBytes, bucket, storageKey}` + Location header. Создаёт Book (`bookType=BOOK`, `metadata.user_uploaded=true`) + Page[] (по одной на phys-страницу PDF, `pageNumber=pdfPageNumber=i+1`, `textContent` через PDFBox PDFTextStripper) + library_files entry (`sourceType=USER_UPLOAD`). Новые ошибки: 413 `payload-too-large` (Spring multipart limit), 415 `unsupported-media-type`, 422 `file-import-error` | ADR-035: Apache PDFBox 3.0.5 для page-by-page extraction. Этап 16.a-e. Второй способ добавления книг помимо shamela ETL. EPUB отложен - нет UX-кейса |
| 2026-05-16 | v1 | Answer citation endpoints: `POST /api/v1/answers/{id}/citations` (CitationRequest reused, TEXT/PDF/REGION), `GET /api/v1/answers/{id}/sources` (List<AnswerSourceResponse> с 9 LEFT JOIN), `DELETE /api/v1/answers/{id}/sources/{answerSourceId}`. Новый DTO `AnswerSourceResponse{id, answerId, sourceId, quote, context, mode, citation, createdAt}` - mirror QuestionSourceResponse. Migration 31 `answer_sources` (тот же шаблон что migration 28: surrogate UUID PK сразу, positional fields, CHECK constraint один-из-четырёх, FK на answers ON DELETE CASCADE) | ADR-033 итерация 3: параллельная иерархия `answer_sources` рядом с `question_sources` и `node_sources`. 3-е применение паттерна подтверждает что platform pivot (ADR-018) масштабируется - тот же CitationPicker + SourceCard + 9-LEFT-JOIN structured citation reused для третьей сущности без копирования бизнес-логики |
| 2026-05-16 | v1 | `CreateBookRequest` расширен 6 опциональными academic полями (`muhaqqiqName`/`publisherName`/`publicationPlaceName`/`editionNumber`/`publishedYearHijri`/`publishedYearGregorian`) с теми же validation rules что в `UpdateBookRequest` (`@Min/@Max`). Backend `BookService.createBook` перегружен - non-blank `name` → `findOrCreate` в справочнике, blank/null → FK остаётся null. `CreateSourceRequest` расширен опциональным `bookId: UUID` - связывает Source с уже существующей Book (ADR-026), `SourceService` валидирует exists через `404 book-not-found`. Старый legacy путь без `bookId` продолжает работать | Этап 20.e: AddSourceModal manual book entry 2-step flow (POST `/library/books` с academic → POST `/sources` с `bookId`). Соответствует ADR-026 + ADR-028, новых архитектурных решений нет |
| 2026-05-16 | v1 | Answers + accept-answer flow для Q&A. Endpoints: `POST /api/v1/questions/{id}/answers` (CreateAnswerRequest{body}), `GET /api/v1/questions/{id}/answers` (List<AnswerResponse>, accepted первым), `PATCH /api/v1/answers/{id}` (UpdateAnswerRequest{body}), `DELETE /api/v1/answers/{id}`, `POST /api/v1/questions/{id}/accepted-answer/{answerId}` (status -> ANSWERED), `DELETE /api/v1/questions/{id}/accepted-answer` (status -> OPEN). Новый DTO `AnswerResponse{id, questionId, body, authorId, createdAt, updatedAt, accepted}` - `accepted` derived (сравнение с question.acceptedAnswerId). Новый error `404 answer-not-found`. `QuestionResponse` расширен полем `acceptedAnswerId: UUID nullable`. Migration 29 `answers` table + migration 30 `questions.accepted_answer_id` FK ON DELETE SET NULL | ADR-034 Q&A answers + accept flow, Этап 19.c. Single-accepted invariant через nullable FK на question, не boolean per answer. MVP без voting/comments/threading |
| 2026-05-16 | v1 | Q&A citation endpoints: `POST /api/v1/questions/{id}/citations` (CitationRequest reused из ADR-027, TEXT/PDF/REGION mode), `GET /api/v1/questions/{id}/sources` (List<QuestionSourceResponse> с 9 LEFT JOIN), `DELETE /api/v1/questions/sources/{questionSourceId}`. Новый DTO `QuestionSourceResponse{id, questionId, sourceId, quote, context, mode, citation, createdAt}` - без legacySnapshot (нет LEGACY mode UI для questions). Migration 28 `question_sources` (объединяет mig 9+23+25 в одну: surrogate UUID PK сразу, positional fields, CHECK constraint один-из-четырёх) | ADR-033: параллельная иерархия `question_sources` рядом с `node_sources`. Финальная валидация ADR-018 platform pivot - тот же CitationPicker + SourceCard + 9-LEFT-JOIN structured citation reused между двумя entity types без копирования бизнес-логики |
| 2026-05-16 | v1 | Q&A endpoints под `/api/v1/questions/*`: `POST` (CreateQuestionRequest{title, body}), `GET` list (filters `?status=&q=`), `GET /{id}` (QuestionResponse), `PATCH /{id}` (UpdateQuestionRequest, partial update), `DELETE /{id}`. Новый error `404 question-not-found`. `QuestionStatus` enum: OPEN/ANSWERED/CLOSED. Migration 26 `questions` table | ADR-032 Q&A foundation, Этап 19.a. Валидация ADR-018 platform pivot через второе приложение. На MVP без source attach (Этап 19.b) |
| 2026-05-16 | v1 | Добавлены endpoints для Этапа 20.d Admin BookEditModal: `PATCH /api/v1/library/books/{id}` (partial update 6 academic полей через `UpdateBookRequest`, PATCH-семантика: null=no change, ""=clear, non-empty=findOrCreate); `GET /api/v1/library/muhaqqiqs?q=&limit=`, `GET /api/v1/library/publishers?q=&limit=`, `GET /api/v1/library/publication-places?q=&limit=` (autocomplete для UI). Новые DTO: `UpdateBookRequest`, `MuhaqqiqResponse{id,name,fullName}`, `PublisherResponse{id,name}`, `PublicationPlaceResponse{id,name}` | Этап 20.d: UI для ручной правки academic metadata после автоматического parser fill. Search-autocomplete защищает от typo-дублей в справочниках |
| 2026-05-16 | v1 | Добавлен endpoint `POST /api/v1/admin/shamela/backfill-bibliography`. Прогоняет `ShamelaBibliographyParser` (20.c) по всем shamela-sourced книгам, заполняет `muhaqqiq_id`/`publisher_id`/`publication_place_id`/`edition_number`/`published_year_hijri`/`published_year_gregorian` через `findOrCreate` в справочниках. DTO: `BackfillBibliographyResponse{scanned, updated, skipped}`. Тело request пустое, без header'ов | Этап 20.c follow-up: добить existing books импортированные до появления parser'а. Smoke: 3/3 dev-книг получили заполненные FK после первого вызова |
| 2026-05-14 | v1 | `NodeSourceResponse` получил поле `id` (UUID) - surrogate PK для citation link. **Breaking change path** `DELETE /api/v1/nodes/{nodeId}/sources/{sourceId}` → `DELETE /api/v1/nodes/{nodeId}/sources/{nodeSourceId}` (по `id` link'а, не `sourceId`). Теперь возможно несколько citation'ов на ту же пару (node, source) с разными positional context (page/range/pdf bbox) - старый composite PK блокировал | ADR-029: FK variant A - surrogate id для node_sources. Migration 25. Bahs-grade workflow требует множественные cit'ы из одной книги с разных страниц |
| 2026-05-14 | v1 | **Breaking refactor** `NodeSourceResponse`: плоские поля `location`, `pageId`, `rangeStart`, `rangeEnd`, `pdfFileId`, `pdfPageNumber`, `pdfBbox`, `imageRegionId`, `bookId` **удалены** и заменены на nested `citation: CitationResponse` объект с 8 nullable refs (authority/book/muhaqqiq/publisher/publicationPlace/location/pdf/region). Новые DTO: `CitationResponse`, `AuthorityCitationRef`, `BookCitationRef`, `MuhaqqiqRef`, `PublisherRef`, `PublicationPlaceRef`, `LocationRef`, `PdfRef`, `RegionRef`. `AuthorityResponse` расширен полями `fullName` и `deathYearHijri` (nullable). `BookDetailResponse` расширен 6 nullable полями (muhaqqiqId, publisherId, publicationPlaceId, editionNumber, publishedYearHijri, publishedYearGregorian) | ADR-028: academic citation metadata. Бахс-grade citation требует 8 полей сноски (полное имя автора + год смерти, мухаккик, издатель, место, edition, годы). Structured response позволяет фронту рисовать каждое поле в своём блоке (RTL/naskh для арабского) вместо склеенной строки |
| 2026-05-13 | v1 | Новый endpoint `POST /api/v1/nodes/{nodeId}/citations` для positional citation (TEXT/PDF/REGION modes). `NodeSourceResponse` расширен 9 полями: `mode`, `pageId`, `rangeStart`, `rangeEnd`, `pdfFileId`, `pdfPageNumber`, `pdfBbox`, `imageRegionId`, `bookId`. `SourceResponse` расширен полем `bookId` (UUID nullable, FK на lib_books). Новые ошибки: `400 invalid-citation`, `404 book-not-found`/`page-not-found`/`pdf-not-available`/`image-region-not-found`. Существующий `POST /api/v1/nodes/{nodeId}/sources` (legacy freeform) сохраняется для AddSourceModal flow | ADR-026 (Source.bookId FK для one-source-per-book), ADR-027 (positional citation fields в node_sources). Этап 18.f CitationPicker |
| 2026-05-11 | v1 | `PdfFileInfoResponse` расширен полем `isCover` (boolean). Помечает обложку книги - по convention shamela/archive.org обложка лежит в `files[0]` когда metadata содержит `"cover": 1`. Фронт пропускает cover из основного potoka чтения - до фикса всегда грузил `fileIndex=0` (cover, 3 страницы) вместо реального контента | Bug fix: пользователь видел 3 страницы PDF вместо тысяч (cover файл попадал в reader как main content) |
| 2026-05-11 | v1 | **Breaking rename** DTO: `BookSummary` → `BookSummaryResponse`, `PageSummary` → `PageSummaryResponse`, `StagingBookSearchResult` → `StagingBookSearchResponse`. Эндпоинты не меняются. Поля внутри records не меняются. Имена в OpenAPI schema (`components/schemas/*`) обновляются с следующим `npm run generate-api` | ADR-022: DTO suffix convention (`*Response` для всех REST DTO, `*Row` для staging). B-04 audit finding |
| 2026-05-11 | v1 | Добавлены 2 endpoint под `/api/v1/library/books/{id}/pdf/*`: `GET /info` (метаданные PDF файлов книги) и `GET ?fileIndex=N` (streaming PDF с Range header support через `ResourceRegion`). Source-agnostic архитектура - `PdfSourceProvider` interface, реализация `PdfLinksSourceProvider` для shamela (archive.org CDN) + будущие MinIO/IIIF. Новая ошибка: 404 `pdf-not-available`. DTO: `PdfInfoResponse`, `PdfFileInfoResponse` (без filename - защита от обхода нашего endpoint) | ADR-021 source-first, Этап 25.a |
| 2026-05-11 | v1 | `PageSummary` расширен `printedPage` и `part` (nullable TEXT). `PageResponse` расширен теми же полями плюс `pdfPageNumber` (nullable INTEGER). `ChapterResponse` получил `startPageNumber` (миграция 18). Source-first нумерация - electronic версия должна ссылаться на оригинальное издание | ADR-021: source-first архитектура. Миграция 19 (lib_pages новые колонки). Mapper заполняет printedPage/part из shamela_page; pdfPageNumber=NULL до Этапа PDF integration |
| 2026-05-09 | v1 | Добавлены 5 admin endpoints под `/api/v1/admin/shamela/*`: `POST /sync-master` (15.6), `POST /import-book/{id}` (15.6), `POST /map-book/{id}` (15.6), `GET /search?q=&limit=` (15.7), `GET /sync-status` (15.7). DTO: `SyncMasterResponse`, `ImportBookResponse`, `MapBookResponse`, `StagingBookSearchResult`, `SyncStatusResponse`. Новые ошибки: 404 `shamela-not-found`, 502 `shamela-api-error`, 500 `shamela-archive-error`/`shamela-reader-error`/`shamela-import-error`. PDF download / async / bulk endpoints отложены | ADR-020: ETL-импорт shamela, Этапы 15.6 (3 базовых endpoint для mutating операций) + 15.7 (search/status для admin UI) |
| 2026-05-08 | v1 | Добавлены 6 эндпоинтов под `/api/v1/library/*` (POST/GET/DELETE books, GET pages range, GET page detail). DTO: `CreateBookRequest`/`BookResponse`/`BookSummary`/`BookDetailResponse`/`ChapterResponse` (recursive)/`PageSummary`/`PageResponse`/`ImageRegionResponse`. Новые ошибки: 404 `book-not-found`, 404 `page-not-found`, 422 `invalid-book` (зарезервирован). `BookType` enum (`QURAN`/`HADITH_COLLECTION`/`BOOK`/`ARTICLE`/`MANUSCRIPT`) | ADR-019: фундамент платформенной library, Этап 14 |
| 2026-05-08 | v1 | `Source` получил поле `authorityId` (UUID, nullable, FK на `Authority`). `NodeSource`/`AttachSourceRequest`/`NodeSourceResponse` получили поле `location` (string, nullable, до 200 символов). Удалены эндпоинты `POST/GET/DELETE /api/v1/nodes/{id}/authorities`. Удалены DTO `NodeAuthorityResponse` и `AttachAuthorityRequest`, enum `Stance` | ADR-017: единая точка привязки цитаты к узлу. `Authority` теперь приходит к узлу транзитивно через `Source.authorityId` |
| 2026-05-07 | v1 | `TopicResponse` получил `nodeCount` и `edgeCount` (int). На POST/GET-list/GET-one заполняются актуальными значениями через TopicRepository.findAllWithCounts/findByIdWithCounts (один SQL с агрегатными LEFT JOIN-подзапросами) | ADR-016: фронт показывает счётчики на карточках тем без N+1 запросов |
| 2026-05-05 | v1 | Добавлен `PATCH /api/v1/edges/{id}` с `UpdateEdgeRequest` (все поля opt). Финальное состояние валидируется целиком (selfloop / topic boundary / ADR-010), ребро меняется атомарно или 422 | ADR-014: reconnect edges - перетаскивание конца ребра на другой handle. Универсальный partial PATCH вместо sub-resource `/reconnect`, чтобы не плодить API surface |
| 2026-05-05 | v1 | `EdgeResponse` получил `sourceHandle`/`targetHandle` (String, nullable). `CreateEdgeRequest` принимает opt одноимённые поля | этап 9 / F.b: drag-create в RF выбирает конкретные стороны handles, после refetch уважается исходный выбор пользователя |
| 2026-05-05 | v1 | `NodeResponse` получил `posX`/`posY` (Double, nullable). `UpdateNodeRequest` принимает opt `posX`+`posY` без revision | этап 9 Miro UX: drag-and-drop позиции узлов сохраняются на беке |
| 2026-05-04 | v1 | Удалено поле `weight` из `Node`/`CreateNodeRequest`/`NodeResponse` | ADR-011: weight субъективен, не используется в StatusCalculation. Заменим категориальной разметкой после auth (Stage 6) |
| 2026-05-03 | v1 | первая версия: Topics, Nodes, Edges, Graph, Revisions | реализация Этапа 4 |
| 2026-05-03 | v1 | добавлены Sources, Authorities, NodeSources, NodeAuthorities | реализация Этапа 5 |
