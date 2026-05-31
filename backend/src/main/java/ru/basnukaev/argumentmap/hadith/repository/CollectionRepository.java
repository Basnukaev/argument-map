package ru.basnukaev.argumentmap.hadith.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.domain.Collection;

/**
 * Доступ к hd_collections. Phase 5 ETL (этап 49.C, §11).
 */
@Repository
public class CollectionRepository {

    private static final String COLUMNS =
            "id, slug, name_ar, name_en, name_ru, compiler_narrator_id, "
                    + "total_hadith, metadata, created_at";

    private static final RowMapper<Collection> ROW_MAPPER = (rs, rn) -> new Collection(
            rs.getObject("id", UUID.class),
            rs.getString("slug"),
            rs.getString("name_ar"),
            rs.getString("name_en"),
            rs.getString("name_ru"),
            rs.getObject("compiler_narrator_id", UUID.class),
            (Integer) rs.getObject("total_hadith"),
            rs.getString("metadata"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public CollectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Collection save(Collection c) {
        jdbcTemplate.update(
                "INSERT INTO hd_collections (" + COLUMNS + ") VALUES "
                        + "(?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                c.id(), c.slug(), c.nameAr(), c.nameEn(), c.nameRu(),
                c.compilerNarratorId(), c.totalHadith(), c.metadata(), odt(c.createdAt())
        );
        return c;
    }

    public Optional<Collection> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_collections WHERE id = ?",
                ROW_MAPPER, id
        ).stream().findFirst();
    }

    public Optional<Collection> findBySlug(String slug) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_collections WHERE slug = ?",
                ROW_MAPPER, slug
        ).stream().findFirst();
    }

    public List<Collection> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_collections ORDER BY created_at ASC",
                ROW_MAPPER
        );
    }
}
