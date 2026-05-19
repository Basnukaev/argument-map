---
name: new-rest-endpoint
description: >
  Use when adding a new REST endpoint, creating a new API method, scaffolding CRUD for
  a new entity, or adding a controller method with its DTO/Service/Repository chain.
  Triggers on: новый endpoint, добавить API, создать контроллер, scaffold service,
  REST, CRUD, новый маршрут, add controller method, new API route, POST/GET/PATCH/DELETE
  endpoint, «добавь endpoint», «новый REST», «add DTO», «new controller». Always use this
  skill to avoid skipping layers (IT test, api-contract.md update, npm run generate-api)
  — these are the steps most commonly forgotten without an explicit checklist.
---

# New REST Endpoint — Scaffold Chain

Этот skill обеспечивает полный scaffold цепочки: Repository → Service → DTO → Controller →
IT → api-contract.md → frontend regeneration. Пропуск любого слоя создаёт долг, который
обнаруживается позже и дороже исправляется.

**Порядок строго bottom-up:** Repository → Service → DTO → Controller → IT → docs.
Не пиши Controller без Service. Не пиши IT без Controller.

---

## Step 0: Определить тип endpoint'а

Выбери тип по HTTP-методу и семантике:

| Тип | Метод | Паттерн URL | Возвращает |
|-----|-------|-------------|------------|
| GET single | GET | `/api/v1/{resource}/{id}` | `EntityResponse` |
| GET list (paginated) | GET | `/api/v1/{resource}` | `PagedResponse<EntityResponse>` |
| POST create | POST | `/api/v1/{resource}` | `ResponseEntity<EntityResponse>` 201 + Location |
| PATCH update | PATCH | `/api/v1/{resource}/{id}` | `EntityResponse` 200 |
| PATCH sub-action | POST или PATCH | `/api/v1/{resource}/{id}/{action}` | `EntityResponse` 200 |
| DELETE | DELETE | `/api/v1/{resource}/{id}` | `ResponseEntity<Void>` 204 |

> **Нестандартные actions** (например bring-to-front, send-to-back) → `POST
> /{id}/{action}` без тела. Семантика «действие», не «создание» — но POST т.к.
> меняет состояние и не идемпотентен.

---

## Step 1: DTO

### Naming conventions

- `Create{Entity}Request` — для POST (входной DTO)
- `Update{Entity}Request` — для PATCH (поля nullable, без `@NotBlank` т.к. partial)
- `{Entity}Response` — для ответа (исходящий DTO)
- `PagedResponse<{Entity}Response>` — для GET-list (обёртка)
- `{entity}Id` — foreign-key поле (не просто `id`). Своё primary key: `id`

### Bean Validation

POST-request — обязательные поля с `@NotBlank`/`@NotNull`/`@Size`:

```java
public record CreateTopicRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,          // опциональное — без @NotBlank
        @NotBlank @Size(max = 10000) String rootQuestion,
        @Pattern(regexp = "PRIVATE|SHARED|PUBLIC",
                message = "visibility должен быть PRIVATE, SHARED или PUBLIC")
        String visibility                               // enum-строка — @Pattern
) {}
```

PATCH-request — все поля `null`-able, нет `@NotBlank`:

```java
public record UpdateTopicRequest(
        @Size(min = 1, max = 200) String title,        // null = не менять
        @Size(max = 2000) String description           // null = не менять
) {}
```

Response — чистый record без аннотаций:

```java
public record TopicResponse(
        UUID id,
        String title,
        String description,
        UUID rootNodeId,
        UUID createdBy,
        Instant createdAt,
        String visibility,
        String statusAlgorithm,
        int nodeCount,
        int edgeCount
) {}
```

---

## Step 2: Repository

Если нужен новый SQL — добавляй в существующий `*Repository` (или создавай новый).

### Шаблон Repository

