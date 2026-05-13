# CitationPicker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Связать library (книги, страницы, PDF) с argument-map узлами через CitationPicker - полноценное positional citation с deep links, поддержка text/PDF/region modes.

**Architecture:** Hybrid data model - `Source.bookId FK lib_books` + `node_sources` расширение 7 positional колонок (CHECK constraint XOR между TEXT/PDF/REGION/LEGACY modes). Backend ensures-or-creates Source per book при citation flow. Frontend - 3-колонный CitationPicker с встроенным mini-reader (extract'нут из apps/library/components в shared/components/reader). Deep links через query params.

**Tech Stack:** Java 21, Spring Boot 3.5, JDBC Template, Liquibase 22+23 миграции, Testcontainers, React 19, Vite, Vitest+MSW, PDF.js, Tailwind v4.

**Spec:** `docs/superpowers/specs/2026-05-13-citation-picker-design.md`

---

## File Structure

### Backend files

**Создаются:**
- `backend/src/main/resources/db/changelog/changes/20260513-22-add-book-id-to-sources.xml`
- `backend/src/main/resources/db/changelog/changes/20260513-23-add-positional-fields-to-node-sources.xml`
- `backend/src/main/java/ru/basnukaev/argumentmap/service/NodeCitationService.java`
- `backend/src/main/java/ru/basnukaev/argumentmap/domain/CitationMode.java`
- `backend/src/main/java/ru/basnukaev/argumentmap/domain/PdfBbox.java`
- `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/CitationRequest.java`
- `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/NodeSourceResponse.java` (replace existing)
- `backend/src/main/java/ru/basnukaev/argumentmap/web/controller/NodeCitationController.java`
- `backend/src/test/java/ru/basnukaev/argumentmap/service/NodeCitationServiceIT.java`
- `backend/src/test/java/ru/basnukaev/argumentmap/web/controller/NodeCitationControllerIT.java`
- `backend/src/test/java/ru/basnukaev/argumentmap/repository/NodeSourceRepositoryExtIT.java` (расширения)

**Модифицируются:**
- `backend/src/main/resources/db/changelog/db.changelog-master.xml` — include 22 + 23
- `backend/src/main/java/ru/basnukaev/argumentmap/domain/Source.java` — +bookId
- `backend/src/main/java/ru/basnukaev/argumentmap/domain/NodeSource.java` — +7 positional полей
- `backend/src/main/java/ru/basnukaev/argumentmap/repository/SourceRepository.java` — +findByBookId, +upsertByBookId
- `backend/src/main/java/ru/basnukaev/argumentmap/repository/NodeSourceRepository.java` — расширенный INSERT + JOIN-query для computed location
- `backend/src/main/java/ru/basnukaev/argumentmap/web/mapper/NodeSourceMappers.java` — mode derivation
- `backend/src/main/java/ru/basnukaev/argumentmap/exception/GlobalExceptionHandler.java` — +citation errors
- `docs/decisions.md` — ADR-026, ADR-027
- `docs/api-contract.md` — новый раздел "Citation API"
- `docs/architecture.md` — упоминание Source.bookId, positional citation
- `docs/glossary.md` — citation mode terms
- `docs/gotchas.md` — page_id stability invariant

### Frontend files

**Создаются:**
- `frontend/src/shared/components/reader/BookHeader.tsx` (move из apps/library)
- `frontend/src/shared/components/reader/ChapterList.tsx` (move)
- `frontend/src/shared/components/reader/PageJump.tsx` (move)
- `frontend/src/shared/components/reader/PageView.tsx` (move + расширение selection)
- `frontend/src/shared/components/reader/PdfViewer.tsx` (move + расширение selection)
- `frontend/src/shared/components/reader/ReaderModeSwitch.tsx` (move)
- `frontend/src/shared/components/reader/utils.ts` (move из apps/library/utils/bookReaderUtils.ts)
- `frontend/src/shared/components/reader/textRangeUtils.ts` (новый - TreeWalker char offsets)
- `frontend/src/shared/components/reader/textRangeUtils.test.ts`
- `frontend/src/shared/components/reader/PageView.test.tsx`
- `frontend/src/shared/components/citation/CitationPicker.tsx`
- `frontend/src/shared/components/citation/BookListSidebar.tsx`
- `frontend/src/shared/components/citation/SelectionPanel.tsx`
- `frontend/src/shared/components/citation/CitationPicker.test.tsx`

**Модифицируются:**
- `frontend/src/apps/library/pages/BookReaderPage.tsx` — import paths + deep link handling
- `frontend/src/apps/argument-map/components/graph/NodeCitationsSection.tsx` — две кнопки, click-to-navigate
- `frontend/src/apps/argument-map/components/graph/NodeCitationsSection.test.tsx` — обновить под две кнопки
- `frontend/src/shared/api/types.ts` — регенерация после backend

**Удаляются:**
- `frontend/src/apps/library/components/BookHeader.tsx` (после move)
- `frontend/src/apps/library/components/ChapterList.tsx` (после move)
- ... и остальные moved-файлы

---

## Tasks

### Task 0: Audit ShamelaToLibraryMapper UPSERT invariant + gotcha

**Files:**
- Read: `backend/src/main/java/ru/basnukaev/argumentmap/library/shamela/service/ShamelaToLibraryMapper.java`
- Read: `backend/src/main/java/ru/basnukaev/argumentmap/library/repository/PageRepository.java`
- Modify: `docs/gotchas.md`

- [ ] **Step 1: Verify current behavior - mapBook skip-if-existing**

Read `ShamelaToLibraryMapper.mapBook` (line 89-128). Confirm logic:
```
existing = bookRepository.findByShamelaBookId(shamelaBookId)
if (existing.isPresent()) {
    return MappedBookResult.alreadyMapped(...);  // skip
}
// fresh path: create book + chapters + pages
```

Read `PageRepository.save` (line 47-63). Confirm pure `INSERT` (no `ON CONFLICT`).

Conclusion: при re-import same book, mapBook возвращает alreadyMapped **до** того как доходит до pageMapper. Существующие `lib_pages` rows не trotrogivayutsya, page_id stable. Это удовлетворяет наш invariant для citation stability.

- [ ] **Step 2: Document gotcha**

Append to `docs/gotchas.md` после existing записей:

```markdown
## lib_pages.id стабильность через mapper skip-if-existing
**Симптом:** Можно ожидать что при re-import shamela master metadata
lib_pages пересоздаются с новыми UUID, что сломает citation.page_id refs

**Причина:** `ShamelaToLibraryMapper.mapBook` (line 96-102) делает
`findByShamelaBookId` check **до** перемаппинга и returns `alreadyMapped`
если book уже импортирована. lib_pages **не** пересоздаются для existing
books. PageRepository.save - pure INSERT (без UPSERT), но он не вызывается
для re-import scenarios.

**Решение:** Этот invariant **полагается на skip-if-existing**. Если в
будущем потребуется обновлять контент страниц при re-import (например
после shamela major release update), нужно сменить mapper на UPSERT по
композитному ключу `(book_id, page_number)` с RETURNING id - чтобы UUID
оставался стабильным. Сейчас этого не требуется (Этап 18.f citation),
зафиксировано как design decision.

**Связано с:** ADR-026, ADR-027 (citation stability requires stable
page_id refs - FK ON DELETE RESTRICT).
```

- [ ] **Step 3: Commit**

```bash
git add docs/gotchas.md
git commit -m "docs: gotcha lib_pages.id stability через mapper skip-if-existing

Audit ShamelaToLibraryMapper.mapBook + PageRepository.save для подэтапа
18.f.1 Citation Picker - подтверждено что текущее skip-if-existing
поведение mapper'а удовлетворяет invariant стабильности page_id для
citation FK с ON DELETE RESTRICT. UPSERT fix не требуется на этом этапе.

Подготовка к этапу 18.f."
```

---

### Task 1: Миграция 22 - Source.bookId FK + ADR-026 + Source domain extensions

**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/20260513-22-add-book-id-to-sources.xml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/domain/Source.java`
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/repository/SourceRepository.java`
- Create: `backend/src/test/java/ru/basnukaev/argumentmap/repository/SourceRepositoryBookIdIT.java`
- Modify: `docs/decisions.md` (ADR-026)
- Modify: `docs/architecture.md`

- [ ] **Step 1: Write the failing IT for Source.bookId persistence**

Create `backend/src/test/java/ru/basnukaev/argumentmap/repository/SourceRepositoryBookIdIT.java`:

```java
package ru.basnukaev.argumentmap.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.basnukaev.argumentmap.config.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.repository.BookRepository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SourceRepositoryBookIdIT {

    @Autowired private SourceRepository sourceRepository;
    @Autowired private BookRepository bookRepository;

    @Test
    void save_persists_book_id_for_sourceType_BOOK() {
        UUID bookId = createBook();
        Source src = new Source(UUID.randomUUID(), SourceType.BOOK,
            "Тафсир Ибн Касира", null, null, null, bookId, null, Instant.now());
        sourceRepository.save(src);

        Optional<Source> found = sourceRepository.findById(src.id());
        assertThat(found).isPresent();
        assertThat(found.get().bookId()).isEqualTo(bookId);
    }

    @Test
    void findByBookId_returns_source_when_exists() {
        UUID bookId = createBook();
        Source src = new Source(UUID.randomUUID(), SourceType.BOOK,
            "test", null, null, null, bookId, null, Instant.now());
        sourceRepository.save(src);

        Optional<Source> found = sourceRepository.findByBookId(bookId);
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(src.id());
    }

    @Test
    void findByBookId_returns_empty_for_unknown_book() {
        assertThat(sourceRepository.findByBookId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void cannot_set_book_id_for_non_BOOK_sourceType() {
        UUID bookId = createBook();
        Source bad = new Source(UUID.randomUUID(), SourceType.URL,
            "url src", "https://example.com", null, null, bookId, null, Instant.now());
        assertThatThrownBy(() -> sourceRepository.save(bad))
            .hasMessageContaining("chk_sources_book_id_only_for_book_type");
    }

    @Test
    void unique_constraint_prevents_duplicate_source_per_book() {
        UUID bookId = createBook();
        Source first = new Source(UUID.randomUUID(), SourceType.BOOK,
            "book", null, null, null, bookId, null, Instant.now());
        sourceRepository.save(first);

        Source second = new Source(UUID.randomUUID(), SourceType.BOOK,
            "book", null, null, null, bookId, null, Instant.now());
        assertThatThrownBy(() -> sourceRepository.save(second))
            .hasMessageContaining("uq_sources_book_per_type");
    }

    private UUID createBook() {
        UUID id = UUID.randomUUID();
        Book b = new Book(id, BookType.BOOK, "тест", null, "ru", null,
            Map.of(), null, Instant.now(), Instant.now());
        bookRepository.save(b);
        return id;
    }
}
```

- [ ] **Step 2: Run test - expected FAIL (Source has no bookId field)**

```bash
cd backend && ./mvnw -Dtest=SourceRepositoryBookIdIT failsafe:integration-test
```

Expected: compile error - `Source` constructor doesn't accept bookId.

- [ ] **Step 3: Create migration 22**

Create `backend/src/main/resources/db/changelog/changes/20260513-22-add-book-id-to-sources.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                            https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="20260513-22-add-book-id-to-sources" author="Abdula Basnukaev">
        <comment>
            Реализация ADR-026: связь Source -> lib_books через book_id FK.
            Один Source per (source_type=BOOK, book_id) - идемпотентность
            при citation flow. ON DELETE RESTRICT блокирует удаление книги
            пока на неё ссылается citation - часть инварианта стабильности.

            (1) book_id UUID nullable FK lib_books(id) ON DELETE RESTRICT
            (2) UNIQUE INDEX (source_type, book_id) WHERE book_id IS NOT NULL
                - один Source per BOOK
            (3) CHECK book_id IS NULL OR source_type = 'BOOK' - book_id
                допустим только для BOOK типа
        </comment>
        <sql>
            ALTER TABLE sources
                ADD COLUMN book_id UUID REFERENCES lib_books(id) ON DELETE RESTRICT;

            CREATE UNIQUE INDEX uq_sources_book_per_type
                ON sources(source_type, book_id)
                WHERE book_id IS NOT NULL;

            ALTER TABLE sources
                ADD CONSTRAINT chk_sources_book_id_only_for_book_type
                CHECK (book_id IS NULL OR source_type = 'BOOK');

            CREATE INDEX idx_sources_book_id ON sources(book_id)
                WHERE book_id IS NOT NULL;
        </sql>
        <rollback>
            <sql>
                DROP INDEX IF EXISTS idx_sources_book_id;
                ALTER TABLE sources DROP CONSTRAINT IF EXISTS chk_sources_book_id_only_for_book_type;
                DROP INDEX IF EXISTS uq_sources_book_per_type;
                ALTER TABLE sources DROP COLUMN IF EXISTS book_id;
            </sql>
        </rollback>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 4: Register migration in master changelog**

Edit `backend/src/main/resources/db/changelog/db.changelog-master.xml`. After the include for `20260512-21-create-library-files.xml`, add:

```xml
    <include file="db/changelog/changes/20260513-22-add-book-id-to-sources.xml"/>
```

- [ ] **Step 5: Extend Source record with bookId**

Edit `backend/src/main/java/ru/basnukaev/argumentmap/domain/Source.java`. Add `UUID bookId` field after `authorityId`:

```java
public record Source(
        UUID id,
        SourceType sourceType,
        String title,
        String citation,
        Reliability reliability,
        UUID authorityId,
        UUID bookId,  // NEW
        Map<String, Object> metadata,
        Instant createdAt
) {}
```

- [ ] **Step 6: Update SourceRepository**

Edit `backend/src/main/java/ru/basnukaev/argumentmap/repository/SourceRepository.java`:

1. Add `book_id` to `COLUMNS` constant
2. Update RowMapper to read `book_id` UUID
3. Update INSERT/UPDATE SQL to include `book_id` placeholder
4. Update `save` method to pass `source.bookId()`
5. Add new method:

```java
public Optional<Source> findByBookId(UUID bookId) {
    return jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM sources WHERE book_id = ?",
            ROW_MAPPER,
            bookId
    ).stream().findFirst();
}

