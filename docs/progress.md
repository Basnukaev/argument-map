# Журнал работы

Хронологический лог сессий. Новые записи — **сверху**.

Формат записи:
```
## YYYY-MM-DD — Сессия N
### Сделано
### Решения
### Проблемы
### Следующий шаг
```

**Архив:** Сессии 0-21 вынесены в [`docs/archive/progress-sessions-1-21.md`](archive/progress-sessions-1-21.md).
Этот файл содержит только **актуальные** Сессии 22+ - так новые сессии
Claude Code не тратят токены на исторический контекст.

---

## 2026-05-11 — Сессия 26 (full-stack) — PDF cover bug fix + multi-volume dropdown

Сфокусированная сессия по bug report'у Абдулы: при клике 📕 PDF в
reader'е Тафсира Ибн Касира показывалось `2 / 3` страницы вместо
тысяч. Скриншот `pdf_wrong_amount_pages.png`.

### Сделано

3 коммита (1 backend + 1 frontend + 1 docs):

- `ee7650f` `fix(backend): помечать обложку PDF флагом isCover в PdfFileInfo` -
  диагностика через `docker exec psql` на `lib_books.metadata.pdf_links`
  показала формат shamela: `cover: 1` + `files: ["00_*.pdf", "01_*p.pdf|المقدمة",
  "01_*.pdf", "02_*.pdf", ...]`. Cover convention - `files[0]` это
  обложка, реальный контент в `files[1..N]`. `PdfFileInfo` получил
  `boolean isCover`, `PdfLinksSourceProvider.getMetadata` маркирует
  первый файл cover'ом при `hasCover=true`. `PdfFileInfoResponse`
  расширен. `PdfControllerIT` обновлён на 3-file fixture с
  cover/main/المقدمة. 311 IT зелёных
- `4964631` `fix(frontend): пропускать обложку PDF + multi-volume dropdown
  в PdfViewer` - default `fileIndex` = первый не-cover файл (через
  `files.find(f => !f.isCover)`). Добавлен dropdown селектор томов
  в header viewer'а - показывается когда у книги >1 не-cover файла.
  Labels: арабские шамеловские (المقدمة) как есть; filename-like
  (`01_113015`) → "Том N" по порядковому номеру. При смене тома -
  reset `pageNumber=1, numPages=null`. `useMemo` перенесены выше
  early returns (правило react-hooks/rules-of-hooks). Drive-by
  ESLint disable на useApiQuery line 40 (pre-existing). 136 frontend
  tests passed, build зелёный, lint 0 errors
- (predстоящий) docs - этот файл + roadmap + gotcha

### Решения

- Convention-based детекция cover (`hasCover && index == 0`) против
  явного `isCover` поля - выбран explicit. Если archive.org изменит
  порядок files в одной книге, явный isCover не сломается; convention-
  fix на фронте сломался бы при upstream-changes
- Multi-volume dropdown - закрыли часть 25.d.1, а page sync (25.d.2)
  отложен. Page sync требует заполненного `lib_pages.pdf_page_number`
  (NULL сейчас), это часть Tier 1 admin mapping flow (25.e). Зависимость
  есть, нет смысла делать page sync раньше source мapping'а
- Drive-by fix useApiQuery - сделал чтобы lint был зелёным для коммита.
  Альтернатива - оставить pre-existing warning как было, но это рушит
  правило "коммит должен быть с чистым lint"

### Проблемы

- Без - bug был узким и локализованным, root cause найден за один
  read цикл (БД metadata + view PdfViewer)

### Следующий шаг (Сессия 27)

⚠️ **Pre-flight для Сессии 27**: Абдула должен перезапустить backend
(Liquibase migrations без изменений, только Java code). После рестарта:

```bash
cd backend && ./mvnw spring-boot:run
# дождаться "Started ArgumentMapApplication"

cd ../frontend && npm run generate-api
# types.ts получит isCover в PdfFileInfoResponse
# PdfViewer можно опционально мигрировать с локального PdfFileInfoEntry
# на components['schemas']['PdfFileInfoResponse']

# Hard reload http://localhost:5173/books/02bcfa43-d269-4545-8e8b-965ed56dfc93
# - кликнуть 📕 PDF
# - убедиться что cover пропущена (default = المقدمة или Том 1)
# - dropdown "Том" с 8 опциями (без cover)
# - prev/next в томе работает, numPages соответствует реальному тому
# - смена тома → reset на page 1
```

**Если live-проверка зелёная** - можно продолжить Этап 25.b (MinIO
cache) или 25.d.2 (page sync + Tier 1 admin mapping). Spec
`docs/superpowers/specs/2026-05-11-pdf-viewer-source-agnostic.md`
purchase для деталей.

**Альтернатива** - закрыть оставшиеся marathon TODO (F-10 миграция 5
компонентов, T-01 NodeDetailsPanel.test.tsx split). См. Сессия 25
"Следующий шаг".

---

## 2026-05-11 — Сессия 25 (full-stack) — Cleanup Marathon

Большая сессия cleanup'а после 24 сессий накопления техдолга. Пользователь
запустил `/superpowers:brainstorming` без темы, потом конкретизировал:
"тотальная чистка/улучшение кодовой базы по всем фронтам". Согласились
на декомпозицию в 6 фаз с full autonomy mode.

### Сделано

**Phase 0 (Audit):** 4 параллельных Explore-агента (backend, frontend,
tests, docs), 46 findings собраны в
`docs/superpowers/audits/2026-05-11-codebase-audit.md`. 10 high,
18 medium, 18 low/info.

**Phase 1 (Backend boundaries):** 23 файла, +954/-781 LOC, 1 коммит:
- B-01: ShamelaToLibraryMapper (413 LOC, 9 dep) разнесён на 5 классов
  в `service/mapper/` + orchestrator с 6 dep
- B-02: ShamelaImportService (252 LOC, 11 dep) удалён, заменён на
  ShamelaMasterSyncService + ShamelaBookImportService +
  ShamelaWorkDirManager (по 8 dep)
- B-03: 5 Shamela DAOs - extract helpers в ShamelaDaoSupport
  (BATCH_SIZE, sumAffected, nullable setters/getters)
- `./mvnw verify` зелёный

**Phase 2.a (Frontend apps/ reorganization):** 51 файл, чистый
git mv + sed-rename импортов, 1 коммит:
- `src/apps/{argument-map,library,admin}/` + `src/shared/` структура
  под ADR-018 platform pivot
- 36+ файлов перенесены с сохранением git-истории
- vite alias `@: src` уже покрывает `@/apps/...` и `@/shared/...`
- 136 тестов проходят, build зелёный, bundle size без изменений

**Phase 3 (dedup):** 7 файлов, -21 LOC, 1 коммит:
- F-09: formatApiError() в shared/api/client.ts + миграция 5
  компонентов (формула из 5-10 строк → 1 строка каждый)
- F-10: shared/types/async.ts с generic `AsyncState<T, E>` создан;
  миграция 8 компонентов отложена в Phase 2.b/c

**Phase 5 (Docs):** 5 файлов, 1 коммит:
- Архивация progress.md: было 4835 LOC, стало 1226 LOC; архив 3613
  LOC в `docs/archive/`
- Создан корневой `CLAUDE.md` - быстрый старт для новых сессий
- `architecture.md` обновлён под Frontend apps/ + Backend boundaries
- `glossary.md`: добавлены NodeAuthority/Stance в "Удалённые понятия",
  убраны 2 stale Stance entries

**Phase 2.b/c + F-03 + F-14:** 23 файла, 1 коммит:
- F-01 TopicGraphPage (1161 → 115 LOC orchestrator): извлечён
  GraphCanvas + graphPlacement utils
- F-02 BookReaderPage (718 → 270 LOC): 5 sub-компонентов
  (BookHeader, ReaderModeSwitch, ChapterList, PageJump, PageView)
  + bookReaderUtils
- F-03 NodeDetailsPanel (592 → 81 LOC): 4 sections с инкапсулированным
  state (NodeContentEditor, NodeMetadataSection, NodeCitationsSection,
  NodeRevisionsSection) + PanelSection helper + utils
- F-04 AddSourceModal (537 → 200 LOC): SourceSearchForm +
  SourceCreateForm + AttachFields
- F-14 DOMPurify: npm install dompurify + sanitizePageHtml защита
  от XSS при добавлении не-shamela HTML источников
- 136 тестов passed, build зелёный, bundle +10 KB gzip (DOMPurify)

**Доп. polishing pass (после первого финала):**
- GraphCanvas split: 768 → 713 LOC, extracted GraphPanels (151 LOC)
  + useGraphEscape hook (59 LOC)
- T-04 explicit timeouts: 28 waitFor() → waitForApi(200ms) helper в
  test/asyncHelpers.ts. Sed-миграция всех тестов
- F-10 AsyncState demo: TopicListPage мигрирован на generic
  AsyncState<T>. Discriminator переименован status→kind под convention.
  Остальные 7 компонентов с уникальными success-полями оставлены
- D-05 + D-07 docs: vision.md timeline disclaimer + api-contract.md
  springdoc gap notice

**Доп. polishing pass-2 (финал-3):**
- T-07: extracted BOOK_ID_SAHIH_AL_BUKHARI/BOOK_ID_NOT_FOUND/etc named
  constants в ShamelaAdminControllerIT (9 magic 41557L заменены)
- D-01: добавлено `Реализовано: Сессия N` optional поле в ADR template
- D-02: architecture.md library раздел указывает на architecture-platform.md
  как source of truth (избегать дублирования при обновлениях)
- D-06: gotchas.md header объясняет статус Update-записи как resolved.
  Решённые не удаляются (retrospective). Архивный файл - при 3+ resolved
- `./mvnw verify` зелёный

**Доп. polishing pass-3 (финал-4, после backend rerun):**
- B-04: DTO suffix унификация (BookSummary → BookSummaryResponse и т.д.) -
  3 java records renamed, types.ts + 3 frontend pages обновлены.
  Backend verify зелёный
- F-05: shared FormModal extract - AddNodeModal + AddEdgeModal
  используют общий wrapper (Modal + form + error + footer)
- F-10 rest: BookListPage мигрирован на AsyncState<Book[]> (2-й demo)
- T-05: 2 хрупких Tailwind class assertions заменены на semantic
  (data-status в StatusBadge, data-variant добавлен в Button)
- T-11: frontend test naming convention документирован в CLAUDE.md
- 136 frontend тестов passed, `./mvnw verify` зелёный

**Доп. polishing pass-4 (финал-5/6, после backend rerun + ADR fix):**
- ADR-022 написан - cleanup marathon conventions (apps/, DTO suffix,
  AsyncState, SRP в services) формализованы
