# PDF Viewer - source-agnostic архитектура

**Дата:** 2026-05-11
**Сессия:** 25 (next)
**Статус:** design draft - детали обсуждаем в Сессии 25 с свежим контекстом
**Связан с:** ADR-021 (source-first), ADR-018 (платформенный pivot)

## Зачем

После закрытия source-first нумерации (Сессия 24, ADR-021) мы имеем
`lib_pages.pdf_page_number` колонку которая всегда NULL. Нужен этап
который:

1. Качает PDF оригинала для книги (из shamela на MVP)
2. Показывает frontend-viewer с конкретной страницей
3. Заполняет `pdf_page_number` в `lib_pages` для existing-страниц
4. (Опционально) позволяет выделять регион на скане для цитирования

**Архитектурный принцип** - source-agnostic. Не привязываемся к
shamela. Поддерживаем будущие источники без переписывания:
- **shamela** (сейчас) - `pdf_links` в `lib_books.metadata`
- **archive.org** (будущее, упомянуто пользователем) - прямой URL вида
  `https://archive.org/download/{itemId}/{filename}.pdf`. Не требует
  API key, public access
- **user-upload** (Этап 16) - MinIO storage, загрузка через
  multipart/form-data

UI и API не должны знать какой это source. Reader просто запрашивает
PDF для bookId - backend сам определяет где брать.

## Проблема 1 - PDF source как провайдер

### Решение - PdfSourceProvider interface

Spring-бин interface, реализаций может быть несколько:

```java
public interface PdfSourceProvider {
    /**
     * Может ли provider обслужить эту книгу. Например ShamelaProvider
     * возвращает true только если book.metadata содержит pdf_links.
     */
    boolean supports(Book book);

    /**
     * Метаданные PDF: количество файлов (книга может быть разбита
     * на тома), общее число PDF-страниц. Lazy - не качаем PDF.
     */
    PdfMetadata getMetadata(Book book);

    /**
     * Streaming download конкретного файла. fileIndex это индекс в
     * pdf_links (book может состоять из нескольких volumes-PDF
     * например). На MVP всегда 0.
     * Range header поддерживается через ResourceRegion / StreamingResponseBody.
     */
    InputStream downloadFile(Book book, int fileIndex);
}
```

`PdfMetadata`:
```java
public record PdfMetadata(
    int fileCount,           // сколько отдельных PDF-файлов
    List<PdfFileInfo> files  // metadata по каждому
) {}

public record PdfFileInfo(
    int index,
    String label,    // "Том 1" или filename
    Long sizeBytes,  // null если неизвестно до download
    Integer pageCount // null если неизвестно
) {}
```

Реализации:

1. **ShamelaPdfSourceProvider** - читает
   `book.metadata.shamela_book_id` + `book.metadata.pdf_links`,
   дёргает `ShamelaApiClient.downloadPdf(relativePath, targetDir)`
   (метод уже существует в коде - смотри ADR-020). Кеширует в MinIO
   после первой загрузки чтобы не качать повторно

2. **ArchiveOrgPdfSourceProvider** (Сессия 26+, не сейчас) - читает
   `book.metadata.archive_org_id` (новый ключ в metadata), строит
   URL вида `https://archive.org/download/{id}/{filename}.pdf`,
   проксирует через свой HTTP client

3. **MinioPdfSourceProvider** (Сессия 26+, для user-upload) - читает
   `book.metadata.minio_object_key`, streaming из MinIO напрямую

**PdfService** - роутер:
```java
@Service
public class PdfService {
    private final List<PdfSourceProvider> providers;

    public InputStream downloadFile(UUID bookId, int fileIndex) {
        Book book = bookRepository.findById(bookId).orElseThrow();
        for (PdfSourceProvider p : providers) {
            if (p.supports(book)) {
                return p.downloadFile(book, fileIndex);
            }
        }
        throw new PdfNotAvailableException(bookId);
    }
    // ...
}
```

## Проблема 2 - REST endpoints

