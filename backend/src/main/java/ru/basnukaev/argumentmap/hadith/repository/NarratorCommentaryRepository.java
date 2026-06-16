package ru.basnukaev.argumentmap.hadith.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.domain.NarratorCommentary;

/**
 * Репозиторий {@code hd_narrator_commentaries} (джарх/таʿдиль о рави, ADR-061).
 * Зеркало {@link NarratorRelationRepository}: save / findByNarratorId / delete.
 * {@code comments} сериализуется в jsonb-массив через {@link ObjectMapper}
 * (НЕ конкатенация строк), читается обратно RowMapper'ом.
 */
@Repository
public class NarratorCommentaryRepository {

    private static final String COLUMNS =
            "id, narrator_id, commenter, commenter_death_year, book_name, author, "
                    + "page, volume, comments, metadata, created_at";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<NarratorCommentary> rowMapper;

    public NarratorCommentaryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.rowMapper = (rs, rn) -> new NarratorCommentary(
                rs.getObject("id", UUID.class),
                rs.getObject("narrator_id", UUID.class),
                rs.getString("commenter"),
                (Integer) rs.getObject("commenter_death_year"),
                rs.getString("book_name"),
                rs.getString("author"),
                (Integer) rs.getObject("page"),
                (Integer) rs.getObject("volume"),
                readComments(rs.getString("comments")),
                rs.getString("metadata"),
                instant(rs, "created_at")
        );
    }

    public NarratorCommentary save(NarratorCommentary c) {
        jdbcTemplate.update(
                "INSERT INTO hd_narrator_commentaries (" + COLUMNS + ") VALUES "
                        + "(?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)",
                c.id(), c.narratorId(), c.commenter(), c.commenterDeathYear(),
                c.bookName(), c.author(), c.page(), c.volume(),
                writeComments(c.comments()), c.metadata(), odt(c.createdAt()));
        return c;
    }

    /**
     * Цитаты о рави. Сортировка по году смерти критика (хронология джарх-
     * та'диля) с NULLS LAST, затем по книге (решение 5 плана).
     */
    public List<NarratorCommentary> findByNarratorId(UUID narratorId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_narrator_commentaries WHERE narrator_id = ? "
                        + "ORDER BY commenter_death_year ASC NULLS LAST, book_name ASC NULLS LAST, "
                        + "created_at ASC",
                rowMapper, narratorId);
    }

    public void deleteByNarratorId(UUID narratorId) {
        jdbcTemplate.update("DELETE FROM hd_narrator_commentaries WHERE narrator_id = ?", narratorId);
    }

    /** {@code List<String>} → jsonb-литерал массива (НЕ конкатенация). */
    private String writeComments(List<String> comments) {
        try {
            return objectMapper.writeValueAsString(comments == null ? List.of() : comments);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("не удалось сериализовать comments рави-цитаты", e);
        }
    }

    /** jsonb-массив строк → {@code List<String>}. */
    private List<String> readComments(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("битый jsonb comments рави-цитаты: " + json, e);
        }
    }
}
