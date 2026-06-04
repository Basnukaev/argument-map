---
name: liquibase-migration
description: >
  Use when creating a new Liquibase database migration in this project.
  Triggers on: creating migrations, adding/dropping columns, creating/dropping tables,
  adding indexes, modifying constraints, any change to db/changelog/. Keywords:
  миграция, changeset, addColumn, createTable, Liquibase, db/changelog, schema change,
  ALTER TABLE, CREATE TABLE, новая колонка, добавить поле, изменить схему. Always use
  this skill to avoid ID format errors and ensure master file registration and rollback
  sections are not forgotten. Use this even if the request seems simple — the ID format
  and master registration are error-prone without this checklist.
---

# Liquibase Migration — Создание нового changeset'а

Этот skill обеспечивает корректный формат ID, регистрацию в мастер-файле,
CDATA-escape и rollback секцию. Все эти детали легко пропустить без явного
checklist'а — именно поэтому skill существует.

---

## Step 1: Определить следующий номер миграции

```bash
ls backend/src/main/resources/db/changelog/changes/ | sort -V | tail -3
```

Вывод покажет последние 3 файла. Взять номер NN из имени последнего файла,
увеличить на 1. Например, если последний файл `20260519-48-edge-z-index.xml`
— следующий номер `49`.

Дата (`YYYYMMDD`) — сегодняшняя дата в UTC.

---

## Step 2: Сформировать ID changeset'а

**Формат:** `YYYYMMDD-NN-short-description-kebab-case`

Правила:
- `YYYYMMDD` — сегодняшняя дата (например `20260519`)
- `NN` — двузначный порядковый номер (с ведущим нулём до 10: `01`, `02`, ...,
  `09`, `10`, `11`, ...)
- `short-description` — 2-4 слова kebab-case, кратко описывает изменение
  (например `add-status-to-topics`, `create-votes-table`, `drop-weight-from-nodes`)

**Примеры корректных ID:**
```
20260519-49-add-status-to-topics
20260520-50-create-tags-table
20260521-51-add-idx-pages-book
```

**Имя файла** = `{changeset-id}.xml` в директории
`backend/src/main/resources/db/changelog/changes/`

---

## Step 3: Создать файл миграции

