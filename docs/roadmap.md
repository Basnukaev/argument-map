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
> `docs/audits/2026-05-11-codebase-audit.md`

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
- **Предпрод UX-overhaul + content-tooling** (закрыт Сессия 54, 17 коммитов,
  спека `docs/specs/2026-06-02-preprod-ux-overhaul.md`) — 8 фаз по
  13 болям Абдулы: SWR-кэш данных (мгновенная навигация), единый ListControls,
  redesign чтения хадиса/Q&A, settings drawer + UI-scale (дефолт 0.9), карточки
  библиотеки, голосование node→topic (ADR), **overhaul админки + Sunnah
  dry-run import-preview** (поэтапное проверяемое наполнение), Alt+K/модалка perf.
  backend BUILD SUCCESS, frontend build/tsc/686 tests ✓
- **Сессия 54 продолжение (62 коммита всего)** — баги ручного теста, бэклог
  (hd_collections мост ADR-054, shamela ADMIN-guard, topic/question/answer votes),
  14 Tier-3 (security/correctness/concurrency), 2 code-review (0 Critical),
  d3-drag флак → CI зелёный (frontend 678/0/0). **archive.org PDF-импорт**
  (спека `2026-06-02-archive-org-pdf-import-design.md`, ADR-056): parser +
  gap-aware preview + import + dual-variant pdf_links + обложки + парсинг arabic
  description. **Итерации archive.org** (фоновое извлечение всех томов,
  volume-dropdown, eager-UI, relabel) — open, спека §10. migrations через 67.
- **Сессия 55 — overhaul (7 фаз + code-review, ~14 коммитов)** — спека
  `docs/specs/2026-06-02-session-55-overhaul.md`. **OCR выпилен полностью**
  (ADR-057, migration 68; Этап 17 OCR-часть отменена, AiEdit+image-upload сохранены);
  **swappable LLM** (ADR-058, пакет `ai/`, anthropic/openai/deepseek + BookMetadataExtraction);
  **content_kind** (migration 69, TEXT_ONLY/TEXT_AND_FILE/FILE_ONLY — ось доступности
  ортогональна book_type); **archive.org→FILE_ONLY** (drop `_text`, HTML-стрип, AI-метаданные,
  лок формы; ADR-056 amend); **reader** bbox-подсветка + 0-page guard; **hadith** availableHadith
  + panel scroll + alminasa reframe; **AI-иснад** (ADR-059, IsnadExtractionService + live
  preview-граф, эфемерный). Code-review 0 Critical / 1 Important (@Retry bypass) + 9 Minor закрыты.
  backend BUILD SUCCESS, frontend 708/0/0. migrations через **69**.
- **Этап 6. Улучшения бэкенда** (закрыт Сессия 39, ADR-037/044) — JSON
  export/import, Dung's framework (миграция 41 + `topics.status_algorithm`
  + `DungFrameworkService` grounded labelling + `PATCH /status-algorithm`).
  FTS (Elasticsearch) и UI-toggle алгоритма — в backlog
- **User feedback Сессии 38** (закрыт Сессия 39, ADR-036) — 7 UX-фиксов:
  защита root-узла от удаления (`NodeIsRootException` 409), Alt+K/Del/
  Backspace/⌘+↵ через единый `useHotkey`, shamela 502→локализованный
  toast, диагностика шрифта BookListPage, unified silent-delete+undo
  (window.confirm убран). Детали — progress.md Сессии 38-39
- **Этап 19. Q&A — первое новое приложение под ADR-018** (закрыт Сессии
  ~30-32, ADR-032/033/034) — миграции 26/28/29/30/31:
  questions+answers+accepted_answer_id, параллельные иерархии
  question_sources/answer_sources (ADR-033 валидирован 3×), accept-answer
  flow, frontend `src/apps/qa/` (List/Create/Detail + AnswersSection +
  CitationPicker targetType nodes/questions/answers). Voting/comments —
  backlog. Детали — progress.md
- **Этап 20. Академическая citation metadata** (закрыт Сессии 31-32,
  ADR-028) — 20.a/b/f выше; 20.c ShamelaBibliographyParser + backfill
  endpoint, 20.d admin BookEditModal (`PATCH /library/books/{id}` + 3
  autocomplete), 20.e AddSourceModal extended (shared
  AcademicMetadataFields, 2-step BOOK flow). Детали — progress.md
