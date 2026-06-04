# Codebase audit 2026-05-11

Полный heavy audit кодовой базы argument-map после 24 сессий Claude Code.
Производный документ для Cleanup Marathon (Phase 1-5).

**Spec:** `docs/specs/2026-05-11-codebase-cleanup-marathon-design.md`
**Plan:** `docs/plans/2026-05-11-codebase-cleanup-marathon.md`

## Метрики

| Метрика | Значение |
|---------|----------|
| Backend Java main | 141 файл, ~7400 LOC |
| Backend Java test | 47 файлов, ~7800 LOC |
| Frontend TS/TSX | 55 файлов, ~11200 LOC (включая 1518 LOC автоген types.ts) |
| Frontend tests | 16 файлов, ~2200 LOC |
| Docs/ markdown | 13 файлов, ~11200 LOC (включая 4835 LOC progress.md) |
| IT / Unit ratio backend | ~87% IT / ~13% Unit |
| Findings total | **46** (B:7 + F:18 + T:11 + D:10) |
| High severity | 10 |
| Medium severity | 18 |
| Low / info | 18 |

## Executive summary - Top-15 priority findings

| Rank | ID | Severity | Title | Phase |
|------|-----|----------|-------|-------|
| 1 | F-01 | high | TopicGraphPage 1161 LOC - разнести на 4+ subcomponents | 2 |
| 2 | F-02 | high | BookReaderPage 714 LOC - разнести на 4 subcomponents | 2 |
| 3 | F-09 | high | Error handling pattern дублирован в 4 модалках | 3 |
| 4 | F-10 | high | AsyncState pattern (loading/success/error) в 8 местах | 3 |
| 5 | F-13 | high | 5 eslint-disable react-hooks/exhaustive-deps в TopicGraphPage | 2 (вместе с F-01) |
| 6 | B-01 | high | ShamelaToLibraryMapper 413 LOC - 3 разные обязанности | 1 |
| 7 | B-02 | high | ShamelaImportService - 11 зависимостей в конструкторе | 1 |
| 8 | T-01 | high | Oversized tests 300+ LOC (ShamelaImportServiceIT, NodeDetailsPanel.test) | 1 |
| 9 | T-04 | high | 30+ waitFor() без timeout - flaky tests | 3 |
| 10 | T-06 | high | ShamelaAdminControllerIT over-mocks 2 сервиса | 3 |
| 11 | F-03 | medium | NodeDetailsPanel 613 LOC - 6 секций без границ | 2 |
| 12 | F-04 | medium | AddSourceModal 550 LOC - 2 режима + create form | 2 |
| 13 | B-03 | medium | Shamela DAO дубли setNullable*/getNullable*/sumAffected | 1 |
| 14 | B-04 | medium | DTO suffix несоответствие (Summary vs Response vs SearchResult) | 4 |
| 15 | F-07 | medium | UUID/ID naming в DTO/props/routes не унифицирован | 4 |

## Поправки к findings агентов

При сведении внесены коррекции:

- **B-06** (Russian comments): агент пометил severity=low. Project ведётся
  только на русском (см. memory `user_role`). Понижено до information-only.
  Russian comments в production коде **разрешены и сохраняются**.
- **F-08** (component naming): уже соблюдается (`*Page`). Не требует
  действий, только документирование. Перенесено в Phase 5 (документация).
- **T-09** (12 UI компонентов без тестов): out of scope marathon'а -
  это feature work (добавление тестов), не cleanup. Помечен Phase=NONE.
- **T-10** (ETL coverage adequate): out of scope, только документация.
- **F-16** (PageDetail intersection type): уже резолвится автоматически
  после следующего `npm run generate-api` - сам собой исчезнет. Phase=NONE.
- **F-18** (Vite dev fallback): уже зафиксирован коммитом 0b4bf86 -
  закрыт. Помечен resolved.

---

## Backend findings (B)

### Boundary

#### B-01 [high] [boundary, srp] ShamelaToLibraryMapper - 3 обязанности

**Файл:** `backend/src/main/java/ru/basnukaev/argumentmap/library/shamela/service/ShamelaToLibraryMapper.java:131-169, 261-309, 332-367` (413 LOC)

