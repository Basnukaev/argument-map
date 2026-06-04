# ADR-028 Academic Citation Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Расширить схему либлиотеки полями для бахс-grade academic citation (мухаккик, издатель, место, edition, годы по хиджре и григорианскому, полное имя автора + год смерти) с нормализованными справочниками и structured citation response вместо склеенной строки.

**Architecture:** Нормализованный middle path - справочники `lib_publishers`/`lib_publication_places`/`lib_muhaqqiqs` для high-reuse полей, расширение existing `authorities` для академического имени автора, per-book scalars (`edition_number`, годы) плоско в `lib_books`. Backend возвращает structured `CitationDetail` через 9 LEFT JOIN, фронт рисует каждое поле отдельным блоком.

**Tech Stack:** Java 21, Spring Boot 3.5, JDBC Template, Postgres 16, Liquibase, Testcontainers, JUnit 5, AssertJ.

**No backward compat:** проект пока без production'а, миграция чистая, существующие dev-данные либо переимпортируются, либо получают null в новых FK. См. memory `feedback_no_prod_no_backward_compat`.

**Файл-структура (что создаётся / меняется):**

| Файл | Действие | Назначение |
|---|---|---|
| `backend/src/main/resources/db/changelog/changes/20260514-24-add-academic-citation-metadata.xml` | create | Liquibase миграция: расширение `authorities` + 3 справочника + расширение `lib_books` |
| `backend/src/main/resources/db/changelog/db.changelog-master.xml` | modify | Регистрация миграции 24 |
| `backend/src/main/java/ru/basnukaev/argumentmap/library/domain/Publisher.java` | create | Record издательства |
| `backend/src/main/java/ru/basnukaev/argumentmap/library/domain/PublicationPlace.java` | create | Record места публикации |
| `backend/src/main/java/ru/basnukaev/argumentmap/library/domain/Muhaqqiq.java` | create | Record редактора тахкика |
| `backend/src/main/java/ru/basnukaev/argumentmap/library/repository/PublisherRepository.java` | create | JDBC repo для publishers с findOrCreate |
| `backend/src/main/java/ru/basnukaev/argumentmap/library/repository/PublicationPlaceRepository.java` | create | JDBC repo для places |
| `backend/src/main/java/ru/basnukaev/argumentmap/library/repository/MuhaqqiqRepository.java` | create | JDBC repo для muhaqqiqs |
| `backend/src/main/java/ru/basnukaev/argumentmap/domain/Authority.java` | modify | + `fullName`, `deathYearHijri` |
| `backend/src/main/java/ru/basnukaev/argumentmap/repository/AuthorityRepository.java` | modify | COLUMNS / ROW_MAPPER / save расширены |
| `backend/src/main/java/ru/basnukaev/argumentmap/library/domain/Book.java` | modify | + 3 FK (`muhaqqiqId`, `publisherId`, `publicationPlaceId`) + 3 скаляра |
| `backend/src/main/java/ru/basnukaev/argumentmap/library/repository/BookRepository.java` | modify | COLUMNS / ROW_MAPPER / save расширены |
| `backend/src/main/java/ru/basnukaev/argumentmap/domain/CitationDetail.java` | create | Structured citation record (27 полей) |
| `backend/src/main/java/ru/basnukaev/argumentmap/repository/NodeSourceRepository.java` | modify | `NodeSourceWithLocation` рефактор, `findByNodeIdWithLocation` с 9 LEFT JOIN |
| `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/CitationResponse.java` | create | Top-level citation DTO |
| `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/AuthorityCitationRef.java` | create | Nested ref для автора |
| `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/BookCitationRef.java` | create | Nested ref для книги |
| `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/MuhaqqiqRef.java` | create | Nested ref для мухаккика |
| `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/PublisherRef.java` | create | Nested ref для издателя |
| `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/PublicationPlaceRef.java` | create | Nested ref для места |
| `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/LocationRef.java` | create | Nested ref для локации в книге |
| `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/PdfRef.java` | create | Nested ref для PDF |
| `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/RegionRef.java` | create | Nested ref для region |
| `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/NodeSourceResponse.java` | modify | `location` + `bookId` уходят, добавляется nested `citation` |
| `backend/src/main/java/ru/basnukaev/argumentmap/web/mapper/DtoMappers.java` | modify | Новый маппер `CitationDetail → CitationResponse`, обновлён `toResponse(NodeSourceWithLocation)` |
| `backend/src/test/java/ru/basnukaev/argumentmap/library/repository/PublisherRepositoryIT.java` | create | 5 IT |
| `backend/src/test/java/ru/basnukaev/argumentmap/library/repository/PublicationPlaceRepositoryIT.java` | create | 5 IT |
| `backend/src/test/java/ru/basnukaev/argumentmap/library/repository/MuhaqqiqRepositoryIT.java` | create | 5 IT |
| `backend/src/test/java/ru/basnukaev/argumentmap/repository/AuthorityRepositoryIT.java` | create or modify | 2-3 новых IT (fullName/deathYear) |
| `backend/src/test/java/ru/basnukaev/argumentmap/library/repository/BookRepositoryIT.java` | modify | + 4 IT (academic round-trip, CHECK violations) |
| `backend/src/test/java/ru/basnukaev/argumentmap/repository/NodeSourceRepositoryIT.java` | modify | + 5 IT (CitationDetail с full/partial/no-book/PDF/REGION) |
| `backend/src/test/java/ru/basnukaev/argumentmap/web/controller/NodeSourceControllerIT.java` | modify or create | + 2 IT (nested citation в response) |
| `docs/decisions.md` | modify | ADR-028 |
| `docs/architecture.md` | modify | Library entity expansion |
| `docs/api-contract.md` | modify | `GET /api/v1/nodes/{id}/sources` response shape + history |
| `docs/glossary.md` | modify | мухаккик / тахкик / edition / хиджра / кунья-насаб-нисба |
| `docs/roadmap.md` | modify | `[x]` на 20.a |
| `docs/progress.md` | modify | Запись «Сессия 31» сверху |
| `docs/SESSION_START_PROMPT.md` | modify | Handoff для следующей сессии |

---

### Task 1: Liquibase миграция 24

**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/20260514-24-add-academic-citation-metadata.xml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml:30` (добавить `<include>` после строки 30)

- [ ] **Step 1: Создать миграционный XML**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                            https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="20260514-24-add-academic-citation-metadata" author="Abdula Basnukaev">
        <comment>
            Реализация Этапа 20.a (ADR-028): полная academic citation metadata.

            Нормализованный middle path для бахс-grade citation:
              (1) Справочники lib_publishers / lib_publication_places /
                  lib_muhaqqiqs - высокий reuse (одно издательство = десятки
                  книг). UNIQUE на name + ETL findOrCreate по нормализованному
                  имени даёт data quality (нет typo-дублей "Дар Тайба" vs
                  "Дар-Тайба").

              (2) Расширение authorities полями full_name + death_year_hijri.
                  authorities уже cross-book entity (один автор пишет N книг),
                  естественное место для академического имени. name остаётся
                  для short display, full_name - для academic citation.

              (3) Per-book scalars в lib_books: edition_number /
                  published_year_hijri / published_year_gregorian. Не reusable
                  (каждая книга имеет свои годы), нет смысла в справочнике.

              (4) FK с ON DELETE SET NULL по аналогии с ADR-017 (удаление
                  справочника не сносит книги каскадно, делает поле NULL).

              (5) CHECK constraints для sanity: положительные годы по хиджре
                  до 2000, по григорианскому до 2200, положительный
                  edition_number.

              (6) No backward compat - проект пока без production'а,
                  существующие dev-rows получают NULL в новых FK
                  (см. memory feedback_no_prod_no_backward_compat).
        </comment>
        <sql>
            ALTER TABLE authorities
              ADD COLUMN full_name              TEXT,
              ADD COLUMN death_year_hijri       INTEGER;
            ALTER TABLE authorities
              ADD CONSTRAINT authorities_death_year_sane
                CHECK (death_year_hijri IS NULL
                       OR (death_year_hijri &gt; 0 AND death_year_hijri &lt; 2000));

            CREATE TABLE lib_publishers (
                id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                name        TEXT NOT NULL UNIQUE,
                created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
            );
            CREATE INDEX idx_lib_publishers_name ON lib_publishers(name);

            CREATE TABLE lib_publication_places (
                id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                name        TEXT NOT NULL UNIQUE,
                created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
            );
            CREATE INDEX idx_lib_publication_places_name ON lib_publication_places(name);

            CREATE TABLE lib_muhaqqiqs (
                id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                name        TEXT NOT NULL UNIQUE,
                full_name   TEXT,
                created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
            );
            CREATE INDEX idx_lib_muhaqqiqs_name ON lib_muhaqqiqs(name);

            ALTER TABLE lib_books
              ADD COLUMN muhaqqiq_id              UUID REFERENCES lib_muhaqqiqs(id) ON DELETE SET NULL,
              ADD COLUMN publisher_id             UUID REFERENCES lib_publishers(id) ON DELETE SET NULL,
              ADD COLUMN publication_place_id     UUID REFERENCES lib_publication_places(id) ON DELETE SET NULL,
              ADD COLUMN edition_number           INTEGER,
              ADD COLUMN published_year_hijri     INTEGER,
              ADD COLUMN published_year_gregorian INTEGER;

            ALTER TABLE lib_books
              ADD CONSTRAINT lib_books_edition_positive
                CHECK (edition_number IS NULL OR edition_number &gt; 0),
              ADD CONSTRAINT lib_books_hijri_sane
                CHECK (published_year_hijri IS NULL
                       OR (published_year_hijri &gt; 0 AND published_year_hijri &lt; 2000)),
              ADD CONSTRAINT lib_books_gregorian_sane
                CHECK (published_year_gregorian IS NULL
                       OR (published_year_gregorian &gt; 0 AND published_year_gregorian &lt; 2200));

            CREATE INDEX idx_lib_books_muhaqqiq_id          ON lib_books(muhaqqiq_id);
            CREATE INDEX idx_lib_books_publisher_id         ON lib_books(publisher_id);
            CREATE INDEX idx_lib_books_publication_place_id ON lib_books(publication_place_id);
        </sql>
        <rollback>
            <sql>
                ALTER TABLE lib_books DROP CONSTRAINT lib_books_gregorian_sane;
                ALTER TABLE lib_books DROP CONSTRAINT lib_books_hijri_sane;
                ALTER TABLE lib_books DROP CONSTRAINT lib_books_edition_positive;
                ALTER TABLE lib_books DROP COLUMN published_year_gregorian;
                ALTER TABLE lib_books DROP COLUMN published_year_hijri;
                ALTER TABLE lib_books DROP COLUMN edition_number;
                ALTER TABLE lib_books DROP COLUMN publication_place_id;
                ALTER TABLE lib_books DROP COLUMN publisher_id;
                ALTER TABLE lib_books DROP COLUMN muhaqqiq_id;
                DROP TABLE lib_muhaqqiqs;
                DROP TABLE lib_publication_places;
                DROP TABLE lib_publishers;
                ALTER TABLE authorities DROP CONSTRAINT authorities_death_year_sane;
                ALTER TABLE authorities DROP COLUMN death_year_hijri;
                ALTER TABLE authorities DROP COLUMN full_name;
            </sql>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 2: Зарегистрировать миграцию в master**

В `backend/src/main/resources/db/changelog/db.changelog-master.xml` после строки `<include file="db/changelog/changes/20260513-23-add-positional-fields-to-node-sources.xml"/>` добавить:

```xml
    <include file="db/changelog/changes/20260514-24-add-academic-citation-metadata.xml"/>
```

- [ ] **Step 3: Применить миграцию через restart backend**

```bash
kill $(lsof -ti:9090) 2>/dev/null; sleep 2
cd backend && ./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  > /tmp/backend.log 2>&1 &
until curl -sf http://localhost:9090/actuator/health; do sleep 2; done
```

Expected: backend стартует за 30-40 сек без ошибок Liquibase.

- [ ] **Step 4: Проверить применение миграции через psql**

```bash
docker exec argumentmap-postgres psql -U argmap -d argumentmap -c "\d lib_books" | grep -E "muhaqqiq_id|publisher_id|publication_place_id|edition_number|published_year_hijri|published_year_gregorian"
```

Expected: 6 строк с новыми колонками.

```bash
docker exec argumentmap-postgres psql -U argmap -d argumentmap -c "\dt lib_publishers|lib_publication_places|lib_muhaqqiqs"
```

Expected: 3 таблицы.

- [ ] **Step 5: Commit миграции**

```bash
git add backend/src/main/resources/db/changelog/changes/20260514-24-add-academic-citation-metadata.xml \
        backend/src/main/resources/db/changelog/db.changelog-master.xml
git commit -m "$(cat <<'EOF'
feat(backend): этап 20.a - миграция 24 academic citation metadata

