# Design spec: Citation Picker (Этап 18.f)

**Дата:** 2026-05-13
**Автор:** brainstorming session Сессии 29
**Статус:** approved, ожидает implementation plan
**Связанные ADR (будут созданы при implementation):** ADR-026, ADR-027
**Связанные ADR (existing):** ADR-017 (трёхуровневая модель цитирования),
ADR-018 (платформенный pivot), ADR-021 (source-first нумерация),
ADR-024 (object storage)

---

## Контекст

После закрытия этапа 25.b (объектное хранилище MinIO с PDF streaming)
foundation для library готов. Следующий шаг по ADR-018 платформенному
pivot - связать **library** (книги, страницы, PDF) с **argument-map**
(узлы, цитаты) через универсальный **CitationPicker** компонент.

Сейчас привязка источника к узлу происходит через `AddSourceModal`,
где пользователь либо выбирает существующий `Source` из master-data,
либо создаёт новый со свободным текстом `citation`. Library книги
никак не интегрированы в этот flow - нет способа выбрать "цитата из
Тафсира Ибн Касира, Т.1 стр.47, строки 12-15" с сохранением
**точной точки в источнике**.

Требования к citation в исламской науке (`такхридж аль-хадис`):
- Прослеживаемость до physical page + line range
- Полноценная справка: книга, издание, том, страница, строка
- Стабильность ссылки во времени (re-import shamela не должен ломать
  ссылки)
- Поддержка как текстового представления (HTML-страницы), так и PDF
  scans, и future image scans

## Цель

Создать CitationPicker компонент который:

1. Открывается из `NodeDetailsPanel` секции «Источники» по кнопке
   «Привести источник»
2. Открывает full-screen модалку с встроенной библиотекой - browse
   книг, выбор страницы, переключение между text/PDF mode
3. Позволяет выделить фрагмент (text selection или PDF bbox) и
   привязать его к узлу argument-map с полной положительной информацией
4. Сохраняет в БД: какая книга, какая страница (для text) или какой
   PDF (для PDF), точная позиция (char range или bbox), human-readable
   location string
5. Citation rows в `NodeCitationsSection` становятся clickable -
   открывают deep link на источник с подсветкой фрагмента

## Не входит в этот spec

- Q&A приложение (Этап 19) и его интеграция с CitationPicker -
  отложено, MVP только argument-map
- Reverse flow: выделение в BookReader → создать citation на узел
  (future enhancement)
- Region selection scan citations через image_region_id -
  схема готовится (FK уже в node_sources), но UI создаётся в Этапе
  17 (image scans + OCR)
- Text↔PDF page mapping (Этап 25.d.2) - если mapping готов, citation
  text mode может предложить также PDF mode при submit. Если нет -
  два режима независимы

## Архитектура

### Точка входа

`NodeDetailsPanel` → секция «Источники» (`NodeCitationsSection`):
- **«Привести источник»** (primary, indigo) → CitationPicker (новый
  flow через library)
- **«Свободный источник»** (secondary, slate) → AddSourceModal
  (existing legacy для URL/article/manual hadith)

Старый AddSourceModal **сохраняется** - покрывает use cases где нет
library книги (URL статьи, custom hadith, ручной ввод).

### CitationPicker layout

Full-screen overlay, 3-колонный layout:

```
╔═══════════════════════════════════════════════════════════════════╗
║ Привести источник для узла: "Дозволенность Мавлида ан-Наби"   [X]║
╠═════════╤═════════════════════════════════════════╤═══════════════╣
║ КНИГИ   │ ◇ Тафсир Ибн Касира            [текст│PDF]│ ВЫБРАННАЯ   ║
║         │ ◇ Том 1, стр 47       [< prev] [next >]   │ ЦИТАТА      ║
║ [поиск] │ ┌─────────────────────────────────────┐   │             ║
║         │ │  ChapterList │  PageView           │   │ ┌─────────┐ ║
║ □ Тафс. │ │  Главы       │  (Arabic text RTL)  │   │ │ "وأرى   │ ║
║ ✓ Тафс. │ │  - 1. Всту.. │                      │   │ │  أن لا.."│ ║
║ □ Маджм.│ │  - 2. Сура.. │  [выделенный        │   │ └─────────┘ ║
║ □ Хусн  │ │  ▸ 3. Сура.. │   фрагмент]         │   │             ║
║ □ Бухар.│ │              │                      │   │ Комментарий:║
║ ...     │ │              │                      │   │ ┌─────────┐ ║
║         │ │              │                      │   │ │         │ ║
║         │ └─────────────────────────────────────┘   │ └─────────┘ ║
║         │                                           │             ║
║         │                                           │ [Привести]  ║
╚═════════╧═════════════════════════════════════════╧═══════════════╝
   ~280px         ~центр (флекс)                       ~320px
```

