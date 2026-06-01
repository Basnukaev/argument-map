# Hadith Viewing/Debug Tool — Design Spec

**Дата:** 2026-06-01 (Сессия 53)
**Контекст:** после импорта sunnah.com (Phase 5 шаг 2) страница хадисов
непригодна — стена арабского текста + сырая разметка sunnah.com. Стратегический
разворот Абдулы: **контент отложить, строить инструментарий для удобного
заполнения / просмотра / дебага контента**. Это первый такой инструмент.
**Связанные:** ADR-052 (sunnah ETL), `2026-05-31-sunnah-etl-design.md`.

## 1. Цель

Сделать `/hadith` пригодной для просмотра и дебага импортированного корпуса:
чистый текст + навигация (по сборникам, алфавиту, поиску).

## 2. Корневая проблема (диагноз)

`HadithListPage` рендерит `normalizedMatn`. `ArabicTextNormalizer` снимает
огласовки/folds, но **НЕ срезает разметку**. Текст sunnah.com содержит inline-
markup: HTML (`<p>`/`<br>`), quran-якоря `<A href="javascript:openquran(5,82,82)">…</A>`,
footnote-маркеры `<c_qNN>…</c_qNN>`, `<a/l/>`. Всё это течёт в `text_ar`/
`text_en` и в `normalizedMatn` (отображение И поиск).

## 3. Решение

### Часть A — чистка текста (backend, делается первой)

- **`SunnahTextCleaner`** (`hadith.sunnah.etl`, source-specific): срезает все
  теги (`<[^>]*>`), декодирует HTML-entities (`StringEscapeUtils.unescapeHtml4`,
  commons-text уже в classpath), схлопывает пробелы. Inner-текст тегов (текст
  аята внутри `<A>…</A>`) сохраняется — убирается только markup.
- Применяется в `SunnahDumpReader` для `bodyAr`/`bodyEn` → staging и `hd_matns`
  чистые; `normalizedMatn` (= нормализация уже чистого) тоже.
- **Перечистка 98 уже импортированных:** dev-операция — `DELETE` sunnah-
  импортированных (`hd_hadiths.metadata->>'source'='sunnah'`) + matns →
  переимпорт через endpoint (идемпотентность иначе «защищает» грязными).

### Часть B — навигируемая страница (frontend + мелкие backend-добавки)

- **`GET /api/v1/hadith/collections`** (новый): список `hd_collections`
  (`{ id, slug, nameAr, nameEn, nameRu, totalHadith }`) для фильтра.
- **`sort` param** на `GET /hadith/hadiths`: `recent` (default, created_at DESC),
  `number` (primary_number ASC), `alphabetical` (normalized_matn ASC — арабский
  алфавитный). Whitelist в service, как у library sort.
- **Редизайн `HadithListPage`:**
  - Top: поиск + чипы-сборники (Все / Бухари / Муслим / …, через collections
    endpoint) + сортировка (По № / Алфавит / Новые).
  - Список: **одна колонка** (читаемая ширина), карточки: «сборник · №»,
    статус-бейдж, **чистый арабский matn** (naskh, RTL, 2-3 строки line-clamp),
    grades-индикатор если есть. **Только арабский** (решение Абдулы). Клик → detail.
  - «Показать ещё» (usePagedSearch) сохраняется.

## 4. Решения (зафиксированы)

1. Страница остаётся top-level «Хадисы» (не двигаем в Библиотеку — это под-проект #3).
2. Карточка — только арабский matn (без английского перевода).
3. Чистка — в `SunnahDumpReader` (разметка источник-специфична).
4. Сортировка `alphabetical` = `ORDER BY normalized_matn` (Postgres collation —
   арабский порядок «достаточно хорош» для браузинга).

## 5. Out of scope (отдельные под-проекты, потом)

- #2 Линковка хадисов в узлы тем (citation-picker для хадисов).
- #3 Примирение `hd_collections` ↔ библиотечный «Сборник хадисов».
- #4 Иснад-граф на хадис (Абдула отложил явно).

## 6. Тестирование

- `SunnahTextCleanerTest` (unit): теги/entities/whitespace/quran-якоря.
- `SunnahDumpReaderIT`: fixture с markup → reader отдаёт чистый текст.
- `GET /collections` + `sort` — IT.
- Frontend: component-тесты `HadithListPage` (чипы фильтруют, sort, чистые карточки).
- Playwright smoke после редизайна.