/**
 * Атомарный upsert по unique (source_type, book_id). При race-condition
 * двух concurrent insert'ов на одну книгу - один выигрывает, второй
 * получает existing row через DO NOTHING + последующий findByBookId.
 * Resurrects soft-deleted refs не применимо (sources не имеют soft-delete).
 */
public Source upsertByBookId(Source source) {
    if (source.bookId() == null || source.sourceType() != SourceType.BOOK) {
        throw new IllegalArgumentException(
            "upsertByBookId требует sourceType=BOOK и не-null bookId");
    }
    UUID returned = jdbcTemplate.query(
            "INSERT INTO sources (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?) "
                    + "ON CONFLICT (source_type, book_id) WHERE book_id IS NOT NULL "
                    + "DO NOTHING RETURNING id",
            rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
            source.id(), source.sourceType().name(), source.title(),
            source.citation(),
            source.reliability() != null ? source.reliability().name() : null,
            source.authorityId(), source.bookId(),
            JdbcJson.toJsonOrNull(source.metadata()),
            JdbcTimes.odt(source.createdAt())
    );
    if (returned != null) {
        return source;
    }
    // Конфликт - другой call уже создал запись. Возвращаем existing.
    return findByBookId(source.bookId()).orElseThrow(() ->
        new IllegalStateException("UPSERT conflict но findByBookId empty - inconsistent state"));
}
```

- [ ] **Step 7: Run IT - expected PASS**

```bash
cd backend && ./mvnw -Dtest=SourceRepositoryBookIdIT failsafe:integration-test
```

Expected: 5/5 tests pass.

- [ ] **Step 8: Add ADR-026 to docs/decisions.md**

Prepend to `docs/decisions.md` (or insert after ADR-025 in numerical order):

```markdown
## ADR-026 - Source.bookId FK для one-source-per-book

**Дата:** 2026-05-13
**Статус:** принят
**Связь:** ADR-017 (Source+Authority unification), ADR-018 (platform pivot)

### Контекст

Source (master data: QURAN/HADITH/BOOK/ARTICLE/URL) и lib_books (импортированные
книги из shamela) до этого были disconnected. CitationPicker требует связи
"цитата → книга" чтобы строить deep link, computed location, "все цитаты на
книгу X" queries.

### Решение

`sources.book_id UUID nullable FK lib_books(id) ON DELETE RESTRICT` +
unique index `(source_type, book_id) WHERE book_id IS NOT NULL` + CHECK
constraint `book_id IS NULL OR source_type = 'BOOK'`. Один Source per
(sourceType=BOOK, bookId). Ensure-or-create при citation flow через
`SourceRepository.upsertByBookId` (atomic ON CONFLICT DO NOTHING RETURNING).

### Альтернативы (отвергнуты)

- **Source.metadata JSONB libBookId** - dangling refs (нет FK constraint),
  GIN-запрос вместо JOIN. Слабая data integrity.
- **NodeSource.bookId напрямую без Source** - ломает ADR-017 abstraction
  "Source как единая точка цитат к узлу". Двойная схема node_sources с
  XOR между source_id и book_id неоднородна.
- **ON DELETE SET NULL вместо RESTRICT** - silent corruption при удалении
  книги (Source остаётся, но bookId стирается → citations теряют ссылку).
  RESTRICT даёт explicit failure - "книгу нельзя удалить пока есть citations".

### Последствия

- (+) Чистая схема, FK constraint, simple JOIN для будущей аналитики
- (+) Идемпотентность через unique index - one-source-per-book
- (+) Расширение естественное: book ⊂ source
- (-) Миграция 22 + одна доп. колонка в sources table
- (-) Soft-delete на book потребует review логики ensure-or-create (currently
  не нужно - books не soft-deletable)

### Триггеры пересмотра

- Появление user-uploaded books где одна книга должна иметь несколько Source
  (разные editions) - тогда unique index пересмотреть
- Multi-tenancy - sources становятся tenant-scoped, FK расширяется
```

- [ ] **Step 9: Update architecture.md**

Edit `docs/architecture.md`. Find section about Sources or доменные сущности.
Add bullet:

> - `Source.bookId` (nullable UUID, FK на `lib_books`) - связь "Source как
>   pointer на книгу". Заполнен только для `sourceType=BOOK`. Один Source
>   per `(sourceType, bookId)` через unique index. См. ADR-026.

- [ ] **Step 10: Run full backend verify**

```bash
cd backend && ./mvnw verify
```

Expected: all 358 existing IT + 5 new = 363 IT + 164 unit pass.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/resources/db/changelog/changes/20260513-22-add-book-id-to-sources.xml \
        backend/src/main/resources/db/changelog/db.changelog-master.xml \
        backend/src/main/java/ru/basnukaev/argumentmap/domain/Source.java \
        backend/src/main/java/ru/basnukaev/argumentmap/repository/SourceRepository.java \
        backend/src/test/java/ru/basnukaev/argumentmap/repository/SourceRepositoryBookIdIT.java \
        docs/decisions.md \
        docs/architecture.md
git commit -m "feat(backend): 18.f.2 - Source.bookId FK + ADR-026

Миграция 22 связывает sources с lib_books через nullable FK на
ON DELETE RESTRICT. Unique index (source_type, book_id) обеспечивает
one-source-per-book идемпотентность для citation flow.

- Миграция 22 + rollback
- Source record расширен полем bookId
- SourceRepository.findByBookId + upsertByBookId (atomic INSERT ON
  CONFLICT DO NOTHING RETURNING + retry findByBookId)
- 5 новых IT
- ADR-026 фиксирует решение"
```

---

### Task 2: Миграция 23 - node_sources positional fields + ADR-027 + NodeSource domain

**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/20260513-23-add-positional-fields-to-node-sources.xml`
- Modify: `db.changelog-master.xml`
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/domain/NodeSource.java`
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/repository/NodeSourceRepository.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/domain/CitationMode.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/domain/PdfBbox.java`
- Create: `backend/src/test/java/ru/basnukaev/argumentmap/repository/NodeSourceRepositoryPositionalIT.java`
- Modify: `docs/decisions.md`

- [ ] **Step 1: Write failing IT for positional fields persistence**

Create `backend/src/test/java/ru/basnukaev/argumentmap/repository/NodeSourceRepositoryPositionalIT.java`. Cover:

```java
@SpringBootTest(classes = TestcontainersConfiguration.class)
@ActiveProfiles("test")
class NodeSourceRepositoryPositionalIT {
    // ... autowired fixtures ...

    @Test
    void text_mode_citation_persists_page_id_and_range() {
        // setup: create node, source (with bookId), page in lib_pages
        UUID pageId = createPage();
        NodeSource ns = NodeSource.textMode(nodeId, sourceId,
            "quote", "context", "snapshot location", pageId, 0, 87, Instant.now());
        repo.save(ns);

        Optional<NodeSource> found = repo.findByPk(nodeId, sourceId);
        assertThat(found.get().pageId()).isEqualTo(pageId);
        assertThat(found.get().rangeStart()).isEqualTo(0);
        assertThat(found.get().rangeEnd()).isEqualTo(87);
        assertThat(found.get().pdfFileId()).isNull();
    }

    @Test
    void pdf_mode_citation_persists_bbox_jsonb() {
        UUID pdfFileId = createLibraryFile();
        JsonNode bbox = new ObjectMapper().createObjectNode()
            .put("x", 0.12).put("y", 0.23).put("width", 0.5).put("height", 0.04);
        NodeSource ns = NodeSource.pdfMode(nodeId, sourceId,
            "quote", "context", "snap", pdfFileId, 47, bbox, Instant.now());
        repo.save(ns);

        Optional<NodeSource> found = repo.findByPk(nodeId, sourceId);
        assertThat(found.get().pdfBbox().get("x").asDouble()).isEqualTo(0.12);
        assertThat(found.get().pdfPageNumber()).isEqualTo(47);
    }

    @Test
    void check_constraint_rejects_mixed_text_and_pdf_modes() {
        UUID pageId = createPage();
        UUID pdfFileId = createLibraryFile();
        NodeSource bad = new NodeSource(nodeId, sourceId, "q", "c", "loc",
            pageId, 0, 50, pdfFileId, 1, /*bbox*/null, null, Instant.now());
        assertThatThrownBy(() -> repo.save(bad))
            .hasMessageContaining("chk_node_sources_one_mode");
    }

    @Test
    void check_constraint_rejects_invalid_range() {
        UUID pageId = createPage();
        NodeSource bad = new NodeSource(nodeId, sourceId, "q", "c", "loc",
            pageId, 100, 50, null, null, null, null, Instant.now());  // end < start
        assertThatThrownBy(() -> repo.save(bad))
            .hasMessageContaining("chk_node_sources_one_mode");
    }

    @Test
    void legacy_mode_with_all_positional_null_works() {
        NodeSource legacy = new NodeSource(nodeId, sourceId, "q", "c", "loc",
            null, null, null, null, null, null, null, Instant.now());
        repo.save(legacy);
        assertThat(repo.findByPk(nodeId, sourceId)).isPresent();
    }