**Проблема:** Класс выполняет 3 функции - `mapBook()` (131-169), `mapChapters()` BFS-обход (261-309), `mapPages()` с дедупликацией (332-367).

**Почему важно:** Изменение логики маппинга глав/страниц требует работы в большом классе. Тесты становятся integration-style, сложно reuse'ить отдельные части.

**Действие:** Разнести на `ShamelaBookMapper` / `ShamelaChapterMapper` / `ShamelaPageMapper`. Оставить `ShamelaToLibraryMapper` как orchestrator (~80 LOC).

**Effort:** M | **Phase:** 1 | **Related:** B-02

---

#### B-02 [high] [boundary, dependencies, srp] ShamelaImportService - 11 зависимостей

**Файл:** `backend/src/main/java/ru/basnukaev/argumentmap/library/shamela/service/ShamelaImportService.java:71-81` (252 LOC)

**Проблема:** Конструктор принимает 11 параметров (ApiClient, Extractor, MasterReader, BookReader, 5 DAOs, SyncStateDao, Properties). Класс координирует слишком много.

**Почему важно:** 11 dependencies = 11 reasons to change. Сложно мокать в тестах.

**Действие:** Разнести на `ShamelaMasterSyncService` (syncMaster) и `ShamelaBookImportService` (importBook). Либо ввести `ShamelaDaoFacade` для группировки DAOs.

**Effort:** M | **Phase:** 1 | **Related:** B-01

---

#### B-07 [low] [boundary, structure] web/dto пакет на границе плотности (16 классов)

**Файл:** `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/` (16 классов в одной директории)

**Проблема:** 16 классов на одном уровне, threshold 15. На границе - при добавлении 2-3 DTO станет явно много.

**Действие:** Разбить на подпакеты `web/dto/{topic,node,edge,source,authority}/`. Не критично сейчас.

**Effort:** M | **Phase:** 1 (если хватит времени, иначе defer)

---

### Naming

#### B-04 [medium] [naming, consistency] DTO suffix несоответствие

**Файл:**
- `library/web/dto/BookSummary.java`
- `library/web/dto/PageSummary.java`
- `library/shamela/web/dto/StagingBookSearchResult.java`

**Проблема:** В коде смешано `*Response`, `*Summary`, `*SearchResult`, `*Row`. Стандарт `*Response/Request` нарушен.

**Действие:**
- `BookSummary` → `BookSummaryResponse`
- `PageSummary` → `PageSummaryResponse`
- `StagingBookSearchResult` → `StagingBookSearchResponse`
- `*Row` остаётся (это staging-уровень, не REST DTO)

**Effort:** S | **Phase:** 4 | **Related:** F-07

---

#### B-05 [low] [naming] Method naming - getOne vs findById

**Файл:** `backend/src/main/java/ru/basnukaev/argumentmap/library/web/controller/BookController.java:66`

**Проблема:** Контроллер использует `getOne(@PathVariable UUID bookId)`, остальные контроллеры - описательные имена.

**Действие:** `BookController.getOne()` → `getBookDetail()`.

**Effort:** S | **Phase:** 4

---

### Duplication

#### B-03 [medium] [duplication, helpers] Shamela DAO - дубли helper-методов

**Файл:**
- `library/shamela/repository/ShamelaBookDao.java:241-288`
- `library/shamela/repository/ShamelaTitleDao.java`
- `library/shamela/repository/ShamelaPageDao.java`
- `library/shamela/repository/ShamelaAuthorDao.java`
- `library/shamela/repository/ShamelaCategoryDao.java`
- `library/shamela/repository/ShamelaSyncStateDao.java`

**Проблема:** 6 DAOs повторяют `setNullableLong/Int/String`, `getNullableLong/Int/Boolean`, `sumAffected`, `BATCH_SIZE = 1000` - 20-30 строк бойлерплейта в каждом.

**Действие:** Extract в `ShamelaDaoHelper` (static utility class) или абстрактный `AbstractShamelaDao` базовый класс.

**Effort:** S | **Phase:** 1 | **Related:** T-02

---

### Hacks / comments

