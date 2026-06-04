---
name: library-page-rendering
description: >
  Use when working with library page rendering pipeline, OCR statuses, PDF viewing,
  image scans, or formatted content. Triggers on: lib_pages, PDF viewer, OCR pipeline,
  OCR status, AI edit status, render mode, page rendering, ImagePageRenderer,
  text_content, formatted_content, ai_edit_status, ocr_status, PdfViewer, PageView,
  BookReaderPage, scanned book, Tesseract, ANTHROPIC_API_KEY, AiEditService, OcrService,
  PdfController, PdfService, page stuck PROCESSING, library page blank.
  Path scope: backend/src/main/java/ru/basnukaev/argumentmap/library/,
  frontend/src/apps/library/, frontend/src/shared/components/reader/.
  Always use when debugging OCR/AI-edit failures or adding a new render mode — the
  state machine and provider chain have non-obvious rules that are easy to get wrong.
---

# Library Page Rendering

Этот skill описывает pipeline рендеринга страниц в library-домене: три активных режима
(PDF / OCR text / AI-edited formatted) плюс один запланированный (Image mode, Этап 18.e).
State machine `lib_pages` управляет жизненным циклом контента каждой страницы.

---

## 1. Overview — режимы рендеринга

| Режим | Условие показа | Backend source | Frontend компонент |
|---|---|---|---|
| **PDF passthrough** | Книга имеет PDF; юзер выбрал PDF mode | `PdfController` → `PdfService.openStream` → lazy provider | `PdfViewer` (react-pdf, lazy-loaded) |
| **OCR text** | `ocr_status = DONE`, `text_content` непустой, `formatted_content = null` | `GET /api/v1/library/pages/{id}` → `PageResponse.textContent` | `PageView` → sanitized HTML render |
| **AI-edited formatted** | `ai_edit_status = DONE`, `formatted_content` содержит ProseMirror JSON | то же, `PageResponse.formattedContent` | `PageView` → `RichTextRenderer` (custom Tiptap extensions) |
| **Image mode** *(planned, Этап 18.e)* | Скан есть (`imageUrl` / `imageStorageKey` заполнен), OCR poor quality | `GET /api/v1/library/pages/{id}` → `PageResponse.imageUrl` | `PageView` уже рендерит `<img>` если `imageUrl` filled; будущий `ImagePageRenderer` расширит это |

**Приоритет показа в `PageView`:**

1. `page.formattedContent` (не null) → `RichTextRenderer` (AI-edited, высший приоритет)
2. `page.textContent` непустой → sanitized HTML render (DOMPurify)
3. `page.imageUrl` (не null) → `<img>` скан
4. Ничего из вышеперечисленного → «страница пуста» placeholder

**PDF mode** — отдельный `readerMode` state в `BookReaderPage`, не часть `PageView`.
Юзер переключает через `ReaderModeSwitch`. В PDF mode весь content-area заменяется
`<PdfViewer>` (lazy Suspense).

---

## 2. State machine `lib_pages`

### OCR status (`ocr_status` VARCHAR, CHECK constraint миграция 34)

```
NULL        → не применимо (PDF-import, нет скана)
PENDING     → скан загружен, OCR ещё не запускался
PROCESSING  → OCR в работе (tess4j async task)
DONE        → text_content заполнен Tesseract output
FAILED      → exception в pipeline, text_content не изменён
```

**Idempotency:** перезапуск из любого состояния допустим — UPDATE SET ocr_status='PENDING'
очищает путь для re-OCR. `text_content` перезаписывается при DONE.

### AI edit status (`ai_edit_status` VARCHAR, CHECK constraint миграция 35)

```
NULL        → AI edit не запускался
PENDING     → в очереди (precondition: ocr_status = DONE)
PROCESSING  → AiEditService.enhanceAsync выполняется
DONE        → formatted_content содержит валидный ProseMirror JSON
FAILED      → ошибка Anthropic API / Resilience4j exhausted / невалидный JSON
```

**Precondition:** AI edit требует `ocr_status = DONE`. Без этого `enhanceAsync` откажет.

### Поля `lib_pages` — ключевые

