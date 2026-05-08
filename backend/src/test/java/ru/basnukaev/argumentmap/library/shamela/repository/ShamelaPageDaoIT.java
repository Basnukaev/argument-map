package ru.basnukaev.argumentmap.library.shamela.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaBookRow;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaPageRow;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShamelaPageDaoIT {

    private static final long BOOK_ID = 42L;

    @Autowired
    private ShamelaPageDao dao;

    @Autowired
    private ShamelaBookDao bookDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM lib_shamela_page");
        jdbcTemplate.update("DELETE FROM lib_shamela_title");
        jdbcTemplate.update("DELETE FROM lib_shamela_book");
        jdbcTemplate.update("DELETE FROM lib_shamela_author");
        jdbcTemplate.update("DELETE FROM lib_shamela_category");

        bookDao.upsertAll(List.of(new ShamelaBookRow(
                BOOK_ID, "test book", null, null, null, null, null,
                1, 0, null, null, null, null, false
        )));
    }

    @Test
    void upsertAll_persists_pages_with_composite_key() {
        dao.upsertAll(List.of(
                new ShamelaPageRow(BOOK_ID, 1, "page-1-content", null, null, null, null),
                new ShamelaPageRow(BOOK_ID, 2, "page-2-content", null, null, null, null)
        ));

        assertThat(dao.findByBookIdAndId(BOOK_ID, 1).orElseThrow().content())
                .isEqualTo("page-1-content");
        assertThat(dao.findByBookIdAndId(BOOK_ID, 2).orElseThrow().content())
                .isEqualTo("page-2-content");
    }

    @Test
    void upsertAll_updates_on_conflict_by_book_id_and_id() {
        dao.upsertAll(List.of(
                new ShamelaPageRow(BOOK_ID, 1, "old", null, null, null, null)
        ));

        dao.upsertAll(List.of(
                new ShamelaPageRow(BOOK_ID, 1, "new", "часть 1", "5", "5", "сервисы")
        ));

        ShamelaPageRow reloaded = dao.findByBookIdAndId(BOOK_ID, 1).orElseThrow();
        assertThat(reloaded.content()).isEqualTo("new");
        assertThat(reloaded.part()).isEqualTo("часть 1");
        assertThat(reloaded.printedPage()).isEqualTo("5");
        assertThat(reloaded.services()).isEqualTo("сервисы");
        assertThat(dao.countByBookId(BOOK_ID)).isEqualTo(1);
    }

    @Test
    void upsertAll_persists_arabic_content_intact() {
        String arabicHtml = "<p>الحمد لله رب العالمين</p>";

        dao.upsertAll(List.of(
                new ShamelaPageRow(BOOK_ID, 1, arabicHtml, null, null, null, null)
        ));

        assertThat(dao.findByBookIdAndId(BOOK_ID, 1).orElseThrow().content())
                .isEqualTo(arabicHtml);
    }

    @Test
    void countByBookId_counts_correctly() {
        dao.upsertAll(List.of(
                new ShamelaPageRow(BOOK_ID, 1, "a", null, null, null, null),
                new ShamelaPageRow(BOOK_ID, 2, "b", null, null, null, null),
                new ShamelaPageRow(BOOK_ID, 3, "c", null, null, null, null)
        ));

        assertThat(dao.countByBookId(BOOK_ID)).isEqualTo(3);
        assertThat(dao.countByBookId(99999L)).isZero();
    }

    @Test
    void cascade_delete_from_book_removes_pages() {
        dao.upsertAll(List.of(
                new ShamelaPageRow(BOOK_ID, 1, "x", null, null, null, null)
        ));
        assertThat(dao.countByBookId(BOOK_ID)).isEqualTo(1);

        jdbcTemplate.update("DELETE FROM lib_shamela_book WHERE id = ?", BOOK_ID);

        assertThat(dao.countByBookId(BOOK_ID)).isZero();
    }

    @Test
    void upsertAll_handles_empty_list() {
        assertThat(dao.upsertAll(List.of())).isZero();
    }

    @Test
    void findByBookIdAndId_returns_empty_when_missing() {
        assertThat(dao.findByBookIdAndId(BOOK_ID, 99)).isEmpty();
    }
}