**B-06** (Russian comments в production) - **информация, действий не требует**. Project ведётся на русском, конвенция допускает русские комментарии.

---

## Frontend findings (F)

### Boundary

#### F-01 [high] [boundary] TopicGraphPage 1161 LOC

**Файл:** `frontend/src/pages/TopicGraphPage.tsx:1-1161`

**Проблема:** Один файл содержит root + Graph subcomponent + React Flow setup + 11 useState для модалок/selection/panels + 15 event handlers + 3 контекстных меню + z-index logic + useCallback с exhaustive-deps нарушениями.

**Действие:** Разнести на:
1. `TopicGraphPage.tsx` - route + loader (loading/error/success wrapper) < 100 LOC
2. `GraphCanvas.tsx` - React Flow + node/edge selection + mouse handlers
3. `GraphToolbar.tsx` - Add Node, Add Edge, Zoom, Visibility toggle
4. `GraphContextMenu.tsx` - правый клик меню
5. `useGraphModals.ts` - hook управляющий модалками

**Effort:** L | **Phase:** 2 | **Related:** F-13, F-05

---

#### F-02 [high] [boundary] BookReaderPage 714 LOC

**Файл:** `frontend/src/pages/BookReaderPage.tsx:1-714`

**Проблема:** Две независимые сущности в одном - навигация (chapter tree + pagination) + контент (text/PDF режимы).

**Действие:** Разнести на:
1. `BookReaderPage.tsx` - координатор < 200 LOC
2. `BookChapterTree.tsx` - рекурсивный TreeNode
3. `BookPageRenderer.tsx` - выбор text/pdf renderer
4. `TextPageViewer.tsx` - рендеринг текста с shamela-specific логикой

**Effort:** M | **Phase:** 2

---

#### F-03 [medium] [boundary] NodeDetailsPanel 613 LOC

**Файл:** `frontend/src/components/graph/NodeDetailsPanel.tsx:1-613`

**Проблема:** 6 независимых секций (edit, citations, revisions, metadata, history) с собственными state-machine'ами. Дублирует loading/error patterns.

**Действие:** Разнести на section-components:
- `NodeEditForm.tsx` - edit content + status + type
- `NodeCitationsPanel.tsx` - citations CRUD
- `NodeMetadataSection.tsx` - created/updated info
- `NodeRevisionsSection.tsx` - revisions list

**Effort:** M | **Phase:** 3 (после F-01)

---

#### F-04 [medium] [boundary] AddSourceModal 550 LOC - 2 режима

**Файл:** `frontend/src/components/graph/AddSourceModal.tsx:1-550`

**Проблема:** Modal переключает 'search'|'create' с дублированием JSX. Create-форма содержит 4 поля + валидацию.

**Действие:**
- `AddSourceModal.tsx` - selection mode
- `SourceSearchForm.tsx` - поиск/выбор
- `SourceCreateForm.tsx` - форма создания (reusable)

**Effort:** S | **Phase:** 3

---

#### F-05 [medium] [boundary, duplication] AddEdgeModal 298 + AddNodeModal 228 одинаковый шаблон

**Файл:**
- `frontend/src/components/graph/AddEdgeModal.tsx:1-298`
- `frontend/src/components/graph/AddNodeModal.tsx:1-228`

**Проблема:** Одинаковый шаблон: disabled fieldset при submitting, error message с fieldErrors, reset при close, одинаковые стили Modal+buttons.

**Действие:** Создать shared `FormModal.tsx` (open/title/submitting/error/onSubmit/onClose/children props).

**Effort:** S | **Phase:** 3 (после F-09)

---

#### F-06 [low] [boundary] CompactMiniMap 244 LOC

**Файл:** `frontend/src/components/graph/CompactMiniMap.tsx:1-244`

**Проблема:** Bbox calc + scaling + SVG render + click handler. Bbox/scaling можно reuse (например export графа).

**Действие:** Extract hook `useGraphBounds()` с bbox/scaling логикой. Не критично.

**Effort:** S | **Phase:** 3 (defer возможно)

---

### Naming

#### F-07 [medium] [naming] UUID/ID inconsistency в DTO/props/routes