ALTER authorities + full_name + death_year_hijri.
CREATE lib_publishers / lib_publication_places / lib_muhaqqiqs справочники.
ALTER lib_books + 3 FK + 3 скаляра (edition_number, годы по хиджре и григорианскому).
3 CHECK constraint для sanity, 3 BTREE индекса на новые FK.

ADR-028 - см. docs/specs/2026-05-14-academic-citation-metadata-design.md
EOF
)"
```

---

### Task 2: Publisher domain + repository

**Files:**
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/library/domain/Publisher.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/library/repository/PublisherRepository.java`
- Create: `backend/src/test/java/ru/basnukaev/argumentmap/library/repository/PublisherRepositoryIT.java`

- [ ] **Step 1: Создать Publisher record**

```java
package ru.basnukaev.argumentmap.library.domain;

import java.time.Instant;
import java.util.UUID;

public record Publisher(
        UUID id,
        String name,
        Instant createdAt
) {
}
```

- [ ] **Step 2: Создать PublisherRepository**

```java
package ru.basnukaev.argumentmap.library.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.library.domain.Publisher;

@Repository
public class PublisherRepository {

    private static final String COLUMNS = "id, name, created_at";

    private static final RowMapper<Publisher> ROW_MAPPER = (rs, rn) -> new Publisher(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public PublisherRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Publisher save(Publisher publisher) {
        jdbcTemplate.update(
                "INSERT INTO lib_publishers (" + COLUMNS + ") VALUES (?, ?, ?)",
                publisher.id(),
                publisher.name(),
                odt(publisher.createdAt())
        );
        return publisher;
    }

    public Optional<Publisher> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_publishers WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public Optional<Publisher> findByName(String name) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_publishers WHERE name = ?",
                ROW_MAPPER,
                name
        ).stream().findFirst();
    }

    /**
     * Helper для ETL: если издатель с таким именем уже есть - возвращает его id,
     * иначе создаёт новый row + возвращает свежий id. Идемпотентен для повторных
     * вызовов с тем же именем.
     */
    public UUID findOrCreate(String name) {
        return findByName(name)
                .map(Publisher::id)
                .orElseGet(() -> save(new Publisher(UUID.randomUUID(), name, Instant.now())).id());
    }

    public List<Publisher> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_publishers ORDER BY name",
                ROW_MAPPER
        );
    }
}
```

- [ ] **Step 3: Написать IT (failing test)**

```java
package ru.basnukaev.argumentmap.library.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.domain.Publisher;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PublisherRepositoryIT {

    @Autowired
    private PublisherRepository repository;

    @Test
    void save_insertsAndFindByIdReturnsIt() {
        Publisher publisher = new Publisher(UUID.randomUUID(), "Дар Тайба", Instant.now());

        repository.save(publisher);

        Publisher reloaded = repository.findById(publisher.id()).orElseThrow();
        assertThat(reloaded.name()).isEqualTo("Дар Тайба");
    }

    @Test
    void findByName_returnsRowWhenExists() {
        Publisher publisher = repository.save(new Publisher(
                UUID.randomUUID(), "Дар аль-Фикр", Instant.now()
        ));

        assertThat(repository.findByName("Дар аль-Фикр"))
                .isPresent()
                .map(Publisher::id)
                .hasValue(publisher.id());
    }

    @Test
    void findByName_returnsEmptyWhenAbsent() {
        assertThat(repository.findByName("Несуществующее издательство")).isEmpty();
    }

    @Test
    void findOrCreate_returnsExistingIdWhenPresent() {
        Publisher publisher = repository.save(new Publisher(
                UUID.randomUUID(), "Дар Ибн Хазм", Instant.now()
        ));

        UUID id = repository.findOrCreate("Дар Ибн Хазм");

        assertThat(id).isEqualTo(publisher.id());
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void findOrCreate_createsNewRowWhenAbsent() {
        UUID id = repository.findOrCreate("Дар аль-Кутуб аль-Ильмия");

        assertThat(repository.findById(id))
                .isPresent()
                .map(Publisher::name)
                .hasValue("Дар аль-Кутуб аль-Ильмия");
    }

    @Test
    void save_uniqueNameViolation_throws() {
        repository.save(new Publisher(UUID.randomUUID(), "Дар Тайба", Instant.now()));

        assertThatThrownBy(() ->
                repository.save(new Publisher(UUID.randomUUID(), "Дар Тайба", Instant.now()))
        ).isInstanceOf(DuplicateKeyException.class);
    }
}
```

- [ ] **Step 4: Запустить IT - убедиться что pass**

```bash
cd backend && ./mvnw -Dtest='PublisherRepositoryIT' -DfailIfNoTests=false test
```

Expected: `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/library/domain/Publisher.java \
        backend/src/main/java/ru/basnukaev/argumentmap/library/repository/PublisherRepository.java \
        backend/src/test/java/ru/basnukaev/argumentmap/library/repository/PublisherRepositoryIT.java
git commit -m "feat(backend): этап 20.a - Publisher record + repository

Domain record + JDBC repository для справочника издательств.
findOrCreate(name) helper для ETL upsert по UNIQUE name.
6 IT через Testcontainers."
```

---

### Task 3: PublicationPlace domain + repository

**Files:**
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/library/domain/PublicationPlace.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/library/repository/PublicationPlaceRepository.java`
- Create: `backend/src/test/java/ru/basnukaev/argumentmap/library/repository/PublicationPlaceRepositoryIT.java`

- [ ] **Step 1: Создать PublicationPlace record**

```java
package ru.basnukaev.argumentmap.library.domain;

import java.time.Instant;
import java.util.UUID;

public record PublicationPlace(
        UUID id,
        String name,
        Instant createdAt
) {
}
```

- [ ] **Step 2: Создать PublicationPlaceRepository**

```java
package ru.basnukaev.argumentmap.library.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.library.domain.PublicationPlace;

@Repository
public class PublicationPlaceRepository {

    private static final String COLUMNS = "id, name, created_at";

    private static final RowMapper<PublicationPlace> ROW_MAPPER = (rs, rn) -> new PublicationPlace(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public PublicationPlaceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PublicationPlace save(PublicationPlace place) {
        jdbcTemplate.update(
                "INSERT INTO lib_publication_places (" + COLUMNS + ") VALUES (?, ?, ?)",
                place.id(),
                place.name(),
                odt(place.createdAt())
        );
        return place;
    }

    public Optional<PublicationPlace> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_publication_places WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public Optional<PublicationPlace> findByName(String name) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_publication_places WHERE name = ?",
                ROW_MAPPER,
                name
        ).stream().findFirst();
    }

    public UUID findOrCreate(String name) {
        return findByName(name)
                .map(PublicationPlace::id)
                .orElseGet(() -> save(new PublicationPlace(UUID.randomUUID(), name, Instant.now())).id());
    }

    public List<PublicationPlace> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_publication_places ORDER BY name",
                ROW_MAPPER
        );
    }
}
```

- [ ] **Step 3: Написать IT**

```java
package ru.basnukaev.argumentmap.library.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.domain.PublicationPlace;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PublicationPlaceRepositoryIT {

    @Autowired
    private PublicationPlaceRepository repository;

    @Test
    void save_insertsAndFindByIdReturnsIt() {
        PublicationPlace place = new PublicationPlace(UUID.randomUUID(), "Бейрут", Instant.now());

        repository.save(place);

        assertThat(repository.findById(place.id())).isPresent()
                .map(PublicationPlace::name).hasValue("Бейрут");
    }

    @Test
    void findByName_returnsRowWhenExists() {
        PublicationPlace place = repository.save(new PublicationPlace(
                UUID.randomUUID(), "Эр-Рияд", Instant.now()
        ));

        assertThat(repository.findByName("Эр-Рияд"))
                .isPresent()
                .map(PublicationPlace::id)
                .hasValue(place.id());
    }

    @Test
    void findByName_returnsEmptyWhenAbsent() {
        assertThat(repository.findByName("Несуществующий город")).isEmpty();
    }

    @Test
    void findOrCreate_returnsExistingIdWhenPresent() {
        PublicationPlace place = repository.save(new PublicationPlace(
                UUID.randomUUID(), "Каир", Instant.now()
        ));

        UUID id = repository.findOrCreate("Каир");

        assertThat(id).isEqualTo(place.id());
    }

    @Test
    void findOrCreate_createsNewRowWhenAbsent() {
        UUID id = repository.findOrCreate("Дамаск");

        assertThat(repository.findById(id)).isPresent()
                .map(PublicationPlace::name).hasValue("Дамаск");
    }

    @Test
    void save_uniqueNameViolation_throws() {
        repository.save(new PublicationPlace(UUID.randomUUID(), "Багдад", Instant.now()));

        assertThatThrownBy(() ->
                repository.save(new PublicationPlace(UUID.randomUUID(), "Багдад", Instant.now()))
        ).isInstanceOf(DuplicateKeyException.class);
    }
}
```

- [ ] **Step 4: Запустить IT**

```bash
cd backend && ./mvnw -Dtest='PublicationPlaceRepositoryIT' -DfailIfNoTests=false test
```

Expected: `Tests run: 6, Failures: 0`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/library/domain/PublicationPlace.java \
        backend/src/main/java/ru/basnukaev/argumentmap/library/repository/PublicationPlaceRepository.java \
        backend/src/test/java/ru/basnukaev/argumentmap/library/repository/PublicationPlaceRepositoryIT.java
git commit -m "feat(backend): этап 20.a - PublicationPlace record + repository

Domain record + JDBC repository для справочника городов публикации.
findOrCreate(name) helper. 6 IT."
```

---

### Task 4: Muhaqqiq domain + repository

**Files:**
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/library/domain/Muhaqqiq.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/library/repository/MuhaqqiqRepository.java`
- Create: `backend/src/test/java/ru/basnukaev/argumentmap/library/repository/MuhaqqiqRepositoryIT.java`

- [ ] **Step 1: Создать Muhaqqiq record (4 поля - с fullName)**

```java
package ru.basnukaev.argumentmap.library.domain;

import java.time.Instant;
import java.util.UUID;

public record Muhaqqiq(
        UUID id,
        String name,
        String fullName,
        Instant createdAt
) {
}
```

- [ ] **Step 2: Создать MuhaqqiqRepository**

```java
package ru.basnukaev.argumentmap.library.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.library.domain.Muhaqqiq;

@Repository
public class MuhaqqiqRepository {

    private static final String COLUMNS = "id, name, full_name, created_at";

    private static final RowMapper<Muhaqqiq> ROW_MAPPER = (rs, rn) -> new Muhaqqiq(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("full_name"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public MuhaqqiqRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Muhaqqiq save(Muhaqqiq muhaqqiq) {
        jdbcTemplate.update(
                "INSERT INTO lib_muhaqqiqs (" + COLUMNS + ") VALUES (?, ?, ?, ?)",
                muhaqqiq.id(),
                muhaqqiq.name(),
                muhaqqiq.fullName(),
                odt(muhaqqiq.createdAt())
        );
        return muhaqqiq;
    }

    public Optional<Muhaqqiq> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_muhaqqiqs WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public Optional<Muhaqqiq> findByName(String name) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_muhaqqiqs WHERE name = ?",
                ROW_MAPPER,
                name
        ).stream().findFirst();
    }

    /**
     * Helper для ETL: создаёт row только с short name (fullName = null),
     * если ETL парсер позже найдёт полное имя - можно обновить через save
     * separate row (нет update operation в этой репе).
     */
    public UUID findOrCreate(String name) {
        return findByName(name)
                .map(Muhaqqiq::id)
                .orElseGet(() -> save(new Muhaqqiq(UUID.randomUUID(), name, null, Instant.now())).id());
    }