- **Этап 21. Auth end-to-end** (закрыт Сессия 41, ADR-040) — Spring
  Security 6 + JWT (jjwt) + BCrypt, миграция 32 users ALTER, AuthController,
  httpOnly refresh cookie, frontend AuthStore + refresh-on-401,
  Login/Register, ProtectedRoute/AdminRoute. Детали — progress.md
- **Этап 22. RBAC permissions + audit** (закрыт Сессии 37/42-44, ADR-043
  + Amendments) — hybrid visibility PRIVATE/SHARED/PUBLIC + members M:N
  для topics/books (миграции 36/37), Q&A author-guards, 22.d audit log
  (миграция 39, синхронный в транзакции, 8 действий), 22.e AdminAuditPage
  `/admin/audit`. Открытые остатки — 22.f (backlog) и 23+ ниже. Детали —
  progress.md

---

## Активные этапы

### Этап 31. Прод-готовность (PROD-READINESS-AUDIT.md)

Аудит `PROD-READINESS-AUDIT.md` (2026-06-18). **Контент наполняется на
проде** (корпус — реимпорт на проде; курация overlay — `pg_dump
hd_field_overrides` или правка на проде; темы — JSON-экспорт ADR-037);
предусловие — бэкап/restore.

- [x] **P0-1 / P0-1a / P1-1 / P1-3** — ЗАКРЫТЫ эпиком курации (С65): защита
      правок от реимпорта, перевод, manual-edit API hd_*-полей, admin
      record-editor UI. (Аудит писался ДО курации.)
- [x] **P0-3** (С65, `5ede8f6`) env-плейсхолдеры DB-кредов: default-doc
      datasource из `${SPRING_DATASOURCE_*}` без fallback (fail-fast) +
      `DatasourceConfigValidator` (prod+localhost/argmap → IllegalStateException)
      + required-env в auth-security.md. local boot verified.
- [x] **P1-2** (С65, `f0a6cee`) data-health `GET /admin/hadith/health`
      (ADMIN, 9 counts: nullAuthenticity 2228, withoutSanad 996, nullTabaqa
      2404, unknownReliability 2913, ...; 2 FILTER-запроса). Live verified.
- [x] **P1-4** (С65, `4ef4253`) member-list только authenticated: carve-out
      `/{topics,books}/{id}/members` из guest-permitAll (был leak user-UUID
      PUBLIC-темы анониму). ADR-064 amendment. GuestAccessProdProfileIT 14/14.
- [x] **P1-5** (С65, `77a3a4d`) CORS verified SAFE (doc-only): env-origins
      fail-closed, no wildcard, allowCredentials(false) т.к. same-origin.
- [x] **P0-3 regression fix** (`23328f8`): DatasourceConfigValidator gated
      `app.datasource.prod-guard` (matchIfMissing=true; opt-out в 3 prod-profile
      IT) — ломал их context-load. Прод-safety сохранён (тест доказывает throws).
- [ ] **P2-1/2/3/4** generic 500-handler, include-stacktrace, AI-translate
      rate-limit, mark-as-reviewed/bulk
- **🚚 remblo (деплой/ops, отдельный репо):** P0-2 бэкап/restore БД (pg_dump
      по расписанию + restore-ранбук), CI, прод-env, имя prod-профиля
      (валидатор P0-3 завязан на `prod`).

### Этап 30. Курация данных — overlay hd_field_overrides (ADR-065) ✅ ЗАКРЫТ (С65)

P0-1 (реимпорт затирает правки) + FB-5 (править/скрывать вторичные
данные при защите первоисточника). Спека:
`docs/specs/2026-06-18-data-curation-overlay.md` (фазы 0-6).
**Весь эпик (фазы 0-6) закрыт в С65, 4 независимых review — все APPROVE.**
Остаток (не блокеры): Фаза 5.b (sanad-UI, transmission_phrase),
effective-facet JOIN (§10), пара Minor из ревью Фазы 6 — в backlog/коде.
(детали фаз ниже — свернуть в строку при следующей доc-гигиене)