| Поле | Тип | Семантика |
|---|---|---|
| `text_content` | TEXT nullable | OCR raw output. `""` (empty string) допустим для scanned-PDF без текста — CHECK constraint проходит. NULL = OCR не запускался |
| `formatted_content` | JSONB nullable | ProseMirror JSON при `ai_edit_status = DONE`. NULL = не редактировалось |
| `image_url` | TEXT nullable | URL или key скана для image mode render |
| `image_bucket` / `image_storage_key` | TEXT nullable | MinIO pointer для скана. Вместе с `image_uploaded_at` либо все заполнены, либо все NULL |
| `ocr_started_at` / `ocr_completed_at` | TIMESTAMPTZ | Для observability и диагностики stuck PROCESSING |
| `ai_edit_started_at` / `ai_edit_completed_at` | TIMESTAMPTZ | Аналогично для AI edit |

### State transitions cheatsheet

| From | To | Triggered by |
|---|---|---|
| OCR `PENDING` | `PROCESSING` | `OcrService.recognizeAsync` (async ThreadPoolExecutor) |
| OCR `PROCESSING` | `DONE` | Tesseract success + `text_content` saved |
| OCR `PROCESSING` | `FAILED` | Tesseract exception / timeout |
| OCR `FAILED` | `PROCESSING` | manual `UPDATE ... SET ocr_status='PENDING'` + retrigger |
| AI_EDIT `PENDING` | `PROCESSING` | `AiEditService.enhanceAsync` (precondition: ocr DONE + ANTHROPIC_API_KEY set) |
| AI_EDIT `PROCESSING` | `DONE` | Anthropic response → valid ProseMirror JSON saved |
| AI_EDIT `PROCESSING` | `FAILED` | Anthropic error / Resilience4j max retries / invalid JSON response |
| AI_EDIT `FAILED` | `PROCESSING` | manual `UPDATE ... SET ai_edit_status='PENDING'` + retrigger |

---

## 3. Common workflows

### 3.1 Добавить новый режим рендеринга (например Image mode, Этап 18.e)

**Pre-implementation checklist:**

- [ ] State machine схема актуальна? Нужен ли новый статус в `lib_pages`?
- [ ] Backend service для mode имеет async pattern + state transitions?
- [ ] Frontend rendering знает про mode selector (`ReaderModeSwitch`)?
- [ ] IT tests покрывают новый mode?
- [ ] Topical docs обновлены если backend logic изменился?

**Высокоуровневый scaffold:**

1. **Domain:** добавить поля в `Page.java` если нужны новые данные (напр. `imageRegionId`).
   Liquibase-миграция если нужна новая колонка.
2. **Backend:** новый service/endpoint для mode-specific stream (пример: `PageImageController`
   для image render). Или расширить `PageRepository` если данные уже есть.
3. **Frontend `PageView.tsx`:** добавить ветку рендеринга после `formattedContent`:
   ```tsx
   {page.imageUrl && (
     <ImagePageRenderer imageUrl={page.imageUrl} pageNumber={page.pageNumber} />
   )}
   ```
4. **`ReaderModeSwitch.tsx`:** добавить новый mode option если mode переключаемый.
5. **IT тест:** покрыть новый mode в `PageControllerIT` или соответствующем IT.
6. **Документация:** обновить `docs/api-contract.md` если новый endpoint.

### 3.2 Debug OCR не запускается / stuck для конкретной страницы

```sql
-- 1. Проверить текущий статус
SELECT id, ocr_status, ocr_started_at, ocr_completed_at, text_content IS NULL as no_text
FROM lib_pages
WHERE id = '<page-id>';
```

**Диагностическое дерево:**

- `ocr_status = NULL` → страница импортирована из PDF (не скан), OCR не применим
- `ocr_status = PENDING` → задача ещё не была взята в работу. Проверить:
  - backend запущен? `curl -sf http://localhost:9090/actuator/health`
  - `OcrService.recognizeAsync` вызывался? Grep лог: `grep "OCR" /tmp/backend.log`
- `ocr_status = PROCESSING` давно (>10 минут) → crash async task. Действие:
  ```sql
  UPDATE lib_pages SET ocr_status = 'PENDING', ocr_started_at = NULL WHERE id = '<page-id>';
  ```
  Затем retrigger через `POST /api/v1/library/pages/{id}/ocr` (если endpoint есть)
  или перезапустить backend
- `ocr_status = FAILED` → проверить `/tmp/backend.log`:
  - `tesseract not found` → Tesseract не установлен (см. `backend/docs/ocr-pipeline.md`)
  - `traineddata not found` → нет `ara.traineddata` / `rus.traineddata` / `eng.traineddata`
  - Transient error → reset и retry (см. выше)

**Установить Tesseract (Debian/WSL2):**
```bash
sudo apt install tesseract-ocr tesseract-ocr-ara tesseract-ocr-rus tesseract-ocr-eng
# Проверить путь к traineddata:
ls /usr/share/tesseract-ocr/*/tessdata/*.traineddata
```

