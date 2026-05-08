package ru.basnukaev.argumentmap.library.shamela.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaAuthorRow;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaBookRow;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaCategoryRow;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShamelaBookDaoIT {

    @Autowired
    private ShamelaBookDao dao;

    @Autowired
    private ShamelaCategoryDao categoryDao;

    @Autowired
    private ShamelaAuthorDao authorDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM lib_shamela_page");
        jdbcTemplate.update("DELETE FROM lib_shamela_title");
        jdbcTemplate.update("DELETE FROM lib_shamela_book");
        jdbcTemplate.update("DELETE FROM lib_shamela_author");
        jdbcTemplate.update("DELETE FROM lib_shamela_category");
    }

    @Test
    void upsertAll_inserts_and_findById_returns_row() {
        dao.upsertAll(List.of(book(1L, "Тестовая книга", null, null)));

        ShamelaBookRow reloaded = dao.findById(1L).orElseThrow();
        assertThat(reloaded.id()).isEqualTo(1L);
        assertThat(reloaded.name()).isEqualTo("Тестовая книга");
        assertThat(reloaded.majorRelease()).isEqualTo(1);
    }

    @Test
    void upsertAll_updates_existing_on_conflict() {
        dao.upsertAll(List.of(book(2L, "first", null, null)));

        dao.upsertAll(List.of(book(2L, "second", null, null)));

        assertThat(dao.findById(2L).orElseThrow().name()).isEqualTo("second");
        assertThat(dao.findAll()).hasSize(1);
    }

    @Test
    void upsertAll_persists_pdf_links_jsonb() {
        String pdfJson = "{\"files\":[\"/1/41557.pdf\"],\"size\":1445468}";
        dao.upsertAll(List.of(new ShamelaBookRow(
                3L, "PDF book", null, null, null, null, null,
                1, 0, null, null, pdfJson, null, false
        )));

        ShamelaBookRow reloaded = dao.findById(3L).orElseThrow();
        assertThat(reloaded.pdfLinksJson())
                .contains("\"files\"")
                .contains("/1/41557.pdf")
                .contains("1445468");
    }

    @Test
    void upsertAll_handles_null_jsonb_fields() {
        dao.upsertAll(List.of(book(4L, "noJson", null, null)));

        ShamelaBookRow reloaded = dao.findById(4L).orElseThrow();
        assertThat(reloaded.pdfLinksJson()).isNull();
        assertThat(reloaded.extraMetadataJson()).isNull();
    }

    @Test
    void upsertAll_sets_imported_at_on_insert() {
        dao.upsertAll(List.of(book(5L, "imported", null, null)));

        Boolean hasImportedAt = jdbcTemplate.queryForObject(
                "SELECT imported_at IS NOT NULL FROM lib_shamela_book WHERE id = 5",
                Boolean.class
        );
        assertThat(hasImportedAt).isTrue();
    }

    @Test
    void upsertAll_respects_fk_to_category_and_author() {
        categoryDao.upsertAll(List.of(new ShamelaCategoryRow(100L, "Хадис", 1, false)));
        authorDao.upsertAll(List.of(new ShamelaAuthorRow(200L, "Ибн Таймийя", null, 728, false)));

        dao.upsertAll(List.of(book(6L, "Иктида", 100L, 200L)));

        ShamelaBookRow reloaded = dao.findById(6L).orElseThrow();
        assertThat(reloaded.categoryId()).isEqualTo(100L);
        assertThat(reloaded.authorId()).isEqualTo(200L);
    }

    @Test
    void upsertAll_with_invalid_fk_throws() {
        assertThatThrownBy(() -> dao.upsertAll(List.of(book(7L, "broken", 999_999L, null))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void upsertAll_handles_empty_list() {
        assertThat(dao.upsertAll(List.of())).isZero();
    }

    @Test
    void upsertAll_marks_deleted_with_timestamp() {
        dao.upsertAll(List.of(new ShamelaBookRow(
                8L, "deleted", null, null, null, null, null,
                1, 0, null, null, null, null, true
        )));

        Boolean hasDeletedAt = jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM lib_shamela_book WHERE id = 8",
                Boolean.class
        );
        assertThat(hasDeletedAt).isTrue();
        assertThat(dao.findById(8L).orElseThrow().deleted()).isTrue();
    }

    @Test
    void findAll_returns_all_rows_ordered_by_id() {
        dao.upsertAll(List.of(
                book(50L, "C", null, null),
                book(40L, "B", null, null),
                book(30L, "A", null, null)
        ));

        assertThat(dao.findAll())
                .extracting(ShamelaBookRow::id)
                .containsExactly(30L, 40L, 50L);
    }

    private static ShamelaBookRow book(long id, String name, Long categoryId, Long authorId) {
        return new ShamelaBookRow(
                id, name, categoryId, authorId,
                null, null, null,
                1, 0,
                null, null, null, null,
                false
        );
    }
}
