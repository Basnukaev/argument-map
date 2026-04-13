# Антипаттерны

Список того, что **не делаем** в проекте. Если замечаешь подобное в коде —
исправлять сразу или оформлять как технический долг в `roadmap.md`.

## Java / Spring

### @Transactional на @Scheduled
**Проблема:** создаёт долгоживущие idle transactions, которые блокируют
VACUUM и ломают Liquibase-миграции (особенно GIN-индексы).

**Неправильно:**
```java
@Scheduled(fixedDelay = 60000)
@Transactional
public void recalculateAllStatuses() {
    // ...
}
```

**Правильно:** `@Scheduled` метод вызывает сервисный метод, внутри которого
уже `@Transactional`. Или каждая итерация — отдельная короткая транзакция.
```java
@Scheduled(fixedDelay = 60000)
public void recalculateAllStatuses() {
    topicService.findAllActive().forEach(topicService::recalculateTopicGraph);
}
```

### God Class / God Service
**Проблема:** сервис на 2000 строк, который делает всё.

**Правильно:** разделять по ответственностям. `GraphService` делает обходы,
`StatusCalculationService` — пересчёт, `NodeService` — CRUD узлов.

### Anemic Domain + Fat Service
**Проблема:** доменные модели — просто мешки данных, вся логика в сервисах.
В строго-ORM-проектах это осознанный выбор. В нашем (records + JDBC) —
логика уровня объекта может жить в самом record (через методы).

**Пример:** проверка `canBeDeleted()` на `Node` — ок. Это не делает объект
"умным", но улучшает читаемость.

### Catching Exception / Throwable
**Неправильно:**
```java
try {
    // ...
} catch (Exception e) {
    log.error("Ошибка", e);
    return null;
}
```

**Правильно:** ловить конкретное исключение и обрабатывать осмысленно.
Если нужно поймать всё — это должен быть глобальный `@ControllerAdvice`,
не локальный catch.

### Возврат null вместо Optional или пустой коллекции
**Неправильно:**
```java
public Node findByIdOrNull(UUID id) { ... }
public List<Edge> getEdges(UUID nodeId) {
    if (nodeId == null) return null;
    // ...
}
```

**Правильно:** `Optional<Node>` для единичных значений, `List.of()` или
пустой список для коллекций.

### Optional как поле или параметр
**Неправильно:**
```java
public record Node(UUID id, Optional<String> description) {}
public void update(UUID id, Optional<String> newContent) {}
```

**Правильно:** `Optional` — только возвращаемое значение. Для опциональных
полей — nullable с явной проверкой или перегрузка методов.

### Primitive obsession
**Проблема:** передавать везде `String id` или `UUID id` без контекста,
когда можно выделить тип.

Для MVP — не обязательно. Но когда появятся разные типы id (`NodeId`,
`TopicId`, `EdgeId`) и компилятор перестанет ловить путаницу — подумать
о value types.

### Long parameter list
**Неправильно:**
```java
public Node createNode(UUID topicId, NodeType type, String content,
                       int weight, UUID createdBy, List<UUID> sourceIds,
                       List<UUID> authorityIds) { ... }
```

**Правильно:** объект-параметр (record) `CreateNodeCommand`.

### Static abuse
- Статические методы только для чистых утилит (`StringUtils.capitalize`)
- Не использовать статику для "удобного" доступа к сервисам
- `ApplicationContext.getBean(...)` — запах, инъекция через конструктор

### Mutable static fields
**Никогда.** Кроме констант (`static final`).

### Field injection
**Неправильно:**
```java
@Autowired
private NodeRepository nodeRepository;
```

**Правильно:** инъекция через конструктор. Для Spring Boot 3+ — даже без
`@Autowired`, работает автоматически для одного конструктора.
```java
private final NodeRepository nodeRepository;

public NodeService(NodeRepository nodeRepository) {
    this.nodeRepository = nodeRepository;
}
```

### Бизнес-логика в контроллере
**Неправильно:** контроллер обращается к репозиторию напрямую, делает
валидацию, пересчёт, маппинг в одном методе.

