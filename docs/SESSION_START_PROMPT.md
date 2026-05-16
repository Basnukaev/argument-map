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

**Сессия 36 epic - 32+ коммита, 9 ruflo agents, 5 этапов закрыто.**
Полный summary в ruflo memory: `mcp__ruflo__memory_retrieve
namespace=argument-map key=session-36-final-snapshot` + детальный
лог в `docs/progress.md` запись от 16.05.

### Опции для Сессии 37 (по приоритету)

**Опция A - RetryStrategy migration (последний 25.b пункт)** -
AWS SDK v2 legacy `RetryPolicy.defaultRetryPolicy().toBuilder()` →
новый `RetryStrategy` API в `S3ClientConfig`. Крупный refactor с
blast radius (затрагивает all S3 calls). ~1-2 часа

**Опция B - Source picker для Корана** (исламский backlog) - таб
«Коран» в CitationPicker с навигацией по сурам, выбор аята, inline
вставка с цитатой и переводом. Backend: integration с quran.com API
или локальный mushaf-датасет. Frontend: новый таб в CitationPicker
рядом с «Library». **Зависит от внешнего источника данных** - может
блокироваться

**Опция C - Source picker для Хадисов** - таб «Хадисы» с 9
сборниками, фильтр по grade (sahih/hasan/daif), показ иснада.
Интеграция с sunnah.com. Аналогично Опции B - **зависит от внешнего
API**

**Опция D - Этап 16 PDF/EPUB upload** - второй способ добавления
книг (помимо Shamela). Apache Tika dependency + FileImportService +
`POST /library/imports/file` multipart. MinIO storage уже готов

**Опция E - 18.h.A1 NodeCard footer chips** - раздельный count
library vs freeform на самой карточке узла в графе. Backend
NodeResponse расширить + aggregate JOIN. ~30 мин

**Опция F - cleanup**:
- 1 unrelated pre-existing fail в `AddSourceModal.test.tsx` про
  reliability radio (existed до Сессии 36)
- `package.json` + `package-lock.json` в корне репы (ruflo
  artefacts от agentic-flow npm) - решить gitignore vs commit
- Manual review layout AnswerCard когда длинный body + раскрытая
  секция «Источники» (19.d agent отметил что playwright headless
  не на 100% это покажет)

### Инфра на момент Сессии 37 entry

- Postgres :5432 healthy, миграции **до 31 включительно** applied
  (28 question_sources, 29 answers, 30 accepted_answer_id, 31
  answer_sources)
- MinIO :9000 healthy
- Backend :9090 + JDWP :5005 - перезапущен в Сессии 36 с новым
  classpath (resilience4j + healthIndicator + AsyncWebConfig +
  19.c/19.d + 25.b). Стартует с логом
  «S3 timeouts: attempt=30s total=135s connect=5s» +
  «MVC async executor configured: core=10, max=50, queue=100»
- Frontend :5173 - может потребовать `rm -rf node_modules/.vite`
  после массовых изменений (CitationPicker, types.ts регенерация)
- **521 backend tests** (484 до Сессии 36, +37)
- **146/147 frontend tests** (1 unrelated pre-existing
  AddSourceModal radio test)

### Реальные artefacts в production-БД для smoke

- Test question `3796f633-1822-45fa-87e1-6337a603b6f1` с
  attached citation (19.b validation)
- Test answer `8872e584-ca2e-4b33-95ce-f3e545df55bb` со status
  ANSWERED + attached citation (19.c + 19.d validation)
- Source `132d75cc-cf4e-4d24-beb3-a4859ba0b776` (тафсир Ибн
  Касира) **reused между 3 entity types**: node_sources +
  question_sources + answer_sources - физический proof ADR-018
  platform pivot

### Ruflo memory keys (Сессия 36) для load в новой сессии

```
mcp__ruflo__memory_retrieve namespace=argument-map key=...
```

Priority (читать в порядке):
1. `autonomy-mode` - правила автономии (ruflo-first v2)
2. `ruflo-max-utilization-rule` - всегда использовать ruflo на максимум
3. `ruflo-execution-pattern` - lessons: ruflo координирует, Claude
   Code executes
4. `strategic-direction-ruflo-migration` - long-term goal
5. `session-36-final-snapshot` - снэпшот середины Сессии 36
6. `e2e-platform-validation-3-entities` - production E2E proof
7. `stage-19d-implementation-result`, `stage-20e-implementation-result`,
   `stage-25b-orphan-janitor-result`, `stage-25b-integrity-cron-result`
   - completion reports от subagents
8. `test-regression-fix-plan` - Node 24 undici bug verdict

Architectural patterns в agentdb_pattern-store:
- `mcp__ruflo__agentdb_pattern-search query="параллельная иерархия"`
  → ADR-033 pattern с confidence=0.95 (validated 3 times)

### Известные мелочи (не блокеры)

- **playwright WSL2 не загружает Google Fonts** через corp proxy 407
  - визуальная проверка шрифтов только в реальном браузере
- **RetryStrategy migration отложен** - AWS SDK v2 API refactor с
  большим blast radius, требует careful migration
- **NodeCard footer chips** (18.h.A1) deferred - duplicate данные с
  header meta-row, low value