**Проблема:**
- `bookId` в routing params (`/books/:bookId`) и props
- `nodeId`, `topicId` в API paths и props
- `id` как primary key в DTO
- `authorityId`, `sourceId` как foreign keys
- Иногда `sourceId` передаётся как `id` в select-компонентах

**Действие:** Зафиксировать конвенцию в `CLAUDE.md` или `docs/architecture.md`:
- `id` - primary key в собственном DTO
- `{entityName}Id` - foreign keys и props
- `:{entityName}Id` - route params

**Effort:** S | **Phase:** 4 | **Related:** B-04

---

#### F-08 [info] Page naming - **уже соблюдается**

Все pages используют `*Page` суффикс. Документировать в CLAUDE.md как стандарт. Action: только документация.

---

### Duplication

#### F-09 [high] [duplication] Error handling в 4 модалках

**Файл:**
- `frontend/src/components/graph/AddEdgeModal.tsx:121`
- `frontend/src/components/graph/AddNodeModal.tsx:115`
- `frontend/src/components/graph/EdgeDetailsPanel.tsx:220`
- `frontend/src/components/graph/NodeDetailsPanel.tsx:278`

**Проблема:** Идентичный код:
```ts
const fieldErrors = e.problem.errors?.map((er) => `${er.field}: ${er.message}`).join('; ');
setError(fieldErrors || e.problem.detail || e.problem.title);
```

**Действие:** Extract в `frontend/src/shared/utils/apiErrors.ts` (после Phase 2 - shared в apps/-структуре):
```ts
export function formatApiError(error: ApiError): string {
  if (error.problem.errors?.length) {
    return error.problem.errors.map(er => `${er.field}: ${er.message}`).join('; ');
  }
  return error.problem.detail || error.problem.title;
}
```

**Effort:** S | **Phase:** 3

---

#### F-10 [high] [duplication] AsyncState pattern в 8 местах

**Файл:**
- `pages/TopicGraphPage.tsx:70-72`
- `pages/TopicListPage.tsx:34-36`
- `pages/BookListPage.tsx:49-53`
- `pages/BookReaderPage.tsx:49-52, 54-57`
- `components/graph/AddSourceModal.tsx:42-45`
- `components/library/PdfViewer.tsx:59-62`
- `components/graph/NodeDetailsPanel.tsx:43-47, 60-64`

**Проблема:** Каждый компонент определяет свой discriminated union `LoadState/ViewState` для loading/success/error. Названия не унифицированы.

**Действие:** Создать `shared/types/async.ts`:
```ts
export type AsyncState<T, E = string> =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; error: E };
```

Использовать в 8 компонентах.

**Effort:** M | **Phase:** 3 (после Phase 2 - shared/ есть)

---

#### F-11 [medium] [duplication] Inline styles в 9 местах

**Файл:**
- `components/graph/CustomEdge.tsx:53, 63` (stroke/transform)
- `components/graph/EdgeDetailsPanel.tsx:332`
- `components/graph/CompactMiniMap.tsx:157`
- `components/graph/AddEdgeModal.tsx:187`
- `components/library/PdfViewer.tsx:193`
- `components/ui/ContextMenu.tsx:56`
- `pages/BookReaderPage.tsx:525`

**Проблема:** `style={{...}}` для динамических значений которые нельзя в Tailwind classes. Не критично - часть из них необходимая (SVG stroke зависит от runtime). Часть можно перенести в CSS variables.

**Действие:** В Phase 3:
- Для SVG stroke - оставить inline (необходимость)
- gridTemplateColumns (AddEdgeModal:187) - попробовать перенести в Tailwind grid utilities
- Pixel padding (BookReaderPage:525) - перенести в Tailwind padding utility

**Effort:** S | **Phase:** 3

---

#### F-12 [low] [duplication] API fetch pattern в 8+ местах

**Проблема:** `apiGetRaw().then().catch()` повторяется с одинаковой error logic.

**Действие:** Опционально создать `useApiQuery<T>` hook. Defer если время не позволит.

**Effort:** M | **Phase:** 3 (defer-able)

---

### Hacks

