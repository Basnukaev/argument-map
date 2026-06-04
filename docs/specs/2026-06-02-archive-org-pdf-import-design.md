# Импорт PDF-книг из archive.org — дизайн админ-инструмента

**Дата:** 2026-06-02
**Статус:** дизайн одобрен Абдулой, готов к плану реализации
**Связано:** ADR-023 (PdfSourceProvider), ADR-024 (MinIO), ADR-028 (academic
citation), ADR-035 (PDF upload), Этап 17 (OCR/AI-edit). Спека под-проекта,
свой цикл spec → plan → impl.

---

## 1. Цель и контекст

Админ должен импортировать книгу из archive.org, вставив URL, и получить
полноценный проверяемый инструмент: распарсить метаданные + список PDF
(обложка/тома, оригинал-скан/OCR), увидеть **превью как ляжет в наш формат**,
**дообогатить недостающие поля** (которых нет в источнике — фронт подсвечивает),
выбрать обложку, и импортировать.

**Ключевой принцип (Абдула):** импорт заполняет ВСЕ «наши» поля; чего нет в
источнике — фронт явно сигналит («нет в источнике, дообогати»). Это
gap-aware enrichment, переиспользуемый паттерн для любых источников в будущем.

**Требования:**
- Смотреть и **оригинальный PDF**, и **OCR/AI-расшифрованный текст**.
- Устойчивость к разнообразию archive.org: с OCR/без, один файл/много мелких,
  есть обложка/нет.
- MVP сначала, допиливать в тестовой эксплуатации (но не забыть обсуждённое).

---

## 2. Archive.org API (вход парсера) — факты из HAR

- **Один вызов, без авторизации:** `GET https://archive.org/metadata/{identifier}`
  → JSON `{ metadata: {...}, files: [...] }`.
- **Identifier** = первый сегмент после `/details/`. Пример URL
  `archive.org/details/fmhji/fmhji1/page/70/mode/2up` → `fmhji`.
- **files[]**: каждый `{ name, format, source, size, mtime, md5, sha1 }`.
  PDF-форматы: `Image Container PDF` (source=original, скан) и
  `Additional Text PDF` (source=derivative, OCR-слой, имя `*_text.pdf`).
- **Именование/тома** (пример fmhji): `fmhji0[_text].pdf` = обложка (~130КБ,
  1 стр.), `fmhji1/2/3[_text].pdf` = 3 тома (19-33МБ). Подтверждает
  «PDF WITH TEXT — 4 files» и «عدد المجلدات: 3».
- **metadata{}**: `title`, `creator` (автор), `language`, `mediatype` (texts),
  `publicdate`, `description` (HTML, арабский — часто содержит المؤلف/الناشر/
  سنة النشر/عدد المجلدات), `identifier`, `identifier-ark`, `subject`,
  `collection`, OCR-поля. **Издатель/год/тома часто только в `description`,
  не в чистых полях** → парсим что чисто, остальное помечаем `missing`.
- **Скачивание:** `GET https://archive.org/download/{id}/{file}` → 302 → CDN
  (`iaXXX.us.archive.org` / `dnXXX.ca.archive.org`). HTTP-клиент следует
  редиректу (наш `PdfLinksSourceProvider` + Java HttpClient это делают).
- **Обложка-картинка:** `https://archive.org/services/img/{id}` (готовый
  thumbnail — золотая обложка со скрина).

---

## 3. Что переиспользуем (инфра на ~80% готова)

- **`lib_books.metadata.pdf_links = { root, size, cover, files[] }`** — точная
  модель мульти-том + обложка. `files` = `["name.pdf|label", ...]` ИЛИ
  абсолютные URL. `cover:1` → `files[0]` — обложка (исключается из чтения).
  Для archive.org: `root="https://archive.org/download/{id}/"`,
  `files=["fmhji1_text.pdf|Том 1", ...]`.
- **`PdfLinksSourceProvider`** — уже резолвит абсолютные URL + root+файл,
  ленивый стрим (Range) + кэш в MinIO при первом чтении. archive.org root+файл
  → `download/{id}/{file}` → 302 → CDN — **работает без изменений**.
