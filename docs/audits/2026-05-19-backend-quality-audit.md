# Backend quality audit 2026-05-19

stability/maintainability round - продолжение audit'а 2026-05-18 после
закрытия Сессии 46 (988→999 тестов, ADR-046/047/048).

baseline на старте сессии: 999 тестов, 8 pre-existing IT failures
(I-1 из audit 2026-05-18, 401 vs 400 без user header), отложены в
backlog как нерешённый architecture trade-off

аудит фокусируется на:
- minimizing bugs (security, exceptions, transactions)
- maintainability (FQN, magic numbers, long methods)
- extensibility (god classes, duplication, dead code)

## Контекст

бэк продолжает быть зрелым - 374 main java файлов, ~28.6k LOC.
последний audit (2026-05-18) закрыл I-2 FQN cleanup, I-3 audit
helpers, I-4 import order, M-1 nullSafe → JSON null. этот audit
ищет накопившиеся новые мелочи + проверяет глобальные паттерны
которые не были покрыты раньше

## Strengths (что хорошо - не трогать)

- **TODO/FIXME/XXX/HACK маркеры: 0** в `src/main` - чисто
- **Lombok imports: 0** - соблюдается convention (только records)
- **JPA/Hibernate: 0** - только JDBC Template
- **H2 в тестах: 0** - только Testcontainers
- **SQL injection через String.format: 0** - все queries
  parameterized через `?`/PreparedStatement
- **`@Scheduled` + `@Transactional`: 0** - все 4 scheduled jobs
  (`OrphanDetectionJanitor`, `IntegrityVerificationJob`,
  `AuditLogRetentionJanitor`, `RefreshTokenCleanupJanitor`) без
  `@Transactional`, документация явно объясняет почему
- **System.exit / Runtime.exec / ProcessBuilder: 0** - нет direct
  shell escalation
- **Weak crypto (MD5/DES/RC4/SHA1/insecure Random): 0** - SHA-256
  для refresh hashes, bcrypt для passwords, SecureRandom где нужен
- **Empty catch blocks / catch Throwable: 0**
- **Field injection через `@Autowired`: 0** (один на конструкторе -
  отдельный issue ниже)
- **Inline FQN references**: предыдущий audit вычистил большую часть,
  осталось ~20 мест (см. ниже Important)

## Critical (must fix)

ничего critical не найдено - production-grade code без security
incidents либо broken invariants

## Important (should fix)

### I-1. `@RequestBody` без `@Valid` - 3 endpoint'а

`backend/CLAUDE.md` REST конвенция: «Валидация через Bean Validation
(`@Valid` + аннотации)». Три места где `@Valid` пропущен:

- `PreferencesController.java:50` - `putAll(@CurrentUser, @RequestBody
  Map<String, Object> updates)` - валидация делегирована в service
  (whitelist + типы), что приемлемо но `@Valid` на Map не имеет
  смысла → **Info, оставить**
- `PreferencesController.java:57` - `putOne(@CurrentUser, @PathVariable
  String key, @RequestBody SingleValueRequest body)` - record с
  одним полем `Object value`, нет Bean Validation annotations.
  Контроллер сам проверяет `body == null` → 400. Acceptable но
  inconsistent
- `TopicExportImportController.java:86` - `importJson(@RequestBody
  TopicExportDto dto, @CurrentUser UUID currentUserId)` -
  `TopicExportDto` это полноценный record с nested structure, мог
  бы иметь validation annotations. Сейчас валидация при импорте
  делегирована в `TopicImportService.importTopic` где валидируется
  `formatVersion`, payload structure. Acceptable но не Bean
  Validation pattern

**Решение:** добавить `@Valid` на `TopicExportImportController.java:86` -
hook для будущих validation annotations в `TopicExportDto`. На
`PreferencesController` не добавляем т.к. `SingleValueRequest`
deliberately weakly-typed (Object value)

### I-2. Inline FQN references - ~20 мест