Использовать этот template (подставить свои значения вместо `{...}`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                            https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <!--
        {Описание на русском: зачем эта миграция, какую проблему решает.
        Если связана с backlog/roadmap — сослаться.
        Если есть важное проектное решение (семантика поля, выбор типа) — объяснить здесь.}
    -->
    <changeSet id="{YYYYMMDD-NN-short-description}" author="Abdula Basnukaev">
        <comment>{Краткий человекочитаемый комментарий на русском}</comment>

        {/* основное изменение — addColumn / createTable / createIndex / etc. */}

        <rollback>
            {/* обратная операция */}
        </rollback>
    </changeSet>

</databaseChangeLog>
```

**Author всегда:** `Abdula Basnukaev` — без исключений.

---

## Step 4: Зарегистрировать в db.changelog-master.xml

Открыть файл `backend/src/main/resources/db/changelog/db.changelog-master.xml`.

Добавить строку **в конец** списка `<include>`, непосредственно перед
закрывающим тегом `</databaseChangeLog>`:

```xml
    <include file="db/changelog/changes/{YYYYMMDD-NN-short-description}.xml"/>
```

Порядок `<include>` записей = порядок применения миграций. Новая миграция
всегда последняя.

---

## CDATA rule — когда экранировать

**Проблема:** символы `&`, `<`, `>` в XML тексте ломают Liquibase парсинг.
Это особенно касается `<comment>` тегов и `<sql>` блоков.

**Правило:** если в тексте (comment, sql, value) встречается `&` — либо
заменить на `&amp;`, либо обернуть весь блок в `<![CDATA[ ... ]]>`.

```xml
<!-- BAD — сломает парсинг -->
<comment>Добавляем поле для Q&A модуля</comment>

<!-- GOOD вариант 1 — entity escape -->
<comment>Добавляем поле для Q&amp;A модуля</comment>

<!-- GOOD вариант 2 — CDATA wrap -->
<comment><![CDATA[Добавляем поле для Q&A модуля]]></comment>
```

Для `<sql>` блоков с условиями или конкатенацией CDATA особенно полезен:

```xml
<sql><![CDATA[
    UPDATE topics SET status = 'draft'
    WHERE created_at < now() AND status IS NULL;
]]></sql>
```

---

## Rollback rules — когда обязателен

**Rollback обязателен** для:
- `addColumn` → `dropColumn`
- `createTable` → `dropTable`
- `createIndex` → `dropIndex`
- `addForeignKeyConstraint` → `dropForeignKeyConstraint`

**Rollback можно пропустить** для:
- `sql` блоков с data migration (если данные нельзя обратно восстановить)
- Необратимых операций (обоснование в comment)

**Паттерн addColumn + createIndex в одном changeSet:**

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

Rollback порядок: **обратный порядку** основных операций (сначала drop index,
потом drop column).

---

## Index rule — когда добавлять индекс в ту же миграцию

Добавлять `<createIndex>` **в ту же миграцию**, если:
- Колонка будет использоваться как JOIN или WHERE предикат
- Создаётся FK-колонка (обычно нужен index на referencing сторону)
- Создаётся уникальная колонка (`unique=true` в `<constraints>`)

Пример composite index для topic + column:
```xml
<createIndex tableName="nodes" indexName="idx_nodes_topic_z_index">
    <column name="topic_id"/>
    <column name="z_index"/>
</createIndex>
```

---

## Примеры из проекта

### Пример 1: addColumn с index (миграция 48)

Файл: `20260519-48-edge-z-index.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                            https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <!--
        Z-index persistence для рёбер (backlog tech debt #1).
        До этой миграции "bring to front" / "send to back" для рёбер
        работали только в локальном RF-state и сбрасывались при refetch.

        Семантика: z_index 0 - default, положительный - выше, отрицательный
        - ниже.
    -->
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

</databaseChangeLog>
```

### Пример 2: createTable с FK + rollback (миграция 46)

Файл: `20260519-46-refresh-tokens.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                            https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <!--
        ADR-047: refresh token rotation (single-use refresh).
        token_hash — SHA-256 hex, не raw value (БД-leak не даёт attacker'у
        живые токены).
    -->
    <changeSet id="20260519-46-refresh-tokens" author="Abdula Basnukaev">
        <comment>refresh_tokens для single-use rotation + steal detection</comment>
        <sql><![CDATA[
            CREATE TABLE refresh_tokens (
                id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                token_hash varchar(64) NOT NULL UNIQUE,
                issued_at timestamptz NOT NULL DEFAULT now(),
                expires_at timestamptz NOT NULL,
                revoked_at timestamptz NULL,
                replaced_by uuid NULL REFERENCES refresh_tokens(id),
                revocation_reason varchar(50) NULL
            );

            CREATE INDEX idx_refresh_tokens_user
                ON refresh_tokens(user_id)
                WHERE revoked_at IS NULL;

            CREATE INDEX idx_refresh_tokens_hash
                ON refresh_tokens(token_hash);

            CREATE INDEX idx_refresh_tokens_expires
                ON refresh_tokens(expires_at)
                WHERE revoked_at IS NULL;
        ]]></sql>
        <rollback>
            <sql>DROP TABLE IF EXISTS refresh_tokens;</sql>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

---

## Checklist перед коммитом

Перед `git add` проверить:

- [ ] ID format корректен: `YYYYMMDD-NN-short-description` (дата сегодняшняя, NN правильный)
- [ ] `author="Abdula Basnukaev"` присутствует
- [ ] `<rollback>` секция есть (или явное обоснование в comment почему пропущена)
- [ ] Файл добавлен в `db.changelog-master.xml` последней строкой в списке `<include>`
- [ ] Нет незаэкранированных `&`, `<`, `>` в XML тексте (comment/sql/value блоках)

---

## Частые ошибки

| Ошибка | Симптом | Решение |
|--------|---------|---------|
| Неверный порядок номера | Миграция применяется не вовремя или конфликтует | `ls changes/ \| sort -V \| tail -3` перед созданием |
| Забыт `<include>` в master | Миграция существует, но Liquibase её не видит | Всегда последнее действие после создания файла |
| `&` в comment без escape | `SAXParseException: The entity "&" was referenced, but not declared` | `&amp;` или CDATA wrap |
| Rollback в неверном порядке | `LiquibaseException` при rollback | Порядок операций в rollback — обратный основным |
| author отличается от конвенции | Inconsistency в changelog истории | Всегда `Abdula Basnukaev` |