Подробнее: `backend/docs/ocr-pipeline.md`

### 3.3 Debug AI edit broken для конкретной страницы

```sql
-- 1. Проверить precondition
SELECT id, ocr_status, ai_edit_status, formatted_content IS NULL as no_fmt
FROM lib_pages
WHERE id = '<page-id>';
```

**Требования для AI edit:**
- `ocr_status = DONE` (без этого AI edit не запустится)
- `ANTHROPIC_API_KEY` установлен в env (иначе backend вернёт 503 `ai-edit-not-configured`)

**Диагностика:**

1. `ocr_status != DONE` → сначала дождаться/исправить OCR pipeline (см. 3.2)
2. `ai_edit_status = FAILED` → проверить лог:
   - `ai-edit-not-configured` → установить `ANTHROPIC_API_KEY` и перезапустить backend
   - `AnthropicApiException: 429` → rate limit, retry сам через Resilience4j exponential backoff
   - `invalid ProseMirror JSON` → проблема в prompt template (`resources/prompts/ai-edit-tahqiq.txt`)
3. `ai_edit_status = PROCESSING` давно → crash. Reset:
   ```sql
   UPDATE lib_pages SET ai_edit_status = 'PENDING', ai_edit_started_at = NULL
   WHERE id = '<page-id>';
   ```

**Smoke test через curl** (после установки ANTHROPIC_API_KEY + рестарт):
```bash
PAGE_ID=$(psql -h localhost -U argmap argumentmap -tA \
  -c "select id from lib_pages where ocr_status='DONE' limit 1")
# Trigger
curl -X POST "http://localhost:9090/api/v1/library/pages/${PAGE_ID}/ai-edit" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001"
# Poll status
curl "http://localhost:9090/api/v1/library/pages/${PAGE_ID}/ai-edit" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001"
```

Подробнее: `backend/docs/ai-editing.md`

### 3.4 Debug PDF streaming 500 / timeouts

```bash
# 1. Проверить доступность backend
curl -sf http://localhost:9090/actuator/health

# 2. Smoke test PDF info endpoint
curl "http://localhost:9090/api/v1/library/books/<bookId>/pdf/info" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001"

# 3. Smoke test PDF stream (Range request как PDF.js)
curl -v -H "Range: bytes=0-65535" \
  "http://localhost:9090/api/v1/library/books/<bookId>/pdf?fileIndex=0" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" -o /dev/null
```

**Типичные причины:**

- **archive.org недоступен** → cache miss path пытается fetch с archive.org, timeout.
  Решение: загрузить PDF в MinIO (`UserUploadProvider`) или проверить internet access в WSL2
- **416 Range Not Satisfiable** → suffix-range запрос (`bytes=-N`). `PdfController` явно
  отклоняет suffix ranges. PDF.js не делает suffix ranges — значит кастомный клиент или
  тест проблема. Смотреть `RangeNotSatisfiableException.unsupportedSuffix()`
- **500 при Range request в тестах** → известная проблема `MockHttpServletResponse` и
  `ConcurrentModificationException` при async dispatch. Смотреть `PdfControllerIT`
  паттерн async dispatch workaround (Сессия 47 backlog)
- **Empty stream для scanned book** → текст не в PDF (OCR нужен), не PDF text extraction.
  Смотреть `ocr_status` страниц книги

---

## 4. Frontend rendering switch — детали

### `BookReaderPage.tsx`

Управляет `readerMode: 'text' | 'pdf'` state. Переключение через `ReaderModeSwitch`.

- `readerMode = 'text'` → рендерит `<PageView>` с текущей страницей
- `readerMode = 'pdf'` → рендерит `<PdfViewer>` (lazy Suspense)
- Inline PDF preview overlay (`pdfPreviewOpen`) → `<PdfViewer>` в bottom-sheet поверх text mode

**Mapping shamela → PDF:** `lib_pages.part` (том) → `fileIndex` в PdfViewer; `lib_pages.printed_page` → `initialPrintedPage`.

### `PageView.tsx` — рендеринг контента

```
page.formattedContent (не null)  →  <RichTextRenderer> с 8 custom extensions
  - HadithBox, AyahBox, Marginalia, Footnote
  - ColorHighlight, Tashkeel, DecoratedHeading, PageNumber
page.textContent (непустой)      →  sanitized HTML (DOMPurify) в <article>
page.imageUrl (не null)          →  <img> скан
ничего нет                       →  placeholder «страница пуста»
```

