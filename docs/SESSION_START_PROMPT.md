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

### Snapshot состояния на entry Сессии 47

**Сессия 46 закрыла 11 tasks** (21 коммитов, см. `docs/progress.md`
запись от 2026-05-19 «Сессия 46 - Tech debt + Security sweep»):
1. Baseline fixes (PG TIMESTAMPTZ vs Java Instant precision)
2. Actuator behind basic auth в prod (ADR-048)
3. RefreshTokenCleanupJanitor (ADR-047 follow-up)
4. PATCH /api/v1/topics/{id} (title/description editing)
5. NodeTranslationService promoteToDefault helper
6. Audit log для удалённых тем (ADMIN forensics)
7. Authority.type для HadithGrade scholar validation (миграция 47)
8. Shared MinIO Testcontainer (9 IT мигрированы)
9. BookSummaryResponse.createdBy + frontend MINE filter
10. 6 review fixes по итогам `/superpowers:requesting-code-review`
11. Baseline AuthServiceRotation/TopicMemberServiceIT/UserUpload fixes

Тестов: backend 988→999, frontend 565→571. ADR-048 добавлен,
миграция 47 применена.

### Tech debt / Security приоритеты Сессии 47

Backlog оставшиеся (читать `docs/backlog.md` для полного списка):

1. **Z-index persistence для edges** - mirror Node.zIndex (миграция 40 + REST
   endpoint). Сейчас edge z-order ephemeral. Low priority пока не critical
2. **Bulk audit log consolidation** - один BULK_DELETE с entityIds[] вместо
   N rows. Acceptable пока admin audit UI отложен
3. **AuditEntityType / UserRole single source of truth** - сейчас BE String
   константы + FE whitelist в dictionary/types.ts. Расхождение возможно.
   Low priority пока entity types <15
4. **Cursor-based pagination** - сейчас offset OK. Cursor нужен при миллионах
   записей либо stable порядок при concurrent inserts
5. **PdfControllerIT flaky** - `streamPdf_withRange_returnsPartialContent`
   ловит ConcurrentModificationException в `MockHttpServletResponse`. В
   изоляции pass'ает. Не блокер
6. **Frontend UI Authority.type селект** - в AddAuthorityForm и edit modal.
   Backend готов (ADR из Сессии 46), frontend регенерация types.ts уже
   получит type field
7. **AuthorityService.updateAuthority для смены type** - на existing rows
   через прямой SQL сейчас. Нужен PATCH endpoint
8. **HadithGradeService.updateGrade re-validate scholar type** - сейчас
   проверка только при addGrade. Если scholar изменил тип после создания
   grade - стейл

После tech debt - можно браться за фичи:
- Этап 18.e ImagePageRenderer (mode для image-сканов, после Этапа 17)
- Этап 25.d.2/25.d.4 PDF Viewer полировка
- Этап 25.e admin manual page-mapping (Tier 1)
- Source picker для Корана / Хадисов (внешние API)

Полный backlog в `docs/backlog.md`

### Инфра на entry Сессии 47

- Postgres :5432 healthy, миграции до 47 включительно applied
  (47 - `authorities.type`, от Сессии 46)
- MinIO :9000 healthy + 4 bucket'а (library-imported-books,
  library-user-uploads, library-page-images + один служебный)
  через `BucketBootstrap`
- Backend :9090 + JDWP :5005 - запускать с JDWP args (см. CLAUDE.md)
- Frontend :5173 - dev server, после массовых регенераций может
  потребовать `rm -rf node_modules/.vite`
- **Текущий baseline tests** запущен в Сессии 46 entry - смотри
  свежую запись в `docs/progress.md` для финальных цифр

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

