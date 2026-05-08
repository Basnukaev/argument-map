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
- **Аутентификация:** временная заглушка через заголовок `X-User-Id`
  (UUID). Полноценная Bearer JWT - на Этапе 6+ (см. ADR-006)

### Заголовок `X-User-Id`

Все мутирующие эндпоинты (`POST`, `PATCH`) требуют заголовок
`X-User-Id: <uuid>`. UUID должен соответствовать существующему
пользователю в таблице `users`.

- Заголовок отсутствует - `400 Bad Request` (тип `missing-user-header`)
- Невалидный UUID - `400 Bad Request`
- UUID валидный, но пользователя нет - `422 Unprocessable Entity`
  (FK-нарушение, тип `data-integrity-violation`)

`GET` и `DELETE` не требуют заголовка.

## Эндпоинты

### Темы (Topics)

#### POST /api/v1/topics

Создать тему с корневым узлом-вопросом.

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Запрос:**
```json
{
  "title": "Мавлид это бид'а?",
  "description": "Разбор аргументов сторон",
  "rootQuestion": "Является ли празднование мавлида нововведением?"
}
```
- `title`: string, 1-200 символов, обязательно
- `description`: string, 0-2000 символов, опционально (`null` допустимо)
- `rootQuestion`: string, 1-10000 символов, обязательно

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
  "createdAt": "2026-05-03T10:00:00Z"
}
```

**Ошибки:**
- `400` - невалидные поля или отсутствует `X-User-Id`
- `422` - `X-User-Id` указывает на несуществующего пользователя

#### GET /api/v1/topics

Список всех тем.

**Ответ (200 OK):** массив `TopicResponse` (см. POST).

#### GET /api/v1/topics/{topicId}

Одна тема по id.

**Ответ (200 OK):** `TopicResponse`.

**Ошибки:**
- `404` - тема не найдена

#### DELETE /api/v1/topics/{topicId}

Удалить тему. Каскадно удаляет узлы, рёбра, ревизии, привязки.

**Ответ (204 No Content):** без тела.

**Ошибки:**
- `404` - тема не найдена

#### GET /api/v1/topics/{topicId}/graph

Получить весь граф темы (узлы + рёбра) в плоской форме - так, как
потребляют graph-библиотеки (React Flow, Cytoscape).

**Ответ (200 OK):**
```json
{
  "topic": { ... TopicResponse ... },
  "nodes": [ { ... NodeResponse ... } ],
  "edges": [ { ... EdgeResponse ... } ]
}
```

**Ошибки:**
- `404` - тема не найдена

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

#### GET /api/v1/nodes/{nodeId}/revisions

История изменений содержимого узла, в хронологическом порядке.

**Ответ (200 OK):** массив `RevisionResponse`.

**Ошибки:**
- `404` - узел не найден

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

Список источников или поиск по названию.

**Параметры:**
- `q` (опционально) - подстрока для поиска по `title` (case-insensitive)

**Ответ (200 OK):** массив `SourceResponse`.

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
  "metadata": { "birth_year": 1263 }
}
```
- `name`: 1-500 символов, обязательно
- `bio`: до 10000 символов, опционально
- `era`: до 100 символов, опционально
- `madhab`: до 100 символов, опционально
- `metadata`: произвольный JSON, опционально

**Ответ (201 Created):**
- Заголовок `Location: /api/v1/authorities/{id}`
- Тело: `AuthorityResponse`

**Ошибки:** `400` - невалидные поля.

#### GET /api/v1/authorities

Список или поиск по имени (`q`).

**Ответ (200 OK):** массив `AuthorityResponse`.

#### GET /api/v1/authorities/{authorityId}

Один авторитет. `404` если не найден (`authority-not-found`).

#### DELETE /api/v1/authorities/{authorityId}

Удаление + каскад привязок. `204` или `404`.

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

#### DELETE /api/v1/nodes/{nodeId}/sources/{sourceId}

Отвязать источник.

**Ответ:** `204 No Content`. `404` - привязка не найдена.

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
  "createdBy": "uuid",
  "createdAt": "iso8601",
  "updatedAt": "iso8601"
}
```
`posX`/`posY` - координаты узла на канвасе графа. `null` для
узлов, которые ещё не перетаскивались (фронт применит автолейаут).

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
  "createdAt": "iso8601"
}
```
`sourceHandle`/`targetHandle` - id точки подключения ребра на
сторонах узлов. `null` для рёбер созданных не через drag-create
(например, через bulk-импорт) - фронт применит auto-routing.

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
  "createdAt": "iso8601"
}
```

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
- `invalid-edge` (422)
- `invalid-source` (422)
- `missing-user-header` (400)
- `data-integrity-violation` (422)
- `validation` (400) - дополнительно поле `errors`
- `illegal-argument` (400)

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

## История изменений контракта

| Дата | Версия API | Что изменилось | Причина |
|------|------------|----------------|---------|
| 2026-05-08 | v1 | `Source` получил поле `authorityId` (UUID, nullable, FK на `Authority`). `NodeSource`/`AttachSourceRequest`/`NodeSourceResponse` получили поле `location` (string, nullable, до 200 символов). Удалены эндпоинты `POST/GET/DELETE /api/v1/nodes/{id}/authorities`. Удалены DTO `NodeAuthorityResponse` и `AttachAuthorityRequest`, enum `Stance` | ADR-017: единая точка привязки цитаты к узлу. `Authority` теперь приходит к узлу транзитивно через `Source.authorityId` |
| 2026-05-07 | v1 | `TopicResponse` получил `nodeCount` и `edgeCount` (int). На POST/GET-list/GET-one заполняются актуальными значениями через TopicRepository.findAllWithCounts/findByIdWithCounts (один SQL с агрегатными LEFT JOIN-подзапросами) | ADR-016: фронт показывает счётчики на карточках тем без N+1 запросов |
| 2026-05-05 | v1 | Добавлен `PATCH /api/v1/edges/{id}` с `UpdateEdgeRequest` (все поля opt). Финальное состояние валидируется целиком (selfloop / topic boundary / ADR-010), ребро меняется атомарно или 422 | ADR-014: reconnect edges - перетаскивание конца ребра на другой handle. Универсальный partial PATCH вместо sub-resource `/reconnect`, чтобы не плодить API surface |
| 2026-05-05 | v1 | `EdgeResponse` получил `sourceHandle`/`targetHandle` (String, nullable). `CreateEdgeRequest` принимает opt одноимённые поля | этап 9 / F.b: drag-create в RF выбирает конкретные стороны handles, после refetch уважается исходный выбор пользователя |
| 2026-05-05 | v1 | `NodeResponse` получил `posX`/`posY` (Double, nullable). `UpdateNodeRequest` принимает opt `posX`+`posY` без revision | этап 9 Miro UX: drag-and-drop позиции узлов сохраняются на беке |
| 2026-05-04 | v1 | Удалено поле `weight` из `Node`/`CreateNodeRequest`/`NodeResponse` | ADR-011: weight субъективен, не используется в StatusCalculation. Заменим категориальной разметкой после auth (Stage 6) |
| 2026-05-03 | v1 | первая версия: Topics, Nodes, Edges, Graph, Revisions | реализация Этапа 4 |
| 2026-05-03 | v1 | добавлены Sources, Authorities, NodeSources, NodeAuthorities | реализация Этапа 5 |
