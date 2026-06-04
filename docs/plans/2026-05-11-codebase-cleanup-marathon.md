# Codebase Cleanup Marathon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Привести кодовую базу argument-map (backend Java + frontend TS/TSX + docs) к состоянию минимальной избыточности и максимальной читаемости для будущих Claude Code сессий: разрезать монстры (`TopicGraphPage` 1161 LOC, `BookReaderPage` 714 LOC, `ShamelaToLibraryMapper` 413 LOC), внедрить `apps/` структуру фронта под ADR-018, унифицировать naming, удалить шум комментариев и мёртвый код, привести docs в актуальное состояние.

**Architecture:** 6 последовательных фаз. Phase 0 (audit) запускает 4 параллельных Explore-агента и фиксирует scope для следующих 5 фаз. Phase 1-5 идут sequential по audit findings с зелёными билдами между фазами и атомарными коммитами на каждую. Phase 5 архивирует старые сессии progress.md и создаёт project-level CLAUDE.md.

**Tech Stack:** Java 21, Spring Boot 3.5, JDBC Template, Postgres 16, Liquibase, React 19, Vite 6, Tailwind v4, React Flow, Zustand 5, openapi-typescript, Vitest, JUnit 5 + Testcontainers.

**Связанный spec:** `docs/specs/2026-05-11-codebase-cleanup-marathon-design.md`

**Out of scope (повтор из спека):**
- Изменения в схеме БД
- Изменения в публичных REST API контрактах
- `frontend/design-reference/`
- Содержимое `frontend/src/api/types.ts` (автоген)
- Features (этапы 25.b/d, 18.f, 19)
- Bulk shamela import
- Удаление существующих Liquibase миграций

---

## File Structure

### Создаются

| Файл | Назначение |
|------|-----------|
| `docs/audits/2026-05-11-codebase-audit.md` | свод audit findings (Phase 0) |
| `docs/audits/README.md` | index audit-документов |
| `docs/archive/progress-sessions-1-20.md` | архив старых записей progress.md (Phase 5) |
| `CLAUDE.md` (корень проекта) | быстрый старт для новых сессий Claude Code (Phase 5) |
| `frontend/src/apps/argument-map/` (директория) | модуль argument-map (Phase 2) |
| `frontend/src/apps/library/` (директория) | модуль library (Phase 2) |
| `frontend/src/apps/admin/` (директория) | модуль admin (Phase 2) |
| `frontend/src/shared/` (директория) | cross-app код (Phase 2) |
| `frontend/src/apps/argument-map/components/topic-graph/*` | разбиение TopicGraphPage (Phase 2) |
| `frontend/src/apps/library/components/book-reader/*` | разбиение BookReaderPage (Phase 2) |

### Модифицируются (с порогом > 50 LOC изменений)

| Файл | Phase | Что |
|------|-------|-----|
| `backend/src/main/java/.../ShamelaToLibraryMapper.java` | 1 | разнести на BookMapper/ChapterMapper/PageMapper |
| `frontend/src/pages/TopicGraphPage.tsx` | 2 | переехать в apps/, разбить на subcomponents |
| `frontend/src/pages/BookReaderPage.tsx` | 2 | переехать в apps/, разбить на subcomponents |
| `frontend/src/pages/*.tsx` | 2 | переехать в apps/{argument-map,library,admin}/pages/ |
| `frontend/src/components/{graph,layout,library,ui}/*` | 2 | переехать в apps/{...} или shared/ |
| `frontend/src/{stores,utils,api}/*` | 2 | переехать в shared/ или apps/ |
| `frontend/vite.config.ts` | 2 | добавить alias `@/shared`, `@/apps` |
| `frontend/tsconfig.json` | 2 | path mappings для alias'ов |
| `docs/progress.md` | 5 | оставить только Сессии 21+ (актуальные) |
| `docs/architecture.md` | 5 | отразить apps/ структуру и Phase 1 boundaries |
| `docs/glossary.md` | 5 | синхронизировать с кодом |
| `docs/decisions.md` | 5 | пометить stale ADR |
| `docs/gotchas.md` | 5 | удалить решённые ловушки |
| `docs/SESSION_START_PROMPT.md` | финализация | обновить под новую структуру + CLAUDE.md ссылку |

### Удаляются

После Phase 2: пустые директории `frontend/src/pages/`, `frontend/src/components/`, `frontend/src/stores/`, `frontend/src/utils/`, `frontend/src/api/` (всё переехало).

---

## Phase 0: Audit (research only)