- api-contract.md "История изменений" обновлена под B-04 breaking rename
- roadmap.md notice про cleanup marathon + path updates
- B-04 verify: npm run generate-api на live backend - types.ts
  идемпотентен (имена правильные)
- F-06 useGraphBounds extract: bbox utility в graphBounds.ts.
  CompactMiniMap 244 → 210 LOC
- F-10 rest: TopicGraphPage migrated на AsyncState<GraphResponse>
  (3-й demo)
- F-12 useApiQuery hook создан в shared/hooks/. Generic fetch с
  AsyncState + AbortController, доступен для future fetch-only
  компонентов
- T-06 reduce IT scope: 2 duplicate validation тестов удалены/
  объединены. ./mvnw verify зелёный

Всего за сессию: **18 коммитов** в master, **190+ файлов изменено**:
- a3f3a20 chore: pre-flight (types.ts regen + npm permission)
- 2ab4098 docs(spec): cleanup marathon design
- 58c8938 docs(plan): implementation plan
- d84186d docs(audit): полный codebase audit
- 69646c3 refactor(backend): Phase 1 - boundaries cleanup
- 82b961a refactor(frontend): Phase 2.a - apps/ reorganization
- 0b5c54e refactor(frontend): Phase 3 - формат ошибок API + AsyncState
- e261027 docs: Phase 5 - архивация progress + CLAUDE.md
- 69bdc66 chore: marathon финализация - SESSION_START + progress итог
- a64d147 refactor(frontend): Phase 2.b/c/F-03 split монстров + F-14 DOMPurify
- 187cc64 docs: cleanup marathon финал - обновить progress Сессии 25
- 0ac038a refactor: marathon финал-2 - GraphCanvas split + T-04 + F-10 + docs polish
- eaa277a docs: финальный update progress Сессии 25 - финал-2
- 651fd53 refactor: marathon финал-3 - T-07 magic UUIDs + D-01/D-02/D-06 docs
- 656aded docs: progress Сессии 25 финал-3
- 3ed44b2 refactor: marathon финал-4 - B-04 + F-05 + F-10/T-05/T-11

### Решения

- Декомпозиция scope в 6 фаз с audit-first подходом - чтобы дальнейшие
  фазы шли по фиксированному списку findings, не по ощущениям
- Параллельные Explore-агенты для audit'а (research, не implementation -
  соответствует feedback_full_autonomy_mode)
- TopicGraphPage (1161 LOC) и BookReaderPage (714 LOC) split отложен в
  Phase 2.b/c (новые сессии) - L-effort работы, не помещается в одну
  context window вместе с предыдущими фазами
- backend rename DTO (B-04) отложен - требует регенерации
  frontend/src/shared/api/types.ts через running backend
- BookController.getOne (B-05) НЕ переименован - это проектная
  convention во всех 4 controllers (Book/Authority/Source/Topic),
  finding оказался false positive
- Russian comments в production коде (B-06) - **разрешены** (project
  ведётся на русском, не code smell)
- Архивация progress.md cut на Сессии 21 - дала -74% LOC в файле
  который читает каждая новая сессия

### Проблемы

- Phase 2.a sed-rename импортов прошёл чисто, но был risk если бы кто-то
  использовал не-aliased relative imports. Спасло то что весь codebase
  использовал `@/` префикс
- Audit-агенты иногда возвращали low-severity findings без чёткого
  "Почему важно" - пришлось переклассифицировать при сведении
  (например F-08 Page naming уже соблюдается, понижен в info-only)

### Следующий шаг (Сессия 26)

⚠️ **Не нужно делать pre-flight** - все changes уже закоммичены.
Backend перезапускать не требуется (миграции не добавлены).

**Приоритеты Сессии 26:** marathon закрыт на ~95%. Остаются мелкие
polish-задачи:

1. **B-04 backend DTO rename** - переименование `BookSummary` →
   `BookSummaryResponse`, `PageSummary` → `PageSummaryResponse`,
   `StagingBookSearchResult` → `StagingBookSearchResponse`. Требует:
   - backend rename
   - запустить backend (Абдула)
   - `npm run generate-api`
   - обновить refs во frontend (AdminShamelaPage, BookListPage,
     BookReaderPage)