    public List<Muhaqqiq> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_muhaqqiqs ORDER BY name",
                ROW_MAPPER
        );
    }
}
```

- [ ] **Step 3: Написать IT**

```java
package ru.basnukaev.argumentmap.library.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.domain.Muhaqqiq;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MuhaqqiqRepositoryIT {

    @Autowired
    private MuhaqqiqRepository repository;

    @Test
    void save_withFullName_roundTrip() {
        Muhaqqiq muhaqqiq = new Muhaqqiq(
                UUID.randomUUID(),
                "السلامة",
                "سامي بن محمد السلامة",
                Instant.now()
        );

        repository.save(muhaqqiq);

        Muhaqqiq reloaded = repository.findById(muhaqqiq.id()).orElseThrow();
        assertThat(reloaded.name()).isEqualTo("السلامة");
        assertThat(reloaded.fullName()).isEqualTo("سامي بن محمد السلامة");
    }

    @Test
    void save_withNullFullName_persistsNull() {
        Muhaqqiq muhaqqiq = new Muhaqqiq(UUID.randomUUID(), "Аль-Албани", null, Instant.now());

        repository.save(muhaqqiq);

        assertThat(repository.findById(muhaqqiq.id()).orElseThrow().fullName()).isNull();
    }

    @Test
    void findByName_returnsRow() {
        Muhaqqiq muhaqqiq = repository.save(new Muhaqqiq(
                UUID.randomUUID(), "Шуайб аль-Арна'ут", "أبو أسامة شعيب الأرناؤوط", Instant.now()
        ));

        assertThat(repository.findByName("Шуайб аль-Арна'ут"))
                .isPresent()
                .map(Muhaqqiq::id)
                .hasValue(muhaqqiq.id());
    }

    @Test
    void findOrCreate_createsWithNullFullName() {
        UUID id = repository.findOrCreate("Новый редактор");

        Muhaqqiq created = repository.findById(id).orElseThrow();
        assertThat(created.name()).isEqualTo("Новый редактор");
        assertThat(created.fullName()).isNull();
    }

    @Test
    void findOrCreate_returnsExistingId() {
        Muhaqqiq existing = repository.save(new Muhaqqiq(
                UUID.randomUUID(), "Ас-Сахалити", "محمد ناصر الدين السحاليتي", Instant.now()
        ));

        assertThat(repository.findOrCreate("Ас-Сахалити")).isEqualTo(existing.id());
    }

    @Test
    void save_uniqueNameViolation_throws() {
        repository.save(new Muhaqqiq(UUID.randomUUID(), "Ат-Турки", null, Instant.now()));

        assertThatThrownBy(() ->
                repository.save(new Muhaqqiq(UUID.randomUUID(), "Ат-Турки", "разный fullName", Instant.now()))
        ).isInstanceOf(DuplicateKeyException.class);
    }
}
```

- [ ] **Step 4: Запустить IT**

```bash
cd backend && ./mvnw -Dtest='MuhaqqiqRepositoryIT' -DfailIfNoTests=false test
```

Expected: `Tests run: 6, Failures: 0`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/library/domain/Muhaqqiq.java \
        backend/src/main/java/ru/basnukaev/argumentmap/library/repository/MuhaqqiqRepository.java \
        backend/src/test/java/ru/basnukaev/argumentmap/library/repository/MuhaqqiqRepositoryIT.java
git commit -m "feat(backend): этап 20.a - Muhaqqiq record + repository

Domain record (id/name/fullName/createdAt) + JDBC repository для
справочника редакторов тахкика. findOrCreate(name) создаёт row
только с short name (fullName = null), ETL парсер может позже
обогатить полным именем. 6 IT."
```

---

### Task 5: Расширение Authority record + AuthorityRepository

**Files:**
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/domain/Authority.java` (entire file)
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/repository/AuthorityRepository.java` (COLUMNS, ROW_MAPPER, save)
- Modify or Create: `backend/src/test/java/ru/basnukaev/argumentmap/repository/AuthorityRepositoryIT.java` (+ 3 IT)
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/web/mapper/DtoMappers.java:100-107` (toResponse Authority)
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/AuthorityResponse.java` (+ fullName, deathYearHijri)

- [ ] **Step 1: Расширить Authority record**

```java
package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

public record Authority(
        UUID id,
        String name,
        String bio,
        String era,
        String madhab,
        String metadata,
        Instant createdAt,
        String fullName,
        Integer deathYearHijri
) {
}
```

- [ ] **Step 2: Обновить AuthorityRepository**

```java
package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.Authority;

@Repository
public class AuthorityRepository {

    private static final String COLUMNS =
            "id, name, bio, era, madhab, metadata, created_at, full_name, death_year_hijri";

    private static final RowMapper<Authority> ROW_MAPPER = (rs, rn) -> {
        int deathYear = rs.getInt("death_year_hijri");
        Integer deathYearOrNull = rs.wasNull() ? null : deathYear;
        return new Authority(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("bio"),
                rs.getString("era"),
                rs.getString("madhab"),
                rs.getString("metadata"),
                instant(rs, "created_at"),
                rs.getString("full_name"),
                deathYearOrNull
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public AuthorityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Authority save(Authority authority) {
        jdbcTemplate.update(
                "INSERT INTO authorities (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)",
                authority.id(),
                authority.name(),
                authority.bio(),
                authority.era(),
                authority.madhab(),
                authority.metadata(),
                odt(authority.createdAt()),
                authority.fullName(),
                authority.deathYearHijri()
        );
        return authority;
    }

    public Optional<Authority> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM authorities WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public List<Authority> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM authorities ORDER BY name",
                ROW_MAPPER
        );
    }

    public List<Authority> searchByName(String query) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM authorities WHERE name ILIKE ? ORDER BY name",
                ROW_MAPPER,
                "%" + query + "%"
        );
    }

    public Optional<Authority> findByName(String name) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM authorities WHERE name = ? ORDER BY created_at LIMIT 1",
                ROW_MAPPER,
                name
        ).stream().findFirst();
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM authorities WHERE id = ?", id) > 0;
    }
}
```

- [ ] **Step 3: Обновить AuthorityResponse DTO**

Открыть `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/AuthorityResponse.java`. Добавить 2 поля в конец record:

```java
public record AuthorityResponse(
        UUID id,
        String name,
        String bio,
        String era,
        String madhab,
        com.fasterxml.jackson.databind.JsonNode metadata,
        java.time.Instant createdAt,
        String fullName,
        Integer deathYearHijri
) {
}
```

(Импорты на JsonNode/Instant уже могут быть выше - не дублировать.)

- [ ] **Step 4: Обновить DtoMappers.toResponse(Authority)**

В `backend/src/main/java/ru/basnukaev/argumentmap/web/mapper/DtoMappers.java` строки 100-107 заменить:

```java
public static AuthorityResponse toResponse(Authority authority) {
    return new AuthorityResponse(
            authority.id(), authority.name(), authority.bio(),
            authority.era(), authority.madhab(),
            jsonFromString(authority.metadata()),
            authority.createdAt(),
            authority.fullName(),
            authority.deathYearHijri()
    );
}
```

- [ ] **Step 5: Написать AuthorityRepositoryIT (новый файл если нет)**

Создать `backend/src/test/java/ru/basnukaev/argumentmap/repository/AuthorityRepositoryIT.java` (если уже есть - добавить 3 теста в конец):

```java
package ru.basnukaev.argumentmap.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.Authority;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthorityRepositoryIT {

    @Autowired
    private AuthorityRepository repository;

    @Test
    void save_withFullNameAndDeathYear_roundTrip() {
        Authority authority = new Authority(
                UUID.randomUUID(),
                "ابن كثير",
                null, "VIII в.х.", "shafii", null, Instant.now(),
                "إسماعيل بن عمر بن كثير الدمشقي",
                774
        );

        repository.save(authority);

        Authority reloaded = repository.findById(authority.id()).orElseThrow();
        assertThat(reloaded.fullName()).isEqualTo("إسماعيل بن عمر بن كثير الدمشقي");
        assertThat(reloaded.deathYearHijri()).isEqualTo(774);
    }

    @Test
    void save_withNullAcademicFields_persistsNulls() {
        Authority authority = new Authority(
                UUID.randomUUID(), "Без полной инфы",
                null, null, null, null, Instant.now(),
                null, null
        );

        repository.save(authority);

        Authority reloaded = repository.findById(authority.id()).orElseThrow();
        assertThat(reloaded.fullName()).isNull();
        assertThat(reloaded.deathYearHijri()).isNull();
    }

    @Test
    void save_deathYearZero_violatesCheck() {
        Authority bad = new Authority(
                UUID.randomUUID(), "Bad death year",
                null, null, null, null, Instant.now(),
                null, 0
        );

        assertThatThrownBy(() -> repository.save(bad))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_deathYearTooLarge_violatesCheck() {
        Authority bad = new Authority(
                UUID.randomUUID(), "Future scholar",
                null, null, null, null, Instant.now(),
                null, 2500
        );

        assertThatThrownBy(() -> repository.save(bad))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

- [ ] **Step 6: Запустить IT**

```bash
cd backend && ./mvnw -Dtest='AuthorityRepositoryIT' -DfailIfNoTests=false test
```

Expected: `Tests run: 4, Failures: 0`.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/domain/Authority.java \
        backend/src/main/java/ru/basnukaev/argumentmap/repository/AuthorityRepository.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/dto/AuthorityResponse.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/mapper/DtoMappers.java \
        backend/src/test/java/ru/basnukaev/argumentmap/repository/AuthorityRepositoryIT.java
git commit -m "feat(backend): этап 20.a - расширение Authority + AuthorityRepository

+ Authority.fullName, deathYearHijri (ADR-028).
AuthorityRepository.COLUMNS / ROW_MAPPER / save расширены.
AuthorityResponse DTO + DtoMappers обновлены.
4 новых IT - round-trip с full data, null fields, CHECK violations."
```

---

### Task 6: Расширение Book record + BookRepository

**Files:**
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/library/domain/Book.java` (entire file)
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/library/repository/BookRepository.java` (COLUMNS, ROW_MAPPER, save)
- Modify: `backend/src/test/java/ru/basnukaev/argumentmap/library/repository/BookRepositoryIT.java` (+ 4 IT)
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/library/web/dto/BookResponse.java` и `BookDetailResponse.java` (+ academic fields)
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/library/web/mapper/LibraryDtoMappers.java` (mapping)

- [ ] **Step 1: Расширить Book record**

```java
package ru.basnukaev.argumentmap.library.domain;

import java.time.Instant;
import java.util.UUID;

public record Book(
        UUID id,
        BookType bookType,
        String title,
        UUID authorityId,
        String language,
        String description,
        String metadata,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        UUID muhaqqiqId,
        UUID publisherId,
        UUID publicationPlaceId,
        Integer editionNumber,
        Integer publishedYearHijri,
        Integer publishedYearGregorian
) {
}
```

- [ ] **Step 2: Обновить BookRepository**

В `BookRepository.java` заменить COLUMNS / ROW_MAPPER / save:

```java
private static final String COLUMNS =
        "id, book_type, title, authority_id, language, description, metadata, "
        + "created_by, created_at, updated_at, "
        + "muhaqqiq_id, publisher_id, publication_place_id, "
        + "edition_number, published_year_hijri, published_year_gregorian";

private static final RowMapper<Book> ROW_MAPPER = (rs, rn) -> {
    int edition = rs.getInt("edition_number");
    Integer editionOrNull = rs.wasNull() ? null : edition;
    int yearH = rs.getInt("published_year_hijri");
    Integer yearHOrNull = rs.wasNull() ? null : yearH;
    int yearG = rs.getInt("published_year_gregorian");
    Integer yearGOrNull = rs.wasNull() ? null : yearG;

    return new Book(
            rs.getObject("id", UUID.class),
            BookType.valueOf(rs.getString("book_type")),
            rs.getString("title"),
            rs.getObject("authority_id", UUID.class),
            rs.getString("language"),
            rs.getString("description"),
            rs.getString("metadata"),
            rs.getObject("created_by", UUID.class),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            rs.getObject("muhaqqiq_id", UUID.class),
            rs.getObject("publisher_id", UUID.class),
            rs.getObject("publication_place_id", UUID.class),
            editionOrNull,
            yearHOrNull,
            yearGOrNull
    );
};
```

И обновить `save`:

```java
public Book save(Book book) {
    jdbcTemplate.update(
            "INSERT INTO lib_books (" + COLUMNS + ") "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            book.id(),
            book.bookType().name(),
            book.title(),
            book.authorityId(),
            book.language(),
            book.description(),
            book.metadata(),
            book.createdBy(),
            odt(book.createdAt()),
            odt(book.updatedAt()),
            book.muhaqqiqId(),
            book.publisherId(),
            book.publicationPlaceId(),
            book.editionNumber(),
            book.publishedYearHijri(),
            book.publishedYearGregorian()
    );
    return book;
}
```

- [ ] **Step 3: Поправить call sites где конструируется Book**

Найти все места где создаётся `new Book(...)`:

```bash
cd backend && grep -rn "new Book(" --include="*.java"
```

