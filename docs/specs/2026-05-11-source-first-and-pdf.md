# Source-first page numbering + Sub-chapters + PDF integration

**Дата:** 2026-05-11
**Сессия:** 24 (next)
**Статус:** design draft - детали обсуждаем в Сессии 24 с свежим контекстом

## Зачем

После UX-проверки Library shamela MVP (Сессия 23) выявлены три
взаимосвязанные проблемы которые требуют архитектурного решения
до того как импортировать ещё книги или строить CitationPicker:

1. **Page numbering не соответствует оригиналу книги**
2. **Sub-chapters теряются при импорте**
3. **Нужна поддержка PDF/scan оригиналов с region-selection**

Принципиально это всё про **source-first**: электронная версия книги
должна точно соответствовать оригиналу (бумажному изданию), чтобы
ссылки в argument-map / Q&A могли быть проверены в реальной книге
того же издания.

## Проблема 1 - page numbering

### Симптом
Скриншоты: `ours_wrong_page_num.png` vs `shamela_right_page.png`

На нашем фронте (`/books/{uuid}`) для книги Тафсир Ибн Касира
показывается "Страница **1** / 4710", а на shamela paginator
показывает `ج: المقدمة, ص: 3` (раздел: المقدمة, страница 3) - и
PDF-viewer открывает физическую страницу с маркером "أ" (это
"первая страница предисловия" в книжной нумерации).