- **Левая (~280px)** - BookListSidebar: локальный поиск + фильтр
  по bookType + список карточек
- **Центр (флекс)** - EmbeddedReader: BookHeader + ReaderModeSwitch +
  PageJump + ChapterList | PageView/PdfViewer + prev/next footer
- **Правая (~320px)** - SelectionPanel: preview выделенного фрагмента,
  textarea для context, кнопка «Привести»

### Компонентная иерархия

```
NodeDetailsPanel (existing)
  └─ NodeCitationsSection (модифицированная)
     ├─ кнопка "Привести источник" → CitationPicker
     └─ кнопка "Свободный источник" → AddSourceModal (existing)

CitationPicker (новый, src/shared/components/citation/)
  ├─ BookListSidebar (новый, реюз логики apps/library/pages/BookListPage)
  ├─ EmbeddedReader (новый wrapper)
  │   ├─ ReaderModeSwitch (moved → shared/components/reader/)
  │   ├─ ChapterList (moved)
  │   ├─ PageView (moved, расширен `selectable` prop)
  │   └─ PdfViewer (moved, расширен `selectable` prop)
  └─ SelectionPanel (новый): preview + context + submit

BookReaderPage (модифицированная)
  ├─ использует moved components из shared/components/reader/
  └─ обрабатывает query params для deep links на citations
```

### UX flow

1. User в графе кликает узел → `NodeDetailsPanel` справа → секция
   «Источники» → клик «Привести источник»
2. CitationPicker модалка открывается (fullscreen overlay, conditional
   render для чистого state)
3. User выбирает книгу слева (или ищет) → центр загружает её reader
   на странице 1
4. User листает страницы (ChapterList / PageJump / prev-next) +
   переключает text/PDF
5. **Text mode**: ЛКМ-drag выделяет фрагмент → `window.getSelection()`
   → range computed через DOM TreeWalker (char offsets от начала
   HTML контента страницы) → preview справа + кнопка «Привести»
   становится active
6. **PDF mode**: ЛКМ-drag рисует rectangle поверх canvas (custom
   overlay layer без зависимостей) → bbox normalized в координатах
   0-1 → preview справа (если PDF имеет text layer, извлекаем text
   через `getTextContent()` как quote snapshot)
7. User пишет комментарий → клик «Привести» → POST → закрытие
   модалки → секция «Источники» обновляется
8. Новая citation отображается с **computed на backend** location
   string ("Тафсир Ибн Касира, Т.1 стр.47, строки 12-15") и
   clickable - открывает deep link на источник

## Data model

### Миграция 22 - Source.book_id FK

```sql
ALTER TABLE sources
  ADD COLUMN book_id UUID REFERENCES lib_books(id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_sources_book_per_type
  ON sources(source_type, book_id)
  WHERE book_id IS NOT NULL;

ALTER TABLE sources
  ADD CONSTRAINT chk_sources_book_id_only_for_book_type
  CHECK (book_id IS NULL OR source_type = 'BOOK');
```

`ON DELETE RESTRICT` намеренно жёсткий - книгу нельзя удалить пока
на неё ссылается citation. Это часть инварианта стабильности.

Идемпотентность: один Source per (sourceType=BOOK, bookId). При
citation flow backend ищет existing или создаёт новый (ensure-or-create
паттерн).

### Миграция 23 - node_sources positional citation fields

