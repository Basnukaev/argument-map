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
- `book-not-found` (404)
- `page-not-found` (404)
- `invalid-edge` (422)
- `invalid-source` (422)
- `invalid-book` (422)
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

Response 201, body - полный `BookResponse`:
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
  "updatedAt": "ISO-8601"
}
```
Header `Location: /api/v1/library/books/{id}`.

Ошибки:
- 400 `validation` - blank title, missing bookType, bad metadata JSON
- 400 `missing-user-header` - нет `X-User-Id`
- 404 `authority-not-found` - `authorityId` указан, но запись отсутствует

### GET /api/v1/library/books?q={search}&type={bookType} - список

Query: `q` (optional, ILIKE по title), `type` (optional, фильтр по
`bookType`). Сортировка по `createdAt`.

Response 200 - массив `BookSummary` (без description, metadata,
createdBy, updatedAt - они в детальном GET):
```json
[
  {
    "id": "...",
    "bookType": "QURAN",
    "title": "Священный Коран",
    "authorityId": null,
    "language": "ar",
    "createdAt": "..."
  }
]
```
Pagination не делаем на MVP.

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

Ошибки: 404 `book-not-found`.

### DELETE /api/v1/library/books/{id} - удалить книгу

Каскад через FK на `lib_chapters`/`lib_pages`/`lib_image_regions`.

Response 204 (success), 404 `book-not-found`.

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

Координаты `imageRegions` нормализованы (0..1), не пиксельные.

Ошибки: 404 `page-not-found`.

### Что **не** реализовано в Этапе 14

- POST для chapters/pages/imageRegions - страницы и главы создаются
  только в составе книги через будущие import endpoints (Этапы 15-17)
- PATCH/PUT для books/chapters/pages - вернёмся когда понадобится
- multipart upload для image-сканов - Этап 17

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

### Что **не** реализовано в shamela admin

- `GET /admin/shamela/book/{id}/pdf/{fileIndex}` - lazy PDF download
  через `StreamingResponseBody` + tempfile cleanup. Согласовано с
  ADR-020 «PDF lazy»
- Async POST endpoints через `@Async`/queue - на MVP синхронные
- Bulk endpoints (`POST /map-books?ids=...`) - до решения bulk vs
  lazy после фронт-валидации

## История изменений контракта

| Дата | Версия API | Что изменилось | Причина |
|------|------------|----------------|---------|
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