То есть shamela page numbering это **multi-dimensional**:
- `ج` (juz' = том/раздел) - dropdown с разделами книги
- `ص` (safha = страница) - input, в пределах juz'

У нас же plain `1...N` где N это sequential id из `shamela_page.id`
(internal counter shamela).

### Причина

В `ShamelaToLibraryMapper.mapPages` я взял `shamela_page.id` как
`lib_pages.page_number`. Это **internal sequential counter shamela**,
а не "page number в реальной книге".

Реальные поля в `lib_shamela_page`:
- `id` - internal counter (1..N), уникален в книге
- `printed_page` - **TEXT** маркер страницы в реальной книге
  (может быть "1", "3", "أ" (арабская буква), "ج" том/juz)
- `part` - **TEXT** для multi-volume книг (например "1", "2", "أ")
- `number` - ещё какой-то shamela-internal number

`printed_page` и `part` я в Mapper полностью проигнорировал.

### Решение (предложение)

**Расширить `lib_pages`** через миграцию 19:

```sql
ALTER TABLE lib_pages ADD COLUMN printed_page TEXT NULL;
ALTER TABLE lib_pages ADD COLUMN part TEXT NULL;
ALTER TABLE lib_pages ADD COLUMN pdf_page_number INTEGER NULL;
```

- `printed_page` TEXT - маркер из реального издания (для отображения
  пользователю). Может быть число, арабская буква, римское число.
  TEXT потому что shamela это уже TEXT хранит, не приведено к int
- `part` TEXT - том/juz' для multi-volume. Nullable - однотомные
  не нуждаются
- `pdf_page_number` INTEGER - страница в PDF исходного издания
  (для cross-referencing когда PDF подключён). Заполняется в
  будущем (Этап 16)
- `page_number` INTEGER - оставляем как есть (internal sequence
  для navigation/sort)

**Изменения в коде:**
- `Page` record + `PageRepository` + `PageResponse` + `PageSummary` -
  добавить новые поля
- `ShamelaToLibraryMapper.mapPages` - заполнять `printed_page` и
  `part` из `shamela_page.printedPage`/`shamela_page.part`
- Frontend `BookReaderPage.PageJump` - отображать `printed_page`
  вместо `pageNumber` если он есть; navigation через internal
  `pageNumber` остаётся (для prev/next и URL state)
- Frontend `BookReaderPage` - dropdown selector для `part` если в
  книге больше одного part

**Open question** для обсуждения в Сессии 24:
- Может быть проще хранить **composite display**:
  `display_page_label TEXT` (например "ج 1, ص 5") который Mapper
  собирает один раз. Это упростит frontend - просто рендерить
  label
- Или хранить compound в metadata jsonb (gибкость, без миграции)?
- Решение зависит от того насколько часто будем делать query по
  part/printed_page (если часто - колонки + индексы; редко - jsonb)

## Проблема 2 - Sub-chapters потеряны

### Симптом
Скриншоты: `shamela_chapter_levels.png` (многоуровневый tree с
кнопками `[+]` для раскрытия) vs `our_chapters.png` (плоский список
первого уровня).

В shamela у книги Тафсир Ибн Касира видно:
- `+ مقدمة المحقق` (collapsible)
- `- مقدمة الناشر` (раскрыто)
  - `- أسباب تحقيق الكتاب` (sub-item)
  - `- الفصل الأول ترجمة مختصرة للحافظ ابن كثير...` (sub-item)
- `[+] الفصل الثاني دراسة مختصرة للتفسير` (раскрытый)

У нас в `/books/{uuid}` side-panel только flat список первого уровня
без иерархии.

### Причина (требует диагностики в Сессии 24)

Mapper'е `ShamelaToLibraryMapper.mapChapters` строит BFS-tree:
```java
for (ShamelaTitleRow t : titles) {
    if (t.parentId() == null || !byId.containsKey(t.parentId())) {
        queue.add(t);  // root
    } else {
        children.computeIfAbsent(t.parentId(), k -> new ArrayList<>()).add(t);
    }
}
```

Возможные причины bug:
1. **`parentId = 0` в shamela** - не `null`. Я делаю null-check но
   0 проходит как valid parent → orphan check `!byId.containsKey(0)`
   делает все titles корнями
2. **`parent_id` semantics другая** - может быть не chapter-id, а
   page-id или какой-то другой ref
3. **shamela_title.parent_id хранится как TEXT, не INTEGER**, и
   parsing где-то слажал

**Curl-диагностика для Сессии 24:**

```bash
# Посмотреть raw shamela_title для книги 1681 (Сахих аль-Бухари)
docker exec argumentmap-postgres psql -U argmap -d argumentmap \
  -c "SELECT id, parent_id, content, page_ref FROM lib_shamela_title \
      WHERE book_id = 1681 ORDER BY id LIMIT 20;"
```

Распределение parent_id:
```sql
SELECT parent_id, COUNT(*) FROM lib_shamela_title
WHERE book_id = 1681 GROUP BY parent_id ORDER BY COUNT(*) DESC LIMIT 10;
```

Если все `parent_id = 0` или `NULL` - shamela не даёт иерархию через
это поле, и иерархия выражена иначе (например через `content`
markers или порядок id).

Если `parent_id` имеет содержательные значения но я их теряю -
fix в `parseIntegerOrNull` или в логике BFS.

### Решение

После диагностики - либо fix BFS в Mapper'е, либо альтернативный
способ извлечения иерархии (если parent_id неинформативен).

## Проблема 3 - PDF integration (новое требование)

### Цель

Каждая электронная книга должна **ссылаться на оригинал** (PDF/scan).
При наличии PDF - пользователь может:
- Открыть PDF-viewer для конкретной страницы (cross-reference)
- Выделить участок текста на скане для цитирования в argument-map / Q&A
- Видеть номер физической страницы в книге (printed_page + part)

Это новое архитектурное требование - **source-first**: электронный
текст это репрезентация оригинала, не отдельная сущность.

### Что есть

Backend (ADR-020):
- `ShamelaApiClient.downloadPdf(relativePath, targetDir)` - метод
  существует, готов к использованию
- `lib_books.metadata` JSONB содержит `pdf_links` (raw shamela json
  со списком файлов и URL'ов)
- В roadmap.md Этап 17 "image-сканы + OCR" с Tess4j, ImageRegion API,
  async OCR pipeline - но это OCR, не just PDF viewer

Frontend - **ничего** про PDF пока

### Решение (предложение для Сессии 24+)

**Этап 19 (новый): PDF Viewer + Region Selection**

(Возможно стоит вставить ДО Этапа 16 PDF/EPUB upload потому что
shamela уже имеет PDF и это блокер для CitationPicker quality)

**Backend:**
1. Endpoint `GET /api/v1/admin/shamela/book/{id}/pdf/{fileIndex}` -
   lazy PDF download через `StreamingResponseBody` + cleanup
   tempfile (уже в backlog 15.6)
2. Endpoint `POST /api/v1/library/pages/{id}/regions` - создать
   ImageRegion (тип уже в схеме `lib_image_regions` из миграции 16)
3. `Page.pdfPageNumber` - связь page → PDF страница

**Storage:**
- MinIO для cached PDF (чтобы не качать каждый раз) - Этап 16-related
- Или nginx-served статика из shamela download-dir на MVP
- Решить в Сессии 24

**Frontend:**
- Page reader получает **toggle** "📕 PDF" / "📃 Текст"
- При выборе PDF - открывается `react-pdf` viewer на нужной странице
- Над PDF - overlay `react-image-crop` для выделения регионов
- При выделении - modal "Привязать цитату" (CitationPicker 18.f)
  с region координатами + page id
- Лучше сначала **минимальный PDF viewer без region selection**,
  потом добавить selection в Этап 17 OCR

**Уточнить в Сессии 24:**
- React-pdf vs PDF.js напрямую - реакт-обёртка может ломаться на
  больших PDF (Сахих аль-Бухари scan ~50MB)
- Worker thread для PDF rendering
- Где хранить PDF - MinIO (overkill для MVP?) или nginx serve

## Action plan для Сессии 24

1. **Pre-flight** (Абдула делает руками перед Claude'ом):
   - Postgres + backend (миграция 18 уже применена, перезапуск
     достаточен если бэк упал)
   - Frontend dev server
   - Открыть spec этот файл

2. **Диагностика sub-chapters** (1 SQL запрос) - понять реальную
   semantics parent_id в shamela. Может оказаться что bug чисто
   в Mapper'е и fix небольшой

3. **Миграция 19 + Mapper update для printed_page/part** - средняя
   задача. Это самый частый use case (любая shamela книга имеет
   эти поля)

4. **Frontend display printed_page вместо internal pageNumber** -
   маленький фикс если backend готов. Navigation остаётся на
   `pageNumber` для URL state и prev/next, отображение - на
   `printed_page` если есть

5. **PDF integration** - **отдельная сессия**, не пытаться в одной
   с (3)+(4). Нужно проектировать storage, viewer, region API
   независимо

### Что НЕ делать в Сессии 24

- **Не делать CitationPicker (18.f)** до закрытия (1)-(4). Цитирование
  должно ссылаться на правильный page-marker, иначе придётся
  переделывать ссылки в БД
- **Не импортировать ещё книги** (Тафсир, Хусн аль-максыд) до
  миграции 19 - они получат старую неправильную нумерацию и
  потребуют повторного импорта
- Bulk vs lazy решение - откладывается до PDF integration (Этап 19?)

## Сценарии использования (для проверки решения)

После Сессии 24 пользователь должен мочь:

1. **Найти цитату в реальной книге**: видеть в reader "Том 1,
   страница 47" → открыть свою бумажную копию Сахих аль-Бухари
   издания "ат-Тыба ас-Султания" → найти ту же страницу 47 в томе 1
2. **Ориентироваться по chapter hierarchy**: видеть в side-panel
   многоуровневый tree → кликать на под-главу → переход на её
   первую страницу
3. **(будущее, Этап 19+)** Открыть PDF-viewer для текущей страницы,
   выделить участок и сослаться на него из argument-map узла

## Ссылки

- Скриншоты в `/mnt/c/my_folders/projects/argument-map/`:
  `ours_wrong_page_num.png`, `shamela_right_page.png`,
  `shamela_chapter_levels.png`, `our_chapters.png`,
  `pdf_icon_screen.png`, `pdf_click_open_screen.png`
- ADR-020 ETL shamela
- ADR-018 платформенный pivot
- Этап 16 PDF/EPUB upload в roadmap.md (нужно переоценить
  приоритет в свете этого spec)
- Этап 17 image-сканы + OCR в roadmap.md