**Правильно:** контроллер → сервис → репозиторий. Одно направление.

### Возврат доменных моделей из контроллера
**Неправильно:** возвращать `Node` напрямую из REST-эндпоинта.

**Правильно:** `NodeResponse` DTO. Защита от утечки внутренней структуры
и возможность эволюции API независимо от доменной модели.

---

## SQL / PostgreSQL

### SELECT *
**Неправильно:**
```sql
SELECT * FROM nodes WHERE topic_id = ?
```

**Правильно:** перечислять поля явно. Защищает от неожиданностей при
изменении схемы и снижает объём передаваемых данных.
```sql
SELECT id, topic_id, node_type, content, status, weight, created_at, updated_at
FROM nodes WHERE topic_id = ?
```

### N+1 запросы
**Проблема:** загрузить 100 тем, потом для каждой отдельно загрузить узлы —
101 запрос вместо одного.

**Правильно:** один запрос с JOIN или `WHERE topic_id IN (...)`, потом
группировка в Java.

### Отсутствие индексов на внешних ключах
Postgres **не создаёт** индекс автоматически под foreign key. Это нужно
делать явно в миграции. Отсутствие индекса убивает производительность
JOIN'ов и каскадных удалений.

**Правило:** на каждый FK — индекс в той же миграции, где создаётся FK.

### Использование VARCHAR(n) без необходимости
В Postgres `VARCHAR(n)` и `TEXT` одинаковы по производительности. `VARCHAR`
без ограничения нужен только если реально есть бизнес-ограничение
(например, `username VARCHAR(50)`).

**Правило:** по умолчанию использовать `TEXT`, если нет явного лимита.

### TIMESTAMP без TIME ZONE
**Неправильно:** `TIMESTAMP` (без `WITH TIME ZONE`).

**Правильно:** `TIMESTAMPTZ` всегда. В Java на стороне приложения —
`Instant` или `OffsetDateTime`, не `LocalDateTime`.

### BOOLEAN как INT / CHAR
**Неправильно:** `is_active INT DEFAULT 1`

**Правильно:** `is_active BOOLEAN DEFAULT TRUE`. Postgres нативно
поддерживает boolean, нет причин эмулировать.

### Конкатенация строк для запросов (SQL injection)
**Неправильно:**
```java
jdbcTemplate.queryForList("SELECT * FROM nodes WHERE content LIKE '%" + query + "%'");
```

**Правильно:** параметризованные запросы всегда.
```java
jdbcTemplate.queryForList(
    "SELECT ... FROM nodes WHERE content ILIKE ?",
    "%" + query + "%"
);
```

### Хранение enum'ов как VARCHAR без CHECK
**Проблема:** в колонку `node_type VARCHAR` можно записать что угодно,
включая опечатки.

**Правильно:** либо Postgres ENUM тип, либо `CHECK (node_type IN ('QUESTION', 'CLAIM', ...))`.
Для проекта: используем `TEXT` + `CHECK` — проще эволюция (добавить
значение — просто изменить constraint).

### Неявные JOIN'ы
**Неправильно:**
```sql
SELECT n.*, e.*
FROM nodes n, edges e
WHERE n.id = e.from_node_id
```

**Правильно:** явный `JOIN ... ON ...`.

### Soft delete без индекса на `deleted_at`
Если где-то понадобится soft delete — обязательно индекс (частичный) и
фильтр `WHERE deleted_at IS NULL` во всех запросах. Но в этом проекте
по ADR-решению — **только hard delete**, история через `revisions`.

### Миграции, которые нельзя откатить
Liquibase поддерживает `<rollback>` — использовать, где это имеет смысл.
Особенно для ALTER TABLE. DROP — почти всегда невосстановим, писать
комментарий.

### Изменение структуры и данных в одной миграции
**Неправильно:** одна миграция: `ALTER TABLE`, затем `UPDATE ... SET ...`.
Ломается на rollback, плохо версионируется.

**Правильно:** разделить на две: структурная и дата-миграция.

