package ru.basnukaev.argumentmap.hadith.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.domain.Narrator;

/**
 * Доступ к hd_narrators. Vision 49d Section 2.6 Phase 1.
 * JDBC Template, snake_case columns, без JPA.
 */
@Repository
public class NarratorRepository {

    private static final String COLUMNS =
            "id, authority_id, name_ar, name_ar_normalized, kunya, laqab, "
                    + "year_birth_hijri, year_death_hijri, birthplace, death_place, "
                    + "primary_residence, reliability_grade, reliability_comment, "
                    + "transmitted_count_cached, metadata, created_at, "
                    + "external_source, external_id, tabaqa, grade_text, "
                    + "born_on_text, died_on_text";

    private static final RowMapper<Narrator> ROW_MAPPER = (rs, rn) -> new Narrator(
            rs.getObject("id", UUID.class),
            rs.getObject("authority_id", UUID.class),
            rs.getString("name_ar"),
            rs.getString("name_ar_normalized"),
            rs.getString("kunya"),
            rs.getString("laqab"),
            (Integer) rs.getObject("year_birth_hijri"),
            (Integer) rs.getObject("year_death_hijri"),
            rs.getString("birthplace"),
            rs.getString("death_place"),
            rs.getString("primary_residence"),
            rs.getString("reliability_grade"),
            rs.getString("reliability_comment"),
            rs.getInt("transmitted_count_cached"),
            rs.getString("metadata"),
            instant(rs, "created_at"),
            rs.getString("external_source"),
            rs.getString("external_id"),
            rs.getString("tabaqa"),
            rs.getString("grade_text"),
            rs.getString("born_on_text"),
            rs.getString("died_on_text")
    );

    private final JdbcTemplate jdbcTemplate;

    public NarratorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Narrator save(Narrator n) {
        jdbcTemplate.update(
                "INSERT INTO hd_narrators (" + COLUMNS + ") VALUES ("
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, "
                        + "?, ?, ?, ?, ?, ?)",
                n.id(), n.authorityId(), n.nameAr(), n.nameArNormalized(),
                n.kunya(), n.laqab(), n.yearBirthHijri(), n.yearDeathHijri(),
                n.birthplace(), n.deathPlace(), n.primaryResidence(),
                n.reliabilityGrade(), n.reliabilityComment(),
                n.transmittedCountCached(), n.metadata(), odt(n.createdAt()),
                n.externalSource(), n.externalId(), n.tabaqa(), n.gradeText(),
                n.bornOnText(), n.diedOnText()
        );
        return n;
    }

    public Optional<Narrator> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_narrators WHERE id = ?",
                ROW_MAPPER, id
        ).stream().findFirst();
    }

    /**
     * Lookup по нормализованному арабскому имени — дедуп нарраторов на
     * персисте извлечённого иснада (ADR-059 amendment). На name_ar_normalized
     * сознательно НЕТ unique-constraint: разные исторические личности могут
     * иметь одинаковую нормализованную форму (омонимы), так что это MVP-дедуп
     * find-then-save, не строгий natural key. LIMIT 1 — берём первого
     * совпавшего (детерминированно по created_at, старейший).
     */
    public Optional<Narrator> findByNameArNormalized(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_narrators "
                        + "WHERE name_ar_normalized = ? ORDER BY created_at ASC LIMIT 1",
                ROW_MAPPER, normalized
        ).stream().findFirst();
    }

    /** Точный дедуп рави по природному ключу источника (alminasa narrator id). */
    public Optional<Narrator> findByExternalId(String externalSource, String externalId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_narrators "
                        + "WHERE external_source = ? AND external_id = ?",
                ROW_MAPPER, externalSource, externalId
        ).stream().findFirst();
    }

    /**
     * Bulk fetch narrator'ов по списку id одной волной (избегаем N+1
     * при сборке sanad-графа, где один граф ссылается на 5-10 narrator'ов).
     */
    public List<Narrator> findByIds(List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_narrators WHERE id IN (" + placeholders + ")",
                ROW_MAPPER, ids.toArray()
        );
    }

    /**
     * Paginated listing с filters (Vision 49d Phase 1 - search/filter).
     * q substring по name_ar_normalized (case-insensitive); reliability
     * exact match через whitelist.
     */
    public List<Narrator> findPage(String q, String reliability, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS)
                .append(" FROM hd_narrators WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND LOWER(name_ar_normalized) LIKE LOWER(?)");
            args.add("%" + q + "%");
        }
        if (reliability != null && !reliability.isBlank()) {
            sql.append(" AND reliability_grade = ?");
            args.add(reliability);
        }
        sql.append(" ORDER BY year_death_hijri ASC NULLS LAST, name_ar_normalized ASC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    public long countFiltered(String q, String reliability) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM hd_narrators WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND LOWER(name_ar_normalized) LIKE LOWER(?)");
            args.add("%" + q + "%");
        }
        if (reliability != null && !reliability.isBlank()) {
            sql.append(" AND reliability_grade = ?");
            args.add(reliability);
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    /**
     * Полное обновление рави по id — все колонки кроме id и created_at.
     * Используется маппером alminasa для upsert-паттерна: find → update | insert.
     */
    public void update(Narrator n) {
        jdbcTemplate.update(
                "UPDATE hd_narrators SET "
                        + "authority_id = ?, name_ar = ?, name_ar_normalized = ?, "
                        + "kunya = ?, laqab = ?, year_birth_hijri = ?, year_death_hijri = ?, "
                        + "birthplace = ?, death_place = ?, primary_residence = ?, "
                        + "reliability_grade = ?, reliability_comment = ?, "
                        + "transmitted_count_cached = ?, metadata = ?::jsonb, "
                        + "external_source = ?, external_id = ?, tabaqa = ?, grade_text = ?, "
                        + "born_on_text = ?, died_on_text = ? "
                        + "WHERE id = ?",
                n.authorityId(), n.nameAr(), n.nameArNormalized(),
                n.kunya(), n.laqab(), n.yearBirthHijri(), n.yearDeathHijri(),
                n.birthplace(), n.deathPlace(), n.primaryResidence(),
                n.reliabilityGrade(), n.reliabilityComment(),
                n.transmittedCountCached(), n.metadata(),
                n.externalSource(), n.externalId(), n.tabaqa(), n.gradeText(),
                n.bornOnText(), n.diedOnText(),
                n.id()
        );
    }

    /**
     * Загружает все нормализованные имена рави из alminasa (external_source='alminasa')
     * в Map {@code name_ar_normalized → List<id>} для Java-резолва
     * narrator-relations (план 3, решение 11б).
     * При нескольких рави с одним именем (омонимы) список содержит оба id —
     * маппер оставляет FK пустым (гомонимы не резолвятся однозначно).
     */
    public Map<String, List<UUID>> findExternalNormalizedNameIds() {
        Map<String, List<UUID>> result = new HashMap<>();
        jdbcTemplate.query(
                "SELECT name_ar_normalized, id FROM hd_narrators "
                        + "WHERE external_source = 'alminasa'",
                rs -> {
                    String name = rs.getString("name_ar_normalized");
                    UUID id = rs.getObject("id", UUID.class);
                    result.computeIfAbsent(name, k -> new ArrayList<>()).add(id);
                });
        return result;
    }
}