    @Test
    void cannot_delete_page_referenced_by_citation() {
        UUID pageId = createPage();
        NodeSource ns = NodeSource.textMode(nodeId, sourceId, "q", "c", "loc",
            pageId, 0, 10, Instant.now());
        repo.save(ns);

        assertThatThrownBy(() -> pageRepo.deleteById(pageId))
            .hasMessageContaining("foreign key constraint");
    }
}
```

- [ ] **Step 2: Run test - expected FAIL**

Compilation errors expected (NodeSource constructor doesn't match).

- [ ] **Step 3: Create migration 23**

Create `backend/src/main/resources/db/changelog/changes/20260513-23-add-positional-fields-to-node-sources.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog ...>
    <changeSet id="20260513-23-add-positional-fields-to-node-sources" author="Abdula Basnukaev">
        <comment>
            Реализация ADR-027: positional citation fields в node_sources.
            Четыре mutually exclusive режима citation: TEXT (page_id + range),
            PDF (pdf_file_id + pdf_page_number + pdf_bbox), REGION
            (image_region_id), LEGACY (все positional null - для freeform).
            CHECK constraint обеспечивает один-из-четырёх.

            (1) page_id FK lib_pages ON DELETE RESTRICT
            (2) range_start, range_end INT - char offsets (по plain text после
                TreeWalker strip HTML tags)
            (3) pdf_file_id FK library_files ON DELETE RESTRICT
            (4) pdf_page_number INT - страница в PDF (1-based)
            (5) pdf_bbox JSONB - {x,y,w,h} нормализованный 0-1 (zoom-invariant)
            (6) image_region_id FK lib_image_regions ON DELETE RESTRICT - future
        </comment>
        <sql>
            ALTER TABLE node_sources
                ADD COLUMN page_id          UUID REFERENCES lib_pages(id) ON DELETE RESTRICT,
                ADD COLUMN range_start      INTEGER,
                ADD COLUMN range_end        INTEGER,
                ADD COLUMN pdf_file_id      UUID REFERENCES library_files(id) ON DELETE RESTRICT,
                ADD COLUMN pdf_page_number  INTEGER,
                ADD COLUMN pdf_bbox         JSONB,
                ADD COLUMN image_region_id  UUID REFERENCES lib_image_regions(id) ON DELETE RESTRICT;

            ALTER TABLE node_sources
                ADD CONSTRAINT chk_node_sources_one_mode
                CHECK (
                  (page_id IS NOT NULL AND range_start IS NOT NULL AND range_end IS NOT NULL
                   AND range_start >= 0 AND range_end > range_start
                   AND pdf_file_id IS NULL AND image_region_id IS NULL)
                  OR
                  (pdf_file_id IS NOT NULL AND pdf_page_number IS NOT NULL AND pdf_bbox IS NOT NULL
                   AND pdf_page_number >= 1
                   AND page_id IS NULL AND image_region_id IS NULL)
                  OR
                  (image_region_id IS NOT NULL AND page_id IS NULL AND pdf_file_id IS NULL)
                  OR
                  (page_id IS NULL AND pdf_file_id IS NULL AND image_region_id IS NULL)
                );

            CREATE INDEX idx_node_sources_page_id ON node_sources(page_id)
                WHERE page_id IS NOT NULL;
            CREATE INDEX idx_node_sources_pdf_file_id ON node_sources(pdf_file_id)
                WHERE pdf_file_id IS NOT NULL;
            CREATE INDEX idx_node_sources_image_region_id ON node_sources(image_region_id)
                WHERE image_region_id IS NOT NULL;
        </sql>
        <rollback>
            <sql>
                DROP INDEX IF EXISTS idx_node_sources_image_region_id;
                DROP INDEX IF EXISTS idx_node_sources_pdf_file_id;
                DROP INDEX IF EXISTS idx_node_sources_page_id;
                ALTER TABLE node_sources DROP CONSTRAINT IF EXISTS chk_node_sources_one_mode;
                ALTER TABLE node_sources
                    DROP COLUMN IF EXISTS image_region_id,
                    DROP COLUMN IF EXISTS pdf_bbox,
                    DROP COLUMN IF EXISTS pdf_page_number,
                    DROP COLUMN IF EXISTS pdf_file_id,
                    DROP COLUMN IF EXISTS range_end,
                    DROP COLUMN IF EXISTS range_start,
                    DROP COLUMN IF EXISTS page_id;
            </sql>
        </rollback>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 4: Register migration в master changelog**

Add `<include file="db/changelog/changes/20260513-23-add-positional-fields-to-node-sources.xml"/>` after migration 22.

- [ ] **Step 5: Create domain CitationMode and PdfBbox**

Create `backend/src/main/java/ru/basnukaev/argumentmap/domain/CitationMode.java`:

```java
package ru.basnukaev.argumentmap.domain;

public enum CitationMode {
    TEXT, PDF, REGION, LEGACY;

    public static CitationMode derive(boolean hasPage, boolean hasPdf, boolean hasRegion) {
        if (hasPage) return TEXT;
        if (hasPdf) return PDF;
        if (hasRegion) return REGION;
        return LEGACY;
    }
}
```

Create `backend/src/main/java/ru/basnukaev/argumentmap/domain/PdfBbox.java`:

```java
package ru.basnukaev.argumentmap.domain;

/**
 * Прямоугольная область на PDF странице. Координаты нормализованы 0-1
 * относительно page viewport - zoom-invariant.
 */
public record PdfBbox(double x, double y, double width, double height) {
    public PdfBbox {
        if (x < 0 || x > 1 || y < 0 || y > 1
                || width <= 0 || width > 1 || height <= 0 || height > 1
                || x + width > 1.001 || y + height > 1.001) {
            throw new IllegalArgumentException(
                "PdfBbox coords должны быть в 0-1 и x+w/y+h <= 1: " + this);
        }
    }
}
```

- [ ] **Step 6: Extend NodeSource record**

Edit `backend/src/main/java/ru/basnukaev/argumentmap/domain/NodeSource.java`:

```java
package ru.basnukaev.argumentmap.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record NodeSource(
        UUID nodeId,
        UUID sourceId,
        String quote,
        String context,
        String location,
        UUID pageId,           // NEW - TEXT mode
        Integer rangeStart,    // NEW
        Integer rangeEnd,      // NEW
        UUID pdfFileId,        // NEW - PDF mode
        Integer pdfPageNumber, // NEW
        JsonNode pdfBbox,      // NEW
        UUID imageRegionId,    // NEW - REGION mode
        Instant createdAt
) {
    public static NodeSource textMode(UUID nodeId, UUID sourceId,
            String quote, String context, String location,
            UUID pageId, int rangeStart, int rangeEnd, Instant createdAt) {
        return new NodeSource(nodeId, sourceId, quote, context, location,
            pageId, rangeStart, rangeEnd, null, null, null, null, createdAt);
    }

    public static NodeSource pdfMode(UUID nodeId, UUID sourceId,
            String quote, String context, String location,
            UUID pdfFileId, int pdfPageNumber, JsonNode pdfBbox, Instant createdAt) {
        return new NodeSource(nodeId, sourceId, quote, context, location,
            null, null, null, pdfFileId, pdfPageNumber, pdfBbox, null, createdAt);
    }

    public static NodeSource regionMode(UUID nodeId, UUID sourceId,
            String quote, String context, String location,
            UUID imageRegionId, Instant createdAt) {
        return new NodeSource(nodeId, sourceId, quote, context, location,
            null, null, null, null, null, null, imageRegionId, createdAt);
    }

    public static NodeSource legacyMode(UUID nodeId, UUID sourceId,
            String quote, String context, String location, Instant createdAt) {
        return new NodeSource(nodeId, sourceId, quote, context, location,
            null, null, null, null, null, null, null, createdAt);
    }

    public CitationMode mode() {
        return CitationMode.derive(pageId != null, pdfFileId != null, imageRegionId != null);
    }
}
```

- [ ] **Step 7: Update NodeSourceRepository**

Edit `NodeSourceRepository.java`:
1. Extend `COLUMNS` constant: add `page_id, range_start, range_end, pdf_file_id, pdf_page_number, pdf_bbox, image_region_id`
2. Update RowMapper to read new fields (use `JdbcJson.readJsonNode(rs, "pdf_bbox")` для JSONB)
3. Update INSERT SQL to include new placeholders (use `?::jsonb` cast for pdf_bbox)
4. Update `save` to pass new fields

Pattern example for one field:
```java
private static final String COLUMNS =
    "node_id, source_id, quote, context, location, "
    + "page_id, range_start, range_end, pdf_file_id, pdf_page_number, "
    + "pdf_bbox, image_region_id, created_at";

// INSERT SQL:
"INSERT INTO node_sources (" + COLUMNS + ") VALUES "
    + "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)"
```

- [ ] **Step 8: Run IT - expected PASS**

```bash
cd backend && ./mvnw -Dtest=NodeSourceRepositoryPositionalIT failsafe:integration-test
```

Expected: 6/6 tests pass.

- [ ] **Step 9: Add ADR-027 to docs/decisions.md**

Append to `docs/decisions.md`:

```markdown
## ADR-027 - Positional citation fields в node_sources

**Дата:** 2026-05-13
**Статус:** принят
**Связь:** ADR-026 (Source.bookId), ADR-021 (source-first numbering)

### Контекст

Citation в исламской науке должна указывать на физическое место в книге
(том, страница, строка) - для воспроизводимости и проверки. Этот ADR
описывает positional citation на разных уровнях: textual range на text
view, bbox на PDF, region на image scan.

### Решение

`node_sources` расширяется 7 nullable колонками: `page_id`, `range_start`,
`range_end` (TEXT mode), `pdf_file_id`, `pdf_page_number`, `pdf_bbox` JSONB
(PDF mode), `image_region_id` (REGION mode). CHECK constraint обеспечивает
ровно один из 4 modes (TEXT/PDF/REGION/LEGACY). `pdf_bbox` normalized 0-1
для zoom-invariance.

Java side: `CitationMode` enum + factory methods `NodeSource.textMode()`,
`pdfMode()`, `regionMode()`, `legacyMode()`. `PdfBbox` record с validation.

### Альтернативы (отвергнуты)

- **Один JSONB column для positional info** - слабее integrity, нет
  query-able индексов на FK refs, сложнее validation
- **Отдельная таблица `node_source_positions`** - over-engineering для
  1:1 связи с node_sources, дополнительный JOIN при чтении
- **Pixel coords для PDF bbox** - не zoom-invariant, breaks при resize/DPI
- **Two запроса на чтение citation** (`node_sources` + positional) -
  два round-trip вместо одного JOIN

### Последствия

- (+) Single-table design - один INSERT/SELECT покрывает все modes
- (+) FK ON DELETE RESTRICT гарантирует data integrity - нельзя удалить
  page/PDF/region пока на них ссылается citation
- (+) Backward compatible - LEGACY mode для existing rows и AddSourceModal
- (-) Wide table (13 columns в node_sources) - но все nullable, не storage
  burden на legacy
- (-) CHECK constraint complex (4 branches) - но clear от чтения SQL

### Триггеры пересмотра

- Если modes > 4 (например audio citation, video timestamps) - возможно
  отдельная таблица станет лучше
- Если queries по positional fields станут hot path - возможно partial
  indexes недостаточно
```

- [ ] **Step 10: Run full backend verify**

```bash
cd backend && ./mvnw verify
```

Expected: 363 + 6 = 369 IT pass.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/resources/db/changelog/changes/20260513-23-add-positional-fields-to-node-sources.xml \
        backend/src/main/resources/db/changelog/db.changelog-master.xml \
        backend/src/main/java/ru/basnukaev/argumentmap/domain/NodeSource.java \
        backend/src/main/java/ru/basnukaev/argumentmap/domain/CitationMode.java \
        backend/src/main/java/ru/basnukaev/argumentmap/domain/PdfBbox.java \
        backend/src/main/java/ru/basnukaev/argumentmap/repository/NodeSourceRepository.java \
        backend/src/test/java/ru/basnukaev/argumentmap/repository/NodeSourceRepositoryPositionalIT.java \
        docs/decisions.md
git commit -m "feat(backend): 18.f.3 - positional citation fields в node_sources + ADR-027

Миграция 23 расширяет node_sources 7 nullable колонками для positional
citation (TEXT/PDF/REGION/LEGACY modes). CHECK constraint обеспечивает
mode XOR. FK ON DELETE RESTRICT защищает data integrity. PdfBbox с
validation 0-1 для zoom-invariance.

- Миграция 23 + rollback
- NodeSource record расширен 7 positional полями + 4 factory methods
- CitationMode enum + PdfBbox record
- NodeSourceRepository обновлён под новые колонки (?::jsonb cast)
- 6 новых IT
- ADR-027 фиксирует решение"
```

---

### Task 3: NodeCitationService + Controller + IT + computed location

**Files:**
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/service/NodeCitationService.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/CitationRequest.java`
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/NodeSourceResponse.java`
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/web/mapper/NodeSourceMappers.java`
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/web/controller/NodeCitationController.java`
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/exception/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/repository/NodeSourceRepository.java` - JOIN query
- Create: `backend/src/test/java/ru/basnukaev/argumentmap/service/NodeCitationServiceIT.java`
- Create: `backend/src/test/java/ru/basnukaev/argumentmap/web/controller/NodeCitationControllerIT.java`
- Modify: `docs/api-contract.md`
- Modify: `docs/glossary.md`

- [ ] **Step 1: Write failing IT for NodeCitationService.createCitation text mode**

Create `NodeCitationServiceIT.java`. First test:

```java
@SpringBootTest(classes = TestcontainersConfiguration.class)
@ActiveProfiles("test")
class NodeCitationServiceIT {
    @Autowired NodeCitationService service;
    @Autowired NodeSourceRepository nsRepo;
    @Autowired SourceRepository sourceRepo;
    // ... other fixtures: topic, node, book, page ...

    @Test
    void createCitation_text_mode_creates_source_and_node_source() {
        UUID nodeId = createNode();
        UUID bookId = createBook("Тафсир Ибн Касира");
        UUID pageId = createPage(bookId, 47, "1", "47", "وأرى أن لا تكون البدعة...");

        CitationRequest req = CitationRequest.textMode(bookId, pageId, 0, 87,
            "وأرى أن لا تكون...", "Ибн Касир признаёт");

        NodeSourceResponse response = service.createCitation(nodeId, req);

        assertThat(response.mode()).isEqualTo(CitationMode.TEXT);
        assertThat(response.pageId()).isEqualTo(pageId);
        assertThat(response.rangeStart()).isEqualTo(0);
        assertThat(response.rangeEnd()).isEqualTo(87);
        assertThat(response.location()).contains("Тафсир Ибн Касира");
        assertThat(response.location()).contains("Т.1");
        assertThat(response.location()).contains("стр.47");
        assertThat(response.location()).contains("строки 0-87");

        // Source создан через ensure-or-create
        Optional<Source> src = sourceRepo.findByBookId(bookId);
        assertThat(src).isPresent();
        assertThat(src.get().sourceType()).isEqualTo(SourceType.BOOK);
    }
}
```