#### F-13 [high] [hacks] 5 eslint-disable exhaustive-deps в TopicGraphPage

**Файл:** `frontend/src/pages/TopicGraphPage.tsx:204, 240, 605, 652, 803`

**Проблема:** 5 useEffect'ов с `eslint-disable-next-line react-hooks/exhaustive-deps` + 1 `react-hooks/refs` - сигнал что dependency logic сложная и архитектура неправильная.

**Действие:** Резолвится при разбиении F-01:
- Извлечь refresh-логику в `useGraphRefresh()` hook
- Subcomponents имеют простые useEffect-цепочки

**Effort:** часть L F-01 | **Phase:** 2 | **Related:** F-01

---

#### F-14 [medium] [hacks, security] TODO DOMPurify не реализован

**Файл:** `frontend/src/pages/BookReaderPage.tsx:706`

**Проблема:** Comment `// TODO: DOMPurify для не-shamela источников`. Текущий код использует `dangerouslySetInnerHTML` без санитизации.

**Почему важно:** Security risk если появятся non-shamela источники (XSS).

**Действие:** В Phase 3:
- Установить `dompurify` + types
- Sanitize все non-shamela HTML
- Обновить gotchas.md ссылкой на это решение

**Effort:** S | **Phase:** 3

---

#### F-15 [info] - покрыт F-11

Дубликат F-11 (inline styles SVG). Consolidated.

---

#### F-16, F-17, F-18 [resolved/info]

- F-16 (PageDetail intersection) - резолвится через `npm run generate-api`. Phase=NONE
- F-17 (shamela sanitization) - текущее решение OK, будущий refactor когда добавятся источники. Phase=NONE сейчас
- F-18 (vite dev fallback) - уже закрыт коммитом 0b4bf86. Resolved

---

### Apps/ mapping (для Phase 2)

| Файл (src/) | → App | Заметки |
|-------------|-------|---------|
| `App.tsx` | (root) | router root, остаётся в src/ корне |
| `main.tsx` | (root) | entry point, остаётся |
| `index.css` | (root) | global styles, остаётся |
| `pages/TopicGraphPage.tsx` | argument-map | **РАЗНЕСТИ** перед миграцией (F-01) |
| `pages/TopicListPage.tsx` | argument-map | |
| `pages/CreateTopicPage.tsx` | argument-map | |
| `pages/TopicListPage.test.tsx` | argument-map | |
| `pages/CreateTopicPage.test.tsx` | argument-map | |
| `pages/TopicGraphPage.test.tsx` | argument-map | |
| `pages/BookListPage.tsx` | library | |
| `pages/BookReaderPage.tsx` | library | **РАЗНЕСТИ** (F-02) |
| `pages/AdminShamelaPage.tsx` | admin | |
| `components/graph/*` (all) | argument-map | NodeCard, CustomEdge, CompactMiniMap, NodeDetailsPanel (разнести F-03), EdgeDetailsPanel, AddNode/Edge/SourceModal (F-04, F-05), NodeSelect |
| `components/library/PdfViewer.tsx` | library | |
| `components/layout/*` | shared | Header, etc. |
| `components/ui/*` | shared | Button, Modal, ContextMenu, etc. |
| `api/client.ts` | shared | API client wrapper |
| `api/client.test.ts` | shared | |
| `api/types.ts` | shared | autogen - **содержимое не трогаем** |
| `stores/toastStore.ts` | shared | cross-app toast |
| `stores/toastStore.test.ts` | shared | |
| `utils/designTokens.ts` | shared | used by both apps |
| `utils/edgeRules.ts` | argument-map | |
| `utils/edgeRules.test.ts` | argument-map | |
| `utils/graphLayout.ts` | argument-map | |
| `utils/graphLayout.test.ts` | argument-map | |
| `utils/attachmentTokens.ts` | argument-map | |

**Целевая структура после Phase 2:**

