# Сессия 56 — переделка хадисов под alminasa.ai как единственный источник

**Статус:** спроектировано (Сессия 56). Дата: 2026-06-03. Реализация — отдельным
этапом (план через `writing-plans`).
**Контекст:** запрос Абдулы — полноценный парсер хадисов из alminasa.ai с маппингом
под нашу модель и доработкой модели под богатые хадисоведческие данные (матн,
варианты, иснад, риджаль, типы, оценки, такхридж, шарх). Источники-кандидаты:
sunnah.com и alminasa.ai (+ HAR-файлы обоих в корне репо). Решение по стратегии
принято в диалоге (см. ниже), зафиксировано в memory `feedback_hadith_source_strategy`.

---

## Решения (приняты Абдулой в брейнсторме)

1. **alminasa.ai = ЕДИНСТВЕННЫЙ источник** арабского контента и хадисоведения.
   sunnah.com — только ради en-переводов либо выпил (выбрано: **выпил**).
2. **Доступ = bulk-снапшот через staging** (краулинг их ES → наши staging-таблицы →
   маппинг в домен). Рантайм НИКОГДА не ходит в alminasa. Снапшот наш, с атрибуцией.
3. **Структура «один хадис = одна цепочка» (1:1 атомарно) + cross-refs.** Каждый
   alminasa `hadith_id` → один `hd_hadith` (один матн, одна печатная цепочка). Связи
   «то же предание другим путём» (طرق/такхридж) — через таблицу `hd_hadith_crossrefs`.
   Граф «все طرق» строится на чтении агрегацией cross-refs.
4. **Переводы = AI on-demand** (ru/en). sunnah-ETL и AI-извлечение иснада (ADR-059) —
   удаляются как legacy. AI перенацеливается на перевод/Q&A/гариб/PDF.

---

## Ключевые факты из HAR-анализа (что реально отдаёт alminasa)

**Доступ:** открытый read-only ES-прокси, без авторизации/токена/cookie. Проверяет
только заголовки `Origin`/`Referer` (тривиально проставляются на сервере).
`POST https://alminasa.ai/api/reactivesearchproxy/{index}-read/_search` (одиночный) или
`/_msearch` (ndjson, батч). Тело — обычный ES Query DSL. Объём: **~82,596 хадисов /
12 сборников / 11,221 رواة**. Индексы датированы `2024-08-24` → их контракт может молча
поменяться (аргумент за снапшот + сырой JSONB в staging).

**Формат идентификаторов:** `hadith_id = "{bookId}-{serial}"` (напр. `146-1`, где
146 = صحيح البخاري). narrator `id` — устойчивый numeric (напр. `5719` = علقمة بن وقاص).
Это **природные ключи для идемпотентного импорта** — заменяют наивный fuzzy-матчинг имён.

**Индекс `hadith-12` (`_source`):**
- `hadith_id`, `hadith_serial_id`, `book_name` (ar), `number[]`
- `type` — **тип хадиса** (напр. `مرفوع`); у нас в модели НЕ было
- `chapter`, `sub_chapter` (ar строки — структура свода)
- `editions[]` — `{edition, page, volume}` (несколько печатных изданий)
- `page`, `volume` (основное издание)
- `hadith` — **полный текст с inline-разметкой рави**: `حَدَّثَنَا <a class=rawy id=4698>الْحُمَيْدِيُّ…</a> ، قَالَ : حَدَّثَنَا <a class=rawy id=3443>سُفْيَانُ</a> …`
- `narrators[]` — **упорядоченная цепочка**: `{id, full_name, level, grade, is_companion,
  is_unknown, hasCommentary, reference}`. Порядок = collector→companion.
- `matn_with_tashkeel` — **чистый матн** (иснад отрезан, полный ташкиль)
- `raw_narrations[]` — массив `hadith_id` параллельных преданий (**такхридж/طرق**)
- `narrations_numbers[]` — `{narration_id, number[]}` (номера cross-refs в их книгах)
- `rulings[]` — `{ruling, ruler, ruler_dod, book_name, number, page, volume}` (вердикты
  с годом смерти учёного)

