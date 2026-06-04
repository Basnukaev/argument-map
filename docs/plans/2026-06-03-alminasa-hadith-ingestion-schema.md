# alminasa Hadith Ingestion — Plan 1: DB Schema + Domain Model + Repositories

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the hadith domain schema + Java model + JDBC repositories so they can hold the rich, structured data alminasa.ai provides (hadith type, narrator external IDs + tabaqa + verbatim grade, takhrij cross-refs, imported rulings, sharḥ/ʿilal/gharīb explanations, print editions, narrator network). This is the foundation that the crawler (Plan 2) and mapper (Plan 3) write into.

**Architecture:** Additive only — extend `hd_hadiths`/`hd_narrators` with new columns (UNIQUE on `external_source,external_id` for idempotent upsert), add five new child tables, mirror the existing JDBC repository pattern (manual `RowMapper`, `?::jsonb` casts, no JPA). Existing seeded data and the still-present sunnah ETL keep compiling via backward-compat secondary constructors (the precedent set by `Collection`).

**Tech Stack:** Java 21 records, Spring Boot 3.5 JDBC Template (no JPA/Hibernate), PostgreSQL 16, Liquibase, Testcontainers (no H2).

**Spec:** `docs/specs/2026-06-03-alminasa-hadith-source-design.md` (§B data model).

**Migration numbering:** last applied migration is `20260602-69-...`; this plan adds **70** and **71**. Use the `liquibase-migration` skill when creating each migration file (ID format + master registration + rollback are error-prone).

**Conventions reminder:** Liquibase author always `Abdula Basnukaev`; comments/JavaDoc in Russian, identifiers in English; IT classes named `*IT`, run on Testcontainers Postgres; escape `&` in XML.

---

## File Structure

**Created:**
- `backend/src/main/resources/db/changelog/changes/20260603-70-hd-alminasa-columns.xml` — new columns on `hd_hadiths` + `hd_narrators` + UNIQUE constraints.
- `backend/src/main/resources/db/changelog/changes/20260603-71-hd-alminasa-tables.xml` — five new tables.
- `backend/src/main/java/ru/basnukaev/argumentmap/hadith/domain/HadithEdition.java`
- `.../hadith/domain/HadithRuling.java`
- `.../hadith/domain/HadithExplanation.java`
- `.../hadith/domain/HadithCrossref.java`
- `.../hadith/domain/NarratorRelation.java`
- `.../hadith/repository/HadithEditionRepository.java`
- `.../hadith/repository/HadithRulingRepository.java`
- `.../hadith/repository/HadithExplanationRepository.java`
- `.../hadith/repository/HadithCrossrefRepository.java`
- `.../hadith/repository/NarratorRelationRepository.java`
- `backend/src/test/java/ru/basnukaev/argumentmap/hadith/repository/AlminasaSchemaRepositoryIT.java` — round-trip ITs for new + extended repos.

**Modified:**
- `backend/src/main/resources/db/changelog/db.changelog-master.xml` — register the two migrations.
- `.../hadith/domain/Hadith.java` — +6 fields + backward-compat constructor.
- `.../hadith/domain/Narrator.java` — +6 fields + backward-compat constructor.
- `.../hadith/repository/HadithRepository.java` — COLUMNS/RowMapper/save + `findByExternalId` + `upsertByExternalId`.
- `.../hadith/repository/NarratorRepository.java` — COLUMNS/RowMapper/save + `findByExternalId`.

---

## Task 1: Migration 70 — new columns on hd_hadiths + hd_narrators

**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/20260603-70-hd-alminasa-columns.xml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`

- [ ] **Step 1: Create the migration file**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                            https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <!--
        Сессия 56: разворот источника хадисов на alminasa.ai (единственный
        источник). Спека docs/specs/2026-06-03-alminasa-hadith-source-design.md.

        Новые колонки расширяют существующие hd_hadiths / hd_narrators под
        структурные данные alminasa:
          - external_source/external_id — природный ключ источника
            (hadith_id "146-1", narrator id "5719"). UNIQUE → идемпотентный
            upsert вместо наивного fuzzy-матчинга имён.
          - hadith_type — тип (марфу'/маукуф/...), которого не было в модели.
          - chapter_ar/sub_chapter_ar — структура свода (денормализованные ar-метки).
          - full_text_ar — полный матн+иснад с inline-разметкой рави
            (<a class=rawy id=N>) для кликабельного иснада в UI.
          - narrators: tabaqa (level), grade_text (джарх-та'диль дословно),
            born_on_text/died_on_text (проза). enum reliability_grade остаётся
            грубой производной для фильтров.

        UNIQUE(external_source, external_id) допускает множественные NULL
        (Postgres) — существующие seed-строки без external_* не нарушают его.
    -->
    <changeSet id="20260603-70-hd-alminasa-columns" author="Abdula Basnukaev">
        <comment>Сессия 56: колонки alminasa на hd_hadiths/hd_narrators + UNIQUE external_id</comment>
        <sql><![CDATA[
            ALTER TABLE hd_hadiths
                ADD COLUMN external_source varchar(30),
                ADD COLUMN external_id     varchar(40),
                ADD COLUMN hadith_type     varchar(40),
                ADD COLUMN chapter_ar      text,
                ADD COLUMN sub_chapter_ar  text,
                ADD COLUMN full_text_ar    text;
            ALTER TABLE hd_hadiths
                ADD CONSTRAINT uq_hd_hadiths_external UNIQUE (external_source, external_id);

            ALTER TABLE hd_narrators
                ADD COLUMN external_source varchar(30),
                ADD COLUMN external_id     varchar(40),
                ADD COLUMN tabaqa          varchar(120),
                ADD COLUMN grade_text      text,
                ADD COLUMN born_on_text    text,
                ADD COLUMN died_on_text    text;
            ALTER TABLE hd_narrators
                ADD CONSTRAINT uq_hd_narrators_external UNIQUE (external_source, external_id);
        ]]></sql>
        <rollback><![CDATA[
            ALTER TABLE hd_narrators DROP CONSTRAINT uq_hd_narrators_external;
            ALTER TABLE hd_narrators
                DROP COLUMN external_source, DROP COLUMN external_id,
                DROP COLUMN tabaqa, DROP COLUMN grade_text,
                DROP COLUMN born_on_text, DROP COLUMN died_on_text;
            ALTER TABLE hd_hadiths DROP CONSTRAINT uq_hd_hadiths_external;
            ALTER TABLE hd_hadiths
                DROP COLUMN external_source, DROP COLUMN external_id,
                DROP COLUMN hadith_type, DROP COLUMN chapter_ar,
                DROP COLUMN sub_chapter_ar, DROP COLUMN full_text_ar;
        ]]></rollback>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 2: Register in master changelog**

Add this line in `db.changelog-master.xml` immediately after the `...69-lib-books-content-kind.xml` include (line 76):

```xml
    <include file="db/changelog/changes/20260603-70-hd-alminasa-columns.xml"/>