В каждом call site добавить 6 null'ов в конец параметров. Ожидаемые сайты (по cleanup marathon audit):
- `ShamelaToLibraryMapper` и nested mapper classes (`library/shamela/service/mapper/*`)
- `BookService.create` если использует `new Book`
- `BookRepositoryIT` (тесты уже использующие `new Book` - добавить 6 null'ов)
- `BookServiceIT`
- `BookControllerIT`
- любые другие IT

Для existing call sites (где академики не нужны) - просто `null, null, null, null, null, null` в конце. Поправить всё.

- [ ] **Step 4: Добавить 4 IT в BookRepositoryIT**

В `backend/src/test/java/ru/basnukaev/argumentmap/library/repository/BookRepositoryIT.java` добавить в конец class (перед закрывающей `}`):

```java
@Test
void save_withFullAcademicData_roundTrip() {
    UUID muhaqqiqId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lib_muhaqqiqs (id, name) VALUES (?, ?)",
            muhaqqiqId, "السلامة"
    );
    UUID publisherId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lib_publishers (id, name) VALUES (?, ?)",
            publisherId, "Дар Тайба"
    );
    UUID placeId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lib_publication_places (id, name) VALUES (?, ?)",
            placeId, "Эр-Рияд"
    );

    Book book = new Book(
            UUID.randomUUID(), BookType.BOOK,
            "تفسير القرآن العظيم", null, "ar",
            null, null, userId, Instant.now(), Instant.now(),
            muhaqqiqId, publisherId, placeId,
            2, 1420, 1999
    );

    bookRepository.save(book);

    Book reloaded = bookRepository.findById(book.id()).orElseThrow();
    assertThat(reloaded.muhaqqiqId()).isEqualTo(muhaqqiqId);
    assertThat(reloaded.publisherId()).isEqualTo(publisherId);
    assertThat(reloaded.publicationPlaceId()).isEqualTo(placeId);
    assertThat(reloaded.editionNumber()).isEqualTo(2);
    assertThat(reloaded.publishedYearHijri()).isEqualTo(1420);
    assertThat(reloaded.publishedYearGregorian()).isEqualTo(1999);
}

@Test
void save_withPartialAcademicData_persistsNullsForMissing() {
    UUID publisherId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lib_publishers (id, name) VALUES (?, ?)",
            publisherId, "Дар аль-Фикр"
    );

    Book book = new Book(
            UUID.randomUUID(), BookType.BOOK,
            "Книга с partial data", null, "ar",
            null, null, userId, Instant.now(), Instant.now(),
            null, publisherId, null,
            null, 1430, null
    );

    bookRepository.save(book);

    Book reloaded = bookRepository.findById(book.id()).orElseThrow();
    assertThat(reloaded.muhaqqiqId()).isNull();
    assertThat(reloaded.publisherId()).isEqualTo(publisherId);
    assertThat(reloaded.editionNumber()).isNull();
    assertThat(reloaded.publishedYearHijri()).isEqualTo(1430);
    assertThat(reloaded.publishedYearGregorian()).isNull();
}

@Test
void save_editionNumberZero_violatesCheck() {
    Book bad = new Book(
            UUID.randomUUID(), BookType.BOOK, "bad edition",
            null, "ar", null, null, userId, Instant.now(), Instant.now(),
            null, null, null, 0, null, null
    );

    assertThatThrownBy(() -> bookRepository.save(bad))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
}

@Test
void save_publishedYearGregorianTooLarge_violatesCheck() {
    Book bad = new Book(
            UUID.randomUUID(), BookType.BOOK, "future book",
            null, "ar", null, null, userId, Instant.now(), Instant.now(),
            null, null, null, null, null, 2500
    );

    assertThatThrownBy(() -> bookRepository.save(bad))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
}
```

Добавить импорт `import static org.assertj.core.api.Assertions.assertThatThrownBy;` в начало файла.

- [ ] **Step 5: Запустить BookRepositoryIT**

```bash
cd backend && ./mvnw -Dtest='BookRepositoryIT' -DfailIfNoTests=false test
```

Expected: все тесты pass (старые + 4 новых).

- [ ] **Step 6: Обновить BookSummaryResponse и BookDetailResponse**

В `backend/src/main/java/ru/basnukaev/argumentmap/library/web/dto/BookDetailResponse.java` добавить 6 полей в конец record. Если поля не нужны на summary - оставить BookSummaryResponse и BookResponse как есть. Проверить:

```bash
cd backend && grep -l "record Book.*Response" src/main/java/ru/basnukaev/argumentmap/library/web/dto/
```

Открыть `BookDetailResponse.java` и добавить:

```java
        // ADR-028 academic citation metadata
        UUID muhaqqiqId,
        UUID publisherId,
        UUID publicationPlaceId,
        Integer editionNumber,
        Integer publishedYearHijri,
        Integer publishedYearGregorian
```

- [ ] **Step 7: Обновить LibraryDtoMappers**

В `LibraryDtoMappers.toBookDetailResponse(...)` (или эквивалентный method) добавить 6 параметров в конструктор BookDetailResponse. Проверить файл сначала чтобы найти точный method:

```bash
cd backend && grep -n "BookDetailResponse" src/main/java/ru/basnukaev/argumentmap/library/web/mapper/LibraryDtoMappers.java
```

Добавить в конец конструктора: `book.muhaqqiqId(), book.publisherId(), book.publicationPlaceId(), book.editionNumber(), book.publishedYearHijri(), book.publishedYearGregorian()`.

- [ ] **Step 8: Compile проверка**

```bash
cd backend && ./mvnw -DskipTests compile
```

Expected: BUILD SUCCESS. Если есть ошибки про `new Book(...)` в других местах - добавить 6 null'ов в эти call sites.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/library/domain/Book.java \
        backend/src/main/java/ru/basnukaev/argumentmap/library/repository/BookRepository.java \
        backend/src/main/java/ru/basnukaev/argumentmap/library/web/dto/BookDetailResponse.java \
        backend/src/main/java/ru/basnukaev/argumentmap/library/web/mapper/LibraryDtoMappers.java \
        backend/src/test/java/ru/basnukaev/argumentmap/library/repository/BookRepositoryIT.java \
        backend/src/main/java/ru/basnukaev/argumentmap/library/shamela/service/mapper \
        backend/src/main/java/ru/basnukaev/argumentmap/library/service \
        backend/src/test/java/ru/basnukaev/argumentmap/library
git commit -m "feat(backend): этап 20.a - расширение Book + BookRepository

Book record + 6 полей: muhaqqiqId, publisherId, publicationPlaceId,
editionNumber, publishedYearHijri, publishedYearGregorian (ADR-028).
BookRepository.COLUMNS / ROW_MAPPER / save обновлены.
BookDetailResponse + LibraryDtoMappers расширены.
4 новых IT - full / partial / CHECK violations.
Обновлены call sites new Book(...) в ShamelaToLibraryMapper и тестах
(добавлены 6 null'ов в конец конструктора - new fields не заполняются
до Этапа 20.c bibliography parser)."
```

---

### Task 7: CitationDetail + NodeSourceRepository SQL переписан

**Files:**
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/domain/CitationDetail.java`
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/repository/NodeSourceRepository.java` (NodeSourceWithLocation record + JOIN SQL + RowMapper)
- Modify: `backend/src/test/java/ru/basnukaev/argumentmap/repository/NodeSourceRepositoryIT.java` (+ 5 IT)

- [ ] **Step 1: Создать CitationDetail record**

```java
package ru.basnukaev.argumentmap.domain;

import java.util.UUID;

public record CitationDetail(
        UUID authorityId,
        String authorityName,
        String authorFullName,
        Integer authorDeathYearHijri,

        UUID bookId,
        String bookTitle,
        String bookLanguage,

        UUID muhaqqiqId,
        String muhaqqiqName,
        String muhaqqiqFullName,

        UUID publisherId,
        String publisherName,
        UUID publicationPlaceId,
        String publicationPlaceName,
        Integer editionNumber,
        Integer publishedYearHijri,
        Integer publishedYearGregorian,

        UUID pageId,
        String part,
        String printedPage,
        Integer pageNumber,
        Integer rangeStart,
        Integer rangeEnd,

        UUID pdfFileId,
        Integer pdfPageNumber,
        String pdfBbox,

        UUID imageRegionId,
        Integer regionPrintedPage,
        Integer regionPageNumber
) {
}
```

- [ ] **Step 2: Переписать NodeSourceRepository.NodeSourceWithLocation + JOIN_LOCATION_SQL + маппер**

В `backend/src/main/java/ru/basnukaev/argumentmap/repository/NodeSourceRepository.java`:

1. Заменить nested record:

```java
public record NodeSourceWithLocation(NodeSource ns, CitationDetail citation) {
}
```

(Старая версия `(NodeSource ns, String computedLocation, UUID bookId)` уходит)

2. Заменить `JOIN_LOCATION_SQL` константу + `prefixedColumns()`:

```java
private static final String JOIN_LOCATION_SQL = """
        SELECT %COLS%,
          s.book_id AS src_book_id,
          a.id AS authority_id,
          a.name AS authority_name,
          a.full_name AS author_full_name,
          a.death_year_hijri AS author_death_year_hijri,
          b.title AS book_title,
          b.language AS book_language,
          b.edition_number,
          b.published_year_hijri,
          b.published_year_gregorian,
          mh.id AS muhaqqiq_id,
          mh.name AS muhaqqiq_name,
          mh.full_name AS muhaqqiq_full_name,
          pub.id AS publisher_id,
          pub.name AS publisher_name,
          pl.id AS publication_place_id,
          pl.name AS publication_place_name,
          p.part AS page_part,
          p.printed_page AS page_printed_page,
          p.page_number AS page_page_number,
          p2.printed_page AS region_printed_page,
          p2.page_number AS region_page_number
        FROM node_sources ns
        LEFT JOIN sources s                   ON s.id = ns.source_id
        LEFT JOIN lib_books b                 ON b.id = s.book_id
        LEFT JOIN authorities a               ON a.id = b.authority_id
        LEFT JOIN lib_muhaqqiqs mh            ON mh.id = b.muhaqqiq_id
        LEFT JOIN lib_publishers pub          ON pub.id = b.publisher_id
        LEFT JOIN lib_publication_places pl   ON pl.id = b.publication_place_id
        LEFT JOIN lib_pages p                 ON p.id = ns.page_id
        LEFT JOIN lib_image_regions ir        ON ir.id = ns.image_region_id
        LEFT JOIN lib_pages p2                ON p2.id = ir.page_id
        """.replace("%COLS%", prefixedColumns());
```

3. Добавить helper `citationFromRow`:

```java
private static CitationDetail citationFromRow(java.sql.ResultSet rs) throws java.sql.SQLException {
    int edition = rs.getInt("edition_number");
    Integer editionOrNull = rs.wasNull() ? null : edition;
    int yearH = rs.getInt("published_year_hijri");
    Integer yearHOrNull = rs.wasNull() ? null : yearH;
    int yearG = rs.getInt("published_year_gregorian");
    Integer yearGOrNull = rs.wasNull() ? null : yearG;
    int deathY = rs.getInt("author_death_year_hijri");
    Integer deathYOrNull = rs.wasNull() ? null : deathY;
    int pageNum = rs.getInt("page_page_number");
    Integer pageNumOrNull = rs.wasNull() ? null : pageNum;
    int rangeStart = rs.getInt("range_start");
    Integer rangeStartOrNull = rs.wasNull() ? null : rangeStart;
    int rangeEnd = rs.getInt("range_end");
    Integer rangeEndOrNull = rs.wasNull() ? null : rangeEnd;
    int pdfPage = rs.getInt("pdf_page_number");
    Integer pdfPageOrNull = rs.wasNull() ? null : pdfPage;
    int regPrinted = rs.getInt("region_printed_page");
    Integer regPrintedOrNull = rs.wasNull() ? null : regPrinted;
    int regPage = rs.getInt("region_page_number");
    Integer regPageOrNull = rs.wasNull() ? null : regPage;

    return new CitationDetail(
            rs.getObject("authority_id", UUID.class),
            rs.getString("authority_name"),
            rs.getString("author_full_name"),
            deathYOrNull,

            rs.getObject("src_book_id", UUID.class),
            rs.getString("book_title"),
            rs.getString("book_language"),

            rs.getObject("muhaqqiq_id", UUID.class),
            rs.getString("muhaqqiq_name"),
            rs.getString("muhaqqiq_full_name"),

            rs.getObject("publisher_id", UUID.class),
            rs.getString("publisher_name"),
            rs.getObject("publication_place_id", UUID.class),
            rs.getString("publication_place_name"),
            editionOrNull,
            yearHOrNull,
            yearGOrNull,

            rs.getObject("page_id", UUID.class),
            rs.getString("page_part"),
            rs.getString("page_printed_page"),
            pageNumOrNull,
            rangeStartOrNull,
            rangeEndOrNull,

            rs.getObject("pdf_file_id", UUID.class),
            pdfPageOrNull,
            rs.getString("pdf_bbox"),

            rs.getObject("image_region_id", UUID.class),
            regPrintedOrNull,
            regPageOrNull
    );
}
```

4. Заменить `findByNodeIdWithLocation` и `findByPkWithLocation`:

```java
public List<NodeSourceWithLocation> findByNodeIdWithLocation(UUID nodeId) {
    return jdbcTemplate.query(
            JOIN_LOCATION_SQL + " WHERE ns.node_id = ? ORDER BY ns.created_at",
            (rs, rn) -> new NodeSourceWithLocation(
                    ROW_MAPPER.mapRow(rs, rn),
                    citationFromRow(rs)
            ),
            nodeId
    );
}

public Optional<NodeSourceWithLocation> findByPkWithLocation(UUID nodeId, UUID sourceId) {
    return jdbcTemplate.query(
            JOIN_LOCATION_SQL + " WHERE ns.node_id = ? AND ns.source_id = ?",
            (rs, rn) -> new NodeSourceWithLocation(
                    ROW_MAPPER.mapRow(rs, rn),
                    citationFromRow(rs)
            ),
            nodeId, sourceId
    ).stream().findFirst();
}
```

(Заметка: `ROW_MAPPER` для `NodeSource` использует `node_id, source_id, ...` без префикса - SQL уже их selectit как `ns.node_id AS node_id` через `prefixedColumns()`. Проверь, что generated alias не конфликтует - если есть конфликт, добавить `AS` пересмотр в `prefixedColumns()`. Существующий код работал - значит alias уже OK.)

- [ ] **Step 3: Добавить импорт CitationDetail в NodeSourceRepository**

```java
import ru.basnukaev.argumentmap.domain.CitationDetail;
```

- [ ] **Step 4: Compile - проверить что DtoMappers.toResponse(NodeSourceWithLocation) теперь не компилится**

```bash
cd backend && ./mvnw -DskipTests compile 2>&1 | grep -E "ERROR|computedLocation|bookId"
```

Expected: ошибка в `DtoMappers.toResponse(NodeSourceWithLocation row)` - старые `row.computedLocation()` и `row.bookId()` больше не существуют. Это ожидаемо - починим в Task 8.

- [ ] **Step 5: Закомментировать broken DtoMappers метод временно**

В `backend/src/main/java/ru/basnukaev/argumentmap/web/mapper/DtoMappers.java` строки 122-141 (метод `toResponse(NodeSourceWithLocation row)`) - закомментировать, чтобы compile прошёл для запуска IT. В Task 8 удалим временную заглушку и напишем правильный mapper.

Заменить блок 122-141 на:

```java
// TEMP: Old mapper using string computedLocation - replaced in Task 8 by
// structured CitationResponse mapping. Commented out to unblock Task 7 compile.
// public static NodeSourceResponse toResponse(NodeSourceRepository.NodeSourceWithLocation row) { ... }
```

И в `NodeSourceController.list` строка 44 заменить:

```java
@GetMapping
public List<NodeSourceResponse> list(@PathVariable UUID nodeId) {
    // TEMP: structured citation mapping - implemented in Task 8
    return nodeSourceService.getNodeSources(nodeId).stream()
            .map(DtoMappers::toResponse).toList();
}
```

(Это временный fallback на `getNodeSources` без JOIN - вернём `getNodeSourcesWithLocation` в Task 8.)

- [ ] **Step 6: Добавить 5 IT в NodeSourceRepositoryIT**

В `backend/src/test/java/ru/basnukaev/argumentmap/repository/NodeSourceRepositoryIT.java` добавить (используя existing helpers `insertSource`, `insertNode` и т.п.):

```java
@Test
void findByNodeIdWithLocation_fullAcademicData_returnsAllFields() {
    UUID muhaqqiqId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lib_muhaqqiqs (id, name, full_name) VALUES (?, ?, ?)",
            muhaqqiqId, "السلامة", "سامي بن محمد السلامة"
    );
    UUID publisherId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lib_publishers (id, name) VALUES (?, ?)",
            publisherId, "Дар Тайба"
    );
    UUID placeId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lib_publication_places (id, name) VALUES (?, ?)",
            placeId, "Эр-Рияд"
    );
    UUID authorityId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO authorities (id, name, full_name, death_year_hijri, created_at) "
                    + "VALUES (?, ?, ?, ?, now())",
            authorityId, "ابن كثير", "إسماعيل بن عمر بن كثير الدمشقي", 774
    );
    UUID bookId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lib_books (id, book_type, title, authority_id, language, created_by, "
                    + "muhaqqiq_id, publisher_id, publication_place_id, "
                    + "edition_number, published_year_hijri, published_year_gregorian) "
                    + "VALUES (?, 'BOOK', ?, ?, 'ar', ?, ?, ?, ?, ?, ?, ?)",
            bookId, "تفسير القرآن العظيم", authorityId, userId,
            muhaqqiqId, publisherId, placeId, 2, 1420, 1999
    );
    UUID pageId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lib_pages (id, book_id, page_number, text_content, printed_page, part) "
                    + "VALUES (?, ?, ?, 'page text', ?, ?)",
            pageId, bookId, 1, "145", "1"
    );
    UUID sourceWithBookId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO sources (id, source_type, title, citation, book_id) "
                    + "VALUES (?, 'BOOK', ?, 'short', ?)",
            sourceWithBookId, "Тафсир", bookId
    );
    NodeSource link = NodeSource.textMode(
            nodeId, sourceWithBookId, "цитата", "контекст", "snapshot location",
            pageId, 100, 200, Instant.now()
    );
    nodeSourceRepository.save(link);

    var rows = nodeSourceRepository.findByNodeIdWithLocation(nodeId);

    assertThat(rows).hasSize(1);
    var c = rows.get(0).citation();
    assertThat(c.authorityId()).isEqualTo(authorityId);
    assertThat(c.authorFullName()).isEqualTo("إسماعيل بن عمر بن كثير الدمشقي");
    assertThat(c.authorDeathYearHijri()).isEqualTo(774);
    assertThat(c.bookTitle()).isEqualTo("تفسير القرآن العظيم");
    assertThat(c.muhaqqiqName()).isEqualTo("السلامة");
    assertThat(c.muhaqqiqFullName()).isEqualTo("سامي بن محمد السلامة");
    assertThat(c.publisherName()).isEqualTo("Дар Тайба");
    assertThat(c.publicationPlaceName()).isEqualTo("Эр-Рияд");
    assertThat(c.editionNumber()).isEqualTo(2);
    assertThat(c.publishedYearHijri()).isEqualTo(1420);
    assertThat(c.publishedYearGregorian()).isEqualTo(1999);
    assertThat(c.part()).isEqualTo("1");
    assertThat(c.printedPage()).isEqualTo("145");
}

