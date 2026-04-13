# Дизайн REST API

Этот документ описывает правила дизайна REST API для проекта. Цель —
предсказуемое, удобное API для фронтенда, с первого дня готовое к
эволюции и масштабированию.

## Базовые решения

- **Версионирование в URL:** `/api/v1/...` с первого дня
- **Формат данных:** JSON в запросах и ответах
- **Именование полей в JSON:** `camelCase` (настраивается в Jackson)
- **Кодировка:** UTF-8
- **Даты и время:** ISO 8601 с таймзоной (`2026-04-13T10:30:00Z`)
- **Идентификаторы:** UUID v4 как строки
- **Спецификация:** OpenAPI 3.x через `springdoc-openapi`, доступна по
  `/swagger-ui.html`

## Структура URL

### Ресурсы — существительные во множественном числе
```
/api/v1/topics
/api/v1/nodes
/api/v1/edges
/api/v1/sources
/api/v1/authorities
```

### Вложенные ресурсы — для отношений "принадлежности"
```
GET    /api/v1/topics/{topicId}/graph           — весь граф темы
GET    /api/v1/topics/{topicId}/nodes           — узлы темы
POST   /api/v1/nodes/{nodeId}/sources           — привязать источник к узлу
DELETE /api/v1/nodes/{nodeId}/sources/{sourceId}
GET    /api/v1/nodes/{nodeId}/revisions         — история изменений узла
```

Вложенность — максимум 2 уровня. Глубже — выносить в плоские эндпоинты
с параметрами.

### Специальные действия — sub-resource
Если действие не вписывается в CRUD, использовать sub-resource:
```
POST /api/v1/topics/{topicId}/graph/recalculate   — принудительный пересчёт
POST /api/v1/nodes/{nodeId}/weight                — изменить вес
```

Не превращать в RPC-style (`POST /api/v1/recalculateGraph`).

## HTTP-методы и статусы

### Методы
| Метод | Использование | Идемпотентно |
|-------|---------------|--------------|
| GET | Получение ресурса | Да |
| POST | Создание / действие | Нет |
| PUT | Полная замена ресурса | Да |
| PATCH | Частичное обновление | Нет (формально) |
| DELETE | Удаление | Да |

**Для проекта:** использовать `PATCH` для обновлений, не `PUT`. `PUT`
требует передавать все поля, `PATCH` — только изменяемые.

### Статусы ответов

**Успешные (2xx):**
- `200 OK` — успешный `GET`, `PATCH`, `PUT`
- `201 Created` — успешный `POST` создания ресурса. В заголовке `Location`
  возвращать URL созданного ресурса
- `204 No Content` — успешный `DELETE` без тела ответа

**Клиентские ошибки (4xx):**
- `400 Bad Request` — синтаксически некорректный запрос, невалидный JSON,
  ошибка валидации полей DTO
- `401 Unauthorized` — нет аутентификации (когда появится)
- `403 Forbidden` — аутентификация есть, но нет прав
- `404 Not Found` — ресурс не существует
- `409 Conflict` — конфликт состояния (например, удаление узла, на
  который ссылаются другие)
- `422 Unprocessable Entity` — запрос корректен синтаксически, но
  нарушает бизнес-правила (нельзя создать ребро к самому себе)

**Серверные ошибки (5xx):**
- `500 Internal Server Error` — необработанное исключение
- `503 Service Unavailable` — БД недоступна, внешний сервис упал

**Разграничение 400 vs 422:**
- `400`: "Поле `content` не может быть пустым" — нарушение схемы
- `422`: "Нельзя создать ребро от узла к самому себе" — бизнес-правило

## Формат ответов