- **`library_files.source_type` уже включает `ARCHIVE_ORG`** + `source_url`.
- **Академические поля** (muhaqqiq/publisher/place/edition/year hijri+greg) +
  `findOrCreate` + `AcademicMetadataFields` UI + autocomplete endpoints.
- **Preview-confirm паттерн** (`AdminSunnahPage`), **dashboard-карточки**
  (`AdminDashboardPage`), **`FileImportService`** (PDF→pages+text через PDFBox).
- **OCR/AI-edit пайплайн** (Этап 17) — для скан-only книг без текстового слоя.

**Гэпы под MVP:** нет колонки `cover_url`; нет `ArchiveOrgClient`/admin-страницы
(строим); volume-dropdown в reader (итерация).

---

## 4. Архитектура (компоненты с чёткими границами)

### Backend
- **`ArchiveOrgClient`** (`library/archiveorg/`) — HTTP к
  `archive.org/metadata/{id}`; `extractIdentifier(url)`; resilience4j
  circuit-breaker как у shamela. Вход: id/url. Выход: сырой
  `ArchiveOrgMetadata { metadata, files[] }`.
- **`ArchiveOrgMetadataMapper`** — чистая логика: сырые метаданные → **PreviewDTO**
  с провенансом по полю + авто-группировкой файлов. Вход: сырой DTO. Выход:
  `ArchiveOrgPreview`. Тестируется без сети (фикстуры из реальных metadata.json).
- **`ArchiveOrgImportService`** — `preview(url)` (без записи) + `import(request)`
  (создаёт `lib_books` + pdf_links + cover_url + академ. поля; опц. фоновое
  извлечение текста; lazy/eager). ADMIN-only (mirror Sunnah/Shamela guard).
- **`ArchiveOrgAdminController`** — `GET /api/v1/admin/archive-org/preview?url=`
  + `POST /api/v1/admin/archive-org/import`.

### Frontend
- **`AdminArchiveOrgPage`** (`/admin/archive-org`) — URL-инпут → preview →
  enrich → confirm. Mirror `AdminSunnahPage` структуры.
- Dashboard-карточка «Импорт из archive.org» (что получится: книга с томами +
  оригинал/OCR + академ. метаданные + обложка).
- Переиспользует `AcademicMetadataFields`, gap-индикаторы (новый мелкий
  `FieldProvenanceBadge`).

### Данные
- **PreviewDTO** (provenance): `{ field: { value, source: 'archive_org'|'missing' } }`
  для title/author/publisher/place/muhaqqiq/edition/yearHijri/yearGregorian/
  volumes/language. + `rawDescription` (для копипасты). + `files` (сгруппированы:
  `[{ role: 'cover'|'volume', volumeNo, original?: {name,size,url}, ocr?: {...} }]`).
  + `coverOptions: [{ kind: 'thumbnail'|'cover_pdf_page'|'upload', url? }]`.
- **ImportRequest**: подтверждённые поля + маппинг файлов (что обложка/тома,
  какой вариант) + выбор обложки + флаги `lazy|eager`, `extractText`,
  `testModePages?: number`.

---

## 5. Gap-aware enrichment (ключевая фича)

Каждое «наше» поле в preview несёт `source`:
- `archive_org` (зелёный бейдж) — взято из метаданных, prefilled, редактируемо.
- `missing` (жёлтый бейдж «нет в источнике») — пусто, фронт зовёт заполнить.

Confirm не блокирует на пустых (можно импортировать частично), но визуально
ясно что недозаполнено. Паттерн обобщаемый: тот же PreviewDTO-с-провенансом
позже для shamela/sunnah/alminasa.

---

## 6. Оригинал + OCR dual-view

- pdf_links хранит **обе** ветки на том: оригинал Image-PDF (для точного
  PDF-просмотра) и OCR text-PDF (источник текста). Модель: либо два
  `files`-набора в metadata (`pdf_links` + `pdf_links_ocr`), либо помечаем
  каждый файл `variant: original|ocr` — **решение на этапе плана** (предпочт.
  расширить элемент files до `{name,label,variant,volumeNo}` без ломки текущего
  парсинга строк `"name|label"`).
- Reader: PDF-режим показывает оригинал; текст-режим/цитаты/AI-edit — из
  извлечённого OCR-текста. Volume-dropdown — итерация.

---