```
frontend/src/
  apps/
    argument-map/
      pages/      (TopicGraphPage, TopicListPage, CreateTopicPage + tests)
      components/ (graph/*, + topic-graph/ subcomponents после F-01)
      utils/      (edgeRules, graphLayout, attachmentTokens + tests)
      hooks/      (useGraphModals, useGraphRefresh, useGraphBounds)
    library/
      pages/      (BookListPage, BookReaderPage)
      components/ (PdfViewer + book-reader/ subcomponents после F-02)
    admin/
      pages/      (AdminShamelaPage)
  shared/
    api/
    components/   (layout/, ui/)
    stores/       (toastStore)
    utils/        (designTokens)
    types/        (async.ts после F-10)
  App.tsx
  main.tsx
  index.css
```

---

## Tests findings (T)

### Structure

#### T-01 [high] [structure, size] Oversized tests 300+ LOC

**Файл:**
- `backend/src/test/java/.../ShamelaAdminControllerIT.java:1-401`
- `backend/src/test/java/.../ShamelaImportServiceIT.java:1-367` (особенно тест на полный pipeline 157 LOC)
- `frontend/src/components/graph/NodeDetailsPanel.test.tsx:1-403` (13 behaviors в одном файле)

**Действие:** Разнести по логическим suite'ам с shared `@BeforeEach`/`beforeEach`. Делать в Phase 1 для backend, в Phase 3 для frontend (после F-03 разбиения NodeDetailsPanel).

**Effort:** M | **Phase:** 1 (backend) + 3 (frontend) | **Related:** T-02

---

#### T-02 [medium] [structure, duplication] SQL INSERT дублирование

**Файл:**
- `EdgeServiceIT.java:54-61, 88-91, 310-313` (INSERT users/topics 3 раза)
- `ShamelaAdminControllerIT.java:86-96`

**Действие:** Создать `backend/src/test/java/.../TestFixtures.java` (или package-private util) с `insertUser`, `insertTopic` helpers.

**Effort:** S | **Phase:** 1 | **Related:** B-03

---

#### T-03 [medium] [structure, duplication, frontend] HTMLDialogElement mock в 12 файлах

**Файл:** `frontend/src/components/graph/*.test.tsx` (12 файлов с одинаковым beforeAll polyfill)

**Действие:** Extract в `frontend/src/test/setupDialog.ts`, вызвать один раз в `test-setup.ts`.

**Effort:** S | **Phase:** 1

---

### Smells

#### T-04 [high] [smells, async, flakiness] 30+ waitFor() без timeout

**Файл:**
- `AddEdgeModal.test.tsx:85, 105, 123, 145, 167, 189, 211`
- `NodeDetailsPanel.test.tsx:137, 158, 189`
- ~20 other test files

**Действие:** Добавить explicit timeout (200ms для синхронных моков, 500ms для async). Создать helper:
```ts
export const waitForApiCall = (fn: () => void) => waitFor(fn, { timeout: 200 });
```

**Effort:** M | **Phase:** 3

---

#### T-05 [medium] [smells, brittle, frontend] Tailwind class assertions

**Файл:**
- `NodeDetailsPanel.test.tsx:68` (toHaveClass('bg-amber-100'))
- `Button.test.tsx` (toHaveClass('bg-white'/'bg-indigo-600'))

**Действие:** Заменить:
- На семантические `expect(badge).toHaveStyle('background-color: ...')` - если важен цвет
- На `data-theme="alert"` атрибут + assertion на него
- Удалить если просто "присутствует"

**Effort:** M | **Phase:** 3

---

#### T-06 [high] [smells, over-mocking] ShamelaAdminControllerIT over-mocks

**Файл:** `backend/src/test/java/.../ShamelaAdminControllerIT.java:53-401`

**Проблема:** MockMvc-тест мокает `@MockitoBean ShamelaImportService` и `ShamelaToLibraryMapper`. Тестируется только сериализация JSON, реальная логика - в ShamelaImportServiceIT. Дублирование assertion'ов.

**Действие:** Сократить scope - 1 happy-path + 1 error-case на endpoint, убрать duplication. Mock setup в `@BeforeEach`.

**Effort:** M | **Phase:** 3 | **Related:** T-01, T-02

---

#### T-07 [medium] [smells, magic-strings] Magic UUIDs/strings

**Файл:**
- `ShamelaAdminControllerIT.java` (41557L, 99999L, 41558L magic book IDs)
- `EdgeServiceIT.java` (UUID.randomUUID() без semantic names)