**Индекс `narrators-12` (`_source`):**
- `full_name`, `extended_full_name`, `nickname` (кунья), `origin` (нисба)
- `level` — **табака** (свободный ar ordinal: `الثانية`, `العاشرة`, `صحابي`, …)
- `born_on`, `died_on` — **проза** (`ولد على عهده عهد النبي…`, `في خلافة عبد الملك…`)
- `lived_in`, `died_in` (места)
- `grade` — **джарх-та'диль дословно** (`ثقة ثبت`, `ثقة حافظ فقيه, إمام حجة إلا أنه تغير
  حفظه بأخرة, وكان ربما دلس لكن عن الثقات`)
- `book_titles[]` — в каких сводах встречается
- `top_students[]`, `top_scholars[]` — **сеть передатчиков** (`"الزهري - (24)"` — имя+частота)
- `GET /api/narrator-has-commentary?narratorId=N` → `{hasCommentary: bool}`

**Индекс `hadith-explanation-12`:** `{explanation_book_name, explanation_book_author,
explanation_page, explanation_volume, hadith_explanation_array:[{id, sharh}]}` — шарх
(напр. `فتح الباري` Ибн Хаджара, текст до ~59KB). Содержит вложенный `hadith` (дубль).

**Индекс `rulings-12_v2`:** `{hadith_id, rulings:[{hadith_id, ruling, type, book_name,
number, page, volume}], ruler, ruler_dod, narrations_type}`.

**UI-вкладки сайта** (наводка на возможные доп-данные): تخريج / رواة / شروح / **علل
(иляль — скрытые дефекты)** / حكم / **غريب (гариб — редкие слова)**. علل и غريب — при
реализации проверить: отдельные индексы или часть explanation.

---

## A. Архитектура доступа: краулер + staging

```
alminasa ES (read-only proxy)                  Наша БД (snapshot, владеем)
 hadith-12 / narrators-12 /     crawl          am_staging_*          map        hd_* домен
 explanation-12 / rulings-12  ──search_after──▶ (raw JSONB +        ─────────▶  (upsert by
 (+ ilal/gharib?)              _msearch батч,   разобранные поля)    external_id  external_id)
                               retry, rate-lim, чекпоинт/коллекцию)  идемпотентно