```sql
ALTER TABLE node_sources
  ADD COLUMN page_id          UUID REFERENCES lib_pages(id) ON DELETE RESTRICT,
  ADD COLUMN range_start      INTEGER,
  ADD COLUMN range_end        INTEGER,
  ADD COLUMN pdf_file_id      UUID REFERENCES library_files(id) ON DELETE RESTRICT,
  ADD COLUMN pdf_page_number  INTEGER,
  ADD COLUMN pdf_bbox         JSONB,
  ADD COLUMN image_region_id  UUID REFERENCES lib_image_regions(id) ON DELETE RESTRICT;

ALTER TABLE node_sources
  ADD CONSTRAINT chk_node_sources_one_mode
  CHECK (
    -- TEXT mode
    (page_id IS NOT NULL AND range_start IS NOT NULL AND range_end IS NOT NULL
     AND range_start >= 0 AND range_end > range_start
     AND pdf_file_id IS NULL AND image_region_id IS NULL)
    OR
    -- PDF mode
    (pdf_file_id IS NOT NULL AND pdf_page_number IS NOT NULL AND pdf_bbox IS NOT NULL
     AND pdf_page_number >= 1
     AND page_id IS NULL AND image_region_id IS NULL)
    OR
    -- REGION mode (future, FK уже работает)
    (image_region_id IS NOT NULL AND page_id IS NULL AND pdf_file_id IS NULL)
    OR
    -- LEGACY mode (freeform citation без library)
    (page_id IS NULL AND pdf_file_id IS NULL AND image_region_id IS NULL)
  );

CREATE INDEX idx_node_sources_page_id    ON node_sources(page_id)
  WHERE page_id IS NOT NULL;
CREATE INDEX idx_node_sources_pdf_file   ON node_sources(pdf_file_id)
  WHERE pdf_file_id IS NOT NULL;
CREATE INDEX idx_node_sources_image_reg  ON node_sources(image_region_id)
  WHERE image_region_id IS NOT NULL;
```

CHECK четвёртой веткой поддерживает legacy citations через
AddSourceModal (без positional info) - обратная совместимость с уже
существующими node_sources rows.

`pdf_bbox` нормализован в координатах 0-1 (не пиксели) - zoom-invariant,
работает при любом PDF.js viewport scale.

### ShamelaToLibraryMapper UPSERT инвариант

Перед миграциями 22-23 - аудит `ShamelaToLibraryMapper.mapBook` (из
Сессии 22). Ожидаемое поведение: `lib_pages` upsert по композитному
ключу `(book_id, page_number)` через `ON CONFLICT DO UPDATE`. Если
текущая реализация DELETE+INSERT - меняется на UPSERT первой подзадачей
этого этапа. Иначе FK `ON DELETE RESTRICT` блокирует любой re-import
книги на которую уже сослались citations.

## Backend API

### POST /api/v1/nodes/{nodeId}/citations (новый endpoint)

Дополняет существующий POST `/api/v1/nodes/{nodeId}/sources`, не
заменяет.

**Request (text mode):**
```json
{
  "bookId": "uuid",
  "pageId": "uuid",
  "rangeStart": 0,
  "rangeEnd": 87,
  "quote": "وأرى أن لا تكون البدعة...",
  "context": "Ибн Касир признаёт..."
}
```

**Request (pdf mode):**
```json
{
  "bookId": "uuid",
  "pdfFileId": "uuid",
  "pdfPageNumber": 47,
  "pdfBbox": {"x": 0.12, "y": 0.23, "width": 0.65, "height": 0.05},
  "quote": "...",
  "context": "..."
}
```

**Request (region mode, future):**
```json
{
  "bookId": "uuid",
  "imageRegionId": "uuid",
  "context": "..."
}
```

**Response 201:**
```json
{
  "nodeId": "uuid",
  "sourceId": "uuid",
  "quote": "...",
  "context": "...",
  "location": "Тафсир Ибн Касира, Т.1 стр.47, строки 0-87",
  "mode": "TEXT" | "PDF" | "REGION",
  "pageId": "uuid|null",
  "rangeStart": "int|null",
  "rangeEnd": "int|null",
  "pdfFileId": "uuid|null",
  "pdfPageNumber": "int|null",
  "pdfBbox": {...} | null,
  "imageRegionId": "uuid|null",
  "createdAt": "iso8601"
}
```

**Errors:**
- 400 `invalid-citation-mode` - не ровно один из трёх режимов
- 400 `range-out-of-bounds` - range_end <= range_start
- 400 `invalid-bbox` - координаты вне 0-1
- 404 `node-not-found` / `book-not-found` / `page-not-found` /
  `pdf-not-available` / `image-region-not-found`

### GET /api/v1/nodes/{nodeId}/sources (расширен)

