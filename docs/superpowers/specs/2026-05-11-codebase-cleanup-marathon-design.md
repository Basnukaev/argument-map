# Codebase cleanup marathon - design spec

**Дата:** 2026-05-11
**Контекст:** после 24 сессий Claude Code накопилась техдолг по 4 категориям:
границы модулей/файлы разрослись, naming/consistency, дублирование/хаки,
архитектура фронта плоская. Цель - привести кодовую базу к состоянию в котором
будущим Claude Code сессиям легче работать (читаемость, понятность, меньше
токенов на чтение, чёткие границы).

**Scope:** backend Java, frontend TS/TSX, тесты, документация. Исключение -
`frontend/design-reference/` (статические дизайн-референсы).

**Constraints (от пользователя):**
- тесты и билд должны продолжать проходить
- фичи работают как до рефакторинга
- автономный режим - решения принимаются без согласований
- меньше токенов в кодовой базе (выпиливать лишние комментарии, мёртвый код)
- best practices приоритет
- проект ведёт только Claude Code, оптимизируем UX для Claude Code

---

## Архитектура marathon'а: 6 фаз последовательно

```
Phase 0 (Audit)   →   Phase 1 (Backend boundaries)
                  →   Phase 2 (Frontend apps/)
                  →   Phase 3 (Дубликаты/хаки/мёртвый код)
                  →   Phase 4 (Naming/consistency)
                  →   Phase 5 (Docs cleanup + project CLAUDE.md)
                  →   Финализация (полный билд + handoff)
```

Каждая фаза - отдельный коммит (или несколько атомарных коммитов внутри фазы).
Между фазами зелёный билд + тесты обязательны.

### Порядок фаз - почему именно такой

- Audit первым потому что без него работаем по ощущениям и scope разрастается
- Structural changes (Phase 1-2) перед naming (Phase 4) - иначе переименовываем
  дважды (пути в импортах + имена)
- Дубликаты (Phase 3) видны после структурных изменений - в разрозненном коде
  дубли "оправданы соседством"
- Docs cleanup (Phase 5) последним - после того как код устаканился, чтобы
  architecture.md/glossary.md описывали финальное состояние

---

## Phase 0: Audit (research only, без правок кода)

### Метод

4 параллельных Explore-агента, каждый получает фиксированный чек-лист и
формат finding'а. Координатор сводит результаты в один документ.

### Чек-листы для агентов

#### Backend Java чек-лист

```
1. BOUNDARY (границы модулей):
   - файлы > 300 LOC - flag, описать обязанности
   - классы с > 7 публичных методов - flag
   - сервисы которые знают про > 5 других сервисов/репозиториев - flag
   - package с > 15 классов одного уровня без подпакетов - flag
   - cross-package depending: web/controller имеет import из repository/*
     напрямую - flag

2. NAMING:
   - UUID-поля: `bookId` vs `bookUuid` vs `id` - найти все варианты,
     посчитать кол-во
   - DTO suffix: `*Response` vs `*Dto` vs `*Result` - флагить
     несоответствия
   - Method naming: `find*` vs `get*` vs `load*` для read - флагить
   - Boolean naming: `is*`, `has*`, `should*` - какой стиль преобладает
   - Russian comments inside production code - flag

3. DUPLICATION:
   - copy-paste блоки > 5 строк - flag с обеими file:line
   - параллельные иерархии (например parallel mapper'ы) - flag
   - одинаковые catch-блоки / error-формирование - flag

4. HACKS:
   - все TODO/FIXME/XXX/HACK - перечислить
   - try-catch с пустым catch - flag
   - hardcoded UUIDs/URLs/dates - flag
   - reflective access, suppressed warnings - flag
   - временные workarounds с комментарием "временно" / "пока" - flag
   - излишние / тавтологичные комментарии (claude-generated noise) - flag
```

#### Frontend TS/TSX чек-лист

```
1. BOUNDARY:
   - файлы > 250 LOC - flag (исключение - api/types.ts автоген)
   - компоненты > 200 LOC - кандидат на разбиение
   - hooks > 50 LOC - кандидат на разбиение
   - apps/ readiness mapping: для КАЖДОГО файла указать предполагаемое
     место в apps/{argument-map,library,admin} или shared/
   - stores: какие cross-app vs single-app
   - circular imports - flag

2. NAMING:
   - UUID consistency, особенно в DTO с backend (bookId vs id)
   - component naming: PageX vs XPage vs ScreenX - флагить
   - hook naming: `use*` consistency
   - store naming: `useXStore` vs другие варианты

3. DUPLICATION:
   - copy-paste UI-блоков
   - параллельная логика fetch'а данных в разных компонентах
   - одинаковые axios-обёртки

4. HACKS:
   - all TODO/FIXME
   - `@ts-ignore`, `as any`, `as unknown as X`
   - eslint-disable
   - временные fallback'и
   - inline styles вместо tailwind
   - излишние комментарии (claude-generated noise)
```