```java
@Repository
public class TopicRepository {

    private static final String COLUMNS = "id, title, description, ...";
    private final JdbcTemplate jdbc;

    public TopicRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // GET-single: find* → Optional, get* → бросает исключение
    public Optional<Topic> findById(UUID id) {
        List<Topic> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM topics WHERE id = ?",
                ROW_MAPPER, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    // GET-list: два метода — findPage + countFiltered
    // WHERE clause — через один appendFilters helper (не дублировать!)
    public List<Topic> findPage(String visibility, int limit, int offset) {
        var sql = new StringBuilder("SELECT " + COLUMNS + " FROM topics");
        var params = new ArrayList<>();
        appendFilters(sql, params, visibility);
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return jdbc.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public long countFiltered(String visibility) {
        var sql = new StringBuilder("SELECT COUNT(*) FROM topics");
        var params = new ArrayList<>();
        appendFilters(sql, params, visibility);
        return jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
    }

    // Единственный источник истины для WHERE clause
    private void appendFilters(StringBuilder sql, List<Object> params, String visibility) {
        if (visibility != null) {
            sql.append(" WHERE visibility = ?");
            params.add(visibility);
        }
    }

    private static final RowMapper<Topic> ROW_MAPPER = (rs, rn) -> new Topic(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            // ...
    );
}
```

**Правила:**
- Без JPA/Hibernate. Только `JdbcTemplate` + `RowMapper`
- `COLUMNS` константа — DRY для SELECT
- `findPage` + `countFiltered` с общим `appendFilters` — COUNT и SELECT должны давать
  одинаковый результат при одинаковых фильтрах
- snake_case в SQL, camelCase в Java

---

## Step 3: Service

```java
@Service
public class TopicService {

    private final TopicRepository topicRepository;
    private final AuditLogService auditLogService;   // если mutation

    public TopicService(TopicRepository topicRepository, AuditLogService auditLogService) {
        this.topicRepository = topicRepository;
        this.auditLogService = auditLogService;
    }

    // find* → Optional (не бросает)
    public Optional<Topic> findById(UUID id) {
        return topicRepository.findById(id);
    }

    // get* → бросает NotFoundException
    public Topic getTopic(UUID id) {
        return topicRepository.findById(id)
                .orElseThrow(() -> new TopicNotFoundException(id));
    }

    // Mutation: @Transactional, permission check, audit log
    @Transactional
    public Topic createTopic(String title, String description,
                             String rootQuestion, String visibility, UUID userId) {
        // валидация фильтров / бизнес-правил — здесь, не в Repository
        if (visibility != null && !TopicVisibility.isValid(visibility)) {
            throw new IllegalArgumentException("Невалидное visibility: " + visibility);
        }
        Topic topic = new Topic(UUID.randomUUID(), title, description, ...);
        topicRepository.save(topic);
        auditLogService.logCreate(AuditEntityType.TOPIC, topic.id(),
                null, null, userId, Map.of("title", title));
        return topic;
    }

    // GET-list: два метода, validation filter combinations here
    @Transactional(readOnly = true)
    public List<Topic> listPage(String visibility, int limit, int offset) {
        return topicRepository.findPage(visibility, limit, offset);
    }

    @Transactional(readOnly = true)
    public long countFiltered(String visibility) {
        return topicRepository.countFiltered(visibility);
    }
}
```

**Правила:**
- `@Transactional` только на Service (не Repository, не Controller)
- `@Transactional(readOnly = true)` для read-only методов
- Валидация бизнес-правил (enum whitelist, combination rules) — здесь, не в Repository
- `IllegalArgumentException` → 400 (невалидный param), `Invalid*Exception` → 422
  (нарушение инвариант payload)
- Audit log вызывается в mutation-методах явно (`auditLogService.log*`)

---

## Step 4: Controller

