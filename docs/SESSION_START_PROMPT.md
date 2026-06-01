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

### ⭐ АКТУАЛЬНО — entry Сессии 53

**Сессия 53 закрыла Phase 5 ETL шаг 2 ПОЛНОСТЬЮ (2.a-2.e) + прогнала реальный
пилот** (~10 коммитов `2b24e76..HEAD`, см. progress.md): конвейер **дамп →
`sn_staging_*` → mapper → `hd_*`** + прод-обвязка + admin REST. **98 хадисов
Бухари импортированы из настоящего дампа** в hd_*. ~45 тестов + 2 multi-agent
review (0 Critical обе) + de-flake BookRepositoryIT. Backend **0 реальных
failures** (системная full-suite flakiness — в backlog, отдельная тест-гигиена).

**Phase 5 ETL** (спека `docs/superpowers/specs/2026-05-31-sunnah-etl-design.md`
§11). Эпик ~ещё 1 сессия:
1. ✅ **step 1** (Сессия 51, ADR-050): migration 57 `hd_collections`.
2. ✅ **step 2 (2.a-2.e)** (Сессия 53, ADR-051/052): staging + DAO + mapper +
   normalizer + `SunnahDumpReader` (РЕАЛЬНАЯ денормализ. схема: 7 таблиц,
   дробный babID → `chapter_id` varchar) + `SunnahImportService` + **прод-обвязка**
   (`SunnahDumpProperties` + conditional MySQL DataSource + `SunnahAdminController`
   ADMIN-only, bulk-policy gate). **Реальный пилот прогнан**: `POST /import/bukhari`
   → 98 импортировано (2 курируемых победили). Дедуп вариаций + структурный иснад
   — ОТЛОЖЕНЫ.
3. ✅ **Под-проект #1 (просмотр/дебаг хадисов)** — СДЕЛАН (спека
   `2026-06-01-hadith-viewing-tool-design.md`): `SunnahTextCleaner` (срез
   markup, перечистка), `GET /hadith/collections` + sort + `previewMatn`
   (диакритизированный), редизайн `HadithListPage` (чипы-сборники + sort +
   чистые арабские карточки). ⚠️ Playwright НЕ прогнан (MCP chromium missing).

**ПИВОТ Абдулы (важно для приоритизации): контент — в последнюю очередь,
строим ИНСТРУМЕНТЫ** (заполнение/просмотр/дебаг контента). Очередь под-проектов:
4. **Под-проект #2 — линковка хадисов в узлы** (спека
   `2026-06-01-hadith-node-citation-design.md`). **#2.A backend ✅** (`84a565e`):
   `HadithCitationService` (мост `Hadith.sourceId`) + `POST /nodes/{id}/
   hadith-citations` + IT. ← **СЛЕДУЮЩИЙ ШАГ — #2.B, порядок строгий:**
   - **(a) #2.B.backend — обогащение source-списка (ещё НЕ сделано, это backend):**
     `HadithRepository.findBySourceIds(List<UUID>)` (batch reverse-lookup) +
     обогатить `GET /nodes/{id}/sources` для HADITH-источников полями
     `hadithId`/`previewMatn`/`collectionName`/`primaryNumber` в `NodeSourceResponse`
     (сейчас их нет — без них фронт не нарисует хадис-опору) + IT.
   - **(b) рестарт backend** (с `SUNNAH_DUMP_*` env, команда ниже) → **`generate-api`**
     (тогда в types.ts появятся И POST endpoint, И обогащённый NodeSourceResponse).
   - **(c) #2.B.frontend — picker:** НЕ переиспользовать `HadithListPage` (это
     full-page: Header + `<Link>`-навигация, нет onSelect/reusable export).
     Сделать **новую `HadithPickerModal`** ({open && <Modal/>}), переиспользующую
     хук `usePagedSearch` + `GET /api/v1/hadith/hadiths` (q/status/collectionId/
     sort, `PagedResponse<HadithResponse>`, previewMatn на карточке), с
     `onSelect(hadithId)` вместо Link. Структурный референс — `CitationPicker.tsx`.
   - **(d) рендер + кнопка:** site = `NodeCitationsSection.tsx` (секция «Опора»).
     Добавить 3-ю кнопку «прикрепить хадис» рядом с существующими; ветку
     рендера HADITH-опоры (по `hadithId` из обогащённого ответа: сборник·№ +
     matn-сниппет naskh + ссылка на `/hadith/{hadithId}`) ПЕРЕД FreeformCite
     fallback. Контракты: `api-contract.md` GET /hadith/hadiths + POST
     /nodes/{id}/hadith-citations.
5. **Под-проект #3 — `hd_collections` ↔ библиотечный «Сборник хадисов»**
   (book_type=HADITH): два представления одного сборника, архитектура.
   + опц. frontend AdminSunnahPage (импорт без curl).
6. **Под-проект #4 / Phase 5 step 3 `IsnadExtraction`** (КОНТЕНТ, отложено
   Абдулой): matn+isnad блоб → AI (ADR-042) → hd_sanads + trust-level. step 4
   `SunnahApiClient` + полный корпус (sample-дамп = только 100 хадисов Бухари).

**Manual за Абдулой:** **НОВОЕ — глянуть `/hadith` на :5173** (редизайн +
реальные данные Бухари, текст теперь чистый; Playwright не прогнан, нужен
визуальный взгляд). Висит с Сессии 52: dark-theme primary Button hover,
thesis-книга 15 рендер, минимап при detail-панели.

**Инфра:** Docker (postgres+minio) up + **`sunnah-mysql` :3307** (root/root,
БД `sunnah`; SQL-дамп лежит как `db/00-samplegitdb.sql` ВНУТРИ контейнера,
host-копия — `/tmp/sunnah.sql`, re-fetch: `curl -sL raw.githubusercontent.com/
sunnah-com/api/master/db/00-samplegitdb.sql`). Backend :9090 запущен **с
`SUNNAH_DUMP_*` env** (см. progress.md «Инфра пилота») + JDWP :5005 — без этих
env импорт-endpoint → 503. ⚠️ Работающий JVM стартовал ДО коммита #2.A
(`84a565e`) — endpoint `/nodes/{id}/hadith-citations` НЕ в нём; перед
`generate-api` нужен полный рестарт backend. migration 59 применена. Дев-
Postgres: 101 hd_hadiths (3 CANONICAL сид + 98 VARIANT импорт Бухари),
0 matns с markup. frontend :5173. Admin для curl:
`00000000-0000-0000-0000-000000000001`.

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

