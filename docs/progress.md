# Журнал работы

Последние сессии. Новые записи - **сверху**

Формат записи описан в `docs/doc-hygiene.md` Принцип 5

**Архив:**
- Сессии 0-21: [`docs/archive/progress-sessions-1-21.md`](archive/progress-sessions-1-21.md)
- Сессии 22-29: [`docs/archive/progress-sessions-22-29.md`](archive/progress-sessions-22-29.md)

---

## 2026-05-17 - Сессия 39 lazy PDF streaming 25.d.5

Закрыл последний открытый пункт Этапа 25.b/d - lazy Range streaming
для shamela PDF из archive.org через backend. До этого первое
открытие 135MB книги блокировало юзера на ~30 сек пока бэкенд
скачивал весь PDF целиком для кеша. Теперь Range request форвардится
напрямую к archive.org и стримится бэкендом без буферизации в памяти.
Backend 592 IT (+17 от 575), `mvnw verify` BUILD SUCCESS

### Backend (3 commits)

- `62d14e1` feat - `PdfSourceProvider.openStream(book, fileIndex,
  RangeSpec)` как primary read path. Domain `RangeSpec(startInclusive,
  endInclusive?)` (end nullable для open-ended `bytes=N-`) +
  `PdfStreamingResult(stream, contentLength, start, end, totalSize,
  isPartial)` AutoCloseable. `UserUploadProvider.openStream` - MinIO
  native Range через `GetObjectRequest.range()`.
  `PdfLinksSourceProvider.openStream` - cache hit MinIO Range; cache
  miss + null range синхронный fill через `locateFile()`; cache miss +
  range lazy forward к archive.org через `PdfFetcher.openStream`
  (HTTP Range header добавляется). `HttpClientPdfFetcher.openStream`
  защищён тем же `@CircuitBreaker(pdfDownload)` что и `fetch()`.
  `PdfService.openStream` - роутер через provider.
  `RangeNotSatisfiableException` → 416 Problem Details в
  `GlobalExceptionHandler` с `start`/`totalSize` properties
- `854cc69` feat - `PdfController.streamPdf` мигрирован на
  `PdfService.openStream`. Status / headers / content строятся из
  `PdfStreamingResult` полей. Default chunk cap 1MB сохранён.
  `PdfControllerIT` адаптирован под новый API + новый тест
  `streamPdf_rangeOutsideFile_returns416`
- `f47b4e2` feat IT - `HttpClientPdfFetcherRangeStreamingIT` (новый,
  6 тестов) через локальный `com.sun.net.httpserver.HttpServer` на
  динамическом порту: 200 full, 206 partial, 200 при игнорировании
  Range (mirror без Range support), 5xx → exception, open-ended
  `bytes=N-`, 416 от upstream. JDK HttpServer выбран вместо WireMock
  - нет нового runtime dep, sub-10мс startup. `UserUploadProviderIT`
  (+5) и `PdfLinksSourceProviderIT` (+5) - cache hit/miss с разными
  range scenarios + 416 + invalid fileIndex

### Решения

- **MinIO tee при cache miss + range?** Отложено - требует
  `PipedInputStream` или background executor + careful sync. Сейчас
  каждый Range request на не-кешированной книге = отдельный upstream
  HTTP. Trade-off acceptance: latency распределена ровнее, нет
  30-сек блока в начале. Тригерь tee когда появится production
  traffic где много юзеров на одну книгу
- **WireMock vs JDK HttpServer для тестов?** JDK HttpServer - нет
  нового runtime dep, lightweight, достаточно для контракт-уровня.
  WireMock дал бы advanced features (recording / fault injection)
  которые на этом уровне не нужны
- **Default method в `PdfSourceProvider.openStream`?** Нет -
  явный signature каждому provider'у заставляет подумать про lazy
  семантику конкретно для своего источника. Default через `locateFile`
  + `MinIO.getRange` дал бы regression к старому поведению для
  PdfLinks (полный download)
- **Удалить `locateFile` после миграции на `openStream`?** Нет -
  используется в IT (cache verification, multi-volume), при cache
  miss + null range (admin smoke / full download path). Не deprecated

### Docs

- ADR-023 **Amendment 2026-05-17** в `decisions.md` про lazy
  streaming - rejected alternatives (tee, double request, no-cache)
- `roadmap.md` 25.d.5 → `[x]` с описанием
- `api-contract.md` PDF API раздел расширен: Range header semantics,
  Content-Range, lazy streaming описание, 416 ошибка, 503 circuit
  breaker

### Verify

- Backend: `./mvnw verify` 592/592 BUILD SUCCESS
- Smoke curl - см. отчёт

### Следующий шаг

Этап 25 PDF Viewer почти закрыт - остаются `25.d.2` (text↔pdf page
sync, Tier 1 admin flow), `25.d.4` (inline PDF preview redesign),
`25.e/f` (после Этапа 17). Можно переключаться на любой пункт из
SESSION_START_PROMPT по выбору Абдулы

---

## 2026-05-17 - Сессия 39 финал, Этап 6 JSON export/import

Закрыл единственный нетронутый пункт Этапа 6 - JSON-сериализация темы
целиком для backup и обмена между инстансами. Backend 575 IT (+21
от 554), frontend 170 vitest без регрессий, lint clean, build ok

### Backend (3 commits)

- `733842c` feat - `TopicExportDto` + 7 nested records
  (TopicData/NodeData/EdgeData/NodeSourceData/SourceData/AuthorityData/
  BookRef) + `TopicImportResponse{topicId, importedNodes, ...,
  warnings[]}`. `TopicExportService.exportTopic` собирает unique
  sources через LinkedHashSet (стабильный порядок по first-seen).
  `TopicImportService.importTopic` с UUID remapping через
  `Map<oldUUID, newUUID>` для каждой entity, FK references
  (edges.fromNodeId, node_sources.nodeId/sourceId) пере-mapping
  по словарю. createdBy перезаписан на импортирующего user'а
  (security). Authorities find-or-create по name (без era - dup
  избегаем), books find-or-skip с warning. Positional refs
  null'ифицируются если source без bookId.
  `UnsupportedExportFormatException` → 422 unsupported-format-version
  с receivedVersion/supportedVersions properties
- `dd97246` feat - `TopicExportImportController` с двумя endpoints:
  `GET /api/v1/topics/{id}/export` (Content-Disposition: attachment;
  filename="topic-{shortId}.json"), `POST /api/v1/topics/import`
  routed по consumes (application/json для programmatic flow,
  multipart/form-data для UI file upload)
- `ee99efe` feat IT - 19 тестов через Testcontainers:
  - `TopicExportServiceIT` (5): empty topic, full tree с дедупликацией
    sources, revisions exclusion, source without authority/book, 404
  - `TopicImportServiceIT` (8): invalid format version, null topic,
    empty payload, fresh instance remapping, missing book → warning,
    existing authority by name reused, existing book preserved, round-trip
  - `TopicExportImportControllerIT` (6): export 200 + filename header,
    export 404, importJson 201, importMultipart 201, invalid version
    422, missing X-User-Id 400

### Frontend (1 commit)

- `bb0417d` feat - в TopicListPage header кнопка «Импортировать тему»
  (ghost Upload icon) триггерит hidden `<input type="file">`
  программно. handleFileSelected → apiPostMultipart → toast.success
  с action «Открыть» → navigate на новую тему. Warnings показываются
  отдельным toast.warning. 422 unsupported-format-version → специальный
  toast.error.
  На каждой TopicCard в углу `<Download>` icon button (opacity-0,
  fade-in на group-hover) - apiGetRaw `/export` → Blob +
  URL.createObjectURL + programmatic `<a download>` click +
  setTimeout(0) revoke. stopPropagation чтобы не сработал обёрточный
  `<Link>`. 8 новых i18n keys ru/ar (topic.export.*, topic.import.*).
  Types регенерированы (TopicImportResponse + TopicExportDto + TopicData
  доступны в components.schemas)

### Решения

- **Включать revisions?** Нет - история не нужна для обмена/backup,
  10x размер при минимальной ценности
- **Включать Books полностью?** Нет - shared library resource (ADR-019),
  hint (id+title+authorityId) достаточен для пользователя
- **Reuse imported UUIDs?** Нет - PK violations при self-import.
  UUID remapping + защита от ownership override
- **Authority match by name VS (name+era)?** name - era это
  disambiguation, не invariant. Дубликаты избегаются, occasional
  false-match приемлем
- **Книги auto-create при импорте?** Нет - подмена source provenance.
  Find-or-skip с warning - пользователь явно импортирует книги
  через основной flow если нужно
- **Один endpoint /import vs два?** Один с content-type routing.
  Spring routes на одном path по `consumes` (JSON body для curl,
  multipart для UI)

### Docs

- ADR-037 в `decisions.md` с rejected alternatives (inline books,
  imported UUIDs reuse, auto-create books, multipart-only)
- `api-contract.md` новая секция «Topic export/import API» с описанием
  обоих endpoints + DTO + warnings semantics. History entry добавлен
- `roadmap.md` Этап 6 → `[x]` JSON export/import

### Verify

- Backend: `./mvnw verify` 575/575 BUILD SUCCESS
- Frontend: `npx tsc --noEmit -p tsconfig.app.json` clean,
  `npm run lint` 0 errors (4 pre-existing warnings),
  `npm run build` 2.55s ok,
  `npm run test:run` 170/170 pass
- Smoke (curl):
  ```
  curl -s http://localhost:9090/v3/api-docs | grep -o "topics/import\|topics/.*export" | sort -u
  /api/v1/topics/import
  /api/v1/topics/{topicId}/export
  ```
  endpoints зарегистрированы

### Что осталось в Этапе 6

- Полнотекстовый поиск по содержимому узлов (Postgres `tsvector`) -
  низкий приоритет, ждёт когда базы наполнятся
- Реализация Dung's argumentation framework - research-grade фича,
  не блокирует основной MVP

### Следующий шаг

Этап 6 закрыт по приоритетной части. Можно двигаться к
Этапу 17 OCR / другим Опциям A-H из SESSION_START_PROMPT по выбору
Абдулы

---

## 2026-05-17 - Сессия 39 продолжение, delete UX unification (#7)

