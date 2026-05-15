# Стартовый промпт для новой сессии Claude Code

Этот файл - **стабильный** контекст начала любой сессии. Обновляется
только раздел «Текущий приоритет» (в конце документа). Остальное -
постоянное

Правила эволюции этого файла - в `docs/doc-hygiene.md` Принцип 6.
Если файл вырос за 400 строк - вылавливай дубли с CLAUDE.md /
progress.md / roadmap.md и выкидывай

---

## Режим работы - автономный заместитель

Абдула передал режим **полной автономии в рамках проекта**

### Что разрешено без спроса

- **Все тактические решения** - архитектура в рамках уже зафиксированного
  стэка, декомпозиция, выбор библиотек, порядок этапов, разделение
  коммитов
- **Subagents через `Agent` tool** - для исследования (Explore) и code
  review. Параллельный запуск на implementation задачах не оправдан
  (эксперимент Сессии 21 не дал выигрыша)
- **Закрытие сессии** - запись в `progress.md` и обновление раздела
  «Текущий приоритет» в этом файле. Новая сессия читает и продолжает
  без апрува
- **Коммиты** в любую часть репы - Conventional Commits, разумная
  атомарность

### Red lines - НИКОГДА без явного спроса

- Не удалять системные папки (`~/.claude`, `~/.ssh`, etc) или другие
  проекты в `~/projects/`
- Не делать `git push --force` на main/master
- Не амендить опубликованные коммиты
- Не пропускать pre-commit hooks через `--no-verify`
- Не менять стратегию проекта (`vision.md` / ADR-018) - уровень
  Абдулы. Можно предлагать, не реализовывать без апрува
- Не делать destructive ops (`git reset --hard`, `rm -rf` каталогов)
  без понимания что отменяется

### Когда эскалировать

Не зависать молча на блокерах. Звать сразу если:

- что-то не скачивается несколько раз (npm/maven/docker fail)
- версия не находится и retry не помогает
- что-то не запускается после ~3 разумных попыток диагностики
- противоречие в спецификации/доках которое нельзя решить выбором
- внешний blocker - API-ключ, доступ к shamela, OCR-модель

Формат: «пробовал X и Y, не работает потому что Z, предлагаю A или
B, твой выбор», не «как мне быть?» в вакууме

Полная версия - в memory `feedback_full_autonomy_mode.md`

---

## START-OF-SESSION PROTOCOL

Перед первым ответом в новой сессии **выполни**:

### 1. Прочитай в таком порядке

1. **`CLAUDE.md`** (корень) - стэк, команды, layout, навигация по
   документации - уже в твоём контексте при старте
2. **`docs/progress.md`** - последние 2-3 записи + «Следующий шаг»
3. **`docs/roadmap.md`** - текущий приоритетный этап. Закрытые
   этапы свёрнуты в одну строку, активные имеют чек-лист
4. **«Текущий приоритет»** ниже в этом файле - что Абдула или
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

В `~/.claude/projects/-mnt-c-my-folders-projects-argument-map/memory/`
есть auto-memory: автономный режим, decision authority, WSL-only,
не-частые-билды, React key-trick, RTL/наshк, design-reference check,
playwright для UI verification, no bulk shamela parsing, no backward
compat. Прочитай `MEMORY.md` index при старте

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

**Этап 20.d Admin BookEditModal** или **20.c follow-up bulk-backfill
endpoint** (после Сессии 35 - всё что было в working tree закоммичено
8 атомарными коммитами, parser 20.c сделан, doc-долги Сессий 32-34
закрыты)

### Что было в Сессии 35

Чисто отработавшая сессия в автономном режиме:

- **v2 design migration** из Сессии 34 разложена в 6 атомарных коммитов:
  tokens infrastructure → UI primitives → AppHeader+menus → pages
  → graph → source card+reader. Все 6 коммитов прошли verify
  (TS clean / ESLint 0 errors / 143/143 frontend tests)
- **Doc-долги Сессий 32-34** закрыты одним `docs:` коммитом:
  api-contract обновлён (NodeSourceResponse.id + DELETE
  `/sources/{nodeSourceId}` + changelog), ADR-029 FK variant A,
  ADR-030 i18n архитектура, ADR-031 v2 design system tokens,
  5 gotchas (StrictMode duplicate / closure useCallback / batch-Edit
  cyrillic / Tailwind v4 `@theme inline` / mass-replace sed grep audit)