#### Tests audit чек-лист

```
1. STRUCTURE:
   - IT (integration) vs unit ratio
   - тесты длиннее 80 LOC - flag
   - duplicate setup'ы между тестами одного класса (можно extract в
     @BeforeEach)

2. TEST SMELLS:
   - over-mocking: > 5 mock в одном тесте - flag
   - assertion на implementation detail (private state) - flag
   - time-dependent тесты (Thread.sleep, Instant.now без clock) - flag
   - magic UUIDs/strings - flag
   - тесты которые тестируют ту же ветку дважды
   - frontend: тесты которые ждут конкретные классы Tailwind

3. COVERAGE THIN SPOTS:
   - production файлы без соответствующего тестового
   - сервисы с тестами только на happy path

4. NAMING:
   - test method naming convention
   - given_when_then vs описательные имена - какой преобладает
```

#### Docs/ADR vs reality чек-лист

```
1. ADR-014..021 - для каждого:
   - реализовано как описано? flag отклонения
   - есть ли ссылки на отменённые/устаревшие подходы

2. architecture.md / architecture-platform.md:
   - секции "Backend модули" / "Frontend модули" - совпадает ли список
     с реальностью
   - диаграммы (если есть) - актуальны ли

3. glossary.md:
   - термины - все ли встречаются в коде с теми же именами
   - есть ли в коде "сущности" не описанные в glossary

4. progress.md "Закрыто":
   - все ли пункты помечённые как "x" реально закрыты в коде
   - флагить pending TODO которые помечены как закрытые
   - НЕ читать весь progress.md целиком (4835 LOC) - только последние
     3 сессии + раздел "Будущие фичи"

5. api-contract.md vs реальный OpenAPI:
   - указанные endpoints реально существуют
   - response schemas совпадают (выборочно, последние 5 endpoints)

6. gotchas.md:
   - все ли gotcha всё ещё актуальны (бывает что фикс снял проблему)
```

### Формат finding'а

```markdown
### B-12 [high] [boundary, +naming] backend - ShamelaToLibraryMapper

**Файл:** `library/shamela/etl/ShamelaToLibraryMapper.java:1-413`

**Проблема:** 413 LOC, 11 публичных методов, делает три разных вещи -
маппинг book metadata, маппинг chapters, маппинг pages.

**Почему важно:** при добавлении новой shamela-фичи приходится трогать
один большой класс с риском задеть смежные ветки. Тесты на этот класс
длиной 80+ LOC.

**Предлагаемое действие:** разнести по 3 классам - `BookMapper`,
`ChapterMapper`, `PageMapper`.

**Effort:** L

**Phase:** Phase 1 (backend boundaries)

**Related:** B-7 (тесты на этот класс)
```

### Output Phase 0

Документ `docs/superpowers/audits/2026-05-11-codebase-audit.md`:

```
# Codebase audit 2026-05-11

## Executive summary
[топ-10 проблем + предложение приоритета]

## Backend findings
B-01 ... B-NN

## Frontend findings
F-01 ... F-NN

## Tests findings
T-01 ... T-NN

## Docs findings
D-01 ... D-NN

## Cross-cutting findings (multi-стек)
[если есть]

## Phase backlog
### Phase 1 (backend boundaries): [список ID]
### Phase 2 (frontend apps/): [список ID]
### Phase 3 (дубли/хаки): [список ID]
### Phase 4 (naming): [список ID]
### Phase 5 (docs): [список ID]
```

### Severity критерии

- `high`: блокирует/тормозит работу, risk высокий - делать первым в фазе
- `medium`: качество жизни ухудшается, работать можно
- `low`: косметика. Делаем если время остаётся

### Phase 0 acceptance

- [ ] 4 агента отработали, findings собраны
- [ ] Дубликаты cross-cutting между потоками удалены
- [ ] Top-20 приоритизирован в executive summary
- [ ] Документ записан и закоммичен
- [ ] Зелёный билд НЕ проверяется (нет правок кода)

---

## Phase 1: Backend boundaries cleanup

### Что делаем