```
GET /api/v1/library/books/{bookId}/pdf/info
  - Метаданные. 200 - { fileCount, files: [{index, label,
    sizeBytes, pageCount}] }. 404 - книга без PDF source.

GET /api/v1/library/books/{bookId}/pdf?fileIndex=0
  - Streaming PDF. Поддерживает Range header
    (для частичной загрузки). Content-Type: application/pdf.
  - На MVP - один файл (fileIndex=0). При multi-volume - фронт
    запрашивает несколько раз с разными fileIndex.
```

**Streaming через ResourceRegion + Range header.** Это критично для
производительности: PDF-Сахих аль-Бухари ~50MB, но reader открывает
страницу 47 - незачем качать весь файл. `react-pdf` (PDF.js)
запрашивает chunks по 64KB-256KB через Range header. Spring
поддерживает out-of-the-box если возвращать `ResponseEntity<ResourceRegion>`.

**Кеш**: после первого download через ShamelaProvider - кладём файл в
MinIO. Следующие запросы идут из MinIO напрямую (быстрее + offloads
shamela API). Eviction по LRU после 30 дней неиспользования (cron).

## Проблема 3 - Заполнение `pdf_page_number`

После download PDF мы должны связать каждую `lib_pages` запись с
номером физической страницы в PDF.

Сложность: matching `printed_page` (TEXT маркер реальной книги, может
быть "47", "أ", "iv") → `pdf_page_number` (physical page в PDF, 1..N)
**не тривиален**:

- Первые N страниц PDF обычно обложка/copyright/предисловие, без
  маркера или с римскими/арабскими буквами
- Реальная страница "1" книги начинается где-то на physical page 5-15
- Может быть несколько printed_page="1" в книге (предисловие потом
  основной текст)

**Решение - tier'ы стратегий matching, в порядке прироста сложности:**

Tier 1 (MVP): **manual mapping** - админ открывает PDF и admin-UI,
кликает "это physical page 5 в PDF = первая логическая стр". Дальше
автоматический shift всех остальных страниц = +4 offset. Работает
только для книг с непрерывной нумерацией без частей.

Tier 2: **парсинг PDF text layer + fuzzy matching на printed_page**.
PDF.js может извлечь текст каждой страницы. Берём первые 100 символов
каждой PDF page, ищем номер страницы (regex `(\d+)|أ|ب|ج`), сматчиваем
с `lib_pages.printed_page` через scoring. На манускриптах не работает
(text layer отсутствует).

Tier 3: **OCR через Tess4j** для PDF без text layer. После OCR - тот
же fuzzy match. Очень дорого для больших PDF, делаем по запросу.

**На MVP реализуем Tier 1.** Tier 2-3 - в backlog. Это **отдельный
admin-flow**, не блокирует reader-фичу: PDF Viewer работает с
`pdf_page_number=NULL`, frontend показывает PDF на physical page равной
internal pageNumber как fallback.

## Проблема 4 - Frontend PDF Viewer

### Стэк

**react-pdf** (`@react-pdf/renderer` или `pdfjs-dist` напрямую) -
стандарт react-сообщества. PDF.js работает через Web Worker (вне
main thread), поэтому большие PDF не блокируют UI.

**Альтернативы рассмотрены:**
- `pdfobject` - простой `<embed>` через браузерный PDF viewer.
  Минус: нет programmatic control над страницей/zoom, нет region
  selection
- `pdfviewer.org` - готовый iframe-embed. Минус: внешний host,
  privacy
- raw `pdfjs-dist` - max контроль, но больше кода. Возьмём
  `react-pdf` (обёртка) если он закрывает требования

### Архитектура компонента

```tsx
// PdfViewer.tsx
function PdfViewer({ bookId, currentPdfPage, onPageChange }: Props) {
    // Fetch PDF info при mount
    const { info, error } = useQuery(['pdf-info', bookId], ...);

    // PDF.js рендерит конкретную страницу
    return (
        <Document file={`/api/v1/library/books/${bookId}/pdf`}>
            <Page pageNumber={currentPdfPage} />
        </Document>
    );
}
```

