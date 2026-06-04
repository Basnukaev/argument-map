# Backend audit 2026-05-18

stability/quality round - cleanup без новых features. baseline `./mvnw verify`:
**879 тестов**, 871 pass, 8 fail (pre-existing, см. ниже), 2 skipped

## Контекст

бэк зрелый - 370 main java файлов, 107 test файлов. ETL/audit/auth/permissions
закрыты на больших циклах. coding-standards и antipatterns соблюдены в
бОльшей части кодбаз (нет TODO/FIXME, два `@SuppressWarnings` оба
оправданы AOP-вызовами через CB)

аудит ищет узкие места которые накопились между подэтапами и не были
закрыты code review раундами

## Critical (must fix)

ничего критического не нашёл - production-grade code

## Important (should fix)

### I-1. Pre-existing test failures - 8 IT падают с 401 вместо 400

`./mvnw verify` идёт с 8 устойчивыми failures:
- `FileImportControllerIT.POST_missingUserHeader_returns400`
- `ShamelaAdminControllerIT.mapBook_returns_400_when_x_user_id_header_missing`
- `BookControllerIT.createBook_withoutUserHeader_returns400`
- `AnswerControllerIT.DELETE_answer_missingUserHeader_returns400`
- `QuestionControllerIT.createQuestion_missingUserHeader_returns400`
- `TopicControllerIT.createTopic_withInvalidUserHeader_returns400`
- `TopicControllerIT.createTopic_withoutUserHeader_returns400_problemDetail`
- `TopicExportImportControllerIT.importJson_withoutUserHeader_returns400`

все ожидают 400 (через `MissingUserHeaderException` → `missing-user-header`),
но Spring Security возвращает 401 раньше. это конфликт между permitAll и
кастомным X-User-Id фильтром. ADR-040 amendment нужен, либо test fixture
обновить под фактическое поведение (401 - валидный response для unauthenticated
mutating)

**не входило в scope этой сессии** - вынес в backlog (см. ниже)

### I-2. Inline FQN references вместо imports - 11 мест (нарушает CLAUDE.md)

`backend/CLAUDE.md` явно требует «Импорты вместо полных квалифицированных имён»

вхождения в production code (не в @link/javadoc):
- `BookService.java:203,214` - `ru.basnukaev.argumentmap.auth.domain.UserRole.ADMIN`
- `FileImportService.java:149,159` - `ru.basnukaev.argumentmap.library.domain.BookVisibility.PRIVATE`
- `NodeSourceService.java:59` - return type `NodeSourceRepository.NodeSourceWithLocation`
- `NodeVoteService.java:63,80` - `SecurityContextUtils.currentRole()`
- `ShamelaToLibraryMapper.java:156` - `BookVisibility.PUBLIC`
- `TopicImportService.java:132,133` - `TopicVisibility.PRIVATE`, `StatusAlgorithm.MVP`
- `DtoMappers.java:235` - `CitationMode.LEGACY`

`java.util.Objects.equals` используется в 15 местах FQN-style (BookService,
EdgeService, NodeService, NodeTranslationService, QuestionService, AnswerService) -
тоже нарушение, добавить `import java.util.Objects`

### I-3. Audit snapshot building duplication - 7 сервисов

каждый mutation site вручную строит `LinkedHashMap<String, Object>` для
`auditLogService.logCreate/logDelete`:

```java
Map<String, Object> snapshot = new LinkedHashMap<>();
snapshot.put("title", existing.title());
snapshot.put("bookType", existing.bookType() == null ? null : existing.bookType().name());
snapshot.put("visibility", existing.visibility());
auditLogService.logDelete(...);
```

это паттерн в `BookService`, `NodeService`, `EdgeService`, `TopicService`,
`NodeTranslationService`, `QuestionService`, `AnswerService` - 30 случаев
`new LinkedHashMap<>()` в main code

**helper-метод** `AuditLogService.snapshot(Object... keyValuePairs)` (или
builder pattern) ликвидирует boilerplate, делает audit code читаемее.

`FieldDiff` сравнения тоже дублируются - в `EdgeService.updateEdge`,
`BookService.updateAcademicMetadata` и других каждое поле проверяется
вручную через `!Objects.equals(...)` + `diff.put(...)`. helper
`DiffBuilder` упростит

### I-4. Import order misorganized - 7 сервисов

в `EdgeService`, `NodeService`, `QuestionService`, `AnswerService`,
`NodeTranslationService` (грубо все которые добавили audit logging):

```java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;   // <-- неправильное место
import java.util.Map;              // <-- должен быть в первом блоке

import ru.basnukaev.argumentmap.domain.AuditEntityType;
```

стандарт - все `java.*` в одном блоке, `org.*` в следующем, `ru.basnukaev.*`
в последнем. IDE/checkstyle поймал бы

## Minor (nice to have)

### M-1. `nullSafe` в AuditLogService возвращает пустую строку для null

```java
private static Object nullSafe(Object v) {
    return v == null ? "" : v;
}
```

