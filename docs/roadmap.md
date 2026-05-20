# Roadmap

Карта работ по проекту. Структура:

- **Закрытые этапы** - сжаты до одной-двух строк с указанием Сессии
  закрытия. Детали - в `docs/progress.md` (свежие) или
  `docs/archive/progress-sessions-*.md` (старые)
- **Активные этапы** - полный чек-лист подзадач
- **Бэклог** - вынесен в `docs/backlog.md`

Правила эволюции этого файла - в `docs/doc-hygiene.md` (Принципы 3 и 4)

> **Структурные изменения (Сессия 25):** после cleanup marathon
> frontend перенесён в `src/apps/{argument-map,library,admin}/` +
> `src/shared/`. Backend разделён по responsibility:
> `ShamelaImportService` удалён, разнесён на
> `MasterSyncService` + `BookImportService` + `WorkDirManager`. DTO
> переименованы под `*Response` convention. См. ADR-022 + аудит
> `docs/superpowers/audits/2026-05-11-codebase-audit.md`

---

## Закрытые этапы

- **Этап 0. Инициализация Spring Boot** (закрыт Сессия 1) - Java 21,
  Spring Boot 3.5, Postgres 16, Liquibase, Testcontainers
- **Этап 1. Схема БД** (закрыт Сессия 2) - 11 базовых миграций
  (topics / nodes / edges / sources / authorities / node_sources /
  node_authorities / revisions)
- **Этап 2. Доменная модель и репозитории** (закрыт Сессия 3) - 8
  records + 8 JDBC repositories с RowMapper, `JdbcTimes` для
  Instant↔TIMESTAMPTZ
- **Этап 3. Бизнес-логика** (закрыт Сессия 4) - TopicService,
  NodeService, EdgeService, GraphService, StatusCalculationService
  (MVP алгоритм пересчёта статусов)
- **Этап 4. REST API** (закрыт Сессия 5) - DTO + ручные мапперы,
  `@ControllerAdvice` Problem Details RFC 7807, OpenAPI через
  springdoc, `X-User-Id` через `@CurrentUser` (ADR-006)
- **Этап 5. Справочники и поиск** (закрыт Сессия 5) - SourceService /
  AuthorityService + REST, привязка к узлам, бизнес-валидация
  `reliability` только для HADITH
- **Этап 7. Фронтенд - MVP графа** (закрыт Сессия 17, ADR-008/009) -
  React 19 + Vite + React Flow, страницы TopicList / CreateTopic /
  TopicGraph с CRUD, side-panel деталей, редактирование, ревизии
- **Этап 8. Семантика связей** (закрыт Сессия 15, ADR-010) - матрица
  допустимых пар на бэке + фронте, контекстные подписи, toggle
- **Этап 9. Miro-подобный UX** (закрыт Сессия 16, ADR-012/013) - 4
  handles, drag-create, контекстное меню, persistence позиций
  (pos_x/pos_y), source/target handle
- **Этап 10. Редактирование рёбер** (закрыт Сессия 16, ADR-014) -
  reconnect через PATCH /edges/{id}, EdgeDetailsPanel
- **Этап 11. Визуальная полировка по дизайн-референсу** (закрыт
  Сессия 17, ADR-015/016) - status-bar слева, TypeChip, designTokens,
  nodeCount/edgeCount, GraphScreen layout
- **Этап 12. Привязка источников/авторитетов через UI** (закрыт
  Сессия 18) - AddSource/AddAuthorityModal с search+create,
  conditional render идиома, attachmentTokens.ts
- **Этап 13. Адаптация фронта под ADR-017** (закрыт частично, Сессия
  19) - Source/Authority объединены, удаление AddAuthorityModal,
  секция «Цитаты» с трёхуровневой иерархией. 13.c.2/13.d wontfix
  с приходом ADR-018 platform pivot
- **Этап 14. Library MVP** (закрыт Сессия 20, ADR-019) - миграция 16
  (lib_books/chapters/pages/image_regions), domain records + JDBC
  repos, REST `/api/v1/library/books`
- **Этап 15. Shamela import через desktop-API** (закрыт Сессия 22,
  ADR-020/021) - staging-схема (миграция 17), ApiClient + extractor +
  SQLite readers + 6 DAO, ShamelaImportService + ToLibraryMapper, 3
  admin endpoints. 296 IT