@Test
void findByNodeIdWithLocation_partialAcademicData_returnsNullsForMissing() {
    UUID publisherId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lib_publishers (id, name) VALUES (?, ?)",
            publisherId, "Дар аль-Фикр"
    );
    UUID bookId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lib_books (id, book_type, title, language, created_by, publisher_id) "
                    + "VALUES (?, 'BOOK', ?, 'ar', ?, ?)",
            bookId, "Книга без полной инфы", userId, publisherId
    );
    UUID pageId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lib_pages (id, book_id, page_number, text_content) "
                    + "VALUES (?, ?, 1, 'text')",
            pageId, bookId
    );
    UUID srcId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO sources (id, source_type, title, citation, book_id) "
                    + "VALUES (?, 'BOOK', 'src', 'cit', ?)",
            srcId, bookId
    );
    nodeSourceRepository.save(NodeSource.textMode(
            nodeId, srcId, "q", "c", "loc", pageId, null, null, Instant.now()
    ));

    var c = nodeSourceRepository.findByNodeIdWithLocation(nodeId).get(0).citation();

    assertThat(c.publisherName()).isEqualTo("Дар аль-Фикр");
    assertThat(c.muhaqqiqId()).isNull();
    assertThat(c.muhaqqiqName()).isNull();
    assertThat(c.publicationPlaceId()).isNull();
    assertThat(c.editionNumber()).isNull();
    assertThat(c.authorityId()).isNull();
    assertThat(c.authorFullName()).isNull();
}

@Test
void findByNodeIdWithLocation_sourceWithoutBook_returnsCitationWithNulls() {
    UUID srcId = insertSource();   // existing helper - source без book_id
    nodeSourceRepository.save(NodeSource.legacyMode(
            nodeId, srcId, "q", "c", "стр. 42", Instant.now()
    ));

    var c = nodeSourceRepository.findByNodeIdWithLocation(nodeId).get(0).citation();

    assertThat(c.bookId()).isNull();
    assertThat(c.bookTitle()).isNull();
    assertThat(c.publisherName()).isNull();
    assertThat(c.authorityId()).isNull();
    assertThat(c.pageId()).isNull();
}

@Test
void findByNodeIdWithLocation_pdfMode_populatesPdfFields() {
    UUID srcId = insertSource();
    UUID pdfFileId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO library_files (id, source_type, content_hash, bucket, storage_key, "
                    + "size_bytes, content_type, content_subtype) "
                    + "VALUES (?, 'PDF_LINK', 'hashval', 'library-imported-books', 'k', 1, 'application/pdf', 'IMPORTED')",
            pdfFileId
    );
    nodeSourceRepository.save(NodeSource.pdfMode(
            nodeId, srcId, "q", "c", "PDF стр.50",
            pdfFileId, 50, null, Instant.now()
    ));

    var c = nodeSourceRepository.findByNodeIdWithLocation(nodeId).get(0).citation();

    assertThat(c.pdfFileId()).isEqualTo(pdfFileId);
    assertThat(c.pdfPageNumber()).isEqualTo(50);
    assertThat(c.pageId()).isNull();
    assertThat(c.imageRegionId()).isNull();
}

@Test
void findByNodeIdWithLocation_regionMode_populatesRegionAndPagePrintedNumber() {
    UUID bookId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lib_books (id, book_type, title, language, created_by) "
                    + "VALUES (?, 'BOOK', 'title', 'ar', ?)",
            bookId, userId
    );
    UUID pageId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lib_pages (id, book_id, page_number, image_url, printed_page) "
                    + "VALUES (?, ?, 7, 'img.url', '13')",
            pageId, bookId
    );
    UUID regionId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lib_image_regions (id, page_id, x, y, width, height) "
                    + "VALUES (?, ?, 0.1, 0.1, 0.3, 0.2)",
            regionId, pageId
    );
    UUID srcId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO sources (id, source_type, title, citation, book_id) "
                    + "VALUES (?, 'BOOK', 'src', 'cit', ?)",
            srcId, bookId
    );
    nodeSourceRepository.save(NodeSource.regionMode(
            nodeId, srcId, "q", "c", "region loc", regionId, Instant.now()
    ));

    var c = nodeSourceRepository.findByNodeIdWithLocation(nodeId).get(0).citation();

    assertThat(c.imageRegionId()).isEqualTo(regionId);
    assertThat(c.regionPrintedPage()).isEqualTo(13);
    assertThat(c.regionPageNumber()).isEqualTo(7);
    assertThat(c.pageId()).isNull();
}
```

Если в helper'е `insertSource()` создаётся source без `book_id`, то тест `_sourceWithoutBook` корректен. Проверить:

```bash
grep -n "insertSource\|INSERT INTO sources" backend/src/test/java/ru/basnukaev/argumentmap/repository/NodeSourceRepositoryIT.java
```

- [ ] **Step 7: Запустить NodeSourceRepositoryIT**

```bash
cd backend && ./mvnw -Dtest='NodeSourceRepositoryIT' -DfailIfNoTests=false test
```

Expected: все тесты pass (старые + 5 новых).

Если падает по `regPrintedOrNull` - проверить что `printed_page` в `lib_pages` это TEXT а не INTEGER. Если TEXT - заменить в `citationFromRow` `rs.getInt("region_printed_page")` на `rs.getString` и тип в `CitationDetail.regionPrintedPage` на `String`. Проверить миграцию 19:

```bash
grep "printed_page" backend/src/main/resources/db/changelog/changes/20260511-19-add-printed-page-and-part-to-lib-pages.xml
```

Поправить тип CitationDetail.regionPrintedPage если SQL тип TEXT - вернуться в Step 1 и поправить record. (Spec уверен что `printed_page TEXT`, но verify.)

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/domain/CitationDetail.java \
        backend/src/main/java/ru/basnukaev/argumentmap/repository/NodeSourceRepository.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/mapper/DtoMappers.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/controller/NodeSourceController.java \
        backend/src/test/java/ru/basnukaev/argumentmap/repository/NodeSourceRepositoryIT.java
git commit -m "feat(backend): этап 20.a - CitationDetail + NodeSourceRepository SQL 9 JOIN

CitationDetail record (27 raw fields) - structured citation вместо
склеенной computed_location string. NodeSourceWithLocation теперь содержит
ns + citation (старые computedLocation/bookId заменены на citation.bookId).
findByNodeIdWithLocation переписан с 9 LEFT JOIN: sources, lib_books,
authorities, lib_muhaqqiqs, lib_publishers, lib_publication_places,
lib_pages (TEXT mode), lib_image_regions + lib_pages-as-p2 (REGION mode).

DtoMappers.toResponse(NodeSourceWithLocation) временно закомментирован,
NodeSourceController.list временно использует getNodeSources без JOIN -
правильный mapper и controller в Task 8 (CitationResponse DTO layer).

5 новых IT - full academic data, partial, source без book, PDF mode, REGION mode."
```

---

### Task 8: DTO layer - CitationResponse + 7 nested refs + NodeSourceResponse rework

**Files:**
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/CitationResponse.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/AuthorityCitationRef.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/BookCitationRef.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/MuhaqqiqRef.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/PublisherRef.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/PublicationPlaceRef.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/LocationRef.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/PdfRef.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/RegionRef.java`
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/NodeSourceResponse.java` (рефактор)
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/web/mapper/DtoMappers.java`
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/web/controller/NodeSourceController.java` (откат TEMP)
- Modify: `backend/src/test/java/ru/basnukaev/argumentmap/web/controller/NodeSourceControllerIT.java` (+ 2 IT)

- [ ] **Step 1: Создать 8 nested ref records и CitationResponse**

