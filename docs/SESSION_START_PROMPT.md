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

**Этап 20.f - frontend `<LibraryCite>` блочный рендер** (продолжение
Сессии 31)

После Сессии 31 backend готов для structured academic citation:
- 425/425 IT pass
- миграция 24 (3 справочника + расширение Authority + Book)
- `CitationDetail` + `CitationResponse` + 8 nested ref DTO
- `NodeSourceRepository.findByNodeIdWithLocation` отдаёт structured

Что осталось:

1. `cd frontend && npm run generate-api` - регенерировать
   `frontend/src/shared/api/types.ts`. Сломается компиляция в
   `CitationsList.tsx` / `NodeCitationsSection.tsx` где обращаются к
   `link.location` / `link.bookId`
2. Переписать `apps/argument-map/components/graph/CitationsList.tsx` -
   `LibraryCite` рендерит structured блоками:
   - Author block (RTL/naskh): `{authorFullName} (т.{deathYearHijri} هـ)`
   - Title block (RTL/naskh): `{bookTitle}`
   - Muhaqqiq block: `тахкик: {muhaqqiqFullName ?? muhaqqiqName}`
   - Publisher block: `изд. {publisherName} · {publicationPlaceName} · {editionNumber}-е изд.`
   - Years block: `{publishedYearHijri} هـ / {publishedYearGregorian} м.`
   - Location block (моноширинный): `Т.{part} · стр.{printedPage}`
   - Каждый блок с правильным `dir` атрибутом и шрифтом
   - Условный рендер: если nested ref = null, блок скрывается
3. `NodeCitationsSection.tsx` - адаптировать типы, header counts
   остаются
4. Playwright smoke - открыть `/topics/{id}` с тестовой citation
   (node `4139cb32-28ba-4d98-9954-225e8e3c863d` → Тафсир Ибн Касира),
   убедиться в блочном рендере
5. Опционально SQL insert для academic data smoke-citation (мухаккик,
   издатель, edition) - чтобы увидеть полный блочный рендер

Параллельно или вслед: подэтап **20.c shamela bibliography parser**
(см. `roadmap.md`)

### Инфра на момент Сессии 32 entry

- Postgres :5432, миграции до 24 включительно applied
- MinIO :9000 healthy
- Backend :9090 + JDWP :5005 running
- Frontend :5173 running с HMR
- Smoke citation в production-БД: `4139cb32...` → Тафсир Ибн Касира

Подробности backend изменений - в `progress.md` Сессия 31