- **Этап 18.a-d, 18.f-h.B1/C1. Library frontend + CitationPicker**
  (закрыт Сессии 23-30, ADR-022/026/027) - Header + BookListPage +
  BookReaderPage, AdminShamelaPage, CitationPicker text mode,
  argument-map переключение через NodeCitationsSection с
  LibraryCite/FreeformCite. Frontend reorg в `src/apps/`+`src/shared/`
- **Этап 25.a/c/d.1/d.3. PDF Viewer source-agnostic** (закрыт частями
  Сессии 24-27, ADR-023) - PdfSourceProvider + react-pdf, cover skip,
  multi-volume dropdown, page jump, download кнопка
- **Этап 25.b. Object storage MinIO** (закрыт Сессия 28, ADR-024) -
  миграция 21 library_files, S3-compatible AWS SDK v2, ObjectStorageService
  + 4 bucket'а, streaming напрямую из MinIO. 357 IT
- **Этап 20.a/b/f. Academic citation metadata + frontend SourceCard**
  (закрыт частично, Сессии 31-32, ADR-028) - миграция 24, 3 справочника
  (Publisher/PublicationPlace/Muhaqqiq), расширение Authority + Book,
  CitationDetail + 9 LEFT JOIN, structured CitationResponse. Frontend
  SourceCard variant D «всё к правому борту» (Claude Design handoff) -
  12 атомов, RTL/LTR mix через `<bdi>`, quote `dir="auto"`.
  BookDetailResponse extended + BookHeader structured RtlRow
- **i18n minimal** (Сессия 32) - ручной dictionary ru/ar 22 keys,
  zustand store + localStorage persist + LocaleEffect синхронизирует
  `<html lang dir>`. RU/AR toggle в Header. Tailwind logical classes
  автоматически mirror'ятся
- **FK variant A для node_sources** (Сессия 32, миграция 25) -
  surrogate `id UUID` PK заменил `(node_id, source_id)`. User может
  прицепить N разных фрагментов одной книги к одному узлу. DELETE
  endpoint path меняется на `/sources/{nodeSourceId}`
- **Этап 16. Library - PDF/EPUB upload** (закрыт Сессия 37, ADR-035) -
  PDFBox 3.0.5 page-by-page extraction, `FileImportService` + `POST
  /api/v1/library/imports/file` multipart до 50MB, `library-user-uploads`
  bucket (ADR-024), `BucketBootstrap` для dev first-run. 16 IT
  (FileImportServiceIT + FileImportControllerIT через MockMvc +
  Testcontainers MinIO). EPUB отложен - нет UX-кейса. Backend
  верифицирован 537/537. **16.f frontend** - admin `FileUploadModal`
  на `/admin/shamela` (третий Card блок + `apiPostMultipart` helper +
  локализованный mapping ошибок 413/415/422), 5 vitest. Дизайн временный
  до появления полноценного user-facing UX-референса. **16.g** -
  закрыт feature gap: endpoint расширен 6 academic полями
  (`muhaqqiqName`/`publisherName`/`publicationPlaceName`/`editionNumber`/
  `publishedYearHijri`/`publishedYearGregorian`) с ручной range валидацией,
  Backend 543 IT pass. Frontend - collapsible секция через shared
  `<AcademicMetadataFields/>` (тот же что в 20.e), 156 vitest. Mirror
  паттерна AddSourceModal 20.e (2-step flow): пользователь больше не
  должен после upload вторым шагом открывать BookEditModal. **16.h** -
  post-review critical fix (Сессия 38): `UserUploadProvider` (order=50)
  - до этого uploaded PDF был в MinIO + library_files, но не читаем
  через `PdfService` (единственный `PdfLinksSourceProvider` смотрел
  только metadata.pdf_links). Теперь после upload книга сразу
  доступна через `/api/v1/library/books/{id}/pdf/info` и `/pdf`.
  +9 UserUploadProviderIT + 1 E2E в FileImportControllerIT (всего 554)