**Reader toggle**: в reader header две кнопки
`<TabSwitch options={['📃 Текст', '📕 PDF']}>`. При выборе PDF -
рендерим PdfViewer вместо PageView.

**Page sync**:
- Internal `pageNumber` (URL state) остаётся sour of truth
- При render PdfViewer - получаем `currentPdfPage` =
  `state.pages[currentIndex].pdfPageNumber ?? internalPageNumber`
- Если NULL (Tier 1 не заполнен) - PDF показывает physical=internal
  как fallback
- Smart fallback: если первый PDF page без `pdfPageNumber`,
  показываем notification "PDF mapping недоступен, показываем
  по physical page"

### Оптимизация - как shamela

shamela показывает страницу за раз без подгрузки соседних. Не
implement infinite-scroll внутри PDF. Применяем same подход:

- Один `<Page>` за раз
- Lazy: при `currentPdfPage` change → react-pdf автомат запрашивает
  новый range через PDF.js worker
- Browser caches range responses (HTTP cache headers `Cache-Control:
  max-age`)
- prefetch next/prev page после inactivity 2-3 сек (optional, через
  IntersectionObserver или setTimeout)
- PDF.js worker сразу обрабатывает только requested page, остальное
  при page change

### Состояния

```tsx
type PdfState =
    | { kind: 'unavailable' }              // book без PDF source
    | { kind: 'loading-info' }
    | { kind: 'ready'; info: PdfMetadata }
    | { kind: 'error'; message: string };
```

Reader-toggle disabled на 📕 PDF если `kind: 'unavailable'`.

## Проблема 5 - Region selection (опционально, Сессия 26)

После того как PDF Viewer работает - region selection через
`react-image-crop`. Эта проблема **отдельный подэтап**, не делаем в
Сессии 25 вместе с PDF Viewer.

Архитектура:
- `<PdfViewer>` имеет overlay `<RegionOverlay>` поверх рендереной
  PDF страницы
- При mouse-drag - вычисляется bbox в нормализованных координатах
  (0..1)
- При release - открывается CitationPicker модалка с pageId +
  координатами региона
- POST `/api/v1/library/pages/{pageId}/regions` создаёт
  `lib_image_regions` запись с `x/y/width/height` + опциональным
  `extracted_text` (OCR или manual)

Этот flow позволяет:
1. Открыть Тафсир Ибн Касира → PDF
2. Найти страницу 47 в томе 1
3. Выделить хадис мышкой
4. CitationPicker откроется с регионом
5. Прикрепить к узлу argument-map с meta `"Том 1, стр 47, регион"`

## Декомпозиция Этапа 25

Подэтапы для Сессии 25 (и возможно Сессии 26):

### 25.a - Backend skeleton (Сессия 25, обязательно)
- `PdfSourceProvider` interface + `PdfMetadata`/`PdfFileInfo` records
- `ShamelaPdfSourceProvider` - читает `pdf_links` из
  `lib_books.metadata`, использует `ShamelaApiClient.downloadPdf`
  (уже существует)
- `PdfService` роутер
- `GET /api/v1/library/books/{id}/pdf/info` endpoint
- `GET /api/v1/library/books/{id}/pdf?fileIndex=0` endpoint с
  Range header support через `ResourceRegion`
- 5-7 IT через MockMvc + @MockitoBean ShamelaApiClient

### 25.b - MinIO infrastructure (Сессия 25, или вынести)
- `docker-compose.yml` - сервис minio + minio init bucket
- `application.yml` блок minio:
- `MinioCacheService` - put/get через AWS SDK S3
- Eviction job: cron 1/day, удаляет файлы >30 дней без access
- ShamelaProvider использует cache: при download, кладёт в MinIO;
  при повторном запросе - читает из MinIO

### 25.c - react-pdf install + базовый viewer (Сессия 25)
- npm install `react-pdf` + worker setup в vite.config.ts
- `PdfViewer.tsx` компонент
- Toggle `📃 Текст / 📕 PDF` в reader header (новый компонент
  `ReaderModeSwitch`)
