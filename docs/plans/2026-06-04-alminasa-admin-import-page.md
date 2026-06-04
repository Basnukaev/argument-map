# alminasa Hadith Ingestion — Plan 5: AdminHadithImportPage + import-REST

> **Оркестрация:** OMC. Скаффолд REST — по чек-листу скилла
> `new-rest-endpoint` (DTO→Service→Controller→IT→api-contract→regen).
> После исполнения — независимый review + playwright-верификация UI.

**Goal:** админка импорта alminasa: каталог 12 сборников (staged/mapped
прогресс), управление краулером (существующие endpoints Плана 2),
async-запуск маппера (рави / хадисы / по сборнику), dry-run превью одного
хадиса ДО записи (философия «поэтапного проверяемого импорта»).

**Спека:** `docs/specs/2026-06-03-alminasa-hadith-source-design.md` §E.
**База:** План 3 (маппер+dry-run service-методы ГОТОВЫ), План 2 (crawl REST),
образец UI — бывшая AdminSunnahPage (паттерны в git-истории, 2d1e752^).

## Дизайн-решения

1. **Каталог всегда показывает все 12 сборников** (статическая карта
   `AlminasaCollections`), даже при пустом staging: bookId, slug, nameAr
   (staging `book_name` приоритетнее карты), nameRu, stagedCount,
   mappedCount. Источники: новый `AmHadithStagingDao.catalogByBook()`
   (один SQL: book_id, max(book_name), count(*) GROUP BY).
   **mappedCount = ТОЛЬКО alminasa-хадисы (фикс C1 критика):** новый
   `HadithRepository.countByCollectionGroupedForSource(String
   externalSource)` (`WHERE collection_id IS NOT NULL AND external_source
   = ?`) — `countByCollectionGrouped()` без фильтра посчитал бы legacy
   sunnah-строку bukhari в dev-БД. `findBySlug` пуст (сборник ещё не
   создавался маппером) → mappedCount=0, не ошибка. Обязательный IT:
   смешанная коллекция (1 alminasa + 1 sunnah) → mappedCount==1.
2. **Импорт — async на single-thread executor.** Новый
   `AlminasaImportLauncher` + executor-бин в НОВОМ `AlminasaImportConfig`
   **БЕЗ** `@ConditionalOnProperty(alminasa.enabled)` (импорт работает
   чисто по локальному staging, alminasa-API не нужен; crawl-конфиг
   гейтится — НЕ переиспользовать его бин: при enabled=false верстка
   сломается). queue=0 + AbortPolicy. In-memory `AtomicReference<State>`
   (status IDLE/RUNNING, kind NARRATORS/HADITHS/ALL, bookIdFilter,
   startedAt, processedSoFar, lastSummary, lastError — bookIdFilter/
   startedAt/kind живут в State, НЕ расширять AlminasaImportSummary).
   **Контракт переходов (фикс C2):** RUNNING ставится СИНХРОННО в
   launch-методе ДО submit (TaskRejected → откат в IDLE + 409); async-тело
   ОБЯЗАНО `try { summary → IDLE+lastSummary } catch (RuntimeException →
   IDLE+lastError) finally { гарантия status != RUNNING }` — иначе один
   transient-фейл навечно лочит 409. Обязательный IT: запуск с гарантированно
   падающим импортом → await IDLE + lastError != null + повторный запуск
   202 (не 409). Один executor сериализует ВСЕ виды импорта (narrators
   при работающем hadiths → 409 — осознанно). Повторный запуск при
   RUNNING → `AlminasaImportConflictException` → **409**
   `alminasa-import-already-running` (IT на 409 — ТОЛЬКО latch-вариант,
   «быстрый маленький датасет» флакает). Состояние НЕ переживает рестарт —
   осознанно; рестарт бэка = аварийное восстановление (документировать в
   javadoc).
   **Live-прогресс (фикс M1, решение b):** `AlminasaImportService.
   importNarrators/importHadiths` получают перегрузку с
   `IntConsumer onProcessed` (null-safe; существующие сигнатуры/ITs не
   трогаются) — launcher инкрементит processedSoFar, статус-эндпоинт
   отдаёт его при RUNNING.
3. **Dry-run** — `GET /dry-run/{hadithId}` (семантически read-only:
   маппинг+rollback). **Граница рефактора (фикс M2): меняется ТОЛЬКО
   `AlminasaHadithMapper:208`** (`findById().orElseThrow`) → новый
   `AlminasaStagingNotFoundException` → **404** `alminasa-staging-not-found`.
   Пустой матн (`mapHadith:142`) ОСТАЁТСЯ `AlminasaMappingException`
   (на него завязаны AlminasaImportServiceIT:190 и AlminasaMapperIT:297 —
   НЕ трогать) → **422** `alminasa-mapping-failed` (+handlers в
   GlobalExceptionHandler). IT: dry-run нестейдженного id → 404;
   staged-но-пустой-матн → 422. UI: 404 → «хадис не найден в staging»,
   422 → текст ошибки маппинга.
4. **ADMIN-гейт** — как AlminasaAdminController (requireAdmin-паттерн
   существующего контроллера; 403 `forbidden-admin-only`, 401 без
   principal).
5. **Массовый ГЕЙТ не нарушаем**: UI запускает только маппинг УЖЕ
   застейдженных данных (локальная БД) — гейт alminasa касается краулинга
   (обхода их API), кнопки start/pause краулера уже есть и остаются
   page-by-page resumable.
