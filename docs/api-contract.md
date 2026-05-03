# API-контракт

Этот документ - **источник истины** для контракта между бэкендом и фронтендом.
Обе команды (обе части монорепы) ориентируются на этот файл.

## Статус

✅ **v1, Этап 4** - реализованы эндпоинты для тем, узлов, рёбер, графа,
ревизий. Источники и авторитеты появятся после Этапа 5.

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
  "content": "string, 1-10000 символов",
  "weight": 5
}
```
- `weight`: int, 1-10, обязательно

**Ответ (201 Created):**
- Заголовок `Location: /api/v1/nodes/{id}`
- Тело: `NodeResponse` (см. ниже)

**Ошибки:**
- `400` - невалидные поля
- `404` - тема не найдена

#### PATCH /api/v1/nodes/{nodeId}

Обновить содержимое узла. Пишет revision (before/after). Не триггерит
пересчёт статусов (content не входит в алгоритм).

**Заголовки:** `X-User-Id: <uuid>` (обязательно)

**Запрос:**
```json
{
  "content": "новое содержимое"
}
```

**Ответ (200 OK):** обновлённый `NodeResponse`.

**Ошибки:**
- `400` - невалидное содержимое
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
  "rationale": "string, 0-2000 символов, опционально"
}
```

**Ответ (201 Created):**
- Заголовок `Location: /api/v1/edges/{id}`
- Тело: `EdgeResponse`

**Ошибки:**
- `400` - невалидные поля
- `404` - один из узлов не найден
- `422` - бизнес-нарушение:
  - `invalid-edge`: ребро от узла к самому себе
  - `invalid-edge`: узлы из разных тем

#### DELETE /api/v1/edges/{edgeId}

Удалить ребро. Триггерит пересчёт статусов темы.

**Ответ (204 No Content):** без тела.

**Ошибки:**
- `404` - ребро не найдено

### Источники (Sources)

_Появятся после Этапа 5._

### Авторитеты (Authorities)

_Появятся после Этапа 5._

## Общие типы ответов

### TopicResponse
```json
{
  "id": "uuid",
  "title": "string",
  "description": "string|null",
  "rootNodeId": "uuid|null",
  "createdBy": "uuid",
  "createdAt": "iso8601"
}
```

### NodeResponse
```json
{
  "id": "uuid",
  "topicId": "uuid",
  "nodeType": "QUESTION|CLAIM|ARGUMENT|EVIDENCE",
  "content": "string",
  "status": "STANDING|DISPUTED|REFUTED|UNVERIFIED",
  "weight": 5,
  "createdBy": "uuid",
  "createdAt": "iso8601",
  "updatedAt": "iso8601"
}
```

### EdgeResponse
```json
{
  "id": "uuid",
  "fromNodeId": "uuid",
  "toNodeId": "uuid",
  "edgeType": "SUPPORTS|REFUTES|QUALIFIES|INVALIDATES|RESPONDS_TO",
  "rationale": "string|null",
  "createdBy": "uuid",
  "createdAt": "iso8601"
}
```

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
- `invalid-edge` (422)
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
    { "field": "weight", "message": "должно быть от 1 до 10" }
  ]
}
```

## История изменений контракта

| Дата | Версия API | Что изменилось | Причина |
|------|------------|----------------|---------|
| 2026-05-03 | v1 | первая версия: Topics, Nodes, Edges, Graph, Revisions | реализация Этапа 4 |
