package ru.basnukaev.argumentmap.library.shamela.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaCategoryRow;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShamelaCategoryDaoIT {

    @Autowired
    private ShamelaCategoryDao dao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        // pages/titles на book CASCADE - но категория не зависит от book,
        // зато book зависит от category. Чистим в порядке FK
        jdbcTemplate.update("DELETE FROM lib_shamela_page");
        jdbcTemplate.update("DELETE FROM lib_shamela_title");
        jdbcTemplate.update("DELETE FROM lib_shamela_book");
        jdbcTemplate.update("DELETE FROM lib_shamela_author");
        jdbcTemplate.update("DELETE FROM lib_shamela_category");
    }

    @Test
    void upsertAll_inserts_all_rows() {
        List<ShamelaCategoryRow> rows = List.of(
                new ShamelaCategoryRow(1L, "Хадис", 1, false),
                new ShamelaCategoryRow(2L, "Фикх", 2, false),
                new ShamelaCategoryRow(3L, "Тафсир", 3, false)
        );

        int affected = dao.upsertAll(rows);

        assertThat(affected).isEqualTo(3);
        assertThat(dao.findAll()).hasSize(3);
    }

    @Test
    void upsertAll_updates_existing_on_conflict() {
        dao.upsertAll(List.of(new ShamelaCategoryRow(10L, "старое имя", 5, false)));

        dao.upsertAll(List.of(new ShamelaCategoryRow(10L, "новое имя", 7, false)));

        ShamelaCategoryRow reloaded = dao.findById(10L).orElseThrow();
        assertThat(reloaded.name()).isEqualTo("новое имя");
        assertThat(reloaded.displayOrder()).isEqualTo(7);
    }

    @Test
    void upsertAll_marks_deleted_with_now_timestamp() {
        dao.upsertAll(List.of(new ShamelaCategoryRow(20L, "Удалённая", null, true)));

        ShamelaCategoryRow row = dao.findById(20L).orElseThrow();
        assertThat(row.deleted()).isTrue();
        Boolean hasTimestamp = jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM lib_shamela_category WHERE id = 20",
                Boolean.class
        );
        assertThat(hasTimestamp).isTrue();
    }

    @Test
    void upsertAll_clears_deleted_at_when_undeleted() {
        dao.upsertAll(List.of(new ShamelaCategoryRow(30L, "name", 1, true)));

        dao.upsertAll(List.of(new ShamelaCategoryRow(30L, "name", 1, false)));

        Boolean hasTimestamp = jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM lib_shamela_category WHERE id = 30",
                Boolean.class
        );
        assertThat(hasTimestamp).isFalse();
        assertThat(dao.findById(30L).orElseThrow().deleted()).isFalse();
    }

    @Test
    void upsertAll_handles_empty_list() {
        int affected = dao.upsertAll(List.of());

        assertThat(affected).isZero();
        assertThat(dao.findAll()).isEmpty();
    }

    @Test
    void findById_returns_empty_when_missing() {
        Optional<ShamelaCategoryRow> result = dao.findById(99999L);

        assertThat(result).isEmpty();
    }
}