После hotkey unification Абдула заметил разнобой: context menu
«Удалить» удалял silent, а Del/Backspace (только что добавленный
subagent'ом коммитом `4a4002d`) показывал native `window.confirm()` -
уродский, не локализованный, блокирующий. Унифицировали через
паттерн Gmail/Slack: оба пути теперь silent delete + toast.success
с действующей кнопкой «Отменить» (5 сек TTL по defaults success
toast)

### Frontend (1 commit + docs)

- `XXX` fix(frontend) - убрали `window.confirm()` целиком из
  `GraphCanvas.handleDelete`. Единая точка `runDelete(nodeIds, edgeIds)` -
  используется из context menu (`deleteOneNode`/`deleteOneEdge`),
  hotkey Del/Backspace (`handleDelete`) и toolbar bulk-delete.
  Snapshot узлов до DELETE → toast.success с action «Отменить» →
  при клике `restoreNodeFromSnapshot` через POST `/api/v1/nodes`
  + PATCH posX/posY. Edges НЕ восстанавливаются (новый id у
  re-created узла) - предупреждение через tooltip-hint у Undo кнопки
- `ToastAction.hint?: string` - расширили API toast action button
  опциональным title-tooltip. Используется для
  «связи не восстанавливаются - привяжите вручную»
- 4 новых i18n ключа: `graph.node.deleted_toast`,
  `graph.node.deleted_undo`, `graph.node.undo_failed`,
  `graph.node.undo_no_edges_hint` + `graph.edge.deleted_toast` +
  `graph.node.deleted_toast_multi` (ru/ar)
- 3 новых vitest в `GraphCanvas.test.tsx`: confirm spy assertions +
  toast appearance + undo flow с POST mock

### Решение про undo

Прагматичный путь: **re-create без edges**. Альтернативы:
1. Backend soft-delete + revive endpoint - сохраняет id + edges,
   но требует миграцию (`deleted_at`) + новый endpoint + изменение
   запросов исключающих soft-deleted. Overkill для случая «упс,
   нажал не туда»
2. Frontend re-create с edges - проблема: после DELETE backend каскадно
   удаляет edges, restore'ить их нужно отдельной серией POST'ов с
   риском rule violations (ADR-010 матрица). И всё равно новый id

Выбран (3): undo восстанавливает только узел через POST. Цена -
edges теряются - честно сообщается через tooltip. Большинство
случайных удалений - leaf узлы где edges и так минимальны

### Docs

- `roadmap.md` - #7 в «User feedback Сессии 38»
- `frontend/docs/ui-guidelines.md` - **новая секция «Destructive
  actions»** с правилом «не использовать native confirm/alert/prompt»

### Verify

- `npx tsc --noEmit -p tsconfig.app.json` clean
- `npm run lint` 0 errors (4 pre-existing warnings)
- `npm run build` 2.55s ok
- `npm run test:run` 170/170 pass (167 baseline + 3 GraphCanvas
  delete UX)
- Playwright headless smoke - все 12 шагов pass:
  - 0 native confirm на любом пути удаления (Del + context menu)
  - toast.success появляется с Undo кнопкой
  - tooltip-hint у Undo показывает предупреждение про edges
  - клик Undo восстанавливает узел (count возвращается)
  - context menu Удалить тоже silent + toast undo
  - скриншоты `/tmp/delete-ux-{1-6}-*.png`

---

## 2026-05-17 - Сессия 39, hotkey unification (#2 / #4)

Параллельно с bug-fix subagent'ом закрыли последние два observable
замечания пользователя (#2 Alt+K на не-EN раскладке, #4 ⌘+↵ submit).
Вместо точечного fix'а провели **системную унификацию** всех keyboard
shortcuts через `react-hotkeys-hook` 5.x с обёрткой `useHotkey`
(ADR-036). Заодно подобрали Del/Backspace handler subagent'а (#3) -
мигрировали на ту же систему

### Frontend (4 commits)

- `1ba8faa` feat **infra** - `react-hotkeys-hook@5.3.2` +
  `shared/hooks/useHotkey.ts` (тонкая обёртка с дефолтами:
  preventDefault, enableOnFormTags=false, useKey=true для
  layout-independence) + `shared/components/ui/ShortcutHint.tsx`
  (отображение combination как набор `<Kbd>` с platform-aware glyph'ами:
  `mod` → `⌘` Mac / `Ctrl` Win/Linux). 8 vitest (useHotkey 3 +
  ShortcutHint 5)
- `e4b5938` refactor **миграция 16 файлов**:
  - App.tsx (Alt+K palette - решает #2 через useKey:true)
  - CommandPalette (escape/arrows/enter + enableOnFormTags)
  - CitationPicker, ContextMenu, AvatarMenu, BellMenu, Select,
    NodeSelect, useGraphEscape - escape close
  - GraphCanvas Del/Backspace (#3 migrated на useHotkey
    `'delete,backspace'`)
  - FormModal - автоматический `mod+enter` submit +
    `<ShortcutHint keys="mod+enter">` в footer. Решает #4.
    `<Kbd>⌘</Kbd>` хардкоды убраны из AddNodeModal/AddEdgeModal
  - Header `<ShortcutHint keys="alt+k">` вместо `<Kbd>Alt</Kbd><Kbd>K</Kbd>`
  - PageJump/PdfViewer inline onKeyDown оставлены с комментариями
    (form-bound Enter-to-submit, не global hotkey - идиоматично)
- `b2517c3` fix **#2/#4 + preventDefault gotcha** - useGraphEscape
  `preventDefault: false` на уровне опций + ручной
  `e.preventDefault()` в callback только когда реально обрабатываем.
  Иначе react-hotkeys-hook стопал бы Esc до того как native
  `<dialog>` его получит - Modal не закрывался бы по Escape

### Docs (этот commit)

- ADR-036 react-hotkeys-hook + альтернативы (vanilla, hotkeys-js,
  tinykeys) с обоснованием
- `frontend/docs/coding-standards.md` секция Hotkeys: useHotkey
  вместо addEventListener, modifier `mod` для cross-platform,
  preventDefault gotcha для native dialog, `ShortcutHint` для UI
- `gotchas.md` запись «event.key vs event.code в keyboard handlers»
  с reproducer ru/ar/en раскладок
- roadmap: #2/#4 → `[x]` (#3 уже был помечен subagent'ом, чуть
  доуточнили формулировку)

### Verify

- `npx tsc --noEmit -p tsconfig.app.json` clean
- `npm run lint` 0 errors (4 warnings pre-existing)
- `npm run build` 2.57s ok
- `npm run test:run` 167/167 pass (156 baseline + 8 useHotkey/ShortcutHint
  + 3 от bug-fix subagent'а AdminShamela)
- playwright headless smoke 5/5:
  - Alt+K open palette
  - Esc close palette
  - AddNodeModal open
  - Esc close AddNodeModal (после preventDefault fix)
  - Cmd+Enter submit AddNodeModal

### Что осталось

- #6 финальное решение по шрифту - waiting Абдулу
- Опции A-H из SESSION_START_PROMPT не тронуты

### Следующий шаг

Все 6 user feedback закрыты. Можно двигаться к Опциям A-H по выбору
Абдулы (Этап 17 OCR / импорт-экспорт темы JSON / прочее)

---

## 2026-05-17 - Сессия 39, user feedback #1 / #3 / #5 / #6

Закрыли 4 из 6 observable замечаний пользователя из конца Сессии 38
(#2 и #4 - hotkey unification - параллельно ведёт другой subagent).
Backend +2 IT (NodeServiceIT 9→11), frontend +3 vitest
(AdminShamelaPage.test новый). Все коммиты атомарные

### Backend (1 commit)

- `9e8e045` feat **#1 root protection** - `NodeIsRootException` 409
  Conflict. `NodeService.deleteNode` подтягивает `Topic` и сверяет
  `nodeId == topic.rootNodeId` ДО удаления. Иначе бэк бы отдал 500
  или каскадно разрушил граф. `GlobalExceptionHandler` мапит в
  Problem Details `type=node-is-root` + `nodeId` / `topicId` properties.
  +2 IT: root throws, non-root succeeds (sanity)

### Frontend (3 commits)

- `c6c8188` feat **#5 shamela toast UX** - `AdminShamelaPage`
  `formatShamelaError` мапит `problem.type` через `ApiError.is(suffix)`:
  shamela-api-error → «внешний сервис shamela.ws недоступен. возможно
  требуется VPN или сервис временно лежит. попробуйте позже»; archive
  → «не удалось распаковать»; reader → «ошибка чтения каталога».
  Unknown тип фолбэк на title+detail. +3 vitest в новом
  `AdminShamelaPage.test.tsx` (502 case, archive case, fallback)
- `4a4002d` feat **#1 + #3 GraphCanvas** - root protection (UI):
  - `rootNodeId = graph.topic?.rootNodeId` derived
  - context menu: для root пункт «Удалить» рендерится disabled с
    подсказкой («корневой вопрос нельзя удалить - удалите тему
    целиком»), для не-root - обычный danger
  - bulk-delete из toolbar: фильтрует root, toast.warning после
    успеха что один узел пропущен
  - `deleteOneNode` защитный barrier - toast.warning если будущая
    точка входа попробует удалить root
  - Del/Backspace handler (#3): `useEffect` с `event.code` (любая
    раскладка), игнорит фокус в input/textarea/contentEditable +
    открытый modal + контекстное меню. Триггерит `handleDelete` -
    root filter уже там. TODO: hotkey subagent мигрирует на единую
    систему через react-hotkeys-hook

### Docs (1 commit, далее)

- #6 диагностика шрифта через playwright (см. ниже)
- ADR не нужен - #1 это bug fix, #5 - UX, #6 - диагностика без
  изменения

### #6 диагностика - результат playwright

`http://localhost:5173/books`:
- `--font-book-title` CSS var = `'Manrope', 'Source Serif', Georgia, serif`
  - **уже не EB Garamond** как обещает комментарий в tokens.css
  (возможно subagent типографии Сессии 36 не докоммитил, либо
  rollback произошёл)
- `document.fonts.size = 0` - ноль web-fonts загрузилось вообще
  (включая Amiri для арабских title)
- Причина: WSL2 corp proxy 407 блокирует Google Fonts CSS request
  (HTML preconnect → `fonts.googleapis.com` → 407). Известная gotcha
- Для всех 5 книг `book.language='ar'`, поэтому Card.Title идёт
  по `arabic=true` ветке → `font-arabic` class →
  `'Amiri','Scheherazade New','Noto Naskh Arabic',serif` →
  все три отвалились через прокси → fallback **system serif**
  (Liberation Serif на Linux/WSL2)
- screenshot: `/tmp/book-list-fonts.png`. Выглядит **читаемо** -
  это нормальный serif. «выврвиглазность» - вероятно из-за
  отсутствия типографики (italic glyphs, hinting), которая в
  production browser с интернетом будет другая
- **Не меняем шрифт** - решение по визуальному дизайну за Абдулой.
  Можно: (a) в production с реальным интернетом проверить как
  EB Garamond/Amiri выглядят; (b) если в production тоже плохо -
  обсудить переход на Lora / PT Serif / Old Standard TT; (c)
  если в WSL2 хочется хорошего dev preview - подключить fonts
  через локальные `@font-face` файлы в `public/fonts/` минуя
  Google CDN

### Что НЕ закрыто в Сессии 39

- **#2 Alt+K layout fix** - параллельно делает hotkey subagent
- **#4 Cmd+Enter + централизация hotkeys** - там же. Будет
  отдельный handoff от hotkey subagent
- **#6 финальное решение по шрифту** - waiting Абдулу
- Опции A-H из SESSION_START_PROMPT не тронуты (вначале #1-#6)

### Следующий шаг

Если hotkey subagent ещё не закончил - подождать его коммитов,
проверить что #2/#4 действительно закрыты. Если да - двигаться к
Опции A (Этап 17 OCR) или B (импорт/экспорт темы JSON) из
SESSION_START_PROMPT по выбору Абдулы

---

## 2026-05-17 - Сессия 38, post-review fixes Этапа 16

Закрыли critical issue + 3 important issue из code review Сессии 37.
Критическое - после `POST /imports/file` загруженный PDF был в MinIO +
`library_files` catalog, но **не читаем** через `PdfService` (единственный
`PdfLinksSourceProvider` смотрел `metadata.pdf_links` который
`FileImportService` не пишет). Кнопка «Открыть книгу» в FileUploadModal
toast вела в reader который не мог получить PDF - critical UX gap

### Backend (5 commits)

- `b5d4cc4` feat **Этап 16.h** - `UserUploadProvider` (`@Order(50)`,
  выше `PdfLinksSourceProvider` order=100). `supports` - true если
  есть active blob в `library_files` с `source_type=USER_UPLOAD`.
  `getMetadata` возвращает single PdfFileInfo (page_count из
  `book.metadata.pdf_page_count`). `locateFile` резолвит
  `(bucket, storage_key)` из catalog - никакого upstream download
- Новый репозиторный метод `findActiveByBookIdAndSourceType` для
  scoped lookup. `PdfService` javadoc обновлён - перечисляет оба
  provider'а
- Тесты +11: 9 кейсов `UserUploadProviderIT` через Testcontainers
  MinIO+Postgres + 1 E2E `POST_upload_thenGET_pdfInfo_...` в
  `FileImportControllerIT` (upload → GET /pdf/info → 200 со списком →
  GET /pdf → 200 PDF). Этот E2E - регрессионный якорь, дублировать
  для каждого нового способа создания Book
- `dcfdf24` fix **BucketBootstrap concurrent startup** - catch
  `BucketAlreadyOwnedByYouException` + `BucketAlreadyExistsException`
  при race condition между двумя pod'ами на createBucket. Трактуется
  как success, INFO лог с e.getClass().getSimpleName() для debug
- `5c5277e` fix **language whitelist** в FileImportController.
  Whitelist `Set.of("ar","ru","en")` (mirror frontend FileUploadModal).
  Blank/null - валидно (сервис применит default "ar"), вне whitelist →
  422 `file-import-error`. Закрывает contract drift
- `f9519c0` docs - уточнить комментарий в FileImportService про порядок
  pages/S3. Старый утверждал «защищает от pages без blob'а», на самом
  деле наоборот - от blob без pages при page-extraction failure.
  Edge case commit DB failure после S3 put → orphan blob упомянут с
  отсылкой на OrphanDetectionJanitor 25.b

### Проверки

- `./mvnw verify` - **554/554 pass** (543 до Сессии 38 + 11 новых),
  BUILD SUCCESS за 1:27
- Backend dev :9090 рестартован, поднимается с логом «bucket bootstrap
  завершён - все 4 bucket'а доступны»
- **Smoke на живом backend:** uploaded test PDF
  `/tmp/smoke.pdf` (590 bytes, 1 page) через
  `POST /api/v1/library/imports/file` - получил book_id
  `b683aaf1-a8a3-453b-b06e-bab4066bd0e7`. Затем
  `GET /api/v1/library/books/{id}/pdf/info` → 200 с правильным JSON
  (single-file, label=smoke, pageCount=1). `GET /pdf?fileIndex=0` →
  200 application/pdf с валидным PDF byte content. **Critical gap
  подтверждён закрытым на production-like setup**
- Language whitelist подтверждён на live backend: `language=zzzz` →
  422 с message `language должен быть одним из [ar, ru, en],
  получено 'zzzz'`

### Документация

- `docs/roadmap.md` - в записи закрытого Этапа 16 добавлено упоминание
  **16.h** post-review fix
- `docs/api-contract.md` - в секции File import API добавлена note
  что после upload книга **сразу** доступна через `/pdf/info` + `/pdf`
  endpoints через UserUploadProvider, language whitelist описан в
  таблице полей. Запись в «История изменений»
- `docs/gotchas.md` - **новая gotcha** «Каждый PdfSourceProvider должен
  явно поддержать новый source type» с симптом / причина / решение +
  превентивный паттерн (3-step smoke после новых способов создания Book)
- `docs/progress.md` - эта запись

### Известные мелочи (не блокеры)

- Frontend не трогался - фронт URL `/books/{bookId}` уже правильный,
  reader просто заработал после backend fix. Manual UI verification
  всё ещё нужна (Опция D - responsive sweep плюс sanity check на
  live книгу)
- Smoke book `b683aaf1-a8a3-453b-b06e-bab4066bd0e7` оставлен в
  production-БД (`smoke.pdf`, 1 страница). Можно удалить через
  `DELETE /api/v1/library/books/{id}` (если admin endpoint
  поддерживает USER_UPLOAD) или вручную через mc/psql

### Следующий шаг (для Сессии 39 / далее)

Опции из Сессии 37 остаются актуальными (Этап 17 OCR, Этап 6
импорт/экспорт JSON, 25.d.5 lazy PDF streaming etc). Опция D
**responsive sweep** дополнительно становится приоритетной потому что
PDF reader теперь работает end-to-end (раньше не имело смысла
полировать UX на сломанном flow)

---

## 2026-05-17 - Сессия 37, Этап 16.g - academic fields в FileImportController

Закрыли feature gap из Этапов 16.b/f - расширили `POST
/api/v1/library/imports/file` 6 опциональными academic-полями
(`muhaqqiqName`/`publisherName`/`publicationPlaceName`/`editionNumber`/
`publishedYearHijri`/`publishedYearGregorian`) с теми же диапазонами
что в `CreateBookRequest` (edition 1..99, year 1..9999). Mirror
паттерна 2-step flow в `AddSourceModal` Этапа 20.e: пользователь
больше не должен после upload вторым шагом открывать `BookEditModal`
для добавления тахкика - всё в одной модалке

### Backend

`feat(backend): Этап 16.g - FileImportController academic fields`:

- `ImportMetadata` record расширен с 4 до 10 полей + helper
  `hasAcademicData()` (non-blank string или non-null int). Старый
  4-args конструктор сохранён через delegation на 10-args для
  обратной совместимости с тестами
- `FileImportService.importPdf` развилка: если `hasAcademicData()` -
  вызывает 13-args `BookService.createBook` (которая делает
  `findOrCreate` в справочниках через 20.e инфраструктуру), иначе
  старый 7-args путь без academic FK (shamela ETL-совместимый)
- `FileImportController.uploadFile` - 6 новых `@RequestParam`
  опциональных + helper `validateAcademicRanges()` для ручной
  валидации диапазонов. Bean Validation для `@RequestParam`
  требует `@Validated` на классе + handler для
  `HandlerMethodValidationException`, что в проекте нигде не
  настроено - ручная валидация бросает `FileImportException`
  → 422 `file-import-error`
- 3 новых IT в `FileImportServiceIT`:
  `importPdf_withAcademicData_callsCreateBook13Args` (все 3 FK
  заполнены через findOrCreate), `_withPartialAcademicData_*`
  (mixed null/non-null, blank treated as null),
  `_withoutAcademicData_keepsLegacyPathAndNullFKs` (sanity что
  старый путь работает)
- 3 новых IT в `FileImportControllerIT`:
  `POST_withAcademicMultipart_returns201AndBookHasAcademicFK`
  (verify через JOIN), `POST_withInvalidEditionRange_returns422`,
  `POST_withInvalidYearRange_returns422`
- `./mvnw verify`: **543 IT pass** (было 537, +6). Backend
  верифицирован

### Frontend

`feat(frontend): Этап 16.g - FileUploadModal collapsible academic section`:

- В `FileUploadModal` добавили **свернутую по умолчанию** секцию
  «Академические данные». Toggle через `<button aria-expanded>`
  с chevron icon (ChevronRight / ChevronDown), i18n текст меняется
  по состоянию (`admin.file_upload.academic.show_section` /
  `hide_section`)
- Использовали **существующий** shared `<AcademicMetadataFields/>`
  (тот же что в `BookEditModal` 20.d и `SourceCreateForm` 20.e) -
  **не дублируем**. 6 полей: 3 autocomplete (мухаккик / издатель /
  место) + 3 number (edition / year_hijri / year_gregorian)
- Submit handler trim'нутые non-empty значения отправляет в FormData
  как новые multipart поля. Int-поля через `parseIntOrNull`. Backend
  получает только заполненное (отсутствие param = no FK)
- types регенерированы через `npm run generate-api` - openapi spec
  бэка добавил 6 новых query полей в operation
- 4 новых vitest: collapsed-by-default,
  toggle-раскрывает-6-полей, submit-с-academic-полями-в-FormData
  (мокаем `globalThis.fetch` напрямую - jsdom + node 24 undici не
  парсят `request.formData()` из FormData body, передаётся как-есть
  строкой `"[object FormData]"`. Решение в комменте теста),
  submit-без-academic-не-шлёт-поля
- **156 vitest pass** (было 152, +4). Lint clean, build clean,
  typecheck clean

### Документация

- `api-contract.md` - секция «File import API» дополнена 6 новыми
  полями + примечание о ручной валидации диапазонов. Запись в
  «История изменений» (сверху)
- `roadmap.md` - строка Этапа 16 дополнена упоминанием 16.g
- `progress.md` - эта запись

### Коммиты

- `945d4b9` feat(backend): Этап 16.g - FileImportController academic fields (mirror 20.e)
- `9dbfac4` feat(frontend): Этап 16.g - FileUploadModal collapsible academic section (reuses AcademicMetadataFields)
- `<этот>` docs: 16.g complete - api-contract + roadmap + progress

ADR не требовался - просто следование уже принятому паттерну
ADR-028 (academic citation metadata) + ADR-035 (PDFBox)

---

## 2026-05-17 - Сессия 37, Этап 16.f - PDF upload (frontend admin)

Минимальный admin UX для PDF upload поверх уже готового backend
endpoint'a (см. предыдущую запись от 2026-05-17). Без дизайн-референса
расширили существующую `AdminShamelaPage` третьим Card блоком - быстрый
способ начать пользоваться новой возможностью пока полноценный UX
не дизайнится

### Сделано

**Frontend (2 коммита feat + 1 docs):**

`feat(frontend): apiPostMultipart helper для multipart uploads`:
- `shared/api/client.ts` - расширение low-level `request()`:
  новое поле `formData?: FormData` в `RequestOptions`. Когда передано,
  Content-Type не выставляется вручную - браузер сам формирует
  `multipart/form-data; boundary=...`. Если выставить руками - boundary
  не подставится и Spring multipart parser отвергнет запрос
- новый экспорт `apiPostMultipart<T>(path, formData, options)` - тонкая
  обёртка для FormData uploads
- регенерация `shared/api/types.ts` через `npm run generate-api` -
  появились `FileImportResponse` и `operations.uploadFile`

`feat(frontend): Этап 16.f - FileUploadModal + AdminShamelaPage upload section`:
- `apps/admin/components/FileUploadModal.tsx` (~280 строк):
  - на базе `FormModal` (Modal + form + footer + cancel/submit + error)
  - file picker: стилизованный label с dashed-border вокруг
    скрытого `<input type="file" className="sr-only" accept="application/pdf">`,
    focus-within ring через Tailwind, иконка `FileText`
  - после выбора - preview filename (`<bdi>` + dir=auto для mixed-content) +
    размер в KB/MB через `useNumberFormat` для локаль-aware чисел
  - поля: title (text, `dir="auto"`, `font-naskh` при арабском вводе),
    language Select RU/AR/EN (default ar), description Textarea
  - submit disabled пока `file === null`
  - после 201 - `toast.success` с action «Открыть книгу» который
    делает `navigate('/books/{bookId}')` + `onUploaded` callback
    + reset state + `onClose()`
  - `mapErrorMessage`: 413 → too_large, 415 → wrong_format,
    422 → corrupt_pdf, TypeError → network, fallback → generic
- `apps/admin/components/FileUploadModal.test.tsx` (5 vitest):
  disabled-submit без файла, happy path (file + title + state + toast
  + callbacks), 413/415/422 локализованные сообщения. Conditional render
  `{open && <Modal/>}` + `<MemoryRouter>` для `useNavigate`. Mock
  `HTMLDialogElement.showModal/close` как в AddSourceModal.test
- `apps/admin/pages/AdminShamelaPage.tsx`:
  - добавлен `useState uploadOpen`
  - третий Card блок «Загрузить PDF» между sync-status и search section -
    иконка `FileUp`, title + subtitle, primary кнопка «Загрузить новую
    книгу»
  - conditional render `{uploadOpen && <FileUploadModal .../>}`
  - `onUploaded` триггерит refresh sync-status (увеличение `reloadStatusToken`)
- `shared/i18n/dictionary.ts` - **28 keys** префикс `admin.file_upload.*`
  в обеих локалях ru/ar (section_title/subtitle/action, modal
  title/subtitle, file_label/help/choose/change, field_title/authority/
  language/description, lang_ar/ru/en, submit/submitting, success_toast/
  open_book, error_too_large/wrong_format/corrupt_pdf/network/generic)

**Документация:**
- `roadmap.md` - строка Этапа 16 дополнена упоминанием 16.f frontend
  (третий Card блок + apiPostMultipart helper + локализованный error
  mapping + 5 vitest)
- эта запись в `progress.md`
- `api-contract.md` **не правилось** - frontend подсессия не меняла
  endpoint contract (только потребляет уже задокументированный)

### Verify

- `npm run lint` - 0 errors (3 pre-existing warnings unrelated)
- `npm run test:run` - **152/152 passed** (147 baseline + 5 новых).
  Pre-existing AddSourceModal "reliability radio" fail упомянутый в
  SESSION_START_PROMPT уже не воспроизводится
- `npx tsc --noEmit -p tsconfig.app.json` - clean
- `npm run build` - SUCCESS, 2.77s, 2344 modules transformed
- **playwright smoke (headless WSL2)** - открыл `/admin/shamela`,
  убедился что кнопка «Загрузить новую книгу» рендерится, клик
  открывает модалку с правильным title «Загрузка PDF в библиотеку»,
  file picker label «Выбрать файл» виден, submit-кнопка disabled без
  файла, все поля (title/language/description) присутствуют.
  Скриншоты в `/tmp/file-upload-modal-*.png`

### Гетча тестового окружения

В jsdom Request с `FormData` body не сохраняет multipart
Content-Type корректно - `request.formData()` падает с
"Content-Type was not multipart/form-data". В реальном браузере fetch
с FormData всегда формирует правильный `multipart/form-data; boundary=...`.
Решение: в happy-path тесте проверяем хит endpoint + state
после-успеха (onUploaded/onClose/toast), не парсим тело запроса.
Реальная multipart-сборка проверена руками через playwright (запрос
до бэка пока не дошёл, но это сценарий следующей manual проверки)

### Что НЕ сделано

- Поля `authorityName` / `muhaqqiqName` / `publisherName` /
  `publicationPlaceName` / `editionNumber` / `publishedYearHijri` /
  `publishedYearGregorian` - в задаче упомянуты но **бэкенд endpoint
  их не принимает** (только `title`, `authorityId` UUID, `language`,
  `description`). Эти поля выставляются через PATCH metadata позже
  (как в `BookEditModal`). Сразу при upload их нет - сознательное
  упрощение MVP. Если нужно - сделать через `CreateBookRequest`
  отдельный flow или дополнить `FileImportController` query-params
- `authorityId` поле не вынесено в UI - требует autocomplete по
  authorities (есть `GET /api/v1/authorities`), не самый минимальный
  UX. Отложено
- Drag-and-drop для file - простой picker через label

### Следующий шаг

- **Опция 1**: manual smoke на реальный PDF через UI (Абдула) -
  выбрать локальный PDF, загрузить, убедиться что `/books/{id}` открыл
  валидный reader с N-страничным контентом
- **Опция 2**: добавить authorityId autocomplete в FileUploadModal
  (получить `GET /authorities`, отрендерить как Select)
- **Опция 3**: вернуться к опциям A-F из Сессии 36 entry
  (RetryStrategy migration / Source picker Корана / Хадисов / NodeCard
  footer chips / cleanup pre-existing)

---

## 2026-05-17 - Сессия 37, Этап 16 - PDF/EPUB upload (backend)

Закрытие всего Этапа 16 одним подходом - PDF upload через multipart
endpoint, page-by-page extraction через PDFBox, blob storage в
существующий MinIO bucket. EPUB сознательно отложен (см. ADR-035) -
нет UX-кейса, основной контент - PDF

### Сделано

**Backend (3 коммита feat + 1 docs):**

`feat(backend): Этап 16.a/d - FileImportService с PDFBox page-by-page extraction`:
- `pom.xml`: добавлен `org.apache.pdfbox:pdfbox:3.0.5` (~3MB transitive
  с fontbox + commons-logging)
- `library/imports/` - новый пакет с тремя файлами:
  - `ImportMetadata.java` - record для опциональных полей
    (title/authorityId/language/description)
  - `FileImportException.java` - кастомное исключение для bad PDF
  - `FileImportService.java` - бизнес-логика `@Transactional importPdf()`:
    `Loader.loadPDF(byte[])` → check encrypted/empty → resolve title
    (user > PDF metadata > filename) → `BookService.createBook` →
    `PDFTextStripper.setStartPage/setEndPage` для каждой phys-страницы →
    `pageRepository.save(Page{pageNumber=i+1, pdfPageNumber=i+1, textContent})`
    → `ObjectStorageService.putAndRegister(bucket=userUploads,
    sourceType=USER_UPLOAD)`. Helper'ы: `resolveTitle`,
    `buildBookMetadataJson` (с user_uploaded/original_filename/
    pdf_page_count маркерами), `sanitizeFilename` (strip path,
    replace whitespace -> `_`)

`feat(backend): Этап 16.b/c - POST /library/imports/file multipart endpoint + bucket bootstrap`:
- `library/imports/web/FileImportController.java` -
  `POST /api/v1/library/imports/file` multipart/form-data:
  - `@RequestParam("file") MultipartFile` + опциональные string params
    + `@CurrentUser UUID`
  - Pre-validation: empty file → 422, wrong MIME → 415
  - 201 Created + Location header
- `FileImportResponse.java` - record для ответа
- `UnsupportedMediaTypeException.java` - для 415 mapping
- `application.yml`:
  - `spring.servlet.multipart.max-file-size=50MB` +
    `max-request-size=50MB`
  - Новый `storage.bucket-bootstrap.enabled` flag (default false для IT),
    в local-profile = true
- `library/storage/BucketBootstrap.java` - `@ConditionalOnProperty`
  `@EventListener(ApplicationReadyEvent)` создаёт 4 bucket'а
  idempotent через `HeadBucket → CreateBucket` + включает versioning
  для 3 critical. Удобно для dev first-run
- `GlobalExceptionHandler.java` расширен 3 handlers:
  - `FileImportException` → 422 `file-import-error`
  - `UnsupportedMediaTypeException` → 415 `unsupported-media-type`
  - `MaxUploadSizeExceededException` (Spring multipart) → 413
    `payload-too-large`

`feat(backend): Этап 16.e - IT для FileImport через Testcontainers MinIO + MockMvc`:
- `FileImportServiceIT` (10 тестов через `@SpringBootTest`):
  3-page PDF → Book+3Pages с правильным pageNumber/pdfPageNumber/
  textContent, title priority (override/metadata/filename), MinIO
  blob verification через `listObjectVersions`, filename
  sanitization (path strip + space replace), empty/corrupted → 422,
  default language ar, metadata JSON markers
- `FileImportControllerIT` (6 тестов через MockMvc):
  valid PDF → 201 + Location + body, wrong MIME → 415, empty → 422,
  corrupted → 422 c detail "PDF", missing X-User-Id → 400, minimum
  fields path
- PDF фикстуры генерируются programmatically в каждом тесте через
  PDFBox (`PDDocument` + `PDPageContentStream`) - не коммитим
  binary'и. WSL2 fallback `LiberationSans for Helvetica` - не
  аффектит text extraction

**Документация:**
- `decisions.md` - **новый ADR-035** «PDFBox для page-by-page
  extraction (vs Tika, EPUB отложен)»: альтернативы (Tika избыточен -
  внутри тоже PDFBox; Aspose/iText commercial; PDFBox 2.x устарел),
  EPUB обоснование откладывания, последствия (50MB limit, encrypted
  not supported, scanned-images empty text_content, WSL2 fontbox
  warning, ADR-024 bucket выбор)
- `api-contract.md` - новая секция «File import API (ADR-035, Этап 16)»
  с полным контрактом endpoint'а + не-реализовано список + entry
  в «История изменений»
- `roadmap.md` - Этап 16 целиком сжат в строку и перемещён в
  «Закрытые этапы». 16.a-e checkboxes удалены
- эта запись в `progress.md`

### Verify

- `./mvnw verify` - **537/537 tests pass, BUILD SUCCESS**, 01:23 min
  (было 521, +16 новых: 10 FileImportServiceIT + 6 FileImportControllerIT)
- `./mvnw -DskipTests compile` - 236 source files, zero warnings от
  нашего кода (Mockito self-attaching warning - не наш)

### Гочи / отклонения

- **EPUB отложен сознательно** - в спеке этапа было «опционально,
  если EPUB кажется too much - сделай только PDF». Сделал только
  PDF. epub4j-core добавит другую API (manifest+spine parsing,
  chapter vs page semantics), нетривиальная работа без UX-кейса.
  Зафиксировано в ADR-035
- **BucketBootstrap default off в тестах** - IT поднимают свои MinIO
  через `@Container static MinIOContainer` + сами создают buckets в
  `@BeforeEach`. Если bootstrap бы запускался при каждом
  `@SpringBootTest` - race с тестовым setUp. Решение:
  `@ConditionalOnProperty matchIfMissing=false`, в `application-local`
  выставляем `true`. В test config-section yaml не выставляем
- **PDFBox 3.x new API** - `PDDocument.load(byte[])` deprecated в 3.x,
  использовать `Loader.loadPDF(byte[])` из `org.apache.pdfbox.Loader`.
  Это static factory class. Не очевидно сходу - первый шаг попробовал
  старый API из 2.x docs, компилятор сразу укажет
- **WSL2 fontbox warning** - `Using fallback font LiberationSans for
  base font Helvetica` появляется на каждом text extraction. Не
  аффектит результат (text всё равно extract'ится) - PDFBox использует
  LibreOffice fonts из системы. Warning не лечится без install
  оригинальных Helvetica
- **Scanned-images PDF (нет text layer)** - PDFTextStripper возвращает
  пустую строку. Сохраняем как `""` (empty string), CHECK constraint
  `lib_pages_content_present` (`text_content IS NOT NULL OR image_url
  IS NOT NULL`) допускает - проверяет только NULL. В будущем (Этап 17)
  такие страницы пойдут через OCR pipeline
- **Page.chapter_id=null для всех страниц** - PDF outline (bookmarks)
  не парсится в этой версии. Все page без chapter. Если позже
  понадобится structure - добавим `PDDocumentOutline` walker

---

## 2026-05-17 - Сессия 37, 25.b - RetryStrategy migration

Закрытие последнего пункта Этапа 25.b operational hardening - миграция
с deprecated `RetryPolicy` AWS SDK v2 на современный `RetryStrategy` API.
Этап 25.b теперь полностью закрыт

### Сделано

**Backend (1 коммит `feat(backend): Этап 25.b - RetryStrategy ...`):**

- `S3ClientConfig.java`:
  - import `software.amazon.awssdk.core.retry.RetryPolicy` удалён,
    добавлены `software.amazon.awssdk.awscore.retry.AwsRetryStrategy` +
    `software.amazon.awssdk.retries.api.RetryStrategy`
  - `RetryPolicy.defaultRetryPolicy().toBuilder().numRetries(N).build()`
    заменён на
    `AwsRetryStrategy.standardRetryStrategy().toBuilder().maxAttempts(N+1).build()`.
    `+1` потому что новый API считает initial attempt частью лимита,
    legacy `numRetries` - нет. Семантика exponential backoff с jitter +
    retry на 5xx / throttling / connection reset идентична
  - `ClientOverrideConfiguration.builder().retryPolicy(...)` →
    `.retryStrategy(...)`. Остальные настройки (`apiCallTimeout`,
    `apiCallAttemptTimeout`) не тронуты
  - JavaDoc обновлён, упоминает что используется `RetryStrategy` и
    почему `+1` для `maxAttempts`
- Документация: `roadmap.md` 25.b отметка `[x]` + summary,
  `decisions.md` ADR-024 Amendment про переход на `RetryStrategy`,
  эта запись в `progress.md`

### Verify

- `./mvnw verify` - **521/521 tests pass, BUILD SUCCESS**, 01:14 min
  (включая 6 IT `IntegrityVerificationJobIT`, 6 IT `OrphanDetectionJanitorIT`,
  ObjectStorageService IT с MinIO testcontainer)
- `./mvnw -DskipTests clean compile` - zero deprecation warnings
  (filter применил для AWS SDK)
- Версия AWS SDK 2.44.4 (bom-import) - `RetryStrategy` доступен с 2.26.x,
  обновлять SDK не пришлось
- API contract: внешних изменений нет (внутренняя конфигурация
  S3 client'а)

### Гочи / отклонения

- В первом проходе чуть не использовал `AwsRetryStrategy.defaultRetryStrategy()`
  (общий, абстрактный) - но `standardRetryStrategy()` возвращает
  конкретный `StandardRetryStrategy` с `toBuilder()` нужным для
  `maxAttempts` override. `defaultRetryStrategy()` возвращает только
  базовый `RetryStrategy` без явного типа конкретного варианта
- Проверка bytecode'а через `javap` была быстрее чем чтение AWS docs -
  подтвердил что `ClientOverrideConfiguration.Builder.retryStrategy(RetryStrategy)`
  существует и работает рядом со старым `.retryPolicy(...)` (тот не
  удалён, только deprecated). Это даёт backward compat если кто-то
  ещё на legacy

---

## 2026-05-16 - Сессия 36, подсессия 19.d (full-stack) - Answer sources, ADR-033 итерация 3

Закрытие Этапа 19.d - параллельная иерархия `answer_sources` для
ответов в Q&A. 3-я итерация паттерна ADR-033 (node_sources, question_
sources, answer_sources) подтверждает что platform pivot (ADR-018)
масштабируется без перехода на generic citations table.

### Сделано

**Backend (1 коммит `feat(backend): Этап 19.d ...`):**

- Migration 31 `answer_sources` table - mirror migration 28 с
  `answer_id` FK на `answers(id) ON DELETE CASCADE`, surrogate UUID PK
  сразу (FK variant A), positional fields для TEXT/PDF/REGION mode,
  CHECK constraint один-из-четырёх, 5 индексов (2 full + 3 partial)
- `qa/domain/AnswerSource.java` - record + 3 static factories
  textMode/pdfMode/regionMode, идентичен `QuestionSource` substitute
  question_id → answer_id
- `qa/repository/AnswerSourceRepository.java` - JDBC RowMapper + save/
  findById/findByAnswerId/deleteById + 9-LEFT-JOIN `JOIN_LOCATION_SQL`
  + `AnswerSourceWithLocation` record + `findByAnswerIdWithLocation`/
  `findByIdWithLocation`. **Gotcha поймал**: SQL alias `as` для
  `answer_sources` это reserved keyword Postgres - использовал `ansrc`
- `qa/service/AnswerCitationService.java` - identical логика с
  `QuestionCitationService`: TEXT/PDF/REGION validation, page.bookId
  match, ensure-or-create Source per book, snapshot location format,
  pdfBbox JSON serialization. Кидает `AnswerNotFoundException` (уже
  существовал от 19.c)
- `qa/web/controller/AnswerCitationController.java` - 3 endpoint
  `/api/v1/answers/{id}/{citations|sources}` mirror QuestionCitation
- `qa/web/dto/AnswerSourceResponse.java` - record с `answerId` вместо
  `questionId`
- `QaDtoMappers.toResponse` перегружен по типу аргумента - один класс
  на оба citation flow в qa модуле (question + answer)
- 19 IT тестов `AnswerCitationServiceIT` (Testcontainers, mirror 18
  от QuestionCitationServiceIT + extra empty list test)

**Frontend (1 коммит `feat(frontend): Этап 19.d ...`):**

- `shared/components/citation/CitationPicker.tsx` - расширение
  `targetType` union literal: `'nodes' | 'questions' → 'nodes' |
  'questions' | 'answers'`. Никаких других изменений - URL формула
  `/api/v1/${targetType}/${targetId}/citations` уже generic
- `apps/qa/components/AnswerCitationsSection.tsx` - новый компонент
  mirror `QuestionCitationsSection` с подменой questionId → answerId.
  Использует `targetType='answers'` для CitationPicker, тот же
  `SourceCard` без fork. Reuse i18n keys `qa.sources.*`
- `apps/qa/components/AnswersSection.tsx` - в `AnswerCard` добавлен
  toggle «Показать/Скрыть источники» (chevron icon), при раскрытии
  рендерится `AnswerCitationsSection` collapsed-by-default. Не
  перегружает layout AnswerCard когда у вопроса много ответов
- `shared/i18n/dictionary.ts` - 3 новых ключа RU/AR
  (`qa.answers.sources_show/sources_hide/sources_attach`)
- `shared/api/types.ts` regenerated через `npm run generate-api` -
  `AnswerSourceResponse` schema появилась автоматически из OpenAPI

**Docs (1 коммит `docs: 19.d complete ...`):**

- `api-contract.md` - новая секция «Answer sources API (ADR-033
  итерация 3, Этап 19.d)» с описанием 3 endpoint + запись в истории
  изменений
- `roadmap.md` - 19.d `[x]` строка с summary и явной отметкой что
  ADR-033 валидирован 3 раза
- `decisions.md` - Amendment к ADR-033 «3-я итерация - паттерн
  валидирован», триггер пересмотра передвинут на 4-й entity type +
  метрики 19.d для сравнения с 19.b

### Verify

- `./mvnw clean verify` - **507/507 tests pass, BUILD SUCCESS**
  (включая мои 19 новых IT, общий счёт вырос с 488 до 507)
- frontend typecheck clean, lint 0 errors (3 pre-existing warnings)
- frontend tests **146/147** - 1 unrelated pre-existing
  AddSourceModal radio fail (зафиксирован в session-36-final-snapshot)
- frontend build SUCCESS 2.41s
- backend перезапущен на :9090, types.ts regen прошёл
- playwright headless smoke на existing question
  `3796f633-1822-...` подтвердил:
  - AnswerCard рендерится с 2 кнопками toggle и attach
  - Toggle «Показать источники» раскрывает AnswerCitationsSection
  - Внутри секции «Источники не прицеплены» + кнопка «Привести источник»
  - Кнопка открывает CitationPicker dialog с правильным targetLabel
    (preview body ответа, не title вопроса)
- end-to-end через curl: POST /citations создал structured citation
  с полным academic 9-LEFT-JOIN response (authority/book/muhaqqiq/
  publisher/edition/year), DELETE /sources/{id} вернул 204, cleanup OK

### Не сделано в 19.d (отложено)

- Freeform LEGACY citation для answers (schema готова, controller
  endpoint - если появится UX-кейс типа AddSourceModal-аналога для
  answers)
- Frontend тесты для AnswerCitationsSection - mirror policy от 19.b
  (там тоже не делали unit тестов QuestionCitationsSection, smoke
  через playwright достаточно)
- Soft delete + audit - после auth

### Что посмотреть руками

1. Открой `/qa/3796f633-1822-45fa-87e1-6337a603b6f1`
2. Создай новый ответ через форму внизу
3. На AnswerCard нажми кнопку «Показать источники»
4. Должен раскрыться блок «Источники» с «Источники не прицеплены» и
   кнопкой «Привести источник»
5. Нажми кнопку - откроется CitationPicker модалка с header
   «Привести источник для: «<твоё body превью>»»
6. Выбери книгу из списка слева, страницу, выдели текст, нажми
   «Привести» - citation создаётся, модалка закрывается, в секции
   появляется SourceCard
7. На SourceCard нажми «Перейти к источнику» - откроется reader
   с подсветкой fragment'a (тот же deep-link flow что для questions)
8. Кнопка корзины в SourceCard - detach citation, появляется toast
   «Источник отвязан»

---

## 2026-05-16 - Сессия 36, подсессия 20.e (full-stack) - AddSourceModal academic form

Параллельная подсессия закрытия Этапа 20.e - AddSourceModal расширенная
форма для `sourceType=BOOK` с заполнением academic metadata. Работала
параллельно с 19.c подсессией (разные файлы - 20.e касается library/
source/citation, 19.c - qa/answer/question). Конфликтов с 19.c не было,
коммиты прошли atomically.

### Сделано

**Backend (1 коммит `feat(backend): Этап 20.e ...`):**

- `CreateBookRequest` расширен 6 опциональными academic полями
  (muhaqqiqName / publisherName / publicationPlaceName / editionNumber /
  publishedYearHijri / publishedYearGregorian), `@Min/@Max` validation
  совпадает с UpdateBookRequest
- `BookService.createBook` перегружен (старая 7-args сохранена для
  shamela ETL + legacy callers, новая 13-args). resolveFk reused для
  blank/non-blank → null / findOrCreate
- `CreateSourceRequest` расширен опциональным `bookId: UUID`.
  `SourceService.createSource` валидирует Book.exists → 404 при
  nonexistent (вместо FK-violation 500). Старый legacy путь без bookId
  работает как раньше
- 9 новых IT тестов: 4 BookServiceIT (academic FK / без academic /
  partial / duplicate reuses FK), 3 BookControllerIT (POST+GET с
  academic / edition validation / year validation), 2 SourceControllerIT
  (POST с bookId / 404 nonexistent)
- `./mvnw verify` - 484 tests pass (было 475)

**Frontend (1 коммит `feat(frontend): Этап 20.e ...`):**

- Новый shared компонент
  `frontend/src/shared/components/citation/AcademicMetadataFields.tsx`:
  extracted из admin BookEditModal AutocompleteRow + 3 fetch helpers.
  Controlled, не знает про PATCH vs CREATE semantics
- BookEditModal мигрирован на shared компонент - дубль ~150 строк
  удалён, UI identical
- SourceCreateForm.CreateForm расширен полем `academic`. Conditional
  render <AcademicMetadataFields/> только для `sourceType === 'BOOK'`.
  При переключении type academic не очищается (UX)
- AddSourceModal.createAndAttach: 2-step flow для BOOK с заполненным
  academic (POST `/library/books` → POST `/sources` с `bookId` →
  attach), иначе legacy single-step
- types.ts регенерированы из обновлённого OpenAPI
- 4 новых i18n key RU/AR
- 4 новых AddSourceModal vitest теста (section visible / hidden /
  2-step / legacy)
- typecheck clean, lint 0 errors (3 react-refresh warnings, pre-existing
  pattern), tests 146/147 pass (1 unrelated pre-existing reliability
  radio test), build SUCCESS

**Docs (1 коммит `docs: 20.e complete ...`):**

- api-contract.md - запись в «История изменений» 16.05 v1 о расширении
  CreateBookRequest + CreateSourceRequest. Новый ADR не нужен -
  следует ADR-026 + ADR-028
- roadmap.md - 20.e отмечен [x] с описанием
- progress.md - эта запись

### Гочи / отклонения

- В первом проходе мои Edit'ы получили system reminder про "файл изменён
  параллельно" - но при проверке через `git diff` все изменения были на
  месте. Не было реальной race condition с 19.c подсессией. Параноя
  ложная, файлы целы
- BookResponse не содержит academic fields (только BookDetailResponse) -
  IT тест `createBook_withAcademicFields` пришлось переписать на
  POST + GET сценарий чтобы проверять через `$.muhaqqiq.name`
- Сохранил backward compat для legacy callers `BookService.createBook` -
  старая 7-args перегрузка осталась чтобы shamela ETL не сломать

### Открытые вопросы / следующее

- 20.c-e полностью закрыты, Этап 20 academic citation готов
- Можно двигаться к 21 (мульти-юзер / auth) или 19.d (answer voting)



Spawned subagent для закрытия Этапа 19.c (Answers) - главная сессия
делегировала из-за заполнения контекста. Работа в автономном режиме,
3 коммита по очереди.

### Сделано

**Backend (commit `bd16a44`):**

- Migration 29 - `answers` table (id, question_id FK CASCADE, body,
  author_id FK на users, timestamps). Composite index `(question_id,
  created_at)` для основного query «список ответов для вопроса»
- Migration 30 - `questions.accepted_answer_id` nullable FK на
  answers(id) ON DELETE SET NULL + partial index
- `Answer` record + `AnswerRepository` (save/findById/findByQuestionId/
  findByQuestionIdSortedByAccepted/update/deleteById). Метод
  sortedByAccepted - `ORDER BY (id = ?) DESC, created_at` для accepted-
  first сортировки
- `AnswerService` - 6 публичных методов: createAnswer (404 если
  question отсутствует), getAnswersForQuestion (accepted first),
  updateAnswer (PATCH body), deleteAnswer (404), acceptAnswer
  (atomic 2-column update + проверка что answer принадлежит question),
  revokeAcceptance (status -> OPEN)
- `AnswerController` под `/api/v1` - 6 endpoints, derived `accepted`
  boolean в `AnswerResponse` сравнением с `question.acceptedAnswerId`
- `AnswerNotFoundException` + handler 404 `answer-not-found`
- `QuestionResponse` + `Question` record расширены полем
  `acceptedAnswerId: UUID nullable`
- `QuestionRepository` save опускает accepted_answer_id (DEFAULT NULL)
  + новые методы setAcceptedAnswer / revokeAcceptedAnswer
- **20 IT тестов** в `AnswerServiceIT`: create (success / question
  not found / blank body) / list (empty / question not found / sorted
  by created_at / accepted first) / update (success / not found /
  blank) / delete (success / not found) / accept (success / answer
  not in question / question not found / answer not found) / revoke
  (success) / cascade delete / ON DELETE SET NULL (statusUnchanged) /
  bulk insert ordering
- **475/475 tests pass** через `./mvnw verify`

**Frontend (commit `8650d83`):**

- `AnswersSection.tsx` - state machine loading/success/error, list
  AnswerDto через GET, inline-форма (Field.Textarea + counter +
  submit), refetch после create
- `AnswerCard` (внутри файла) - dir="auto" + font-arabic для body,
  derived `accepted` boolean из API, зелёный ribbon «Принят» с
  CheckCircle icon. Кнопки:
  - «Принять как ответ» / «Отозвать принятие» - только если asker
    (DEV_USER_ID === question.askedBy)
  - «Удалить» - только если author (DEV_USER_ID === answer.authorId)
- Интеграция в `QuestionDetailPage.tsx` между QuestionCitationsSection
  и кнопкой удаления вопроса. Новый callback `refetchQuestion` для
  parent чтобы при accept/revoke получить свежий `acceptedAnswerId`
  и `status`
- Регенерирован `types.ts` через `npm run generate-api`
- **12 i18n keys** RU + AR: `qa.answers.{section_title, empty,
  add_button, placeholder, accept_button, revoke_button, accepted_label,
  delete_button, delete_confirm, created, accepted, revoked}`
- Lint: 0 errors, 1 pre-existing warning (SourceCreateForm fast-refresh)
- Typecheck: clean
- Build: clean при чистом test-setup.ts (есть pre-existing experimental
  правка из предыдущей сессии которая ломает tsc -b, не моё)

**Документация (commit pending):**

- ADR-034 - решение Option B (nullable FK accepted_answer_id), 3
  отвергнутые альтернативы (boolean per answer + CHECK / отдельная
  таблица / voting/comments как часть 19.c)
- api-contract.md - новый раздел «Answers API» с 6 endpoints +
  расширение QuestionResponse + changelog запись
- roadmap.md - 19.c отмечен [x]

### Verify

- backend `./mvnw verify`: 475/475 pass
- frontend `npx tsc --noEmit -p tsconfig.app.json`: clean (исключая
  pre-existing test-setup.ts experimental)
- frontend `npm run lint`: 0 errors
- frontend `npm run build`: clean при чистом test-setup.ts
- backend restart на :9090 с JDWP, миграции 29+30 применились

### Технические заметки

- ON DELETE SET NULL семантика - удаление принятого ответа напрямую
  SQL автоматически обнуляет accepted_answer_id, но status в OPEN не
  возвращает (это business decision). Покрыто IT тестом
  `onDeleteSetNull_acceptedAnswerDeleted_fkBecomesNull_statusUnchanged`
- `current user` для UI - через `import.meta.env.VITE_DEV_USER_ID`
  (та же модель как X-User-Id для mutating запросов). До Spring
  Security в Этапе 6
- Frontend `test-setup.ts` - в uncommitted state с experimental
  AbortController override от предыдущей сессии, ломает `npm run
  build`. Не трогал т.к. unrelated к 19.c. На master чистом - билд
  собирается

### Следующий шаг

19.c закрыт. Открытые опции:
- 19.d Voting (up/down votes на answers) - `answer_votes` table,
  weighted ranking
- 19.e Comments на answers - `answer_comments`
- 20.e AddSourceModal расширенная (academic поля для sourceType=BOOK)
- frontend test-setup.ts experimental - либо доделать либо откатить
  (мешает build)
- 12 pre-existing frontend test failures из предыдущей сессии в
  backlog

## 2026-05-16 - Сессия 36 (full-stack) - Этап 19.b валидация ADR-018 platform pivot

Сессия в режиме полной автономии от Абдулы. Стартовала с просьбы
«используй ruflo чтобы автомомно без меня делать все задачи в этом
проекте». Этап 19.b закрыт чисто, далее cleanup опции D + review
feedback + правки правил автономии по запросу user'а.

### Сделано

**Этап 19.b Q&A source attach (3 коммита feat + 1 fix):**

- **Migration 28 question_sources** - объединяет в одну mig 9+23+25:
  surrogate UUID PK сразу (ADR-029), positional fields для
  TEXT/PDF/REGION mode (ADR-027) + LEGACY, CHECK constraint
  один-из-четырёх, 5 индексов (2 full на question_id/source_id +
  3 partial на positional FKs)
- **Параллельная иерархия в `qa/` package** (ADR-033): QuestionSource
  record (с textMode/pdfMode/regionMode factories), QuestionSourceRepository
  (9 LEFT JOIN для structured CitationDetail), QuestionCitationService
  (identical validation+ensure-Source+snapshot+PDF-bbox-serialization
  логика), QuestionCitationController (POST citations/GET sources/DELETE
  sources), QaDtoMappers (делегирует core DtoMappers.toCitationResponse)
- **18 IT тестов**: TEXT/PDF/REGION mode + ensure-Source reuse + 404/400
  паттерны (question/book/page/region not found, wrong-book, invalid
  range, missing bookId) + list + detach + cascade delete
- **Frontend reuse через generic CitationPicker** (refactor: nodeId →
  targetType + targetId + targetLabel). URL формируется как
  `/api/v1/${targetType}/${targetId}/citations`. NodeCitationsSection
  передаёт targetType="nodes"
- **QuestionCitationsSection** (новый apps/qa/components/) - reuse того
  же SourceCard + buildDeepLink + pickLatinTitle. Только library-mode
  citations (без freeform). Optimistic UI на detach. Loading/error/empty
  states. Количество + count badge
- **5 i18n keys RU/AR**: qa.sources.section_title/empty/add_button/
  detached/detach_failed

**Code review через Agent (subagent_type=reviewer):**

Reviewer пометил 4 Important + 3 Minor. Все закрыты:

- detach 404 вместо 400: QuestionCitationService.detachById бросает
  SourceNotFoundException (mirror NodeSourceService)
- DELETE URL hierarchy: `/api/v1/questions/{questionId}/sources/
  {questionSourceId}` (mirror NodeSourceController + место под
  авторизацию по владельцу)
- SourceCardLink interface: SourceCard принимает структурный type
  `{citation, quote, context}` - убрал двойной cast `as unknown as
  NodeSourceResponse` в QuestionCitationsSection. NodeSourceResponse
  и QuestionSourceResponse оба структурно совместимы
- i18n «Символы» в CitationPicker через `citation_picker.chars_label`,
  i18n «(книга)» через `source_form.untitled`, `sources ?? []` null
  safety, IT тест detach_throws404

**Cleanup опция D (1 коммит refactor):**

SourceSearchForm/SourceCreateForm - все cyrillic literals заменены
на t() с ключами `source_form.*` (~17 пар RU+AR). Это закрывает
backlog Сессии 33.

**Правила про subagents удалены (1 коммит docs):**

По запросу user'а 16.05: «если в проекте где-то есть правила не
использовать subagents или рой агентов или подсессии, удали эти
правила». Поправлен SESSION_START_PROMPT + memory feedback_full_
autonomy_mode.md - subagents теперь явно разрешены без ограничений.

**Ruflo MCP использование:**

User спросил почему мало используется ruflo. Объяснил: core Claude
Code Agent tool + Skill + hooks - покрывают большинство кейсов;
ruflo даёт **specific values** через agentdb_pattern-store /
memory_store / agent_spawn. По факту:

- pattern «параллельная иерархия для validation platform pivot»
  сохранён в `mcp__ruflo__agentdb_pattern-store` (HNSW vector
  store, type=architectural-decision, confidence=0.95)
- состояние completion 19.b сохранено в `mcp__ruflo__memory_store`
  с namespace=argument-map (384-dim embedding для semantic recall)

### Решения

**ADR-033 параллельная иерархия `question_sources`** - выбрана Опция B
(mirror schema + parallel domain/repo/service/controller) vs Опция A
(generic citations table с polymorphic FK - premature generalization
для 2 entities, breaking миграция) vs Опция C (generic via Java
inheritance - middle-tier complexity без gain). Trade-off: ~200 LOC
дублированного кода за zero risk + proof of platform reuse. Revisit
к Option A или C при 3-м entity type (answers, comments).

**Q&A только positional citation, без freeform** - схема question_
sources поддерживает LEGACY mode, controller не имеет attach endpoint.
Если появится UX-кейс «freeform URL/article attach для question» -
добавить отдельный POST endpoint.

### Проблемы (открытые, не блокеры)

- **12 pre-existing frontend test failures** (3 файла) - регрессия
  между Сессией 35 (143/143 pass) и Сессией 36. Подтверждено
  stash-проверкой что failures есть на чистом master без моих
  изменений. Файлы: TopicGraphPage.test.tsx (4 теста), TopicListPage
  .test.tsx (3), NodeDetailsPanel.test.tsx (5). Подозрение -
  waitForApi 200ms timeout перестал хватать с v2 design tokens
  компонентов. Подробности и план fix - в backlog
- **Шрифт title книг в BookListPage** - запрос Абдулы с reference
  screenshot. Записано в backlog для будущего этапа

### Verify в конце сессии

- Backend `./mvnw verify`: **455/455 tests pass, BUILD SUCCESS**
- Frontend `npx tsc --noEmit`: clean
- Frontend `npm run lint`: 0 errors, 1 pre-existing warning (не моя)
- Frontend `npm run build`: SUCCESS 2.58s
- Frontend `npm test`: 131/143 (12 pre-existing - см. выше)
- Backend smoke через curl: POST question → POST citation TEXT mode →
  GET sources возвращает structured citation (тафсир Ибн Касира с
  authority/book/muhaqqiq/publisher/place/edition=1/hijri=1431/greg=1999)
- Playwright headless smoke: question detail page рендерит SourceCard
  identical как в NodeDetailsPanel - тот же chip «ИЗ БИБЛИОТЕКИ»,
  arabic quote dir=auto, метаданные раскрыты по default

### Подсессия 19.c запущена в Сессии 36

В конце Сессии 36 (по причине заполнения контекста ~60%) запущен
Agent subagent_type=coder в фоне для реализации Этапа 19.c (Answers).
Полный handoff prompt включает: миграции 29+30, domain/repo/service/
controller Answer, accept-answer flow через PATCH UpdateQuestionRequest
расширение, IT тесты, frontend QuestionDetailPage секция «Ответы»,
ADR-034, api-contract, roadmap [x].

Agent работает автономно. Результат - набор коммитов на master или
эскалация если столкнётся с блокером. Может закончить раньше окончания
Сессии 36 или продолжить в следующей сессии (ruflo autopilot включён,
maxIterations=200, timeoutMinutes=720).

### Следующий шаг (для Сессии 37)

19.a + 19.b закрыты, 19.c в работе через подсессию. Опции:

**Опция A (~0.5 сессии) - 20.e AddSourceModal расширенная.** При
sourceType=BOOK показывать 6 academic полей (мухаккик/издатель/место/
edition/год хиджры/год григориан). Reuse AutocompleteRow из
BookEditModal через shared `<AcademicMetadataFields>` компонент.
**Важно:** сложнее чем кажется - при submit нужно либо создать Book
row через `POST /api/v1/library/books` + linked Source, либо сохранить
metadata в Source.metadata JSON. ADR требуется (выбор подхода). Текущий
freeform Source.bookId=null - не позволяет SourceCard structured
citation. Рекомендую создание Book row - правильнее семантически

**Опция B (~1 сессия) - 19.c Answers.** answers table + UI add answer
+ accepted answer flag. Полная Q&A semantic. После - можно add
answer_sources аналог question_sources используя тот же pattern из
ADR-033 (ещё одна валидация platform reuse)

**Опция C (~0.5 сессии) - фикс 12 pre-existing tests.** Диагностика
регрессии TopicListPage/TopicGraphPage/NodeDetailsPanel.test.tsx
между Сессией 35 и 36. Подозрение waitForApi timeout или MSW handler
mismatch с v2 components

**Опция D (~30 мин) - шрифт title в BookListPage.** Подобрать красивый
serif (Source Serif 4 уже подключён, или новый - PT Serif/Lora/
EB Garamond/Crimson Text). Reference screenshot Абдулы 16.05

### Инфра на момент Сессии 37 entry

- Postgres :5432 healthy, миграции до 28 включительно applied
- MinIO :9000 healthy
- Backend :9090 running после restart с новым DELETE URL hierarchy
- Frontend :5173 - может потребовать `rm -rf node_modules/.vite` после
  CitationPicker props changes
- Test question `3796f633-1822-45fa-87e1-6337a603b6f1` с одной
  citation в production-БД для smoke
- Source `132d75cc-cf4e-4d24-beb3-a4859ba0b776` reused между
  node_sources (тафсир тестового узла из Сессии 32 smoke) и
  question_sources (созданный в этой сессии) - proof в БД

---

## 2026-05-16 - Сессия 35 (frontend+backend+docs) - v2 коммиты + doc-долги + 20.c parser

Подобрала uncommitted v2 design migration из Сессии 34, закрыла все
зависшие doc-долги Сессий 32-34, и сделала Этап 20.c (shamela
bibliography parser). Чисто отработавшая сессия в режиме автономии.

### Сделано

**v2 design migration (Сессия 34) разложена в 6 атомарных коммитов:**

- `feat(frontend): v2 design tokens infrastructure` - styles/tokens.css
  + index.css `@theme inline` + themeStore + ThemeEffect + index.html
  FOUC script + Manrope/Source Serif/Amiri шрифты + clsx dep
- `feat(frontend): v2 UI primitives на токенах` - 13 primitives + 2
  новых (Chip/Field) + designTokens.ts
- `feat(frontend): AppHeader + ThemeSwitch + CommandPalette + Bell/Avatar menus`
- `feat(frontend): v2 pages migration + i18n keys` - 6 страниц
  (BookList sort+import, TopicList, CreateTopic two-column, TopicGraph
  AppHeader, BookReader, AdminShamela activity log) + ~25 ключей RU/AR
- `refactor(frontend): graph v2 migration` - 20 файлов графа
  (NodeCard rounded-md, EdgeDetailsPanel solid bg-edge-*-bg,
  CompactMiniMap CSS vars, legend удалена, modals/forms tokens)
- `refactor(frontend): source card + reader на v2 токенах` - 18 файлов
  (QuoteBlock bg-paper, PrimaryButton outline, Collapsible default open,
  reader compose)

**Doc-долги Сессий 32-34 закрыты (один коммит `docs:`):**

- **api-contract.md:** NodeSourceResponse.id (UUID), DELETE
  `/sources/{nodeSourceId}` (breaking path), строка в "Историю изменений"
- **ADR-029 FK variant A** - surrogate UUID id для node_sources, mig 25,
  multi-citation per pair
- **ADR-030 i18n архитектура** - manual dictionary с DictKey union
  literal, useCallback стабилизация, alternatives (i18next отвергнут)
- **ADR-031 v2 design system tokens** - двухслойная token-арх
  (semantics tokens.css + @theme inline bridge), runtime темизация
- **gotchas +5:** React StrictMode duplicate API requests (Сессия 32),
  closure-функции useCallback (Сессия 33), batch-Edit cyrillic verify-grep
  (Сессия 33), Tailwind v4 @theme inline обязателен (Сессия 34),
  mass-replace sed grep audit (Сессия 34)

**Этап 20.c shamela bibliography parser:**

- `ShamelaBibliographyParser` - regex по 5 markers
  (المحقق / حققه ووضع حواشيه / تحقيق / الناشر / الطبعة / عام النشر)
- Heuristic split publisher по " - " для publicationPlace; explicit
  `مكان النشر:` имеет приоритет
- Arabic ordinal dictionary (الأولى=1, ..., العاشرة=10) + fallback на
  arabic-indic digits
- Years: arabic-indic digits перед `هـ` (хиджра) и `م` (григориан)
- `ParsedBibliography` record - все 6 полей nullable, конс. парсер
- Интегрирован в `ShamelaToLibraryMapper.mapBook` через
  `findOrCreate` в `MuhaqqiqRepository`/`PublisherRepository`/
  `PublicationPlaceRepository`. Заменил 6×null в Book record на FK+years
- 11 unit-тестов с реальными фикстурами из dev-БД

### Решения

- **`@theme inline` зафиксирован в ADR-031** как required для v4
  runtime темизации - без него `var()` раскрывается статически на этапе
  сборки. Уровень gotcha + ADR
- **Surrogate UUID id для node_sources** (ADR-029) обоснован vs
  composite PK с positional fields (Option B, Postgres NULL-handling
  хрупкий) и vs отдельная таблица node_source_positions (Option C,
  over-engineering для 1:1)
- **Parser консервативен** - вместо смелого парсинга всех вариантов
  shamela bibliography возвращает null для missing markers. Admin
  BookEditModal (20.d) дополучит UI для ручной правки
- **Bulk-backfill endpoint** для existing books отложен на 20.c
  follow-up - требует `BookRepository.update` + admin endpoint, не
  ложится естественно в один pass с parser

### Проблемы (открытые)

- **Bulk-backfill 20.c follow-up** - 3 dev-книги в БД остались с null
  FK (импорт до 20.c). Чтобы заполнить - либо новый endpoint
  `POST /api/v1/admin/shamela/backfill-bibliography`, либо manual SQL.
  Endpoint - один сервис-метод + один controller-метод + BookRepository
  partial update SQL. ~30 минут работы
- **Heuristic split publisher** по " - " - может слипнуть имя
  издательства с пробелами на конце. Acceptable для MVP, при появлении
  false positive (publisher усечён) - tighten condition (требовать
  пробел или арабскую букву после `-` начала)
- **@tabler/icons-react все еще blocked** через корпоративный proxy
  (ETIMEDOUT). Workaround `svg.lucide:not([stroke-width])` остается

### Дополнительно сделано (после handoff Сессии 35)

В той же сессии, после первого handoff коммита, продолжил работу:

- **Cmd+K → Alt+K migration** (коммиты `3a39ad8`, `cefa404`, `17353fa`,
  `dd4b1ca`). Палитра не открывалась с Cmd+K в Chrome на Win/Linux -
  native accelerator перехватывал. Listener поднят из Header.tsx в
  App.tsx через `paletteStore` (zustand) - работает на любом route
  включая graph page. Capture phase + stopPropagation не освободили
  Ctrl+K. Финальное решение - Alt+K. Gotcha в `gotchas.md` со списком
  зарезервированных/свободных Chrome accelerators
- **20.c follow-up bulk-backfill endpoint** (`59e8414`).
  `POST /api/v1/admin/shamela/backfill-bibliography` через
  `ShamelaBibliographyBackfillService`. Critical parser fix - real БД
  хранит CR character (chr(13)) как separator, не literal `\r` (2 char).
  Regex расширен на alternation. Smoke: 3/3 dev-книг с заполненными FK
  (тафсир получил мухаккика+publisher+place+ed=1+hijri=1431+greg=1999,
  все аналогично)
- **Этап 20.d Admin BookEditModal** (`ea42007` backend + `9c3467d`
  frontend). Backend: `PATCH /api/v1/library/books/{id}` через
  `UpdateBookRequest` (PATCH-семантика null=no change, ""=clear,
  non-empty=findOrCreate). 3 autocomplete endpoints muhaqqiqs/publishers/
  publication-places (ILIKE substring search). Frontend: BookEditModal
  с 6 Field primitive + 3 inline autocomplete (debounce 250ms +
  AbortController cancel). Pencil icon в Card.Cover на /books, кнопка
  «Перечитать metadata» в /admin/shamela. Playwright smoke: тафсир
  Ибн Касира prefilled 6 полей. Verify: backend 425/425, frontend
  143/143, lint clean
- **Этап 19.a Q&A foundation** (`ba5cf8c` backend + `8c75605` frontend
  + ADR-032 в decisions.md). Migration 26 questions table + Question
  domain/repo/service/controller (CRUD под /api/v1/questions). 3
  frontend pages (QuestionListPage с status filter + search /
  CreateQuestionPage с Field + counters / QuestionDetailPage с status
  switcher + delete confirm). Header nav «Q&A» enabled. 29 i18n keys
  RU/AR. Playwright headless подтвердил полный UI CRUD flow.
  Backend verify 425/425

### Следующий шаг (для Сессии 36)

Этапы 20 и 19.a закрыты. Опции на выбор:

**Опция A (рекомендую) - Этап 19.b Q&A source attach** (~1 сессия) -
**настоящая валидация platform pivot**. Migration 27 `question_sources`
(аналог `node_sources` через ADR-027/029) + reuse CitationPicker в
QuestionDetailPage. Если работает - архитектура подтверждена.

**Опция B - Этап 20.e AddSourceModal расширенная** (~0.5 сессии).
При sourceType=BOOK дополнительные academic поля. Reuse autocomplete
через shared `<AcademicMetadataFields>` компонент.

**Опция C - Этап 19.c Answers** (~1 сессия). Answers table + UI add
answer + accepted answer flag.

**Опция D - cleanup**: SourceSearchForm/SourceCreateForm i18n placeholder,
ESLint rule на cyrillic JSX literals, @tabler/icons retry.

Приоритет 2 (альтернатива):

- **Этап 19 Q&A приложение** - валидация платформенной архитектуры
  через второе приложение использующее common Source/Book stack
- **@tabler/icons-react retry** когда сеть позволит → создать
  `src/shared/icons.ts` shim + sed-replace `lucide-react` → `@/shared/icons`

### Инфра на момент Сессии 36 entry

- Postgres :5432 healthy (4+ days uptime), миграции до 25 включительно
- MinIO :9000 healthy
- Backend :9090 + JDWP :5005 (рестарт нужен после parser changes -
  старый процесс на :9090 не подхватит новый classpath)
- Frontend :5173 - после restart Vite должен подобрать v2 changes
  (но кэш .vite может потребовать `rm -rf node_modules/.vite`)
- Все 425 backend tests pass, frontend 143 tests pass, lint 0 errors

---

## 2026-05-15 - Сессия 34 (frontend) - v2 design migration

Большая UI-инициатива от Абдулы: миграция всей фронт-страницы на v2 design
system из `frontend/design-reference/v2/project/handoff/` (5 markdown'ов
с tokens/components/pages + готовый tsx-набор). Этап 20.c parser **не
начат**, перенесён на Сессию 35.

### Архитектурные артефакты (durable)

- **`frontend/src/styles/tokens.css`** (новый) - семантический слой
  CSS variables: ink-scale (0-900), accent-50/100/500/600/700,
  ok/warn/err по 3 ступени, type-abstract / type-empirical /
  edge-supports/refutes/qualifies/responds для графа, surface aliases
  (`--c-bg` / `--c-bg-elevated` / `--c-bg-sunken` / `--c-border` /
  `--c-text` / `--c-text-muted`). `[data-theme="dark"]` block с
  инвертированной ink-scale + ярче accent для контраста на тёмном
- **`src/index.css`** - `@theme inline` bridge: `--color-ink-900 →
  var(--c-ink-900)` чтобы Tailwind v4 автоматически генерировал
  `bg-ink-900`/`text-ink-900`/`border-ink-900` utility-классы. `inline`
  keyword обязателен - без него v4 раскрывает `var()` статически и
  тема не переключается
- **`src/shared/stores/themeStore.ts`** + **`src/shared/components/
  ThemeEffect.tsx`** - zustand store с persist в `localStorage.app.theme`,
  prefers-color-scheme fallback. ThemeEffect синхронизирует
  `<html data-theme="dark">` после mount
- **FOUC inline script** в `index.html` - читает localStorage до React
  mount, ставит `data-theme` сразу - иначе flash light→dark при первой
  загрузке
- **Шрифты:** Manrope (UI) + Source Serif 4 (serif для prose) + Amiri
  (arabic preferred) + Noto Naskh fallback - подключены через Google
  Fonts preconnect
- **Primitives** - `Button` (6 вариантов × 4 размера, backwards-compat),
  `Card` + namespace `Card.Cover/Body/Eyebrow/Title/Meta`, новые `Chip`
  и `Field` (с `Input`/`Textarea`/`Meta`), обновлены `Modal` / `IconButton`
  / `Badge` / `Kbd` / `Toaster` / `Select` / `ContextMenu` / `FormModal`
  на токены
- **Layout** - `Header` (бренд ﷽ + nav + поиск ⌘K + LocaleSwitch +
  ThemeSwitch + BellMenu + AvatarMenu), `CommandPalette` (Cmd+K,
  фильтр + ↑↓Enter), `BellMenu` + `AvatarMenu` (placeholder dropdown'ы
  до бэка)
- **`designTokens.ts` рефакторен** на новые tokens-классы. Edge stroke
  теперь `var(--c-edge-*)` чтобы React Flow переключал цвета в dark

### Сделано

В рабочем дереве (НЕ закоммичено в этой сессии - 1 большой changeset):

- v2 tokens infrastructure (4 новых файла, переписан index.css)
- 9 primitives обновлены / 2 новых (Chip, Field)
- 4 layout-компонента: Header переписан, CommandPalette/BellMenu/
  AvatarMenu/ThemeSwitch новые
- 6 страниц мигрированы: BookListPage (+Импорт Shamela кнопка +
  сортировка dropdown + 5-col grid 2xl), TopicListPage (Card.Body +
  themeable mini-graph), CreateTopicPage (two-column form + Field
  primitive + sticky aside paper-aside), TopicGraphPage (+`<Header />`
  сверху + secondary crumb), BookReaderPage (bg-bg), AdminShamelaPage
  (status pill + Activity Log placeholder с OK/WARN/ERR badges)
- 18+ файлов графа: NodeCard rounded-md + shadow tokens + ring-2 для
  selected, NodeDetailsPanel убран gradient, EdgeDetailsPanel gradient
  → solid `bg-edge-*-bg`, CompactMiniMap на `var(--c-*)` для темизации,
  shift end-[416px] при detail panel, удалена status legend (anti-pattern)
- Source card: цитата на `bg-paper` (тёплый кремовый, не голубой),
  "Перейти к источнику" - **outline** (не filled primary), metadata
  раскрыта по default
- NodeCitationsSection: кнопки "Привести источник / Свободный" из
  side-by-side в vertical stack (текст обрезался)
- `clsx@^2.1.1` добавлен в package.json как explicit dependency
- Mass-replace через sed: 4 волны (borders / bg+text / accent+focus /
  semantic colors) по всему src
- i18n: ~25 новых ключей RU/AR (palette.*, notifications.empty,
  avatar.*, admin.status_*, admin.activity_log, admin.sync_done,
  book.list.sort_*/import_from_shamela)

### Code review через Agent + 20 issues закрыты

Subagent code review нашёл 3 Critical, 10 Important, 7 Minor. **Всё
зафикшено в той же сессии:**

- **C1** ESLint `react-hooks/set-state-in-effect` в CommandPalette →
  container/body split с remount при `open` flip (key-trick idiom)
- **C2** CreateTopicPage tests fail из-за Field `*` marker меняющего
  accessible name → `aria-hidden="true"` + regex `getByLabelText(/^Название/)`
- **C3** CompactMiniMap hex literals (`#c4b5fd` etc) → `var(--c-type-*)`,
  `var(--c-{status}-500)`, `var(--c-edge-*)`, `var(--c-accent-500)`
- **I4** EdgeDetailsPanel `bg-gradient-to-b from-emerald-50/70` →
  solid `bg-edge-*-bg` через `headerBgFor()`
- **I5** sed-leftovers (ring-indigo-400, divide-slate-100, rose,
  shadow-xl, accent-indigo-600) - cleaned по 7 файлам
- **I7-I8** spacing-scale + text-[Xpx] violations (191 occurrences) → 0
- **I9** Modal `"Закрыть"` → `t('common.close')`
- **I10** AdminShamela toast strings → i18n keys с `{placeholder}` interpolation
- **I11** 16 `exhaustive-deps` warnings → 0 (добавлен `t` в deps,
  он stable per memory `feedback_stable_hooks_for_deps`)
- **I12** NodeCard `rounded-xl` → `rounded-md` + `shadow-sh*` tokens
- **I13** Tabler stroke-width override через `:not([stroke-width])` -
  не перебивает explicit `strokeWidth={2.5}` в edges/labels
- **M14-M17** Amiri preload, FOUC inline script, fixed `bg-black/50`
  backdrop (не `ink-900/40` который инвертируется в dark)

### Решения

- **Tailwind v4 `@theme inline`** обязателен для runtime темизации -
  без `inline` v4 раскрывает `var()` статически при сборке и
  `[data-theme="dark"]` не работает. Знание для будущих изменений
  токенов (gotcha-кандидат)
- **NODE_TYPE_TOKENS унификация** - QUESTION/CLAIM/ARGUMENT все
  получили `type-abstract-bg/fg`, EVIDENCE - `type-empirical-*`.
  Per handoff/02-tokens.md это semantic группировка (концепты vs
  наблюдения), не per-тип палитра как в v1
- **MiniMap shift через prop `detailOpen`** вместо абсолютной
  позиции по центру: minimap всегда snap к inline-end, сдвигается на
  416px (panel 400 + gap 16) когда виден detail panel
- **CommandPalette container/body split** - state живёт в body
  который unmount'ится при `open=false`, естественный reset через
  remount. Альтернатива (useEffect + setQuery) ловит ESLint
- **PrimaryButton "Перейти к источнику" outline** (не filled) per
  референс v3 - primary CTA в detail panel это уже статусные кнопки
  узла, переход в библиотеку - secondary
- **Tabler icons workaround** - npm install падает через корпоративный
  proxy (ETIMEDOUT). Глобальный CSS `svg.lucide:not([stroke-width])
  { stroke-width: 1.5 }` визуально приближает lucide к Tabler без
  установки. Когда сеть позволит - заменить через shim-модуль

### Проблемы (открытые)

- **@tabler/icons-react не установлен** - `npm install` падает
  ETIMEDOUT через proxy `66.151.42.7:64526`. curl reachable, но
  npm streaming не доходит. Сейчас визуальное приближение через
  stroke-width override. Полная замена ждёт когда сеть позволит
- **Backlog от Сессии 33 НЕ закрыт** - api-contract.md (DELETE path,
  NodeSourceResponse.id, BookDetailResponse nested), ADR-029
  (FK variant A), ADR-030 (i18n арх.), gotchas (StrictMode duplicate
  requests, useT стабилизация, batch-Edit cyrillic) - оставлено
  на Сессию 35 потому что эта была чисто UI работа
- **Этап 20.c shamela parser НЕ начат** - перенесён на Сессию 35

### Следующий шаг (для Сессии 35)

1. **Закоммитить v2 миграцию** - changeset из этой сессии в working
   tree, около 60 файлов. Разбить на атомарные коммиты по теме:
   `feat(frontend): v2 design tokens + theme switching`,
   `refactor(frontend): mass replace v1 → v2 palette`,
   `feat(frontend): CommandPalette + BellMenu + AvatarMenu`,
   `feat(frontend): BookList sort + import button`,
   `feat(frontend): AdminShamela activity log`,
   `fix(frontend): code-review follow-up Сессии 34`
2. **Doc-долги от Сессии 33** - api-contract.md update, ADR-029/030,
   gotchas (3 шт), плюс новая gotcha про `@theme inline` v4
3. **Этап 20.c shamela bibliography parser** - оригинальный приоритет,
   план в SESSION_START_PROMPT (раздел Текущий приоритет в Сессии 34)
4. **@tabler/icons-react** - попробовать установить заново когда
   proxy успокоится. Если сработает - shim-файл + sed-replace
   `from 'lucide-react'` → `from '@/shared/icons'`

---

## 2026-05-15 - Сессия 33 (frontend) - полная RTL/i18n локализация

Пользователь дал детальный план фикса RTL/LTR + bidi для двуязычного
интерфейса (RU/AR): 10 шагов от единого модуля определения арабского
скрипта до документации. По ходу сессии расширилось до полной i18n-
локализации - все хардкод-русские строки в видимом UI заменены на
ключи из словаря.

### Архитектурные артефакты (durable)

- **`frontend/src/shared/i18n/`** - расширен новыми primitives:
  - `script.ts` - единый `hasArabicScript` (Unicode blocks Arabic/
    Supplement/Extended-A/Presentation Forms). Inline regex'ы
    `/[؀-ۿ]/` запрещены - заменены на импорт
  - `useFormatDate.ts` - локаль-aware Intl.DateTimeFormat (ru-RU/ar)
    с стилями `full`/`short`. Стабилен через `useCallback([locale])`
  - `useNumberFormat.ts` - локаль-aware Intl.NumberFormat
  - `dictionary.ts` - расширен с ~22 до ~280 ключей в 15+
    namespace'ах. DictKey union literal type даёт compile-time safety

- **`frontend/docs/i18n-guide.md`** - canonical reference ~280 строк
  для будущих сессий: 3 понятия которые нельзя путать (локаль UI /
  язык контента / направление текста), алгоритмы добавления UI/layout/
  иконки/контента, mixed-content через `<bdi>`, форматирование, что
  зеркалится / не зеркалится, чек-лист перед PR, 8 пар анти-паттернов
  ❌ vs ✅. Cross-link из `frontend/CLAUDE.md` и `coding-standards.md`

- **Token refactor**: `STATUS_TOKENS`, `NODE_TYPE_TOKENS`,
  `EDGE_TYPE_TOKENS`, `NODE_TYPE_META`, `EDGE_TYPE_META` - поле
  `label/hint: string` → `labelKey/hintKey: DictKey`. Удалён
  `NODE_TYPE_LABEL`. `getContextualEdgeLabel` → `getContextualEdgeLabelKey`.
  Один контракт «токен описывает визуал, переводы в словаре»

- **Tailwind logical classes** - все физические `ml/mr/pl/pr/left/right/
  text-left/border-l/rounded-l-*` заменены на `ms/me/ps/pe/start/end/
  text-start/border-s/rounded-s-*` во всём `src/` кроме `NodeCard.tsx`
  и `CompactMiniMap.tsx` (граф React Flow - пространственная структура)

### Сделано (~30 атомарных коммитов)

Основные группы:
- **Foundation** (`b3f724c`, `f8e1e13`, `f2ed968`, `133d484`) - модуль
  script.ts, dictionary expansion, useFormatDate/useNumberFormat
- **Token refactor** (`1a2679c`, `2e4b8f1`) - labelKey/hintKey DictKey
- **Mechanic fixes** (`0d64867` физ.классы, `bb93e2b` Header бренд,
  `3accf3a` NodeCard dir=auto+naskh, `e3f67fc` bidi-изоляция dates/IDs,
  `8e062e4` панели/тосты по локали, `0c73474` RtlRow shamela inline,
  `0a93b6f` FreeformCite dir=auto authority)
- **i18n покрытие компонентов** (`08c9dd3`, `b829426`, `b458f56`,
  `9052413`, `47ee880`, `80b795b`, `de14bdf`, `8a99e07`) - 25+
  компонентов от Header до AdminShamela
- **Hotfix** (`4a8eff5`) - useT/useFormatDate стабилизированы через
  useCallback после диагностики infinite-loop fetch в TopicGraphPage
- **Docs** (`7ef433d`, `d450277`) - i18n-guide.md + coding-standards
  раздел RTL/bidi + CLAUDE.md правила
- **Post-review cleanups** (`3581272` и др.) - после code review
  feedback (12 Important issues все закрыты)

### Code review (subagent)

Запрошен через `/superpowers:requesting-code-review` после первой
итерации (21 commit). Результат: 11 strengths, **0 Critical**, **12
Important**, 7 Minor, verdict **Ready to merge**. Все Important
закрыты в follow-up commits (~10 шт)

### Ключевые design decisions

- **Locale UI vs Content language vs Text direction** - три разных
  понятия, не смешивать. UI следует `useLocaleStore`, контент -
  `dir="auto"`, шрифт - `hasArabicScript`. Раньше было
  `book.language === 'ar' ? 'rtl' : 'ltr'` в нескольких местах -
  ломалось на «RU UI + AR книга»
- **Inline shamela формат для метаданных** (вместо infobox) - в обеих
  локалях `Label: value` на одной строке. Direction родителя зеркалит
  порядок автоматически
- **Граф React Flow не зеркалится** - canvas/позиции/minimap остаются
  LTR. Меняется только текст внутри узлов (dir=auto + font-naskh) и
  UI-панели вокруг канваса
- **FormModal как DRY-точка** - «Отмена» переведена один раз в shared
  компоненте, автоматически покрывает все формы

### Что НЕ сделано (backlog для следующих сессий)

- **Внутренние формы AddSourceModal** - SourceSearchForm/
  SourceCreateForm/AttachFields placeholder'ы захардкожены
- **ESLint pre-commit rule** на cyrillic literals в JSX
- **AR locale parameterized tests** - сейчас завязаны на default `ru`
- **Bibliography parser 20.c** - была планируемая работа Сессии 33,
  переехала в Сессию 34

### Метрики

- 30 атомарных коммитов, push в origin/master в конце сессии
- 143/143 тестов
- Build/typecheck чист, ESLint только 16 safe warnings про `t in deps`

---

## 2026-05-14 - Сессия 32 (full-stack) - 20.f LibraryCite redesign + i18n + FK variant A

После Сессии 31 (бэк 20.a-b + frontend 20.f первая итерация) пользователь
дал три feedback'а: карточка citation выглядит «двух-колоночной» (mixed
RTL/LTR), не получается создать вторую citation на ту же книгу
(`fk_error`), нужны переводимые labels с переключателем локали + ширина
header книги выровнена

### Сделано (4 функциональных коммита)

- **`72ddd0b`** `feat(frontend): SourceCard «всё к правому борту»` -
  применён дизайн D из handoff bundle Claude Design (claude.ai/design).
  12 атомов в `shared/components/citation/sourceCard/`:
  Bdi / Chip / Collapsible / FlexValue / HijriYear / Label / PrimaryButton /
  QuoteBlock / RtlRow / SourceCard / SourceCardHeader / cardShell.
  Концепция variant D: вся карточка `dir="rtl"`, всё к правому борту,
  `<bdi dir="ltr">` для cyrillic, quote `dir="auto"` (UA bidi resolve)
- **`c1a6ff1`** `feat: i18n minimal + structured BookHeader` -
  shared/i18n/ (dictionary ru/ar 22 keys, useLocaleStore zustand,
  useT hook). Backend BookDetailResponse extended с nested
  Authority/Muhaqqiq/Publisher/PublicationPlace refs (BookService
  резолвит FK). BookHeader переписан structured с RtlRow + переводимые
  labels (Автор/Тахкик/Издатель/Издание/Год)
- **`d86e010`** `feat(frontend): RU/AR locale toggle + layout fix` -
  LocaleSwitch chip в Header, localStorage persist, LocaleEffect
  синхронизирует `<html lang dir>`. Tailwind logical classes
  (ms-/me-/ps-/pe-/border-s-/text-start) автоматически mirror'ятся.
  BookHeader wrapped в Card для consistency width с PageView,
  ReaderModeSwitch (Текст/PDF) перенесён в sticky toolbar
- **`8f3b2c9`** `feat: FK variant A` - миграция 25 заменяет
  `node_sources_pkey (node_id, source_id)` на surrogate `id UUID PK`.
  Backward-compat aliases `findByIds/delete` в repository для legacy
  flow. Now user может прицепить N разных фрагментов одной книги к
  одному узлу - то что нужно для бахс анализа. DELETE endpoint
  изменился на `/sources/{nodeSourceId}` (breaking change path param)

Bidi quirks fix'ы (`3588d62`, `bcfc18f`) - ушли в pre-redesign, потом
полностью заменены SourceCard handoff'ом

### Решения

- **Variant D «всё к правому борту»** rejected my previous подход с
  один-direction-на-строку. Все рядки в RTL контейнере, latin/cyrillic
  через `<bdi dir="ltr">` сидят справа но читаются LTR. Чище structure,
  работает в обе локали без переделок
- **Ручной i18n dictionary** (без i18next/react-intl) - 22 keys, ручной
  type-safe через DictKey union. Простой zustand store + LocaleEffect.
  Когда словарь вырастет за 200+ keys - можно migrate на i18next без
  изменения вызывающего кода (useT hook сохраняется)
- **FK variant A vs B vs C** - выбран A (surrogate id PK). B (composite
  с positional fields) overkill для текущей user feedback. C (frontend
  replace dialog) теряет данные. A даёт реальный multi-citation use case
- **LTR wrapper для publisher · place pair** - в RTL row flex reverses
  order. Wrap pair в `dir="ltr"` chip-span сохраняет visual «Дар Тайба ·
  Эр-Рияд» вместо реверсного «Эр-Рияд · Дар Тайба»
- **Shamela parser НЕ извлекает academic fields** - проверено: mapper
  сохраняет raw `bibliography` text в `description`, regex / parser
  нужно создать (Этап 20.c)

### Проблемы

- **Duplicate API requests** в dev - React StrictMode двойной mount.
  Tried: AbortController + onCountsChange via useRef + ref guard.
  Ref guard сломал re-mount (state lost). Откат к AbortController +
  принятие 2 request в dev tab (production = 1 request, by-design React)
- 27 call sites `new Book(...)` + new Authority(...) - rewrite в 18+8
  файлах. Возможно future refactor на builder pattern
- DELETE path break: `/sources/{sourceId}` → `/sources/{nodeSourceId}`.
  Обновлены NodeDetailsPanel.test.tsx + NodeSourceControllerIT.
  api-contract.md не обновлён - **TODO для следующей сессии**

### Следующий шаг

**Сессия 33 - этап 20.c Shamela bibliography parser**

В БД (по результатам `SELECT description FROM lib_books WHERE
description IS NOT NULL LIMIT 5`) можно увидеть форматы. Plan:

1. Создать `ShamelaBibliographyParser` в
   `backend/src/main/java/ru/basnukaev/argumentmap/library/shamela/service/`
   - regex для каждого поля (мухаккик `تحقيق:`, publisher `الناشر:` /
     `دار`, place, edition `الطبعة:`, year hijri `هـ` / gregorian `م`)
   - Return record `ParsedBibliography(muhaqqiqName, publisherName,
     placeName, editionNumber, yearHijri, yearGregorian)` - все nullable
2. Интегрировать в `ShamelaToLibraryMapper.mapBook` - после resolving
   authority вызвать parser, для каждого non-null field вызвать
   `*Repository.findOrCreate(name)` и заполнить FK на book
3. Unit-тесты на ~10 реальных bibliography строк (extract from
   production-БД через `psql`)
4. Endpoint backfill в `ShamelaAdminController`:
   `POST /api/v1/admin/shamela/backfill-academic-metadata` - перебор
   всех замапленных книг, parser + UPDATE academic fields
5. После backfill - smoke на `/books/{id}` любой shamela-imported книги:
   BookHeader должен показать structured metadata

**Доделки следующей сессии (низкоприоритетные):**

- `api-contract.md` update: NodeSourceResponse получил `id`, DELETE
  path меняется на `/sources/{nodeSourceId}`, BookDetailResponse +
  nested refs. Добавить historic line про migration 25 FK variant A
- ADR-029 для FK variant A (decisional - surrogate vs composite PK)
- ADR-030 для i18n архитектуры (минимальный manual dictionary vs
  i18next - обоснование выбора)
- gotcha: «React StrictMode duplicate requests in dev» - by-design,
  AbortController не fix'ит (request уже на network к моменту cleanup)
- `roadmap.md` обновить - проставить `[x]` на 20.f + FK fix добавить
  как Этап 23 (или подэтап существующего)

### Инфраструктура (Сессия 33 entry)

- Postgres :5432, миграции до 25 включительно applied
- Backend :9090 + JDWP :5005 running
- Frontend :5173 running с HMR + i18n locale persist в localStorage
- Smoke: book `02bcfa43-...` имеет filled academic data
  (мухаккик/publisher/place/edition/years), `/books/{id}` показывает
  structured BookHeader, `/topics/a6617d11-...` citation card работает
- 425/425 backend IT, 143/143 frontend tests pass

---

## 2026-05-14 - Сессия 31 (backend) - Этап 20.a-b academic citation metadata ЗАКРЫТ

Реализован ADR-028 - расширение схемы для бахс-grade academic citation.
Нормализованный middle path: справочники для high-reuse полей +
расширение `authorities` для академического имени автора + per-book
скаляры

### Сделано

- `f3338b3` `docs: design spec ADR-028`
- `e6450ae` `docs: implementation plan ADR-028`
- `8033fcb` миграция 24: ALTER `authorities` + `full_name` +
  `death_year_hijri`, CREATE `lib_publishers` / `lib_publication_places` /
  `lib_muhaqqiqs` (UNIQUE name), ALTER `lib_books` + 3 FK + 3 scalars,
  3 CHECK + 4 BTREE индекса
- `48959a5` 3 справочника Publisher / PublicationPlace / Muhaqqiq -
  record + JDBC repository с `findOrCreate`, 18 IT
- `01b7a13` Authority + `fullName` / `deathYearHijri`. Поправлены call
  sites `new Authority(...)` в 8 файлах. 3 новых IT (round-trip + 2x
  CHECK violation)
- `42bbad1` Book + 6 полей. Поправлены call sites `new Book(...)` в 18
  файлах. 4 новых IT
- `808be8e` `CitationDetail` record (27 полей) + 9 LEFT JOIN в
  `NodeSourceRepository.findByNodeIdWithLocation`. 5 новых IT
- `7cdfc78` `CitationResponse` + 8 nested ref DTO. `NodeSourceResponse`
  рефакторен (плоские поля → nested citation). `DtoMappers.toCitationResponse`
  + 8 helpers
- `14a5c12` ADR-028 + doc updates (architecture / api-contract /
  glossary / roadmap)

`./mvnw verify`: 425/425 IT pass (+56 vs Сессии 30)

### Решения

- Option A (плоские поля) - rejected: typo-дубли + поиск невозможен
- Option B (1:N book_editions) - rejected: каскад изменений overkill
  для shamela one-edition-per-book. Future migration path сохранён
- Option C (JSONB academic_metadata) - rejected: нет query-able
  индексов, type unsafe
- Выбран middle path - **справочники + расширение Authority + per-book
  скаляры**
- Structured `CitationDetail` вместо string concat - решает слипание
  арабского с латинскими/кириллическими частями. Frontend рендерит
  каждое поле блоком

### Проблемы

- 27 call sites `new Book(...)` и 17 `new Authority(...)` - rewrite
  в 8+18 файлах. Возможен будущий рефактор на builder pattern
- `printed_page` / `part` в `lib_pages` оказались **TEXT** (могут быть
  римскими цифрами / арабскими буквами). `CitationDetail.regionPrintedPage`
  изначально planned Integer → поправлен на String

### Сделано (продолжение - 20.f frontend)

Сессия расширена, 20.f закрыт в той же сессии:

- `23d738d` `feat(frontend): этап 20.f - LibraryCite блочный рендер`
  - `npm run generate-api` обновил types.ts с nested `citation`
  - Backend: добавлено опциональное поле `legacySnapshot` в
    `NodeSourceResponse` (восстановление legacy snapshot для LEGACY
    mode без отката всего рефактора)
  - `NodeCitationsSection.tsx` `LibraryCite` полностью переписан:
    Author / Book title / Muhaqqiq / Publisher · Place · Edition /
    Years / Location / Quote / Deep link - каждый conditional блок
    со своим dir / font / стилем
  - `buildDeepLink` на nested `citation.book.id` / `location.pageId`
    / `pdf.fileId` вместо плоских полей
  - FreeformCite использует `link.legacySnapshot` вместо удалённого
    `link.location`
  - 143/143 frontend tests pass, 40/40 backend controller IT pass,
    bundle 327kB / gzip 103kB (без изменения)
- `bcfc18f` `fix(frontend): 20.f - bidi RTL/LTR для кириллических labels`
  - После playwright smoke увидели bidi-quirk: кириллические labels
    («тахкик:», «(т.774») flip'ались поверх arabic spans
  - Wrap strategy: container divs в `dir="ltr"`, arabic spans inline
    в `dir="rtl"` с unicode-bidi: isolate для location parts

**Playwright smoke** на `/topics/a6617d11.../`, node «Сахаба и саляф не
праздновали Мавлид» с pre-fill через SQL UPDATE (мухаккик السلامة,
publisher Дар Тайба, place Эр-Рияд, edition 2, годы 1420/1999, author
fullName + death_year_hijri 774). Все 15 блоков визуально присутствуют
и читабельны. Screenshot в `/tmp/librarycite-3-card.png`

### Следующий шаг (для Сессии 32)

Оставшиеся подэтапы Этапа 20:

- **20.c** Shamela bibliography parser - regex extraction мухаккика /
  publisher / edition / year из raw `lib_books.description` (там
  лежит bibliography из shamela). Использует `*Repository.findOrCreate(name)`
  для upsert справочников. ~0.5 сессии
- **20.d** Admin BookEditModal - frontend UI для ручного дозаполнения
  academic fields после импорта (когда parser не справился). Search +
  autocomplete по существующим публишерам/местам. ~1 сессия
- **20.e** AddSourceModal расширенная форма - при manual entry для
  sourceType=BOOK запросить полные поля. ~0.5 сессии

**Minor visual polish** для будущего: bidi ordering author name + year
в Author block ещё не идеален (год слева от имени). Low ROI - функционально
работает, читается, оставляю на следующий polish-pass

---

## 2026-05-14 - Сессия 30 (frontend) - user-feedback fixes + 18.h.B1+C1 design polish

Открыта после ручного browser-теста после Сессии 29. Три feedback
пункта, все закрыты

### Сделано

- `5fc87d1` «Цитаты» → «Опора» (مُسْتَنَدٌ - то на что опирается
  тезис), иконка Quote → Anchor. Backend: убран `«, строки X-Y»` из
  computed location SQL JOIN. Display теперь `Т.X стр.Y`
- `ced7e79` 18.h.B1+C1: `CitationsList` разделён на `LibraryCite`
  (3px indigo bar + «Из библиотеки» badge + Перейти к источнику)
  vs `FreeformCite` (slate bg + «Свободная» badge + AlertCircle для
  URL без citation). `NodeDetailsPanel` header получил inline meta-row
  `⚓ N опора (📖 lib · ❝ free)`. `NodeCitationsSection` переключён
  с lazy `onFirstOpen` на eager-load on mount + `onCountsChange`
  callback в parent
- `6d9b6d8` убран `Math.random()` из render (react-hooks/no-impure-function-during-render)
- `22f1be4` иконки в опоре увеличены до читаемого размера 13-14px

### Решения

- **«Опора» вместо «Источники»** - семантический эквивалент
  исламского концепта `мустанад`/`далиль`, не конфликтует с domain
  term `Source`
- **Range убран из display location** - бесполезен в academic
  citation. Остаётся только для технического highlight через
  `?highlight=` query param
- **Lift state up** через `onCountsChange` callback вместо backend
  расширения - state colocation, наружу только агрегаты
- **18.h.A1 (NodeCard footer chips) deferred** - duplicate данные с
  header meta-row, low ROI

### Следующий шаг

Сессия 31 - этап **20.a Academic citation metadata** (ADR-028).
Закрыто в Сессии 31, см. запись выше
