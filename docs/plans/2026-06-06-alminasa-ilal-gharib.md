# alminasa — План 8: вкладки علل (иляль) и غريب (гариб)

> **Оркестрация:** OMC. Гейт снят: Абдула снял свежие HAR с кликами по
> вкладкам (2026-06-06), контракты разобраны — отчёт-фикстуры в
> `backend/src/test/resources/alminasa/s59/`. Спека §G предусматривала
> этот этап; `hd_explanations.kind CHECK (SHARH|ILAL|GHARIB)` готов с
> Плана 1.

**Goal:** скрытые дефекты (علل — «Илал ад-Даракутни» и т.п.) и
толкования редких слов (غريب — «ан-Нихая» Ибн аль-Асира, «Лисан
аль-араб») приходят в `hd_explanations` с kind=ILAL/GHARIB и
показываются отдельными секциями в Hadith Explorer.

## Контракты (из HAR-разбора, проверено живыми фикстурами)

1. **`hadith-commentary-12` (علل):** `POST …es-prod-euw1-hadith-commentary-12-read/_search`,
   запрос `terms:{commentary.narrations:[<hadith_id'ы>]}`. `_source`:
   `type` ('external'), `commentary.{id(int), narrations[](hadith_id-строки
   — КЛЮЧ ДЖОЙНА), book_name, author_name, matn, page, volume,
   commentary_text(текст иляля), full_text, full_text_html}` + вложенный
   `hadith.*` (пустой при type=external). Покрытие разреженное.
2. **`ambiguous-12` (غريب):** `terms:{id:[<int'ы>]}`. `_source`:
   `id(int PK), book_name(словарь), author, explanation(текст, длинный),
   page, volume`. **Ids приходят из hadith-doc поля `ambiguous[]`**:
   `[{reference(СЛОВО в матне), reference_id, explanation_ids[int]}]` —
   ★ ОНО УЖЕ В НАШЕМ STAGING RAW: 16 784 из 33 300 хадисов с непустым
   `ambiguous[]` (проверено SQL) → **перекраул hadith-12 НЕ нужен**.

## Дизайн-решения

1. **Backfill-краул, не пере-обход (фикс C1 критика — Option A,
   БЕЗ resumability):** НОВЫЙ сервис `AlminasaDependentsBackfillService`
   (НЕ «зеркало CrawlService» — тот hardcoded на HADITH_INDEX_KEY;
   реюзаем ТОЛЬКО `AmCrawlCheckpointDao` и паттерны). Цикл идёт
   keyset'ом `(book_id, hadith_serial_id)` по `am_staging_hadith`
   ЧИСТО В ПАМЯТИ — у `am_crawl_checkpoint` НЕТ колонок под пару
   (int book_id, long serial), а backfill — one-shot ~30-40 мин по
   статичному корпусу за admin-эндпоинтом: resumability низкоценна.
   Чекпоинт `index_name='backfill-s59'` хранит только КОАРС-прогресс
   для поллинга статуса (advance: last_sort_value=serial,
   last_sort_id=hadith_id последней строки, fetched_count=processed);
   crash → рестарт с нуля (upsert-идемпотентность это позволяет),
   pause — флагом на границе страницы (без resume с середины — после
   pause рестарт тоже с нуля; документировать в javadoc).
   **Отдельный single-thread executor-бин** (`alminasaBackfillExecutor`,
   queue=0+AbortPolicy) — НЕ реюзать crawl-executor (сериализовало бы
   backfill за живым краулом); backfill и crawl МОГУТ идти параллельно
   (разные index_name-строки). Свой конфликт: `backfill-already-running`
   409 (паттерн launcher'а С58: CAS+finally).
   **Батчинг per-index (фикс M1):** ILAL — батчи hadith_id'ов по
   `dependent-batch-size` (25, как существующие dependents), ES
   size=dependent-fetch-size; GHARIB — батчи explanation_ids по
   min(200, dependent-fetch-size), ES size = размеру батча (1 id =
   ровно 1 док); существующий overflow-warn паттерн. Объём: ~333
   страницы × (4 ILAL + ~2 GHARIB запросов) × delay 1000ms ≈ 30-40 мин.
2. **Миграция 75** (СНАЧАЛА проверить, что master включает 73/74 —
   MN1): `am_staging_commentary` (PK `commentary_id int`; горячие:
   book_name, author_name; `narrations jsonb NOT NULL`; raw jsonb NOT
   NULL; **точный индекс: `CREATE INDEX idx_am_staging_commentary_narrations
   ON am_staging_commentary USING gin (narrations);`** — дефолтный
   jsonb_ops корректен для `@>`) и `am_staging_ambiguous` (PK
   `ambiguous_id int`; горячие: book_name, author; raw jsonb NOT
   NULL). DAO-upsert'ы — `ON CONFLICT (pk) DO UPDATE` (зеркало
   AmHadithStagingDao.upsertAll). Скилл liquibase-migration.
