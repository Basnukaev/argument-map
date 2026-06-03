package ru.basnukaev.argumentmap.hadith.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.domain.HadithRuling;

@Repository
public class HadithRulingRepository {

    private static final String COLUMNS =
            "id, hadith_id, ruler_name, ruler_death_year, ruling_text, "
                    + "book_name, page, volume, metadata, created_at";

    private static final RowMapper<HadithRuling> ROW_MAPPER = (rs, rn) -> new HadithRuling(
            rs.getObject("id", UUID.class),
            rs.getObject("hadith_id", UUID.class),
            rs.getString("ruler_name"),
            (Integer) rs.getObject("ruler_death_year"),
            rs.getString("ruling_text"),
            rs.getString("book_name"),
            (Integer) rs.getObject("page"),
            (Integer) rs.getObject("volume"),
            rs.getString("metadata"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public HadithRulingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HadithRuling save(HadithRuling r) {
        jdbcTemplate.update(
                "INSERT INTO hd_rulings (" + COLUMNS + ") VALUES "
                        + "(?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                r.id(), r.hadithId(), r.rulerName(), r.rulerDeathYear(), r.rulingText(),
                r.bookName(), r.page(), r.volume(), r.metadata(), odt(r.createdAt()));
        return r;
    }

    public List<HadithRuling> findByHadithId(UUID hadithId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_rulings WHERE hadith_id = ? "
                        + "ORDER BY ruler_death_year NULLS LAST, created_at ASC",
                ROW_MAPPER, hadithId);
    }

    public void deleteByHadithId(UUID hadithId) {
        jdbcTemplate.update("DELETE FROM hd_rulings WHERE hadith_id = ?", hadithId);
    }
}
