# Sunnah.com ETL — Feasibility Spike + Design Spec

**Дата:** 2026-05-31 (Сессия 51)
**Этап:** 49.C Hadith Chains Explorer — **Phase 5** (ETL импорт sunnah.com)
**Статус:** feasibility-спайк выполнен ✅ + design draft. **Код не написан** —
эпик на 2-3 сессии, начинается после approval Абдулы по decision points (§9).
**Связанные:** ADR-049 (sanad graph), `2026-05-20-hadith-explorer-design.md`,
миграции 52-56 (hd_* схема).

---

## 1. Цель

Импортировать корпус хадисов из sunnah.com в `hd_*` схему, чтобы Hadith
Explorer перестал быть демо на 3 курируемых хадиса и получил тысячи хадисов
с текстом (ar/en), нумерацией по сборникам, главами и оценками учёных.

## 2. TL;DR спайка (главное)

1. **Reachability: ✅ но только через corp-прокси.** sunnah.com и
   api.sunnah.com доступны из WSL2 **исключительно через `HTTPS_PROXY`**
   (прямое соединение = timeout). Это **противоположно** shamela.ws
   (который доступен напрямую, а прокси его 407-ит). Вывод для кода:
   `SunnahHttpClientConfig` должен **использовать** прокси, переиспользуя
   `applyProxy()` паттерн из `ShamelaHttpClientConfig` (Authenticator на
   `RequestorType.PROXY` + уже существующий static-блок
   `jdk.http.auth.tunneling.disabledSchemes=""` в `ArgumentMapApplication`).

2. **🔴 Критическое ограничение для флагмана.** Хадис в sunnah.com — это
   `body` = **matn + isnad единым текстовым блобом**. Структурированной
   цепочки передатчиков (упорядоченный список narrator-id) **НЕТ**.
   Наш sanad-граф (Сессия 50) работает на `hd_sanads` /
   `hd_sanad_narrators` (упорядоченная передача). **sunnah.com их не
   заполняет.** → Phase 5 даёт **широту каталога** (текст + grades +
   структура сборник/книга/глава, отлично для Phase 4 поиска), но
   **не питает sanad-граф**. Структурированный иснад остаётся
   curated-only либо требует отдельного эпика (Arabic isnad-parser).

3. **Два источника данных:** официальный REST API (нужен бесплатный
   `X-API-Key`, rate-limited) **или** open dump на GitHub
   (`sunnah-com/api`, MySQL `db/`, без ключа — лучше для bulk).

4. **🟡 Лицензирование — OPEN QUESTION.** Тексты переводов на sunnah.com
   имеют разные права (Sahih International и пр.). До bulk-импорта
   подтвердить условия редистрибуции (как с «no bulk shamela parsing
   без UX-валидации» — та же осторожность).

## 3. Reachability — детали спайка

| Цель | Через прокси | Напрямую (no proxy) |
|---|---|---|
| `https://sunnah.com/` | **200** (0.42s) | timeout (28) |
| `https://api.sunnah.com/v1/collections` | **403** (нужен ключ) | timeout (28) |
| `https://github.com/sunnah-com/api` | **200** | (n/a, GitHub) |

Прокси в shell: `HTTPS_PROXY=http://<user>:<pass>@66.151.42.7:64526`.
403 на API = хост жив, нужен валидный `X-API-Key` (bogus key → `{"message":"Forbidden"}`).

**Контраст с shamela:** `ShamelaHttpClientConfig` намеренно делает
`.proxy(ProxySelector.of(null))` (direct) и игнорирует `HTTPS_PROXY`,
т.к. shamela.ws доступен напрямую, а прокси его не whitelist-ит (407).
sunnah.com — наоборот. **Не копировать дефолт shamela вслепую.**

## 4. Источники данных (выбор)

### Вариант A — официальный REST API (`api.sunnah.com/v1`)
- Эндпоинты: `/collections` → `/collections/{name}/books` →
  `.../chapters` → `.../hadiths`; плюс `/hadiths` (фильтры),
  `/hadiths/{urn}`, `/hadiths/urns?...`, `/hadiths/random`.
- Auth: `X-API-Key` (бесплатно, заявка на sunnah.com/developers).
- Плюсы: всегда свежие данные, инкрементальная синхронизация.
- Минусы: rate limits, нужен ключ (env var), пагинация по тысячам
  хадисов = много запросов через прокси.

### Вариант B — open dump (`github.com/sunnah-com/api`, рекомендуется как backbone)
- Репо: официальный API sunnah.com (Python/Flask + **MySQL**), есть
  `db/` + sample dataset (через Docker Compose), OpenAPI `spec.v1.yml`.
- Плюсы: bulk, без ключа, без rate-limit, воспроизводимо offline,
  один прогон. Идеально для первичного наполнения.
- Минусы: периодически устаревает (нужно re-pull); формат — MySQL dump,
  не наш Postgres (нужен конвертер staging-уровня).

**Рекомендация:** B как backbone первичного импорта (staging из dump →
mapper → hd_*), A как опциональный freshness-слой позже. Зеркалит
shamela-паттерн (staging DAO → mapper → целевые таблицы).

## 5. Модель данных sunnah.com (из `spec.v1.yml`)

