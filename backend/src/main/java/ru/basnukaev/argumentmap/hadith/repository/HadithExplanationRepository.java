package ru.basnukaev.argumentmap.hadith.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

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
                odt(e.createdAt()));
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
}
