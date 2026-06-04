# Project-specific Skills — design

**Дата:** 2026-05-19
**Статус:** committed, awaiting execution
**Sub-project:** C из программы «Claude Code harness setup»
(статья Anthropic «How Claude Code works in large codebases», май 2026)
**Предыдущий sub-project:** B (Hooks setup) — closed
**Следующий sub-project:** D (LSP — Java jdtls, когда Eclipse mirrors разблокируются)

---

## 1. Контекст и motivation

Anthropic в статье от 14.05.2026 описывает harness Claude Code как
последовательность слоёв: CLAUDE.md → hooks → skills → plugins → MCP.
Sub-projects A (Foundation cleanup) и B (Hooks setup) закрыты. Следующий
слой — **skills**: локальные процедурные гайды для повторяющихся рабочих
процессов.

В отличие от CLAUDE.md (всегда в контексте), skills загружаются **по
требованию** — только когда Claude решает, что они релевантны текущей
задаче. Это позволяет хранить детальные, опциональные инструкции без
постоянного увеличения контекста.

Проект argument-map имеет несколько устойчивых workflow которые
повторяются раз в несколько сессий и каждый раз требуют вспоминать
нетривиальные детали:

- **Liquibase миграции** — строгий формат ID, CDATA-escape, rollback,
  порядок нумерации, регистрация в мастер-файле
- **REST endpoint scaffold** — цепочка Controller + Service + DTO + IT +
  api-contract update
- **Shamela ETL debug** — специфичные симптомы, staging DAO паттерны,
  типовые root causes
- **Library page rendering** — 3 render-mode state machine
  (PDF/OCR/Image), `lib_pages` статусы

Без skills Claude каждый раз либо читает backend/CLAUDE.md целиком для
поиска паттерна Liquibase, либо полагается на prior context из истории.
Skills превращают эти нетривиальные процедуры в надёжный, воспроизводимый
процесс.

### Ссылка на Anthropic article

Статья рекомендует project-specific skills для:
1. Workflows специфичных для домена (наш случай: Liquibase + shamela ETL)
2. Scaffolding patterns (наш: REST endpoint chain)
3. Debugging playbooks (наш: shamela-parser-debug)

Ключевое из статьи: description поля в frontmatter — **основной
механизм активации**. Claude видит только имя + description в
`available_skills` и решает вызвать skill только для задач достаточно
сложных, чтобы skill имел смысл.

---

## 2. Goals