- [x] **P0-1a** merge-страховка перевода матна (Сессия 64-cont, `f7b6fb5`)
- [x] **Фаза 1** схема + repo + домен (Сессия 65) — миграция 78
      `hd_field_overrides`, `OverrideEntity`/`FieldOverride`/
      `CurationWhitelist`, `OverrideRepository` (upsert/batch/delete),
      ADR-065. 15 тестов (whitelist unit + repo IT, CHECK/UNIQUE)
- [x] **Фаза 2** (Сессия 65) apply-слой `OverrideApplyService` +
      `OverrideSet` (каст-помощники §3.4) + репозиторный fold в
      `findById/findPage/findByIds/findBySourceIds` хадиса/рави
      (НЕ в `findByExternalId`/dedup — RAW для импорта). 12 тестов
      (unit helpers + pure apply + IT effective-vs-raw)
- [x] **Фаза 3** (Сессия 65) generic write-API `PUT/DELETE/GET
      /admin/curation/overrides` (ADMIN, audit, 7 error-types) +
      frontend `EditableField` (пилот hadiths+narrators). Независимый
      review: **APPROVE, 0 Critical/Important, 8 Minor** (дешёвые
      закрыты, остальное → §10/Фаза 5). Live-смоук + Playwright render.
      **← review-чекпоинт ПРОЙДЕН**
- [x] **Фаза 4** (Сессия 65) record-level hide/reveal: backend
      `OverrideApplyService.applyRecordHide` (читатель — запись вырезана,
      ADMIN — `hiddenByAdmin`+reason для раскрытия, §4.3) на rulings/
      explanations/commentaries; DTO +id/hiddenByAdmin/hideReason; frontend
      `HideToggle` (EyeOff/Eye + reason-модалка + пилюля). Независимый
      review: **APPROVE, 0 Crit/Imp, 4 Minor** (граница STUDENT-cut добавлена).
      Live-смоук + Playwright. matns/sanads record-hide — Фаза 5
- [x] **Фаза 5** (Сессия 65) field-edit + hide на сателлитах: backend
      5 apply()-методов (ruling/explanation/commentary/matn/sanad) +
      `applyAndHide` (field-edit+record-hide одним батч-load); matns/sanads
      теперь effective+hideable; frontend `CurationFieldsPanel` на rulings/
      explanations/commentaries/matns. Review: **APPROVE 0 Crit/Imp, 2 Minor**
      (позиц. корректность 5 рекордов verified field-by-field). **5.b отложено:**
      sanad-UI (только в RF-графе) + `hd_sanad_narrators.transmission_phrase`
      (композитный ключ) + `ExplanationDto.author_death_year` не surface'ится
- [x] **Фаза 6** (Сессия 65, `1d5017c`+`6a9e2eb`) C9-перевод матна → overlay
      по СТАБИЛЬНОМУ ключу `(hadith_id, is_primary)` (синтетические
      `primary_text_ru/en` на entity_id=hadith_id); migration 79 (idempotent,
      non-destructive); C9 `editTranslation` пишет overlay; P0-1a снят
      (`findPrimaryByHadithId` удалён). Review **APPROVE 0 Crit/Imp, 2 Minor**
      (guard primary_text_* на generic-эндпоинте закрыт). HEADLINE-IT:
      перевод выживает реимпорт. **ЭПИК ЗАКРЫТ.**

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

### Этап 25. PDF Viewer - operational hardening + полировка

Основные подэтапы закрыты (см. выше в закрытых). Остаётся:

- [x] **25.b. operational hardening** (Сессии 36-37, ADR-024) —
      Resilience4j circuit breaker `pdfDownload`,
      `ObjectStorageHealthIndicator`, `OrphanDetectionJanitor` +
      `IntegrityVerificationJob` (@Scheduled, conditional), AWS SDK v2
      `RetryStrategy`, bounded async `ThreadPoolTaskExecutor`,
      `apiCallTimeout` split. Детали — progress.md Сессии 36-37
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

Этапы 21 (auth) и 22 (RBAC + audit) закрыты (см. выше). Остаётся:

- [ ] **22.f (backlog):** Private Q&A visibility model (visibility +
      members для questions/answers) если понадобится для закрытых
      учёных групп + audit retention policy janitor (cron cleanup
      >6 месяцев когда хранилище подскочит)
- [ ] **23+:** Open list - sanad explorer, multi-grading, RTL UI,
      экспорт PDF/SVG, mobile, advanced search. См. `docs/backlog.md`
      раздел «Будущие фичи»

