package ru.basnukaev.argumentmap.hadith.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;

/**
 * Доступ к hd_sanads + hd_sanad_narrators. Vision 49d Section 2.6.
 */
@Repository
public class SanadRepository {

    private static final String SANAD_COLS =
            "id, hadith_id, chain_grade, compiled_by_id, compiled_in_book_id, "
                    + "primary_chain, metadata, created_at";

    private static final RowMapper<Sanad> SANAD_MAPPER = (rs, rn) -> new Sanad(
            rs.getObject("id", UUID.class),
            rs.getObject("hadith_id", UUID.class),
            rs.getString("chain_grade"),
            rs.getObject("compiled_by_id", UUID.class),
            rs.getObject("compiled_in_book_id", UUID.class),
            rs.getBoolean("primary_chain"),
            rs.getString("metadata"),
            instant(rs, "created_at")
    );

    private static final RowMapper<SanadNarrator> SN_MAPPER = (rs, rn) -> new SanadNarrator(
            rs.getObject("sanad_id", UUID.class),
            rs.getInt("position"),
            rs.getObject("narrator_id", UUID.class),
            rs.getString("transmission_phrase")
    );

    private final JdbcTemplate jdbcTemplate;

    public SanadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Sanad save(Sanad s) {
        jdbcTemplate.update(
                "INSERT INTO hd_sanads (" + SANAD_COLS + ") VALUES "
                        + "(?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                s.id(), s.hadithId(), s.chainGrade(), s.compiledById(),
                s.compiledInBookId(), s.primaryChain(), s.metadata(),
                odt(s.createdAt())
        );
        return s;
    }

    public List<Sanad> findByHadithId(UUID hadithId) {
        return jdbcTemplate.query(
                "SELECT " + SANAD_COLS + " FROM hd_sanads WHERE hadith_id = ? "
                        + "ORDER BY primary_chain DESC, created_at ASC",
                SANAD_MAPPER, hadithId
        );
    }

    /**
     * Удаляет все цепи хадиса и их линковки на нарраторов — для
     * delete-recreate идемпотентного персиста извлечённого иснада (ADR-059
     * amendment): повторный импорт хадиса обновляет цепь, а не плодит дубли.
     *
     * <p>Линковки сносим явно (хотя FK {@code fk_hd_sn_sanad} с ON DELETE
     * CASCADE подхватил бы их сам) — порядок FK очевиден и не зависим от
     * каскада. Сами нарраторы (hd_narrators) НЕ трогаем: они шарятся между
     * хадисами/цепями (дедуп по normalized-name), удалять опасно.
     */
    public void deleteByHadithId(UUID hadithId) {
        jdbcTemplate.update(
                "DELETE FROM hd_sanad_narrators WHERE sanad_id IN "
                        + "(SELECT id FROM hd_sanads WHERE hadith_id = ?)",
                hadithId);
        jdbcTemplate.update("DELETE FROM hd_sanads WHERE hadith_id = ?", hadithId);
    }

    public void saveNarratorLink(SanadNarrator link) {
        jdbcTemplate.update(
                "INSERT INTO hd_sanad_narrators (sanad_id, position, narrator_id, transmission_phrase) "
                        + "VALUES (?, ?, ?, ?)",
                link.sanadId(), link.position(), link.narratorId(),
                link.transmissionPhrase()
        );
    }

    public List<SanadNarrator> findNarratorsBySanadId(UUID sanadId) {
        return jdbcTemplate.query(
                "SELECT sanad_id, position, narrator_id, transmission_phrase "
                        + "FROM hd_sanad_narrators WHERE sanad_id = ? ORDER BY position ASC",
                SN_MAPPER, sanadId
        );
    }

    /**
     * Bulk fetch narrators для нескольких sanads одной волной (избегаем
     * N+1 на bundled hadith detail endpoint).
     */
    public List<SanadNarrator> findNarratorsBySanadIds(List<UUID> sanadIds) {
        if (sanadIds.isEmpty()) return List.of();
        String placeholders = String.join(",", sanadIds.stream().map(id -> "?").toList());
        return jdbcTemplate.query(
                "SELECT sanad_id, position, narrator_id, transmission_phrase "
                        + "FROM hd_sanad_narrators WHERE sanad_id IN (" + placeholders + ") "
                        + "ORDER BY sanad_id, position ASC",
                SN_MAPPER, sanadIds.toArray()
        );
    }
}