**Контекст:** запускаем 4 параллельных Explore-агентов через single message с 4 Agent tool calls. Каждый агент получает фиксированный чек-лист (из спека) и формат finding'а. Координатор сводит результаты, дедупит cross-cutting findings, приоритизирует, пишет один документ.

### Task 0.1: Запустить 4 параллельных Explore-агента

**Files:**
- Read-only: backend Java, frontend TS/TSX, тесты, docs/

- [ ] **Step 1: Подготовить промпты для агентов**

Промпты должны:
- начинаться с роли и scope
- содержать полный чек-лист из спека (раздел "Чек-листы")
- содержать пример формата finding'а
- запрашивать output в виде markdown-секции с findings
- запрашивать executive summary (top-5 проблем) от агента
- исключать `frontend/design-reference/` для frontend агента
- запрашивать **полные file:line** в каждом finding'е

- [ ] **Step 2: Запустить агентов параллельно**

Single message с 4 Agent tool calls (subagent_type=Explore), все foreground.

- [ ] **Step 3: Получить результаты от всех 4-х**

Дождаться завершения. Если какой-то агент вернул шум вместо findings - попросить повторить со специфичным промптом про формат.

### Task 0.2: Свести findings в единый документ

**Files:**
- Create: `docs/audits/2026-05-11-codebase-audit.md`

- [ ] **Step 1: Дедуп cross-cutting**

Пройтись по парам потоков (backend/tests, frontend/tests, backend/docs, frontend/docs), найти findings указывающие на одну и ту же проблему с разных углов. Слить в один finding с tags из обеих категорий.

- [ ] **Step 2: Приоритизация Top-20**

Из всех findings отсортировать по severity (high → medium → low) с учётом effort (S → M → L). Записать Top-20 в executive summary.

- [ ] **Step 3: Phase backlog**

Каждый finding имеет поле "Phase". Сгруппировать в backlog'и:
- Phase 1 backlog: [B-XX списком]
- Phase 2 backlog: [F-XX, B-XX где relevant]
- Phase 3 backlog: [findings duplicate/hacks]
- Phase 4 backlog: [findings naming]
- Phase 5 backlog: [D-XX, T-XX]

- [ ] **Step 4: Записать документ**

Структура из спека (раздел "Output Phase 0"). Header с метаданными:
- дата audit'а
- количество findings по категориям
- ссылка на спек

- [ ] **Step 5: Создать audits/README.md**

```markdown
# Codebase audits

Аудиты кодовой базы - инвентаризация технического долга для cleanup сессий.

## Файлы

- `2026-05-11-codebase-audit.md` - первый полный audit после 24 сессий
  (backend Java + frontend TS/TSX + tests + docs). Источник для cleanup
  marathon (см. `docs/specs/2026-05-11-codebase-cleanup-marathon-design.md`)
```

- [ ] **Step 6: Commit**

```bash
git add docs/audits/
git commit -m "docs(audit): полный codebase audit перед cleanup marathon"
```

### Phase 0 acceptance gate

- [ ] Документ `audits/2026-05-11-codebase-audit.md` существует и не пуст
- [ ] Executive summary содержит Top-20 проблем
- [ ] Phase backlog'и заполнены (для каждой Phase 1-5)
- [ ] README.md создан
- [ ] Commit на месте

---

## Phase 1: Backend boundaries cleanup

**Контекст:** идём по B-XX findings категории `boundary` в приоритете high → medium. Известные крупные кандидаты:
- `ShamelaToLibraryMapper.java` 413 LOC - почти точно надо разнести
- остальные - смотрим что скажет audit

**Детальные шаги добавляются после Phase 0 audit'а** - на момент написания плана конкретные file:line известны только для самого большого файла. Шаги ниже - skeleton, расширяется при выполнении.

### Task 1.1: Pre-flight - запустить тесты бэка

- [ ] **Step 1: Прогнать ./mvnw test**

```bash
cd /mnt/c/my_folders/projects/argument-map/backend
./mvnw test
```

Expected: зелёный билд. Если красный - **не начинаем Phase 1**, fix first.

### Task 1.2: Разнести ShamelaToLibraryMapper (если в audit findings)