## 7. Извлечение текста + test-mode

- **Полное извлечение** (цель): скачать OCR-PDF тома → PDFBox → `lib_pages`
  (`text_content`, `pdf_page_number`) → searchable reader + цитаты + AI-edit.
  Тяжёлое (минуты, ГБ) → **фоновым шагом** (async, как OCR-пайплайн), не блокирует
  импорт. Где OCR-слоя нет → наш Tesseract.
- **Test-mode** (Абдула): тумблер «извлечь только N страниц» (напр. 2-5) — для
  отладки, чтоб не висеть десятки минут. `testModePages` в ImportRequest.

---

## 8. REST-контракт

- `GET /api/v1/admin/archive-org/preview?url={archiveOrgUrl}` (ADMIN) →
  `ArchiveOrgPreview` (без записи). 400 невалидный URL; 404 item не найден;
  502 archive.org недоступен.
- `POST /api/v1/admin/archive-org/import` (ADMIN) body `ImportRequest` →
  `{ bookId, volumesRegistered, coverSet, extractionJobId? }`.

---

## 9. Миграция

- **67** `lib_books ADD COLUMN cover_url TEXT NULL` + (опц.) backfill из
  `pdf_links.cover`. BookListPage/BookReader рендерят cover_url (fallback —
  текущий letter-avatar).

---

## 10. Декомпозиция

**MVP (текущий спринт):**
1. `ArchiveOrgClient` + `ArchiveOrgMetadataMapper` (parser + provenance +
   авто-группировка) + тесты на фикстурах.
2. `preview` endpoint + `AdminArchiveOrgPage` (URL→preview с gap-бейджами +
   маппинг файлов + выбор обложки) + dashboard-карточка.
3. `import` endpoint: `lib_books` + pdf_links (оригинал+OCR) + cover_url
   (migration 67) + академ. поля; **lazy PDF**; **test-mode извлечение N
   страниц**.
4. Reader показывает импортированную книгу (PDF-просмотр оригинала; обложка
   на карточке).

**Итерации (тестовая эксплуатация):**
- Полное фоновое извлечение всех томов (+ Tesseract для скан-only). В рамках
  этой итерации — **вынести извлечение текста из транзакции импорта** (сейчас
  при `extractText=true` синхронный download+parse PDF идёт внутри
  `@Transactional` в `ArchiveOrgImportService`; за флагом, дефолт off):
  перевести на async (как OCR-пайплайн), чтобы не держать БД-транзакцию
  открытой на минуты при больших томах.
- Volume-dropdown в reader; eager-download UI.
- Provenance-enrichment как общий паттерн для shamela/sunnah/alminasa.

---

## 11. Error handling / edge cases

- Невалидный/не-archive.org URL → 400 с понятным сообщением.
- item без PDF (только EPUB/изображения) → preview сигналит «нет PDF».
- нет OCR-варианта → флаг «только скан»; нет обложки → thumbnail fallback или
  letter-avatar.
- archive.org down / 302-цепочка падает → 502 + actionable toast.
- Идемпотентность: повторный импорт того же identifier → находит существующую
  книгу (natural key — archive.org identifier в metadata) либо предупреждает.

---

## 12. Тестирование

- `ArchiveOrgMetadataMapperTest` — фикстуры реальных metadata.json (fmhji +
  2-3 edge: single-volume, no-OCR, many-files, no-cover — дёргаю сам, API
  публичный) → провенанс + группировка.
- `ArchiveOrgImportServiceIT` (Testcontainers) — import создаёт book+pdf_links+
  cover_url; test-mode извлекает ровно N страниц; идемпотентность.
- `ArchiveOrgAdminControllerIT` — ADMIN-guard (non-admin 403), preview/import
  контракты, невалидный URL.
- `@Tag("live")` — реальный вызов archive.org (исключён из verify).
- Frontend: AdminArchiveOrgPage (preview render + gap-бейджи + confirm) MSW.

---

## 13. Открытые вопросы (решить в плане)

- Точная модель dual original+OCR в pdf_links (расширить элемент files vs
  второй набор). Предпочт. — расширить элемент.
- Идемпотентный natural key (archive.org identifier) — куда писать (metadata
  jsonb `archive_org_id` + поиск по нему).
