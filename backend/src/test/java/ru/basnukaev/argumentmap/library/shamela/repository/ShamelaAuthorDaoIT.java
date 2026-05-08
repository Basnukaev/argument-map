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
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaAuthorRow;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShamelaAuthorDaoIT {

    @Autowired
    private ShamelaAuthorDao dao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM lib_shamela_page");
        jdbcTemplate.update("DELETE FROM lib_shamela_title");
        jdbcTemplate.update("DELETE FROM lib_shamela_book");
        jdbcTemplate.update("DELETE FROM lib_shamela_author");
    }

    @Test
    void upsertAll_inserts_all_rows() {
        List<ShamelaAuthorRow> rows = List.of(
                new ShamelaAuthorRow(1L, "Ибн Таймийя", "biography", 728, false),
                new ShamelaAuthorRow(2L, "Ибн Касир", null, 774, false)
        );

        int affected = dao.upsertAll(rows);

        assertThat(affected).isEqualTo(2);
        assertThat(dao.findAll()).hasSize(2);
    }

    @Test
    void upsertAll_updates_existing_on_conflict() {
        dao.upsertAll(List.of(new ShamelaAuthorRow(5L, "old", null, null, false)));

        dao.upsertAll(List.of(new ShamelaAuthorRow(5L, "new", "обновлённая биография", 800, false)));

        ShamelaAuthorRow reloaded = dao.findById(5L).orElseThrow();
        assertThat(reloaded.name()).isEqualTo("new");
        assertThat(reloaded.biography()).isEqualTo("обновлённая биография");
        assertThat(reloaded.deathYear()).isEqualTo(800);
    }

    @Test
    void upsertAll_marks_deleted_with_now_timestamp() {
        dao.upsertAll(List.of(new ShamelaAuthorRow(10L, "deleted", null, null, true)));

        assertThat(dao.findById(10L).orElseThrow().deleted()).isTrue();
    }

    @Test
    void upsertAll_clears_deleted_at_when_undeleted() {
        dao.upsertAll(List.of(new ShamelaAuthorRow(11L, "x", null, null, true)));

        dao.upsertAll(List.of(new ShamelaAuthorRow(11L, "x", null, null, false)));

        assertThat(dao.findById(11L).orElseThrow().deleted()).isFalse();
    }

    @Test
    void upsertAll_handles_empty_list() {
        assertThat(dao.upsertAll(List.of())).isZero();
    }

    @Test
    void upsertAll_persists_long_biography() {
        String longBio = "ا".repeat(20_000);

        dao.upsertAll(List.of(new ShamelaAuthorRow(20L, "автор", longBio, null, false)));

        assertThat(dao.findById(20L).orElseThrow().biography()).hasSize(20_000);
    }

    @Test
    void upsertAll_handles_null_death_year() {
        dao.upsertAll(List.of(new ShamelaAuthorRow(30L, "имя", null, null, false)));

        assertThat(dao.findById(30L).orElseThrow().deathYear()).isNull();
    }

    @Test
    void findById_returns_empty_when_missing() {
        assertThat(dao.findById(99999L)).isEmpty();
    }
}