```java
@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {

    private final TopicService topicService;
    private final PermissionService permissionService;

    public TopicController(TopicService topicService,
                           PermissionService permissionService) {
        this.topicService = topicService;
        this.permissionService = permissionService;
    }

    // POST create: @Valid, @RequestBody, userId, 201 + Location
    @PostMapping
    public ResponseEntity<TopicResponse> create(
            @Valid @RequestBody CreateTopicRequest request,
            @CurrentUser UUID userId) {
        Topic created = topicService.createTopic(
                request.title(), request.description(),
                request.rootQuestion(), request.visibility(), userId
        );
        TopicResponse body = DtoMappers.toResponse(topicService.getTopicWithCounts(created.id()));
        return ResponseEntity.created(URI.create("/api/v1/topics/" + created.id())).body(body);
    }

    // GET list: @RequestParam(required=false), PageRequest.from, двойной вызов service
    @GetMapping
    public PagedResponse<TopicResponse> list(
            @CurrentUser UUID userId,
            @RequestParam(name = "visibility", required = false) String visibility,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        PageRequest pr = PageRequest.from(page, size);
        List<TopicWithCounts> items = topicService.listVisibleTopicsPage(
                userId, role, visibility, pr.size(), pr.offset());
        long total = topicService.countVisibleTopics(userId, role, visibility);
        return PagedResponse.of(items.stream().map(DtoMappers::toResponse).toList(),
                pr.page(), pr.size(), total);
    }

    // GET single: permission check, возврат response напрямую (не ResponseEntity)
    @GetMapping("/{topicId}")
    public TopicResponse getOne(@PathVariable UUID topicId,
                                @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        permissionService.assertCanRead(topicId, userId, role);
        return DtoMappers.toResponse(topicService.getTopicWithCounts(topicId));
    }

    // PATCH: @Valid, @RequestBody, возвращает 200 с телом
    @PatchMapping("/{topicId}")
    public TopicResponse patchTopic(@PathVariable UUID topicId,
                                    @Valid @RequestBody UpdateTopicRequest request,
                                    @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        Topic updated = topicService.updateTopic(
                topicId, request.title(), request.description(), userId, role);
        return DtoMappers.toResponse(topicService.getTopicWithCounts(updated.id()));
    }

    // DELETE: 204 No Content
    @DeleteMapping("/{topicId}")
    public ResponseEntity<Void> delete(@PathVariable UUID topicId,
                                       @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        topicService.deleteTopic(topicId, userId, role);
        return ResponseEntity.noContent().build();
    }
}
```

**Правила:**
- `@Valid` на `@RequestBody` — без него Bean Validation не срабатывает (→ 400 не придёт)
- `@CurrentUser UUID userId` — извлекается из SecurityContext через `JwtAuthenticationFilter`
  или `XUserIdAuthenticationFilter` (dev/test: `X-User-Id` header)
- `SecurityContextUtils.currentRoleOrAnonymous()` — роль читается из SecurityContext, не
  через новый ArgumentResolver
- Permission checks — в Service через `permissionService.assertCan*`, или в Controller
  для simple visibility guard. Бросает `*AccessDeniedException` → 403
- DtoMappers.toResponse(domain) — статические маппинг-методы, не добавлять бизнес-логику

### Mapper

Добавляй в `DtoMappers.java` (static методы):

```java
// В DtoMappers:
public static TopicResponse toResponse(Topic topic) {
    return new TopicResponse(topic.id(), topic.title(), topic.description(),
            topic.rootNodeId(), topic.createdBy(), topic.createdAt(),
            topic.visibility(), topic.statusAlgorithm(), 0, 0);
}
```

---

## Step 5: Pagination (только для GET-list)

Полный шаблон GET-list с пагинацией и фильтрами (на примере `AuthorityController`):

```java
// Controller
@GetMapping
public PagedResponse<AuthorityResponse> list(
        @RequestParam(name = "q", required = false) String query,
        @RequestParam(name = "era", required = false) String era,
        @RequestParam(name = "page", required = false) Integer page,
        @RequestParam(name = "size", required = false) Integer size) {
    PageRequest pr = PageRequest.from(page, size);          // default page=0, size=20, MAX=100
    List<Authority> items = authorityService.listPage(query, era, pr.size(), pr.offset());
    long total = authorityService.countFiltered(query, era);
    return PagedResponse.of(
            items.stream().map(DtoMappers::toResponse).toList(),
            pr.page(), pr.size(), total
    );
}
```