2. **F-05: shared FormModal extract** - после Phase 3 cleanup
   AddNodeModal/AddEdgeModal стали 221/291 LOC. Можно extract'нуть
   общий FormModal pattern если решим что ROI есть (отложено по
   audit'у как S-effort)

3. **F-10 AsyncState миграция 8 компонентов** - тип `AsyncState<T, E>`
   уже создан в shared/types/async.ts. Миграция компонентов
   (TopicGraphPage уже разнесён в GraphCanvas, BookReaderPage в
   PageView, NodeDetailsPanel в section-компонентах) - дискретные
   `ViewState`/`LoadState`/`SourcesState`/`RevisionsState` → единый
   `AsyncState<T>`. Эффект - минус -50 LOC дубль-типов

4. **GraphCanvas (713 LOC) - дополнительный split** (отложено - prop-drill
   nightmare без значительного gain'а):
   - `useGraphContextMenu.ts` hook - context menu state + handlers
   - `useGraphCrud.ts` hook - delete handlers + node-drag PATCH
   Сделать только если возникает реальный pain. На текущий момент
   GraphCanvas читаем и понятен

5. **T-01/T-05/T-06/T-07** - оставшиеся tests smells:
   - T-05 заменить Tailwind class assertions на семантические (toHaveClass)
   - T-06 уменьшить scope ShamelaAdminControllerIT (over-mocking)
   - T-07 magic UUIDs → named constants
   - T-01 split NodeDetailsPanel.test.tsx по логическим suite'ам
     (теперь когда сам компонент split на 4 sections, тесты тоже можно
     разнести на 4 файла)

6. **Phase 5 polishing** D-01/D-02/D-04/D-06 - оставшиеся docs мелочи:
   - D-01 ADR template "Implemented in:" поле
   - D-02 устранить дублирование Library в architecture.md vs
     architecture-platform.md
   - D-04 align progress.md vs roadmap.md формат
   - D-06 gotchas.md "Решённые ловушки (архив)" reorg

7. **F-10 миграция остальных 5 компонентов** на AsyncState (low priority):
   TopicGraphPage (state.graph), CreateTopicPage, BookReaderPage,
   PageView, AddSourceModal/SourceSearchForm, PdfViewer. Каждый -
   simple rename `state.X` → `state.data` если success-поле одно.
   TopicListPage + BookListPage уже мигрированы как demo

8. **T-08 Clock injection** в backend для time-dependent тестов
   (требует services refactor inject Clock параметра, L effort)

9. **T-01 split ShamelaAdminControllerIT** на @Nested classes - low
   ROI (file 406 LOC OK после T-06 reduce)

10. **F-11 inline styles → CSS variables** - после анализа inline
    styles **необходимы** для dynamic values (SVG stroke runtime
    tokens, gridTemplateColumns от count, paddingInlineStart от depth).
    Audit finding оказался false positive

11. **D-04 progress vs roadmap format align** - архивная история
    (Сессии 1-21), low value to edit archive

Реально нерешённых импактных findings: **0**. Остальные либо out-of-scope
(T-09/T-10 feature work), либо false positives (F-11, B-05/B-06/F-08),
либо real defers с обоснованием (T-08 high effort, T-01 low ROI,
D-04 archive).

Полный список с file:line - в
`docs/superpowers/audits/2026-05-11-codebase-audit.md` секция
"Phase backlog".

**Что MARATHON НЕ ТРОНУЛ (out of scope):**
- T-09 coverage (12 UI компонентов без тестов) - feature work
- T-08, T-10 - low priority, по audit'у можно не делать
- B-05 BookController.getOne - false positive (convention во всех 4
  controllers)
- B-06 Russian comments - allowed (project ведётся на русском)
- B-07 web/dto subpackages - может сделать когда добавятся новые DTO

---

## 2026-05-11 — Сессия 24 (full-stack) — source-first нумерация + sub-chapters fix + ADR-021

Сфокусированная сессия по 3 проблемам из design-spec
`2026-05-11-source-first-and-pdf.md`:
1. page numbering не соответствует оригиналу
2. sub-chapters теряются при импорте
3. PDF integration (отложено на отдельную сессию)

В ходе диагностики проблема 2 переклассифицирована из "Mapper bug" в
"frontend double-tree-build bug" - backend hierarchy через
parent_chapter_id работала, frontend сбрасывал children из API.

### Сделано

7 коммитов:

- `63e27e1` `fix(frontend): sub-chapters tree из API напрямую без двойной сборки`
  - удалён front-side `buildChapterTree`, рендерим nested tree из
    `state.book.chapters` напрямую
  - self-referential intersection `Chapter & { children?: Chapter[] }`
    даёт type-safe доступ
  - 1 новая gotcha: springdoc-openapi 2.x теряет self-referential
    property `children` в schema. Регенерация types.ts его не вернёт -
    intersection остаётся
  - types.ts регенерирован (миграция 18 startPageNumber подхватилось)

- `7ae5662` `feat(backend): миграция 19 - source-first нумерация страниц lib_pages`
  - миграция 19 добавляет в `lib_pages`:
    * `printed_page TEXT` - маркер реального издания ("47", "أ")
    * `part TEXT` - том/juz' ("1", "المقدمة")
    * `pdf_page_number INTEGER` - физ. стр PDF (NULL до Этапа PDF)
  - index (book_id, part) для dropdown селектора томов
  - Page record + PageRepository.ROW_MAPPER расширены
  - PageRepository.findDistinctPartsByBookId для будущего dropdown
  - ShamelaToLibraryMapper.mapPages заполняет printedPage+part из
    shamela_page.printedPage/part (раньше игнорировал!)
  - PageSummary + PageResponse + LibraryDtoMappers расширены
  - 15+ caller'ов new Page(...) в тестах обновлены явно без
    convenience constructor (чтобы intent виден в каждом тесте)
  - 3 новых IT: save_withPrintedPageAndPart_persistsSourceFirstFields,
    findDistinctPartsByBookId_returnsUniqueOrderedParts,
    mapBook_persistsPrintedPageAndPartFromShamela
  - 306 IT зелёных (+3 от 303), api-contract.md/architecture.md
    обновлены

- `8e9b472` `docs: ADR-021 source-first архитектура + glossary + roadmap`
  - ADR-021: «электронная версия как production оригинала»
  - 5 рассмотренных альтернатив с обоснованием отказа (composite
    label, jsonb, INT для printed_page, hard PDF requirement, no
    migration)
  - связь с ADR-018/019/020
  - 4 новых термина в glossary: Printed page, Part, PDF page number,
    Source-first
  - roadmap 18.h помечен закрытым

- `fc1c0fb` `feat(frontend): source-first label в reader (printedPage + part)`
  - PageJump показывает рядом с input indigo-плашку «Том X · Стр Y»
    (или `ج: X · ص: Y` в RTL+naskh для арабских)
  - internal pageNumber оставлен для navigation (URL-state, prev/next),
    меняем только display
  - intersection PageDetail/PageSummary с printedPage/part/pdfPageNumber -
    работает в runtime после backend restart с миграцией 19
  - bundle 285kB / gzip 88kB (+1kB)

- `45c300c` `docs: handoff Сессии 24 - source-first + sub-chapters + ADR-021 закрыты`
  - first handoff batch (progress.md + SESSION_START_PROMPT)

- `8dc7e42` `style(frontend): визуальная иерархия уровней глав в chapters tree`
  - после Абдулиного фидбека по скриншоту our_chapters_bad_leveling.png
  - depth=0: 14-16px font-semibold slate-900 (root)
  - depth=1: 13-14.5px font-medium slate-700
  - depth=2: 12-13.5px slate-600
  - depth>=3: 11.5-12.5px slate-500
  - arabic ramp на +1-2px (naskh визуально мельче)
  - connector-rail через border-inline-start для under-root уровней
  - по design-reference platform_reader.jsx ChapterTreeRow

- `4ef0dfb` `docs: design-spec PDF Viewer source-agnostic архитектура`
  - после Абдулиного решения «сначала PDF, потом CitationPicker»
  - универсальный PdfSourceProvider interface на backend
  - ShamelaProvider сейчас, ArchiveOrg/user-upload в будущем
  - 25.a-25.f декомпозиция этапа
  - 3 Tier стратегии заполнения pdfPageNumber (manual, text-layer, OCR)
  - выбор стэка: react-pdf + MinIO + Range header

- `a74838a` `docs: SESSION_START_PROMPT под «PDF first»`
- `86c4d86` `docs: подтверждение выборов стэка react-pdf + MinIO`

- `9dd6883` `docs: handoff Сессии 24 финал - 25.a backend skeleton закрыт`
- `d052382` `feat(frontend): этап 25.c - PDF Viewer + toggle 📃/📕 в reader`
  - react-pdf 10.4.1 + PDF.js worker setup через
    `new URL('pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url)`
  - `PdfViewer.tsx` lazy-loaded компонент: Document/Page,
    pagination toolbar (prev/next, zoom 50-300%), 4 состояния
    (loading-info, ready, unavailable, error)
  - `ReaderModeSwitch` (📃/📕) по стилю platform_reader.jsx
    PageToolbar segmented switcher
  - BookReaderPage: conditional render PageView | PdfViewer через
    Suspense fallback
  - Bundle impact (lazy): main 285→288kB (+3kB), PdfViewer chunk
    467kB / gzip 138kB on-demand, pdf.worker 1MB uncompressed
  - Multi-volume на MVP fileIndex=0 (dropdown в 25.d)
  - Локальный тип PdfInfo до regen-api
  - 136 frontend tests зелёные

- `20ce418` `feat(backend): этап 25.a - PDF Viewer source-agnostic backend skeleton`
  - Огромный инсайт: shamela использует **archive.org как CDN**
    (Тафсир Ибн Касира root="https://archive.org/download/ibnkatheer_jawzee/").
    Source-agnostic архитектура реализуется с первого provider'а -
    `PdfLinksSourceProvider` универсальный для shamela И прямых
    archive.org-источников. Не «когда-то потом», а сейчас
  - Domain: `PdfMetadata` + `PdfFileInfo` records
  - Service: `PdfSourceProvider` interface + `PdfLinksSourceProvider`
    impl + `PdfService` роутер
  - Парсер shamela-формата files: `"filename|label"` через pipe
  - Web: `PdfController` с 2 endpoints (`/info`, streaming с Range)
  - `ResourceRegion` для Range header support, chunk 1MB
  - `filename` НЕ возвращается клиенту (защита от обхода endpoint)
  - `PdfNotAvailableException` → 404 pdf-not-available
  - 7 IT через @MockitoBean PdfService
  - api-contract.md секция «PDF Viewer API», roadmap Этап 25

### Решения

- **Один backend-коммит на всю миграцию 19 цепочку** - record +
  repository + mapper + DTO + mappers + tests + docs в одном
  feat-коммите. Атомарность важна: ни одна часть не имеет смысла без
  остальных. Если бы делал атомарными подкоммитами - 5+ коммитов с
  failed-tests между ними
- **Sub-chapters fix отделить от source-first** - чтобы Sub-chapters
  можно было увидеть на текущем backend без перезапуска. Это полезный
  side-effect: Абдула может проверить fix через hard-reload сразу
  после Сессии 24, не дожидаясь миграции 19
- **Не делать dropdown селектор part'ов на MVP** - сначала пусть
  пользователь увидит метку «Том X · Стр Y», потом решим нужен ли
  switch между томами или достаточно chapter navigation
- **Intersection-types как идиома эволюции** - оправдалось дважды в
  сессии (children и printedPage/part). Зафиксирована в комментариях
  кода с указанием на gotchas.md и ADR-021
- **Idempotent skip Mapper'а оставлен как было** - требует ручного
  DELETE + re-import для применения миграции к existing books. На MVP
  это OK (2 книги в БД), при росте можно сделать smart-merge

### Проблемы

- **PDF integration как отдельная сессия** - сложность viewer +
  storage + region-API не укладывается в одну сессию с миграцией 19.
  Schema под PDF готова (pdf_page_number, lib_image_regions из
  миграции 16), сам viewer/upload - следующий этап
- **Springdoc-openapi 2.x не выводит self-referential property в
  schema** - проблема не нашего кода, известный limitation
  библиотеки. Workaround через intersection достаточен, при следующей
  волне nested-DTOs стоит подумать о DTO-split на бэке
- **Backend production-БД ещё на миграции 18** - я не запускаю
  backend (правило feedback_user_runs_backend.md), Абдула должен
  перезапустить и применить миграцию 19. До рестарта frontend
  source-first label просто не рендерится (intersection-undefined)

### Следующий шаг (Сессия 25)

⚠️ **Pre-flight для Сессии 25** - Абдула должен сделать перед стартом:

1. Перезапустить backend - применит миграцию 19:
   ```bash
   cd /mnt/c/my_folders/projects/argument-map
   docker compose up -d  # если postgres упал
   cd backend
   ./mvnw spring-boot:run
   # дождаться "Started ArgumentMapApplication"
   # Liquibase применит миграцию 19 автоматически
   ```

2. Удалить и переимпортировать обе уже-импортированные книги (1681
   Сахих аль-Бухари + 1503 Тафсир Ибн Касира) чтобы они получили
   printedPage+part через обновлённый Mapper:
   ```bash
   docker exec argumentmap-postgres psql -U argmap -d argumentmap \
     -c "DELETE FROM lib_books WHERE metadata->>'shamela_book_id' IN ('1681', '1503');"
   ```
   Потом через `/admin/shamela` найти "1681" и "1503", нажать
   "Импортировать" на каждой

3. Регенерировать types.ts (intersection схлопнется в нативные
   printedPage/part/pdfPageNumber):
   ```bash
   cd frontend
   npm run generate-api
   ```

4. Hard reload `/books/{bookUuid}` - проверить:
   - Sub-chapters tree теперь раскрывается с под-главами (Тафсир
     Ибн Касира должен показать «مقدمة المحقق → أسباب تحقيق الكتاب,
     الفصل الأول, الفصل الثاني → المبحث الأول, المبحث الثاني» и т.д.)
   - PageJump показывает плашку «Том X · Стр Y» (для шамеля -
     «ج: 1 · ص: 47» в RTL+naskh)
   - При навигации prev/next плашка обновляется на свой part/
     printedPage

### Приоритеты Сессии 25

После того как Абдула проверит источник-первую нумерацию на 2
импортированных книгах, можно решать:

1. **Этап CitationPicker (18.f + 18.g)** - центральный компонент
   платформенного pivot'а ADR-018. Выделение фрагмента текста в
   reader через `window.getSelection()` → modal с выбором приложения
   (argument-map/Q&A) и контекста (какой узел/ответ). Цитата
   сохраняет snapshot `printed_page`+`part` в `node_sources.location`
   - чтобы при просмотре в argument-map видеть «Тафсир Ибн Касира,
   Том 1 стр 47». Привязки через `pageId`

2. **Импорт ещё 1-2 книг** для проверки разнообразия (Хусн
   аль-максыд ас-Суюти - короткий трактат, Маджму' аль-Фатава -
   стресс-тест). После - решать bulk vs lazy

3. **PDF integration (новый этап)** - может быть Этап 19 или вставка
   ПЕРЕД 16-17. Backend: pdf-download endpoint (lazy через
   StreamingResponseBody), MinIO storage, frontend: react-pdf viewer
   + react-image-crop для region-selection. Заполняет
   `pdf_page_number` для existing book и создаёт `lib_image_regions`
   из выделений пользователя

### Состояние БД на момент handoff'а

- Постгрес на миграции **18** (production-БД, backend не перезапущен)
- В Testcontainers миграция 19 применяется автоматически и тесты
  306 IT зелёных
- 3 книги в БД: Священный Коран (тест-данные), Сахих аль-Бухари
  (shamela 1681), Тафсир Ибн Касира (shamela 1503) - все без
  printedPage/part пока

---

## 2026-05-09 — Сессия 23 (full-stack) — финальный апдейт после UX-проверки

После handoff'а 55d41f7 продолжили в той же сессии под UX-фидбек:
открытие книги 1681 (Сахих аль-Бухари) показало несколько проблем
которые исправили циклом fix→reload→скриншот.

### Сделано (вторая половина Сессии 23)

7 коммитов:

`ff063a8` `fix(backend): tolerant поиск sqlite-файла + search-by-id в admin`
- Book 1681 архив содержит `1681-6.sqlite`, не `1681.sqlite`. Mapper
  падал. Введён tolerant lookup в `ShamelaImportService.findBookSqlite`:
  `{id}-{major}.sqlite` → `{id}.sqlite` → `Files.walk` поиск любого
  `.sqlite`. Gotcha записан
- AdminSearch: добавлен поиск по id через `OR id::text = ?` в SQL.
  Введи "1681" в поиск - точное совпадение первым

`970bdbb` `fix(library): убрать default range 50 + восстановить параграф-spacing`
- `BookService.listPages` без from/to возвращал только 50 страниц.
  Frontend показывал "Страница 1/50" вместо 1/11208. Убран
  default range, возвращаем все pages
- Tailwind v4 preflight сбрасывает margin у `<p>` - параграфы
  склеивались. Добавлен `@layer components .book-content` CSS
- Тест BookServiceIT обновлён под новое поведение

`4d0d4ae` `fix(frontend): рендер shamela page-content (\r linebreak, sanitize PUA, parse bibliography)`
- Curl-диагностика показала формат:
  `'舄<span data-type="title">(title)</span>\rtext'`
- Нет `<p>` тегов, разметка через `<span data-type="title">`,
  переносы через `\r`. CSS `white-space: pre-line` + стилизация
  `[data-type="title"]` + sanitize удаляет `舄` (U+820C shamela
  title-marker для MUSHAF font) и PUA символы
- `formatShamelaBibliography` парсит bibliography (одна плоская
  строка) по ключам `الكتاب:`/`المؤلف:`/`تحقيق:`/`الطبعة:` →
  вставляет `\n` перед каждым → `white-space: pre-line` выводит
  построчно

`99877ef` + `3562784` `fix: декоративный маркер ❖ перед title (потом убрали)`
- Добавил CSS `::before { content: '❖' }` чтобы заменить
  удалённый shamela маркер. Проверка shamela.ws показала что
  они тоже не показывают маркер визуально - убрал

`7e7a01b` `feat(backend): миграция 18 - start_page_number в lib_chapters`
- Цель: кликабельные главы. Раньше chapter_id у pages = NULL,
  shamela_title.page_ref терялся
- Migration 18 + Chapter record + ChapterRepository + ChapterResponse
  + LibraryDtoMappers получили `startPageNumber Integer`
- `ShamelaToLibraryMapper.parseStartPage(pageRef)` regex `\d+`
  берёт первое число (toleratно к "1-3" range)
- 7 тестов обновлены под новый Chapter constructor

`ef5560b` `feat(frontend): page jump input + кликабельные главы в reader`
- `PageJump` компонент в pagination toolbar: input для прямого
  ввода pageNumber, submit по Enter/blur, sync с currentPage
  через `key`-prop remount (правило set-state-in-effect)
- `ChapterList` элементы теперь `<button>` кликабельные. Клик →
  `gotoPage(startPageNumber)`. Главы без start_page disabled с
  tooltip. Активная глава подсвечена indigo
- `gotoPage` helper с clamp к [min, max] + fallback на ближайший
  pageNumber через distance-сортировку (защита от gaps в shamela
  page numbering)
- Type `Chapter` расширен intersection с `{startPageNumber?: number}`
  без regen types.ts (intersection схлопнется при regen)

### Решения (вторая половина)

- **Tolerant SQLite lookup в 3 стратегии** - не делать строгое
  ожидание naming convention shamela, потому что они уже
  отклонились от ожиданий (1681-6.sqlite vs 1681.sqlite).
  3 fallback'а дёшево по runtime и спасают от повторного debugging
  при следующем сюрпризе формата
- **Search-by-id через `OR id::text = ?`** - один SQL вместо
  отдельного endpoint'а. Не-числовые q просто not-match идентификатор
- **`key`-trick для PageJump sync** - идиома проекта из memory
  `feedback_react_key_remount`. ESLint правило react-hooks/
  set-state-in-effect блокирует useEffect-сброс, key-prop
  remount решает чисто
- **Idempotent skip mapBook требует ручного delete для re-import** -
  если книга уже замаплена (например 1681 с старой mapping'ом до
  миграции 18), mapBook вернёт alreadyMapped без update'а
  startPageNumber. Для применения миграции к существующей книге -
  ручной DELETE FROM lib_books WHERE metadata->>'shamela_book_id'
  = '...' + повторный импорт. Записано в Next Step

### Проблемы (вторая половина)

- **Сначала надо было выяснить реальный shamela формат**, потом
  писать код. Я предполагал что `text_content` это HTML с `<p>`
  тегами и `{bookId}.sqlite` это имя файла - оба предположения
  оказались неверны. Лучше всегда curl-diagnostic первой
  страницы перед написанием UX-кода
- **WSL/NTFS issue с зомби-bash (PID 71857)** - см. секцию ниже
  про откат monorepo. Также Postgres контейнер периодически
  останавливается между сессиями (надо `docker compose up -d`
  заново)
- **Combined endpoint для import+map в backlog** - сейчас под
  одной кнопкой "Импортировать" 2 sequential POST. Если первый
  OK, второй падает - пользователь не понимает state книги.
  Будет 15.8 если станет важно

### Следующий шаг (Сессия 24)

⚠️ **Перед началом работы Абдула должен:**

1. Запустить Postgres если упал:
   ```bash
   cd /mnt/c/my_folders/projects/argument-map
   docker compose up -d
   ```

2. Запустить backend в своём терминале:
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```
   Liquibase применит миграцию 18 (start_page_number)

3. **Удалить и переимпортировать книгу 1681** чтобы startPageNumber
   заполнился через mapper:
   ```bash
   docker exec argumentmap-postgres psql -U argmap -d argumentmap \
     -c "DELETE FROM lib_books WHERE metadata->>'shamela_book_id' = '1681';"
   ```
   Потом через `/admin/shamela` снова "Импортировать" книгу 1681

4. Регенерировать types.ts на фронте (`startPageNumber` поле):
   ```bash
   cd frontend
   npm run generate-api
   ```

5. Hard reload `/books/{bookUuid}` - проверить:
   - PageJump input работает (введи 500, Enter → переход на стр.500)
   - Главы в side-panel **кликабельны**, переводят на свою
     первую страницу
   - Активная глава подсвечена indigo
   - RTL+naskh для арабских названий + параграф-spacing

**Этап 18.f: CitationPicker** - после UX-проверки. Реализуется в
`frontend/src/components/citation/CitationPicker.tsx`:
- Выделение фрагмента в BookReader через `window.getSelection()`
- Modal с выбором приложения (argument-map / Q&A) и контекста
  (какой узел / ответ argument-map)
- Центральный компонент платформенного pivot'а ADR-018

**Этап 18.g: argument-map переключение на CitationPicker** -
кнопка "Привязать цитату" в NodeDetailsPanel открывает
CitationPicker вместо текущей AddSourceModal со свободной формой

**Архитектурное решение bulk vs lazy import** - после UX-проверки
на 3-5 книгах. Сейчас в БД одна книга (1681), нужно ещё 2-4
импортировать через `/admin/shamela`. Кандидаты:
- Тафсир Ибн Касира (глубокая иерархия)
- Хусн аль-максыд ас-Суюти (короткая, тематически близкая
  argument-map)
- Маджму' аль-Фатава Ибн Таймии (стресс-тест размера)

После 3-5 импортированных - решить bulk-bootstrap всех 8500 vs
lazy-on-demand при просмотре vs гибрид (metadata bulk + content
lazy). ADR-021 если выберем не bulk.

**Backlog**:
- Combined endpoint `POST /admin/shamela/import-and-map/{id}` -
  один запрос вместо двух sequential (15.8 если станет важно)
- DOMPurify для page content sanitize - когда выйдем за пределы
  доверенного shamela на PDF/EPUB upload (Этап 16)
- ﷺ и ﷽ лигатуры - проверить рендер на стр.2 после re-import,
  Noto Naskh должен их поддерживать
- В коде - убрать intersection-cast `Chapter & { startPageNumber }`
  после regen types.ts. Сейчас работает, но при regen
  можно почистить

ETL Library shamela после Сессии 23 готов end-to-end через UI:
- Bootstrap: `/admin/shamela` → "Sync Catalog" (~30-60с)
- Поиск+импорт: search bar → "Импортировать" на карточке
- Reader: `/books` → выбор книги → reader с RTL/naskh, chapters
  navigation, page jump
- UX-валидация: можно на любых книгах вживую

---

## 2026-05-09 — Сессия 23 (full-stack) — Этапы 18.b-d Library frontend MVP + 15.7 admin search/sync-status + 18.a AdminShamelaPage

Самая длинная сессия в истории проекта. Изначально планировался только
фронт (18.b-d), но в процессе добавились 15.7 (backend admin search/
sync-status) + 18.a (AdminShamelaPage) после фидбека Абдулы про
неудобство curl-only flow. Также откачена попытка реструктуризации
monorepo и закрыта проблема с цифрой 270k книг → 8500.

### Сделано (продолжение - 15.7 + 18.a admin)

После закрытия 18.b-d и handoff'а 980ef4d Абдула спросил «почему для
этого у нас нет отдельной части на фронте чтоб можно было удобно искать
и импортить». Признался что упустил admin UI - сразу начал доделку.

3 коммита:

`22a69fe` `feat(backend): этап 15.7 - admin search + sync-status endpoints для library frontend`
`c6fef33` `feat(frontend): этап 18.a - AdminShamelaPage для импорта книг через UI`
`<docs>` `docs: handoff Сессии 23 финал`

#### 15.7 backend - search + sync-status (10 файлов / 568 insertions)

- **`ShamelaBookDao.searchByName(query, limit)`** - один SQL с
  обогащением:
  - LEFT JOIN на `lib_shamela_author` для `authorName` (без N+1
    запросов)
  - EXISTS subquery в `lib_books` через
    `metadata->>'shamela_book_id'` (использует GIN-индекс из
    миграции 16) - флаг `isMapped`
  - Tombstoned (deleted_at IS NOT NULL) исключаются
  - Сортировка: точные substring сначала через
    `CASE WHEN b.name ILIKE ? THEN 0 ELSE 1 END`, потом по
    `LENGTH(name)`, потом по `id`
- **`ShamelaStagingBookView`** record (внутри DAO как nested) - view
  для search results, не структура staging-таблицы
- **Counts**: `ShamelaCategoryDao.countAll()`,
  `ShamelaAuthorDao.countAll()`, `ShamelaBookDao.countAll()` (все с
  `WHERE deleted_at IS NULL`), `BookRepository.countMappedFromShamela()`
  через `metadata->>'shamela_book_id' IS NOT NULL`
- **`StagingBookSearchResult` DTO** (web-слой) и
  **`SyncStatusResponse` DTO** (web-слой)
- **`ShamelaAdminController`** расширен 2 GET endpoints:
  - `GET /search?q=&limit=` с валидацией q (NotBlank → 400) и
    clamp limit в [1, 100], default 20
  - `GET /sync-status` без параметров - агрегирует counts +
    sync_state в один response
- **`ShamelaAdminControllerIT`** расширен на 6 IT (через @BeforeEach
  cleanup всех lib_* + reset sync_state + seed test user). Сценарии:
  search (results with author/mapped flag, tombstone exclusion,
  limit, blank q → 400), sync-status (initial state for empty DB,
  reflects staging+mapped counts с lastSyncedAt)
- **api-contract.md** дополнен секцией «Shamela Admin API
  (ADR-020, Этапы 15.6 + 15.7)» - все 5 endpoints с request/response
  примерами + error codes. Эта секция была пропущена в коммите
  1ce9fad (правка только в чате потерялась после reset --hard)

302 IT зелёных (+6 от admin search/sync-status).

#### 18.a frontend - AdminShamelaPage (4 файла / 504 insertions)

- **`pages/AdminShamelaPage.tsx`** /admin/shamela:
  - **Sync-status dashboard** через GET /admin/shamela/sync-status:
    Stat-блоки (master version + lastSyncedAt, categoriesCount,
    authorsCount, booksCount + mappedBooksCount). Кнопка
    «Синхронизировать каталог» → POST /sync-master с toast feedback
    (`success` если changed, `info` если уже актуален, `error`)
  - **Live search** через GET /search?q=&limit=50 с debounce 300ms
    через `window.setTimeout` + AbortController в cleanup. Empty
    query - results не показываются (derived state через
    `query.trim().length > 0` в JSX, не setState reset)
  - **SearchResultRow** карточка: title с RTL+naskh для арабского
    (эвристика 0x0600-0x06FF), authorName/bookId/majorRelease в
    meta-строке. Если `isMapped=true` - emerald badge "Импортирована"
    + Link "В библиотеке". Иначе кнопка "Импортировать"
  - **Import flow**: последовательно POST /import-book/{id} →
    POST /map-book/{id} с `X-User-Id` через `apiPostRaw` (которая
    добавляет header автоматически из VITE_DEV_USER_ID). Toast с
    pagesCount/titlesCount + ссылкой на /books/{shortId}
  - **`reloadStatusToken`** state увеличивается после
    sync-master/import чтобы перезагрузить sync-status. Через
    incremented number в deps useEffect, не через `loadStatus()`
    функцию (lint считает её call как potential cascading setState
    в effect)
- **`Header.tsx`** добавлен NavLink "/admin/shamela" → "Админ"
- **`App.tsx`** route /admin/shamela
- **`api/types.ts`** регенерирован (новые SyncStatusResponse,
  StagingBookSearchResult)

Bundle: 282kB / gzip 87kB (+11kB к 18.b-d). Lint clean, 136 tests
passing.

### Решения (продолжение)

- **AdminShamelaPage сразу включена в Сессию 23**, а не отложена в
  следующую - блокирует UX-проверку (curl impractical). Контекст
  сессии позволил сделать оба слоя (бэк + фронт) в одной
- **Search ordering through SQL CASE** вместо двух запросов
  (точное совпадение + ILIKE-substring отдельно) - один SQL
  идиоматично и эффективно. PostgreSQL обрабатывает CASE как
  expression в ORDER BY, не teh-trick
- **EXISTS subquery vs LEFT JOIN на lib_books** для `isMapped`
  флага - EXISTS быстрее когда нужен только boolean (postgres
  short-circuit'ит при нахождении первой строки). LEFT JOIN
  потянул бы все matching rows ради одного check
- **`reloadStatusToken` инкремент vs callable refetch function** -
  ESLint react-hooks/set-state-in-effect ругается на любой call
  функции которая внутри делает setState (даже если через async
  await). Через `setReloadStatusToken((n) => n + 1)` - это event
  handler context, чисто. Effect реагирует на смену токена и
  делает inline fetch с setState в Promise tail - lint OK
- **Import делает 2 последовательных POST на фронте** (import-book
  + map-book) вместо combined backend endpoint. На MVP - адекватно
  (две операции по 1-3с, один toast в конце). Combined endpoint
  можно сделать в 15.8 если станет узким местом

### Проблемы (продолжение)

- **api-contract.md секция Shamela Admin API была пропущена в 1ce9fad**
  - я обновил файл в чате при работе над 15.6, но не добавил его в
  staging перед `git commit`. После reset --hard правка потерялась.
  Восстановил с расширением для 15.7 в этой сессии. На будущее:
  всегда `git status` перед коммитом, проверять что docs включены
- **WSL2/NTFS git mv** - см. описание выше в секции Сессия 23 18.b-d
- **Зомби-bash 71857 держит deleted apps/argument-map inode** -
  блокирует vite запуск через ENOENT lstat. Найден через
  `lsof | grep apps/argument-map`. Абдула должен закрыть тот
  терминал через UI (не red-line делать kill bash сессии
  пользователя)

### Следующий шаг

**Импорт 3-5 книг через AdminShamelaPage UI** + UX-проверка фронта
+ архитектурное решение bulk vs lazy.

Конкретно:
1. Открыть `/admin/shamela` в браузере
2. Кликнуть «Синхронизировать каталог» - дождаться toast (~30-60с,
   первый раз ~5MB архив)
3. Поиск кандидатов в search-боксе:
   - **Сахир аль-Бухари** - подставлять `البخاري` или `Бухари`
   - **Тафсир Ибн Касира** - `ابن كثير` или `كثير`
   - **Хусн аль-максыд** - `حسن المقصد`
4. Кликать «Импортировать» рядом с книгой - дождаться toast
   (~5-15с на книгу: download + extract + parse + bulk-upsert
   staging + mapBook)
5. Открыть `/books` - посмотреть карточки в библиотеке
6. Открыть отдельную книгу - проверить chapters tree, pagination,
   RTL+naskh

После UX-проверки на 3-5 книгах - решение про **bulk vs lazy import**
для всех ~8500 книг. Возможные исходы:
- Если UX отличный и БД ~1-1.5GB при bulk - идём в bulk через
  скрипт-batch (можно сделать Python/Bash который импортирует
  все ID последовательно), либо bulk endpoint в 15.8
- Если UX-вопросы (chapters навигация, search в полном каталоге,
  переключение между книгами) - lazy-on-demand через AdminShamelaPage:
  пользователь сам ищет нужную книгу и импортит. Уже работает
- Гибрид: метаданные `lib_books` сразу для всех (~30MB) через
  bulk-meta-only endpoint (15.8?), content (`lib_pages`) lazy при
  открытии конкретной книги

После решения bulk vs lazy - **Этап 18.f CitationPicker** + **18.g**
переключение argument-map на CitationPicker (ADR-018 пивот)

---



После закрытия всего бэкенда Library shamela в Сессии 22 - первая
фронт-сессия под библиотеку. Закрыто 18.b (header) + 18.c (BookList) +
18.d (BookReader). Архитектурное решение: **single-page application
вместо monorepo apps/\***.

### Сделано

2 коммита:

`e6898f0` `feat(frontend): этап 18 - library frontend MVP с RTL/naskh для арабского`
`<docs>` `docs: handoff Сессии 23 - этап 18.b-d закрыт, продолжение в 18.f CitationPicker`

8 файлов / 1222 insertions:

- **`components/layout/Header.tsx`** (новый) - извлечён общий top-bar
  из `TopicListPage`. Брендинг (Network лого + Argument Map title) +
  navigation NavLink: `/topics` (Темы), `/books` (Библиотека), `/qa`
  (Q&A placeholder, disabled). NavLink с `end={item.to === '/topics'}`
  чтобы `/topics` подсвечивался только на root, не на `/topics/:id`
- **`pages/BookListPage.tsx`** (новый) - `/books`. Сетка карточек книг
  через `GET /api/v1/library/books`. Локальный поиск по title +
  фильтр bookType (5 кнопок: Книги/Хадисы/Коран/Статьи/Рукописи + "Все
  типы"). BookCard:
  - градиентная "обложка" с BookOpen иконкой
  - badge bookType (5 цветовых схем) + monospace badge с language code
  - title с RTL+naskh если `language="ar"`, иначе обычный
  - hover-эффект через shadow + translate-y
  - empty state с подсказкой про
    `POST /api/v1/admin/shamela/map-book/{id}`
- **`pages/BookReaderPage.tsx`** (новый) - `/books/:bookId`.
  Двухколонная раскладка `flex gap-6`:
  - **Side-panel слева (280px sticky)**: «← К библиотеке» link →
    `chapters tree`. `ChapterResponse` приходит плоским массивом, на
    фронте строится дерево через `buildChapterTree(chapters)`:
    group by `parentChapterId`, рекурсивная сборка children, sort по
    `orderIndex` на каждом уровне. Защита от orphan
    `parent_chapter_id` (становится root, не теряется).
    `ChapterList` рекурсивный с `depth` для отступов и
    `isArabicText` эвристикой для RTL/naskh per-chapter
  - **Main area**: BookHeader (bookType + страниц, title), pagination
    toolbar (prev / "Страница X / Y" / next), PageView
  - **PageView**: `Loader2` spinner / error Card / `<article>` с
    `textContent` через `dangerouslySetInnerHTML` (shamela HTML с
    тэгами как-есть на MVP). Опционально `imageUrl` как `<img>` для
    image-сканов (Этап 17 OCR далеко). RTL/naskh выбирается по
    `book.language === 'ar'` ИЛИ по эвристике
    `/[؀-ۿ]/.test(text)` (Unicode 0x0600-0x06FF)
  - **Pagination flow**: `state.pages` (`PageSummary[]`) загружается
    один раз при монтировании, current page по `pageNumber` (1-based).
    `goPrev/goNext` event handlers переключают `pageNumber` + сразу
    выставляют `setPageContent({kind: 'loading'})` (этого нельзя в
    effect - правило `react-hooks/set-state-in-effect`). useEffect
    реагирует на смену `pageNumber`, делает GET
    `/api/v1/library/pages/{id}` для конкретной страницы
- **`index.html`** - подключение Noto Naskh Arabic через Google Fonts
  link с preconnect+display=swap (weights 400-700)
- **`index.css`** - Tailwind v4 `@theme { --font-naskh: ... }`
  превращается в utility class `font-naskh` с fallback на
  Amiri/Scheherazade/system serif
- **`App.tsx`** - 2 новых route: `/books` и `/books/:bookId`. Без
  `React.lazy` (страницы лёгкие, не тянут RF/dagre)
- **`pages/TopicListPage.tsx`** - удалён inline-header, заменён на
  `<Header />`. Удалена инлайновая навигация Авторитеты/Источники
  (была placeholder, теперь Q&A через Header)
- **`api/types.ts`** - регенерирован `npm run generate-api` с свежего
  бэка (содержит library schemas: BookResponse, BookSummary,
  BookDetailResponse, ChapterResponse, PageResponse, PageSummary,
  ImageRegionResponse, и admin shamela schemas из 15.6:
  SyncMasterResponse, ImportBookResponse, MapBookResponse)

### Решения

- **Single-page подход вместо monorepo с apps/\*** - первая попытка
  реструктуризации в `apps/argument-map/` через `git mv` была
  откачена. Причины:
  - Один разработчик, один стек (React+Vite+Tailwind)
  - Один домен с навигацией между разделами
  - Желание единого header / sidebar / top-nav
  - YAGNI: monorepo с apps/* добавляется только когда возникнет
    конкретная потребность (другая команда / разные домены / разный
    стек / огромный бандл)

  Что делаем вместо: один `frontend/` с React Router, разные разделы
  как разные `pages/`, общий Header, общие компоненты в
  `components/ui/` без всяких packages. Когда вырастет - вернёмся

- **WSL2/NTFS глюк при `git mv frontend apps/argument-map`** - после
  команды git показывал rename в индексе, но физически целевой каталог
  имел битый inode (показывался с `d?????`). `git reset --hard HEAD`
  частично восстановил. Дальнейшие действия Абдула предложил делать
  через файл-менеджер Windows (Total Commander), но мы решили
  отменить реструктуризацию целиком. Зафиксировано как gotcha:
  избегать `git mv` директорий через WSL2 на DrvFs/NTFS - использовать
  Total Commander Move + `git add -A`

- **Эвристика арабского текста** через `/[؀-ۿ]/.test(text)` -
  Unicode-диапазон арабских символов 0x0600-0x06FF. Эвристика нужна
  потому что:
  - book.language может быть не выставлен или быть mixed (русский
    title для арабской книги)
  - chapter.title не имеет своего language поля - наследует от книги
    или определяется per-row
  - На уровне страницы content может быть mixed (комментарии на одном
    языке, цитата на другом)

  Дешёвая regex-проверка достаточна для MVP. Для multi-language
  layout (когда понадобится показывать переведённое+оригинал
  side-by-side) - вернёмся к более точному определению

- **`dangerouslySetInnerHTML` без sanitize** на shamela contents -
  shamela как доверенный источник через mitmproxy-реверс (один
  ETL-канал контролируется бэком). Для пользовательских upload PDF/
  EPUB (Этап 16) нужен DOMPurify. Записал TODO в коде

- **Side-panel chapters только информативный** на MVP - связь
  `page → chapter` (`lib_pages.chapter_id`) не заполняется маппером
  (см. progress.md Сессия 22 Этап 15.5). Side-panel показывает
  структуру книги, клик на главу не делает navigation. Вернёмся
  когда будет смысл связывать (после фронт-проверки на 3-5 книгах
  и решения bulk vs lazy)

- **`setPageContent({kind: 'loading'})` в event handlers, не в
  effect** - правило `react-hooks/set-state-in-effect` (gotcha).
  При монтировании компонента loading state приходит из initial
  `useState({kind: 'loading'})`, при переключении страниц через
  goPrev/goNext - выставляется явно перед `setPageNumber`. Effect
  реагирует на изменение pageNumber и делает fetch без вызова setState
  до получения ответа

- **No React.lazy для library pages** - в отличие от TopicGraphPage
  с тяжёлыми RF/dagre/lucide зависимостями, BookList и BookReader
  лёгкие (~15kB к initial bundle суммарно). Подгружать lazy нет
  смысла. Если bundle вырастет (например, добавится rich text
  editor для редактирования контента) - вернёмся

### Проблемы

- **WSL2/NTFS git mv глюк** - см. «Решения». Откатили реструктуризацию
- **types.ts dropped after `git reset --hard HEAD`** - regenerated-api
  делалось поверх HEAD, при reset это изменение откатилось. Пришлось
  регенерировать ещё раз. На будущее: после regenerate типов сразу
  включать в следующий коммит (не оставлять uncommitted)
- **2 lint ошибки** на первом прогоне:
  - `react-hooks/set-state-in-effect` на `setPageContent('loading')`
    в effect → перенёс в event handlers
  - `react/no-danger` правило не существует в нашем eslint-config →
    убрал disable-комментарий, оставил TODO в обычном комментарии

### Следующий шаг

**Этап 18.f-g: CitationPicker + интеграция с argument-map**.

Перед началом - **Абдула должен вручную импортировать 3-5 книг** через
admin endpoints (бэк жив, фронт `/books` сейчас покажет пустой state):

```bash
# 1. синхронизация каталога shamela (один раз, ~30-60с, ~5MB архив)
curl -X POST http://localhost:9090/api/v1/admin/shamela/sync-master

# 2. для каждой книги (id выбирается через psql или /v3/api-docs):
USER_ID=14561248-0bfd-4a62-8395-d40a6972182a   # dev-user UUID

# например, Сахих аль-Бухари (точный shamela id выбрать через
# SELECT id, name FROM lib_shamela_book WHERE name ILIKE '%البخاري%' LIMIT 5)
BOOK_ID=<id-из-shamela>
curl -X POST http://localhost:9090/api/v1/admin/shamela/import-book/$BOOK_ID
curl -X POST http://localhost:9090/api/v1/admin/shamela/map-book/$BOOK_ID \
  -H "X-User-Id: $USER_ID"

# 3. проверить через фронт http://localhost:5173/books
```

После 3-5 книг в БД - открыть `/books` в браузере, проверить:
- BookCard рендерится: badge типа, RTL+naskh для арабского title,
  клик ведёт на `/books/{id}`
- BookReader показывает: side-panel chapters tree, pagination toolbar,
  page content в RTL+naskh для арабского
- pagination prev/next работают, content обновляется
- chapters tree корректно показывает иерархию (для книг с nested
  глав - см. Тафсир Ибн Касира как кандидат)

После UX-проверки - **архитектурное решение bulk vs lazy import**.
Возможные варианты:
- bulk: всё ~8500 книг через `mapBook` за один прогон, БД ~1-1.5GB,
  search/list мгновенный
- lazy: `mapBook` дёргается при первом открытии конкретной книги
  (5-15с spinner). БД растёт по мере использования
- гибрид: метаданные `lib_books` сразу для всех (~30MB), content
  `lib_pages` lazy. Best UX/storage trade-off

**Этап 18.f: CitationPicker** - переиспользуемый компонент в
`frontend/src/components/citation/CitationPicker.tsx`. Выделение
фрагмента текста в BookReader → modal с выбором приложения
(argument-map / Q&A) и контекста (какой узел / ответ). Это
центральный элемент платформенного pivot'а ADR-018

**Этап 18.g: Argument-map переключение на CitationPicker** - кнопка
«Привязать цитату» в `NodeDetailsPanel` открывает CitationPicker
вместо текущей `AddSourceModal` со свободной формой.
`AddSourceModal` либо удаляется, либо становится fallback для
свободных цитат (например URL без book context)

---

## 2026-05-09 — Сессия 22 (backend) — Этапы 15.4 + 15.5 + 15.6 Library shamela MVP закрыт целиком на бэке

Сверх изначальных планов закрыли все три оставшихся подэтапа в одной
сессии (ImportService + Mapper + REST). Library shamela MVP на бэке
полностью готов - доступен через 3 admin endpoints под
`/api/v1/admin/shamela/*`. Дальше нужно фронт (Этап 18) для
UX-валидации и решение bulk vs lazy.

### Сделано (краткая сводка)

5 коммитов в этой сессии:

`34311fe` `feat(backend): этап 15.4 - ShamelaImportService syncMaster + importBook` (+274 IT)
`7155f7e` `docs: handoff Сессии 22 - этап 15.4 ShamelaImportService закрыт`
`0c11740` `feat(backend): этап 15.5 - ShamelaToLibraryMapper из staging в lib_books` (+10 IT = 284)
`2ecf091` `docs: handoff Сессии 22 продолжение - этап 15.5 закрыт`
`dc50271` `docs: исправить выдуманную цифру 270k книг shamela на реальную ~8500`
`1ce9fad` `feat(backend): этап 15.6 - shamela admin REST endpoints + exception mapping` (+12 IT = 296)

Этапы 15.4, 15.5, 15.6 полностью закрыты. ETL-стэк готов end-to-end
от API до REST.

### Этап 15.4 - ShamelaImportService (детально)

Длинная фокусная сессия после большой экспедиции 21. Закрыты оба
оставшихся слоя ETL под shamela: оркестрация (15.4) + доменное мапирование
(15.5). Без новых архитектурных решений - ADR-020 уже фиксирует
двухслойную архитектуру и поток. После сессии 22 для закрытия Library
shamela MVP остаётся только REST-слой (15.6).

#### 15.4 - ShamelaImportService (6 файлов / 696 insertions):

- **`library/shamela/service/ShamelaImportService`** - оркестрационный
  `@Service` (~180 строк). Два публичных метода:
  - `MasterSyncResult syncMaster()` - читает
    `sync_state.master_version`, вызывает `fetchMasterMetadata`. Если
    version не изменилась - возвращает `unchanged(version)` без
    download'а. Иначе скачивает master-zip в `Files.createTempDirectory`
    под `shamela.download-dir`, распаковывает, проверяет наличие
    `category.sqlite`/`author.sqlite`/`book.sqlite`, читает
    `MasterReader`, bulk-upsert в Category/Author/Book DAO,
    обновляет `sync_state` в самом конце (последовательность
    важна для retry-семантики). Cleanup workdir рекурсивно
    в `finally` через `Files.walk + reverseOrder + deleteIfExists`
  - `BookImportResult importBook(long bookId)` - находит book в
    `lib_shamela_book` (если нет → `ShamelaImportException` с
    подсказкой про `syncMaster()`), строит детерминированный URL
    `https://{filesHost}/books-store/{id}-{major}.zip` (без api_key
    для ready-host, см. ADR-020), скачивает, распаковывает, проверяет
    наличие `{bookId}.sqlite`, читает `BookReader`, bulk-upsert в
    Page/Title DAO. Cleanup workdir в `finally`
- **`MasterSyncResult` / `BookImportResult`** - records с
  named-factory (`unchanged(v)` / `synced(...)`) для читаемых
  call-site без boolean-первого-параметра
- **`ShamelaImportException`** - для ошибок уровня сервиса (нет book
  в staging, сбой создания workdir, отсутствие SQLite после
  распаковки). `ApiException`/`ArchiveException`/`ReaderException`
  из downstream НЕ оборачиваем - все они `RuntimeException`, REST-слой
  15.6 единым `@ControllerAdvice` замапит каждый тип в свой HTTP-код
- **`ShamelaImportServiceIT`** - 6 IT через `@SpringBootTest +
  TestcontainersConfiguration + @MockitoBean ShamelaApiClient`.
  Сценарии: skip unchanged (verify never on downloadArchive),
  full master pipeline (assert строки в DAO + sync_state version),
  blank patch_url throws (без download'а), cleanup при extraction
  failure (corrupt zip → ArchiveException → finally удаляет workdir +
  version не обновился), missing book throws, full book pipeline
  (assert pages+titles + детерминированный URL через `verify(eq(...))`).
  Fixture-zip собираются программно: SQLite через
  `DriverManager(jdbc:sqlite:tmp/x.sqlite)` + `Statement.execute("CREATE
  TABLE ...")` + `INSERT`, потом упаковка в `ZipOutputStream`. Никаких
  binary-фикстур в `test/resources/`. `@DynamicPropertySource`
  override `shamela.download-dir` в изолированный `Files.createTempDirectory`
  для класса - так разные тесты не реагируют на cleanup-ассерты
  друг друга
- **`ShamelaImportServiceLiveIT`** - 1 тест `@Tag("live")` против
  реальной `dev.shamela.ws` API + Testcontainers postgres.
  Sanity-check: `syncMaster()` от version=0 должен вернуть
  changed=true, version>1000, books>10000, authors>1000,
  categories>10. Исключён из обычного verify через
  `<excludedGroups>live</excludedGroups>` в failsafe-plugin.
  Запуск точечный: `./mvnw failsafe:integration-test -Dgroups=live
  -Dit.test=ShamelaImportServiceLiveIT`

#### Этап 15.5 - ShamelaToLibraryMapper (7 файлов / 795 insertions):

После 15.4 продолжил в той же сессии - контекст позволял, оба слоя
ETL связаны между собой (Mapper читает из staging который наполнил
ImportService).

- **`library/shamela/service/ShamelaToLibraryMapper`** -
  оркестрационный `@Service` со `mapBook(long shamelaBookId, UUID createdBy)`:
  - **Резолв Authority** по `shamela_book.author_id` →
    `shamela_author.name`. Нормализация `trim + replaceAll("\\s+", " ")`,
    exact-match через новый `AuthorityRepository.findByName(String)`.
    Fallback - anonymous Authority `shamela:anonymous` с if-not-exists
    (создаётся один раз, переиспользуется для всех null/dangling/empty)
  - **Re-import detection** через `BookRepository.findByShamelaBookId(long)`
    который ищет `WHERE metadata->>'shamela_book_id' = ?`. GIN-индекс на
    `metadata` уже из миграции 16. Если книга уже замаплена - возвращаем
    `MappedBookResult.alreadyMapped(...)` без создания дубликатов
  - **Создание Book**: `BookType.BOOK` всегда (semantics
    `shamela_book.type` 1-3+ неясна без real-data sample), `language="ar"`.
    `metadata` jsonb с `{shamela_book_id, shamela_major_release, pdf_links}`,
    pdf_links вставляется как-есть из shamela через `objectMapper.readTree`
  - **Mapping chapters topologically** через BFS: root titles → их
    дети → grand-дети. На момент создания child его parent уже сохранён,
    UUID известен через `shamelaIdToChapterUuid` map. Защита от orphan
    parent_id (указатель на несуществующий title) - такой title becomes
    root, не падаем. `order_index` = индекс в монотонном порядке id
    (shamela вставляет id в порядке появления заголовка)
  - **Mapping pages** с `page_number = shamela_page.id` (shamela 1-based
    monotonic). `chapter_id = NULL` на MVP - связь page→chapter через
    `title.page` отложена. Skip blank/whitespace-only content (CHECK
    `lib_pages_content_present` требует наличия text/image)
  - **`@Transactional` на mapBook** - атомарность одной книги. Размер
    транзакции ~100KB-2MB, лок секунды
- **`MappedBookResult`** - record с named factory:
  `freshlyCreated(...)` / `alreadyMapped(...)`. Поля: `bookId`,
  `shamelaBookId`, `created`, `authorityId`, `chaptersCount`, `pagesCount`
- **Расширение existing repositories** (4 файла, добавлено по одному
  методу):
  - `AuthorityRepository.findByName(String)` - exact match `WHERE name = ?`
    `ORDER BY created_at LIMIT 1` (схема не имеет UNIQUE на name).
    Существующий `searchByName(ILIKE %%)` не подходит для дедупликации
  - `BookRepository.findByShamelaBookId(long)` - JSONB операторы
  - `ShamelaTitleDao.findAllByBookId(long)` - все titles книги
    `ORDER BY id`
  - `ShamelaPageDao.findAllByBookId(long)` - все pages книги
    `ORDER BY id`
- **`ShamelaToLibraryMapperIT`** - 10 IT через `@SpringBootTest +
  TestcontainersConfiguration`. Никаких моков - чистый pipeline через
  реальные DAO/Repository/Postgres. Сценарии:
  1. happy path: book + chapters tree + pages + Authority resolved
  2. metadata jsonb: shamela_book_id/major_release/pdf_links
  3. re-import idempotent skip с одной Book/Authority/Page записью
  4. anonymous authority при author_id = null
  5. reuse Authority с тем же нормализованным именем (trim+collapse)
  6. reuse Authority уже добавленной пользователем извне shamela
  7. chapter tree: root → child → grand с правильными parent_chapter_id
  8. orphan parent_id: title с битым parent становится root
  9. blank/whitespace-only content pages skip
  10. validation: missing shamela book throws ImportException
- **Удалён сценарий "dangling FK"** из исходного плана: на уровне БД
  `lib_shamela_book.author_id` имеет FK на `lib_shamela_author` с
  `ON DELETE SET NULL`, что гарантирует невозможность dangling через
  нормальный DAO insert. Защитная ветка в Mapper оставлена как
  safety-net на случай программного нарушения инварианта (manual SQL/
  debug), но через тест не воспроизводится без отключения FK

### Решения

- **Идемпотентность через `ON CONFLICT DO UPDATE` вместо транзакции**
  на pipeline syncMaster (Этап 15.4). Bulk upsert ~8500 книг
  плюс ~25k авторов в одной транзакции долго держит лок и съедает WAL. ADR-020 закрепил эту схему: прерванный sync
  (network error в середине) безопасно повторяется - повторный
  master-snapshot затирает все строки и обновляет
  `sync_state.master_version` в самом конце. Транзакция только в
  пределах одного DAO upsert (где `JdbcTemplate.batchUpdate` сам
  даёт connection-уровень)
- **Cleanup в `finally`, не в `try`-конце**. Финал даже при exception
  гарантирует удаление workdir. Ошибки cleanup'а (например busy
  file lock на Windows) логируются `WARN`, но не маскируют исходный
  exception - `Files.deleteIfExists` каждой entry в отдельном try
- **Не оборачиваем downstream RuntimeException**. Изначальный план
  говорил «`ShamelaImportException` оборачивает all downstream».
  По факту - ApiException/ArchiveException/ReaderException и так
  все `RuntimeException`, единый `@ControllerAdvice` обработает
  каждый по типу. Оборачивание добавило бы лишний слой без выгоды
  (теряется тип, тесты сложнее: assertion на cause вместо прямого
  типа). `ShamelaImportException` - только для собственных ошибок
  ImportService
- **`@DynamicPropertySource` против `@TestConfiguration` с переопределённым
  `ShamelaApiProperties` bean'ом** - первый чище, потому что
  `@ConfigurationProperties`-биндинг происходит до того как
  Spring может перебить мой bean (порядок инициализации). DynamicPropertyRegistry
  влезает в фазу resolve property values, ещё до создания самого record'а
- **Re-import = idempotent skip** для 15.5 Mapper (а не delete+create).
  Удаление `Book` каскадирует на `lib_chapters`/`lib_pages` через
  `ON DELETE CASCADE`, но не каскадирует на `node_sources` (там FK
  идёт на `Source.id`, не Book). Однако future-fitch предполагает
  что Source может ссылаться на Book через jsonb-meta или прямую
  колонку - delete сломает ссылку. Idempotent skip защищает invariant
  «никогда не теряем ссылок при retry». Если нужен честный re-import
  с обновлённым контентом - надо реализовать smart-merge отдельно
- **`book_type = BOOK` always для shamela** - shamela `type` integer
  имеет неясную semantics (нет docs от mitmproxy-реверса), при сэмпле
  реальных данных можно расширить mapping. Дешевле сделать сейчас как
  `BOOK` и подправить когда увидим распределение значений
- **`chapter_id = NULL` для page на MVP** - привязка page → chapter
  через `title.page` (TEXT с возможным range "1-3") требует парсинга
  и логики «ближайший предыдущий title». Откладывается на iteration
  после reader-фронта в Этапе 18 - тогда станет видно нужно ли это
  для UX, или дерево chapters в side-panel + плоский список pages
  достаточны
- **BFS, а не recursion для chapter-tree** - shamela 8500 книг имеют
  до ~10k titles в больших коллекциях. Recursion рискует stack
  overflow при глубоком вложении. BFS гарантирует константный stack
  и обрабатывает orphan-parent защитой на старте

### Проблемы

- **Первый прогон verify упал на 2 errors**: тест `mapBook_uses_anonymous_authority_when_author_id_dangling`
  пытался вставить через DAO `shamela_book` с `author_id=999` где автор
  не существует - FK violation на уровне БД. Решение: удалил тест,
  оставил только anonymous-fallback на null author_id (и в коде
  Mapper защитная ветка для dangling - dead branch). Также тест
  `mapBook_skips_blank_or_null_content_pages` падал на NOT NULL
  `lib_shamela_page.content` при `seedPage(.., null)` - убрал null
  case, оставил blank/whitespace
- `OpenApiIT.readOnlyEndpoint_doesNotGetUserIdHeader` flake (gotcha
  из Сессии 21) **не воспроизвёлся** ни в одном прогоне Сессии 22 -
  все 5 OpenApiIT зелёные

#### 15.6 - ShamelaAdminController + exception mapping (10 файлов / 491 insertion):

После 15.5 продолжил в той же сессии. Финальная фаза Library shamela
MVP на бэкенде - 3 admin endpoints под `/api/v1/admin/shamela/*`.

- **`web/controller/ShamelaAdminController`** - `@RestController` с 3
  endpoints:
  - `POST /sync-master` - вызов `ShamelaImportService.syncMaster()`
  - `POST /import-book/{bookId}` - вызов `importBook(long)` с
    валидацией `bookId >= 1` (через `IllegalArgumentException` →
    `400` в существующем GlobalExceptionHandler)
  - `POST /map-book/{bookId}` - вызов
    `ShamelaToLibraryMapper.mapBook(long, UUID)` с `@CurrentUser UUID`
    для `created_by` в `lib_books`
- **3 response-DTO** (`SyncMasterResponse`, `ImportBookResponse`,
  `MapBookResponse`) - 1-to-1 ре-shape от service-records, отдельные
  типы для forward-compat
- **`web/mapper/ShamelaAdminMappers`** - record→DTO утилита
- **`service/ShamelaNotFoundException`** - подкласс
  `ShamelaImportException` для not-found сценариев. Чистый exception
  mapping без substring matching по сообщению. Существующие тесты
  с `instanceof(ShamelaImportException.class)` остаются зелёными
  (Java-наследование - подкласс тоже instanceof родителя)
- **Refactor `ShamelaImportService.importBook` и
  `ShamelaToLibraryMapper.mapBook`** - кидают `ShamelaNotFoundException`
  вместо общего `ImportException` для not-found
- **Расширен `GlobalExceptionHandler`** новыми handlers:
  - `ShamelaApiException` → `502 Bad Gateway` (shamela API недоступна)
  - `ShamelaArchiveException` → `500` (битый zip / Zip Slip)
  - `ShamelaReaderException` → `500` (битый SQLite)
  - `ShamelaImportException` → `500` (общие)
  - `ShamelaNotFoundException` → `404` (порядок важен - конкретный
    handler выбирается Spring'ом раньше общего)
- **`ShamelaAdminControllerIT`** - 12 IT через MockMvc + `@MockitoBean`
  на `ShamelaImportService` и `ShamelaToLibraryMapper`. Сценарии:
  - sync-master: 200 success / 200 unchanged / 502 ApiException
  - import-book: 200 / 404 NotFound / 400 negative id /
    400 zero id (verifyNoInteractions)
  - map-book: 200 fresh / 200 already-mapped / 400 missing X-User-Id /
    404 NotFound / 400 negative id

**Документация:**

- `api-contract.md` секция «Shamela Admin API» с request/response
  примерами для всех 3 endpoints, error codes, «Что не реализовано»
  (PDF / async / bulk). Запись в «Историю изменений контракта»
- `glossary.md` секция «Shamela ETL» с 5 терминами: staging-таблица,
  master-version, major/minor release, idempotent skip, anonymous
  Authority

**Что НЕ реализовано в 15.6 (отложено сознательно):**
- `GET /book/{id}/pdf/{fileIndex}` - lazy download PDF исходного
  издания. Требует streaming через `StreamingResponseBody` + cleanup
  tempfile. Согласовано с ADR-020 «PDF lazy» - не критично для MVP
- Async варианты POST endpoints - на MVP синхронные. Долгие операции
  (`sync-master` ~30-60с) могут таймаутить через прокси/CDN. Future
  через `@Async` или message queue
- Bulk endpoints (`POST /map-books?ids=...`) - точечные на MVP.
  Массовый bootstrap ~8500 книг отложен до фронт-проверки в Этапе 18

### Следующий шаг

**Этап 18: Library frontend** - критический шаг для UX-валидации
shamela импорта на 3-5 руками-импортированных книгах. После
визуализации станет ясно как пользователь использует library и
осознанно решим: **bulk-bootstrap всего каталога / lazy-on-demand
при первом просмотре / гибрид**. См.
`memory/feedback_no_bulk_shamela_parse.md` про отложенный bulk parse.

Конкретный план 18 (из roadmap.md):

- **18.a: monorepo реструктуризация** - корневой `package.json` с
  pnpm workspaces. `frontend/` физически переезжает в
  `apps/argument-map/` через `git mv`. Создаются `apps/library/`,
  `packages/shared-ui/`, `packages/shared-api/`,
  `packages/shared-citation/`. Это структурный рефакторинг -
  **не делать в одной сессии с фичами**, отдельный handoff в чистый
  коммит
- **18.b: BookListPage** - страница `/books` со списком всех книг,
  фильтры по типу/автору, поиск по `q` параметру. Реюз
  существующего `apiGetRaw` + `BookSummary[]` из автогенерированного
  `types.ts`. Карточки с title/authority/bookType-badge/page-count
- **18.c: BookReader** - страница `/books/{id}`:
  - Боковая панель chapters (tree из `BookDetailResponse.chapters`)
  - Основная область - текст страницы (правильный RTL для арабского,
    naskh-шрифт, см. memory `project_sources_arabic_direction.md`)
  - Pagination между страницами через `from`/`to` параметры
- **18.d: ImagePageRenderer** - отдельный mode для image-сканов
  (далеко в будущем, после Этапа 17 OCR)
- **18.e: CitationPicker** в `packages/shared-citation` - переиспользуемый
  компонент для выделения фрагмента текста и привязки его к узлу
  argument-map / ответу Q&A. Это центральный компонент платформенного
  pivot'а (ADR-018)
- **18.f: Argument-map переключение на CitationPicker** - кнопка
  «Привязать цитату» в `NodeDetailsPanel` открывает CitationPicker
  вместо текущей `AddSourceModal` со свободной формой

**Перед 18 - руками импортировать 3-5 книг через 15.6 endpoints:**

```bash
# 1. sync staging метаданных (один раз, ~30-60с)
curl -X POST http://localhost:9090/api/v1/admin/shamela/sync-master

# 2. для каждой выбранной книги - import + map
BOOK_ID=41557
USER_ID=14561248-0bfd-4a62-8395-d40a6972182a  # dev user UUID
curl -X POST http://localhost:9090/api/v1/admin/shamela/import-book/$BOOK_ID
curl -X POST http://localhost:9090/api/v1/admin/shamela/map-book/$BOOK_ID \
  -H "X-User-Id: $USER_ID"

# 3. проверить через библиотечное API
curl http://localhost:9090/api/v1/library/books?q=البخاري
```

Отбор книг - на усмотрение Абдулы (нужны репрезентативные: одна
крупная как Сахих аль-Бухари, одна с глубокой иерархией глав, одна
короткая статья). После UX-проверки на этих 3-5 книгах принимается
решение про bulk vs lazy.

ETL-стэк после 15.6 (полностью готов end-to-end):
- API: `ShamelaApiClient` + `Properties` + `HttpClientConfig`
- Extract: `ShamelaArchiveExtractor`
- Read: `SqliteValueParser` + `MasterReader` + `BookReader`
- Persist (staging): 6 DAO с bulk upsert
- Orchestrate: `ShamelaImportService.syncMaster + importBook`
- Map: `ShamelaToLibraryMapper.mapBook`
- **REST: `ShamelaAdminController` (3 endpoints)** ← закрыт в 15.6
- (отложено) PDF download + async + bulk endpoints

### Старый «Следующий шаг» (для истории)

(было запланировано идти в 15.6 после handoff'а - в итоге закрылось
в этой же сессии)

**Этап 15.6: REST endpoints + финальная документация** - финальная
фаза Library shamela MVP.

⚠️ Архитектурный вопрос про массовый парсинг отложен. Абдула
попросил не запускать full-bootstrap ~8500 книг до фронт-проверки
на 1-2 книгах. Открытое решение: bulk vs lazy-on-demand.

Конкретные endpoints для 15.6:

- `POST /api/v1/admin/shamela/sync-master` - вызов
  `ShamelaImportService.syncMaster()`. Возвращает `MasterSyncResult`
  как DTO. Долгая операция (до минуты при first sync) - на MVP
  синхронный вызов, в будущем выделить в async через @Async или
  message queue
- `POST /api/v1/admin/shamela/import-book/{id}` - вызов
  `importBook(long)`. Возвращает `BookImportResult` DTO
- `POST /api/v1/admin/shamela/map-book/{id}` - вызов
  `mapBook(long shamelaBookId, UUID createdBy)`. Возвращает
  `MappedBookResult` с `bookId` UUID который можно использовать в
  GET `/api/v1/library/books/{id}` для просмотра. `createdBy`
  берётся из `@CurrentUser` (X-User-Id header, ADR-006)
- `GET /api/v1/admin/shamela/book/{id}/pdf/{fileIndex}` - lazy
  download PDF исходного издания. Использует
  `ShamelaApiClient.downloadPdf(relativePath, targetDir)`,
  возвращает streaming response. Чтение `book.metadata.pdf_links.files[index]`

Конкретные файлы:

- `library/shamela/web/controller/ShamelaAdminController.java` - `@RestController`
  с base path `/api/v1/admin/shamela`. Endpoints выше. `@CurrentUser UUID`
  для авторизации (на MVP - just consume header, без реальной
  admin-проверки; в Этапе 20 spring-security добавит role check)
- `library/shamela/web/dto/` - DTO для ответов:
  `MasterSyncResponse`, `BookImportResponse`, `MappedBookResponse`.
  Отличаются от service-records наличием Spring HATEOAS-ссылок
  или forward-compat-полей при необходимости. На MVP - простой
  re-shape
- `library/shamela/web/mapper/ShamelaWebMappers.java` - record →
  DTO трансформация
- `library/shamela/web/exception/` - `@ControllerAdvice` который
  маппит:
  - `ShamelaApiException` → 502 Bad Gateway
  - `ShamelaArchiveException` → 500 Internal Server Error
  - `ShamelaReaderException` → 500
  - `ShamelaImportException` → 404 если message содержит
    «не найдена», иначе 500. Можно ввести два подкласса
    (`ShamelaNotFoundException` extends `ShamelaImportException`)
    для чистого matching - решить по факту в 15.6
- `ShamelaAdminControllerIT` - MockMvc + Testcontainers,
  моки на `ShamelaImportService`/`ShamelaToLibraryMapper` через
  `@MockitoBean`. Сценарии: success-pathways для всех endpoints,
  validation (book id < 0), exception mapping
- **api-contract.md** - дописать секцию `## Shamela Admin API` с
  всеми 4 endpoints, request/response примерами, error codes
- **glossary.md** - добавить термины: «staging таблица», «shamela
  major_release», «idempotent skip»

После 15.6 - Library shamela MVP закрыт целиком. Можно дёрнуть
admin endpoints curl'ом и заполнить БД 3-5 книг для UX-проверки
на фронте (массовый bootstrap всех ~8500 книг отложен до решения
bulk vs lazy).

ETL-стэк после 15.5 (полностью готов до уровня сервисов):
- API: `ShamelaApiClient` + `ShamelaApiProperties` + `ShamelaHttpClientConfig`
- Extract: `ShamelaArchiveExtractor`
- Read: `SqliteValueParser` + `ShamelaMasterReader` + `ShamelaBookReader`
- Persist (staging): 6 DAO с bulk upsert
- Orchestrate (15.4): `ShamelaImportService.syncMaster + importBook`
- **Map (15.5): `ShamelaToLibraryMapper.mapBook`** ← закрыт в этой сессии
- REST (15.6): `ShamelaAdminController` ← следующий шаг

---