- При выборе PDF - рендерится `<Document><Page /></Document>` для
  current page

### 25.d - Page sync (Сессия 25)
- При currentIndex change - вычислять
  `currentPdfPage = state.pages[currentIndex].pdfPageNumber ??
   internalPageNumber`
- Fallback notification "PDF mapping недоступен" если
  `pdfPageNumber=NULL`
- prev/next inside PDF mode тоже работает через
  `setPageNumber(internal)` → page sync

### 25.e - admin manual page-mapping (Tier 1, опционально, Сессия 26)
- Admin UI `/admin/library/books/{id}/pdf-mapping`
- Открывается PDF + список pages с `printedPage` маркерами
- Drag-and-drop или клик "это первая логическая страница"
- POST endpoint обновляет offset, обновляет все pages с
  `pdfPageNumber = physical_page - offset + page_number`

### 25.f - Region selection (Сессия 26+, отдельная сессия)
- `react-image-crop` overlay
- `RegionOverlay` поверх `<Page>` PDF.js
- POST `/api/v1/library/pages/{pageId}/regions`
- Координаты нормализованы (0..1), не пиксели
- Используется в CitationPicker (Этап 18.f)

## Действия для Сессии 25 - порядок

**Минимальная цель Сессии 25**: PDF Viewer работает на shamela-книге.
Пользователь может toggle 📃/📕, увидеть PDF Сахих аль-Бухари на
текущей странице, листать prev/next, и закрыть. Region selection -
отдельная сессия.

Порядок реализации (~ один день в режиме автономии):
1. 25.a backend skeleton + 25.b MinIO infrastructure - 30-40% работы
2. 25.c frontend react-pdf + 25.d page sync - 40-50% работы
3. Live-проверка: импортированная книга 1681 → toggle PDF →
   страница 47 отображается, prev/next работает

Перед стартом - Абдула должен:
- Решить порядок 25.f (Region) vs 18.f (CitationPicker). Я
  рекомендую **сначала текстовый CitationPicker** (18.f) в Сессии
  25 после PDF Viewer (если время остаётся), region добавим в 18.f
  потом. Это даёт CitationPicker который сразу работает на текст-
  выделение в reader, region - бонус для тех страниц где scan
  лучше текста (manuscripts)
- Подтвердить выбор `react-pdf` vs custom pdfjs-dist
- Подтвердить MinIO в docker-compose (новая зависимость
  инфраструктуры)

## Будущие источники PDF

### archive.org
- Public PDF без API key. URL формат: `https://archive.org/download/{id}/{filename}.pdf`
- ID получаем через ручную привязку в admin UI или через парсинг
  `https://archive.org/details/{id}` страницы
- В `lib_books.metadata`: новый ключ `archive_org_id` (если книга
  привязана к archive.org-источнику)
- `ArchiveOrgPdfSourceProvider` реализуется когда станет важно
  (Сессия 27+)

### user-upload (Этап 16)
- multipart/form-data на `POST /api/v1/library/imports/file`
- Apache Tika для metadata extraction
- MinIO storage
- В `lib_books.metadata`: `minio_object_key`
- `MinioPdfSourceProvider` реализуется в Этапе 16

### потенциальные другие
- waqfeya.com / IslamicBook.ws / dorar.net - часто имеют PDF, но
  закрытый/коммерческий доступ. Откладываем
- Manuscript libraries (King Saud, Princeton Islamic Manuscripts) -
  IIIF API. Это совсем другой стандарт, отдельный этап

## Ссылки

- ADR-021 - source-first архитектура
- ADR-020 - shamela ETL + downloadPdf уже существует
- Дизайн-референс `frontend/design-reference/project/platform_reader.jsx` -
  ReaderHeader, PageToolbar дают вдохновение для toggle 📃/📕
- shamela `pdf_icon_screen.png` + `pdf_click_open_screen.png` - как
  выглядит shamela paginator с PDF-кнопкой
- react-pdf docs: https://github.com/wojtekmaj/react-pdf
- PDF.js docs: https://mozilla.github.io/pdf.js/