`backend/CLAUDE.md`: «Импорты вместо полных квалифицированных имён».
Предыдущий audit (I-2 2026-05-18) убрал большую часть, но остались:

- `repository/NodeSourceRepository.java:293` -
  `java.util.Collections.nCopies` → import
- `service/TopicExportService.java:99` - `new java.util.ArrayList<>()` → import
- `library/pdf/service/PdfLinksSourceProvider.java:290` -
  `java.util.Locale.ROOT` → import
- `library/shamela/service/mapper/ShamelaBibliographyParser.java:67-78` -
  `java.util.Map.ofEntries`, `java.util.Map.entry` x11 раз → import
- `library/repository/PageRepository.java:173,199-200,224,250-251,275` -
  `java.time.Instant` параметры (7 occurrences) → import

mechanical low-risk fix, IDE/checkstyle поймал бы

### I-3. Избыточный `@Autowired` на конструкторе

`library/imports/AnthropicClient.java:68` - явный `@Autowired` на
конструкторе. С Spring 4.3+ единственный публичный конструктор
inject'ится автоматически без annotation. в проекте остальные
service классы не используют `@Autowired` на конструкторах (
PreferencesController, BookService и др.) - inconsistency

**Решение:** удалить `@Autowired`, оставить только `@Component`/`@Service`
на классе

### I-4. Дублирующая логика `citationFromRow` в трёх Repository

три файла содержат **идентичную** private static функцию для
маппинга citation полей из `ResultSet`:

- `repository/NodeSourceRepository.java:199` (58 lines)
- `qa/repository/AnswerSourceRepository.java:159` (58 lines)
- `qa/repository/QuestionSourceRepository.java:156` (58 lines)

логика: парсинг `book_id`, `page_number`, `publisher_name`,
`muhaqqiq_name`, `edition_number`, `quote`, `context`, `inline_position`,
`citation_mode` → `CitationDetail` record

**Решение:** вынести в `CitationDetailRowMapper` shared util в
`repository/` (или `domain/` рядом с `CitationDetail`). 3x58=174 LOC
становится 58 LOC. Изменение mechanical low-risk - private static
method, нет breaking change

## Minor (nice to have)

### M-1. Magic numbers - локально terpimo но повторяющиеся

- `DEFAULT_LIMIT = 20` + `MAX_LIMIT = 100` повторены в 4 controller'ах
  (`PublicationPlaceController`, `PublisherController`, `MuhaqqiqController`,
  `ShamelaAdminController`). Можно вынести в `web.dto.PageRequest`
  как public constants. **отложено** - small benefit, breaking change
  scope не оправдан
- `BUFFER_SIZE = 64 * 1024` повторено в `ObjectStorageService` и
  `IntegrityVerificationJob` - тот же байтовый буфер для streaming.
  **отложено** - 2 места, OK
- `KEEP_ALIVE_SECONDS = 60/120/...` варьируется между configs -
  ожидаемо (web vs OCR vs AI edit), не дубликация

### M-2. Long methods (>50 LOC) - 15 методов

топ candidates:

- `service/NodeCitationService.createCitation` (100 lines) +
  `qa/service/AnswerCitationService.createCitation` (98) +
  `qa/service/QuestionCitationService.createCitation` (98) -
  параллельная триада, та же логика для разных parent entities.
  candidate на shared `CitationCreationFlow` helper. **отложено** -
  требует careful refactor, не mechanical
- `service/DungFrameworkService.computeGroundedLabelling` (87 lines) -
  argumentation framework algorithm, splittable on `compute*Step`
  helpers. **отложено** - сложная domain логика, разделение может
  ухудшить читаемость
- `service/TopicImportService.importTopic` (70 lines) - оркестрация
  6-7 шагов импорта (validate → topic → nodes → edges → sources →
  authorities → response). **отложено** - оркестрация одного flow,
  split добавит state passing между методами