```

- [ ] **Step 3: Verify the migration applies**

Run: `cd backend && ./mvnw -q -Dtest=AlminasaSchemaRepositoryIT test` — will not exist yet; instead at this step run a compile + a throwaway context check:
Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS (XML well-formed; full apply is verified by the IT in Task 8).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/changelog/changes/20260603-70-hd-alminasa-columns.xml \
        backend/src/main/resources/db/changelog/db.changelog-master.xml
git commit -m "feat(hadith): миграция 70 — колонки alminasa на hd_hadiths/hd_narrators (Сессия 56)"
```

---

## Task 2: Migration 71 — five new child tables

**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/20260603-71-hd-alminasa-tables.xml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`

- [ ] **Step 1: Create the migration file**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                            https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <!--
        Сессия 56: новые дочерние таблицы под данные alminasa.
          hd_hadith_editions   — несколько печатных изданий (edition/page/volume).
          hd_rulings           — импортированные вердикты учёных (свободный ruler +
                                 год смерти). Ортогонально hadith_grades (ручные
                                 оценки юзеров через authorities).
          hd_explanations      — شروح/علل/غريب (kind), может быть большой текст.
          hd_hadith_crossrefs  — такхридж/طرق (raw_narrations): связь хадиса с
                                 сиблинг-преданиями по external_id (+ резолв в FK).
          hd_narrator_relations— сеть передатчиков (top_students/top_scholars):
                                 имя+частота, опциональный резолв в narrator FK.
        Все ON DELETE CASCADE от родителя (хадис/рави). Индексы по FK сразу.
    -->
    <changeSet id="20260603-71-hd-alminasa-tables" author="Abdula Basnukaev">
        <comment>Сессия 56: hd_hadith_editions / hd_rulings / hd_explanations / hd_hadith_crossrefs / hd_narrator_relations</comment>
        <sql><![CDATA[
            CREATE TABLE hd_hadith_editions (
                id           uuid PRIMARY KEY,
                hadith_id    uuid NOT NULL REFERENCES hd_hadiths(id) ON DELETE CASCADE,
                edition_name text,
                page         integer,
                volume       integer
            );
            CREATE INDEX idx_hd_editions_hadith ON hd_hadith_editions(hadith_id);

            CREATE TABLE hd_rulings (
                id               uuid PRIMARY KEY,
                hadith_id        uuid NOT NULL REFERENCES hd_hadiths(id) ON DELETE CASCADE,
                ruler_name       text,
                ruler_death_year integer,
                ruling_text      text,
                book_name        text,
                page             integer,
                volume           integer,
                metadata         jsonb,
                created_at       timestamptz NOT NULL DEFAULT now()
            );
            CREATE INDEX idx_hd_rulings_hadith ON hd_rulings(hadith_id);

            CREATE TABLE hd_explanations (
                id                uuid PRIMARY KEY,
                hadith_id         uuid NOT NULL REFERENCES hd_hadiths(id) ON DELETE CASCADE,
                kind              varchar(20) NOT NULL CHECK (kind IN ('SHARH','ILAL','GHARIB')),
                book_name         text,
                author            text,
                author_death_year integer,
                page              integer,
                volume            integer,
                text              text,
                metadata          jsonb,
                created_at        timestamptz NOT NULL DEFAULT now()
            );
            CREATE INDEX idx_hd_explanations_hadith ON hd_explanations(hadith_id);

            CREATE TABLE hd_hadith_crossrefs (
                id                  uuid PRIMARY KEY,
                hadith_id           uuid NOT NULL REFERENCES hd_hadiths(id) ON DELETE CASCADE,
                related_external_id varchar(40) NOT NULL,
                related_hadith_id   uuid REFERENCES hd_hadiths(id) ON DELETE SET NULL,
                relation_type       varchar(30),
                note                text,
                created_at          timestamptz NOT NULL DEFAULT now()
            );
            CREATE INDEX idx_hd_crossrefs_hadith ON hd_hadith_crossrefs(hadith_id);
            CREATE INDEX idx_hd_crossrefs_related ON hd_hadith_crossrefs(related_hadith_id);

            CREATE TABLE hd_narrator_relations (
                id                  uuid PRIMARY KEY,
                narrator_id         uuid NOT NULL REFERENCES hd_narrators(id) ON DELETE CASCADE,
                related_narrator_id uuid REFERENCES hd_narrators(id) ON DELETE SET NULL,
                related_name        text,
                role                varchar(20) NOT NULL CHECK (role IN ('STUDENT','SCHOLAR')),
                cnt                 integer,
                created_at          timestamptz NOT NULL DEFAULT now()
            );
            CREATE INDEX idx_hd_nrel_narrator ON hd_narrator_relations(narrator_id);
        ]]></sql>
        <rollback><![CDATA[
            DROP TABLE hd_narrator_relations;
            DROP TABLE hd_hadith_crossrefs;
            DROP TABLE hd_explanations;
            DROP TABLE hd_rulings;
            DROP TABLE hd_hadith_editions;
        ]]></rollback>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 2: Register in master changelog** (immediately after the 70 include)

```xml
    <include file="db/changelog/changes/20260603-71-hd-alminasa-tables.xml"/>