**Files:**
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/library/shamela/service/ShamelaToLibraryMapper.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/library/shamela/service/mapper/ShamelaBookMapper.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/library/shamela/service/mapper/ShamelaChapterMapper.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/library/shamela/service/mapper/ShamelaPageMapper.java`
- Modify: тесты `ShamelaToLibraryMapperTest.java` (если есть)

- [ ] **Step 1: Прочитать ShamelaToLibraryMapper целиком**

Понять реальные обязанности. Проверить что предположение "3 области (book/chapter/page)" верно. Если структура другая - переделать декомпозицию.

- [ ] **Step 2: Извлечь ShamelaBookMapper**

Перенести методы относящиеся к book metadata. Public методы становятся interface для ShamelaToLibraryMapper.

- [ ] **Step 3: Прогнать тесты**

```bash
./mvnw test -Dtest=ShamelaToLibraryMapperTest
```

Expected: зелёные.

- [ ] **Step 4: Извлечь ShamelaChapterMapper**

То же что Step 2 для chapters.

- [ ] **Step 5: Прогнать тесты**

То же что Step 3.

- [ ] **Step 6: Извлечь ShamelaPageMapper**

То же для pages.

- [ ] **Step 7: ShamelaToLibraryMapper становится orchestrator'ом**

Делегирует к 3 mapper'ам. Должен стать < 100 LOC.

- [ ] **Step 8: Прогнать полный тестовый набор**

```bash
./mvnw test
```

Expected: все тесты зелёные.

### Task 1.3..1.N: Остальные findings boundary категории

Шаги по шаблону Task 1.2 на основе audit findings.

### Task 1.End: Phase 1 commit + verify

- [ ] **Step 1: Прогнать verify**

```bash
./mvnw verify
```

Expected: зелёный билд + интеграционные тесты.

- [ ] **Step 2: Commit**

```bash
git add backend/
git commit -m "refactor(backend): boundaries cleanup - разнести крупные классы"
```

### Phase 1 acceptance gate

- [ ] Все B-XX boundary high/medium findings закрыты или явно отложены
- [ ] `./mvnw verify` зелёный
- [ ] Коммит на месте
- [ ] Размер бекенда не вырос значительно (LOC должен снизиться или
      остаться тем же)

---

## Phase 2: Frontend apps/ reorganization

**Контекст:** самая рискованная фаза - массивные перемещения файлов и обновления импортов. Разбиваем на под-задачи:
1. Pre-flight: green tests baseline
2. Создать структуру каталогов apps/{argument-map,library,admin}, shared/
3. Настроить vite alias + tsconfig path mapping
4. Разбить монстров (TopicGraphPage, BookReaderPage) **до** миграции
5. Перенести файлы с обновлением импортов
6. Прогнать тесты, build, vite dev
7. Удалить пустые директории
8. Commit

### Task 2.1: Pre-flight

**Files:**
- Read-only: тесты frontend

- [ ] **Step 1: Прогнать npm test**

```bash
cd /mnt/c/my_folders/projects/argument-map/frontend
npm test -- --run
```

Expected: зелёный. Если красный - не начинаем Phase 2.

- [ ] **Step 2: Прогнать npm run build**

```bash
npm run build
```

Expected: зелёный. Запомнить bundle size для финального sanity check.

- [ ] **Step 3: Прогнать typecheck**

Если в package.json есть `tsc --noEmit` или `typecheck` скрипт.

```bash
npm run typecheck 2>/dev/null || npx tsc --noEmit
```

Expected: 0 ошибок.

### Task 2.2: Настроить vite alias и tsconfig paths

**Files:**
- Modify: `frontend/vite.config.ts`
- Modify: `frontend/tsconfig.json` (или `tsconfig.app.json`)

- [ ] **Step 1: Vite alias**

В `vite.config.ts` добавить в `resolve.alias`:

```ts
import path from 'node:path'