**Success criterion:** каждый из 4 skills usable и корректно активируется
без явного `/skill-name` вызова — достаточно описать задачу ("создай
миграцию", "добавь endpoint").

Конкретные goals:

1. **Liquibase migration skill** — исключить ошибки формата changeset ID,
   автоматически регистрировать в db.changelog-master.xml, не пропускать
   rollback секцию
2. **New REST endpoint skill** — ускорить scaffold цепочки (DTO + Controller
   + Service + IT + docs) за счёт explicit checklist порядка
3. **Shamela parser debug skill** — дать playbook для типичных ETL issues
   без необходимости читать весь shamela package
4. **Library page rendering skill** — зафиксировать state machine
   lib_pages и 3 render-mode логику в одном месте

---

## 3. Non-goals

- Автоматизация через скрипты (skills — текстовые инструкции, не код)
- Все 4 skills в одной сессии — только liquibase-migration в Сессии 48,
  остальные в backlog
- Изменения backend/src или frontend/src
- Push на origin
- Запуск `./mvnw verify` (skills — документация, не код)
- Описание существующих skills (Superpowers, frontend-design и пр.)
- LSP, MCP, Sub-project E (уже закрыты или отдельно)

---

## 4. Design — каждый skill

### 4.1 liquibase-migration

**Trigger (description для Claude):** Активируется когда Claude создаёт
новую Liquibase-миграцию для проекта, добавляет колонку, создаёт таблицу,
изменяет схему БД. Ключевые сигналы: «миграция», «changeset», «добавить
колонку», «создать таблицу», «изменить схему», «Liquibase», действия над
`db/changelog/`.

**Inputs:**
- Описание изменения (например "add status column to topics")
- Целевая таблица
- Тип изменения (addColumn / createTable / createIndex / dropColumn / etc.)

**Outputs:**
- Файл `backend/src/main/resources/db/changelog/changes/YYYYMMDD-NN-description.xml`
- Запись `<include>` в `db.changelog-master.xml`
- Корректный changeset ID по формату
- Rollback секция
- CDATA wrap для XML-опасных символов

**Format ID:**
```
YYYYMMDD-NN-short-description-kebab-case
```
где `NN` — следующий порядковый номер (определяется через `ls changes/ | sort -V | tail -3`).

**Template (вербатим в skill):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                            https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <!--
        Краткое описание зачем эта миграция.
        Если связана с backlog/roadmap - сослаться.
    -->
    <changeSet id="YYYYMMDD-NN-short-description" author="Abdula Basnukaev">
        <comment>Человекочитаемый комментарий на русском</comment>
        <!-- основное изменение здесь -->
        <rollback>
            <!-- обратная операция -->
        </rollback>
    </changeSet>

</databaseChangeLog>
```

**CDATA rule:** если в comment или SQL тексте встречается `&`, `<`, `>` —
оборачивать в `<![CDATA[ ... ]]>` или экранировать как `&amp;`, `&lt;`.
Символ `&` в обычном XML тексте ломает Liquibase парсинг (gotcha из
`docs/gotchas.md`).

**Примеры (реальные из проекта):**

1. addColumn с index:
```xml
<changeSet id="20260519-48-edge-z-index" author="Abdula Basnukaev">
    <comment>Добавляем edges.z_index для stacking order рёбер в графе</comment>
    <addColumn tableName="edges">
        <column name="z_index" type="INTEGER" defaultValueNumeric="0">
            <constraints nullable="false"/>
        </column>
    </addColumn>
    <createIndex tableName="edges" indexName="idx_edges_z_index">
        <column name="z_index"/>
    </createIndex>
    <rollback>
        <dropIndex tableName="edges" indexName="idx_edges_z_index"/>
        <dropColumn tableName="edges" columnName="z_index"/>
    </rollback>
</changeSet>
```

2. createTable (из миграции 02):
```xml
<changeSet id="20260413-02-create-users-table" author="Abdula Basnukaev">
    <comment>Создаём таблицу пользователей</comment>
    <createTable tableName="users">
        <column name="id" type="uuid" defaultValueComputed="gen_random_uuid()">
            <constraints primaryKey="true"/>
        </column>
        <column name="username" type="VARCHAR(100)">
            <constraints nullable="false" unique="true"/>
        </column>
        <column name="created_at" type="TIMESTAMPTZ" defaultValueComputed="now()">
            <constraints nullable="false"/>
        </column>
    </createTable>
    <rollback>
        <dropTable tableName="users"/>
    </rollback>
</changeSet>
```

**Acceptance criteria:**
- ID format корректен (YYYYMMDD-NN-description)
- author всегда "Abdula Basnukaev"
- rollback секция присутствует (если изменение обратимо)
- файл зарегистрирован в db.changelog-master.xml
- нет незаэкранированных `&` в XML

---

### 4.2 new-rest-endpoint

**Trigger (description):** Активируется когда нужно добавить новый REST
endpoint, создать новый API метод, scaffold CRUD для новой сущности.
Ключевые сигналы: «новый endpoint», «добавить API», «создать контроллер»,
«scaffold service», «REST», «CRUD», «новый маршрут».

**Inputs:**
- HTTP метод + путь (например POST /api/v1/topics/{id}/flags)
- Домен (argument-map / library / auth / admin)
- DTO входа/выхода

**Outputs (цепочка):**
1. DTO файлы (`*Request` / `*Response` record)
2. Repository метод (если нужен новый SQL)
3. Service метод с бизнес-логикой
4. Controller метод с аннотациями
5. IT тест (positive + negative cases)
6. Обновление `docs/api-contract.md`

**Порядок чётко зафиксирован** — bottom-up: Repository → Service → DTO →
Controller → IT → docs. Controller не пишется без Service. IT не пишется
без Controller.

**Примеры структуры:** skill содержит ссылки на существующие примеры:
- Простой endpoint: `GET /api/v1/topics/{id}` → `TopicController.getOne`
- Paginated list: `GET /api/v1/authorities` → `AuthorityController.list`
- Mutation: `PATCH /api/v1/authorities/{id}` → `AuthorityController.update`

**Acceptance criteria:**
- Chain полностью покрыт (нет оставшихся TODO)
- api-contract.md обновлён в том же коммите
- IT покрывает: success, invalid input (400), not found (404)

---

### 4.3 shamela-parser-debug

**Trigger (description):** Активируется при дебаге shamela ETL issues,
ошибках парсинга, staging таблицах, ShamelaApiClient failures,
ShamelaMapper неверных данных. Ключевые сигналы: «shamela», «ETL»,
«staging», «парсинг», «ShamelaApiClient», «import не работает».

**Типичные issues + diagnostic steps:**

1. **ShamelaApiClient 404/timeout** — проверить `shamela.base-url` в
   application.yml, проверить `@Tag("live")` тест изоляцию
2. **Staging DAO неверные данные** — читать staging таблицы напрямую
   (`SELECT * FROM shamela_staging_* WHERE ...`), сравнить с ожидаемым
   из API response
3. **ShamelaMapper NPE** — проверить null-safety в mapper chain,
   особенно `ShamelaAuthorityResolver` (может вернуть Optional.empty)
4. **Дубликаты при re-import** — staging таблицы не очищаются автоматически,
   проверить upsert логику
5. **ETL зависает** — проверить `@Tag("live")` тесты не запущены ли в
   обычном verify, проверить Testcontainers shutdown hooks

**Memory references:** `docs/gotchas.md` секция shamela ETL, ADR про
shamela import design.

**Acceptance criteria:**
- Skill покрывает 5 наиболее частых symptom classes
- Каждый symptom имеет diagnostic command или code snippet

---

### 4.4 library-page-rendering

**Trigger (description):** Активируется при работе с lib_pages rendering
pipeline, OCR статусами, PDF view, Image scans, форматированным контентом.
Ключевые сигналы: «lib_pages», «OCR», «render mode», «PDF страница»,
«ImageRegion», «formatted_content», «ai_edit_status».

**State machine lib_pages:**
- `ocr_status`: PENDING → PROCESSING → DONE / FAILED
- `ai_edit_status`: PENDING → PROCESSING → DONE / FAILED
- `formatted_content`: null (raw OCR), populated (AI-edited)

**3 render modes (frontend logic):**
1. **PDF mode** — если `book.pdfPath` существует и page имеет `printed_page`
2. **OCR mode** — если `ocr_status = DONE` и `content` непустой
3. **Image mode** — если есть image scans в `lib_pages` + ImageRegion

**Приоритет режимов:** PDF > OCR > Image (fallback цепочка)

**Backend docs references:**
- `backend/docs/ocr-pipeline.md` — детали OCR async pipeline
- `backend/docs/ai-editing.md` — AI edit state machine

**Acceptance criteria:**
- State machine описан точно (все статусы)
- 3 render mode логика воспроизводима из skill без чтения frontend кода

---

## 5. Storage layout

**Где живут skills:** `.claude/skills/<skill-name>/SKILL.md`

Обоснование:
- `.claude/` — уже существует в проекте (settings.json, hooks/, commands/)
- `skills/` — mirror структуры плагинов Superpowers (`.claude/plugins/
  cache/claude-plugins-official/*/skills/`)
- Каждый skill — отдельная директория (возможность добавить
  `references/` или `assets/` без реорганизации)
- Не `~/.claude/` (глобальный) — skills специфичны для этого проекта

**Итоговая структура:**
```
.claude/
├── commands/
├── helpers/
├── hooks/
├── lsp-setup.md
├── settings.json
├── settings.local.json
└── skills/
    ├── liquibase-migration/
    │   └── SKILL.md
    ├── new-rest-endpoint/
    │   └── SKILL.md
    ├── shamela-parser-debug/
    │   └── SKILL.md
    └── library-page-rendering/
        └── SKILL.md
```

**Регистрация в settings.json:** По конвенции Claude Code Skills
(`SKILL.md` frontmatter с `name:` + `description:` fields) — skills
в `.claude/skills/` обнаруживаются автоматически при наличии корректного
frontmatter. Явная регистрация в settings.json не требуется (как работают
плагины-навыки из `~/.claude/plugins/`).

---

## 6. Acceptance criteria (overall)

- [ ] Spec committed
- [ ] Plan для liquibase-migration committed
- [ ] `.claude/skills/liquibase-migration/SKILL.md` создан
- [ ] Manual test: задача «add column X to table Y» активирует skill
- [ ] Skill содержит: frontmatter, step-by-step, template, 2 примера
- [ ] db.changelog-master.xml registration step включён
- [ ] Backlog для 3 remaining skills зафиксирован в `docs/backlog.md`
- [ ] SESSION_START_PROMPT обновлён (Sub-project C entry)

---

## 7. Декомпозиция (4 отдельных коммита, по одному на skill)

| Коммит | Что | Условие |
|--------|-----|---------|
| `feat(.claude): liquibase-migration skill` | Сессия 48 | этот файл |
| `feat(.claude): new-rest-endpoint skill` | backlog | зависит от C1 |
| `feat(.claude): shamela-parser-debug skill` | backlog | зависит от C1 |
| `feat(.claude): library-page-rendering skill` | backlog | зависит от C1 |

Каждый skill независим от других. Порядок следующих 3 — по
частоте использования (new-rest-endpoint > library-page-rendering >
shamela-parser-debug).

---

## 8. Risks

### Risk 1: Skills не активируются автоматически
**Описание:** Claude Code может требовать явной регистрации skills или
поддерживать только skills из `~/.claude/plugins/`, игнорируя `.claude/skills/`.

**Митигация:** Frontmatter формат (name + description) идентичен плагинным
skills (проверено на `brainstorming/SKILL.md`). При необходимости — явный
вызов через slash command как fallback. Добавить запись в `.claude/commands/`
как wrapper.

**Severity:** Medium — skill можно вызвать вручную `/liquibase-migration`
даже если автоактивация не работает.

### Risk 2: Конфликт с Superpowers plugin
**Описание:** Superpowers `brainstorming` или `writing-plans` может
попытаться перехватить задачи по созданию миграций как «creative work».

**Митигация:** Описание liquibase-migration skill специфично к Liquibase
XML — не пересекается с brainstorming trigger (creative work). Суперпаверс
активируется на «новая фича», не на «changeset XML».

**Severity:** Low.

### Risk 3: Устаревание skill при изменении конвенций
**Описание:** Если изменится формат changeset ID или мастер-файл переедет,
skill даст неверные инструкции.

**Митигация:** В skill явно указано ссылаться на `backend/CLAUDE.md`
секцию Liquibase как источник истины. При Quarterly review (Sub-project E)
skills включить в список проверки.

**Severity:** Low — skills простые файлы, легко обновить.

### Risk 4: Over-engineering — skills не дают value
**Описание:** Claude уже может создавать миграции без skill по CLAUDE.md.

**Митигация:** CLAUDE.md содержит только краткую выжимку. Skill содержит
полный template, примеры реальных файлов, step-by-step с конкретными
командами `ls | sort -V | tail -3`. Это снижает вероятность ошибок
формата ID при работе без свежего контекста.

**Severity:** Low.