```

- [ ] **Step 3: Verify compile**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/changelog/changes/20260603-71-hd-alminasa-tables.xml \
        backend/src/main/resources/db/changelog/db.changelog-master.xml
git commit -m "feat(hadith): миграция 71 — таблицы editions/rulings/explanations/crossrefs/narrator-relations (Сессия 56)"
```

---

## Task 3: Extend Hadith domain record

**Files:**
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/hadith/domain/Hadith.java`

- [ ] **Step 1: Replace the record with the extended version + backward-compat constructor**

The current record has 8 components. Append the 6 new ones AT THE END and add an 8-arg secondary constructor (externals null) so existing call-sites (`SunnahToHadithMapper`, `DevHadithSeeder`, IT fixtures) keep compiling — exactly the pattern `Collection` uses for `bookId`.

```java
public record Hadith(
        UUID id,
        UUID collectionId,
        Integer primaryNumber,
        String normalizedMatn,
        String status,
        UUID sourceId,
        String metadata,
        Instant createdAt,
        String externalSource,
        String externalId,
        String hadithType,
        String chapterAr,
        String subChapterAr,
        String fullTextAr
) {
    /**
     * Backward-compat конструктор без alminasa-полей (8 аргументов) для
     * существующих call-site'ов (sunnah-маппер, seeder, IT-фикстуры) — пока
     * legacy не удалён (Plan 2). alminasa-импортёр использует полный конструктор.
     */
    public Hadith(
            UUID id, UUID collectionId, Integer primaryNumber, String normalizedMatn,
            String status, UUID sourceId, String metadata, Instant createdAt
    ) {
        this(id, collectionId, primaryNumber, normalizedMatn, status, sourceId,
                metadata, createdAt, null, null, null, null, null, null);
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS (the `HadithRepository.ROW_MAPPER` still constructs with 8 args via the secondary constructor — it gets updated in Task 4; compile stays green meanwhile).

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/hadith/domain/Hadith.java
git commit -m "feat(hadith): Hadith record +alminasa поля (external/type/chapter/fullText) (Сессия 56)"
```

---

## Task 4: Extend HadithRepository (read/write new columns + external-id upsert)

**Files:**
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/hadith/repository/HadithRepository.java`

- [ ] **Step 1: Update COLUMNS constant** to include the new columns (append in the same order as the record):

```java
    private static final String COLUMNS =
            "id, collection_id, primary_number, normalized_matn, status, "
                    + "source_id, metadata, created_at, "
                    + "external_source, external_id, hadith_type, "
                    + "chapter_ar, sub_chapter_ar, full_text_ar";
```

- [ ] **Step 2: Update ROW_MAPPER** to read the new columns:

```java
    private static final RowMapper<Hadith> ROW_MAPPER = (rs, rn) -> new Hadith(
            rs.getObject("id", UUID.class),
            rs.getObject("collection_id", UUID.class),
            (Integer) rs.getObject("primary_number"),
            rs.getString("normalized_matn"),
            rs.getString("status"),
            rs.getObject("source_id", UUID.class),
            rs.getString("metadata"),
            instant(rs, "created_at"),
            rs.getString("external_source"),
            rs.getString("external_id"),
            rs.getString("hadith_type"),
            rs.getString("chapter_ar"),
            rs.getString("sub_chapter_ar"),
            rs.getString("full_text_ar")
    );
```

- [ ] **Step 3: Update `save` to insert the new columns**

```java
    public Hadith save(Hadith h) {
        jdbcTemplate.update(
                "INSERT INTO hd_hadiths (" + COLUMNS + ") VALUES "
                        + "(?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)",
                h.id(), h.collectionId(), h.primaryNumber(), h.normalizedMatn(),
                h.status(), h.sourceId(), h.metadata(), odt(h.createdAt()),
                h.externalSource(), h.externalId(), h.hadithType(),
                h.chapterAr(), h.subChapterAr(), h.fullTextAr()
        );
        return h;
    }
```

- [ ] **Step 4: Add `findByExternalId` (idempotency lookup)** — place near `findByCollectionIdAndPrimaryNumber`:

```java
    /** Поиск по природному ключу источника (alminasa hadith_id) для идемпотентного импорта. */
    public Optional<Hadith> findByExternalId(String externalSource, String externalId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_hadiths "
                        + "WHERE external_source = ? AND external_id = ?",
                ROW_MAPPER, externalSource, externalId
        ).stream().findFirst();
    }
```

- [ ] **Step 5: Verify compile**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/hadith/repository/HadithRepository.java
git commit -m "feat(hadith): HadithRepository читает/пишет alminasa-колонки + findByExternalId (Сессия 56)"
```

---

## Task 5: Extend Narrator domain record

**Files:**
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/hadith/domain/Narrator.java`

- [ ] **Step 1: Replace the record with the extended version + backward-compat constructor** (append 6 fields; keep the 16-arg constructor used by `IsnadPersistenceService.createNarrator` and `DevHadithSeeder`):

```java
public record Narrator(
        UUID id,
        UUID authorityId,
        String nameAr,
        String nameArNormalized,
        String kunya,
        String laqab,
        Integer yearBirthHijri,
        Integer yearDeathHijri,
        String birthplace,
        String deathPlace,
        String primaryResidence,
        String reliabilityGrade,
        String reliabilityComment,
        int transmittedCountCached,
        String metadata,
        Instant createdAt,
        String externalSource,
        String externalId,
        String tabaqa,
        String gradeText,
        String bornOnText,
        String diedOnText
) {
    /**
     * Backward-compat конструктор без alminasa-полей (16 аргументов) для
     * существующих call-site'ов (IsnadPersistenceService, DevHadithSeeder,
     * IT-фикстуры). alminasa-импортёр использует полный конструктор.
     */
    public Narrator(
            UUID id, UUID authorityId, String nameAr, String nameArNormalized,
            String kunya, String laqab, Integer yearBirthHijri, Integer yearDeathHijri,
            String birthplace, String deathPlace, String primaryResidence,
            String reliabilityGrade, String reliabilityComment, int transmittedCountCached,
            String metadata, Instant createdAt
    ) {
        this(id, authorityId, nameAr, nameArNormalized, kunya, laqab,
                yearBirthHijri, yearDeathHijri, birthplace, deathPlace, primaryResidence,
                reliabilityGrade, reliabilityComment, transmittedCountCached, metadata,
                createdAt, null, null, null, null, null, null);
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/hadith/domain/Narrator.java
git commit -m "feat(hadith): Narrator record +alminasa поля (external/tabaqa/gradeText/born/died) (Сессия 56)"
```

---

## Task 6: Extend NarratorRepository

**Files:**
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/hadith/repository/NarratorRepository.java`

- [ ] **Step 1: Update COLUMNS**

```java
    private static final String COLUMNS =
            "id, authority_id, name_ar, name_ar_normalized, kunya, laqab, "
                    + "year_birth_hijri, year_death_hijri, birthplace, death_place, "
                    + "primary_residence, reliability_grade, reliability_comment, "
                    + "transmitted_count_cached, metadata, created_at, "
                    + "external_source, external_id, tabaqa, grade_text, "
                    + "born_on_text, died_on_text";
```

- [ ] **Step 2: Update ROW_MAPPER**

```java
    private static final RowMapper<Narrator> ROW_MAPPER = (rs, rn) -> new Narrator(
            rs.getObject("id", UUID.class),
            rs.getObject("authority_id", UUID.class),
            rs.getString("name_ar"),
            rs.getString("name_ar_normalized"),
            rs.getString("kunya"),
            rs.getString("laqab"),
            (Integer) rs.getObject("year_birth_hijri"),
            (Integer) rs.getObject("year_death_hijri"),
            rs.getString("birthplace"),
            rs.getString("death_place"),
            rs.getString("primary_residence"),
            rs.getString("reliability_grade"),
            rs.getString("reliability_comment"),
            rs.getInt("transmitted_count_cached"),
            rs.getString("metadata"),
            instant(rs, "created_at"),
            rs.getString("external_source"),
            rs.getString("external_id"),
            rs.getString("tabaqa"),
            rs.getString("grade_text"),
            rs.getString("born_on_text"),
            rs.getString("died_on_text")
    );
```

- [ ] **Step 3: Update `save`**

```java
    public Narrator save(Narrator n) {
        jdbcTemplate.update(
                "INSERT INTO hd_narrators (" + COLUMNS + ") VALUES ("
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, "
                        + "?, ?, ?, ?, ?, ?)",
                n.id(), n.authorityId(), n.nameAr(), n.nameArNormalized(),
                n.kunya(), n.laqab(), n.yearBirthHijri(), n.yearDeathHijri(),
                n.birthplace(), n.deathPlace(), n.primaryResidence(),
                n.reliabilityGrade(), n.reliabilityComment(),
                n.transmittedCountCached(), n.metadata(), odt(n.createdAt()),
                n.externalSource(), n.externalId(), n.tabaqa(), n.gradeText(),
                n.bornOnText(), n.diedOnText()
        );
        return n;
    }
```

- [ ] **Step 4: Add `findByExternalId`** (place near `findByNameArNormalized`):

```java
    /** Точный дедуп рави по природному ключу источника (alminasa narrator id). */
    public Optional<Narrator> findByExternalId(String externalSource, String externalId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_narrators "
                        + "WHERE external_source = ? AND external_id = ?",
                ROW_MAPPER, externalSource, externalId
        ).stream().findFirst();
    }
```

- [ ] **Step 5: Verify compile**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/hadith/repository/NarratorRepository.java
git commit -m "feat(hadith): NarratorRepository читает/пишет alminasa-колонки + findByExternalId (Сессия 56)"
```

---

## Task 7: New domain records for the five child tables

**Files:**
- Create: `HadithEdition.java`, `HadithRuling.java`, `HadithExplanation.java`, `HadithCrossref.java`, `NarratorRelation.java` (all in `.../hadith/domain/`)

- [ ] **Step 1: Create `HadithEdition.java`**

```java
package ru.basnukaev.argumentmap.hadith.domain;

import java.util.UUID;

/** Печатное издание хадиса (alminasa editions[]): edition/page/volume. */
public record HadithEdition(
        UUID id,
        UUID hadithId,
        String editionName,
        Integer page,
        Integer volume
) {
}
```

- [ ] **Step 2: Create `HadithRuling.java`**

```java
package ru.basnukaev.argumentmap.hadith.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Импортированный вердикт учёного (alminasa rulings[]): свободный ruler +
 * год смерти. Ортогонально hadith_grades (ручные оценки юзеров через authorities).
 */
public record HadithRuling(
        UUID id,
        UUID hadithId,
        String rulerName,
        Integer rulerDeathYear,
        String rulingText,
        String bookName,
        Integer page,
        Integer volume,
        String metadata,
        Instant createdAt
) {
}
```

- [ ] **Step 3: Create `HadithExplanation.java`**

```java
package ru.basnukaev.argumentmap.hadith.domain;

import java.time.Instant;
import java.util.UUID;

/** Шарх/иляль/гариб (alminasa explanation/علل/غريب). kind ∈ {SHARH, ILAL, GHARIB}. */
public record HadithExplanation(
        UUID id,
        UUID hadithId,
        String kind,
        String bookName,
        String author,
        Integer authorDeathYear,
        Integer page,
        Integer volume,
        String text,
        String metadata,
        Instant createdAt
) {
}
```

- [ ] **Step 4: Create `HadithCrossref.java`**

```java
package ru.basnukaev.argumentmap.hadith.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Такхридж/طرق (alminasa raw_narrations): связь хадиса с сиблинг-преданием.
 * relatedHadithId — резолв relatedExternalId в наш FK когда сиблинг уже импортирован.
 */
public record HadithCrossref(
        UUID id,
        UUID hadithId,
        String relatedExternalId,
        UUID relatedHadithId,
        String relationType,
        String note,
        Instant createdAt
) {
}
```

- [ ] **Step 5: Create `NarratorRelation.java`**

```java
package ru.basnukaev.argumentmap.hadith.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Сеть передатчиков (alminasa top_students/top_scholars): имя + частота.
 * relatedNarratorId — резолв related_name в наш FK когда рави уже импортирован.
 * role ∈ {STUDENT, SCHOLAR}.
 */
public record NarratorRelation(
        UUID id,
        UUID narratorId,
        UUID relatedNarratorId,
        String relatedName,
        String role,
        Integer cnt,
        Instant createdAt
) {
}
```

- [ ] **Step 6: Verify compile + commit**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/hadith/domain/HadithEdition.java \
        backend/src/main/java/ru/basnukaev/argumentmap/hadith/domain/HadithRuling.java \
        backend/src/main/java/ru/basnukaev/argumentmap/hadith/domain/HadithExplanation.java \
        backend/src/main/java/ru/basnukaev/argumentmap/hadith/domain/HadithCrossref.java \
        backend/src/main/java/ru/basnukaev/argumentmap/hadith/domain/NarratorRelation.java
git commit -m "feat(hadith): доменные records editions/rulings/explanations/crossrefs/relations (Сессия 56)"
```

---

## Task 8: Repositories for the five child tables

**Files:**
- Create: `HadithEditionRepository.java`, `HadithRulingRepository.java`, `HadithExplanationRepository.java`, `HadithCrossrefRepository.java`, `NarratorRelationRepository.java` (all in `.../hadith/repository/`)

> Note: existing hadith repositories share helper methods `instant(rs, col)` (read `timestamptz` → `Instant`) and `odt(Instant)` (write `Instant` → `OffsetDateTime`). Inspect `HadithRepository.java` for their exact location/visibility; if they are `private static` there, replicate the same two helpers as `private static` in each new repository (they are ~3 lines each). Do not invent a new shared base class — the codebase keeps these inline per repository.

- [ ] **Step 1: Create `HadithEditionRepository.java`**

```java
package ru.basnukaev.argumentmap.hadith.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.domain.HadithEdition;

@Repository
public class HadithEditionRepository {

    private static final String COLUMNS = "id, hadith_id, edition_name, page, volume";

    private static final RowMapper<HadithEdition> ROW_MAPPER = (rs, rn) -> new HadithEdition(
            rs.getObject("id", UUID.class),
            rs.getObject("hadith_id", UUID.class),
            rs.getString("edition_name"),
            (Integer) rs.getObject("page"),
            (Integer) rs.getObject("volume")
    );

    private final JdbcTemplate jdbcTemplate;

    public HadithEditionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HadithEdition save(HadithEdition e) {
        jdbcTemplate.update(
                "INSERT INTO hd_hadith_editions (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?)",
                e.id(), e.hadithId(), e.editionName(), e.page(), e.volume()
        );
        return e;
    }

    public List<HadithEdition> findByHadithId(UUID hadithId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_hadith_editions WHERE hadith_id = ? "
                        + "ORDER BY volume NULLS LAST, page NULLS LAST",
                ROW_MAPPER, hadithId);
    }

    /** Идемпотентность импорта: пере-импорт хадиса пересоздаёт его издания. */
    public void deleteByHadithId(UUID hadithId) {
        jdbcTemplate.update("DELETE FROM hd_hadith_editions WHERE hadith_id = ?", hadithId);
    }
}
```

- [ ] **Step 2: Create `HadithRulingRepository.java`**

```java
package ru.basnukaev.argumentmap.hadith.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.domain.HadithRuling;