export default defineConfig({
  // ... existing config
  resolve: {
    alias: {
      '@/shared': path.resolve(__dirname, 'src/shared'),
      '@/apps': path.resolve(__dirname, 'src/apps'),
    },
  },
})
```

- [ ] **Step 2: TS paths**

В `tsconfig.app.json` (или равном):

```json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/shared/*": ["src/shared/*"],
      "@/apps/*": ["src/apps/*"]
    }
  }
}
```

- [ ] **Step 3: Vitest config**

Vitest наследует от vite.config.ts если корректно настроено. Проверить.

- [ ] **Step 4: Sanity check**

```bash
npm test -- --run && npm run build
```

Expected: всё ещё зелёное (alias добавлены но не используются - тестируем что ничего не сломалось).

### Task 2.3: Создать структуру директорий

- [ ] **Step 1: mkdir**

```bash
cd /mnt/c/my_folders/projects/argument-map/frontend/src
mkdir -p apps/argument-map/{pages,components,utils,hooks}
mkdir -p apps/library/{pages,components,hooks}
mkdir -p apps/admin/pages
mkdir -p shared/{api,components/layout,components/ui,stores,utils,hooks}
```

### Task 2.4: Разбить TopicGraphPage.tsx (1161 LOC)

**Files:**
- Read: `frontend/src/pages/TopicGraphPage.tsx`
- Create: `frontend/src/apps/argument-map/pages/TopicGraphPage.tsx` (orchestrator)
- Create: `frontend/src/apps/argument-map/components/topic-graph/TopicGraphCanvas.tsx`
- Create: `frontend/src/apps/argument-map/components/topic-graph/TopicGraphToolbar.tsx`
- Create: `frontend/src/apps/argument-map/components/topic-graph/TopicGraphSidebar.tsx` (если применимо)
- Create: `frontend/src/apps/argument-map/hooks/useTopicGraphState.ts` (если есть state-heavy логика)

- [ ] **Step 1: Прочитать TopicGraphPage.tsx целиком**

Понять реальные UI-зоны. Возможные деления:
- Header/Toolbar (топ-бар с действиями)
- Canvas (React Flow с узлами/рёбрами)
- Side panels (NodeDetailsPanel, EdgeDetailsPanel - уже отдельные)
- State management (если есть большой useReducer/useState)

- [ ] **Step 2: Выделить useTopicGraphState (если уместно)**

Если в TopicGraphPage большой state (>10 useState) или сложный useReducer - вынести в hook.

- [ ] **Step 3: Выделить subcomponents**

По UI-зонам. Каждый файл < 250 LOC цель.

- [ ] **Step 4: Прогнать тесты**

```bash
npm test -- --run TopicGraphPage
```

Expected: зелёные.

### Task 2.5: Разбить BookReaderPage.tsx (714 LOC)

Аналогично Task 2.4 для BookReaderPage. Возможные subcomponents:
- BookReaderHeader (топ-бар с book title, navigation)
- ChaptersTree (sidebar tree глав)
- PageNavigation (jump to page input + prev/next)
- PageContent (рендер страницы с PDF viewer toggle)

### Task 2.6: Миграция остальных файлов

**Принцип:** перемещаем по 5-10 файлов за раз, после каждой группы - тесты.

- [ ] **Step 1: Frontend pages → apps/{...}/pages/**

```bash
git mv src/pages/TopicListPage.tsx src/apps/argument-map/pages/
git mv src/pages/TopicListPage.test.tsx src/apps/argument-map/pages/
git mv src/pages/CreateTopicPage.tsx src/apps/argument-map/pages/
git mv src/pages/CreateTopicPage.test.tsx src/apps/argument-map/pages/
git mv src/pages/TopicGraphPage.test.tsx src/apps/argument-map/pages/
git mv src/pages/BookListPage.tsx src/apps/library/pages/
git mv src/pages/AdminShamelaPage.tsx src/apps/admin/pages/
```

(TopicGraphPage и BookReaderPage уже перемещены через Task 2.4/2.5 как новые orchestrator'ы; старые удаляются)

- [ ] **Step 2: Components graph → apps/argument-map/components/**

```bash
git mv src/components/graph src/apps/argument-map/components/graph
```

- [ ] **Step 3: Components library → apps/library/components/**

```bash
git mv src/components/library src/apps/library/components/library
```

(или переименовать в `book-reader` если только PdfViewer + book-related)

- [ ] **Step 4: Components layout, ui → shared/components/**

```bash
git mv src/components/layout src/shared/components/layout
git mv src/components/ui src/shared/components/ui
```

- [ ] **Step 5: stores → shared/stores/**

```bash
git mv src/stores src/shared/stores
```

- [ ] **Step 6: utils - разнести по принадлежности**

```bash
git mv src/utils/edgeRules.ts src/apps/argument-map/utils/
git mv src/utils/edgeRules.test.ts src/apps/argument-map/utils/
git mv src/utils/graphLayout.ts src/apps/argument-map/utils/
git mv src/utils/graphLayout.test.ts src/apps/argument-map/utils/
git mv src/utils/attachmentTokens.ts src/apps/argument-map/utils/
git mv src/utils/designTokens.ts src/shared/utils/
```

- [ ] **Step 7: api → shared/api/**

```bash
git mv src/api src/shared/api
```

- [ ] **Step 8: Обновить импорты**

В перемещённых файлах + App.tsx + main.tsx. Импорты заменяются:
- `from '../components/...'` → `from '@/shared/components/...'` или `from '@/apps/.../components/...'`
- `from '../api/client'` → `from '@/shared/api/client'`
- relative imports внутри одного app остаются relative

Используем grep + Edit. Алгоритм:
1. `grep -rn "from '\.\./" src/apps src/shared` - находим все relative imports пересекающие границы
2. Каждый заменяем на @/shared/... или @/apps/...
3. После каждой группы - tests

- [ ] **Step 9: Удалить пустые директории**

```bash
rmdir src/pages src/components src/stores src/utils src/api 2>/dev/null
```

### Task 2.7: Tests + build + dev server

- [ ] **Step 1: npm test**

```bash
npm test -- --run
```

Expected: все тесты зелёные. Если красные - исправить импорты, тестовые пути.

- [ ] **Step 2: npm run build**

```bash
npm run build
```

Expected: зелёный, bundle size ~ как до Phase 2 (новая структура не добавляет dependencies).

- [ ] **Step 3: npm run typecheck**

```bash
npm run typecheck 2>/dev/null || npx tsc --noEmit
```

Expected: 0 ошибок.

- [ ] **Step 4: Vite dev smoke check**

Не запускать долгий dev server в фоне - просто `npx vite build --mode development` или открыть страницу через playwright skill если возможно.

### Task 2.End: Phase 2 commit

- [ ] **Step 1: Commit**

```bash
git add frontend/
git commit -m "refactor(frontend): внедрение apps/ структуры + разбиение монстров"
```

### Phase 2 acceptance gate

- [ ] Все F-XX boundary findings закрыты
- [ ] TopicGraphPage и BookReaderPage разбиты, orchestrator < 250 LOC
- [ ] `frontend/src/{pages,components,stores,utils,api}` удалены (пусты)
- [ ] `npm test`, `npm run build`, `npx tsc --noEmit` все зелёные
- [ ] Bundle size не вырос значительно (< +10%)
- [ ] Commit на месте

---

## Phase 3: Дубликаты/хаки/мёртвый код

**Контекст:** идём по findings duplicate + hacks + dead code из audit'а. После Phase 1-2 структуры устаканились, дубли видны.

### Task 3.1: Extract дубликатов

Шаги по шаблону:
1. Прочитать оба места дублирования
2. Extract в shared utility (frontend - в `shared/utils/`, backend - в подходящий пакет)
3. Заменить оба места на вызов utility
4. Тесты на новую utility (если логика нетривиальная)
5. Прогнать тесты после каждой группы

### Task 3.2: Чинить хаки

Каждый hack из audit'а:
1. Понять что хак обходит
2. Решить proper решение
3. Заменить
4. Если proper решение - большая работа - оставить hack с детальным TODO с owner + date + reference

### Task 3.3: Удалить мёртвый код

**Files:** все файлы где audit нашёл dead code

- [ ] **Step 1: Удалить unused exports**

Использовать `npx ts-unused-exports tsconfig.json` (если есть) или ручной поиск.

- [ ] **Step 2: Удалить закомментированные блоки**

Grep для блоков `/\* ... \*/` и `//...` > 3 строк подряд, не являющихся JSDoc/TSDoc.