### Единичный ресурс
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "content": "Мавлид является дозволенным",
  "nodeType": "CLAIM",
  "status": "DISPUTED",
  "weight": 7,
  "topicId": "...",
  "createdBy": {
    "id": "...",
    "username": "abdullah"
  },
  "createdAt": "2026-04-13T10:30:00Z",
  "updatedAt": "2026-04-13T11:45:00Z"
}
```

### Списки с пагинацией
```json
{
  "items": [
    { "id": "...", "title": "..." },
    { "id": "...", "title": "..." }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 157,
    "totalPages": 8
  }
}
```

**Почему не просто массив на верхнем уровне:** чтобы можно было добавить
метаданные (пагинация, фильтры) без breaking change.

### Ошибки — Problem Details (RFC 7807)
Формат, совместимый с `Content-Type: application/problem+json`:
```json
{
  "type": "https://argumentmap.example/errors/node-not-found",
  "title": "Узел не найден",
  "status": 404,
  "detail": "Узел с id=550e8400-e29b-41d4-a716-446655440000 не найден в теме abc123",
  "instance": "/api/v1/nodes/550e8400-e29b-41d4-a716-446655440000"
}
```

Для ошибок валидации — добавлять поле `errors` с массивом:
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

Реализуется через глобальный `@ControllerAdvice`. Spring Boot 3 имеет
встроенную поддержку Problem Details (`ProblemDetail`).

## Пагинация

### Offset-based (для MVP)
```
GET /api/v1/sources?page=0&size=20&sort=title,asc
```

Простая, удобная, но медленная на больших смещениях. Для нашего объёма
данных это ок.

### Cursor-based (для будущего — журналов, лент)
```
GET /api/v1/revisions?cursor=eyJ...&limit=20
```

Курсор — непрозрачная для клиента строка (base64 от `created_at + id`).
Понадобится для `revisions` и подобных append-only данных.

**Решение для проекта:** начинаем с offset-based, переходим на cursor
для конкретных эндпоинтов, когда появится реальная необходимость.

### Лимиты
- `size` по умолчанию: 20
- `size` максимум: 100 (запрос больше — возвращать 400)
- Всегда валидировать `page >= 0` и `size > 0`

## Фильтрация и сортировка

### Фильтры — query-параметры
```
GET /api/v1/nodes?topicId=...&status=STANDING&nodeType=CLAIM
GET /api/v1/sources?type=HADITH&reliability=sahih
```

Простые фильтры — равенство. Для сложных (диапазоны, `OR`, полнотекстовый
поиск) — отдельный эндпоинт `POST /search` с телом-запросом.

### Сортировка
```
GET /api/v1/topics?sort=createdAt,desc
GET /api/v1/nodes?sort=weight,desc&sort=createdAt,asc
```

Валидировать, что поле сортировки — в белом списке. Не пропускать
произвольные поля в SQL — это дыра для инъекций.

### Полнотекстовый поиск
```
GET /api/v1/nodes?q=мавлид
```

`q` — зарезервированный параметр для текстового поиска. Реализация
через Postgres `tsvector` — после MVP.

## Версионирование

### В URL: `/api/v1/...`
Очевидно, видно в логах, тривиально роутить. Минус — "не по REST-пуристски",
нам всё равно.

### Breaking change = новая версия
Что считается breaking:
- Удаление поля из ответа
- Переименование поля
- Изменение типа поля
- Удаление эндпоинта
- Изменение обязательности параметра
- Изменение формата ошибки

Что **не** breaking:
- Добавление нового поля в ответ (если клиент игнорирует неизвестные —
  как должен делать нормальный JSON-парсер)
- Добавление нового эндпоинта
- Добавление необязательного параметра

### Жизненный цикл версий
- Новая версия — `/api/v2/...` запускается параллельно с `/api/v1/...`
- Старая версия помечается deprecated (в OpenAPI + заголовок `Deprecation`
  в ответах)
- Старая версия живёт минимум 6 месяцев после deprecation
- Потом — удаляется

## Валидация

### Вход — Bean Validation на DTO
```java
public record CreateNodeRequest(
    @NotNull UUID topicId,
    @NotNull NodeType nodeType,
    @NotBlank @Size(max = 10000) String content,
    @Min(1) @Max(10) int weight
) {}
```

Валидация автоматически через `@Valid` в контроллере. Ошибки отлавливает
`@ControllerAdvice` и превращает в 400 Problem Details.

### Бизнес-валидация — в сервисе
Всё, что нельзя проверить аннотациями:
- "Нельзя создать ребро от узла к самому себе"
- "Узел может быть корневым только если тип `QUESTION`"
- "Источник типа `HADITH` требует указания сборника в `metadata`"

Бросать доменные исключения → `@ControllerAdvice` → 422.

## DTO vs Domain

### Правило: домен не покидает сервисный слой
- Контроллер принимает `*Request` DTO
- Контроллер вызывает сервис, передавая DTO или примитивы
- Сервис работает с доменными моделями
- Сервис возвращает домен
- Маппер превращает домен в `*Response` DTO
- Контроллер отдаёт DTO

### Типы DTO
- `Create*Request` — тело POST
- `Update*Request` — тело PATCH
- `*Response` — ответ (единичный объект)
- `*Summary` — компактный вариант для списков и вложенных объектов
- `*Detail` — если нужен расширенный вариант

Пример:
```java
public record NodeSummary(UUID id, NodeType nodeType, String contentPreview, NodeStatus status) {}
public record NodeResponse(UUID id, UUID topicId, NodeType nodeType, String content,
                           NodeStatus status, int weight, UserSummary createdBy,
                           Instant createdAt, Instant updatedAt) {}