@Repository
public class HadithRulingRepository {

    private static final String COLUMNS =
            "id, hadith_id, ruler_name, ruler_death_year, ruling_text, "
                    + "book_name, page, volume, metadata, created_at";

    private static final RowMapper<HadithRuling> ROW_MAPPER = (rs, rn) -> new HadithRuling(
            rs.getObject("id", UUID.class),
            rs.getObject("hadith_id", UUID.class),
            rs.getString("ruler_name"),
            (Integer) rs.getObject("ruler_death_year"),
            rs.getString("ruling_text"),
            rs.getString("book_name"),
            (Integer) rs.getObject("page"),
            (Integer) rs.getObject("volume"),
            rs.getString("metadata"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public HadithRulingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HadithRuling save(HadithRuling r) {
        jdbcTemplate.update(
                "INSERT INTO hd_rulings (" + COLUMNS + ") VALUES "
                        + "(?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                r.id(), r.hadithId(), r.rulerName(), r.rulerDeathYear(), r.rulingText(),
                r.bookName(), r.page(), r.volume(), r.metadata(), odt(r.createdAt())
        );
        return r;
    }

    public List<HadithRuling> findByHadithId(UUID hadithId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_rulings WHERE hadith_id = ? "
                        + "ORDER BY ruler_death_year NULLS LAST, created_at ASC",
                ROW_MAPPER, hadithId);
    }

    public void deleteByHadithId(UUID hadithId) {
        jdbcTemplate.update("DELETE FROM hd_rulings WHERE hadith_id = ?", hadithId);
    }

    private static Instant instant(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant();
    }

    private static OffsetDateTime odt(Instant i) {
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }
}
```

- [ ] **Step 3: Create `HadithExplanationRepository.java`**

```java
package ru.basnukaev.argumentmap.hadith.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.domain.HadithExplanation;

@Repository
public class HadithExplanationRepository {