- [ ] **Step 3: Удалить unused imports**

ESLint должен ловить - запустить `npm run lint -- --fix` если есть.

- [ ] **Step 4: Удалить unused locals**

То же через линтер.

### Task 3.4: Удалить излишние комментарии

**Принцип:** комментарий **why** оставляем, **what** удаляем.

Удаляемые паттерны:
- `// получаем книгу` перед `getBook()` - тавтология
- `// TODO: добавить логирование` без owner и старше 3 сессий
- claude-generated блочные комментарии описывающие то что код и так показывает
- избыточные JSDoc на private/internal методах

Сохраняемые:
- Комментарии объясняющие нетривиальное решение (workaround, performance trick)
- TODO с owner + ссылкой на issue/ADR
- JSDoc на публичных API
- Ссылки на ADR / spec / gotcha

### Task 3.End: Phase 3 verify + commit

- [ ] **Step 1: Прогнать всё**

```bash
cd /mnt/c/my_folders/projects/argument-map/backend && ./mvnw verify
cd /mnt/c/my_folders/projects/argument-map/frontend && npm test -- --run && npm run build
```

Expected: всё зелёное.

- [ ] **Step 2: Commit**

```bash
git add .
git commit -m "refactor: дедуп + удаление мёртвого кода и шума комментариев"
```

### Phase 3 acceptance gate

- [ ] Все duplicate/hack/dead-code findings закрыты или с явным основанием отложены
- [ ] LOC frontend и backend снизились (или хотя бы не выросли)
- [ ] Билды зелёные
- [ ] Commit на месте

---

## Phase 4: Naming/consistency pass

**Контекст:** на основе naming findings из audit'а. На момент написания плана - точные конвенции зафиксированы в спеке (раздел Phase 4).

### Task 4.1: UUID-поля

- [ ] **Step 1: Найти все варианты UUID-полей**

```bash
cd backend && grep -rn "UUID.*Id\|UUID.*Uuid" src/main/java/ | head -50
cd frontend && grep -rn "Id:\|Uuid:" src/ | head -50
```