**Tashkeel toggle:** кнопка «убрать/показать огласовки» для арабского контента.
В `formattedContent` path работает через `stripTashkeelFromDoc` (JSON transform);
в legacy `textContent` path через `stripTashkeelText` (regex на raw text перед sanitize).

### `PdfViewer.tsx` — PDF streaming

Использует `react-pdf` (pdfjs-dist). Backend endpoint:
- `GET /api/v1/library/books/{bookId}/pdf` — stream (Range requests)
- `GET /api/v1/library/books/{bookId}/pdf/info` — metadata (fileIndex, label, pageCount)

**Chunk size cap:** backend обрезает Range до 1MB. PDF.js делает серию Range запросов.

**Lazy loading:** `PdfViewer` lazy-loaded через `React.lazy` (react-pdf ~600KB gzipped).

---

## 5. Files cheat sheet

| Что | Где |
|---|---|
| PDF service | `backend/src/main/java/.../library/pdf/service/PdfService.java` |
| PDF controller | `backend/src/main/java/.../library/pdf/web/PdfController.java` |
| PDF source providers | `backend/.../library/pdf/service/PdfSourceProvider.java` (interface), `UserUploadProvider.java`, `PdfLinksSourceProvider.java`, `HttpClientPdfFetcher.java` |
| OCR service | `backend/src/main/java/.../library/imports/OcrService.java` |
| AI edit service | `backend/src/main/java/.../library/imports/AiEditService.java` |
| Anthropic HTTP client | `backend/.../library/imports/AnthropicClient.java` |
| AI edit prompt | `backend/src/main/resources/prompts/ai-edit-tahqiq.txt` |
| Page domain record | `backend/src/main/java/.../library/domain/Page.java` |
| OcrStatus constants | `backend/.../library/domain/OcrStatus.java` |
| AiEditStatus constants | `backend/.../library/domain/AiEditStatus.java` |
| Page repository | `backend/src/main/java/.../library/repository/PageRepository.java` |
| Frontend PDF viewer | `frontend/src/shared/components/reader/PdfViewer.tsx` |
| Frontend page view | `frontend/src/shared/components/reader/PageView.tsx` |
| Frontend book reader | `frontend/src/apps/library/pages/BookReaderPage.tsx` |
| Reader mode switch | `frontend/src/shared/components/reader/ReaderModeSwitch.tsx` |
| Topical docs (OCR) | `backend/docs/ocr-pipeline.md` |
| Topical docs (AI edit) | `backend/docs/ai-editing.md` |

---

## 6. Common errors table

| Ошибка | Симптом | Решение |
|---|---|---|
| OCR stuck PROCESSING | `ocr_status = PROCESSING` >10 минут, нет прогресса | Сервис упал. `UPDATE lib_pages SET ocr_status='PENDING'` + retrigger |
| AI edit вернул не-ProseMirror JSON | `ai_edit_status = FAILED`, лог `invalid JSON` | Prompt template дал неструктурированный ответ. Проверить `ai-edit-tahqiq.txt`, запустить `AiEditServiceLiveIT` вручную |
| PDF 416 Range Not Satisfiable | `416` при Range запросе | Suffix-range (`bytes=-N`) не поддерживается. PDF.js не делает suffix ranges — проблема в custom клиенте/тесте |
| PDF пустой контент для scanned book | Книга есть, страницы есть, `textContent = null/""` | Для сканов текст не в PDF — нужен OCR pipeline, не PDF text extraction. Проверить `ocr_status` |
| Frontend `BookReaderPage` blank | Страница открывается, content не показывается | Проверить: 1) `pageContent` state loading/error; 2) `page.textContent` и `page.formattedContent` оба null; 3) console errors про RichTextRenderer extensions |
| Tesseract `trained data not found` | OCR → FAILED, лог `tessdata` ошибка | Не установлены языковые пакеты. `apt install tesseract-ocr-ara tesseract-ocr-rus tesseract-ocr-eng` |
| AI edit вернул 503 | `{"type":"ai-edit-not-configured"}` | `ANTHROPIC_API_KEY` не установлен. Установить + перезапустить backend |
| `text_content = ""` проходит CHECK | Ожидали ошибку БД при пустой строке | CHECK constraint допускает `""`. Скан без распознанного текста — valid state |

---

## 7. Примеры

### Пример 1: Добавление Image mode (Этап 18.e scaffold)

Цель: рендерить страницы как скан-изображения напрямую когда OCR плохого качества.

**Backend** — данные уже есть в `Page.imageUrl` / `imageStorageKey`. Если нужен
отдельный endpoint для image stream через MinIO (а не presigned URL):