`PagedResponse.of(items, page, size, total)` вычислит `totalPages`, `hasNext`, `hasPrev`.

**Структура ответа:**
```json
{
  "items": [...],
  "page": 0,
  "size": 20,
  "total": 47,
  "totalPages": 3,
  "hasNext": true,
  "hasPrev": false
}
```

**Repository checklist для list:**
- [ ] `findPage(filters..., int limit, int offset)` — SELECT с LIMIT/OFFSET
- [ ] `countFiltered(filters...)` — COUNT с теми же фильтрами
- [ ] Оба метода используют `appendFilters(...)` — единый WHERE-источник
- [ ] Сортировка по умолчанию `created_at DESC` (или явное обоснование для другой)

---

## Step 6: IT тест

```java
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional                      // rollback после каждого теста
class TopicControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        // Создать user (FK для topics/nodes)
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com"
        );
    }

    // Happy path — 201
    @Test
    void createTopic_returns201_withLocationAndBody() throws Exception {
        var req = new CreateTopicRequest("Тема", "описание", "Вопрос?", null);
        mockMvc.perform(post("/api/v1/topics")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/topics/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Тема"));
    }

    // Validation error — 400
    @Test
    void createTopic_blankTitle_returns400() throws Exception {
        var req = new CreateTopicRequest("", "описание", "Вопрос?", null);
        mockMvc.perform(post("/api/v1/topics")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // Not found — 404
    @Test
    void getOneTopic_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/topics/{id}", UUID.randomUUID())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    // Unauthorized — 401 (нет X-User-Id)
    @Test
    void createTopic_withoutUserHeader_returns401() throws Exception {
        var req = new CreateTopicRequest("Тема", null, "Вопрос?", null);
        mockMvc.perform(post("/api/v1/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // Permission denied — 403
    @Test
    void deleteTopic_byNonOwner_returns403() throws Exception {
        // создать тему от userId, пытаться удалить от другого
        UUID anotherUser = createUser();
        UUID topicId = createTopic(userId);
        mockMvc.perform(delete("/api/v1/topics/{id}", topicId)
                        .header("X-User-Id", anotherUser.toString()))
                .andExpect(status().isForbidden());
    }
}
```

**Naming:** `method_condition_outcome` — например `createTopic_blankTitle_returns400`.

**Обязательные кейсы:**
- [ ] Happy path (200/201/204)
- [ ] Validation error — 400 (missing/invalid field)
- [ ] Not found — 404
- [ ] Unauthorized — 401 (без X-User-Id)
- [ ] Permission denied — 403 (если endpoint protected)
- [ ] Pagination: если GET-list — `paginated_returnsCorrectPage` + `sizeOverMax_clampsTo100`

---

## Step 7: api-contract.md update

Каждый новый endpoint **обязательно** добавляется в `docs/api-contract.md` в тот же коммит.

Формат записи:

```markdown
### POST /api/v1/topics

Создать новую тему. Возвращает созданную тему с Location header.

**Auth:** Bearer JWT / X-User-Id (dev)
**Permission:** любой authenticated

**Request body:**
```json
{
  "title": "string (required, max 200)",
  "description": "string (optional, max 2000)",
  "rootQuestion": "string (required, max 10000)",
  "visibility": "PRIVATE | SHARED | PUBLIC (optional, default PRIVATE)"
}
```

**Responses:**
- `201 Created` + `Location: /api/v1/topics/{id}` — тема создана
- `400 Bad Request` — невалидное тело (Problem Details)
- `401 Unauthorized` — нет principal

**Response body (201):**
```json
{
  "id": "uuid",
  "title": "string",
  "description": "string | null",
  "rootNodeId": "uuid",
  "createdBy": "uuid",
  "createdAt": "ISO-8601",
  "visibility": "PRIVATE | SHARED | PUBLIC",
  "statusAlgorithm": "MVP | DUNG_GROUNDED",
  "nodeCount": 1,
  "edgeCount": 0
}
```
```