- [ ] **Step 2: Применить конвенцию**

Конвенция (из спека):
- В Entity primary key: `UUID id`
- В DTO собственный ID: `UUID id`
- В DTO foreign reference: `bookId`, `chapterId`, etc.
- На frontend - то же

- [ ] **Step 3: Прогнать тесты**

### Task 4.2: DTO suffix унификация

Привести к `*Response` / `*Request` / `*Command`. Удалить `*Dto`, `*Result` если такие были.

### Task 4.3: Method naming

- `find*` - возврат Optional или nullable
- `get*` - бросает exception
- Удалить `load*` (если использовался)

### Task 4.4: Boolean naming

- `is*` для состояний
- `has*` для владения

### Task 4.5: Component / Hook / Store naming

Frontend:
- `*Page` для top-level pages
- `*Panel` для боковых панелей
- `*Modal` для модалок
- `use*` для hooks
- `use*Store` для Zustand stores

### Task 4.End: Phase 4 verify + commit

- [ ] **Step 1: Регенерировать openapi types**

```bash
cd backend && ./mvnw -DskipTests compile
# backend перезапускает Абдула; после старта:
cd ../frontend && npm run generate-api
```

(Если переименования затронули REST endpoint params/response fields)

- [ ] **Step 2: Прогнать всё**

```bash
cd backend && ./mvnw verify
cd ../frontend && npm test -- --run && npm run build
```

- [ ] **Step 3: Commit**

```bash
git add .
git commit -m "refactor: унификация naming conventions (DTO, methods, UUID, components)"
```

### Phase 4 acceptance gate

- [ ] Все naming findings закрыты
- [ ] OpenAPI schema актуальна
- [ ] Билды зелёные
- [ ] Commit на месте

---

## Phase 5: Docs cleanup + project CLAUDE.md

### Task 5.1: Архивация progress.md

**Files:**
- Modify: `docs/progress.md`
- Create: `docs/archive/progress-sessions-1-20.md`

- [ ] **Step 1: Mkdir archive**

```bash
mkdir -p docs/archive
```

- [ ] **Step 2: Найти cutpoint между Сессиями 20 и 21**

Открыть `docs/progress.md`, найти заголовок `## ... — Сессия 21`. Всё что **выше** (старше) - в архив, всё что ниже + Сессии 21+ - остаётся.

- [ ] **Step 3: Создать archive файл с шапкой**

```markdown
# Архивный лог progress - Сессии 1-20

Этот файл - архив старых записей `docs/progress.md`. Содержит хронологию
сессий с Сессии 1 (инициализация проекта) по Сессию 20 включительно.

**Когда читать:** только при необходимости исторического контекста.
Для текущей работы используйте актуальный `docs/progress.md` (Сессии 21+).

Архивирован: 2026-05-11 в рамках Cleanup Marathon (Phase 5).

---

[здесь содержимое старых записей]
```

- [ ] **Step 4: Обрезать progress.md**

В `docs/progress.md` оставить:
- Header
- Ссылку наверху: "Архив сессий 1-20 в `docs/archive/progress-sessions-1-20.md`"
- Записи Сессий 21+

### Task 5.2: Создать корневой CLAUDE.md

**Files:**
- Create: `/mnt/c/my_folders/projects/argument-map/CLAUDE.md`

- [ ] **Step 1: Содержимое**

```markdown
# CLAUDE.md - argument-map quick start

Этот файл - быстрый старт для новых сессий Claude Code в этом проекте.
Полный context-промпт - в `docs/SESSION_START_PROMPT.md`.

## Стэк

- Backend: Java 21, Spring Boot 3.5, JDBC Template, Postgres 16, Liquibase
- Frontend: React 19, Vite 6, Tailwind v4, React Flow, Zustand 5, Vitest
- Docker compose для Postgres (опционально MinIO)

## Структура

```
backend/                 - Java модуль (Maven)
frontend/                - React/TS модуль
  src/apps/              - три приложения (argument-map, library, admin)
  src/shared/            - cross-app код
  design-reference/      - статические дизайн-референсы (НЕ ТРОГАТЬ)
docs/                    - вся документация
  archive/               - архив старых сессий progress
  superpowers/           - specs, plans, audits
scripts/                 - утилиты (seed-mawlid.sh и др.)
docker-compose.yml       - инфраструктура
```

## Команды

```bash
# Backend
cd backend
./mvnw verify              # полный билд + тесты
./mvnw test                # только тесты
./mvnw -DskipTests compile # компиляция без тестов
# spring-boot:run запускает Абдула в отдельном терминале - не запускай сам

