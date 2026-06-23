package ru.basnukaev.argumentmap.hadith.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.curation.service.OverrideApplyService;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;

/**
 * Доступ к hd_hadiths. Vision 49d Section 2.6 Phase 1.
 *
 * <p><b>Курация (ADR-065):</b> display-методы ({@link #findById},
 * {@link #findPage}, {@link #findByNarratorIdPage}, {@link #findBySourceIds})
 * накладывают overlay-правки через {@link OverrideApplyService} — отдают
 * EFFECTIVE-значения. Import idempotency lookups ({@link #findByExternalId},
 * {@link #findByCollectionIdAndPrimaryNumber}) отдают RAW (импорт сравнивает/
 * пишет базовый слой; иначе правка затёрлась бы обратно в импорт).
 */
@Repository
public class HadithRepository {

    private static final String COLUMNS =
            "id, collection_id, primary_number, normalized_matn, status, "
                    + "source_id, metadata, created_at, "
                    + "external_source, external_id, hadith_type, "
                    + "chapter_ar, sub_chapter_ar, full_text_ar, authenticity";

    private static final RowMapper<Hadith> ROW_MAPPER = (rs, rn) -> new Hadith(
            rs.getObject("id", UUID.class),
            rs.getObject("collection_id", UUID.class),
            (Integer) rs.getObject("primary_number"),
            rs.getString("normalized_matn"),
            rs.getString("status"),
            rs.getObject("source_id", UUID.class),
            rs.getString("metadata"),
            instant(rs, "created_at"),
            rs.getString("external_source"),
            rs.getString("external_id"),
            rs.getString("hadith_type"),
            rs.getString("chapter_ar"),
            rs.getString("sub_chapter_ar"),
            rs.getString("full_text_ar"),
            rs.getString("authenticity")
    );

    private final JdbcTemplate jdbcTemplate;
    private final OverrideApplyService overrideApply;

    public HadithRepository(JdbcTemplate jdbcTemplate, OverrideApplyService overrideApply) {
        this.jdbcTemplate = jdbcTemplate;
        this.overrideApply = overrideApply;
    }

    public Hadith save(Hadith h) {
        jdbcTemplate.update(
                "INSERT INTO hd_hadiths (" + COLUMNS + ") VALUES "
                        + "(?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)",
                h.id(), h.collectionId(), h.primaryNumber(), h.normalizedMatn(),
                h.status(), h.sourceId(), h.metadata(), odt(h.createdAt()),
                h.externalSource(), h.externalId(), h.hadithType(),
                h.chapterAr(), h.subChapterAr(), h.fullTextAr(), h.authenticity()
        );
        return h;
    }

