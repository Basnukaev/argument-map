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

### ⭐ АКТУАЛЬНО — entry Сессии 56 (старт после марафона Сессии 55)

**Сессия 55 — крупный автономный overhaul (7 фаз + code-review, ~14 коммитов
`9a766cc..HEAD`).** Детали — `docs/progress.md` (запись Сессии 55) + спека
`docs/superpowers/specs/2026-06-02-session-55-overhaul.md`. По 10 запросам Абдулы:
- **OCR выпилен полностью** (ADR-057, migration 68) — Tesseract плохо парсил арабский.
  AiEditService + image-scan upload сохранены как субстрат для будущего AI-распознавания.
- **Swappable LLM** (ADR-058): пакет `ai/` — `LlmClient` + Anthropic/OpenAI/DeepSeek через
  `@ConditionalOnProperty(ai.provider)` + `BookMetadataExtractionService`.
- **content_kind** (migration 69) — ось доступности TEXT_ONLY/TEXT_AND_FILE/FILE_ONLY
  (ортогональна `book_type`=жанр). Ридер по типу.
- **archive.org overhaul**: FILE_ONLY, drop `_text` OCR-варианты, HTML-стрип, AI-метаданные,
  лок формы после импорта. Закрыло баги обложки-как-тома/абракадабры/сырого-HTML/спиннера.
- **Reader**: bbox-подсветка PDF-цитат, 0-page guard.
- **Hadith**: `availableHadith` честный счётчик (дамп=bukhari-only!), скролл превью-панели,
  alminasa переформулирована.
- **AI-иснад** (ADR-059): `IsnadExtractionService` (LLM из матна) + live preview-граф в
  AdminSunnahPage (кнопка «Извлечь иснад (ИИ)»). Эфемерный (без персистенции в hd_*).
- **Code-review** (multi-agent): 0 Critical, 1 Important (`@Retry` bypass на AI-edit — фикс +
  `LlmClientRetryIT`) + 9 Minor закрыты.

**Верификация (финал):** backend `./mvnw verify` → **BUILD SUCCESS**; frontend build ✓ /
tsc ✓ / eslint **0 проблем** / **vitest 708/0/0**.

**СЛЕДУЮЩИЙ ШАГ (тёплый путь):**
1. **🖐️ РУЧНАЯ ПРОВЕРКА UI** всего overhaul'а Абдулой (playwright env-blocked, нет Chromium) —
   archive.org импорт (FILE_ONLY ридер), content_kind кнопки, hadith превью/иснад, bbox.
2. **AI-ключ для живой проверки** — задать `ai.provider` (anthropic/openai/deepseek) + ключ
   в env, проверить AI-метаданные книг + извлечение иснада вживую.
3. **Rijal-обогащение нарраторов** — persistence сделана (Фаза 9: hd_sanads/narrators/
   sanad_narrators на импорте, дедуп по норм. имени). Осталось: био/надёжность нарраторов
   из authoritative rijal-источника (alminasa future) + улучшить дедуп (false-merge гомонимов).
4. **bbox-citation CREATION для FILE_ONLY** (roadmap 25.f) — CitationPicker PDF-режим
   (выбор страницы + рисование bbox через react-image-crop). Display уже готов.
5. **Полный дамп sunnah.com** (контент-ops) — сейчас только bukhari (100 строк).

**Прочее отложенное:** migration 69 jsonb-guard hardening (backlog); shamela chapter-cycle,
bibliography dash-split, getDetail perf (Tier-3 backlog). См. `docs/backlog.md`.

**Инфра:** Docker (postgres+minio) up + **`sunnah-mysql` :3307** (root/root, БД `sunnah`;
дамп = только bukhari). Backend :9090 + JDWP :5005. **ВАЖНО: sunnah-конфиг — через
`-Dspring-boot.run.arguments`, НЕ env.** Команда рестарта:
`./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" -Dspring-boot.run.arguments="--sunnah.dump.enabled=true --sunnah.dump.url=jdbc:mysql://localhost:3307/sunnah?allowPublicKeyRetrieval=true&useSSL=false --sunnah.dump.username=root --sunnah.dump.password=root"`
**Для AI-фич:** добавить `--ai.provider=deepseek --ai.deepseek.api-key=...` (или anthropic/openai).
migrations через **69** (68 drop ocr-columns, 69 lib_books.content_kind). **Psql роль `argmap`**
(не postgres): `docker exec argumentmap-postgres psql -U argmap -d argumentmap`. frontend :5173.
Admin для curl/тестов: `00000000-0000-0000-0000-000000000001`. HAR archive.org в gitignore.
**Дев-данные:** fmhji переимпортирован чисто (FILE_ONLY, 4 файла); bukhari hd_* 1 хадис.

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