`AuthorityCitationRef.java`:
```java
package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

public record AuthorityCitationRef(
        UUID id,
        String name,
        String fullName,
        Integer deathYearHijri
) {
}
```

`BookCitationRef.java`:
```java
package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

public record BookCitationRef(
        UUID id,
        String title,
        String language,
        Integer editionNumber,
        Integer publishedYearHijri,
        Integer publishedYearGregorian
) {
}
```

`MuhaqqiqRef.java`:
```java
package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

public record MuhaqqiqRef(
        UUID id,
        String name,
        String fullName
) {
}
```

`PublisherRef.java`:
```java
package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

public record PublisherRef(
        UUID id,
        String name
) {
}
```

`PublicationPlaceRef.java`:
```java
package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

public record PublicationPlaceRef(
        UUID id,
        String name
) {
}
```

`LocationRef.java`:
```java
package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

public record LocationRef(
        UUID pageId,
        String part,
        String printedPage,
        Integer pageNumber,
        Integer rangeStart,
        Integer rangeEnd
) {
}
```

`PdfRef.java`:
```java
package ru.basnukaev.argumentmap.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record PdfRef(
        UUID fileId,
        Integer pageNumber,
        JsonNode bbox
) {
}
```

`RegionRef.java`:
```java
package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

public record RegionRef(
        UUID id,
        Integer printedPage,
        Integer pageNumber
) {
}
```

`CitationResponse.java`:
```java
package ru.basnukaev.argumentmap.web.dto;

public record CitationResponse(
        AuthorityCitationRef authority,
        BookCitationRef book,
        MuhaqqiqRef muhaqqiq,
        PublisherRef publisher,
        PublicationPlaceRef publicationPlace,
        LocationRef location,
        PdfRef pdf,
        RegionRef region
) {
}
```

- [ ] **Step 2: Рефакторить NodeSourceResponse**

Заменить `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/NodeSourceResponse.java`:

```java
package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.UUID;

import ru.basnukaev.argumentmap.domain.CitationMode;

/**
 * Response с structured citation (ADR-028). Поля location/bookId старого
 * формата заменены на nested CitationResponse - frontend рендерит каждое
 * поле в своём блоке для правильного RTL/наskh.
 */
public record NodeSourceResponse(
        UUID nodeId,
        UUID sourceId,
        String quote,
        String context,
        CitationMode mode,
        CitationResponse citation,
        Instant createdAt
) {
}
```

(Старые поля `location`, `pageId`, `rangeStart`, `rangeEnd`, `pdfFileId`, `pdfPageNumber`, `pdfBbox`, `imageRegionId`, `bookId` уходят - все они теперь в `citation`. Они **дублировались** раньше - теперь источник истины только в `citation`.)

- [ ] **Step 3: Обновить DtoMappers**

В `backend/src/main/java/ru/basnukaev/argumentmap/web/mapper/DtoMappers.java`:

1. Удалить старый закомментированный `toResponse(NodeSourceRepository.NodeSourceWithLocation row)` (TEMP из Task 7).

2. Удалить старый `toResponse(NodeSource link)` (теперь без computed citation он бессмысленный).

3. Добавить новые мапперы:

```java
public static CitationResponse toCitationResponse(CitationDetail c) {
    return new CitationResponse(
            toAuthorityRef(c),
            toBookRef(c),
            toMuhaqqiqRef(c),
            toPublisherRef(c),
            toPublicationPlaceRef(c),
            toLocationRef(c),
            toPdfRef(c),
            toRegionRef(c)
    );
}

private static AuthorityCitationRef toAuthorityRef(CitationDetail c) {
    if (c.authorityId() == null) return null;
    return new AuthorityCitationRef(
            c.authorityId(), c.authorityName(),
            c.authorFullName(), c.authorDeathYearHijri()
    );
}

private static BookCitationRef toBookRef(CitationDetail c) {
    if (c.bookId() == null) return null;
    return new BookCitationRef(
            c.bookId(), c.bookTitle(), c.bookLanguage(),
            c.editionNumber(), c.publishedYearHijri(), c.publishedYearGregorian()
    );
}

private static MuhaqqiqRef toMuhaqqiqRef(CitationDetail c) {
    if (c.muhaqqiqId() == null) return null;
    return new MuhaqqiqRef(c.muhaqqiqId(), c.muhaqqiqName(), c.muhaqqiqFullName());
}

private static PublisherRef toPublisherRef(CitationDetail c) {
    if (c.publisherId() == null) return null;
    return new PublisherRef(c.publisherId(), c.publisherName());
}

private static PublicationPlaceRef toPublicationPlaceRef(CitationDetail c) {
    if (c.publicationPlaceId() == null) return null;
    return new PublicationPlaceRef(c.publicationPlaceId(), c.publicationPlaceName());
}

private static LocationRef toLocationRef(CitationDetail c) {
    if (c.pageId() == null) return null;
    return new LocationRef(
            c.pageId(), c.part(), c.printedPage(),
            c.pageNumber(), c.rangeStart(), c.rangeEnd()
    );
}

private static PdfRef toPdfRef(CitationDetail c) {
    if (c.pdfFileId() == null) return null;
    return new PdfRef(c.pdfFileId(), c.pdfPageNumber(), jsonFromString(c.pdfBbox()));
}

private static RegionRef toRegionRef(CitationDetail c) {
    if (c.imageRegionId() == null) return null;
    return new RegionRef(c.imageRegionId(), c.regionPrintedPage(), c.regionPageNumber());
}

public static NodeSourceResponse toResponse(NodeSourceRepository.NodeSourceWithLocation row) {
    NodeSource link = row.ns();
    return new NodeSourceResponse(
            link.nodeId(),
            link.sourceId(),
            link.quote(),
            link.context(),
            link.mode(),
            toCitationResponse(row.citation()),
            link.createdAt()
    );
}
```

Импорты добавить в начало файла:
```java
import ru.basnukaev.argumentmap.domain.CitationDetail;
import ru.basnukaev.argumentmap.web.dto.CitationResponse;
import ru.basnukaev.argumentmap.web.dto.AuthorityCitationRef;
import ru.basnukaev.argumentmap.web.dto.BookCitationRef;
import ru.basnukaev.argumentmap.web.dto.MuhaqqiqRef;
import ru.basnukaev.argumentmap.web.dto.PublisherRef;
import ru.basnukaev.argumentmap.web.dto.PublicationPlaceRef;
import ru.basnukaev.argumentmap.web.dto.LocationRef;
import ru.basnukaev.argumentmap.web.dto.PdfRef;
import ru.basnukaev.argumentmap.web.dto.RegionRef;
```

- [ ] **Step 4: Восстановить NodeSourceController.list**

В `backend/src/main/java/ru/basnukaev/argumentmap/web/controller/NodeSourceController.java` строки 42-46 заменить:

```java
@GetMapping
public List<NodeSourceResponse> list(@PathVariable UUID nodeId) {
    return nodeSourceService.getNodeSourcesWithLocation(nodeId).stream()
            .map(DtoMappers::toResponse).toList();
}
```

Также проверить `attach` метод - там используется `DtoMappers.toResponse(NodeSource link)` который мы удалили. Заменить на маппер через service:

В `NodeSourceController.attach`:
```java
@PostMapping
public ResponseEntity<NodeSourceResponse> attach(@PathVariable UUID nodeId,
                                                 @Valid @RequestBody AttachSourceRequest request) {
    nodeSourceService.attachSource(
            nodeId, request.sourceId(), request.quote(), request.context(), request.location()
    );
    NodeSourceResponse response = nodeSourceService
            .getNodeSourcesWithLocation(nodeId).stream()
            .filter(r -> r.ns().sourceId().equals(request.sourceId()))
            .findFirst()
            .map(DtoMappers::toResponse)
            .orElseThrow();
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

(Альтернатива: добавить `findByPkWithLocation` use в Service. Implement straight через filter для простоты.)

- [ ] **Step 5: Compile проверка**

```bash
cd backend && ./mvnw -DskipTests compile
```

Expected: BUILD SUCCESS. Если есть ошибки - найти их (вероятно в `BookControllerIT` или местах где старые поля `NodeSourceResponse.location()` используются) и поправить. Существующие тесты `NodeSourceControllerIT` могут читать `response.location()` - заменить на `response.citation()...`.

- [ ] **Step 6: Обновить NodeSourceControllerIT существующие тесты + добавить 2 новых**

Найти существующие тесты controller:
```bash
ls backend/src/test/java/ru/basnukaev/argumentmap/web/controller/NodeSourceControllerIT.java 2>/dev/null || ls backend/src/test/java/ru/basnukaev/argumentmap/web -name "*NodeSource*"
```

Если есть - открыть и обновить assertions на старые поля (location/pageId etc.) - использовать `.citation.book.title` / `.citation.location.printedPage` JSONPath.

Добавить 2 IT в конец class:

```java
@Test
void list_returnsStructuredCitationForAcademicBook() throws Exception {
    // setup: создать book с full academic data + page + node + source + node_source
    // (используя test setup helpers или прямые JdbcTemplate inserts)
    UUID muhaqqiqId = UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO lib_muhaqqiqs (id, name, full_name) VALUES (?, ?, ?)",
            muhaqqiqId, "السلامة", "سامي بن محمد السلامة");
    UUID publisherId = UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO lib_publishers (id, name) VALUES (?, ?)",
            publisherId, "Дар Тайба");
    UUID authorityId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO authorities (id, name, full_name, death_year_hijri, created_at) "
                    + "VALUES (?, 'ابن كثير', 'إسماعيل بن عمر بن كثير الدمشقي', 774, now())",
            authorityId
    );
    UUID bookId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lib_books (id, book_type, title, authority_id, language, created_by, "
                    + "muhaqqiq_id, publisher_id, edition_number, published_year_hijri, published_year_gregorian) "
                    + "VALUES (?, 'BOOK', 'تفسير القرآن العظيم', ?, 'ar', ?, ?, ?, 2, 1420, 1999)",
            bookId, authorityId, userId, muhaqqiqId, publisherId
    );
    UUID pageId = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lib_pages (id, book_id, page_number, text_content, printed_page, part) "
                    + "VALUES (?, ?, 1, 'text', '145', '1')",
            pageId, bookId
    );
    UUID srcId = UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO sources (id, source_type, title, citation, book_id) "
            + "VALUES (?, 'BOOK', 'src', 'c', ?)", srcId, bookId);
    jdbcTemplate.update("INSERT INTO node_sources (node_id, source_id, quote, context, location, page_id, range_start, range_end, created_at) "
            + "VALUES (?, ?, 'q', 'c', 'loc', ?, 0, 0, now())", nodeId, srcId, pageId);

    mockMvc.perform(get("/api/v1/nodes/{nodeId}/sources", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].citation.authority.fullName")
                    .value("إسماعيل بن عمر بن كثير الدمشقي"))
            .andExpect(jsonPath("$[0].citation.authority.deathYearHijri").value(774))
            .andExpect(jsonPath("$[0].citation.book.title").value("تفسير القرآن العظيم"))
            .andExpect(jsonPath("$[0].citation.book.editionNumber").value(2))
            .andExpect(jsonPath("$[0].citation.book.publishedYearHijri").value(1420))
            .andExpect(jsonPath("$[0].citation.muhaqqiq.fullName").value("سامي بن محمد السلامة"))
            .andExpect(jsonPath("$[0].citation.publisher.name").value("Дар Тайба"))
            .andExpect(jsonPath("$[0].citation.location.part").value("1"))
            .andExpect(jsonPath("$[0].citation.location.printedPage").value("145"));
}