### Long-running транзакции
Транзакция, которая живёт минутами, блокирует VACUUM и может ломать
миграции. Видимо в `pg_stat_activity` как `idle in transaction`.

Причины обычно: `@Transactional` на большом методе, или открытая
транзакция без закрытия.

---

## REST API

### Глаголы в URL
**Неправильно:**
```
POST /api/createNode
GET  /api/getNodeById/{id}
POST /api/deleteNode/{id}
```

**Правильно:**
```
POST   /api/nodes
GET    /api/nodes/{id}
DELETE /api/nodes/{id}
```

Ресурсы — существительные, действия — HTTP-методы.

### 200 OK для ошибок
**Неправильно:** возвращать `200 OK` с телом `{"error": "..."}`. Это
ломает клиентов, мониторинг и стандартный HTTP-toolchain.

**Правильно:** использовать соответствующие коды. Ошибка валидации — 400,
не найдено — 404, конфликт — 409, ошибка сервера — 500.

### Несогласованные имена
**Неправильно:** в одном эндпоинте `created_at`, в другом `createdAt`,
в третьем `createdDate`.

**Правильно:** выбрать конвенцию (для проекта: `camelCase` в JSON) и
держаться её везде. Настроить в Jackson глобально.

### Утечка внутренней структуры
**Неправильно:** возвращать `Node` как есть со всеми внутренними полями
(`createdBy` как UUID, `topicId`, и т.д.), которые клиенту не нужны.

**Правильно:** DTO, который содержит только то, что нужно фронту.
`createdBy` → вложенный `UserSummary {id, username}`.

### Chatty API
**Проблема:** чтобы отобразить граф темы, фронт должен:
1. `GET /topics/{id}` — тема
2. `GET /topics/{id}/nodes` — узлы
3. Для каждого узла `GET /nodes/{id}/sources` — источники
4. Для каждого узла `GET /nodes/{id}/authorities` — авторитеты

Это 1 + N + N*2 запросов.

**Правильно:** эндпоинт `GET /topics/{id}/graph` возвращает всё сразу,
в одной структуре. Для нашего случая это естественно — граф редко
бывает огромным.

### Отсутствие пагинации
**Проблема:** `GET /sources` возвращает 10000 записей. Клиент повисает.

**Правильно:** любой list-эндпоинт поддерживает пагинацию с самого начала.
См. `api-design.md` про cursor-based vs offset pagination.

### Отсутствие версионирования
**Проблема:** API меняется, старые клиенты ломаются.

**Правильно:** `/api/v1/...` с первого дня. Следующая мажорная версия
живёт параллельно: `/api/v2/...`. Без этого `PATCH` в прод становится
страшным.

### Смешение ошибок валидации и бизнес-ошибок
**Проблема:** 400 для всего подряд — и валидации DTO, и "узел нельзя
удалить, так как на него есть ссылки".

**Правильно:** 400 для синтаксических/валидационных, 422 (Unprocessable
Entity) для семантических, 409 для конфликтов, 404 для "не найдено".

### Возврат нестабильных структур
**Проблема:** сегодня `{"data": [...]}`, завтра `{"items": [...]}`,
послезавтра просто `[...]`.

**Правильно:** договориться один раз, см. `api-design.md`.

### Exposing entity IDs как auto-increment integers
**Проблема:** `/nodes/1`, `/nodes/2` — легко перебирать, enumeration attack,
утечка объёма данных.

**Правильно:** UUID v4 везде. У нас уже так по ADR.

### Нет idempotency для POST
**Проблема:** клиент отправил POST, сеть отвалилась, клиент ретраит —
создалось два узла.

**Правильно:** для критичных операций — заголовок `Idempotency-Key`,
сервер кеширует результат по ключу. Для MVP — не обязательно, но
запланировано в roadmap.

### Отсутствие спецификации API
**Проблема:** фронт пишется по устному описанию.

**Правильно:** OpenAPI (springdoc-openapi) генерируется из кода,
доступен по `/swagger-ui.html` и `/v3/api-docs`. Это **обязательно**
с первого дня.