**Действие:** Extract в константы (`TEST_BOOK_SAHIH_AL_BUKHARI = 41557L` и т.д.).

**Effort:** S | **Phase:** 3

---

#### T-08 [low] [smells, time-dependent] Instant.now() в фикстурах

**Файл:** `EdgeServiceIT.java:168, 183`

**Действие:** Defer. Не критично - timestamps в фикстурах должны быть "now".

**Effort:** S | **Phase:** defer (low priority)

---

### Coverage (out of marathon scope)

- **T-09** - 12 UI без тестов: feature work, не cleanup. Phase=NONE.
- **T-10** - ETL coverage adequate (unit + IT покрывают). Phase=NONE.

### Naming

#### T-11 [low] [naming, consistency] BE vs FE test naming

**Проблема:** Backend `method_behavior_outcome` (English), Frontend natural Russian descriptions.

**Действие:** Документировать различие в CLAUDE.md как осознанный выбор. Не мигрировать (объём L, ценность low).

**Effort:** S (только docs) | **Phase:** 5

---

## Docs findings (D)

Все 10 findings - low/medium severity, housekeeping без contradictions. Все в Phase 5.

#### D-01 [low] [adr] ADR не имеют "Implemented in:" поля

**Действие:** Добавить в template ADR поля `Status date:` и `Implemented in:`. Для ADR-017 заполнить ("Сессия 19, миграция 15").

**Effort:** S | **Phase:** 5

---

#### D-02 [medium] [architecture] architecture.md vs architecture-platform.md дублирование Library

**Файл:** `docs/architecture.md:254` и весь `docs/architecture-platform.md`

**Действие:** В `architecture.md` оставить 5-строчный обзор Library с ссылкой "полное описание в `architecture-platform.md`". Не дублировать.

**Effort:** S | **Phase:** 5

---

#### D-03 [low] [glossary] Printed page как primary identifier

**Файл:** `docs/glossary.md:166-199`

**Действие:** В разделе Library переименовать на "Library (книги, главы, страницы, source-first нумерация)" и поднять `Printed page` выше `Page` с пометкой "primary source identifier (source-first)".

**Effort:** S | **Phase:** 5

---

#### D-04 [low] [progress, roadmap] Несовместимый формат закрытия этапов

**Действие:** В progress.md (Сессия 19, конец раздела про Этап 13) добавить резюме в формате roadmap'а ([x]/[~]).

**Effort:** S | **Phase:** 5

---

#### D-05 [medium] [api-contract] api-contract.md vs реальный OpenAPI - children gap

**Файл:** `docs/api-contract.md` (раздел PageResponse/ChapterResponse)

**Действие:** Добавить примечание про springdoc-openapi gap для self-referential `children` и frontend intersection solution.

**Effort:** S | **Phase:** 5

---

#### D-06 [low] [gotchas] Gotcha @CurrentUser резолвен, но не помечен

**Файл:** `docs/gotchas.md:151-182`

**Действие:** Перенести в раздел "Решённые ловушки (архив)" с пометкой `[RESOLVED in Session 16]`. Применить паттерн ко всем resolved gotchas.

**Effort:** S | **Phase:** 5

---

#### D-07 [low] [vision] vision.md не указывает timeline

**Действие:** Добавить disclaimer в начале: "Долгосрочное видение, Этап 18+. Текущий focus - см. roadmap.md".

**Effort:** S | **Phase:** 5

---

#### D-08 [medium] [architecture] Frontend модули устарели в architecture.md

**Действие:** После Phase 2 (apps/ структура есть) - обновить раздел "Frontend модули". Это **Phase 5 после Phase 2**.

**Effort:** M | **Phase:** 5

---

#### D-09 [low] [glossary] Удалённые понятия неполные

**Действие:** Добавить в `glossary.md` раздел "Удалённые понятия":
- `NodeAuthority` (ADR-017, миграция 15)
- `Stance` enum (ADR-017)

**Effort:** S | **Phase:** 5

---

#### D-10 [low] [meta] progress.md архивация может сломать ссылки