Берём findings B-XX категории boundary из audit'а, идём по приоритету.
Известные крупные файлы (на момент дизайна):

- `ShamelaToLibraryMapper.java` (413 LOC) - разнести на BookMapper /
  ChapterMapper / PageMapper
- `ShamelaBookDao.java` (289 LOC) - проверить, возможно DAO норм
- `ShamelaImportService.java` (252 LOC) - проверить, разнести если
  смешивает orchestration с domain logic
- `PdfLinksSourceProvider.java` (217 LOC) - проверить
- `GlobalExceptionHandler.java` (181 LOC) - проверить, возможно
  смешивает разные exception types

Точный список - из audit'а.

### Принципы рефакторинга

- НЕ менять сигнатуры публичных REST API
- НЕ менять схему БД
- Один класс - одна обязанность (single responsibility)
- Внутренние классы экстрактим в файлы только если переиспользуются
  или существенно крупные
- DTO/mapper'ы НЕ объединять между подсистемами (library/ DTO и
  argument-map/ DTO остаются отдельными)

### Acceptance

- [ ] `./mvnw verify` зелёный
- [ ] Все integration tests проходят
- [ ] Коммит с conventional commit'ом `refactor(backend): ...`

---

## Phase 2: Frontend apps/ reorganization

### Цель

Внедрить ADR-018 platform pivot структуру `src/apps/{argument-map,library,admin}` +
`src/shared/` для общего. Подготовить canvas для Этапа 19 (Q&A).

### Целевая структура

```
frontend/src/
  apps/
    argument-map/
      pages/
        TopicListPage.tsx
        TopicGraphPage.tsx (разбит на subcomponents - см. ниже)
        CreateTopicPage.tsx
      components/
        graph/ (NodeDetailsPanel, EdgeDetailsPanel, Add*Modal, и т.д.)
      utils/
        edgeRules.ts
        graphLayout.ts
        attachmentTokens.ts
    library/
      pages/
        BookListPage.tsx
        BookReaderPage.tsx (разбит на subcomponents - см. ниже)
      components/
        PdfViewer.tsx (и другие из components/library/)
    admin/
      pages/
        AdminShamelaPage.tsx
  shared/
    api/
      client.ts
      types.ts (автоген - НЕ трогаем содержимое)
    components/
      layout/ (всё что было в components/layout/)
      ui/ (всё что было в components/ui/)
    stores/
      toastStore.ts
    utils/
      designTokens.ts
  App.tsx
  main.tsx
  index.css
```

### Разбиение монстров перед миграцией

#### TopicGraphPage.tsx (1161 LOC)

Цель - разнести на компоненты по UI-зонам:

```
apps/argument-map/pages/TopicGraphPage.tsx (orchestrator < 200 LOC)
apps/argument-map/components/topic-graph/
  TopicGraphToolbar.tsx
  TopicGraphCanvas.tsx
  TopicGraphSidebar.tsx (или Right/Left отдельно)
  hooks/useTopicGraphState.ts (если есть сложный state)
```

Точные зоны - по факту читая TopicGraphPage.tsx.

#### BookReaderPage.tsx (714 LOC)

Аналогично:

```
apps/library/pages/BookReaderPage.tsx (orchestrator < 200 LOC)
apps/library/components/book-reader/
  BookReaderHeader.tsx
  ChaptersTree.tsx
  PageNavigation.tsx
  PageContent.tsx
```

#### NodeDetailsPanel.tsx (613 LOC), AddSourceModal.tsx (550 LOC)

Кандидаты на разбиение, решение по факту.

### Принципы

- Импорты обновить через relative paths внутри app + absolute через `@/shared`
- vite config - alias `@/shared` → `src/shared`
- `api/types.ts` - содержимое НЕ трогаем (автоген), переносим как есть

### Acceptance

- [ ] `npm run build` без ошибок
- [ ] `npm test` (Vitest) зелёный
- [ ] `npm run typecheck` без ошибок
- [ ] vite dev server поднимается, страницы открываются
- [ ] Smoke test через playwright (если возможно): открыть `/topics`,
      `/books`, `/admin/shamela` - не падают
- [ ] Коммит с `refactor(frontend): внедрение apps/ структуры`

---

## Phase 3: Дубликаты/хаки/мёртвый код cleanup

### Что делаем

Берём findings из audit'а категорий `duplication` + `hacks` + `dead code`.
Применяем:

