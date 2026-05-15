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

---

## Активные этапы

### Этап 6. Улучшения бэкенда (после MVP, не блокирует другие)

- [ ] Полнотекстовый поиск по содержимому узлов (Postgres `tsvector`)
- [ ] Реализация Dung's argumentation framework для продвинутого пересчёта
- [ ] Импорт/экспорт темы в JSON

### Этап 16. Library - PDF/EPUB upload

**Зачем:** второй способ добавления книг. Покрывает случаи когда
shamela не имеет нужной книги. MinIO storage готов из 25.b

- [ ] **16.a:** Apache Tika dependency + `FileImportService` -
      извлечение текста и metadata из PDF/EPUB
- [ ] **16.b:** REST endpoint `POST /api/v1/library/imports/file`
      multipart/form-data, размер до 50MB
- [ ] **16.c:** MinIO storage уже есть из 25.b - загруженные PDF
      сохраняются в `library-user-uploads` bucket
- [ ] **16.d:** PDF page-by-page extraction, `Page.page_number`
      соответствует физической странице PDF
- [ ] **16.e:** IT с зафиксированными PDF/EPUB-фикстурами

### Этап 17. Library - image-сканы + OCR

**Зачем:** третий способ добавления книг для сканов рукописей или
редких книг где текст недоступен

- [ ] **17.a:** PageImageService - upload изображений-страниц через
      `POST /api/v1/library/books/{id}/pages` (multipart, по одной)
- [ ] **17.b:** Tess4j integration - OCR арабского через `ara`
      training data. Async через `@Async` + фоновый таск-runner
- [ ] **17.c:** ImageRegion API - `POST /api/v1/library/pages/{id}/regions`
      для создания выделенного региона
- [ ] **17.d:** re-OCR endpoint - возможность перезапустить OCR
- [ ] **17.e:** ADR на OCR pipeline - выбор Tesseract, fallback на
      ручной ввод, точки расширения

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

- [ ] **19.a:** бэкенд Q&A модуль - `Question`, `Answer`,
      `AnswerCitation` сущности. Базовый CRUD
- [ ] **19.b:** `src/apps/qa/` фронт - страницы `/qa` (список
      вопросов), `/qa/{id}` (вопрос + ответы со ссылками)
- [ ] **19.c:** интеграция с library через CitationPicker - тот же
      компонент что в argument-map. Если работает - валидация что
      фундамент правильный

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
- [ ] **20.e:** AddSourceModal расширенная форма - при manual entry
      для sourceType=BOOK запросить полные поля (необязательны для
      URL/ARTICLE freeform)

Объём 20.c-e: ~2 сессии. Не блокирует Этап 19 Q&A

### Этап 25. PDF Viewer - operational hardening + полировка

Основные подэтапы закрыты (см. выше в закрытых). Остаётся:

- [ ] **25.b. operational hardening** - незакрытые пункты из ADR-024:
  - Circuit breaker через Resilience4j (>50% errors за 60с → 503)
  - Health-check indicator: `headBucket` ping в `actuator/health`
  - Orphan-detection janitor: фоновый job сравнивает MinIO
    listObjects vs `library_files`
  - Integrity verification cron: weekly сверка `content_hash`
  - AWS SDK v2 migration legacy `RetryPolicy` → `RetryStrategy`
  - `StreamingResponseBody` bounded `ThreadPoolTaskExecutor` для
    защиты от thread exhaustion
  - `apiCallTimeout` split: connectTimeout vs apiCallTimeout per-request
- [ ] **25.d.2: text↔pdf page sync** - internal pageNumber →
      pdfPageNumber mapping с fallback на physical=internal если null.
      Требует Tier 1 admin page-mapping flow
- [ ] **25.d.4: Inline PDF preview redesign** - кардинальное
      переустройство reader'а. Вместо tab toggle Text/PDF - кнопка
      PDF на каждой странице text mode, при клике открывается inline
      preview PDF этой страницы внизу. См. `shamela_page_view.png` +
      `after_click_on_pdf_icon_shamela.png` в design-reference
- [ ] **25.d.5: Lazy streaming через backend** - сейчас
      `PdfLinksSourceProvider.downloadFile` качает **весь PDF** на бэк.
      Lazy streaming: форвардить Range-request frontend → archive.org
      → отдавать chunks по мере получения. Связано с ADR-023
- [ ] **25.e:** admin manual page-mapping (Tier 1, опционально)
- [ ] **25.f:** region selection через react-image-crop +
      `POST /api/v1/library/pages/{id}/regions` (после Этапа 17)

### Этап 21+. Аутентификация и далее

- [ ] **21:** Spring Security + JWT - реальная аутентификация
- [ ] **22:** Многопользовательский режим - private/shared/public
      visibility для тем, books, ответов
- [ ] **23+:** Open list - sanad explorer, multi-grading, RTL UI,
      экспорт PDF/SVG, mobile, advanced search. См. `docs/backlog.md`
      раздел «Будущие фичи»

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
