# Журнал работы

Последние сессии. Новые записи - **сверху**

Формат записи описан в `docs/doc-hygiene.md` Принцип 5

**Архив:** `docs/archive/progress-sessions-{1-21,22-29,30-37,38-45,46-52}.md`
(сессии ≤52). Здесь — 53+.

<!-- NEWEST-ENTRY-ANCHOR -->

## 2026-06-04/05 - Сессия 57 - alminasa Планы 3-7: маппер, выпил legacy, админка, Explorer, AI-перевод

Полностью автономный марафон `/autopilot` (MAX-режим): закрыт ВЕСЬ
оставшийся alminasa-трек ADR-060 — Планы 3→7 последовательно, каждый по
циклу «план-док → критик → исполнение субагентами → тесты → review →
фиксы». ~35 коммитов. Для каждого плана критик-ревью ДО исполнения
ловило реальные дефекты дизайна (off-by-one семантики формул,
lock-on-failure launcher'а, mappedCount без source-фильтра и др.).

### Сделано

**План 3 — маппер staging→hd_*** (`docs/plans/2026-06-04-alminasa-mapper.md`):
- `AlminasaIsnadParser` — детерминированный парс rawy-тегов из
  `full_text_ar`; семантика формул «сегмент ПОСЛЕ тега» (= собственная
  речь рави о получении; критик C1 поймал off-by-one исходного дизайна),
  реверс → position 0 = сподвижник, формулы без сдвигов; эталонный
  вектор 146-1 залочен IT + round-trip `SanadGraphService.buildGraph`.
- `AlminasaNarratorMapper` (upsert по external_id, grade→enum c verbatim
  в grade_text, relations из top_students/scholars) +
  `AlminasaHadithMapper` (транзакционный порядок resolve→delete-сателлиты→
  insert; рулинги = union embedded+index per-док с дедупом; статус
  сахихайн→CANONICAL; пре-чек коллизии (collection, primary_number));
  `AlminasaImportService` (per-док tx, failure isolation, resolve
  crossref-FK SQL-ом + relations в Java по нормализованным именам),
  dry-run с setRollbackOnly. 51 тест; review 0C/1I (закрыт).
**План 4 — выпил legacy** (`…-legacy-removal.md`): `hadith/sunnah/**`
  (41 файл) + `hadith/isnad/**` (AI-иснад, ADR-059 → SUPERSEDED),
  миграция 74 DROP `sn_staging_*`, AdminSunnahPage + 82 i18n-ключа,
  `/admin/sunnah/*` из api-contract, regen types (-389 строк); roadmap
  49.C split + backlog-чистка. `buildGraph`/`SanadGraph` живы.
**План 5 — AdminHadithImportPage** (`…-admin-import-page.md`): 5 admin-
  endpoints (catalog: mappedCount ТОЛЬКО по external_source='alminasa';
  import/status с live processedSoFar; async launcher — CAS IDLE→RUNNING
  до submit + finally-гарантия выхода (критик C2: иначе transient-фейл
  навечно лочит 409), прямой executor.execute вместо @Async
  (self-invocation мимо прокси — вскрыто latch-тестом); dry-run
  404/422). Страница: краулер start/pause + каталог 12 сборников +
  статус импорта + dry-run превью цепи; импорт-кнопки disabled при
  crawl RUNNING. 14 IT + 7 MSW.
**План 6 — Hadith Explorer на alminasa** (`…-frontend-explorer.md`):
  detail +8 полей / narrator +6 / sanad-graph +externalId;
  `parseIsnadHtml` (без dangerouslySetInnerHTML) + кликабельный иснад
  (lifted graph-фетч, единая панель, клик = данные из графа без
  доп. фетча); секции вердиктов (provenance «на параллельную передачу»),
  шарха (collapsible), такхриджа (resolved→Link), изданий; NarratorPanel:
  tabaqa??generation, gradeText??reliabilityComment.
**План 7 — AI-перевод** (`…-ai-translation.md`): POST
  /matns/{id}/translate (кэш text_ru/text_en + cached-флаг, force=ADMIN,
  isEnabled→503, сервис БЕЗ @Transactional — LLM 5-15с вне tx, два
  UPDATE по lang, guard пустого ответа→502); кнопки RU/EN у hero-матна
  и вариаций (без дубля для primary). 10 IT со стабом LlmClient.

### Live-верификация (playwright headless + дев-краул в рамках гейта)
Дев-краул 1 страницы (100 хадисов / 315 рави / шархи / рулинги, pause
на границе) → UI-прогон админки: каталог 12 сборников со staged-counts,
импорт рави → маппинг всех → **вскрыт live-баг: kunya/laqab живых доков
> varchar(120)** (2 хадиса падали) → фикс truncate + re-run → 100/100,
26 crossref-FK + 916 relation-FK срезолвлено. Dry-run 146-1 через UI.
Explorer: detail 146-1 — тип مرفوع, кликабельный иснад (клик открыл
панель الحميدي), 8 вердиктов с provenance-подписями, 3 шарха, такхридж
20 мест, 2 издания. Скриншоты /tmp/p5-*.png, /tmp/p6-*.png.
**Очистка по гейту:** alminasa-строки hd_* удалены, staging TRUNCATE,
чекпоинт IDLE (статус-smoke подтверждён).

### Верификация (финал)
Backend `./mvnw verify` **1318/1318 BUILD SUCCESS** (на границе П4 был
flake HttpClientPdfFetcherCircuitBreakerIT — изолированный перегон
зелёный, документированный TC-cache flake). Frontend **vitest 737/737**
(116 файлов), tsc + eslint чисто, build success. types.ts регенерирован
3 раза (только аддитивные/удаления по планам).

### Code-review (независимые, по workflow)
П3: 0 Critical / 1 Important (bookIdFilter семантика — закрыт early-break).
П4: lite-verify, 2 гэпа (stale ADR-индекс, i18n alminasa-карточки) — закрыты.
П5-7 (объединённый): **0 Critical / 0 Important / 5 Minor** — 4 закрыты
(@NotNull lang + IT, дубль перевод-контролов, guard пустого LLM-ответа,
index-key комментарий), 1 принят (unmount-guard, React 19 безвреден).

### Решения/доки
ADR не потребовались (всё в рамках ADR-058/060); ADR-059 помечен
SUPERSEDED. Gotchas +2 (setRollbackOnly невидим из @Transactional-теста;
не-NFC combining-знаки в арабских литералах). api-contract: +6 endpoints
(Import Admin API, translate), -секция /admin/sunnah. Architecture:
маппинг-пайплайн.

### Проблемы/known
- kunya/laqab >120 в live-данных — закрыт truncate'ом (4be4b7c), полный
  текст остаётся в staging raw.
- `.omc/state` мусор от хуков субагентов появлялся внутри backend/src —
  вычищен, в git не попадал.
- Релейшны передатчиков: FK-резолв best-effort (короткие формы имён),
  916 на 100 хадисов — лучше ожиданий, но полнота не гарантирована.

### ЖДЁТ АБДУЛУ (юзер-гейты, без них трек дальше не двигается)
1. **Письмо alminasa (مركز تميز)** → разрешение на массовый обход
   12 сборников (backlog «Связаться с alminasa.ai»). После ответа:
   `/admin/hadith-import` → Старт краулера → (часы, resumable) →
   «Импорт рави» → «Маппинг всех сборников».
2. **Свежий HAR с кликами по вкладкам علل (иляль) и غريب (гариб)** на
   alminasa.ai — без него вкладки НЕ реализованы (контракты ES-индексов
   `hadith-commentary-12`/`ambiguous-12` неизвестны; ничего не выдумано).
3. **AI-ключ для live-перевода** (`--ai.provider=... --ai.api-key=...`,
   за прокси `--ai.http.proxy=...`): проверить POST translate живьём
   (сейчас только стаб-тесты; без ключа кнопки дают 503-тост).
4. **Ручные UI-проверки** (накопленные + новые):
   - НОВОЕ `/admin/hadith-import`: дев-краул Старт→Пауза (гейт: 1-2
     страницы!), импорт рави, маппинг сборника, прогресс-бары, dry-run
     `146-1` (после краула) — RTL-выравнивание, tooltip на disabled
     кнопках при RUNNING-краулере.
   - НОВОЕ `/hadith` Explorer (нужны данные — после дев-краула+импорта):
     detail хадиса — клик по рави В ТЕКСТЕ иснада И в React Flow графе
     (обе точки должны открывать ОДНУ панель; RF-клик headless не
     проверяется надёжно), collapsible шарха, такхридж-переходы,
     кнопки перевода (503-тост без ключа).
   - Накопленное с С55 (playwright env-blocked тогда): archive.org
     FILE_ONLY ридер, content_kind кнопки, bbox-подсветка,
     DeepSeek-метаданные.
   - Тест-логин для проверок: `pw-admin-57@test.local` /
     `Pw-Admin-57!pass` (ADMIN, создан этой сессией).

### Инфра-стейт (на конец Сессии 57)
Docker postgres+minio up; backend :9090 + JDWP :5005 запущен (код
Планов 5-7); frontend :5173 запущен; миграции через **74** (74 — DROP
sn_staging_*); staging/hd_* alminasa-данные ОЧИЩЕНЫ, чекпоинт IDLE;
sunnah-mysql больше не нужен совсем. Smoke:
`curl -s -H "X-User-Id: 00000000-0000-0000-0000-000000000001"
http://localhost:9090/api/v1/admin/alminasa/import/status` → IDLE.

### Следующий шаг
Трек alminasa кодово ЗАВЕРШЁН — дальше только юзер-гейты (блок «ЖДЁТ
АБДУЛУ» выше). Параллельные кандидаты: 49.B rating+pagination или 49.D
observability (спеки готовы); либо после ответа alminasa — массовый
обход + наблюдение за перф (resolveNarratorRelations OFFSET-caveat,
батчинг маппера если живой прогон 82k покажет боль).

## 2026-06-03/04 - Сессия 56 - alminasa единственный источник: спека + Планы 1-2

Реализация разворота ADR-060 (alminasa.ai = единственный источник хадисов).
Сессия шла в два захода: первый заход (03.06) закрыл спеку + План 1 и **упал**
(API-ошибки клиента) ровно на границе после doc-коммита Плана 1 — крах ничего
не потерял (атомарные коммиты). Второй заход (04.06) — План 2 целиком,
subagent-driven (имплементер + spec-ревью + quality-ревью на каждую группу задач).

### Сделано

**Спека + План 1 (заход 1, 03.06):** дизайн-спека
`docs/specs/2026-06-03-alminasa-hadith-source-design.md` (HAR-анализ,
архитектура краулер→staging→map, модель данных, фазовый план 1-7); план 1
`2026-06-03-alminasa-hadith-ingestion-schema.md`; **миграции 70-71** (alminasa-колонки
hd_hadiths/hd_narrators: external_id/type/chapter/full_text/tabaqa/grade_text;
5 новых таблиц hd_hadith_editions/hd_rulings/hd_explanations/hd_hadith_crossrefs/
hd_narrator_relations); расширенные Hadith/Narrator records + репозитории с
findByExternalId; 5 доменных records + репозитории; round-trip IT
(AlminasaSchemaRepositoryIT); **ADR-060** + glossary/architecture. 13 коммитов.

**План 2 (заход 2, 04.06):** `2026-06-04-alminasa-crawler-staging.md` — ES-клиент +
resumable краулер → staging. 15 коммитов:
- **HAR-разбор субагентом** → уточнения спеки: путь индекса `es-prod-euw1-{index}-read`;
  сайт пагинирует from+size (упёрся бы в ES-лимит 10k) → у нас `search_after` по
  `hadith_serial_id`; `narrators[].id` — строка; у narrator-дока id только в ES `_id`;
  вкладки علل/غريب — отдельные индексы, контракты не сняты (отложено до Плана 6).
  Фикстуры из HAR → `backend/src/test/resources/alminasa/`.
- **Миграция 72**: `am_staging_hadith/narrator/explanation/ruling` (raw jsonb NOT NULL
  forward-compat, природные PK, UNIQUE(hadith_serial_id) сторожит search_after) +
  `am_crawl_checkpoint` (generic по index_name, status CHECK).
- **AlminasaRows** (JsonNode→Row, fail-fast на битых доках, trim) + 4 staging-DAO +
  чекпоинт-DAO с resume-семантикой (upsertRunning(reset), advance=АБСОЛЮТНЫЙ счётчик).
- **AlminasaEsClient**: 4 запроса (страница хадисов search_after + terms-батчи
  narrators/explanations/rulings), Origin/Referer, опц. корп-прокси (Authenticator —
  безопасен, серверного Authorization нет), @Retry(alminasaApi) + transient-предикат
  (0/429/5xx; interrupt=-1 и 4xx НЕ ретраим), empty-collection short-circuit,
  warn при переполнении dependent-fetch-size.
- **AlminasaCrawlService**: «hadith-first» resumable цикл — страница хадисов →
  зависимые по id страницы (только проверенные HAR'ом формы запросов, нет
  сортировочных требований к зависимым индексам); чекпоинт на границе КАЖДОЙ
  страницы; pause на границе; stale-takeover (10 мин); single-thread executor
  (queue=0+AbortPolicy = реальный guard сериализации); БЕЗ @Transactional на цикле.
- **Admin REST** `/api/v1/admin/alminasa/crawl/{start,pause,status}` (202/200/200,
  409 alminasa-crawl-already-running, ADMIN-only как sunnah); race
  «stale-takeover при живом воркере» → TaskRejectedException → честный 409
  (чекпоинт не трогаем — живой воркер сам продолжает heartbeat).
- **Доки**: api-contract (3 endpoint'а + DTO), gotchas (ES-прокси: контракт и
  ловушки), architecture (pipeline краулера), regen types.ts (только добавления).
- **31 новый тест**: AlminasaRowsTest(5) + AmStagingDaoIT(4) + predicate(3) +
  StubIT(7) + RetryIT(1, лок regрессии self-invocation Сессии 55) +
  CrawlServiceIT(5: full/resume/pause/conflict/stale) + ControllerIT(6, вкл.
  детерминированный тест race живого воркера через latch).

### Решения
- ADR-060 (заход 1). План 2: `_msearch` не нужен (одиночные `_search` c terms);
  alminasa.enabled default-on (публичный прокси, секретов нет); чекпоинт generic
  без collection-измерения (глобальный hadith-first обход, прогресс по сборникам
  План 5 возьмёт из staging group by book_id); fetched_count = абсолютный count(*)
  (replay страницы не раздувает прогресс).
- **Продукт (Абдула, из консультации с веб-Клодом):** до массового обхода написать
  в مركز تميز (alminasa) про официальный доступ — пункт в backlog; полный обход
  12 сборников ГЕЙТИТСЯ этим пунктом. Memory `feedback_hadith_source_strategy`
  дополнена.

### Code-review (per-группа + финальный)
Каждая группа: spec-ревью + quality-ревью. 3 фикс-итерации по ревью: (1) предикат
без негативных тестов + empty-collection guards; (2) fetched_count дрейф при replay +
лживый javadoc 409 + total_hits каждую страницу; (3) race stale-takeover→500 →
TaskRejectedException→409 + детерминированный IT. Финальный ревью всего диапазона:
**0 Critical, 0 Important, Ready to merge**; единственный named residual —
queryability `terms.id` на narrators-индексе непроверяема стабом (live-smoke,
гейтится backlog-пунктом).

### Верификация (финал)
Backend `./mvnw verify` → **BUILD SUCCESS, 1324/1324**. Frontend **vitest 720/720**
(114 файлов), tsc clean, types.ts — только добавления. Live-smoke краулера НЕ гонялся
(см. backlog-гейт); admin-endpoints проверены IT + ручная проверка ниже.

### Хвост сессии — live-инцидент первого dev-краулинга + хотфикс (10b26b2)
Абдула прогнал dev-краул → `DuplicateKeyException` на первой странице. Systematic
debugging: **`hadith_serial_id` — номер ВНУТРИ сборника, не глобальный** (живой зонд:
12 доков с serial=1, по одному на сборник; заодно получен полный book-id map всех 12
сборников для Плана 3: 19=Муватта, 121=Ахмад, 137=Дарими, 146=Бухари, 158=Муслим,
173=Ибн Маджа, 184=Абу Дауд, 195=Тирмизи, 319=Насаи, 345=Ибн Хузайма, 454=Ибн Хиббан,
594=Мустадрак). UNIQUE-индекс миграции 72 сработал задуманной канарейкой (упал ДО
записи строк). Хотфикс TDD: миграция 73 (UNIQUE снят + checkpoint.last_sort_id),
**составной search_after [serial, hadith_id]** (живым зондом подтверждён), фикстура
из реальных live-доков. **Live-верификация:** 300 хадисов / 719 рави / 2069 рулингов
застейджены, serial=1..3 → по 12 сборников каждый, pause на границе, `terms.id` к
narrators подтверждён (закрыт residual финального ревью). Dev-данные очищены,
чекпоинт IDLE. gotcha + api-contract (lastSortId) + types regen — в том же коммите.

### Проблемы/known
- ~~`terms.id` к narrators-12 — live-предположение~~ **подтверждён живьём** (см. хвост).
- Вкладки علل/غريب: контракты НЕ в HAR — перед Планом 6 снять свежий HAR с кликами.
- Полный обход 12 сборников — ТОЛЬКО после ответа alminasa (backlog).

### Следующий шаг
**План 3 — маппер staging→hd_*** (процесс: спека → план в `docs/plans/`,
оркестрация через OMC `/ralplan`): детерминированный парс
иснада из `full_text_ar` по `<a class=rawy id=N>` (порядок = narrators[], реверс в
position 0 = Пророк ﷺ), upsert по external_id в hd_* (Plan 1 колонки/таблицы готовы),
cross-refs из raw_narrations, рулинги/шархи/relations, book-id→slug map (146=البخاري,
остальные из book_name при краулинге), unit-тесты на реальном hadith-HTML из фикстур.
После него План 4 (выпил sunnah ETL + AI-иснад) → 5 (админка) → 6-7 (фронт, AI-перевод).

### Перенесено из SESSION_START_PROMPT (файл выпилен при переходе на OMC, 2026-06-04)

**Дополнительно в очереди (после Планов):** 🖐️ ручные проверки UI (накоплено
с Сессии 55, playwright env-blocked): archive.org FILE_ONLY ридер, content_kind
кнопки, bbox-подсветка, DeepSeek-метаданные; плюс admin alminasa endpoints
(smoke ниже).

**Инфра-стейт (на конец Сессии 56):** Docker (postgres+minio) up; backend :9090
+ JDWP :5005 (команда — CLAUDE.md); frontend :5173; psql роль `argmap`;
миграции через **73** (73 — составной курсор, хвост-хотфикс). sunnah-mysql
`:3307` нужен только для sunnah-legacy (до Плана 4). AI-фичи: `--ai.provider=...` + ключ аргументом (НЕ в репо), за
корп-прокси `--ai.http.proxy=...`. Admin для curl:
`00000000-0000-0000-0000-000000000001`. HAR'ы в gitignore; полные сэмплы
ответов alminasa — `/tmp/alminasa-fixtures/` (если /tmp пережил ребут) либо
пере-извлечь из HAR в корне. Smoke alminasa:
`curl -s -H "X-User-Id: 00000000-0000-0000-0000-000000000001" http://localhost:9090/api/v1/admin/alminasa/crawl/status`
→ `{"status":"IDLE",...}`. Дев-данные: fmhji (FILE_ONLY, 4 файла); bukhari
hd_* 1 хадис (sunnah-legacy).

**Известные мелочи (не блокеры):** jsdom+node24 не парсит multipart FormData
(mock fetch в FileUploadModal.test); node24+undici AbortSignal workaround в
test-setup.ts (gotchas); playwright WSL2 не грузит Google Fonts через
corp-proxy (шрифты проверять в реальном браузере).

## 2026-06-02/03 - Сессия 55 - крупный автономный overhaul (7 фаз + code-review)

Запрос Абдулы: 10 пунктов + скриншоты `img*.png` + HAR (archive.org/alminasa).
Полностью автономный марафон. Спека `docs/specs/2026-06-02-session-55-overhaul.md`.
Карта кода — multi-agent workflow (6 агентов). ~14 коммитов.

### Сделано (7 фаз)
1. **Выпил Tesseract OCR** (ADR-057, migration 68 drop `lib_pages.ocr_*`). Удалены
   OcrService/Config/Controller/Status/JobResponse + Tess4j + frontend-OCR. `@EnableAsync`
   перенесён OcrConfig→AiEditConfig. Сохранён AiEditService + image-scan upload (субстрат
   для будущего AI-распознавания).
2. **Swappable LLM** (ADR-058): пакет `ai/` — `LlmClient` интерфейс + AnthropicLlmClient/
   OpenAiCompatibleLlmClient/DeepSeekLlmClient через `@ConditionalOnProperty(ai.provider)`,
   retry-инстанс `llmApi`. `BookMetadataExtractionService` (LLM→био-поля, graceful fallback).
3. **content_kind** (migration 69) — НОВАЯ ось доступности TEXT_ONLY/TEXT_AND_FILE/
   FILE_ONLY, ортогональна `book_type` (жанр). Импортёры выставляют через updateContentKind.
   Frontend: ридер по типу (FILE_ONLY→PDF сразу, TEXT_ONLY→без PDF).
4. **archive.org overhaul** (ADR-056 amend): FILE_ONLY, drop `_text` OCR-варианты (это и
   была абракадабра), HTML-стрип описания, AI-метаданные (regex fallback), лок формы после
   импорта. Закрыло баги обложки-как-тома, OCR-интерливинга, сырого HTML, дефолта-на-cover.
5. **Reader**: bbox-подсветка при PDF-цитате (display; creation=roadmap 25.f), 0-page guard.
6. **Hadith**: `availableHadith` честный счётчик (дамп = bukhari-only сэмпл!), независимый
   скролл превью-панели, alminasa-карточка переформулирована.
7. **AI-иснад** (ADR-059): `IsnadExtractionService` (LLM парсит цепочку из матна) +
   in-memory `SanadGraphResponse` (reuse Hadith Explorer viz) + `POST /admin/sunnah/
   extract-isnad` + кнопка «Извлечь иснад (ИИ)» в превью.
8. **Иснад persistence-on-import** (Фаза 9, ADR-059 amend): `IsnadPersistenceService` —
   на импорте (single default-on, bulk `?extractIsnad=true`) извлечённая цепочка пишется
   в hd_sanads/hd_narrators/hd_sanad_narrators (дедуп нарраторов по нормализованному
   имени — MVP, возможен false-merge гомонимов; idempotent delete-recreate). Теперь
   `/hadith` explorer показывает иснад импортированных хадисов (не только preview). +39 тестов.

### Бонус — Tier-3 фиксы из бэклога (Фазы 10-12, после основного scope)
- **Фаза 10 (backend):** ShamelaChapterMapper cycle-detect+log (главы не пропадают молча),
  ShamelaBibliographyParser word-count guard (страна не вклеивается в издателя),
  HadithController.getDetail O(sanads×links)→groupingBy.
- **Фаза 11 (frontend):** QuestionDetailPage delete-gating (author+ADMIN), AnswersSection
  per-answer busyIds, QuestionListPage proper load-error, AdminUsersPage locale-aware дата.
- **Фаза 12 (backend):** AI-edit liveness-escape (stale PROCESSING re-claim, property
  `ai.edit.processing-timeout-minutes`), HttpClientPdfFetcher negative Content-Length guard.
- Итого ~9 Tier-3 пунктов бэклога закрыты. backend BUILD SUCCESS, frontend 716 тестов.

### Хвост сессии — DeepSeek-прокси + сворачивание хадисов (по запросу Абдулы)
- **LLM за корп-прокси** (`ai.http.proxy`): WSL2 blackhole-DNS api.deepseek.com→127.0.0.1 +
  обязательный authenticated HTTPS_PROXY. Диагностика через standalone-репро: builder
  `Authenticator` (как shamela) проходит прокси-407, НО JDK вырезает серверный
  `Authorization: Bearer` → 401. Фикс: превентивный `Proxy-Authorization: Basic` +
  `jdk.httpclient.allowRestrictedHeaders` (static-блок), прокси ТОЛЬКО на LLM-HttpClient.
  `ai/LlmHttpClients`, gotcha. **DeepSeek заработал живьём** (метаданные archive.org ИИ —
  лучше regex: split издатель/место). Ключ передан аргументом, НЕ в репо.
- **UI-фиксы:** убраны OCR-упоминания в admin-карточках (archive.org «+OCR»→«PDF»,
  PDF-upload без обещания авто-извлечения); CitationPicker не виснет на FILE_ONLY
  (честное «только PDF» вместо вечного спиннера). Отложено (косметика): ровные чипы,
  z-index FloatingActionBar/модалка, header overlap; AI-vision метаданных по front-matter PDF.
- **Хадисы свёрнуты:** субагент enrich+grounding успел частично отредактировать
  (extract-isnad endpoint, grounding) → **откатил** (`git checkout`), чтобы Абдула
  переделал с чистой базы. РАЗВОРОТ стратегии (memory `feedback_hadith_source_strategy`):
  **alminasa = единственный источник** (Сессия 56), AI-isnad-from-matn (ADR-059) → legacy.

### Решения
- ADR-057 (OCR removed), ADR-058 (swappable LLM), ADR-059 (AI-иснад), ADR-056 amendment
  (archive.org FILE_ONLY). content_kind vs book_type — две ортогональные оси.
- **Стратегия источников хадисов**: sunnah дамп primary, иснад AI из матна (без внешней
  зависимости), alminasa.ai = проприетарный ES → НЕ скрейпим, оставлен как будущее
  обогащение риджаль-данными.

### Code-review (multi-agent, 5 измерений + adversarial verify)
14 raw → 11 confirmed, **0 Critical, 1 Important, 10 Minor**. Important + 9 Minor закрыты:
- **Important:** `@Retry` перестал применяться к AI-edit (single-arg `complete` = default-метод
  без @Retry → self-invocation мимо proxy). Фикс: AiEditService зовёт two-arg `complete(null,
  prompt)`, default-метод удалён, +`LlmClientRetryIT` (503/503/200 через proxy → 3 запроса).
- **Minor:** IsnadExtractionRequest.number Integer→String («1a»), bbox wrong-volume guard,
  dead i18n/union, 5 doc-фиксов (ADR-058/057/041 статусы, api-contract OCR, spec divergences).
- **Отложено:** migration 69 jsonb guard (нельзя править applied changeset; backlog).

### Верификация (финал)
Backend `./mvnw verify` → **BUILD SUCCESS** (см. progress). Frontend: tsc clean, eslint
**0 проблем** (убран pre-existing unused-disable), build ✓, **vitest 708/708** (109 файлов).
Live-smoke: archive.org re-import fmhji → FILE_ONLY, 4 файла (no `_text`), описание plain-text;
extract-isnad → `{llmEnabled:false}` (graceful без ключа).

### Проблемы/known
- AI-фичи (метаданные книг, иснад) работают только с реальным LLM-ключом (`ai.provider`
  + `*_API_KEY` env); без ключа graceful (regex fallback / `llmEnabled:false`).
- Дамп sunnah = только bukhari (100 строк); полный корпус — контент-ops.
- **Весь UI требует ручной проверки** (playwright env-blocked, нет Chromium).

### Следующий шаг
1. **🔴 Переделка хадисов под alminasa** (Сессия 56) — alminasa = единственный источник
   (memory `feedback_hadith_source_strategy`); дизайн-спека + alminasa-парсер (staging→map).
   AI-isnad-from-matn (ADR-059) становится legacy.
2. **🖐️ Ручная проверка UI** overhaul'а + DeepSeek-метаданные (ключ поднят).
3. **bbox-citation CREATION** (roadmap 25.f) — архитектурный блокер (pdfFileId↔library_files),
   нужно решение по модели. Display готов.
4. **Косметика:** ровные чипы admin, z-index модалки, header overlap; AI-vision метаданных
   по front-matter PDF (Абдула просил — титул/выходные данные несут ISBN/тома/издателя).

## 2026-06-02 - Сессия 54 (батч 5) - archive.org PDF-импорт MVP (новый инструмент)

Запрос Абдулы: админ-инструмент импорта книг из archive.org по URL (parser +
preview + gap-aware enrichment + выбор обложки/томов). Brainstorm→спека
(`docs/specs/2026-06-02-archive-org-pdf-import-design.md`, одобрена)
→ реализация 2 субагентами (backend+frontend). ADR-056, migration 67. ~3 коммита.

### Сделано (MVP)
- **Backend** (ADR-056): `ArchiveOrgClient` (metadata API, extractIdentifier, CB) +
  `ArchiveOrgMetadataMapper` (provenance archive_org/missing + авто-группировка
  cover/volumes original+OCR, устойчиво к вариативности) + `ArchiveOrgImportService`
  (preview no-write + import: lib_books + pdf_links **dual-variant object-form** +
  cover_url + академ.поля + lazy PDF + test-mode N страниц + идемпотентность по
  metadata.archive_org_id) + `ArchiveOrgAdminController` (ADMIN-only). migration 67
  cover_url. PdfLinksSourceProvider backward-compat (legacy string + object form).
  +45 IT, реальные фикстуры.
- **Frontend**: `AdminArchiveOrgPage` (/admin/archive-org) URL→preview с gap-бейджами
  (зелёный из источника / жёлтый «нет, заполни») + raw arabic description + список
  томов (original/OCR/«только скан») + cover-picker + test-mode тумблер → импорт.
  Dashboard-карточка. +5 тестов (27 pass).
- **Live-smoke прошёл**: `/admin/archive-org/preview?url=...fmhji` → реальные
  метаданные, title+language=archive_org, остальное=missing (как задумано).

### Следующий шаг (итерации archive.org)
**СДЕЛАНО (батч 6):** ✅ обложки — `coverUrl` в Book/BookResponse/Summary/Detail +
рендер `<img>` на карточке (fallback letter-avatar при null/404) + thumbnail в
reader-шапке; ✅ парсинг arabic `description` (`ArchiveOrgDescriptionParser`) —
author/publisher/год(hijri+greg)/тома/издание из текста → gap-поля стали
`archive_org` (fmhji парсит всё). migration 67.
**ОСТАЁТСЯ (итерации, в тестовой эксплуатации):** полное фоновое извлечение всех
томов (+Tesseract scan-only — сейчас sync за `extractText`/`testModePages`);
volume-dropdown в reader; eager-download UI; relabel/reassign томов в preview
(нужен `ImportRequest.fileMapping` — backend); place/muhaqqiq split из description;
provenance-enrichment как общий паттерн для shamela/sunnah/alminasa. Детали — спека §10.

## 2026-06-02 - Сессия 54 (батч 4) - «го дальше»: hd_collections UI + shamela guard + review + зелёный CI

Продолжение по бэклогу после батча 3. ~7 коммитов.

### Сделано
1. **hd_collections UI** (мост #3 виден end-to-end): книга `HADITH_COLLECTION` в
   библиотеке → бейдж «Сборник хадисов» + клик резолвит коллекцию (`by-book`) →
   `/hadith?collectionId=` (реальный контент, обходит «тонкую книгу»).
   HadithListPage читает `?collectionId=`. **Проверено живьём.**
2. **shamela-admin ADMIN-guard**: все 7 endpoints ADMIN-only (mirror Sunnah).
   **Живьём: admin 200 / non-admin 403.** Security-гэп закрыт.
3. **Multi-agent code review батчей 2-4** (Workflow, 19 агентов, 6 измерений +
   adversarial verify): 13 raw → **8 confirmed, 0 Critical**. Закрыты: Important
   (migration 65 rollback падал по FK при удалении system-user-owner lib_books →
   DELETE lib_books первым; md5sum обнулён для recompute) + 7 Minor (system-user
   ON CONFLICT→WHERE NOT EXISTS, ShamelaBookDao javadoc, vote-service javadoc
   422→400, VoteWidget мёртвый eslint-disable, BookListPage aria-label с title,
   4 orphaned dict-ключа).
4. **Регрессия TypeChip-теста починена**: chip-padding фикс (батч 3) сменил
   h-5/h-6 на padding-based, но TypeChip.test ассертил старое (проскочил т.к.
   гонялся scope'ом src/apps/argument-map, а тест в shared/). Обновлены ассерты.
5. **d3-drag jsdom-флак убран → ПОЛНОСТЬЮ ЗЕЛЁНЫЙ `npm test`**: bulkActions «2
   failed/10 errors» в full-suite (корень: user-event MouseEvent view=null →
   React-Flow d3-drag nodrag(null)). Фикс: мок d3-drag в test-setup + inline
   @xyflow/* в vite.config. Full suite: 105 файлов / 665 тестов / **0 failed**.

### Верификация (КУМУЛЯТИВНО, финал)
- Backend `./mvnw verify` → **BUILD SUCCESS** (полный сьют).
- Frontend `npx vitest run` → **665 pass / 0 failed / 0 errors** (стабильно 4×) +
  build ✓ + tsc ✓ + lint 0 err. **CI полностью зелёный впервые за сессию.**

### Следующий шаг
Все явные запросы Абдулы (3 батча) + бэклог (answer_votes, hd_collections backend+UI,
shamela guard) + 6 ручных багов + 2 код-ревью (batch1 + batches2-4, 0 Critical) +
d3-флак + **14 Tier-3 пунктов бэклога** (security: login timing/disabled-account/
decompression-bomb; correctness: acceptAnswer-CLOSED/body-clear/updateQuestion;
concurrency: OCR atomic-claim/authority-race migration 66+ADR-055/AnthropicClient
transient-retry; UX: ContextMenu clamp/Toaster assertive/useViewTracking/sort) —
**ЗАКРЫТЫ**. CI полностью зелёный (backend BUILD SUCCESS, frontend 678/0/0).

Остаётся НИЗКОПРИОРИТЕТНОЕ Tier-3 (shamela chapter parent-cycle, bibliography
dash-split, getDetail O(sanads×links) perf, OcrService NULL→FAILED — нужен новый
статус+миграция+фронт) + ОТЛОЖЕННОЕ Абдулой: IsnadExtraction (AI, контент); полный
in-place рендер hadith-сборника как книги (редирект достаточен); shamela
`category.sqlite` sync (живой shamela.ws). **Наибольшая ценность сейчас —
ВИЗУАЛЬНАЯ проверка руками** (playwright env-blocked, нет Chromium): весь
UX-overhaul + content-tooling. **БД пуста — наполнять через /admin tools.**

## 2026-06-02 - Сессия 54 (батч 3) - баги из ручного тестирования Абдулы (6 фиксов)

Абдула прогнал руками → список багов со скринами. Все 6 закрыты (~5 коммитов).

1. **Обновление статусов узлов не работало** («Не удалось обновить ни один узел»):
   PATCH /nodes/{id} не принимал `status` (только content/pos/lang) → 400. Добавлен
   `status` в UpdateNodeRequest + `NodeService.updateStatus` (assertCanWrite, валидация
   enum, audit). Персистит для узлов без влияющих рёбер (StatusCalc MVP пересчитывает
   только при изменении рёбер). **Проверено живьём: 200 + узел STANDING.**
2. **Импорт хадисов «не настроен»** (503): backend терял `SUNNAH_DUMP_*` env при
   рестарте (fork spring-boot:run не наследовал env). Фикс — запускать с
   **`-Dspring-boot.run.arguments="--sunnah.dump.enabled=true --sunnah.dump.url=... ..."`**
   (детерминированно, не зависит от env-наследования). **Проверено: /collections +
   /preview → 200.**
3. **PDF shamela не грузился** (`pdf_links.root отсутствует`): code gap — shamela-native
   `pdf_links` без `root` (относит. пути) не резолвились. Фикс: резолв против shamela
   CDN. +IT.
4. **Shamela sync `category.sqlite отсутствует`**: environmental/upstream (структура
   архива/сеть). Сделан рекурсивный fallback + actionable ошибка + gotcha (не хак).
5. **Миникарта**: синий прямоугольник вьюпорта при zoom-out наезжал на header/footer
   → overflow-hidden на области карты.
6. **Ребро RESPONDS_TO**: иконка ↩ (влево, против хода) путала направление → ↳ (вперёд).
   **+ чип типа узла**: текст касался границ → padding + зазор до контента.

**Проблемы/known:** shamela-admin endpoints без ADMIN-guard (security-backlog, см. батч 2);
shamela sync category.sqlite зависит от живого shamela.ws (env). bulkActions d3-шум.

**Инфра ВАЖНО:** backend запускать с `-Dspring-boot.run.arguments="--sunnah.dump.*"`
(НЕ env — fork их теряет). См. SESSION_START «Инфра». DB чистая, сидер opt-in.

## 2026-06-02 - Сессия 54 (батч 2) - баг-фиксы + чистка мусора + answer_votes + DB cleanup

**Продолжение Сессии 54** (тот же автономный заход). Абдула дал второй батч:
«продолжай по бэклогу, голоса на ответы и hd_collections» + список багов/чисток.
~13 коммитов поверх 27 из батча 1.

### Сделано

1. **fix vote-баг (КРИТ):** клик по голосу на карточке → разлогин + навигация.
   Корень: VoteWidget внутри React-Router `<Link>` делал только `stopPropagation`
   → onClick Link'а не срабатывал → не вызывался его preventDefault → браузер
   делал НАТИВНУЮ навигацию по `<a href>` = **full page reload** (отсюда И
   переход, И «разлогин»). Fix: `e.preventDefault()` в кнопках/контейнере.
2. **Выпилен весь user-preferences вертикаль** (мусор/dead): backend
   PreferencesController/Service/Repo/domain + 2 IT + **migration 63 DROP
   user_preferences**; frontend preferencesStore + PreferencesEffect +
   UserPreferencesSection. Это убрало: textSize (мёртвый `--text-size-scale`,
   дублировал рабочий UI-scale), arabicFont-3opt (мёртвый `--font-arabic-pref`
   дубль 10-опционного), tashkeel/transliteration/bilingual (junk).
3. **bilingual node mode + tashkeel удалены полностью** (frontend): NodeCard
   всегда рендерит content; Tashkeel tiptap-extension + stripTashkeel util +
   reader/editor кнопки убраны. (NodeResponse.translations оставлен в API —
   фронт игнорит, без regenerate-ripple.)
4. **Фикс title-weight для арабского** (#5): `--title-weight` применяется к
   арабским заголовкам (fixed-weight шрифты → браузер снапит 400/700, bold↔normal
   работает; документировано).
5. **Импорт-страницы показывают контент по умолчанию** (#9/#10): backend
   `GET /admin/shamela/books` (paged, q-опционален) — AdminShamelaPage теперь
   листает весь каталог по умолчанию + пагинация + фильтр, **убрана кнопка
   «импорт из файла»** (PDF только на /admin). AdminSunnahPage — автовыбор
   первого сборника → список хадисов виден сразу (был blank-until-filter).
6. **Голосование за ответы** (#1, бэклог): migration 64 `answer_votes` (зеркало
   question_votes) + AnswerVote стек + POST/DELETE/GET /api/v1/answers/{id}/vote
   + AnswerResponse +voteScore/userVote. Frontend: обобщённый `VoteWidget` на
   карточках ответов.
7. **DB cleanup (#11):** truncate всего user-facing контента + sunnah-staging +
   audit (topics/nodes/qa/lib_books+5915 pages/hd_*/sources/authorities/votes),
   удалены 35 тестовых юзеров + 86 refresh_tokens. **Сохранены:** admin-юзер
   (`00000000-...-0001`), схема, **shamela master-каталог lib_shamela_book=8589**
   (источник импорта). `DevHadithSeeder` → **opt-in** (`dev.seed-hadith=true`,
   по умолчанию выкл) чтобы чистая БД не пере-наполнялась при рестарте.

### Решения

- Весь preferences-вертикаль = мусор/dead → удалён целиком, не латали по полю.
  Memory `feedback_no_junk_settings`.
- DB cleanup: сохранить shamela-каталог (import source, нужен для наполнения) +
  admin + схему; сидер хадисов opt-out.
- vote внутри Link: `preventDefault` обязателен (не только stopPropagation).

### Проблемы / known

- ~~**shamela-admin endpoints без role-check** («на MVP») — pre-existing
  security-гэп (Sunnah-admin наоборот ADMIN-only).~~ **ЗАКРЫТО 2026-06-02:**
  все 7 shamela-admin endpoint теперь ADMIN-only (`requireAdmin()` mirror Sunnah;
  non-admin→403 forbidden-admin-only, anonymous на map-book→401). Consistency
  с Sunnah-admin восстановлена.
- bulkActions d3-drag uncaught-шум (pre-existing, тесты проходят).

### Следующий шаг

**hd_collections ↔ библиотечный «Сборник хадисов» (#2, под-проект #3) — BACKEND
МОСТ СДЕЛАН** (ADR-054, migration 65): `hd_collections.book_id → lib_books` +
ленивый `BookCollectionBridgeService` (создаёт lib_books HADITH_COLLECTION при
импорте, system-user owner 0002) + двусторонние линки (`CollectionResponse.bookId`
+ `GET /hadith/collections/by-book/{bookId}`). 49 IT.
**hd_collections UI — СДЕЛАНО** (батч 4 «го дальше»): обошёл проблему «тонкой
книги» через **редирект** — книга `HADITH_COLLECTION` в библиотеке получает бейдж
«Сборник хадисов» и при клике резолвит коллекцию (`by-book`) → ведёт в
`/hadith?collectionId=` (реальный контент, не пустой BookReader). HadithListPage
читает `?collectionId=` из URL. Мост #3 теперь видим end-to-end.
**ADMIN-guard на shamela-admin — СДЕЛАНО** (батч 4): все 7 endpoints ADMIN-only
(mirror Sunnah), проверено живьём (admin 200 / non-admin 403). Security-гэп закрыт.
**Остаток (опц., backlog):** полный in-place рендеринг hadith-сборника как книги
в BookReader (листать хадисы вместо lib_pages) — сейчас решено редиректом, что
достаточно; визуальная playwright-проверка (env-blocked); IsnadExtraction (AI,
контент, отложено); shamela category.sqlite (зависит от живого shamela.ws).

## 2026-06-02 - Сессия 54 - Крупный предпрод UX-overhaul + content-tooling (8 фаз, 17 коммитов)

**Автономный режим (ultracode).** Абдула дал большой product-брифинг («доведи до
предпрода, подготовь инструменты для ручного наполнения контентом») с 13 болями и
ушёл на часы, доверив все решения. Бриф зафиксирован в спеке
`docs/specs/2026-06-02-preprod-ux-overhaul.md` (источник истины) +
memory `project_session_54_preprod` + 3 feedback-memory. Сделано **широким
параллелизмом субагентов** (backend ∥ frontend на disjoint-доменах; frontend
сериализуется на общем dictionary.ts/master-changelog). Коммиты `1102d27..HEAD`.

### Сделано (по фазам брифа)

- **Фаза 1 — #2.B hadith→node citation (handoff-scope, ЗАКРЫТ).** Backend:
  `HadithRepository.findBySourceIds` (reverse IN-lookup) + nullable nested
  `HadithRef` в `NodeSourceResponse` (hadithId/primaryNumber/collectionName/
  previewMatn/status), enrichment в `NodeSourceController.list`. Frontend:
  `HadithPickerModal` (usePagedSearch over /hadith/hadiths) + 3-я кнопка
  «Прикрепить хадис» в `NodeCitationsSection` + рендер `HadithCite` перед
  FreeformCite. generate-api. +IT (8) +тесты.
- **Фаза 2 — SWR-кэш данных (бриф #2,#3).** Новый `queryCache` (in-memory Map
  по URL) + stale-while-revalidate в `useApiQuery` и `usePagedSearch` (кэширует
  весь накопленный Load-More список под page-0 ключом). Возврат на страницу —
  мгновенно, фоновая ревалидация. Корень жалобы «навигация тормозит». +10 тестов.
  Alt+K (#3): убран `backdrop-blur` с overlay (блюрил весь граф на каждом
  открытии) — мгновенно.
- **Фаза 3a — карточки библиотеки (#9).** Theme-aware палитра обложек
  (--cover-1..5, muted в dark) вместо foreground-токенов (прыгали к ~87% в dark
  → glare). Equal-height (Card.Title clamp + flex). #12: `SourceDetailPanel`
  стартует под header (top-12) — не сливается с шапкой.
- **Фаза 3b — голосование node→topic (#13, ADR в decisions.md).** Backend:
  удалён весь node-vote стек + migration 60 DROP node_votes + vote-поля убраны
  из NodeResponse; добавлен topic-vote стек (migration 61 topic_votes,
  TopicVote/Repo/Service/Controller, POST/DELETE/GET /topics/{id}/vote,
  TopicResponse +voteScore/userVote bulk-load). Frontend: удалён VoteWidget с
  узлов, новый `TopicVoteWidget` на карточках TopicListPage + шапке TopicGraphPage.
- **Фаза 4 — единый ListControls (#7,#10).** shared `ListToolbar`/`FilterChips`/
  `SortSelect`/`SearchInput`/`LoadMoreButton` применены на 4 списках. Topics+Q&A
  мигрированы на usePagedSearch (SWR-кэш + серверный поиск Q&A). Легенда статусов
  хадиса. +21 компонент-тест.
- **Фаза 5 — redesign чтения хадиса (#6).** 4 секции (Текст hero / полноэкранный
  иснад 70vh / Оценки панель / Вариации сворачиваемые) + sticky in-page nav
  (IntersectionObserver). detail на useApiQuery (SWR).
- **Фаза 6 — settings drawer + масштаб + reader fonts (#4,#11).** Settings как
  slide-over (шестерёнка/Alt+,/palette — не уводит со страницы). UI-scale store
  (compact 0.9 / standard 1.0 / comfortable 1.1, **дефолт compact ≈−10%**,
  откат «Стандартный (базовый)» в один клик). ReaderFontControls «Aa» в
  BookReader (шрифт live на тексте). +15 тестов.
- **Фаза 7 — overhaul админки + Sunnah import-preview (#1,#5, ЦЕНТРАЛЬНЫЙ).**
  Backend: 3 ADMIN endpoint'а — browse дампа, **DRY-RUN preview** (rollback-only
  транзакция: реальный код импорта → читаем uncommitted hd_* → setRollbackOnly,
  0 загрязнения), single-import по номеру. Frontend: `AdminDashboardPage`
  (карточки-возможности с «что получится», PDF promoted, alminasa.ai-заглушка),
  `AdminSunnahPage` (browse → preview как-будет-в-нашем-формате → импорт по
  одному), убран бесполезный лог из AdminShamelaPage. +8 IT +5 тестов.
- **Фаза 8 — redesign Q&A (#8).** QuestionDetailPage читаемая центр-колонка +
  статус-бейдж с тултипом + действия в kebab; accepted-answer пришпилен с лентой;
  composer-карточка; карточки списка equal-height. shared statusTokens/
  QuestionStatusBadge/OverflowMenu.
- **Бонус — голосование за вопросы** (завершение #13, после code review):
  migration 62 `question_votes` (зеркало topic_votes) + qa-стек + POST/DELETE/GET
  /questions/{id}/vote (open discussion, без visibility) + QuestionResponse
  +voteScore/userVote. Frontend: TopicVoteWidget обобщён в shared `VoteWidget`
  (entity-agnostic voteUrl, DRY), на карточках QuestionListPage + шапке
  QuestionDetailPage. +28 IT, generate-api.

### Решения

- **ADR (decisions.md): голосование перенесено node→topic** — узлы курируемые,
  голос за тему = сигнал популярности сообщества.
- **Dry-run preview через rollback-only транзакцию** (Фаза 7) — гарантированно
  точный preview (тот же код что импорт), 0 загрязнения БД. Отвергнут pure-mapping
  (риск расхождения с реальным маппером).
- **UI-scale дефолт compact 0.9** — Абдула явно хочет компактнее; откат на 100%
  обязателен и реализован.
- Параллелизм: backend ∥ frontend; frontend строго по одному (общий dictionary.ts
  + master-changelog — узкие места, не параллелятся; worktree не спасает frontend
  т.к. node_modules не копируется).

### Проблемы / known

- ✅ **3 pre-existing fail** `NodeDetailsPanel.test.tsx > секция Опора` —
  **ПОЧИНЕНЫ** (был не timing, а stale-mock: NodeCitationsSection читает
  /sources+/authorities как PagedResponse `.items`, а моки отдавали сырые
  массивы после миграции endpoints на PagedResponse). Fix: helper `paged()`.
  NodeDetailsPanel 25/25. Остаётся только d3-drag uncaught-шум в
  `bulkActions.test.tsx` (React Flow teardown в jsdom — не test-failure, тесты
  файла проходят; pre-existing, отдельная тест-гигиена).
- **Осиротевший `git stash@{0}`** (WIP on master @ e1802b6, BookListPage+
  dictionary) — избыточен (закоммиченный BookListPage уже на ListControls).
  НЕ дропнут (создан не мной). Абдуле: `git stash show -p stash@{0}`, при
  ненадобности `git stash drop`.
- Lint: 1 pre-existing warning (unused eslint-disable в BookReaderPage:174).
- **Визуальная playwright-проверка НЕ прогнана** — структурно всё зелёное
  (backend BUILD SUCCESS, frontend build ✓ / tsc ✓ / 686 тестов pass), но
  редизайны и глобальный масштаб 0.9 нужно глянуть глазами (см. «Что посмотреть»).

### Верификация

- Backend: **`./mvnw verify` → BUILD SUCCESS** (полный сьют, 0 реальных failures).
- Frontend: `npm run build` ✓, `eslint` 0 errors, `tsc` ✓, vitest **686 pass /
  3 pre-existing fail**.
- **Multi-agent code review (Workflow, 27 агентов, 6 измерений + adversarial
  verify): 21 raw → 17 confirmed, 0 Critical.** Закрыты обе Important (TopicVoteWidget
  props-clobber, usePagedSearch loadingMore залипал) + 12 Minor (Sunnah browse
  ORDER BY, queryCache cap, focus-trap, i18n, orphaned keys, доки, +6 authz-IT).
  Backlog: Sunnah offset-overflow
  (repo-wide PageRequest), bulkActions d3-flake.

### Следующий шаг

**Все 8 фаз брифа + Alt+K + #12 закрыты.** Остаток / на будущее:
1. **Визуально проверить руками** все редизайны (особенно глобальный масштаб 0.9
   — если мелко, переключить «Стандартный (базовый)» в настройках) + прогнать
   playwright-smoke когда удобно.
2. ✅ 3 `NodeDetailsPanel секция Опора` падения починены (stale PagedResponse
   моки). Остаётся опц.: d3-drag uncaught-шум в bulkActions.test.tsx (teardown).
3. ✅ Голосование на **вопросах** добавлено (Фаза 3b+, migration 62 question_votes
   + обобщённый shared `VoteWidget`). Осталось опц.: answer_votes (голос за
   отдельный ответ — accept-answer уже сигналит лучший, поэтому низкий приоритет).
4. Опц.: alminasa.ai import-tool (заглушка-карточка в админке готова).
5. Очистить `git stash@{0}` если не нужен.
6. ✅ Code review проведён (multi-agent, 0 Critical, Important+Minor закрыты) —
   см. «Верификация» выше.

## 2026-06-01 - Сессия 53 - Phase 5 ETL sunnah.com шаг 2 (2.a-2.e) + РЕАЛЬНЫЙ ПИЛОТ Бухари

**Автономный режим (ultracode).** Цель из handoff'а Сессии 52: Phase 5 ETL
sunnah.com шаг 2. Закрыт **весь шаг 2 (2.a-2.e)**: конвейер дамп → `sn_staging_*`
→ mapper → `hd_*`, прогнан **против реального дампа** (98 хадисов Бухари
импортированы). ~10 коммитов (`2b24e76..HEAD`), 2 multi-agent review-волны
(0 Critical обе) + de-flake BookRepositoryIT.

### Сделано

**Ключевая декомпозиция.** `SunnahDataSource` как интерфейс ПЕРЕД любым
конкретным reader'ом → staging-схема, DAO, mapper и все 27 тестов
зафиксированы **независимо** от нерешённой развилки «как читать дамп». Поэтому
шаги 2.a-2.c сделаны автономно без новых зависимостей и без сети.

**1. Шаг 2.a — migration 59 `sn_staging_*` (`2b24e76`, ADR-051).** Четыре
staging-таблицы (collection/book/chapter/hadith), зеркало логической модели
sunnah.com (spec §5). Отличия от shamela staging: естественные составные PK
(идемпотентный `ON CONFLICT` upsert), денормализация языков в `*_ar`/`*_en`,
`raw` jsonb forward-compat, без `deleted_at` tombstone (dump = полный snapshot).

**2. Шаг 2.b — `SunnahDataSource` + DTO + DAO (`2b24e76`).** Интерфейс
источника (dump + API → одни staging, единый mapper; гранулярность по
сборнику). 4 staging DTO-records + 4 DAO (упрощённый idiom
`batchUpdate(sql, List<Object[]>)` — без shamela-boilerplate, `imported_at`
через DB-default). `SunnahStagingDaoIT` 12 тестов.

**3. Шаг 2.c — `SunnahToHadithMapper` + `ArabicTextNormalizer` (`141b0b7`).**
Маппер staging → hd_collections/hd_hadiths/hd_matns: текст ar/en, grades
`[{graded_by}]` → metadata `[{scholar,grade}]` (контракт parseGrades),
структура книга/глава → matn.metadata, status=VARIANT (импорт не выдаётся за
канон), идемпотентность по `(collection_id, primary_number)`. Нормализатор
арабского (NFKC + снятие диакритики + сведение алиф/йа/та-марбута/хамза) —
вычисляется, а не вбивается руками. 6 mapper-IT + 9 normalizer-unit.

**4. Multi-agent code review (Workflow, 41 агент, `be8bdf0`).** 5 измерений
→ adversarial verify: 36 raw → 25 confirmed, **0 Critical**, 11 false-positive
отброшено. Все 6 Important + большинство Minor закрыты. Production-багов НЕ
было — гэпы в покрытии (вакуумные тесты enrichment book/chapter), доках,
hardening. Фиксы: parseNumber только ASCII (иначе коллизия idempotency с
арабо-индийскими цифрами), NFKC в normalizer, +18 тестов, architecture-platform/
glossary/ADR-051 docs.

**5. Шаг 2.d — `SunnahDumpReader` + end-to-end импорт (`a7443a4`+`29ba54a`,
ADR-052).** Изучена РЕАЛЬНАЯ схема дампа (`db/00-samplegitdb.sql`, 7 таблиц) —
денормализованная, ≠ логической spec.v1.yml: `HadithTable` консолидирует ar+en+
grade, книга/глава inline; `ChapterData` хранит bookID (не bookNumber); babID
ДРОБНЫЙ (1.1, 22.10). **Из-за дробного babID исправлен migration 59:
`chapter_id` integer → varchar** (иначе 1.0/1.1 схлопнулись бы), ripple в
DTO/DAO/mapper/тесты. `SunnahDumpReader` (JDBC MySQL → DTO, канонизация babID,
JOIN ChapterData→BookData) + `SunnahImportService` (source→staging→mapper,
bulk-policy gate, source как параметр). pom += `mysql-connector-j` +
`testcontainers:mysql`. **Первый dual-container IT** (Postgres+MySQL) — end-to-end
дамп→hd_*. Reader IT 4 + import IT 3.

**6. Вторая review-волна (Workflow, 3 измерения, `29ba54a`).** 17 raw → 7
confirmed, **0 Critical**, 10 отброшено. Все test-quality: fixture был
недостаточно discriminating (1 книга где bookID==bookNumber → JOIN не доказан;
mutation select bookID прошёл бы). Усилен fixture (книга bookID 2.0 ≠ bookNumber
5; orphan-хадис; muslim hasbooks='no') + тесты доказывают JOIN end-to-end +
ветки b==null/ch==null/no-grades + matn idempotency.

**7. Шаг 2.e — прод-обвязка + РЕАЛЬНЫЙ ПИЛОТ (`adb76b2`).** Просьба Абдулы:
«сделай всё сам в плане дампа, без ручной работы». Сделано:
- `SunnahDumpProperties` + `SunnahDumpConfig` (@ConditionalOnProperty bean
  MySQL DataSource + reader, изолирован от Postgres) + `SunnahAdminController`
  (ADMIN-only, bulk-policy gate: GET /collections превью, POST /import/{coll}) +
  503 `sunnah-dump-not-configured`. Controller IT 3, api-contract + generate-api.
- **Реальный прогон:** поднял MySQL (docker, `db/00-samplegitdb.sql` —
  13 collections / 100 hadiths Бухари), backend с `SUNNAH_DUMP_*` env →
  `POST /import/bukhari` → **inserted=98** skippedExisting=2 (курируемые №1/№8
  побеждают). hd_hadiths 101, matn ar/en + book/chapter enrichment корректны,
  detail/list API отдают. Реальные данные видны в Hadith Explorer.

**8. De-flake BookRepositoryIT.** Рекуррентный флак full-прогона — оказался
test pollution (другой класс коммитит lib_books), не tie-break. Fix:
subsequence-ассерт. Системная flakiness (PdfControllerIT и др.) — в backlog,
отдельная тест-гигиена.

**9. ПОД-ПРОЕКТ #1 — инструмент просмотра/дебага хадисов (pivot Абдулы:
«контент в последнюю очередь, нужны инструменты для заполнения/просмотра/
дебага»).** Страница хадисов была непригодна (стена текста + сырая разметка
sunnah). Спека `2026-06-01-hadith-viewing-tool-design.md` (brainstorm →
approved). Сделано (4 коммита):
- **`SunnahTextCleaner`** (`adb…`): срезает inline-разметку (HTML, quran-якоря
  `<A href=openquran>`, footnote `<c_qNN>`), decode entities, в reader. Реальная
  перечистка: delete 98 + reimport → 0 matns с markup (хадис №32 теперь чистый).
- **`GET /hadith/collections`** (chip-фильтр + реальный hadithCount) + **sort**
  (number/alphabetical-арабский/recent) + **previewMatn** (диакритизированный
  text_ar первичного matn, batch-load — красивее folded normalizedMatn).
- **Редизайн `HadithListPage`**: чипы-сборники + sort + одна колонка, чистые
  арабские карточки (naskh/RTL). 3 component-теста, lint 0err, build green.
- ⚠️ Playwright visual НЕ прогнан (MCP chromium missing + skill не
  зарегистрирован) — но страница ЖИВАЯ на :5173 с реальными данными.

### Решения

- **ADR-051** staging-схема sn_staging_* (+ дополнение про mapper-слой).
- **ADR-052** (step 2.d): дамп sunnah.com читаем через **MySQL-драйвер +
  Testcontainers** (решение Абдулы) — `mysql-connector-j` runtime +
  `testcontainers:mysql`. Отвергнуто: конвертация в SQLite, API-first.
  Реализовано в этой же сессии.
- **migration 59 `chapter_id` integer → varchar** — из-за дробного babID
  реального дампа (1.1, 22.10). Миграция была unreleased (дев-бэк на 58),
  потому правлена in place, а не alter-миграцией.

### Проблемы / known

- Системная flakiness full-прогона: IT-классы делят Testcontainers Postgres,
  каждый `verify` краснит 1 случайную «жертву» (зелёная в изоляции).
  `BookRepositoryIT` исправлен (subsequence-ассерт); `PdfControllerIT` и др. —
  в backlog (отдельная тест-гигиена). **0 реальных failures**, ~1137 тестов.
- ⚠️ **migration 59 правлена in place** (chapter_id varchar) — дев-БД была на
  58, конфликта checksum нет; теперь backend применил 59 при рестарте.

### Следующий шаг

**Phase 5 шаг 2 закрыт (2.a-2.e) + под-проект #1 (просмотр хадисов) сделан.**
Пивот Абдулы: контент — в последнюю очередь, **строим инструменты**. Очередь
под-проектов (спека `2026-06-01-hadith-viewing-tool-design.md` §5 — out of scope #1):

1. **Под-проект #1 — просмотр/дебаг хадисов: ✅ СДЕЛАН** (чистка текста +
   чипы-сборники + sort + чистые арабские карточки). Остаток (опц.): английский
   текст тоже содержит HTML — `SunnahTextCleaner` уже применяется и к bodyEn,
   так что чисто; диакритизация на карточке — через previewMatn, сделано.
2. **Под-проект #2 — линковка хадисов в узлы тем** (спека
   `2026-06-01-hadith-node-citation-design.md`). **#2.A backend ✅** (`84a565e`):
   `HadithCitationService` (ensure-source через мост `Hadith.sourceId` +
   attach) + `POST /nodes/{id}/hadith-citations` + IT (4). **← #2.B — порядок:**
   **(a) backend-обогащение (НЕ сделано):** `HadithRepository.findBySourceIds`
   + обогатить `GET /nodes/{id}/sources` для HADITH полями `hadithId`/
   `previewMatn`/`collectionName`/`primaryNumber` в `NodeSourceResponse` (без
   них фронт не нарисует) + IT; **(b)** рестарт backend → `generate-api`;
   **(c) frontend:** новая `HadithPickerModal` (НЕ reuse full-page
   `HadithListPage` — у неё нет onSelect; переиспользовать хук `usePagedSearch`
   + `GET /hadith/hadiths`), `onSelect(hadithId)`; **(d)** рендер хадис-опоры
   в `NodeCitationsSection.tsx` (3-я кнопка «прикрепить хадис» + ветка рендера
   по `hadithId` перед FreeformCite). Детали — SESSION_START «next».
3. **Под-проект #3 — примирение `hd_collections` ↔ библиотечный «Сборник
   хадисов»** (book_type=HADITH): два представления одного сборника. Архитектура.
4. **Frontend AdminSunnahPage** (опц.): кнопки превью+импорт поверх
   `/api/v1/admin/sunnah/*` (типы в types.ts) — триггерить импорт без curl.
5. **Под-проект #4 / Phase 5 step 3 `IsnadExtraction`** (= контент, Абдула
   отложил): matn+isnad блоб → AI (ADR-042) → hd_sanads + trust-level. step 4
   `SunnahApiClient` + полный корпус (sample-дамп = только 100 хадисов Бухари).

Источник за интерфейсом `SunnahDataSource` (dump сейчас, API позже одной
реализацией, mapper/контроллер не двигаются).

**Инфра пилота (важно для воспроизводимости):**
- Контейнер `sunnah-mysql` на :3307 (`db/00-samplegitdb.sql` загружен,
  root/root, БД `sunnah`). Дамп-файл — `/tmp/sunnah.sql` (re-fetch:
  `curl -sL raw.githubusercontent.com/sunnah-com/api/master/db/00-samplegitdb.sql`).
- Backend сейчас запущен с `SUNNAH_DUMP_ENABLED=true SUNNAH_DUMP_URL=
  'jdbc:mysql://localhost:3307/sunnah?allowPublicKeyRetrieval=true&useSSL=false'
  SUNNAH_DUMP_USERNAME=root SUNNAH_DUMP_PASSWORD=root` + JDWP :5005. Без этих
  env импорт-endpoint → 503 (by design).
- Дев-Postgres теперь: 101 hd_hadiths (3 сид + 98 импорт Бухари VARIANT).
- Admin user для curl: `00000000-0000-0000-0000-000000000001`.

**Ручная проверка (от Абдулы, висит с Сессии 52):** hadith/narrator списки
(debounce+Load More), dark-theme primary Button hover, thesis-книга 15, минимап
при detail-панели. **Новое:** Бухари в Hadith Explorer теперь 101 хадис с
реальным текстом — глянуть как выглядит (en содержит HTML — см. follow-up #1).

