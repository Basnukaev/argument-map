# Стартовый промпт для новой сессии Claude Code

Этот файл - **стабильный** контекст начала любой сессии. Обновляется
только раздел «Текущий приоритет» (в конце документа). Остальное -
постоянное

Правила эволюции этого файла - в `docs/doc-hygiene.md` Принцип 6.
Если файл вырос за 400 строк - вылавливай дубли с CLAUDE.md /
progress.md / roadmap.md и выкидывай

---

## Режим работы

**Полная автономия + ruflo-first.** Двигаемся в сторону полного
перехода на ruflo way работы со всех сторон (subagents, memory,
session continuity, tasks, learning, code review). Strategic direction
зафиксирован в `mcp__ruflo__memory_retrieve namespace=argument-map
key=strategic-direction-ruflo-migration`.

Правила автономии и subagent usage:
- `mcp__ruflo__memory_retrieve namespace=argument-map key=autonomy-mode`
  - текущий snapshot правил с semantic recall (ruflo-first variant
  v2 от 16.05). Subagents - **ВПРЕДЬ через ruflo**: для long-running
  implementation tasks `mcp__ruflo__hive-mind_spawn` или
  `mcp__ruflo__swarm_init`+`agent_spawn`, для quick research можно
  нативный Agent subagent_type=Explore
- `mcp__ruflo__autopilot_status` - состояние long-horizon resumption
  (включён 16.05, maxIterations=200, timeoutMinutes=720)
- `mcp__ruflo__agentdb_pattern-search` - cross-session architectural
  patterns (например ADR-033 «параллельная иерархия» сохранён как
  `type=architectural-decision`)

Новая сессия первым делом делает `mcp__ruflo__memory_retrieve`
для `autonomy-mode` + `strategic-direction-ruflo-migration` перед
чтением остальной документации.

---

## START-OF-SESSION PROTOCOL

Перед первым ответом в новой сессии **выполни**:

### 1. Прочитай в таком порядке

1. **Ruflo memory** - `mcp__ruflo__memory_retrieve namespace=argument-map
   key=autonomy-mode` - правила автономии. Ранее были в `feedback_full_
   autonomy_mode.md`, теперь живут в ruflo с semantic recall между
   сессиями. И `key=argument-map-19b-completion-state` для последнего
   completion snapshot
2. **`CLAUDE.md`** (корень) - стэк, команды, layout, навигация по
   документации - уже в твоём контексте при старте
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

Два слоя памяти после миграции 16.05:

- **Ruflo memory** (primary) - `mcp__ruflo__memory_retrieve` /
  `mcp__ruflo__memory_search_unified` / `mcp__ruflo__agentdb_pattern-
  search` с `namespace=argument-map`. Sub-cross-session recall через
  HNSW vector store. Сюда переехал autonomy mode + architectural patterns
- **Локальная файловая memory** (legacy) - в
  `~/.claude/projects/-mnt-c-my-folders-projects-argument-map/memory/`.
  Прочитай `MEMORY.md` index при старте - остались feedback'и про
  decision authority, WSL-only, не-частые-билды, React key-trick,
  RTL/наshк, design-reference check, playwright UI verification,
  no bulk shamela parsing, no backward compat. Постепенно мигрировать
  в ruflo при касании

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

**Если задачи закончились** - смотрим `docs/backlog.md`, секции:
- «Tech debt / performance optimization»
- «Security backlog»
- «Бэк - бэклог»
- «Фронт - общие улучшения»

И двигаемся по приоритету (Critical → Important → Minor)

### Snapshot состояния на entry Сессии 46

**Последние закрытые этапы / работы** (детали - в `docs/progress.md`):
- **2026-05-19 ADR-046 Rate limiting** (`/auth/login` + `/auth/register`)
  - in-memory sliding window 1 мин + lockout 15 мин, opt-in через env
  в prod (default disabled), whitelist 127.0.0.1+::1, port-stripping IP,
  Clock injection для testability. 20 тестов
- **2026-05-19 ADR-047 Refresh token rotation** - single-use refresh с
  `refresh_tokens` таблицей (миграция 46), SHA-256 hashing, steal
  detection (revoke-all-chain при reuse), `jti` claim в JWT. 11 IT
- **2026-05-19 Test coverage audit + IT для 5 untested services** -
  `AuthorityService`, `SourceService`, `NodeSourceService`,
  `NodeProjectionService`, `TopicMemberService`. JaCoCo backend +
  coverage-v8 frontend для аудита. Отчёт - в `docs/superpowers/audits/`
- **2026-05-18 Backend arch audit** - 8 findings, 4 fix'а
  применены (NodeProjectionService, VisibilityPolicy, Source guards;
  Actor record отложен в backlog)
- **2026-05-18 Stability/quality round** - backend + frontend audit'ы,
  E2E Playwright suite (44 tests, 7 suites), translator attribution
  (миграция 45)

### Tech debt / Security приоритеты Сессии 46

В порядке убывания приоритета. Каждый - отдельный коммит / atomic
подэтап. Беру первый available, в конце - следующий

1. **Actuator endpoints behind auth в prod** (Crit Security #7) -
   сейчас `/actuator/**` permitAll во всех profiles. В prod -
   basic auth (`spring.security.user.{name,password}` из env) на
   actuator path, кроме `/actuator/health` + `/actuator/info` для
   LB liveness/readiness. Dev/test остаётся permitAll. IT в prod
   profile: 401 для protected, 200 для health/info, basic auth
   разрешает access
2. **RefreshTokenCleanupJanitor** (pre-prod mandatory) - daily cron
   `@Scheduled` cleanup для revoked старше N дней + expired never
   used. Replicate pattern `AuditLogRetentionJanitor` (CondOnProp +
   retention property + cron). Без него `refresh_tokens` растёт
   линейно от login activity
3. **PATCH /api/v1/topics/{id}** (round 4 #10) - редактирование
   title/description. Сейчас readonly в `TopicSettingsDrawer` -
   нет endpoint'а для rename. Permission canWrite, audit log
   UPDATE с FieldDiff(title, description). Frontend form в drawer
4. **NodeTranslationService DRY** (round 4 #2) - private helper
   `promoteToDefault(nodeId, candidateId)` извлечь из дубля в
   `addTranslation` + `removeTranslation`
5. **Audit log для удалённых тем** (round 3 #6) - `assertCanWrite`
   404 если topic deleted → admin не может посмотреть кто удалил
   через `/audit/topics/{id}`. Special case либо 410 Gone
6. **Authority.type column для HadithGrade scholar** (round 3 #4) -
   миграция + CHECK SCHOLAR/MUHAQQIQ/PUBLISHER/AUTHOR, валидация в
   `HadithGradeService.addGrade`. Либо ADR на flat namespace
7. **Shared MinIO Testcontainer** (flag'нут 2 раза) - 9 IT
   классов поднимают свой MinIOContainer = 45-90 сек overhead на
   verify. Singleton либо `withReuse(true)`
8. **BookSummaryResponse.createdBy** (round 4 #8) - frontend «Мои»
   filter сейчас approximates через `visibility==='PRIVATE'`. Добавить
   `createdBy: UUID` для strict matching

После всех 8 - смотрим `docs/backlog.md` под другие пункты (Edge
z-order persistence, Bulk audit consolidation, Cursor pagination,
Translation editor UI, Cross-references drawer, и т.д.)

### Инфра на entry Сессии 46

- Postgres :5432 healthy, миграции до 46 включительно applied
  (последняя - `46-create-refresh-tokens.xml` от ADR-047)
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

