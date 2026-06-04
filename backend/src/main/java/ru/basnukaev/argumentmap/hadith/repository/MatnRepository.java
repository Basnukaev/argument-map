package ru.basnukaev.argumentmap.hadith.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.domain.Matn;

/**
 * Доступ к hd_matns. Vision 49d Section 2.6.
 */
@Repository
public class MatnRepository {

    private static final String COLUMNS =
            "id, hadith_id, text_ar, text_ar_normalized, text_ru, text_en, "
                    + "collection_id, printed_number, page_no, volume, "
                    + "is_primary, divergence_summary, metadata, created_at";

    private static final RowMapper<Matn> ROW_MAPPER = (rs, rn) -> new Matn(
            rs.getObject("id", UUID.class),
            rs.getObject("hadith_id", UUID.class),
            rs.getString("text_ar"),
            rs.getString("text_ar_normalized"),
            rs.getString("text_ru"),
            rs.getString("text_en"),
            rs.getObject("collection_id", UUID.class),
            (Integer) rs.getObject("printed_number"),
            (Integer) rs.getObject("page_no"),
            (Integer) rs.getObject("volume"),
            rs.getBoolean("is_primary"),
            rs.getString("divergence_summary"),
            rs.getString("metadata"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public MatnRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Matn save(Matn m) {
        jdbcTemplate.update(
                "INSERT INTO hd_matns (" + COLUMNS + ") VALUES "
                        + "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                m.id(), m.hadithId(), m.textAr(), m.textArNormalized(),
                m.textRu(), m.textEn(), m.collectionId(),
                m.printedNumber(), m.pageNo(), m.volume(),
                m.isPrimary(), m.divergenceSummary(), m.metadata(),
                odt(m.createdAt())
        );
        return m;
    }

    public List<Matn> findByHadithId(UUID hadithId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_matns WHERE hadith_id = ? "
                        + "ORDER BY is_primary DESC, created_at ASC",
                ROW_MAPPER, hadithId
        );
    }

    /**
     * Удаляет все матны данного хадиса — примитив идемпотентного реимпорта
     * (delete-recreate паттерн маппера alminasa, план 3, решение 9).
     */
    public void deleteByHadithId(UUID hadithId) {
        jdbcTemplate.update("DELETE FROM hd_matns WHERE hadith_id = ?", hadithId);
    }

    /**
     * Текст первичного matn (text_ar) по списку hadith-id, одним запросом —
     * для preview-карточек списка (избегаем N+1). Возвращает map hadith_id →
     * text_ar только для хадисов с is_primary matn.
     */
    public Map<UUID, String> findPrimaryTextByHadithIds(List<UUID> hadithIds) {
        if (hadithIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> texts = new HashMap<>();
        String placeholders = hadithIds.stream().map(x -> "?").collect(Collectors.joining(","));
        jdbcTemplate.query(
                "SELECT hadith_id, text_ar FROM hd_matns "
                        + "WHERE is_primary = true AND hadith_id IN (" + placeholders + ")",
                (java.sql.ResultSet rs) -> {
                    texts.put(rs.getObject("hadith_id", UUID.class), rs.getString("text_ar"));
                },
                hadithIds.toArray());
        return texts;
    }
}