- [ ] **Step 2: Run test - expected FAIL (NodeCitationService not defined)**

```bash
cd backend && ./mvnw -Dtest=NodeCitationServiceIT failsafe:integration-test
```

Expected: compile error.

- [ ] **Step 3: Create DTOs**

Create `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/CitationRequest.java`:

```java
package ru.basnukaev.argumentmap.web.dto;

import jakarta.validation.constraints.Size;
import ru.basnukaev.argumentmap.domain.PdfBbox;

import java.util.UUID;

public record CitationRequest(
        UUID bookId,
        // TEXT mode
        UUID pageId,
        Integer rangeStart,
        Integer rangeEnd,
        // PDF mode
        UUID pdfFileId,
        Integer pdfPageNumber,
        PdfBbox pdfBbox,
        // REGION mode (future)
        UUID imageRegionId,
        // common
        @Size(max = 10000) String quote,
        @Size(max = 2000) String context
) {
    public static CitationRequest textMode(UUID bookId, UUID pageId, int start, int end,
            String quote, String context) {
        return new CitationRequest(bookId, pageId, start, end,
            null, null, null, null, quote, context);
    }
    public static CitationRequest pdfMode(UUID bookId, UUID pdfFileId, int pdfPage,
            PdfBbox bbox, String quote, String context) {
        return new CitationRequest(bookId, null, null, null,
            pdfFileId, pdfPage, bbox, null, quote, context);
    }
    public static CitationRequest regionMode(UUID bookId, UUID imageRegionId, String context) {
        return new CitationRequest(bookId, null, null, null,
            null, null, null, imageRegionId, null, context);
    }
}
```

Replace `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/NodeSourceResponse.java`:

```java
package ru.basnukaev.argumentmap.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import ru.basnukaev.argumentmap.domain.CitationMode;

import java.time.Instant;
import java.util.UUID;

public record NodeSourceResponse(
        UUID nodeId,
        UUID sourceId,
        String quote,
        String context,
        String location,         // computed на бэке
        CitationMode mode,
        UUID pageId,
        Integer rangeStart,
        Integer rangeEnd,
        UUID pdfFileId,
        Integer pdfPageNumber,
        JsonNode pdfBbox,
        UUID imageRegionId,
        UUID bookId,             // для frontend deep link (из Source.bookId)
        Instant createdAt
) {}
```

- [ ] **Step 4: Add computed-location JOIN query to NodeSourceRepository**

Edit `NodeSourceRepository.java`. Add:

```java
public record NodeSourceWithLocation(NodeSource ns, String computedLocation, UUID bookId) {}

public List<NodeSourceWithLocation> findByNodeIdWithLocation(UUID nodeId) {
    String sql = """
        SELECT ns.*,
          s.book_id AS src_book_id,
          CASE
            WHEN ns.page_id IS NOT NULL THEN
              COALESCE(b.title, '?') || ', Т.' || COALESCE(p.part, '?')
                || ' стр.' || COALESCE(p.printed_page, p.page_number::text)
                || ', строки ' || ns.range_start || '-' || ns.range_end
            WHEN ns.pdf_file_id IS NOT NULL THEN
              COALESCE(b.title, '?') || ', PDF стр.' || ns.pdf_page_number || ', регион'
            WHEN ns.image_region_id IS NOT NULL THEN
              COALESCE(b.title, '?') || ', скан стр.' ||
              COALESCE(p2.printed_page, p2.page_number::text)
            ELSE ns.location
          END AS computed_location
        FROM node_sources ns
        LEFT JOIN sources s ON s.id = ns.source_id
        LEFT JOIN lib_books b ON b.id = s.book_id
        LEFT JOIN lib_pages p ON p.id = ns.page_id
        LEFT JOIN lib_image_regions ir ON ir.id = ns.image_region_id
        LEFT JOIN lib_pages p2 ON p2.id = ir.page_id
        WHERE ns.node_id = ?
        ORDER BY ns.created_at
        """;
    return jdbcTemplate.query(sql, (rs, rn) -> new NodeSourceWithLocation(
        ROW_MAPPER.mapRow(rs, rn),
        rs.getString("computed_location"),
        rs.getObject("src_book_id", UUID.class)
    ), nodeId);
}

public Optional<NodeSourceWithLocation> findByPkWithLocation(UUID nodeId, UUID sourceId) {
    // same SQL + AND ns.source_id = ? + .stream().findFirst()
}
```

- [ ] **Step 5: Implement NodeCitationService**

Create `backend/src/main/java/ru/basnukaev/argumentmap/service/NodeCitationService.java`:

```java
package ru.basnukaev.argumentmap.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.basnukaev.argumentmap.domain.*;
import ru.basnukaev.argumentmap.exception.*;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.repository.*;
import ru.basnukaev.argumentmap.web.dto.CitationRequest;
import ru.basnukaev.argumentmap.web.dto.NodeSourceResponse;
import ru.basnukaev.argumentmap.web.mapper.NodeSourceMappers;

import java.time.Instant;
import java.util.UUID;

@Service
public class NodeCitationService {

    private final NodeRepository nodeRepo;
    private final BookRepository bookRepo;
    private final PageRepository pageRepo;
    private final SourceRepository sourceRepo;
    private final NodeSourceRepository nsRepo;
    private final LibraryFileRepository libraryFileRepo;
    // ... constructor ...

    @Transactional
    public NodeSourceResponse createCitation(UUID nodeId, CitationRequest req) {
        if (!nodeRepo.existsById(nodeId)) {
            throw new NodeNotFoundException(nodeId);
        }
        if (req.bookId() == null) {
            throw new InvalidCitationException("bookId required");
        }
        Book book = bookRepo.findById(req.bookId())
            .orElseThrow(() -> new BookNotFoundException(req.bookId()));

        // Validate mode XOR
        boolean isText = req.pageId() != null;
        boolean isPdf = req.pdfFileId() != null;
        boolean isRegion = req.imageRegionId() != null;
        int activeModes = (isText ? 1 : 0) + (isPdf ? 1 : 0) + (isRegion ? 1 : 0);
        if (activeModes != 1) {
            throw new InvalidCitationException(
                "Ровно один из (pageId / pdfFileId / imageRegionId) должен быть указан");
        }

        // Validate referenced entity exists
        Page page = null;
        if (isText) {
            if (req.rangeStart() == null || req.rangeEnd() == null
                    || req.rangeEnd() <= req.rangeStart() || req.rangeStart() < 0) {
                throw new InvalidCitationException("Invalid range_start/range_end");
            }
            page = pageRepo.findById(req.pageId())
                .orElseThrow(() -> new PageNotFoundException(req.pageId()));
            if (!page.bookId().equals(req.bookId())) {
                throw new InvalidCitationException("pageId не принадлежит bookId");
            }
        }
        if (isPdf) {
            if (req.pdfPageNumber() == null || req.pdfPageNumber() < 1) {
                throw new InvalidCitationException("pdfPageNumber required, >=1");
            }
            if (req.pdfBbox() == null) {
                throw new InvalidCitationException("pdfBbox required для PDF mode");
            }
            libraryFileRepo.findActiveById(req.pdfFileId())
                .orElseThrow(() -> new PdfNotAvailableException(req.pdfFileId()));
        }
        // REGION mode validation similar - check lib_image_regions

        // Ensure-or-create Source for (BOOK, bookId)
        Source source = sourceRepo.findByBookId(req.bookId()).orElseGet(() -> {
            Source created = new Source(UUID.randomUUID(), SourceType.BOOK,
                book.title(), null, null, book.authorityId(),
                req.bookId(), null, Instant.now());
            return sourceRepo.upsertByBookId(created);
        });

        // Build snapshot location string (для node_sources.location text ≤200)
        String snapshotLocation = buildLocationSnapshot(book, page, req);

        // Insert node_sources row
        Instant now = Instant.now();
        NodeSource ns;
        if (isText) {
            ns = NodeSource.textMode(nodeId, source.id(), req.quote(), req.context(),
                snapshotLocation, req.pageId(), req.rangeStart(), req.rangeEnd(), now);
        } else if (isPdf) {
            ns = NodeSource.pdfMode(nodeId, source.id(), req.quote(), req.context(),
                snapshotLocation, req.pdfFileId(), req.pdfPageNumber(),
                pdfBboxToJsonNode(req.pdfBbox()), now);
        } else {
            ns = NodeSource.regionMode(nodeId, source.id(), req.quote(), req.context(),
                snapshotLocation, req.imageRegionId(), now);
        }
        nsRepo.save(ns);

        // Read back with computed location for response
        return nsRepo.findByPkWithLocation(nodeId, source.id())
            .map(NodeSourceMappers::toResponseWithMode)
            .orElseThrow();
    }

    private String buildLocationSnapshot(Book book, Page page, CitationRequest req) {
        // Format identical to JOIN SQL computed_location
        // ... implementation ...
    }

    private JsonNode pdfBboxToJsonNode(PdfBbox bbox) {
        // ObjectMapper.valueToTree(bbox)
    }
}
```

- [ ] **Step 6: Add exception classes**

Create:
- `InvalidCitationException` (RuntimeException with code "invalid-citation")
- `BookNotFoundException` (RuntimeException with code "book-not-found")
- `PageNotFoundException` (RuntimeException with code "page-not-found")
- `PdfNotAvailableException` (RuntimeException with code "pdf-not-available")

Each extends domain RuntimeException следующему паттерну existing
exceptions в `backend/src/main/java/ru/basnukaev/argumentmap/exception/`.

Add to `GlobalExceptionHandler.java`:
- @ExceptionHandler InvalidCitationException → 400 with `invalid-citation` type
- @ExceptionHandler BookNotFoundException → 404 `book-not-found`
- @ExceptionHandler PageNotFoundException → 404 `page-not-found`
- @ExceptionHandler PdfNotAvailableException → 404 `pdf-not-available`

- [ ] **Step 7: Create NodeCitationController**

```java
package ru.basnukaev.argumentmap.web.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.basnukaev.argumentmap.service.NodeCitationService;
import ru.basnukaev.argumentmap.web.dto.CitationRequest;
import ru.basnukaev.argumentmap.web.dto.NodeSourceResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nodes")
public class NodeCitationController {
    private final NodeCitationService service;
    public NodeCitationController(NodeCitationService service) { this.service = service; }

    @PostMapping("/{nodeId}/citations")
    @ResponseStatus(HttpStatus.CREATED)
    public NodeSourceResponse create(
            @PathVariable UUID nodeId,
            @Valid @RequestBody CitationRequest request) {
        return service.createCitation(nodeId, request);
    }
}
```

- [ ] **Step 8: Update GET endpoint to use new JOIN query**

Edit existing `NodeSourceController.getByNode` (or NodeService method) to use
`nsRepo.findByNodeIdWithLocation(nodeId)` + map к NodeSourceResponse через
`NodeSourceMappers.toResponseWithMode`.

Add `NodeSourceMappers.toResponseWithMode(NodeSourceWithLocation)`:

```java
public static NodeSourceResponse toResponseWithMode(NodeSourceWithLocation row) {
    NodeSource ns = row.ns();
    return new NodeSourceResponse(
        ns.nodeId(), ns.sourceId(), ns.quote(), ns.context(),
        row.computedLocation(),
        ns.mode(),
        ns.pageId(), ns.rangeStart(), ns.rangeEnd(),
        ns.pdfFileId(), ns.pdfPageNumber(), ns.pdfBbox(),
        ns.imageRegionId(),
        row.bookId(),
        ns.createdAt()
    );
}
```

- [ ] **Step 9: Add more IT tests for service**

Add to `NodeCitationServiceIT.java`:
- `createCitation_pdf_mode_creates_with_bbox`
- `createCitation_region_mode_with_existing_image_region`
- `createCitation_concurrent_same_book_creates_one_Source` (use CompletableFuture с 2 parallel calls, проверить findByBookId returns 1)
- `createCitation_invalid_mode_no_positional_fields_throws_400`
- `createCitation_invalid_mode_both_text_and_pdf_throws_400`
- `createCitation_invalid_range_end_lte_start_throws_400`
- `createCitation_invalid_bbox_out_of_bounds_throws_400` (via PdfBbox constructor)
- `createCitation_book_not_found_404`
- `createCitation_page_not_found_404`
- `createCitation_page_wrong_book_400`
- `createCitation_pdf_not_available_404`