3. **Маппинг — расширение `AlminasaHadithMapper.insertExplanations`**
   (delete-recreate per hadith: `deleteByHadithId` сносит ВСЕ kind
   разом и в той же транзакции пере-вставляет SHARH+ILAL+GHARIB —
   **SHARH выживает re-map, идемпотентность сохранена** — явная
   фиксация по критике):
   - ILAL (фикс C2 — это НЕ параметр-свап SHARH-пути!): НОВЫЙ
     DAO-метод `AmCommentaryStagingDao.findByNarration(String externalId)`
     → `SELECT … FROM am_staging_commentary WHERE narrations @> ?::jsonb`
     с bind-значением JSON-массива `["146-2"]` (строить Jackson'ом, НЕ
     конкатенацией). Unit-тест НА ФИКСТУРЕ s59: 146-2 находится этим
     методом. Строка kind=ILAL: book_name, author=author_name,
     **text=`commentary_text`** (финально — чистый разбор Даракутни;
     full_text дублирует матн-вопрос, уже показанный на странице),
     page/volume; metadata={commentaryId, narrations, fullText,
     fullTextHtml} (под будущий тогл «полный контекст»).
     N+1 по GIN на re-map 33k — ОСОЗНАННО принят (консистентно с
     существующими SHARH/rulings-lookups; GIN делает пустые ответы
     дешёвыми; one-shot admin-операция).
   - GHARIB: из `raw.ambiguous[]` хадиса. **Ключ строки (фикс M3):
     (hadith × ambiguous_id × reference_id)** — одна строка на
     (слово-вхождение × словарная статья): одна статья, общая для
     двух слов хадиса, даёт ДВЕ строки (заголовки-слова не теряются).
     ambiguous_id без дока в staging (sparse backfill) → молча skip.
     Строка kind=GHARIB: book_name, author, text=explanation,
     page/volume; **metadata={ambiguousId, reference(СЛОВО),
     referenceId}** (схему не расширяем; подсветка слова в матне —
     backlog).
   - Re-map 33k после backfill — лечит корпус.
4. **`ExplanationDto` + `reference`** (nullable, GHARIB-заголовок) —
   парс metadata в контроллере: `toExplanationDto` СЕЙЧАС metadata НЕ
   читает — добавить defensive try/catch jsonb-парс по образцу
   `toRulingDto` (:223).
5. **UI**: секция «Шарх» сейчас рендерит ВСЕ explanations — разделить
   на ТРИ секции по kind (شروح / علل / غريب), graceful-hide + пункты
   навигации. Группировка по kind ДО рендера (ORDER BY kind,
   created_at уже стабилен — не пересортировывать), key — per-section
   (MN3). GHARIB-карточка: заголовок = СЛОВО (reference, RTL, крупно)
   + словарь/автор + текст; ILAL-карточка: книга/автор (Даракутни) +
   текст иляля. i18n ru+ar.
6. **Гейт-этика**: объём backfill мал (2 индекса по уже снятому
   корпусу), письмо alminasa всё ещё рекомендуется (backlog не
   снимаем), но юзер-гейт обхода Абдула снял сам в С58.

## Вне скоупа (→ backlog, материал HAR есть)

`chains-links-12` (богатые рёбра сети: verb/grade/singular — 13k+ на
крупного рави, narrator-first краул), `narrator-commentary-12`
(джарх-цитаты учёных о рави → таблица hd_narrator_commentaries),
`references` (каталог 86 книг корпуса), расширенный профиль рави.

## Tasks

- [ ] **T1 миграция 75 + staging-DAO** (+ Rows-парсеры из фикстур
  s59, IT round-trip).
- [ ] **T2 ES-клиент + backfill-цикл**: 2 fetch-метода в
  AlminasaEsClient (terms-шаблоны ИЗ ФИКСТУР s59: индекс/тело;
  батчинг per-index по реш. 1), НОВЫЙ
  AlminasaDependentsBackfillService (in-memory keyset, коарс-чекпоинт
  'backfill-s59' только для статуса, СВОЙ executor-бин
  alminasaBackfillExecutor, CAS+finally-контракт как launcher С58,
  параллельность с crawl допустима) + admin REST
  `POST /admin/alminasa/backfill/{start,pause}` (свой 409
  backfill-already-running) + `GET /admin/alminasa/backfill/status`.
  IT со стабом (паттерн AlminasaEsClientStubIT) + IT
  фейл→IDLE→relaunch.
- [ ] **T3 маппер**: insertExplanations ILAL+GHARIB (реш. 3) + unit
  на фикстурах s59 + IT e2e (staged commentary/ambiguous → re-map →
  3 kind'а в hd_explanations).
- [ ] **T4 web**: ExplanationDto.reference + api-contract + regen.
- [ ] **T5 UI**: три секции по kind + i18n + MSW-тесты.
- [ ] **T6 live**: backfill на 33k (фон, ~20 мин) → re-map →
  playwright-проверка хадиса с гарибом (напр. 184-1: слово أَبْعَدَ) и
  с илялем (146-2) → скриншоты.
- [ ] **T7**: review → fixes → roadmap/progress/backlog (вне-скоуп
  пункты) → handoff.

## Definition of Done

1. **CI-уровень (главный гейт, фикс критика):** IT доказывает
   round-trip БЕЗ живого API — staged commentary-фикстура 146-2 →
   re-map → строка ILAL у хадиса 146-2; staged ambiguous-фикстуры
   760182/770632 + hadith-raw с ambiguous[] → re-map → строки GHARIB
   с reference-словами. Плюс unit `findByNarration` (C2).
2. Live (T6): хадис 184-1 — секция «Гариб» со словом أَبْعَدَ +
   толкования 2 словарей; 146-2 — «Иляль» с текстом Даракутни.
   Пустой результат на live = СИГНАЛ БАГА (silent-zero C2), не успех.
3. Идемпотентность backfill (upsert) + re-map (SHARH выживает);
   verify/vitest/tsc зелёные.
4. Review 0 Critical/Important; backlog дополнен вне-скоуп находками
   (chains-links-12, narrator-commentary-12, references, расширенный
   профиль рави).
