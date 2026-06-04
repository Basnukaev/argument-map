# liquibase-migration Skill — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Создать `.claude/skills/liquibase-migration/SKILL.md` — project-specific
skill для надёжного создания Liquibase changeset'ов с правильным форматом ID,
регистрацией в мастер-файле, CDATA-escape и rollback секцией.

**Architecture:** Один файл SKILL.md с YAML frontmatter (name + description
для автоактивации) + Markdown инструкции со step-by-step процедурой,
XML template и двумя реальными примерами из проекта. Skill живёт в
`.claude/skills/liquibase-migration/` — зеркалит структуру плагинов
Superpowers но на уровне проекта.

**Tech Stack:** Markdown, YAML frontmatter, XML (Liquibase DSL), bash команды.

---

### Task 1: Создать директорию и SKILL.md

**Files:**
- Create: `.claude/skills/liquibase-migration/SKILL.md`

- [ ] **Step 1: Убедиться что `.claude/skills/` директория существует**

```bash
ls /home/basnukaev/projects/argument-map/.claude/skills 2>/dev/null || echo "needs creation"
```

Если директория не существует — создать:
```bash
mkdir -p /home/basnukaev/projects/argument-map/.claude/skills/liquibase-migration
```

- [ ] **Step 2: Определить текущий максимальный номер миграции**

```bash
ls /home/basnukaev/projects/argument-map/backend/src/main/resources/db/changelog/changes/ | sort -V | tail -3
```

Ожидаемый вывод (на момент написания плана):
```
20260519-46-refresh-tokens.xml
20260519-47-authorities-type.xml
20260519-48-edge-z-index.xml
```

Следующая миграция — номер 49.

- [ ] **Step 3: Создать SKILL.md**

Содержимое описано детально в Task 2. Файл создаётся через Write tool.

- [ ] **Step 4: Commit**

```bash
git add .claude/skills/liquibase-migration/SKILL.md
git commit -m "feat(.claude): liquibase-migration skill"
```

---

### Task 2: Содержимое SKILL.md (вербатим)

**Files:**
- Create: `.claude/skills/liquibase-migration/SKILL.md`

Skill должен содержать следующие секции:

**Frontmatter:**
```yaml
---
name: liquibase-migration
description: >
  Use when creating a new Liquibase database migration in this project.
  Triggers on: creating migrations, adding/dropping columns, creating/dropping tables,
  adding indexes, modifying constraints, any change to db/changelog/. Keywords:
  миграция, changeset, addColumn, createTable, Liquibase, db/changelog, schema change,
  ALTER TABLE, CREATE TABLE, новая колонка. Always use this skill to avoid ID format
  errors and ensure master file registration and rollback sections are not forgotten.
---
```

**Тело skill (все секции):**

1. **Обзор** (1-2 параграфа): что умеет skill, зачем нужен
2. **Step 1: Determine next number** — команда для определения NN
3. **Step 2: Format ID** — правило `YYYYMMDD-NN-short-description`
4. **Step 3: Create file** — XML template с placeholders
5. **Step 4: Register in master** — как добавить `<include>` в конец
   db.changelog-master.xml
6. **CDATA rule** — когда и как экранировать `&`
7. **Rollback rules** — когда rollback обязателен, когда можно пропустить
8. **Index rule** — когда добавлять index в ту же миграцию
9. **Examples** — 2 реальных примера из проекта (addColumn + createTable)
10. **Checklist** — 5 пунктов перед коммитом

---

### Task 3: Manual test plan

Цель — убедиться что skill даёт корректные инструкции при реальной задаче.

**Test scenario:** После создания skill дать Claude задачу:
> «Добавь колонку `status VARCHAR(50) DEFAULT 'draft'` в таблицу `topics`»

**Ожидаемое поведение:**

- [ ] Claude активирует liquibase-migration skill (видно из сообщения или
  системного лога `available_skills`)
- [ ] Claude выполняет `ls changes/ | sort -V | tail -3` для определения номера
- [ ] Claude создаёт файл с ID `20260519-49-add-status-to-topics` (или
  следующий актуальный номер + дата)
- [ ] changeSet содержит `author="Abdula Basnukaev"`
- [ ] changeSet содержит `<rollback>` секцию с `<dropColumn>`
- [ ] Claude добавляет `<include>` в db.changelog-master.xml в конец списка
- [ ] Нет незаэкранированных `&` в XML

**Если skill не активируется автоматически:**
- Попробовать явный вызов: скопировать задачу с упоминанием «liquibase» / «миграция»
- Как fallback: добавить `.claude/commands/liquibase-migration.md` — slash command
  wrapper который читает SKILL.md

**Ожидаемый итог теста:** все 7 чек-пунктов выполнены.

---

### Итоговые файлы

| Файл | Действие |
|------|---------|
| `docs/specs/2026-05-19-project-skills-design.md` | Создан (Step A) |
| `docs/plans/2026-05-19-project-skills-plan.md` | Создан (Step B, этот файл) |
| `.claude/skills/liquibase-migration/SKILL.md` | Создать (Step C) |

---

### Что в backlog (не в этом плане)

3 remaining skills — отдельные задачи для следующих сессий:

1. **new-rest-endpoint** — scaffold chain DTO+Controller+Service+IT+api-contract.md
2. **library-page-rendering** — PDF/OCR/Image modes, lib_pages state machine
3. **shamela-parser-debug** — ETL diagnostic playbook

Приоритет: new-rest-endpoint > library-page-rendering > shamela-parser-debug
(по частоте использования).

Для каждого: аналогичный single-file SKILL.md в `.claude/skills/<name>/`.