```java
// PageImageController (уже существует в library/imports/web/)
@GetMapping("/{pageId}/image")
public ResponseEntity<StreamingResponseBody> streamImage(@PathVariable UUID pageId) {
    // аналогично PdfController но для MinIO library-page-images bucket
}
```

**Frontend `PageView.tsx`** — уже рендерит `imageUrl` если присутствует:
```tsx
{page.imageUrl && (
  <ImagePageRenderer imageUrl={page.imageUrl} pageNumber={page.pageNumber} />
)}
```

Для Этапа 18.e — создать `ImagePageRenderer` component с zoom/pan для удобства.

**`ReaderModeSwitch`** — добавить `'image'` option если image mode переключаемый.

**IT тест:**
```java
@Test
void getPage_withImageUrl_returnsImageUrl() throws Exception {
    // create page с imageBucket/imageStorageKey...
    mockMvc.perform(get("/api/v1/library/pages/{id}", pageId)...)
           .andExpect(jsonPath("$.imageUrl").isNotEmpty());
}
```

---

### Пример 2: Debug страница stuck PROCESSING (реальный workflow)

Ситуация: страница `lib_pages` имеет `ocr_status = PROCESSING` уже час.

```sql
-- Шаг 1: проверить
SELECT id, ocr_status, ocr_started_at,
       now() - ocr_started_at as stuck_duration,
       text_content IS NULL as no_text
FROM lib_pages
WHERE id = 'e4f2b1c3-...';
-- Результат: ocr_status = PROCESSING, stuck_duration = 01:12:34
```

```bash
# Шаг 2: проверить лог на ошибки
grep -i "ocr\|tesseract\|OcrService" /tmp/backend.log | tail -20
# Если видим: "ERROR OcrService - OCR failed for page e4f2b1c3-..."
# → service упал на этой задаче
```

```sql
-- Шаг 3: reset в PENDING
UPDATE lib_pages
SET ocr_status = 'PENDING',
    ocr_started_at = NULL
WHERE id = 'e4f2b1c3-...';
```

```bash
# Шаг 4: retrigger через API (если endpoint существует)
curl -X POST "http://localhost:9090/api/v1/library/pages/e4f2b1c3-.../ocr" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001"

# Шаг 5: мониторить
watch -n 5 'psql -h localhost -U argmap argumentmap -c \
  "SELECT ocr_status, ocr_started_at FROM lib_pages WHERE id='"'"'e4f2b1c3-...'"'"'"'
```

---

### Пример 3: `text_content = ""` для scanned PDF — почему CHECK constraint не ругается

**Ситуация:** книга импортирована как скан (PDF → image extraction), Tesseract не нашёл
текст (чистый скан арабского каллиграфического шрифта). OCR вернул пустую строку.
Разработчик ожидал `text_content = NULL`, но видит `text_content = ""`.

**Почему:** CHECK constraint в миграции 34 допускает пустую строку:
```sql
-- CHECK constraint (примерно):
CONSTRAINT lib_pages_text_content_len CHECK (text_content IS NULL OR length(text_content) >= 0)
-- Пустая строка: IS NULL → false, length("") = 0 >= 0 → true
```

**Frontend поведение:** `PageView` проверяет `text && (рендер)` — empty string falsy в JS,
поэтому `text = ""` ведёт себя как null. Страница показывает placeholder «страница пуста»
или `imageUrl` если есть скан.

**Правильный запрос для «страниц без текста»:**
```sql
SELECT COUNT(*) FROM lib_pages
WHERE ocr_status = 'DONE' AND (text_content IS NULL OR text_content = '');
-- Эти страницы — кандидаты для Image mode render
```

---

## 8. Pre-implementation checklist (добавление mode / debug)

Перед любой работой с rendering pipeline:

- [ ] Текущее состояние `lib_pages` для нужных страниц проверено? (SELECT ocr_status, ai_edit_status)
- [ ] Backend сервис для нового mode имеет async pattern + state transitions?
- [ ] `Page.java` domain record достаточен или нужны новые поля + Liquibase-миграция?
- [ ] Frontend `PageView.tsx` знает про новый mode selector?
- [ ] `ReaderModeSwitch` обновлён если mode переключаемый пользователем?
- [ ] IT тесты покрывают happy path + failure cases нового mode?
- [ ] Topical docs обновлены (`backend/docs/ocr-pipeline.md` / `ai-editing.md`)?
- [ ] `docs/api-contract.md` обновлён если новый endpoint?