- **Этап 17. Library - image-сканы + OCR + rich text editor + AI editing**
  (закрыт Сессии 41-43, ADR-039/041/042) - **17.0** Tiptap 3.23
  + 8 custom extensions (HadithBox/AyahBox/Marginalia/Footnote/
  ColorHighlight/Tashkeel/DecoratedHeading/PageNumber), миграция 33
  formatted_content jsonb, AdminPageEditorPage с toolbar, 43 schema
  tests. **17.a** PageImageService + POST `/library/books/{id}/pages`
  multipart, MinIO `library-page-images` bucket, миграция 34 6 nullable
  колонок. **17.b** Tess4j 5.13.0 + OcrService через @Async
  ocrTaskExecutor (core=2/max=4), state machine PENDING/PROCESSING/
  DONE/FAILED, POST `/pages/{id}/ocr` + GET polling. Tesseract -
  system dependency (apt install tesseract-ocr-ara). **17.c**
  ImageRegion API (3 endpoints, normalized 0..1 + Bean Validation).
  **17.d** re-OCR через existing endpoint (idempotent на state machine).
  **17.e** AI editing pass через Anthropic Claude (claude-sonnet-4-6)
  - миграция 35 ai_edit_status fields, POST `/pages/{id}/ai-edit` +
  GET polling, 503 если ANTHROPIC_API_KEY не настроен, prompt template
  в resources/prompts/ai-edit-tahqiq.txt, AiEditConfig dedicated pool,
  20+ tests (validation + stub HttpServer + service IT + controller IT
  + опциональный live). **17.e.f** Frontend: useAiEdit hook (polling
  каждые 3 сек, max 5 мин hard timeout, AbortController cleanup,
  ApiError 503 → toast), кнопка «AI редактирование» (Wand2 icon,
  indigo accent) в AdminPageEditorPage toolbar, processing overlay
  с counter + cancel, callback применяет formattedContent в editor
  и в state (isFallback hint исчезает после success). 10 i18n keys
  ru/ar, 5 useAiEdit тестов (vi.useFakeTimers). 304 tests pass.
  **17.f** ADR-041 + ADR-042. ImagePageRenderer (18.e) - отдельный
  пункт в Этапе 18
- **Responsive Фаза 1+2** (закрыто Сессии 39+40) - mobile/tablet
  адаптация UI. Фаза 1: `useIsMobile` hook, Modal full-screen,
  NodeDetailsPanel overlay, Header drawer, Select adaptive max-height.
  Фаза 2 (10 точек): BookReaderPage chapters drawer + fullscreen PDF
  preview, sticky `dvh`, PdfViewer toolbar 2-row stack, list/create
  страницы mobile padding, AdminShamelaPage table horizontal scroll +
  StatusStrip col-span, CitationPicker 3-tab switcher, AcademicMetadata
  1-col grid, filter chips overflow-x-auto. 0 horizontal scroll на
  375px на всех тронутых страницах. 179 tests pass

---

## Активные этапы

### User feedback Сессии 38 (закрывается в Сессии 39)

- [x] **#1:** root узел темы защищён от удаления - backend
      `NodeIsRootException` 409 (`NodeService.deleteNode` guard
      по `topic.root_node_id`), frontend скрывает «Удалить» в
      context menu для корня + bulk-delete фильтрует root +
      toast.warning
- [x] **#2:** Alt+K layout-independent - решено через `useHotkey`
      wrapper над `react-hotkeys-hook` (useKey:true → event.code)
- [x] **#3:** Del/Backspace handler в `TopicGraphPage` мигрирован
      с временного useEffect на `useHotkey('delete,backspace', ...)`
      в `GraphCanvas` - после унификации
