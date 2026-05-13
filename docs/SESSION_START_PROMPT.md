# Стартовый промпт для новой сессии Claude Code

Этот файл - шаблон для начала новой сессии после исчерпания контекста
текущей. Скопировать содержимое раздела "Промпт для копирования" в
начало новой сессии - Claude получит полный контекст без ручного
объяснения.

## КРИТИЧНО для Сессии 30+ (после Сессии 29 - этап 18.f ПОЛНОСТЬЮ ЗАКРЫТ)

Сессия 29 закрыла **весь этап 18.f CitationPicker** в одну сессию - 12 tasks
из plan'а (Task 0-11), 11 коммитов (3 docs + 4 backend + 4 frontend) +
финальный handoff. Полный stack от миграций БД до frontend deep links.

**Production-ready state:**
- Backend: migrations 22+23 applied на production-БД, NodeCitationService
  работает end-to-end (curl smoke прошёл в Task 4)
- Frontend: CitationPicker + NodeCitationsSection 2 кнопки + BookReaderPage
  deep links. 143/143 tests pass, TS clean, ESLint 0 errors, build success
- Bundle initial 327kB/gzip 103kB (+71kB к pre-этапу 18.f)

**Backend ready в production-БД** - migrations 22+23 applied, NodeCitationService
работает end-to-end (curl smoke прошёл с Cyrillic + Arabic location).

**Done в Сессии 29:**
- Backend (Task 0-4): `67b3594` gotcha, `13823cd` migration 22 + ADR-026 + 8 IT,
  `c1c1c9f` migration 23 + ADR-027 + 8 IT, `0b86a0e` NodeCitationService + 21 IT,
  curl smoke прошёл. Production-БД миграции applied
- Frontend (Task 5-11): `19129d5` extract mini-reader, `8793d0d` textRangeUtils +
  PageView selection, `a8f24aa` CitationPicker компонент, `c6dfa18`
  NodeCitationsSection две кнопки + deep links, `335701d` BookReaderPage deep
  link parsing, `c11175a` lint+build verify
- Docs/handoff: `af2254d` spec, `361a8bc` plan, `4ae81eb` + `e99b8c5` +
  `9154dbb` промежуточные handoff

**После Сессии 29 user-feedback fixes** (2 mini-коммита):
- Секция «Цитаты» → **«Опора»** (مُسْتَنَدٌ/دَلِيلٌ) с иконкой `Anchor`.
  Семантически богаче чем «Цитаты»/«Источники» для исламского контекста,
  покрывает library + freeform. Рекомендация от Claude Design в
  `frontend/design-reference/project/citations.jsx`
- Backend computed location: убран `«строки X-Y»` (это были char offsets
  plain text, не визуальные строки - технический highlight payload, не
  academic citation). Display теперь только `Т.X стр.Y`, range используется
  только для `?highlight=` deep link

**Сессия 30 - выбор приоритета** (этап 18.f закрыт целиком):

**Done в Сессии 29:**
- `af2254d` design spec + `361a8bc` implementation plan (12 tasks)
- `67b3594` Task 0 - gotcha lib_pages.id stability (mapper skip-if-existing
  даёт invariant без UPSERT fix)
- `13823cd` Task 1 - миграция 22 (Source.bookId FK) + ADR-026 + 8 IT
- `c1c1c9f` Task 2 - миграция 23 (node_sources +7 positional колонок) +
  ADR-027 + 8 IT

1. **Этап 18.h Design polish «Опора»** (~1-2 ч) - применить варианты
   A1+B1+C1 из `frontend/design-reference/project/citations.jsx`:
   footer chips на NodeCard (library count vs freeform count раздельно),
   типизированные карточки в side-panel (LibraryCite indigo bar vs
   FreeformCite slate background), inline meta-row в header. **Quick win**,
   нет backend changes
2. **Этап 20 Academic citation metadata** (~3-5 сессий) - **новый ADR-028**.
   `lib_books` сейчас имеет minimum metadata, для proper academic citation
   нужны: мухаккик (тахкик), издательство, место, edition, год хиджри+
   григорианский, полное имя автора с куньей/насабом/нисбой. Без мухаккика
   citation считается дефектной в исламском `бахс`. Подробности в roadmap
   Этап 20.a-f
3. **Этап 19 Q&A приложение** (~3-5 сессий) - валидация платформенности
   через первое новое приложение поверх library. Backend: миграция
   questions/answers/answer_citations, REST API CRUD. Frontend:
   `src/apps/qa/`
4. **PDF bbox selection в CitationPicker** (~1 сессия) - backend API
   change (PdfFileInfoResponse с fileId UUID) + frontend PDF tab в picker
5. **Marathon TODO** F-01 split TopicGraphPage (1161 LOC), F-02 split
   BookReaderPage (714 LOC). Low ROI

**Smoke данные в production-БД:**
- node `4139cb32-28ba-4d98-9954-225e8e3c863d` имеет 1 citation на
  Тафсир Ибн Касира (page `a50ceb1a...`, range 0-50, mode TEXT)
- Можно использовать для browser-test «Перейти к источнику» button

**Ключевые артефакты (читать перед началом!):**
1. `docs/superpowers/specs/2026-05-13-citation-picker-design.md` -
   полный spec с архитектурой, data model, API, UX flow, error
   handling, testing strategy
2. `docs/superpowers/plans/2026-05-13-citation-picker.md` -
   **12-task implementation plan** с bite-sized TDD steps,
   complete code snippets, exact commit messages. **Начать с Task 0**

Также см. `feedback_brainstorming_autonomy.md` в memory - не использовать
Visual Companion в этом проекте, всё в чате через ASCII/text.

**Scope зафиксирован:**
- MVP только argument-map (Q&A → Этап 19 отложен)
- Full positional citation модель (миграции 22+23, ADR-026+027)
- 4 modes: TEXT (pageId+range), PDF (pdfFileId+pdfPageNumber+bbox),
  REGION (image_region_id, для будущих сканов), LEGACY (freeform)
- AddSourceModal **сохраняется** как «Свободный источник» (вторая
  кнопка), CitationPicker - новый primary flow «Привести источник»
- Mini-reader extract из apps/library/components в shared/components/reader
- Deep links через query params для navigation на citation source с
  подсветкой фрагмента

**Объём этапа:** реалистично 1.5-2 сессии. Если context наполняется -
handoff после Task 4 (backend done) или после Task 9 (frontend feature
complete). См. progress.md Сессия 29 «Следующий шаг» для порядка
tasks с estimates.

**Команды для исполнения plan'а:**
- `superpowers:executing-plans` skill для TDD execution с checkpoint'ами
- ИЛИ `superpowers:subagent-driven-development` если хочется один subagent
  per task (рекомендуется для длительных tasks 3, 7 где много кода)

**page_id stability** уже выполнен через ShamelaToLibraryMapper.mapBook
skip-if-existing - Task 0 в plan'е это только audit + добавление gotcha,
fix не нужен.

**Инфраструктура (Сессия 30 entry):**
- Postgres :5432 healthy (миграция 21 применена, **22+23 будут применены
  в Task 1-2**)
- Backend :9090 + JDWP :5005 running с MinIO streaming PDF stack
- Frontend :5173 running
- MinIO :9000 healthy с 4 buckets + 1 PDF object из smoke 25.b

## КРИТИЧНО для Сессии 29+ (после Сессии 28 - этап 25.b ПОЛНОСТЬЮ закрыт)

Сессия 28 **закрыла весь этап 25.b** (от ADR-024 до production-ready
MinIO streaming) в 6 фазах (9 feat/refactor/docs коммитов + 1 правило):

1. **Phase 1 (25.b.1)** - `aafcfc0` docs: ADR-024 фиксирует 7 решений
   пакетом (S3-compatible AWS SDK v2 / permanent storage / versioning
   forever / library_files catalog / SHA-256 / 4-bucket criticality /
   GDPR soft-delete two-phase). architecture-platform.md новый раздел,
   glossary 7 терминов, roadmap split 25.b на 6 подэтапов

2. **Phase 2 (25.b.2)** - `6189ee6` Liquibase миграция 21 +
   `LibraryFileSourceType` enum + `LibraryFile` record +
   `LibraryFileRepository` JDBC + 19 IT