    public Optional<Hadith> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_hadiths WHERE id = ?",
                ROW_MAPPER, id
        ).stream().findFirst().map(overrideApply::applyOne);
    }

    /**
     * Полное обновление хадиса по id — все колонки кроме id и created_at.
     * Используется маппером alminasa для upsert-паттерна: find → update | insert.
     */
    public void update(Hadith h) {
        jdbcTemplate.update(
                "UPDATE hd_hadiths SET "
                        + "collection_id = ?, primary_number = ?, normalized_matn = ?, "
                        + "status = ?, source_id = ?, metadata = ?::jsonb, "
                        + "external_source = ?, external_id = ?, hadith_type = ?, "
                        + "chapter_ar = ?, sub_chapter_ar = ?, full_text_ar = ?, "
                        + "authenticity = ? "
                        + "WHERE id = ?",
                h.collectionId(), h.primaryNumber(), h.normalizedMatn(),
                h.status(), h.sourceId(), h.metadata(),
                h.externalSource(), h.externalId(), h.hadithType(),
                h.chapterAr(), h.subChapterAr(), h.fullTextAr(),
                h.authenticity(),
                h.id()
        );
    }

    /**
     * Выставляет {@code source_id} (мост в citation-домен) — под-проект #2,
     * при первом прикреплении хадиса к узлу как опоры. Идемпотентно на
     * уровне сервиса (вызывается только если source_id был null).
     */
    public void updateSourceId(UUID hadithId, UUID sourceId) {
        jdbcTemplate.update("UPDATE hd_hadiths SET source_id = ? WHERE id = ?",
                sourceId, hadithId);
    }

    /**
     * Natural-key lookup (collection_id, primary_number) — естественный ключ
     * импортированного хадиса. Используется ETL для идемпотентности (UNIQUE
     * constraint hd_hadiths_collection_number_unique).
     */
    public Optional<Hadith> findByCollectionIdAndPrimaryNumber(UUID collectionId, int primaryNumber) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_hadiths "
                        + "WHERE collection_id = ? AND primary_number = ?",
                ROW_MAPPER, collectionId, primaryNumber
        ).stream().findFirst();
    }

    /** Поиск по природному ключу источника (alminasa hadith_id) для идемпотентного импорта. */
    public Optional<Hadith> findByExternalId(String externalSource, String externalId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_hadiths "
                        + "WHERE external_source = ? AND external_id = ?",
                ROW_MAPPER, externalSource, externalId
        ).stream().findFirst();
    }

    /**
     * Обратный lookup по списку {@code source_id} (мост citation→hadith,
     * под-проект #2). Используется при обогащении {@code GET
     * /nodes/{id}/sources}: хадис-опоры узнаём по source_id. Один SQL вместо
     * N findById. Пустой список — без запроса (IN () невалиден в SQL),
     * как в {@link MatnRepository#findPrimaryTextByHadithIds}.
     */
    public List<Hadith> findBySourceIds(List<UUID> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return List.of();
        }
        String placeholders = sourceIds.stream().map(x -> "?").collect(Collectors.joining(","));
        return overrideApply.applyHadiths(jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_hadiths "
                        + "WHERE source_id IN (" + placeholders + ")",
                ROW_MAPPER, sourceIds.toArray()
        ));
    }

    public List<Hadith> findPage(String q, String status, String authenticity, UUID collectionId,
                                 String sort, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS)
                .append(" FROM hd_hadiths WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, q, status, authenticity, collectionId);
        sql.append(orderByClause(sort)).append(" LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return overrideApply.applyHadiths(jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray()));
    }

    /**
     * Единый набор фильтров для {@link #findPage} и {@link #countFiltered} —
     * status (ось провенанса) и authenticity (ось достоверности)
     * ортогональны и комбинируются по AND.
     *
     * <p><b>Курация (ADR-065):</b> фильтр идёт по БАЗОВОМУ слою колонки —
     * apply накладывается ПОСЛЕ fetch+пагинации, поэтому override authenticity/
     * status в фасет-фильтре WHERE не учитывается (правка видна в карточке, но
     * фасет-счётчик считает по импорту). Осознанное ограничение; effective-
     * фасет через JOIN — спека §10 (backlog).
     */
    private static void appendFilters(StringBuilder sql, List<Object> args, String q,
                                      String status, String authenticity, UUID collectionId) {
        if (q != null && !q.isBlank()) {
            sql.append(" AND LOWER(normalized_matn) LIKE LOWER(?)");
            args.add("%" + q + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (authenticity != null && !authenticity.isBlank()) {
            sql.append(" AND authenticity = ?");
            args.add(authenticity);
        }
        if (collectionId != null) {
            sql.append(" AND collection_id = ?");
            args.add(collectionId);
        }
    }

    /**
     * Whitelist ORDER BY — SQL-safe (фиксированные клаузы, не интерполяция).
     * {@code alphabetical} — арабский алфавитный (по normalized_matn);
     * {@code number} — по номеру в сборнике; иначе {@code recent}.
     */
    private static String orderByClause(String sort) {
        return switch (sort == null ? "recent" : sort) {
            case "number" -> " ORDER BY primary_number ASC NULLS LAST, created_at DESC";
            case "alphabetical" -> " ORDER BY normalized_matn ASC";
            default -> " ORDER BY created_at DESC";
        };
    }

    /** Число хадисов по каждому сборнику (для chip-фильтра на UI). Один GROUP BY. */
    public Map<UUID, Long> countByCollectionGrouped() {
        Map<UUID, Long> counts = new HashMap<>();
        jdbcTemplate.query(
                "SELECT collection_id, COUNT(*) AS cnt FROM hd_hadiths "
                        + "WHERE collection_id IS NOT NULL GROUP BY collection_id",
                (java.sql.ResultSet rs) -> {
                    counts.put(rs.getObject("collection_id", UUID.class), rs.getLong("cnt"));
                });
        return counts;
    }

    /**
     * Число хадисов по сборнику с фильтром по источнику (план 5, фикс C1):
     * зеркало {@link #countByCollectionGrouped} + {@code AND external_source = ?}.
     * Каталог alminasa-импорта считает mappedCount ТОЛЬКО для alminasa-хадисов —
     * иначе legacy sunnah-строки (тот же сборник в dev-БД) исказили бы прогресс.
     */
    public Map<UUID, Long> countByCollectionGroupedForSource(String externalSource) {
        Map<UUID, Long> counts = new HashMap<>();
        jdbcTemplate.query(
                "SELECT collection_id, COUNT(*) AS cnt FROM hd_hadiths "
                        + "WHERE collection_id IS NOT NULL AND external_source = ? "
                        + "GROUP BY collection_id",
                (java.sql.ResultSet rs) -> {
                    counts.put(rs.getObject("collection_id", UUID.class), rs.getLong("cnt"));
                },
                externalSource);
        return counts;
    }

    public long countFiltered(String q, String status, String authenticity, UUID collectionId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM hd_hadiths WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, q, status, authenticity, collectionId);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    /**
     * Хадисы, в иснадах которых встречается данный narrator (علم الرجال):
     * hd_sanad_narrators → hd_sanads → hd_hadiths. DISTINCT — один хадис
     * может ссылаться на narrator'а в нескольких своих цепях.
     */
    public List<Hadith> findByNarratorIdPage(UUID narratorId, int limit, int offset) {
        // ВНИМАНИЕ: ручной список колонок (h.-алиасы для JOIN) должен совпадать
        // с COLUMNS/ROW_MAPPER по порядку и числу — при расширении маппера править здесь тоже.
        return overrideApply.applyHadiths(jdbcTemplate.query(
                "SELECT DISTINCT h.id, h.collection_id, h.primary_number, h.normalized_matn, "
                        + "h.status, h.source_id, h.metadata, h.created_at, "
                        + "h.external_source, h.external_id, h.hadith_type, "
                        + "h.chapter_ar, h.sub_chapter_ar, h.full_text_ar, h.authenticity "
                        + "FROM hd_hadiths h "
                        + "JOIN hd_sanads s ON s.hadith_id = h.id "
                        + "JOIN hd_sanad_narrators sn ON sn.sanad_id = s.id "
                        + "WHERE sn.narrator_id = ? "
                        + "ORDER BY h.created_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, narratorId, limit, offset
        ));
    }

    public long countByNarratorId(UUID narratorId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT h.id) FROM hd_hadiths h "
                        + "JOIN hd_sanads s ON s.hadith_id = h.id "
                        + "JOIN hd_sanad_narrators sn ON sn.sanad_id = s.id "
                        + "WHERE sn.narrator_id = ?",
                Long.class, narratorId
        );
        return count == null ? 0L : count;
    }
}