Aim for ~12-15 service IT.

- [ ] **Step 10: Create NodeCitationControllerIT**

MockMvc tests, mock service:
- POST text mode happy path → 201, body matches
- POST pdf mode happy path
- POST validation errors → 400 ProblemDetails
- POST node-not-found / book-not-found / page-not-found → 404

Aim for ~6-8 controller IT.

- [ ] **Step 11: Run full backend verify**

```bash
cd backend && ./mvnw verify
```

Expected: ~369 + 15 + 8 = ~392 IT pass.

- [ ] **Step 12: Update api-contract.md**

Add to `docs/api-contract.md` new section "Citation API" с описанием
`POST /api/v1/nodes/{nodeId}/citations` (request schema text/pdf/region,
response schema NodeSourceResponse расширенный, error codes). Update
NodeSourceResponse schema section с новыми полями. Add changelog entry.

- [ ] **Step 13: Update glossary.md**

Add term entries:
- `CitationMode` (TEXT/PDF/REGION/LEGACY)
- `Positional citation`
- `PdfBbox` (normalized 0-1)

- [ ] **Step 14: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/service/NodeCitationService.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/dto/CitationRequest.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/dto/NodeSourceResponse.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/mapper/NodeSourceMappers.java \
        backend/src/main/java/ru/basnukaev/argumentmap/web/controller/NodeCitationController.java \
        backend/src/main/java/ru/basnukaev/argumentmap/exception/ \
        backend/src/main/java/ru/basnukaev/argumentmap/repository/NodeSourceRepository.java \
        backend/src/test/ \
        docs/api-contract.md \
        docs/glossary.md
git commit -m "feat(backend): 18.f.4 - NodeCitationService + Controller + computed location

POST /api/v1/nodes/{nodeId}/citations принимает CitationRequest в одном
из трёх режимов (text/pdf/region), ensure-or-create Source per book,
insert node_sources row, returns NodeSourceResponse с computed location
через SQL JOIN. Validation дублирует CHECK constraint с понятными
ProblemDetails error codes.

GET /api/v1/nodes/{nodeId}/sources обновлён - использует findByNodeIdWithLocation
JOIN-query для computed location в каждом mode.

- NodeCitationService: validation, ensure-or-create Source, insert
- 4 новых exceptions + GlobalExceptionHandler mappings
- NodeCitationController endpoint
- NodeSourceRepository.findByNodeIdWithLocation + findByPkWithLocation
- ~15 service IT + ~8 controller IT
- api-contract.md + glossary.md обновлены"
```

---

### Task 4: Backend smoke test + restart

**Files:**
- Modify: backend running state

- [ ] **Step 1: Apply migrations on production-БД by restarting backend**

```bash
kill $(lsof -ti:9090); sleep 2
cd backend && ./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  > /tmp/backend.log 2>&1 &
until curl -sf http://localhost:9090/actuator/health > /dev/null; do sleep 2; done
echo "backend up"
```

- [ ] **Step 2: Verify migrations 22+23 applied**

```bash
docker exec argumentmap-postgres psql -U argmap -d argumentmap -c \
  "SELECT id FROM databasechangelog WHERE id LIKE '20260513%' ORDER BY id;"
```

Expected: 2 rows with ids '20260513-22-...' and '20260513-23-...'.

- [ ] **Step 3: Smoke create citation through curl**

Find an existing book + page + node UUIDs from БД:

```bash
docker exec argumentmap-postgres psql -U argmap -d argumentmap -c \
  "SELECT id, title FROM lib_books LIMIT 1;" \
  -c "SELECT id, page_number, printed_page, part FROM lib_pages WHERE book_id = '<BOOK_UUID>' ORDER BY page_number LIMIT 1;" \
  -c "SELECT id FROM nodes LIMIT 1;"
```

POST citation text mode:

```bash
curl -X POST 'http://localhost:9090/api/v1/nodes/<NODE_UUID>/citations' \
  -H 'X-User-Id: 14561248-0bfd-4a62-8395-d40a6972182a' \
  -H 'Content-Type: application/json' \
  -d '{
    "bookId": "<BOOK_UUID>",
    "pageId": "<PAGE_UUID>",
    "rangeStart": 0,
    "rangeEnd": 87,
    "quote": "smoke quote",
    "context": "smoke test from CLI"
  }' | jq
```

Expected: 201 с response содержащим `mode: "TEXT"`, `location` строкой
с book title + Т + стр + range.

- [ ] **Step 4: Smoke GET через existing endpoint - проверить computed location**

```bash
curl 'http://localhost:9090/api/v1/nodes/<NODE_UUID>/sources' \
  -H 'X-User-Id: 14561248-0bfd-4a62-8395-d40a6972182a' | jq
```

Expected: array с новым citation, `location` field computed через JOIN.

- [ ] **Step 5: No commit (smoke only)**

If smoke fails - rollback и fix перед продолжением Task 5.

---

### Task 5: Frontend - extract shared mini-reader

**Files:**
- Move (git mv): apps/library/components/{BookHeader,ChapterList,PageJump,PageView,PdfViewer,ReaderModeSwitch}.tsx → shared/components/reader/
- Move: apps/library/utils/bookReaderUtils.ts → shared/components/reader/utils.ts
- Modify: apps/library/pages/BookReaderPage.tsx (update imports)
- Modify: any existing test files (update imports)

- [ ] **Step 1: Plan moves and verify imports**

```bash
cd /mnt/c/my_folders/projects/argument-map/frontend
grep -rn "apps/library/components/BookHeader\|apps/library/components/ChapterList\|apps/library/components/PageJump\|apps/library/components/PageView\|apps/library/components/PdfViewer\|apps/library/components/ReaderModeSwitch\|apps/library/utils/bookReaderUtils" src/ test/
```

This lists all files that import the to-be-moved files. Expected:
mostly `BookReaderPage.tsx` plus tests.

- [ ] **Step 2: Create target directory**

```bash
mkdir -p frontend/src/shared/components/reader
```

- [ ] **Step 3: Move files via git mv**

```bash
cd frontend
git mv src/apps/library/components/BookHeader.tsx src/shared/components/reader/BookHeader.tsx
git mv src/apps/library/components/ChapterList.tsx src/shared/components/reader/ChapterList.tsx
git mv src/apps/library/components/PageJump.tsx src/shared/components/reader/PageJump.tsx
git mv src/apps/library/components/PageView.tsx src/shared/components/reader/PageView.tsx
git mv src/apps/library/components/PdfViewer.tsx src/shared/components/reader/PdfViewer.tsx
git mv src/apps/library/components/ReaderModeSwitch.tsx src/shared/components/reader/ReaderModeSwitch.tsx
git mv src/apps/library/utils/bookReaderUtils.ts src/shared/components/reader/utils.ts
```

(Gotcha note: WSL2 + NTFS sometimes flakes on git mv - if fails, fallback to
manual cp + git add + git rm. See gotchas.md.)

- [ ] **Step 4: Update imports in BookReaderPage.tsx**

Search-and-replace all old paths:
- `@/apps/library/components/BookHeader` → `@/shared/components/reader/BookHeader`
- `@/apps/library/components/ChapterList` → `@/shared/components/reader/ChapterList`
- ... (same for ChapterList, PageJump, PageView, PdfViewer, ReaderModeSwitch)
- `@/apps/library/utils/bookReaderUtils` → `@/shared/components/reader/utils`

- [ ] **Step 5: Update imports inside moved files**

Each moved file may import from siblings (e.g. PageView imports utils).
Update relative imports to the new location.

- [ ] **Step 6: Run typecheck**

```bash
cd frontend && npx tsc --noEmit -p tsconfig.app.json
```

Expected: no errors. Fix any missed imports.

- [ ] **Step 7: Run vitest**

```bash
cd frontend && npm test -- --run
```

Expected: all existing tests still pass.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor(frontend): 18.f.5 - extract mini-reader в shared/components/reader

Перенос reader-компонентов из apps/library/components в
shared/components/reader для последующего реюза в CitationPicker.
Behavior не меняется - чистый move + import paths update.

Перенесены:
- BookHeader, ChapterList, PageJump, PageView, PdfViewer, ReaderModeSwitch
- bookReaderUtils.ts → utils.ts

BookReaderPage обновлены imports. Все existing тесты зелёные."
```

---

### Task 6: PageView / PdfViewer selection props + textRangeUtils

**Files:**
- Create: `frontend/src/shared/components/reader/textRangeUtils.ts`
- Create: `frontend/src/shared/components/reader/textRangeUtils.test.ts`
- Modify: `frontend/src/shared/components/reader/PageView.tsx`
- Modify: `frontend/src/shared/components/reader/PdfViewer.tsx`
- Create: `frontend/src/shared/components/reader/PageView.test.tsx` (если нет)

- [ ] **Step 1: Write failing tests for textRangeUtils**

Create `textRangeUtils.test.ts`:

```typescript
import { describe, it, expect } from 'vitest';
import { computeRangeOffsets, applyHighlight } from './textRangeUtils';

describe('computeRangeOffsets', () => {
  it('возвращает char offsets для selection в plain text', () => {
    const container = document.createElement('div');
    container.innerHTML = '<p>Hello world test</p>';
    document.body.appendChild(container);

    const textNode = container.querySelector('p')!.firstChild!;
    const range = document.createRange();
    range.setStart(textNode, 6);  // "world"
    range.setEnd(textNode, 11);

    const result = computeRangeOffsets(container, range);
    expect(result).toEqual({ start: 6, end: 11, quote: 'world' });

    document.body.removeChild(container);
  });

  it('пропускает HTML теги при подсчёте offsets', () => {
    const container = document.createElement('div');
    container.innerHTML = '<p>Hello <em>bold</em> text</p>';
    document.body.appendChild(container);

    const lastText = container.querySelector('p')!.lastChild!;
    const range = document.createRange();
    range.setStart(lastText, 1);  // " text" -> "text" start
    range.setEnd(lastText, 5);

    const result = computeRangeOffsets(container, range);
    expect(result?.quote).toBe('text');
    // offsets: H(0) e(1) l(2) l(3) o(4) ' '(5) b(6) o(7) l(8) d(9) ' '(10) t(11)...
    expect(result?.start).toBe(11);
    expect(result?.end).toBe(15);

    document.body.removeChild(container);
  });
});

describe('applyHighlight', () => {
  it('оборачивает text в <mark> tag по char offsets', () => {
    const container = document.createElement('div');
    container.innerHTML = '<p>Hello world</p>';
    applyHighlight(container, 6, 11);
    expect(container.innerHTML).toContain('<mark>world</mark>');
  });
});
```

- [ ] **Step 2: Run test - expected FAIL**

```bash
cd frontend && npm test -- --run textRangeUtils
```

Expected: cannot import not-existing module.

- [ ] **Step 3: Implement textRangeUtils**

Create `textRangeUtils.ts`:

```typescript
export interface TextRange {
  start: number;
  end: number;
  quote: string;
}

/**
 * Вычисляет char offsets от начала plain text container'а к Range
 * (получен из window.getSelection().getRangeAt(0)). HTML теги
 * не считаются, только text node content в порядке появления.
 */
export function computeRangeOffsets(
  container: HTMLElement,
  range: Range
): TextRange | null {
  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT);
  let offset = 0;
  let start: number | null = null;
  let end: number | null = null;
  let node: Node | null = walker.nextNode();

  while (node) {
    const len = node.textContent?.length ?? 0;
    if (node === range.startContainer) {
      start = offset + range.startOffset;
    }
    if (node === range.endContainer) {
      end = offset + range.endOffset;
      break;
    }
    offset += len;
    node = walker.nextNode();
  }

  if (start === null || end === null || end <= start) {
    return null;
  }

  return {
    start,
    end,
    quote: range.toString(),
  };
}

/**
 * Оборачивает text в container'е в <mark> по char offsets. Если
 * range охватывает несколько text nodes - создаёт несколько <mark>
 * elements соответствующих частям.
 */
export function applyHighlight(
  container: HTMLElement,
  startOffset: number,
  endOffset: number
): void {
  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT);
  let offset = 0;
  const toWrap: Array<{ node: Text; localStart: number; localEnd: number }> = [];

  let node: Node | null = walker.nextNode();
  while (node && node instanceof Text) {
    const len = node.textContent?.length ?? 0;
    const nodeStart = offset;
    const nodeEnd = offset + len;

    if (nodeEnd > startOffset && nodeStart < endOffset) {
      const localStart = Math.max(0, startOffset - nodeStart);
      const localEnd = Math.min(len, endOffset - nodeStart);
      toWrap.push({ node, localStart, localEnd });
    }

    offset += len;
    node = walker.nextNode();
  }

  // Reverse чтобы не сбить offsets при mutation
  for (const w of toWrap.reverse()) {
    const before = w.node.textContent!.substring(0, w.localStart);
    const mark = w.node.textContent!.substring(w.localStart, w.localEnd);
    const after = w.node.textContent!.substring(w.localEnd);
    const markElem = document.createElement('mark');
    markElem.textContent = mark;
    const parent = w.node.parentNode!;
    parent.insertBefore(document.createTextNode(before), w.node);
    parent.insertBefore(markElem, w.node);
    parent.insertBefore(document.createTextNode(after), w.node);
    parent.removeChild(w.node);
  }
}
```