- Extract дубликатов в shared utils (для frontend) или общие методы (backend)
- Чиним хаки на правильные решения (если возможно в рамках сессии)
- Удаляем мёртвый код:
  - неиспользуемые exports
  - dead branches (с условиями всегда true/false)
  - unused imports
  - закомментированные блоки кода
- Удаляем излишние комментарии:
  - `// получаем книгу` перед `getBook()` - тавтология
  - `// TODO: добавить логирование` без owner и старше 3 сессий - удалить
    или превратить в реальный TODO с описанием
  - claude-generated блочные комментарии описывающие то что код и так
    показывает - удалить
  - JavaDoc / TSDoc оставлять только на публичных API где он несёт ценность

### Принципы оптимизации под Claude Code

- Каждый лишний токен в коде = больший токен-расход для будущих сессий
- Имя метода/класса лучше передаёт интент чем 3-строчный комментарий
- Комментарий **why** оставляем (неочевидные решения, workarounds, ссылки
  на bug/ADR)
- Комментарий **what** удаляем (повторение кода словами)

### Acceptance

- [ ] `./mvnw verify` + `npm run build` + `npm test` зелёные
- [ ] Линтеры (eslint, checkstyle если есть) зелёные
- [ ] Bundle size frontend проверен - не вырос (должен уменьшиться или
      остаться тем же)
- [ ] Коммит `refactor: дедуп + удаление мёртвого кода и шума`

---

## Phase 4: Naming/consistency pass

### Что делаем

Из audit'а:

- UUID consistency: единая конвенция для primary keys в DTO
  - Решение: на backend Java `UUID id` в Entity, в DTO - `UUID id`
    (не `bookId`) если контекст DTO ясно об этом говорит. В cross-entity
    DTO - `entityNameId` (например `bookId` в `ChapterResponse`)
  - На frontend TypeScript - то же поведение (`id` в собственном DTO,
    `bookId` в чужих DTO)
- DTO suffix: всё либо `*Response` / `*Request` / `*Command`. Никаких
  `*Dto`, `*Result`
- Method naming на backend:
  - `find*` - может вернуть null / Optional
  - `get*` - бросает exception если не нашёл
  - `load*` - не используем (унифицировать)
- Boolean naming:
  - `is*` для состояний (isActive)
  - `has*` для владения (hasChapter)
  - НЕ использовать `should*`, `can*` в production data (только в logic)
- Component naming: `*Page` суффикс для top-level page-компонентов,
  `*Panel` для боковых панелей, `*Modal` для модалок
- Hook naming: `use*` обязательно
- Store naming: `use*Store` (например `useToastStore`)

### Acceptance

- [ ] `./mvnw verify` + `npm run build` + `npm test` зелёные
- [ ] OpenAPI schema регенерирована (springdoc), types.ts регенерирован
- [ ] Коммит `refactor: унификация naming conventions`

---

## Phase 5: Docs cleanup + project CLAUDE.md

### Что делаем

#### 1. Архивация `progress.md`

`progress.md` 4835 LOC = 24 сессии. Каждая новая сессия читает его - это
огромный токен-расход.

Решение:
- Создать `docs/archive/progress-sessions-1-20.md` (Сессии 1-20)
- В `progress.md` оставить Сессии 21+ (актуальные) + ссылка наверху на
  архив
- В архивный файл - короткая шапка "архивный лог сессий 1-20, читать
  только при необходимости"

#### 2. Создать project-level `CLAUDE.md`

Корень проекта, ~150 LOC max:

```markdown
# CLAUDE.md для argument-map

Этот файл - быстрый старт для новых сессий Claude Code. Полный контекст -
в docs/SESSION_START_PROMPT.md.

## Структура проекта
[backend, frontend apps/, docs/, scripts/, design-reference (НЕ трогать)]

## Стэк
[Java 21, Spring Boot 3.5, JDBC Template, Postgres 16, React 19, Vite 6,
Tailwind v4]

## Команды
[./mvnw verify, npm run build, npm test, scripts/seed-mawlid.sh]

## Главные доки (в порядке важности)
1. docs/SESSION_START_PROMPT.md - стартовый промпт
2. docs/roadmap.md - текущий этап
3. docs/decisions.md - ADR'ы
4. docs/gotchas.md - ловушки
5. docs/architecture.md / architecture-platform.md
6. docs/api-contract.md
7. docs/glossary.md
8. docs/progress.md (актуальное) + docs/archive/ (исторические сессии)

## Конвенции
[короткие правила: naming, commit style, не трогать design-reference, etc.]
```

#### 3. Обновить `architecture.md`

Под новую apps/ структуру frontend + boundaries backend.