---

## Step 8: Frontend regeneration

После прохождения IT для нового endpoint — обязательный шаг:

```bash
# 1. Убедиться что backend запущен (порт 9090)
curl -sf http://localhost:9090/actuator/health

# 2. Регенерировать типы
cd frontend && npm run generate-api

# 3. Проверить что новый endpoint появился
git diff frontend/src/shared/api/types.ts
```

Если `generate-api` завершился с ошибкой — backend не запущен или не применена миграция.
Запустить backend:
```bash
cd backend && ./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  > /tmp/backend.log 2>&1 &
until curl -sf http://localhost:9090/actuator/health; do sleep 2; done
```

---

## Step 9: Audit log (для mutation endpoints)

Если endpoint меняет данные — вызвать `auditLogService` в Service-методе:

```java
// CREATE
auditLogService.logCreate(AuditEntityType.TOPIC, entity.id(),
        null, null,                         // parentType, parentId (если есть)
        actorUserId,
        Map.of("title", entity.title()));   // snapshot — только key fields

// UPDATE
auditLogService.logUpdate(AuditEntityType.TOPIC, entity.id(),
        null, null, actorUserId,
        Map.of("title", new AuditLogService.FieldDiff(oldTitle, newTitle)));

// DELETE
auditLogService.logDelete(AuditEntityType.TOPIC, entity.id(),
        null, null, actorUserId,
        Map.of("title", entity.title()));

// VISIBILITY CHANGE
auditLogService.logVisibilityChange(AuditEntityType.TOPIC, entity.id(),
        actorUserId, oldVisibility, newVisibility);
```

Snapshot содержит только identifying fields (title/content/visibility) — не полный entity.
Полные snapshots раздуют jsonb без практической пользы.

---

## Pre-commit checklist

Перед `git add` проверить:

- [ ] `docs/api-contract.md` обновлён (новый endpoint + request/response + status codes)?
- [ ] IT написан и проходит — happy path + 400 + 404 + 401?
- [ ] `@Valid` на `@RequestBody` в Controller?
- [ ] Permission checks покрыты (если endpoint protected) — IT-кейс на 403?
- [ ] Pagination conventions соблюдены (если GET-list)?
- [ ] `appendFilters` используется для DRY count vs select?
- [ ] Audit log вызван (если mutation)?
- [ ] `npm run generate-api` выполнен и `git diff types.ts` показывает новый endpoint?

---

## Частые ошибки

| Ошибка | Симптом | Решение |
|--------|---------|---------|
| Забыт `@Valid` на `@RequestBody` | `@NotBlank` / `@Size` не срабатывают, 200 вместо 400 | Всегда `@Valid @RequestBody` |
| Service бросает checked exception, controller не handle | 500 вместо 4xx | Добавить `@ExceptionHandler` в `GlobalExceptionHandler.java` |
| Нет маппинга в GlobalExceptionHandler | 500 с неизвестным exception типом | Добавить `@ExceptionHandler(NewXyzException.class)` → 4xx |
| COUNT и SELECT расходятся при фильтрации | Неверный `total` в `PagedResponse`, pagination breaks | Единый `appendFilters` helper для обоих методов |
| `api-contract.md` не обновлён | Frontend dev не знает о новом endpoint, Swagger отстаёт | Обновлять в том же коммите что и Controller |
| Забыт `npm run generate-api` | Frontend работает со старыми `types.ts`, новые поля `any` | Запускать сразу после прохождения IT |
| Audit log пропущен в mutation endpoint | Нет трассировки изменений в audit_log таблице | Явный вызов `auditLogService.log*` в Service |

---

## Примеры из проекта

### Пример 1: GET single — `TopicController.getOne`

Файл: `backend/src/main/java/.../web/controller/TopicController.java`

```java
@GetMapping("/{topicId}")
public TopicResponse getOne(@PathVariable UUID topicId, @CurrentUser UUID userId) {
    String role = SecurityContextUtils.currentRoleOrAnonymous();
    permissionService.assertCanRead(topicId, userId, role);
    return DtoMappers.toResponse(topicService.getTopicWithCounts(topicId));
}
```

