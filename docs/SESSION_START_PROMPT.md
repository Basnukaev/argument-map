# Стартовый промпт для новой сессии Claude Code

Этот файл - **стабильный** контекст начала любой сессии. Обновляется
только раздел «Текущий приоритет» (в конце документа). Остальное -
постоянное

Правила эволюции этого файла - в `docs/doc-hygiene.md` Принцип 6.
Если файл вырос за 400 строк - вылавливай дубли с CLAUDE.md /
progress.md / roadmap.md и выкидывай

---

## Режим работы

**Полная автономия.** Тактические решения - сам, без подтверждения.
Subagents - через нативный Agent tool (`subagent_type=Explore` для
research, `general-purpose` для исполнения, `Plan` для дизайна
крупных изменений).

Накапливаемые правила (full autonomy, WSL-only, не-частые-билды,
design-reference check, playwright UI verification и т.д.) живут в
локальной файловой memory под
`~/.claude/projects/-home-basnukaev-projects-argument-map/memory/`.
`MEMORY.md` index подгружается автоматически при старте сессии.

---

## START-OF-SESSION PROTOCOL

Перед первым ответом в новой сессии **выполни**:

### 1. Прочитай в таком порядке

1. **`CLAUDE.md`** (корень) - стэк, команды, layout, навигация по
   документации - уже в твоём контексте при старте
2. **Локальная файловая memory** - `MEMORY.md` index подгружается
   автоматически при старте сессии. Там накопленные `feedback_*`
   правила (full autonomy, WSL-only, no frequent builds,
   design-reference check, playwright UI verification и т.д.)
3. **`docs/progress.md`** - последние 2-3 записи + «Следующий шаг»
4. **`docs/roadmap.md`** - текущий приоритетный этап. Закрытые
   этапы свёрнуты в одну строку, активные имеют чек-лист
5. **«Текущий приоритет»** ниже в этом файле - что Абдула или
   предыдущая сессия зафиксировали как next step

### 2. По мере работы читай по запросу

- `docs/decisions.md` - если задача в принципиальной области
  (миграция, API contract, новый домен). Полный файл большой -
  читай по grep'у, не целиком
- `docs/gotchas.md` - перед миграцией / тонким Spring/JDBC кодом /
  фронтом с React Flow или RTL
- `docs/architecture.md` + `architecture-platform.md` - перед
  новой доменной сущностью или изменением core flow
- `docs/api-contract.md` - перед изменением REST endpoint или
  добавлением поля DTO
- `docs/glossary.md` - когда встретится незнакомый доменный термин
- `docs/backlog.md` - если рассматриваешь добавить новую идею
- `frontend/design-reference/` - **до** UI-изменений (см. memory
  `feedback_design_reference_check.md`)

### 3. Memory и feedback

**Локальная файловая memory** - единственный слой памяти. Живёт в
`~/.claude/projects/-home-basnukaev-projects-argument-map/memory/`.
`MEMORY.md` index подгружается автоматически при старте сессии. Там
feedback'и про decision authority, WSL-only, не-частые-билды, React
key-trick, RTL/наshк, design-reference check, playwright UI
verification, no bulk shamela parsing, no backward compat, full
autonomy mode и т.д.

Новый feedback от Абдулы (correction или validated approach) -
сохранить как `feedback_<slug>.md` с frontmatter `type: feedback` и
добавить строку в `MEMORY.md`. Подробности правил - в `auto memory`
секции системного промпта.

### 4. Проверь актуальное состояние инфры

- `git log --oneline -15` - свежие коммиты
- `docker ps | grep argumentmap-postgres` - БД healthy
- `lsof -ti:9090 -ti:5173` - что-то на портах
- Backend / frontend сам запускай по необходимости (см. CLAUDE.md
  раздел «Команды»). Не жди инструкций

### 5. Скажи Абдуле краткое summary

«вижу - последний раз X, продолжаю с Y из roadmap». Если задача
ясна - сразу за работу, не жди апрува

---

## Документация по ходу работы

После **каждого** `feat`/`fix` коммита проверь чек-лист (детали - в
`backend/CLAUDE.md` или `frontend/CLAUDE.md` секция «После коммита»):

| Что произошло | Что обновить |
|---|---|
| Закрыт пункт roadmap | `roadmap.md` `[x]` |
| Закрыт целый этап | `roadmap.md` - сжать в строку (см. `doc-hygiene.md` Принцип 3) |
| Принято решение между альтернативами | новый ADR в `decisions.md` |
| Миграция БД / новая колонка | ADR + `architecture.md` |
| Новый REST endpoint / поле DTO | `api-contract.md` |
| Поймал баг который может повториться | `gotchas.md` |
| Новое доменное понятие | `glossary.md` |
| Reorg структуры (пути / пакеты) | синхронизация всех мест (см. `doc-hygiene.md` Принцип 8) |

