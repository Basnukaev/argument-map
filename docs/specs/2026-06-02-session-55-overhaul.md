# Сессия 55 — overhaul: OCR-removal, swappable AI, типы книг, archive.org/reader/hadith фиксы, AI-иснад

**Статус:** реализовано (Сессия 55). Дата: 2026-06-02.
**Контекст:** запрос Абдулы (10 пунктов + скриншоты img*.png + HAR archive.org/alminasa).
Карта кода — multi-agent workflow `understand-session55` (6 агентов, находки ниже
встроены). Все решения приняты автономно (Абдула: «не задавай вопросов, решай сам»).

---

## Ключевые факты из mapping-workflow (что оказалось не так, как на поверхности)

1. **`book_type` УЖЕ есть** — но это *жанр* (QURAN/HADITH_COLLECTION/BOOK/ARTICLE/
   MANUSCRIPT), используется end-to-end (фильтр, бейдж, routing HADITH_COLLECTION→
   hadith-explorer). Запрошенные TEXT_ONLY/TEXT_AND_FILE/FILE_ONLY — **ортогональная
   ось доступности** → новая колонка `content_kind`, НЕ переиспользуем `book_type`.

2. **«Пустые хадисы» (nawawi40) — это данные, не код.** Загруженный `sunnah-mysql`
   дамп = **только bukhari-сэмпл**: `HadithTable` 100 строк все `collection='bukhari'`;
   nawawi40 + 11 сборников = 0 строк. Чип-счётчик (42) берётся из каталожной колонки
   `Collections.totalhadith`; список — из реальных строк. Даже bukhari: каталог 7291 vs
   реально 100. Фикс = честный `availableHadith` + явный empty-state (+ полный дамп позже).

3. **archive.org `_text.pdf` = сам источник абракадабры** — это *собственный* Tesseract-
   OCR-слой archive.org (плохой арабский), та же «OCR плохо парсит арабский», что мы
   убиваем. → **archive.org импорт = FILE_ONLY** (только оригинальные сканы-PDF; `_text`
   варианты не регистрируем). Это разом схлопывает баги #2 (обложка/OCR как тома), #5
   (абракадабра), бесконечный спиннер текста.

4. **Баг #1 (метаданные жёлтые) — stale running JVM**, не баг парсера (парсер live
   работает: author/publisher/edition/hijri/greg/volumes = archive_org; place+muhaqqiq
   честно отсутствуют). Но AI-парсинг всё равно нужен — regex хрупок к пробелам
   (`المؤلف:` vs `عدد المجلدات :`). Action: рестарт backend + AI-экстрактор + regression-тест.

5. **Иснад уже лежит внутри matn** (`حدثنا فلان عن فلان`); структурного иснада в дампе
   нет, `hd_sanads`/`hd_narrators` пусты. `SanadGraphService`+`SanadGraphResponse`
   (React Flow) УЖЕ существуют (Hadith Explorer Phase 3), `SunnahHadithPreview.isnad`
   зарезервирован. → AI-извлечение цепочки тем же swappable-LLM.

6. **AI-интеграция:** только `AnthropicClient` (concrete, без интерфейса). `ai.provider`
   в `application.yml` — **мёртвый конфиг** (не читается). `@EnableAsync` объявлен ТОЛЬКО
   на `OcrConfig`, а `AiEditConfig` от него зависит → при удалении OcrConfig перенести
   `@EnableAsync` ПЕРВЫМ делом.

---

## Решения (ADR-уровень)

### D1. Полное удаление Tesseract OCR (ADR-057)
Удаляем `OcrService`/`OcrController`/`OcrConfig` + Tess4j + колонки `lib_pages.ocr_*` +
весь frontend-OCR (кнопка/бейджи/«Извлечь текст из OCR-слоя»). **Сохраняем**: `AiEditService`
(будущая AI-распознавалка), image-scan upload (`PageImageService`) как субстрат для AI.
Будущее распознавание текста — только через AI (LLM-vision), не Tesseract.

### D2. Swappable LLM (ADR-058)
`LlmClient { complete(system, user): String }` интерфейс. Реализации:
- `AnthropicLlmClient` — `/v1/messages`, заголовок `x-api-key` (рефактор существующего).
- `OpenAiCompatibleLlmClient` — `/v1/chat/completions`, `Authorization: Bearer`,
  `choices[0].message.content`. Один класс покрывает **OpenAI И DeepSeek** (разные
  `base-url`+`model`).
Выбор через `@ConditionalOnProperty(ai.provider = anthropic|openai|deepseek)`.
Конфиг per-provider: `ai.<provider>.{base-url,model,api-key}`. `AiEditService` инъектит
интерфейс. Регистрируется fallback-провайдер при отсутствии ключа (503 как сейчас).

### D3. `BookMetadataExtractionService`
Промптит LLM арабским HTML-описанием → структурированные поля книги
(title/author(s)/publisher/place/edition/hijriYear/gregYear/volumes). Возвращает
provenance per-поле. Regex-парсер (`ArchiveOrgDescriptionParser`) остаётся **fallback**'ом
(если LLM не сконфигурён/упал). Вызывается в archive.org preview.