- [ ] **Step 4: Run tests - expected PASS**

```bash
cd frontend && npm test -- --run textRangeUtils
```

- [ ] **Step 5: Extend PageView with selection props**

Edit `frontend/src/shared/components/reader/PageView.tsx`. Add props:

```typescript
interface Props {
  // existing ...
  selectable?: boolean;
  onSelectionChange?: (sel: TextSelection | null) => void;
  highlightRange?: [number, number] | null;
}

export interface TextSelection {
  pageId: string;
  rangeStart: number;
  rangeEnd: number;
  quote: string;
}
```

Implementation:

```typescript
import { useEffect, useRef } from 'react';
import { computeRangeOffsets, applyHighlight } from './textRangeUtils';

// inside component:
const contentRef = useRef<HTMLDivElement>(null);

useEffect(() => {
  if (!selectable || !contentRef.current) return;
  const container = contentRef.current;

  const handleMouseUp = () => {
    const sel = window.getSelection();
    if (!sel || sel.rangeCount === 0 || sel.isCollapsed) {
      onSelectionChange?.(null);
      return;
    }
    const range = sel.getRangeAt(0);
    if (!container.contains(range.commonAncestorContainer)) return;
    const offsets = computeRangeOffsets(container, range);
    if (offsets && pageDetail?.pageId) {
      onSelectionChange?.({
        pageId: pageDetail.pageId,
        rangeStart: offsets.start,
        rangeEnd: offsets.end,
        quote: offsets.quote,
      });
    }
  };

  container.addEventListener('mouseup', handleMouseUp);
  return () => container.removeEventListener('mouseup', handleMouseUp);
}, [selectable, onSelectionChange, pageDetail?.pageId]);

// Render highlight after content loaded
useEffect(() => {
  if (!highlightRange || !contentRef.current) return;
  applyHighlight(contentRef.current, highlightRange[0], highlightRange[1]);
  // Scroll into view
  const mark = contentRef.current.querySelector('mark');
  mark?.scrollIntoView({ behavior: 'smooth', block: 'center' });
}, [highlightRange, pageDetail?.htmlContent]);
```

Wrap rendered content `<div ref={contentRef} dangerouslySetInnerHTML={...} />`.

- [ ] **Step 6: Write integration test for PageView selection**

Create `PageView.test.tsx`:

```typescript
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import PageView from './PageView';

describe('PageView selection', () => {
  it('вызывает onSelectionChange при выделении text', () => {
    const handler = vi.fn();
    const detail = {
      pageId: 'page-uuid',
      pageNumber: 1,
      htmlContent: '<p>Hello world test</p>',
    };
    const { container } = render(
      <PageView state={{ kind: 'loaded', detail }}
                selectable={true}
                onSelectionChange={handler} />
    );

    // Programmatic Range
    const p = container.querySelector('p')!;
    const textNode = p.firstChild!;
    const range = document.createRange();
    range.setStart(textNode, 6);
    range.setEnd(textNode, 11);

    const selection = window.getSelection()!;
    selection.removeAllRanges();
    selection.addRange(range);

    // Trigger handler
    fireEvent.mouseUp(container.querySelector('[data-content="page"]')!);

    expect(handler).toHaveBeenCalledWith(expect.objectContaining({
      pageId: 'page-uuid',
      rangeStart: 6,
      rangeEnd: 11,
      quote: 'world',
    }));
  });

  it('рендерит <mark> для highlightRange', () => {
    const detail = { pageId: 'p', pageNumber: 1, htmlContent: '<p>Hello world</p>' };
    const { container } = render(
      <PageView state={{ kind: 'loaded', detail }}
                highlightRange={[6, 11]} />
    );
    expect(container.querySelector('mark')?.textContent).toBe('world');
  });
});
```

Run: `npm test -- --run PageView`. Expected: pass.

- [ ] **Step 7: Extend PdfViewer with bbox selection props**

Edit `frontend/src/shared/components/reader/PdfViewer.tsx`. Add:

```typescript
interface Props {
  // existing ...
  selectable?: boolean;
  onBboxChange?: (sel: PdfSelection | null) => void;
  highlightBbox?: PdfBboxHighlight | null;
}

export interface PdfSelection {
  pdfFileId: string;
  pdfPageNumber: number;
  bbox: { x: number; y: number; width: number; height: number };
  quote?: string;
}

export interface PdfBboxHighlight {
  pdfPageNumber: number;
  bbox: { x: number; y: number; width: number; height: number };
}
```

Implement overlay div поверх PDF.js `<Page>`:

```tsx
{selectable && (
  <div
    className="absolute inset-0 cursor-crosshair"
    onMouseDown={handleBboxDragStart}
    // track drag, compute normalized bbox, call onBboxChange
  >
    {currentDragBbox && (
      <div className="absolute border-2 border-indigo-500 bg-indigo-200/30 pointer-events-none"
           style={{
             left: `${currentDragBbox.x * 100}%`,
             top: `${currentDragBbox.y * 100}%`,
             width: `${currentDragBbox.width * 100}%`,
             height: `${currentDragBbox.height * 100}%`,
           }} />
    )}
  </div>
)}

{highlightBbox && pageNumber === highlightBbox.pdfPageNumber && (
  <div className="absolute border-2 border-amber-500 bg-amber-200/30 pointer-events-none"
       style={{ /* same percentage style */ }} />
)}
```

Drag-to-bbox logic in handler: track mouseDown coordinates relative to
container, mouseMove updates bbox, mouseUp finalizes and calls `onBboxChange`.
Normalize все coords to 0-1 dividing by container.offsetWidth / offsetHeight.

If text layer available - extract text inside bbox using PDF.js
`page.getTextContent()` (filter items within bbox bounds) и pass как
`quote`.

- [ ] **Step 8: Run all reader tests + typecheck**

```bash
cd frontend && npm test -- --run reader && npx tsc --noEmit -p tsconfig.app.json
```

Expected: pass.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/shared/components/reader/
git commit -m "feat(frontend): 18.f.6 - PageView/PdfViewer selection props

PageView получает selectable + onSelectionChange + highlightRange props.
Selection через window.getSelection() → computeRangeOffsets (TreeWalker
по text nodes для char offsets, HTML теги не считаются). highlightRange
рендерит <mark> через applyHighlight + scrollIntoView.

PdfViewer получает selectable + onBboxChange + highlightBbox props.
Custom overlay поверх PDF.js <Page>, mouse drag создаёт normalized
bbox (0-1). Text layer extraction для quote snapshot если доступен.

- textRangeUtils.ts (computeRangeOffsets + applyHighlight) с 2 unit-тестами
- PageView extension + 2 integration test
- PdfViewer extension (manual smoke - bbox drag в next task через CitationPicker)
- Все типы проходят tsc clean"
```

---

### Task 7: CitationPicker компонент + integration test

**Files:**
- Create: `frontend/src/shared/components/citation/CitationPicker.tsx`
- Create: `frontend/src/shared/components/citation/BookListSidebar.tsx`
- Create: `frontend/src/shared/components/citation/SelectionPanel.tsx`
- Create: `frontend/src/shared/components/citation/CitationPicker.test.tsx`

- [ ] **Step 1: Write failing integration test for CitationPicker**

Create `frontend/src/shared/components/citation/CitationPicker.test.tsx`. MSW-based test:

```typescript
import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import CitationPicker from './CitationPicker';

const server = setupServer(
  http.get('/api/v1/library/books', () => HttpResponse.json([
    { id: 'book-1', title: 'Тафсир Ибн Касира', bookType: 'BOOK', languageCode: 'ar' },
    { id: 'book-2', title: 'Бухари', bookType: 'BOOK', languageCode: 'ar' },
  ])),
  http.get('/api/v1/library/books/:id', ({ params }) => HttpResponse.json({
    id: params.id, title: 'Тафсир Ибн Касира', /* ... */ chapters: [], pdfFiles: [],
  })),
  http.get('/api/v1/library/books/:id/pages', () => HttpResponse.json([
    { id: 'page-1', pageNumber: 1, printedPage: '1', part: '1' },
  ])),
  http.post('/api/v1/nodes/:nodeId/citations', async ({ request }) => {
    const body = await request.json();
    return HttpResponse.json({
      nodeId: 'node-1', sourceId: 'src-1',
      mode: 'TEXT', location: 'Тафсир, Т.1 стр.1, строки 0-5',
      ...body, createdAt: new Date().toISOString(),
    }, { status: 201 });
  }),
);

beforeAll(() => server.listen());
afterAll(() => server.close());

