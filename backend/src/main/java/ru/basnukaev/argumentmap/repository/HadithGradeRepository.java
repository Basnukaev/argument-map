package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.HadithGrade;
import ru.basnukaev.argumentmap.domain.HadithGradeValue;
import ru.basnukaev.argumentmap.domain.HadithGradeWithScholar;

/**
 * JDBC-репозиторий для {@link HadithGrade}. CRUD + JOIN-helper для
 * read-model'и с scholar info.
 */
@Repository
public class HadithGradeRepository {

    private static final String COLUMNS =
            "id, source_id, scholar_id, grade, grade_citation, comment, created_at, created_by";

    private static final RowMapper<HadithGrade> ROW_MAPPER = (rs, rn) -> new HadithGrade(
            rs.getObject("id", UUID.class),
            rs.getObject("source_id", UUID.class),
            rs.getObject("scholar_id", UUID.class),
            HadithGradeValue.valueOf(rs.getString("grade")),
            rs.getString("grade_citation"),
            rs.getString("comment"),
            instant(rs, "created_at"),
            rs.getObject("created_by", UUID.class)
    );

    /**
     * JOIN sql для read-model'и с denormalized scholar info. Используется в
     * {@link #findBySourceIdWithScholar(UUID)} для GET endpoint'а.
     */
    private static final String JOIN_SCHOLAR_SQL =
            "SELECT hg.id, hg.source_id, hg.scholar_id, "
                    + "a.name AS scholar_name, a.full_name AS scholar_full_name, "
                    + "a.death_year_hijri AS scholar_death_year_hijri, "
                    + "hg.grade, hg.grade_citation, hg.comment, hg.created_at, hg.created_by "
                    + "FROM hadith_grades hg "
                    + "JOIN authorities a ON a.id = hg.scholar_id ";

    private static final RowMapper<HadithGradeWithScholar> JOIN_ROW_MAPPER = (rs, rn) -> {
        int deathYear = rs.getInt("scholar_death_year_hijri");
        Integer deathYearOrNull = rs.wasNull() ? null : deathYear;
        return new HadithGradeWithScholar(
                rs.getObject("id", UUID.class),
                rs.getObject("source_id", UUID.class),
                rs.getObject("scholar_id", UUID.class),
                rs.getString("scholar_name"),
                rs.getString("scholar_full_name"),
                deathYearOrNull,
                HadithGradeValue.valueOf(rs.getString("grade")),
                rs.getString("grade_citation"),
                rs.getString("comment"),
                instant(rs, "created_at"),
                rs.getObject("created_by", UUID.class)
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public HadithGradeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HadithGrade save(HadithGrade grade) {
        jdbcTemplate.update(
                "INSERT INTO hadith_grades (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                grade.id(),
                grade.sourceId(),
                grade.scholarId(),
                grade.grade().name(),
                grade.gradeCitation(),
                grade.comment(),
                odt(grade.createdAt()),
                grade.createdBy()
        );
        return grade;
    }

    public Optional<HadithGrade> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hadith_grades WHERE id = ?",
                ROW_MAPPER, id
        ).stream().findFirst();
    }

    public List<HadithGrade> findBySourceId(UUID sourceId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hadith_grades WHERE source_id = ? ORDER BY created_at",
                ROW_MAPPER, sourceId
        );
    }

    public List<HadithGradeWithScholar> findBySourceIdWithScholar(UUID sourceId) {
        return jdbcTemplate.query(
                JOIN_SCHOLAR_SQL + "WHERE hg.source_id = ? ORDER BY hg.created_at",
                JOIN_ROW_MAPPER, sourceId
        );
    }

    public boolean existsForSourceAndScholar(UUID sourceId, UUID scholarId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM hadith_grades WHERE source_id = ? AND scholar_id = ?",
                Long.class, sourceId, scholarId
        );
        return count != null && count > 0;
    }

    /**
     * Обновляет grade / gradeCitation / comment. Возвращает true если строка
     * существовала; иначе false (caller должен реагировать 404).
     */
    public boolean update(UUID id, HadithGradeValue newGrade, String gradeCitation, String comment) {
        return jdbcTemplate.update(
                "UPDATE hadith_grades SET grade = ?, grade_citation = ?, comment = ? WHERE id = ?",
                newGrade.name(), gradeCitation, comment, id
        ) > 0;
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM hadith_grades WHERE id = ?", id) > 0;
    }
}