# Frontend (везде в WSL2, не Windows)
cd frontend
npm test -- --run          # все тесты Vitest
npm run build              # production build
npm run generate-api       # регенерация types.ts из OpenAPI бэка
npx tsc --noEmit           # typecheck

# Инфра
docker compose up -d       # postgres
scripts/seed-mawlid.sh     # тестовая тема
```

## Документация (в порядке важности при старте новой сессии)

1. `docs/SESSION_START_PROMPT.md` - стартовый промпт со всеми правилами
2. `docs/roadmap.md` - текущий этап и backlog
3. `docs/decisions.md` - ADR'ы (фундаментальные решения)
4. `docs/gotchas.md` - известные ловушки и их обход
5. `docs/architecture.md` + `docs/architecture-platform.md`
6. `docs/api-contract.md` - источник истины REST контракта
7. `docs/glossary.md` - термины проекта
8. `docs/progress.md` - актуальный лог сессий (старое в `docs/archive/`)
9. `docs/specs/` - спеки крупных работ
10. `docs/audits/` - аудиты кодовой базы

## Конвенции

- **Naming:** см. detail в `docs/architecture.md`, кратко - `*Response/*Request/*Command` DTO, `find*` nullable, `get*` throws, `is*/has*` booleans, `*Page/*Panel/*Modal` components, `use*` hooks
- **Commits:** Conventional Commits (`feat:`, `refactor:`, `docs:`, `chore:`, `fix:`, etc.) + scope при необходимости
- **Stack discipline:** не менять стэк без апрува (см. ADR-018)
- **Не трогать:** `frontend/design-reference/`, схема БД (только через Liquibase миграции), `frontend/src/shared/api/types.ts` (автоген)
- **WSL2:** все команды бэка/фронта выполнять в WSL2, не в Windows-side
- **Backend rerun:** не запускай spring-boot:run сам - просить Абдулу
```

### Task 5.3: Обновить architecture.md

**Files:**
- Modify: `docs/architecture.md`

- [ ] **Step 1: Отразить apps/ структуру**

В разделе "Frontend модули" заменить описание плоской структуры на apps/ + shared/. Перечислить три apps с их responsibility.

- [ ] **Step 2: Отразить Phase 1 backend changes**

Если ShamelaToLibraryMapper был разнесён - обновить описание.

### Task 5.4: Обновить glossary.md

**Files:**
- Modify: `docs/glossary.md`

- [ ] **Step 1: Сверить с кодом**

Для каждого термина в glossary проверить - встречается в коде? с тем же именем?

- [ ] **Step 2: Удалить устаревшие**

- [ ] **Step 3: Добавить новые**

Если в коде есть сущности не описанные в glossary - добавить.

### Task 5.5: Stale ADR

**Files:**
- Modify: `docs/decisions.md`

- [ ] **Step 1: По D-XX findings из audit'а**

Для каждого stale ADR пометить:
- `[OBSOLETE - заменён <ADR-NN> / факт код<reference>]`
- `[PARTIALLY APPLIED - <деталь>]`

- [ ] **Step 2: Если ADR удаляются - НЕ удалять**, а помечать. Удалять можно только если ADR не релевантен и никогда не был реализован.

### Task 5.6: gotchas.md

**Files:**
- Modify: `docs/gotchas.md`

- [ ] **Step 1: По D-XX findings**

Удалить gotcha которые уже не актуальны (фикс снял проблему). Сохранить актуальные.

### Task 5.End: Phase 5 commit

- [ ] **Step 1: Commit**

```bash
git add docs/ CLAUDE.md
git commit -m "docs: cleanup + проектный CLAUDE.md + архивация progress"
```

### Phase 5 acceptance gate

- [ ] `docs/progress.md` < 1500 LOC
- [ ] `docs/archive/progress-sessions-1-20.md` создан
- [ ] `CLAUDE.md` создан в корне
- [ ] `docs/architecture.md` отражает реальную структуру
- [ ] `docs/glossary.md` синхронизирован
- [ ] Stale ADR помечены
- [ ] Commit на месте

---

## Финализация

### Task F.1: Полный билд

- [ ] **Step 1: Backend**

```bash
cd /mnt/c/my_folders/projects/argument-map/backend
./mvnw verify
```

Expected: зелёный.

- [ ] **Step 2: Frontend**

```bash
cd /mnt/c/my_folders/projects/argument-map/frontend
npm test -- --run
npm run build
npx tsc --noEmit
```

Expected: всё зелёное.

- [ ] **Step 3: Bundle size sanity check**

Сравнить размер `dist/assets/*.js` с pre-cleanup значениями (запомнили в Task 2.1 Step 2). Допустимо +10% максимум.

### Task F.2: Обновить SESSION_START_PROMPT.md

**Files:**
- Modify: `docs/SESSION_START_PROMPT.md`

- [ ] **Step 1: Указать новую apps/ структуру**

В разделе "Структура проекта" заменить плоскую структуру на apps/+shared/.

- [ ] **Step 2: Указать на CLAUDE.md**

Добавить пункт: "**Перед началом** - прочитать `CLAUDE.md` в корне проекта (быстрый context)".

- [ ] **Step 3: Обновить топ команд**

Если поменялись команды (например npm run generate-api местоположение types.ts) - обновить.

### Task F.3: Записать в progress.md итог marathon'а

**Files:**
- Modify: `docs/progress.md`

- [ ] **Step 1: Создать запись Сессия 25**

```markdown
## 2026-05-11 — Сессия 25 — Cleanup Marathon

Полный рефакторинг кодовой базы после 24 сессий. 6 фаз, X коммитов.

### Сделано

- Phase 0: Audit через 4 параллельных Explore-агентов, документ
  `docs/audits/2026-05-11-codebase-audit.md` с N findings
- Phase 1: backend boundaries (ShamelaToLibraryMapper разнесён на 3,
  плюс ...)
- Phase 2: внедрена `src/apps/{argument-map,library,admin}` + `src/shared/`
  структура фронта, TopicGraphPage (1161 → orchestrator + N
  subcomponents), BookReaderPage аналогично
- Phase 3: дедуп N мест, починены/удалены M хаков, удалено K строк
  комментариев-шума
- Phase 4: унификация naming (DTO suffix, UUID-поля, method prefixes,
  component naming)
- Phase 5: progress.md заархивирован (Сессии 1-20 → archive/),
  создан корневой CLAUDE.md, architecture.md и glossary.md
  синхронизированы

### Решения

- ADR-018 platform pivot структура **применена** на фронте (был
  plan, теперь реальность)
- Принят принцип "комментарий why - оставляем, what - удаляем"
- progress.md разделён на актуальный (21+) и архивный (1-20) для
  снижения токен-расхода новых сессий

### Проблемы

[заполнить если были]

### Следующий шаг (Сессия 26)

Возврат к feature work по roadmap'у:
- 18.f CitationPicker (приоритет №1 после marathon)
- 25.b MinIO cache / 25.d page sync
- Импорт ещё 1-2 книг shamela
```

### Task F.4: Финальный коммит

- [ ] **Step 1: Commit**

```bash
git add .
git commit -m "chore: cleanup marathon финализация - SESSION_START + progress итог"
```

### Финализация acceptance gate

- [ ] Все билды зелёные
- [ ] Все тесты проходят
- [ ] SESSION_START_PROMPT.md и CLAUDE.md описывают новую структуру
- [ ] progress.md содержит запись о Сессии 25
- [ ] Финальный коммит на месте
- [ ] `git status` показывает чистую working directory

---

## Self-Review (выполняется после написания плана)

**Spec coverage:**
- ✅ Phase 0 audit → Phase 0 раздел плана
- ✅ Phase 1 backend boundaries → Phase 1 раздел плана
- ✅ Phase 2 frontend apps/ + разбиение монстров → Phase 2 раздел
- ✅ Phase 3 дубли/хаки/dead-code → Phase 3 раздел
- ✅ Phase 4 naming → Phase 4 раздел
- ✅ Phase 5 docs + CLAUDE.md + архивация → Phase 5 раздел
- ✅ Финализация → отдельный раздел

**Placeholder scan:**
- ⚠️ В Phase 1 Task 1.3..1.N и в Phase 2 Task 2.6 Step 8 есть фразы
  "Шаги по шаблону на основе audit findings" - это допустимо потому
  что детали неизвестны до audit'а. Уточняются после Phase 0.
- ⚠️ Phase 3 Task 3.1-3.4 - тоже framework а не конкретные шаги.
  Audit-зависимо, окей.
- ✅ Phase 0, 2 (TopicGraphPage/BookReaderPage разбиение), 4, 5,
  финализация - конкретные шаги.

**Type/method consistency:**
- ✅ ShamelaBookMapper / ChapterMapper / PageMapper - имена
  используются единообразно
- ✅ apps/argument-map / apps/library / apps/admin - имена
  единообразны
- ✅ alias @/shared, @/apps - используются единообразно

**Решения inline без re-review:** Phase 0 → Subagent-Driven (4
параллельных Explore-агентов), остальные фазы → Inline Execution в
основной сессии с TaskUpdate per phase. Это применение autonomy mode
пользователя (явное "сделай как хочешь").