### Этап 49 (Сессия 49d vision expansion) — большие фичи в planning

Запрошены Абдулой в начале Сессии 49d (2026-05-20). Полное описание —
в `docs/specs/2026-05-20-vision-expansion-49d.md`. Отдельные
design-specs создаются по мере приоритезации.

- [ ] **49.A: Roles ADMIN/SCHOLAR/STUDENT/USER** - расширение `users.role`
      enum, AuthorizationService, route guards. Spec:
      `docs/specs/2026-05-20-roles-system-design.md` (572 строки, ready для
      implementation). Effort ~19.5h. Subphases 49.a-49.j.

- [ ] **49.B: Rating + pagination** для Topics/Q&A/Library - sorting
      by popularity, view tracking, optional vote system. Spec:
      `docs/specs/2026-05-20-rating-pagination-design.md` (в
      работе subagent'ом)

- [~] **49.C: Hadith Chains Explorer (BIG)** - новое приложение
      `src/apps/hadith/` (ADR-018 platform pivot validation).
      **🔄 РАЗВОРОТ ADR-060 (Сессия 56): alminasa.ai = единственный источник**
      (спека `docs/specs/2026-06-03-alminasa-hadith-source-design.md`);
      sunnah-ETL и AI-иснад ниже по тексту — legacy, выпиливаются Планом 4.
      Прогресс alminasa-трека:
      - [x] **Планы 1-2** (схема+домен+репо; ES-клиент+резюмируемый
            краулер) ✅ 2026-06-03/04 — миграции 70-73 (hd_*-колонки,
            5 таблиц, am_staging_* + checkpoint, составной курсор
            [serial, hadith_id]: hadith_serial_id per-book, НЕ глобален),
            AlminasaEsClient + admin REST `/admin/alminasa/crawl/*`,
            live-верифицирован, 33 теста. План:
            `docs/plans/2026-06-04-alminasa-crawler-staging.md`,
            progress.md Сессия 56.
      - [x] **План 3** — маппер staging→hd_* + детерминированный парс иснада
            из `<a class=rawy>` (БЕЗ AI) ✅ 2026-06-04 — AlminasaIsnadParser
            («сегмент после тега», реверс pos0=сподвижник),
            Narrator/HadithMapper (upsert по external_id, рулинги
            union+дедуп, статус сахихайн→CANONICAL), ImportService
            (per-док tx, resolve FK), dry-run. 51 тест, verify 1354.
            Review: 0 Critical / 1 Important (закрыт). План:
            `docs/plans/2026-06-04-alminasa-mapper.md`.
      - [x] **План 4** — выпил legacy ✅ 2026-06-04 — sunnah ETL
            (41 файл) + AI-иснад (ADR-059 superseded), миграция 74 DROP
            sn_staging_*, AdminSunnahPage + 82 i18n-ключа, regen types.ts
            (-389 строк); buildGraph/SanadGraph живы. Review-гэпы закрыты.
            verify 1288+4(flake-rerun green), vitest 711, tsc clean. План:
            `docs/plans/2026-06-04-alminasa-legacy-removal.md`.
      - [x] **План 5** — AdminHadithImportPage ✅ 2026-06-04 — 5 admin-
            endpoints (catalog с mappedCount по source / import status с
            live-прогрессом / async launcher c CAS+finally-контрактом /
            dry-run 404|422), страница 4 секции (краулер start/pause,
            каталог 12, импорт, dry-run превью цепи). Live-верифицирован
            playwright: дев-краул 100 хадисов → импорт 100/100 (вскрыл
            и закрыл live-баг kunya/laqab>120). План:
            `docs/plans/2026-06-04-alminasa-admin-import-page.md`.
      - [x] **План 6** — Hadith Explorer на alminasa-данных ✅ 2026-06-04
            — detail +8 полей (type/chapter/fullTextAr/editions/rulings с
            provenance/explanations/crossrefs), narrator +6
            (tabaqa/gradeText/relations), sanad-graph +externalId;
            кликабельный иснад (parseIsnadHtml, lifted graph-фетч, единая
            панель), вердикты/шарх/такхридж/издания, сеть передатчиков.
            Live-верифицирован playwright (146-1). План:
            `docs/plans/2026-06-04-alminasa-frontend-explorer.md`.
      - [x] **План 7** — AI-перевод матна on-demand ✅ 2026-06-05 —
            POST /matns/{id}/translate (кэш в text_ru/text_en,
            force=ADMIN, isEnabled→503, LLM вне tx), кнопки RU/EN у
            hero-матна и вариаций. Тесты со стабом; live — ждёт ключ.
            План: `docs/plans/2026-06-04-alminasa-ai-translation.md`.
      - Review Планов 5-7: **0 Critical / 0 Important** / 5 Minor
            (4 закрыты фикс-коммитом, 1 принят). verify 1318+,
            vitest 737, tsc clean.
      - [x] **С58 фидбек-фиксы** ✅ 2026-06-06 — 2 live-бага (ثنا-формулы,
            SAHABI-детекция), turuq-graph + version-узлы + тогл «Все
            пути», вердикты-ссылки, такхридж-имена, sibling-matns,
            «في الإسناد», «Без оценки». Review 0C/0I.
      - [x] **План 8 — вкладки علل/غريب** ✅ 2026-06-06 — миграция 75,
            backfill-краул 2 индексов (33k за 17 мин), маппинг
            ILAL/GHARIB (65 280 + 2 018 строк live), три секции UI.
            План: `docs/plans/2026-06-06-alminasa-ilal-gharib.md`.
      - [x] **narrator-commentary — джарх/таʿдиль о рави** ✅ 2026-06-16
            (Сессия 61, ADR-061, миграция 76) — backfill `narrator-commentary-12`
            (29 546 цитат, re-map 7 789 рави 0 ошибок) → `hd_narrator_commentaries`
            → секция «Оценки учёных о передатчике» на карточке рави. Review
            APPROVE 0C/0I. План:
            `docs/plans/2026-06-16-alminasa-narrator-commentary.md`.
      - ✅ **Все user-гейты СНЯТЫ** (2026-06-06): массовый обход —
            Абдула снял сам; HAR علل/غريب — снят и разобран; AI-ключ
            DeepSeek — live-перевод работает. Письмо alminasa
            (вежливость) — остаётся в backlog. **NB: корпус 33k очищен
            в С60** (смок чистой БД по просьбе Абдулы) — восстановление
            перекраулом через админку (~1-2 ч).
      **Legacy sunnah-трек: ВЫПИЛЕН Планом 4** ✅ 2026-06-04 (ADR-060;
      ADR-059 superseded). Удалены sunnah-ETL (`hadith/sunnah/**`,
      `sn_staging_*` дроп миграцией 74, `/admin/sunnah/*`, AdminSunnahPage)
      и AI-иснад (`hadith/isnad/**`, `buildGraphFromExtracted`).
      Остались (живые): sanad-граф foundation (ADR-049, migrations 52-57)
      и `SanadGraphService.buildGraph`/`SanadGraph` — работают на
      alminasa-данных. История — progress.md Сессии 50-55, план
      `docs/plans/2026-06-04-alminasa-legacy-removal.md`.

- [ ] **49.D: Observability** - structured logging + Prometheus metrics
      + OpenTelemetry tracing + frontend error reporting. Spec:
      `docs/specs/2026-05-20-observability-design.md` (в работе
      subagent'ом)

- [ ] **49.E: Library collections + favorites** - библиотека становится
      общей (PUBLIC default), пользователи добавляют книги в personal
      collections. Новая таблица `user_book_collections`. Спек пока не
      создан — vision spec Section 2.2

- [ ] **49.F: Shamela full search** - расширенный поиск (автор,
      категория, направление), категории парсятся в `lib_shamela_categories`,
      possible Elasticsearch. Спек пока не создан — vision spec
      Section 2.3

- [x] **49.G: Guest view** ✅ 2026-06-18 (С62, ADR-064) — анонимный
      read-only доступ: permitAll GET (topics/hadith/library/questions) во
      всех профилях + RBAC service-фильтр (аноним → только PUBLIC,
      PRIVATE→403); auth-aware UI прячет write-действия. Побочно закрыт
      pre-existing IDOR (BookService + C-1 sub-resources). Открытый вопрос:
      member-list PUBLIC-контента доступен анониму (P1-4 прод-аудита).

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