- **collection**: `name`, `hasBooks`, `hasChapters`, `totalHadith`,
  `totalAvailableHadith`, lang-массив `{lang, title, shortIntro}`.
- **book**: `bookNumber`, `hadithStartNumber`, `hadithEndNumber`,
  `numberOfHadith`, lang-массив `{lang, name}`.
- **chapter**: `bookNumber`, `chapterId`, lang `{lang, chapterNumber,
  chapterTitle, intro, ending}`.
- **hadith**: `collection`, `bookNumber`, `chapterId`, `hadithNumber`,
  lang-массив `{lang, urn, body, grades:[{graded_by, grade}]}`.
  ⚠️ `body` = matn+isnad как **единый текст**. Narrator-цепочки нет.

## 6. Маппинг sunnah.com → `hd_*` схема

| sunnah.com | наша таблица.колонка | примечание |
|---|---|---|
| collection (Bukhari, Muslim…) | **книга-сборник** (`lib_books` или sources) → её `id` | нужно создать/зарезолвить как `hd_hadiths.primary_book_id` / `hd_matns.source_book_id` |
| book/chapter | `hd_matns.metadata` (bookNumber/chapterId/chapterTitle) | главы пока в metadata, отдельной hd_chapters нет |
| hadith.hadithNumber | `hd_hadiths.primary_number`, `hd_matns.printed_number` | |
| hadith.body (ar) | `hd_matns.text_ar` (+ `text_ar_normalized`) | normalize как в matn-diff |
| hadith.body (en) | `hd_matns.text_en` | |
| (нормализованный matn) | `hd_hadiths.normalized_matn` | для dedup/поиска |
| hadith.grades[] | `hd_hadiths.metadata.grades` (JSONB) | формат как у курируемого seed Сессии 50 |
| **isnad / narrators** | `hd_narrators` / `hd_sanads` / `hd_sanad_narrators` | **❌ НЕ заполняется** из sunnah.com |

**Идентичность хадиса (dedup):** один и тот же хадис в нескольких
сборниках → один `hd_hadiths` + несколько `hd_matns` (по `source_book_id`).
Ключ дедупа — `normalized_matn` (LCS/нормализация уже есть в matn-diff).
Риск: ложные слипания/расхождения; вынести порог в config, логировать.

## 7. Предлагаемая архитектура ETL (зеркало shamela-паттерна)

```
sunnah dump/API
   → SunnahHttpClientConfig (proxy-aware HttpClient, переиспользует applyProxy)
   → SunnahApiClient / SunnahDumpReader (staging-уровень)
   → staging DAO (sn_staging_*) — raw rows как у shamela ETL
   → SunnahToHadithMapper (resolve collection→book, dedup matn, grades→metadata)
   → hd_hadiths + hd_matns (+ grades в metadata)
   → admin-триггер (AdminShamelaPage-стиль), bulk-policy gate
```

- **Bulk-policy gate:** как «no bulk shamela parsing без UX-валидации» —
  импорт по одной collection, превью staging до commit, без массового
  прогона вслепую.
- **Idempotency:** повторный импорт обновляет, не дублирует
  (`source_book_id` + `printed_number` natural key).
- **Тесты:** `@Tag("live")` для реального API; unit mapper-тесты на
  фиксированных JSON-фикстурах (без сети).

## 8. Риски / открытые вопросы

1. 🔴 **Нет структурированного иснада** (§2.2) — sanad-граф не питается.
   Решение по объёму: (a) принять (каталог без графа для импортированных),
   (b) отдельный эпик Arabic isnad-parser, (c) гибрид: импорт каталога +
   ручная курация графов для важнейших хадисов.
2. 🟡 **Лицензирование** переводов — подтвердить до bulk.
3. 🟡 **Прокси-стабильность** на тысячах запросов (вариант A); вариант B
   снимает это.
4. 🟡 **collection→book резолвинг** — нужны ли сборники как `lib_books`
   или отдельная сущность hadith-collection? (решить в дизайне кода).
5. 🟡 **Объём** — full sunnah.com ~ десятки тысяч хадисов; влияние на БД,
   Phase 4 поиск (всё ещё нужен ES для арабской морфологии — backlog).

## 9. Decision points для Абдулы (нужны до кода)

1. **Источник:** dump (B, рекомендую) vs API (A) vs гибрид?
2. **Объём первого импорта:** 1-2 сборника (Бухари+Муслим) pilot vs всё?
3. **Sanad-граф для импортированных:** принять отсутствие графа /
   запланировать isnad-parser / гибрид-курация (§8.1)?
4. **collection как сущность:** reuse `lib_books` vs новая hd_collections?
5. **Лицензия:** кто подтверждает права на тексты переводов?

## 10. Что сделано в спайке (Сессия 51)

- ✅ Reachability матрица (прокси vs direct) для sunnah.com/API/GitHub.
- ✅ Подтверждён reusable proxy-паттерн (`ShamelaHttpClientConfig.applyProxy`).
- ✅ Модель данных sunnah.com из `spec.v1.yml`.
- ✅ Маппинг на `hd_*` (миграции 52-56), выявлено отсутствие иснада.
- ✅ Архитектура ETL + риски + decision points.
- ❌ Код (по плану — после approval §9).