    private static final String COLUMNS =
            "id, hadith_id, kind, book_name, author, author_death_year, "
                    + "page, volume, text, metadata, created_at";

    private static final RowMapper<HadithExplanation> ROW_MAPPER = (rs, rn) -> new HadithExplanation(
            rs.getObject("id", UUID.class),
            rs.getObject("hadith_id", UUID.class),
            rs.getString("kind"),
            rs.getString("book_name"),
            rs.getString("author"),
            (Integer) rs.getObject("author_death_year"),
            (Integer) rs.getObject("page"),
            (Integer) rs.getObject("volume"),
            rs.getString("text"),
            rs.getString("metadata"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public HadithExplanationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HadithExplanation save(HadithExplanation e) {
        jdbcTemplate.update(
                "INSERT INTO hd_explanations (" + COLUMNS + ") VALUES "
                        + "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                e.id(), e.hadithId(), e.kind(), e.bookName(), e.author(),
                e.authorDeathYear(), e.page(), e.volume(), e.text(), e.metadata(),
                odt(e.createdAt())
        );
        return e;
    }

    public List<HadithExplanation> findByHadithId(UUID hadithId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_explanations WHERE hadith_id = ? "
                        + "ORDER BY kind, created_at ASC",
                ROW_MAPPER, hadithId);
    }

    public void deleteByHadithId(UUID hadithId) {
        jdbcTemplate.update("DELETE FROM hd_explanations WHERE hadith_id = ?", hadithId);
    }

    private static Instant instant(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant();
    }

    private static OffsetDateTime odt(Instant i) {
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }
}
```

- [ ] **Step 4: Create `HadithCrossrefRepository.java`**

```java
package ru.basnukaev.argumentmap.hadith.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.domain.HadithCrossref;

@Repository
public class HadithCrossrefRepository {