Возвращает массив **расширенного NodeSourceResponse** с полями mode,
pageId, rangeStart, rangeEnd, pdfFileId, pdfPageNumber, pdfBbox,
imageRegionId (null'ы для legacy citations).

Поле `location` computed на backend через SQL JOIN:

```sql
SELECT ns.*,
  CASE
    WHEN ns.page_id IS NOT NULL THEN
      b.title || ', Т.' || COALESCE(p.part::text, '?')
              || ' стр.' || COALESCE(p.printed_page::text, p.page_number::text)
              || ', строки ' || ns.range_start || '-' || ns.range_end
    WHEN ns.pdf_file_id IS NOT NULL THEN
      b.title || ', PDF стр.' || ns.pdf_page_number || ', регион'
    WHEN ns.image_region_id IS NOT NULL THEN
      b.title || ', скан стр.' || COALESCE(p2.printed_page::text, p2.page_number::text)
    ELSE ns.location  -- legacy snapshot
  END AS computed_location
FROM node_sources ns
LEFT JOIN sources s ON s.id = ns.source_id
LEFT JOIN lib_books b ON b.id = s.book_id
LEFT JOIN lib_pages p ON p.id = ns.page_id
LEFT JOIN lib_image_regions ir ON ir.id = ns.image_region_id
LEFT JOIN lib_pages p2 ON p2.id = ir.page_id
WHERE ns.node_id = ?
```

В колонку `node_sources.location` (text ≤200) при insert пишется
**snapshot** этого же computed string - safety net на случай
миграционных пертурбаций или если book/page будут soft-deleted.

## Java domain model

```java
// Source расширен
public record Source(
    UUID id, SourceType sourceType, String title, String citation,
    Reliability reliability, UUID authorityId,
    UUID bookId,  // NEW
    Map<String,Object> metadata, Instant createdAt
) {}

// NodeSource расширен
public record NodeSource(
    UUID nodeId, UUID sourceId, String quote, String context, String location,
    UUID pageId, Integer rangeStart, Integer rangeEnd,
    UUID pdfFileId, Integer pdfPageNumber, JsonNode pdfBbox,
    UUID imageRegionId,
    Instant createdAt
) {}

// Новые request/response
public record CitationRequest(
    UUID bookId,
    UUID pageId, Integer rangeStart, Integer rangeEnd,
    UUID pdfFileId, Integer pdfPageNumber, PdfBbox pdfBbox,
    UUID imageRegionId,
    String quote, String context
) {}

public record PdfBbox(double x, double y, double width, double height) {}

public record CitationMode(String value) {
    public static final CitationMode TEXT = new CitationMode("TEXT");
    public static final CitationMode PDF = new CitationMode("PDF");
    public static final CitationMode REGION = new CitationMode("REGION");
    public static final CitationMode LEGACY = new CitationMode("LEGACY");
}

public record NodeSourceResponse(
    UUID nodeId, UUID sourceId,
    String quote, String context, String location,
    CitationMode mode,
    UUID pageId, Integer rangeStart, Integer rangeEnd,
    UUID pdfFileId, Integer pdfPageNumber, PdfBbox pdfBbox,
    UUID imageRegionId,
    Instant createdAt
) {}
```

## Service слой

Новый `NodeCitationService` (или метод в `NodeSourceService`):

```java
public NodeSourceResponse createCitation(UUID nodeId, CitationRequest req) {
    // 1. Validate node exists (404 если нет)
    // 2. Validate mode: ровно один из (text fields) / (pdf fields) / (region fields)
    //    дублирует CHECK constraint, но даёт понятный error message раньше
    // 3. Validate book exists и доступна (404 book-not-found)
    // 4. Validate ссылающиеся entities: pageId / pdfFileId / imageRegionId (404)
    // 5. Ensure-or-create Source for (sourceType=BOOK, bookId):
    //    Optional<Source> existing = sourceRepo.findByBookId(bookId);
    //    UUID sourceId = existing.map(Source::id).orElseGet(() -> {
    //      Book book = bookRepo.findById(bookId).orElseThrow();
    //      Source created = new Source(... sourceType=BOOK, bookId, title=book.title, ...);
    //      return sourceRepo.upsertByBookId(created).id();  // ON CONFLICT DO NOTHING RETURNING id
    //    });
    // 6. Build location snapshot string из JOIN-данных
    // 7. Insert node_sources row с positional полями + snapshot location
    // 8. Return computed response через findByPkWithLocation
}
```

Race condition при concurrent ensure-or-create на ту же книгу:
unique index `uq_sources_book_per_type` ловит коллизию → паттерн
`INSERT ... ON CONFLICT (source_type, book_id) DO NOTHING RETURNING id`
гарантирует atomicity (тот же что в `LibraryFileRepository.upsertByBucketAndKey`
из 25.b post-review).

## Frontend changes

### Extract: shared mini-reader

Перенос файлов:
- `apps/library/components/BookHeader.tsx` → `shared/components/reader/`
- `apps/library/components/ChapterList.tsx` → `shared/components/reader/`
- `apps/library/components/PageJump.tsx` → `shared/components/reader/`
- `apps/library/components/PageView.tsx` → `shared/components/reader/`
- `apps/library/components/PdfViewer.tsx` → `shared/components/reader/`
- `apps/library/components/ReaderModeSwitch.tsx` → `shared/components/reader/`
- `apps/library/utils/bookReaderUtils.ts` → `shared/components/reader/utils.ts`

Обновляются импорты в `BookReaderPage`. Поведение не меняется - чистый
move + расширение PageView / PdfViewer новыми selection props.

### Расширение PageView

```typescript
interface Props {
  // existing props ...
  selectable?: boolean;
  onSelectionChange?: (sel: TextSelection | null) => void;
  highlightRange?: [number, number] | null;  // для deep link rendering
}

interface TextSelection {
  pageId: string;
  rangeStart: number;  // char offset от начала HTML текста
  rangeEnd: number;
  quote: string;       // выделенный text
}
```

- Selection через `window.getSelection()` → DOM Range → нормализация
  к char offsets **по plain text** (т.е. сумма `length` всех text
  nodes по порядку через TreeWalker, HTML теги не считаются)
- `highlightRange` рендерит `<mark>` обёртку через тот же TreeWalker
  механизм + auto-scroll into view
- Backward stability: char offsets устойчивы пока HTML структура
  страницы не меняется. При re-import shamela `mapBook` через UPSERT
  сохраняет HTML контент стабильным, offsets валидны. Если admin
  явно отредактирует страницу - existing citations могут указывать
  на shifted text (acceptable, edge case до 25.x admin tools)

### Расширение PdfViewer

```typescript
interface Props {
  // existing props ...
  selectable?: boolean;
  onBboxChange?: (sel: PdfSelection | null) => void;
  highlightBbox?: PdfBboxHighlight | null;
}

interface PdfSelection {
  pdfFileId: string;
  pdfPageNumber: number;
  bbox: { x: number; y: number; width: number; height: number };  // 0-1 normalized
  quote?: string;  // если PDF имеет text layer
}

interface PdfBboxHighlight {
  pdfPageNumber: number;
  bbox: { x: number; y: number; width: number; height: number };
}
```

- Custom overlay layer поверх PDF.js `<Page>` canvas (минимальная
  реализация через mouse events, без зависимости react-image-crop)
- Drag создаёт rectangle → normalized coords (0-1 относительно
  page viewport.width / viewport.height)
- Если PDF.js text layer доступен, извлекаем text внутри bbox через
  `page.getTextContent()` + intersection с bbox → передаём как quote
- `highlightBbox` рендерит rectangle с indigo border + полупрозрачным
  fill'ом

### CitationPicker

Новый файл `frontend/src/shared/components/citation/CitationPicker.tsx`.

```typescript
interface CitationPickerState {
  selectedBookId: string | null;
  bookDetail: BookDetail | null;
  pages: PageSummary[];
  pageNumber: number;
  readerMode: 'text' | 'pdf';
  textSelection: TextSelection | null;
  pdfSelection: PdfSelection | null;
  context: string;
  submitting: boolean;
  submitError: string | null;
}
```

Open/close через conditional render (idiom проекта `{open && <CitationPicker .../>}`)
для чистого state при каждом открытии. Esc закрывает (только если
не submitting).

### Изменения NodeCitationsSection

```tsx
<div className="mt-2 flex gap-2">
  <Button variant="primary" size="sm" icon={BookOpen}
          onClick={() => setCitationPickerOpen(true)} disabled={!nodeId}
          className="flex-1">
    Привести источник
  </Button>
  <Button variant="ghost" size="sm" icon={Plus}
          onClick={() => setAddSourceOpen(true)} disabled={!nodeId}
          className="flex-1">
    Свободный источник
  </Button>
</div>

{citationPickerOpen && nodeId && (
  <CitationPicker
    nodeId={nodeId}
    nodeContent={nodeContent}
    onClose={() => setCitationPickerOpen(false)}
    onCreated={loadSources}
  />
)}
{addSourceOpen && nodeId && <AddSourceModal ... />}
```

Каждый citation row в `CitationsList` становится clickable:
- text mode (pageId + rangeStart + rangeEnd) →
  `/books/${bookId}?pageId=${pageId}&highlight=${rangeStart}-${rangeEnd}`
- pdf mode (pdfFileId) →
  `/books/${bookId}?pdf=1&pdfPageNumber=${n}&bbox=${x},${y},${w},${h}`
- legacy (нет positional) - статичный display, не кликабельно

### Deep link handling в BookReaderPage

Query params на mount:
- `pageId=uuid` → findIndex в pages → setPageNumber, fallback на 1
  с toast если не найдено
- `highlight=start-end` → передаётся в PageView как `highlightRange={[start, end]}`
- `pdf=1` → setReaderMode('pdf')
- `pdfPageNumber=N` → передаётся в PdfViewer как initial page
- `bbox=x,y,w,h` → передаётся как `highlightBbox`

### Регенерация типов

После backend changes - `npm run generate-api` регенерирует
`frontend/src/shared/api/types.ts`. `NodeSourceResponse` получает
новые поля, появляется `CitationRequest` и сопутствующие.

## Error handling & edge cases

### Backend

- Concurrent ensure-or-create Source на ту же книгу - резолвится
  через `ON CONFLICT DO NOTHING RETURNING id` + retry findByBookId
- Удалённая книга/page между selection и submit - FK `ON DELETE RESTRICT`
  на всех trovel paths блокирует delete пока есть references. 409
  Conflict если admin force-delete attempt
- Invalid range / bbox - CHECK constraint + бэк-валидация дублирует
  с ProblemDetails (RFC 7807)
- Soft-deleted PDF (library_files.deleted_at IS NOT NULL) - `findActiveByBookId`
  empty → submit падает на ensure-or-create reference. ProblemDetails
  `pdf-not-available`

### Frontend

- Book list загрузка fail - retry button, picker остаётся открытым
- Selected book unavailable - toast + sidebar возвращает в неselected
- Empty selection submit - кнопка disabled (защита через state)
- Network fail при submit - toast + restore submitting=false, retry
- Esc во время submitting - игнорируется
- Deep link на удалённый pageId - fallback на page 1 + toast
- Corrupted query params (`highlight=abc-xyz`) - silent fallback
- PDF.js load fail для bbox highlight - lazy error boundary, fallback
  на page-level open

### UX edge cases

- Viewport < 1024px - stacked layout (sidebar сверху, reader центр,
  panel снизу). Min height 600px
- RTL Arabic selection - char offsets по logical order. Verify Selection
  API работает корректно
- PDF.js zoom level changes - bbox normalized 0-1 zoom-invariant
- Multi-volume книги - book select одна сущность, `printedPage` + `part`
  различают тома, PageJump поддерживает оба

## Testing strategy

### Backend IT (~25-30 новых)

- Миграция 22 чистый apply, FK + unique index + CHECK
- Миграция 23 чистый apply, 7 новых колонок + 4-ветка CHECK
- `SourceRepository.findByBookId` - hit / miss / soft-deleted
- `NodeCitationService.createCitation`:
  - text mode happy path (location computed, source ensure-or-create)
  - text mode с non-existent pageId → 404
  - text mode с rangeEnd <= rangeStart → 400
  - pdf mode happy path
  - pdf mode bbox validation
  - region mode happy path (pre-created lib_image_regions)
  - Concurrent two citations same book - один Source, два node_sources
  - Re-citation одной книги разными узлами - source reused
- `NodeCitationController` через MockMvc: каждый mode + validation errors
- `GET /api/v1/nodes/{nodeId}/sources` возвращает computed location
- `ShamelaToLibraryMapper` UPSERT invariant - mapBook дважды, page.id
  стабилен

### Frontend tests (~15-20 новых)

- `CitationPicker` integration (Vitest + RTL + MSW):
  - открытие → load books → select book → load reader → select text →
    submit → POST вызывается → onCreated callback
  - PDF mode: select → bbox drag → submit
  - Empty selection submit disabled
  - Esc закрывает (если не submitting)
- `BookReaderPage` deep link parsing
- `PageView` с highlightRange - `<mark>` рендерится
- `PdfViewer` с highlightBbox - overlay rectangle на coords
- Selection in PageView - правильные char offsets
- PdfViewer bbox selection - normalized bbox

### Tests которые меняются

- `NodeCitationsSection.test.tsx` - две кнопки + тест на открытие
  CitationPicker
- `AddSourceModal.test.tsx` - не меняется
- Если есть `BookReaderPage.test.tsx` - тесты на deep link handling

### Manual smoke (playwright)

После unit/IT - end-to-end через playwright:
1. Создать узел → NodeDetailsPanel → секция «Источники» → «Привести источник»
2. Выбрать «Тафсир Ибн Касира» → text mode → выделить фрагмент стр.47 →
   comment → «Привести»
3. Citation появилась с computed location, click → BookReader открыт
   на стр.47 с highlighted range
4. Back → PDF mode → выделить bbox на стр.47 → «Привести»
5. Click → BookReader в PDF mode на стр.47 с rectangle highlight
6. Restart backend → данные persist

## ADR которые будут созданы при implementation

### ADR-026 - Source.bookId FK для one-source-per-book

**Контекст:** Source (master data) и lib_books (импортированные книги)
сейчас disconnected. CitationPicker требует связи "цитата → книга".

**Решение:** Source.book_id UUID FK lib_books(id) ON DELETE RESTRICT,
unique index (source_type, book_id) WHERE book_id IS NOT NULL. Один
Source per (sourceType=BOOK, bookId). Ensure-or-create при citation flow.

**Альтернативы (отвергнуты):**
- Source.metadata JSONB libBookId - dangling refs (нет FK), сложнее
  искать "все цитаты книги X"
- NodeSource.bookId напрямую без Source - ломает ADR-017 abstraction
  (Source как единая точка)
- ON DELETE SET NULL - silent corruption, RESTRICT даёт explicit failure

### ADR-027 - Positional citation fields в node_sources

**Контекст:** Citation должна указывать точную позицию (текстовый
range / PDF bbox / image scan region) с deep link на источник.

**Решение:** node_sources расширяется 7 nullable колонками (page_id,
range_start, range_end, pdf_file_id, pdf_page_number, pdf_bbox,
image_region_id) + CHECK constraint обеспечивает один из четырёх
modes (TEXT / PDF / REGION / LEGACY). pdf_bbox normalized 0-1 для
zoom-invariance.

**Альтернативы (отвергнуты):**
- Один JSONB column для positional info - слабее integrity, сложнее
  query
- Отдельная таблица `node_source_positions` - over-engineering для
  1:1 связи
- Pixel coords для bbox - не zoom-invariant, breaks при resize

## Scope этого этапа (1 сессия)

- 2 миграции (22, 23) + 2 ADR
- ShamelaToLibraryMapper UPSERT audit + при необходимости fix
- Расширение Source / NodeSource records, repos, service, controller,
  DTO
- 25-30 новых backend IT
- Extract shared mini-reader (move + расширение selection props)
- CitationPicker + связанные UI компоненты
- Deep link handling в BookReaderPage
- Замена кнопки в NodeCitationsSection на две + click handler в rows
- 15-20 новых frontend tests
- Manual playwright smoke

Реалистично - 1.5-2 дня плотной работы. Если в одну сессию не уложимся
- handoff на логической границе (после backend части или после shared
extract).

## Decomposition (для implementation plan)

Предлагаемые подэтапы:
1. **18.f.1** - audit + fix ShamelaToLibraryMapper UPSERT (если нужно)
2. **18.f.2** - миграция 22 + ADR-026 + Source.bookId backend changes
3. **18.f.3** - миграция 23 + ADR-027 + node_sources extension + repos
4. **18.f.4** - NodeCitationService + Controller + IT
5. **18.f.5** - extract shared mini-reader (pure move + базовые tests)
6. **18.f.6** - PageView/PdfViewer selection props + tests
7. **18.f.7** - CitationPicker компонент + integration test
8. **18.f.8** - NodeCitationsSection two-button + click-to-navigate
9. **18.f.9** - BookReaderPage deep link handling + tests
10. **18.f.10** - manual playwright smoke + handoff
