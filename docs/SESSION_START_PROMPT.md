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

**Этап 20.c shamela bibliography parser** (после Сессии 33 -
полная RTL/i18n локализация фронта закрыта)

Сессия 33 закрыла системный фикс RTL/LTR + i18n покрытие:
- **RTL/bidi mechanics** - единый `hasArabicScript` из `@/shared/i18n`,
  все физические Tailwind-классы (`ml`/`mr`/`pl`/`pr`/`left`/`right`/
  `text-left`/`border-l`) заменены на логические (`ms`/`me`/`ps`/`pe`/
  `start`/`end`/`text-start`/`border-s`). Граф React Flow не зеркалится.
  `dir="auto"` для контента из API, `<bdi>` для mixed-content
- **i18n инфраструктура** - dictionary расширен с ~22 до ~280 ключей
  по namespace'ам (nav/reader/common/topic.list/book.list/status/node.
  type/edge.type/edge.label/graph.ctx/admin/citation_picker/add_source
  и др.). Новые primitives: `useFormatDate`, `useNumberFormat` -
  локаль-aware, стабильны через `useCallback`
- **Token refactor** - `STATUS_TOKENS`/`NODE_TYPE_TOKENS`/`EDGE_TYPE_
  TOKENS`/`NODE_TYPE_META`/`EDGE_TYPE_META`: `label/hint` → `labelKey/
  hintKey: DictKey`. `getContextualEdgeLabel` → `getContextualEdgeLabelKey`.
  `NODE_TYPE_LABEL` удалён. Один контракт «токен описывает визуал,
  переводы в словаре»
- **i18n покрытие всех страниц** - Header, TopicListPage, TopicGraphPage,
  GraphCanvas (context menu/toasts/confirm), GraphPanels, NodeCard,
  NodeDetailsPanel + 4 секции (Content/Metadata/Citations/Revisions),
  EdgeDetailsPanel, AddNodeModal, AddEdgeModal, AddSourceModal,
  CitationPicker, BookListPage, BookReaderPage + PageJump + PdfViewer,
  AdminShamelaPage (DateFormat/NumberFormat локаль-aware)
- **FormModal** общий компонент - точка одного перевода «Отмена» для
  всех форм проекта (DRY)
- **Документация** - `frontend/docs/i18n-guide.md` (~280 строк) -
  canonical reference для будущих сессий: 3 понятия которые нельзя
  путать (локаль/контент/направление), algorithms, anti-patterns,
  чек-лист перед PR. Cross-link из CLAUDE.md и coding-standards