    private static final String COLUMNS =
            "id, hadith_id, related_external_id, related_hadith_id, "
                    + "relation_type, note, created_at";

    private static final RowMapper<HadithCrossref> ROW_MAPPER = (rs, rn) -> new HadithCrossref(
            rs.getObject("id", UUID.class),
            rs.getObject("hadith_id", UUID.class),
            rs.getString("related_external_id"),
            rs.getObject("related_hadith_id", UUID.class),
            rs.getString("relation_type"),
            rs.getString("note"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public HadithCrossrefRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HadithCrossref save(HadithCrossref c) {
        jdbcTemplate.update(
                "INSERT INTO hd_hadith_crossrefs (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                c.id(), c.hadithId(), c.relatedExternalId(), c.relatedHadithId(),
                c.relationType(), c.note(), odt(c.createdAt())
        );
        return c;
    }

    public List<HadithCrossref> findByHadithId(UUID hadithId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_hadith_crossrefs WHERE hadith_id = ? "
                        + "ORDER BY created_at ASC",
                ROW_MAPPER, hadithId);
    }

    public void deleteByHadithId(UUID hadithId) {
        jdbcTemplate.update("DELETE FROM hd_hadith_crossrefs WHERE hadith_id = ?", hadithId);
    }

    private static Instant instant(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant();
    }

    private static OffsetDateTime odt(Instant i) {
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }
}
```

- [ ] **Step 5: Create `NarratorRelationRepository.java`**

```java
package ru.basnukaev.argumentmap.hadith.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.domain.NarratorRelation;

@Repository
public class NarratorRelationRepository {

    private static final String COLUMNS =
            "id, narrator_id, related_narrator_id, related_name, role, cnt, created_at";