ADR / gotcha / api-contract пишутся **сразу**, не в конце сессии

---

## Декомпозиция и проверки

### Декомпозиция

- Задача больше 1-2 файлов → подэтапы X.a / X.b / X.c
- Между подэтапами - прогон проверок и коммит. Не один большой
- Каждый подэтап имеет внятную границу

### Когда запускать билды/тесты

**Не на каждом чихе**. Полный прогон делается **по факту**:

- В конце завершённой логической фазы
- Перед коммитом если в фазе были средние/крупные изменения
- Когда есть конкретный сигнал что что-то могло сломаться

Команды:
- Фронт: `npm run lint && npm run build && npm run test:run`
- Бэк: `./mvnw verify`
- Smoke через curl с `X-User-Id` после прохождения тестов

См. memory `feedback_no_frequent_builds.md`

---

## Контрольные точки качества handoff'а

При закрытии сессии новая сессия должна получить:

1. **Что закрыто** - запись в `progress.md` без переписывания git log
2. **Что открыто и в каком приоритете** - раздел «Текущий приоритет»
   ниже в этом файле + чек-лист в `roadmap.md`
3. **Контекст последних решений** - ADR-N или ссылка на новые
   gotcha если они были
4. **Текущая инфра** - порты / UUID / тестовая тема (если изменились)
5. **Ключевые файлы** - если в текущей задаче трогаешь редкие части
   репы и они без этой подсказки сложно найти

В конце сессии **обязательно**:

- запись в `progress.md` по формату (см. `doc-hygiene.md` Принцип 5)
- `roadmap.md` обновлён - закрытые подэтапы `[x]`, закрытые целиком
  этапы сжаты в строку
- «Текущий приоритет» ниже **переписан** под следующую сессию
- если изменилась структура / пути - синхронизация согласно
  `doc-hygiene.md` Принцип 8
- `progress.md` > 1500 строк? - архивировать в
  `docs/archive/progress-sessions-N-M.md`

---

## Текущий приоритет

> **Этот раздел обновляется каждой сессией**. Всё выше - стабильное

### Режим Сессии 46+

**Автономный без остановок** - двигаемся пока пользователь явно не
скажет «стоп». Не спрашивать «продолжить?» / «начать?» / «коммитить?».
Тактические решения сам, по логичной границе подэтапа коммит, после
коммита беру следующий пункт из списка ниже либо из `docs/backlog.md`

**Фокус сессии**: улучшение кодовой базы, стабильность продукта,
усиление тестов. Новых фичей не добавляем без явного запроса -
закрываем tech debt + security + missing test coverage из backlog

**Discipline на тяжёлые прогоны** (см. memory `feedback_verify_run_discipline.md`):
- `./mvnw verify` ~2-3 минуты в WSL2. Запускать только на ключевых
  этапах (см. список в memory). Точечный прогон одного IT класса -
  `./mvnw -Dit.test=ClassNameIT -DfailIfNoTests=false -Dsurefire.skip=true verify` (~15-30s)
- Не запускать full verify «на всякий случай» между логическими блоками,
  после косметического edit'а, сразу после subagent'а который сам прогнал verify

**Если задачи закончились** - смотрим `docs/backlog.md`, секции:
- «Tech debt / performance optimization»
- «Security backlog»
- «Бэк - бэклог»
- «Фронт - общие улучшения»

И двигаемся по приоритету (Critical → Important → Minor)

### ⭐ АКТУАЛЬНО — entry Сессии 57 (после alminasa Планов 1-2 Сессии 56)

**Сессия 56 — alminasa = единственный источник (ADR-060), Планы 1-2 закрыты.**
Детали — `docs/progress.md` (запись Сессии 56). Кратко:
- **План 1** ✅: миграции 70-71 (alminasa-колонки hd_* + 5 таблиц editions/rulings/
  explanations/crossrefs/narrator-relations), домен+репозитории с findByExternalId.
- **План 2** ✅: миграция 72 (`am_staging_*` + `am_crawl_checkpoint`),
  `hadith/alminasa/` — AlminasaEsClient (ES-прокси `es-prod-euw1-{index}-read`,
  search_after по hadith_serial_id, terms-батчи, @Retry alminasaApi),
  AlminasaCrawlService (resumable hadith-first краулер: pause/resume/stale-takeover,
  чекпоинт на границе страницы, абсолютный fetched_count), admin REST
  `/api/v1/admin/alminasa/crawl/{start,pause,status}` (202/200/200, 409). 31 тест.
  Фикстуры из HAR: `backend/src/test/resources/alminasa/`.