@Test
void list_returnsNullNestedRefsForSourceWithoutBook() throws Exception {
    UUID srcId = UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO sources (id, source_type, title, citation) "
            + "VALUES (?, 'URL', 'src', 'c')", srcId);
    jdbcTemplate.update("INSERT INTO node_sources (node_id, source_id, quote, context, location, created_at) "
            + "VALUES (?, ?, 'q', 'c', 'снепшот', now())", nodeId, srcId);

    mockMvc.perform(get("/api/v1/nodes/{nodeId}/sources", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].citation.book").doesNotExist())
            .andExpect(jsonPath("$[0].citation.authority").doesNotExist())
            .andExpect(jsonPath("$[0].citation.publisher").doesNotExist())
            .andExpect(jsonPath("$[0].citation.location").doesNotExist());
}
```

(Заметка: если jsonPath `.doesNotExist()` неудобен из-за Jackson сериализующего null nested - использовать `.isEmpty()` или `value(null)`. Адаптировать по факту.)

Если NodeSourceControllerIT не существует - проверить:
```bash
find backend/src/test -name "*NodeSource*IT.java"
```

Если controller IT нет - создать новый файл по образцу `BookControllerIT.java`.

- [ ] **Step 7: Запустить controller IT**

```bash
cd backend && ./mvnw -Dtest='NodeSourceControllerIT' -DfailIfNoTests=false test
```

Expected: pass. Если падает из-за `attach` где `DtoMappers.toResponse(NodeSource)` уже удалён - адаптировать. Если падает из-за jsonPath - адаптировать assertions.

- [ ] **Step 8: Compile + полный verify**

```bash
cd backend && ./mvnw verify
```

Expected: BUILD SUCCESS, все тесты pass. Если падают шамела/library IT из-за `new Book(...)` без 6 null'ов - поправить call sites.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/web/dto/CitationResponse.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/dto/AuthorityCitationRef.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/dto/BookCitationRef.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/dto/MuhaqqiqRef.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/dto/PublisherRef.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/dto/PublicationPlaceRef.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/dto/LocationRef.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/dto/PdfRef.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/dto/RegionRef.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/dto/NodeSourceResponse.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/mapper/DtoMappers.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/controller/NodeSourceController.java \
        backend/src/test/java/ru/basnukaev/argumentmap/web/controller/NodeSourceControllerIT.java
git commit -m "feat(backend): этап 20.a - CitationResponse + 8 nested ref DTO

CitationResponse top-level: authority / book / muhaqqiq / publisher /
publicationPlace / location / pdf / region - все nullable nested ref.
8 ref records создают чёткие визуальные блоки для фронта (см. ADR-028
spec про rendering strategy).

NodeSourceResponse рефакторен - старые поля location/pageId/rangeStart/
rangeEnd/pdfFileId/pdfPageNumber/pdfBbox/imageRegionId/bookId уходят в
nested citation, нет дублирования источников истины.

DtoMappers.toCitationResponse(CitationDetail) + 8 private to*Ref(...)
помощников - каждый возвращает null если соответствующий FK не задан.

NodeSourceController.attach использует findByPkWithLocation для построения
полного response. list восстановлен на getNodeSourcesWithLocation.

2 новых NodeSourceController IT - full academic citation в response и
null nested refs для source без book.

Frontend (TopicGraphPage / NodeCitationsSection / CitationsList) ломается
при regenerate-api - починим в подэтапе 20.f следующей сессии."
```

---

### Task 9: Documentation - ADR-028 + 4 updates

**Files:**
- Modify: `docs/decisions.md` (новый ADR-028 в конец перед roadmap-style секциями)
- Modify: `docs/architecture.md`
- Modify: `docs/api-contract.md`
- Modify: `docs/glossary.md`
- Modify: `docs/roadmap.md`

- [ ] **Step 1: Добавить ADR-028 в decisions.md**

Открыть `docs/decisions.md`, найти последний ADR (ADR-027 на строке ~2237), добавить после него:

```markdown
## ADR-028: Academic citation metadata - нормализованный middle path

**Дата:** 2026-05-14
**Статус:** принят
**Связь:** ADR-017 (Source+Authority unification), ADR-018 (platform pivot), ADR-019 (library domain), ADR-026 (Source.bookId), ADR-027 (positional citation)

### Контекст

Текущая schema `lib_books` (`title, authority_id, language, description, metadata JSONB`)
не покрывает академическую сноску исламского `бахс` (научное исследование). Для
бахс-grade citation требуется минимум 8 полей:
- полное имя автора (кунья + насаб + нисба)
- год смерти автора по хиджре
- название книги
- **мухаккик** - редактор тахкика (КРИТИЧНО - разные тахкики имеют разные пагинации)
- издательство
- место издания
- номер издания
- год издания по хиджре + григорианскому

Сейчас computed location на бэке возвращает `{title}, Т.X стр.Y` - слишком
кратко для academic use case.

### Решение

Расширение schema по нормализованному middle path:

1. **Справочники для high-reuse полей:**
   - `lib_publishers (id, name UNIQUE, created_at)`
   - `lib_publication_places (id, name UNIQUE, created_at)`
   - `lib_muhaqqiqs (id, name UNIQUE, full_name, created_at)`

   Reuse реальный - одно издательство публикует десятки книг. UNIQUE на name
   + ETL helper `findOrCreate(name)` даёт data quality (нет typo-дублей).

2. **Расширение `authorities`:**
   - `+ full_name TEXT` - полное имя с куньей/насабом/нисбой
   - `+ death_year_hijri INTEGER` - для footnote первого упоминания

   `authorities` уже cross-book entity (один автор пишет N книг), естественное
   место для академического имени.

3. **Per-book scalars в `lib_books`:**
   - `+ muhaqqiq_id`, `publisher_id`, `publication_place_id` UUID FK на справочники
   - `+ edition_number`, `published_year_hijri`, `published_year_gregorian` INTEGER

   Edition и годы не reusable - каждая книга имеет свои. Нет смысла в справочнике.

4. **Structured citation response** вместо склеенной строки. Backend возвращает
   `CitationDetail` с 27 raw полями через 9 LEFT JOIN. DTO `CitationResponse`
   содержит 8 nullable nested ref (authority / book / muhaqqiq / publisher /
   publicationPlace / location / pdf / region). Frontend рендерит каждое поле
   в своём блоке - решает проблему слипания арабского текста с латинскими
   цифрами и кириллическими пометками типа `изд.`

5. **No backward compat** - проект пока без production'а, миграция чистая.
   Existing dev-rows получают NULL в новых FK (см. memory `feedback_no_prod_no_backward_compat`).

### Альтернативы (rejected)

**Option A - все 8 полей плоско в `lib_books` как TEXT/INTEGER без справочников.**
- Простая миграция, никаких JOIN'ов
- Минус: typo-дубли при импорте 1000+ книг (`Дар Тайба` / `Дар-Тайба` / `دار طيبة`)
- Минус: поиск книг по publisher / city / muhaqqiq невозможен
- Editing publisher name требует обходить все книги

**Option B - отдельная `lib_book_editions` 1:N.**
- Clean architecture: lib_books = work, lib_book_editions = edition
- Минус: каскад изменений массивный - `lib_pages.book_id` должен стать
  `edition_id` (пагинации specific к edition), ETL переделывать, REST API
  менять, frontend перерабатывать
- Для MVP overkill: shamela импорт даёт **одно** издание per book
- Future migration path сохранён: при появлении multi-edition use case можно
  retrofit `lib_books` → `lib_book_editions` + создать `lib_works` parent

**Option C - JSONB `academic_metadata` в `lib_books`.**
- Минимум schema changes
- Минус: нет query-able индексов на отдельные поля
- Минус: type safety теряется в Java (`Map<String, Object>` или нестед record над JSONB)

### Последствия

**Положительные:**
- Citation для бахс качества: 8-полевая сноска по конвенции исламской академической традиции
- Data quality через справочники: нет typo-дублей publisher/city/muhaqqiq
- Поиск книг по publisher / city / muhaqqiq возможен (`WHERE publisher_id = ?`)
- Frontend получает structured data - визуально читаемые блоки вместо склеенной строки

**Отрицательные:**
- 9 LEFT JOIN в citation query (приемлемо для 1-50 citations per node, all on indexed FK)
- Frontend `<LibraryCite>` ломается при regenerate-api в этой сессии - чинится в подэтапе 20.f

**Триггеры пересмотра:**
- Появление реального multi-edition use case → миграция на Option B
- Появление production'а + первых реальных пользователей → отмена no-backward-compat правила
```

- [ ] **Step 2: Обновить architecture.md**

В `docs/architecture.md` найти раздел про Library / Book / Authority. Добавить упоминание новых полей и справочников:

```bash
grep -n "lib_books\|Authority\b\|Library" docs/architecture.md | head -10
```

В соответствующих секциях добавить:
- В описании `Book`: упомянуть 6 новых полей и 3 FK
- В описании `Authority`: упомянуть `fullName`, `deathYearHijri`
- В Library секции: новые таблицы `lib_publishers`, `lib_publication_places`, `lib_muhaqqiqs`
- В разделе сitation: new `CitationDetail` record + `CitationResponse` DTO

- [ ] **Step 3: Обновить api-contract.md**

