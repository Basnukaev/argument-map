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
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaTitleRow;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShamelaTitleDaoIT {

    private static final long BOOK_ID = 77L;

    @Autowired
    private ShamelaTitleDao dao;

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
    void upsertAll_persists_titles_with_composite_key() {
        dao.upsertAll(List.of(
                new ShamelaTitleRow(BOOK_ID, 1, "Глава 1", "1", null),
                new ShamelaTitleRow(BOOK_ID, 2, "Глава 1.1", "5", 1)
        ));

        assertThat(dao.findByBookIdAndId(BOOK_ID, 2).orElseThrow().parentId()).isEqualTo(1);
        assertThat(dao.countByBookId(BOOK_ID)).isEqualTo(2);
    }

    @Test
    void upsertAll_updates_on_conflict_by_book_id_and_id() {
        dao.upsertAll(List.of(
                new ShamelaTitleRow(BOOK_ID, 1, "old", "1", null)
        ));

        dao.upsertAll(List.of(
                new ShamelaTitleRow(BOOK_ID, 1, "new", "10", 99)
        ));

        ShamelaTitleRow reloaded = dao.findByBookIdAndId(BOOK_ID, 1).orElseThrow();
        assertThat(reloaded.content()).isEqualTo("new");
        assertThat(reloaded.pageRef()).isEqualTo("10");
        assertThat(reloaded.parentId()).isEqualTo(99);
    }

    @Test
    void upsertAll_persists_arabic_content_intact() {
        String arabicTitle = "بَابُ الإِيمَانِ";

        dao.upsertAll(List.of(
                new ShamelaTitleRow(BOOK_ID, 1, arabicTitle, "1", null)
        ));

        assertThat(dao.findByBookIdAndId(BOOK_ID, 1).orElseThrow().content())
                .isEqualTo(arabicTitle);
    }

    @Test
    void upsertAll_handles_null_parent_id() {
        dao.upsertAll(List.of(
                new ShamelaTitleRow(BOOK_ID, 1, "корневой", "1", null)
        ));

        assertThat(dao.findByBookIdAndId(BOOK_ID, 1).orElseThrow().parentId()).isNull();
    }

    @Test
    void cascade_delete_from_book_removes_titles() {
        dao.upsertAll(List.of(
                new ShamelaTitleRow(BOOK_ID, 1, "глава", "1", null)
        ));
        assertThat(dao.countByBookId(BOOK_ID)).isEqualTo(1);

        jdbcTemplate.update("DELETE FROM lib_shamela_book WHERE id = ?", BOOK_ID);

        assertThat(dao.countByBookId(BOOK_ID)).isZero();
    }

    @Test
    void countByBookId_counts_correctly() {
        dao.upsertAll(List.of(
                new ShamelaTitleRow(BOOK_ID, 1, "a", "1", null),
                new ShamelaTitleRow(BOOK_ID, 2, "b", "2", 1)
        ));

        assertThat(dao.countByBookId(BOOK_ID)).isEqualTo(2);
    }

    @Test
    void upsertAll_handles_empty_list() {
        assertThat(dao.upsertAll(List.of())).isZero();
    }
}