- [x] **#4:** ⌘+↵ submit в FormModal через `useHotkey('mod+enter',
      formRef.current?.requestSubmit, { enableOnFormTags: true })`.
      `<ShortcutHint keys="mod+enter">` показывает ⌘ на Mac / Ctrl
      на Win/Linux. Хардкодные `<Kbd>⌘</Kbd>` в AddNodeModal/
      AddEdgeModal убраны. ADR-036 + миграция 17+ существующих
      keydown handlers на единую систему
- [x] **#5:** shamela 502 → локализованный toast вместо сырого
      Problem Details с замаскированным api_key. Mapping по
      `problem.type` (`shamela-api-error` / `-archive-error` /
      `-reader-error`)
- [x] **#6:** диагностика шрифта title книг в `BookListPage` -
      `--font-book-title` в `tokens.css` уже Manrope (не EB Garamond
      как обещает комментарий), но Google Fonts в WSL2 blocked 407
      proxy → нулевая загрузка любых web-fonts, всё падает в
      system serif/sans fallback. Решение по факту font'а - за
      Абдулой (см. диагностический коммит)
- [x] **#7 (Сессия 39):** UX unification удаления узлов - context
      menu и Del/Backspace теперь идентичны: silent delete +
      `toast.success` с действующей кнопкой «Отменить» на 3 сек
      (паттерн Gmail/Slack). `window.confirm()` убран полностью.
      Undo восстанавливает узел через POST `/api/v1/nodes` (новый
      id, без edges - tooltip-hint предупреждает). Toast action API
      расширен опциональным `hint`. ui-guidelines дополнены
      секцией «Destructive actions»

### Этап 6. Улучшения бэкенда (после MVP, не блокирует другие)

- [x] **Этап 6 закрыт** - JSON export/import (Сессия 39, ADR-037), Dung's
  framework (Сессия 38, ADR-044, миграция 41 + `topics.status_algorithm`
  + `DungFrameworkService` grounded labelling + `PATCH /api/v1/topics/{id}
  /status-algorithm`). Полнотекстовый поиск отложен через отдельный
  Elasticsearch сервис (см. `docs/backlog.md` «Архитектурные решения»).
  Frontend UI toggle алгоритма - в backlog

### Этап 18. Library frontend - оставшиеся подэтапы

Основное закрыто (см. выше в закрытых). Что осталось:

- [ ] **18.e: ImagePageRenderer** - отдельный mode для image-сканов:
      картинка + overlay для OCR-текста + рисование regions через
      react-image-crop. Релевантно после Этапа 17
- [ ] **18.h.A1 (deferred):** NodeCard footer chips в графе -
      раздельный count library vs freeform на самой карточке узла.
      Требует backend NodeResponse расширение
      (citationLibraryCount + citationFreeformCount через aggregate
      JOIN в NodeRepository). Откладывается - duplicate данные с
      header meta-row

### Этап 19. Q&A - первое полностью новое приложение

**Зачем:** проверить платформенность фундамента. Если library
позволяет легко собрать новое приложение - архитектура работает

- [x] **19.a:** Q&A foundation (ADR-032). Migration 26 `questions`
      table + Question domain + QuestionStatus enum (OPEN/ANSWERED/
      CLOSED). REST CRUD под `/api/v1/questions` (POST/GET list с
      filters status/q/ GET/{id}/PATCH/DELETE). Frontend `src/apps/qa/`:
      QuestionListPage с status filter + search, CreateQuestionPage
      (Field + maxLength counters), QuestionDetailPage с status switcher
      + delete. Header nav «Q&A» enabled. 30 i18n keys RU/AR
- [x] **19.b:** Source attach - `question_sources` table (migration 28
      объединила 9+23+25 в одну) + REST `POST /api/v1/questions/{id}/citations`
      + `GET /{id}/sources` + `DELETE /sources/{questionSourceId}`.
      Параллельная иерархия (ADR-033) в `qa/` package - `QuestionSource`/
      `QuestionSourceRepository`/`QuestionCitationService`/
      `QuestionCitationController` mirror `node_sources` stack.
      Frontend - `CitationPicker` расширен `targetType: 'nodes' | 'questions'`
      prop, `QuestionCitationsSection` использует тот же `SourceCard`.
      18 IT тестов pass. Smoke playwright подтвердил identical UI
      rendering structured citation на question detail page
- [x] **19.c:** Answers + accept-answer flow (ADR-034). Migration 29
      `answers` table + migration 30 `questions.accepted_answer_id`
      nullable FK ON DELETE SET NULL. REST endpoints под `/api/v1`:
      POST/GET `/questions/{id}/answers`, PATCH/DELETE `/answers/{id}`,
      POST/DELETE `/questions/{id}/accepted-answer/{answerId}`.
      `AnswerResponse` с derived `accepted: boolean`. 20 IT тестов
      через Testcontainers (create / list ordered / accept / revoke /
      cascade / SET NULL). Frontend - `AnswersSection.tsx` с inline-
      формой добавления + AnswerCard с ribbon «Принят», кнопками
      «Принять» (для asker) и «Удалить» (для author). 12 i18n keys
      RU/AR. Voting + comments в backlog как отдельные этапы
- [x] **19.d:** Answer sources - параллельная иерархия `answer_sources`
      (ADR-033 итерация 3). Migration 31 mirror migration 28 - тот же
      шаблон (surrogate UUID PK + positional fields + CHECK constraint
      один-из-четырёх + 5 индексов), FK на `answers(id) ON DELETE CASCADE`.
      Backend: `AnswerSource` record + `AnswerSourceRepository` (с alias
      `ansrc` - `as` reserved keyword) + `AnswerCitationService` + 3
      REST endpoint под `/api/v1/answers/{id}/{citations|sources}`.
      `QaDtoMappers.toResponse` перегружен по типу - один класс на оба
      flow. Frontend: `CitationPicker` расширен `targetType: 'answers'`,
      новый `AnswerCitationsSection` mirror `QuestionCitationsSection`,
      встроен в `AnswerCard` collapsed-by-default через toggle.
      19 IT тестов pass (mirror 18 от 19.b + extra empty list test).
      Smoke playwright подтвердил identical citation rendering на answer
      level. **ADR-033 паттерн валидирован 3 раза подряд - platform
      pivot масштабируется без перехода на generic citations table**

### Этап 20. Полная академическая citation metadata - продолжение

20.a/b/f закрыты (см. выше). Остаётся:

- [x] **20.c:** Shamela bibliography parser - regex-based extraction
      из `lib_shamela_book.bibliography` (мухаккик/издатель/место/edition/
      год хиджры+григориан). `ShamelaBibliographyParser` + интеграция в
      `ShamelaToLibraryMapper.mapBook` через `findOrCreate` в Muhaqqiq/
      Publisher/PublicationPlace репозиториях. 12 unit-тестов с реальными
      фикстурами (CR character separator + literal escape variant).
      `POST /api/v1/admin/shamela/backfill-bibliography` для existing
      books через `ShamelaBibliographyBackfillService` (non-destructive
      merge). Smoke 3/3 dev-книг получили заполненные FK
- [x] **20.d:** Admin BookEditModal - модалка с 6 полей (Field primitive)
      + 3 inline autocomplete с debounced fetch (250ms + AbortController
      cancel). Backend `PATCH /api/v1/library/books/{id}` через
      `UpdateBookRequest` (PATCH-семантика: null=no change, ""=clear,
      non-empty=findOrCreate). 3 autocomplete endpoints
      `GET /api/v1/library/{muhaqqiqs, publishers, publication-places}?q=&limit=`.
      Frontend - Pencil icon в углу карточки в BookListPage + кнопка
      «Перечитать metadata» в AdminShamelaPage (вызывает backfill).
      Smoke: тафсир Ибн Касира prefilled all 6 полей
- [x] **20.e:** AddSourceModal extended form - при manual entry для
      `sourceType=BOOK` показывается shared `<AcademicMetadataFields/>`
      (6 полей муhaккик/издатель/место/edition/год хиджра/григориан).
      Backend `CreateBookRequest` + `CreateSourceRequest` расширены
      (`bookId` UUID, 6 academic optional). `BookService.createBook`
      перегружен с findOrCreate в справочниках. 2-step UI flow: при
      заполненном academic - POST `/api/v1/library/books` → POST
      `/api/v1/sources` с `bookId` → attach. Legacy single-step без
      `bookId` работает как раньше. BookEditModal мигрирован на shared
      компонент. 9 backend IT + 4 frontend Vitest

Объём 20.c-e: ~2 сессии. Не блокирует Этап 19 Q&A

### Этап 25. PDF Viewer - operational hardening + полировка

Основные подэтапы закрыты (см. выше в закрытых). Остаётся:

- **25.b. operational hardening** - незакрытые пункты из ADR-024:
  - [x] **Circuit breaker через Resilience4j** (Сессия 36) -
        `pdfDownload` instance защищает `HttpClientPdfFetcher.fetch()`,
        50% failure threshold за окно из 10 запросов → OPEN 30 секунд →
        HALF_OPEN 3 пробных → CLOSED. Fallback кидает
        `ShamelaApiException` без upstream HTTP. 4 IT тесты pass.
        Actuator endpoints `/circuitbreakers` + `/circuitbreakerevents`
        для observability
  - [x] **Health-check indicator** (Сессия 36) - `ObjectStorageHealthIndicator`
        implements `HealthIndicator`, делает `HeadBucket` на primary bucket
        `library-imported-books`. UP с {endpoint, bucket, latencyMs} details,
        DOWN с statusCode/errorCode при S3 ошибке. Auto-discovered Spring
        Actuator под ключом `objectStorage` в `/actuator/health`. 2 IT тестов
        с MinIO testcontainer. Используется load balancer / k8s readiness probe
  - [x] **Orphan-detection janitor** (Сессия 36) - `OrphanDetectionJanitor`
        `@Scheduled` cron `0 0 3 * * *`. Forward sweep:
        `listObjectsV2Paginator` per bucket → проверка
        `findActiveByBucketAndKey`. Reverse sweep: `findAllActive` →
        `headObject` per row. Log-only через `log.warn` с (type, bucket,
        key, size, age) - manual review через логи. Conditional
        `storage.janitor.enabled` (default false). 6 IT тестов с MinIO
        testcontainer: matched/s3-only/catalog-only/soft-deleted/multi-
        bucket/mixed
  - [x] **Integrity verification cron** (Сессия 36) -
        `IntegrityVerificationJob` `@Scheduled` cron `0 0 4 * * SUN`
        (воскресенье 04:00, weekly). `findAllActive` → для каждой row
        `getObject` + streaming SHA-256 через `MessageDigest` → сравнение
        case-insensitive с `content_hash`. Mismatch → `log.error`
        CORRUPTION; `NoSuchKey` → `log.warn` MISSING (consolidated report
        через `OrphanDetectionJanitor`). Throttle между files
        `storage.integrity.delay-millis` (default 100ms, 0 в тестах).
        Conditional `storage.integrity.enabled` (default false). 6 IT
        тестов с MinIO testcontainer: healthy/corrupted/missing/soft-
        deleted-skipped/mixed/case-insensitive-hash
  - [x] **AWS SDK v2 migration `RetryPolicy` → `RetryStrategy`** (Сессия 37) -
        deprecated `software.amazon.awssdk.core.retry.RetryPolicy` заменён
        на современный `software.amazon.awssdk.retries.api.RetryStrategy`
        через `AwsRetryStrategy.standardRetryStrategy()`. Семантика
        сохранена: exponential backoff с jitter + retry на 5xx /
        throttling / connection reset. `maxAttempts = maxRetries + 1`
        (новый API считает initial attempt частью лимита, legacy
        `numRetries` нет). `apiCallTimeout` split (см. ниже) не тронут
  - [x] **`StreamingResponseBody` bounded `ThreadPoolTaskExecutor`**
        (Сессия 36) - `AsyncWebConfig` устанавливает `ThreadPoolTaskExecutor`
        как default для async MVC: core=10, max=50, queue=100, keepAlive=60s,
        `CallerRunsPolicy` для back-pressure (вместо OOM thread exhaustion),
        async timeout=5мин. Микрометр метрики автоматически в `/actuator/metrics/executor.*`
  - [x] **`apiCallTimeout` split** (Сессия 36) - `apiCallAttemptTimeout`
        = `readTimeout` (per single attempt), `apiCallTimeout` =
        `readTimeout × (maxRetries + 1) + 50% jitter` (total wall-clock
        budget включая backoff между retries). Раньше overall = single
        attempt → retries не успевали пройти. Логирование вычисленных
        timeouts в startup для observability
- [ ] **25.d.2: text↔pdf page sync** - internal pageNumber →
      pdfPageNumber mapping с fallback на physical=internal если null.
      Требует Tier 1 admin page-mapping flow
- [ ] **25.d.4: Inline PDF preview redesign** - кардинальное
      переустройство reader'а. Вместо tab toggle Text/PDF - кнопка
      PDF на каждой странице text mode, при клике открывается inline
      preview PDF этой страницы внизу. См. `shamela_page_view.png` +
      `after_click_on_pdf_icon_shamela.png` в design-reference
- [x] **25.d.5: Lazy streaming через backend** (Сессия 39, ADR-023
      Amendment) - `PdfSourceProvider.openStream(book, fileIndex,
      RangeSpec)` стал primary read path. UserUpload через MinIO
      native Range. PdfLinks: cache hit MinIO Range, cache miss + range
      lazy forward к archive.org (HTTP Range header), cache miss +
      null range синхронный fill через `locateFile()` (legacy для
      admin smoke). `RangeNotSatisfiableException` → 416 Problem
      Details. Первый Range запрос на 135MB книгу теперь 1-2 сек
      вместо 30 сек. MinIO tee при cache miss + range отложен -
      второй итерацией если будет реальный production traffic.
      17 IT (575→592 backend pass)
- [ ] **25.e:** admin manual page-mapping (Tier 1, опционально)
- [ ] **25.f:** region selection через react-image-crop +
      `POST /api/v1/library/pages/{id}/regions` (после Этапа 17)

### Этап 21+. Аутентификация и далее

- [x] **Этап 21 (Сессия 41, ADR-040) - auth end-to-end:** backend Spring
      Security 6 + JWT (jjwt 0.12.6) + BCrypt + миграция 32 users ALTER
      (password_hash/role/enabled) + AuthController (register/login/refresh/
      logout/me) + httpOnly+Secure+SameSite=Strict refresh cookie +
      JwtAuthenticationFilter + XUserIdAuthenticationFilter dev fallback +
      DevUserSeeder (admin@argumentmap.local/admin12345). Frontend AuthStore
      (Zustand, persist user) + apiClient Bearer interceptor + refresh-on-401
      с dedup queue + LoginPage/RegisterPage (hero-style AuthShell) +
      ProtectedRoute + AdminRoute (requireRole) + Logout flow в AvatarMenu +
      Vite proxy /api+/actuator для same-origin cookies. 36 frontend tests +
      30+ backend IT (246 frontend + 605 backend total). Transitional в
      dev/test: GET /api/** остаётся permitAll - покрывает 60+ existing IT
      без переписывания. В prod profile GET тоже authenticated()
- [x] **Этап 22 (Сессии 42-44, ADR-043 + Amendment) - RBAC permissions
      per-entity:** topics (22.a backend + 22.b frontend) + library books
      (22.c backend + 22.c.f frontend) + Q&A author guards. Hybrid visibility
      model (PRIVATE/SHARED/PUBLIC) + members M:N (MEMBER/EDITOR) + ADMIN
      bypass через PermissionService service-layer ассерты. Миграции 36
      (topic_members) + 37 (lib_books.visibility + lib_book_members). REST
      POST/GET/PATCH/DELETE `/api/v1/{topics|library/books}/{id}/members`
      + PATCH `/visibility`. 403 Problem Details
      `forbidden-{topic|book|answer|question}-{access|write}`. Q&A без
      visibility - только author/admin guards. Frontend: VisibilityRadioGroup
      + VisibilityBadge в shared (reuse topics+books), Topic/BookMembersModal,
      visibility radio в CreateTopicPage/BookEditModal, badge на Topic/BookList
      cards + Topic/BookReader headers с change/manage кнопками для owner,
      hiding write actions, permissionErrors helper. Total: backend 733+
      tests, frontend 333 (Topic/BookMembersModal по 5 + CreateTopicPage +3)
- [x] **22.d (Сессия 37, ADR-043 Amendment 3) - audit log per-entity:**
      миграция 39 + `AuditLogService` synchronous в той же транзакции
      что и mutation. Integration во все mutation-сервисы (TopicService,
      NodeService, EdgeService, BookService, QuestionService, AnswerService,
      TopicMemberService, BookMemberService) - 8 действий
      (CREATE/UPDATE/DELETE/VISIBILITY_CHANGE/MEMBER_ADD/MEMBER_REMOVE/
      MEMBER_ROLE_CHANGE). REST `GET /api/v1/audit/{topics|books}/{id}`
      (owner+EDITOR), `/audit/me` (свои), `/audit/admin` (ADMIN only с
      entityType/actorId/dateFrom/dateTo фильтрами). `PagedResponse<
      AuditLogResponse>` с username bulk-JOIN. Backend 770 tests +16.
      Private Q&A visibility model и admin UI - отложены в 22.e/backlog
- [x] **22.e (Сессия 37, frontend) - admin audit UI:** AdminAuditPage
      `/admin/audit` под ProtectedRoute requireRole="ADMIN". Table
      с timestamp / entity_type / entity_id / action badge (color-coded
      emerald/blue/rose/purple/amber) / actor_username / parent / view-
      details. FilterBar (native <select>): entityType / action /
      actorId / dateFrom / dateTo - Apply копирует draft→applied state,
      триггерит refetch с query params (action client-side т.к. бэк не
      принимает). DetailsModal - pretty-printed JSON changes через
      JSON.stringify(JSON.parse,null,2), parse error fallback. Load More
      pagination как в TopicListPage. Nav-link в AdminShamelaPage
      overflow menu (••• → "Audit log"). ~40 i18n keys RU/AR. Tests +5
      (362 total). Этап 22 полностью закрыт (a/b/c/c.f/d/e)
- [ ] **22.f (backlog):** Private Q&A visibility model (visibility +
      members для questions/answers) если понадобится для закрытых
      учёных групп + audit retention policy janitor (cron cleanup
      >6 месяцев когда хранилище подскочит)
- [ ] **23+:** Open list - sanad explorer, multi-grading, RTL UI,
      экспорт PDF/SVG, mobile, advanced search. См. `docs/backlog.md`
      раздел «Будущие фичи»

### Этап 49 (Сессия 49d vision expansion) — большие фичи в planning

Запрошены Абдулой в начале Сессии 49d (2026-05-20). Полное описание —
в `docs/superpowers/specs/2026-05-20-vision-expansion-49d.md`. Отдельные
design-specs создаются по мере приоритезации.

- [ ] **49.A: Roles ADMIN/SCHOLAR/STUDENT/USER** - расширение `users.role`
      enum, AuthorizationService, route guards. Spec: `docs/superpowers/
      specs/2026-05-20-roles-system-design.md` (572 строки, ready для
      implementation). Effort ~19.5h. Subphases 49.a-49.j.

- [ ] **49.B: Rating + pagination** для Topics/Q&A/Library - sorting
      by popularity, view tracking, optional vote system. Spec: `docs/
      superpowers/specs/2026-05-20-rating-pagination-design.md` (в
      работе subagent'ом)

- [ ] **49.C: Hadith Chains Explorer (BIG)** - новое приложение под
      `src/apps/hadith/` (ADR-018 platform pivot validation). Visualization
      sanad через React Flow, narrator database, matn variations, AI
      assist phase 2. Spec: `docs/superpowers/specs/
      2026-05-20-hadith-explorer-design.md` (в работе subagent'ом).
      Объём — 5-10 sessions.

- [ ] **49.D: Observability** - structured logging + Prometheus metrics
      + OpenTelemetry tracing + frontend error reporting. Spec: `docs/
      superpowers/specs/2026-05-20-observability-design.md` (в работе
      subagent'ом)

- [ ] **49.E: Library collections + favorites** - библиотека становится
      общей (PUBLIC default), пользователи добавляют книги в personal
      collections. Новая таблица `user_book_collections`. Спек пока не
      создан — vision spec Section 2.2

- [ ] **49.F: Shamela full search** - расширенный поиск (автор,
      категория, направление), категории парсятся в `lib_shamela_categories`,
      possible Elasticsearch. Спек пока не создан — vision spec
      Section 2.3

- [ ] **49.G: Guest view** - анонимный доступ для просмотра public
      Topics / Books / Q&A. Public GET endpoints permitAll в prod,
      auth-aware UI hides write actions. Спек пока не создан — vision
      spec Section 2.5

---

## Cross-cutting / инфраструктура

Сквозные куски кода не привязанные к одному этапу. Каждый с
пометкой «введён в этапе X»

- **Modal** (`shared/components/ui/Modal.tsx`) - на нативном
  `<dialog>` с focus trap, Escape, role=dialog. Введён в **этапе 7**
- **Toast** (`shared/stores/toastStore.ts` +
  `shared/components/ui/Toaster.tsx`) - Zustand 4 типа
  (`error`/`warning`/`info`/`success`), auto-dismiss. API
  `toast.warning('...')` без хука. Введена в **этапе 9**
- **ContextMenu** (`shared/components/ui/ContextMenu.tsx`) -
  универсальный компонент для правых кликов с поддержкой header,
  danger-пунктов, иконок lucide. Введён в **этапе 9**
- **Select** (`shared/components/ui/Select.tsx`) - custom dropdown
  с centered options, manual collapse override, scrollIntoView для
  active. Введён в **этапе 25.c** (Сессия 27)
- **PageJump** (`apps/library/...`) - input для прямого ввода
  pageNumber с key-trick для sync. Введён в **этапе 18.d**
- **NodeSelect** (`apps/argument-map/components/`) - custom
  dropdown с lucide-иконками типа узла + цветной dot статуса.
  Введён в **этапе 9/15**

## Бэклог

См. `docs/backlog.md` - идеи и задачи без привязки к этапу:
responsive/mobile адаптация, будущие фичи исламского контекста
(sanad, multi-grading, RTL и др.), мелочи бэка