- **Этап 20.c shamela bibliography parser:**
  - `ShamelaBibliographyParser` - regex по المحقق/الناشر/الطبعة/
    عام النشر + arabic ordinal dictionary + arabic-indic digit
    conversion
  - `ParsedBibliography` record (6 nullable fields)
  - Интегрирован в `ShamelaToLibraryMapper.mapBook` через
    `findOrCreate` в Muhaqqiq/Publisher/PublicationPlace repos
  - 11 unit-тестов с реальными фикстурами из dev-БД
  - Verify: 425/425 backend tests pass

**Что НЕ сделано (отложено):**

- **Bulk-backfill endpoint** для existing books (3 dev-книги в БД
  остались с null FK) - требует BookRepository partial update + admin
  endpoint. ~30 минут работы, не ложится естественно в parser commit
- **@tabler/icons-react** через corp proxy всё ещё ETIMEDOUT, workaround
  `svg.lucide:not([stroke-width])` остаётся

### Стартовая последовательность Сессии 36

**1. Выбор приоритета (короткое решение, до начала кода):**

Опция A - **20.c follow-up bulk-backfill** (~30 мин):
- `BookService.refreshBibliographyMetadata(bookId)` или batch-вариант
- `POST /api/v1/admin/shamela/backfill-bibliography` endpoint
- Frontend: кнопка в AdminShamelaPage «Перечитать metadata»
- Smoke: запустить на 3 dev-книгах, проверить заполнение FK

Опция B - **20.d Admin BookEditModal** (~1 сессия):
- UI для ручной правки 6 academic полей после импорта
- Search + autocomplete по справочникам через
  `GET /api/v1/library/muhaqqiqs?q=...` (нужно ли создавать?) или inline
- PATCH endpoint `/api/v1/library/books/{id}` с расширенным request

Опция C - **Этап 19 Q&A приложение** - валидация платформенной
архитектуры через второе приложение использующее common stack.
ADR-018 platform pivot обоснован но не доказан реальным вторым use case

Рекомендация - **A + B в одну сессию**: A быстрый, B логически следует
после A (Admin может править ровно ту metadata которую backfill частично
заполнил). Итого ~1.5 сессии. Или сделать A сразу + начать B, закрытие
B перенести на 37.

**2. После выбора:**

Поднять backend (с JDWP флагом из CLAUDE.md) - он сейчас на старом
classpath после parser changes Сессии 35, нужен restart чтобы
findOrCreate работал.

Frontend на :5173 - после restart Vite должен подобрать v2 changes.
Если HMR cache stale, `rm -rf node_modules/.vite` (Сессия 34 показала
этот workflow).

### Backlog после 20.c-e

- **Этап 19 Q&A приложение** (платформенная валидация)
- **@tabler/icons-react retry** + shim + sed-replace когда сеть позволит
- **Backlog i18n** (Сессия 33): SourceSearchForm/SourceCreateForm
  placeholders на словарь, ESLint pre-commit rule на cyrillic literals
  в JSX, AR parameterized tests, EDGE_DEFAULT_LABEL_KEY declaration
  выше функции, AR числа через `Intl.NumberFormat('ar')`

### Известные мелочи (не блокеры)

- **27 call sites `new Book(...)`** и **17 `new Authority(...)`** -
  возможно future refactor на builder pattern. Сейчас Book получает
  16 параметров, читать тяжело
- **3 MSW unhandled rejections** в NodeDetailsPanel.test.tsx при
  full vitest run - не ломают тесты (143/143 passed), известный
  flaky issue infrastructure, не код
- **Backend restart обязателен** после classpath changes -
  `spring-boot:run` не подхватывает свежие classes автоматически
- **Frontend rm -rf node_modules/.vite** иногда нужен после
  mass-replace для очистки HMR cache

### Инфра на момент Сессии 36 entry

- Postgres :5432, миграции до 25 включительно applied
- MinIO :9000 healthy
- Backend :9090 + JDWP :5005 - **рестарт обязателен** после parser
  changes Сессии 35