это семантически отличает «не было значения» от «пустая строка» в audit
diff. для текстовых полей пустая строка может быть валидным значением -
сравнение `audit.before == "" && audit.after == "foo"` неоднозначно
(было ли поле пустым или null'ом).

лучше JSON-null - Jackson сериализует `null` → `null` в JSON, явное
отличие от `""`. в API контракте clarify что null = absent.

**низкий приоритет** - audit log используется для forensic, не для UI
консьюмера сейчас (admin UI отложен)

### M-2. `BookService.createBook` тройная перегрузка - 3 публичных метода

`createBook(...,7 args)` → `createBook(...,13 args)` → `createBook(...,14 args, visibility)` -
каждая вызывает следующую с defaults. это нормальный default-args pattern
для Java (нет default параметров), но имхо чище через
**Command/Request record**:

```java
public record CreateBookCommand(BookType type, String title, UUID authorityId,
                                String language, String description, String metadataJson,
                                UUID currentUserId, String muhaqqiqName, ...,
                                String visibility) {
    public CreateBookCommand withDefaults() { ... }
}

public Book createBook(CreateBookCommand cmd) { ... }
```

это снизит размер `BookService` и упростит будущие add-fields. не делаем
сейчас потому что это breaking change для всех callers - тестов и сервисов

### M-3. `BookService` >450 строк - splittable

`BookService` (497 lines) можно разделить:
- `BookService` - CRUD, visibility, metadata (core)
- `BookChapterReader` (или вынести метод в `ChapterService`) -
  `buildChapterTree`, `getBookWithChapters` помещение

но split добавит coupling между сервисами без явного выигрыша. сейчас
сервис в пределах разумного. отложено.

### M-4. `GlobalExceptionHandler` 28 `@ExceptionHandler` методов в одном классе

`GlobalExceptionHandler` 497 строк, 28 handlers. Spring требует чтобы все
handlers были в одном `@RestControllerAdvice` либо использовать `@Order`
для нескольких. альтернатива - разбить по доменам:

- `AuthExceptionHandler` (auth/permissions)
- `LibraryExceptionHandler` (book/page/pdf/shamela/file import)
- `QaExceptionHandler` (question/answer)
- `CoreExceptionHandler` (topic/node/edge/source/authority + generic)

каждый с `@Order(...)`. но split может вызвать ordering issues если
exceptions imhanren друг от друга. сейчас всё работает. **отложено**

### M-5. Magic numbers в нескольких местах

- `BookService.DEFAULT_PAGE_RANGE = 50` - constant в сервисе, не в property
- `Integer.MAX_VALUE` в `listPages` как «весь диапазон» - явный sentinel
  лучше чем reuse MAX_VALUE
- `NodeService.ALLOWED_ORIGINAL_LANGS = Set.of("ar", "ru", "en")` -
  hardcoded, удобно было бы вынести в config либо БД CHECK

все терпимо, не критично

### M-6. `DtoMappers.toResponse(Node)` 5-перегрузок цепочка

5 перегрузок `toResponse(Node, ...)` вызывают друг друга через default
arguments. читается ок но добавляет 50 строк в файл - 5 javadoc-блоков +
сигнатуры. одна `toResponse(Node, NodeContext)` где `NodeContext` -
record-агрегатор был бы компактнее, но опять breaking change на all-callers.
**отложено**

## Что НЕ нашёл (зон без issues)

- @Transactional на @Scheduled - **корректно**, 3 @Scheduled (`OrphanDetectionJanitor`,
  `IntegrityVerificationJob`, `AuditLogRetentionJanitor`) без @Transactional,
  явно документировано в комментариях
- SQL injection - все JDBC queries через `?` placeholders, единственный
  `+` в SQL это `"SELECT " + COLUMNS + " ..."` в `TopicRepository` где
  COLUMNS - private static final String, не user input
- N+1 queries - все list endpoints используют bulk load patterns (см.
  `DtoMappers.toResponse(GraphView, ...)` с pre-fetched maps)
- Dead code - найден один `@SuppressWarnings("unused")` в
  `HttpClientPdfFetcher` x2 - оба для @CircuitBreaker AOP fallback методов,
  легитимно (Resilience4j вызывает рефлексией)
- TODO/FIXME/HACK - **ноль** маркеров в src/main
- Commented out code - ноль (`grep` ничего не нашёл)
- Field injection - проверил, везде конструктор-инъекция

## Recommendations (priorities)

### Закрываем в этой сессии (impactful + safe)

1. **I-2 FQN → imports** - механическая замена, low risk, соблюдение
   coding-standards. ~25 правок в 7 файлах
2. **I-3 audit helpers** - `AuditLogService.snapshot()` helper +
   `DiffBuilder`. снижает boilerplate в 7 сервисах на ~20%. безопасно
3. **I-4 import order** - 5-7 файлов, чисто формальная правка
4. **M-1 audit nullSafe → JSON null** - один метод, IT тест на проверку
   что Jackson сериализует null правильно

### Откладываем в backlog

- **I-1 401 vs 400 tests** - требует discussion архитектуры (нужен ли
  кастомный 400 если Spring Security возвращает 401? сценарии где это
  ломает frontend?). не cleanup-задача, а решение требующее обсуждения
- **M-2 CreateBookCommand record** - breaking change, нужен план migration
- **M-3 BookService split** - subjective improvement, нет clear winner
- **M-4 ExceptionHandler split** - ordering risk

## Coverage gaps (отложенные IT-тесты)

- `AuditLogService` - core тесты есть (`AuditLogServiceIT`), но нет
  edge cases для serialization failure (Map с self-referencing object)
- concurrent operations - permission upgrades, member add/remove под
  параллельным трафиком. low priority - не было incidents
- `BookService.updateAcademicMetadata` - edge case partial update
  с пустыми строками (blank → clear) и пробелами (`" "` → clear?)

эти gaps не критичны, отложены до того момента когда конкретный bug
вылезет

## Acceptance criteria для этой сессии

- ✓ baseline `./mvnw verify` 871 pass / 8 pre-existing fail
- → план: 5-7 атомарных коммитов с тэгами `refactor(backend):`
- → не trogат public API, не trogать DB schema
- → финальный `./mvnw verify` = 871+ pass (то же или больше)
