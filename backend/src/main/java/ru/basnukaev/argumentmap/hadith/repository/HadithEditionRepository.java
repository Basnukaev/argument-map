package ru.basnukaev.argumentmap.hadith.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.domain.HadithEdition;

@Repository
public class HadithEditionRepository {

    private static final String COLUMNS = "id, hadith_id, edition_name, page, volume";

    private static final RowMapper<HadithEdition> ROW_MAPPER = (rs, rn) -> new HadithEdition(
            rs.getObject("id", UUID.class),
            rs.getObject("hadith_id", UUID.class),
            rs.getString("edition_name"),
            (Integer) rs.getObject("page"),
            (Integer) rs.getObject("volume")
    );

    private final JdbcTemplate jdbcTemplate;

    public HadithEditionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HadithEdition save(HadithEdition e) {
        jdbcTemplate.update(
                "INSERT INTO hd_hadith_editions (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?)",
                e.id(), e.hadithId(), e.editionName(), e.page(), e.volume());
        return e;
    }

    public List<HadithEdition> findByHadithId(UUID hadithId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_hadith_editions WHERE hadith_id = ? "
                        + "ORDER BY volume NULLS LAST, page NULLS LAST",
                ROW_MAPPER, hadithId);
    }

    public void deleteByHadithId(UUID hadithId) {
        jdbcTemplate.update("DELETE FROM hd_hadith_editions WHERE hadith_id = ?", hadithId);
    }
}