describe('CitationPicker', () => {
  it('open → select book → load reader → submit text citation', async () => {
    const onCreated = vi.fn();
    render(
      <CitationPicker nodeId="node-1" nodeContent="test node"
                      onClose={vi.fn()} onCreated={onCreated} />
    );

    // Books loaded в sidebar
    await waitFor(() => expect(screen.getByText('Тафсир Ибн Касира')).toBeInTheDocument());

    await userEvent.click(screen.getByText('Тафсир Ибн Касира'));

    // Reader loaded
    await waitFor(() => expect(screen.getByText(/Том/)).toBeInTheDocument());

    // Trigger fake selection (simulate via custom DOM events)
    // [actual selection через jsdom programmatic Range setting, потом fireEvent.mouseUp]
    // ...

    const submitBtn = screen.getByRole('button', { name: /Привести/i });
    await userEvent.click(submitBtn);

    await waitFor(() => expect(onCreated).toHaveBeenCalled());
  });

  it('кнопка Привести disabled пока нет selection', async () => {
    render(<CitationPicker nodeId="node-1" nodeContent="t" onClose={vi.fn()} onCreated={vi.fn()} />);
    await waitFor(() => screen.getByText('Тафсир Ибн Касира'));
    await userEvent.click(screen.getByText('Тафсир Ибн Касира'));
    expect(screen.getByRole('button', { name: /Привести/i })).toBeDisabled();
  });

  it('Esc закрывает (если не submitting)', async () => {
    const onClose = vi.fn();
    render(<CitationPicker nodeId="n" nodeContent="t" onClose={onClose} onCreated={vi.fn()} />);
    await userEvent.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run test - expected FAIL (not implemented)**

```bash
cd frontend && npm test -- --run CitationPicker
```

- [ ] **Step 3: Implement BookListSidebar**

Create `BookListSidebar.tsx` (~120 LOC). Loads `/api/v1/library/books`,
local filter по title + bookType filter chips. Emit `onSelect(bookId)`.
Реализация близка к `apps/library/pages/BookListPage` но без navigation -
просто список selectable cards.

- [ ] **Step 4: Implement SelectionPanel**

Create `SelectionPanel.tsx` (~80 LOC). Props:
- `selection: TextSelection | PdfSelection | null`
- `context: string` + `onContextChange`
- `submitting: boolean`, `submitError: string | null`
- `onSubmit: () => void`

Renders:
- Preview выделенного фрагмента (quote text в italic + computed location preview)
- Textarea для context
- Primary Button "Привести" disabled если !selection || submitting

- [ ] **Step 5: Implement CitationPicker main component**

Create `CitationPicker.tsx` (~280 LOC).

```typescript
import { useState, useEffect, useRef } from 'react';
import Modal from '@/shared/components/ui/Modal';
import BookListSidebar from './BookListSidebar';
import SelectionPanel from './SelectionPanel';
import BookHeader from '@/shared/components/reader/BookHeader';
import ReaderModeSwitch from '@/shared/components/reader/ReaderModeSwitch';
import ChapterList from '@/shared/components/reader/ChapterList';
import PageView, { type TextSelection } from '@/shared/components/reader/PageView';
import PdfViewer, { type PdfSelection } from '@/shared/components/reader/PdfViewer';
import PageJump from '@/shared/components/reader/PageJump';
import { apiGetRaw, apiPostRaw, formatApiError } from '@/shared/api/client';

interface Props {
  nodeId: string;
  nodeContent: string;
  onClose: () => void;
  onCreated: () => void;
}

function CitationPicker({ nodeId, nodeContent, onClose, onCreated }: Props) {
  const [selectedBookId, setSelectedBookId] = useState<string | null>(null);
  const [bookDetail, setBookDetail] = useState<BookDetail | null>(null);
  const [pages, setPages] = useState<PageSummary[]>([]);
  const [pageNumber, setPageNumber] = useState(1);
  const [readerMode, setReaderMode] = useState<'text' | 'pdf'>('text');
  const [textSelection, setTextSelection] = useState<TextSelection | null>(null);
  const [pdfSelection, setPdfSelection] = useState<PdfSelection | null>(null);
  const [context, setContext] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  // Load book detail при selectedBookId изменении
  useEffect(() => {
    if (!selectedBookId) return;
    // fetch /api/v1/library/books/:id and pages
    // setBookDetail, setPages
  }, [selectedBookId]);

  // Clear selection при page change
  useEffect(() => {
    setTextSelection(null);
    setPdfSelection(null);
  }, [pageNumber, readerMode]);

  async function handleSubmit() {
    setSubmitting(true);
    setSubmitError(null);
    try {
      const body = textSelection
        ? {
            bookId: selectedBookId,
            pageId: textSelection.pageId,
            rangeStart: textSelection.rangeStart,
            rangeEnd: textSelection.rangeEnd,
            quote: textSelection.quote,
            context: context.trim() || undefined,
          }
        : pdfSelection
        ? {
            bookId: selectedBookId,
            pdfFileId: pdfSelection.pdfFileId,
            pdfPageNumber: pdfSelection.pdfPageNumber,
            pdfBbox: pdfSelection.bbox,
            quote: pdfSelection.quote,
            context: context.trim() || undefined,
          }
        : null;
      if (!body) return;
      await apiPostRaw(`/api/v1/nodes/${nodeId}/citations`, body);
      onCreated();
      onClose();
    } catch (e) {
      setSubmitError(formatApiError(e, 'Не удалось привязать цитату'));
      setSubmitting(false);
    }
  }

  return (
    <Modal open onClose={() => !submitting && onClose()} title={`Привести источник для: ${truncate(nodeContent, 60)}`} size="fullscreen">
      <div className="flex h-[80vh] gap-3">
        <BookListSidebar
          selectedBookId={selectedBookId}
          onSelect={setSelectedBookId}
          className="w-[280px]"
        />
        <div className="flex flex-1 flex-col">
          {bookDetail ? (
            <>
              <BookHeader book={bookDetail} />
              <div className="flex items-center gap-2">
                <ReaderModeSwitch mode={readerMode} onChange={setReaderMode} />
                <PageJump value={pageNumber} max={pages.length} onChange={setPageNumber} />
              </div>
              <div className="flex flex-1">
                <ChapterList chapters={bookDetail.chapters} onChapterClick={(p) => setPageNumber(p)} />
                {readerMode === 'text' ? (
                  <PageView state={pageState} selectable onSelectionChange={setTextSelection} />
                ) : (
                  <PdfViewer file={bookDetail.pdfFiles[0]} pageNumber={pageNumber}
                             selectable onBboxChange={setPdfSelection} />
                )}
              </div>
            </>
          ) : (
            <EmptyReaderPlaceholder />
          )}
        </div>
        <SelectionPanel
          selection={textSelection ?? pdfSelection}
          context={context}
          onContextChange={setContext}
          submitting={submitting}
          submitError={submitError}
          onSubmit={handleSubmit}
          className="w-[320px]"
        />
      </div>
    </Modal>
  );
}

export default CitationPicker;
```

- [ ] **Step 6: Run integration tests**

```bash
cd frontend && npm test -- --run CitationPicker
```

Expected: 3/3 pass.

- [ ] **Step 7: Typecheck and lint**

```bash
cd frontend && npx tsc --noEmit -p tsconfig.app.json && npm run lint
```

Expected: clean.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/shared/components/citation/
git commit -m "feat(frontend): 18.f.7 - CitationPicker компонент

Полноэкранная модалка 3-колонный layout:
- BookListSidebar (~280px) - library browse с фильтром
- EmbeddedReader (центр) - реюз mini-reader из shared/components/reader
- SelectionPanel (~320px) - preview выделения + context input + submit

State: selectedBookId, bookDetail, pages, pageNumber, readerMode,
textSelection/pdfSelection (mutually exclusive), context, submitting.

POST /api/v1/nodes/:nodeId/citations с body одного из режимов
(text/pdf). Esc закрывает (если не submitting).

3 integration tests через MSW: happy path, disabled submit без
selection, Esc closing."
```

---

### Task 8: NodeCitationsSection two buttons + click-to-navigate

**Files:**
- Modify: `frontend/src/apps/argument-map/components/graph/NodeCitationsSection.tsx`
- Modify: `frontend/src/apps/argument-map/components/graph/NodeCitationsSection.test.tsx`
- Modify: `frontend/src/shared/api/types.ts` (regenerated)

- [ ] **Step 1: Regenerate API types from backend**

Ensure backend running (Task 4 done):

```bash
cd frontend && npm run generate-api
```

Expected: `src/shared/api/types.ts` updated with new fields на `NodeSourceResponse`.

- [ ] **Step 2: Write failing test for two buttons + click-to-navigate**

Edit `NodeCitationsSection.test.tsx`. Add tests:

```typescript
it('показывает две кнопки: Привести источник + Свободный источник', async () => {
  // ... render NodeCitationsSection ...
  await userEvent.click(screen.getByText(/Источники/i));  // expand panel
  expect(screen.getByRole('button', { name: /Привести источник/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /Свободный источник/i })).toBeInTheDocument();
});

it('клик Привести источник открывает CitationPicker', async () => {
  // render + expand + click
  await userEvent.click(screen.getByRole('button', { name: /Привести источник/i }));
  expect(screen.getByRole('dialog', { name: /Привести источник/i })).toBeInTheDocument();
});

it('клик на citation row с pageId navigates к deep link', async () => {
  // mock /api/v1/nodes/:id/sources возвращает row с pageId+range
  const mockNavigate = vi.fn();
  vi.mock('react-router', () => ({ useNavigate: () => mockNavigate }));
  // render + expand
  const row = await screen.findByText('Тафсир Ибн Касира');
  await userEvent.click(row);
  expect(mockNavigate).toHaveBeenCalledWith(
    expect.stringMatching(/\/books\/.*pageId=.*highlight=/)
  );
});
```

- [ ] **Step 3: Run test - expected FAIL**

```bash
cd frontend && npm test -- --run NodeCitationsSection
```

- [ ] **Step 4: Implement two-button layout + CitationPicker integration**

Edit `NodeCitationsSection.tsx`:

```typescript
import { BookOpen, Plus, ... } from 'lucide-react';
import CitationPicker from '@/shared/components/citation/CitationPicker';

// inside component:
const [citationPickerOpen, setCitationPickerOpen] = useState(false);
const [addSourceOpen, setAddSourceOpen] = useState(false);

return (
  <>
    <PanelSection ...>
      <CitationsList state={state} onDetach={detachSource} />
      <div className="mt-2 flex gap-2">
        <Button variant="primary" size="sm" icon={BookOpen}
                onClick={() => setCitationPickerOpen(true)}
                disabled={!nodeId} className="flex-1">
          Привести источник
        </Button>
        <Button variant="ghost" size="sm" icon={Plus}
                onClick={() => setAddSourceOpen(true)}
                disabled={!nodeId} className="flex-1">
          Свободный источник
        </Button>
      </div>
    </PanelSection>

    {citationPickerOpen && nodeId && (
      <CitationPicker
        nodeId={nodeId}
        nodeContent={/* pass through from parent */ ''}
        onClose={() => setCitationPickerOpen(false)}
        onCreated={loadSources}
      />
    )}
    {addSourceOpen && nodeId && (
      <AddSourceModal ... />
    )}
  </>
);
```

- [ ] **Step 5: Implement click-to-navigate on citation rows**

Edit `CitationsList` component. For each row, build deep link URL based
on mode:

```typescript
function buildDeepLink(link: NodeSourceDto, bookId: string | null): string | null {
  if (!bookId) return null;
  if (link.mode === 'TEXT' && link.pageId && link.rangeStart != null && link.rangeEnd != null) {
    return `/books/${bookId}?pageId=${link.pageId}&highlight=${link.rangeStart}-${link.rangeEnd}`;
  }
  if (link.mode === 'PDF' && link.pdfFileId && link.pdfPageNumber && link.pdfBbox) {
    const b = link.pdfBbox as any;
    return `/books/${bookId}?pdf=1&pdfPageNumber=${link.pdfPageNumber}&bbox=${b.x},${b.y},${b.width},${b.height}`;
  }
  return null;  // legacy or region (region UI in Этап 17)
}

// in row render:
const deepLink = buildDeepLink(link, link.bookId);
{deepLink ? (
  <button onClick={() => navigate(deepLink)}
          className="text-indigo-600 hover:underline">
    Перейти к источнику
  </button>
) : null}
```

- [ ] **Step 6: Run tests - expected PASS**

```bash
cd frontend && npm test -- --run NodeCitationsSection
```

- [ ] **Step 7: Typecheck**

```bash
cd frontend && npx tsc --noEmit -p tsconfig.app.json
```

- [ ] **Step 8: Commit**

```bash
git add frontend/src/apps/argument-map/components/graph/NodeCitationsSection.tsx \
        frontend/src/apps/argument-map/components/graph/NodeCitationsSection.test.tsx \
        frontend/src/shared/api/types.ts
git commit -m "feat(frontend): 18.f.8 - NodeCitationsSection две кнопки + click-to-navigate

Заменена единая кнопка 'Привязать цитату' на две:
- 'Привести источник' (primary) - открывает CitationPicker для
  library-backed citation
- 'Свободный источник' (ghost) - открывает existing AddSourceModal
  для legacy freeform citation (URL/article/ручной хадис)

Citation rows получают clickable 'Перейти к источнику' button которая
navigates на deep link:
- TEXT: /books/:id?pageId=X&highlight=start-end
- PDF: /books/:id?pdf=1&pdfPageNumber=N&bbox=x,y,w,h
- LEGACY/REGION: ссылка не показывается

Регенерированы api/types.ts с расширенным NodeSourceResponse. 3 новых
test pass."
```

---

### Task 9: BookReaderPage deep link handling

**Files:**
- Modify: `frontend/src/apps/library/pages/BookReaderPage.tsx`
- Create: `frontend/src/apps/library/pages/BookReaderPage.test.tsx` (если нет)

- [ ] **Step 1: Write failing test**

Create or extend `BookReaderPage.test.tsx`:

```typescript
import { render, waitFor, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import BookReaderPage from './BookReaderPage';

it('?pageId=X&highlight=10-50 устанавливает page + рендерит <mark>', async () => {
  // mock api responses for book + pages
  render(
    <MemoryRouter initialEntries={['/books/book-1?pageId=page-3&highlight=10-50']}>
      <Routes>
        <Route path="/books/:bookId" element={<BookReaderPage />} />
      </Routes>
    </MemoryRouter>
  );

  await waitFor(() => expect(screen.getByText(/3/)).toBeInTheDocument());
  await waitFor(() => expect(document.querySelector('mark')).toBeInTheDocument());
});

it('?pdf=1&pdfPageNumber=5 переключает в PDF mode на странице 5', async () => {
  // ...
});

it('невалидный pageId fallback на 1 + toast', async () => {
  // ...
});
```

- [ ] **Step 2: Run test - expected FAIL**

- [ ] **Step 3: Implement deep link handling в BookReaderPage**

Edit `BookReaderPage.tsx`:

```typescript
import { useSearchParams } from 'react-router';
import { toast } from '@/shared/stores/toastStore';

function BookReaderPage() {
  const { bookId } = useParams<{ bookId: string }>();
  const [searchParams] = useSearchParams();
  // ... existing state ...

  // After pages loaded - apply deep link params
  useEffect(() => {
    if (state.kind !== 'success' || !state.pages.length) return;
    const pageIdParam = searchParams.get('pageId');
    const pdfFlag = searchParams.get('pdf') === '1';
    const pdfPageNumber = searchParams.get('pdfPageNumber');

    if (pdfFlag) {
      setReaderMode('pdf');
      if (pdfPageNumber) {
        const n = parseInt(pdfPageNumber, 10);
        if (!isNaN(n)) setPageNumber(n);
      }
    } else if (pageIdParam) {
      const found = state.pages.findIndex((p) => p.id === pageIdParam);
      if (found !== -1) {
        setPageNumber(state.pages[found].pageNumber);
      } else {
        toast.warning('Страница не найдена, открыта первая');
        setPageNumber(1);
      }
    }
  }, [state, searchParams]);

  // Pass highlightRange to PageView
  const highlightParam = searchParams.get('highlight');
  const highlightRange: [number, number] | null = useMemo(() => {
    if (!highlightParam) return null;
    const [s, e] = highlightParam.split('-').map((x) => parseInt(x, 10));
    if (isNaN(s) || isNaN(e) || e <= s) return null;
    return [s, e];
  }, [highlightParam]);

  // Pass highlightBbox to PdfViewer
  const bboxParam = searchParams.get('bbox');
  const highlightBbox = useMemo(() => {
    if (!bboxParam || !pdfPageNumber) return null;
    const [x, y, w, h] = bboxParam.split(',').map(parseFloat);
    if ([x,y,w,h].some(isNaN)) return null;
    return { pdfPageNumber: parseInt(pdfPageNumber, 10), bbox: { x, y, width: w, height: h } };
  }, [bboxParam, pdfPageNumber]);

  // pass to <PageView highlightRange={highlightRange} ... />
  // pass to <PdfViewer highlightBbox={highlightBbox} ... />
}
```

- [ ] **Step 4: Run tests - expected PASS**

```bash
cd frontend && npm test -- --run BookReaderPage
```

- [ ] **Step 5: Typecheck**

```bash
cd frontend && npx tsc --noEmit -p tsconfig.app.json
```

- [ ] **Step 6: Commit**

```bash
git add frontend/src/apps/library/pages/BookReaderPage.tsx \
        frontend/src/apps/library/pages/BookReaderPage.test.tsx
git commit -m "feat(frontend): 18.f.9 - BookReaderPage deep link handling

Query params handling после load pages:
- ?pageId=uuid → set pageNumber через findIndex, fallback 1 + toast при miss
- ?highlight=start-end → передаётся в PageView как highlightRange
  → <mark> рендерится через applyHighlight + scrollIntoView
- ?pdf=1 → setReaderMode('pdf')
- ?pdfPageNumber=N → initial page в PdfViewer
- ?bbox=x,y,w,h → передаётся в PdfViewer как highlightBbox →
  rectangle overlay над PDF page

Silent fallback на corrupted params (NaN values etc).

3 integration tests для каждого пути."
```

---

### Task 10: Frontend full verify (lint + build + tests)

**Files:** none (verify only)

- [ ] **Step 1: Run lint**

```bash
cd frontend && npm run lint
```

Expected: 0 errors.

- [ ] **Step 2: Run all tests**

```bash
cd frontend && npm test -- --run
```

Expected: ~136 existing + ~15-20 new = ~155 tests pass.

- [ ] **Step 3: Run production build**

```bash
cd frontend && npm run build
```

Expected: bundle build success. Note bundle sizes для progress.md.

- [ ] **Step 4: If errors found - fix and commit fixes**

Fix any breakages, commit как `fix(frontend): 18.f.10 - <description>`.

---

### Task 11: Manual playwright smoke end-to-end

**Files:** none (manual verification + screenshots)

- [ ] **Step 1: Ensure backend + frontend running**

```bash
# Backend
kill $(lsof -ti:9090); sleep 2
cd backend && ./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  > /tmp/backend.log 2>&1 &
until curl -sf http://localhost:9090/actuator/health > /dev/null; do sleep 2; done

# Frontend
kill $(lsof -ti:5173); sleep 2
cd frontend && npm run dev > /tmp/frontend.log 2>&1 &
sleep 5
```

- [ ] **Step 2: Playwright smoke script**

Write `/tmp/cite-smoke.spec.ts`:

```typescript
import { test, expect } from '@playwright/test';

test('CitationPicker end-to-end', async ({ page }) => {
  // 1. Open argument-map с существующей темой
  await page.goto('http://localhost:5173/topics/640a7ac7-2827-4b80-9893-dc7142f100e4');

  // 2. Click on a node to open NodeDetailsPanel
  await page.locator('.react-flow__node').first().click();

  // 3. Expand "Источники" section
  await page.locator('text=Источники').click();

  // 4. Click "Привести источник"
  await page.locator('button:has-text("Привести источник")').click();

  // 5. CitationPicker модалка открылась
  await expect(page.locator('text=Привести источник для')).toBeVisible();

  // 6. Click на Тафсир Ибн Касира в sidebar
  await page.locator('text=Тафсир Ибн Касира').click();

  // 7. Reader загрузился - ждём текст страницы
  await page.waitForSelector('[data-content="page"]', { timeout: 10000 });

  // 8. Programmatically select first paragraph
  await page.evaluate(() => {
    const content = document.querySelector('[data-content="page"]');
    if (!content) return;
    const range = document.createRange();
    range.selectNodeContents(content.querySelector('p')!);
    const sel = window.getSelection()!;
    sel.removeAllRanges();
    sel.addRange(range);
    content.dispatchEvent(new Event('mouseup'));
  });

  // 9. Submit
  await page.locator('button:has-text("Привести")').click();

  // 10. Citation appears в NodeDetailsPanel
  await expect(page.locator('text=Тафсир Ибн Касира')).toBeVisible();

  // 11. Click "Перейти к источнику"
  await page.locator('button:has-text("Перейти к источнику")').click();

  // 12. BookReader открылся с подсветкой
  await page.waitForURL(/\/books\/.*pageId/);
  await expect(page.locator('mark')).toBeVisible();
});
```

- [ ] **Step 3: Run playwright**

```bash
cd /tmp && npx playwright test cite-smoke.spec.ts --headed
```

Expected: PASS. Screenshot at each major step.

- [ ] **Step 4: Document smoke result в progress.md (preview - финальная запись в Task 12)**

Note any UX issues found. Если есть - fix immediately + commit.

- [ ] **Step 5: Restart backend smoke - verify persistence**

```bash
kill $(lsof -ti:9090); sleep 2
# restart
```

Re-open browser, verify citation appeared в node details still loads correctly.

- [ ] **Step 6: No commit (smoke only)**

---

### Task 12: Handoff - progress, roadmap, SESSION_START_PROMPT

**Files:**
- Modify: `docs/progress.md`
- Modify: `docs/roadmap.md`
- Modify: `docs/SESSION_START_PROMPT.md`

- [ ] **Step 1: Add progress entry**

Edit `docs/progress.md`. Add new entry at top (after current sessions header):

```markdown
## 2026-05-13 — Сессия 29 — Этап 18.f CitationPicker ПОЛНОСТЬЮ закрыт

Сессия начата с brainstorming через superpowers skill, scope согласован
с user'ом (MVP только argument-map, бэк full positional citation модель,
1 сессия). Spec в docs/superpowers/specs/2026-05-13-citation-picker-design.md,
plan в docs/superpowers/plans/2026-05-13-citation-picker.md.

### Сделано

[Describe each task: commit hash + что закрыто, как в Сессии 28]

### Решения

[Key decisions, ADR-026 + ADR-027]

### Проблемы

[Any issues hit, fixes]

### Следующий шаг

Этап 18.g - переключить argument-map AddSourceModal на CitationPicker
по умолчанию (текущая интеграция - две кнопки), либо deprecate.
Или Этап 19 - Q&A приложение (новый app в src/apps/qa/).
```

- [ ] **Step 2: Update roadmap.md**

Find Этап 18 section. Mark `[x]` подэтапы 18.f.1-10. If Этап 18 closed entirely,
move/mark.

- [ ] **Step 3: Update SESSION_START_PROMPT.md**

Update top section "КРИТИЧНО для Сессии N+":
- Mark Сессия 29 завершила 18.f
- Update ОТКРЫТО раздел: убрать 18.f, добавить 18.g/19 как next priority
- Update ИНФРАСТРУКТУРА: migrations 22+23 в production-БД
- Update example ПРИВЕТСТВИЕ под текущее состояние

- [ ] **Step 4: Final verify**

```bash
cd backend && ./mvnw verify
cd ../frontend && npm run lint && npm run build && npm test -- --run
```

Expected: all green.

- [ ] **Step 5: Final handoff commit**

```bash
git add docs/progress.md docs/roadmap.md docs/SESSION_START_PROMPT.md
git commit -m "docs: handoff Сессии 29 - этап 18.f CitationPicker ПОЛНОСТЬЮ закрыт

Этап 18.f закрыт в одну сессию через 12 tasks (brainstorming +
spec + plan + 9 implementation коммитов + handoff).

Закрыто:
- ADR-026 Source.bookId FK + миграция 22
- ADR-027 positional citation fields + миграция 23
- NodeCitationService + Controller + 23 backend IT
- Mini-reader extracted в shared/components/reader
- CitationPicker компонент с 3-column layout
- NodeCitationsSection две кнопки + deep links
- BookReaderPage deep link handling

Backend: ~392 IT + 164 unit зелёные. Frontend: ~155 tests pass,
bundle build successful, playwright smoke passed.

Сессия 30 - выбор: 18.g argument-map default to CitationPicker
или Этап 19 Q&A приложение."
```

---

## Self-Review

### Spec coverage check

| Spec requirement | Covered by task |
|---|---|
| Миграция 22 Source.bookId FK | Task 1 |
| Миграция 23 node_sources positional | Task 2 |
| ADR-026, ADR-027 | Task 1 + Task 2 |
| ShamelaToLibraryMapper UPSERT invariant | Task 0 (audit + gotcha) |
| Ensure-or-create Source per book | Task 3 (NodeCitationService + SourceRepository.upsertByBookId) |
| Computed location SQL JOIN | Task 3 (findByNodeIdWithLocation) |
| Validation 4-mode XOR | Task 3 (service + CHECK constraint) |
| POST /api/v1/nodes/:id/citations | Task 3 |
| GET расширенный с computed location | Task 3 |
| 25-30 backend IT | Task 1-3 (~30 total) |
| Extract shared mini-reader | Task 5 |
| PageView selectable + highlightRange | Task 6 |
| PdfViewer selectable + highlightBbox | Task 6 |
| TreeWalker char offsets | Task 6 (textRangeUtils) |
| CitationPicker компонент 3-column | Task 7 |
| NodeCitationsSection две кнопки | Task 8 |
| Click-to-navigate citation rows | Task 8 |
| Deep link query params handling | Task 9 |
| BookReader auto-scroll к highlight | Task 9 (mark scrollIntoView из applyHighlight в Task 6) |
| Frontend lint+build+tests | Task 10 |
| Manual playwright smoke | Task 11 |
| api-contract.md, glossary, architecture | Task 3 |
| Handoff doc updates | Task 12 |

All spec requirements covered.

### Placeholder scan

Searched plan for "TBD", "TODO", "implement later", "fill in details" -
none present. Some Tasks use phrases like "rest of similar fields"
when expanding `SourceRepository.save` to include `book_id` - these
are clear instructions, not placeholders. Where a similar pattern
applies (e.g. "add 6 more service IT covering X/Y/Z scenarios"),
the scenarios are explicitly listed.

### Type consistency

- `CitationMode` enum used consistently as TEXT/PDF/REGION/LEGACY across
  domain, DTO, frontend types
- `NodeSource` constructor signature defined in Task 2 step 6, used
  consistently in Task 3 service via factory methods (textMode, pdfMode,
  regionMode, legacyMode)
- `PdfBbox` record with constructor validation Task 2 step 5, used in
  Task 3 CitationRequest, Task 6 PdfViewer
- `NodeSourceResponse` extended fields (mode, pageId, bookId, etc) Task 3
  step 3, used in Task 8 frontend buildDeepLink
- Backend endpoint paths consistent: `/api/v1/nodes/:nodeId/citations` POST
  in Task 3 step 7, used in Task 7 CitationPicker handleSubmit
- Frontend deep link URL formats consistent в Task 8 buildDeepLink and
  Task 9 BookReaderPage useSearchParams parsing

No naming inconsistencies found.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-13-citation-picker.md`.

Two execution options:

1. **Subagent-Driven (recommended)** - Fresh subagent per task, two-stage review между tasks, fast iteration. Каждая task = независимый subagent dispatch с фокусированным контекстом.

2. **Inline Execution** - Execute tasks in this session sequentially через executing-plans skill. Batch execution с checkpoints для review. Один контекст на всё.

Учитывая что user в режиме автономии и сессия уже частично использована на brainstorming - **рекомендую Inline Execution** через `executing-plans` skill чтобы избежать накладных расходов subagent dispatch'а на 12 tasks.