- `auth/web/security/SecurityConfig.securityFilterChain` (91) +
  `ActuatorSecurityConfig.actuatorFilterChain` (71) - Spring Security
  DSL fluent calls, сложно разбить без потери читаемости
- `auth/service/AuthService.refresh` (57) - JWT refresh rotation
  flow. **отложено**
- `library/imports/AiEditService.enhance` (85) - LLM call pipeline.
  **отложено**
- `library/imports/FileImportService.importPdf` (116) - PDF upload
  workflow. **отложено**
- `auth/web/security/RateLimitFilter.doFilterInternal` (80) -
  filter logic с многими branches. **отложено**

общая характеристика: большинство длинных методов - integration
points / workflow orchestration, где разделение усложнит трассировку
flow. split откладывается до момента когда конкретный метод нужно
будет расширить - тогда разрезать имеет смысл (extract-by-need)

### M-3. God class candidates - 5 файлов >350 LOC

- `exception/GlobalExceptionHandler.java` - 539 LOC, 58 `@ExceptionHandler`
  (previous audit I-3 / M-4 уже flag'нул, остался отложенным
  из-за ordering risk при split)
- `library/service/BookService.java` - 482 LOC (previous audit
  M-2/M-3 flag'нул, отложен «wait until 600 LOC»). сейчас 482 -
  всё ещё в backlog
- `service/TopicImportService.java` - 424 LOC, одна public
  importTopic + private steps. acceptable
- `library/pdf/service/PdfLinksSourceProvider.java` - 402 LOC.
  отложен на subagent A (working on PdfController), не трогаю
- `repository/NodeSourceRepository.java` - 353 LOC, типичный
  репозиторий со множеством query helpers. acceptable

ни один не пересёк threshold 600 где split становится urgent

### M-4. `@Transactional` на async write methods

`AiEditService.enhance` и `OcrService.recognize` - оба делают
**несколько JDBC updates** (PROCESSING → DONE/FAILED) без
`@Transactional`. Каждый update отдельный transaction → race
condition если concurrent enhance того же pageId. ID idempotent
(last-write-wins), но семантически окно где status=PROCESSING без
started_at либо started_at без status

**не bug сейчас** - концurrent enhance same pageId блокируется на
`enhanceAsync` через single-flight check (`isPending()` перед
submit). но если future caller вызовет `enhance()` синхронно без
single-flight - race вернётся. **отложено** - low impact, async
pipeline уже единственный entry point

## Что НЕ нашёл (zerstörnoeo nothing)

- **deprecated API usage**: 0 в src/main. Spring Boot 3.5, Spring
  Security 6.5, AWS SDK v2 - все на актуальных API
- **Custom exceptions без handler в GlobalExceptionHandler**: все
  56 custom exception classes имеют либо direct `@ExceptionHandler`
  либо обрабатываются через generic Exception handler. Спот-проверил
  `UnsupportedExportFormatException`, `UnsupportedMediaTypeException`,
  `RangeNotSatisfiableException` - все handled
- **N+1 queries**: все list endpoints используют bulk load patterns
  (previous audit подтвердил, в этой сессии не нашёл новых)
- **Soft secrets в коде**: 0 hardcoded passwords/keys. Все через
  env vars / `application.yml` с placeholders. dev-only password
  `{noop}placeholder` в `application.yml` - явно `dev` profile
- **`@RestController` без request mapping**: 0
- **public method без callers (dead code)**: не проверял exhaustive
  (требует IDE-level analysis), spot-check показал нет очевидных
  орфанов
- **double-checked locking без volatile**: 0 - проект не использует
  double-checked pattern, lazy init через Spring lifecycle

## Fixed в этой сессии

| Issue | Commit | Что закрыто |
|---|---|---|
| I-2 | `06827b7` | inline FQN refs → imports в 5 файлах (PageRepository, NodeSourceRepository, TopicExportService, PdfLinksSourceProvider, ShamelaBibliographyParser) |
| I-4 | `8a4a461` | citationFromRow дедупликация → CitationDetailRowMapper. 3x58 LOC → 1x58 LOC. -89 LOC net |
| I-1 (subset) | `10bd951` | @Valid на TopicExportImportController.importJson |
| - | `d4b6d6b` | revert dcc7df5 - @Autowired нужен в AnthropicClient из-за второго конструктора |

### Снято с плана (false positive)

**I-3 убрать `@Autowired` в AnthropicClient** - **отменено**. Spring
4.3+ auto-injects единственный конструктор, но у AnthropicClient их
два (production + test). При двух конструкторах Spring не может
выбрать без `@Autowired` hint - падает с `No default constructor
found` на старте ApplicationContext. Поймал через failing
`ArgumentMapApplicationTests.contextLoads` после dcc7df5, откатил
через d4b6d6b. **`@Autowired` здесь обязателен, не избыточен**.

Этот false positive - lesson learned: «убрать `@Autowired`» -
mechanical только когда конструктор единственный. Когда есть test
constructor (как у AnthropicClient, HttpClientPdfFetcher etc.) -
оставлять

## Recommendations (priorities)

### Закрываем в этой сессии (impactful + safe)

1. **I-2 FQN → imports** в 5 файлах - mechanical, low risk
2. **I-3 Удалить избыточный `@Autowired`** на конструкторе
   `AnthropicClient` - single line, zero risk
3. **I-4 `citationFromRow` дедупликация** - вынести в shared
   `CitationDetailRowMapper`. ~174 LOC → ~60 LOC, mechanical refactor
4. **I-1 (subset) добавить `@Valid` на `TopicExportImportController`**
   `importJson` - hook для будущей DTO validation

### Откладываем в backlog

- **I-1 `PreferencesController` `@Valid`** - не application (Map<String,
  Object> + opaque SingleValueRequest)
- **M-1 magic numbers** - low benefit, breaking-change scope
- **M-2 long methods** - все 15 кандидатов требуют careful refactor с
  thought, не cleanup task. Extract-by-need принцип - резать когда
  расширение требует
- **M-3 GlobalExceptionHandler split** - previous audit отложил
  ordering risk, situation не изменилась
- **M-3 BookService split** - 482/600 LOC, ещё не triggered
- **M-4 async write `@Transactional`** - не bug сейчас, single-flight
  блокирует race. Document когда добавить sync entry point

## Metrics (старт → end сессии)

| метрика | до | после |
|---|---|---|
| Deprecated APIs | 0 | 0 |
| TODO/FIXME маркеры | 0 | 0 |
| Magic numbers (3+ duplicate sites) | 0 | 0 (M-1 отложено) |
| Inline FQN references | ~20 | 0 |
| Long methods (>50 LOC) | 15 | 15 (M-2 отложено - extract-by-need) |
| God classes (>500 LOC) | 1 (GlobalExceptionHandler 539) | 1 (M-3 отложено) |
| Custom exceptions без handler | 0 | 0 |
| `@RequestBody` без `@Valid` (oversight) | 1 (TopicExportImportController) | 0 |
| Дублирующая `citationFromRow` логика | 3 sites x 58 LOC = 174 | 1 shared = 58 (-89 LOC net) |
| Lombok imports | 0 | 0 |
| JPA/Hibernate | 0 | 0 |
| H2 в тестах | 0 | 0 |
| SQL injection через String.format | 0 | 0 |
| `@Scheduled` + `@Transactional` antipattern | 0 | 0 |
| System.exit / ProcessBuilder | 0 | 0 |
| Weak crypto (MD5/SHA1/Random) | 0 | 0 |
| Empty catch / catch Throwable | 0 | 0 |

## Acceptance criteria для этой сессии

- baseline 999 tests passing на старте (8 pre-existing fails не в scope)
- atomic commits по теме, не giant cleanup
- финальный точечный verify затронутых ITs = pass
- audit-файл обновлён после каждого fix'а (Fixed секция)
