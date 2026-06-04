package ru.basnukaev.argumentmap.hadith.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.domain.HadithCrossref;

@Repository
public class HadithCrossrefRepository {

    private static final String COLUMNS =
            "id, hadith_id, related_external_id, related_hadith_id, "
                    + "relation_type, note, created_at";

    private static final RowMapper<HadithCrossref> ROW_MAPPER = (rs, rn) -> new HadithCrossref(
            rs.getObject("id", UUID.class),
            rs.getObject("hadith_id", UUID.class),
            rs.getString("related_external_id"),
            rs.getObject("related_hadith_id", UUID.class),
            rs.getString("relation_type"),
            rs.getString("note"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public HadithCrossrefRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HadithCrossref save(HadithCrossref c) {
        jdbcTemplate.update(
                "INSERT INTO hd_hadith_crossrefs (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                c.id(), c.hadithId(), c.relatedExternalId(), c.relatedHadithId(),
                c.relationType(), c.note(), odt(c.createdAt()));
        return c;
    }

    public List<HadithCrossref> findByHadithId(UUID hadithId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_hadith_crossrefs WHERE hadith_id = ? "
                        + "ORDER BY created_at ASC",
                ROW_MAPPER, hadithId);
    }

    public void deleteByHadithId(UUID hadithId) {
        jdbcTemplate.update("DELETE FROM hd_hadith_crossrefs WHERE hadith_id = ?", hadithId);
    }

    /**
     * Resolve-проход: проставляет {@code related_hadith_id} для всех crossref-строк,
     * у которых FK ещё NULL, по совпадению {@code related_external_id} с
     * {@code external_id} уже импортированных alminasa-хадисов.
     * Один UPDATE вместо N+1 — индекс {@code idx_hd_crossrefs_related} покрывает.
     * Вызывается после полного батч-импорта (re-runnable: повторный вызов — нет эффекта).
     *
     * @return число обновлённых строк
     */
    public int resolveRelatedHadithIds() {
        return jdbcTemplate.update(
                "UPDATE hd_hadith_crossrefs c "
                        + "SET related_hadith_id = h.id "
                        + "FROM hd_hadiths h "
                        + "WHERE c.related_hadith_id IS NULL "
                        + "AND h.external_source = 'alminasa' "
                        + "AND h.external_id = c.related_external_id");
    }
}