В `docs/api-contract.md` найти раздел `GET /api/v1/nodes/{id}/sources`. Заменить пример response на новый структурированный JSON (взять из spec'а раздел 5 "DTO REST API"):

```bash
grep -n "/nodes.*sources\|NodeSourceResponse" docs/api-contract.md | head -5
```

Заменить пример + добавить строку в "История изменений контракта":

```markdown
| 2026-05-14 | `GET /api/v1/nodes/{nodeId}/sources` | response shape изменён - `location`/`pageId`/`rangeStart`/`rangeEnd`/`pdfFileId`/`pdfPageNumber`/`pdfBbox`/`imageRegionId`/`bookId` заменены на nested `citation` объект с 8 nullable refs (ADR-028 academic citation) |
```

- [ ] **Step 4: Обновить glossary.md**

В `docs/glossary.md` добавить:

```markdown
## ADR-028 (academic citation)

- **мухаккик (تحقيق)** - редактор/исследователь тахкика. Готовит критическое
  издание классического текста: сверяет рукописи, расставляет диакритику,
  даёт сноски. **Критично:** разные тахкики одной книги имеют разные
  пагинации, поэтому citation `Тафсир Ибн Касира, стр.145` без указания
  тахкика неоднозначна.

- **тахкик** - процесс подготовки критического издания. Работа мухаккика.

- **edition (издание, طبعة)** - конкретное опубликованное издание книги.
  Книга `Тафсир Ибн Касира` может иметь edition 2 от Дар Тайба и edition 1
  от Дар аль-Фикр - это разные пагинации с разными мухаккиками.

- **хиджра (هـ)** - исламский лунный календарь. Эра начинается с 622 г. н.э.
  Современные мусульманские книги обычно дают год публикации на обоих
  календарях (`1420 هـ / 1999 м.`).

- **кунья (كنية)** - первая часть полного арабского имени, форма «Абу/Умм
  Х» (отец/мать Х). Пример: `Абу Абдуллах`.

- **насаб (نسب)** - родословная часть имени: `Х ибн Y ибн Z` (Х сын Y сына Z).
  Пример: `Мухаммад ибн Ахмад ибн Усман`.

- **нисба (نسبة)** - последняя часть имени, привязка к месту/племени/мазхабу.
  Пример: `аль-Багдади` (из Багдада), `аш-Шафии` (приверженец мазхаба
  имама Шафии).

- **полное имя автора** - в академической citation должно включать кунью +
  насаб + нисбу + год смерти при первом упоминании. Пример: `أبو الفداء
  إسماعيل بن عمر بن كثير الدمشقي (т.774 هـ)`.

- **бахс (بحث)** - научное исследование/разбор. Жанр исламской науки -
  разбор вопроса с привлечением цитат из Корана, хадиса, мнений учёных.
```

- [ ] **Step 5: Обновить roadmap.md**

В `docs/roadmap.md` строки 806-824 (этап 20.a-f) проставить `[x]` на 20.a + 20.b + 20.f computed location (frontend display части в подэтапе 20.f следующей сессии):

```markdown
- [x] **20.a: ADR-028** - полная academic citation model. Принят нормализованный
      middle path: справочники для high-reuse полей (publisher/place/muhaqqiq),
      расширение authorities для академического имени автора, per-book скаляры
      (edition/years) плоско. Structured CitationResponse вместо склеенной строки.
- [x] **20.b: Backend миграция + domain** - миграция 24, 3 новых record +
      repository, Authority + Book расширены. CitationDetail + 8 LEFT JOIN.
      DTO CitationResponse + 8 nested refs. ~20 IT.
- [ ] **20.c: Shamela bibliography parser** - regex извлечение мухаккика
      и publisher из raw bibliography text. Отдельная сессия.
- [ ] **20.d: Admin BookEditModal** - frontend UI для ручного дозаполнения
      academic fields. Отдельная сессия.
- [ ] **20.e: AddSourceModal расширенная форма** - manual entry для
      sourceType=BOOK с полными полями. Отдельная сессия.
- [x] **20.f: Computed location update** - backend компонент закрыт в 20.b
      (CitationDetail из JOIN). Frontend `<LibraryCite>` блочный рендер -
      следующая сессия (после regenerate-api).
```

- [ ] **Step 6: Commit документации**

```bash
git add docs/decisions.md docs/architecture.md docs/api-contract.md \
        docs/glossary.md docs/roadmap.md
git commit -m "docs: ADR-028 academic citation metadata + 4 doc updates

- decisions.md: ADR-028 с контекстом, решением, 3 альтернативами, последствиями
- architecture.md: Library секция, Book + Authority описание расширены, новые справочники
- api-contract.md: GET /api/v1/nodes/{id}/sources response shape changelog
- glossary.md: мухаккик / тахкик / edition / хиджра / кунья / насаб / нисба / бахс
- roadmap.md: [x] на 20.a + 20.b + 20.f (frontend часть переносится в следующую сессию)"
```

---

### Task 10: Final verify + frontend impact документация + handoff

**Files:**
- Run: `./mvnw verify` финальный прогон
- Modify: `docs/progress.md` (новая запись «Сессия 31»)
- Modify: `docs/SESSION_START_PROMPT.md` (handoff для следующей сессии)

- [ ] **Step 1: Финальный verify**

```bash
cd backend && ./mvnw verify
```

Expected: BUILD SUCCESS. Все ~370 IT pass. Никаких regression.

Если падают тесты которые используют старые поля `NodeSourceResponse.location()` / `.bookId()` etc - найти и поправить.

- [ ] **Step 2: Записать «Сессия 31» в progress.md**

В `docs/progress.md` добавить **в начало** (после header'а на строке 17, перед «## 2026-05-14 — Сессия 30»):

```markdown
## 2026-05-14 — Сессия 31 (backend) - Этап 20.a-b academic citation metadata ЗАКРЫТ

Реализован ADR-028 - расширение схемы для бахс-grade academic citation.
Нормализованный middle path: справочники для high-reuse полей + расширение
authorities для академического имени автора + per-book скаляры.

### Сделано (~8-9 коммитов, 9 tasks плана)

- **Task 1** - миграция 24: ALTER authorities (+full_name, +death_year_hijri),
  CREATE lib_publishers / lib_publication_places / lib_muhaqqiqs (с UNIQUE name),
  ALTER lib_books (+3 FK +3 scalars), 3 CHECK + 4 BTREE индекса
- **Task 2-4** - 3 простых record + 3 JDBC repository (Publisher / PublicationPlace
  / Muhaqqiq) с findOrCreate helper для ETL upsert. 18 IT всего
- **Task 5** - расширение Authority record + AuthorityRepository + AuthorityResponse +
  DtoMappers. 4 IT (round-trip / null / 2x CHECK violation)
- **Task 6** - расширение Book record + BookRepository + BookDetailResponse +
  LibraryDtoMappers. Поправлены call sites `new Book(...)` в Shamela mapper и
  тестах. 4 новых IT
- **Task 7** - CitationDetail record (27 raw полей) + переписанный
  NodeSourceRepository.findByNodeIdWithLocation с 9 LEFT JOIN.
  NodeSourceWithLocation рефакторен (ns + citation). 5 новых IT
- **Task 8** - CitationResponse + 8 nested ref DTO (AuthorityCitationRef,
  BookCitationRef, MuhaqqiqRef, PublisherRef, PublicationPlaceRef,
  LocationRef, PdfRef, RegionRef). NodeSourceResponse рефакторен - старые
  плоские поля заменены на nested citation. DtoMappers.toCitationResponse
  с 8 private helpers (каждый возвращает null если FK не задан). 2 controller IT
- **Task 9** - ADR-028 + 4 doc updates (architecture / api-contract / glossary / roadmap)

### Решения

- **Option A (расширить lib_books плоско)** rejected: typo-дубли + поиск
- **Option B (lib_book_editions 1:N)** rejected: каскад изменений массивный,
  MVP overkill. Future migration path сохранён через rename + parent table
- **Option C (JSONB academic_metadata)** rejected: нет query-able индексов, type unsafe
- **Authority extension** вместо new `lib_authors` - cross-book entity, естественное место
- **Structured CitationDetail** вместо string concat - решает проблему слипания арабского
  с латинскими/кириллическими частями. Frontend рендерит каждое поле в своём блоке
- **No backward compat** - новый memory entry зафиксирован (нет prod, можем
  делать DROP/TRUNCATE как угодно)

### Проблемы

- Большое количество call sites `new Book(...)` в shamela mapper + IT - пришлось
  добавлять 6 null'ов в конец конструктора. Возможно стоит рефактор на builder
  pattern в будущей сессии (отдельный TODO)
- Frontend ломается при regenerate-api - `NodeSourceResponse.location` поле
  исчезает, заменяется на `citation: { ... }`. Чинится в подэтапе 20.f следующей
  сессии (новый `<LibraryCite>` рендер блоков)
- Если `printed_page` хранится как TEXT (миграция 19), `CitationDetail.regionPrintedPage`
  тип должен быть String а не Integer - проверить и поправить если так

### Следующий шаг

**Сессия 32** - **подэтап 20.f**: frontend `<LibraryCite>` блочный рендер.

1. **Запустить frontend regenerate-api**:
   ```bash
   cd frontend && npm run generate-api
   ```
   Это обновит `frontend/src/shared/api/types.ts` с новой shape
   `NodeSourceResponse.citation`. **Сломается компиляция** TypeScript в
   `CitationsList.tsx` / `NodeCitationsSection.tsx` где обращаются к
   `link.location` / `link.bookId` etc.

2. **Переписать `frontend/src/apps/argument-map/components/graph/CitationsList.tsx`**:
   - `LibraryCite` компонент рендерит structured citation блоками:
     - Author block (RTL/наskh): `{authorFullName} (т.{deathYearHijri} هـ)`
     - Title block (RTL/наskh): `{bookTitle}`
     - Muhaqqiq block: `тахкик: {muhaqqiqName}`
     - Publisher block: `изд. {publisherName} · {publicationPlaceName} · {editionNumber}-е изд.`
     - Years block: `{publishedYearHijri} هـ / {publishedYearGregorian} м.`
     - Location block (моноширинный): `Т.{part} · стр.{printedPage}`
   - Каждый блок с правильным `dir` атрибутом и шрифтом
   - Условный рендер каждого блока: если nested ref = null, блок скрывается

3. **`NodeCitationsSection.tsx`** - адаптировать typings, header counts остаются

4. **Playwright smoke** - открыть `/topics/{id}` страницу с тестовой citation
   на Тафсир Ибн Касира (node `4139cb32-28ba-4d98-9954-225e8e3c863d`), убедиться
   что citation рендерится блочно

5. **(Опционально)** ручной курсор-add academic data для smoke citation:
   ```sql
   INSERT INTO lib_muhaqqiqs (id, name, full_name) VALUES (uuid_generate_v4(), 'السلامة', 'سامي بن محمد السلامة');
   INSERT INTO lib_publishers (id, name) VALUES (uuid_generate_v4(), 'Дар Тайба');
   INSERT INTO lib_publication_places (id, name) VALUES (uuid_generate_v4(), 'Эр-Рияд');
   UPDATE lib_books SET muhaqqiq_id = (SELECT id FROM lib_muhaqqiqs LIMIT 1),
                         publisher_id = (SELECT id FROM lib_publishers LIMIT 1),
                         publication_place_id = (SELECT id FROM lib_publication_places LIMIT 1),
                         edition_number = 2, published_year_hijri = 1420, published_year_gregorian = 1999
   WHERE id = '02bcfa43-d269-4545-8e8b-965ed56dfc93';
   UPDATE authorities SET full_name = 'إسماعيل بن عمر بن كثير الدمشقي', death_year_hijri = 774
   WHERE id = (SELECT authority_id FROM lib_books WHERE id = '02bcfa43-d269-4545-8e8b-965ed56dfc93');
   ```
   Чтобы посмотреть полный блочный рендер на реальной citation.

6. **Подэтап 20.c shamela bibliography parser** - параллельно или вслед за 20.f.
```

- [ ] **Step 3: Обновить SESSION_START_PROMPT.md**

В `docs/SESSION_START_PROMPT.md` обновить раздел «КРИТИЧНО для Сессии 31+» -
заменить на «КРИТИЧНО для Сессии 32+» с новым контекстом:

Найти блок начиная с `## КРИТИЧНО для Сессии 31+ (после Сессии 30 - этап 18.f + 18.h ЗАКРЫТЫ)`. Заменить на:

```markdown
## КРИТИЧНО для Сессии 32+ (после Сессии 31 - этап 20.a-b ЗАКРЫТ)

Сессия 31 закрыла backend часть Этапа 20 (ADR-028 academic citation
metadata). Расширена schema, добавлены справочники, structured citation
response готов на backend.

**Production-ready state:**
- Backend: миграция 24 applied (lib_publishers / lib_publication_places /
  lib_muhaqqiqs + расширения authorities + lib_books). NodeSourceRepository
  возвращает structured CitationDetail через 9 LEFT JOIN
- Frontend: **сломан** при regenerate-api - поле `location` исчезло из
  `NodeSourceResponse`, заменено на nested `citation: CitationResponse`
- Backend tests: ~370+ IT зелёные, ~20 новых через Testcontainers

## ВЫБРАН ПРИОРИТЕТ Сессии 32: подэтап 20.f frontend `<LibraryCite>` блочный рендер

Запустить `npm run generate-api`, переписать `CitationsList.tsx` / `NodeCitationsSection.tsx`
на structured citation с блочным рендером (Author / Title / Muhaqqiq /
Publisher / Years / Location - каждое поле в своём `<div>` с правильным dir/font).

**Стартовая последовательность:**
1. `cd frontend && npm run generate-api` - regenerate types.ts
2. Открыть `frontend/src/apps/argument-map/components/graph/CitationsList.tsx`
   и `frontend/src/apps/argument-map/components/graph/NodeCitationsSection.tsx` -
   фиксить TypeScript errors на `link.location` / `link.bookId`
3. Реализовать `<LibraryCite>` с 6 conditional блоков (см. progress.md
   Сессия 31 «Следующий шаг»)
4. Playwright smoke на `/topics/{topicId}` с тестовой citation
5. (Optional) SQL update тестовой book/authority с academic data для full block render demo

**Альтернативные приоритеты** (для будущих сессий, не для 32):
- 20.c shamela bibliography parser - regex extraction мухаккика/publisher из bibliography text
- 20.d Admin BookEditModal - frontend UI для ручного дозаполнения
- 20.e AddSourceModal расширенная форма
- Этап 19 Q&A приложение
```

(Остальные блоки в SESSION_START_PROMPT можно оставить - они исторические и не блокируют новую сессию.)

- [ ] **Step 4: Финальный handoff коммит**

```bash
git add docs/progress.md docs/SESSION_START_PROMPT.md
git commit -m "docs: handoff Сессии 31 - этап 20.a-b ЗАКРЫТ, Сессия 32 = 20.f frontend

ADR-028 реализован полностью на backend. Frontend ломается при
regenerate-api в следующей сессии - там же чиним через <LibraryCite>
блочный рендер."
```

- [ ] **Step 5: Финальный sanity check**

```bash
git log --oneline -15
```

Expected: 9-10 коммитов «feat(backend): этап 20.a - ...» / «docs: ...» + handoff в конце.

```bash
cd backend && ./mvnw verify
```

Expected: BUILD SUCCESS финальный.

```bash
docker exec argumentmap-postgres psql -U argmap -d argumentmap -c "\d lib_publishers; \d lib_publication_places; \d lib_muhaqqiqs;" | head -30
```

Expected: 3 таблицы видны.

---

## Self-Review

**1. Spec coverage:**
- Schema → Task 1 ✓
- Domain (Publisher/Place/Muhaqqiq + repos) → Tasks 2-4 ✓
- Authority extension → Task 5 ✓
- Book extension → Task 6 ✓
- CitationDetail + 9-JOIN SQL → Task 7 ✓
- CitationResponse + 8 nested refs + DtoMappers + NodeSourceResponse rework → Task 8 ✓
- ETL импорт - в Task 6 (через 6 null'ов в shamela mapper) - parser отложен в 20.c
- Testing: 5 IT в Task 2 (Publisher), 5 в Task 3 (PublicationPlace), 6 в Task 4
  (Muhaqqiq), 4 в Task 5 (Authority), 4 в Task 6 (Book), 5 в Task 7
  (NodeSource), 2 в Task 8 (Controller) = ~31 IT - **превышает spec'овский
  бюджет 15-20**, разумно для полного coverage с edge cases
- ADR-028 + 4 doc updates → Task 9 ✓
- Out of scope (20.c-f) явно документирован

**2. Placeholder scan:** проверено - нет TBD/TODO в шагах, каждый шаг содержит конкретный код или команду.

**3. Type consistency:**
- `Muhaqqiq` record: `(id, name, fullName, createdAt)` - 4 поля, везде так
- `CitationDetail.muhaqqiqFullName` присутствует во всех использованиях
- `MuhaqqiqRef(id, name, fullName)` - 3 поля, везде так
- `NodeSourceWithLocation(NodeSource ns, CitationDetail citation)` - везде so
- `NodeSourceResponse` рефакторен консистентно во всех 3 местах (DTO / mapper / controller / tests)
- Method names: `findOrCreate(name)` на всех 3 справочниках
- Все CHECK constraint имена унифицированы: `authorities_death_year_sane` / `lib_books_edition_positive` / `lib_books_hijri_sane` / `lib_books_gregorian_sane`

Plan готов.

---

## Execution Handoff

Plan saved to `docs/plans/2026-05-14-academic-citation-metadata.md`.

Два варианта execution:

**1. Subagent-Driven (recommended)** - dispatch fresh subagent per task с review между tasks. Изоляция context, чище review checkpoint между группами Task 2/3/4 (параллельные простые repos) vs Task 6/7/8 (взаимосвязанные).

**2. Inline Execution** - execute tasks в этой же session через `superpowers:executing-plans`. Batch с checkpoints. Быстрее но context растёт.

В режиме автономии user предпочитает быстрые итерации без лишних gates. Inline execution - быстрее, меньше overhead. Subagent-driven - чище review.