- Frontend :5173 - рестарт желателен после v2 коммитов
- localStorage: `app.locale` + `app.theme` для persist
- Smoke citation в production-БД (node `4139cb32-...` topic
  `a6617d11-...`): Тафсир Ибн Касира с filled academic data
- В dev-БД 3 mapped book'а с descriptions - кандидаты для bulk-backfill

### Что было в Сессии 34

Большая UI-инициатива от Абдулы по миграции на v2 design system из
`frontend/design-reference/v2/project/handoff/`:

- **Tokens + темизация** - `src/styles/tokens.css` (новый, семантические
  CSS-variables ink/accent/ok/warn/err + type-* + edge-* + surface
  aliases, light + `[data-theme="dark"]`), `@theme inline` block в
  `index.css` (Tailwind v4 - **inline обязателен**, без него тема не
  переключается), `themeStore.ts` + `ThemeEffect.tsx`, FOUC inline
  script в `index.html` до React mount
- **Шрифты** - Manrope (UI) + Source Serif 4 + Amiri (preferred Arabic)
  + Noto Naskh fallback. Подключены через Google Fonts preconnect
- **Primitives** - Button (backwards-compat сохранён), Card + namespace
  Cover/Body/Eyebrow/Title/Meta, новые Chip + Field, обновлены Modal /
  IconButton / Badge / Kbd / Toaster / Select / ContextMenu / FormModal
- **Layout** - Header переписан (бренд ﷽ + nav + поиск + LocaleSwitch +
  **новый ThemeSwitch Sun/Moon** + **BellMenu** + **AvatarMenu**),
  **CommandPalette** (Cmd+K глобально), 4 новых layout-компонента
- **6 страниц + 18 графовых файлов** мигрированы:
  BookListPage (+Импорт Shamela + сортировка + 5-col grid), TopicListPage,
  CreateTopicPage (two-column form + Field primitive), TopicGraphPage
  (+`<Header />` сверху + secondary crumb), BookReaderPage (bg-bg),
  AdminShamelaPage (status pill + **Activity Log placeholder**)
- **Graph fixes** - NodeCard rounded-md, NodeDetailsPanel убран gradient,
  EdgeDetailsPanel gradient → solid `bg-edge-*-bg`, CompactMiniMap на
  `var(--c-*)` для темизации + shift end-[416px] при detail panel,
  удалена status legend (anti-pattern)
- **Source card** - QuoteBlock на `bg-paper` (тёплый кремовый, не
  голубой), "Перейти к источнику" - **outline** (не filled primary),
  metadata раскрыта по default
- **NodeCitations** - кнопки в vertical stack (текст не помещался)
- **i18n** - ~25 новых ключей RU/AR (palette/notifications/avatar/
  admin.status_*/activity_log/sync_done/import_done/book.list.sort_*)
- **Tabler icons workaround** - `@tabler/icons-react` не установился
  через корпоративный proxy (ETIMEDOUT). Глобальный CSS
  `svg.lucide:not([stroke-width]) { stroke-width: 1.5 }` визуально
  приближает к Tabler. Полная замена ждёт когда сеть позволит
- **Code review через Agent** - 3 Critical / 10 Important / 7 Minor
  все закрыты в этой же сессии (ESLint, tests, hex literals,
  gradient anti-pattern, sed-leftovers, spacing-scale, text-[Xpx],
  Modal i18n, toast i18n, exhaustive-deps, rounded-xl, FOUC, Amiri)

**Verify:** TypeScript clean / ESLint 0 errors / 143/143 tests / build
26s. Изменения в working tree, **НЕ закоммичены** - около 60 файлов
plus 4 new (tokens.css, themeStore.ts, ThemeEffect.tsx, CommandPalette/
BellMenu/AvatarMenu/Chip/Field/ThemeSwitch).

**Memory:**
- [[feedback-no-prod-no-backward-compat]] активно
- [[feedback-handoff-ui-checks]] - после frontend изменений давать
  чек-лист «что посмотреть» (URL/actions/expected)
- [[feedback-responsive-ui-future]] - новый код держит в уме
  mobile/tablet через Tailwind responsive utilities + logical classes
- [[feedback-grep-after-batch-edits]] - после sed/Edit mass-replace
  делать verify-grep на остатки палитры / scale violations