- **Верификация:** backend verify **1324/1324 BUILD SUCCESS**, frontend vitest
  **720/720**, tsc clean. Финальный multi-review: 0 Critical/Important.

**СЛЕДУЮЩИЙ ШАГ (по порядку):**
1. **🔴 План 3 — маппер staging→hd_*** (через writing-plans, спека §C):
   детерминированный парс иснада из `full_text_ar` по `<a class=rawy id=N>`
   (порядок тегов = narrators[], реверс → position 0 = Пророк ﷺ, как
   IsnadPersistenceService), upsert по external_id (Plan 1 готов), cross-refs из
   raw_narrations, рулинги/шархи/relations, book-id→slug map (146=البخاري),
   статус хадиса правилом (сахихайн→CANONICAL). Unit-тесты на реальном
   hadith-HTML из фикстур. End-to-end IT одного хадиса.
2. **План 4 — выпил legacy**: sunnah ETL (`hadith/sunnah/**`, `sn_staging_*`
   drop-миграцией, AdminSunnahPage, sunnah.dump.*, docker sunnah-mysql) +
   AI-иснад (`hadith/isnad/**`, ADR-059 → superseded). SanadGraphService/
   SanadGraph ОСТАЮТСЯ (переиспользуются).
3. **План 5 — AdminHadithImportPage** (каталог/прогресс/dry-run/resume),
   **Планы 6-7** — фронт-данные + AI-перевод.
4. **🖐️ Ручные проверки UI** (накоплено с Сессии 55, playwright env-blocked):
   archive.org FILE_ONLY ридер, content_kind кнопки, bbox-подсветка,
   DeepSeek-метаданные. Плюс новое: admin alminasa endpoints (curl ниже).

**⚠️ Гейты и residual-риски alminasa:**
- **Полный обход 12 сборников — ТОЛЬКО после ответа alminasa** (backlog «Связаться
  с alminasa.ai»; письмо пишет Абдула). Dev-краулинг 1-2 страниц для отладки — ок.
- `terms.id` к narrators-12 — единственное live-предположение без тестового сигнала;
  проверить первым dev-краулингом (narrators должны прийти непустыми).
- Вкладки علل/غريب — контракты не сняты; перед Планом 6 свежий HAR.

**Инфра:** Docker (postgres+minio) up. Backend :9090 + JDWP :5005 (рестарт —
команда в CLAUDE.md; sunnah-mysql `:3307` НЕ нужен для alminasa-работы, нужен
только если трогаешь sunnah-legacy до Плана 4). Для AI-фич: `--ai.provider=...`
+ ключ аргументом (НЕ в репо); за корп-прокси `--ai.http.proxy=...`.
migrations через **72**. Psql роль `argmap`. frontend :5173.
Admin для curl: `00000000-0000-0000-0000-000000000001`. HAR'ы в gitignore;
полные сэмплы ответов alminasa — `/tmp/alminasa-fixtures/` (если /tmp пережил
ребут) либо пере-извлечь из HAR в корне.
Smoke alminasa: `curl -s -H "X-User-Id: 00000000-0000-0000-0000-000000000001" http://localhost:9090/api/v1/admin/alminasa/crawl/status` → `{"status":"IDLE",...}`.
**Дев-данные:** fmhji (FILE_ONLY, 4 файла); bukhari hd_* 1 хадис (sunnah-legacy).

### Историч. снапшоты (Сессии 47/49d/49c) — сжаты

Снапшоты приоритетов прошлых сессий убраны (doc-hygiene Принцип 6); детали — в `docs/progress.md` и `docs/archive/progress-sessions-*.md`.

### Известные мелочи (не блокеры)

- **progress.md > 1500 строк** - проверять при handoff, при
  превышении - архивировать в `docs/archive/progress-sessions-N-M.md`
- **jsdom + node 24 не парсит multipart FormData** - в
  `FileUploadModal.test` тесты multipart используют mock
  `globalThis.fetch` (зафиксировано в комментарии теста)
- **Node 24 + undici 7 AbortSignal instanceof bug** - workaround
  в `frontend/src/test-setup.ts` (см. `docs/gotchas.md`)
- **PDFBox text_content=""` для scanned-PDF** проходит CHECK
  `lib_pages_content_present` (NULL only check) - OCR pipeline
  (Этап 17) seed'ит эти пустые text_content. Закрыто в Этапе 17
- **playwright WSL2 не загружает Google Fonts** через corp proxy
  407 - визуальная проверка шрифтов только в реальном браузере