- **Bugfix infinite-loop** - `useT`/`useFormatDate` стабилизированы
  через useCallback (без этого `t` в deps useEffect ломал TopicGraphPage
  бесконечными fetch'ами)
- **Code review** через subagent - 11 strengths, 0 Critical, 12
  Important - все закрыты в follow-up commits. Verdict «Ready to merge»

Закоммичено ~30 атомарных commit'ов локально, push в origin/master
сделан в конце сессии. Тесты 143/143, build/typecheck чистые.

Старая Сессия 32 (выжимка для контекста):
- **20.f LibraryCite redesign** (`72ddd0b`) - SourceCard variant D
- **i18n minimal** (`c1a6ff1`, `d86e010`) - первая итерация dictionary
- **FK variant A** (`8f3b2c9`) - миграция 25, surrogate `id UUID` PK для
  node_sources. DELETE path: `/sources/{nodeSourceId}` (breaking)

**Цель 20.c:** извлечь academic metadata из raw `lib_books.description`
(там shamela хранит bibliography текст с мухаккиком, publisher и т.д.)

**Стартовая последовательность Сессии 34:**

1. **Узнать формат shamela bibliography** - в БД:
   ```sql
   SELECT id, title, description FROM lib_books
   WHERE description IS NOT NULL LIMIT 5;
   ```
   Это покажет реальные префиксы (`المؤلف:`, `الكتاب:`, `تحقيق:`,
   `الناشر:`, `الطبعة:`, `سنة النشر:` и т.п.)

2. **Создать `ShamelaBibliographyParser`** в
   `backend/src/main/java/ru/basnukaev/argumentmap/library/shamela/service/`:
   - Regex-based extraction для каждого поля
   - Fallback NULL если префикс не найден
   - Return record `ParsedBibliography(muhaqqiq, publisher, place, edition, yearHijri, yearGregorian)`

3. **Интегрировать в `ShamelaToLibraryMapper.mapBook`** - после resolving
   authority вызвать `bibliographyParser.parse(bibliography)`, для каждого
   non-null поля вызвать `*Repository.findOrCreate(name)` и заполнить FK
   на новой Book

4. **Unit-тесты** с фикстурами реальных shamela bibliography строк
   (5-10 разных книг)

5. **Опционально: bulk-backfill endpoint** в `ShamelaAdminController`:
   `POST /api/v1/admin/shamela/backfill-academic-metadata` - пройтись
   по всем замапленным книгам и обогатить пустые поля без re-import

6. **Smoke**: re-map тестовой книги через `POST /api/v1/admin/shamela/map-book/{id}`,
   curl `/api/v1/nodes/{id}/sources` - увидеть filled muhaqqiq/publisher

### Doc-долги после Сессий 32+33 (важно сделать в Сессии 34)

- **`api-contract.md`** обновить:
  - `NodeSourceResponse` получил поле `id` (UUID) (из Сессии 32)
  - `DELETE /api/v1/nodes/{nodeId}/sources/{nodeSourceId}` - **breaking
    change** path param (раньше был `{sourceId}`) (из Сессии 32)
  - `BookDetailResponse` обогащён nested refs (authority/muhaqqiq/
    publisher/publicationPlace) (из Сессии 32)
  - Строка в "Историю изменений"
- **ADR-029** для FK variant A (decisional - surrogate id PK vs
  composite PK с positional fields). Rejected alternatives
- **ADR-030** для i18n архитектуры - после Сессии 33 решение
  стало гораздо более развёрнутым: manual dictionary с DictKey
  union literal types vs i18next, useCallback-стабилизация хуков,
  локаль-aware токены через labelKey, FormModal как DRY-точка
- **gotcha** `gotchas.md`:
  - React StrictMode duplicate API requests в dev (Сессия 32)
  - Closure-функции из хуков всегда стабилизировать через useCallback
    если они потенциально попадут в deps useEffect/useMemo
    (Сессия 33 - infinite loop в TopicGraphPage из-за нестабильного `t`)
  - Batch-Edit'ы по cyrillic строкам - после делать `grep -nE
    "label:.*'[А-ЯЁ]"` потому что Edit может silent-skip без error
    если whitespace не точно совпадает (Сессия 33)

### Backlog i18n после Сессии 33 (не блокеры)

- **SourceSearchForm + SourceCreateForm + AttachFields placeholders** -
  внутренние формы создания свободного источника (admin workflow).
  Сами Modal-обёртки переведены, но детали полей внутри пока
  захардкожены на русском
- **ESLint pre-commit rule** на cyrillic literals в JSX attribute
  strings - chyba review рекомендации, должно предотвратить регрессии
- **AR locale parameterized tests** - сейчас тесты завязаны на
  default `ru` локаль. Параметризовать setup для обеих локалей
- **EDGE_DEFAULT_LABEL_KEY** - move declaration выше функции для
  читаемости (M-1 из review)
- **AR числа** - проверить что `Intl.NumberFormat('ar')` даёт
  arabic-indic digits ١٢٣٤ или западные (зависит от runtime).
  Если западные - добавить `useGrouping: false` или digit option

### Что осталось из Этапа 20 после 20.c

- **20.d Admin BookEditModal** (~1 сессия) - frontend UI для ручного
  дозаполнения когда parser не справился. Search + autocomplete
- **20.e AddSourceModal расширенная форма** (~0.5 сессии) - manual
  entry sourceType=BOOK с полями

### Известные мелочи (не блокеры)

- **Backend running** - после изменений требует kill+restart
  (`spring-boot:run` не подхватывает свежие classes автоматически)
- **27 call sites `new Book(...)`** и **17 `new Authority(...)`** -
  возможно future refactor на builder pattern

### Инфра на момент Сессии 34 entry

- Postgres :5432, миграции до 25 включительно applied
- MinIO :9000 healthy
- Backend :9090 + JDWP :5005 running
- Frontend :5173 running с HMR + localStorage `app.locale` для persist
- Smoke citation в production-БД (node `4139cb32-...` topic
  `a6617d11-...`): Тафсир Ибн Касира с **filled** academic data
  (мухаккик السلامة, publisher Дар Тайба, place Эр-Рияд, edition 2,
  годы 1420/1999, author fullName + death 774)
- Тестовая Source `132d75cc-...` имеет title `Тафсир Ибн Касира` -
  ru transliteration (выставлено вручную в Сессии 31). Для других
  shamela-imported books source.title пока arabic

### Альтернативные приоритеты

- 20.d Admin BookEditModal (если parser хватает на 70%+ books)
- Этап 19 Q&A приложение (валидация платформы)

**Memory:**
- [[feedback-no-prod-no-backward-compat]] активно
- [[feedback-handoff-ui-checks]] - после frontend изменений давать
  чек-лист «что посмотреть» (URL/actions/expected)
- [[feedback-responsive-ui-future]] - новый код держит в уме
  mobile/tablet через Tailwind responsive utilities + logical classes