### D4. `content_kind` (новая ось, ADR — в migration-комментарии)
`BookContentKind { TEXT_ONLY, TEXT_AND_FILE, FILE_ONLY }`. Колонка `lib_books.content_kind`
(migration 69, после OCR-drop 68). Предикаты:
- HAS_TEXT = `EXISTS(lib_pages WHERE text_content IS NOT NULL AND trim<>'' )` (пустые
  плейсхолдеры сканов НЕ считаются текстом).
- HAS_FILE = `PdfLinksSourceProvider.supports(book) || UserUploadProvider.supports(book)`.
HADITH_COLLECTION-жанр уже минует ридер (routing в /hadith) → для него `content_kind`
нейтрален (ставим TEXT_ONLY-подобное или sentinel; ридер не открывается). Вычисляется
**каждым импортёром после** записи pages/files через `BookRepository.updateContentKind`
(зеркало `updateCoverUrl`). Backfill существующих в миграции тем же предикатом.

Frontend: FILE_ONLY → ридер сразу в PDF, скрыть вкладку «Текст»; TEXT_ONLY → скрыть PDF,
показать «у этой книги нет PDF-варианта»; TEXT_AND_FILE → обе вкладки (как сейчас).

### D5. archive.org overhaul (ADR-056 amendment b)
Регистрируем только оригинальные Image-Container PDF (`fmhjiN.pdf`, N≥1) как тома +
`fmhji0.pdf` как обложку-картинку. **Не регистрируем** `_text` (OCR) варианты и cover-OCR.
Описание стрипаем в plain-text при импорте. AI-экстракция метаданных. content_kind=FILE_ONLY.
Frontend: лок формы после импорта (success-сводка + «Открыть книгу»/«Импортировать ещё»),
убрать чекбокс «Извлечь текст».

### D6. Reader/PDF
Дефолт режима по content_kind; убить бесконечный спиннер (0 страниц → PDF/placeholder).
Volume-dropdown дефолт = Том 1 (label-match + предпочесть original). PDF non-Range slow-path:
прокинуть Range через Vite-прокси, отдавать первый чанк, ослабить circuit-breaker на
transient. bbox deep-link цитат → PDF page+bbox для FILE_ONLY.

### D7. Hadith tooling (ADR — стратегия источников)
**Первичный источник** = sunnah.com дамп (matn + переводы + grade + структура).
**Обогащение иснадом** = AI-извлечение из matn (БЕЗ внешней зависимости). **alminasa.ai**
= проприетарный Elasticsearch (`reactivesearchproxy`, индексы hadith/narrators/rulings/
explanation); bulk-скрейпинг хрупкий + юридически серый → **НЕ делаем core-импортом**;
оставляем как опциональное будущее обогащение (narrator-биографии/рейтинги), задокументировано.
Консолидируем: один hadith-флоу `browse → preview → extract-isnad → import`; alminasa-карточка
остаётся заглушкой с честной подписью «будущее обогащение риджаль-данными».

### D8. AI-иснад (ADR-059)
`IsnadExtractionService` (LlmClient) парсит matn → цепочка передатчиков → `hd_sanads`/
`hd_narrators`/`hd_sanad_narrators` (дедуп нарраторов, фразы передачи عن/حدثنا/أخبرنا,
position 0 = ближе к Пророку). Живой preview-граф в AdminSunnahPage (reuse `SanadGraphResponse`)
вместо заглушки «Граф иснада будет извлечён на следующем этапе».

---

## Фазы (последовательно — тяжёлое пересечение файлов)

1. **OCR demolition** (Tesseract). Самодостаточно. → commit.
2. **Swappable LLM** + `BookMetadataExtractionService`. → commit.
3. **content_kind** (migration 69 + полный chain + frontend per-type). → commit.
4. **archive.org overhaul** (FILE_ONLY, drop _text, AI-метаданные, лок формы). → commit.
5. **Reader/PDF** (спиннер, dropdown, non-Range, bbox). → commit.
6. **Hadith** (availableHadith, panel scroll, консолидация). → commit.
7. **AI-иснад** (extraction + live graph). Крупнейшая фича. → commit(ы).
8. **Code-review + handoff** (multi-agent review, verify, docs/progress/roadmap/memory).

Каждая фаза: реализация субагентом (экономия контекста) → интеграция/верификация/коммит
на границе. Полный verify в конце фазы, не на каждый чих (memory `feedback_no_frequent_builds`).

## Открытые/отложенные
- Полный дамп sunnah.com (контент-ops, не код) — пустые сборники до загрузки.
- alminasa narrator-enrichment — будущее.
- bbox deep-link для FILE_ONLY — если PDF не несёт text-layer координат, deep-link по странице.
- **D8 (AI-иснад) реализован PREVIEW-ONLY** — извлечение строит граф
  in-memory, БЕЗ персиста в `hd_*`. Персист-на-импорте + дедуп
  нарраторов из rijal отложены (см. `backlog.md` «Isnad
  persistence-on-import»).
- **D6 (Reader/PDF) реализована только bbox-половина** —
  non-Range slow-path / Range-прокси через Vite / ослабление
  circuit-breaker НЕ сделаны (отложены). Текущий Range-путь PDF
  работает адекватно: suffix-range 416 намеренный, Content-Length
  присутствует → PDF.js использует explicit-ranges.