3. **Phase 3 (25.b.3)** - `c7afd88` docker-compose minio + minio-init
   (4 bucket'а, versioning ON на 3 critical) + AWS SDK v2 2.44.4 через
   bom-import + s3 + url-connection-client + application.yml блок
   storage + `ObjectStorageProperties` record +  `S3ClientConfig` bean +
   `S3ClientConfigIT` smoke (3 теста)

4. **Phase 4 (25.b.4)** - `14c82ef` `ObjectStorageService` API + 16 IT
   через Testcontainers MinIO. Domain records `PutResult` + `StoredObject` +
   `ObjectStorageException`. Service методы: put / putAndRegister / get /
   getRange / exists / headObject / softDelete / hardDelete

5. **Phase 5 (25.b.5)** - `79b4534` интеграция в PdfLinksSourceProvider
   с двухуровневым cache (L1 local + L2 MinIO). 8 IT. Refactor PdfFetcher
   как testable interface. **Промежуточный** - L1 cache hit обходил
   catalog registration в случае tempDir leftover

6. **Phase 6 (25.b.6 final)** - `9e58b2d` `refactor(backend)` полный
   рефактор PDF stack: убран local cache, PDF streaming напрямую из
   MinIO через `StreamingResponseBody`. User указал "не храни мусор, не
   делай обратную совместимость". API changes:
   - PdfSourceProvider: `downloadFile → Path` → `locateFile → PdfLocation`
   - PdfService: `getOrDownload` → `locate` + `openFull/openRange`
   - PdfController: `StreamingResponseBody` вместо `FileSystemResource`
   - Новый domain record `PdfLocation` (bucket/storageKey/sizeBytes/contentType)
   - Temp file только как short-lived buffer для SHA-256 (удаляется после put)

End-to-end проверено playwright + restart cycle:
- Чистый start: catalog 0, MinIO empty
- Первый клик: `pdf download from upstream` (7сек 1.5MB), catalog +
  MinIO populated
- Второй клик: `pdf cache hit catalog=<uuid>`, no upstream
- **Restart backend**: catalog + MinIO **survive**
- После restart click: `pdf cache hit` снова - кеш persistent

357 IT (+22 за сессию vs Сессии 27 base) + 164 unit зелёные.

Также `43aac93` - правило про backend/frontend rerun: Claude сам
запускает оба dev-сервера (backend ВСЕГДА с JDWP `-agentlib:jdwp=...:5005`
для IntelliJ Remote JVM Debug), не ждёт user'а. Старое правило про
"backend запускает Абдула" удалено

**ПРОЕКТНЫЕ РЕШЕНИЯ (зафиксированы в Сессии 28)**:
- bucket names: `library-imported-books` / `library-user-uploads` /
  `library-page-images` / `derived-artifacts`
- MinIO version pin: `minio/minio:RELEASE.2025-07-23T15-54-02Z-cpuv1`
  (cpuv1 для WSL2 без AVX2)
- AWS SDK v2 2.44.4 через bom-import
- content hash SHA-256
- soft-delete по умолчанию, hard-delete через two-phase

**ИНФРАСТРУКТУРА сейчас (Сессия 29 entry)**:
- Postgres :5432 (миграция 21 применена, library_files в production-БД
  с 1 row от smoke-теста PDF cache)
- Backend :9090 + JDWP :5005 (весь PDF stack рефакторен под MinIO
  streaming)
- Frontend :5173 (PDF UX из Сессии 27 работает через новый backend
  без изменений на фронте)
- MinIO :9000 + console :9001 (minioadmin / minioadmin) - 4 bucket'а
  готовы, versioning на 3 critical, 1 PDF object 1.5MiB в
  library-imported-books

**Главный приоритет Сессии 30** - **исполнение plan'а 18.f** task-by-task.
Сессия 29 выбрала вариант 1 (18.f CitationPicker) из вариантов ниже,
сделала brainstorming+spec+plan. Сессия 30 начинает с Task 0.

**Старые варианты (для контекста, 18.f уже в работе):**

1. **Этап 18.f CitationPicker** (**в работе**, plan готов) - центральный
   элемент платформенного pivot'а ADR-018. PDF foundation готов - можно
   строить cross-app citation flow. `shared/components/citation/` с
   window.getSelection() → modal → выбор приложения (argument-map /
   Q&A) + контекста. Source-first: snapshot `printed_page` + `part` в
   `node_sources.location`. Unlock'ает 18.g и Этап 19 (Q&A)

2. **Этап 25.d.2 text↔pdf page sync** - internal pageNumber →
   pdfPageNumber mapping. Требует Tier 1 admin mapping UI

3. **ADR-025 bulk vs lazy import direction** - короткий ADR (~30 мин)
   зафиксировать "lazy by default" сейчас когда видно что lazy
   PDF download работает. 8500 книг bootstrap'ить не нужно

4. **Marathon TODO** (F-01/F-02 split TopicGraphPage/BookReaderPage) -
   low ROI

Детальный план в `progress.md` Сессия 28 Phase 6 "Следующий шаг".

**Памятка для будущих responsive/mobile сессий:**
- `Select.maxVisibleItems` сейчас 12 (без scrollbar при ≤12 опций).
  На mobile/tablet viewport zoom-in может ужать пространство - 12
  опций не уместятся. При работе над адаптивным UI пересмотреть
  значение или сделать adaptive (CSS `vh`-based вместо count-based)

## КРИТИЧНО для Сессии 28 (после Сессии 27 - PDF UX completion)

Сессия 27 закрыла **все** user feedback пункты из Сессий 25-27 через
**12 коммитов**. Главный focus: PDF reading UX и source-first navigation.

**Что закрыто в Сессии 27:**

1. **Phase 1** - CJK cleanup (миграция 20 backfill 66 pages),
   chapters tree collapsible, inline PDF preview bottom-sheet
2. **Phase 2** - PDF mapping через shamela `part`/`printedPage` (фикс
   bug'а где Том 3 Стр 39 открывал не тот PDF), sticky chapter
   highlight для всего диапазона страниц, click parent navigates +
   expands, red PDF button styling, padding fix
3. **Phase 2 continuation** - editable blue block (Том dropdown +
   printedPage input для navigation как в shamela), resize bottom-
   sheet drag handle
4. **Phase 2 final** - custom `<Select>` portирован из
   design-reference (`shared/components/ui/Select.tsx`) с centered
   options, manual collapse override, scrollIntoView для active
   chapter, sticky text toolbar чтобы prev/next остались
   accessible при открытом bottom-sheet

## КРИТИЧНО для Сессии 27+ (после Сессии 26 - PDF cover bug fix + UX polish)

После Сессии 26 (2026-05-11):
- PDF cover bug закрыт (`isCover` поле в `PdfFileInfo`, frontend
  пропускает обложку по дефолту, multi-volume dropdown работает)
- PDF UX polish: chapters tree RTL rail, dropdown style, page jump
  в PDF, download кнопка, loading flicker fix
- ADR-023 принят как направление: миграция long-running backend
  процессов на event-driven (worker'ы + persisted queue + checkpointing)
  когда дойдём до Этап 16 user-upload или multi-user beta
- Memory entries добавлены: playwright skill для UI verification,
  design-reference checks перед UI changes

**Главный приоритет Сессии 27 - 25.b MinIO cache** (см.
`docs/progress.md` Сессия 26 "Следующий шаг" - там детальный план).

## КРИТИЧНО для Сессии 26+ (после Cleanup Marathon)

После Сессии 25 (2026-05-11) структура frontend **изменилась**:

- Старые пути `frontend/src/components/graph/X` → `frontend/src/apps/argument-map/components/graph/X`
- Старые пути `frontend/src/pages/TopicGraphPage` → `frontend/src/apps/argument-map/pages/TopicGraphPage`
- Старые пути `frontend/src/pages/BookListPage`/`BookReaderPage` → `frontend/src/apps/library/pages/`
- Старые пути `frontend/src/pages/AdminShamelaPage` → `frontend/src/apps/admin/pages/`
- Старые пути `frontend/src/components/ui/`, `layout/` → `frontend/src/shared/components/{ui,layout}/`
- Старые пути `frontend/src/api/`, `stores/`, `utils/designTokens` → `frontend/src/shared/{api,stores,utils}/`
- Старые пути `frontend/src/utils/{edgeRules,graphLayout,attachmentTokens}` → `frontend/src/apps/argument-map/utils/`

**Backend** разнесён по responsibilities (см. `docs/architecture.md`):
`ShamelaImportService` удалён → `ShamelaMasterSyncService` +
`ShamelaBookImportService` + `ShamelaWorkDirManager`.
`ShamelaToLibraryMapper` стал orchestrator над 5 классами в
`library/shamela/service/mapper/`. 5 DAOs используют `ShamelaDaoSupport`.

**Документация:**
- **Перед началом** прочитать `CLAUDE.md` в корне проекта - быстрый
  обзор стэка/структуры/команд/конвенций
- `docs/progress.md` теперь содержит только Сессии 22+; архив Сессий
  0-21 в `docs/archive/progress-sessions-1-21.md` (читать только при
  поиске исторического контекста)
- Cleanup marathon spec/plan/audit:
  - `docs/superpowers/specs/2026-05-11-codebase-cleanup-marathon-design.md`
  - `docs/superpowers/plans/2026-05-11-codebase-cleanup-marathon.md`
  - `docs/superpowers/audits/2026-05-11-codebase-audit.md` (46 findings)

**Незакрытые findings из audit (Phase 2.b/c + Phase 3 polishing):**
- F-01 разнести `apps/argument-map/pages/TopicGraphPage.tsx` (1161 LOC)
  на TopicGraphPage + GraphCanvas + GraphToolbar + GraphContextMenu +
  useGraphModals/useGraphRefresh hooks. Резолвит также F-13 (5
  eslint-disable exhaustive-deps)
- F-02 разнести `apps/library/pages/BookReaderPage.tsx` (714 LOC) на
  BookReaderPage + BookChapterTree + BookPageRenderer + TextPageViewer
- F-03 разнести NodeDetailsPanel (613 LOC) на section-components
  (после F-01)
- F-04 разнести AddSourceModal (550 LOC) на SourceSearchForm +
  SourceCreateForm
- F-05 extract shared FormModal из AddNodeModal/AddEdgeModal
- F-10 миграция 8 компонентов на `shared/types/async.ts AsyncState<T>`
  (тип уже создан, миграция отложена до split монстров)
- F-14 установить DOMPurify + sanitize non-shamela HTML в BookReaderPage
- T-01 frontend: разнести `NodeDetailsPanel.test.tsx` (403 LOC) на
  логические suites
- T-04 explicit timeouts в 30+ `waitFor()` вызовах
- T-05 заменить Tailwind class assertions на семантические
- T-06 уменьшить scope ShamelaAdminControllerIT (over-mocking)
- T-07 magic UUIDs → named constants
- B-04 backend DTO rename (BookSummary → BookSummaryResponse и т.д.)
  - требует запущенного backend для regenerate types.ts
- Phase 5 polishing: D-01, D-02, D-04, D-05, D-06, D-07 (см. audit)

Эти TODO детально расписаны в audit-документе с file:line и
proposed actions.

Раздел "Что обновить перед каждым handoff'ом" описывает что нужно
проверить в этом файле перед тем как использовать его в новой
сессии (актуальные TODO, изменения инфраструктуры, новые ADR и т.п.).

---

## Что обновить перед каждым handoff'ом

Перед использованием промпта в новой сессии **проверь актуальность**
полей помеченных `<!-- AUTOFILL -->`:

- Текущая дата (`Today's date is ...`) - если этот промпт лежал
  >недели, обновить
- Список открытых TODO - сверить с `roadmap.md` и записями `progress.md`
- Текущий PORT/UUID/тестовая тема - проверить что dev-тема ещё
  существует, иначе пересоздать через `scripts/seed-mawlid.sh`
- Список последних `git log --oneline -10` - чтобы новая сессия
  понимала свежий контекст коммитов
- Bundle size - после крупных рефакторингов или новых тяжёлых
  зависимостей сверить с реальностью (`npm run build`)

---

## Промпт для копирования

```
Ты Claude Code, продолжаешь работу над проектом argument-map с Абдулой
Баснукаевым. Это монорепа в /mnt/c/my_folders/projects/argument-map:
- backend/ — Java 21, Spring Boot 3.5, JDBC Template, Postgres 16, Liquibase
- frontend/ — React 19, Vite 6, Tailwind v4, React Flow, Zustand 5,
  openapi-typescript, Vitest
- Сейчас активная зона работы - backend (Library MVP shamela ETL)

══════════════════════════════════════════════
РЕЖИМ РАБОТЫ - АВТОНОМНЫЙ ЗАМЕСТИТЕЛЬ (с Сессии 19+)
══════════════════════════════════════════════

Абдула передал режим **полной автономии в рамках проекта**:

- **Все тактические решения** (архитектура, декомпозиция, выбор
  библиотек в рамках уже зафиксированного стэка, порядок этапов,
  разделение коммитов) - принимаешь сам, без подтверждения
- **Под-сессии (subagents) через `Agent` tool** - использовать
  ограниченно. Для исследования (Explore) и code review - норм.
  Параллельный запуск нескольких агентов на implementation-задачи
  не оправдан в этом проекте (был эксперимент в Сессии 21 на 15.3 -
  выигрыша не дал). Делаешь сам последовательно
- **Закрытие сессии** записью в `SESSION_START_PROMPT.md` для
  следующей сессии - сам решаешь когда. Новая сессия читает и
  продолжает без ожидания апрува
- **Коммиты** - делаешь сам, в любую часть репы (бэк/фронт/корень/
  доки). Conventional Commits, разумная атомарность

**Red lines - НИКОГДА без явного спроса:**
- Не удалять системные папки (`/home/*` кроме нашего проекта,
  `~/.claude`, `~/.ssh`, etc) или другие проекты в `~/projects/`
- Не делать `git push --force` на main/master
- Не амендить опубликованные коммиты
- Не пропускать pre-commit hooks через `--no-verify`
- Не менять стратегию проекта (vision.md / ADR-018) - это уровень
  Абдулы. Можно предлагать, не реализовывать без апрува
- Не делать destructive ops (`git reset --hard`, `rm -rf` каталогов)
  без понимания что отменяется

**Когда эскалировать к Абдуле** - не зависать молча на блокерах:
- Что-то **не скачивается** несколько раз (npm/maven/docker fail)
- **Версия не находится** и retry не помогает
- **Что-то не запускается** после ~3 разумных попыток диагностики
- **Противоречие в спецификации/доках** которое нельзя решить
  выбором
- **Внешний blocker** - API-ключ, доступ к shamela, OCR-модель

Формат эскалации: «пробовал X и Y, не получается потому что Z,
предлагаю A или B, твой выбор» - не «как мне быть?» в вакууме.

См. memory `feedback_full_autonomy_mode.md` для полного описания
полномочий и red lines. Помни про это правило **до начала любой
работы** в новой сессии.

══════════════════════════════════════════════
START-OF-SESSION PROTOCOL (выполни ДО ответа)
══════════════════════════════════════════════
1. Прочитай ПОЛНОСТЬЮ:
   - docs/progress.md (последние 3 записи + раздел "Следующий шаг")
   - docs/roadmap.md (текущий этап, открытые пункты, бэклог
     "Будущие фичи (исламский контекст)" - 18+ записей из
     дизайн-референса)
   - docs/decisions.md (все ADR, особенно последние 3: ADR-014
     reconnect, ADR-015 status-bar слева, ADR-016 nodeCount/
     edgeCount в TopicResponse). В Этапе 12 ADR не было - чисто
     UI поверх готового бэк-контракта
   - docs/gotchas.md (все ловушки, чтобы не наступить)
   - docs/api-contract.md (бегло, источник истины контракта)
   - frontend/CLAUDE.md и backend/CLAUDE.md (правила работы и
     чек-лист документации после коммита)
   - frontend/docs/ui-guidelines.md (после Этапа 11 -
     обновлённая палитра, status-bar слева, токены в designTokens.ts)
   - frontend/design-reference/README.md (handoff-бандл с
     дизайном; jsx файлы - визуальная спецификация для будущих
     фич, не код для копирования)
2. Проверь актуальное состояние:
   - git log --oneline -15 (свежие коммиты)
   - docker ps | grep argumentmap-postgres (контейнер БД healthy)
   - lsof -ti:9090 lsof -ti:5173 (что-то на портах)
3. В ~/.claude/projects/.../memory/MEMORY.md есть auto-memory:
   - WSL-only - всё в WSL2, не на Windows-стороне
   - User: Abdula Basnukaev, Java/Spring backend разработчик с
     проектом CREW в бэкграунде, новичок в JS/React
   - Decision authority: решай сам по умолчанию, спрашивай при
     дилеммах. ADR только когда через месяц возникнет вопрос почему
   - React key-trick для reset state (НЕ useEffect-сброс)
   - Stable callbacks + sameIds для RF массивов (анти-инфинит-луп)
   - Stale closure в useCallback с dynamic data → useRef для
     актуального snapshot (см. gotchas.md)
   - layoutGraph mixed-режим может перебросить fresh узлы. Решено
     через backfill posX/posY на load + previousNodes hint
     (см. gotchas.md)
   - Sources & Arabic direction: shamela-парсинг будущее,
     арабский как first-class (RTL + naskh) - в дизайн-референсе
     отдельные секции, в roadmap бэклог "Будущие фичи"
   - Conditional render для одноразовых модалок ({open && <Modal/>})
     вместо useEffect-сброса state - идиома проекта, обходит
     react-hooks/set-state-in-effect (см. gotchas.md). Внутри
     Modal всегда `open` prop. Применено в AddSourceModal,
     AddAuthorityModal
4. Скажи Абдуле: "вижу - последний раз X, продолжаю с Y из roadmap"
   (короткое summary, не вопрос). Если в ОТКРЫТО есть приоритет 0
   с пометкой «требует подтверждения» - ТОЛЬКО ТАМ ждёшь
5. **Сразу начинаешь работу** по приоритетной задаче из roadmap
   (см. ОТКРЫТО раздел ниже). Режим автономии - ждать апрув на
   каждый шаг не нужно. Если по ходу нашёл архитектурное решение
   которое требует обсуждения - тогда спрашиваешь точечно

══════════════════════════════════════════════
ТЕКУЩЕЕ СОСТОЯНИЕ (зафиксировано на 2026-05-11 после Сессии 24 - source-first нумерация страниц (миграция 19, ADR-021) + sub-chapters fix + frontend display printedPage/part. Backend код готов, production-БД пока на миграции 18 - требуется restart перед стартом Сессии 25)
══════════════════════════════════════════════

⚠️ **ВАЖНО**: проект пережил стратегический pivot - см. ADR-018 в
`docs/decisions.md`. Argument-map больше не центральный продукт,
теперь это платформа цифровых инструментов для исламских учёных и
студентов. Library (книги + цитирование) - фундамент. Прочитай:
- `docs/vision.md` - что и зачем строим
- `docs/architecture-platform.md` - как технически устроено целевое
- `docs/decisions.md` ADR-018 (pivot) и ADR-019 (доменный пакет library)
- `docs/roadmap.md` - закрыты Этапы 13-14, активны 15-22
  (shamela parser, PDF, OCR, frontend library, Q&A, auth)
- `docs/superpowers/specs/2026-05-08-library-mvp-design.md` - design
  spec под Этап 14, поможет понять как устроена library



ЗАКРЫТО:
- Бэк: этапы 0-5 целиком, 172 теста (111 unit + 61 IT)
- Фронт MVP (этап 7): TopicListPage, CreateTopicPage, TopicGraphPage
  с полным CRUD + side-panel деталей узла + редактирование + ревизии
- Этап 8: семантика связей (ADR-010 матрица, бэк-валидация, фронт
  фильтрация, контекстные подписи рёбер, toggle подписей)
- Этап 9 целиком: 4 handles, drag-create, контекстное меню (правый
  клик на pane/узле/ребре), z-index управление, persistence позиций
  узлов (full-stack миграция БД pos_x/pos_y, ADR-012)
- Этап 10 целиком (сессия 16): reconnect edges (ADR-014, partial
  PATCH /api/v1/edges/{id}, optimistic update без flicker),
  EdgeDetailsPanel
- **Этап 11 целиком (сессия 17): визуальная полировка по
  дизайн-референсу** - 8 подэтапов, 9 коммитов:
  1. документация и токены (ADR-015 status-bar, ui-guidelines
     обновлён, glossary с исламскими терминами, бэклог из дизайна)
  2. UI-примитивы (Button расширен 6×4, Badge, StatusBadge,
     TypeChip, Kbd, IconButton, Card, designTokens.ts)
  3. NodeCard - status-bar 5px слева вместо border-2, TypeChip+
     StatusBadge в header, line-clamp-2 body
  4. CustomEdge - переключён на EDGE_TYPE_TOKENS, badge с soft shadow
  5. AddNodeModal/AddEdgeModal - тип в grid карточек с превью
  6. NodeDetailsPanel/EdgeDetailsPanel - градиент header, collapse
     секции, diff-блоки в истории (red-50/40 / emerald-50/40)
  7a. Бэк (ADR-016): nodeCount/edgeCount в TopicResponse через
      агрегатный SQL (один LEFT JOIN-запрос для всех тем).
      TopicWithCounts record + TopicRepository.findAll/ByIdWithCounts
  7b. TopicListPage - сетка карточек с мини-графом SVG, бейдж count,
      topbar с навигацией (Авторитеты/Источники placeholder)
  8. GraphScreen layout - левый вертикальный toolbar (IconButton),
     floating легенда (bottom-left) / zoom controls (bottom-center
     через rfInstance) / hotkeys hint (top-right). CompactMiniMap
     перенесён top-right → bottom-right
- **Сессия 19 backend + Сессия 19 frontend (частично): ADR-017
  трёхуровневая модель цитирования** -
  - Бэк закоммичен в `302f2be` `refactor(backend): ADR-017
    объединение Source+Authority` (одним большим refactor): миграция
    15 дропает `node_authorities`, добавляет `sources.authority_id`
    + `node_sources.location`. Удалены `Stance` enum, NodeAuthority/
    Repo/Service/Controller/DTO, эндпоинты `/nodes/{id}/authorities`
  - Фронт (Сессия 19, 4 коммита):
    - `cb813da` - 13.a удаление AddAuthorityModal + секции «Авторитеты»,
      минус 1031 строка
    - `61dae69` - 13.b секция «Цитаты» с трёхуровневой иерархией +
      скрытие для QUESTION + RTL для арабских цитат
    - `08505d4` - 13.c.1 поле location в AttachFields
    - docs - запись progress + Этап 13 в roadmap
  - **Этап 13 закрыт частично-достаточно**: 13.c.2 (author-picker)
    и 13.d (seed-мавлид) wontfix - устаревают с library, см. ADR-018
- **Сессия 19 (pivot): ADR-018 платформенный pivot** - 3 коммита:
  - `0326eb0` `docs: ADR-018 + vision + architecture-platform +
    README + roadmap reorganized` - новые `docs/vision.md` и
    `docs/architecture-platform.md`, переписан корневой README,
    roadmap получил Этапы 14-22
  - `2b8d058` `docs: автономный режим работы Claude Code как
    заместителя` - режим автономии в проекте, red lines, формат
    эскалации
- **Сессия 23 (full-stack, длительная): 18.b-d + 15.7 + 18.a + миграция 18 + UX-фиксы** -
  ~13 коммитов. Закрыто полностью:
  - 18.b/c/d - Header, BookListPage, BookReaderPage с RTL/naskh
  - 15.7 - admin search (JOIN+EXISTS) + sync-status endpoints
  - 18.a - AdminShamelaPage с live-search, импортом, status-dashboard
  - Откачена попытка monorepo apps/* (WSL2 git mv глюк, gotchas.md)
  - Fixed: 270k→8500 цифра, shamela book sqlite `{id}-{major}.sqlite`
    naming (`findBookSqlite` tolerant lookup), search-by-id в admin,
    .env.local guide, default-page-range 50 убран, shamela page-content
    rendering (`\r` linebreak, `舄` PUA sanitize, bibliography
    `الكتاب:`/`المؤلف:` parser), `<p>` margin fix через @layer
    components, маркер ❖ добавлен потом убран (shamela тоже не
    показывает)
  - Миграция 18: `lib_chapters.start_page_number` + Chapter record +
    Mapper.parseStartPage (regex `\d+` из shamela page_ref) +
    ChapterResponse DTO
  - PageJump компонент (input для прямого ввода pageNumber, key-trick
    для sync), кликабельные главы (button + indigo highlight на
    активной), gotoPage с clamp + nearest-distance fallback
  - **303 IT зелёных** + 136 frontend tests + lint clean
- **Сессия 23 (фронт): Этап 18.b-d Library frontend MVP (старая запись начала сессии)** - 2 коммита:
  - `e6898f0` `feat(frontend): этап 18 - library frontend MVP с RTL/naskh для арабского` -
    Single-page подход вместо monorepo apps/* (первая попытка
    реструктуризации откачена - WSL2/NTFS git mv глюк, см. gotchas.md).
    `components/layout/Header.tsx` извлечён общий top-bar из TopicListPage
    с NavLink на /topics, /books, /qa (placeholder). `pages/BookListPage.tsx`
    /books - сетка карточек книг через GET /api/v1/library/books, локальный
    поиск по title + фильтр bookType (5 типов). `pages/BookReaderPage.tsx`
    /books/:bookId - двухколонная: side-panel chapters tree (рекурсивный
    из flat ChapterResponse через buildChapterTree group-by-parent + topo
    sort, защита от orphan parent_id), main с pagination + PageView через
    dangerouslySetInnerHTML (shamela HTML, sanitize TODO). Эвристика
    арабского текста через Unicode 0x0600-0x06FF + RTL+naskh. Google Fonts
    Noto Naskh Arabic подключён через index.html preconnect, Tailwind v4
    @theme --font-naskh. types.ts регенерирован с свежего бэка. 8 файлов /
    1222 insertions. Bundle initial 271kB / gzip 84kB (+15kB к pre-Этапу-18).
    Lint clean, **136 frontend tests passing**, build success
  - **Этап 18.b/c/d закрыт**, остаётся 18.f (CitationPicker) + 18.g
    (argument-map переключение на CitationPicker)
- **Сессия 22 (бэк): Этапы 15.4 + 15.5 + 15.6 (5 feat + handoff коммитов)** -
  Library shamela MVP закрыт целиком на бэкенде. Сверх первоначальных
  планов закрыли все 3 оставшихся подэтапа в одной сессии:
  - `1ce9fad` `feat(backend): этап 15.6 - shamela admin REST endpoints + exception mapping` -
    `ShamelaAdminController` с 3 endpoints под `/api/v1/admin/shamela/*`
    (sync-master, import-book/{id}, map-book/{id}). 3 response-DTO +
    mapper. Расширен `GlobalExceptionHandler`: ApiException→502,
    Archive/Reader→500, ImportException→500, NotFound→404. Введён
    `ShamelaNotFoundException extends ShamelaImportException` для
    cleanup-маппинга без substring matching. 12 IT через MockMvc +
    `@MockitoBean`. api-contract.md секция «Shamela Admin API» +
    glossary.md секция «Shamela ETL» (5 терминов). 10 файлов /
    491 insertion. **PDF download / async / bulk endpoints отложены**
    (см. «Что не реализовано» в api-contract.md). **296 IT зелёных**
    (+12 от ControllerIT)
  - 15.4: `34311fe` ShamelaImportService syncMaster + importBook
    (детали ниже)
  - 15.5: `0c11740` ShamelaToLibraryMapper из staging в lib_books
    (детали ниже)
  - `dc50271` исправление выдуманной цифры 270k → ~8500 книг
    (реальная по mitmproxy-реверсу из Сессии 21). Понижен ассерт в
    LiveIT с `>10_000` до `>5_000`
- **Сессия 22 (бэк): подробности этапов 15.4 + 15.5** -
  - `34311fe` `feat(backend): этап 15.4 - ShamelaImportService syncMaster + importBook` -
    оркестрационный `@Service` со связкой ApiClient+Extractor+Reader+DAO
    в один pipeline. Идемпотентность через `ON CONFLICT DO UPDATE` в
    DAO (без транзакции на pipeline - bulk upsert ~8500 книг + ~25k
    авторов иначе держит лок). Cleanup workdir в finally рекурсивно. 4 prod-файла
    (ShamelaImportService + 2 result-records + ShamelaImportException),
    2 test-файла (ShamelaImportServiceIT с 6 IT через @MockitoBean
    ApiClient + Testcontainers postgres + fixture-zip собираются
    программно через DriverManager(jdbc:sqlite:); ShamelaImportServiceLiveIT
    @Tag("live") для реальной shamela API). 696 insertions
  - `7155f7e` `docs: handoff Сессии 22 - этап 15.4 ShamelaImportService закрыт`
  - `0c11740` `feat(backend): этап 15.5 - ShamelaToLibraryMapper из staging в lib_books` -
    второй слой ETL (доменное мапирование). `mapBook(long, UUID)`:
    резолв Authority по нормализованному name (trim+collapse+exact),
    fallback "shamela:anonymous" Authority с if-not-exists. Re-import
    detection через `BookRepository.findByShamelaBookId` (jsonb GIN).
    Chapters topologically через BFS, защита от orphan parent_id.
    Pages с page_number=shamela_page.id, chapter_id=NULL на MVP, skip
    blank/whitespace. `@Transactional` на mapBook. Расширения existing
    repos: `findByName(exact)`, `findByShamelaBookId`, `findAllByBookId`
    в Title/Page DAO. 7 файлов / 795 insertions. 10 IT через
    @SpringBootTest без моков. Защитная ветка для dangling FK
    оставлена в коде, тестом не воспроизводится (FK на DB-уровне
    гарантирует невозможность через DAO insert)
  - **Этапы 15.4 + 15.5 закрыты**. ETL-стэк полностью готов до уровня
    сервисов. Остаётся **15.6 (REST)**. **284 IT зелёных** (+16 от
    Сессии 21). OpenApiIT-flake не воспроизвёлся ни разу в Сессии 22
- **Сессия 21 (бэк): Этапы 15.1 + 15.2 + 15.3 (5 коммитов)** -
  пилот-сессия с большой диагностической экспедицией. Изначальный план
  Этапа 15 был jsoup-парсер shamela.ws. 6 попыток обойти Cloudflare
  managed challenge (curl/WebFetch/flaresolverr v3.3.21/v3.4.6 с прокси
  и без, session-mode) - все провалились. shamela.ws/book/X имеет
  агрессивный CF challenge которого Chromium-120 в flaresolverr не
  пробивает за 280с. Параллельная сессия выполнила mitmproxy-реверс
  desktop-API shamela 4 → получили 6 endpoints чистого канала без CF.
  План переписан полностью:
  - `507e0ba` `feat(backend): этап 15.1 - shamela staging-схема + ADR-020` -
    миграция 17 (`lib_shamela_category/author/book/page/title/sync_state`)
    + ADR-020 (двухслойная архитектура: staging + ShamelaToLibraryMapper)
    + architecture-platform.md workflow A полностью переписан под API
    + roadmap.md Этап 15 переразбит на 15.1-15.6 + gotcha OpenApiIT flake
    + .gitignore /node_modules/ для vite cache leftover
  - `9d6c63d` `docs: handoff Сессии 21 - этап 15.1 закрыт, продолжение в 15.2`
    - первый handoff внутри сессии (юзер сказал «контекст позволяет,
    продолжай»)
  - `f511b6a` `feat(backend): этап 15.2 - shamela api client + archive extractor` -
    pom.xml + sqlite-jdbc 3.45.3.0 + maven-failsafe excludedGroups=live,
    application.yml блок shamela:, library/shamela/api/{ShamelaApiClient,
    ShamelaApiProperties, ShamelaHttpClientConfig, ShamelaApiException,
    dto/MasterMetadata, dto/BookMetadata}, library/shamela/etl/
    {ShamelaArchiveExtractor, ShamelaArchiveException}, юнит-тесты
    extractor (6 кейсов включая Zip Slip), ShamelaApiClientLiveIT
    с @Tag("live"). 12 файлов / 688 строк
  - `520cbf5` `fix(backend): разрешить Basic auth для HTTPS-туннеля прокси` -
    Java HttpClient блокирует Basic auth через CONNECT по умолчанию
    (jdk.http.auth.tunneling.disabledSchemes=Basic). System.setProperty
    в applyProxy() снимает блок. Подтверждено живым прогоном:
    fetchMasterMetadata вернула master-0-1261.zip URL, downloadArchive
    скачал 5MB+ zip с правильной PK-сигнатурой через corporate-прокси
    proxys.io за 8.2с суммарно. shamela API работает end-to-end.
    Зафиксирована gotcha
  - `a98c3ea` `feat(backend): этап 15.3 - shamela SQLite readers + 6 staging DAO` -
    5 records в etl/dto/, SqliteValueParser (null-safe
    TEXT→Long/Integer/Boolean, "99999"→null для года, 19 unit-тестов),
    ShamelaMasterReader + 13 unit (через `DriverManager(jdbc:sqlite:)`,
    eager List, reserved-word `order` в кавычках), ShamelaBookReader +
    9 unit (включая arabic content roundtrip), 6 DAO с bulk upsert
    `ON CONFLICT(id) DO UPDATE` батчами 1000, JSONB через `?::jsonb`
    cast в SQL, composite PK для page/title, SyncStateDao singleton
    через `JdbcTimes.odt()`. 43 IT через Testcontainers. 25 файлов,
    2505 insertions. 268 IT зелёных (+43 от DAO IT)
  - **Этапы 15.1, 15.2, 15.3 закрыты**, остаётся 15.4-15.6
- **Сессия 20 (бэк): Этап 14 Library MVP** - 5 коммитов + 1 docs:
  - `506f144` `docs: design spec для Этапа 14 Library MVP` -
    полный design-doc в `docs/superpowers/specs/`
  - `6489b0e` `feat(backend): library liquibase migration 16` - 14.a
    миграция 16 (lib_books/chapters/pages/image_regions с FK +
    индексами + CHECK constraints) + ADR-019 принят
  - `f22e9c7` `feat(backend): library domain records and jdbc
    repositories` - 14.b: 5 records + 4 JDBC repositories по паттерну
    SourceRepository + 30 IT через Testcontainers
  - `0a3cf14` `docs: правило о темпе сборок и тестов` - feedback
    зафиксирован в 4 местах документации (не запускать verify/
    build после каждого мелкого изменения - только по факту в
    конце фазы)
  - `3db5247` `feat(backend): library REST api - books and pages
    CRUD` - 14.c: BookService + 6 эндпоинтов + 8 DTO + LibraryDtoMappers
    + BookController + 32 IT (15 service + 17 controller). Curl-smoke
    подтверждает работу на runtime :9090
  - `19e9017` `docs: ADR-019 формализация` - 14.d: architecture.md
    + api-contract.md + glossary.md дополнены под library
  - **Этап 14 закрыт целиком**, 225 IT в проекте
- **Сессия 18 (Этап 12 целиком): привязка источников и авторитетов
  через UI** - 5 коммитов:
  12.a. NodeDetailsPanel секции "Источники"/"Авторитеты" - lazy-load
       через GET /nodes/{id}/sources + параллельный GET /sources для
       матчинга id→название. Карточки источников (kind/title/citation/
       quote/context), строки авторитетов с avatar+stance бейджем.
       Удаление через DELETE optimistic. PanelSection расширен
       onFirstOpen callback. `apiPostRaw` добавлен в client.ts
  12.b. AddSourceModal с поиском - локальная фильтрация справочника
       по title/citation, опциональные quote/context при привязке.
       Conditional render родителя (`{open && <Modal/>}`) обходит
       react-hooks/set-state-in-effect
  12.c. Inline-создание Source - mode='create' в той же модалке.
       Форма sourceType/title/citation/reliability (показ только
       для HADITH, фронт строже бэка). Submit делает POST /sources
       → POST /nodes/{id}/sources. Извлечён attachmentTokens.ts
  12.d. AddAuthorityModal со stance + create - симметрично, но
       stance обязателен. StancePicker с цветовым кодированием
       (HOLDS=emerald/OPPOSES=red/NEUTRAL=slate). Create-form:
       name (required), era, madhab, bio
  12.e. Документация (этот раздел + roadmap Этап 12 + ui-guidelines
       + новый gotcha про conditional render модалок)
- Cross-cutting: Toast, ContextMenu (с separator items), Modal,
  NodeSelect (custom dropdown с lucide-иконками), CompactMiniMap
- ADR-011-016 все приняты. В Этапе 12 ADR не делал - чистый UI
  поверх готового бэк-контракта

ОТКРЫТО (по приоритету) - после Сессии 28 (этап 25.b ПОЛНОСТЬЮ
закрыт от ADR-024 до production-ready MinIO streaming. PdfLinksSourceProvider
интегрирован, PdfController стримит из MinIO, cache survives restart):

⚠️ **АРХИТЕКТУРНЫЙ ВОПРОС из Сессии 23** (open): bulk-bootstrap всех
8500 shamela книг vs lazy-import on user request. После 25.b.5+6
полного завершения станет понятно. Возможен ADR-025

1. **Этап 18.f CitationPicker** (recommendation) - центральный
   элемент платформенного pivot'а ADR-018. PDF foundation готов -
   можно строить cross-app citation flow. После этого 18.g (переключение
   argument-map на CitationPicker) → 19 (Q&A app)

2. **ADR-025 lazy import direction** - короткий ADR зафиксировать
   что 8500 shamela книг bootstrap'ить не нужно, lazy on-demand через
   PdfLinksSourceProvider (уже работает)

3. **Этап 25.d.2 text↔pdf page sync** - internal pageNumber →
   pdfPageNumber mapping. Требует Tier 1 admin mapping UI

4. **Marathon TODO** - F-01 split TopicGraphPage (1161 LOC), F-02
   split BookReaderPage (714 LOC), F-10 миграция на AsyncState<T>

PDF должен быть **source-agnostic** (не привязан к shamela) -
реализации: ShamelaProvider сейчас, ArchiveOrg/user-upload в будущем.
Подробный spec: `docs/superpowers/specs/2026-05-11-pdf-viewer-source-agnostic.md`

   Live-проверка end-to-end PDF Viewer (после Pre-flight Сессии 25):
   - открыть `/books/bd61050f-...` (Тафсир Ибн Касира)
   - кликнуть toggle «📕 PDF» в header reader'а
   - подождать первую загрузку (~50MB качается через нас из archive.org,
     может быть 30-60с на медленном WSL proxy)
   - PDF должен открыться на странице 1, prev/next работает, zoom тоже
   - **первая загрузка медленная** - это известный issue, MinIO в 25.b
     решит: повторные посещения этой книги из кеша мгновенно

2. **Этап 18.f: CitationPicker** - **после 25.a-25.d**. Создаёт
   `frontend/src/components/citation/CitationPicker.tsx`.
   Выделение фрагмента текста в BookReader (через
   `window.getSelection()`) → modal с выбором приложения (argument-map
   / Q&A) и контекста (какой узел / ответ). **Важно для source-first**:
   при привязке цитата должна сохранять snapshot полей
   `printed_page`+`part` в `node_sources.location` (например
   `"Том 1, стр 47"`), плюс UUID `pageId` для точного reference.
   Это центральный элемент платформенного pivot'а ADR-018

3. **Этап 18.g: Argument-map переключение на CitationPicker** -
   кнопка «Привязать цитату» в `NodeDetailsPanel` открывает
   CitationPicker вместо текущей `AddSourceModal` со свободной формой.
   AddSourceModal либо удаляется, либо становится fallback для
   свободных цитат (URL без book context)

4. **Этап 25.e + 25.f: admin page-mapping + region selection** -
   после CitationPicker. Region selection даёт CitationPicker
   возможность сослаться не только на текстовое выделение, но и на
   прямоугольник на PDF-скане (для manuscripts и нечитаемого OCR).
   `react-image-crop` overlay поверх `<Page>` PDF.js, POST
   `/api/v1/library/pages/{pageId}/regions` создаёт
   `lib_image_regions` запись (таблица уже есть из миграции 16)

**Альтернативный путь**: archive.org как источник PDF (Абдула
упомянул `https://archive.org/details/fmhji/fmhji1/page/70/mode/2up`).
Не делаем сейчас, но `ArchiveOrgPdfSourceProvider` уже описан в
spec'е - реализуется когда захотим добавить books из archive.org.
Просто новая реализация interface'а, остальное не меняется.

4. **Импорт ещё 1-2 книг для разнообразия** - может быть Хусн
   аль-максыд ас-Суюти (короткий трактат, тематически близок
   argument-map) или Маджму' аль-Фатава Ибн Таймии (стресс-тест на
   размер). После 4-5 книг - bulk vs lazy решение становится
   более определённым

5. **Архитектурное решение «bulk vs lazy import»** - откладывалось
   до PDF integration. Теперь после Этапа 25 будет видно: если PDF
   download lazy, то и mapBook тоже логично lazy. Если PDF
   pre-fetch'ить - bulk тоже разумен. ADR-022 если выберем не bulk

6. **Этап 16: PDF/EPUB upload** (после Этапа 25) - Apache Tika для
   non-shamela PDF (загрузка пользователем). MinIO storage уже
   будет готов из 25
7. **Этап 17: image-сканы + OCR** - Tess4j для арабского,
   ImageRegion API уже будет готов из 25 (только OCR pipeline
   добавляется)
8. **Этап 19: Q&A приложение** - первое полностью новое поверх
   library. Валидация платформенности
9. **Этап 20+: Auth, multi-tenancy, прочее**

10. **Доделки 15.6 (отложены сознательно)** - частично закроются
    Этапом 25:
    - `GET /api/v1/admin/shamela/book/{id}/pdf/{fileIndex}` - lazy
      PDF download (войдёт в Этап 25 как часть PDF storage)
    - Async-варианты POST endpoints (через `@Async` или queue) -
      пока не нужно
    - Bulk endpoints (`POST /map-books?ids=...`) - после bulk vs
      lazy решения

См. `docs/roadmap.md` для деталей всех этапов

**Бэклог argument-map** (старые позиции из до-pivot, теперь
второстепенные):
- Smart edge routing, тёмная тема, экспорт PNG/SVG, Z-index
  persistence - всё это «когда захотим вернуться к argument-map
  улучшениям» после library/Q&A

ИНФРАСТРУКТУРА:
- Postgres контейнер: argumentmap-postgres на :5432 (docker ps)
- Бэк (Claude запускает сам в фоне, ВСЕГДА с JDWP debug args для
  IntelliJ Remote JVM Debug на :5005):
  ```
  cd backend && ./mvnw spring-boot:run \
    -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
    > /tmp/backend.log 2>&1 &
  ```
  Порт 9090, готовность: `until curl -sf http://localhost:9090/actuator/health; do sleep 2; done`
  Перезапуск после миграции: `kill $(lsof -ti:9090); sleep 2;` + та же команда
- Фронт (Claude запускает сам в фоне):
  `cd frontend && npm run dev > /tmp/frontend.log 2>&1 &`
  Порт 5173, watch.usePolling=true для WSL2
- Dev user UUID: 14561248-0bfd-4a62-8395-d40a6972182a
  (frontend/.env.local: VITE_DEV_USER_ID)
- Тестовая тема "Дозволенность Мавлида ан-Наби":
  640a7ac7-2827-4b80-9893-dc7142f100e4
  Скрипт пересоздания: scripts/seed-mawlid.sh
- Bundle (после Этапа 12): initial 256kB / gzip 82kB,
  TopicGraphPage chunk 373kB / gzip 116kB. +29kB к Этапу 11 за
  AddSourceModal+AddAuthorityModal+секции в NodeDetailsPanel
- Если регенерируешь типы (`npm run generate-api`) - сначала
  убедись что слушает СВЕЖИЙ бэкенд с твоими изменениями. Проверь
  через curl http://localhost:9090/v3/api-docs что новые поля есть
  (gotcha из сессии 17: старая инстанция на 9090 даст устаревшую
  схему)

КЛЮЧЕВЫЕ ФАЙЛЫ:
- frontend/src/utils/designTokens.ts — STATUS_TOKENS / NODE_TYPE_TOKENS
  / EDGE_TYPE_TOKENS, источник истины для палитр. Все компоненты
  импортируют отсюда (status bar, badge, type chip и т.д.)
- frontend/src/components/ui/ — после Этапа 11: Button (6×4 + icon),
  Badge, StatusBadge (data-testid сохранён для совместимости
  тестов), TypeChip, Kbd, IconButton, Card, Modal, Toaster,
  ContextMenu (с separator support)
- frontend/src/pages/TopicGraphPage.tsx — hub-страница графа,
  собирает все компоненты (RF, NodeCard, CustomEdge, AddNodeModal,
  AddEdgeModal, NodeDetailsPanel, EdgeDetailsPanel, ContextMenu,
  CompactMiniMap). lastNodesRef + backfill posX/posY useEffect.
  findFreePosition spiral search. Esc-очередь. Левый toolbar +
  floating legend/zoom/hotkeys через RF Panel
- frontend/src/pages/TopicListPage.tsx — сетка карточек тем с
  TopicMiniGraph SVG (декоративный, точки по nodeCount), topbar
  с навигацией, локальный поиск
- frontend/src/components/graph/ — NodeCard (status-bar слева,
  TypeChip+StatusBadge), CustomEdge (use EDGE_TYPE_TOKENS),
  AddNodeModal (autoEdge + grid карточек типа), AddEdgeModal,
  NodeDetailsPanel/EdgeDetailsPanel (градиент header + collapse
  секции, diff-блоки в истории, после Этапа 12 секции "Источники"/
  "Авторитеты" работают полноценно с lazy-load), NodeSelect
  (custom dropdown), CompactMiniMap (bottom-right), AddSourceModal
  (search + create mode), AddAuthorityModal (search + create со
  stance picker)
- frontend/src/utils/attachmentTokens.ts — после Этапа 12:
  SOURCE_TYPE_LABEL/ICON/HINT/ORDER, STANCE_LABEL/BADGE_STYLES/
  RADIO_STYLES/ORDER. Источник истины для отображения source/
  authority типов и stance. По аналогии с designTokens.ts
- frontend/src/utils/edgeRules.ts — матрица ADR-010,
  NODE_TYPE_META, EDGE_TYPE_META, getRelatedNodeOptions.
  ВНИМАНИЕ: частично пересекается с designTokens.ts - в будущей
  итерации стоит консолидировать в один источник
- frontend/src/utils/graphLayout.ts — layout с allSaved/noneSaved/
  mixed режимами + previousNodes hint
- frontend/src/stores/toastStore.ts — Zustand toast-store
- frontend/src/api/client.ts — apiGetRaw/apiPostRaw/apiPatchRaw/
  apiDeleteRaw + ApiError
- frontend/src/api/types.ts — генерируется из OpenAPI бэка.
  TopicResponse теперь с nodeCount + edgeCount (ADR-016)
- frontend/src/App.tsx — React.lazy для TopicGraphPage
- frontend/design-reference/ — handoff-бандл от Claude Design.
  Это **визуальная спецификация будущих фич**, не код для копирования.
  primitives.jsx/nodes.jsx/screens.jsx - что реализовано в Этапе 11.
  islamic.jsx/extras.jsx - бэклог (sanad, multi-grading, RTL,
  settings, onboarding и т.д.)
- backend service/ TopicService.java — listTopicsWithCounts /
  getTopicWithCounts (ADR-016)
- backend repository/ TopicWithCounts.java — record + Repository
  методы findAllWithCounts / findByIdWithCounts
- backend service/ EdgeService.java — createEdge + updateEdge
  partial с финальной валидацией
- backend service/ EdgeSemantics.java — матрица ADR-010 на беке
- backend service/ NodeService.updatePosition — изолированный
  метод без revision и updatedAt
- backend config/ OpenApiConfig.java — OperationCustomizer
  для X-User-Id header вместо query.userId

══════════════════════════════════════════════
КАК РАБОТАТЬ
══════════════════════════════════════════════

ДЕКОМПОЗИЦИЯ КРУПНЫХ ЗАДАЧ:
- Любая задача больше 1-2 файлов → подэтапы X.a / X.b / X.c
- Между подэтапами — прогон проверок и КОММИТ. Не один большой
- Каждый подэтап имеет внятную границу

ДОКУМЕНТАЦИЯ ВЕДЁТСЯ ПО ХОДУ (важно!):
После КАЖДОГО feat/fix коммита проверить чек-лист из
frontend/CLAUDE.md и backend/CLAUDE.md:
- Закрыт пункт roadmap → [x]
- Принято решение между альтернативами → ADR в decisions.md
- Миграция БД / новая колонка → ADR + architecture.md
- Новый REST endpoint / поле DTO → api-contract.md
- Поймал баг который может повториться → gotcha
- Новое доменное понятие → glossary.md
- ADR/gotcha/api-contract пишутся СРАЗУ, не в конце сессии

КОГДА ЗАПУСКАТЬ ПРОВЕРКИ (важно - не на каждом чихе):
- Полный прогон делается **по факту**, а не ритуально:
  - в конце завершённой логической фазы (подэтап, новый класс с тестом,
    рефакторинг группы файлов)
  - перед коммитом если в фазе были существенные изменения
  - когда есть конкретный сигнал что что-то могло сломаться (миграция,
    изменение API-контракта, рефакторинг затрагивающий несколько слоёв)
- Между мелкими правками одного файла - **не запускать**. Это шум,
  он съедает контекст и время; компилятор/линтер всё равно ругнётся
  по факту, когда дойдёт до прогона
- Команды:
  - Фронт: `npm run lint && npm run build && npm run test:run`
  - Бэк: `./mvnw verify`
  - Если фича работает с API - smoke через curl с X-User-Id (после
    того как тесты прошли)

КОММИТЫ:
- Conventional Commits с обязательным scope: feat(frontend),
  fix(backend), chore, docs, refactor, test, style, perf, build, ci
- Чисто визуальные правки без изменения поведения - style(frontend):
- Не коммитить .claude/settings.local.json, img*.png/gif/jpg
- Не амендить опубликованные коммиты, не push без явной просьбы

ПРОВЕРКА ИНТУИТИВНОСТИ UI:
Перед "готово":
- Иконка/эмодзи понятны без расшифровки? Если нет - словесная метка
- Disabled/error состояние - видна причина? Если нет - tooltip/подсказка
- Результат действия очевиден? Если нет - feedback (toast/hover)
- Прежде чем сказать "готово" - попроси Абдулу проверить через UI

ПРОВЕРКА ЧЕРЕЗ CURL:
- Создал/изменил эндпоинт - curl с реальным X-User-Id
- Создал валидацию - оба пути: разрешённый и запрещённый
- "Тесты прошли" ≠ "фича работает"

ВИЗУАЛЬНЫЕ ПРАВКИ (после Этапа 11):
- Все цвета и токены - через `frontend/src/utils/designTokens.ts`
  (STATUS_TOKENS / NODE_TYPE_TOKENS / EDGE_TYPE_TOKENS). Не
  хардкодить цвета прямо в компонентах
- Brand-цвет: indigo (не blue). focus-ring → indigo-500
- Скругления карточек - rounded-xl, кнопок/инпутов - rounded-md
- Статус узла - bar 5px слева (НЕ border-2 вокруг). См. ADR-015
- Тип узла - капсула TypeChip (chipBg/chipText), не просто иконка
- Тесты на конкретные tailwind-классы (например `toHaveClass(
  'bg-amber-100')` в StatusBadge) обновлять при смене токенов,
  поведенческие тесты не трогать

КОГДА ОСТАНАВЛИВАТЬСЯ И HANDOFF:
Признаки:
- Контекст забивается, остался один сложный кусок
- Сделал N подэтапов, ещё M открыто, до конца не дотяну
- В задаче открылись новые вопросы дизайна

Действия:
1. Коммит того что уже работает (с зелёными тестами)
2. Запись в docs/progress.md - подробная, с ADR/gotcha/api-contract
   обновлёнными по ходу
3. Обновить roadmap.md
4. **Обязательно обновить docs/SESSION_START_PROMPT.md** - список
   открытых TODO, актуальные коммиты, следующий приоритет, обновлённое
   приветствие. Это главное - новая сессия читает его first thing
   и продолжает работу автономно
5. Коммит docs (`docs: handoff Сессии N`)
6. Короткое сообщение Абдуле «закрыл X, в SESSION_START_PROMPT
   следующий приоритет Y». **Не нужно ждать апрува** на следующую
   сессию - режим автономии. Абдула сам решит когда start новую

ПАМЯТЬ И FEEDBACK:
- Корректирующий feedback ("не делай так") → сохраняй в auto-memory
- Подтверждение неочевидного решения ("да, это правильно") → тоже
- Перед началом загляни в MEMORY.md и feedback_*.md

══════════════════════════════════════════════
СТИЛЬ ОБЩЕНИЯ
══════════════════════════════════════════════
- Русский, нижний регистр в начале предложений, без точек в конце
- Короткое тире "-", не длинное "—"
- enum-значения в бэктиках с русским рядом: разворот (`TURN`)
- Перечисления 2+ длинных элементов - списком через дефис
- Без пафосных заголовков ("Статус:", "Вывод:") - живым языком
- Технические термины и идентификаторы - на английском в коде,
  русский в обсуждении
- Если неуверен - спроси, не догадывайся

══════════════════════════════════════════════
ПРИВЕТСТВИЕ
══════════════════════════════════════════════
После прочтения 5+ файлов из START-OF-SESSION PROTOCOL начни ответ
с короткого summary последнего состояния и предложения. Например:

"вижу - Сессия 28 закрыла **весь этап 25.b** (object storage от
ADR-024 до production MinIO streaming) в 6 фазах + правило про
backend rerun. 9 коммитов:

- docs aafcfc0 (25.b.1) - ADR-024 (7 решений пакетом: S3-compatible
  AWS SDK v2, permanent storage не cache, versioning forever,
  library_files catalog, SHA-256, 4-bucket criticality, GDPR
  soft+two-phase hard-delete) + architecture-platform.md + glossary
- feat(backend) 6189ee6 (25.b.2) - Liquibase миграция 21 +
  LibraryFileSourceType + LibraryFile record + LibraryFileRepository
  (save/update/findActive*/softDelete/hardDelete/markVerified) + 19 IT
- docs cefc151 - handoff Сессии 28 Phase 2
- docs 43aac93 - правило про Claude-side backend/frontend rerun с JDWP
  debug args (старое 'backend запускает Абдула' удалено)
- feat(backend) c7afd88 (25.b.3) - docker-compose minio + minio-init
  (4 bucket'а с versioning ON на 3 critical) + AWS SDK v2 2.44.4 через
  bom-import + S3ClientConfig bean + ObjectStorageProperties + smoke IT
- docs ea443ac - handoff Phase 3
- feat(backend) 14c82ef (25.b.4) - ObjectStorageService API +
  Testcontainers MinIO IT
- feat(backend) 79b4534 (25.b.5) - интеграция в PdfLinksSourceProvider
  с двухуровневым cache (промежуточный шаг, был flaw)
- refactor(backend) 9e58b2d (25.b.6 final) - удалён local cache,
  PDF streaming напрямую из MinIO через StreamingResponseBody.
  PdfSourceProvider.locateFile → PdfLocation. PdfController использует
  ObjectStorageService.getRange напрямую

357 IT + 164 unit зелёные. End-to-end проверено playwright + restart:
PDF cache survives restart, второй click - mгновенный 'pdf cache hit
catalog=<uuid>' без upstream call.

Backend running с PdfService/PdfLocation/StreamingResponseBody pipeline,
JDWP :5005, MinIO UP с persistent 1.5MiB PDF object, production-БД
library_files имеет 1 smoke row.

Главный приоритет - выбор из вариантов: 18.f CitationPicker
(recommendation, домен forward) / ADR-025 lazy import direction /
25.d.2 page sync. Детально в progress.md Сессия 28 Phase 6
'Следующий шаг'."

Жди подтверждение. После него - смело за работу.
```

---

## Контрольные точки качества handoff'а

Хороший handoff даёт новой сессии:
1. **Что закрыто** - чтобы не переделывать
2. **Что открыто и в каком приоритете** - чтобы знать с чего
3. **Контекст последних решений** (ADR-N) - чтобы не нарушить
4. **Текущая инфра** (порты, UUID, тестовая тема) - чтобы сразу
   запустить smoke
5. **Указатели на ключевые файлы** - чтобы не искать вслепую
6. **Памятки про известные ловушки** - чтобы не наступить

Если в handoff'е чего-то нет, и новая сессия задаёт вопрос
"а где X" - значит handoff неполный, надо доработать этот файл.