#### 4. Сверить `glossary.md` с кодом

Удалить термины которых нет в коде, добавить новые термины.

#### 5. Сверить ADR-014..021 с реальностью

По findings D-XX из audit'а. Stale ADR пометить статусом "SUPERSEDED BY ..."
или "OBSOLETE" с пояснением.

#### 6. Обновить `gotchas.md`

Удалить gotcha которые уже решены, оставить актуальные.

#### 7. Создать `docs/superpowers/audits/README.md`

Короткий index какие audit-документы есть и для чего.

### Acceptance

- [ ] `progress.md` < 1500 LOC (только актуальные сессии)
- [ ] `archive/progress-sessions-1-20.md` создан с шапкой
- [ ] `CLAUDE.md` создан в корне проекта
- [ ] `architecture.md` отражает реальную apps/ структуру
- [ ] `glossary.md` синхронизирован с кодом
- [ ] Stale ADR помечены
- [ ] Коммит `docs: cleanup + project CLAUDE.md + архивация progress`

---

## Финализация

### Что делаем

1. Полный билд бэка: `./mvnw verify` - **просим Абдулу запустить** (по
   feedback memory он держит backend в отдельном терминале, мы не
   запускаем spring-boot:run; verify окей)
2. Полный билд фронта: `npm run build` + `npm test` + `npm run typecheck`
3. Smoke test через playwright (если возможно)
4. Bundle size sanity check - размер vendor.js + main.js не вырос
5. Обновить `SESSION_START_PROMPT.md`:
   - указать новую apps/ структуру
   - указать на `CLAUDE.md` в корне
   - обновить топ команд
6. Записать в `progress.md` итог marathon'а (новая запись Сессия 25)
7. Финальный коммит `chore: cleanup marathon - финализация`

### Acceptance

- [ ] Все билды зелёные
- [ ] Все тесты проходят
- [ ] SESSION_START_PROMPT.md обновлён
- [ ] progress.md запись о Сессии 25 marathon
- [ ] Финальный коммит на месте

---

## Out of scope

- Изменения в БД схеме - не трогаем (требует Liquibase миграцию + рестарт)
- Изменения в REST API публичных контрактах - не меняем (фронт сломается)
- `frontend/design-reference/` - не трогаем
- `api/types.ts` содержимое - автоген, не редактируем вручную
- Реализация Этапа 25.b/d (MinIO, page sync) - это features, не cleanup
- Реализация Этапа 18.f (CitationPicker) - features
- Этап 19 (Q&A) - features
- Bulk import shamela книг - feature
- Удаление существующих миграций Liquibase - запрещено (применённые
  миграции иммутабельны)

## Риски и mitigation

| Риск | Mitigation |
|------|------------|
| Поломка фичей при reorganization фронта | Smoke test после Phase 2, откат коммита если |
| Поломка тестов при разделении классов | После каждого extract'а - `./mvnw test` |
| Конфликт с pending feature work | Сейчас feature branch'а нет, master чист после 0b4bf86 |
| Context overflow в рамках одной сессии | План декомпозирован, можно прерваться между фазами, handoff в SESSION_START_PROMPT |
| Bundle size вырос из-за рефакторинга | Sanity check в финализации, откат проблемных коммитов |
| Поломка ADR-018 spirit | Apps/ структура **усиливает** ADR-018 - совместимо |

## Non-goals

- НЕ переделываем архитектуру верхнего уровня (ADR-018 platform pivot
  остаётся)
- НЕ внедряем новые библиотеки/фреймворки
- НЕ меняем выбранный стэк (Spring Boot, JDBC, React, Vite, Tailwind)
- НЕ переписываем работающие фичи "по красоте"
- НЕ пишем новые тесты сверх того что нужно для подтверждения что
  рефакторинг не сломал поведение

## Принципы исполнения

1. **Зелёные тесты после каждой фазы** - не идём дальше с красными
2. **Один коммит на фазу** (минимум) - чтобы можно было откатить
3. **Conventional Commits** - `refactor(scope): ...`, `docs: ...`,
   `chore: ...`
4. **Параллельные субагенты** - только для Phase 0 audit'а (research).
   Для остальных фаз - sequential работа в основной сессии (по
   feedback memory subagent-driven implementation в этом проекте не
   оправдан)
5. **Скепсис к собственному импульсу рефакторить** - если изменение
   не закрывает finding из audit'а, не делаем
6. **Прагматизм над perfectionism** - не дотираем последние 5% полировки
   ценой остановки marathon'а