6. **Frontend** — новая `AdminHadithImportPage` (route
   `/admin/hadith-import`, ProtectedRoute ADMIN): секции Краулер (статус
   + start/pause, poll 3s при RUNNING), Каталог (таблица 12 строк,
   прогресс staged→mapped, кнопка «Маппинг» per-book + «Маппинг всего» +
   «Импорт рави»), Статус импорта (poll 3s при RUNNING: processedSoFar
   live; по завершении — summary с failures), Dry-run (input `146-1` →
   превью: поля, цепь (position/имя/формула), counts сателлитов; 404/422
   — явные сообщения). **Импорт-кнопки disabled пока crawl RUNNING (фикс
   M3)** — tooltip «дождитесь паузы краулера»; сервер НЕ блокирует
   (идемпотентность лечит частичные данные re-run'ом — задокументировать
   в api-contract). Карточка дашборда alminasa УЖЕ существует
   (AdminDashboardPage:76, disabled) — НЕ создавать новую, а включить:
   action navigate `/admin/hadith-import`, обновить cta. i18n
   `admin.hadith.*` ru+ar. UI-паттерны бывшей AdminSunnahPage: explicit
   error-state, key-remount, RTL `dir`.
7. **Все 5 endpoints — в существующем `AlminasaAdminController`**
   (103 строки, когезия; отдельный контроллер не нужен).

## Endpoints (api-contract в том же коммите)

| Метод | Путь | Ответ | Ошибки |
|---|---|---|---|
| GET | `/api/v1/admin/alminasa/catalog` | `List<AlminasaCatalogEntryResponse>` | 401/403 |
| GET | `/api/v1/admin/alminasa/import/status` | `AlminasaImportStatusResponse` | 401/403 |
| POST | `/api/v1/admin/alminasa/import/narrators` | 202 + status | 401/403/409 |
| POST | `/api/v1/admin/alminasa/import/hadiths?bookId=` | 202 + status | 401/403/409 |
| GET | `/api/v1/admin/alminasa/dry-run/{hadithId}` | `AlminasaDryRunResponse` | 401/403/404/422 |

DTO: `AlminasaCatalogEntryResponse(bookId, slug, nameAr, nameRu,
stagedCount, mappedCount)`; `AlminasaImportStatusResponse(status, kind,
bookIdFilter, startedAt, narratorsProcessed/Failed, hadithsProcessed/
Failed, crossrefsResolved, relationsResolved, failures[], error)`;
`AlminasaDryRunResponse` — маппинг `AlminasaDryRunResult` (hadith-поля,
matnPreview, chain[{position, externalId, nameAr, formula}], counts).

## Tasks

- [x] **T1 backend** (коммит 1): `AmHadithStagingDao.catalogByBook()` +
  `HadithRepository.countByCollectionGroupedForSource` +
  `AlminasaCatalogService` + `AlminasaImportLauncher` + новый
  `AlminasaImportConfig` (executor, БЕЗ alminasa.enabled-гейта) +
  `AlminasaStagingNotFoundException` (только mapper:208) + перегрузки
  ImportService с IntConsumer + 5 endpoints в `AlminasaAdminController`
  + DTO + handlers 404/422/409 + **IT ≥11 кейсов** (MockMvc: happy
  202/200 narrators и hadiths, 401, 403 non-admin, 409 double-launch
  ЧЕРЕЗ LATCH, импорт-фейл → IDLE+lastError+повторный 202, bookId-фильтр
  скоупит импорт одним сборником, dry-run 200/404/422, catalog counts
  смешанная коллекция alminasa-only, catalog при пустом staging — 12
  строк с нулями) + api-contract.md.
- [x] **T2 regen** (коммит 2): backend up → `npm run generate-api` → tsc.
- [x] **T3 frontend** (коммит 3): AdminHadithImportPage + роут + карточка
  дашборда (enable) + i18n + MSW-тесты (каталог рендер, dry-run флоу,
  conflict 409 toast, статусы краулера/импорта).
- [x] **T4 верификация**: полный verify + vitest + tsc; **playwright
  headless** (standalone-скрипт, bundled chromium): дев-краул 1-2 страниц
  (гейт ОК) → скриншоты каталога/статусов → dry-run 146-1 живьём →
  импорт bukhari staged-подмножества → проверка каталога mappedCount →
  **очистка (точный SQL):** hd_* — сначала сателлиты алminasa-хадисов
  (`DELETE FROM hd_matns/hd_sanad_narrators/hd_sanads/hd_hadith_editions/
  hd_rulings/hd_explanations/hd_hadith_crossrefs WHERE hadith_id IN
  (SELECT id FROM hd_hadiths WHERE external_source='alminasa')`; у
  sanad_narrators — через sanad_id), затем `DELETE FROM hd_narrator_relations
  WHERE narrator_id IN (SELECT id FROM hd_narrators WHERE
  external_source='alminasa')`, затем hd_hadiths/hd_narrators по
  external_source='alminasa' (FK-порядок; ON DELETE CASCADE проверить —
  если есть, хватит родителей); staging — `TRUNCATE am_staging_hadith,
  am_staging_narrator, am_staging_explanation, am_staging_ruling`;
  чекпоинт — `UPDATE am_crawl_checkpoint SET status='IDLE',
  last_sort_value=NULL, last_sort_id=NULL, fetched_count=0, total_hits=
  NULL, error=NULL` (как С56). Smoke: catalog → 12 строк нули, crawl
  status IDLE.
- [x] **T5 review**: независимый code-review диапазона → Critical/Important
  → fix-коммиты → roadmap.

## Definition of Done

1. 5 endpoints живые, IT ≥10 кейсов, api-contract + types.ts в синхроне.
2. UI: каталог 12 строк, краулер start/pause, импорт с прогрессом,
   dry-run превью с цепью и формулами.
3. Playwright-скриншоты сняты, dev-данные очищены, чекпоинт IDLE.
4. Review: 0 открытых Critical/Important.