DTO: `TopicResponse` — record с `id`, `title`, `nodeCount`, `edgeCount` и пр.
IT: `TopicControllerIT.getOneTopic_*` — happy path (200), unknown id (404),
no header (401), другой user PRIVATE (403).

---

### Пример 2: GET list с filters + pagination — `AuditLogController.auditAdmin`

Файл: `backend/src/main/java/.../web/controller/AuditLogController.java`

```java
@GetMapping("/admin")
public PagedResponse<AuditLogResponse> auditAdmin(
        @CurrentUser UUID currentUserId,
        @RequestParam(name = "entityType", required = false) String entityType,
        @RequestParam(name = "actorId", required = false) UUID actorId,
        @RequestParam(name = "dateFrom", required = false) String dateFromIso,
        @RequestParam(name = "dateTo", required = false) String dateToIso,
        @RequestParam(name = "page", required = false) Integer page,
        @RequestParam(name = "size", required = false) Integer size) {
    String role = SecurityContextUtils.currentRoleOrAnonymous();
    if (!UserRole.ADMIN.equals(role)) {
        throw new AdminOnlyException(currentUserId);  // → 403
    }
    // Валидация enum-строки до вызова Repository
    if (entityType != null && !AuditEntityType.isValid(entityType)) {
        throw new IllegalArgumentException("Невалидный entityType: " + entityType);
    }
    Instant dateFrom = parseIso(dateFromIso, "dateFrom");
    Instant dateTo   = parseIso(dateToIso, "dateTo");
    PageRequest pr = PageRequest.from(page, size);
    List<AuditLog> items = auditLogService.findFilteredPage(
            entityType, actorId, dateFrom, dateTo, pr.size(), pr.offset());
    long total = auditLogService.countFiltered(entityType, actorId, dateFrom, dateTo);
    return PagedResponse.of(toResponses(items), pr.page(), pr.size(), total);
}
```

Отличительные черты: ADMIN-только guard, валидация enum-параметра до Repository,
ISO-8601 парсинг с helpful error message (`parseIso` helper), двойной вызов service
(`findFilteredPage` + `countFiltered`).

---

### Пример 3: Mutation action endpoint — `EdgeController.bringToFront`

Файл: `backend/src/main/java/.../web/controller/EdgeController.java`

```java
// POST без тела — action, меняет состояние (not idempotent)
@PostMapping("/{edgeId}/z-order/bring-to-front")
public EdgeResponse bringToFront(@PathVariable UUID edgeId,
                                 @CurrentUser UUID userId) {
    String role = SecurityContextUtils.currentRoleOrAnonymous();
    Edge edge = edgeService.bringToFront(edgeId, userId, role);
    return DtoMappers.toResponse(edge);
}
```

Особенности: нет `@RequestBody` (action без тела), возвращает обновлённый entity напрямую
(не `ResponseEntity`), 200 ОК. Permission check делегируется в `edgeService.bringToFront`
(внутри проверяет `permissionService.assertCanWrite(topicId, userId, role)`).

---

## Error handling reference

Существующие exception → HTTP маппинги в `GlobalExceptionHandler`:

| Exception | HTTP | Problem type |
|-----------|------|--------------|
| `TopicNotFoundException` | 404 | `topic-not-found` |
| `NodeNotFoundException` | 404 | `node-not-found` |
| `TopicAccessDeniedException` | 403 | `forbidden-topic-access` |
| `TopicWriteAccessDeniedException` | 403 | `forbidden-topic-write` |
| `AdminOnlyException` | 403 | `forbidden-admin-only` |
| `MethodArgumentNotValidException` | 400 | (Spring auto) |
| `IllegalArgumentException` | 400 | `bad-request` |
| `DataIntegrityViolationException` | 409 | `conflict` |

Для **нового домена** — создать `*NotFoundException extends RuntimeException` +
добавить `@ExceptionHandler` в `GlobalExceptionHandler.java`.