```

- **AlminasaEsClient** — узкий HTTP-клиент поверх их прокси. Конфиг через
  `@ConditionalOnProperty(alminasa.enabled)` + `alminasa.base-url`. Проставляет
  `Origin/Referer`. Поддерживает корп-прокси (как `ai.http.proxy`, gotcha «LLM за прокси»).
- **Краулер — фоновый возобновляемый job** (НЕ синхронный preview как sunnah). Пагинация
  `search_after` по `hadith_serial_id`, батчи `_msearch`. Чекпоинт на (index, collection,
  last_sort_value). Муснад Ахмада = 26,985 → нельзя «за один присест».
- **Сырой JSONB в staging** — пере-маппинг без пере-краулинга; forward-compat к смене
  контракта. Разобранные «горячие» поля — отдельными колонками для запросов/прогресса.
- **Рейт-лимит + ретраи** — консервативный старт, подбор эмпирически.

## B. Модель данных (расширяем существующую, не сносим)

Следующая Liquibase-миграция начинается с **70** (текущая последняя — 69; формат ID и
регистрация в master — через skill `liquibase-migration`). Все таблицы с rollback.

**Новые колонки:**

- `hd_hadiths`:
  `external_source VARCHAR`, `external_id VARCHAR` (UNIQUE с external_source),
  `hadith_type VARCHAR` (марфу'/маукуф/макту'/кудси — verbatim ar + при желании нормализ.),
  `chapter_ar TEXT`, `sub_chapter_ar TEXT`, `full_text_ar TEXT` (полный `hadith` с
  inline-тегами рави — для кликабельного иснада в UI).
- `hd_narrators`:
  `external_source VARCHAR`, `external_id VARCHAR` (UNIQUE с external_source),
  `tabaqa VARCHAR` (alminasa `level`), `grade_text TEXT` (verbatim джарх-та'диль),
  `born_on_text TEXT`, `died_on_text TEXT`. Enum `reliability_grade` ОСТАЁТСЯ как грубая
  производная (маппинг `ثقة*`→THIQA, `صدوق`→SADUQ, `is_companion`→SAHABI, …) для
  фильтров/цвета. `*_hijri` парсим из прозы когда есть число.

**Новые таблицы:**

- `hd_narrator_relations` — сеть передатчиков:
  `id, narrator_id FK, related_narrator_id FK NULL, related_name TEXT, role VARCHAR
  CHECK(STUDENT|SCHOLAR), cnt INTEGER, created_at`. `related_narrator_id` резолвится
  по `related_name`/id когда совпадает с импортированным рави; иначе NULL + имя.
- `hd_hadith_crossrefs` — такхридж/طرق:
  `id, hadith_id FK, related_external_id VARCHAR, related_hadith_id FK NULL, relation_type
  VARCHAR, note TEXT, created_at`. Из `raw_narrations` + cross-narration рулингов.
- `hd_rulings` — импортированные вердикты (свободный ruler):
  `id, hadith_id FK, ruler_name TEXT, ruler_death_year INTEGER NULL, ruling_text TEXT,
  book_name TEXT, page INTEGER NULL, volume INTEGER NULL, metadata JSONB, created_at`.
  `hadith_grades` (привязка к authorities) ОСТАЁТСЯ под РУЧНЫЕ оценки юзеров — это
  ортогонально импортированным рулингам.
- `hd_explanations` — شروح/علل/غريب:
  `id, hadith_id FK, kind VARCHAR CHECK(SHARH|ILAL|GHARIB), book_name TEXT, author TEXT,
  author_death_year INTEGER NULL, page INTEGER NULL, volume INTEGER NULL, text TEXT,
  metadata JSONB, created_at`.
- `hd_hadith_editions` — печатные издания:
  `id, hadith_id FK, edition_name TEXT, page INTEGER NULL, volume INTEGER NULL`.
- Staging: `am_staging_hadith` (PK external_id), `am_staging_narrator` (PK external_id),
  `am_staging_explanation`, `am_staging_ruling` — каждая с `raw JSONB`, разобранными
  горячими полями, `imported_at`, idempotent upsert (паттерн `sn_staging_*`).
- Чекпоинт краулинга: `am_crawl_checkpoint` (index, collection, last_sort, status,
  fetched_count, updated_at) — для resume.

## C. Логика маппинга и детерминированного парса иснада

- **Иснад — БЕЗ AI.** `hd_hadiths.full_text_ar` (поле `hadith`) режется по
  `<a class=rawy id=N>…</a>`. Текст ПЕРЕД каждым тегом (с прошлого тега) → нормализованный
  `transmission_phrase` (حدثنا/أخبرنا/أخبرني/سمعت/عن/أنّ/قال). Порядок тегов = `narrators[]`.
  alminasa отдаёт collector→companion → реверсим в `position 0 = сторона Пророка` (как уже
  делает `IsnadPersistenceService`). Каждый рави резолвится по `external_id`.
- **Два текста:** `matn_with_tashkeel` → `hd_matns.text_ar` (+ `text_ar_normalized` через
  `ArabicTextNormalizer`, `is_primary=true`); полный `hadith` → `hd_hadiths.full_text_ar`.
- **Рави:** upsert по `external_id`; `grade`→`grade_text` дословно + грубый enum;
  `level`→`tabaqa`; born/died → `*_text` (+ INTEGER при наличии числа).
- **Статус хадиса** (`hd_hadiths.status` CANONICAL/VARIANT/WEAK/FABRICATED) выводим
  правилом: сборники-сахихайн → CANONICAL; иначе из рулингов/типа (консервативно VARIANT).
- **Book-id → slug map:** 146=البخاري известно; остальные 11 ID извлекаются при краулинге
  из `book_name`. `hd_collections` upsert по slug (как сейчас).

## D. Удаление legacy

- **sunnah ETL целиком:** `backend/.../hadith/sunnah/**`, `sn_staging_*` (миграция-drop),
  `AdminSunnahPage.tsx`, связанные тесты, dev-конфиг `sunnah.dump.*`,
  docker `sunnah-mysql :3307`.
- **AI-извлечение иснада:** `backend/.../hadith/isnad/**` (IsnadExtractionService,
  IsnadPersistenceService, Extracted*), endpoint `POST /admin/sunnah/extract-isnad`,
  фронтовая кнопка «Извлечь иснад (ИИ)». **`SanadGraphService`/`SanadGraphResponse`/
  фронтовый `SanadGraph` — ОСТАЮТСЯ** (визуализация переиспользуется на данных alminasa).
- **ADR-060** «alminasa — единственный источник хадисов»: помечает решение Сессии-55
  (sunnah primary + AI-иснад) и ADR-059 (в части извлечения иснада) как superseded.
  Swappable LLM (ADR-058) остаётся в силе для перевода/Q&A.

## E. Админка и фронт

- **AdminSunnahPage → AdminHadithImportPage (alminasa):** каталог 12 сборников
  (краулед / мапнуто / прогресс-бар), старт/пауза/resume краулинга (статус из
  `am_crawl_checkpoint`), отдельный прогон импорта рави (11k), отдельно
  explanation/rulings, dry-run превью одного хадиса (map с rollback) перед bulk-map.
  Сохраняем философию «проверяемого импорта»: превью до записи.
- **Фронт раскрывает новые данные** (всё было помечено MISSING в карте кода):
  тип хадиса; **кликабельный иснад прямо в тексте** (рендер `full_text_ar`, рави →
  NarratorPanel); граф **сети передатчиков** (top_students/scholars); **такхридж**
  «встречается в N местах» + объединённый граф **طرق** через cross-refs; вкладки
  **شروح/علل/غريب**; вердикты с годом смерти учёного. Переиспользуем существующие
  HadithDetailPage/SanadGraph/NarratorPanel/MatnVariations.
- **AI-перевод on-demand:** кнопка «Перевести (ru/en)» на матне → swappable LlmClient →
  `hd_matns.text_ru/text_en`. Отдельный подэтап.

## F. Фазовый план реализации (детали — через writing-plans)

1. **Схема** — Liquibase 70+: колонки + новые таблицы + staging + checkpoint + drop
   `sn_staging_*`.
2. **ES-клиент + краулер** — AlminasaEsClient, фоновый job с `search_after`/чекпоинтами →
   `am_staging_*`. IT через мок-ответы (фикстуры из HAR).
3. **Маппер staging→домен** — детерминированный парс иснада (unit-тесты на реальном
   `hadith`-HTML из HAR), upsert по external_id, cross-refs, рулинги, explanations,
   relations. IT на end-to-end одного хадиса.
4. **Удаление legacy** — sunnah ETL + AI-isnad + ADR-060.
5. **Админка** — AdminHadithImportPage (каталог/прогресс/dry-run/resume).
6. **Фронт-данные** — тип, кликабельный иснад, сеть рави, такхридж/طرق, шарх/иляль/гариб,
   вердикты.
7. **AI-перевод on-demand** (ru/en).

## G. Открытые пункты (проверить при реализации, не блокеры)

- `علل` / `غريب` — отдельные ES-индексы или поля explanation? (снять свежий HAR/запрос).
- Эмпирический рейт-лимит alminasa (старт консервативный).
- Полный map 12 book-id → slug (извлечь при первом краулинге).
- Юр./атрибуция: фиксируем источник в метаданных хадиса; рантайм не зависит от alminasa.
- Парс `born_on/died_on` прозы в INTEGER hijri — best-effort, не терять verbatim.

## H. Источники истины, которые надо обновить в коммитах реализации

- `docs/decisions.md` — ADR-060.
- `docs/api-contract.md` + регенерация `frontend/src/shared/api/types.ts` — новые
  admin-endpoints (crawl/map/progress) и обогащённый HadithDetailResponse.
- `docs/architecture.md` / `glossary.md` — alminasa pipeline, термины (табака, иляль,
  гариб, такхридж, طرق).
- `docs/gotchas.md` — открытый ES-прокси (Origin/Referer), датированный индекс.
- HAR-файлы alminasa/sunnah в корне → перенести в gitignore-зону (как archive.org HAR).