**Действие:** Перед архивацией progress.md - grep на ссылки, обновить относительные пути.

**Effort:** S | **Phase:** 5

---

## Cross-cutting findings

Findings которые охватывают несколько потоков:

| Тема | Backend | Frontend | Tests | Docs |
|------|---------|----------|-------|------|
| **Helper duplication** | B-03 (DAO helpers) | F-05 (Modal pattern) | T-02 (SQL fixtures), T-03 (Dialog mock) | - |
| **DTO/ID naming** | B-04 (DTO suffix) | F-07 (UUID props) | - | - |
| **AsyncState/Error handling** | - | F-09, F-10, F-12 | - | - |
| **Boundary разрезание + связанные tests** | B-01, B-02 | F-01..F-04 | T-01 (oversized tests) | D-08 (после reorg) |

---

## Phase backlog

### Phase 1 - Backend boundaries (10 findings)

**High:** B-01, B-02, T-01 (backend часть)
**Medium:** B-03, T-02
**Low:** B-07 (defer-able)

Действия:
1. ShamelaToLibraryMapper → 3 mapper'а (B-01)
2. ShamelaImportService → 2 сервиса или Facade (B-02)
3. Shamela DAO helpers → ShamelaDaoHelper (B-03) + общий TestFixtures (T-02)
4. Backend oversized tests разнести (T-01 backend часть)
5. (opt) web/dto подпакеты (B-07)

### Phase 2 - Frontend apps/ + разбиение монстров (8 findings)

**High:** F-01, F-02, F-13
**Medium:** F-03 (после F-01), F-04

Действия:
1. Pre-flight: зелёный билд
2. Vite alias + tsconfig paths
3. Mkdir apps/{...}, shared/
4. **Разнести TopicGraphPage** (F-01) + резолв F-13 eslint-disable + extract `useGraphRefresh`
5. **Разнести BookReaderPage** (F-02)
6. Перенести файлы по apps/ mapping
7. Обновить импорты
8. Тесты + build

### Phase 3 - Cleanup (8 findings)

**High:** F-09, F-10, T-04, T-06
**Medium:** F-03 (продолжение), F-04, F-05, F-11, F-14, T-05, T-07
**Low:** F-06, F-12, T-08

Действия:
1. F-09 → shared/utils/apiErrors.ts
2. F-10 → shared/types/async.ts + migrate 8 компонентов
3. F-03 → split NodeDetailsPanel (если не сделано в Phase 2)
4. F-04 → split AddSourceModal
5. F-05 → shared FormModal
6. F-14 → DOMPurify install + sanitize non-shamela
7. T-04 → explicit timeouts + waitForApiCall helper
8. T-05 → semantic assertions instead of Tailwind classes
9. T-06 → reduce ShamelaAdminControllerIT scope
10. T-07 → extract test constants

### Phase 4 - Naming (5 findings)

**Medium:** B-04, F-07
**Low:** B-05

Действия:
1. B-04 - DTO suffix unification (BookSummary → BookSummaryResponse и т.д.)
2. B-05 - BookController.getOne → getBookDetail
3. F-07 - документировать UUID/ID convention в CLAUDE.md
4. Регенерация types.ts после backend изменений

### Phase 5 - Docs (10 findings + apps/ doc update)

**Medium:** D-02, D-05, D-08
**Low:** D-01, D-03, D-04, D-06, D-07, D-09, D-10
**Info:** F-08, T-11

Действия:
1. Архивация progress.md Сессии 1-20 → archive/
2. Создать корневой CLAUDE.md
3. Обновить D-08 frontend модули (после Phase 2)
4. D-02 устранить дублирование Library docs
5. D-05 добавить springdoc-openapi gap notice
6. D-03 elevate printed_page
7. D-09 add NodeAuthority/Stance to removed
8. D-06 reorg gotchas (resolved архив)
9. D-01 + D-04 + D-07 housekeeping
10. D-10 update links перед archivации

---

## Финализация (не Phase)

После всех Phase:
- `./mvnw verify` зелёный
- `npm run build` + `npm test` зелёные
- SESSION_START_PROMPT.md обновлён
- progress.md запись Сессии 25
- Финальный коммит