public record NodeDetail(NodeResponse node, List<EdgeResponse> incomingEdges,
                         List<EdgeResponse> outgoingEdges, List<SourceSummary> sources,
                         List<AuthoritySummary> authorities) {}
```

## Bulk-операции

Когда фронт должен создать много связанных объектов сразу (например,
импортировать граф), отдельный эндпоинт:
```
POST /api/v1/topics/{topicId}/graph:import
```

Двоеточие в пути — условность для non-CRUD операций (Google API style).
Тело — полная структура, ответ — созданные id.

## Идемпотентность

Для критичных создающих запросов — заголовок `Idempotency-Key` (UUID от
клиента). Сервер сохраняет `(idempotencyKey, response)` в кеш на N часов.
Повторный запрос с тем же ключом возвращает сохранённый ответ.

Для MVP — не обязательно. Запланировать в roadmap.

## CORS

Фронт будет на другом origin. Настроить CORS на уровне Spring Security
или через `WebMvcConfigurer`:
- Allowed origins — из конфига (`app.cors.allowed-origins`)
- Allowed methods — `GET, POST, PATCH, DELETE, OPTIONS`
- Allowed headers — `Content-Type, Authorization, Idempotency-Key`

Не использовать `*` в проде.

## Аутентификация (задел на будущее)

API должен быть готов к добавлению аутентификации без breaking changes:
- Все эндпоинты должны уметь читать `Authorization: Bearer ...` заголовок
- `createdBy` должен читаться из токена, не из тела запроса
- Спроектировать сразу с учётом, что "текущий пользователь" — это
  `Principal`, а не параметр запроса

Для MVP — hardcoded `createdBy` или заглушка. Запланировано в roadmap.

## Здоровье и наблюдаемость

- `/actuator/health` — liveness/readiness для Kubernetes
- `/actuator/info` — версия, git commit
- `/actuator/metrics` — метрики Prometheus (добавить позже)
- Все запросы логировать с `requestId` (MDC) для трассировки

## Чек-лист нового эндпоинта

Перед мерджем любого нового эндпоинта проверить:

- [ ] URL следует конвенции (существительные, множественное число, `/api/v1/`)
- [ ] HTTP-метод соответствует семантике
- [ ] Возвращает правильный HTTP-статус (`201` для создания, `204` для DELETE)
- [ ] Запрос описан через DTO с валидацией (`@Valid`)
- [ ] Ответ описан через DTO, а не доменная модель
- [ ] Ошибки возвращаются как Problem Details
- [ ] Для списков есть пагинация
- [ ] Доступен в OpenAPI (`/swagger-ui.html`)
- [ ] Написан интеграционный тест
- [ ] Не ломает существующие эндпоинты