    private static final RowMapper<NarratorRelation> ROW_MAPPER = (rs, rn) -> new NarratorRelation(
            rs.getObject("id", UUID.class),
            rs.getObject("narrator_id", UUID.class),
            rs.getObject("related_narrator_id", UUID.class),
            rs.getString("related_name"),
            rs.getString("role"),
            (Integer) rs.getObject("cnt"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public NarratorRelationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public NarratorRelation save(NarratorRelation r) {
        jdbcTemplate.update(
                "INSERT INTO hd_narrator_relations (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                r.id(), r.narratorId(), r.relatedNarratorId(), r.relatedName(),
                r.role(), r.cnt(), odt(r.createdAt())
        );
        return r;
    }

    public List<NarratorRelation> findByNarratorId(UUID narratorId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_narrator_relations WHERE narrator_id = ? "
                        + "ORDER BY role, cnt DESC NULLS LAST",
                ROW_MAPPER, narratorId);
    }

    public void deleteByNarratorId(UUID narratorId) {
        jdbcTemplate.update("DELETE FROM hd_narrator_relations WHERE narrator_id = ?", narratorId);
    }

    private static Instant instant(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant();
    }

    private static OffsetDateTime odt(Instant i) {
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }
}
```

- [ ] **Step 6: Verify compile**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS. (Tests come in Task 9; remove the unused `Timestamp`/`Instant` imports from `HadithEditionRepository` since it has no `created_at` — keep only what compiles cleanly.)

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/hadith/repository/HadithEditionRepository.java \
        backend/src/main/java/ru/basnukaev/argumentmap/hadith/repository/HadithRulingRepository.java \
        backend/src/main/java/ru/basnukaev/argumentmap/hadith/repository/HadithExplanationRepository.java \
        backend/src/main/java/ru/basnukaev/argumentmap/hadith/repository/HadithCrossrefRepository.java \
        backend/src/main/java/ru/basnukaev/argumentmap/hadith/repository/NarratorRelationRepository.java
git commit -m "feat(hadith): репозитории editions/rulings/explanations/crossrefs/relations (Сессия 56)"
```

---

## Task 9: Round-trip integration test (proves schema + repos work end-to-end)

**Files:**
- Create: `backend/src/test/java/ru/basnukaev/argumentmap/hadith/repository/AlminasaSchemaRepositoryIT.java`

This single IT proves: migrations 70+71 apply on a real Postgres, the new columns round-trip on `hd_hadiths`/`hd_narrators`, `findByExternalId` works, and all five child repositories insert+read. It needs a parent `hd_collections`, `hd_hadiths`, and `hd_narrators` row (FK targets).

- [ ] **Step 1: Write the failing test**

```java
package ru.basnukaev.argumentmap.hadith.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithCrossref;
import ru.basnukaev.argumentmap.hadith.domain.HadithEdition;
import ru.basnukaev.argumentmap.hadith.domain.HadithExplanation;
import ru.basnukaev.argumentmap.hadith.domain.HadithRuling;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorRelation;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AlminasaSchemaRepositoryIT {

    @Autowired CollectionRepository collectionRepository;
    @Autowired HadithRepository hadithRepository;
    @Autowired NarratorRepository narratorRepository;
    @Autowired HadithEditionRepository editionRepository;
    @Autowired HadithRulingRepository rulingRepository;
    @Autowired HadithExplanationRepository explanationRepository;
    @Autowired HadithCrossrefRepository crossrefRepository;
    @Autowired NarratorRelationRepository relationRepository;

    @Test
    void hadithRoundTripsAlminasaColumns() {
        UUID collectionId = UUID.randomUUID();
        collectionRepository.save(new Collection(
                collectionId, "bukhari-it-" + collectionId, "صحيح البخاري",
                "Sahih al-Bukhari", null, null, 7031, "{}", Instant.now()));

        UUID hadithId = UUID.randomUUID();
        hadithRepository.save(new Hadith(
                hadithId, collectionId, 1, "انما الاعمال بالنيات", "CANONICAL",
                null, "{}", Instant.now(),
                "alminasa", "146-1", "مرفوع", "باب بدء الوحي",
                "باب كيف كان بدء الوحي", "<a class=rawy id=4698>الحميدي</a>"));

        Hadith found = hadithRepository.findByExternalId("alminasa", "146-1").orElseThrow();
        assertThat(found.id()).isEqualTo(hadithId);
        assertThat(found.hadithType()).isEqualTo("مرفوع");
        assertThat(found.chapterAr()).isEqualTo("باب بدء الوحي");
        assertThat(found.fullTextAr()).contains("rawy id=4698");
    }

    @Test
    void narratorRoundTripsAlminasaColumns() {
        UUID narratorId = UUID.randomUUID();
        narratorRepository.save(new Narrator(
                narratorId, null, "علقمة بن وقاص العتواري", "علقمه بن وقاص العتواري",
                "أبو يحيى", null, null, null, "المدينة", "المدينة", "المدينة",
                "THIQA", null, 0, "{}", Instant.now(),
                "alminasa", "5719", "الثانية", "ثقة ثبت",
                "ولد على عهده عهد النبي", "في خلافة عبد الملك بن مروان"));

        Narrator found = narratorRepository.findByExternalId("alminasa", "5719").orElseThrow();
        assertThat(found.id()).isEqualTo(narratorId);
        assertThat(found.tabaqa()).isEqualTo("الثانية");
        assertThat(found.gradeText()).isEqualTo("ثقة ثبت");
        assertThat(found.bornOnText()).contains("النبي");
    }

    @Test
    void childTablesInsertAndRead() {
        UUID collectionId = UUID.randomUUID();
        collectionRepository.save(new Collection(
                collectionId, "child-it-" + collectionId, "ص", null, null, null, null,
                "{}", Instant.now()));
        UUID hadithId = UUID.randomUUID();
        hadithRepository.save(new Hadith(
                hadithId, collectionId, 1, "n", "VARIANT", null, "{}", Instant.now()));
        UUID narratorId = UUID.randomUUID();
        narratorRepository.save(new Narrator(
                narratorId, null, "x", "x", null, null, null, null, null, null, null,
                "UNKNOWN", null, 0, "{}", Instant.now()));

        editionRepository.save(new HadithEdition(
                UUID.randomUUID(), hadithId, "دار طوق النجاة", 6, 1));
        rulingRepository.save(new HadithRuling(
                UUID.randomUUID(), hadithId, "البخاري", 256, "أورده في صحيحه",
                "صحيح البخاري", 6, 1, "{}", Instant.now()));
        explanationRepository.save(new HadithExplanation(
                UUID.randomUUID(), hadithId, "SHARH", "فتح الباري", "ابن حجر", 852,
                15, 1, "نص الشرح", "{}", Instant.now()));
        crossrefRepository.save(new HadithCrossref(
                UUID.randomUUID(), hadithId, "146-2356", null, "raw", null, Instant.now()));
        relationRepository.save(new NarratorRelation(
                UUID.randomUUID(), narratorId, null, "الزهري", "STUDENT", 24, Instant.now()));

        assertThat(editionRepository.findByHadithId(hadithId)).hasSize(1);
        assertThat(rulingRepository.findByHadithId(hadithId)).singleElement()
                .satisfies(r -> assertThat(r.rulerDeathYear()).isEqualTo(256));
        assertThat(explanationRepository.findByHadithId(hadithId)).singleElement()
                .satisfies(e -> assertThat(e.kind()).isEqualTo("SHARH"));
        assertThat(crossrefRepository.findByHadithId(hadithId)).singleElement()
                .satisfies(c -> assertThat(c.relatedExternalId()).isEqualTo("146-2356"));
        assertThat(relationRepository.findByNarratorId(narratorId)).singleElement()
                .satisfies(rel -> assertThat(rel.cnt()).isEqualTo(24));
    }
}
```

- [ ] **Step 2: Run the test, expect FAIL first if any wiring is off, then PASS**

Run: `cd backend && ./mvnw -q -Dtest=AlminasaSchemaRepositoryIT test`
Expected: PASS (3 tests). If `CollectionRepository` constructor signature differs from what's used here, adjust the `new Collection(...)` 9-arg call to match the verified backward-compat constructor; do not change production code to fit the test.

- [ ] **Step 3: Full verify (logical phase boundary — schema + model + repos complete)**

Run: `cd backend && ./mvnw verify`
Expected: BUILD SUCCESS, all existing IT + the new one green. This is a real signal moment (migration + new tables + domain arity change touched multiple layers).

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/ru/basnukaev/argumentmap/hadith/repository/AlminasaSchemaRepositoryIT.java
git commit -m "test(hadith): round-trip IT для alminasa-схемы (колонки + 5 таблиц) (Сессия 56)"
```

---

## Task 10: Update docs (same-commit doc hygiene)

**Files:**
- Modify: `docs/decisions.md` (ADR-060 stub), `docs/architecture.md`, `docs/glossary.md`

- [ ] **Step 1: Add ADR-060** to `docs/decisions.md` — title "alminasa.ai — единственный источник хадисов", status Accepted, context (HAR-анализ показал structured иснад/риджаль/такхридж/рулинги/шарх), decision (bulk-снапшот через staging, 1:1 атомарно + cross-refs, AI-перевод), consequences (ADR-059 AI-иснад → superseded в части извлечения; sunnah ETL → удаляется в Plan 2). Reference the spec path.

- [ ] **Step 2: Update `docs/architecture.md`** — under the hadith section, note the new columns + 5 tables and that they hold alminasa-imported data.

- [ ] **Step 3: Update `docs/glossary.md`** — add terms: табака (tabaqa), такхридж (takhrij), طرق (turuq), иляль (ʿilal), гариб (gharīb al-hadith), джарх ва та'диль.

- [ ] **Step 4: Commit**

```bash
git add docs/decisions.md docs/architecture.md docs/glossary.md
git commit -m "docs: ADR-060 + architecture/glossary под alminasa-схему (Сессия 56)"
```

---

## Self-Review notes (already applied)

- **Spec coverage (Plan 1 scope = §B schema):** new columns on hd_hadiths/hd_narrators ✓ (Task 1); five new tables ✓ (Task 2); domain records ✓ (Tasks 3,5,7); repositories with external-id idempotency lookups ✓ (Tasks 4,6,8); proof ✓ (Task 9). Out of Plan-1 scope (deferred): staging tables `am_staging_*` + checkpoint (Plan 2, where the crawler uses them); the deterministic isnad parser + mapper (Plan 3); legacy removal (separate plan).
- **Placeholders:** none — every step has full code/SQL or an exact command.
- **Type consistency:** record component names match repository COLUMNS order and RowMapper calls; `findByExternalId(externalSource, externalId)` signature consistent across Hadith/Narrator repos; child-table `deleteByHadithId`/`deleteByNarratorId` reserved for Plan 3 idempotent re-import.
- **Known nit flagged inline:** `HadithEditionRepository` has no `created_at`, so it must not import/declare the `instant`/`odt` helpers (Task 8 Step 6 calls this out).
